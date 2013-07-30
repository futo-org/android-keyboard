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

package com.android.inputmethod.latin.utils;

import com.android.inputmethod.annotations.UsedForTesting;

import java.util.ArrayList;

/**
 * Utility methods for parsing and serializing Comma-Separated Values. The public APIs of this
 * utility class are {@link #split(String)}, {@link #split(int,String)}, {@link #join(String...)},
 * {@link #join(int,String...)}, and {@link #join(int,int[],String...)}.
 *
 * This class implements CSV parsing and serializing methods conforming to RFC 4180 with an
 * exception:
 *  These methods can't handle new line code escaped in double quotes.
 */
@UsedForTesting
public final class CsvUtils {
    private CsvUtils() {
        // This utility class is not publicly instantiable.
    }

    public static final int SPLIT_FLAGS_NONE = 0x0;
    /**
     * A flag for {@link #split(int,String)}. If this flag is specified, the method will trim
     * spaces around fields before splitting. Note that this behavior doesn't conform to RFC 4180.
     */
    public static final int SPLIT_FLAGS_TRIM_SPACES  = 0x1;

    public static final int JOIN_FLAGS_NONE = 0x0;
    /**
     * A flag for {@link #join(int,String...)} and {@link #join(int,int[],String...)}. If this
     * flag is specified, these methods surround each field with double quotes before joining.
     */
    public static final int JOIN_FLAGS_ALWAYS_QUOTED = 0x1;
    /**
     * A flag for {@link #join(int,String...)} and {@link #join(int,int[],String...)}. If this
     * flag is specified, these methods add an extra space just after the comma separator. Note that
     * this behavior doesn't conform to RFC 4180.
     */
    public static final int JOIN_FLAGS_EXTRA_SPACE   = 0x2;

    // Note that none of these characters match high or low surrogate characters, so we need not
    // take care of matching by code point.
    private static final char COMMA = ',';
    private static final char SPACE = ' ';
    private static final char QUOTE = '"';

    @SuppressWarnings("serial")
    public static class CsvParseException extends RuntimeException {
        public CsvParseException(final String message) {
            super(message);
        }
    }

    /**
     * Find the first non-space character in the text.
     *
     * @param text the text to be searched.
     * @param fromIndex the index to start the search from, inclusive.
     * @return the index of the first occurrence of the non-space character in the
     * <code>text</code> that is greater than or equal to <code>fromIndex</code>, or the length of
     * the <code>text</code> if the character does not occur.
     */
    private static int indexOfNonSpace(final String text, final int fromIndex) {
        final int length = text.length();
        if (fromIndex < 0 || fromIndex > length) {
            throw new IllegalArgumentException("text=" + text + " fromIndex=" + fromIndex);
        }
        int index = fromIndex;
        while (index < length && text.charAt(index) == SPACE) {
            index++;
        }
        return index;
    }

    /**
     * Find the last non-space character in the text.
     *
     * @param text the text to be searched.
     * @param fromIndex the index to start the search from, exclusive.
     * @param toIndex the index to end the search at, inclusive. Usually <code>toIndex</code>
     * points a non-space character.
     * @return the index of the last occurrence of the non-space character in the
     * <code>text</code>, exclusive. It is less than <code>fromIndex</code> and greater than
     * <code>toIndex</code>, or <code>toIndex</code> if the character does not occur.
     */
    private static int lastIndexOfNonSpace(final String text, final int fromIndex,
            final int toIndex) {
        if (toIndex < 0 || fromIndex > text.length() || fromIndex < toIndex) {
            throw new IllegalArgumentException(
                    "text=" + text + " fromIndex=" + fromIndex + " toIndex=" + toIndex);
        }
        int index = fromIndex;
        while (index > toIndex && text.charAt(index - 1) == SPACE) {
            index--;
        }
        return index;
    }

    /**
     * Find the index of a comma separator. The search takes account of quoted fields and escape
     * quotes.
     *
     * @param text the text to be searched.
     * @param fromIndex the index to start the search from, inclusive.
     * @return the index of the comma separator, exclusive.
     */
    private static int indexOfSeparatorComma(final String text, final int fromIndex) {
        final int length = text.length();
        if (fromIndex < 0 || fromIndex > length) {
            throw new IllegalArgumentException("text=" + text + " fromIndex=" + fromIndex);
        }
        final boolean isQuoted = (length - fromIndex > 0 && text.charAt(fromIndex) == QUOTE);
        for (int index = fromIndex + (isQuoted ? 1 : 0); index < length; index++) {
            final char c = text.charAt(index);
            if (c == COMMA && !isQuoted) {
                return index;
            }
            if (c == QUOTE) {
                final int nextIndex = index + 1;
                if (nextIndex < length && text.charAt(nextIndex) == QUOTE) {
                    // Quoted quote.
                    index = nextIndex;
                    continue;
                }
                // Closing quote.
                final int endIndex = text.indexOf(COMMA, nextIndex);
                return endIndex < 0 ? length : endIndex;
            }
        }
        return length;
    }

    /**
     * Removing any enclosing QUOTEs (U+0022), and convert any two consecutive QUOTEs into
     * one QUOTE.
     *
     * @param text the CSV field text that may have enclosing QUOTEs and escaped QUOTE character.
     * @return the text that has been removed enclosing quotes and converted two consecutive QUOTEs
     * into one QUOTE.
     */
    @UsedForTesting
    /* private */ static String unescapeField(final String text) {
        StringBuilder sb = null;
        final int length = text.length();
        final boolean isQuoted = (length > 0 && text.charAt(0) == QUOTE);
        int start = isQuoted ? 1 : 0;
        int end = start;
        while (start <= length && (end = text.indexOf(QUOTE, start)) >= start) {
            final int nextIndex = end + 1;
            if (nextIndex == length && isQuoted) {
                // Closing quote.
                break;
            }
            if (nextIndex < length && text.charAt(nextIndex) == QUOTE) {
                if (!isQuoted) {
                    throw new CsvParseException("Escaped quote in text");
                }
                // Quoted quote.
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.append(text.substring(start, nextIndex));
                start = nextIndex + 1;
            } else {
                throw new CsvParseException(
                        isQuoted ? "Raw quote in quoted text" : "Raw quote in text");
            }
        }
        if (end < 0 && isQuoted) {
            throw new CsvParseException("Unterminated quote");
        }
        if (end < 0) {
            end = length;
        }
        if (sb != null && start < length) {
            sb.append(text.substring(start, end));
        }
        return sb == null ? text.substring(start, end) : sb.toString();
    }

    /**
     * Split the CSV text into fields. The leading and trailing spaces of the each field can be
     * trimmed optionally.
     *
     * @param splitFlags flags for split behavior. {@link #SPLIT_FLAGS_TRIM_SPACES} will trim
     * spaces around each fields.
     * @param line the text of CSV fields.
     * @return the array of unescaped CVS fields.
     * @throws CsvParseException
     */
    @UsedForTesting
    public static String[] split(final int splitFlags, final String line) throws CsvParseException {
        final boolean trimSpaces = (splitFlags & SPLIT_FLAGS_TRIM_SPACES) != 0;
        final ArrayList<String> fields = CollectionUtils.newArrayList();
        final int length = line.length();
        int start = 0;
        do {
            final int csvStart = trimSpaces ? indexOfNonSpace(line, start) : start;
            final int end = indexOfSeparatorComma(line, csvStart);
            final int csvEnd = trimSpaces ? lastIndexOfNonSpace(line, end, csvStart) : end;
            final String csvText = unescapeField(line.substring(csvStart, csvEnd));
            fields.add(csvText);
            start = end + 1;
        } while (start <= length);
        return fields.toArray(new String[fields.size()]);
    }

    @UsedForTesting
    public static String[] split(final String line) throws CsvParseException {
        return split(SPLIT_FLAGS_NONE, line);
    }

    /**
     * Convert the raw CSV field text to the escaped text. It adds enclosing QUOTEs (U+0022) if the
     * raw value contains any QUOTE or comma. Also it converts any QUOTE character into two
     * consecutive QUOTE characters.
     *
     * @param text the raw CSV field text to be escaped.
     * @param alwaysQuoted true if the escaped text should always be enclosed by QUOTEs.
     * @return the escaped text.
     */
    @UsedForTesting
    /* private */ static String escapeField(final String text, final boolean alwaysQuoted) {
        StringBuilder sb = null;
        boolean needsQuoted = alwaysQuoted;
        final int length = text.length();
        int indexToBeAppended = 0;
        for (int index = indexToBeAppended; index < length; index++) {
            final char c = text.charAt(index);
            if (c == COMMA) {
                needsQuoted = true;
            } else if (c == QUOTE) {
                needsQuoted = true;
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.append(text.substring(indexToBeAppended, index));
                indexToBeAppended = index + 1;
                sb.append(QUOTE); // escaping quote.
                sb.append(QUOTE); // escaped quote.
            }
        }
        if (sb != null && indexToBeAppended < length) {
            sb.append(text.substring(indexToBeAppended));
        }
        final String escapedText = (sb == null) ? text : sb.toString();
        return needsQuoted ? QUOTE + escapedText + QUOTE : escapedText;
    }

    private static final String SPACES = "                    ";

    private static void padToColumn(final StringBuilder sb, final int column) {
        int padding;
        while ((padding = column - sb.length()) > 0) {
            final String spaces = SPACES.substring(0, Math.min(padding, SPACES.length()));
            sb.append(spaces);
        }
    }

    /**
     * Join CSV text fields with comma. The column positions of the fields can be specified
     * optionally. Surround each fields with double quotes before joining.
     *
     * @param joinFlags flags for join behavior. {@link #JOIN_FLAGS_EXTRA_SPACE} will add an extra
     * space after each comma separator. {@link #JOIN_FLAGS_ALWAYS_QUOTED} will always add
     * surrounding quotes to each element.
     * @param columnPositions the array of column positions of the fields. It can be shorter than
     * <code>fields</code> or null. Note that specifying the array column positions of the fields
     * doesn't conform to RFC 4180.
     * @param fields the CSV text fields.
     * @return the string of the joined and escaped <code>fields</code>.
     */
    @UsedForTesting
    public static String join(final int joinFlags, final int columnPositions[],
            final String... fields) {
        final boolean alwaysQuoted = (joinFlags & JOIN_FLAGS_ALWAYS_QUOTED) != 0;
        final String separator = COMMA + ((joinFlags & JOIN_FLAGS_EXTRA_SPACE) != 0 ? " " : "");
        final StringBuilder sb = new StringBuilder();
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) {
                sb.append(separator);
            }
            if (columnPositions != null && index < columnPositions.length) {
                padToColumn(sb, columnPositions[index]);
            }
            final String escapedText = escapeField(fields[index], alwaysQuoted);
            sb.append(escapedText);
        }
        return sb.toString();
    }

    @UsedForTesting
    public static String join(final int joinFlags, final String... fields) {
        return join(joinFlags, null, fields);
    }

    @UsedForTesting
    public static String join(final String... fields) {
        return join(JOIN_FLAGS_NONE, null, fields);
    }
}
