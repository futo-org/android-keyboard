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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.utils.ResizableIntArray;

import android.util.Log;

// TODO: This class is not thread-safe.
public final class InputPointers {
    private static final String TAG = InputPointers.class.getSimpleName();
    private final int mDefaultCapacity;
    private final ResizableIntArray mXCoordinates;
    private final ResizableIntArray mYCoordinates;
    private final ResizableIntArray mPointerIds;
    private final ResizableIntArray mTimes;

    public InputPointers(int defaultCapacity) {
        mDefaultCapacity = defaultCapacity;
        mXCoordinates = new ResizableIntArray(defaultCapacity);
        mYCoordinates = new ResizableIntArray(defaultCapacity);
        mPointerIds = new ResizableIntArray(defaultCapacity);
        mTimes = new ResizableIntArray(defaultCapacity);
    }

    public void addPointer(int index, int x, int y, int pointerId, int time) {
        mXCoordinates.add(index, x);
        mYCoordinates.add(index, y);
        mPointerIds.add(index, pointerId);
        mTimes.add(index, time);
    }

    @UsedForTesting
    void addPointer(int x, int y, int pointerId, int time) {
        mXCoordinates.add(x);
        mYCoordinates.add(y);
        mPointerIds.add(pointerId);
        mTimes.add(time);
    }

    public void set(InputPointers ip) {
        mXCoordinates.set(ip.mXCoordinates);
        mYCoordinates.set(ip.mYCoordinates);
        mPointerIds.set(ip.mPointerIds);
        mTimes.set(ip.mTimes);
    }

    public void copy(InputPointers ip) {
        mXCoordinates.copy(ip.mXCoordinates);
        mYCoordinates.copy(ip.mYCoordinates);
        mPointerIds.copy(ip.mPointerIds);
        mTimes.copy(ip.mTimes);
    }

    /**
     * Append the pointers in the specified {@link InputPointers} to the end of this.
     * @param src the source {@link InputPointers} to read the data from.
     * @param startPos the starting index of the pointers in {@code src}.
     * @param length the number of pointers to be appended.
     */
    @UsedForTesting
    void append(InputPointers src, int startPos, int length) {
        if (length == 0) {
            return;
        }
        mXCoordinates.append(src.mXCoordinates, startPos, length);
        mYCoordinates.append(src.mYCoordinates, startPos, length);
        mPointerIds.append(src.mPointerIds, startPos, length);
        mTimes.append(src.mTimes, startPos, length);
    }

    /**
     * Append the times, x-coordinates and y-coordinates in the specified {@link ResizableIntArray}
     * to the end of this.
     * @param pointerId the pointer id of the source.
     * @param times the source {@link ResizableIntArray} to read the event times from.
     * @param xCoordinates the source {@link ResizableIntArray} to read the x-coordinates from.
     * @param yCoordinates the source {@link ResizableIntArray} to read the y-coordinates from.
     * @param startPos the starting index of the data in {@code times} and etc.
     * @param length the number of data to be appended.
     */
    public void append(int pointerId, ResizableIntArray times, ResizableIntArray xCoordinates,
            ResizableIntArray yCoordinates, int startPos, int length) {
        if (length == 0) {
            return;
        }
        mXCoordinates.append(xCoordinates, startPos, length);
        mYCoordinates.append(yCoordinates, startPos, length);
        mPointerIds.fill(pointerId, mPointerIds.getLength(), length);
        mTimes.append(times, startPos, length);
    }

    /**
     * Shift to the left by elementCount, discarding elementCount pointers at the start.
     * @param elementCount how many elements to shift.
     */
    public void shift(final int elementCount) {
        mXCoordinates.shift(elementCount);
        mYCoordinates.shift(elementCount);
        mPointerIds.shift(elementCount);
        mTimes.shift(elementCount);
    }

    public void reset() {
        final int defaultCapacity = mDefaultCapacity;
        mXCoordinates.reset(defaultCapacity);
        mYCoordinates.reset(defaultCapacity);
        mPointerIds.reset(defaultCapacity);
        mTimes.reset(defaultCapacity);
    }

    public int getPointerSize() {
        return mXCoordinates.getLength();
    }

    public int[] getXCoordinates() {
        return mXCoordinates.getPrimitiveArray();
    }

    public int[] getYCoordinates() {
        return mYCoordinates.getPrimitiveArray();
    }

    public int[] getPointerIds() {
        return mPointerIds.getPrimitiveArray();
    }

    public int[] getTimes() {
        if (LatinImeLogger.sDBG) {
            if (!isValidTimeStamps()) {
                throw new RuntimeException("Time stamps are invalid.");
            }
        }
        return mTimes.getPrimitiveArray();
    }

    @Override
    public String toString() {
        return "size=" + getPointerSize() + " id=" + mPointerIds + " time=" + mTimes
                + " x=" + mXCoordinates + " y=" + mYCoordinates;
    }

    private boolean isValidTimeStamps() {
        final int[] times = mTimes.getPrimitiveArray();
        for (int i = 1; i < getPointerSize(); ++i) {
            if (times[i] < times[i - 1]) {
                // dump
                for (int j = 0; j < times.length; ++j) {
                    Log.d(TAG, "--- (" + j + ") " + times[j]);
                }
                return false;
            }
        }
        return true;
    }
}
