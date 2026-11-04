package edu.sunlight.screentime

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockLaunchDebounceTest {
  @Test
  fun `rapid reentry waits only until the original launch interval ends`() {
    val debounce = AppLockLaunchDebounce()
    val video = "com.example.video"
    assertEquals(0L, debounce.remainingMillis(video, 1_000L))
    debounce.launched(video, 1_000L)
    assertEquals(550L, debounce.remainingMillis(video, 1_200L))
    assertEquals(1L, debounce.remainingMillis(video, 1_749L))
    assertEquals(0L, debounce.remainingMillis(video, 1_750L))
    assertEquals(0L, debounce.remainingMillis(video, 2_000L))
  }

  @Test
  fun `switching to another locked package does not wait`() {
    val debounce = AppLockLaunchDebounce()
    debounce.launched("com.example.video", 1_000L)
    assertEquals(0L, debounce.remainingMillis("com.example.chat", 1_100L))
  }
}
