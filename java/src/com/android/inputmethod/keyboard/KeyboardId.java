/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.keyboard;

import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.compat.InputTypeCompatUtils;

import java.util.Arrays;
import java.util.Locale;

// TODO: Move to com.android.inputmethod.keyboard.internal package.
/**
 * Represents the parameters necessary to construct a new LatinKeyboard,
 * which also serve as a unique identifier for each keyboard type.
 */
public class KeyboardId {
    public static final int MODE_TEXT = 0;
    public static final int MODE_URL = 1;
    public static final int MODE_EMAIL = 2;
    public static final int MODE_IM = 3;
    public static final int MODE_PHONE = 4;
    public static final int MODE_NUMBER = 5;

    public static final int ELEMENT_ALPHABET = 0;
    /* TODO: Implement alphabet variant shift keyboard.
    public static final int ELEMENT_ALPHABET_MANUAL_TEMPORARY_SHIFT = 1;
    public static final int ELEMENT_ALPHABET_AUTOMATIC_TEMPORARY_SHIFT = 2;
    public static final int ELEMENT_ALPHABET_SHIFT_LOCK = 3;
    public static final int ELEMENT_ALPHABET_SHIFT_LOCK_SHIFT = 4;
    */
    public static final int ELEMENT_SYMBOLS = 5;
    public static final int ELEMENT_SYMBOLS_SHIFT = 6;
    public static final int ELEMENT_PHONE = 7;
    public static final int ELEMENT_PHONE_SHIFT = 8;
    public static final int ELEMENT_NUMBER = 9;

    private static final int F2KEY_MODE_NONE = 0;
    private static final int F2KEY_MODE_SETTINGS = 1;
    private static final int F2KEY_MODE_SHORTCUT_IME = 2;
    private static final int F2KEY_MODE_SHORTCUT_IME_OR_SETTINGS = 3;

    public final Locale mLocale;
    public final int mOrientation;
    public final int mWidth;
    public final int mMode;
    public final int mElementState;
    private final int mInputType;
    private final int mImeOptions;
    private final boolean mSettingsKeyEnabled;
    public final boolean mClobberSettingsKey;
    public final boolean mShortcutKeyEnabled;
    public final boolean mHasShortcutKey;

    private final int mHashCode;

    public KeyboardId(int elementState, Locale locale, int orientation, int width, int mode,
            int inputType, int imeOptions, boolean settingsKeyEnabled, boolean clobberSettingsKey,
            boolean shortcutKeyEnabled, boolean hasShortcutKey) {
        this.mLocale = locale;
        this.mOrientation = orientation;
        this.mWidth = width;
        this.mMode = mode;
        this.mElementState = elementState;
        this.mInputType = inputType;
        this.mImeOptions = imeOptions;
        this.mSettingsKeyEnabled = settingsKeyEnabled;
        this.mClobberSettingsKey = clobberSettingsKey;
        this.mShortcutKeyEnabled = shortcutKeyEnabled;
        this.mHasShortcutKey = hasShortcutKey;

        this.mHashCode = hashCode(this);
    }

    private static int hashCode(KeyboardId id) {
        return Arrays.hashCode(new Object[] {
                id.mOrientation,
                id.mElementState,
                id.mMode,
                id.mWidth,
                id.navigateAction(),
                id.passwordInput(),
                id.mSettingsKeyEnabled,
                id.mClobberSettingsKey,
                id.mShortcutKeyEnabled,
                id.mHasShortcutKey,
                id.imeAction(),
                id.mLocale
        });
    }

    private boolean equals(KeyboardId other) {
        if (other == this)
            return true;
        return other.mOrientation == this.mOrientation
                && other.mElementState == this.mElementState
                && other.mMode == this.mMode
                && other.mWidth == this.mWidth
                && other.navigateAction() == this.navigateAction()
                && other.passwordInput() == this.passwordInput()
                && other.mSettingsKeyEnabled == this.mSettingsKeyEnabled
                && other.mClobberSettingsKey == this.mClobberSettingsKey
                && other.mShortcutKeyEnabled == this.mShortcutKeyEnabled
                && other.mHasShortcutKey == this.mHasShortcutKey
                && other.imeAction() == this.imeAction()
                && other.mLocale.equals(this.mLocale);
    }

    public boolean isAlphabetKeyboard() {
        return mElementState < ELEMENT_SYMBOLS;
    }

    public boolean isSymbolsKeyboard() {
        return mElementState == ELEMENT_SYMBOLS || mElementState == ELEMENT_SYMBOLS_SHIFT;
    }

    public boolean isPhoneKeyboard() {
        return mElementState == ELEMENT_PHONE || mElementState == ELEMENT_PHONE_SHIFT;
    }

    public boolean isPhoneShiftKeyboard() {
        return mElementState == ELEMENT_PHONE_SHIFT;
    }

    public boolean navigateAction() {
        // Note: Turn off checking navigation flag to show TAB key for now.
        boolean navigateAction = InputTypeCompatUtils.isWebInputType(mInputType);
//                || EditorInfoCompatUtils.hasFlagNavigateNext(mImeOptions)
//                || EditorInfoCompatUtils.hasFlagNavigatePrevious(mImeOptions);
        return navigateAction;
    }

    public boolean passwordInput() {
        return InputTypeCompatUtils.isPasswordInputType(mInputType)
                || InputTypeCompatUtils.isVisiblePasswordInputType(mInputType);
    }

    public int imeAction() {
        // We are interested only in {@link EditorInfo#IME_MASK_ACTION} enum value and
        // {@link EditorInfo#IME_FLAG_NO_ENTER_ACTION}.
        return mImeOptions & (
                EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
    }

    public boolean hasSettingsKey() {
        return mSettingsKeyEnabled && !mClobberSettingsKey;
    }

    public int f2KeyMode() {
        if (mClobberSettingsKey) {
            // Never shows the Settings key
            return KeyboardId.F2KEY_MODE_SHORTCUT_IME;
        }

        if (mSettingsKeyEnabled) {
            return KeyboardId.F2KEY_MODE_SETTINGS;
        } else {
            // It should be alright to fall back to the Settings key on 7-inch layouts
            // even when the Settings key is not explicitly enabled.
            return KeyboardId.F2KEY_MODE_SHORTCUT_IME_OR_SETTINGS;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KeyboardId && equals((KeyboardId) other);
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public String toString() {
        return String.format("[%s %s %s%d %s %s %s%s%s%s%s%s%s]",
                elementStateToString(mElementState),
                mLocale,
                (mOrientation == 1 ? "port" : "land"), mWidth,
                modeName(mMode),
                EditorInfoCompatUtils.imeOptionsName(imeAction()),
                f2KeyModeName(f2KeyMode()),
                (mClobberSettingsKey ? " clobberSettingsKey" : ""),
                (navigateAction() ? " navigateAction" : ""),
                (passwordInput() ? " passwordInput" : ""),
                (hasSettingsKey() ? " hasSettingsKey" : ""),
                (mShortcutKeyEnabled ? " shortcutKeyEnabled" : ""),
                (mHasShortcutKey ? " hasShortcutKey" : "")
        );
    }

    public static boolean equivalentEditorInfoForKeyboard(EditorInfo a, EditorInfo b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.inputType == b.inputType
                && a.imeOptions == b.imeOptions
                && TextUtils.equals(a.privateImeOptions, b.privateImeOptions);
    }

    public static String elementStateToString(int elementState) {
        switch (elementState) {
        case ELEMENT_ALPHABET: return "alphabet";
        /* TODO: Implement alphabet variant shift keyboard.
        case ELEMENT_ALPHABET_MANUAL_TEMPORARY_SHIFT: return "alphabetManualTemporaryShift";
        case ELEMENT_ALPHABET_AUTOMATIC_TEMPORARY_SHIFT: return "alphabetAutomaticTemporaryShift";
        case ELEMENT_ALPHABET_SHIFT_LOCK: return "alphabetShiftLock";
        case ELEMENT_ALPHABET_SHIFT_LOCK_SHIFT: return "alphabetShiftLockShift";
        */
        case ELEMENT_SYMBOLS: return "symbols";
        case ELEMENT_SYMBOLS_SHIFT: return "symbolsShift";
        case ELEMENT_PHONE: return "phone";
        case ELEMENT_PHONE_SHIFT: return "phoneShift";
        case ELEMENT_NUMBER: return "number";
        default: return null;
        }
    }

    public static String modeName(int mode) {
        switch (mode) {
        case MODE_TEXT: return "text";
        case MODE_URL: return "url";
        case MODE_EMAIL: return "email";
        case MODE_IM: return "im";
        case MODE_PHONE: return "phone";
        case MODE_NUMBER: return "number";
        default: return null;
        }
    }

    public static String f2KeyModeName(int f2KeyMode) {
        switch (f2KeyMode) {
        case F2KEY_MODE_NONE: return "none";
        case F2KEY_MODE_SETTINGS: return "settings";
        case F2KEY_MODE_SHORTCUT_IME: return "shortcutIme";
        case F2KEY_MODE_SHORTCUT_IME_OR_SETTINGS: return "shortcutImeOrSettings";
        default: return null;
        }
    }
}
