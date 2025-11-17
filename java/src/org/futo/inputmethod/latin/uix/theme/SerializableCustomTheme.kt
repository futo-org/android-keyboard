package org.futo.inputmethod.latin.uix.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.DislikeAnalyzer
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.MathUtils
import com.google.android.material.color.utilities.TemperatureCache
import com.google.android.material.color.utilities.TonalPalette
import com.google.android.material.color.utilities.Variant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.futo.inputmethod.latin.uix.ExtraColors
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.wrapDarkColorScheme
import org.futo.inputmethod.latin.uix.wrapLightColorScheme
import java.io.File

private object ColorAsStringSerializer : KSerializer<SerializableColor> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ColorAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SerializableColor) {
        val argb = value.c
        val a = (argb shr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8)  and 0xFF
        val b =  argb         and 0xFF

        val hex = buildString {
            if (a.toInt() != 0xFF) append("%02X".format(a))
            append("%02X".format(r))
            append("%02X".format(g))
            append("%02X".format(b))
        }
        encoder.encodeString("#$hex")
    }

    override fun deserialize(decoder: Decoder): SerializableColor {
        val str = decoder.decodeString().removePrefix("#").uppercase()
        val len = str.length

        val (a, r, g, b) = when (len) {
            6 -> listOf("FF", str.substring(0,2), str.substring(2,4), str.substring(4,6))
            8 -> listOf(str.substring(0,2), str.substring(2,4), str.substring(4,6), str.substring(6,8))
            else -> throw IllegalArgumentException("Invalid color string: #$str")
        }
        val color = (a.toLong(16) shl 24) or
                (r.toLong(16) shl 16) or
                (g.toLong(16) shl 8)  or
                b.toLong(16)
        return SerializableColor(color and 0xFFFFFFFFL)
    }
}

@Serializable(with = ColorAsStringSerializer::class)
data class SerializableColor(val c: Long) {
    fun toColor(): Color = Color(c)
}

fun Color.toSColor() = SerializableColor(this.toArgb().toUInt().toLong())

@Serializable
data class SerializableCustomTheme(
    val primary: SerializableColor,
    val onPrimary: SerializableColor,
    val primaryContainer: SerializableColor,
    val onPrimaryContainer: SerializableColor,
    val inversePrimary: SerializableColor,
    val secondary: SerializableColor,
    val onSecondary: SerializableColor,
    val secondaryContainer: SerializableColor,
    val onSecondaryContainer: SerializableColor,
    val tertiary: SerializableColor,
    val onTertiary: SerializableColor,
    val tertiaryContainer: SerializableColor,
    val onTertiaryContainer: SerializableColor,
    val background: SerializableColor,
    val onBackground: SerializableColor,
    val surface: SerializableColor,
    val onSurface: SerializableColor,
    val surfaceVariant: SerializableColor,
    val onSurfaceVariant: SerializableColor,
    val surfaceTint: SerializableColor,
    val inverseSurface: SerializableColor,
    val inverseOnSurface: SerializableColor,
    val error: SerializableColor,
    val onError: SerializableColor,
    val errorContainer: SerializableColor,
    val onErrorContainer: SerializableColor,
    val outline: SerializableColor,
    val outlineVariant: SerializableColor,
    val scrim: SerializableColor,
    val surfaceBright: SerializableColor,
    val surfaceDim: SerializableColor,
    val surfaceContainer: SerializableColor,
    val surfaceContainerHigh: SerializableColor,
    val surfaceContainerHighest: SerializableColor,
    val surfaceContainerLow: SerializableColor,
    val surfaceContainerLowest: SerializableColor,
    val keyboardSurface: SerializableColor,
    val keyboardSurfaceDim: SerializableColor,
    val keyboardContainer: SerializableColor,
    val keyboardContainerVariant: SerializableColor,
    val onKeyboardContainer: SerializableColor,
    val keyboardPress: SerializableColor,
    val primaryTransparent: SerializableColor,
    val onSurfaceTransparent: SerializableColor,
    val keyboardContainerPressed: SerializableColor,
    val onKeyboardContainerPressed: SerializableColor,

    //val keyboardBackgroundGradient: Brush?,
    val hintColor: SerializableColor?,
    val hintHiVis: Boolean,

    val navigationBarColor: SerializableColor? = null,
    val keyboardBackgroundShader: String? = null,

    val backgroundImage: String? = null, // or inline base64?
    val backgroundImageOpacity: Float = 1.0f, // and cropping etc
    val keyRoundness: Float = 1.0f,
) {
    fun toKeyboardScheme(context: Context): KeyboardColorScheme {
        return KeyboardColorScheme(
            base = ColorScheme(
                primary                 = primary.toColor(),
                onPrimary               = onPrimary.toColor(),
                primaryContainer        = primaryContainer.toColor(),
                onPrimaryContainer      = onPrimaryContainer.toColor(),
                inversePrimary          = inversePrimary.toColor(),
                secondary               = secondary.toColor(),
                onSecondary             = onSecondary.toColor(),
                secondaryContainer      = secondaryContainer.toColor(),
                onSecondaryContainer    = onSecondaryContainer.toColor(),
                tertiary                = tertiary.toColor(),
                onTertiary              = onTertiary.toColor(),
                tertiaryContainer       = tertiaryContainer.toColor(),
                onTertiaryContainer     = onTertiaryContainer.toColor(),
                background              = background.toColor(),
                onBackground            = onBackground.toColor(),
                surface                 = surface.toColor(),
                onSurface               = onSurface.toColor(),
                surfaceVariant          = surfaceVariant.toColor(),
                onSurfaceVariant        = onSurfaceVariant.toColor(),
                surfaceTint             = surfaceTint.toColor(),
                inverseSurface          = inverseSurface.toColor(),
                inverseOnSurface        = inverseOnSurface.toColor(),
                error                   = error.toColor(),
                onError                 = onError.toColor(),
                errorContainer          = errorContainer.toColor(),
                onErrorContainer        = onErrorContainer.toColor(),
                outline                 = outline.toColor(),
                outlineVariant          = outlineVariant.toColor(),
                scrim                   = scrim.toColor(),
                surfaceBright           = surfaceBright.toColor(),
                surfaceDim              = surfaceDim.toColor(),
                surfaceContainer        = surfaceContainer.toColor(),
                surfaceContainerHigh    = surfaceContainerHigh.toColor(),
                surfaceContainerHighest = surfaceContainerHighest.toColor(),
                surfaceContainerLow     = surfaceContainerLow.toColor(),
                surfaceContainerLowest  = surfaceContainerLowest.toColor(),
            ),
            extended = ExtraColors(
                keyboardSurface            = keyboardSurface.toColor(),
                keyboardSurfaceDim         = keyboardSurfaceDim.toColor(),
                keyboardContainer          = keyboardContainer.toColor(),
                keyboardContainerVariant   = keyboardContainerVariant.toColor(),
                onKeyboardContainer        = onKeyboardContainer.toColor(),
                keyboardPress              = keyboardPress.toColor(),
                primaryTransparent         = primaryTransparent.toColor(),
                onSurfaceTransparent       = onSurfaceTransparent.toColor(),
                keyboardContainerPressed   = keyboardContainerPressed.toColor(),
                onKeyboardContainerPressed = onKeyboardContainerPressed.toColor(),

                hintColor          = hintColor?.toColor(),
                navigationBarColor = navigationBarColor?.toColor(),

                hintHiVis = hintHiVis,
                keyboardBackgroundShader = keyboardBackgroundShader,

                keyboardBackgroundBitmap = backgroundImage?.let {
                    val file = File(it)
                    if(file.isFile) {
                        BitmapFactory.decodeFile(file.absolutePath).asImageBitmap()
                    } else {
                        null
                    }
                },

                keyboardBackgroundGradient = backgroundImage?.let {
                    SolidColor(keyboardSurface.toColor().copy(alpha = 1.0f - backgroundImageOpacity))
                },

                keyRoundness = keyRoundness
            )
        )
    }
}

fun fromKeyboardScheme(scheme: KeyboardColorScheme): SerializableCustomTheme {
    return SerializableCustomTheme(
        primary                    = scheme.primary.toSColor(),
        onPrimary                  = scheme.onPrimary.toSColor(),
        primaryContainer           = scheme.primaryContainer.toSColor(),
        onPrimaryContainer         = scheme.onPrimaryContainer.toSColor(),
        inversePrimary             = scheme.inversePrimary.toSColor(),
        secondary                  = scheme.secondary.toSColor(),
        onSecondary                = scheme.onSecondary.toSColor(),
        secondaryContainer         = scheme.secondaryContainer.toSColor(),
        onSecondaryContainer       = scheme.onSecondaryContainer.toSColor(),
        tertiary                   = scheme.tertiary.toSColor(),
        onTertiary                 = scheme.onTertiary.toSColor(),
        tertiaryContainer          = scheme.tertiaryContainer.toSColor(),
        onTertiaryContainer        = scheme.onTertiaryContainer.toSColor(),
        background                 = scheme.background.toSColor(),
        onBackground               = scheme.onBackground.toSColor(),
        surface                    = scheme.surface.toSColor(),
        onSurface                  = scheme.onSurface.toSColor(),
        surfaceVariant             = scheme.surfaceVariant.toSColor(),
        onSurfaceVariant           = scheme.onSurfaceVariant.toSColor(),
        surfaceTint                = scheme.surfaceTint.toSColor(),
        inverseSurface             = scheme.inverseSurface.toSColor(),
        inverseOnSurface           = scheme.inverseOnSurface.toSColor(),
        error                      = scheme.error.toSColor(),
        onError                    = scheme.onError.toSColor(),
        errorContainer             = scheme.errorContainer.toSColor(),
        onErrorContainer           = scheme.onErrorContainer.toSColor(),
        outline                    = scheme.outline.toSColor(),
        outlineVariant             = scheme.outlineVariant.toSColor(),
        scrim                      = scheme.scrim.toSColor(),
        surfaceBright              = scheme.surfaceBright.toSColor(),
        surfaceDim                 = scheme.surfaceDim.toSColor(),
        surfaceContainer           = scheme.surfaceContainer.toSColor(),
        surfaceContainerHigh       = scheme.surfaceContainerHigh.toSColor(),
        surfaceContainerHighest    = scheme.surfaceContainerHighest.toSColor(),
        surfaceContainerLow        = scheme.surfaceContainerLow.toSColor(),
        surfaceContainerLowest     = scheme.surfaceContainerLowest.toSColor(),
        keyboardSurface            = scheme.keyboardSurface.toSColor(),
        keyboardSurfaceDim         = scheme.keyboardSurfaceDim.toSColor(),
        keyboardContainer          = scheme.keyboardContainer.toSColor(),
        keyboardContainerVariant   = scheme.keyboardContainerVariant.toSColor(),
        onKeyboardContainer        = scheme.onKeyboardContainer.toSColor(),
        keyboardPress              = scheme.keyboardPress.toSColor(),
        primaryTransparent         = scheme.primaryTransparent.toSColor(),
        onSurfaceTransparent       = scheme.onSurfaceTransparent.toSColor(),
        keyboardContainerPressed   = scheme.keyboardContainerPressed.toSColor(),
        onKeyboardContainerPressed = scheme.onKeyboardContainerPressed.toSColor(),

        hintColor                  = scheme.hintColor?.toSColor(),
        navigationBarColor         = scheme.navigationBarColor?.toSColor(),
        hintHiVis                  = scheme.hintHiVis,
        keyboardBackgroundShader   = scheme.keyboardBackgroundShader,
        keyRoundness               = scheme.extended.keyRoundness
    )
}

@SuppressLint("RestrictedApi")
internal fun getColorSchemeFromDynamicScheme(s: DynamicScheme): ColorScheme {
    val mdc = MaterialDynamicColors()

    return ColorScheme(
        primary                 = Color(mdc.primary().getArgb(s)),
        onPrimary               = Color(mdc.onPrimary().getArgb(s)),
        primaryContainer        = Color(mdc.primaryContainer().getArgb(s)),
        onPrimaryContainer      = Color(mdc.onPrimaryContainer().getArgb(s)),
        inversePrimary          = Color(mdc.inversePrimary().getArgb(s)),
        secondary               = Color(mdc.secondary().getArgb(s)),
        onSecondary             = Color(mdc.onSecondary().getArgb(s)),
        secondaryContainer      = Color(mdc.secondaryContainer().getArgb(s)),
        onSecondaryContainer    = Color(mdc.onSecondaryContainer().getArgb(s)),
        tertiary                = Color(mdc.tertiary().getArgb(s)),
        onTertiary              = Color(mdc.onTertiary().getArgb(s)),
        tertiaryContainer       = Color(mdc.tertiaryContainer().getArgb(s)),
        onTertiaryContainer     = Color(mdc.onTertiaryContainer().getArgb(s)),
        background              = Color(mdc.background().getArgb(s)),
        onBackground            = Color(mdc.onBackground().getArgb(s)),
        surface                 = Color(mdc.surface().getArgb(s)),
        onSurface               = Color(mdc.onSurface().getArgb(s)),
        surfaceVariant          = Color(mdc.surfaceVariant().getArgb(s)),
        onSurfaceVariant        = Color(mdc.onSurfaceVariant().getArgb(s)),
        surfaceTint             = Color(mdc.surfaceTint().getArgb(s)),
        inverseSurface          = Color(mdc.inverseSurface().getArgb(s)),
        inverseOnSurface        = Color(mdc.inverseOnSurface().getArgb(s)),
        error                   = Color(mdc.error().getArgb(s)),
        onError                 = Color(mdc.onError().getArgb(s)),
        errorContainer          = Color(mdc.errorContainer().getArgb(s)),
        onErrorContainer        = Color(mdc.onErrorContainer().getArgb(s)),
        outline                 = Color(mdc.outline().getArgb(s)),
        outlineVariant          = Color(mdc.outlineVariant().getArgb(s)),
        scrim                   = Color(mdc.scrim().getArgb(s)),
        surfaceBright           = Color(mdc.surfaceBright().getArgb(s)),
        surfaceContainer        = Color(mdc.surfaceContainer().getArgb(s)),
        surfaceContainerHigh    = Color(mdc.surfaceContainerHigh().getArgb(s)),
        surfaceContainerHighest = Color(mdc.surfaceContainerHighest().getArgb(s)),
        surfaceContainerLow     = Color(mdc.surfaceContainerLow().getArgb(s)),
        surfaceContainerLowest  = Color(mdc.surfaceContainerLowest().getArgb(s)),
        surfaceDim              = Color(mdc.surfaceDim().getArgb(s)),
    )
}


@SuppressLint("RestrictedApi")
data class CustomThemeBuilderConfiguration(
    val hue: Double,
    val chroma: Double,
    val tone: Double,
    val darkMode: Boolean,
    val amoledDark: Boolean,
    val contrast: Float
) {
    val variant = Variant.VIBRANT
    val col = Hct.from(hue, chroma, tone)
    val palette = TonalPalette.fromHueAndChroma(hue, chroma)
    fun getTone(tone: Double): Color = Color(palette.getHct(tone).toInt())
    fun buildScheme(): KeyboardColorScheme {
        val dynamicScheme = DynamicScheme(
            col,
            variant,
            darkMode,
            contrast.toDouble(),
            TonalPalette.fromHueAndChroma(col.hue, col.chroma),
            TonalPalette.fromHueAndChroma(MathUtils.sanitizeDegreesDouble(col.hue + 15.0), maxOf(col.chroma - 32.0, col.chroma * 0.5)),
            TonalPalette.fromHct(DislikeAnalyzer.fixIfDisliked(TemperatureCache(col).getAnalogousColors(3, 6).get(2))),
            TonalPalette.fromHueAndChroma(col.hue, col.chroma / 1.0),
            TonalPalette.fromHueAndChroma(col.hue, col.chroma / 1.0 + 4.0),
        )

        val scheme = getColorSchemeFromDynamicScheme(dynamicScheme)

        return when {
            darkMode -> wrapDarkColorScheme(scheme)
            else -> wrapLightColorScheme(scheme)
        }
    }
}