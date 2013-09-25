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

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;

public final class SpannableStringUtils {
    /**
     * Copies the spans from the region <code>start...end</code> in
     * <code>source</code> to the region
     * <code>destoff...destoff+end-start</code> in <code>dest</code>.
     * Spans in <code>source</code> that begin before <code>start</code>
     * or end after <code>end</code> but overlap this range are trimmed
     * as if they began at <code>start</code> or ended at <code>end</code>.
     * Only SuggestionSpans that don't have the SPAN_PARAGRAPH span are copied.
     *
     * This code is almost entirely taken from {@link TextUtils#copySpansFrom}, except for the
     * kind of span that is copied.
     *
     * @throws IndexOutOfBoundsException if any of the copied spans
     * are out of range in <code>dest</code>.
     */
    public static void copyNonParagraphSuggestionSpansFrom(Spanned source, int start, int end,
                                     Spannable dest, int destoff) {
        Object[] spans = source.getSpans(start, end, SuggestionSpan.class);

        for (int i = 0; i < spans.length; i++) {
            int fl = source.getSpanFlags(spans[i]);
            if (0 != (fl & Spannable.SPAN_PARAGRAPH)) continue;

            int st = source.getSpanStart(spans[i]);
            int en = source.getSpanEnd(spans[i]);

            if (st < start)
                st = start;
            if (en > end)
                en = end;

            dest.setSpan(spans[i], st - start + destoff, en - start + destoff,
                         fl);
        }
    }

    /**
     * Returns a CharSequence concatenating the specified CharSequences, retaining their
     * SuggestionSpans that don't have the PARAGRAPH flag, but not other spans.
     *
     * This code is almost entirely taken from {@link TextUtils#concat(CharSequence...)}, except
     * it calls copyNonParagraphSuggestionSpansFrom instead of {@link TextUtils#copySpansFrom}.
     */
    public static CharSequence concatWithNonParagraphSuggestionSpansOnly(CharSequence... text) {
        if (text.length == 0) {
            return "";
        }

        if (text.length == 1) {
            return text[0];
        }

        boolean spanned = false;
        for (int i = 0; i < text.length; i++) {
            if (text[i] instanceof Spanned) {
                spanned = true;
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length; i++) {
            sb.append(text[i]);
        }

        if (!spanned) {
            return sb.toString();
        }

        SpannableString ss = new SpannableString(sb);
        int off = 0;
        for (int i = 0; i < text.length; i++) {
            int len = text[i].length();

            if (text[i] instanceof Spanned) {
                copyNonParagraphSuggestionSpansFrom((Spanned) text[i], 0, len, ss, off);
            }

            off += len;
        }

        return new SpannedString(ss);
    }
}
