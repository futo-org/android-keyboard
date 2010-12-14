/*
 * Copyright (C) 2010 Google Inc.
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
    public static final int MODE_WEB = 4;
    public static final int MODE_PHONE = 5;
    public static final int MODE_NUMBER = 6;

    public final Locale mLocale;
    public final int mOrientation;
    public final int mMode;
    public final int mXmlId;
    public final int mColorScheme;
    public final boolean mHasSettingsKey;
    public final boolean mVoiceKeyEnabled;
    public final boolean mHasVoiceKey;
    public final int mImeOptions;
    public final boolean mEnableShiftLock;
    public final String mXmlName;

    private final int mHashCode;

    public KeyboardId(String xmlName, int xmlId, Locale locale, int orientation, int mode,
            int colorScheme, boolean hasSettingsKey, boolean voiceKeyEnabled, boolean hasVoiceKey,
            int imeOptions, boolean enableShiftLock) {
        this.mLocale = locale;
        this.mOrientation = orientation;
        this.mMode = mode;
        this.mXmlId = xmlId;
        this.mColorScheme = colorScheme;
        this.mHasSettingsKey = hasSettingsKey;
        this.mVoiceKeyEnabled = voiceKeyEnabled;
        this.mHasVoiceKey = hasVoiceKey;
        // We are interested only in IME_MASK_ACTION enum value and IME_FLAG_NO_ENTER_ACTION.
        this.mImeOptions = imeOptions
                & (EditorInfo.IME_MASK_ACTION | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        this.mEnableShiftLock = enableShiftLock;
        this.mXmlName = xmlName;

        this.mHashCode = Arrays.hashCode(new Object[] {
                locale,
                orientation,
                mode,
                xmlId,
                colorScheme,
                hasSettingsKey,
                voiceKeyEnabled,
                hasVoiceKey,
                imeOptions,
                enableShiftLock,
        });
    }

    public int getXmlId() {
        return mXmlId;
    }

    public boolean isAlphabetKeyboard() {
        return mXmlId == R.xml.kbd_qwerty;
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
            && other.mMode == this.mMode
            && other.mXmlId == this.mXmlId
            && other.mColorScheme == this.mColorScheme
            && other.mHasSettingsKey == this.mHasSettingsKey
            && other.mVoiceKeyEnabled == this.mVoiceKeyEnabled
            && other.mHasVoiceKey == this.mHasVoiceKey
            && other.mImeOptions == this.mImeOptions
            && other.mEnableShiftLock == this.mEnableShiftLock;
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public String toString() {
        return String.format("[%s.xml %s %s %s imeOptions=%s %s%s%s%s%s]",
                mXmlName,
                mLocale,
                (mOrientation == 1 ? "port" : "land"),
                modeName(mMode),
                imeOptionsName(mImeOptions),
                colorSchemeName(mColorScheme),
                (mHasSettingsKey ? " hasSettingsKey" : ""),
                (mVoiceKeyEnabled ? " voiceKeyEnabled" : ""),
                (mHasVoiceKey ? " hasVoiceKey" : ""),
                (mEnableShiftLock ? " enableShiftLock" : ""));
    }

    public static String modeName(int mode) {
        switch (mode) {
        case MODE_TEXT: return "text";
        case MODE_URL: return "url";
        case MODE_EMAIL: return "email";
        case MODE_IM: return "im";
        case MODE_WEB: return "web";
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

    public static String imeOptionsName(int imeOptions) {
        if (imeOptions == -1) return null;
        final int actionNo = imeOptions & EditorInfo.IME_MASK_ACTION;
        final String action;
        switch (actionNo) {
        case EditorInfo.IME_ACTION_UNSPECIFIED: action = "actionUnspecified"; break;
        case EditorInfo.IME_ACTION_NONE: action = "actionNone"; break;
        case EditorInfo.IME_ACTION_GO: action = "actionGo"; break;
        case EditorInfo.IME_ACTION_SEARCH: action = "actionSearch"; break;
        case EditorInfo.IME_ACTION_SEND: action = "actionSend"; break;
        case EditorInfo.IME_ACTION_DONE: action = "actionDone"; break;
        case EditorInfo.IME_ACTION_PREVIOUS: action = "actionPrevious"; break;
        default: action = "actionUnknown(" + actionNo + ")"; break;
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return "flagNoEnterAction|" + action;
        } else {
            return action;
        }
    }
}

