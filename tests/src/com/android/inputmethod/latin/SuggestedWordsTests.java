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

import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.Locale;

@SmallTest
public class SuggestedWordsTests extends AndroidTestCase {
    public void testGetSuggestedWordsExcludingTypedWord() {
        final String TYPED_WORD = "typed";
        final int TYPED_WORD_FREQ = 5;
        final int NUMBER_OF_ADDED_SUGGESTIONS = 5;
        final ArrayList<SuggestedWordInfo> list = CollectionUtils.newArrayList();
        list.add(new SuggestedWordInfo(TYPED_WORD, TYPED_WORD_FREQ,
                SuggestedWordInfo.KIND_TYPED, ""));
        for (int i = 0; i < NUMBER_OF_ADDED_SUGGESTIONS; ++i) {
            list.add(new SuggestedWordInfo("" + i, 1, SuggestedWordInfo.KIND_CORRECTION, ""));
        }

        final SuggestedWords words = new SuggestedWords(
                list,
                false /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */,
                false /* isPrediction*/);
        assertEquals(NUMBER_OF_ADDED_SUGGESTIONS + 1, words.size());
        assertEquals("typed", words.getWord(0));
        assertEquals(SuggestedWordInfo.KIND_TYPED, words.getInfo(0).mKind);
        assertEquals("0", words.getWord(1));
        assertEquals(SuggestedWordInfo.KIND_CORRECTION, words.getInfo(1).mKind);
        assertEquals("4", words.getWord(5));
        assertEquals(SuggestedWordInfo.KIND_CORRECTION, words.getInfo(5).mKind);

        final SuggestedWords wordsWithoutTyped = words.getSuggestedWordsExcludingTypedWord();
        assertEquals(words.size() - 1, wordsWithoutTyped.size());
        assertEquals("0", wordsWithoutTyped.getWord(0));
        assertEquals(SuggestedWordInfo.KIND_CORRECTION, wordsWithoutTyped.getInfo(0).mKind);
    }
}
