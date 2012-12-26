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

package com.android.inputmethod.event;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.android.inputmethod.latin.Constants;

/**
 * A hardware event decoder for a hardware qwerty-ish keyboard.
 *
 * The events are always hardware keypresses, but they can be key down or key up events, they
 * can be dead keys, they can be meta keys like shift or ctrl... This does not deal with
 * 10-key like keyboards; a different decoder is used for this.
 */
public class HardwareKeyboardEventDecoder implements HardwareEventDecoder {
    final int mDeviceId;

    public HardwareKeyboardEventDecoder(final int deviceId) {
        mDeviceId = deviceId;
        // TODO: get the layout for this hardware keyboard
    }

    @Override
    public Event decodeHardwareKey(final KeyEvent keyEvent) {
        final Event event = Event.obtainEvent();
        // KeyEvent#getUnicodeChar() does not exactly returns a unicode char, but rather a value
        // that includes both the unicode char in the lower 21 bits and flags in the upper bits,
        // hence the name "codePointAndFlags". {@see KeyEvent#getUnicodeChar()} for more info.
        final int codePointAndFlags = keyEvent.getUnicodeChar();
        // The keyCode is the abstraction used by the KeyEvent to represent different keys that
        // do not necessarily map to a unicode character. This represents a physical key, like
        // the key for 'A' or Space, but also Backspace or Ctrl or Caps Lock.
        final int keyCode = keyEvent.getKeyCode();
        if (KeyEvent.KEYCODE_DEL == keyCode) {
            event.setCommittableEvent(Constants.CODE_DELETE);
            return event;
        }
        if (keyEvent.isPrintingKey() || KeyEvent.KEYCODE_SPACE == keyCode
                || KeyEvent.KEYCODE_ENTER == keyCode) {
            if (0 != (codePointAndFlags & KeyCharacterMap.COMBINING_ACCENT)) {
                // A dead key.
                event.setDeadEvent(codePointAndFlags & KeyCharacterMap.COMBINING_ACCENT_MASK);
            } else {
                // A committable character. This should be committed right away, taking into
                // account the current state.
                event.setCommittableEvent(codePointAndFlags);
            }
        } else {
            event.setNotHandledEvent();
        }
        return event;
    }
}
