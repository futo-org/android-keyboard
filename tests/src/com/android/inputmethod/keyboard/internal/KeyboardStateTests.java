/*
 * Copyright (C) 2011 The Android Open Source Project
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

public class KeyboardStateTests extends AndroidTestCase {
    private MockKeyboardSwitcher mSwitcher;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSwitcher = new MockKeyboardSwitcher();

        final String layoutSwitchBackSymbols = "";
        final boolean hasDistinctMultitouch = true;
        mSwitcher.loadKeyboard(layoutSwitchBackSymbols, hasDistinctMultitouch);
    }

    // Argument for KeyboardState.onPressShift and onReleaseShift.
    private static final boolean NOT_SLIDING = false;
    private static final boolean SLIDING = true;
    // Argument for KeyboardState.onCodeInput.
    private static final boolean SINGLE = true;
    private static final boolean MULTI = false;
    static final boolean NO_AUTO_CAPS = false;
    private static final boolean AUTO_CAPS = true;

    private void assertAlphabetNormal() {
        assertTrue(mSwitcher.assertAlphabetNormal());
    }

    private void assertAlphabetManualShifted() {
        assertTrue(mSwitcher.assertAlphabetManualShifted());
    }

    private void assertAlphabetAutomaticShifted() {
        assertTrue(mSwitcher.assertAlphabetAutomaticShifted());
    }

    private void assertAlphabetShiftLocked() {
        assertTrue(mSwitcher.assertAlphabetShiftLocked());
    }

    private void assertSymbolsNormal() {
        assertTrue(mSwitcher.assertSymbolsNormal());
    }

    private void assertSymbolsShifted() {
        assertTrue(mSwitcher.assertSymbolsShifted());
    }

    // Initial state test.
    public void testLoadKeyboard() {
        assertAlphabetNormal();
    }

    // Shift key in alphabet mode.
    public void testShift() {
        // Press/release shift key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetManualShifted();

        // Press/release shift key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();

        // TODO: Sliding test
    }

    // Switching between alphabet and symbols.
    public void testAlphabetAndSymbols() {
        // Press/release "?123" key.
        mSwitcher.onPressSymbol();
        assertSymbolsNormal();
        mSwitcher.onReleaseSymbol();
        assertSymbolsNormal();

        // Press/release "ABC" key.
        mSwitcher.onPressSymbol();
        assertAlphabetNormal();
        mSwitcher.onReleaseSymbol();
        assertAlphabetNormal();

        // TODO: Sliding test
        // TODO: Snap back test
    }

    // Switching between symbols and symbols shifted.
    public void testSymbolsAndSymbolsShifted() {
        // Press/release "?123" key.
        mSwitcher.onPressSymbol();
        assertSymbolsNormal();
        mSwitcher.onReleaseSymbol();
        assertSymbolsNormal();

        // Press/release "=\<" key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertSymbolsShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertSymbolsShifted();

        // Press/release "ABC" key.
        mSwitcher.onPressSymbol();
        assertAlphabetNormal();
        mSwitcher.onReleaseSymbol();
        assertAlphabetNormal();

        // TODO: Sliding test
        // TODO: Snap back test
    }

    // Automatic upper case test
    public void testAutomaticUpperCase() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state with auto caps enabled.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();

        // Press shift key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Release shift key.
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();

        // TODO: Chording test.
    }

    // TODO: UpdateShiftState with shift locked, etc.

    // TODO: Multitouch test

    // TODO: Change focus test.

    // TODO: Change orientation test.

    // Long press shift key.
    // TODO: Move long press recognizing timer/logic into KeyboardState.
    public void testLongPressShift() {
        // Long press shift key
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Long press recognized in LatinKeyboardView.KeyTimerHandler.
        mSwitcher.toggleCapsLock();
        assertAlphabetShiftLocked();
        mSwitcher.onCodeInput(Keyboard.CODE_CAPSLOCK, SINGLE);
        assertAlphabetShiftLocked();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetShiftLocked();

        // Long press shift key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Long press recognized in LatinKeyboardView.KeyTimerHandler.
        mSwitcher.toggleCapsLock();
        assertAlphabetNormal();
        mSwitcher.onCodeInput(Keyboard.CODE_CAPSLOCK, SINGLE);
        assertAlphabetNormal();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();
    }

    // Double tap shift key.
    // TODO: Move double tap recognizing timer/logic into KeyboardState.
    public void testDoubleTapShift() {
        // First shift key tap.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Second shift key tap.
        // Double tap recognized in LatinKeyboardView.KeyTimerHandler.
        mSwitcher.toggleCapsLock();
        assertAlphabetShiftLocked();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetShiftLocked();

        // First shift key tap.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();
        // Second shift key tap.
        // Second tap is ignored in LatinKeyboardView.KeyTimerHandler.
    }
}
