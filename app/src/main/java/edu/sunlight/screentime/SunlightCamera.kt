package edu.sunlight.screentime

import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Binds front-camera analysis to the activity. Luminance is computed on a background thread and
 * readings are delivered on the main thread; frames stay in memory and are never stored.
 */
internal class SunlightCamera(
    private val activity: ComponentActivity,
    private val onLightReading: (Int) -> Unit,
) {
  private var cameraProvider: ProcessCameraProvider? = null
  private var analysisExecutor: ExecutorService? = null
  @Volatile private var generation = 0

  fun start() {
    val request = ++generation
    analysisExecutor?.shutdown()
    val executor = Executors.newSingleThreadExecutor().also { analysisExecutor = it }
    val mainExecutor = ContextCompat.getMainExecutor(activity)
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
          analysis.setAnalyzer(executor) { frame ->
            try {
              if (request == generation) {
                val luma = meanLuma(frame.planes[0].buffer)
                mainExecutor.execute { if (request == generation) onLightReading(luma) }
              }
            } finally {
              frame.close()
            }
          }
          provider.unbindAll()
          provider.bindToLifecycle(activity, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        },
        mainExecutor,
    )
  }

  fun stop() {
    generation++
    cameraProvider?.unbindAll()
    cameraProvider = null
    analysisExecutor?.shutdown()
    analysisExecutor = null
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
