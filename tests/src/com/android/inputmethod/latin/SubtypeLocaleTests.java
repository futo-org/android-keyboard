/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.content.Context;
import android.test.AndroidTestCase;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SubtypeLocaleTests extends AndroidTestCase {
    private List<InputMethodSubtype> mKeyboardSubtypes;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final Context context = getContext();
        final String packageName = context.getApplicationInfo().packageName;

        SubtypeLocale.init(context);

        final InputMethodManager imm = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        for (final InputMethodInfo imi : imm.getInputMethodList()) {
            if (imi.getPackageName().equals(packageName)) {
                mKeyboardSubtypes = new ArrayList<InputMethodSubtype>();
                final int subtypeCount = imi.getSubtypeCount();
                for (int i = 0; i < subtypeCount; ++i) {
                    InputMethodSubtype subtype = imi.getSubtypeAt(i);
                    if (subtype.getMode().equals("keyboard")) {
                        mKeyboardSubtypes.add(subtype);
                    }
                }
                break;
            }
        }
        assertNotNull("Can not find input method " + packageName, mKeyboardSubtypes);
        assertTrue("Can not find keyboard subtype", mKeyboardSubtypes.size() > 0);
    }

    public void testSubtypeLocale() {
        final StringBuilder messages = new StringBuilder();
        int failedCount = 0;
        for (final InputMethodSubtype subtype : mKeyboardSubtypes) {
            final Locale locale = LocaleUtils.constructLocaleFromString(subtype.getLocale());
            if (locale.getLanguage().equals("zz")) {
                // This is special language name for language agnostic usage.
                continue;
            }
            final String subtypeLocaleString =
                    subtype.containsExtraValueKey(LatinIME.SUBTYPE_EXTRA_VALUE_KEYBOARD_LOCALE)
                    ? subtype.getExtraValueOf(LatinIME.SUBTYPE_EXTRA_VALUE_KEYBOARD_LOCALE)
                    : subtype.getLocale();
            final Locale subtypeLocale = LocaleUtils.constructLocaleFromString(subtypeLocaleString);
            // The subtype name in its locale.  For example 'English (US)' or 'Deutsch (QWERTY)'.
            final String subtypeName = SubtypeLocale.getFullDisplayName(subtypeLocale);
            // The locale language name in its locale.
            final String languageName = locale.getDisplayLanguage(locale);
            if (!subtypeName.contains(languageName)) {
                failedCount++;
                messages.append(String.format(
                        "subtype name is '%s' and should contain locale '%s' language name '%s'\n",
                        subtypeName, subtypeLocale, languageName));
            }
        }
        assertEquals(messages.toString(), 0, failedCount);
    }
}
