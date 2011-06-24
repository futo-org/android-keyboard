/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.compat;

import android.test.AndroidTestCase;

public class ArraysCompatUtilsTests extends AndroidTestCase {
    // See {@link tests.api.java.util.ArraysTest}.
    private static final int ARRAY_SIZE = 100;
    private final int[] mIntArray = new int[ARRAY_SIZE];

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        for (int counter = 0; counter < ARRAY_SIZE; counter++) {
            mIntArray[counter] = counter;
        }
    }

    public void testEmptyArray() {
        final int index = ArraysCompatUtils.binarySearch(mIntArray, 0, 0, 0);
        assertEquals("empty", ~0, index);
        final int compat = ArraysCompatUtils.compatBinarySearch(mIntArray, 0, 0, 0);
        assertEquals("empty compat", ~0, compat);
    }

    public void testEmptyRangeArray() {
        final int mid = ARRAY_SIZE / 3;
        final int index = ArraysCompatUtils.binarySearch(mIntArray, mid, mid, 1);
        assertEquals("empty", ~mid, index);
        final int compat = ArraysCompatUtils.compatBinarySearch(mIntArray, mid, mid, 1);
        assertEquals("empty compat", ~mid, compat);
    }

    public void testFind() {
        for (int counter = 0; counter < ARRAY_SIZE; counter++) {
            final int index = ArraysCompatUtils.binarySearch(mIntArray, 0, ARRAY_SIZE, counter);
            assertEquals("found", counter, index);
        }
        for (int counter = 0; counter < ARRAY_SIZE; counter++) {
            final int compat = ArraysCompatUtils.compatBinarySearch(
                    mIntArray, 0, ARRAY_SIZE, counter);
            assertEquals("found compat", counter, compat);
        }
    }

    public void testFindNegative() {
        final int offset = ARRAY_SIZE / 2;
        for (int counter = 0; counter < ARRAY_SIZE; counter++) {
            mIntArray[counter] -= offset;
        }
        for (int counter = 0; counter < ARRAY_SIZE; counter++) {
            final int index = ArraysCompatUtils.binarySearch(
                    mIntArray, 0, ARRAY_SIZE, counter - offset);
            assertEquals("found", counter, index);
        }
        for (int counter = 0; counter < ARRAY_SIZE; counter++) {
            final int compat = ArraysCompatUtils.compatBinarySearch(
                    mIntArray, 0, ARRAY_SIZE, counter - offset);
            assertEquals("found compat", counter, compat);
        }
    }

    public void testNotFountAtTop() {
        final int index = ArraysCompatUtils.binarySearch(mIntArray, 0, ARRAY_SIZE, -1);
        assertEquals("not found top", ~0, index);
        final int compat = ArraysCompatUtils.compatBinarySearch(
                    mIntArray, 0, ARRAY_SIZE, -1);
        assertEquals("not found top compat", ~0, compat);
    }

    public void testNotFountAtEnd() {
        final int index = ArraysCompatUtils.binarySearch(mIntArray, 0, ARRAY_SIZE, ARRAY_SIZE);
        assertEquals("not found end", ~ARRAY_SIZE, index);
        final int compat = ArraysCompatUtils.compatBinarySearch(
                    mIntArray, 0, ARRAY_SIZE, ARRAY_SIZE);
        assertEquals("not found end compat", ~ARRAY_SIZE, compat);
    }

    public void testNotFountAtMid() {
        final int mid = ARRAY_SIZE / 3;
        mIntArray[mid] = mIntArray[mid + 1];
        final int index = ArraysCompatUtils.binarySearch(mIntArray, 0, ARRAY_SIZE, mid);
        assertEquals("not found mid", ~mid, index);
        final int compat = ArraysCompatUtils.compatBinarySearch(
                    mIntArray, 0, ARRAY_SIZE, mid);
        assertEquals("not found mid compat", ~mid, compat);
    }
}
