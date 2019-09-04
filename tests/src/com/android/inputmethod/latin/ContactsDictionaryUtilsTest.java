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
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Tests for {@link ContactsDictionaryUtils}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContactsDictionaryUtilsTest {

    @Test
    public void testGetWordEndPosition() {
        final String testString1 = "Larry Page";
        assertEquals(5, ContactsDictionaryUtils.getWordEndPosition(
                testString1, testString1.length(), 0 /* startIndex */));

        assertEquals(10, ContactsDictionaryUtils.getWordEndPosition(
                testString1, testString1.length(), 6 /* startIndex */));

        final String testString2 = "Larry-Page";
        assertEquals(10, ContactsDictionaryUtils.getWordEndPosition(
                testString2, testString1.length(), 0 /* startIndex */));

        final String testString3 = "Larry'Page";
        assertEquals(10, ContactsDictionaryUtils.getWordEndPosition(
                testString3, testString1.length(), 0 /* startIndex */));
    }

    @Test
    public void testUseFirstLastBigramsForLocale() {
        assertTrue(ContactsDictionaryUtils.useFirstLastBigramsForLocale(Locale.ENGLISH));
        assertTrue(ContactsDictionaryUtils.useFirstLastBigramsForLocale(Locale.US));
        assertTrue(ContactsDictionaryUtils.useFirstLastBigramsForLocale(Locale.UK));
        assertFalse(ContactsDictionaryUtils.useFirstLastBigramsForLocale(Locale.CHINA));
        assertFalse(ContactsDictionaryUtils.useFirstLastBigramsForLocale(Locale.GERMAN));
    }
}
