package edu.sunlight.screentime

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock")

internal val Context.appLockRepository: AppLockRepository
  get() = AppLockRepository(applicationContext.appLockDataStore)

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60

/** Stores the nightly lock in its own DataStore file, separate from routine settings. */
internal class AppLockRepository(
    private val store: DataStore<Preferences>,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
  val settings: Flow<AppLockSettings> = store.data.map(::settingsFrom)

  suspend fun current(): AppLockSettings = settings.first()

  suspend fun setEnabled(enabled: Boolean, now: Instant): Boolean =
      editUnlessLocked(now) { it[Keys.enabled] = enabled }

  suspend fun setLockTime(time: LocalTime, now: Instant): Boolean =
      editUnlessLocked(now) {
        it[Keys.hour] = time.hour
        it[Keys.minute] = time.minute
      }

  /** Callers sanitize against installed apps; malformed names are dropped here as well. */
  suspend fun setPackages(packages: Set<String>, now: Instant): Boolean =
      editUnlessLocked(now) {
        it[Keys.packages] = packages.filter(AppSelection::isValidPackageName).toSet()
      }

  /** Called only when a real (non-simulated) morning routine reaches COMPLETE. */
  suspend fun recordRoutineCompleted(now: Instant) {
    store.edit { it[Keys.lastPass] = now.toEpochMilli() }
  }

  /** Records a deliberate emergency unlock. Returns false when nothing was locked. */
  suspend fun recordEmergencyUnlock(now: Instant): Boolean {
    var recorded = false
    store.edit { preferences ->
      val settings = settingsFrom(preferences)
      if (AppLockPolicy.status(settings, now, zone()) is AppLockStatus.Locked) {
        preferences[Keys.lastEmergency] = now.toEpochMilli()
        preferences[Keys.emergencyCount] = settings.emergencyUnlockCount + 1
        recorded = true
      }
    }
    return recorded
  }

  private suspend fun editUnlessLocked(
      now: Instant,
      change: (MutablePreferences) -> Unit,
  ): Boolean {
    var saved = false
    store.edit { preferences ->
      if (AppLockPolicy.canEdit(settingsFrom(preferences), now, zone())) {
        change(preferences)
        preferences[Keys.armedAt] = now.toEpochMilli()
        saved = true
      }
    }
    return saved
  }

  private object Keys {
    val enabled = booleanPreferencesKey("lock_enabled")
    val hour = intPreferencesKey("lock_hour")
    val minute = intPreferencesKey("lock_minute")
    val packages = stringSetPreferencesKey("locked_packages")
    val armedAt = longPreferencesKey("armed_at")
    val lastPass = longPreferencesKey("last_routine_completed_at")
    val lastEmergency = longPreferencesKey("last_emergency_unlock_at")
    val emergencyCount = intPreferencesKey("emergency_unlock_count")
  }

  private companion object {
    fun settingsFrom(preferences: Preferences): AppLockSettings {
      val hour = preferences[Keys.hour]
      val minute = preferences[Keys.minute]
      val lockTime =
          if (hour in 0 until HOURS_PER_DAY && minute in 0 until MINUTES_PER_HOUR) {
            LocalTime.of(requireNotNull(hour), requireNotNull(minute))
          } else {
            DEFAULT_LOCK_TIME
          }
      return AppLockSettings(
          enabled = preferences[Keys.enabled] ?: false,
          lockTime = lockTime,
          packages =
              preferences[Keys.packages].orEmpty().filter(AppSelection::isValidPackageName).toSet(),
          armedAt = preferences[Keys.armedAt]?.let(Instant::ofEpochMilli),
          lastRoutineCompletedAt = preferences[Keys.lastPass]?.let(Instant::ofEpochMilli),
          lastEmergencyUnlockAt = preferences[Keys.lastEmergency]?.let(Instant::ofEpochMilli),
          emergencyUnlockCount = (preferences[Keys.emergencyCount] ?: 0).coerceAtLeast(0),
      )
    }
  }
}
