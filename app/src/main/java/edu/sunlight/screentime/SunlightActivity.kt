package edu.sunlight.screentime

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.TimePicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalTime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val FOCUS_SCREEN_BRIGHTNESS = 0.03f

/**
 * Thin host for the morning routine: renders [SunlightViewModel] state and adapts Android-only
 * concerns (native pickers, camera permission and frames, window display mode) to it.
 */
@Suppress("TooManyFunctions")
class SunlightActivity : ComponentActivity() {
  private val debugAvailable: Boolean
    get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

  private val viewModel: SunlightViewModel by viewModels {
    SunlightViewModel.factory(this, debugAvailable)
  }

  private var cameraRunning = false
  private val sunlightCamera =
      SunlightCamera(this) { luma ->
        viewModel.onLightReading(luma)
        if (!viewModel.state.value.needsCamera) stopCamera()
      }

  private val cameraPermission =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.onCameraEvent(CameraEvent.PERMISSION_DENIED)
      }

  private var overlayPermissionRequested = false
  private var requestOverlayPermissionAfterNightDisplay = false
  private var appInBackground = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()
      SunlightScreen(
          state = state,
          onAddSunlightTime = { chooseSunlightTime(null) },
          onBeginFocus = {
            viewModel.startRoutine(debug = false)
            if (viewModel.state.value.phase.isActive()) {
              requestNightDisplaySetup()
            }
          },
          onEndSession = viewModel::endSession,
          onChooseDuration = {
            val current = viewModel.state.value
            if (!current.phase.isActive()) {
              showDurationPicker(current.sunlightMinutes, viewModel::saveSunlightMinutes)
            }
          },
          onDebugSunlight = { viewModel.startRoutine(debug = true) },
          onEnableCamera = {
            if (viewModel.state.value.needsCamera)
                cameraPermission.launch(Manifest.permission.CAMERA)
          },
          onDebugSpeedChange = viewModel::changeDebugSpeed,
          onEditSunlightTime = { time -> chooseSunlightTime(time) },
          onRemoveSunlightTime = { time -> viewModel.removeSunlightTime(time) },
      )
    }
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        launch { viewModel.runClock() }
        launch {
          viewModel.state
              .map { it.phase.isActive() }
              .distinctUntilChanged()
              .collect(::applyFocusDisplay)
        }
        launch {
          viewModel.state.collect { state ->
            syncCamera(state)
            syncBlueLightFilter()
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    appInBackground = false
    if (requestOverlayPermissionAfterNightDisplay) {
      requestOverlayPermissionAfterNightDisplay = false
      requestOverlayPermissionIfNeeded()
    }
    syncBlueLightFilter()
  }

  override fun onStop() {
    stopCamera()
    viewModel.onCameraEvent(CameraEvent.STOPPED)
    appInBackground = true
    syncBlueLightFilter()
    super.onStop()
  }

  private fun chooseSunlightTime(existingTime: LocalTime?) {
    if (viewModel.state.value.phase.isActive()) return
    val current =
        existingTime
            ?: viewModel.state.value.nextSunlightTime
            ?: LocalTime.of(DEFAULT_RELEASE_HOUR, 0)
    val pickerView = layoutInflater.inflate(R.layout.release_time_picker, null)
    val picker = pickerView.findViewById<TimePicker>(R.id.release_time_picker)
    picker.setIs24HourView(false)
    picker.hour = current.hour
    picker.minute = current.minute
    AlertDialog.Builder(this)
        .setTitle(if (existingTime == null) "Add Sunlight Time" else "Edit Sunlight Time")
        .setView(pickerView)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton("Save") { _, _ ->
          // Commit any keyboard edit before reading the native spinner's value.
          picker.clearFocus()
          val selected = LocalTime.of(picker.hour, picker.minute)
          viewModel.saveSunlightTime(existingTime, selected)
        }
        .show()
  }

  /**
   * Runs while resumed: camera access is requested and frames analyzed only when state needs it.
   */
  private fun syncCamera(state: SunlightScreenState) {
    when {
      !state.needsCamera -> stopCamera()
      ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
          PackageManager.PERMISSION_GRANTED ->
          if (viewModel.onCameraEvent(CameraEvent.PERMISSION_MISSING)) {
            cameraPermission.launch(Manifest.permission.CAMERA)
          }
      !cameraRunning -> {
        cameraRunning = true
        sunlightCamera.start()
        viewModel.onCameraEvent(CameraEvent.STARTED)
      }
    }
  }

  private fun stopCamera() {
    sunlightCamera.stop()
    cameraRunning = false
  }

  private fun requestOverlayPermissionIfNeeded() {
    if (
        viewModel.state.value.debugMode ||
            Settings.canDrawOverlays(this) ||
            overlayPermissionRequested
    )
        return
    overlayPermissionRequested = true
    startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
    )
  }

  private fun requestNightDisplaySetup() {
    if (viewModel.state.value.debugMode) return
    val nightDisplaySettings = Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)
    if (nightDisplaySettings.resolveActivity(packageManager) == null) {
      requestOverlayPermissionIfNeeded()
      return
    }
    AlertDialog.Builder(this)
        .setTitle("Enable Android Night Light?")
        .setMessage(
            "Night Light is Android's built-in blue-light filter. Open its settings to enable it and choose your preferred intensity."
        )
        .setNegativeButton("Not now") { _, _ -> requestOverlayPermissionIfNeeded() }
        .setPositiveButton("Open settings") { _, _ ->
          requestOverlayPermissionAfterNightDisplay = true
          startActivity(nightDisplaySettings)
        }
        .setOnCancelListener { requestOverlayPermissionIfNeeded() }
        .show()
  }

  private fun syncBlueLightFilter() {
    val state = viewModel.state.value
    val authorized = !state.debugMode && Settings.canDrawOverlays(this)
    if (state.phase.shouldRunBlueLightFilter(authorized, appInBackground)) {
      BlueLightFilterService.start(this)
    } else {
      BlueLightFilterService.stop(this)
    }
  }

  private fun applyFocusDisplay(active: Boolean) {
    if (active) enterFocusDisplay() else leaveFocusDisplay()
  }

  private fun enterFocusDisplay() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.attributes = window.attributes.apply { screenBrightness = FOCUS_SCREEN_BRIGHTNESS }
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

  private fun hideSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  private companion object {
    const val DEFAULT_RELEASE_HOUR = 7
  }
}
