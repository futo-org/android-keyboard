package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedLightColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val lightScheme = extendedLightColorScheme(
    primary=Color(0xFFB02378),
    onPrimary=Color(0xFFFFFFFF),
    primaryContainer=Color(0xFFFFA6F5),
    onPrimaryContainer=Color(0xFF530054),
    secondary=Color(0xFF611958),
    onSecondary=Color(0xFFE094D7),
    secondaryContainer=Color(0xFFFFD9F8),
    onSecondaryContainer=Color(0xFF5F395B),
    tertiary=Color(0xFF005422),
    onTertiary=Color(0xFFD3F2E0),
    tertiaryContainer=Color(0xFFA7D9BC),
    onTertiaryContainer=Color(0xFF00210D),
    error=Color(0xFF873A13),
    onError=Color(0xFFEDC4AF),
    errorContainer=Color(0xFFFADFD2),
    onErrorContainer=Color(0xFF732600),
    outline=Color(0xFF594553),
    outlineVariant=Color(0xFFD5A8C5),
    surface=Color(0xFFFFEDF9),
    onSurface=Color(0xFF21161E),
    onSurfaceVariant=Color(0xFF40383D),
    surfaceContainerHighest=Color(0xFFE5B9D7),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFFFFBAE9),
    keyboardSurfaceDim=Color(0xFFF0B6DD),
    keyboardContainer=Color(0xFFFFD9F8),
    keyboardContainerVariant=Color(0xFFFFCCF6),
    onKeyboardContainer=Color(0xFF21161E),
    keyboardPress=Color(0xFFF29DE3),
    keyboardFade0=Color(0xFFFFBAE9),
    keyboardFade1=Color(0xFFFFBAE9),
    primaryTransparent=Color(0xFFB02378).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFF21161E).copy(alpha = 0.1f),
)

val CottonCandy = ThemeOption(
    dynamic = false,
    key = "CottonCandy",
    name = R.string.theme_cotton_candy,
    available = { true }
) {
    lightScheme
}

@Composable
@Preview
private fun PreviewThemeLight() {
    ThemePreview(CottonCandy)
}




