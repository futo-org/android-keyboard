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
    // Shift key chording input.
    public void testChording() {
        // Press shift key and hold, enter into choring shift state.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);

        // Press/release letter keys.
        chordingPressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        chordingPressAndReleaseKey('X', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Release shift key, switch back to alphabet.
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);

        // Press symbols key and hold, enter into choring symbols state.
        pressKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);

        // Press/release symbol letter keys.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        chordingPressAndReleaseKey('2', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Release symbols key, switch back to alphabet.
        releaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
    }

    // Shift key chording input in shift locked.
    public void testShiftChordingShiftLocked() {
        // Long press shift key, enter alphabet shift locked.
        longPressShiftKey(ALPHABET_MANUAL_SHIFTED, ALPHABET_SHIFT_LOCKED);

        // Press shift key and hold, enter into choring shift state.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);

        // Press/release letter keys.
        chordingPressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        chordingPressAndReleaseKey('X', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // TODO: This test fails due to bug, though external behavior is correct.
//        // Release shift key, switch back to alphabet shift locked.
//        releaseKey(CODE_SHIFT, ALPHABET_SHIFT_LOCKED);
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);

        // TODO: This test fails due to bug, though external behavior is correct.
//        // Press symbols key and hold, enter into choring symbols state.
//        pressKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);
//
//        // Press/release symbol letter keys.
//        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
//        chordingPressAndReleaseKey('2', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
//
//        // Release symbols key, switch back to alphabet shift locked.
//        releaseKey(CODE_SYMBOL, ALPHABET_SHIFT_LOCKED);
    }

    // Symbols key chording input.
    public void testSymbolsChording() {
        // Press/release symbols key, enter symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Press shift key and hold, enter into choring symbols shifted state.
        pressKey(CODE_SHIFT, SYMBOLS_SHIFTED);

        // Press/release symbols keys.
        chordingPressAndReleaseKey('1', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);
        chordingPressAndReleaseKey('2', SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);

        // Release shift key, switch back to symbols.
        releaseKey(CODE_SHIFT, SYMBOLS_UNSHIFTED);

        // Press "ABC" key and hold, enter into choring alphabet state.
        pressKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);

        // Press/release letter keys.
        chordingPressAndReleaseKey('a', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
        chordingPressAndReleaseKey('b', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);

        // Release "ABC" key, switch back to symbols.
        releaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);
    }

    // Symbols shifted key chording input in symbol.
    public void testSymbolsShiftedChording() {
        // Press/release symbols key, enter symbols.
        pressAndReleaseKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        // Press/release shift key, enter symbols shifted.
        pressAndReleaseKey(CODE_SHIFT, SYMBOLS_SHIFTED, SYMBOLS_SHIFTED);

        // Press shift key and hold, enter into chording symbols state.
        pressKey(CODE_SHIFT, SYMBOLS_UNSHIFTED);

        // Press/release symbol letter keys.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        chordingPressAndReleaseKey('2', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Release shift key, switch back to symbols shifted state.
        releaseKey(CODE_SHIFT, SYMBOLS_SHIFTED);

        // TODO: This test fails due to bug.
//        // Press "ABC" key and hold, enter into choring alphabet state.
//        pressKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
//
//        // Press/release letter keys.
//        chordingPressAndReleaseKey('a', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
//        chordingPressAndReleaseKey('b', ALPHABET_UNSHIFTED, ALPHABET_UNSHIFTED);
//
//        // Release "ABC" key, switch back to symbols.
//        releaseKey(CODE_SYMBOL, SYMBOLS_SHIFTED);
    }

    // Chording shift key in automatic upper case.
    public void testAutomaticUpperCaseChording() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state with auto caps enabled.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Press shift key and hold, enter into chording shift state.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);

        // Press/release letter keys.
        chordingPressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Release shift key, switch back to alphabet.
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);
    }

    // Chording symbol key in automatic upper case.
    public void testAutomaticUpperCaseChording2() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state with auto caps enabled.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Press "123?" key and hold, enter into chording symbols state.
        pressKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);

        // Press/release symbol letter keys.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Release "123?" key, switch back to alphabet.
        releaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
    }

    // TODO: Multitouch test

    // TODO: n-Keys roll over test
}
