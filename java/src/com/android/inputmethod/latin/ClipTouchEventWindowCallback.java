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

package com.android.inputmethod.latin;

import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

public class ClipTouchEventWindowCallback extends WindowCallbackAdapter {
    private final View mDecorView;
    private final int mKeyboardBottomRowVerticalCorrection;

    public ClipTouchEventWindowCallback(Window window, int keyboardBottomRowVerticalCorrection) {
        super(window.getCallback());
        mDecorView = window.getDecorView();
        mKeyboardBottomRowVerticalCorrection = keyboardBottomRowVerticalCorrection;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent me) {
        final int height = mDecorView.getHeight();
        final MotionEvent event = clipMotionEvent(me, height,
                height + mKeyboardBottomRowVerticalCorrection);
        return super.dispatchTouchEvent(event);
    }

    private static MotionEvent clipMotionEvent(MotionEvent me, int minHeight, int maxHeight) {
        final int pointerCount = me.getPointerCount();
        boolean shouldClip = false;
        for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++) {
            final float y = me.getY(pointerIndex);
            if (y >= minHeight && y < maxHeight) {
                shouldClip = true;
                break;
            }
        }
        if (!shouldClip)
            return me;

        if (pointerCount == 1) {
            me.setLocation(me.getX(), minHeight - 1);
            return me;
        }

        final int[] pointerIds = new int[pointerCount];
        final MotionEvent.PointerCoords[] pointerCoords =
                new MotionEvent.PointerCoords[pointerCount];
        for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++) {
            pointerIds[pointerIndex] = me.getPointerId(pointerIndex);
            final MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            me.getPointerCoords(pointerIndex, coords);
            pointerCoords[pointerIndex] = coords;
            if (coords.y >= minHeight && coords.y < maxHeight)
                coords.y = minHeight - 1;
        }
        return MotionEvent.obtain(
                me.getDownTime(), me.getEventTime(), me.getAction(), pointerCount, pointerIds,
                pointerCoords, me.getMetaState(), me.getXPrecision(), me.getYPrecision(),
                me.getDeviceId(), me.getEdgeFlags(), me.getSource(), me.getFlags());
    }
}
