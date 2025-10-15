package org.futo.inputmethod.latin.uix.theme.presets

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.wrapDarkColorScheme
import org.futo.inputmethod.latin.uix.wrapLightColorScheme

val DynamicSystemTheme = ThemeOption(
    dynamic = true,
    key = "DynamicSystem",
    name = R.string.theme_dynamic_system,
    available = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    obtainColors = {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw IllegalStateException("DynamicSystemTheme obtainColors called when available() == false")
        }

        val isLight = (it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_NO

        when {
            isLight -> wrapLightColorScheme(dynamicLightColorScheme(it))
            else -> wrapDarkColorScheme(dynamicDarkColorScheme(it))
        }
    }
)

val DynamicDarkTheme = ThemeOption(
    dynamic = true,
    key = "DynamicDark",
    name = R.string.theme_dynamic_dark,
    available = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    obtainColors = {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw IllegalStateException("DynamicDarkTheme obtainColors called when available() == false")
        }

        wrapDarkColorScheme(dynamicDarkColorScheme(it))
    }
)

val DynamicLightTheme = ThemeOption(
    dynamic = true,
    key = "DynamicLight",
    name = R.string.theme_dynamic_light,
    available = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
    obtainColors = {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw IllegalStateException("DynamicLightTheme obtainColors called when available() == false")
        }

        wrapLightColorScheme(dynamicLightColorScheme(it))
    }
)
