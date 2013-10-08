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

import com.android.inputmethod.latin.settings.SettingsValues;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;

import java.util.Locale;

@SmallTest
public class CapsModeUtilsTests extends AndroidTestCase {
    private static void onePathForCaps(final CharSequence cs, final int expectedResult,
            final int mask, final SettingsValues sv, final boolean hasSpaceBefore) {
        int oneTimeResult = expectedResult & mask;
        assertEquals("After >" + cs + "<", oneTimeResult,
                CapsModeUtils.getCapsMode(cs, mask, sv, hasSpaceBefore));
    }

    private static void allPathsForCaps(final CharSequence cs, final int expectedResult,
            final SettingsValues sv, final boolean hasSpaceBefore) {
        final int c = TextUtils.CAP_MODE_CHARACTERS;
        final int w = TextUtils.CAP_MODE_WORDS;
        final int s = TextUtils.CAP_MODE_SENTENCES;
        onePathForCaps(cs, expectedResult, c | w | s, sv, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, w | s, sv, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, c | s, sv, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, c | w, sv, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, c, sv, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, w, sv, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, s, sv, hasSpaceBefore);
    }

    public void testGetCapsMode() {
        final int c = TextUtils.CAP_MODE_CHARACTERS;
        final int w = TextUtils.CAP_MODE_WORDS;
        final int s = TextUtils.CAP_MODE_SENTENCES;
        SettingsValues sv = SettingsValues.makeDummySettingsValuesForTest(Locale.ENGLISH);
        allPathsForCaps("", c | w | s, sv, false);
        allPathsForCaps("Word", c, sv, false);
        allPathsForCaps("Word.", c, sv, false);
        allPathsForCaps("Word ", c | w, sv, false);
        allPathsForCaps("Word. ", c | w | s, sv, false);
        allPathsForCaps("Word..", c, sv, false);
        allPathsForCaps("Word.. ", c | w | s, sv, false);
        allPathsForCaps("Word... ", c | w | s, sv, false);
        allPathsForCaps("Word ... ", c | w | s, sv, false);
        allPathsForCaps("Word . ", c | w, sv, false);
        allPathsForCaps("In the U.S ", c | w, sv, false);
        allPathsForCaps("In the U.S. ", c | w, sv, false);
        allPathsForCaps("Some stuff (e.g. ", c | w, sv, false);
        allPathsForCaps("In the U.S.. ", c | w | s, sv, false);
        allPathsForCaps("\"Word.\" ", c | w | s, sv, false);
        allPathsForCaps("\"Word\". ", c | w | s, sv, false);
        allPathsForCaps("\"Word\" ", c | w, sv, false);

        // Test for phantom space
        allPathsForCaps("Word", c | w, sv, true);
        allPathsForCaps("Word.", c | w | s, sv, true);

        // Tests after some whitespace
        allPathsForCaps("Word\n", c | w | s, sv, false);
        allPathsForCaps("Word\n", c | w | s, sv, true);
        allPathsForCaps("Word\n ", c | w | s, sv, true);
        allPathsForCaps("Word.\n", c | w | s, sv, false);
        allPathsForCaps("Word.\n", c | w | s, sv, true);
        allPathsForCaps("Word.\n ", c | w | s, sv, true);

        sv = SettingsValues.makeDummySettingsValuesForTest(Locale.FRENCH);
        allPathsForCaps("\"Word.\" ", c | w, sv, false);
        allPathsForCaps("\"Word\". ", c | w | s, sv, false);
        allPathsForCaps("\"Word\" ", c | w, sv, false);
    }
}
