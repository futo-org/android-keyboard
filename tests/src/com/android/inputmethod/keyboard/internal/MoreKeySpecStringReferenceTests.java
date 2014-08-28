/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Resources;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.tests.R;

import java.util.Locale;

@SmallTest
public class MoreKeySpecStringReferenceTests extends InstrumentationTestCase {
    private static final Locale TEST_LOCALE = Locale.ENGLISH;
    private final KeyboardTextsSet mTextsSet = new KeyboardTextsSet();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final Instrumentation instrumentation = getInstrumentation();
        final Context testContext = instrumentation.getContext();
        final Resources testRes = testContext.getResources();
        final String testPackageName = testRes.getResourcePackageName(R.string.empty_string);
        mTextsSet.setLocale(TEST_LOCALE, testRes, testPackageName);
    }

    private void assertTextArray(final String message, final String value,
            final String ... expectedArray) {
        final String resolvedActual = mTextsSet.resolveTextReference(value);
        final String[] actual = MoreKeySpec.splitKeySpecs(resolvedActual);
        final String[] expected = (expectedArray.length == 0) ? null : expectedArray;
        MoreKeySpecSplitTests.assertArrayEquals(message, expected, actual);
    }

    private void assertError(final String message, final String value, final String ... expected) {
        try {
            assertTextArray(message, value, expected);
            fail(message);
        } catch (Exception pcpe) {
            // success.
        }
    }

    public void testResolveNullText() {
        assertEquals("resolve null",
                mTextsSet.resolveTextReference(null), null);
    }

    public void testResolveEmptyText() {
        assertEquals("resolve empty text",
                mTextsSet.resolveTextReference("!string/empty_string"), null);
    }

    public void testSplitSingleEscaped() {
        assertTextArray("Escaped !string", "\\!string",
                "\\!string");
        assertTextArray("Escaped !string/", "\\!string/",
                "\\!string/");
        assertTextArray("Escaped !STRING/", "\\!STRING/",
                "\\!STRING/");
        assertTextArray("Escaped !string/name", "\\!string/empty_string",
                "\\!string/empty_string");
        assertTextArray("Escaped !STRING/NAME", "\\!STRING/EMPTY_STRING",
                "\\!STRING/EMPTY_STRING");
    }

    public void testSplitMultiEscaped() {
        assertTextArray("Multiple escaped !string", "\\!,\\!string/empty_string",
                "\\!", "\\!string/empty_string");
        assertTextArray("Multiple escaped !STRING", "\\!,\\!STRING/EMPTY_STRING",
                "\\!", "\\!STRING/EMPTY_STRING");
    }

    public void testSplitStringReferenceError() {
        assertError("Incomplete resource name", "!string/", "!string/");
        assertError("Non existing resource", "!string/non_existing");
    }

    public void testSplitEmptyStringReference() {
        assertTextArray("Empty string", "!string/empty_string");
    }

    public void testSplitResourceSingle() {
        assertTextArray("Single char", "!string/single_char",
                "a");
        assertTextArray("Space", "!string/space",
                " ");
        assertTextArray("Single label", "!string/single_label",
                "abc");
        assertTextArray("Spaces", "!string/spaces",
                "   ");
        assertTextArray("Spaces in label", "!string/spaces_in_label",
                "a b c");
        assertTextArray("Spaces at beginning of label", "!string/spaces_at_beginning_of_label",
                " abc");
        assertTextArray("Spaces at end of label", "!string/spaces_at_end_of_label",
                "abc ");
        assertTextArray("label surrounded by spaces", "!string/label_surrounded_by_spaces",
                " abc ");
        assertTextArray("Escape and single char", "\\\\!string/single_char",
                "\\\\a");
    }

    public void testSplitResourceSingleEscaped() {
        assertTextArray("Escaped char",
                "!string/escaped_char", "\\a");
        assertTextArray("Escaped comma",
                "!string/escaped_comma", "\\,");
        assertTextArray("Escaped comma escape",
                "!string/escaped_comma_escape", "a\\,\\");
        assertTextArray("Escaped escape",
                "!string/escaped_escape", "\\\\");
        assertTextArray("Escaped label",
                "!string/escaped_label", "a\\bc");
        assertTextArray("Escaped label at beginning",
                "!string/escaped_label_at_beginning", "\\abc");
        assertTextArray("Escaped label at end",
                "!string/escaped_label_at_end", "abc\\");
        assertTextArray("Escaped label with comma",
                "!string/escaped_label_with_comma", "a\\,c");
        assertTextArray("Escaped label with comma at beginning",
                "!string/escaped_label_with_comma_at_beginning", "\\,bc");
        assertTextArray("Escaped label with comma at end",
                "!string/escaped_label_with_comma_at_end", "ab\\,");
        assertTextArray("Escaped label with successive",
                "!string/escaped_label_with_successive", "\\,\\\\bc");
        assertTextArray("Escaped label with escape",
                "!string/escaped_label_with_escape", "a\\\\c");
    }

    public void testSplitResourceMulti() {
        assertTextArray("Multiple chars",
                "!string/multiple_chars", "a", "b", "c");
        assertTextArray("Multiple chars surrounded by spaces",
                "!string/multiple_chars_surrounded_by_spaces",
                " a ", " b ", " c ");
        assertTextArray("Multiple labels",
                "!string/multiple_labels", "abc", "def", "ghi");
        assertTextArray("Multiple labels surrounded by spaces",
                "!string/multiple_labels_surrounded_by_spaces", " abc ", " def ", " ghi ");
    }

    public void testSplitResourcetMultiEscaped() {
        assertTextArray("Multiple chars with comma",
                "!string/multiple_chars_with_comma",
                "a", "\\,", "c");
        assertTextArray("Multiple chars with comma surrounded by spaces",
                "!string/multiple_chars_with_comma_surrounded_by_spaces",
                " a ", " \\, ", " c ");
        assertTextArray("Multiple labels with escape",
                "!string/multiple_labels_with_escape",
                "\\abc", "d\\ef", "gh\\i");
        assertTextArray("Multiple labels with escape surrounded by spaces",
                "!string/multiple_labels_with_escape_surrounded_by_spaces",
                " \\abc ", " d\\ef ", " gh\\i ");
        assertTextArray("Multiple labels with comma and escape",
                "!string/multiple_labels_with_comma_and_escape",
                "ab\\\\", "d\\\\\\,", "g\\,i");
        assertTextArray("Multiple labels with comma and escape surrounded by spaces",
                "!string/multiple_labels_with_comma_and_escape_surrounded_by_spaces",
                " ab\\\\ ", " d\\\\\\, ", " g\\,i ");
    }

    public void testSplitMultipleResources() {
        assertTextArray("Literals and resources",
                "1,!string/multiple_chars,z",
                "1", "a", "b", "c", "z");
        assertTextArray("Literals and resources and escape at end",
                "\\1,!string/multiple_chars,z\\",
                "\\1", "a", "b", "c", "z\\");
        assertTextArray("Multiple single resource chars and labels",
                "!string/single_char,!string/single_label,!string/escaped_comma",
                "a", "abc", "\\,");
        assertTextArray("Multiple single resource chars and labels 2",
                "!string/single_char,!string/single_label,!string/escaped_comma_escape",
                "a", "abc", "a\\,\\");
        assertTextArray("Multiple multiple resource chars and labels",
                "!string/multiple_chars,!string/multiple_labels,!string/multiple_chars_with_comma",
                "a", "b", "c", "abc", "def", "ghi", "a", "\\,", "c");
        assertTextArray("Concatenated resources",
                "!string/multiple_chars!string/multiple_labels!string/multiple_chars_with_comma",
                "a", "b", "cabc", "def", "ghia", "\\,", "c");
        assertTextArray("Concatenated resource and literal",
                "abc!string/multiple_labels",
                "abcabc", "def", "ghi");
    }

    public void testSplitIndirectReference() {
        assertTextArray("Indirect",
                "!string/indirect_string", "a", "b", "c");
        assertTextArray("Indirect with literal",
                "1,!string/indirect_string_with_literal,2", "1", "x", "a", "b", "c", "y", "2");
        assertTextArray("Indirect2",
                "!string/indirect2_string", "a", "b", "c");
    }

    public void testSplitInfiniteIndirectReference() {
        assertError("Infinite indirection",
                "1,!string/infinite_indirection,2", "1", "infinite", "<infinite>", "loop", "2");
    }

    public void testLabelReferece() {
        assertTextArray("Indirect naviagte actions as more key",
                "!string/keyspec_indirect_navigate_actions",
                "!fixedColumnOrder!2",
                "!hasLabels!", "ActionPrevious|!code/key_action_previous",
                "!hasLabels!", "ActionNext|!code/key_action_next");
    }

    public void testUselessUpperCaseSpecifier() {
        assertTextArray("EMPTY STRING",
                "!STRING/EMPTY_STRING", "!STRING/EMPTY_STRING");

        assertTextArray("SINGLE CHAR",
                "!STRING/SINGLE_CHAR", "!STRING/SINGLE_CHAR");
        assertTextArray("Escape and SINGLE CHAR",
                "\\\\!STRING/SINGLE_CHAR", "\\\\!STRING/SINGLE_CHAR");

        assertTextArray("MULTIPLE CHARS",
                "!STRING/MULTIPLE_CHARS", "!STRING/MULTIPLE_CHARS");

        assertTextArray("Literals and RESOURCES",
                "1,!STRING/MULTIPLE_CHARS,z", "1", "!STRING/MULTIPLE_CHARS", "z");
        assertTextArray("Multiple single RESOURCE chars and LABELS 2",
                "!STRING/SINGLE_CHAR,!STRING/SINGLE_LABEL,!STRING/ESCAPED_COMMA_ESCAPE",
                "!STRING/SINGLE_CHAR", "!STRING/SINGLE_LABEL", "!STRING/ESCAPED_COMMA_ESCAPE");

        assertTextArray("INDIRECT",
                "!STRING/INDIRECT_STRING", "!STRING/INDIRECT_STRING");
        assertTextArray("INDIRECT with literal",
                "1,!STRING/INDIRECT_STRING_WITH_LITERAL,2",
                "1", "!STRING/INDIRECT_STRING_WITH_LITERAL", "2");
        assertTextArray("INDIRECT2",
                "!STRING/INDIRECT2_STRING", "!STRING/INDIRECT2_STRING");

        assertTextArray("Upper indirect",
                "!string/upper_indirect_string", "!STRING/MULTIPLE_CHARS");
        assertTextArray("Upper indirect with literal",
                "1,!string/upper_indirect_string_with_literal,2",
                "1", "x", "!STRING/MULTIPLE_CHARS", "y", "2");
        assertTextArray("Upper indirect2",
                "!string/upper_indirect2_string", "!STRING/UPPER_INDIRECT_STRING");

        assertTextArray("UPPER INDIRECT",
                "!STRING/upper_INDIRECT_STRING", "!STRING/upper_INDIRECT_STRING");
        assertTextArray("Upper INDIRECT with literal",
                "1,!STRING/upper_INDIRECT_STRING_WITH_LITERAL,2",
                "1", "!STRING/upper_INDIRECT_STRING_WITH_LITERAL", "2");
        assertTextArray("Upper INDIRECT2",
                "!STRING/upper_INDIRECT2_STRING", "!STRING/upper_INDIRECT2_STRING");

        assertTextArray("INFINITE INDIRECTION",
                "1,!STRING/INFINITE_INDIRECTION,2", "1", "!STRING/INFINITE_INDIRECTION", "2");

        assertTextArray("Upper infinite indirection",
                "1,!string/upper_infinite_indirection,2",
                "1", "infinite", "!STRING/INFINITE_INDIRECTION", "loop", "2");
        assertTextArray("Upper INFINITE INDIRECTION",
                "1,!STRING/UPPER_INFINITE_INDIRECTION,2",
                "1", "!STRING/UPPER_INFINITE_INDIRECTION", "2");

        assertTextArray("INDIRECT NAVIGATE ACTIONS AS MORE KEY",
                "!STRING/INDIRECT_NAVIGATE_ACTIONS_AS_MORE_KEY",
                "!STRING/INDIRECT_NAVIGATE_ACTIONS_AS_MORE_KEY");
     }
}
