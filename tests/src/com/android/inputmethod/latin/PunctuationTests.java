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

import android.provider.Settings.Secure;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.inputmethod.latin.R;

@LargeTest
public class PunctuationTests extends InputTestsBase {

    final String NEXT_WORD_PREDICTION_OPTION = "next_word_prediction";

    public void testWordThenSpaceThenPunctuationFromStripTwice() {
        final String WORD_TO_TYPE = "this ";
        final String PUNCTUATION_FROM_STRIP = "!";
        final String EXPECTED_RESULT = "this!! ";
        final boolean defaultNextWordPredictionOption =
                mLatinIME.getResources().getBoolean(R.bool.config_default_next_word_prediction);
        final boolean previousNextWordPredictionOption =
                setBooleanPreference(NEXT_WORD_PREDICTION_OPTION, false,
                        defaultNextWordPredictionOption);
        try {
            mLatinIME.loadSettings();
            type(WORD_TO_TYPE);
            sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
            runMessages();
            assertTrue("type word then type space should display punctuation strip",
                    mLatinIME.getSuggestedWordsForTest().isPunctuationSuggestions());
            pickSuggestionManually(PUNCTUATION_FROM_STRIP);
            pickSuggestionManually(PUNCTUATION_FROM_STRIP);
            assertEquals("type word then type space then punctuation from strip twice",
                    EXPECTED_RESULT, mEditText.getText().toString());
        } finally {
            setBooleanPreference(NEXT_WORD_PREDICTION_OPTION, previousNextWordPredictionOption,
                    defaultNextWordPredictionOption);
        }
    }

    public void testWordThenSpaceThenPunctuationFromKeyboardTwice() {
        final String WORD_TO_TYPE = "this !!";
        final String EXPECTED_RESULT = "this !!";
        type(WORD_TO_TYPE);
        assertEquals("manual pick then space then punctuation from keyboard twice", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testManualPickThenPunctuationFromStripTwiceThenType() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = "is";
        final String PUNCTUATION_FROM_STRIP = "!";
        final String EXPECTED_RESULT = "this!! is";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(WORD1_TO_TYPE);
        pickSuggestionManually(PUNCTUATION_FROM_STRIP);
        pickSuggestionManually(PUNCTUATION_FROM_STRIP);
        type(WORD2_TO_TYPE);
        assertEquals("pick word then pick punctuation twice then type", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testManualPickThenManualPickWithPunctAtStart() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_PICK = "!is";
        final String EXPECTED_RESULT = "this!is";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(WORD1_TO_TYPE);
        pickSuggestionManually(WORD2_TO_PICK);
        assertEquals("manual pick then manual pick a word with punct at start", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testManuallyPickedWordThenColon() {
        final String WORD_TO_TYPE = "this";
        final String PUNCTUATION = ":";
        final String EXPECTED_RESULT = "this:";
        type(WORD_TO_TYPE);
        pickSuggestionManually(WORD_TO_TYPE);
        type(PUNCTUATION);
        assertEquals("manually pick word then colon",
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testManuallyPickedWordThenOpenParen() {
        final String WORD_TO_TYPE = "this";
        final String PUNCTUATION = "(";
        final String EXPECTED_RESULT = "this (";
        type(WORD_TO_TYPE);
        pickSuggestionManually(WORD_TO_TYPE);
        type(PUNCTUATION);
        assertEquals("manually pick word then open paren",
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testManuallyPickedWordThenCloseParen() {
        final String WORD_TO_TYPE = "this";
        final String PUNCTUATION = ")";
        final String EXPECTED_RESULT = "this)";
        type(WORD_TO_TYPE);
        pickSuggestionManually(WORD_TO_TYPE);
        type(PUNCTUATION);
        assertEquals("manually pick word then close paren",
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testManuallyPickedWordThenSmiley() {
        final String WORD_TO_TYPE = "this";
        final String SPECIAL_KEY = ":-)";
        final String EXPECTED_RESULT = "this :-)";
        type(WORD_TO_TYPE);
        pickSuggestionManually(WORD_TO_TYPE);
        mLatinIME.onTextInput(SPECIAL_KEY);
        assertEquals("manually pick word then press the smiley key",
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testManuallyPickedWordThenDotCom() {
        final String WORD_TO_TYPE = "this";
        final String SPECIAL_KEY = ".com";
        final String EXPECTED_RESULT = "this.com";
        type(WORD_TO_TYPE);
        pickSuggestionManually(WORD_TO_TYPE);
        mLatinIME.onTextInput(SPECIAL_KEY);
        assertEquals("manually pick word then press the .com key",
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testTypeWordTypeDotThenPressDotCom() {
        final String WORD_TO_TYPE = "this.";
        final String SPECIAL_KEY = ".com";
        final String EXPECTED_RESULT = "this.com";
        type(WORD_TO_TYPE);
        mLatinIME.onTextInput(SPECIAL_KEY);
        assertEquals("type word type dot then press the .com key",
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testAutoCorrectionWithSingleQuoteInside() {
        final String WORD_TO_TYPE = "you'f ";
        final String EXPECTED_RESULT = "you'd ";
        type(WORD_TO_TYPE);
        assertEquals("auto-correction with single quote inside. ID = "
                + Secure.getString(getContext().getContentResolver(), Secure.ANDROID_ID)
                + " ; Suggestions = " + mLatinIME.getSuggestedWordsForTest(),
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testAutoCorrectionWithSingleQuotesAround() {
        final String WORD_TO_TYPE = "'tgis' ";
        final String EXPECTED_RESULT = "'this' ";
        type(WORD_TO_TYPE);
        assertEquals("auto-correction with single quotes around. ID = "
                + Secure.getString(getContext().getContentResolver(), Secure.ANDROID_ID)
                + " ; Suggestions = " + mLatinIME.getSuggestedWordsForTest(),
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testAutoSpaceWithDoubleQuotes() {
        final String STRING_TO_TYPE = "He said\"hello\"to me. I replied,\"hi\"."
                + "Then, 5\"passed. He said\"bye\"and left.";
        final String EXPECTED_RESULT = "He said \"hello\" to me. I replied, \"hi\". "
                + "Then, 5\" passed. He said \"bye\" and left. \"";
        // Split by double quote, so that we can type the double quotes individually.
        for (final String partToType : STRING_TO_TYPE.split("\"")) {
            // Split at word boundaries. This regexp means "anywhere that is preceded
            // by a word character but not followed by a word character, OR that is not
            // preceded by a word character but followed by a word character".
            // We need to input word by word because auto-spaces are only active when
            // manually picking or gesturing (which we can't simulate yet), but only words
            // can be picked.
            final String[] wordsToType = partToType.split("(?<=\\w)(?!\\w)|(?<!\\w)(?=\\w)");
            for (final String wordToType : wordsToType) {
                type(wordToType);
                if (wordToType.matches("^\\w+$")) {
                    // Only pick selection if that was a word, because if that was not a word,
                    // then we don't have a composition.
                    pickSuggestionManually(wordToType);
                }
            }
            type("\"");
        }
        assertEquals("auto-space with double quotes",
                EXPECTED_RESULT, mEditText.getText().toString());
    }
}
