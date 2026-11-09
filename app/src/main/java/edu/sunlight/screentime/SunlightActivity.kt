package edu.sunlight.screentime

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Thin host for the morning routine: renders [SunlightViewModel] state and adapts Android-only
 * concerns (native pickers, camera permission and frames, window display mode) to it.
 */
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
          onChooseReleaseTime = {
            showWakeUpTimePicker(viewModel.state.value.releaseTime, viewModel::saveReleaseTime)
          },
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
}
