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

package com.android.inputmethod.latin.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SpannableStringUtilsTests {

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testConcatWithSuggestionSpansOnly() {
        SpannableStringBuilder s = new SpannableStringBuilder("test string\ntest string\n"
                + "test string\ntest string\ntest string\ntest string\ntest string\ntest string\n"
                + "test string\ntest string\n");
        final int N = 10;
        for (int i = 0; i < N; ++i) {
            // Put a PARAGRAPH-flagged span that should not be found in the result.
            s.setSpan(new SuggestionSpan(getContext(),
                    new String[] {"" + i}, Spanned.SPAN_PARAGRAPH),
                    i * 12, i * 12 + 12, Spanned.SPAN_PARAGRAPH);
            // Put a normal suggestion span that should be found in the result.
            s.setSpan(new SuggestionSpan(getContext(), new String[] {"" + i}, 0), i, i * 2, 0);
            // Put a URL span than should not be found in the result.
            s.setSpan(new URLSpan("http://a"), i, i * 2, 0);
        }

        final CharSequence a = s.subSequence(0, 15);
        final CharSequence b = s.subSequence(15, s.length());
        final Spanned result =
                (Spanned)SpannableStringUtils.concatWithNonParagraphSuggestionSpansOnly(a, b);

        Object[] spans = result.getSpans(0, result.length(), SuggestionSpan.class);
        for (int i = 0; i < spans.length; i++) {
            final int flags = result.getSpanFlags(spans[i]);
            assertEquals("Should not find a span with PARAGRAPH flag",
                    flags & Spanned.SPAN_PARAGRAPH, 0);
            assertTrue("Should be a SuggestionSpan", spans[i] instanceof SuggestionSpan);
        }
    }

    private static void assertSpanCount(final int expectedCount, final CharSequence cs) {
        final int actualCount;
        if (cs instanceof Spanned) {
            final Spanned spanned = (Spanned) cs;
            actualCount = spanned.getSpans(0, spanned.length(), Object.class).length;
        } else {
            actualCount = 0;
        }
        assertEquals(expectedCount, actualCount);
    }

    private static void assertSpan(final CharSequence cs, final Object expectedSpan,
            final int expectedStart, final int expectedEnd, final int expectedFlags) {
        assertTrue(cs instanceof Spanned);
        final Spanned spanned = (Spanned) cs;
        final Object[] actualSpans = spanned.getSpans(0, spanned.length(), Object.class);
        for (Object actualSpan : actualSpans) {
            if (actualSpan == expectedSpan) {
                final int actualStart = spanned.getSpanStart(actualSpan);
                final int actualEnd = spanned.getSpanEnd(actualSpan);
                final int actualFlags = spanned.getSpanFlags(actualSpan);
                assertEquals(expectedStart, actualStart);
                assertEquals(expectedEnd, actualEnd);
                assertEquals(expectedFlags, actualFlags);
                return;
            }
        }
        assertTrue(false);
    }

    @Test
    public void testSplitCharSequenceWithSpan() {
        // text:  " a bcd efg hij  "
        // span1:  ^^^^^^^
        // span2:  ^^^^^
        // span3:              ^
        final SpannableString spannableString = new SpannableString(" a bcd efg hij  ");
        final Object span1 = new Object();
        final Object span2 = new Object();
        final Object span3 = new Object();
        final int SPAN1_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        final int SPAN2_FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
        final int SPAN3_FLAGS = Spanned.SPAN_INCLUSIVE_INCLUSIVE;
        spannableString.setSpan(span1, 0, 7, SPAN1_FLAGS);
        spannableString.setSpan(span2, 0, 5, SPAN2_FLAGS);
        spannableString.setSpan(span3, 12, 13, SPAN3_FLAGS);
        final CharSequence[] charSequencesFromSpanned = SpannableStringUtils.split(
                spannableString, " ", true /* preserveTrailingEmptySegmengs */);
        final CharSequence[] charSequencesFromString = SpannableStringUtils.split(
                spannableString.toString(), " ", true /* preserveTrailingEmptySegmengs */);


        assertEquals(7, charSequencesFromString.length);
        assertEquals(7, charSequencesFromSpanned.length);

        // text:  ""
        // span1: ^
        // span2: ^
        // span3:
        assertEquals("", charSequencesFromString[0].toString());
        assertSpanCount(0, charSequencesFromString[0]);
        assertEquals("", charSequencesFromSpanned[0].toString());
        assertSpanCount(2, charSequencesFromSpanned[0]);
        assertSpan(charSequencesFromSpanned[0], span1, 0, 0, SPAN1_FLAGS);
        assertSpan(charSequencesFromSpanned[0], span2, 0, 0, SPAN2_FLAGS);

        // text:  "a"
        // span1:  ^
        // span2:  ^
        // span3:
        assertEquals("a", charSequencesFromString[1].toString());
        assertSpanCount(0, charSequencesFromString[1]);
        assertEquals("a", charSequencesFromSpanned[1].toString());
        assertSpanCount(2, charSequencesFromSpanned[1]);
        assertSpan(charSequencesFromSpanned[1], span1, 0, 1, SPAN1_FLAGS);
        assertSpan(charSequencesFromSpanned[1], span2, 0, 1, SPAN2_FLAGS);

        // text:  "bcd"
        // span1:  ^^^
        // span2:  ^^
        // span3:
        assertEquals("bcd", charSequencesFromString[2].toString());
        assertSpanCount(0, charSequencesFromString[2]);
        assertEquals("bcd", charSequencesFromSpanned[2].toString());
        assertSpanCount(2, charSequencesFromSpanned[2]);
        assertSpan(charSequencesFromSpanned[2], span1, 0, 3, SPAN1_FLAGS);
        assertSpan(charSequencesFromSpanned[2], span2, 0, 2, SPAN2_FLAGS);

        // text:  "efg"
        // span1:
        // span2:
        // span3:
        assertEquals("efg", charSequencesFromString[3].toString());
        assertSpanCount(0, charSequencesFromString[3]);
        assertEquals("efg", charSequencesFromSpanned[3].toString());
        assertSpanCount(0, charSequencesFromSpanned[3]);

        // text:  "hij"
        // span1:
        // span2:
        // span3:   ^
        assertEquals("hij", charSequencesFromString[4].toString());
        assertSpanCount(0, charSequencesFromString[4]);
        assertEquals("hij", charSequencesFromSpanned[4].toString());
        assertSpanCount(1, charSequencesFromSpanned[4]);
        assertSpan(charSequencesFromSpanned[4], span3, 1, 2, SPAN3_FLAGS);

        // text:  ""
        // span1:
        // span2:
        // span3:
        assertEquals("", charSequencesFromString[5].toString());
        assertSpanCount(0, charSequencesFromString[5]);
        assertEquals("", charSequencesFromSpanned[5].toString());
        assertSpanCount(0, charSequencesFromSpanned[5]);

        // text:  ""
        // span1:
        // span2:
        // span3:
        assertEquals("", charSequencesFromString[6].toString());
        assertSpanCount(0, charSequencesFromString[6]);
        assertEquals("", charSequencesFromSpanned[6].toString());
        assertSpanCount(0, charSequencesFromSpanned[6]);
    }

    @Test
    public void testSplitCharSequencePreserveTrailingEmptySegmengs() {
        assertEquals(1, SpannableStringUtils.split("", " ",
                false /* preserveTrailingEmptySegmengs */).length);
        assertEquals(1, SpannableStringUtils.split(new SpannedString(""), " ",
                false /* preserveTrailingEmptySegmengs */).length);

        assertEquals(1, SpannableStringUtils.split("", " ",
                true /* preserveTrailingEmptySegmengs */).length);
        assertEquals(1, SpannableStringUtils.split(new SpannedString(""), " ",
                true /* preserveTrailingEmptySegmengs */).length);

        assertEquals(0, SpannableStringUtils.split(" ", " ",
                false /* preserveTrailingEmptySegmengs */).length);
        assertEquals(0, SpannableStringUtils.split(new SpannedString(" "), " ",
                false /* preserveTrailingEmptySegmengs */).length);

        assertEquals(2, SpannableStringUtils.split(" ", " ",
                true /* preserveTrailingEmptySegmengs */).length);
        assertEquals(2, SpannableStringUtils.split(new SpannedString(" "), " ",
                true /* preserveTrailingEmptySegmengs */).length);

        assertEquals(3, SpannableStringUtils.split("a b c  ", " ",
                false /* preserveTrailingEmptySegmengs */).length);
        assertEquals(3, SpannableStringUtils.split(new SpannedString("a b c  "), " ",
                false /* preserveTrailingEmptySegmengs */).length);

        assertEquals(5, SpannableStringUtils.split("a b c  ", " ",
                true /* preserveTrailingEmptySegmengs */).length);
        assertEquals(5, SpannableStringUtils.split(new SpannedString("a b c  "), " ",
                true /* preserveTrailingEmptySegmengs */).length);

        assertEquals(6, SpannableStringUtils.split("a     b ", " ",
                false /* preserveTrailingEmptySegmengs */).length);
        assertEquals(6, SpannableStringUtils.split(new SpannedString("a     b "), " ",
                false /* preserveTrailingEmptySegmengs */).length);

        assertEquals(7, SpannableStringUtils.split("a     b ", " ",
                true /* preserveTrailingEmptySegmengs */).length);
        assertEquals(7, SpannableStringUtils.split(new SpannedString("a     b "), " ",
                true /* preserveTrailingEmptySegmengs */).length);
    }
}
