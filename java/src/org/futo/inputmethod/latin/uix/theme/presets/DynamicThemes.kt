package org.futo.inputmethod.latin.uix.theme.presets

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption

val DynamicSystemTheme = ThemeOption(
    dynamic = true,
    key = "DynamicSystem",
    name = R.string.dynamic_system_theme_name,
    available = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    obtainColors = {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw IllegalStateException("DynamicSystemTheme obtainColors called when available() == false")
        }

        val uiModeManager = it.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        when (uiModeManager.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> dynamicDarkColorScheme(it)
            UiModeManager.MODE_NIGHT_NO -> dynamicLightColorScheme(it)
            else -> {
                val currentNightMode = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if(currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
                    dynamicLightColorScheme(it)
                } else {
                    dynamicDarkColorScheme(it)
                }
            }
        }
    }
)

val DynamicDarkTheme = ThemeOption(
    dynamic = true,
    key = "DynamicDark",
    name = R.string.dynamic_dark_theme_name,
    available = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    obtainColors = {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw IllegalStateException("DynamicDarkTheme obtainColors called when available() == false")
        }

        dynamicDarkColorScheme(it)
    }
)

val DynamicLightTheme = ThemeOption(
    dynamic = true,
    key = "DynamicLight",
    name = R.string.dynamic_light_theme_name,
    available = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    obtainColors = {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw IllegalStateException("DynamicLightTheme obtainColors called when available() == false")
        }

        dynamicLightColorScheme(it)
    }
)
