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
        SessionPlanner.nextRelease(
            Instant.parse("2026-10-07T18:00:00Z"),
            listOf(LocalTime.of(7, 0), LocalTime.of(13, 30)),
            zone,
        )

    assertEquals(Instant.parse("2026-10-08T07:00:00Z"), result)
  }

  @Test
  fun `chooses the next of several remaining times today`() {
    val zone = ZoneId.of("UTC")
    val result =
        SessionPlanner.nextRelease(
            Instant.parse("2026-10-07T08:00:00Z"),
            listOf(LocalTime.of(18, 0), LocalTime.of(7, 0), LocalTime.of(13, 30)),
            zone,
        )

    assertEquals(Instant.parse("2026-10-07T13:30:00Z"), result)
  }

  @Test
  fun `returns null when no sunlight times exist`() {
    val result =
        SessionPlanner.nextRelease(
            Instant.parse("2026-10-07T08:00:00Z"),
            emptyList(),
            ZoneId.of("UTC"),
        )

    assertEquals(null, result)
  }
}
