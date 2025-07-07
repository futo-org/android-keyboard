package org.futo.inputmethod.latin.uix.theme.presets

import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.CustomAccentColor
import org.futo.inputmethod.latin.uix.CustomBaseColor
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption

private fun safeColor(code: String, fallback: String): Color {
    return try { Color(parseColor(code)) } catch (_: IllegalArgumentException) { Color(parseColor(fallback)) }
}

private fun colorsFrom(accent: Color, base: Color) = extendedDarkColorScheme(
    primary = accent,
    onPrimary = Color.Black,
    primaryContainer = accent,
    onPrimaryContainer = Color.White,
    secondary = accent,
    onSecondary = Color.Black,
    secondaryContainer = base,
    onSecondaryContainer = Color.White,
    tertiary = accent,
    onTertiary = Color.Black,
    tertiaryContainer = accent,
    onTertiaryContainer = Color.White,
    error = Color(0xFFFA6060),
    onError = Color.Black,
    errorContainer = Color(0xFF730000),
    onErrorContainer = Color.White,
    outline = base.copy(alpha = 0.5f),
    outlineVariant = base.copy(alpha = 0.2f),
    surface = base,
    onSurface = Color.White,
    onSurfaceVariant = Color.White,
    surfaceContainerHighest = base,
    keyboardSurface = base,
    keyboardContainer = base,
    keyboardContainerVariant = base.copy(alpha = 0.8f),
    onKeyboardContainer = Color.White,
    keyboardPress = accent.copy(alpha = 0.7f),
    keyboardFade0 = base,
    keyboardFade1 = base,
    primaryTransparent = accent.copy(alpha = 0.3f),
    onSurfaceTransparent = Color.White.copy(alpha = 0.1f),
)

val CustomTheme = ThemeOption(
    dynamic = false,
    key = "CustomTheme",
    name = R.string.theme_custom,
    available = { true },
    obtainColors = {
        val accentStr = it.getSetting(CustomAccentColor)
        val baseStr = it.getSetting(CustomBaseColor)
        val accent = safeColor(accentStr, CustomAccentColor.default)
        val base = safeColor(baseStr, CustomBaseColor.default)
        colorsFrom(accent, base)
    }
)
