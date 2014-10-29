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

package com.android.inputmethod.latin.common;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Arrays;

@SmallTest
public class InputPointersTests extends AndroidTestCase {
    private static final int DEFAULT_CAPACITY = 48;

    public void testNewInstance() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        assertEquals("new instance size", 0, src.getPointerSize());
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
        assertEquals("size after reset", 0, src.getPointerSize());
        assertNotSame("xCoordinates after reset", xCoordinates, src.getXCoordinates());
        assertNotSame("yCoordinates after reset", yCoordinates, src.getYCoordinates());
        assertNotSame("pointerIds after reset", pointerIds, src.getPointerIds());
        assertNotSame("times after reset", times, src.getTimes());
    }

    public void testAdd() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int limit = src.getXCoordinates().length * 2 + 10;
        for (int i = 0; i < limit; i++) {
            final int x = i;
            final int y = i * 2;
            final int pointerId = i * 3;
            final int time = i * 4;
            src.addPointer(x, y, pointerId, time);
            assertEquals("size after add " + i, i + 1, src.getPointerSize());
        }
        for (int i = 0; i < limit; i++) {
            final int x = i;
            final int y = i * 2;
            final int pointerId = i * 3;
            final int time = i * 4;
            assertEquals("xCoordinates at " + i, x, src.getXCoordinates()[i]);
            assertEquals("yCoordinates at " + i, y, src.getYCoordinates()[i]);
            assertEquals("pointerIds at " + i, pointerId, src.getPointerIds()[i]);
            assertEquals("times at " + i, time, src.getTimes()[i]);
        }
    }

    public void testAddAt() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int limit = 1000, step = 100;
        for (int i = 0; i < limit; i += step) {
            final int x = i;
            final int y = i * 2;
            final int pointerId = i * 3;
            final int time = i * 4;
            src.addPointerAt(i, x, y, pointerId, time);
            assertEquals("size after add at " + i, i + 1, src.getPointerSize());
        }
        for (int i = 0; i < limit; i += step) {
            final int x = i;
            final int y = i * 2;
            final int pointerId = i * 3;
            final int time = i * 4;
            assertEquals("xCoordinates at " + i, x, src.getXCoordinates()[i]);
            assertEquals("yCoordinates at " + i, y, src.getYCoordinates()[i]);
            assertEquals("pointerIds at " + i, pointerId, src.getPointerIds()[i]);
            assertEquals("times at " + i, time, src.getTimes()[i]);
        }
    }

    public void testSet() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int limit = src.getXCoordinates().length * 2 + 10;
        for (int i = 0; i < limit; i++) {
            final int x = i;
            final int y = i * 2;
            final int pointerId = i * 3;
            final int time = i * 4;
            src.addPointer(x, y, pointerId, time);
        }
        final InputPointers dst = new InputPointers(DEFAULT_CAPACITY);
        dst.set(src);
        assertEquals("size after set", dst.getPointerSize(), src.getPointerSize());
        assertSame("xCoordinates after set", dst.getXCoordinates(), src.getXCoordinates());
        assertSame("yCoordinates after set", dst.getYCoordinates(), src.getYCoordinates());
        assertSame("pointerIds after set", dst.getPointerIds(), src.getPointerIds());
        assertSame("times after set", dst.getTimes(), src.getTimes());
    }

    public void testCopy() {
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int limit = 100;
        for (int i = 0; i < limit; i++) {
            final int x = i;
            final int y = i * 2;
            final int pointerId = i * 3;
            final int time = i * 4;
            src.addPointer(x, y, pointerId, time);
        }
        final InputPointers dst = new InputPointers(DEFAULT_CAPACITY);
        dst.copy(src);
        assertEquals("size after copy", dst.getPointerSize(), src.getPointerSize());
        assertNotSame("xCoordinates after copy", dst.getXCoordinates(), src.getXCoordinates());
        assertNotSame("yCoordinates after copy", dst.getYCoordinates(), src.getYCoordinates());
        assertNotSame("pointerIds after copy", dst.getPointerIds(), src.getPointerIds());
        assertNotSame("times after copy", dst.getTimes(), src.getTimes());
        final int size = dst.getPointerSize();
        assertIntArrayEquals("xCoordinates values after copy",
                dst.getXCoordinates(), 0, src.getXCoordinates(), 0, size);
        assertIntArrayEquals("yCoordinates values after copy",
                dst.getYCoordinates(), 0, src.getYCoordinates(), 0, size);
        assertIntArrayEquals("pointerIds values after copy",
                dst.getPointerIds(), 0, src.getPointerIds(), 0, size);
        assertIntArrayEquals("times values after copy",
                dst.getTimes(), 0, src.getTimes(), 0, size);
    }

    public void testAppend() {
        final int dstLength = 50;
        final InputPointers dst = new InputPointers(DEFAULT_CAPACITY);
        for (int i = 0; i < dstLength; i++) {
            final int x = i * 4;
            final int y = i * 3;
            final int pointerId = i * 2;
            final int time = i;
            dst.addPointer(x, y, pointerId, time);
        }
        final InputPointers dstCopy = new InputPointers(DEFAULT_CAPACITY);
        dstCopy.copy(dst);

        final ResizableIntArray srcXCoords = new ResizableIntArray(DEFAULT_CAPACITY);
        final ResizableIntArray srcYCoords = new ResizableIntArray(DEFAULT_CAPACITY);
        final ResizableIntArray srcPointerIds = new ResizableIntArray(DEFAULT_CAPACITY);
        final ResizableIntArray srcTimes = new ResizableIntArray(DEFAULT_CAPACITY);
        final int srcLength = 100;
        final int srcPointerId = 10;
        for (int i = 0; i < srcLength; i++) {
            final int x = i;
            final int y = i * 2;
            // The time value must be larger than <code>dst</code>.
            final int time = i * 4 + dstLength;
            srcXCoords.add(x);
            srcYCoords.add(y);
            srcPointerIds.add(srcPointerId);
            srcTimes.add(time);
        }

        final int startPos = 0;
        dst.append(srcPointerId, srcTimes, srcXCoords, srcYCoords,
                startPos, 0 /* length */);
        assertEquals("size after append zero", dstLength, dst.getPointerSize());
        assertIntArrayEquals("xCoordinates after append zero",
                dstCopy.getXCoordinates(), startPos, dst.getXCoordinates(), startPos, dstLength);
        assertIntArrayEquals("yCoordinates after append zero",
                dstCopy.getYCoordinates(), startPos, dst.getYCoordinates(), startPos, dstLength);
        assertIntArrayEquals("pointerIds after append zero",
                dstCopy.getPointerIds(), startPos, dst.getPointerIds(), startPos, dstLength);
        assertIntArrayEquals("times after append zero",
                dstCopy.getTimes(), startPos, dst.getTimes(), startPos, dstLength);

        dst.append(srcPointerId, srcTimes, srcXCoords, srcYCoords,
                startPos, srcLength);
        assertEquals("size after append", dstLength + srcLength, dst.getPointerSize());
        assertTrue("primitive length after append",
                dst.getPointerIds().length >= dstLength + srcLength);
        assertIntArrayEquals("original xCoordinates values after append",
                dstCopy.getXCoordinates(), startPos, dst.getXCoordinates(), startPos, dstLength);
        assertIntArrayEquals("original yCoordinates values after append",
                dstCopy.getYCoordinates(), startPos, dst.getYCoordinates(), startPos, dstLength);
        assertIntArrayEquals("original pointerIds values after append",
                dstCopy.getPointerIds(), startPos, dst.getPointerIds(), startPos, dstLength);
        assertIntArrayEquals("original times values after append",
                dstCopy.getTimes(), startPos, dst.getTimes(), startPos, dstLength);
        assertIntArrayEquals("appended xCoordinates values after append",
                srcXCoords.getPrimitiveArray(), startPos, dst.getXCoordinates(),
                dstLength, srcLength);
        assertIntArrayEquals("appended yCoordinates values after append",
                srcYCoords.getPrimitiveArray(), startPos, dst.getYCoordinates(),
                dstLength, srcLength);
        assertIntArrayEquals("appended pointerIds values after append",
                srcPointerIds.getPrimitiveArray(), startPos, dst.getPointerIds(),
                dstLength, srcLength);
        assertIntArrayEquals("appended times values after append",
                srcTimes.getPrimitiveArray(), startPos, dst.getTimes(), dstLength, srcLength);
    }

    public void testAppendResizableIntArray() {
        final int dstLength = 50;
        final InputPointers dst = new InputPointers(DEFAULT_CAPACITY);
        for (int i = 0; i < dstLength; i++) {
            final int x = i * 4;
            final int y = i * 3;
            final int pointerId = i * 2;
            final int time = i;
            dst.addPointer(x, y, pointerId, time);
        }
        final InputPointers dstCopy = new InputPointers(DEFAULT_CAPACITY);
        dstCopy.copy(dst);

        final int srcLength = 100;
        final int srcPointerId = 1;
        final int[] srcPointerIds = new int[srcLength];
        Arrays.fill(srcPointerIds, srcPointerId);
        final ResizableIntArray srcTimes = new ResizableIntArray(DEFAULT_CAPACITY);
        final ResizableIntArray srcXCoords = new ResizableIntArray(DEFAULT_CAPACITY);
        final ResizableIntArray srcYCoords= new ResizableIntArray(DEFAULT_CAPACITY);
        for (int i = 0; i < srcLength; i++) {
            // The time value must be larger than <code>dst</code>.
            final int time = i * 2 + dstLength;
            final int x = i * 3;
            final int y = i * 4;
            srcTimes.add(time);
            srcXCoords.add(x);
            srcYCoords.add(y);
        }

        dst.append(srcPointerId, srcTimes, srcXCoords, srcYCoords, 0, 0);
        assertEquals("size after append zero", dstLength, dst.getPointerSize());
        assertIntArrayEquals("xCoordinates after append zero",
                dstCopy.getXCoordinates(), 0, dst.getXCoordinates(), 0, dstLength);
        assertIntArrayEquals("yCoordinates after append zero",
                dstCopy.getYCoordinates(), 0, dst.getYCoordinates(), 0, dstLength);
        assertIntArrayEquals("pointerIds after append zero",
                dstCopy.getPointerIds(), 0, dst.getPointerIds(), 0, dstLength);
        assertIntArrayEquals("times after append zero",
                dstCopy.getTimes(), 0, dst.getTimes(), 0, dstLength);

        dst.append(srcPointerId, srcTimes, srcXCoords, srcYCoords, 0, srcLength);
        assertEquals("size after append", dstLength + srcLength, dst.getPointerSize());
        assertTrue("primitive length after append",
                dst.getPointerIds().length >= dstLength + srcLength);
        assertIntArrayEquals("original xCoordinates values after append",
                dstCopy.getXCoordinates(), 0, dst.getXCoordinates(), 0, dstLength);
        assertIntArrayEquals("original yCoordinates values after append",
                dstCopy.getYCoordinates(), 0, dst.getYCoordinates(), 0, dstLength);
        assertIntArrayEquals("original pointerIds values after append",
                dstCopy.getPointerIds(), 0, dst.getPointerIds(), 0, dstLength);
        assertIntArrayEquals("original times values after append",
                dstCopy.getTimes(), 0, dst.getTimes(), 0, dstLength);
        assertIntArrayEquals("appended xCoordinates values after append",
                srcXCoords.getPrimitiveArray(), 0, dst.getXCoordinates(), dstLength, srcLength);
        assertIntArrayEquals("appended yCoordinates values after append",
                srcYCoords.getPrimitiveArray(), 0, dst.getYCoordinates(), dstLength, srcLength);
        assertIntArrayEquals("appended pointerIds values after append",
                srcPointerIds, 0, dst.getPointerIds(), dstLength, srcLength);
        assertIntArrayEquals("appended times values after append",
                srcTimes.getPrimitiveArray(), 0, dst.getTimes(), dstLength, srcLength);
    }

    // TODO: Consolidate this method with
    // {@link ResizableIntArrayTests#assertIntArrayEquals(String,int[],int,int[],int,int)}.
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
        final InputPointers src = new InputPointers(DEFAULT_CAPACITY);
        final int limit = 100;
        final int shiftAmount = 20;
        for (int i = 0; i < limit; i++) {
            final int x = i;
            final int y = i * 2;
            final int pointerId = i * 3;
            final int time = i * 4;
            src.addPointer(x, y, pointerId, time);
        }
        src.shift(shiftAmount);
        assertEquals("length after shift", src.getPointerSize(), limit - shiftAmount);
        for (int i = 0; i < limit - shiftAmount; ++i) {
            final int oldIndex = i + shiftAmount;
            final int x = oldIndex;
            final int y = oldIndex * 2;
            final int pointerId = oldIndex * 3;
            final int time = oldIndex * 4;
            assertEquals("xCoordinates at " + i, x, src.getXCoordinates()[i]);
            assertEquals("yCoordinates at " + i, y, src.getYCoordinates()[i]);
            assertEquals("pointerIds at " + i, pointerId, src.getPointerIds()[i]);
            assertEquals("times at " + i, time, src.getTimes()[i]);
        }
    }
}
