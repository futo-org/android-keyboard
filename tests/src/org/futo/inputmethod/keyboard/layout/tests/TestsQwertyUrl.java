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

package org.futo.inputmethod.keyboard.layout.tests;

import android.test.suitebuilder.annotation.SmallTest;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import org.futo.inputmethod.keyboard.layout.LayoutBase;
import org.futo.inputmethod.keyboard.layout.Qwerty;
import org.futo.inputmethod.keyboard.layout.customizer.EnglishCustomizer;
import org.futo.inputmethod.keyboard.layout.expected.ExpectedKey;
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2;

import java.util.Locale;

/**
 * en_US: English (United States)/qwerty, URL input field.
 */
@SmallTest
public class TestsQwertyUrl extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("en", "US");
    private static final LayoutBase LAYOUT = new Qwerty(new EnglishUrlCustomizer(LOCALE));

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    @Override
    protected KeyboardLayoutSetV2 createKeyboardLayoutSet(final InputMethodSubtype subtype,
                                                          final EditorInfo editorInfo, final boolean voiceInputKeyEnabled,
                                                          final boolean languageSwitchKeyEnabled, final boolean splitLayoutEnabled) {
        final EditorInfo emailField = new EditorInfo();
        emailField.inputType =
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
        return super.createKeyboardLayoutSet(
                subtype, emailField, voiceInputKeyEnabled, languageSwitchKeyEnabled,
                splitLayoutEnabled);
    }

    private static class EnglishUrlCustomizer extends EnglishCustomizer {
        EnglishUrlCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getKeysRightToSpacebar(final boolean isPhone) {
            final ExpectedKey periodKey = key(".", getPunctuationMoreKeys(isPhone));
            return joinKeys(key("/"), periodKey);
        }
    }
}
