package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedLightColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val lightScheme = extendedLightColorScheme(
    primary=Color(0xFF6D5E0F),
    onPrimary=Color(0xFFFFFFFF),
    primaryContainer=Color(0xFFF8E286),
    onPrimaryContainer=Color(0xFF3B2F00),
    secondary=Color(0xFF3B370D),
    onSecondary=Color(0xFFDED08C),
    secondaryContainer=Color(0xFFF9F1D1),
    onSecondaryContainer=Color(0xFF61593A),
    tertiary=Color(0xFF314291),
    onTertiary=Color(0xFFD0D7F7),
    tertiaryContainer=Color(0xFFA4AFDE),
    onTertiaryContainer=Color(0xFF00082B),
    error=Color(0xFF8C1C1C),
    onError=Color(0xFFF0B4B4),
    errorContainer=Color(0xFFFFDAD6),
    onErrorContainer=Color(0xFF730B0F),
    outline=Color(0xFF635D49),
    outlineVariant=Color(0xFFCDC09E),
    surface=Color(0xFFFFF9ED),
    onSurface=Color(0xFF1E1C13),
    onSurfaceVariant=Color(0xFF4A4840),
    surfaceContainerHighest=Color(0xFFE5D7BA),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFFEDDCBB),
    keyboardSurfaceDim=Color(0xFFE0D3B8),
    keyboardContainer=Color(0xFFFFF8DE),
    keyboardContainerVariant=Color(0xFFFFF4C9),
    onKeyboardContainer=Color(0xFF1E1C13),
    keyboardPress=Color(0xFFFFEDAB),
    keyboardFade0=Color(0xFFEDDCBB),
    keyboardFade1=Color(0xFFEDDCBB),
    primaryTransparent=Color(0xFF6D5E0F).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFF1E1C13).copy(alpha = 0.1f),
)

val Sunflower = ThemeOption(
    dynamic = false,
    key = "Sunflower",
    name = R.string.theme_sunflower,
    available = { true }
) {
    lightScheme
}

@Composable
@Preview
private fun PreviewThemeLight() {
    ThemePreview(Sunflower)
}




