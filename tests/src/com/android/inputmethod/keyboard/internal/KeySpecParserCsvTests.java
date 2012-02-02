/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.text.TextUtils;

import com.android.inputmethod.latin.tests.R;

import java.util.Arrays;

public class KeySpecParserCsvTests extends AndroidTestCase {
    private Resources mTestResources;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTestResources = getTestContext().getResources();
    }

    private static String format(String message, Object expected, Object actual) {
        return message + " expected:<" + expected + "> but was:<" + actual + ">";
    }

    private void assertTextArray(String message, String value, String ... expected) {
        final String actual[] = KeySpecParser.parseCsvString(value, mTestResources,
                R.string.empty_string);
        if (expected.length == 0) {
            assertNull(message, actual);
            return;
        }
        assertEquals(message + ": expected=" + Arrays.toString(expected)
                + " actual=" + Arrays.toString(actual)
                + ": result length", expected.length, actual.length);
        for (int i = 0; i < actual.length; i++) {
            final boolean equals = TextUtils.equals(expected[i], actual[i]);
            assertTrue(format(message + ": result at " + i + ":", expected[i], actual[i]), equals);
        }
    }

    private void assertError(String message, String value, String ... expected) {
        try {
            assertTextArray(message, value, expected);
            fail(message);
        } catch (Exception pcpe) {
            // success.
        }
    }

    // \U001d11e: MUSICAL SYMBOL G CLEF
    private static final String PAIR1 = "\ud834\udd1e";
    // \U001d122: MUSICAL SYMBOL F CLEF
    private static final String PAIR2 = "\ud834\udd22";
    // \U002f8a6: CJK COMPATIBILITY IDEOGRAPH-2F8A6; variant character of \u6148.
    private static final String PAIR3 = "\ud87e\udca6";
    private static final String SURROGATE1 = PAIR1 + PAIR2;
    private static final String SURROGATE2 = PAIR1 + PAIR2 + PAIR3;

    public void testParseCsvTextZero() {
        assertTextArray("Empty string", "");
    }

    public void testParseCsvTextSingle() {
        assertTextArray("Single char", "a", "a");
        assertTextArray("Surrogate pair", PAIR1, PAIR1);
        assertTextArray("Single escape", "\\", "\\");
        assertTextArray("Space", " ", " ");
        assertTextArray("Single label", "abc", "abc");
        assertTextArray("Single srrogate pairs label", SURROGATE2, SURROGATE2);
        assertTextArray("Spaces", "   ", "   ");
        assertTextArray("Spaces in label", "a b c", "a b c");
        assertTextArray("Spaces at beginning of label", " abc", " abc");
        assertTextArray("Spaces at end of label", "abc ", "abc ");
        assertTextArray("Label surrounded by spaces", " abc ", " abc ");
        assertTextArray("Surrogate pair surrounded by space",
                " " + PAIR1 + " ",
                " " + PAIR1 + " ");
        assertTextArray("Surrogate pair within characters",
                "ab" + PAIR2 + "cd",
                "ab" + PAIR2 + "cd");
        assertTextArray("Surrogate pairs within characters",
                "ab" + SURROGATE1 + "cd",
                "ab" + SURROGATE1 + "cd");

        assertTextArray("Incomplete resource reference 1", "string", "string");
        assertTextArray("Incomplete resource reference 2", "@string", "@string");
        assertTextArray("Incomplete resource reference 3", "string/", "string/");
        assertTextArray("Incomplete resource reference 4", "@" + SURROGATE2, "@" + SURROGATE2);
    }

    public void testParseCsvTextSingleEscaped() {
        assertTextArray("Escaped char", "\\a", "\\a");
        assertTextArray("Escaped surrogate pair", "\\" + PAIR1, "\\" + PAIR1);
        assertTextArray("Escaped comma", "\\,", "\\,");
        assertTextArray("Escaped comma escape", "a\\,\\", "a\\,\\");
        assertTextArray("Escaped escape", "\\\\", "\\\\");
        assertTextArray("Escaped label", "a\\bc", "a\\bc");
        assertTextArray("Escaped surrogate", "a\\" + PAIR1 + "c", "a\\" + PAIR1 + "c");
        assertTextArray("Escaped label at beginning", "\\abc", "\\abc");
        assertTextArray("Escaped surrogate at beginning", "\\" + SURROGATE2, "\\" + SURROGATE2);
        assertTextArray("Escaped label at end", "abc\\", "abc\\");
        assertTextArray("Escaped surrogate at end", SURROGATE2 + "\\", SURROGATE2 + "\\");
        assertTextArray("Escaped label with comma", "a\\,c", "a\\,c");
        assertTextArray("Escaped surrogate with comma",
                PAIR1 + "\\," + PAIR2, PAIR1 + "\\," + PAIR2);
        assertTextArray("Escaped label with comma at beginning", "\\,bc", "\\,bc");
        assertTextArray("Escaped surrogate with comma at beginning",
                "\\," + SURROGATE1, "\\," + SURROGATE1);
        assertTextArray("Escaped label with comma at end", "ab\\,", "ab\\,");
        assertTextArray("Escaped surrogate with comma at end",
                SURROGATE2 + "\\,", SURROGATE2 + "\\,");
        assertTextArray("Escaped label with successive", "\\,\\\\bc", "\\,\\\\bc");
        assertTextArray("Escaped surrogate with successive",
                "\\,\\\\" + SURROGATE1, "\\,\\\\" + SURROGATE1);
        assertTextArray("Escaped label with escape", "a\\\\c", "a\\\\c");
        assertTextArray("Escaped surrogate with escape",
                PAIR1 + "\\\\" + PAIR2, PAIR1 + "\\\\" + PAIR2);

        assertTextArray("Escaped @string", "\\@string", "\\@string");
        assertTextArray("Escaped @string/", "\\@string/", "\\@string/");
        assertTextArray("Escaped @string/", "\\@string/empty_string", "\\@string/empty_string");
    }

    public void testParseCsvTextMulti() {
        assertTextArray("Multiple chars", "a,b,c", "a", "b", "c");
        assertTextArray("Multiple chars", "a,b,\\c", "a", "b", "\\c");
        assertTextArray("Multiple chars and escape at beginning and end",
                "\\a,b,\\c\\", "\\a", "b", "\\c\\");
        assertTextArray("Multiple surrogates", PAIR1 + "," + PAIR2 + "," + PAIR3,
                PAIR1, PAIR2, PAIR3);
        assertTextArray("Multiple chars surrounded by spaces", " a , b , c ", " a ", " b ", " c ");
        assertTextArray("Multiple labels", "abc,def,ghi", "abc", "def", "ghi");
        assertTextArray("Multiple surrogated", SURROGATE1 + "," + SURROGATE2,
                SURROGATE1, SURROGATE2);
        assertTextArray("Multiple labels surrounded by spaces", " abc , def , ghi ",
                " abc ", " def ", " ghi ");
    }

    public void testParseCsvTextMultiEscaped() {
        assertTextArray("Multiple chars with comma", "a,\\,,c", "a", "\\,", "c");
        assertTextArray("Multiple chars with comma surrounded by spaces", " a , \\, , c ",
                " a ", " \\, ", " c ");
        assertTextArray("Multiple labels with escape",
                "\\abc,d\\ef,gh\\i", "\\abc", "d\\ef", "gh\\i");
        assertTextArray("Multiple labels with escape surrounded by spaces",
                " \\abc , d\\ef , gh\\i ", " \\abc ", " d\\ef ", " gh\\i ");
        assertTextArray("Multiple labels with comma and escape",
                "ab\\\\,d\\\\\\,,g\\,i", "ab\\\\", "d\\\\\\,", "g\\,i");
        assertTextArray("Multiple labels with comma and escape surrounded by spaces",
                " ab\\\\ , d\\\\\\, , g\\,i ", " ab\\\\ ", " d\\\\\\, ", " g\\,i ");

        assertTextArray("Multiple escaped @string", "\\@,\\@string/empty_string",
                "\\@", "\\@string/empty_string");
    }

    public void testParseCsvResourceError() {
        assertError("Incomplete resource name", "@string/", "@string/");
        assertError("Non existing resource", "@string/non_existing");
    }

    public void testParseCsvResourceZero() {
        assertTextArray("Empty string",
                "@string/empty_string");
    }

    public void testParseCsvResourceSingle() {
        assertTextArray("Single char",
                "@string/single_char", "a");
        assertTextArray("Space",
                "@string/space", " ");
        assertTextArray("Single label",
                "@string/single_label", "abc");
        assertTextArray("Spaces",
                "@string/spaces", "   ");
        assertTextArray("Spaces in label",
                "@string/spaces_in_label", "a b c");
        assertTextArray("Spaces at beginning of label",
                "@string/spaces_at_beginning_of_label", " abc");
        assertTextArray("Spaces at end of label",
                "@string/spaces_at_end_of_label", "abc ");
        assertTextArray("label surrounded by spaces",
                "@string/label_surrounded_by_spaces", " abc ");

        assertTextArray("Escape and single char",
                "\\\\@string/single_char", "\\\\a");
    }

    public void testParseCsvResourceSingleEscaped() {
        assertTextArray("Escaped char",
                "@string/escaped_char", "\\a");
        assertTextArray("Escaped comma",
                "@string/escaped_comma", "\\,");
        assertTextArray("Escaped comma escape",
                "@string/escaped_comma_escape", "a\\,\\");
        assertTextArray("Escaped escape",
                "@string/escaped_escape", "\\\\");
        assertTextArray("Escaped label",
                "@string/escaped_label", "a\\bc");
        assertTextArray("Escaped label at beginning",
                "@string/escaped_label_at_beginning", "\\abc");
        assertTextArray("Escaped label at end",
                "@string/escaped_label_at_end", "abc\\");
        assertTextArray("Escaped label with comma",
                "@string/escaped_label_with_comma", "a\\,c");
        assertTextArray("Escaped label with comma at beginning",
                "@string/escaped_label_with_comma_at_beginning", "\\,bc");
        assertTextArray("Escaped label with comma at end",
                "@string/escaped_label_with_comma_at_end", "ab\\,");
        assertTextArray("Escaped label with successive",
                "@string/escaped_label_with_successive", "\\,\\\\bc");
        assertTextArray("Escaped label with escape",
                "@string/escaped_label_with_escape", "a\\\\c");
    }

    public void testParseCsvResourceMulti() {
        assertTextArray("Multiple chars",
                "@string/multiple_chars", "a", "b", "c");
        assertTextArray("Multiple chars surrounded by spaces",
                "@string/multiple_chars_surrounded_by_spaces",
                " a ", " b ", " c ");
        assertTextArray("Multiple labels",
                "@string/multiple_labels", "abc", "def", "ghi");
        assertTextArray("Multiple labels surrounded by spaces",
                "@string/multiple_labels_surrounded_by_spaces", " abc ", " def ", " ghi ");
    }

    public void testParseCsvResourcetMultiEscaped() {
        assertTextArray("Multiple chars with comma",
                "@string/multiple_chars_with_comma",
                "a", "\\,", "c");
        assertTextArray("Multiple chars with comma surrounded by spaces",
                "@string/multiple_chars_with_comma_surrounded_by_spaces",
                " a ", " \\, ", " c ");
        assertTextArray("Multiple labels with escape",
                "@string/multiple_labels_with_escape",
                "\\abc", "d\\ef", "gh\\i");
        assertTextArray("Multiple labels with escape surrounded by spaces",
                "@string/multiple_labels_with_escape_surrounded_by_spaces",
                " \\abc ", " d\\ef ", " gh\\i ");
        assertTextArray("Multiple labels with comma and escape",
                "@string/multiple_labels_with_comma_and_escape",
                "ab\\\\", "d\\\\\\,", "g\\,i");
        assertTextArray("Multiple labels with comma and escape surrounded by spaces",
                "@string/multiple_labels_with_comma_and_escape_surrounded_by_spaces",
                " ab\\\\ ", " d\\\\\\, ", " g\\,i ");
    }

    public void testParseMultipleResources() {
        assertTextArray("Literals and resources",
                "1,@string/multiple_chars,z", "1", "a", "b", "c", "z");
        assertTextArray("Literals and resources and escape at end",
                "\\1,@string/multiple_chars,z\\", "\\1", "a", "b", "c", "z\\");
        assertTextArray("Multiple single resource chars and labels",
                "@string/single_char,@string/single_label,@string/escaped_comma",
                "a", "abc", "\\,");
        assertTextArray("Multiple single resource chars and labels 2",
                "@string/single_char,@string/single_label,@string/escaped_comma_escape",
                "a", "abc", "a\\,\\");
        assertTextArray("Multiple multiple resource chars and labels",
                "@string/multiple_chars,@string/multiple_labels,@string/multiple_chars_with_comma",
                "a", "b", "c", "abc", "def", "ghi", "a", "\\,", "c");
        assertTextArray("Concatenated resources",
                "@string/multiple_chars@string/multiple_labels@string/multiple_chars_with_comma",
                "a", "b", "cabc", "def", "ghia", "\\,", "c");
        assertTextArray("Concatenated resource and literal",
                "abc@string/multiple_labels",
                "abcabc", "def", "ghi");
    }
}
