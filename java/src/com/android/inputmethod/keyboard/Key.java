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

package com.android.inputmethod.keyboard;

import static com.android.inputmethod.keyboard.internal.KeyboardIconsSet.ICON_UNDEFINED;
import static com.android.inputmethod.latin.Constants.CODE_OUTPUT_TEXT;
import static com.android.inputmethod.latin.Constants.CODE_SHIFT;
import static com.android.inputmethod.latin.Constants.CODE_SWITCH_ALPHA_SYMBOL;
import static com.android.inputmethod.latin.Constants.CODE_UNSPECIFIED;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.android.inputmethod.keyboard.internal.KeyDrawParams;
import com.android.inputmethod.keyboard.internal.KeySpecParser;
import com.android.inputmethod.keyboard.internal.KeyStyle;
import com.android.inputmethod.keyboard.internal.KeyVisualAttributes;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.keyboard.internal.KeyboardRow;
import com.android.inputmethod.keyboard.internal.MoreKeySpec;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StringUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Class for describing the position and characteristics of a single key in the keyboard.
 */
public class Key implements Comparable<Key> {
    private static final String TAG = Key.class.getSimpleName();

    /**
     * The key code (unicode or custom code) that this key generates.
     */
    public final int mCode;

    /** Label to display */
    public final String mLabel;
    /** Hint label to display on the key in conjunction with the label */
    public final String mHintLabel;
    /** Flags of the label */
    private final int mLabelFlags;
    private static final int LABEL_FLAGS_ALIGN_LEFT = 0x01;
    private static final int LABEL_FLAGS_ALIGN_RIGHT = 0x02;
    private static final int LABEL_FLAGS_ALIGN_LEFT_OF_CENTER = 0x08;
    private static final int LABEL_FLAGS_FONT_NORMAL = 0x10;
    private static final int LABEL_FLAGS_FONT_MONO_SPACE = 0x20;
    // Start of key text ratio enum values
    private static final int LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK = 0x1C0;
    private static final int LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO = 0x40;
    private static final int LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO = 0x80;
    private static final int LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO = 0xC0;
    private static final int LABEL_FLAGS_FOLLOW_KEY_LARGE_LABEL_RATIO = 0x100;
    private static final int LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO = 0x140;
    // End of key text ratio mask enum values
    private static final int LABEL_FLAGS_HAS_POPUP_HINT = 0x200;
    private static final int LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT = 0x400;
    private static final int LABEL_FLAGS_HAS_HINT_LABEL = 0x800;
    private static final int LABEL_FLAGS_WITH_ICON_LEFT = 0x1000;
    private static final int LABEL_FLAGS_WITH_ICON_RIGHT = 0x2000;
    private static final int LABEL_FLAGS_AUTO_X_SCALE = 0x4000;
    private static final int LABEL_FLAGS_PRESERVE_CASE = 0x8000;
    private static final int LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED = 0x10000;
    private static final int LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL = 0x20000;
    private static final int LABEL_FLAGS_DISABLE_HINT_LABEL = 0x40000000;
    private static final int LABEL_FLAGS_DISABLE_ADDITIONAL_MORE_KEYS = 0x80000000;

    /** Icon to display instead of a label. Icon takes precedence over a label */
    private final int mIconId;

    /** Width of the key, not including the gap */
    public final int mWidth;
    /** Height of the key, not including the gap */
    public final int mHeight;
    /** X coordinate of the key in the keyboard layout */
    public final int mX;
    /** Y coordinate of the key in the keyboard layout */
    public final int mY;
    /** Hit bounding box of the key */
    public final Rect mHitBox = new Rect();

    /** More keys. It is guaranteed that this is null or an array of one or more elements */
    public final MoreKeySpec[] mMoreKeys;
    /** More keys column number and flags */
    private final int mMoreKeysColumnAndFlags;
    private static final int MORE_KEYS_COLUMN_MASK = 0x000000ff;
    private static final int MORE_KEYS_FLAGS_FIXED_COLUMN_ORDER = 0x80000000;
    private static final int MORE_KEYS_FLAGS_HAS_LABELS = 0x40000000;
    private static final int MORE_KEYS_FLAGS_NEEDS_DIVIDERS = 0x20000000;
    private static final int MORE_KEYS_FLAGS_EMBEDDED_MORE_KEY = 0x10000000;
    private static final String MORE_KEYS_AUTO_COLUMN_ORDER = "!autoColumnOrder!";
    private static final String MORE_KEYS_FIXED_COLUMN_ORDER = "!fixedColumnOrder!";
    private static final String MORE_KEYS_HAS_LABELS = "!hasLabels!";
    private static final String MORE_KEYS_NEEDS_DIVIDERS = "!needsDividers!";
    private static final String MORE_KEYS_EMBEDDED_MORE_KEY = "!embeddedMoreKey!";

    /** Background type that represents different key background visual than normal one. */
    public final int mBackgroundType;
    public static final int BACKGROUND_TYPE_NORMAL = 0;
    public static final int BACKGROUND_TYPE_FUNCTIONAL = 1;
    public static final int BACKGROUND_TYPE_ACTION = 2;
    public static final int BACKGROUND_TYPE_STICKY_OFF = 3;
    public static final int BACKGROUND_TYPE_STICKY_ON = 4;

    private final int mActionFlags;
    private static final int ACTION_FLAGS_IS_REPEATABLE = 0x01;
    private static final int ACTION_FLAGS_NO_KEY_PREVIEW = 0x02;
    private static final int ACTION_FLAGS_ALT_CODE_WHILE_TYPING = 0x04;
    private static final int ACTION_FLAGS_ENABLE_LONG_PRESS = 0x08;

    public final KeyVisualAttributes mKeyVisualAttributes;

    private final OptionalAttributes mOptionalAttributes;

    private static final class OptionalAttributes {
        /** Text to output when pressed. This can be multiple characters, like ".com" */
        public final String mOutputText;
        public final int mAltCode;
        /** Icon for disabled state */
        public final int mDisabledIconId;
        /** Preview version of the icon, for the preview popup */
        public final int mPreviewIconId;
        /** The visual insets */
        public final int mVisualInsetsLeft;
        public final int mVisualInsetsRight;

        public OptionalAttributes(final String outputText, final int altCode,
                final int disabledIconId, final int previewIconId,
                final int visualInsetsLeft, final int visualInsetsRight) {
            mOutputText = outputText;
            mAltCode = altCode;
            mDisabledIconId = disabledIconId;
            mPreviewIconId = previewIconId;
            mVisualInsetsLeft = visualInsetsLeft;
            mVisualInsetsRight = visualInsetsRight;
        }
    }

    private final int mHashCode;

    /** The current pressed state of this key */
    private boolean mPressed;
    /** Key is enabled and responds on press */
    private boolean mEnabled = true;

    /**
     * This constructor is being used only for keys in more keys keyboard.
     */
    public Key(final KeyboardParams params, final MoreKeySpec moreKeySpec, final int x, final int y,
            final int width, final int height, final int labelFlags) {
        this(params, moreKeySpec.mLabel, null, moreKeySpec.mIconId, moreKeySpec.mCode,
                moreKeySpec.mOutputText, x, y, width, height, labelFlags);
    }

    /**
     * This constructor is being used only for key in popup suggestions pane.
     */
    public Key(final KeyboardParams params, final String label, final String hintLabel,
            final int iconId, final int code, final String outputText, final int x, final int y,
            final int width, final int height, final int labelFlags) {
        mHeight = height - params.mVerticalGap;
        mWidth = width - params.mHorizontalGap;
        mHintLabel = hintLabel;
        mLabelFlags = labelFlags;
        mBackgroundType = BACKGROUND_TYPE_NORMAL;
        mActionFlags = 0;
        mMoreKeys = null;
        mMoreKeysColumnAndFlags = 0;
        mLabel = label;
        if (outputText == null) {
            mOptionalAttributes = null;
        } else {
            mOptionalAttributes = new OptionalAttributes(outputText, CODE_UNSPECIFIED,
                    ICON_UNDEFINED, ICON_UNDEFINED, 0, 0);
        }
        mCode = code;
        mEnabled = (code != CODE_UNSPECIFIED);
        mIconId = iconId;
        // Horizontal gap is divided equally to both sides of the key.
        mX = x + params.mHorizontalGap / 2;
        mY = y;
        mHitBox.set(x, y, x + width + 1, y + height);
        mKeyVisualAttributes = null;

        mHashCode = computeHashCode(this);
    }

    /**
     * Create a key with the given top-left coordinate and extract its attributes from the XML
     * parser.
     * @param res resources associated with the caller's context
     * @param params the keyboard building parameters.
     * @param row the row that this key belongs to. row's x-coordinate will be the right edge of
     *        this key.
     * @param parser the XML parser containing the attributes for this key
     * @throws XmlPullParserException
     */
    public Key(final Resources res, final KeyboardParams params, final KeyboardRow row,
            final XmlPullParser parser) throws XmlPullParserException {
        final float horizontalGap = isSpacer() ? 0 : params.mHorizontalGap;
        final int rowHeight = row.mRowHeight;
        mHeight = rowHeight - params.mVerticalGap;

        final TypedArray keyAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key);

        final KeyStyle style = params.mKeyStyles.getKeyStyle(keyAttr, parser);
        final float keyXPos = row.getKeyX(keyAttr);
        final float keyWidth = row.getKeyWidth(keyAttr, keyXPos);
        final int keyYPos = row.getKeyY();

        // Horizontal gap is divided equally to both sides of the key.
        mX = Math.round(keyXPos + horizontalGap / 2);
        mY = keyYPos;
        mWidth = Math.round(keyWidth - horizontalGap);
        mHitBox.set(Math.round(keyXPos), keyYPos, Math.round(keyXPos + keyWidth) + 1,
                keyYPos + rowHeight);
        // Update row to have current x coordinate.
        row.setXPos(keyXPos + keyWidth);

        mBackgroundType = style.getInt(keyAttr,
                R.styleable.Keyboard_Key_backgroundType, row.getDefaultBackgroundType());

        final int baseWidth = params.mBaseWidth;
        final int visualInsetsLeft = Math.round(keyAttr.getFraction(
                R.styleable.Keyboard_Key_visualInsetsLeft, baseWidth, baseWidth, 0));
        final int visualInsetsRight = Math.round(keyAttr.getFraction(
                R.styleable.Keyboard_Key_visualInsetsRight, baseWidth, baseWidth, 0));
        mIconId = KeySpecParser.getIconId(style.getString(keyAttr,
                R.styleable.Keyboard_Key_keyIcon));
        final int disabledIconId = KeySpecParser.getIconId(style.getString(keyAttr,
                R.styleable.Keyboard_Key_keyIconDisabled));
        final int previewIconId = KeySpecParser.getIconId(style.getString(keyAttr,
                R.styleable.Keyboard_Key_keyIconPreview));

        mLabelFlags = style.getFlag(keyAttr, R.styleable.Keyboard_Key_keyLabelFlags)
                | row.getDefaultKeyLabelFlags();
        final boolean needsToUpperCase = needsToUpperCase(mLabelFlags, params.mId.mElementId);
        final Locale locale = params.mId.mLocale;
        int actionFlags = style.getFlag(keyAttr, R.styleable.Keyboard_Key_keyActionFlags);
        String[] moreKeys = style.getStringArray(keyAttr, R.styleable.Keyboard_Key_moreKeys);

        int moreKeysColumn = style.getInt(keyAttr,
                R.styleable.Keyboard_Key_maxMoreKeysColumn, params.mMaxMoreKeysKeyboardColumn);
        int value;
        if ((value = KeySpecParser.getIntValue(moreKeys, MORE_KEYS_AUTO_COLUMN_ORDER, -1)) > 0) {
            moreKeysColumn = value & MORE_KEYS_COLUMN_MASK;
        }
        if ((value = KeySpecParser.getIntValue(moreKeys, MORE_KEYS_FIXED_COLUMN_ORDER, -1)) > 0) {
            moreKeysColumn = MORE_KEYS_FLAGS_FIXED_COLUMN_ORDER | (value & MORE_KEYS_COLUMN_MASK);
        }
        if (KeySpecParser.getBooleanValue(moreKeys, MORE_KEYS_HAS_LABELS)) {
            moreKeysColumn |= MORE_KEYS_FLAGS_HAS_LABELS;
        }
        if (KeySpecParser.getBooleanValue(moreKeys, MORE_KEYS_NEEDS_DIVIDERS)) {
            moreKeysColumn |= MORE_KEYS_FLAGS_NEEDS_DIVIDERS;
        }
        if (KeySpecParser.getBooleanValue(moreKeys, MORE_KEYS_EMBEDDED_MORE_KEY)) {
            moreKeysColumn |= MORE_KEYS_FLAGS_EMBEDDED_MORE_KEY;
        }
        mMoreKeysColumnAndFlags = moreKeysColumn;

        final String[] additionalMoreKeys;
        if ((mLabelFlags & LABEL_FLAGS_DISABLE_ADDITIONAL_MORE_KEYS) != 0) {
            additionalMoreKeys = null;
        } else {
            additionalMoreKeys = style.getStringArray(keyAttr,
                    R.styleable.Keyboard_Key_additionalMoreKeys);
        }
        moreKeys = KeySpecParser.insertAdditionalMoreKeys(moreKeys, additionalMoreKeys);
        if (moreKeys != null) {
            actionFlags |= ACTION_FLAGS_ENABLE_LONG_PRESS;
            mMoreKeys = new MoreKeySpec[moreKeys.length];
            for (int i = 0; i < moreKeys.length; i++) {
                mMoreKeys[i] = new MoreKeySpec(
                        moreKeys[i], needsToUpperCase, locale, params.mCodesSet);
            }
        } else {
            mMoreKeys = null;
        }
        mActionFlags = actionFlags;

        if ((mLabelFlags & LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL) != 0) {
            mLabel = params.mId.mCustomActionLabel;
        } else {
            mLabel = KeySpecParser.toUpperCaseOfStringForLocale(style.getString(keyAttr,
                    R.styleable.Keyboard_Key_keyLabel), needsToUpperCase, locale);
        }
        if ((mLabelFlags & LABEL_FLAGS_DISABLE_HINT_LABEL) != 0) {
            mHintLabel = null;
        } else {
            mHintLabel = KeySpecParser.toUpperCaseOfStringForLocale(style.getString(keyAttr,
                    R.styleable.Keyboard_Key_keyHintLabel), needsToUpperCase, locale);
        }
        String outputText = KeySpecParser.toUpperCaseOfStringForLocale(style.getString(keyAttr,
                R.styleable.Keyboard_Key_keyOutputText), needsToUpperCase, locale);
        final int code = KeySpecParser.parseCode(style.getString(keyAttr,
                R.styleable.Keyboard_Key_code), params.mCodesSet, CODE_UNSPECIFIED);
        // Choose the first letter of the label as primary code if not specified.
        if (code == CODE_UNSPECIFIED && TextUtils.isEmpty(outputText)
                && !TextUtils.isEmpty(mLabel)) {
            if (StringUtils.codePointCount(mLabel) == 1) {
                // Use the first letter of the hint label if shiftedLetterActivated flag is
                // specified.
                if (hasShiftedLetterHint() && isShiftedLetterActivated()
                        && !TextUtils.isEmpty(mHintLabel)) {
                    mCode = mHintLabel.codePointAt(0);
                } else {
                    mCode = mLabel.codePointAt(0);
                }
            } else {
                // In some locale and case, the character might be represented by multiple code
                // points, such as upper case Eszett of German alphabet.
                outputText = mLabel;
                mCode = CODE_OUTPUT_TEXT;
            }
        } else if (code == CODE_UNSPECIFIED && outputText != null) {
            if (StringUtils.codePointCount(outputText) == 1) {
                mCode = outputText.codePointAt(0);
                outputText = null;
            } else {
                mCode = CODE_OUTPUT_TEXT;
            }
        } else {
            mCode = KeySpecParser.toUpperCaseOfCodeForLocale(code, needsToUpperCase, locale);
        }
        final int altCode = KeySpecParser.toUpperCaseOfCodeForLocale(
                KeySpecParser.parseCode(style.getString(keyAttr,
                R.styleable.Keyboard_Key_altCode), params.mCodesSet, CODE_UNSPECIFIED),
                needsToUpperCase, locale);
        if (outputText == null && altCode == CODE_UNSPECIFIED
                && disabledIconId == ICON_UNDEFINED && previewIconId == ICON_UNDEFINED
                && visualInsetsLeft == 0 && visualInsetsRight == 0) {
            mOptionalAttributes = null;
        } else {
            mOptionalAttributes = new OptionalAttributes(outputText, altCode,
                    disabledIconId, previewIconId,
                    visualInsetsLeft, visualInsetsRight);
        }
        mKeyVisualAttributes = KeyVisualAttributes.newInstance(keyAttr);
        keyAttr.recycle();
        mHashCode = computeHashCode(this);
        if (hasShiftedLetterHint() && TextUtils.isEmpty(mHintLabel)) {
            Log.w(TAG, "hasShiftedLetterHint specified without keyHintLabel: " + this);
        }
    }

    private static boolean needsToUpperCase(final int labelFlags, final int keyboardElementId) {
        if ((labelFlags & LABEL_FLAGS_PRESERVE_CASE) != 0) return false;
        switch (keyboardElementId) {
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
            return true;
        default:
            return false;
        }
    }

    private static int computeHashCode(final Key key) {
        return Arrays.hashCode(new Object[] {
                key.mX,
                key.mY,
                key.mWidth,
                key.mHeight,
                key.mCode,
                key.mLabel,
                key.mHintLabel,
                key.mIconId,
                key.mBackgroundType,
                Arrays.hashCode(key.mMoreKeys),
                key.getOutputText(),
                key.mActionFlags,
                key.mLabelFlags,
                // Key can be distinguishable without the following members.
                // key.mOptionalAttributes.mAltCode,
                // key.mOptionalAttributes.mDisabledIconId,
                // key.mOptionalAttributes.mPreviewIconId,
                // key.mHorizontalGap,
                // key.mVerticalGap,
                // key.mOptionalAttributes.mVisualInsetLeft,
                // key.mOptionalAttributes.mVisualInsetRight,
                // key.mMaxMoreKeysColumn,
        });
    }

    private boolean equalsInternal(final Key o) {
        if (this == o) return true;
        return o.mX == mX
                && o.mY == mY
                && o.mWidth == mWidth
                && o.mHeight == mHeight
                && o.mCode == mCode
                && TextUtils.equals(o.mLabel, mLabel)
                && TextUtils.equals(o.mHintLabel, mHintLabel)
                && o.mIconId == mIconId
                && o.mBackgroundType == mBackgroundType
                && Arrays.equals(o.mMoreKeys, mMoreKeys)
                && TextUtils.equals(o.getOutputText(), getOutputText())
                && o.mActionFlags == mActionFlags
                && o.mLabelFlags == mLabelFlags;
    }

    @Override
    public int compareTo(Key o) {
        if (equalsInternal(o)) return 0;
        if (mHashCode > o.mHashCode) return 1;
        return -1;
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof Key && equalsInternal((Key)o);
    }

    @Override
    public String toString() {
        final String label;
        if (StringUtils.codePointCount(mLabel) == 1 && mLabel.codePointAt(0) == mCode) {
            label = "";
        } else {
            label = "/" + mLabel;
        }
        return String.format("%s%s %d,%d %dx%d %s/%s/%s",
                Constants.printableCode(mCode), label, mX, mY, mWidth, mHeight, mHintLabel,
                KeyboardIconsSet.getIconName(mIconId), backgroundName(mBackgroundType));
    }

    private static String backgroundName(final int backgroundType) {
        switch (backgroundType) {
        case BACKGROUND_TYPE_NORMAL: return "normal";
        case BACKGROUND_TYPE_FUNCTIONAL: return "functional";
        case BACKGROUND_TYPE_ACTION: return "action";
        case BACKGROUND_TYPE_STICKY_OFF: return "stickyOff";
        case BACKGROUND_TYPE_STICKY_ON: return "stickyOn";
        default: return null;
        }
    }

    public void markAsLeftEdge(final KeyboardParams params) {
        mHitBox.left = params.mLeftPadding;
    }

    public void markAsRightEdge(final KeyboardParams params) {
        mHitBox.right = params.mOccupiedWidth - params.mRightPadding;
    }

    public void markAsTopEdge(final KeyboardParams params) {
        mHitBox.top = params.mTopPadding;
    }

    public void markAsBottomEdge(final KeyboardParams params) {
        mHitBox.bottom = params.mOccupiedHeight + params.mBottomPadding;
    }

    public final boolean isSpacer() {
        return this instanceof Spacer;
    }

    public final boolean isShift() {
        return mCode == CODE_SHIFT;
    }

    public final boolean isModifier() {
        return mCode == CODE_SHIFT || mCode == CODE_SWITCH_ALPHA_SYMBOL;
    }

    public final boolean isRepeatable() {
        return (mActionFlags & ACTION_FLAGS_IS_REPEATABLE) != 0;
    }

    public final boolean noKeyPreview() {
        return (mActionFlags & ACTION_FLAGS_NO_KEY_PREVIEW) != 0;
    }

    public final boolean altCodeWhileTyping() {
        return (mActionFlags & ACTION_FLAGS_ALT_CODE_WHILE_TYPING) != 0;
    }

    public final boolean isLongPressEnabled() {
        // We need not start long press timer on the key which has activated shifted letter.
        return (mActionFlags & ACTION_FLAGS_ENABLE_LONG_PRESS) != 0
                && (mLabelFlags & LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) == 0;
    }

    public final Typeface selectTypeface(final KeyDrawParams params) {
        // TODO: Handle "bold" here too?
        if ((mLabelFlags & LABEL_FLAGS_FONT_NORMAL) != 0) {
            return Typeface.DEFAULT;
        }
        if ((mLabelFlags & LABEL_FLAGS_FONT_MONO_SPACE) != 0) {
            return Typeface.MONOSPACE;
        }
        return params.mTypeface;
    }

    public final int selectTextSize(final KeyDrawParams params) {
        switch (mLabelFlags & LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK) {
        case LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO:
            return params.mLetterSize;
        case LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO:
            return params.mLargeLetterSize;
        case LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO:
            return params.mLabelSize;
        case LABEL_FLAGS_FOLLOW_KEY_LARGE_LABEL_RATIO:
            return params.mLargeLabelSize;
        case LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO:
            return params.mHintLabelSize;
        default: // No follow key ratio flag specified.
            return StringUtils.codePointCount(mLabel) == 1 ? params.mLetterSize : params.mLabelSize;
        }
    }

    public final int selectTextColor(final KeyDrawParams params) {
        return isShiftedLetterActivated() ? params.mTextInactivatedColor : params.mTextColor;
    }

    public final int selectHintTextSize(final KeyDrawParams params) {
        if (hasHintLabel()) {
            return params.mHintLabelSize;
        }
        if (hasShiftedLetterHint()) {
            return params.mShiftedLetterHintSize;
        }
        return params.mHintLetterSize;
    }

    public final int selectHintTextColor(final KeyDrawParams params) {
        if (hasHintLabel()) {
            return params.mHintLabelColor;
        }
        if (hasShiftedLetterHint()) {
            return isShiftedLetterActivated() ? params.mShiftedLetterHintActivatedColor
                    : params.mShiftedLetterHintInactivatedColor;
        }
        return params.mHintLetterColor;
    }

    public final int selectMoreKeyTextSize(final KeyDrawParams params) {
        return hasLabelsInMoreKeys() ? params.mLabelSize : params.mLetterSize;
    }

    public final String getPreviewLabel() {
        return isShiftedLetterActivated() ? mHintLabel : mLabel;
    }

    private boolean previewHasLetterSize() {
        return (mLabelFlags & LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO) != 0
                || StringUtils.codePointCount(getPreviewLabel()) == 1;
    }

    public final int selectPreviewTextSize(final KeyDrawParams params) {
        if (previewHasLetterSize()) {
            return params.mPreviewTextSize;
        }
        return params.mLetterSize;
    }

    public Typeface selectPreviewTypeface(final KeyDrawParams params) {
        if (previewHasLetterSize()) {
            return selectTypeface(params);
        }
        return Typeface.DEFAULT_BOLD;
    }

    public final boolean isAlignLeft() {
        return (mLabelFlags & LABEL_FLAGS_ALIGN_LEFT) != 0;
    }

    public final boolean isAlignRight() {
        return (mLabelFlags & LABEL_FLAGS_ALIGN_RIGHT) != 0;
    }

    public final boolean isAlignLeftOfCenter() {
        return (mLabelFlags & LABEL_FLAGS_ALIGN_LEFT_OF_CENTER) != 0;
    }

    public final boolean hasPopupHint() {
        return (mLabelFlags & LABEL_FLAGS_HAS_POPUP_HINT) != 0;
    }

    public final boolean hasShiftedLetterHint() {
        return (mLabelFlags & LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT) != 0;
    }

    public final boolean hasHintLabel() {
        return (mLabelFlags & LABEL_FLAGS_HAS_HINT_LABEL) != 0;
    }

    public final boolean hasLabelWithIconLeft() {
        return (mLabelFlags & LABEL_FLAGS_WITH_ICON_LEFT) != 0;
    }

    public final boolean hasLabelWithIconRight() {
        return (mLabelFlags & LABEL_FLAGS_WITH_ICON_RIGHT) != 0;
    }

    public final boolean needsXScale() {
        return (mLabelFlags & LABEL_FLAGS_AUTO_X_SCALE) != 0;
    }

    public final boolean isShiftedLetterActivated() {
        return (mLabelFlags & LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) != 0;
    }

    public final int getMoreKeysColumn() {
        return mMoreKeysColumnAndFlags & MORE_KEYS_COLUMN_MASK;
    }

    public final boolean isFixedColumnOrderMoreKeys() {
        return (mMoreKeysColumnAndFlags & MORE_KEYS_FLAGS_FIXED_COLUMN_ORDER) != 0;
    }

    public final boolean hasLabelsInMoreKeys() {
        return (mMoreKeysColumnAndFlags & MORE_KEYS_FLAGS_HAS_LABELS) != 0;
    }

    public final int getMoreKeyLabelFlags() {
        return hasLabelsInMoreKeys()
                ? LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO
                : LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO;
    }

    public final boolean needsDividersInMoreKeys() {
        return (mMoreKeysColumnAndFlags & MORE_KEYS_FLAGS_NEEDS_DIVIDERS) != 0;
    }

    public final boolean hasEmbeddedMoreKey() {
        return (mMoreKeysColumnAndFlags & MORE_KEYS_FLAGS_EMBEDDED_MORE_KEY) != 0;
    }

    public final String getOutputText() {
        final OptionalAttributes attrs = mOptionalAttributes;
        return (attrs != null) ? attrs.mOutputText : null;
    }

    public final int getAltCode() {
        final OptionalAttributes attrs = mOptionalAttributes;
        return (attrs != null) ? attrs.mAltCode : CODE_UNSPECIFIED;
    }

    public Drawable getIcon(final KeyboardIconsSet iconSet, final int alpha) {
        final OptionalAttributes attrs = mOptionalAttributes;
        final int disabledIconId = (attrs != null) ? attrs.mDisabledIconId : ICON_UNDEFINED;
        final int iconId = mEnabled ? mIconId : disabledIconId;
        final Drawable icon = iconSet.getIconDrawable(iconId);
        if (icon != null) {
            icon.setAlpha(alpha);
        }
        return icon;
    }

    public Drawable getPreviewIcon(final KeyboardIconsSet iconSet) {
        final OptionalAttributes attrs = mOptionalAttributes;
        final int previewIconId = (attrs != null) ? attrs.mPreviewIconId : ICON_UNDEFINED;
        return previewIconId != ICON_UNDEFINED
                ? iconSet.getIconDrawable(previewIconId) : iconSet.getIconDrawable(mIconId);
    }

    public final int getDrawX() {
        final OptionalAttributes attrs = mOptionalAttributes;
        return (attrs == null) ? mX : mX + attrs.mVisualInsetsLeft;
    }

    public final int getDrawWidth() {
        final OptionalAttributes attrs = mOptionalAttributes;
        return (attrs == null) ? mWidth
                : mWidth - attrs.mVisualInsetsLeft - attrs.mVisualInsetsRight;
    }

    /**
     * Informs the key that it has been pressed, in case it needs to change its appearance or
     * state.
     * @see #onReleased()
     */
    public void onPressed() {
        mPressed = true;
    }

    /**
     * Informs the key that it has been released, in case it needs to change its appearance or
     * state.
     * @see #onPressed()
     */
    public void onReleased() {
        mPressed = false;
    }

    public final boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(final boolean enabled) {
        mEnabled = enabled;
    }

    /**
     * Detects if a point falls on this key.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether or not the point falls on the key. If the key is attached to an edge, it
     * will assume that all points between the key and the edge are considered to be on the key.
     * @see #markAsLeftEdge(KeyboardParams) etc.
     */
    public boolean isOnKey(final int x, final int y) {
        return mHitBox.contains(x, y);
    }

    /**
     * Returns the square of the distance to the nearest edge of the key and the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the square of the distance of the point from the nearest edge of the key
     */
    public int squaredDistanceToEdge(final int x, final int y) {
        final int left = mX;
        final int right = left + mWidth;
        final int top = mY;
        final int bottom = top + mHeight;
        final int edgeX = x < left ? left : (x > right ? right : x);
        final int edgeY = y < top ? top : (y > bottom ? bottom : y);
        final int dx = x - edgeX;
        final int dy = y - edgeY;
        return dx * dx + dy * dy;
    }

    private final static int[] KEY_STATE_NORMAL_HIGHLIGHT_ON = {
        android.R.attr.state_checkable,
        android.R.attr.state_checked
    };

    private final static int[] KEY_STATE_PRESSED_HIGHLIGHT_ON = {
        android.R.attr.state_pressed,
        android.R.attr.state_checkable,
        android.R.attr.state_checked
    };

    private final static int[] KEY_STATE_NORMAL_HIGHLIGHT_OFF = {
        android.R.attr.state_checkable
    };

    private final static int[] KEY_STATE_PRESSED_HIGHLIGHT_OFF = {
        android.R.attr.state_pressed,
        android.R.attr.state_checkable
    };

    private final static int[] KEY_STATE_NORMAL = {
    };

    private final static int[] KEY_STATE_PRESSED = {
        android.R.attr.state_pressed
    };

    // functional normal state (with properties)
    private static final int[] KEY_STATE_FUNCTIONAL_NORMAL = {
            android.R.attr.state_single
    };

    // functional pressed state (with properties)
    private static final int[] KEY_STATE_FUNCTIONAL_PRESSED = {
            android.R.attr.state_single,
            android.R.attr.state_pressed
    };

    // action normal state (with properties)
    private static final int[] KEY_STATE_ACTIVE_NORMAL = {
            android.R.attr.state_active
    };

    // action pressed state (with properties)
    private static final int[] KEY_STATE_ACTIVE_PRESSED = {
            android.R.attr.state_active,
            android.R.attr.state_pressed
    };

    /**
     * Returns the drawable state for the key, based on the current state and type of the key.
     * @return the drawable state of the key.
     * @see android.graphics.drawable.StateListDrawable#setState(int[])
     */
    public final int[] getCurrentDrawableState() {
        switch (mBackgroundType) {
        case BACKGROUND_TYPE_FUNCTIONAL:
            return mPressed ? KEY_STATE_FUNCTIONAL_PRESSED : KEY_STATE_FUNCTIONAL_NORMAL;
        case BACKGROUND_TYPE_ACTION:
            return mPressed ? KEY_STATE_ACTIVE_PRESSED : KEY_STATE_ACTIVE_NORMAL;
        case BACKGROUND_TYPE_STICKY_OFF:
            return mPressed ? KEY_STATE_PRESSED_HIGHLIGHT_OFF : KEY_STATE_NORMAL_HIGHLIGHT_OFF;
        case BACKGROUND_TYPE_STICKY_ON:
            return mPressed ? KEY_STATE_PRESSED_HIGHLIGHT_ON : KEY_STATE_NORMAL_HIGHLIGHT_ON;
        default: /* BACKGROUND_TYPE_NORMAL */
            return mPressed ? KEY_STATE_PRESSED : KEY_STATE_NORMAL;
        }
    }

    public static class Spacer extends Key {
        public Spacer(final Resources res, final KeyboardParams params, final KeyboardRow row,
                final XmlPullParser parser) throws XmlPullParserException {
            super(res, params, row, parser);
        }

        /**
         * This constructor is being used only for divider in more keys keyboard.
         */
        protected Spacer(final KeyboardParams params, final int x, final int y, final int width,
                final int height) {
            super(params, null, null, ICON_UNDEFINED, CODE_UNSPECIFIED,
                    null, x, y, width, height, 0);
        }
    }
}
