package edu.sunlight.screentime

import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer

internal class SunlightCamera(
    private val activity: ComponentActivity,
    private val onLightReading: (Int) -> Unit,
) {
  private var cameraProvider: ProcessCameraProvider? = null
  private var generation = 0

  fun start() {
    val request = ++generation
    val providerFuture = ProcessCameraProvider.getInstance(activity)
    providerFuture.addListener(
        {
          if (request != generation) return@addListener
          val provider = providerFuture.get()
          cameraProvider = provider
          val analysis =
              ImageAnalysis.Builder()
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()
          analysis.setAnalyzer(ContextCompat.getMainExecutor(activity)) { frame ->
            try {
              if (request == generation) onLightReading(meanLuma(frame.planes[0].buffer))
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
    generation++
    cameraProvider?.unbindAll()
    cameraProvider = null
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
    const val UNSIGNED_BYTE_MASK = 0xff
  }
}
