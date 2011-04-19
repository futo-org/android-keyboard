/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.inputmethod.keyboard;

/**
 * This class keeps track of a key index and a position where {@link PointerTracker} is.
 */
/* package */ class PointerTrackerKeyState {
    private final KeyDetector mKeyDetector;

    // The position and time at which first down event occurred.
    private long mDownTime;
    private long mUpTime;

    // The current key index where this pointer is.
    private int mKeyIndex = KeyDetector.NOT_A_KEY;
    // The position where mKeyIndex was recognized for the first time.
    private int mKeyX;
    private int mKeyY;

    // Last pointer position.
    private int mLastX;
    private int mLastY;

    public PointerTrackerKeyState(KeyDetector keyDetecor) {
        mKeyDetector = keyDetecor;
    }

    public int getKeyIndex() {
        return mKeyIndex;
    }

    public int getKeyX() {
        return mKeyX;
    }

    public int getKeyY() {
        return mKeyY;
    }

    public long getDownTime() {
        return mDownTime;
    }

    public long getUpTime() {
        return mUpTime;
    }

    public int getLastX() {
        return mLastX;
    }

    public int getLastY() {
        return mLastY;
    }

    public int onDownKey(int x, int y, long eventTime) {
        mDownTime = eventTime;
        return onMoveToNewKey(onMoveKeyInternal(x, y), x, y);
    }

    private int onMoveKeyInternal(int x, int y) {
        mLastX = x;
        mLastY = y;
        return mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null);
    }

    public int onMoveKey(int x, int y) {
        return onMoveKeyInternal(x, y);
    }

    public int onMoveToNewKey(int keyIndex, int x, int y) {
        mKeyIndex = keyIndex;
        mKeyX = x;
        mKeyY = y;
        return keyIndex;
    }

    public int onUpKey(int x, int y, long eventTime) {
        mUpTime = eventTime;
        mKeyIndex = KeyDetector.NOT_A_KEY;
        return onMoveKeyInternal(x, y);
    }
}
