package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary=Color(0xFF7EAEF2),
    onPrimary=Color(0xFF0E2545),
    primaryContainer=Color(0xFF13325E),
    onPrimaryContainer=Color(0xFFDEEDFC),
    secondary=Color(0xFFC5E0FC),
    onSecondary=Color(0xFF374D6E),
    secondaryContainer=Color(0xFF1B2738),
    onSecondaryContainer=Color(0xFF97B0C9),
    tertiary=Color(0xFFF2CB7D),
    onTertiary=Color(0xFF402D08),
    tertiaryContainer=Color(0xFF735825),
    onTertiaryContainer=Color(0xFFFFF0D1),
    error=Color(0xFFFF8C80),
    onError=Color(0xFF4D2B2B),
    errorContainer=Color(0xFF803B3B),
    onErrorContainer=Color(0xFFFFDFDB),
    outline=Color(0xFF9198A1),
    outlineVariant=Color(0xFF1F2833),
    surface=Color(0xFF0F172A),
    onSurface=Color(0xFFF8FAFC),
    onSurfaceVariant=Color(0xFFB4BABF),
    surfaceContainerHighest=Color(0xFF252E42),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF0F172A),
    keyboardSurfaceDim=Color(0xFF0C121F),
    keyboardContainer=Color(0xFF16213D),
    keyboardContainerVariant=Color(0xFF131C34),
    onKeyboardContainer=Color(0xFFF8FAFC),
    keyboardPress=Color(0xFF253766),
    keyboardFade0=Color(0xFF0F172A),
    keyboardFade1=Color(0xFF0F172A),
    primaryTransparent=Color(0xFF7EAEF2).copy(alpha = 0.3f),
    onSurfaceTransparent= Color(0xFFF8FAFC).copy(alpha = 0.1f),
)

val VoiceInputTheme = ThemeOption(
    dynamic = false,
    key = "VoiceInputTheme",
    name = R.string.theme_vi,
    available = { true }
) {
    darkScheme
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(VoiceInputTheme)
}