package org.futo.inputmethod.latin.uix

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import com.google.android.material.color.DynamicColors
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import kotlin.math.roundToInt

val KeyBordersSetting = SettingsKey(booleanPreferencesKey("keyBorders"), true)
val HiddenKeysSetting = SettingsKey(booleanPreferencesKey("hiddenKeys"), false)
val KeyHintsSetting   = SettingsKey(booleanPreferencesKey("keyHints"), false)

val KeyboardHeightMultiplierSetting = SettingsKey(floatPreferencesKey("keyboardHeightMultiplier"), 1.0f)
val KeyboardBottomOffsetSetting = SettingsKey(floatPreferencesKey("keyboardOffset"), 0.0f)

fun adjustColorBrightnessForContrast(bgColor: Int, fgColor: Int, desiredContrast: Float, adjustSaturation: Boolean = false): Int {
    // Convert RGB colors to HSL
    val bgHSL = FloatArray(3)
    ColorUtils.colorToHSL(bgColor, bgHSL)
    val fgHSL = FloatArray(3)
    ColorUtils.colorToHSL(fgColor, fgHSL)

    // Estimate the adjustment needed in lightness to achieve the desired contrast
    // This is a simplified approach and may not be perfectly accurate
    val lightnessAdjustment = (desiredContrast - 1) / 10.0f // Simplified and heuristic-based adjustment

    // Adjust the background color's lightness
    bgHSL[2] = bgHSL[2] + lightnessAdjustment
    bgHSL[2] = bgHSL[2].coerceIn(0f, 1f) // Ensure the lightness stays within valid range

    if(adjustSaturation) {
        bgHSL[1] = (bgHSL[1] + lightnessAdjustment).coerceIn(0f, 1f)
    }

    // Convert back to RGB and return the adjusted color
    return ColorUtils.HSLToColor(bgHSL)
}

class BasicThemeProvider(val context: Context, val overrideColorScheme: ColorScheme? = null) :
    DynamicThemeProvider {
    override val primaryKeyboardColor: Int

    override val keyboardBackground: Drawable
    override val keyBackground: Drawable
    override val spaceBarBackground: Drawable

    override val keyFeedback: Drawable

    override val moreKeysTextColor: Int
    override val moreKeysKeyboardBackground: Drawable
    override val popupKey: Drawable

    private val colors: HashMap<Int, Int> = HashMap()
    override fun getColor(i: Int): Int? {
        return colors[i]
    }


    private val drawables: HashMap<Int, Drawable> = HashMap()
    override fun getDrawable(i: Int): Drawable? {
        return drawables[i]
    }

    override fun getKeyboardHeightMultiplier(): Float {
        return keyboardHeight
    }

    override fun getKeyboardBottomOffset(): Float {
        return dp(keyboardBottomOffsetValue.dp)
    }

    private fun dp(dp: Dp): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.value,
            context.resources.displayMetrics
        );
    }

    private fun coloredRectangle(@ColorInt color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
        }
    }

    private fun coloredRoundedRectangle(@ColorInt color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    private fun coloredOval(@ColorInt color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            cornerRadius = Float.MAX_VALUE
            setColor(color)
        }
    }

    private fun StateListDrawable.addStateWithHighlightLayerOnPressed(@ColorInt highlight: Int, stateSet: IntArray, drawable: Drawable, cornerRadius: Dp = 8.dp) {
        addState(intArrayOf(android.R.attr.state_pressed) + stateSet, LayerDrawable(
            arrayOf(
                drawable,
                coloredRoundedRectangle(highlight, dp(cornerRadius))
            )
        )
        )
        addState(stateSet, drawable)
    }

    val expertMode: Boolean
    val keyBorders: Boolean
    val showKeyHints: Boolean

    val keyboardHeight: Float
    val keyboardBottomOffsetValue: Float

    fun hasUpdated(newPreferences: Preferences): Boolean {
        return when {
            newPreferences[HiddenKeysSetting.key] != expertMode -> true
            newPreferences[KeyBordersSetting.key] != keyBorders -> true
            newPreferences[KeyHintsSetting.key] != showKeyHints -> true

            newPreferences[KeyboardHeightMultiplierSetting.key] != keyboardHeight -> true
            newPreferences[KeyboardBottomOffsetSetting.key] != keyboardBottomOffsetValue -> true

            else -> false
        }
    }

    init {
        val colorScheme = if(overrideColorScheme != null) {
            overrideColorScheme
        }else if(!DynamicColors.isDynamicColorAvailable()) {
            DarkColorScheme
        } else {
            val dCtx = DynamicColors.wrapContextIfAvailable(context)

            dynamicLightColorScheme(dCtx)
        }

        expertMode = context.getSettingBlocking(HiddenKeysSetting)
        keyBorders = context.getSettingBlocking(KeyBordersSetting)
        showKeyHints = context.getSettingBlocking(KeyHintsSetting)

        keyboardHeight = context.getSettingBlocking(KeyboardHeightMultiplierSetting.key, KeyboardHeightMultiplierSetting.default)
        keyboardBottomOffsetValue = context.getSettingBlocking(KeyboardBottomOffsetSetting.key, KeyboardBottomOffsetSetting.default)

        val primary = colorScheme.primary.toArgb()
        val secondary = colorScheme.secondary.toArgb()
        val highlight = colorScheme.outline.copy(alpha = 0.33f).toArgb()

        val background = colorScheme.surface.toArgb()
        val surface = colorScheme.background.toArgb()
        val outline = colorScheme.outline.toArgb()

        val primaryContainer = colorScheme.primaryContainer.toArgb()
        val onPrimaryContainer = colorScheme.onPrimaryContainer.toArgb()

        val onPrimary = colorScheme.onPrimary.toArgb()
        val onPrimaryThird = colorScheme.onPrimary.copy(alpha = 0.33f).toArgb()
        val onSecondary = colorScheme.onSecondary.toArgb()
        val onBackground = colorScheme.onBackground.toArgb()
        val onBackgroundHalf = colorScheme.onBackground.copy(alpha = 0.5f).toArgb()
        val onBackgroundThird = colorScheme.onBackground.copy(alpha = 0.33f).toArgb()

        val transparent = Color.Transparent.toArgb()
        primaryKeyboardColor = if(keyBorders) {
            colorScheme.background.toArgb()
        } else {
            colorScheme.surface.toArgb()
        }

        val ratio = 1.5f
        val keyColor = if(keyBorders) {
            var c = adjustColorBrightnessForContrast(primaryKeyboardColor, primaryKeyboardColor, ratio)
            if(c == primaryKeyboardColor) {
                // May happen if the color is already 100% white
                c = adjustColorBrightnessForContrast(primaryKeyboardColor, primaryKeyboardColor, 1.0f / (ratio / 2.0f + 0.5f))
            }
            c
        } else {
            transparent
        }
        val functionalKeyColor = if(keyBorders) {
            adjustColorBrightnessForContrast(primaryKeyboardColor, primaryKeyboardColor, ratio / 2.0f + 0.5f, adjustSaturation = true)
        } else {
            transparent
        }

        val enterKeyBackground = if(expertMode) { functionalKeyColor } else { primary }
        val enterKeyForeground = if(expertMode) { onBackgroundThird } else { onPrimary }

        colors[R.styleable.Keyboard_Key_keyTextColor] = onBackground
        colors[R.styleable.Keyboard_Key_keyTextInactivatedColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyPressedTextColor] = onPrimary
        colors[R.styleable.Keyboard_Key_keyTextShadowColor] = 0
        colors[R.styleable.Keyboard_Key_functionalTextColor] = onBackground
        colors[R.styleable.Keyboard_Key_keyHintLetterColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyHintLabelColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyPreviewTextColor] = onSecondary
        colors[R.styleable.MainKeyboardView_languageOnSpacebarTextColor] = onBackgroundHalf
        colors[R.styleable.MainKeyboardView_gestureTrailColor] = primary

        val overrideDrawable: (Int, Int, Int) -> Unit = { a, b, color ->
            drawables[a] = AppCompatResources.getDrawable(
                context,
                b
            )!!.apply {
                setTint(color)
            }
        }

        // No good replacements for these icons yet, but we set them anyway for setTint
        overrideDrawable(R.styleable.Keyboard_iconEnterKey, R.drawable.sym_keyboard_return_lxx_light, enterKeyForeground)
        overrideDrawable(R.styleable.Keyboard_iconGoKey, R.drawable.sym_keyboard_go_lxx_light, enterKeyForeground)
        overrideDrawable(R.styleable.Keyboard_iconNextKey, R.drawable.sym_keyboard_next_lxx_light, enterKeyForeground)
        overrideDrawable(R.styleable.Keyboard_iconDoneKey, R.drawable.sym_keyboard_done_lxx_light, enterKeyForeground)
        overrideDrawable(R.styleable.Keyboard_iconPreviousKey, R.drawable.sym_keyboard_previous_lxx_light, enterKeyForeground)
        overrideDrawable(R.styleable.Keyboard_iconSearchKey, R.drawable.sym_keyboard_search_lxx_light, enterKeyForeground)

        overrideDrawable(R.styleable.Keyboard_iconDeleteKey, R.drawable.delete, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconSettingsKey, R.drawable.settings, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconEmojiActionKey, R.drawable.smile, onPrimary)
        overrideDrawable(R.styleable.Keyboard_iconEmojiNormalKey, R.drawable.smile, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconLanguageSwitchKey, R.drawable.globe, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconShiftKey, R.drawable.shift, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconShiftKeyShifted, R.drawable.shiftshifted, onBackground)

        if(!showKeyHints) {
            colors[R.styleable.Keyboard_Key_keyHintLetterColor] = transparent
            colors[R.styleable.Keyboard_Key_keyHintLabelColor] = transparent
        }

        if(expertMode) {
            colors[R.styleable.Keyboard_Key_keyTextColor] = transparent
            colors[R.styleable.Keyboard_Key_keyTextInactivatedColor] = transparent
            colors[R.styleable.Keyboard_Key_keyHintLetterColor] = transparent
            colors[R.styleable.Keyboard_Key_keyHintLabelColor] = transparent


            // Note: We don't fully hide some things, but fade them away as they may be important landmarks
            colors[R.styleable.Keyboard_Key_functionalTextColor] = onBackgroundThird
            overrideDrawable(R.styleable.Keyboard_iconShiftKey, R.drawable.shift, onBackgroundThird)
            overrideDrawable(R.styleable.Keyboard_iconShiftKeyShifted, R.drawable.shiftshifted, onBackgroundThird)
            overrideDrawable(R.styleable.Keyboard_iconDeleteKey, R.drawable.delete, onBackgroundThird)
            overrideDrawable(R.styleable.Keyboard_iconEmojiNormalKey, R.drawable.smile, transparent)
        }

        keyboardBackground = coloredRectangle(primaryKeyboardColor)

        keyBackground = StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_active),
                coloredRoundedRectangle(enterKeyBackground, dp(128.dp)),
                cornerRadius = 128.dp
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable, android.R.attr.state_checked),
                coloredRoundedRectangle(colorScheme.secondaryContainer.toArgb(), dp(8.dp))
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable),
                if(keyBorders) {
                    coloredRoundedRectangle(keyColor, dp(8.dp))
                } else {
                    coloredRectangle(transparent)
                }
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_first),
                if(keyBorders) {
                    coloredRoundedRectangle(functionalKeyColor, dp(8.dp))
                } else {
                    coloredRectangle(transparent)
                }
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_empty),
                if(keyBorders) {
                    coloredRoundedRectangle(keyColor, dp(8.dp))
                } else {
                    coloredRectangle(transparent)
                }
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(),
                if(keyBorders) {
                    coloredRoundedRectangle(keyColor, dp(8.dp))
                } else {
                    coloredRectangle(transparent)
                }
            )
        }

        val spaceCornerRadius = if(keyBorders) {
            8.dp
        } else {
            48.dp
        }

        val spaceDrawable = if(keyBorders) {
            coloredRoundedRectangle(keyColor, dp(spaceCornerRadius))
        } else if(expertMode) {
            coloredRoundedRectangle(colorScheme.outline.copy(alpha = 0.1f).toArgb(), dp(spaceCornerRadius))
        } else {
            coloredRoundedRectangle(highlight, dp(spaceCornerRadius))
        }

        spaceBarBackground = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),
                LayerDrawable(
                    arrayOf(
                        spaceDrawable,
                        coloredRoundedRectangle(highlight, dp(spaceCornerRadius))
                    )
                )
            )
            addState(intArrayOf(),
                spaceDrawable
            )
        }

        keyFeedback = ShapeDrawable().apply {
            paint.color = secondary
            shape = RoundRectShape(
                floatArrayOf(
                    dp(8.dp), dp(8.dp), dp(8.dp), dp(8.dp),
                    dp(8.dp), dp(8.dp), dp(8.dp), dp(8.dp),
                ), null, null
            )

            intrinsicWidth = dp(48.dp).roundToInt()
            intrinsicHeight = dp(24.dp).roundToInt()

            setPadding(0, 0, 0, dp(50.dp).roundToInt())
        }

        moreKeysTextColor = onPrimaryContainer
        moreKeysKeyboardBackground = coloredRoundedRectangle(primaryContainer, dp(8.dp))
        popupKey = StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(primary, intArrayOf(),
                coloredRoundedRectangle(primaryContainer, dp(8.dp))
            )
        }
    }

}