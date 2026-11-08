package edu.sunlight.screentime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val currentStreakKey = intPreferencesKey("streak_current")
private val longestStreakKey = intPreferencesKey("streak_longest")
private val lastCompletedDateKey = stringPreferencesKey("streak_last_completed_date")

/** Persists streak data in the app's local Preferences DataStore. */
internal class StreakRepository(
    private val dataStore: DataStore<Preferences>,
) {
  /** Emits the durable streak state, using safe defaults for missing or malformed values. */
  val streak: Flow<StreakState>
    get() = dataStore.data.map { it.toStreakState() }.distinctUntilChanged()

  /** Records one completed normal routine for [completionDate]. */
  suspend fun recordCompletion(completionDate: LocalDate) {
    dataStore.edit { preferences ->
      val updated =
          StreakCalculator.recordCompletion(
              existing = preferences.toStreakState(),
              completionDate = completionDate,
          )

      preferences[currentStreakKey] = updated.currentStreak
      preferences[longestStreakKey] = updated.longestStreak
      updated.lastCompletedDate?.let { preferences[lastCompletedDateKey] = it.toString() }
    }
  }
}

private fun Preferences.toStreakState(): StreakState {
  val currentStreak = this[currentStreakKey]?.takeIf { it >= 0 } ?: 0
  val longestStreak = (this[longestStreakKey]?.takeIf { it >= 0 } ?: 0).coerceAtLeast(currentStreak)
  val lastCompletedDate =
      this[lastCompletedDateKey]?.let { value ->
        try {
          LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
          null
        }
      }

  return StreakState(
      currentStreak = currentStreak,
      longestStreak = longestStreak,
      lastCompletedDate = lastCompletedDate,
  )
}
