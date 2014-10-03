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
import com.android.inputmethod.latin.RichInputMethodSubtype;

import java.util.ArrayList;
import java.util.Locale;

@SmallTest
public class SpacebarLanguageUtilsTests extends AndroidTestCase {
    // All input method subtypes of LatinIME.
    private final ArrayList<RichInputMethodSubtype> mSubtypesList = new ArrayList<>();

    private RichInputMethodManager mRichImm;
    private Resources mRes;

    RichInputMethodSubtype EN_US;
    RichInputMethodSubtype EN_GB;
    RichInputMethodSubtype ES_US;
    RichInputMethodSubtype FR;
    RichInputMethodSubtype FR_CA;
    RichInputMethodSubtype FR_CH;
    RichInputMethodSubtype DE;
    RichInputMethodSubtype DE_CH;
    RichInputMethodSubtype HI_ZZ;
    RichInputMethodSubtype ZZ;
    RichInputMethodSubtype DE_QWERTY;
    RichInputMethodSubtype FR_QWERTZ;
    RichInputMethodSubtype EN_US_AZERTY;
    RichInputMethodSubtype EN_UK_DVORAK;
    RichInputMethodSubtype ES_US_COLEMAK;
    RichInputMethodSubtype ZZ_AZERTY;
    RichInputMethodSubtype ZZ_PC;

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
            mSubtypesList.add(new RichInputMethodSubtype(subtype));
        }

        EN_US = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.US.toString(), "qwerty"));
        EN_GB = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.UK.toString(), "qwerty"));
        ES_US = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "es_US", "spanish"));
        FR = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.FRENCH.toString(), "azerty"));
        FR_CA = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.CANADA_FRENCH.toString(), "qwerty"));
        FR_CH = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "fr_CH", "swiss"));
        DE = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.GERMAN.toString(), "qwertz"));
        DE_CH = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "de_CH", "swiss"));
        HI_ZZ = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "hi_ZZ", "qwerty"));
        ZZ = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                SubtypeLocaleUtils.NO_LANGUAGE, "qwerty"));
        DE_QWERTY = new RichInputMethodSubtype(
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                    Locale.GERMAN.toString(), "qwerty"));
        FR_QWERTZ = new RichInputMethodSubtype(
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                    Locale.FRENCH.toString(), "qwertz"));
        EN_US_AZERTY = new RichInputMethodSubtype(
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                    Locale.US.toString(), "azerty"));
        EN_UK_DVORAK = new RichInputMethodSubtype(
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                    Locale.UK.toString(), "dvorak"));
        ES_US_COLEMAK = new RichInputMethodSubtype(
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                    "es_US", "colemak"));
        ZZ_AZERTY = new RichInputMethodSubtype(
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                    SubtypeLocaleUtils.NO_LANGUAGE, "azerty"));
        ZZ_PC = new RichInputMethodSubtype(
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                    SubtypeLocaleUtils.NO_LANGUAGE, "pcqwerty"));
    }

    public void testAllFullDisplayNameForSpacebar() {
        for (final RichInputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(subtype.getRawSubtype());
            final String spacebarText = subtype.getFullDisplayName();
            final String languageName = SubtypeLocaleUtils
                    .getSubtypeLocaleDisplayName(subtype.getLocale());
            if (subtype.isNoLanguage()) {
                assertFalse(subtypeName, spacebarText.contains(languageName));
            } else {
                assertTrue(subtypeName, spacebarText.contains(languageName));
            }
        }
    }

   public void testAllMiddleDisplayNameForSpacebar() {
        for (final RichInputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(subtype.getRawSubtype());
            if (SubtypeLocaleUtils.sExceptionalLocaleDisplayedInRootLocale.contains(
                    subtype.getLocale())) {
                // Skip test because the language part of this locale string doesn't represent
                // the locale to be displayed on the spacebar (for example hi_ZZ and Hinglish).
                continue;
            }
            final String spacebarText = subtype.getMiddleDisplayName();
            if (subtype.isNoLanguage()) {
                assertEquals(subtypeName, SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(
                        subtype.getRawSubtype()), spacebarText);
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
    //  hi_ZZ qwerty  F  Hinglish  Hinglish
    //  zz    qwerty  F  QWERTY    QWERTY
    //  fr    qwertz  T  Français  Français
    //  de    qwerty  T  Deutsch   Deutsch
    //  en_US azerty  T  English   English (US)
    //  zz    azerty  T  AZERTY    AZERTY

    private final RunInLocale<Void> testsPredefinedSubtypesForSpacebar = new RunInLocale<Void>() {
        @Override
        protected Void job(final Resources res) {
            assertEquals("en_US", "English (US)", EN_US.getFullDisplayName());
            assertEquals("en_GB", "English (UK)", EN_GB.getFullDisplayName());
            assertEquals("es_US", "Español (EE.UU.)", ES_US.getFullDisplayName());
            assertEquals("fr", "Français", FR.getFullDisplayName());
            assertEquals("fr_CA", "Français (Canada)", FR_CA.getFullDisplayName());
            assertEquals("fr_CH", "Français (Suisse)", FR_CH.getFullDisplayName());
            assertEquals("de", "Deutsch", DE.getFullDisplayName());
            assertEquals("de_CH", "Deutsch (Schweiz)", DE_CH.getFullDisplayName());
            assertEquals("hi_ZZ", "Hinglish", HI_ZZ.getFullDisplayName());
            assertEquals("zz", "QWERTY", ZZ.getFullDisplayName());

            assertEquals("en_US", "English", EN_US.getMiddleDisplayName());
            assertEquals("en_GB", "English", EN_GB.getMiddleDisplayName());
            assertEquals("es_US", "Español", ES_US.getMiddleDisplayName());
            assertEquals("fr", "Français", FR.getMiddleDisplayName());
            assertEquals("fr_CA", "Français", FR_CA.getMiddleDisplayName());
            assertEquals("fr_CH", "Français", FR_CH.getMiddleDisplayName());
            assertEquals("de", "Deutsch", DE.getMiddleDisplayName());
            assertEquals("de_CH", "Deutsch", DE_CH.getMiddleDisplayName());
            assertEquals("hi_ZZ", "Hinglish", HI_ZZ.getMiddleDisplayName());
            assertEquals("zz", "QWERTY", ZZ.getMiddleDisplayName());
            return null;
        }
    };

    private final RunInLocale<Void> testsAdditionalSubtypesForSpacebar = new RunInLocale<Void>() {
        @Override
        protected Void job(final Resources res) {
            assertEquals("fr qwertz", "Français", FR_QWERTZ.getFullDisplayName());
            assertEquals("de qwerty", "Deutsch", DE_QWERTY.getFullDisplayName());
            assertEquals("en_US azerty", "English (US)", EN_US_AZERTY.getFullDisplayName());
            assertEquals("en_UK dvorak", "English (UK)", EN_UK_DVORAK.getFullDisplayName());
            assertEquals("es_US colemak", "Español (EE.UU.)", ES_US_COLEMAK.getFullDisplayName());
            assertEquals("zz azerty", "AZERTY", ZZ_AZERTY.getFullDisplayName());
            assertEquals("zz pc", "PC", ZZ_PC.getFullDisplayName());

            assertEquals("fr qwertz", "Français", FR_QWERTZ.getMiddleDisplayName());
            assertEquals("de qwerty", "Deutsch", DE_QWERTY.getMiddleDisplayName());
            assertEquals("en_US azerty", "English", EN_US_AZERTY.getMiddleDisplayName());
            assertEquals("en_UK dvorak", "English", EN_UK_DVORAK.getMiddleDisplayName());
            assertEquals("es_US colemak", "Español", ES_US_COLEMAK.getMiddleDisplayName());
            assertEquals("zz azerty", "AZERTY", ZZ_AZERTY.getMiddleDisplayName());
            assertEquals("zz pc", "PC", ZZ_PC.getMiddleDisplayName());
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
