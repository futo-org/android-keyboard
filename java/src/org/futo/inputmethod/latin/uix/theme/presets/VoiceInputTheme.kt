package org.futo.inputmethod.latin.uix.theme.presets

import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption

val VoiceInputTheme = ThemeOption(
    dynamic = false,
    key = "VoiceInputTheme",
    name = R.string.voice_input_theme_name,
    available = { true }
) {
    DarkColorScheme
}