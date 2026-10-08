package edu.sunlight.screentime

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val android.content.Context.focusDataStore by preferencesDataStore(name = "focus_settings")
private val releaseHourKey = intPreferencesKey("release_hour")
private val releaseMinuteKey = intPreferencesKey("release_minute")

class SunlightActivity : ComponentActivity() {
  private var releaseTime by mutableStateOf<LocalTime?>(null)
  private var phase by mutableStateOf(FocusPhase.SETUP)
  private var statusMessage by mutableStateOf("Choose the time to begin checking for sunlight.")
  private var sessionTarget: Instant? = null
  private var countdownEndsAt: Instant? = null
  private var brightSince: Instant? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private var displayNow by mutableStateOf(Instant.now())

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
    setContent { SunlightScreen() }
    tickClock()
  }

  override fun onResume() {
    super.onResume()
    if (phase != FocusPhase.SETUP && phase != FocusPhase.COMPLETE) hideSystemBars()
  }

  override fun onDestroy() {
    stopCamera()
    super.onDestroy()
  }

  private fun chooseReleaseTime() {
    val current = releaseTime ?: LocalTime.of(7, 0)
    TimePickerDialog(
            this,
            { _, hour, minute ->
              releaseTime = LocalTime.of(hour, minute)
              lifecycleScope.launch {
                focusDataStore.edit { preferences ->
                  preferences[releaseHourKey] = hour
                  preferences[releaseMinuteKey] = minute
                }
              }
              statusMessage = "Sunlight checking will begin at ${displayTime(releaseTime!!)}."
            },
            current.hour,
            current.minute,
            android.text.format.DateFormat.is24HourFormat(this),
        )
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
      FocusPhase.WAITING -> if (!now.isBefore(sessionTarget)) startCamera()
      FocusPhase.FINAL_COUNTDOWN -> if (!now.isBefore(countdownEndsAt)) finishSession()
      else -> Unit
    }
    if (phase == FocusPhase.WAITING || phase == FocusPhase.FINAL_COUNTDOWN) {
      window.decorView.postDelayed({ updateSession() }, 1_000)
    }
  }

  private fun startCamera() {
    phase = FocusPhase.SEEKING_SUNLIGHT
    statusMessage = "Move the phone into direct sunlight to finish the light check."
    val providerFuture = ProcessCameraProvider.getInstance(this)
    providerFuture.addListener(
        {
          val provider = providerFuture.get()
          cameraProvider = provider
          val analysis =
              ImageAnalysis.Builder()
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()
          analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { frame ->
            try {
              if (meanLuma(frame.planes[0].buffer) >= LIGHT_THRESHOLD) {
                val now = Instant.now()
                if (brightSince == null) brightSince = now
                if (Duration.between(brightSince, now) >= REQUIRED_BRIGHT_DURATION)
                    sunlightDetected()
              } else {
                brightSince = null
              }
            } finally {
              frame.close()
            }
          }
          provider.unbindAll()
          provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        },
        ContextCompat.getMainExecutor(this),
    )
  }

  private fun sunlightDetected() {
    if (phase != FocusPhase.SEEKING_SUNLIGHT) return
    stopCamera()
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
    stopCamera()
    phase = FocusPhase.SETUP
    sessionTarget = null
    countdownEndsAt = null
    statusMessage = "Focus session ended."
    leaveFocusDisplay()
  }

  private fun stopCamera() {
    cameraProvider?.unbindAll()
    cameraProvider = null
    brightSince = null
  }

  private fun enterFocusDisplay() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.attributes = window.attributes.apply { screenBrightness = 0.03f }
    hideSystemBars()
  }

  private fun leaveFocusDisplay() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.attributes =
        window.attributes.apply {
          screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    WindowCompat.getInsetsController(window, window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
  }

  private fun meanLuma(buffer: ByteBuffer): Int {
    val duplicate = buffer.duplicate()
    var total = 0L
    var count = 0
    while (duplicate.hasRemaining()) {
      total += duplicate.get().toInt() and 0xff
      count += 1
    }
    return if (count == 0) 0 else (total / count).toInt()
  }

  private fun tickClock() {
    displayNow = Instant.now()
    window.decorView.postDelayed({ tickClock() }, 1_000)
  }

  private fun hideSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  @androidx.compose.runtime.Composable
  private fun SunlightScreen() {
    val active =
        phase == FocusPhase.WAITING ||
            phase == FocusPhase.SEEKING_SUNLIGHT ||
            phase == FocusPhase.FINAL_COUNTDOWN
    val foreground = if (active) NightRed else Color.White
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(if (active) Color.Black else Color(0xff171717))
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      BasicText(
          currentClock(),
          style = TextStyle(color = foreground, fontSize = 52.sp, fontWeight = FontWeight.Light),
      )
      Spacer(Modifier.height(32.dp))
      BasicText(
          statusMessage,
          style = TextStyle(color = foreground, fontSize = 20.sp),
          modifier = Modifier.semantics { contentDescription = statusMessage },
      )
      Spacer(Modifier.height(32.dp))
      if (!active) {
        FocusAction("Choose release time", foreground, ::chooseReleaseTime)
        Spacer(Modifier.height(16.dp))
        BasicText(
            releaseTime?.let { "Check begins at ${displayTime(it)}" } ?: "No release time selected",
            style = TextStyle(color = Color.LightGray, fontSize = 16.sp),
        )
        Spacer(Modifier.height(24.dp))
        FocusAction("Start Focus", foreground, ::beginFocus)
      } else {
        if (phase == FocusPhase.FINAL_COUNTDOWN) {
          BasicText(
              "${remainingCountdown()} remaining",
              style = TextStyle(color = foreground, fontSize = 18.sp),
          )
          Spacer(Modifier.height(24.dp))
        }
        BasicText(
            "Hold to end session",
            style = TextStyle(color = foreground, fontSize = 18.sp),
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = ::endSession),
        )
      }
    }
  }

  @androidx.compose.runtime.Composable
  private fun FocusAction(label: String, color: Color, action: () -> Unit) {
    BasicText(
        label,
        style = TextStyle(color = color, fontSize = 20.sp),
        modifier =
            Modifier.fillMaxWidth()
                .padding(16.dp)
                .combinedClickable(onClick = action, onLongClick = action),
    )
  }

  private fun currentClock(): String =
      displayNow
          .atZone(ZoneId.systemDefault())
          .toLocalTime()
          .format(DateTimeFormatter.ofPattern("HH:mm"))

  private fun remainingCountdown(): String {
    val seconds =
        countdownEndsAt?.let { Duration.between(displayNow, it).seconds.coerceAtLeast(0) } ?: 0
    return String.format("%02d:%02d", seconds / 60, seconds % 60)
  }

  private fun displayTime(time: LocalTime): String =
      time.format(DateTimeFormatter.ofPattern("HH:mm"))

  private enum class FocusPhase {
    SETUP,
    WAITING,
    SEEKING_SUNLIGHT,
    FINAL_COUNTDOWN,
    COMPLETE,
  }

  private companion object {
    const val LIGHT_THRESHOLD = 220
    val REQUIRED_BRIGHT_DURATION: Duration = Duration.ofSeconds(3)
    val FINAL_FOCUS_DURATION: Duration = Duration.ofMinutes(15)
    val NightRed = Color(0xffff5c5c)
  }
}
