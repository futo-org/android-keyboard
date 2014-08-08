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

import java.util.ArrayList;
import java.util.Locale;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.utils.DistracterFilterCheckingExactMatchesAndSuggestions;

/**
 * Unit test for DistracterFilter
 */
@LargeTest
public class DistracterFilterTest extends AndroidTestCase {
    private DistracterFilterCheckingExactMatchesAndSuggestions mDistracterFilter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getContext();
        mDistracterFilter = new DistracterFilterCheckingExactMatchesAndSuggestions(context);
        RichInputMethodManager.init(context);
        final RichInputMethodManager richImm = RichInputMethodManager.getInstance();
        final ArrayList<InputMethodSubtype> subtypes = new ArrayList<>();
        subtypes.add(richImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.US.toString(), "qwerty"));
        subtypes.add(richImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.FRENCH.toString(), "azerty"));
        subtypes.add(richImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.GERMAN.toString(), "qwertz"));
        mDistracterFilter.updateEnabledSubtypes(subtypes);
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

        typedWord = "thabk";
        // For this test case, we consider "thabk" is a distracter to "thank"
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "thanks";
        // For this test case, we consider "thanks" is not a distracter to any other word
        // in dictionaries.
        assertFalse(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "thabks";
        // For this test case, we consider "thabks" is a distracter to "thanks"
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "think";
        // For this test case, we consider "think" is not a distracter to any other word
        // in dictionaries.
        assertFalse(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "thibk";
        // For this test case, we consider "thibk" is a distracter to "think"
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        typedWord = "tgis";
        // For this test case, we consider "tgis" is a distracter to "this"
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeEnUs));

        final Locale localeDeDe = new Locale("de");

        typedWord = "fUEr";
        // For this test case, we consider "fUEr" is a distracter to "für".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeDeDe));

        typedWord = "fuer";
        // For this test case, we consider "fuer" is a distracter to "für".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeDeDe));

        typedWord = "fur";
        // For this test case, we consider "fur" is a distracter to "für".
        assertTrue(mDistracterFilter.isDistracterToWordsInDictionaries(
                EMPTY_PREV_WORDS_INFO, typedWord, localeDeDe));

        final Locale localeFrFr = new Locale("fr");

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
