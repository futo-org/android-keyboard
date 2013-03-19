/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;

import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.ResourceUtils;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.research.ResearchLogger;

public final class TouchScreenRegulator {
    private static final String TAG = TouchScreenRegulator.class.getSimpleName();
    private static boolean DEBUG_MODE = LatinImeLogger.sDBG;

    public interface ProcessMotionEvent {
        public boolean processMotionEvent(MotionEvent me);
    }

    private final ProcessMotionEvent mView;
    private final boolean mNeedsSuddenJumpingHack;

    /** Whether we've started dropping move events because we found a big jump */
    private boolean mDroppingEvents;
    /**
     * Whether multi-touch disambiguation needs to be disabled if a real multi-touch event has
     * occured
     */
    private boolean mDisableDisambiguation;
    /** The distance threshold at which we start treating the touch session as a multi-touch */
    private int mJumpThresholdSquare = Integer.MAX_VALUE;
    private int mLastX;
    private int mLastY;
    // One-seventh of the keyboard width seems like a reasonable threshold
    private static final float JUMP_THRESHOLD_RATIO_TO_KEYBOARD_WIDTH = 1.0f / 7.0f;

    public TouchScreenRegulator(final Context context, final ProcessMotionEvent view) {
        mView = view;
        mNeedsSuddenJumpingHack = Boolean.parseBoolean(ResourceUtils.getDeviceOverrideValue(
                context.getResources(), R.array.sudden_jumping_touch_event_device_list));
    }

    public void setKeyboardGeometry(final int keyboardWidth) {
        final float jumpThreshold = keyboardWidth * JUMP_THRESHOLD_RATIO_TO_KEYBOARD_WIDTH;
        mJumpThresholdSquare = (int)(jumpThreshold * jumpThreshold);
    }

    /**
     * This function checks to see if we need to handle any sudden jumps in the pointer location
     * that could be due to a multi-touch being treated as a move by the firmware or hardware.
     * Once a sudden jump is detected, all subsequent move events are discarded
     * until an UP is received.<P>
     * When a sudden jump is detected, an UP event is simulated at the last position and when
     * the sudden moves subside, a DOWN event is simulated for the second key.
     * @param me the motion event
     * @return true if the event was consumed, so that it doesn't continue to be handled by
     * {@link MainKeyboardView}.
     */
    private boolean handleSuddenJumping(final MotionEvent me) {
        if (!mNeedsSuddenJumpingHack)
            return false;
        final int action = me.getAction();
        final int x = (int) me.getX();
        final int y = (int) me.getY();
        boolean result = false;

        // Real multi-touch event? Stop looking for sudden jumps
        if (me.getPointerCount() > 1) {
            mDisableDisambiguation = true;
        }
        if (mDisableDisambiguation) {
            // If UP, reset the multi-touch flag
            if (action == MotionEvent.ACTION_UP) mDisableDisambiguation = false;
            return false;
        }

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            // Reset the "session"
            mDroppingEvents = false;
            mDisableDisambiguation = false;
            break;
        case MotionEvent.ACTION_MOVE:
            // Is this a big jump?
            final int distanceSquare = (mLastX - x) * (mLastX - x) + (mLastY - y) * (mLastY - y);
            // Check the distance.
            if (distanceSquare > mJumpThresholdSquare) {
                // If we're not yet dropping events, start dropping and send an UP event
                if (!mDroppingEvents) {
                    mDroppingEvents = true;
                    // Send an up event
                    MotionEvent translated = MotionEvent.obtain(
                            me.getEventTime(), me.getEventTime(),
                            MotionEvent.ACTION_UP,
                            mLastX, mLastY, me.getMetaState());
                    mView.processMotionEvent(translated);
                    translated.recycle();
                }
                result = true;
            } else if (mDroppingEvents) {
                // If moves are small and we're already dropping events, continue dropping
                result = true;
            }
            break;
        case MotionEvent.ACTION_UP:
            if (mDroppingEvents) {
                // Send a down event first, as we dropped a bunch of sudden jumps and assume that
                // the user is releasing the touch on the second key.
                MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                        MotionEvent.ACTION_DOWN,
                        x, y, me.getMetaState());
                mView.processMotionEvent(translated);
                translated.recycle();
                mDroppingEvents = false;
                // Let the up event get processed as well, result = false
            }
            break;
        }
        // Track the previous coordinate
        mLastX = x;
        mLastY = y;
        return result;
    }

    public boolean onTouchEvent(final MotionEvent me) {
        // If there was a sudden jump, return without processing the actual motion event.
        if (handleSuddenJumping(me)) {
            if (DEBUG_MODE)
                Log.w(TAG, "onTouchEvent: ignore sudden jump " + me);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.suddenJumpingTouchEventHandler_onTouchEvent(me);
            }
            return true;
        }
        return mView.processMotionEvent(me);
    }
}
