package edu.sunlight.screentime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal const val EMERGENCY_UNLOCK_DELAY_SECONDS = 10
private const val MILLIS_PER_SECOND = 1_000L

/**
 * Deliberate escape hatch: a long-press starts a cancellable countdown, and only reaching zero
 * calls [onConfirmed]. Accessibility users get the same flow through the named long-press action.
 */
@Suppress("FunctionNaming")
@Composable
internal fun EmergencyUnlockAction(accent: Color, onConfirmed: () -> Unit) {
  var remaining by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(remaining) {
    val seconds = remaining ?: return@LaunchedEffect
    if (seconds == 0) {
      remaining = null
      onConfirmed()
    } else {
      delay(MILLIS_PER_SECOND)
      remaining = seconds - 1
    }
  }
  val counting = remaining
  BasicText(
      if (counting == null) "Hold for emergency unlock"
      else "Emergency unlock in $counting s. Tap to cancel.",
      style = TextStyle(color = accent, fontSize = 16.sp, textAlign = TextAlign.Center),
      modifier =
          Modifier.fillMaxWidth()
              .testTag("emergency-unlock")
              .border(1.dp, accent, RoundedCornerShape(16.dp))
              .semantics { liveRegion = LiveRegionMode.Polite }
              .combinedClickable(
                  role = Role.Button,
                  onClickLabel = if (counting == null) null else "Cancel emergency unlock",
                  onClick = { remaining = null },
                  onLongClickLabel = "Start emergency unlock",
                  onLongClick = {
                    if (remaining == null) remaining = EMERGENCY_UNLOCK_DELAY_SECONDS
                  },
              )
              .heightIn(min = 56.dp)
              .padding(16.dp),
  )
}

@Suppress("FunctionNaming", "MagicNumber")
@Composable
internal fun CheckboxMark(checked: Boolean, color: Color, enabled: Boolean) {
  val tint = if (enabled) color else SunlightPalette.outline
  Box(
      Modifier.size(24.dp)
          .then(
              if (checked) Modifier.background(tint, RoundedCornerShape(4.dp))
              else Modifier.border(2.dp, tint, RoundedCornerShape(4.dp))
          )
  ) {
    if (checked) {
      Canvas(Modifier.size(24.dp)) {
        val scale = size.width / 24f
        val mark =
            Path().apply {
              moveTo(6f * scale, 12f * scale)
              lineTo(10f * scale, 16f * scale)
              lineTo(18f * scale, 8f * scale)
            }
        drawPath(mark, SunlightPalette.onAccent, style = Stroke(width = 2.5f * scale))
      }
    }
  }
}
