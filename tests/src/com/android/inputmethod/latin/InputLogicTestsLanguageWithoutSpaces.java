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

import android.test.suitebuilder.annotation.LargeTest;
import android.view.inputmethod.BaseInputConnection;

import com.android.inputmethod.latin.suggestions.SuggestionStripView;

@LargeTest
public class InputLogicTestsLanguageWithoutSpaces extends InputTestsBase {
    public void testAutoCorrectForLanguageWithoutSpaces() {
        final String STRING_TO_TYPE = "tgis is";
        final String EXPECTED_RESULT = "thisis";
        changeKeyboardLocaleAndDictLocale("th", "en_US");
        type(STRING_TO_TYPE);
        assertEquals("simple auto-correct for language without spaces", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testRevertAutoCorrectForLanguageWithoutSpaces() {
        final String STRING_TO_TYPE = "tgis ";
        final String EXPECTED_INTERMEDIATE_RESULT = "this";
        final String EXPECTED_FINAL_RESULT = "tgis";
        changeKeyboardLocaleAndDictLocale("th", "en_US");
        type(STRING_TO_TYPE);
        assertEquals("simple auto-correct for language without spaces",
                EXPECTED_INTERMEDIATE_RESULT, mEditText.getText().toString());
        type(Constants.CODE_DELETE);
        assertEquals("simple auto-correct for language without spaces",
                EXPECTED_FINAL_RESULT, mEditText.getText().toString());
        // Check we are back to composing the word
        assertEquals("don't resume suggestion on backspace", 0,
                BaseInputConnection.getComposingSpanStart(mEditText.getText()));
        assertEquals("don't resume suggestion on backspace", 4,
                BaseInputConnection.getComposingSpanEnd(mEditText.getText()));
    }

    public void testDontResumeSuggestionOnBackspace() {
        final String WORD_TO_TYPE = "and this ";
        changeKeyboardLocaleAndDictLocale("th", "en_US");
        type(WORD_TO_TYPE);
        assertEquals("don't resume suggestion on backspace", -1,
                BaseInputConnection.getComposingSpanStart(mEditText.getText()));
        assertEquals("don't resume suggestion on backspace", -1,
                BaseInputConnection.getComposingSpanEnd(mEditText.getText()));
        type(" ");
        type(Constants.CODE_DELETE);
        assertEquals("don't resume suggestion on backspace", -1,
                BaseInputConnection.getComposingSpanStart(mEditText.getText()));
        assertEquals("don't resume suggestion on backspace", -1,
                BaseInputConnection.getComposingSpanEnd(mEditText.getText()));
    }

    public void testStartComposingInsideText() {
        final String WORD_TO_TYPE = "abcdefgh ";
        final int typedLength = WORD_TO_TYPE.length() - 1; // -1 because space gets eaten
        final int CURSOR_POS = 4;
        changeKeyboardLocaleAndDictLocale("th", "en_US");
        type(WORD_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, typedLength, typedLength, -1, -1);
        mInputConnection.setSelection(CURSOR_POS, CURSOR_POS);
        mLatinIME.onUpdateSelection(typedLength, typedLength,
                CURSOR_POS, CURSOR_POS, -1, -1);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        assertEquals("start composing inside text", -1,
                BaseInputConnection.getComposingSpanStart(mEditText.getText()));
        assertEquals("start composing inside text", -1,
                BaseInputConnection.getComposingSpanEnd(mEditText.getText()));
        type("xxxx");
        assertEquals("start composing inside text", 4,
                BaseInputConnection.getComposingSpanStart(mEditText.getText()));
        assertEquals("start composing inside text", 8,
                BaseInputConnection.getComposingSpanEnd(mEditText.getText()));
    }

    public void testPredictions() {
        final String WORD_TO_TYPE = "Barack ";
        changeKeyboardLocaleAndDictLocale("th", "en_US");
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        // Make sure there is no space
        assertEquals("predictions in lang without spaces", "Barack",
                mEditText.getText().toString());
        // Test the first prediction is displayed
        assertEquals("predictions in lang without spaces", "Obama",
                mLatinIME.getFirstSuggestedWord());
    }
}
