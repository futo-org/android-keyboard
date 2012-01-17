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
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();

        // Press/release letter keys.
        mSwitcher.onOtherKeyPressed();
        mSwitcher.onCodeInput('Z', MULTI);
        assertAlphabetManualShifted();
        mSwitcher.onOtherKeyPressed();
        mSwitcher.onCodeInput('X', MULTI);
        assertAlphabetManualShifted();

        // Release shift key, snap back to normal state.
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        mSwitcher.onReleaseShift(NOT_SLIDING);
        mSwitcher.updateShiftState();
        assertAlphabetNormal();
    }

    // Symbols key chording input.
    public void testSymbolsChording() {
        // Press symbols key and hold, enter into choring shift state.
        mSwitcher.onPressSymbol();
        assertSymbolsNormal();

        // Press/release symbol letter keys.
        mSwitcher.onOtherKeyPressed();
        mSwitcher.onCodeInput('1', MULTI);
        assertSymbolsNormal();
        mSwitcher.onOtherKeyPressed();
        mSwitcher.onCodeInput('2', MULTI);
        assertSymbolsNormal();

        // Release shift key, snap back to normal state.
        mSwitcher.onCodeInput(Keyboard.CODE_SWITCH_ALPHA_SYMBOL, SINGLE);
        mSwitcher.onReleaseSymbol();
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
        mSwitcher.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Press/release letter keys.
        mSwitcher.onOtherKeyPressed();
        mSwitcher.onCodeInput('Z', MULTI);
        assertAlphabetManualShifted();
        // Release shift key, snap back to alphabet.
        mSwitcher.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        mSwitcher.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();
    }

    // Chording symbol key in automatic upper case.
    public void testAutomaticUpperCaseChrding2() {
        mSwitcher.setAutoCapsMode(AUTO_CAPS);
        // Update shift state with auto caps enabled.
        mSwitcher.updateShiftState();
        assertAlphabetAutomaticShifted();

        // Press "123?" key.
        mSwitcher.onPressSymbol();
        assertSymbolsNormal();
        // Press/release symbol letter keys.
        mSwitcher.onOtherKeyPressed();
        assertSymbolsNormal();
        mSwitcher.onCodeInput('1', MULTI);
        assertSymbolsNormal();
        // Release "123?" key, snap back to alphabet.
        mSwitcher.onCodeInput(Keyboard.CODE_SWITCH_ALPHA_SYMBOL, SINGLE);
        mSwitcher.onReleaseSymbol();
        assertAlphabetNormal();
    }

    // TODO: Multitouch test
}
