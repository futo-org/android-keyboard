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
import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;

/**
 * Keyboard state machine.
 *
 * This class contains all keyboard state transition logic.
 *
 * The input events are {@link #onLoadKeyboard(String)}, {@link #onSaveKeyboardState()},
 * {@link #onPressKey(int)}, {@link #onReleaseKey(int, boolean)},
 * {@link #onCodeInput(int, boolean, boolean)}, {@link #onCancelInput(boolean)},
 * {@link #onUpdateShiftState(boolean)}.
 *
 * The actions are {@link SwitchActions}'s methods.
 */
public class KeyboardState {
    private static final String TAG = KeyboardState.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_ACTION = false;

    public interface SwitchActions {
        public void setAlphabetKeyboard();
        public void setAlphabetManualShiftedKeyboard();
        public void setAlphabetAutomaticShiftedKeyboard();
        public void setAlphabetShiftLockedKeyboard();
        public void setSymbolsKeyboard();
        public void setSymbolsShiftedKeyboard();

        /**
         * Request to call back {@link KeyboardState#onUpdateShiftState(boolean)}.
         */
        public void requestUpdatingShiftState();
    }

    private final SwitchActions mSwitchActions;

    private ShiftKeyState mShiftKeyState = new ShiftKeyState("Shift");
    private ModifierKeyState mSymbolKeyState = new ModifierKeyState("Symbol");

    // TODO: Merge {@link #mSwitchState}, {@link #mIsAlphabetMode}, {@link #mAlphabetShiftState},
    // {@link #mIsSymbolShifted}, {@link #mPrevMainKeyboardWasShiftLocked}, and
    // {@link #mPrevSymbolsKeyboardWasShifted} into single state variable.
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

    private boolean mIsAlphabetMode;
    private KeyboardShiftState mAlphabetShiftState = new KeyboardShiftState();
    private boolean mIsSymbolShifted;
    private boolean mPrevMainKeyboardWasShiftLocked;
    private boolean mPrevSymbolsKeyboardWasShifted;

    private final SavedKeyboardState mSavedKeyboardState = new SavedKeyboardState();

    static class SavedKeyboardState {
        public boolean mIsValid;
        public boolean mIsAlphabetMode;
        public boolean mIsShiftLocked;
        public boolean mIsShifted;

        @Override
        public String toString() {
            if (!mIsValid) return "INVALID";
            if (mIsAlphabetMode) {
                if (mIsShiftLocked) return "ALPHABET_SHIFT_LOCKED";
                return mIsShifted ? "ALPHABET_SHIFTED" : "ALPHABET";
            } else {
                return mIsShifted ? "SYMBOLS_SHIFTED" : "SYMBOLS";
            }
        }
    }

    public KeyboardState(SwitchActions switchActions) {
        mSwitchActions = switchActions;
    }

    public void onLoadKeyboard(String layoutSwitchBackSymbols) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onLoadKeyboard: " + this);
        }
        mLayoutSwitchBackSymbols = layoutSwitchBackSymbols;
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false);
        mPrevMainKeyboardWasShiftLocked = false;
        mPrevSymbolsKeyboardWasShifted = false;
        mShiftKeyState.onRelease();
        mSymbolKeyState.onRelease();
        onRestoreKeyboardState();
    }

    public void onSaveKeyboardState() {
        final SavedKeyboardState state = mSavedKeyboardState;
        state.mIsAlphabetMode = mIsAlphabetMode;
        if (mIsAlphabetMode) {
            state.mIsShiftLocked = mAlphabetShiftState.isShiftLocked();
            state.mIsShifted = !state.mIsShiftLocked
                    && mAlphabetShiftState.isShiftedOrShiftLocked();
        } else {
            state.mIsShiftLocked = false;
            state.mIsShifted = mIsSymbolShifted;
        }
        state.mIsValid = true;
        if (DEBUG_EVENT) {
            Log.d(TAG, "onSaveKeyboardState: saved=" + state + " " + this);
        }
    }

    private void onRestoreKeyboardState() {
        final SavedKeyboardState state = mSavedKeyboardState;
        if (DEBUG_EVENT) {
            Log.d(TAG, "onRestoreKeyboardState: saved=" + state + " " + this);
        }
        if (!state.mIsValid || state.mIsAlphabetMode) {
            setAlphabetKeyboard();
        } else {
            if (state.mIsShifted) {
                setSymbolsShiftedKeyboard();
            } else {
                setSymbolsKeyboard();
            }
        }

        if (!state.mIsValid) return;
        state.mIsValid = false;

        if (state.mIsAlphabetMode) {
            setShiftLocked(state.mIsShiftLocked);
            if (!state.mIsShiftLocked) {
                setShifted(state.mIsShifted ? MANUAL_SHIFT : UNSHIFT);
            }
        }
    }

    // TODO: Remove this method.
    public boolean isShiftLocked() {
        return mAlphabetShiftState.isShiftLocked();
    }

    private static final int UNSHIFT = 0;
    private static final int MANUAL_SHIFT = 1;
    private static final int AUTOMATIC_SHIFT = 2;

    private void setShifted(int shiftMode) {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setShifted: shiftMode=" + shiftModeToString(shiftMode) + " " + this);
        }
        if (!mIsAlphabetMode) return;
        final int prevShiftMode;
        if (mAlphabetShiftState.isAutomaticTemporaryUpperCase()) {
            prevShiftMode = AUTOMATIC_SHIFT;
        } else if (mAlphabetShiftState.isManualTemporaryUpperCase()) {
            prevShiftMode = MANUAL_SHIFT;
        } else {
            prevShiftMode = UNSHIFT;
        }
        switch (shiftMode) {
        case AUTOMATIC_SHIFT:
            mAlphabetShiftState.setAutomaticTemporaryUpperCase();
            if (shiftMode != prevShiftMode) {
                mSwitchActions.setAlphabetAutomaticShiftedKeyboard();
            }
            break;
        case MANUAL_SHIFT:
            mAlphabetShiftState.setShifted(true);
            if (shiftMode != prevShiftMode) {
                mSwitchActions.setAlphabetManualShiftedKeyboard();
            }
            break;
        case UNSHIFT:
            mAlphabetShiftState.setShifted(false);
            if (shiftMode != prevShiftMode) {
                mSwitchActions.setAlphabetKeyboard();
            }
            break;
        }
    }

    private void setShiftLocked(boolean shiftLocked) {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setShiftLocked: shiftLocked=" + shiftLocked + " " + this);
        }
        if (!mIsAlphabetMode) return;
        if (shiftLocked && (!mAlphabetShiftState.isShiftLocked()
                || mAlphabetShiftState.isShiftLockShifted())) {
            mSwitchActions.setAlphabetShiftLockedKeyboard();
        }
        if (!shiftLocked && mAlphabetShiftState.isShiftLocked()) {
            mSwitchActions.setAlphabetKeyboard();
        }
        mAlphabetShiftState.setShiftLocked(shiftLocked);
    }

    private void toggleAlphabetAndSymbols() {
        if (mIsAlphabetMode) {
            setSymbolsKeyboard();
        } else {
            setAlphabetKeyboard();
        }
    }

    private void toggleShiftInSymbols() {
        if (mIsSymbolShifted) {
            setSymbolsKeyboard();
        } else {
            setSymbolsShiftedKeyboard();
        }
    }

    private void setAlphabetKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setAlphabetKeyboard");
        }
        mPrevSymbolsKeyboardWasShifted = mIsSymbolShifted;
        mSwitchActions.setAlphabetKeyboard();
        mIsAlphabetMode = true;
        mIsSymbolShifted = false;
        mSwitchState = SWITCH_STATE_ALPHA;
        setShiftLocked(mPrevMainKeyboardWasShiftLocked);
        mPrevMainKeyboardWasShiftLocked = false;
        mSwitchActions.requestUpdatingShiftState();
    }

    // TODO: Make this method private
    public void setSymbolsKeyboard() {
        mPrevMainKeyboardWasShiftLocked = mAlphabetShiftState.isShiftLocked();
        if (mPrevSymbolsKeyboardWasShifted) {
            setSymbolsShiftedKeyboard();
            return;
        }

        if (DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsKeyboard");
        }
        mSwitchActions.setSymbolsKeyboard();
        mIsAlphabetMode = false;
        mIsSymbolShifted = false;
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false);
        mPrevSymbolsKeyboardWasShifted = false;
        mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
    }

    private void setSymbolsShiftedKeyboard() {
        if (DEBUG_ACTION) {
            Log.d(TAG, "setSymbolsShiftedKeyboard");
        }
        mSwitchActions.setSymbolsShiftedKeyboard();
        mIsAlphabetMode = false;
        mIsSymbolShifted = true;
        // Reset alphabet shift state.
        mAlphabetShiftState.setShiftLocked(false);
        mPrevSymbolsKeyboardWasShifted = false;
        mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
    }

    public void onPressKey(int code) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onPressKey: code=" + Keyboard.printableCode(code) + " " + this);
        }
        if (code == Keyboard.CODE_SHIFT) {
            onPressShift();
        } else if (code == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
            onPressSymbol();
        } else {
            mShiftKeyState.onOtherKeyPressed();
            mSymbolKeyState.onOtherKeyPressed();
        }
    }

    public void onReleaseKey(int code, boolean withSliding) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onReleaseKey: code=" + Keyboard.printableCode(code)
                    + " sliding=" + withSliding + " " + this);
        }
        if (code == Keyboard.CODE_SHIFT) {
            onReleaseShift(withSliding);
        } else if (code == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
            onReleaseSymbol(withSliding);
        }
    }

    private void onPressSymbol() {
        toggleAlphabetAndSymbols();
        mSymbolKeyState.onPress();
        mSwitchState = SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL;
    }

    private void onReleaseSymbol(boolean withSliding) {
        if (mSwitchState == SWITCH_STATE_CHORDING_ALPHA) {
            // Switch back to the previous keyboard mode if the user chords the mode change key and
            // another key, then releases the mode change key.
            toggleAlphabetAndSymbols();
        } else if (!withSliding) {
            // If the mode change key is being released without sliding, we should forget the
            // previous symbols keyboard shift state and simply switch back to symbols layout
            // (never symbols shifted) next time the mode gets changed to symbols layout.
            mPrevSymbolsKeyboardWasShifted = false;
        }
        mSymbolKeyState.onRelease();
    }

    public void onUpdateShiftState(boolean autoCaps) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onUpdateShiftState: autoCaps=" + autoCaps + " " + this);
        }
        onUpdateShiftStateInternal(autoCaps);
    }

    private void onUpdateShiftStateInternal(boolean autoCaps) {
        if (mIsAlphabetMode) {
            if (!mAlphabetShiftState.isShiftLocked() && !mShiftKeyState.isIgnoring()) {
                if (mShiftKeyState.isReleasing() && autoCaps) {
                    // Only when shift key is releasing, automatic temporary upper case will be set.
                    setShifted(AUTOMATIC_SHIFT);
                } else {
                    setShifted(mShiftKeyState.isMomentary() ? MANUAL_SHIFT : UNSHIFT);
                }
            }
        } else {
            // In symbol keyboard mode, we should clear shift key state because only alphabet
            // keyboard has shift key.
            mSymbolKeyState.onRelease();
        }
    }

    private void onPressShift() {
        if (mIsAlphabetMode) {
            if (mAlphabetShiftState.isShiftLocked()) {
                // Shift key is pressed while caps lock state, we will treat this state as shifted
                // caps lock state and mark as if shift key pressed while normal state.
                setShifted(MANUAL_SHIFT);
                mShiftKeyState.onPress();
            } else if (mAlphabetShiftState.isAutomaticTemporaryUpperCase()) {
                // Shift key is pressed while automatic temporary upper case, we have to move to
                // manual temporary upper case.
                setShifted(MANUAL_SHIFT);
                mShiftKeyState.onPress();
            } else if (mAlphabetShiftState.isShiftedOrShiftLocked()) {
                // In manual upper case state, we just record shift key has been pressing while
                // shifted state.
                mShiftKeyState.onPressOnShifted();
            } else {
                // In base layout, chording or manual temporary upper case mode is started.
                setShifted(MANUAL_SHIFT);
                mShiftKeyState.onPress();
            }
        } else {
            // In symbol mode, just toggle symbol and symbol more keyboard.
            toggleShiftInSymbols();
            mSwitchState = SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE;
            mShiftKeyState.onPress();
        }
    }

    private void onReleaseShift(boolean withSliding) {
        if (mIsAlphabetMode) {
            final boolean isShiftLocked = mAlphabetShiftState.isShiftLocked();
            if (mShiftKeyState.isMomentary()) {
                if (mAlphabetShiftState.isShiftLockShifted()) {
                    // After chording input while caps lock state.
                    setShiftLocked(true);
                } else {
                    // After chording input while normal state.
                    setShifted(UNSHIFT);
                }
            } else if (mAlphabetShiftState.isShiftLockShifted() && withSliding) {
                // In caps lock state, shift has been pressed and slid out to other key.
                setShiftLocked(true);
            } else if (isShiftLocked && !mAlphabetShiftState.isShiftLockShifted()
                    && (mShiftKeyState.isPressing() || mShiftKeyState.isPressingOnShifted())
                    && !withSliding) {
                // Shift has been long pressed, ignore this release.
            } else if (isShiftLocked && !mShiftKeyState.isIgnoring() && !withSliding) {
                // Shift has been pressed without chording while caps lock state.
                setShiftLocked(false);
            } else if (mAlphabetShiftState.isShiftedOrShiftLocked()
                    && mShiftKeyState.isPressingOnShifted() && !withSliding) {
                // Shift has been pressed without chording while shifted state.
                setShifted(UNSHIFT);
            } else if (mAlphabetShiftState.isManualTemporaryUpperCaseFromAuto()
                    && mShiftKeyState.isPressing() && !withSliding) {
                // Shift has been pressed without chording while manual temporary upper case
                // transited from automatic temporary upper case.
                setShifted(UNSHIFT);
            }
        } else {
            // In symbol mode, switch back to the previous keyboard mode if the user chords the
            // shift key and another key, then releases the shift key.
            if (mSwitchState == SWITCH_STATE_CHORDING_SYMBOL) {
                toggleShiftInSymbols();
            }
        }
        mShiftKeyState.onRelease();
    }

    public void onCancelInput(boolean isSinglePointer) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onCancelInput: single=" + isSinglePointer + " " + this);
        }
        // Switch back to the previous keyboard mode if the user cancels sliding input.
        if (isSinglePointer) {
            if (mSwitchState == SWITCH_STATE_MOMENTARY_ALPHA_AND_SYMBOL) {
                toggleAlphabetAndSymbols();
            } else if (mSwitchState == SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE) {
                toggleShiftInSymbols();
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

    public void onCodeInput(int code, boolean isSinglePointer, boolean autoCaps) {
        if (DEBUG_EVENT) {
            Log.d(TAG, "onCodeInput: code=" + Keyboard.printableCode(code)
                    + " single=" + isSinglePointer
                    + " autoCaps=" + autoCaps + " " + this);
        }

        if (mIsAlphabetMode && code == Keyboard.CODE_CAPSLOCK) {
            if (mAlphabetShiftState.isShiftLocked()) {
                setShiftLocked(false);
                // Shift key is long pressed or double tapped while caps lock state, we will
                // toggle back to normal state. And mark as if shift key is released.
                mShiftKeyState.onRelease();
            } else {
                setShiftLocked(true);
            }
        }

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
                if (mIsAlphabetMode) {
                    mSwitchState = SWITCH_STATE_ALPHA;
                } else {
                    mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
                }
            } else if (isSinglePointer) {
                // Switch back to the previous keyboard mode if the user pressed the mode change key
                // and slid to other key, then released the finger.
                // If the user cancels the sliding input, switching back to the previous keyboard
                // mode is handled by {@link #onCancelInput}.
                toggleAlphabetAndSymbols();
            } else {
                // Chording input is being started. The keyboard mode will be switched back to the
                // previous mode in {@link onReleaseSymbol} when the mode change key is released.
                mSwitchState = SWITCH_STATE_CHORDING_ALPHA;
            }
            break;
        case SWITCH_STATE_MOMENTARY_SYMBOL_AND_MORE:
            if (code == Keyboard.CODE_SHIFT) {
                // Detected only the shift key has been pressed on symbol layout, and then released.
                mSwitchState = SWITCH_STATE_SYMBOL_BEGIN;
            } else if (isSinglePointer) {
                // Switch back to the previous keyboard mode if the user pressed the shift key on
                // symbol mode and slid to other key, then released the finger.
                toggleShiftInSymbols();
                mSwitchState = SWITCH_STATE_SYMBOL;
            } else {
                // Chording input is being started. The keyboard mode will be switched back to the
                // previous mode in {@link onReleaseShift} when the shift key is released.
                mSwitchState = SWITCH_STATE_CHORDING_SYMBOL;
            }
            break;
        case SWITCH_STATE_SYMBOL_BEGIN:
            if (!isSpaceCharacter(code) && (Keyboard.isLetterCode(code)
                    || code == Keyboard.CODE_OUTPUT_TEXT)) {
                mSwitchState = SWITCH_STATE_SYMBOL;
            }
            // Switch back to alpha keyboard mode immediately if user types a quote character.
            if (isLayoutSwitchBackCharacter(code)) {
                setAlphabetKeyboard();
            }
            break;
        case SWITCH_STATE_SYMBOL:
        case SWITCH_STATE_CHORDING_SYMBOL:
            // Switch back to alpha keyboard mode if user types one or more non-space/enter
            // characters followed by a space/enter or a quote character.
            if (isSpaceCharacter(code) || isLayoutSwitchBackCharacter(code)) {
                setAlphabetKeyboard();
            }
            break;
        }

        // If the code is a letter, update keyboard shift state.
        if (Keyboard.isLetterCode(code)) {
            onUpdateShiftStateInternal(autoCaps);
        }
    }

    private static String shiftModeToString(int shiftMode) {
        switch (shiftMode) {
        case UNSHIFT: return "UNSHIFT";
        case MANUAL_SHIFT: return "MANUAL";
        case AUTOMATIC_SHIFT: return "AUTOMATIC";
        default: return null;
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
        return "[keyboard=" + (mIsAlphabetMode ? mAlphabetShiftState.toString()
                        : (mIsSymbolShifted ? "SYMBOLS_SHIFTED" : "SYMBOLS"))
                + " shift=" + mShiftKeyState
                + " symbol=" + mSymbolKeyState
                + " switch=" + switchStateToString(mSwitchState) + "]";
    }
}
