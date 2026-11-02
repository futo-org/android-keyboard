/*
 * Copyright (C) 2024 The Android Open Source Project
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

package org.futo.inputmethod.keyboard.internal;

public final class SurfaceSwipeDeleteDetector {
    private int mStep;
    private int mStartX;
    private boolean mActive;
    private int mSelectedWords;
    private long mHoldTimeout;
    private long mActivationTime;
    private boolean mAdjustmentEnabled;

    public void start(final int startX, final int step, final long holdTimeout) {
        mStartX = startX;
        mStep = step > 0 ? step : 1;
        mHoldTimeout = holdTimeout;
        mActive = false;
        mSelectedWords = 0;
        mAdjustmentEnabled = false;
    }

    public boolean isActive() {
        return mActive;
    }

    public int getSelectedWords() {
        return mSelectedWords;
    }

    public void cancel() {
        mActive = false;
        mSelectedWords = 0;
        mAdjustmentEnabled = false;
    }

    public int onMove(final int x, final boolean isRTL, final long eventTime) {
        if (!mActive) {
            final int steps = (x - mStartX) / mStep;
            if (steps >= 0) {
                return 0;
            }
            mActive = true;
            mSelectedWords = 1;
            mActivationTime = eventTime;
            mAdjustmentEnabled = false;
            mStartX = x;
            return isRTL ? 1 : -1;
        }
        if (!mAdjustmentEnabled) {
            if (eventTime - mActivationTime < mHoldTimeout) {
                return 0;
            }
            mAdjustmentEnabled = true;
            mStartX = x;
        }
        final int steps = (x - mStartX) / mStep;
        if (steps == 0) {
            return 0;
        }
        final int rawDelta = isRTL ? -steps : steps;
        final int wordDelta = isRTL ? rawDelta : -rawDelta;
        final int selectedWords = Math.max(0, mSelectedWords + wordDelta);
        final int appliedDelta = selectedWords - mSelectedWords;
        mSelectedWords = selectedWords;
        mStartX += steps * mStep;
        if (appliedDelta == 0) {
            return 0;
        }
        return isRTL ? appliedDelta : -appliedDelta;
    }
}
