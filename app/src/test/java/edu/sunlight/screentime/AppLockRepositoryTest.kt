package edu.sunlight.screentime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory store: avoids file-rename behavior that differs on Windows JVMs. */
private class MemoryPreferencesStore : DataStore<Preferences> {
  private val state = MutableStateFlow(emptyPreferences())
  private val mutex = Mutex()
  override val data: Flow<Preferences> = state

  override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
      mutex.withLock {
        transform(state.value).also { state.value = it }
      }
}

class AppLockRepositoryTest {
  private val store = MemoryPreferencesStore()
  private val repository = AppLockRepository(store) { ZoneId.of("UTC") }
  private val noon = Instant.parse("2026-10-07T12:00:00Z")
  private val lateNight = Instant.parse("2026-10-07T23:00:00Z")

  @Test
  fun `defaults to off with a ten pm lock time`() = runBlocking {
    val settings = repository.current()
    assertFalse(settings.enabled)
    assertEquals(LocalTime.of(22, 0), settings.lockTime)
    assertTrue(settings.packages.isEmpty())
    assertNull(settings.armedAt)
  }

  @Test
  fun `saves settings and arms from the edit time`() = runBlocking {
    assertTrue(repository.setPackages(setOf("com.example.video"), noon))
    assertTrue(repository.setLockTime(LocalTime.of(21, 30), noon))
    assertTrue(repository.setEnabled(true, noon))
    val settings = repository.current()
    assertEquals(setOf("com.example.video"), settings.packages)
    assertEquals(LocalTime.of(21, 30), settings.lockTime)
    assertTrue(settings.enabled)
    assertEquals(noon, settings.armedAt)
  }

  @Test
  fun `refuses edits while locked so a lock cannot be loosened`() = runBlocking {
    repository.setPackages(setOf("com.example.video"), noon)
    repository.setEnabled(true, noon)
    assertFalse(repository.setEnabled(false, lateNight))
    assertFalse(repository.setPackages(emptySet(), lateNight))
    assertFalse(repository.setLockTime(LocalTime.of(23, 30), lateNight))
    val settings = repository.current()
    assertTrue(settings.enabled)
    assertEquals(LocalTime.of(22, 0), settings.lockTime)
    assertEquals(setOf("com.example.video"), settings.packages)
  }

  @Test
  fun `drops malformed package names when saving and loading`() = runBlocking {
    repository.setPackages(setOf("com.example.video", "not a package"), noon)
    assertEquals(setOf("com.example.video"), repository.current().packages)
    store.edit {
      it[stringSetPreferencesKey("locked_packages")] = setOf("bad name", "com.example.ok")
      it[intPreferencesKey("lock_hour")] = 99
    }
    val loaded = repository.current()
    assertEquals(setOf("com.example.ok"), loaded.packages)
    assertEquals(LocalTime.of(22, 0), loaded.lockTime)
  }

  @Test
  fun `completed routine unlocks and emergency unlock is recorded separately`() = runBlocking {
    repository.setPackages(setOf("com.example.video"), noon)
    repository.setEnabled(true, noon)
    val zone = ZoneId.of("UTC")
    assertTrue(repository.recordEmergencyUnlock(lateNight))
    val afterOverride = repository.current()
    assertEquals(lateNight, afterOverride.lastEmergencyUnlockAt)
    assertEquals(1, afterOverride.emergencyUnlockCount)
    assertNull(afterOverride.lastRoutineCompletedAt)
    assertFalse(AppLockPolicy.isLocked("com.example.video", afterOverride, lateNight, zone))

    val morning = Instant.parse("2026-10-08T22:30:00Z")
    repository.recordRoutineCompleted(morning)
    val afterPass = repository.current()
    assertEquals(morning, afterPass.lastRoutineCompletedAt)
    assertFalse(AppLockPolicy.isLocked("com.example.video", afterPass, morning, zone))
  }

  @Test
  fun `emergency unlock is ignored when nothing is locked`() = runBlocking {
    repository.setPackages(setOf("com.example.video"), noon)
    repository.setEnabled(true, noon)
    assertFalse(repository.recordEmergencyUnlock(noon))
    assertNull(repository.current().lastEmergencyUnlockAt)
  }

  @Test
  fun `concurrent picker additions preserve both selections without a settings refresh`() =
      runBlocking {
        val apps = setOf("com.example.video", "com.example.chat")
        apps
            .map { packageName ->
              async { repository.setPackageSelected(packageName, true, apps, noon) }
            }
            .awaitAll()
        assertEquals(apps, repository.current().packages)
      }

  @Test
  fun `picker removal uses the latest selection and retains other pending additions`() =
      runBlocking {
        val video = "com.example.video"
        val chat = "com.example.chat"
        val apps = setOf(video, chat)
        repository.setPackageSelected(video, true, apps, noon)
        repository.setPackageSelected(chat, true, apps, noon)
        repository.setPackageSelected(video, false, apps, noon)
        assertEquals(setOf(chat), repository.current().packages)
      }

  @Test
  fun `picker changes reject unlaunchable protected and malformed packages`() = runBlocking {
    val video = "com.example.video"
    val launchable = setOf(video, "com.android.settings", "not a package")
    for (packageName in launchable + "com.example.missing") {
      repository.setPackageSelected(packageName, true, launchable, noon)
    }
    assertEquals(setOf(video), repository.current().packages)
  }

  @Test
  fun `picker cannot add or remove packages while locked`() = runBlocking {
    val video = "com.example.video"
    val chat = "com.example.chat"
    val apps = setOf(video, chat)
    repository.setPackageSelected(video, true, apps, noon)
    repository.setEnabled(true, noon)
    assertFalse(repository.setPackageSelected(chat, true, apps, lateNight))
    assertFalse(repository.setPackageSelected(video, false, apps, lateNight))
    assertEquals(setOf(video), repository.current().packages)
  }
}
