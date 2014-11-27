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

package com.android.inputmethod.keyboard.layout.tests;

import android.util.Log;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.keyboard.KeyboardLayoutSetTestsBase;
import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.expected.AbstractLayoutBase;
import com.android.inputmethod.keyboard.layout.expected.ActualKeyboardBuilder;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey.ExpectedAdditionalMoreKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Arrays;

/**
 * Base class for keyboard layout unit test.
 */
abstract class LayoutTestsBase extends KeyboardLayoutSetTestsBase {
    private LayoutBase mLayout;
    private InputMethodSubtype mSubtype;
    private String mLogTag;
    private KeyboardLayoutSet mKeyboardLayoutSet;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mLayout = getLayout();
        mSubtype = getSubtype(mLayout.getLocale(), mLayout.getName());
        mLogTag = SubtypeLocaleUtils.getSubtypeNameForLogging(mSubtype) + "/"
                + (isPhone() ? "phone" : "tablet");
        // TODO: Test with language switch key enabled and disabled.
        mKeyboardLayoutSet = createKeyboardLayoutSet(mSubtype, null /* editorInfo */,
                true /* voiceInputKeyEnabled */, true /* languageSwitchKeyEnabled */,
                false /* splitLayoutEnabled */);
    }

    @Override
    protected int getKeyboardThemeForTests() {
        return KeyboardTheme.THEME_ID_KLP;
    }

    // Those helper methods have a lower case name to be readable when defining expected keyboard
    // layouts.

    // Helper method to create an {@link ExpectedKey} object that has the label.
    static ExpectedKey key(final String label, final ExpectedKey ... moreKeys) {
        return AbstractLayoutBase.key(label, moreKeys);
    }

    // Helper method to create an {@link ExpectedKey} object that has the label and the output text.
    static ExpectedKey key(final String label, final String outputText,
            final ExpectedKey ... moreKeys) {
        return AbstractLayoutBase.key(label, outputText, moreKeys);
    }

    // Helper method to create an {@link ExpectedKey} object that has new "more keys".
    static ExpectedKey key(final ExpectedKey key, final ExpectedKey ... moreKeys) {
        return AbstractLayoutBase.key(key, moreKeys);
    }

    // Helper method to create an {@link ExpectedAdditionalMoreKey} object for an
    // "additional more key" that has the label.
    public static ExpectedAdditionalMoreKey additionalMoreKey(final String label) {
        return AbstractLayoutBase.additionalMoreKey(label);
    }

    // Helper method to create an {@link ExpectedKey} object for a "more key" that has the label.
    static ExpectedKey moreKey(final String label) {
        return AbstractLayoutBase.moreKey(label);
    }

    // Helper method to create an {@link ExpectedKey} object for a "more key" that has the label
    // and the output text.
    static ExpectedKey moreKey(final String label, final String outputText) {
        return AbstractLayoutBase.moreKey(label, outputText);
    }

    // Helper method to create {@link ExpectedKey} array by joining {@link ExpectedKey},
    // {@link ExpectedKey} array, and {@link String}.
    static ExpectedKey[] joinMoreKeys(final Object ... moreKeys) {
        return AbstractLayoutBase.joinKeys(moreKeys);
    }

    // Helper method to create {@link ExpectedKey} array by joining {@link ExpectedKey},
    // {@link ExpectedKey} array, and {@link String}.
    static ExpectedKey[] joinKeys(final Object ... keys) {
        return AbstractLayoutBase.joinKeys(keys);
    }

    // Keyboard layout for testing subtype.
    abstract LayoutBase getLayout();

    ExpectedKeyboardBuilder setAccentedLetters(final ExpectedKeyboardBuilder builder) {
        return builder;
    }

    // TODO: Add phone, phone symbols, number, number password layout tests.

    public final void testLayouts() {
        doKeyboardTests(KeyboardId.ELEMENT_ALPHABET);
        doKeyboardTests(KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED);
        doKeyboardTests(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED);
        doKeyboardTests(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED);
        doKeyboardTests(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED);
        doKeyboardTests(KeyboardId.ELEMENT_SYMBOLS);
        doKeyboardTests(KeyboardId.ELEMENT_SYMBOLS_SHIFTED);
    }

    // Comparing expected keyboard and actual keyboard.
    private void doKeyboardTests(final int elementId) {
        final ExpectedKey[][] expectedKeyboard = mLayout.getLayout(isPhone(), elementId);
        // Skip test if no keyboard is defined.
        if (expectedKeyboard == null) {
            return;
        }
        final String tag = mLogTag + "/" + KeyboardId.elementIdToName(elementId);
        // Create actual keyboard object.
        final Keyboard keyboard = mKeyboardLayoutSet.getKeyboard(elementId);
        // Create actual keyboard to be compared with the expected keyboard.
        final Key[][] actualKeyboard = ActualKeyboardBuilder.buildKeyboard(
                keyboard.getSortedKeys());

        // Dump human readable definition of expected/actual keyboards.
        Log.d(tag, "expected=\n" + ExpectedKeyboardBuilder.toString(expectedKeyboard));
        Log.d(tag, "actual  =\n" + ActualKeyboardBuilder.toString(actualKeyboard));
        // Test both keyboards have the same number of rows.
        assertEquals(tag + " labels"
                + "\nexpected=" + ExpectedKeyboardBuilder.toString(expectedKeyboard)
                + "\nactual  =" + ActualKeyboardBuilder.toString(actualKeyboard),
                expectedKeyboard.length, actualKeyboard.length);
        for (int r = 0; r < actualKeyboard.length; r++) {
            final int row = r + 1;
            // Test both keyboards' rows have the same number of columns.
            assertEquals(tag + " labels row=" + row
                    + "\nexpected=" + Arrays.toString(expectedKeyboard[r])
                    + "\nactual  =" + ActualKeyboardBuilder.toString(actualKeyboard[r]),
                    expectedKeyboard[r].length, actualKeyboard[r].length);
            for (int c = 0; c < actualKeyboard[r].length; c++) {
                final int column = c + 1;
                final Key actualKey = actualKeyboard[r][c];
                final ExpectedKey expectedKey = expectedKeyboard[r][c];
                // Test both keyboards' keys have the same visual outlook and key output.
                assertTrue(tag + " labels row,column=" + row + "," + column
                        + "\nexpected=" + expectedKey
                        + "\nactual  =" + ActualKeyboardBuilder.toString(actualKey),
                        expectedKey.equalsTo(actualKey));
            }
        }
    }
}
