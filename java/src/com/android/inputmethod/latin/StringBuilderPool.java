/*
 * Copyright (C) 2011 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A pool of string builders to be used from anywhere.
 */
public class StringBuilderPool {
    // Singleton
    private static final StringBuilderPool sInstance = new StringBuilderPool();
    private static final boolean DEBUG = false;
    private StringBuilderPool() {}
    // TODO: Make this a normal array with a size of 20, or a ConcurrentQueue
    private final List<StringBuilder> mPool =
            Collections.synchronizedList(new ArrayList<StringBuilder>());

    public static StringBuilder getStringBuilder(final int initialSize) {
        // TODO: although the pool is synchronized, the following is not thread-safe.
        // Two threads entering this at the same time could take the same size of the pool and the
        // second to attempt removing this index from the pool would crash with an
        // IndexOutOfBoundsException.
        // At the moment this pool is only used in Suggest.java and only in one thread so it's
        // okay. The simplest thing to do here is probably to replace the ArrayList with a
        // ConcurrentQueue.
        final int poolSize = sInstance.mPool.size();
        final StringBuilder sb = poolSize > 0 ? (StringBuilder) sInstance.mPool.remove(poolSize - 1)
                : new StringBuilder(initialSize);
        sb.setLength(0);
        return sb;
    }

    public static void recycle(final StringBuilder garbage) {
        if (DEBUG) {
            final int gid = garbage.hashCode();
            for (final StringBuilder q : sInstance.mPool) {
                if (gid == q.hashCode()) throw new RuntimeException("Duplicate id " + gid);
            }
        }
        sInstance.mPool.add(garbage);
    }

    public static void ensureCapacity(final int capacity, final int initialSize) {
        for (int i = sInstance.mPool.size(); i < capacity; ++i) {
            final StringBuilder sb = new StringBuilder(initialSize);
            sInstance.mPool.add(sb);
        }
    }

    public static int getSize() {
        return sInstance.mPool.size();
    }
}
