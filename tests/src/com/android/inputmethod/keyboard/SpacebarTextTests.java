/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.AdditionalSubtype;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.ImfUtils;
import com.android.inputmethod.latin.StringUtils;
import com.android.inputmethod.latin.SubtypeLocale;

import java.util.ArrayList;
import java.util.Locale;

public class SpacebarTextTests extends AndroidTestCase {
    // Locale to subtypes list.
    private final ArrayList<InputMethodSubtype> mSubtypesList = CollectionUtils.newArrayList();

    private Resources mRes;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getContext();
        mRes = context.getResources();
        SubtypeLocale.init(context);
    }

    public void testAllFullDisplayName() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocale.getSubtypeDisplayName(subtype, mRes);
            final String spacebarText = MainKeyboardView.getFullDisplayName(subtype, mRes);
            final String languageName =
                    SubtypeLocale.getSubtypeLocaleDisplayName(subtype.getLocale());
            if (SubtypeLocale.isNoLanguage(subtype)) {
                assertFalse(subtypeName, spacebarText.contains(languageName));
            } else {
                assertTrue(subtypeName, spacebarText.contains(languageName));
            }
        }
    }

   public void testAllMiddleDisplayName() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocale.getSubtypeDisplayName(subtype, mRes);
            final String spacebarText = MainKeyboardView.getMiddleDisplayName(subtype);
            if (SubtypeLocale.isNoLanguage(subtype)) {
                assertEquals(subtypeName,
                        SubtypeLocale.getKeyboardLayoutSetName(subtype), spacebarText);
            } else {
                assertEquals(subtypeName,
                        SubtypeLocale.getSubtypeLocaleDisplayName(subtype.getLocale()),
                        spacebarText);
            }
        }
    }

    public void testAllShortDisplayName() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocale.getSubtypeDisplayName(subtype, mRes);
            final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
            final String spacebarText = MainKeyboardView.getShortDisplayName(subtype);
            final String languageCode = StringUtils.toTitleCase(locale.getLanguage(), locale);
            if (SubtypeLocale.isNoLanguage(subtype)) {
                assertEquals(subtypeName, "", spacebarText);
            } else {
                assertEquals(subtypeName, languageCode, spacebarText);
            }
        }
    }

    // InputMethodSubtype's display name for spacebar text in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout | Short  Middle      Full
    // ------ ------ - ---- --------- ----------------------
    //  en_US qwerty F  En  English   English (US)           exception
    //  en_GB qwerty F  En  English   English (UK)           exception
    //  fr    azerty F  Fr  Français  Français
    //  fr_CA qwerty F  Fr  Français  Français (Canada)
    //  de    qwertz F  De  Deutsch   Deutsch
    //  zz    qwerty F      QWERTY    QWERTY
    //  fr    qwertz T  Fr  Français  Français (QWERTZ)
    //  de    qwerty T  De  Deutsch   Deutsch (QWERTY)
    //  en_US azerty T  En  English   English (US) (AZERTY)
    //  zz    azerty T      AZERTY    AZERTY

    public void testPredefinedSubtypes() {
        final Context context = getContext();
        final InputMethodSubtype EN_US = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.US.toString(), "qwerty");
        final InputMethodSubtype EN_GB = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.UK.toString(), "qwerty");
        final InputMethodSubtype FR = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.FRENCH.toString(), "azerty");
        final InputMethodSubtype FR_CA = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.CANADA_FRENCH.toString(), "qwerty");
        final InputMethodSubtype DE = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, Locale.GERMAN.toString(), "qwertz");
        final InputMethodSubtype ZZ = ImfUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                context, SubtypeLocale.NO_LANGUAGE, "qwerty");

        assertEquals("en_US", "English (US)",
                MainKeyboardView.getFullDisplayName(EN_US, mRes));
        assertEquals("en_GB", "English (UK)",
                MainKeyboardView.getFullDisplayName(EN_GB, mRes));
        assertEquals("fr   ", "Français",
                MainKeyboardView.getFullDisplayName(FR, mRes));
        assertEquals("fr_CA", "Français (Canada)",
                MainKeyboardView.getFullDisplayName(FR_CA, mRes));
        assertEquals("de   ", "Deutsch",
                MainKeyboardView.getFullDisplayName(DE, mRes));
        assertEquals("zz   ", "QWERTY",
                MainKeyboardView.getFullDisplayName(ZZ, mRes));

        assertEquals("en_US", "English",  MainKeyboardView.getMiddleDisplayName(EN_US));
        assertEquals("en_GB", "English",  MainKeyboardView.getMiddleDisplayName(EN_GB));
        assertEquals("fr   ", "Français", MainKeyboardView.getMiddleDisplayName(FR));
        assertEquals("fr_CA", "Français", MainKeyboardView.getMiddleDisplayName(FR_CA));
        assertEquals("de   ", "Deutsch",  MainKeyboardView.getMiddleDisplayName(DE));
        assertEquals("zz   ", "QWERTY",   MainKeyboardView.getMiddleDisplayName(ZZ));

        assertEquals("en_US", "En", MainKeyboardView.getShortDisplayName(EN_US));
        assertEquals("en_GB", "En", MainKeyboardView.getShortDisplayName(EN_GB));
        assertEquals("fr   ", "Fr", MainKeyboardView.getShortDisplayName(FR));
        assertEquals("fr_CA", "Fr", MainKeyboardView.getShortDisplayName(FR_CA));
        assertEquals("de   ", "De", MainKeyboardView.getShortDisplayName(DE));
        assertEquals("zz   ", "",   MainKeyboardView.getShortDisplayName(ZZ));
    }

    public void testAdditionalSubtype() {
        final InputMethodSubtype DE_QWERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.GERMAN.toString(), "qwerty", null);
        final InputMethodSubtype FR_QWERTZ = AdditionalSubtype.createAdditionalSubtype(
                Locale.FRENCH.toString(), "qwertz", null);
        final InputMethodSubtype US_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.US.toString(), "azerty", null);
        final InputMethodSubtype ZZ_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                SubtypeLocale.NO_LANGUAGE, "azerty", null);

        assertEquals("fr qwertz",    "Français (QWERTZ)",
                MainKeyboardView.getFullDisplayName(FR_QWERTZ, mRes));
        assertEquals("de qwerty",    "Deutsch (QWERTY)",
                MainKeyboardView.getFullDisplayName(DE_QWERTY, mRes));
        assertEquals("en_US azerty", "English (US) (AZERTY)",
                MainKeyboardView.getFullDisplayName(US_AZERTY, mRes));
        assertEquals("zz azerty",    "AZERTY",
                MainKeyboardView.getFullDisplayName(ZZ_AZERTY, mRes));

        assertEquals("fr qwertz",    "Français", MainKeyboardView.getMiddleDisplayName(FR_QWERTZ));
        assertEquals("de qwerty",    "Deutsch",  MainKeyboardView.getMiddleDisplayName(DE_QWERTY));
        assertEquals("en_US azerty", "English",  MainKeyboardView.getMiddleDisplayName(US_AZERTY));
        assertEquals("zz azerty",    "AZERTY",   MainKeyboardView.getMiddleDisplayName(ZZ_AZERTY));

        assertEquals("fr qwertz",    "Fr", MainKeyboardView.getShortDisplayName(FR_QWERTZ));
        assertEquals("de qwerty",    "De", MainKeyboardView.getShortDisplayName(DE_QWERTY));
        assertEquals("en_US azerty", "En", MainKeyboardView.getShortDisplayName(US_AZERTY));
        assertEquals("zz azerty",    "",  MainKeyboardView.getShortDisplayName(ZZ_AZERTY));
    }
}
