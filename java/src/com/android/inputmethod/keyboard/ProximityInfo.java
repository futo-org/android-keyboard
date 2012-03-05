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

import com.android.inputmethod.keyboard.Keyboard.Params.TouchPositionCorrection;
import com.android.inputmethod.latin.Utils;
import com.android.inputmethod.latin.spellcheck.SpellCheckerProximityInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProximityInfo {
    public static final int MAX_PROXIMITY_CHARS_SIZE = 16;
    /** Number of key widths from current touch point to search for nearest keys. */
    private static float SEARCH_DISTANCE = 1.2f;
    private static final Key[] EMPTY_KEY_ARRAY = new Key[0];

    private final int mKeyHeight;
    private final int mGridWidth;
    private final int mGridHeight;
    private final int mGridSize;
    private final int mCellWidth;
    private final int mCellHeight;
    // TODO: Find a proper name for mKeyboardMinWidth
    private final int mKeyboardMinWidth;
    private final int mKeyboardHeight;
    private final Key[][] mGridNeighbors;

    ProximityInfo(int gridWidth, int gridHeight, int minWidth, int height, int keyWidth,
            int keyHeight, Set<Key> keys, TouchPositionCorrection touchPositionCorrection,
            Map<Integer, List<Integer>> additionalProximityChars) {
        mGridWidth = gridWidth;
        mGridHeight = gridHeight;
        mGridSize = mGridWidth * mGridHeight;
        mCellWidth = (minWidth + mGridWidth - 1) / mGridWidth;
        mCellHeight = (height + mGridHeight - 1) / mGridHeight;
        mKeyboardMinWidth = minWidth;
        mKeyboardHeight = height;
        mKeyHeight = keyHeight;
        mGridNeighbors = new Key[mGridSize][];
        if (minWidth == 0 || height == 0) {
            // No proximity required. Keyboard might be more keys keyboard.
            return;
        }
        computeNearestNeighbors(keyWidth, keys, touchPositionCorrection, additionalProximityChars);
    }

    public static ProximityInfo createDummyProximityInfo() {
        return new ProximityInfo(1, 1, 1, 1, 1, 1, Collections.<Key> emptySet(),
                null, Collections.<Integer, List<Integer>> emptyMap());
    }

    public static ProximityInfo createSpellCheckerProximityInfo(final int[] proximity) {
        final ProximityInfo spellCheckerProximityInfo = createDummyProximityInfo();
        spellCheckerProximityInfo.mNativeProximityInfo =
                spellCheckerProximityInfo.setProximityInfoNative(
                        SpellCheckerProximityInfo.ROW_SIZE, 480, 300, 11, 3, proximity, 0,
                        null, null, null, null, null, null, null, null);
        return spellCheckerProximityInfo;
    }

    private long mNativeProximityInfo;
    static {
        Utils.loadNativeLibrary();
    }

    private native long setProximityInfoNative(int maxProximityCharsSize, int displayWidth,
            int displayHeight, int gridWidth, int gridHeight, int[] proximityCharsArray,
            int keyCount, int[] keyXCoordinates, int[] keyYCoordinates,
            int[] keyWidths, int[] keyHeights, int[] keyCharCodes,
            float[] sweetSpotCenterX, float[] sweetSpotCenterY, float[] sweetSpotRadii);

    private native void releaseProximityInfoNative(long nativeProximityInfo);

    private final void setProximityInfo(Key[][] gridNeighborKeys, int keyboardWidth,
            int keyboardHeight, Set<Key> keys,
            TouchPositionCorrection touchPositionCorrection) {
        final int[] proximityCharsArray = new int[mGridSize * MAX_PROXIMITY_CHARS_SIZE];
        Arrays.fill(proximityCharsArray, KeyDetector.NOT_A_CODE);
        for (int i = 0; i < mGridSize; ++i) {
            final int proximityCharsLength = gridNeighborKeys[i].length;
            for (int j = 0; j < proximityCharsLength; ++j) {
                proximityCharsArray[i * MAX_PROXIMITY_CHARS_SIZE + j] =
                        gridNeighborKeys[i][j].mCode;
            }
        }
        final int keyCount = keys.size();
        final int[] keyXCoordinates = new int[keyCount];
        final int[] keyYCoordinates = new int[keyCount];
        final int[] keyWidths = new int[keyCount];
        final int[] keyHeights = new int[keyCount];
        final int[] keyCharCodes = new int[keyCount];
        final float[] sweetSpotCenterXs;
        final float[] sweetSpotCenterYs;
        final float[] sweetSpotRadii;
        final boolean calculateSweetSpotParams;
        if (touchPositionCorrection != null && touchPositionCorrection.isValid()) {
            sweetSpotCenterXs = new float[keyCount];
            sweetSpotCenterYs = new float[keyCount];
            sweetSpotRadii = new float[keyCount];
            calculateSweetSpotParams = true;
            int i = 0;
            for (final Key key : keys) {
                keyXCoordinates[i] = key.mX;
                keyYCoordinates[i] = key.mY;
                keyWidths[i] = key.mWidth;
                keyHeights[i] = key.mHeight;
                keyCharCodes[i] = key.mCode;
                if (calculateSweetSpotParams) {
                    final Rect hitBox = key.mHitBox;
                    final int row = hitBox.top / mKeyHeight;
                    if (row < touchPositionCorrection.mRadii.length) {
                        final float hitBoxCenterX = (hitBox.left + hitBox.right) * 0.5f;
                        final float hitBoxCenterY = (hitBox.top + hitBox.bottom) * 0.5f;
                        final float hitBoxWidth = hitBox.right - hitBox.left;
                        final float hitBoxHeight = hitBox.bottom - hitBox.top;
                        final float x = touchPositionCorrection.mXs[row];
                        final float y = touchPositionCorrection.mYs[row];
                        final float radius = touchPositionCorrection.mRadii[row];
                        sweetSpotCenterXs[i] = hitBoxCenterX + x * hitBoxWidth;
                        sweetSpotCenterYs[i] = hitBoxCenterY + y * hitBoxHeight;
                        sweetSpotRadii[i] = radius * (float) Math.sqrt(
                                hitBoxWidth * hitBoxWidth + hitBoxHeight * hitBoxHeight);
                    }
                }
                i++;
            }
        } else {
            sweetSpotCenterXs = sweetSpotCenterYs = sweetSpotRadii = null;
            calculateSweetSpotParams = false;
        }

        mNativeProximityInfo = setProximityInfoNative(MAX_PROXIMITY_CHARS_SIZE,
                keyboardWidth, keyboardHeight, mGridWidth, mGridHeight, proximityCharsArray,
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

    private void computeNearestNeighbors(int defaultWidth, Set<Key> keys,
            TouchPositionCorrection touchPositionCorrection,
            Map<Integer, List<Integer>> additionalProximityChars) {
        final Map<Integer, Key> keyCodeMap = new HashMap<Integer, Key>();
        for (final Key key : keys) {
            keyCodeMap.put(key.mCode, key);
        }
        final int thresholdBase = (int) (defaultWidth * SEARCH_DISTANCE);
        final int threshold = thresholdBase * thresholdBase;
        // Round-up so we don't have any pixels outside the grid
        final Key[] neighborKeys = new Key[keys.size()];
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
                int currentCodesSize = count;
                for (int i = 0; i < currentCodesSize; ++i) {
                    final int c = neighborKeys[i].mCode;
                    final List<Integer> additionalChars = additionalProximityChars.get(c);
                    if (additionalChars == null || additionalChars.size() == 0) {
                        continue;
                    }
                    for (int j = 0; j < additionalChars.size(); ++j) {
                        final int additionalChar = additionalChars.get(j);
                        boolean contains = false;
                        for (int k = 0; k < count; ++k) {
                            if(additionalChar == neighborKeys[k].mCode) {
                                contains = true;
                                break;
                            }
                        }
                        if (!contains) {
                            neighborKeys[count++] = keyCodeMap.get(additionalChar);
                        }
                    }
                }
                mGridNeighbors[(y / mCellHeight) * mGridWidth + (x / mCellWidth)] =
                        Arrays.copyOfRange(neighborKeys, 0, count);
            }
        }
        setProximityInfo(mGridNeighbors, mKeyboardMinWidth, mKeyboardHeight, keys,
                touchPositionCorrection);
    }

    public Key[] getNearestKeys(int x, int y) {
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
