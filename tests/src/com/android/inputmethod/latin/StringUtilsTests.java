/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.test.AndroidTestCase;
import android.text.TextUtils;

import java.util.Locale;

public class StringUtilsTests extends AndroidTestCase {
    public void testContainsInArray() {
        assertFalse("empty array", StringUtils.containsInArray("key", new String[0]));
        assertFalse("not in 1 element", StringUtils.containsInArray("key", new String[] {
                "key1"
        }));
        assertFalse("not in 2 elements", StringUtils.containsInArray("key", new String[] {
                "key1", "key2"
        }));

        assertTrue("in 1 element", StringUtils.containsInArray("key", new String[] {
                "key"
        }));
        assertTrue("in 2 elements", StringUtils.containsInArray("key", new String[] {
                "key1", "key"
        }));
    }

    public void testContainsInCsv() {
        assertFalse("null", StringUtils.containsInCsv("key", null));
        assertFalse("empty", StringUtils.containsInCsv("key", ""));
        assertFalse("not in 1 element", StringUtils.containsInCsv("key", "key1"));
        assertFalse("not in 2 elements", StringUtils.containsInCsv("key", "key1,key2"));

        assertTrue("in 1 element", StringUtils.containsInCsv("key", "key"));
        assertTrue("in 2 elements", StringUtils.containsInCsv("key", "key1,key"));
    }

    public void testAppendToCsvIfNotExists() {
        assertEquals("null", "key", StringUtils.appendToCsvIfNotExists("key", null));
        assertEquals("empty", "key", StringUtils.appendToCsvIfNotExists("key", ""));

        assertEquals("not in 1 element", "key1,key",
                StringUtils.appendToCsvIfNotExists("key", "key1"));
        assertEquals("not in 2 elements", "key1,key2,key",
                StringUtils.appendToCsvIfNotExists("key", "key1,key2"));

        assertEquals("in 1 element", "key",
                StringUtils.appendToCsvIfNotExists("key", "key"));
        assertEquals("in 2 elements at position 1", "key,key2",
                StringUtils.appendToCsvIfNotExists("key", "key,key2"));
        assertEquals("in 2 elements at position 2", "key1,key",
                StringUtils.appendToCsvIfNotExists("key", "key1,key"));
        assertEquals("in 3 elements at position 2", "key1,key,key3",
                StringUtils.appendToCsvIfNotExists("key", "key1,key,key3"));
    }

    public void testRemoveFromCsvIfExists() {
        assertEquals("null", "", StringUtils.removeFromCsvIfExists("key", null));
        assertEquals("empty", "", StringUtils.removeFromCsvIfExists("key", ""));

        assertEquals("not in 1 element", "key1",
                StringUtils.removeFromCsvIfExists("key", "key1"));
        assertEquals("not in 2 elements", "key1,key2",
                StringUtils.removeFromCsvIfExists("key", "key1,key2"));

        assertEquals("in 1 element", "",
                StringUtils.removeFromCsvIfExists("key", "key"));
        assertEquals("in 2 elements at position 1", "key2",
                StringUtils.removeFromCsvIfExists("key", "key,key2"));
        assertEquals("in 2 elements at position 2", "key1",
                StringUtils.removeFromCsvIfExists("key", "key1,key"));
        assertEquals("in 3 elements at position 2", "key1,key3",
                StringUtils.removeFromCsvIfExists("key", "key1,key,key3"));

        assertEquals("in 3 elements at position 1,2,3", "",
                StringUtils.removeFromCsvIfExists("key", "key,key,key"));
        assertEquals("in 5 elements at position 2,4", "key1,key3,key5",
                StringUtils.removeFromCsvIfExists("key", "key1,key,key3,key,key5"));
    }

    private void onePathForCaps(final CharSequence cs, final int expectedResult, final int mask,
            final Locale l, final boolean hasSpaceBefore) {
        int oneTimeResult = expectedResult & mask;
        assertEquals("After >" + cs + "<", oneTimeResult,
                StringUtils.getCapsMode(cs, mask, l, hasSpaceBefore));
    }

    private void allPathsForCaps(final CharSequence cs, final int expectedResult, final Locale l,
            final boolean hasSpaceBefore) {
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
