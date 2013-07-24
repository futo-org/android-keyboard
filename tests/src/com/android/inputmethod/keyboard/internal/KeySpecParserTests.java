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
import static com.android.inputmethod.latin.Constants.CODE_OUTPUT_TEXT;
import static com.android.inputmethod.latin.Constants.CODE_UNSPECIFIED;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.RunInLocale;

import java.util.Arrays;
import java.util.Locale;

@SmallTest
public class KeySpecParserTests extends AndroidTestCase {
    private final static Locale TEST_LOCALE = Locale.ENGLISH;
    final KeyboardCodesSet mCodesSet = new KeyboardCodesSet();
    final KeyboardTextsSet mTextsSet = new KeyboardTextsSet();

    private static final String CODE_SETTINGS = "!code/key_settings";
    private static final String ICON_SETTINGS = "!icon/settings_key";
    private static final String CODE_SETTINGS_UPPERCASE = CODE_SETTINGS.toUpperCase(Locale.ROOT);
    private static final String ICON_SETTINGS_UPPERCASE = ICON_SETTINGS.toUpperCase(Locale.ROOT);
    private static final String CODE_NON_EXISTING = "!code/non_existing";
    private static final String ICON_NON_EXISTING = "!icon/non_existing";

    private int mCodeSettings;
    private int mCodeActionNext;
    private int mSettingsIconId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final String language = TEST_LOCALE.getLanguage();
        mCodesSet.setLanguage(language);
        mTextsSet.setLanguage(language);
        final Context context = getContext();
        new RunInLocale<Void>() {
            @Override
            protected Void job(final Resources res) {
                mTextsSet.loadStringResources(context);
                return null;
            }
        }.runInLocale(context.getResources(), TEST_LOCALE);

        mCodeSettings = KeySpecParser.parseCode(
                CODE_SETTINGS, mCodesSet, CODE_UNSPECIFIED);
        mCodeActionNext = KeySpecParser.parseCode(
                "!code/key_action_next", mCodesSet, CODE_UNSPECIFIED);
        mSettingsIconId = KeySpecParser.getIconId(ICON_SETTINGS);
    }

    private void assertParser(String message, String moreKeySpec, String expectedLabel,
            String expectedOutputText, int expectedIcon, int expectedCode) {
        final String labelResolved = KeySpecParser.resolveTextReference(moreKeySpec, mTextsSet);
        final MoreKeySpec spec = new MoreKeySpec(labelResolved, false /* needsToUpperCase */,
                Locale.US, mCodesSet);
        assertEquals(message + " [label]", expectedLabel, spec.mLabel);
        assertEquals(message + " [ouptputText]", expectedOutputText, spec.mOutputText);
        assertEquals(message + " [icon]",
                KeyboardIconsSet.getIconName(expectedIcon),
                KeyboardIconsSet.getIconName(spec.mIconId));
        assertEquals(message + " [code]",
                Constants.printableCode(expectedCode),
                Constants.printableCode(spec.mCode));
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
                "a", "abc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with surrogate outputText", "a|" + SURROGATE1,
                "a", SURROGATE1, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single surrogate with outputText", PAIR3 + "|abc",
                PAIR3, "abc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped outputText", "a|a\\|c",
                "a", "a|c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped surrogate outputText",
                "a|" + PAIR1 + "\\|" + PAIR2,
                "a", PAIR1 + "|" + PAIR2, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with comma outputText", "a|a,b",
                "a", "a,b", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with escaped comma outputText", "a|a\\,b",
                "a", "a,b", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with outputText starts with bang", "a|!bc",
                "a", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Single letter with surrogate outputText starts with bang", "a|!" + SURROGATE2,
                "a", "!" + SURROGATE2, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
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
        assertParser("Simple surrogate label", SURROGATE1,
                SURROGATE1, SURROGATE1, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped bar", "a\\|c",
                "a|c", "a|c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Surrogate label with escaped bar", PAIR1 + "\\|" + PAIR2,
                PAIR1 + "|" + PAIR2, PAIR1 + "|" + PAIR2,
                ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped escape", "a\\\\c",
                "a\\c", "a\\c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with comma", "a,c",
                "a,c", "a,c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label with escaped comma", "a\\,c",
                "a,c", "a,c", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Label starts with bang", "!bc",
                "!bc", "!bc", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
        assertParser("Surrogate label starts with bang", "!" + SURROGATE1,
                "!" + SURROGATE1, "!" + SURROGATE1, ICON_UNDEFINED, CODE_OUTPUT_TEXT);
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

    public void testIconAndCode() {
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
        assertParser("Settings as more key", "!text/settings_as_more_key",
                null, null, mSettingsIconId, mCodeSettings);

        assertParser("Action next as more key", "!text/label_next_key|!code/key_action_next",
                "Next", null, ICON_UNDEFINED, mCodeActionNext);

        assertParser("Popular domain",
                "!text/keylabel_for_popular_domain|!text/keylabel_for_popular_domain ",
                ".com", ".com ", ICON_UNDEFINED, CODE_OUTPUT_TEXT);
    }

    public void testFormatError() {
        assertParserError("Empty spec", "", null,
                null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParserError("Empty label with outputText", "|a",
                null, "a", ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParserError("Empty label with code", "|" + CODE_SETTINGS,
                null, null, ICON_UNDEFINED, mCodeSettings);
        assertParserError("Empty outputText with label", "a|",
                "a", null, ICON_UNDEFINED, CODE_UNSPECIFIED);
        assertParserError("Empty outputText with icon", ICON_SETTINGS + "|",
                null, null, mSettingsIconId, CODE_UNSPECIFIED);
        assertParserError("Empty icon and code", "|",
                null, null, ICON_UNDEFINED, CODE_UNSPECIFIED);
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

    private static void assertInsertAdditionalMoreKeys(String message, String[] moreKeys,
            String[] additionalMoreKeys, String[] expected) {
        final String[] actual =
                KeySpecParser.insertAdditionalMoreKeys( moreKeys, additionalMoreKeys);
        assertArrayEquals(message, expected, actual);
    }

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

    private static void assertGetBooleanValue(String message, String key, String[] moreKeys,
            String[] expected, boolean expectedValue) {
        final String[] actual = Arrays.copyOf(moreKeys, moreKeys.length);
        final boolean actualValue = KeySpecParser.getBooleanValue(actual, key);
        assertEquals(message + " [value]", expectedValue, actualValue);
        assertArrayEquals(message, expected, actual);
    }

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

    private static void assertGetIntValue(String message, String key, int defaultValue,
            String[] moreKeys, String[] expected, int expectedValue) {
        final String[] actual = Arrays.copyOf(moreKeys, moreKeys.length);
        final int actualValue = KeySpecParser.getIntValue(actual, key, defaultValue);
        assertEquals(message + " [value]", expectedValue, actualValue);
        assertArrayEquals(message, expected, actual);
    }

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
