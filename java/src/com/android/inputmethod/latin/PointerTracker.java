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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.LatinKeyboardBaseView.OnKeyboardActionListener;
import com.android.inputmethod.latin.LatinKeyboardBaseView.UIHandler;

import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class PointerTracker {
    private static final String TAG = "PointerTracker";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_MOVE = false;

    public interface UIProxy {
        public void invalidateKey(Key key);
        public void showPreview(int keyIndex, PointerTracker tracker);
    }

    public final int mPointerId;

    // Timing constants
    private static final int REPEAT_START_DELAY = 400;
    /* package */  static final int REPEAT_INTERVAL = 50; // ~20 keys per second
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int MULTITAP_INTERVAL = 800; // milliseconds
    private static final int KEY_DEBOUNCE_TIME = 70;

    // Miscellaneous constants
    private static final int NOT_A_KEY = LatinKeyboardBaseView.NOT_A_KEY;
    private static final int[] KEY_DELETE = { Keyboard.KEYCODE_DELETE };

    private final UIProxy mProxy;
    private final UIHandler mHandler;
    private final KeyDetector mKeyDetector;
    private OnKeyboardActionListener mListener;
    private final boolean mHasDistinctMultitouch;

    private Key[] mKeys;
    private int mKeyDebounceThresholdSquared = -1;

    private int mCurrentKey = NOT_A_KEY;
    private int mStartX;
    private int mStartY;
    private long mDownTime;

    // true if event is already translated to a key action (long press or mini-keyboard)
    private boolean mKeyAlreadyProcessed;

    // true if this pointer is repeatable key
    private boolean mIsRepeatableKey;

    // for move de-bouncing
    private int mLastCodeX;
    private int mLastCodeY;
    private int mLastX;
    private int mLastY;

    // for time de-bouncing
    private int mLastKey;
    private long mLastKeyTime;
    private long mLastMoveTime;
    private long mCurrentKeyTime;

    // For multi-tap
    private int mLastSentIndex;
    private int mTapCount;
    private long mLastTapTime;
    private boolean mInMultiTap;
    private final StringBuilder mPreviewLabel = new StringBuilder(1);

    // pressed key
    private int mPreviousKey = NOT_A_KEY;

    public PointerTracker(int id, UIHandler handler, KeyDetector keyDetector, UIProxy proxy,
            boolean hasDistinctMultitouch) {
        if (proxy == null || handler == null || keyDetector == null)
            throw new NullPointerException();
        mPointerId = id;
        mProxy = proxy;
        mHandler = handler;
        mKeyDetector = keyDetector;
        mHasDistinctMultitouch = hasDistinctMultitouch;
        resetMultiTap();
    }

    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        mListener = listener;
    }

    public void setKeyboard(Key[] keys, float hysteresisPixel) {
        if (keys == null || hysteresisPixel < 1.0f)
            throw new IllegalArgumentException();
        mKeys = keys;
        mKeyDebounceThresholdSquared = (int)(hysteresisPixel * hysteresisPixel);
        // Update current key index because keyboard layout has been changed.
        mCurrentKey = mKeyDetector.getKeyIndexAndNearbyCodes(mStartX, mStartY, null);
    }

    private boolean isValidKeyIndex(int keyIndex) {
        return keyIndex >= 0 && keyIndex < mKeys.length;
    }

    public Key getKey(int keyIndex) {
        return isValidKeyIndex(keyIndex) ? mKeys[keyIndex] : null;
    }

    public boolean isModifier() {
        Key key = getKey(mCurrentKey);
        if (key == null)
            return false;
        int primaryCode = key.codes[0];
        return primaryCode == Keyboard.KEYCODE_SHIFT
                || primaryCode == Keyboard.KEYCODE_MODE_CHANGE;
    }

    public void updateKey(int keyIndex) {
        if (mKeyAlreadyProcessed)
            return;
        int oldKeyIndex = mPreviousKey;
        mPreviousKey = keyIndex;
        if (keyIndex != oldKeyIndex) {
            if (isValidKeyIndex(oldKeyIndex)) {
                // if new key index is not a key, old key was just released inside of the key.
                final boolean inside = (keyIndex == NOT_A_KEY);
                mKeys[oldKeyIndex].onReleased(inside);
                mProxy.invalidateKey(mKeys[oldKeyIndex]);
            }
            if (isValidKeyIndex(keyIndex)) {
                mKeys[keyIndex].onPressed();
                mProxy.invalidateKey(mKeys[keyIndex]);
            }
        }
    }

    public void setAlreadyProcessed() {
        mKeyAlreadyProcessed = true;
    }

    public void onTouchEvent(int action, int x, int y, long eventTime) {
        switch (action) {
        case MotionEvent.ACTION_MOVE:
            onMoveEvent(x, y, eventTime);
            break;
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            onDownEvent(x, y, eventTime);
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            onUpEvent(x, y, eventTime);
            break;
        case MotionEvent.ACTION_CANCEL:
            onCancelEvent(x, y, eventTime);
            break;
        }
    }

    public void onDownEvent(int x, int y, long eventTime) {
        if (DEBUG)
            debugLog("onDownEvent:", x, y);
        int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null);
        mCurrentKey = keyIndex;
        mStartX = x;
        mStartY = y;
        mDownTime = eventTime;
        mKeyAlreadyProcessed = false;
        mIsRepeatableKey = false;
        startMoveDebouncing(x, y);
        startTimeDebouncing(eventTime);
        checkMultiTap(eventTime, keyIndex);
        if (mListener != null) {
            int primaryCode = isValidKeyIndex(keyIndex) ? mKeys[keyIndex].codes[0] : 0;
            mListener.onPress(primaryCode);
            // This onPress call may have changed keyboard layout and have updated mCurrentKey
            keyIndex = mCurrentKey;
        }
        if (isValidKeyIndex(keyIndex)) {
            if (mKeys[keyIndex].repeatable) {
                repeatKey(keyIndex);
                mHandler.startKeyRepeatTimer(REPEAT_START_DELAY, keyIndex, this);
                mIsRepeatableKey = true;
            }
            mHandler.startLongPressTimer(LONGPRESS_TIMEOUT, keyIndex, this);
        }
        showKeyPreviewAndUpdateKey(keyIndex);
        updateMoveDebouncing(x, y);
    }

    public void onMoveEvent(int x, int y, long eventTime) {
        if (DEBUG_MOVE)
            debugLog("onMoveEvent:", x, y);
        if (mKeyAlreadyProcessed)
            return;
        int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null);
        if (isValidKeyIndex(keyIndex)) {
            if (mCurrentKey == NOT_A_KEY) {
                updateTimeDebouncing(eventTime);
                mCurrentKey = keyIndex;
                mHandler.startLongPressTimer(LONGPRESS_TIMEOUT, keyIndex, this);
            } else if (isMinorMoveBounce(x, y, keyIndex, mCurrentKey)) {
                updateTimeDebouncing(eventTime);
            } else {
                resetMultiTap();
                resetTimeDebouncing(eventTime, mCurrentKey);
                resetMoveDebouncing();
                mCurrentKey = keyIndex;
                mHandler.startLongPressTimer(LONGPRESS_TIMEOUT, keyIndex, this);
            }
        } else {
            if (mCurrentKey != NOT_A_KEY) {
                updateTimeDebouncing(eventTime);
                mCurrentKey = keyIndex;
                mHandler.cancelLongPressTimer();
            } else if (isMinorMoveBounce(x, y, keyIndex, mCurrentKey)) {
                updateTimeDebouncing(eventTime);
            } else {
                resetMultiTap();
                resetTimeDebouncing(eventTime, mCurrentKey);
                resetMoveDebouncing();
                mCurrentKey = keyIndex;
                mHandler.cancelLongPressTimer();
            }
        }
        /*
         * While time debouncing is in effect, mCurrentKey holds the new key and this tracker
         * holds the last key.  At ACTION_UP event if time debouncing will be in effect
         * eventually, the last key should be sent as the result.  In such case mCurrentKey
         * should not be showed as popup preview.
         */
        showKeyPreviewAndUpdateKey(isMinorTimeBounce() ? mLastKey : mCurrentKey);
        updateMoveDebouncing(x, y);
    }

    public void onUpEvent(int x, int y, long eventTime) {
        if (DEBUG)
            debugLog("onUpEvent  :", x, y);
        if (mKeyAlreadyProcessed)
            return;
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null);
        if (isMinorMoveBounce(x, y, keyIndex, mCurrentKey)) {
            updateTimeDebouncing(eventTime);
        } else {
            resetMultiTap();
            resetTimeDebouncing(eventTime, mCurrentKey);
            mCurrentKey = keyIndex;
        }
        if (isMinorTimeBounce()) {
            mCurrentKey = mLastKey;
            x = mLastCodeX;
            y = mLastCodeY;
        }
        showKeyPreviewAndUpdateKey(NOT_A_KEY);
        if (!mIsRepeatableKey) {
            detectAndSendKey(mCurrentKey, x, y, eventTime);
        }
        if (isValidKeyIndex(keyIndex))
            mProxy.invalidateKey(mKeys[keyIndex]);
    }

    public void onCancelEvent(int x, int y, long eventTime) {
        if (DEBUG)
            debugLog("onCancelEvt:", x, y);
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        showKeyPreviewAndUpdateKey(NOT_A_KEY);
        int keyIndex = mCurrentKey;
        if (isValidKeyIndex(keyIndex))
           mProxy.invalidateKey(mKeys[keyIndex]);
    }

    public void repeatKey(int keyIndex) {
        Key key = getKey(keyIndex);
        if (key != null) {
            // While key is repeating, because there is no need to handle multi-tap key, we can
            // pass -1 as eventTime argument.
            detectAndSendKey(keyIndex, key.x, key.y, -1);
        }
    }

    public int getLastX() {
        return mLastX;
    }

    public int getLastY() {
        return mLastY;
    }

    public long getDownTime() {
        return mDownTime;
    }

    // These package scope methods are only for debugging purpose.
    /* package */ int getStartX() {
        return mStartX;
    }

    /* package */ int getStartY() {
        return mStartY;
    }

    private void startMoveDebouncing(int x, int y) {
        mLastCodeX = x;
        mLastCodeY = y;
    }

    private void updateMoveDebouncing(int x, int y) {
        mLastX = x;
        mLastY = y;
    }

    private void resetMoveDebouncing() {
        mLastCodeX = mLastX;
        mLastCodeY = mLastY;
    }

    private boolean isMinorMoveBounce(int x, int y, int newKey, int curKey) {
        if (mKeys == null || mKeyDebounceThresholdSquared < 0)
            throw new IllegalStateException("keyboard and/or hysteresis not set");
        if (newKey == curKey) {
            return true;
        } else if (isValidKeyIndex(curKey)) {
            return getSquareDistanceToKeyEdge(x, y, mKeys[curKey])
                    < mKeyDebounceThresholdSquared;
        } else {
            return false;
        }
    }

    private static int getSquareDistanceToKeyEdge(int x, int y, Key key) {
        final int left = key.x;
        final int right = key.x + key.width;
        final int top = key.y;
        final int bottom = key.y + key.height;
        final int edgeX = x < left ? left : (x > right ? right : x);
        final int edgeY = y < top ? top : (y > bottom ? bottom : y);
        final int dx = x - edgeX;
        final int dy = y - edgeY;
        return dx * dx + dy * dy;
    }

    private void startTimeDebouncing(long eventTime) {
        mLastKey = NOT_A_KEY;
        mLastKeyTime = 0;
        mCurrentKeyTime = 0;
        mLastMoveTime = eventTime;
    }

    private void updateTimeDebouncing(long eventTime) {
        mCurrentKeyTime += eventTime - mLastMoveTime;
        mLastMoveTime = eventTime;
    }

    private void resetTimeDebouncing(long eventTime, int currentKey) {
        mLastKey = currentKey;
        mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
        mCurrentKeyTime = 0;
        mLastMoveTime = eventTime;
    }

    private boolean isMinorTimeBounce() {
        return mCurrentKeyTime < mLastKeyTime && mCurrentKeyTime < KEY_DEBOUNCE_TIME
                && mLastKey != NOT_A_KEY;
    }

    private void showKeyPreviewAndUpdateKey(int keyIndex) {
        updateKey(keyIndex);
        // The modifier key, such as shift key, should not be shown as preview when multi-touch is
        // supported. On thge other hand, if multi-touch is not supported, the modifier key should
        // be shown as preview.
        if (mHasDistinctMultitouch && isModifier()) {
            mProxy.showPreview(NOT_A_KEY, this);
        } else {
            mProxy.showPreview(keyIndex, this);
        }
    }

    private void detectAndSendKey(int index, int x, int y, long eventTime) {
        final OnKeyboardActionListener listener = mListener;
        final Key key = getKey(index);

        if (key == null) {
            if (listener != null)
                listener.onCancel();
        } else {
            if (key.text != null) {
                if (listener != null) {
                    listener.onText(key.text);
                    listener.onRelease(NOT_A_KEY);
                }
            } else {
                int code = key.codes[0];
                //TextEntryState.keyPressedAt(key, x, y);
                int[] codes = mKeyDetector.newCodeArray();
                mKeyDetector.getKeyIndexAndNearbyCodes(x, y, codes);
                // Multi-tap
                if (mInMultiTap) {
                    if (mTapCount != -1) {
                        mListener.onKey(Keyboard.KEYCODE_DELETE, KEY_DELETE, x, y);
                    } else {
                        mTapCount = 0;
                    }
                    code = key.codes[mTapCount];
                }
                /*
                 * Swap the first and second values in the codes array if the primary code is not
                 * the first value but the second value in the array. This happens when key
                 * debouncing is in effect.
                 */
                if (codes.length >= 2 && codes[0] != code && codes[1] == code) {
                    codes[1] = codes[0];
                    codes[0] = code;
                }
                if (listener != null) {
                    listener.onKey(code, codes, x, y);
                    listener.onRelease(code);
                }
            }
            mLastSentIndex = index;
            mLastTapTime = eventTime;
        }
    }

    /**
     * Handle multi-tap keys by producing the key label for the current multi-tap state.
     */
    public CharSequence getPreviewText(Key key) {
        if (mInMultiTap) {
            // Multi-tap
            mPreviewLabel.setLength(0);
            mPreviewLabel.append((char) key.codes[mTapCount < 0 ? 0 : mTapCount]);
            return mPreviewLabel;
        } else {
            return key.label;
        }
    }

    private void resetMultiTap() {
        mLastSentIndex = NOT_A_KEY;
        mTapCount = 0;
        mLastTapTime = -1;
        mInMultiTap = false;
    }

    private void checkMultiTap(long eventTime, int keyIndex) {
        Key key = getKey(keyIndex);
        if (key == null)
            return;

        final boolean isMultiTap =
                (eventTime < mLastTapTime + MULTITAP_INTERVAL && keyIndex == mLastSentIndex);
        if (key.codes.length > 1) {
            mInMultiTap = true;
            if (isMultiTap) {
                mTapCount = (mTapCount + 1) % key.codes.length;
                return;
            } else {
                mTapCount = -1;
                return;
            }
        }
        if (!isMultiTap) {
            resetMultiTap();
        }
    }

    private void debugLog(String title, int x, int y) {
        Key key = getKey(mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null));
        final String code;
        if (key == null) {
            code = "----";
        } else {
            int primaryCode = key.codes[0];
            code = String.format((primaryCode < 0) ? "%4d" : "0x%02x", primaryCode);
        }
        Log.d(TAG, String.format("%s [%d] %3d,%3d %s %s", title, mPointerId, x, y, code,
                isModifier() ? "modifier" : ""));
    }
}