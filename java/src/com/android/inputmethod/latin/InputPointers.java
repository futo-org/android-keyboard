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

import java.util.Arrays;

// TODO: This class is not thread-safe.
public class InputPointers {
    private final int mDefaultCapacity;
    private final ScalableIntArray mXCoordinates;
    private final ScalableIntArray mYCoordinates;
    private final ScalableIntArray mPointerIds;
    private final ScalableIntArray mTimes;

    public InputPointers(int defaultCapacity) {
        mDefaultCapacity = defaultCapacity;
        mXCoordinates = new ScalableIntArray(defaultCapacity);
        mYCoordinates = new ScalableIntArray(defaultCapacity);
        mPointerIds = new ScalableIntArray(defaultCapacity);
        mTimes = new ScalableIntArray(defaultCapacity);
    }

    public void addPointer(int index, int x, int y, int pointerId, int time) {
        mXCoordinates.add(index, x);
        mYCoordinates.add(index, y);
        mPointerIds.add(index, pointerId);
        mTimes.add(index, time);
    }

    public void addPointer(int x, int y, int pointerId, int time) {
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
    public void append(InputPointers src, int startPos, int length) {
        if (length == 0) {
            return;
        }
        mXCoordinates.append(src.mXCoordinates, startPos, length);
        mYCoordinates.append(src.mYCoordinates, startPos, length);
        mPointerIds.append(src.mPointerIds, startPos, length);
        mTimes.append(src.mTimes, startPos, length);
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
        return mTimes.getPrimitiveArray();
    }

    private static class ScalableIntArray {
        private int[] mArray;
        private int mLength;

        public ScalableIntArray(int capacity) {
            reset(capacity);
        }

        public void add(int index, int val) {
            if (mLength < index + 1) {
                mLength = index;
                add(val);
            } else {
                mArray[index] = val;
            }
        }

        public void add(int val) {
            final int nextLength = mLength + 1;
            ensureCapacity(nextLength);
            mArray[mLength] = val;
            mLength = nextLength;
        }

        private void ensureCapacity(int minimumCapacity) {
            if (mArray.length < minimumCapacity) {
                final int nextCapacity = mArray.length * 2;
                // The following is the same as newLength = Math.max(minimumCapacity, nextCapacity);
                final int newLength = minimumCapacity > nextCapacity
                        ? minimumCapacity
                        : nextCapacity;
                mArray = Arrays.copyOf(mArray, newLength);
            }
        }

        public int getLength() {
            return mLength;
        }

        public void reset(int capacity) {
            mArray = new int[capacity];
            mLength = 0;
        }

        public int[] getPrimitiveArray() {
            return mArray;
        }

        public void set(ScalableIntArray ip) {
            mArray = ip.mArray;
            mLength = ip.mLength;
        }

        public void copy(ScalableIntArray ip) {
            ensureCapacity(ip.mLength);
            System.arraycopy(ip.mArray, 0, mArray, 0, ip.mLength);
            mLength = ip.mLength;
        }

        public void append(ScalableIntArray src, int startPos, int length) {
            final int currentLength = mLength;
            final int newLength = currentLength + length;
            ensureCapacity(newLength);
            System.arraycopy(src.mArray, startPos, mArray, currentLength, length);
            mLength = newLength;
        }
    }
}
