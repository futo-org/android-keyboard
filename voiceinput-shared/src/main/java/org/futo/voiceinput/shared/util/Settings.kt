package org.futo.voiceinput.shared.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class ValueFromSettings<T>(val key: Preferences.Key<T>, val default: T) {
    private var _value = default

    val value: T
        get() {
            return _value
        }

    suspend fun load(context: Context, onResult: ((T) -> Unit)? = null) {
        val valueFlow: Flow<T> =
            context.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

        valueFlow.collect {
            _value = it

            if (onResult != null) {
                onResult(it)
            }
        }
    }

    suspend fun get(context: Context): T {
        val valueFlow: Flow<T> =
            context.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

        return valueFlow.first()
    }
}


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settingsVoice")
val ENABLE_SOUND = booleanPreferencesKey("enable_sounds")
val VERBOSE_PROGRESS = booleanPreferencesKey("verbose_progress")
val ENABLE_ENGLISH = booleanPreferencesKey("enable_english")
val ENABLE_MULTILINGUAL = booleanPreferencesKey("enable_multilingual")
val DISALLOW_SYMBOLS = booleanPreferencesKey("disallow_symbols")

val ENGLISH_MODEL_INDEX = intPreferencesKey("english_model_index")
val ENGLISH_MODEL_INDEX_DEFAULT = 0

val MULTILINGUAL_MODEL_INDEX = intPreferencesKey("multilingual_model_index")
val MULTILINGUAL_MODEL_INDEX_DEFAULT = 1

val LANGUAGE_TOGGLES = stringSetPreferencesKey("enabled_languages")