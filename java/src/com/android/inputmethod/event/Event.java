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
    // A character that is already final, for example pressing an alphabetic character on a
    // hardware qwerty keyboard.
    final public static int EVENT_COMMITTABLE = 1;
    // A dead key, which means a character that should combine with what is coming next. Examples
    // include the "^" character on an azerty keyboard which combines with "e" to make "ê", or
    // AltGr+' on a dvorak international keyboard which combines with "e" to make "é". This is
    // true regardless of the language or combining mode, and should be seen as a property of the
    // key - a dead key followed by another key with which it can combine should be regarded as if
    // the keyboard actually had such a key.
    final public static int EVENT_DEAD = 2;
    // A toggle event is triggered by a key that affects the previous character. An example would
    // be a numeric key on a 10-key keyboard, which would toggle between 1 - a - b - c with
    // repeated presses.
    final public static int EVENT_TOGGLE = 3;
    // A mode event instructs the combiner to change modes. The canonical example would be the
    // hankaku/zenkaku key on a Japanese keyboard, or even the caps lock key on a qwerty keyboard
    // if handled at the combiner level.
    final public static int EVENT_MODE_KEY = 4;

    final private static int NOT_A_CODE_POINT = 0;

    final private int mType; // The type of event - one of the constants above
    // The code point associated with the event, if relevant. This is a unicode code point, and
    // has nothing to do with other representations of the key. It is only relevant if this event
    // is the right type: COMMITTABLE or DEAD or TOGGLE, but for a mode key like hankaku/zenkaku or
    // ctrl, there is no code point associated so this should be NOT_A_CODE_POINT to avoid
    // unintentional use of its value when it's not relevant.
    final public int mCodePoint;
    // The next event, if any. Null if there is no next event yet.
    final public Event mNextEvent;

    // This method is private - to create a new event, use one of the create* utility methods.
    private Event(final int type, final int codePoint, final Event next) {
        mType = type;
        mCodePoint = codePoint;
        mNextEvent = next;
    }

    public static Event createDeadEvent(final int codePoint, final Event next) {
        return new Event(EVENT_DEAD, codePoint, next);
    }

    public static Event createCommittableEvent(final int codePoint, final Event next) {
        return new Event(EVENT_COMMITTABLE, codePoint, next);
    }

    public static Event createNotHandledEvent() {
        return new Event(EVENT_NOT_HANDLED, NOT_A_CODE_POINT, null);
    }

    public boolean isCommittable() {
        return EVENT_COMMITTABLE == mType;
    }

    public boolean isDead() {
        return EVENT_DEAD == mType;
    }
}
