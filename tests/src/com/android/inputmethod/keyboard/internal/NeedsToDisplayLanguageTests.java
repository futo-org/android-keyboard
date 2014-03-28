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

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Locale;

@SmallTest
public class NeedsToDisplayLanguageTests extends AndroidTestCase {
    private final NeedsToDisplayLanguage mNeedsToDisplayLanguage = new NeedsToDisplayLanguage();

    private RichInputMethodManager mRichImm;

    InputMethodSubtype EN_US;
    InputMethodSubtype FR;
    InputMethodSubtype ZZ;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getContext();
        RichInputMethodManager.init(context);
        mRichImm = RichInputMethodManager.getInstance();
        SubtypeLocaleUtils.init(context);

        EN_US = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.US.toString(), "qwerty");
        FR = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                Locale.FRENCH.toString(), "azerty");
        ZZ = mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                SubtypeLocaleUtils.NO_LANGUAGE, "qwerty");
    }

    public void testOneSubtype() {
        mNeedsToDisplayLanguage.updateEnabledSubtypeCount(1);

        mNeedsToDisplayLanguage.updateIsSystemLanguageSameAsInputLanguage(true /* isSame */);
        assertFalse("one same English (US)", mNeedsToDisplayLanguage.needsToDisplayLanguage(EN_US));
        assertTrue("one same NoLanguage", mNeedsToDisplayLanguage.needsToDisplayLanguage(ZZ));

        mNeedsToDisplayLanguage.updateIsSystemLanguageSameAsInputLanguage(false /* isSame */);
        assertTrue("one diff English (US)", mNeedsToDisplayLanguage.needsToDisplayLanguage(EN_US));
        assertTrue("one diff NoLanguage", mNeedsToDisplayLanguage.needsToDisplayLanguage(ZZ));
    }

    public void testTwoSubtype() {
        mNeedsToDisplayLanguage.updateEnabledSubtypeCount(2);

        mNeedsToDisplayLanguage.updateIsSystemLanguageSameAsInputLanguage(true /* isSame */);
        assertTrue("two same English (US)", mNeedsToDisplayLanguage.needsToDisplayLanguage(EN_US));
        assertTrue("two same French", mNeedsToDisplayLanguage.needsToDisplayLanguage(FR));
        assertTrue("two same NoLanguage", mNeedsToDisplayLanguage.needsToDisplayLanguage(ZZ));

        mNeedsToDisplayLanguage.updateIsSystemLanguageSameAsInputLanguage(false /* isSame */);
        assertTrue("two diff English (US)", mNeedsToDisplayLanguage.needsToDisplayLanguage(EN_US));
        assertTrue("two diff French", mNeedsToDisplayLanguage.needsToDisplayLanguage(ZZ));
        assertTrue("two diff NoLanguage", mNeedsToDisplayLanguage.needsToDisplayLanguage(FR));
    }
}
