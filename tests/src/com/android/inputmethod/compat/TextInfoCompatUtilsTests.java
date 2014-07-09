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
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.textservice.TextInfo;

import java.util.Arrays;

@SmallTest
public class TextInfoCompatUtilsTests extends AndroidTestCase {
    final private static String TEST_TEXT = "0123456789";
    final private static int TEST_COOKIE = 0x1234;
    final private static int TEST_SEQUENCE_NUMBER = 0x4321;
    final private static int TEST_CHAR_SEQUENCE_START = 1;
    final private static int TEST_CHAR_SEQUENCE_END = 6;
    final private static StyleSpan TEST_STYLE_SPAN = new StyleSpan(Typeface.BOLD);
    final private static int TEST_STYLE_SPAN_START = 4;
    final private static int TEST_STYLE_SPAN_END = 5;
    final private static int TEST_STYLE_SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
    final private static URLSpan TEST_URL_SPAN_URL = new URLSpan("http://example.com");
    final private static int TEST_URL_SPAN_START = 3;
    final private static int TEST_URL_SPAN_END = 7;
    final private static int TEST_URL_SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

    public void testGetCharSequence() {
        final SpannableString text = new SpannableString(TEST_TEXT);
        text.setSpan(TEST_STYLE_SPAN, TEST_STYLE_SPAN_START, TEST_STYLE_SPAN_END,
                TEST_STYLE_SPAN_FLAGS);
        text.setSpan(TEST_URL_SPAN_URL, TEST_URL_SPAN_START, TEST_URL_SPAN_END,
                TEST_URL_SPAN_FLAGS);

        final TextInfo textInfo = TextInfoCompatUtils.newInstance(text,
                TEST_CHAR_SEQUENCE_START, TEST_CHAR_SEQUENCE_END, TEST_COOKIE,
                TEST_SEQUENCE_NUMBER);
        final Spanned expectedSpanned = (Spanned) text.subSequence(TEST_CHAR_SEQUENCE_START,
                TEST_CHAR_SEQUENCE_END);
        final CharSequence actualCharSequence =
                TextInfoCompatUtils.getCharSequenceOrString(textInfo);

        // This should be valid even if TextInfo#getCharSequence is not supported.
        assertTrue(TextUtils.equals(expectedSpanned, actualCharSequence));

        if (TextInfoCompatUtils.isCharSequenceSupported()) {
            // This is valid only if TextInfo#getCharSequence is supported.
            assertTrue("should be Spanned", actualCharSequence instanceof Spanned);
            assertTrue(Arrays.equals(marshall(expectedSpanned), marshall(actualCharSequence)));
        }
    }

    private static byte[] marshall(final CharSequence cahrSequence) {
        Parcel parcel = null;
        try {
            parcel = Parcel.obtain();
            TextUtils.writeToParcel(cahrSequence, parcel, 0);
            return parcel.marshall();
        } finally {
            if (parcel != null) {
                parcel.recycle();
                parcel = null;
            }
        }
    }
}
