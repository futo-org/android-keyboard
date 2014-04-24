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
import android.os.SystemClock;
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
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

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
    private Keyboard mKeyboard;
    private AccessibilityEntityProvider mAccessibilityNodeProvider;

    private Key mLastHoverKey = null;

    /**
     * Inset in pixels to look for keys when the user's finger exits the keyboard area.
     */
    private int mEdgeSlop;

    /** The most recently set keyboard mode. */
    private int mLastKeyboardMode = KEYBOARD_IS_HIDDEN;
    private static final int KEYBOARD_IS_HIDDEN = -1;

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
                R.dimen.config_accessibility_edge_slop);
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

        // Since this class is constructed lazily, we might not get a subsequent
        // call to setKeyboard() and therefore need to call it now.
        setKeyboard(view.getKeyboard());
    }

    /**
     * Called when the keyboard layout changes.
     * <p>
     * <b>Note:</b> This method will be called even if accessibility is not
     * enabled.
     * @param keyboard The keyboard that is being set to the wrapping view.
     */
    public void setKeyboard(final Keyboard keyboard) {
        if (keyboard == null) {
            return;
        }
        if (mAccessibilityNodeProvider != null) {
            mAccessibilityNodeProvider.setKeyboard(keyboard);
        }
        final Keyboard lastKeyboard = mKeyboard;
        final int lastKeyboardMode = mLastKeyboardMode;
        mKeyboard = keyboard;
        mLastKeyboardMode = keyboard.mId.mMode;

        // Since this method is called even when accessibility is off, make sure
        // to check the state before announcing anything.
        if (!AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            return;
        }
        // Announce the language name only when the language is changed.
        if (lastKeyboard == null || !lastKeyboard.mId.mSubtype.equals(keyboard.mId.mSubtype)) {
            announceKeyboardLanguage(keyboard);
        }
        // Announce the mode only when the mode is changed.
        if (lastKeyboardMode != keyboard.mId.mMode) {
            announceKeyboardMode(keyboard);
        }
    }

    /**
     * Called when the keyboard is hidden and accessibility is enabled.
     */
    public void onHideWindow() {
        if (mView == null) {
            return;
        }
        announceKeyboardHidden();
        mLastKeyboardMode = KEYBOARD_IS_HIDDEN;
    }

    /**
     * Announces which language of keyboard is being displayed.
     *
     * @param keyboard The new keyboard.
     */
    private void announceKeyboardLanguage(final Keyboard keyboard) {
        final String languageText = SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(
                keyboard.mId.mSubtype);
        sendWindowStateChanged(languageText);
    }

    /**
     * Announces which type of keyboard is being displayed.
     * If the keyboard type is unknown, no announcement is made.
     *
     * @param keyboard The new keyboard.
     */
    private void announceKeyboardMode(final Keyboard keyboard) {
        final int mode = keyboard.mId.mMode;
        final Context context = mView.getContext();
        final int modeTextResId = KEYBOARD_MODE_RES_IDS.get(mode);
        if (modeTextResId == 0) {
            return;
        }
        final String modeText = context.getString(modeTextResId);
        final String text = context.getString(R.string.announce_keyboard_mode, modeText);
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
     * Receives hover events when touch exploration is turned on in SDK versions ICS and higher.
     *
     * @param event The hover event.
     * @param keyDetector The {@link KeyDetector} to determine on which key the <code>event</code>
     *     is hovering.
     * @return {@code true} if the event is handled
     */
    public boolean dispatchHoverEvent(final MotionEvent event, final KeyDetector keyDetector) {
        if (mView == null) {
            return false;
        }

        final int x = (int) event.getX();
        final int y = (int) event.getY();
        final Key previousKey = mLastHoverKey;
        final Key key;

        if (pointInView(x, y)) {
            key = keyDetector.detectHitKey(x, y);
        } else {
            key = null;
        }
        mLastHoverKey = key;

        switch (event.getAction()) {
        case MotionEvent.ACTION_HOVER_EXIT:
            // Make sure we're not getting an EXIT event because the user slid
            // off the keyboard area, then force a key press.
            if (key != null) {
                final long downTime = simulateKeyPress(key);
                simulateKeyRelease(key, downTime);
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
     * Simulates a key press by injecting touch event into the keyboard view.
     * This avoids the complexity of trackers and listeners within the keyboard.
     *
     * @param key The key to press.
     */
    private long simulateKeyPress(final Key key) {
        final int x = key.getHitBox().centerX();
        final int y = key.getHitBox().centerY();
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        mView.onTouchEvent(downEvent);
        downEvent.recycle();
        return downTime;
    }

    /**
     * Simulates a key release by injecting touch event into the keyboard view.
     * This avoids the complexity of trackers and listeners within the keyboard.
     *
     * @param key The key to release.
     */
    private void simulateKeyRelease(final Key key, final long downTime) {
        final int x = key.getHitBox().centerX();
        final int y = key.getHitBox().centerY();
        final MotionEvent upEvent = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        mView.onTouchEvent(upEvent);
        upEvent.recycle();
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
        if (mView == null || mKeyboard == null) {
            return;
        }

        final KeyboardId keyboardId = mKeyboard.mId;
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
        if (mView == null || mKeyboard == null) {
            return;
        }

        final KeyboardId keyboardId = mKeyboard.mId;
        final int elementId = keyboardId.mElementId;
        final Context context = mView.getContext();
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
            return;
        }

        final String text = context.getString(resId);
        AccessibilityUtils.getInstance().announceForAccessibility(mView, text);
    }
}
