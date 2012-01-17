/*
 * Copyright (C) 2012 The Android Open Source Project
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

public class KeyboardStateNonDistinctTests extends AndroidTestCase
        implements MockKeyboardSwitcher.Constants {
    protected MockKeyboardSwitcher mSwitcher;

    public boolean hasDistinctMultitouch() {
        return false;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSwitcher = new MockKeyboardSwitcher();

        final String layoutSwitchBackSymbols = "";
        mSwitcher.loadKeyboard(layoutSwitchBackSymbols, hasDistinctMultitouch());
    }

    public void assertAlphabetNormal() {
        assertTrue(mSwitcher.assertAlphabetNormal());
    }

    public void assertAlphabetManualShifted() {
        assertTrue(mSwitcher.assertAlphabetManualShifted());
    }

    public void assertAlphabetAutomaticShifted() {
        assertTrue(mSwitcher.assertAlphabetAutomaticShifted());
    }

    public void assertAlphabetShiftLocked() {
        assertTrue(mSwitcher.assertAlphabetShiftLocked());
    }

    public void assertSymbolsNormal() {
        assertTrue(mSwitcher.assertSymbolsNormal());
    }

    public void assertSymbolsShifted() {
        assertTrue(mSwitcher.assertSymbolsShifted());
    }

    // Initial state test.
    public void testLoadKeyboard() {
        assertAlphabetNormal();
    }

    // Shift key in alphabet mode.
    public void testShift() {
        // Press/release shift key, enter into shift state.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        mSwitcher.onCodeInput(CODE_SHIFT);
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        // Press/release shift key, back to normal state.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        mSwitcher.onCodeInput(CODE_SHIFT);
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetNormal();

        // Press/release shift key, enter into shift state.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        mSwitcher.onCodeInput(CODE_SHIFT);
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        // Press/release letter key, snap back to normal state.
        mSwitcher.onPressKey('Z');
        mSwitcher.onCodeInput('Z');
        mSwitcher.onReleaseKey('Z');
        assertAlphabetNormal();
    }

    // Shift key sliding input.
    public void testShiftSliding() {
        // Press shift key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        // Slide out shift key.
        mSwitcher.onReleaseKey(CODE_SHIFT, SLIDING);
        assertAlphabetManualShifted();

        // Enter into letter key.
        mSwitcher.onPressKey('Z');
        assertAlphabetManualShifted();
        // Release letter key, snap back to alphabet.
        mSwitcher.onCodeInput('Z');
        mSwitcher.onReleaseKey('Z');
        assertAlphabetNormal();
    }

    public void enterSymbolsMode() {
        // Press/release "?123" key.
        mSwitcher.onPressKey(CODE_SYMBOL);
        assertSymbolsNormal();
        mSwitcher.onCodeInput(CODE_SYMBOL);
        mSwitcher.onReleaseKey(CODE_SYMBOL);
        assertSymbolsNormal();
    }

    public void leaveSymbolsMode() {
        // Press/release "ABC" key.
        mSwitcher.onPressKey(CODE_SYMBOL);
        assertAlphabetNormal();
        mSwitcher.onCodeInput(CODE_SYMBOL);
        mSwitcher.onReleaseKey(CODE_SYMBOL);
        assertAlphabetNormal();
    }

    // Switching between alphabet and symbols.
    public void testAlphabetAndSymbols() {
        enterSymbolsMode();
        leaveSymbolsMode();
    }

    // Switching between alphabet shift locked and symbols.
    public void testAlphabetShiftLockedAndSymbols() {
        enterShiftLockWithLongPressShift();
        enterSymbolsMode();

        // Press/release "ABC" key, switch back to shift locked mode.
        mSwitcher.onPressKey(CODE_SYMBOL);
        assertAlphabetShiftLocked();
        mSwitcher.onCodeInput(CODE_SYMBOL);
        mSwitcher.onReleaseKey(CODE_SYMBOL);
        assertAlphabetShiftLocked();
    }

    // Symbols key sliding input.
    public void testSymbolsSliding() {
        // Press "123?" key.
        mSwitcher.onPressKey(CODE_SYMBOL);
        assertSymbolsNormal();
        // Slide out from "123?" key.
        mSwitcher.onReleaseKey(CODE_SYMBOL, SLIDING);
        assertSymbolsNormal();

        // Enter into letter key.
        mSwitcher.onPressKey('z');
        assertSymbolsNormal();
        // Release letter key, snap back to alphabet.
        mSwitcher.onCodeInput('z');
        mSwitcher.onReleaseKey('z');
        assertAlphabetNormal();
    }

    // Switching between symbols and symbols shifted.
    public void testSymbolsAndSymbolsShifted() {
        enterSymbolsMode();

        // Press/release "=\<" key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertSymbolsShifted();
        mSwitcher.onCodeInput(CODE_SHIFT);
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertSymbolsShifted();

        // Press/release "?123" key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertSymbolsNormal();
        mSwitcher.onCodeInput(CODE_SHIFT);
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertSymbolsNormal();

        leaveSymbolsMode();
    }

    // Symbols shift sliding input
    public void testSymbolsShiftSliding() {
        enterSymbolsMode();

        // Press "=\<" key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertSymbolsShifted();
        // Slide out "=\<" key.
        mSwitcher.onReleaseKey(CODE_SHIFT, SLIDING);
        assertSymbolsShifted();

        // Enter into symbol shifted letter key.
        mSwitcher.onPressKey('~');
        assertSymbolsShifted();
        // Release symbol shifted letter key, snap back to symbols.
        mSwitcher.onCodeInput('~');
        mSwitcher.onReleaseKey('~');
        assertSymbolsNormal();
    }

    // Symbols shift sliding input from symbols shifted.
    public void testSymbolsShiftSliding2() {
        enterSymbolsMode();

        // Press/release "=\<" key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertSymbolsShifted();
        mSwitcher.onCodeInput(CODE_SHIFT);
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertSymbolsShifted();

        // Press "123?" key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertSymbolsNormal();
        // Slide out "123?" key.
        mSwitcher.onReleaseKey(CODE_SHIFT, SLIDING);
        assertSymbolsNormal();

        // Enter into symbol letter key.
        mSwitcher.onPressKey('1');
        assertSymbolsNormal();
        // Release symbol letter key, snap back to symbols shift.
        mSwitcher.onCodeInput('1');
        mSwitcher.onReleaseKey('1');
        assertSymbolsShifted();
    }

    // Automatic snap back to alphabet from symbols by space key.
    public void testSnapBackBySpace() {
        enterSymbolsMode();

        // Enter a symbol letter.
        mSwitcher.onPressKey('1');
        assertSymbolsNormal();
        mSwitcher.onCodeInput('1');
        mSwitcher.onReleaseKey('1');
        assertSymbolsNormal();
        // Enter space, snap back to alphabet.
        mSwitcher.onPressKey(CODE_SPACE);
        assertSymbolsNormal();
        mSwitcher.onCodeInput(CODE_SPACE);
        mSwitcher.onReleaseKey(CODE_SPACE);
        assertAlphabetNormal();
    }

    // TODO: Add automatic snap back to shift locked test.

    // Automatic snap back to alphabet from symbols by registered letters.
    public void testSnapBack() {
        final String snapBackChars = "'";
        final int snapBackCode = snapBackChars.codePointAt(0);
        final boolean hasDistinctMultitouch = true;
        mSwitcher.loadKeyboard(snapBackChars, hasDistinctMultitouch);

        enterSymbolsMode();

        // Enter a symbol letter.
        mSwitcher.onPressKey('1');
        assertSymbolsNormal();
        mSwitcher.onCodeInput('1');
        mSwitcher.onReleaseKey('1');
        assertSymbolsNormal();
        // Enter snap back letter, snap back to alphabet.
        mSwitcher.onPressKey(snapBackCode);
        assertSymbolsNormal();
        mSwitcher.onCodeInput(snapBackCode);
        mSwitcher.onReleaseKey(snapBackCode);
        assertAlphabetNormal();
    }

    // Automatic upper case test
    public void testAutomaticUpperCase() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state with auto caps enabled.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();

        // Press shift key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        // Release shift key.
        mSwitcher.onCodeInput(CODE_SHIFT);
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetNormal();
    }

    // Sliding from shift key in automatic upper case.
    public void testAutomaticUpperCaseSliding() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state with auto caps enabled.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();

        // Press shift key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        // Slide out shift key.
        mSwitcher.onReleaseKey(CODE_SHIFT, SLIDING);
        assertAlphabetManualShifted();
        // Enter into letter key.
        mSwitcher.onPressKey('Z');
        assertAlphabetManualShifted();
        // Release letter key, snap back to alphabet.
        mSwitcher.onCodeInput('Z');
        mSwitcher.onReleaseKey('Z');
        assertAlphabetNormal();
    }

    // Sliding from symbol key in automatic upper case.
    public void testAutomaticUpperCaseSliding2() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state with auto caps enabled.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();

        // Press "123?" key.
        mSwitcher.onPressKey(CODE_SYMBOL);
        assertSymbolsNormal();
        // Slide out "123?" key.
        mSwitcher.onReleaseKey(CODE_SYMBOL, SLIDING);
        assertSymbolsNormal();
        // Enter into symbol letter keys.
        mSwitcher.onPressKey('1');
        assertSymbolsNormal();
        // Release symbol letter key, snap back to alphabet.
        mSwitcher.onCodeInput('1');
        mSwitcher.onReleaseKey('1');
        assertAlphabetNormal();
    }

    public void enterShiftLockWithLongPressShift() {
        // Long press shift key
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        // Long press recognized in LatinKeyboardView.KeyTimerHandler.
        mSwitcher.toggleCapsLock();
        assertAlphabetShiftLocked();
        mSwitcher.onCodeInput(CODE_CAPSLOCK);
        assertAlphabetShiftLocked();
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetShiftLocked();
    }

    public void leaveShiftLockWithLongPressShift() {
        // Press shift key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        // Long press recognized in LatinKeyboardView.KeyTimerHandler.
        mSwitcher.toggleCapsLock();
        assertAlphabetNormal();
        mSwitcher.onCodeInput(CODE_CAPSLOCK);
        assertAlphabetNormal();
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetNormal();
    }

    // Long press shift key.
    // TODO: Move long press recognizing timer/logic into KeyboardState.
    public void testLongPressShift() {
        enterShiftLockWithLongPressShift();
        leaveShiftLockWithLongPressShift();
     }

    // Leave shift lock with single tap shift key.
    public void testShiftInShiftLock() {
        enterShiftLockWithLongPressShift();
        assertAlphabetShiftLocked();

        // Tap shift key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        mSwitcher.onCodeInput(CODE_SHIFT, SINGLE);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetNormal();
    }

    // Double tap shift key.
    // TODO: Move double tap recognizing timer/logic into KeyboardState.
    public void testDoubleTapShift() {
        // First shift key tap.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        mSwitcher.onCodeInput(CODE_SHIFT);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        // Second shift key tap.
        // Double tap recognized in LatinKeyboardView.KeyTimerHandler.
        mSwitcher.toggleCapsLock();
        assertAlphabetShiftLocked();
        mSwitcher.onCodeInput(CODE_CAPSLOCK);
        assertAlphabetShiftLocked();

        // First shift key tap.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        mSwitcher.onCodeInput(CODE_SHIFT);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetNormal();
        // Second shift key tap.
        // Second tap is ignored in LatinKeyboardView.KeyTimerHandler.
    }

    // Update shift state.
    public void testUpdateShiftState() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();
    }

    // Update shift state when shift locked.
    public void testUpdateShiftStateInShiftLocked() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        enterShiftLockWithLongPressShift();
        assertAlphabetShiftLocked();
        // Update shift state when shift locked
        mSwitcher.updateShiftState();
        assertAlphabetShiftLocked();
    }

    // TODO: Change focus test.

    // TODO: Change orientation test.
}
