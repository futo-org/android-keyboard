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

import android.content.res.Resources;
import android.text.TextUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CapsModeUtilsTests {
    private static void onePathForCaps(final CharSequence cs, final int expectedResult,
            final int mask, final SpacingAndPunctuations sp, final boolean hasSpaceBefore) {
        final int oneTimeResult = expectedResult & mask;
        assertEquals("After >" + cs + "<", oneTimeResult,
                CapsModeUtils.getCapsMode(cs, mask, sp, hasSpaceBefore));
    }

    private static void allPathsForCaps(final CharSequence cs, final int expectedResult,
            final SpacingAndPunctuations sp, final boolean hasSpaceBefore) {
        final int c = TextUtils.CAP_MODE_CHARACTERS;
        final int w = TextUtils.CAP_MODE_WORDS;
        final int s = TextUtils.CAP_MODE_SENTENCES;
        onePathForCaps(cs, expectedResult, c | w | s, sp, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, w | s, sp, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, c | s, sp, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, c | w, sp, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, c, sp, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, w, sp, hasSpaceBefore);
        onePathForCaps(cs, expectedResult, s, sp, hasSpaceBefore);
    }

    @Test
    public void testGetCapsMode() {
        final int c = TextUtils.CAP_MODE_CHARACTERS;
        final int w = TextUtils.CAP_MODE_WORDS;
        final int s = TextUtils.CAP_MODE_SENTENCES;
        final RunInLocale<SpacingAndPunctuations> job = new RunInLocale<SpacingAndPunctuations>() {
            @Override
            protected SpacingAndPunctuations job(final Resources res) {
                return new SpacingAndPunctuations(res);
            }
        };
        final Resources res = InstrumentationRegistry.getTargetContext().getResources();
        SpacingAndPunctuations sp = job.runInLocale(res, Locale.ENGLISH);
        allPathsForCaps("", c | w | s, sp, false);
        allPathsForCaps("Word", c, sp, false);
        allPathsForCaps("Word.", c, sp, false);
        allPathsForCaps("Word ", c | w, sp, false);
        allPathsForCaps("Word. ", c | w | s, sp, false);
        allPathsForCaps("Word..", c, sp, false);
        allPathsForCaps("Word.. ", c | w | s, sp, false);
        allPathsForCaps("Word... ", c | w | s, sp, false);
        allPathsForCaps("Word ... ", c | w | s, sp, false);
        allPathsForCaps("Word . ", c | w, sp, false);
        allPathsForCaps("In the U.S ", c | w, sp, false);
        allPathsForCaps("In the U.S. ", c | w, sp, false);
        allPathsForCaps("Some stuff (e.g. ", c | w, sp, false);
        allPathsForCaps("In the U.S.. ", c | w | s, sp, false);
        allPathsForCaps("\"Word.\" ", c | w | s, sp, false);
        allPathsForCaps("\"Word\". ", c | w | s, sp, false);
        allPathsForCaps("\"Word\" ", c | w, sp, false);

        // Test for phantom space
        allPathsForCaps("Word", c | w, sp, true);
        allPathsForCaps("Word.", c | w | s, sp, true);

        // Tests after some whitespace
        allPathsForCaps("Word\n", c | w | s, sp, false);
        allPathsForCaps("Word\n", c | w | s, sp, true);
        allPathsForCaps("Word\n ", c | w | s, sp, true);
        allPathsForCaps("Word.\n", c | w | s, sp, false);
        allPathsForCaps("Word.\n", c | w | s, sp, true);
        allPathsForCaps("Word.\n ", c | w | s, sp, true);

        sp = job.runInLocale(res, Locale.FRENCH);
        allPathsForCaps("\"Word.\" ", c | w, sp, false);
        allPathsForCaps("\"Word\". ", c | w | s, sp, false);
        allPathsForCaps("\"Word\" ", c | w, sp, false);

        // Test special case for German. German does not capitalize at the start of a
        // line when the previous line starts with a comma. It does in other cases.
        sp = job.runInLocale(res, Locale.GERMAN);
        allPathsForCaps("Liebe Sara,\n", c | w, sp, false);
        allPathsForCaps("Liebe Sara,\n", c | w, sp, true);
        allPathsForCaps("Liebe Sara,  \n  ", c | w, sp, false);
        allPathsForCaps("Liebe Sara  \n  ", c | w | s, sp, false);
        allPathsForCaps("Liebe Sara.\n  ", c | w | s, sp, false);
        sp = job.runInLocale(res, Locale.ENGLISH);
        allPathsForCaps("Liebe Sara,\n", c | w | s, sp, false);
        allPathsForCaps("Liebe Sara,\n", c | w | s, sp, true);
        allPathsForCaps("Liebe Sara,  \n  ", c | w | s, sp, false);
        allPathsForCaps("Liebe Sara  \n  ", c | w | s, sp, false);
        allPathsForCaps("Liebe Sara.\n  ", c | w | s, sp, false);

        // Test armenian period
        sp = job.runInLocale(res, LocaleUtils.constructLocaleFromString("hy_AM"));
        assertTrue("Period is not sentence separator in Armenian",
                !sp.isSentenceSeparator('.'));
        assertTrue("Sentence separator is Armenian period in Armenian",
                sp.isSentenceSeparator(0x589));
        // No space : capitalize only if MODE_CHARACTERS
        allPathsForCaps("Word", c, sp, false);
        allPathsForCaps("Word.", c, sp, false);
        // Space, but no armenian period : capitalize if MODE_WORDS but not SENTENCES
        allPathsForCaps("Word. ", c | w, sp, false);
        // Armenian period : capitalize if MODE_SENTENCES
        allPathsForCaps("Word\u0589 ", c | w | s, sp, false);

        // Test for sentence terminators
        sp = job.runInLocale(res, Locale.ENGLISH);
        allPathsForCaps("Word? ", c | w | s, sp, false);
        allPathsForCaps("Word?", c | w | s, sp, true);
        allPathsForCaps("Word?", c, sp, false);
        allPathsForCaps("Word! ", c | w | s, sp, false);
        allPathsForCaps("Word!", c | w | s, sp, true);
        allPathsForCaps("Word!", c, sp, false);
        allPathsForCaps("Word; ", c | w, sp, false);
        allPathsForCaps("Word;", c | w, sp, true);
        allPathsForCaps("Word;", c, sp, false);
        // Test for sentence terminators in Greek
        sp = job.runInLocale(res, LocaleUtils.constructLocaleFromString("el"));
        allPathsForCaps("Word? ", c | w | s, sp, false);
        allPathsForCaps("Word?", c | w | s, sp, true);
        allPathsForCaps("Word?", c, sp, false);
        allPathsForCaps("Word! ", c | w | s, sp, false);
        allPathsForCaps("Word!", c | w | s, sp, true);
        allPathsForCaps("Word!", c, sp, false);
        // In Greek ";" is the question mark and it terminates the sentence
        allPathsForCaps("Word; ", c | w | s, sp, false);
        allPathsForCaps("Word;", c | w | s, sp, true);
        allPathsForCaps("Word;", c, sp, false);
    }
}
