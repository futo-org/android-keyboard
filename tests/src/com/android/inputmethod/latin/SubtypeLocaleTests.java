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
import java.util.Locale;

public class SubtypeLocaleTests extends AndroidTestCase {
    private static final Locale LOCALE_zz_QY = SubtypeLocale.LOCALE_NO_LANGUAGE_QWERTY;
    private static final Locale LOCALE_de_QY =
            new Locale(Locale.GERMAN.getLanguage(), SubtypeLocale.QWERTY);

    private ArrayList<InputMethodSubtype> mSubtypesList;

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
                mSubtypesList = new ArrayList<InputMethodSubtype>();
                final int subtypeCount = imi.getSubtypeCount();
                for (int i = 0; i < subtypeCount; i++) {
                    final InputMethodSubtype ims = imi.getSubtypeAt(i);
                    mSubtypesList.add(ims);
                }
                break;
            }
        }
        assertNotNull("Can not find input method " + packageName, mSubtypesList);
        assertTrue("Can not find keyboard subtype", mSubtypesList.size() > 0);
    }

    private static Locale getSubtypeLocale(InputMethodSubtype subtype) {
        return LocaleUtils.constructLocaleFromString(subtype.getLocale());
    }

    private static Locale getKeyboardLocale(InputMethodSubtype subtype) {
        final String subtypeLocaleString = subtype.containsExtraValueKey(
                LatinIME.SUBTYPE_EXTRA_VALUE_KEYBOARD_LOCALE)
                ? subtype.getExtraValueOf(LatinIME.SUBTYPE_EXTRA_VALUE_KEYBOARD_LOCALE)
                : subtype.getLocale();
        return LocaleUtils.constructLocaleFromString(subtypeLocaleString);
    }

    public void testFullDisplayName() {
        final StringBuilder messages = new StringBuilder();
        int failedCount = 0;
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final Locale locale = getKeyboardLocale(subtype);
            if (locale.getLanguage().equals(SubtypeLocale.NO_LANGUAGE)) {
                // This is special language name for language agnostic usage.
                continue;
            }
            final String keyboardName = SubtypeLocale.getFullDisplayName(locale);
            final String languageName = SubtypeLocale.toTitleCase(
                    locale.getDisplayLanguage(locale), locale);
            if (!keyboardName.contains(languageName)) {
                failedCount++;
                messages.append(String.format(
                        "locale %s: keyboard name '%s' should contain language name '%s'\n",
                        locale, keyboardName, languageName));
            }
        }
        assertEquals(messages.toString(), 0, failedCount);
    }

    public void testFullDisplayNameNoLanguage() {
        assertEquals("zz_QY", "QWERTY", SubtypeLocale.getFullDisplayName(LOCALE_zz_QY));

        final String de_QY = SubtypeLocale.getFullDisplayName(LOCALE_de_QY);
        assertTrue("de_QY", de_QY.contains("(QWERTY"));
        assertTrue("de_QY", de_QY.contains(Locale.GERMAN.getDisplayLanguage(Locale.GERMAN)));
    }

    public void testMiddleDisplayName() {
        final StringBuilder messages = new StringBuilder();
        int failedCount = 0;
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final Locale locale = getKeyboardLocale(subtype);
            if (locale.getLanguage().equals(SubtypeLocale.NO_LANGUAGE)) {
                // This is special language name for language agnostic usage.
                continue;
            }
            final String keyboardName = SubtypeLocale.getMiddleDisplayName(locale);
            final String languageName = SubtypeLocale.toTitleCase(
                    locale.getDisplayLanguage(locale), locale);
            if (!keyboardName.equals(languageName)) {
                failedCount++;
                messages.append(String.format(
                        "locale %s: keyboard name '%s' should be equals to language name '%s'\n",
                        locale, keyboardName, languageName));
            }
        }
        assertEquals(messages.toString(), 0, failedCount);
    }

    public void testMiddleDisplayNameNoLanguage() {
        assertEquals("zz_QY", "QWERTY", SubtypeLocale.getMiddleDisplayName(LOCALE_zz_QY));
        assertEquals("de_QY", "Deutsch", SubtypeLocale.getMiddleDisplayName(LOCALE_de_QY));
    }

    public void testShortDisplayName() {
        final StringBuilder messages = new StringBuilder();
        int failedCount = 0;
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final Locale locale = getKeyboardLocale(subtype);
            if (locale.getCountry().equals(SubtypeLocale.QWERTY)) {
                // This is special country code for QWERTY keyboard.
                continue;
            }
            final String keyboardName = SubtypeLocale.getShortDisplayName(locale);
            final String languageCode = SubtypeLocale.toTitleCase(locale.getLanguage(), locale);
            if (!keyboardName.equals(languageCode)) {
                failedCount++;
                messages.append(String.format(
                        "locale %s: keyboard name '%s' should be equals to language code '%s'\n",
                        locale, keyboardName, languageCode));
            }
        }
        assertEquals(messages.toString(), 0, failedCount);
    }

    public void testShortDisplayNameNoLanguage() {
        assertEquals("zz_QY", "QY", SubtypeLocale.getShortDisplayName(LOCALE_zz_QY));
        assertEquals("de_QY", "De", SubtypeLocale.getShortDisplayName(LOCALE_de_QY));
    }
}
