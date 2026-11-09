package edu.sunlight.screentime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

@Suppress("FunctionNaming")
@Composable
internal fun SunlightTimesPage(
    state: SunlightScreenState,
    onAddTime: () -> Unit,
    onEditTime: (LocalTime) -> Unit,
    onRemoveTime: (LocalTime) -> Unit,
) {
  val active = state.phase.isActive()
  val accent = if (active) SunlightPalette.nightAccent else SunlightPalette.sunlightAccent
  val text = if (active) SunlightPalette.nightAccent else SunlightPalette.primaryText
  val secondary = if (active) SunlightPalette.nightSecondary else SunlightPalette.secondaryText
  Column(
      Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(24.dp)
          .testTag("page-Alarms"),
      verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    BasicText(
        "Sunlight Times",
        style = TextStyle(color = accent, fontSize = 28.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier.semantics { heading() },
    )
    RoutineLabel(
        "Each daily time starts a fresh sunlight check. No alarm will ring in this preview.",
        secondary,
    )
    if (state.sunlightTimes.isEmpty()) {
      BasicText("No Sunlight Times yet.", style = TextStyle(color = text, fontSize = 18.sp))
    } else {
      state.sunlightTimes.forEach { time ->
        Row(
            Modifier.fillMaxWidth()
                .testTag("sunlight-time-${time.hour}-${time.minute}")
                .then(
                    if (active) Modifier
                    else Modifier.clickable(role = Role.Button) { onEditTime(time) }
                )
                .heightIn(min = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RoutineLabel("Sunlight Time", secondary)
            BasicText(
                displayTime(time),
                style = TextStyle(color = text, fontSize = 30.sp, fontWeight = FontWeight.Medium),
            )
          }
          if (!active) {
            BasicText(
                "Edit",
                style = TextStyle(color = accent, fontSize = 14.sp),
            )
            RemoveTimeAction(time) { onRemoveTime(time) }
          }
        }
      }
    }
    if (!active) RoutineAction("Add Sunlight Time", onAddTime)
  }
}

@Suppress("FunctionNaming")
@Composable
private fun RemoveTimeAction(time: LocalTime, onRemove: () -> Unit) {
  val description = "Remove ${displayTime(time)}"
  Box(
      Modifier.background(SunlightPalette.danger, RoundedCornerShape(12.dp))
          .clickable(role = Role.Button, onClick = onRemove)
          .semantics { contentDescription = description }
          .testTag("remove-sunlight-time-${time.hour}-${time.minute}")
          .padding(12.dp),
      contentAlignment = Alignment.Center,
  ) {
    TrashCanIcon(SunlightPalette.onAccent)
  }
}

@Suppress("FunctionNaming", "MagicNumber")
@Composable
private fun TrashCanIcon(color: Color) {
  Canvas(Modifier.size(24.dp)) {
    val stroke = Stroke(width = 1.8.dp.toPx())
    drawRect(
        color,
        topLeft = Offset(size.width * 0.25f, size.height * 0.3f),
        size = Size(size.width * 0.5f, size.height * 0.58f),
        style = stroke,
    )
    drawLine(
        color,
        Offset(size.width * 0.18f, size.height * 0.22f),
        Offset(size.width * 0.82f, size.height * 0.22f),
        strokeWidth = stroke.width,
    )
    drawLine(
        color,
        Offset(size.width * 0.4f, size.height * 0.12f),
        Offset(size.width * 0.6f, size.height * 0.12f),
        strokeWidth = stroke.width,
    )
  }
}
