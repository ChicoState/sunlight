package edu.sunlight.screentime

import android.widget.TimePicker
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ReleaseTimePickerTest {
  @get:Rule val compose = createAndroidComposeRule<SunlightActivity>()

  @Test
  fun addingAfternoonTimeAndCancellingAnEditPreservesSelection() {
    openAddPicker()
    onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).check { view, error ->
      if (error != null) throw error
      val picker = view as TimePicker
      assertFalse(picker.is24HourView)
      picker.hour = 13
      picker.minute = 5
    }
    onView(withText("Save")).inRoot(isDialog()).perform(click())
    compose.onNodeWithText("1:05 PM").performScrollTo().assertIsDisplayed()

    compose.activityRule.scenario.recreate()
    compose.onNodeWithTag("tab-Alarms").performClick()
    compose.waitUntil(5_000) {
      compose.onAllNodesWithTag("sunlight-time-13-5").fetchSemanticsNodes().isNotEmpty()
    }

    compose.onNodeWithTag("sunlight-time-13-5").performClick()
    onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).check { view, error ->
      if (error != null) throw error
      val picker = view as TimePicker
      assertEquals(13, picker.hour)
      assertEquals(5, picker.minute)
      picker.hour = 0
    }
    onView(withText("Cancel")).inRoot(isDialog()).perform(click())
    compose.onNodeWithText("1:05 PM").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun removingATimePersistsAfterRecreation() {
    openAddPicker()
    setPickerTime(14, 17)
    onView(withText("Save")).inRoot(isDialog()).perform(click())

    compose.onNodeWithText("Add Sunlight Time").performClick()
    setPickerTime(14, 18)
    onView(withText("Save")).inRoot(isDialog()).perform(click())

    compose.onNodeWithContentDescription("Remove 2:17 PM").performClick()
    compose.onNodeWithTag("sunlight-time-14-17").assertDoesNotExist()

    compose.activityRule.scenario.recreate()
    compose.onNodeWithTag("tab-Alarms").performClick()
    compose.waitUntil(5_000) {
      compose.onAllNodesWithTag("sunlight-time-14-18").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag("sunlight-time-14-17").assertDoesNotExist()
  }

  private fun openAddPicker() {
    compose.onNodeWithText("Add Sunlight Time").performScrollTo().performClick()
  }

  private fun setPickerTime(hour: Int, minute: Int) {
    onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).check { view, error ->
      if (error != null) throw error
      (view as TimePicker).apply {
        this.hour = hour
        this.minute = minute
      }
    }
  }
}
