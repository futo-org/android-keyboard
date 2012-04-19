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
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;

import java.util.ArrayList;
import java.util.Locale;

public class SubtypeLocaleTests extends AndroidTestCase {
    // Locale to subtypes list.
    private final ArrayList<InputMethodSubtype> mSubtypesList = new ArrayList<InputMethodSubtype>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getContext();
        InputMethodManagerCompatWrapper.init(context);
        SubtypeLocale.init(context);
    }

    public void testAllFullDisplayName() {
        final StringBuilder messages = new StringBuilder();
        int failedCount = 0;
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
            if (SubtypeLocale.isNoLanguage(subtype)) {
                // This is special language name for language agnostic usage.
                continue;
            }
            final String keyboardName = SubtypeLocale.getFullDisplayName(subtype);
            final String languageName = StringUtils.toTitleCase(
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

   public void testAllMiddleDisplayName() {
        final StringBuilder messages = new StringBuilder();
        int failedCount = 0;
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
            if (SubtypeLocale.isNoLanguage(subtype)) {
                // This is special language name for language agnostic usage.
                continue;
            }
            final String keyboardName = SubtypeLocale.getMiddleDisplayName(subtype);
            final String languageName = StringUtils.toTitleCase(
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

    public void testAllShortDisplayName() {
        final StringBuilder messages = new StringBuilder();
        int failedCount = 0;
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
            final String keyboardName = SubtypeLocale.getShortDisplayName(subtype);
            final String languageCode = StringUtils.toTitleCase(locale.getLanguage(), locale);
            if (!keyboardName.equals(languageCode)) {
                failedCount++;
                messages.append(String.format(
                        "locale %s: keyboard name '%s' should be equals to language code '%s'\n",
                        locale, keyboardName, languageCode));
            }
        }
        assertEquals(messages.toString(), 0, failedCount);
    }

    // Get InputMethodSubtype's display name in its locale.
    //            additional
    // locale layout  Short  Middle      Full
    // ------ ------ - ---- --------- -----------------
    //  en_US qwerty F  En  English   English (US)      exception
    //  en_GB qwerty F  En  English   English (UK)      exception
    //  fr    azerty F  Fr  Français  Français
    //  fr_CA qwerty F  Fr  Français  Français (Canada)
    //  de    qwertz F  De  Deutsch   Deutsch
    //  zz    qwerty F      QWERTY    QWERTY
    //  fr    qwertz T  Fr  Français  Français (QWERTZ)
    //  de    qwerty T  De  Deutsch   Deutsch (QWERTY)
    //  zz    azerty T      AZERTY    AZERTY

    public void testSampleSubtypes() {
        final Context context = getContext();
        final InputMethodSubtype EN_US = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.US.toString(), AdditionalSubtype.QWERTY);
        final InputMethodSubtype EN_GB = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.UK.toString(), AdditionalSubtype.QWERTY);
        final InputMethodSubtype FR = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.FRENCH.toString(), AdditionalSubtype.AZERTY);
        final InputMethodSubtype FR_CA = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.CANADA_FRENCH.toString(), AdditionalSubtype.QWERTY);
        final InputMethodSubtype DE = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.GERMAN.toString(), AdditionalSubtype.QWERTZ);
        final InputMethodSubtype ZZ = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, SubtypeLocale.NO_LANGUAGE, AdditionalSubtype.QWERTY);

        assertFalse(AdditionalSubtype.isAdditionalSubtype(EN_US));
        assertFalse(AdditionalSubtype.isAdditionalSubtype(EN_GB));
        assertFalse(AdditionalSubtype.isAdditionalSubtype(FR));
        assertFalse(AdditionalSubtype.isAdditionalSubtype(FR_CA));
        assertFalse(AdditionalSubtype.isAdditionalSubtype(DE));
        assertFalse(AdditionalSubtype.isAdditionalSubtype(ZZ));

        assertEquals("en_US", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_US));
        assertEquals("en_GB", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_GB));
        assertEquals("fr   ", "azerty", SubtypeLocale.getKeyboardLayoutSetName(FR));
        assertEquals("fr_CA", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(FR_CA));
        assertEquals("de   ", "qwertz", SubtypeLocale.getKeyboardLayoutSetName(DE));
        assertEquals("zz   ", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(ZZ));

        assertEquals("en_US", "English (US)",      SubtypeLocale.getFullDisplayName(EN_US));
        assertEquals("en_GB", "English (UK)",      SubtypeLocale.getFullDisplayName(EN_GB));
        assertEquals("fr   ", "Français",          SubtypeLocale.getFullDisplayName(FR));
        assertEquals("fr_CA", "Français (Canada)", SubtypeLocale.getFullDisplayName(FR_CA));
        assertEquals("de   ", "Deutsch",           SubtypeLocale.getFullDisplayName(DE));
        assertEquals("zz   ", "QWERTY",            SubtypeLocale.getFullDisplayName(ZZ));

        assertEquals("en_US", "English",  SubtypeLocale.getMiddleDisplayName(EN_US));
        assertEquals("en_GB", "English",  SubtypeLocale.getMiddleDisplayName(EN_GB));
        assertEquals("fr   ", "Français", SubtypeLocale.getMiddleDisplayName(FR));
        assertEquals("fr_CA", "Français", SubtypeLocale.getMiddleDisplayName(FR_CA));
        assertEquals("de   ", "Deutsch",  SubtypeLocale.getMiddleDisplayName(DE));
        assertEquals("zz   ", "QWERTY",   SubtypeLocale.getMiddleDisplayName(ZZ));

        assertEquals("en_US", "En", SubtypeLocale.getShortDisplayName(EN_US));
        assertEquals("en_GB", "En", SubtypeLocale.getShortDisplayName(EN_GB));
        assertEquals("fr   ", "Fr", SubtypeLocale.getShortDisplayName(FR));
        assertEquals("fr_CA", "Fr", SubtypeLocale.getShortDisplayName(FR_CA));
        assertEquals("de   ", "De", SubtypeLocale.getShortDisplayName(DE));
        assertEquals("zz   ", "", SubtypeLocale.getShortDisplayName(ZZ));
    }

    public void testAdditionalSubtype() {
        final InputMethodSubtype DE_QWERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.GERMAN.toString(), AdditionalSubtype.QWERTY, null);
        final InputMethodSubtype FR_QWERTZ = AdditionalSubtype.createAdditionalSubtype(
                Locale.FRENCH.toString(), AdditionalSubtype.QWERTZ, null);
        final InputMethodSubtype EN_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.ENGLISH.toString(), AdditionalSubtype.AZERTY, null);
        final InputMethodSubtype ZZ_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                SubtypeLocale.NO_LANGUAGE, AdditionalSubtype.AZERTY, null);

        assertTrue(AdditionalSubtype.isAdditionalSubtype(FR_QWERTZ));
        assertTrue(AdditionalSubtype.isAdditionalSubtype(DE_QWERTY));
        assertTrue(AdditionalSubtype.isAdditionalSubtype(EN_AZERTY));
        assertTrue(AdditionalSubtype.isAdditionalSubtype(ZZ_AZERTY));

        assertEquals("fr qwertz", "Français (QWERTZ)", SubtypeLocale.getFullDisplayName(FR_QWERTZ));
        assertEquals("de qwerty", "Deutsch (QWERTY)",  SubtypeLocale.getFullDisplayName(DE_QWERTY));
        assertEquals("en azerty", "English (AZERTY)",  SubtypeLocale.getFullDisplayName(EN_AZERTY));
        assertEquals("zz azerty", "AZERTY",            SubtypeLocale.getFullDisplayName(ZZ_AZERTY));

        assertEquals("fr qwertz", "Français", SubtypeLocale.getMiddleDisplayName(FR_QWERTZ));
        assertEquals("de qwerty", "Deutsch",  SubtypeLocale.getMiddleDisplayName(DE_QWERTY));
        assertEquals("en azerty", "English",  SubtypeLocale.getMiddleDisplayName(EN_AZERTY));
        assertEquals("zz azerty", "AZERTY",   SubtypeLocale.getMiddleDisplayName(ZZ_AZERTY));

        assertEquals("fr qwertz", "Fr", SubtypeLocale.getShortDisplayName(FR_QWERTZ));
        assertEquals("de qwerty", "De", SubtypeLocale.getShortDisplayName(DE_QWERTY));
        assertEquals("en azerty", "En", SubtypeLocale.getShortDisplayName(EN_AZERTY));
        assertEquals("zz azerty", "", SubtypeLocale.getShortDisplayName(ZZ_AZERTY));
    }
}
