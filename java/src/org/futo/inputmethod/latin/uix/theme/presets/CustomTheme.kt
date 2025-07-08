package org.futo.inputmethod.latin.uix.theme.presets

import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.CustomAccentColor
import org.futo.inputmethod.latin.uix.CustomBaseColor
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption

private fun safeColor(code: String, fallback: String): Color {
    return try { Color(parseColor(code)) } catch (_: IllegalArgumentException) { Color(parseColor(fallback)) }
}

private fun lighten(color: Color, amount: Float): Color {
    return Color(
        red = color.red + (1f - color.red) * amount,
        green = color.green + (1f - color.green) * amount,
        blue = color.blue + (1f - color.blue) * amount,
        alpha = color.alpha
    )
}

private fun idealOnColor(color: Color): Color {
    return if (color.luminance() > 0.5f) Color.Black else Color.White
}

private fun colorsFrom(accent: Color, base: Color) = extendedDarkColorScheme(
    primary = accent,
    onPrimary = idealOnColor(accent),
    primaryContainer = accent,
    onPrimaryContainer = idealOnColor(accent),
    secondary = lighten(accent, 0.2f),
    onSecondary = idealOnColor(lighten(accent, 0.2f)),
    secondaryContainer = lighten(accent, 0.2f),
    onSecondaryContainer = idealOnColor(lighten(accent, 0.2f)),
    tertiary = lighten(accent, 0.4f),
    onTertiary = idealOnColor(lighten(accent, 0.4f)),
    tertiaryContainer = lighten(accent, 0.4f),
    onTertiaryContainer = idealOnColor(lighten(accent, 0.4f)),
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
