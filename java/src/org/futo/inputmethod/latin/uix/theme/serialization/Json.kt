package org.futo.inputmethod.latin.uix.theme.serialization

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.get
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.MaterialDynamicColors
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.uix.ExtraColors
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.theme.AdvancedThemeOptions
import org.futo.inputmethod.latin.uix.theme.KeyBackground
import org.futo.inputmethod.latin.uix.theme.KeyIcon
import org.futo.inputmethod.latin.uix.theme.ThemeDecodingContext
import org.futo.inputmethod.latin.uix.theme.decodeKeyedBitmaps
import org.futo.inputmethod.latin.uix.theme.decodeOptionalFont
import org.futo.inputmethod.latin.uix.theme.decodeOptionalImage
import org.futo.inputmethod.latin.uix.utils.createNinePatchDrawable
import kotlin.math.roundToInt

private object ColorAsStringSerializer : KSerializer<SerializableJsonColor> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ColorAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SerializableJsonColor) {
        encoder.encodeString(argbLongToHexColorString(value.c, AlphaOrder.ARGB))
    }

    override fun deserialize(decoder: Decoder): SerializableJsonColor {
        return SerializableJsonColor(parseHexColorStringToARGBLong(decoder.decodeString(),
            AlphaOrder.ARGB))
    }
}

@Serializable(with = ColorAsStringSerializer::class)
data class SerializableJsonColor(val c: Long) {
    fun toColor(): Color = Color(c)
}

fun Color.toSColor() = SerializableJsonColor(this.toArgb().toUInt().toLong())

// Require this color not be invisible
private fun Int?.argbNotInvisible(): Int? = this?.let {
    if((it shr 24) and 0xff == 0) null else it
}

/**
 * Reads the 1-pixel 9-patch border and returns a NinePatchDrawable.
 * The supplied bitmap must still contain the 1-pixel frame.
 * Input bitmap is assumed to be 640dp and gets scaled according to resources screen density
 */
internal fun Bitmap.toNinePatchDrawable(res: Resources): KeyBackground? {
    val targetDensity = 640.0f
    val w = width
    val h = height

    val scale = res.displayMetrics.densityDpi / targetDensity
    fun sc(v: Int) = (v * scale).roundToInt()

    val xRegions = mutableListOf<Pair<Int, Int>>()
    val yRegions = mutableListOf<Pair<Int, Int>>()
    val padding = Rect(0, 0, 0, 0)

    val black = 0xFF000000.toInt()

    // top line -> horizontal stretch
    var stretchStart = -1
    for (x in 1 until w - 1) {
        val c = this[x, 0]
        val isBlack = c == black
        if (isBlack && stretchStart == -1) stretchStart = x - 1
        if (!isBlack && stretchStart != -1) {
            xRegions.add(sc(stretchStart) to sc(x - 1))
            stretchStart = -1
        }
    }
    if (stretchStart != -1) xRegions.add(sc(stretchStart) to sc(w - 2))

    // left line -> vertical stretch
    stretchStart = -1
    for (y in 1 until h - 1) {
        val c = this[0, y]
        val isBlack = c == black
        if (isBlack && stretchStart == -1) stretchStart = y - 1
        if (!isBlack && stretchStart != -1) {
            yRegions.add(sc(stretchStart) to sc(y - 1))
            stretchStart = -1
        }
    }
    if (stretchStart != -1) yRegions.add(sc(stretchStart) to sc(h - 2))

    var paddingStart = -1
    for (x in 1 until w) {
        val c = this[x, h - 1]
        val isBlack = c == black
        if (isBlack && paddingStart == -1) paddingStart = x - 1
        if (!isBlack && paddingStart != -1) {
            padding.left = sc(paddingStart)
            padding.right = sc(x - 1)
            paddingStart = -1
            break
        }
    }
    if (paddingStart != -1) {
        padding.left = sc(paddingStart)
        padding.right = sc(w - 1)
    }

    paddingStart = -1
    for (y in 1 until h) {
        val c = this[w - 1, y]
        val isBlack = c == black
        if (isBlack && paddingStart == -1) paddingStart = y - 1
        if (!isBlack && paddingStart != -1) {
            padding.top = sc(paddingStart)
            padding.bottom = sc(y - 1)
            paddingStart = -1
            break
        }
    }
    if (paddingStart != -1) {
        padding.top = sc(paddingStart)
        padding.bottom = sc(h - 1)
    }

    val fgColor = this[0, 0].argbNotInvisible()

    return createNinePatchDrawable(this, scale, res, fgColor, android.graphics.Color.WHITE, xRegions, yRegions, padding,
        removeMargin = 1)
}

@Serializable
data class SerializableJsonTheme(
    val primary: SerializableJsonColor,
    val onPrimary: SerializableJsonColor,
    val primaryContainer: SerializableJsonColor,
    val onPrimaryContainer: SerializableJsonColor,
    val inversePrimary: SerializableJsonColor,
    val secondary: SerializableJsonColor,
    val onSecondary: SerializableJsonColor,
    val secondaryContainer: SerializableJsonColor,
    val onSecondaryContainer: SerializableJsonColor,
    val tertiary: SerializableJsonColor,
    val onTertiary: SerializableJsonColor,
    val tertiaryContainer: SerializableJsonColor,
    val onTertiaryContainer: SerializableJsonColor,
    val background: SerializableJsonColor,
    val onBackground: SerializableJsonColor,
    val surface: SerializableJsonColor,
    val onSurface: SerializableJsonColor,
    val surfaceVariant: SerializableJsonColor,
    val onSurfaceVariant: SerializableJsonColor,
    val surfaceTint: SerializableJsonColor,
    val inverseSurface: SerializableJsonColor,
    val inverseOnSurface: SerializableJsonColor,
    val error: SerializableJsonColor,
    val onError: SerializableJsonColor,
    val errorContainer: SerializableJsonColor,
    val onErrorContainer: SerializableJsonColor,
    val outline: SerializableJsonColor,
    val outlineVariant: SerializableJsonColor,
    val scrim: SerializableJsonColor,
    val surfaceBright: SerializableJsonColor,
    val surfaceDim: SerializableJsonColor,
    val surfaceContainer: SerializableJsonColor,
    val surfaceContainerHigh: SerializableJsonColor,
    val surfaceContainerHighest: SerializableJsonColor,
    val surfaceContainerLow: SerializableJsonColor,
    val surfaceContainerLowest: SerializableJsonColor,
    val keyboardSurface: SerializableJsonColor,
    val keyboardSurfaceDim: SerializableJsonColor,
    val keyboardContainer: SerializableJsonColor,
    val keyboardContainerVariant: SerializableJsonColor,
    val onKeyboardContainer: SerializableJsonColor,
    val keyboardPress: SerializableJsonColor,
    val primaryTransparent: SerializableJsonColor,
    val onSurfaceTransparent: SerializableJsonColor,
    val keyboardContainerPressed: SerializableJsonColor,
    val onKeyboardContainerPressed: SerializableJsonColor,

    val hintColor: SerializableJsonColor?,
    val hintHiVis: Boolean = false,

    val navigationBarColor: SerializableJsonColor? = null,
    val keyboardBackgroundShader: String? = null,

    val thumbnailImage: String? = null,
    val thumbnailScale: Float = 1.0f,
    val backgroundImage: String? = null,
    val backgroundImageOpacity: Float = 1.0f,
    val backgroundImageCropping: List<Float> = emptyList(),

    val keyRoundness: Float = 1.0f,
    val keyBorders: Boolean = true,

    val keysFont: String? = null,

    val name: String? = null,
    val id: String? = null,
    val author: String? = null,
    val url: String? = null,

    val keyBackgrounds: Map<String, String> = mapOf(),
    val keyIcons: Map<String, String> = mapOf()
)  {
    fun toKeyboardScheme(ctx: ThemeDecodingContext): KeyboardColorScheme {
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

                keyboardBackgroundGradient = backgroundImage?.let {
                    SolidColor(keyboardSurface.toColor().copy(alpha = 1.0f - backgroundImageOpacity))
                },
                advancedThemeOptions = AdvancedThemeOptions(
                    keyRoundness = keyRoundness,
                    keyBorders = keyBorders,
                    backgroundShader = keyboardBackgroundShader,

                    thumbnailImage = decodeOptionalImage(ctx, thumbnailImage),
                    thumbnailScale = thumbnailScale,
                    backgroundImage = decodeOptionalImage(ctx, backgroundImage),
                    backgroundImageVisibleArea = run {
                        if(backgroundImageCropping.size == 4) {
                            Rect(backgroundImageCropping[0].roundToInt(), backgroundImageCropping[1].roundToInt(), backgroundImageCropping[2].roundToInt(), backgroundImageCropping[3].roundToInt())
                        } else {
                            null
                        }
                    },
                    keyBackgrounds = decodeKeyedBitmaps(ctx, keyBackgrounds.entries, keyFn={it.key}, valFn={it.value}) { _, it ->
                        val bitmap = it.asAndroidBitmap()
                        bitmap.toNinePatchDrawable(ctx.context.resources)
                    },
                    keyIcons = decodeKeyedBitmaps(ctx, keyIcons.entries, keyFn={it.key}, valFn={it.value}) { _, it ->
                        KeyIcon(it.asAndroidBitmap().toDrawable(ctx.context.resources))
                    },
                    font = decodeOptionalFont(ctx, keysFont),

                    themeName = name,
                    themeAuthor = author
                ),
            )
        )
    }
}
@OptIn(ExperimentalSerializationApi::class)
val themeJson = Json {
    allowComments = true
    allowTrailingComma = true
    prettyPrint = true
    //encodeDefaults = true
}
class JsonZipTheme(val json: String) : SerializableTheme {
    private val parsed = themeJson.decodeFromString<SerializableJsonTheme>(json)
    override fun toKeyboardScheme(ctx: ThemeDecodingContext): KeyboardColorScheme {
        return parsed.toKeyboardScheme(ctx)
    }

    override fun validate(): Boolean {
        return true
    }
    override val id: String? get() = parsed.id
    override val thumbnailImage: String? get() = parsed.thumbnailImage

    override val name: String get() = parsed.name ?: "Unknown name"
    override val author: String get() = parsed.author ?: "Unknown author"
}



fun initBasicSerializableThemeFromKeyboardScheme(scheme: KeyboardColorScheme): SerializableJsonTheme {
    return SerializableJsonTheme(
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

