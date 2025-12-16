package org.futo.inputmethod.latin.uix

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.internal.KeyDrawParams
import org.futo.inputmethod.latin.uix.theme.KeyBackground
import org.futo.inputmethod.latin.uix.theme.KeyDrawingConfiguration
import org.futo.inputmethod.v2keyboard.Direction
import org.futo.inputmethod.v2keyboard.KeyVisualStyle

/** Visual style descriptor for a key */
data class VisualStyleDescriptor(
    /** Key background drawable when not pressed */
    val backgroundDrawable: Drawable?,

    /** Key foreground label/icon color when not pressed */
    @ColorInt
    val foregroundColor: Int,

    /** Key background drawable when pressed */
    val backgroundDrawablePressed: Drawable? = backgroundDrawable,

    /** Key foreground label/icon color when pressed */
    @ColorInt
    val foregroundColorPressed: Int = foregroundColor,

    /** Key background drawable when flicking */
    val backgroundDrawableFlicking: Map<Direction?, Drawable?>? = null
)

interface DynamicThemeProvider {
    val keyBorders: Boolean

    val keyboardColor: Int
    val keyColor: Int

    val keyboardBackground: Drawable
    val keyBackground: Drawable

    val moreKeysTextColor: Int
    val moreKeysKeyboardBackground: Drawable

    val displayDpi: Int

    val hintColor: Int?
    val hintHiVis: Boolean

    @ColorInt
    fun getColor(i: Int): Int?

    fun getDrawable(i: Int): Drawable?

    fun getIcon(iconName: String): Drawable?

    fun getKeyStyleDescriptor(visualStyle: KeyVisualStyle): VisualStyleDescriptor

    var typefaceOverride: Typeface?
    val themeTypeface: Typeface?
    fun selectKeyTypeface(defaultTypeface: Typeface): Typeface =
        typefaceOverride ?: themeTypeface ?: defaultTypeface

    fun selectKeyDrawingConfiguration(keyboard: Keyboard?, params: KeyDrawParams, key: Key): KeyDrawingConfiguration
    fun getPreviewBackground(keyboard: Keyboard?, key: Key): KeyBackground

    companion object {
        @ColorInt
        fun getColorOrDefault(i: Int, @ColorInt default: Int, keyAttr: TypedArray, provider: DynamicThemeProvider?): Int {
            return (provider?.getColor(i)) ?: keyAttr.getColor(i, default)
        }

        fun getDrawableOrDefault(i: Int, keyAttr: TypedArray, provider: DynamicThemeProvider?): Drawable? {
            return (provider?.getDrawable(i)) ?: keyAttr.getDrawable(i)
        }

        @JvmStatic
        fun obtainFromContext(context: Context): DynamicThemeProvider {
            if (context is DynamicThemeProviderOwner) {
                return context.getDrawableProvider()
            } else if (context is ContextThemeWrapper) {
                val baseContext = context.baseContext
                if (baseContext is DynamicThemeProviderOwner) {
                    return baseContext.getDrawableProvider()
                }
            }

            throw IllegalArgumentException("Could not find DynamicThemeProviderOwner")
        }
    }

    val actionBarColor: Color
}