package org.futo.inputmethod.latin.uix

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LifecycleCoroutineScope
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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

suspend fun <T> Context.getSetting(key: Preferences.Key<T>, default: T): T {
    val valueFlow: Flow<T> =
        this.dataStore.data.map { preferences -> preferences[key] ?: default }.take(1)

    return valueFlow.first()
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
            onObtained(value)
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


val THEME_KEY = stringPreferencesKey("activeThemeOption")