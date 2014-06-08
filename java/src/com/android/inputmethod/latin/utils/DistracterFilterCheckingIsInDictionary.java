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

package com.android.inputmethod.latin.utils;

import java.util.List;
import java.util.Locale;

import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.PrevWordsInfo;

public class DistracterFilterCheckingIsInDictionary implements DistracterFilter {
    private final DistracterFilter mDistracterFilter;
    private final Dictionary mDictionary;

    public DistracterFilterCheckingIsInDictionary(final DistracterFilter distracterFilter,
            final Dictionary dictionary) {
        mDistracterFilter = distracterFilter;
        mDictionary = dictionary;
    }

    @Override
    public boolean isDistracterToWordsInDictionaries(PrevWordsInfo prevWordsInfo,
            String testedWord, Locale locale) {
        if (mDictionary.isInDictionary(testedWord)) {
            // This filter treats entries that are already in the dictionary as non-distracters
            // because they have passed the filtering in the past.
            return false;
        } else {
            return mDistracterFilter.isDistracterToWordsInDictionaries(
                    prevWordsInfo, testedWord, locale);
        }
    }

    @Override
    public void updateEnabledSubtypes(List<InputMethodSubtype> enabledSubtypes) {
        // Do nothing.
    }

    @Override
    public void close() {
        // Do nothing.
    }
}
