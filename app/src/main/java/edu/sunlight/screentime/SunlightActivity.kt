package edu.sunlight.screentime

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
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
import androidx.lifecycle.lifecycleScope
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val android.content.Context.focusDataStore by preferencesDataStore(name = "focus_settings")
private val releaseHourKey = intPreferencesKey("release_hour")
private val releaseMinuteKey = intPreferencesKey("release_minute")
private const val FOCUS_SCREEN_BRIGHTNESS = 0.03f

class SunlightActivity : ComponentActivity() {
  private var releaseTime by mutableStateOf<LocalTime?>(null)
  private var phase by mutableStateOf(FocusPhase.SETUP)
  private var statusMessage by mutableStateOf("Choose the time to begin checking for sunlight.")
  private var sessionTarget: Instant? = null
  private var countdownEndsAt: Instant? = null
  private var displayNow by mutableStateOf(Instant.now())
  private val sunlightCamera = SunlightCamera(this, ::sunlightDetected)

  private val cameraPermission =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSession()
        else statusMessage = "Camera permission is required to begin a Focus session."
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launch {
      val preferences = focusDataStore.data.first()
      val hour = preferences[releaseHourKey]
      val minute = preferences[releaseMinuteKey]
      if (hour != null && minute != null) releaseTime = LocalTime.of(hour, minute)
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
              ),
          onChooseReleaseTime = ::chooseReleaseTime,
          onBeginFocus = ::beginFocus,
          onEndSession = ::endSession,
      )
    }
    tickClock { displayNow = it }
  }

  override fun onResume() {
    super.onResume()
    if (phase != FocusPhase.SETUP && phase != FocusPhase.COMPLETE) hideSystemBars()
  }

  override fun onDestroy() {
    sunlightCamera.stop()
    super.onDestroy()
  }

  private fun chooseReleaseTime() {
    val current = releaseTime ?: LocalTime.of(DEFAULT_RELEASE_HOUR, 0)
    val pickerView = layoutInflater.inflate(R.layout.release_time_picker, null)
    val picker = pickerView.findViewById<TimePicker>(R.id.release_time_picker)
    picker.setIs24HourView(false)
    picker.hour = current.hour
    picker.minute = current.minute
    AlertDialog.Builder(this)
        .setTitle("Sunlight check time")
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
          statusMessage = "Sunlight checking will begin at ${displayTime(selected)}."
        }
        .show()
  }

  private fun beginFocus() {
    if (releaseTime == null) {
      statusMessage = "Choose a sunlight-check time first."
      return
    }
    if (
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    ) {
      startSession()
    } else {
      cameraPermission.launch(Manifest.permission.CAMERA)
    }
  }

  private fun startSession() {
    val now = Instant.now()
    sessionTarget =
        SessionPlanner.nextRelease(now, requireNotNull(releaseTime), ZoneId.systemDefault())
    phase = FocusPhase.WAITING
    statusMessage =
        "Focus is active. Camera checking begins at ${displayTime(requireNotNull(releaseTime))}."
    enterFocusDisplay()
    updateSession()
  }

  private fun updateSession() {
    val now = Instant.now()
    when (phase) {
      FocusPhase.WAITING ->
          if (!now.isBefore(sessionTarget)) {
            phase = FocusPhase.SEEKING_SUNLIGHT
            statusMessage = "Move the phone into direct sunlight to finish the light check."
            sunlightCamera.start()
          }
      FocusPhase.FINAL_COUNTDOWN -> if (!now.isBefore(countdownEndsAt)) finishSession()
      else -> Unit
    }
    if (phase == FocusPhase.WAITING || phase == FocusPhase.FINAL_COUNTDOWN) {
      window.decorView.postDelayed({ updateSession() }, UPDATE_INTERVAL_MILLIS)
    }
  }

  private fun sunlightDetected() {
    if (phase != FocusPhase.SEEKING_SUNLIGHT) return
    sunlightCamera.stop()
    countdownEndsAt = Instant.now().plus(FINAL_FOCUS_DURATION)
    phase = FocusPhase.FINAL_COUNTDOWN
    statusMessage = "Sunlight detected. Your final 15-minute Focus countdown has begun."
    updateSession()
  }

  private fun finishSession() {
    phase = FocusPhase.COMPLETE
    statusMessage = "Focus complete. Good morning."
    leaveFocusDisplay()
  }

  private fun endSession() {
    sunlightCamera.stop()
    phase = FocusPhase.SETUP
    sessionTarget = null
    countdownEndsAt = null
    statusMessage = "Focus session ended."
    leaveFocusDisplay()
  }

  private companion object {
    const val DEFAULT_RELEASE_HOUR = 7
    val FINAL_FOCUS_DURATION: Duration = Duration.ofMinutes(15)
  }
}

private const val UPDATE_INTERVAL_MILLIS = 1_000L

private fun ComponentActivity.tickClock(onTick: (Instant) -> Unit) {
  onTick(Instant.now())
  window.decorView.postDelayed({ tickClock(onTick) }, UPDATE_INTERVAL_MILLIS)
}

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
