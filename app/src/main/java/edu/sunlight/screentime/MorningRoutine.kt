package edu.sunlight.screentime

import java.time.Duration
import java.time.Instant

internal const val DEFAULT_SUNLIGHT_MINUTES = 15
internal const val MAX_SUNLIGHT_MINUTES = 120

internal fun sunlightMinutesOrDefault(minutes: Int?): Int =
    minutes?.takeIf { it in 1..MAX_SUNLIGHT_MINUTES } ?: DEFAULT_SUNLIGHT_MINUTES

/** Both camera frames and debug readings enter through [acceptLight]. */
internal class MorningRoutine(private val wakeAt: Instant, val sunlightMinutes: Int) {
  init {
    require(sunlightMinutes in 1..MAX_SUNLIGHT_MINUTES)
  }

  var phase = FocusPhase.WAITING
    private set

  var countdownEndsAt: Instant? = null
    private set

  private var brightSince: Instant? = null

  fun advance(now: Instant) {
    if (phase == FocusPhase.WAITING && !now.isBefore(wakeAt)) {
      phase = FocusPhase.SEEKING_SUNLIGHT
    }
    if (phase == FocusPhase.FINAL_COUNTDOWN && !now.isBefore(countdownEndsAt)) {
      phase = FocusPhase.COMPLETE
    }
  }

  fun acceptLight(luma: Int, now: Instant) {
    if (phase != FocusPhase.SEEKING_SUNLIGHT || now.isBefore(wakeAt)) return
    if (luma < LIGHT_THRESHOLD) {
      resetLightCheck()
      return
    }
    val since = brightSince ?: now.also { brightSince = it }
    if (Duration.between(since, now) >= REQUIRED_BRIGHT_DURATION) {
      countdownEndsAt = now.plus(Duration.ofMinutes(sunlightMinutes.toLong()))
      phase = FocusPhase.FINAL_COUNTDOWN
    }
  }

  fun resetLightCheck() {
    brightSince = null
  }

  private companion object {
    const val LIGHT_THRESHOLD = 220
    val REQUIRED_BRIGHT_DURATION: Duration = Duration.ofSeconds(3)
  }
}
