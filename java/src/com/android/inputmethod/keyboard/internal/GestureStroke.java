/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.res.TypedArray;
import android.util.Log;

import com.android.inputmethod.latin.InputPointers;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.ResizableIntArray;
import com.android.inputmethod.latin.utils.ResourceUtils;

public class GestureStroke {
    private static final String TAG = GestureStroke.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SPEED = false;

    // The height of extra area above the keyboard to draw gesture trails.
    // Proportional to the keyboard height.
    public static final float EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO = 0.25f;

    public static final int DEFAULT_CAPACITY = 128;

    private final int mPointerId;
    private final ResizableIntArray mEventTimes = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mXCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mYCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);

    private final GestureStrokeParams mParams;

    private int mKeyWidth; // pixel
    private int mMinYCoordinate; // pixel
    private int mMaxYCoordinate; // pixel
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

    public static final class GestureStrokeParams {
        // Static threshold for gesture after fast typing
        public final int mStaticTimeThresholdAfterFastTyping; // msec
        // Static threshold for starting gesture detection
        public final float mDetectFastMoveSpeedThreshold; // keyWidth/sec
        // Dynamic threshold for gesture after fast typing
        public final int mDynamicThresholdDecayDuration; // msec
        // Time based threshold values
        public final int mDynamicTimeThresholdFrom; // msec
        public final int mDynamicTimeThresholdTo; // msec
        // Distance based threshold values
        public final float mDynamicDistanceThresholdFrom; // keyWidth
        public final float mDynamicDistanceThresholdTo; // keyWidth
        // Parameters for gesture sampling
        public final float mSamplingMinimumDistance; // keyWidth
        // Parameters for gesture recognition
        public final int mRecognitionMinimumTime; // msec
        public final float mRecognitionSpeedThreshold; // keyWidth/sec

        // Default GestureStroke parameters.
        public static final GestureStrokeParams DEFAULT = new GestureStrokeParams();

        private GestureStrokeParams() {
            // These parameter values are default and intended for testing.
            mStaticTimeThresholdAfterFastTyping = 350; // msec
            mDetectFastMoveSpeedThreshold = 1.5f; // keyWidth / sec
            mDynamicThresholdDecayDuration = 450; // msec
            mDynamicTimeThresholdFrom = 300; // msec
            mDynamicTimeThresholdTo = 20; // msec
            mDynamicDistanceThresholdFrom = 6.0f; // keyWidth
            mDynamicDistanceThresholdTo = 0.35f; // keyWidth
            // The following parameters' change will affect the result of regression test.
            mSamplingMinimumDistance = 1.0f / 6.0f; // keyWidth
            mRecognitionMinimumTime = 100; // msec
            mRecognitionSpeedThreshold = 5.5f; // keyWidth / sec
        }

        public GestureStrokeParams(final TypedArray mainKeyboardViewAttr) {
            mStaticTimeThresholdAfterFastTyping = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gestureStaticTimeThresholdAfterFastTyping,
                    DEFAULT.mStaticTimeThresholdAfterFastTyping);
            mDetectFastMoveSpeedThreshold = ResourceUtils.getFraction(mainKeyboardViewAttr,
                    R.styleable.MainKeyboardView_gestureDetectFastMoveSpeedThreshold,
                    DEFAULT.mDetectFastMoveSpeedThreshold);
            mDynamicThresholdDecayDuration = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gestureDynamicThresholdDecayDuration,
                    DEFAULT.mDynamicThresholdDecayDuration);
            mDynamicTimeThresholdFrom = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gestureDynamicTimeThresholdFrom,
                    DEFAULT.mDynamicTimeThresholdFrom);
            mDynamicTimeThresholdTo = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gestureDynamicTimeThresholdTo,
                    DEFAULT.mDynamicTimeThresholdTo);
            mDynamicDistanceThresholdFrom = ResourceUtils.getFraction(mainKeyboardViewAttr,
                    R.styleable.MainKeyboardView_gestureDynamicDistanceThresholdFrom,
                    DEFAULT.mDynamicDistanceThresholdFrom);
            mDynamicDistanceThresholdTo = ResourceUtils.getFraction(mainKeyboardViewAttr,
                    R.styleable.MainKeyboardView_gestureDynamicDistanceThresholdTo,
                    DEFAULT.mDynamicDistanceThresholdTo);
            mSamplingMinimumDistance = ResourceUtils.getFraction(mainKeyboardViewAttr,
                    R.styleable.MainKeyboardView_gestureSamplingMinimumDistance,
                    DEFAULT.mSamplingMinimumDistance);
            mRecognitionMinimumTime = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_gestureRecognitionMinimumTime,
                    DEFAULT.mRecognitionMinimumTime);
            mRecognitionSpeedThreshold = ResourceUtils.getFraction(mainKeyboardViewAttr,
                    R.styleable.MainKeyboardView_gestureRecognitionSpeedThreshold,
                    DEFAULT.mRecognitionSpeedThreshold);
        }
    }

    private static final int MSEC_PER_SEC = 1000;

    public GestureStroke(final int pointerId, final GestureStrokeParams params) {
        mPointerId = pointerId;
        mParams = params;
    }

    public void setKeyboardGeometry(final int keyWidth, final int keyboardHeight) {
        mKeyWidth = keyWidth;
        mMinYCoordinate = -(int)(keyboardHeight * EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO);
        mMaxYCoordinate = keyboardHeight;
        // TODO: Find an appropriate base metric for these length. Maybe diagonal length of the key?
        mDetectFastMoveSpeedThreshold = (int)(keyWidth * mParams.mDetectFastMoveSpeedThreshold);
        mGestureDynamicDistanceThresholdFrom =
                (int)(keyWidth * mParams.mDynamicDistanceThresholdFrom);
        mGestureDynamicDistanceThresholdTo = (int)(keyWidth * mParams.mDynamicDistanceThresholdTo);
        mGestureSamplingMinimumDistance = (int)(keyWidth * mParams.mSamplingMinimumDistance);
        mGestureRecognitionSpeedThreshold = (int)(keyWidth * mParams.mRecognitionSpeedThreshold);
        if (DEBUG) {
            Log.d(TAG, String.format(
                    "[%d] setKeyboardGeometry: keyWidth=%3d tT=%3d >> %3d tD=%3d >> %3d",
                    mPointerId, keyWidth,
                    mParams.mDynamicTimeThresholdFrom,
                    mParams.mDynamicTimeThresholdTo,
                    mGestureDynamicDistanceThresholdFrom,
                    mGestureDynamicDistanceThresholdTo));
        }
    }

    public int getLength() {
        return mEventTimes.getLength();
    }

    public void onDownEvent(final int x, final int y, final long downTime,
            final long gestureFirstDownTime, final long lastTypingTime) {
        reset();
        final long elapsedTimeAfterTyping = downTime - lastTypingTime;
        if (elapsedTimeAfterTyping < mParams.mStaticTimeThresholdAfterFastTyping) {
            mAfterFastTyping = true;
        }
        if (DEBUG) {
            Log.d(TAG, String.format("[%d] onDownEvent: dT=%3d%s", mPointerId,
                    elapsedTimeAfterTyping, mAfterFastTyping ? " afterFastTyping" : ""));
        }
        final int elapsedTimeFromFirstDown = (int)(downTime - gestureFirstDownTime);
        addPointOnKeyboard(x, y, elapsedTimeFromFirstDown, true /* isMajorEvent */);
    }

    private int getGestureDynamicDistanceThreshold(final int deltaTime) {
        if (!mAfterFastTyping || deltaTime >= mParams.mDynamicThresholdDecayDuration) {
            return mGestureDynamicDistanceThresholdTo;
        }
        final int decayedThreshold =
                (mGestureDynamicDistanceThresholdFrom - mGestureDynamicDistanceThresholdTo)
                * deltaTime / mParams.mDynamicThresholdDecayDuration;
        return mGestureDynamicDistanceThresholdFrom - decayedThreshold;
    }

    private int getGestureDynamicTimeThreshold(final int deltaTime) {
        if (!mAfterFastTyping || deltaTime >= mParams.mDynamicThresholdDecayDuration) {
            return mParams.mDynamicTimeThresholdTo;
        }
        final int decayedThreshold =
                (mParams.mDynamicTimeThresholdFrom - mParams.mDynamicTimeThresholdTo)
                * deltaTime / mParams.mDynamicThresholdDecayDuration;
        return mParams.mDynamicTimeThresholdFrom - decayedThreshold;
    }

    public final boolean isStartOfAGesture() {
        if (!hasDetectedFastMove()) {
            return false;
        }
        final int size = getLength();
        if (size <= 0) {
            return false;
        }
        final int lastIndex = size - 1;
        final int deltaTime = mEventTimes.get(lastIndex) - mDetectFastMoveTime;
        if (deltaTime < 0) {
            return false;
        }
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

    public void duplicateLastPointWith(final int time) {
        final int lastIndex = getLength() - 1;
        if (lastIndex >= 0) {
            final int x = mXCoordinates.get(lastIndex);
            final int y = mYCoordinates.get(lastIndex);
            if (DEBUG) {
                Log.d(TAG, String.format("[%d] duplicateLastPointWith: %d,%d|%d", mPointerId,
                        x, y, time));
            }
            // TODO: Have appendMajorPoint()
            appendPoint(x, y, time);
            updateIncrementalRecognitionSize(x, y, time);
        }
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
        final int lastIndex = getLength() - 1;
        // The point that is created by {@link duplicateLastPointWith(int)} may have later event
        // time than the next {@link MotionEvent}. To maintain the monotonicity of the event time,
        // drop the successive point here.
        if (lastIndex >= 0 && mEventTimes.get(lastIndex) > time) {
            Log.w(TAG, String.format("[%d] drop stale event: %d,%d|%d last: %d,%d|%d", mPointerId,
                    x, y, time, mXCoordinates.get(lastIndex), mYCoordinates.get(lastIndex),
                    mEventTimes.get(lastIndex)));
            return;
        }
        mEventTimes.add(time);
        mXCoordinates.add(x);
        mYCoordinates.add(y);
    }

    private void updateMajorEvent(final int x, final int y, final int time) {
        mLastMajorEventTime = time;
        mLastMajorEventX = x;
        mLastMajorEventY = y;
    }

    private final boolean hasDetectedFastMove() {
        return mDetectFastMoveTime > 0;
    }

    private int detectFastMove(final int x, final int y, final int time) {
        final int size = getLength();
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
            if (!hasDetectedFastMove() && pixelsPerSec > mDetectFastMoveSpeedThreshold * msecs) {
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

    /**
     * Add a touch event as a gesture point. Returns true if the touch event is on the valid
     * gesture area.
     * @param x the x-coordinate of the touch event
     * @param y the y-coordinate of the touch event
     * @param time the elapsed time in millisecond from the first gesture down
     * @param isMajorEvent false if this is a historical move event
     * @return true if the touch event is on the valid gesture area
     */
    public boolean addPointOnKeyboard(final int x, final int y, final int time,
            final boolean isMajorEvent) {
        final int size = getLength();
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
        return y >= mMinYCoordinate && y < mMaxYCoordinate;
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
            mIncrementalRecognitionSize = getLength();
        }
    }

    public final boolean hasRecognitionTimePast(
            final long currentTime, final long lastRecognitionTime) {
        return currentTime > lastRecognitionTime + mParams.mRecognitionMinimumTime;
    }

    public final void appendAllBatchPoints(final InputPointers out) {
        appendBatchPoints(out, getLength());
    }

    public final void appendIncrementalBatchPoints(final InputPointers out) {
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
