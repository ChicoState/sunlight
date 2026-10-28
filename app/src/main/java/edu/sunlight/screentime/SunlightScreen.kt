package edu.sunlight.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun SunlightScreen(
    state: SunlightScreenState,
    onChooseReleaseTime: () -> Unit,
    onBeginFocus: () -> Unit,
    onEndSession: () -> Unit,
    onChooseDuration: () -> Unit = {},
    onDebugSunlight: () -> Unit = {},
    onEnableCamera: () -> Unit = {},
    onDebugSpeedChange: (Int) -> Unit = {},
) {
  var page by rememberSaveable { mutableStateOf("Home") }
  val active = state.phase.isActive()
  val accent = if (active) SunlightPalette.nightAccent else SunlightPalette.sunlightAccent
  Column(
      Modifier.fillMaxSize()
          .background(if (active) Color.Black else SunlightPalette.background)
          .safeDrawingPadding()
  ) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
      if (page == "Home") {
        Column(
            Modifier.widthIn(max = 560.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(
                "Sunlight",
                style =
                    TextStyle(
                        color = accent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                modifier = Modifier.semantics { heading() },
            )
            RoutineLabel(
                "A brighter start to your day.",
                if (active) SunlightPalette.nightSecondary else SunlightPalette.secondaryText,
            )
          }
          MorningRoutinePanel(state, onChooseReleaseTime, onChooseDuration, onDebugSpeedChange)
          if (!active) {
            HomeActions(state, onBeginFocus, onDebugSunlight)
          }
          if (state.cameraPermissionNeeded && !state.debugMode) {
            RoutineAction("Allow camera", onEnableCamera, filled = false, accent = accent)
          }
        }
      } else {
        BasicText(
            page,
            style = TextStyle(color = accent, fontSize = 28.sp),
            modifier = Modifier.padding(24.dp).testTag("page-$page").semantics { heading() },
        )
      }
    }
    if (active) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        EndSessionAction(accent, onEndSession)
      }
    }
    BottomNavigation(page, active) { page = it }
  }
}

@Suppress("FunctionNaming")
@Composable
private fun HomeActions(state: SunlightScreenState, onBegin: () -> Unit, onDebug: () -> Unit) {
  RoutineAction(
      if (state.phase == FocusPhase.COMPLETE) "Try morning routine again"
      else "Try morning routine",
      onBegin,
  )
  RoutineLabel(
      "Preview: no alarm or app blocking yet. One light check starts your timer.",
      SunlightPalette.secondaryText,
  )
  if (state.debugAvailable) {
    RoutineAction("Debug: simulate sunlight", onDebug, filled = false)
    RoutineLabel(
        "Starts a test now using simulated camera brightness. Your saved wake-up time stays the same.",
        SunlightPalette.secondaryText,
    )
  }
}

@Suppress("FunctionNaming")
@Composable
internal fun RoutineAction(
    label: String,
    action: () -> Unit,
    filled: Boolean = true,
    accent: Color = SunlightPalette.sunlightAccent,
) {
  val shape = RoundedCornerShape(16.dp)
  BasicText(
      label,
      style =
          TextStyle(
              color = if (filled) SunlightPalette.onAccent else accent,
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.Center,
          ),
      modifier =
          Modifier.fillMaxWidth()
              .then(
                  if (filled) Modifier.background(accent, shape)
                  else Modifier.border(1.dp, accent, shape)
              )
              .clickable(role = Role.Button, onClick = action)
              .heightIn(min = 56.dp)
              .padding(16.dp),
  )
}

@Suppress("FunctionNaming")
@Composable
private fun EndSessionAction(accent: Color, onEndSession: () -> Unit) {
  BasicText(
      "Hold to exit early",
      style = TextStyle(color = accent, fontSize = 16.sp, textAlign = TextAlign.Center),
      modifier =
          Modifier.fillMaxWidth()
              .border(1.dp, accent, RoundedCornerShape(16.dp))
              .combinedClickable(
                  role = Role.Button,
                  onClick = {},
                  onLongClickLabel = "Exit morning routine preview",
                  onLongClick = onEndSession,
              )
              .heightIn(min = 56.dp)
              .padding(16.dp),
  )
}

@Suppress("FunctionNaming")
@Composable
internal fun RoutineLabel(value: String, color: Color) {
  BasicText(value, style = TextStyle(color = color, fontSize = 14.sp, lineHeight = 22.sp))
}
