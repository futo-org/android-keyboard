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

package com.android.inputmethod.latin.personalization;

import java.util.Locale;

import android.content.Context;

import com.android.inputmethod.latin.DictionaryFacilitator;

public class PersonalizationDictionaryUpdater {
    final Context mContext;
    final DictionaryFacilitator mDictionaryFacilitator;
    boolean mDictCleared = false;

    public PersonalizationDictionaryUpdater(final Context context,
            final DictionaryFacilitator dictionaryFacilitator) {
        mContext = context;
        mDictionaryFacilitator = dictionaryFacilitator;
    }

    public Locale getLocale() {
        return null;
    }

    public void onLoadSettings(final boolean usePersonalizedDicts,
            final boolean isSystemLocaleSameAsLocaleOfAllEnabledSubtypesOfEnabledImes) {
        if (!mDictCleared) {
            // Clear and never update the personalization dictionary.
            PersonalizationHelper.removeAllPersonalizationDictionaries(mContext);
            mDictionaryFacilitator.clearPersonalizationDictionary();
            mDictCleared = true;
        }
    }

    public void onDestroy() {
    }
}
