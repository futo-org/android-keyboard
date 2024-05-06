package org.futo.inputmethod.latin.uix

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

suspend fun <T> Context.getSetting(key: Preferences.Key<T>, default: T): T {
    val valueFlow: Flow<T> =
        this.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

    return valueFlow.first()
}

fun <T> Context.getSettingFlow(key: Preferences.Key<T>, default: T): Flow<T> {
    return dataStore.data.map { preferences -> preferences[key] ?: default }
}

suspend fun <T> Context.setSetting(key: Preferences.Key<T>, value: T) {
    this.dataStore.edit { preferences ->
        preferences[key] = value
    }
}


fun <T> Context.getSettingBlocking(key: Preferences.Key<T>, default: T): T {
    val context = this

    return runBlocking {
        context.getSetting(key, default)
    }
}

fun <T> Context.getSettingBlocking(key: SettingsKey<T>): T {
    return getSettingBlocking(key.key, key.default)
}

fun <T> Context.setSettingBlocking(key: Preferences.Key<T>, value: T) {
    val context = this
    runBlocking {
        context.setSetting(key, value)
    }
}

fun <T> LifecycleOwner.deferGetSetting(key: Preferences.Key<T>, default: T, onObtained: (T) -> Unit): Job {
    val context = (this as Context)
    return lifecycleScope.launch {
        withContext(Dispatchers.Default) {
            val value = context.getSetting(key, default)

            withContext(Dispatchers.Main) {
                onObtained(value)
            }
        }
    }
}

fun <T> LifecycleOwner.deferSetSetting(key: Preferences.Key<T>, value: T): Job {
    val context = (this as Context)
    return lifecycleScope.launch {
        withContext(Dispatchers.Default) {
            context.setSetting(key, value)
        }
    }
}

data class SettingsKey<T>(
    val key: Preferences.Key<T>,
    val default: T
)

suspend fun <T> Context.getSetting(key: SettingsKey<T>): T {
    return getSetting(key.key, key.default)
}

fun <T> Context.getSettingFlow(key: SettingsKey<T>): Flow<T> {
    return getSettingFlow(key.key, key.default)
}

suspend fun <T> Context.setSetting(key: SettingsKey<T>, value: T) {
    return setSetting(key.key, value)
}

fun <T> LifecycleOwner.deferGetSetting(key: SettingsKey<T>, onObtained: (T) -> Unit): Job {
    return deferGetSetting(key.key, key.default, onObtained)
}

fun <T> LifecycleOwner.deferSetSetting(key: SettingsKey<T>, value: T): Job {
    return deferSetSetting(key.key, value)
}


val THEME_KEY = SettingsKey(
    key = stringPreferencesKey("activeThemeOption"),
    default = DynamicSystemTheme.key
)

val USE_SYSTEM_VOICE_INPUT = SettingsKey(
    key = booleanPreferencesKey("useSystemVoiceInput"),
    default = false
)

val USE_TRANSFORMER_FINETUNING = SettingsKey(
    key = booleanPreferencesKey("useTransformerFinetuning"),
    default = false
)

val SUGGESTION_BLACKLIST = SettingsKey(
    key = stringSetPreferencesKey("suggestionBlacklist"),
    default = setOf()
)

val SHOW_EMOJI_SUGGESTIONS = SettingsKey(
    key = booleanPreferencesKey("suggestEmojis"),
    default = true
)