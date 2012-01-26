/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import com.android.inputmethod.keyboard.internal.PointerTrackerQueue;
import com.android.inputmethod.latin.LatinImeLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PointerTracker {
    private static final String TAG = PointerTracker.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_MOVE_EVENT = false;
    private static final boolean DEBUG_LISTENER = false;
    private static boolean DEBUG_MODE = LatinImeLogger.sDBG;

    public interface KeyEventHandler {
        /**
         * Get KeyDetector object that is used for this PointerTracker.
         * @return the KeyDetector object that is used for this PointerTracker
         */
        public KeyDetector getKeyDetector();

        /**
         * Get KeyboardActionListener object that is used to register key code and so on.
         * @return the KeyboardActionListner for this PointerTracker
         */
        public KeyboardActionListener getKeyboardActionListener();

        /**
         * Get DrawingProxy object that is used for this PointerTracker.
         * @return the DrawingProxy object that is used for this PointerTracker
         */
        public DrawingProxy getDrawingProxy();

        /**
         * Get TimerProxy object that handles key repeat and long press timer event for this
         * PointerTracker.
         * @return the TimerProxy object that handles key repeat and long press timer event.
         */
        public TimerProxy getTimerProxy();
    }

    public interface DrawingProxy extends MoreKeysPanel.Controller {
        public void invalidateKey(Key key);
        public TextView inflateKeyPreviewText();
        public void showKeyPreview(PointerTracker tracker);
        public void dismissKeyPreview(PointerTracker tracker);
    }

    public interface TimerProxy {
        public void startKeyTypedTimer(long delay);
        public boolean isTyping();
        public void startKeyRepeatTimer(long delay, PointerTracker tracker);
        public void startLongPressTimer(long delay, PointerTracker tracker);
        public void cancelLongPressTimer();
        public void cancelKeyTimers();

        public static class Adapter implements TimerProxy {
            @Override
            public void startKeyTypedTimer(long delay) {}
            @Override
            public boolean isTyping() { return false; }
            @Override
            public void startKeyRepeatTimer(long delay, PointerTracker tracker) {}
            @Override
            public void startLongPressTimer(long delay, PointerTracker tracker) {}
            @Override
            public void cancelLongPressTimer() {}
            @Override
            public void cancelKeyTimers() {}
        }
    }

    private static KeyboardSwitcher sKeyboardSwitcher;
    // Parameters for pointer handling.
    private static LatinKeyboardView.PointerTrackerParams sParams;
    private static int sTouchNoiseThresholdDistanceSquared;

    private static final List<PointerTracker> sTrackers = new ArrayList<PointerTracker>();
    private static PointerTrackerQueue sPointerTrackerQueue;

    public final int mPointerId;

    private DrawingProxy mDrawingProxy;
    private TimerProxy mTimerProxy;
    private KeyDetector mKeyDetector;
    private KeyboardActionListener mListener = EMPTY_LISTENER;

    private Keyboard mKeyboard;
    private Set<Key> mKeys;
    private int mKeyQuarterWidthSquared;
    private final TextView mKeyPreviewText;

    // The position and time at which first down event occurred.
    private long mDownTime;
    private long mUpTime;

    // The current key where this pointer is.
    private Key mCurrentKey = null;
    // The position where the current key was recognized for the first time.
    private int mKeyX;
    private int mKeyY;

    // Last pointer position.
    private int mLastX;
    private int mLastY;

    // true if keyboard layout has been changed.
    private boolean mKeyboardLayoutHasBeenChanged;

    // true if event is already translated to a key action.
    private boolean mKeyAlreadyProcessed;

    // true if this pointer has been long-pressed and is showing a more keys panel.
    private boolean mIsShowingMoreKeysPanel;

    // true if this pointer is repeatable key
    private boolean mIsRepeatableKey;

    // true if this pointer is in sliding key input
    boolean mIsInSlidingKeyInput;

    // true if sliding key is allowed.
    private boolean mIsAllowedSlidingKeyInput;

    // ignore modifier key if true
    private boolean mIgnoreModifierKey;

    // Empty {@link KeyboardActionListener}
    private static final KeyboardActionListener EMPTY_LISTENER =
            new KeyboardActionListener.Adapter();

    public static void init(boolean hasDistinctMultitouch, Context context) {
        if (hasDistinctMultitouch) {
            sPointerTrackerQueue = new PointerTrackerQueue();
        } else {
            sPointerTrackerQueue = null;
        }

        setParameters(LatinKeyboardView.PointerTrackerParams.DEFAULT);
        sKeyboardSwitcher = KeyboardSwitcher.getInstance();
    }

    public static void setParameters(LatinKeyboardView.PointerTrackerParams params) {
        sParams = params;
        sTouchNoiseThresholdDistanceSquared = (int)(
                params.mTouchNoiseThresholdDistance * params.mTouchNoiseThresholdDistance);
    }

    public static PointerTracker getPointerTracker(final int id, KeyEventHandler handler) {
        final List<PointerTracker> trackers = sTrackers;

        // Create pointer trackers until we can get 'id+1'-th tracker, if needed.
        for (int i = trackers.size(); i <= id; i++) {
            final PointerTracker tracker = new PointerTracker(i, handler);
            trackers.add(tracker);
        }

        return trackers.get(id);
    }

    public static boolean isAnyInSlidingKeyInput() {
        return sPointerTrackerQueue != null ? sPointerTrackerQueue.isAnyInSlidingKeyInput() : false;
    }

    public static void setKeyboardActionListener(KeyboardActionListener listener) {
        for (final PointerTracker tracker : sTrackers) {
            tracker.mListener = listener;
        }
    }

    public static void setKeyDetector(KeyDetector keyDetector) {
        for (final PointerTracker tracker : sTrackers) {
            tracker.setKeyDetectorInner(keyDetector);
            // Mark that keyboard layout has been changed.
            tracker.mKeyboardLayoutHasBeenChanged = true;
        }
    }

    public static void dismissAllKeyPreviews() {
        for (final PointerTracker tracker : sTrackers) {
            tracker.setReleasedKeyGraphics(tracker.mCurrentKey);
        }
    }

    public PointerTracker(int id, KeyEventHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        mPointerId = id;
        setKeyDetectorInner(handler.getKeyDetector());
        mListener = handler.getKeyboardActionListener();
        mDrawingProxy = handler.getDrawingProxy();
        mTimerProxy = handler.getTimerProxy();
        mKeyPreviewText = mDrawingProxy.inflateKeyPreviewText();
    }

    public TextView getKeyPreviewText() {
        return mKeyPreviewText;
    }

    // Returns true if keyboard has been changed by this callback.
    private boolean callListenerOnPressAndCheckKeyboardLayoutChange(Key key) {
        final boolean ignoreModifierKey = mIgnoreModifierKey && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, "onPress    : " + KeyDetector.printableCode(key)
                    + " ignoreModifier=" + ignoreModifierKey
                    + " enabled=" + key.isEnabled());
        }
        if (ignoreModifierKey) {
            return false;
        }
        if (key.isEnabled()) {
            mListener.onPressKey(key.mCode);
            final boolean keyboardLayoutHasBeenChanged = mKeyboardLayoutHasBeenChanged;
            mKeyboardLayoutHasBeenChanged = false;
            return keyboardLayoutHasBeenChanged;
        }
        return false;
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnCodeInput(Key key, int primaryCode, int[] keyCodes, int x, int y) {
        final boolean ignoreModifierKey = mIgnoreModifierKey && key.isModifier();
        final boolean alterCode = key.altCodeWhileTyping() && mTimerProxy.isTyping();
        final int code = alterCode ? key.mAltCode : primaryCode;
        if (DEBUG_LISTENER) {
            Log.d(TAG, "onCodeInput: " + Keyboard.printableCode(code) + " text=" + key.mOutputText
                    + " codes="+ KeyDetector.printableCodes(keyCodes) + " x=" + x + " y=" + y
                    + " ignoreModifier=" + ignoreModifierKey + " alterCode=" + alterCode
                    + " enabled=" + key.isEnabled());
        }
        if (ignoreModifierKey) {
            return;
        }
        if (key.isEnabled()) {
            if (code == Keyboard.CODE_OUTPUT_TEXT) {
                mListener.onTextInput(key.mOutputText);
            } else if (code != Keyboard.CODE_UNSPECIFIED) {
                mListener.onCodeInput(code, keyCodes, x, y);
            }
            if (!key.altCodeWhileTyping() && !key.isModifier()) {
                mTimerProxy.startKeyTypedTimer(sParams.mIgnoreSpecialKeyTimeout);
            }
        }
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnRelease(Key key, int primaryCode, boolean withSliding) {
        final boolean ignoreModifierKey = mIgnoreModifierKey && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, "onRelease  : " + Keyboard.printableCode(primaryCode)
                    + " sliding=" + withSliding + " ignoreModifier=" + ignoreModifierKey
                    + " enabled="+ key.isEnabled());
        }
        if (ignoreModifierKey) {
            return;
        }
        if (key.isEnabled()) {
            mListener.onReleaseKey(primaryCode, withSliding);
        }
    }

    private void callListenerOnCancelInput() {
        if (DEBUG_LISTENER)
            Log.d(TAG, "onCancelInput");
        mListener.onCancelInput();
    }

    private void setKeyDetectorInner(KeyDetector keyDetector) {
        mKeyDetector = keyDetector;
        mKeyboard = keyDetector.getKeyboard();
        mKeys = mKeyboard.mKeys;
        final int keyQuarterWidth = mKeyboard.mMostCommonKeyWidth / 4;
        mKeyQuarterWidthSquared = keyQuarterWidth * keyQuarterWidth;
    }

    public boolean isInSlidingKeyInput() {
        return mIsInSlidingKeyInput;
    }

    public Key getKey() {
        return mCurrentKey;
    }

    public boolean isModifier() {
        return mCurrentKey != null && mCurrentKey.isModifier();
    }

    public Key getKeyOn(int x, int y) {
        return mKeyDetector.getKeyAndNearbyCodes(x, y, null);
    }

    private void setReleasedKeyGraphics(Key key) {
        mDrawingProxy.dismissKeyPreview(this);
        if (key != null && key.isEnabled()) {
            key.onReleased();
            mDrawingProxy.invalidateKey(key);

            if (key.isShift()) {
                for (final Key shiftKey : mKeyboard.mShiftKeys) {
                    if (shiftKey != key) {
                        shiftKey.onReleased();
                        mDrawingProxy.invalidateKey(shiftKey);
                    }
                }
            }

            if (key.altCodeWhileTyping()) {
                final Key altKey = mKeyboard.getKey(key.mAltCode);
                if (altKey != null) {
                    altKey.onReleased();
                    mDrawingProxy.invalidateKey(altKey);
                }
            }
        }
    }

    private void setPressedKeyGraphics(Key key) {
        if (key != null && key.isEnabled()) {
            if (!key.noKeyPreview()) {
                mDrawingProxy.showKeyPreview(this);
            }
            key.onPressed();
            mDrawingProxy.invalidateKey(key);

            if (key.isShift()) {
                for (final Key shiftKey : mKeyboard.mShiftKeys) {
                    if (shiftKey != key) {
                        shiftKey.onPressed();
                        mDrawingProxy.invalidateKey(shiftKey);
                    }
                }
            }

            if (key.altCodeWhileTyping() && mTimerProxy.isTyping()) {
                final Key altKey = mKeyboard.getKey(key.mAltCode);
                if (altKey != null) {
                    // TODO: Show altKey's preview.
                    altKey.onPressed();
                    mDrawingProxy.invalidateKey(altKey);
                }
            }
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

    private Key onDownKey(int x, int y, long eventTime) {
        mDownTime = eventTime;
        return onMoveToNewKey(onMoveKeyInternal(x, y), x, y);
    }

    private Key onMoveKeyInternal(int x, int y) {
        mLastX = x;
        mLastY = y;
        return mKeyDetector.getKeyAndNearbyCodes(x, y, null);
    }

    private Key onMoveKey(int x, int y) {
        return onMoveKeyInternal(x, y);
    }

    private Key onMoveToNewKey(Key newKey, int x, int y) {
        mCurrentKey = newKey;
        mKeyX = x;
        mKeyY = y;
        return newKey;
    }

    private Key onUpKey(int x, int y, long eventTime) {
        mUpTime = eventTime;
        mCurrentKey = null;
        return onMoveKeyInternal(x, y);
    }

    public void processMotionEvent(int action, int x, int y, long eventTime,
            KeyEventHandler handler) {
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            onDownEvent(x, y, eventTime, handler);
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            onUpEvent(x, y, eventTime);
            break;
        case MotionEvent.ACTION_MOVE:
            onMoveEvent(x, y, eventTime);
            break;
        case MotionEvent.ACTION_CANCEL:
            onCancelEvent(x, y, eventTime);
            break;
        }
    }

    public void onDownEvent(int x, int y, long eventTime, KeyEventHandler handler) {
        if (DEBUG_EVENT)
            printTouchEvent("onDownEvent:", x, y, eventTime);

        mDrawingProxy = handler.getDrawingProxy();
        mTimerProxy = handler.getTimerProxy();
        setKeyboardActionListener(handler.getKeyboardActionListener());
        setKeyDetectorInner(handler.getKeyDetector());
        // Naive up-to-down noise filter.
        final long deltaT = eventTime - mUpTime;
        if (deltaT < sParams.mTouchNoiseThresholdTime) {
            final int dx = x - mLastX;
            final int dy = y - mLastY;
            final int distanceSquared = (dx * dx + dy * dy);
            if (distanceSquared < sTouchNoiseThresholdDistanceSquared) {
                if (DEBUG_MODE)
                    Log.w(TAG, "onDownEvent: ignore potential noise: time=" + deltaT
                            + " distance=" + distanceSquared);
                mKeyAlreadyProcessed = true;
                return;
            }
        }

        final PointerTrackerQueue queue = sPointerTrackerQueue;
        if (queue != null) {
            final Key key = getKeyOn(x, y);
            if (key != null && key.isModifier()) {
                // Before processing a down event of modifier key, all pointers already being
                // tracked should be released.
                queue.releaseAllPointers(eventTime);
            }
            queue.add(this);
        }
        onDownEventInternal(x, y, eventTime);
    }

    private void onDownEventInternal(int x, int y, long eventTime) {
        Key key = onDownKey(x, y, eventTime);
        // Sliding key is allowed when 1) enabled by configuration, 2) this pointer starts sliding
        // from modifier key, or 3) this pointer's KeyDetector always allows sliding input.
        mIsAllowedSlidingKeyInput = sParams.mSlidingKeyInputEnabled
                || (key != null && key.isModifier())
                || mKeyDetector.alwaysAllowsSlidingInput();
        mKeyboardLayoutHasBeenChanged = false;
        mKeyAlreadyProcessed = false;
        mIsRepeatableKey = false;
        mIsInSlidingKeyInput = false;
        mIgnoreModifierKey = false;
        if (key != null) {
            // This onPress call may have changed keyboard layout. Those cases are detected at
            // {@link #setKeyboard}. In those cases, we should update key according to the new
            // keyboard layout.
            if (callListenerOnPressAndCheckKeyboardLayoutChange(key)) {
                key = onDownKey(x, y, eventTime);
            }

            startRepeatKey(key);
            startLongPressTimer(key);
            setPressedKeyGraphics(key);
        }
    }

    private void startSlidingKeyInput(Key key) {
        if (!mIsInSlidingKeyInput) {
            mIgnoreModifierKey = key.isModifier();
        }
        mIsInSlidingKeyInput = true;
    }

    public void onMoveEvent(int x, int y, long eventTime) {
        if (DEBUG_MOVE_EVENT)
            printTouchEvent("onMoveEvent:", x, y, eventTime);
        if (mKeyAlreadyProcessed)
            return;

        final int lastX = mLastX;
        final int lastY = mLastY;
        final Key oldKey = mCurrentKey;
        Key key = onMoveKey(x, y);
        if (key != null) {
            if (oldKey == null) {
                // The pointer has been slid in to the new key, but the finger was not on any keys.
                // In this case, we must call onPress() to notify that the new key is being pressed.
                // This onPress call may have changed keyboard layout. Those cases are detected at
                // {@link #setKeyboard}. In those cases, we should update key according to the
                // new keyboard layout.
                if (callListenerOnPressAndCheckKeyboardLayoutChange(key)) {
                    key = onMoveKey(x, y);
                }
                onMoveToNewKey(key, x, y);
                startLongPressTimer(key);
                setPressedKeyGraphics(key);
            } else if (isMajorEnoughMoveToBeOnNewKey(x, y, key)) {
                // The pointer has been slid in to the new key from the previous key, we must call
                // onRelease() first to notify that the previous key has been released, then call
                // onPress() to notify that the new key is being pressed.
                setReleasedKeyGraphics(oldKey);
                callListenerOnRelease(oldKey, oldKey.mCode, true);
                startSlidingKeyInput(oldKey);
                mTimerProxy.cancelKeyTimers();
                startRepeatKey(key);
                if (mIsAllowedSlidingKeyInput) {
                    // This onPress call may have changed keyboard layout. Those cases are detected
                    // at {@link #setKeyboard}. In those cases, we should update key according
                    // to the new keyboard layout.
                    if (callListenerOnPressAndCheckKeyboardLayoutChange(key)) {
                        key = onMoveKey(x, y);
                    }
                    onMoveToNewKey(key, x, y);
                    startLongPressTimer(key);
                    setPressedKeyGraphics(key);
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
                        setReleasedKeyGraphics(oldKey);
                    }
                }
            }
        } else {
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, key)) {
                // The pointer has been slid out from the previous key, we must call onRelease() to
                // notify that the previous key has been released.
                setReleasedKeyGraphics(oldKey);
                callListenerOnRelease(oldKey, oldKey.mCode, true);
                startSlidingKeyInput(oldKey);
                mTimerProxy.cancelLongPressTimer();
                if (mIsAllowedSlidingKeyInput) {
                    onMoveToNewKey(key, x, y);
                } else {
                    mKeyAlreadyProcessed = true;
                }
            }
        }
    }

    public void onUpEvent(int x, int y, long eventTime) {
        if (DEBUG_EVENT)
            printTouchEvent("onUpEvent  :", x, y, eventTime);

        final PointerTrackerQueue queue = sPointerTrackerQueue;
        if (queue != null) {
            if (mCurrentKey != null && mCurrentKey.isModifier()) {
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

    // Let this pointer tracker know that one of newer-than-this pointer trackers got an up event.
    // This pointer tracker needs to keep the key top graphics "pressed", but needs to get a
    // "virtual" up event.
    public void onPhantomUpEvent(int x, int y, long eventTime) {
        if (DEBUG_EVENT)
            printTouchEvent("onPhntEvent:", x, y, eventTime);
        onUpEventInternal(x, y, eventTime);
        mKeyAlreadyProcessed = true;
    }

    private void onUpEventInternal(int x, int y, long eventTime) {
        mTimerProxy.cancelKeyTimers();
        mIsInSlidingKeyInput = false;
        final int keyX, keyY;
        if (isMajorEnoughMoveToBeOnNewKey(x, y, onMoveKey(x, y))) {
            keyX = x;
            keyY = y;
        } else {
            // Use previous fixed key coordinates.
            keyX = mKeyX;
            keyY = mKeyY;
        }
        final Key key = onUpKey(keyX, keyY, eventTime);
        setReleasedKeyGraphics(key);
        if (mIsShowingMoreKeysPanel) {
            mDrawingProxy.dismissMoreKeysPanel();
            mIsShowingMoreKeysPanel = false;
        }
        if (mKeyAlreadyProcessed)
            return;
        if (!mIsRepeatableKey) {
            detectAndSendKey(key, keyX, keyY);
        }
    }

    public void onShowMoreKeysPanel(int x, int y, KeyEventHandler handler) {
        onLongPressed();
        onDownEvent(x, y, SystemClock.uptimeMillis(), handler);
        mIsShowingMoreKeysPanel = true;
    }

    public void onLongPressed() {
        mKeyAlreadyProcessed = true;
        setReleasedKeyGraphics(mCurrentKey);
        final PointerTrackerQueue queue = sPointerTrackerQueue;
        if (queue != null) {
            queue.remove(this);
        }
    }

    public void onCancelEvent(int x, int y, long eventTime) {
        if (DEBUG_EVENT)
            printTouchEvent("onCancelEvt:", x, y, eventTime);

        final PointerTrackerQueue queue = sPointerTrackerQueue;
        if (queue != null) {
            queue.releaseAllPointersExcept(this, eventTime);
            queue.remove(this);
        }
        onCancelEventInternal();
    }

    private void onCancelEventInternal() {
        mTimerProxy.cancelKeyTimers();
        setReleasedKeyGraphics(mCurrentKey);
        mIsInSlidingKeyInput = false;
        if (mIsShowingMoreKeysPanel) {
            mDrawingProxy.dismissMoreKeysPanel();
            mIsShowingMoreKeysPanel = false;
        }
    }

    private void startRepeatKey(Key key) {
        if (key != null && key.isRepeatable()) {
            onRepeatKey(key);
            mTimerProxy.startKeyRepeatTimer(sParams.mKeyRepeatStartTimeout, this);
            mIsRepeatableKey = true;
        } else {
            mIsRepeatableKey = false;
        }
    }

    public void onRepeatKey(Key key) {
        if (key != null) {
            detectAndSendKey(key, key.mX, key.mY);
        }
    }

    private boolean isMajorEnoughMoveToBeOnNewKey(int x, int y, Key newKey) {
        if (mKeys == null || mKeyDetector == null)
            throw new NullPointerException("keyboard and/or key detector not set");
        Key curKey = mCurrentKey;
        if (newKey == curKey) {
            return false;
        } else if (curKey != null) {
            return curKey.squaredDistanceToEdge(x, y)
                    >= mKeyDetector.getKeyHysteresisDistanceSquared();
        } else {
            return true;
        }
    }

    private void startLongPressTimer(Key key) {
        if (key == null) return;
        if (key.mCode == Keyboard.CODE_SHIFT) {
            if (sParams.mLongPressShiftKeyTimeout > 0) {
                mTimerProxy.startLongPressTimer(sParams.mLongPressShiftKeyTimeout, this);
            }
        } else if (key.mCode == Keyboard.CODE_SPACE) {
            if (sParams.mLongPressSpaceKeyTimeout > 0) {
                mTimerProxy.startLongPressTimer(sParams.mLongPressSpaceKeyTimeout, this);
            }
        } else if (key.hasUppercaseLetter() && mKeyboard.isManualTemporaryUpperCase()) {
            // We need not start long press timer on the key which has manual temporary upper case
            // code defined and the keyboard is in manual temporary upper case mode.
            return;
        } else if (sKeyboardSwitcher.isInMomentarySwitchState()) {
            // We use longer timeout for sliding finger input started from the symbols mode key.
            mTimerProxy.startLongPressTimer(sParams.mLongPressKeyTimeout * 3, this);
        } else {
            mTimerProxy.startLongPressTimer(sParams.mLongPressKeyTimeout, this);
        }
    }

    private void detectAndSendKey(Key key, int x, int y) {
        if (key == null) {
            callListenerOnCancelInput();
            return;
        }

        int code = key.mCode;
        final int[] codes = mKeyDetector.newCodeArray();
        mKeyDetector.getKeyAndNearbyCodes(x, y, codes);

        // If keyboard is in manual temporary upper case state and key has manual temporary
        // uppercase letter as key hint letter, alternate character code should be sent.
        if (mKeyboard.isManualTemporaryUpperCase() && key.hasUppercaseLetter()) {
            code = key.mHintLabel.charAt(0);
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

    private long mPreviousEventTime;

    private void printTouchEvent(String title, int x, int y, long eventTime) {
        final Key key = mKeyDetector.getKeyAndNearbyCodes(x, y, null);
        final String code = KeyDetector.printableCode(key);
        final long delta = eventTime - mPreviousEventTime;
        Log.d(TAG, String.format("%s%s[%d] %4d %4d %5d %s", title,
                (mKeyAlreadyProcessed ? "-" : " "), mPointerId, x, y, delta, code));
        mPreviousEventTime = eventTime;
    }
}
