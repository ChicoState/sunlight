package edu.sunlight.screentime

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MorningRoutineTest {
  private val wakeAt = Instant.parse("2026-10-22T07:00:00Z")

  @Test
  fun `light before wake-up cannot start the timer or contribute to detection`() {
    val routine = MorningRoutine(wakeAt, 5)
    routine.acceptLight(255, wakeAt.minusSeconds(4))
    routine.acceptLight(255, wakeAt.minusSeconds(1))
    assertEquals(FocusPhase.WAITING, routine.phase)
    assertNull(routine.countdownEndsAt)
    routine.advance(wakeAt)
    assertEquals(FocusPhase.SEEKING_SUNLIGHT, routine.phase)
    routine.acceptLight(255, wakeAt)
    assertNull(routine.countdownEndsAt)
  }

  @Test
  fun `camera and simulated brightness start the selected duration after sustained light`() {
    val routine = MorningRoutine(wakeAt, 5)
    routine.advance(wakeAt)
    routine.acceptLight(255, wakeAt)
    routine.acceptLight(255, wakeAt.plusSeconds(3))
    assertEquals(FocusPhase.FINAL_COUNTDOWN, routine.phase)
    assertEquals(wakeAt.plusSeconds(303), routine.countdownEndsAt)
    routine.acceptLight(255, wakeAt.plusSeconds(5))
    assertEquals(wakeAt.plusSeconds(303), routine.countdownEndsAt)
    routine.advance(wakeAt.plusSeconds(302))
    assertEquals(FocusPhase.FINAL_COUNTDOWN, routine.phase)
    routine.advance(wakeAt.plusSeconds(303))
    assertEquals(FocusPhase.COMPLETE, routine.phase)
  }

  @Test
  fun `darkness interrupts the initial light check`() {
    val routine = MorningRoutine(wakeAt, 1)
    routine.advance(wakeAt)
    routine.acceptLight(255, wakeAt)
    routine.acceptLight(0, wakeAt.plusSeconds(2))
    routine.acceptLight(255, wakeAt.plusSeconds(3))
    routine.acceptLight(255, wakeAt.plusSeconds(5))
    assertEquals(FocusPhase.SEEKING_SUNLIGHT, routine.phase)
    routine.acceptLight(255, wakeAt.plusSeconds(6))
    assertEquals(wakeAt.plusSeconds(66), routine.countdownEndsAt)
  }

  @Test
  fun `camera stops cannot carry a partial light check into the next start`() {
    val routine = MorningRoutine(wakeAt, 1)
    routine.advance(wakeAt)
    routine.acceptLight(255, wakeAt)
    routine.resetLightCheck()
    routine.acceptLight(255, wakeAt.plusSeconds(20))
    assertEquals(FocusPhase.SEEKING_SUNLIGHT, routine.phase)
  }

  @Test
  fun `duration is bounded and corrupt saved values fall back to default`() {
    listOf(0, -1, 121, Int.MAX_VALUE).forEach { minutes ->
      assertThrows(IllegalArgumentException::class.java) { MorningRoutine(wakeAt, minutes) }
      assertEquals(15, sunlightMinutesOrDefault(minutes))
    }
    assertEquals(15, sunlightMinutesOrDefault(null))
    assertEquals(1, sunlightMinutesOrDefault(1))
    assertEquals(120, sunlightMinutesOrDefault(120))
  }
}
