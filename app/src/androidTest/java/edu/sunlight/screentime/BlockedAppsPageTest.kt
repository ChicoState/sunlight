package edu.sunlight.screentime

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BlockedAppsPageTest {
  @get:Rule val compose = createComposeRule()

  private val apps =
      listOf(
          LaunchableApp("com.example.chat", "Chat"),
          LaunchableApp("com.example.video", "Video"),
      )

  private fun state(locked: Boolean = false, accessEnabled: Boolean = true) =
      BlockedAppsState(
          settings =
              AppLockSettings(
                  enabled = true,
                  lockTime = LocalTime.of(22, 0),
                  packages = setOf("com.example.video"),
              ),
          statusText = if (locked) "Locked since 10:00 PM." else "Selected apps lock at 10:00 PM.",
          locked = locked,
          accessEnabled = accessEnabled,
          apps = apps,
      )

  @Test
  fun pickerShowsAppsAsCheckboxesAndReportsToggles() {
    val toggles = mutableListOf<Pair<String, Boolean>>()
    var timeEdits = 0
    compose.setContent {
      BlockedAppsPage(
          state(),
          night = false,
          actions =
              BlockedAppsActions(
                  onToggleApp = { pkg, checked -> toggles += pkg to checked },
                  onChooseLockTime = { timeEdits++ },
              ),
      )
    }
    compose.onNodeWithTag("page-Blocked apps").assertIsDisplayed()
    compose.onNodeWithText("Selected apps lock at 10:00 PM.").assertIsDisplayed()
    compose.onNodeWithTag("app-com.example.video").performScrollTo().assertIsOn()
    compose.onNodeWithTag("app-com.example.chat").performScrollTo().assertIsOff().performClick()
    compose.onNodeWithTag("lock-time").performClick()
    compose.onNodeWithText("10:00 PM").assertIsDisplayed()
    compose.runOnIdle {
      assertEquals(listOf("com.example.chat" to true), toggles)
      assertEquals(1, timeEdits)
    }
  }

  @Test
  fun missingAccessShowsSetupRowWithSettingsLink() {
    var opens = 0
    compose.setContent {
      BlockedAppsPage(
          state(accessEnabled = false),
          night = false,
          actions = BlockedAppsActions(onOpenAccessibilitySettings = { opens++ }),
      )
    }
    compose.onNodeWithTag("access-setup").assertIsDisplayed()
    compose.onNodeWithText("Open Accessibility settings").performScrollTo().performClick()
    compose.runOnIdle { assertEquals(1, opens) }
  }

  @Test
  fun lockFreezesEditsAndEmergencyUnlockNeedsHoldThenDelay() {
    var unlocks = 0
    compose.setContent {
      BlockedAppsPage(
          state(locked = true),
          night = true,
          actions = BlockedAppsActions(onEmergencyUnlock = { unlocks++ }),
      )
    }
    compose.onNodeWithTag("lock-enabled").assertIsNotEnabled()
    compose.onNodeWithTag("app-com.example.chat").performScrollTo().assertIsNotEnabled()
    val unlock = compose.onNodeWithTag("emergency-unlock").performScrollTo()
    unlock.performClick()
    compose.runOnIdle { assertEquals(0, unlocks) }

    compose.mainClock.autoAdvance = false
    unlock.performTouchInput { longClick() }
    compose.mainClock.advanceTimeBy(5_000)
    compose.onNodeWithText("Emergency unlock in", substring = true).assertIsDisplayed()
    compose.runOnIdle { assertEquals(0, unlocks) }
    compose.mainClock.advanceTimeBy(6_000)
    compose.runOnIdle { assertEquals(1, unlocks) }
  }

  @Test
  fun tappingDuringCountdownCancelsEmergencyUnlock() {
    var unlocks = 0
    compose.setContent {
      AppBlockedScreen(
          appLabel = "Video",
          statusText = "Locked since 10:00 PM.",
          onGoHome = {},
          onOpenSunlight = {},
          onEmergencyUnlock = { unlocks++ },
      )
    }
    compose.onNodeWithText("Video is locked").assertIsDisplayed()
    compose.mainClock.autoAdvance = false
    val unlock = compose.onNodeWithTag("emergency-unlock").performScrollTo()
    unlock.performTouchInput { longClick() }
    compose.mainClock.advanceTimeBy(2_000)
    unlock.performClick()
    compose.mainClock.advanceTimeBy(15_000)
    compose.runOnIdle { assertEquals(0, unlocks) }
    compose.onNodeWithText("Hold for emergency unlock").assertIsDisplayed()
  }
}
