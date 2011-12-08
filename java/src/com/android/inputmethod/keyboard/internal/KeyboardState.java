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

import android.text.TextUtils;

import com.android.inputmethod.keyboard.Keyboard;

// TODO: Add unit tests
public class KeyboardState {
    public interface SwitchActions {
        public void setAlphabetKeyboard();
        public static final int UNSHIFT = 0;
        public static final int MANUAL_SHIFT = 1;
        public static final int AUTOMATIC_SHIFT = 2;
        public void setShifted(int shiftMode);
        public void setShiftLocked(boolean shiftLocked);
        public void setSymbolsKeyboard();
        public void setSymbolsShiftedKeyboard();
    }

    private KeyboardShiftState mKeyboardShiftState = new KeyboardShiftState();

    private ShiftKeyState mShiftKeyState = new ShiftKeyState("Shift");
    private ModifierKeyState mSymbolKeyState = new ModifierKeyState("Symbol");

    private static final int SWITCH_STATE_ALPHA = 0;
    private static final int SWITCH_STATE_SYMBOL_BEGIN = 1;
    private static final int SWITCH_STATE_SYMBOL = 2;
    // The following states are used only on the distinct multi-touch panel devices.
    private static final int SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL = 3;
    private static final int SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE = 4;
    private static final int SWITCH_STATE_CHORDING_ALPHA = 5;
    private static final int SWITCH_STATE_CHORDING_SYMBOL = 6;
    private int mSwitchState = SWITCH_STATE_ALPHA;

    private String mLayoutSwitchBackSymbols;

    private final SwitchActions mSwitchActions;

    public KeyboardState(SwitchActions switchActions) {
        mSwitchActions = switchActions;
    }

    public void onLoadKeyboard(String layoutSwitchBackSymbols) {
        mLayoutSwitchBackSymbols = layoutSwitchBackSymbols;
        mKeyboardShiftState.setShifted(false);
        mKeyboardShiftState.setShiftLocked(false);
        mShiftKeyState.onRelease();
        mSymbolKeyState.onRelease();
    }

    // TODO: Get rid of this method
    public void onSetKeyboard(boolean isAlphabetMode) {
        mSwitchState = isAlphabetMode ? SWITCH_STATE_ALPHA : SWITCH_STATE_SYMBOL_BEGIN;
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

    private void toggleAlphabetAndSymbols(boolean isAlphabetMode) {
        if (isAlphabetMode) {
            mSwitchActions.setSymbolsKeyboard();
        } else {
            mSwitchActions.setAlphabetKeyboard();
        }
    }

    private void toggleShiftInSymbols(boolean isSymbolShifted) {
        if (isSymbolShifted) {
            mSwitchActions.setSymbolsKeyboard();
        } else {
            mSwitchActions.setSymbolsShiftedKeyboard();
        }
    }

    public void onReleaseCapsLock() {
        mShiftKeyState.onRelease();
    }

    // TODO: Get rid of isAlphabetMode argument.
    public void onPressSymbol(boolean isAlphabetMode) {
        toggleAlphabetAndSymbols(isAlphabetMode);
        mSymbolKeyState.onPress();
        mSwitchState = SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL;
    }

    // TODO: Get rid of isAlphabetMode argument.
    public void onReleaseSymbol(boolean isAlphabetMode) {
        // Snap back to the previous keyboard mode if the user chords the mode change key and
        // another key, then releases the mode change key.
        if (mSwitchState == SWITCH_STATE_CHORDING_ALPHA) {
            toggleAlphabetAndSymbols(isAlphabetMode);
        }
        mSymbolKeyState.onRelease();
    }

    public void onOtherKeyPressed() {
        mShiftKeyState.onOtherKeyPressed();
        mSymbolKeyState.onOtherKeyPressed();
    }

    // TODO: Get rid of isAlphabetMode argument.
    public void onUpdateShiftState(boolean isAlphabetMode, boolean autoCaps) {
        if (isAlphabetMode) {
            if (!isShiftLocked() && !mShiftKeyState.isIgnoring()) {
                if (mShiftKeyState.isReleasing() && autoCaps) {
                    // Only when shift key is releasing, automatic temporary upper case will be set.
                    mSwitchActions.setShifted(SwitchActions.AUTOMATIC_SHIFT);
                } else {
                    mSwitchActions.setShifted(mShiftKeyState.isMomentary()
                            ? SwitchActions.MANUAL_SHIFT : SwitchActions.UNSHIFT);
                }
            }
        } else {
            // In symbol keyboard mode, we should clear shift key state because only alphabet
            // keyboard has shift key.
            mSymbolKeyState.onRelease();
        }
    }

    // TODO: Get rid of isAlphabetMode and isSymbolShifted arguments.
    public void onPressShift(boolean isAlphabetMode, boolean isSymbolShifted) {
        if (isAlphabetMode) {
            if (isShiftLocked()) {
                // Shift key is pressed while caps lock state, we will treat this state as shifted
                // caps lock state and mark as if shift key pressed while normal state.
                mSwitchActions.setShifted(SwitchActions.MANUAL_SHIFT);
                mShiftKeyState.onPress();
            } else if (isAutomaticTemporaryUpperCase()) {
                // Shift key is pressed while automatic temporary upper case, we have to move to
                // manual temporary upper case.
                mSwitchActions.setShifted(SwitchActions.MANUAL_SHIFT);
                mShiftKeyState.onPress();
            } else if (isShiftedOrShiftLocked()) {
                // In manual upper case state, we just record shift key has been pressing while
                // shifted state.
                mShiftKeyState.onPressOnShifted();
            } else {
                // In base layout, chording or manual temporary upper case mode is started.
                mSwitchActions.setShifted(SwitchActions.MANUAL_SHIFT);
                mShiftKeyState.onPress();
            }
        } else {
            // In symbol mode, just toggle symbol and symbol more keyboard.
            toggleShiftInSymbols(isSymbolShifted);
            mSwitchState = SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE;
            mShiftKeyState.onPress();
        }
    }

    // TODO: Get rid of isAlphabetMode and isSymbolShifted arguments.
    public void onReleaseShift(boolean isAlphabetMode, boolean isSymbolShifted,
            boolean withSliding) {
        if (isAlphabetMode) {
            final boolean isShiftLocked = isShiftLocked();
            if (mShiftKeyState.isMomentary()) {
                // After chording input while normal state.
                mSwitchActions.setShifted(SwitchActions.UNSHIFT);
            } else if (isShiftLocked && !isShiftLockShifted() && (mShiftKeyState.isPressing()
                    || mShiftKeyState.isPressingOnShifted()) && !withSliding) {
                // Shift has been long pressed, ignore this release.
            } else if (isShiftLocked && !mShiftKeyState.isIgnoring() && !withSliding) {
                // Shift has been pressed without chording while caps lock state.
                mSwitchActions.setShiftLocked(false);
            } else if (isShiftedOrShiftLocked() && mShiftKeyState.isPressingOnShifted()
                    && !withSliding) {
                // Shift has been pressed without chording while shifted state.
                mSwitchActions.setShifted(SwitchActions.UNSHIFT);
            } else if (isManualTemporaryUpperCaseFromAuto() && mShiftKeyState.isPressing()
                    && !withSliding) {
                // Shift has been pressed without chording while manual temporary upper case
                // transited from automatic temporary upper case.
                mSwitchActions.setShifted(SwitchActions.UNSHIFT);
            }
        } else {
            // In symbol mode, snap back to the previous keyboard mode if the user chords the shift
            // key and another key, then releases the shift key.
            if (mSwitchState == SWITCH_STATE_CHORDING_SYMBOL) {
                toggleShiftInSymbols(isSymbolShifted);
            }
        }
        mShiftKeyState.onRelease();
    }

    // TODO: Get rid of isAlphabetMode and isSymbolShifted arguments.
    public void onCancelInput(boolean isAlphabetMode, boolean isSymbolShifted,
            boolean isSinglePointer) {
        // Snap back to the previous keyboard mode if the user cancels sliding input.
        if (isSinglePointer) {
            if (mSwitchState == SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL) {
                toggleAlphabetAndSymbols(isAlphabetMode);
            } else if (mSwitchState == SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE) {
                toggleShiftInSymbols(isSymbolShifted);
            }
        }
    }

    public boolean isInMomentarySwitchState() {
        return mSwitchState == SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL
                || mSwitchState == SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE;
    }

    private static boolean isSpaceCharacter(int c) {
        return c == Keyboard.CODE_SPACE || c == Keyboard.CODE_ENTER;
    }

    private boolean isLayoutSwitchBackCharacter(int c) {
        if (TextUtils.isEmpty(mLayoutSwitchBackSymbols)) return false;
        if (mLayoutSwitchBackSymbols.indexOf(c) >= 0) return true;
        return false;
    }

    // TODO: Get rid of isAlphabetMode and isSymbolShifted arguments.
    public void onCodeInput(boolean isAlphabetMode, boolean isSymbolShifted, int code,
            boolean isSinglePointer) {
        switch (mSwitchState) {
        case SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL:
            // Only distinct multi touch devices can be in this state.
            // On non-distinct multi touch devices, mode change key is handled by
            // {@link LatinIME#onCodeInput}, not by {@link LatinIME#onPress} and
            // {@link LatinIME#onRelease}. So, on such devices, {@link #mSwitchState} starts
            // from {@link #SWITCH_STATE_SYMBOL_BEGIN}, or {@link #SWITCH_STATE_ALPHA}, not from
            // {@link #SWITCH_STATE_MOMENTARY}.
            if (code == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
                // Detected only the mode change key has been pressed, and then released.
                if (isAlphabetMode) {
                    mSwitchState = SWITCH_STATE_ALPHA;
                } else {
                    mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
                }
            } else if (isSinglePointer) {
                // Snap back to the previous keyboard mode if the user pressed the mode change key
                // and slid to other key, then released the finger.
                // If the user cancels the sliding input, snapping back to the previous keyboard
                // mode is handled by {@link #onCancelInput}.
                toggleAlphabetAndSymbols(isAlphabetMode);
            } else {
                // Chording input is being started. The keyboard mode will be snapped back to the
                // previous mode in {@link onReleaseSymbol} when the mode change key is released.
                mSwitchState = SWITCH_STATE_CHORDING_ALPHA;
            }
            break;
        case SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE:
            if (code == Keyboard.CODE_SHIFT) {
                // Detected only the shift key has been pressed on symbol layout, and then released.
                mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
            } else if (isSinglePointer) {
                // Snap back to the previous keyboard mode if the user pressed the shift key on
                // symbol mode and slid to other key, then released the finger.
                toggleShiftInSymbols(isSymbolShifted);
                mSwitchState = SWITCH_STATE_SYMBOL;
            } else {
                // Chording input is being started. The keyboard mode will be snapped back to the
                // previous mode in {@link onReleaseShift} when the shift key is released.
                mSwitchState = SWITCH_STATE_CHORDING_SYMBOL;
            }
            break;
        case SWITCH_STATE_SYMBOL_BEGIN:
            if (!isSpaceCharacter(code) && code >= 0) {
                mSwitchState = SWITCH_STATE_SYMBOL;
            }
            // Snap back to alpha keyboard mode immediately if user types a quote character.
            if (isLayoutSwitchBackCharacter(code)) {
                mSwitchActions.setAlphabetKeyboard();
            }
            break;
        case SWITCH_STATE_SYMBOL:
        case SWITCH_STATE_CHORDING_SYMBOL:
            // Snap back to alpha keyboard mode if user types one or more non-space/enter
            // characters followed by a space/enter or a quote character.
            if (isSpaceCharacter(code) || isLayoutSwitchBackCharacter(code)) {
                mSwitchActions.setAlphabetKeyboard();
            }
            break;
        }
    }

    private static String switchStateToString(int switchState) {
        switch (switchState) {
        case SWITCH_STATE_ALPHA: return "ALPHA";
        case SWITCH_STATE_SYMBOL_BEGIN: return "SYMBOL-BEGIN";
        case SWITCH_STATE_SYMBOL: return "SYMBOL";
        case SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL: return "MOMENTARY-ALPHA-SYMBOL";
        case SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE: return "MOMENTARY-SYMBOL-MORE";
        case SWITCH_STATE_CHORDING_ALPHA: return "CHORDING-ALPHA";
        case SWITCH_STATE_CHORDING_SYMBOL: return "CHORDING-SYMBOL";
        default: return null;
        }
    }

    @Override
    public String toString() {
        return "[keyboard=" + mKeyboardShiftState
                + " shift=" + mShiftKeyState
                + " symbol=" + mSymbolKeyState
                + " switch=" + switchStateToString(mSwitchState) + "]";
    }
}
