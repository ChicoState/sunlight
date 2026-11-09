package edu.sunlight.screentime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueLightFilterPolicyTest {
  @Test
  fun `filter runs only during an authorized background focus phase`() {
    assertTrue(
        FocusPhase.WAITING.shouldRunBlueLightFilter(
            overlayAuthorized = true,
            appInBackground = true,
        )
    )
    assertTrue(
        FocusPhase.SEEKING_SUNLIGHT.shouldRunBlueLightFilter(
            overlayAuthorized = true,
            appInBackground = true,
        )
    )
    assertTrue(
        FocusPhase.FINAL_COUNTDOWN.shouldRunBlueLightFilter(
            overlayAuthorized = true,
            appInBackground = true,
        )
    )

    assertFalse(
        FocusPhase.SETUP.shouldRunBlueLightFilter(overlayAuthorized = true, appInBackground = true)
    )
    assertFalse(
        FocusPhase.COMPLETE.shouldRunBlueLightFilter(
            overlayAuthorized = true,
            appInBackground = true,
        )
    )
    assertFalse(
        FocusPhase.WAITING.shouldRunBlueLightFilter(
            overlayAuthorized = false,
            appInBackground = true,
        )
    )
    assertFalse(
        FocusPhase.WAITING.shouldRunBlueLightFilter(
            overlayAuthorized = true,
            appInBackground = false,
        )
    )
  }

  @Test
  fun `filter does not run for setup or completed phases`() {
    assertFalse(
        FocusPhase.SETUP.shouldRunBlueLightFilter(overlayAuthorized = true, appInBackground = true)
    )
    assertFalse(
        FocusPhase.COMPLETE.shouldRunBlueLightFilter(
            overlayAuthorized = true,
            appInBackground = true,
        )
    )
  }
}
