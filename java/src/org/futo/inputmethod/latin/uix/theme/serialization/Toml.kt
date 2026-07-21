    package org.futo.inputmethod.latin.uix.theme.serialization

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.exceptions.TomlDecodingException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.futo.inputmethod.latin.uix.ExtraColors
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.theme.AdvancedThemeOptions
import org.futo.inputmethod.latin.uix.theme.KeyIcon
import org.futo.inputmethod.latin.uix.theme.ThemeDecodingContext
import org.futo.inputmethod.latin.uix.theme.decodeKeyedBitmaps
import org.futo.inputmethod.latin.uix.theme.decodeOptionalFont
import org.futo.inputmethod.latin.uix.theme.decodeOptionalImage
import org.futo.inputmethod.latin.uix.utils.createNinePatchDrawable
import kotlin.math.abs
import kotlin.math.roundToInt

    // Why does TOML throw an exception for floats specified without decimal point?!
object LenientFloatSerializer : KSerializer<Float> {
    override val descriptor = PrimitiveSerialDescriptor("LenientFloat", PrimitiveKind.FLOAT)

    override fun serialize(encoder: Encoder, value: Float) {
        encoder.encodeFloat(value)
    }

    override fun deserialize(decoder: Decoder): Float {
        return try {
            decoder.decodeFloat()
        }catch(e: TomlDecodingException) {
            decoder.decodeInt().toFloat()
        }
    }
}


typealias LFloat = @Serializable(with = LenientFloatSerializer::class) Float

@Serializable
private data class SerializedTomlFile(
    val name: String = "Untitled theme",
    val author: String = "Anonymous",
    val id: String? = null,
    val version: Int = 1,
    val description: String = "No description specified.",
    val options: Options = Options(),
    val colors: Colors,
    val matchrules: Matchrules = Matchrules(),
    val asset: Asset = Asset(),
) {
    @Serializable
    data class Options(
        val auto_borders: Boolean = true,
        val center_hints: Boolean = false,
        val roundedness: LFloat = 1.0f,

        val scale_text: LFloat = 1.0f,
        val scale_hints: LFloat = 1.0f,
        val weight_text: LFloat = 400.0f,
        val weight_hints: LFloat = 500.0f,
        val font: Font? = null,
        val background: Background? = null,
    ) {
        @Serializable
        data class Font(val font: String? = null)

        @Serializable
        data class Background(
            val image: String,
            val opacity: LFloat = 0.5f,
            val action_bar_opacity: LFloat = 0.5f,

            val cropping: List<LFloat> = listOf(0.0f, 0.0f, 1.0f, 1.0f)
        )
    }

    @Serializable
    data class Colors(
        val primary: String,
        val on_primary: String,
        val primary_container: String,
        val on_primary_container: String,
        val inverse_primary: String,

        val secondary: String,
        val on_secondary: String,
        val secondary_container: String,
        val on_secondary_container: String,

        val tertiary: String,
        val on_tertiary: String,
        val tertiary_container: String,
        val on_tertiary_container: String,

        val background: String,
        val on_background: String,

        val surface: String,
        val on_surface: String,
        val surface_variant: String,
        val on_surface_variant: String,
        val surface_tint: String,
        val inverse_surface: String,
        val inverse_on_surface: String,

        val error: String,
        val on_error: String,
        val error_container: String,
        val on_error_container: String,

        val outline: String,
        val outline_variant: String,
        val scrim: String,

        val surface_bright: String,
        val surface_dim: String,
        val surface_container: String,
        val surface_container_high: String,
        val surface_container_highest: String,
        val surface_container_low: String,
        val surface_container_lowest: String,

        val keyboard_surface: String,
        val keyboard_surface_dim: String,
        val keyboard_container: String,
        val keyboard_container_variant: String,
        val on_keyboard_container: String,
        val keyboard_press: String,
        val keyboard_container_pressed: String,
        val on_keyboard_container_pressed: String,
    )

    @Serializable
    data class Matchrules(
        val border: List<Matchrule> = emptyList(),
        val icon: List<Matchrule> = emptyList()
    ) {
        @Serializable
        data class Matchrule(
            val selector: String,
            val asset: String,
        )
    }

    class ValidationException(val reason: String) : Exception(reason)

    @Serializable
    data class Asset(
        val icon: List<Icon> = emptyList(),
        val border: List<Border> = emptyList()
    ) {
        @Serializable
        data class Icon(
            val name: String,
            val target_density: LFloat = 640.0f
        )

        @Serializable
        data class Border(
            val name: String,
            val target_density: LFloat = 640.0f,
            val background_tint: String = "#ffffff",
            val foreground_tint: String = "#777777",
            val padding: List<LFloat> = listOf(0.0f, 0.0f, 0.0f, 0.0f),
            val slicing: List<LFloat> = listOf(0.0f, 0.0f, 1.0f, 1.0f),
            val gap: List<LFloat> = listOf(1.0f, 1.0f, 1.0f, 1.0f)
        )
    }

    fun lookupBorder(name: String) =
        asset.border.firstOrNull { it.name == name } ?: Asset.Border(name)

    fun lookupIcon(name: String) =
        asset.icon.firstOrNull { it.name == name } ?: Asset.Icon(name)

    private fun String.fromNamedColor() = when(this) {
            "primary" ->    colors.primary
            "on_primary" ->     colors.on_primary
            "primary_container" ->  colors.primary_container
            "on_primary_container" ->   colors.on_primary_container
            "inverse_primary" ->    colors.inverse_primary
            "secondary" ->  colors.secondary
            "on_secondary" ->   colors.on_secondary
            "secondary_container" ->    colors.secondary_container
            "on_secondary_container" ->     colors.on_secondary_container
            "tertiary" ->   colors.tertiary
            "on_tertiary" ->    colors.on_tertiary
            "tertiary_container" ->     colors.tertiary_container
            "on_tertiary_container" ->  colors.on_tertiary_container
            "background" ->     colors.background
            "on_background" ->  colors.on_background
            "surface" ->    colors.surface
            "on_surface" ->     colors.on_surface
            "surface_variant" ->    colors.surface_variant
            "on_surface_variant" ->     colors.on_surface_variant
            "surface_tint" ->   colors.surface_tint
            "inverse_surface" ->    colors.inverse_surface
            "inverse_on_surface" ->     colors.inverse_on_surface
            "error" ->  colors.error
            "on_error" ->   colors.on_error
            "error_container" ->    colors.error_container
            "on_error_container" ->     colors.on_error_container
            "outline" ->    colors.outline
            "outline_variant" ->    colors.outline_variant
            "scrim" ->  colors.scrim
            "surface_bright" ->     colors.surface_bright
            "surface_dim" ->    colors.surface_dim
            "surface_container" ->  colors.surface_container
            "surface_container_high" ->     colors.surface_container_high
            "surface_container_highest" ->  colors.surface_container_highest
            "surface_container_low" ->  colors.surface_container_low
            "surface_container_lowest" ->   colors.surface_container_lowest
            "keyboard_surface" ->   colors.keyboard_surface
            "keyboard_surface_dim" ->   colors.keyboard_surface_dim
            "keyboard_container" ->     colors.keyboard_container
            "keyboard_container_variant" ->     colors.keyboard_container_variant
            "on_keyboard_container" ->  colors.on_keyboard_container
            "keyboard_press" ->     colors.keyboard_press
            "keyboard_container_pressed" ->     colors.keyboard_container_pressed
            "on_keyboard_container_pressed" ->  colors.on_keyboard_container_pressed
            else -> throw IllegalArgumentException("Unknown named color $this")
    }.also { if(!it.startsWith("#")) throw IllegalArgumentException("Core colors must not be aliased") }

    private fun String.toColor(): Color = when {
        this.startsWith("#") -> Color(parseHexColorStringToARGBLong(this, AlphaOrder.RGBA))
        else -> this.fromNamedColor().toColor()
    }


    fun validate(): Boolean {
        val colorPairs = listOf(
            "primary:onPrimary" to colors.primary to colors.on_primary,
            "secondary:onSecondary" to colors.secondary to colors.on_secondary,
            "tertiary:onTertiary" to colors.tertiary to colors.on_tertiary,
            "background:onBackground" to colors.background to colors.on_background,
            "surface:onSurface" to colors.surface to colors.on_surface,
            "primaryContainer:onPrimaryContainer" to colors.primary_container to colors.on_primary_container,
            "secondaryContainer:onSecondaryContainer" to colors.secondary_container to colors.on_secondary_container,
            "tertiaryContainer:onTertiaryContainer" to colors.tertiary_container to colors.on_tertiary_container
        )

        for(v in colorPairs) {
            val bgname = v.first.first
            val bg = v.first.second
            val fg = v.second

            val bgc = bg.toColor().compositeOver(colors.background.toColor()).compositeOver(Color.White)
            val fgc = fg.toColor().compositeOver(bgc)

            val contrast = abs(bgc.luminance() - fgc.luminance())
            if(contrast < 0.08) {
                throw ValidationException("Contrast between these colors is far too insufficient and UI elements will not be readable: $bgname (bg=$bg fg=$fg)")
            }
        }

        return true
    }

    fun toKeyboardScheme(ctx: ThemeDecodingContext): KeyboardColorScheme {
        val backgroundImage = options.background?.let { decodeOptionalImage(ctx, it.image) }
        return KeyboardColorScheme(
            base = ColorScheme(
                primary                 = colors.primary.toColor(),
                onPrimary               = colors.on_primary.toColor(),
                primaryContainer        = colors.primary_container.toColor(),
                onPrimaryContainer      = colors.on_primary_container.toColor(),
                inversePrimary          = colors.inverse_primary.toColor(),
                secondary               = colors.secondary.toColor(),
                onSecondary             = colors.on_secondary.toColor(),
                secondaryContainer      = colors.secondary_container.toColor(),
                onSecondaryContainer    = colors.on_secondary_container.toColor(),
                tertiary                = colors.tertiary.toColor(),
                onTertiary              = colors.on_tertiary.toColor(),
                tertiaryContainer       = colors.tertiary_container.toColor(),
                onTertiaryContainer     = colors.on_tertiary_container.toColor(),
                background              = colors.background.toColor(),
                onBackground            = colors.on_background.toColor(),
                surface                 = colors.surface.toColor(),
                onSurface               = colors.on_surface.toColor(),
                surfaceVariant          = colors.surface_variant.toColor(),
                onSurfaceVariant        = colors.on_surface_variant.toColor(),
                surfaceTint             = colors.surface_tint.toColor(),
                inverseSurface          = colors.inverse_surface.toColor(),
                inverseOnSurface        = colors.inverse_on_surface.toColor(),
                error                   = colors.error.toColor(),
                onError                 = colors.on_error.toColor(),
                errorContainer          = colors.error_container.toColor(),
                onErrorContainer        = colors.on_error_container.toColor(),
                outline                 = colors.outline.toColor(),
                outlineVariant          = colors.outline_variant.toColor(),
                scrim                   = colors.scrim.toColor(),
                surfaceBright           = colors.surface_bright.toColor(),
                surfaceDim              = colors.surface_dim.toColor(),
                surfaceContainer        = colors.surface_container.toColor(),
                surfaceContainerHigh    = colors.surface_container_high.toColor(),
                surfaceContainerHighest = colors.surface_container_highest.toColor(),
                surfaceContainerLow     = colors.surface_container_low.toColor(),
                surfaceContainerLowest  = colors.surface_container_lowest.toColor(),
            ),
            extended = ExtraColors(
                keyboardSurface            = colors.keyboard_surface.toColor(),
                keyboardSurfaceDim         = colors.keyboard_surface_dim.toColor(),
                keyboardContainer          = colors.keyboard_container.toColor(),
                keyboardContainerVariant   = colors.keyboard_container_variant.toColor(),
                onKeyboardContainer        = colors.on_keyboard_container.toColor(),
                keyboardPress              = colors.keyboard_press.toColor(),
                primaryTransparent         = colors.primary.toColor().copy(alpha = 0.5f),
                onSurfaceTransparent       = colors.on_surface.toColor().copy(alpha = 0.5f),
                keyboardContainerPressed   = colors.keyboard_container_pressed.toColor(),
                onKeyboardContainerPressed = colors.on_keyboard_container_pressed.toColor(),

                hintColor          = null,
                navigationBarColor = null,

                keyboardBackgroundGradient = options.background?.let {
                    SolidColor(colors.keyboard_surface.toColor().copy(alpha = 1.0f - it.opacity))
                },
                advancedThemeOptions = AdvancedThemeOptions(
                    textSizeMultiplier = options.scale_text,
                    hintSizeMultiplier = options.scale_hints,
                    textWeight = options.weight_text,
                    hintWeight = options.weight_hints,

                    centerHints = options.center_hints,

                    keyRoundness = options.roundedness,
                    keyBorders = options.auto_borders,
                    backgroundShader = null,

                    thumbnailImage = backgroundImage,
                    thumbnailScale = 1.0f,
                    backgroundImage = backgroundImage,
                    backgroundImageVisibleArea = run {
                        if(backgroundImage != null && options.background?.cropping?.size == 4) {

                            Rect(
                                (backgroundImage.width * options.background.cropping[0]).roundToInt(),
                                (backgroundImage.height * options.background.cropping[1]).roundToInt(),
                                (backgroundImage.width * options.background.cropping[2]).roundToInt(),
                                (backgroundImage.height * options.background.cropping[3]).roundToInt(),
                            )
                        } else {
                            null
                        }
                    },
                    keyBackgrounds = decodeKeyedBitmaps(ctx, matchrules.border, keyFn={it.selector}, valFn={it.asset}) { cfg, img ->
                        val bitmap = img.asAndroidBitmap()
                        val meta = lookupBorder(cfg.asset)

                        val scale = ctx.context.resources.displayMetrics.densityDpi / meta.target_density
                        val w = (bitmap.width * scale).roundToInt()
                        val h = (bitmap.height * scale).roundToInt()

                        val xRegions = mutableListOf<Pair<Int, Int>>()
                        val yRegions = mutableListOf<Pair<Int, Int>>()
                        val padding = Rect(0, 0, 0, 0)
                        val gap = RectF(1.0f, 1.0f, 1.0f, 1.0f)

                        if(meta.slicing.size == 4) {
                            xRegions.add((meta.slicing[0] * w).roundToInt() to(meta.slicing[2] * w).roundToInt())
                            yRegions.add((meta.slicing[1] * h).roundToInt() to (meta.slicing[3] * h).roundToInt())
                        }

                        if(meta.padding.size == 4) {
                            padding.left = (meta.padding[0] * w).roundToInt()
                            padding.top = (meta.padding[1] * h).roundToInt()
                            padding.right = ((1.0f-meta.padding[2]) * w).roundToInt()
                            padding.bottom = ((1.0f-meta.padding[3]) * h).roundToInt()
                        }

                        if(meta.gap.size == 4) {
                            gap.left = meta.gap[0]
                            gap.top = meta.gap[1]
                            gap.right = meta.gap[2]
                            gap.bottom = meta.gap[3]
                        }

                        createNinePatchDrawable(bitmap, scale, ctx.context.resources,
                            meta.foreground_tint.toColor().toArgb(),
                            meta.background_tint.toColor().toArgb(),
                            xRegions, yRegions, padding, gap)
                    },
                    keyIcons = decodeKeyedBitmaps(ctx, matchrules.icon, keyFn={it.selector}, valFn={it.asset}) { cfg, img ->
                        KeyIcon(img.asAndroidBitmap().toDrawable(ctx.context.resources))
                    },
                    font = decodeOptionalFont(ctx, options.font?.font),

                    themeName = name,
                    themeAuthor = author
                ),
            )
        )
    }
}

private val formatVersionRegex = """Format version: (\d\.?\d?)""".toRegex(setOf(RegexOption.IGNORE_CASE))
fun findFormatVersion(v: String): Int? =
    formatVersionRegex.find(v)
        ?.groupValues
        ?.getOrNull(1)
        ?.split(".")
        ?.firstOrNull()
        ?.toIntOrNull()

class TomlZipTheme(string: String) : SerializableTheme {
    val formatVersion = findFormatVersion(string)

    private val parsed = Toml.decodeFromString<SerializedTomlFile>(string)
    override fun toKeyboardScheme(ctx: ThemeDecodingContext): KeyboardColorScheme {
        return parsed.toKeyboardScheme(ctx)
    }

    override fun validate(): Boolean {
        return parsed.validate()
    }
    override val id: String? get() = parsed.id
    override val thumbnailImage: String? get() = parsed.options.background?.image

    override val name: String get() = parsed.name
    override val author: String get() = parsed.author
}