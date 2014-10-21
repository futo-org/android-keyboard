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

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Tests for {@link CollectionUtils}.
 */
@SmallTest
public class CollectionUtilsTests extends AndroidTestCase {
    /**
     * Tests that {@link CollectionUtils#arrayAsList(E[],int,int)} gives the expected
     * results for a few valid inputs.
     */
    public void testArrayAsList() {
        final String[] array = { "0", "1", "2", "3", "4" };
        final ArrayList<String> empty = new ArrayList<>();
        assertEquals(empty, CollectionUtils.arrayAsList(array, 0, 0));
        assertEquals(empty, CollectionUtils.arrayAsList(array, 1, 1));
        final ArrayList<String> expected123 = new ArrayList<>(Arrays.asList("1", "2", "3"));
        assertEquals(expected123, CollectionUtils.arrayAsList(array, 1, 4));
    }

    /**
     * Tests that {@link CollectionUtils#isEmpty(java.util.Collection)} gives the expected
     * results for a few cases.
     */
    public void testIsNullOrEmpty() {
        assertTrue(CollectionUtils.isNullOrEmpty(null));
        assertTrue(CollectionUtils.isNullOrEmpty(new ArrayList<>()));
        assertTrue(CollectionUtils.isNullOrEmpty(Collections.EMPTY_SET));
        assertFalse(CollectionUtils.isNullOrEmpty(Collections.singleton("Not empty")));
    }

}
