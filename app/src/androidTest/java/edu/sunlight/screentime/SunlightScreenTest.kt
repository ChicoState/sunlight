package edu.sunlight.screentime

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
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
    compose.onNodeWithTag("wake-up-time").performScrollTo().performClick()
    compose.onNodeWithText("Try morning routine").performScrollTo().performClick()
    compose.runOnIdle {
      assertEquals(1, timeChoices)
      assertEquals(1, starts)
    }
  }

  @Test
  fun activeSessionRequiresHoldToEnd() {
    var ends = 0
    compose.setContent { SunlightScreen(screenState(FocusPhase.WAITING), {}, {}, { ends++ }) }
    val end = compose.onNodeWithText("Hold to exit early")
    end.performClick()
    compose.runOnIdle { assertEquals(0, ends) }
    end.performTouchInput { longClick() }
    compose.runOnIdle { assertEquals(1, ends) }
    compose.onNodeWithText("Try morning routine").assertDoesNotExist()
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
        .onNodeWithText(
            "Preview: the camera is off during this timer. Repeated sunlight checks are not available yet."
        )
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun completedSessionCanStartAgain() {
    var starts = 0
    compose.setContent { SunlightScreen(screenState(FocusPhase.COMPLETE), {}, { starts++ }, {}) }
    compose.onNodeWithText("Morning routine complete.").assertIsDisplayed()
    compose.onNodeWithText("Try morning routine again").performScrollTo().performClick()
    compose.runOnIdle { assertEquals(1, starts) }
  }

  @Test
  fun bottomNavigationOpensSeparatePagesAndReturnsHome() {
    compose.setContent { SunlightScreen(screenState(FocusPhase.SETUP), {}, {}, {}) }
    listOf("Blocked apps", "Alarms", "Settings").forEach { page ->
      compose.onNodeWithTag("tab-$page").assertIsDisplayed().performClick().assertIsSelected()
      compose.onNodeWithTag("page-$page").assertIsDisplayed()
      compose.onNodeWithText("Try morning routine").assertDoesNotExist()
    }
    compose.onNodeWithTag("tab-Home").performClick().assertIsSelected()
    compose.onNodeWithText("Wake-up time").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun earlyExitRemainsAvailableAwayFromHome() {
    var ends = 0
    compose.setContent {
      SunlightScreen(screenState(FocusPhase.FINAL_COUNTDOWN), {}, {}, { ends++ })
    }
    compose.onNodeWithTag("tab-Settings").performClick()
    compose.onNodeWithText("Hold to exit early").performTouchInput { longClick() }
    compose.runOnIdle { assertEquals(1, ends) }
  }

  @Test
  fun durationIsEditableAndNumberedStagesAreGone() {
    var edits = 0
    compose.setContent {
      SunlightScreen(
          screenState(FocusPhase.SETUP).copy(sunlightMinutes = 25),
          {},
          {},
          {},
          onChooseDuration = { edits++ },
      )
    }
    compose.onNodeWithTag("sunlight-duration").performScrollTo().performClick()
    compose.onNodeWithText("25 min").assertIsDisplayed()
    compose.onNodeWithText("1 Wake up").assertDoesNotExist()
    compose.onNodeWithText("2 Sunlight").assertDoesNotExist()
    compose.runOnIdle { assertEquals(1, edits) }
  }

  @Test
  fun debugActionIsExplicitAndAbsentFromReleaseUi() {
    compose.setContent { SunlightScreen(screenState(FocusPhase.SETUP), {}, {}, {}) }
    compose.onNodeWithText("Debug: simulate sunlight").assertDoesNotExist()
  }

  @Test
  fun debugButtonStartsTheInjectedLightTest() {
    var tests = 0
    compose.setContent {
      SunlightScreen(
          screenState(FocusPhase.SETUP).copy(debugAvailable = true),
          {},
          {},
          {},
          onDebugSunlight = { tests++ },
      )
    }
    compose.onNodeWithText("Debug: simulate sunlight").performScrollTo().performClick()
    compose.runOnIdle { assertEquals(1, tests) }
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
