/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.inputmethod.keyboard.Keyboard;

public class InputLogicTests extends InputTestsBase {

    public void testTypeWord() {
        final String WORD_TO_TYPE = "abcd";
        type(WORD_TO_TYPE);
        assertEquals("type word", WORD_TO_TYPE, mTextView.getText().toString());
    }

    public void testPickSuggestionThenBackspace() {
        final String WORD_TO_TYPE = "this";
        final String EXPECTED_RESULT = "thi";
        type(WORD_TO_TYPE);
        pickSuggestionManually(0, WORD_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
        type(Keyboard.CODE_DELETE);
        assertEquals("press suggestion then backspace", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testPickAutoCorrectionThenBackspace() {
        final String WORD_TO_TYPE = "tgis";
        final String WORD_TO_PICK = "this";
        final String EXPECTED_RESULT = "thi";
        type(WORD_TO_TYPE);
        // Choose the auto-correction, which is always in position 0. For "tgis", the
        // auto-correction should be "this".
        pickSuggestionManually(0, WORD_TO_PICK);
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
        assertEquals("pick typed word over auto-correction then backspace", WORD_TO_PICK,
                mTextView.getText().toString());
        type(Keyboard.CODE_DELETE);
        assertEquals("pick typed word over auto-correction then backspace", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testPickTypedWordOverAutoCorrectionThenBackspace() {
        final String WORD_TO_TYPE = "tgis";
        final String EXPECTED_RESULT = "tgi";
        type(WORD_TO_TYPE);
        // Choose the typed word, which should be in position 1 (because position 0 should
        // be occupied by the "this" auto-correction, as checked by testAutoCorrect())
        pickSuggestionManually(1, WORD_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
        assertEquals("pick typed word over auto-correction then backspace", WORD_TO_TYPE,
                mTextView.getText().toString());
        type(Keyboard.CODE_DELETE);
        assertEquals("pick typed word over auto-correction then backspace", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testPickDifferentSuggestionThenBackspace() {
        final String WORD_TO_TYPE = "tgis";
        final String WORD_TO_PICK = "thus";
        final String EXPECTED_RESULT = "thu";
        type(WORD_TO_TYPE);
        // Choose the second suggestion, which should be in position 2 and should be "thus"
        // when "tgis is typed.
        pickSuggestionManually(2, WORD_TO_PICK);
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
        assertEquals("pick different suggestion then backspace", WORD_TO_PICK,
                mTextView.getText().toString());
        type(Keyboard.CODE_DELETE);
        assertEquals("pick different suggestion then backspace", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testDeleteSelection() {
        final String STRING_TO_TYPE = "some text delete me some text";
        final int SELECTION_START = 10;
        final int SELECTION_END = 19;
        final String EXPECTED_RESULT = "some text  some text";
        type(STRING_TO_TYPE);
        // There is no IMF to call onUpdateSelection for us so we must do it by hand.
        // Send once to simulate the cursor actually responding to the move caused by typing.
        // This is necessary because LatinIME is bookkeeping to avoid confusing a real cursor
        // move with a move triggered by LatinIME inputting stuff.
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        mInputConnection.setSelection(SELECTION_START, SELECTION_END);
        // And now we simulate the user actually selecting some text.
        mLatinIME.onUpdateSelection(0, 0, SELECTION_START, SELECTION_END, -1, -1);
        type(Keyboard.CODE_DELETE);
        assertEquals("delete selection", EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testAutoCorrect() {
        final String STRING_TO_TYPE = "tgis ";
        final String EXPECTED_RESULT = "this ";
        type(STRING_TO_TYPE);
        assertEquals("simple auto-correct", EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testAutoCorrectWithPeriod() {
        final String STRING_TO_TYPE = "tgis.";
        final String EXPECTED_RESULT = "this.";
        type(STRING_TO_TYPE);
        assertEquals("auto-correct with period", EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testAutoCorrectWithPeriodThenRevert() {
        final String STRING_TO_TYPE = "tgis.";
        final String EXPECTED_RESULT = "tgis.";
        type(STRING_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        type(Keyboard.CODE_DELETE);
        assertEquals("auto-correct with period then revert", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testDoubleSpace() {
        final String STRING_TO_TYPE = "this  ";
        final String EXPECTED_RESULT = "this. ";
        type(STRING_TO_TYPE);
        assertEquals("double space make a period", EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testCancelDoubleSpace() {
        final String STRING_TO_TYPE = "this  ";
        final String EXPECTED_RESULT = "this  ";
        type(STRING_TO_TYPE);
        type(Keyboard.CODE_DELETE);
        assertEquals("double space make a period", EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testBackspaceAtStartAfterAutocorrect() {
        final String STRING_TO_TYPE = "tgis ";
        final String EXPECTED_RESULT = "this ";
        final int NEW_CURSOR_POSITION = 0;
        type(STRING_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        mInputConnection.setSelection(NEW_CURSOR_POSITION, NEW_CURSOR_POSITION);
        mLatinIME.onUpdateSelection(0, 0, NEW_CURSOR_POSITION, NEW_CURSOR_POSITION, -1, -1);
        type(Keyboard.CODE_DELETE);
        assertEquals("auto correct then move cursor to start of line then backspace",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testAutoCorrectThenMoveCursorThenBackspace() {
        final String STRING_TO_TYPE = "and tgis ";
        final String EXPECTED_RESULT = "andthis ";
        final int NEW_CURSOR_POSITION = STRING_TO_TYPE.indexOf('t');
        type(STRING_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        mInputConnection.setSelection(NEW_CURSOR_POSITION, NEW_CURSOR_POSITION);
        mLatinIME.onUpdateSelection(0, 0, NEW_CURSOR_POSITION, NEW_CURSOR_POSITION, -1, -1);
        type(Keyboard.CODE_DELETE);
        assertEquals("auto correct then move cursor then backspace",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testNoSpaceAfterManualPick() {
        final String WORD_TO_TYPE = "this";
        final String EXPECTED_RESULT = WORD_TO_TYPE;
        type(WORD_TO_TYPE);
        pickSuggestionManually(0, WORD_TO_TYPE);
        assertEquals("no space after manual pick", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testManualPickThenType() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = "is";
        final String EXPECTED_RESULT = "this is";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(0, WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then type", EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testManualPickThenSeparator() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = "!";
        final String EXPECTED_RESULT = "this!";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(0, WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then separator", EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testManualPickThenSpaceThenType() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = " is";
        final String EXPECTED_RESULT = "this is";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(0, WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then space then type", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testManualPickThenManualPick() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_PICK = "is";
        final String EXPECTED_RESULT = "this is";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(0, WORD1_TO_TYPE);
        // Here we fake picking a word through bigram prediction. This test is taking
        // advantage of the fact that Latin IME blindly trusts the caller of #pickSuggestionManually
        // to actually pass the right string.
        pickSuggestionManually(1, WORD2_TO_PICK);
        assertEquals("manual pick then manual pick", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testDeleteWholeComposingWord() {
        final String WORD_TO_TYPE = "this";
        type(WORD_TO_TYPE);
        for (int i = 0; i < WORD_TO_TYPE.length(); ++i) {
            type(Keyboard.CODE_DELETE);
        }
        assertEquals("delete whole composing word", "", mTextView.getText().toString());
    }
    // TODO: Add some tests for non-BMP characters
}
