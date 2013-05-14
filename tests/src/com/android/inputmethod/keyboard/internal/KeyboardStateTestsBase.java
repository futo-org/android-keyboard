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

import android.test.AndroidTestCase;

public class KeyboardStateTestsBase extends AndroidTestCase
        implements MockKeyboardSwitcher.MockConstants {
    protected MockKeyboardSwitcher mSwitcher;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSwitcher = new MockKeyboardSwitcher();
        mSwitcher.setAutoCapsMode(CAP_MODE_OFF);

        loadKeyboard(ALPHABET_UNSHIFTED);
    }

    public void setAutoCapsMode(final int autoCaps) {
        mSwitcher.setAutoCapsMode(autoCaps);
    }

    private static void assertLayout(final String message, final int expected, final int actual) {
        assertTrue(message + ": expected=" + MockKeyboardSwitcher.getLayoutName(expected)
                + " actual=" + MockKeyboardSwitcher.getLayoutName(actual),
                expected == actual);
    }

    public void updateShiftState(final int afterUpdate) {
        mSwitcher.updateShiftState();
        assertLayout("afterUpdate", afterUpdate, mSwitcher.getLayoutId());
    }

    public void loadKeyboard(final int afterLoad) {
        mSwitcher.loadKeyboard();
        mSwitcher.updateShiftState();
        assertLayout("afterLoad", afterLoad, mSwitcher.getLayoutId());
    }

    public void rotateDevice(final int afterRotate) {
        mSwitcher.saveKeyboardState();
        mSwitcher.loadKeyboard();
        assertLayout("afterRotate", afterRotate, mSwitcher.getLayoutId());
    }

    private void pressKeyWithoutTimerExpire(final int code, final boolean isSinglePointer,
            final int afterPress) {
        mSwitcher.onPressKey(code, isSinglePointer);
        assertLayout("afterPress", afterPress, mSwitcher.getLayoutId());
    }

    public void pressKey(final int code, final int afterPress) {
        mSwitcher.expireDoubleTapTimeout();
        pressKeyWithoutTimerExpire(code, true, afterPress);
    }

    public void releaseKey(final int code, final int afterRelease) {
        mSwitcher.onCodeInput(code);
        mSwitcher.onReleaseKey(code, NOT_SLIDING);
        assertLayout("afterRelease", afterRelease, mSwitcher.getLayoutId());
    }

    public void pressAndReleaseKey(final int code, final int afterPress, final int afterRelease) {
        pressKey(code, afterPress);
        releaseKey(code, afterRelease);
    }

    public void chordingPressKey(final int code, final int afterPress) {
        mSwitcher.expireDoubleTapTimeout();
        pressKeyWithoutTimerExpire(code, false, afterPress);
    }

    public void chordingReleaseKey(final int code, final int afterRelease) {
        mSwitcher.onCodeInput(code);
        mSwitcher.onReleaseKey(code, NOT_SLIDING);
        assertLayout("afterRelease", afterRelease, mSwitcher.getLayoutId());
    }

    public void chordingPressAndReleaseKey(final int code, final int afterPress,
            final int afterRelease) {
        chordingPressKey(code, afterPress);
        chordingReleaseKey(code, afterRelease);
    }

    public void pressAndSlideFromKey(final int code, final int afterPress, final int afterSlide) {
        pressKey(code, afterPress);
        mSwitcher.onReleaseKey(code, SLIDING);
        assertLayout("afterSlide", afterSlide, mSwitcher.getLayoutId());
    }

    public void stopSlidingOnKey(final int code, final int afterPress, final int afterSlide) {
        pressKey(code, afterPress);
        mSwitcher.onCodeInput(code);
        mSwitcher.onReleaseKey(code, NOT_SLIDING);
        mSwitcher.onFinishSlidingInput();
        assertLayout("afterSlide", afterSlide, mSwitcher.getLayoutId());
    }

    public void stopSlidingAndCancel(final int afterCancelSliding) {
        mSwitcher.onFinishSlidingInput();
        assertLayout("afterCancelSliding", afterCancelSliding, mSwitcher.getLayoutId());
    }

    public void longPressKey(final int code, final int afterPress, final int afterLongPress) {
        pressKey(code, afterPress);
        mSwitcher.onLongPressTimeout(code);
        assertLayout("afterLongPress", afterLongPress, mSwitcher.getLayoutId());
    }

    public void longPressAndReleaseKey(final int code, final int afterPress,
            final int afterLongPress, final int afterRelease) {
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
