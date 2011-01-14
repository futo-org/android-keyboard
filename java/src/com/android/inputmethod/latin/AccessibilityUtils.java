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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility functions for accessibility support.
 */
public class AccessibilityUtils {
    /** Shared singleton instance. */
    private static final AccessibilityUtils sInstance = new AccessibilityUtils();
    private /* final */ LatinIME mService;
    private /* final */ AccessibilityManager mAccessibilityManager;
    private /* final */ Map<Integer, CharSequence> mDescriptions;

    /**
     * Returns a shared instance of AccessibilityUtils.
     *
     * @return A shared instance of AccessibilityUtils.
     */
    public static AccessibilityUtils getInstance() {
        return sInstance;
    }

    /**
     * Initializes (or re-initializes) the shared instance of AccessibilityUtils
     * with the specified parent service and preferences.
     *
     * @param service The parent input method service.
     * @param prefs The parent preferences.
     */
    public static void init(LatinIME service, SharedPreferences prefs) {
        sInstance.initialize(service, prefs);
    }

    private AccessibilityUtils() {
        // This class is not publicly instantiable.
    }

    /**
     * Initializes (or re-initializes) with the specified parent service and
     * preferences.
     *
     * @param service The parent input method service.
     * @param prefs The parent preferences.
     */
    private void initialize(LatinIME service, SharedPreferences prefs) {
        mService = service;
        mAccessibilityManager = (AccessibilityManager) service.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        mDescriptions = null;
    }

    /**
     * Returns true if accessibility is enabled.
     *
     * @return {@code true} if accessibility is enabled.
     */
    public boolean isAccessibilityEnabled() {
        return mAccessibilityManager.isEnabled();
    }

    /**
     * Speaks a key's action after it has been released. Does not speak letter
     * keys since typed keys are already spoken aloud by TalkBack.
     * <p>
     * No-op if accessibility is not enabled.
     * </p>
     *
     * @param primaryCode The primary code of the released key.
     * @param switcher The input method's {@link KeyboardSwitcher}.
     */
    public void onRelease(int primaryCode, KeyboardSwitcher switcher) {
        if (!isAccessibilityEnabled()) {
            return;
        }

        int resId = -1;

        switch (primaryCode) {
            case Keyboard.CODE_SHIFT: {
                if (switcher.isShiftedOrShiftLocked()) {
                    resId = R.string.description_shift_on;
                } else {
                    resId = R.string.description_shift_off;
                }
                break;
            }

            case Keyboard.CODE_SWITCH_ALPHA_SYMBOL: {
                if (switcher.isAlphabetMode()) {
                    resId = R.string.description_symbols_off;
                } else {
                    resId = R.string.description_symbols_on;
                }
                break;
            }
        }

        if (resId >= 0) {
            speakDescription(mService.getResources().getText(resId));
        }
    }

    /**
     * Speaks a key's description for accessibility. If a key has an explicit
     * description defined in keycodes.xml, that will be used. Otherwise, if the
     * key is a Unicode character, then its character will be used.
     * <p>
     * No-op if accessibility is not enabled.
     * </p>
     *
     * @param primaryCode The primary code of the pressed key.
     * @param switcher The input method's {@link KeyboardSwitcher}.
     */
    public void onPress(int primaryCode, KeyboardSwitcher switcher) {
        if (!isAccessibilityEnabled()) {
            return;
        }

        // TODO Use the current keyboard state to read "Switch to symbols"
        // instead of just "Symbols" (and similar for shift key).
        CharSequence description = describeKey(primaryCode);
        if (description == null && Character.isDefined((char) primaryCode)) {
            description = Character.toString((char) primaryCode);
        }

        if (description != null) {
            speakDescription(description);
        }
    }

    /**
     * Returns a text description for a given key code. If the key does not have
     * an explicit description, returns <code>null</code>.
     *
     * @param keyCode An integer key code.
     * @return A {@link CharSequence} describing the key or <code>null</code> if
     *         no description is available.
     */
    private CharSequence describeKey(int keyCode) {
        // If not loaded yet, load key descriptions from XML file.
        if (mDescriptions == null) {
            mDescriptions = loadDescriptions();
        }

        return mDescriptions.get(keyCode);
    }

    /**
     * Loads key descriptions from resources.
     */
    private Map<Integer, CharSequence> loadDescriptions() {
        final Map<Integer, CharSequence> descriptions = new HashMap<Integer, CharSequence>();
        final TypedArray array = mService.getResources().obtainTypedArray(R.array.key_descriptions);

        // Key descriptions are stored as a key code followed by a string.
        for (int i = 0; i < array.length() - 1; i += 2) {
            int code = array.getInteger(i, 0);
            CharSequence desc = array.getText(i + 1);

            descriptions.put(code, desc);
        }

        array.recycle();

        return descriptions;
    }

    /**
     * Sends a character sequence to be read aloud.
     *
     * @param description The {@link CharSequence} to be read aloud.
     */
    private void speakDescription(CharSequence description) {
        // TODO We need to add an AccessibilityEvent type for IMEs.
        final AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        event.setPackageName(mService.getPackageName());
        event.setClassName(getClass().getName());
        event.setAddedCount(description.length());
        event.getText().add(description);

        mAccessibilityManager.sendAccessibilityEvent(event);
    }
}
