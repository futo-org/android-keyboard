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
import com.android.inputmethod.keyboard.layout.AlphabetShifted;
import com.android.inputmethod.keyboard.layout.Symbols;
import com.android.inputmethod.keyboard.layout.SymbolsShifted;
import com.android.inputmethod.keyboard.layout.expected.ActualKeyboardBuilder;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;
import com.android.inputmethod.keyboard.layout.expected.LayoutBase;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * Base class for keyboard layout unit test.
 */
abstract class LayoutTestsBase extends KeyboardLayoutSetTestsBase {
    private InputMethodSubtype mSubtype;
    private String mLogTag;
    private KeyboardLayoutSet mKeyboardLayoutSet;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSubtype = getSubtype(getTestLocale(), getTestKeyboardLayout());
        mLogTag = SubtypeLocaleUtils.getSubtypeNameForLogging(mSubtype) + "/"
                + (isPhone() ? "phone" : "tablet");
        mKeyboardLayoutSet = createKeyboardLayoutSet(mSubtype, null /* editorInfo */);
    }

    // Those helper methods have a lower case name to be readable when defining expected keyboard
    // layouts.

    // Helper method to create {@link ExpectedKey} object that has the label.
    static ExpectedKey key(final String label, final ExpectedKey ... moreKeys) {
        return LayoutBase.key(label, moreKeys);
    }

    // Helper method to create {@link ExpectedKey} object that has the label and the output text.
    static ExpectedKey key(final String label, final String outputText,
            final ExpectedKey ... moreKeys) {
        return LayoutBase.key(label, outputText, moreKeys);
    }

    // Helper method to create {@link ExpectedKey} object that has new "more keys".
    static ExpectedKey key(final ExpectedKey key, final ExpectedKey ... moreKeys) {
        return LayoutBase.key(key, moreKeys);
    }

    // Helper method to create {@link ExpectedKey} object for "more key" that has the label.
    static ExpectedKey moreKey(final String label) {
        return LayoutBase.moreKey(label);
    }

    // Helper method to create {@link ExpectedKey} object for "more key" that has the label and the
    // output text.
    static ExpectedKey moreKey(final String label, final String outputText) {
        return LayoutBase.moreKey(label, outputText);
    }

    // Helper method to create {@link ExpectedKey} array by joining {@link ExpectedKey},
    // {@link ExpectedKey} array, and {@link String}.
    static ExpectedKey[] join(final Object ... keys) {
        return LayoutBase.join(keys);
    }

    // Locale for testing subtype.
    abstract Locale getTestLocale();

    // Keyboard layout name for testing subtype.
    abstract String getTestKeyboardLayout();

    // Alphabet keyboard for testing subtype.
    abstract ExpectedKey[][] getAlphabetLayout(final boolean isPhone);

    // Alphabet automatic shifted keyboard for testing subtype.
    ExpectedKey[][] getAlphabetAutomaticShiftedLayout(final boolean isPhone) {
        return AlphabetShifted.getDefaultLayout(getAlphabetLayout(isPhone), getTestLocale());
    }

    // Alphabet manual shifted  keyboard for testing subtype.
    ExpectedKey[][] getAlphabetManualShiftedLayout(final boolean isPhone) {
        return AlphabetShifted.getDefaultLayout(getAlphabetLayout(isPhone), getTestLocale());
    }

    // Alphabet shift locked keyboard for testing subtype.
    ExpectedKey[][] getAlphabetShiftLockedLayout(final boolean isPhone) {
        return AlphabetShifted.getDefaultLayout(getAlphabetLayout(isPhone), getTestLocale());
    }

    // Alphabet shift lock shifted keyboard for testing subtype.
    ExpectedKey[][] getAlphabetShiftLockShiftedLayout(final boolean isPhone) {
        return AlphabetShifted.getDefaultLayout(getAlphabetLayout(isPhone), getTestLocale());
    }

    // Symbols keyboard for testing subtype.
    ExpectedKey[][] getSymbolsLayout(final boolean isPhone) {
        return Symbols.getDefaultLayout(isPhone);
    }

    // Symbols shifted keyboard for testing subtype.
    ExpectedKey[][] getSymbolsShiftedLayout(final boolean isPhone) {
        return SymbolsShifted.getDefaultLayout(isPhone);
    }

    // TODO: Add phone, phone symbols, number, number password layout tests.

    public final void testAlphabet() {
        final int elementId = KeyboardId.ELEMENT_ALPHABET;
        doKeyboardTests(elementId, getAlphabetLayout(isPhone()));
    }

    public final void testAlphabetAutomaticShifted() {
        final int elementId = KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED;
        doKeyboardTests(elementId, getAlphabetAutomaticShiftedLayout(isPhone()));
    }

    public final void testAlphabetManualShifted() {
        final int elementId = KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED;
        doKeyboardTests(elementId, getAlphabetManualShiftedLayout(isPhone()));
    }

    public final void testAlphabetShiftLocked() {
        final int elementId = KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED;
        doKeyboardTests(elementId, getAlphabetShiftLockedLayout(isPhone()));
    }

    public final void testAlphabetShiftLockShifted() {
        final int elementId = KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED;
        doKeyboardTests(elementId, getAlphabetShiftLockShiftedLayout(isPhone()));
    }

    public final void testSymbols() {
        final int elementId = KeyboardId.ELEMENT_SYMBOLS;
        doKeyboardTests(elementId, getSymbolsLayout(isPhone()));
    }

    public final void testSymbolsShifted() {
        final int elementId = KeyboardId.ELEMENT_SYMBOLS_SHIFTED;
        doKeyboardTests(elementId, getSymbolsShiftedLayout(isPhone()));
    }

    // Comparing expected keyboard and actual keyboard.
    private void doKeyboardTests(final int elementId, final ExpectedKey[][] expectedKeyboard) {
        // Skip test if no keyboard is defined.
        if (expectedKeyboard == null) {
            return;
        }
        final String tag = mLogTag + "/" + KeyboardId.elementIdToName(elementId);
        // Create actual keyboard object.
        final Keyboard keyboard = mKeyboardLayoutSet.getKeyboard(elementId);
        // Create actual keyboard to be compared with the expected keyboard.
        final Key[][] actualKeyboard = ActualKeyboardBuilder.buildKeyboard(keyboard.getKeys());

        // Dump human readable definition of expected/actual keyboards.
        Log.d(tag, "expected=\n" + ExpectedKeyboardBuilder.toString(expectedKeyboard));
        Log.d(tag, "actual  =\n" + ActualKeyboardBuilder.toString(actualKeyboard));
        // Test both keyboards have the same number of rows.
        assertEquals(tag + " labels"
                + "\nexpected=" + Arrays.deepToString(expectedKeyboard)
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
