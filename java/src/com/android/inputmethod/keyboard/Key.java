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
import android.content.res.XmlResourceParser;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Xml;

import com.android.inputmethod.keyboard.internal.KeyStyles;
import com.android.inputmethod.keyboard.internal.KeyStyles.KeyStyle;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.keyboard.internal.KeyboardParser;
import com.android.inputmethod.keyboard.internal.KeyboardParser.ParseException;
import com.android.inputmethod.keyboard.internal.PopupCharactersParser;
import com.android.inputmethod.keyboard.internal.Row;
import com.android.inputmethod.latin.R;

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
    public final int mLabelOption;
    public static final int LABEL_OPTION_ALIGN_LEFT = 0x01;
    public static final int LABEL_OPTION_ALIGN_RIGHT = 0x02;
    public static final int LABEL_OPTION_ALIGN_LEFT_OF_CENTER = 0x08;
    private static final int LABEL_OPTION_LARGE_LETTER = 0x10;
    private static final int LABEL_OPTION_FONT_NORMAL = 0x20;
    private static final int LABEL_OPTION_FONT_MONO_SPACE = 0x40;
    private static final int LABEL_OPTION_FOLLOW_KEY_LETTER_RATIO = 0x80;
    private static final int LABEL_OPTION_FOLLOW_KEY_HINT_LABEL_RATIO = 0x100;
    private static final int LABEL_OPTION_HAS_POPUP_HINT = 0x200;
    private static final int LABEL_OPTION_HAS_UPPERCASE_LETTER = 0x400;
    private static final int LABEL_OPTION_HAS_HINT_LABEL = 0x800;

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
    /** Whether this key is sticky, i.e., a toggle key */
    public final boolean mSticky;
    /** X coordinate of the key in the keyboard layout */
    public final int mX;
    /** Y coordinate of the key in the keyboard layout */
    public final int mY;
    /** Text to output when pressed. This can be multiple characters, like ".com" */
    public final CharSequence mOutputText;
    /** Popup characters */
    public final CharSequence[] mPopupCharacters;
    /** Popup keyboard maximum column number */
    public final int mMaxPopupColumn;

    /**
     * Flags that specify the anchoring to edges of the keyboard for detecting touch events
     * that are just out of the boundary of the key. This is a bit mask of
     * {@link Keyboard#EDGE_LEFT}, {@link Keyboard#EDGE_RIGHT},
     * {@link Keyboard#EDGE_TOP} and {@link Keyboard#EDGE_BOTTOM}.
     */
    private int mEdgeFlags;
    /** Whether this is a functional key which has different key top than normal key */
    public final boolean mFunctional;
    /** Whether this key repeats itself when held down */
    public final boolean mRepeatable;

    /** The current pressed state of this key */
    private boolean mPressed;
    /** If this is a sticky key, is its highlight on? */
    private boolean mHighlightOn;
    /** Key is enabled and responds on press */
    private boolean mEnabled = true;

    // keyWidth constants
    private static final int KEYWIDTH_FILL_RIGHT = 0;
    private static final int KEYWIDTH_FILL_BOTH = -1;

    private final static int[] KEY_STATE_NORMAL_ON = {
        android.R.attr.state_checkable,
        android.R.attr.state_checked
    };

    private final static int[] KEY_STATE_PRESSED_ON = {
        android.R.attr.state_pressed,
        android.R.attr.state_checkable,
        android.R.attr.state_checked
    };

    private final static int[] KEY_STATE_NORMAL_OFF = {
        android.R.attr.state_checkable
    };

    private final static int[] KEY_STATE_PRESSED_OFF = {
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

    public static int getRtlParenthesisCode(int code) {
        if (sRtlParenthesisMap.containsKey(code)) {
            return sRtlParenthesisMap.get(code);
        } else {
            return code;
        }
    }

    /**
     * This constructor is being used only for key in popup mini keyboard.
     */
    public Key(Resources res, KeyboardParams params, CharSequence popupCharacter, int x, int y,
            int width, int height, int edgeFlags) {
        mHeight = height - params.mVerticalGap;
        mHorizontalGap = params.mHorizontalGap;
        mVerticalGap = params.mVerticalGap;
        mVisualInsetsLeft = mVisualInsetsRight = 0;
        mWidth = width - mHorizontalGap;
        mEdgeFlags = edgeFlags;
        mHintLabel = null;
        mLabelOption = 0;
        mFunctional = false;
        mSticky = false;
        mRepeatable = false;
        mPopupCharacters = null;
        mMaxPopupColumn = 0;
        final String popupSpecification = popupCharacter.toString();
        mLabel = PopupCharactersParser.getLabel(popupSpecification);
        mOutputText = PopupCharactersParser.getOutputText(popupSpecification);
        final int code = PopupCharactersParser.getCode(res, popupSpecification);
        mCode = params.mIsRtlKeyboard ? getRtlParenthesisCode(code) : code;
        mIcon = params.mIconsSet.getIcon(PopupCharactersParser.getIconId(popupSpecification));
        // Horizontal gap is divided equally to both sides of the key.
        mX = x + mHorizontalGap / 2;
        mY = y;
    }

    /**
     * Create a key with the given top-left coordinate and extract its attributes from the XML
     * parser.
     * @param res resources associated with the caller's context
     * @param params the keyboard building parameters.
     * @param row the row that this key belongs to.
     * @param x the x coordinate of the top-left
     * @param y the y coordinate of the top-left
     * @param parser the XML parser containing the attributes for this key
     * @param keyStyles active key styles set
     */
    public Key(Resources res, KeyboardParams params, Row row, int x, int y,
            XmlResourceParser parser, KeyStyles keyStyles) {

        final TypedArray keyboardAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        int keyWidth;
        try {
            mHeight = KeyboardParser.getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_rowHeight,
                    params.mHeight, row.mRowHeight) - params.mVerticalGap;
            mHorizontalGap = KeyboardParser.getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_horizontalGap,
                    params.mWidth, params.mHorizontalGap);
            mVerticalGap = params.mVerticalGap;
            keyWidth = KeyboardParser.getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_keyWidth,
                    params.mWidth, row.mDefaultKeyWidth);
        } finally {
            keyboardAttr.recycle();
        }

        final TypedArray keyAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key);
        try {
            final KeyStyle style;
            if (keyAttr.hasValue(R.styleable.Keyboard_Key_keyStyle)) {
                String styleName = keyAttr.getString(R.styleable.Keyboard_Key_keyStyle);
                style = keyStyles.getKeyStyle(styleName);
                if (style == null)
                    throw new ParseException("Unknown key style: " + styleName, parser);
            } else {
                style = keyStyles.getEmptyKeyStyle();
            }

            final int keyboardWidth = params.mOccupiedWidth;
            int keyXPos = KeyboardParser.getDimensionOrFraction(keyAttr,
                    R.styleable.Keyboard_Key_keyXPos, keyboardWidth, x);
            if (keyXPos < 0) {
                // If keyXPos is negative, the actual x-coordinate will be k + keyXPos.
                keyXPos += keyboardWidth;
                if (keyXPos < x) {
                    // keyXPos shouldn't be less than x because drawable area for this key starts
                    // at x. Or, this key will overlaps the adjacent key on its left hand side.
                    keyXPos = x;
                }
            }
            if (keyWidth == KEYWIDTH_FILL_RIGHT) {
                // If keyWidth is zero, the actual key width will be determined to fill out the
                // area up to the right edge of the keyboard.
                keyWidth = keyboardWidth - keyXPos;
            } else if (keyWidth <= KEYWIDTH_FILL_BOTH) {
                // If keyWidth is negative, the actual key width will be determined to fill out the
                // area between the nearest key on the left hand side and the right edge of the
                // keyboard.
                keyXPos = x;
                keyWidth = keyboardWidth - keyXPos;
            }

            // Horizontal gap is divided equally to both sides of the key.
            mX = keyXPos + mHorizontalGap / 2;
            mY = y;
            mWidth = keyWidth - mHorizontalGap;

            CharSequence[] popupCharacters = style.getTextArray(
                    keyAttr, R.styleable.Keyboard_Key_popupCharacters);
            if (params.mId.mPasswordInput) {
                popupCharacters = PopupCharactersParser.filterOut(
                        res, popupCharacters, PopupCharactersParser.NON_ASCII_FILTER);
            }
            // In Arabic symbol layouts, we'd like to keep digits in popup characters regardless of
            // config_digit_popup_characters_enabled.
            if (params.mId.isAlphabetKeyboard() && !res.getBoolean(
                    R.bool.config_digit_popup_characters_enabled)) {
                mPopupCharacters = PopupCharactersParser.filterOut(
                        res, popupCharacters, PopupCharactersParser.DIGIT_FILTER);
            } else {
                mPopupCharacters = popupCharacters;
            }
            mMaxPopupColumn = style.getInt(keyboardAttr,
                    R.styleable.Keyboard_Key_maxPopupKeyboardColumn,
                    params.mMaxPopupColumn);

            mRepeatable = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_isRepeatable, false);
            mFunctional = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_isFunctional, false);
            mSticky = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_isSticky, false);
            mEnabled = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_enabled, true);
            mEdgeFlags = 0;

            final KeyboardIconsSet iconsSet = params.mIconsSet;
            mVisualInsetsLeft = KeyboardParser.getDimensionOrFraction(keyAttr,
                    R.styleable.Keyboard_Key_visualInsetsLeft, keyboardWidth, 0);
            mVisualInsetsRight = KeyboardParser.getDimensionOrFraction(keyAttr,
                    R.styleable.Keyboard_Key_visualInsetsRight, keyboardWidth, 0);
            mPreviewIcon = iconsSet.getIcon(style.getInt(
                    keyAttr, R.styleable.Keyboard_Key_keyIconPreview,
                    KeyboardIconsSet.ICON_UNDEFINED));
            mIcon = iconsSet.getIcon(style.getInt(
                    keyAttr, R.styleable.Keyboard_Key_keyIcon,
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
                mCode = params.mIsRtlKeyboard ? getRtlParenthesisCode(firstChar) : firstChar;
            } else if (code != Keyboard.CODE_UNSPECIFIED) {
                mCode = code;
            } else {
                mCode = Keyboard.CODE_DUMMY;
            }
        } finally {
            keyAttr.recycle();
        }
    }

    public void addEdgeFlags(int flags) {
        mEdgeFlags |= flags;
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

    public boolean hasPopupHint() {
        return (mLabelOption & LABEL_OPTION_HAS_POPUP_HINT) != 0;
    }

    public boolean hasUppercaseLetter() {
        return (mLabelOption & LABEL_OPTION_HAS_UPPERCASE_LETTER) != 0;
    }

    public boolean hasHintLabel() {
        return (mLabelOption & LABEL_OPTION_HAS_HINT_LABEL) != 0;
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
     */
    public boolean isOnKey(int x, int y) {
        final int left = mX - mHorizontalGap / 2;
        final int right = left + mWidth + mHorizontalGap;
        final int top = mY;
        final int bottom = top + mHeight + mVerticalGap;
        final int flags = mEdgeFlags;
        if (flags == 0) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
        final boolean leftEdge = (flags & Keyboard.EDGE_LEFT) != 0;
        final boolean rightEdge = (flags & Keyboard.EDGE_RIGHT) != 0;
        final boolean topEdge = (flags & Keyboard.EDGE_TOP) != 0;
        final boolean bottomEdge = (flags & Keyboard.EDGE_BOTTOM) != 0;
        // In order to mitigate rounding errors, we use (left <= x <= right) here.
        return (x >= left || leftEdge) && (x <= right || rightEdge)
                && (y >= top || topEdge) && (y <= bottom || bottomEdge);
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

    /**
     * Returns the drawable state for the key, based on the current state and type of the key.
     * @return the drawable state of the key.
     * @see android.graphics.drawable.StateListDrawable#setState(int[])
     */
    public int[] getCurrentDrawableState() {
        final boolean pressed = mPressed;
        if (!mSticky && mFunctional) {
            if (pressed) {
                return KEY_STATE_FUNCTIONAL_PRESSED;
            } else {
                return KEY_STATE_FUNCTIONAL_NORMAL;
            }
        }

        int[] states = KEY_STATE_NORMAL;

        if (mHighlightOn) {
            if (pressed) {
                states = KEY_STATE_PRESSED_ON;
            } else {
                states = KEY_STATE_NORMAL_ON;
            }
        } else {
            if (mSticky) {
                if (pressed) {
                    states = KEY_STATE_PRESSED_OFF;
                } else {
                    states = KEY_STATE_NORMAL_OFF;
                }
            } else {
                if (pressed) {
                    states = KEY_STATE_PRESSED;
                }
            }
        }
        return states;
    }
}
