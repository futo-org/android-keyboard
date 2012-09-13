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
        final ArrayList<String> result = CollectionUtils.newArrayList(elements.length - 1);
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
        if (length <= 0) {
            return new int[0];
        }
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

    /**
     * Determine what caps mode should be in effect at the current offset in
     * the text. Only the mode bits set in <var>reqModes</var> will be
     * checked. Note that the caps mode flags here are explicitly defined
     * to match those in {@link InputType}.
     *
     * This code is a straight copy of TextUtils.getCapsMode (modulo namespace and formatting
     * issues). This will change in the future as we simplify the code for our use and fix bugs.
     *
     * @param cs The text that should be checked for caps modes.
     * @param reqModes The modes to be checked: may be any combination of
     * {@link #CAP_MODE_CHARACTERS}, {@link #CAP_MODE_WORDS}, and
     * {@link #CAP_MODE_SENTENCES}.
     *
     * @return Returns the actual capitalization modes that can be in effect
     * at the current position, which is any combination of
     * {@link #CAP_MODE_CHARACTERS}, {@link #CAP_MODE_WORDS}, and
     * {@link #CAP_MODE_SENTENCES}.
     */
    public static int getCapsMode(CharSequence cs, int reqModes) {
        // Quick description of what we want to do:
        // CAP_MODE_CHARACTERS is always on.
        // CAP_MODE_WORDS is on if there is some whitespace before the cursor.
        // CAP_MODE_SENTENCES is on if there is some whitespace before the cursor, and the end
        //   of a sentence just before that.
        // We ignore opening parentheses and the like just before the cursor for purposes of
        // finding whitespace for WORDS and SENTENCES modes.
        // The end of a sentence ends with a period, question mark or exclamation mark. If it's
        // a period, it also needs not to be an abbreviation, which means it also needs to either
        // be immediately preceded by punctuation, or by a string of only letters with single
        // periods interleaved.

        // Step 1 : check for cap MODE_CHARACTERS. If it's looked for, it's always on.
        if ((reqModes & (TextUtils.CAP_MODE_WORDS | TextUtils.CAP_MODE_SENTENCES)) == 0) {
            // Here we are not looking for MODE_WORDS or MODE_SENTENCES, so since we already
            // evaluated MODE_CHARACTERS, we can return.
            return TextUtils.CAP_MODE_CHARACTERS & reqModes;
        }

        // Step 2 : Skip (ignore at the end of input) any opening punctuation. This includes
        // opening parentheses, brackets, opening quotes, everything that *opens* a span of
        // text in the linguistic sense. In RTL languages, this is still an opening sign, although
        // it may look like a right parenthesis for example. We also include double quote and
        // single quote since they aren't start punctuation in the unicode sense, but should still
        // be skipped for English. TODO: does this depend on the language?
        int i;
        for (i = cs.length(); i > 0; i--) {
            final char c = cs.charAt(i - 1);
            if (c != '"' && c != '\'' && Character.getType(c) != Character.START_PUNCTUATION) {
                break;
            }
        }

        // We are now on the character that precedes any starting punctuation, so in the most
        // frequent case this will be whitespace or a letter, although it may occasionally be a
        // start of line, or some symbol.

        // Step 3 : Search for the start of a paragraph. From the starting point computed in step 2,
        // we go back over any space or tab char sitting there. We find the start of a paragraph
        // if the first char that's not a space or tab is a start of line (as in, either \n or
        // start of text).
        int j = i;
        while (j > 0 && Character.isWhitespace(cs.charAt(j - 1))) {
            j--;
        }
        if (j == 0) {
            // There is only whitespace between the start of the text and the cursor. Both
            // MODE_WORDS and MODE_SENTENCES should be active.
            return (TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS
                    | TextUtils.CAP_MODE_SENTENCES) & reqModes;
        }
        if (i == j) {
            // If we don't have whitespace before index i, it means neither MODE_WORDS
            // nor mode sentences should be on so we can return right away.
            return TextUtils.CAP_MODE_CHARACTERS & reqModes;
        }
        if ((reqModes & TextUtils.CAP_MODE_SENTENCES) == 0) {
            // Here we know we have whitespace before the cursor (if not, we returned in the above
            // if i == j clause), so we need MODE_WORDS to be on. And we don't need to evaluate
            // MODE_SENTENCES so we can return right away.
            return (TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS) & reqModes;
        }
        // Please note that because of the reqModes & CAP_MODE_SENTENCES test a few lines above,
        // we know that MODE_SENTENCES is being requested.

        // Step 4 : Search for MODE_SENTENCES.
        for (; j > 0; j--) {
            // Here we look to go over any closing punctuation. This is because in dominant variants
            // of English, the final period is placed within double quotes and maybe other closing
            // punctuation signs.
            // TODO: this is wrong for almost everything except American typography rules for
            // English. It's wrong for British typography rules for English, it's wrong for French,
            // it's wrong for German, it's wrong for Spanish, and possibly everything else.
            // (note that American rules and British rules have nothing to do with en_US and en_GB,
            // as both rules are used in both countries - it's merely a name for the set of rules)
            final char c = cs.charAt(j - 1);
            if (c != '"' && c != '\'' && Character.getType(c) != Character.END_PUNCTUATION) {
                break;
            }
        }

        if (j <= 0) return TextUtils.CAP_MODE_CHARACTERS & reqModes;
        char c = cs.charAt(j - 1);
        if (c == '.' || c == '?' || c == '!') {
            // Here we found a marker for sentence end (we consider these to be one of
            // either . or ? or ! only). So this is probably the end of a sentence, but if we
            // found a period, we still want to check the case where this is a abbreviation
            // period rather than a full stop. To do this, we look for a period within a word
            // before the period we just found; if any, we take that to mean it was an
            // abbreviation.
            // A typical example of the above is "In the U.S. ", where the last period is
            // not a full stop and we should not capitalize.
            // TODO: the rule below is broken. In particular it fails for runs of periods,
            // whatever the reason. In the example "in the U.S..", the last period is a full
            // stop following the abbreviation period, and we should capitalize but we don't.
            // Likewise, "I don't know... " should capitalize, but fails to do so.
            if (c == '.') {
                for (int k = j - 2; k >= 0; k--) {
                    c = cs.charAt(k);
                    if (c == '.') {
                        return TextUtils.CAP_MODE_CHARACTERS & reqModes;
                    }
                    if (!Character.isLetter(c)) {
                        break;
                    }
                }
            }
            return (TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_SENTENCES) & reqModes;
        }
        return TextUtils.CAP_MODE_CHARACTERS & reqModes;
    }
}
