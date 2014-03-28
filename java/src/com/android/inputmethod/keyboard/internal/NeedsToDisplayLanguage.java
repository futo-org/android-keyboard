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

/**
 * This class determines that the language name on the spacebar should be displayed or not.
 */
public final class NeedsToDisplayLanguage {
    private int mEnabledSubtypeCount;
    private boolean mIsSystemLanguageSameAsInputLanguage;

    public boolean needsToDisplayLanguage(final InputMethodSubtype subtype) {
        if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
            return true;
        }
        return mEnabledSubtypeCount >= 2 || !mIsSystemLanguageSameAsInputLanguage;
    }

    public void updateEnabledSubtypeCount(final int count) {
        mEnabledSubtypeCount = count;
    }

    public void updateIsSystemLanguageSameAsInputLanguage(final boolean isSame) {
        mIsSystemLanguageSameAsInputLanguage = isSame;
    }
}
