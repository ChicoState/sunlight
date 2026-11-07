package edu.sunlight.screentime

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRepositoryTest {
  @Test
  fun `empty store yields no wake-up time and the default duration`() = runBlocking {
    val settings = SettingsRepository(FakePreferencesDataStore()).settings.first()
    assertNull(settings.releaseTime)
    assertEquals(DEFAULT_SUNLIGHT_MINUTES, settings.sunlightMinutes)
  }

  @Test
  fun `existing preference keys are still read`() = runBlocking {
    val store =
        FakePreferencesDataStore(
            preferencesOf(
                intPreferencesKey("release_hour") to 6,
                intPreferencesKey("release_minute") to 45,
                intPreferencesKey("sunlight_minutes") to 30,
            )
        )
    val settings = SettingsRepository(store).settings.first()
    assertEquals(LocalTime.of(6, 45), settings.releaseTime)
    assertEquals(30, settings.sunlightMinutes)
  }

  @Test
  fun `corrupt saved values fall back instead of crashing`() = runBlocking {
    val store =
        FakePreferencesDataStore(
            preferencesOf(
                intPreferencesKey("release_hour") to 25,
                intPreferencesKey("release_minute") to 0,
                intPreferencesKey("sunlight_minutes") to 0,
            )
        )
    val settings = SettingsRepository(store).settings.first()
    assertNull(settings.releaseTime)
    assertEquals(DEFAULT_SUNLIGHT_MINUTES, settings.sunlightMinutes)
  }

  @Test
  fun `saved values round trip under the existing keys`() = runBlocking {
    val store = FakePreferencesDataStore()
    val repository = SettingsRepository(store)
    repository.saveReleaseTime(LocalTime.of(13, 5))
    repository.saveSunlightMinutes(1)
    val settings = repository.settings.first()
    assertEquals(LocalTime.of(13, 5), settings.releaseTime)
    assertEquals(1, settings.sunlightMinutes)
    val raw = store.data.first()
    assertEquals(13, raw[intPreferencesKey("release_hour")])
    assertEquals(5, raw[intPreferencesKey("release_minute")])
    assertEquals(1, raw[intPreferencesKey("sunlight_minutes")])
  }
}
