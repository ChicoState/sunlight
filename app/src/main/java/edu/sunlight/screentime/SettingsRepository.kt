package edu.sunlight.screentime

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val Context.focusDataStore by preferencesDataStore(name = "focus_settings")

// Existing key names; renaming them requires an explicit migration (see infrastructure_plan.md).
private val releaseHourKey = intPreferencesKey("release_hour")
private val releaseMinuteKey = intPreferencesKey("release_minute")
private val sunlightTimesKey = stringSetPreferencesKey("sunlight_times")
private val sunlightMinutesKey = intPreferencesKey("sunlight_minutes")
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59

internal data class RoutineSettings(
    val sunlightTimes: List<LocalTime> = emptyList(),
    val sunlightMinutes: Int = DEFAULT_SUNLIGHT_MINUTES,
) {
  val releaseTime: LocalTime?
    get() = sunlightTimes.firstOrNull()
}

/** Wraps the local DataStore keys and validates every value read from disk. */
internal class SettingsRepository(private val dataStore: DataStore<Preferences>) {
  val settings: Flow<RoutineSettings> =
      dataStore.data
          .map { preferences ->
            val storedTimes = preferences[sunlightTimesKey]
            val hour = preferences[releaseHourKey]
            val minute = preferences[releaseMinuteKey]
            val legacyTime = releaseTimeOrNull(hour, minute)
            val times =
                if (storedTimes != null) decodeSunlightTimes(storedTimes)
                else normalizeSunlightTimes(listOfNotNull(legacyTime))

            if (storedTimes == null && legacyTime != null) {
              dataStore.edit { mutablePreferences ->
                mutablePreferences[sunlightTimesKey] = encodeSunlightTimes(times)
                mutablePreferences.remove(releaseHourKey)
                mutablePreferences.remove(releaseMinuteKey)
              }
            }

            RoutineSettings(
                sunlightTimes = times,
                sunlightMinutes = sunlightMinutesOrDefault(preferences[sunlightMinutesKey]),
            )
          }
          .distinctUntilChanged()

  suspend fun saveSunlightTimes(times: List<LocalTime>) {
    val normalized = normalizeSunlightTimes(times)
    dataStore.edit { preferences ->
      preferences[sunlightTimesKey] = encodeSunlightTimes(normalized)
      preferences.remove(releaseHourKey)
      preferences.remove(releaseMinuteKey)
    }
  }

  suspend fun saveReleaseTime(time: LocalTime) {
    saveSunlightTimes(listOf(time))
  }

  suspend fun saveSunlightMinutes(minutes: Int) {
    dataStore.edit { it[sunlightMinutesKey] = minutes }
  }
}

private fun releaseTimeOrNull(hour: Int?, minute: Int?): LocalTime? {
  val validHour = hour?.takeIf { it in 0..MAX_HOUR }
  val validMinute = minute?.takeIf { it in 0..MAX_MINUTE }
  return if (validHour == null || validMinute == null) null
  else LocalTime.of(validHour, validMinute)
}
