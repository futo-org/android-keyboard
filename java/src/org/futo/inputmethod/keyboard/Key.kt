/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.futo.inputmethod.keyboard

import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import androidx.collection.MutableIntIntMap
import androidx.collection.mutableIntIntMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.futo.inputmethod.keyboard.internal.KeyDrawParams
import org.futo.inputmethod.keyboard.internal.KeySpecParser
import org.futo.inputmethod.keyboard.internal.KeyVisualAttributes
import org.futo.inputmethod.keyboard.internal.KeyboardIconsSet
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.keyboard.internal.MoreKeySpec
import org.futo.inputmethod.keyboard.internal.MoreKeySpec.LettersOnBaseLayout
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.common.StringUtils
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.v2keyboard.Direction
import org.futo.inputmethod.v2keyboard.KeyVisualStyle
import org.futo.inputmethod.v2keyboard.computeDirectionsFromDeltaPos
import kotlin.math.roundToInt
import kotlin.math.sqrt


data object KeyConsts {
    const val LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM: Int = 0x02
    const val LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM: Int = 0x04
    const val LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER: Int = 0x08

    // Font typeface specification.
    const val LABEL_FLAGS_FONT_MASK: Int = 0x30
    const val LABEL_FLAGS_FONT_NORMAL: Int = 0x10
    const val LABEL_FLAGS_FONT_MONO_SPACE: Int = 0x20
    const val LABEL_FLAGS_FONT_DEFAULT: Int = 0x30

    // Start of key text ratio enum values
    const val LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK: Int = 0x1C0
    const val LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO: Int = 0x40
    const val LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO: Int = 0x80
    const val LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO: Int = 0xC0
    const val LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO: Int = 0x140

    // End of key text ratio mask enum values
    const val LABEL_FLAGS_HAS_POPUP_HINT: Int = 0x200
    const val LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT: Int = 0x400
    const val LABEL_FLAGS_HAS_HINT_LABEL: Int = 0x800

    // The bit to calculate the ratio of key label width against key width. If autoXScale bit is on
    // and autoYScale bit is off, the key label may be shrunk only for X-direction.
    // If both autoXScale and autoYScale bits are on, the key label text size may be auto scaled.
    const val LABEL_FLAGS_AUTO_X_SCALE: Int = 0x4000
    const val LABEL_FLAGS_AUTO_Y_SCALE: Int = 0x8000
    const val LABEL_FLAGS_AUTO_SCALE: Int = (LABEL_FLAGS_AUTO_X_SCALE
            or LABEL_FLAGS_AUTO_Y_SCALE)
    const val LABEL_FLAGS_PRESERVE_CASE: Int = 0x10000
    const val LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED: Int = 0x20000
    const val LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL: Int = 0x40000
    const val LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR: Int = 0x80000
    const val LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO: Int = 0x100000
    const val LABEL_FLAGS_DISABLE_HINT_LABEL: Int = 0x40000000
    const val LABEL_FLAGS_DISABLE_ADDITIONAL_MORE_KEYS: Int = -0x80000000

    const val MORE_KEYS_COLUMN_NUMBER_MASK: Int = 0x000000ff

    // If this flag is specified, more keys keyboard should have the specified number of columns.
    // Otherwise more keys keyboard should have less than or equal to the specified maximum number
    // of columns.
    const val MORE_KEYS_FLAGS_FIXED_COLUMN: Int = 0x00000100

    // If this flag is specified, the order of more keys is determined by the order in the more
    // keys' specification. Otherwise the order of more keys is automatically determined.
    const val MORE_KEYS_FLAGS_FIXED_ORDER: Int = 0x00000200
    const val MORE_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER: Int = 0
    const val MORE_KEYS_MODE_FIXED_COLUMN_WITH_AUTO_ORDER: Int = MORE_KEYS_FLAGS_FIXED_COLUMN
    const val MORE_KEYS_MODE_FIXED_COLUMN_WITH_FIXED_ORDER: Int =
        (MORE_KEYS_FLAGS_FIXED_COLUMN or MORE_KEYS_FLAGS_FIXED_ORDER)
    const val MORE_KEYS_FLAGS_HAS_LABELS: Int = 0x40000000
    const val MORE_KEYS_FLAGS_NEEDS_DIVIDERS: Int = 0x20000000
    const val MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY: Int = 0x10000000

    const val MORE_KEYS_AUTO_COLUMN_ORDER: String = "!autoColumnOrder!"
    const val MORE_KEYS_FIXED_COLUMN_ORDER: String = "!fixedColumnOrder!"
    const val MORE_KEYS_HAS_LABELS: String = "!hasLabels!"
    const val MORE_KEYS_NEEDS_DIVIDERS: String = "!needsDividers!"
    const val MORE_KEYS_NO_PANEL_AUTO_MORE_KEY: String = "!noPanelAutoMoreKey!"

    const val BACKGROUND_TYPE_EMPTY: Int = 0
    const val BACKGROUND_TYPE_NORMAL: Int = 1
    const val BACKGROUND_TYPE_FUNCTIONAL: Int = 2
    const val BACKGROUND_TYPE_STICKY_OFF: Int = 3
    const val BACKGROUND_TYPE_STICKY_ON: Int = 4
    const val BACKGROUND_TYPE_ACTION: Int = 5
    const val BACKGROUND_TYPE_SPACEBAR: Int = 6

    const val ACTION_FLAGS_IS_REPEATABLE: Int = 0x01
    const val ACTION_FLAGS_NO_KEY_PREVIEW: Int = 0x02
    const val ACTION_FLAGS_ALT_CODE_WHILE_TYPING: Int = 0x04
    const val ACTION_FLAGS_ENABLE_LONG_PRESS: Int = 0x08
}


/**
 * Class for describing the position and characteristics of a single key in the keyboard.
 */
data class Key(
    /**
     * The key code (unicode or custom code) that this key generates.
     */
    val code: Int,

    /** Label to display  */
    val label: String,

    /** Icon to display instead of a label. Icon takes precedence over a label  */
    val iconId: String = KeyboardIconsSet.ICON_UNDEFINED,

    /** Hint label to display on the key in conjunction with the label  */
    val hintLabel: String? = null,

    /** Hint icon to display instead of hint label. Icon takes precedence over a label  */
    val hintIconId: String? = if (hintLabel?.isNotEmpty() == true) {
        ""
    } else {
        null
    },

    /** Flags of the label  */
    val labelFlags: Int,

    /** Width of the key, minus the gap  */
    val width: Int,

    /** Height of the key, minus the gap  */
    val height: Int,

    /**
     * The combined width in pixels of the horizontal gaps belonging to this key, both to the left
     * and to the right. I.e., [width] + [horizontalGap] = total width belonging to the key.
     */
    val horizontalGap: Int,

    /**
     * The combined height in pixels of the vertical gaps belonging to this key, both above and
     * below. I.e., [height] + [verticalGap] = total height belonging to the key.
     */
    val verticalGap: Int,

    /** X coordinate of the top-left corner of the key in the keyboard layout, excluding the gap.  */
    val x: Int,

    /** Y coordinate of the top-left corner of the key in the keyboard layout, excluding the gap.  */
    val y: Int,

    /** Hit bounding box of the key  */
    val hitBox: Rect = Rect(
        x, y,
        x + width + horizontalGap,
        y + height + verticalGap
    ),

    /** More keys. If the key has no morekeys, this will be an empty list. */
    val moreKeys: List<MoreKeySpec> = listOf(),

    /** More keys column number and flags  */
    val moreKeysColumnAndFlags: Int = 0,

    /** Background type that represents different key background visual than normal one.  */
    val visualStyle: KeyVisualStyle,

    val actionFlags: Int,

    val visualAttributes: KeyVisualAttributes? = null,

    /** Text to output. If set, code should be set to Constants.CODE_OUTPUT_TEXT */
    val outputText: String? = null,

    // Not sure what this is
    val altCode: Int = Constants.CODE_UNSPECIFIED,

    /** Backgroundless spacer. Use Constants.CODE_UNSPECIFIED */
    val isSpacer: Boolean = false,

    /** Key is enabled and responds on press  */
    val isEnabled: Boolean = code != Constants.CODE_UNSPECIFIED,

    /** Flick keys */
    val flickKeys: Map<Direction, Key>? = null,

    /** Whether long-press should be fast */
    val isFastLongPress: Boolean,

    /** Affects the key itself but not the popup */
    val labelOverride: String? = null,

    /** Affects the key itself but not the popup */
    val iconOverride: String? = null,

    /** Row/column indices */
    val row: Int,
    val column: Int,
) {
    /** Validation */
    init {
        assert((code == Constants.CODE_OUTPUT_TEXT && outputText != null) ||
                (code != Constants.CODE_OUTPUT_TEXT && outputText == null)) {
            "Output text validation failed: $code $outputText"
        }
    }

    /** The current pressed state of this key  */
    private var mPressed = false

    /** x position for drawing */
    val drawX: Int = x + horizontalGap / 2 // + visualInsetsLeft

    /** width for drawing */
    val drawWidth: Int = width // - visualInsetsLeft - visualInsetsRight

    /** width + gap */
    val totalWidth = width + horizontalGap

    /** hint label to use, either constructor-specified or from moreKeys */
    val effectiveHintLabel = hintLabel ?: moreKeys.firstOrNull()?.let {
        val label = it.mLabel

        if(it.mNeedsToUpperCase) {
            StringUtils.toTitleCaseOfKeyLabel(label, it.mLocale)
        } else {
            label
        }
    }

    /** hint icon to use, either constructor-specified or from moreKeys */
    val effectiveHintIcon = hintIconId ?: moreKeys.firstOrNull()?.let {
        it.mIconId ?: KeySpecParser.getIconId(it.mLabel)
    }


    val isAlignIconToBottom =
        (labelFlags and KeyConsts.LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM) != 0

    val isAlignLabelOffCenter =
        (labelFlags and KeyConsts.LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER) != 0

    val hasPopupHint =
        (labelFlags and KeyConsts.LABEL_FLAGS_HAS_POPUP_HINT) != 0

    val hasShiftedLetterHint =
        ((labelFlags and KeyConsts.LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT) != 0
                && !TextUtils.isEmpty(effectiveHintLabel))

    val hasHintLabel = (labelFlags and KeyConsts.LABEL_FLAGS_HAS_HINT_LABEL) != 0

    val needsAutoXScale = (labelFlags and KeyConsts.LABEL_FLAGS_AUTO_X_SCALE) != 0

    val needsAutoScale =
        (labelFlags and KeyConsts.LABEL_FLAGS_AUTO_SCALE) == KeyConsts.LABEL_FLAGS_AUTO_SCALE

    val hasCustomActionLabel =
        (labelFlags and KeyConsts.LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL) != 0

    private val isShiftedLetterActivated =
        ((labelFlags and KeyConsts.LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) != 0
                && !TextUtils.isEmpty(effectiveHintLabel))

    val moreKeysColumnNumber =
        moreKeysColumnAndFlags and KeyConsts.MORE_KEYS_COLUMN_NUMBER_MASK

    val isMoreKeysFixedColumn =
        (moreKeysColumnAndFlags and KeyConsts.MORE_KEYS_FLAGS_FIXED_COLUMN) != 0

    val isMoreKeysFixedOrder =
        (moreKeysColumnAndFlags and KeyConsts.MORE_KEYS_FLAGS_FIXED_ORDER) != 0

    val hasLabelsInMoreKeys =
        (moreKeysColumnAndFlags and KeyConsts.MORE_KEYS_FLAGS_HAS_LABELS) != 0

    val moreKeyLabelFlags = run {
        val labelSizeFlag = if (hasLabelsInMoreKeys)
            KeyConsts.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO
        else
            KeyConsts.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO

        labelSizeFlag or KeyConsts.LABEL_FLAGS_AUTO_X_SCALE
    }

    val needsDividersInMoreKeys =
        (moreKeysColumnAndFlags and KeyConsts.MORE_KEYS_FLAGS_NEEDS_DIVIDERS) != 0

    val hasNoPanelAutoMoreKey =
        (moreKeysColumnAndFlags and KeyConsts.MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY) != 0


    val isActionKey: Boolean = visualStyle == KeyVisualStyle.Action

    val isShift: Boolean = code == Constants.CODE_SHIFT

    val isModifier: Boolean = code == Constants.CODE_SHIFT || code == Constants.CODE_SWITCH_ALPHA_SYMBOL

    val isRepeatable: Boolean = (actionFlags and KeyConsts.ACTION_FLAGS_IS_REPEATABLE) != 0

    val noKeyPreview: Boolean = (actionFlags and KeyConsts.ACTION_FLAGS_NO_KEY_PREVIEW) != 0

    val altCodeWhileTyping: Boolean = (actionFlags and KeyConsts.ACTION_FLAGS_ALT_CODE_WHILE_TYPING) != 0

    val isLongPressEnabled: Boolean =
        // We need not start long press timer on the key which has activated shifted letter.
        ((actionFlags and KeyConsts.ACTION_FLAGS_ENABLE_LONG_PRESS) != 0
                && (labelFlags and KeyConsts.LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) == 0)

    fun selectTypeface(params: KeyDrawParams): Typeface {
        return when (labelFlags and KeyConsts.LABEL_FLAGS_FONT_MASK) {
            KeyConsts.LABEL_FLAGS_FONT_NORMAL -> Typeface.DEFAULT
            KeyConsts.LABEL_FLAGS_FONT_MONO_SPACE -> Typeface.MONOSPACE
            KeyConsts.LABEL_FLAGS_FONT_DEFAULT ->             // The type-face is specified by keyTypeface attribute.
                params.mTypeface

            else ->
                params.mTypeface
        }
    }

    fun selectTextSize(params: KeyDrawParams): Int {
        return when (labelFlags and KeyConsts.LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK) {
            KeyConsts.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO -> params.mLetterSize
            KeyConsts.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO -> params.mLargeLetterSize
            KeyConsts.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO -> params.mLabelSize
            KeyConsts.LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO -> params.mHintLabelSize
            else -> if (StringUtils.codePointCount(label) == 1 || visualStyle == KeyVisualStyle.Normal) params.mLetterSize else params.mLabelSize
        }
    }

    fun selectTextColor(provider: DynamicThemeProvider, params: KeyDrawParams): Int {
        return provider.getKeyStyleDescriptor(visualStyle).let { style ->
            when {
                mPressed -> style.foregroundColorPressed
                else -> style.foregroundColor
            }
        }
    }

    fun selectBackground(provider: DynamicThemeProvider): Drawable? {
        return provider.getKeyStyleDescriptor(visualStyle).let { style ->
            when {
                mPressed && hasFlick -> run {
                    style.backgroundDrawableFlicking?.get(mFlickDirection)
                }

                mPressed -> style.backgroundDrawablePressed
                else -> style.backgroundDrawable
            }
        }
    }

    fun selectHintTextSize(provider: DynamicThemeProvider, params: KeyDrawParams): Int {
        var value = if (hasHintLabel) {
            params.mHintLabelSize
        }else if (hasShiftedLetterHint) {
            params.mShiftedLetterHintSize
        }else {
            params.mHintLetterSize
        }

        if(provider.hintHiVis) {
            value = (value * 1.3).roundToInt()
        }

        return value
    }

    fun selectHintTypeface(provider: DynamicThemeProvider, params: KeyDrawParams): Typeface {
        return when {
            hasHintLabel || provider.hintHiVis -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(provider.selectKeyTypeface(Typeface.DEFAULT), 700, false)
            } else {
                provider.selectKeyTypeface(Typeface.DEFAULT_BOLD)
            }

            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(provider.selectKeyTypeface(Typeface.DEFAULT), 500, false)
            } else {
                provider.selectKeyTypeface(Typeface.DEFAULT)
            }
        }
    }

    fun selectHintTextColor(provider: DynamicThemeProvider, params: KeyDrawParams): Int {
        return provider.hintColor ?: provider.getKeyStyleDescriptor(visualStyle).let { style ->
            when {
                mPressed -> style.foregroundColorPressed
                else -> style.foregroundColor
            }
        }.let {
            Color(it).copy(alpha = 0.8f).toArgb()
        }
    }

    fun selectMoreKeyTextSize(params: KeyDrawParams): Int {
        return if (hasLabelsInMoreKeys) params.mLabelSize else params.mLetterSize
    }

    val previewLabel: String?
        get() = when {
            isShiftedLetterActivated -> hintLabel
            mPressed && hasFlick && (mFlickDirection != null) -> flickKeys!![mFlickDirection]!!.previewLabel
            else -> label
        }

    private fun previewHasLetterSize(): Boolean {
        return ((labelFlags and KeyConsts.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO) != 0
                || StringUtils.codePointCount(previewLabel) == 1)
    }

    fun selectPreviewTextSize(params: KeyDrawParams): Int {
        return params.mPreviewTextSize
    }

    fun selectPreviewTypeface(params: KeyDrawParams): Typeface {
        if (previewHasLetterSize()) {
            return selectTypeface(params)
        }
        return Typeface.DEFAULT_BOLD
    }

    fun isAlignHintLabelToBottom(defaultFlags: Int): Boolean {
        return ((labelFlags or defaultFlags) and KeyConsts.LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM) != 0
    }

    fun needsToKeepBackgroundAspectRatio(defaultFlags: Int): Boolean {
        return ((labelFlags or defaultFlags) and KeyConsts.LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO) != 0
    }

    fun getIcon(iconSet: KeyboardIconsSet, alpha: Int): Drawable? {
        val iconId = iconId
        val icon = iconSet.getIconDrawable(iconId)
        if (icon != null) {
            icon.alpha = alpha
        }
        return icon
    }

    fun getIconOverride(iconSet: KeyboardIconsSet, alpha: Int): Drawable? {
        val iconId = iconOverride ?: iconId
        val icon = iconSet.getIconDrawable(iconId)
        if (icon != null) {
            icon.alpha = alpha
        }
        return icon
    }

    fun getHintIcon(iconSet: KeyboardIconsSet, alpha: Int): Drawable? {
        val iconId = effectiveHintIcon ?: return null

        val icon = iconSet.getIconDrawable(iconId)
        if (icon != null) {
            icon.alpha = alpha
        }
        return icon
    }

    fun getPreviewIcon(iconSet: KeyboardIconsSet): Drawable? {
        return iconSet.getIconDrawable(iconId)
    }

    val pressed: Boolean
        get() = mPressed

    /**
     * Informs the key that it has been pressed, in case it needs to change its appearance or
     * state.
     * @see .onReleased
     */
    fun onPressed() {
        mPressed = true
        themeCache.clear()
    }

    /**
     * Informs the key that it has been released, in case it needs to change its appearance or
     * state.
     * @see .onPressed
     */
    fun onReleased() {
        mPressed = false
        themeCache.clear()
    }

    /**
     * Detects if a point falls on this key.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether or not the point falls on the key. If the key is attached to an edge, it
     * will assume that all points between the key and the edge are considered to be on the key.
     * @see .markAsLeftEdge
     */
    fun isOnKey(x: Int, y: Int): Boolean {
        return hitBox.contains(x, y)
    }

    /**
     * Returns the distance to the nearest edge of the key and the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the distance of the point from the nearest edge of the key
     */
    fun distanceToEdge(x: Int, y: Int): Float {
        val left = this.x
        val right = left + width
        val top = this.y
        val bottom = top + height
        val edgeX = if (x < left) left else (if (x > right) right else x)
        val edgeY = if (y < top) top else (if (y > bottom) bottom else y)
        val dx = x - edgeX
        val dy = y - edgeY
        return sqrt((dx * dx + dy * dy).toFloat())
    }

    fun distanceToCenter(x: Int, y: Int): Float {
        val cx = (this.x + width / 2)
        val cy = (this.y + height / 2)

        val dx = x - cx
        val dy = y - cy
        return sqrt((dx * dx + dy * dy).toFloat())
    }

    val hasFlick: Boolean = flickKeys != null && flickKeys.isNotEmpty()
    private var mFlickDirection: Direction? = null
    fun flickDirection(dx: Int, dy: Int): Direction? = run {
        if(flickKeys == null) {
            null
        } else {
            val dirs = computeDirectionsFromDeltaPos(
                dx = dx.toDouble(),
                dy = dy.toDouble(),
                threshold = (width / 3).toDouble()
            )
            dirs.firstOrNull { flickKeys.contains(it) }
        }
    }.also {
        mFlickDirection = it
    }

    fun flick(dx: Int, dy: Int): Key =
        flickDirection(dx, dy)?.let { (flickKeys ?: return@let null)[it] } ?: this

    val flickDirection: Direction?
        get() = mFlickDirection

    companion object {
        @JvmStatic
        fun removeRedundantMoreKeys(
            key: Key,
            lettersOnBaseLayout: LettersOnBaseLayout?,
            onlyDuplicateKeys: Boolean
        ): Key {
            val moreKeys = key.moreKeys

            var clearedMoreKeys: Array<MoreKeySpec?>?

            clearedMoreKeys = MoreKeySpec.removeDuplicateMoreKeys(key.code, moreKeys.toTypedArray())

            if (!onlyDuplicateKeys) {
                clearedMoreKeys = MoreKeySpec.removeRedundantMoreKeys(
                    clearedMoreKeys, lettersOnBaseLayout!!
                )
            }


            val differs = when {
                (moreKeys.isEmpty() && clearedMoreKeys == null) -> false
                (moreKeys.isNotEmpty() && clearedMoreKeys != null) -> true
                (clearedMoreKeys.contentEquals(moreKeys.toTypedArray())) -> false
                else -> true
            }

            return if(differs) {
                key.copy(
                    moreKeys = clearedMoreKeys?.filterNotNull() ?: listOf()
                )
            } else {
                key
            }
        }

        private fun needsToUpcase(labelFlags: Int, keyboardElementId: Int): Boolean {
            if ((labelFlags and KeyConsts.LABEL_FLAGS_PRESERVE_CASE) != 0) return false
            return when (keyboardElementId) {
                KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> true
                else -> false
            }
        }

        @JvmStatic
        fun buildKeyForMoreKeySpec(
            moreKeySpec: MoreKeySpec,
            x: Int,
            y: Int,
            labelFlags: Int,
            params: KeyboardParams
        ): Key {
            return Key(
                code = moreKeySpec.mCode,
                label = moreKeySpec.mLabel ?: "",
                iconId = moreKeySpec.mIconId ?: "",
                labelFlags = labelFlags,
                width = params.mDefaultKeyWidth,
                height = params.mDefaultRowHeight,
                horizontalGap = params.mHorizontalGap,
                verticalGap = params.mVerticalGap,
                x = x,
                y = y,
                visualStyle = KeyVisualStyle.MoreKey,
                actionFlags = KeyConsts.ACTION_FLAGS_NO_KEY_PREVIEW,
                outputText = moreKeySpec.mOutputText,
                isFastLongPress = false,

                row = x / params.mDefaultKeyWidth, column = y / params.mDefaultRowHeight
            )
        }
    }

    // This should be invalidated (cleared) whenever something about the key that a KeyQualifier
    // depends on changes. Currently this is only pressed but may include flickDirection in future
    var themeCache: MutableIntIntMap = mutableIntIntMapOf()
}
