/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.util;

public class SparseIntArray {
    private final SparseArray<Integer> mArray;

    public SparseIntArray() {
        this(10);
    }

    public SparseIntArray(final int initialCapacity) {
        mArray = new SparseArray<>(initialCapacity);
    }

    public int size() {
        return mArray.size();
    }

    public void clear() {
        mArray.clear();
    }

    public void put(final int key, final int value) {
        mArray.put(key, value);
    }

    public int get(final int key) {
        return get(key, 0);
    }

    public int get(final int key, final int valueIfKeyNotFound) {
        return mArray.get(key, valueIfKeyNotFound);
    }

    public int indexOfKey(final int key) {
        return mArray.indexOfKey(key);
    }

    public int keyAt(final int index) {
        return mArray.keyAt(index);
    }
}
