/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.inputmethod.keyboard;

public interface KeyboardActionListener {

    /**
     * Called when the user presses a key. This is sent before the
     * {@link #onKey} is called. For keys that repeat, this is only
     * called once.
     *
     * @param primaryCode
     *            the unicode of the key being pressed. If the touch is
     *            not on a valid key, the value will be zero.
     */
    void onPress(int primaryCode);

    /**
     * Called when the user releases a key. This is sent after the
     * {@link #onKey} is called. For keys that repeat, this is only
     * called once.
     *
     * @param primaryCode
     *            the code of the key that was released
     */
    void onRelease(int primaryCode);

    /**
     * Send a key press to the listener.
     *
     * @param primaryCode
     *            this is the key that was pressed
     * @param keyCodes
     *            the codes for all the possible alternative keys with
     *            the primary code being the first. If the primary key
     *            code is a single character such as an alphabet or
     *            number or symbol, the alternatives will include other
     *            characters that may be on the same key or adjacent
     *            keys. These codes are useful to correct for
     *            accidental presses of a key adjacent to the intended
     *            key.
     * @param x
     *            x-coordinate pixel of touched event. If onKey is not called by onTouchEvent,
     *            the value should be NOT_A_TOUCH_COORDINATE.
     * @param y
     *            y-coordinate pixel of touched event. If onKey is not called by onTouchEvent,
     *            the value should be NOT_A_TOUCH_COORDINATE.
     */
    void onKey(int primaryCode, int[] keyCodes, int x, int y);

    /**
     * Sends a sequence of characters to the listener.
     *
     * @param text
     *            the sequence of characters to be displayed.
     */
    void onText(CharSequence text);

    /**
     * Called when user released a finger outside any key.
     */
    void onCancel();

    /**
     * Called when the user quickly moves the finger from right to
     * left.
     */
    void swipeLeft();

    /**
     * Called when the user quickly moves the finger from left to
     * right.
     */
    void swipeRight();

    /**
     * Called when the user quickly moves the finger from up to down.
     */
    void swipeDown();

    /**
     * Called when the user quickly moves the finger from down to up.
     */
    void swipeUp();
}
