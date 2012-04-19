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
        mLabelsSet.loadStringResources(getContext());
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

    private static void assertArrayEquals(String message, Object[] expected, Object[] actual) {
        if (expected == actual) {
            return;
        }
        if (expected == null || actual == null) {
            assertEquals(message, Arrays.toString(expected), Arrays.toString(actual));
            return;
        }
        if (expected.length != actual.length) {
            assertEquals(message + " [length]", Arrays.toString(expected), Arrays.toString(actual));
            return;
        }
        for (int i = 0; i < expected.length; i++) {
            assertEquals(message + " [" + i + "]",
                    Arrays.toString(expected), Arrays.toString(actual));
        }
    }

    private void assertTextArray(String message, String value, String ... expectedArray) {
        final String[] actual = KeySpecParser.parseCsvString(value, mLabelsSet);
        final String[] expected = (expectedArray.length == 0) ? null : expectedArray;
        assertArrayEquals(message, expected, actual);
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
        assertTextArray("Incomplete RESOURCE REFERENCE 2", "!LABEL", "!LABEL");
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
        assertTextArray("Escaped !LABEL/", "\\!LABEL/", "\\!LABEL/");
        assertTextArray("Escaped !label/name", "\\!label/empty_string", "\\!label/empty_string");
        assertTextArray("Escaped !LABEL/NAME", "\\!LABEL/EMPTY_STRING", "\\!LABEL/EMPTY_STRING");
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
        assertTextArray("Multiple escaped !LABEL", "\\!,\\!LABEL/EMPTY_STRING",
                "\\!", "\\!LABEL/EMPTY_STRING");
    }

    public void testParseCsvResourceError() {
        assertError("Incomplete resource name", "!label/", "!label/");
        assertError("Non existing resource", "!label/non_existing");
    }

    public void testParseCsvResourceZero() {
        assertTextArray("Empty string",
                "!label/empty_string");
        assertTextArray("EMPTY STRING",
                "!LABEL/EMPTY_STRING");
    }

    public void testParseCsvResourceSingle() {
        assertTextArray("Single char",
                "!label/single_char", "a");
        assertTextArray("SINGLE CHAR",
                "!LABEL/SINGLE_CHAR", "a");
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
        assertTextArray("Escape and SINGLE CHAR",
                "\\\\!LABEL/SINGLE_CHAR", "\\\\a");
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
        assertTextArray("MULTIPLE CHARS",
                "!LABEL/MULTIPLE_CHARS", "a", "b", "c");
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
        assertTextArray("Literals and RESOURCES",
                "1,!LABEL/MULTIPLE_CHARS,z", "1", "a", "b", "c", "z");
        assertTextArray("Literals and resources and escape at end",
                "\\1,!label/multiple_chars,z\\", "\\1", "a", "b", "c", "z\\");
        assertTextArray("Multiple single resource chars and labels",
                "!label/single_char,!label/single_label,!label/escaped_comma",
                "a", "abc", "\\,");
        assertTextArray("Multiple single resource chars and labels 2",
                "!label/single_char,!label/single_label,!label/escaped_comma_escape",
                "a", "abc", "a\\,\\");
        assertTextArray("Multiple single RESOURCE chars and LABELS 2",
                "!LABEL/SINGLE_CHAR,!LABEL/SINGLE_LABEL,!LABEL/ESCAPED_COMMA_ESCAPE",
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
        assertTextArray("Indirect2",
                "!label/indirect2_string", "a", "b", "c");

        assertTextArray("INDIRECT",
                "!LABEL/INDIRECT_STRING", "a", "b", "c");
        assertTextArray("INDIRECT with literal",
                "1,!LABEL/INDIRECT_STRING_WITH_LITERAL,2", "1", "x", "a", "b", "c", "y", "2");
        assertTextArray("INDIRECT2",
                "!LABEL/INDIRECT2_STRING", "a", "b", "c");

        assertTextArray("Upper indirect",
                "!label/upper_indirect_string", "a", "b", "c");
        assertTextArray("Upper indirect with literal",
                "1,!label/upper_indirect_string_with_literal,2", "1", "x", "a", "b", "c", "y", "2");
        assertTextArray("Upper indirect2",
                "!label/upper_indirect2_string", "a", "b", "c");

        assertTextArray("UPPER INDIRECT",
                "!LABEL/upper_INDIRECT_STRING", "a", "b", "c");
        assertTextArray("Upper INDIRECT with literal",
                "1,!LABEL/upper_INDIRECT_STRING_WITH_LITERAL,2", "1", "x", "a", "b", "c", "y", "2");
        assertTextArray("Upper INDIRECT2",
                "!LABEL/upper_INDIRECT2_STRING", "a", "b", "c");
    }

    public void testParseInfiniteIndirectReference() {
        assertError("Infinite indirection",
                "1,!label/infinite_indirection,2", "1", "infinite", "<infinite>", "loop", "2");
        assertError("INFINITE INDIRECTION",
                "1,!LABEL/INFINITE_INDIRECTION,2", "1", "infinite", "<infinite>", "loop", "2");

        assertError("Upper infinite indirection",
                "1,!label/upper_infinite_indirection,2",
                "1", "infinite", "<infinite>", "loop", "2");
        assertError("Upper INFINITE INDIRECTION",
                "1,!LABEL/UPPER_INFINITE_INDIRECTION,2",
                "1", "infinite", "<infinite>", "loop", "2");
    }

    public void testLabelReferece() {
        assertTextArray("Label time am", "!label/label_time_am", "AM");
        assertTextArray("LABEL TIME AM", "!LABEL/LABEL_TIME_AM", "AM");

        assertTextArray("More keys for am pm", "!label/more_keys_for_am_pm",
                "!fixedColumnOrder!2", "!hasLabels!", "AM", "PM");
        assertTextArray("MORE KEYS FOR AM OM", "!LABEL/MORE_KEYS_FOR_AM_PM",
                "!fixedColumnOrder!2", "!hasLabels!", "AM", "PM");

        assertTextArray("Settings as more key", "!label/settings_as_more_key",
                "!icon/settings_key|!code/key_settings");
        assertTextArray("SETTINGS AS MORE KEY", "!LABEL/SETTINGS_AS_MORE_KEY",
                "!icon/settings_key|!code/key_settings");

        assertTextArray("Indirect naviagte actions as more key",
                "!label/indirect_navigate_actions_as_more_key",
                "!fixedColumnOrder!2",
                "!hasLabels!", "Prev|!code/key_action_previous",
                "!hasLabels!", "Next|!code/key_action_next");
        assertTextArray("INDIRECT NAVIGATE ACTIONS AS MORE KEY",
                "!LABEL/INDIRECT_NAVIGATE_ACTIONS_AS_MORE_KEY",
                "!fixedColumnOrder!2",
                "!hasLabels!", "Prev|!code/key_action_previous",
                "!hasLabels!", "Next|!code/key_action_next");
    }
}
