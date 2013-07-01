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

import com.android.inputmethod.latin.utils.TextRange;

import android.inputmethodservice.InputMethodService;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import java.util.Locale;

@SmallTest
public class RichInputConnectionAndTextRangeTests extends AndroidTestCase {

    // The following is meant to be a reasonable default for
    // the "word_separators" resource.
    private static final String sSeparators = ".,:;!?-";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    private class MockConnection extends InputConnectionWrapper {
        final CharSequence mTextBefore;
        final CharSequence mTextAfter;
        final ExtractedText mExtractedText;

        public MockConnection(final CharSequence text, final int cursorPosition) {
            super(null, false);
            // Interaction of spans with Parcels is completely non-trivial, but in the actual case
            // the CharSequences do go through Parcels because they go through IPC. There
            // are some significant differences between the behavior of Spanned objects that
            // have and that have not gone through parceling, so it's much easier to simulate
            // the environment with Parcels than try to emulate things by hand.
            final Parcel p = Parcel.obtain();
            TextUtils.writeToParcel(text.subSequence(0, cursorPosition), p, 0 /* flags */);
            TextUtils.writeToParcel(text.subSequence(cursorPosition, text.length()), p,
                    0 /* flags */);
            final byte[] marshalled = p.marshall();
            p.unmarshall(marshalled, 0, marshalled.length);
            p.setDataPosition(0);
            mTextBefore = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(p);
            mTextAfter = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(p);
            mExtractedText = null;
            p.recycle();
        }

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
        TextRange r;

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

    /**
     * Test logic in getting the word range at the cursor.
     */
    public void testGetSuggestionSpansAtWord() {
        helpTestGetSuggestionSpansAtWord(10);
        helpTestGetSuggestionSpansAtWord(12);
        helpTestGetSuggestionSpansAtWord(15);
        helpTestGetSuggestionSpansAtWord(16);
    }

    private void helpTestGetSuggestionSpansAtWord(final int cursorPos) {
        final MockInputMethodService mockInputMethodService = new MockInputMethodService();
        final RichInputConnection ic = new RichInputConnection(mockInputMethodService);

        final String[] SUGGESTIONS1 = { "swing", "strong" };
        final String[] SUGGESTIONS2 = { "storing", "strung" };

        // Test the usual case.
        SpannableString text = new SpannableString("This is a string for test");
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS1, 0 /* flags */),
                10 /* start */, 16 /* end */, 0 /* flags */);
        mockInputMethodService.setInputConnection(new MockConnection(text, cursorPos));
        TextRange r;
        SuggestionSpan[] suggestions;

        r = ic.getWordRangeAtCursor(" ", 0);
        suggestions = r.getSuggestionSpansAtWord();
        assertEquals(suggestions.length, 1);
        MoreAsserts.assertEquals(suggestions[0].getSuggestions(), SUGGESTIONS1);

        // Test the case with 2 suggestion spans in the same place.
        text = new SpannableString("This is a string for test");
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS1, 0 /* flags */),
                10 /* start */, 16 /* end */, 0 /* flags */);
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS2, 0 /* flags */),
                10 /* start */, 16 /* end */, 0 /* flags */);
        mockInputMethodService.setInputConnection(new MockConnection(text, cursorPos));
        r = ic.getWordRangeAtCursor(" ", 0);
        suggestions = r.getSuggestionSpansAtWord();
        assertEquals(suggestions.length, 2);
        MoreAsserts.assertEquals(suggestions[0].getSuggestions(), SUGGESTIONS1);
        MoreAsserts.assertEquals(suggestions[1].getSuggestions(), SUGGESTIONS2);

        // Test a case with overlapping spans, 2nd extending past the start of the word
        text = new SpannableString("This is a string for test");
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS1, 0 /* flags */),
                10 /* start */, 16 /* end */, 0 /* flags */);
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS2, 0 /* flags */),
                5 /* start */, 16 /* end */, 0 /* flags */);
        mockInputMethodService.setInputConnection(new MockConnection(text, cursorPos));
        r = ic.getWordRangeAtCursor(" ", 0);
        suggestions = r.getSuggestionSpansAtWord();
        assertEquals(suggestions.length, 1);
        MoreAsserts.assertEquals(suggestions[0].getSuggestions(), SUGGESTIONS1);

        // Test a case with overlapping spans, 2nd extending past the end of the word
        text = new SpannableString("This is a string for test");
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS1, 0 /* flags */),
                10 /* start */, 16 /* end */, 0 /* flags */);
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS2, 0 /* flags */),
                10 /* start */, 20 /* end */, 0 /* flags */);
        mockInputMethodService.setInputConnection(new MockConnection(text, cursorPos));
        r = ic.getWordRangeAtCursor(" ", 0);
        suggestions = r.getSuggestionSpansAtWord();
        assertEquals(suggestions.length, 1);
        MoreAsserts.assertEquals(suggestions[0].getSuggestions(), SUGGESTIONS1);

        // Test a case with overlapping spans, 2nd extending past both ends of the word
        text = new SpannableString("This is a string for test");
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS1, 0 /* flags */),
                10 /* start */, 16 /* end */, 0 /* flags */);
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS2, 0 /* flags */),
                5 /* start */, 20 /* end */, 0 /* flags */);
        mockInputMethodService.setInputConnection(new MockConnection(text, cursorPos));
        r = ic.getWordRangeAtCursor(" ", 0);
        suggestions = r.getSuggestionSpansAtWord();
        assertEquals(suggestions.length, 1);
        MoreAsserts.assertEquals(suggestions[0].getSuggestions(), SUGGESTIONS1);

        // Test a case with overlapping spans, none right on the word
        text = new SpannableString("This is a string for test");
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS1, 0 /* flags */),
                5 /* start */, 16 /* end */, 0 /* flags */);
        text.setSpan(new SuggestionSpan(Locale.ENGLISH, SUGGESTIONS2, 0 /* flags */),
                5 /* start */, 20 /* end */, 0 /* flags */);
        mockInputMethodService.setInputConnection(new MockConnection(text, cursorPos));
        r = ic.getWordRangeAtCursor(" ", 0);
        suggestions = r.getSuggestionSpansAtWord();
        assertEquals(suggestions.length, 0);
    }
}
