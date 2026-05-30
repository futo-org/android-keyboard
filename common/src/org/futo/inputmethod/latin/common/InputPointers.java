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

package org.futo.inputmethod.latin.common;

import android.util.Log;

import org.futo.inputmethod.annotations.UsedForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nonnull;

import kotlin.Pair;

// TODO: This class is not thread-safe.
public final class InputPointers {
    public static final class GestureSegment {
        public final int pointerId;
        public final ResizableIntArray x = new ResizableIntArray(0);
        public final ResizableIntArray y = new ResizableIntArray(0);
        public final ResizableIntArray t = new ResizableIntArray(0);

        public GestureSegment(int pointerId) {
            this.pointerId = pointerId;
        }
    }
    private static final boolean DEBUG_TIME = false;
    private static final boolean DEBUG_TRACE = false;
    private static final String TAG = "InputPointers";

    private final int mDefaultCapacity;
    private final ResizableIntArray mXCoordinates;
    private final ResizableIntArray mYCoordinates;
    private final ResizableIntArray mPointerIds;
    private final ResizableIntArray mTimes;
    private final ArrayList<GestureSegment> mGestureSegments = new ArrayList<>();
    private final HashMap<Integer, GestureSegment> mActivePointerSegments = new HashMap<>();

    public InputPointers(final int defaultCapacity) {
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

    public void addPointerAt(final int index, final int x, final int y, final int pointerId,
            final int time) {
        mXCoordinates.addAt(index, x);
        mYCoordinates.addAt(index, y);
        mPointerIds.addAt(index, pointerId);
        if (DEBUG_TIME) {
            fillWithLastTimeUntil(index);
        }
        mTimes.addAt(index, time);
    }

    @UsedForTesting
    public void addPointer(final int x, final int y, final int pointerId, final int time) {
        mXCoordinates.add(x);
        mYCoordinates.add(y);
        mPointerIds.add(pointerId);
        mTimes.add(time);
    }

    public void set(@Nonnull final InputPointers ip) {
        if(DEBUG_TRACE) Log.d(TAG, "set " + System.identityHashCode(this) + " ip=" + System.identityHashCode(ip));
        mXCoordinates.set(ip.mXCoordinates);
        mYCoordinates.set(ip.mYCoordinates);
        mPointerIds.set(ip.mPointerIds);
        mTimes.set(ip.mTimes);

        mGestureSegments.clear();
        mGestureSegments.addAll(ip.getGestureSegments());
        mActivePointerSegments.clear();
    }

    public void copy(@Nonnull final InputPointers ip) {
        if(DEBUG_TRACE) Log.d(TAG, "copy " + System.identityHashCode(this) + " ip=" + System.identityHashCode(ip));
        mXCoordinates.copy(ip.mXCoordinates);
        mYCoordinates.copy(ip.mYCoordinates);
        mPointerIds.copy(ip.mPointerIds);
        mTimes.copy(ip.mTimes);

        mGestureSegments.clear();
        mGestureSegments.addAll(ip.getGestureSegments());
        mActivePointerSegments.clear();
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
    public void append(final int pointerId, @Nonnull final ResizableIntArray times,
            @Nonnull final ResizableIntArray xCoordinates,
            @Nonnull final ResizableIntArray yCoordinates, final int startPos, final int length) {
        if(DEBUG_TRACE) {
            Log.d(TAG, "add " + pointerId + " " + length + " " + System.identityHashCode(this) + " " + Thread.currentThread().getId());
            Log.d(TAG, "  x:" + xCoordinates.get(startPos) + "->" + xCoordinates.get(startPos + length - 1));
            Log.d(TAG, "  y:" + yCoordinates.get(startPos) + "->" + yCoordinates.get(startPos + length - 1));
            Log.d(TAG, "  t:" + times.get(startPos) + "->" + times.get(startPos + length - 1));
        }
        if (length == 0) {
            return;
        }
        mXCoordinates.append(xCoordinates, startPos, length);
        mYCoordinates.append(yCoordinates, startPos, length);
        mPointerIds.fill(pointerId, mPointerIds.getLength(), length);
        mTimes.append(times, startPos, length);

        GestureSegment seg = mActivePointerSegments.get(pointerId);
        if(seg == null) {
            Log.e(TAG, "Attempting to append points to pointerId " + pointerId + " which was not pressed down!!");
            return;
        }
        seg.x.append(xCoordinates, startPos, length);
        seg.y.append(yCoordinates, startPos, length);
        seg.t.append(times, startPos, length);
    }

    public void onPointerDown(final int pointerId) {
        if(DEBUG_TRACE) Log.d(TAG, "down " + pointerId + " " + System.identityHashCode(this));
        GestureSegment seg = new GestureSegment(pointerId);
        mActivePointerSegments.put(pointerId, seg);
        mGestureSegments.add(seg);

    }
    public void onPointerUp(final int pointerId) {
        if(DEBUG_TRACE) Log.d(TAG, "up " + pointerId + " " + System.identityHashCode(this));
        //mActivePointerSegments.remove(pointerId);
    }

    /**
     * Shift to the left by elementCount, discarding elementCount pointers at the start.
     * @param elementCount how many elements to shift.
     */
    @UsedForTesting
    public void shift(final int elementCount) {
        mXCoordinates.shift(elementCount);
        mYCoordinates.shift(elementCount);
        mPointerIds.shift(elementCount);
        mTimes.shift(elementCount);

        for(int i=0; i<elementCount; i++) mGestureSegments.remove(0);
    }

    public void reset() {
        if(DEBUG_TRACE) Log.d(TAG, "reset full" + Thread.currentThread().getId());
        resetNonBatch();
        resetForBatch();
    }

    public void resetNonBatch() {
        if(DEBUG_TRACE) Log.d(TAG, "(reset nonbatch)" + Thread.currentThread().getId());
        final int defaultCapacity = mDefaultCapacity;
        mXCoordinates.reset(defaultCapacity);
        mYCoordinates.reset(defaultCapacity);
        mPointerIds.reset(defaultCapacity);
        mTimes.reset(defaultCapacity);
    }

    public void resetForBatch() {
        if(DEBUG_TRACE) Log.d(TAG, "(reset forbatch)" + Thread.currentThread().getId());
        mGestureSegments.clear();
        mActivePointerSegments.clear();
    }

    public void upAllPointers() {
        if(DEBUG_TRACE) Log.d(TAG, "up all pointers " + Thread.currentThread().getId());
        mActivePointerSegments.clear();
    }

    public int getPointerSize() {
        return mXCoordinates.getLength();
    }

    @Nonnull
    public int[] getXCoordinates() {
        return mXCoordinates.getPrimitiveArray();
    }

    @Nonnull
    public int[] getYCoordinates() {
        return mYCoordinates.getPrimitiveArray();
    }

    @Nonnull
    public int[] getPointerIds() {
        return mPointerIds.getPrimitiveArray();
    }

    @Nonnull
    public List<GestureSegment> getGestureSegments() { return mGestureSegments; }

    /**
     * Gets the time each point was registered, in milliseconds, relative to the first event in the
     * sequence.
     * @return The time each point was registered, in milliseconds, relative to the first event in
     * the sequence.
     */
    @Nonnull
    public int[] getTimes() {
        return mTimes.getPrimitiveArray();
    }

    @Override
    public String toString() {
        return "size=" + getPointerSize() + " id=" + mPointerIds + " time=" + mTimes
                + " x=" + mXCoordinates + " y=" + mYCoordinates;
    }
}
