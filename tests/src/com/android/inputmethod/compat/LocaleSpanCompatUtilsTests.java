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

package com.android.inputmethod.compat;

import android.graphics.Typeface;
import android.os.Build;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;

import java.util.Locale;

@SmallTest
public class LocaleSpanCompatUtilsTests extends AndroidTestCase {
    public void testInstantiatable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // LocaleSpan isn't yet available.
            return;
        }
        assertTrue(LocaleSpanCompatUtils.isLocaleSpanAvailable());
        final Object japaneseLocaleSpan = LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE);
        assertNotNull(japaneseLocaleSpan);
        assertEquals(Locale.JAPANESE,
                LocaleSpanCompatUtils.getLocaleFromLocaleSpan(japaneseLocaleSpan));
    }

    private static void assertLocaleSpan(final Spanned spanned, final int index,
            final int expectedStart, final int expectedEnd,
            final Locale expectedLocale, final int expectedSpanFlags) {
        final Object span = spanned.getSpans(0, spanned.length(), Object.class)[index];
        assertEquals(expectedLocale, LocaleSpanCompatUtils.getLocaleFromLocaleSpan(span));
        assertEquals(expectedStart, spanned.getSpanStart(span));
        assertEquals(expectedEnd, spanned.getSpanEnd(span));
        assertEquals(expectedSpanFlags, spanned.getSpanFlags(span));
    }

    private static void assertSpanEquals(final Object expectedSpan, final Spanned spanned,
            final int index) {
        final Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
        assertEquals(expectedSpan, spans[index]);
    }

    private static void assertSpanCount(final int expectedCount, final Spanned spanned) {
        final Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
        assertEquals(expectedCount, spans.length);
    }

    public void testUpdateLocaleSpan() {
        if (!LocaleSpanCompatUtils.isLocaleSpanAvailable()) {
            return;
        }

        // Test if the simplest case works.
        {
            final SpannableString text = new SpannableString("0123456789");
            LocaleSpanCompatUtils.updateLocaleSpan(text, 1, 5, Locale.JAPANESE);
            assertSpanCount(1, text);
            assertLocaleSpan(text, 0, 1, 5, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Test if only LocaleSpans are updated.
        {
            final SpannableString text = new SpannableString("0123456789");
            final StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
            text.setSpan(styleSpan, 0, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 1, 5, Locale.JAPANESE);
            assertSpanCount(2, text);
            assertSpanEquals(styleSpan, text, 0);
            assertLocaleSpan(text, 1, 1, 5, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Test if two jointed spans are merged into one span.
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE), 1, 3,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 3, 5, Locale.JAPANESE);
            assertSpanCount(1, text);
            assertLocaleSpan(text, 0, 1, 5, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Test if two overlapped spans are merged into one span.
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE), 1, 4,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 3, 5, Locale.JAPANESE);
            assertSpanCount(1, text);
            assertLocaleSpan(text, 0, 1, 5, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Test if three overlapped spans are merged into one span.
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE), 1, 4,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE), 5, 6,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 2, 8, Locale.JAPANESE);
            assertSpanCount(1, text);
            assertLocaleSpan(text, 0, 1, 8, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Test if disjoint spans remain disjoint.
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE), 1, 3,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE), 5, 6,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 8, 9, Locale.JAPANESE);
            assertSpanCount(3, text);
            assertLocaleSpan(text, 0, 1, 3, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            assertLocaleSpan(text, 1, 5, 6, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            assertLocaleSpan(text, 2, 8, 9, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Test if existing span flags are preserved during merge.
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE), 1, 5,
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE | Spanned.SPAN_INTERMEDIATE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 3, 4, Locale.JAPANESE);
            assertSpanCount(1, text);
            assertLocaleSpan(text, 0, 1, 5, Locale.JAPANESE,
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE | Spanned.SPAN_INTERMEDIATE);
        }

        // Test if existing span flags are preserved even when partially overlapped (leading edge).
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE), 1, 5,
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE | Spanned.SPAN_INTERMEDIATE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 3, 7, Locale.JAPANESE);
            assertSpanCount(1, text);
            assertLocaleSpan(text, 0, 1, 7, Locale.JAPANESE,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE | Spanned.SPAN_INTERMEDIATE);
        }

        // Test if existing span flags are preserved even when partially overlapped (trailing edge).
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.JAPANESE), 3, 7,
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE | Spanned.SPAN_INTERMEDIATE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 1, 5, Locale.JAPANESE);
            assertSpanCount(1, text);
            assertLocaleSpan(text, 0, 1, 7, Locale.JAPANESE,
                    Spanned.SPAN_EXCLUSIVE_INCLUSIVE | Spanned.SPAN_INTERMEDIATE);
        }

        // Test if existing locale span will be removed when the locale doesn't match.
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.ENGLISH), 3, 5,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 1, 7, Locale.JAPANESE);
            assertSpanCount(1, text);
            assertLocaleSpan(text, 0, 1, 7, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Test if existing locale span will be removed when the locale doesn't match. (case 2)
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.ENGLISH), 3, 7,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 5, 6, Locale.JAPANESE);
            assertSpanCount(3, text);
            assertLocaleSpan(text, 0, 3, 5, Locale.ENGLISH, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            assertLocaleSpan(text, 1, 6, 7, Locale.ENGLISH, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            assertLocaleSpan(text, 2, 5, 6, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Test if existing locale span will be removed when the locale doesn't match. (case 3)
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.ENGLISH), 3, 7,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 2, 5, Locale.JAPANESE);
            assertSpanCount(2, text);
            assertLocaleSpan(text, 0, 5, 7, Locale.ENGLISH, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            assertLocaleSpan(text, 1, 2, 5, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Test if existing locale span will be removed when the locale doesn't match. (case 3)
        {
            final SpannableString text = new SpannableString("0123456789");
            text.setSpan(LocaleSpanCompatUtils.newLocaleSpan(Locale.ENGLISH), 3, 7,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            LocaleSpanCompatUtils.updateLocaleSpan(text, 5, 8, Locale.JAPANESE);
            assertSpanCount(2, text);
            assertLocaleSpan(text, 0, 3, 5, Locale.ENGLISH, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            assertLocaleSpan(text, 1, 5, 8, Locale.JAPANESE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
