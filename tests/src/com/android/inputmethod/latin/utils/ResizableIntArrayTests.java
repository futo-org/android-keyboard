/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.util.Arrays;

@SmallTest
public class ResizableIntArrayTests extends AndroidTestCase {
    private static final int DEFAULT_CAPACITY = 48;

    public void testNewInstance() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int[] array = src.getPrimitiveArray();
        assertEquals("new instance length", 0, src.getLength());
        assertNotNull("new instance array", array);
        assertEquals("new instance array length", DEFAULT_CAPACITY, array.length);
    }

    public void testAdd() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int[] array = src.getPrimitiveArray();
        int[] array2 = null, array3 = null;
        final int limit = DEFAULT_CAPACITY * 2 + 10;
        for (int i = 0; i < limit; i++) {
            src.add(i);
            assertEquals("length after add " + i, i + 1, src.getLength());
            if (i == DEFAULT_CAPACITY) {
                array2 = src.getPrimitiveArray();
            }
            if (i == DEFAULT_CAPACITY * 2) {
                array3 = src.getPrimitiveArray();
            }
            if (i < DEFAULT_CAPACITY) {
                assertSame("array after add " + i, array, src.getPrimitiveArray());
            } else if (i < DEFAULT_CAPACITY * 2) {
                assertSame("array after add " + i, array2, src.getPrimitiveArray());
            } else if (i < DEFAULT_CAPACITY * 3) {
                assertSame("array after add " + i, array3, src.getPrimitiveArray());
            }
        }
        for (int i = 0; i < limit; i++) {
            assertEquals("value at " + i, i, src.get(i));
        }
    }

    public void testAddAt() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int limit = DEFAULT_CAPACITY * 10, step = DEFAULT_CAPACITY * 2;
        for (int i = 0; i < limit; i += step) {
            src.add(i, i);
            assertEquals("length after add at " + i, i + 1, src.getLength());
        }
        for (int i = 0; i < limit; i += step) {
            assertEquals("value at " + i, i, src.get(i));
        }
    }

    public void testGet() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        try {
            final int value = src.get(0);
            fail("get(0) shouldn't succeed");
        } catch (ArrayIndexOutOfBoundsException e) {
            // success
        }
        try {
            final int value = src.get(DEFAULT_CAPACITY);
            fail("get(DEFAULT_CAPACITY) shouldn't succeed");
        } catch (ArrayIndexOutOfBoundsException e) {
            // success
        }

        final int index = DEFAULT_CAPACITY / 2;
        src.add(index, 100);
        assertEquals("legth after add at " + index, index + 1, src.getLength());
        assertEquals("value after add at " + index, 100, src.get(index));
        assertEquals("value after add at 0", 0, src.get(0));
        try {
            final int value = src.get(src.getLength());
            fail("get(length) shouldn't succeed");
        } catch (ArrayIndexOutOfBoundsException e) {
            // success
        }
    }

    public void testReset() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int[] array = src.getPrimitiveArray();
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            src.add(i);
            assertEquals("length after add " + i, i + 1, src.getLength());
        }

        final int smallerLength = DEFAULT_CAPACITY / 2;
        src.reset(smallerLength);
        final int[] array2 = src.getPrimitiveArray();
        assertEquals("length after reset", 0, src.getLength());
        assertNotSame("array after reset", array, array2);

        int[] array3 = null;
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            src.add(i);
            assertEquals("length after add " + i, i + 1, src.getLength());
            if (i == smallerLength) {
                array3 = src.getPrimitiveArray();
            }
            if (i < smallerLength) {
                assertSame("array after add " + i, array2, src.getPrimitiveArray());
            } else if (i < smallerLength * 2) {
                assertSame("array after add " + i, array3, src.getPrimitiveArray());
            }
        }
    }

    public void testSetLength() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int[] array = src.getPrimitiveArray();
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            src.add(i);
            assertEquals("length after add " + i, i + 1, src.getLength());
        }

        final int largerLength = DEFAULT_CAPACITY * 2;
        src.setLength(largerLength);
        final int[] array2 = src.getPrimitiveArray();
        assertEquals("length after larger setLength", largerLength, src.getLength());
        assertNotSame("array after larger setLength", array, array2);
        assertEquals("array length after larger setLength", largerLength, array2.length);
        for (int i = 0; i < largerLength; i++) {
            final int v = src.get(i);
            if (i < DEFAULT_CAPACITY) {
                assertEquals("value at " + i, i, v);
            } else {
                assertEquals("value at " + i, 0, v);
            }
        }

        final int smallerLength = DEFAULT_CAPACITY / 2;
        src.setLength(smallerLength);
        final int[] array3 = src.getPrimitiveArray();
        assertEquals("length after smaller setLength", smallerLength, src.getLength());
        assertSame("array after smaller setLength", array2, array3);
        assertEquals("array length after smaller setLength", largerLength, array3.length);
        for (int i = 0; i < smallerLength; i++) {
            assertEquals("value at " + i, i, src.get(i));
        }
    }

    public void testSet() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int limit = DEFAULT_CAPACITY * 2 + 10;
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
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            src.add(i);
        }

        final ResizableIntArray dst = new ResizableIntArray(DEFAULT_CAPACITY);
        final int[] array = dst.getPrimitiveArray();
        dst.copy(src);
        assertEquals("length after copy", dst.getLength(), src.getLength());
        assertSame("array after copy", array, dst.getPrimitiveArray());
        assertNotSame("array after copy", dst.getPrimitiveArray(), src.getPrimitiveArray());
        assertIntArrayEquals("values after copy",
                dst.getPrimitiveArray(), 0, src.getPrimitiveArray(), 0, dst.getLength());

        final int smallerLength = DEFAULT_CAPACITY / 2;
        dst.reset(smallerLength);
        final int[] array2 = dst.getPrimitiveArray();
        dst.copy(src);
        final int[] array3 = dst.getPrimitiveArray();
        assertEquals("length after copy to smaller", dst.getLength(), src.getLength());
        assertNotSame("array after copy to smaller", array2, array3);
        assertNotSame("array after copy to smaller", array3, src.getPrimitiveArray());
        assertIntArrayEquals("values after copy to smaller",
                dst.getPrimitiveArray(), 0, src.getPrimitiveArray(), 0, dst.getLength());
    }

    public void testAppend() {
        final int srcLen = DEFAULT_CAPACITY;
        final ResizableIntArray src = new ResizableIntArray(srcLen);
        for (int i = 0; i < srcLen; i++) {
            src.add(i);
        }
        final ResizableIntArray dst = new ResizableIntArray(DEFAULT_CAPACITY * 2);
        final int[] array = dst.getPrimitiveArray();
        final int dstLen = DEFAULT_CAPACITY / 2;
        for (int i = 0; i < dstLen; i++) {
            final int value = -i - 1;
            dst.add(value);
        }
        final ResizableIntArray dstCopy = new ResizableIntArray(dst.getLength());
        dstCopy.copy(dst);

        dst.append(src, 0, 0);
        assertEquals("length after append zero", dstLen, dst.getLength());
        assertSame("array after append zero", array, dst.getPrimitiveArray());
        assertIntArrayEquals("values after append zero",
                dstCopy.getPrimitiveArray(), 0, dst.getPrimitiveArray(), 0, dstLen);

        dst.append(src, 0, srcLen);
        assertEquals("length after append", dstLen + srcLen, dst.getLength());
        assertSame("array after append", array, dst.getPrimitiveArray());
        assertTrue("primitive length after append",
                dst.getPrimitiveArray().length >= dstLen + srcLen);
        assertIntArrayEquals("original values after append",
                dstCopy.getPrimitiveArray(), 0, dst.getPrimitiveArray(), 0, dstLen);
        assertIntArrayEquals("appended values after append",
                src.getPrimitiveArray(), 0, dst.getPrimitiveArray(), dstLen, srcLen);

        dst.append(src, 0, srcLen);
        assertEquals("length after 2nd append", dstLen + srcLen * 2, dst.getLength());
        assertNotSame("array after 2nd append", array, dst.getPrimitiveArray());
        assertTrue("primitive length after 2nd append",
                dst.getPrimitiveArray().length >= dstLen + srcLen * 2);
        assertIntArrayEquals("original values after 2nd append",
                dstCopy.getPrimitiveArray(), 0, dst.getPrimitiveArray(), 0, dstLen);
        assertIntArrayEquals("appended values after 2nd append",
                src.getPrimitiveArray(), 0, dst.getPrimitiveArray(), dstLen, srcLen);
        assertIntArrayEquals("appended values after 2nd append",
                src.getPrimitiveArray(), 0, dst.getPrimitiveArray(), dstLen + srcLen, srcLen);
    }

    public void testFill() {
        final int srcLen = DEFAULT_CAPACITY;
        final ResizableIntArray src = new ResizableIntArray(srcLen);
        for (int i = 0; i < srcLen; i++) {
            src.add(i);
        }
        final int[] array = src.getPrimitiveArray();

        final int startPos = srcLen / 3;
        final int length = srcLen / 3;
        final int endPos = startPos + length;
        assertTrue(startPos >= 1);
        final int value = 123;
        try {
            src.fill(value, -1, length);
            fail("fill from -1 shouldn't succeed");
        } catch (IllegalArgumentException e) {
            // success
        }
        try {
            src.fill(value, startPos, -1);
            fail("fill negative length shouldn't succeed");
        } catch (IllegalArgumentException e) {
            // success
        }

        src.fill(value, startPos, length);
        assertEquals("length after fill", srcLen, src.getLength());
        assertSame("array after fill", array, src.getPrimitiveArray());
        for (int i = 0; i < srcLen; i++) {
            final int v = src.get(i);
            if (i >= startPos && i < endPos) {
                assertEquals("new values after fill at " + i, value, v);
            } else {
                assertEquals("unmodified values after fill at " + i, i, v);
            }
        }

        final int length2 = srcLen * 2 - startPos;
        final int largeEnd = startPos + length2;
        assertTrue(largeEnd > srcLen);
        final int value2 = 456;
        src.fill(value2, startPos, length2);
        assertEquals("length after large fill", largeEnd, src.getLength());
        assertNotSame("array after large fill", array, src.getPrimitiveArray());
        for (int i = 0; i < largeEnd; i++) {
            final int v = src.get(i);
            if (i >= startPos && i < largeEnd) {
                assertEquals("new values after large fill at " + i, value2, v);
            } else {
                assertEquals("unmodified values after large fill at " + i, i, v);
            }
        }

        final int startPos2 = largeEnd + length2;
        final int endPos2 = startPos2 + length2;
        final int value3 = 789;
        src.fill(value3, startPos2, length2);
        assertEquals("length after disjoint fill", endPos2, src.getLength());
        for (int i = 0; i < endPos2; i++) {
            final int v = src.get(i);
            if (i >= startPos2 && i < endPos2) {
                assertEquals("new values after disjoint fill at " + i, value3, v);
            } else if (i >= startPos && i < largeEnd) {
                assertEquals("unmodified values after disjoint fill at " + i, value2, v);
            } else if (i < startPos) {
                assertEquals("unmodified values after disjoint fill at " + i, i, v);
            } else {
                assertEquals("gap values after disjoint fill at " + i, 0, v);
            }
        }
    }

    private static void assertIntArrayEquals(final String message, final int[] expecteds,
            final int expectedPos, final int[] actuals, final int actualPos, final int length) {
        if (expecteds == actuals) {
            return;
        }
        if (expecteds == null || actuals == null) {
            assertEquals(message, Arrays.toString(expecteds), Arrays.toString(actuals));
            return;
        }
        if (expecteds.length < expectedPos + length || actuals.length < actualPos + length) {
            fail(message + ": insufficient length: expecteds=" + Arrays.toString(expecteds)
                    + " actuals=" + Arrays.toString(actuals));
            return;
        }
        for (int i = 0; i < length; i++) {
            assertEquals(message + " [" + i + "]",
                    expecteds[i + expectedPos], actuals[i + actualPos]);
        }
    }

    public void testShift() {
        final ResizableIntArray src = new ResizableIntArray(DEFAULT_CAPACITY);
        final int limit = DEFAULT_CAPACITY * 10;
        final int shiftAmount = 20;
        for (int i = 0; i < limit; ++i) {
            src.add(i, i);
            assertEquals("length after add at " + i, i + 1, src.getLength());
        }
        src.shift(shiftAmount);
        for (int i = 0; i < limit - shiftAmount; ++i) {
            assertEquals("value at " + i, i + shiftAmount, src.get(i));
        }
    }
}
