package edu.sunlight.screentime

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()
      SunlightScreen(
          state = state,
          onChooseReleaseTime = {
            showWakeUpTimePicker(viewModel.state.value.releaseTime, viewModel::saveReleaseTime)
          },
          onBeginFocus = { viewModel.startRoutine(debug = false) },
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
        viewModel.state.collect(::syncCamera)
      }
    }
  }

  override fun onStop() {
    stopCamera()
    viewModel.onCameraEvent(CameraEvent.STOPPED)
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
}
