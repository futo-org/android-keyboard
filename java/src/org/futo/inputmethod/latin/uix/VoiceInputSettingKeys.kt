package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

val ENABLE_SOUND = SettingsKey(
    key = booleanPreferencesKey("enable_sounds"),
    default = true
)

val VERBOSE_PROGRESS = SettingsKey(
    key = booleanPreferencesKey("verbose_progress"),
    default = false
)

val ENABLE_ENGLISH = SettingsKey(
    key = booleanPreferencesKey("enable_english"),
    default = true
)

val ENABLE_MULTILINGUAL = SettingsKey(
    key = booleanPreferencesKey("enable_multilingual"),
    default = false
)

val DISALLOW_SYMBOLS = SettingsKey(
    key = booleanPreferencesKey("disallow_symbols"),
    default = true
)

val PREFER_BLUETOOTH = SettingsKey(
    key = booleanPreferencesKey("prefer_bluetooth_recording"),
    default = false
)

val CAN_EXPAND_SPACE = SettingsKey(
    key = booleanPreferencesKey("can_expand_space"),
    default = true
)

val AUDIO_FOCUS = SettingsKey(
    key = booleanPreferencesKey("request_audio_focus"),
    default = true
)

val USE_VAD_AUTOSTOP = SettingsKey(
    key = booleanPreferencesKey("use_vad_autostop"),
    default = true
)

val ENGLISH_MODEL_INDEX = SettingsKey(
    key = intPreferencesKey("english_model_index"),
    default = 0
)

val MULTILINGUAL_MODEL_INDEX = SettingsKey(
    key = intPreferencesKey("multilingual_model_index"),
    default = 1
)

val LANGUAGE_TOGGLES = SettingsKey(
    key = stringSetPreferencesKey("enabled_languages"),
    default = setOf()
)

val USE_PERSONAL_DICT = SettingsKey(
    key = booleanPreferencesKey("use_personal_dict_voice_input"),
    default = true
)

// Dictation command settings
val DICTATION_COMMANDS_ENABLED = SettingsKey(
    key = booleanPreferencesKey("dictation_commands_enabled"),
    default = true
)

val DICTATION_FORMATTING = SettingsKey(
    key = booleanPreferencesKey("dictation_formatting"),
    default = true
)

val DICTATION_CAPITALIZATION = SettingsKey(
    key = booleanPreferencesKey("dictation_capitalization"),
    default = true
)

val DICTATION_PUNCTUATION = SettingsKey(
    key = booleanPreferencesKey("dictation_punctuation"),
    default = true
)

val DICTATION_SYMBOLS = SettingsKey(
    key = booleanPreferencesKey("dictation_symbols"),
    default = true
)

val DICTATION_MATH = SettingsKey(
    key = booleanPreferencesKey("dictation_math"),
    default = true
)

val DICTATION_CURRENCY = SettingsKey(
    key = booleanPreferencesKey("dictation_currency"),
    default = true
)

val DICTATION_EMOTICONS = SettingsKey(
    key = booleanPreferencesKey("dictation_emoticons"),
    default = true
)

val DICTATION_IP_MARKS = SettingsKey(
    key = booleanPreferencesKey("dictation_ip_marks"),
    default = true
)