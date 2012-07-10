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

public class InputPointers {
    private final ScalableIntArray mXCoordinates = new ScalableIntArray();
    private final ScalableIntArray mYCoordinates = new ScalableIntArray();
    private final ScalableIntArray mPointerIds = new ScalableIntArray();
    private final ScalableIntArray mTimes = new ScalableIntArray();

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
     * @param src the source {@link InputPointers} to append the pointers.
     * @param startPos the starting index of the pointers in {@code src}.
     * @param length the number of pointers to be appended.
     */
    public void append(InputPointers src, int startPos, int length) {
        final int currentLength = getPointerSize();
        final int newLength = currentLength + length;
        mXCoordinates.ensureCapacity(newLength);
        mYCoordinates.ensureCapacity(newLength);
        mPointerIds.ensureCapacity(newLength);
        mTimes.ensureCapacity(newLength);
        System.arraycopy(src.getXCoordinates(), startPos, getXCoordinates(), currentLength, length);
        System.arraycopy(src.getYCoordinates(), startPos, getYCoordinates(), currentLength, length);
        System.arraycopy(src.getPointerIds(), startPos, getPointerIds(), currentLength, length);
        System.arraycopy(src.getTimes(), startPos, getTimes(), currentLength, length);
    }

    public void reset() {
        mXCoordinates.reset();
        mYCoordinates.reset();
        mPointerIds.reset();
        mTimes.reset();
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
        private static final int DEFAULT_SIZE = BinaryDictionary.MAX_WORD_LENGTH;
        private int[] mArray;
        private int mLength;

        public ScalableIntArray() {
            reset();
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

        public void ensureCapacity(int minimumCapacity) {
            if (mArray.length < minimumCapacity) {
                final int nextCapacity = mArray.length * 2;
                grow(minimumCapacity > nextCapacity ? minimumCapacity : nextCapacity);
            }
        }

        private void grow(int newCapacity) {
            final int[] newArray = new int[newCapacity];
            System.arraycopy(mArray, 0, newArray, 0, mArray.length);
            mArray = newArray;
        }

        public int getLength() {
            return mLength;
        }

        public void reset() {
            mArray = new int[DEFAULT_SIZE];
            mLength = 0;
        }

        public int[] getPrimitiveArray() {
            return mArray;
        }

        public void copy(ScalableIntArray ip) {
            ensureCapacity(ip.mLength);
            System.arraycopy(ip.mArray, 0, mArray, 0, ip.mLength);
            mLength = ip.mLength;
        }

        public void set(ScalableIntArray ip) {
            mArray = ip.mArray;
            mLength = ip.mLength;
        }
    }
}
