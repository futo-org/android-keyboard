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
import android.util.SparseArray;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.InputPointers;
import com.android.inputmethod.latin.SuggestedWords;

// TODO: Remove this class by consolidating with PointerTracker
public class GestureTracker {
    private static final String TAG = GestureTracker.class.getSimpleName();
    private static final boolean DEBUG_LISTENER = false;

    // TODO: There should be an option to turn on/off the gesture input.
    private static final boolean GESTURE_ON = true;

    private static final GestureTracker sInstance = new GestureTracker();

    private static final int MIN_RECOGNITION_TIME = 100;
    private static final int MIN_GESTURE_DURATION = 200;

    private static final float GESTURE_RECOG_SPEED_THRESHOLD = 0.4f;
    private static final float SQUARED_GESTURE_RECOG_SPEED_THRESHOLD =
            GESTURE_RECOG_SPEED_THRESHOLD * GESTURE_RECOG_SPEED_THRESHOLD;
    private static final float GESTURE_RECOG_CURVATURE_THRESHOLD = (float) (Math.PI / 4);

    private boolean mIsAlphabetKeyboard;
    private boolean mIsPossibleGesture = false;
    private boolean mInGesture = false;

    private KeyboardActionListener mListener;
    private SuggestedWords mSuggestions;

    private final SparseArray<GestureStroke> mGestureStrokes = new SparseArray<GestureStroke>();

    private int mLastRecognitionPointSize = 0;
    private long mLastRecognitionTime = 0;

    public static void init(KeyboardActionListener listner) {
        sInstance.mListener = listner;
    }

    public static GestureTracker getInstance() {
        return sInstance;
    }

    private GestureTracker() {
    }

    public void setKeyboard(Keyboard keyboard) {
        mIsAlphabetKeyboard = keyboard.mId.isAlphabetKeyboard();
        GestureStroke.setGestureSampleLength(keyboard.mMostCommonKeyWidth / 2,
                keyboard.mMostCommonKeyHeight / 6);
    }

    private void startBatchInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, "onStartBatchInput");
        }
        mInGesture = true;
        mListener.onStartBatchInput();
        mSuggestions = null;
    }

    // TODO: The corresponding startBatchInput() is a private method. Reorganize the code.
    public void endBatchInput() {
        if (isInGesture() && mSuggestions != null && mSuggestions.size() > 0) {
            final CharSequence text = mSuggestions.getWord(0);
            if (DEBUG_LISTENER) {
                Log.d(TAG, "onEndBatchInput: text=" + text);
            }
            mListener.onEndBatchInput(text);
        }
        mInGesture = false;
        clearBatchInputPoints();
    }

    public void abortBatchInput() {
        mIsPossibleGesture = false;
        mInGesture = false;
    }

    public boolean isInGesture() {
        return mInGesture;
    }

    public void onDownEvent(PointerTracker tracker, int x, int y, long eventTime, Key key) {
        mIsPossibleGesture = false;
        if (GESTURE_ON && mIsAlphabetKeyboard && key != null && !key.isModifier()) {
            mIsPossibleGesture = true;
            addPointToStroke(x, y, 0, tracker.mPointerId, false);
        }
    }

    public void onMoveEvent(PointerTracker tracker, int x, int y, long eventTime,
            boolean isHistorical, Key key) {
        final int gestureTime = (int)(eventTime - tracker.getDownTime());
        if (GESTURE_ON && mIsPossibleGesture) {
            final GestureStroke stroke = addPointToStroke(x, y, gestureTime, tracker.mPointerId,
                    isHistorical);
            if (!isInGesture() && stroke.isStartOfAGesture(gestureTime)) {
                startBatchInput();
            }
        }

        if (key != null && isInGesture()) {
            final InputPointers batchPoints = getIncrementalBatchPoints();
            if (updateBatchInputRecognitionState(eventTime, batchPoints.getPointerSize())) {
                if (DEBUG_LISTENER) {
                    Log.d(TAG, "onUpdateBatchInput: batchPoints=" + batchPoints.getPointerSize());
                }
                mSuggestions = mListener.onUpdateBatchInput(batchPoints);
            }
        }
    }

    public void onUpEvent(PointerTracker tracker, int x, int y, long eventTime) {
        if (isInGesture()) {
            final InputPointers batchPoints = getAllBatchPoints();
            if (DEBUG_LISTENER) {
                Log.d(TAG, "onUpdateBatchInput: batchPoints=" + batchPoints.getPointerSize());
            }
            mSuggestions = mListener.onUpdateBatchInput(batchPoints);
        }
    }

    private GestureStroke addPointToStroke(int x, int y, int time, int pointerId,
            boolean isHistorical) {
        GestureStroke stroke = mGestureStrokes.get(pointerId);
        if (stroke == null) {
            stroke = new GestureStroke(pointerId);
            mGestureStrokes.put(pointerId, stroke);
        }
        stroke.addPoint(x, y, time, isHistorical);
        return stroke;
    }

    // The working and return object of the following methods, {@link #getIncrementalBatchPoints()}
    // and {@link #getAllBatchPoints()}.
    private final InputPointers mAggregatedPointers = new InputPointers();

    private InputPointers getIncrementalBatchPoints() {
        final InputPointers pointers = mAggregatedPointers;
        pointers.reset();
        final int strokeSize = mGestureStrokes.size();
        for (int index = 0; index < strokeSize; index++) {
            final GestureStroke stroke = mGestureStrokes.valueAt(index);
            stroke.appendIncrementalBatchPoints(pointers);
        }
        return pointers;
    }

    private InputPointers getAllBatchPoints() {
        final InputPointers pointers = mAggregatedPointers;
        pointers.reset();
        final int strokeSize = mGestureStrokes.size();
        for (int index = 0; index < strokeSize; index++) {
            final GestureStroke stroke = mGestureStrokes.valueAt(index);
            stroke.appendAllBatchPoints(pointers);
        }
        return pointers;
    }

    private void clearBatchInputPoints() {
        final int strokeSize = mGestureStrokes.size();
        for (int index = 0; index < strokeSize; index++) {
            final GestureStroke stroke = mGestureStrokes.valueAt(index);
            stroke.reset();
        }
        mLastRecognitionPointSize = 0;
        mLastRecognitionTime = 0;
    }

    private boolean updateBatchInputRecognitionState(long eventTime, int size) {
        if (size > mLastRecognitionPointSize
                && eventTime > mLastRecognitionTime + MIN_RECOGNITION_TIME) {
            mLastRecognitionPointSize = size;
            mLastRecognitionTime = eventTime;
            return true;
        }
        return false;
    }

    private static class GestureStroke {
        private final int mPointerId;
        private final InputPointers mInputPointers = new InputPointers();
        private float mLength;
        private float mAngle;
        private int mIncrementalRecognitionPoint;
        private boolean mHasSharpCorner;
        private long mLastPointTime;
        private int mLastPointX;
        private int mLastPointY;

        private static int sMinGestureLength;
        private static int sSquaredGestureSampleLength;

        private static final float DOUBLE_PI = (float)(2 * Math.PI);

        public static void setGestureSampleLength(final int minGestureLength,
                final int sampleLength) {
            sMinGestureLength = minGestureLength;
            sSquaredGestureSampleLength = sampleLength * sampleLength;
        }

        public GestureStroke(int pointerId) {
            mPointerId = pointerId;
            reset();
        }

        public boolean isStartOfAGesture(int downDuration) {
            return downDuration > MIN_GESTURE_DURATION / 2  && mLength > sMinGestureLength / 2;
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
            final float dist = squaredDistance(lastX, lastY, x, y);
            if (dist > sSquaredGestureSampleLength) {
                mInputPointers.addPointer(x, y, mPointerId, time);
                mLength += dist;
                final float angle = angle(lastX, lastY, x, y);
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
                    final int squaredDuration = duration * duration;
                    final float squaredSpeed =
                            squaredDistance(mLastPointX, mLastPointY, x, y) / squaredDuration;
                    if (squaredSpeed < SQUARED_GESTURE_RECOG_SPEED_THRESHOLD) {
                        mIncrementalRecognitionPoint = size;
                    }
                }
                updateLastPoint(x, y, time);
            }
        }

        private float getAngleDiff(float a1, float a2) {
            final float diff = Math.abs(a1 - a2);
            if (diff > Math.PI) {
                return DOUBLE_PI - diff;
            }
            return diff;
        }

        public void appendAllBatchPoints(InputPointers out) {
            out.append(mInputPointers, 0, mInputPointers.getPointerSize());
        }

        public void appendIncrementalBatchPoints(InputPointers out) {
            out.append(mInputPointers, 0, mIncrementalRecognitionPoint);
        }
    }

    static float squaredDistance(int p1x, int p1y, int p2x, int p2y) {
        final float dx = p1x - p2x;
        final float dy = p1y - p2y;
        return dx * dx + dy * dy;
    }

    static float angle(int p1x, int p1y, int p2x, int p2y) {
        final int dx = p1x - p2x;
        final int dy = p1y - p2y;
        if (dx == 0 && dy == 0) return 0;
        return (float)Math.atan2(dy, dx);
    }
}
