package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.extendedLightColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val lightScheme = extendedLightColorScheme(
    primary=Color(0xFF314BA1),
    onPrimary=Color(0xFFFFFFFF),
    primaryContainer=Color(0xFFBBCAFA),
    onPrimaryContainer=Color(0xFF1B2033),
    secondary=Color(0xFF243469),
    onSecondary=Color(0xFF99B1FF),
    secondaryContainer=Color(0xFFE8EDFF),
    onSecondaryContainer=Color(0xFF3C4561),
    tertiary=Color(0xFF7D4725),
    onTertiary=Color(0xFFFFE1CF),
    tertiaryContainer=Color(0xFFF2C7AC),
    onTertiaryContainer=Color(0xFF632C0A),
    error=Color(0xFF802212),
    onError=Color(0xFFF29B9B),
    errorContainer=Color(0xFFFFD6D6),
    onErrorContainer=Color(0xFF730004),
    outline=Color(0xFF606470),
    outlineVariant=Color(0xFFABB3CC),
    surface=Color(0xFFDBE3FF),
    onSurface=Color(0xFF1B1D22),
    onSurfaceVariant=Color(0xFF363840),
    surfaceContainerHighest=Color(0xFFB8C2E5),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFFBACAFF),
    keyboardSurfaceDim=Color(0xFFAFBEF0),
    keyboardContainer=Color(0xFFE8EDFF),
    keyboardContainerVariant=Color(0xFFD8E1FE),
    onKeyboardContainer=Color(0xFF1B1D22),
    keyboardPress=Color(0xFFA6B9FF),
    keyboardFade0=Color(0xFFBACAFF),
    keyboardFade1=Color(0xFFBACAFF),
    primaryTransparent=Color(0xFF314BA1).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFF1B1D22).copy(alpha = 0.1f),
)

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFFBEC2FF),
    onPrimary=Color(0xFF131636),
    primaryContainer=Color(0xFF131636),
    onPrimaryContainer=Color(0xFFE6E6ED),
    secondary=Color(0xFFAFBEF1),
    onSecondary=Color(0xFF28376B),
    secondaryContainer=Color(0xFF0B1124),
    onSecondaryContainer=Color(0xFF899FB2),
    tertiary=Color(0xFFFFB08C),
    onTertiary=Color(0xFF422D24),
    tertiaryContainer=Color(0xFF614235),
    onTertiaryContainer=Color(0xFFFFE5D9),
    error=Color(0xFFFF6E5E),
    onError=Color(0xFF4D1517),
    errorContainer=Color(0xFF941E26),
    onErrorContainer=Color(0xFFFFD6D6),
    outline=Color(0xFF79798A),
    outlineVariant=Color(0xFF1B1B29),
    surface=Color(0xFF0A0B17),
    onSurface=Color(0xFFE3E1EC),
    onSurfaceVariant=Color(0xFFAEAEBF),
    surfaceContainerHighest=Color(0xFF262838),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF0F1026),
    keyboardSurfaceDim=Color(0xFF0E0E1C),
    keyboardContainer=Color(0xFF1B1D42),
    keyboardContainerVariant=Color(0xFF16163B),
    onKeyboardContainer=Color(0xFFE3E1EC),
    keyboardPress=Color(0xFF2A2D66),
    keyboardFade0=Color(0xFF0F1026),
    keyboardFade1=Color(0xFF0F1026),
    primaryTransparent=Color(0xFFBEC2FF).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFE3E1EC).copy(alpha = 0.1f),
)


val DeepSeaLight = ThemeOption(
    dynamic = false,
    key = "DeepSeaLight",
    name = R.string.theme_deepsea_light,
    available = { true }
) {
    lightScheme
}

val DeepSeaDark = ThemeOption(
    dynamic = false,
    key = "DeepSeaDark",
    name = R.string.theme_deepsea_dark,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewThemeLight() {
    ThemePreview(DeepSeaLight)
}

@Composable
@Preview
private fun PreviewThemeDark() {
    ThemePreview(DeepSeaDark)
}