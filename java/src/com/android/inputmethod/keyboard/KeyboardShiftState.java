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

import android.util.Log;

public class KeyboardShiftState {
    private static final String TAG = "KeyboardShiftState";
    private static final boolean DEBUG = KeyboardSwitcher.DEBUG_STATE;

    private static final int NORMAL = 0;
    private static final int MANUAL_SHIFTED = 1;
    private static final int SHIFT_LOCKED = 2;
    private static final int AUTO_SHIFTED = 3;
    private static final int SHIFT_LOCK_SHIFTED = 4;

    private int mState = NORMAL;

    public boolean setShifted(boolean newShiftState) {
        final int oldState = mState;
        if (newShiftState) {
            if (oldState == NORMAL || oldState == AUTO_SHIFTED) {
                mState = MANUAL_SHIFTED;
            } else if (oldState == SHIFT_LOCKED) {
                mState = SHIFT_LOCK_SHIFTED;
            }
        } else {
            if (oldState == MANUAL_SHIFTED || oldState == AUTO_SHIFTED) {
                mState = NORMAL;
            } else if (oldState == SHIFT_LOCK_SHIFTED) {
                mState = SHIFT_LOCKED;
                            }
        }
        if (DEBUG)
            Log.d(TAG, "setShifted(" + newShiftState + "): " + toString(oldState) + " > " + this);
        return mState != oldState;
    }

    public void setShiftLocked(boolean newShiftLockState) {
        final int oldState = mState;
        if (newShiftLockState) {
            if (oldState == NORMAL || oldState == MANUAL_SHIFTED || oldState == AUTO_SHIFTED)
                mState = SHIFT_LOCKED;
        } else {
            if (oldState == SHIFT_LOCKED || oldState == SHIFT_LOCK_SHIFTED)
                mState = NORMAL;
        }
        if (DEBUG)
            Log.d(TAG, "setShiftLocked(" + newShiftLockState + "): " + toString(oldState)
                    + " > " + this);
    }

    public void setAutomaticTemporaryUpperCase() {
        final int oldState = mState;
        mState = AUTO_SHIFTED;
        if (DEBUG)
            Log.d(TAG, "setAutomaticTemporaryUpperCase: " + toString(oldState) + " > " + this);
    }

    public boolean isShiftedOrShiftLocked() {
        return mState != NORMAL;
    }

    public boolean isShiftLocked() {
        return mState == SHIFT_LOCKED || mState == SHIFT_LOCK_SHIFTED;
    }

    public boolean isAutomaticTemporaryUpperCase() {
        return mState == AUTO_SHIFTED;
    }

    public boolean isManualTemporaryUpperCase() {
        return mState == MANUAL_SHIFTED || mState == SHIFT_LOCK_SHIFTED;
    }

    @Override
    public String toString() {
        return toString(mState);
    }

    private static String toString(int state) {
        switch (state) {
        case NORMAL: return "NORMAL";
        case MANUAL_SHIFTED: return "MANUAL_SHIFTED";
        case SHIFT_LOCKED: return "SHIFT_LOCKED";
        case AUTO_SHIFTED: return "AUTO_SHIFTED";
        case SHIFT_LOCK_SHIFTED: return "SHIFT_LOCK_SHIFTED";
        default: return "UKNOWN";
        }
    }
}
