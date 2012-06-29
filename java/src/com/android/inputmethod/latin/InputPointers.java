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
        return mXCoordinates.mArray;
    }

    public int[] getYCoordinates() {
        return mYCoordinates.mArray;
    }

    public int[] getPointerIds() {
        return mPointerIds.mArray;
    }

    public int[] getTimes() {
        return mTimes.mArray;
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
            if (mLength >= mArray.length) {
                final int[] newArray = new int[mLength * 2];
                System.arraycopy(mArray, 0, newArray, 0, mLength);
            }
            mArray[mLength] = val;
            ++mLength;
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
            mArray = Arrays.copyOf(ip.mArray, ip.mArray.length);
        }

        public void set(ScalableIntArray ip) {
            mArray = ip.mArray;
            mLength = ip.mLength;
        }
    }
}
