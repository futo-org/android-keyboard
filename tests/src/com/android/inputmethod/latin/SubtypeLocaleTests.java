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
    private List<InputMethodSubtype> mKeyboardSubtypes;

    public interface Predicator<T> {
        public boolean evaluate(T object);
    }

    private static <T> List<T> filter(List<T> source, Predicator<? super T> predicator) {
        final ArrayList<T> filtered = new ArrayList<T>();
        for (final T element : source) {
            if (predicator.evaluate(element))
                filtered.add(element);
        }
        return filtered;
    }

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
                mKeyboardSubtypes = filter(imi.getSubtypes(),
                        new Predicator<InputMethodSubtype>() {
                            @Override
                            public boolean evaluate(InputMethodSubtype ims) {
                                return ims.getMode().equals("keyboard");
                            }
                });
                break;
            }
        }
        assertNotNull("Can not find input method " + PACKAGE, mKeyboardSubtypes);
        assertTrue("Can not find keyboard subtype", mKeyboardSubtypes.size() > 0);
    }

    // Copied from {@link java.junit.Assert#format(String, Object, Object)}
    private static String format(String message, Object expected, Object actual) {
        return message + " expected:<" + expected + "> but was:<" + actual + ">";
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
        for (final InputMethodSubtype subtype : mKeyboardSubtypes) {
            final String localeCode = subtype.getLocale();
            final Locale locale = new Locale(localeCode);
            // The locale name which will be displayed on spacebar.  For example 'English (US)' or
            // 'Francais (Canada)'.  (c=\u008d)
            final String displayName = SubtypeLocale.getFullDisplayName(locale);
            // The subtype name in its locale.  For example 'English (US) Keyboard' or
            // 'Clavier Francais (Canada)'.  (c=\u008d)
            final String subtypeName = getStringWithLocale(subtype.getNameResId(), locale);
            assertTrue(
                    format("subtype display name of " + localeCode + ":", subtypeName, displayName),
                    subtypeName.contains(displayName));
        }
    }
}
