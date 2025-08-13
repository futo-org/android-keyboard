/*
 * Copyright (C) 2015 The Android Open Source Project
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

package org.futo.inputmethod.keyboard;

import static org.futo.inputmethod.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;

import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import org.futo.inputmethod.compat.EditorInfoCompatUtils;
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutElement;
import org.futo.inputmethod.latin.settings.LongPressKeySettings;
import org.futo.inputmethod.latin.utils.InputTypeUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * Unique identifier for each keyboard type.
 */
public final class KeyboardId {
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

    public final String mKeyboardLayoutSetName;
    public final Locale mLocale;
    public final int mWidth;
    public final int mHeight;
    public final int mMode;
    public final int mElementId;
    public final EditorInfo mEditorInfo;
    public final boolean mClobberSettingsKey;
    public final boolean mBottomEmojiKeyEnabled;
    public final int mBottomActionKeyId;
    public final String mCustomActionLabel;
    public final boolean mHasShortcutKey;
    public final boolean mIsSplitLayout;
    public final boolean mNumberRow;
    public final int mNumberRowMode;
    public final boolean mUseLocalNumbers;
    public final boolean mArrowRow;
    public final boolean mAlternativePeriodKey;
    public final LongPressKeySettings mLongPressKeySettings;
    public final KeyboardLayoutElement mElement;

    private final int mHashCode;

    public KeyboardId(String mKeyboardLayoutSetName, Locale mLocale, int mWidth, int mHeight, int mMode, int mElementId, EditorInfo mEditorInfo, boolean mClobberSettingsKey, boolean mBottomEmojiKeyEnabled, int mBottomActionKeyId, String mCustomActionLabel, boolean mHasShortcutKey, boolean mIsSplitLayout, boolean mNumberRow, int mNumberRowMode, boolean mUseLocalNumbers, boolean mArrowRow, boolean mAlternativePeriodKey, LongPressKeySettings mLongPressKeySettings, KeyboardLayoutElement mElement) {
        this.mKeyboardLayoutSetName = mKeyboardLayoutSetName;
        this.mLocale = mLocale;
        this.mWidth = mWidth;
        this.mHeight = mHeight;
        this.mMode = mMode;
        this.mElementId = mElementId;
        this.mEditorInfo = mEditorInfo;
        this.mClobberSettingsKey = mClobberSettingsKey;
        this.mBottomEmojiKeyEnabled = mBottomEmojiKeyEnabled;
        this.mBottomActionKeyId = mBottomActionKeyId;
        this.mCustomActionLabel = mCustomActionLabel;
        this.mHasShortcutKey = mHasShortcutKey;
        this.mIsSplitLayout = mIsSplitLayout;
        this.mNumberRow = mNumberRow;
        this.mNumberRowMode = mNumberRowMode;
        this.mUseLocalNumbers = mUseLocalNumbers;
        this.mArrowRow = mArrowRow;
        this.mAlternativePeriodKey = mAlternativePeriodKey;
        this.mLongPressKeySettings = mLongPressKeySettings;
        this.mElement = mElement;

        mHashCode = computeHashCode(this);
    }

    private static int computeHashCode(final KeyboardId id) {
        return Arrays.hashCode(new Object[] {
                id.mElementId,
                id.mMode,
                id.mWidth,
                id.mHeight,
                id.passwordInput(),
                id.mClobberSettingsKey,
                id.mHasShortcutKey,
                id.mBottomEmojiKeyEnabled,
                id.mBottomActionKeyId,
                id.isMultiLine(),
                id.imeAction(),
                id.mCustomActionLabel,
                id.navigateNext(),
                id.navigatePrevious(),
                id.mKeyboardLayoutSetName,
                id.mLocale,
                id.mIsSplitLayout,
                id.mNumberRow,
                id.mNumberRowMode,
                id.mUseLocalNumbers,
                id.mAlternativePeriodKey,
                id.mLongPressKeySettings.hashCode(),
                id.mElement.hashCode()
        });
    }

    private boolean equals(final KeyboardId other) {
        if (other == this)
            return true;
        return other.mElementId == mElementId
                && other.mMode == mMode
                && other.mWidth == mWidth
                && other.mHeight == mHeight
                && other.passwordInput() == passwordInput()
                && other.mClobberSettingsKey == mClobberSettingsKey
                && other.mHasShortcutKey == mHasShortcutKey
                && other.mBottomEmojiKeyEnabled == mBottomEmojiKeyEnabled
                && other.mBottomActionKeyId == mBottomActionKeyId
                && other.isMultiLine() == isMultiLine()
                && other.imeAction() == imeAction()
                && TextUtils.equals(other.mCustomActionLabel, mCustomActionLabel)
                && other.navigateNext() == navigateNext()
                && other.navigatePrevious() == navigatePrevious()
                && other.mKeyboardLayoutSetName.equals(mKeyboardLayoutSetName)
                && other.mLocale.equals(mLocale)
                && other.mIsSplitLayout == mIsSplitLayout
                && other.mNumberRow == mNumberRow
                && other.mNumberRowMode == mNumberRowMode
                && other.mUseLocalNumbers == mUseLocalNumbers
                && other.mAlternativePeriodKey == mAlternativePeriodKey
                && other.mArrowRow == mArrowRow
                && other.mLongPressKeySettings.equals(mLongPressKeySettings)
                && other.mElement.equals(mElement);
    }

    private static boolean isAlphabetKeyboard(final int elementId) {
        return elementId < ELEMENT_SYMBOLS;
    }

    public boolean isAlphabetKeyboard() {
        return isAlphabetKeyboard(mElementId);
    }

    public boolean navigateNext() {
        return (mEditorInfo.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0
                || imeAction() == EditorInfo.IME_ACTION_NEXT;
    }

    public boolean navigatePrevious() {
        return (mEditorInfo.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0
                || imeAction() == EditorInfo.IME_ACTION_PREVIOUS;
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
        return InputTypeUtils.getImeOptionsActionIdFromEditorInfo(mEditorInfo);
    }

    public Locale getLocale() {
        return mLocale;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof KeyboardId && equals((KeyboardId) other);
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "[%s %s:%s %dx%d %s %s%s%s%s%s%s%s%s%s]",
                elementIdToName(mElementId),
                mLocale,
                mKeyboardLayoutSetName,
                mWidth, mHeight,
                modeName(mMode),
                actionName(imeAction()),
                (navigateNext() ? " navigateNext" : ""),
                (navigatePrevious() ? " navigatePrevious" : ""),
                (mClobberSettingsKey ? " clobberSettingsKey" : ""),
                (passwordInput() ? " passwordInput" : ""),
                (mHasShortcutKey ? " hasShortcutKey" : ""),
                (mBottomEmojiKeyEnabled ? " languageSwitchKeyEnabled" : ""),
                (isMultiLine() ? " isMultiLine" : ""),
                (mIsSplitLayout ? " isSplitLayout" : "")
        );
    }

    public static boolean equivalentEditorInfoForKeyboard(final EditorInfo a, final EditorInfo b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.inputType == b.inputType
                && a.imeOptions == b.imeOptions
                && TextUtils.equals(a.privateImeOptions, b.privateImeOptions);
    }

    public static String elementIdToName(final int elementId) {
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

    public static String modeName(final int mode) {
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

    public static String actionName(final int actionId) {
        return (actionId == InputTypeUtils.IME_ACTION_CUSTOM_LABEL) ? "actionCustomLabel"
                : EditorInfoCompatUtils.imeActionName(actionId);
    }
}
