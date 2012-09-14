/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard.internal;

import com.android.inputmethod.latin.InputPointers;
import com.android.inputmethod.latin.ResizableIntArray;

public class GestureStroke {
    public static final int DEFAULT_CAPACITY = 128;

    private final int mPointerId;
    private final ResizableIntArray mEventTimes = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mXCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mYCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private float mLength;
    private int mIncrementalRecognitionSize;
    private int mLastIncrementalBatchSize;
    private long mLastPointTime;
    private int mLastPointX;
    private int mLastPointY;

    private int mMinGestureLength;
    private int mMinGestureSampleLength;

    // TODO: Move some of these to resource.
    private static final float MIN_GESTURE_LENGTH_RATIO_TO_KEY_WIDTH = 0.75f;
    private static final int MIN_GESTURE_DURATION = 100; // msec
    private static final float MIN_GESTURE_SAMPLING_RATIO_TO_KEY_WIDTH = 1.0f / 6.0f;
    private static final float GESTURE_RECOG_SPEED_THRESHOLD = 0.4f; // dip/msec

    public GestureStroke(final int pointerId) {
        mPointerId = pointerId;
    }

    public void setKeyboardGeometry(final int keyWidth) {
        // TODO: Find an appropriate base metric for these length. Maybe diagonal length of the key?
        mMinGestureLength = (int)(keyWidth * MIN_GESTURE_LENGTH_RATIO_TO_KEY_WIDTH);
        mMinGestureSampleLength = (int)(keyWidth * MIN_GESTURE_SAMPLING_RATIO_TO_KEY_WIDTH);
    }

    public boolean isStartOfAGesture() {
        final int size = mEventTimes.getLength();
        final int downDuration = (size > 0) ? mEventTimes.get(size - 1) : 0;
        return downDuration > MIN_GESTURE_DURATION && mLength > mMinGestureLength;
    }

    public void reset() {
        mLength = 0;
        mIncrementalRecognitionSize = 0;
        mLastIncrementalBatchSize = 0;
        mLastPointTime = 0;
        mEventTimes.setLength(0);
        mXCoordinates.setLength(0);
        mYCoordinates.setLength(0);
    }

    private void updateLastPoint(final int x, final int y, final int time) {
        mLastPointTime = time;
        mLastPointX = x;
        mLastPointY = y;
    }

    public void addPoint(final int x, final int y, final int time, final boolean isHistorical) {
        final int size = mEventTimes.getLength();
        if (size == 0) {
            mEventTimes.add(time);
            mXCoordinates.add(x);
            mYCoordinates.add(y);
            if (!isHistorical) {
                updateLastPoint(x, y, time);
            }
            return;
        }

        final int lastX = mXCoordinates.get(size - 1);
        final int lastY = mYCoordinates.get(size - 1);
        final float dist = getDistance(lastX, lastY, x, y);
        if (dist > mMinGestureSampleLength) {
            mEventTimes.add(time);
            mXCoordinates.add(x);
            mYCoordinates.add(y);
            mLength += dist;
        }

        if (!isHistorical) {
            final int duration = (int)(time - mLastPointTime);
            if (mLastPointTime != 0 && duration > 0) {
                final float speed = getDistance(mLastPointX, mLastPointY, x, y) / duration;
                if (speed < GESTURE_RECOG_SPEED_THRESHOLD) {
                    mIncrementalRecognitionSize = size;
                }
            }
            updateLastPoint(x, y, time);
        }
    }

    public void appendAllBatchPoints(final InputPointers out) {
        appendBatchPoints(out, mEventTimes.getLength());
    }

    public void appendIncrementalBatchPoints(final InputPointers out) {
        appendBatchPoints(out, mIncrementalRecognitionSize);
    }

    private void appendBatchPoints(final InputPointers out, final int size) {
        final int length = size - mLastIncrementalBatchSize;
        if (length <= 0) {
            return;
        }
        out.append(mPointerId, mEventTimes, mXCoordinates, mYCoordinates,
                mLastIncrementalBatchSize, length);
        mLastIncrementalBatchSize = size;
    }

    private static float getDistance(final int x1, final int y1, final int x2, final int y2) {
        final float dx = x1 - x2;
        final float dy = y1 - y2;
        // Note that, in recent versions of Android, FloatMath is actually slower than
        // java.lang.Math due to the way the JIT optimizes java.lang.Math.
        return (float)Math.sqrt(dx * dx + dy * dy);
    }
}
