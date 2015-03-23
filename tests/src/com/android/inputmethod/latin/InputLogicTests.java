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

import static android.test.MoreAsserts.assertNotEqual;

import android.test.suitebuilder.annotation.LargeTest;
import android.text.TextUtils;
import android.view.inputmethod.BaseInputConnection;

import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.define.DecoderSpecificConstants;
import com.android.inputmethod.latin.settings.Settings;

@LargeTest
public class InputLogicTests extends InputTestsBase {

    private boolean mNextWordPrediction;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mNextWordPrediction = getBooleanPreference(Settings.PREF_BIGRAM_PREDICTIONS, true);
    }

    @Override
    public void tearDown() throws Exception {
        setBooleanPreference(Settings.PREF_BIGRAM_PREDICTIONS, mNextWordPrediction, true);
        super.tearDown();
    }

    public void testTypeWord() {
        final String WORD_TO_TYPE = "abcd";
        type(WORD_TO_TYPE);
        assertEquals("type word", WORD_TO_TYPE, mEditText.getText().toString());
    }

    public void testPickSuggestionThenBackspace() {
        final String WORD_TO_TYPE = "this";
        final String EXPECTED_RESULT = "thi";
        type(WORD_TO_TYPE);
        pickSuggestionManually(WORD_TO_TYPE);
        sendUpdateForCursorMoveTo(WORD_TO_TYPE.length());
        type(Constants.CODE_DELETE);
        assertEquals("press suggestion then backspace", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testPickAutoCorrectionThenBackspace() {
        final String WORD_TO_TYPE = "tgis";
        final String WORD_TO_PICK = "this";
        final String EXPECTED_RESULT = "thi";
        type(WORD_TO_TYPE);
        // Choose the auto-correction. For "tgis", the auto-correction should be "this".
        pickSuggestionManually(WORD_TO_PICK);
        sendUpdateForCursorMoveTo(WORD_TO_TYPE.length());
        assertEquals("pick typed word over auto-correction then backspace", WORD_TO_PICK,
                mEditText.getText().toString());
        type(Constants.CODE_DELETE);
        assertEquals("pick typed word over auto-correction then backspace", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testPickTypedWordOverAutoCorrectionThenBackspace() {
        final String WORD_TO_TYPE = "tgis";
        final String EXPECTED_RESULT = "tgi";
        type(WORD_TO_TYPE);
        // Choose the typed word.
        pickSuggestionManually(WORD_TO_TYPE);
        sendUpdateForCursorMoveTo(WORD_TO_TYPE.length());
        assertEquals("pick typed word over auto-correction then backspace", WORD_TO_TYPE,
                mEditText.getText().toString());
        type(Constants.CODE_DELETE);
        assertEquals("pick typed word over auto-correction then backspace", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testPickDifferentSuggestionThenBackspace() {
        final String WORD_TO_TYPE = "tgis";
        final String WORD_TO_PICK = "thus";
        final String EXPECTED_RESULT = "thu";
        type(WORD_TO_TYPE);
        // Choose the second suggestion, which should be "thus" when "tgis" is typed.
        pickSuggestionManually(WORD_TO_PICK);
        sendUpdateForCursorMoveTo(WORD_TO_TYPE.length());
        assertEquals("pick different suggestion then backspace", WORD_TO_PICK,
                mEditText.getText().toString());
        type(Constants.CODE_DELETE);
        assertEquals("pick different suggestion then backspace", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDeleteSelection() {
        final String STRING_TO_TYPE = "some text delete me some text";
        final int typedLength = STRING_TO_TYPE.length();
        final int SELECTION_START = 10;
        final int SELECTION_END = 19;
        final String EXPECTED_RESULT = "some text  some text";
        type(STRING_TO_TYPE);
        // Don't use the sendUpdateForCursorMove* family of methods here because they
        // don't handle selections.
        // Send once to simulate the cursor actually responding to the move caused by typing.
        // This is necessary because LatinIME is bookkeeping to avoid confusing a real cursor
        // move with a move triggered by LatinIME inputting stuff.
        mLatinIME.onUpdateSelection(0, 0, typedLength, typedLength, -1, -1);
        mInputConnection.setSelection(SELECTION_START, SELECTION_END);
        // And now we simulate the user actually selecting some text.
        mLatinIME.onUpdateSelection(typedLength, typedLength,
                SELECTION_START, SELECTION_END, -1, -1);
        type(Constants.CODE_DELETE);
        assertEquals("delete selection", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testDeleteSelectionTwice() {
        final String STRING_TO_TYPE = "some text delete me some text";
        final int typedLength = STRING_TO_TYPE.length();
        final int SELECTION_START = 10;
        final int SELECTION_END = 19;
        final String EXPECTED_RESULT = "some text some text";
        type(STRING_TO_TYPE);
        // Don't use the sendUpdateForCursorMove* family of methods here because they
        // don't handle selections.
        // Send once to simulate the cursor actually responding to the move caused by typing.
        // This is necessary because LatinIME is bookkeeping to avoid confusing a real cursor
        // move with a move triggered by LatinIME inputting stuff.
        mLatinIME.onUpdateSelection(0, 0, typedLength, typedLength, -1, -1);
        mInputConnection.setSelection(SELECTION_START, SELECTION_END);
        // And now we simulate the user actually selecting some text.
        mLatinIME.onUpdateSelection(typedLength, typedLength,
                SELECTION_START, SELECTION_END, -1, -1);
        type(Constants.CODE_DELETE);
        type(Constants.CODE_DELETE);
        assertEquals("delete selection twice", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testAutoCorrect() {
        final String STRING_TO_TYPE = "tgis ";
        final String EXPECTED_RESULT = "this ";
        type(STRING_TO_TYPE);
        assertEquals("simple auto-correct", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testAutoCorrectWithQuote() {
        final String STRING_TO_TYPE = "didn' ";
        final String EXPECTED_RESULT = "didn't ";
        type(STRING_TO_TYPE);
        assertEquals("auto-correct with quote", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testAutoCorrectWithPeriod() {
        final String STRING_TO_TYPE = "tgis.";
        final String EXPECTED_RESULT = "this.";
        type(STRING_TO_TYPE);
        assertEquals("auto-correct with period", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testAutoCorrectWithPeriodThenRevert() {
        final String STRING_TO_TYPE = "tgis.";
        final String EXPECTED_RESULT = "tgis.";
        type(STRING_TO_TYPE);
        sendUpdateForCursorMoveTo(STRING_TO_TYPE.length());
        type(Constants.CODE_DELETE);
        assertEquals("auto-correct with period then revert", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testAutoCorrectWithSpaceThenRevert() {
        // Backspacing to cancel the "tgis"->"this" autocorrection should result in
        // a "phantom space": if the user presses space immediately after,
        // only one space will be inserted in total.
        final String STRING_TO_TYPE = "tgis ";
        final String EXPECTED_RESULT = "tgis";
        type(STRING_TO_TYPE);
        sendUpdateForCursorMoveTo(STRING_TO_TYPE.length());
        type(Constants.CODE_DELETE);
        assertEquals("auto-correct with space then revert", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testAutoCorrectWithSpaceThenRevertThenTypeMore() {
        final String STRING_TO_TYPE_FIRST = "tgis ";
        final String STRING_TO_TYPE_SECOND = "a";
        final String EXPECTED_RESULT = "tgis a";
        type(STRING_TO_TYPE_FIRST);
        sendUpdateForCursorMoveTo(STRING_TO_TYPE_FIRST.length());
        type(Constants.CODE_DELETE);

        type(STRING_TO_TYPE_SECOND);
        sendUpdateForCursorMoveTo(STRING_TO_TYPE_FIRST.length() - 1
                + STRING_TO_TYPE_SECOND.length());
        assertEquals("auto-correct with space then revert then type more", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testAutoCorrectToSelfDoesNotRevert() {
        final String STRING_TO_TYPE = "this ";
        final String EXPECTED_RESULT = "this";
        type(STRING_TO_TYPE);
        sendUpdateForCursorMoveTo(STRING_TO_TYPE.length());
        type(Constants.CODE_DELETE);
        assertEquals("auto-correct with space does not revert", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDoubleSpace() {
        // U+1F607 is an emoji
        final String[] STRINGS_TO_TYPE =
                new String[] { "this   ", "a+  ", "\u1F607  ", "..  ", ")  ", "(  ", "%  " };
        final String[] EXPECTED_RESULTS =
                new String[] { "this.  ", "a+. ", "\u1F607. ", "..  ", "). ", "(  ", "%. " };
        verifyDoubleSpace(STRINGS_TO_TYPE, EXPECTED_RESULTS);
    }

    public void testDoubleSpaceHindi() {
        changeLanguage("hi");
        // U+1F607 is an emoji
        final String[] STRINGS_TO_TYPE =
                new String[] { "this   ", "a+  ", "\u1F607  ", "||  ", ")  ", "(  ", "%  " };
        final String[] EXPECTED_RESULTS =
                new String[] { "this|  ", "a+| ", "\u1F607| ", "||  ", ")| ", "(  ", "%| " };
        verifyDoubleSpace(STRINGS_TO_TYPE, EXPECTED_RESULTS);
    }

    private void verifyDoubleSpace(String[] stringsToType, String[] expectedResults) {
        // Set default pref just in case
        setBooleanPreference(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true, true);
        for (int i = 0; i < stringsToType.length; ++i) {
            mEditText.setText("");
            type(stringsToType[i]);
            assertEquals("double space processing", expectedResults[i],
                    mEditText.getText().toString());
        }
    }

    public void testCancelDoubleSpaceEnglish() {
        final String STRING_TO_TYPE = "this  ";
        final String EXPECTED_RESULT = "this ";
        type(STRING_TO_TYPE);
        type(Constants.CODE_DELETE);
        assertEquals("double space make a period", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testCancelDoubleSpaceHindi() {
        changeLanguage("hi");
        final String STRING_TO_TYPE = "this  ";
        final String EXPECTED_RESULT = "this ";
        type(STRING_TO_TYPE);
        type(Constants.CODE_DELETE);
        assertEquals("double space make a period", EXPECTED_RESULT, mEditText.getText().toString());
    }

    private void testDoubleSpacePeriodWithSettings(final boolean expectsPeriod,
            final Object... settingsKeysValues) {
        final Object[] oldSettings = new Object[settingsKeysValues.length / 2];
        final String STRING_WITHOUT_PERIOD = "this  ";
        final String STRING_WITH_PERIOD = "this. ";
        final String EXPECTED_RESULT = expectsPeriod ? STRING_WITH_PERIOD : STRING_WITHOUT_PERIOD;
        try {
            for (int i = 0; i < settingsKeysValues.length; i += 2) {
                if (settingsKeysValues[i + 1] instanceof String) {
                    oldSettings[i / 2] = setStringPreference((String)settingsKeysValues[i],
                            (String)settingsKeysValues[i + 1], "0");
                } else {
                    oldSettings[i / 2] = setBooleanPreference((String)settingsKeysValues[i],
                            (Boolean)settingsKeysValues[i + 1], false);
                }
            }
            mLatinIME.loadSettings();
            mEditText.setText("");
            type(STRING_WITHOUT_PERIOD);
            assertEquals("double-space-to-period with specific settings "
                    + TextUtils.join(" ", settingsKeysValues),
                    EXPECTED_RESULT, mEditText.getText().toString());
        } finally {
            // Restore old settings
            for (int i = 0; i < settingsKeysValues.length; i += 2) {
                if (null == oldSettings[i / 2]) {
                    break;
                } if (oldSettings[i / 2] instanceof String) {
                    setStringPreference((String)settingsKeysValues[i], (String)oldSettings[i / 2],
                            "");
                } else {
                    setBooleanPreference((String)settingsKeysValues[i], (Boolean)oldSettings[i / 2],
                            false);
                }
            }
        }
    }

    public void testDoubleSpacePeriod() {
        // Reset settings to default, else these tests will go flaky.
        setBooleanPreference(Settings.PREF_SHOW_SUGGESTIONS, true, true);
        setBooleanPreference(Settings.PREF_AUTO_CORRECTION, true, true);
        setBooleanPreference(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true, true);
        testDoubleSpacePeriodWithSettings(true);
        // "Suggestion visibility" to off
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_SHOW_SUGGESTIONS, false);
        // "Suggestion visibility" to on
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_SHOW_SUGGESTIONS, true);

        // "Double-space period" to "off"
        testDoubleSpacePeriodWithSettings(false, Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, false);

        // "Auto-correction" to "off"
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_AUTO_CORRECTION, false);
        // "Auto-correction" to "on"
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_AUTO_CORRECTION, true);

        // "Suggestion visibility" to "always hide" and "Auto-correction" to "off"
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_SHOW_SUGGESTIONS, false,
                Settings.PREF_AUTO_CORRECTION, false);
        // "Suggestion visibility" to "always hide" and "Auto-correction" to "off"
        testDoubleSpacePeriodWithSettings(false, Settings.PREF_SHOW_SUGGESTIONS, false,
                Settings.PREF_AUTO_CORRECTION, false,
                Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, false);
    }

    public void testBackspaceAtStartAfterAutocorrect() {
        final String STRING_TO_TYPE = "tgis ";
        final int typedLength = STRING_TO_TYPE.length();
        final String EXPECTED_RESULT = "this ";
        final int NEW_CURSOR_POSITION = 0;
        type(STRING_TO_TYPE);
        sendUpdateForCursorMoveTo(typedLength);
        mInputConnection.setSelection(NEW_CURSOR_POSITION, NEW_CURSOR_POSITION);
        sendUpdateForCursorMoveTo(NEW_CURSOR_POSITION);
        type(Constants.CODE_DELETE);
        assertEquals("auto correct then move cursor to start of line then backspace",
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testAutoCorrectThenMoveCursorThenBackspace() {
        final String STRING_TO_TYPE = "and tgis ";
        final int typedLength = STRING_TO_TYPE.length();
        final String EXPECTED_RESULT = "andthis ";
        final int NEW_CURSOR_POSITION = STRING_TO_TYPE.indexOf('t');
        type(STRING_TO_TYPE);
        sendUpdateForCursorMoveTo(typedLength);
        mInputConnection.setSelection(NEW_CURSOR_POSITION, NEW_CURSOR_POSITION);
        sendUpdateForCursorMoveTo(NEW_CURSOR_POSITION);
        type(Constants.CODE_DELETE);
        assertEquals("auto correct then move cursor then backspace",
                EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testNoSpaceAfterManualPick() {
        final String WORD_TO_TYPE = "this";
        final String EXPECTED_RESULT = WORD_TO_TYPE;
        type(WORD_TO_TYPE);
        pickSuggestionManually(WORD_TO_TYPE);
        assertEquals("no space after manual pick", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testManualPickThenType() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = "is";
        final String EXPECTED_RESULT = "this is";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then type", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testManualPickThenSeparator() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = "!";
        final String EXPECTED_RESULT = "this!";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then separator", EXPECTED_RESULT, mEditText.getText().toString());
    }

    // This test matches testClusteringPunctuationForFrench.
    // In some non-English languages, ! and ? are clustering punctuation signs.
    public void testClusteringPunctuation() {
        final String WORD1_TO_TYPE = "test";
        final String WORD2_TO_TYPE = "!!?!:!";
        final String EXPECTED_RESULT = "test!!?!:!";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("clustering punctuation", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testManualPickThenStripperThenPick() {
        final String WORD_TO_TYPE = "this";
        final String STRIPPER = "\n";
        final String EXPECTED_RESULT = "this\nthis";
        type(WORD_TO_TYPE);
        pickSuggestionManually(WORD_TO_TYPE);
        type(STRIPPER);
        type(WORD_TO_TYPE);
        pickSuggestionManually(WORD_TO_TYPE);
        assertEquals("manual pick then \\n then manual pick", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testManualPickThenSpaceThenType() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_TYPE = " is";
        final String EXPECTED_RESULT = "this is";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then space then type", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testManualPickThenManualPick() {
        final String WORD1_TO_TYPE = "this";
        final String WORD2_TO_PICK = "is";
        final String EXPECTED_RESULT = "this is";
        type(WORD1_TO_TYPE);
        pickSuggestionManually(WORD1_TO_TYPE);
        // Here we fake picking a word through bigram prediction.
        pickSuggestionManually(WORD2_TO_PICK);
        assertEquals("manual pick then manual pick", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDeleteWholeComposingWord() {
        final String WORD_TO_TYPE = "this";
        type(WORD_TO_TYPE);
        for (int i = 0; i < WORD_TO_TYPE.length(); ++i) {
            type(Constants.CODE_DELETE);
        }
        assertEquals("delete whole composing word", "", mEditText.getText().toString());
    }

    public void testResumeSuggestionOnBackspace() {
        final String STRING_TO_TYPE = "and this ";
        final int typedLength = STRING_TO_TYPE.length();
        type(STRING_TO_TYPE);
        assertEquals("resume suggestion on backspace", -1,
                BaseInputConnection.getComposingSpanStart(mEditText.getText()));
        assertEquals("resume suggestion on backspace", -1,
                BaseInputConnection.getComposingSpanEnd(mEditText.getText()));
        sendUpdateForCursorMoveTo(typedLength);
        type(Constants.CODE_DELETE);
        assertEquals("resume suggestion on backspace", 4,
                BaseInputConnection.getComposingSpanStart(mEditText.getText()));
        assertEquals("resume suggestion on backspace", 8,
                BaseInputConnection.getComposingSpanEnd(mEditText.getText()));
    }

    private void helperTestComposing(final String wordToType, final boolean shouldBeComposing) {
        mEditText.setText("");
        type(wordToType);
        assertEquals("start composing inside text", shouldBeComposing ? 0 : -1,
                BaseInputConnection.getComposingSpanStart(mEditText.getText()));
        assertEquals("start composing inside text", shouldBeComposing ? wordToType.length() : -1,
                BaseInputConnection.getComposingSpanEnd(mEditText.getText()));
    }

    public void testStartComposing() {
        // Should start composing on a letter
        helperTestComposing("a", true);
        type("  "); // To reset the composing state
        // Should not start composing on quote
        helperTestComposing("'", false);
        type("  ");
        helperTestComposing("'-", false);
        type("  ");
        // Should not start composing on dash
        helperTestComposing("-", false);
        type("  ");
        helperTestComposing("-'", false);
        type("  ");
        helperTestComposing("a-", true);
        type("  ");
        helperTestComposing("a'", true);
    }

    // TODO: Add some tests for non-BMP characters

    public void testAutoCorrectByUserHistory() {
        type("qpmz");
        type(Constants.CODE_SPACE);

        int startIndex = mEditText.getText().length();
        type("qpmx");
        type(Constants.CODE_SPACE);
        int endIndex = mEditText.getText().length();
        assertEquals("auto-corrected by user history",
                "qpmz ", mEditText.getText().subSequence(startIndex, endIndex).toString());
    }

    public void testPredictionsAfterSpace() {
        final String WORD_TO_TYPE = "Barack ";
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();
        // Test the first prediction is displayed
        final SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("predictions after space", "Obama",
                suggestedWords.size() > 0 ? suggestedWords.getWord(0) : null);
    }

    public void testPredictionsWithDoubleSpaceToPeriod() {
        mLatinIME.clearPersonalizedDictionariesForTest();
        final String WORD_TO_TYPE = "Barack  ";
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();

        type(Constants.CODE_DELETE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();

        SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("predictions after cancel double-space-to-period", "Obama",
                mLatinIME.getSuggestedWordsForTest().getWord(0));
    }

    public void testPredictionsAfterManualPick() {
        final String WORD_TO_TYPE = "Barack";
        type(WORD_TO_TYPE);
        // Choose the auto-correction. For "Barack", the auto-correction should be "Barack".
        pickSuggestionManually(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();
        // Test the first prediction is displayed
        final SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("predictions after manual pick", "Obama",
                suggestedWords.size() > 0 ? suggestedWords.getWord(0) : null);
    }

    public void testPredictionsAfterPeriod() {
        mLatinIME.clearPersonalizedDictionariesForTest();
        final String WORD_TO_TYPE = "Barack. ";
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();

        SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertFalse(mLatinIME.getSuggestedWordsForTest().isEmpty());
    }

    public void testPredictionsAfterRecorrection() {
        final String PREFIX = "A ";
        final String WORD_TO_TYPE = "Barack";
        final String FIRST_NON_TYPED_SUGGESTION = "Barrack";
        final int endOfPrefix = PREFIX.length();
        final int endOfWord = endOfPrefix + WORD_TO_TYPE.length();
        final int endOfSuggestion = endOfPrefix + FIRST_NON_TYPED_SUGGESTION.length();
        final int indexForManualCursor = endOfPrefix + 3; // +3 because it's after "Bar" in "Barack"
        type(PREFIX);
        sendUpdateForCursorMoveTo(endOfPrefix);
        type(WORD_TO_TYPE);
        pickSuggestionManually(FIRST_NON_TYPED_SUGGESTION);
        sendUpdateForCursorMoveTo(endOfSuggestion);
        runMessages();
        type(" ");
        sendUpdateForCursorMoveBy(1);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();
        // Simulate a manual cursor move
        mInputConnection.setSelection(indexForManualCursor, indexForManualCursor);
        sendUpdateForCursorMoveTo(indexForManualCursor);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();
        pickSuggestionManually(WORD_TO_TYPE);
        sendUpdateForCursorMoveTo(endOfWord);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();
        // Test the first prediction is displayed
        final SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("predictions after recorrection", "Obama",
                suggestedWords.size() > 0 ? suggestedWords.getWord(0) : null);
    }

    public void testComposingMultipleBackspace() {
        final String WORD_TO_TYPE = "radklro";
        final int TIMES_TO_TYPE = 3;
        final int TIMES_TO_BACKSPACE = 8;
        type(WORD_TO_TYPE);
        type(Constants.CODE_DELETE);
        type(Constants.CODE_DELETE);
        type(Constants.CODE_DELETE);
        type(WORD_TO_TYPE);
        type(Constants.CODE_DELETE);
        type(Constants.CODE_DELETE);
        type(WORD_TO_TYPE);
        type(Constants.CODE_DELETE);
        type(Constants.CODE_DELETE);
        type(Constants.CODE_DELETE);
        assertEquals("composing with multiple backspace",
                WORD_TO_TYPE.length() * TIMES_TO_TYPE - TIMES_TO_BACKSPACE,
                mEditText.getText().length());
    }

    public void testManySingleQuotes() {
        final String WORD_TO_AUTOCORRECT = "i";
        final String WORD_AUTOCORRECTED = "I";
        final String QUOTES = "''''''''''''''''''''";
        final String WORD_TO_TYPE = WORD_TO_AUTOCORRECT + QUOTES + " ";
        final String EXPECTED_RESULT = WORD_AUTOCORRECTED + QUOTES + " ";
        type(WORD_TO_TYPE);
        assertEquals("auto-correct with many trailing single quotes", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testManySingleQuotesOneByOne() {
        final String WORD_TO_AUTOCORRECT = "i";
        final String WORD_AUTOCORRECTED = "I";
        final String QUOTES = "''''''''''''''''''''";
        final String WORD_TO_TYPE = WORD_TO_AUTOCORRECT + QUOTES + " ";
        final String EXPECTED_RESULT = WORD_AUTOCORRECTED + QUOTES + " ";

        for (int i = 0; i < WORD_TO_TYPE.length(); ++i) {
            type(WORD_TO_TYPE.substring(i, i+1));
            sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
            runMessages();
        }
        assertEquals("type many trailing single quotes one by one", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testTypingSingleQuotesOneByOne() {
        final String WORD_TO_TYPE = "it's ";
        final String EXPECTED_RESULT = WORD_TO_TYPE;
        for (int i = 0; i < WORD_TO_TYPE.length(); ++i) {
            type(WORD_TO_TYPE.substring(i, i+1));
            sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
            runMessages();
        }
        assertEquals("type words letter by letter", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testBasicGesture() {
        gesture("this");
        assertEquals("this", mEditText.getText().toString());
    }

    public void testGestureGesture() {
        gesture("got");
        gesture("milk");
        assertEquals("got milk", mEditText.getText().toString());
    }

    public void testGestureBackspaceGestureAgain() {
        gesture("this");
        type(Constants.CODE_DELETE);
        assertEquals("gesture then backspace", "", mEditText.getText().toString());
        gesture("this");
        if (DecoderSpecificConstants.SHOULD_REMOVE_PREVIOUSLY_REJECTED_SUGGESTION) {
            assertNotEqual("this", mEditText.getText().toString());
        } else {
            assertEquals("this", mEditText.getText().toString());
        }
    }

    private void typeOrGestureWordAndPutCursorInside(final boolean gesture, final String word,
            final int startPos) {
        final int END_OF_WORD = startPos + word.length();
        final int NEW_CURSOR_POSITION = startPos + word.length() / 2;
        if (gesture) {
            gesture(word);
        } else {
            type(word);
        }
        sendUpdateForCursorMoveTo(END_OF_WORD);
        runMessages();
        sendUpdateForCursorMoveTo(NEW_CURSOR_POSITION);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
    }

    private void typeWordAndPutCursorInside(final String word, final int startPos) {
        typeOrGestureWordAndPutCursorInside(false /* gesture */, word, startPos);
    }

    private void gestureWordAndPutCursorInside(final String word, final int startPos) {
        typeOrGestureWordAndPutCursorInside(true /* gesture */, word, startPos);
    }

    private void ensureComposingSpanPos(final String message, final int from, final int to) {
        assertEquals(message, from, BaseInputConnection.getComposingSpanStart(mEditText.getText()));
        assertEquals(message, to, BaseInputConnection.getComposingSpanEnd(mEditText.getText()));
    }

    public void testTypeWithinComposing() {
        final String WORD_TO_TYPE = "something";
        final String EXPECTED_RESULT = "some thing";
        typeWordAndPutCursorInside(WORD_TO_TYPE, 0 /* startPos */);
        type(" ");
        ensureComposingSpanPos("space while in the middle of a word cancels composition", -1, -1);
        assertEquals("space in the middle of a composing word", EXPECTED_RESULT,
                mEditText.getText().toString());
        int cursorPos = sendUpdateForCursorMoveToEndOfLine();
        runMessages();
        type(" ");
        assertEquals("mbo", "some thing ", mEditText.getText().toString());
        typeWordAndPutCursorInside(WORD_TO_TYPE, cursorPos + 1 /* startPos */);
        type(Constants.CODE_DELETE);
        ensureComposingSpanPos("delete while in the middle of a word cancels composition", -1, -1);
    }

    public void testTypeWithinGestureComposing() {
        final String WORD_TO_TYPE = "something";
        final String EXPECTED_RESULT = "some thing";
        gestureWordAndPutCursorInside(WORD_TO_TYPE, 0 /* startPos */);
        type(" ");
        ensureComposingSpanPos("space while in the middle of a word cancels composition", -1, -1);
        assertEquals("space in the middle of a composing word", EXPECTED_RESULT,
                mEditText.getText().toString());
        int cursorPos = sendUpdateForCursorMoveToEndOfLine();
        runMessages();
        type(" ");
        typeWordAndPutCursorInside(WORD_TO_TYPE, cursorPos + 1 /* startPos */);
        type(Constants.CODE_DELETE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        ensureComposingSpanPos("delete while in the middle of a word cancels composition", -1, -1);
    }

    public void testManualPickThenSeparatorForFrench() {
        final String WORD1_TO_TYPE = "test";
        final String WORD2_TO_TYPE = "!";
        final String EXPECTED_RESULT = "test !";
        changeLanguage("fr");
        type(WORD1_TO_TYPE);
        pickSuggestionManually(WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("manual pick then separator for French", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testClusteringPunctuationForFrench() {
        final String WORD1_TO_TYPE = "test";
        final String WORD2_TO_TYPE = "!!?!:!";
        // In English, the expected result would be "test!!?!:!"
        final String EXPECTED_RESULT = "test !!?! : !";
        changeLanguage("fr");
        type(WORD1_TO_TYPE);
        pickSuggestionManually(WORD1_TO_TYPE);
        type(WORD2_TO_TYPE);
        assertEquals("clustering punctuation for French", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testWordThenSpaceThenPunctuationFromStripTwice() {
        setBooleanPreference(Settings.PREF_BIGRAM_PREDICTIONS, false, true);

        final String WORD_TO_TYPE = "test ";
        final String PUNCTUATION_FROM_STRIP = "!";
        final String EXPECTED_RESULT = "test!! ";
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        assertTrue("type word then type space should display punctuation strip",
                mLatinIME.getSuggestedWordsForTest().isPunctuationSuggestions());
        pickSuggestionManually(PUNCTUATION_FROM_STRIP);
        pickSuggestionManually(PUNCTUATION_FROM_STRIP);
        assertEquals(EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testWordThenSpaceDisplaysPredictions() {
        final String WORD_TO_TYPE = "Barack ";
        final String EXPECTED_RESULT = "Obama";
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        final SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("type word then type space yields predictions for French",
                EXPECTED_RESULT, suggestedWords.size() > 0 ? suggestedWords.getWord(0) : null);
    }
}
