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

import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.keyboard.internal.KeyboardShiftState;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 * <p>The layout file for a keyboard contains XML that looks like the following snippet:</p>
 * <pre>
 * &lt;Keyboard
 *         latin:keyWidth="%10p"
 *         latin:keyHeight="50px"
 *         latin:horizontalGap="2px"
 *         latin:verticalGap="2px" &gt;
 *     &lt;Row latin:keyWidth="32px" &gt;
 *         &lt;Key latin:keyLabel="A" /&gt;
 *         ...
 *     &lt;/Row&gt;
 *     ...
 * &lt;/Keyboard&gt;
 * </pre>
 */
public class Keyboard {
    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;

    /** Some common keys code.  These should be aligned with values/keycodes.xml */
    public static final int CODE_ENTER = '\n';
    public static final int CODE_TAB = '\t';
    public static final int CODE_SPACE = ' ';
    public static final int CODE_PERIOD = '.';
    public static final int CODE_DASH = '-';
    public static final int CODE_SINGLE_QUOTE = '\'';
    public static final int CODE_DOUBLE_QUOTE = '"';
    // TODO: Check how this should work for right-to-left languages. It seems to stand
    // that for rtl languages, a closing parenthesis is a left parenthesis. Is this
    // managed by the font? Or is it a different char?
    public static final int CODE_CLOSING_PARENTHESIS = ')';
    public static final int CODE_CLOSING_SQUARE_BRACKET = ']';
    public static final int CODE_CLOSING_CURLY_BRACKET = '}';
    public static final int CODE_CLOSING_ANGLE_BRACKET = '>';
    public static final int CODE_DIGIT0 = '0';
    public static final int CODE_PLUS = '+';


    /** Special keys code.  These should be aligned with values/keycodes.xml */
    public static final int CODE_DUMMY = 0;
    public static final int CODE_SHIFT = -1;
    public static final int CODE_SWITCH_ALPHA_SYMBOL = -2;
    public static final int CODE_CAPSLOCK = -3;
    public static final int CODE_CANCEL = -4;
    public static final int CODE_DELETE = -5;
    public static final int CODE_SETTINGS = -6;
    public static final int CODE_SETTINGS_LONGPRESS = -7;
    public static final int CODE_SHORTCUT = -8;
    // Code value representing the code is not specified.
    public static final int CODE_UNSPECIFIED = -99;

    public final KeyboardId mId;

    /** Total height of the keyboard, including the padding and keys */
    public final int mOccupiedHeight;
    /** Total width of the keyboard, including the padding and keys */
    public final int mOccupiedWidth;

    public final int mHeight;
    public final int mWidth;

    /** Default row height */
    public final int mDefaultRowHeight;

    /** Default gap between rows */
    public final int mVerticalGap;

    public final int mMostCommonKeyWidth;

    /** Popup keyboard template */
    public final int mPopupKeyboardResId;

    /** Maximum column for popup keyboard */
    public final int mMaxPopupColumn;

    /** True if Right-To-Left keyboard */
    public final boolean mIsRtlKeyboard;

    /** List of keys and icons in this keyboard */
    public final List<Key> mKeys;
    public final List<Key> mShiftKeys;
    public final Set<Key> mShiftLockKeys;
    public final Map<Key, Drawable> mShiftedIcons;
    public final Map<Key, Drawable> mUnshiftedIcons;
    public final KeyboardIconsSet mIconsSet;

    private final KeyboardShiftState mShiftState = new KeyboardShiftState();

    private final ProximityInfo mProximityInfo;

    public Keyboard(KeyboardParams params) {
        mId = params.mId;
        mOccupiedHeight = params.mOccupiedHeight;
        mOccupiedWidth = params.mOccupiedWidth;
        mHeight = params.mHeight;
        mWidth = params.mWidth;
        mMostCommonKeyWidth = params.mMostCommonKeyWidth;
        mIsRtlKeyboard = params.mIsRtlKeyboard;
        mPopupKeyboardResId = params.mPopupKeyboardResId;
        mMaxPopupColumn = params.mMaxPopupColumn;

        mDefaultRowHeight = params.mDefaultRowHeight;
        mVerticalGap = params.mVerticalGap;

        mKeys = Collections.unmodifiableList(params.mKeys);
        mShiftKeys = Collections.unmodifiableList(params.mShiftKeys);
        mShiftLockKeys = Collections.unmodifiableSet(params.mShiftLockKeys);
        mShiftedIcons = Collections.unmodifiableMap(params.mShiftedIcons);
        mUnshiftedIcons = Collections.unmodifiableMap(params.mUnshiftedIcons);
        mIconsSet = params.mIconsSet;

        mProximityInfo = new ProximityInfo(
                params.GRID_WIDTH, params.GRID_HEIGHT, mOccupiedWidth, mOccupiedHeight,
                mMostCommonKeyWidth, mKeys);
    }

    public int getProximityInfo() {
        return mProximityInfo.getNativeProximityInfo();
    }

    // TODO: Access mKeys directly
    public List<Key> getKeys() {
        return mKeys;
    }

    public boolean hasShiftLockKey() {
        return !mShiftLockKeys.isEmpty();
    }

    public boolean setShiftLocked(boolean newShiftLockState) {
        for (final Key key : mShiftLockKeys) {
            // To represent "shift locked" state. The highlight is handled by background image that
            // might be a StateListDrawable.
            key.setHighlightOn(newShiftLockState);
            // To represent "shifted" state. The key might have a shifted icon.
            if (newShiftLockState && mShiftedIcons.containsKey(key)) {
                key.setIcon(mShiftedIcons.get(key));
            } else {
                key.setIcon(mUnshiftedIcons.get(key));
            }
        }
        mShiftState.setShiftLocked(newShiftLockState);
        return true;
    }

    public boolean isShiftLocked() {
        return mShiftState.isShiftLocked();
    }

    public boolean setShifted(boolean newShiftState) {
        for (final Key key : mShiftKeys) {
            if (!newShiftState && !mShiftState.isShiftLocked()) {
                key.setIcon(mUnshiftedIcons.get(key));
            } else if (newShiftState && !mShiftState.isShiftedOrShiftLocked()) {
                key.setIcon(mShiftedIcons.get(key));
            }
        }
        return mShiftState.setShifted(newShiftState);
    }

    public boolean isShiftedOrShiftLocked() {
        return mShiftState.isShiftedOrShiftLocked();
    }

    public void setAutomaticTemporaryUpperCase() {
        setShifted(true);
        mShiftState.setAutomaticTemporaryUpperCase();
    }

    public boolean isAutomaticTemporaryUpperCase() {
        return isAlphaKeyboard() && mShiftState.isAutomaticTemporaryUpperCase();
    }

    public boolean isManualTemporaryUpperCase() {
        return isAlphaKeyboard() && mShiftState.isManualTemporaryUpperCase();
    }

    public boolean isManualTemporaryUpperCaseFromAuto() {
        return isAlphaKeyboard() && mShiftState.isManualTemporaryUpperCaseFromAuto();
    }

    public KeyboardShiftState getKeyboardShiftState() {
        return mShiftState;
    }

    public boolean isAlphaKeyboard() {
        return mId.isAlphabetKeyboard();
    }

    public boolean isPhoneKeyboard() {
        return mId.isPhoneKeyboard();
    }

    public boolean isNumberKeyboard() {
        return mId.isNumberKeyboard();
    }

    public CharSequence adjustLabelCase(CharSequence label) {
        if (isShiftedOrShiftLocked() && !TextUtils.isEmpty(label) && label.length() < 3
                && Character.isLowerCase(label.charAt(0))) {
            return label.toString().toUpperCase(mId.mLocale);
        }
        return label;
    }

    /**
     * Returns the indices of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    public int[] getNearestKeys(int x, int y) {
        return mProximityInfo.getNearestKeys(x, y);
    }
}
