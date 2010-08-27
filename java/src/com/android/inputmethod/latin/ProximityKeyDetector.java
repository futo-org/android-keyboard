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

package com.android.inputmethod.latin;

import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;

import java.util.Arrays;

class ProximityKeyDetector {
    private static final int MAX_NEARBY_KEYS = 12;

    private Keyboard mKeyboard;
    private Key[] mKeys;

    private boolean mProximityCorrectOn;
    private int mProximityThresholdSquare;

    // working area
    private int[] mDistances = new int[MAX_NEARBY_KEYS];

    public void setKeyboard(Keyboard keyboard, Key[] keys) {
        if (keyboard == null || keys == null)
            throw new NullPointerException();
        mKeyboard = keyboard;
        mKeys = keys;
    }

    public void setProximityCorrectionEnabled(boolean enabled) {
        mProximityCorrectOn = enabled;
    }

    public boolean isProximityCorrectionEnabled() {
        return mProximityCorrectOn;
    }

    public void setProximityThreshold(int threshold) {
        mProximityThresholdSquare = threshold * threshold;
    }

    public int[] newCodeArray() {
        int[] codes = new int[MAX_NEARBY_KEYS];
        Arrays.fill(codes, LatinKeyboardBaseView.NOT_A_KEY);
        return codes;
    }

    public int getKeyIndexAndNearbyCodes(int x, int y, int[] allKeys) {
        final Key[] keys = mKeys;
        if (keys == null)
            throw new IllegalStateException("keyboard isn't set");
        // mKeyboard is guaranteed not null at setKeybaord() method
        int primaryIndex = LatinKeyboardBaseView.NOT_A_KEY;
        int closestKey = LatinKeyboardBaseView.NOT_A_KEY;
        int closestKeyDist = mProximityThresholdSquare + 1;
        int[] distances = mDistances;
        Arrays.fill(distances, Integer.MAX_VALUE);
        int [] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
        final int keyCount = nearestKeyIndices.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[nearestKeyIndices[i]];
            int dist = 0;
            boolean isInside = key.isInside(x,y);
            if (isInside) {
                primaryIndex = nearestKeyIndices[i];
            }

            if (((mProximityCorrectOn
                    && (dist = key.squaredDistanceFrom(x, y)) < mProximityThresholdSquare)
                    || isInside)
                    && key.codes[0] > 32) {
                // Find insertion point
                final int nCodes = key.codes.length;
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndices[i];
                }

                if (allKeys == null) continue;

                for (int j = 0; j < distances.length; j++) {
                    if (distances[j] > dist) {
                        // Make space for nCodes codes
                        System.arraycopy(distances, j, distances, j + nCodes,
                                distances.length - j - nCodes);
                        System.arraycopy(allKeys, j, allKeys, j + nCodes,
                                allKeys.length - j - nCodes);
                        System.arraycopy(key.codes, 0, allKeys, j, nCodes);
                        Arrays.fill(distances, j, j + nCodes, dist);
                        break;
                    }
                }
            }
        }
        if (primaryIndex == LatinKeyboardBaseView.NOT_A_KEY) {
            primaryIndex = closestKey;
        }
        return primaryIndex;
    }
}