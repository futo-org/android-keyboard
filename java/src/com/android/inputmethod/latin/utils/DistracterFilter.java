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

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.Suggest;

/**
 * This class is used to prevent distracters/misspellings being added to personalization
 * or user history dictionaries
 */
public class DistracterFilter {
    private final Suggest mSuggest;
    private final Keyboard mKeyboard;

    /**
     * Create a DistracterFilter instance.
     *
     * @param suggest an instance of Suggest which will be used to obtain a list of suggestions
     *                for a potential distracter/misspelling
     * @param keyboard the keyboard that is currently being used. This information is needed
     *                 when calling mSuggest.getSuggestedWords(...) to obtain a list of suggestions.
     */
    public DistracterFilter(final Suggest suggest, final Keyboard keyboard) {
        mSuggest = suggest;
        mKeyboard = keyboard;
    }

    public boolean isDistractorToWordsInDictionaries(final String prevWord,
            final String targetWord) {
        // TODO: to be implemented
        return false;
    }
}
