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

import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;

import java.util.Locale;

@SmallTest
public class DictionaryInfoUtilsTests extends AndroidTestCase {
    public void testLooksValidForDictionaryInsertion() {
        final RunInLocale<SpacingAndPunctuations> job = new RunInLocale<SpacingAndPunctuations>() {
            @Override
            protected SpacingAndPunctuations job(final Resources res) {
                return new SpacingAndPunctuations(res);
            }
        };
        final Resources res = getContext().getResources();
        final SpacingAndPunctuations sp = job.runInLocale(res, Locale.ENGLISH);
        assertTrue(DictionaryInfoUtils.looksValidForDictionaryInsertion("aochaueo", sp));
        assertFalse(DictionaryInfoUtils.looksValidForDictionaryInsertion("", sp));
        assertTrue(DictionaryInfoUtils.looksValidForDictionaryInsertion("ao-ch'aueo", sp));
        assertFalse(DictionaryInfoUtils.looksValidForDictionaryInsertion("2908743256", sp));
        assertTrue(DictionaryInfoUtils.looksValidForDictionaryInsertion("31aochaueo", sp));
        assertFalse(DictionaryInfoUtils.looksValidForDictionaryInsertion("akeo  raeoch oerch .",
                sp));
        assertFalse(DictionaryInfoUtils.looksValidForDictionaryInsertion("!!!", sp));
    }

    public void testGetMainDictId() {
        assertEquals("main:en",
                DictionaryInfoUtils.getMainDictId(LocaleUtils.constructLocaleFromString("en")));
        assertEquals("main:en_us",
                DictionaryInfoUtils.getMainDictId(LocaleUtils.constructLocaleFromString("en_US")));
        assertEquals("main:en_gb",
                DictionaryInfoUtils.getMainDictId(LocaleUtils.constructLocaleFromString("en_GB")));

        assertEquals("main:es",
                DictionaryInfoUtils.getMainDictId(LocaleUtils.constructLocaleFromString("es")));
        assertEquals("main:es_us",
                DictionaryInfoUtils.getMainDictId(LocaleUtils.constructLocaleFromString("es_US")));

        assertEquals("main:en_us_posix", DictionaryInfoUtils.getMainDictId(
                        LocaleUtils.constructLocaleFromString("en_US_POSIX")));
    }
}
