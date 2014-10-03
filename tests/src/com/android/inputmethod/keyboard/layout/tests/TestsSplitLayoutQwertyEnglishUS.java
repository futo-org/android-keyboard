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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.keyboard.layout.LayoutBase;
import com.android.inputmethod.keyboard.layout.Qwerty;
import com.android.inputmethod.keyboard.layout.customizer.EnglishCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;

import java.util.Locale;

/**
 * en_US: English (United States)/qwerty - split layout
 */
@SmallTest
public class TestsSplitLayoutQwertyEnglishUS extends LayoutTestsBase {
    private static final Locale LOCALE = new Locale("en", "US");
    private static final LayoutBase LAYOUT = new Qwerty(new EnglishSplitCustomizer(LOCALE));

    @Override
    protected KeyboardLayoutSet createKeyboardLayoutSet(final InputMethodSubtype subtype,
            final EditorInfo editorInfo, final boolean voiceInputKeyEnabled,
            final boolean languageSwitchKeyEnabled, final boolean splitLayoutEnabled) {
        return super.createKeyboardLayoutSet(subtype, editorInfo, voiceInputKeyEnabled,
            languageSwitchKeyEnabled, true /* splitLayoutEnabled */);
    }

    @Override
    LayoutBase getLayout() { return LAYOUT; }

    private static class EnglishSplitCustomizer extends EnglishCustomizer {
        EnglishSplitCustomizer(Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getSpaceKeys(final boolean isPhone) {
            if (isPhone) {
                return super.getSpaceKeys(isPhone);
            }
            return joinKeys(LANGUAGE_SWITCH_KEY, SPACE_KEY, SPACE_KEY);
        }
    }
}
