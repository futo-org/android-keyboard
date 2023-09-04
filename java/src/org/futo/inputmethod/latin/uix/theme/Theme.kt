package org.futo.inputmethod.latin.uix.theme

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun StatusBarColorSetter() {
    val backgroundColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    LaunchedEffect(backgroundColor) {
        val window = (context as Activity).window
        val color = backgroundColor.copy(alpha = 0.75f).toArgb()

        window.statusBarColor = color
        window.navigationBarColor = color

        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
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