/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.test.AndroidTestCase;

public class ResizableIntArrayTests extends AndroidTestCase {
    private static final int DEFAULT_CAPACITY = 48;

    public void testNewInstance() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        assertEquals("new instance length", 0, src.getLength());
        assertNotNull("new instance array", src.getPrimitiveArray());
    }

    public void testReset() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int[] array = src.getPrimitiveArray();

        src.reset(DEFAULT_CAPACITY);
        assertEquals("length after reset", 0, src.getLength());
        assertNotSame("array after reset", array, src.getPrimitiveArray());
    }

    public void testAdd() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int limit = src.getPrimitiveArray().length * 2 + 10;
        for (int i = 0; i < limit; i++) {
            src.add(i);
            assertEquals("length after add " + i, i + 1, src.getLength());
        }
        for (int i = 0; i < limit; i++) {
            assertEquals("value at " + i, i, src.getPrimitiveArray()[i]);
        }
    }

    public void testAddAt() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int limit = 1000, step = 100;
        for (int i = 0; i < limit; i += step) {
            src.add(i, i);
            assertEquals("length after add at " + i, i + 1, src.getLength());
        }
        for (int i = 0; i < limit; i += step) {
            assertEquals("value at " + i, i, src.getPrimitiveArray()[i]);
        }
    }

    public void testSet() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int limit = src.getPrimitiveArray().length * 2 + 10;
        for (int i = 0; i < limit; i++) {
            src.add(i);
        }
        final ResizableIntArray dst = new ResizableIntArray(DEFAULT_CAPACITY);
        dst.set(src);
        assertEquals("length after set", dst.getLength(), src.getLength());
        assertSame("array after set", dst.getPrimitiveArray(), src.getPrimitiveArray());
    }

    public void testCopy() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int limit = 100;
        for (int i = 0; i < limit; i++) {
            src.add(i);
        }
        final ResizableIntArray dst = new ResizableIntArray(DEFAULT_CAPACITY);
        dst.copy(src);
        assertEquals("length after copy", dst.getLength(), src.getLength());
        assertNotSame("array after copy", dst.getPrimitiveArray(), src.getPrimitiveArray());
        final int length = dst.getLength();
        assertArrayEquals("values after copy",
                dst.getPrimitiveArray(), 0, src.getPrimitiveArray(), 0, length);
    }

    public void testAppend() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int srcLen = 100;
        for (int i = 0; i < srcLen; i++) {
            src.add(i);
        }
        final int dstLen = 50;
        final ResizableIntArray dst = new ResizableIntArray(DEFAULT_CAPACITY);
        for (int i = 0; i < dstLen; i++) {
            final int value = -i - 1;
            dst.add(value);
        }
        final ResizableIntArray dstCopy = new ResizableIntArray(dst.getLength());
        dstCopy.copy(dst);

        dst.append(src, 0, 0);
        assertEquals("length after append zero", dstLen, dst.getLength());
        assertArrayEquals("values after append zero",
                dstCopy.getPrimitiveArray(), 0, dst.getPrimitiveArray(), 0, dstLen);

        dst.append(src, 0, srcLen);
        assertEquals("length after append", dstLen + srcLen, dst.getLength());
        assertTrue("primitive length after append",
                dst.getPrimitiveArray().length >= dstLen + srcLen);
        assertArrayEquals("original values after append",
                dstCopy.getPrimitiveArray(), 0, dst.getPrimitiveArray(), 0, dstLen);
        assertArrayEquals("appended values after append",
                src.getPrimitiveArray(), 0, dst.getPrimitiveArray(), dstLen, srcLen);
    }

    private static void assertArrayEquals(String message, int[] expecteds, int expectedPos,
            int[] actuals, int actualPos, int length) {
        if (expecteds == null && actuals == null) {
            return;
        }
        if (expecteds == null || actuals == null) {
            fail(message + ": expecteds=" + expecteds + " actuals=" + actuals);
        }
        for (int i = 0; i < length; i++) {
            assertEquals(message + ": element at " + i,
                    expecteds[i + expectedPos], actuals[i + actualPos]);
        }
    }
}
