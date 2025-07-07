package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val ENABLE_AI_REPLY = SettingsKey(
    key = booleanPreferencesKey("enable_ai_reply"),
    default = true
)

val AI_REPLY_PROMPT = SettingsKey(
    key = stringPreferencesKey("ai_reply_prompt"),
    default = "Write a short helpful reply"
)

val GROQ_REPLY_API_KEY = SettingsKey(
    key = stringPreferencesKey("groq_reply_api_key"),
    default = ""
)

val GROQ_REPLY_MODEL = SettingsKey(
    key = stringPreferencesKey("groq_reply_model"),
    default = "llama3-70b-8192"
)

val ENABLE_SWITCH_APPS = SettingsKey(
    key = booleanPreferencesKey("enable_switch_apps"),
    default = true
)
