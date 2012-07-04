/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.test.AndroidTestCase;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import com.android.inputmethod.latin.RichInputConnection.Range;

public class RichInputConnectionTests extends AndroidTestCase {

    // The following is meant to be a reasonable default for
    // the "word_separators" resource.
    private static final String sSeparators = ".,:;!?-";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    private class MockConnection extends InputConnectionWrapper {
        final String mTextBefore;
        final String mTextAfter;
        final ExtractedText mExtractedText;

        public MockConnection(String textBefore, String textAfter, ExtractedText extractedText) {
            super(null, false);
            mTextBefore = textBefore;
            mTextAfter = textAfter;
            mExtractedText = extractedText;
        }

        /* (non-Javadoc)
         * @see android.view.inputmethod.InputConnectionWrapper#getTextBeforeCursor(int, int)
         */
        @Override
        public CharSequence getTextBeforeCursor(int n, int flags) {
            return mTextBefore;
        }

        /* (non-Javadoc)
         * @see android.view.inputmethod.InputConnectionWrapper#getTextAfterCursor(int, int)
         */
        @Override
        public CharSequence getTextAfterCursor(int n, int flags) {
            return mTextAfter;
        }

        /* (non-Javadoc)
         * @see android.view.inputmethod.InputConnectionWrapper#getExtractedText(
         *         ExtractedTextRequest, int)
         */
        @Override
        public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
            return mExtractedText;
        }

        @Override
        public boolean beginBatchEdit() {
            return true;
        }

        @Override
        public boolean endBatchEdit() {
            return true;
        }
    }

    /************************** Tests ************************/

    /**
     * Test for getting previous word (for bigram suggestions)
     */
    public void testGetPreviousWord() {
        // If one of the following cases breaks, the bigram suggestions won't work.
        assertEquals(RichInputConnection.getPreviousWord("abc def", sSeparators), "abc");
        assertNull(RichInputConnection.getPreviousWord("abc", sSeparators));
        assertNull(RichInputConnection.getPreviousWord("abc. def", sSeparators));

        // The following tests reflect the current behavior of the function
        // RichInputConnection#getPreviousWord.
        // TODO: However at this time, the code does never go
        // into such a path, so it should be safe to change the behavior of
        // this function if needed - especially since it does not seem very
        // logical. These tests are just there to catch any unintentional
        // changes in the behavior of the RichInputConnection#getPreviousWord method.
        assertEquals(RichInputConnection.getPreviousWord("abc def ", sSeparators), "abc");
        assertEquals(RichInputConnection.getPreviousWord("abc def.", sSeparators), "abc");
        assertEquals(RichInputConnection.getPreviousWord("abc def .", sSeparators), "def");
        assertNull(RichInputConnection.getPreviousWord("abc ", sSeparators));
    }

    /**
     * Test for getting the word before the cursor (for bigram)
     */
    public void testGetThisWord() {
        assertEquals(RichInputConnection.getThisWord("abc def", sSeparators), "def");
        assertEquals(RichInputConnection.getThisWord("abc def ", sSeparators), "def");
        assertNull(RichInputConnection.getThisWord("abc def.", sSeparators));
        assertNull(RichInputConnection.getThisWord("abc def .", sSeparators));
    }

    /**
     * Test logic in getting the word range at the cursor.
     */
    public void testGetWordRangeAtCursor() {
        ExtractedText et = new ExtractedText();
        final RichInputConnection ic = new RichInputConnection();
        InputConnection mockConnection;
        mockConnection = new MockConnection("word wo", "rd", et);
        et.startOffset = 0;
        et.selectionStart = 7;
        Range r;

        ic.beginBatchEdit(mockConnection);
        // basic case
        r = ic.getWordRangeAtCursor(" ", 0);
        assertEquals("word", r.mWord);

        // more than one word
        r = ic.getWordRangeAtCursor(" ", 1);
        assertEquals("word word", r.mWord);
        ic.endBatchEdit();

        // tab character instead of space
        mockConnection = new MockConnection("one\tword\two", "rd", et);
        ic.beginBatchEdit(mockConnection);
        r = ic.getWordRangeAtCursor("\t", 1);
        ic.endBatchEdit();
        assertEquals("word\tword", r.mWord);

        // only one word doesn't go too far
        mockConnection = new MockConnection("one\tword\two", "rd", et);
        ic.beginBatchEdit(mockConnection);
        r = ic.getWordRangeAtCursor("\t", 1);
        ic.endBatchEdit();
        assertEquals("word\tword", r.mWord);

        // tab or space
        mockConnection = new MockConnection("one word\two", "rd", et);
        ic.beginBatchEdit(mockConnection);
        r = ic.getWordRangeAtCursor(" \t", 1);
        ic.endBatchEdit();
        assertEquals("word\tword", r.mWord);

        // tab or space multiword
        mockConnection = new MockConnection("one word\two", "rd", et);
        ic.beginBatchEdit(mockConnection);
        r = ic.getWordRangeAtCursor(" \t", 2);
        ic.endBatchEdit();
        assertEquals("one word\tword", r.mWord);

        // splitting on supplementary character
        final String supplementaryChar = "\uD840\uDC8A";
        mockConnection = new MockConnection("one word" + supplementaryChar + "wo", "rd", et);
        ic.beginBatchEdit(mockConnection);
        r = ic.getWordRangeAtCursor(supplementaryChar, 0);
        ic.endBatchEdit();
        assertEquals("word", r.mWord);
    }
}
