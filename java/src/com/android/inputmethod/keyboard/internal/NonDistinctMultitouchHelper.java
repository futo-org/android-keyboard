/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.util.Log;
import android.view.MotionEvent;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.keyboard.PointerTracker.KeyEventHandler;
import com.android.inputmethod.latin.utils.CoordinateUtils;

public final class NonDistinctMultitouchHelper {
    private static final String TAG = NonDistinctMultitouchHelper.class.getSimpleName();

    private int mOldPointerCount = 1;
    private Key mOldKey;

    public void processMotionEvent(final MotionEvent me, final KeyEventHandler keyEventHandler) {
        final int pointerCount = me.getPointerCount();
        final int oldPointerCount = mOldPointerCount;
        mOldPointerCount = pointerCount;
        // Ignore continuous multitouch events because we can't trust the coordinates in mulitouch
        // events.
        if (pointerCount > 1 && oldPointerCount > 1) {
            return;
        }

        final int action = me.getActionMasked();
        final int index = me.getActionIndex();
        final long eventTime = me.getEventTime();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        // Use only main (id=0) pointer tracker.
        final PointerTracker mainTracker = PointerTracker.getPointerTracker(0, keyEventHandler);

        // In single touch.
        if (oldPointerCount == 1 && pointerCount == 1) {
            mainTracker.processMotionEvent(action, x, y, eventTime, keyEventHandler);
            return;
        }

        // Single-touch to multi-touch transition.
        if (oldPointerCount == 1 && pointerCount == 2) {
            // Send an up event for the last pointer, be cause we can't trust the corrdinates of
            // this multitouch event.
            final int[] lastCoords = CoordinateUtils.newInstance();
            mainTracker.getLastCoordinates(lastCoords);
            mOldKey = mainTracker.getKeyOn(
                    CoordinateUtils.x(lastCoords), CoordinateUtils.y(lastCoords));
            // TODO: Stop calling PointerTracker.onUpEvent directly.
            mainTracker.onUpEvent(
                    CoordinateUtils.x(lastCoords), CoordinateUtils.y(lastCoords), eventTime);
            return;
        }

        // Multi-touch to single touch transition.
        if (oldPointerCount == 2 && pointerCount == 1) {
            // Send a down event for the latest pointer if the key is different from the
            // previous key.
            final Key newKey = mainTracker.getKeyOn(x, y);
            if (mOldKey != newKey) {
                // TODO: Stop calling PointerTracker.onDownEvent directly.
                mainTracker.onDownEvent(x, y, eventTime, keyEventHandler);
                if (action == MotionEvent.ACTION_UP) {
                    // TODO: Stop calling PointerTracker.onUpEvent directly.
                    mainTracker.onUpEvent(x, y, eventTime);
                }
            }
            return;
        }

        Log.w(TAG, "Unknown touch panel behavior: pointer count is "
                + pointerCount + " (previously " + oldPointerCount + ")");
    }
}
