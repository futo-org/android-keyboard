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
import android.text.TextUtils;
import android.view.inputmethod.BaseInputConnection;

import com.android.inputmethod.latin.settings.Settings;

@LargeTest
public class InputLogicTests extends InputTestsBase {

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
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
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
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
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
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
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
        mLatinIME.onUpdateSelection(0, 0, WORD_TO_TYPE.length(), WORD_TO_TYPE.length(), -1, -1);
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
        // There is no IMF to call onUpdateSelection for us so we must do it by hand.
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
        // There is no IMF to call onUpdateSelection for us so we must do it by hand.
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
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        type(Constants.CODE_DELETE);
        assertEquals("auto-correct with period then revert", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testAutoCorrectWithSpaceThenRevert() {
        final String STRING_TO_TYPE = "tgis ";
        final String EXPECTED_RESULT = "tgis ";
        type(STRING_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        type(Constants.CODE_DELETE);
        assertEquals("auto-correct with space then revert", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testAutoCorrectToSelfDoesNotRevert() {
        final String STRING_TO_TYPE = "this ";
        final String EXPECTED_RESULT = "this";
        type(STRING_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        type(Constants.CODE_DELETE);
        assertEquals("auto-correct with space does not revert", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDoubleSpace() {
        // Set default pref just in case
        setBooleanPreference(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true, true);
        // U+1F607 is an emoji
        final String[] STRINGS_TO_TYPE =
                new String[] { "this   ", "a+  ", "\u1F607  ", "..  ", ")  ", "(  ", "%  " };
        final String[] EXPECTED_RESULTS =
                new String[] { "this.  ", "a+. ", "\u1F607. ", "..  ", "). ", "(  ", "%. " };
        for (int i = 0; i < STRINGS_TO_TYPE.length; ++i) {
            mEditText.setText("");
            type(STRINGS_TO_TYPE[i]);
            assertEquals("double space processing", EXPECTED_RESULTS[i],
                    mEditText.getText().toString());
        }
    }

    public void testCancelDoubleSpace() {
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
        setStringPreference(Settings.PREF_AUTO_CORRECTION_THRESHOLD, "1", "1");
        setBooleanPreference(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true, true);
        testDoubleSpacePeriodWithSettings(true /* expectsPeriod */);
        // "Suggestion visibility" to off
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_SHOW_SUGGESTIONS, false);
        // "Suggestion visibility" to on
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_SHOW_SUGGESTIONS, true);

        // "Double-space period" to "off"
        testDoubleSpacePeriodWithSettings(false, Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, false);

        // "Auto-correction" to "off"
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_AUTO_CORRECTION_THRESHOLD, "0");
        // "Auto-correction" to "modest"
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_AUTO_CORRECTION_THRESHOLD, "1");
        // "Auto-correction" to "very aggressive"
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_AUTO_CORRECTION_THRESHOLD, "3");

        // "Suggestion visibility" to "always hide" and "Auto-correction" to "off"
        testDoubleSpacePeriodWithSettings(true, Settings.PREF_SHOW_SUGGESTIONS, false,
                Settings.PREF_AUTO_CORRECTION_THRESHOLD, "0");
        // "Suggestion visibility" to "always hide" and "Auto-correction" to "off"
        testDoubleSpacePeriodWithSettings(false, Settings.PREF_SHOW_SUGGESTIONS, false,
                Settings.PREF_AUTO_CORRECTION_THRESHOLD, "0",
                Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, false);
    }

    public void testBackspaceAtStartAfterAutocorrect() {
        final String STRING_TO_TYPE = "tgis ";
        final int typedLength = STRING_TO_TYPE.length();
        final String EXPECTED_RESULT = "this ";
        final int NEW_CURSOR_POSITION = 0;
        type(STRING_TO_TYPE);
        mLatinIME.onUpdateSelection(0, 0, typedLength, typedLength, -1, -1);
        mInputConnection.setSelection(NEW_CURSOR_POSITION, NEW_CURSOR_POSITION);
        mLatinIME.onUpdateSelection(typedLength, typedLength,
                NEW_CURSOR_POSITION, NEW_CURSOR_POSITION, -1, -1);
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
        mLatinIME.onUpdateSelection(0, 0, typedLength, typedLength, -1, -1);
        mInputConnection.setSelection(NEW_CURSOR_POSITION, NEW_CURSOR_POSITION);
        mLatinIME.onUpdateSelection(typedLength, typedLength,
                NEW_CURSOR_POSITION, NEW_CURSOR_POSITION, -1, -1);
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

    // This test matches the one in InputLogicTestsNonEnglish. In some non-English languages,
    // ! and ? are clustering punctuation signs.
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
        mLatinIME.onUpdateSelection(0, 0, typedLength, typedLength, -1, -1);
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
        final String WORD_TO_BE_CORRECTED = "qpmx";
        final String NOT_CORRECTED_RESULT = "qpmx ";
        final String DESIRED_WORD = "qpmz";
        final String CORRECTED_RESULT = "qpmz ";
        final int typeCountNotToAutocorrect = 1;
        final int typeCountToAutoCorrect = 16;
        int startIndex = 0;
        int endIndex = 0;

        for (int i = 0; i < typeCountNotToAutocorrect; i++) {
            type(DESIRED_WORD);
            type(Constants.CODE_SPACE);
        }
        startIndex = mEditText.getText().length();
        type(WORD_TO_BE_CORRECTED);
        type(Constants.CODE_SPACE);
        endIndex = mEditText.getText().length();
        assertEquals("not auto-corrected by user history", NOT_CORRECTED_RESULT,
                mEditText.getText().subSequence(startIndex, endIndex).toString());
        for (int i = typeCountNotToAutocorrect; i < typeCountToAutoCorrect; i++) {
            type(DESIRED_WORD);
            type(Constants.CODE_SPACE);
        }
        startIndex = mEditText.getText().length();
        type(WORD_TO_BE_CORRECTED);
        type(Constants.CODE_SPACE);
        endIndex = mEditText.getText().length();
        assertEquals("auto-corrected by user history",
                CORRECTED_RESULT, mEditText.getText().subSequence(startIndex, endIndex).toString());
    }

    public void testPredictionsAfterSpace() {
        final String WORD_TO_TYPE = "Barack ";
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        // Test the first prediction is displayed
        final SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("predictions after space", "Obama",
                suggestedWords.size() > 0 ? suggestedWords.getWord(0) : null);
    }

    public void testPredictionsWithDoubleSpaceToPeriod() {
        mLatinIME.clearPersonalizedDictionariesForTest();
        final String WORD_TO_TYPE = "Barack ";
        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        // No need to test here, testPredictionsAfterSpace is testing it already
        type(" ");
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        // Test the predictions have been cleared
        SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("predictions cleared after double-space-to-period", suggestedWords.size(), 0);
        type(Constants.CODE_DELETE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        // Test the first prediction is displayed
        suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("predictions after cancel double-space-to-period", "Obama",
                suggestedWords.size() > 0 ? suggestedWords.getWord(0) : null);
    }

    public void testPredictionsAfterManualPick() {
        final String WORD_TO_TYPE = "Barack";
        type(WORD_TO_TYPE);
        // Choose the auto-correction. For "Barack", the auto-correction should be "Barack".
        pickSuggestionManually(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
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
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("No prediction after period after inputting once.", 0, suggestedWords.size());

        type(WORD_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("Beginning-of-Sentence prediction after inputting 2 times.", "Barack",
                suggestedWords.size() > 0 ? suggestedWords.getWord(0) : null);
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
        mLatinIME.onUpdateSelection(0, 0, endOfPrefix, endOfPrefix, -1, -1);
        type(WORD_TO_TYPE);
        pickSuggestionManually(FIRST_NON_TYPED_SUGGESTION);
        mLatinIME.onUpdateSelection(endOfPrefix, endOfPrefix, endOfSuggestion, endOfSuggestion,
                -1, -1);
        runMessages();
        type(" ");
        mLatinIME.onUpdateSelection(endOfSuggestion, endOfSuggestion,
                endOfSuggestion + 1, endOfSuggestion + 1, -1, -1);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        // Simulate a manual cursor move
        mInputConnection.setSelection(indexForManualCursor, indexForManualCursor);
        mLatinIME.onUpdateSelection(endOfSuggestion + 1, endOfSuggestion + 1,
                indexForManualCursor, indexForManualCursor, -1, -1);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
        runMessages();
        pickSuggestionManually(WORD_TO_TYPE);
        mLatinIME.onUpdateSelection(indexForManualCursor, indexForManualCursor,
                endOfWord, endOfWord, -1, -1);
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
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
            sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
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
            sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
            runMessages();
        }
        assertEquals("type words letter by letter", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testSwitchLanguages() {
        final String WORD_TO_TYPE_FIRST_PART = "com";
        final String WORD_TO_TYPE_SECOND_PART = "md";
        final String EXPECTED_RESULT = "comme";
        changeLanguage("en");
        type(WORD_TO_TYPE_FIRST_PART);
        changeLanguage("fr");
        runMessages();
        type(WORD_TO_TYPE_SECOND_PART);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE);
        runMessages();
        final SuggestedWords suggestedWords = mLatinIME.getSuggestedWordsForTest();
        assertEquals("Suggestions updated after switching languages",
                    EXPECTED_RESULT, suggestedWords.size() > 0 ? suggestedWords.getWord(1) : null);
    }
}
