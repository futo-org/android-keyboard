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

import java.util.Arrays;
import java.util.List;

public class ProximityInfo {
    public static final int MAX_PROXIMITY_CHARS_SIZE = 16;

    private final int mGridWidth;
    private final int mGridHeight;
    private final int mGridSize;

    ProximityInfo(int gridWidth, int gridHeight) {
        mGridWidth = gridWidth;
        mGridHeight = gridHeight;
        mGridSize = mGridWidth * mGridHeight;
    }

    private int mNativeProximityInfo;
    static {
        Utils.loadNativeLibrary();
    }
    private native int setProximityInfoNative(int maxProximityCharsSize, int displayWidth,
            int displayHeight, int gridWidth, int gridHeight, int[] proximityCharsArray);
    private native void releaseProximityInfoNative(int nativeProximityInfo);

    public final void setProximityInfo(int[][] gridNeighborKeyIndexes, int keyboardWidth,
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
        mNativeProximityInfo = setProximityInfoNative(MAX_PROXIMITY_CHARS_SIZE,
                keyboardWidth, keyboardHeight, mGridWidth, mGridHeight, proximityCharsArray);
    }

    // TODO: Get rid of this function's input (keyboard).
    public int getNativeProximityInfo(Keyboard keyboard) {
        if (mNativeProximityInfo == 0) {
            // TODO: Move this function to ProximityInfo and make this private.
            keyboard.computeNearestNeighbors();
        }
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
}
