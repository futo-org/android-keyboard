package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFFFF6B6B),
    onPrimary=Color(0xFF5C0000),
    primaryContainer=Color(0xFF5C0000),
    onPrimaryContainer=Color(0xFFFFB4AB),
    secondary=Color(0xFFFFB4AB),
    onSecondary=Color(0xFF5C0000),
    secondaryContainer=Color(0xFF330000),
    onSecondaryContainer=Color(0xFFFFB4AB),
    surface=Color(0xFF000000),
    onSurface=Color(0xFFE6E1E5),
    surfaceContainerHighest=Color(0xFF232129),
    keyboardSurface=Color(0xFF000000),
    keyboardContainer=Color(0xFF330000).copy(alpha = 0.6f),
    keyboardContainerVariant=Color(0xFF330000).copy(alpha = 0.2f),
    onKeyboardContainer=Color(0xFFE6E1E5).copy(alpha = 0.8f),
    keyboardPress=Color(0xFF5C0000),
    keyboardFade0=Color(0xFF000000),
    keyboardFade1=Color(0xFF000000),
    primaryTransparent=Color(0xFFFF6B6B).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFE6E1E5).copy(alpha = 0.1f),
)

val AMOLEDDarkRed = ThemeOption(
    dynamic = false,
    key = "AMOLEDDarkRed",
    name = R.string.theme_amoled_dark_red,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(AMOLEDDarkRed)
}
