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
import android.view.ViewConfiguration;

public class PointerTracker {
    public interface UIProxy {
        public void invalidateKey(Key key);
        public void showPreview(int keyIndex, PointerTracker tracker);
        // TODO: These methods might be temporary.
        public void dismissPopupKeyboard();
        public boolean isMiniKeyboardOnScreen();
    }

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
    private final ProximityKeyDetector mKeyDetector;
    private OnKeyboardActionListener mListener;

    private Key[] mKeys;
    private int mKeyDebounceThresholdSquared = -1;

    private int mCurrentKey = NOT_A_KEY;
    private int mStartX;
    private int mStartY;

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

    public PointerTracker(UIHandler handler, ProximityKeyDetector keyDetector, UIProxy proxy) {
        if (proxy == null || handler == null || keyDetector == null)
            throw new NullPointerException();
        mProxy = proxy;
        mHandler = handler;
        mKeyDetector = keyDetector;
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
    }

    public Key getKey(int keyIndex) {
        return (keyIndex >= 0 && keyIndex < mKeys.length) ? mKeys[keyIndex] : null;
    }

    public void updateKey(int keyIndex) {
        int oldKeyIndex = mPreviousKey;
        mPreviousKey = keyIndex;
        if (keyIndex != oldKeyIndex) {
            if (oldKeyIndex != NOT_A_KEY && oldKeyIndex < mKeys.length) {
                // if new key index is not a key, old key was just released inside of the key.
                final boolean inside = (keyIndex == NOT_A_KEY);
                mKeys[oldKeyIndex].onReleased(inside);
                mProxy.invalidateKey(mKeys[oldKeyIndex]);
            }
            if (keyIndex != NOT_A_KEY && keyIndex < mKeys.length) {
                mKeys[keyIndex].onPressed();
                mProxy.invalidateKey(mKeys[keyIndex]);
            }
        }
    }

    public void onDownEvent(int touchX, int touchY, long eventTime) {
        int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(touchX, touchY, null);
        mCurrentKey = keyIndex;
        mStartX = touchX;
        mStartY = touchY;
        startMoveDebouncing(touchX, touchY);
        startTimeDebouncing(eventTime);
        checkMultiTap(eventTime, keyIndex);
        if (mListener != null) {
            int primaryCode = (keyIndex != NOT_A_KEY) ? mKeys[keyIndex].codes[0] : 0;
            mListener.onPress(primaryCode);
        }
        if (keyIndex >= 0 && mKeys[keyIndex].repeatable) {
            repeatKey(keyIndex);
            mHandler.startKeyRepeatTimer(REPEAT_START_DELAY, keyIndex, this);
        }
        if (keyIndex != NOT_A_KEY) {
            mHandler.startLongPressTimer(keyIndex, LONGPRESS_TIMEOUT);
        }
        showKeyPreviewAndUpdateKey(keyIndex);
        updateMoveDebouncing(touchX, touchY);
    }

    public void onMoveEvent(int touchX, int touchY, long eventTime) {
        int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(touchX, touchY, null);
        if (keyIndex != NOT_A_KEY) {
            if (mCurrentKey == NOT_A_KEY) {
                updateTimeDebouncing(eventTime);
                mCurrentKey = keyIndex;
                mHandler.startLongPressTimer(keyIndex, LONGPRESS_TIMEOUT);
            } else if (isMinorMoveBounce(touchX, touchY, keyIndex, mCurrentKey)) {
                updateTimeDebouncing(eventTime);
            } else {
                resetMultiTap();
                resetTimeDebouncing(eventTime, mCurrentKey);
                resetMoveDebouncing();
                mCurrentKey = keyIndex;
                mHandler.startLongPressTimer(keyIndex, LONGPRESS_TIMEOUT);
            }
        } else {
            mHandler.cancelLongPressTimer();
        }
        /*
         * While time debouncing is in effect, mCurrentKey holds the new key and this tracker
         * holds the last key.  At ACTION_UP event if time debouncing will be in effect
         * eventually, the last key should be sent as the result.  In such case mCurrentKey
         * should not be showed as popup preview.
         */
        showKeyPreviewAndUpdateKey(isMinorTimeBounce() ? mLastKey : mCurrentKey);
        updateMoveDebouncing(touchX, touchY);
    }

    public void onUpEvent(int touchX, int touchY, long eventTime) {
        int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(touchX, touchY, null);
        boolean wasInKeyRepeat = mHandler.isInKeyRepeat();
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        if (isMinorMoveBounce(touchX, touchY, keyIndex, mCurrentKey)) {
            updateTimeDebouncing(eventTime);
        } else {
            resetMultiTap();
            resetTimeDebouncing(eventTime, mCurrentKey);
            mCurrentKey = keyIndex;
        }
        if (isMinorTimeBounce()) {
            mCurrentKey = mLastKey;
            touchX = mLastCodeX;
            touchY = mLastCodeY;
        }
        showKeyPreviewAndUpdateKey(NOT_A_KEY);
        // If we're not on a repeating key (which sends on a DOWN event)
        if (!wasInKeyRepeat && !mProxy.isMiniKeyboardOnScreen()) {
            detectAndSendKey(mCurrentKey, touchX, touchY, eventTime);
        }
        if (keyIndex != NOT_A_KEY && keyIndex < mKeys.length)
            mProxy.invalidateKey(mKeys[keyIndex]);
    }

    public void onCancelEvent(int touchX, int touchY, long eventTime) {
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        mProxy.dismissPopupKeyboard();
        showKeyPreviewAndUpdateKey(NOT_A_KEY);
        int keyIndex = mCurrentKey;
        if (keyIndex != NOT_A_KEY && keyIndex < mKeys.length)
           mProxy.invalidateKey(mKeys[keyIndex]);
    }

    public void repeatKey(int keyIndex) {
        Key key = mKeys[keyIndex];
        // While key is repeating, because there is no need to handle multi-tap key, we can pass
        // -1 as eventTime argument.
        detectAndSendKey(keyIndex, key.x, key.y, -1);
    }

    // These package scope methods are only for debugging purpose.
    /* package */ int getStartX() {
        return mStartX;
    }

    /* package */ int getStartY() {
        return mStartY;
    }

    /* package */ int getLastX() {
        return mLastX;
    }

    /* package */ int getLastY() {
        return mLastY;
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
        } else if (curKey >= 0 && curKey < mKeys.length) {
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
        mProxy.showPreview(keyIndex, this);
    }

    private void detectAndSendKey(int index, int x, int y, long eventTime) {
        if (index != NOT_A_KEY && index < mKeys.length) {
            final Key key = mKeys[index];
            OnKeyboardActionListener listener = mListener;
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
        if (keyIndex == NOT_A_KEY) return;
        Key key = mKeys[keyIndex];
        if (key.codes.length > 1) {
            mInMultiTap = true;
            if (eventTime < mLastTapTime + MULTITAP_INTERVAL && keyIndex == mLastSentIndex) {
                mTapCount = (mTapCount + 1) % key.codes.length;
                return;
            } else {
                mTapCount = -1;
                return;
            }
        }
        if (eventTime > mLastTapTime + MULTITAP_INTERVAL || keyIndex != mLastSentIndex) {
            resetMultiTap();
        }
    }
}