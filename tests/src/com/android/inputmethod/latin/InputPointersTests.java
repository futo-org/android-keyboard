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

package com.android.inputmethod.latin;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.inputmethod.latin.utils.ResizableIntArray;

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
            src.addPointer(i, i * 2, i * 3, i * 4);
            assertEquals("size after add " + i, i + 1, src.getPointerSize());
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
            assertEquals("size after add at " + i, i + 1, src.getPointerSize());
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
            src.addPointer(i, i * 2, i * 3, i * 4);
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
        assertEquals("size after append zero", dstLen, dst.getPointerSize());
        assertIntArrayEquals("xCoordinates after append zero",
                dstCopy.getXCoordinates(), 0, dst.getXCoordinates(), 0, dstLen);
        assertIntArrayEquals("yCoordinates after append zero",
                dstCopy.getYCoordinates(), 0, dst.getYCoordinates(), 0, dstLen);
        assertIntArrayEquals("pointerIds after append zero",
                dstCopy.getPointerIds(), 0, dst.getPointerIds(), 0, dstLen);
        assertIntArrayEquals("times after append zero",
                dstCopy.getTimes(), 0, dst.getTimes(), 0, dstLen);

        dst.append(src, 0, srcLen);
        assertEquals("size after append", dstLen + srcLen, dst.getPointerSize());
        assertTrue("primitive length after append",
                dst.getPointerIds().length >= dstLen + srcLen);
        assertIntArrayEquals("original xCoordinates values after append",
                dstCopy.getXCoordinates(), 0, dst.getXCoordinates(), 0, dstLen);
        assertIntArrayEquals("original yCoordinates values after append",
                dstCopy.getYCoordinates(), 0, dst.getYCoordinates(), 0, dstLen);
        assertIntArrayEquals("original pointerIds values after append",
                dstCopy.getPointerIds(), 0, dst.getPointerIds(), 0, dstLen);
        assertIntArrayEquals("original times values after append",
                dstCopy.getTimes(), 0, dst.getTimes(), 0, dstLen);
        assertIntArrayEquals("appended xCoordinates values after append",
                src.getXCoordinates(), 0, dst.getXCoordinates(), dstLen, srcLen);
        assertIntArrayEquals("appended yCoordinates values after append",
                src.getYCoordinates(), 0, dst.getYCoordinates(), dstLen, srcLen);
        assertIntArrayEquals("appended pointerIds values after append",
                src.getPointerIds(), 0, dst.getPointerIds(), dstLen, srcLen);
        assertIntArrayEquals("appended times values after append",
                src.getTimes(), 0, dst.getTimes(), dstLen, srcLen);
    }

    public void testAppendResizableIntArray() {
        final int srcLen = 100;
        final int srcPointerId = 1;
        final int[] srcPointerIds = new int[srcLen];
        Arrays.fill(srcPointerIds, srcPointerId);
        final ResizableIntArray srcTimes = new ResizableIntArray(DEFAULT_CAPACITY);
        final ResizableIntArray srcXCoords = new ResizableIntArray(DEFAULT_CAPACITY);
        final ResizableIntArray srcYCoords= new ResizableIntArray(DEFAULT_CAPACITY);
        for (int i = 0; i < srcLen; i++) {
            srcTimes.add(i * 2);
            srcXCoords.add(i * 3);
            srcYCoords.add(i * 4);
        }
        final int dstLen = 50;
        final InputPointers dst = new InputPointers(DEFAULT_CAPACITY);
        for (int i = 0; i < dstLen; i++) {
            final int value = -i - 1;
            dst.addPointer(value * 4, value * 3, value * 2, value);
        }
        final InputPointers dstCopy = new InputPointers(DEFAULT_CAPACITY);
        dstCopy.copy(dst);

        dst.append(srcPointerId, srcTimes, srcXCoords, srcYCoords, 0, 0);
        assertEquals("size after append zero", dstLen, dst.getPointerSize());
        assertIntArrayEquals("xCoordinates after append zero",
                dstCopy.getXCoordinates(), 0, dst.getXCoordinates(), 0, dstLen);
        assertIntArrayEquals("yCoordinates after append zero",
                dstCopy.getYCoordinates(), 0, dst.getYCoordinates(), 0, dstLen);
        assertIntArrayEquals("pointerIds after append zero",
                dstCopy.getPointerIds(), 0, dst.getPointerIds(), 0, dstLen);
        assertIntArrayEquals("times after append zero",
                dstCopy.getTimes(), 0, dst.getTimes(), 0, dstLen);

        dst.append(srcPointerId, srcTimes, srcXCoords, srcYCoords, 0, srcLen);
        assertEquals("size after append", dstLen + srcLen, dst.getPointerSize());
        assertTrue("primitive length after append",
                dst.getPointerIds().length >= dstLen + srcLen);
        assertIntArrayEquals("original xCoordinates values after append",
                dstCopy.getXCoordinates(), 0, dst.getXCoordinates(), 0, dstLen);
        assertIntArrayEquals("original yCoordinates values after append",
                dstCopy.getYCoordinates(), 0, dst.getYCoordinates(), 0, dstLen);
        assertIntArrayEquals("original pointerIds values after append",
                dstCopy.getPointerIds(), 0, dst.getPointerIds(), 0, dstLen);
        assertIntArrayEquals("original times values after append",
                dstCopy.getTimes(), 0, dst.getTimes(), 0, dstLen);
        assertIntArrayEquals("appended xCoordinates values after append",
                srcXCoords.getPrimitiveArray(), 0, dst.getXCoordinates(), dstLen, srcLen);
        assertIntArrayEquals("appended yCoordinates values after append",
                srcYCoords.getPrimitiveArray(), 0, dst.getYCoordinates(), dstLen, srcLen);
        assertIntArrayEquals("appended pointerIds values after append",
                srcPointerIds, 0, dst.getPointerIds(), dstLen, srcLen);
        assertIntArrayEquals("appended times values after append",
                srcTimes.getPrimitiveArray(), 0, dst.getTimes(), dstLen, srcLen);
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
            src.addPointer(i, i * 2, i * 3, i * 4);
        }
        src.shift(shiftAmount);
        for (int i = 0; i < limit - shiftAmount; ++i) {
            assertEquals("xCoordinates at " + i, i + shiftAmount, src.getXCoordinates()[i]);
            assertEquals("yCoordinates at " + i, (i + shiftAmount) * 2, src.getYCoordinates()[i]);
            assertEquals("pointerIds at " + i, (i + shiftAmount) * 3, src.getPointerIds()[i]);
            assertEquals("times at " + i, (i + shiftAmount) * 4, src.getTimes()[i]);
        }
    }
}
