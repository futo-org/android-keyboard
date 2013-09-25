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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;
import android.text.SpannableStringBuilder;
import android.text.Spannable;
import android.text.Spanned;

@SmallTest
public class SpannableStringUtilsTests extends AndroidTestCase {
    public void testConcatWithSuggestionSpansOnly() {
        SpannableStringBuilder s = new SpannableStringBuilder("test string\ntest string\n"
                + "test string\ntest string\ntest string\ntest string\ntest string\ntest string\n"
                + "test string\ntest string\n");
        final int N = 10;
        for (int i = 0; i < N; ++i) {
            // Put a PARAGRAPH-flagged span that should not be found in the result.
            s.setSpan(new SuggestionSpan(getContext(),
                    new String[] {"" + i}, Spannable.SPAN_PARAGRAPH),
                    i * 12, i * 12 + 12, Spannable.SPAN_PARAGRAPH);
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
                    flags & Spannable.SPAN_PARAGRAPH, 0);
            assertTrue("Should be a SuggestionSpan", spans[i] instanceof SuggestionSpan);
        }
    }
}
