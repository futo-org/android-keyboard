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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.ExpandableBinaryDictionary;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

/**
 * Unit tests for contextual dictionary
 */
@LargeTest
public class ContextualDictionaryTests extends AndroidTestCase {
    private static final String TAG = ContextualDictionaryTests.class.getSimpleName();

    private static final Locale LOCALE_EN_US = new Locale("en", "US");

    private DictionaryFacilitator getDictionaryFacilitator() {
        final ArrayList<String> dictTypes = new ArrayList<>();
        dictTypes.add(Dictionary.TYPE_CONTEXTUAL);
        final DictionaryFacilitator dictionaryFacilitator = new DictionaryFacilitator();
        dictionaryFacilitator.resetDictionariesForTesting(getContext(), LOCALE_EN_US, dictTypes,
                new HashMap<String, File>(), new HashMap<String, Map<String, String>>());
        return dictionaryFacilitator;
    }

    public void testAddPhrase() {
        final DictionaryFacilitator dictionaryFacilitator = getDictionaryFacilitator();
        final String[] phrase = new String[] {"a", "b", "c", "d"};
        final int probability = 100;
        final int bigramProbabilityForWords = 150;
        final int bigramProbabilityForPhrases = 200;
        dictionaryFacilitator.addPhraseToContextualDictionary(
                phrase, probability, bigramProbabilityForWords, bigramProbabilityForPhrases);
        final ExpandableBinaryDictionary contextualDictionary =
                dictionaryFacilitator.getSubDictForTesting(Dictionary.TYPE_CONTEXTUAL);
        contextualDictionary.waitAllTasksForTests();
        // Word
        assertTrue(contextualDictionary.isInDictionary("a"));
        assertTrue(contextualDictionary.isInDictionary("b"));
        assertTrue(contextualDictionary.isInDictionary("c"));
        assertTrue(contextualDictionary.isInDictionary("d"));
        // Phrase
        assertTrue(contextualDictionary.isInDictionary("a b c d"));
        assertTrue(contextualDictionary.isInDictionary("b c d"));
        assertTrue(contextualDictionary.isInDictionary("c d"));
        assertFalse(contextualDictionary.isInDictionary("a b c"));
        assertFalse(contextualDictionary.isInDictionary("abcd"));
        // TODO: Add tests for probability.
        // TODO: Add tests for n-grams.
    }
}
