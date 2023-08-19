package org.futo.inputmethod.latin.uix.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DarkColorScheme = darkColorScheme(
    primary = Slate600,
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

@Composable
fun WhisperVoiceInputTheme(content: @Composable () -> Unit) {
    // TODO: Switch light or dark mode
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }

        else -> DarkColorScheme
    }

    //val colorScheme = DarkColorScheme // TODO: Figure out light/dynamic if it's worth it


    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            if(view.context is Activity) {
                val window = (view.context as Activity).window
                window.statusBarColor = colorScheme.primary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    false
            }
        }
    }


    MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,

    )
}