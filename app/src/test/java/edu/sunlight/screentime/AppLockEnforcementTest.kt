package edu.sunlight.screentime

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockEnforcementTest {
  @Test
  fun `unlocked apps are left alone`() {
    assertEquals(BlockAction.NONE, action(locked = false, repeat = false, since = 0))
  }

  @Test
  fun `a locked app is sent home and covered by the block screen`() {
    assertEquals(
        BlockAction.HOME_AND_BLOCK_SCREEN,
        action(locked = true, repeat = false, since = 0),
    )
  }

  @Test
  fun `a locked app reopened right away is still sent home without restarting the block screen`() {
    assertEquals(BlockAction.HOME_ONLY, action(locked = true, repeat = true, since = 100))
  }

  @Test
  fun `the same app reopened after the debounce gets the block screen again`() {
    assertEquals(
        BlockAction.HOME_AND_BLOCK_SCREEN,
        action(
            locked = true,
            repeat = true,
            since = AppLockEnforcement.BLOCK_SCREEN_DEBOUNCE_MILLIS,
        ),
    )
  }

  private fun action(locked: Boolean, repeat: Boolean, since: Long) =
      AppLockEnforcement.action(
          locked = locked,
          repeatOfLastBlock = repeat,
          millisSinceLastBlock = since,
      )
}
