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
    private MockKeyboardSwitcher mSwitcher;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSwitcher = new MockKeyboardSwitcher();

        final String layoutSwitchBackSymbols = "";
        final boolean hasDistinctMultitouch = false;
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
        // Press/release shift key, enter into shift state.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetNormal();
        mSwitcher.toggleShift();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetManualShifted();
        // Press/release shift key, back to normal state.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetManualShifted();
        mSwitcher.toggleShift();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetNormal();

        // Press/release shift key, enter into shift state.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetNormal();
        mSwitcher.toggleShift();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetManualShifted();
        // Press/release letter key, snap back to normal state.
        mSwitcher.onOtherKeyPressed();
        mSwitcher.onCodeInput('Z', SINGLE);
        assertAlphabetNormal();
    }

    private void enterSymbolsMode() {
        // Press/release "?123" key.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetNormal();
        mSwitcher.toggleAlphabetAndSymbols();
        mSwitcher.onCodeInput(Keyboard.CODE_SWITCH_ALPHA_SYMBOL, SINGLE);
        assertSymbolsNormal();
    }

    private void leaveSymbolsMode() {
        // Press/release "ABC" key.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        mSwitcher.toggleAlphabetAndSymbols();
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

        // Press/release "?123" key.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetShiftLocked();
        mSwitcher.toggleAlphabetAndSymbols();
        mSwitcher.onCodeInput(Keyboard.CODE_SWITCH_ALPHA_SYMBOL, SINGLE);
        assertSymbolsNormal();

        // Press/release "ABC" key, switch back to shift locked mode.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        mSwitcher.toggleAlphabetAndSymbols();
        mSwitcher.onCodeInput(Keyboard.CODE_SWITCH_ALPHA_SYMBOL, SINGLE);
        assertAlphabetShiftLocked();
    }

    // Switching between symbols and symbols shifted.
    public void testSymbolsAndSymbolsShifted() {
        enterSymbolsMode();

        // Press/release "=\<" key.
        // Press/release shift key, enter into shift state.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        mSwitcher.toggleShift();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertSymbolsShifted();

        // Press/release "?123" key.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsShifted();
        mSwitcher.toggleShift();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertSymbolsNormal();

        leaveSymbolsMode();
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

        // TODO: Add automatic snap back to shift locked test.
    }

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
        mSwitcher.onOtherKeyPressed();
        assertAlphabetAutomaticShifted();
        // Release shift key.
        mSwitcher.toggleShift();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetNormal();
    }

    private void enterShiftLockWithLongPressShift() {
        // Press shift key.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetNormal();
        // Long press recognized in LatinKeyboardView.KeyTimerHandler.
        mSwitcher.toggleCapsLock();
        mSwitcher.onCodeInput(Keyboard.CODE_CAPSLOCK, SINGLE);
        assertAlphabetShiftLocked();
    }

    private void leaveShiftLockWithLongPressShift() {
        // Press shift key.
        mSwitcher.onOtherKeyPressed();
        // Long press recognized in LatinKeyboardView.KeyTimerHandler.
        mSwitcher.toggleCapsLock();
        mSwitcher.onCodeInput(Keyboard.CODE_CAPSLOCK, SINGLE);
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
        mSwitcher.onOtherKeyPressed();
        assertAlphabetShiftLocked();
        mSwitcher.toggleShift();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetNormal();
    }

    // Double tap shift key.
    // TODO: Move double tap recognizing timer/logic into KeyboardState.
    public void testDoubleTapShift() {
        // First shift key tap.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetNormal();
        mSwitcher.toggleShift();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetManualShifted();
        // Second shift key tap.
        // Double tap recognized in LatinKeyboardView.KeyTimerHandler.
        mSwitcher.toggleCapsLock();
        assertAlphabetShiftLocked();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetShiftLocked();

        // First shift key tap.
        mSwitcher.onOtherKeyPressed();
        assertAlphabetShiftLocked();
        mSwitcher.toggleShift();
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
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
