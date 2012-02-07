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

import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.compat.InputTypeCompatUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * Unique identifier for each keyboard type.
 */
public class KeyboardId {
    public static final int MODE_TEXT = 0;
    public static final int MODE_URL = 1;
    public static final int MODE_EMAIL = 2;
    public static final int MODE_IM = 3;
    public static final int MODE_PHONE = 4;
    public static final int MODE_NUMBER = 5;

    public static final int ELEMENT_ALPHABET = 0;
    public static final int ELEMENT_ALPHABET_MANUAL_SHIFTED = 1;
    public static final int ELEMENT_ALPHABET_AUTOMATIC_SHIFTED = 2;
    public static final int ELEMENT_ALPHABET_SHIFT_LOCKED = 3;
    public static final int ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED = 4;
    public static final int ELEMENT_SYMBOLS = 5;
    public static final int ELEMENT_SYMBOLS_SHIFTED = 6;
    public static final int ELEMENT_PHONE = 7;
    public static final int ELEMENT_PHONE_SYMBOLS = 8;
    public static final int ELEMENT_NUMBER = 9;

    private static final int F2KEY_MODE_NONE = 0;
    private static final int F2KEY_MODE_SETTINGS = 1;
    private static final int F2KEY_MODE_SHORTCUT_IME = 2;
    private static final int F2KEY_MODE_SHORTCUT_IME_OR_SETTINGS = 3;

    private static final int IME_ACTION_CUSTOM_LABEL = EditorInfo.IME_MASK_ACTION + 1;

    public final Locale mLocale;
    public final int mOrientation;
    public final int mWidth;
    public final int mMode;
    public final int mElementId;
    private final EditorInfo mEditorInfo;
    private final boolean mSettingsKeyEnabled;
    public final boolean mClobberSettingsKey;
    public final boolean mShortcutKeyEnabled;
    public final boolean mHasShortcutKey;
    public final String mCustomActionLabel;

    private final int mHashCode;

    public KeyboardId(int elementId, Locale locale, int orientation, int width, int mode,
            EditorInfo editorInfo, boolean settingsKeyEnabled, boolean clobberSettingsKey,
            boolean shortcutKeyEnabled, boolean hasShortcutKey) {
        this.mLocale = locale;
        this.mOrientation = orientation;
        this.mWidth = width;
        this.mMode = mode;
        this.mElementId = elementId;
        this.mEditorInfo = editorInfo;
        this.mSettingsKeyEnabled = settingsKeyEnabled;
        this.mClobberSettingsKey = clobberSettingsKey;
        this.mShortcutKeyEnabled = shortcutKeyEnabled;
        this.mHasShortcutKey = hasShortcutKey;
        this.mCustomActionLabel = (editorInfo.actionLabel != null)
                ? editorInfo.actionLabel.toString() : null;

        this.mHashCode = hashCode(this);
    }

    private static int hashCode(KeyboardId id) {
        return Arrays.hashCode(new Object[] {
                id.mOrientation,
                id.mElementId,
                id.mMode,
                id.mWidth,
                id.navigateAction(),
                id.passwordInput(),
                id.mSettingsKeyEnabled,
                id.mClobberSettingsKey,
                id.mShortcutKeyEnabled,
                id.mHasShortcutKey,
                id.isMultiLine(),
                id.imeAction(),
                id.mLocale
        });
    }

    private boolean equals(KeyboardId other) {
        if (other == this)
            return true;
        return other.mOrientation == this.mOrientation
                && other.mElementId == this.mElementId
                && other.mMode == this.mMode
                && other.mWidth == this.mWidth
                && other.navigateAction() == this.navigateAction()
                && other.passwordInput() == this.passwordInput()
                && other.mSettingsKeyEnabled == this.mSettingsKeyEnabled
                && other.mClobberSettingsKey == this.mClobberSettingsKey
                && other.mShortcutKeyEnabled == this.mShortcutKeyEnabled
                && other.mHasShortcutKey == this.mHasShortcutKey
                && other.isMultiLine() == this.isMultiLine()
                && other.imeAction() == this.imeAction()
                && other.mLocale.equals(this.mLocale);
    }

    public boolean isAlphabetKeyboard() {
        return mElementId < ELEMENT_SYMBOLS;
    }

    public boolean isAlphabetShiftLockedKeyboard() {
        return mElementId == ELEMENT_ALPHABET_SHIFT_LOCKED
                || mElementId == ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED;
    }

    public boolean isAlphabetShiftedOrShiftLockedKeyboard() {
        return isAlphabetKeyboard() && mElementId != ELEMENT_ALPHABET;
    }

    public boolean isAlphabetManualShiftedKeyboard() {
        return mElementId == ELEMENT_ALPHABET_MANUAL_SHIFTED;
    }

    public boolean isSymbolsKeyboard() {
        return mElementId == ELEMENT_SYMBOLS || mElementId == ELEMENT_SYMBOLS_SHIFTED;
    }

    public boolean isPhoneKeyboard() {
        return mElementId == ELEMENT_PHONE || mElementId == ELEMENT_PHONE_SYMBOLS;
    }

    public boolean isPhoneShiftKeyboard() {
        return mElementId == ELEMENT_PHONE_SYMBOLS;
    }

    public boolean navigateAction() {
        // Note: Turn off checking navigation flag to show TAB key for now.
        boolean navigateAction = InputTypeCompatUtils.isWebInputType(mEditorInfo.inputType);
//                || EditorInfoCompatUtils.hasFlagNavigateNext(mImeOptions)
//                || EditorInfoCompatUtils.hasFlagNavigatePrevious(mImeOptions);
        return navigateAction;
    }

    public boolean passwordInput() {
        final int inputType = mEditorInfo.inputType;
        return InputTypeCompatUtils.isPasswordInputType(inputType)
                || InputTypeCompatUtils.isVisiblePasswordInputType(inputType);
    }

    public boolean isMultiLine() {
        return (mEditorInfo.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
    }

    public int imeAction() {
        if ((mEditorInfo.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_NONE;
        } else if (mEditorInfo.actionLabel != null) {
            return IME_ACTION_CUSTOM_LABEL;
        } else {
            return mEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
        }
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
                elementIdToName(mElementId),
                mLocale,
                (mOrientation == 1 ? "port" : "land"), mWidth,
                modeName(mMode),
                imeAction(),
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

    public static String elementIdToName(int elementId) {
        switch (elementId) {
        case ELEMENT_ALPHABET: return "alphabet";
        case ELEMENT_ALPHABET_MANUAL_SHIFTED: return "alphabetManualShifted";
        case ELEMENT_ALPHABET_AUTOMATIC_SHIFTED: return "alphabetAutomaticShifted";
        case ELEMENT_ALPHABET_SHIFT_LOCKED: return "alphabetShiftLocked";
        case ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED: return "alphabetShiftLockShifted";
        case ELEMENT_SYMBOLS: return "symbols";
        case ELEMENT_SYMBOLS_SHIFTED: return "symbolsShifted";
        case ELEMENT_PHONE: return "phone";
        case ELEMENT_PHONE_SYMBOLS: return "phoneSymbols";
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

    public static String actionName(int actionId) {
        return (actionId == IME_ACTION_CUSTOM_LABEL) ? "actionCustomLabel"
                : EditorInfoCompatUtils.imeActionName(actionId);
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
