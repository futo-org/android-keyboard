/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.util.Locale;

@SmallTest
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


    public void testCapitalizeFirstCodePoint() {
        assertEquals("SSaa",
                StringUtils.capitalizeFirstCodePoint("ßaa", Locale.GERMAN));
        assertEquals("Aßa",
                StringUtils.capitalizeFirstCodePoint("aßa", Locale.GERMAN));
        assertEquals("Iab",
                StringUtils.capitalizeFirstCodePoint("iab", Locale.ENGLISH));
        assertEquals("CAmElCaSe",
                StringUtils.capitalizeFirstCodePoint("cAmElCaSe", Locale.ENGLISH));
        assertEquals("İab",
                StringUtils.capitalizeFirstCodePoint("iab", new Locale("tr")));
        assertEquals("AİB",
                StringUtils.capitalizeFirstCodePoint("AİB", new Locale("tr")));
        assertEquals("A",
                StringUtils.capitalizeFirstCodePoint("a", Locale.ENGLISH));
        assertEquals("A",
                StringUtils.capitalizeFirstCodePoint("A", Locale.ENGLISH));
    }

    public void testCapitalizeFirstAndDowncaseRest() {
        assertEquals("SSaa",
                StringUtils.capitalizeFirstAndDowncaseRest("ßaa", Locale.GERMAN));
        assertEquals("Aßa",
                StringUtils.capitalizeFirstAndDowncaseRest("aßa", Locale.GERMAN));
        assertEquals("Iab",
                StringUtils.capitalizeFirstAndDowncaseRest("iab", Locale.ENGLISH));
        assertEquals("Camelcase",
                StringUtils.capitalizeFirstAndDowncaseRest("cAmElCaSe", Locale.ENGLISH));
        assertEquals("İab",
                StringUtils.capitalizeFirstAndDowncaseRest("iab", new Locale("tr")));
        assertEquals("Aib",
                StringUtils.capitalizeFirstAndDowncaseRest("AİB", new Locale("tr")));
        assertEquals("A",
                StringUtils.capitalizeFirstAndDowncaseRest("a", Locale.ENGLISH));
        assertEquals("A",
                StringUtils.capitalizeFirstAndDowncaseRest("A", Locale.ENGLISH));
    }

    public void testGetCapitalizationType() {
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType("capitalize"));
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType("cApITalize"));
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType("capitalizE"));
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType("__c a piu$@tali56ze"));
        assertEquals(StringUtils.CAPITALIZE_FIRST,
                StringUtils.getCapitalizationType("A__c a piu$@tali56ze"));
        assertEquals(StringUtils.CAPITALIZE_FIRST,
                StringUtils.getCapitalizationType("Capitalize"));
        assertEquals(StringUtils.CAPITALIZE_FIRST,
                StringUtils.getCapitalizationType("     Capitalize"));
        assertEquals(StringUtils.CAPITALIZE_ALL,
                StringUtils.getCapitalizationType("CAPITALIZE"));
        assertEquals(StringUtils.CAPITALIZE_ALL,
                StringUtils.getCapitalizationType("  PI26LIE"));
        assertEquals(StringUtils.CAPITALIZE_NONE,
                StringUtils.getCapitalizationType(""));
    }

    public void testIsIdenticalAfterUpcaseIsIdenticalAfterDowncase() {
        assertFalse(StringUtils.isIdenticalAfterUpcase("capitalize"));
        assertTrue(StringUtils.isIdenticalAfterDowncase("capitalize"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("cApITalize"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("cApITalize"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("capitalizE"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("capitalizE"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("__c a piu$@tali56ze"));
        assertTrue(StringUtils.isIdenticalAfterDowncase("__c a piu$@tali56ze"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("A__c a piu$@tali56ze"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("A__c a piu$@tali56ze"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("Capitalize"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("Capitalize"));
        assertFalse(StringUtils.isIdenticalAfterUpcase("     Capitalize"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("     Capitalize"));
        assertTrue(StringUtils.isIdenticalAfterUpcase("CAPITALIZE"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("CAPITALIZE"));
        assertTrue(StringUtils.isIdenticalAfterUpcase("  PI26LIE"));
        assertFalse(StringUtils.isIdenticalAfterDowncase("  PI26LIE"));
        assertTrue(StringUtils.isIdenticalAfterUpcase(""));
        assertTrue(StringUtils.isIdenticalAfterDowncase(""));
    }

    private static void checkCapitalize(final String src, final String dst, final String separators,
            final Locale locale) {
        assertEquals(dst, StringUtils.capitalizeEachWord(src, separators, locale));
        assert(src.equals(dst)
                == StringUtils.isIdenticalAfterCapitalizeEachWord(src, separators));
    }

    public void testCapitalizeEachWord() {
        checkCapitalize("", "", " ", Locale.ENGLISH);
        checkCapitalize("test", "Test", " ", Locale.ENGLISH);
        checkCapitalize("    test", "    Test", " ", Locale.ENGLISH);
        checkCapitalize("Test", "Test", " ", Locale.ENGLISH);
        checkCapitalize("    Test", "    Test", " ", Locale.ENGLISH);
        checkCapitalize(".Test", ".test", " ", Locale.ENGLISH);
        checkCapitalize(".Test", ".Test", " .", Locale.ENGLISH);
        checkCapitalize(".Test", ".Test", ". ", Locale.ENGLISH);
        checkCapitalize("test and retest", "Test And Retest", " .", Locale.ENGLISH);
        checkCapitalize("Test and retest", "Test And Retest", " .", Locale.ENGLISH);
        checkCapitalize("Test And Retest", "Test And Retest", " .", Locale.ENGLISH);
        checkCapitalize("Test And.Retest  ", "Test And.Retest  ", " .", Locale.ENGLISH);
        checkCapitalize("Test And.retest  ", "Test And.Retest  ", " .", Locale.ENGLISH);
        checkCapitalize("Test And.retest  ", "Test And.retest  ", " ", Locale.ENGLISH);
        checkCapitalize("Test And.Retest  ", "Test And.retest  ", " ", Locale.ENGLISH);
        checkCapitalize("test and ietest", "Test And İetest", " .", new Locale("tr"));
        checkCapitalize("test and ietest", "Test And Ietest", " .", Locale.ENGLISH);
        checkCapitalize("Test&Retest", "Test&Retest", " \n.!?*()&", Locale.ENGLISH);
        checkCapitalize("Test&retest", "Test&Retest", " \n.!?*()&", Locale.ENGLISH);
        checkCapitalize("test&Retest", "Test&Retest", " \n.!?*()&", Locale.ENGLISH);
        checkCapitalize("rest\nrecreation! And in the end...",
                "Rest\nRecreation! And In The End...", " \n.!?*,();&", Locale.ENGLISH);
        checkCapitalize("lorem ipsum dolor sit amet", "Lorem Ipsum Dolor Sit Amet",
                " \n.,!?*()&;", Locale.ENGLISH);
        checkCapitalize("Lorem!Ipsum (Dolor) Sit * Amet", "Lorem!Ipsum (Dolor) Sit * Amet",
                " \n,.;!?*()&", Locale.ENGLISH);
        checkCapitalize("Lorem!Ipsum (dolor) Sit * Amet", "Lorem!Ipsum (Dolor) Sit * Amet",
                " \n,.;!?*()&", Locale.ENGLISH);
    }

    public void testLooksLikeURL() {
        assertTrue(StringUtils.lastPartLooksLikeURL("http://www.google."));
        assertFalse(StringUtils.lastPartLooksLikeURL("word wo"));
        assertTrue(StringUtils.lastPartLooksLikeURL("/etc/foo"));
        assertFalse(StringUtils.lastPartLooksLikeURL("left/right"));
        assertTrue(StringUtils.lastPartLooksLikeURL("www.goo"));
        assertTrue(StringUtils.lastPartLooksLikeURL("www."));
        assertFalse(StringUtils.lastPartLooksLikeURL("U.S.A"));
        assertFalse(StringUtils.lastPartLooksLikeURL("U.S.A."));
        assertTrue(StringUtils.lastPartLooksLikeURL("rtsp://foo."));
        assertTrue(StringUtils.lastPartLooksLikeURL("://"));
        assertFalse(StringUtils.lastPartLooksLikeURL("abc/"));
        assertTrue(StringUtils.lastPartLooksLikeURL("abc.def/ghi"));
        assertFalse(StringUtils.lastPartLooksLikeURL("abc.def"));
        // TODO: ideally this would not look like a URL, but to keep down the complexity of the
        // code for now True is acceptable.
        assertTrue(StringUtils.lastPartLooksLikeURL("abc./def"));
        // TODO: ideally this would not look like a URL, but to keep down the complexity of the
        // code for now True is acceptable.
        assertTrue(StringUtils.lastPartLooksLikeURL(".abc/def"));
    }
}
