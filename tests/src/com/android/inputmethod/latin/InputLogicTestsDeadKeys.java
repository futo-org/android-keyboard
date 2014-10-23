/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.test.suitebuilder.annotation.LargeTest;

import com.android.inputmethod.event.Event;
import com.android.inputmethod.latin.common.Constants;

import java.util.ArrayList;

@LargeTest
public class InputLogicTestsDeadKeys extends InputTestsBase {
    // A helper class for readability
    static class EventList extends ArrayList<Event> {
        public EventList addCodePoint(final int codePoint, final boolean isDead) {
            final Event event;
            if (isDead) {
                event = Event.createDeadEvent(codePoint, Event.NOT_A_KEY_CODE, null /* next */);
            } else {
                event = Event.createSoftwareKeypressEvent(codePoint, Event.NOT_A_KEY_CODE,
                        Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                        false /* isKeyRepeat */);
            }
            add(event);
            return this;
        }

        public EventList addKey(final int keyCode) {
            add(Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, keyCode,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                    false /* isKeyRepeat */));
            return this;
        }
    }

    public void testDeadCircumflexSimple() {
        final int MODIFIER_LETTER_CIRCUMFLEX_ACCENT = 0x02C6;
        final String EXPECTED_RESULT = "aê";
        final EventList events = new EventList()
                .addCodePoint('a', false)
                .addCodePoint(MODIFIER_LETTER_CIRCUMFLEX_ACCENT, true)
                .addCodePoint('e', false);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("simple dead circumflex", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testDeadCircumflexBackspace() {
        final int MODIFIER_LETTER_CIRCUMFLEX_ACCENT = 0x02C6;
        final String EXPECTED_RESULT = "ae";
        final EventList events = new EventList()
                .addCodePoint('a', false)
                .addCodePoint(MODIFIER_LETTER_CIRCUMFLEX_ACCENT, true)
                .addKey(Constants.CODE_DELETE)
                .addCodePoint('e', false);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("dead circumflex backspace", EXPECTED_RESULT, mEditText.getText().toString());
    }

    public void testDeadCircumflexFeedback() {
        final int MODIFIER_LETTER_CIRCUMFLEX_ACCENT = 0x02C6;
        final String EXPECTED_RESULT = "a\u02C6";
        final EventList events = new EventList()
                .addCodePoint('a', false)
                .addCodePoint(MODIFIER_LETTER_CIRCUMFLEX_ACCENT, true);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("dead circumflex gives feedback", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDeadDiaeresisSpace() {
        final int MODIFIER_LETTER_DIAERESIS = 0xA8;
        final String EXPECTED_RESULT = "a\u00A8e\u00A8i";
        final EventList events = new EventList()
                .addCodePoint('a', false)
                .addCodePoint(MODIFIER_LETTER_DIAERESIS, true)
                .addCodePoint(Constants.CODE_SPACE, false)
                .addCodePoint('e', false)
                .addCodePoint(MODIFIER_LETTER_DIAERESIS, true)
                .addCodePoint(Constants.CODE_ENTER, false)
                .addCodePoint('i', false);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("dead diaeresis space commits the dead char", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDeadAcuteLetterBackspace() {
        final int MODIFIER_LETTER_ACUTE = 0xB4;
        final String EXPECTED_RESULT1 = "aá";
        final String EXPECTED_RESULT2 = "a";
        final EventList events = new EventList()
                .addCodePoint('a', false)
                .addCodePoint(MODIFIER_LETTER_ACUTE, true)
                .addCodePoint('a', false);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("dead acute on a typed", EXPECTED_RESULT1, mEditText.getText().toString());
        mLatinIME.onEvent(Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT,
                Constants.CODE_DELETE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                false /* isKeyRepeat */));
        assertEquals("a with acute deleted", EXPECTED_RESULT2, mEditText.getText().toString());
    }

    public void testFinnishStroke() {
        final int MODIFIER_LETTER_STROKE = '-';
        final String EXPECTED_RESULT = "x\u0110\u0127";
        final EventList events = new EventList()
                .addCodePoint('x', false)
                .addCodePoint(MODIFIER_LETTER_STROKE, true)
                .addCodePoint('D', false)
                .addCodePoint(MODIFIER_LETTER_STROKE, true)
                .addCodePoint('h', false);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("Finnish dead stroke", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDoubleDeadOgonek() {
        final int MODIFIER_LETTER_OGONEK = 0x02DB;
        final String EXPECTED_RESULT = "txǫs\u02DBfk";
        final EventList events = new EventList()
                .addCodePoint('t', false)
                .addCodePoint('x', false)
                .addCodePoint(MODIFIER_LETTER_OGONEK, true)
                .addCodePoint('o', false)
                .addCodePoint('s', false)
                .addCodePoint(MODIFIER_LETTER_OGONEK, true)
                .addCodePoint(MODIFIER_LETTER_OGONEK, true)
                .addCodePoint('f', false)
                .addCodePoint(MODIFIER_LETTER_OGONEK, true)
                .addCodePoint(MODIFIER_LETTER_OGONEK, true)
                .addKey(Constants.CODE_DELETE)
                .addCodePoint('k', false);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("double dead ogonek, and backspace", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDeadCircumflexDeadDiaeresis() {
        final int MODIFIER_LETTER_CIRCUMFLEX_ACCENT = 0x02C6;
        final int MODIFIER_LETTER_DIAERESIS = 0xA8;
        final String EXPECTED_RESULT = "r̂̈";

        final EventList events = new EventList()
                .addCodePoint(MODIFIER_LETTER_CIRCUMFLEX_ACCENT, true)
                .addCodePoint(MODIFIER_LETTER_DIAERESIS, true)
                .addCodePoint('r', false);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("both circumflex and diaeresis on r", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDeadCircumflexDeadDiaeresisBackspace() {
        final int MODIFIER_LETTER_CIRCUMFLEX_ACCENT = 0x02C6;
        final int MODIFIER_LETTER_DIAERESIS = 0xA8;
        final String EXPECTED_RESULT = "û";

        final EventList events = new EventList()
                .addCodePoint(MODIFIER_LETTER_CIRCUMFLEX_ACCENT, true)
                .addCodePoint(MODIFIER_LETTER_DIAERESIS, true)
                .addKey(Constants.CODE_DELETE)
                .addCodePoint('u', false);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("dead circumflex, dead diaeresis, backspace, u", EXPECTED_RESULT,
                mEditText.getText().toString());
    }

    public void testDeadCircumflexDoubleDeadDiaeresisBackspace() {
        final int MODIFIER_LETTER_CIRCUMFLEX_ACCENT = 0x02C6;
        final int MODIFIER_LETTER_DIAERESIS = 0xA8;
        final String EXPECTED_RESULT = "\u02C6u";

        final EventList events = new EventList()
                .addCodePoint(MODIFIER_LETTER_CIRCUMFLEX_ACCENT, true)
                .addCodePoint(MODIFIER_LETTER_DIAERESIS, true)
                .addCodePoint(MODIFIER_LETTER_DIAERESIS, true)
                .addKey(Constants.CODE_DELETE)
                .addCodePoint('u', false);
        for (final Event event : events) {
            mLatinIME.onEvent(event);
        }
        assertEquals("dead circumflex, double dead diaeresis, backspace, u", EXPECTED_RESULT,
                mEditText.getText().toString());
    }
}
