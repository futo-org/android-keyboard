package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedLightColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val lightScheme = extendedLightColorScheme(
    primary=Color(0xFF1D2023),
    onPrimary=Color(0xFFFFFFFF),
    primaryContainer=Color(0xFFBAC5DB),
    onPrimaryContainer=Color(0xFF1A2026),
    secondary=Color(0xFF303133),
    onSecondary=Color(0xFFAFB5BA),
    secondaryContainer=Color(0xFFE5E5E5),
    onSecondaryContainer=Color(0xFF4B5054),
    tertiary=Color(0xFF223D78),
    onTertiary=Color(0xFFDAE3F7),
    tertiaryContainer=Color(0xFF819EDE),
    onTertiaryContainer=Color(0xFF00082B),
    error=Color(0xFF8C2A2D),
    onError=Color(0xFFF2BFC1),
    errorContainer=Color(0xFFF2C2C5),
    onErrorContainer=Color(0xFF80262C),
    outline=Color(0xFF4B5052),
    outlineVariant=Color(0xFFB8BDBF),
    surface=Color(0xFFF8F8F8),
    onSurface=Color(0xFF17181A),
    onSurfaceVariant=Color(0xFF404344),
    surfaceContainerHighest=Color(0xFFCCCCCC),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFFF8F8F8),
    keyboardSurfaceDim=Color(0xFFF0F0F0),
    keyboardContainer=Color(0xFFE5E5E5),
    keyboardContainerVariant=Color(0xFFF0F0F0),
    onKeyboardContainer=Color(0xFF17181A),
    keyboardPress=Color(0xFFBABCBF),
    keyboardFade0=Color(0xFFF8F8F8),
    keyboardFade1=Color(0xFFF8F8F8),
    primaryTransparent=Color(0xFF212223).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFF17181A).copy(alpha = 0.1f),
)

val Snowfall = ThemeOption(
    dynamic = false,
    key = "Snowfall",
    name = R.string.theme_snowfall,
    available = { true }
) {
    lightScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(Snowfall)
}