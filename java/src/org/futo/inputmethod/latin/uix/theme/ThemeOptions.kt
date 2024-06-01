package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDDarkPurple
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialLight
import org.futo.inputmethod.latin.uix.theme.presets.CottonCandy
import org.futo.inputmethod.latin.uix.theme.presets.DeepSeaDark
import org.futo.inputmethod.latin.uix.theme.presets.DeepSeaLight
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicLightTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.Emerald
import org.futo.inputmethod.latin.uix.theme.presets.Snowfall
import org.futo.inputmethod.latin.uix.theme.presets.SteelGray
import org.futo.inputmethod.latin.uix.theme.presets.Sunflower
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme

data class ThemeOption(
    val dynamic: Boolean,
    val key: String,
    @StringRes val name: Int,
    val available: (Context) -> Boolean,
    val obtainColors: (Context) -> ColorScheme,
)

val ThemeOptions = mapOf(
    VoiceInputTheme.key to VoiceInputTheme,

    DynamicSystemTheme.key to DynamicSystemTheme,
    DynamicDarkTheme.key to DynamicDarkTheme,
    DynamicLightTheme.key to DynamicLightTheme,

    ClassicMaterialDark.key to ClassicMaterialDark,
    ClassicMaterialLight.key to ClassicMaterialLight,
    AMOLEDDarkPurple.key to AMOLEDDarkPurple,

    Sunflower.key to Sunflower,
    Snowfall.key to Snowfall,
    SteelGray.key to SteelGray,
    Emerald.key to Emerald,
    CottonCandy.key to CottonCandy,

    DeepSeaLight.key to DeepSeaLight,
    DeepSeaDark.key to DeepSeaDark,

)

val ThemeOptionKeys = ThemeOptions.keys