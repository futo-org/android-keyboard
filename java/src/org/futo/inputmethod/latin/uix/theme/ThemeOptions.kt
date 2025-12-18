package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import androidx.annotation.StringRes
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.actions.BugInfo
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDDarkPurple
import org.futo.inputmethod.latin.uix.theme.presets.CatppuccinMocha
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialLight
import org.futo.inputmethod.latin.uix.theme.presets.CottonCandy
import org.futo.inputmethod.latin.uix.theme.presets.DeepSeaDark
import org.futo.inputmethod.latin.uix.theme.presets.DeepSeaLight
import org.futo.inputmethod.latin.uix.theme.presets.DefaultDarkScheme
import org.futo.inputmethod.latin.uix.theme.presets.DefaultLightScheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicLightTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.Emerald
import org.futo.inputmethod.latin.uix.theme.presets.Gradient1
import org.futo.inputmethod.latin.uix.theme.presets.HotDog
import org.futo.inputmethod.latin.uix.theme.presets.Snowfall
import org.futo.inputmethod.latin.uix.theme.presets.SteelGray
import org.futo.inputmethod.latin.uix.theme.presets.Sunflower
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.inputmethod.latin.uix.theme.presets.DevTheme
import org.futo.inputmethod.latin.uix.theme.presets.HighContrastYellow

data class ThemeOption(
    val dynamic: Boolean,
    val key: String,
    @StringRes val name: Int,
    val available: (Context) -> Boolean,
    val obtainColors: (Context) -> KeyboardColorScheme,
)

val ThemeOptions = mapOf(
    DefaultDarkScheme.key to DefaultDarkScheme,
    DefaultLightScheme.key to DefaultLightScheme,

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

    Gradient1.key to Gradient1,
    VoiceInputTheme.key to VoiceInputTheme,
    HotDog.key to HotDog,
    DevTheme.key to DevTheme,
    HighContrastYellow.key to HighContrastYellow,
    CatppuccinMocha.key to CatppuccinMocha,
)

val ThemeOptionKeys = ThemeOptions.keys

fun defaultThemeOption(context: Context): ThemeOption =
    if(context.resources.getBoolean(R.bool.use_dev_styling)) {
        DevTheme
    } else {
        if(DynamicSystemTheme.available(context)) {
            DynamicSystemTheme
        } else {
            DefaultDarkScheme
        }
    }

fun getThemeOption(context: Context, key: String): ThemeOption? {
    return ThemeOptions[key] ?: run {
        return ZipThemes.ThemeFileName.fromSetting(key)?.let { name ->
            ThemeOption(
                dynamic = false,
                key = key,
                name = 0,
                available = { true },
                obtainColors = {
                    try {
                        ZipThemes.loadScheme(context, name)
                    } catch(e: Exception) {
                        BugViewerState.pushBug(BugInfo(
                            name = "Theme $name",
                            details = e.toString(),
                        ))
                        defaultThemeOption(context).obtainColors(it)
                    }
                }
            )
        }
    }
}

fun ThemeOption?.orDefault(context: Context): ThemeOption {
    val themeOptionFromSettings = this
    val themeOption = when {
        themeOptionFromSettings == null -> defaultThemeOption(context)
        !themeOptionFromSettings.available(context) -> defaultThemeOption(context)
        else -> themeOptionFromSettings
    }

    return themeOption
}