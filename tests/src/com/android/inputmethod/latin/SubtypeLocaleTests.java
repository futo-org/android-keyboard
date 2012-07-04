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
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.LocaleUtils.RunInLocale;

import java.util.ArrayList;
import java.util.Locale;

public class SubtypeLocaleTests extends AndroidTestCase {
    // Locale to subtypes list.
    private final ArrayList<InputMethodSubtype> mSubtypesList = new ArrayList<InputMethodSubtype>();

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
            if (SubtypeLocale.isNoLanguage(subtype)) {
                final String noLanguage = mRes.getString(R.string.subtype_no_language);
                assertTrue(subtypeName, subtypeName.contains(noLanguage));
            } else {
                final String languageName =
                        SubtypeLocale.getSubtypeLocaleDisplayName(subtype.getLocale());
                assertTrue(subtypeName, subtypeName.contains(languageName));
            }
        }
    }

    // InputMethodSubtype's display name in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout |  display name
    // ------ ------ - ----------------------
    //  en_US qwerty F  English (US)            exception
    //  en_GB qwerty F  English (UK)            exception
    //  fr    azerty F  Français
    //  fr_CA qwerty F  Français (Canada)
    //  de    qwertz F  Deutsch
    //  zz    qwerty F  No language (QWERTY)    in system locale
    //  fr    qwertz T  Français (QWERTZ)
    //  de    qwerty T  Deutsch (QWERTY)
    //  en_US azerty T  English (US) (AZERTY)
    //  zz    azerty T  No language (AZERTY)    in system locale

    public void testPredefinedSubtypesInEnglish() {
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

        assertEquals("en_US", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_US));
        assertEquals("en_GB", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_GB));
        assertEquals("fr   ", "azerty", SubtypeLocale.getKeyboardLayoutSetName(FR));
        assertEquals("fr_CA", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(FR_CA));
        assertEquals("de   ", "qwertz", SubtypeLocale.getKeyboardLayoutSetName(DE));
        assertEquals("zz   ", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(ZZ));

        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("en_US", "English (US)",
                        SubtypeLocale.getSubtypeDisplayName(EN_US, res));
                assertEquals("en_GB", "English (UK)",
                        SubtypeLocale.getSubtypeDisplayName(EN_GB, res));
                assertEquals("fr   ", "Français",
                        SubtypeLocale.getSubtypeDisplayName(FR, res));
                assertEquals("fr_CA", "Français (Canada)",
                        SubtypeLocale.getSubtypeDisplayName(FR_CA, res));
                assertEquals("de   ", "Deutsch",
                        SubtypeLocale.getSubtypeDisplayName(DE, res));
                assertEquals("zz   ", "No language (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayName(ZZ, res));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testAdditionalSubtypesInEnglish() {
        final InputMethodSubtype DE_QWERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.GERMAN.toString(), "qwerty", null);
        final InputMethodSubtype FR_QWERTZ = AdditionalSubtype.createAdditionalSubtype(
                Locale.FRENCH.toString(), "qwertz", null);
        final InputMethodSubtype US_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.US.toString(), "azerty", null);
        final InputMethodSubtype ZZ_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                SubtypeLocale.NO_LANGUAGE, "azerty", null);

        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("fr qwertz",    "Français (QWERTZ)",
                        SubtypeLocale.getSubtypeDisplayName(FR_QWERTZ, res));
                assertEquals("de qwerty",    "Deutsch (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayName(DE_QWERTY, res));
                assertEquals("en_US azerty", "English (US) (AZERTY)",
                        SubtypeLocale.getSubtypeDisplayName(US_AZERTY, res));
                assertEquals("zz azerty",    "No language (AZERTY)",
                        SubtypeLocale.getSubtypeDisplayName(ZZ_AZERTY, res));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testPredefinedSubtypesInFrench() {
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

        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("en_US", "English (US)",
                        SubtypeLocale.getSubtypeDisplayName(EN_US, res));
                assertEquals("en_GB", "English (UK)",
                        SubtypeLocale.getSubtypeDisplayName(EN_GB, res));
                assertEquals("fr   ", "Français",
                        SubtypeLocale.getSubtypeDisplayName(FR, res));
                assertEquals("fr_CA", "Français (Canada)",
                        SubtypeLocale.getSubtypeDisplayName(FR_CA, res));
                assertEquals("de   ", "Deutsch",
                        SubtypeLocale.getSubtypeDisplayName(DE, res));
                assertEquals("zz   ", "Pas de langue (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayName(ZZ, res));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }

    public void testAdditionalSubtypesInFrench() {
        final InputMethodSubtype DE_QWERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.GERMAN.toString(), "qwerty", null);
        final InputMethodSubtype FR_QWERTZ = AdditionalSubtype.createAdditionalSubtype(
                Locale.FRENCH.toString(), "qwertz", null);
        final InputMethodSubtype US_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.US.toString(), "azerty", null);
        final InputMethodSubtype ZZ_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                SubtypeLocale.NO_LANGUAGE, "azerty", null);

        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("fr qwertz",    "Français (QWERTZ)",
                        SubtypeLocale.getSubtypeDisplayName(FR_QWERTZ, res));
                assertEquals("de qwerty",    "Deutsch (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayName(DE_QWERTY, res));
                assertEquals("en_US azerty", "English (US) (AZERTY)",
                        SubtypeLocale.getSubtypeDisplayName(US_AZERTY, res));
                assertEquals("zz azerty",    "Aucune langue (AZERTY)",
                        SubtypeLocale.getSubtypeDisplayName(ZZ_AZERTY, res));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }
}
