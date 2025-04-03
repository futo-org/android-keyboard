package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFFBBD5F0),
    onPrimary=Color(0xFF393E47),
    primaryContainer=Color(0xFF393E47),
    onPrimaryContainer=Color(0xFFE0F0FF),
    secondary=Color(0xFFF0F8FF),
    onSecondary=Color(0xFF65676B),
    secondaryContainer=Color(0xFF3D3D3D),
    onSecondaryContainer=Color(0xFFCBD1D6),
    tertiary=Color(0xFF94B6FF),
    onTertiary=Color(0xFF15274D),
    tertiaryContainer=Color(0xFF224080),
    onTertiaryContainer=Color(0xFFC7D9FF),
    error=Color(0xFFF2A7A9),
    onError=Color(0xFF783032),
    errorContainer=Color(0xFF801A20),
    onErrorContainer=Color(0xFFF2C2C5),
    outline=Color(0xFFB2B2B2),
    outlineVariant=Color(0xFF545859),
    surface=Color(0xFF2E2E2E),
    onSurface=Color(0xFFE5F2FF),
    onSurfaceVariant=Color(0xFFC7C7C7),
    surfaceContainerHighest=Color(0xFF4D4D4D),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF2E2E2E),
    keyboardSurfaceDim=Color(0xFF262626),
    keyboardContainer=Color(0xFF454545),
    keyboardContainerVariant=Color(0xFF383838),
    onKeyboardContainer=Color(0xFFE5F2FF),
    keyboardPress=Color(0xFF585B5E),
    keyboardFade0=Color(0xFF2E2E2E),
    keyboardFade1=Color(0xFF2E2E2E),
    primaryTransparent=Color(0xFFD3E1F0).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFE5F2FF).copy(alpha = 0.1f),
)

val SteelGray = ThemeOption(
    dynamic = false,
    key = "SteelGray",
    name = R.string.theme_steel_gray,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(SteelGray)
}