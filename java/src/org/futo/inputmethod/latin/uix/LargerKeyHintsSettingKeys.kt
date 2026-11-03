package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.floatPreferencesKey

// Scales the small hint characters on keys, as a multiplier on top of the theme's own hint size.
// 1.0 (the default) draws hints exactly as before.
val KEY_HINT_SCALE = SettingsKey(
    key = floatPreferencesKey("key_hint_scale"),
    default = 1.0f
)
