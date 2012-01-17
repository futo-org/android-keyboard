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

import com.android.inputmethod.keyboard.Keyboard;

public class KeyboardStateNonDistinctTests extends AndroidTestCase {
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

    // Argument for KeyboardState.onPressShift and onReleaseShift.
    public static final boolean NOT_SLIDING = false;
    public static final boolean SLIDING = true;
    // Argument for KeyboardState.onCodeInput.
    public static final boolean SINGLE = true;
    public static final boolean MULTI = false;
    public static final boolean NO_AUTO_CAPS = false;
    public static final boolean AUTO_CAPS = true;

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
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Press/release shift key, back to normal state.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();

        // Press/release shift key, enter into shift state.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Press/release letter key, snap back to normal state.
        mSwitcher.onOtherKeyPressed();
        mSwitcher.onCodeInput('Z', SINGLE);
        assertAlphabetNormal();
    }

    // Shift key sliding input.
    public void testShiftSliding() {
        // Press shift key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Slide out shift key.
        mSwitcher.onReleaseShift(SLIDING);
        assertAlphabetManualShifted();

        // Enter into letter key.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetManualShifted();
        // Release letter key, snap back to alphabet.
        mSwitcher.onCodeInput('Z', SINGLE);
        assertAlphabetNormal();
    }

    public void enterSymbolsMode() {
        // Press/release "?123" key.
        mSwitcher.onPressSymbol();
        assertSymbolsNormal();
        mSwitcher.onCodeInput(Keyboard.CODE_SWITCH_ALPHA_SYMBOL, SINGLE);
        mSwitcher.onReleaseSymbol();
        assertSymbolsNormal();
    }

    public void leaveSymbolsMode() {
        // Press/release "ABC" key.
        mSwitcher.onPressSymbol();
        assertAlphabetNormal();
        mSwitcher.onCodeInput(Keyboard.CODE_SWITCH_ALPHA_SYMBOL, SINGLE);
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
        mSwitcher.onPressSymbol();
        assertAlphabetShiftLocked();
        mSwitcher.onCodeInput(Keyboard.CODE_SWITCH_ALPHA_SYMBOL, SINGLE);
        mSwitcher.onReleaseSymbol();
        assertAlphabetShiftLocked();
    }

    // Symbols key sliding input.
    public void testSymbolsSliding() {
        // Press "123?" key.
        mSwitcher.onPressSymbol();
        assertSymbolsNormal();
        // Slide out from "123?" key.
        mSwitcher.onReleaseSymbol();
        assertSymbolsNormal();

        // Enter into letter key.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        // Release letter key, snap back to alphabet.
        mSwitcher.onCodeInput('z', SINGLE);
        assertAlphabetNormal();
    }

    // Switching between symbols and symbols shifted.
    public void testSymbolsAndSymbolsShifted() {
        enterSymbolsMode();

        // Press/release "=\<" key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertSymbolsShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertSymbolsShifted();

        // Press/release "?123" key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertSymbolsNormal();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertSymbolsNormal();

        leaveSymbolsMode();
    }

    // Symbols shift sliding input
    public void testSymbolsShiftSliding() {
        enterSymbolsMode();

        // Press "=\<" key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertSymbolsShifted();
        // Slide out "=\<" key.
        mSwitcher.onReleaseShift(SLIDING);
        assertSymbolsShifted();

        // Enter into symbol shifted letter key.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsShifted();
        // Release symbol shifted letter key, snap back to symbols.
        mSwitcher.onCodeInput('~', SINGLE);
        assertSymbolsNormal();
    }

    // Symbols shift sliding input from symbols shifted.
    public void testSymbolsShiftSliding2() {
        enterSymbolsMode();

        // Press/release "=\<" key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertSymbolsShifted();
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertSymbolsShifted();

        // Press "123?" key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertSymbolsNormal();
        // Slide out "123?" key.
        mSwitcher.onReleaseShift(SLIDING);
        assertSymbolsNormal();

        // Enter into symbol letter key.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        // Release symbol letter key, snap back to symbols shift.
        mSwitcher.onCodeInput('1', SINGLE);
        assertSymbolsShifted();
    }

    // Automatic snap back to alphabet from symbols by space key.
    public void testSnapBackBySpace() {
        enterSymbolsMode();

        // Enter a symbol letter.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        mSwitcher.onCodeInput('1', SINGLE);
        assertSymbolsNormal();
        // Enter space, snap back to alphabet.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        mSwitcher.onCodeInput(Keyboard.CODE_SPACE, SINGLE);
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
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        mSwitcher.onCodeInput('1', SINGLE);
        assertSymbolsNormal();
        // Enter snap back letter, snap back to alphabet.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        mSwitcher.onCodeInput(snapBackCode, SINGLE);
        assertAlphabetNormal();
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
    }

    // Sliding from shift key in automatic upper case.
    public void testAutomaticUpperCaseSliding() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state with auto caps enabled.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();

        // Press shift key.
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Slide out shift key.
        mSwitcher.onReleaseShift(SLIDING);
        assertAlphabetManualShifted();
        // Enter into letter key.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetManualShifted();
        // Release letter key, snap back to alphabet.
        mSwitcher.onCodeInput('Z', SINGLE);
        assertAlphabetNormal();
    }

    // Sliding from symbol key in automatic upper case.
    public void testAutomaticUpperCaseSliding2() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state with auto caps enabled.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();

        // Press "123?" key.
        mSwitcher.onPressSymbol();
        assertSymbolsNormal();
        // Slide out "123?" key.
        mSwitcher.onReleaseSymbol();
        assertSymbolsNormal();
        // Enter into symbol letter keys.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        // Release symbol letter key, snap back to alphabet.
        mSwitcher.onCodeInput('1', SINGLE);
        assertAlphabetNormal();
    }

    public void enterShiftLockWithLongPressShift() {
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
    }

    public void leaveShiftLockWithLongPressShift() {
        // Press shift key.
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
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetManualShifted();
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
