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

import com.android.inputmethod.latin.EditingUtils.Range;

public class EditingUtilsTests extends AndroidTestCase {

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
         * @see android.view.inputmethod.InputConnectionWrapper#getExtractedText(ExtractedTextRequest, int)
         */
        @Override
        public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
            return mExtractedText;
        }
    }

    /************************** Tests ************************/

    /**
     * Test for getting previous word (for bigram suggestions)
     */
    public void testGetPreviousWord() {
        // If one of the following cases breaks, the bigram suggestions won't work.
        assertEquals(EditingUtils.getPreviousWord("abc def", sSeparators), "abc");
        assertNull(EditingUtils.getPreviousWord("abc", sSeparators));
        assertNull(EditingUtils.getPreviousWord("abc. def", sSeparators));

        // The following tests reflect the current behavior of the function
        // EditingUtils#getPreviousWord.
        // TODO: However at this time, the code does never go
        // into such a path, so it should be safe to change the behavior of
        // this function if needed - especially since it does not seem very
        // logical. These tests are just there to catch any unintentional
        // changes in the behavior of the EditingUtils#getPreviousWord method.
        assertEquals(EditingUtils.getPreviousWord("abc def ", sSeparators), "abc");
        assertEquals(EditingUtils.getPreviousWord("abc def.", sSeparators), "abc");
        assertEquals(EditingUtils.getPreviousWord("abc def .", sSeparators), "def");
        assertNull(EditingUtils.getPreviousWord("abc ", sSeparators));
    }

    /**
     * Test for getting the word before the cursor (for bigram)
     */
    public void testGetThisWord() {
        assertEquals(EditingUtils.getThisWord("abc def", sSeparators), "def");
        assertEquals(EditingUtils.getThisWord("abc def ", sSeparators), "def");
        assertNull(EditingUtils.getThisWord("abc def.", sSeparators));
        assertNull(EditingUtils.getThisWord("abc def .", sSeparators));
    }

    /**
     * Test logic in getting the word range at the cursor.
     */
    public void testGetWordRangeAtCursor() {
        ExtractedText et = new ExtractedText();
        InputConnection mockConnection;
        mockConnection = new MockConnection("word wo", "rd", et);
        et.startOffset = 0;
        et.selectionStart = 7;
        Range r;

        // basic case
        r = EditingUtils.getWordRangeAtCursor(mockConnection, " ", 0);
        assertEquals("word", r.mWord);
        r = null;

        // more than one word
        r = EditingUtils.getWordRangeAtCursor(mockConnection, " ", 1);
        assertEquals("word word", r.mWord);
        r = null;

        // tab character instead of space
        mockConnection = new MockConnection("one\tword\two", "rd", et);
        r = EditingUtils.getWordRangeAtCursor(mockConnection, "\t", 1);
        assertEquals("word\tword", r.mWord);
        r = null;

        // only one word doesn't go too far
        mockConnection = new MockConnection("one\tword\two", "rd", et);
        r = EditingUtils.getWordRangeAtCursor(mockConnection, "\t", 1);
        assertEquals("word\tword", r.mWord);
        r = null;

        // tab or space
        mockConnection = new MockConnection("one word\two", "rd", et);
        r = EditingUtils.getWordRangeAtCursor(mockConnection, " \t", 1);
        assertEquals("word\tword", r.mWord);
        r = null;

        // tab or space multiword
        mockConnection = new MockConnection("one word\two", "rd", et);
        r = EditingUtils.getWordRangeAtCursor(mockConnection, " \t", 2);
        assertEquals("one word\tword", r.mWord);
        r = null;

        // splitting on supplementary character
        final String supplementaryChar = "\uD840\uDC8A";
        mockConnection = new MockConnection("one word" + supplementaryChar + "wo", "rd", et);
        r = EditingUtils.getWordRangeAtCursor(mockConnection, supplementaryChar, 0);
        assertEquals("word", r.mWord);
        r = null;
    }
}
