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

import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;

public class SparseArray<E> {
    private final ArrayList<Integer> mKeys;
    private final ArrayList<E> mValues;

    public SparseArray() {
        this(10);
    }

    public SparseArray(final int initialCapacity) {
        mKeys = CollectionUtils.newArrayList(initialCapacity);
        mValues = CollectionUtils.newArrayList(initialCapacity);
    }

    public int size() {
        return mKeys.size();
    }

    public void clear() {
        mKeys.clear();
        mValues.clear();
    }

    public void put(final int key, final E value) {
        final int index = Collections.binarySearch(mKeys, key);
        if (index >= 0) {
            mValues.set(index, value);
            return;
        }
        final int insertIndex = ~index;
        mKeys.add(insertIndex, key);
        mValues.add(insertIndex, value);
    }

    public E get(final int key) {
        return get(key, null);
    }

    public E get(final int key, final E valueIfKeyNotFound) {
        final int index = Collections.binarySearch(mKeys, key);
        if (index >= 0) {
            return mValues.get(index);
        }
        return valueIfKeyNotFound;
    }

    public int indexOfKey(final int key) {
        return mKeys.indexOf(key);
    }

    public int indexOfValue(final E value) {
        return mValues.indexOf(value);
    }

    public int keyAt(final int index) {
        return mKeys.get(index);
    }

    public E valueAt(final int index) {
        return mValues.get(index);
    }
}
