package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDDarkPurple
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicLightTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme

data class ThemeOption(
    val dynamic: Boolean,
    val key: String,
    val name: String, // TODO: @StringRes Int
    val available: (Context) -> Boolean,
    val obtainColors: (Context) -> ColorScheme,
)

val ThemeOptions = hashMapOf(
    DynamicSystemTheme.key to DynamicSystemTheme,
    DynamicDarkTheme.key to DynamicDarkTheme,
    DynamicLightTheme.key to DynamicLightTheme,

    ClassicMaterialDark.key to ClassicMaterialDark,
    VoiceInputTheme.key to VoiceInputTheme,
    AMOLEDDarkPurple.key to AMOLEDDarkPurple,
)

val ThemeOptionKeys = arrayOf(
    DynamicSystemTheme.key,
    DynamicDarkTheme.key,
    DynamicLightTheme.key,

    ClassicMaterialDark.key,
    VoiceInputTheme.key,
    AMOLEDDarkPurple.key,
)