package edu.sunlight.screentime

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

internal object SessionPlanner {
  fun nextRelease(now: Instant, releaseTimes: List<LocalTime>, zone: ZoneId): Instant? {
    if (releaseTimes.isEmpty()) return null
    val today = now.atZone(zone).toLocalDate()
    return releaseTimes
        .map { releaseTime ->
          val candidate = today.atTime(releaseTime).atZone(zone).toInstant()
          if (candidate.isAfter(now)) candidate
          else today.plusDays(1).atTime(releaseTime).atZone(zone).toInstant()
        }
        .minOrNull()
  }
}
