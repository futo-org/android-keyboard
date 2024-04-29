package org.futo.inputmethod.latin.uix.settings

import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.dataStore
import org.futo.inputmethod.latin.uix.getSetting

data class DataStoreItem<T>(val value: T, val setValue: (T) -> Job)

@Composable
fun <T> useDataStoreValueBlocking(key: Preferences.Key<T>, default: T): T {
    val context = LocalContext.current

    val initialValue = remember {
        runBlocking {
            context.getSetting(key, default)
        }
    }

    val valueFlow: Flow<T> = remember {
        context.dataStore.data.map { preferences ->
            preferences[key] ?: default
        }
    }

    return valueFlow.collectAsState(initial = initialValue).value
}

@Composable
fun <T> useDataStoreValueBlocking(v: SettingsKey<T>): T {
    return useDataStoreValueBlocking(key = v.key, default = v.default)
}

@Composable
fun <T> useDataStore(key: Preferences.Key<T>, default: T): DataStoreItem<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val enableSoundFlow: Flow<T> = remember {
        context.dataStore.data.map {
                preferences -> preferences[key] ?: default
        }
    }

    val value = enableSoundFlow.collectAsState(initial = default).value!!

    val setValue = { newValue: T ->
        coroutineScope.launch {
            context.dataStore.edit { preferences ->
                preferences[key] = newValue
            }
        }
    }

    return DataStoreItem(value, setValue)
}


@Composable
fun <T> useDataStore(key: SettingsKey<T>): DataStoreItem<T> {
    return useDataStore(key.key, key.default)
}

@Composable
fun useSharedPrefsBool(key: String, default: Boolean): DataStoreItem<Boolean> {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val sharedPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val value = remember { mutableStateOf(sharedPrefs.getBoolean(key, default)) }

    // This is not the most efficient way to do this... but it works for a settings menu
    DisposableEffect(Unit) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                if (key == changedKey) {
                    value.value = sharedPreferences.getBoolean(key, value.value)
                }
            }

        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)

        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val setValue = { newValue: Boolean ->
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                sharedPrefs.edit {
                    putBoolean(key, newValue)
                }
            }
        }
    }

    return DataStoreItem(value.value, setValue)
}