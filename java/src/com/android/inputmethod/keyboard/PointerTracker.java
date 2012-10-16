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
import com.android.inputmethod.keyboard.internal.GestureStroke.GestureStrokeParams;
import com.android.inputmethod.keyboard.internal.GestureStrokeWithPreviewPoints;
import com.android.inputmethod.keyboard.internal.PointerTrackerQueue;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.InputPointers;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.research.ResearchLogger;

import java.util.ArrayList;

public final class PointerTracker implements PointerTrackerQueue.Element {
    private static final String TAG = PointerTracker.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_MOVE_EVENT = false;
    private static final boolean DEBUG_LISTENER = false;
    private static boolean DEBUG_MODE = LatinImeLogger.sDBG || DEBUG_EVENT;

    /** True if {@link PointerTracker}s should handle gesture events. */
    private static boolean sShouldHandleGesture = false;
    private static boolean sMainDictionaryAvailable = false;
    private static boolean sGestureHandlingEnabledByInputField = false;
    private static boolean sGestureHandlingEnabledByUser = false;

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

    static final class PointerTrackerParams {
        public final boolean mSlidingKeyInputEnabled;
        public final int mTouchNoiseThresholdTime;
        public final int mTouchNoiseThresholdDistance;
        public final int mSuppressKeyPreviewAfterBatchInputDuration;

        public static final PointerTrackerParams DEFAULT = new PointerTrackerParams();

        private PointerTrackerParams() {
            mSlidingKeyInputEnabled = false;
            mTouchNoiseThresholdTime = 0;
            mTouchNoiseThresholdDistance = 0;
            mSuppressKeyPreviewAfterBatchInputDuration = 0;
        }

        public PointerTrackerParams(final TypedArray mainKeyboardViewAttr) {
            mSlidingKeyInputEnabled = mainKeyboardViewAttr.getBoolean(
                    R.styleable.MainKeyboardView_slidingKeyInputEnable, false);
            mTouchNoiseThresholdTime = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_touchNoiseThresholdTime, 0);
            mTouchNoiseThresholdDistance = mainKeyboardViewAttr.getDimensionPixelSize(
                    R.styleable.MainKeyboardView_touchNoiseThresholdDistance, 0);
            mSuppressKeyPreviewAfterBatchInputDuration = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_suppressKeyPreviewAfterBatchInputDuration, 0);
        }
    }

    // Parameters for pointer handling.
    private static PointerTrackerParams sParams;
    private static GestureStrokeParams sGestureStrokeParams;
    private static boolean sNeedsPhantomSuddenMoveEventHack;
    // Move this threshold to resource.
    // TODO: Device specific parameter would be better for device specific hack?
    private static final float PHANTOM_SUDDEN_MOVE_THRESHOLD = 0.25f; // in keyWidth
    // This hack might be device specific.
    private static final boolean sNeedsProximateBogusDownMoveUpEventHack = true;

    private static final ArrayList<PointerTracker> sTrackers = CollectionUtils.newArrayList();
    private static PointerTrackerQueue sPointerTrackerQueue;

    public final int mPointerId;

    private DrawingProxy mDrawingProxy;
    private TimerProxy mTimerProxy;
    private KeyDetector mKeyDetector;
    private KeyboardActionListener mListener = EMPTY_LISTENER;

    private Keyboard mKeyboard;
    private int mPhantonSuddenMoveThreshold;
    private final BogusMoveEventDetector mBogusMoveEventDetector = new BogusMoveEventDetector();

    private boolean mIsDetectingGesture = false; // per PointerTracker.
    private static boolean sInGesture = false;
    private static long sGestureFirstDownTime;
    private static TimeRecorder sTimeRecorder;
    private static final InputPointers sAggregratedPointers = new InputPointers(
            GestureStroke.DEFAULT_CAPACITY);
    private static int sLastRecognitionPointSize = 0; // synchronized using sAggregratedPointers
    private static long sLastRecognitionTime = 0; // synchronized using sAggregratedPointers

    static final class BogusMoveEventDetector {
        // Move these thresholds to resource.
        // These thresholds' unit is a diagonal length of a key.
        private static final float BOGUS_MOVE_ACCUMULATED_DISTANCE_THRESHOLD = 0.53f;
        private static final float BOGUS_MOVE_RADIUS_THRESHOLD = 1.14f;

        private int mAccumulatedDistanceThreshold;
        private int mRadiusThreshold;

        // Accumulated distance from actual and artificial down keys.
        /* package */ int mAccumulatedDistanceFromDownKey;
        private int mActualDownX;
        private int mActualDownY;

        public void setKeyboardGeometry(final int keyWidth, final int keyHeight) {
            final float keyDiagonal = (float)Math.hypot(keyWidth, keyHeight);
            mAccumulatedDistanceThreshold = (int)(
                    keyDiagonal * BOGUS_MOVE_ACCUMULATED_DISTANCE_THRESHOLD);
            mRadiusThreshold = (int)(keyDiagonal * BOGUS_MOVE_RADIUS_THRESHOLD);
        }

        public void onActualDownEvent(final int x, final int y) {
            mActualDownX = x;
            mActualDownY = y;
        }

        public void onDownKey() {
            mAccumulatedDistanceFromDownKey = 0;
        }

        public void onMoveKey(final int distance) {
            mAccumulatedDistanceFromDownKey += distance;
        }

        public boolean hasTraveledLongDistance(final int x, final int y) {
            final int dx = Math.abs(x - mActualDownX);
            final int dy = Math.abs(y - mActualDownY);
            // A bogus move event should be a horizontal movement. A vertical movement might be
            // a sloppy typing and should be ignored.
            return dx >= dy && mAccumulatedDistanceFromDownKey >= mAccumulatedDistanceThreshold;
        }

        /* package */ int getDistanceFromDownEvent(final int x, final int y) {
            return getDistance(x, y, mActualDownX, mActualDownY);
        }

        public boolean isCloseToActualDownEvent(final int x, final int y) {
            return getDistanceFromDownEvent(x, y) < mRadiusThreshold;
        }
    }

    static final class TimeRecorder {
        private final int mSuppressKeyPreviewAfterBatchInputDuration;
        private final int mStaticTimeThresholdAfterFastTyping; // msec
        private long mLastTypingTime;
        private long mLastLetterTypingTime;
        private long mLastBatchInputTime;

        public TimeRecorder(final PointerTrackerParams pointerTrackerParams,
                final GestureStrokeParams gestureStrokeParams) {
            mSuppressKeyPreviewAfterBatchInputDuration =
                    pointerTrackerParams.mSuppressKeyPreviewAfterBatchInputDuration;
            mStaticTimeThresholdAfterFastTyping =
                    gestureStrokeParams.mStaticTimeThresholdAfterFastTyping;
        }

        public boolean isInFastTyping(final long eventTime) {
            final long elapsedTimeSinceLastLetterTyping = eventTime - mLastLetterTypingTime;
            return elapsedTimeSinceLastLetterTyping < mStaticTimeThresholdAfterFastTyping;
        }

        private boolean wasLastInputTyping() {
            return mLastTypingTime >= mLastBatchInputTime;
        }

        public void onCodeInput(final int code, final long eventTime) {
            // Record the letter typing time when
            // 1. Letter keys are typed successively without any batch input in between.
            // 2. A letter key is typed within the threshold time since the last any key typing.
            // 3. A non-letter key is typed within the threshold time since the last letter key
            // typing.
            if (Character.isLetter(code)) {
                if (wasLastInputTyping()
                        || eventTime - mLastTypingTime < mStaticTimeThresholdAfterFastTyping) {
                    mLastLetterTypingTime = eventTime;
                }
            } else {
                if (eventTime - mLastLetterTypingTime < mStaticTimeThresholdAfterFastTyping) {
                    // This non-letter typing should be treated as a part of fast typing.
                    mLastLetterTypingTime = eventTime;
                }
            }
            mLastTypingTime = eventTime;
        }

        public void onEndBatchInput(final long eventTime) {
            mLastBatchInputTime = eventTime;
        }

        public long getLastLetterTypingTime() {
            return mLastLetterTypingTime;
        }

        public boolean needsToSuppressKeyPreviewPopup(final long eventTime) {
            return !wasLastInputTyping()
                    && eventTime - mLastBatchInputTime < mSuppressKeyPreviewAfterBatchInputDuration;
        }
    }

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

    // true if this pointer is in a sliding key input.
    boolean mIsInSlidingKeyInput;
    // true if this pointer is in a sliding key input from a modifier key,
    // so that further modifier keys should be ignored.
    boolean mIsInSlidingKeyInputFromModifier;

    // true if a sliding key input is allowed.
    private boolean mIsAllowedSlidingKeyInput;

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
        sGestureStrokeParams = GestureStrokeParams.DEFAULT;
        sTimeRecorder = new TimeRecorder(sParams, sGestureStrokeParams);
    }

    public static void setParameters(final TypedArray mainKeyboardViewAttr) {
        sParams = new PointerTrackerParams(mainKeyboardViewAttr);
        sGestureStrokeParams = new GestureStrokeParams(mainKeyboardViewAttr);
        sTimeRecorder = new TimeRecorder(sParams, sGestureStrokeParams);
    }

    private static void updateGestureHandlingMode() {
        sShouldHandleGesture = sMainDictionaryAvailable
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
        mGestureStrokeWithPreviewPoints = new GestureStrokeWithPreviewPoints(
                id, sGestureStrokeParams);
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
        final boolean ignoreModifierKey = mIsInSlidingKeyInputFromModifier && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onPress    : %s%s%s", mPointerId,
                    KeyDetector.printableCode(key),
                    ignoreModifierKey ? " ignoreModifier" : "",
                    key.isEnabled() ? "" : " disabled"));
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
            final int y, final long eventTime) {
        final boolean ignoreModifierKey = mIsInSlidingKeyInputFromModifier && key.isModifier();
        final boolean altersCode = key.altCodeWhileTyping() && mTimerProxy.isTypingState();
        final int code = altersCode ? key.getAltCode() : primaryCode;
        if (DEBUG_LISTENER) {
            final String output = code == Keyboard.CODE_OUTPUT_TEXT
                    ? key.getOutputText() : Keyboard.printableCode(code);
            Log.d(TAG, String.format("[%d] onCodeInput: %4d %4d %s%s%s", mPointerId, x, y,
                    output, ignoreModifierKey ? " ignoreModifier" : "",
                    altersCode ? " altersCode" : "", key.isEnabled() ? "" : " disabled"));
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
            sTimeRecorder.onCodeInput(code, eventTime);
            if (code == Keyboard.CODE_OUTPUT_TEXT) {
                mListener.onTextInput(key.getOutputText());
            } else if (code != Keyboard.CODE_UNSPECIFIED) {
                mListener.onCodeInput(code, x, y);
            }
        }
    }

    // Note that we need primaryCode argument because the keyboard may be in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnRelease(final Key key, final int primaryCode,
            final boolean withSliding) {
        if (sInGesture) {
            return;
        }
        final boolean ignoreModifierKey = mIsInSlidingKeyInputFromModifier && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onRelease  : %s%s%s%s", mPointerId,
                    Keyboard.printableCode(primaryCode),
                    withSliding ? " sliding" : "", ignoreModifierKey ? " ignoreModifier" : "",
                    key.isEnabled() ?  "": " disabled"));
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
            Log.d(TAG, String.format("[%d] onCancelInput", mPointerId));
        }
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.pointerTracker_callListenerOnCancelInput();
        }
        mListener.onCancelInput();
    }

    private void setKeyDetectorInner(final KeyDetector keyDetector) {
        final Keyboard keyboard = keyDetector.getKeyboard();
        if (keyDetector == mKeyDetector && keyboard == mKeyboard) {
            return;
        }
        mKeyDetector = keyDetector;
        mKeyboard = keyDetector.getKeyboard();
        final int keyWidth = mKeyboard.mMostCommonKeyWidth;
        final int keyHeight = mKeyboard.mMostCommonKeyHeight;
        mGestureStrokeWithPreviewPoints.setKeyboardGeometry(keyWidth);
        final Key newKey = mKeyDetector.detectHitKey(mKeyX, mKeyY);
        if (newKey != mCurrentKey) {
            if (mDrawingProxy != null) {
                setReleasedKeyGraphics(mCurrentKey);
            }
            // Keep {@link #mCurrentKey} that comes from previous keyboard.
        }
        mPhantonSuddenMoveThreshold = (int)(keyWidth * PHANTOM_SUDDEN_MOVE_THRESHOLD);
        mBogusMoveEventDetector.setKeyboardGeometry(keyWidth, keyHeight);
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

    private static boolean needsToSuppressKeyPreviewPopup(final long eventTime) {
        if (!sShouldHandleGesture) return false;
        return sTimeRecorder.needsToSuppressKeyPreviewPopup(eventTime);
    }

    private void setPressedKeyGraphics(final Key key, final long eventTime) {
        if (key == null) {
            return;
        }

        // Even if the key is disabled, it should respond if it is in the altCodeWhileTyping state.
        final boolean altersCode = key.altCodeWhileTyping() && mTimerProxy.isTypingState();
        final boolean needsToUpdateGraphics = key.isEnabled() || altersCode;
        if (!needsToUpdateGraphics) {
            return;
        }

        if (!key.noKeyPreview() && !sInGesture && !needsToSuppressKeyPreviewPopup(eventTime)) {
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
        mBogusMoveEventDetector.onDownKey();
        return onMoveToNewKey(onMoveKeyInternal(x, y), x, y);
    }

    static int getDistance(final int x1, final int y1, final int x2, final int y2) {
        return (int)Math.hypot(x1 - x2, y1 - y2);
    }

    private Key onMoveKeyInternal(final int x, final int y) {
        mBogusMoveEventDetector.onMoveKey(getDistance(x, y, mLastX, mLastY));
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
            Log.d(TAG, String.format("[%d] onStartBatchInput", mPointerId));
        }
        sInGesture = true;
        synchronized (sAggregratedPointers) {
            sAggregratedPointers.reset();
            sLastRecognitionPointSize = 0;
            sLastRecognitionTime = 0;
            mListener.onStartBatchInput();
        }
        final boolean isOldestTracker = sPointerTrackerQueue.getOldestElement() == this;
        mDrawingProxy.showGesturePreviewTrail(this, isOldestTracker);
    }

    private void mayUpdateBatchInput(final long eventTime, final Key key) {
        if (key != null) {
            synchronized (sAggregratedPointers) {
                final GestureStroke stroke = mGestureStrokeWithPreviewPoints;
                stroke.appendIncrementalBatchPoints(sAggregratedPointers);
                final int size = sAggregratedPointers.getPointerSize();
                if (size > sLastRecognitionPointSize
                        && stroke.hasRecognitionTimePast(eventTime, sLastRecognitionTime)) {
                    sLastRecognitionPointSize = size;
                    sLastRecognitionTime = eventTime;
                    if (DEBUG_LISTENER) {
                        Log.d(TAG, String.format("[%d] onUpdateBatchInput: batchPoints=%d",
                                mPointerId, size));
                    }
                    mListener.onUpdateBatchInput(sAggregratedPointers);
                }
            }
        }
        final boolean isOldestTracker = sPointerTrackerQueue.getOldestElement() == this;
        mDrawingProxy.showGesturePreviewTrail(this, isOldestTracker);
    }

    private void mayEndBatchInput(final long eventTime) {
        synchronized (sAggregratedPointers) {
            mGestureStrokeWithPreviewPoints.appendAllBatchPoints(sAggregratedPointers);
            if (getActivePointerTrackerCount() == 1) {
                if (DEBUG_LISTENER) {
                    Log.d(TAG, String.format("[%d] onEndBatchInput   : batchPoints=%d",
                            mPointerId, sAggregratedPointers.getPointerSize()));
                }
                sInGesture = false;
                sTimeRecorder.onEndBatchInput(eventTime);
                mListener.onEndBatchInput(sAggregratedPointers);
            }
        }
        final boolean isOldestTracker = sPointerTrackerQueue.getOldestElement() == this;
        mDrawingProxy.showGesturePreviewTrail(this, isOldestTracker);
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
            final int distance = getDistance(x, y, mLastX, mLastY);
            if (distance < sParams.mTouchNoiseThresholdDistance) {
                if (DEBUG_MODE)
                    Log.w(TAG, String.format("[%d] onDownEvent:"
                            + " ignore potential noise: time=%d distance=%d",
                            mPointerId, deltaT, distance));
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.pointerTracker_onDownEvent(deltaT, distance * distance);
                }
                mKeyAlreadyProcessed = true;
                return;
            }
        }

        final Key key = getKeyOn(x, y);
        mBogusMoveEventDetector.onActualDownEvent(x, y);
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
            mGestureStrokeWithPreviewPoints.onDownEvent(x, y, eventTime, sGestureFirstDownTime,
                    sTimeRecorder.getLastLetterTypingTime());
        }
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
        resetSlidingKeyInput();
        if (key != null) {
            // This onPress call may have changed keyboard layout. Those cases are detected at
            // {@link #setKeyboard}. In those cases, we should update key according to the new
            // keyboard layout.
            if (callListenerOnPressAndCheckKeyboardLayoutChange(key)) {
                key = onDownKey(x, y, eventTime);
            }

            startRepeatKey(key);
            startLongPressTimer(key);
            setPressedKeyGraphics(key, eventTime);
        }
    }

    private void startSlidingKeyInput(final Key key) {
        if (!mIsInSlidingKeyInput) {
            mIsInSlidingKeyInputFromModifier = key.isModifier();
        }
        mIsInSlidingKeyInput = true;
    }

    private void resetSlidingKeyInput() {
        mIsInSlidingKeyInput = false;
        mIsInSlidingKeyInputFromModifier = false;
    }

    private void onGestureMoveEvent(final int x, final int y, final long eventTime,
            final boolean isMajorEvent, final Key key) {
        final int gestureTime = (int)(eventTime - sGestureFirstDownTime);
        if (mIsDetectingGesture) {
            mGestureStrokeWithPreviewPoints.addPoint(x, y, gestureTime, isMajorEvent);
            mayStartBatchInput(key);
            if (sInGesture) {
                mayUpdateBatchInput(eventTime, key);
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
                mTimerProxy.cancelLongPressTimer();
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
                setPressedKeyGraphics(key, eventTime);
            } else if (isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, key)) {
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
                    setPressedKeyGraphics(key, eventTime);
                } else {
                    // HACK: On some devices, quick successive touches may be reported as a sudden
                    // move by touch panel firmware. This hack detects such cases and translates the
                    // move event to successive up and down events.
                    // TODO: Should find a way to balance gesture detection and this hack.
                    if (sNeedsPhantomSuddenMoveEventHack
                            && getDistance(x, y, lastX, lastY) >= mPhantonSuddenMoveThreshold) {
                        if (DEBUG_MODE) {
                            Log.w(TAG, String.format("[%d] onMoveEvent:"
                                    + " phantom sudden move event (distance=%d) is translated to "
                                    + "up[%d,%d,%s]/down[%d,%d,%s] events", mPointerId,
                                    getDistance(x, y, lastX, lastY),
                                    lastX, lastY, Keyboard.printableCode(oldKey.mCode),
                                    x, y, Keyboard.printableCode(key.mCode)));
                        }
                        // TODO: This should be moved to outside of this nested if-clause?
                        if (ProductionFlag.IS_EXPERIMENTAL) {
                            ResearchLogger.pointerTracker_onMoveEvent(x, y, lastX, lastY);
                        }
                        onUpEventInternal(eventTime);
                        onDownEventInternal(x, y, eventTime);
                    }
                    // HACK: On some devices, quick successive proximate touches may be reported as
                    // a bogus down-move-up event by touch panel firmware. This hack detects such
                    // cases and breaks these events into separate up and down events.
                    else if (sNeedsProximateBogusDownMoveUpEventHack
                            && sTimeRecorder.isInFastTyping(eventTime)
                            && mBogusMoveEventDetector.isCloseToActualDownEvent(x, y)) {
                        if (DEBUG_MODE) {
                            final float keyDiagonal = (float)Math.hypot(
                                    mKeyboard.mMostCommonKeyWidth, mKeyboard.mMostCommonKeyHeight);
                            final float radiusRatio =
                                    mBogusMoveEventDetector.getDistanceFromDownEvent(x, y)
                                    / keyDiagonal;
                            Log.w(TAG, String.format("[%d] onMoveEvent:"
                                    + " bogus down-move-up event (raidus=%.2f key diagonal) is "
                                    + " translated to up[%d,%d,%s]/down[%d,%d,%s] events",
                                    mPointerId, radiusRatio,
                                    lastX, lastY, Keyboard.printableCode(oldKey.mCode),
                                    x, y, Keyboard.printableCode(key.mCode)));
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
                            if (DEBUG_MODE) {
                                Log.w(TAG, String.format("[%d] onMoveEvent:"
                                        + " detected sliding finger while multi touching",
                                        mPointerId));
                            }
                            onUpEvent(x, y, eventTime);
                            mKeyAlreadyProcessed = true;
                        }
                        if (!mIsDetectingGesture) {
                            mKeyAlreadyProcessed = true;
                        }
                        setReleasedKeyGraphics(oldKey);
                    }
                }
            }
        } else {
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, key)) {
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
        resetSlidingKeyInput();
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
            mayEndBatchInput(eventTime);
            return;
        }

        if (mKeyAlreadyProcessed) {
            return;
        }
        if (currentKey != null && !currentKey.isRepeatable()) {
            detectAndSendKey(currentKey, mKeyX, mKeyY, eventTime);
        }
    }

    public void onShowMoreKeysPanel(final int x, final int y, final KeyEventHandler handler) {
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
        resetSlidingKeyInput();
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
            detectAndSendKey(key, key.mX, key.mY, SystemClock.uptimeMillis());
            mTimerProxy.startTypingStateTimer(key);
        }
    }

    private boolean isMajorEnoughMoveToBeOnNewKey(final int x, final int y, final long eventTime,
            final Key newKey) {
        if (mKeyDetector == null) {
            throw new NullPointerException("keyboard and/or key detector not set");
        }
        final Key curKey = mCurrentKey;
        if (newKey == curKey) {
            return false;
        } else if (curKey != null) {
            final int keyHysteresisDistanceSquared = mKeyDetector.getKeyHysteresisDistanceSquared(
                    mIsInSlidingKeyInputFromModifier);
            final int distanceFromKeyEdgeSquared = curKey.squaredDistanceToEdge(x, y);
            if (distanceFromKeyEdgeSquared >= keyHysteresisDistanceSquared) {
                if (DEBUG_MODE) {
                    final float distanceToEdgeRatio = (float)Math.sqrt(distanceFromKeyEdgeSquared)
                            / mKeyboard.mMostCommonKeyWidth;
                    Log.d(TAG, String.format("[%d] isMajorEnoughMoveToBeOnNewKey:"
                            +" %.2f key width from key edge",
                            mPointerId, distanceToEdgeRatio));
                }
                return true;
            }
            if (sNeedsProximateBogusDownMoveUpEventHack && !mIsAllowedSlidingKeyInput
                    && sTimeRecorder.isInFastTyping(eventTime)
                    && mBogusMoveEventDetector.hasTraveledLongDistance(x, y)) {
                if (DEBUG_MODE) {
                    final float keyDiagonal = (float)Math.hypot(
                            mKeyboard.mMostCommonKeyWidth, mKeyboard.mMostCommonKeyHeight);
                    final float lengthFromDownRatio =
                            mBogusMoveEventDetector.mAccumulatedDistanceFromDownKey / keyDiagonal;
                    Log.d(TAG, String.format("[%d] isMajorEnoughMoveToBeOnNewKey:"
                            + " %.2f key diagonal from virtual down point",
                            mPointerId, lengthFromDownRatio));
                }
                return true;
            }
            return false;
        } else { // curKey == null && newKey != null
            return true;
        }
    }

    private void startLongPressTimer(final Key key) {
        if (key != null && key.isLongPressEnabled() && !sInGesture) {
            mTimerProxy.startLongPressTimer(this);
        }
    }

    private void detectAndSendKey(final Key key, final int x, final int y, final long eventTime) {
        if (key == null) {
            callListenerOnCancelInput();
            return;
        }

        final int code = key.mCode;
        callListenerOnCodeInput(key, code, x, y, eventTime);
        callListenerOnRelease(key, code, false);
    }

    private void printTouchEvent(final String title, final int x, final int y,
            final long eventTime) {
        final Key key = mKeyDetector.detectHitKey(x, y);
        final String code = KeyDetector.printableCode(key);
        Log.d(TAG, String.format("[%d]%s%s %4d %4d %5d %s", mPointerId,
                (mKeyAlreadyProcessed ? "-" : " "), title, x, y, eventTime, code));
    }
}
