/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import static com.android.inputmethod.latin.Constants.CODE_OUTPUT_TEXT;
import static com.android.inputmethod.latin.Constants.CODE_UNSPECIFIED;

import android.text.TextUtils;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * The string parser of more keys specification.
 * The specification is comma separated texts each of which represents one "more key".
 * The specification might have label or string resource reference in it. These references are
 * expanded before parsing comma.
 * - Label reference should be a string representation of label (!text/label_name)
 * - String resource reference should be a string representation of resource (!text/resource_name)
 * Each "more key" specification is one of the following:
 * - Label optionally followed by keyOutputText or code (keyLabel|keyOutputText).
 * - Icon followed by keyOutputText or code (!icon/icon_name|!code/code_name)
 *   - Icon should be a string representation of icon (!icon/icon_name).
 *   - Code should be a code point presented by hexadecimal string prefixed with "0x", or a string
 *     representation of code (!code/code_name).
 * Special character, comma ',' backslash '\', and bar '|' can be escaped by '\' character.
 * Note that the '\' is also parsed by XML parser and CSV parser as well.
 * See {@link KeyboardIconsSet} about icon_name.
 */
public final class KeySpecParser {
    private static final boolean DEBUG = LatinImeLogger.sDBG;

    private static final int MAX_STRING_REFERENCE_INDIRECTION = 10;

    // Constants for parsing.
    private static final char COMMA = ',';
    private static final char BACKSLASH = '\\';
    private static final char VERTICAL_BAR = '|';
    private static final String PREFIX_TEXT = "!text/";
    static final String PREFIX_ICON = "!icon/";
    private static final String PREFIX_CODE = "!code/";
    private static final String PREFIX_HEX = "0x";
    private static final String ADDITIONAL_MORE_KEY_MARKER = "%";

    private KeySpecParser() {
        // Intentional empty constructor for utility class.
    }

    /**
     * Split the text containing multiple key specifications separated by commas into an array of
     * key specifications.
     * A key specification can contain a character escaped by the backslash character, including a
     * comma character.
     * Note that an empty key specification will be eliminated from the result array.
     *
     * @param text the text containing multiple key specifications.
     * @return an array of key specification text. Null if the specified <code>text</code> is empty
     * or has no key specifications.
     */
    public static String[] splitKeySpecs(final String text) {
        final int size = text.length();
        if (size == 0) {
            return null;
        }
        // Optimization for one-letter key specification.
        if (size == 1) {
            return text.charAt(0) == COMMA ? null : new String[] { text };
        }

        ArrayList<String> list = null;
        int start = 0;
        // The characters in question in this loop are COMMA and BACKSLASH. These characters never
        // match any high or low surrogate character. So it is OK to iterate through with char
        // index.
        for (int pos = 0; pos < size; pos++) {
            final char c = text.charAt(pos);
            if (c == COMMA) {
                // Skip empty entry.
                if (pos - start > 0) {
                    if (list == null) {
                        list = CollectionUtils.newArrayList();
                    }
                    list.add(text.substring(start, pos));
                }
                // Skip comma
                start = pos + 1;
            } else if (c == BACKSLASH) {
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

    private static boolean hasIcon(final String moreKeySpec) {
        return moreKeySpec.startsWith(PREFIX_ICON);
    }

    private static boolean hasCode(final String moreKeySpec) {
        final int end = indexOfLabelEnd(moreKeySpec, 0);
        if (end > 0 && end + 1 < moreKeySpec.length() && moreKeySpec.startsWith(
                PREFIX_CODE, end + 1)) {
            return true;
        }
        return false;
    }

    private static String parseEscape(final String text) {
        if (text.indexOf(BACKSLASH) < 0) {
            return text;
        }
        final int length = text.length();
        final StringBuilder sb = new StringBuilder();
        for (int pos = 0; pos < length; pos++) {
            final char c = text.charAt(pos);
            if (c == BACKSLASH && pos + 1 < length) {
                // Skip escape char
                pos++;
                sb.append(text.charAt(pos));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int indexOfLabelEnd(final String moreKeySpec, final int start) {
        if (moreKeySpec.indexOf(BACKSLASH, start) < 0) {
            final int end = moreKeySpec.indexOf(VERTICAL_BAR, start);
            if (end == 0) {
                throw new KeySpecParserError(VERTICAL_BAR + " at " + start + ": " + moreKeySpec);
            }
            return end;
        }
        final int length = moreKeySpec.length();
        for (int pos = start; pos < length; pos++) {
            final char c = moreKeySpec.charAt(pos);
            if (c == BACKSLASH && pos + 1 < length) {
                // Skip escape char
                pos++;
            } else if (c == VERTICAL_BAR) {
                return pos;
            }
        }
        return -1;
    }

    public static String getLabel(final String moreKeySpec) {
        if (hasIcon(moreKeySpec)) {
            return null;
        }
        final int end = indexOfLabelEnd(moreKeySpec, 0);
        final String label = (end > 0) ? parseEscape(moreKeySpec.substring(0, end))
                : parseEscape(moreKeySpec);
        if (TextUtils.isEmpty(label)) {
            throw new KeySpecParserError("Empty label: " + moreKeySpec);
        }
        return label;
    }

    private static String getOutputTextInternal(final String moreKeySpec) {
        final int end = indexOfLabelEnd(moreKeySpec, 0);
        if (end <= 0) {
            return null;
        }
        if (indexOfLabelEnd(moreKeySpec, end + 1) >= 0) {
            throw new KeySpecParserError("Multiple " + VERTICAL_BAR + ": " + moreKeySpec);
        }
        return parseEscape(moreKeySpec.substring(end + /* VERTICAL_BAR */1));
    }

    static String getOutputText(final String moreKeySpec) {
        if (hasCode(moreKeySpec)) {
            return null;
        }
        final String outputText = getOutputTextInternal(moreKeySpec);
        if (outputText != null) {
            if (StringUtils.codePointCount(outputText) == 1) {
                // If output text is one code point, it should be treated as a code.
                // See {@link #getCode(Resources, String)}.
                return null;
            }
            if (!TextUtils.isEmpty(outputText)) {
                return outputText;
            }
            throw new KeySpecParserError("Empty outputText: " + moreKeySpec);
        }
        final String label = getLabel(moreKeySpec);
        if (label == null) {
            throw new KeySpecParserError("Empty label: " + moreKeySpec);
        }
        // Code is automatically generated for one letter label. See {@link getCode()}.
        return (StringUtils.codePointCount(label) == 1) ? null : label;
    }

    static int getCode(final String moreKeySpec, final KeyboardCodesSet codesSet) {
        if (hasCode(moreKeySpec)) {
            final int end = indexOfLabelEnd(moreKeySpec, 0);
            if (indexOfLabelEnd(moreKeySpec, end + 1) >= 0) {
                throw new KeySpecParserError("Multiple " + VERTICAL_BAR + ": " + moreKeySpec);
            }
            return parseCode(moreKeySpec.substring(end + 1), codesSet, CODE_UNSPECIFIED);
        }
        final String outputText = getOutputTextInternal(moreKeySpec);
        if (outputText != null) {
            // If output text is one code point, it should be treated as a code.
            // See {@link #getOutputText(String)}.
            if (StringUtils.codePointCount(outputText) == 1) {
                return outputText.codePointAt(0);
            }
            return CODE_OUTPUT_TEXT;
        }
        final String label = getLabel(moreKeySpec);
        // Code is automatically generated for one letter label.
        if (StringUtils.codePointCount(label) == 1) {
            return label.codePointAt(0);
        }
        return CODE_OUTPUT_TEXT;
    }

    public static int parseCode(final String text, final KeyboardCodesSet codesSet,
            final int defCode) {
        if (text == null) return defCode;
        if (text.startsWith(PREFIX_CODE)) {
            return codesSet.getCode(text.substring(PREFIX_CODE.length()));
        } else if (text.startsWith(PREFIX_HEX)) {
            return Integer.parseInt(text.substring(PREFIX_HEX.length()), 16);
        } else {
            return Integer.parseInt(text);
        }
    }

    public static int getIconId(final String moreKeySpec) {
        if (moreKeySpec != null && hasIcon(moreKeySpec)) {
            final int end = moreKeySpec.indexOf(VERTICAL_BAR, PREFIX_ICON.length());
            final String name = (end < 0) ? moreKeySpec.substring(PREFIX_ICON.length())
                    : moreKeySpec.substring(PREFIX_ICON.length(), end);
            return KeyboardIconsSet.getIconId(name);
        }
        return KeyboardIconsSet.ICON_UNDEFINED;
    }

    private static <T> ArrayList<T> arrayAsList(final T[] array, final int start, final int end) {
        if (array == null) {
            throw new NullPointerException();
        }
        if (start < 0 || start > end || end > array.length) {
            throw new IllegalArgumentException();
        }

        final ArrayList<T> list = CollectionUtils.newArrayList(end - start);
        for (int i = start; i < end; i++) {
            list.add(array[i]);
        }
        return list;
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static String[] filterOutEmptyString(final String[] array) {
        if (array == null) {
            return EMPTY_STRING_ARRAY;
        }
        ArrayList<String> out = null;
        for (int i = 0; i < array.length; i++) {
            final String entry = array[i];
            if (TextUtils.isEmpty(entry)) {
                if (out == null) {
                    out = arrayAsList(array, 0, i);
                }
            } else if (out != null) {
                out.add(entry);
            }
        }
        if (out == null) {
            return array;
        }
        return out.toArray(new String[out.size()]);
    }

    public static String[] insertAdditionalMoreKeys(final String[] moreKeySpecs,
            final String[] additionalMoreKeySpecs) {
        final String[] moreKeys = filterOutEmptyString(moreKeySpecs);
        final String[] additionalMoreKeys = filterOutEmptyString(additionalMoreKeySpecs);
        final int moreKeysCount = moreKeys.length;
        final int additionalCount = additionalMoreKeys.length;
        ArrayList<String> out = null;
        int additionalIndex = 0;
        for (int moreKeyIndex = 0; moreKeyIndex < moreKeysCount; moreKeyIndex++) {
            final String moreKeySpec = moreKeys[moreKeyIndex];
            if (moreKeySpec.equals(ADDITIONAL_MORE_KEY_MARKER)) {
                if (additionalIndex < additionalCount) {
                    // Replace '%' marker with additional more key specification.
                    final String additionalMoreKey = additionalMoreKeys[additionalIndex];
                    if (out != null) {
                        out.add(additionalMoreKey);
                    } else {
                        moreKeys[moreKeyIndex] = additionalMoreKey;
                    }
                    additionalIndex++;
                } else {
                    // Filter out excessive '%' marker.
                    if (out == null) {
                        out = arrayAsList(moreKeys, 0, moreKeyIndex);
                    }
                }
            } else {
                if (out != null) {
                    out.add(moreKeySpec);
                }
            }
        }
        if (additionalCount > 0 && additionalIndex == 0) {
            // No '%' marker is found in more keys.
            // Insert all additional more keys to the head of more keys.
            if (DEBUG && out != null) {
                throw new RuntimeException("Internal logic error:"
                        + " moreKeys=" + Arrays.toString(moreKeys)
                        + " additionalMoreKeys=" + Arrays.toString(additionalMoreKeys));
            }
            out = arrayAsList(additionalMoreKeys, additionalIndex, additionalCount);
            for (int i = 0; i < moreKeysCount; i++) {
                out.add(moreKeys[i]);
            }
        } else if (additionalIndex < additionalCount) {
            // The number of '%' markers are less than additional more keys.
            // Append remained additional more keys to the tail of more keys.
            if (DEBUG && out != null) {
                throw new RuntimeException("Internal logic error:"
                        + " moreKeys=" + Arrays.toString(moreKeys)
                        + " additionalMoreKeys=" + Arrays.toString(additionalMoreKeys));
            }
            out = arrayAsList(moreKeys, 0, moreKeysCount);
            for (int i = additionalIndex; i < additionalCount; i++) {
                out.add(additionalMoreKeys[additionalIndex]);
            }
        }
        if (out == null && moreKeysCount > 0) {
            return moreKeys;
        } else if (out != null && out.size() > 0) {
            return out.toArray(new String[out.size()]);
        } else {
            return null;
        }
    }

    @SuppressWarnings("serial")
    public static final class KeySpecParserError extends RuntimeException {
        public KeySpecParserError(final String message) {
            super(message);
        }
    }

    public static String resolveTextReference(final String rawText,
            final KeyboardTextsSet textsSet) {
        int level = 0;
        String text = rawText;
        StringBuilder sb;
        do {
            level++;
            if (level >= MAX_STRING_REFERENCE_INDIRECTION) {
                throw new RuntimeException("too many @string/resource indirection: " + text);
            }

            final int prefixLen = PREFIX_TEXT.length();
            final int size = text.length();
            if (size < prefixLen) {
                return text;
            }

            sb = null;
            for (int pos = 0; pos < size; pos++) {
                final char c = text.charAt(pos);
                if (text.startsWith(PREFIX_TEXT, pos) && textsSet != null) {
                    if (sb == null) {
                        sb = new StringBuilder(text.substring(0, pos));
                    }
                    final int end = searchTextNameEnd(text, pos + prefixLen);
                    final String name = text.substring(pos + prefixLen, end);
                    sb.append(textsSet.getText(name));
                    pos = end - 1;
                } else if (c == BACKSLASH) {
                    if (sb != null) {
                        // Append both escape character and escaped character.
                        sb.append(text.substring(pos, Math.min(pos + 2, size)));
                    }
                    pos++;
                } else if (sb != null) {
                    sb.append(c);
                }
            }

            if (sb != null) {
                text = sb.toString();
            }
        } while (sb != null);
        return text;
    }

    private static int searchTextNameEnd(final String text, final int start) {
        final int size = text.length();
        for (int pos = start; pos < size; pos++) {
            final char c = text.charAt(pos);
            // Label name should be consisted of [a-zA-Z_0-9].
            if ((c >= 'a' && c <= 'z') || c == '_' || (c >= '0' && c <= '9')) {
                continue;
            }
            return pos;
        }
        return size;
    }

    public static int getIntValue(final String[] moreKeys, final String key,
            final int defaultValue) {
        if (moreKeys == null) {
            return defaultValue;
        }
        final int keyLen = key.length();
        boolean foundValue = false;
        int value = defaultValue;
        for (int i = 0; i < moreKeys.length; i++) {
            final String moreKeySpec = moreKeys[i];
            if (moreKeySpec == null || !moreKeySpec.startsWith(key)) {
                continue;
            }
            moreKeys[i] = null;
            try {
                if (!foundValue) {
                    value = Integer.parseInt(moreKeySpec.substring(keyLen));
                    foundValue = true;
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException(
                        "integer should follow after " + key + ": " + moreKeySpec);
            }
        }
        return value;
    }

    public static boolean getBooleanValue(final String[] moreKeys, final String key) {
        if (moreKeys == null) {
            return false;
        }
        boolean value = false;
        for (int i = 0; i < moreKeys.length; i++) {
            final String moreKeySpec = moreKeys[i];
            if (moreKeySpec == null || !moreKeySpec.equals(key)) {
                continue;
            }
            moreKeys[i] = null;
            value = true;
        }
        return value;
    }

    public static int toUpperCaseOfCodeForLocale(final int code, final boolean needsToUpperCase,
            final Locale locale) {
        if (!Constants.isLetterCode(code) || !needsToUpperCase) return code;
        final String text = new String(new int[] { code } , 0, 1);
        final String casedText = KeySpecParser.toUpperCaseOfStringForLocale(
                text, needsToUpperCase, locale);
        return StringUtils.codePointCount(casedText) == 1
                ? casedText.codePointAt(0) : CODE_UNSPECIFIED;
    }

    public static String toUpperCaseOfStringForLocale(final String text,
            final boolean needsToUpperCase, final Locale locale) {
        if (text == null || !needsToUpperCase) return text;
        return text.toUpperCase(locale);
    }
}
