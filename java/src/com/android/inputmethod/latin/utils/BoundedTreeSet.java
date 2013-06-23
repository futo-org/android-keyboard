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

package com.android.inputmethod.latin.utils;

import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * A TreeSet that is bounded in size and throws everything that's smaller than its limit
 */
public final class BoundedTreeSet extends TreeSet<SuggestedWordInfo> {
    private final int mCapacity;
    public BoundedTreeSet(final Comparator<SuggestedWordInfo> comparator, final int capacity) {
        super(comparator);
        mCapacity = capacity;
    }

    @Override
    public boolean add(final SuggestedWordInfo e) {
        if (size() < mCapacity) return super.add(e);
        if (comparator().compare(e, last()) > 0) return false;
        super.add(e);
        pollLast(); // removes the last element
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends SuggestedWordInfo> e) {
        if (null == e) return false;
        return super.addAll(e);
    }
}
