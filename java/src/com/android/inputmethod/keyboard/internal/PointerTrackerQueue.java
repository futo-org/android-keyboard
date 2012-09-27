/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.util.Log;

import com.android.inputmethod.latin.CollectionUtils;

import java.util.ArrayList;

public final class PointerTrackerQueue {
    private static final String TAG = PointerTrackerQueue.class.getSimpleName();
    private static final boolean DEBUG = false;

    public interface Element {
        public boolean isModifier();
        public boolean isInSlidingKeyInput();
        public void onPhantomUpEvent(long eventTime);
    }

    private static final int INITIAL_CAPACITY = 10;
    private final ArrayList<Element> mExpandableArrayOfActivePointers =
            CollectionUtils.newArrayList(INITIAL_CAPACITY);
    private int mArraySize = 0;

    public synchronized int size() {
        return mArraySize;
    }

    public synchronized void add(final Element pointer) {
        final ArrayList<Element> expandableArray = mExpandableArrayOfActivePointers;
        final int arraySize = mArraySize;
        if (arraySize < expandableArray.size()) {
            expandableArray.set(arraySize, pointer);
        } else {
            expandableArray.add(pointer);
        }
        mArraySize = arraySize + 1;
    }

    public synchronized void remove(final Element pointer) {
        final ArrayList<Element> expandableArray = mExpandableArrayOfActivePointers;
        final int arraySize = mArraySize;
        int newSize = 0;
        for (int index = 0; index < arraySize; index++) {
            final Element element = expandableArray.get(index);
            if (element == pointer) {
                if (newSize != index) {
                    Log.w(TAG, "Found duplicated element in remove: " + pointer);
                }
                continue; // Remove this element from the expandableArray.
            }
            if (newSize != index) {
                // Shift this element toward the beginning of the expandableArray.
                expandableArray.set(newSize, element);
            }
            newSize++;
        }
        mArraySize = newSize;
    }

    public synchronized Element getOldestElement() {
        return (mArraySize == 0) ? null : mExpandableArrayOfActivePointers.get(0);
    }

    public synchronized void releaseAllPointersOlderThan(final Element pointer,
            final long eventTime) {
        if (DEBUG) {
            Log.d(TAG, "releaseAllPoniterOlderThan: " + pointer + " " + this);
        }
        final ArrayList<Element> expandableArray = mExpandableArrayOfActivePointers;
        final int arraySize = mArraySize;
        int newSize, index;
        for (newSize = index = 0; index < arraySize; index++) {
            final Element element = expandableArray.get(index);
            if (element == pointer) {
                break; // Stop releasing elements.
            }
            if (!element.isModifier()) {
                element.onPhantomUpEvent(eventTime);
                continue; // Remove this element from the expandableArray.
            }
            if (newSize != index) {
                // Shift this element toward the beginning of the expandableArray.
                expandableArray.set(newSize, element);
            }
            newSize++;
        }
        // Shift rest of the expandableArray.
        int count = 0;
        for (; index < arraySize; index++) {
            final Element element = expandableArray.get(index);
            if (element == pointer) {
                if (count > 0) {
                    Log.w(TAG, "Found duplicated element in releaseAllPointersOlderThan: "
                            + pointer);
                }
                count++;
            }
            if (newSize != index) {
                expandableArray.set(newSize, expandableArray.get(index));
                newSize++;
            }
        }
        mArraySize = newSize;
    }

    public void releaseAllPointers(final long eventTime) {
        releaseAllPointersExcept(null, eventTime);
    }

    public synchronized void releaseAllPointersExcept(final Element pointer,
            final long eventTime) {
        if (DEBUG) {
            if (pointer == null) {
                Log.d(TAG, "releaseAllPoniters: " + this);
            } else {
                Log.d(TAG, "releaseAllPoniterExcept: " + pointer + " " + this);
            }
        }
        final ArrayList<Element> expandableArray = mExpandableArrayOfActivePointers;
        final int arraySize = mArraySize;
        int newSize = 0, count = 0;
        for (int index = 0; index < arraySize; index++) {
            final Element element = expandableArray.get(index);
            if (element == pointer) {
                if (count > 0) {
                    Log.w(TAG, "Found duplicated element in releaseAllPointersExcept: " + pointer);
                }
                count++;
            } else {
                element.onPhantomUpEvent(eventTime);
                continue; // Remove this element from the expandableArray.
            }
            if (newSize != index) {
                // Shift this element toward the beginning of the expandableArray.
                expandableArray.set(newSize, element);
            }
            newSize++;
        }
        mArraySize = newSize;
    }

    public synchronized boolean hasModifierKeyOlderThan(final Element pointer) {
        final ArrayList<Element> expandableArray = mExpandableArrayOfActivePointers;
        final int arraySize = mArraySize;
        for (int index = 0; index < arraySize; index++) {
            final Element element = expandableArray.get(index);
            if (element == pointer) {
                return false; // Stop searching modifier key.
            }
            if (element.isModifier()) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isAnyInSlidingKeyInput() {
        final ArrayList<Element> expandableArray = mExpandableArrayOfActivePointers;
        final int arraySize = mArraySize;
        for (int index = 0; index < arraySize; index++) {
            final Element element = expandableArray.get(index);
            if (element.isInSlidingKeyInput()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized String toString() {
        final StringBuilder sb = new StringBuilder();
        final ArrayList<Element> expandableArray = mExpandableArrayOfActivePointers;
        final int arraySize = mArraySize;
        for (int index = 0; index < arraySize; index++) {
            final Element element = expandableArray.get(index);
            if (sb.length() > 0)
                sb.append(" ");
            sb.append(element.toString());
        }
        return "[" + sb.toString() + "]";
    }
}
