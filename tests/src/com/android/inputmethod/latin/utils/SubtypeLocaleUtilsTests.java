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
public class SubtypeLocaleUtilsTests extends AndroidTestCase {
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

    public void testAllFullDisplayName() {
        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(subtype);
            if (SubtypeLocaleUtils.isNoLanguage(subtype)) {
                final String layoutName = SubtypeLocaleUtils
                        .getKeyboardLayoutSetDisplayName(subtype);
                assertTrue(subtypeName, subtypeName.contains(layoutName));
            } else {
                final String languageName = SubtypeLocaleUtils
                        .getSubtypeLocaleDisplayNameInSystemLocale(subtype.getLocale());
                assertTrue(subtypeName, subtypeName.contains(languageName));
            }
        }
    }

    public void testKeyboardLayoutSetName() {
        assertEquals("en_US", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(EN_US));
        assertEquals("en_GB", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(EN_GB));
        assertEquals("es_US", "spanish", SubtypeLocaleUtils.getKeyboardLayoutSetName(ES_US));
        assertEquals("fr", "azerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(FR));
        assertEquals("fr_CA", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(FR_CA));
        assertEquals("fr_CH", "swiss", SubtypeLocaleUtils.getKeyboardLayoutSetName(FR_CH));
        assertEquals("de", "qwertz", SubtypeLocaleUtils.getKeyboardLayoutSetName(DE));
        assertEquals("de_CH", "swiss", SubtypeLocaleUtils.getKeyboardLayoutSetName(DE_CH));
        assertEquals("zz", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(ZZ));

        assertEquals("de qwerty", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(DE_QWERTY));
        assertEquals("fr qwertz", "qwertz", SubtypeLocaleUtils.getKeyboardLayoutSetName(FR_QWERTZ));
        assertEquals("en_US azerty", "azerty",
                SubtypeLocaleUtils.getKeyboardLayoutSetName(EN_US_AZERTY));
        assertEquals("en_UK dvorak", "dvorak",
                SubtypeLocaleUtils.getKeyboardLayoutSetName(EN_UK_DVORAK));
        assertEquals("es_US colemak", "colemak",
                SubtypeLocaleUtils.getKeyboardLayoutSetName(ES_US_COLEMAK));
        assertEquals("zz azerty", "azerty",
                SubtypeLocaleUtils.getKeyboardLayoutSetName(ZZ_AZERTY));
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
    //  fr_CH swiss   F  French (Switzerland)
    //  de    qwertz  F  German
    //  de_CH swiss   F  German (Switzerland)
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
                assertEquals("fr", "French",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR));
                assertEquals("fr_CA", "French (Canada)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_CA));
                assertEquals("fr_CH", "French (Switzerland)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_CH));
                assertEquals("de", "German",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE));
                assertEquals("de_CH", "German (Switzerland)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE_CH));
                assertEquals("zz", "Alphabet (QWERTY)",
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
                assertEquals("fr qwertz", "French (QWERTZ)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_QWERTZ));
                assertEquals("de qwerty", "German (QWERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE_QWERTY));
                assertEquals("en_US azerty", "English (US) (AZERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_US_AZERTY));
                assertEquals("en_UK dvorak","English (UK) (Dvorak)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_UK_DVORAK));
                assertEquals("es_US colemak", "Spanish (US) (Colemak)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ES_US_COLEMAK));
                assertEquals("zz azerty", "Alphabet (AZERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ZZ_AZERTY));
                assertEquals("zz pc", "Alphabet (PC)",
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
    //  fr_CH swiss   F  Français (Suisse)
    //  de    qwertz  F  Allemand
    //  de_CH swiss   F  Allemand (Suisse)
    //  zz    qwerty  F  Alphabet latin (QWERTY)
    //  fr    qwertz  T  Français (QWERTZ)
    //  de    qwerty  T  Allemand (QWERTY)
    //  en_US azerty  T  Anglais (États-Unis) (AZERTY)   exception
    //  en_UK dvorak  T  Anglais (Royaume-Uni) (Dvorak)   exception
    //  es_US colemak T  Espagnol (États-Unis) (Colemak)  exception
    //  zz    pc      T  Alphabet latin (PC)

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
                assertEquals("fr", "Français",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR));
                assertEquals("fr_CA", "Français (Canada)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_CA));
                assertEquals("fr_CH", "Français (Suisse)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_CH));
                assertEquals("de", "Allemand",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE));
                assertEquals("de_CH", "Allemand (Suisse)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE_CH));
                assertEquals("zz", "Alphabet latin (QWERTY)",
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
                assertEquals("fr qwertz", "Français (QWERTZ)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(FR_QWERTZ));
                assertEquals("de qwerty", "Allemand (QWERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(DE_QWERTY));
                assertEquals("en_US azerty", "Anglais (États-Unis) (AZERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_US_AZERTY));
                assertEquals("en_UK dvorak", "Anglais (Royaume-Uni) (Dvorak)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(EN_UK_DVORAK));
                assertEquals("es_US colemak", "Espagnol (États-Unis) (Colemak)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ES_US_COLEMAK));
                assertEquals("zz azerty", "Alphabet latin (AZERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ZZ_AZERTY));
                assertEquals("zz pc", "Alphabet latin (PC)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ZZ_PC));
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }

    public void testIsRtlLanguage() {
        // Known Right-to-Left language subtypes.
        final InputMethodSubtype ARABIC = mRichImm
                .findSubtypeByLocaleAndKeyboardLayoutSet("ar", "arabic");
        assertNotNull("Arabic", ARABIC);
        final InputMethodSubtype FARSI = mRichImm
                .findSubtypeByLocaleAndKeyboardLayoutSet("fa", "farsi");
        assertNotNull("Farsi", FARSI);
        final InputMethodSubtype HEBREW = mRichImm
                .findSubtypeByLocaleAndKeyboardLayoutSet("iw", "hebrew");
        assertNotNull("Hebrew", HEBREW);

        for (final InputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(subtype);
            if (subtype.equals(ARABIC) || subtype.equals(FARSI) || subtype.equals(HEBREW)) {
                assertTrue(subtypeName, SubtypeLocaleUtils.isRtlLanguage(subtype));
            } else {
                assertFalse(subtypeName, SubtypeLocaleUtils.isRtlLanguage(subtype));
            }
        }
    }
}
