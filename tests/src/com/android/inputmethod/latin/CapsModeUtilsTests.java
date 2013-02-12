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

package com.android.inputmethod.latin;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;

import java.util.Locale;

@SmallTest
public class CapsModeUtilsTests extends AndroidTestCase {
    private static void onePathForCaps(final CharSequence cs, final int expectedResult,
            final int mask, final Locale l, final boolean hasSpaceBefore) {
        int oneTimeResult = expectedResult & mask;
        assertEquals("After >" + cs + "<", oneTimeResult,
                CapsModeUtils.getCapsMode(cs, mask, l, hasSpaceBefore));
    }

    private static void allPathsForCaps(final CharSequence cs, final int expectedResult,
            final Locale l, final boolean hasSpaceBefore) {
        final int c = TextUtils.CAP_MODE_CHARACTERS;
        final int w = TextUtils.CAP_MODE_WORDS;
        final int s = TextUtils.CAP_MODE_SENTENCES;
        onePathForCaps(cs, expectedResult, c | w | s, l, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, w | s, l, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, c | s, l, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, c | w, l, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, c, l, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, w, l, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, s, l, hasSpaceBefore);
    }

    public void testGetCapsMode() {
        final int c = TextUtils.CAP_MODE_CHARACTERS;
        final int w = TextUtils.CAP_MODE_WORDS;
        final int s = TextUtils.CAP_MODE_SENTENCES;
        Locale l = Locale.ENGLISH;
        allPathsForCaps("", c | w | s, l, false);
        allPathsForCaps("Word", c, l, false);
        allPathsForCaps("Word.", c, l, false);
        allPathsForCaps("Word ", c | w, l, false);
        allPathsForCaps("Word. ", c | w | s, l, false);
        allPathsForCaps("Word..", c, l, false);
        allPathsForCaps("Word.. ", c | w | s, l, false);
        allPathsForCaps("Word... ", c | w | s, l, false);
        allPathsForCaps("Word ... ", c | w | s, l, false);
        allPathsForCaps("Word . ", c | w, l, false);
        allPathsForCaps("In the U.S ", c | w, l, false);
        allPathsForCaps("In the U.S. ", c | w, l, false);
        allPathsForCaps("Some stuff (e.g. ", c | w, l, false);
        allPathsForCaps("In the U.S.. ", c | w | s, l, false);
        allPathsForCaps("\"Word.\" ", c | w | s, l, false);
        allPathsForCaps("\"Word\". ", c | w | s, l, false);
        allPathsForCaps("\"Word\" ", c | w, l, false);

        // Test for phantom space
        allPathsForCaps("Word", c | w, l, true);
        allPathsForCaps("Word.", c | w | s, l, true);

        // Tests after some whitespace
        allPathsForCaps("Word\n", c | w | s, l, false);
        allPathsForCaps("Word\n", c | w | s, l, true);
        allPathsForCaps("Word\n ", c | w | s, l, true);
        allPathsForCaps("Word.\n", c | w | s, l, false);
        allPathsForCaps("Word.\n", c | w | s, l, true);
        allPathsForCaps("Word.\n ", c | w | s, l, true);

        l = Locale.FRENCH;
        allPathsForCaps("\"Word.\" ", c | w, l, false);
        allPathsForCaps("\"Word\". ", c | w | s, l, false);
        allPathsForCaps("\"Word\" ", c | w, l, false);
    }
}
