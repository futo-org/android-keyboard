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

import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.compat.InputTypeCompatUtils;
import com.android.inputmethod.latin.R;

import android.view.inputmethod.EditorInfo;

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

    public final Locale mLocale;
    public final int mOrientation;
    public final int mWidth;
    public final int mMode;
    public final int mXmlId;
    public final int mColorScheme;
    public final boolean mWebInput;
    public final boolean mPasswordInput;
    public final boolean mHasSettingsKey;
    public final boolean mVoiceKeyEnabled;
    public final boolean mHasVoiceKey;
    public final int mImeAction;
    public final boolean mEnableShiftLock;

    public final String mXmlName;
    public final EditorInfo mAttribute;

    private final int mHashCode;

    public KeyboardId(String xmlName, int xmlId, int colorScheme, Locale locale, int orientation,
            int width, int mode, EditorInfo attribute, boolean hasSettingsKey,
            boolean voiceKeyEnabled, boolean hasVoiceKey, boolean enableShiftLock) {
        final int inputType = (attribute != null) ? attribute.inputType : 0;
        final int imeOptions = (attribute != null) ? attribute.imeOptions : 0;
        this.mLocale = locale;
        this.mOrientation = orientation;
        this.mWidth = width;
        this.mMode = mode;
        this.mXmlId = xmlId;
        this.mColorScheme = colorScheme;
        this.mWebInput = InputTypeCompatUtils.isWebInputType(inputType);
        this.mPasswordInput = InputTypeCompatUtils.isPasswordInputType(inputType)
                || InputTypeCompatUtils.isVisiblePasswordInputType(inputType);
        this.mHasSettingsKey = hasSettingsKey;
        this.mVoiceKeyEnabled = voiceKeyEnabled;
        this.mHasVoiceKey = hasVoiceKey;
        // We are interested only in {@link EditorInfo#IME_MASK_ACTION} enum value and
        // {@link EditorInfo#IME_FLAG_NO_ENTER_ACTION}.
        this.mImeAction = imeOptions & (
                EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        this.mEnableShiftLock = enableShiftLock;

        this.mXmlName = xmlName;
        this.mAttribute = attribute;

        this.mHashCode = Arrays.hashCode(new Object[] {
                locale,
                orientation,
                width,
                mode,
                xmlId,
                colorScheme,
                mWebInput,
                mPasswordInput,
                hasSettingsKey,
                voiceKeyEnabled,
                hasVoiceKey,
                mImeAction,
                enableShiftLock,
        });
    }

    public KeyboardId cloneWithNewLayout(String xmlName, int xmlId) {
        return new KeyboardId(xmlName, xmlId, mColorScheme, mLocale, mOrientation, mWidth, mMode,
                mAttribute, mHasSettingsKey, mVoiceKeyEnabled, mHasVoiceKey, mEnableShiftLock);
    }

    public KeyboardId cloneWithNewGeometry(int width) {
        if (mWidth == width)
            return this;
        return new KeyboardId(mXmlName, mXmlId, mColorScheme, mLocale, mOrientation, width, mMode,
                mAttribute, mHasSettingsKey, mVoiceKeyEnabled, mHasVoiceKey, mEnableShiftLock);
    }

    public int getXmlId() {
        return mXmlId;
    }

    public boolean isAlphabetKeyboard() {
        return mXmlId == R.xml.kbd_qwerty;
    }

    public boolean isSymbolsKeyboard() {
        return mXmlId == R.xml.kbd_symbols;
    }

    public boolean isPhoneKeyboard() {
        return mMode == MODE_PHONE;
    }

    public boolean isNumberKeyboard() {
        return mMode == MODE_NUMBER;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KeyboardId && equals((KeyboardId) other);
    }

    boolean equals(KeyboardId other) {
        return other.mLocale.equals(this.mLocale)
            && other.mOrientation == this.mOrientation
            && other.mWidth == this.mWidth
            && other.mMode == this.mMode
            && other.mXmlId == this.mXmlId
            && other.mColorScheme == this.mColorScheme
            && other.mWebInput == this.mWebInput
            && other.mPasswordInput == this.mPasswordInput
            && other.mHasSettingsKey == this.mHasSettingsKey
            && other.mVoiceKeyEnabled == this.mVoiceKeyEnabled
            && other.mHasVoiceKey == this.mHasVoiceKey
            && other.mImeAction == this.mImeAction
            && other.mEnableShiftLock == this.mEnableShiftLock;
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
                colorSchemeName(mColorScheme),
                (mWebInput ? " webInput" : ""),
                (mPasswordInput ? " passwordInput" : ""),
                (mHasSettingsKey ? " hasSettingsKey" : ""),
                (mVoiceKeyEnabled ? " voiceKeyEnabled" : ""),
                (mHasVoiceKey ? " hasVoiceKey" : ""),
                (mEnableShiftLock ? " enableShiftLock" : "")
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
        }
        return null;
    }

    public static String colorSchemeName(int colorScheme) {
        switch (colorScheme) {
        case KeyboardView.COLOR_SCHEME_WHITE: return "white";
        case KeyboardView.COLOR_SCHEME_BLACK: return "black";
        }
        return null;
    }
}
