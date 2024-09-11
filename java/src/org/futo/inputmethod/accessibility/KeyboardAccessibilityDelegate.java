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

package org.futo.inputmethod.accessibility;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import org.futo.inputmethod.keyboard.Key;
import org.futo.inputmethod.keyboard.KeyDetector;
import org.futo.inputmethod.keyboard.Keyboard;
import org.futo.inputmethod.keyboard.KeyboardView;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.settings.Settings;
import org.futo.inputmethod.latin.settings.SettingsValues;

import java.util.List;

/**
 * This class represents a delegate that can be registered in a class that extends
 * {@link KeyboardView} to enhance accessibility support via composition rather via inheritance.
 *
 * To implement accessibility mode, the target keyboard view has to:<p>
 * - Call {@link #setKeyboard(Keyboard)} when a new keyboard is set to the keyboard view.
 *
 * @param <KV> The keyboard view class type.
 */
public class KeyboardAccessibilityDelegate<KV extends KeyboardView>
        extends ExploreByTouchHelper {
    private static final String TAG = KeyboardAccessibilityDelegate.class.getSimpleName();
    protected static final boolean DEBUG_HOVER = false;

    protected final KV mKeyboardView;
    protected final KeyDetector mKeyDetector;
    private Keyboard mKeyboard;
    private Key mLastHoverKey;

    public static final int HOVER_EVENT_POINTER_ID = 0;

    public KeyboardAccessibilityDelegate(final KV keyboardView, final KeyDetector keyDetector) {
        super(keyboardView);
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

    @Override
    protected int getVirtualViewAt(float x, float y) {
        Key k = mKeyDetector.detectHitKey((int)x, (int)y);
        if(k == null) {
            return HOST_ID;
        }

        return getVirtualViewIdOf(k);
    }

    @Override
    protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
        final List<Key> sortedKeys = mKeyboard.getSortedKeys();
        final int size = sortedKeys.size();
        for (int index = 0; index < size; index++) {
            virtualViewIds.add(index);
        }
    }

    @Override
    protected void onPopulateNodeForVirtualView(int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
        Key k = getKeyOf(virtualViewId);
        if(k == null) {
            // TODO: For some reason, sometimes this is null and crashes.
            // Just set this to something to prevent crash
            Log.e(TAG, "Invalid virtual view id " + virtualViewId);

            node.setContentDescription("Unknown");
            node.setBoundsInParent(new Rect(0, 0, 0, 0));

            node.setFocusable(false);
            node.setScreenReaderFocusable(false);

            return;
        }

        node.setClassName(android.inputmethodservice.Keyboard.Key.class.getName());

        String description = getKeyDescription(k);

        if(description == null || description.isBlank()) {
            Log.e(TAG, "Invalid key has blank description: " + k.toString());
            description = "Unknown";
        }

        node.setContentDescription(description);
        node.setBoundsInParent(k.getHitBox());

        node.setFocusable(true);
        node.setScreenReaderFocusable(true);

        if(k.isActionKey() || k.getCode() == Constants.CODE_SWITCH_ALPHA_SYMBOL || k.getCode() == Constants.CODE_EMOJI || k.getCode() == Constants.CODE_SYMBOL_SHIFT) {
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setClickable(true);
        } else {
            node.setTextEntryKey(true);
        }
    }

    @Override
    protected boolean onPerformActionForVirtualView(int virtualViewId, int action, @Nullable Bundle arguments) {
        Key k = getKeyOf(virtualViewId);
        if(k == null) return false;

        if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
            // Handle the click action for the virtual button
            performClickOn(k);
            return true;
        }
        return false;
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
     * Perform long click on a key.
     *
     * @param key A key to be long pressed on.
     */
    public void performLongClickOn(final Key key) {
        // A extended class should override this method to implement long press.
    }


    public Key getKeyOf(final int virtualViewId) {
        if (mKeyboard == null) {
            return null;
        }
        final List<Key> sortedKeys = mKeyboard.getSortedKeys();
        // Use a virtual view id as an index of the sorted keys list.
        if (virtualViewId >= 0 && virtualViewId < sortedKeys.size()) {
            return sortedKeys.get(virtualViewId);
        }
        return null;
    }

    public int getVirtualViewIdOf(final Key key) {
        if (mKeyboard == null) {
            return View.NO_ID;
        }
        final List<Key> sortedKeys = mKeyboard.getSortedKeys();
        final int size = sortedKeys.size();
        for (int index = 0; index < size; index++) {
            if (sortedKeys.get(index) == key) {
                // Use an index of the sorted keys list as a virtual view id.
                return index;
            }
        }
        return View.NO_ID;
    }

    /**
     * Returns the context-specific description for a {@link Key}.
     *
     * @param key The key to describe.
     * @return The context-specific description of the key.
     */
    public String getKeyDescription(final Key key) {
//        final EditorInfo editorInfo = mKeyboard.mId.mEditorInfo; NPE?
        final EditorInfo editorInfo = new EditorInfo();
        final boolean shouldObscure = AccessibilityUtils.getInstance().shouldObscureInput(editorInfo);
        final SettingsValues currentSettings = Settings.getInstance().getCurrent();
        final String keyCodeDescription = KeyCodeDescriptionMapper.getInstance().getDescriptionForKey(
                mKeyboardView.getContext(), mKeyboard, key, shouldObscure);
        if (currentSettings.isWordSeparator(key.getCode())) {
            return AccessibilityUtils.getInstance().getAutoCorrectionDescription(
                    keyCodeDescription, shouldObscure);
        }
        return keyCodeDescription;
    }
}
