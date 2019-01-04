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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class DictionaryFacilitatorLruCacheTests {

    @Test
    public void testGetFacilitator() {
        final DictionaryFacilitatorLruCache cache =
                new DictionaryFacilitatorLruCache(InstrumentationRegistry.getTargetContext(), "");

        final DictionaryFacilitator dictionaryFacilitatorEnUs = cache.get(Locale.US);
        assertNotNull(dictionaryFacilitatorEnUs);
        assertTrue(dictionaryFacilitatorEnUs.isForLocale(Locale.US));

        final DictionaryFacilitator dictionaryFacilitatorFr = cache.get(Locale.FRENCH);
        assertNotNull(dictionaryFacilitatorEnUs);
        assertTrue(dictionaryFacilitatorFr.isForLocale(Locale.FRENCH));

        final DictionaryFacilitator dictionaryFacilitatorDe = cache.get(Locale.GERMANY);
        assertNotNull(dictionaryFacilitatorDe);
        assertTrue(dictionaryFacilitatorDe.isForLocale(Locale.GERMANY));
    }
}
