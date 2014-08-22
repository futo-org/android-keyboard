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

package com.android.inputmethod.keyboard.internal;

import static com.android.inputmethod.keyboard.internal.LanguageOnSpacebarHelper.FORMAT_TYPE_FULL_LOCALE;
import static com.android.inputmethod.keyboard.internal.LanguageOnSpacebarHelper.FORMAT_TYPE_LANGUAGE_ONLY;
import static com.android.inputmethod.keyboard.internal.LanguageOnSpacebarHelper.FORMAT_TYPE_NONE;

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@SmallTest
public class LanguageOnSpacebarHelperTests extends AndroidTestCase {
    private final LanguageOnSpacebarHelper mLanguageOnSpacebarHelper =
            new LanguageOnSpacebarHelper();

    private RichInputMethodManager mRichImm;

    InputMethodSubtype EN_US_QWERTY;
    InputMethodSubtype EN_GB_QWERTY;
    InputMethodSubtype FR_AZERTY;
    InputMethodSubtype FR_CA_QWERTY;
    InputMethodSubtype FR_CH_SWISS;
    InputMethodSubtype FR_CH_QWERTY;
    InputMethodSubtype FR_CH_QWERTZ;
    InputMethodSubtype ZZ_QWERTY;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getContext();
        RichInputMethodManager.init(context);
        mRichImm = RichInputMethodManager.getInstance();
        SubtypeLocaleUtils.init(context);

        EN_US_QWERTY = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.US.toString(), "qwerty");
        EN_GB_QWERTY = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.UK.toString(), "qwerty");
        FR_AZERTY = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.FRENCH.toString(), "azerty");
        FR_CA_QWERTY = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.CANADA_FRENCH.toString(), "qwerty");
        FR_CH_SWISS = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                "fr_CH", "swiss");
        FR_CH_QWERTZ = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                "fr_CH", "qwertz");
        FR_CH_QWERTY = AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                "fr_CH", "qwerty");
        ZZ_QWERTY = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                SubtypeLocaleUtils.NO_LANGUAGE, "qwerty");
    }

    private static List<InputMethodSubtype> asList(final InputMethodSubtype ... subtypes) {
        return Arrays.asList(subtypes);
    }

    public void testOneSubtype() {
        mLanguageOnSpacebarHelper.updateEnabledSubtypes(asList(EN_US_QWERTY));
        mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(true /* isSame */);
        assertEquals("one same English (US)", FORMAT_TYPE_NONE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(EN_US_QWERTY));
        assertEquals("one same NoLanguage", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(ZZ_QWERTY));

        mLanguageOnSpacebarHelper.updateEnabledSubtypes(asList(FR_AZERTY));
        mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(false /* isSame */);
        assertEquals("one diff English (US)", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(EN_US_QWERTY));
        assertEquals("one diff NoLanguage", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(ZZ_QWERTY));
    }

    public void testTwoSubtypes() {
        mLanguageOnSpacebarHelper.updateEnabledSubtypes(asList(EN_US_QWERTY, FR_AZERTY));

        mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(true /* isSame */);
        assertEquals("two same English (US)", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(EN_US_QWERTY));
        assertEquals("two same French)", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_AZERTY));
        assertEquals("two same NoLanguage", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(ZZ_QWERTY));

        mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(false /* isSame */);
        assertEquals("two diff English (US)", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(EN_US_QWERTY));
        assertEquals("two diff French", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_AZERTY));
        assertEquals("two diff NoLanguage", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(ZZ_QWERTY));
    }

    public void testSameLanuageSubtypes() {
        mLanguageOnSpacebarHelper.updateEnabledSubtypes(
                asList(EN_US_QWERTY, EN_GB_QWERTY, FR_AZERTY, ZZ_QWERTY));

        mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(true /* isSame */);
        assertEquals("two same English (US)", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(EN_US_QWERTY));
        assertEquals("two same English (UK)", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(EN_GB_QWERTY));
        assertEquals("two same NoLanguage", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(ZZ_QWERTY));

        mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(false /* isSame */);
        assertEquals("two diff English (US)", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(EN_US_QWERTY));
        assertEquals("two diff English (UK)", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(EN_GB_QWERTY));
        assertEquals("two diff NoLanguage", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(ZZ_QWERTY));
    }

    public void testMultiSameLanuageSubtypes() {
        mLanguageOnSpacebarHelper.updateEnabledSubtypes(
                asList(FR_AZERTY, FR_CA_QWERTY, FR_CH_SWISS, FR_CH_QWERTY, FR_CH_QWERTZ));

        mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(true /* isSame */);
        assertEquals("multi same French", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_AZERTY));
        assertEquals("multi same French (CA)", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_CA_QWERTY));
        assertEquals("multi same French (CH)", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_CH_SWISS));
        assertEquals("multi same French (CH) (QWERTY)", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_CH_QWERTY));
        assertEquals("multi same French (CH) (QWERTZ)", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_CH_QWERTZ));

        mLanguageOnSpacebarHelper.updateIsSystemLanguageSameAsInputLanguage(false /* isSame */);
        assertEquals("multi diff French", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_AZERTY));
        assertEquals("multi diff French (CA)", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_CA_QWERTY));
        assertEquals("multi diff French (CH)", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_CH_SWISS));
        assertEquals("multi diff French (CH) (QWERTY)", FORMAT_TYPE_FULL_LOCALE,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_CH_QWERTY));
        assertEquals("multi diff French (CH) (QWERTZ)", FORMAT_TYPE_LANGUAGE_ONLY,
                mLanguageOnSpacebarHelper.getLanguageOnSpacebarFormatType(FR_CH_QWERTZ));
    }
}
