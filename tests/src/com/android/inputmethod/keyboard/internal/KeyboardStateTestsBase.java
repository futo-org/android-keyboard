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

import android.test.AndroidTestCase;

public class KeyboardStateTestsBase extends AndroidTestCase
        implements MockKeyboardSwitcher.Constants {
    protected MockKeyboardSwitcher mSwitcher;

    private String mLayoutSwitchBackSymbols = "";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSwitcher = new MockKeyboardSwitcher();
        mSwitcher.setAutoCapsMode(CAP_MODE_OFF);

        loadKeyboard(ALPHABET_UNSHIFTED);
    }

    public void setAutoCapsMode(int autoCaps) {
        mSwitcher.setAutoCapsMode(autoCaps);
    }

    public void setLayoutSwitchBackSymbols(String switchBackSymbols) {
        mLayoutSwitchBackSymbols = switchBackSymbols;
    }

    private static void assertLayout(String message, int expected, int actual) {
        assertTrue(message + ": expected=" + MockKeyboardSwitcher.getLayoutName(expected)
                + " actual=" + MockKeyboardSwitcher.getLayoutName(actual),
                expected == actual);
    }

    public void updateShiftState(int afterUpdate) {
        mSwitcher.updateShiftState();
        assertLayout("afterUpdate", afterUpdate, mSwitcher.getLayoutId());
    }

    public void loadKeyboard(int afterLoad) {
        mSwitcher.loadKeyboard(mLayoutSwitchBackSymbols);
        mSwitcher.updateShiftState();
        assertLayout("afterLoad", afterLoad, mSwitcher.getLayoutId());
    }

    public void rotateDevice(int afterRotate) {
        mSwitcher.saveKeyboardState();
        mSwitcher.loadKeyboard(mLayoutSwitchBackSymbols);
        assertLayout("afterRotate", afterRotate, mSwitcher.getLayoutId());
    }

    private void pressKeyWithoutTimerExpire(int code, boolean isSinglePointer, int afterPress) {
        mSwitcher.onPressKey(code, isSinglePointer);
        assertLayout("afterPress", afterPress, mSwitcher.getLayoutId());
    }

    public void pressKey(int code, int afterPress) {
        mSwitcher.expireDoubleTapTimeout();
        pressKeyWithoutTimerExpire(code, true, afterPress);
    }

    public void releaseKey(int code, int afterRelease) {
        mSwitcher.onCodeInput(code, SINGLE);
        mSwitcher.onReleaseKey(code, NOT_SLIDING);
        assertLayout("afterRelease", afterRelease, mSwitcher.getLayoutId());
    }

    public void pressAndReleaseKey(int code, int afterPress, int afterRelease) {
        pressKey(code, afterPress);
        releaseKey(code, afterRelease);
    }

    public void chordingPressKey(int code, int afterPress) {
        mSwitcher.expireDoubleTapTimeout();
        pressKeyWithoutTimerExpire(code, false, afterPress);
    }

    public void chordingReleaseKey(int code, int afterRelease) {
        mSwitcher.onCodeInput(code, MULTI);
        mSwitcher.onReleaseKey(code, NOT_SLIDING);
        assertLayout("afterRelease", afterRelease, mSwitcher.getLayoutId());
    }

    public void chordingPressAndReleaseKey(int code, int afterPress, int afterRelease) {
        chordingPressKey(code, afterPress);
        chordingReleaseKey(code, afterRelease);
    }

    public void pressAndSlideFromKey(int code, int afterPress, int afterSlide) {
        pressKey(code, afterPress);
        mSwitcher.onReleaseKey(code, SLIDING);
        assertLayout("afterSlide", afterSlide, mSwitcher.getLayoutId());
    }

    public void longPressKey(int code, int afterPress, int afterLongPress) {
        pressKey(code, afterPress);
        mSwitcher.onLongPressTimeout(code);
        assertLayout("afterLongPress", afterLongPress, mSwitcher.getLayoutId());
    }

    public void longPressAndReleaseKey(int code, int afterPress, int afterLongPress,
            int afterRelease) {
        longPressKey(code, afterPress, afterLongPress);
        releaseKey(code, afterRelease);
    }

    public void secondPressKey(int code, int afterPress) {
        pressKeyWithoutTimerExpire(code, true, afterPress);
    }

    public void secondPressAndReleaseKey(int code, int afterPress, int afterRelease) {
        secondPressKey(code, afterPress);
        releaseKey(code, afterRelease);
    }
}
