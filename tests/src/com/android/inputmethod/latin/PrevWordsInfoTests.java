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

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.PrevWordsInfo.WordInfo;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class PrevWordsInfoTests extends AndroidTestCase {
    public void testConstruct() {
        assertEquals(new PrevWordsInfo(new WordInfo("a")), new PrevWordsInfo(new WordInfo("a")));
        assertEquals(new PrevWordsInfo(WordInfo.BEGINNING_OF_SENTENCE),
                new PrevWordsInfo(WordInfo.BEGINNING_OF_SENTENCE));
        assertEquals(new PrevWordsInfo(WordInfo.EMPTY_WORD_INFO),
                new PrevWordsInfo(WordInfo.EMPTY_WORD_INFO));
        assertEquals(new PrevWordsInfo(WordInfo.EMPTY_WORD_INFO),
                new PrevWordsInfo(WordInfo.EMPTY_WORD_INFO));
    }

    public void testIsBeginningOfSentenceContext() {
        assertFalse(new PrevWordsInfo().isBeginningOfSentenceContext());
        assertTrue(new PrevWordsInfo(WordInfo.BEGINNING_OF_SENTENCE)
                .isBeginningOfSentenceContext());
        assertTrue(PrevWordsInfo.BEGINNING_OF_SENTENCE.isBeginningOfSentenceContext());
        assertFalse(new PrevWordsInfo(new WordInfo("a")).isBeginningOfSentenceContext());
        assertFalse(new PrevWordsInfo(new WordInfo("")).isBeginningOfSentenceContext());
        assertFalse(new PrevWordsInfo(WordInfo.EMPTY_WORD_INFO).isBeginningOfSentenceContext());
        assertTrue(new PrevWordsInfo(WordInfo.BEGINNING_OF_SENTENCE, new WordInfo("a"))
                .isBeginningOfSentenceContext());
        assertFalse(new PrevWordsInfo(new WordInfo("a"), WordInfo.BEGINNING_OF_SENTENCE)
                .isBeginningOfSentenceContext());
        assertFalse(new PrevWordsInfo(WordInfo.EMPTY_WORD_INFO, WordInfo.BEGINNING_OF_SENTENCE)
                .isBeginningOfSentenceContext());
    }

    public void testGetNextPrevWordsInfo() {
        final PrevWordsInfo prevWordsInfo_a = new PrevWordsInfo(new WordInfo("a"));
        final PrevWordsInfo prevWordsInfo_b_a =
                prevWordsInfo_a.getNextPrevWordsInfo(new WordInfo("b"));
        assertEquals("b", prevWordsInfo_b_a.getNthPrevWord(1));
        assertEquals("a", prevWordsInfo_b_a.getNthPrevWord(2));
        final PrevWordsInfo prevWordsInfo_bos_b =
                prevWordsInfo_b_a.getNextPrevWordsInfo(WordInfo.BEGINNING_OF_SENTENCE);
        assertTrue(prevWordsInfo_bos_b.isBeginningOfSentenceContext());
        assertEquals("b", prevWordsInfo_bos_b.getNthPrevWord(2));
        final PrevWordsInfo prevWordsInfo_c_bos =
                prevWordsInfo_b_a.getNextPrevWordsInfo(new WordInfo("c"));
        assertEquals("c", prevWordsInfo_c_bos.getNthPrevWord(1));
    }
}
