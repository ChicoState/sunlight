package edu.sunlight.screentime

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DebugClockTest {
  private val start = Instant.parse("2026-10-22T07:00:00Z")

  @Test
  fun `speed changes apply live without changing time already elapsed`() {
    val clock = DebugClock(start, 0)
    assertEquals(start.plusSeconds(2), clock.now(2_000_000_000))
    clock.setSpeed(100, 2_000_000_000)
    assertEquals(start.plusSeconds(2), clock.now(2_000_000_000))
    assertEquals(start.plusSeconds(102), clock.now(3_000_000_000))
    clock.setSpeed(1, 3_000_000_000)
    assertEquals(start.plusSeconds(103), clock.now(4_000_000_000))
    clock.setSpeed(25, 4_000_000_000)
    assertEquals(start.plusSeconds(153), clock.now(6_000_000_000))
  }

  @Test
  fun `fractional intervals are retained across rapid adjustments`() {
    val clock = DebugClock(start, 0)
    clock.setSpeed(100, 100_000_000)
    clock.setSpeed(50, 110_000_000)
    clock.setSpeed(1, 120_000_000)
    assertEquals(start.plusMillis(1_610), clock.now(130_000_000))
  }

  @Test
  fun `accelerated clock drives the same light check and routine completion`() {
    val clock = DebugClock(start, 0)
    val routine = MorningRoutine(start, 1)
    routine.advance(clock.now(0))
    routine.acceptLight(255, clock.now(0))
    clock.setSpeed(100, 0)
    routine.acceptLight(255, clock.now(30_000_000))
    assertEquals(FocusPhase.FINAL_COUNTDOWN, routine.phase)
    clock.setSpeed(1, 30_000_000)
    routine.advance(clock.now(1_030_000_000))
    assertEquals(FocusPhase.FINAL_COUNTDOWN, routine.phase)
    clock.setSpeed(100, 1_030_000_000)
    routine.advance(clock.now(1_630_000_000))
    assertEquals(FocusPhase.COMPLETE, routine.phase)
  }

  @Test
  fun `out of range speeds cannot affect the clock`() {
    val clock = DebugClock(start, 0)
    listOf(0, -1, 101, Int.MAX_VALUE).forEach { invalid ->
      assertThrows(IllegalArgumentException::class.java) { clock.setSpeed(invalid, 0) }
    }
    assertEquals(start.plusSeconds(1), clock.now(1_000_000_000))
  }
}
