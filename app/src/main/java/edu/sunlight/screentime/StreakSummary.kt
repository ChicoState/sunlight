package edu.sunlight.screentime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Home-page presentation of the current and longest morning-routine streaks. */
@Composable
@Suppress("FunctionNaming")
internal fun StreakSummary(
    streak: StreakState,
    modifier: Modifier = Modifier,
    accent: Color = SunlightPalette.sunlightAccent,
) {
  val current = streak.currentStreak
  val headline = if (current == 0) "Start your streak" else "$current-day streak"
  val supportingText =
      if (current == 0) "Complete a morning routine to begin."
      else "Best: ${streak.longestStreak} days"

  Column(
      modifier = modifier.fillMaxWidth().testTag("streak-summary").padding(vertical = 2.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    BasicText(
        headline,
        style =
            TextStyle(
                color = accent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
            ),
    )
    BasicText(
        supportingText,
        style = TextStyle(color = SunlightPalette.secondaryText, fontSize = 14.sp),
    )
  }
}
