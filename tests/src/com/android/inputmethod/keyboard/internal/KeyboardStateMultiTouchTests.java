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

    // Chording input in shifted.
    public void testChordingShifted() {
        // Press shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Press shift key and hold, enter into choring shift state.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
        // Press/release letter key.
        chordingPressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Release shift key, switch back to alphabet shifted.
        releaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);

        // Press "?123" key and hold, enter into choring symbols state.
        pressKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);
        // Press/release symbol letter key.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Release "123?" key, switch back to alphabet unshifted.
        releaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
    }

    // Chording input in shift locked.
    public void testChordingShiftLocked() {
        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);

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
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
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
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
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
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
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
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
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
        // Set capitalize the first character of all words mode.
        setAutoCapsMode(CAP_MODE_WORDS);

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

    // Chording letter key with shift key.
    public void testChordingLetterAndShiftKey() {
        // Press letter key and hold.
        pressKey('z', ALPHABET_UNSHIFTED);
        // Press shift key, {@link PointerTracker} will fire a phantom release letter key.
        chordingReleaseKey('z', ALPHABET_UNSHIFTED);
        chordingPressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
        // Press another letter key and hold.
        chordingPressAndReleaseKey('J', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Release shift key
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);
    }

    // Multi touch input in manual shifted.
    public void testMultiTouchManualShifted() {
        // Press/release shift key, enter into alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Press 'X' key and hold
        pressKey('X', ALPHABET_MANUAL_SHIFTED);
        // Press 'z' key and hold, switch back to alphabet unshifted.
        chordingPressKey('z', ALPHABET_UNSHIFTED);
        // Release 'X' key
        releaseKey('X', ALPHABET_UNSHIFTED);
        // Release 'z' key
        releaseKey('z', ALPHABET_UNSHIFTED);
    }

    // Multi touch input in automatic upper case.
    public void testMultiTouchAutomaticUpperCase() {
        // Set auto word caps mode on.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Update shift state with auto caps enabled.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);

        // Press 'X' key and hold
        pressKey('X', ALPHABET_AUTOMATIC_SHIFTED);
        // Press 'z' key and hold, switch back to alphabet unshifted.
        chordingPressKey('z', ALPHABET_UNSHIFTED);
        // Release 'X' key
        releaseKey('X', ALPHABET_UNSHIFTED);
        // Release 'z' key
        releaseKey('z', ALPHABET_UNSHIFTED);
    }

    // Multi touch input in capitalize character mode.
    public void testMultiTouchCapModeCharacter() {
        // Set auto character caps mode on.
        setAutoCapsMode(CAP_MODE_CHARACTERS);
        // Update shift state with auto caps enabled.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);

        // Press 'X' key and hold
        pressKey('X', ALPHABET_AUTOMATIC_SHIFTED);
        // Press 'Z' key and hold, stay in automatic shifted mode.
        chordingPressKey('Z', ALPHABET_AUTOMATIC_SHIFTED);
        // Release 'X' key
        releaseKey('X', ALPHABET_AUTOMATIC_SHIFTED);
        // Release 'Z' key
        releaseKey('Z', ALPHABET_AUTOMATIC_SHIFTED);
    }

    // Multi touch shift chording input in manual shifted.
    public void testMultiTouchShiftChordingManualShifted() {
        // Press/release shift key, enter into alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Press shift key and hold, stays in alphabet shifted.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
        // Press 'X' key and hold
        chordingPressKey('X', ALPHABET_MANUAL_SHIFTED);
        // Press 'Z' key and hold, stays in alphabet shifted.
        chordingPressKey('Z', ALPHABET_MANUAL_SHIFTED);
        // Release 'X' key
        releaseKey('X', ALPHABET_MANUAL_SHIFTED);
        // Release 'Z' key
        releaseKey('Z', ALPHABET_MANUAL_SHIFTED);
        // Release shift key, switch back to alphabet shifted.
        releaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
    }

    // Multi touch shift chording input in automatic upper case.
    public void testMultiTouchShiftChordingAutomaticUpperCase() {
        // Set auto word caps mode on.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Update shift state with auto caps enabled.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);

        // Press shift key and hold, switch to alphabet shifted.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
        // Press 'X' key and hold
        chordingPressKey('X', ALPHABET_MANUAL_SHIFTED);
        // Press 'Z' key and hold, stays in alphabet shifted.
        chordingPressKey('Z', ALPHABET_MANUAL_SHIFTED);
        // Release 'X' key
        releaseKey('X', ALPHABET_MANUAL_SHIFTED);
        // Release 'Z' key
        releaseKey('Z', ALPHABET_MANUAL_SHIFTED);
        // Release shift key, updated to alphabet unshifted.
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);

        // Update shift state with auto caps enabled.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);

        // Press shift key and hold, switch to alphabet shifted.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
        // Press 'X' key and hold
        chordingPressKey('X', ALPHABET_MANUAL_SHIFTED);
        // Release 'X' key
        releaseKey('X', ALPHABET_MANUAL_SHIFTED);
        // Press  key and hold, stays in alphabet shifted.
        chordingPressKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_MANUAL_SHIFTED);
        // Release 'Z' key
        releaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_MANUAL_SHIFTED);
        // Release shift key, updated to alphabet automatic shifted.
        releaseKey(CODE_SHIFT, ALPHABET_AUTOMATIC_SHIFTED);
    }

    // Multi touch shift chording input in capitalize character mode.
    public void testMultiTouchShiftChordingCapModeCharacter() {
        // Set auto character caps mode on.
        setAutoCapsMode(CAP_MODE_CHARACTERS);
        // Update shift state with auto caps enabled.
        pressAndReleaseKey(CODE_AUTO_CAPS_TRIGGER, ALPHABET_UNSHIFTED, ALPHABET_AUTOMATIC_SHIFTED);

        // Press shift key and hold, switch to alphabet shifted.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
        // Press 'X' key and hold
        chordingPressKey('X', ALPHABET_MANUAL_SHIFTED);
        // Press 'Z' key and hold, stay in automatic shifted mode.
        chordingPressKey('Z', ALPHABET_MANUAL_SHIFTED);
        // Release 'X' key
        releaseKey('X', ALPHABET_MANUAL_SHIFTED);
        // Release 'Z' key
        releaseKey('Z', ALPHABET_MANUAL_SHIFTED);
        // Release shift key, updated to alphabet automatic shifted.
        releaseKey(CODE_SHIFT, ALPHABET_AUTOMATIC_SHIFTED);
    }

    public void testLongPressShiftAndChording() {
        // Long press shift key, enter maybe shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release letter key, remain in manual shifted.
        chordingPressAndReleaseKey('A', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Release shift key, back to alphabet (not shift locked).
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);

        // Long press shift key, enter alphabet shift locked.
        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
                ALPHABET_SHIFT_LOCKED);
        // Long press shift key, enter maybe alphabet.
        longPressShiftKey(ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_SHIFT_LOCK_SHIFTED);
        // Press/release letter key, remain in manual shifted.
        chordingPressAndReleaseKey('A', ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_SHIFT_LOCK_SHIFTED);
        // Release shift key, back to shift locked (not alphabet).
        releaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCKED);
        // Long press shift key, enter alphabet
        longPressAndReleaseShiftKey(ALPHABET_SHIFT_LOCK_SHIFTED, ALPHABET_SHIFT_LOCK_SHIFTED,
                ALPHABET_UNSHIFTED);

        // Press/release shift key, enter alphabet shifted.
        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Long press shift key, enter maybe alphabet.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release letter key, remain in manual shifted.
        chordingPressAndReleaseKey('A', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Release shift key, back to alphabet shifted (not alphabet).
        releaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);

        // Set capitalize the first character of all words mode.
        setAutoCapsMode(CAP_MODE_WORDS);
        // Load keyboard, should be in automatic shifted.
        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);
        // Long press shift key, enter maybe shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Press/release letter key, remain in manual shifted.
        chordingPressAndReleaseKey('A', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        // Release shift key, back to alphabet (not shift locked).
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);
    }

    public void testDoubleTapShiftAndChording() {
        // TODO: The following tests fail due to bug. Temporarily commented.
//        // First shift key tap.
//        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
//        // Second shift key tap, maybe shift locked.
//        secondPressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
//        // Press/release letter key, remain in manual shifted.
//        chordingPressAndReleaseKey('A', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
//        // Release shift key, back to alphabet shifted (not shift locked).
//        releaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
//
//        // Long press shift key, enter alphabet shift locked.
//        longPressAndReleaseShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED,
//                ALPHABET_SHIFT_LOCKED);
//        // First shift key tap.
//        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
//        // Second shift key tap, maybe shift unlocked.
//        secondPressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
//        // Press/release letter key, remain in manual shifted.
//        chordingPressAndReleaseKey('A', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
//        // Release shift key, back to alphabet (not shift locked).
//        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);
//
//        // Set capitalize the first character of all words mode.
//        setAutoCapsMode(CAP_MODE_WORDS);
//        // Load keyboard, should be in automatic shifted.
//        loadKeyboard(ALPHABET_AUTOMATIC_SHIFTED);
//        // First shift key tap.
//        pressAndReleaseKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED, ALPHABET_UNSHIFTED);
//        // Second shift key tap, maybe shift locked.
//        secondPressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);
//        // Press/release letter key, remain in manual shifted.
//        chordingPressAndReleaseKey('A', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
//        // Release shift key, back to alphabet (not shift locked).
//        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);
    }
}
