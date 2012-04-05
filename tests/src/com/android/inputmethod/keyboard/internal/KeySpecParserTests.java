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

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.internal.KeySpecParser.MoreKeySpec;

import java.util.Arrays;
import java.util.Locale;

public class KeySpecParserTests extends AndroidTestCase {
    private final KeyboardCodesSet mCodesSet = new KeyboardCodesSet();

    private static final int ICON_UNDEFINED = KeyboardIconsSet.ICON_UNDEFINED;

    private static final String CODE_SETTINGS_NAME = "key_settings";
    private static final String ICON_SETTINGS_NAME = "settingsKey";

    private static final String CODE_SETTINGS = "!code/" + CODE_SETTINGS_NAME;
    private static final String ICON_SETTINGS = "!icon/" + ICON_SETTINGS_NAME;
    private static final String CODE_NON_EXISTING = "!code/non_existing";
    private static final String ICON_NON_EXISTING = "!icon/non_existing";

    private int mCodeSettings;
    private int mSettingsIconId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mCodesSet.setLanguage(Locale.ENGLISH.getLanguage());
        mCodeSettings = mCodesSet.getCode(CODE_SETTINGS_NAME);
        mSettingsIconId = KeyboardIconsSet.getIconId(ICON_SETTINGS_NAME);
    }

    private void assertParser(String message, String moreKeySpec, String expectedLabel,
            String expectedOutputText, int expectedIcon, int expectedCode) {
        final MoreKeySpec spec = new MoreKeySpec(moreKeySpec, mCodesSet);
        assertEquals(message + ": label:", expectedLabel, spec.mLabel);
        assertEquals(message + ": ouptputText:", expectedOutputText, spec.mOutputText);
        assertEquals(message + ": icon:", expectedIcon, spec.mIconId);
        assertEquals(message + ": codes value:", expectedCode, spec.mCode);
    }

    private void assertParserError(String message, String moreKeySpec, String expectedLabel,
            String expectedOutputText, int expectedIcon, int expectedCode) {
        try {
            assertParser(message, moreKeySpec, expectedLabel, expectedOutputText, expectedIcon,
                    expectedCode);
            fail(message);
        } catch (Exception pcpe) {
            // success.
        }
    }

    // \U001d11e: MUSICAL SYMBOL G CLEF
    private static final String PAIR1 = "\ud834\udd1e";
    private static final int CODE1 = PAIR1.codePointAt(0);
    // \U001d122: MUSICAL SYMBOL F CLEF
    private static final String PAIR2 = "\ud834\udd22";
    private static final int CODE2 = PAIR2.codePointAt(0);
    // \U002f8a6: CJK COMPATIBILITY IDEOGRAPH-2F8A6; variant character of \u6148.
    private static final String PAIR3 = "\ud87e\udca6";
    private static final String SURROGATE1 = PAIR1 + PAIR2;
    private static final String SURROGATE2 = PAIR1 + PAIR2 + PAIR3;

    public void testSingleLetter() {
        assertParser("Single letter", "a",
                "a", null, ICON_UNDEFINED, 'a');
        assertParser("Single surrogate", PAIR1,
                PAIR1, null, ICON_UNDEFINED, CODE1);
        assertParser("Single escaped bar", "\\|",
                "|", null, ICON_UNDEFINED, '|');
        assertParser("Single escaped escape", "\\\\",
                "\\", null, ICON_UNDEFINED, '\\');
        assertParser("Single comma", ",",
                ",", null, ICON_UNDEFINED, ',');
        assertParser("Single escaped comma", "\\,",
                ",", null, ICON_UNDEFINED, ',');
        assertParser("Single escaped letter", "\\a",
                "a", null, ICON_UNDEFINED, 'a');
        assertParser("Single escaped surrogate", "\\" + PAIR2,
                PAIR2, null, ICON_UNDEFINED, CODE2);
        assertParser("Single bang", "!",
                "!", null, ICON_UNDEFINED, '!');
        assertParser("Single escaped bang", "\\!",
                "!", null, ICON_UNDEFINED, '!');
        assertParser("Single output text letter", "a|a",
                "a", null, ICON_UNDEFINED, 'a');
        assertParser("Single surrogate pair outputText", "G Clef|" + PAIR1,
                "G Clef", null, ICON_UNDEFINED, CODE1);
        assertParser("Single letter with outputText", "a|abc",
                "a", "abc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with surrogate outputText", "a|" + SURROGATE1,
                "a", SURROGATE1, ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single surrogate with outputText", PAIR3 + "|abc",
                PAIR3, "abc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped outputText", "a|a\\|c",
                "a", "a|c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped surrogate outputText",
                "a|" + PAIR1 + "\\|" + PAIR2,
                "a", PAIR1 + "|" + PAIR2, ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with comma outputText", "a|a,b",
                "a", "a,b", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped comma outputText", "a|a\\,b",
                "a", "a,b", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with outputText starts with bang", "a|!bc",
                "a", "!bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with surrogate outputText starts with bang", "a|!" + SURROGATE2,
                "a", "!" + SURROGATE2, ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with outputText contains bang", "a|a!c",
                "a", "a!c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped bang outputText", "a|\\!bc",
                "a", "!bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single escaped escape with single outputText", "\\\\|\\\\",
                "\\", null, ICON_UNDEFINED, '\\');
        assertParser("Single escaped bar with single outputText", "\\||\\|",
                "|", null, ICON_UNDEFINED, '|');
        assertParser("Single letter with code", "a|" + CODE_SETTINGS,
                "a", null, ICON_UNDEFINED, mCodeSettings);
    }

    public void testLabel() {
        assertParser("Simple label", "abc",
                "abc", "abc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Simple surrogate label", SURROGATE1,
                SURROGATE1, SURROGATE1, ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bar", "a\\|c",
                "a|c", "a|c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Surrogate label with escaped bar", PAIR1 + "\\|" + PAIR2,
                PAIR1 + "|" + PAIR2, PAIR1 + "|" + PAIR2,
                ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped escape", "a\\\\c",
                "a\\c", "a\\c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with comma", "a,c",
                "a,c", "a,c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped comma", "a\\,c",
                "a,c", "a,c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label starts with bang", "!bc",
                "!bc", "!bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Surrogate label starts with bang", "!" + SURROGATE1,
                "!" + SURROGATE1, "!" + SURROGATE1, ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label contains bang", "a!c",
                "a!c", "a!c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bang", "\\!bc",
                "!bc", "!bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped letter", "\\abc",
                "abc", "abc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with outputText", "abc|def",
                "abc", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with comma and outputText", "a,c|def",
                "a,c", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Escaped comma label with outputText", "a\\,c|def",
                "a,c", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Escaped label with outputText", "a\\|c|def",
                "a|c", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bar outputText", "abc|d\\|f",
                "abc", "d|f", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Escaped escape label with outputText", "a\\\\|def",
                "a\\", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label starts with bang and outputText", "!bc|def",
                "!bc", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label contains bang label and outputText", "a!c|def",
                "a!c", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Escaped bang label with outputText", "\\!bc|def",
                "!bc", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with comma outputText", "abc|a,b",
                "abc", "a,b", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped comma outputText", "abc|a\\,b",
                "abc", "a,b", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with outputText starts with bang", "abc|!bc",
                "abc", "!bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with outputText contains bang", "abc|a!c",
                "abc", "a!c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bang outputText", "abc|\\!bc",
                "abc", "!bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bar outputText", "abc|d\\|f",
                "abc", "d|f", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Escaped bar label with escaped bar outputText", "a\\|c|d\\|f",
                "a|c", "d|f", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with code", "abc|" + CODE_SETTINGS,
                "abc", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Escaped label with code", "a\\|c|" + CODE_SETTINGS,
                "a|c", null, ICON_UNDEFINED, mCodeSettings);
    }

    public void testIconAndCode() {
        assertParser("Icon with outputText", ICON_SETTINGS + "|abc",
                null, "abc", mSettingsIconId, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Icon with outputText starts with bang", ICON_SETTINGS + "|!bc",
                null, "!bc", mSettingsIconId, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Icon with outputText contains bang", ICON_SETTINGS + "|a!c",
                null, "a!c", mSettingsIconId, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Icon with escaped bang outputText", ICON_SETTINGS + "|\\!bc",
                null, "!bc", mSettingsIconId, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label starts with bang and code", "!bc|" + CODE_SETTINGS,
                "!bc", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Label contains bang and code", "a!c|" + CODE_SETTINGS,
                "a!c", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Escaped bang label with code", "\\!bc|" + CODE_SETTINGS,
                "!bc", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Icon with code", ICON_SETTINGS + "|" + CODE_SETTINGS,
                null, null, mSettingsIconId, mCodeSettings);
    }

    public void testFormatError() {
        assertParserError("Empty spec", "", null,
                null, ICON_UNDEFINED, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Empty label with outputText", "|a",
                null, "a", ICON_UNDEFINED, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Empty label with code", "|" + CODE_SETTINGS,
                null, null, ICON_UNDEFINED, mCodeSettings);
        assertParserError("Empty outputText with label", "a|",
                "a", null, ICON_UNDEFINED, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Empty outputText with icon", ICON_SETTINGS + "|",
                null, null, mSettingsIconId, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Empty icon and code", "|",
                null, null, ICON_UNDEFINED, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Icon without code", ICON_SETTINGS,
                null, null, mSettingsIconId, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Non existing icon", ICON_NON_EXISTING + "|abc",
                null, "abc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParserError("Non existing code", "abc|" + CODE_NON_EXISTING,
                "abc", null, ICON_UNDEFINED, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Third bar at end", "a|b|",
                "a", null, ICON_UNDEFINED, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Multiple bar", "a|b|c",
                "a", null, ICON_UNDEFINED, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Multiple bar with label and code", "a|" + CODE_SETTINGS + "|c",
                "a", null, ICON_UNDEFINED, mCodeSettings);
        assertParserError("Multiple bar with icon and outputText", ICON_SETTINGS + "|b|c",
                null, null, mSettingsIconId, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Multiple bar with icon and code",
                ICON_SETTINGS + "|" + CODE_SETTINGS + "|c",
                null, null, mSettingsIconId, mCodeSettings);
    }

    private static void assertMoreKeys(String message, String[] moreKeys,
            String[] additionalMoreKeys, String[] expected) {
        final String[] actual = KeySpecParser.insertAddtionalMoreKeys(
                moreKeys, additionalMoreKeys);
        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || actual == null) {
            assertEquals(message, Arrays.toString(expected), Arrays.toString(actual));
        } else {
            if (expected.length != actual.length) {
                assertEquals(message, Arrays.toString(expected), Arrays.toString(actual));
            }
            for (int i = 0; i < expected.length; i++) {
                if (!actual[i].equals(expected[i])) {
                    assertEquals(message, Arrays.toString(expected), Arrays.toString(actual));
                }
            }
        }
    }

    public void testEmptyEntry() {
        assertMoreKeys("null more keys and null additons",
                null,
                null,
                null);
        assertMoreKeys("null more keys and empty additons",
                null,
                new String[0],
                null);
        assertMoreKeys("empty more keys and null additons",
                new String[0],
                null,
                null);
        assertMoreKeys("empty more keys and empty additons",
                new String[0],
                new String[0],
                null);

        assertMoreKeys("filter out empty more keys",
                new String[] { null, "a", "", "b", null },
                null,
                new String[] { "a", "b" });
        assertMoreKeys("filter out empty additons",
                new String[] { "a", "%", "b", "%", "c", "%", "d" },
                new String[] { null, "A", "", "B", null },
                new String[] { "a", "A", "b", "B", "c", "d" });
    }

    public void testInsertAdditionalMoreKeys() {
        // Escaped marker.
        assertMoreKeys("escaped marker",
                new String[] { "\\%", "%-)" },
                new String[] { "1", "2" },
                new String[] { "1", "2", "\\%", "%-)" });

        // 0 more key.
        assertMoreKeys("null & null", null, null, null);
        assertMoreKeys("null & 1 additon",
                null,
                new String[] { "1" },
                new String[] { "1" });
        assertMoreKeys("null & 2 additons",
                null,
                new String[] { "1", "2" },
                new String[] { "1", "2" });

        // 0 additional more key.
        assertMoreKeys("1 more key & null",
                new String[] { "A" },
                null,
                new String[] { "A" });
        assertMoreKeys("2 more keys & null",
                new String[] { "A", "B" },
                null,
                new String[] { "A", "B" });

        // No marker.
        assertMoreKeys("1 more key & 1 addtional & no marker",
                new String[] { "A" },
                new String[] { "1" },
                new String[] { "1", "A" });
        assertMoreKeys("1 more key & 2 addtionals & no marker",
                new String[] { "A" },
                new String[] { "1", "2" },
                new String[] { "1", "2", "A" });
        assertMoreKeys("2 more keys & 1 addtional & no marker",
                new String[] { "A", "B" },
                new String[] { "1" },
                new String[] { "1", "A", "B" });
        assertMoreKeys("2 more keys & 2 addtionals & no marker",
                new String[] { "A", "B" },
                new String[] { "1", "2" },
                new String[] { "1", "2", "A", "B" });

        // 1 marker.
        assertMoreKeys("1 more key & 1 additon & marker at head",
                new String[] { "%", "A" },
                new String[] { "1" },
                new String[] { "1", "A" });
        assertMoreKeys("1 more key & 1 additon & marker at tail",
                new String[] { "A", "%" },
                new String[] { "1" },
                new String[] { "A", "1" });
        assertMoreKeys("2 more keys & 1 additon & marker at middle",
                new String[] { "A", "%", "B" },
                new String[] { "1" },
                new String[] { "A", "1", "B" });

        // 1 marker & excess additional more keys.
        assertMoreKeys("1 more key & 2 additons & marker at head",
                new String[] { "%", "A", "B" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "B", "2" });
        assertMoreKeys("1 more key & 2 additons & marker at tail",
                new String[] { "A", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "A", "B", "1", "2" });
        assertMoreKeys("2 more keys & 2 additons & marker at middle",
                new String[] { "A", "%", "B" },
                new String[] { "1", "2" },
                new String[] { "A", "1", "B", "2" });

        // 2 markers.
        assertMoreKeys("0 more key & 2 addtional & 2 markers",
                new String[] { "%", "%" },
                new String[] { "1", "2" },
                new String[] { "1", "2" });
        assertMoreKeys("1 more key & 2 addtional & 2 markers at head",
                new String[] { "%", "%", "A" },
                new String[] { "1", "2" },
                new String[] { "1", "2", "A" });
        assertMoreKeys("1 more key & 2 addtional & 2 markers at tail",
                new String[] { "A", "%", "%" },
                new String[] { "1", "2" },
                new String[] { "A", "1", "2" });
        assertMoreKeys("2 more keys & 2 addtional & 2 markers at middle",
                new String[] { "A", "%", "%", "B" },
                new String[] { "1", "2" },
                new String[] { "A", "1", "2", "B" });
        assertMoreKeys("2 more keys & 2 addtional & 2 markers at head & middle",
                new String[] { "%", "A", "%", "B" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "2", "B" });
        assertMoreKeys("2 more keys & 2 addtional & 2 markers at head & tail",
                new String[] { "%", "A", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "B", "2" });
        assertMoreKeys("2 more keys & 2 addtional & 2 markers at middle & tail",
                new String[] { "A", "%", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "A", "1", "B", "2" });

        // 2 markers & excess additional more keys.
        assertMoreKeys("0 more key & 2 additons & 2 markers",
                new String[] { "%", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "2", "3" });
        assertMoreKeys("1 more key & 2 additons & 2 markers at head",
                new String[] { "%", "%", "A" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "2", "A", "3" });
        assertMoreKeys("1 more key & 2 additons & 2 markers at tail",
                new String[] { "A", "%", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "A", "1", "2", "3" });
        assertMoreKeys("2 more keys & 2 additons & 2 markers at middle",
                new String[] { "A", "%", "%", "B" },
                new String[] { "1", "2", "3" },
                new String[] { "A", "1", "2", "B", "3" });
        assertMoreKeys("2 more keys & 2 additons & 2 markers at head & middle",
                new String[] { "%", "A", "%", "B" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "A", "2", "B", "3" });
        assertMoreKeys("2 more keys & 2 additons & 2 markers at head & tail",
                new String[] { "%", "A", "B", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "A", "B", "2", "3" });
        assertMoreKeys("2 more keys & 2 additons & 2 markers at middle & tail",
                new String[] { "A", "%", "B", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "A", "1", "B", "2", "3" });

        // 0 addtional more key and excess markers.
        assertMoreKeys("0 more key & null & excess marker",
                new String[] { "%" },
                null,
                null);
        assertMoreKeys("1 more key & null & excess marker at head",
                new String[] { "%", "A" },
                null,
                new String[] { "A" });
        assertMoreKeys("1 more key & null & excess marker at tail",
                new String[] { "A", "%" },
                null,
                new String[] { "A" });
        assertMoreKeys("2 more keys & null & excess marker at middle",
                new String[] { "A", "%", "B" },
                null,
                new String[] { "A", "B" });
        assertMoreKeys("2 more keys & null & excess markers",
                new String[] { "%", "A", "%", "B", "%" },
                null,
                new String[] { "A", "B" });

        // Excess markers.
        assertMoreKeys("0 more key & 1 additon & excess marker",
                new String[] { "%", "%" },
                new String[] { "1" },
                new String[] { "1" });
        assertMoreKeys("1 more key & 1 additon & excess marker at head",
                new String[] { "%", "%", "A" },
                new String[] { "1" },
                new String[] { "1", "A" });
        assertMoreKeys("1 more key & 1 additon & excess marker at tail",
                new String[] { "A", "%", "%" },
                new String[] { "1" },
                new String[] { "A", "1" });
        assertMoreKeys("2 more keys & 1 additon & excess marker at middle",
                new String[] { "A", "%", "%", "B" },
                new String[] { "1" },
                new String[] { "A", "1", "B" });
        assertMoreKeys("2 more keys & 1 additon & excess markers",
                new String[] { "%", "A", "%", "B", "%" },
                new String[] { "1" },
                new String[] { "1", "A", "B" });
        assertMoreKeys("2 more keys & 2 additons & excess markers",
                new String[] { "%", "A", "%", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "2", "B" });
        assertMoreKeys("2 more keys & 3 additons & excess markers",
                new String[] { "%", "A", "%", "%", "B", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "A", "2", "3", "B" });
    }
}
