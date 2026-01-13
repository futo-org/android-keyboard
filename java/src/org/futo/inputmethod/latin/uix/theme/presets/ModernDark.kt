// Modern Dark: A custom theme mimicking Gboard with charcoal backgrounds, teal-grey accents, and semi-circular key shapes.

package org.futo.inputmethod.latin.uix.theme.presets

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.extendedLightColorScheme
import org.futo.inputmethod.latin.uix.theme.AdvancedThemeOptions
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

// --- SECTION 1: THE DARK COLORS (FIXED SAGE-TEAL & CHARCOAL) ---
private val modernDarkScheme = extendedDarkColorScheme(
    primary = Color(0xFF6E8287), 
    onPrimary = Color(0xFF1C1B1F),
    primaryContainer = Color(0xFF3C4043),
    onPrimaryContainer = Color(0xFFE8EAED),
    secondary = Color(0xFF6E8287),
    onSecondary = Color(0xFF1C1B1F),
    secondaryContainer = Color(0xFF303134),
    onSecondaryContainer = Color(0xFFE8EAED),
    tertiary = Color(0xFF6E8287),
    onTertiary = Color(0xFF1C1B1F),
    tertiaryContainer = Color(0xFF303134),
    onTertiaryContainer = Color(0xFFE8EAED),
    error = Color(0xFFF28B82),
    onError = Color(0xFF1C1B1F),
    errorContainer = Color(0xFF303134),
    onErrorContainer = Color(0xFFF28B82),
    outline = Color(0xFF9AA0A6),
    outlineVariant = Color(0xFF3C4043),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFFBDC1C6),
    surfaceContainerHighest = Color(0xFF3C4043),
    keyboardSurface = Color(0xFF1C1B1F), 
    keyboardSurfaceDim = Color(0xFF121212),
    keyboardContainer = Color(0xFF3C4043),
    keyboardContainerVariant = Color(0xFF6E8287),
    onKeyboardContainer = Color(0xFFE8EAED),
    keyboardPress = Color(0xFF4F5356),
    primaryTransparent = Color(0xFF6E8287).copy(alpha = 0.3f),
    onSurfaceTransparent = Color(0xFFE8EAED).copy(alpha = 0.1f),
).let { base ->
    // Use specific padding values to squash the keys into a certain shape and enable bold text.
    base.copy(extended = base.extended.copy(advancedThemeOptions = AdvancedThemeOptions(
        keyRoundness = 1.0f,
        keyPaddingHorizontal = 12.dp,
        keyPaddingVertical = 18.dp,
        keyBorders = true,
        forceBold = true
    )))
}

// --- SECTION 2: THE LIGHT COLORS (GBOARD LIGHT SAGE) ---
private val modernLightScheme = extendedLightColorScheme(
    primary = Color(0xFF6E8287),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF174EA6),
    secondary = Color(0xFF6E8287),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F3F4),
    onSecondaryContainer = Color(0xFF3C4043),
    tertiary = Color(0xFF6E8287),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1F3F4),
    onTertiaryContainer = Color(0xFF174EA6),
    error = Color(0xFFD93025),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF1F3F4),
    onErrorContainer = Color(0xFFD93025),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFBDC1C6),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF70757A),
    surfaceContainerHighest = Color(0xFFDADCE0),
    keyboardSurface = Color(0xFFDADCE0), // Stronger border contrast
    keyboardSurfaceDim = Color(0xFFC8CBCE),
    keyboardContainer = Color(0xFFFFFFFF),
    keyboardContainerVariant = Color(0xFF6E8287),
    onKeyboardContainer = Color(0xFF1C1B1F),
    keyboardPress = Color(0xFFE8EAED),
    primaryTransparent = Color(0xFF6E8287).copy(alpha = 0.1f),
    onSurfaceTransparent = Color(0xFF1C1B1F).copy(alpha = 0.05f),
).let { base ->
    base.copy(extended = base.extended.copy(advancedThemeOptions = AdvancedThemeOptions(
        keyRoundness = 1.0f,
        keyPaddingHorizontal = 12.dp,
        keyPaddingVertical = 18.dp,
        keyBorders = true,
        forceBold = true
    )))
}

// --- SECTION 3: THE AUTOMATIC SWITCH & FONT LOADING ---
val ModernDark = ThemeOption(
    dynamic = true,
    key = "ModernDark",
    name = R.string.theme_modern_dark,
    available = { true }
) { context ->
    // 1. Determine if we are in Dark or Light mode
    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val baseScheme = if (isDark) modernDarkScheme else modernLightScheme
    
    // 2. Load the custom font from res/font/gsb.ttf
    val customTypeface = try {
        ResourcesCompat.getFont(context, R.font.gsb)
    } catch (e: Exception) {
        null // Fallback to system font if file is missing
    }

    // 3. Inject the font into the theme before returning it
    baseScheme.copy(
        extended = baseScheme.extended.copy(
            advancedThemeOptions = baseScheme.extended.advancedThemeOptions.copy(
                font = customTypeface
            )
        )
    )
}

@Composable
@Preview
private fun PreviewModernDark() {
    ThemePreview(ModernDark)
}
