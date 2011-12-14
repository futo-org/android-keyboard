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
import android.util.Log;

import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.keyboard.internal.KeyboardShiftState;

import java.util.Collections;
import java.util.HashMap;
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
    private static final String TAG = Keyboard.class.getSimpleName();

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
    public static final int CODE_DELETE = -5;
    public static final int CODE_SETTINGS = -6;
    public static final int CODE_SHORTCUT = -7;
    // Code value representing the code is not specified.
    public static final int CODE_UNSPECIFIED = -99;

    public final KeyboardId mId;
    public final int mThemeId;

    /** Total height of the keyboard, including the padding and keys */
    public final int mOccupiedHeight;
    /** Total width of the keyboard, including the padding and keys */
    public final int mOccupiedWidth;

    /** The padding above the keyboard */
    public final int mTopPadding;
    /** Default gap between rows */
    public final int mVerticalGap;

    public final int mMostCommonKeyHeight;
    public final int mMostCommonKeyWidth;

    /** More keys keyboard template */
    public final int mMoreKeysTemplate;

    /** Maximum column for mini keyboard */
    public final int mMaxMiniKeyboardColumn;

    /** True if Right-To-Left keyboard */
    public final boolean mIsRtlKeyboard;

    /** List of keys and icons in this keyboard */
    public final List<Key> mKeys;
    public final List<Key> mShiftKeys;
    public final Set<Key> mShiftLockKeys;
    public final Map<Key, Drawable> mShiftedIcons;
    public final Map<Key, Drawable> mUnshiftedIcons;
    public final KeyboardIconsSet mIconsSet;

    private final Map<Integer, Key> mKeyCache = new HashMap<Integer, Key>();

    private final ProximityInfo mProximityInfo;

    // TODO: Remove this variable.
    private final KeyboardShiftState mShiftState = new KeyboardShiftState();

    public Keyboard(KeyboardParams params) {
        mId = params.mId;
        mThemeId = params.mThemeId;
        mOccupiedHeight = params.mOccupiedHeight;
        mOccupiedWidth = params.mOccupiedWidth;
        mMostCommonKeyHeight = params.mMostCommonKeyHeight;
        mMostCommonKeyWidth = params.mMostCommonKeyWidth;
        mIsRtlKeyboard = params.mIsRtlKeyboard;
        mMoreKeysTemplate = params.mMoreKeysTemplate;
        mMaxMiniKeyboardColumn = params.mMaxMiniKeyboardColumn;

        mTopPadding = params.mTopPadding;
        mVerticalGap = params.mVerticalGap;

        mKeys = Collections.unmodifiableList(params.mKeys);
        mShiftKeys = Collections.unmodifiableList(params.mShiftKeys);
        mShiftLockKeys = Collections.unmodifiableSet(params.mShiftLockKeys);
        mShiftedIcons = Collections.unmodifiableMap(params.mShiftedIcons);
        mUnshiftedIcons = Collections.unmodifiableMap(params.mUnshiftedIcons);
        mIconsSet = params.mIconsSet;

        mProximityInfo = new ProximityInfo(
                params.GRID_WIDTH, params.GRID_HEIGHT, mOccupiedWidth, mOccupiedHeight,
                mMostCommonKeyWidth, mMostCommonKeyHeight, mKeys, params.mTouchPositionCorrection);
    }

    public ProximityInfo getProximityInfo() {
        return mProximityInfo;
    }

    public Key getKey(int code) {
        final Integer keyCode = code;
        if (mKeyCache.containsKey(keyCode)) {
            return mKeyCache.get(keyCode);
        }

        for (final Key key : mKeys) {
            if (key.mCode == code) {
                mKeyCache.put(keyCode, key);
                return key;
            }
        }
        mKeyCache.put(keyCode, null);
        return null;
    }

    // TODO: Remove this method.
    public boolean hasShiftLockKey() {
        return !mShiftLockKeys.isEmpty();
    }

    // TODO: Remove this method.
    public void setShiftLocked(boolean newShiftLockState) {
        for (final Key key : mShiftLockKeys) {
            // To represent "shift locked" state. The highlight is handled by background image that
            // might be a StateListDrawable.
            key.setHighlightOn(newShiftLockState);
            key.setIcon(newShiftLockState ? mShiftedIcons.get(key) : mUnshiftedIcons.get(key));
        }
        mShiftState.setShiftLocked(newShiftLockState);
    }

    // TODO: Move this method to KeyboardId.
    public boolean isShiftLocked() {
        return mShiftState.isShiftLocked();
    }

    // TODO: Remove this method.
    public void setShifted(boolean newShiftState) {
        if (!mShiftState.isShiftLocked()) {
            for (final Key key : mShiftKeys) {
                key.setIcon(newShiftState ? mShiftedIcons.get(key) : mUnshiftedIcons.get(key));
            }
        }
        mShiftState.setShifted(newShiftState);
    }

    // TODO: Move this method to KeyboardId.
    public boolean isShiftedOrShiftLocked() {
        return mShiftState.isShiftedOrShiftLocked();
    }

    // TODO: Remove this method
    public void setAutomaticTemporaryUpperCase() {
        mShiftState.setAutomaticTemporaryUpperCase();
    }

    // TODO: Move this method to KeyboardId.
    public boolean isManualTemporaryUpperCase() {
        return mShiftState.isManualTemporaryUpperCase();
    }

    // TODO: Remove this method.
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

    public static String printableCode(int code) {
        switch (code) {
        case CODE_SHIFT: return "shift";
        case CODE_SWITCH_ALPHA_SYMBOL: return "symbol";
        case CODE_CAPSLOCK: return "capslock";
        case CODE_DELETE: return "delete";
        case CODE_SHORTCUT: return "shortcut";
        case CODE_DUMMY: return "dummy";
        case CODE_UNSPECIFIED: return "unspec";
        default:
            if (code < 0) Log.w(TAG, "Unknow negative key code=" + code);
            if (code < 0x100) return String.format("\\u%02x", code);
            return String.format("\\u04x", code);
        }
    }
}
