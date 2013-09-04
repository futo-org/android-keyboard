/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.keyboard.internal;

import android.text.TextUtils;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.RecapitalizeStatus;

public class MockKeyboardSwitcher implements KeyboardState.SwitchActions {
    public interface MockConstants {
        // Argument for {@link KeyboardState#onPressKey} and {@link KeyboardState#onReleaseKey}.
        public static final boolean NOT_SLIDING = false;
        public static final boolean SLIDING = true;
        // Argument for {@link KeyboardState#onCodeInput}.
        public static final boolean SINGLE = true;
        public static final boolean MULTI = false;
        public static final int CAP_MODE_OFF = Constants.TextUtils.CAP_MODE_OFF;
        public static final int CAP_MODE_WORDS = TextUtils.CAP_MODE_WORDS;
        public static final int CAP_MODE_CHARACTERS = TextUtils.CAP_MODE_CHARACTERS;

        public static final int CODE_SHIFT = Constants.CODE_SHIFT;
        public static final int CODE_SYMBOL = Constants.CODE_SWITCH_ALPHA_SYMBOL;
        public static final int CODE_SPACE = Constants.CODE_SPACE;
        public static final int CODE_AUTO_CAPS_TRIGGER = Constants.CODE_SPACE;

        public static final int ALPHABET_UNSHIFTED = 0;
        public static final int ALPHABET_MANUAL_SHIFTED = 1;
        public static final int ALPHABET_AUTOMATIC_SHIFTED = 2;
        public static final int ALPHABET_SHIFT_LOCKED = 3;
        public static final int ALPHABET_SHIFT_LOCK_SHIFTED = 4;
        public static final int SYMBOLS_UNSHIFTED = 5;
        public static final int SYMBOLS_SHIFTED = 6;
    }

    private int mLayout = MockConstants.ALPHABET_UNSHIFTED;

    private int mAutoCapsMode = MockConstants.CAP_MODE_OFF;
    // Following InputConnection's behavior. Simulating InputType.TYPE_TEXT_FLAG_CAP_WORDS.
    private int mAutoCapsState = MockConstants.CAP_MODE_OFF;

    private boolean mIsInDoubleTapShiftKeyTimeout;
    private int mLongPressTimeoutCode;

    private final KeyboardState mState = new KeyboardState(this);

    public int getLayoutId() {
        return mLayout;
    }

    public static String getLayoutName(final int layoutId) {
        switch (layoutId) {
        case MockConstants.ALPHABET_UNSHIFTED: return "ALPHABET_UNSHIFTED";
        case MockConstants.ALPHABET_MANUAL_SHIFTED: return "ALPHABET_MANUAL_SHIFTED";
        case MockConstants.ALPHABET_AUTOMATIC_SHIFTED: return "ALPHABET_AUTOMATIC_SHIFTED";
        case MockConstants.ALPHABET_SHIFT_LOCKED: return "ALPHABET_SHIFT_LOCKED";
        case MockConstants.ALPHABET_SHIFT_LOCK_SHIFTED: return "ALPHABET_SHIFT_LOCK_SHIFTED";
        case MockConstants.SYMBOLS_UNSHIFTED: return "SYMBOLS_UNSHIFTED";
        case MockConstants.SYMBOLS_SHIFTED: return "SYMBOLS_SHIFTED";
        default: return "UNKNOWN<" + layoutId + ">";
        }
    }

    public void setAutoCapsMode(final int autoCaps) {
        mAutoCapsMode = autoCaps;
        mAutoCapsState = autoCaps;
    }

    public void expireDoubleTapTimeout() {
        mIsInDoubleTapShiftKeyTimeout = false;
    }

    @Override
    public void setAlphabetKeyboard() {
        mLayout = MockConstants.ALPHABET_UNSHIFTED;
    }

    @Override
    public void setAlphabetManualShiftedKeyboard() {
        mLayout = MockConstants.ALPHABET_MANUAL_SHIFTED;
    }

    @Override
    public void setAlphabetAutomaticShiftedKeyboard() {
        mLayout = MockConstants.ALPHABET_AUTOMATIC_SHIFTED;
    }

    @Override
    public void setAlphabetShiftLockedKeyboard() {
        mLayout = MockConstants.ALPHABET_SHIFT_LOCKED;
    }

    @Override
    public void setAlphabetShiftLockShiftedKeyboard() {
        mLayout = MockConstants.ALPHABET_SHIFT_LOCK_SHIFTED;
    }

    @Override
    public void setSymbolsKeyboard() {
        mLayout = MockConstants.SYMBOLS_UNSHIFTED;
    }

    @Override
    public void setSymbolsShiftedKeyboard() {
        mLayout = MockConstants.SYMBOLS_SHIFTED;
    }

    @Override
    public void setEmojiKeyboard() {
        // Just ignore.
    }

    @Override
    public void requestUpdatingShiftState() {
        mState.onUpdateShiftState(mAutoCapsState, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE);
    }

    @Override
    public void startDoubleTapShiftKeyTimer() {
        mIsInDoubleTapShiftKeyTimeout = true;
    }

    @Override
    public void cancelDoubleTapShiftKeyTimer() {
        mIsInDoubleTapShiftKeyTimeout = false;
    }

    @Override
    public boolean isInDoubleTapShiftKeyTimeout() {
        return mIsInDoubleTapShiftKeyTimeout;
    }

    public void updateShiftState() {
        mState.onUpdateShiftState(mAutoCapsState, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE);
    }

    public void loadKeyboard() {
        mState.onLoadKeyboard();
    }

    public void saveKeyboardState() {
        mState.onSaveKeyboardState();
    }

    public void onPressKey(final int code, final boolean isSinglePointer) {
        mState.onPressKey(code, isSinglePointer, mAutoCapsState);
    }

    public void onReleaseKey(final int code, final boolean withSliding) {
        mState.onReleaseKey(code, withSliding);
        if (mLongPressTimeoutCode == code) {
            mLongPressTimeoutCode = 0;
        }
    }

    public void onCodeInput(final int code) {
        if (mAutoCapsMode == MockConstants.CAP_MODE_WORDS) {
            if (Constants.isLetterCode(code)) {
                mAutoCapsState = (code == MockConstants.CODE_AUTO_CAPS_TRIGGER)
                        ? mAutoCapsMode : MockConstants.CAP_MODE_OFF;
            }
        } else {
            mAutoCapsState = mAutoCapsMode;
        }
        mState.onCodeInput(code, mAutoCapsState);
    }

    public void onFinishSlidingInput() {
        mState.onFinishSlidingInput();
    }
}
