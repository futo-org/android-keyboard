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

import android.util.SparseArray;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CollectionUtils {
    private CollectionUtils() {
        // This utility class is not publicly instantiable.
    }

    public static <K,V> HashMap<K,V> newHashMap() {
        return new HashMap<K,V>();
    }

    public static <K, V> WeakHashMap<K, V> newWeakHashMap() {
        return new WeakHashMap<K, V>();
    }

    public static <K,V> TreeMap<K,V> newTreeMap() {
        return new TreeMap<K,V>();
    }

    public static <K, V> Map<K,V> newSynchronizedTreeMap() {
        final TreeMap<K,V> treeMap = newTreeMap();
        return Collections.synchronizedMap(treeMap);
    }

    public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap() {
        return new ConcurrentHashMap<K,V>();
    }

    public static <E> HashSet<E> newHashSet() {
        return new HashSet<E>();
    }

    public static <E> TreeSet<E> newTreeSet() {
        return new TreeSet<E>();
    }

    public static <E> ArrayList<E> newArrayList() {
        return new ArrayList<E>();
    }

    public static <E> ArrayList<E> newArrayList(final int initialCapacity) {
        return new ArrayList<E>(initialCapacity);
    }

    public static <E> ArrayList<E> newArrayList(final Collection<E> collection) {
        return new ArrayList<E>(collection);
    }

    public static <E> LinkedList<E> newLinkedList() {
        return new LinkedList<E>();
    }

    public static <E> CopyOnWriteArrayList<E> newCopyOnWriteArrayList() {
        return new CopyOnWriteArrayList<E>();
    }

    public static <E> CopyOnWriteArrayList<E> newCopyOnWriteArrayList(
            final Collection<E> collection) {
        return new CopyOnWriteArrayList<E>(collection);
    }

    public static <E> CopyOnWriteArrayList<E> newCopyOnWriteArrayList(final E[] array) {
        return new CopyOnWriteArrayList<E>(array);
    }

    public static <E> ArrayDeque<E> newArrayDeque() {
        return new ArrayDeque<E>();
    }

    public static <E> SparseArray<E> newSparseArray() {
        return new SparseArray<E>();
    }
}
