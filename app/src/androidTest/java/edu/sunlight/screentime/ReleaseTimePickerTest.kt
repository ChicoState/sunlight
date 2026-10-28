package edu.sunlight.screentime

import android.widget.TimePicker
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
  fun selectingAfternoonTimeAndCancellingAnEditPreservesSelection() {
    openPicker()
    onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).check { view, error ->
      if (error != null) throw error
      val picker = view as TimePicker
      assertFalse(picker.is24HourView)
      picker.hour = 13
      picker.minute = 5
    }
    onView(withText("Set time")).inRoot(isDialog()).perform(click())
    compose.onNodeWithText("1:05 PM").performScrollTo().assertIsDisplayed()

    openPicker()
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

  private fun openPicker() {
    compose.onNodeWithText("Wake-up time").performScrollTo().performClick()
  }
}
