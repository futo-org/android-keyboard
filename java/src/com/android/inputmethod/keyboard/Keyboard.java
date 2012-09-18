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

import android.util.Log;
import android.util.SparseArray;

import com.android.inputmethod.keyboard.internal.KeyVisualAttributes;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.latin.CollectionUtils;



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

    /** Some common keys code. Must be positive.
     * These should be aligned with values/keycodes.xml
     */
    public static final int CODE_ENTER = '\n';
    public static final int CODE_TAB = '\t';
    public static final int CODE_SPACE = ' ';
    public static final int CODE_PERIOD = '.';
    public static final int CODE_DASH = '-';
    public static final int CODE_SINGLE_QUOTE = '\'';
    public static final int CODE_DOUBLE_QUOTE = '"';
    public static final int CODE_QUESTION_MARK = '?';
    public static final int CODE_EXCLAMATION_MARK = '!';
    // TODO: Check how this should work for right-to-left languages. It seems to stand
    // that for rtl languages, a closing parenthesis is a left parenthesis. Is this
    // managed by the font? Or is it a different char?
    public static final int CODE_CLOSING_PARENTHESIS = ')';
    public static final int CODE_CLOSING_SQUARE_BRACKET = ']';
    public static final int CODE_CLOSING_CURLY_BRACKET = '}';
    public static final int CODE_CLOSING_ANGLE_BRACKET = '>';

    /** Special keys code. Must be negative.
     * These should be aligned with KeyboardCodesSet.ID_TO_NAME[],
     * KeyboardCodesSet.DEFAULT[] and KeyboardCodesSet.RTL[]
     */
    public static final int CODE_SHIFT = -1;
    public static final int CODE_SWITCH_ALPHA_SYMBOL = -2;
    public static final int CODE_OUTPUT_TEXT = -3;
    public static final int CODE_DELETE = -4;
    public static final int CODE_SETTINGS = -5;
    public static final int CODE_SHORTCUT = -6;
    public static final int CODE_ACTION_ENTER = -7;
    public static final int CODE_ACTION_NEXT = -8;
    public static final int CODE_ACTION_PREVIOUS = -9;
    public static final int CODE_LANGUAGE_SWITCH = -10;
    public static final int CODE_RESEARCH = -11;
    // Code value representing the code is not specified.
    public static final int CODE_UNSPECIFIED = -12;

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

    /** Per keyboard key visual parameters */
    public final KeyVisualAttributes mKeyVisualAttributes;

    public final int mMostCommonKeyHeight;
    public final int mMostCommonKeyWidth;

    /** More keys keyboard template */
    public final int mMoreKeysTemplate;

    /** Maximum column for more keys keyboard */
    public final int mMaxMoreKeysKeyboardColumn;

    /** Array of keys and icons in this keyboard */
    public final Key[] mKeys;
    public final Key[] mShiftKeys;
    public final Key[] mAltCodeKeysWhileTyping;
    public final KeyboardIconsSet mIconsSet;

    private final SparseArray<Key> mKeyCache = CollectionUtils.newSparseArray();

    private final ProximityInfo mProximityInfo;
    private final boolean mProximityCharsCorrectionEnabled;

    public Keyboard(final KeyboardParams params) {
        mId = params.mId;
        mThemeId = params.mThemeId;
        mOccupiedHeight = params.mOccupiedHeight;
        mOccupiedWidth = params.mOccupiedWidth;
        mMostCommonKeyHeight = params.mMostCommonKeyHeight;
        mMostCommonKeyWidth = params.mMostCommonKeyWidth;
        mMoreKeysTemplate = params.mMoreKeysTemplate;
        mMaxMoreKeysKeyboardColumn = params.mMaxMoreKeysKeyboardColumn;
        mKeyVisualAttributes = params.mKeyVisualAttributes;
        mTopPadding = params.mTopPadding;
        mVerticalGap = params.mVerticalGap;

        mKeys = params.mKeys.toArray(new Key[params.mKeys.size()]);
        mShiftKeys = params.mShiftKeys.toArray(new Key[params.mShiftKeys.size()]);
        mAltCodeKeysWhileTyping = params.mAltCodeKeysWhileTyping.toArray(
                new Key[params.mAltCodeKeysWhileTyping.size()]);
        mIconsSet = params.mIconsSet;

        mProximityInfo = new ProximityInfo(params.mId.mLocale.toString(),
                params.GRID_WIDTH, params.GRID_HEIGHT, mOccupiedWidth, mOccupiedHeight,
                mMostCommonKeyWidth, mMostCommonKeyHeight, mKeys, params.mTouchPositionCorrection);
        mProximityCharsCorrectionEnabled = params.mProximityCharsCorrectionEnabled;
    }

    public boolean hasProximityCharsCorrection(final int code) {
        if (!mProximityCharsCorrectionEnabled) {
            return false;
        }
        // Note: The native code has the main keyboard layout only at this moment.
        // TODO: Figure out how to handle proximity characters information of all layouts.
        final boolean canAssumeNativeHasProximityCharsInfoOfAllKeys = (
                mId.mElementId == KeyboardId.ELEMENT_ALPHABET
                || mId.mElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED);
        return canAssumeNativeHasProximityCharsInfoOfAllKeys || Character.isLetter(code);
    }

    public ProximityInfo getProximityInfo() {
        return mProximityInfo;
    }

    public Key getKey(final int code) {
        if (code == CODE_UNSPECIFIED) {
            return null;
        }
        synchronized (mKeyCache) {
            final int index = mKeyCache.indexOfKey(code);
            if (index >= 0) {
                return mKeyCache.valueAt(index);
            }

            for (final Key key : mKeys) {
                if (key.mCode == code) {
                    mKeyCache.put(code, key);
                    return key;
                }
            }
            mKeyCache.put(code, null);
            return null;
        }
    }

    public boolean hasKey(final Key aKey) {
        if (mKeyCache.indexOfValue(aKey) >= 0) {
            return true;
        }

        for (final Key key : mKeys) {
            if (key == aKey) {
                mKeyCache.put(key.mCode, key);
                return true;
            }
        }
        return false;
    }

    public static boolean isLetterCode(final int code) {
        return code >= CODE_SPACE;
    }

    @Override
    public String toString() {
        return mId.toString();
    }

    /**
     * Returns the array of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    public Key[] getNearestKeys(final int x, final int y) {
        // Avoid dead pixels at edges of the keyboard
        final int adjustedX = Math.max(0, Math.min(x, mOccupiedWidth - 1));
        final int adjustedY = Math.max(0, Math.min(y, mOccupiedHeight - 1));
        return mProximityInfo.getNearestKeys(adjustedX, adjustedY);
    }

    public static String printableCode(final int code) {
        switch (code) {
        case CODE_SHIFT: return "shift";
        case CODE_SWITCH_ALPHA_SYMBOL: return "symbol";
        case CODE_OUTPUT_TEXT: return "text";
        case CODE_DELETE: return "delete";
        case CODE_SETTINGS: return "settings";
        case CODE_SHORTCUT: return "shortcut";
        case CODE_ACTION_ENTER: return "actionEnter";
        case CODE_ACTION_NEXT: return "actionNext";
        case CODE_ACTION_PREVIOUS: return "actionPrevious";
        case CODE_LANGUAGE_SWITCH: return "languageSwitch";
        case CODE_UNSPECIFIED: return "unspec";
        case CODE_TAB: return "tab";
        case CODE_ENTER: return "enter";
        default:
            if (code <= 0) Log.w(TAG, "Unknown non-positive key code=" + code);
            if (code < CODE_SPACE) return String.format("'\\u%02x'", code);
            if (code < 0x100) return String.format("'%c'", code);
            return String.format("'\\u%04x'", code);
        }
    }
}
