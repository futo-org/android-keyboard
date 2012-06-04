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

import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.android.inputmethod.keyboard.internal.PointerTrackerQueue;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.ResearchLogger;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.util.ArrayList;

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
        public void startTypingStateTimer();
        public boolean isTypingState();
        public void startKeyRepeatTimer(PointerTracker tracker);
        public void startLongPressTimer(PointerTracker tracker);
        public void startLongPressTimer(int code);
        public void cancelLongPressTimer();
        public void startDoubleTapTimer();
        public void cancelDoubleTapTimer();
        public boolean isInDoubleTapTimeout();
        public void cancelKeyTimers();

        public static class Adapter implements TimerProxy {
            @Override
            public void startTypingStateTimer() {}
            @Override
            public boolean isTypingState() { return false; }
            @Override
            public void startKeyRepeatTimer(PointerTracker tracker) {}
            @Override
            public void startLongPressTimer(PointerTracker tracker) {}
            @Override
            public void startLongPressTimer(int code) {}
            @Override
            public void cancelLongPressTimer() {}
            @Override
            public void startDoubleTapTimer() {}
            @Override
            public void cancelDoubleTapTimer() {}
            @Override
            public boolean isInDoubleTapTimeout() { return false; }
            @Override
            public void cancelKeyTimers() {}
        }
    }

    // Parameters for pointer handling.
    private static LatinKeyboardView.PointerTrackerParams sParams;
    private static int sTouchNoiseThresholdDistanceSquared;
    private static boolean sNeedsPhantomSuddenMoveEventHack;

    private static final ArrayList<PointerTracker> sTrackers = new ArrayList<PointerTracker>();
    private static PointerTrackerQueue sPointerTrackerQueue;

    public final int mPointerId;

    private DrawingProxy mDrawingProxy;
    private TimerProxy mTimerProxy;
    private KeyDetector mKeyDetector;
    private KeyboardActionListener mListener = EMPTY_LISTENER;

    private Keyboard mKeyboard;
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

    public static void init(boolean hasDistinctMultitouch,
            boolean needsPhantomSuddenMoveEventHack) {
        if (hasDistinctMultitouch) {
            sPointerTrackerQueue = new PointerTrackerQueue();
        } else {
            sPointerTrackerQueue = null;
        }
        sNeedsPhantomSuddenMoveEventHack = needsPhantomSuddenMoveEventHack;

        setParameters(LatinKeyboardView.PointerTrackerParams.DEFAULT);
    }

    public static void setParameters(LatinKeyboardView.PointerTrackerParams params) {
        sParams = params;
        sTouchNoiseThresholdDistanceSquared = (int)(
                params.mTouchNoiseThresholdDistance * params.mTouchNoiseThresholdDistance);
    }

    public static PointerTracker getPointerTracker(final int id, KeyEventHandler handler) {
        final ArrayList<PointerTracker> trackers = sTrackers;

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
            tracker.getKeyPreviewText().setVisibility(View.INVISIBLE);
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
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.pointerTracker_callListenerOnPressAndCheckKeyboardLayoutChange(key,
                    ignoreModifierKey);
        }
        if (ignoreModifierKey) {
            return false;
        }
        if (key.isEnabled()) {
            mListener.onPressKey(key.mCode);
            final boolean keyboardLayoutHasBeenChanged = mKeyboardLayoutHasBeenChanged;
            mKeyboardLayoutHasBeenChanged = false;
            if (!key.altCodeWhileTyping() && !key.isModifier()) {
                mTimerProxy.startTypingStateTimer();
            }
            return keyboardLayoutHasBeenChanged;
        }
        return false;
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnCodeInput(Key key, int primaryCode, int x, int y) {
        final boolean ignoreModifierKey = mIgnoreModifierKey && key.isModifier();
        final boolean altersCode = key.altCodeWhileTyping() && mTimerProxy.isTypingState();
        final int code = altersCode ? key.mAltCode : primaryCode;
        if (DEBUG_LISTENER) {
            Log.d(TAG, "onCodeInput: " + Keyboard.printableCode(code) + " text=" + key.mOutputText
                    + " x=" + x + " y=" + y
                    + " ignoreModifier=" + ignoreModifierKey + " altersCode=" + altersCode
                    + " enabled=" + key.isEnabled());
        }
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.pointerTracker_callListenerOnCodeInput(key, x, y, ignoreModifierKey,
                    altersCode, code);
        }
        if (ignoreModifierKey) {
            return;
        }
        // Even if the key is disabled, it should respond if it is in the altCodeWhileTyping state.
        if (key.isEnabled() || altersCode) {
            if (code == Keyboard.CODE_OUTPUT_TEXT) {
                mListener.onTextInput(key.mOutputText);
            } else if (code != Keyboard.CODE_UNSPECIFIED) {
                mListener.onCodeInput(code, x, y);
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
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.pointerTracker_callListenerOnRelease(key, primaryCode, withSliding,
                    ignoreModifierKey);
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
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.pointerTracker_callListenerOnCancelInput();
        }
        mListener.onCancelInput();
    }

    private void setKeyDetectorInner(KeyDetector keyDetector) {
        mKeyDetector = keyDetector;
        mKeyboard = keyDetector.getKeyboard();
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
        return mKeyDetector.detectHitKey(x, y);
    }

    private void setReleasedKeyGraphics(Key key) {
        mDrawingProxy.dismissKeyPreview(this);
        if (key == null) {
            return;
        }

        // Even if the key is disabled, update the key release graphics just in case.
        updateReleaseKeyGraphics(key);

        if (key.isShift()) {
            for (final Key shiftKey : mKeyboard.mShiftKeys) {
                if (shiftKey != key) {
                    updateReleaseKeyGraphics(shiftKey);
                }
            }
        }

        if (key.altCodeWhileTyping()) {
            final int altCode = key.mAltCode;
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                updateReleaseKeyGraphics(altKey);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.mAltCode == altCode) {
                    updateReleaseKeyGraphics(k);
                }
            }
        }
    }

    private void setPressedKeyGraphics(Key key) {
        if (key == null) {
            return;
        }

        // Even if the key is disabled, it should respond if it is in the altCodeWhileTyping state.
        final boolean altersCode = key.altCodeWhileTyping() && mTimerProxy.isTypingState();
        final boolean needsToUpdateGraphics = key.isEnabled() || altersCode;
        if (!needsToUpdateGraphics) {
            return;
        }

        if (!key.noKeyPreview()) {
            mDrawingProxy.showKeyPreview(this);
        }
        updatePressKeyGraphics(key);

        if (key.isShift()) {
            for (final Key shiftKey : mKeyboard.mShiftKeys) {
                if (shiftKey != key) {
                    updatePressKeyGraphics(shiftKey);
                }
            }
        }

        if (key.altCodeWhileTyping() && mTimerProxy.isTypingState()) {
            final int altCode = key.mAltCode;
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                updatePressKeyGraphics(altKey);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.mAltCode == altCode) {
                    updatePressKeyGraphics(k);
                }
            }
        }
    }

    private void updateReleaseKeyGraphics(Key key) {
        key.onReleased();
        mDrawingProxy.invalidateKey(key);
    }

    private void updatePressKeyGraphics(Key key) {
        key.onPressed();
        mDrawingProxy.invalidateKey(key);
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
        return mKeyDetector.detectHitKey(x, y);
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
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.pointerTracker_onDownEvent(deltaT, distanceSquared);
                }
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
                    if (sNeedsPhantomSuddenMoveEventHack
                            && lastMoveSquared >= mKeyQuarterWidthSquared) {
                        if (DEBUG_MODE) {
                            Log.w(TAG, String.format("onMoveEvent:"
                                    + " phantom sudden move event is translated to "
                                    + "up[%d,%d]/down[%d,%d] events", lastX, lastY, x, y));
                        }
                        if (ProductionFlag.IS_EXPERIMENTAL) {
                            ResearchLogger.pointerTracker_onMoveEvent(x, y, lastX, lastY);
                        }
                        onUpEventInternal();
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
        onUpEventInternal();
    }

    // Let this pointer tracker know that one of newer-than-this pointer trackers got an up event.
    // This pointer tracker needs to keep the key top graphics "pressed", but needs to get a
    // "virtual" up event.
    public void onPhantomUpEvent(int x, int y, long eventTime) {
        if (DEBUG_EVENT)
            printTouchEvent("onPhntEvent:", x, y, eventTime);
        onUpEventInternal();
        mKeyAlreadyProcessed = true;
    }

    private void onUpEventInternal() {
        mTimerProxy.cancelKeyTimers();
        mIsInSlidingKeyInput = false;
        // Release the last pressed key.
        setReleasedKeyGraphics(mCurrentKey);
        if (mIsShowingMoreKeysPanel) {
            mDrawingProxy.dismissMoreKeysPanel();
            mIsShowingMoreKeysPanel = false;
        }
        if (mKeyAlreadyProcessed)
            return;
        if (!mIsRepeatableKey) {
            detectAndSendKey(mCurrentKey, mKeyX, mKeyY);
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
            onRegisterKey(key);
            mTimerProxy.startKeyRepeatTimer(this);
            mIsRepeatableKey = true;
        } else {
            mIsRepeatableKey = false;
        }
    }

    public void onRegisterKey(Key key) {
        if (key != null) {
            detectAndSendKey(key, key.mX, key.mY);
            if (!key.altCodeWhileTyping() && !key.isModifier()) {
                mTimerProxy.startTypingStateTimer();
            }
        }
    }

    private boolean isMajorEnoughMoveToBeOnNewKey(int x, int y, Key newKey) {
        if (mKeyDetector == null)
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
        if (key != null && key.isLongPressEnabled()) {
            mTimerProxy.startLongPressTimer(this);
        }
    }

    private void detectAndSendKey(Key key, int x, int y) {
        if (key == null) {
            callListenerOnCancelInput();
            return;
        }

        int code = key.mCode;
        callListenerOnCodeInput(key, code, x, y);
        callListenerOnRelease(key, code, false);
    }

    private long mPreviousEventTime;

    private void printTouchEvent(String title, int x, int y, long eventTime) {
        final Key key = mKeyDetector.detectHitKey(x, y);
        final String code = KeyDetector.printableCode(key);
        final long delta = eventTime - mPreviousEventTime;
        Log.d(TAG, String.format("%s%s[%d] %4d %4d %5d %s", title,
                (mKeyAlreadyProcessed ? "-" : " "), mPointerId, x, y, delta, code));
        mPreviousEventTime = eventTime;
    }
}
