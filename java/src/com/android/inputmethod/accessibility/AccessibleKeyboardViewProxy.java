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

package com.android.inputmethod.accessibility;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.latin.R;

public final class AccessibleKeyboardViewProxy extends AccessibilityDelegateCompat {
    private static final AccessibleKeyboardViewProxy sInstance = new AccessibleKeyboardViewProxy();

    /** Map of keyboard modes to resource IDs. */
    private static final SparseIntArray KEYBOARD_MODE_RES_IDS = new SparseIntArray();

    static {
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_DATE, R.string.keyboard_mode_date);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_DATETIME, R.string.keyboard_mode_date_time);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_EMAIL, R.string.keyboard_mode_email);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_IM, R.string.keyboard_mode_im);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_NUMBER, R.string.keyboard_mode_number);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_PHONE, R.string.keyboard_mode_phone);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_TEXT, R.string.keyboard_mode_text);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_TIME, R.string.keyboard_mode_time);
        KEYBOARD_MODE_RES_IDS.put(KeyboardId.MODE_URL, R.string.keyboard_mode_url);
    }

    private InputMethodService mInputMethod;
    private MainKeyboardView mView;
    private AccessibilityEntityProvider mAccessibilityNodeProvider;

    private Key mLastHoverKey = null;

    /**
     * Inset in pixels to look for keys when the user's finger exits the keyboard area.
     */
    private int mEdgeSlop;

    /** The most recently set keyboard mode. */
    private int mLastKeyboardMode;

    public static void init(final InputMethodService inputMethod) {
        sInstance.initInternal(inputMethod);
    }

    public static AccessibleKeyboardViewProxy getInstance() {
        return sInstance;
    }

    private AccessibleKeyboardViewProxy() {
        // Not publicly instantiable.
    }

    private void initInternal(final InputMethodService inputMethod) {
        mInputMethod = inputMethod;
        mEdgeSlop = inputMethod.getResources().getDimensionPixelSize(
                R.dimen.accessibility_edge_slop);
    }

    /**
     * Sets the view wrapped by this proxy.
     *
     * @param view The view to wrap.
     */
    public void setView(final MainKeyboardView view) {
        if (view == null) {
            // Ignore null views.
            return;
        }
        mView = view;

        // Ensure that the view has an accessibility delegate.
        ViewCompat.setAccessibilityDelegate(view, this);

        if (mAccessibilityNodeProvider == null) {
            return;
        }
        mAccessibilityNodeProvider.setView(view);
    }

    /**
     * Called when the keyboard layout changes.
     * <p>
     * <b>Note:</b> This method will be called even if accessibility is not
     * enabled.
     */
    public void setKeyboard() {
        if (mView == null) {
            return;
        }
        if (mAccessibilityNodeProvider != null) {
            mAccessibilityNodeProvider.setKeyboard();
        }
        final int keyboardMode = mView.getKeyboard().mId.mMode;

        // Since this method is called even when accessibility is off, make sure
        // to check the state before announcing anything. Also, don't announce
        // changes within the same mode.
        if (AccessibilityUtils.getInstance().isAccessibilityEnabled()
                && (mLastKeyboardMode != keyboardMode)) {
            announceKeyboardMode(keyboardMode);
        }
        mLastKeyboardMode = keyboardMode;
    }

    /**
     * Called when the keyboard is hidden and accessibility is enabled.
     */
    public void onHideWindow() {
        if (mView == null) {
            return;
        }
        announceKeyboardHidden();
        mLastKeyboardMode = -1;
    }

    /**
     * Announces which type of keyboard is being displayed. If the keyboard type
     * is unknown, no announcement is made.
     *
     * @param mode The new keyboard mode.
     */
    private void announceKeyboardMode(int mode) {
        final int resId = KEYBOARD_MODE_RES_IDS.get(mode);
        if (resId == 0) {
            return;
        }
        final Context context = mView.getContext();
        final String keyboardMode = context.getString(resId);
        final String text = context.getString(R.string.announce_keyboard_mode, keyboardMode);
        sendWindowStateChanged(text);
    }

    /**
     * Announces that the keyboard has been hidden.
     */
    private void announceKeyboardHidden() {
        final Context context = mView.getContext();
        final String text = context.getString(R.string.announce_keyboard_hidden);

        sendWindowStateChanged(text);
    }

    /**
     * Sends a window state change event with the specified text.
     *
     * @param text The text to send with the event.
     */
    private void sendWindowStateChanged(final String text) {
        final AccessibilityEvent stateChange = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        mView.onInitializeAccessibilityEvent(stateChange);
        stateChange.getText().add(text);
        stateChange.setContentDescription(null);

        final ViewParent parent = mView.getParent();
        if (parent != null) {
            parent.requestSendAccessibilityEvent(mView, stateChange);
        }
    }

    /**
     * Proxy method for View.getAccessibilityNodeProvider(). This method is called in SDK
     * version 15 (Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) and higher to obtain the virtual
     * node hierarchy provider.
     *
     * @param host The host view for the provider.
     * @return The accessibility node provider for the current keyboard.
     */
    @Override
    public AccessibilityEntityProvider getAccessibilityNodeProvider(final View host) {
        if (mView == null) {
            return null;
        }
        return getAccessibilityNodeProvider();
    }

    /**
     * Intercepts touch events before dispatch when touch exploration is turned on in ICS and
     * higher.
     *
     * @param event The motion event being dispatched.
     * @return {@code true} if the event is handled
     */
    public boolean dispatchTouchEvent(final MotionEvent event) {
        // To avoid accidental key presses during touch exploration, always drop
        // touch events generated by the user.
        return false;
    }

    /**
     * Receives hover events when touch exploration is turned on in SDK versions ICS and higher.
     *
     * @param event The hover event.
     * @return {@code true} if the event is handled
     */
    public boolean dispatchHoverEvent(final MotionEvent event, final PointerTracker tracker) {
        if (mView == null) {
            return false;
        }

        final int x = (int) event.getX();
        final int y = (int) event.getY();
        final Key previousKey = mLastHoverKey;
        final Key key;

        if (pointInView(x, y)) {
            key = tracker.getKeyOn(x, y);
        } else {
            key = null;
        }
        mLastHoverKey = key;

        switch (event.getAction()) {
        case MotionEvent.ACTION_HOVER_EXIT:
            // Make sure we're not getting an EXIT event because the user slid
            // off the keyboard area, then force a key press.
            if (key != null) {
                getAccessibilityNodeProvider().simulateKeyPress(key);
            }
            //$FALL-THROUGH$
        case MotionEvent.ACTION_HOVER_ENTER:
            return onHoverKey(key, event);
        case MotionEvent.ACTION_HOVER_MOVE:
            if (key != previousKey) {
                return onTransitionKey(key, previousKey, event);
            }
            return onHoverKey(key, event);
        }
        return false;
    }

    /**
     * @return A lazily-instantiated node provider for this view proxy.
     */
    private AccessibilityEntityProvider getAccessibilityNodeProvider() {
        // Instantiate the provide only when requested. Since the system
        // will call this method multiple times it is a good practice to
        // cache the provider instance.
        if (mAccessibilityNodeProvider == null) {
            mAccessibilityNodeProvider = new AccessibilityEntityProvider(mView, mInputMethod);
        }
        return mAccessibilityNodeProvider;
    }

    /**
     * Utility method to determine whether the given point, in local coordinates, is inside the
     * view, where the area of the view is contracted by the edge slop factor.
     *
     * @param localX The local x-coordinate.
     * @param localY The local y-coordinate.
     */
    private boolean pointInView(final int localX, final int localY) {
        return (localX >= mEdgeSlop) && (localY >= mEdgeSlop)
                && (localX < (mView.getWidth() - mEdgeSlop))
                && (localY < (mView.getHeight() - mEdgeSlop));
    }

    /**
     * Simulates a transition between two {@link Key}s by sending a HOVER_EXIT on the previous key,
     * a HOVER_ENTER on the current key, and a HOVER_MOVE on the current key.
     *
     * @param currentKey The currently hovered key.
     * @param previousKey The previously hovered key.
     * @param event The event that triggered the transition.
     * @return {@code true} if the event was handled.
     */
    private boolean onTransitionKey(final Key currentKey, final Key previousKey,
            final MotionEvent event) {
        final int savedAction = event.getAction();
        event.setAction(MotionEvent.ACTION_HOVER_EXIT);
        onHoverKey(previousKey, event);
        event.setAction(MotionEvent.ACTION_HOVER_ENTER);
        onHoverKey(currentKey, event);
        event.setAction(MotionEvent.ACTION_HOVER_MOVE);
        final boolean handled = onHoverKey(currentKey, event);
        event.setAction(savedAction);
        return handled;
    }

    /**
     * Handles a hover event on a key. If {@link Key} extended View, this would be analogous to
     * calling View.onHoverEvent(MotionEvent).
     *
     * @param key The currently hovered key.
     * @param event The hover event.
     * @return {@code true} if the event was handled.
     */
    private boolean onHoverKey(final Key key, final MotionEvent event) {
        // Null keys can't receive events.
        if (key == null) {
            return false;
        }
        final AccessibilityEntityProvider provider = getAccessibilityNodeProvider();

        switch (event.getAction()) {
        case MotionEvent.ACTION_HOVER_ENTER:
            provider.sendAccessibilityEventForKey(
                    key, AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER);
            provider.performActionForKey(
                    key, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, null);
            break;
        case MotionEvent.ACTION_HOVER_EXIT:
            provider.sendAccessibilityEventForKey(
                    key, AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT);
            break;
        }
        return true;
    }

    /**
     * Notifies the user of changes in the keyboard shift state.
     */
    public void notifyShiftState() {
        if (mView == null) {
            return;
        }

        final Keyboard keyboard = mView.getKeyboard();
        final KeyboardId keyboardId = keyboard.mId;
        final int elementId = keyboardId.mElementId;
        final Context context = mView.getContext();
        final CharSequence text;

        switch (elementId) {
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
            text = context.getText(R.string.spoken_description_shiftmode_locked);
            break;
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
        case KeyboardId.ELEMENT_SYMBOLS_SHIFTED:
            text = context.getText(R.string.spoken_description_shiftmode_on);
            break;
        default:
            text = context.getText(R.string.spoken_description_shiftmode_off);
        }
        AccessibilityUtils.getInstance().announceForAccessibility(mView, text);
    }

    /**
     * Notifies the user of changes in the keyboard symbols state.
     */
    public void notifySymbolsState() {
        if (mView == null) {
            return;
        }

        final Keyboard keyboard = mView.getKeyboard();
        final Context context = mView.getContext();
        final KeyboardId keyboardId = keyboard.mId;
        final int elementId = keyboardId.mElementId;
        final int resId;

        switch (elementId) {
        case KeyboardId.ELEMENT_ALPHABET:
        case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED:
        case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED:
            resId = R.string.spoken_description_mode_alpha;
            break;
        case KeyboardId.ELEMENT_SYMBOLS:
        case KeyboardId.ELEMENT_SYMBOLS_SHIFTED:
            resId = R.string.spoken_description_mode_symbol;
            break;
        case KeyboardId.ELEMENT_PHONE:
            resId = R.string.spoken_description_mode_phone;
            break;
        case KeyboardId.ELEMENT_PHONE_SYMBOLS:
            resId = R.string.spoken_description_mode_phone_shift;
            break;
        default:
            resId = -1;
        }

        if (resId < 0) {
            return;
        }
        final String text = context.getString(resId);
        AccessibilityUtils.getInstance().announceForAccessibility(mView, text);
    }
}
