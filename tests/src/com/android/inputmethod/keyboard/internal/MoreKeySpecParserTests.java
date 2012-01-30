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

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.R;

import java.util.Arrays;

public class MoreKeySpecParserTests extends AndroidTestCase {
    private Resources mRes;

    private static final int ICON_SETTINGS_KEY = R.styleable.Keyboard_iconSettingsKey;
    private static final int ICON_UNDEFINED = KeyboardIconsSet.ICON_UNDEFINED;

    private static final String CODE_SETTINGS = "@integer/key_settings";
    private static final String ICON_SETTINGS = "@icon/settingsKey";
    private static final String CODE_NON_EXISTING = "@integer/non_existing";
    private static final String ICON_NON_EXISTING = "@icon/non_existing";

    private int mCodeSettings;

    @Override
    protected void setUp() {
        Resources res = getContext().getResources();
        mRes = res;

        final String packageName = res.getResourcePackageName(R.string.english_ime_name);
        final int codeId = res.getIdentifier(CODE_SETTINGS.substring(1), null, packageName);
        mCodeSettings = res.getInteger(codeId);
    }

    private void assertParser(String message, String moreKeySpec, String expectedLabel,
            String expectedOutputText, int expectedIcon, int expectedCode) {
        String actualLabel = MoreKeySpecParser.getLabel(moreKeySpec);
        assertEquals(message + ": label:", expectedLabel, actualLabel);

        String actualOutputText = MoreKeySpecParser.getOutputText(moreKeySpec);
        assertEquals(message + ": ouptputText:", expectedOutputText, actualOutputText);

        int actualIcon = MoreKeySpecParser.getIconAttrId(moreKeySpec);
        assertEquals(message + ": icon:", expectedIcon, actualIcon);

        int actualCode = MoreKeySpecParser.getCode(mRes, moreKeySpec);
        assertEquals(message + ": codes value:", expectedCode, actualCode);
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

    public void testSingleLetter() {
        assertParser("Single letter", "a",
                "a", null, ICON_UNDEFINED, 'a');
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
        assertParser("Single at", "@",
                "@", null, ICON_UNDEFINED, '@');
        assertParser("Single escaped at", "\\@",
                "@", null, ICON_UNDEFINED, '@');
        assertParser("Single letter with outputText", "a|abc",
                "a", "abc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped outputText", "a|a\\|c",
                "a", "a|c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with comma outputText", "a|a,b",
                "a", "a,b", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped comma outputText", "a|a\\,b",
                "a", "a,b", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with outputText starts with at", "a|@bc",
                "a", "@bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with outputText contains at", "a|a@c",
                "a", "a@c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped at outputText", "a|\\@bc",
                "a", "@bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single escaped escape with outputText", "\\\\|\\\\",
                "\\", "\\", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single escaped bar with outputText", "\\||\\|",
                "|", "|", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Single letter with code", "a|" + CODE_SETTINGS,
                "a", null, ICON_UNDEFINED, mCodeSettings);
    }

    public void testLabel() {
        assertParser("Simple label", "abc",
                "abc", "abc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bar", "a\\|c",
                "a|c", "a|c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped escape", "a\\\\c",
                "a\\c", "a\\c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with comma", "a,c",
                "a,c", "a,c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped comma", "a\\,c",
                "a,c", "a,c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label starts with at", "@bc",
                "@bc", "@bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label contains at", "a@c",
                "a@c", "a@c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped at", "\\@bc",
                "@bc", "@bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
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
        assertParser("Label starts with at and outputText", "@bc|def",
                "@bc", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label contains at label and outputText", "a@c|def",
                "a@c", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Escaped at label with outputText", "\\@bc|def",
                "@bc", "def", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with comma outputText", "abc|a,b",
                "abc", "a,b", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped comma outputText", "abc|a\\,b",
                "abc", "a,b", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with outputText starts with at", "abc|@bc",
                "abc", "@bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with outputText contains at", "abc|a@c",
                "abc", "a@c", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label with escaped at outputText", "abc|\\@bc",
                "abc", "@bc", ICON_UNDEFINED, Keyboard.CODE_OUTPUT_TEXT);
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
                null, "abc", ICON_SETTINGS_KEY, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Icon with outputText starts with at", ICON_SETTINGS + "|@bc",
                null, "@bc", ICON_SETTINGS_KEY, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Icon with outputText contains at", ICON_SETTINGS + "|a@c",
                null, "a@c", ICON_SETTINGS_KEY, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Icon with escaped at outputText", ICON_SETTINGS + "|\\@bc",
                null, "@bc", ICON_SETTINGS_KEY, Keyboard.CODE_OUTPUT_TEXT);
        assertParser("Label starts with at and code", "@bc|" + CODE_SETTINGS,
                "@bc", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Label contains at and code", "a@c|" + CODE_SETTINGS,
                "a@c", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Escaped at label with code", "\\@bc|" + CODE_SETTINGS,
                "@bc", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Icon with code", ICON_SETTINGS + "|" + CODE_SETTINGS,
                null, null, ICON_SETTINGS_KEY, mCodeSettings);
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
                null, null, ICON_SETTINGS_KEY, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Empty icon and code", "|",
                null, null, ICON_UNDEFINED, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Icon without code", ICON_SETTINGS,
                null, null, ICON_SETTINGS_KEY, Keyboard.CODE_UNSPECIFIED);
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
                null, null, ICON_SETTINGS_KEY, Keyboard.CODE_UNSPECIFIED);
        assertParserError("Multiple bar with icon and code",
                ICON_SETTINGS + "|" + CODE_SETTINGS + "|c",
                null, null, ICON_SETTINGS_KEY, mCodeSettings);
    }

    private void assertMoreKeys(String message, String[] moreKeys, String[] additionalMoreKeys,
            String[] expected) {
        final String[] actual = MoreKeySpecParser.insertAddtionalMoreKeys(
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

    public void testInsertAdditionalMoreKeys() {
        // Escaped marker.
        assertMoreKeys("escaped marker",
                new String[] { "\\%", "%-)" },
                new String[] { "1", "2" },
                new String[] { "1", "2", "\\%", "%-)" });

        // 0 more key.
        assertMoreKeys("null & null", null, null, null);
        assertMoreKeys("null & 1 additional",
                null,
                new String[] { "1" },
                new String[] { "1" });
        assertMoreKeys("null & 2 additionals",
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
        assertMoreKeys("1 more key & 1 additional & marker at head",
                new String[] { "%", "A" },
                new String[] { "1" },
                new String[] { "1", "A" });
        assertMoreKeys("1 more key & 1 additional & marker at tail",
                new String[] { "A", "%" },
                new String[] { "1" },
                new String[] { "A", "1" });
        assertMoreKeys("2 more keys & 1 additional & marker at middle",
                new String[] { "A", "%", "B" },
                new String[] { "1" },
                new String[] { "A", "1", "B" });

        // 1 marker & excess additional more keys.
        assertMoreKeys("1 more key & 2 additionals & marker at head",
                new String[] { "%", "A", "B" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "B", "2" });
        assertMoreKeys("1 more key & 2 additionals & marker at tail",
                new String[] { "A", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "A", "B", "1", "2" });
        assertMoreKeys("2 more keys & 2 additionals & marker at middle",
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

        // 2 markers & excess additional keys.
        assertMoreKeys("0 more key & 2 addtional & 2 markers",
                new String[] { "%", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "2", "3" });
        assertMoreKeys("1 more key & 2 addtional & 2 markers at head",
                new String[] { "%", "%", "A" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "2", "A", "3" });
        assertMoreKeys("1 more key & 2 addtional & 2 markers at tail",
                new String[] { "A", "%", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "A", "1", "2", "3" });
        assertMoreKeys("2 more keys & 2 addtional & 2 markers at middle",
                new String[] { "A", "%", "%", "B" },
                new String[] { "1", "2", "3" },
                new String[] { "A", "1", "2", "B", "3" });
        assertMoreKeys("2 more keys & 2 addtional & 2 markers at head & middle",
                new String[] { "%", "A", "%", "B" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "A", "2", "B", "3" });
        assertMoreKeys("2 more keys & 2 addtional & 2 markers at head & tail",
                new String[] { "%", "A", "B", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "A", "B", "2", "3" });
        assertMoreKeys("2 more keys & 2 addtional & 2 markers at middle & tail",
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
        assertMoreKeys("0 more key & 1 addtional & excess marker",
                new String[] { "%", "%" },
                new String[] { "1" },
                new String[] { "1" });
        assertMoreKeys("1 more key & 1 addtional & excess marker at head",
                new String[] { "%", "%", "A" },
                new String[] { "1" },
                new String[] { "1", "A" });
        assertMoreKeys("1 more key & 1 addtional & excess marker at tail",
                new String[] { "A", "%", "%" },
                new String[] { "1" },
                new String[] { "A", "1" });
        assertMoreKeys("2 more keys & 1 addtional & excess marker at middle",
                new String[] { "A", "%", "%", "B" },
                new String[] { "1" },
                new String[] { "A", "1", "B" });
        assertMoreKeys("2 more keys & 1 addtional & excess markers",
                new String[] { "%", "A", "%", "B", "%" },
                new String[] { "1" },
                new String[] { "1", "A", "B" });
        assertMoreKeys("2 more keys & 2 addtionals & excess markers",
                new String[] { "%", "A", "%", "B", "%" },
                new String[] { "1", "2" },
                new String[] { "1", "A", "2", "B" });
        assertMoreKeys("2 more keys & 3 addtionals & excess markers",
                new String[] { "%", "A", "%", "%", "B", "%" },
                new String[] { "1", "2", "3" },
                new String[] { "1", "A", "2", "3", "B" });
    }
}
