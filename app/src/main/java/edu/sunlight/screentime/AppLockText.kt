package edu.sunlight.screentime

import java.time.Instant
import java.time.ZoneId

internal fun appLockStatusText(status: AppLockStatus, zone: ZoneId): String {
  fun at(instant: Instant) = displayTime(instant.atZone(zone).toLocalTime())
  return when (status) {
    AppLockStatus.Off -> "Nightly app lock is off."
    is AppLockStatus.Scheduled -> "Selected apps lock at ${at(status.nextLockAt)}."
    is AppLockStatus.Locked ->
        "Locked since ${at(status.since)}. Apps unlock when your morning sunlight timer finishes."
    is AppLockStatus.UnlockedByRoutine ->
        "Unlocked when your morning routine finished at ${at(status.at)}. " +
            "Apps lock again at ${at(status.nextLockAt)}."
    is AppLockStatus.UnlockedByEmergency ->
        "Emergency unlock used at ${at(status.at)}. This is not a completed routine. " +
            "Apps lock again at ${at(status.nextLockAt)}."
  }
}
