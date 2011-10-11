/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.PopupWindow;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.deprecated.VoiceProxy;
import com.android.inputmethod.keyboard.PointerTracker.DrawingProxy;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;
import com.android.inputmethod.latin.Utils;

import java.util.WeakHashMap;

/**
 * A view that is responsible for detecting key presses and touch movements.
 *
 * @attr ref R.styleable#KeyboardView_keyHysteresisDistance
 * @attr ref R.styleable#KeyboardView_verticalCorrection
 * @attr ref R.styleable#KeyboardView_popupLayout
 */
public class LatinKeyboardView extends KeyboardView implements PointerTracker.KeyEventHandler,
        SuddenJumpingTouchEventHandler.ProcessMotionEvent {
    private static final String TAG = LatinKeyboardView.class.getSimpleName();

    private static final boolean ENABLE_CAPSLOCK_BY_DOUBLETAP = true;

    private final SuddenJumpingTouchEventHandler mTouchScreenRegulator;

    // Timing constants
    private final int mKeyRepeatInterval;

    // Mini keyboard
    private PopupWindow mMoreKeysWindow;
    private MoreKeysPanel mMoreKeysPanel;
    private int mMoreKeysPanelPointerTrackerId;
    private final WeakHashMap<Key, MoreKeysPanel> mMoreKeysPanelCache =
            new WeakHashMap<Key, MoreKeysPanel>();

    /** Listener for {@link KeyboardActionListener}. */
    private KeyboardActionListener mKeyboardActionListener;

    private final boolean mHasDistinctMultitouch;
    private int mOldPointerCount = 1;
    private int mOldKeyIndex;

    private final boolean mConfigShowMiniKeyboardAtTouchedPoint;
    protected KeyDetector mKeyDetector;

    // To detect double tap.
    protected GestureDetector mGestureDetector;

    private final KeyTimerHandler mKeyTimerHandler = new KeyTimerHandler(this);

    private static class KeyTimerHandler extends StaticInnerHandlerWrapper<LatinKeyboardView>
            implements TimerProxy {
        private static final int MSG_REPEAT_KEY = 1;
        private static final int MSG_LONGPRESS_KEY = 2;
        private static final int MSG_IGNORE_DOUBLE_TAP = 3;

        private boolean mInKeyRepeat;

        public KeyTimerHandler(LatinKeyboardView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            final LatinKeyboardView keyboardView = getOuterInstance();
            final PointerTracker tracker = (PointerTracker) msg.obj;
            switch (msg.what) {
            case MSG_REPEAT_KEY:
                tracker.onRepeatKey(msg.arg1);
                startKeyRepeatTimer(keyboardView.mKeyRepeatInterval, msg.arg1, tracker);
                break;
            case MSG_LONGPRESS_KEY:
                keyboardView.openMiniKeyboardIfRequired(msg.arg1, tracker);
                break;
            }
        }

        @Override
        public void startKeyRepeatTimer(long delay, int keyIndex, PointerTracker tracker) {
            mInKeyRepeat = true;
            sendMessageDelayed(obtainMessage(MSG_REPEAT_KEY, keyIndex, 0, tracker), delay);
        }

        public void cancelKeyRepeatTimer() {
            mInKeyRepeat = false;
            removeMessages(MSG_REPEAT_KEY);
        }

        public boolean isInKeyRepeat() {
            return mInKeyRepeat;
        }

        @Override
        public void startLongPressTimer(long delay, int keyIndex, PointerTracker tracker) {
            cancelLongPressTimer();
            sendMessageDelayed(obtainMessage(MSG_LONGPRESS_KEY, keyIndex, 0, tracker), delay);
        }

        @Override
        public void cancelLongPressTimer() {
            removeMessages(MSG_LONGPRESS_KEY);
        }

        @Override
        public void cancelKeyTimers() {
            cancelKeyRepeatTimer();
            cancelLongPressTimer();
            removeMessages(MSG_IGNORE_DOUBLE_TAP);
        }

        public void startIgnoringDoubleTap() {
            sendMessageDelayed(obtainMessage(MSG_IGNORE_DOUBLE_TAP),
                    ViewConfiguration.getDoubleTapTimeout());
        }

        public boolean isIgnoringDoubleTap() {
            return hasMessages(MSG_IGNORE_DOUBLE_TAP);
        }

        public void cancelAllMessages() {
            cancelKeyTimers();
        }
    }

    private class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
        private boolean mProcessingShiftDoubleTapEvent = false;

        @Override
        public boolean onDoubleTap(MotionEvent firstDown) {
            final Keyboard keyboard = getKeyboard();
            if (ENABLE_CAPSLOCK_BY_DOUBLETAP && keyboard instanceof LatinKeyboard
                    && ((LatinKeyboard) keyboard).isAlphaKeyboard()) {
                final int pointerIndex = firstDown.getActionIndex();
                final int id = firstDown.getPointerId(pointerIndex);
                final PointerTracker tracker = getPointerTracker(id);
                // If the first down event is on shift key.
                if (tracker.isOnShiftKey((int) firstDown.getX(), (int) firstDown.getY())) {
                    mProcessingShiftDoubleTapEvent = true;
                    return true;
                }
            }
            mProcessingShiftDoubleTapEvent = false;
            return false;
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent secondTap) {
            if (mProcessingShiftDoubleTapEvent
                    && secondTap.getAction() == MotionEvent.ACTION_DOWN) {
                final MotionEvent secondDown = secondTap;
                final int pointerIndex = secondDown.getActionIndex();
                final int id = secondDown.getPointerId(pointerIndex);
                final PointerTracker tracker = getPointerTracker(id);
                // If the second down event is also on shift key.
                if (tracker.isOnShiftKey((int) secondDown.getX(), (int) secondDown.getY())) {
                    // Detected a double tap on shift key. If we are in the ignoring double tap
                    // mode, it means we have already turned off caps lock in
                    // {@link KeyboardSwitcher#onReleaseShift} .
                    onDoubleTapShiftKey(tracker, mKeyTimerHandler.isIgnoringDoubleTap());
                    return true;
                }
                // Otherwise these events should not be handled as double tap.
                mProcessingShiftDoubleTapEvent = false;
            }
            return mProcessingShiftDoubleTapEvent;
        }
    }

    public LatinKeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mTouchScreenRegulator = new SuddenJumpingTouchEventHandler(getContext(), this);

        final Resources res = getResources();
        mConfigShowMiniKeyboardAtTouchedPoint = res.getBoolean(
                R.bool.config_show_mini_keyboard_at_touched_point);
        final float keyHysteresisDistance = res.getDimension(R.dimen.key_hysteresis_distance);
        mKeyDetector = new KeyDetector(keyHysteresisDistance);

        final boolean ignoreMultitouch = true;
        mGestureDetector = new GestureDetector(
                getContext(), new DoubleTapListener(), null, ignoreMultitouch);
        mGestureDetector.setIsLongpressEnabled(false);

        mHasDistinctMultitouch = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT);
        mKeyRepeatInterval = res.getInteger(R.integer.config_key_repeat_interval);

        PointerTracker.init(mHasDistinctMultitouch, getContext());
    }

    public void startIgnoringDoubleTap() {
        if (ENABLE_CAPSLOCK_BY_DOUBLETAP)
            mKeyTimerHandler.startIgnoringDoubleTap();
    }

    public void setKeyboardActionListener(KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        PointerTracker.setKeyboardActionListener(listener);
    }

    /**
     * Returns the {@link KeyboardActionListener} object.
     * @return the listener attached to this keyboard
     */
    @Override
    public KeyboardActionListener getKeyboardActionListener() {
        return mKeyboardActionListener;
    }

    @Override
    public KeyDetector getKeyDetector() {
        return mKeyDetector;
    }

    @Override
    public DrawingProxy getDrawingProxy() {
        return this;
    }

    @Override
    public TimerProxy getTimerProxy() {
        return mKeyTimerHandler;
    }

    @Override
    public void setKeyPreviewPopupEnabled(boolean previewEnabled, int delay) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard instanceof LatinKeyboard) {
            final LatinKeyboard latinKeyboard = (LatinKeyboard)keyboard;
            if (latinKeyboard.isPhoneKeyboard() || latinKeyboard.isNumberKeyboard()) {
                // Phone and number keyboard never shows popup preview.
                super.setKeyPreviewPopupEnabled(false, delay);
                return;
            }
        }
        super.setKeyPreviewPopupEnabled(previewEnabled, delay);
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    @Override
    public void setKeyboard(Keyboard keyboard) {
        // Remove any pending messages, except dismissing preview
        mKeyTimerHandler.cancelKeyTimers();
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft(), -getPaddingTop() + mVerticalCorrection);
        mKeyDetector.setProximityThreshold(keyboard.mMostCommonKeyWidth);
        PointerTracker.setKeyDetector(mKeyDetector);
        mTouchScreenRegulator.setKeyboard(keyboard);
        mMoreKeysPanelCache.clear();
    }

    /**
     * Returns whether the device has distinct multi-touch panel.
     * @return true if the device has distinct multi-touch panel.
     */
    public boolean hasDistinctMultitouch() {
        return mHasDistinctMultitouch;
    }

    /**
     * When enabled, calls to {@link KeyboardActionListener#onCodeInput} will include key
     * codes for adjacent keys.  When disabled, only the primary key code will be
     * reported.
     * @param enabled whether or not the proximity correction is enabled
     */
    public void setProximityCorrectionEnabled(boolean enabled) {
        mKeyDetector.setProximityCorrectionEnabled(enabled);
    }

    /**
     * Returns true if proximity correction is enabled.
     */
    public boolean isProximityCorrectionEnabled() {
        return mKeyDetector.isProximityCorrectionEnabled();
    }

    @Override
    public void cancelAllMessages() {
        mKeyTimerHandler.cancelAllMessages();
        super.cancelAllMessages();
    }

    private boolean openMiniKeyboardIfRequired(int keyIndex, PointerTracker tracker) {
        // Check if we have a popup layout specified first.
        if (mMoreKeysLayout == 0) {
            return false;
        }

        // Check if we are already displaying popup panel.
        if (mMoreKeysPanel != null)
            return false;
        final Key parentKey = tracker.getKey(keyIndex);
        if (parentKey == null)
            return false;
        return onLongPress(parentKey, tracker);
    }

    private void onDoubleTapShiftKey(@SuppressWarnings("unused") PointerTracker tracker,
            final boolean ignore) {
        // When shift key is double tapped, the first tap is correctly processed as usual tap. And
        // the second tap is treated as this double tap event, so that we need not mark tracker
        // calling setAlreadyProcessed() nor remove the tracker from mPointerQueue.
        final int primaryCode = ignore ? Keyboard.CODE_HAPTIC_AND_AUDIO_FEEDBACK_ONLY
                : Keyboard.CODE_CAPSLOCK;
        mKeyboardActionListener.onCodeInput(primaryCode, null, 0, 0);
    }

    // This default implementation returns a more keys panel.
    protected MoreKeysPanel onCreateMoreKeysPanel(Key parentKey) {
        if (parentKey.mMoreKeys == null)
            return null;

        final View container = LayoutInflater.from(getContext()).inflate(mMoreKeysLayout, null);
        if (container == null)
            throw new NullPointerException();

        final MiniKeyboardView miniKeyboardView =
                (MiniKeyboardView)container.findViewById(R.id.mini_keyboard_view);
        final Keyboard parentKeyboard = getKeyboard();
        final Keyboard miniKeyboard = new MiniKeyboard.Builder(
                this, parentKeyboard.mMoreKeysTemplate, parentKey, parentKeyboard).build();
        miniKeyboardView.setKeyboard(miniKeyboard);
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        return miniKeyboardView;
    }

    public void setSpacebarTextFadeFactor(float fadeFactor, LatinKeyboard oldKeyboard) {
        final Keyboard keyboard = getKeyboard();
        // We should not set text fade factor to the keyboard which does not display the language on
        // its spacebar.
        if (keyboard instanceof LatinKeyboard && keyboard == oldKeyboard) {
            ((LatinKeyboard)keyboard).setSpacebarTextFadeFactor(fadeFactor, this);
        }
    }

    /**
     * Called when a key is long pressed. By default this will open mini keyboard associated
     * with this key.
     * @param parentKey the key that was long pressed
     * @param tracker the pointer tracker which pressed the parent key
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    protected boolean onLongPress(Key parentKey, PointerTracker tracker) {
        final int primaryCode = parentKey.mCode;
        final Keyboard keyboard = getKeyboard();
        if (keyboard instanceof LatinKeyboard) {
            final LatinKeyboard latinKeyboard = (LatinKeyboard) keyboard;
            if (primaryCode == Keyboard.CODE_DIGIT0 && latinKeyboard.isPhoneKeyboard()) {
                tracker.onLongPressed();
                // Long pressing on 0 in phone number keypad gives you a '+'.
                return invokeOnKey(Keyboard.CODE_PLUS);
            }
            if (primaryCode == Keyboard.CODE_SHIFT && latinKeyboard.isAlphaKeyboard()) {
                tracker.onLongPressed();
                return invokeOnKey(Keyboard.CODE_CAPSLOCK);
            }
        }
        if (primaryCode == Keyboard.CODE_SETTINGS || primaryCode == Keyboard.CODE_SPACE) {
            // Both long pressing settings key and space key invoke IME switcher dialog.
            if (getKeyboardActionListener().onCustomRequest(
                    LatinIME.CODE_SHOW_INPUT_METHOD_PICKER)) {
                tracker.onLongPressed();
                return true;
            } else {
                return openMoreKeysPanel(parentKey, tracker);
            }
        } else {
            return openMoreKeysPanel(parentKey, tracker);
        }
    }

    private boolean invokeOnKey(int primaryCode) {
        getKeyboardActionListener().onCodeInput(primaryCode, null,
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE,
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE);
        return true;
    }

    private boolean openMoreKeysPanel(Key parentKey, PointerTracker tracker) {
        MoreKeysPanel moreKeysPanel = mMoreKeysPanelCache.get(parentKey);
        if (moreKeysPanel == null) {
            moreKeysPanel = onCreateMoreKeysPanel(parentKey);
            if (moreKeysPanel == null)
                return false;
            mMoreKeysPanelCache.put(parentKey, moreKeysPanel);
        }
        if (mMoreKeysWindow == null) {
            mMoreKeysWindow = new PopupWindow(getContext());
            mMoreKeysWindow.setBackgroundDrawable(null);
            mMoreKeysWindow.setAnimationStyle(R.style.MiniKeyboardAnimation);
        }
        mMoreKeysPanel = moreKeysPanel;
        mMoreKeysPanelPointerTrackerId = tracker.mPointerId;

        final Keyboard keyboard = getKeyboard();
        moreKeysPanel.setShifted(keyboard.isShiftedOrShiftLocked());
        final int pointX = (mConfigShowMiniKeyboardAtTouchedPoint) ? tracker.getLastX()
                : parentKey.mX + parentKey.mWidth / 2;
        final int pointY = parentKey.mY - keyboard.mVerticalGap;
        moreKeysPanel.showMoreKeysPanel(
                this, this, pointX, pointY, mMoreKeysWindow, getKeyboardActionListener());
        final int translatedX = moreKeysPanel.translateX(tracker.getLastX());
        final int translatedY = moreKeysPanel.translateY(tracker.getLastY());
        tracker.onShowMoreKeysPanel(
                translatedX, translatedY, SystemClock.uptimeMillis(), moreKeysPanel);
        dimEntireKeyboard(true);
        return true;
    }

    private PointerTracker getPointerTracker(final int id) {
        return PointerTracker.getPointerTracker(id, this);
    }

    public boolean isInSlidingKeyInput() {
        if (mMoreKeysPanel != null) {
            return true;
        } else {
            return PointerTracker.isAnyInSlidingKeyInput();
        }
    }

    public int getPointerCount() {
        return mOldPointerCount;
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        if (getKeyboard() == null) {
            return false;
        }
        return mTouchScreenRegulator.onTouchEvent(me);
    }

    @Override
    public boolean processMotionEvent(MotionEvent me) {
        final boolean nonDistinctMultitouch = !mHasDistinctMultitouch;
        final int action = me.getActionMasked();
        final int pointerCount = me.getPointerCount();
        final int oldPointerCount = mOldPointerCount;
        mOldPointerCount = pointerCount;

        // TODO: cleanup this code into a multi-touch to single-touch event converter class?
        // If the device does not have distinct multi-touch support panel, ignore all multi-touch
        // events except a transition from/to single-touch.
        if (nonDistinctMultitouch && pointerCount > 1 && oldPointerCount > 1) {
            return true;
        }

        // Gesture detector must be enabled only when mini-keyboard is not on the screen.
        if (mMoreKeysPanel == null && mGestureDetector != null
                && mGestureDetector.onTouchEvent(me)) {
            PointerTracker.dismissAllKeyPreviews();
            mKeyTimerHandler.cancelKeyTimers();
            return true;
        }

        final long eventTime = me.getEventTime();
        final int index = me.getActionIndex();
        final int id = me.getPointerId(index);
        final int x, y;
        if (mMoreKeysPanel != null && id == mMoreKeysPanelPointerTrackerId) {
            x = mMoreKeysPanel.translateX((int)me.getX(index));
            y = mMoreKeysPanel.translateY((int)me.getY(index));
        } else {
            x = (int)me.getX(index);
            y = (int)me.getY(index);
        }

        if (mKeyTimerHandler.isInKeyRepeat()) {
            final PointerTracker tracker = getPointerTracker(id);
            // Key repeating timer will be canceled if 2 or more keys are in action, and current
            // event (UP or DOWN) is non-modifier key.
            if (pointerCount > 1 && !tracker.isModifier()) {
                mKeyTimerHandler.cancelKeyRepeatTimer();
            }
            // Up event will pass through.
        }

        // TODO: cleanup this code into a multi-touch to single-touch event converter class?
        // Translate mutli-touch event to single-touch events on the device that has no distinct
        // multi-touch panel.
        if (nonDistinctMultitouch) {
            // Use only main (id=0) pointer tracker.
            PointerTracker tracker = getPointerTracker(0);
            if (pointerCount == 1 && oldPointerCount == 2) {
                // Multi-touch to single touch transition.
                // Send a down event for the latest pointer if the key is different from the
                // previous key.
                final int newKeyIndex = tracker.getKeyIndexOn(x, y);
                if (mOldKeyIndex != newKeyIndex) {
                    tracker.onDownEvent(x, y, eventTime, this);
                    if (action == MotionEvent.ACTION_UP)
                        tracker.onUpEvent(x, y, eventTime);
                }
            } else if (pointerCount == 2 && oldPointerCount == 1) {
                // Single-touch to multi-touch transition.
                // Send an up event for the last pointer.
                final int lastX = tracker.getLastX();
                final int lastY = tracker.getLastY();
                mOldKeyIndex = tracker.getKeyIndexOn(lastX, lastY);
                tracker.onUpEvent(lastX, lastY, eventTime);
            } else if (pointerCount == 1 && oldPointerCount == 1) {
                tracker.processMotionEvent(action, x, y, eventTime, this);
            } else {
                Log.w(TAG, "Unknown touch panel behavior: pointer count is " + pointerCount
                        + " (old " + oldPointerCount + ")");
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < pointerCount; i++) {
                final PointerTracker tracker = getPointerTracker(me.getPointerId(i));
                final int px, py;
                if (mMoreKeysPanel != null
                        && tracker.mPointerId == mMoreKeysPanelPointerTrackerId) {
                    px = mMoreKeysPanel.translateX((int)me.getX(i));
                    py = mMoreKeysPanel.translateY((int)me.getY(i));
                } else {
                    px = (int)me.getX(i);
                    py = (int)me.getY(i);
                }
                tracker.onMoveEvent(px, py, eventTime);
            }
        } else {
            getPointerTracker(id).processMotionEvent(action, x, y, eventTime, this);
        }

        return true;
    }

    @Override
    public void closing() {
        super.closing();
        dismissMoreKeysPanel();
        mMoreKeysPanelCache.clear();
    }

    @Override
    public boolean dismissMoreKeysPanel() {
        if (mMoreKeysWindow != null && mMoreKeysWindow.isShowing()) {
            mMoreKeysWindow.dismiss();
            mMoreKeysPanel = null;
            mMoreKeysPanelPointerTrackerId = -1;
            dimEntireKeyboard(false);
            return true;
        }
        return false;
    }

    public boolean handleBack() {
        return dismissMoreKeysPanel();
    }

    @Override
    public void draw(Canvas c) {
        Utils.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < Utils.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                super.draw(c);
                tryGC = false;
            } catch (OutOfMemoryError e) {
                tryGC = Utils.GCUtils.getInstance().tryGCOrWait("LatinKeyboardView", e);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        // Token is available from here.
        VoiceProxy.getInstance().onAttachedToWindow();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Drop non-hover touch events when touch exploration is enabled.
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            return false;
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            final PointerTracker tracker = getPointerTracker(0);
            return AccessibleKeyboardViewProxy.getInstance().dispatchPopulateAccessibilityEvent(
                    event, tracker) || super.dispatchPopulateAccessibilityEvent(event);
        }

        return super.dispatchPopulateAccessibilityEvent(event);
    }

    /**
     * Receives hover events from the input framework. This method overrides
     * View.dispatchHoverEvent(MotionEvent) on SDK version ICS or higher. On
     * lower SDK versions, this method is never called.
     *
     * @param event The motion event to be dispatched.
     * @return {@code true} if the event was handled by the view, {@code false}
     *         otherwise
     */
    public boolean dispatchHoverEvent(MotionEvent event) {
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            final PointerTracker tracker = getPointerTracker(0);
            return AccessibleKeyboardViewProxy.getInstance().dispatchHoverEvent(event, tracker);
        }

        // Reflection doesn't support calling superclass methods.
        return false;
    }
}
