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
    public void testShiftChording() {
        // Press shift key and hold, enter into choring shift state.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);

        // Press/release letter keys.
        chordingPressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);
        chordingPressAndReleaseKey('X', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Release shift key, snap back to normal state.
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);
    }

    // Symbols key chording input.
    public void testSymbolsChording() {
        // Press symbols key and hold, enter into choring shift state.
        pressKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);

        // Press/release symbol letter keys.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);
        chordingPressAndReleaseKey('2', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Release shift key, snap back to normal state.
        releaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
    }

    // Chording shift key in automatic upper case.
    public void testAutomaticUpperCaseChording() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state with auto caps enabled.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Press shift key.
        pressKey(CODE_SHIFT, ALPHABET_MANUAL_SHIFTED);

        // Press/release letter keys.
        chordingPressAndReleaseKey('Z', ALPHABET_MANUAL_SHIFTED, ALPHABET_MANUAL_SHIFTED);

        // Release shift key, snap back to alphabet.
        releaseKey(CODE_SHIFT, ALPHABET_UNSHIFTED);
    }

    // Chording symbol key in automatic upper case.
    public void testAutomaticUpperCaseChording2() {
        // Set auto caps mode on.
        setAutoCapsMode(AUTO_CAPS);

        // Update shift state with auto caps enabled.
        updateShiftState(ALPHABET_AUTOMATIC_SHIFTED);

        // Press "123?" key.
        pressKey(CODE_SYMBOL, SYMBOLS_UNSHIFTED);

        // Press/release symbol letter keys.
        chordingPressAndReleaseKey('1', SYMBOLS_UNSHIFTED, SYMBOLS_UNSHIFTED);

        // Release "123?" key, snap back to alphabet.
        releaseKey(CODE_SYMBOL, ALPHABET_UNSHIFTED);
    }

    // TODO: Multitouch test

    // TODO: n-Keys roll over test
}
