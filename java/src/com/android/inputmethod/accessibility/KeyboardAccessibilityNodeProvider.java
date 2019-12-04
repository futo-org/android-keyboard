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
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.latin.common.CoordinateUtils;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsValues;

import java.util.List;

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
final class KeyboardAccessibilityNodeProvider<KV extends KeyboardView>
        extends AccessibilityNodeProviderCompat {
    private static final String TAG = KeyboardAccessibilityNodeProvider.class.getSimpleName();

    // From {@link android.view.accessibility.AccessibilityNodeInfo#UNDEFINED_ITEM_ID}.
    private static final int UNDEFINED = Integer.MAX_VALUE;

    private final KeyCodeDescriptionMapper mKeyCodeDescriptionMapper;
    private final AccessibilityUtils mAccessibilityUtils;

    /** Temporary rect used to calculate in-screen bounds. */
    private final Rect mTempBoundsInScreen = new Rect();

    /** The parent view's cached on-screen location. */
    private final int[] mParentLocation = CoordinateUtils.newInstance();

    /** The virtual view identifier for the focused node. */
    private int mAccessibilityFocusedView = UNDEFINED;

    /** The virtual view identifier for the hovering node. */
    private int mHoveringNodeId = UNDEFINED;

    /** The keyboard view to provide an accessibility node info. */
    private final KV mKeyboardView;
    /** The accessibility delegate. */
    private final KeyboardAccessibilityDelegate<KV> mDelegate;

    /** The current keyboard. */
    private Keyboard mKeyboard;

    public KeyboardAccessibilityNodeProvider(final KV keyboardView,
            final KeyboardAccessibilityDelegate<KV> delegate) {
        super();
        mKeyCodeDescriptionMapper = KeyCodeDescriptionMapper.getInstance();
        mAccessibilityUtils = AccessibilityUtils.getInstance();
        mKeyboardView = keyboardView;
        mDelegate = delegate;

        // Since this class is constructed lazily, we might not get a subsequent
        // call to setKeyboard() and therefore need to call it now.
        setKeyboard(keyboardView.getKeyboard());
    }

    /**
     * Sets the keyboard represented by this node provider.
     *
     * @param keyboard The keyboard that is being set to the keyboard view.
     */
    public void setKeyboard(final Keyboard keyboard) {
        mKeyboard = keyboard;
    }

    private Key getKeyOf(final int virtualViewId) {
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

    private int getVirtualViewIdOf(final Key key) {
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
     * Creates and populates an {@link AccessibilityEvent} for the specified key
     * and event type.
     *
     * @param key A key on the host keyboard view.
     * @param eventType The event type to create.
     * @return A populated {@link AccessibilityEvent} for the key.
     * @see AccessibilityEvent
     */
    public AccessibilityEvent createAccessibilityEvent(final Key key, final int eventType) {
        final int virtualViewId = getVirtualViewIdOf(key);
        final String keyDescription = getKeyDescription(key);
        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(mKeyboardView.getContext().getPackageName());
        event.setClassName(key.getClass().getName());
        event.setContentDescription(keyDescription);
        event.setEnabled(true);
        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        record.setSource(mKeyboardView, virtualViewId);
        return event;
    }

    public void onHoverEnterTo(final Key key) {
        final int id = getVirtualViewIdOf(key);
        if (id == View.NO_ID) {
            return;
        }
        // Start hovering on the key. Because our accessibility model is lift-to-type, we should
        // report the node info without click and long click actions to avoid unnecessary
        // announcements.
        mHoveringNodeId = id;
        // Invalidate the node info of the key.
        sendAccessibilityEventForKey(key, AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED);
        sendAccessibilityEventForKey(key, AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER);
    }

    public void onHoverExitFrom(final Key key) {
        mHoveringNodeId = UNDEFINED;
        // Invalidate the node info of the key to be able to revert the change we have done
        // in {@link #onHoverEnterTo(Key)}.
        sendAccessibilityEventForKey(key, AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED);
        sendAccessibilityEventForKey(key, AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT);
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
            updateParentLocation();

            // Add the virtual children of the root View.
            final List<Key> sortedKeys = mKeyboard.getSortedKeys();
            final int size = sortedKeys.size();
            for (int index = 0; index < size; index++) {
                final Key key = sortedKeys.get(index);
                if (key.isSpacer()) {
                    continue;
                }
                // Use an index of the sorted keys list as a virtual view id.
                rootInfo.addChild(mKeyboardView, index);
            }
            return rootInfo;
        }

        // Find the key that corresponds to the given virtual view id.
        final Key key = getKeyOf(virtualViewId);
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
        info.setTextEntryKey(true);
        info.setClassName(key.getClass().getName());
        info.setContentDescription(keyDescription);
        info.setBoundsInParent(boundsInParent);
        info.setBoundsInScreen(boundsInScreen);
        info.setParent(mKeyboardView);
        info.setSource(mKeyboardView, virtualViewId);
        info.setEnabled(key.isEnabled());
        info.setVisibleToUser(true);
        info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        if (key.isLongPressEnabled()) {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
        }

        if (mAccessibilityFocusedView == virtualViewId) {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        } else {
            info.addAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
        }
        return info;
    }

    @Override
    public boolean performAction(final int virtualViewId, final int action,
            final Bundle arguments) {
        final Key key = getKeyOf(virtualViewId);
        if (key == null) {
            return false;
        }
        return performActionForKey(key, action);
    }

    /**
     * Performs the specified accessibility action for the given key.
     *
     * @param key The on which to perform the action.
     * @param action The action to perform.
     * @return The result of performing the action, or false if the action is not supported.
     */
    boolean performActionForKey(final Key key, final int action) {
        switch (action) {
        case AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS:
            mAccessibilityFocusedView = getVirtualViewIdOf(key);
            sendAccessibilityEventForKey(
                    key, AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            return true;
        case AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
            mAccessibilityFocusedView = UNDEFINED;
            sendAccessibilityEventForKey(
                    key, AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
            return true;
        case AccessibilityNodeInfoCompat.ACTION_CLICK:
            sendAccessibilityEventForKey(key, AccessibilityEvent.TYPE_VIEW_CLICKED);
            mDelegate.performClickOn(key);
            return true;
        case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
            sendAccessibilityEventForKey(key, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            mDelegate.performLongClickOn(key);
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
        final EditorInfo editorInfo = mKeyboard.mId.mEditorInfo;
        final boolean shouldObscure = mAccessibilityUtils.shouldObscureInput(editorInfo);
        final SettingsValues currentSettings = Settings.getInstance().getCurrent();
        final String keyCodeDescription = mKeyCodeDescriptionMapper.getDescriptionForKey(
                mKeyboardView.getContext(), mKeyboard, key, shouldObscure);
        if (currentSettings.isWordSeparator(key.getCode())) {
            return mAccessibilityUtils.getAutoCorrectionDescription(
                    keyCodeDescription, shouldObscure);
        }
        return keyCodeDescription;
    }

    /**
     * Updates the parent's on-screen location.
     */
    private void updateParentLocation() {
        mKeyboardView.getLocationOnScreen(mParentLocation);
    }
}
