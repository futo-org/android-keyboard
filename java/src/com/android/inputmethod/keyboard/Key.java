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
import android.util.Xml;

import com.android.inputmethod.keyboard.internal.KeyStyles;
import com.android.inputmethod.keyboard.internal.KeyStyles.KeyStyle;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder.ParseException;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.keyboard.internal.MoreKeySpecParser;
import com.android.inputmethod.latin.R;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Class for describing the position and characteristics of a single key in the keyboard.
 */
public class Key {
    /**
     * The key code (unicode or custom code) that this key generates.
     */
    public final int mCode;

    /** Label to display */
    public final CharSequence mLabel;
    /** Hint label to display on the key in conjunction with the label */
    public final CharSequence mHintLabel;
    /** Option of the label */
    private final int mLabelOption;
    private static final int LABEL_OPTION_ALIGN_LEFT = 0x01;
    private static final int LABEL_OPTION_ALIGN_RIGHT = 0x02;
    private static final int LABEL_OPTION_ALIGN_LEFT_OF_CENTER = 0x08;
    private static final int LABEL_OPTION_LARGE_LETTER = 0x10;
    private static final int LABEL_OPTION_FONT_NORMAL = 0x20;
    private static final int LABEL_OPTION_FONT_MONO_SPACE = 0x40;
    private static final int LABEL_OPTION_FOLLOW_KEY_LETTER_RATIO = 0x80;
    private static final int LABEL_OPTION_FOLLOW_KEY_HINT_LABEL_RATIO = 0x100;
    private static final int LABEL_OPTION_HAS_POPUP_HINT = 0x200;
    private static final int LABEL_OPTION_HAS_UPPERCASE_LETTER = 0x400;
    private static final int LABEL_OPTION_HAS_HINT_LABEL = 0x800;
    private static final int LABEL_OPTION_WITH_ICON_LEFT = 0x1000;
    private static final int LABEL_OPTION_WITH_ICON_RIGHT = 0x2000;
    private static final int LABEL_OPTION_AUTO_X_SCALE = 0x4000;

    /** Icon to display instead of a label. Icon takes precedence over a label */
    private Drawable mIcon;
    /** Preview version of the icon, for the preview popup */
    private Drawable mPreviewIcon;

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
    public final CharSequence[] mMoreKeys;
    /** More keys maximum column number */
    public final int mMaxMoreKeysColumn;

    /** Background type that represents different key background visual than normal one. */
    public final int mBackgroundType;
    public static final int BACKGROUND_TYPE_NORMAL = 0;
    public static final int BACKGROUND_TYPE_FUNCTIONAL = 1;
    public static final int BACKGROUND_TYPE_ACTION = 2;
    public static final int BACKGROUND_TYPE_STICKY = 3;

    /** Whether this key repeats itself when held down */
    public final boolean mRepeatable;

    /** The current pressed state of this key */
    private boolean mPressed;
    /** If this is a sticky key, is its highlight on? */
    private boolean mHighlightOn;
    /** Key is enabled and responds on press */
    private boolean mEnabled = true;
    /** Whether this key needs to show the "..." popup hint for special purposes */
    private boolean mNeedsSpecialPopupHint;

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

    private static int getCode(Resources res, KeyboardParams params, String moreKeySpec) {
        return getRtlParenthesisCode(
                MoreKeySpecParser.getCode(res, moreKeySpec), params.mIsRtlKeyboard);
    }

    private static Drawable getIcon(KeyboardParams params, String moreKeySpec) {
        return params.mIconsSet.getIcon(MoreKeySpecParser.getIconId(moreKeySpec));
    }

    /**
     * This constructor is being used only for key in more keys keyboard.
     */
    public Key(Resources res, KeyboardParams params, String moreKeySpec,
            int x, int y, int width, int height) {
        this(params, MoreKeySpecParser.getLabel(moreKeySpec), null, getIcon(params, moreKeySpec),
                getCode(res, params, moreKeySpec), MoreKeySpecParser.getOutputText(moreKeySpec),
                x, y, width, height);
    }

    /**
     * This constructor is being used only for key in popup suggestions pane.
     */
    public Key(KeyboardParams params, CharSequence label, CharSequence hintLabel, Drawable icon,
            int code, CharSequence outputText, int x, int y, int width, int height) {
        mHeight = height - params.mVerticalGap;
        mHorizontalGap = params.mHorizontalGap;
        mVerticalGap = params.mVerticalGap;
        mVisualInsetsLeft = mVisualInsetsRight = 0;
        mWidth = width - mHorizontalGap;
        mHintLabel = hintLabel;
        mLabelOption = 0;
        mBackgroundType = BACKGROUND_TYPE_NORMAL;
        mRepeatable = false;
        mMoreKeys = null;
        mMaxMoreKeysColumn = 0;
        mLabel = label;
        mOutputText = outputText;
        mCode = code;
        mIcon = icon;
        // Horizontal gap is divided equally to both sides of the key.
        mX = x + mHorizontalGap / 2;
        mY = y;
        mHitBox.set(x, y, x + width + 1, y + height);
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
     */
    public Key(Resources res, KeyboardParams params, KeyboardBuilder.Row row,
            XmlPullParser parser, KeyStyles keyStyles) {
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
                throw new ParseException("Unknown key style: " + styleName, parser);
        } else {
            style = keyStyles.getEmptyKeyStyle();
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

        final CharSequence[] moreKeys = style.getTextArray(keyAttr,
                R.styleable.Keyboard_Key_moreKeys);
        // In Arabic symbol layouts, we'd like to keep digits in more keys regardless of
        // config_digit_more_keys_enabled.
        if (params.mId.isAlphabetKeyboard()
                && !res.getBoolean(R.bool.config_digit_more_keys_enabled)) {
            mMoreKeys = MoreKeySpecParser.filterOut(res, moreKeys, MoreKeySpecParser.DIGIT_FILTER);
        } else {
            mMoreKeys = moreKeys;
        }
        mMaxMoreKeysColumn = style.getInt(keyAttr,
                R.styleable.Keyboard_Key_maxMoreKeysColumn, params.mMaxMiniKeyboardColumn);

        mBackgroundType = style.getInt(keyAttr,
                R.styleable.Keyboard_Key_backgroundType, BACKGROUND_TYPE_NORMAL);
        mRepeatable = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_isRepeatable, false);
        mEnabled = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_enabled, true);

        final KeyboardIconsSet iconsSet = params.mIconsSet;
        mVisualInsetsLeft = (int) KeyboardBuilder.getDimensionOrFraction(keyAttr,
                R.styleable.Keyboard_Key_visualInsetsLeft, params.mBaseWidth, 0);
        mVisualInsetsRight = (int) KeyboardBuilder.getDimensionOrFraction(keyAttr,
                R.styleable.Keyboard_Key_visualInsetsRight, params.mBaseWidth, 0);
        mPreviewIcon = iconsSet.getIcon(style.getInt(keyAttr,
                R.styleable.Keyboard_Key_keyIconPreview, KeyboardIconsSet.ICON_UNDEFINED));
        mIcon = iconsSet.getIcon(style.getInt(keyAttr, R.styleable.Keyboard_Key_keyIcon,
                KeyboardIconsSet.ICON_UNDEFINED));
        final int shiftedIconId = style.getInt(keyAttr, R.styleable.Keyboard_Key_keyIconShifted,
                KeyboardIconsSet.ICON_UNDEFINED);
        if (shiftedIconId != KeyboardIconsSet.ICON_UNDEFINED) {
            final Drawable shiftedIcon = iconsSet.getIcon(shiftedIconId);
            params.addShiftedIcon(this, shiftedIcon);
        }
        mHintLabel = style.getText(keyAttr, R.styleable.Keyboard_Key_keyHintLabel);

        mLabel = style.getText(keyAttr, R.styleable.Keyboard_Key_keyLabel);
        mLabelOption = style.getFlag(keyAttr, R.styleable.Keyboard_Key_keyLabelOption, 0);
        mOutputText = style.getText(keyAttr, R.styleable.Keyboard_Key_keyOutputText);
        // Choose the first letter of the label as primary code if not
        // specified.
        final int code = style.getInt(keyAttr, R.styleable.Keyboard_Key_code,
                Keyboard.CODE_UNSPECIFIED);
        if (code == Keyboard.CODE_UNSPECIFIED && !TextUtils.isEmpty(mLabel)) {
            final int firstChar = mLabel.charAt(0);
            mCode = getRtlParenthesisCode(firstChar, params.mIsRtlKeyboard);
        } else if (code != Keyboard.CODE_UNSPECIFIED) {
            mCode = code;
        } else {
            mCode = Keyboard.CODE_DUMMY;
        }

        keyAttr.recycle();
    }

    public void markAsLeftEdge(KeyboardParams params) {
        mHitBox.left = params.mHorizontalEdgesPadding;
    }

    public void markAsRightEdge(KeyboardParams params) {
        mHitBox.right = params.mOccupiedWidth - params.mHorizontalEdgesPadding;
    }

    public void markAsTopEdge(KeyboardParams params) {
        mHitBox.top = params.mTopPadding;
    }

    public void markAsBottomEdge(KeyboardParams params) {
        mHitBox.bottom = params.mOccupiedHeight + params.mBottomPadding;
    }

    public boolean isSticky() {
        return mBackgroundType == BACKGROUND_TYPE_STICKY;
    }

    public boolean isSpacer() {
        return false;
    }

    public Typeface selectTypeface(Typeface defaultTypeface) {
        // TODO: Handle "bold" here too?
        if ((mLabelOption & LABEL_OPTION_FONT_NORMAL) != 0) {
            return Typeface.DEFAULT;
        } else if ((mLabelOption & LABEL_OPTION_FONT_MONO_SPACE) != 0) {
            return Typeface.MONOSPACE;
        } else {
            return defaultTypeface;
        }
    }

    public int selectTextSize(int letter, int largeLetter, int label, int hintLabel) {
        if (mLabel.length() > 1
                && (mLabelOption & (LABEL_OPTION_FOLLOW_KEY_LETTER_RATIO
                        | LABEL_OPTION_FOLLOW_KEY_HINT_LABEL_RATIO)) == 0) {
            return label;
        } else if ((mLabelOption & LABEL_OPTION_FOLLOW_KEY_HINT_LABEL_RATIO) != 0) {
            return hintLabel;
        } else if ((mLabelOption & LABEL_OPTION_LARGE_LETTER) != 0) {
            return largeLetter;
        } else {
            return letter;
        }
    }

    public boolean isAlignLeft() {
        return (mLabelOption & LABEL_OPTION_ALIGN_LEFT) != 0;
    }

    public boolean isAlignRight() {
        return (mLabelOption & LABEL_OPTION_ALIGN_RIGHT) != 0;
    }

    public boolean isAlignLeftOfCenter() {
        return (mLabelOption & LABEL_OPTION_ALIGN_LEFT_OF_CENTER) != 0;
    }

    public boolean hasPopupHint() {
        return (mLabelOption & LABEL_OPTION_HAS_POPUP_HINT) != 0;
    }

    public void setNeedsSpecialPopupHint(boolean needsSpecialPopupHint) {
        mNeedsSpecialPopupHint = needsSpecialPopupHint;
    }

    public boolean needsSpecialPopupHint() {
        return mNeedsSpecialPopupHint;
    }

    public boolean hasUppercaseLetter() {
        return (mLabelOption & LABEL_OPTION_HAS_UPPERCASE_LETTER) != 0;
    }

    public boolean hasHintLabel() {
        return (mLabelOption & LABEL_OPTION_HAS_HINT_LABEL) != 0;
    }

    public boolean hasLabelWithIconLeft() {
        return (mLabelOption & LABEL_OPTION_WITH_ICON_LEFT) != 0;
    }

    public boolean hasLabelWithIconRight() {
        return (mLabelOption & LABEL_OPTION_WITH_ICON_RIGHT) != 0;
    }

    public boolean needsXScale() {
        return (mLabelOption & LABEL_OPTION_AUTO_X_SCALE) != 0;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public Drawable getPreviewIcon() {
        return mPreviewIcon;
    }

    public void setIcon(Drawable icon) {
        mIcon = icon;
    }

    public void setPreviewIcon(Drawable icon) {
        mPreviewIcon = icon;
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

    public void setHighlightOn(boolean highlightOn) {
        mHighlightOn = highlightOn;
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
     * @return whether or not the point falls on the key. If the key is attached to an edge, it will
     * assume that all points between the key and the edge are considered to be on the key.
     * @see {@link #markAsLeftEdge(KeyboardParams)} etc.
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
        final boolean pressed = mPressed;

        switch (mBackgroundType) {
        case BACKGROUND_TYPE_FUNCTIONAL:
            return pressed ? KEY_STATE_FUNCTIONAL_PRESSED : KEY_STATE_FUNCTIONAL_NORMAL;
        case BACKGROUND_TYPE_ACTION:
            return pressed ? KEY_STATE_ACTIVE_PRESSED : KEY_STATE_ACTIVE_NORMAL;
        case BACKGROUND_TYPE_STICKY:
            if (mHighlightOn) {
                return pressed ? KEY_STATE_PRESSED_HIGHLIGHT_ON : KEY_STATE_NORMAL_HIGHLIGHT_ON;
            } else {
                return pressed ? KEY_STATE_PRESSED_HIGHLIGHT_OFF : KEY_STATE_NORMAL_HIGHLIGHT_OFF;
            }
        default: /* BACKGROUND_TYPE_NORMAL */
            return pressed ? KEY_STATE_PRESSED : KEY_STATE_NORMAL;
        }
    }

    public static class Spacer extends Key {
        public Spacer(Resources res, KeyboardParams params, KeyboardBuilder.Row row,
                XmlPullParser parser, KeyStyles keyStyles) {
            super(res, params, row, parser, keyStyles);
        }

        /**
         * This constructor is being used only for divider in more keys keyboard.
         */
        public Spacer(KeyboardParams params, Drawable icon, int x, int y, int width, int height) {
            super(params, null, null, icon, Keyboard.CODE_DUMMY, null, x, y, width, height);
        }

        @Override
        public boolean isSpacer() {
            return true;
        }
    }
}
