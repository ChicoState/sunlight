package edu.sunlight.screentime

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val Context.focusDataStore by preferencesDataStore(name = "focus_settings")

// Existing key names; renaming them requires an explicit migration (see infrastructure_plan.md).
private val releaseHourKey = intPreferencesKey("release_hour")
private val releaseMinuteKey = intPreferencesKey("release_minute")
private val sunlightMinutesKey = intPreferencesKey("sunlight_minutes")
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59

internal data class RoutineSettings(
    val releaseTime: LocalTime? = null,
    val sunlightMinutes: Int = DEFAULT_SUNLIGHT_MINUTES,
)

/** Wraps the local DataStore keys and validates every value read from disk. */
internal class SettingsRepository(private val dataStore: DataStore<Preferences>) {
  val settings: Flow<RoutineSettings> =
      dataStore.data
          .map { preferences ->
            RoutineSettings(
                releaseTime =
                    releaseTimeOrNull(preferences[releaseHourKey], preferences[releaseMinuteKey]),
                sunlightMinutes = sunlightMinutesOrDefault(preferences[sunlightMinutesKey]),
            )
          }
          .distinctUntilChanged()

  suspend fun saveReleaseTime(time: LocalTime) {
    dataStore.edit { preferences ->
      preferences[releaseHourKey] = time.hour
      preferences[releaseMinuteKey] = time.minute
    }
  }

  suspend fun saveSunlightMinutes(minutes: Int) {
    dataStore.edit { it[sunlightMinutesKey] = sunlightMinutesOrDefault(minutes) }
  }
}

private fun releaseTimeOrNull(hour: Int?, minute: Int?): LocalTime? {
  val validHour = hour?.takeIf { it in 0..MAX_HOUR }
  val validMinute = minute?.takeIf { it in 0..MAX_MINUTE }
  return if (validHour == null || validMinute == null) null
  else LocalTime.of(validHour, validMinute)
}
