package edu.sunlight.screentime

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal object SessionPlanner {
  fun nextRelease(now: Instant, releaseTime: LocalTime, zone: ZoneId): Instant {
    val today = LocalDate.ofInstant(now, zone)
    val candidate = today.atTime(releaseTime).atZone(zone).toInstant()
    return if (candidate.isAfter(now)) candidate
    else today.plusDays(1).atTime(releaseTime).atZone(zone).toInstant()
  }
}
