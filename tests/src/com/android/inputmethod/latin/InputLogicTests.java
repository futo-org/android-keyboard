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
        final String EXPECTED_RESULT = "this";
        type(WORD_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
        type(Keyboard.CODE_DELETE);
        assertEquals("press suggestion then backspace", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testPickAutoCorrectionThenBackspace() {
        final String WORD_TO_TYPE = "tgis";
        final String WORD_TO_PICK = "this";
        final String EXPECTED_RESULT = "tgis";
        type(WORD_TO_TYPE);
        // Choose the auto-correction, which is always in position 0. For "tgis", the
        // auto-correction should be "this".
        mLatinIME.pickSuggestionManually(0, WORD_TO_PICK);
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
        assertEquals("pick typed word over auto-correction then backspace", WORD_TO_PICK,
                mTextView.getText().toString());
        type(Keyboard.CODE_DELETE);
        assertEquals("pick typed word over auto-correction then backspace", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testPickTypedWordOverAutoCorrectionThenBackspace() {
        final String WORD_TO_TYPE = "tgis";
        final String EXPECTED_RESULT = "tgis";
        type(WORD_TO_TYPE);
        // Choose the typed word, which should be in position 1 (because position 0 should
        // be occupied by the "this" auto-correction, as checked by testAutoCorrect())
        mLatinIME.pickSuggestionManually(1, WORD_TO_TYPE);
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
        final String EXPECTED_RESULT = "tgis";
        type(WORD_TO_TYPE);
        // Choose the second suggestion, which should be in position 2 and should be "thus"
        // when "tgis is typed.
        mLatinIME.pickSuggestionManually(2, WORD_TO_PICK);
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

    public void testAutoCorrectForFrench() {
        final String STRING_TO_TYPE = "irq ";
        final String EXPECTED_RESULT = "ira ";
        changeLanguage("fr");
        type(STRING_TO_TYPE);
        assertEquals("simple auto-correct for French", EXPECTED_RESULT,
                mTextView.getText().toString());
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
        mLatinIME.pickSuggestionManually(0, WORD_TO_TYPE);
        assertEquals("no space after manual pick", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testManualPickThenType() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = "is";
        final String EXPECTED_RESULT = "this is";
        type(WORD1_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then type", EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testManualPickThenSeparator() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = "!";
        final String EXPECTED_RESULT = "this!";
        type(WORD1_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then separator", EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testWordThenSpaceThenPunctuationFromStripTwice() {
        final String WORD_TO_TYPE = "this ";
        final String PUNCTUATION_FROM_STRIP = "!";
        final String EXPECTED_RESULT = "this!! ";
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        assertTrue("type word then type space should display punctuation strip",
                mLatinIME.isShowingPunctuationList());
        mLatinIME.pickSuggestionManually(0, PUNCTUATION_FROM_STRIP);
        mLatinIME.pickSuggestionManually(0, PUNCTUATION_FROM_STRIP);
        assertEquals("type word then type space then punctuation from strip twice", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testManualPickThenSeparatorForFrench() {
        final String WORD1_TO_TYPE = "test";
        final String WORD2_TO_TYPE = "!";
        final String EXPECTED_RESULT = "test !";
        changeLanguage("fr");
        type(WORD1_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then separator for French", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testWordThenSpaceThenPunctuationFromStripTwiceForFrench() {
        final String WORD_TO_TYPE = "test ";
        final String PUNCTUATION_FROM_STRIP = "!";
        final String EXPECTED_RESULT = "test !!";
        changeLanguage("fr");
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        assertTrue("type word then type space should display punctuation strip",
                mLatinIME.isShowingPunctuationList());
        mLatinIME.pickSuggestionManually(0, PUNCTUATION_FROM_STRIP);
        mLatinIME.pickSuggestionManually(0, PUNCTUATION_FROM_STRIP);
        assertEquals("type word then type space then punctuation from strip twice for French",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testWordThenSpaceThenPunctuationFromKeyboardTwice() {
        final String WORD_TO_TYPE = "this !!";
        final String EXPECTED_RESULT = "this !!";
        type(WORD_TO_TYPE);
        assertEquals("manual pick then space then punctuation from keyboard twice", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testManualPickThenPunctuationFromStripTwiceThenType() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = "is";
        final String PUNCTUATION_FROM_STRIP = "!";
        final String EXPECTED_RESULT = "this!! is";
        type(WORD1_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD1_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, PUNCTUATION_FROM_STRIP);
        mLatinIME.pickSuggestionManually(0, PUNCTUATION_FROM_STRIP);
        type(WORD2_TO_TYPE);
        assertEquals("pick word then pick punctuation twice then type", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testManualPickThenSpaceThenType() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = " is";
        final String EXPECTED_RESULT = "this is";
        type(WORD1_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then space then type", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testManualPickThenManualPick() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_PICK = "is";
        final String EXPECTED_RESULT = "this is";
        type(WORD1_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD1_TO_TYPE);
        // Here we fake picking a word through bigram prediction. This test is taking
        // advantage of the fact that Latin IME blindly trusts the caller of #pickSuggestionManually
        // to actually pass the right string.
        mLatinIME.pickSuggestionManually(1, WORD2_TO_PICK);
        assertEquals("manual pick then manual pick", EXPECTED_RESULT,
                mTextView.getText().toString());
    }

    public void testManualPickThenManualPickWithPunctAtStart() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_PICK = "!is";
        final String EXPECTED_RESULT = "this!is";
        type(WORD1_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD1_TO_TYPE);
        mLatinIME.pickSuggestionManually(1, WORD2_TO_PICK);
        assertEquals("manual pick then manual pick a word with punct at start", EXPECTED_RESULT,
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

    public void testManuallyPickedWordThenColon() {
        final String WORD_TO_TYPE = "this";
        final String PUNCTUATION = ":";
        final String EXPECTED_RESULT = "this:";
        type(WORD_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD_TO_TYPE);
        type(PUNCTUATION);
        assertEquals("manually pick word then colon",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testManuallyPickedWordThenOpenParen() {
        final String WORD_TO_TYPE = "this";
        final String PUNCTUATION = "(";
        final String EXPECTED_RESULT = "this (";
        type(WORD_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD_TO_TYPE);
        type(PUNCTUATION);
        assertEquals("manually pick word then open paren",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testManuallyPickedWordThenCloseParen() {
        final String WORD_TO_TYPE = "this";
        final String PUNCTUATION = ")";
        final String EXPECTED_RESULT = "this)";
        type(WORD_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD_TO_TYPE);
        type(PUNCTUATION);
        assertEquals("manually pick word then close paren",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testManuallyPickedWordThenSmiley() {
        final String WORD_TO_TYPE = "this";
        final String SPECIAL_KEY = ":-)";
        final String EXPECTED_RESULT = "this :-)";
        type(WORD_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD_TO_TYPE);
        mLatinIME.onTextInput(SPECIAL_KEY);
        assertEquals("manually pick word then press the smiley key",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testManuallyPickedWordThenDotCom() {
        final String WORD_TO_TYPE = "this";
        final String SPECIAL_KEY = ".com";
        final String EXPECTED_RESULT = "this.com";
        type(WORD_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD_TO_TYPE);
        mLatinIME.onTextInput(SPECIAL_KEY);
        assertEquals("manually pick word then press the .com key",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testTypeWordTypeDotThenPressDotCom() {
        final String WORD_TO_TYPE = "this.";
        final String SPECIAL_KEY = ".com";
        final String EXPECTED_RESULT = "this.com";
        type(WORD_TO_TYPE);
        mLatinIME.onTextInput(SPECIAL_KEY);
        assertEquals("type word type dot then press the .com key",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testAutoCorrectionWithSingleQuoteInside() {
        final String WORD_TO_TYPE = "you'f ";
        final String EXPECTED_RESULT = "you'd ";
        type(WORD_TO_TYPE);
        assertEquals("auto-correction with single quote inside",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testAutoCorrectionWithSingleQuotesAround() {
        final String WORD_TO_TYPE = "'tgis' ";
        final String EXPECTED_RESULT = "'this' ";
        type(WORD_TO_TYPE);
        assertEquals("auto-correction with single quotes around",
                EXPECTED_RESULT, mTextView.getText().toString());
    }

    public void testBlueUnderline() {
        final String STRING_TO_TYPE = "tgis";
        final int EXPECTED_SPAN_START = 0;
        final int EXPECTED_SPAN_END = 4;
        type(STRING_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        final Span span = new Span(mTextView.getText());
        assertEquals("show blue underline, span start", EXPECTED_SPAN_START, span.mStart);
        assertEquals("show blue underline, span end", EXPECTED_SPAN_END, span.mEnd);
        assertEquals("show blue underline, span color", true, span.isAutoCorrectionIndicator());
    }

    public void testBlueUnderlineDisappears() {
        final String STRING_1_TO_TYPE = "tgis";
        final String STRING_2_TO_TYPE = "q";
        final int EXPECTED_SPAN_START = 0;
        final int EXPECTED_SPAN_END = 5;
        type(STRING_1_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        type(STRING_2_TO_TYPE);
        // We haven't have time to look into the dictionary yet, so the line should still be
        // blue to avoid any flicker.
        final Span spanBefore = new Span(mTextView.getText());
        assertEquals("extend blue underline, span start", EXPECTED_SPAN_START, spanBefore.mStart);
        assertEquals("extend blue underline, span end", EXPECTED_SPAN_END, spanBefore.mEnd);
        assertEquals("extend blue underline, span color", true,
                spanBefore.isAutoCorrectionIndicator());
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        // Now we have been able to re-evaluate the word, there shouldn't be an auto-correction span
        final Span spanAfter = new Span(mTextView.getText());
        assertNull("hide blue underline", spanAfter.mSpan);
    }

    public void testBlueUnderlineOnBackspace() {
        final String STRING_TO_TYPE = "tgis";
        final int EXPECTED_SPAN_START = 0;
        final int EXPECTED_SPAN_END = 4;
        type(STRING_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        type(Keyboard.CODE_SPACE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        type(Keyboard.CODE_DELETE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        type(Keyboard.CODE_DELETE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        final Span span = new Span(mTextView.getText());
        assertEquals("show blue underline after backspace, span start",
                EXPECTED_SPAN_START, span.mStart);
        assertEquals("show blue underline after backspace, span end",
                EXPECTED_SPAN_END, span.mEnd);
        assertEquals("show blue underline after backspace, span color", true,
                span.isAutoCorrectionIndicator());
    }

    public void testBlueUnderlineDisappearsWhenCursorMoved() {
        final String STRING_TO_TYPE = "tgis";
        final int NEW_CURSOR_POSITION = 0;
        type(STRING_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        // Simulate the onUpdateSelection() event
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        runMessages();
        // Here the blue underline has been set. testBlueUnderline() is testing for this already,
        // so let's not test it here again.
        // Now simulate the user moving the cursor.
        mInputConnection.setSelection(NEW_CURSOR_POSITION, NEW_CURSOR_POSITION);
        mLatinIME.onUpdateSelection(0, 0, NEW_CURSOR_POSITION, NEW_CURSOR_POSITION, -1, -1);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        final Span span = new Span(mTextView.getText());
        assertNull("blue underline removed when cursor is moved", span.mSpan);
    }
    // TODO: Add some tests for non-BMP characters
}
