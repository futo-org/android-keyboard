package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

val VoiceInputTheme = ThemeOption(
    dynamic = false,
    key = "VoiceInputTheme",
    name = R.string.voice_input_theme_name,
    available = { true }
) {
    DarkColorScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(VoiceInputTheme)
}