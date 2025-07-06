package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFF66FF99),
    onPrimary=Color(0xFF003314),
    primaryContainer=Color(0xFF003314),
    onPrimaryContainer=Color(0xFFB2FFC9),
    secondary=Color(0xFFB2FFC9),
    onSecondary=Color(0xFF003314),
    secondaryContainer=Color(0xFF00220D),
    onSecondaryContainer=Color(0xFFB2FFC9),
    surface=Color(0xFF000000),
    onSurface=Color(0xFFE6E1E5),
    surfaceContainerHighest=Color(0xFF232129),
    keyboardSurface=Color(0xFF000000),
    keyboardContainer=Color(0xFF00220D).copy(alpha = 0.6f),
    keyboardContainerVariant=Color(0xFF00220D).copy(alpha = 0.2f),
    onKeyboardContainer=Color(0xFFE6E1E5).copy(alpha = 0.8f),
    keyboardPress=Color(0xFF003314),
    keyboardFade0=Color(0xFF000000),
    keyboardFade1=Color(0xFF000000),
    primaryTransparent=Color(0xFF66FF99).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFE6E1E5).copy(alpha = 0.1f),
)

val AMOLEDDarkGreen = ThemeOption(
    dynamic = false,
    key = "AMOLEDDarkGreen",
    name = R.string.theme_amoled_dark_green,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(AMOLEDDarkGreen)
}
