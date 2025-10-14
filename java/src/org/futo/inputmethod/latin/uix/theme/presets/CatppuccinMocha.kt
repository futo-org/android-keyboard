package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFFF4B8E4),
    onPrimary=Color(0xFF2A2430),
    primaryContainer=Color(0xFF3B2A35),
    onPrimaryContainer=Color(0xFFFDE8F7),
    secondary=Color(0xFFF5C2E7),
    onSecondary=Color(0xFF332A33),
    secondaryContainer=Color(0xFF46323E),
    onSecondaryContainer=Color(0xFFFDEAF6),
    tertiary=Color(0xFFF2CDCD),
    onTertiary=Color(0xFF312829),
    tertiaryContainer=Color(0xFF3E2F30),
    onTertiaryContainer=Color(0xFFFEF0F0),
    error=Color(0xFFF38BA8),
    onError=Color(0xFF3A121E),
    errorContainer=Color(0xFFF38BA8),
    onErrorContainer=Color(0xFF1E1E2E),
    outline=Color(0xFF6C7086),
    outlineVariant=Color(0xFF45475A),
    surface=Color(0xFF11111B),
    onSurface=Color(0xFFCDD6F4),
    onSurfaceVariant=Color(0xFFBAC2DE),
    surfaceContainerHighest=Color(0xFF313244),
    shadow=Color(0xFF11111B).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF181825),
    keyboardContainer=Color(0xFF1E1E2E).copy(alpha = 0.6f),
    keyboardContainerVariant=Color(0xFF1E1E2E).copy(alpha = 0.2f),
    onKeyboardContainer=Color(0xFFCDD6F4).copy(alpha = 0.8f),
    keyboardPress=Color(0xFF313244),
    keyboardFade0=Color(0xFF11111B),
    keyboardFade1=Color(0xFF11111B),
    primaryTransparent=Color(0xFFCBA6F7).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFCDD6F4).copy(alpha = 0.1f),
)

val CatppuccinMocha = ThemeOption(
    dynamic = false,
    key = "CatppuccinMocha",
    name = R.string.theme_catppuccin_mocha,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(CatppuccinMocha)
}