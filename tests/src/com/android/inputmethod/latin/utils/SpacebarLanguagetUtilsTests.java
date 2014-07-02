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
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.RichInputMethodManager;

import java.util.ArrayList;
import java.util.Locale;

@SmallTest
public class SpacebarLanguagetUtilsTests extends AndroidTestCase {
    // All input method subtypes of LatinIME.
    private final ArrayList<InputMethodSubtype> mSubtypesList = new ArrayList<>();

    private RichInputMethodManager mRichImm;
    private Resources mRes;

    InputMethodSubtype EN_US;
    InputMethodSubtype EN_GB;
    InputMethodSubtype ES_US;
    InputMethodSubtype FR;
    InputMethodSubtype FR_CA;
    InputMethodSubtype FR_CH;
    InputMethodSubtype DE;
    InputMethodSubtype DE_CH;
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

        final InputMethodInfo imi = mRichImm.getInputMethodInfoOfThisIme();
        final int subtypeCount = imi.getSubtypeCount();
        for (int index = 0; index < subtypeCount; index++) {
            final InputMethodSubtype subtype = imi.getSubtypeAt(index);
            mSubtypesList.add(subtype);
        }

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
        FR_CH = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "fr_CH", "swiss");
        DE = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.GERMAN.toString(), "qwertz");
        DE_CH = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "de_CH", "swiss");
        ZZ = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                SubtypeLocaleUtils.NO_LANGUAGE, "qwerty");
        DE_QWERTY = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                Locale.GERMAN.toString(), "qwerty");
        FR_QWERTZ = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                Locale.FRENCH.toString(), "qwertz");
        EN_US_AZERTY = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                Locale.US.toString(), "azerty");
        EN_UK_DVORAK = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                Locale.UK.toString(), "dvorak");
        ES_US_COLEMAK = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                "es_US", "colemak");
        ZZ_AZERTY = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                SubtypeLocaleUtils.NO_LANGUAGE, "azerty");
        ZZ_PC = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                SubtypeLocaleUtils.NO_LANGUAGE, "pcqwerty");
    }

    public void testAllFullDisplayNameForSpacebar() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(subtype);
            final String spacebarText = SpacebarLanguageUtils.getFullDisplayName(subtype);
            final String languageName = SubtypeLocaleUtils
                    .getSubtypeLocaleDisplayName(subtype.getLocale());
            if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
                assertFalse(subtypeName, spacebarText.contains(languageName));
            } else {
                assertTrue(subtypeName, spacebarText.contains(languageName));
            }
        }
    }

   public void testAllMiddleDisplayNameForSpacebar() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(subtype);
            final String spacebarText = SpacebarLanguageUtils.getMiddleDisplayName(subtype);
            if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
                assertEquals(subtypeName,
                        SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(subtype), spacebarText);
            } else {
                final Locale locale = SubtypeLocaleUtils.getSubtypeLocale(subtype);
                assertEquals(subtypeName,
                        SubtypeLocaleUtils.getSubtypeLocaleDisplayName(locale.getLanguage()),
                        spacebarText);
            }
        }
    }

    // InputMethodSubtype's display name for spacebar text in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  Middle     Full
    // ------ ------- - --------- ----------------------
    //  en_US qwerty  F  English   English (US)           exception
    //  en_GB qwerty  F  English   English (UK)           exception
    //  es_US spanish F  Español   Español (EE.UU.)       exception
    //  fr    azerty  F  Français  Français
    //  fr_CA qwerty  F  Français  Français (Canada)
    //  fr_CH swiss   F  Français  Français (Suisse)
    //  de    qwertz  F  Deutsch   Deutsch
    //  de_CH swiss   F  Deutsch   Deutsch (Schweiz)
    //  zz    qwerty  F  QWERTY    QWERTY
    //  fr    qwertz  T  Français  Français
    //  de    qwerty  T  Deutsch   Deutsch
    //  en_US azerty  T  English   English (US)
    //  zz    azerty  T  AZERTY    AZERTY

    private final RunInLocale<Void> testsPredefinedSubtypesForSpacebar = new RunInLocale<Void>() {
        @Override
        protected Void job(final Resources res) {
            assertEquals("en_US", "English (US)",
                    SpacebarLanguageUtils.getFullDisplayName(EN_US));
            assertEquals("en_GB", "English (UK)",
                    SpacebarLanguageUtils.getFullDisplayName(EN_GB));
            assertEquals("es_US", "Español (EE.UU.)",
                    SpacebarLanguageUtils.getFullDisplayName(ES_US));
            assertEquals("fr", "Français",
                    SpacebarLanguageUtils.getFullDisplayName(FR));
            assertEquals("fr_CA", "Français (Canada)",
                    SpacebarLanguageUtils.getFullDisplayName(FR_CA));
            assertEquals("fr_CH", "Français (Suisse)",
                    SpacebarLanguageUtils.getFullDisplayName(FR_CH));
            assertEquals("de", "Deutsch",
                    SpacebarLanguageUtils.getFullDisplayName(DE));
            assertEquals("de_CH", "Deutsch (Schweiz)",
                    SpacebarLanguageUtils.getFullDisplayName(DE_CH));
            assertEquals("zz", "QWERTY",
                    SpacebarLanguageUtils.getFullDisplayName(ZZ));

            assertEquals("en_US", "English",
                    SpacebarLanguageUtils.getMiddleDisplayName(EN_US));
            assertEquals("en_GB", "English",
                    SpacebarLanguageUtils.getMiddleDisplayName(EN_GB));
            assertEquals("es_US", "Español",
                    SpacebarLanguageUtils.getMiddleDisplayName(ES_US));
            assertEquals("fr", "Français",
                    SpacebarLanguageUtils.getMiddleDisplayName(FR));
            assertEquals("fr_CA", "Français",
                    SpacebarLanguageUtils.getMiddleDisplayName(FR_CA));
            assertEquals("fr_CH", "Français",
                    SpacebarLanguageUtils.getMiddleDisplayName(FR_CH));
            assertEquals("de", "Deutsch",
                    SpacebarLanguageUtils.getMiddleDisplayName(DE));
            assertEquals("de_CH", "Deutsch",
                    SpacebarLanguageUtils.getMiddleDisplayName(DE_CH));
            assertEquals("zz", "QWERTY",
                    SpacebarLanguageUtils.getMiddleDisplayName(ZZ));
            return null;
        }
    };

    private final RunInLocale<Void> testsAdditionalSubtypesForSpacebar = new RunInLocale<Void>() {
        @Override
        protected Void job(final Resources res) {
            assertEquals("fr qwertz", "Français",
                    SpacebarLanguageUtils.getFullDisplayName(FR_QWERTZ));
            assertEquals("de qwerty", "Deutsch",
                    SpacebarLanguageUtils.getFullDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "English (US)",
                    SpacebarLanguageUtils.getFullDisplayName(EN_US_AZERTY));
            assertEquals("en_UK dvorak", "English (UK)",
                    SpacebarLanguageUtils.getFullDisplayName(EN_UK_DVORAK));
            assertEquals("es_US colemak", "Español (EE.UU.)",
                    SpacebarLanguageUtils.getFullDisplayName(ES_US_COLEMAK));
            assertEquals("zz azerty", "AZERTY",
                    SpacebarLanguageUtils.getFullDisplayName(ZZ_AZERTY));
            assertEquals("zz pc", "PC",
                    SpacebarLanguageUtils.getFullDisplayName(ZZ_PC));

            assertEquals("fr qwertz", "Français",
                    SpacebarLanguageUtils.getMiddleDisplayName(FR_QWERTZ));
            assertEquals("de qwerty", "Deutsch",
                    SpacebarLanguageUtils.getMiddleDisplayName(DE_QWERTY));
            assertEquals("en_US azerty", "English",
                    SpacebarLanguageUtils.getMiddleDisplayName(EN_US_AZERTY));
            assertEquals("en_UK dvorak", "English",
                    SpacebarLanguageUtils.getMiddleDisplayName(EN_UK_DVORAK));
            assertEquals("es_US colemak", "Español",
                    SpacebarLanguageUtils.getMiddleDisplayName(ES_US_COLEMAK));
            assertEquals("zz azerty", "AZERTY",
                    SpacebarLanguageUtils.getMiddleDisplayName(ZZ_AZERTY));
            assertEquals("zz pc", "PC",
                    SpacebarLanguageUtils.getMiddleDisplayName(ZZ_PC));
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
