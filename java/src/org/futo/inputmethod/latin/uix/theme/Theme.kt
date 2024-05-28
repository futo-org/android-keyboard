package org.futo.inputmethod.latin.uix.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.Window
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.settings.useDataStoreValueBlocking
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import kotlin.math.sqrt

val DarkColorScheme = darkColorScheme(
    primary = Slate500,
    onPrimary = Slate50,

    primaryContainer = Slate700,
    onPrimaryContainer = Slate50,

    secondary = Slate700,
    onSecondary = Slate50,

    secondaryContainer = Slate600,
    onSecondaryContainer = Slate50,

    tertiary = Stone700,
    onTertiary = Stone50,

    tertiaryContainer = Stone600,
    onTertiaryContainer = Stone50,

    background = Slate900,
    onBackground = Slate50,

    surface = Slate800,
    onSurface = Slate50,

    outline = Slate300,

    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300
)

fun applyWindowColors(window: Window, @ColorInt color: Int, statusBar: Boolean) {
    if(statusBar) {
        window.statusBarColor = color
    }
    window.navigationBarColor = color

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val view = window.decorView
        val uiFlags = view.systemUiVisibility

        val luminance = sqrt(
            0.299 * android.graphics.Color.red(color) / 255.0
                    + 0.587 * android.graphics.Color.green(color) / 255.0
                    + 0.114 * android.graphics.Color.blue(color) / 255.0
        )

        if (luminance > 0.5 && color != android.graphics.Color.TRANSPARENT) {
            view.systemUiVisibility = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            view.systemUiVisibility = uiFlags and (View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv())
        }
    }
}

@Composable
fun StatusBarColorSetter() {
    val backgroundColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    LaunchedEffect(backgroundColor) {
        applyWindowColors((context as Activity).window, backgroundColor.toArgb(), statusBar = false)
    }
}

@Composable
fun UixThemeWrapper(colorScheme: ColorScheme, content: @Composable () -> Unit) {
    MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
    )
}

fun ThemeOption?.ensureAvailable(context: Context): ThemeOption? {
    return if(this == null) {
        null
    } else {
        if(!this.available(context)) {
            null
        } else {
            this
        }
    }
}

@Composable
fun UixThemeAuto(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val themeIdx = useDataStoreValueBlocking(THEME_KEY.key, THEME_KEY.default)

    val theme: ThemeOption = themeIdx?.let { ThemeOptions[it].ensureAvailable(context) }
        ?: VoiceInputTheme

    val colors = remember(theme.key) { theme.obtainColors(context) }

    UixThemeWrapper(colorScheme = colors, content)
}