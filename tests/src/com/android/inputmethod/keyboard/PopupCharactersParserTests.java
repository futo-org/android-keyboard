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

package com.android.inputmethod.keyboard;

import com.android.inputmethod.latin.R;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.test.AndroidTestCase;

public class PopupCharactersParserTests extends AndroidTestCase {
    private Resources mRes;

    private static final String CODE_SETTINGS = "@integer/key_settings";
    private static final String ICON_SETTINGS = "@drawable/sym_keyboard_settings";
    private static final String CODE_NON_EXISTING = "@integer/non_existing";
    private static final String ICON_NON_EXISTING = "@drawable/non_existing";

    private int mCodeSettings;
    private Drawable mIconSettings;

    private static final Integer[] DUMMY_CODES = { 0 };

    @Override
    protected void setUp() {
        Resources res = getContext().getResources();
        mRes = res;

        final String packageName = res.getResourcePackageName(R.string.english_ime_name);
        final int codeId = res.getIdentifier(CODE_SETTINGS.substring(1), null, packageName);
        final int iconId = res.getIdentifier(ICON_SETTINGS.substring(1), null, packageName);
        mCodeSettings = res.getInteger(codeId);
        mIconSettings = res.getDrawable(iconId);
    }

    private void assertParser(String message, String popupSpec, String expectedLabel,
            String expectedOutputText, Drawable expectedIcon, Integer ... expectedCodes) {
        String actualLabel = PopupCharactersParser.getLabel(popupSpec);
        assertEquals(message + ": label:", expectedLabel, actualLabel);

        String actualOutputText = PopupCharactersParser.getOutputText(popupSpec);
        assertEquals(message + ": ouptputText:", expectedOutputText, actualOutputText);

        Drawable actualIcon = PopupCharactersParser.getIcon(mRes, popupSpec);
        // We can not compare drawables, checking null or non-null instead.
        if (expectedIcon == null) {
            assertNull(message + ": icon null:", actualIcon);
        } else {
            assertNotNull(message + ": icon non-null:", actualIcon);
        }

        int[] actualCodes = PopupCharactersParser.getCodes(mRes, popupSpec);
        if (expectedCodes == null) {
            assertNull(message + ": codes null:", actualCodes);
            return;
        }
        assertSame(message + ": codes length:", expectedCodes.length, actualCodes.length);
        for (int i = 0; i < actualCodes.length; i++) {
            assertEquals(message + ": codes value at " + i + ":", (int)expectedCodes[i],
                    actualCodes[i]);
        }
    }

    private void assertParserError(String message, String popupSpec, String expectedLabel,
            String expectedOutputText, Drawable expectedIcon, Integer ... expectedCodes) {
        try {
            if (expectedCodes.length > 0) {
                assertParser(message, popupSpec, expectedLabel, expectedOutputText, expectedIcon,
                        expectedCodes);
            } else {
                assertParser(message, popupSpec, expectedLabel, expectedOutputText, expectedIcon,
                        DUMMY_CODES);
            }
            fail(message);
        } catch (PopupCharactersParser.PopupCharactersParserError pcpe) {
            // success.
        }
    }

    public void testSingleLetter() {
        assertParser("Single letter", "a", "a", null, null, (int)'a');
        assertParser("Single escaped bar", "\\|", "|", null, null, (int)'|');
        assertParser("Single escaped escape", "\\\\", "\\", null, null, (int)'\\');
        assertParser("Single comma", ",", ",", null, null, (int)',');
        assertParser("Single escaped comma", "\\,", ",", null, null, (int)',');
        assertParser("Single escaped letter", "\\a", "a", null, null, (int)'a');
        assertParser("Single at", "@", "@", null, null, (int)'@');
        assertParser("Single escaped at", "\\@", "@", null, null, (int)'@');
        assertParser("Single letter with outputText", "a|abc", "a", "abc", null, DUMMY_CODES);
        assertParser("Single letter with escaped outputText", "a|a\\|c", "a", "a|c", null,
                DUMMY_CODES);
        assertParser("Single letter with comma outputText", "a|a,b", "a", "a,b", null, DUMMY_CODES);
        assertParser("Single letter with escaped comma outputText", "a|a\\,b", "a", "a,b", null,
                DUMMY_CODES);
        assertParser("Single letter with outputText starts with at", "a|@bc", "a", "@bc", null,
                DUMMY_CODES);
        assertParser("Single letter with outputText contains at", "a|a@c", "a", "a@c", null,
                DUMMY_CODES);
        assertParser("Single letter with escaped at outputText", "a|\\@bc", "a", "@bc", null,
                DUMMY_CODES);
        assertParser("Single escaped escape with outputText", "\\\\|\\\\", "\\", "\\", null,
                DUMMY_CODES);
        assertParser("Single escaped bar with outputText", "\\||\\|", "|", "|", null, DUMMY_CODES);
        assertParser("Single letter with code", "a|" + CODE_SETTINGS, "a", null, null,
                mCodeSettings);
    }

    public void testLabel() {
        assertParser("Simple label", "abc", "abc", "abc", null, DUMMY_CODES);
        assertParser("Label with escaped bar", "a\\|c", "a|c", "a|c", null, DUMMY_CODES);
        assertParser("Label with escaped escape", "a\\\\c", "a\\c", "a\\c", null, DUMMY_CODES);
        assertParser("Label with comma", "a,c", "a,c", "a,c", null, DUMMY_CODES);
        assertParser("Label with escaped comma", "a\\,c", "a,c", "a,c", null, DUMMY_CODES);
        assertParser("Label starts with at", "@bc", "@bc", "@bc", null, DUMMY_CODES);
        assertParser("Label contains at", "a@c", "a@c", "a@c", null, DUMMY_CODES);
        assertParser("Label with escaped at", "\\@bc", "@bc", "@bc", null, DUMMY_CODES);
        assertParser("Label with escaped letter", "\\abc", "abc", "abc", null, DUMMY_CODES);
        assertParser("Label with outputText", "abc|def", "abc", "def", null, DUMMY_CODES);
        assertParser("Label with comma and outputText", "a,c|def", "a,c", "def", null, DUMMY_CODES);
        assertParser("Escaped comma label with outputText", "a\\,c|def", "a,c", "def", null,
                DUMMY_CODES);
        assertParser("Escaped label with outputText", "a\\|c|def", "a|c", "def", null, DUMMY_CODES);
        assertParser("Label with escaped bar outputText", "abc|d\\|f", "abc", "d|f", null,
                DUMMY_CODES);
        assertParser("Escaped escape label with outputText", "a\\\\|def", "a\\", "def", null,
                DUMMY_CODES);
        assertParser("Label starts with at and outputText", "@bc|def", "@bc", "def", null,
                DUMMY_CODES);
        assertParser("Label contains at label and outputText", "a@c|def", "a@c", "def", null,
                DUMMY_CODES);
        assertParser("Escaped at label with outputText", "\\@bc|def", "@bc", "def", null,
                DUMMY_CODES);
        assertParser("Label with comma outputText", "abc|a,b", "abc", "a,b", null, DUMMY_CODES);
        assertParser("Label with escaped comma outputText", "abc|a\\,b", "abc", "a,b", null,
                DUMMY_CODES);
        assertParser("Label with outputText starts with at", "abc|@bc", "abc", "@bc", null,
                DUMMY_CODES);
        assertParser("Label with outputText contains at", "abc|a@c", "abc", "a@c", null,
                DUMMY_CODES);
        assertParser("Label with escaped at outputText", "abc|\\@bc", "abc", "@bc", null,
                DUMMY_CODES);
        assertParser("Label with escaped bar outputText", "abc|d\\|f", "abc", "d|f",
                null, DUMMY_CODES);
        assertParser("Escaped bar label with escaped bar outputText", "a\\|c|d\\|f", "a|c", "d|f",
                null, DUMMY_CODES);
        assertParser("Label with code", "abc|" + CODE_SETTINGS, "abc", null, null, mCodeSettings);
        assertParser("Escaped label with code", "a\\|c|" + CODE_SETTINGS, "a|c", null, null,
                mCodeSettings);
    }

    public void testIconAndCode() {
        assertParser("Icon with outputText", ICON_SETTINGS + "|abc", null, "abc", mIconSettings,
                DUMMY_CODES);
        assertParser("Icon with outputText starts with at", ICON_SETTINGS + "|@bc", null, "@bc",
                mIconSettings, DUMMY_CODES);
        assertParser("Icon with outputText contains at", ICON_SETTINGS + "|a@c", null, "a@c",
                mIconSettings, DUMMY_CODES);
        assertParser("Icon with escaped at outputText", ICON_SETTINGS + "|\\@bc", null, "@bc",
                mIconSettings, DUMMY_CODES);
        assertParser("Label starts with at and code", "@bc|" + CODE_SETTINGS, "@bc", null, null,
                mCodeSettings);
        assertParser("Label contains at and code", "a@c|" + CODE_SETTINGS, "a@c", null, null,
                mCodeSettings);
        assertParser("Escaped at label with code", "\\@bc|" + CODE_SETTINGS, "@bc", null, null,
                mCodeSettings);
        assertParser("Icon with code", ICON_SETTINGS + "|" + CODE_SETTINGS, null, null,
                mIconSettings, mCodeSettings);
    }

    public void testFormatError() {
        assertParserError("Empty spec", "", null, null, null);
        assertParserError("Empty label with outputText", "|a", null, "a", null);
        assertParserError("Empty label with code", "|" + CODE_SETTINGS, null, null, null,
                mCodeSettings);
        assertParserError("Empty outputText with label", "a|", "a", null, null);
        assertParserError("Empty outputText with icon", ICON_SETTINGS + "|", null, null,
                mIconSettings);
        assertParserError("Empty icon and code", "|", null, null, null);
        assertParserError("Icon without code", ICON_SETTINGS, null, null, mIconSettings);
        assertParserError("Non existing icon", ICON_NON_EXISTING + "|abc", null, "abc", null);
        assertParserError("Non existing code", "abc|" + CODE_NON_EXISTING, "abc", null, null);
        assertParserError("Third bar at end", "a|b|", "a", null, null);
        assertParserError("Multiple bar", "a|b|c", "a", null, null);
        assertParserError("Multiple bar with label and code", "a|" + CODE_SETTINGS + "|c", "a",
                null, null, mCodeSettings);
        assertParserError("Multiple bar with icon and outputText", ICON_SETTINGS + "|b|c", null,
                null, mIconSettings);
        assertParserError("Multiple bar with icon and code",
                ICON_SETTINGS + "|" + CODE_SETTINGS + "|c", null, null, mIconSettings,
                mCodeSettings);
    }
}
