package edu.sunlight.screentime

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeDisplayTest {
  @Test
  fun `midnight displays as 12 AM`() {
    assertEquals("12:00 AM", displayTime(LocalTime.MIDNIGHT))
  }

  @Test
  fun `noon displays as 12 PM`() {
    assertEquals("12:00 PM", displayTime(LocalTime.NOON))
  }

  @Test
  fun `afternoon uses 12 hour time with minutes`() {
    assertEquals("1:05 PM", displayTime(LocalTime.of(13, 5)))
  }
}
