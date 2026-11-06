package edu.sunlight.screentime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("FunctionNaming")
@Composable
internal fun BottomNavigation(selected: String, active: Boolean, onSelect: (String) -> Unit) {
  Row(
      Modifier.fillMaxWidth()
          .background(if (active) SunlightPalette.nightSurface else SunlightPalette.surface)
          .selectableGroup(),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    listOf("Home", "Blocked apps", "Alarms", "Settings").forEach { page ->
      val color =
          when {
            active && page == selected -> SunlightPalette.nightAccent
            active -> SunlightPalette.nightSecondary
            page == selected -> SunlightPalette.sunlightAccent
            else -> SunlightPalette.secondaryText
          }
      Column(
          Modifier.weight(1f)
              .testTag("tab-$page")
              .selectable(page == selected, role = Role.Tab, onClick = { onSelect(page) })
              .padding(horizontal = 4.dp, vertical = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        NavigationIcon(page, color)
        BasicText(
            page,
            style = TextStyle(color = color, fontSize = 12.sp, textAlign = TextAlign.Center),
        )
      }
    }
  }
}

@Suppress("FunctionNaming", "MagicNumber")
@Composable
private fun NavigationIcon(page: String, color: Color) {
  Canvas(Modifier.size(24.dp)) {
    scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
      val stroke = Stroke(width = 1.8f)
      when (page) {
        "Home" -> {
          val house =
              Path().apply {
                moveTo(3f, 11f)
                lineTo(12f, 3f)
                lineTo(21f, 11f)
                moveTo(5f, 10f)
                lineTo(5f, 21f)
                lineTo(10f, 21f)
                lineTo(10f, 15f)
                lineTo(14f, 15f)
                lineTo(14f, 21f)
                lineTo(19f, 21f)
                lineTo(19f, 10f)
              }
          drawPath(house, color, style = stroke)
        }
        "Blocked apps" -> {
          drawRect(color, Offset(4f, 4f), Size(16f, 16f), style = stroke)
          drawLine(color, Offset(5f, 19f), Offset(19f, 5f), strokeWidth = 1.8f)
        }
        "Alarms" -> {
          drawCircle(color, 8f, Offset(12f, 13f), style = stroke)
          drawLine(color, Offset(12f, 13f), Offset(12f, 8f), 1.8f)
          drawLine(color, Offset(12f, 13f), Offset(16f, 15f), 1.8f)
          drawLine(color, Offset(3f, 5f), Offset(7f, 2f), 1.8f)
          drawLine(color, Offset(17f, 2f), Offset(21f, 5f), 1.8f)
        }
        "Settings" -> {
          drawCircle(color, 7f, Offset(12f, 12f), style = stroke)
          drawCircle(color, 2.5f, Offset(12f, 12f), style = stroke)
          repeat(8) { tooth ->
            rotate(tooth * 45f, Offset(12f, 12f)) {
              drawLine(color, Offset(12f, 2f), Offset(12f, 5f), 2.5f)
            }
          }
        }
      }
    }
  }
}
