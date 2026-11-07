package edu.sunlight.screentime

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SunlightScheduleTest {
  @Test
  fun `normalization sorts times and removes duplicates`() {
    val result =
        normalizeSunlightTimes(
            listOf(LocalTime.of(13, 30), LocalTime.of(7, 0), LocalTime.of(13, 30))
        )

    assertEquals(listOf(LocalTime.of(7, 0), LocalTime.of(13, 30)), result)
  }

  @Test
  fun `stored minute values round trip and corrupt values are ignored`() {
    val decoded = decodeSunlightTimes(setOf("810", "420", "bad", "-1", "1440"))

    assertEquals(listOf(LocalTime.of(7, 0), LocalTime.of(13, 30)), decoded)
    assertEquals(setOf("420", "810"), encodeSunlightTimes(decoded))
  }
}
