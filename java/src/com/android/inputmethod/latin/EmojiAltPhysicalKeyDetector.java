/*
 * Copyright (C) 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin;

import android.util.Log;
import android.view.KeyEvent;

import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.settings.Settings;

/**
 * A class for detecting Emoji-Alt physical key.
 */
final class EmojiAltPhysicalKeyDetector {
    private static final String TAG = "EmojiAltPhysicalKeyDetector";

    private final RichInputConnection mRichInputConnection;

    // True if the Alt key has been used as a modifier. In this case the Alt key up isn't
    // recognized as an emoji key.
    private boolean mAltHasBeenUsedAsAModifier;

    public EmojiAltPhysicalKeyDetector(final RichInputConnection richInputConnection) {
        mRichInputConnection = richInputConnection;
    }

    /**
     * Record a down key event.
     * @param keyEvent a down key event.
     */
    public void onKeyDown(final KeyEvent keyEvent) {
        if (isAltKey(keyEvent)) {
            mAltHasBeenUsedAsAModifier = false;
        }
        if (containsAltModifier(keyEvent)) {
            mAltHasBeenUsedAsAModifier = true;
        }
    }

    /**
     * Determine whether an up key event is a special key up or not.
     * @param keyEvent an up key event.
     */
    public void onKeyUp(final KeyEvent keyEvent) {
        if (keyEvent.isCanceled()) {
            // This key up event was a part of key combinations and should be ignored.
            return;
        }
        if (!isAltKey(keyEvent)) {
            mAltHasBeenUsedAsAModifier |= containsAltModifier(keyEvent);
            return;
        }
        if (containsAltModifier(keyEvent)) {
            mAltHasBeenUsedAsAModifier = true;
            return;
        }
        if (!Settings.getInstance().getCurrent().mEnableEmojiAltPhysicalKey) {
            return;
        }
        if (mAltHasBeenUsedAsAModifier) {
            return;
        }
        if (!mRichInputConnection.isConnected()) {
            Log.w(TAG, "onKeyUp() : No connection to text view");
            return;
        }
        onEmojiAltKeyDetected();
    }

    private static void onEmojiAltKeyDetected() {
        KeyboardSwitcher.getInstance().onToggleEmojiKeyboard();
    }

    private static boolean isAltKey(final KeyEvent keyEvent) {
        final int keyCode = keyEvent.getKeyCode();
        return keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT;
    }

    private static boolean containsAltModifier(final KeyEvent keyEvent) {
        final int metaState = keyEvent.getMetaState();
        // TODO: Support multiple keyboards. Take device id into account.
        switch (keyEvent.getKeyCode()) {
        case KeyEvent.KEYCODE_ALT_LEFT:
            // Return true if Left-Alt is pressed with Right-Alt pressed.
            return (metaState & KeyEvent.META_ALT_RIGHT_ON) != 0;
        case KeyEvent.KEYCODE_ALT_RIGHT:
            // Return true if Right-Alt is pressed with Left-Alt pressed.
            return (metaState & KeyEvent.META_ALT_LEFT_ON) != 0;
        default:
            return (metaState & (KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_RIGHT_ON)) != 0;
        }
    }
}
