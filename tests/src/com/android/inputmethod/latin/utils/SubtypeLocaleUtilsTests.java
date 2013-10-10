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

package com.android.inputmethod.latin.utils;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;

import java.util.ArrayList;
import java.util.Locale;

@SmallTest
public class SubtypeLocaleUtilsTests extends AndroidTestCase {
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
        SubtypeLocaleUtils.init(context);

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
                SubtypeLocaleUtils.NO_LANGUAGE, "qwerty");
        DE_QWERTY = AdditionalSubtypeUtils.createAdditionalSubtype(
                Locale.GERMAN.toString(), "qwerty", null);
        FR_QWERTZ = AdditionalSubtypeUtils.createAdditionalSubtype(
                Locale.FRENCH.toString(), "qwertz", null);
        EN_US_AZERTY = AdditionalSubtypeUtils.createAdditionalSubtype(
                Locale.US.toString(), "azerty", null);
        EN_UK_DVORAK = AdditionalSubtypeUtils.createAdditionalSubtype(
                Locale.UK.toString(), "dvorak", null);
        ES_US_COLEMAK = AdditionalSubtypeUtils.createAdditionalSubtype(
                "es_US", "colemak", null);
        ZZ_AZERTY = AdditionalSubtypeUtils.createAdditionalSubtype(
                SubtypeLocaleUtils.NO_LANGUAGE, "azerty", null);
        ZZ_PC = AdditionalSubtypeUtils.createAdditionalSubtype(
                SubtypeLocaleUtils.NO_LANGUAGE, "pcqwerty", null);

    }

    public void testAllFullDisplayName() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName =
                    SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype);
            if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
                final String noLanguage = mRes.getString(R.string.subtype_no_language);
                assertTrue(subtypeName, subtypeName.contains(noLanguage));
            } else {
                final String languageName =
                        SubtypeLocaleUtils.getSubtypeLocaleDisplayName(subtype.getLocale());
                assertTrue(subtypeName, subtypeName.contains(languageName));
            }
        }
    }

    public void testKeyboardLayoutSetName() {
        assertEquals("en_US", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(EN_US));
        assertEquals("en_GB", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(EN_GB));
        assertEquals("es_US", "spanish", SubtypeLocaleUtils.getKeyboardLayoutSetName(ES_US));
        assertEquals("fr   ", "azerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(FR));
        assertEquals("fr_CA", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(FR_CA));
        assertEquals("de   ", "qwertz", SubtypeLocaleUtils.getKeyboardLayoutSetName(DE));
        assertEquals("zz   ", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(ZZ));
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
    //  zz    qwerty  F  Alphabet (QWERTY)
    //  fr    qwertz  T  French (QWERTZ)
    //  de    qwerty  T  German (QWERTY)
    //  en_US azerty  T  English (US) (AZERTY)   exception
    //  en_UK dvorak  T  English (UK) (Dvorak)   exception
    //  es_US colemak T  Spanish (US) (Colemak)  exception
    //  zz    pc      T  Alphabet (PC)

    public void testPredefinedSubtypesInEnglishSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(final Resources res) {
                assertEquals("en_US", "English (US)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_US));
                assertEquals("en_GB", "English (UK)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_GB));
                assertEquals("es_US", "Spanish (US)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ES_US));
                assertEquals("fr   ", "French",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR));
                assertEquals("fr_CA", "French (Canada)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_CA));
                assertEquals("de   ", "German",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE));
                assertEquals("zz   ", "Alphabet (QWERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ZZ));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testAdditionalSubtypesInEnglishSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(final Resources res) {
                assertEquals("fr qwertz",    "French (QWERTZ)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_QWERTZ));
                assertEquals("de qwerty",    "German (QWERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE_QWERTY));
                assertEquals("en_US azerty", "English (US) (AZERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_US_AZERTY));
                assertEquals("en_UK dvorak", "English (UK) (Dvorak)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_UK_DVORAK));
                assertEquals("es_US colemak","Spanish (US) (Colemak)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ES_US_COLEMAK));
                assertEquals("zz pc",    "Alphabet (PC)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ZZ_PC));
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
    //  zz    pc      T  Alphabet (PC)

    public void testPredefinedSubtypesInFrenchSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(final Resources res) {
                assertEquals("en_US", "Anglais (États-Unis)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_US));
                assertEquals("en_GB", "Anglais (Royaume-Uni)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_GB));
                assertEquals("es_US", "Espagnol (États-Unis)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ES_US));
                assertEquals("fr   ", "Français",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR));
                assertEquals("fr_CA", "Français (Canada)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_CA));
                assertEquals("de   ", "Allemand",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE));
                assertEquals("zz   ", "Alphabet latin (QWERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ZZ));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }

    public void testAdditionalSubtypesInFrenchSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(final Resources res) {
                assertEquals("fr qwertz",    "Français (QWERTZ)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_QWERTZ));
                assertEquals("de qwerty",    "Allemand (QWERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE_QWERTY));
                assertEquals("en_US azerty", "Anglais (États-Unis) (AZERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_US_AZERTY));
                assertEquals("en_UK dvorak", "Anglais (Royaume-Uni) (Dvorak)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_UK_DVORAK));
                assertEquals("es_US colemak","Espagnol (États-Unis) (Colemak)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ES_US_COLEMAK));
                assertEquals("zz pc",    "Alphabet latin (PC)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ZZ_PC));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }

    public void testAllFullDisplayNameForSpacebar() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName =
                    SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype);
            final String spacebarText = SubtypeLocaleUtils.getFullDisplayName(subtype);
            final String languageName =
                    SubtypeLocaleUtils.getSubtypeLocaleDisplayName(subtype.getLocale());
            if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
                assertFalse(subtypeName, spacebarText.contains(languageName));
            } else {
                assertTrue(subtypeName, spacebarText.contains(languageName));
            }
        }
    }

   public void testAllMiddleDisplayNameForSpacebar() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName =
                    SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype);
            final String spacebarText = SubtypeLocaleUtils.getMiddleDisplayName(subtype);
            if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
                assertEquals(subtypeName,
                        SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype), spacebarText);
            } else {
                assertEquals(subtypeName,
                        SubtypeLocaleUtils.getSubtypeLocaleDisplayName(subtype.getLocale()),
                        spacebarText);
            }
        }
    }

    public void testAllShortDisplayNameForSpacebar() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName =
                    SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype);
            final Locale locale = SubtypeLocaleUtils.getSubtypeLocale(subtype);
            final String spacebarText = SubtypeLocaleUtils.getShortDisplayName(subtype);
            final String languageCode = StringUtils.capitalizeFirstCodePoint(
                    locale.getLanguage(), locale);
            if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
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
        protected Void job(final Resources res) {
            assertEquals("en_US", "English (US)", SubtypeLocaleUtils.getFullDisplayName(EN_US));
            assertEquals("en_GB", "English (UK)", SubtypeLocaleUtils.getFullDisplayName(EN_GB));
            assertEquals("es_US", "Español (EE.UU.)",
                    SubtypeLocaleUtils.getFullDisplayName(ES_US));
            assertEquals("fr   ", "Français",     SubtypeLocaleUtils.getFullDisplayName(FR));
            assertEquals("fr_CA", "Français (Canada)",
                    SubtypeLocaleUtils.getFullDisplayName(FR_CA));
            assertEquals("de   ", "Deutsch",      SubtypeLocaleUtils.getFullDisplayName(DE));
            assertEquals("zz   ", "QWERTY",       SubtypeLocaleUtils.getFullDisplayName(ZZ));

            assertEquals("en_US", "English",  SubtypeLocaleUtils.getMiddleDisplayName(EN_US));
            assertEquals("en_GB", "English",  SubtypeLocaleUtils.getMiddleDisplayName(EN_GB));
            assertEquals("es_US", "Español",  SubtypeLocaleUtils.getMiddleDisplayName(ES_US));
            assertEquals("fr   ", "Français", SubtypeLocaleUtils.getMiddleDisplayName(FR));
            assertEquals("fr_CA", "Français", SubtypeLocaleUtils.getMiddleDisplayName(FR_CA));
            assertEquals("de   ", "Deutsch",  SubtypeLocaleUtils.getMiddleDisplayName(DE));
            assertEquals("zz   ", "QWERTY",   SubtypeLocaleUtils.getMiddleDisplayName(ZZ));

            assertEquals("en_US", "En", SubtypeLocaleUtils.getShortDisplayName(EN_US));
            assertEquals("en_GB", "En", SubtypeLocaleUtils.getShortDisplayName(EN_GB));
            assertEquals("es_US", "Es", SubtypeLocaleUtils.getShortDisplayName(ES_US));
            assertEquals("fr   ", "Fr", SubtypeLocaleUtils.getShortDisplayName(FR));
            assertEquals("fr_CA", "Fr", SubtypeLocaleUtils.getShortDisplayName(FR_CA));
            assertEquals("de   ", "De", SubtypeLocaleUtils.getShortDisplayName(DE));
            assertEquals("zz   ", "",   SubtypeLocaleUtils.getShortDisplayName(ZZ));
            return null;
        }
    };

    private final RunInLocale<Void> testsAdditionalSubtypesForSpacebar = new RunInLocale<Void>() {
        @Override
        protected Void job(final Resources res) {
            assertEquals("fr qwertz",    "Français",
                    SubtypeLocaleUtils.getFullDisplayName(FR_QWERTZ));
            assertEquals("de qwerty",    "Deutsch",
                    SubtypeLocaleUtils.getFullDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "English (US)",
                    SubtypeLocaleUtils.getFullDisplayName(EN_US_AZERTY));
            assertEquals("zz azerty",    "AZERTY",
                    SubtypeLocaleUtils.getFullDisplayName(ZZ_AZERTY));

            assertEquals("fr qwertz",    "Français",
                    SubtypeLocaleUtils.getMiddleDisplayName(FR_QWERTZ));
            assertEquals("de qwerty",    "Deutsch",
                    SubtypeLocaleUtils.getMiddleDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "English",
                    SubtypeLocaleUtils.getMiddleDisplayName(EN_US_AZERTY));
            assertEquals("zz azerty",    "AZERTY",
                    SubtypeLocaleUtils.getMiddleDisplayName(ZZ_AZERTY));

            assertEquals("fr qwertz",    "Fr", SubtypeLocaleUtils.getShortDisplayName(FR_QWERTZ));
            assertEquals("de qwerty",    "De", SubtypeLocaleUtils.getShortDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "En",
                    SubtypeLocaleUtils.getShortDisplayName(EN_US_AZERTY));
            assertEquals("zz azerty",    "",   SubtypeLocaleUtils.getShortDisplayName(ZZ_AZERTY));
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
