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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.NgramContext.WordInfo;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;
import com.android.inputmethod.latin.utils.NgramContextUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NgramContextTests {

    @Test
    public void testConstruct() {
        assertEquals(new NgramContext(new WordInfo("a")), new NgramContext(new WordInfo("a")));
        assertEquals(new NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO),
                new NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO));
        assertEquals(new NgramContext(WordInfo.EMPTY_WORD_INFO),
                new NgramContext(WordInfo.EMPTY_WORD_INFO));
        assertEquals(new NgramContext(WordInfo.EMPTY_WORD_INFO),
                new NgramContext(WordInfo.EMPTY_WORD_INFO));
    }

    @Test
    public void testIsBeginningOfSentenceContext() {
        assertFalse(new NgramContext().isBeginningOfSentenceContext());
        assertTrue(new NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)
                .isBeginningOfSentenceContext());
        assertTrue(NgramContext.BEGINNING_OF_SENTENCE.isBeginningOfSentenceContext());
        assertFalse(new NgramContext(new WordInfo("a")).isBeginningOfSentenceContext());
        assertFalse(new NgramContext(new WordInfo("")).isBeginningOfSentenceContext());
        assertFalse(new NgramContext(WordInfo.EMPTY_WORD_INFO).isBeginningOfSentenceContext());
        assertTrue(new NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO, new WordInfo("a"))
                .isBeginningOfSentenceContext());
        assertFalse(new NgramContext(new WordInfo("a"), WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)
                .isBeginningOfSentenceContext());
        assertFalse(new NgramContext(
                WordInfo.EMPTY_WORD_INFO, WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)
                .isBeginningOfSentenceContext());
    }

    @Test
    public void testGetNextNgramContext() {
        final NgramContext ngramContext_a = new NgramContext(new WordInfo("a"));
        final NgramContext ngramContext_b_a =
                ngramContext_a.getNextNgramContext(new WordInfo("b"));
        assertEquals("b", ngramContext_b_a.getNthPrevWord(1));
        assertEquals("a", ngramContext_b_a.getNthPrevWord(2));
        final NgramContext ngramContext_bos_b =
                ngramContext_b_a.getNextNgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO);
        assertTrue(ngramContext_bos_b.isBeginningOfSentenceContext());
        assertEquals("b", ngramContext_bos_b.getNthPrevWord(2));
        final NgramContext ngramContext_c_bos =
                ngramContext_b_a.getNextNgramContext(new WordInfo("c"));
        assertEquals("c", ngramContext_c_bos.getNthPrevWord(1));
    }

    @Test
    public void testExtractPrevWordsContextTest() {
        final NgramContext ngramContext_bos =
                new NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO);
        assertEquals("<S>", ngramContext_bos.extractPrevWordsContext());
        final NgramContext ngramContext_a = new NgramContext(new WordInfo("a"));
        final NgramContext ngramContext_b_a =
                ngramContext_a.getNextNgramContext(new WordInfo("b"));
        assertEquals("b", ngramContext_b_a.getNthPrevWord(1));
        assertEquals("a", ngramContext_b_a.getNthPrevWord(2));
        assertEquals("a b", ngramContext_b_a.extractPrevWordsContext());

        final NgramContext ngramContext_bos_b =
                ngramContext_b_a.getNextNgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO);
        assertTrue(ngramContext_bos_b.isBeginningOfSentenceContext());
        assertEquals("b", ngramContext_bos_b.getNthPrevWord(2));
        assertEquals("a b <S>", ngramContext_bos_b.extractPrevWordsContext());

        final NgramContext ngramContext_empty = new NgramContext(WordInfo.EMPTY_WORD_INFO);
        assertEquals("", ngramContext_empty.extractPrevWordsContext());
        final NgramContext ngramContext_a_empty =
                ngramContext_empty.getNextNgramContext(new WordInfo("a"));
        assertEquals("a", ngramContext_a_empty.getNthPrevWord(1));
        assertEquals("a", ngramContext_a_empty.extractPrevWordsContext());
    }

    @Test
    public void testExtractPrevWordsContextArray() {
        final NgramContext ngramContext_bos =
                new NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO);
        assertEquals("<S>", ngramContext_bos.extractPrevWordsContext());
        assertEquals(1, ngramContext_bos.extractPrevWordsContextArray().length);
        final NgramContext ngramContext_a = new NgramContext(new WordInfo("a"));
        final NgramContext ngramContext_b_a =
                ngramContext_a.getNextNgramContext(new WordInfo("b"));
        assertEquals(2, ngramContext_b_a.extractPrevWordsContextArray().length);
        assertEquals("b", ngramContext_b_a.getNthPrevWord(1));
        assertEquals("a", ngramContext_b_a.getNthPrevWord(2));
        assertEquals("a", ngramContext_b_a.extractPrevWordsContextArray()[0]);
        assertEquals("b", ngramContext_b_a.extractPrevWordsContextArray()[1]);

        final NgramContext ngramContext_bos_b =
                ngramContext_b_a.getNextNgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO);
        assertTrue(ngramContext_bos_b.isBeginningOfSentenceContext());
        assertEquals(3, ngramContext_bos_b.extractPrevWordsContextArray().length);
        assertEquals("b", ngramContext_bos_b.getNthPrevWord(2));
        assertEquals("a", ngramContext_bos_b.extractPrevWordsContextArray()[0]);
        assertEquals("b", ngramContext_bos_b.extractPrevWordsContextArray()[1]);
        assertEquals("<S>", ngramContext_bos_b.extractPrevWordsContextArray()[2]);

        final NgramContext ngramContext_empty = new NgramContext(WordInfo.EMPTY_WORD_INFO);
        assertEquals(0, ngramContext_empty.extractPrevWordsContextArray().length);
        final NgramContext ngramContext_a_empty =
                ngramContext_empty.getNextNgramContext(new WordInfo("a"));
        assertEquals(1, ngramContext_a_empty.extractPrevWordsContextArray().length);
        assertEquals("a", ngramContext_a_empty.extractPrevWordsContextArray()[0]);
    }

    @Test
    public void testGetNgramContextFromNthPreviousWord() {
        SpacingAndPunctuations spacingAndPunctuations = new SpacingAndPunctuations(
                InstrumentationRegistry.getTargetContext().getResources());
        assertEquals("<S>", NgramContextUtils.getNgramContextFromNthPreviousWord("",
                spacingAndPunctuations, 1).extractPrevWordsContext());
        assertEquals("<S> b", NgramContextUtils.getNgramContextFromNthPreviousWord("a. b ",
                spacingAndPunctuations, 1).extractPrevWordsContext());
        assertEquals("<S> b", NgramContextUtils.getNgramContextFromNthPreviousWord("a? b ",
                spacingAndPunctuations, 1).extractPrevWordsContext());
        assertEquals("<S> b", NgramContextUtils.getNgramContextFromNthPreviousWord("a! b ",
                spacingAndPunctuations, 1).extractPrevWordsContext());
        assertEquals("<S> b", NgramContextUtils.getNgramContextFromNthPreviousWord("a\nb ",
                spacingAndPunctuations, 1).extractPrevWordsContext());
        assertEquals("<S> a b", NgramContextUtils.getNgramContextFromNthPreviousWord("a b ",
                spacingAndPunctuations, 1).extractPrevWordsContext());
        assertFalse(NgramContextUtils
                .getNgramContextFromNthPreviousWord("a b c d e", spacingAndPunctuations, 1)
                .extractPrevWordsContext().startsWith("<S>"));
    }
}
