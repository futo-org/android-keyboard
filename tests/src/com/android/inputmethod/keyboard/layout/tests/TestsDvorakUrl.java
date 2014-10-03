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
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.keyboard.layout.Dvorak;
import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.customizer.DvorakCustomizer.EnglishDvorakCustomizer;
import com.android.inputmethod.keyboard.layout.customizer.LayoutCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

/**
 * en_US: English (United States)/dvorak, URL input field.
 */
@SmallTest
public class TestsDvorakUrl extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("en", "US");
    private static final LayoutBase LAYOUT = new DvorakEmail(new DvorakUrlCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    @Override
    protected KeyboardLayoutSet createKeyboardLayoutSet(final InputMethodSubtype subtype,
            final EditorInfo editorInfo, final boolean voiceInputKeyEnabled,
            final boolean languageSwitchKeyEnabled, final boolean splitLayoutEnabled) {
        final EditorInfo emailField = new EditorInfo();
        emailField.inputType =
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
        return super.createKeyboardLayoutSet(
                subtype, emailField, voiceInputKeyEnabled, languageSwitchKeyEnabled,
                splitLayoutEnabled);
    }

    private static class DvorakUrlCustomizer extends EnglishDvorakCustomizer {
        DvorakUrlCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey getEnterKey(final boolean isPhone) {
            return isPhone ? ENTER_KEY : super.getEnterKey(isPhone);
        }

        @Override
        public ExpectedKey getEmojiKey(final boolean isPhone) {
            return DOMAIN_KEY;
        }

        @Override
        public ExpectedKey[] getKeysLeftToSpacebar(final boolean isPhone) {
            return isPhone ? super.getKeysLeftToSpacebar(isPhone)
                    : joinKeys(key("/", SETTINGS_KEY));
        }
    }

    private static class DvorakEmail extends Dvorak {
        DvorakEmail(final LayoutCustomizer customizer) { super(customizer); }

        @Override
        protected ExpectedKey getRow1_1Key(final boolean isPhone, final int elementId) {
            if (isPhone && (elementId == KeyboardId.ELEMENT_ALPHABET
                    || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED)) {
                return key("/", joinMoreKeys(additionalMoreKey("1")));
            }
            return super.getRow1_1Key(isPhone, elementId);
        }
    }
}
