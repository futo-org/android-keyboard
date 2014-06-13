/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.accessibility;

import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.MoreKeysKeyboardView;
import com.android.inputmethod.keyboard.PointerTracker;

/**
 * This class represents a delegate that can be registered in {@link MoreKeysKeyboardView} to
 * enhance accessibility support via composition rather via inheritance.
 */
public class MoreKeysKeyboardAccessibilityDelegate
        extends KeyboardAccessibilityDelegate<MoreKeysKeyboardView> {
    private static final String TAG = MoreKeysKeyboardAccessibilityDelegate.class.getSimpleName();

    private final Rect mMoreKeysKeyboardValidBounds = new Rect();
    private static final int CLOSING_INSET_IN_PIXEL = 1;
    private int mOpenAnnounceResId;
    private int mCloseAnnounceResId;

    public MoreKeysKeyboardAccessibilityDelegate(final MoreKeysKeyboardView moreKeysKeyboardView,
            final KeyDetector keyDetector) {
        super(moreKeysKeyboardView, keyDetector);
    }

    public void setOpenAnnounce(final int resId) {
        mOpenAnnounceResId = resId;
    }

    public void setCloseAnnounce(final int resId) {
        mCloseAnnounceResId = resId;
    }

    public void onShowMoreKeysKeyboard() {
        sendWindowStateChanged(mOpenAnnounceResId);
    }

    public void onDismissMoreKeysKeyboard() {
        sendWindowStateChanged(mCloseAnnounceResId);
    }

    @Override
    protected void onHoverEnter(final MotionEvent event) {
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverEnter: key=" + getHoverKeyOf(event));
        }
        super.onHoverEnter(event);
        final int actionIndex = event.getActionIndex();
        final int x = (int)event.getX(actionIndex);
        final int y = (int)event.getY(actionIndex);
        final int pointerId = event.getPointerId(actionIndex);
        final long eventTime = event.getEventTime();
        mKeyboardView.onDownEvent(x, y, pointerId, eventTime);
    }

    @Override
    protected void onHoverMove(final MotionEvent event) {
        super.onHoverMove(event);
        final int actionIndex = event.getActionIndex();
        final int x = (int)event.getX(actionIndex);
        final int y = (int)event.getY(actionIndex);
        final int pointerId = event.getPointerId(actionIndex);
        final long eventTime = event.getEventTime();
        mKeyboardView.onMoveEvent(x, y, pointerId, eventTime);
    }

    @Override
    protected void onHoverExit(final MotionEvent event) {
        final Key lastKey = getLastHoverKey();
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverExit: key=" + getHoverKeyOf(event) + " last=" + lastKey);
        }
        if (lastKey != null) {
            super.onHoverExitFrom(lastKey);
        }
        setLastHoverKey(null);
        final int actionIndex = event.getActionIndex();
        final int x = (int)event.getX(actionIndex);
        final int y = (int)event.getY(actionIndex);
        final int pointerId = event.getPointerId(actionIndex);
        final long eventTime = event.getEventTime();
        // A hover exit event at one pixel width or height area on the edges of more keys keyboard
        // are treated as closing.
        mMoreKeysKeyboardValidBounds.set(0, 0, mKeyboardView.getWidth(), mKeyboardView.getHeight());
        mMoreKeysKeyboardValidBounds.inset(CLOSING_INSET_IN_PIXEL, CLOSING_INSET_IN_PIXEL);
        if (mMoreKeysKeyboardValidBounds.contains(x, y)) {
            // Invoke {@link MoreKeysKeyboardView#onUpEvent(int,int,int,long)} as if this hover
            // exit event selects a key.
            mKeyboardView.onUpEvent(x, y, pointerId, eventTime);
            // TODO: Should fix this reference. This is a hack to clear the state of
            // {@link PointerTracker}.
            PointerTracker.dismissAllMoreKeysPanels();
            return;
        }
        // Close the more keys keyboard.
        // TODO: Should fix this reference. This is a hack to clear the state of
        // {@link PointerTracker}.
        PointerTracker.dismissAllMoreKeysPanels();
    }
}
