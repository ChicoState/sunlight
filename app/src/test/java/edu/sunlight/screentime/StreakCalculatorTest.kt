package edu.sunlight.screentime

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {
  private val monday = LocalDate.of(2026, 11, 2)

  @Test
  fun `first completion starts a streak`() {
    val result = StreakCalculator.recordCompletion(StreakState(), monday)

    assertEquals(StreakState(1, 1, monday), result)
  }

  @Test
  fun `consecutive completion increases current and longest streak`() {
    val first = StreakCalculator.recordCompletion(StreakState(), monday)

    val result = StreakCalculator.recordCompletion(first, monday.plusDays(1))

    assertEquals(StreakState(2, 2, monday.plusDays(1)), result)
  }

  @Test
  fun `missed day restarts current streak but preserves longest streak`() {
    val existing = StreakState(2, 2, monday.plusDays(1))

    val result = StreakCalculator.recordCompletion(existing, monday.plusDays(3))

    assertEquals(StreakState(1, 2, monday.plusDays(3)), result)
  }

  @Test
  fun `same-day completion is ignored`() {
    val existing = StreakState(2, 4, monday.plusDays(1))

    val result = StreakCalculator.recordCompletion(existing, monday.plusDays(1))

    assertEquals(existing, result)
  }

  @Test
  fun `displayed streak expires after yesterday`() {
    val existing = StreakState(3, 3, monday)

    assertEquals(3, StreakCalculator.currentStreakForToday(existing, monday.plusDays(1)))
    assertEquals(0, StreakCalculator.currentStreakForToday(existing, monday.plusDays(2)))
  }
}
