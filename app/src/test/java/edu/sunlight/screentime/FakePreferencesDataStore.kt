package edu.sunlight.screentime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory DataStore that applies updates synchronously, like a single-writer store. */
internal class FakePreferencesDataStore(initial: Preferences = emptyPreferences()) :
    DataStore<Preferences> {
  private val current = MutableStateFlow(initial)

  override val data: Flow<Preferences> = current

  override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
      transform(current.value).also { current.value = it }
}
