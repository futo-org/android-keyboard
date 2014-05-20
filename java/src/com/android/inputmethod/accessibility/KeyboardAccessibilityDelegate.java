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

import android.os.SystemClock;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardView;

public class KeyboardAccessibilityDelegate<KV extends KeyboardView>
        extends AccessibilityDelegateCompat {
    protected final KV mKeyboardView;
    protected final KeyDetector mKeyDetector;
    private Keyboard mKeyboard;
    private KeyboardAccessibilityNodeProvider mAccessibilityNodeProvider;
    private Key mLastHoverKey;

    public KeyboardAccessibilityDelegate(final KV keyboardView, final KeyDetector keyDetector) {
        super();
        mKeyboardView = keyboardView;
        mKeyDetector = keyDetector;

        // Ensure that the view has an accessibility delegate.
        ViewCompat.setAccessibilityDelegate(keyboardView, this);
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
        mKeyboard = keyboard;
    }

    protected Keyboard getKeyboard() {
        return mKeyboard;
    }

    /**
     * Sends a window state change event with the specified text.
     *
     * @param text The text to send with the event.
     */
    protected void sendWindowStateChanged(final String text) {
        final AccessibilityEvent stateChange = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        mKeyboardView.onInitializeAccessibilityEvent(stateChange);
        stateChange.getText().add(text);
        stateChange.setContentDescription(null);

        final ViewParent parent = mKeyboardView.getParent();
        if (parent != null) {
            parent.requestSendAccessibilityEvent(mKeyboardView, stateChange);
        }
    }

    /**
     * Delegate method for View.getAccessibilityNodeProvider(). This method is called in SDK
     * version 15 (Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) and higher to obtain the virtual
     * node hierarchy provider.
     *
     * @param host The host view for the provider.
     * @return The accessibility node provider for the current keyboard.
     */
    @Override
    public KeyboardAccessibilityNodeProvider getAccessibilityNodeProvider(final View host) {
        return getAccessibilityNodeProvider();
    }

    /**
     * @return A lazily-instantiated node provider for this view delegate.
     */
    protected KeyboardAccessibilityNodeProvider getAccessibilityNodeProvider() {
        // Instantiate the provide only when requested. Since the system
        // will call this method multiple times it is a good practice to
        // cache the provider instance.
        if (mAccessibilityNodeProvider == null) {
            mAccessibilityNodeProvider = new KeyboardAccessibilityNodeProvider(mKeyboardView);
        }
        return mAccessibilityNodeProvider;
    }

    /**
     * Receives hover events when touch exploration is turned on in SDK versions ICS and higher.
     *
     * @param event The hover event.
     * @return {@code true} if the event is handled
     */
    public boolean dispatchHoverEvent(final MotionEvent event) {
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        final Key previousKey = mLastHoverKey;
        final Key key = mKeyDetector.detectHitKey(x, y);
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
     * Simulates a key press by injecting touch an event into the keyboard view.
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
        mKeyboardView.onTouchEvent(downEvent);
        downEvent.recycle();
        return downTime;
    }

    /**
     * Simulates a key release by injecting touch an event into the keyboard view.
     * This avoids the complexity of trackers and listeners within the keyboard.
     *
     * @param key The key to release.
     */
    private void simulateKeyRelease(final Key key, final long downTime) {
        final int x = key.getHitBox().centerX();
        final int y = key.getHitBox().centerY();
        final MotionEvent upEvent = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        mKeyboardView.onTouchEvent(upEvent);
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
        final KeyboardAccessibilityNodeProvider provider = getAccessibilityNodeProvider();

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
}
