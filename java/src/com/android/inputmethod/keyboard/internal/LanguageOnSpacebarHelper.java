/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Collections;
import java.util.List;

/**
 * This class determines that the language name on the spacebar should be displayed in what format.
 */
public final class LanguageOnSpacebarHelper {
    public static final int FORMAT_TYPE_NONE = 0;
    public static final int FORMAT_TYPE_LANGUAGE_ONLY = 1;
    public static final int FORMAT_TYPE_FULL_LOCALE = 2;

    private List<InputMethodSubtype> mEnabledSubtypes = Collections.emptyList();
    private boolean mIsSystemLanguageSameAsInputLanguage;

    public int getLanguageOnSpacebarFormatType(final InputMethodSubtype subtype) {
        if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
            return FORMAT_TYPE_FULL_LOCALE;
        }
        // Only this subtype is enabled and equals to the system locale.
        if (mEnabledSubtypes.size() < 2 && mIsSystemLanguageSameAsInputLanguage) {
            return FORMAT_TYPE_NONE;
        }
        final String keyboardLanguage = SubtypeLocaleUtils.getSubtypeLocale(subtype).getLanguage();
        final String keyboardLayout = SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype);
        int sameLanguageAndLayoutCount = 0;
        for (final InputMethodSubtype ims : mEnabledSubtypes) {
            final String language = SubtypeLocaleUtils.getSubtypeLocale(ims).getLanguage();
            if (keyboardLanguage.equals(language) && keyboardLayout.equals(
                    SubtypeLocaleUtils.getKeyboardLayoutSetName(ims))) {
                sameLanguageAndLayoutCount++;
            }
        }
        // Display full locale name only when there are multiple subtypes that have the same
        // locale and keyboard layout. Otherwise displaying language name is enough.
        return sameLanguageAndLayoutCount > 1 ? FORMAT_TYPE_FULL_LOCALE
                : FORMAT_TYPE_LANGUAGE_ONLY;
    }

    public void updateEnabledSubtypes(final List<InputMethodSubtype> enabledSubtypes) {
        mEnabledSubtypes = enabledSubtypes;
    }

    public void updateIsSystemLanguageSameAsInputLanguage(final boolean isSame) {
        mIsSystemLanguageSameAsInputLanguage = isSame;
    }
}
