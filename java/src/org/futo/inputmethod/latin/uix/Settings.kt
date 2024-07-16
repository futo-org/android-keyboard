package org.futo.inputmethod.latin.uix

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.preference.PreferenceManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.ActiveSubtype
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme

// Used before first unlock (direct boot)
private object DefaultDataStore : DataStore<Preferences> {
    private var activePreferences = preferencesOf(
        ActiveSubtype.key to "en_US:",
        SubtypesSetting.key to setOf("en_US:"),
        THEME_KEY.key to ClassicMaterialDark.key,
        KeyHintsSetting.key to true
    )

    var subtypesInitialized = false

    suspend fun updateSubtypes(subtypes: Set<String>) {
        val newPreferences = activePreferences.toMutablePreferences()
        newPreferences[SubtypesSetting.key] = subtypes

        activePreferences = newPreferences
        sharedData.emit(activePreferences)
    }

    val sharedData = MutableSharedFlow<Preferences>(1)

    override val data: Flow<Preferences>
        get() {
            return unlockedDataStore?.data ?: sharedData
        }

    init {
        sharedData.tryEmit(activePreferences)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        return unlockedDataStore?.updateData(transform) ?: run {
            val newActiveSubtype = transform(activePreferences)[ActiveSubtype.key]
            if(newActiveSubtype != null && newActiveSubtype != activePreferences[ActiveSubtype.key]) {
                val newPreferences = activePreferences.toMutablePreferences()
                newPreferences[ActiveSubtype.key] = newActiveSubtype
                activePreferences = newPreferences
                sharedData.emit(newPreferences)
            }

            return activePreferences
        }
    }
}

// Set and used after first unlock (direct boot)
private var unlockedDataStore: DataStore<Preferences>? = null

// Initializes unlockedDataStore, or uses DefaultDataStore if device is still locked (direct boot)
@OptIn(DelicateCoroutinesApi::class)
val Context.dataStore: DataStore<Preferences>
    get() {
        return unlockedDataStore ?: run {
            val userManager = getSystemService(Context.USER_SERVICE) as UserManager
            return if (userManager.isUserUnlocked) {
                // The device has been unlocked
                val newDataStore = PreferenceDataStoreFactory.create(
                    corruptionHandler = null,
                    migrations = listOf(),
                    scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                ) {
                    applicationContext.preferencesDataStoreFile("settings")
                }

                unlockedDataStore = newDataStore

                // Send new values to the DefaultDataStore for any listeners
                GlobalScope.launch {
                    newDataStore.data.collect { value ->
                        DefaultDataStore.sharedData.emit(value)
                    }
                }

                newDataStore
            } else {
                // The device is still locked, return default data store
                if (!DefaultDataStore.subtypesInitialized) {
                    DefaultDataStore.subtypesInitialized = true
                    GlobalScope.launch {
                        DefaultDataStore.updateSubtypes(Subtypes.getDirectBootInitialLayouts(this@dataStore))
                    }
                }

                DefaultDataStore
            }
        }
    }


object PreferenceUtils {
    fun getDefaultSharedPreferences(context: Context): SharedPreferences {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        return if (userManager.isUserUnlocked) {
            PreferenceManager.getDefaultSharedPreferences(context)
        } else {
            PreferenceManager.getDefaultSharedPreferences(context.createDeviceProtectedStorageContext())
        }
    }
}

val Context.isDirectBootUnlocked: Boolean
    get() {
        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        return userManager.isUserUnlocked
    }


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