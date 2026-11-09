package edu.sunlight.screentime

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

private const val DEFAULT_LOCK_HOUR = 22
internal val DEFAULT_LOCK_TIME: LocalTime = LocalTime.of(DEFAULT_LOCK_HOUR, 0)

/**
 * Persisted nightly app-lock configuration and history.
 *
 * [armedAt] is the last time the configuration changed. Only lock starts at or after it count, so
 * an edit never locks apps immediately; it takes effect from the next lock time.
 */
internal data class AppLockSettings(
    val enabled: Boolean = false,
    val lockTime: LocalTime = DEFAULT_LOCK_TIME,
    val packages: Set<String> = emptySet(),
    val armedAt: Instant? = null,
    val lastRoutineCompletedAt: Instant? = null,
    val lastEmergencyUnlockAt: Instant? = null,
    val emergencyUnlockCount: Int = 0,
)

internal sealed interface AppLockStatus {
  /** Disabled or no apps selected. */
  data object Off : AppLockStatus

  data class Scheduled(val nextLockAt: Instant) : AppLockStatus

  data class Locked(val since: Instant) : AppLockStatus

  data class UnlockedByRoutine(val at: Instant, val nextLockAt: Instant) : AppLockStatus

  /** An emergency unlock is recorded as such; it is never reported as a sunlight pass. */
  data class UnlockedByEmergency(val at: Instant, val nextLockAt: Instant) : AppLockStatus
}

/**
 * Pure rules for the nightly lock.
 *
 * The lock begins at the most recent occurrence of the local lock time and lasts until a morning
 * routine completes (or an emergency unlock is used) at or after that start. Instants are compared,
 * so DST and time-zone changes only move the local lock start: in a spring-forward gap the lock
 * starts after the gap; in a fall-back overlap it starts at the first occurrence.
 */
internal object AppLockPolicy {
  fun lockStartedAt(now: Instant, lockTime: LocalTime, zone: ZoneId): Instant {
    val today = now.atZone(zone).toLocalDate()
    val candidate = today.atTime(lockTime).atZone(zone).toInstant()
    return if (candidate.isAfter(now)) {
      today.minusDays(1).atTime(lockTime).atZone(zone).toInstant()
    } else {
      candidate
    }
  }

  fun nextLockAt(now: Instant, lockTime: LocalTime, zone: ZoneId): Instant =
      SessionPlanner.nextRelease(now, lockTime, zone)

  fun status(settings: AppLockSettings, now: Instant, zone: ZoneId): AppLockStatus {
    val armedAt = settings.armedAt
    val start = lockStartedAt(now, settings.lockTime, zone)
    // Unlocks only count inside the current lock window; future instants (clock moved back) don't.
    fun inWindow(at: Instant?) = at?.takeUnless { it.isBefore(start) || it.isAfter(now) }
    val pass = inWindow(settings.lastRoutineCompletedAt)
    val emergency = inWindow(settings.lastEmergencyUnlockAt)
    val next by lazy { nextLockAt(now, settings.lockTime, zone) }
    return when {
      !settings.enabled || settings.packages.isEmpty() || armedAt == null -> AppLockStatus.Off
      start.isBefore(armedAt) -> AppLockStatus.Scheduled(next)
      pass != null -> AppLockStatus.UnlockedByRoutine(pass, next)
      emergency != null -> AppLockStatus.UnlockedByEmergency(emergency, next)
      else -> AppLockStatus.Locked(start)
    }
  }

  fun isLocked(
      packageName: String,
      settings: AppLockSettings,
      now: Instant,
      zone: ZoneId,
      protectedPackages: Set<String> = emptySet(),
  ): Boolean =
      packageName in settings.packages &&
          packageName !in protectedPackages &&
          packageName !in AppSelection.BUILT_IN_PROTECTED &&
          status(settings, now, zone) is AppLockStatus.Locked

  /** Configuration is frozen while locked so the lock can only end by routine or emergency. */
  fun canEdit(settings: AppLockSettings, now: Instant, zone: ZoneId): Boolean =
      status(settings, now, zone) !is AppLockStatus.Locked
}

/** Validation for package names, which arrive from PackageManager, DataStore, and intents. */
internal object AppSelection {
  private const val MAX_PACKAGE_NAME_LENGTH = 255
  private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

  /** Packages that must stay usable so the phone can always be operated and recovered. */
  val BUILT_IN_PROTECTED: Set<String> =
      setOf(
          "com.android.settings",
          "com.android.systemui",
          "com.android.phone",
          "com.android.dialer",
          "com.google.android.dialer",
          "com.android.server.telecom",
          "com.android.emergency",
          "com.google.android.apps.safetyhub",
          "com.android.launcher3",
          "com.google.android.apps.nexuslauncher",
      )

  fun isValidPackageName(name: String): Boolean =
      name.length <= MAX_PACKAGE_NAME_LENGTH && packagePattern.matches(name)

  fun protectedPackages(ownPackage: String, resolved: Set<String>): Set<String> =
      BUILT_IN_PROTECTED + ownPackage + resolved

  fun sanitize(
      requested: Set<String>,
      launchable: Set<String>,
      protectedPackages: Set<String>,
  ): Set<String> =
      requested
          .filter { isValidPackageName(it) && it in launchable }
          .filterNot { it in protectedPackages || it in BUILT_IN_PROTECTED }
          .toSet()

  /** True when every listed app is selected; an empty list is never "all selected". */
  fun allSelected(selected: Set<String>, listed: Collection<String>): Boolean =
      listed.isNotEmpty() && selected.containsAll(listed)

  /** Adds every listed app ([select]) or removes only the listed apps; callers then sanitize. */
  fun selectAll(selected: Set<String>, listed: Collection<String>, select: Boolean): Set<String> =
      if (select) selected + listed else selected - listed.toSet()
}

internal enum class BlockAction {
  NONE,
  /** The block screen was just shown for this app; only dismiss the app again. */
  HOME_ONLY,
  HOME_AND_BLOCK_SCREEN,
}

/**
 * What the accessibility service does when an app comes to the foreground.
 *
 * A locked app is always dismissed with the Home global action, so it never stays usable behind the
 * block screen, even when reopened from Recents or a notification. The block screen itself is
 * debounced so one launch that emits several window events starts it only once.
 */
internal object AppLockEnforcement {
  const val BLOCK_SCREEN_DEBOUNCE_MILLIS = 750L

  fun action(locked: Boolean, repeatOfLastBlock: Boolean, millisSinceLastBlock: Long): BlockAction =
      when {
        !locked -> BlockAction.NONE
        repeatOfLastBlock && millisSinceLastBlock < BLOCK_SCREEN_DEBOUNCE_MILLIS ->
            BlockAction.HOME_ONLY
        else -> BlockAction.HOME_AND_BLOCK_SCREEN
      }
}
