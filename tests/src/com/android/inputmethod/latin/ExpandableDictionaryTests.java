/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Unit test for ExpandableDictionary
 */
@SmallTest
public class ExpandableDictionaryTests extends AndroidTestCase {

    private final static int UNIGRAM_FREQ = 50;
    // See UserBinaryDictionary for more information about this variable.
    // For tests, its actual value does not matter.
    private final static int SHORTCUT_FREQ = 14;

    public void testAddWordAndGetWordFrequency() {
        final ExpandableDictionary dict = new ExpandableDictionary(Dictionary.TYPE_USER);

        // Add words
        dict.addWord("abcde", "abcde", UNIGRAM_FREQ, SHORTCUT_FREQ);
        dict.addWord("abcef", null, UNIGRAM_FREQ + 1, 0);

        // Check words
        assertFalse(dict.isValidWord("abcde"));
        assertEquals(UNIGRAM_FREQ, dict.getWordFrequency("abcde"));
        assertTrue(dict.isValidWord("abcef"));
        assertEquals(UNIGRAM_FREQ+1, dict.getWordFrequency("abcef"));

        dict.addWord("abc", null, UNIGRAM_FREQ + 2, 0);
        assertTrue(dict.isValidWord("abc"));
        assertEquals(UNIGRAM_FREQ + 2, dict.getWordFrequency("abc"));

        // Add existing word with lower frequency
        dict.addWord("abc", null, UNIGRAM_FREQ, 0);
        assertEquals(UNIGRAM_FREQ + 2, dict.getWordFrequency("abc"));

        // Add existing word with higher frequency
        dict.addWord("abc", null, UNIGRAM_FREQ + 3, 0);
        assertEquals(UNIGRAM_FREQ + 3, dict.getWordFrequency("abc"));
    }
}
