package edu.sunlight.screentime

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.lifecycle.SavedStateHandle
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunlightViewModelTest {
  private var wallNow = Instant.parse("2026-10-22T05:00:00Z")
  private var elapsedNanos = 0L
  private val wakeAt =
      SessionPlanner.nextRelease(wallNow, LocalTime.of(7, 0), ZoneId.systemDefault())

  private fun settings(minutes: Int = 5): Preferences =
      preferencesOf(
          intPreferencesKey("release_hour") to 7,
          intPreferencesKey("release_minute") to 0,
          intPreferencesKey("sunlight_minutes") to minutes,
      )

  private fun viewModel(
      handle: SavedStateHandle = SavedStateHandle(),
      store: FakePreferencesDataStore = FakePreferencesDataStore(settings()),
      debugAvailable: Boolean = true,
      recordCompletion: suspend (Instant) -> Unit = {},
  ) =
      SunlightViewModel(
          savedState = handle,
          settings = SettingsRepository(store),
          debugAvailable = debugAvailable,
          recordCompletion = recordCompletion,
          wallClock = { wallNow },
          elapsedNanos = { elapsedNanos },
          scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
      )

  private fun SunlightViewModel.reachCountdown() {
    startRoutine(debug = false)
    wallNow = wakeAt
    tick()
    onLightReading(255)
    wallNow = wakeAt.plusSeconds(3)
    onLightReading(255)
  }

  @Test
  fun `no session means the setup phase and stored settings`() {
    val state = viewModel().state.value
    assertEquals(FocusPhase.SETUP, state.phase)
    assertEquals(LocalTime.of(7, 0), state.releaseTime)
    assertEquals(5, state.sunlightMinutes)
  }

  @Test
  fun `starting without a wake-up time asks for one`() {
    val model = viewModel(store = FakePreferencesDataStore())
    model.startRoutine(debug = false)
    assertEquals(FocusPhase.SETUP, model.state.value.phase)
    assertEquals("Choose your wake-up time first.", model.state.value.statusMessage)
  }

  @Test
  fun `normal routine waits without needing the camera until wake-up time`() {
    val model = viewModel()
    model.startRoutine(debug = false)
    assertEquals(FocusPhase.WAITING, model.state.value.phase)
    assertFalse(model.state.value.needsCamera)
    wallNow = wakeAt
    model.tick()
    assertEquals(FocusPhase.SEEKING_SUNLIGHT, model.state.value.phase)
    assertTrue(model.state.value.needsCamera)
  }

  @Test
  fun `camera is no longer needed once the countdown starts`() {
    val model = viewModel()
    model.reachCountdown()
    val state = model.state.value
    assertEquals(FocusPhase.FINAL_COUNTDOWN, state.phase)
    assertFalse(state.needsCamera)
    assertEquals(wakeAt.plusSeconds(3 + 5 * 60), state.countdownEndsAt)
    assertEquals("Bright light detected. Your 5-minute timer has started.", state.statusMessage)
  }

  @Test
  fun `countdown reaches completion`() {
    val model = viewModel()
    model.reachCountdown()
    wallNow = wakeAt.plusSeconds(3 + 5 * 60)
    model.tick()
    assertEquals(FocusPhase.COMPLETE, model.state.value.phase)
    assertFalse(model.state.value.phase.isActive())
    assertEquals("Your morning timer is complete.", model.state.value.statusMessage)
  }

  @Test
  fun `duration is snapshotted when the routine starts`() {
    val store = FakePreferencesDataStore(settings(minutes = 5))
    val model = viewModel(store = store)
    model.startRoutine(debug = false)
    runBlocking { SettingsRepository(store).saveSunlightMinutes(30) }
    assertEquals(30, model.state.value.sunlightMinutes)
    wallNow = wakeAt
    model.tick()
    model.onLightReading(255)
    wallNow = wakeAt.plusSeconds(3)
    model.onLightReading(255)
    assertEquals(wakeAt.plusSeconds(3 + 5 * 60), model.state.value.countdownEndsAt)
  }

  @Test
  fun `duration edits are rejected during an active routine`() {
    val store = FakePreferencesDataStore(settings(minutes = 5))
    val model = viewModel(store = store)
    model.startRoutine(debug = false)
    model.saveSunlightMinutes(40)
    assertEquals(5, runBlocking { SettingsRepository(store).settings.first().sunlightMinutes })
  }

  @Test
  fun `in-progress session survives a recreated view model from the same saved state`() {
    val handle = SavedStateHandle()
    val store = FakePreferencesDataStore(settings())
    val first = viewModel(handle, store)
    first.reachCountdown()
    val before = first.state.value

    val second = viewModel(handle, store)
    val after = second.state.value
    assertEquals(FocusPhase.FINAL_COUNTDOWN, after.phase)
    assertEquals(before.countdownEndsAt, after.countdownEndsAt)
    assertEquals(before.statusMessage, after.statusMessage)
    assertFalse(after.debugMode)
    wallNow = before.countdownEndsAt
    second.tick()
    assertEquals(FocusPhase.COMPLETE, second.state.value.phase)
  }

  @Test
  fun `waiting session restores its wake-up time rather than recomputing it`() {
    val handle = SavedStateHandle()
    val store = FakePreferencesDataStore(settings())
    viewModel(handle, store).startRoutine(debug = false)
    runBlocking { SettingsRepository(store).saveReleaseTime(LocalTime.of(9, 0)) }

    val restored = viewModel(handle, store)
    assertEquals(FocusPhase.WAITING, restored.state.value.phase)
    wallNow = wakeAt
    restored.tick()
    assertEquals(FocusPhase.SEEKING_SUNLIGHT, restored.state.value.phase)
  }

  @Test
  fun `debug session restores its flag, speed and simulated time`() {
    val handle = SavedStateHandle()
    val store = FakePreferencesDataStore(settings())
    val first = viewModel(handle, store)
    first.startRoutine(debug = true)
    first.changeDebugSpeed(100)
    elapsedNanos = 1_000_000_000

    val restored = viewModel(handle, store).state.value
    assertTrue(restored.debugMode)
    assertEquals(100, restored.debugSpeed)
    assertEquals(wallNow.plusSeconds(100), restored.displayNow)
  }

  @Test
  fun `debug readings come from the simulator and ignore the camera`() {
    val model = viewModel()
    model.startRoutine(debug = true)
    assertEquals(FocusPhase.SEEKING_SUNLIGHT, model.state.value.phase)
    assertFalse(model.state.value.needsCamera)
    model.onLightReading(0)
    elapsedNanos = 3_000_000_000
    model.tick()
    assertEquals(FocusPhase.FINAL_COUNTDOWN, model.state.value.phase)
    assertEquals(
        "Simulated sunlight detected. Your 5-minute timer has started.",
        model.state.value.statusMessage,
    )
  }

  @Test
  fun `debug test is rejected when debugging is unavailable`() {
    val model = viewModel(debugAvailable = false)
    model.startRoutine(debug = true)
    assertEquals(FocusPhase.SETUP, model.state.value.phase)
  }

  @Test
  fun `debug speed applies live, is bounded, and resets for each run`() {
    val model = viewModel()
    model.startRoutine(debug = true)
    model.changeDebugSpeed(101)
    assertEquals(1, model.state.value.debugSpeed)
    model.changeDebugSpeed(25)
    elapsedNanos = 2_000_000_000
    model.tick()
    assertEquals(25, model.state.value.debugSpeed)
    assertEquals(wallNow.plusSeconds(50), model.state.value.displayNow)
    model.endSession()
    model.startRoutine(debug = true)
    assertEquals(1, model.state.value.debugSpeed)
  }

  @Test
  fun `debug speed cannot change a normal routine`() {
    val model = viewModel()
    model.startRoutine(debug = false)
    model.changeDebugSpeed(50)
    assertEquals(1, model.state.value.debugSpeed)
  }

  @Test
  fun `ending a session resets state and clears the saved session`() {
    val handle = SavedStateHandle()
    val store = FakePreferencesDataStore(settings())
    val model = viewModel(handle, store)
    model.startRoutine(debug = true)
    model.changeDebugSpeed(10)
    model.endSession()
    val state = model.state.value
    assertEquals(FocusPhase.SETUP, state.phase)
    assertNull(state.countdownEndsAt)
    assertFalse(state.debugMode)
    assertEquals(1, state.debugSpeed)
    assertFalse(state.cameraPermissionNeeded)
    assertEquals("You exited the morning routine preview early.", state.statusMessage)
    assertEquals(FocusPhase.SETUP, viewModel(handle, store).state.value.phase)
  }

  @Test
  fun `missing camera permission prompts once per session and denial is explained`() {
    val model = viewModel()
    model.startRoutine(debug = false)
    assertFalse(model.onCameraEvent(CameraEvent.PERMISSION_MISSING))
    wallNow = wakeAt
    model.tick()
    assertTrue(model.onCameraEvent(CameraEvent.PERMISSION_MISSING))
    assertTrue(model.state.value.cameraPermissionNeeded)
    assertFalse(model.onCameraEvent(CameraEvent.PERMISSION_MISSING))
    model.onCameraEvent(CameraEvent.PERMISSION_DENIED)
    assertEquals(
        "Camera access is off. Allow access to check sunlight.",
        model.state.value.statusMessage,
    )
    model.onCameraEvent(CameraEvent.STARTED)
    assertFalse(model.state.value.cameraPermissionNeeded)
    assertEquals(
        "Find bright sunlight and keep the front camera uncovered.",
        model.state.value.statusMessage,
    )
  }

  @Test
  fun `stopping the camera discards a partial light check`() {
    val model = viewModel()
    model.startRoutine(debug = false)
    wallNow = wakeAt
    model.tick()
    model.onLightReading(255)
    model.onCameraEvent(CameraEvent.STOPPED)
    wallNow = wakeAt.plusSeconds(3)
    model.onLightReading(255)
    assertEquals(FocusPhase.SEEKING_SUNLIGHT, model.state.value.phase)
  }

  @Test
  fun `saving a wake-up time updates state and storage`() {
    val store = FakePreferencesDataStore()
    val model = viewModel(store = store)
    model.saveReleaseTime(LocalTime.of(13, 5))
    assertEquals(LocalTime.of(13, 5), model.state.value.releaseTime)
    assertEquals("Wake-up time: 1:05 PM. Try the routine below.", model.state.value.statusMessage)
    val stored = runBlocking { SettingsRepository(store).settings.first() }
    assertEquals(LocalTime.of(13, 5), stored.releaseTime)
  }

  @Test
  fun `real completion records its deadline, idempotently, even after restoring next night`() {
    val handle = SavedStateHandle()
    val completions = mutableListOf<Instant>()
    val model = viewModel(handle = handle, recordCompletion = { completions += it })
    model.reachCountdown()
    val deadline = requireNotNull(model.state.value.countdownEndsAt)
    assertTrue(completions.isEmpty())
    wallNow = deadline.plusSeconds(86_400)
    model.tick()
    assertEquals(listOf(deadline), completions)
    val snapshot = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
    val restored = viewModel(handle = snapshot, recordCompletion = { completions += it })
    restored.tick()
    // A restored session re-derives COMPLETE and may record again; the write is idempotent.
    assertEquals(setOf(deadline), completions.toSet())
  }

  @Test
  fun `debug completion and early exit never record a real completion`() {
    val completions = mutableListOf<Instant>()
    val model = viewModel(recordCompletion = { completions += it })
    model.reachCountdown()
    model.endSession()
    model.startRoutine(debug = true)
    elapsedNanos = 3_000_000_000
    model.tick()
    elapsedNanos = 400_000_000_000
    model.tick()
    assertEquals(FocusPhase.COMPLETE, model.state.value.phase)
    assertTrue(completions.isEmpty())
  }
}
