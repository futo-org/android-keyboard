/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.LocaleUtils.RunInLocale;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Locale;

@SmallTest
public class SubtypeLocaleTests extends AndroidTestCase {
    // Locale to subtypes list.
    private final ArrayList<InputMethodSubtype> mSubtypesList = CollectionUtils.newArrayList();

    private RichInputMethodManager mRichImm;
    private Resources mRes;

    InputMethodSubtype EN_US;
    InputMethodSubtype EN_GB;
    InputMethodSubtype ES_US;
    InputMethodSubtype FR;
    InputMethodSubtype FR_CA;
    InputMethodSubtype DE;
    InputMethodSubtype ZZ;
    InputMethodSubtype DE_QWERTY;
    InputMethodSubtype FR_QWERTZ;
    InputMethodSubtype EN_US_AZERTY;
    InputMethodSubtype EN_UK_DVORAK;
    InputMethodSubtype ES_US_COLEMAK;
    InputMethodSubtype ZZ_AZERTY;
    InputMethodSubtype ZZ_PC;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getContext();
        RichInputMethodManager.init(context);
        mRichImm = RichInputMethodManager.getInstance();
        mRes = context.getResources();
        SubtypeLocale.init(context);

        EN_US = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.US.toString(), "qwerty");
        EN_GB = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.UK.toString(), "qwerty");
        ES_US = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "es_US", "spanish");
        FR = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.FRENCH.toString(), "azerty");
        FR_CA = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.CANADA_FRENCH.toString(), "qwerty");
        DE = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.GERMAN.toString(), "qwertz");
        ZZ = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                SubtypeLocale.NO_LANGUAGE, "qwerty");
        DE_QWERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.GERMAN.toString(), "qwerty", null);
        FR_QWERTZ = AdditionalSubtype.createAdditionalSubtype(
                Locale.FRENCH.toString(), "qwertz", null);
        EN_US_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.US.toString(), "azerty", null);
        EN_UK_DVORAK = AdditionalSubtype.createAdditionalSubtype(
                Locale.UK.toString(), "dvorak", null);
        ES_US_COLEMAK = AdditionalSubtype.createAdditionalSubtype(
                "es_US", "colemak", null);
        ZZ_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                SubtypeLocale.NO_LANGUAGE, "azerty", null);
        ZZ_PC = AdditionalSubtype.createAdditionalSubtype(
                SubtypeLocale.NO_LANGUAGE, "pcqwerty", null);

    }

    public void testAllFullDisplayName() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocale.getSubtypeDisplayNameInSystemLocale(subtype);
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

    public void testKeyboardLayoutSetName() {
        assertEquals("en_US", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_US));
        assertEquals("en_GB", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_GB));
        assertEquals("es_US", "spanish", SubtypeLocale.getKeyboardLayoutSetName(ES_US));
        assertEquals("fr   ", "azerty", SubtypeLocale.getKeyboardLayoutSetName(FR));
        assertEquals("fr_CA", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(FR_CA));
        assertEquals("de   ", "qwertz", SubtypeLocale.getKeyboardLayoutSetName(DE));
        assertEquals("zz   ", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(ZZ));
    }

    // InputMethodSubtype's display name in system locale (en_US).
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  display name
    // ------ ------- - ----------------------
    //  en_US qwerty  F  English (US)            exception
    //  en_GB qwerty  F  English (UK)            exception
    //  es_US spanish F  Spanish (US)            exception
    //  fr    azerty  F  French
    //  fr_CA qwerty  F  French (Canada)
    //  de    qwertz  F  German
    //  zz    qwerty  F  No language (QWERTY)
    //  fr    qwertz  T  French (QWERTZ)
    //  de    qwerty  T  German (QWERTY)
    //  en_US azerty  T  English (US) (AZERTY)   exception
    //  en_UK dvorak  T  English (UK) (Dvorak)   exception
    //  es_US colemak T  Spanish (US) (Colemak)  exception
    //  zz    pc      T  No language (PC)

    public void testPredefinedSubtypesInEnglishSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("en_US", "English (US)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(EN_US));
                assertEquals("en_GB", "English (UK)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(EN_GB));
                assertEquals("es_US", "Spanish (US)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(ES_US));
                assertEquals("fr   ", "French",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(FR));
                assertEquals("fr_CA", "French (Canada)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(FR_CA));
                assertEquals("de   ", "German",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(DE));
                assertEquals("zz   ", "No language (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(ZZ));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testAdditionalSubtypesInEnglishSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("fr qwertz",    "French (QWERTZ)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(FR_QWERTZ));
                assertEquals("de qwerty",    "German (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(DE_QWERTY));
                assertEquals("en_US azerty", "English (US) (AZERTY)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(EN_US_AZERTY));
                assertEquals("en_UK dvorak", "English (UK) (Dvorak)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(EN_UK_DVORAK));
                assertEquals("es_US colemak","Spanish (US) (Colemak)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(ES_US_COLEMAK));
                assertEquals("zz azerty",    "No language (PC)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(ZZ_PC));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.ENGLISH);
    }

    // InputMethodSubtype's display name in system locale (fr).
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  display name
    // ------ ------- - ----------------------
    //  en_US qwerty  F  Anglais (États-Unis)            exception
    //  en_GB qwerty  F  Anglais (Royaume-Uni)            exception
    //  es_US spanish F  Espagnol (États-Unis)            exception
    //  fr    azerty  F  Français
    //  fr_CA qwerty  F  Français (Canada)
    //  de    qwertz  F  Allemand
    //  zz    qwerty  F  Aucune langue (QWERTY)
    //  fr    qwertz  T  Français (QWERTZ)
    //  de    qwerty  T  Allemand (QWERTY)
    //  en_US azerty  T  Anglais (États-Unis) (AZERTY)   exception
    //  en_UK dvorak  T  Anglais (Royaume-Uni) (Dvorak)   exception
    //  es_US colemak T  Espagnol (États-Unis) (Colemak)  exception
    //  zz    pc      T  Aucune langue (PC)

    public void testPredefinedSubtypesInFrenchSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("en_US", "Anglais (États-Unis)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(EN_US));
                assertEquals("en_GB", "Anglais (Royaume-Uni)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(EN_GB));
                assertEquals("es_US", "Espagnol (États-Unis)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(ES_US));
                assertEquals("fr   ", "Français",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(FR));
                assertEquals("fr_CA", "Français (Canada)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(FR_CA));
                assertEquals("de   ", "Allemand",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(DE));
                assertEquals("zz   ", "Aucune langue (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(ZZ));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }

    public void testAdditionalSubtypesInFrenchSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("fr qwertz",    "Français (QWERTZ)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(FR_QWERTZ));
                assertEquals("de qwerty",    "Allemand (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(DE_QWERTY));
                assertEquals("en_US azerty", "Anglais (États-Unis) (AZERTY)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(EN_US_AZERTY));
                assertEquals("en_UK dvorak", "Anglais (Royaume-Uni) (Dvorak)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(EN_UK_DVORAK));
                assertEquals("es_US colemak","Espagnol (États-Unis) (Colemak)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(ES_US_COLEMAK));
                assertEquals("zz azerty",    "Aucune langue (PC)",
                        SubtypeLocale.getSubtypeDisplayNameInSystemLocale(ZZ_PC));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }

    public void testAllFullDisplayNameForSpacebar() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocale.getSubtypeDisplayNameInSystemLocale(subtype);
            final String spacebarText = SubtypeLocale.getFullDisplayName(subtype);
            final String languageName =
                    SubtypeLocale.getSubtypeLocaleDisplayName(subtype.getLocale());
            if (SubtypeLocale.isNoLanguage(subtype)) {
                assertFalse(subtypeName, spacebarText.contains(languageName));
            } else {
                assertTrue(subtypeName, spacebarText.contains(languageName));
            }
        }
    }

   public void testAllMiddleDisplayNameForSpacebar() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocale.getSubtypeDisplayNameInSystemLocale(subtype);
            final String spacebarText = SubtypeLocale.getMiddleDisplayName(subtype);
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

    public void testAllShortDisplayNameForSpacebar() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocale.getSubtypeDisplayNameInSystemLocale(subtype);
            final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
            final String spacebarText = SubtypeLocale.getShortDisplayName(subtype);
            final String languageCode = StringUtils.capitalizeFirstCodePoint(
                    locale.getLanguage(), locale);
            if (SubtypeLocale.isNoLanguage(subtype)) {
                assertEquals(subtypeName, "", spacebarText);
            } else {
                assertEquals(subtypeName, languageCode, spacebarText);
            }
        }
    }

    // InputMethodSubtype's display name for spacebar text in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  | Short  Middle      Full
    // ------ ------- - ---- --------- ----------------------
    //  en_US qwerty  F  En  English   English (US)           exception
    //  en_GB qwerty  F  En  English   English (UK)           exception
    //  es_US spanish F  Es  Español   Español (EE.UU.)       exception
    //  fr    azerty  F  Fr  Français  Français
    //  fr_CA qwerty  F  Fr  Français  Français (Canada)
    //  de    qwertz  F  De  Deutsch   Deutsch
    //  zz    qwerty  F      QWERTY    QWERTY
    //  fr    qwertz  T  Fr  Français  Français
    //  de    qwerty  T  De  Deutsch   Deutsch
    //  en_US azerty  T  En  English   English (US)
    //  zz    azerty  T      AZERTY    AZERTY

    private final RunInLocale<Void> testsPredefinedSubtypesForSpacebar = new RunInLocale<Void>() {
        @Override
        protected Void job(Resources res) {
            assertEquals("en_US", "English (US)",      SubtypeLocale.getFullDisplayName(EN_US));
            assertEquals("en_GB", "English (UK)",      SubtypeLocale.getFullDisplayName(EN_GB));
            assertEquals("es_US", "Español (EE.UU.)",  SubtypeLocale.getFullDisplayName(ES_US));
            assertEquals("fr   ", "Français",          SubtypeLocale.getFullDisplayName(FR));
            assertEquals("fr_CA", "Français (Canada)", SubtypeLocale.getFullDisplayName(FR_CA));
            assertEquals("de   ", "Deutsch",           SubtypeLocale.getFullDisplayName(DE));
            assertEquals("zz   ", "QWERTY",            SubtypeLocale.getFullDisplayName(ZZ));

            assertEquals("en_US", "English",  SubtypeLocale.getMiddleDisplayName(EN_US));
            assertEquals("en_GB", "English",  SubtypeLocale.getMiddleDisplayName(EN_GB));
            assertEquals("es_US", "Español",  SubtypeLocale.getMiddleDisplayName(ES_US));
            assertEquals("fr   ", "Français", SubtypeLocale.getMiddleDisplayName(FR));
            assertEquals("fr_CA", "Français", SubtypeLocale.getMiddleDisplayName(FR_CA));
            assertEquals("de   ", "Deutsch",  SubtypeLocale.getMiddleDisplayName(DE));
            assertEquals("zz   ", "QWERTY",   SubtypeLocale.getMiddleDisplayName(ZZ));

            assertEquals("en_US", "En", SubtypeLocale.getShortDisplayName(EN_US));
            assertEquals("en_GB", "En", SubtypeLocale.getShortDisplayName(EN_GB));
            assertEquals("es_US", "Es", SubtypeLocale.getShortDisplayName(ES_US));
            assertEquals("fr   ", "Fr", SubtypeLocale.getShortDisplayName(FR));
            assertEquals("fr_CA", "Fr", SubtypeLocale.getShortDisplayName(FR_CA));
            assertEquals("de   ", "De", SubtypeLocale.getShortDisplayName(DE));
            assertEquals("zz   ", "",   SubtypeLocale.getShortDisplayName(ZZ));
            return null;
        }
    };

    private final RunInLocale<Void> testsAdditionalSubtypesForSpacebar = new RunInLocale<Void>() {
        @Override
        protected Void job(Resources res) {
            assertEquals("fr qwertz",    "Français",
                    SubtypeLocale.getFullDisplayName(FR_QWERTZ));
            assertEquals("de qwerty",    "Deutsch",
                    SubtypeLocale.getFullDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "English (US)",
                    SubtypeLocale.getFullDisplayName(EN_US_AZERTY));
            assertEquals("zz azerty",    "AZERTY",
                    SubtypeLocale.getFullDisplayName(ZZ_AZERTY));

            assertEquals("fr qwertz",    "Français",
                    SubtypeLocale.getMiddleDisplayName(FR_QWERTZ));
            assertEquals("de qwerty",    "Deutsch",
                    SubtypeLocale.getMiddleDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "English",
                    SubtypeLocale.getMiddleDisplayName(EN_US_AZERTY));
            assertEquals("zz azerty",    "AZERTY",
                    SubtypeLocale.getMiddleDisplayName(ZZ_AZERTY));

            assertEquals("fr qwertz",    "Fr", SubtypeLocale.getShortDisplayName(FR_QWERTZ));
            assertEquals("de qwerty",    "De", SubtypeLocale.getShortDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "En", SubtypeLocale.getShortDisplayName(EN_US_AZERTY));
            assertEquals("zz azerty",    "",   SubtypeLocale.getShortDisplayName(ZZ_AZERTY));
            return null;
        }
    };

    public void testPredefinedSubtypesForSpacebarInEnglish() {
        testsPredefinedSubtypesForSpacebar.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testAdditionalSubtypeForSpacebarInEnglish() {
        testsAdditionalSubtypesForSpacebar.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testPredefinedSubtypesForSpacebarInFrench() {
        testsPredefinedSubtypesForSpacebar.runInLocale(mRes, Locale.FRENCH);
    }

    public void testAdditionalSubtypeForSpacebarInFrench() {
        testsAdditionalSubtypesForSpacebar.runInLocale(mRes, Locale.FRENCH);
    }
}
