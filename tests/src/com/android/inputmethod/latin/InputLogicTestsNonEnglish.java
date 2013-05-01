/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.inputmethod.latin.suggestions.SuggestionStripView;

@LargeTest
public class InputLogicTestsNonEnglish extends InputTestsBase {
    final String NEXT_WORD_PREDICTION_OPTION = "next_word_prediction";

    public void testAutoCorrectForFrench() {
        final String STRING_TO_TYPE = "irq ";
        final String EXPECTED_RESULT = "ira ";
        changeLanguage("fr");
        type(STRING_TO_TYPE);
        assertEquals("simple auto-correct for French", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testManualPickThenSeparatorForFrench() {
        final String WORD1_TO_TYPE = "test";
        final String WORD2_TO_TYPE = "!";
        final String EXPECTED_RESULT = "test !";
        changeLanguage("fr");
        type(WORD1_TO_TYPE);
        pickSuggestionManually(0, WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then separator for French", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testWordThenSpaceThenPunctuationFromStripTwiceForFrench() {
        final String WORD_TO_TYPE = "test ";
        final String PUNCTUATION_FROM_STRIP = "!";
        final String EXPECTED_RESULT = "test !!";
        final boolean defaultNextWordPredictionOption =
                mLatinIME.getResources().getBoolean(R.bool.config_default_next_word_prediction);
        final boolean previousNextWordPredictionOption =
                setBooleanPreference(NEXT_WORD_PREDICTION_OPTION, false,
                        defaultNextWordPredictionOption);
        try {
            changeLanguage("fr");
            type(WORD_TO_TYPE);
            sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
            runMessages();
            assertTrue("type word then type space should display punctuation strip",
                    mLatinIME.isShowingPunctuationList());
            pickSuggestionManually(0, PUNCTUATION_FROM_STRIP);
            pickSuggestionManually(0, PUNCTUATION_FROM_STRIP);
            assertEquals("type word then type space then punctuation from strip twice for French",
                    EXPECTED_RESULT, mEditText.getText().toString());
        } finally {
            setBooleanPreference(NEXT_WORD_PREDICTION_OPTION, previousNextWordPredictionOption,
                    defaultNextWordPredictionOption);
        }
    }

    public void testWordThenSpaceDisplaysPredictions() {
        final String WORD_TO_TYPE = "beaujolais ";
        final String EXPECTED_RESULT = "nouveau";
        final boolean defaultNextWordPredictionOption =
                mLatinIME.getResources().getBoolean(R.bool.config_default_next_word_prediction);
        final boolean previousNextWordPredictionOption =
                setBooleanPreference(NEXT_WORD_PREDICTION_OPTION, true,
                        defaultNextWordPredictionOption);
        try {
            changeLanguage("fr");
            type(WORD_TO_TYPE);
            sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
            runMessages();
            assertEquals("type word then type space yields predictions for French",
                    EXPECTED_RESULT, mLatinIME.getFirstSuggestedWord());
        } finally {
            setBooleanPreference(NEXT_WORD_PREDICTION_OPTION, previousNextWordPredictionOption,
                    defaultNextWordPredictionOption);
        }
    }

    public void testAutoCorrectForGerman() {
        final String STRING_TO_TYPE = "unf ";
        final String EXPECTED_RESULT = "und ";
        changeLanguage("de");
        type(STRING_TO_TYPE);
        assertEquals("simple auto-correct for German", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testAutoCorrectWithUmlautForGerman() {
        final String STRING_TO_TYPE = "ueber ";
        final String EXPECTED_RESULT = "über ";
        changeLanguage("de");
        type(STRING_TO_TYPE);
        assertEquals("auto-correct with umlaut for German", EXPECTED_RESULT,
                mEditText.getText().toString());
    }
}
