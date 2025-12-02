package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption

private val gradientScheme1 = extendedDarkColorScheme(
    primary=Color(0xFFF786F1),
    onPrimary=Color(0xFF520B4E),
    primaryContainer=Color(0xFF102347),
    onPrimaryContainer=Color(0xFFE5EEFF),
    secondary= Color(0xFFF7D0F5),
    onSecondary=Color(0xFF80457C),
    secondaryContainer=Color(0xFF162033),
    onSecondaryContainer=Color(0xFFABBAD9),
    tertiary=Color(0xFF7FEB86),
    onTertiary=Color(0xFF145218),
    tertiaryContainer=Color(0xFF246B29),
    onTertiaryContainer=Color(0xFFD6FFD9),
    error=Color(0xFFFF8C80),
    onError=Color(0xFF4D2B2B),
    errorContainer=Color(0xFF803B3B),
    onErrorContainer=Color(0xFFFFDFDB),
    outline=Color(0xFFB1A9CC),
    outlineVariant=Color(0xFF363340),
    surface=Color(0xFF0E0E1A),
    onSurface=Color(0xFFE1E9FA),
    onSurfaceVariant=Color(0xFFC8C9CC),
    surfaceContainerHighest=Color(0xFF2E384D),
    shadow=Color(0xFF000000).copy(alpha = 0.7f),
    keyboardSurface=Color(0xFF0F2F6E),
    keyboardContainer=Color(0xFFFFFFFF).copy(alpha = 0.17f),
    keyboardContainerVariant=Color(0xFFFFFFFF).copy(alpha = 0.08f),
    onKeyboardContainer=Color(0xFFFFFFFF),
    keyboardPress=Color(0xFF7D56BF),
    keyboardFade0=Color(0xFF0F2F6E),
    keyboardFade1=Color(0xFF0F2F6E),
    primaryTransparent=Color(0xFFEB7FE4).copy(alpha = 0.3f),
    onSurfaceTransparent=Color(0xFFE1E9FA).copy(alpha = 0.1f),

    keyboardBackgroundGradient = Brush.verticalGradient(
        0.0f to Color(0xFF123A87),
        1.0f to Color(0xFF852D80)
    ),
    navigationBarColor = Color(0xFF852D80),
    navigationBarColorForTransparency = Color(0xFF000000),
)


val Gradient1 = ThemeOption(
    dynamic = false,
    key = "Gradient1",
    name = R.string.theme_gradient1,
    available = { true }
) {
    gradientScheme1
}