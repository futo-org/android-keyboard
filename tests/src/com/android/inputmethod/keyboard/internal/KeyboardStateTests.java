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

import android.test.AndroidTestCase;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.internal.KeyboardState.SwitchActions;

public class KeyboardStateTests extends AndroidTestCase {
    private static final int ALPHABET_UNSHIFTED = 0;
    private static final int ALPHABET_MANUAL_SHIFTED = 1;
    private static final int ALPHABET_AUTOMATIC_SHIFTED = 2;
    private static final int ALPHABET_SHIFT_LOCKED = 3;
    private static final int SYMBOLS_UNSHIFTED = 4;
    private static final int SYMBOLS_SHIFTED = 5;

    static class KeyboardSwitcher implements KeyboardState.SwitchActions {
        public int mLayout = ALPHABET_UNSHIFTED;

        @Override
        public void setAlphabetKeyboard() {
            mLayout = ALPHABET_UNSHIFTED;
        }

        @Override
        public void setShifted(int shiftMode) {
            if (shiftMode == SwitchActions.UNSHIFT) {
                mLayout = ALPHABET_UNSHIFTED;
            } else if (shiftMode == SwitchActions.MANUAL_SHIFT) {
                mLayout = ALPHABET_MANUAL_SHIFTED;
            } else if (shiftMode == SwitchActions.AUTOMATIC_SHIFT) {
                mLayout = ALPHABET_AUTOMATIC_SHIFTED;
            }
        }

        @Override
        public void setShiftLocked(boolean shiftLocked) {
            if (shiftLocked) {
                mLayout = ALPHABET_SHIFT_LOCKED;
            } else {
                mLayout = ALPHABET_UNSHIFTED;
            }
        }

        @Override
        public void setSymbolsKeyboard() {
            mLayout = SYMBOLS_UNSHIFTED;
        }

        @Override
        public void setSymbolsShiftedKeyboard() {
            mLayout = SYMBOLS_SHIFTED;
        }
    }

    private KeyboardSwitcher mSwitcher;
    private KeyboardState mState;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSwitcher = new KeyboardSwitcher();
        mState = new KeyboardState(mSwitcher);

        final String layoutSwitchBackCharacter = "";
        // TODO: Unit tests for non-distinct multi touch device.
        final boolean hasDistinctMultitouch = true;
        mState.onLoadKeyboard(layoutSwitchBackCharacter, hasDistinctMultitouch);
    }

    // Argument for KeyboardState.onPressShift and onReleaseShift.
    private static final boolean NOT_SLIDING = false;
    private static final boolean SLIDING = true;
    // Argument for KeyboardState.onCodeInput.
    private static final boolean SINGLE = true;
    private static final boolean MULTI = false;


    private void assertAlphabetNormal() {
        assertEquals(ALPHABET_UNSHIFTED, mSwitcher.mLayout);
    }

    private void assertAlphabetManualShifted() {
        assertEquals(ALPHABET_MANUAL_SHIFTED, mSwitcher.mLayout);
    }

    private void assertAlphabetAutomaticShifted() {
        assertEquals(ALPHABET_AUTOMATIC_SHIFTED, mSwitcher.mLayout);
    }

    private void assertAlphabetShiftLocked() {
        assertEquals(ALPHABET_SHIFT_LOCKED, mSwitcher.mLayout);
    }

    private void assertSymbolsNormal() {
        assertEquals(SYMBOLS_UNSHIFTED, mSwitcher.mLayout);
    }

    private void assertSymbolsShifted() {
        assertEquals(SYMBOLS_SHIFTED, mSwitcher.mLayout);
    }

    // Initial state test.
    public void testLoadKeyboard() {
        assertAlphabetNormal();
    }

    // Shift key in alphabet mode.
    public void testShift() {
        // Press/release shift key.
        mState.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mState.onReleaseShift(NOT_SLIDING);
        assertAlphabetManualShifted();

        // Press/release shift key.
        mState.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mState.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();

        // TODO: Sliding test
    }

    // Switching between alphabet and symbols.
    public void testAlphabetAndSymbols() {
        // Press/release "?123" key.
        mState.onPressSymbol();
        assertSymbolsNormal();
        mState.onReleaseSymbol();
        assertSymbolsNormal();

        // Press/release "ABC" key.
        mState.onPressSymbol();
        assertAlphabetNormal();
        mState.onReleaseSymbol();
        assertAlphabetNormal();

        // TODO: Sliding test
        // TODO: Snap back test
    }

    // Switching between symbols and symbols shifted.
    public void testSymbolsAndSymbolsShifted() {
        // Press/release "?123" key.
        mState.onPressSymbol();
        assertSymbolsNormal();
        mState.onReleaseSymbol();
        assertSymbolsNormal();

        // Press/release "=\<" key.
        mState.onPressShift(NOT_SLIDING);
        assertSymbolsShifted();
        mState.onReleaseShift(NOT_SLIDING);
        assertSymbolsShifted();

        // Press/release "ABC" key.
        mState.onPressSymbol();
        assertAlphabetNormal();
        mState.onReleaseSymbol();
        assertAlphabetNormal();

        // TODO: Sliding test
        // TODO: Snap back test
    }

    // Automatic upper case test
    public void testAutomaticUpperCase() {
        // Update shift state with auto caps enabled.
        mState.onUpdateShiftState(true);
        assertAlphabetAutomaticShifted();

        // Press shift key.
        mState.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Release shift key.
        mState.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();

        // TODO: Chording test.
    }

    // TODO: UpdateShiftState with shift locked, etc.

    // TODO: Multitouch test

    // TODO: Change focus test.

    // TODO: Change orientation test.

    // Long press shift key.
    // TODO: Move long press recognizing timer/logic into KeyboardState.
    public void testLongPressShift() {
        // Long press shift key
        mState.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Long press recognized in LatinKeyboardView.KeyTimerHandler.
        mState.onToggleCapsLock();
        assertAlphabetShiftLocked();
        mState.onCodeInput(Keyboard.CODE_CAPSLOCK, SINGLE);
        assertAlphabetShiftLocked();
        mState.onReleaseShift(NOT_SLIDING);
        assertAlphabetShiftLocked();

        // Long press shift key.
        mState.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Long press recognized in LatinKeyboardView.KeyTimerHandler.
        mState.onToggleCapsLock();
        assertAlphabetNormal();
        mState.onCodeInput(Keyboard.CODE_CAPSLOCK, SINGLE);
        assertAlphabetNormal();
        mState.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();
    }

    // Double tap shift key.
    // TODO: Move double tap recognizing timer/logic into KeyboardState.
    public void testDoubleTapShift() {
        // First shift key tap.
        mState.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mState.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetManualShifted();
        mState.onReleaseShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        // Second shift key tap.
        // Double tap recognized in LatinKeyboardView.KeyTimerHandler.
        mState.onToggleCapsLock();
        assertAlphabetShiftLocked();
        mState.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetShiftLocked();

        // First shift key tap.
        mState.onPressShift(NOT_SLIDING);
        assertAlphabetManualShifted();
        mState.onCodeInput(Keyboard.CODE_SHIFT, SINGLE);
        assertAlphabetManualShifted();
        mState.onReleaseShift(NOT_SLIDING);
        assertAlphabetNormal();
        // Second shift key tap.
        // Second tap is ignored in LatinKeyboardView.KeyTimerHandler.
    }
}
