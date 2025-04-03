package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFF67FB59),
    onPrimary=Color(0xFF112E10),
    primaryContainer=Color(0xFF112E10),
    onPrimaryContainer=Color(0xFF99E788),
    secondary=Color(0xFF004702),
    onSecondary=Color(0xFF8CD97C),
    secondaryContainer=Color(0xFF141C15),
    onSecondaryContainer=Color(0xFF62A663),
    tertiary=Color(0xFF52F5F5),
    onTertiary=Color(0xFF003737),
    tertiaryContainer=Color(0xFF005252),
    onTertiaryContainer=Color(0xFFCBF5F5),
    error=Color(0xFFFF705E),
    onError=Color(0xFF4F1013),
    errorContainer=Color(0xFF7A181F),
    onErrorContainer=Color(0xFFFFD6D6),
    outline=Color(0xFF879580),
    outlineVariant=Color(0xFF344A2A),
    surface=Color(0xFF091208),
    onSurface=Color(0xFFDCE5D5),
    onSurfaceVariant=Color(0xFFA8B3A2),
    surfaceContainerHighest=Color(0xFF293827),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF0D1A0B),
    keyboardSurfaceDim=Color(0xFF0A1409),
    keyboardContainer=Color(0xFF162D13),
    keyboardContainerVariant=Color(0xFF0F210D),
    onKeyboardContainer=Color(0xFFDCE5D5),
    keyboardPress=Color(0xFF24521E),
    keyboardFade0=Color(0xFF0D1A0B),
    keyboardFade1=Color(0xFF0D1A0B),
    primaryTransparent=Color(0xFF67FB59).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFDCE5D5).copy(alpha = 0.1f),
)

val Emerald = ThemeOption(
    dynamic = false,
    key = "Emerald",
    name = R.string.theme_emerald,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewThemeDark() {
    ThemePreview(Emerald)
}