package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.annotation.ColorRes
import androidx.annotation.DoNotInline
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.core.math.MathUtils
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import kotlin.math.pow
import kotlin.math.roundToInt

typealias Tones = MutableMap<Int, Color>

private val StandardToneKeys = listOf(100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)
data class TonalPalette(
    val primary: Tones, // accent1
    val secondary: Tones, // accent2
    val tertiary: Tones, // accent3
    val neutral: Tones, // neutral1
    val neutralVariant: Tones, // neutral2
    val error: Tones, // error
    val otherDynamicColors: Map<String, Color>
) {
    private fun lookupTones(luminance: Int, tones: Tones): Color =
        tones.getOrPut(luminance, {
            val closestKey = if(luminance > 50) {
                StandardToneKeys.filter { it < luminance }.max()
            } else {
                StandardToneKeys.filter { it > luminance }.min()
            }
            tones[closestKey]!!.setLuminance(luminance.toFloat())
        })

    fun resolve(name: String): Color? {
        Log.d("DynamicColorUtil", "Search for $name in list of ${otherDynamicColors.keys.joinToString(", ")}")
        if(name in otherDynamicColors) return otherDynamicColors[name]

        val luminance = name.filter { it.isDigit() }.toIntOrNull() ?: return null
        if(luminance !in 0..100) return null

        val tones = when(name.filter { !it.isDigit() }) {
            "primary" -> primary
            "secondary" -> secondary
            "tertiary" -> tertiary
            "neutral" -> neutral
            "neutralVariant" -> neutralVariant
            "error" -> error
            else -> null
        } ?: return null

        return lookupTones(luminance, tones)
    }
}

private fun buildTonesMap(context: Context?, entries: List<ColEnums.Cols>): Tones = entries.associate {
    it.level to it.resolve(context)
}.toMutableMap()

private fun dynamicTonalPaletteInternal(context: Context?): TonalPalette =
    TonalPalette(
        primary = buildTonesMap(context, ColEnums.Primary.entries),
        secondary = buildTonesMap(context, ColEnums.Secondary.entries),
        tertiary = buildTonesMap(context, ColEnums.Tertiary.entries),
        neutral = buildTonesMap(context, ColEnums.Neutral.entries),
        neutralVariant = buildTonesMap(context, ColEnums.NeutralVariant.entries),
        error = buildTonesMap(context, ColEnums.Error.entries),
        otherDynamicColors = ColEnums.DynamicColor.entries.associate {
            val color = if(context == null) {
                Color(it.default)
            } else try {
                if(Build.VERSION.SDK_INT < it.apiLevel) throw Resources.NotFoundException()
                ColorResourceHelper.getColor(context, it.id)
            } catch(e: Resources.NotFoundException) {
                it.default31.resolve(context)
            }

            it.colName to color
        }
    )

val GenericPalette = dynamicTonalPaletteInternal(null)

fun dynamicTonalPalette(context: Context): TonalPalette =
    try {
        dynamicTonalPaletteInternal(context)
    }catch(e: Exception) {
        throwIfDebug(e)
        GenericPalette
    }

private object ColorResourceHelper {
    @DoNotInline
    fun getColor(context: Context, @ColorRes id: Int): Color {
        return Color(context.resources.getColor(id, context.theme))
    }
}

/**
 * Set the luminance(tone) of this color. Chroma may decrease because chroma has a different maximum
 * for any given hue and luminance.
 *
 * @param newLuminance 0 <= newLuminance <= 100; invalid values are corrected.
 */
internal fun Color.setLuminance(@FloatRange(from = 0.0, to = 100.0) newLuminance: Float): Color {
    if ((newLuminance < 0.0001) or (newLuminance > 99.9999)) {
        // aRGBFromLstar() from monet ColorUtil.java
        val y = 100 * labInvf((newLuminance + 16) / 116)
        val component = delinearized(y)
        return Color(
            /* red = */ component,
            /* green = */ component,
            /* blue = */ component,
        )
    }

    val sLAB = this.convert(ColorSpaces.CieLab)
    return Color(
        /* luminance = */ newLuminance,
        /* a = */ sLAB.component2(),
        /* b = */ sLAB.component3(),
        colorSpace = ColorSpaces.CieLab
    )
        .convert(ColorSpaces.Srgb)
}
/** Helper method from monet ColorUtils.java */
private fun labInvf(ft: Float): Float {
    val e = 216f / 24389f
    val kappa = 24389f / 27f
    val ft3 = ft * ft * ft
    return if (ft3 > e) {
        ft3
    } else {
        (116 * ft - 16) / kappa
    }
}

/**
 * Helper method from monet ColorUtils.java
 *
 * Delinearizes an RGB component.
 *
 * @param rgbComponent 0.0 <= rgb_component <= 100.0, represents linear R/G/B channel
 * @return 0 <= output <= 255, color channel converted to regular RGB space
 */
private fun delinearized(rgbComponent: Float): Int {
    val normalized = rgbComponent / 100
    val delinearized =
        if (normalized <= 0.0031308) {
            normalized * 12.92
        } else {
            1.055 * normalized.toDouble().pow(1.0 / 2.4) - 0.055
        }
    return MathUtils.clamp((delinearized * 255.0).roundToInt(), 0, 255)
}


// Defines all obtainable material3 system colors for API 31+
private object ColEnums {
    interface Cols {
        val level: Int
        val id: Int
        val default: Long
        val apiLevel: Int

        val reluminate: Int?

        fun resolve(context: Context?): Color {
            var color = if(context == null) {
                Color(default)
            } else try {
                if(Build.VERSION.SDK_INT < apiLevel) throw Resources.NotFoundException()
                ColorResourceHelper.getColor(context, id)
            } catch(e: Resources.NotFoundException) {
                Color(default)
            }

            reluminate?.let {
                color = color.setLuminance(it.toFloat())
            }

            return color
        }
    }

    enum class Primary(override val level: Int, override val id: Int, override val default: Long, override val reluminate: Int? = null, override val apiLevel: Int = Build.VERSION_CODES.S): Cols {
        Primary100(100, android.R.color.system_accent1_0, 0xFFFFFFFF),
        Primary99(99, android.R.color.system_accent1_10, 0xFFFEFBFF),
        Primary95(95, android.R.color.system_accent1_50, 0xFFEEF0FF),
        Primary90(90, android.R.color.system_accent1_100, 0xFFD9E2FF),
        Primary80(80, android.R.color.system_accent1_200, 0xFFB0C6FF),
        Primary70(70, android.R.color.system_accent1_300, 0xFF94AAE4),
        Primary60(60, android.R.color.system_accent1_400, 0xFF7A90C8),
        Primary50(50, android.R.color.system_accent1_500, 0xFF6076AC),
        Primary40(40, android.R.color.system_accent1_600, 0xFF475D92),
        Primary30(30, android.R.color.system_accent1_700, 0xFF2F4578),
        Primary20(20, android.R.color.system_accent1_800, 0xFF152E60),
        Primary10(10, android.R.color.system_accent1_900, 0xFF001945),
        Primary0(0, android.R.color.system_accent1_1000, 0xFF000000),
    }

    enum class Secondary(override val level: Int, override val id: Int, override val default: Long, override val reluminate: Int? = null, override val apiLevel: Int = Build.VERSION_CODES.S): Cols {
        Secondary100(100, android.R.color.system_accent2_0, 0xFFFFFFFF),
        Secondary99(99, android.R.color.system_accent2_10, 0xFFFEFBFF),
        Secondary95(95, android.R.color.system_accent2_50, 0xFFEEF0FF),
        Secondary90(90, android.R.color.system_accent2_100, 0xFFDCE2F9),
        Secondary80(80, android.R.color.system_accent2_200, 0xFFC0C6DC),
        Secondary70(70, android.R.color.system_accent2_300, 0xFFA4ABC1),
        Secondary60(60, android.R.color.system_accent2_400, 0xFF8A90A5),
        Secondary50(50, android.R.color.system_accent2_500, 0xFF70778B),
        Secondary40(40, android.R.color.system_accent2_600, 0xFF575E71),
        Secondary30(30, android.R.color.system_accent2_700, 0xFF404659),
        Secondary20(20, android.R.color.system_accent2_800, 0xFF2A3042),
        Secondary10(10, android.R.color.system_accent2_900, 0xFF151B2C),
        Secondary0(0, android.R.color.system_accent2_1000, 0xFF000000),
    }

    enum class Tertiary(override val level: Int, override val id: Int, override val default: Long, override val reluminate: Int? = null, override val apiLevel: Int = Build.VERSION_CODES.S): Cols {
        Tertiary100(100, android.R.color.system_accent3_0, 0xFFFFFFFF),
        Tertiary99(99, android.R.color.system_accent3_10, 0xFFFFFBFF),
        Tertiary95(95, android.R.color.system_accent3_50, 0xFFFFEBFA),
        Tertiary90(90, android.R.color.system_accent3_100, 0xFFFDD7FA),
        Tertiary80(80, android.R.color.system_accent3_200, 0xFFE0BBDD),
        Tertiary70(70, android.R.color.system_accent3_300, 0xFFC3A0C1),
        Tertiary60(60, android.R.color.system_accent3_400, 0xFFA886A6),
        Tertiary50(50, android.R.color.system_accent3_500, 0xFF8C6D8C),
        Tertiary40(40, android.R.color.system_accent3_600, 0xFF725572),
        Tertiary30(30, android.R.color.system_accent3_700, 0xFF593D59),
        Tertiary20(20, android.R.color.system_accent3_800, 0xFF412742),
        Tertiary10(10, android.R.color.system_accent3_900, 0xFF2A122C),
        Tertiary0(0, android.R.color.system_accent3_1000, 0xFF000000),
    }

    enum class Neutral(override val level: Int, override val id: Int, override val default: Long, override val reluminate: Int? = null, override val apiLevel: Int = Build.VERSION_CODES.S): Cols {
        Neutral100(100, android.R.color.system_neutral1_0, 0xFFFFFFFF),
        Neutral99(99, android.R.color.system_neutral1_10, 0xFFFEFBFF),
        Neutral95(95, android.R.color.system_neutral1_50, 0xFFF1F0F7),
        Neutral90(90, android.R.color.system_neutral1_100, 0xFFE2E2E9),
        Neutral80(80, android.R.color.system_neutral1_200, 0xFFC6C6CD),
        Neutral70(70, android.R.color.system_neutral1_300, 0xFFABABB1),
        Neutral60(60, android.R.color.system_neutral1_400, 0xFF909097),
        Neutral50(50, android.R.color.system_neutral1_500, 0xFF76777D),
        Neutral40(40, android.R.color.system_neutral1_600, 0xFF5D5E64),
        Neutral30(30, android.R.color.system_neutral1_700, 0xFF45464C),
        Neutral20(20, android.R.color.system_neutral1_800, 0xFF2F3036),
        Neutral10(10, android.R.color.system_neutral1_900, 0xFF1A1B20),
        Neutral0(0, android.R.color.system_neutral1_1000, 0xFF000000),
    }

    enum class NeutralVariant(override val level: Int, override val id: Int, override val default: Long, override val reluminate: Int? = null, override val apiLevel: Int = Build.VERSION_CODES.S): Cols {
        NeutralVariant100(100, android.R.color.system_neutral2_0, 0xFFFFFFFF),
        NeutralVariant99(99, android.R.color.system_neutral2_10, 0xFFFEFBFF),
        NeutralVariant95(95, android.R.color.system_neutral2_50, 0xFFF0F0FA),
        NeutralVariant90(90, android.R.color.system_neutral2_100, 0xFFE1E2EC),
        NeutralVariant80(80, android.R.color.system_neutral2_200, 0xFFC5C6D0),
        NeutralVariant70(70, android.R.color.system_neutral2_300, 0xFFA9ABB4),
        NeutralVariant60(60, android.R.color.system_neutral2_400, 0xFF8F9099),
        NeutralVariant50(50, android.R.color.system_neutral2_500, 0xFF757780),
        NeutralVariant40(40, android.R.color.system_neutral2_600, 0xFF5C5E67),
        NeutralVariant30(30, android.R.color.system_neutral2_700, 0xFF44464F),
        NeutralVariant20(20, android.R.color.system_neutral2_800, 0xFF2E3038),
        NeutralVariant10(10, android.R.color.system_neutral2_900, 0xFF191B23),
        NeutralVariant0(0, android.R.color.system_neutral2_1000, 0xFF000000),

        NeutralVariant98(98, android.R.color.system_neutral2_50, 0xFFF0F0FA, 98),
        NeutralVariant96(96, android.R.color.system_neutral2_50, 0xFFF0F0FA, 96),
        NeutralVariant94(94, android.R.color.system_neutral2_100, 0xFFE1E2EC, 94),
        NeutralVariant92(92, android.R.color.system_neutral2_100, 0xFFE1E2EC, 92),
        NeutralVariant87(87, android.R.color.system_neutral2_200, 0xFFC5C6D0, 87),

        NeutralVariant24(24, android.R.color.system_neutral2_700, 0xFF44464F, 24),
        NeutralVariant22(22, android.R.color.system_neutral2_700, 0xFF44464F, 22),
        NeutralVariant17(17, android.R.color.system_neutral2_800, 0xFF2E3038, 17),
        NeutralVariant12(12, android.R.color.system_neutral2_800, 0xFF2E3038, 12),
        NeutralVariant6(6, android.R.color.system_neutral2_900, 0xFF191B23, 6),
        NeutralVariant4(4, android.R.color.system_neutral2_900, 0xFF191B23, 4),
    }

    enum class Error(override val level: Int, override val id: Int, override val default: Long, override val reluminate: Int? = null, override val apiLevel: Int = Build.VERSION_CODES.VANILLA_ICE_CREAM): Cols {
        Error100(100, android.R.color.system_error_0, 0xFFffffff),
        Error99(99, android.R.color.system_error_10, 0xFFFFFBF9),
        Error95(95, android.R.color.system_error_50, 0xFFFCEEEE),
        Error90(90, android.R.color.system_error_100, 0xFFF9DEDC),
        Error80(80, android.R.color.system_error_200, 0xFFF2B8B5),
        Error70(70, android.R.color.system_error_300, 0xFFEC928E),
        Error60(60, android.R.color.system_error_400, 0xFFE46962),
        Error50(50, android.R.color.system_error_500, 0xFFDC362E),
        Error40(40, android.R.color.system_error_600, 0xFFB3261E),
        Error30(30, android.R.color.system_error_700, 0xFF8C1D18),
        Error20(20, android.R.color.system_error_800, 0xFF601410),
        Error10(10, android.R.color.system_error_900, 0xFF410E0B),
        Error0(0, android.R.color.system_error_1000, 0xFF000000),
    }

    enum class DynamicColor(val colName: String, val id: Int, val default31: Cols, val default: Long, val apiLevel: Int = Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PrimaryContainerLight("primary_container_light", android.R.color.system_primary_container_light,
            Primary.Primary90, 0xFF5E73A9),
        OnPrimaryContainerLight("on_primary_container_light", android.R.color.system_on_primary_container_light,
            Primary.Primary10, 0xFFFFFFFF),
        PrimaryLight("primary_light", android.R.color.system_primary_light,
            Primary.Primary40, 0xFF2A4174),
        OnPrimaryLight("on_primary_light", android.R.color.system_on_primary_light,
            Primary.Primary100, 0xFFFFFFFF),
        SecondaryContainerLight("secondary_container_light", android.R.color.system_secondary_container_light,
            Secondary.Secondary90, 0xFF6E7488),
        OnSecondaryContainerLight("on_secondary_container_light", android.R.color.system_on_secondary_container_light,
            Secondary.Secondary10, 0xFFFFFFFF),
        SecondaryLight("secondary_light", android.R.color.system_secondary_light,
            Secondary.Secondary40, 0xFF3C4255),
        OnSecondaryLight("on_secondary_light", android.R.color.system_on_secondary_light,
            Secondary.Secondary100, 0xFFFFFFFF),
        TertiaryContainerLight("tertiary_container_light", android.R.color.system_tertiary_container_light,
            Tertiary.Tertiary90, 0xFF8A6A89),
        OnTertiaryContainerLight("on_tertiary_container_light", android.R.color.system_on_tertiary_container_light,
            Tertiary.Tertiary10, 0xFFFFFFFF),
        TertiaryLight("tertiary_light", android.R.color.system_tertiary_light,
            Tertiary.Tertiary40, 0xFF553A55),
        OnTertiaryLight("on_tertiary_light", android.R.color.system_on_tertiary_light,
            Tertiary.Tertiary100, 0xFFFFFFFF),
        BackgroundLight("background_light", android.R.color.system_background_light,
            NeutralVariant.NeutralVariant98, 0xFFFAF8FF),
        OnBackgroundLight("on_background_light", android.R.color.system_on_background_light,
            NeutralVariant.NeutralVariant10, 0xFF1A1B20),
        SurfaceLight("surface_light", android.R.color.system_surface_light,
            NeutralVariant.NeutralVariant98, 0xFFFAF8FF),
        OnSurfaceLight("on_surface_light", android.R.color.system_on_surface_light,
            NeutralVariant.NeutralVariant10, 0xFF1A1B20),
        SurfaceContainerLowLight("surface_container_low_light", android.R.color.system_surface_container_low_light,
            NeutralVariant.NeutralVariant96, 0xFFF4F3FA),
        SurfaceContainerLowestLight("surface_container_lowest_light", android.R.color.system_surface_container_lowest_light,
            NeutralVariant.NeutralVariant100, 0xFFFFFFFF),
        SurfaceContainerLight("surface_container_light", android.R.color.system_surface_container_light,
            NeutralVariant.NeutralVariant94, 0xFFEEEDF4),
        SurfaceContainerHighLight("surface_container_high_light", android.R.color.system_surface_container_high_light,
            NeutralVariant.NeutralVariant92, 0xFFE8E7EF),
        SurfaceContainerHighestLight("surface_container_highest_light", android.R.color.system_surface_container_highest_light,
            NeutralVariant.NeutralVariant90, 0xFFE2E2E9),
        SurfaceBrightLight("surface_bright_light", android.R.color.system_surface_bright_light,
            NeutralVariant.NeutralVariant98, 0xFFFAF8FF),
        SurfaceDimLight("surface_dim_light", android.R.color.system_surface_dim_light,
            NeutralVariant.NeutralVariant87, 0xFFDAD9E0),
        SurfaceVariantLight("surface_variant_light", android.R.color.system_surface_variant_light,
            NeutralVariant.NeutralVariant90, 0xFFE1E2EC),
        OnSurfaceVariantLight("on_surface_variant_light", android.R.color.system_on_surface_variant_light,
            NeutralVariant.NeutralVariant30, 0xFF40434B),
        OutlineLight("outline_light", android.R.color.system_outline_light,
            NeutralVariant.NeutralVariant50, 0xFF5D5F67),
        OutlineVariantLight("outline_variant_light", android.R.color.system_outline_variant_light,
            NeutralVariant.NeutralVariant80, 0xFF797A83),
        ErrorLight("error_light", android.R.color.system_error_light,
            Error.Error40, 0xFF8C0009),
        OnErrorLight("on_error_light", android.R.color.system_on_error_light,
            Error.Error100, 0xFFFFFFFF),
        ErrorContainerLight("error_container_light", android.R.color.system_error_container_light,
            Error.Error90, 0xFFDA342E),
        OnErrorContainerLight("on_error_container_light", android.R.color.system_on_error_container_light,
            Error.Error10, 0xFFFFFFFF),


        PrimaryContainerDark("primary_container_dark", android.R.color.system_primary_container_dark,
            Primary.Primary30, 0xFF7A90C8),
        OnPrimaryContainerDark("on_primary_container_dark", android.R.color.system_on_primary_container_dark,
            Primary.Primary90, 0xFF000000),
        PrimaryDark("primary_dark", android.R.color.system_primary_dark,
            Primary.Primary80, 0xFFB7CAFF),
        OnPrimaryDark("on_primary_dark", android.R.color.system_on_primary_dark,
            Primary.Primary20, 0xFF00143B),
        SecondaryContainerDark("secondary_container_dark", android.R.color.system_secondary_container_dark,
            Secondary.Secondary30, 0xFF8A90A5),
        OnSecondaryContainerDark("on_secondary_container_dark", android.R.color.system_on_secondary_container_dark,
            Secondary.Secondary90, 0xFF000000),
        SecondaryDark("secondary_dark", android.R.color.system_secondary_dark,
            Secondary.Secondary80, 0xFFC4CAE1),
        OnSecondaryDark("on_secondary_dark", android.R.color.system_on_secondary_dark,
            Secondary.Secondary20, 0xFF0F1626),
        TertiaryContainerDark("tertiary_container_dark", android.R.color.system_tertiary_container_dark,
            Tertiary.Tertiary30, 0xFFA886A6),
        OnTertiaryContainerDark("on_tertiary_container_dark", android.R.color.system_on_tertiary_container_dark,
            Tertiary.Tertiary90, 0xFF000000),
        TertiaryDark("tertiary_dark", android.R.color.system_tertiary_dark,
            Tertiary.Tertiary80, 0xFFE4BFE2),
        OnTertiaryDark("on_tertiary_dark", android.R.color.system_on_tertiary_dark,
            Tertiary.Tertiary20, 0xFF240D26),
        BackgroundDark("background_dark", android.R.color.system_background_dark,
            NeutralVariant.NeutralVariant6, 0xFF121318),
        OnBackgroundDark("on_background_dark", android.R.color.system_on_background_dark,
            NeutralVariant.NeutralVariant90, 0xFFE2E2E9),
        SurfaceDark("surface_dark", android.R.color.system_surface_dark,
            NeutralVariant.NeutralVariant6, 0xFF121318),
        OnSurfaceDark("on_surface_dark", android.R.color.system_on_surface_dark,
            NeutralVariant.NeutralVariant90, 0xFFFCFAFF),
        SurfaceContainerLowDark("surface_container_low_dark", android.R.color.system_surface_container_low_dark,
            NeutralVariant.NeutralVariant10, 0xFF1A1B20),
        SurfaceContainerLowestDark("surface_container_lowest_dark", android.R.color.system_surface_container_lowest_dark,
            NeutralVariant.NeutralVariant4, 0xFF0C0E13),
        SurfaceContainerDark("surface_container_dark", android.R.color.system_surface_container_dark,
            NeutralVariant.NeutralVariant12, 0xFF1E1F25),
        SurfaceContainerHighDark("surface_container_high_dark", android.R.color.system_surface_container_high_dark,
            NeutralVariant.NeutralVariant17, 0xFF282A2F),
        SurfaceContainerHighestDark("surface_container_highest_dark", android.R.color.system_surface_container_highest_dark,
            NeutralVariant.NeutralVariant22, 0xFF33343A),
        SurfaceBrightDark("surface_bright_dark", android.R.color.system_surface_bright_dark,
            NeutralVariant.NeutralVariant24, 0xFF38393F),
        SurfaceDimDark("surface_dim_dark", android.R.color.system_surface_dim_dark,
            NeutralVariant.NeutralVariant6, 0xFF121318),
        SurfaceVariantDark("surface_variant_dark", android.R.color.system_surface_variant_dark,
            NeutralVariant.NeutralVariant30, 0xFF44464F),
        OnSurfaceVariantDark("on_surface_variant_dark", android.R.color.system_on_surface_variant_dark,
            NeutralVariant.NeutralVariant80, 0xFFC9CAD4),
        OutlineDark("outline_dark", android.R.color.system_outline_dark,
            NeutralVariant.NeutralVariant60, 0xFFA1A2AC),
        OutlineVariantDark("outline_variant_dark", android.R.color.system_outline_variant_dark,
            NeutralVariant.NeutralVariant30, 0xFF81838C),
        ErrorDark("error_dark", android.R.color.system_error_dark,
            Error.Error80, 0xFFFFBAB1),
        OnErrorDark("on_error_dark", android.R.color.system_on_error_dark,
            Error.Error20, 0xFF370001),
        ErrorContainerDark("error_container_dark", android.R.color.system_error_container_dark,
            Error.Error30, 0xFFFF5449),
        OnErrorContainerDark("on_error_container_dark", android.R.color.system_on_error_container_dark,
            Error.Error90, 0xFF000000),
    }
}
