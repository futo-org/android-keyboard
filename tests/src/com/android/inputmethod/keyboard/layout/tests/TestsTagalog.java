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

package com.android.inputmethod.keyboard.layout.tests;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.Spanish;
import com.android.inputmethod.keyboard.layout.customizer.SpanishCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

/**
 * tl: Tagalog/spanish
 */
@SmallTest
public class TestsTagalog extends TestsSpanish {
    private static final Locale LOCALE = new Locale("tl");
    private static final LayoutBase LAYOUT = new Spanish(new TagalogCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class TagalogCustomizer extends SpanishCustomizer {
        TagalogCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getPunctuationMoreKeys(final boolean isPhone) {
            return isPhone ? PHONE_PUNCTUATION_MORE_KEYS : TABLET_PUNCTUATION_MORE_KEYS;
        }
    }
}
