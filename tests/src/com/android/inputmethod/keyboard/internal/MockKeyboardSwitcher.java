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

import com.android.inputmethod.keyboard.internal.KeyboardState.SwitchActions;

public class MockKeyboardSwitcher implements KeyboardState.SwitchActions {
    private static final int ALPHABET_UNSHIFTED = 0;
    private static final int ALPHABET_MANUAL_SHIFTED = 1;
    private static final int ALPHABET_AUTOMATIC_SHIFTED = 2;
    private static final int ALPHABET_SHIFT_LOCKED = 3;
    private static final int SYMBOLS_UNSHIFTED = 4;
    private static final int SYMBOLS_SHIFTED = 5;

    private int mLayout = ALPHABET_UNSHIFTED;

    // TODO: Separate Auto Caps mode and Auto Caps state of input.
    private boolean mAutoCaps = KeyboardStateTests.NO_AUTO_CAPS;

    private final KeyboardState mState = new KeyboardState(this);

    public boolean assertAlphabetNormal() {
        return mLayout == ALPHABET_UNSHIFTED;
    }

    public boolean assertAlphabetManualShifted() {
        return mLayout == ALPHABET_MANUAL_SHIFTED;
    }

    public boolean assertAlphabetAutomaticShifted() {
        return mLayout == ALPHABET_AUTOMATIC_SHIFTED;
    }

    public boolean assertAlphabetShiftLocked() {
        return mLayout == ALPHABET_SHIFT_LOCKED;
    }

    public boolean assertSymbolsNormal() {
        return mLayout == SYMBOLS_UNSHIFTED;
    }

    public boolean assertSymbolsShifted() {
        return mLayout == SYMBOLS_SHIFTED;
    }

    public void setAutoCapsMode(boolean autoCaps) {
        mAutoCaps = autoCaps;
    }

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

    @Override
    public void requestUpdatingShiftState() {
        mState.onUpdateShiftState(mAutoCaps);
    }

    public void toggleCapsLock() {
        mState.onToggleCapsLock();
    }

    public void updateShiftState() {
        mState.onUpdateShiftState(mAutoCaps);
    }

    public void loadKeyboard(String layoutSwitchBackSymbols,
            boolean hasDistinctMultitouch) {
        mState.onLoadKeyboard(layoutSwitchBackSymbols, hasDistinctMultitouch);
    }

    public void onPressShift(boolean withSliding) {
        mState.onPressShift(withSliding);
    }

    public void onReleaseShift(boolean withSliding) {
        mState.onReleaseShift(withSliding);
    }

    public void onPressSymbol() {
        mState.onPressSymbol();
    }

    public void onReleaseSymbol() {
        mState.onReleaseSymbol();
    }

    public void onOtherKeyPressed() {
        mState.onOtherKeyPressed();
    }

    public void onCodeInput(int code, boolean isSinglePointer) {
        mState.onCodeInput(code, isSinglePointer, mAutoCaps);
    }

    public void onCancelInput(boolean isSinglePointer) {
        mState.onCancelInput(isSinglePointer);
    }
}