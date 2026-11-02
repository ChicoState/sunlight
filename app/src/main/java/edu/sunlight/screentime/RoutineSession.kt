package edu.sunlight.screentime

import androidx.lifecycle.SavedStateHandle
import java.time.Instant

private const val SIMULATED_BRIGHTNESS = 255
private const val KEY_WAKE_AT = "session.wakeAt"
private const val KEY_MINUTES = "session.sunlightMinutes"
private const val KEY_COUNTDOWN_ENDS_AT = "session.countdownEndsAt"
private const val KEY_CAMERA_REQUESTED = "session.cameraRequested"
private const val KEY_CAMERA_NEEDED = "session.cameraPermissionNeeded"
private const val KEY_DEBUG_ANCHOR_TIME = "session.debugAnchorTime"
private const val KEY_DEBUG_ANCHOR_ELAPSED = "session.debugAnchorElapsed"
private const val KEY_DEBUG_SPEED = "session.debugSpeed"

/**
 * One morning routine run: the [MorningRoutine] plus the clock chosen when it started. The clock is
 * fixed for the session, so callers never switch between real and simulated time.
 */
internal class RoutineSession(
    val routine: MorningRoutine,
    private val debugClock: DebugRoutineClock?,
    systemClock: RoutineClock,
) {
  private val clock: RoutineClock = debugClock ?: systemClock
  var cameraRequested = false
  var cameraPermissionNeeded = false

  val isDebug: Boolean
    get() = debugClock != null

  val debugSpeed: Int
    get() = debugClock?.debugClock?.speed ?: 1

  /** Only a real routine that is checking for light uses the camera. */
  val needsCamera: Boolean
    get() = !isDebug && routine.phase == FocusPhase.SEEKING_SUNLIGHT

  fun now(): Instant = clock.now()

  /** Advances time; debug tests feed simulated brightness into the same light detector. */
  fun advance() {
    val now = clock.now()
    routine.advance(now)
    if (isDebug && routine.phase == FocusPhase.SEEKING_SUNLIGHT) {
      routine.acceptLight(SIMULATED_BRIGHTNESS, now)
    }
  }

  /** Camera frames are ignored during a debug test, which uses simulated light only. */
  fun acceptCameraLight(luma: Int) {
    if (!isDebug) routine.acceptLight(luma, clock.now())
  }

  /** Returns false when the speed cannot apply, because this is not an active debug test. */
  fun setDebugSpeed(speed: Int): Boolean {
    val applies = debugClock != null && routine.phase.isActive() && speed in 1..MAX_DEBUG_SPEED
    if (applies) debugClock?.setSpeed(speed)
    return applies
  }

  fun saveTo(handle: SavedStateHandle) {
    handle[KEY_WAKE_AT] = routine.wakeAt
    handle[KEY_MINUTES] = routine.sunlightMinutes
    handle[KEY_COUNTDOWN_ENDS_AT] = routine.countdownEndsAt
    handle[KEY_CAMERA_REQUESTED] = cameraRequested
    handle[KEY_CAMERA_NEEDED] = cameraPermissionNeeded
    val simulated = debugClock?.debugClock
    handle[KEY_DEBUG_ANCHOR_TIME] = simulated?.anchorTime
    handle[KEY_DEBUG_ANCHOR_ELAPSED] = simulated?.anchorElapsed
    handle[KEY_DEBUG_SPEED] = simulated?.speed
  }

  companion object {
    /** Restores a saved session, or returns null when none is saved or its values are invalid. */
    fun restore(
        handle: SavedStateHandle,
        systemClock: RoutineClock,
        elapsedNanos: () -> Long,
    ): RoutineSession? {
      val wakeAt = handle.get<Instant>(KEY_WAKE_AT)
      val minutes = handle.get<Int>(KEY_MINUTES)?.takeIf { it in 1..MAX_SUNLIGHT_MINUTES }
      if (wakeAt == null || minutes == null) return null
      val routine = MorningRoutine(wakeAt, minutes, handle.get<Instant>(KEY_COUNTDOWN_ENDS_AT))
      return RoutineSession(routine, restoreDebugClock(handle, elapsedNanos), systemClock).apply {
        cameraRequested = handle[KEY_CAMERA_REQUESTED] ?: false
        cameraPermissionNeeded = handle[KEY_CAMERA_NEEDED] ?: false
      }
    }

    fun clear(handle: SavedStateHandle) {
      handle.remove<Instant>(KEY_WAKE_AT)
    }

    private fun restoreDebugClock(
        handle: SavedStateHandle,
        elapsedNanos: () -> Long,
    ): DebugRoutineClock? {
      val anchorTime = handle.get<Instant>(KEY_DEBUG_ANCHOR_TIME)
      val anchorElapsed = handle.get<Long>(KEY_DEBUG_ANCHOR_ELAPSED)
      val speed = handle.get<Int>(KEY_DEBUG_SPEED)?.takeIf { it in 1..MAX_DEBUG_SPEED }
      return if (anchorTime == null || anchorElapsed == null || speed == null) null
      else DebugRoutineClock(DebugClock(anchorTime, anchorElapsed, speed), elapsedNanos)
    }
  }
}
