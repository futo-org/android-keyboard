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

package com.android.inputmethod.accessibility;

import android.content.Context;
import android.os.Message;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.inputmethod.compat.MotionEventCompatUtils;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

/**
 * Detects flick gestures within a stream of hover events.
 * <p>
 * A flick gesture is defined as a stream of hover events with the following
 * properties:
 * <ul>
 *   <li>Begins with a {@link MotionEventCompatUtils#ACTION_HOVER_ENTER} event
 *   <li>Contains any number of {@link MotionEventCompatUtils#ACTION_HOVER_MOVE}
 *       events
 *   <li>Ends with a {@link MotionEventCompatUtils#ACTION_HOVER_EXIT} event
 *   <li>Maximum duration of 250 milliseconds
 *   <li>Minimum distance between enter and exit points must be at least equal to
 *       scaled double tap slop (see
 *       {@link ViewConfiguration#getScaledDoubleTapSlop()})
 * </ul>
 * <p>
 * Initial enter events are intercepted and cached until the stream fails to
 * satisfy the constraints defined above, at which point the cached enter event
 * is sent to its source {@link AccessibleKeyboardViewProxy} and subsequent move
 * and exit events are ignored.
 */
public abstract class FlickGestureDetector {
    public static final int FLICK_UP = 0;
    public static final int FLICK_RIGHT = 1;
    public static final int FLICK_LEFT = 2;
    public static final int FLICK_DOWN = 3;

    private final FlickHandler mFlickHandler;
    private final int mFlickRadiusSquare;

    private AccessibleKeyboardViewProxy mCachedView;
    private PointerTracker mCachedTracker;
    private MotionEvent mCachedHoverEnter;

    private static class FlickHandler extends StaticInnerHandlerWrapper<FlickGestureDetector> {
        private static final int MSG_FLICK_TIMEOUT = 1;

        /** The maximum duration of a flick gesture in milliseconds. */
        private static final int DELAY_FLICK_TIMEOUT = 250;

        public FlickHandler(FlickGestureDetector outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            final FlickGestureDetector gestureDetector = getOuterInstance();

            switch (msg.what) {
            case MSG_FLICK_TIMEOUT:
                gestureDetector.clearFlick(true);
            }
        }

        public void startFlickTimeout() {
            cancelFlickTimeout();
            sendEmptyMessageDelayed(MSG_FLICK_TIMEOUT, DELAY_FLICK_TIMEOUT);
        }

        public void cancelFlickTimeout() {
            removeMessages(MSG_FLICK_TIMEOUT);
        }
    }

    /**
     * Creates a new flick gesture detector.
     *
     * @param context The parent context.
     */
    public FlickGestureDetector(Context context) {
        final int doubleTapSlop = ViewConfiguration.get(context).getScaledDoubleTapSlop();

        mFlickHandler = new FlickHandler(this);
        mFlickRadiusSquare = doubleTapSlop * doubleTapSlop;
    }

    /**
     * Processes motion events to detect flick gestures.
     *
     * @param event The current event.
     * @param view The source of the event.
     * @param tracker A pointer tracker for the event.
     * @return {@code true} if the event was handled.
     */
    public boolean onHoverEvent(MotionEvent event, AccessibleKeyboardViewProxy view,
            PointerTracker tracker) {
        // Always cache and consume the first hover event.
        if (event.getAction() == MotionEventCompatUtils.ACTION_HOVER_ENTER) {
            mCachedView = view;
            mCachedTracker = tracker;
            mCachedHoverEnter = MotionEvent.obtain(event);
            mFlickHandler.startFlickTimeout();
            return true;
        }

        // Stop if the event has already been canceled.
        if (mCachedHoverEnter == null) {
            return false;
        }

        final float distanceSquare = calculateDistanceSquare(mCachedHoverEnter, event);
        final long timeout = event.getEventTime() - mCachedHoverEnter.getEventTime();

        switch (event.getAction()) {
        case MotionEventCompatUtils.ACTION_HOVER_MOVE:
            // Consume all valid move events before timeout.
            return true;
        case MotionEventCompatUtils.ACTION_HOVER_EXIT:
            // Ignore exit events outside the flick radius.
            if (distanceSquare < mFlickRadiusSquare) {
                clearFlick(true);
                return false;
            } else {
                return dispatchFlick(mCachedHoverEnter, event);
            }
        default:
            return false;
        }
    }

    /**
     * Clears the cached flick information and optionally forwards the event to
     * the source view's internal hover event handler.
     *
     * @param sendCachedEvent Set to {@code true} to forward the hover event to
     *            the source view.
     */
    private void clearFlick(boolean sendCachedEvent) {
        mFlickHandler.cancelFlickTimeout();

        if (mCachedHoverEnter != null) {
            if (sendCachedEvent) {
                mCachedView.onHoverEventInternal(mCachedHoverEnter, mCachedTracker);
            }
            mCachedHoverEnter.recycle();
            mCachedHoverEnter = null;
        }

        mCachedTracker = null;
        mCachedView = null;
    }

    /**
     * Computes the direction of a flick gesture and forwards it to
     * {@link #onFlick(MotionEvent, MotionEvent, int)} for handling.
     *
     * @param e1 The {@link MotionEventCompatUtils#ACTION_HOVER_ENTER} event
     *            where the flick started.
     * @param e2 The {@link MotionEventCompatUtils#ACTION_HOVER_EXIT} event
     *            where the flick ended.
     * @return {@code true} if the flick event was handled.
     */
    private boolean dispatchFlick(MotionEvent e1, MotionEvent e2) {
        clearFlick(false);

        final float dX = e2.getX() - e1.getX();
        final float dY = e2.getY() - e1.getY();
        final int direction;

        if (dY > dX) {
            if (dY > -dX) {
                direction = FLICK_DOWN;
            } else {
                direction = FLICK_LEFT;
            }
        } else {
            if (dY > -dX) {
                direction = FLICK_RIGHT;
            } else {
                direction = FLICK_UP;
            }
        }

        return onFlick(e1, e2, direction);
    }

    private float calculateDistanceSquare(MotionEvent e1, MotionEvent e2) {
        final float dX = e2.getX() - e1.getX();
        final float dY = e2.getY() - e1.getY();
        return (dX * dX) + (dY * dY);
    }

    /**
     * Handles a detected flick gesture.
     *
     * @param e1 The {@link MotionEventCompatUtils#ACTION_HOVER_ENTER} event
     *            where the flick started.
     * @param e2 The {@link MotionEventCompatUtils#ACTION_HOVER_EXIT} event
     *            where the flick ended.
     * @param direction The direction of the flick event, one of:
     *            <ul>
     *              <li>{@link #FLICK_UP}
     *              <li>{@link #FLICK_DOWN}
     *              <li>{@link #FLICK_LEFT}
     *              <li>{@link #FLICK_RIGHT}
     *            </ul>
     * @return {@code true} if the flick event was handled.
     */
    public abstract boolean onFlick(MotionEvent e1, MotionEvent e2, int direction);
}
