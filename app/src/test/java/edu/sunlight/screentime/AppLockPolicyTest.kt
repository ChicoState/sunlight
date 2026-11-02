package edu.sunlight.screentime

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
  private val utc = ZoneId.of("UTC")
  private val pacific = ZoneId.of("America/Los_Angeles")
  private val tenPm = LocalTime.of(22, 0)
  private val video = "com.example.video"

  private fun settings(
      armedAt: String = "2026-10-07T12:00:00Z",
      lockTime: LocalTime = tenPm,
      lastPass: String? = null,
      lastOverride: String? = null,
  ) =
      AppLockSettings(
          enabled = true,
          lockTime = lockTime,
          packages = setOf(video),
          armedAt = Instant.parse(armedAt),
          lastRoutineCompletedAt = lastPass?.let(Instant::parse),
          lastEmergencyUnlockAt = lastOverride?.let(Instant::parse),
      )

  private fun locked(settings: AppLockSettings, now: String, zone: ZoneId = utc) =
      AppLockPolicy.isLocked(video, settings, Instant.parse(now), zone)

  @Test
  fun `locks a selected app from the lock time`() {
    assertFalse(locked(settings(), "2026-10-07T21:59:59Z"))
    assertTrue(locked(settings(), "2026-10-07T22:00:00Z"))
  }

  @Test
  fun `lock continues across midnight until the routine completes`() {
    assertTrue(locked(settings(), "2026-10-08T00:30:00Z"))
    assertTrue(locked(settings(), "2026-10-08T07:10:00Z"))
    val completed = settings(lastPass = "2026-10-08T07:20:00Z")
    assertTrue(locked(completed, "2026-10-08T07:19:59Z"))
    assertFalse(locked(completed, "2026-10-08T07:20:00Z"))
    assertFalse(locked(completed, "2026-10-08T21:59:59Z"))
  }

  @Test
  fun `a completed routine before the lock time does not pre-unlock that night`() {
    val earlyPass = settings(lastPass = "2026-10-07T21:00:00Z")
    assertTrue(locked(earlyPass, "2026-10-07T22:00:00Z"))
  }

  @Test
  fun `the lock stays on for consecutive nights without a completed routine`() {
    val passed = settings(lastPass = "2026-10-08T07:00:00Z")
    assertTrue(locked(passed, "2026-10-09T15:00:00Z"))
  }

  @Test
  fun `lock time just after midnight locks for the rest of that day`() {
    val earlyMorning = settings(lockTime = LocalTime.of(0, 30))
    assertFalse(locked(earlyMorning, "2026-10-08T00:29:00Z"))
    assertTrue(locked(earlyMorning, "2026-10-08T23:00:00Z"))
  }

  @Test
  fun `settings changed after the lock time take effect next night`() {
    val armedLate = settings(armedAt = "2026-10-07T23:00:00Z")
    assertFalse(locked(armedLate, "2026-10-07T23:30:00Z"))
    assertTrue(locked(armedLate, "2026-10-08T22:00:00Z"))
  }

  @Test
  fun `emergency unlock lasts until the next lock time and is not a sunlight pass`() {
    val override = settings(lastOverride = "2026-10-07T23:00:00Z")
    assertFalse(locked(override, "2026-10-08T06:00:00Z"))
    assertTrue(locked(override, "2026-10-08T22:00:00Z"))
    val status = AppLockPolicy.status(override, Instant.parse("2026-10-08T06:00:00Z"), utc)
    assertEquals(
        AppLockStatus.UnlockedByEmergency(
            Instant.parse("2026-10-07T23:00:00Z"),
            Instant.parse("2026-10-08T22:00:00Z"),
        ),
        status,
    )
  }

  @Test
  fun `unselected disabled and protected apps are never locked`() {
    val now = Instant.parse("2026-10-07T23:00:00Z")
    assertFalse(AppLockPolicy.isLocked("com.example.other", settings(), now, utc))
    assertFalse(AppLockPolicy.isLocked(video, settings().copy(enabled = false), now, utc))
    val withSettings = settings().copy(packages = setOf(video, "com.android.settings"))
    assertFalse(AppLockPolicy.isLocked("com.android.settings", withSettings, now, utc))
    assertFalse(
        AppLockPolicy.isLocked(
            video,
            settings(),
            now,
            utc,
            protectedPackages = setOf(video),
        )
    )
  }

  @Test
  fun `status reports scheduled locked and unlocked states`() {
    val scheduled = AppLockPolicy.status(settings(), Instant.parse("2026-10-07T12:00:00Z"), utc)
    assertEquals(AppLockStatus.Scheduled(Instant.parse("2026-10-07T22:00:00Z")), scheduled)
    val active = AppLockPolicy.status(settings(), Instant.parse("2026-10-08T01:00:00Z"), utc)
    assertEquals(AppLockStatus.Locked(Instant.parse("2026-10-07T22:00:00Z")), active)
    val passed =
        AppLockPolicy.status(
            settings(lastPass = "2026-10-08T07:20:00Z"),
            Instant.parse("2026-10-08T08:00:00Z"),
            utc,
        )
    assertEquals(
        AppLockStatus.UnlockedByRoutine(
            Instant.parse("2026-10-08T07:20:00Z"),
            Instant.parse("2026-10-08T22:00:00Z"),
        ),
        passed,
    )
    assertEquals(
        AppLockStatus.Off,
        AppLockPolicy.status(settings().copy(packages = emptySet()), Instant.now(), utc),
    )
    assertEquals(
        AppLockStatus.Off,
        AppLockPolicy.status(settings().copy(enabled = false), Instant.now(), utc),
    )
  }

  @Test
  fun `edits are frozen only while locked`() {
    assertTrue(AppLockPolicy.canEdit(settings(), Instant.parse("2026-10-07T12:00:00Z"), utc))
    assertFalse(AppLockPolicy.canEdit(settings(), Instant.parse("2026-10-07T23:00:00Z"), utc))
  }

  @Test
  fun `spring-forward gap moves a missing lock time to after the gap`() {
    // 2026-03-08 02:00 PST jumps to 03:00 PDT, so 02:30 does not exist that day.
    val gap = settings(armedAt = "2026-03-01T00:00:00Z", lockTime = LocalTime.of(2, 30))
    val passed = gap.copy(lastRoutineCompletedAt = Instant.parse("2026-03-07T18:00:00Z"))
    // 03:15 PDT is before the shifted 03:30 PDT lock start.
    assertFalse(locked(passed, "2026-03-08T10:15:00Z", pacific))
    assertTrue(locked(passed, "2026-03-08T10:30:00Z", pacific))
  }

  @Test
  fun `fall-back overlap locks at the first occurrence of the lock time`() {
    // 2026-11-01 01:30 occurs at 08:30Z (PDT) and again at 09:30Z (PST).
    val overlap = settings(armedAt = "2026-10-01T00:00:00Z", lockTime = LocalTime.of(1, 30))
    val passed = overlap.copy(lastRoutineCompletedAt = Instant.parse("2026-10-31T18:00:00Z"))
    assertFalse(locked(passed, "2026-11-01T08:29:59Z", pacific))
    assertTrue(locked(passed, "2026-11-01T08:30:00Z", pacific))
    assertTrue(locked(passed, "2026-11-01T09:45:00Z", pacific))
  }

  @Test
  fun `next lock reuses the session planner`() {
    assertEquals(
        Instant.parse("2026-10-08T22:00:00Z"),
        AppLockPolicy.nextLockAt(Instant.parse("2026-10-08T07:00:00Z"), tenPm, utc),
    )
    assertEquals(
        Instant.parse("2026-10-09T22:00:00Z"),
        AppLockPolicy.nextLockAt(Instant.parse("2026-10-08T22:00:00Z"), tenPm, utc),
    )
  }
}
