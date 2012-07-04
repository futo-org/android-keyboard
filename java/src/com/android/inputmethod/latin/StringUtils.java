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

public class StringUtils {
    private StringUtils() {
        // This utility class is not publicly instantiable.
    }

    public static int codePointCount(String text) {
        if (TextUtils.isEmpty(text)) return 0;
        return text.codePointCount(0, text.length());
    }

    public static boolean containsInArray(String key, String[] array) {
        for (final String element : array) {
            if (key.equals(element)) return true;
        }
        return false;
    }

    public static boolean containsInCsv(String key, String csv) {
        if (TextUtils.isEmpty(csv)) return false;
        return containsInArray(key, csv.split(","));
    }

    public static String appendToCsvIfNotExists(String key, String csv) {
        if (TextUtils.isEmpty(csv)) return key;
        if (containsInCsv(key, csv)) return csv;
        return csv + "," + key;
    }

    public static String removeFromCsvIfExists(String key, String csv) {
        if (TextUtils.isEmpty(csv)) return "";
        final String[] elements = csv.split(",");
        if (!containsInArray(key, elements)) return csv;
        final ArrayList<String> result = new ArrayList<String>(elements.length - 1);
        for (final String element : elements) {
            if (!key.equals(element)) result.add(element);
        }
        return TextUtils.join(",", result);
    }

    /**
     * Returns true if a and b are equal ignoring the case of the character.
     * @param a first character to check
     * @param b second character to check
     * @return {@code true} if a and b are equal, {@code false} otherwise.
     */
    public static boolean equalsIgnoreCase(char a, char b) {
        // Some language, such as Turkish, need testing both cases.
        return a == b
                || Character.toLowerCase(a) == Character.toLowerCase(b)
                || Character.toUpperCase(a) == Character.toUpperCase(b);
    }

    /**
     * Returns true if a and b are equal ignoring the case of the characters, including if they are
     * both null.
     * @param a first CharSequence to check
     * @param b second CharSequence to check
     * @return {@code true} if a and b are equal, {@code false} otherwise.
     */
    public static boolean equalsIgnoreCase(CharSequence a, CharSequence b) {
        if (a == b)
            return true;  // including both a and b are null.
        if (a == null || b == null)
            return false;
        final int length = a.length();
        if (length != b.length())
            return false;
        for (int i = 0; i < length; i++) {
            if (!equalsIgnoreCase(a.charAt(i), b.charAt(i)))
                return false;
        }
        return true;
    }

    /**
     * Returns true if a and b are equal ignoring the case of the characters, including if a is null
     * and b is zero length.
     * @param a CharSequence to check
     * @param b character array to check
     * @param offset start offset of array b
     * @param length length of characters in array b
     * @return {@code true} if a and b are equal, {@code false} otherwise.
     * @throws IndexOutOfBoundsException
     *   if {@code offset < 0 || length < 0 || offset + length > data.length}.
     * @throws NullPointerException if {@code b == null}.
     */
    public static boolean equalsIgnoreCase(CharSequence a, char[] b, int offset, int length) {
        if (offset < 0 || length < 0 || length > b.length - offset)
            throw new IndexOutOfBoundsException("array.length=" + b.length + " offset=" + offset
                    + " length=" + length);
        if (a == null)
            return length == 0;  // including a is null and b is zero length.
        if (a.length() != length)
            return false;
        for (int i = 0; i < length; i++) {
            if (!equalsIgnoreCase(a.charAt(i), b[offset + i]))
                return false;
        }
        return true;
    }

    /**
     * Returns true if cs contains any upper case characters.
     *
     * @param cs the CharSequence to check
     * @return {@code true} if cs contains any upper case characters, {@code false} otherwise.
     */
    public static boolean hasUpperCase(final CharSequence cs) {
        final int length = cs.length();
        for (int i = 0, cp = 0; i < length; i += Character.charCount(cp)) {
            cp = Character.codePointAt(cs, i);
            if (Character.isUpperCase(cp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove duplicates from an array of strings.
     *
     * This method will always keep the first occurrence of all strings at their position
     * in the array, removing the subsequent ones.
     */
    public static void removeDupes(final ArrayList<CharSequence> suggestions) {
        if (suggestions.size() < 2) return;
        int i = 1;
        // Don't cache suggestions.size(), since we may be removing items
        while (i < suggestions.size()) {
            final CharSequence cur = suggestions.get(i);
            // Compare each suggestion with each previous suggestion
            for (int j = 0; j < i; j++) {
                CharSequence previous = suggestions.get(j);
                if (TextUtils.equals(cur, previous)) {
                    suggestions.remove(i);
                    i--;
                    break;
                }
            }
            i++;
        }
    }

    public static String toTitleCase(String s, Locale locale) {
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

    public static int[] toCodePointArray(final String string) {
        final char[] characters = string.toCharArray();
        final int length = characters.length;
        final int[] codePoints = new int[Character.codePointCount(characters, 0, length)];
        int codePoint = Character.codePointAt(characters, 0);
        int dsti = 0;
        for (int srci = Character.charCount(codePoint);
                srci < length; srci += Character.charCount(codePoint), ++dsti) {
            codePoints[dsti] = codePoint;
            codePoint = Character.codePointAt(characters, srci);
        }
        codePoints[dsti] = codePoint;
        return codePoints;
    }
}
