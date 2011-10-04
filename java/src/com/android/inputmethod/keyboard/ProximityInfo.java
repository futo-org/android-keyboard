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

import com.android.inputmethod.keyboard.internal.KeyboardParams.TouchPositionCorrection;
import com.android.inputmethod.latin.Utils;
import com.android.inputmethod.latin.spellcheck.SpellCheckerProximityInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProximityInfo {
    public static final int MAX_PROXIMITY_CHARS_SIZE = 16;
    /** Number of key widths from current touch point to search for nearest keys. */
    private static float SEARCH_DISTANCE = 1.2f;
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private final int mKeyHeight;
    private final int mGridWidth;
    private final int mGridHeight;
    private final int mGridSize;
    private final int mCellWidth;
    private final int mCellHeight;
    // TODO: Find a proper name for mKeyboardMinWidth
    private final int mKeyboardMinWidth;
    private final int mKeyboardHeight;
    private final int[][] mGridNeighbors;

    ProximityInfo(int gridWidth, int gridHeight, int minWidth, int height, int keyWidth,
            int keyHeight, List<Key> keys, TouchPositionCorrection touchPositionCorrection) {
        mGridWidth = gridWidth;
        mGridHeight = gridHeight;
        mGridSize = mGridWidth * mGridHeight;
        mCellWidth = (minWidth + mGridWidth - 1) / mGridWidth;
        mCellHeight = (height + mGridHeight - 1) / mGridHeight;
        mKeyboardMinWidth = minWidth;
        mKeyboardHeight = height;
        mKeyHeight = keyHeight;
        mGridNeighbors = new int[mGridSize][];
        if (minWidth == 0 || height == 0) {
            // No proximity required. Keyboard might be mini keyboard.
            return;
        }
        computeNearestNeighbors(keyWidth, keys, touchPositionCorrection);
    }

    public static ProximityInfo createDummyProximityInfo() {
        return new ProximityInfo(1, 1, 1, 1, 1, 1, Collections.<Key>emptyList(), null);
    }

    public static ProximityInfo createSpellCheckerProximityInfo() {
        final ProximityInfo spellCheckerProximityInfo = createDummyProximityInfo();
        spellCheckerProximityInfo.mNativeProximityInfo =
                spellCheckerProximityInfo.setProximityInfoNative(
                        SpellCheckerProximityInfo.ROW_SIZE,
                        480, 300, 10, 3, SpellCheckerProximityInfo.PROXIMITY,
                        0, null, null, null, null, null, null, null, null);
        return spellCheckerProximityInfo;
    }

    private int mNativeProximityInfo;
    static {
        Utils.loadNativeLibrary();
    }
    private native int setProximityInfoNative(int maxProximityCharsSize, int displayWidth,
            int displayHeight, int gridWidth, int gridHeight, int[] proximityCharsArray,
            int keyCount, int[] keyXCoordinates, int[] keyYCoordinates,
            int[] keyWidths, int[] keyHeights, int[] keyCharCodes,
            float[] sweetSpotCenterX, float[] sweetSpotCenterY, float[] sweetSpotRadii);
    private native void releaseProximityInfoNative(int nativeProximityInfo);

    private final void setProximityInfo(int[][] gridNeighborKeyIndexes, int keyboardWidth,
            int keyboardHeight, List<Key> keys,
            TouchPositionCorrection touchPositionCorrection) {
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
        final int[] keyXCoordinates = new int[keyCount];
        final int[] keyYCoordinates = new int[keyCount];
        final int[] keyWidths = new int[keyCount];
        final int[] keyHeights = new int[keyCount];
        final int[] keyCharCodes = new int[keyCount];
        for (int i = 0; i < keyCount; ++i) {
            final Key key = keys.get(i);
            keyXCoordinates[i] = key.mX;
            keyYCoordinates[i] = key.mY;
            keyWidths[i] = key.mWidth;
            keyHeights[i] = key.mHeight;
            keyCharCodes[i] = key.mCode;
        }

        float[] sweetSpotCenterXs = null;
        float[] sweetSpotCenterYs = null;
        float[] sweetSpotRadii = null;

        if (touchPositionCorrection != null && touchPositionCorrection.isValid()) {
            sweetSpotCenterXs = new float[keyCount];
            sweetSpotCenterYs = new float[keyCount];
            sweetSpotRadii = new float[keyCount];
            calculateSweetSpot(keys, touchPositionCorrection,
                    sweetSpotCenterXs, sweetSpotCenterYs, sweetSpotRadii);
        }

        mNativeProximityInfo = setProximityInfoNative(MAX_PROXIMITY_CHARS_SIZE,
                keyboardWidth, keyboardHeight, mGridWidth, mGridHeight, proximityCharsArray,
                keyCount, keyXCoordinates, keyYCoordinates, keyWidths, keyHeights, keyCharCodes,
                sweetSpotCenterXs, sweetSpotCenterYs, sweetSpotRadii);
    }

    private void calculateSweetSpot(List<Key> keys, TouchPositionCorrection touchPositionCorrection,
            float[] sweetSpotCenterXs, float[] sweetSpotCenterYs, float[] sweetSpotRadii) {
        final int keyCount = keys.size();
        final float[] xs = touchPositionCorrection.mXs;
        final float[] ys = touchPositionCorrection.mYs;
        final float[] radii = touchPositionCorrection.mRadii;
        for (int i = 0; i < keyCount; ++i) {
            final Key key = keys.get(i);
            final Rect hitBox = key.mHitBox;
            final int row = hitBox.top / mKeyHeight;
            if (row < radii.length) {
                final float hitBoxCenterX = (hitBox.left + hitBox.right) * 0.5f;
                final float hitBoxCenterY = (hitBox.top + hitBox.bottom) * 0.5f;
                final float hitBoxWidth = hitBox.right - hitBox.left;
                final float hitBoxHeight = hitBox.bottom - hitBox.top;
                final float x = xs[row];
                final float y = ys[row];
                final float radius = radii[row];
                sweetSpotCenterXs[i] = hitBoxCenterX + x * hitBoxWidth;
                sweetSpotCenterYs[i] = hitBoxCenterY + y * hitBoxHeight;
                sweetSpotRadii[i] = radius
                        * (float)Math.sqrt(hitBoxWidth * hitBoxWidth + hitBoxHeight * hitBoxHeight);
            }
        }
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

    private void computeNearestNeighbors(int defaultWidth, List<Key> keys,
            TouchPositionCorrection touchPositionCorrection) {
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
        setProximityInfo(mGridNeighbors, mKeyboardMinWidth, mKeyboardHeight, keys,
                touchPositionCorrection);
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
