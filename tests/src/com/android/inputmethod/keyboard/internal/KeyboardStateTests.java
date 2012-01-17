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

import com.android.inputmethod.keyboard.Keyboard;

public class KeyboardStateTests extends KeyboardStateNonDistinctTests {
    @Override
    public boolean hasDistinctMultitouch() {
        return true;
    }

    // Shift key chording input.
    public void testShiftChording() {
        // Press shift key and hold, enter into choring shift state.
        mSwitcher.onPressKey(Keyboard.CODE_SHIFT);
        assertAlphabetManualShifted();

        // Press/release letter keys.
        mSwitcher.onPressKey('Z');
        mSwitcher.onCodeInput('Z', MULTI);
        mSwitcher.onReleaseKey('Z');
        assertAlphabetManualShifted();
        mSwitcher.onPressKey('X');
        mSwitcher.onCodeInput('X', MULTI);
        mSwitcher.onReleaseKey('X');
        assertAlphabetManualShifted();

        // Release shift key, snap back to normal state.
        mSwitcher.onCodeInput(CODE_SHIFT);
        mSwitcher.onReleaseKey(CODE_SHIFT);
        mSwitcher.updateShiftState();
        assertAlphabetNormal();
    }

    // Symbols key chording input.
    public void testSymbolsChording() {
        // Press symbols key and hold, enter into choring shift state.
        mSwitcher.onPressKey(CODE_SYMBOL);
        assertSymbolsNormal();

        // Press/release symbol letter keys.
        mSwitcher.onPressKey('1');
        mSwitcher.onCodeInput('1', MULTI);
        mSwitcher.onReleaseKey('1');
        assertSymbolsNormal();
        mSwitcher.onPressKey('2');
        mSwitcher.onCodeInput('2', MULTI);
        mSwitcher.onReleaseKey('2');
        assertSymbolsNormal();

        // Release shift key, snap back to normal state.
        mSwitcher.onCodeInput(CODE_SYMBOL);
        mSwitcher.onReleaseKey(CODE_SYMBOL);
        mSwitcher.updateShiftState();
        assertAlphabetNormal();
    }

    // Chording shift key in automatic upper case.
    public void testAutomaticUpperCaseChording() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state with auto caps enabled.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();

        // Press shift key.
        mSwitcher.onPressKey(CODE_SHIFT);
        assertAlphabetManualShifted();
        // Press/release letter keys.
        mSwitcher.onPressKey('Z');
        mSwitcher.onCodeInput('Z', MULTI);
        mSwitcher.onReleaseKey('Z');
        assertAlphabetManualShifted();
        // Release shift key, snap back to alphabet.
        mSwitcher.onCodeInput(CODE_SHIFT);
        mSwitcher.onReleaseKey(CODE_SHIFT);
        assertAlphabetNormal();
    }

    // Chording symbol key in automatic upper case.
    public void testAutomaticUpperCaseChrding2() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state with auto caps enabled.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();

        // Press "123?" key.
        mSwitcher.onPressKey(CODE_SYMBOL);
        assertSymbolsNormal();
        // Press/release symbol letter keys.
        mSwitcher.onPressKey('1');
        assertSymbolsNormal();
        mSwitcher.onCodeInput('1', MULTI);
        mSwitcher.onReleaseKey('1');
        assertSymbolsNormal();
        // Release "123?" key, snap back to alphabet.
        mSwitcher.onCodeInput(CODE_SYMBOL);
        mSwitcher.onReleaseKey(CODE_SYMBOL);
        assertAlphabetNormal();
    }

    // TODO: Multitouch test
}
