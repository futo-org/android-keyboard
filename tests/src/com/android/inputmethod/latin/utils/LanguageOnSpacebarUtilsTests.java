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

package com.android.inputmethod.latin.utils;

import static com.android.inputmethod.latin.utils.LanguageOnSpacebarUtils.FORMAT_TYPE_FULL_LOCALE;
import static com.android.inputmethod.latin.utils.LanguageOnSpacebarUtils.FORMAT_TYPE_LANGUAGE_ONLY;
import static com.android.inputmethod.latin.utils.LanguageOnSpacebarUtils.FORMAT_TYPE_NONE;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.inputmethod.InputMethodSubtype;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.RichInputMethodSubtype;
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.LanguageOnSpacebarUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Locale;

import javax.annotation.Nonnull;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LanguageOnSpacebarUtilsTests {
    private RichInputMethodManager mRichImm;

    RichInputMethodSubtype EN_US_QWERTY;
    RichInputMethodSubtype EN_GB_QWERTY;
    RichInputMethodSubtype FR_AZERTY;
    RichInputMethodSubtype FR_CA_QWERTY;
    RichInputMethodSubtype FR_CH_SWISS;
    RichInputMethodSubtype FR_CH_QWERTY;
    RichInputMethodSubtype FR_CH_QWERTZ;
    RichInputMethodSubtype IW_HEBREW;
    RichInputMethodSubtype ZZ_QWERTY;

    @Before
    public void setUp() throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        RichInputMethodManager.init(context);
        mRichImm = RichInputMethodManager.getInstance();

        EN_US_QWERTY = findSubtypeOf(Locale.US.toString(), "qwerty");
        EN_GB_QWERTY = findSubtypeOf(Locale.UK.toString(), "qwerty");
        FR_AZERTY = findSubtypeOf(Locale.FRENCH.toString(), "azerty");
        FR_CA_QWERTY = findSubtypeOf(Locale.CANADA_FRENCH.toString(), "qwerty");
        FR_CH_SWISS = findSubtypeOf("fr_CH", "swiss");
        FR_CH_QWERTZ = new RichInputMethodSubtype(
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype("fr_CH", "qwertz"));
        FR_CH_QWERTY = new RichInputMethodSubtype(
                AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype("fr_CH", "qwerty"));
        IW_HEBREW = findSubtypeOf("iw", "hebrew");
        ZZ_QWERTY = findSubtypeOf(SubtypeLocaleUtils.NO_LANGUAGE, "qwerty");
    }

    @Nonnull
    private RichInputMethodSubtype findSubtypeOf(final String localeString,
            final String keyboardLayoutSetName) {
        final InputMethodSubtype subtype = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                localeString, keyboardLayoutSetName);
        if (subtype == null) {
            throw new RuntimeException("Can't find subtype of " + localeString + " with "
                    + keyboardLayoutSetName);
        }
        return new RichInputMethodSubtype(subtype);
    }

    private static void enableSubtypes(final RichInputMethodSubtype ... subtypes) {
        final ArrayList<InputMethodSubtype> enabledSubtypes = new ArrayList<>();
        for (final RichInputMethodSubtype subtype : subtypes) {
            enabledSubtypes.add(subtype.getRawSubtype());
        }
        LanguageOnSpacebarUtils.setEnabledSubtypes(enabledSubtypes);
    }

    private static void assertFormatType(final RichInputMethodSubtype subtype,
            final boolean implicitlyEnabledSubtype, final Locale systemLocale,
            final int expectedFormat) {
        LanguageOnSpacebarUtils.onSubtypeChanged(subtype, implicitlyEnabledSubtype, systemLocale);
        assertEquals(subtype.getLocale() + " implicitly=" + implicitlyEnabledSubtype
                + " in " + systemLocale, expectedFormat,
                LanguageOnSpacebarUtils.getLanguageOnSpacebarFormatType(subtype));
    }

    @Test
    public void testOneSubtypeImplicitlyEnabled() {
        enableSubtypes(EN_US_QWERTY);
        assertFormatType(EN_US_QWERTY, true, Locale.US,            FORMAT_TYPE_NONE);

        enableSubtypes(EN_GB_QWERTY);
        assertFormatType(EN_GB_QWERTY, true, Locale.UK,            FORMAT_TYPE_NONE);

        enableSubtypes(FR_AZERTY);
        assertFormatType(FR_AZERTY,    true, Locale.FRANCE,        FORMAT_TYPE_NONE);

        enableSubtypes(FR_CA_QWERTY);
        assertFormatType(FR_CA_QWERTY, true, Locale.CANADA_FRENCH, FORMAT_TYPE_NONE);
    }

    @Test
    public void testOneSubtypeExplicitlyEnabled() {
        enableSubtypes(EN_US_QWERTY);
        assertFormatType(EN_US_QWERTY, false, Locale.UK,     FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(EN_US_QWERTY, false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);

        enableSubtypes(EN_GB_QWERTY);
        assertFormatType(EN_GB_QWERTY, false, Locale.US,     FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(EN_GB_QWERTY, false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);

        enableSubtypes(FR_AZERTY);
        assertFormatType(FR_AZERTY,    false, Locale.US,            FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_AZERTY,    false, Locale.CANADA_FRENCH, FORMAT_TYPE_LANGUAGE_ONLY);

        enableSubtypes(FR_CA_QWERTY);
        assertFormatType(FR_CA_QWERTY, false, Locale.US,            FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CA_QWERTY, false, Locale.FRANCE,        FORMAT_TYPE_LANGUAGE_ONLY);
    }

    @Test
    public void testOneSubtypeImplicitlyEnabledWithNoLanguageSubtype() {
        final Locale Locale_IW = new Locale("iw");
        enableSubtypes(IW_HEBREW, ZZ_QWERTY);
        // TODO: Should this be FORMAT_TYPE_NONE?
        assertFormatType(IW_HEBREW,    true, Locale_IW, FORMAT_TYPE_LANGUAGE_ONLY);
        // TODO: Should this be FORMAT_TYPE_NONE?
        assertFormatType(ZZ_QWERTY,    true, Locale_IW, FORMAT_TYPE_FULL_LOCALE);
    }

    @Test
    public void testTwoSubtypesExplicitlyEnabled() {
        enableSubtypes(EN_US_QWERTY, FR_AZERTY);
        assertFormatType(EN_US_QWERTY, false, Locale.US,     FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_AZERTY,    false, Locale.US,     FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(EN_US_QWERTY, false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_AZERTY,    false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(EN_US_QWERTY, false, Locale.JAPAN,  FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_AZERTY,    false, Locale.JAPAN,  FORMAT_TYPE_LANGUAGE_ONLY);

        enableSubtypes(EN_US_QWERTY, ZZ_QWERTY);
        assertFormatType(EN_US_QWERTY, false, Locale.US,     FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(ZZ_QWERTY,    false, Locale.US,     FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(EN_US_QWERTY, false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(ZZ_QWERTY,    false, Locale.FRANCE, FORMAT_TYPE_FULL_LOCALE);

    }

    @Test
    public void testMultiSubtypeWithSameLanuageAndSameLayout() {
        // Explicitly enable en_US, en_GB, fr_FR, and no language keyboards.
        enableSubtypes(EN_US_QWERTY, EN_GB_QWERTY, FR_CA_QWERTY, ZZ_QWERTY);

        assertFormatType(EN_US_QWERTY, false, Locale.US,    FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(EN_GB_QWERTY, false, Locale.US,    FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(FR_CA_QWERTY, false, Locale.US,    FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(ZZ_QWERTY,    false, Locale.US,    FORMAT_TYPE_FULL_LOCALE);

        assertFormatType(EN_US_QWERTY, false, Locale.JAPAN, FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(EN_GB_QWERTY, false, Locale.JAPAN, FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(FR_CA_QWERTY, false, Locale.JAPAN, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(ZZ_QWERTY,    false, Locale.JAPAN, FORMAT_TYPE_FULL_LOCALE);
    }

    @Test
    public void testMultiSubtypesWithSameLanguageButHaveDifferentLayout() {
        enableSubtypes(FR_AZERTY, FR_CA_QWERTY, FR_CH_SWISS, FR_CH_QWERTZ);

        assertFormatType(FR_AZERTY,    false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CA_QWERTY, false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CH_SWISS,  false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CH_QWERTZ, false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);

        assertFormatType(FR_AZERTY,    false, Locale.CANADA_FRENCH, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CA_QWERTY, false, Locale.CANADA_FRENCH, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CH_SWISS,  false, Locale.CANADA_FRENCH, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CH_QWERTZ, false, Locale.CANADA_FRENCH, FORMAT_TYPE_LANGUAGE_ONLY);

        assertFormatType(FR_AZERTY,    false, Locale.JAPAN, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CA_QWERTY, false, Locale.JAPAN, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CH_SWISS,  false, Locale.JAPAN, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CH_QWERTZ, false, Locale.JAPAN, FORMAT_TYPE_LANGUAGE_ONLY);
    }

    @Test
    public void testMultiSubtypesWithSameLanguageAndMayHaveSameLayout() {
        enableSubtypes(FR_AZERTY, FR_CA_QWERTY, FR_CH_SWISS, FR_CH_QWERTY, FR_CH_QWERTZ);

        assertFormatType(FR_AZERTY,    false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CA_QWERTY, false, Locale.FRANCE, FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(FR_CH_SWISS,  false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CH_QWERTY, false, Locale.FRANCE, FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(FR_CH_QWERTZ, false, Locale.FRANCE, FORMAT_TYPE_LANGUAGE_ONLY);

        assertFormatType(FR_AZERTY,    false, Locale.CANADA_FRENCH, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CA_QWERTY, false, Locale.CANADA_FRENCH, FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(FR_CH_SWISS,  false, Locale.CANADA_FRENCH, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CH_QWERTY, false, Locale.CANADA_FRENCH, FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(FR_CH_QWERTZ, false, Locale.CANADA_FRENCH, FORMAT_TYPE_LANGUAGE_ONLY);

        assertFormatType(FR_AZERTY,    false, Locale.JAPAN, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CA_QWERTY, false, Locale.JAPAN, FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(FR_CH_SWISS,  false, Locale.JAPAN, FORMAT_TYPE_LANGUAGE_ONLY);
        assertFormatType(FR_CH_QWERTY, false, Locale.JAPAN, FORMAT_TYPE_FULL_LOCALE);
        assertFormatType(FR_CH_QWERTZ, false, Locale.JAPAN, FORMAT_TYPE_LANGUAGE_ONLY);
    }
}
