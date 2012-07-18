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

public class InputPointersTests extends AndroidTestCase {
    private static final int DEFAULT_CAPACITY = 48;

    public void testNewInstance() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        assertEquals("newInstance size", 0, src.getPointerSize());
        assertNotNull("new instance xCoordinates", src.getXCoordinates());
        assertNotNull("new instance yCoordinates", src.getYCoordinates());
        assertNotNull("new instance pointerIds", src.getPointerIds());
        assertNotNull("new instance times", src.getTimes());
    }

    public void testReset() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int[] xCoordinates = src.getXCoordinates();
        final int[] yCoordinates = src.getXCoordinates();
        final int[] pointerIds = src.getXCoordinates();
        final int[] times = src.getXCoordinates();

        src.reset();
        assertEquals("after reset size", 0, src.getPointerSize());
        assertNotSame("after reset xCoordinates", xCoordinates, src.getXCoordinates());
        assertNotSame("after reset yCoordinates", yCoordinates, src.getYCoordinates());
        assertNotSame("after reset pointerIds", pointerIds, src.getPointerIds());
        assertNotSame("after reset times", times, src.getTimes());
    }

    public void testAdd() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int limit = src.getXCoordinates().length * 2 + 10;
        for (int i = 0; i < limit; i++) {
            src.addPointer(i, i * 2, i * 3, i * 4);
            assertEquals("after add " + i, i + 1, src.getPointerSize());
        }
        for (int i = 0; i < limit; i++) {
            assertEquals("xCoordinates at " + i, i, src.getXCoordinates()[i]);
            assertEquals("yCoordinates at " + i, i * 2, src.getYCoordinates()[i]);
            assertEquals("pointerIds at " + i, i * 3, src.getPointerIds()[i]);
            assertEquals("times at " + i, i * 4, src.getTimes()[i]);
        }
    }

    public void testAddAt() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int limit = 1000, step = 100;
        for (int i = 0; i < limit; i += step) {
            src.addPointer(i, i, i * 2, i * 3, i * 4);
            assertEquals("after add at " + i, i + 1, src.getPointerSize());
        }
        for (int i = 0; i < limit; i += step) {
            assertEquals("xCoordinates at " + i, i, src.getXCoordinates()[i]);
            assertEquals("yCoordinates at " + i, i * 2, src.getYCoordinates()[i]);
            assertEquals("pointerIds at " + i, i * 3, src.getPointerIds()[i]);
            assertEquals("times at " + i, i * 4, src.getTimes()[i]);
        }
    }

    public void testSet() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int limit = src.getXCoordinates().length * 2 + 10;
        for (int i = 0; i < limit; i++) {
            src.addPointer(i, i * 2, i * 3, i * 4);
        }
        final InputPointers dst = new InputPointers(DEFAULT_CAPACITY);
        dst.set(src);
        assertEquals("after set size", dst.getPointerSize(), src.getPointerSize());
        assertSame("after set xCoordinates", dst.getXCoordinates(), src.getXCoordinates());
        assertSame("after set yCoordinates", dst.getYCoordinates(), src.getYCoordinates());
        assertSame("after set pointerIds", dst.getPointerIds(), src.getPointerIds());
        assertSame("after set times", dst.getTimes(), src.getTimes());
    }

    public void testCopy() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int limit = 100;
        for (int i = 0; i < limit; i++) {
            src.addPointer(i, i * 2, i * 3, i * 4);
        }
        final InputPointers dst = new InputPointers(DEFAULT_CAPACITY);
        dst.copy(src);
        assertEquals("after copy size", dst.getPointerSize(), src.getPointerSize());
        assertNotSame("after copy xCoordinates", dst.getXCoordinates(), src.getXCoordinates());
        assertNotSame("after copy yCoordinates", dst.getYCoordinates(), src.getYCoordinates());
        assertNotSame("after copy pointerIds", dst.getPointerIds(), src.getPointerIds());
        assertNotSame("after copy times", dst.getTimes(), src.getTimes());
        final int size = dst.getPointerSize();
        assertArrayEquals("after copy xCoordinates values",
                dst.getXCoordinates(), 0, src.getXCoordinates(), 0, size);
        assertArrayEquals("after copy yCoordinates values",
                dst.getYCoordinates(), 0, src.getYCoordinates(), 0, size);
        assertArrayEquals("after copy pointerIds values",
                dst.getPointerIds(), 0, src.getPointerIds(), 0, size);
        assertArrayEquals("after copy times values",
                dst.getTimes(), 0, src.getTimes(), 0, size);
    }

    public void testAppend() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int srcLen = 100;
        for (int i = 0; i < srcLen; i++) {
            src.addPointer(i, i * 2, i * 3, i * 4);
        }
        final int dstLen = 50;
        final InputPointers dst = new InputPointers(DEFAULT_CAPACITY);
        for (int i = 0; i < dstLen; i++) {
            final int value = -i - 1;
            dst.addPointer(value * 4, value * 3, value * 2, value);
        }
        final InputPointers dstCopy = new InputPointers(DEFAULT_CAPACITY);
        dstCopy.copy(dst);

        dst.append(src, 0, 0);
        assertEquals("after append zero size", dstLen, dst.getPointerSize());
        assertArrayEquals("after append zero xCoordinates", dstCopy.getXCoordinates(), 0,
                dst.getXCoordinates(), 0, dstLen);
        assertArrayEquals("after append zero yCoordinates", dstCopy.getYCoordinates(), 0,
                dst.getYCoordinates(), 0, dstLen);
        assertArrayEquals("after append zero pointerIds", dstCopy.getPointerIds(), 0,
                dst.getPointerIds(), 0, dstLen);
        assertArrayEquals("after append zero times", dstCopy.getTimes(), 0,
                dst.getTimes(), 0, dstLen);

        dst.append(src, 0, srcLen);
        assertEquals("after append size", dstLen + srcLen, dst.getPointerSize());
        assertTrue("after append size primitive length",
                dst.getPointerIds().length >= dstLen + srcLen);
        assertArrayEquals("after append xCoordinates", dstCopy.getXCoordinates(), 0,
                dst.getXCoordinates(), 0, dstLen);
        assertArrayEquals("after append yCoordinates", dstCopy.getYCoordinates(), 0,
                dst.getYCoordinates(), 0, dstLen);
        assertArrayEquals("after append pointerIds", dstCopy.getPointerIds(), 0,
                dst.getPointerIds(), 0, dstLen);
        assertArrayEquals("after append times", dstCopy.getTimes(), 0,
                dst.getTimes(), 0, dstLen);
        assertArrayEquals("after append xCoordinates", dst.getXCoordinates(), dstLen,
                src.getXCoordinates(), 0, srcLen);
        assertArrayEquals("after append yCoordinates", dst.getYCoordinates(), dstLen,
                src.getYCoordinates(), 0, srcLen);
        assertArrayEquals("after append pointerIds", dst.getPointerIds(), dstLen,
                src.getPointerIds(), 0, srcLen);
        assertArrayEquals("after append times", dst.getTimes(), dstLen,
                src.getTimes(), 0, srcLen);
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
