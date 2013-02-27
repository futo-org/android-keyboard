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

import com.android.inputmethod.latin.LocaleUtils.RunInLocale;

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
        ZZ_PC = AdditionalSubtype.createAdditionalSubtype(
                SubtypeLocale.NO_LANGUAGE, "pcqwerty", null);

    }

    public void testAllFullDisplayName() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocale.getSubtypeDisplayName(subtype);
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
    // locale layout  |  display name
    // ------ ------- - ----------------------
    //  en_US qwerty  F  English (US)            exception
    //  en_GB qwerty  F  English (UK)            exception
    //  es_US spanish F  Español (EE.UU.)        exception
    //  fr    azerty  F  Français
    //  fr_CA qwerty  F  Français (Canada)
    //  de    qwertz  F  Deutsch
    //  zz    qwerty  F  No language (QWERTY)    in system locale
    //  fr    qwertz  T  Français (QWERTZ)
    //  de    qwerty  T  Deutsch (QWERTY)
    //  en_US azerty  T  English (US) (AZERTY)   exception
    //  en_UK dvorak  T  English (UK) (Dvorak)   exception
    //  es_US colemak T  Español (EE.UU.) (Colemak)  exception
    //  zz    pc      T  No language (PC)        in system locale

    public void testPredefinedSubtypesInEnglish() {
        assertEquals("en_US", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_US));
        assertEquals("en_GB", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_GB));
        assertEquals("es_US", "spanish", SubtypeLocale.getKeyboardLayoutSetName(ES_US));
        assertEquals("fr   ", "azerty", SubtypeLocale.getKeyboardLayoutSetName(FR));
        assertEquals("fr_CA", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(FR_CA));
        assertEquals("de   ", "qwertz", SubtypeLocale.getKeyboardLayoutSetName(DE));
        assertEquals("zz   ", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(ZZ));

        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("en_US", "English (US)",
                        SubtypeLocale.getSubtypeDisplayName(EN_US));
                assertEquals("en_GB", "English (UK)",
                        SubtypeLocale.getSubtypeDisplayName(EN_GB));
                assertEquals("es_US", "Español (EE.UU.)",
                        SubtypeLocale.getSubtypeDisplayName(ES_US));
                assertEquals("fr   ", "Français",
                        SubtypeLocale.getSubtypeDisplayName(FR));
                assertEquals("fr_CA", "Français (Canada)",
                        SubtypeLocale.getSubtypeDisplayName(FR_CA));
                assertEquals("de   ", "Deutsch",
                        SubtypeLocale.getSubtypeDisplayName(DE));
                assertEquals("zz   ", "No language (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayName(ZZ));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testAdditionalSubtypesInEnglish() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("fr qwertz",    "Français (QWERTZ)",
                        SubtypeLocale.getSubtypeDisplayName(FR_QWERTZ));
                assertEquals("de qwerty",    "Deutsch (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayName(DE_QWERTY));
                assertEquals("en_US azerty", "English (US) (AZERTY)",
                        SubtypeLocale.getSubtypeDisplayName(EN_US_AZERTY));
                assertEquals("en_UK dvorak", "English (UK) (Dvorak)",
                        SubtypeLocale.getSubtypeDisplayName(EN_UK_DVORAK));
                assertEquals("es_US colemak","Español (EE.UU.) (Colemak)",
                        SubtypeLocale.getSubtypeDisplayName(ES_US_COLEMAK));
                assertEquals("zz pc",        "No language (PC)",
                        SubtypeLocale.getSubtypeDisplayName(ZZ_PC));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testPredefinedSubtypesInFrench() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("en_US", "English (US)",
                        SubtypeLocale.getSubtypeDisplayName(EN_US));
                assertEquals("en_GB", "English (UK)",
                        SubtypeLocale.getSubtypeDisplayName(EN_GB));
                assertEquals("es_US", "Español (EE.UU.)",
                        SubtypeLocale.getSubtypeDisplayName(ES_US));
                assertEquals("fr   ", "Français",
                        SubtypeLocale.getSubtypeDisplayName(FR));
                assertEquals("fr_CA", "Français (Canada)",
                        SubtypeLocale.getSubtypeDisplayName(FR_CA));
                assertEquals("de   ", "Deutsch",
                        SubtypeLocale.getSubtypeDisplayName(DE));
                assertEquals("zz   ", "Aucune langue (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayName(ZZ));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }

    public void testAdditionalSubtypesInFrench() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job(Resources res) {
                assertEquals("fr qwertz",    "Français (QWERTZ)",
                        SubtypeLocale.getSubtypeDisplayName(FR_QWERTZ));
                assertEquals("de qwerty",    "Deutsch (QWERTY)",
                        SubtypeLocale.getSubtypeDisplayName(DE_QWERTY));
                assertEquals("en_US azerty", "English (US) (AZERTY)",
                        SubtypeLocale.getSubtypeDisplayName(EN_US_AZERTY));
                assertEquals("en_UK dvorak", "English (UK) (Dvorak)",
                        SubtypeLocale.getSubtypeDisplayName(EN_UK_DVORAK));
                assertEquals("es_US colemak","Español (EE.UU.) (Colemak)",
                        SubtypeLocale.getSubtypeDisplayName(ES_US_COLEMAK));
                assertEquals("zz azerty",    "Aucune langue (PC)",
                        SubtypeLocale.getSubtypeDisplayName(ZZ_PC));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
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
        assertEquals("en_US", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_US));
        assertEquals("en_GB", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(EN_GB));
        assertEquals("es_US", "spanish", SubtypeLocale.getKeyboardLayoutSetName(ES_US));
        assertEquals("fr   ", "azerty", SubtypeLocale.getKeyboardLayoutSetName(FR));
        assertEquals("fr_CA", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(FR_CA));
        assertEquals("de   ", "qwertz", SubtypeLocale.getKeyboardLayoutSetName(DE));
        assertEquals("zz   ", "qwerty", SubtypeLocale.getKeyboardLayoutSetName(ZZ));

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
}
