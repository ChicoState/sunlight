package edu.sunlight.screentime

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SECONDS_PER_MINUTE = 60
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

internal data class SunlightScreenState(
    val releaseTime: LocalTime?,
    val phase: FocusPhase,
    val statusMessage: String,
    val displayNow: Instant,
    val countdownEndsAt: Instant?,
)

internal enum class FocusPhase(val title: String, val guidance: String) {
  SETUP(
      "Make room for morning.",
      "Focus until your check time. Find sunlight. Finish with 15 quiet minutes.",
  ),
  WAITING(
      "Time to switch off.",
      "Keep Sunlight open. The screen stays on and dimmed during Focus.",
  ),
  SEEKING_SUNLIGHT(
      "Find the morning light.",
      "Keep the front camera uncovered. Bright artificial light may also pass the check.",
  ),
  FINAL_COUNTDOWN("A little more quiet.", "Light check complete. The camera is now off."),
  COMPLETE(
      "Good morning.",
      "Light check and final countdown complete. You're ready for the day.",
  ),
}

internal fun FocusPhase.isActive(): Boolean =
    this == FocusPhase.WAITING ||
        this == FocusPhase.SEEKING_SUNLIGHT ||
        this == FocusPhase.FINAL_COUNTDOWN

internal fun currentClock(now: Instant): String =
    now.atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)

internal fun remainingCountdown(now: Instant, countdownEndsAt: Instant?): String {
  val seconds = countdownEndsAt?.let { Duration.between(now, it).seconds.coerceAtLeast(0) } ?: 0
  return String.format(
      Locale.ROOT,
      "%02d:%02d",
      seconds / SECONDS_PER_MINUTE,
      seconds % SECONDS_PER_MINUTE,
  )
}

internal fun displayTime(time: LocalTime): String = time.format(timeFormatter)
