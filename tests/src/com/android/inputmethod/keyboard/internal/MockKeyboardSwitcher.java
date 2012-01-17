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

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.internal.KeyboardState.SwitchActions;

public class MockKeyboardSwitcher implements KeyboardState.SwitchActions {
    public interface Constants {
        // Argument for KeyboardState.onPressKey and onReleaseKey.
        public static final boolean NOT_SLIDING = false;
        public static final boolean SLIDING = true;
        // Argument for KeyboardState.onCodeInput.
        public static final boolean SINGLE = true;
        public static final boolean MULTI = false;
        public static final boolean NO_AUTO_CAPS = false;
        public static final boolean AUTO_CAPS = true;

        public static final int CODE_SHIFT = Keyboard.CODE_SHIFT;
        public static final int CODE_SYMBOL = Keyboard.CODE_SWITCH_ALPHA_SYMBOL;
        public static final int CODE_CAPSLOCK = Keyboard.CODE_CAPSLOCK;
        public static final int CODE_SPACE = Keyboard.CODE_SPACE;
    }

    public static final String WORD_SEPARATORS = " ,.";

    private static final int ALPHABET_UNSHIFTED = 0;
    private static final int ALPHABET_MANUAL_SHIFTED = 1;
    private static final int ALPHABET_AUTOMATIC_SHIFTED = 2;
    private static final int ALPHABET_SHIFT_LOCKED = 3;
    private static final int SYMBOLS_UNSHIFTED = 4;
    private static final int SYMBOLS_SHIFTED = 5;

    private int mLayout = ALPHABET_UNSHIFTED;

    private boolean mAutoCapsMode = Constants.NO_AUTO_CAPS;
    // Following InputConnection's behavior. Simulating InputType.TYPE_TEXT_FLAG_CAP_WORDS.
    private boolean mAutoCapsState = true;

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
        mAutoCapsMode = autoCaps;
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
        mState.onUpdateShiftState(mAutoCapsMode && mAutoCapsState);
    }

    public void toggleCapsLock() {
        mState.onToggleCapsLock();
    }

    public void updateShiftState() {
        mState.onUpdateShiftState(mAutoCapsMode && mAutoCapsState);
    }

    public void loadKeyboard(String layoutSwitchBackSymbols,
            boolean hasDistinctMultitouch) {
        mState.onLoadKeyboard(layoutSwitchBackSymbols, hasDistinctMultitouch);
    }

    public void onPressKey(int code) {
        mState.onPressKey(code);
    }

    public void onReleaseKey(int code) {
        onReleaseKey(code, Constants.NOT_SLIDING);
    }

    public void onReleaseKey(int code, boolean withSliding) {
        mState.onReleaseKey(code, withSliding);
    }

    public void onCodeInput(int code) {
        onCodeInput(code, Constants.SINGLE);
    }

    public void onCodeInput(int code, boolean isSinglePointer) {
        mAutoCapsState = (WORD_SEPARATORS.indexOf(code) >= 0);
        mState.onCodeInput(code, isSinglePointer, mAutoCapsMode && mAutoCapsState);
    }

    public void onCancelInput(boolean isSinglePointer) {
        mState.onCancelInput(isSinglePointer);
    }
}