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

import android.test.suitebuilder.annotation.LargeTest;

import com.android.inputmethod.latin.utils.DistracterFilter;

/**
 * Unit test for DistracterFilter
 */
@LargeTest
public class DistracterFilterTest extends InputTestsBase {
    private DistracterFilter mDistracterFilter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDistracterFilter = mLatinIME.createDistracterFilter();
    }

    public void testIsDistractorToWordsInDictionaries() {
        final String EMPTY_PREV_WORD = null;
        String typedWord = "alot";
        // For this test case, we consider "alot" is a distracter to "a lot".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(EMPTY_PREV_WORD, typedWord));

        typedWord = "mot";
        // For this test case, we consider "mot" is a distracter to "not".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(EMPTY_PREV_WORD, typedWord));

        typedWord = "wierd";
        // For this test case, we consider "wierd" is a distracter to "weird".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(EMPTY_PREV_WORD, typedWord));

        typedWord = "hoe";
        // For this test case, we consider "hoe" is a distracter to "how".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(EMPTY_PREV_WORD, typedWord));

        typedWord = "nit";
        // For this test case, we consider "nit" is a distracter to "not".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(EMPTY_PREV_WORD, typedWord));

        typedWord = "ill";
        // For this test case, we consider "ill" is a distracter to "I'll".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(EMPTY_PREV_WORD, typedWord));

        typedWord = "asdfd";
        // For this test case, we consider "asdfd" is not a distracter to any word in dictionaries.
        assertFalse(
                mDistracterFilter.isDistracterToWordsInDictionaries(EMPTY_PREV_WORD, typedWord));

        typedWord = "thank";
        // For this test case, we consider "thank" is not a distracter to any other word
        // in dictionaries.
        assertFalse(
                mDistracterFilter.isDistracterToWordsInDictionaries(EMPTY_PREV_WORD, typedWord));
    }
}
