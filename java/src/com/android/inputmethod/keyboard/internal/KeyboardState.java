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

// TODO: Add unit tests
public class KeyboardState {
    private KeyboardShiftState mKeyboardShiftState = new KeyboardShiftState();

    // TODO: Combine these key state objects with auto mode switch state.
    private ShiftKeyState mShiftKeyState = new ShiftKeyState("Shift");
    private ModifierKeyState mSymbolKeyState = new ModifierKeyState("Symbol");

    public KeyboardState() {
    }

    public void onLoadKeyboard() {
        mKeyboardShiftState.setShifted(false);
        mKeyboardShiftState.setShiftLocked(false);
        mShiftKeyState.onRelease();
        mSymbolKeyState.onRelease();
    }

    public boolean isShiftLocked() {
        return mKeyboardShiftState.isShiftLocked();
    }

    public boolean isShiftLockShifted() {
        return mKeyboardShiftState.isShiftLockShifted();
    }

    public boolean isShiftedOrShiftLocked() {
        return mKeyboardShiftState.isShiftedOrShiftLocked();
    }

    public boolean isAutomaticTemporaryUpperCase() {
        return mKeyboardShiftState.isAutomaticTemporaryUpperCase();
    }

    public boolean isManualTemporaryUpperCase() {
        return mKeyboardShiftState.isManualTemporaryUpperCase();
    }

    public boolean isManualTemporaryUpperCaseFromAuto() {
        return mKeyboardShiftState.isManualTemporaryUpperCaseFromAuto();
    }

    // TODO: Get rid of this method
    public void setShifted(boolean shifted) {
        mKeyboardShiftState.setShifted(shifted);
    }

    // TODO: Get rid of this method
    public void setShiftLocked(boolean shiftLocked) {
        mKeyboardShiftState.setShiftLocked(shiftLocked);
    }

    // TODO: Get rid of this method
    public void setAutomaticTemporaryUpperCase() {
        mKeyboardShiftState.setAutomaticTemporaryUpperCase();
    }

    // TODO: Get rid of this method
    public boolean isShiftKeyIgnoring() {
        return mShiftKeyState.isIgnoring();
    }

    // TODO: Get rid of this method
    public boolean isShiftKeyReleasing() {
        return mShiftKeyState.isReleasing();
    }

    // TODO: Get rid of this method
    public boolean isShiftKeyMomentary() {
        return mShiftKeyState.isMomentary();
    }

    // TODO: Get rid of this method
    public boolean isShiftKeyPressing() {
        return mShiftKeyState.isPressing();
    }

    // TODO: Get rid of this method
    public boolean isShiftKeyPressingOnShifted() {
        return mShiftKeyState.isPressingOnShifted();
    }

    public void onToggleCapsLock() {
        mShiftKeyState.onRelease();
    }

    public void onPressSymbol() {
        mSymbolKeyState.onPress();
    }

    public void onReleaseSymbol() {
        mSymbolKeyState.onRelease();
    }

    public void onOtherKeyPressed() {
        mShiftKeyState.onOtherKeyPressed();
        mSymbolKeyState.onOtherKeyPressed();
    }

    public void onUpdateShiftState(boolean isAlphabetMode) {
        if (!isAlphabetMode) {
            // In symbol keyboard mode, we should clear shift key state because only alphabet
            // keyboard has shift key.
            mSymbolKeyState.onRelease();
        }
    }

    // TODO: Get rid of these boolean arguments.
    public void onPressShift(boolean isAlphabetMode, boolean isShiftLocked,
            boolean isAutomaticTemporaryUpperCase, boolean isShiftedOrShiftLocked) {
        if (isAlphabetMode) {
            if (isShiftLocked) {
                // Shift key is pressed while caps lock state, we will treat this state as shifted
                // caps lock state and mark as if shift key pressed while normal state.
                mShiftKeyState.onPress();
            } else if (isAutomaticTemporaryUpperCase) {
                // Shift key is pressed while automatic temporary upper case, we have to move to
                // manual temporary upper case.
                mShiftKeyState.onPress();
            } else if (isShiftedOrShiftLocked) {
                // In manual upper case state, we just record shift key has been pressing while
                // shifted state.
                mShiftKeyState.onPressOnShifted();
            } else {
                // In base layout, chording or manual temporary upper case mode is started.
                mShiftKeyState.onPress();
            }
        } else {
            // In symbol mode, just toggle symbol and symbol more keyboard.
            mShiftKeyState.onPress();
        }
    }

    public void onReleaseShift() {
        mShiftKeyState.onRelease();
    }

    @Override
    public String toString() {
        return "[keyboard=" + mKeyboardShiftState
                + " shift=" + mShiftKeyState
                + " symbol=" + mSymbolKeyState + "]";
    }
}
