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

package com.android.inputmethod.latin;

import java.util.Locale;

import android.test.suitebuilder.annotation.LargeTest;

import com.android.inputmethod.latin.utils.DistracterFilterUsingSuggestion;

/**
 * Unit test for DistracterFilter
 */
@LargeTest
public class DistracterFilterTest extends InputTestsBase {
    private DistracterFilterUsingSuggestion mDistracterFilter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDistracterFilter = new DistracterFilterUsingSuggestion(getContext());
        mDistracterFilter.updateEnabledSubtypes(mLatinIME.getEnabledSubtypesForTest());
    }

    public void testIsDistractorToWordsInDictionaries() {
        final PrevWordsInfo EMPTY_PREV_WORDS_INFO = PrevWordsInfo.EMPTY_PREV_WORDS_INFO;

        final Locale localeEnUs = new Locale("en", "US");
        String typedWord;

        typedWord = "Bill";
        // For this test case, we consider "Bill" is a distracter to "bill".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "nOt";
        // For this test case, we consider "nOt" is a distracter to "not".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "youre";
        // For this test case, we consider "youre" is a distracter to "you're".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "Banana";
        // For this test case, we consider "Banana" is a distracter to "banana".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "orange";
        // For this test case, we consider "orange" is not a distracter to any word in dictionaries.
        assertFalse(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "Orange";
        // For this test case, we consider "Orange" is a distracter to "orange".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "café";
        // For this test case, we consider "café" is a distracter to "cafe".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "cafe";
        // For this test case, we consider "cafe" is not a distracter to any word in dictionaries.
        assertFalse(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "I'll";
        // For this test case, we consider "I'll" is not a distracter to any word in dictionaries.
        assertFalse(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "ill";
        // For this test case, we consider "ill" is a distracter to "I'll"
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "asdfd";
        // For this test case, we consider "asdfd" is not a distracter to any word in dictionaries.
        assertFalse(
                mDistracterFilter.isDistracterToWordsInDictionaries(
                        EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "thank";
        // For this test case, we consider "thank" is not a distracter to any other word
        // in dictionaries.
        assertFalse(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        final Locale localeDeDe = new Locale("de", "DE");

        typedWord = "fuer";
        // For this test case, we consider "fuer" is a distracter to "für".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeDeDe));

        typedWord = "fUEr";
        // For this test case, we consider "fUEr" is a distracter to "für".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeDeDe));

        typedWord = "fur";
        // For this test case, we consider "fur" is a distracter to "für".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeDeDe));

        final Locale localeFrFr = new Locale("fr", "FR");

        typedWord = "a";
        // For this test case, we consider "a" is a distracter to "à".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeFrFr));

        typedWord = "à";
        // For this test case, we consider "à" is not a distracter to any word in dictionaries.
        assertFalse(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeFrFr));

        typedWord = "etre";
        // For this test case, we consider "etre" is a distracter to "être".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeFrFr));

        typedWord = "États-unis";
        // For this test case, we consider "États-unis" is a distracter to "États-Unis".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeFrFr));

        typedWord = "ÉtatsUnis";
        // For this test case, we consider "ÉtatsUnis" is a distracter to "États-Unis".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeFrFr));
    }
}
