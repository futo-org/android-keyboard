/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.test.AndroidTestCase;
import com.android.inputmethod.latin.tests.R;

public class SuggestTests extends AndroidTestCase {
    private static final String TAG = "SuggestTests";

    private SuggestHelper sh;

    @Override
    protected void setUp() {
        int[] resId = new int[] { R.raw.test };
        sh = new SuggestHelper(TAG, getTestContext(), resId);
    }

    /************************** Tests ************************/

    /**
     * Tests for simple completions of one character.
     */
    public void testCompletion1char() {
        assertTrue(sh.isDefaultSuggestion("peopl", "people"));
        assertTrue(sh.isDefaultSuggestion("abou", "about"));
        assertTrue(sh.isDefaultSuggestion("thei", "their"));
    }

    /**
     * Tests for simple completions of two characters.
     */
    public void testCompletion2char() {
        assertTrue(sh.isDefaultSuggestion("peop", "people"));
        assertTrue(sh.isDefaultSuggestion("calli", "calling"));
        assertTrue(sh.isDefaultSuggestion("busine", "business"));
    }

    /**
     * Tests for proximity errors.
     */
    public void testProximityPositive() {
        assertTrue(sh.isDefaultSuggestion("peiple", "people"));
        assertTrue(sh.isDefaultSuggestion("peoole", "people"));
        assertTrue(sh.isDefaultSuggestion("pwpple", "people"));
    }

    /**
     * Tests for proximity errors - negative, when the error key is not near.
     */
    public void testProximityNegative() {
        assertFalse(sh.isDefaultSuggestion("arout", "about"));
        assertFalse(sh.isDefaultSuggestion("ire", "are"));
    }

    /**
     * Tests for checking if apostrophes are added automatically.
     */
    public void testApostropheInsertion() {
        assertTrue(sh.isDefaultSuggestion("im", "I'm"));
        assertTrue(sh.isDefaultSuggestion("dont", "don't"));
    }

    /**
     * Test to make sure apostrophed word is not suggested for an apostrophed word.
     */
    public void testApostrophe() {
        assertFalse(sh.isDefaultSuggestion("don't", "don't"));
    }

    /**
     * Tests for suggestion of capitalized version of a word.
     */
    public void testCapitalization() {
        assertTrue(sh.isDefaultSuggestion("i'm", "I'm"));
        assertTrue(sh.isDefaultSuggestion("sunday", "Sunday"));
        assertTrue(sh.isDefaultSuggestion("sundat", "Sunday"));
    }

    /**
     * Tests to see if more than one completion is provided for certain prefixes.
     */
    public void testMultipleCompletions() {
        assertTrue(sh.isASuggestion("com", "come"));
        assertTrue(sh.isASuggestion("com", "company"));
        assertTrue(sh.isASuggestion("th", "the"));
        assertTrue(sh.isASuggestion("th", "that"));
        assertTrue(sh.isASuggestion("th", "this"));
        assertTrue(sh.isASuggestion("th", "they"));
    }

    /**
     * Does the suggestion engine recognize zero frequency words as valid words.
     */
    public void testZeroFrequencyAccepted() {
        assertTrue(sh.isValid("yikes"));
        assertFalse(sh.isValid("yike"));
    }

    /**
     * Tests to make sure that zero frequency words are not suggested as completions.
     */
    public void testZeroFrequencySuggestionsNegative() {
        assertFalse(sh.isASuggestion("yike", "yikes"));
        assertFalse(sh.isASuggestion("what", "whatcha"));
    }

    /**
     * Tests to ensure that words with large edit distances are not suggested, in some cases
     * and not considered corrections, in some cases.
     */
    public void testTooLargeEditDistance() {
        assertFalse(sh.isASuggestion("sniyr", "about"));
        assertFalse(sh.isDefaultCorrection("rjw", "the"));
    }

    /**
     * Make sure sh.isValid is case-sensitive.
     */
    public void testValidityCaseSensitivity() {
        assertTrue(sh.isValid("Sunday"));
        assertFalse(sh.isValid("sunday"));
    }

    /**
     * Are accented forms of words suggested as corrections?
     */
    public void testAccents() {
        // ni<LATIN SMALL LETTER N WITH TILDE>o
        assertTrue(sh.isDefaultCorrection("nino", "ni\u00F1o"));
        // ni<LATIN SMALL LETTER N WITH TILDE>o
        assertTrue(sh.isDefaultCorrection("nimo", "ni\u00F1o"));
        // Mar<LATIN SMALL LETTER I WITH ACUTE>a
        assertTrue(sh.isDefaultCorrection("maria", "Mar\u00EDa"));
    }

    /**
     * Make sure bigrams are showing when first character is typed
     *  and don't show any when there aren't any
     */
    public void testBigramsAtFirstChar() {
        assertTrue(sh.isDefaultNextSuggestion("about", "p", "part"));
        assertTrue(sh.isDefaultNextSuggestion("I'm", "a", "about"));
        assertTrue(sh.isDefaultNextSuggestion("about", "b", "business"));
        assertTrue(sh.isASuggestion("about", "b", "being"));
        assertFalse(sh.isDefaultNextSuggestion("about", "p", "business"));
    }

    /**
     * Make sure bigrams score affects the original score
     */
    public void testBigramsScoreEffect() {
        assertTrue(sh.isDefaultCorrection("pa", "page"));
        assertTrue(sh.isDefaultNextCorrection("about", "pa", "part"));
        assertTrue(sh.isDefaultCorrection("sa", "said"));
        assertTrue(sh.isDefaultNextCorrection("from", "sa", "same"));
    }
}
