package edu.sunlight.screentime

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockTextTest {
  private val utc = ZoneId.of("UTC")
  private val tenPm = Instant.parse("2026-10-08T22:00:00Z")

  @Test
  fun `describes each lock state without overstating it`() {
    assertEquals("Nightly app lock is off.", appLockStatusText(AppLockStatus.Off, utc))
    assertEquals(
        "Selected apps lock at 10:00 PM.",
        appLockStatusText(AppLockStatus.Scheduled(tenPm), utc),
    )
    assertEquals(
        "Locked since 10:00 PM. Apps unlock when your morning sunlight timer finishes.",
        appLockStatusText(AppLockStatus.Locked(tenPm), utc),
    )
    assertEquals(
        "Unlocked when your morning routine finished at 7:20 AM. Apps lock again at 10:00 PM.",
        appLockStatusText(
            AppLockStatus.UnlockedByRoutine(Instant.parse("2026-10-08T07:20:00Z"), tenPm),
            utc,
        ),
    )
    assertEquals(
        "Emergency unlock used at 11:05 PM. This is not a completed routine. " +
            "Apps lock again at 10:00 PM.",
        appLockStatusText(
            AppLockStatus.UnlockedByEmergency(Instant.parse("2026-10-07T23:05:00Z"), tenPm),
            utc,
        ),
    )
  }
}
