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

import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.compat.InputTypeCompatUtils;
import com.android.inputmethod.latin.R;

import java.util.Arrays;
import java.util.Locale;

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

    public static final int F2KEY_MODE_NONE = 0;
    public static final int F2KEY_MODE_SETTINGS = 1;
    public static final int F2KEY_MODE_SHORTCUT_IME = 2;
    public static final int F2KEY_MODE_SHORTCUT_IME_OR_SETTINGS = 3;

    public final Locale mLocale;
    public final int mOrientation;
    public final int mWidth;
    public final int mMode;
    public final int mXmlId;
    public final boolean mNavigateAction;
    public final boolean mPasswordInput;
    // TODO: Clean up these booleans and modes.
    public final boolean mHasSettingsKey;
    public final int mF2KeyMode;
    public final boolean mClobberSettingsKey;
    public final boolean mShortcutKeyEnabled;
    public final boolean mHasShortcutKey;
    public final int mImeAction;

    public final String mXmlName;
    public final EditorInfo mAttribute;

    private final int mHashCode;

    public KeyboardId(String xmlName, int xmlId, Locale locale, int orientation, int width,
            int mode, EditorInfo attribute, boolean hasSettingsKey, int f2KeyMode,
            boolean clobberSettingsKey, boolean shortcutKeyEnabled, boolean hasShortcutKey) {
        final int inputType = (attribute != null) ? attribute.inputType : 0;
        final int imeOptions = (attribute != null) ? attribute.imeOptions : 0;
        this.mLocale = locale;
        this.mOrientation = orientation;
        this.mWidth = width;
        this.mMode = mode;
        this.mXmlId = xmlId;
        // Note: Turn off checking navigation flag to show TAB key for now.
        this.mNavigateAction = InputTypeCompatUtils.isWebInputType(inputType);
//                || EditorInfoCompatUtils.hasFlagNavigateNext(imeOptions)
//                || EditorInfoCompatUtils.hasFlagNavigatePrevious(imeOptions);
        this.mPasswordInput = InputTypeCompatUtils.isPasswordInputType(inputType)
                || InputTypeCompatUtils.isVisiblePasswordInputType(inputType);
        this.mHasSettingsKey = hasSettingsKey;
        this.mF2KeyMode = f2KeyMode;
        this.mClobberSettingsKey = clobberSettingsKey;
        this.mShortcutKeyEnabled = shortcutKeyEnabled;
        this.mHasShortcutKey = hasShortcutKey;
        // We are interested only in {@link EditorInfo#IME_MASK_ACTION} enum value and
        // {@link EditorInfo#IME_FLAG_NO_ENTER_ACTION}.
        this.mImeAction = imeOptions & (
                EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION);

        this.mXmlName = xmlName;
        this.mAttribute = attribute;

        this.mHashCode = Arrays.hashCode(new Object[] {
                locale,
                orientation,
                width,
                mode,
                xmlId,
                mNavigateAction,
                mPasswordInput,
                hasSettingsKey,
                f2KeyMode,
                clobberSettingsKey,
                shortcutKeyEnabled,
                hasShortcutKey,
                mImeAction,
        });
    }

    public KeyboardId cloneWithNewXml(String xmlName, int xmlId) {
        return new KeyboardId(xmlName, xmlId, mLocale, mOrientation, mWidth, mMode, mAttribute,
                false, F2KEY_MODE_NONE, false, false, false);
    }

    public int getXmlId() {
        return mXmlId;
    }

    public boolean isAlphabetKeyboard() {
        return mXmlId == R.xml.kbd_qwerty;
    }

    public boolean isSymbolsKeyboard() {
        return mXmlId == R.xml.kbd_symbols || mXmlId == R.xml.kbd_symbols_shift;
    }

    public boolean isPhoneKeyboard() {
        return mMode == MODE_PHONE;
    }

    public boolean isPhoneShiftKeyboard() {
        return mXmlId == R.xml.kbd_phone_shift;
    }

    public boolean isNumberKeyboard() {
        return mMode == MODE_NUMBER;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KeyboardId && equals((KeyboardId) other);
    }

    private boolean equals(KeyboardId other) {
        return other.mLocale.equals(this.mLocale)
            && other.mOrientation == this.mOrientation
            && other.mWidth == this.mWidth
            && other.mMode == this.mMode
            && other.mXmlId == this.mXmlId
            && other.mNavigateAction == this.mNavigateAction
            && other.mPasswordInput == this.mPasswordInput
            && other.mHasSettingsKey == this.mHasSettingsKey
            && other.mF2KeyMode == this.mF2KeyMode
            && other.mClobberSettingsKey == this.mClobberSettingsKey
            && other.mShortcutKeyEnabled == this.mShortcutKeyEnabled
            && other.mHasShortcutKey == this.mHasShortcutKey
            && other.mImeAction == this.mImeAction;
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public String toString() {
        return String.format("[%s.xml %s %s%d %s %s %s%s%s%s%s%s%s]",
                mXmlName,
                mLocale,
                (mOrientation == 1 ? "port" : "land"), mWidth,
                modeName(mMode),
                EditorInfoCompatUtils.imeOptionsName(mImeAction),
                f2KeyModeName(mF2KeyMode),
                (mClobberSettingsKey ? " clobberSettingsKey" : ""),
                (mNavigateAction ? " navigateAction" : ""),
                (mPasswordInput ? " passwordInput" : ""),
                (mHasSettingsKey ? " hasSettingsKey" : ""),
                (mShortcutKeyEnabled ? " shortcutKeyEnabled" : ""),
                (mHasShortcutKey ? " hasShortcutKey" : "")
        );
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
