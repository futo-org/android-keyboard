package org.futo.inputmethod.latin.uix.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.dataStore

class DataStoreCache(
    val currPreferences: Preferences
)

val LocalDataStoreCache = staticCompositionLocalOf<DataStoreCache?> {
    null
}

@Composable
fun DataStoreCacheProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val initialPrefs = remember {
        runBlocking {
            context.dataStore.data.first()
        }
    }
    val prefs = context.dataStore.data.collectAsState(initialPrefs)

    val cache = remember(prefs.value) {
        DataStoreCache(prefs.value)
    }

    CompositionLocalProvider(LocalDataStoreCache provides cache) {
        content()
    }
}

data class DataStoreItem<T>(val value: T, val setValue: (T) -> Job)

@Composable
fun <T> useDataStore(key: Preferences.Key<T>, default: T, blocking: Boolean = false): DataStoreItem<T> {
    val context = LocalContext.current
    val cache = LocalDataStoreCache.current
    val coroutineScope = rememberCoroutineScope()

    val value = cache?.currPreferences?.get(key) ?: default

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
fun <T> useDataStoreValue(settingsKey: SettingsKey<T>): T {
    val cache = LocalDataStoreCache.current

    return cache?.currPreferences?.get(settingsKey.key) ?: settingsKey.default
}


@Composable
fun <T> useDataStore(key: SettingsKey<T>, blocking: Boolean = false): DataStoreItem<T> {
    return useDataStore(key.key, key.default, blocking)
}

@Composable
fun<T> useSharedPrefsGeneric(key: String, default: T, get: (SharedPreferences, String, T) -> T, put: (SharedPreferences, String, T) -> Unit): DataStoreItem<T> {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val sharedPrefs = remember { PreferenceUtils.getDefaultSharedPreferences(context) }

    val value = remember { mutableStateOf(get(sharedPrefs, key, default)) }

    // This is not the most efficient way to do this... but it works for a settings menu
    DisposableEffect(Unit) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                if (key == changedKey) {
                    value.value = get(sharedPreferences, key, value.value)
                }
            }

        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)

        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val setValue = { newValue: T ->
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                put(sharedPrefs, key, newValue)
            }
        }
    }

    return DataStoreItem(value.value, setValue)
}


@Composable
fun useSharedPrefsBool(key: String, default: Boolean): DataStoreItem<Boolean> {
    return useSharedPrefsGeneric(key, default,
        get = { sharedPreferences, k, d ->
            sharedPreferences.getBoolean(k, d)
        },
        put = { sharedPreferences, k, v ->
            sharedPreferences.edit {
                putBoolean(k, v)
            }
        }
    )
}

@Composable
fun useSharedPrefsInt(key: String, default: Int): DataStoreItem<Int> {
    return useSharedPrefsGeneric(key, default,
        get = { sharedPreferences, k, d ->
            sharedPreferences.getInt(k, d)
        },
        put = { sharedPreferences, k, v ->
            sharedPreferences.edit {
                putInt(k, v)
            }
        }
    )
}


@Composable
private fun<T> SyncDataStoreToPreferences(key: SettingsKey<T>, update: (newValue: T, editor: SharedPreferences.Editor) -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { PreferenceUtils.getDefaultSharedPreferences(context) }
    val value = useDataStore(key)

    LaunchedEffect(value) {
        val edit = sharedPrefs.edit {
            update(value.value, this)
        }
    }
}

@Composable
fun SyncDataStoreToPreferencesInt(key: SettingsKey<Int>, sharedPreference: String) {
    SyncDataStoreToPreferences(key) { value, editor ->
        editor.putInt(sharedPreference, value)
    }
}