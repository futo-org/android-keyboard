package org.futo.inputmethod.latin.uix

import android.content.Context
import android.graphics.Color
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.material.color.DynamicColors
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import kotlin.math.roundToInt

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

    private fun StateListDrawable.addStateWithHighlightLayerOnPressed(@ColorInt highlight: Int, stateSet: IntArray, drawable: Drawable) {
        addState(intArrayOf(android.R.attr.state_pressed) + stateSet, LayerDrawable(
            arrayOf(
                drawable,
                coloredRoundedRectangle(highlight, dp(8.dp))
            )
        )
        )
        addState(stateSet, drawable)
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


        val primary = colorScheme.primary.toArgb()
        val secondary = colorScheme.secondary.toArgb()
        val highlight = colorScheme.outline.copy(alpha = 0.33f).toArgb()

        val background = colorScheme.surface.toArgb()
        val surface = colorScheme.background.toArgb()
        val outline = colorScheme.outline.toArgb()

        val primaryContainer = colorScheme.primaryContainer.toArgb()
        val onPrimaryContainer = colorScheme.onPrimaryContainer.toArgb()

        val onPrimary = colorScheme.onPrimary.toArgb()
        val onSecondary = colorScheme.onSecondary.toArgb()
        val onBackground = colorScheme.onBackground.toArgb()
        val onBackgroundHalf = colorScheme.onBackground.copy(alpha = 0.5f).toArgb()

        val transparent = Color.TRANSPARENT

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

        val overrideDrawable: (Int, Int, Int) -> Unit = { a, b, color ->
            drawables[a] = AppCompatResources.getDrawable(
                context,
                b
            )!!.apply {
                setTint(color)
            }
        }

        // No good replacements for these cons yet, but we set them anyway for setTint
        overrideDrawable(R.styleable.Keyboard_iconEnterKey, R.drawable.sym_keyboard_return_lxx_light, onPrimary)
        overrideDrawable(R.styleable.Keyboard_iconGoKey, R.drawable.sym_keyboard_go_lxx_light, onPrimary)
        overrideDrawable(R.styleable.Keyboard_iconNextKey, R.drawable.sym_keyboard_next_lxx_light, onPrimary)
        overrideDrawable(R.styleable.Keyboard_iconDoneKey, R.drawable.sym_keyboard_done_lxx_light, onPrimary)
        overrideDrawable(R.styleable.Keyboard_iconPreviousKey, R.drawable.sym_keyboard_previous_lxx_light, onPrimary)
        overrideDrawable(R.styleable.Keyboard_iconSearchKey, R.drawable.sym_keyboard_search_lxx_light, onPrimary)

        overrideDrawable(R.styleable.Keyboard_iconDeleteKey, R.drawable.delete, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconSettingsKey, R.drawable.settings, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconEmojiActionKey, R.drawable.smile, onPrimary)
        overrideDrawable(R.styleable.Keyboard_iconEmojiNormalKey, R.drawable.smile, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconLanguageSwitchKey, R.drawable.globe, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconShiftKey, R.drawable.shift, onBackground)
        overrideDrawable(R.styleable.Keyboard_iconShiftKeyShifted, R.drawable.shiftshifted, onBackground)

        primaryKeyboardColor = background

        keyboardBackground = coloredRectangle(background)

        keyBackground = StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_active),
                coloredRoundedRectangle(primary, dp(8.dp)).apply {
                    setSize(dp(64.dp).toInt(), dp(48.dp).toInt())
                }
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable, android.R.attr.state_checked),
                coloredRoundedRectangle(colorScheme.secondaryContainer.toArgb(), dp(8.dp))
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable),
                coloredRectangle(transparent)
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_empty),
                coloredRectangle(transparent)
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(),
                coloredRectangle(transparent)
            )
        }

        spaceBarBackground = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),
                LayerDrawable(
                    arrayOf(
                        coloredRoundedRectangle(highlight, dp(32.dp)),
                        coloredRoundedRectangle(highlight, dp(32.dp))
                    )
                )
            )
            addState(intArrayOf(),
                coloredRoundedRectangle(highlight, dp(32.dp))
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