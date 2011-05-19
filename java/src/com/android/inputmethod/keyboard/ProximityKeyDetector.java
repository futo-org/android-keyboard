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

import java.util.Arrays;

public class ProximityKeyDetector extends KeyDetector {
    private static final String TAG = ProximityKeyDetector.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int MAX_NEARBY_KEYS = 12;

    // working area
    private final int[] mDistances = new int[MAX_NEARBY_KEYS];
    private final int[] mIndices = new int[MAX_NEARBY_KEYS];

    @Override
    protected int getMaxNearbyKeys() {
        return MAX_NEARBY_KEYS;
    }

    private void initializeNearbyKeys() {
        Arrays.fill(mDistances, Integer.MAX_VALUE);
        Arrays.fill(mIndices, NOT_A_KEY);
    }

    /**
     * Insert the key into nearby keys buffer and sort nearby keys by ascending order of distance.
     *
     * @param keyIndex index of the key.
     * @param distance distance between the key's edge and user touched point.
     * @param isOnKey true if the point is on the key.
     * @return order of the key in the nearby buffer, 0 if it is the nearest key.
     */
    private int sortNearbyKeys(int keyIndex, int distance, boolean isOnKey) {
        final int[] distances = mDistances;
        final int[] indices = mIndices;
        for (int insertPos = 0; insertPos < distances.length; insertPos++) {
            final int comparingDistance = distances[insertPos];
            if (distance < comparingDistance || (distance == comparingDistance && isOnKey)) {
                final int nextPos = insertPos + 1;
                if (nextPos < distances.length) {
                    System.arraycopy(distances, insertPos, distances, nextPos,
                            distances.length - nextPos);
                    System.arraycopy(indices, insertPos, indices, nextPos,
                            indices.length - nextPos);
                }
                distances[insertPos] = distance;
                indices[insertPos] = keyIndex;
                return insertPos;
            }
        }
        return distances.length;
    }

    private void getNearbyKeyCodes(final int[] allCodes) {
        final Key[] keys = getKeys();
        final int[] indices = mIndices;

        // allCodes[0] should always have the key code even if it is a non-letter key.
        if (indices[0] == NOT_A_KEY) {
            allCodes[0] = NOT_A_CODE;
            return;
        }

        int numCodes = 0;
        for (int j = 0; j < indices.length && numCodes < allCodes.length; j++) {
            final int index = indices[j];
            if (index == NOT_A_KEY)
                break;
            final int code = keys[index].mCode;
            // filter out a non-letter key from nearby keys
            if (code < Keyboard.CODE_SPACE)
                continue;
            allCodes[numCodes++] = code;
        }
    }

    @Override
    public int getKeyIndexAndNearbyCodes(int x, int y, final int[] allCodes) {
        final Key[] keys = getKeys();
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

        initializeNearbyKeys();
        int primaryIndex = NOT_A_KEY;
        for (final int index : mKeyboard.getNearestKeys(touchX, touchY)) {
            final Key key = keys[index];
            final boolean isInside = key.isInside(touchX, touchY);
            final int distance = key.squaredDistanceToEdge(touchX, touchY);
            if (isInside || (mProximityCorrectOn && distance < mProximityThresholdSquare)) {
                final int insertedPosition = sortNearbyKeys(index, distance, isInside);
                if (insertedPosition == 0 && isInside)
                    primaryIndex = index;
            }
        }

        if (allCodes != null && allCodes.length > 0) {
            getNearbyKeyCodes(allCodes);
            if (DEBUG) {
                Log.d(TAG, "x=" + x + " y=" + y
                        + " primary="
                        + (primaryIndex == NOT_A_KEY ? "none" : keys[primaryIndex].mCode)
                        + " codes=" + Arrays.toString(allCodes));
            }
        }

        return primaryIndex;
    }
}
