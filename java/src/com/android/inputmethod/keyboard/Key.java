/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.android.inputmethod.keyboard.internal.KeyStyles;
import com.android.inputmethod.keyboard.internal.KeyStyles.KeyStyle;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.MoreKeySpecParser;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.XmlParseUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Class for describing the position and characteristics of a single key in the keyboard.
 */
public class Key {
    private static final String TAG = Key.class.getSimpleName();

    /**
     * The key code (unicode or custom code) that this key generates.
     */
    public final int mCode;
    public final int mAltCode;

    /** Label to display */
    public final String mLabel;
    /** Hint label to display on the key in conjunction with the label */
    public final String mHintLabel;
    /** Flags of the label */
    private final int mLabelFlags;
    private static final int LABEL_FLAGS_ALIGN_LEFT = 0x01;
    private static final int LABEL_FLAGS_ALIGN_RIGHT = 0x02;
    private static final int LABEL_FLAGS_ALIGN_LEFT_OF_CENTER = 0x08;
    private static final int LABEL_FLAGS_LARGE_LETTER = 0x10;
    private static final int LABEL_FLAGS_FONT_NORMAL = 0x20;
    private static final int LABEL_FLAGS_FONT_MONO_SPACE = 0x40;
    private static final int LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO = 0x80;
    private static final int LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO = 0x100;
    private static final int LABEL_FLAGS_HAS_POPUP_HINT = 0x200;
    private static final int LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT = 0x400;
    private static final int LABEL_FLAGS_HAS_HINT_LABEL = 0x800;
    private static final int LABEL_FLAGS_WITH_ICON_LEFT = 0x1000;
    private static final int LABEL_FLAGS_WITH_ICON_RIGHT = 0x2000;
    private static final int LABEL_FLAGS_AUTO_X_SCALE = 0x4000;
    private static final int LABEL_FLAGS_PRESERVE_CASE = 0x8000;
    private static final int LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED = 0x10000;

    /** Icon to display instead of a label. Icon takes precedence over a label */
    private final int mIconAttrId;
    // TODO: Remove this variable.
    private Drawable mIcon;
    /** Icon for disabled state */
    private final int mDisabledIconAttrId;
    /** Preview version of the icon, for the preview popup */
    public final int mPreviewIconAttrId;

    /** Width of the key, not including the gap */
    public final int mWidth;
    /** Height of the key, not including the gap */
    public final int mHeight;
    /** The horizontal gap around this key */
    public final int mHorizontalGap;
    /** The vertical gap below this key */
    public final int mVerticalGap;
    /** The visual insets */
    public final int mVisualInsetsLeft;
    public final int mVisualInsetsRight;
    /** X coordinate of the key in the keyboard layout */
    public final int mX;
    /** Y coordinate of the key in the keyboard layout */
    public final int mY;
    /** Hit bounding box of the key */
    public final Rect mHitBox = new Rect();

    /** Text to output when pressed. This can be multiple characters, like ".com" */
    public final CharSequence mOutputText;
    /** More keys */
    public final String[] mMoreKeys;
    /** More keys maximum column number */
    public final int mMaxMoreKeysColumn;

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

    private final int mHashCode;

    /** The current pressed state of this key */
    private boolean mPressed;
    /** Key is enabled and responds on press */
    private boolean mEnabled = true;

    // RTL parenthesis character swapping map.
    private static final Map<Integer, Integer> sRtlParenthesisMap = new HashMap<Integer, Integer>();

    static {
        // The all letters need to be mirrored are found at
        // http://www.unicode.org/Public/6.0.0/ucd/extracted/DerivedBinaryProperties.txt
        addRtlParenthesisPair('(', ')');
        addRtlParenthesisPair('[', ']');
        addRtlParenthesisPair('{', '}');
        addRtlParenthesisPair('<', '>');
        // \u00ab: LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
        // \u00bb: RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK
        addRtlParenthesisPair('\u00ab', '\u00bb');
        // \u2039: SINGLE LEFT-POINTING ANGLE QUOTATION MARK
        // \u203a: SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
        addRtlParenthesisPair('\u2039', '\u203a');
        // \u2264: LESS-THAN OR EQUAL TO
        // \u2265: GREATER-THAN OR EQUAL TO
        addRtlParenthesisPair('\u2264', '\u2265');
    }

    private static void addRtlParenthesisPair(int left, int right) {
        sRtlParenthesisMap.put(left, right);
        sRtlParenthesisMap.put(right, left);
    }

    public static int getRtlParenthesisCode(int code, boolean isRtl) {
        if (isRtl && sRtlParenthesisMap.containsKey(code)) {
            return sRtlParenthesisMap.get(code);
        } else {
            return code;
        }
    }

    private static int getCode(Resources res, Keyboard.Params params, String moreKeySpec) {
        return getRtlParenthesisCode(
                MoreKeySpecParser.getCode(res, moreKeySpec), params.mIsRtlKeyboard);
    }

    private static Drawable getIcon(Keyboard.Params params, String moreKeySpec) {
        final int iconAttrId = MoreKeySpecParser.getIconAttrId(moreKeySpec);
        if (iconAttrId == KeyboardIconsSet.ICON_UNDEFINED) {
            return null;
        } else {
            return params.mIconsSet.getIconByAttrId(iconAttrId);
        }
    }

    /**
     * This constructor is being used only for key in more keys keyboard.
     */
    public Key(Resources res, Keyboard.Params params, String moreKeySpec,
            int x, int y, int width, int height) {
        this(params, MoreKeySpecParser.getLabel(moreKeySpec), null, getIcon(params, moreKeySpec),
                getCode(res, params, moreKeySpec), MoreKeySpecParser.getOutputText(moreKeySpec),
                x, y, width, height);
    }

    /**
     * This constructor is being used only for key in popup suggestions pane.
     */
    public Key(Keyboard.Params params, String label, String hintLabel, Drawable icon,
            int code, CharSequence outputText, int x, int y, int width, int height) {
        mHeight = height - params.mVerticalGap;
        mHorizontalGap = params.mHorizontalGap;
        mVerticalGap = params.mVerticalGap;
        mVisualInsetsLeft = mVisualInsetsRight = 0;
        mWidth = width - mHorizontalGap;
        mHintLabel = hintLabel;
        mLabelFlags = 0;
        mBackgroundType = BACKGROUND_TYPE_NORMAL;
        mActionFlags = 0;
        mMoreKeys = null;
        mMaxMoreKeysColumn = 0;
        mLabel = label;
        mOutputText = outputText;
        mCode = code;
        mAltCode = Keyboard.CODE_UNSPECIFIED;
        mIconAttrId = KeyboardIconsSet.ATTR_UNDEFINED;
        mIcon = icon;
        mDisabledIconAttrId = KeyboardIconsSet.ATTR_UNDEFINED;
        mPreviewIconAttrId = KeyboardIconsSet.ATTR_UNDEFINED;
        // Horizontal gap is divided equally to both sides of the key.
        mX = x + mHorizontalGap / 2;
        mY = y;
        mHitBox.set(x, y, x + width + 1, y + height);

        mHashCode = hashCode(this);
    }

    /**
     * Create a key with the given top-left coordinate and extract its attributes from the XML
     * parser.
     * @param res resources associated with the caller's context
     * @param params the keyboard building parameters.
     * @param row the row that this key belongs to. row's x-coordinate will be the right edge of
     *        this key.
     * @param parser the XML parser containing the attributes for this key
     * @param keyStyles active key styles set
     * @throws XmlPullParserException
     */
    public Key(Resources res, Keyboard.Params params, Keyboard.Builder.Row row,
            XmlPullParser parser, KeyStyles keyStyles) throws XmlPullParserException {
        final float horizontalGap = isSpacer() ? 0 : params.mHorizontalGap;
        final int keyHeight = row.mRowHeight;
        mVerticalGap = params.mVerticalGap;
        mHeight = keyHeight - mVerticalGap;

        final TypedArray keyAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key);

        final KeyStyle style;
        if (keyAttr.hasValue(R.styleable.Keyboard_Key_keyStyle)) {
            String styleName = keyAttr.getString(R.styleable.Keyboard_Key_keyStyle);
            style = keyStyles.getKeyStyle(styleName);
            if (style == null)
                throw new XmlParseUtils.ParseException(
                        "Unknown key style: " + styleName, parser);
        } else {
            style = KeyStyles.getEmptyKeyStyle();
        }

        final float keyXPos = row.getKeyX(keyAttr);
        final float keyWidth = row.getKeyWidth(keyAttr, keyXPos);
        final int keyYPos = row.getKeyY();

        // Horizontal gap is divided equally to both sides of the key.
        mX = (int) (keyXPos + horizontalGap / 2);
        mY = keyYPos;
        mWidth = (int) (keyWidth - horizontalGap);
        mHorizontalGap = (int) horizontalGap;
        mHitBox.set((int)keyXPos, keyYPos, (int)(keyXPos + keyWidth) + 1, keyYPos + keyHeight);
        // Update row to have current x coordinate.
        row.setXPos(keyXPos + keyWidth);

        mBackgroundType = style.getInt(keyAttr,
                R.styleable.Keyboard_Key_backgroundType, BACKGROUND_TYPE_NORMAL);

        final KeyboardIconsSet iconsSet = params.mIconsSet;
        mVisualInsetsLeft = (int) Keyboard.Builder.getDimensionOrFraction(keyAttr,
                R.styleable.Keyboard_Key_visualInsetsLeft, params.mBaseWidth, 0);
        mVisualInsetsRight = (int) Keyboard.Builder.getDimensionOrFraction(keyAttr,
                R.styleable.Keyboard_Key_visualInsetsRight, params.mBaseWidth, 0);
        mPreviewIconAttrId = KeyboardIconsSet.getIconAttrId(style.getInt(keyAttr,
                R.styleable.Keyboard_Key_keyIconPreview, KeyboardIconsSet.ICON_UNDEFINED));
        mIconAttrId = KeyboardIconsSet.getIconAttrId(style.getInt(keyAttr,
                R.styleable.Keyboard_Key_keyIcon, KeyboardIconsSet.ICON_UNDEFINED));
        mIcon = iconsSet.getIconByAttrId(mIconAttrId);
        mDisabledIconAttrId = KeyboardIconsSet.getIconAttrId(style.getInt(keyAttr,
                R.styleable.Keyboard_Key_keyIconDisabled, KeyboardIconsSet.ICON_UNDEFINED));

        mLabelFlags = style.getFlag(keyAttr, R.styleable.Keyboard_Key_keyLabelFlags, 0);
        final boolean preserveCase = (mLabelFlags & LABEL_FLAGS_PRESERVE_CASE) != 0;
        int actionFlags = style.getFlag(keyAttr, R.styleable.Keyboard_Key_keyActionFlags, 0);
        final String[] additionalMoreKeys = style.getStringArray(
                keyAttr, R.styleable.Keyboard_Key_additionalMoreKeys);
        final String[] moreKeys = MoreKeySpecParser.insertAddtionalMoreKeys(style.getStringArray(
                keyAttr, R.styleable.Keyboard_Key_moreKeys), additionalMoreKeys);
        if (moreKeys != null) {
            actionFlags |= ACTION_FLAGS_ENABLE_LONG_PRESS;
            for (int i = 0; i < moreKeys.length; i++) {
                moreKeys[i] = adjustCaseOfStringForKeyboardId(
                        moreKeys[i], preserveCase, params.mId);
            }
        }
        mActionFlags = actionFlags;
        mMoreKeys = moreKeys;
        mMaxMoreKeysColumn = style.getInt(keyAttr,
                R.styleable.Keyboard_Key_maxMoreKeysColumn, params.mMaxMiniKeyboardColumn);

        mLabel = adjustCaseOfStringForKeyboardId(style.getString(
                keyAttr, R.styleable.Keyboard_Key_keyLabel), preserveCase, params.mId);
        mHintLabel = adjustCaseOfStringForKeyboardId(style.getString(
                keyAttr, R.styleable.Keyboard_Key_keyHintLabel), preserveCase, params.mId);
        String outputText = adjustCaseOfStringForKeyboardId(style.getString(
                keyAttr, R.styleable.Keyboard_Key_keyOutputText), preserveCase, params.mId);
        final int code = style.getInt(
                keyAttr, R.styleable.Keyboard_Key_code, Keyboard.CODE_UNSPECIFIED);
        // Choose the first letter of the label as primary code if not specified.
        if (code == Keyboard.CODE_UNSPECIFIED && TextUtils.isEmpty(outputText)
                && !TextUtils.isEmpty(mLabel)) {
            if (mLabel.codePointCount(0, mLabel.length()) == 1) {
                final int activatedCode;
                // Use the first letter of the hint label if shiftedLetterActivated flag is
                // specified.
                if (hasShiftedLetterHint() && isShiftedLetterActivated()
                        && !TextUtils.isEmpty(mHintLabel)) {
                    activatedCode = mHintLabel.codePointAt(0);
                } else {
                    activatedCode = mLabel.codePointAt(0);
                }
                mCode = getRtlParenthesisCode(activatedCode, params.mIsRtlKeyboard);
            } else {
                // In some locale and case, the character might be represented by multiple code
                // points, such as upper case Eszett of German alphabet.
                outputText = mLabel;
                mCode = Keyboard.CODE_OUTPUT_TEXT;
            }
        } else if (code == Keyboard.CODE_UNSPECIFIED && outputText != null) {
            mCode = Keyboard.CODE_OUTPUT_TEXT;
        } else {
            mCode = adjustCaseOfCodeForKeyboardId(code, preserveCase, params.mId);
        }
        mOutputText = outputText;
        mAltCode = adjustCaseOfCodeForKeyboardId(style.getInt(keyAttr,
                R.styleable.Keyboard_Key_altCode, Keyboard.CODE_UNSPECIFIED), preserveCase,
                params.mId);
        mHashCode = hashCode(this);

        keyAttr.recycle();

        if (hasShiftedLetterHint() && TextUtils.isEmpty(mHintLabel)) {
            Log.w(TAG, "hasShiftedLetterHint specified without keyHintLabel: " + this);
        }
    }

    private static int adjustCaseOfCodeForKeyboardId(int code, boolean preserveCase,
            KeyboardId id) {
        if (!Keyboard.isLetterCode(code) || preserveCase) return code;
        final String text = new String(new int[] { code } , 0, 1);
        final String casedText = adjustCaseOfStringForKeyboardId(text, preserveCase, id);
        return casedText.codePointCount(0, casedText.length()) == 1
                ? casedText.codePointAt(0) : Keyboard.CODE_UNSPECIFIED;
    }

    private static String adjustCaseOfStringForKeyboardId(String text, boolean preserveCase,
            KeyboardId id) {
        if (text == null || preserveCase) return text;
        switch (id.mElementId) {
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
            return text.toUpperCase(id.mLocale);
        default:
            return text;
        }
    }

    private static int hashCode(Key key) {
        return Arrays.hashCode(new Object[] {
                key.mX,
                key.mY,
                key.mWidth,
                key.mHeight,
                key.mCode,
                key.mLabel,
                key.mHintLabel,
                key.mIconAttrId,
                key.mBackgroundType,
                // Key can be distinguishable without the following members.
                // key.mAltCode,
                // key.mOutputText,
                // key.mActionFlags,
                // key.mLabelFlags,
                // key.mIcon,
                // key.mDisabledIconAttrId,
                // key.mPreviewIconAttrId,
                // key.mHorizontalGap,
                // key.mVerticalGap,
                // key.mVisualInsetLeft,
                // key.mVisualInsetRight,
                // Arrays.hashCode(key.mMoreKeys),
                // key.mMaxMoreKeysColumn,
        });
    }

    private boolean equals(Key o) {
        if (this == o) return true;
        return o.mX == mX
                && o.mY == mY
                && o.mWidth == mWidth
                && o.mHeight == mHeight
                && o.mCode == mCode
                && TextUtils.equals(o.mLabel, mLabel)
                && TextUtils.equals(o.mHintLabel, mHintLabel)
                && o.mIconAttrId != mIconAttrId
                && o.mBackgroundType != mBackgroundType;
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Key && equals((Key)o);
    }

    @Override
    public String toString() {
        String top = Keyboard.printableCode(mCode);
        if (mLabel != null && mLabel.codePointCount(0, mLabel.length()) != 1) {
            top += "/\"" + mLabel + '"';
        }
        return String.format("%s %d,%d", top, mX, mY);
    }

    public void markAsLeftEdge(Keyboard.Params params) {
        mHitBox.left = params.mHorizontalEdgesPadding;
    }

    public void markAsRightEdge(Keyboard.Params params) {
        mHitBox.right = params.mOccupiedWidth - params.mHorizontalEdgesPadding;
    }

    public void markAsTopEdge(Keyboard.Params params) {
        mHitBox.top = params.mTopPadding;
    }

    public void markAsBottomEdge(Keyboard.Params params) {
        mHitBox.bottom = params.mOccupiedHeight + params.mBottomPadding;
    }

    public boolean isSpacer() {
        return false;
    }

    public boolean isShift() {
        return mCode == Keyboard.CODE_SHIFT;
    }

    public boolean isModifier() {
        return mCode == Keyboard.CODE_SHIFT || mCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL;
    }

    public boolean isRepeatable() {
        return (mActionFlags & ACTION_FLAGS_IS_REPEATABLE) != 0;
    }

    public boolean noKeyPreview() {
        return (mActionFlags & ACTION_FLAGS_NO_KEY_PREVIEW) != 0;
    }

    public boolean altCodeWhileTyping() {
        return (mActionFlags & ACTION_FLAGS_ALT_CODE_WHILE_TYPING) != 0;
    }

    public boolean isLongPressEnabled() {
        // We need not start long press timer on the key which has activated shifted letter.
        return (mActionFlags & ACTION_FLAGS_ENABLE_LONG_PRESS) != 0
                && (mLabelFlags & LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) == 0;
    }

    public Typeface selectTypeface(Typeface defaultTypeface) {
        // TODO: Handle "bold" here too?
        if ((mLabelFlags & LABEL_FLAGS_FONT_NORMAL) != 0) {
            return Typeface.DEFAULT;
        } else if ((mLabelFlags & LABEL_FLAGS_FONT_MONO_SPACE) != 0) {
            return Typeface.MONOSPACE;
        } else {
            return defaultTypeface;
        }
    }

    public int selectTextSize(int letter, int largeLetter, int label, int hintLabel) {
        if (mLabel.length() > 1
                && (mLabelFlags & (LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO
                        | LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO)) == 0) {
            return label;
        } else if ((mLabelFlags & LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO) != 0) {
            return hintLabel;
        } else if ((mLabelFlags & LABEL_FLAGS_LARGE_LETTER) != 0) {
            return largeLetter;
        } else {
            return letter;
        }
    }

    public boolean isAlignLeft() {
        return (mLabelFlags & LABEL_FLAGS_ALIGN_LEFT) != 0;
    }

    public boolean isAlignRight() {
        return (mLabelFlags & LABEL_FLAGS_ALIGN_RIGHT) != 0;
    }

    public boolean isAlignLeftOfCenter() {
        return (mLabelFlags & LABEL_FLAGS_ALIGN_LEFT_OF_CENTER) != 0;
    }

    public boolean hasPopupHint() {
        return (mLabelFlags & LABEL_FLAGS_HAS_POPUP_HINT) != 0;
    }

    public boolean hasShiftedLetterHint() {
        return (mLabelFlags & LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT) != 0;
    }

    public boolean hasHintLabel() {
        return (mLabelFlags & LABEL_FLAGS_HAS_HINT_LABEL) != 0;
    }

    public boolean hasLabelWithIconLeft() {
        return (mLabelFlags & LABEL_FLAGS_WITH_ICON_LEFT) != 0;
    }

    public boolean hasLabelWithIconRight() {
        return (mLabelFlags & LABEL_FLAGS_WITH_ICON_RIGHT) != 0;
    }

    public boolean needsXScale() {
        return (mLabelFlags & LABEL_FLAGS_AUTO_X_SCALE) != 0;
    }

    public boolean isShiftedLetterActivated() {
        return (mLabelFlags & LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) != 0;
    }

    // TODO: Get rid of this method.
    public Drawable getIcon(KeyboardIconsSet iconSet) {
        return mEnabled ? mIcon : iconSet.getIconByAttrId(mDisabledIconAttrId);
    }

    // TODO: Get rid of this method.
    public void setIcon(Drawable icon) {
        mIcon = icon;
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

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    /**
     * Detects if a point falls on this key.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether or not the point falls on the key. If the key is attached to an edge, it
     * will assume that all points between the key and the edge are considered to be on the key.
     * @see #markAsLeftEdge(Keyboard.Params) etc.
     */
    public boolean isOnKey(int x, int y) {
        return mHitBox.contains(x, y);
    }

    /**
     * Returns the square of the distance to the nearest edge of the key and the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the square of the distance of the point from the nearest edge of the key
     */
    public int squaredDistanceToEdge(int x, int y) {
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
    public int[] getCurrentDrawableState() {
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
        public Spacer(Resources res, Keyboard.Params params, Keyboard.Builder.Row row,
                XmlPullParser parser, KeyStyles keyStyles) throws XmlPullParserException {
            super(res, params, row, parser, keyStyles);
        }

        /**
         * This constructor is being used only for divider in more keys keyboard.
         */
        public Spacer(Keyboard.Params params, Drawable icon, int x, int y, int width, int height) {
            super(params, null, null, icon, Keyboard.CODE_UNSPECIFIED, null, x, y, width, height);
        }

        @Override
        public boolean isSpacer() {
            return true;
        }
    }
}
