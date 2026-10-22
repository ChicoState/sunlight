package edu.sunlight.screentime

import android.view.KeyEvent
import android.widget.NumberPicker
import android.widget.SeekBar
import android.widget.TimePicker
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MorningRoutineFlowTest {
  @get:Rule val compose = createAndroidComposeRule<SunlightActivity>()

  @Test
  fun durationSavesSurvivesRecreationAndDebugUsesIt() {
    compose.onNodeWithTag("sunlight-duration").performScrollTo().performClick()
    onView(isAssignableFrom(NumberPicker::class.java)).inRoot(isDialog()).check { view, error ->
      if (error != null) throw error
      (view as NumberPicker).value = 1
    }
    onView(withText("Save")).inRoot(isDialog()).perform(click())
    compose.onNodeWithText("1 min").assertIsDisplayed()
    // Reopening verifies the value shown by the actual duration editor.
    compose.onNodeWithTag("sunlight-duration").performClick()
    onView(isAssignableFrom(NumberPicker::class.java)).inRoot(isDialog()).check { view, error ->
      if (error != null) throw error
      assertEquals(1, (view as NumberPicker).value)
      view.value = 40
    }
    onView(withText("Cancel")).inRoot(isDialog()).perform(click())
    compose.onNodeWithText("1 min").assertIsDisplayed()
    compose.activityRule.scenario.recreate()
    compose.waitUntil(5_000) {
      compose.onAllNodesWithText("1 min").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText("1 min").assertIsDisplayed()
    compose.onNodeWithText("Debug: simulate sunlight").performScrollTo().performClick()
    compose.onNodeWithText("DEBUG TEST · SIMULATED LIGHT").performScrollTo().assertIsDisplayed()
    compose.waitUntil(8_000) {
      compose.onAllNodesWithTag("routine-timer").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag("routine-timer").performScrollTo().assertIsDisplayed()
    compose
        .onNodeWithText("Simulated sunlight detected. Your 1-minute timer has started.")
        .performScrollTo()
        .assertIsDisplayed()
    compose.onNodeWithText("Allow camera").assertDoesNotExist()
    compose.onNodeWithText("Hold to exit early").performTouchInput { longClick() }
  }

  @Test
  fun startingBeforeWakeUpWaitsWithoutRequestingCamera() {
    val future = LocalTime.now().plusMinutes(10)
    compose.onNodeWithTag("wake-up-time").performScrollTo().performClick()
    onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).check { view, error ->
      if (error != null) throw error
      (view as TimePicker).apply {
        hour = future.hour
        minute = future.minute
      }
    }
    onView(withText("Set time")).inRoot(isDialog()).perform(click())
    compose.onNodeWithText("Try morning routine").performScrollTo().performClick()
    compose.onNodeWithText("Waiting for wake-up time.").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Allow camera").assertDoesNotExist()
    compose.onNodeWithText("Debug: simulate sunlight").assertDoesNotExist()
    compose.onNodeWithTag("debug-speed").assertDoesNotExist()
    compose.onNodeWithText("Hold to exit early").performTouchInput { longClick() }
  }

  @Test
  fun speedSliderChangesLiveAndAcceleratesCompletion() {
    compose.onNodeWithTag("sunlight-duration").performScrollTo().performClick()
    onView(isAssignableFrom(NumberPicker::class.java)).inRoot(isDialog()).check { view, error ->
      if (error != null) throw error
      (view as NumberPicker).value = 1
    }
    onView(withText("Save")).inRoot(isDialog()).perform(click())
    compose.onNodeWithText("Debug: simulate sunlight").performScrollTo().performClick()
    compose.onNodeWithText("Time speed: 1×").performScrollTo().assertIsDisplayed()
    changeSpeedWithKeys(24)
    compose.onNodeWithText("Time speed: 25×").assertIsDisplayed()
    changeSpeedWithKeys(-24)
    compose.onNodeWithText("Time speed: 1×").assertIsDisplayed()
    changeSpeedWithKeys(99)
    compose.waitUntil(12_000) {
      compose.onAllNodesWithText("Morning routine complete.").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText("Morning routine complete.").performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag("debug-speed").assertDoesNotExist()
    compose.onNodeWithText("Debug: simulate sunlight").performScrollTo().performClick()
    compose.onNodeWithText("Time speed: 1×").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Hold to exit early").performTouchInput { longClick() }
  }

  private fun changeSpeedWithKeys(delta: Int) {
    onView(isAssignableFrom(SeekBar::class.java)).check { view, error ->
      if (error != null) throw error
      val bar = view as SeekBar
      val key = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
      repeat(kotlin.math.abs(delta)) { bar.onKeyDown(key, KeyEvent(KeyEvent.ACTION_DOWN, key)) }
    }
  }
}
