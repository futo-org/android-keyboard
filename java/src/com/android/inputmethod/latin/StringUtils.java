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

package com.android.inputmethod.latin;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Locale;

public final class StringUtils {
    private StringUtils() {
        // This utility class is not publicly instantiable.
    }

    public static int codePointCount(final String text) {
        if (TextUtils.isEmpty(text)) return 0;
        return text.codePointCount(0, text.length());
    }

    public static boolean containsInArray(final String key, final String[] array) {
        for (final String element : array) {
            if (key.equals(element)) return true;
        }
        return false;
    }

    public static boolean containsInCsv(final String key, final String csv) {
        if (TextUtils.isEmpty(csv)) return false;
        return containsInArray(key, csv.split(","));
    }

    public static String appendToCsvIfNotExists(final String key, final String csv) {
        if (TextUtils.isEmpty(csv)) return key;
        if (containsInCsv(key, csv)) return csv;
        return csv + "," + key;
    }

    public static String removeFromCsvIfExists(final String key, final String csv) {
        if (TextUtils.isEmpty(csv)) return "";
        final String[] elements = csv.split(",");
        if (!containsInArray(key, elements)) return csv;
        final ArrayList<String> result = CollectionUtils.newArrayList(elements.length - 1);
        for (final String element : elements) {
            if (!key.equals(element)) result.add(element);
        }
        return TextUtils.join(",", result);
    }

    /**
     * Find a string that start with specified prefix from an array.
     *
     * @param prefix a prefix string to find.
     * @param array an string array to be searched.
     * @return the rest part of the string that starts with the prefix.
     * Returns null if it couldn't be found.
     */
    public static String findPrefixedString(final String prefix, final String[] array) {
        for (final String element : array) {
            if (element.startsWith(prefix)) {
                return element.substring(prefix.length());
            }
        }
        return null;
    }

    /**
     * Remove duplicates from an array of strings.
     *
     * This method will always keep the first occurrence of all strings at their position
     * in the array, removing the subsequent ones.
     */
    public static void removeDupes(final ArrayList<String> suggestions) {
        if (suggestions.size() < 2) return;
        int i = 1;
        // Don't cache suggestions.size(), since we may be removing items
        while (i < suggestions.size()) {
            final String cur = suggestions.get(i);
            // Compare each suggestion with each previous suggestion
            for (int j = 0; j < i; j++) {
                final String previous = suggestions.get(j);
                if (TextUtils.equals(cur, previous)) {
                    suggestions.remove(i);
                    i--;
                    break;
                }
            }
            i++;
        }
    }

    public static String toTitleCase(final String s, final Locale locale) {
        if (s.length() <= 1) {
            // TODO: is this really correct? Shouldn't this be s.toUpperCase()?
            return s;
        }
        // TODO: fix the bugs below
        // - This does not work for Greek, because it returns upper case instead of title case.
        // - It does not work for Serbian, because it fails to account for the "lj" character,
        // which should be "Lj" in title case and "LJ" in upper case.
        // - It does not work for Dutch, because it fails to account for the "ij" digraph, which
        // are two different characters but both should be capitalized as "IJ" as if they were
        // a single letter.
        // - It also does not work with unicode surrogate code points.
        return s.toUpperCase(locale).charAt(0) + s.substring(1);
    }

    private static final int[] EMPTY_CODEPOINTS = {};

    public static int[] toCodePointArray(final String string) {
        final int length = string.length();
        if (length <= 0) {
            return EMPTY_CODEPOINTS;
        }
        final int[] codePoints = new int[string.codePointCount(0, length)];
        int destIndex = 0;
        for (int index = 0; index < length; index = string.offsetByCodePoints(index, 1)) {
            codePoints[destIndex] = string.codePointAt(index);
            destIndex++;
        }
        return codePoints;
    }

    public static String[] parseCsvString(final String text) {
        final int size = text.length();
        if (size == 0) {
            return null;
        }
        if (codePointCount(text) == 1) {
            return text.codePointAt(0) == Constants.CSV_SEPARATOR ? null : new String[] { text };
        }

        ArrayList<String> list = null;
        int start = 0;
        for (int pos = 0; pos < size; pos++) {
            final char c = text.charAt(pos);
            if (c == Constants.CSV_SEPARATOR) {
                // Skip empty entry.
                if (pos - start > 0) {
                    if (list == null) {
                        list = CollectionUtils.newArrayList();
                    }
                    list.add(text.substring(start, pos));
                }
                // Skip comma
                start = pos + 1;
            } else if (c == Constants.CSV_ESCAPE) {
                // Skip escape character and escaped character.
                pos++;
            }
        }
        final String remain = (size - start > 0) ? text.substring(start) : null;
        if (list == null) {
            return remain != null ? new String[] { remain } : null;
        }
        if (remain != null) {
            list.add(remain);
        }
        return list.toArray(new String[list.size()]);
    }
}
