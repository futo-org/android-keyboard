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

public class KeyboardStateMultiTouchTests extends KeyboardStateTestsBase {
    // Chording input in alphabet.
    public void testChordingAlphabet() {
        // Press shift key and hold, enter into choring shift state.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
        // Press/release letter key.
        chordingPressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Release shift key, switch back to alphabet.
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);

        // Press "?123" key and hold, enter into choring symbols state.
        pressKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);
        // Press/release symbol letter key.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Release "ABC" key, switch back to alphabet.
        releaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
    }

    // Chording input in shift locked.
    public void testChordingShiftLocked() {
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);

        // Press shift key and hold, enter into choring shift state.
        pressKey(CODE_SHIFT, ALPHABET_SHIFT_LOCK_SHIFTED);
        // Press/release letter key.
        chordingPressAndReleaseKey('Z', ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_SHIFT_LOCK_SHIFTED);
        // Release shift key, switch back to alphabet shift locked.
        releaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCKED);

        // Press "?123" key and hold, enter into choring symbols state.
        pressKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);
        // Press/release symbol letter key.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Release "123?" key, switch back to alphabet shift locked.
        releaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED);
    }

    // Chording input in symbols.
    public void testChordingSymbols() {
        // Press/release "?123" key, enter symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press "=\<" key and hold, enter into choring symbols shifted state.
        pressKey(CODE_SHIFT, SYMBOLS_SHIFTED);
        // Press/release symbol letter key.
        chordingPressAndReleaseKey('1', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Release "=\<" key, switch back to symbols.
        releaseKey(CODE_SHIFT, SYMBOLS_UNSHIFTED);

        // Press "ABC" key and hold, enter into choring alphabet state.
        pressKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
        // Press/release letter key.
        chordingPressAndReleaseKey('a', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Release "ABC" key, switch back to symbols.
        releaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);

        // Alphabet shifted -> symbols -> "ABC" key + letter -> symbols
        // -> alphabet.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press "ABC" key, enter into chording alphabet state.
        pressKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
        // Enter/release letter key.
        chordingPressAndReleaseKey('a', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Release "ABC" key, switch back to symbols.
        releaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet (not alphabet shifted).
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shift locked -> symbols -> "ABC" key + letter -> symbols ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press "ABC" key, enter into chording alphabet shift locked.
        pressKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED);
        // Enter/release letter key.
        chordingPressAndReleaseKey('A', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Release "ABC" key, switch back to symbols.
        releaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> symbols -> "=\<" key + letter -> symbols ->
        // alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press "=\<" key, enter into symbols shifted chording state.
        pressKey(CODE_SHIFT, SYMBOLS_SHIFTED);
        // Enter/release symbols shift letter key.
        chordingPressAndReleaseKey('~', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Release "=\<" key, switch back to symbols.
        releaseKey(CODE_SHIFT, SYMBOLS_UNSHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
    }

    // Chording input in symbol shifted.
    public void testChordingSymbolsShifted() {
        // Press/release "?123" key, enter symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);

        // Press "?123" key and hold, enter into chording symbols state.
        pressKey(CODE_SHIFT, SYMBOLS_UNSHIFTED);
        // Press/release symbol letter key.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Release "=\<" key, switch back to symbols shifted state.
        releaseKey(CODE_SHIFT, SYMBOLS_SHIFTED);

        // Press "ABC" key and hold, enter into choring alphabet state.
        pressKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
        // Press/release letter key.
        chordingPressAndReleaseKey('a', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Release "ABC" key, switch back to symbols.
        releaseKey(CODE_SYMBOL, SYMBOLS_SHIFTED);

        // Alphabet shifted -> symbols shifted -> "ABC" key + letter -> symbols shifted ->
        // alphabet.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press "ABC" key, enter into chording alphabet state.
        pressKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
        // Enter/release letter key.
        chordingPressAndReleaseKey('a', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        // Release "ABC" key, switch back to symbols shifted.
        releaseKey(CODE_SYMBOL, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet (not alphabet shifted).
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Alphabet shift locked -> symbols shifted -> "ABC" key + letter -> symbols shifted
        // -> alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press "ABC" key, enter into chording alphabet shift locked.
        pressKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED);
        // Enter/release letter key.
        chordingPressAndReleaseKey('A', ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
        // Release "ABC" key, switch back to symbols shifted.
        releaseKey(CODE_SYMBOL, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);

        // Alphabet shift locked -> symbols shifted -> "=\<" key + letter -> symbols shifted
        // -> alphabet shift locked.
        // Load keyboard
        loadKeyboard(ALPHABET_UNSHIFTED);
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);
        // Press/release "?123" key, enter into symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release "=\<" key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        // Press "=\<" key, enter into symbols chording state.
        pressKey(CODE_SHIFT, SYMBOLS_UNSHIFTED);
        // Enter/release symbols letter key.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Release "=\<" key, switch back to symbols shifted.
        releaseKey(CODE_SHIFT, SYMBOLS_SHIFTED);
        // Press/release "ABC" key, switch to alphabet shift locked.
        pressAndReleaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED, ALPHABET_SHIFT_LOCKED);
    }

    // Chording input in automatic upper case.
    public void testChordingAutomaticUpperCase() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state with auto caps enabled.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);
        // Press shift key and hold, enter into chording shift state.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
        // Press/release letter key.
        chordingPressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Release shift key, switch back to alphabet.
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);

        // Update shift state with auto caps enabled.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);
        // Press "123?" key and hold, enter into chording symbols state.
        pressKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);
        // Press/release symbol letter key.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Release "123?" key, switch back to alphabet.
        releaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
    }
}
