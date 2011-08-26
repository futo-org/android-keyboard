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

import com.android.inputmethod.latin.LocaleUtils;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SubtypeLocaleTests extends AndroidTestCase {
    private static final String PACKAGE = LatinIME.class.getPackage().getName();

    private Resources mRes;
    private List<InputMethodSubtype> mKeyboardSubtypes = new ArrayList<InputMethodSubtype>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final Context context = getContext();
        mRes = context.getResources();

        SubtypeLocale.init(context);

        final InputMethodManager imm = (InputMethodManager) context.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        for (final InputMethodInfo imi : imm.getInputMethodList()) {
            if (imi.getPackageName().equals(PACKAGE)) {
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
        assertNotNull("Can not find input method " + PACKAGE, mKeyboardSubtypes);
        assertTrue("Can not find keyboard subtype", mKeyboardSubtypes.size() > 0);
    }

    private String getStringWithLocale(int resId, Locale locale) {
        final Locale savedLocale = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            return mRes.getString(resId);
        } finally {
            Locale.setDefault(savedLocale);
        }
    }

    public void testSubtypeLocale() {
        final StringBuilder messages = new StringBuilder();
        int failedCount = 0;
        for (final InputMethodSubtype subtype : mKeyboardSubtypes) {
            final String localeCode = subtype.getLocale();
            final Locale locale = LocaleUtils.constructLocaleFromString(localeCode);
            // The locale name which will be displayed on spacebar.  For example 'English (US)' or
            // 'Francais (Canada)'.  (c=\u008d)
            final String displayName = SubtypeLocale.getFullDisplayName(locale);
            // The subtype name in its locale.  For example 'English (US) Keyboard' or
            // 'Clavier Francais (Canada)'.  (c=\u008d)
            final String subtypeName = getStringWithLocale(subtype.getNameResId(), locale);
            if (subtypeName.contains(displayName)) {
                failedCount++;
                messages.append(String.format(
                        "subtype name is '%s' and should contain locale '%s' name '%s'\n",
                        subtypeName, localeCode, displayName));
            }
        }
        assertEquals(messages.toString(), 0, failedCount);
    }
}
