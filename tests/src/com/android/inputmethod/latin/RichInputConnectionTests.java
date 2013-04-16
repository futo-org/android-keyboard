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

import android.inputmethodservice.InputMethodService;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import com.android.inputmethod.latin.RichInputConnection.Range;

@SmallTest
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

        @Override
        public boolean finishComposingText() {
            return true;
        }
    }

    private class MockInputMethodService extends InputMethodService {
        InputConnection mInputConnection;
        public void setInputConnection(final InputConnection inputConnection) {
            mInputConnection = inputConnection;
        }
        @Override
        public InputConnection getCurrentInputConnection() {
            return mInputConnection;
        }
    }

    /************************** Tests ************************/

    /**
     * Test for getting previous word (for bigram suggestions)
     */
    public void testGetPreviousWord() {
        // If one of the following cases breaks, the bigram suggestions won't work.
        assertEquals(RichInputConnection.getNthPreviousWord("abc def", sSeparators, 2), "abc");
        assertNull(RichInputConnection.getNthPreviousWord("abc", sSeparators, 2));
        assertNull(RichInputConnection.getNthPreviousWord("abc. def", sSeparators, 2));

        // The following tests reflect the current behavior of the function
        // RichInputConnection#getNthPreviousWord.
        // TODO: However at this time, the code does never go
        // into such a path, so it should be safe to change the behavior of
        // this function if needed - especially since it does not seem very
        // logical. These tests are just there to catch any unintentional
        // changes in the behavior of the RichInputConnection#getPreviousWord method.
        assertEquals(RichInputConnection.getNthPreviousWord("abc def ", sSeparators, 2), "abc");
        assertEquals(RichInputConnection.getNthPreviousWord("abc def.", sSeparators, 2), "abc");
        assertEquals(RichInputConnection.getNthPreviousWord("abc def .", sSeparators, 2), "def");
        assertNull(RichInputConnection.getNthPreviousWord("abc ", sSeparators, 2));

        assertEquals(RichInputConnection.getNthPreviousWord("abc def", sSeparators, 1), "def");
        assertEquals(RichInputConnection.getNthPreviousWord("abc def ", sSeparators, 1), "def");
        assertNull(RichInputConnection.getNthPreviousWord("abc def.", sSeparators, 1));
        assertNull(RichInputConnection.getNthPreviousWord("abc def .", sSeparators, 1));
    }

    /**
     * Test logic in getting the word range at the cursor.
     */
    public void testGetWordRangeAtCursor() {
        ExtractedText et = new ExtractedText();
        final MockInputMethodService mockInputMethodService = new MockInputMethodService();
        final RichInputConnection ic = new RichInputConnection(mockInputMethodService);
        mockInputMethodService.setInputConnection(new MockConnection("word wo", "rd", et));
        et.startOffset = 0;
        et.selectionStart = 7;
        Range r;

        ic.beginBatchEdit();
        // basic case
        r = ic.getWordRangeAtCursor(" ", 0);
        assertTrue(TextUtils.equals("word", r.mWord));

        // more than one word
        r = ic.getWordRangeAtCursor(" ", 1);
        assertTrue(TextUtils.equals("word word", r.mWord));
        ic.endBatchEdit();

        // tab character instead of space
        mockInputMethodService.setInputConnection(new MockConnection("one\tword\two", "rd", et));
        ic.beginBatchEdit();
        r = ic.getWordRangeAtCursor("\t", 1);
        ic.endBatchEdit();
        assertTrue(TextUtils.equals("word\tword", r.mWord));

        // only one word doesn't go too far
        mockInputMethodService.setInputConnection(new MockConnection("one\tword\two", "rd", et));
        ic.beginBatchEdit();
        r = ic.getWordRangeAtCursor("\t", 1);
        ic.endBatchEdit();
        assertTrue(TextUtils.equals("word\tword", r.mWord));

        // tab or space
        mockInputMethodService.setInputConnection(new MockConnection("one word\two", "rd", et));
        ic.beginBatchEdit();
        r = ic.getWordRangeAtCursor(" \t", 1);
        ic.endBatchEdit();
        assertTrue(TextUtils.equals("word\tword", r.mWord));

        // tab or space multiword
        mockInputMethodService.setInputConnection(new MockConnection("one word\two", "rd", et));
        ic.beginBatchEdit();
        r = ic.getWordRangeAtCursor(" \t", 2);
        ic.endBatchEdit();
        assertTrue(TextUtils.equals("one word\tword", r.mWord));

        // splitting on supplementary character
        final String supplementaryChar = "\uD840\uDC8A";
        mockInputMethodService.setInputConnection(
                new MockConnection("one word" + supplementaryChar + "wo", "rd", et));
        ic.beginBatchEdit();
        r = ic.getWordRangeAtCursor(supplementaryChar, 0);
        ic.endBatchEdit();
        assertTrue(TextUtils.equals("word", r.mWord));
    }
}
