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

package com.android.inputmethod.latin.suggestions;

import static junit.framework.TestCase.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.SuggestedWords;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SuggestionStripLayoutHelperTests {
    private static void confirmShowTypedWord(final String message, final int inputType) {
        assertFalse(message, SuggestionStripLayoutHelper.shouldOmitTypedWord(
                inputType,
                false /* gestureFloatingPreviewTextEnabled */,
                false /* shouldShowUiToAcceptTypedWord */));
        assertFalse(message, SuggestionStripLayoutHelper.shouldOmitTypedWord(
                inputType,
                true /* gestureFloatingPreviewTextEnabled */,
                false /* shouldShowUiToAcceptTypedWord */));
        assertFalse(message, SuggestionStripLayoutHelper.shouldOmitTypedWord(
                inputType,
                false /* gestureFloatingPreviewTextEnabled */,
                true /* shouldShowUiToAcceptTypedWord */));
        assertFalse(message, SuggestionStripLayoutHelper.shouldOmitTypedWord(
                inputType,
                true /* gestureFloatingPreviewTextEnabled */,
                true /* shouldShowUiToAcceptTypedWord */));
    }

    @Test
    public void testShouldShowTypedWord() {
        confirmShowTypedWord("no input style",
                SuggestedWords.INPUT_STYLE_NONE);
        confirmShowTypedWord("application specifed",
                SuggestedWords.INPUT_STYLE_APPLICATION_SPECIFIED);
        confirmShowTypedWord("recorrection",
                SuggestedWords.INPUT_STYLE_RECORRECTION);
    }

    @Test
    public void testShouldOmitTypedWordWhileTyping() {
        assertFalse("typing", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_TYPING,
                false /* gestureFloatingPreviewTextEnabled */,
                false /* shouldShowUiToAcceptTypedWord */));
        assertFalse("typing", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_TYPING,
                true /* gestureFloatingPreviewTextEnabled */,
                false /* shouldShowUiToAcceptTypedWord */));
        assertTrue("typing", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_TYPING,
                false /* gestureFloatingPreviewTextEnabled */,
                true /* shouldShowUiToAcceptTypedWord */));
        assertTrue("typing", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_TYPING,
                true /* gestureFloatingPreviewTextEnabled */,
                true /* shouldShowUiToAcceptTypedWord */));
    }

    @Test
    public void testShouldOmitTypedWordWhileGesturing() {
        assertFalse("gesturing", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_UPDATE_BATCH,
                false /* gestureFloatingPreviewTextEnabled */,
                false /* shouldShowUiToAcceptTypedWord */));
        assertFalse("gesturing", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_UPDATE_BATCH,
                true /* gestureFloatingPreviewTextEnabled */,
                false /* shouldShowUiToAcceptTypedWord */));
        assertFalse("gesturing", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_UPDATE_BATCH,
                false /* gestureFloatingPreviewTextEnabled */,
                true /* shouldShowUiToAcceptTypedWord */));
        assertTrue("gesturing", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_UPDATE_BATCH,
                true /* gestureFloatingPreviewTextEnabled */,
                true /* shouldShowUiToAcceptTypedWord */));
    }

    @Test
    public void testShouldOmitTypedWordWhenGestured() {
        assertFalse("gestured", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_TAIL_BATCH,
                false /* gestureFloatingPreviewTextEnabled */,
                false /* shouldShowUiToAcceptTypedWord */));
        assertFalse("gestured", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_TAIL_BATCH,
                true /* gestureFloatingPreviewTextEnabled */,
                false /* shouldShowUiToAcceptTypedWord */));
        assertTrue("gestured", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_TAIL_BATCH,
                false /* gestureFloatingPreviewTextEnabled */,
                true /* shouldShowUiToAcceptTypedWord */));
        assertTrue("gestured", SuggestionStripLayoutHelper.shouldOmitTypedWord(
                SuggestedWords.INPUT_STYLE_TAIL_BATCH,
                true /* gestureFloatingPreviewTextEnabled */,
                true /* shouldShowUiToAcceptTypedWord */));
    }

    // Note that this unit test assumes that the number of suggested words in the suggestion strip
    // is 3.
    private static final int POSITION_OMIT = -1;
    private static final int POSITION_LEFT = 0;
    private static final int POSITION_CENTER = 1;
    private static final int POSITION_RIGHT = 2;

    @Test
    public void testGetPositionInSuggestionStrip() {
        assertEquals("1st word without auto correction", POSITION_CENTER,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        SuggestedWords.INDEX_OF_TYPED_WORD /* indexInSuggestedWords */,
                        false /* willAutoCorrect */,
                        false /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("2nd word without auto correction", POSITION_LEFT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        SuggestedWords.INDEX_OF_AUTO_CORRECTION /* indexInSuggestedWords */,
                        false /* willAutoCorrect */,
                        false /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("3rd word without auto correction", POSITION_RIGHT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        2 /* indexInSuggestedWords */,
                        false /* willAutoCorrect */,
                        false /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));

        assertEquals("typed word with auto correction", POSITION_LEFT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        SuggestedWords.INDEX_OF_TYPED_WORD /* indexInSuggestedWords */,
                        true /* willAutoCorrect */,
                        false /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("2nd word with auto correction", POSITION_CENTER,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        SuggestedWords.INDEX_OF_AUTO_CORRECTION /* indexInSuggestedWords */,
                        true /* willAutoCorrect */,
                        false /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("3rd word with auto correction", POSITION_RIGHT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        2 /* indexInSuggestedWords */,
                        true /* willAutoCorrect */,
                        false /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));

        assertEquals("1st word without auto correction", POSITION_OMIT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        SuggestedWords.INDEX_OF_TYPED_WORD /* indexInSuggestedWords */,
                        false /* willAutoCorrect */,
                        true /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("2nd word without auto correction", POSITION_CENTER,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        SuggestedWords.INDEX_OF_AUTO_CORRECTION /* indexInSuggestedWords */,
                        false /* willAutoCorrect */,
                        true /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("3rd word without auto correction", POSITION_LEFT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        2 /* indexInSuggestedWords */,
                        false /* willAutoCorrect */,
                        true /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("4th word without auto correction", POSITION_RIGHT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        3 /* indexInSuggestedWords */,
                        false /* willAutoCorrect */,
                        true /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));

        assertEquals("typed word with auto correction", POSITION_OMIT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        SuggestedWords.INDEX_OF_TYPED_WORD /* indexInSuggestedWords */,
                        true /* willAutoCorrect */,
                        true /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("2nd word with auto correction", POSITION_CENTER,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        SuggestedWords.INDEX_OF_AUTO_CORRECTION /* indexInSuggestedWords */,
                        true /* willAutoCorrect */,
                        true /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("3rd word with auto correction", POSITION_LEFT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        2 /* indexInSuggestedWords */,
                        true /* willAutoCorrect */,
                        true /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
        assertEquals("4th word with auto correction", POSITION_RIGHT,
                SuggestionStripLayoutHelper.getPositionInSuggestionStrip(
                        3 /* indexInSuggestedWords */,
                        true /* willAutoCorrect */,
                        true /* omitTypedWord */,
                        POSITION_CENTER /* centerPositionInStrip */,
                        POSITION_LEFT /* typedWordPositionWhenAutoCorrect */));
    }
}
