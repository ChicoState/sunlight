package edu.sunlight.screentime

import android.Manifest
import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.NumberPicker
import android.widget.TimePicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val android.content.Context.focusDataStore by preferencesDataStore(name = "focus_settings")
private val releaseHourKey = intPreferencesKey("release_hour")
private val releaseMinuteKey = intPreferencesKey("release_minute")
private val sunlightMinutesKey = intPreferencesKey("sunlight_minutes")
private const val FOCUS_SCREEN_BRIGHTNESS = 0.03f

@Suppress("TooManyFunctions")
class SunlightActivity : ComponentActivity() {
  private var releaseTime by mutableStateOf<LocalTime?>(null)
  private var phase by mutableStateOf(FocusPhase.SETUP)
  private var statusMessage by mutableStateOf("")
  private var sunlightMinutes by mutableStateOf(DEFAULT_SUNLIGHT_MINUTES)
  private var countdownEndsAt by mutableStateOf<Instant?>(null)
  private var routine: MorningRoutine? = null
  private var debugMode by mutableStateOf(false)
  private var debugSpeed by mutableStateOf(1)
  private var debugClock: DebugClock? = null
  private var cameraPermissionNeeded by mutableStateOf(false)
  private var cameraRequested = false
  private var cameraRunning = false
  private var displayNow by mutableStateOf(Instant.now())
  private val sunlightCamera = SunlightCamera(this) { light -> if (!debugMode) acceptLight(light) }
  private val debugAvailable: Boolean
    get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

  private val cameraPermission =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (routine?.phase == FocusPhase.SEEKING_SUNLIGHT && !debugMode) {
          cameraPermissionNeeded = !granted
          if (granted) updateSession()
          else statusMessage = "Camera access is off. Allow access to check sunlight."
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launch {
      val preferences = focusDataStore.data.first()
      val hour = preferences[releaseHourKey]
      val minute = preferences[releaseMinuteKey]
      if (hour != null && minute != null) releaseTime = LocalTime.of(hour, minute)
      sunlightMinutes = sunlightMinutesOrDefault(preferences[sunlightMinutesKey])
    }
    setContent {
      SunlightScreen(
          state =
              SunlightScreenState(
                  releaseTime = releaseTime,
                  phase = phase,
                  statusMessage = statusMessage,
                  displayNow = displayNow,
                  countdownEndsAt = countdownEndsAt,
                  sunlightMinutes = sunlightMinutes,
                  debugAvailable = debugAvailable,
                  debugMode = debugMode,
                  debugSpeed = debugSpeed,
                  cameraPermissionNeeded = cameraPermissionNeeded,
              ),
          onChooseReleaseTime = ::chooseReleaseTime,
          onBeginFocus = ::beginFocus,
          onEndSession = ::endSession,
          onChooseDuration = ::chooseSunlightDuration,
          onDebugSunlight = ::testSunlight,
          onEnableCamera = ::requestCamera,
          onDebugSpeedChange = ::changeDebugSpeed,
      )
    }
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (isActive) {
          updateSession()
          delay(
              if (debugMode && phase.isActive()) DEBUG_UPDATE_INTERVAL_MILLIS
              else UPDATE_INTERVAL_MILLIS
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (phase != FocusPhase.SETUP && phase != FocusPhase.COMPLETE) hideSystemBars()
  }

  override fun onDestroy() {
    sunlightCamera.stop()
    super.onDestroy()
  }

  override fun onStop() {
    stopCamera()
    routine?.resetLightCheck()
    super.onStop()
  }

  private fun chooseReleaseTime() {
    val current = releaseTime ?: LocalTime.of(DEFAULT_RELEASE_HOUR, 0)
    val pickerView = layoutInflater.inflate(R.layout.release_time_picker, null)
    val picker = pickerView.findViewById<TimePicker>(R.id.release_time_picker)
    picker.setIs24HourView(false)
    picker.hour = current.hour
    picker.minute = current.minute
    AlertDialog.Builder(this)
        .setTitle("Wake-up time")
        .setView(pickerView)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton("Set time") { _, _ ->
          // Commit any keyboard edit before reading the native spinner's value.
          picker.clearFocus()
          val selected = LocalTime.of(picker.hour, picker.minute)
          releaseTime = selected
          lifecycleScope.launch {
            focusDataStore.edit { preferences ->
              preferences[releaseHourKey] = selected.hour
              preferences[releaseMinuteKey] = selected.minute
            }
          }
          statusMessage = "Wake-up time: ${displayTime(selected)}. Try the routine below."
        }
        .show()
  }

  private fun beginFocus() {
    if (releaseTime == null) {
      statusMessage = "Choose your wake-up time first."
      return
    }
    startSession(debug = false)
  }

  private fun startSession(debug: Boolean) {
    stopCamera()
    debugMode = debug
    cameraRequested = false
    cameraPermissionNeeded = false
    countdownEndsAt = null
    val now = Instant.now()
    debugSpeed = 1
    debugClock = if (debug) DebugClock(now, SystemClock.elapsedRealtimeNanos()) else null
    val wakeAt =
        if (debug) now
        else SessionPlanner.nextRelease(now, requireNotNull(releaseTime), ZoneId.systemDefault())
    routine = MorningRoutine(wakeAt, sunlightMinutes)
    phase = FocusPhase.WAITING
    statusMessage =
        if (debug) "Debug test: simulated sunlight. Camera stays off."
        else
            "Sunlight checking starts at ${displayTime(requireNotNull(releaseTime))}. Camera stays off until then."
    enterFocusDisplay()
    updateSession()
  }

  private fun updateSession() {
    val now = routineNow()
    displayNow = now
    val current = routine ?: return
    val previous = current.phase
    current.advance(now)
    phase = current.phase
    if (phase == FocusPhase.COMPLETE && previous != phase) finishSession()
    if (phase != FocusPhase.SEEKING_SUNLIGHT) return
    if (debugMode) {
      acceptLight(SIMULATED_BRIGHTNESS)
    } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      if (
          ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
              PackageManager.PERMISSION_GRANTED
      ) {
        cameraPermissionNeeded = false
        if (!cameraRunning) {
          cameraRunning = true
          statusMessage = "Find bright sunlight and keep the front camera uncovered."
          sunlightCamera.start()
        }
      } else {
        cameraPermissionNeeded = true
        if (!cameraRequested) requestCamera()
      }
    }
  }

  private fun acceptLight(luma: Int) {
    val current = routine ?: return
    if (current.phase != FocusPhase.SEEKING_SUNLIGHT) return
    current.acceptLight(luma, routineNow())
    phase = current.phase
    if (phase == FocusPhase.FINAL_COUNTDOWN) {
      stopCamera()
      countdownEndsAt = current.countdownEndsAt
      statusMessage =
          "${if (debugMode) "Simulated sunlight" else "Bright light"} detected. " +
              "Your ${current.sunlightMinutes}-minute timer has started."
    }
  }

  private fun requestCamera() {
    if (routine?.phase != FocusPhase.SEEKING_SUNLIGHT || debugMode) return
    cameraRequested = true
    cameraPermissionNeeded = true
    cameraPermission.launch(Manifest.permission.CAMERA)
  }

  private fun stopCamera() {
    sunlightCamera.stop()
    cameraRunning = false
  }

  private fun testSunlight() {
    if (debugAvailable && !phase.isActive()) startSession(debug = true)
  }

  private fun routineNow(): Instant =
      debugClock?.now(SystemClock.elapsedRealtimeNanos()) ?: Instant.now()

  private fun changeDebugSpeed(speed: Int) {
    if (!debugAvailable || !debugMode || !phase.isActive()) return
    if (speed !in 1..MAX_DEBUG_SPEED) return
    debugClock?.setSpeed(speed, SystemClock.elapsedRealtimeNanos())
    debugSpeed = speed
    updateSession()
  }

  private fun chooseSunlightDuration() {
    if (phase.isActive()) return
    val picker =
        NumberPicker(this).apply {
          minValue = 1
          maxValue = MAX_SUNLIGHT_MINUTES
          value = sunlightMinutes
          wrapSelectorWheel = false
          contentDescription = "Sunlight duration in minutes"
        }
    AlertDialog.Builder(this)
        .setTitle("Sunlight duration")
        .setMessage("Choose 1–120 minutes.")
        .setView(picker)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton("Save") { _, _ ->
          picker.clearFocus()
          sunlightMinutes = sunlightMinutesOrDefault(picker.value)
          lifecycleScope.launch { focusDataStore.edit { it[sunlightMinutesKey] = sunlightMinutes } }
        }
        .show()
  }

  private fun finishSession() {
    phase = FocusPhase.COMPLETE
    statusMessage = "Your morning timer is complete."
    leaveFocusDisplay()
  }

  private fun endSession() {
    stopCamera()
    phase = FocusPhase.SETUP
    routine = null
    countdownEndsAt = null
    debugMode = false
    debugClock = null
    debugSpeed = 1
    cameraPermissionNeeded = false
    statusMessage = "You exited the morning routine preview early."
    leaveFocusDisplay()
  }

  private companion object {
    const val DEFAULT_RELEASE_HOUR = 7
    const val SIMULATED_BRIGHTNESS = 255
  }
}

private const val UPDATE_INTERVAL_MILLIS = 1_000L
private const val DEBUG_UPDATE_INTERVAL_MILLIS = 50L

private fun ComponentActivity.enterFocusDisplay() {
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  window.attributes = window.attributes.apply { screenBrightness = FOCUS_SCREEN_BRIGHTNESS }
  hideSystemBars()
}

private fun ComponentActivity.leaveFocusDisplay() {
  window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  window.attributes =
      window.attributes.apply {
        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
      }
  WindowCompat.getInsetsController(window, window.decorView)
      .show(WindowInsetsCompat.Type.systemBars())
}

private fun ComponentActivity.hideSystemBars() {
  WindowCompat.getInsetsController(window, window.decorView).apply {
    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    hide(WindowInsetsCompat.Type.systemBars())
  }
}
