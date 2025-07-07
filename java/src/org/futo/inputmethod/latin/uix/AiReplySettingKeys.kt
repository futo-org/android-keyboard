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

val ENABLE_SWITCH_APPS = SettingsKey(
    key = booleanPreferencesKey("enable_switch_apps"),
    default = true
)
