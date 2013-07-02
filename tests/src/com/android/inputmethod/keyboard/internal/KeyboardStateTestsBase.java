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

import com.android.inputmethod.latin.Constants;

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

    /**
     * Set auto caps mode.
     *
     * @param autoCaps the auto cap mode.
     */
    public void setAutoCapsMode(final int autoCaps) {
        mSwitcher.setAutoCapsMode(autoCaps);
    }

    private static void assertLayout(final String message, final int expected, final int actual) {
        assertTrue(message + ": expected=" + MockKeyboardSwitcher.getLayoutName(expected)
                + " actual=" + MockKeyboardSwitcher.getLayoutName(actual),
                expected == actual);
    }

    /**
     * Emulate update keyboard shift state.
     *
     * @param afterUpdate the keyboard state after updating the keyboard shift state.
     */
    public void updateShiftState(final int afterUpdate) {
        mSwitcher.updateShiftState();
        assertLayout("afterUpdate", afterUpdate, mSwitcher.getLayoutId());
    }

    /**
     * Emulate load default keyboard.
     *
     * @param afterLoad the keyboard state after loading default keyboard.
     */
    public void loadKeyboard(final int afterLoad) {
        mSwitcher.loadKeyboard();
        mSwitcher.updateShiftState();
        assertLayout("afterLoad", afterLoad, mSwitcher.getLayoutId());
    }

    /**
     * Emulate rotate device.
     *
     * @param afterRotate the keyboard state after rotating device.
     */
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

    /**
     * Emulate key press.
     *
     * @param code the key code to press.
     * @param afterPress the keyboard state after pressing the key.
     */
    public void pressKey(final int code, final int afterPress) {
        mSwitcher.expireDoubleTapTimeout();
        pressKeyWithoutTimerExpire(code, true, afterPress);
    }

    /**
     * Emulate key release and register.
     *
     * @param code the key code to release and register
     * @param afterRelease the keyboard state after releasing the key.
     */
    public void releaseKey(final int code, final int afterRelease) {
        mSwitcher.onCodeInput(code);
        mSwitcher.onReleaseKey(code, NOT_SLIDING);
        assertLayout("afterRelease", afterRelease, mSwitcher.getLayoutId());
    }

    /**
     * Emulate key press and release.
     *
     * @param code the key code to press and release.
     * @param afterPress the keyboard state after pressing the key.
     * @param afterRelease the keyboard state after releasing the key.
     */
    public void pressAndReleaseKey(final int code, final int afterPress, final int afterRelease) {
        pressKey(code, afterPress);
        releaseKey(code, afterRelease);
    }

    /**
     * Emulate chording key press.
     *
     * @param code the chording key code.
     * @param afterPress the keyboard state after pressing chording key.
     */
    public void chordingPressKey(final int code, final int afterPress) {
        mSwitcher.expireDoubleTapTimeout();
        pressKeyWithoutTimerExpire(code, false, afterPress);
    }

    /**
     * Emulate chording key release.
     *
     * @param code the cording key code.
     * @param afterRelease the keyboard state after releasing chording key.
     */
    public void chordingReleaseKey(final int code, final int afterRelease) {
        mSwitcher.onCodeInput(code);
        mSwitcher.onReleaseKey(code, NOT_SLIDING);
        assertLayout("afterRelease", afterRelease, mSwitcher.getLayoutId());
    }

    /**
     * Emulate chording key press and release.
     *
     * @param code the chording key code.
     * @param afterPress the keyboard state after pressing chording key.
     * @param afterRelease the keyboard state after releasing chording key.
     */
    public void chordingPressAndReleaseKey(final int code, final int afterPress,
            final int afterRelease) {
        chordingPressKey(code, afterPress);
        chordingReleaseKey(code, afterRelease);
    }

    /**
     * Emulate start of the sliding key input.
     *
     * @param code the key code to start sliding.
     * @param afterPress the keyboard state after pressing the key.
     * @param afterSlide the keyboard state after releasing the key with sliding input.
     */
    public void pressAndSlideFromKey(final int code, final int afterPress, final int afterSlide) {
        pressKey(code, afterPress);
        mSwitcher.onReleaseKey(code, SLIDING);
        assertLayout("afterSlide", afterSlide, mSwitcher.getLayoutId());
    }

    /**
     * Emulate end of the sliding key input.
     *
     * @param code the key code to stop sliding.
     * @param afterPress the keyboard state after pressing the key.
     * @param afterSlide the keyboard state after releasing the key and stop sliding.
     */
    public void stopSlidingOnKey(final int code, final int afterPress, final int afterSlide) {
        pressKey(code, afterPress);
        mSwitcher.onCodeInput(code);
        mSwitcher.onReleaseKey(code, NOT_SLIDING);
        mSwitcher.onFinishSlidingInput();
        assertLayout("afterSlide", afterSlide, mSwitcher.getLayoutId());
    }

    /**
     * Emulate cancel the sliding key input.
     *
     * @param afterCancelSliding the keyboard state after canceling sliding input.
     */
    public void stopSlidingAndCancel(final int afterCancelSliding) {
        mSwitcher.onFinishSlidingInput();
        assertLayout("afterCancelSliding", afterCancelSliding, mSwitcher.getLayoutId());
    }

    /**
     * Emulate long press shift key.
     *
     * @param afterPress the keyboard state after pressing shift key.
     * @param afterLongPress the keyboard state after long press fired.
     */
    public void longPressShiftKey(final int afterPress, final int afterLongPress) {
        // Long press shift key will register {@link Constants#CODE_CAPS_LOCK}. See
        // {@link R.xml#key_styles_common} and its baseForShiftKeyStyle. We thus emulate the
        // behavior that is implemented in {@link MainKeyboardView#onLongPress(PointerTracker)}.
        pressKey(Constants.CODE_SHIFT, afterPress);
        mSwitcher.onPressKey(Constants.CODE_CAPSLOCK, true /* isSinglePointer */);
        mSwitcher.onCodeInput(Constants.CODE_CAPSLOCK);
        assertLayout("afterLongPress", afterLongPress, mSwitcher.getLayoutId());
    }

    /**
     * Emulate long press shift key and release.
     *
     * @param afterPress the keyboard state after pressing shift key.
     * @param afterLongPress the keyboard state after long press fired.
     * @param afterRelease the keyboard state after shift key is released.
     */
    public void longPressAndReleaseShiftKey(final int afterPress, final int afterLongPress,
            final int afterRelease) {
        // Long press shift key will register {@link Constants#CODE_CAPS_LOCK}. See
        // {@link R.xml#key_styles_common} and its baseForShiftKeyStyle. We thus emulate the
        // behavior that is implemented in {@link MainKeyboardView#onLongPress(PointerTracker)}.
        longPressShiftKey(afterPress, afterLongPress);
        releaseKey(Constants.CODE_CAPSLOCK, afterRelease);
    }

    /**
     * Emulate the second press of the double tap.
     *
     * @param code the key code to double tap.
     * @param afterPress the keyboard state after pressing the second tap.
     */
    public void secondPressKey(final int code, final int afterPress) {
        pressKeyWithoutTimerExpire(code, true, afterPress);
    }

    /**
     * Emulate the second tap of the double tap.
     *
     * @param code the key code to double tap.
     * @param afterPress the keyboard state after pressing the second tap.
     * @param afterRelease the keyboard state after releasing the second tap.
     */
    public void secondPressAndReleaseKey(final int code, final int afterPress,
            final int afterRelease) {
        secondPressKey(code, afterPress);
        releaseKey(code, afterRelease);
    }
}
