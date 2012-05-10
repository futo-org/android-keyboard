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
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeProviderCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardView;

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
public class AccessibilityEntityProvider extends AccessibilityNodeProviderCompat {
    private static final String TAG = AccessibilityEntityProvider.class.getSimpleName();

    private final KeyboardView mKeyboardView;
    private final InputMethodService mInputMethodService;
    private final KeyCodeDescriptionMapper mKeyCodeDescriptionMapper;
    private final AccessibilityUtils mAccessibilityUtils;

    /** A map of integer IDs to {@link Key}s. */
    private final SparseArray<Key> mVirtualViewIdToKey = new SparseArray<Key>();

    /** Temporary rect used to calculate in-screen bounds. */
    private final Rect mTempBoundsInScreen = new Rect();

    /** The parent view's cached on-screen location. */
    private final int[] mParentLocation = new int[2];

    public AccessibilityEntityProvider(KeyboardView keyboardView, InputMethodService inputMethod) {
        mKeyboardView = keyboardView;
        mInputMethodService = inputMethod;

        mKeyCodeDescriptionMapper = KeyCodeDescriptionMapper.getInstance();
        mAccessibilityUtils = AccessibilityUtils.getInstance();

        assignVirtualViewIds();
        updateParentLocation();

        // Ensure that the on-screen bounds are cleared when the layout changes.
        mKeyboardView.getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
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
    public AccessibilityEvent createAccessibilityEvent(Key key, int eventType) {
        final int virtualViewId = generateVirtualViewIdForKey(key);
        final String keyDescription = getKeyDescription(key);

        final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(mKeyboardView.getContext().getPackageName());
        event.setClassName(key.getClass().getName());
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
     * @return A populated {@link AccessibilityNodeInfoCompat} for a virtual
     *         descendant or the host View.
     * @see AccessibilityNodeInfoCompat
     */
    @Override
    public AccessibilityNodeInfoCompat createAccessibilityNodeInfo(int virtualViewId) {
        AccessibilityNodeInfoCompat info = null;

        if (virtualViewId == View.NO_ID) {
            // We are requested to create an AccessibilityNodeInfo describing
            // this View, i.e. the root of the virtual sub-tree.
            info = AccessibilityNodeInfoCompat.obtain(mKeyboardView);
            ViewCompat.onInitializeAccessibilityNodeInfo(mKeyboardView, info);

            // Add the virtual children of the root View.
            final Keyboard keyboard = mKeyboardView.getKeyboard();
            final Key[] keys = keyboard.mKeys;
            for (Key key : keys) {
                final int childVirtualViewId = generateVirtualViewIdForKey(key);
                info.addChild(mKeyboardView, childVirtualViewId);
            }
        } else {
            // Find the view that corresponds to the given id.
            final Key key = mVirtualViewIdToKey.get(virtualViewId);
            if (key == null) {
                Log.e(TAG, "Invalid virtual view ID: " + virtualViewId);
                return null;
            }

            final String keyDescription = getKeyDescription(key);
            final Rect boundsInParent = key.mHitBox;

            // Calculate the key's in-screen bounds.
            mTempBoundsInScreen.set(boundsInParent);
            mTempBoundsInScreen.offset(mParentLocation[0], mParentLocation[1]);

            final Rect boundsInScreen = mTempBoundsInScreen;

            // Obtain and initialize an AccessibilityNodeInfo with
            // information about the virtual view.
            info = AccessibilityNodeInfoCompat.obtain();
            info.setPackageName(mKeyboardView.getContext().getPackageName());
            info.setClassName(key.getClass().getName());
            info.setBoundsInParent(boundsInParent);
            info.setBoundsInScreen(boundsInScreen);
            info.setParent(mKeyboardView);
            info.setSource(mKeyboardView, virtualViewId);
            info.setBoundsInScreen(boundsInScreen);
            info.setText(keyDescription);
            info.setEnabled(true);
        }

        return info;
    }

    /**
     * Returns the context-specific description for a {@link Key}.
     *
     * @param key The key to describe.
     * @return The context-specific description of the key.
     */
    private String getKeyDescription(Key key) {
        final EditorInfo editorInfo = mInputMethodService.getCurrentInputEditorInfo();
        final boolean shouldObscure = mAccessibilityUtils.shouldObscureInput(editorInfo);
        final String keyDescription = mKeyCodeDescriptionMapper.getDescriptionForKey(
                mKeyboardView.getContext(), mKeyboardView.getKeyboard(), key, shouldObscure);

        return keyDescription;
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

        final Key[] keys = keyboard.mKeys;
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
     * Generates a virtual view identifier for the specified key.
     *
     * @param key The key to identify.
     * @return A virtual view identifier.
     */
    private static int generateVirtualViewIdForKey(Key key) {
        // The key code is unique within an instance of a Keyboard.
        return key.mCode;
    }

    private final OnGlobalLayoutListener mGlobalLayoutListener = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            assignVirtualViewIds();
            updateParentLocation();
        }
    };
}
