/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.RunInLocale;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RichInputMethodSubtypeTests {
    // All input method subtypes of LatinIME.
    private final ArrayList<RichInputMethodSubtype> mSubtypesList = new ArrayList<>();

    private RichInputMethodManager mRichImm;
    private Resources mRes;
    private InputMethodSubtype mSavedAddtionalSubtypes[];

    RichInputMethodSubtype EN_US;
    RichInputMethodSubtype EN_GB;
    RichInputMethodSubtype ES_US;
    RichInputMethodSubtype FR;
    RichInputMethodSubtype FR_CA;
    RichInputMethodSubtype FR_CH;
    RichInputMethodSubtype DE;
    RichInputMethodSubtype DE_CH;
    RichInputMethodSubtype HI;
    RichInputMethodSubtype SR;
    RichInputMethodSubtype ZZ;
    RichInputMethodSubtype DE_QWERTY;
    RichInputMethodSubtype FR_QWERTZ;
    RichInputMethodSubtype EN_US_AZERTY;
    RichInputMethodSubtype EN_UK_DVORAK;
    RichInputMethodSubtype ES_US_COLEMAK;
    RichInputMethodSubtype ZZ_AZERTY;
    RichInputMethodSubtype ZZ_PC;

    // These are preliminary subtypes and may not exist.
    RichInputMethodSubtype HI_LATN; // Hinglish
    RichInputMethodSubtype SR_LATN; // Serbian Latin
    RichInputMethodSubtype HI_LATN_DVORAK;
    RichInputMethodSubtype SR_LATN_QWERTY;

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
        HI = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "hi", "hindi"));
        SR = new RichInputMethodSubtype(mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "sr", "south_slavic"));
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

        final InputMethodSubtype hiLatn = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "hi_ZZ", "qwerty");
        if (hiLatn != null) {
            HI_LATN = new RichInputMethodSubtype(hiLatn);
            HI_LATN_DVORAK = new RichInputMethodSubtype(
                    AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                            "hi_ZZ", "dvorak"));
        }
        final InputMethodSubtype srLatn = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "sr_ZZ", "serbian_qwertz");
        if (srLatn != null) {
            SR_LATN = new RichInputMethodSubtype(srLatn);
            SR_LATN_QWERTY = new RichInputMethodSubtype(
                    AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                            "sr_ZZ", "qwerty"));
        }
    }

    @After
    public void tearDown() throws Exception {
        // Restore additional subtypes.
        mRichImm.setAdditionalInputMethodSubtypes(mSavedAddtionalSubtypes);
    }

    @Test
    public void testAllFullDisplayNameForSpacebar() {
        for (final RichInputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(subtype.getRawSubtype());
            final String spacebarText = subtype.getFullDisplayName();
            final String languageName = SubtypeLocaleUtils
                    .getSubtypeLocaleDisplayName(subtype.getLocale().toString());
            if (subtype.isNoLanguage()) {
                assertFalse(subtypeName, spacebarText.contains(languageName));
            } else {
                assertTrue(subtypeName, spacebarText.contains(languageName));
            }
        }
    }

    @Test
    public void testAllMiddleDisplayNameForSpacebar() {
        for (final RichInputMethodSubtype subtype : mSubtypesList) {
            final String subtypeName = SubtypeLocaleUtils
                    .getSubtypeDisplayNameInSystemLocale(subtype.getRawSubtype());
            final Locale locale = subtype.getLocale();
            final Locale displayLocale = SubtypeLocaleUtils.getDisplayLocaleOfSubtypeLocale(
                    locale.toString());
            if (Locale.ROOT.equals(displayLocale)) {
                // Skip test because the language part of this locale string doesn't represent
                // the locale to be displayed on the spacebar (for example Hinglish).
                continue;
            }
            final String spacebarText = subtype.getMiddleDisplayName();
            if (subtype.isNoLanguage()) {
                assertEquals(subtypeName, SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(
                        subtype.getRawSubtype()), spacebarText);
            } else {
                assertEquals(subtypeName,
                        SubtypeLocaleUtils.getSubtypeLanguageDisplayName(locale.toString()),
                        spacebarText);
            }
        }
    }

    // InputMethodSubtype's display name for spacebar text in its locale.
    //               isAdditionalSubtype (T=true, F=false)
    // locale layout         |  Middle     Full
    // ------ -------------- - --------- ----------------------
    //  en_US qwerty         F  English   English (US)           exception
    //  en_GB qwerty         F  English   English (UK)           exception
    //  es_US spanish        F  Español   Español (EE.UU.)       exception
    //  fr    azerty         F  Français  Français
    //  fr_CA qwerty         F  Français  Français (Canada)
    //  fr_CH swiss          F  Français  Français (Suisse)
    //  de    qwertz         F  Deutsch   Deutsch
    //  de_CH swiss          F  Deutsch   Deutsch (Schweiz)
    //  hi    hindi          F  हिन्दी       हिन्दी
    //  hi_ZZ qwerty         F  Hinglish  Hinglish               exception
    //  sr    south_slavic   F  Српски    Српски
    //  sr_ZZ serbian_qwertz F  Srpski    Srpski                 exception
    //  zz    qwerty         F  QWERTY    QWERTY
    //  fr    qwertz         T  Français  Français
    //  de    qwerty         T  Deutsch   Deutsch
    //  en_US azerty         T  English   English (US)
    //  en_GB dvorak         T  English   English (UK)
    //  hi_ZZ dvorak         T  Hinglish  Hinglish               exception
    //  sr_ZZ qwerty         T  Srpski    Srpski                 exception
    //  zz    azerty         T  AZERTY    AZERTY

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
            assertEquals("hi", "हिन्दी", HI.getFullDisplayName());
            assertEquals("sr", "Српски", SR.getFullDisplayName());
            assertEquals("zz", "QWERTY", ZZ.getFullDisplayName());

            assertEquals("en_US", "English", EN_US.getMiddleDisplayName());
            assertEquals("en_GB", "English", EN_GB.getMiddleDisplayName());
            assertEquals("es_US", "Español", ES_US.getMiddleDisplayName());
            assertEquals("fr", "Français", FR.getMiddleDisplayName());
            assertEquals("fr_CA", "Français", FR_CA.getMiddleDisplayName());
            assertEquals("fr_CH", "Français", FR_CH.getMiddleDisplayName());
            assertEquals("de", "Deutsch", DE.getMiddleDisplayName());
            assertEquals("de_CH", "Deutsch", DE_CH.getMiddleDisplayName());
            assertEquals("zz", "QWERTY", ZZ.getMiddleDisplayName());

            // These are preliminary subtypes and may not exist.
            if (HI_LATN != null) {
                assertEquals("hi_ZZ", "Hinglish", HI_LATN.getFullDisplayName());
                assertEquals("hi_ZZ", "Hinglish", HI_LATN.getMiddleDisplayName());
            }
            if (SR_LATN != null) {
                assertEquals("sr_ZZ", "Srpski", SR_LATN.getFullDisplayName());
                assertEquals("sr_ZZ", "Srpski", SR_LATN.getMiddleDisplayName());
            }
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

            // These are preliminary subtypes and may not exist.
            if (HI_LATN_DVORAK != null) {
                assertEquals("hi_ZZ dvorak", "Hinglish", HI_LATN_DVORAK.getFullDisplayName());
                assertEquals("hi_ZZ dvorak", "Hinglish", HI_LATN_DVORAK.getMiddleDisplayName());
            }
            if (SR_LATN_QWERTY != null) {
                assertEquals("sr_ZZ qwerty", "Srpski", SR_LATN_QWERTY.getFullDisplayName());
                assertEquals("sr_ZZ qwerty", "Srpski", SR_LATN_QWERTY.getMiddleDisplayName());
            }
            return null;
        }
    };

    @Test
    public void testPredefinedSubtypesForSpacebarInEnglish() {
        testsPredefinedSubtypesForSpacebar.runInLocale(mRes, Locale.ENGLISH);
    }

    @Test
    public void testAdditionalSubtypeForSpacebarInEnglish() {
        testsAdditionalSubtypesForSpacebar.runInLocale(mRes, Locale.ENGLISH);
    }

    @Test
    public void testPredefinedSubtypesForSpacebarInFrench() {
        testsPredefinedSubtypesForSpacebar.runInLocale(mRes, Locale.FRENCH);
    }

    @Test
    public void testAdditionalSubtypeForSpacebarInFrench() {
        testsAdditionalSubtypesForSpacebar.runInLocale(mRes, Locale.FRENCH);
    }

    @Test
    public void testRichInputMethodSubtypeForNullInputMethodSubtype() {
        RichInputMethodSubtype subtype = RichInputMethodSubtype.getRichInputMethodSubtype(null);
        assertNotNull(subtype);
        assertEquals("zz", subtype.getRawSubtype().getLocale());
        assertEquals("keyboard", subtype.getRawSubtype().getMode());
    }
}
