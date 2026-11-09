package edu.sunlight.screentime

import android.content.res.ColorStateList
import android.widget.SeekBar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat

@Suppress("FunctionNaming")
@Composable
internal fun DebugSpeedControl(speed: Int, onChange: (Int) -> Unit) {
  val currentOnChange by rememberUpdatedState(onChange)
  Column(Modifier.fillMaxWidth().testTag("debug-speed")) {
    RoutineLabel("Time speed: ${speed}×", SunlightPalette.nightAccent)
    AndroidView(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        factory = { context ->
          SeekBar(context).apply {
            max = MAX_DEBUG_SPEED - 1
            keyProgressIncrement = 1
            contentDescription = "Debug time speed"
            progressTintList = ColorStateList.valueOf(SunlightPalette.nightAccent.toArgb())
            thumbTintList = progressTintList
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                  override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) currentOnChange(progress + 1)
                  }

                  override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                  override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                }
            )
          }
        },
        update = { bar ->
          if (bar.progress != speed - 1) bar.progress = speed - 1
          ViewCompat.setStateDescription(bar, "${speed}×")
        },
    )
    RoutineLabel("1× real time  ·  100× fastest", SunlightPalette.nightSecondary)
  }
}
