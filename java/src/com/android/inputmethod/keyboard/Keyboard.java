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

import android.util.SparseArray;

import com.android.inputmethod.keyboard.internal.KeyVisualAttributes;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.KeyboardParams;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.CollectionUtils;

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 * <p>The layout file for a keyboard contains XML that looks like the following snippet:</p>
 * <pre>
 * &lt;Keyboard
 *         latin:keyWidth="10%p"
 *         latin:rowHeight="50px"
 *         latin:horizontalGap="2%p"
 *         latin:verticalGap="2%p" &gt;
 *     &lt;Row latin:keyWidth="10%p" &gt;
 *         &lt;Key latin:keyLabel="A" /&gt;
 *         ...
 *     &lt;/Row&gt;
 *     ...
 * &lt;/Keyboard&gt;
 * </pre>
 */
public class Keyboard {
    public final KeyboardId mId;
    public final int mThemeId;

    /** Total height of the keyboard, including the padding and keys */
    public final int mOccupiedHeight;
    /** Total width of the keyboard, including the padding and keys */
    public final int mOccupiedWidth;

    /** Base height of the keyboard, used to calculate rows' height */
    public final int mBaseHeight;
    /** Base width of the keyboard, used to calculate keys' width */
    public final int mBaseWidth;

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
    private final Key[] mKeys;
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
        mBaseHeight = params.mBaseHeight;
        mBaseWidth = params.mBaseWidth;
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

    protected Keyboard(final Keyboard keyboard) {
        mId = keyboard.mId;
        mThemeId = keyboard.mThemeId;
        mOccupiedHeight = keyboard.mOccupiedHeight;
        mOccupiedWidth = keyboard.mOccupiedWidth;
        mBaseHeight = keyboard.mBaseHeight;
        mBaseWidth = keyboard.mBaseWidth;
        mMostCommonKeyHeight = keyboard.mMostCommonKeyHeight;
        mMostCommonKeyWidth = keyboard.mMostCommonKeyWidth;
        mMoreKeysTemplate = keyboard.mMoreKeysTemplate;
        mMaxMoreKeysKeyboardColumn = keyboard.mMaxMoreKeysKeyboardColumn;
        mKeyVisualAttributes = keyboard.mKeyVisualAttributes;
        mTopPadding = keyboard.mTopPadding;
        mVerticalGap = keyboard.mVerticalGap;

        mKeys = keyboard.mKeys;
        mShiftKeys = keyboard.mShiftKeys;
        mAltCodeKeysWhileTyping = keyboard.mAltCodeKeysWhileTyping;
        mIconsSet = keyboard.mIconsSet;

        mProximityInfo = keyboard.mProximityInfo;
        mProximityCharsCorrectionEnabled = keyboard.mProximityCharsCorrectionEnabled;
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

    public Key[] getKeys() {
        return mKeys;
    }

    public Key getKeyFromOutputText(final String outputText) {
        for (final Key key : getKeys()) {
            if (outputText.equals(key.getOutputText())) {
                return key;
            }
        }
        return null;
    }

    public Key getKey(final int code) {
        if (code == Constants.CODE_UNSPECIFIED) {
            return null;
        }
        synchronized (mKeyCache) {
            final int index = mKeyCache.indexOfKey(code);
            if (index >= 0) {
                return mKeyCache.valueAt(index);
            }

            for (final Key key : getKeys()) {
                if (key.getCode() == code) {
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

        for (final Key key : getKeys()) {
            if (key == aKey) {
                mKeyCache.put(key.getCode(), key);
                return true;
            }
        }
        return false;
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
}
