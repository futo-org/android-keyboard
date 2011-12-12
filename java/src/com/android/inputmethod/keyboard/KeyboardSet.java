/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SettingsValues;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Utils;

import java.util.Locale;

/**
 * This class has a set of {@link KeyboardId}s. Each of them represents a different keyboard
 * specific to a keyboard state, such as alphabet, symbols, and so on.  Layouts in the same
 * {@link KeyboardSet} are related to each other.
 * A {@link KeyboardSet} needs to be created for each {@link android.view.inputmethod.EditorInfo}.
 */
public class KeyboardSet {
    // TODO: Make these KeyboardId private.
    public final KeyboardId mAlphabetId;
    public final KeyboardId mSymbolsId;
    public final KeyboardId mSymbolsShiftedId;

    KeyboardSet(Builder builder) {
        mAlphabetId = builder.getKeyboardId(false, false);
        mSymbolsId = builder.getKeyboardId(true, false);
        mSymbolsShiftedId = builder.getKeyboardId(true, true);
    }

    public static class Builder {
        private final Resources mResources;
        private final EditorInfo mEditorInfo;

        private final int mMode;
        private final boolean mVoiceKeyEnabled;
        private final boolean mNoSettingsKey;
        private final boolean mHasSettingsKey;
        private final int mF2KeyMode;
        private final boolean mVoiceKeyOnMain;
        private final Locale mLocale;
        private final Configuration mConf;
        private final DisplayMetrics mMetrics;

        public Builder(Context context, EditorInfo editorInfo, SettingsValues settingsValues) {
            mResources = context.getResources();
            mEditorInfo = editorInfo;
            final SubtypeSwitcher subtypeSwitcher = SubtypeSwitcher.getInstance();
            final String packageName = context.getPackageName();

            mMode = Utils.getKeyboardMode(mEditorInfo);
            final boolean settingsKeyEnabled = settingsValues.isSettingsKeyEnabled();
            @SuppressWarnings("deprecation")
            final boolean noMicrophone = Utils.inPrivateImeOptions(
                    packageName, LatinIME.IME_OPTION_NO_MICROPHONE, editorInfo)
                    || Utils.inPrivateImeOptions(
                            null, LatinIME.IME_OPTION_NO_MICROPHONE_COMPAT, editorInfo);
            mVoiceKeyEnabled = settingsValues.isVoiceKeyEnabled(editorInfo) && !noMicrophone;
            mVoiceKeyOnMain = settingsValues.isVoiceKeyOnMain();
            mNoSettingsKey = Utils.inPrivateImeOptions(
                    packageName, LatinIME.IME_OPTION_NO_SETTINGS_KEY, editorInfo);
            mHasSettingsKey = settingsKeyEnabled && !mNoSettingsKey;
            mF2KeyMode = getF2KeyMode(settingsKeyEnabled, mNoSettingsKey);
            final boolean forceAscii = Utils.inPrivateImeOptions(
                    packageName, LatinIME.IME_OPTION_FORCE_ASCII, editorInfo);
            final boolean asciiCapable = subtypeSwitcher.currentSubtypeContainsExtraValueKey(
                    LatinIME.SUBTYPE_EXTRA_VALUE_ASCII_CAPABLE);
            mLocale = (forceAscii && !asciiCapable) ? Locale.US : subtypeSwitcher.getInputLocale();
            mConf = mResources.getConfiguration();
            mMetrics = mResources.getDisplayMetrics();
        }

        public KeyboardSet build() {
            return new KeyboardSet(this);
        }

        KeyboardId getKeyboardId(boolean isSymbols, boolean isShift) {
            final int xmlId = getXmlId(mMode, isSymbols, isShift);
            final boolean hasShortCutKey = mVoiceKeyEnabled && (isSymbols != mVoiceKeyOnMain);
            return new KeyboardId(mResources.getResourceEntryName(xmlId), xmlId, mLocale,
                    mConf.orientation, mMetrics.widthPixels, mMode, mEditorInfo, mHasSettingsKey,
                    mF2KeyMode, mNoSettingsKey, mVoiceKeyEnabled, hasShortCutKey);
        }

        private static int getXmlId(int mode, boolean isSymbols, boolean isShift) {
            switch (mode) {
            case KeyboardId.MODE_PHONE:
                return (isSymbols && isShift) ? R.xml.kbd_phone_shift : R.xml.kbd_phone;
            case KeyboardId.MODE_NUMBER:
                return R.xml.kbd_number;
            default:
                if (isSymbols) {
                    return isShift ? R.xml.kbd_symbols_shift : R.xml.kbd_symbols;
                }
                return R.xml.kbd_qwerty;
            }
        }

        private static int getF2KeyMode(boolean settingsKeyEnabled, boolean noSettingsKey) {
            if (noSettingsKey) {
                // Never shows the Settings key
                return KeyboardId.F2KEY_MODE_SHORTCUT_IME;
            }

            if (settingsKeyEnabled) {
                return KeyboardId.F2KEY_MODE_SETTINGS;
            } else {
                // It should be alright to fall back to the Settings key on 7-inch layouts
                // even when the Settings key is not explicitly enabled.
                return KeyboardId.F2KEY_MODE_SHORTCUT_IME_OR_SETTINGS;
            }
        }
    }
}
