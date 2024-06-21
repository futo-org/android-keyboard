package org.futo.inputmethod.latin.uix

import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

interface DynamicThemeProvider {
    val primaryKeyboardColor: Int

    val keyboardBackground: Drawable
    val keyBackground: Drawable
    val spaceBarBackground: Drawable

    val keyFeedback: Drawable

    val moreKeysTextColor: Int
    val moreKeysKeyboardBackground: Drawable
    val popupKey: Drawable
    val actionPopupKey: Drawable

    @ColorInt
    fun getColor(i: Int): Int?

    fun getDrawable(i: Int): Drawable?

    fun getIcon(iconName: String): Drawable?

    fun getKeyboardHeightMultiplier(): Float

    companion object {
        @ColorInt
        fun getColorOrDefault(i: Int, @ColorInt default: Int, keyAttr: TypedArray, provider: DynamicThemeProvider?): Int {
            return (provider?.getColor(i)) ?: keyAttr.getColor(i, default)
        }

        fun getDrawableOrDefault(i: Int, keyAttr: TypedArray, provider: DynamicThemeProvider?): Drawable? {
            return (provider?.getDrawable(i)) ?: keyAttr.getDrawable(i)
        }
    }
}