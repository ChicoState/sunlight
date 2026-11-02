package edu.sunlight.screentime

import java.time.Instant

/** Source of the time a routine runs on: wall-clock time, or simulated time in a debug test. */
internal fun interface RoutineClock {
  fun now(): Instant
}

/** Simulated time anchored to Android's monotonic elapsed clock; see [DebugClock]. */
internal class DebugRoutineClock(
    val debugClock: DebugClock,
    private val elapsedNanos: () -> Long,
) : RoutineClock {
  override fun now(): Instant = debugClock.now(elapsedNanos())

  fun setSpeed(speed: Int) = debugClock.setSpeed(speed, elapsedNanos())
}
