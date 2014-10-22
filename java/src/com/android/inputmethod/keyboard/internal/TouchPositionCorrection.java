/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.define.DebugFlags;

public final class TouchPositionCorrection {
    private static final int TOUCH_POSITION_CORRECTION_RECORD_SIZE = 3;

    private boolean mEnabled;
    private float[] mXs;
    private float[] mYs;
    private float[] mRadii;

    public void load(final String[] data) {
        final int dataLength = data.length;
        if (dataLength % TOUCH_POSITION_CORRECTION_RECORD_SIZE != 0) {
            if (DebugFlags.DEBUG_ENABLED) {
                throw new RuntimeException(
                        "the size of touch position correction data is invalid");
            }
            return;
        }

        final int length = dataLength / TOUCH_POSITION_CORRECTION_RECORD_SIZE;
        mXs = new float[length];
        mYs = new float[length];
        mRadii = new float[length];
        try {
            for (int i = 0; i < dataLength; ++i) {
                final int type = i % TOUCH_POSITION_CORRECTION_RECORD_SIZE;
                final int index = i / TOUCH_POSITION_CORRECTION_RECORD_SIZE;
                final float value = Float.parseFloat(data[i]);
                if (type == 0) {
                    mXs[index] = value;
                } else if (type == 1) {
                    mYs[index] = value;
                } else {
                    mRadii[index] = value;
                }
            }
            mEnabled = dataLength > 0;
        } catch (NumberFormatException e) {
            if (DebugFlags.DEBUG_ENABLED) {
                throw new RuntimeException(
                        "the number format for touch position correction data is invalid");
            }
            mEnabled = false;
            mXs = null;
            mYs = null;
            mRadii = null;
        }
    }

    @UsedForTesting
    public void setEnabled(final boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isValid() {
        return mEnabled;
    }

    public int getRows() {
        return mRadii.length;
    }

    @SuppressWarnings({ "static-method", "unused" })
    public float getX(final int row) {
        return 0.0f;
        // Touch position correction data for X coordinate is obsolete.
        // return mXs[row];
    }

    public float getY(final int row) {
        return mYs[row];
    }

    public float getRadius(final int row) {
        return mRadii[row];
    }
}
