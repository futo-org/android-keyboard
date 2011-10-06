/*
 * Copyright (C) 2010,2011 The Android Open Source Project
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

import com.android.inputmethod.latin.tests.R;

import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;

import java.util.Locale;

public class SuggestTests extends SuggestTestsBase {
    private SuggestHelper mHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final AssetFileDescriptor dict = openTestRawResourceFd(R.raw.test);
        final Locale locale = Locale.US;
        mHelper = new SuggestHelper(
                getContext(), mTestPackageFile, dict.getStartOffset(), dict.getLength(),
                createKeyboardId(locale, Configuration.ORIENTATION_PORTRAIT), locale);
        mHelper.setCorrectionMode(Suggest.CORRECTION_FULL_BIGRAM);
    }

    /************************** Tests ************************/

    /**
     * Tests for simple completions of one character.
     */
    public void testCompletion1char() {
        suggested("people", mHelper.getFirstSuggestion("peopl"));
        suggested("about", mHelper.getFirstSuggestion("abou"));
        suggested("their", mHelper.getFirstSuggestion("thei"));
    }

    /**
     * Tests for simple completions of two characters.
     */
    public void testCompletion2char() {
        suggested("people", mHelper.getFirstSuggestion("peop"));
        suggested("calling", mHelper.getFirstSuggestion("calli"));
        suggested("business", mHelper.getFirstSuggestion("busine"));
    }

    /**
     * Tests for proximity errors.
     */
    public void testProximityPositive() {
        suggested("typed peiple", "people", mHelper.getFirstSuggestion("peiple"));
        suggested("typed peoole", "people", mHelper.getFirstSuggestion("peoole"));
        suggested("typed pwpple", "people", mHelper.getFirstSuggestion("pwpple"));
    }

    /**
     * Tests for proximity errors - negative, when the error key is not close.
     */
    public void testProximityNegative() {
        notSuggested("about", mHelper.getFirstSuggestion("arout"));
        notSuggested("are", mHelper.getFirstSuggestion("ire"));
    }

    /**
     * Tests for checking if apostrophes are added automatically.
     */
    public void testApostropheInsertion() {
        suggested("I'm", mHelper.getFirstSuggestion("im"));
        suggested("don't", mHelper.getFirstSuggestion("dont"));
    }

    /**
     * Test to make sure apostrophed word is not suggested for an apostrophed word.
     */
    public void testApostrophe() {
        notSuggested("don't", mHelper.getFirstSuggestion("don't"));
    }

    /**
     * Tests for suggestion of capitalized version of a word.
     */
    public void testCapitalization() {
        suggested("I'm", mHelper.getFirstSuggestion("i'm"));
        suggested("Sunday", mHelper.getFirstSuggestion("sunday"));
        suggested("Sunday", mHelper.getFirstSuggestion("sundat"));
    }

    /**
     * Tests to see if more than one completion is provided for certain prefixes.
     */
    public void testMultipleCompletions() {
        isInSuggestions("com: come", mHelper.getSuggestIndex("com", "come"));
        isInSuggestions("com: company", mHelper.getSuggestIndex("com", "company"));
        isInSuggestions("th: the", mHelper.getSuggestIndex("th", "the"));
        isInSuggestions("th: that", mHelper.getSuggestIndex("th", "that"));
        isInSuggestions("th: this", mHelper.getSuggestIndex("th", "this"));
        isInSuggestions("th: they", mHelper.getSuggestIndex("th", "they"));
    }

    /**
     * Does the suggestion engine recognize zero frequency words as valid words.
     */
    public void testZeroFrequencyAccepted() {
        assertTrue("valid word yikes", mHelper.isValidWord("yikes"));
        assertFalse("non valid word yike", mHelper.isValidWord("yike"));
    }

    /**
     * Tests to make sure that zero frequency words are not suggested as completions.
     */
    public void testZeroFrequencySuggestionsNegative() {
        assertTrue(mHelper.getSuggestIndex("yike", "yikes") < 0);
        assertTrue(mHelper.getSuggestIndex("what", "whatcha") < 0);
    }

    /**
     * Tests to ensure that words with large edit distances are not suggested, in some cases.
     * Also such word is not considered auto correction, in some cases.
     */
    public void testTooLargeEditDistance() {
        assertTrue(mHelper.getSuggestIndex("sniyr", "about") < 0);
        // TODO: The following test fails.
        // notSuggested("the", mHelper.getAutoCorrection("rjw"));
    }

    /**
     * Make sure mHelper.isValidWord is case-sensitive.
     */
    public void testValidityCaseSensitivity() {
        assertTrue("valid word Sunday", mHelper.isValidWord("Sunday"));
        assertFalse("non valid word sunday", mHelper.isValidWord("sunday"));
    }

    /**
     * Are accented forms of words suggested as corrections?
     */
    public void testAccents() {
        // ni<LATIN SMALL LETTER N WITH TILDE>o
        suggested("ni\u00F1o", mHelper.getAutoCorrection("nino"));
        // ni<LATIN SMALL LETTER N WITH TILDE>o
        suggested("ni\u00F1o", mHelper.getAutoCorrection("nimo"));
        // Mar<LATIN SMALL LETTER I WITH ACUTE>a
        suggested("Mar\u00EDa", mHelper.getAutoCorrection("maria"));
    }

    /**
     * Make sure bigrams are showing when first character is typed
     *  and don't show any when there aren't any
     */
    public void testBigramsAtFirstChar() {
        suggested("bigram: about p[art]",
                "part", mHelper.getBigramFirstSuggestion("about", "p"));
        suggested("bigram: I'm a[bout]",
                "about", mHelper.getBigramFirstSuggestion("I'm", "a"));
        suggested("bigram: about b[usiness]",
                "business", mHelper.getBigramFirstSuggestion("about", "b"));
        isInSuggestions("bigram: about b[eing]",
                mHelper.searchBigramSuggestion("about", "b", "being"));
        notSuggested("bigram: about p",
                "business", mHelper.getBigramFirstSuggestion("about", "p"));
    }

    /**
     * Make sure bigrams score affects the original score
     */
    public void testBigramsScoreEffect() {
        suggested("single: page",
                "page", mHelper.getAutoCorrection("pa"));
        suggested("bigram: about pa[rt]",
                "part", mHelper.getBigramAutoCorrection("about", "pa"));
        // TODO: The following test fails.
        // suggested("single: said", "said", mHelper.getAutoCorrection("sa"));
        suggested("bigram: from sa[me]",
                "same", mHelper.getBigramAutoCorrection("from", "sa"));
    }
}
