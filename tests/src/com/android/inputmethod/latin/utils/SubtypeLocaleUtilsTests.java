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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.RichInputMethodSubtype;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SubtypeLocaleUtilsTests {
    // All input method subtypes of LatinIME.
    private final ArrayList<RichInputMethodSubtype> mSubtypesList = new ArrayList<>();

    private RichInputMethodManager mRichImm;
    private Resources mRes;
    private InputMethodSubtype mSavedAddtionalSubtypes[];

    InputMethodSubtype EN_US;
    InputMethodSubtype EN_GB;
    InputMethodSubtype ES_US;
    InputMethodSubtype FR;
    InputMethodSubtype FR_CA;
    InputMethodSubtype FR_CH;
    InputMethodSubtype DE;
    InputMethodSubtype DE_CH;
    InputMethodSubtype HI;
    InputMethodSubtype SR;
    InputMethodSubtype ZZ;
    InputMethodSubtype DE_QWERTY;
    InputMethodSubtype FR_QWERTZ;
    InputMethodSubtype EN_US_AZERTY;
    InputMethodSubtype EN_UK_DVORAK;
    InputMethodSubtype ES_US_COLEMAK;
    InputMethodSubtype ZZ_AZERTY;
    InputMethodSubtype ZZ_PC;

    // These are preliminary subtypes and may not exist.
    InputMethodSubtype HI_LATN; // Hinglish
    InputMethodSubtype SR_LATN; // Serbian Latin
    InputMethodSubtype HI_LATN_DVORAK; // Hinglis Dvorak
    InputMethodSubtype SR_LATN_QWERTY; // Serbian Latin Qwerty

    @Before
    public void setUp() throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        mRes = context.getResources();
        RichInputMethodManager.init(context);
        mRichImm = RichInputMethodManager.getInstance();

        // Save and reset additional subtypes
        mSavedAddtionalSubtypes = mRichImm.getAdditionalSubtypes();
        final InputMethodSubtype[] predefinedAddtionalSubtypes =
                AdditionalSubtypeUtils.createAdditionalSubtypesArray(
                        AdditionalSubtypeUtils.createPrefSubtypes(
                                mRes.getStringArray(R.array.predefined_subtypes)));
        mRichImm.setAdditionalInputMethodSubtypes(predefinedAddtionalSubtypes);

        final InputMethodInfo imi = mRichImm.getInputMethodInfoOfThisIme();
        final int subtypeCount = imi.getSubtypeCount();
        for (int index = 0; index < subtypeCount; index++) {
            final InputMethodSubtype subtype = imi.getSubtypeAt(index);
            mSubtypesList.add(new RichInputMethodSubtype(subtype));
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
        HI = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "hi", "hindi");
        SR = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "sr", "south_slavic");
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

        HI_LATN = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet("hi_ZZ", "qwerty");
        if (HI_LATN != null) {
            HI_LATN_DVORAK = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                    "hi_ZZ", "dvorak");
        }
        SR_LATN = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet("sr_ZZ", "serbian_qwertz");
        if (SR_LATN != null) {
            SR_LATN_QWERTY = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                    "sr_ZZ", "qwerty");
        }
    }

    @After
    public void tearDown() throws Exception {
        // Restore additional subtypes.
        mRichImm.setAdditionalInputMethodSubtypes(mSavedAddtionalSubtypes);
    }

    @Test
    public void testAllFullDisplayName() {
        for (final RichInputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(subtype.getRawSubtype());
            if (subtype.isNoLanguage()) {
                final String layoutName = SubtypeLocaleUtils
                        .getKeyboardLayoutSetDisplayName(subtype.getRawSubtype());
                assertTrue(subtypeName, subtypeName.contains(layoutName));
            } else {
                final String languageName = SubtypeLocaleUtils
                        .getSubtypeLocaleDisplayNameInSystemLocale(subtype.getLocale().toString());
                assertTrue(subtypeName, subtypeName.contains(languageName));
            }
        }
    }

    @Test
    public void testKeyboardLayoutSetName() {
        assertEquals("en_US", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(EN_US));
        assertEquals("en_GB", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(EN_GB));
        assertEquals("es_US", "spanish", SubtypeLocaleUtils.getKeyboardLayoutSetName(ES_US));
        assertEquals("fr", "azerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(FR));
        assertEquals("fr_CA", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(FR_CA));
        assertEquals("fr_CH", "swiss", SubtypeLocaleUtils.getKeyboardLayoutSetName(FR_CH));
        assertEquals("de", "qwertz", SubtypeLocaleUtils.getKeyboardLayoutSetName(DE));
        assertEquals("de_CH", "swiss", SubtypeLocaleUtils.getKeyboardLayoutSetName(DE_CH));
        assertEquals("hi", "hindi", SubtypeLocaleUtils.getKeyboardLayoutSetName(HI));
        assertEquals("sr", "south_slavic", SubtypeLocaleUtils.getKeyboardLayoutSetName(SR));
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

        // These are preliminary subtypes and may not exist.
        if (HI_LATN != null) {
            assertEquals("hi_ZZ", "qwerty", SubtypeLocaleUtils.getKeyboardLayoutSetName(HI_LATN));
            assertEquals("hi_ZZ dvorak", "dvorak",
                    SubtypeLocaleUtils.getKeyboardLayoutSetName(HI_LATN_DVORAK));
        }
        if (SR_LATN != null) {
            assertEquals("sr_ZZ", "serbian_qwertz",
                    SubtypeLocaleUtils.getKeyboardLayoutSetName(SR_LATN));
            assertEquals("sr_ZZ qwerty", "qwerty",
                    SubtypeLocaleUtils.getKeyboardLayoutSetName(SR_LATN_QWERTY));
        }
    }

    // InputMethodSubtype's display name in system locale (en_US).
    //               isAdditionalSubtype (T=true, F=false)
    // locale layout         |  display name
    // ------ -------------- - ----------------------
    //  en_US qwerty         F  English (US)            exception
    //  en_GB qwerty         F  English (UK)            exception
    //  es_US spanish        F  Spanish (US)            exception
    //  fr    azerty         F  French
    //  fr_CA qwerty         F  French (Canada)
    //  fr_CH swiss          F  French (Switzerland)
    //  de    qwertz         F  German
    //  de_CH swiss          F  German (Switzerland)
    //  hi    hindi          F  Hindi
    //  hi_ZZ qwerty         F  Hinglish                exception
    //  sr    south_slavic   F  Serbian
    //  sr_ZZ serbian_qwertz F  Serbian (Latin)         exception
    //  zz    qwerty         F  Alphabet (QWERTY)
    //  fr    qwertz         T  French (QWERTZ)
    //  de    qwerty         T  German (QWERTY)
    //  en_US azerty         T  English (US) (AZERTY)   exception
    //  en_UK dvorak         T  English (UK) (Dvorak)   exception
    //  es_US colemak        T  Spanish (US) (Colemak)  exception
    //  hi_ZZ dvorak         T  Hinglish (Dvorka)       exception
    //  sr_ZZ qwerty         T  Serbian (QWERTY)        exception
    //  zz    pc             T  Alphabet (PC)

    @Test
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
                assertEquals("hi", "Hindi",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(HI));
                assertEquals("sr", "Serbian",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(SR));
                assertEquals("zz", "Alphabet (QWERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ZZ));
                // These are preliminary subtypes and may not exist.
                if (HI_LATN != null) {
                    assertEquals("hi_ZZ", "Hinglish",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(HI_LATN));
                }
                if (SR_LATN != null) {
                    assertEquals("sr_ZZ", "Serbian (Latin)",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(SR_LATN));
                }
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.ENGLISH);
    }

    @Test
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
                // These are preliminary subtypes and may not exist.
                if (HI_LATN_DVORAK != null) {
                    assertEquals("hi_ZZ", "Hinglish (Dvorak)",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(HI_LATN_DVORAK));
                }
                if (SR_LATN_QWERTY != null) {
                    assertEquals("sr_ZZ", "Serbian (QWERTY)",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(SR_LATN_QWERTY));
                }
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.ENGLISH);
    }

    // InputMethodSubtype's display name in system locale (fr).
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  display name
    // ------ ------- - ----------------------
    //  en_US qwerty         F  Anglais (États-Unis)             exception
    //  en_GB qwerty         F  Anglais (Royaume-Uni)            exception
    //  es_US spanish        F  Espagnol (États-Unis)            exception
    //  fr    azerty         F  Français
    //  fr_CA qwerty         F  Français (Canada)
    //  fr_CH swiss          F  Français (Suisse)
    //  de    qwertz         F  Allemand
    //  de_CH swiss          F  Allemand (Suisse)
    //  hi    hindi          F  Hindi                            exception
    //  hi_ZZ qwerty         F  Hindi/Anglais                    exception
    //  sr    south_slavic   F  Serbe                            exception
    //  sr_ZZ serbian_qwertz F  Serbe (latin)                    exception
    //  zz    qwerty         F  Alphabet latin (QWERTY)
    //  fr    qwertz         T  Français (QWERTZ)
    //  de    qwerty         T  Allemand (QWERTY)
    //  en_US azerty         T  Anglais (États-Unis) (AZERTY)    exception
    //  en_UK dvorak         T  Anglais (Royaume-Uni) (Dvorak)   exception
    //  es_US colemak        T  Espagnol (États-Unis) (Colemak)  exception
    //  hi_ZZ dvorak         T  Hindi/Anglais (Dvorka)           exception
    //  sr_ZZ qwerty         T  Serbe (QWERTY)                   exception
    //  zz    pc             T  Alphabet latin (PC)

    @Test
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
                assertEquals("hi", "Hindi",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(HI));
                assertEquals("sr", "Serbe",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(SR));
                assertEquals("zz", "Alphabet latin (QWERTY)",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(ZZ));
                // These are preliminary subtypes and may not exist.
                if (HI_LATN != null) {
                    assertEquals("hi_ZZ", "Hindi/Anglais",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(HI_LATN));
                }
                if (SR_LATN != null) {
                    assertEquals("sr_ZZ", "Serbe (latin)",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(SR_LATN));
                }
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }

    @Test
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
                // These are preliminary subtypes and may not exist.
                if (HI_LATN_DVORAK != null) {
                    assertEquals("hi_ZZ", "Hindi/Anglais (Dvorak)",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(HI_LATN_DVORAK));
                }
                if (SR_LATN_QWERTY != null) {
                    assertEquals("sr_ZZ", "Serbe (QWERTY)",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(SR_LATN_QWERTY));
                }
                return null;
            }
        };
        tests.runInLocale(mRes, Locale.FRENCH);
    }

    // InputMethodSubtype's display name in system locale (hi).
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  display name
    // ------ ------- - ----------------------
    //  hi    hindi   F  हिन्दी
    //  hi_ZZ qwerty  F  हिंग्लिश
    //  hi_ZZ dvorak  T  हिंग्लिश (Dvorak)

    @Test
    public void testHinglishSubtypesInHindiSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job (final Resources res) {
                assertEquals("hi", "हिन्दी",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(HI));
                // These are preliminary subtypes and may not exist.
                if (HI_LATN != null) {
                    assertEquals("hi_ZZ", "हिंग्लिश",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(HI_LATN));
                    assertEquals("hi_ZZ", "हिंग्लिश (Dvorak)",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(HI_LATN_DVORAK));
                }
                return null;
            }
        };
        tests.runInLocale(mRes, new Locale("hi"));
    }

    // InputMethodSubtype's display name in system locale (sr).
    //               isAdditionalSubtype (T=true, F=false)
    // locale layout         |  display name
    // ------ -------------- - ----------------------
    //  sr    south_slavic   F  Српски
    //  sr_ZZ serbian_qwertz F  Српски (латиница)
    //  sr_ZZ qwerty         T  Српски (QWERTY)

    @Test
    public void testSerbianLatinSubtypesInSerbianSystemLocale() {
        final RunInLocale<Void> tests = new RunInLocale<Void>() {
            @Override
            protected Void job (final Resources res) {
                assertEquals("sr", "Српски",
                        SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(SR));
                // These are preliminary subtypes and may not exist.
                if (SR_LATN != null) {
                    assertEquals("sr_ZZ", "Српски (латиница)",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(SR_LATN));
                    assertEquals("sr_ZZ", "Српски (QWERTY)",
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(SR_LATN_QWERTY));
                }
                return null;
            }
        };
        tests.runInLocale(mRes, new Locale("sr"));
    }

    @Test
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

        for (final RichInputMethodSubtype subtype : mSubtypesList) {
            final InputMethodSubtype rawSubtype = subtype.getRawSubtype();
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(rawSubtype);
            if (rawSubtype.equals(ARABIC) || rawSubtype.equals(FARSI)
                    || rawSubtype.equals(HEBREW)) {
                assertTrue(subtypeName, subtype.isRtlSubtype());
            } else {
                assertFalse(subtypeName, subtype.isRtlSubtype());
            }
        }
    }
}
