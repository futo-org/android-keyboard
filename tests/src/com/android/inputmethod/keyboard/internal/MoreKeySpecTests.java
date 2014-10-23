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

import static com.android.inputmethod.keyboard.internal.KeyboardIconsSet.ICON_UNDEFINED;
import static com.android.inputmethod.latin.common.Constants.CODE_UNSPECIFIED;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.common.Constants;

import java.util.Arrays;
import java.util.Locale;

@SmallTest
public final class MoreKeySpecTests extends KeySpecParserTestsBase {
    @Override
    protected void assertParser(final String message, final String moreKeySpec,
            final String expectedLabel, final String expectedOutputText, final int expectedIconId,
            final int expectedCode) {
        final String labelResolved = mTextsSet.resolveTextReference(moreKeySpec);
        final MoreKeySpec spec = new MoreKeySpec(
                labelResolved, false /* needsToUpperCase */, Locale.US);
        assertEquals(message + " [label]", expectedLabel, spec.mLabel);
        assertEquals(message + " [ouptputText]", expectedOutputText, spec.mOutputText);
        assertEquals(message + " [icon]",
                KeyboardIconsSet.getIconName(expectedIconId),
                KeyboardIconsSet.getIconName(spec.mIconId));
        assertEquals(message + " [code]",
                Constants.printableCode(expectedCode),
                Constants.printableCode(spec.mCode));
    }

    // TODO: Move this method to {@link KeySpecParserBase}.
    public void testEmptySpec() {
        assertParserError("Null spec", null,
                null, null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParserError("Empty spec", "",
                null, null, ICON_UNDEFINED, CODE_UNSPECIFIED);
    }

    private static void assertArrayEquals(final String message, final Object[] expected,
            final Object[] actual) {
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

    private static void assertInsertAdditionalMoreKeys(final String message,
            final String[] moreKeys, final String[] additionalMoreKeys, final String[] expected) {
        final String[] actual = MoreKeySpec.insertAdditionalMoreKeys(moreKeys, additionalMoreKeys);
        assertArrayEquals(message, expected, actual);
    }

    @SuppressWarnings("static-method")
    public void testEmptyEntry() {
        assertInsertAdditionalMoreKeys("null more keys and null additons",
                null,
                null,
                null);
        assertInsertAdditionalMoreKeys("null more keys and empty additons",
                null,
                new String[0],
                null);
        assertInsertAdditionalMoreKeys("empty more keys and null additons",
                new String[0],
                null,
                null);
        assertInsertAdditionalMoreKeys("empty more keys and empty additons",
                new String[0],
                new String[0],
                null);

        assertInsertAdditionalMoreKeys("filter out empty more keys",
                new String[] { null, "a", "", "b", null },
                null,
                new String[] { "a", "b" });
        assertInsertAdditionalMoreKeys("filter out empty additons",
                new String[] { "a", "%", "b", "%", "c", "%", "d" },
                new String[] { null, "A", "", "B", null },
                new String[] { "a", "A", "b", "B", "c", "d" });
    }

    @SuppressWarnings("static-method")
    public void testInsertAdditionalMoreKeys() {
        // Escaped marker.
        assertInsertAdditionalMoreKeys("escaped marker",
                new String[] { "\\%", "%-)" },
                new String[] { "1", "2" },
                new String[] { "1", "2", "\\%", "%-)" });

        // 0 more key.
        assertInsertAdditionalMoreKeys("null & null", null, null, null);
        assertInsertAdditionalMoreKeys("null & 1 additon",
                null,
                new String[] { "1" },
                new String[] { "1" });
        assertInsertAdditionalMoreKeys("null & 2 additons",
                null,
                new String[] { "1", "2" },
                new String[] { "1", "2" });

        // 0 additional more key.
        assertInsertAdditionalMoreKeys("1 more key & null",
                new String[] { "A" },
                null,
                new String[] { "A" });
        assertInsertAdditionalMoreKeys("2 more keys & null",
                new String[] { "A", "B" },
                null,
                new String[] { "A", "B" });

        // No marker.
        assertInsertAdditionalMoreKeys("1 more key & 1 addtional & no marker",
                new String[] { "A" },
                new String[] { "1" },
                new String[] { "1", "A" });
        assertInsertAdditionalMoreKeys("1 more key & 2 addtionals & no marker",
                new String[] { "A" },
                new String[] { "1", "2" },
                new String[] { "1", "2", "A" });
        assertInsertAdditionalMoreKeys("2 more keys & 1 addtional & no marker",
                new String[] { "A", "B" },
                new String[] { "1" },
                new String[] { "1", "A", "B" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 addtionals & no marker",
                new String[] { "A", "B" },
                new String[] { "1", "2" },
                new String[] { "1", "2", "A", "B" });

        // 1 marker.
        assertInsertAdditionalMoreKeys("1 more key & 1 additon & marker at head",
                new String[] { "%", "A" },
                new String[] { "1" },
                new String[] { "1", "A" });
        assertInsertAdditionalMoreKeys("1 more key & 1 additon & marker at tail",
                new String[] { "A", "%" },
                new String[] { "1" },
                new String[] { "A", "1" });
        assertInsertAdditionalMoreKeys("2 more keys & 1 additon & marker at middle",
                new String[] { "A", "%", "B" },
                new String[] { "1" },
                new String[] { "A", "1", "B" });

        // 1 marker & excess additional more keys.
        assertInsertAdditionalMoreKeys("1 more key & 2 additons & marker at head",
                new String[] { "%", "A", "B" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "B", "2" });
        assertInsertAdditionalMoreKeys("1 more key & 2 additons & marker at tail",
                new String[] { "A", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "A", "B", "1", "2" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 additons & marker at middle",
                new String[] { "A", "%", "B" },
                new String[] { "1", "2" },
                new String[] { "A", "1", "B", "2" });

        // 2 markers.
        assertInsertAdditionalMoreKeys("0 more key & 2 addtional & 2 markers",
                new String[] { "%", "%" },
                new String[] { "1", "2" },
                new String[] { "1", "2" });
        assertInsertAdditionalMoreKeys("1 more key & 2 addtional & 2 markers at head",
                new String[] { "%", "%", "A" },
                new String[] { "1", "2" },
                new String[] { "1", "2", "A" });
        assertInsertAdditionalMoreKeys("1 more key & 2 addtional & 2 markers at tail",
                new String[] { "A", "%", "%" },
                new String[] { "1", "2" },
                new String[] { "A", "1", "2" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 addtional & 2 markers at middle",
                new String[] { "A", "%", "%", "B" },
                new String[] { "1", "2" },
                new String[] { "A", "1", "2", "B" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 addtional & 2 markers at head & middle",
                new String[] { "%", "A", "%", "B" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "2", "B" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 addtional & 2 markers at head & tail",
                new String[] { "%", "A", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "B", "2" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 addtional & 2 markers at middle & tail",
                new String[] { "A", "%", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "A", "1", "B", "2" });

        // 2 markers & excess additional more keys.
        assertInsertAdditionalMoreKeys("0 more key & 2 additons & 2 markers",
                new String[] { "%", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "2", "3" });
        assertInsertAdditionalMoreKeys("1 more key & 2 additons & 2 markers at head",
                new String[] { "%", "%", "A" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "2", "A", "3" });
        assertInsertAdditionalMoreKeys("1 more key & 2 additons & 2 markers at tail",
                new String[] { "A", "%", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "A", "1", "2", "3" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 additons & 2 markers at middle",
                new String[] { "A", "%", "%", "B" },
                new String[] { "1", "2", "3" },
                new String[] { "A", "1", "2", "B", "3" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 additons & 2 markers at head & middle",
                new String[] { "%", "A", "%", "B" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "A", "2", "B", "3" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 additons & 2 markers at head & tail",
                new String[] { "%", "A", "B", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "A", "B", "2", "3" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 additons & 2 markers at middle & tail",
                new String[] { "A", "%", "B", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "A", "1", "B", "2", "3" });

        // 0 addtional more key and excess markers.
        assertInsertAdditionalMoreKeys("0 more key & null & excess marker",
                new String[] { "%" },
                null,
                null);
        assertInsertAdditionalMoreKeys("1 more key & null & excess marker at head",
                new String[] { "%", "A" },
                null,
                new String[] { "A" });
        assertInsertAdditionalMoreKeys("1 more key & null & excess marker at tail",
                new String[] { "A", "%" },
                null,
                new String[] { "A" });
        assertInsertAdditionalMoreKeys("2 more keys & null & excess marker at middle",
                new String[] { "A", "%", "B" },
                null,
                new String[] { "A", "B" });
        assertInsertAdditionalMoreKeys("2 more keys & null & excess markers",
                new String[] { "%", "A", "%", "B", "%" },
                null,
                new String[] { "A", "B" });

        // Excess markers.
        assertInsertAdditionalMoreKeys("0 more key & 1 additon & excess marker",
                new String[] { "%", "%" },
                new String[] { "1" },
                new String[] { "1" });
        assertInsertAdditionalMoreKeys("1 more key & 1 additon & excess marker at head",
                new String[] { "%", "%", "A" },
                new String[] { "1" },
                new String[] { "1", "A" });
        assertInsertAdditionalMoreKeys("1 more key & 1 additon & excess marker at tail",
                new String[] { "A", "%", "%" },
                new String[] { "1" },
                new String[] { "A", "1" });
        assertInsertAdditionalMoreKeys("2 more keys & 1 additon & excess marker at middle",
                new String[] { "A", "%", "%", "B" },
                new String[] { "1" },
                new String[] { "A", "1", "B" });
        assertInsertAdditionalMoreKeys("2 more keys & 1 additon & excess markers",
                new String[] { "%", "A", "%", "B", "%" },
                new String[] { "1" },
                new String[] { "1", "A", "B" });
        assertInsertAdditionalMoreKeys("2 more keys & 2 additons & excess markers",
                new String[] { "%", "A", "%", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "2", "B" });
        assertInsertAdditionalMoreKeys("2 more keys & 3 additons & excess markers",
                new String[] { "%", "A", "%", "%", "B", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "A", "2", "3", "B" });
    }

    private static final String HAS_LABEL = "!hasLabel!";
    private static final String NEEDS_DIVIDER = "!needsDividers!";
    private static final String AUTO_COLUMN_ORDER = "!autoColumnOrder!";
    private static final String FIXED_COLUMN_ORDER = "!fixedColumnOrder!";

    private static void assertGetBooleanValue(final String message, final String key,
            final String[] moreKeys, final String[] expected, final boolean expectedValue) {
        final String[] actual = Arrays.copyOf(moreKeys, moreKeys.length);
        final boolean actualValue = MoreKeySpec.getBooleanValue(actual, key);
        assertEquals(message + " [value]", expectedValue, actualValue);
        assertArrayEquals(message, expected, actual);
    }

    @SuppressWarnings("static-method")
    public void testGetBooleanValue() {
        assertGetBooleanValue("Has label", HAS_LABEL,
                new String[] { HAS_LABEL, "a", "b", "c" },
                new String[] { null, "a", "b", "c" }, true);
        // Upper case specification will not work.
        assertGetBooleanValue("HAS LABEL", HAS_LABEL,
                new String[] { HAS_LABEL.toUpperCase(Locale.ROOT), "a", "b", "c" },
                new String[] { "!HASLABEL!", "a", "b", "c" }, false);

        assertGetBooleanValue("No has label", HAS_LABEL,
                new String[] { "a", "b", "c" },
                new String[] { "a", "b", "c" }, false);
        assertGetBooleanValue("No has label with fixed clumn order", HAS_LABEL,
                new String[] { FIXED_COLUMN_ORDER + "3", "a", "b", "c" },
                new String[] { FIXED_COLUMN_ORDER + "3", "a", "b", "c" }, false);

        // Upper case specification will not work.
        assertGetBooleanValue("Multiple has label", HAS_LABEL,
                new String[] {
                    "a", HAS_LABEL.toUpperCase(Locale.ROOT), "b", "c", HAS_LABEL, "d" },
                new String[] {
                    "a", "!HASLABEL!", "b", "c", null, "d" }, true);
        // Upper case specification will not work.
        assertGetBooleanValue("Multiple has label with needs dividers", HAS_LABEL,
                new String[] {
                    "a", HAS_LABEL, "b", NEEDS_DIVIDER, HAS_LABEL.toUpperCase(Locale.ROOT), "d" },
                new String[] {
                    "a", null, "b", NEEDS_DIVIDER, "!HASLABEL!", "d" }, true);
    }

    private static void assertGetIntValue(final String message, final String key,
            final int defaultValue, final String[] moreKeys, final String[] expected,
            final int expectedValue) {
        final String[] actual = Arrays.copyOf(moreKeys, moreKeys.length);
        final int actualValue = MoreKeySpec.getIntValue(actual, key, defaultValue);
        assertEquals(message + " [value]", expectedValue, actualValue);
        assertArrayEquals(message, expected, actual);
    }

    @SuppressWarnings("static-method")
    public void testGetIntValue() {
        assertGetIntValue("Fixed column order 3", FIXED_COLUMN_ORDER, -1,
                new String[] { FIXED_COLUMN_ORDER + "3", "a", "b", "c" },
                new String[] { null, "a", "b", "c" }, 3);
        // Upper case specification will not work.
        assertGetIntValue("FIXED COLUMN ORDER 3", FIXED_COLUMN_ORDER, -1,
                new String[] { FIXED_COLUMN_ORDER.toUpperCase(Locale.ROOT) + "3", "a", "b", "c" },
                new String[] { "!FIXEDCOLUMNORDER!3", "a", "b", "c" }, -1);

        assertGetIntValue("No fixed column order", FIXED_COLUMN_ORDER, -1,
                new String[] { "a", "b", "c" },
                new String[] { "a", "b", "c" }, -1);
        assertGetIntValue("No fixed column order with auto column order", FIXED_COLUMN_ORDER, -1,
                new String[] { AUTO_COLUMN_ORDER + "5", "a", "b", "c" },
                new String[] { AUTO_COLUMN_ORDER + "5", "a", "b", "c" }, -1);

        assertGetIntValue("Multiple fixed column order 3,5", FIXED_COLUMN_ORDER, -1,
                new String[] { FIXED_COLUMN_ORDER + "3", "a", FIXED_COLUMN_ORDER + "5", "b" },
                new String[] { null, "a", null, "b" }, 3);
        // Upper case specification will not work.
        assertGetIntValue("Multiple fixed column order 5,3 with has label", FIXED_COLUMN_ORDER, -1,
                new String[] {
                    FIXED_COLUMN_ORDER.toUpperCase(Locale.ROOT) + "5", HAS_LABEL, "a",
                    FIXED_COLUMN_ORDER + "3", "b" },
                new String[] { "!FIXEDCOLUMNORDER!5", HAS_LABEL, "a", null, "b" }, 3);
    }
}
