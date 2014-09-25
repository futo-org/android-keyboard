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

package com.android.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.R;

import java.util.Arrays;
import java.util.Locale;

@SmallTest
public class MoreKeySpecSplitTests extends AndroidTestCase {
    private static final Locale TEST_LOCALE = Locale.ENGLISH;
    private final KeyboardTextsSet mTextsSet = new KeyboardTextsSet();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final Context targetContext = getContext();
        final Resources targetRes = targetContext.getResources();
        final String targetPackageName = targetRes.getResourcePackageName(
                R.string.english_ime_name);
        mTextsSet.setLocale(TEST_LOCALE, targetRes, targetPackageName);
    }

    static <T> void assertArrayEquals(final String message, final T[] expected, final T[] actual) {
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
            final T e = expected[i];
            final T a = actual[i];
            if (e == a) {
                continue;
            }
            assertEquals(message + " [" + i + "]", e, a);
        }
    }

    private void assertTextArray(final String message, final String value,
            final String ... expectedArray) {
        final String resolvedActual = mTextsSet.resolveTextReference(value);
        final String[] actual = MoreKeySpec.splitKeySpecs(resolvedActual);
        final String[] expected = (expectedArray.length == 0) ? null : expectedArray;
        assertArrayEquals(message, expected, actual);
    }

    private void assertError(final String message, final String value, final String ... expected) {
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

    public void testSplitZero() {
        assertTextArray("Empty string", "");
        assertTextArray("Empty entry", ",");
        assertTextArray("Empty entry at beginning", ",a", "a");
        assertTextArray("Empty entry at end", "a,", "a");
        assertTextArray("Empty entry at middle", "a,,b", "a", "b");
        assertTextArray("Empty entries with escape", ",a,b\\,c,,d,", "a", "b\\,c", "d");
    }

    public void testSplitSingle() {
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

        assertTextArray("Incomplete resource reference 1", "text", "text");
        assertTextArray("Incomplete resource reference 2", "!text", "!text");
        assertTextArray("Incomplete RESOURCE REFERENCE 2", "!TEXT", "!TEXT");
        assertTextArray("Incomplete resource reference 3", "text/", "text/");
        assertTextArray("Incomplete resource reference 4", "!" + SURROGATE2, "!" + SURROGATE2);
    }

    public void testSplitSingleEscaped() {
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

        assertTextArray("Escaped !text", "\\!text", "\\!text");
        assertTextArray("Escaped !text/", "\\!text/", "\\!text/");
        assertTextArray("Escaped !TEXT/", "\\!TEXT/", "\\!TEXT/");
        assertTextArray("Escaped !text/name", "\\!text/empty_string", "\\!text/empty_string");
        assertTextArray("Escaped !TEXT/NAME", "\\!TEXT/EMPTY_STRING", "\\!TEXT/EMPTY_STRING");
    }

    public void testSplitMulti() {
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

    public void testSplitMultiEscaped() {
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

        assertTextArray("Multiple escaped !text", "\\!,\\!text/empty_string",
                "\\!", "\\!text/empty_string");
        assertTextArray("Multiple escaped !TEXT", "\\!,\\!TEXT/EMPTY_STRING",
                "\\!", "\\!TEXT/EMPTY_STRING");
    }

    public void testSplitTextReferenceError() {
        assertError("Incomplete text name", "!text/", "!text/");
        assertError("Non existing text", "!text/non_existing");
    }

    public void testSplitEmptyTextReference() {
        // Note that morekeys_q of English locale is empty.
        assertTextArray("Empty string", "!text/morekeys_q");
    }

    public void testLabelReferece() {
        assertTextArray("Label time am", "!text/keylabel_time_am", "AM");

        assertTextArray("More keys for am pm", "!text/morekeys_am_pm",
                "!fixedColumnOrder!2", "!hasLabels!", "AM", "PM");

        assertTextArray("Settings as more key", "!text/keyspec_settings",
                "!icon/settings_key|!code/key_settings");
    }

    public void testUselessUpperCaseSpecifier() {
        assertTextArray("EMPTY STRING",
                "!TEXT/EMPTY_STRING", "!TEXT/EMPTY_STRING");

        assertTextArray("SINGLE CHAR",
                "!TEXT/SINGLE_CHAR", "!TEXT/SINGLE_CHAR");
        assertTextArray("Escape and SINGLE CHAR",
                "\\\\!TEXT/SINGLE_CHAR", "\\\\!TEXT/SINGLE_CHAR");

        assertTextArray("MULTIPLE CHARS",
                "!TEXT/MULTIPLE_CHARS", "!TEXT/MULTIPLE_CHARS");

        assertTextArray("Literals and RESOURCES",
                "1,!TEXT/MULTIPLE_CHARS,z", "1", "!TEXT/MULTIPLE_CHARS", "z");
        assertTextArray("Multiple single RESOURCE chars and LABELS 2",
                "!TEXT/SINGLE_CHAR,!TEXT/SINGLE_LABEL,!TEXT/ESCAPED_COMMA_ESCAPE",
                "!TEXT/SINGLE_CHAR", "!TEXT/SINGLE_LABEL", "!TEXT/ESCAPED_COMMA_ESCAPE");

        assertTextArray("INDIRECT",
                "!TEXT/INDIRECT_STRING", "!TEXT/INDIRECT_STRING");
        assertTextArray("INDIRECT with literal",
                "1,!TEXT/INDIRECT_STRING_WITH_LITERAL,2",
                "1", "!TEXT/INDIRECT_STRING_WITH_LITERAL", "2");
        assertTextArray("INDIRECT2",
                "!TEXT/INDIRECT2_STRING", "!TEXT/INDIRECT2_STRING");

        assertTextArray("UPPER INDIRECT",
                "!TEXT/upper_INDIRECT_STRING", "!TEXT/upper_INDIRECT_STRING");
        assertTextArray("Upper INDIRECT with literal",
                "1,!TEXT/upper_INDIRECT_STRING_WITH_LITERAL,2",
                "1", "!TEXT/upper_INDIRECT_STRING_WITH_LITERAL", "2");
        assertTextArray("Upper INDIRECT2",
                "!TEXT/upper_INDIRECT2_STRING", "!TEXT/upper_INDIRECT2_STRING");

        assertTextArray("INFINITE INDIRECTION",
                "1,!TEXT/INFINITE_INDIRECTION,2", "1", "!TEXT/INFINITE_INDIRECTION", "2");

        assertTextArray("Upper INFINITE INDIRECTION",
                "1,!TEXT/UPPER_INFINITE_INDIRECTION,2",
                "1", "!TEXT/UPPER_INFINITE_INDIRECTION", "2");

        assertTextArray("LABEL TIME AM", "!TEXT/LABEL_TIME_AM", "!TEXT/LABEL_TIME_AM");
        assertTextArray("MORE KEYS FOR AM OM", "!TEXT/MORE_KEYS_FOR_AM_PM",
                "!TEXT/MORE_KEYS_FOR_AM_PM");
        assertTextArray("SETTINGS AS MORE KEY", "!TEXT/SETTINGS_AS_MORE_KEY",
                "!TEXT/SETTINGS_AS_MORE_KEY");
        assertTextArray("INDIRECT NAVIGATE ACTIONS AS MORE KEY",
                "!TEXT/INDIRECT_NAVIGATE_ACTIONS_AS_MORE_KEY",
                "!TEXT/INDIRECT_NAVIGATE_ACTIONS_AS_MORE_KEY");
     }
}
