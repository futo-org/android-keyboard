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

import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;

import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.latin.InputTypeUtils;
import com.android.inputmethod.latin.SubtypeLocale;

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
    public static final int MODE_DATE = 6;
    public static final int MODE_TIME = 7;
    public static final int MODE_DATETIME = 8;

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

    private static final int IME_ACTION_CUSTOM_LABEL = EditorInfo.IME_MASK_ACTION + 1;

    public final InputMethodSubtype mSubtype;
    public final Locale mLocale;
    public final int mOrientation;
    public final int mWidth;
    public final int mMode;
    public final int mElementId;
    private final EditorInfo mEditorInfo;
    public final boolean mClobberSettingsKey;
    public final boolean mShortcutKeyEnabled;
    public final boolean mHasShortcutKey;
    public final boolean mLanguageSwitchKeyEnabled;
    public final String mCustomActionLabel;

    private final int mHashCode;

    public KeyboardId(int elementId, InputMethodSubtype subtype, int orientation, int width,
            int mode, EditorInfo editorInfo, boolean clobberSettingsKey, boolean shortcutKeyEnabled,
            boolean hasShortcutKey, boolean languageSwitchKeyEnabled) {
        mSubtype = subtype;
        mLocale = SubtypeLocale.getSubtypeLocale(subtype);
        mOrientation = orientation;
        mWidth = width;
        mMode = mode;
        mElementId = elementId;
        mEditorInfo = editorInfo;
        mClobberSettingsKey = clobberSettingsKey;
        mShortcutKeyEnabled = shortcutKeyEnabled;
        mHasShortcutKey = hasShortcutKey;
        mLanguageSwitchKeyEnabled = languageSwitchKeyEnabled;
        mCustomActionLabel = (editorInfo.actionLabel != null)
                ? editorInfo.actionLabel.toString() : null;

        mHashCode = computeHashCode(this);
    }

    private static int computeHashCode(KeyboardId id) {
        return Arrays.hashCode(new Object[] {
                id.mOrientation,
                id.mElementId,
                id.mMode,
                id.mWidth,
                id.passwordInput(),
                id.mClobberSettingsKey,
                id.mShortcutKeyEnabled,
                id.mHasShortcutKey,
                id.mLanguageSwitchKeyEnabled,
                id.isMultiLine(),
                id.imeAction(),
                id.mCustomActionLabel,
                id.navigateNext(),
                id.navigatePrevious(),
                id.mSubtype
        });
    }

    private boolean equals(KeyboardId other) {
        if (other == this)
            return true;
        return other.mOrientation == mOrientation
                && other.mElementId == mElementId
                && other.mMode == mMode
                && other.mWidth == mWidth
                && other.passwordInput() == passwordInput()
                && other.mClobberSettingsKey == mClobberSettingsKey
                && other.mShortcutKeyEnabled == mShortcutKeyEnabled
                && other.mHasShortcutKey == mHasShortcutKey
                && other.mLanguageSwitchKeyEnabled == mLanguageSwitchKeyEnabled
                && other.isMultiLine() == isMultiLine()
                && other.imeAction() == imeAction()
                && TextUtils.equals(other.mCustomActionLabel, mCustomActionLabel)
                && other.navigateNext() == navigateNext()
                && other.navigatePrevious() == navigatePrevious()
                && other.mSubtype.equals(mSubtype);
    }

    public boolean isAlphabetKeyboard() {
        return mElementId < ELEMENT_SYMBOLS;
    }

    public boolean navigateNext() {
        return (mEditorInfo.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0;
    }

    public boolean navigatePrevious() {
        return (mEditorInfo.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0;
    }

    public boolean passwordInput() {
        final int inputType = mEditorInfo.inputType;
        return InputTypeUtils.isPasswordInputType(inputType)
                || InputTypeUtils.isVisiblePasswordInputType(inputType);
    }

    public boolean isMultiLine() {
        return (mEditorInfo.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
    }

    public int imeAction() {
        final int actionId = mEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
        if ((mEditorInfo.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_NONE;
        } else if (mEditorInfo.actionLabel != null) {
            return IME_ACTION_CUSTOM_LABEL;
        } else {
            return actionId;
        }
    }

    public int imeActionId() {
        final int actionId = imeAction();
        return actionId == IME_ACTION_CUSTOM_LABEL ? mEditorInfo.actionId : actionId;
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
        return String.format("[%s %s:%s %s%d %s %s %s%s%s%s%s%s%s%s]",
                elementIdToName(mElementId),
                mLocale,
                mSubtype.getExtraValueOf(KEYBOARD_LAYOUT_SET),
                (mOrientation == 1 ? "port" : "land"), mWidth,
                modeName(mMode),
                imeAction(),
                (navigateNext() ? "navigateNext" : ""),
                (navigatePrevious() ? "navigatePrevious" : ""),
                (mClobberSettingsKey ? " clobberSettingsKey" : ""),
                (passwordInput() ? " passwordInput" : ""),
                (mShortcutKeyEnabled ? " shortcutKeyEnabled" : ""),
                (mHasShortcutKey ? " hasShortcutKey" : ""),
                (mLanguageSwitchKeyEnabled ? " languageSwitchKeyEnabled" : ""),
                (isMultiLine() ? "isMultiLine" : "")
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
        case MODE_DATE: return "date";
        case MODE_TIME: return "time";
        case MODE_DATETIME: return "datetime";
        default: return null;
        }
    }

    public static String actionName(int actionId) {
        return (actionId == IME_ACTION_CUSTOM_LABEL) ? "actionCustomLabel"
                : EditorInfoCompatUtils.imeActionName(actionId);
    }
}
