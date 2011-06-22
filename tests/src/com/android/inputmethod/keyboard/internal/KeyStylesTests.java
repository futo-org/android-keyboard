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

import com.android.inputmethod.keyboard.internal.KeyStyles.EmptyKeyStyle;

import android.test.AndroidTestCase;
import android.text.TextUtils;

public class KeyStylesTests extends AndroidTestCase {
    private static String format(String message, Object expected, Object actual) {
        return message + " expected:<" + expected + "> but was:<" + actual + ">";
    }

    private static void assertTextArray(String message, CharSequence value,
            CharSequence ... expected) {
        final CharSequence actual[] = EmptyKeyStyle.parseCsvText(value);
        if (expected.length == 0) {
            assertNull(message, actual);
            return;
        }
        assertSame(message + ": result length", expected.length, actual.length);
        for (int i = 0; i < actual.length; i++) {
            final boolean equals = TextUtils.equals(expected[i], actual[i]);
            assertTrue(format(message + ": result at " + i + ":", expected[i], actual[i]), equals);
        }
    }

    public void testParseCsvTextZero() {
        assertTextArray("Empty string", "");
    }

    public void testParseCsvTextSingle() {
        assertTextArray("Single char", "a", "a");
        assertTextArray("Space", " ", " ");
        assertTextArray("Single label", "abc", "abc");
        assertTextArray("Spaces", "   ", "   ");
        assertTextArray("Spaces in label", "a b c", "a b c");
        assertTextArray("Spaces at beginning of label", " abc", " abc");
        assertTextArray("Spaces at end of label", "abc ", "abc ");
        assertTextArray("label surrounded by spaces", " abc ", " abc ");
    }

    public void testParseCsvTextSingleEscaped() {
        assertTextArray("Escaped char", "\\a", "a");
        assertTextArray("Escaped comma", "\\,", ",");
        assertTextArray("Escaped escape", "\\\\", "\\");
        assertTextArray("Escaped label", "a\\bc", "abc");
        assertTextArray("Escaped label at begininng", "\\abc", "abc");
        assertTextArray("Escaped label with comma", "a\\,c", "a,c");
        assertTextArray("Escaped label with comma at beginning", "\\,bc", ",bc");
        assertTextArray("Escaped label with successive", "\\,\\\\bc", ",\\bc");
        assertTextArray("Escaped label with escape", "a\\\\c", "a\\c");
    }

    public void testParseCsvTextMulti() {
        assertTextArray("Multiple chars", "a,b,c", "a", "b", "c");
        assertTextArray("Multiple chars surrounded by spaces", " a , b , c ", " a ", " b ", " c ");
        assertTextArray("Multiple labels", "abc,def,ghi", "abc", "def", "ghi");
        assertTextArray("Multiple labels surrounded by spaces", " abc , def , ghi ",
                " abc ", " def ", " ghi ");
    }

    public void testParseCsvTextMultiEscaped() {
        assertTextArray("Multiple chars with comma", "a,\\,,c", "a", ",", "c");
        assertTextArray("Multiple chars with comma surrounded by spaces", " a , \\, , c ",
                " a ", " , ", " c ");
        assertTextArray("Multiple labels with escape", "\\abc,d\\ef,gh\\i", "abc", "def", "ghi");
        assertTextArray("Multiple labels with escape surrounded by spaces",
                " \\abc , d\\ef , gh\\i ", " abc ", " def ", " ghi ");
        assertTextArray("Multiple labels with comma and escape",
                "ab\\\\,d\\\\\\,,g\\,i", "ab\\", "d\\,", "g,i");
        assertTextArray("Multiple labels with comma and escape surrounded by spaces",
                " ab\\\\ , d\\\\\\, , g\\,i ", " ab\\ ", " d\\, ", " g,i ");
    }
}
