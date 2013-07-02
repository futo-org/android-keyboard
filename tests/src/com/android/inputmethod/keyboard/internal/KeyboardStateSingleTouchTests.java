/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.test.suitebuilder.annotation.SmallTest;

@SmallTest
public class KeyboardStateSingleTouchTests extends KeyboardStateTestsBase {
    // Shift key in alphabet.
    public void testShiftAlphabet() {
        // Press/release shift key, enter into alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Press/release shift key, back to alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);

        // Press/release shift key, enter into alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release letter key, switch back to alphabet.
        pressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
    }

    // Shift key in symbols.
    public void testShiftSymbols() {
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);

        // Press/release "?123" key, back to symbols.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press/release symbol letter key, remain in symbols shifted.
        pressAndReleaseKey('1', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
    }

    // Switching between alphabet and symbols.
    public void testAlphabetAndSymbols() {
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, back to alphabet.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, back to alphabet.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Press/release "?123" key, back to symbols (not symbols shifted).
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
    }

    // Switching between alphabet shifted and symbols.
    public void testAlphabetShiftedAndSymbols() {
        // Press/release shift key, enter into alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, back to alphabet (not alphabet shifted).
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Press/release shift key, enter into alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\< key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, back to alphabet (not alphabet shifted).
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
    }

    // Switching between alphabet shift locked and symbols.
    public void testAlphabetShiftLockedAndSymbols() {
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, back to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, back to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, back to symbols (not symbols shifted).
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
    }

    // Automatic switch back to alphabet by space key.
    public void testSwitchBackBySpace() {
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter symbol letter.
        pressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter space, switch back to alphabet.
        pressAndReleaseKey(CODE_SPACE, SYMBOLS_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Enter symbol shift letter.
        pressAndReleaseKey('~', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Enter space, switch back to alphabet.
        pressAndReleaseKey(CODE_SPACE, SYMBOLS_SHIFTED, ALPHABET_UNSHIFTED);
        // Press/release "?123" key, enter into symbols (not symbols shifted).
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
    }

    // Automatic switch back to alphabet shift locked test by space key.
    public void testSwitchBackBySpaceShiftLocked() {
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter symbol letter.
        pressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter space, switch back to alphabet shift locked.
        pressAndReleaseKey(CODE_SPACE, SYMBOLS_UNSHIFTED, ALPHABET_SHIFT_LOCKED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Enter symbol shift letter.
        pressAndReleaseKey('~', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Enter space, switch back to alphabet shift locked.
        pressAndReleaseKey(CODE_SPACE, SYMBOLS_SHIFTED, ALPHABET_SHIFT_LOCKED);
    }

    // Automatic upper case test
    public void testAutomaticUpperCase() {
        // Set capitalize the first character of all words mode.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Load keyboard, should be in automatic shifted.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release letter key, switch to alphabet.
        pressAndReleaseKey('A', ALPHABET_AUTOMATIC_SHIFTED, ALPHABET_UNSHIFTED);
        // Press/release auto caps trigger letter, should be in automatic shifted.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release shift key, back to alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Press/release letter key, remain in alphabet.
        pressAndReleaseKey('a', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Press/release auto caps trigger letter, should be in automatic shifted.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release symbol letter key, remain in symbols.
        pressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release space, switch back to automatic shifted.
        pressAndReleaseKey(CODE_SPACE, SYMBOLS_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press/release symbol shift letter key, remain in symbols shifted.
        pressAndReleaseKey('~', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press/release space, switch back to automatic shifted.
        pressAndReleaseKey(CODE_SPACE, SYMBOLS_SHIFTED, ALPHABET_AUTOMATIC_SHIFTED);
    }

    // Long press shift key.
    public void testLongPressShift() {
        // Set auto caps mode off.
        setAutoCapsMode(CAP_MODE_OFF);
        // Load keyboard, should be in alphabet.
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release shift key, back to alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_UNSHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release letter key, remain in shift locked.
        pressAndReleaseKey('A', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Press/release word separator, remain in shift locked.
        pressAndReleaseKey(CODE_SPACE, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Press/release shift key, back to alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_UNSHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Long press shift key, back to alphabet.
        longPressAndReleaseShiftKey(ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_SHIFT_LOCK_SHIFTED,
                ALPHABET_UNSHIFTED);

        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release shift key, back to alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_UNSHIFTED);

        // Set capitalize the first character of all words mode.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Load keyboard, should be in automatic shifted.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release shift key, back to alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_UNSHIFTED);
    }

    // Double tap shift key.
    public void testDoubleTapShift() {
        // First shift key tap.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Second shift key tap.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // First shift key tap.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_UNSHIFTED);
        // Second shift key tap.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Press/release shift key, enter alphabet manual shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // First shift key tap.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Second shift key tap.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // First shift key tap.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_UNSHIFTED);
        // Second shift key tap.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Set capitalize the first character of all words mode.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Load keyboard, should be in automatic shifted.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // First shift key tap.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Second shift key tap.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // First shift key tap.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_UNSHIFTED);
        // Second shift key tap.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
    }

    // Update shift state.
    public void testUpdateShiftState() {
        // Set auto caps mode off.
        setAutoCapsMode(CAP_MODE_OFF);
        // Load keyboard, should be in alphabet.
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Update shift state, remained in alphabet.
        updateShiftState(ALPHABET_UNSHIFTED);

        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Update shift state, back to alphabet.
        updateShiftState(ALPHABET_UNSHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Update shift state, remained in alphabet shift locked.
        updateShiftState(ALPHABET_SHIFT_LOCKED);
        // Long press shift key, back to alphabet.
        longPressAndReleaseShiftKey(ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_SHIFT_LOCK_SHIFTED,
                ALPHABET_UNSHIFTED);

        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Update shift state, remained in symbols.
        updateShiftState(SYMBOLS_UNSHIFTED);

        // Press/release "=\<" key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Update shift state, remained in symbols shifted.
        updateShiftState(SYMBOLS_SHIFTED);

        // Set capitalize the first character of all words mode.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Load keyboard, should be in automatic shifted.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);
        // Update shift state, remained in automatic shifted.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release shift key, enter alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Update shift state, enter to automatic shifted (not alphabet shifted).
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Update shift state, remained in alphabet shift locked (not automatic shifted).
        updateShiftState(ALPHABET_SHIFT_LOCKED);
        // Long press shift key, back to alphabet.
        longPressAndReleaseShiftKey(ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_SHIFT_LOCK_SHIFTED,
                ALPHABET_UNSHIFTED);

        // Load keyboard, should be in automatic shifted.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Update shift state, remained in symbols.
        updateShiftState(SYMBOLS_UNSHIFTED);

        // Press/release "=\<" key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Update shift state, remained in symbols shifted.
        updateShiftState(SYMBOLS_SHIFTED);
    }

    // Sliding input in alphabet.
    public void testSlidingAlphabet() {
        // Alphabet -> shift key + letter -> alphabet.
        // Press and slide from shift key, enter alphabet shifted.
        pressAndSlideFromKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Enter/release letter keys, switch back to alphabet.
        pressAndSlideFromKey('A', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        stopSlidingOnKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet -> "?123" key + letter -> alphabet.
        // Press and slide from "123?" key, enter symbols.
        pressAndSlideFromKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release into symbol letter keys, switch back to alphabet.
        pressAndSlideFromKey('@', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        stopSlidingOnKey('!', SYMBOLS_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shifted -> shift key + letter -> alphabet.
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press and slide from shift key, remain alphabet shifted.
        pressAndSlideFromKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Enter/release letter keys, switch back to alphabet (not alphabet shifted).
        pressAndSlideFromKey('A', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        stopSlidingOnKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shifted -> "?123" key + letter -> alphabet.
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press and slide from "123?" key, enter symbols.
        pressAndSlideFromKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release into symbol letter keys, switch back to alphabet (not alphabet shifted).
        pressAndSlideFromKey('@', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        stopSlidingOnKey('!', SYMBOLS_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shift locked -> shift key + letter -> alphabet shift locked.
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press and slide from "123?" key, enter symbols.
        pressAndSlideFromKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release into symbol letter keys, switch back to alphabet shift locked.
        pressAndSlideFromKey('!', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        stopSlidingOnKey('!', SYMBOLS_UNSHIFTED, ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> "?123" key + letter -> alphabet shift locked.
        // Press and slide from shift key, enter alphabet shifted.
        pressAndSlideFromKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_SHIFT_LOCKED);
        // Enter/release letter keys, switch back to shift locked.
        pressAndSlideFromKey('A', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        stopSlidingOnKey('Z', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
    }

    // Cancel sliding input in alphabet.
    public void testSlidingAlphabetCancel() {
        // Alphabet -> shift key + letter -> cancel -> alphabet.
        // Press and slide from shift key, enter alphabet shifted.
        pressAndSlideFromKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press and slide from shift key, enter alphabet shifted.
        pressAndSlideFromKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Enter/release letter key, remains in alphabet shifted.
        pressAndSlideFromKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Cancel sliding, switch back to alphabet.
        stopSlidingAndCancel(ALPHABET_UNSHIFTED);

        // Alphabet -> "?123" key + letter -> cancel -> alphabet.
        // Press and slide from "123?" key, enter symbols.
        pressAndSlideFromKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release into symbol letter key, remains in symbols.
        pressAndSlideFromKey('!', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Cancel sliding, switch back to alphabet.
        stopSlidingAndCancel(ALPHABET_UNSHIFTED);

        // Alphabet shifted -> shift key + letter -> cancel -> alphabet.
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press and slide from shift key, remain alphabet shifted.
        pressAndSlideFromKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Enter/release letter key, remains in alphabet shifted.
        pressAndSlideFromKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Cancel sliding, switch back to alphabet (not alphabet shifted).
        stopSlidingAndCancel(ALPHABET_UNSHIFTED);

        // Alphabet shifted -> "?123" key + letter -> cancel -> alphabet.
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press and slide from "123?" key, enter symbols.
        pressAndSlideFromKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release into symbol letter key, remains in symbols.
        pressAndSlideFromKey('!', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Cancel sliding, switch back to alphabet (not alphabet shifted).
        stopSlidingAndCancel(ALPHABET_UNSHIFTED);

        // Alphabet shift locked -> shift key + letter -> cancel -> alphabet shift locked.
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press and slide from "123?" key, enter symbols.
        pressAndSlideFromKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release into symbol letter key, remains in symbols.
        pressAndSlideFromKey('!', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Cancel sliding, switch back to alphabet shift locked.
        stopSlidingAndCancel( ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> "?123" key + letter -> cancel -> alphabet shift locked.
        // Press and slide from shift key, enter alphabet shifted.
        pressAndSlideFromKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_SHIFT_LOCKED);
        // Enter/release letter key, remains in alphabet shift locked.
        pressAndSlideFromKey('Z', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Enter/release letter key, switch back to shift locked.
        stopSlidingAndCancel(ALPHABET_SHIFT_LOCKED);
    }

    // Sliding input in symbols.
    public void testSlidingSymbols() {
        // Symbols -> "=\<" key + letter -> symbols.
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press and slide from shift key, enter symbols shifted.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Enter/release symbol shifted letter keys, switch back to symbols.
        pressAndSlideFromKey('|', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        stopSlidingOnKey('~', SYMBOLS_SHIFTED, SYMBOLS_UNSHIFTED);

        // Symbols -> "ABC" key + letter -> Symbols.
        // Press and slide from "ABC" key, enter alphabet.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Enter/release letter keys, switch back to symbols.
        pressAndSlideFromKey('z', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        stopSlidingOnKey('a', ALPHABET_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shifted -> symbols -> "ABC" key + letter -> symbols ->
        // alphabet.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press and slide from "ABC" key.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Enter/release letter keys, switch back to symbols.
        pressAndSlideFromKey('z', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        stopSlidingOnKey('a', ALPHABET_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet (not alphabet shifted).
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shift locked -> symbols -> "ABC" key + letter -> symbols ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press and slide from "ABC" key, enter alphabet shift locked.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Enter/release letter keys, switch back to symbols.
        pressAndSlideFromKey('Z', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        stopSlidingOnKey('A', ALPHABET_SHIFT_LOCKED, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> symbols -> "=\<" key + letter -> symbols ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press and slide from "=\<" key, enter symbols shifted.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Enter/release symbols shift letter keys, switch back to symbols.
        pressAndSlideFromKey('|', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        stopSlidingOnKey('~', SYMBOLS_SHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
    }

    // Cancel sliding input in symbols.
    public void testSlidingSymbolsCancel() {
        // Symbols -> "=\<" key + letter -> cancel -> symbols.
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press and slide from shift key, enter symbols shifted.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Enter/release symbol shifted letter key, remains in symbols shifted.
        pressAndSlideFromKey('|', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Cancel sliding, switch back to symbols.
        stopSlidingAndCancel(SYMBOLS_UNSHIFTED);

        // Symbols -> "ABC" key + letter -> Symbols.
        // Press and slide from "ABC" key, enter alphabet.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Enter/release letter keys, remains in alphabet.
        pressAndSlideFromKey('z', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Cancel sliding, switch back to symbols.
        stopSlidingAndCancel(SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shifted -> symbols -> "ABC" key + letter -> symbols ->
        // alphabet.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press and slide from "ABC" key.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Enter/release letter key, remains in alphabet.
        pressAndSlideFromKey('z', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Cancel sliding, switch back to symbols.
        stopSlidingAndCancel(SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet (not alphabet shifted).
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shift locked -> symbols -> "ABC" key + letter -> symbols ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press and slide from "ABC" key, enter alphabet shift locked.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Enter/release letter key, remains in alphabet shifted.
        pressAndSlideFromKey('Z', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Cancel sliding, switch back to symbols.
        stopSlidingAndCancel(SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> symbols -> "=\<" key + letter -> symbols ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press and slide from "=\<" key, enter symbols shifted.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Enter/release symbols shift letter key, remains in symbols shifted.
        pressAndSlideFromKey('|', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Cancel sliding, switch back to symbols.
        stopSlidingAndCancel(SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
    }

    // Sliding input in symbols shifted.
    public void testSlidingSymbolsShifted() {
        // Symbols shifted -> "?123" + letter -> symbols shifted.
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press and slide from shift key, enter symbols.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release symbol letter keys, switch back to symbols shifted.
        pressAndSlideFromKey('2', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        stopSlidingOnKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_SHIFTED);

        // Symbols shifted -> "ABC" key + letter -> symbols shifted.
        // Press and slide from "ABC" key, enter alphabet.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Enter/release letter keys, switch back to symbols shifted.
        pressAndSlideFromKey('z', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        stopSlidingOnKey('a', ALPHABET_UNSHIFTED, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shifted -> symbols shifted -> "ABC" + letter -> symbols shifted ->
        // alphabet.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press and slide from "ABC" key.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Enter/release letter keys, switch back to symbols shifted.
        pressAndSlideFromKey('z', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        stopSlidingOnKey('a', ALPHABET_UNSHIFTED, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet (not alphabet shifted).
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shift locked -> symbols shifted -> "ABC" + letter -> symbols shifted ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press and slide from "ABC" key.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Enter/release letter keys, switch back to symbols shifted.
        pressAndSlideFromKey('Z', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        stopSlidingOnKey('A', ALPHABET_SHIFT_LOCKED, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> symbols shifted -> "?123" + letter -> symbols shifted ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press and slide from "?123" key.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release symbol letter keys, switch back to symbols shifted.
        pressAndSlideFromKey('2', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        stopSlidingOnKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
    }

    // Cancel sliding input in symbols shifted.
    public void testSlidingSymbolsShiftedCancel() {
        // Symbols shifted -> "?123" + letter -> symbols shifted.
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press and slide from shift key, enter symbols.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release symbol letter key, remains in symbols.
        pressAndSlideFromKey('2', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Cancel sliding, switch back to symbols shifted.
        stopSlidingAndCancel(SYMBOLS_SHIFTED);

        // Symbols shifted -> "ABC" key + letter -> symbols shifted.
        // Press and slide from "ABC" key, enter alphabet.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Enter/release letter key, remains in alphabet.
        pressAndSlideFromKey('z', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Cancel sliding, switch back to symbols shifted.
        stopSlidingAndCancel(SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shifted -> symbols shifted -> "ABC" + letter -> symbols shifted ->
        // alphabet.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press and slide from "ABC" key.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Enter/release letter key, remains in alphabet.
        pressAndSlideFromKey('z', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Cancel sliding, switch back to symbols shifted.
        stopSlidingAndCancel(SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet (not alphabet shifted).
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shift locked -> symbols shifted -> "ABC" + letter -> symbols shifted ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press and slide from "ABC" key.
        pressAndSlideFromKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Enter/release letter key, remains in alphabet shift locked.
        pressAndSlideFromKey('Z', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Cancel sliding, switch back to symbols shifted.
        stopSlidingAndCancel(SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> symbols shifted -> "?123" + letter -> symbols shifted ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter into symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press and slide from "?123" key.
        pressAndSlideFromKey(CODE_SHIFT, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Enter/release symbol letter key, remains in symbols.
        pressAndSlideFromKey('2', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Cancel sliding, switch back to symbols shifted.
        stopSlidingAndCancel(SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
    }

    // Change focus to new text field.
    public void testChangeFocus() {
        // Press/release shift key.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_UNSHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_UNSHIFTED);

        // Press/release "?123" key.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_UNSHIFTED);

        // Press/release "?123" key.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_UNSHIFTED);
    }

    // Change focus to auto caps text field.
    public void testChangeFocusAutoCaps() {
        // Set capitalize the first character of all words mode.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release shift key, enter alphabet.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release "?123" key.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);

        // Press/release "?123" key.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Change focus to new text field.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);
    }

    // Change orientation.
    public void testChangeOrientation() {
        // Alphabet -> rotate -> alphabet.
        updateShiftState(ALPHABET_UNSHIFTED);
        // Rotate device, remain in alphabet.
        rotateDevice(ALPHABET_UNSHIFTED);

        // Alphabet automatic shifted -> rotate -> automatic shifted.
        // Set capitalize the first character of all words mode.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Press/release auto caps trigger letter to enter alphabet automatic shifted.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);
        // Rotate device, remain in alphabet.
        rotateDevice(ALPHABET_AUTOMATIC_SHIFTED);
        setAutoCapsMode(CAP_MODE_OFF);
        // Press/release auto caps trigger letter to reset shift state.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_AUTOMATIC_SHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shifted -> rotate -> alphabet shifted.
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rotate device, remain in alphabet shifted.
        rotateDevice(ALPHABET_MANUAL_SHIFTED);

        // Alphabet shift locked -> rotate -> alphabet shift locked.
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Rotate device, remain in alphabet shift locked.
        rotateDevice(ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> symbols -> rotate -> symbols ->
        // Alphabet shift locked.
        // Press/release "?123" key, enter symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Rotate device, remain in symbols,
        rotateDevice(SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, alphabet shift locked state should be maintained.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> symbols shifted -> rotate -> symbols shifted ->
        // Alphabet shift locked.
        // Press/release "?123" key, enter symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Rotate device, remain in symbols shifted.
        rotateDevice(SYMBOLS_SHIFTED);
        // Press/release "ABC" key, alphabet shift locked state should be maintained.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> symbols shifted -> alphabet shift locked -> rotate ->
        // Alphabet shift locked -> symbols.
        // Press/release "?123" key, enter symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, enter alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Rotate device, remain in alphabet shift locked.
        rotateDevice(ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter symbols (not symbols shifted).
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Alphabet -> symbols shifted -> alphabet -> rotate ->
        // Alphabet -> symbols.
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Press/release "?123" key, enter symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, enter alphabet.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Rotate device, remain in alphabet shift locked.
        rotateDevice(ALPHABET_UNSHIFTED);
        // Press/release "?123" key, enter symbols (not symbols shifted).
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
    }

    // Rapidly type shift key.
    public void testRapidShiftTyping() {
        // Press/release shift key
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release shift key.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release shift key.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);

        // Press/release shift key to enter alphabet manual shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release shift key
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('j', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release shift key.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release shift key.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);

        // Long press shift key to enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Press/release shift key
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('j', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release shift key.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release shift key.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);

        // Set capitalize the first character of all words mode.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Press/release auto caps trigger letter to enter alphabet automatic shifted.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);
        // Press/release shift key
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('j', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release shift key.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
        // Rapidly press/release shift key.
        secondPressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Rapidly press/release letter key.
        secondPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
    }
}
