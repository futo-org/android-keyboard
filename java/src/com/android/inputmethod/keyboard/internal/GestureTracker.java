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

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.InputPointers;

// TODO: Remove this class by consolidating with PointerTracker
public class GestureTracker {
    private static final String TAG = GestureTracker.class.getSimpleName();
    private static final boolean DEBUG_LISTENER = false;

    // TODO: There should be an option to turn on/off the gesture input.
    private static final boolean GESTURE_ON = true;

    private static final GestureTracker sInstance = new GestureTracker();

    private static final int MIN_RECOGNITION_TIME = 100;

    private boolean mIsAlphabetKeyboard;
    private boolean mIsPossibleGesture = false;
    private boolean mInGesture = false;

    private KeyboardActionListener mListener;

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
    }

    private void startBatchInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, "onStartBatchInput");
        }
        mInGesture = true;
        mListener.onStartBatchInput();
    }

    // TODO: The corresponding startBatchInput() is a private method. Reorganize the code.
    public void endBatchInput() {
        if (isInGesture()) {
            final InputPointers batchPoints = PointerTracker.getAllBatchPoints();
            if (DEBUG_LISTENER) {
                Log.d(TAG, "onEndBatchInput: batchPoints=" + batchPoints.getPointerSize());
            }
            mListener.onEndBatchInput(batchPoints);
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
        // A gesture should start only from the letter key.
        if (GESTURE_ON && mIsAlphabetKeyboard && key != null && Keyboard.isLetterCode(key.mCode)) {
            mIsPossibleGesture = true;
            tracker.getGestureStroke().addPoint(x, y, 0, false);
        }
    }

    public void onMoveEvent(PointerTracker tracker, int x, int y, long eventTime,
            boolean isHistorical, Key key) {
        final int gestureTime = (int)(eventTime - tracker.getDownTime());
        if (GESTURE_ON && mIsPossibleGesture) {
            final GestureStroke stroke = tracker.getGestureStroke();
            stroke.addPoint(x, y, gestureTime, isHistorical);
            if (!isInGesture() && stroke.isStartOfAGesture(gestureTime)) {
                startBatchInput();
            }
        }

        if (key != null && isInGesture()) {
            final InputPointers batchPoints = PointerTracker.getIncrementalBatchPoints();
            if (updateBatchInputRecognitionState(eventTime, batchPoints.getPointerSize())) {
                if (DEBUG_LISTENER) {
                    Log.d(TAG, "onUpdateBatchInput: batchPoints=" + batchPoints.getPointerSize());
                }
                mListener.onUpdateBatchInput(batchPoints);
            }
        }
    }

    public void onUpEvent(PointerTracker tracker, int x, int y, long eventTime) {
        if (isInGesture()) {
            final InputPointers batchPoints = PointerTracker.getAllBatchPoints();
            if (DEBUG_LISTENER) {
                Log.d(TAG, "onUpdateBatchInput: batchPoints=" + batchPoints.getPointerSize());
            }
            mListener.onUpdateBatchInput(batchPoints);
        }
    }

    private void clearBatchInputPoints() {
        PointerTracker.clearBatchInputPoints();
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
}
