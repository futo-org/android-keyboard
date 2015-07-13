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

import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.StringUtils;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;
import com.android.inputmethod.latin.utils.NgramContextUtils;
import com.android.inputmethod.latin.utils.RunInLocale;
import com.android.inputmethod.latin.utils.ScriptUtils;
import com.android.inputmethod.latin.utils.TextRange;

import java.util.Locale;

@SmallTest
public class RichInputConnectionAndTextRangeTests extends AndroidTestCase {

    // The following is meant to be a reasonable default for
    // the "word_separators" resource.
    private SpacingAndPunctuations mSpacingAndPunctuations;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final RunInLocale<SpacingAndPunctuations> job = new RunInLocale<SpacingAndPunctuations>() {
            @Override
            protected SpacingAndPunctuations job(final Resources res) {
                return new SpacingAndPunctuations(res);
            }
        };
        final Resources res = getContext().getResources();
        mSpacingAndPunctuations = job.runInLocale(res, Locale.ENGLISH);
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

        public int cursorPos() {
            return mTextBefore.length();
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

    static class MockInputMethodService extends InputMethodService {
        private MockConnection mMockConnection;
        public void setInputConnection(final MockConnection mockConnection) {
            mMockConnection = mockConnection;
        }
        public int cursorPos() {
            return mMockConnection.cursorPos();
        }
        @Override
        public InputConnection getCurrentInputConnection() {
            return mMockConnection;
        }
    }

    /************************** Tests ************************/

    /**
     * Test for getting previous word (for bigram suggestions)
     */
    public void testGetPreviousWord() {
        // If one of the following cases breaks, the bigram suggestions won't work.
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def", mSpacingAndPunctuations, 2).getNthPrevWord(1), "abc");
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc", mSpacingAndPunctuations, 2), NgramContext.BEGINNING_OF_SENTENCE);
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc. def", mSpacingAndPunctuations, 2), NgramContext.BEGINNING_OF_SENTENCE);

        assertFalse(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def", mSpacingAndPunctuations, 2).isBeginningOfSentenceContext());
        assertTrue(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc", mSpacingAndPunctuations, 2).isBeginningOfSentenceContext());

        // For n-gram
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def", mSpacingAndPunctuations, 1).getNthPrevWord(1), "def");
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def", mSpacingAndPunctuations, 1).getNthPrevWord(2), "abc");
        assertTrue(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def", mSpacingAndPunctuations, 2).isNthPrevWordBeginningOfSentence(2));

        // The following tests reflect the current behavior of the function
        // RichInputConnection#getNthPreviousWord.
        // TODO: However at this time, the code does never go
        // into such a path, so it should be safe to change the behavior of
        // this function if needed - especially since it does not seem very
        // logical. These tests are just there to catch any unintentional
        // changes in the behavior of the RichInputConnection#getPreviousWord method.
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def ", mSpacingAndPunctuations, 2).getNthPrevWord(1), "abc");
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def.", mSpacingAndPunctuations, 2).getNthPrevWord(1), "abc");
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def .", mSpacingAndPunctuations, 2).getNthPrevWord(1), "def");
        assertTrue(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc ", mSpacingAndPunctuations, 2).isBeginningOfSentenceContext());

        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def", mSpacingAndPunctuations, 1).getNthPrevWord(1), "def");
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def ", mSpacingAndPunctuations, 1).getNthPrevWord(1), "def");
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc 'def", mSpacingAndPunctuations, 1).getNthPrevWord(1), "'def");
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def.", mSpacingAndPunctuations, 1), NgramContext.BEGINNING_OF_SENTENCE);
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc def .", mSpacingAndPunctuations, 1), NgramContext.BEGINNING_OF_SENTENCE);
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc, def", mSpacingAndPunctuations, 2), NgramContext.EMPTY_PREV_WORDS_INFO);
        // question mark is treated as the end of the sentence. Hence, beginning of the
        // sentence is expected.
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc? def", mSpacingAndPunctuations, 2), NgramContext.BEGINNING_OF_SENTENCE);
        // Exclamation mark is treated as the end of the sentence. Hence, beginning of the
        // sentence is expected.
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc! def", mSpacingAndPunctuations, 2), NgramContext.BEGINNING_OF_SENTENCE);
        assertEquals(NgramContextUtils.getNgramContextFromNthPreviousWord(
                "abc 'def", mSpacingAndPunctuations, 2), NgramContext.EMPTY_PREV_WORDS_INFO);
    }

    public void testGetWordRangeAtCursor() {
        /**
         * Test logic in getting the word range at the cursor.
         */
        final SpacingAndPunctuations SPACE = new SpacingAndPunctuations(
                mSpacingAndPunctuations, new int[] { Constants.CODE_SPACE });
        final SpacingAndPunctuations TAB = new SpacingAndPunctuations(
                mSpacingAndPunctuations, new int[] { Constants.CODE_TAB });
        // A character that needs surrogate pair to represent its code point (U+2008A).
        final String SUPPLEMENTARY_CHAR_STRING = "\uD840\uDC8A";
        final SpacingAndPunctuations SUPPLEMENTARY_CHAR = new SpacingAndPunctuations(
                mSpacingAndPunctuations, StringUtils.toSortedCodePointArray(
                        SUPPLEMENTARY_CHAR_STRING));
        final String HIRAGANA_WORD = "\u3042\u3044\u3046\u3048\u304A"; // あいうえお
        final String GREEK_WORD = "\u03BA\u03B1\u03B9"; // και

        ExtractedText et = new ExtractedText();
        final MockInputMethodService mockInputMethodService = new MockInputMethodService();
        final RichInputConnection ic = new RichInputConnection(mockInputMethodService);
        mockInputMethodService.setInputConnection(new MockConnection("word wo", "rd", et));
        et.startOffset = 0;
        et.selectionStart = 7;
        TextRange r;

        ic.beginBatchEdit();
        // basic case
        r = ic.getWordRangeAtCursor(SPACE, ScriptUtils.SCRIPT_LATIN);
        assertTrue(TextUtils.equals("word", r.mWord));

        // tab character instead of space
        mockInputMethodService.setInputConnection(new MockConnection("one\tword\two", "rd", et));
        ic.beginBatchEdit();
        r = ic.getWordRangeAtCursor(TAB, ScriptUtils.SCRIPT_LATIN);
        ic.endBatchEdit();
        assertTrue(TextUtils.equals("word", r.mWord));

        // splitting on supplementary character
        mockInputMethodService.setInputConnection(
                new MockConnection("one word" + SUPPLEMENTARY_CHAR_STRING + "wo", "rd", et));
        ic.beginBatchEdit();
        r = ic.getWordRangeAtCursor(SUPPLEMENTARY_CHAR, ScriptUtils.SCRIPT_LATIN);
        ic.endBatchEdit();
        assertTrue(TextUtils.equals("word", r.mWord));

        // split on chars outside the specified script
        mockInputMethodService.setInputConnection(
                new MockConnection(HIRAGANA_WORD + "wo", "rd" + GREEK_WORD, et));
        ic.beginBatchEdit();
        r = ic.getWordRangeAtCursor(SUPPLEMENTARY_CHAR, ScriptUtils.SCRIPT_LATIN);
        ic.endBatchEdit();
        assertTrue(TextUtils.equals("word", r.mWord));

        // likewise for greek
        mockInputMethodService.setInputConnection(
                new MockConnection("text" + GREEK_WORD, "text", et));
        ic.beginBatchEdit();
        r = ic.getWordRangeAtCursor(SUPPLEMENTARY_CHAR, ScriptUtils.SCRIPT_GREEK);
        ic.endBatchEdit();
        assertTrue(TextUtils.equals(GREEK_WORD, r.mWord));
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
        final SpacingAndPunctuations SPACE = new SpacingAndPunctuations(
                mSpacingAndPunctuations, new int[] { Constants.CODE_SPACE });
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

        r = ic.getWordRangeAtCursor(SPACE, ScriptUtils.SCRIPT_LATIN);
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
        r = ic.getWordRangeAtCursor(SPACE, ScriptUtils.SCRIPT_LATIN);
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
        r = ic.getWordRangeAtCursor(SPACE, ScriptUtils.SCRIPT_LATIN);
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
        r = ic.getWordRangeAtCursor(SPACE, ScriptUtils.SCRIPT_LATIN);
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
        r = ic.getWordRangeAtCursor(SPACE, ScriptUtils.SCRIPT_LATIN);
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
        r = ic.getWordRangeAtCursor(SPACE, ScriptUtils.SCRIPT_LATIN);
        suggestions = r.getSuggestionSpansAtWord();
        assertEquals(suggestions.length, 0);
    }

    public void testCursorTouchingWord() {
        final MockInputMethodService ims = new MockInputMethodService();
        final RichInputConnection ic = new RichInputConnection(ims);
        final SpacingAndPunctuations sap = mSpacingAndPunctuations;

        ims.setInputConnection(new MockConnection("users", 5));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertTrue(ic.isCursorTouchingWord(sap, true /* checkTextAfter */));

        ims.setInputConnection(new MockConnection("users'", 5));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertTrue(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection("users'", 6));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertTrue(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection("'users'", 6));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertTrue(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection("'users'", 7));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertTrue(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection("users '", 6));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertFalse(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection("users '", 7));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertFalse(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection("re-", 3));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertTrue(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection("re--", 4));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertFalse(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection("-", 1));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertFalse(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection("--", 2));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertFalse(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection(" -", 2));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertFalse(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection(" --", 3));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertFalse(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection(" users '", 1));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertTrue(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection(" users '", 3));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertTrue(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection(" users '", 7));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertFalse(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection(" users are", 7));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertTrue(ic.isCursorTouchingWord(sap, true));

        ims.setInputConnection(new MockConnection(" users 'are", 7));
        ic.resetCachesUponCursorMoveAndReturnSuccess(ims.cursorPos(), ims.cursorPos(), true);
        assertFalse(ic.isCursorTouchingWord(sap, true));
    }
}
