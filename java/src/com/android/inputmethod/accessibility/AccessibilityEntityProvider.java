/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeProviderCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.CoordinateUtils;

/**
 * Exposes a virtual view sub-tree for {@link KeyboardView} and generates
 * {@link AccessibilityEvent}s for individual {@link Key}s.
 * <p>
 * A virtual sub-tree is composed of imaginary {@link View}s that are reported
 * as a part of the view hierarchy for accessibility purposes. This enables
 * custom views that draw complex content to report them selves as a tree of
 * virtual views, thus conveying their logical structure.
 * </p>
 */
public final class AccessibilityEntityProvider extends AccessibilityNodeProviderCompat {
    private static final String TAG = AccessibilityEntityProvider.class.getSimpleName();
    private static final int UNDEFINED = Integer.MIN_VALUE;

    private final InputMethodService mInputMethodService;
    private final KeyCodeDescriptionMapper mKeyCodeDescriptionMapper;
    private final AccessibilityUtils mAccessibilityUtils;

    /** A map of integer IDs to {@link Key}s. */
    private final SparseArray<Key> mVirtualViewIdToKey = CollectionUtils.newSparseArray();

    /** Temporary rect used to calculate in-screen bounds. */
    private final Rect mTempBoundsInScreen = new Rect();

    /** The parent view's cached on-screen location. */
    private final int[] mParentLocation = CoordinateUtils.newInstance();

    /** The virtual view identifier for the focused node. */
    private int mAccessibilityFocusedView = UNDEFINED;

    /** The current keyboard view. */
    private KeyboardView mKeyboardView;

    public AccessibilityEntityProvider(final KeyboardView keyboardView,
            final InputMethodService inputMethod) {
        mInputMethodService = inputMethod;
        mKeyCodeDescriptionMapper = KeyCodeDescriptionMapper.getInstance();
        mAccessibilityUtils = AccessibilityUtils.getInstance();
        setView(keyboardView);
    }

    /**
     * Sets the keyboard view represented by this node provider.
     *
     * @param keyboardView The keyboard view to represent.
     */
    public void setView(final KeyboardView keyboardView) {
        mKeyboardView = keyboardView;
        updateParentLocation();

        // Since this class is constructed lazily, we might not get a subsequent
        // call to setKeyboard() and therefore need to call it now.
        setKeyboard();
    }

    /**
     * Sets the keyboard represented by this node provider.
     */
    public void setKeyboard() {
        assignVirtualViewIds();
    }

    /**
     * Creates and populates an {@link AccessibilityEvent} for the specified key
     * and event type.
     *
     * @param key A key on the host keyboard view.
     * @param eventType The event type to create.
     * @return A populated {@link AccessibilityEvent} for the key.
     * @see AccessibilityEvent
     */
    public AccessibilityEvent createAccessibilityEvent(final Key key, final int eventType) {
        final int virtualViewId = generateVirtualViewIdForKey(key);
        final String keyDescription = getKeyDescription(key);
        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(mKeyboardView.getContext().getPackageName());
        event.setClassName(key.getClass().getName());
        event.setContentDescription(keyDescription);
        event.setEnabled(true);
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        record.setSource(mKeyboardView, virtualViewId);
        return event;
    }

    /**
     * Returns an {@link AccessibilityNodeInfoCompat} representing a virtual
     * view, i.e. a descendant of the host View, with the given <code>virtualViewId</code> or
     * the host View itself if <code>virtualViewId</code> equals to {@link View#NO_ID}.
     * <p>
     * A virtual descendant is an imaginary View that is reported as a part of
     * the view hierarchy for accessibility purposes. This enables custom views
     * that draw complex content to report them selves as a tree of virtual
     * views, thus conveying their logical structure.
     * </p>
     * <p>
     * The implementer is responsible for obtaining an accessibility node info
     * from the pool of reusable instances and setting the desired properties of
     * the node info before returning it.
     * </p>
     *
     * @param virtualViewId A client defined virtual view id.
     * @return A populated {@link AccessibilityNodeInfoCompat} for a virtual descendant or the host
     * View.
     * @see AccessibilityNodeInfoCompat
     */
    @Override
    public AccessibilityNodeInfoCompat createAccessibilityNodeInfo(final int virtualViewId) {
        if (virtualViewId == UNDEFINED) {
            return null;
        }
        if (virtualViewId == View.NO_ID) {
            // We are requested to create an AccessibilityNodeInfo describing
            // this View, i.e. the root of the virtual sub-tree.
            final AccessibilityNodeInfoCompat rootInfo =
                    AccessibilityNodeInfoCompat.obtain(mKeyboardView);
            ViewCompat.onInitializeAccessibilityNodeInfo(mKeyboardView, rootInfo);

            // Add the virtual children of the root View.
            final Keyboard keyboard = mKeyboardView.getKeyboard();
            final Key[] keys = keyboard.getKeys();
            for (Key key : keys) {
                final int childVirtualViewId = generateVirtualViewIdForKey(key);
                rootInfo.addChild(mKeyboardView, childVirtualViewId);
            }
            return rootInfo;
        }

        // Find the view that corresponds to the given id.
        final Key key = mVirtualViewIdToKey.get(virtualViewId);
        if (key == null) {
            Log.e(TAG, "Invalid virtual view ID: " + virtualViewId);
            return null;
        }
        final String keyDescription = getKeyDescription(key);
        final Rect boundsInParent = key.getHitBox();

        // Calculate the key's in-screen bounds.
        mTempBoundsInScreen.set(boundsInParent);
        mTempBoundsInScreen.offset(
                CoordinateUtils.x(mParentLocation), CoordinateUtils.y(mParentLocation));
        final Rect boundsInScreen = mTempBoundsInScreen;

        // Obtain and initialize an AccessibilityNodeInfo with information about the virtual view.
        final AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        info.setPackageName(mKeyboardView.getContext().getPackageName());
        info.setClassName(key.getClass().getName());
        info.setContentDescription(keyDescription);
        info.setBoundsInParent(boundsInParent);
        info.setBoundsInScreen(boundsInScreen);
        info.setParent(mKeyboardView);
        info.setSource(mKeyboardView, virtualViewId);
        info.setBoundsInScreen(boundsInScreen);
        info.setEnabled(true);
        info.setVisibleToUser(true);

        if (mAccessibilityFocusedView == virtualViewId) {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
        }
        return info;
    }

    /**
     * Simulates a key press by injecting touch events into the keyboard view.
     * This avoids the complexity of trackers and listeners within the keyboard.
     *
     * @param key The key to press.
     */
    void simulateKeyPress(final Key key) {
        final int x = key.getHitBox().centerX();
        final int y = key.getHitBox().centerY();
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        final MotionEvent upEvent = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);

        mKeyboardView.onTouchEvent(downEvent);
        mKeyboardView.onTouchEvent(upEvent);
        downEvent.recycle();
        upEvent.recycle();
    }

    @Override
    public boolean performAction(final int virtualViewId, final int action,
            final Bundle arguments) {
        final Key key = mVirtualViewIdToKey.get(virtualViewId);
        if (key == null) {
            return false;
        }
        return performActionForKey(key, action, arguments);
    }

    /**
     * Performs the specified accessibility action for the given key.
     *
     * @param key The on which to perform the action.
     * @param action The action to perform.
     * @param arguments The action's arguments.
     * @return The result of performing the action, or false if the action is not supported.
     */
    boolean performActionForKey(final Key key, final int action, final Bundle arguments) {
        final int virtualViewId = generateVirtualViewIdForKey(key);

        switch (action) {
        case AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS:
            if (mAccessibilityFocusedView == virtualViewId) {
                return false;
            }
            mAccessibilityFocusedView = virtualViewId;
            sendAccessibilityEventForKey(
                    key, AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            return true;
        case AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
            if (mAccessibilityFocusedView != virtualViewId) {
                return false;
            }
            mAccessibilityFocusedView = UNDEFINED;
            sendAccessibilityEventForKey(
                    key, AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            return true;
        default:
            return false;
        }
    }

    /**
     * Sends an accessibility event for the given {@link Key}.
     *
     * @param key The key that's sending the event.
     * @param eventType The type of event to send.
     */
    void sendAccessibilityEventForKey(final Key key, final int eventType) {
        final AccessibilityEvent event = createAccessibilityEvent(key, eventType);
        mAccessibilityUtils.requestSendAccessibilityEvent(event);
    }

    /**
     * Returns the context-specific description for a {@link Key}.
     *
     * @param key The key to describe.
     * @return The context-specific description of the key.
     */
    private String getKeyDescription(final Key key) {
        final EditorInfo editorInfo = mInputMethodService.getCurrentInputEditorInfo();
        final boolean shouldObscure = mAccessibilityUtils.shouldObscureInput(editorInfo);
        final SettingsValues currentSettings = Settings.getInstance().getCurrent();
        final String keyCodeDescription = mKeyCodeDescriptionMapper.getDescriptionForKey(
                mKeyboardView.getContext(), mKeyboardView.getKeyboard(), key, shouldObscure);
        if (currentSettings.isWordSeparator(key.getCode())) {
            return mAccessibilityUtils.getAutoCorrectionDescription(
                    keyCodeDescription, shouldObscure);
        } else {
            return keyCodeDescription;
        }
    }

    /**
     * Assigns virtual view IDs to keyboard keys and populates the related maps.
     */
    private void assignVirtualViewIds() {
        final Keyboard keyboard = mKeyboardView.getKeyboard();
        if (keyboard == null) {
            return;
        }
        mVirtualViewIdToKey.clear();

        final Key[] keys = keyboard.getKeys();
        for (Key key : keys) {
            final int virtualViewId = generateVirtualViewIdForKey(key);
            mVirtualViewIdToKey.put(virtualViewId, key);
        }
    }

    /**
     * Updates the parent's on-screen location.
     */
    private void updateParentLocation() {
        mKeyboardView.getLocationOnScreen(mParentLocation);
    }

    /**
     * Generates a virtual view identifier for the given key. Returned
     * identifiers are valid until the next global layout state change.
     *
     * @param key The key to identify.
     * @return A virtual view identifier.
     */
    private static int generateVirtualViewIdForKey(final Key key) {
        // The key x- and y-coordinates are stable between layout changes.
        // Generate an identifier by bit-shifting the x-coordinate to the
        // left-half of the integer and OR'ing with the y-coordinate.
        return ((0xFFFF & key.getX()) << (Integer.SIZE / 2)) | (0xFFFF & key.getY());
    }
}
