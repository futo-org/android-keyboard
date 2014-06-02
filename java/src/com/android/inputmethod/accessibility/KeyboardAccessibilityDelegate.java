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
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.PointerTracker;

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

    protected final Keyboard getKeyboard() {
        return mKeyboard;
    }

    protected final void setLastHoverKey(final Key key) {
        mLastHoverKey = key;
    }

    protected final Key getLastHoverKey() {
        return mLastHoverKey;
    }

    /**
     * Sends a window state change event with the specified string resource id.
     *
     * @param resId The string resource id of the text to send with the event.
     */
    protected void sendWindowStateChanged(final int resId) {
        if (resId == 0) {
            return;
        }
        final Context context = mKeyboardView.getContext();
        sendWindowStateChanged(context.getString(resId));
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
     * Get a key that a hover event is on.
     *
     * @param event The hover event.
     * @return key The key that the <code>event</code> is on.
     */
    protected final Key getHoverKeyOf(final MotionEvent event) {
        final int actionIndex = event.getActionIndex();
        final int x = (int)event.getX(actionIndex);
        final int y = (int)event.getY(actionIndex);
        return mKeyDetector.detectHitKey(x, y);
    }

    /**
     * Receives hover events when touch exploration is turned on in SDK versions ICS and higher.
     *
     * @param event The hover event.
     * @return {@code true} if the event is handled.
     */
    public boolean onHoverEvent(final MotionEvent event) {
        switch (event.getActionMasked()) {
        case MotionEvent.ACTION_HOVER_ENTER:
            onHoverEnter(event);
            break;
        case MotionEvent.ACTION_HOVER_MOVE:
            onHoverMove(event);
            break;
        case MotionEvent.ACTION_HOVER_EXIT:
            onHoverExit(event);
            break;
        default:
            Log.w(getClass().getSimpleName(), "Unknown hover event: " + event);
            break;
        }
        return true;
    }

    /**
     * Process {@link MotionEvent#ACTION_HOVER_ENTER} event.
     *
     * @param event A hover enter event.
     */
    protected void onHoverEnter(final MotionEvent event) {
        final Key key = getHoverKeyOf(event);
        if (key != null) {
            onHoverEnterKey(key);
        }
        setLastHoverKey(key);
    }

    /**
     * Process {@link MotionEvent#ACTION_HOVER_MOVE} event.
     *
     * @param event A hover move event.
     */
    protected void onHoverMove(final MotionEvent event) {
        final Key previousKey = getLastHoverKey();
        final Key key = getHoverKeyOf(event);
        if (key != previousKey) {
            if (previousKey != null) {
                onHoverExitKey(previousKey);
            }
            if (key != null) {
                onHoverEnterKey(key);
            }
        }
        if (key != null) {
            onHoverMoveKey(key);
        }
        setLastHoverKey(key);
    }

    /**
     * Process {@link MotionEvent#ACTION_HOVER_EXIT} event.
     *
     * @param event A hover exit event.
     */
    protected void onHoverExit(final MotionEvent event) {
        final Key lastKey = getLastHoverKey();
        if (lastKey != null) {
            onHoverExitKey(lastKey);
        }
        final Key key = getHoverKeyOf(event);
        // Make sure we're not getting an EXIT event because the user slid
        // off the keyboard area, then force a key press.
        if (key != null) {
            simulateTouchEvent(MotionEvent.ACTION_DOWN, event);
            simulateTouchEvent(MotionEvent.ACTION_UP, event);
            onHoverExitKey(key);
        }
        setLastHoverKey(null);
    }

    /**
     * Simulating a touch event by injecting a synthesized touch event into {@link PointerTracker}.
     *
     * @param touchAction The action of the synthesizing touch event.
     * @param hoverEvent The base hover event from that the touch event is synthesized.
     */
    protected void simulateTouchEvent(final int touchAction, final MotionEvent hoverEvent) {
        final MotionEvent touchEvent = synthesizeTouchEvent(touchAction, hoverEvent);
        final int actionIndex = touchEvent.getActionIndex();
        final int pointerId = touchEvent.getPointerId(actionIndex);
        final PointerTracker tracker = PointerTracker.getPointerTracker(pointerId);
        tracker.processMotionEvent(touchEvent, mKeyDetector);
        touchEvent.recycle();
    }

    /**
     * Synthesize a touch event from a hover event.
     *
     * @param touchAction The action of the synthesizing touch event.
     * @param event The base hover event from that the touch event is synthesized.
     * @return The synthesized touch event of <code>touchAction</code> that has pointer information
     * of <code>event</code>.
     */
    protected static MotionEvent synthesizeTouchEvent(final int touchAction,
            final MotionEvent event) {
        final long downTime = event.getDownTime();
        final long eventTime = event.getEventTime();
        final int actionIndex = event.getActionIndex();
        final float x = event.getX(actionIndex);
        final float y = event.getY(actionIndex);
        final int pointerId = event.getPointerId(actionIndex);
        return MotionEvent.obtain(downTime, eventTime, touchAction, x, y, pointerId);
    }

    /**
     * Handles a hover enter event on a key.
     *
     * @param key The currently hovered key.
     */
    protected void onHoverEnterKey(final Key key) {
        key.onPressed();
        mKeyboardView.invalidateKey(key);
        final KeyboardAccessibilityNodeProvider provider = getAccessibilityNodeProvider();
        provider.sendAccessibilityEventForKey(key, AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER);
        provider.performActionForKey(key, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
    }

    /**
     * Handles a hover move event on a key.
     *
     * @param key The currently hovered key.
     */
    protected void onHoverMoveKey(final Key key) { }

    /**
     * Handles a hover exit event on a key.
     *
     * @param key The currently hovered key.
     */
    protected void onHoverExitKey(final Key key) {
        key.onReleased();
        mKeyboardView.invalidateKey(key);
        final KeyboardAccessibilityNodeProvider provider = getAccessibilityNodeProvider();
        provider.sendAccessibilityEventForKey(key, AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT);
    }
}
