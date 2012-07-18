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

import android.util.FloatMath;

import com.android.inputmethod.latin.InputPointers;

public class GestureStroke {
    private final int mPointerId;
    private final InputPointers mInputPointers = new InputPointers();
    private float mLength;
    private float mAngle;
    private int mIncrementalRecognitionPoint;
    private boolean mHasSharpCorner;
    private long mLastPointTime;
    private int mLastPointX;
    private int mLastPointY;

    private int mMinGestureLength;
    private int mMinGestureSampleLength;

    // TODO: Tune these parameters.
    private static final float MIN_GESTURE_DETECTION_RATIO_TO_KEY_WIDTH = 1.0f / 4.0f;
    private static final float MIN_GESTURE_SAMPLING_RATIO_TO_KEY_HEIGHT = 1.0f / 6.0f;
    private static final int MIN_GESTURE_DURATION = 100; // msec
    private static final float GESTURE_RECOG_SPEED_THRESHOLD = 0.4f; // dip/msec
    private static final float GESTURE_RECOG_CURVATURE_THRESHOLD = (float)(Math.PI / 4.0f);

    private static final float DOUBLE_PI = (float)(2 * Math.PI);

    public GestureStroke(int pointerId) {
        mPointerId = pointerId;
        reset();
    }

    public void setGestureSampleLength(final int keyWidth, final int keyHeight) {
        mMinGestureLength = (int)(keyWidth * MIN_GESTURE_DETECTION_RATIO_TO_KEY_WIDTH);
        mMinGestureSampleLength = (int)(keyHeight * MIN_GESTURE_SAMPLING_RATIO_TO_KEY_HEIGHT);
    }

    public boolean isStartOfAGesture(int downDuration) {
        return downDuration > MIN_GESTURE_DURATION && mLength > mMinGestureLength;
    }

    public void reset() {
        mLength = 0;
        mAngle = 0;
        mIncrementalRecognitionPoint = 0;
        mHasSharpCorner = false;
        mLastPointTime = 0;
        mInputPointers.reset();
    }

    private void updateLastPoint(final int x, final int y, final int time) {
        mLastPointTime = time;
        mLastPointX = x;
        mLastPointY = y;
    }

    public void addPoint(final int x, final int y, final int time, final boolean isHistorical) {
        final int size = mInputPointers.getPointerSize();
        if (size == 0) {
            mInputPointers.addPointer(x, y, mPointerId, time);
            if (!isHistorical) {
                updateLastPoint(x, y, time);
            }
            return;
        }

        final int[] xCoords = mInputPointers.getXCoordinates();
        final int[] yCoords = mInputPointers.getYCoordinates();
        final int lastX = xCoords[size - 1];
        final int lastY = yCoords[size - 1];
        final float dist = getDistance(lastX, lastY, x, y);
        if (dist > mMinGestureSampleLength) {
            mInputPointers.addPointer(x, y, mPointerId, time);
            mLength += dist;
            final float angle = getAngle(lastX, lastY, x, y);
            if (size > 1) {
                float curvature = getAngleDiff(angle, mAngle);
                if (curvature > GESTURE_RECOG_CURVATURE_THRESHOLD) {
                    if (size > mIncrementalRecognitionPoint) {
                        mIncrementalRecognitionPoint = size;
                    }
                    mHasSharpCorner = true;
                }
                if (!mHasSharpCorner) {
                    mIncrementalRecognitionPoint = size;
                }
            }
            mAngle = angle;
        }

        if (!isHistorical) {
            final int duration = (int)(time - mLastPointTime);
            if (mLastPointTime != 0 && duration > 0) {
                final float speed = getDistance(mLastPointX, mLastPointY, x, y) / duration;
                if (speed < GESTURE_RECOG_SPEED_THRESHOLD) {
                    mIncrementalRecognitionPoint = size;
                }
            }
            updateLastPoint(x, y, time);
        }
    }

    public void appendAllBatchPoints(final InputPointers out) {
        out.append(mInputPointers, 0, mInputPointers.getPointerSize());
    }

    public void appendIncrementalBatchPoints(final InputPointers out) {
        out.append(mInputPointers, 0, mIncrementalRecognitionPoint);
    }

    private static float getDistance(final int p1x, final int p1y,
            final int p2x, final int p2y) {
        final float dx = p1x - p2x;
        final float dy = p1y - p2y;
        // TODO: Optimize out this {@link FloatMath#sqrt(float)} call.
        return FloatMath.sqrt(dx * dx + dy * dy);
    }

    private static float getAngle(final int p1x, final int p1y, final int p2x, final int p2y) {
        final int dx = p1x - p2x;
        final int dy = p1y - p2y;
        if (dx == 0 && dy == 0) return 0;
        return (float)Math.atan2(dy, dx);
    }

    private static float getAngleDiff(final float a1, final float a2) {
        final float diff = Math.abs(a1 - a2);
        if (diff > Math.PI) {
            return DOUBLE_PI - diff;
        }
        return diff;
    }
}
