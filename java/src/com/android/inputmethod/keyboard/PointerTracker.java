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

import android.content.res.TypedArray;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.keyboard.internal.GestureStroke;
import com.android.inputmethod.keyboard.internal.GestureStrokeWithPreviewPoints;
import com.android.inputmethod.keyboard.internal.PointerTrackerQueue;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.InputPointers;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.research.ResearchLogger;

import java.util.ArrayList;

public class PointerTracker implements PointerTrackerQueue.Element {
    private static final String TAG = PointerTracker.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_MOVE_EVENT = false;
    private static final boolean DEBUG_LISTENER = false;
    private static boolean DEBUG_MODE = LatinImeLogger.sDBG;

    /** True if {@link PointerTracker}s should handle gesture events. */
    private static boolean sShouldHandleGesture = false;
    private static boolean sMainDictionaryAvailable = false;
    private static boolean sGestureHandlingEnabledByInputField = false;
    private static boolean sGestureHandlingEnabledByUser = false;
    private static boolean sGestureOffWhileFastTyping = false;

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
        public void showKeyPreview(PointerTracker tracker);
        public void dismissKeyPreview(PointerTracker tracker);
        public void showGesturePreviewTrail(PointerTracker tracker, boolean isOldestTracker);
    }

    public interface TimerProxy {
        public void startTypingStateTimer(Key typedKey);
        public boolean isTypingState();
        public void startGestureOffWhileFastTypingTimer();
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
            public void startTypingStateTimer(Key typedKey) {}
            @Override
            public boolean isTypingState() { return false; }
            @Override
            public void startGestureOffWhileFastTypingTimer() {}
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

    static class PointerTrackerParams {
        public final boolean mSlidingKeyInputEnabled;
        public final int mTouchNoiseThresholdTime;
        public final float mTouchNoiseThresholdDistance;
        public final int mTouchNoiseThresholdDistanceSquared;

        public static final PointerTrackerParams DEFAULT = new PointerTrackerParams();

        private PointerTrackerParams() {
            mSlidingKeyInputEnabled = false;
            mTouchNoiseThresholdTime = 0;
            mTouchNoiseThresholdDistance = 0.0f;
            mTouchNoiseThresholdDistanceSquared = 0;
        }

        public PointerTrackerParams(TypedArray mainKeyboardViewAttr) {
            mSlidingKeyInputEnabled = mainKeyboardViewAttr.getBoolean(
                    R.styleable.MainKeyboardView_slidingKeyInputEnable, false);
            mTouchNoiseThresholdTime = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_touchNoiseThresholdTime, 0);
            final float touchNouseThresholdDistance = mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_touchNoiseThresholdDistance, 0);
            mTouchNoiseThresholdDistance = touchNouseThresholdDistance;
            mTouchNoiseThresholdDistanceSquared =
                    (int)(touchNouseThresholdDistance * touchNouseThresholdDistance);
        }
    }

    // Parameters for pointer handling.
    private static PointerTrackerParams sParams;
    private static boolean sNeedsPhantomSuddenMoveEventHack;

    private static final ArrayList<PointerTracker> sTrackers = CollectionUtils.newArrayList();
    private static PointerTrackerQueue sPointerTrackerQueue;

    public final int mPointerId;

    private DrawingProxy mDrawingProxy;
    private TimerProxy mTimerProxy;
    private KeyDetector mKeyDetector;
    private KeyboardActionListener mListener = EMPTY_LISTENER;

    private Keyboard mKeyboard;
    private int mKeyQuarterWidthSquared;

    private boolean mIsDetectingGesture = false; // per PointerTracker.
    private static boolean sInGesture = false;
    private static long sGestureFirstDownTime;
    private static long sLastLetterTypingUpTime;
    private static final InputPointers sAggregratedPointers = new InputPointers(
            GestureStroke.DEFAULT_CAPACITY);
    private static int sLastRecognitionPointSize = 0;
    private static long sLastRecognitionTime = 0;

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

    // true if this pointer is in sliding key input
    boolean mIsInSlidingKeyInput;

    // true if sliding key is allowed.
    private boolean mIsAllowedSlidingKeyInput;

    // ignore modifier key if true
    private boolean mIgnoreModifierKey;

    // Empty {@link KeyboardActionListener}
    private static final KeyboardActionListener EMPTY_LISTENER =
            new KeyboardActionListener.Adapter();

    private final GestureStrokeWithPreviewPoints mGestureStrokeWithPreviewPoints;

    public static void init(boolean hasDistinctMultitouch,
            boolean needsPhantomSuddenMoveEventHack) {
        if (hasDistinctMultitouch) {
            sPointerTrackerQueue = new PointerTrackerQueue();
        } else {
            sPointerTrackerQueue = null;
        }
        sNeedsPhantomSuddenMoveEventHack = needsPhantomSuddenMoveEventHack;
        sParams = PointerTrackerParams.DEFAULT;
    }

    public static void setParameters(final TypedArray mainKeyboardViewAttr) {
        sParams = new PointerTrackerParams(mainKeyboardViewAttr);
    }

    private static void updateGestureHandlingMode() {
        sShouldHandleGesture = sMainDictionaryAvailable
                && !sGestureOffWhileFastTyping
                && sGestureHandlingEnabledByInputField
                && sGestureHandlingEnabledByUser
                && !AccessibilityUtils.getInstance().isTouchExplorationEnabled();
    }

    // Note that this method is called from a non-UI thread.
    public static void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        sMainDictionaryAvailable = mainDictionaryAvailable;
        updateGestureHandlingMode();
    }

    public static void setGestureHandlingEnabledByUser(final boolean gestureHandlingEnabledByUser) {
        sGestureHandlingEnabledByUser = gestureHandlingEnabledByUser;
        updateGestureHandlingMode();
    }

    public static void setGestureOffWhileFastTyping() {
        sGestureOffWhileFastTyping = true;
        updateGestureHandlingMode();
    }

    public static void clearGestureOffWhileFastTyping() {
        sGestureOffWhileFastTyping = false;
        updateGestureHandlingMode();
    }

    public static PointerTracker getPointerTracker(final int id, final KeyEventHandler handler) {
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

    public static void setKeyboardActionListener(final KeyboardActionListener listener) {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.mListener = listener;
        }
    }

    public static void setKeyDetector(final KeyDetector keyDetector) {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.setKeyDetectorInner(keyDetector);
            // Mark that keyboard layout has been changed.
            tracker.mKeyboardLayoutHasBeenChanged = true;
        }
        final Keyboard keyboard = keyDetector.getKeyboard();
        sGestureHandlingEnabledByInputField = !keyboard.mId.passwordInput();
        updateGestureHandlingMode();
    }

    public static void setReleasedKeyGraphicsToAllKeys() {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.setReleasedKeyGraphics(tracker.mCurrentKey);
        }
    }

    private PointerTracker(final int id, final KeyEventHandler handler) {
        if (handler == null) {
            throw new NullPointerException();
        }
        mPointerId = id;
        mGestureStrokeWithPreviewPoints = new GestureStrokeWithPreviewPoints(id);
        setKeyDetectorInner(handler.getKeyDetector());
        mListener = handler.getKeyboardActionListener();
        mDrawingProxy = handler.getDrawingProxy();
        mTimerProxy = handler.getTimerProxy();
    }

    // Returns true if keyboard has been changed by this callback.
    private boolean callListenerOnPressAndCheckKeyboardLayoutChange(final Key key) {
        if (sInGesture) {
            return false;
        }
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
            mTimerProxy.startTypingStateTimer(key);
            return keyboardLayoutHasBeenChanged;
        }
        return false;
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnCodeInput(final Key key, final int primaryCode, final int x,
            final int y) {
        final boolean ignoreModifierKey = mIgnoreModifierKey && key.isModifier();
        final boolean altersCode = key.altCodeWhileTyping() && mTimerProxy.isTypingState();
        final int code = altersCode ? key.getAltCode() : primaryCode;
        if (DEBUG_LISTENER) {
            Log.d(TAG, "onCodeInput: " + Keyboard.printableCode(code)
                    + " text=" + key.getOutputText() + " x=" + x + " y=" + y
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
                mListener.onTextInput(key.getOutputText());
                mTimerProxy.startGestureOffWhileFastTypingTimer();
            } else if (code != Keyboard.CODE_UNSPECIFIED) {
                mListener.onCodeInput(code, x, y);
                mTimerProxy.startGestureOffWhileFastTypingTimer();
            }
        }
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnRelease(final Key key, final int primaryCode,
            final boolean withSliding) {
        if (sInGesture) {
            return;
        }
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
        if (DEBUG_LISTENER) {
            Log.d(TAG, "onCancelInput");
        }
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.pointerTracker_callListenerOnCancelInput();
        }
        mListener.onCancelInput();
    }

    private void setKeyDetectorInner(final KeyDetector keyDetector) {
        mKeyDetector = keyDetector;
        mKeyboard = keyDetector.getKeyboard();
        mGestureStrokeWithPreviewPoints.setKeyboardGeometry(mKeyboard.mMostCommonKeyWidth);
        final Key newKey = mKeyDetector.detectHitKey(mKeyX, mKeyY);
        if (newKey != mCurrentKey) {
            if (mDrawingProxy != null) {
                setReleasedKeyGraphics(mCurrentKey);
            }
            // Keep {@link #mCurrentKey} that comes from previous keyboard.
        }
        final int keyQuarterWidth = mKeyboard.mMostCommonKeyWidth / 4;
        mKeyQuarterWidthSquared = keyQuarterWidth * keyQuarterWidth;
    }

    @Override
    public boolean isInSlidingKeyInput() {
        return mIsInSlidingKeyInput;
    }

    public Key getKey() {
        return mCurrentKey;
    }

    @Override
    public boolean isModifier() {
        return mCurrentKey != null && mCurrentKey.isModifier();
    }

    public Key getKeyOn(final int x, final int y) {
        return mKeyDetector.detectHitKey(x, y);
    }

    private void setReleasedKeyGraphics(final Key key) {
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
            final int altCode = key.getAltCode();
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                updateReleaseKeyGraphics(altKey);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.getAltCode() == altCode) {
                    updateReleaseKeyGraphics(k);
                }
            }
        }
    }

    private void setPressedKeyGraphics(final Key key) {
        if (key == null) {
            return;
        }

        // Even if the key is disabled, it should respond if it is in the altCodeWhileTyping state.
        final boolean altersCode = key.altCodeWhileTyping() && mTimerProxy.isTypingState();
        final boolean needsToUpdateGraphics = key.isEnabled() || altersCode;
        if (!needsToUpdateGraphics) {
            return;
        }

        if (!key.noKeyPreview() && !sInGesture) {
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
            final int altCode = key.getAltCode();
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                updatePressKeyGraphics(altKey);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.getAltCode() == altCode) {
                    updatePressKeyGraphics(k);
                }
            }
        }
    }

    private void updateReleaseKeyGraphics(final Key key) {
        key.onReleased();
        mDrawingProxy.invalidateKey(key);
    }

    private void updatePressKeyGraphics(final Key key) {
        key.onPressed();
        mDrawingProxy.invalidateKey(key);
    }

    public GestureStrokeWithPreviewPoints getGestureStrokeWithPreviewPoints() {
        return mGestureStrokeWithPreviewPoints;
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

    private Key onDownKey(final int x, final int y, final long eventTime) {
        mDownTime = eventTime;
        return onMoveToNewKey(onMoveKeyInternal(x, y), x, y);
    }

    private Key onMoveKeyInternal(final int x, final int y) {
        mLastX = x;
        mLastY = y;
        return mKeyDetector.detectHitKey(x, y);
    }

    private Key onMoveKey(final int x, final int y) {
        return onMoveKeyInternal(x, y);
    }

    private Key onMoveToNewKey(final Key newKey, final int x, final int y) {
        mCurrentKey = newKey;
        mKeyX = x;
        mKeyY = y;
        return newKey;
    }

    private static int getActivePointerTrackerCount() {
        return (sPointerTrackerQueue == null) ? 1 : sPointerTrackerQueue.size();
    }

    private void mayStartBatchInput(final Key key) {
        if (sInGesture || !mGestureStrokeWithPreviewPoints.isStartOfAGesture()) {
            return;
        }
        if (key == null || !Character.isLetter(key.mCode)) {
            return;
        }
        if (DEBUG_LISTENER) {
            Log.d(TAG, "onStartBatchInput");
        }
        sInGesture = true;
        mListener.onStartBatchInput();
        final boolean isOldestTracker = sPointerTrackerQueue.getOldestElement() == this;
        mDrawingProxy.showGesturePreviewTrail(this, isOldestTracker);
    }

    private void updateBatchInput(final long eventTime) {
        synchronized (sAggregratedPointers) {
            mGestureStrokeWithPreviewPoints.appendIncrementalBatchPoints(sAggregratedPointers);
            final int size = sAggregratedPointers.getPointerSize();
            if (size > sLastRecognitionPointSize
                    && GestureStroke.hasRecognitionTimePast(eventTime, sLastRecognitionTime)) {
                sLastRecognitionPointSize = size;
                sLastRecognitionTime = eventTime;
                if (DEBUG_LISTENER) {
                    Log.d(TAG, "onUpdateBatchInput: batchPoints=" + size);
                }
                mListener.onUpdateBatchInput(sAggregratedPointers);
            }
        }
        final boolean isOldestTracker = sPointerTrackerQueue.getOldestElement() == this;
        mDrawingProxy.showGesturePreviewTrail(this, isOldestTracker);
    }

    private void mayEndBatchInput() {
        synchronized (sAggregratedPointers) {
            mGestureStrokeWithPreviewPoints.appendAllBatchPoints(sAggregratedPointers);
            mGestureStrokeWithPreviewPoints.reset();
            if (getActivePointerTrackerCount() == 1) {
                if (DEBUG_LISTENER) {
                    Log.d(TAG, "onEndBatchInput: batchPoints="
                            + sAggregratedPointers.getPointerSize());
                }
                sInGesture = false;
                mListener.onEndBatchInput(sAggregratedPointers);
                clearBatchInputPointsOfAllPointerTrackers();
            }
        }
        final boolean isOldestTracker = sPointerTrackerQueue.getOldestElement() == this;
        mDrawingProxy.showGesturePreviewTrail(this, isOldestTracker);
    }

    private static void abortBatchInput() {
        clearBatchInputPointsOfAllPointerTrackers();
    }

    private static void clearBatchInputPointsOfAllPointerTrackers() {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.mGestureStrokeWithPreviewPoints.reset();
        }
        sAggregratedPointers.reset();
        sLastRecognitionPointSize = 0;
        sLastRecognitionTime = 0;
    }

    public void processMotionEvent(final int action, final int x, final int y, final long eventTime,
            final KeyEventHandler handler) {
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
            onMoveEvent(x, y, eventTime, null);
            break;
        case MotionEvent.ACTION_CANCEL:
            onCancelEvent(x, y, eventTime);
            break;
        }
    }

    public void onDownEvent(final int x, final int y, final long eventTime,
            final KeyEventHandler handler) {
        if (DEBUG_EVENT) {
            printTouchEvent("onDownEvent:", x, y, eventTime);
        }

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
            if (distanceSquared < sParams.mTouchNoiseThresholdDistanceSquared) {
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

        final Key key = getKeyOn(x, y);
        final PointerTrackerQueue queue = sPointerTrackerQueue;
        if (queue != null) {
            if (key != null && key.isModifier()) {
                // Before processing a down event of modifier key, all pointers already being
                // tracked should be released.
                queue.releaseAllPointers(eventTime);
            }
            queue.add(this);
        }
        onDownEventInternal(x, y, eventTime);
        if (!sShouldHandleGesture) {
            return;
        }
        // A gesture should start only from the letter key.
        mIsDetectingGesture = (mKeyboard != null) && mKeyboard.mId.isAlphabetKeyboard()
                && !mIsShowingMoreKeysPanel && key != null && Keyboard.isLetterCode(key.mCode);
        if (mIsDetectingGesture) {
            if (getActivePointerTrackerCount() == 1) {
                sGestureFirstDownTime = eventTime;
            }
            onGestureDownEvent(x, y, eventTime);
        }
    }

    private void onGestureDownEvent(final int x, final int y, final long eventTime) {
        mIsDetectingGesture = true;
        mGestureStrokeWithPreviewPoints.setLastLetterTypingTime(eventTime, sLastLetterTypingUpTime);
        final int elapsedTimeFromFirstDown = (int)(eventTime - sGestureFirstDownTime);
        mGestureStrokeWithPreviewPoints.addPoint(x, y, elapsedTimeFromFirstDown,
                true /* isMajorEvent */);
    }

    private void onDownEventInternal(final int x, final int y, final long eventTime) {
        Key key = onDownKey(x, y, eventTime);
        // Sliding key is allowed when 1) enabled by configuration, 2) this pointer starts sliding
        // from modifier key, or 3) this pointer's KeyDetector always allows sliding input.
        mIsAllowedSlidingKeyInput = sParams.mSlidingKeyInputEnabled
                || (key != null && key.isModifier())
                || mKeyDetector.alwaysAllowsSlidingInput();
        mKeyboardLayoutHasBeenChanged = false;
        mKeyAlreadyProcessed = false;
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

    private void startSlidingKeyInput(final Key key) {
        if (!mIsInSlidingKeyInput) {
            mIgnoreModifierKey = key.isModifier();
        }
        mIsInSlidingKeyInput = true;
    }

    private void onGestureMoveEvent(final int x, final int y, final long eventTime,
            final boolean isMajorEvent, final Key key) {
        final int gestureTime = (int)(eventTime - sGestureFirstDownTime);
        if (mIsDetectingGesture) {
            mGestureStrokeWithPreviewPoints.addPoint(x, y, gestureTime, isMajorEvent);
            mayStartBatchInput(key);
            if (sInGesture && key != null) {
                updateBatchInput(eventTime);
            }
        }
    }

    public void onMoveEvent(final int x, final int y, final long eventTime, final MotionEvent me) {
        if (DEBUG_MOVE_EVENT) {
            printTouchEvent("onMoveEvent:", x, y, eventTime);
        }
        if (mKeyAlreadyProcessed) {
            return;
        }

        if (sShouldHandleGesture && me != null) {
            // Add historical points to gesture path.
            final int pointerIndex = me.findPointerIndex(mPointerId);
            final int historicalSize = me.getHistorySize();
            for (int h = 0; h < historicalSize; h++) {
                final int historicalX = (int)me.getHistoricalX(pointerIndex, h);
                final int historicalY = (int)me.getHistoricalY(pointerIndex, h);
                final long historicalTime = me.getHistoricalEventTime(h);
                onGestureMoveEvent(historicalX, historicalY, historicalTime,
                        false /* isMajorEvent */, null);
            }
        }

        onMoveEventInternal(x, y, eventTime);
    }

    private void onMoveEventInternal(final int x, final int y, final long eventTime) {
        final int lastX = mLastX;
        final int lastY = mLastY;
        final Key oldKey = mCurrentKey;
        Key key = onMoveKey(x, y);

        if (sShouldHandleGesture) {
            // Register move event on gesture tracker.
            onGestureMoveEvent(x, y, eventTime, true /* isMajorEvent */, key);
            if (sInGesture) {
                mIgnoreModifierKey = true;
                mTimerProxy.cancelLongPressTimer();
                mIsInSlidingKeyInput = true;
                mCurrentKey = null;
                setReleasedKeyGraphics(oldKey);
                return;
            }
        }

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
                    // TODO: Should find a way to balance gesture detection and this hack.
                    if (sNeedsPhantomSuddenMoveEventHack
                            && lastMoveSquared >= mKeyQuarterWidthSquared
                            && !mIsDetectingGesture) {
                        if (DEBUG_MODE) {
                            Log.w(TAG, String.format("onMoveEvent:"
                                    + " phantom sudden move event is translated to "
                                    + "up[%d,%d]/down[%d,%d] events", lastX, lastY, x, y));
                        }
                        // TODO: This should be moved to outside of this nested if-clause?
                        if (ProductionFlag.IS_EXPERIMENTAL) {
                            ResearchLogger.pointerTracker_onMoveEvent(x, y, lastX, lastY);
                        }
                        onUpEventInternal(eventTime);
                        onDownEventInternal(x, y, eventTime);
                    } else {
                        // HACK: If there are currently multiple touches, register the key even if
                        // the finger slides off the key. This defends against noise from some
                        // touch panels when there are close multiple touches.
                        // Caveat: When in chording input mode with a modifier key, we don't use
                        // this hack.
                        if (getActivePointerTrackerCount() > 1 && sPointerTrackerQueue != null
                                && !sPointerTrackerQueue.hasModifierKeyOlderThan(this)) {
                            onUpEventInternal(eventTime);
                        }
                        if (!mIsDetectingGesture) {
                            mKeyAlreadyProcessed = true;
                        }
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
                    if (!mIsDetectingGesture) {
                        mKeyAlreadyProcessed = true;
                    }
                }
            }
        }
    }

    public void onUpEvent(final int x, final int y, final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onUpEvent  :", x, y, eventTime);
        }

        final PointerTrackerQueue queue = sPointerTrackerQueue;
        if (queue != null) {
            if (!sInGesture) {
                if (mCurrentKey != null && mCurrentKey.isModifier()) {
                    // Before processing an up event of modifier key, all pointers already being
                    // tracked should be released.
                    queue.releaseAllPointersExcept(this, eventTime);
                } else {
                    queue.releaseAllPointersOlderThan(this, eventTime);
                }
            }
        }
        onUpEventInternal(eventTime);
        if (queue != null) {
            queue.remove(this);
        }
    }

    // Let this pointer tracker know that one of newer-than-this pointer trackers got an up event.
    // This pointer tracker needs to keep the key top graphics "pressed", but needs to get a
    // "virtual" up event.
    @Override
    public void onPhantomUpEvent(final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onPhntEvent:", getLastX(), getLastY(), eventTime);
        }
        onUpEventInternal(eventTime);
        mKeyAlreadyProcessed = true;
    }

    private void onUpEventInternal(final long eventTime) {
        mTimerProxy.cancelKeyTimers();
        mIsInSlidingKeyInput = false;
        mIsDetectingGesture = false;
        final Key currentKey = mCurrentKey;
        mCurrentKey = null;
        // Release the last pressed key.
        setReleasedKeyGraphics(currentKey);
        if (mIsShowingMoreKeysPanel) {
            mDrawingProxy.dismissMoreKeysPanel();
            mIsShowingMoreKeysPanel = false;
        }

        if (sInGesture) {
            if (currentKey != null) {
                callListenerOnRelease(currentKey, currentKey.mCode, true);
            }
            mayEndBatchInput();
            return;
        }
        // This event will be recognized as a regular code input. Clear unused possible batch points
        // so they are not mistakenly displayed as preview.
        clearBatchInputPointsOfAllPointerTrackers();
        if (mKeyAlreadyProcessed) {
            return;
        }
        if (currentKey != null && !currentKey.isRepeatable()) {
            detectAndSendKey(currentKey, mKeyX, mKeyY);
            final int code = currentKey.mCode;
            if (Keyboard.isLetterCode(code) && code != Keyboard.CODE_SPACE) {
                sLastLetterTypingUpTime = eventTime;
            }
        }
    }

    public void onShowMoreKeysPanel(final int x, final int y, final KeyEventHandler handler) {
        abortBatchInput();
        onLongPressed();
        mIsShowingMoreKeysPanel = true;
        onDownEvent(x, y, SystemClock.uptimeMillis(), handler);
    }

    public void onLongPressed() {
        mKeyAlreadyProcessed = true;
        setReleasedKeyGraphics(mCurrentKey);
        final PointerTrackerQueue queue = sPointerTrackerQueue;
        if (queue != null) {
            queue.remove(this);
        }
    }

    public void onCancelEvent(final int x, final int y, final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onCancelEvt:", x, y, eventTime);
        }

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

    private void startRepeatKey(final Key key) {
        if (key != null && key.isRepeatable() && !sInGesture) {
            onRegisterKey(key);
            mTimerProxy.startKeyRepeatTimer(this);
        }
    }

    public void onRegisterKey(final Key key) {
        if (key != null) {
            detectAndSendKey(key, key.mX, key.mY);
            mTimerProxy.startTypingStateTimer(key);
        }
    }

    private boolean isMajorEnoughMoveToBeOnNewKey(final int x, final int y, final Key newKey) {
        if (mKeyDetector == null) {
            throw new NullPointerException("keyboard and/or key detector not set");
        }
        final Key curKey = mCurrentKey;
        if (newKey == curKey) {
            return false;
        } else if (curKey != null) {
            return curKey.squaredDistanceToEdge(x, y)
                    >= mKeyDetector.getKeyHysteresisDistanceSquared();
        } else {
            return true;
        }
    }

    private void startLongPressTimer(final Key key) {
        if (key != null && key.isLongPressEnabled() && !sInGesture) {
            mTimerProxy.startLongPressTimer(this);
        }
    }

    private void detectAndSendKey(final Key key, final int x, final int y) {
        if (key == null) {
            callListenerOnCancelInput();
            return;
        }

        final int code = key.mCode;
        callListenerOnCodeInput(key, code, x, y);
        callListenerOnRelease(key, code, false);
    }

    private void printTouchEvent(final String title, final int x, final int y,
            final long eventTime) {
        final Key key = mKeyDetector.detectHitKey(x, y);
        final String code = KeyDetector.printableCode(key);
        Log.d(TAG, String.format("%s%s[%d] %4d %4d %5d %s", title,
                (mKeyAlreadyProcessed ? "-" : " "), mPointerId, x, y, eventTime, code));
    }
}
