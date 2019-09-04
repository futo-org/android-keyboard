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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.common.CollectionUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link CollectionUtils}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class CollectionUtilsTests {
    /**
     * Tests that {@link CollectionUtils#arrayAsList(Object[],int,int)} fails as expected
     * with some invalid inputs.
     */
    @Test
    public void testArrayAsListFailure() {
        final String[] array = { "0", "1" };
        // Negative start
        try {
            CollectionUtils.arrayAsList(array, -1, 1);
            fail("Failed to catch start < 0");
        } catch (final IllegalArgumentException e) {
            assertEquals("Invalid start: -1 end: 1 with array.length: 2", e.getMessage());
        }
        // start > end
        try {
            CollectionUtils.arrayAsList(array, 1, -1);
            fail("Failed to catch start > end");
        } catch (final IllegalArgumentException e) {
            assertEquals("Invalid start: 1 end: -1 with array.length: 2", e.getMessage());
        }
        // end > array.length
        try {
            CollectionUtils.arrayAsList(array, 1, 3);
            fail("Failed to catch end > array.length");
        } catch (final IllegalArgumentException e) {
            assertEquals("Invalid start: 1 end: 3 with array.length: 2", e.getMessage());
        }
    }

    /**
     * Tests that {@link CollectionUtils#arrayAsList(Object[],int,int)} gives the expected
     * results for a few valid inputs.
     */
    @Test
    public void testArrayAsList() {
        final ArrayList<String> empty = new ArrayList<>();
        assertEquals(empty, CollectionUtils.arrayAsList(new String[] {}, 0, 0));
        final String[] array = { "0", "1", "2", "3", "4" };
        assertEquals(empty, CollectionUtils.arrayAsList(array, 0, 0));
        assertEquals(empty, CollectionUtils.arrayAsList(array, 1, 1));
        assertEquals(empty, CollectionUtils.arrayAsList(array, array.length, array.length));
        final ArrayList<String> expected123 = new ArrayList<>(Arrays.asList("1", "2", "3"));
        assertEquals(expected123, CollectionUtils.arrayAsList(array, 1, 4));
    }

    /**
     * Tests that {@link CollectionUtils#isNullOrEmpty(java.util.Collection)} gives the expected
     * results for a few cases.
     */
    @Test
    public void testIsNullOrEmpty() {
        assertTrue(CollectionUtils.isNullOrEmpty((List<String>) null));
        assertTrue(CollectionUtils.isNullOrEmpty((Map<String, String>) null));
        assertTrue(CollectionUtils.isNullOrEmpty(new ArrayList<String>()));
        assertTrue(CollectionUtils.isNullOrEmpty(new HashMap<String, String>()));
        assertFalse(CollectionUtils.isNullOrEmpty(Collections.singletonList("Not empty")));
        assertFalse(CollectionUtils.isNullOrEmpty(Collections.singletonMap("Not", "empty")));
    }
}
