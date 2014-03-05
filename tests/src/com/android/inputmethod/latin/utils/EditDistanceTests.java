/*
 * Copyright (C) 2010 The Android Open Source Project
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

@SmallTest
public class EditDistanceTests extends AndroidTestCase {
    /*
     * dist(kitten, sitting) == 3
     *
     * kitten-
     * .|||.|
     * sitting
     */
    public void testExample1() {
        final int dist = BinaryDictionaryUtils.editDistance("kitten", "sitting");
        assertEquals("edit distance between 'kitten' and 'sitting' is 3",
                3, dist);
    }

    /*
     * dist(Sunday, Saturday) == 3
     *
     * Saturday
     * |  |.|||
     * S--unday
     */
    public void testExample2() {
        final int dist = BinaryDictionaryUtils.editDistance("Saturday", "Sunday");
        assertEquals("edit distance between 'Saturday' and 'Sunday' is 3",
                3, dist);
    }

    public void testBothEmpty() {
        final int dist = BinaryDictionaryUtils.editDistance("", "");
        assertEquals("when both string are empty, no edits are needed",
                0, dist);
    }

    public void testFirstArgIsEmpty() {
        final int dist = BinaryDictionaryUtils.editDistance("", "aaaa");
        assertEquals("when only one string of the arguments is empty,"
                 + " the edit distance is the length of the other.",
                 4, dist);
    }

    public void testSecoondArgIsEmpty() {
        final int dist = BinaryDictionaryUtils.editDistance("aaaa", "");
        assertEquals("when only one string of the arguments is empty,"
                 + " the edit distance is the length of the other.",
                 4, dist);
    }

    public void testSameStrings() {
        final String arg1 = "The quick brown fox jumps over the lazy dog.";
        final String arg2 = "The quick brown fox jumps over the lazy dog.";
        final int dist = BinaryDictionaryUtils.editDistance(arg1, arg2);
        assertEquals("when same strings are passed, distance equals 0.",
                0, dist);
    }

    public void testSameReference() {
        final String arg = "The quick brown fox jumps over the lazy dog.";
        final int dist = BinaryDictionaryUtils.editDistance(arg, arg);
        assertEquals("when same string references are passed, the distance equals 0.",
                0, dist);
    }

    public void testNullArg() {
        try {
            BinaryDictionaryUtils.editDistance(null, "aaa");
            fail("IllegalArgumentException should be thrown.");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        try {
            BinaryDictionaryUtils.editDistance("aaa", null);
            fail("IllegalArgumentException should be thrown.");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
