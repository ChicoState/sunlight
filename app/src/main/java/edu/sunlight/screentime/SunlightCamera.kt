package edu.sunlight.screentime

import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

internal class SunlightCamera(
    private val activity: ComponentActivity,
    private val onSunlightDetected: () -> Unit,
) {
  private var brightSince: Instant? = null
  private var cameraProvider: ProcessCameraProvider? = null

  fun start() {
    val providerFuture = ProcessCameraProvider.getInstance(activity)
    providerFuture.addListener(
        {
          val provider = providerFuture.get()
          cameraProvider = provider
          val analysis =
              ImageAnalysis.Builder()
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()
          analysis.setAnalyzer(ContextCompat.getMainExecutor(activity)) { frame ->
            try {
              if (meanLuma(frame.planes[0].buffer) >= LIGHT_THRESHOLD) {
                val now = Instant.now()
                if (brightSince == null) brightSince = now
                if (Duration.between(brightSince, now) >= REQUIRED_BRIGHT_DURATION) {
                  onSunlightDetected()
                }
              } else {
                brightSince = null
              }
            } finally {
              frame.close()
            }
          }
          provider.unbindAll()
          provider.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        },
        ContextCompat.getMainExecutor(activity),
    )
  }

  fun stop() {
    cameraProvider?.unbindAll()
    cameraProvider = null
    brightSince = null
  }

  private fun meanLuma(buffer: ByteBuffer): Int {
    val duplicate = buffer.duplicate()
    var total = 0L
    var count = 0
    while (duplicate.hasRemaining()) {
      total += duplicate.get().toInt() and UNSIGNED_BYTE_MASK
      count += 1
    }
    return if (count == 0) 0 else (total / count).toInt()
  }

  private companion object {
    const val LIGHT_THRESHOLD = 220
    const val UNSIGNED_BYTE_MASK = 0xff
    val REQUIRED_BRIGHT_DURATION: Duration = Duration.ofSeconds(3)
  }
}
