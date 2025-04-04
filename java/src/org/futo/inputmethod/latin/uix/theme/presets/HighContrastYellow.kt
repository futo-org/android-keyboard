package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview
import org.futo.inputmethod.latin.uix.wrapLightColorScheme

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFFFFFFFF),
    onPrimary=Color(0xFF000000),
    primaryContainer=Color(0xFF858585),
    onPrimaryContainer=Color(0xFF000000),
    secondary=Color(0xFFFFFF00),
    onSecondary=Color(0xFF000000),
    secondaryContainer=Color(0xFF858585),
    onSecondaryContainer=Color(0xFF000000),
    tertiary=Color(0xFFFFFF00),
    onTertiary=Color(0xFF000000),
    tertiaryContainer=Color(0xFF858585),
    onTertiaryContainer=Color(0xFF000000),
    error=Color(0xFFFF1A00),
    onError=Color(0xFF000000),
    errorContainer=Color(0xFFBE000F),
    onErrorContainer=Color(0xFFFFFFFF),
    outline=Color(0xFFFFFFFF),
    outlineVariant=Color(0xFFFFFFFF),
    surface=Color(0xFF000000),
    onSurface=Color(0xFFFFFFFF),
    onSurfaceVariant=Color(0xFFFFFFFF),
    surfaceContainerHighest=Color(0xFF7A7A7A),
    shadow=Color(0xFF000000).copy(alpha = 0.5f),
    keyboardSurface=Color(0xFF000000),
    keyboardSurfaceDim=Color(0xFF000000),
    keyboardContainer=Color(0xFFFFFF00),
    keyboardContainerVariant=Color(0xFFFFFFFF),
    onKeyboardContainer=Color(0xFF000000),
    keyboardPress=Color(0xFFB9B9B9),
    keyboardFade0=Color(0xFF000000),
    keyboardFade1=Color(0xFF000000),
    primaryTransparent=Color(0xFFFFFF00),
    onSurfaceTransparent=Color(0xFFFFFFFF),
    keyboardContainerPressed=Color(0xFF777777),
    onKeyboardContainerPressed=Color(0xFFFFFFFF),
    hintColor=Color(0xFF0000FF),
    hintHiVis=true
)


val HighContrastYellow = ThemeOption(
    dynamic = false,
    key = "HighContrastYellow",
    name = R.string.theme_high_contrast_yellow,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewThemeLight() {
    ThemePreview(HighContrastYellow)
}




