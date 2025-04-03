package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFFD0BCFF),
    onPrimary=Color(0xFF381E72),
    primaryContainer=Color(0xFF3A2966),
    onPrimaryContainer=Color(0xFFE4D4FF),
    secondary=Color(0xFFCFBAFF),
    onSecondary=Color(0xFF3E2663),
    secondaryContainer=Color(0xFF1E192B),
    onSecondaryContainer=Color(0xFFAC9DC4),
    tertiary=Color(0xFFF1FFA3),
    onTertiary=Color(0xFF444D12),
    tertiaryContainer=Color(0xFF5A6618),
    onTertiaryContainer=Color(0xFFF9FFD6),
    error=Color(0xFFFA7C75),
    onError=Color(0xFF591A16),
    errorContainer=Color(0xFF8C1D18),
    onErrorContainer=Color(0xFFF9AFA9),
    outline=Color(0xFF9E93AD),
    outlineVariant=Color(0xFF3B2D4F),
    surface=Color(0xFF000000),
    onSurface=Color(0xFFE6E1E5),
    onSurfaceVariant=Color(0xFFCCC1D6),
    surfaceContainerHighest=Color(0xFF232129),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF000000),
    keyboardContainer=Color(0xFF1E192B).copy(alpha = 0.6f),
    keyboardContainerVariant=Color(0xFF181324).copy(alpha = 0.2f),
    onKeyboardContainer=Color(0xFFE6E1E5).copy(alpha = 0.8f),
    keyboardPress=Color(0xFF31264F),
    keyboardFade0=Color(0xFF000000),
    keyboardFade1=Color(0xFF000000),
    primaryTransparent=Color(0xFFD0BCFF).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFE6E1E5).copy(alpha = 0.1f),
)

val AMOLEDDarkPurple = ThemeOption(
    dynamic = false,
    key = "AMOLEDDarkPurple",
    name = R.string.theme_amoled_dark,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(AMOLEDDarkPurple)
}