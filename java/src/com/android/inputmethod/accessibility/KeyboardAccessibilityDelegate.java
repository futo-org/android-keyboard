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
import android.os.SystemClock;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
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

/**
 * This class represents a delegate that can be registered in a class that extends
 * {@link KeyboardView} to enhance accessibility support via composition rather via inheritance.
 *
 * To implement accessibility mode, the target keyboard view has to:<p>
 * - Call {@link #setKeyboard(Keyboard)} when a new keyboard is set to the keyboard view.
 * - Dispatch a hover event by calling {@link #onHoverEnter(MotionEvent)}.
 *
 * @param <KV> The keyboard view class type.
 */
public class KeyboardAccessibilityDelegate<KV extends KeyboardView>
        extends AccessibilityDelegateCompat {
    private static final String TAG = KeyboardAccessibilityDelegate.class.getSimpleName();
    protected static final boolean DEBUG_HOVER = false;

    protected final KV mKeyboardView;
    protected final KeyDetector mKeyDetector;
    private Keyboard mKeyboard;
    private KeyboardAccessibilityNodeProvider<KV> mAccessibilityNodeProvider;
    private Key mLastHoverKey;

    public static final int HOVER_EVENT_POINTER_ID = 0;

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
    public KeyboardAccessibilityNodeProvider<KV> getAccessibilityNodeProvider(final View host) {
        return getAccessibilityNodeProvider();
    }

    /**
     * @return A lazily-instantiated node provider for this view delegate.
     */
    protected KeyboardAccessibilityNodeProvider<KV> getAccessibilityNodeProvider() {
        // Instantiate the provide only when requested. Since the system
        // will call this method multiple times it is a good practice to
        // cache the provider instance.
        if (mAccessibilityNodeProvider == null) {
            mAccessibilityNodeProvider =
                    new KeyboardAccessibilityNodeProvider<>(mKeyboardView, this);
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
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverEnter: key=" + key);
        }
        if (key != null) {
            onHoverEnterTo(key);
        }
        setLastHoverKey(key);
    }

    /**
     * Process {@link MotionEvent#ACTION_HOVER_MOVE} event.
     *
     * @param event A hover move event.
     */
    protected void onHoverMove(final MotionEvent event) {
        final Key lastKey = getLastHoverKey();
        final Key key = getHoverKeyOf(event);
        if (key != lastKey) {
            if (lastKey != null) {
                onHoverExitFrom(lastKey);
            }
            if (key != null) {
                onHoverEnterTo(key);
            }
        }
        if (key != null) {
            onHoverMoveWithin(key);
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
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverExit: key=" + getHoverKeyOf(event) + " last=" + lastKey);
        }
        if (lastKey != null) {
            onHoverExitFrom(lastKey);
        }
        final Key key = getHoverKeyOf(event);
        // Make sure we're not getting an EXIT event because the user slid
        // off the keyboard area, then force a key press.
        if (key != null) {
            performClickOn(key);
            onHoverExitFrom(key);
        }
        setLastHoverKey(null);
    }

    /**
     * Perform click on a key.
     *
     * @param key A key to be registered.
     */
    public void performClickOn(final Key key) {
        if (DEBUG_HOVER) {
            Log.d(TAG, "performClickOn: key=" + key);
        }
        simulateTouchEvent(MotionEvent.ACTION_DOWN, key);
        simulateTouchEvent(MotionEvent.ACTION_UP, key);
    }

    /**
     * Simulating a touch event by injecting a synthesized touch event into {@link KeyboardView}.
     *
     * @param touchAction The action of the synthesizing touch event.
     * @param key The key that a synthesized touch event is on.
     */
    private void simulateTouchEvent(final int touchAction, final Key key) {
        final int x = key.getHitBox().centerX();
        final int y = key.getHitBox().centerY();
        final long eventTime = SystemClock.uptimeMillis();
        final MotionEvent touchEvent = MotionEvent.obtain(
                eventTime, eventTime, touchAction, x, y, 0 /* metaState */);
        mKeyboardView.onTouchEvent(touchEvent);
        touchEvent.recycle();
    }

    /**
     * Handles a hover enter event on a key.
     *
     * @param key The currently hovered key.
     */
    protected void onHoverEnterTo(final Key key) {
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverEnterTo: key=" + key);
        }
        key.onPressed();
        mKeyboardView.invalidateKey(key);
        final KeyboardAccessibilityNodeProvider<KV> provider = getAccessibilityNodeProvider();
        provider.onHoverEnterTo(key);
        provider.performActionForKey(key, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
    }

    /**
     * Handles a hover move event on a key.
     *
     * @param key The currently hovered key.
     */
    protected void onHoverMoveWithin(final Key key) { }

    /**
     * Handles a hover exit event on a key.
     *
     * @param key The currently hovered key.
     */
    protected void onHoverExitFrom(final Key key) {
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverExitFrom: key=" + key);
        }
        key.onReleased();
        mKeyboardView.invalidateKey(key);
        final KeyboardAccessibilityNodeProvider<KV> provider = getAccessibilityNodeProvider();
        provider.onHoverExitFrom(key);
    }

    /**
     * Perform long click on a key.
     *
     * @param key A key to be long pressed on.
     */
    public void performLongClickOn(final Key key) {
        // A extended class should override this method to implement long press.
    }
}
