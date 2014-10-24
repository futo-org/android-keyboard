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

import static com.android.inputmethod.keyboard.internal.KeyboardCodesSet.PREFIX_CODE;
import static com.android.inputmethod.keyboard.internal.KeyboardIconsSet.ICON_UNDEFINED;
import static com.android.inputmethod.keyboard.internal.KeyboardIconsSet.PREFIX_ICON;
import static com.android.inputmethod.latin.common.Constants.CODE_OUTPUT_TEXT;
import static com.android.inputmethod.latin.common.Constants.CODE_UNSPECIFIED;

import android.test.AndroidTestCase;

import java.util.Locale;

abstract class KeySpecParserTestsBase extends AndroidTestCase {
    private final static Locale TEST_LOCALE = Locale.ENGLISH;
    protected final KeyboardTextsSet mTextsSet = new KeyboardTextsSet();

    private static final String CODE_SETTINGS_NAME = "key_settings";
    private static final String CODE_SETTINGS = PREFIX_CODE + CODE_SETTINGS_NAME;
    private static final String ICON_SETTINGS_NAME = "settings_key";
    private static final String ICON_SETTINGS = PREFIX_ICON + ICON_SETTINGS_NAME;
    private static final String CODE_SETTINGS_UPPERCASE = CODE_SETTINGS.toUpperCase(Locale.ROOT);
    private static final String ICON_SETTINGS_UPPERCASE = ICON_SETTINGS.toUpperCase(Locale.ROOT);
    private static final String CODE_NON_EXISTING = PREFIX_CODE + "non_existing";
    private static final String ICON_NON_EXISTING = PREFIX_ICON + "non_existing";

    private int mCodeSettings;
    private int mCodeActionNext;
    private int mSettingsIconId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTextsSet.setLocale(TEST_LOCALE, getContext());
        mCodeSettings = KeyboardCodesSet.getCode(CODE_SETTINGS_NAME);
        mCodeActionNext = KeyboardCodesSet.getCode("key_action_next");
        mSettingsIconId = KeyboardIconsSet.getIconId(ICON_SETTINGS_NAME);
    }

    abstract protected void assertParser(final String message, final String keySpec,
            final String expectedLabel, final String expectedOutputText, final int expectedIcon,
            final int expectedCode);

    protected void assertParserError(final String message, final String keySpec,
            final String expectedLabel, final String expectedOutputText, final int expectedIconId,
            final int expectedCode) {
        try {
            assertParser(message, keySpec, expectedLabel, expectedOutputText, expectedIconId,
                    expectedCode);
            fail(message);
        } catch (Exception pcpe) {
            // success.
        }
    }

    // \U001d11e: MUSICAL SYMBOL G CLEF
    private static final String SURROGATE_PAIR1 = "\ud834\udd1e";
    private static final int SURROGATE_CODE1 = SURROGATE_PAIR1.codePointAt(0);
    // \U001d122: MUSICAL SYMBOL F CLEF
    private static final String SURROGATE_PAIR2 = "\ud834\udd22";
    private static final int SURROGATE_CODE2 = SURROGATE_PAIR2.codePointAt(0);
    // \U002f8a6: CJK COMPATIBILITY IDEOGRAPH-2F8A6; variant character of \u6148.
    private static final String SURROGATE_PAIR3 = "\ud87e\udca6";
    private static final String SURROGATE_PAIRS4 = SURROGATE_PAIR1 + SURROGATE_PAIR2;
    private static final String SURROGATE_PAIRS5 = SURROGATE_PAIRS4 + SURROGATE_PAIR3;

    public void testSingleLetter() {
        assertParser("Single letter", "a",
                "a", null, ICON_UNDEFINED, 'a');
        assertParser("Single surrogate", SURROGATE_PAIR1,
                SURROGATE_PAIR1, null, ICON_UNDEFINED, SURROGATE_CODE1);
        assertParser("Sole vertical bar", "|",
                "|", null, ICON_UNDEFINED, '|');
        assertParser("Single escaped vertical bar", "\\|",
                "|", null, ICON_UNDEFINED, '|');
        assertParser("Single escaped escape", "\\\\",
                "\\", null, ICON_UNDEFINED, '\\');
        assertParser("Single comma", ",",
                ",", null, ICON_UNDEFINED, ',');
        assertParser("Single escaped comma", "\\,",
                ",", null, ICON_UNDEFINED, ',');
        assertParser("Single escaped letter", "\\a",
                "a", null, ICON_UNDEFINED, 'a');
        assertParser("Single escaped surrogate", "\\" + SURROGATE_PAIR2,
                SURROGATE_PAIR2, null, ICON_UNDEFINED, SURROGATE_CODE2);
        assertParser("Single bang", "!",
                "!", null, ICON_UNDEFINED, '!');
        assertParser("Single escaped bang", "\\!",
                "!", null, ICON_UNDEFINED, '!');
        assertParser("Single output text letter", "a|a",
                "a", null, ICON_UNDEFINED, 'a');
        assertParser("Single surrogate pair outputText", "G Clef|" + SURROGATE_PAIR1,
                "G Clef", null, ICON_UNDEFINED, SURROGATE_CODE1);
        assertParser("Single letter with outputText", "a|abc",
                "a", "abc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with surrogate outputText", "a|" + SURROGATE_PAIRS4,
                "a", SURROGATE_PAIRS4, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single surrogate with outputText", SURROGATE_PAIR3 + "|abc",
                SURROGATE_PAIR3, "abc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped outputText", "a|a\\|c",
                "a", "a|c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped surrogate outputText",
                "a|" + SURROGATE_PAIR1 + "\\|" + SURROGATE_PAIR2,
                "a", SURROGATE_PAIR1 + "|" + SURROGATE_PAIR2, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with comma outputText", "a|a,b",
                "a", "a,b", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped comma outputText", "a|a\\,b",
                "a", "a,b", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with outputText starts with bang", "a|!bc",
                "a", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with surrogate outputText starts with bang",
                "a|!" + SURROGATE_PAIRS5,
                "a", "!" + SURROGATE_PAIRS5, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with outputText contains bang", "a|a!c",
                "a", "a!c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped bang outputText", "a|\\!bc",
                "a", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single escaped escape with single outputText", "\\\\|\\\\",
                "\\", null, ICON_UNDEFINED, '\\');
        assertParser("Single escaped bar with single outputText", "\\||\\|",
                "|", null, ICON_UNDEFINED, '|');
        assertParser("Single letter with code", "a|" + CODE_SETTINGS,
                "a", null, ICON_UNDEFINED, mCodeSettings);
    }

    public void testLabel() {
        assertParser("Simple label", "abc",
                "abc", "abc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Simple surrogate label", SURROGATE_PAIRS4,
                SURROGATE_PAIRS4, SURROGATE_PAIRS4, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bar", "a\\|c",
                "a|c", "a|c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Surrogate label with escaped bar", SURROGATE_PAIR1 + "\\|" + SURROGATE_PAIR2,
                SURROGATE_PAIR1 + "|" + SURROGATE_PAIR2, SURROGATE_PAIR1 + "|" + SURROGATE_PAIR2,
                ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped escape", "a\\\\c",
                "a\\c", "a\\c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with comma", "a,c",
                "a,c", "a,c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped comma", "a\\,c",
                "a,c", "a,c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label starts with bang", "!bc",
                "!bc", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Surrogate label starts with bang", "!" + SURROGATE_PAIRS4,
                "!" + SURROGATE_PAIRS4, "!" + SURROGATE_PAIRS4, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label contains bang", "a!c",
                "a!c", "a!c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bang", "\\!bc",
                "!bc", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped letter", "\\abc",
                "abc", "abc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with outputText", "abc|def",
                "abc", "def", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with comma and outputText", "a,c|def",
                "a,c", "def", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Escaped comma label with outputText", "a\\,c|def",
                "a,c", "def", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Escaped label with outputText", "a\\|c|def",
                "a|c", "def", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bar outputText", "abc|d\\|f",
                "abc", "d|f", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Escaped escape label with outputText", "a\\\\|def",
                "a\\", "def", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label starts with bang and outputText", "!bc|def",
                "!bc", "def", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label contains bang label and outputText", "a!c|def",
                "a!c", "def", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Escaped bang label with outputText", "\\!bc|def",
                "!bc", "def", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with comma outputText", "abc|a,b",
                "abc", "a,b", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped comma outputText", "abc|a\\,b",
                "abc", "a,b", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with outputText starts with bang", "abc|!bc",
                "abc", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with outputText contains bang", "abc|a!c",
                "abc", "a!c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bang outputText", "abc|\\!bc",
                "abc", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bar outputText", "abc|d\\|f",
                "abc", "d|f", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Escaped bar label with escaped bar outputText", "a\\|c|d\\|f",
                "a|c", "d|f", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with code", "abc|" + CODE_SETTINGS,
                "abc", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Escaped label with code", "a\\|c|" + CODE_SETTINGS,
                "a|c", null, ICON_UNDEFINED, mCodeSettings);
    }

    public void testCodes() {
        assertParser("Hexadecimal code", "a|0x1000",
                "a", null, ICON_UNDEFINED, 0x1000);
        assertParserError("Illegal hexadecimal code", "a|0x100X",
                "a", null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParser("Escaped hexadecimal code 1", "a|\\0x1000",
                "a", "0x1000", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Escaped hexadecimal code 2", "a|0\\x1000",
                "a", "0x1000", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Escaped hexadecimal code 2", "a|0\\x1000",
                "a", "0x1000", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParserError("Illegally escaped hexadecimal code", "a|0x1\\000",
                "a", null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        // This is a workaround to have a key that has a supplementary code point. We can't put a
        // string in resource as a XML entity of a supplementary code point or a surrogate pair.
        // TODO: Should pass this test.
//        assertParser("Hexadecimal supplementary code", String.format("a|0x%06x", SURROGATE_CODE2),
//                SURROGATE_PAIR2, null, ICON_UNDEFINED, SURROGATE_CODE2);
        assertParser("Zero is treated as output text", "a|0",
                "a", null, ICON_UNDEFINED, '0');
        assertParser("Digit is treated as output text", "a|3",
                "a", null, ICON_UNDEFINED, '3');
        assertParser("Decimal number is treated as an output text", "a|2014",
                "a", "2014", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
    }

    public void testIcons() {
        assertParser("Icon with single letter", ICON_SETTINGS + "|a",
                null, null, mSettingsIconId, 'a');
        assertParser("Icon with outputText", ICON_SETTINGS + "|abc",
                null, "abc", mSettingsIconId, CODE_OUTPUT_TEXT);
        assertParser("Icon with outputText starts with bang", ICON_SETTINGS + "|!bc",
                null, "!bc", mSettingsIconId, CODE_OUTPUT_TEXT);
        assertParser("Icon with outputText contains bang", ICON_SETTINGS + "|a!c",
                null, "a!c", mSettingsIconId, CODE_OUTPUT_TEXT);
        assertParser("Icon with escaped bang outputText", ICON_SETTINGS + "|\\!bc",
                null, "!bc", mSettingsIconId, CODE_OUTPUT_TEXT);
        assertParser("Label starts with bang and code", "!bc|" + CODE_SETTINGS,
                "!bc", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Label contains bang and code", "a!c|" + CODE_SETTINGS,
                "a!c", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Escaped bang label with code", "\\!bc|" + CODE_SETTINGS,
                "!bc", null, ICON_UNDEFINED, mCodeSettings);
        assertParser("Icon with code", ICON_SETTINGS + "|" + CODE_SETTINGS,
                null, null, mSettingsIconId, mCodeSettings);
    }

    public void testResourceReference() {
        assertParser("Settings as more key", "!text/keyspec_settings",
                null, null, mSettingsIconId, mCodeSettings);

        assertParser("Action next as more key", "!text/label_next_key|!code/key_action_next",
                "Next", null, ICON_UNDEFINED, mCodeActionNext);

        assertParser("Popular domain",
                "!text/keyspec_popular_domain|!text/keyspec_popular_domain ",
                ".com", ".com ", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
    }

    public void testFormatError() {
        assertParserError("Empty label with outputText", "|a",
                null, "a", ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParserError("Empty label with code", "|" + CODE_SETTINGS,
                null, null, ICON_UNDEFINED, mCodeSettings);
        assertParserError("Empty outputText with label", "a|",
                "a", null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParserError("Empty outputText with icon", ICON_SETTINGS + "|",
                null, null, mSettingsIconId, CODE_UNSPECIFIED);
        assertParserError("Icon without code", ICON_SETTINGS,
                null, null, mSettingsIconId, CODE_UNSPECIFIED);
        assertParserError("Non existing icon", ICON_NON_EXISTING + "|abc",
                null, "abc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParserError("Non existing code", "abc|" + CODE_NON_EXISTING,
                "abc", null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParserError("Third bar at end", "a|b|",
                "a", null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParserError("Multiple bar", "a|b|c",
                "a", null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParserError("Multiple bar with label and code", "a|" + CODE_SETTINGS + "|c",
                "a", null, ICON_UNDEFINED, mCodeSettings);
        assertParserError("Multiple bar with icon and outputText", ICON_SETTINGS + "|b|c",
                null, null, mSettingsIconId, CODE_UNSPECIFIED);
        assertParserError("Multiple bar with icon and code",
                ICON_SETTINGS + "|" + CODE_SETTINGS + "|c",
                null, null, mSettingsIconId, mCodeSettings);
    }

    public void testUselessUpperCaseSpecifier() {
        assertParser("Single letter with CODE", "a|" + CODE_SETTINGS_UPPERCASE,
                "a", "!CODE/KEY_SETTINGS", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with CODE", "abc|" + CODE_SETTINGS_UPPERCASE,
                "abc", "!CODE/KEY_SETTINGS", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Escaped label with CODE", "a\\|c|" + CODE_SETTINGS_UPPERCASE,
                "a|c", "!CODE/KEY_SETTINGS", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("ICON with outputText", ICON_SETTINGS_UPPERCASE + "|abc",
                "!ICON/SETTINGS_KEY", "abc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("ICON with outputText starts with bang", ICON_SETTINGS_UPPERCASE + "|!bc",
                "!ICON/SETTINGS_KEY", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("ICON with outputText contains bang", ICON_SETTINGS_UPPERCASE + "|a!c",
                "!ICON/SETTINGS_KEY", "a!c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("ICON with escaped bang outputText", ICON_SETTINGS_UPPERCASE + "|\\!bc",
                "!ICON/SETTINGS_KEY", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label starts with bang and CODE", "!bc|" + CODE_SETTINGS_UPPERCASE,
                "!bc", "!CODE/KEY_SETTINGS", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label contains bang and CODE", "a!c|" + CODE_SETTINGS_UPPERCASE,
                "a!c", "!CODE/KEY_SETTINGS", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Escaped bang label with CODE", "\\!bc|" + CODE_SETTINGS_UPPERCASE,
                "!bc", "!CODE/KEY_SETTINGS", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("ICON with CODE", ICON_SETTINGS_UPPERCASE + "|" + CODE_SETTINGS_UPPERCASE,
                "!ICON/SETTINGS_KEY", "!CODE/KEY_SETTINGS", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("SETTINGS AS MORE KEY", "!TEXT/SETTINGS_AS_MORE_KEY",
                "!TEXT/SETTINGS_AS_MORE_KEY", "!TEXT/SETTINGS_AS_MORE_KEY", ICON_UNDEFINED,
                CODE_OUTPUT_TEXT);
        assertParser("ACTION NEXT AS MORE KEY", "!TEXT/LABEL_NEXT_KEY|!CODE/KEY_ACTION_NEXT",
                "!TEXT/LABEL_NEXT_KEY", "!CODE/KEY_ACTION_NEXT", ICON_UNDEFINED,
                CODE_OUTPUT_TEXT);
        assertParser("POPULAR DOMAIN",
                "!TEXT/KEYLABEL_FOR_POPULAR_DOMAIN|!TEXT/KEYLABEL_FOR_POPULAR_DOMAIN ",
                "!TEXT/KEYLABEL_FOR_POPULAR_DOMAIN", "!TEXT/KEYLABEL_FOR_POPULAR_DOMAIN ",
                ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParserError("Empty label with CODE", "|" + CODE_SETTINGS_UPPERCASE,
                null, null, ICON_UNDEFINED, mCodeSettings);
        assertParserError("Empty outputText with ICON", ICON_SETTINGS_UPPERCASE + "|",
                null, null, mSettingsIconId, CODE_UNSPECIFIED);
        assertParser("ICON without code", ICON_SETTINGS_UPPERCASE,
                "!ICON/SETTINGS_KEY", "!ICON/SETTINGS_KEY", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParserError("Multiple bar with label and CODE", "a|" + CODE_SETTINGS_UPPERCASE + "|c",
                "a", null, ICON_UNDEFINED, mCodeSettings);
        assertParserError("Multiple bar with ICON and outputText", ICON_SETTINGS_UPPERCASE + "|b|c",
                null, null, mSettingsIconId, CODE_UNSPECIFIED);
        assertParserError("Multiple bar with ICON and CODE",
                ICON_SETTINGS_UPPERCASE + "|" + CODE_SETTINGS_UPPERCASE + "|c",
                null, null, mSettingsIconId, mCodeSettings);
    }
}
