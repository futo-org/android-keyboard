package org.futo.inputmethod.latin.uix.theme

import android.annotation.SuppressLint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.google.android.material.color.utilities.DislikeAnalyzer
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MathUtils
import com.google.android.material.color.utilities.TemperatureCache
import com.google.android.material.color.utilities.TonalPalette
import com.google.android.material.color.utilities.Variant
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.wrapDarkColorScheme
import org.futo.inputmethod.latin.uix.wrapLightColorScheme
import kotlin.math.pow

@SuppressLint("RestrictedApi")
data class CustomThemeBuilderConfiguration(
    val hue: Double = 0.0,
    val chroma: Double = 0.0,
    val tone: Double = 50.0,
    val darkMode: Boolean = true,
    val contrast: Float = 0f,
    val roundness: Float = 1f,
    val borders: Boolean = true,

    val backgroundImagePath: String? = null,
    val backgroundImageOpacity: Float = 0.7f,
    val backgroundImageRect: Rect = Rect.Zero,
    val thumbnailImagePath: String? = null,
    val thumbnailImageScale: Float = 1.0f,
) {
    private val variant = Variant.VIBRANT
    private val col: Hct = Hct.from(hue, chroma, tone)
    private val palette: TonalPalette = TonalPalette.fromHueAndChroma(hue, chroma)
    private fun getTone(tone: Double): Color = Color(palette.getHct(tone).toInt())
    private fun buildScheme(): KeyboardColorScheme {
        val dynamicScheme = DynamicScheme(
            col,
            variant,
            darkMode,
            contrast.toDouble(),
            TonalPalette.fromHueAndChroma(col.hue, col.chroma),
            TonalPalette.fromHueAndChroma(
                MathUtils.sanitizeDegreesDouble(col.hue + 15.0),
                maxOf(col.chroma - 32.0, col.chroma * 0.5)
            ),
            TonalPalette.fromHct(
                DislikeAnalyzer.fixIfDisliked(
                    TemperatureCache(col).getAnalogousColors(
                        3,
                        6
                    ).get(2)
                )
            ),
            TonalPalette.fromHueAndChroma(col.hue, col.chroma / 1.0),
            TonalPalette.fromHueAndChroma(col.hue, col.chroma / 1.0 + 4.0),
        )

        val scheme = getColorSchemeFromDynamicScheme(dynamicScheme)

        return postProcessColors(when {
            darkMode -> wrapDarkColorScheme(scheme)
            else -> wrapLightColorScheme(scheme)
        })
    }

    private fun postProcessColors(scheme: KeyboardColorScheme) = if (!darkMode) {
        val surfaceModifier = getTone(75.0).copy(alpha = (1.0f - contrast) * 0.8f + 0.1f)
        scheme.copy(
            extended = scheme.extended.copy(
                keyboardContainerVariant = scheme.surfaceTint.copy(alpha = (1.0f - contrast) * 0.2f)
                    .compositeOver(scheme.surfaceContainer),
                keyboardContainer = scheme.surfaceContainerLowest.copy(alpha = contrast)
                    .compositeOver(scheme.surfaceContainer),
                keyboardSurface = surfaceModifier
                    .compositeOver(scheme.surfaceContainerHigh),
                keyboardSurfaceDim = scheme.surfaceTint.copy(alpha = (1.0f - contrast) * 0.07f)
                    .compositeOver(surfaceModifier)
                    .compositeOver(scheme.surfaceContainerHighest)
            )
        )
    } else {
        val contrast = if (contrast < 0.5) {
            contrast / 2.0f
        } else {
            1.0f - ((1.0f - contrast) / 3.0f)
        }
        scheme.copy(
            extended = scheme.extended.copy(
                keyboardContainerVariant = scheme.surfaceTint.copy(alpha = (1.0f - contrast) * 0.12f)
                    .compositeOver(scheme.surfaceContainerHigh),
                keyboardContainer = scheme.surfaceContainerHighest.copy(alpha = contrast)
                    .compositeOver(scheme.surfaceContainerHigh),
                keyboardSurface = scheme.keyboardSurface.copy(alpha = 1.0f - (contrast.pow(4.0f)))
                    .compositeOver(Color.Companion.Black)
            )
        )
    }

    fun build(): SerializableCustomTheme {
        val colorScheme = buildScheme()
        return initBasicSerializableThemeFromKeyboardScheme(colorScheme).let {
            if(backgroundImagePath != null) {
                val alpha = (1.0f - backgroundImageOpacity.pow(2)) * 0.32f + 0.5f
                it.copy(
                    backgroundImage = backgroundImagePath,
                    backgroundImageOpacity = backgroundImageOpacity,
                    thumbnailImage = thumbnailImagePath,
                    thumbnailScale = thumbnailImageScale,
                    backgroundImageCropping = listOf(backgroundImageRect.left, backgroundImageRect.top, backgroundImageRect.right, backgroundImageRect.bottom),
                    keyboardContainer = it.keyboardContainer.toColor()
                        .copy(alpha = alpha).toSColor(),
                    keyboardContainerVariant = it.keyboardContainerVariant.toColor()
                        .copy(alpha = alpha).toSColor(),

                    keyRoundness = roundness,
                    keyBorders = borders,

                )
            } else {
                it
            }
        }
    }
}