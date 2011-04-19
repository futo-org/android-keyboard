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
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;

import android.content.res.Resources;
import android.util.Log;
import android.view.MotionEvent;

import java.util.Arrays;
import java.util.List;

public class PointerTracker {
    private static final String TAG = PointerTracker.class.getSimpleName();
    private static final boolean ENABLE_ASSERTION = false;
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_MOVE_EVENT = false;
    private static final boolean DEBUG_LISTENER = false;
    private static boolean DEBUG_MODE = LatinImeLogger.sDBG;

    public interface UIProxy {
        public void invalidateKey(Key key);
        public void showPreview(int keyIndex, PointerTracker tracker);
        public boolean hasDistinctMultitouch();
        public boolean isAccessibilityEnabled();
    }

    public final int mPointerId;

    // Timing constants
    private final int mDelayBeforeKeyRepeatStart;
    private final int mLongPressKeyTimeout;
    private final int mLongPressShiftKeyTimeout;

    // Miscellaneous constants
    private static final int NOT_A_KEY = KeyDetector.NOT_A_KEY;

    private final UIProxy mProxy;
    private final UIHandler mHandler;
    private final KeyDetector mKeyDetector;
    private KeyboardActionListener mListener = EMPTY_LISTENER;
    private final KeyboardSwitcher mKeyboardSwitcher;
    private final boolean mHasDistinctMultitouch;
    private final boolean mConfigSlidingKeyInputEnabled;

    private final int mTouchNoiseThresholdMillis;
    private final int mTouchNoiseThresholdDistanceSquared;

    private Keyboard mKeyboard;
    private List<Key> mKeys;
    private int mKeyHysteresisDistanceSquared = -1;
    private int mKeyQuarterWidthSquared;

    private final PointerTrackerKeyState mKeyState;

    // true if accessibility is enabled in the parent keyboard
    private boolean mIsAccessibilityEnabled;

    // true if keyboard layout has been changed.
    private boolean mKeyboardLayoutHasBeenChanged;

    // true if event is already translated to a key action (long press or mini-keyboard)
    private boolean mKeyAlreadyProcessed;

    // true if this pointer is repeatable key
    private boolean mIsRepeatableKey;

    // true if this pointer is in sliding key input
    private boolean mIsInSlidingKeyInput;

    // true if sliding key is allowed.
    private boolean mIsAllowedSlidingKeyInput;

    // ignore modifier key if true
    private boolean mIgnoreModifierKey;

    // Empty {@link KeyboardActionListener}
    private static final KeyboardActionListener EMPTY_LISTENER = new KeyboardActionListener() {
        @Override
        public void onPress(int primaryCode, boolean withSliding) {}
        @Override
        public void onRelease(int primaryCode, boolean withSliding) {}
        @Override
        public void onCodeInput(int primaryCode, int[] keyCodes, int x, int y) {}
        @Override
        public void onTextInput(CharSequence text) {}
        @Override
        public void onCancelInput() {}
        @Override
        public void onSwipeDown() {}
    };

    public PointerTracker(int id, UIHandler handler, KeyDetector keyDetector,
            UIProxy proxy, Resources res) {
        if (proxy == null || handler == null || keyDetector == null)
            throw new NullPointerException();
        mPointerId = id;
        mProxy = proxy;
        mHandler = handler;
        mKeyDetector = keyDetector;
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mKeyState = new PointerTrackerKeyState(keyDetector);
        mIsAccessibilityEnabled = proxy.isAccessibilityEnabled();
        mHasDistinctMultitouch = proxy.hasDistinctMultitouch();
        mConfigSlidingKeyInputEnabled = res.getBoolean(R.bool.config_sliding_key_input_enabled);
        mDelayBeforeKeyRepeatStart = res.getInteger(R.integer.config_delay_before_key_repeat_start);
        mLongPressKeyTimeout = res.getInteger(R.integer.config_long_press_key_timeout);
        mLongPressShiftKeyTimeout = res.getInteger(R.integer.config_long_press_shift_key_timeout);
        mTouchNoiseThresholdMillis = res.getInteger(R.integer.config_touch_noise_threshold_millis);
        final float touchNoiseThresholdDistance = res.getDimension(
                R.dimen.config_touch_noise_threshold_distance);
        mTouchNoiseThresholdDistanceSquared = (int)(
                touchNoiseThresholdDistance * touchNoiseThresholdDistance);
    }

    public void setOnKeyboardActionListener(KeyboardActionListener listener) {
        mListener = listener;
    }

    public void setAccessibilityEnabled(boolean accessibilityEnabled) {
        mIsAccessibilityEnabled = accessibilityEnabled;
    }

    // Returns true if keyboard has been changed by this callback.
    private boolean callListenerOnPressAndCheckKeyboardLayoutChange(Key key, boolean withSliding) {
        final boolean ignoreModifierKey = mIgnoreModifierKey && isModifierCode(key.mCode);
        if (DEBUG_LISTENER)
            Log.d(TAG, "onPress    : " + keyCodePrintable(key.mCode) + " sliding=" + withSliding
                    + " ignoreModifier=" + ignoreModifierKey);
        if (ignoreModifierKey)
            return false;
        if (key.mEnabled) {
            mListener.onPress(key.mCode, withSliding);
            final boolean keyboardLayoutHasBeenChanged = mKeyboardLayoutHasBeenChanged;
            mKeyboardLayoutHasBeenChanged = false;
            return keyboardLayoutHasBeenChanged;
        }
        return false;
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnCodeInput(Key key, int primaryCode, int[] keyCodes, int x, int y) {
        final boolean ignoreModifierKey = mIgnoreModifierKey && isModifierCode(key.mCode);
        if (DEBUG_LISTENER)
            Log.d(TAG, "onCodeInput: " + keyCodePrintable(primaryCode)
                    + " codes="+ Arrays.toString(keyCodes) + " x=" + x + " y=" + y
                    + " ignoreModifier=" + ignoreModifierKey);
        if (ignoreModifierKey)
            return;
        if (key.mEnabled)
            mListener.onCodeInput(primaryCode, keyCodes, x, y);
    }

    private void callListenerOnTextInput(Key key) {
        if (DEBUG_LISTENER)
            Log.d(TAG, "onTextInput: text=" + key.mOutputText);
        if (key.mEnabled)
            mListener.onTextInput(key.mOutputText);
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnRelease(Key key, int primaryCode, boolean withSliding) {
        final boolean ignoreModifierKey = mIgnoreModifierKey && isModifierCode(key.mCode);
        if (DEBUG_LISTENER)
            Log.d(TAG, "onRelease  : " + keyCodePrintable(primaryCode) + " sliding="
                    + withSliding + " ignoreModifier=" + ignoreModifierKey);
        if (ignoreModifierKey)
            return;
        if (key.mEnabled)
            mListener.onRelease(primaryCode, withSliding);
    }

    private void callListenerOnCancelInput() {
        if (DEBUG_LISTENER)
            Log.d(TAG, "onCancelInput");
        mListener.onCancelInput();
    }

    public void setKeyboard(Keyboard keyboard, float keyHysteresisDistance) {
        if (keyboard == null || keyHysteresisDistance < 0)
            throw new IllegalArgumentException();
        mKeyboard = keyboard;
        mKeys = keyboard.getKeys();
        mKeyHysteresisDistanceSquared = (int)(keyHysteresisDistance * keyHysteresisDistance);
        final int keyQuarterWidth = keyboard.getKeyWidth() / 4;
        mKeyQuarterWidthSquared = keyQuarterWidth * keyQuarterWidth;
        // Mark that keyboard layout has been changed.
        mKeyboardLayoutHasBeenChanged = true;
    }

    public boolean isInSlidingKeyInput() {
        return mIsInSlidingKeyInput;
    }

    private boolean isValidKeyIndex(int keyIndex) {
        return keyIndex >= 0 && keyIndex < mKeys.size();
    }

    public Key getKey(int keyIndex) {
        return isValidKeyIndex(keyIndex) ? mKeys.get(keyIndex) : null;
    }

    private static boolean isModifierCode(int primaryCode) {
        return primaryCode == Keyboard.CODE_SHIFT
                || primaryCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL;
    }

    private boolean isModifierInternal(int keyIndex) {
        final Key key = getKey(keyIndex);
        return key == null ? false : isModifierCode(key.mCode);
    }

    public boolean isModifier() {
        return isModifierInternal(mKeyState.getKeyIndex());
    }

    private boolean isOnModifierKey(int x, int y) {
        return isModifierInternal(mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null));
    }

    public boolean isOnShiftKey(int x, int y) {
        final Key key = getKey(mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null));
        return key != null && key.mCode == Keyboard.CODE_SHIFT;
    }

    public boolean isSpaceKey(int keyIndex) {
        Key key = getKey(keyIndex);
        return key != null && key.mCode == Keyboard.CODE_SPACE;
    }

    public void setReleasedKeyGraphics() {
        setReleasedKeyGraphics(mKeyState.getKeyIndex());
    }

    private void setReleasedKeyGraphics(int keyIndex) {
        final Key key = getKey(keyIndex);
        if (key != null) {
            key.onReleased();
            mProxy.invalidateKey(key);
        }
    }

    private void setPressedKeyGraphics(int keyIndex) {
        final Key key = getKey(keyIndex);
        if (key != null && key.mEnabled) {
            key.onPressed();
            mProxy.invalidateKey(key);
        }
    }

    private void checkAssertion(PointerTrackerQueue queue) {
        if (mHasDistinctMultitouch && queue == null)
            throw new RuntimeException(
                    "PointerTrackerQueue must be passed on distinct multi touch device");
        if (!mHasDistinctMultitouch && queue != null)
            throw new RuntimeException(
                    "PointerTrackerQueue must be null on non-distinct multi touch device");
    }

    public void onTouchEvent(int action, int x, int y, long eventTime, PointerTrackerQueue queue) {
        switch (action) {
        case MotionEvent.ACTION_MOVE:
            onMoveEvent(x, y, eventTime, queue);
            break;
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            onDownEvent(x, y, eventTime, queue);
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            onUpEvent(x, y, eventTime, queue);
            break;
        case MotionEvent.ACTION_CANCEL:
            onCancelEvent(x, y, eventTime, queue);
            break;
        }
    }

    public void onDownEvent(int x, int y, long eventTime, PointerTrackerQueue queue) {
        if (ENABLE_ASSERTION) checkAssertion(queue);
        if (DEBUG_EVENT)
            printTouchEvent("onDownEvent:", x, y, eventTime);

        // Naive up-to-down noise filter.
        final long deltaT = eventTime - mKeyState.getUpTime();
        if (deltaT < mTouchNoiseThresholdMillis) {
            final int dx = x - mKeyState.getLastX();
            final int dy = y - mKeyState.getLastY();
            final int distanceSquared = (dx * dx + dy * dy);
            if (distanceSquared < mTouchNoiseThresholdDistanceSquared) {
                if (DEBUG_MODE)
                    Log.w(TAG, "onDownEvent: ignore potential noise: time=" + deltaT
                            + " distance=" + distanceSquared);
                mKeyAlreadyProcessed = true;
                return;
            }
        }

        if (queue != null) {
            if (isOnModifierKey(x, y)) {
                // Before processing a down event of modifier key, all pointers already being
                // tracked should be released.
                queue.releaseAllPointers(eventTime);
            }
            queue.add(this);
        }
        onDownEventInternal(x, y, eventTime);
    }

    private void onDownEventInternal(int x, int y, long eventTime) {
        int keyIndex = mKeyState.onDownKey(x, y, eventTime);
        // Sliding key is allowed when 1) enabled by configuration, 2) this pointer starts sliding
        // from modifier key, 3) this pointer is on mini-keyboard, or 4) accessibility is enabled.
        mIsAllowedSlidingKeyInput = mConfigSlidingKeyInputEnabled || isModifierInternal(keyIndex)
                || mKeyDetector instanceof MiniKeyboardKeyDetector
                || mIsAccessibilityEnabled;
        mKeyboardLayoutHasBeenChanged = false;
        mKeyAlreadyProcessed = false;
        mIsRepeatableKey = false;
        mIsInSlidingKeyInput = false;
        mIgnoreModifierKey = false;
        if (isValidKeyIndex(keyIndex)) {
            // This onPress call may have changed keyboard layout. Those cases are detected at
            // {@link #setKeyboard}. In those cases, we should update keyIndex according to the new
            // keyboard layout.
            if (callListenerOnPressAndCheckKeyboardLayoutChange(getKey(keyIndex), false))
                keyIndex = mKeyState.onDownKey(x, y, eventTime);

            // Accessibility disables key repeat because users may need to pause on a key to hear
            // its spoken description.
            final Key key = getKey(keyIndex);
            if (key != null && key.mRepeatable && !mIsAccessibilityEnabled) {
                repeatKey(keyIndex);
                mHandler.startKeyRepeatTimer(mDelayBeforeKeyRepeatStart, keyIndex, this);
                mIsRepeatableKey = true;
            }
            startLongPressTimer(keyIndex);
            showKeyPreview(keyIndex);
            setPressedKeyGraphics(keyIndex);
        }
    }

    private void startSlidingKeyInput(Key key) {
        if (!mIsInSlidingKeyInput)
            mIgnoreModifierKey = isModifierCode(key.mCode);
        mIsInSlidingKeyInput = true;
    }

    public void onMoveEvent(int x, int y, long eventTime, PointerTrackerQueue queue) {
        if (ENABLE_ASSERTION) checkAssertion(queue);
        if (DEBUG_MOVE_EVENT)
            printTouchEvent("onMoveEvent:", x, y, eventTime);
        if (mKeyAlreadyProcessed)
            return;
        final PointerTrackerKeyState keyState = mKeyState;

        final int lastX = keyState.getLastX();
        final int lastY = keyState.getLastY();
        final int oldKeyIndex = keyState.getKeyIndex();
        final Key oldKey = getKey(oldKeyIndex);
        int keyIndex = keyState.onMoveKey(x, y);
        if (isValidKeyIndex(keyIndex)) {
            if (oldKey == null) {
                // The pointer has been slid in to the new key, but the finger was not on any keys.
                // In this case, we must call onPress() to notify that the new key is being pressed.
                // This onPress call may have changed keyboard layout. Those cases are detected at
                // {@link #setKeyboard}. In those cases, we should update keyIndex according to the
                // new keyboard layout.
                if (callListenerOnPressAndCheckKeyboardLayoutChange(getKey(keyIndex), true))
                    keyIndex = keyState.onMoveKey(x, y);
                keyState.onMoveToNewKey(keyIndex, x, y);
                startLongPressTimer(keyIndex);
                showKeyPreview(keyIndex);
                setPressedKeyGraphics(keyIndex);
            } else if (!isMinorMoveBounce(x, y, keyIndex)) {
                // The pointer has been slid in to the new key from the previous key, we must call
                // onRelease() first to notify that the previous key has been released, then call
                // onPress() to notify that the new key is being pressed.
                setReleasedKeyGraphics(oldKeyIndex);
                callListenerOnRelease(oldKey, oldKey.mCode, true);
                startSlidingKeyInput(oldKey);
                mHandler.cancelLongPressTimers();
                if (mIsAllowedSlidingKeyInput) {
                    // This onPress call may have changed keyboard layout. Those cases are detected
                    // at {@link #setKeyboard}. In those cases, we should update keyIndex according
                    // to the new keyboard layout.
                    if (callListenerOnPressAndCheckKeyboardLayoutChange(getKey(keyIndex), true))
                        keyIndex = keyState.onMoveKey(x, y);
                    keyState.onMoveToNewKey(keyIndex, x, y);
                    startLongPressTimer(keyIndex);
                    setPressedKeyGraphics(keyIndex);
                    showKeyPreview(keyIndex);
                } else {
                    // HACK: On some devices, quick successive touches may be translated to sudden
                    // move by touch panel firmware. This hack detects the case and translates the
                    // move event to successive up and down events.
                    final int dx = x - lastX;
                    final int dy = y - lastY;
                    final int lastMoveSquared = dx * dx + dy * dy;
                    if (lastMoveSquared >= mKeyQuarterWidthSquared) {
                        if (DEBUG_MODE)
                            Log.w(TAG, String.format("onMoveEvent: sudden move is translated to "
                                    + "up[%d,%d]/down[%d,%d] events", lastX, lastY, x, y));
                        onUpEventInternal(lastX, lastY, eventTime);
                        onDownEventInternal(x, y, eventTime);
                    } else {
                        mKeyAlreadyProcessed = true;
                        showKeyPreview(NOT_A_KEY);
                        setReleasedKeyGraphics(oldKeyIndex);
                    }
                    return;
                }
            }
        } else {
            if (oldKey != null && !isMinorMoveBounce(x, y, keyIndex)) {
                // The pointer has been slid out from the previous key, we must call onRelease() to
                // notify that the previous key has been released.
                setReleasedKeyGraphics(oldKeyIndex);
                callListenerOnRelease(oldKey, oldKey.mCode, true);
                startSlidingKeyInput(oldKey);
                mHandler.cancelLongPressTimers();
                if (mIsAllowedSlidingKeyInput) {
                    keyState.onMoveToNewKey(keyIndex, x, y);
                } else {
                    mKeyAlreadyProcessed = true;
                    showKeyPreview(NOT_A_KEY);
                    return;
                }
            }
        }
    }

    public void onUpEvent(int x, int y, long eventTime, PointerTrackerQueue queue) {
        if (ENABLE_ASSERTION) checkAssertion(queue);
        if (DEBUG_EVENT)
            printTouchEvent("onUpEvent  :", x, y, eventTime);

        if (queue != null) {
            if (isModifier()) {
                // Before processing an up event of modifier key, all pointers already being
                // tracked should be released.
                queue.releaseAllPointersExcept(this, eventTime);
            } else {
                queue.releaseAllPointersOlderThan(this, eventTime);
            }
            queue.remove(this);
        }
        onUpEventInternal(x, y, eventTime);
    }

    public void onUpEventForRelease(int x, int y, long eventTime) {
        onUpEventInternal(x, y, eventTime);
        mKeyAlreadyProcessed = true;
    }

    private void onUpEventInternal(int x, int y, long eventTime) {
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        mIsInSlidingKeyInput = false;
        final PointerTrackerKeyState keyState = mKeyState;
        final int keyX, keyY;
        if (!isMinorMoveBounce(x, y, keyState.onMoveKey(x, y))) {
            keyX = x;
            keyY = y;
        } else {
            // Use previous fixed key coordinates.
            keyX = keyState.getKeyX();
            keyY = keyState.getKeyY();
        }
        final int keyIndex = keyState.onUpKey(keyX, keyY, eventTime);
        showKeyPreview(NOT_A_KEY);
        setReleasedKeyGraphics(keyIndex);
        if (mKeyAlreadyProcessed)
            return;
        if (!mIsRepeatableKey) {
            detectAndSendKey(keyIndex, keyX, keyY);
        }
    }

    public void onLongPressed(PointerTrackerQueue queue) {
        mKeyAlreadyProcessed = true;
        queue.remove(this);
    }

    public void onCancelEvent(int x, int y, long eventTime, PointerTrackerQueue queue) {
        if (ENABLE_ASSERTION) checkAssertion(queue);
        if (DEBUG_EVENT)
            printTouchEvent("onCancelEvt:", x, y, eventTime);

        if (queue != null)
            queue.remove(this);
        onCancelEventInternal();
    }

    private void onCancelEventInternal() {
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        showKeyPreview(NOT_A_KEY);
        setReleasedKeyGraphics(mKeyState.getKeyIndex());
        mIsInSlidingKeyInput = false;
    }

    public void repeatKey(int keyIndex) {
        Key key = getKey(keyIndex);
        if (key != null) {
            detectAndSendKey(keyIndex, key.mX, key.mY);
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

    private boolean isMinorMoveBounce(int x, int y, int newKey) {
        if (mKeys == null || mKeyHysteresisDistanceSquared < 0)
            throw new IllegalStateException("keyboard and/or hysteresis not set");
        int curKey = mKeyState.getKeyIndex();
        if (newKey == curKey) {
            return true;
        } else if (isValidKeyIndex(curKey)) {
            return mKeys.get(curKey).squaredDistanceToEdge(x, y) < mKeyHysteresisDistanceSquared;
        } else {
            return false;
        }
    }

    private void showKeyPreview(int keyIndex) {
        final Key key = getKey(keyIndex);
        if (key != null && !key.mEnabled)
            return;
        // The modifier key, such as shift key, should not be shown as preview when multi-touch is
        // supported. On the other hand, if multi-touch is not supported, the modifier key should
        // be shown as preview. If accessibility is turned on, the modifier key should be shown as
        // preview.
        if (mHasDistinctMultitouch && isModifier() && !mIsAccessibilityEnabled) {
            mProxy.showPreview(NOT_A_KEY, this);
        } else {
            mProxy.showPreview(keyIndex, this);
        }
    }

    private void startLongPressTimer(int keyIndex) {
        // Accessibility disables long press because users are likely to need to pause on a key
        // for an unspecified duration in order to hear the key's spoken description.
        if (mIsAccessibilityEnabled) {
            return;
        }
        Key key = getKey(keyIndex);
        if (!key.mEnabled)
            return;
        if (key.mCode == Keyboard.CODE_SHIFT) {
            mHandler.startLongPressShiftTimer(mLongPressShiftKeyTimeout, keyIndex, this);
        } else if (key.mManualTemporaryUpperCaseCode != Keyboard.CODE_DUMMY
                && mKeyboard.isManualTemporaryUpperCase()) {
            // We need not start long press timer on the key which has manual temporary upper case
            // code defined and the keyboard is in manual temporary upper case mode.
            return;
        } else if (mKeyboardSwitcher.isInMomentaryAutoModeSwitchState()) {
            // We use longer timeout for sliding finger input started from the symbols mode key.
            mHandler.startLongPressTimer(mLongPressKeyTimeout * 3, keyIndex, this);
        } else {
            mHandler.startLongPressTimer(mLongPressKeyTimeout, keyIndex, this);
        }
    }

    private void detectAndSendKey(int index, int x, int y) {
        final Key key = getKey(index);
        if (key == null) {
            callListenerOnCancelInput();
            return;
        }
        if (key.mOutputText != null) {
            callListenerOnTextInput(key);
            callListenerOnRelease(key, key.mCode, false);
        } else {
            int code = key.mCode;
            final int[] codes = mKeyDetector.newCodeArray();
            mKeyDetector.getKeyIndexAndNearbyCodes(x, y, codes);

            // If keyboard is in manual temporary upper case state and key has manual temporary
            // shift code, alternate character code should be sent.
            if (mKeyboard.isManualTemporaryUpperCase()
                    && key.mManualTemporaryUpperCaseCode != Keyboard.CODE_DUMMY) {
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
            callListenerOnCodeInput(key, code, codes, x, y);
            callListenerOnRelease(key, code, false);
        }
    }

    public CharSequence getPreviewText(Key key) {
        return key.mLabel;
    }

    private long mPreviousEventTime;

    private void printTouchEvent(String title, int x, int y, long eventTime) {
        final int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null);
        final Key key = getKey(keyIndex);
        final String code = (key == null) ? "----" : keyCodePrintable(key.mCode);
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
