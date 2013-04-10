/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.AdditionalSubtype;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.StringUtils;
import com.android.inputmethod.latin.SubtypeLocale;
import com.android.inputmethod.latin.LocaleUtils.RunInLocale;

import java.util.ArrayList;
import java.util.Locale;

@SmallTest
public class SpacebarTextTests extends AndroidTestCase {
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
    InputMethodSubtype US_AZERTY;
    InputMethodSubtype ZZ_AZERTY;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getContext();
        RichInputMethodManager.init(context);
        mRichImm = RichInputMethodManager.getInstance();
        mRes = context.getResources();
        SubtypeLocale.init(context);

        EN_US = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(Locale.US.toString(), "qwerty");
        EN_GB = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(Locale.UK.toString(), "qwerty");
        ES_US = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet("es_US", "spanish");
        FR = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(Locale.FRENCH.toString(), "azerty");
        FR_CA = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.CANADA_FRENCH.toString(), "qwerty");
        DE = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(Locale.GERMAN.toString(), "qwertz");
        ZZ = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(SubtypeLocale.NO_LANGUAGE, "qwerty");
        DE_QWERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.GERMAN.toString(), "qwerty", null);
        FR_QWERTZ = AdditionalSubtype.createAdditionalSubtype(
                Locale.FRENCH.toString(), "qwertz", null);
        US_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                Locale.US.toString(), "azerty", null);
        ZZ_AZERTY = AdditionalSubtype.createAdditionalSubtype(
                SubtypeLocale.NO_LANGUAGE, "azerty", null);
    }

    public void testAllFullDisplayName() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocale.getSubtypeDisplayName(subtype);
            final String spacebarText = MainKeyboardView.getFullDisplayName(subtype);
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
            final String subtypeName = SubtypeLocale.getSubtypeDisplayName(subtype);
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
            final String subtypeName = SubtypeLocale.getSubtypeDisplayName(subtype);
            final Locale locale = SubtypeLocale.getSubtypeLocale(subtype);
            final String spacebarText = MainKeyboardView.getShortDisplayName(subtype);
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

    private final RunInLocale<Void> testsPredefinedSubtypes = new RunInLocale<Void>() {
        @Override
        protected Void job(Resources res) {
            assertEquals("en_US", "English (US)",      MainKeyboardView.getFullDisplayName(EN_US));
            assertEquals("en_GB", "English (UK)",      MainKeyboardView.getFullDisplayName(EN_GB));
            assertEquals("es_US", "Español (EE.UU.)",  MainKeyboardView.getFullDisplayName(ES_US));
            assertEquals("fr   ", "Français",          MainKeyboardView.getFullDisplayName(FR));
            assertEquals("fr_CA", "Français (Canada)", MainKeyboardView.getFullDisplayName(FR_CA));
            assertEquals("de   ", "Deutsch",           MainKeyboardView.getFullDisplayName(DE));
            assertEquals("zz   ", "QWERTY",            MainKeyboardView.getFullDisplayName(ZZ));

            assertEquals("en_US", "English",  MainKeyboardView.getMiddleDisplayName(EN_US));
            assertEquals("en_GB", "English",  MainKeyboardView.getMiddleDisplayName(EN_GB));
            assertEquals("es_US", "Español",  MainKeyboardView.getMiddleDisplayName(ES_US));
            assertEquals("fr   ", "Français", MainKeyboardView.getMiddleDisplayName(FR));
            assertEquals("fr_CA", "Français", MainKeyboardView.getMiddleDisplayName(FR_CA));
            assertEquals("de   ", "Deutsch",  MainKeyboardView.getMiddleDisplayName(DE));
            assertEquals("zz   ", "QWERTY",   MainKeyboardView.getMiddleDisplayName(ZZ));

            assertEquals("en_US", "En", MainKeyboardView.getShortDisplayName(EN_US));
            assertEquals("en_GB", "En", MainKeyboardView.getShortDisplayName(EN_GB));
            assertEquals("es_US", "Es", MainKeyboardView.getShortDisplayName(ES_US));
            assertEquals("fr   ", "Fr", MainKeyboardView.getShortDisplayName(FR));
            assertEquals("fr_CA", "Fr", MainKeyboardView.getShortDisplayName(FR_CA));
            assertEquals("de   ", "De", MainKeyboardView.getShortDisplayName(DE));
            assertEquals("zz   ", "",   MainKeyboardView.getShortDisplayName(ZZ));
            return null;
        }
    };

    private final RunInLocale<Void> testsAdditionalSubtypes = new RunInLocale<Void>() {
        @Override
        protected Void job(Resources res) {
            assertEquals("fr qwertz",    "Français",
                    MainKeyboardView.getFullDisplayName(FR_QWERTZ));
            assertEquals("de qwerty",    "Deutsch",
                    MainKeyboardView.getFullDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "English (US)",
                    MainKeyboardView.getFullDisplayName(US_AZERTY));
            assertEquals("zz azerty",    "AZERTY",
                    MainKeyboardView.getFullDisplayName(ZZ_AZERTY));

            assertEquals("fr qwertz",    "Français",
                    MainKeyboardView.getMiddleDisplayName(FR_QWERTZ));
            assertEquals("de qwerty",    "Deutsch",
                    MainKeyboardView.getMiddleDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "English",
                    MainKeyboardView.getMiddleDisplayName(US_AZERTY));
            assertEquals("zz azerty",    "AZERTY",
                    MainKeyboardView.getMiddleDisplayName(ZZ_AZERTY));

            assertEquals("fr qwertz",    "Fr", MainKeyboardView.getShortDisplayName(FR_QWERTZ));
            assertEquals("de qwerty",    "De", MainKeyboardView.getShortDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "En", MainKeyboardView.getShortDisplayName(US_AZERTY));
            assertEquals("zz azerty",    "",   MainKeyboardView.getShortDisplayName(ZZ_AZERTY));
            return null;
        }
    };

    public void testPredefinedSubtypesInEnglish() {
        testsPredefinedSubtypes.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testAdditionalSubtypeInEnglish() {
        testsAdditionalSubtypes.runInLocale(mRes, Locale.ENGLISH);
    }

    public void testPredefinedSubtypesInFrench() {
        testsPredefinedSubtypes.runInLocale(mRes, Locale.FRENCH);
    }

    public void testAdditionalSubtypeInFrench() {
        testsAdditionalSubtypes.runInLocale(mRes, Locale.FRENCH);
    }
}
