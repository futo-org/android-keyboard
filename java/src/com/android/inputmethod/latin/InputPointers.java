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

import android.util.Log;
import android.util.SparseIntArray;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.define.DebugFlags;
import com.android.inputmethod.latin.utils.ResizableIntArray;

// TODO: This class is not thread-safe.
public final class InputPointers {
    private static final String TAG = InputPointers.class.getSimpleName();
    private static final boolean DEBUG_TIME = false;

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

    private void fillWithLastTimeUntil(final int index) {
        final int fromIndex = mTimes.getLength();
        // Fill the gap with the latest time.
        // See {@link #getTime(int)} and {@link #isValidTimeStamps()}.
        if (fromIndex <= 0) {
            return;
        }
        final int fillLength = index - fromIndex + 1;
        if (fillLength <= 0) {
            return;
        }
        final int lastTime = mTimes.get(fromIndex - 1);
        mTimes.fill(lastTime, fromIndex, fillLength);
    }

    public void addPointerAt(int index, int x, int y, int pointerId, int time) {
        mXCoordinates.addAt(index, x);
        mYCoordinates.addAt(index, y);
        mPointerIds.addAt(index, pointerId);
        if (DebugFlags.DEBUG_ENABLED || DEBUG_TIME) {
            fillWithLastTimeUntil(index);
        }
        mTimes.addAt(index, time);
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
        if (DebugFlags.DEBUG_ENABLED || DEBUG_TIME) {
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
        final int[] pointerIds = mPointerIds.getPrimitiveArray();
        final SparseIntArray lastTimeOfPointers = new SparseIntArray();
        final int size = getPointerSize();
        for (int i = 0; i < size; ++i) {
            final int pointerId = pointerIds[i];
            final int time = times[i];
            final int lastTime = lastTimeOfPointers.get(pointerId, time);
            if (time < lastTime) {
                // dump
                for (int j = 0; j < size; ++j) {
                    Log.d(TAG, "--- (" + j + ") " + times[j]);
                }
                return false;
            }
            lastTimeOfPointers.put(pointerId, time);
        }
        return true;
    }
}
