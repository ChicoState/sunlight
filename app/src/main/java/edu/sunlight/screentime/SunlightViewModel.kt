package edu.sunlight.screentime

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val KEY_STATUS = "status"
private const val UPDATE_INTERVAL_MILLIS = 1_000L
private const val DEBUG_UPDATE_INTERVAL_MILLIS = 50L

/** Activity-reported camera conditions while a real routine checks for light. */
internal enum class CameraEvent {
  STARTED,
  STOPPED,
  PERMISSION_MISSING,
  PERMISSION_DENIED,
}

/**
 * Owns the morning routine session, its clock, and the single screen state. The session is mirrored
 * into [SavedStateHandle] after every change so it survives configuration changes and process
 * death; settings come from [SettingsRepository] as a flow.
 */
internal class SunlightViewModel(
    private val savedState: SavedStateHandle,
    private val settings: SettingsRepository,
    private val debugAvailable: Boolean,
    private val wallClock: RoutineClock,
    private val elapsedNanos: () -> Long,
    scope: CoroutineScope,
) : ViewModel(scope) {
  private var session = RoutineSession.restore(savedState, wallClock, elapsedNanos)
  private var announcedPhase = session?.routine?.phase
  private var status: String = savedState[KEY_STATUS] ?: ""
  private var routineSettings = RoutineSettings()
  private val mutableState =
      MutableStateFlow(SunlightScreenState(null, FocusPhase.SETUP, status, wallClock.now(), null))

  val state: StateFlow<SunlightScreenState> = mutableState.asStateFlow()

  init {
    update {}
    viewModelScope.launch { settings.settings.collect { update { routineSettings = it } } }
  }

  /**
   * Advances the routine until cancelled. Callers run this in a scope that is active only while the
   * UI is resumed, so time-driven transitions pause with the foreground preview.
   */
  suspend fun runClock() {
    while (true) {
      tick()
      val current = state.value
      delay(
          if (current.debugMode && current.phase.isActive()) DEBUG_UPDATE_INTERVAL_MILLIS
          else UPDATE_INTERVAL_MILLIS
      )
    }
  }

  fun tick() = update { session?.advance() }

  fun startRoutine(debug: Boolean) {
    val now = wallClock.now()
    val releaseTime = routineSettings.releaseTime
    val wakeAt =
        if (debug) now.takeIf { debugAvailable && session?.routine?.phase?.isActive() != true }
        else releaseTime?.let { SessionPlanner.nextRelease(now, it, ZoneId.systemDefault()) }
    update {
      if (wakeAt == null) {
        if (!debug) status = "Choose your wake-up time first."
      } else {
        val debugClock =
            if (debug) DebugRoutineClock(DebugClock(now, elapsedNanos()), elapsedNanos) else null
        val routine = MorningRoutine(wakeAt, routineSettings.sunlightMinutes)
        session = RoutineSession(routine, debugClock, wallClock).also { it.advance() }
        status =
            if (debug) "Debug test: simulated sunlight. Camera stays off."
            else
                "Sunlight checking starts at ${releaseTime?.let(::displayTime)}. " +
                    "Camera stays off until then."
      }
    }
  }

  fun endSession() = update {
    session = null
    status = "You exited the morning routine preview early."
  }

  fun saveReleaseTime(time: LocalTime) {
    update {
      routineSettings = routineSettings.copy(releaseTime = time)
      status = "Wake-up time: ${displayTime(time)}. Try the routine below."
    }
    viewModelScope.launch { settings.saveReleaseTime(time) }
  }

  /** Duration is frozen during a routine; each routine snapshots it when it starts. */
  fun saveSunlightMinutes(minutes: Int) {
    if (session?.routine?.phase?.isActive() == true) return
    val valid = sunlightMinutesOrDefault(minutes)
    update { routineSettings = routineSettings.copy(sunlightMinutes = valid) }
    viewModelScope.launch { settings.saveSunlightMinutes(valid) }
  }

  fun changeDebugSpeed(speed: Int) {
    val current = session ?: return
    if (debugAvailable && current.setDebugSpeed(speed)) update { current.advance() }
  }

  /** Frames arrive many times a second, so state is published only when one changes the phase. */
  fun onLightReading(luma: Int) {
    val current = session ?: return
    current.acceptCameraLight(luma)
    if (current.routine.phase != announcedPhase) update {}
  }

  /** Returns true when the Activity should show the system camera-permission prompt. */
  fun onCameraEvent(event: CameraEvent): Boolean {
    val current = session?.takeIf { event == CameraEvent.STOPPED || it.needsCamera } ?: return false
    val prompt = event == CameraEvent.PERMISSION_MISSING && !current.cameraRequested
    // The Activity reports this from its state collector, which would loop on the main thread if
    // repeating a condition already in state published a new state with a new display time.
    val alreadyReported =
        event == CameraEvent.PERMISSION_MISSING &&
            current.cameraRequested &&
            current.cameraPermissionNeeded
    if (!alreadyReported) {
      update {
        when (event) {
          CameraEvent.STOPPED -> current.routine.resetLightCheck()
          CameraEvent.STARTED -> {
            current.cameraPermissionNeeded = false
            status = "Find bright sunlight and keep the front camera uncovered."
          }
          CameraEvent.PERMISSION_MISSING -> {
            current.cameraPermissionNeeded = true
            current.cameraRequested = true
          }
          CameraEvent.PERMISSION_DENIED -> {
            current.cameraPermissionNeeded = true
            status = "Camera access is off. Allow access to check sunlight."
          }
        }
      }
    }
    return prompt
  }

  /**
   * Applies [change], announces phase transitions, saves the session, and publishes state. The
   * announced phase is tracked separately because camera frames change the routine before calling
   * this.
   */
  private inline fun update(change: () -> Unit) {
    change()
    val current = session
    val phase = current?.routine?.phase
    if (current != null && phase != announcedPhase) {
      phaseNotice(current)?.let { status = it }
    }
    announcedPhase = phase
    if (current == null) RoutineSession.clear(savedState) else current.saveTo(savedState)
    savedState[KEY_STATUS] = status
    mutableState.value =
        SunlightScreenState(
            releaseTime = routineSettings.releaseTime,
            phase = current?.routine?.phase ?: FocusPhase.SETUP,
            statusMessage = status,
            displayNow = current?.now() ?: wallClock.now(),
            countdownEndsAt = current?.routine?.countdownEndsAt,
            sunlightMinutes = routineSettings.sunlightMinutes,
            debugAvailable = debugAvailable,
            debugMode = current?.isDebug ?: false,
            cameraPermissionNeeded = current?.cameraPermissionNeeded ?: false,
            debugSpeed = current?.debugSpeed ?: 1,
        )
  }

  companion object {
    fun factory(context: Context, debugAvailable: Boolean): ViewModelProvider.Factory {
      val appContext = context.applicationContext
      return viewModelFactory {
        initializer {
          SunlightViewModel(
              savedState = createSavedStateHandle(),
              settings = SettingsRepository(appContext.focusDataStore),
              debugAvailable = debugAvailable,
              wallClock = { Instant.now() },
              elapsedNanos = SystemClock::elapsedRealtimeNanos,
              scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
          )
        }
      }
    }
  }
}

private fun phaseNotice(session: RoutineSession): String? =
    when (session.routine.phase) {
      FocusPhase.FINAL_COUNTDOWN ->
          "${if (session.isDebug) "Simulated sunlight" else "Bright light"} detected. " +
              "Your ${session.routine.sunlightMinutes}-minute timer has started."
      FocusPhase.COMPLETE -> "Your morning timer is complete."
      else -> null
    }
