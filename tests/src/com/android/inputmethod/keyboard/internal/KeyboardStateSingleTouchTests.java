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

public class KeyboardStateSingleTouchTests extends KeyboardStateTestsBase {
    // Shift key in alphabet mode.
    public void testShift() {
        // Press/release shift key, enter into shift state.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release shift key, back to normal state.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);

        // Press/release shift key, enter into shift state.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release letter key, switch back to normal state.
        pressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
    }

    // Shift key sliding input.
    public void testShiftSliding() {
        // Press and slide from shift key.
        pressAndSlideFromKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Enter/release letter key, switch back to alphabet.
        pressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
    }

    // Switching between alphabet and symbols.
    public void testAlphabetAndSymbols() {
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "?123" key, back to alphabet.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
    }

    // Switching between alphabet shift locked and symbols.
    public void testAlphabetShiftLockedAndSymbols() {
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press/release "ABC" key, switch back to shift locked mode.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
    }

    // Symbols key sliding input.
    public void testSymbolsSliding() {
        // Press and slide from "123?" key.
        pressAndSlideFromKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Enter/release into symbol key, switch back to alphabet.
        pressAndReleaseKey('!', SYMBOLS_UNSHIFTED, ALPHABET_UNSHIFTED);
    }

    // Switching between symbols and symbols shifted.
    public void testSymbolsAndSymbolsShifted() {
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
    }

    // Symbols shift sliding input
    public void testSymbolsShiftSliding() {
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press and slide from "=\<" key.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);

        // Enter/release symbol shifted letter key, switch back to symbols.
        pressAndReleaseKey('~', SYMBOLS_SHIFTED, SYMBOLS_UNSHIFTED);
    }

    // Symbols shift sliding input from symbols shifted.
    public void testSymbolsShiftSliding2() {
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);

        // Press and slide from "123?" key.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Enter/release symbol letter key, switch back to symbols shifted.
        pressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_SHIFTED);
    }

    // Automatic switch back to alphabet from symbols by space key.
    public void testSwitchBackBySpace() {
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Enter a symbol letter.
        pressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Enter space, switch back to alphabet.
        pressAndReleaseKey(CODE_SPACE, SYMBOLS_UNSHIFTED, ALPHABET_UNSHIFTED);
    }

    // Automatic switch back to shift locked test.
    public void testSwitchBackBySpaceInShiftLocked() {
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Enter a symbol letter.
        pressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Enter space, switch back to alphabet.
        pressAndReleaseKey(CODE_SPACE, SYMBOLS_UNSHIFTED, ALPHABET_SHIFT_LOCKED);
    }


    // Automatic switch back to alphabet from symbols by registered letters.
    public void testSwitchBackChar() {
        // Set switch back chars.
        final String switchBackSymbols = "'";
        final int switchBackCode = switchBackSymbols.codePointAt(0);
        setLayoutSwitchBackSymbols(switchBackSymbols);
        loadKeyboard(ALPHABET_UNSHIFTED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Enter a symbol letter.
        pressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Enter switch back letter, switch back to alphabet.
        pressAndReleaseKey(switchBackCode, SYMBOLS_UNSHIFTED, ALPHABET_UNSHIFTED);
    }

    // Automatic upper case test
    public void testAutomaticUpperCase() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state with auto caps enabled.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release shift key, back to alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
    }

    // Sliding from shift key in automatic upper case.
    public void testAutomaticUpperCaseSliding() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state with auto caps enabled.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Press and slide from shift key.
        pressAndSlideFromKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Enter and release letter key, back to alphabet.
        pressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
    }

    // Sliding from symbol key in automatic upper case.
    public void testAutomaticUpperCaseSliding2() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state with auto caps enabled.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Press and slide from "123?" key.
        pressAndSlideFromKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Enter and release symbol letter keys, back to alphabet.
        pressAndReleaseKey('1', SYMBOLS_UNSHIFTED, ALPHABET_UNSHIFTED);
    }

    // Long press shift key.
    // TODO: Move long press recognizing timer/logic into KeyboardState.
    public void testLongPressShift() {
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);

        // Press/release letter key, remain in shift locked.
        pressAndReleaseKey('A', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Press/release letter key, remain in shift locked.
        pressAndReleaseKey('B', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Press/release word separator, remain in shift locked.
        pressAndReleaseKey(CODE_SPACE, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Long press shift key, back to alphabet.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
     }

    // Leave shift lock with single tap shift key.
    public void testShiftInShiftLock() {
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);

        // Press/release shift key, back to alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
    }

    // Double tap shift key.
    // TODO: Move double tap recognizing timer/logic into KeyboardState.
    public void testDoubleTapShift() {
        // First shift key tap.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Second shift key tap.
        // Double tap recognized in LatinKeyboardView.KeyTimerHandler.
        secondTapShiftKey(ALPHABET_SHIFT_LOCKED);

        // First shift key tap.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);

        // Second shift key tap.
        // Second tap is ignored in LatinKeyboardView.KeyTimerHandler.
    }

    // Update shift state.
    public void testUpdateShiftState() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release letter key, back to alphabet.
        pressAndReleaseKey('A', ALPHABET_AUTOMATIC_SHIFTED, ALPHABET_UNSHIFTED);

        // Press/release letter key
        pressAndReleaseKey('b', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Press/release auto caps trigger letter, back to automatic shifted.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);
    }

    // Update shift state when shift locked.
    public void testUpdateShiftStateInShiftLocked() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);

        // Update shift state when shift locked
        updateShiftState(ALPHABET_SHIFT_LOCKED);
    }

    // Change focus to new text field.
    public void testChangeFocus() {
        // Press/release shift key.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_UNSHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_UNSHIFTED);

        // Press/release symbol key.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_UNSHIFTED);

        // Press/release symbol key.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release shift key.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_UNSHIFTED);
    }

    // Change focus to auto caps text field.
    public void testChangeFocusAutoCaps() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release shift key, enter alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release symbol key.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release symbol key.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release shift key.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);
    }

    // Change orientation.
    public void testChangeOrientation() {
        // Press/release shift key.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rotate device.
        rotateDevice(ALPHABET_MANUAL_SHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);
        // Rotate device.
        rotateDevice(ALPHABET_SHIFT_LOCKED);

        // Press/release symbol key.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Rotate device.
        rotateDevice(SYMBOLS_UNSHIFTED);

        // Press/release shift key.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Rotate device.
        rotateDevice(SYMBOLS_SHIFTED);
    }
}
