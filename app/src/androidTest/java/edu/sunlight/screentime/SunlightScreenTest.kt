package edu.sunlight.screentime

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SunlightScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun setupKeepsTimePickerAndStartAccessible() {
    var timeChoices = 0
    var starts = 0
    compose.setContent {
      SunlightScreen(screenState(FocusPhase.SETUP), { timeChoices++ }, { starts++ }, {})
    }
    compose.onNodeWithText("Change", substring = true).performScrollTo().performClick()
    compose.onNodeWithText("Start Focus").performScrollTo().performClick()
    compose.runOnIdle {
      assertEquals(1, timeChoices)
      assertEquals(1, starts)
    }
  }

  @Test
  fun activeSessionRequiresHoldToEnd() {
    var ends = 0
    compose.setContent { SunlightScreen(screenState(FocusPhase.WAITING), {}, {}, { ends++ }) }
    val end = compose.onNodeWithText("Hold to end session").performScrollTo()
    end.performClick()
    compose.runOnIdle { assertEquals(0, ends) }
    end.performTouchInput { longClick() }
    compose.runOnIdle { assertEquals(1, ends) }
    compose.onNodeWithText("Start Focus").assertDoesNotExist()
    compose.onNodeWithText("Change").assertDoesNotExist()
  }

  @Test
  fun sunlightCheckShowsCameraGuidance() {
    compose.setContent { SunlightScreen(screenState(FocusPhase.SEEKING_SUNLIGHT), {}, {}, {}) }
    compose.onNodeWithText("Find the morning light.").assertIsDisplayed()
    compose
        .onNodeWithText("Keep the front camera uncovered.", substring = true)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun finalCountdownShowsRemainingTime() {
    compose.setContent { SunlightScreen(screenState(FocusPhase.FINAL_COUNTDOWN), {}, {}, {}) }
    compose.onNodeWithText("14:59").assertIsDisplayed()
    compose
        .onNodeWithText("Light check complete. The camera is now off.")
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun completedSessionCanStartAgain() {
    var starts = 0
    compose.setContent { SunlightScreen(screenState(FocusPhase.COMPLETE), {}, { starts++ }, {}) }
    compose.onNodeWithText("Good morning.").assertIsDisplayed()
    compose.onNodeWithText("Start another Focus").performScrollTo().performClick()
    compose.runOnIdle { assertEquals(1, starts) }
  }

  private fun screenState(phase: FocusPhase): SunlightScreenState {
    val now = Instant.parse("2026-10-21T06:00:00Z")
    return SunlightScreenState(
        releaseTime = LocalTime.of(7, 0),
        phase = phase,
        statusMessage = "Session status",
        displayNow = now,
        countdownEndsAt = now.plusSeconds(899),
    )
  }
}
