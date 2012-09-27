/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.graphics.Rect;
import android.text.TextUtils;

import com.android.inputmethod.keyboard.internal.TouchPositionCorrection;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.JniUtils;

import java.util.Arrays;

public final class ProximityInfo {
    /** MAX_PROXIMITY_CHARS_SIZE must be the same as MAX_PROXIMITY_CHARS_SIZE_INTERNAL
     * in defines.h */
    public static final int MAX_PROXIMITY_CHARS_SIZE = 16;
    /** Number of key widths from current touch point to search for nearest keys. */
    private static float SEARCH_DISTANCE = 1.2f;
    private static final Key[] EMPTY_KEY_ARRAY = new Key[0];
    private static final float DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS = 0.15f;

    private final int mGridWidth;
    private final int mGridHeight;
    private final int mGridSize;
    private final int mCellWidth;
    private final int mCellHeight;
    // TODO: Find a proper name for mKeyboardMinWidth
    private final int mKeyboardMinWidth;
    private final int mKeyboardHeight;
    private final int mMostCommonKeyWidth;
    private final int mMostCommonKeyHeight;
    private final Key[] mKeys;
    private final Key[][] mGridNeighbors;
    private final String mLocaleStr;

    ProximityInfo(final String localeStr, final int gridWidth, final int gridHeight,
            final int minWidth, final int height, final int mostCommonKeyWidth,
            final int mostCommonKeyHeight, final Key[] keys,
            final TouchPositionCorrection touchPositionCorrection) {
        if (TextUtils.isEmpty(localeStr)) {
            mLocaleStr = "";
        } else {
            mLocaleStr = localeStr;
        }
        mGridWidth = gridWidth;
        mGridHeight = gridHeight;
        mGridSize = mGridWidth * mGridHeight;
        mCellWidth = (minWidth + mGridWidth - 1) / mGridWidth;
        mCellHeight = (height + mGridHeight - 1) / mGridHeight;
        mKeyboardMinWidth = minWidth;
        mKeyboardHeight = height;
        mMostCommonKeyHeight = mostCommonKeyHeight;
        mMostCommonKeyWidth = mostCommonKeyWidth;
        mKeys = keys;
        mGridNeighbors = new Key[mGridSize][];
        if (minWidth == 0 || height == 0) {
            // No proximity required. Keyboard might be more keys keyboard.
            return;
        }
        computeNearestNeighbors();
        mNativeProximityInfo = createNativeProximityInfo(touchPositionCorrection);
    }

    public static ProximityInfo createDummyProximityInfo() {
        return new ProximityInfo("", 1, 1, 1, 1, 1, 1, EMPTY_KEY_ARRAY, null);
    }

    public static ProximityInfo createSpellCheckerProximityInfo(final int[] proximity,
            final int rowSize, final int gridWidth, final int gridHeight) {
        final ProximityInfo spellCheckerProximityInfo = createDummyProximityInfo();
        spellCheckerProximityInfo.mNativeProximityInfo =
                spellCheckerProximityInfo.setProximityInfoNative("",
                        rowSize, gridWidth, gridHeight, gridWidth, gridHeight,
                        1, proximity, 0, null, null, null, null, null, null, null, null);
        return spellCheckerProximityInfo;
    }

    private long mNativeProximityInfo;
    static {
        JniUtils.loadNativeLibrary();
    }

    private native long setProximityInfoNative(
            String locale, int maxProximityCharsSize, int displayWidth,
            int displayHeight, int gridWidth, int gridHeight,
            int mostCommonKeyWidth, int[] proximityCharsArray,
            int keyCount, int[] keyXCoordinates, int[] keyYCoordinates,
            int[] keyWidths, int[] keyHeights, int[] keyCharCodes,
            float[] sweetSpotCenterX, float[] sweetSpotCenterY, float[] sweetSpotRadii);

    private native void releaseProximityInfoNative(long nativeProximityInfo);

    private final long createNativeProximityInfo(
            final TouchPositionCorrection touchPositionCorrection) {
        final Key[][] gridNeighborKeys = mGridNeighbors;
        final int keyboardWidth = mKeyboardMinWidth;
        final int keyboardHeight = mKeyboardHeight;
        final Key[] keys = mKeys;
        final int[] proximityCharsArray = new int[mGridSize * MAX_PROXIMITY_CHARS_SIZE];
        Arrays.fill(proximityCharsArray, Constants.NOT_A_CODE);
        for (int i = 0; i < mGridSize; ++i) {
            final int proximityCharsLength = gridNeighborKeys[i].length;
            for (int j = 0; j < proximityCharsLength; ++j) {
                proximityCharsArray[i * MAX_PROXIMITY_CHARS_SIZE + j] =
                        gridNeighborKeys[i][j].mCode;
            }
        }
        final int keyCount = keys.length;
        final int[] keyXCoordinates = new int[keyCount];
        final int[] keyYCoordinates = new int[keyCount];
        final int[] keyWidths = new int[keyCount];
        final int[] keyHeights = new int[keyCount];
        final int[] keyCharCodes = new int[keyCount];
        final float[] sweetSpotCenterXs;
        final float[] sweetSpotCenterYs;
        final float[] sweetSpotRadii;

        for (int i = 0; i < keyCount; ++i) {
            final Key key = keys[i];
            keyXCoordinates[i] = key.mX;
            keyYCoordinates[i] = key.mY;
            keyWidths[i] = key.mWidth;
            keyHeights[i] = key.mHeight;
            keyCharCodes[i] = key.mCode;
        }

        if (touchPositionCorrection != null && touchPositionCorrection.isValid()) {
            sweetSpotCenterXs = new float[keyCount];
            sweetSpotCenterYs = new float[keyCount];
            sweetSpotRadii = new float[keyCount];
            final float defaultRadius = DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS
                    * (float)Math.hypot(mMostCommonKeyWidth, mMostCommonKeyHeight);
            for (int i = 0; i < keyCount; i++) {
                final Key key = keys[i];
                final Rect hitBox = key.mHitBox;
                sweetSpotCenterXs[i] = hitBox.exactCenterX();
                sweetSpotCenterYs[i] = hitBox.exactCenterY();
                sweetSpotRadii[i] = defaultRadius;
                final int row = hitBox.top / mMostCommonKeyHeight;
                if (row < touchPositionCorrection.getRows()) {
                    final int hitBoxWidth = hitBox.width();
                    final int hitBoxHeight = hitBox.height();
                    final float hitBoxDiagonal = (float)Math.hypot(hitBoxWidth, hitBoxHeight);
                    sweetSpotCenterXs[i] += touchPositionCorrection.getX(row) * hitBoxWidth;
                    sweetSpotCenterYs[i] += touchPositionCorrection.getY(row) * hitBoxHeight;
                    sweetSpotRadii[i] = touchPositionCorrection.getRadius(row) * hitBoxDiagonal;
                }
            }
        } else {
            sweetSpotCenterXs = sweetSpotCenterYs = sweetSpotRadii = null;
        }

        return setProximityInfoNative(mLocaleStr, MAX_PROXIMITY_CHARS_SIZE,
                keyboardWidth, keyboardHeight, mGridWidth, mGridHeight, mMostCommonKeyWidth,
                proximityCharsArray,
                keyCount, keyXCoordinates, keyYCoordinates, keyWidths, keyHeights, keyCharCodes,
                sweetSpotCenterXs, sweetSpotCenterYs, sweetSpotRadii);
    }

    public long getNativeProximityInfo() {
        return mNativeProximityInfo;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mNativeProximityInfo != 0) {
                releaseProximityInfoNative(mNativeProximityInfo);
                mNativeProximityInfo = 0;
            }
        } finally {
            super.finalize();
        }
    }

    private void computeNearestNeighbors() {
        final int defaultWidth = mMostCommonKeyWidth;
        final Key[] keys = mKeys;
        final int thresholdBase = (int) (defaultWidth * SEARCH_DISTANCE);
        final int threshold = thresholdBase * thresholdBase;
        // Round-up so we don't have any pixels outside the grid
        final Key[] neighborKeys = new Key[keys.length];
        final int gridWidth = mGridWidth * mCellWidth;
        final int gridHeight = mGridHeight * mCellHeight;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                final int centerX = x + mCellWidth / 2;
                final int centerY = y + mCellHeight / 2;
                int count = 0;
                for (final Key key : keys) {
                    if (key.isSpacer()) continue;
                    if (key.squaredDistanceToEdge(centerX, centerY) < threshold) {
                        neighborKeys[count++] = key;
                    }
                }
                mGridNeighbors[(y / mCellHeight) * mGridWidth + (x / mCellWidth)] =
                        Arrays.copyOfRange(neighborKeys, 0, count);
            }
        }
    }

    public void fillArrayWithNearestKeyCodes(final int x, final int y, final int primaryKeyCode,
            final int[] dest) {
        final int destLength = dest.length;
        if (destLength < 1) {
            return;
        }
        int index = 0;
        if (primaryKeyCode > Keyboard.CODE_SPACE) {
            dest[index++] = primaryKeyCode;
        }
        final Key[] nearestKeys = getNearestKeys(x, y);
        for (Key key : nearestKeys) {
            if (index >= destLength) {
                break;
            }
            final int code = key.mCode;
            if (code <= Keyboard.CODE_SPACE) {
                break;
            }
            dest[index++] = code;
        }
        if (index < destLength) {
            dest[index] = Constants.NOT_A_CODE;
        }
    }

    public Key[] getNearestKeys(final int x, final int y) {
        if (mGridNeighbors == null) {
            return EMPTY_KEY_ARRAY;
        }
        if (x >= 0 && x < mKeyboardMinWidth && y >= 0 && y < mKeyboardHeight) {
            int index = (y / mCellHeight) * mGridWidth + (x / mCellWidth);
            if (index < mGridSize) {
                return mGridNeighbors[index];
            }
        }
        return EMPTY_KEY_ARRAY;
    }
}
