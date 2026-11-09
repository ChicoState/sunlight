package edu.sunlight.screentime

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPlannerTest {
  @Test
  fun `chooses tomorrow when the release time has passed`() {
    val zone = ZoneId.of("UTC")
    val result =
        SessionPlanner.nextRelease(Instant.parse("2026-10-07T08:00:00Z"), LocalTime.of(7, 0), zone)

    assertEquals(Instant.parse("2026-10-08T07:00:00Z"), result)
  }

  @Test
  fun `chooses today when the release time remains`() {
    val zone = ZoneId.of("UTC")
    val result =
        SessionPlanner.nextRelease(Instant.parse("2026-10-07T06:00:00Z"), LocalTime.of(7, 0), zone)

    assertEquals(Instant.parse("2026-10-07T07:00:00Z"), result)
  }
}
