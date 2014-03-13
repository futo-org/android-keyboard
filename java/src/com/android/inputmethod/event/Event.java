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

import com.android.inputmethod.latin.Constants;

/**
 * Class representing a generic input event as handled by Latin IME.
 *
 * This contains information about the origin of the event, but it is generalized and should
 * represent a software keypress, hardware keypress, or d-pad move alike.
 * Very importantly, this does not necessarily result in inputting one character, or even anything
 * at all - it may be a dead key, it may be a partial input, it may be a special key on the
 * keyboard, it may be a cancellation of a keypress (e.g. in a soft keyboard the finger of the
 * user has slid out of the key), etc. It may also be a batch input from a gesture or handwriting
 * for example.
 * The combiner should figure out what to do with this.
 */
public class Event {
    // Should the types below be represented by separate classes instead? It would be cleaner
    // but probably a bit too much
    // An event we don't handle in Latin IME, for example pressing Ctrl on a hardware keyboard.
    final public static int EVENT_NOT_HANDLED = 0;
    // A key press that is part of input, for example pressing an alphabetic character on a
    // hardware qwerty keyboard. It may be part of a sequence that will be re-interpreted later
    // through combination.
    final public static int EVENT_INPUT_KEYPRESS = 1;
    // A toggle event is triggered by a key that affects the previous character. An example would
    // be a numeric key on a 10-key keyboard, which would toggle between 1 - a - b - c with
    // repeated presses.
    final public static int EVENT_TOGGLE = 2;
    // A mode event instructs the combiner to change modes. The canonical example would be the
    // hankaku/zenkaku key on a Japanese keyboard, or even the caps lock key on a qwerty keyboard
    // if handled at the combiner level.
    final public static int EVENT_MODE_KEY = 3;
    // An event corresponding to a gesture.
    final public static int EVENT_GESTURE = 4;

    // 0 is a valid code point, so we use -1 here.
    final public static int NOT_A_CODE_POINT = -1;
    // -1 is a valid key code, so we use 0 here.
    final public static int NOT_A_KEY_CODE = 0;

    final private static int FLAG_NONE = 0;
    // This event is a dead character, usually input by a dead key. Examples include dead-acute
    // or dead-abovering.
    final private static int FLAG_DEAD = 0x1;

    final private int mType; // The type of event - one of the constants above
    // The code point associated with the event, if relevant. This is a unicode code point, and
    // has nothing to do with other representations of the key. It is only relevant if this event
    // is of KEYPRESS type, but for a mode key like hankaku/zenkaku or ctrl, there is no code point
    // associated so this should be NOT_A_CODE_POINT to avoid unintentional use of its value when
    // it's not relevant.
    final public int mCodePoint;

    // The key code associated with the event, if relevant. This is relevant whenever this event
    // has been triggered by a key press, but not for a gesture for example. This has conceptually
    // no link to the code point, although keys that enter a straight code point may often set
    // this to be equal to mCodePoint for convenience. If this is not a key, this must contain
    // NOT_A_KEY_CODE.
    final public int mKeyCode;

    // Coordinates of the touch event, if relevant. If useful, we may want to replace this with
    // a MotionEvent or something in the future. This is only relevant when the keypress is from
    // a software keyboard obviously, unless there are touch-sensitive hardware keyboards in the
    // future or some other awesome sauce.
    final public int mX;
    final public int mY;

    // Some flags that can't go into the key code. It's a bit field of FLAG_*
    final private int mFlags;

    // The next event, if any. Null if there is no next event yet.
    final public Event mNextEvent;

    // This method is private - to create a new event, use one of the create* utility methods.
    private Event(final int type, final int codePoint, final int keyCode, final int x, final int y,
            final int flags, final Event next) {
        mType = type;
        mCodePoint = codePoint;
        mKeyCode = keyCode;
        mX = x;
        mY = y;
        mFlags = flags;
        mNextEvent = next;
    }

    public static Event createSoftwareKeypressEvent(final int codePoint, final int keyCode,
            final int x, final int y) {
        return new Event(EVENT_INPUT_KEYPRESS, codePoint, keyCode, x, y, FLAG_NONE, null);
    }

    public static Event createHardwareKeypressEvent(final int codePoint, final int keyCode,
            final Event next) {
        return new Event(EVENT_INPUT_KEYPRESS, codePoint, keyCode,
                Constants.EXTERNAL_KEYBOARD_COORDINATE, Constants.EXTERNAL_KEYBOARD_COORDINATE,
                FLAG_NONE, next);
    }

    // This creates an input event for a dead character. @see {@link #FLAG_DEAD}
    public static Event createDeadEvent(final int codePoint, final int keyCode, final Event next) {
        // TODO: add an argument or something if we ever create a software layout with dead keys.
        return new Event(EVENT_INPUT_KEYPRESS, codePoint, keyCode,
                Constants.EXTERNAL_KEYBOARD_COORDINATE, Constants.EXTERNAL_KEYBOARD_COORDINATE,
                FLAG_DEAD, next);
    }

    public static Event createNotHandledEvent() {
        return new Event(EVENT_NOT_HANDLED, NOT_A_CODE_POINT, NOT_A_KEY_CODE,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, FLAG_NONE, null);
    }

    // Returns whether this event is for a dead character. @see {@link #FLAG_DEAD}
    public boolean isDead() {
        return 0 != (FLAG_DEAD & mFlags);
    }

    // TODO: remove this method - we should not have to test this
    public boolean isCommittable() {
        return EVENT_INPUT_KEYPRESS == mType || EVENT_MODE_KEY == mType || EVENT_TOGGLE == mType;
    }
}
