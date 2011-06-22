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

package com.android.inputmethod.keyboard.internal;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.internal.KeyStyles.KeyStyle;
import com.android.inputmethod.keyboard.internal.KeyboardParser.ParseException;
import com.android.inputmethod.latin.R;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Xml;

import java.util.ArrayList;

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
    /** Hint letter to display on the key in conjunction with the label */
    public final CharSequence mHintLetter;
    /** Option of the label */
    public final int mLabelOption;
    public static final int LABEL_OPTION_ALIGN_LEFT = 0x01;
    public static final int LABEL_OPTION_ALIGN_RIGHT = 0x02;
    public static final int LABEL_OPTION_ALIGN_BOTTOM = 0x08;
    public static final int LABEL_OPTION_FONT_NORMAL = 0x10;
    private static final int LABEL_OPTION_POPUP_HINT = 0x20;
    private static final int LABEL_OPTION_HAS_UPPERCASE_LETTER = 0x40;

    /** Icon to display instead of a label. Icon takes precedence over a label */
    private Drawable mIcon;
    /** Preview version of the icon, for the preview popup */
    private Drawable mPreviewIcon;

    /** Width of the key, not including the gap */
    public final int mWidth;
    /** Height of the key, not including the gap */
    public final int mHeight;
    /** The horizontal gap around this key */
    public final int mGap;
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
    public final int mEdgeFlags;
    /** Whether this is a functional key which has different key top than normal key */
    public final boolean mFunctional;
    /** Whether this key repeats itself when held down */
    public final boolean mRepeatable;

    /** The Keyboard that this key belongs to */
    private final Keyboard mKeyboard;

    /** The current pressed state of this key */
    public boolean mPressed;
    /** If this is a sticky key, is its highlight on? */
    public boolean mHighlightOn;
    /** Key is enabled and responds on press */
    public boolean mEnabled = true;

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

    /**
     * This constructor is being used only for key in popup mini keyboard.
     */
    public Key(Resources res, Keyboard keyboard, CharSequence popupCharacter, int x, int y,
            int width, int height, int edgeFlags) {
        mKeyboard = keyboard;
        mHeight = height - keyboard.getVerticalGap();
        mGap = keyboard.getHorizontalGap();
        mVisualInsetsLeft = mVisualInsetsRight = 0;
        mWidth = width - mGap;
        mEdgeFlags = edgeFlags;
        mHintLetter = null;
        mLabelOption = 0;
        mFunctional = false;
        mSticky = false;
        mRepeatable = false;
        mPopupCharacters = null;
        mMaxPopupColumn = 0;
        final String popupSpecification = popupCharacter.toString();
        mLabel = PopupCharactersParser.getLabel(popupSpecification);
        mOutputText = PopupCharactersParser.getOutputText(popupSpecification);
        mCode = PopupCharactersParser.getCode(res, popupSpecification);
        mIcon = PopupCharactersParser.getIcon(res, popupSpecification);
        // Horizontal gap is divided equally to both sides of the key.
        mX = x + mGap / 2;
        mY = y;
    }

    /**
     * Create a key with the given top-left coordinate and extract its attributes from the XML
     * parser.
     * @param res resources associated with the caller's context
     * @param row the row that this key belongs to. The row must already be attached to
     * a {@link Keyboard}.
     * @param x the x coordinate of the top-left
     * @param y the y coordinate of the top-left
     * @param parser the XML parser containing the attributes for this key
     * @param keyStyles active key styles set
     */
    public Key(Resources res, Row row, int x, int y, XmlResourceParser parser,
            KeyStyles keyStyles) {
        mKeyboard = row.getKeyboard();

        final TypedArray keyboardAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        int keyWidth;
        try {
            mHeight = KeyboardParser.getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_rowHeight,
                    mKeyboard.getKeyboardHeight(), row.mDefaultHeight) - row.mVerticalGap;
            mGap = KeyboardParser.getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_horizontalGap,
                    mKeyboard.getDisplayWidth(), row.mDefaultHorizontalGap);
            keyWidth = KeyboardParser.getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_keyWidth,
                    mKeyboard.getDisplayWidth(), row.mDefaultWidth);
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

            final int keyboardWidth = mKeyboard.getDisplayWidth();
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
            mX = keyXPos + mGap / 2;
            mY = y;
            mWidth = keyWidth - mGap;

            final CharSequence[] popupCharacters = style.getTextArray(keyAttr,
                    R.styleable.Keyboard_Key_popupCharacters);
            if (res.getBoolean(R.bool.config_digit_popup_characters_enabled)) {
                mPopupCharacters = popupCharacters;
            } else {
                mPopupCharacters = filterOutDigitPopupCharacters(popupCharacters);
            }
            mMaxPopupColumn = style.getInt(keyboardAttr,
                    R.styleable.Keyboard_Key_maxPopupKeyboardColumn,
                    mKeyboard.getMaxPopupKeyboardColumn());

            mRepeatable = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_isRepeatable, false);
            mFunctional = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_isFunctional, false);
            mSticky = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_isSticky, false);
            mEnabled = style.getBoolean(keyAttr, R.styleable.Keyboard_Key_enabled, true);
            mEdgeFlags = style.getFlag(keyAttr, R.styleable.Keyboard_Key_keyEdgeFlags, 0)
                    | row.mRowEdgeFlags;

            mVisualInsetsLeft = KeyboardParser.getDimensionOrFraction(keyAttr,
                    R.styleable.Keyboard_Key_visualInsetsLeft, mKeyboard.getDisplayHeight(), 0);
            mVisualInsetsRight = KeyboardParser.getDimensionOrFraction(keyAttr,
                    R.styleable.Keyboard_Key_visualInsetsRight, mKeyboard.getDisplayHeight(), 0);
            mPreviewIcon = style.getDrawable(keyAttr, R.styleable.Keyboard_Key_iconPreview);
            Keyboard.setDefaultBounds(mPreviewIcon);
            mIcon = style.getDrawable(keyAttr, R.styleable.Keyboard_Key_keyIcon);
            Keyboard.setDefaultBounds(mIcon);
            mHintLetter = style.getText(keyAttr, R.styleable.Keyboard_Key_keyHintLetter);

            mLabel = style.getText(keyAttr, R.styleable.Keyboard_Key_keyLabel);
            mLabelOption = style.getFlag(keyAttr, R.styleable.Keyboard_Key_keyLabelOption, 0);
            mOutputText = style.getText(keyAttr, R.styleable.Keyboard_Key_keyOutputText);
            // Choose the first letter of the label as primary code if not
            // specified.
            final int code = style.getInt(keyAttr, R.styleable.Keyboard_Key_code,
                    Keyboard.CODE_UNSPECIFIED);
            if (code == Keyboard.CODE_UNSPECIFIED && !TextUtils.isEmpty(mLabel)) {
                mCode = mLabel.charAt(0);
            } else if (code != Keyboard.CODE_UNSPECIFIED) {
                mCode = code;
            } else {
                mCode = Keyboard.CODE_DUMMY;
            }

            final Drawable shiftedIcon = style.getDrawable(keyAttr,
                    R.styleable.Keyboard_Key_shiftedIcon);
            if (shiftedIcon != null)
                mKeyboard.getShiftedIcons().put(this, shiftedIcon);
        } finally {
            keyAttr.recycle();
        }
    }

    public boolean hasPopupHint() {
        return (mLabelOption & LABEL_OPTION_POPUP_HINT) != 0;
    }

    public boolean hasUppercaseLetter() {
        return (mLabelOption & LABEL_OPTION_HAS_UPPERCASE_LETTER) != 0;
    }

    private static boolean isDigitPopupCharacter(CharSequence label) {
        return label != null && label.length() == 1 && Character.isDigit(label.charAt(0));
    }

    private static CharSequence[] filterOutDigitPopupCharacters(CharSequence[] popupCharacters) {
        if (popupCharacters == null || popupCharacters.length < 1)
            return null;
        if (popupCharacters.length == 1 && isDigitPopupCharacter(
                PopupCharactersParser.getLabel(popupCharacters[0].toString())))
            return null;
        ArrayList<CharSequence> filtered = null;
        for (int i = 0; i < popupCharacters.length; i++) {
            final CharSequence popupSpec = popupCharacters[i];
            if (isDigitPopupCharacter(PopupCharactersParser.getLabel(popupSpec.toString()))) {
                if (filtered == null) {
                    filtered = new ArrayList<CharSequence>();
                    for (int j = 0; j < i; j++)
                        filtered.add(popupCharacters[j]);
                }
            } else if (filtered != null) {
                filtered.add(popupSpec);
            }
        }
        if (filtered == null)
            return popupCharacters;
        if (filtered.size() == 0)
            return null;
        return filtered.toArray(new CharSequence[filtered.size()]);
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

    /**
     * Detects if a point falls on this key.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether or not the point falls on the key. If the key is attached to an edge, it will
     * assume that all points between the key and the edge are considered to be on the key.
     */
    public boolean isOnKey(int x, int y) {
        final int flags = mEdgeFlags;
        final boolean leftEdge = (flags & Keyboard.EDGE_LEFT) != 0;
        final boolean rightEdge = (flags & Keyboard.EDGE_RIGHT) != 0;
        final boolean topEdge = (flags & Keyboard.EDGE_TOP) != 0;
        final boolean bottomEdge = (flags & Keyboard.EDGE_BOTTOM) != 0;
        final int left = mX - mGap / 2;
        final int right = left + mWidth + mGap;
        final int top = mY;
        final int bottom = top + mHeight + mKeyboard.getVerticalGap();
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
