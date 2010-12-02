/*
 * Copyright (C) 2010 Google Inc.
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

import java.util.Arrays;

public class ProximityKeyDetector extends KeyDetector {
    private static final int MAX_NEARBY_KEYS = 12;

    // working area
    private int[] mDistances = new int[MAX_NEARBY_KEYS];

    @Override
    protected int getMaxNearbyKeys() {
        return MAX_NEARBY_KEYS;
    }

    @Override
    public int getKeyIndexAndNearbyCodes(int x, int y, int[] allKeys) {
        final Key[] keys = getKeys();
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

        int primaryIndex = NOT_A_KEY;
        int closestKeyIndex = NOT_A_KEY;
        int closestKeyDist = mProximityThresholdSquare + 1;
        final int[] distances = mDistances;
        Arrays.fill(distances, Integer.MAX_VALUE);
        for (final int index : mKeyboard.getNearestKeys(touchX, touchY)) {
            final Key key = keys[index];
            final boolean isInside = key.isInside(touchX, touchY);
            if (isInside)
                primaryIndex = index;
            final int dist = key.squaredDistanceToEdge(touchX, touchY);
            if (isInside || (mProximityCorrectOn && dist < mProximityThresholdSquare)) {
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKeyIndex = index;
                }

                if (allKeys == null) continue;
                final int nCodes = key.codes.length;
                // Find insertion point
                for (int j = 0; j < distances.length; j++) {
                    if (distances[j] > dist) {
                        // Make space for nCodes codes
                        System.arraycopy(distances, j, distances, j + nCodes,
                                distances.length - (j + nCodes));
                        System.arraycopy(allKeys, j, allKeys, j + nCodes,
                                allKeys.length - (j + nCodes));
                        System.arraycopy(key.codes, 0, allKeys, j, nCodes);
                        Arrays.fill(distances, j, j + nCodes, dist);
                        break;
                    }
                }
            }
        }

        return primaryIndex == NOT_A_KEY ? closestKeyIndex : primaryIndex;
    }
}
