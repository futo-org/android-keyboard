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

import java.util.Locale;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

@LargeTest
public class DictionaryFacilitatorLruCacheTests extends AndroidTestCase {
    static final int MAX_CACHE_SIZE = 2;
    static final int MAX_CACHE_SIZE_LARGE = 5;

    public void testCacheSize() {
        final DictionaryFacilitatorLruCache cache =
                new DictionaryFacilitatorLruCache(getContext(), MAX_CACHE_SIZE, "");

        assertEquals(0, cache.getCachedLocalesForTesting().size());
        assertNotNull(cache.get(Locale.US));
        assertEquals(1, cache.getCachedLocalesForTesting().size());
        assertNotNull(cache.get(Locale.UK));
        assertEquals(2, cache.getCachedLocalesForTesting().size());
        assertNotNull(cache.get(Locale.FRENCH));
        assertEquals(2, cache.getCachedLocalesForTesting().size());
        cache.evictAll();
        assertEquals(0, cache.getCachedLocalesForTesting().size());
    }

    public void testGetFacilitator() {
        testGetFacilitator(new DictionaryFacilitatorLruCache(getContext(), MAX_CACHE_SIZE, ""));
        testGetFacilitator(new DictionaryFacilitatorLruCache(
                getContext(), MAX_CACHE_SIZE_LARGE, ""));
    }

    private void testGetFacilitator(final DictionaryFacilitatorLruCache cache) {
        final DictionaryFacilitator dictionaryFacilitatorEnUs = cache.get(Locale.US);
        assertNotNull(dictionaryFacilitatorEnUs);
        assertEquals(Locale.US, dictionaryFacilitatorEnUs.getLocale());

        final DictionaryFacilitator dictionaryFacilitatorFr = cache.get(Locale.FRENCH);
        assertNotNull(dictionaryFacilitatorEnUs);
        assertEquals(Locale.FRENCH, dictionaryFacilitatorFr.getLocale());

        final DictionaryFacilitator dictionaryFacilitatorDe = cache.get(Locale.GERMANY);
        assertNotNull(dictionaryFacilitatorDe);
        assertEquals(Locale.GERMANY, dictionaryFacilitatorDe.getLocale());
    }

    public void testSetUseContactsDictionary() {
        testSetUseContactsDictionary(new DictionaryFacilitatorLruCache(
                getContext(), MAX_CACHE_SIZE, ""));
        testSetUseContactsDictionary(new DictionaryFacilitatorLruCache(
                getContext(), MAX_CACHE_SIZE_LARGE, ""));
    }

    private void testSetUseContactsDictionary(final DictionaryFacilitatorLruCache cache) {
        assertNull(cache.get(Locale.US).getSubDictForTesting(Dictionary.TYPE_CONTACTS));
        cache.setUseContactsDictionary(true /* useContactsDictionary */);
        assertNotNull(cache.get(Locale.US).getSubDictForTesting(Dictionary.TYPE_CONTACTS));
        assertNotNull(cache.get(Locale.FRENCH).getSubDictForTesting(Dictionary.TYPE_CONTACTS));
        assertNotNull(cache.get(Locale.GERMANY).getSubDictForTesting(Dictionary.TYPE_CONTACTS));
        cache.setUseContactsDictionary(false /* useContactsDictionary */);
        assertNull(cache.get(Locale.GERMANY).getSubDictForTesting(Dictionary.TYPE_CONTACTS));
        assertNull(cache.get(Locale.US).getSubDictForTesting(Dictionary.TYPE_CONTACTS));
    }
}
