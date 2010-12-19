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

import com.android.inputmethod.keyboard.KeyboardView.UIHandler;
import com.android.inputmethod.latin.R;

import android.content.res.Resources;
import android.util.Log;
import android.view.MotionEvent;

import java.util.Arrays;

public class PointerTracker {
    private static final String TAG = PointerTracker.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_MOVE_EVENT = false;
    private static final boolean DEBUG_LISTENER = false;

    public interface UIProxy {
        public void invalidateKey(Key key);
        public void showPreview(int keyIndex, PointerTracker tracker);
        public boolean hasDistinctMultitouch();
    }

    public final int mPointerId;

    // Timing constants
    private final int mDelayBeforeKeyRepeatStart;
    private final int mLongPressKeyTimeout;
    private final int mLongPressShiftKeyTimeout;
    private final int mMultiTapKeyTimeout;

    // Miscellaneous constants
    private static final int NOT_A_KEY = KeyDetector.NOT_A_KEY;
    private static final int[] KEY_DELETE = { Keyboard.CODE_DELETE };

    private final UIProxy mProxy;
    private final UIHandler mHandler;
    private final KeyDetector mKeyDetector;
    private KeyboardActionListener mListener = EMPTY_LISTENER;
    private final KeyboardSwitcher mKeyboardSwitcher;
    private final boolean mHasDistinctMultitouch;
    private final boolean mConfigSlidingKeyInputEnabled;

    private Keyboard mKeyboard;
    private Key[] mKeys;
    private int mKeyHysteresisDistanceSquared = -1;

    private final PointerTrackerKeyState mKeyState;

    // true if event is already translated to a key action (long press or mini-keyboard)
    private boolean mKeyAlreadyProcessed;

    // true if this pointer is repeatable key
    private boolean mIsRepeatableKey;

    // true if sliding key is allowed.
    private boolean mIsAllowedSlidingKeyInput;

    // For multi-tap
    private int mLastSentIndex;
    private int mTapCount;
    private long mLastTapTime;
    private boolean mInMultiTap;
    private final StringBuilder mPreviewLabel = new StringBuilder(1);

    // pressed key
    private int mPreviousKey = NOT_A_KEY;

    // Empty {@link KeyboardActionListener}
    private static final KeyboardActionListener EMPTY_LISTENER = new KeyboardActionListener() {
        @Override
        public void onPress(int primaryCode) {}
        @Override
        public void onRelease(int primaryCode) {}
        @Override
        public void onKey(int primaryCode, int[] keyCodes, int x, int y) {}
        @Override
        public void onText(CharSequence text) {}
        @Override
        public void onCancel() {}
        @Override
        public void swipeLeft() {}
        @Override
        public void swipeRight() {}
        @Override
        public void swipeDown() {}
        @Override
        public void swipeUp() {}
    };

    public PointerTracker(int id, UIHandler handler, KeyDetector keyDetector, UIProxy proxy,
            Resources res) {
        if (proxy == null || handler == null || keyDetector == null)
            throw new NullPointerException();
        mPointerId = id;
        mProxy = proxy;
        mHandler = handler;
        mKeyDetector = keyDetector;
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mKeyState = new PointerTrackerKeyState(keyDetector);
        mHasDistinctMultitouch = proxy.hasDistinctMultitouch();
        mConfigSlidingKeyInputEnabled = res.getBoolean(R.bool.config_sliding_key_input_enabled);
        mDelayBeforeKeyRepeatStart = res.getInteger(R.integer.config_delay_before_key_repeat_start);
        mLongPressKeyTimeout = res.getInteger(R.integer.config_long_press_key_timeout);
        mLongPressShiftKeyTimeout = res.getInteger(R.integer.config_long_press_shift_key_timeout);
        mMultiTapKeyTimeout = res.getInteger(R.integer.config_multi_tap_key_timeout);
        resetMultiTap();
    }

    public void setOnKeyboardActionListener(KeyboardActionListener listener) {
        mListener = listener;
    }

    private void callListenerOnPress(int primaryCode) {
        if (DEBUG_LISTENER)
            Log.d(TAG, "onPress    : " + keyCodePrintable(primaryCode));
        mListener.onPress(primaryCode);
    }

    private void callListenerOnKey(int primaryCode, int[] keyCodes, int x, int y) {
        if (DEBUG_LISTENER)
            Log.d(TAG, "onKey      : " + keyCodePrintable(primaryCode)
                    + " codes="+ Arrays.toString(keyCodes) + " x=" + x + " y=" + y);
        mListener.onKey(primaryCode, keyCodes, x, y);
    }

    private void callListenerOnText(CharSequence text) {
        if (DEBUG_LISTENER)
            Log.d(TAG, "onText     : text=" + text);
        mListener.onText(text);
    }

    private void callListenerOnRelease(int primaryCode) {
        if (DEBUG_LISTENER)
            Log.d(TAG, "onRelease  : " + keyCodePrintable(primaryCode));
        mListener.onRelease(primaryCode);
    }

    private void callListenerOnCancel() {
        if (DEBUG_LISTENER)
            Log.d(TAG, "onCancel");
        mListener.onCancel();
    }

    public void setKeyboard(Keyboard keyboard, Key[] keys, float keyHysteresisDistance) {
        if (keyboard == null || keys == null || keyHysteresisDistance < 0)
            throw new IllegalArgumentException();
        mKeyboard = keyboard;
        mKeys = keys;
        mKeyHysteresisDistanceSquared = (int)(keyHysteresisDistance * keyHysteresisDistance);
        // Update current key index because keyboard layout has been changed.
        mKeyState.onSetKeyboard();
    }

    private boolean isValidKeyIndex(int keyIndex) {
        return keyIndex >= 0 && keyIndex < mKeys.length;
    }

    public Key getKey(int keyIndex) {
        return isValidKeyIndex(keyIndex) ? mKeys[keyIndex] : null;
    }

    private static boolean isModifierCode(int primaryCode) {
        return primaryCode == Keyboard.CODE_SHIFT
                || primaryCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL;
    }

    private boolean isModifierInternal(int keyIndex) {
        final Key key = getKey(keyIndex);
        return key == null ? false : isModifierCode(key.mCodes[0]);
    }

    public boolean isModifier() {
        return isModifierInternal(mKeyState.getKeyIndex());
    }

    public boolean isOnModifierKey(int x, int y) {
        return isModifierInternal(mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null));
    }

    public boolean isOnShiftKey(int x, int y) {
        final Key key = getKey(mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null));
        return key != null && key.mCodes[0] == Keyboard.CODE_SHIFT;
    }

    public boolean isSpaceKey(int keyIndex) {
        Key key = getKey(keyIndex);
        return key != null && key.mCodes[0] == Keyboard.CODE_SPACE;
    }

    public void releaseKey() {
        updateKeyGraphics(NOT_A_KEY);
    }

    private void updateKeyGraphics(int keyIndex) {
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
        if (DEBUG_EVENT)
            printTouchEvent("onDownEvent:", x, y, eventTime);
        int keyIndex = mKeyState.onDownKey(x, y, eventTime);
        // Sliding key is allowed when 1) enabled by configuration, 2) this pointer starts sliding
        // from modifier key, or 3) this pointer is on mini-keyboard.
        mIsAllowedSlidingKeyInput = mConfigSlidingKeyInputEnabled || isModifierInternal(keyIndex)
                || mKeyDetector instanceof MiniKeyboardKeyDetector;
        mKeyAlreadyProcessed = false;
        mIsRepeatableKey = false;
        checkMultiTap(eventTime, keyIndex);
        if (isValidKeyIndex(keyIndex)) {
            callListenerOnPress(mKeys[keyIndex].mCodes[0]);
            // This onPress call may have changed keyboard layout and have updated mKeyIndex.
            // If that's the case, mKeyIndex has been updated in setKeyboard().
            keyIndex = mKeyState.getKeyIndex();
        }
        if (isValidKeyIndex(keyIndex)) {
            if (mKeys[keyIndex].mRepeatable) {
                repeatKey(keyIndex);
                mHandler.startKeyRepeatTimer(mDelayBeforeKeyRepeatStart, keyIndex, this);
                mIsRepeatableKey = true;
            }
            startLongPressTimer(keyIndex);
        }
        showKeyPreviewAndUpdateKeyGraphics(keyIndex);
    }

    public void onMoveEvent(int x, int y, long eventTime) {
        if (DEBUG_MOVE_EVENT)
            printTouchEvent("onMoveEvent:", x, y, eventTime);
        if (mKeyAlreadyProcessed)
            return;
        final PointerTrackerKeyState keyState = mKeyState;
        final int keyIndex = keyState.onMoveKey(x, y);
        final Key oldKey = getKey(keyState.getKeyIndex());
        if (isValidKeyIndex(keyIndex)) {
            if (oldKey == null) {
                // The pointer has been slid in to the new key, but the finger was not on any keys.
                // In this case, we must call onPress() to notify that the new key is being pressed.
                callListenerOnPress(getKey(keyIndex).mCodes[0]);
                keyState.onMoveToNewKey(keyIndex, x, y);
                startLongPressTimer(keyIndex);
            } else if (!isMinorMoveBounce(x, y, keyIndex)) {
                // The pointer has been slid in to the new key from the previous key, we must call
                // onRelease() first to notify that the previous key has been released, then call
                // onPress() to notify that the new key is being pressed.
                callListenerOnRelease(oldKey.mCodes[0]);
                mHandler.cancelLongPressTimers();
                if (mIsAllowedSlidingKeyInput) {
                    resetMultiTap();
                    callListenerOnPress(getKey(keyIndex).mCodes[0]);
                    keyState.onMoveToNewKey(keyIndex, x, y);
                    startLongPressTimer(keyIndex);
                } else {
                    setAlreadyProcessed();
                    showKeyPreviewAndUpdateKeyGraphics(NOT_A_KEY);
                    return;
                }
            }
        } else {
            if (oldKey != null && !isMinorMoveBounce(x, y, keyIndex)) {
                // The pointer has been slid out from the previous key, we must call onRelease() to
                // notify that the previous key has been released.
                callListenerOnRelease(oldKey.mCodes[0]);
                mHandler.cancelLongPressTimers();
                if (mIsAllowedSlidingKeyInput) {
                    resetMultiTap();
                    keyState.onMoveToNewKey(keyIndex, x ,y);
                } else {
                    setAlreadyProcessed();
                    showKeyPreviewAndUpdateKeyGraphics(NOT_A_KEY);
                    return;
                }
            }
        }
        showKeyPreviewAndUpdateKeyGraphics(mKeyState.getKeyIndex());
    }

    public void onUpEvent(int pointX, int pointY, long eventTime) {
        int x = pointX;
        int y = pointY;
        if (DEBUG_EVENT)
            printTouchEvent("onUpEvent  :", x, y, eventTime);
        showKeyPreviewAndUpdateKeyGraphics(NOT_A_KEY);
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        if (mKeyAlreadyProcessed)
            return;
        final PointerTrackerKeyState keyState = mKeyState;
        int keyIndex = keyState.onUpKey(x, y);
        if (isMinorMoveBounce(x, y, keyIndex)) {
            // Use previous fixed key index and coordinates.
            keyIndex = keyState.getKeyIndex();
            x = keyState.getKeyX();
            y = keyState.getKeyY();
        }
        if (!mIsRepeatableKey) {
            detectAndSendKey(keyIndex, x, y, eventTime);
        }

        if (isValidKeyIndex(keyIndex))
            mProxy.invalidateKey(mKeys[keyIndex]);
    }

    public void onCancelEvent(int x, int y, long eventTime) {
        if (DEBUG_EVENT)
            printTouchEvent("onCancelEvt:", x, y, eventTime);
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        showKeyPreviewAndUpdateKeyGraphics(NOT_A_KEY);
        int keyIndex = mKeyState.getKeyIndex();
        if (isValidKeyIndex(keyIndex))
           mProxy.invalidateKey(mKeys[keyIndex]);
    }

    public void repeatKey(int keyIndex) {
        Key key = getKey(keyIndex);
        if (key != null) {
            // While key is repeating, because there is no need to handle multi-tap key, we can
            // pass -1 as eventTime argument.
            detectAndSendKey(keyIndex, key.mX, key.mY, -1);
        }
    }

    public int getLastX() {
        return mKeyState.getLastX();
    }

    public int getLastY() {
        return mKeyState.getLastY();
    }

    public long getDownTime() {
        return mKeyState.getDownTime();
    }

    // These package scope methods are only for debugging purpose.
    /* package */ int getStartX() {
        return mKeyState.getStartX();
    }

    /* package */ int getStartY() {
        return mKeyState.getStartY();
    }

    private boolean isMinorMoveBounce(int x, int y, int newKey) {
        if (mKeys == null || mKeyHysteresisDistanceSquared < 0)
            throw new IllegalStateException("keyboard and/or hysteresis not set");
        int curKey = mKeyState.getKeyIndex();
        if (newKey == curKey) {
            return true;
        } else if (isValidKeyIndex(curKey)) {
            return mKeys[curKey].squaredDistanceToEdge(x, y) < mKeyHysteresisDistanceSquared;
        } else {
            return false;
        }
    }

    private void showKeyPreviewAndUpdateKeyGraphics(int keyIndex) {
        updateKeyGraphics(keyIndex);
        // The modifier key, such as shift key, should not be shown as preview when multi-touch is
        // supported. On the other hand, if multi-touch is not supported, the modifier key should
        // be shown as preview.
        if (mHasDistinctMultitouch && isModifier()) {
            mProxy.showPreview(NOT_A_KEY, this);
        } else {
            mProxy.showPreview(keyIndex, this);
        }
    }

    private void startLongPressTimer(int keyIndex) {
        Key key = getKey(keyIndex);
        if (key.mCodes[0] == Keyboard.CODE_SHIFT) {
            mHandler.startLongPressShiftTimer(mLongPressShiftKeyTimeout, keyIndex, this);
        } else if (mKeyboardSwitcher.isInMomentaryAutoModeSwitchState()) {
            // We use longer timeout for sliding finger input started from the symbols mode key.
            mHandler.startLongPressTimer(mLongPressKeyTimeout * 2, keyIndex, this);
        } else {
            mHandler.startLongPressTimer(mLongPressKeyTimeout, keyIndex, this);
        }
    }

    private void detectAndSendKey(int index, int x, int y, long eventTime) {
        final Key key = getKey(index);
        if (key == null) {
            callListenerOnCancel();
            return;
        }
        if (key.mOutputText != null) {
            callListenerOnText(key.mOutputText);
            callListenerOnRelease(key.mCodes[0]);
        } else {
            int code = key.mCodes[0];
            final int[] codes = mKeyDetector.newCodeArray();
            mKeyDetector.getKeyIndexAndNearbyCodes(x, y, codes);
            // Multi-tap
            if (mInMultiTap) {
                if (mTapCount != -1) {
                    callListenerOnKey(Keyboard.CODE_DELETE, KEY_DELETE, x, y);
                } else {
                    mTapCount = 0;
                }
                code = key.mCodes[mTapCount];
            }

            // If keyboard is in manual temporary upper case state and key has manual temporary
            // shift code, alternate character code should be sent.
            if (mKeyboard.isManualTemporaryUpperCase() && key.mManualTemporaryUpperCaseCode != 0) {
                code = key.mManualTemporaryUpperCaseCode;
                codes[0] = code;
            }

            // Swap the first and second values in the codes array if the primary code is not the
            // first value but the second value in the array. This happens when key debouncing is
            // in effect.
            if (codes.length >= 2 && codes[0] != code && codes[1] == code) {
                codes[1] = codes[0];
                codes[0] = code;
            }
            callListenerOnKey(code, codes, x, y);
            callListenerOnRelease(code);
        }
        mLastSentIndex = index;
        mLastTapTime = eventTime;
    }

    /**
     * Handle multi-tap keys by producing the key label for the current multi-tap state.
     */
    public CharSequence getPreviewText(Key key) {
        if (mInMultiTap) {
            // Multi-tap
            mPreviewLabel.setLength(0);
            mPreviewLabel.append((char) key.mCodes[mTapCount < 0 ? 0 : mTapCount]);
            return mPreviewLabel;
        } else {
            return key.mLabel;
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
                (eventTime < mLastTapTime + mMultiTapKeyTimeout && keyIndex == mLastSentIndex);
        if (key.mCodes.length > 1) {
            mInMultiTap = true;
            if (isMultiTap) {
                mTapCount = (mTapCount + 1) % key.mCodes.length;
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

    private long mPreviousEventTime;

    private void printTouchEvent(String title, int x, int y, long eventTime) {
        final int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null);
        final Key key = getKey(keyIndex);
        final String code = (key == null) ? "----" : keyCodePrintable(key.mCodes[0]);
        final long delta = eventTime - mPreviousEventTime;
        Log.d(TAG, String.format("%s%s[%d] %4d %4d %5d %3d(%s)", title,
                (mKeyAlreadyProcessed ? "-" : " "), mPointerId, x, y, delta, keyIndex, code));
        mPreviousEventTime = eventTime;
    }

    private static String keyCodePrintable(int primaryCode) {
        final String modifier = isModifierCode(primaryCode) ? " modifier" : "";
        return  String.format((primaryCode < 0) ? "%4d" : "0x%02x", primaryCode) + modifier;
    }
}
