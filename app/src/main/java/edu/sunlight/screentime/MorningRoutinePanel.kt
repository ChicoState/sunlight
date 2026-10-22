package edu.sunlight.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("FunctionNaming")
@Composable
internal fun MorningRoutinePanel(
    state: SunlightScreenState,
    onChooseTime: () -> Unit,
    onChooseDuration: () -> Unit,
    onDebugSpeedChange: (Int) -> Unit,
) {
  val active = state.phase.isActive()
  val text = if (active) SunlightPalette.nightAccent else SunlightPalette.primaryText
  val secondary = if (active) SunlightPalette.nightSecondary else SunlightPalette.secondaryText
  Column(
      Modifier.fillMaxWidth()
          .background(
              if (active) SunlightPalette.nightSurface else SunlightPalette.surface,
              RoundedCornerShape(20.dp),
          )
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    RoutineLabel(if (state.debugMode) "DEBUG TEST · SIMULATED LIGHT" else "YOUR MORNING", secondary)
    if (state.debugMode && state.debugAvailable && active) {
      DebugSpeedControl(state.debugSpeed, onDebugSpeedChange)
    }
    RoutineSetting(
        "Wake-up time",
        state.releaseTime?.let(::displayTime) ?: "Set time",
        "wake-up-time",
        !active,
        onChooseTime,
        text,
    )
    Box(Modifier.fillMaxWidth().height(1.dp).background(SunlightPalette.outline))
    RoutineSetting(
        "Sunlight duration",
        "${state.sunlightMinutes} min",
        "sunlight-duration",
        !active,
        onChooseDuration,
        text,
    )
    RoutineStatus(state, text, secondary)
  }
}

@Suppress("FunctionNaming")
@Composable
private fun RoutineStatus(state: SunlightScreenState, text: Color, secondary: Color) {
  val active = state.phase.isActive()
  if (state.phase == FocusPhase.FINAL_COUNTDOWN) {
    BasicText(
        remainingCountdown(state.displayNow, state.countdownEndsAt),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 56.sp),
        style = TextStyle(color = text, fontSize = 56.sp, fontWeight = FontWeight.Light),
        modifier = Modifier.testTag("routine-timer"),
    )
    RoutineLabel("Time remaining", secondary)
  } else if (active || state.phase == FocusPhase.COMPLETE) {
    BasicText(
        state.phase.title,
        style = TextStyle(color = text, fontSize = 22.sp, fontWeight = FontWeight.Medium),
    )
  }
  if (state.statusMessage.isNotBlank()) {
    BasicText(
        state.statusMessage,
        style = TextStyle(color = text, fontSize = 16.sp, lineHeight = 24.sp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
  }
  RoutineLabel(
      if (active) state.phase.guidance
      else "Camera stays off until wake-up time. Images are never saved.",
      secondary,
  )
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RoutineSetting(
    label: String,
    value: String,
    tag: String,
    editable: Boolean,
    onClick: () -> Unit,
    color: Color,
) {
  Row(
      Modifier.fillMaxWidth()
          .testTag(tag)
          .then(
              if (editable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
          )
          .heightIn(min = 64.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      RoutineLabel(label, color)
      BasicText(
          value,
          style = TextStyle(color = color, fontSize = 30.sp, fontWeight = FontWeight.Medium),
      )
    }
    if (editable) {
      BasicText(
          "Edit",
          style = TextStyle(color = SunlightPalette.sunlightAccent, fontSize = 14.sp),
          modifier = Modifier.padding(top = 24.dp),
      )
    }
  }
}
