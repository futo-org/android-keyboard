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

import android.test.AndroidTestCase;
import android.text.TextUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class KeySpecParserCsvTests extends AndroidTestCase {
    private final KeyboardLabelsSet mLabelsSet = new KeyboardLabelsSet();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mLabelsSet.setLanguage(Locale.ENGLISH.getLanguage());
        final String[] testResourceNames = getAllResourceIdNames(
                com.android.inputmethod.latin.tests.R.string.class);
        mLabelsSet.loadStringResourcesInternal(getTestContext(),
                testResourceNames,
                com.android.inputmethod.latin.tests.R.string.empty_string);
    }

    private static String[] getAllResourceIdNames(final Class<?> resourceIdClass) {
        final ArrayList<String> names = new ArrayList<String>();
        for (final Field field : resourceIdClass.getFields()) {
            if (field.getType() == Integer.TYPE) {
                names.add(field.getName());
            }
        }
        return names.toArray(new String[names.size()]);
    }

    private static String format(String message, Object expected, Object actual) {
        return message + " expected:<" + expected + "> but was:<" + actual + ">";
    }

    private void assertTextArray(String message, String value, String ... expected) {
        final String actual[] = KeySpecParser.parseCsvString(value, mLabelsSet);
        if (expected.length == 0) {
            assertNull(message + ": expected=null actual=" + Arrays.toString(actual),
                    actual);
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
        assertTextArray("Empty entry", ",");
        assertTextArray("Empty entry at beginning", ",a", "a");
        assertTextArray("Empty entry at end", "a,", "a");
        assertTextArray("Empty entry at middle", "a,,b", "a", "b");
        assertTextArray("Empty entries with escape", ",a,b\\,c,,d,", "a", "b\\,c", "d");
    }

    public void testParseCsvTextSingle() {
        assertTextArray("Single char", "a", "a");
        assertTextArray("Surrogate pair", PAIR1, PAIR1);
        assertTextArray("Single escape", "\\", "\\");
        assertTextArray("Space", " ", " ");
        assertTextArray("Single label", "abc", "abc");
        assertTextArray("Single surrogate pairs label", SURROGATE2, SURROGATE2);
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

        assertTextArray("Incomplete resource reference 1", "label", "label");
        assertTextArray("Incomplete resource reference 2", "!label", "!label");
        assertTextArray("Incomplete resource reference 3", "label/", "label/");
        assertTextArray("Incomplete resource reference 4", "!" + SURROGATE2, "!" + SURROGATE2);
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

        assertTextArray("Escaped !label", "\\!label", "\\!label");
        assertTextArray("Escaped !label/", "\\!label/", "\\!label/");
        assertTextArray("Escaped !label/", "\\!label/empty_string", "\\!label/empty_string");
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

        assertTextArray("Multiple escaped !label", "\\!,\\!label/empty_string",
                "\\!", "\\!label/empty_string");
    }

    public void testParseCsvResourceError() {
        assertError("Incomplete resource name", "!label/", "!label/");
        assertError("Non existing resource", "!label/non_existing");
    }

    public void testParseCsvResourceZero() {
        assertTextArray("Empty string",
                "!label/empty_string");
    }

    public void testParseCsvResourceSingle() {
        assertTextArray("Single char",
                "!label/single_char", "a");
        assertTextArray("Space",
                "!label/space", " ");
        assertTextArray("Single label",
                "!label/single_label", "abc");
        assertTextArray("Spaces",
                "!label/spaces", "   ");
        assertTextArray("Spaces in label",
                "!label/spaces_in_label", "a b c");
        assertTextArray("Spaces at beginning of label",
                "!label/spaces_at_beginning_of_label", " abc");
        assertTextArray("Spaces at end of label",
                "!label/spaces_at_end_of_label", "abc ");
        assertTextArray("label surrounded by spaces",
                "!label/label_surrounded_by_spaces", " abc ");

        assertTextArray("Escape and single char",
                "\\\\!label/single_char", "\\\\a");
    }

    public void testParseCsvResourceSingleEscaped() {
        assertTextArray("Escaped char",
                "!label/escaped_char", "\\a");
        assertTextArray("Escaped comma",
                "!label/escaped_comma", "\\,");
        assertTextArray("Escaped comma escape",
                "!label/escaped_comma_escape", "a\\,\\");
        assertTextArray("Escaped escape",
                "!label/escaped_escape", "\\\\");
        assertTextArray("Escaped label",
                "!label/escaped_label", "a\\bc");
        assertTextArray("Escaped label at beginning",
                "!label/escaped_label_at_beginning", "\\abc");
        assertTextArray("Escaped label at end",
                "!label/escaped_label_at_end", "abc\\");
        assertTextArray("Escaped label with comma",
                "!label/escaped_label_with_comma", "a\\,c");
        assertTextArray("Escaped label with comma at beginning",
                "!label/escaped_label_with_comma_at_beginning", "\\,bc");
        assertTextArray("Escaped label with comma at end",
                "!label/escaped_label_with_comma_at_end", "ab\\,");
        assertTextArray("Escaped label with successive",
                "!label/escaped_label_with_successive", "\\,\\\\bc");
        assertTextArray("Escaped label with escape",
                "!label/escaped_label_with_escape", "a\\\\c");
    }

    public void testParseCsvResourceMulti() {
        assertTextArray("Multiple chars",
                "!label/multiple_chars", "a", "b", "c");
        assertTextArray("Multiple chars surrounded by spaces",
                "!label/multiple_chars_surrounded_by_spaces",
                " a ", " b ", " c ");
        assertTextArray("Multiple labels",
                "!label/multiple_labels", "abc", "def", "ghi");
        assertTextArray("Multiple labels surrounded by spaces",
                "!label/multiple_labels_surrounded_by_spaces", " abc ", " def ", " ghi ");
    }

    public void testParseCsvResourcetMultiEscaped() {
        assertTextArray("Multiple chars with comma",
                "!label/multiple_chars_with_comma",
                "a", "\\,", "c");
        assertTextArray("Multiple chars with comma surrounded by spaces",
                "!label/multiple_chars_with_comma_surrounded_by_spaces",
                " a ", " \\, ", " c ");
        assertTextArray("Multiple labels with escape",
                "!label/multiple_labels_with_escape",
                "\\abc", "d\\ef", "gh\\i");
        assertTextArray("Multiple labels with escape surrounded by spaces",
                "!label/multiple_labels_with_escape_surrounded_by_spaces",
                " \\abc ", " d\\ef ", " gh\\i ");
        assertTextArray("Multiple labels with comma and escape",
                "!label/multiple_labels_with_comma_and_escape",
                "ab\\\\", "d\\\\\\,", "g\\,i");
        assertTextArray("Multiple labels with comma and escape surrounded by spaces",
                "!label/multiple_labels_with_comma_and_escape_surrounded_by_spaces",
                " ab\\\\ ", " d\\\\\\, ", " g\\,i ");
    }

    public void testParseMultipleResources() {
        assertTextArray("Literals and resources",
                "1,!label/multiple_chars,z", "1", "a", "b", "c", "z");
        assertTextArray("Literals and resources and escape at end",
                "\\1,!label/multiple_chars,z\\", "\\1", "a", "b", "c", "z\\");
        assertTextArray("Multiple single resource chars and labels",
                "!label/single_char,!label/single_label,!label/escaped_comma",
                "a", "abc", "\\,");
        assertTextArray("Multiple single resource chars and labels 2",
                "!label/single_char,!label/single_label,!label/escaped_comma_escape",
                "a", "abc", "a\\,\\");
        assertTextArray("Multiple multiple resource chars and labels",
                "!label/multiple_chars,!label/multiple_labels,!label/multiple_chars_with_comma",
                "a", "b", "c", "abc", "def", "ghi", "a", "\\,", "c");
        assertTextArray("Concatenated resources",
                "!label/multiple_chars!label/multiple_labels!label/multiple_chars_with_comma",
                "a", "b", "cabc", "def", "ghia", "\\,", "c");
        assertTextArray("Concatenated resource and literal",
                "abc!label/multiple_labels",
                "abcabc", "def", "ghi");
    }

    public void testParseIndirectReference() {
        assertTextArray("Indirect",
                "!label/indirect_string", "a", "b", "c");
        assertTextArray("Indirect with literal",
                "1,!label/indirect_string_with_literal,2", "1", "x", "a", "b", "c", "y", "2");
    }

    public void testParseInfiniteIndirectReference() {
        assertError("Infinite indirection",
                "1,!label/infinite_indirection,2", "1", "infinite", "<infinite>", "loop", "2");
    }
}
