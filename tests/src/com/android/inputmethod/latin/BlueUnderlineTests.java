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

package com.android.inputmethod.latin;

import android.test.suitebuilder.annotation.LargeTest;
import android.text.style.SuggestionSpan;
import android.text.style.UnderlineSpan;

import com.android.inputmethod.latin.common.Constants;

@LargeTest
public class BlueUnderlineTests extends InputTestsBase {

    public void testBlueUnderline() {
        final String STRING_TO_TYPE = "tgis";
        final int EXPECTED_SPAN_START = 0;
        final int EXPECTED_SPAN_END = 4;
        type(STRING_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        final SpanGetter span = new SpanGetter(mEditText.getText(), SuggestionSpan.class);
        assertEquals("show blue underline, span start", EXPECTED_SPAN_START, span.mStart);
        assertEquals("show blue underline, span end", EXPECTED_SPAN_END, span.mEnd);
        assertEquals("show blue underline, span color", true, span.isAutoCorrectionIndicator());
    }

    public void testBlueUnderlineDisappears() {
        final String STRING_1_TO_TYPE = "tqis";
        final String STRING_2_TO_TYPE = "g";
        final int EXPECTED_SPAN_START = 0;
        final int EXPECTED_SPAN_END = 5;
        type(STRING_1_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        type(STRING_2_TO_TYPE);
        // We haven't have time to look into the dictionary yet, so the line should still be
        // blue to avoid any flicker.
        final SpanGetter spanBefore = new SpanGetter(mEditText.getText(), SuggestionSpan.class);
        assertEquals("extend blue underline, span start", EXPECTED_SPAN_START, spanBefore.mStart);
        assertEquals("extend blue underline, span end", EXPECTED_SPAN_END, spanBefore.mEnd);
        assertTrue("extend blue underline, span color", spanBefore.isAutoCorrectionIndicator());
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        // Now we have been able to re-evaluate the word, there shouldn't be an auto-correction span
        final SpanGetter spanAfter = new SpanGetter(mEditText.getText(), SuggestionSpan.class);
        assertNull("hide blue underline", spanAfter.mSpan);
    }

    public void testBlueUnderlineOnBackspace() {
        final String STRING_TO_TYPE = "tgis";
        final int typedLength = STRING_TO_TYPE.length();
        final int EXPECTED_UNDERLINE_SPAN_START = 0;
        final int EXPECTED_UNDERLINE_SPAN_END = 3;
        type(STRING_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        type(Constants.CODE_SPACE);
        // typedLength + 1 because we also typed a space
        mLatinIME.onUpdateSelection(0, 0, typedLength + 1, typedLength + 1, -1, -1);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        type(Constants.CODE_DELETE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        type(Constants.CODE_DELETE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        final SpanGetter suggestionSpan = new SpanGetter(mEditText.getText(), SuggestionSpan.class);
        assertFalse("show no blue underline after backspace, span should not be the auto-"
                + "correction indicator", suggestionSpan.isAutoCorrectionIndicator());
        final SpanGetter underlineSpan = new SpanGetter(mEditText.getText(), UnderlineSpan.class);
        assertEquals("should be composing, so should have an underline span",
                EXPECTED_UNDERLINE_SPAN_START, underlineSpan.mStart);
        assertEquals("should be composing, so should have an underline span",
                EXPECTED_UNDERLINE_SPAN_END, underlineSpan.mEnd);
    }

    public void testBlueUnderlineDisappearsWhenCursorMoved() {
        final String STRING_TO_TYPE = "tgis";
        final int typedLength = STRING_TO_TYPE.length();
        final int NEW_CURSOR_POSITION = 0;
        type(STRING_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        // Simulate the onUpdateSelection() event
        mLatinIME.onUpdateSelection(0, 0, typedLength, typedLength, -1, -1);
        runMessages();
        // Here the blue underline has been set. testBlueUnderline() is testing for this already,
        // so let's not test it here again.
        // Now simulate the user moving the cursor.
        mInputConnection.setSelection(NEW_CURSOR_POSITION, NEW_CURSOR_POSITION);
        mLatinIME.onUpdateSelection(typedLength, typedLength,
                NEW_CURSOR_POSITION, NEW_CURSOR_POSITION, -1, -1);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        runMessages();
        final SpanGetter span = new SpanGetter(mEditText.getText(), SuggestionSpan.class);
        assertFalse("blue underline removed when cursor is moved",
                span.isAutoCorrectionIndicator());
    }

    public void testComposingStopsOnSpace() {
        final String STRING_TO_TYPE = "this ";
        type(STRING_TO_TYPE);
        sleep(DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS);
        // Simulate the onUpdateSelection() event
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        runMessages();
        // Here the blue underline has been set. testBlueUnderline() is testing for this already,
        // so let's not test it here again.
        // Now simulate the user moving the cursor.
        SpanGetter span = new SpanGetter(mEditText.getText(), UnderlineSpan.class);
        assertNull("should not be composing, so should not have an underline span", span.mSpan);
    }
}
