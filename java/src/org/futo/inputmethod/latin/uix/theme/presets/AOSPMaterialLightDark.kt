package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.extendedLightColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkColorScheme = extendedDarkColorScheme(
    primary=Color(0xFF80CBC4),
    onPrimary=Color(0xFF1D3030),
    primaryContainer=Color(0xFF2C4A4A),
    onPrimaryContainer=Color(0xFF99FFF8),
    secondary=Color(0xFFADEDD7),
    onSecondary=Color(0xFF265950),
    secondaryContainer=Color(0xFF24302E),
    onSecondaryContainer=Color(0xFF97CCC0),
    tertiary=Color(0xFFBDB2FF),
    onTertiary=Color(0xFF1F174D),
    tertiaryContainer=Color(0xFF291F66),
    onTertiaryContainer=Color(0xFFE9E5FF),
    error=Color(0xFFFF8678),
    onError=Color(0xFF631E21),
    errorContainer=Color(0xFF7A252B),
    onErrorContainer=Color(0xFFFFD6D6),
    outline=Color(0xFF9EA3A6),
    outlineVariant=Color(0xFF505A61),
    surface=Color(0xFF131717),
    onSurface=Color(0xFFD8EBE4),
    onSurfaceVariant=Color(0xFFBFD9D4),
    surfaceContainerHighest=Color(0xFF2C3838),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF263238),
    keyboardSurfaceDim=Color(0xFF21272B),
    keyboardContainer=Color(0xFF3C474C),
    keyboardContainerVariant=Color(0xFF2F3A40),
    onKeyboardContainer=Color(0xFFD4D6D7),
    keyboardPress=Color(0xFF3A5A5C),
    keyboardFade0=Color(0xFF21272B),
    keyboardFade1=Color(0xFF21272B),
    primaryTransparent=Color(0xFF80CBC4).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFD8EBE4).copy(alpha = 0.1f),
)

private val lightColorScheme = extendedLightColorScheme(
    primary=Color(0xFF127A70),
    onPrimary=Color(0xFFFFFFFF),
    primaryContainer=Color(0xFF9EE8DD),
    onPrimaryContainer=Color(0xFF20403D),
    secondary=Color(0xFF2D6359),
    onSecondary=Color(0xFFAAE3DC),
    secondaryContainer=Color(0xFFE1F7F2),
    onSecondaryContainer=Color(0xFF536362),
    tertiary=Color(0xFF4A3D99),
    onTertiary=Color(0xFFE5E0FF),
    tertiaryContainer=Color(0xFFBBB3E5),
    onTertiaryContainer=Color(0xFF050026),
    error=Color(0xFF8C3030),
    onError=Color(0xFFF7B5AD),
    errorContainer=Color(0xFFFFDAD6),
    onErrorContainer=Color(0xFF730004),
    outline=Color(0xFF667175),
    outlineVariant=Color(0xFF9DA4A6),
    surface=Color(0xFFECF1F1),
    onSurface=Color(0xFF202929),
    onSurfaceVariant=Color(0xFF363F40),
    surfaceContainerHighest=Color(0xFFCAE5E5),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFFECEFF1),
    keyboardSurfaceDim=Color(0xFFE1E4E5),
    keyboardContainer=Color(0xFFFFFFFF),
    keyboardContainerVariant=Color(0xFFF7F7F7),
    onKeyboardContainer=Color(0xFF37474F),
    keyboardPress=Color(0xFFCAEAED),
    keyboardFade0=Color(0xFFE1E4E5),
    keyboardFade1=Color(0xFFE1E4E5),
    primaryTransparent=Color(0xFF127A70).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFF202929).copy(alpha = 0.1f),
)


val ClassicMaterialDark = ThemeOption(
    dynamic = false,
    key = "ClassicMaterialDark",
    name = R.string.theme_classic_dark,
    available = { true }
) {
    darkColorScheme
}


val ClassicMaterialLight = ThemeOption(
    dynamic = false,
    key = "ClassicMaterialLight",
    name = R.string.theme_classic_light,
    available = { true }
) {
    lightColorScheme
}


@Composable
@Preview
private fun PreviewThemeDark() {
    ThemePreview(ClassicMaterialDark)
}

@Composable
@Preview
private fun PreviewThemeLight() {
    ThemePreview(ClassicMaterialLight)
}
