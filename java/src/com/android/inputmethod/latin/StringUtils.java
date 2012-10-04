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

import com.android.inputmethod.keyboard.Keyboard; // For character constants

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
     * {@link TextUtils#CAP_MODE_CHARACTERS}, {@link TextUtils#CAP_MODE_WORDS}, and
     * {@link TextUtils#CAP_MODE_SENTENCES}.
     * @param locale The locale to consider for capitalization rules
     * @param hasSpaceBefore Whether we should consider there is a space inserted at the end of cs
     *
     * @return Returns the actual capitalization modes that can be in effect
     * at the current position, which is any combination of
     * {@link TextUtils#CAP_MODE_CHARACTERS}, {@link TextUtils#CAP_MODE_WORDS}, and
     * {@link TextUtils#CAP_MODE_SENTENCES}.
     */
    public static int getCapsMode(final CharSequence cs, final int reqModes, final Locale locale,
            final boolean hasSpaceBefore) {
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
        if (hasSpaceBefore) {
            i = cs.length() + 1;
        } else {
            for (i = cs.length(); i > 0; i--) {
                final char c = cs.charAt(i - 1);
                if (c != Keyboard.CODE_DOUBLE_QUOTE && c != Keyboard.CODE_SINGLE_QUOTE
                        && Character.getType(c) != Character.START_PUNCTUATION) {
                    break;
                }
            }
        }

        // We are now on the character that precedes any starting punctuation, so in the most
        // frequent case this will be whitespace or a letter, although it may occasionally be a
        // start of line, or some symbol.

        // Step 3 : Search for the start of a paragraph. From the starting point computed in step 2,
        // we go back over any space or tab char sitting there. We find the start of a paragraph
        // if the first char that's not a space or tab is a start of line (as in \n, start of text,
        // or some other similar characters).
        int j = i;
        char prevChar = Keyboard.CODE_SPACE;
        if (hasSpaceBefore) --j;
        while (j > 0) {
            prevChar = cs.charAt(j - 1);
            if (!Character.isSpaceChar(prevChar) && prevChar != Keyboard.CODE_TAB) break;
            j--;
        }
        if (j <= 0 || Character.isWhitespace(prevChar)) {
            // There are only spacing chars between the start of the paragraph and the cursor,
            // defined as a isWhitespace() char that is neither a isSpaceChar() nor a tab. Both
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
        // English is a special case in that "American typography" rules, which are the most common
        // in English, state that a sentence terminator immediately following a quotation mark
        // should be swapped with it and de-duplicated (included in the quotation mark),
        // e.g. <<Did he say, "let's go home?">>
        // No other language has such a rule as far as I know, instead putting inside the quotation
        // mark as the exact thing quoted and handling the surrounding punctuation independently,
        // e.g. <<Did he say, "let's go home"?>>
        // Hence, specifically for English, we treat this special case here.
        if (Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            for (; j > 0; j--) {
                // Here we look to go over any closing punctuation. This is because in dominant
                // variants of English, the final period is placed within double quotes and maybe
                // other closing punctuation signs. This is generally not true in other languages.
                final char c = cs.charAt(j - 1);
                if (c != Keyboard.CODE_DOUBLE_QUOTE && c != Keyboard.CODE_SINGLE_QUOTE
                        && Character.getType(c) != Character.END_PUNCTUATION) {
                    break;
                }
            }
        }

        if (j <= 0) return TextUtils.CAP_MODE_CHARACTERS & reqModes;
        char c = cs.charAt(--j);

        // We found the next interesting chunk of text ; next we need to determine if it's the
        // end of a sentence. If we have a question mark or an exclamation mark, it's the end of
        // a sentence. If it's neither, the only remaining case is the period so we get the opposite
        // case out of the way.
        if (c == Keyboard.CODE_QUESTION_MARK || c == Keyboard.CODE_EXCLAMATION_MARK) {
            return (TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_SENTENCES) & reqModes;
        }
        if (c != Keyboard.CODE_PERIOD || j <= 0) {
            return (TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS) & reqModes;
        }

        // We found out that we have a period. We need to determine if this is a full stop or
        // otherwise sentence-ending period, or an abbreviation like "e.g.". An abbreviation
        // looks like (\w\.){2,}
        // To find out, we will have a simple state machine with the following states :
        // START, WORD, PERIOD, ABBREVIATION
        // On START : (just before the first period)
        //           letter => WORD
        //           whitespace => end with no caps (it was a stand-alone period)
        //           otherwise => end with caps (several periods/symbols in a row)
        // On WORD : (within the word just before the first period)
        //           letter => WORD
        //           period => PERIOD
        //           otherwise => end with caps (it was a word with a full stop at the end)
        // On PERIOD : (period within a potential abbreviation)
        //           letter => LETTER
        //           otherwise => end with caps (it was not an abbreviation)
        // On LETTER : (letter within a potential abbreviation)
        //           letter => LETTER
        //           period => PERIOD
        //           otherwise => end with no caps (it was an abbreviation)
        // "Not an abbreviation" in the above chart essentially covers cases like "...yes.". This
        // should capitalize.

        final int START = 0;
        final int WORD = 1;
        final int PERIOD = 2;
        final int LETTER = 3;
        final int caps = (TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS
                | TextUtils.CAP_MODE_SENTENCES) & reqModes;
        final int noCaps = (TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS) & reqModes;
        int state = START;
        while (j > 0) {
            c = cs.charAt(--j);
            switch (state) {
            case START:
                if (Character.isLetter(c)) {
                    state = WORD;
                } else if (Character.isWhitespace(c)) {
                    return noCaps;
                } else {
                    return caps;
                }
                break;
            case WORD:
                if (Character.isLetter(c)) {
                    state = WORD;
                } else if (c == Keyboard.CODE_PERIOD) {
                    state = PERIOD;
                } else {
                    return caps;
                }
                break;
            case PERIOD:
                if (Character.isLetter(c)) {
                    state = LETTER;
                } else {
                    return caps;
                }
                break;
            case LETTER:
                if (Character.isLetter(c)) {
                    state = LETTER;
                } else if (c == Keyboard.CODE_PERIOD) {
                    state = PERIOD;
                } else {
                    return noCaps;
                }
            }
        }
        // Here we arrived at the start of the line. This should behave exactly like whitespace.
        return (START == state || LETTER == state) ? noCaps : caps;
    }
}
