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
    private static final boolean DEBUG_SPEED = false;

    public static final int DEFAULT_CAPACITY = 128;

    private final int mPointerId;
    private final ResizableIntArray mEventTimes = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mXCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mYCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);

    private int mKeyWidth; // pixel
    // Static threshold for starting gesture detection
    private int mDetectFastMoveSpeedThreshold; // pixel /sec
    private int mDetectFastMoveTime;
    private int mDetectFastMoveX;
    private int mDetectFastMoveY;
    // Dynamic threshold for gesture after fast typing
    private boolean mAfterFastTyping;
    private int mGestureDynamicDistanceThresholdFrom; // pixel
    private int mGestureDynamicDistanceThresholdTo; // pixel
    // Variables for gesture sampling
    private int mGestureSamplingMinimumDistance; // pixel
    private long mLastMajorEventTime;
    private int mLastMajorEventX;
    private int mLastMajorEventY;
    // Variables for gesture recognition
    private int mGestureRecognitionSpeedThreshold; // pixel / sec
    private int mIncrementalRecognitionSize;
    private int mLastIncrementalBatchSize;

    // TODO: Move some of these to resource.

    // Static threshold for gesture after fast typing
    public static final int GESTURE_STATIC_TIME_THRESHOLD_AFTER_FAST_TYPING = 350; // msec

    // Static threshold for starting gesture detection
    private static final float DETECT_FAST_MOVE_SPEED_THRESHOLD = 1.5f; // keyWidth / sec

    // Dynamic threshold for gesture after fast typing
    private static final int GESTURE_DYNAMIC_THRESHOLD_DECAY_DURATION = 450; // msec
    // Time based threshold values
    private static final int GESTURE_DYNAMIC_TIME_THRESHOLD_FROM = 300; // msec
    private static final int GESTURE_DYNAMIC_TIME_THRESHOLD_TO = 20; // msec
    // Distance based threshold values
    private static final float GESTURE_DYNAMIC_DISTANCE_THRESHOLD_FROM = 6.0f; // keyWidth
    private static final float GESTURE_DYNAMIC_DISTANCE_THRESHOLD_TO = 0.35f; // keyWidth

    // Parameters for gesture sampling
    private static final float GESTURE_SAMPLING_MINIMUM_DISTANCE = 1.0f / 6.0f; // keyWidth

    // Parameters for gesture recognition
    private static final int GESTURE_RECOGNITION_MINIMUM_TIME = 100; // msec
    private static final float GESTURE_RECOGNITION_SPEED_THRESHOLD = 5.5f; // keyWidth / sec

    private static final int MSEC_PER_SEC = 1000;

    public GestureStroke(final int pointerId) {
        mPointerId = pointerId;
    }

    public void setKeyboardGeometry(final int keyWidth) {
        mKeyWidth = keyWidth;
        // TODO: Find an appropriate base metric for these length. Maybe diagonal length of the key?
        mDetectFastMoveSpeedThreshold = (int)(keyWidth * DETECT_FAST_MOVE_SPEED_THRESHOLD);
        mGestureDynamicDistanceThresholdFrom =
                (int)(keyWidth * GESTURE_DYNAMIC_DISTANCE_THRESHOLD_FROM);
        mGestureDynamicDistanceThresholdTo =
                (int)(keyWidth * GESTURE_DYNAMIC_DISTANCE_THRESHOLD_TO);
        mGestureSamplingMinimumDistance = (int)(keyWidth * GESTURE_SAMPLING_MINIMUM_DISTANCE);
        mGestureRecognitionSpeedThreshold =
                (int)(keyWidth * GESTURE_RECOGNITION_SPEED_THRESHOLD);
        if (DEBUG) {
            Log.d(TAG, String.format(
                    "[%d] setKeyboardGeometry: keyWidth=%3d tT=%3d >> %3d tD=%3d >> %3d",
                    mPointerId, keyWidth,
                    GESTURE_DYNAMIC_TIME_THRESHOLD_FROM,
                    GESTURE_DYNAMIC_TIME_THRESHOLD_TO,
                    mGestureDynamicDistanceThresholdFrom,
                    mGestureDynamicDistanceThresholdTo));
        }
    }

    public void onDownEvent(final int x, final int y, final long downTime,
            final long gestureFirstDownTime, final long lastTypingTime) {
        reset();
        final long elapsedTimeAfterTyping = downTime - lastTypingTime;
        if (elapsedTimeAfterTyping < GESTURE_STATIC_TIME_THRESHOLD_AFTER_FAST_TYPING) {
            mAfterFastTyping = true;
        }
        if (DEBUG) {
            Log.d(TAG, String.format("[%d] onDownEvent: dT=%3d%s", mPointerId,
                    elapsedTimeAfterTyping, mAfterFastTyping ? " afterFastTyping" : ""));
        }
        final int elapsedTimeFromFirstDown = (int)(downTime - gestureFirstDownTime);
        addPoint(x, y, elapsedTimeFromFirstDown, true /* isMajorEvent */);
    }

    private int getGestureDynamicDistanceThreshold(final int deltaTime) {
        if (!mAfterFastTyping || deltaTime >= GESTURE_DYNAMIC_THRESHOLD_DECAY_DURATION) {
            return mGestureDynamicDistanceThresholdTo;
        }
        final int decayedThreshold =
                (mGestureDynamicDistanceThresholdFrom - mGestureDynamicDistanceThresholdTo)
                * deltaTime / GESTURE_DYNAMIC_THRESHOLD_DECAY_DURATION;
        return mGestureDynamicDistanceThresholdFrom - decayedThreshold;
    }

    private int getGestureDynamicTimeThreshold(final int deltaTime) {
        if (!mAfterFastTyping || deltaTime >= GESTURE_DYNAMIC_THRESHOLD_DECAY_DURATION) {
            return GESTURE_DYNAMIC_TIME_THRESHOLD_TO;
        }
        final int decayedThreshold =
                (GESTURE_DYNAMIC_TIME_THRESHOLD_FROM - GESTURE_DYNAMIC_TIME_THRESHOLD_TO)
                * deltaTime / GESTURE_DYNAMIC_THRESHOLD_DECAY_DURATION;
        return GESTURE_DYNAMIC_TIME_THRESHOLD_FROM - decayedThreshold;
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
        final int deltaDistance = getDistance(
                mXCoordinates.get(lastIndex), mYCoordinates.get(lastIndex),
                mDetectFastMoveX, mDetectFastMoveY);
        final int distanceThreshold = getGestureDynamicDistanceThreshold(deltaTime);
        final int timeThreshold = getGestureDynamicTimeThreshold(deltaTime);
        final boolean isStartOfAGesture = deltaTime >= timeThreshold
                && deltaDistance >= distanceThreshold;
        if (DEBUG) {
            Log.d(TAG, String.format("[%d] isStartOfAGesture: dT=%3d tT=%3d dD=%3d tD=%3d%s%s",
                    mPointerId, deltaTime, timeThreshold,
                    deltaDistance, distanceThreshold,
                    mAfterFastTyping ? " afterFastTyping" : "",
                    isStartOfAGesture ? " startOfAGesture" : ""));
        }
        return isStartOfAGesture;
    }

    protected void reset() {
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
            if (DEBUG_SPEED) {
                final float speed = (float)pixelsPerSec / msecs / mKeyWidth;
                Log.d(TAG, String.format("[%d] detectFastMove: speed=%5.2f", mPointerId, speed));
            }
            // Equivalent to (pixels / msecs < mStartSpeedThreshold / MSEC_PER_SEC)
            if (mDetectFastMoveTime == 0 && pixelsPerSec > mDetectFastMoveSpeedThreshold * msecs) {
                if (DEBUG) {
                    final float speed = (float)pixelsPerSec / msecs / mKeyWidth;
                    Log.d(TAG, String.format(
                            "[%d] detectFastMove: speed=%5.2f T=%3d points=%3d fastMove",
                            mPointerId, speed, time, size));
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
            final int distance = detectFastMove(x, y, time);
            if (distance > mGestureSamplingMinimumDistance) {
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

    public static final boolean hasRecognitionTimePast(
            final long currentTime, final long lastRecognitionTime) {
        return currentTime > lastRecognitionTime + GESTURE_RECOGNITION_MINIMUM_TIME;
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
