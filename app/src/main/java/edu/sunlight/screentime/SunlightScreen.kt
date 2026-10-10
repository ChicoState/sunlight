package edu.sunlight.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val INACTIVE_BACKGROUND_ARGB = 0xff171717
private const val NIGHT_RED_ARGB = 0xffff5c5c
private const val SECONDS_PER_MINUTE = 60
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal data class SunlightScreenState(
    val releaseTime: LocalTime?,
    val phase: FocusPhase,
    val statusMessage: String,
    val displayNow: Instant,
    val countdownEndsAt: Instant?,
)

@Suppress("FunctionNaming")
@Composable
internal fun SunlightScreen(
    state: SunlightScreenState,
    onChooseReleaseTime: () -> Unit,
    onBeginFocus: () -> Unit,
    onEndSession: () -> Unit,
) {
  val active = state.phase.isActive()
  val foreground = if (active) Color(NIGHT_RED_ARGB) else Color.White
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(if (active) Color.Black else Color(INACTIVE_BACKGROUND_ARGB))
              .padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    BasicText(
        currentClock(state.displayNow),
        style = TextStyle(color = foreground, fontSize = 52.sp, fontWeight = FontWeight.Light),
    )
    Spacer(Modifier.height(32.dp))
    BasicText(
        state.statusMessage,
        style = TextStyle(color = foreground, fontSize = 20.sp),
        modifier = Modifier.semantics { contentDescription = state.statusMessage },
    )
    Spacer(Modifier.height(32.dp))
    if (!active) {
      FocusAction("Choose release time", foreground, onChooseReleaseTime)
      Spacer(Modifier.height(16.dp))
      BasicText(
          state.releaseTime?.let { "Check begins at ${displayTime(it)}" }
              ?: "No release time selected",
          style = TextStyle(color = Color.LightGray, fontSize = 16.sp),
      )
      Spacer(Modifier.height(24.dp))
      FocusAction("Start Focus", foreground, onBeginFocus)
    } else {
      if (state.phase == FocusPhase.FINAL_COUNTDOWN) {
        BasicText(
            "${remainingCountdown(state.displayNow, state.countdownEndsAt)} remaining",
            style = TextStyle(color = foreground, fontSize = 18.sp),
        )
        Spacer(Modifier.height(24.dp))
      }
      BasicText(
          "Hold to end session",
          style = TextStyle(color = foreground, fontSize = 18.sp),
          modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onEndSession),
      )
    }
  }
}

@Suppress("FunctionNaming")
@Composable
private fun FocusAction(label: String, color: Color, action: () -> Unit) {
  BasicText(
      label,
      style = TextStyle(color = color, fontSize = 20.sp),
      modifier =
          Modifier.fillMaxWidth()
              .padding(16.dp)
              .combinedClickable(onClick = action, onLongClick = action),
  )
}

private fun FocusPhase.isActive(): Boolean =
    this == FocusPhase.WAITING ||
        this == FocusPhase.SEEKING_SUNLIGHT ||
        this == FocusPhase.FINAL_COUNTDOWN

private fun currentClock(now: Instant): String =
    now.atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)

private fun remainingCountdown(now: Instant, countdownEndsAt: Instant?): String {
  val seconds = countdownEndsAt?.let { Duration.between(now, it).seconds.coerceAtLeast(0) } ?: 0
  return String.format(
      Locale.ROOT,
      "%02d:%02d",
      seconds / SECONDS_PER_MINUTE,
      seconds % SECONDS_PER_MINUTE,
  )
}

internal fun displayTime(time: LocalTime): String = time.format(timeFormatter)

internal enum class FocusPhase {
  SETUP,
  WAITING,
  SEEKING_SUNLIGHT,
  FINAL_COUNTDOWN,
  COMPLETE,
}
