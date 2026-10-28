package edu.sunlight.screentime

import java.time.Instant

internal const val MAX_DEBUG_SPEED = 100

/** Anchors each rate change so elapsed time is never rescaled retroactively. */
internal class DebugClock(start: Instant, elapsedNanos: Long) {
  private var anchorTime = start
  private var anchorElapsed = elapsedNanos
  private var speed = 1

  fun now(elapsedNanos: Long): Instant =
      anchorTime.plusNanos((elapsedNanos - anchorElapsed).coerceAtLeast(0) * speed)

  fun setSpeed(newSpeed: Int, elapsedNanos: Long) {
    require(newSpeed in 1..MAX_DEBUG_SPEED)
    anchorTime = now(elapsedNanos)
    anchorElapsed = elapsedNanos
    speed = newSpeed
  }
}
