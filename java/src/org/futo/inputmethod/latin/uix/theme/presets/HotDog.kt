package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview
import org.futo.inputmethod.latin.uix.wrapLightColorScheme

private val primaryLight = Color(0xFFC4FF00)
private val onPrimaryLight = Color(0xFF000000)
private val primaryContainerLight = Color(0xFFC0C016)
private val onPrimaryContainerLight = Color(0xFF1A3300)
private val secondaryLight = primaryLight
private val onSecondaryLight = onPrimaryLight
private val secondaryContainerLight = primaryContainerLight
private val onSecondaryContainerLight = onPrimaryContainerLight
private val tertiaryLight = primaryLight
private val onTertiaryLight = onPrimaryLight
private val tertiaryContainerLight = primaryContainerLight
private val onTertiaryContainerLight = onPrimaryContainerLight
private val errorLight = Color(0xFFFF0000)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFDAD6)
private val onErrorContainerLight = Color(0xFF410002)
private val backgroundLight = Color(0xFFFF0000)
private val onBackgroundLight = Color(0xFFFFFFFF)
private val surfaceLight = backgroundLight
private val onSurfaceLight = onBackgroundLight
private val surfaceVariantLight = surfaceLight
private val onSurfaceVariantLight = onSurfaceLight
private val outlineLight = Color(0xFFFFFF00)
private val outlineVariantLight = Color(0xFFFFFF00)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF000000)
private val inverseOnSurfaceLight = Color(0xFFFFFFFF)
private val inversePrimaryLight = Color(0xFFFFFFFF)
private val backgroundContainerLight = Color(0xFFFFEB3B)
private val backgroundContainerDimLight = Color(0xFFFFC107)
private val onBackgroundContainerLight = Color(0xFF000000)


private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
)


val HotDog = ThemeOption(
    dynamic = false,
    key = "HotDog",
    name = R.string.theme_hotdog,
    available = { false }
) {
    wrapLightColorScheme(lightScheme).let {
        /*it.copy(
            base = it.base,
            extended = it.extended.copy(
                backgroundContainer = backgroundContainerLight,
                backgroundContainerDim = backgroundContainerDimLight,
                onBackgroundContainer = onBackgroundContainerLight
            )
        )*/
        it
    }
}

@Composable
@Preview
private fun PreviewThemeLight() {
    ThemePreview(HotDog)
}




