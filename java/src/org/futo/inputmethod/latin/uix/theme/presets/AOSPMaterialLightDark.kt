package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.extendedLightColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview
import org.futo.inputmethod.latin.uix.wrapDarkColorScheme
import org.futo.inputmethod.latin.uix.wrapLightColorScheme

private val darkColorScheme = extendedDarkColorScheme(
    primary=Color(0xFF63E5D9),
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
    outline=Color(0xFF90ADA8),
    outlineVariant=Color(0xFF3C6158),
    surface=Color(0xFF131717),
    onSurface=Color(0xFFD8EBE4),
    onSurfaceVariant=Color(0xFFBFD9D4),
    surfaceContainerHighest=Color(0xFF2C3838),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF1F2A2B),
    keyboardContainer=Color(0xFF293738),
    keyboardContainerVariant=Color(0xFF212F30),
    onKeyboardContainer=Color(0xFFD8EBE4),
    keyboardPress=Color(0xFF3A5A5C),
    keyboardFade0=Color(0xFF1F2A2B),
    keyboardFade1=Color(0xFF1F2A2B),
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
    outline=Color(0xFF435859),
    outlineVariant=Color(0xFF8FBFBF),
    surface=Color(0xFFECF1F1),
    onSurface=Color(0xFF202929),
    onSurfaceVariant=Color(0xFF363F40),
    surfaceContainerHighest=Color(0xFFCAE5E5),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFFCAE8EB),
    keyboardContainer=Color(0xFFE8FDFF),
    keyboardContainerVariant=Color(0xFFD9FCFF),
    onKeyboardContainer=Color(0xFF202929),
    keyboardPress=Color(0xFFA3E0E5),
    keyboardFade0=Color(0xFFCAE8EB),
    keyboardFade1=Color(0xFFCAE8EB),
    primaryTransparent=Color(0xFF127A70).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFF202929).copy(alpha = 0.1f),
)


val ClassicMaterialDark = ThemeOption(
    dynamic = false,
    key = "ClassicMaterialDark",
    name = R.string.classic_material_dark_theme_name,
    available = { true }
) {
    darkColorScheme
}


val ClassicMaterialLight = ThemeOption(
    dynamic = false,
    key = "ClassicMaterialLight",
    name = R.string.classic_material_light_theme_name,
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
