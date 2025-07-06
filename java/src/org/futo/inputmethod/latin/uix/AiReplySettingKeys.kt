package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey

val ENABLE_AI_REPLY = SettingsKey(
    key = booleanPreferencesKey("enable_ai_reply"),
    default = true
)
