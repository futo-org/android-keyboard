package org.futo.inputmethod.latin.uix.theme.presets

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption

val DynamicSystemTheme = ThemeOption(
    dynamic = true,
    key = "DynamicSystem",
    name = "Dynamic System",
    available = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    obtainColors = {
        val uiModeManager = it.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        when (uiModeManager.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> dynamicDarkColorScheme(it)
            UiModeManager.MODE_NIGHT_NO -> dynamicLightColorScheme(it)
            UiModeManager.MODE_NIGHT_AUTO -> {
                val currentNightMode = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if(currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
                    dynamicLightColorScheme(it)
                } else {
                    dynamicDarkColorScheme(it)
                }
            }
            else -> dynamicDarkColorScheme(it)
        }
    }
)

val DynamicDarkTheme = ThemeOption(
    dynamic = true,
    key = "DynamicDark",
    name = "Dynamic Dark",
    available = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    obtainColors = {
        dynamicDarkColorScheme(it)
    }
)

val DynamicLightTheme = ThemeOption(
    dynamic = true,
    key = "DynamicLight",
    name = "Dynamic Light",
    available = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    obtainColors = {
        dynamicLightColorScheme(it)
    }
)
