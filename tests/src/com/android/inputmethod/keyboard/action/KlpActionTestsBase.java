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

package com.android.inputmethod.keyboard.action;

import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;
import java.util.Locale;

abstract class KlpActionTestsBase extends ActionTestsBase {
    // Filter a subtype whose name should be displayed using {@link Locale#ROOT}, such like
    // Hinglish (hi_ZZ) and Serbian-Latn (sr_ZZ).
    static final SubtypeFilter SUBTYPE_FILTER_NAME_IN_BASE_LOCALE = new SubtypeFilter() {
        @Override
        public boolean accept(final InputMethodSubtype subtype) {
            return Locale.ROOT.equals(
                    SubtypeLocaleUtils.getDisplayLocaleOfSubtypeLocale(subtype.getLocale()));
        }
    };

    protected ArrayList<InputMethodSubtype> mSubtypesWhoseNameIsDisplayedInItsLocale;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSubtypesWhoseNameIsDisplayedInItsLocale = getSubtypesFilteredBy(new SubtypeFilter() {
            @Override
            public boolean accept(final InputMethodSubtype subtype) {
                return !SUBTYPE_FILTER_NAME_IN_BASE_LOCALE.accept(subtype);
            }
        });
    }

    @Override
    protected int getKeyboardThemeForTests() {
        return KeyboardTheme.THEME_ID_KLP;
    }
}
