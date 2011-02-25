/*
 * Copyright (C) 2011 Google Inc.
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

public class ProximityInfo {
    public static final int MAX_PROXIMITY_CHARS_SIZE = 16;

    private final int mDisplayWidth;
    private final int mDisplayHeight;
    private final int mGridWidth;
    private final int mGridHeight;
    private final int mGridSize;

    ProximityInfo(int displayWidth, int displayHeight, int gridWidth, int gridHeight) {
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
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

    public final void setProximityInfo(int[][] gridNeighbors) {
        int[] proximityCharsArray = new int[mGridSize * MAX_PROXIMITY_CHARS_SIZE];
        for (int i = 0; i < mGridSize; ++i) {
            final int proximityCharsLength = gridNeighbors[i].length;
            for (int j = 0; j < MAX_PROXIMITY_CHARS_SIZE; ++j) {
                int charCode = KeyDetector.NOT_A_KEY;
                if (j < proximityCharsLength) {
                    charCode = gridNeighbors[i][j];
                }
                proximityCharsArray[i * MAX_PROXIMITY_CHARS_SIZE + j] = charCode;
            }
        }
        mNativeProximityInfo = setProximityInfoNative(MAX_PROXIMITY_CHARS_SIZE,
                mDisplayWidth, mDisplayHeight, mGridWidth, mGridHeight, proximityCharsArray);
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
