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

package com.android.inputmethod.latin.spellcheck;

import android.test.suitebuilder.annotation.LargeTest;
import android.text.style.SuggestionSpan;

import com.android.inputmethod.latin.InputTestsBase;

@LargeTest
public class AndroidSpellCheckerServiceTest extends InputTestsBase {
    public void testSpellchecker() {
        changeLanguage("en_US");
        mEditText.setText("tgis ");
        mEditText.setSelection(mEditText.getText().length());
        mEditText.onAttachedToWindow();
        sleep(1000);
        runMessages();
        sleep(1000);

        final SpanGetter span = new SpanGetter(mEditText.getText(), SuggestionSpan.class);
        // If no span, the following will crash
        final String[] suggestions = span.getSuggestions();
        // For this test we consider "tgis" should yield at least 2 suggestions (at this moment
        // it yields 5).
        assertTrue(suggestions.length >= 2);
        // We also assume the top suggestion should be "this".
        assertEquals("Test basic spell checking", "this", suggestions[0]);
    }

    public void testRussianSpellchecker() {
        changeLanguage("ru");
        mEditText.onAttachedToWindow();
        mEditText.setText("годп ");
        mEditText.setSelection(mEditText.getText().length());
        mEditText.onAttachedToWindow();
        sleep(1000);
        runMessages();
        sleep(1000);

        final SpanGetter span = new SpanGetter(mEditText.getText(), SuggestionSpan.class);
        // We don't ship with Russian LM
        assertNull(span.getSpan());
    }

    public void testSpellcheckWithPeriods() {
        changeLanguage("en_US");
        mEditText.setText("I'm.sure ");
        mEditText.setSelection(mEditText.getText().length());
        mEditText.onAttachedToWindow();
        sleep(1000);
        runMessages();
        sleep(1000);

        final SpanGetter span = new SpanGetter(mEditText.getText(), SuggestionSpan.class);
        // If no span, the following will crash
        final String[] suggestions = span.getSuggestions();
        // The first suggestion should be "I'm sure".
        assertEquals("Test spell checking of mistyped period for space", "I'm sure",
                suggestions[0]);
    }
}
