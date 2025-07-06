package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFF81A4FF),
    onPrimary=Color(0xFF001E60),
    primaryContainer=Color(0xFF001E60),
    onPrimaryContainer=Color(0xFFC3D4FF),
    secondary=Color(0xFFBEC7FF),
    onSecondary=Color(0xFF001E60),
    secondaryContainer=Color(0xFF101B37),
    onSecondaryContainer=Color(0xFFBEC7FF),
    surface=Color(0xFF000000),
    onSurface=Color(0xFFE6E1E5),
    surfaceContainerHighest=Color(0xFF232129),
    keyboardSurface=Color(0xFF000000),
    keyboardContainer=Color(0xFF101B37).copy(alpha = 0.6f),
    keyboardContainerVariant=Color(0xFF101B37).copy(alpha = 0.2f),
    onKeyboardContainer=Color(0xFFE6E1E5).copy(alpha = 0.8f),
    keyboardPress=Color(0xFF001E60),
    keyboardFade0=Color(0xFF000000),
    keyboardFade1=Color(0xFF000000),
    primaryTransparent=Color(0xFF81A4FF).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFE6E1E5).copy(alpha = 0.1f),
)

val AMOLEDDarkBlue = ThemeOption(
    dynamic = false,
    key = "AMOLEDDarkBlue",
    name = R.string.theme_amoled_dark_blue,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(AMOLEDDarkBlue)
}
