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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.util.ArrayList;
import java.util.Locale;

@SmallTest
public class SuggestedWordsTests extends AndroidTestCase {

    /**
     * Helper method to create a dummy {@link SuggestedWordInfo} with specifying
     * {@link SuggestedWordInfo#KIND_TYPED}.
     *
     * @param word the word to be used to create {@link SuggestedWordInfo}.
     * @return a new instance of {@link SuggestedWordInfo}.
     */
    private static SuggestedWordInfo createTypedWordInfo(final String word) {
        // Use 100 as the frequency because the numerical value does not matter as
        // long as it's > 1 and < INT_MAX.
        return new SuggestedWordInfo(word, 100 /* score */,
                SuggestedWordInfo.KIND_TYPED,
                null /* sourceDict */,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                1 /* autoCommitFirstWordConfidence */);
    }

    /**
     * Helper method to create a dummy {@link SuggestedWordInfo} with specifying
     * {@link SuggestedWordInfo#KIND_CORRECTION}.
     *
     * @param word the word to be used to create {@link SuggestedWordInfo}.
     * @return a new instance of {@link SuggestedWordInfo}.
     */
    private static SuggestedWordInfo createCorrectionWordInfo(final String word) {
        return new SuggestedWordInfo(word, 1 /* score */,
                SuggestedWordInfo.KIND_CORRECTION,
                null /* sourceDict */,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */);
    }

    public void testGetSuggestedWordsExcludingTypedWord() {
        final String TYPED_WORD = "typed";
        final int NUMBER_OF_ADDED_SUGGESTIONS = 5;
        final int KIND_OF_SECOND_CORRECTION = SuggestedWordInfo.KIND_CORRECTION;
        final ArrayList<SuggestedWordInfo> list = new ArrayList<>();
        list.add(createTypedWordInfo(TYPED_WORD));
        for (int i = 0; i < NUMBER_OF_ADDED_SUGGESTIONS; ++i) {
            list.add(createCorrectionWordInfo(Integer.toString(i)));
        }

        final SuggestedWords words = new SuggestedWords(
                list, null /* rawSuggestions */,
                false /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isObsoleteSuggestions */,
                SuggestedWords.INPUT_STYLE_NONE);
        assertEquals(NUMBER_OF_ADDED_SUGGESTIONS + 1, words.size());
        assertEquals("typed", words.getWord(0));
        assertTrue(words.getInfo(0).isKindOf(SuggestedWordInfo.KIND_TYPED));
        assertEquals("0", words.getWord(1));
        assertTrue(words.getInfo(1).isKindOf(KIND_OF_SECOND_CORRECTION));
        assertEquals("4", words.getWord(5));
        assertTrue(words.getInfo(5).isKindOf(KIND_OF_SECOND_CORRECTION));

        final SuggestedWords wordsWithoutTyped =
                words.getSuggestedWordsExcludingTypedWordForRecorrection();
        // Make sure that the typed word has indeed been excluded, by testing the size of the
        // suggested words, the string and the kind of the top suggestion, which should match
        // the string and kind of what we inserted after the typed word.
        assertEquals(words.size() - 1, wordsWithoutTyped.size());
        assertEquals("0", wordsWithoutTyped.getWord(0));
        assertTrue(wordsWithoutTyped.getInfo(0).isKindOf(KIND_OF_SECOND_CORRECTION));
    }

    // Helper for testGetTransformedWordInfo
    private SuggestedWordInfo transformWordInfo(final String info,
            final int trailingSingleQuotesCount) {
        final SuggestedWordInfo suggestedWordInfo = createTypedWordInfo(info);
        final SuggestedWordInfo returnedWordInfo =
                Suggest.getTransformedSuggestedWordInfo(suggestedWordInfo,
                Locale.ENGLISH, false /* isAllUpperCase */, false /* isFirstCharCapitalized */,
                trailingSingleQuotesCount);
        assertEquals(suggestedWordInfo.mAutoCommitFirstWordConfidence,
                returnedWordInfo.mAutoCommitFirstWordConfidence);
        return returnedWordInfo;
    }

    public void testGetTransformedSuggestedWordInfo() {
        SuggestedWordInfo result = transformWordInfo("word", 0);
        assertEquals(result.mWord, "word");
        result = transformWordInfo("word", 1);
        assertEquals(result.mWord, "word'");
        result = transformWordInfo("word", 3);
        assertEquals(result.mWord, "word'''");
        result = transformWordInfo("didn't", 0);
        assertEquals(result.mWord, "didn't");
        result = transformWordInfo("didn't", 1);
        assertEquals(result.mWord, "didn't");
        result = transformWordInfo("didn't", 3);
        assertEquals(result.mWord, "didn't''");
    }

    public void testGetTypedWordInfoOrNull() {
        final String TYPED_WORD = "typed";
        final int NUMBER_OF_ADDED_SUGGESTIONS = 5;
        final ArrayList<SuggestedWordInfo> list = new ArrayList<>();
        list.add(createTypedWordInfo(TYPED_WORD));
        for (int i = 0; i < NUMBER_OF_ADDED_SUGGESTIONS; ++i) {
            list.add(createCorrectionWordInfo(Integer.toString(i)));
        }

        // Make sure getTypedWordInfoOrNull() returns non-null object.
        final SuggestedWords wordsWithTypedWord = new SuggestedWords(
                list, null /* rawSuggestions */,
                false /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isObsoleteSuggestions */,
                SuggestedWords.INPUT_STYLE_NONE);
        final SuggestedWordInfo typedWord = wordsWithTypedWord.getTypedWordInfoOrNull();
        assertNotNull(typedWord);
        assertEquals(TYPED_WORD, typedWord.mWord);

        // Make sure getTypedWordInfoOrNull() returns null.
        final SuggestedWords wordsWithoutTypedWord =
                wordsWithTypedWord.getSuggestedWordsExcludingTypedWordForRecorrection();
        assertNull(wordsWithoutTypedWord.getTypedWordInfoOrNull());

        // Make sure getTypedWordInfoOrNull() returns null.
        assertNull(SuggestedWords.EMPTY.getTypedWordInfoOrNull());

        final SuggestedWords emptySuggestedWords = new SuggestedWords(
                new ArrayList<SuggestedWordInfo>(), null /* rawSuggestions */,
                false /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isObsoleteSuggestions */,
                SuggestedWords.INPUT_STYLE_NONE);
        assertNull(emptySuggestedWords.getTypedWordInfoOrNull());

        assertNull(SuggestedWords.EMPTY.getTypedWordInfoOrNull());
    }
}
