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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val COLLECTOR_LOOP_LIMIT = 100

class SunlightViewModelTest {
  private var wallNow = Instant.parse("2026-10-22T05:00:00Z")
  private var elapsedNanos = 0L
  private val wakeAt =
      requireNotNull(
          SessionPlanner.nextRelease(wallNow, listOf(LocalTime.of(7, 0)), ZoneId.systemDefault())
      )

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
  ) =
      SunlightViewModel(
          savedState = handle,
          settings = SettingsRepository(store),
          streaks = StreakRepository(store),
          debugAvailable = debugAvailable,
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
    assertEquals(LocalTime.of(7, 0), state.nextSunlightTime)
    assertEquals(5, state.sunlightMinutes)
  }

  @Test
  fun `starting without a wake-up time asks for one`() {
    val model = viewModel(store = FakePreferencesDataStore())
    model.startRoutine(debug = false)
    assertEquals(FocusPhase.SETUP, model.state.value.phase)
    assertEquals("Add at least one Sunlight Time first.", model.state.value.statusMessage)
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
  fun `normal completion records one persisted streak`() {
    val store = FakePreferencesDataStore(settings())
    val model = viewModel(store = store)
    model.reachCountdown()
    wallNow = wakeAt.plusSeconds(3 + 5 * 60)

    model.tick()
    model.tick()

    assertEquals(1, runBlocking { StreakRepository(store).streak.first().currentStreak })
  }

  @Test
  fun `debug completion does not record a streak`() {
    val store = FakePreferencesDataStore(settings())
    val model = viewModel(store = store)
    model.startRoutine(debug = true)
    elapsedNanos = 3_000_000_000
    model.tick()
    elapsedNanos = 303_000_000_000

    model.tick()

    assertEquals(0, runBlocking { StreakRepository(store).streak.first().currentStreak })
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
  fun `out of range durations keep the saved value`() {
    val store = FakePreferencesDataStore(settings(minutes = 30))
    val model = viewModel(store = store)
    model.saveSunlightMinutes(500)
    assertEquals(30, model.state.value.sunlightMinutes)
    assertEquals(30, runBlocking { SettingsRepository(store).settings.first().sunlightMinutes })
  }

  @Test
  fun `starting again during an active routine keeps the running session`() {
    val model = viewModel()
    model.reachCountdown()
    val countdownEndsAt = model.state.value.countdownEndsAt
    model.startRoutine(debug = false)
    assertEquals(FocusPhase.FINAL_COUNTDOWN, model.state.value.phase)
    assertEquals(countdownEndsAt, model.state.value.countdownEndsAt)
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

  /**
   * The Activity reports missing permission from its state collector. A collector that causes a new
   * state loops without suspending, so reporting must settle; the clock moves on every pass, as a
   * sub-millisecond device clock would, so a fresh display time can't end the loop by coincidence.
   */
  @Test
  fun `reporting missing camera permission from the state collector settles`() {
    val model = viewModel()
    model.startRoutine(debug = false)
    wallNow = wakeAt
    model.tick()
    var emissions = 0
    val collector =
        CoroutineScope(Dispatchers.Unconfined).launch {
          model.state.take(COLLECTOR_LOOP_LIMIT).collect {
            emissions++
            wallNow = wallNow.plusNanos(1)
            model.onCameraEvent(CameraEvent.PERMISSION_MISSING)
          }
        }
    collector.cancel()
    assertTrue("collector looped $emissions times", emissions < COLLECTOR_LOOP_LIMIT)
    assertTrue(model.state.value.cameraPermissionNeeded)
  }

  @Test
  fun `camera permission lost after starting is still reported`() {
    val model = viewModel()
    model.startRoutine(debug = false)
    wallNow = wakeAt
    model.tick()
    assertTrue(model.onCameraEvent(CameraEvent.PERMISSION_MISSING))
    model.onCameraEvent(CameraEvent.STARTED)
    assertFalse(model.state.value.cameraPermissionNeeded)
    assertFalse(model.onCameraEvent(CameraEvent.PERMISSION_MISSING))
    assertTrue(model.state.value.cameraPermissionNeeded)
  }

  @Test
  fun `camera frames publish state only when the phase changes`() {
    val model = viewModel()
    model.startRoutine(debug = false)
    wallNow = wakeAt
    model.tick()
    val published = model.state.value
    model.onLightReading(255)
    wallNow = wakeAt.plusSeconds(1)
    model.onLightReading(0)
    wallNow = wakeAt.plusSeconds(2)
    model.onLightReading(255)
    assertSame(published, model.state.value)
    wallNow = wakeAt.plusSeconds(5)
    model.onLightReading(255)
    assertEquals(FocusPhase.FINAL_COUNTDOWN, model.state.value.phase)
    assertNotNull(model.state.value.countdownEndsAt)
  }

  @Test
  fun `a frame that passes the light check is announced and saved for restoration`() {
    val handle = SavedStateHandle()
    val model = viewModel(handle)
    model.reachCountdown()
    assertEquals(
        "Bright light detected. Your 5-minute timer has started.",
        model.state.value.statusMessage,
    )
    assertEquals(FocusPhase.FINAL_COUNTDOWN, viewModel(handle).state.value.phase)
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
    assertEquals(LocalTime.of(13, 5), model.state.value.nextSunlightTime)
    assertEquals("Sunlight Time saved for 1:05 PM.", model.state.value.statusMessage)
    val stored = runBlocking { SettingsRepository(store).settings.first() }
    assertEquals(LocalTime.of(13, 5), stored.releaseTime)
  }
}
