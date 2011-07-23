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
import android.content.res.TypedArray;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.PopupWindow;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.keyboard.PointerTracker.DrawingProxy;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;
import com.android.inputmethod.keyboard.internal.MiniKeyboardBuilder;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

import java.util.WeakHashMap;

/**
 * A view that is responsible for detecting key presses and touch movements.
 *
 * @attr ref R.styleable#KeyboardView_keyHysteresisDistance
 * @attr ref R.styleable#KeyboardView_verticalCorrection
 * @attr ref R.styleable#KeyboardView_popupLayout
 */
public class LatinKeyboardBaseView extends KeyboardView implements PointerTracker.KeyEventHandler {
    private static final String TAG = LatinKeyboardBaseView.class.getSimpleName();

    private static final boolean ENABLE_CAPSLOCK_BY_DOUBLETAP = true;

    // Timing constants
    private final int mKeyRepeatInterval;

    // XML attribute
    private final float mVerticalCorrection;
    private final int mPopupLayout;

    // Mini keyboard
    private PopupWindow mPopupWindow;
    private PopupPanel mPopupPanel;
    private int mPopupPanelPointerTrackerId;
    private final WeakHashMap<Key, PopupPanel> mPopupPanelCache =
            new WeakHashMap<Key, PopupPanel>();

    /** Listener for {@link KeyboardActionListener}. */
    private KeyboardActionListener mKeyboardActionListener;

    private final boolean mHasDistinctMultitouch;
    private int mOldPointerCount = 1;
    private int mOldKeyIndex;

    protected KeyDetector mKeyDetector;

    // To detect double tap.
    protected GestureDetector mGestureDetector;

    private final KeyTimerHandler mKeyTimerHandler = new KeyTimerHandler(this);

    private static class KeyTimerHandler extends StaticInnerHandlerWrapper<LatinKeyboardBaseView>
            implements TimerProxy {
        private static final int MSG_REPEAT_KEY = 1;
        private static final int MSG_LONGPRESS_KEY = 2;
        private static final int MSG_IGNORE_DOUBLE_TAP = 3;

        private boolean mInKeyRepeat;

        public KeyTimerHandler(LatinKeyboardBaseView outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            final LatinKeyboardBaseView keyboardView = getOuterInstance();
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
                    final boolean ignoringDoubleTap = mKeyTimerHandler.isIgnoringDoubleTap();
                    if (!ignoringDoubleTap)
                        onDoubleTapShiftKey(tracker);
                    return true;
                }
                // Otherwise these events should not be handled as double tap.
                mProcessingShiftDoubleTapEvent = false;
            }
            return mProcessingShiftDoubleTapEvent;
        }
    }

    public LatinKeyboardBaseView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public LatinKeyboardBaseView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        mVerticalCorrection = a.getDimensionPixelOffset(
                R.styleable.KeyboardView_verticalCorrection, 0);
        mPopupLayout = a.getResourceId(R.styleable.KeyboardView_popupLayout, 0);
        a.recycle();

        final Resources res = getResources();
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
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // TODO: Should notify InputMethodService instead?
        KeyboardSwitcher.getInstance().onSizeChanged(w, h, oldw, oldh);
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
        if (getKeyboard() != null) {
            PointerTracker.dismissAllKeyPreviews();
        }
        // Remove any pending messages, except dismissing preview
        mKeyTimerHandler.cancelKeyTimers();
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft(), -getPaddingTop() + mVerticalCorrection);
        mKeyDetector.setProximityThreshold(keyboard.getMostCommonKeyWidth());
        PointerTracker.setKeyDetector(mKeyDetector);
        mPopupPanelCache.clear();
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
        if (mPopupLayout == 0) {
            return false;
        }

        // Check if we are already displaying popup panel.
        if (mPopupPanel != null)
            return false;
        final Key parentKey = tracker.getKey(keyIndex);
        if (parentKey == null)
            return false;
        return onLongPress(parentKey, tracker);
    }

    private void onDoubleTapShiftKey(@SuppressWarnings("unused") PointerTracker tracker) {
        // When shift key is double tapped, the first tap is correctly processed as usual tap. And
        // the second tap is treated as this double tap event, so that we need not mark tracker
        // calling setAlreadyProcessed() nor remove the tracker from mPointerQueue.
        mKeyboardActionListener.onCodeInput(Keyboard.CODE_CAPSLOCK, null, 0, 0);
    }

    // This default implementation returns a popup mini keyboard panel.
    protected PopupPanel onCreatePopupPanel(Key parentKey) {
        if (parentKey.mPopupCharacters == null)
            return null;

        final View container = LayoutInflater.from(getContext()).inflate(mPopupLayout, null);
        if (container == null)
            throw new NullPointerException();

        final PopupMiniKeyboardView miniKeyboardView =
                (PopupMiniKeyboardView)container.findViewById(R.id.mini_keyboard_view);
        final Keyboard parentKeyboard = getKeyboard();
        final Keyboard miniKeyboard = new MiniKeyboardBuilder(
                this, parentKeyboard.getPopupKeyboardResId(), parentKey, parentKeyboard).build();
        miniKeyboardView.setKeyboard(miniKeyboard);

        container.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

        return miniKeyboardView;
    }

    @Override
    protected boolean needsToDimKeyboard() {
        return mPopupPanel != null;
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
        PopupPanel popupPanel = mPopupPanelCache.get(parentKey);
        if (popupPanel == null) {
            popupPanel = onCreatePopupPanel(parentKey);
            if (popupPanel == null)
                return false;
            mPopupPanelCache.put(parentKey, popupPanel);
        }
        if (mPopupWindow == null) {
            mPopupWindow = new PopupWindow(getContext());
            mPopupWindow.setBackgroundDrawable(null);
            mPopupWindow.setAnimationStyle(R.style.PopupMiniKeyboardAnimation);
            // Allow popup window to be drawn off the screen.
            mPopupWindow.setClippingEnabled(false);
        }
        mPopupPanel = popupPanel;
        mPopupPanelPointerTrackerId = tracker.mPointerId;

        tracker.onLongPressed();
        popupPanel.showPanel(this, parentKey, tracker, mPopupWindow);
        final int translatedX = popupPanel.translateX(tracker.getLastX());
        final int translatedY = popupPanel.translateY(tracker.getLastY());
        tracker.onDownEvent(translatedX, translatedY, SystemClock.uptimeMillis(), popupPanel);

        invalidateAllKeys();
        return true;
    }

    private PointerTracker getPointerTracker(final int id) {
        return PointerTracker.getPointerTracker(id, this);
    }

    public boolean isInSlidingKeyInput() {
        if (mPopupPanel != null) {
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
        if (mPopupPanel == null && mGestureDetector != null
                && mGestureDetector.onTouchEvent(me)) {
            PointerTracker.dismissAllKeyPreviews();
            mKeyTimerHandler.cancelKeyTimers();
            return true;
        }

        final long eventTime = me.getEventTime();
        final int index = me.getActionIndex();
        final int id = me.getPointerId(index);
        final int x, y;
        if (mPopupPanel != null && id == mPopupPanelPointerTrackerId) {
            x = mPopupPanel.translateX((int)me.getX(index));
            y = mPopupPanel.translateY((int)me.getY(index));
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
                processMotionEvent(tracker, action, x, y, eventTime, this);
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
                if (mPopupPanel != null && tracker.mPointerId == mPopupPanelPointerTrackerId) {
                    px = mPopupPanel.translateX((int)me.getX(i));
                    py = mPopupPanel.translateY((int)me.getY(i));
                } else {
                    px = (int)me.getX(i);
                    py = (int)me.getY(i);
                }
                tracker.onMoveEvent(px, py, eventTime);
            }
        } else {
            processMotionEvent(getPointerTracker(id), action, x, y, eventTime, this);
        }

        return true;
    }

    private static void processMotionEvent(PointerTracker tracker, int action, int x, int y,
            long eventTime, PointerTracker.KeyEventHandler handler) {
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            tracker.onDownEvent(x, y, eventTime, handler);
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            tracker.onUpEvent(x, y, eventTime);
            break;
        case MotionEvent.ACTION_MOVE:
            tracker.onMoveEvent(x, y, eventTime);
            break;
        case MotionEvent.ACTION_CANCEL:
            tracker.onCancelEvent(x, y, eventTime);
            break;
        }
    }

    @Override
    public void closing() {
        super.closing();
        dismissMiniKeyboard();
        mPopupPanelCache.clear();
    }

    public boolean dismissMiniKeyboard() {
        if (mPopupWindow != null && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
            mPopupPanel = null;
            mPopupPanelPointerTrackerId = -1;
            invalidateAllKeys();
            return true;
        }
        return false;
    }

    public boolean handleBack() {
        return dismissMiniKeyboard();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            return AccessibleKeyboardViewProxy.getInstance().dispatchTouchEvent(event)
                    || super.dispatchTouchEvent(event);
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

    public boolean onHoverEvent(MotionEvent event) {
        // Since reflection doesn't support calling superclass methods, this
        // method checks for the existence of onHoverEvent() in the View class
        // before returning a value.
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            final PointerTracker tracker = getPointerTracker(0);
            return AccessibleKeyboardViewProxy.getInstance().onHoverEvent(event, tracker);
        }

        return false;
    }
}
