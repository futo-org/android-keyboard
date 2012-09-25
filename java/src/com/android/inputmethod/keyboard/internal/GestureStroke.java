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

import android.util.Log;

import com.android.inputmethod.latin.InputPointers;
import com.android.inputmethod.latin.ResizableIntArray;

public class GestureStroke {
    private static final String TAG = GestureStroke.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final int DEFAULT_CAPACITY = 128;

    private final int mPointerId;
    private final ResizableIntArray mEventTimes = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mXCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mYCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private int mIncrementalRecognitionSize;
    private int mLastIncrementalBatchSize;
    private long mLastMajorEventTime;
    private int mLastMajorEventX;
    private int mLastMajorEventY;
    private boolean mAfterFastTyping;

    private int mKeyWidth;
    private int mStartGestureLengthThresholdAfterFastTyping; // pixel
    private int mStartGestureLengthThreshold; // pixel
    private int mMinGestureSamplingLength; // pixel
    private int mGestureRecognitionSpeedThreshold; // pixel / sec
    private int mDetectFastMoveSpeedThreshold; // pixel /sec
    private int mDetectFastMoveTime;
    private int mDetectFastMoveX;
    private int mDetectFastMoveY;

    // TODO: Move some of these to resource.
    private static final int GESTURE_AFTER_FAST_TYPING_DURATION_THRESHOLD = 350; // msec
    private static final float START_GESTURE_LENGTH_THRESHOLD_AFTER_FAST_TYPING_RATIO_TO_KEY_WIDTH =
            8.0f;
    private static final int START_GESTURE_LENGTH_THRESHOLD_DECAY_DURATION = 400; // msec
    private static final float START_GESTURE_LENGTH_THRESHOLD_RATIO_TO_KEY_WIDTH = 0.6f;
    private static final int START_GESTURE_DURATION_THRESHOLD = 70; // msec
    private static final int MIN_GESTURE_RECOGNITION_TIME = 100; // msec
    private static final float MIN_GESTURE_SAMPLING_RATIO_TO_KEY_WIDTH = 1.0f / 6.0f;
    private static final float GESTURE_RECOGNITION_SPEED_THRESHOLD_RATIO_TO_KEY_WIDTH =
            5.5f; // keyWidth / sec
    private static final float DETECT_FAST_MOVE_SPEED_THRESHOLD_RATIO_TO_KEY_WIDTH =
            5.0f; // keyWidth / sec
    private static final int MSEC_PER_SEC = 1000;

    public static final boolean hasRecognitionTimePast(
            final long currentTime, final long lastRecognitionTime) {
        return currentTime > lastRecognitionTime + MIN_GESTURE_RECOGNITION_TIME;
    }

    public GestureStroke(final int pointerId) {
        mPointerId = pointerId;
    }

    public void setKeyboardGeometry(final int keyWidth) {
        mKeyWidth = keyWidth;
        // TODO: Find an appropriate base metric for these length. Maybe diagonal length of the key?
        mStartGestureLengthThresholdAfterFastTyping = (int)(keyWidth
                * START_GESTURE_LENGTH_THRESHOLD_AFTER_FAST_TYPING_RATIO_TO_KEY_WIDTH);
        mStartGestureLengthThreshold =
                (int)(keyWidth * START_GESTURE_LENGTH_THRESHOLD_RATIO_TO_KEY_WIDTH);
        mMinGestureSamplingLength = (int)(keyWidth * MIN_GESTURE_SAMPLING_RATIO_TO_KEY_WIDTH);
        mGestureRecognitionSpeedThreshold =
                (int)(keyWidth * GESTURE_RECOGNITION_SPEED_THRESHOLD_RATIO_TO_KEY_WIDTH);
        mDetectFastMoveSpeedThreshold =
                (int)(keyWidth * DETECT_FAST_MOVE_SPEED_THRESHOLD_RATIO_TO_KEY_WIDTH);
        if (DEBUG) {
            Log.d(TAG, "[" + mPointerId + "] setKeyboardGeometry: keyWidth=" + keyWidth
                    + " tL0=" + mStartGestureLengthThresholdAfterFastTyping
                    + " tL=" + mStartGestureLengthThreshold);
        }
    }

    public void setLastLetterTypingTime(final long downTime, final long lastTypingTime) {
        final long elpasedTimeAfterTyping = downTime - lastTypingTime;
        if (elpasedTimeAfterTyping < GESTURE_AFTER_FAST_TYPING_DURATION_THRESHOLD) {
            mAfterFastTyping = true;
        }
        if (DEBUG) {
            Log.d(TAG, "[" + mPointerId + "] setLastTypingTime: dT=" + elpasedTimeAfterTyping
                    + " afterFastTyping=" + mAfterFastTyping);
        }
    }

    private int getStartGestureLengthThreshold(final int deltaTime) {
        if (!mAfterFastTyping || deltaTime >= START_GESTURE_LENGTH_THRESHOLD_DECAY_DURATION) {
            return mStartGestureLengthThreshold;
        }
        final int decayedThreshold =
                (mStartGestureLengthThresholdAfterFastTyping - mStartGestureLengthThreshold)
                * deltaTime / START_GESTURE_LENGTH_THRESHOLD_DECAY_DURATION;
        return mStartGestureLengthThresholdAfterFastTyping - decayedThreshold;
    }

    public boolean isStartOfAGesture() {
        if (mDetectFastMoveTime == 0) {
            return false;
        }
        final int size = mEventTimes.getLength();
        if (size <= 0) {
            return false;
        }
        final int lastIndex = size - 1;
        final int deltaTime = mEventTimes.get(lastIndex) - mDetectFastMoveTime;
        final int deltaLength = getDistance(
                mXCoordinates.get(lastIndex), mYCoordinates.get(lastIndex),
                mDetectFastMoveX, mDetectFastMoveY);
        final int startGestureLengthThreshold = getStartGestureLengthThreshold(deltaTime);
        final boolean isStartOfAGesture = deltaTime > START_GESTURE_DURATION_THRESHOLD
                && deltaLength > startGestureLengthThreshold;
        if (DEBUG) {
            Log.d(TAG, "[" + mPointerId + "] isStartOfAGesture: dT=" + deltaTime
                    + " dL=" + deltaLength + " tL=" + startGestureLengthThreshold
                    + " points=" + size + (isStartOfAGesture ? " Detect start of a gesture" : ""));
        }
        return isStartOfAGesture;
    }

    public void reset() {
        mIncrementalRecognitionSize = 0;
        mLastIncrementalBatchSize = 0;
        mEventTimes.setLength(0);
        mXCoordinates.setLength(0);
        mYCoordinates.setLength(0);
        mLastMajorEventTime = 0;
        mDetectFastMoveTime = 0;
        mAfterFastTyping = false;
    }

    private void appendPoint(final int x, final int y, final int time) {
        mEventTimes.add(time);
        mXCoordinates.add(x);
        mYCoordinates.add(y);
    }

    private void updateMajorEvent(final int x, final int y, final int time) {
        mLastMajorEventTime = time;
        mLastMajorEventX = x;
        mLastMajorEventY = y;
    }

    private int detectFastMove(final int x, final int y, final int time) {
        final int size = mEventTimes.getLength();
        final int lastIndex = size - 1;
        final int lastX = mXCoordinates.get(lastIndex);
        final int lastY = mYCoordinates.get(lastIndex);
        final int dist = getDistance(lastX, lastY, x, y);
        final int msecs = time - mEventTimes.get(lastIndex);
        if (msecs > 0) {
            final int pixels = getDistance(lastX, lastY, x, y);
            final int pixelsPerSec = pixels * MSEC_PER_SEC;
            if (DEBUG) {
                final float speed = (float)pixelsPerSec / msecs / mKeyWidth;
                Log.d(TAG, String.format("[" + mPointerId + "] speed=%.3f", speed));
            }
            // Equivalent to (pixels / msecs < mStartSpeedThreshold / MSEC_PER_SEC)
            if (mDetectFastMoveTime == 0 && pixelsPerSec > mDetectFastMoveSpeedThreshold * msecs) {
                if (DEBUG) {
                    Log.d(TAG, "[" + mPointerId + "] detect fast move: T="
                            + time + " points = " + size);
                }
                mDetectFastMoveTime = time;
                mDetectFastMoveX = x;
                mDetectFastMoveY = y;
            }
        }
        return dist;
    }

    public void addPoint(final int x, final int y, final int time, final boolean isMajorEvent) {
        final int size = mEventTimes.getLength();
        if (size <= 0) {
            // Down event
            appendPoint(x, y, time);
            updateMajorEvent(x, y, time);
        } else {
            final int dist = detectFastMove(x, y, time);
            if (dist > mMinGestureSamplingLength) {
                appendPoint(x, y, time);
            }
        }
        if (isMajorEvent) {
            updateIncrementalRecognitionSize(x, y, time);
            updateMajorEvent(x, y, time);
        }
    }

    private void updateIncrementalRecognitionSize(final int x, final int y, final int time) {
        final int msecs = (int)(time - mLastMajorEventTime);
        if (msecs <= 0) {
            return;
        }
        final int pixels = getDistance(mLastMajorEventX, mLastMajorEventY, x, y);
        final int pixelsPerSec = pixels * MSEC_PER_SEC;
        // Equivalent to (pixels / msecs < mGestureRecognitionThreshold / MSEC_PER_SEC)
        if (pixelsPerSec < mGestureRecognitionSpeedThreshold * msecs) {
            mIncrementalRecognitionSize = mEventTimes.getLength();
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

    private static int getDistance(final int x1, final int y1, final int x2, final int y2) {
        final int dx = x1 - x2;
        final int dy = y1 - y2;
        // Note that, in recent versions of Android, FloatMath is actually slower than
        // java.lang.Math due to the way the JIT optimizes java.lang.Math.
        return (int)Math.sqrt(dx * dx + dy * dy);
    }
}
