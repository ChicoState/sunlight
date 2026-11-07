package edu.sunlight.screentime

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private const val FOCUS_SCREEN_BRIGHTNESS = 0.03f

/**
 * Keeps the screen on, dimmed, and with system bars hidden (swipe reveals them) during an active
 * preview. This is a foreground display mode, not a device lock.
 */
internal fun ComponentActivity.applyFocusDisplay(active: Boolean) {
  val insets = WindowCompat.getInsetsController(window, window.decorView)
  if (active) {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.attributes = window.attributes.apply { screenBrightness = FOCUS_SCREEN_BRIGHTNESS }
    insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    insets.hide(WindowInsetsCompat.Type.systemBars())
  } else {
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.attributes =
        window.attributes.apply {
          screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    insets.show(WindowInsetsCompat.Type.systemBars())
  }
}
