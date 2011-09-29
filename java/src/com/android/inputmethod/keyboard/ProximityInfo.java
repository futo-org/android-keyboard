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

import com.android.inputmethod.latin.Utils;
import com.android.inputmethod.latin.spellcheck.SpellCheckerProximityInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProximityInfo {
    public static final int MAX_PROXIMITY_CHARS_SIZE = 16;
    /** Number of key widths from current touch point to search for nearest keys. */
    private static float SEARCH_DISTANCE = 1.2f;
    private static final int UNKNOWN_THEME = -1;
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private final int mGridWidth;
    private final int mGridHeight;
    private final int mGridSize;
    private final int mCellWidth;
    private final int mCellHeight;
    // TODO: Find a proper name for mKeyboardMinWidth
    private final int mKeyboardMinWidth;
    private final int mKeyboardHeight;
    private final int[][] mGridNeighbors;

    private final float[] mTouchPositionCorrectionXs;
    private final float[] mTouchPositionCorrectionYs;
    private final float[] mTouchPositionCorrectionRadii;

    ProximityInfo(int gridWidth, int gridHeight, int minWidth, int height, int keyWidth,
            List<Key> keys, float[] touchPositionCorrectionXs, float[] touchPositionCorrectionYs,
            float[] touchPositionCorrectionRadii) {
        mGridWidth = gridWidth;
        mGridHeight = gridHeight;
        mGridSize = mGridWidth * mGridHeight;
        mCellWidth = (minWidth + mGridWidth - 1) / mGridWidth;
        mCellHeight = (height + mGridHeight - 1) / mGridHeight;
        mKeyboardMinWidth = minWidth;
        mKeyboardHeight = height;
        mTouchPositionCorrectionXs = touchPositionCorrectionXs;
        mTouchPositionCorrectionYs = touchPositionCorrectionYs;
        mTouchPositionCorrectionRadii = touchPositionCorrectionRadii;
        mGridNeighbors = new int[mGridSize][];
        if (minWidth == 0 || height == 0) {
            // No proximity required. Keyboard might be mini keyboard.
            return;
        }
        computeNearestNeighbors(keyWidth, keys);
    }

    public static ProximityInfo createDummyProximityInfo() {
        return new ProximityInfo(1, 1, 1, 1, 1, Collections.<Key>emptyList(), null, null, null);
    }

    public static ProximityInfo createSpellCheckerProximityInfo() {
        final ProximityInfo spellCheckerProximityInfo = createDummyProximityInfo();
        spellCheckerProximityInfo.mNativeProximityInfo =
                spellCheckerProximityInfo.setProximityInfoNative(
                        SpellCheckerProximityInfo.ROW_SIZE,
                        480, 300, 10, 3, SpellCheckerProximityInfo.PROXIMITY,
                        0, null, null, null, null, null, UNKNOWN_THEME);
        return spellCheckerProximityInfo;
    }

    private int mNativeProximityInfo;
    static {
        Utils.loadNativeLibrary();
    }
    private native int setProximityInfoNative(int maxProximityCharsSize, int displayWidth,
            int displayHeight, int gridWidth, int gridHeight, int[] proximityCharsArray,
            int keyCount, int[] keyXCoordinates, int[] keyYCoordinates,
            int[] keyWidths, int[] keyHeights, int[] keyCharCodes, int themeId);
    private native void releaseProximityInfoNative(int nativeProximityInfo);

    private final void setProximityInfo(int[][] gridNeighborKeyIndexes, int keyboardWidth,
            int keyboardHeight, List<Key> keys) {
        int[] proximityCharsArray = new int[mGridSize * MAX_PROXIMITY_CHARS_SIZE];
        Arrays.fill(proximityCharsArray, KeyDetector.NOT_A_CODE);
        for (int i = 0; i < mGridSize; ++i) {
            final int proximityCharsLength = gridNeighborKeyIndexes[i].length;
            for (int j = 0; j < proximityCharsLength; ++j) {
                proximityCharsArray[i * MAX_PROXIMITY_CHARS_SIZE + j] =
                        keys.get(gridNeighborKeyIndexes[i][j]).mCode;
            }
        }
        final int keyCount = keys.size();
        int[] keyXCoordinates = new int[keyCount];
        int[] keyYCoordinates = new int[keyCount];
        int[] keyWidths = new int[keyCount];
        int[] keyHeights = new int[keyCount];
        int[] keyCharCodes = new int[keyCount];
        final int themeId = 5;    // TODO: Use real theme id.
        for (int i = 0; i < keyCount; ++i) {
            final Key key = keys.get(i);
            keyXCoordinates[i] = key.mX;
            keyYCoordinates[i] = key.mY;
            keyWidths[i] = key.mWidth;
            keyHeights[i] = key.mHeight;
            keyCharCodes[i] = key.mCode;
        }
        mNativeProximityInfo = setProximityInfoNative(MAX_PROXIMITY_CHARS_SIZE,
                keyboardWidth, keyboardHeight, mGridWidth, mGridHeight, proximityCharsArray,
                keyCount, keyXCoordinates, keyYCoordinates, keyWidths, keyHeights, keyCharCodes,
                themeId);
    }

    public int getNativeProximityInfo() {
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

    private void computeNearestNeighbors(int defaultWidth, List<Key> keys) {
        final int thresholdBase = (int) (defaultWidth * SEARCH_DISTANCE);
        final int threshold = thresholdBase * thresholdBase;
        // Round-up so we don't have any pixels outside the grid
        final int[] indices = new int[keys.size()];
        final int gridWidth = mGridWidth * mCellWidth;
        final int gridHeight = mGridHeight * mCellHeight;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                final int centerX = x + mCellWidth / 2;
                final int centerY = y + mCellHeight / 2;
                int count = 0;
                for (int i = 0; i < keys.size(); i++) {
                    final Key key = keys.get(i);
                    if (key.isSpacer()) continue;
                    if (key.squaredDistanceToEdge(centerX, centerY) < threshold)
                        indices[count++] = i;
                }
                final int[] cell = new int[count];
                System.arraycopy(indices, 0, cell, 0, count);
                mGridNeighbors[(y / mCellHeight) * mGridWidth + (x / mCellWidth)] = cell;
            }
        }
        setProximityInfo(mGridNeighbors, mKeyboardMinWidth, mKeyboardHeight, keys);
    }

    public int[] getNearestKeys(int x, int y) {
        if (mGridNeighbors == null) {
            return EMPTY_INT_ARRAY;
        }
        if (x >= 0 && x < mKeyboardMinWidth && y >= 0 && y < mKeyboardHeight) {
            int index = (y /  mCellHeight) * mGridWidth + (x / mCellWidth);
            if (index < mGridSize) {
                return mGridNeighbors[index];
            }
        }
        return EMPTY_INT_ARRAY;
    }
}
