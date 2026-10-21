package edu.sunlight.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.sunlight.screentime.SunlightPalette.background
import edu.sunlight.screentime.SunlightPalette.nightAccent
import edu.sunlight.screentime.SunlightPalette.nightSecondary
import edu.sunlight.screentime.SunlightPalette.nightSurface
import edu.sunlight.screentime.SunlightPalette.onAccent
import edu.sunlight.screentime.SunlightPalette.outline
import edu.sunlight.screentime.SunlightPalette.primaryText
import edu.sunlight.screentime.SunlightPalette.secondaryText
import edu.sunlight.screentime.SunlightPalette.sunlightAccent
import edu.sunlight.screentime.SunlightPalette.surface
import java.time.LocalTime

private val panelShape = RoundedCornerShape(24.dp)
private val controlShape = RoundedCornerShape(16.dp)

@Suppress("FunctionNaming")
@Composable
internal fun SunlightScreen(
    state: SunlightScreenState,
    onChooseReleaseTime: () -> Unit,
    onBeginFocus: () -> Unit,
    onEndSession: () -> Unit,
) {
  val active = state.phase.isActive()
  val accent = if (active) nightAccent else sunlightAccent
  Box(Modifier.fillMaxSize().background(if (active) Color.Black else background)) {
    Column(
        modifier =
            Modifier.safeDrawingPadding()
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label("SUNLIGHT", accent, weight = FontWeight.SemiBold)
        Label(if (active) "Focus in progress" else "Your morning starts here", accent)
      }
      SessionOverview(state, accent)
      ReleaseTime(state.releaseTime, active, onChooseReleaseTime)
      if (active) {
        EndSessionAction(accent, onEndSession)
      } else {
        FocusAction(
            if (state.phase == FocusPhase.COMPLETE) "Start another Focus" else "Start Focus",
            onBeginFocus,
        )
        Label(
            "Choose a check time, then start Focus. If that time has passed, the check begins tomorrow.",
            secondaryText,
        )
        Label(
            "Camera access is requested when you start. Images stay in memory and are never saved.",
            secondaryText,
        )
      }
    }
  }
}

@Suppress("FunctionNaming")
@Composable
private fun SessionOverview(state: SunlightScreenState, accent: Color) {
  val active = state.phase.isActive()
  val text = if (active) nightAccent else primaryText
  val secondary = if (active) nightSecondary else secondaryText
  val countdown = state.phase == FocusPhase.FINAL_COUNTDOWN
  Column(
      Modifier.fillMaxWidth()
          .background(if (active) nightSurface else surface, panelShape)
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    BasicText(
        state.phase.title,
        style = TextStyle(color = text, fontSize = 28.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier.semantics { heading() },
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Label(if (countdown) "FOCUS TIME REMAINING" else "LOCAL TIME", secondary)
      BasicText(
          if (countdown) remainingCountdown(state.displayNow, state.countdownEndsAt)
          else currentClock(state.displayNow),
          maxLines = 1,
          autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 56.sp),
          style =
              TextStyle(
                  color = text,
                  fontSize = 56.sp,
                  fontWeight = FontWeight.Light,
                  fontFamily = FontFamily.Monospace,
              ),
      )
    }
    SessionStages(state.phase, accent, secondary)
    BasicText(
        state.statusMessage,
        style = TextStyle(color = text, fontSize = 17.sp, lineHeight = 26.sp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Label(state.phase.guidance, secondary)
  }
}

@Suppress("FunctionNaming")
@Composable
private fun SessionStages(phase: FocusPhase, accent: Color, secondary: Color) {
  val stages = listOf("Focus", "Sunlight", "Finish")
  val current =
      when (phase) {
        FocusPhase.SETUP -> -1
        FocusPhase.WAITING -> 0
        FocusPhase.SEEKING_SUNLIGHT -> 1
        FocusPhase.FINAL_COUNTDOWN -> 2
        FocusPhase.COMPLETE -> stages.size
      }
  FlowRow(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    stages.forEachIndexed { index, title ->
      Column(
          Modifier.widthIn(min = 88.dp * LocalDensity.current.fontScale).weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
            Modifier.fillMaxWidth()
                .height(4.dp)
                .background(if (index <= current) accent else outline, controlShape)
        )
        Label("${index + 1} $title", if (index <= current) accent else secondary)
      }
    }
  }
}

@Suppress("FunctionNaming")
@Composable
private fun ReleaseTime(time: LocalTime?, active: Boolean, onChoose: () -> Unit) {
  val color = if (active) nightAccent else primaryText
  val secondary = if (active) nightSecondary else secondaryText
  val modifier =
      Modifier.fillMaxWidth().border(1.dp, if (active) nightSecondary else outline, controlShape)
  Column(
      (if (active) modifier else modifier.clickable(role = Role.Button, onClick = onChoose))
          .heightIn(min = 72.dp)
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Label("SUNLIGHT CHECK BEGINS", secondary)
    BasicText(
        time?.let(::displayTime) ?: "Choose a time",
        style = TextStyle(color = color, fontSize = 24.sp, fontWeight = FontWeight.Medium),
    )
    if (!active) Label(if (time == null) "Set time" else "Change time", sunlightAccent)
    Label(if (active) "Set for this session" else "Local time · AM / PM", secondary)
  }
}

@Suppress("FunctionNaming")
@Composable
private fun FocusAction(label: String, action: () -> Unit) {
  BasicText(
      label,
      style =
          TextStyle(
              color = onAccent,
              fontSize = 18.sp,
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.Center,
          ),
      modifier =
          Modifier.fillMaxWidth()
              .background(sunlightAccent, controlShape)
              .clickable(role = Role.Button, onClick = action)
              .heightIn(min = 56.dp)
              .padding(16.dp),
  )
}

@Suppress("FunctionNaming")
@Composable
private fun EndSessionAction(accent: Color, onEndSession: () -> Unit) {
  BasicText(
      "Hold to end session",
      style = TextStyle(color = accent, fontSize = 18.sp, textAlign = TextAlign.Center),
      modifier =
          Modifier.fillMaxWidth()
              .border(1.dp, accent, controlShape)
              .combinedClickable(
                  role = Role.Button,
                  onClick = {},
                  onLongClickLabel = "End Focus session",
                  onLongClick = onEndSession,
              )
              .heightIn(min = 56.dp)
              .padding(16.dp),
  )
}

@Suppress("FunctionNaming")
@Composable
private fun Label(value: String, color: Color, weight: FontWeight = FontWeight.Normal) {
  BasicText(
      value,
      style = TextStyle(color = color, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = weight),
  )
}
