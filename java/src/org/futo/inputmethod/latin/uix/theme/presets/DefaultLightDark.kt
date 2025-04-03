package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.ui.graphics.Color
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.extendedLightColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFFB2C8FF),
    onPrimary=Color(0xFF131D36),
    primaryContainer=Color(0xFF131D36),
    onPrimaryContainer=Color(0xFFE6E6EE),
    secondary=Color(0xFFA7B3D1),
    onSecondary=Color(0xFF393B40),
    secondaryContainer=Color(0xFF17181C),
    onSecondaryContainer=Color(0xFF949499),
    tertiary=Color(0xFFADC6FF),
    onTertiary=Color(0xFF394866),
    tertiaryContainer=Color(0xFF44567A),
    onTertiaryContainer=Color(0xFFD6E3FF),
    error=Color(0xFFFA6060),
    onError=Color(0xFF663939),
    errorContainer=Color(0xFF732323),
    onErrorContainer=Color(0xFFFFD6D6),
    outline=Color(0xFF767A8A),
    outlineVariant=Color(0xFF1D1E24),
    surface=Color(0xFF121316),
    onSurface=Color(0xFFE6E6EE),
    onSurfaceVariant=Color(0xFFBBBDBF),
    surfaceContainerHighest=Color(0xFF2C2F38),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF121316),
    keyboardSurfaceDim=Color(0xFF0D0D0F),
    keyboardContainer=Color(0xFF1E2024),
    keyboardContainerVariant=Color(0xFF17181C),
    onKeyboardContainer=Color(0xFFE6E6EE),
    keyboardPress=Color(0xFF3C3F47),
    keyboardFade0=Color(0xFF121316),
    keyboardFade1=Color(0xFF121316),
    primaryTransparent=Color(0xFFB2C8FF).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFE6E6EE).copy(alpha = 0.1f)
)

private val lightScheme = extendedLightColorScheme(
    primary=Color(0xFF0A1633),
    onPrimary=Color(0xFFF2F6FF),
    primaryContainer=Color(0xFFD9E4FF),
    onPrimaryContainer=Color(0xFF1D1D33),
    secondary=Color(0xFF132040),
    onSecondary=Color(0xFFD1D1D1),
    secondaryContainer=Color(0xFFEDF0FA),
    onSecondaryContainer=Color(0xFF3B3B4D),
    tertiary=Color(0xFF1D3366),
    onTertiary=Color(0xFFCFDDFF),
    tertiaryContainer=Color(0xFFBDD2FF),
    onTertiaryContainer=Color(0xFF102B61),
    error=Color(0xFF991818),
    onError=Color(0xFFFFC7C7),
    errorContainer=Color(0xFFFFD6D6),
    onErrorContainer=Color(0xFF732323),
    outline=Color(0xFF636570),
    outlineVariant=Color(0xFFBCC2D6),
    surface=Color(0xFFFAFBFF),
    onSurface=Color(0xFF191B21),
    onSurfaceVariant=Color(0xFF484B54),
    surfaceContainerHighest=Color(0xFFD8DEF2),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFFEBECF0),
    keyboardSurfaceDim=Color(0xFFE1E2E5),
    keyboardContainer=Color(0xFFFFFFFF),
    keyboardContainerVariant=Color(0xFFF7F7F7),
    onKeyboardContainer=Color(0xFF191B21),
    keyboardPress=Color(0xFFD4D5D9),
    keyboardFade0=Color(0xFFEDF0FA),
    keyboardFade1=Color(0xFFEDF0FA),
    primaryTransparent=Color(0xFF0A1633).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFF191B21).copy(alpha = 0.1f),
)


val DefaultDarkScheme = ThemeOption(
    dynamic = false,
    key = "DefaultDarkScheme",
    name = R.string.theme_default_dark,
    available = { true }
) {
    darkScheme
}

val DefaultLightScheme = ThemeOption(
    dynamic = false,
    key = "DefaultLightScheme",
    name = R.string.theme_default_light,
    available = { true }
) {
    lightScheme
}
