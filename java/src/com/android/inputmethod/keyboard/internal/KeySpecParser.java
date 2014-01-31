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

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.StringUtils;

/**
 * The string parser of the key specification.
 *
 * Each key specification is one of the following:
 * - Label optionally followed by keyOutputText (keyLabel|keyOutputText).
 * - Label optionally followed by code point (keyLabel|!code/code_name).
 * - Icon followed by keyOutputText (!icon/icon_name|keyOutputText).
 * - Icon followed by code point (!icon/icon_name|!code/code_name).
 * Label and keyOutputText are one of the following:
 * - Literal string.
 * - Label reference represented by (!text/label_name), see {@link KeyboardTextsSet}.
 * - String resource reference represented by (!text/resource_name), see {@link KeyboardTextsSet}.
 * Icon is represented by (!icon/icon_name), see {@link KeyboardIconsSet}.
 * Code is one of the following:
 * - Code point presented by hexadecimal string prefixed with "0x"
 * - Code reference represented by (!code/code_name), see {@link KeyboardCodesSet}.
 * Special character, comma ',' backslash '\', and bar '|' can be escaped by '\' character.
 * Note that the '\' is also parsed by XML parser and {@link MoreKeySpec#splitKeySpecs(String)}
 * as well.
 */
public final class KeySpecParser {
    // Constants for parsing.
    private static final char BACKSLASH = Constants.CODE_BACKSLASH;
    private static final char VERTICAL_BAR = Constants.CODE_VERTICAL_BAR;
    private static final String PREFIX_HEX = "0x";

    private KeySpecParser() {
        // Intentional empty constructor for utility class.
    }

    private static boolean hasIcon(final String keySpec) {
        return keySpec.startsWith(KeyboardIconsSet.PREFIX_ICON);
    }

    private static boolean hasCode(final String keySpec) {
        final int end = indexOfLabelEnd(keySpec, 0);
        if (end > 0 && end + 1 < keySpec.length() && keySpec.startsWith(
                KeyboardCodesSet.PREFIX_CODE, end + 1)) {
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

    private static int indexOfLabelEnd(final String keySpec, final int start) {
        if (keySpec.indexOf(BACKSLASH, start) < 0) {
            final int end = keySpec.indexOf(VERTICAL_BAR, start);
            if (end == 0) {
                throw new KeySpecParserError(VERTICAL_BAR + " at " + start + ": " + keySpec);
            }
            return end;
        }
        final int length = keySpec.length();
        for (int pos = start; pos < length; pos++) {
            final char c = keySpec.charAt(pos);
            if (c == BACKSLASH && pos + 1 < length) {
                // Skip escape char
                pos++;
            } else if (c == VERTICAL_BAR) {
                return pos;
            }
        }
        return -1;
    }

    public static String getLabel(final String keySpec) {
        if (hasIcon(keySpec)) {
            return null;
        }
        final int end = indexOfLabelEnd(keySpec, 0);
        final String label = (end > 0) ? parseEscape(keySpec.substring(0, end))
                : parseEscape(keySpec);
        if (label.isEmpty()) {
            throw new KeySpecParserError("Empty label: " + keySpec);
        }
        return label;
    }

    private static String getOutputTextInternal(final String keySpec) {
        final int end = indexOfLabelEnd(keySpec, 0);
        if (end <= 0) {
            return null;
        }
        if (indexOfLabelEnd(keySpec, end + 1) >= 0) {
            throw new KeySpecParserError("Multiple " + VERTICAL_BAR + ": " + keySpec);
        }
        return parseEscape(keySpec.substring(end + /* VERTICAL_BAR */1));
    }

    public static String getOutputText(final String keySpec) {
        if (hasCode(keySpec)) {
            return null;
        }
        final String outputText = getOutputTextInternal(keySpec);
        if (outputText != null) {
            if (StringUtils.codePointCount(outputText) == 1) {
                // If output text is one code point, it should be treated as a code.
                // See {@link #getCode(Resources, String)}.
                return null;
            }
            if (outputText.isEmpty()) {
                throw new KeySpecParserError("Empty outputText: " + keySpec);
            }
            return outputText;
        }
        final String label = getLabel(keySpec);
        if (label == null) {
            throw new KeySpecParserError("Empty label: " + keySpec);
        }
        // Code is automatically generated for one letter label. See {@link getCode()}.
        return (StringUtils.codePointCount(label) == 1) ? null : label;
    }

    public static int getCode(final String keySpec, final KeyboardCodesSet codesSet) {
        if (hasCode(keySpec)) {
            final int end = indexOfLabelEnd(keySpec, 0);
            if (indexOfLabelEnd(keySpec, end + 1) >= 0) {
                throw new KeySpecParserError("Multiple " + VERTICAL_BAR + ": " + keySpec);
            }
            return parseCode(keySpec.substring(end + 1), codesSet, CODE_UNSPECIFIED);
        }
        final String outputText = getOutputTextInternal(keySpec);
        if (outputText != null) {
            // If output text is one code point, it should be treated as a code.
            // See {@link #getOutputText(String)}.
            if (StringUtils.codePointCount(outputText) == 1) {
                return outputText.codePointAt(0);
            }
            return CODE_OUTPUT_TEXT;
        }
        final String label = getLabel(keySpec);
        // Code is automatically generated for one letter label.
        if (StringUtils.codePointCount(label) == 1) {
            return label.codePointAt(0);
        }
        return CODE_OUTPUT_TEXT;
    }

    public static int parseCode(final String text, final KeyboardCodesSet codesSet,
            final int defCode) {
        if (text == null) return defCode;
        if (text.startsWith(KeyboardCodesSet.PREFIX_CODE)) {
            return codesSet.getCode(text.substring(KeyboardCodesSet.PREFIX_CODE.length()));
        } else if (text.startsWith(PREFIX_HEX)) {
            return Integer.parseInt(text.substring(PREFIX_HEX.length()), 16);
        } else {
            return Integer.parseInt(text);
        }
    }

    public static int getIconId(final String keySpec) {
        if (keySpec != null && hasIcon(keySpec)) {
            final int end = keySpec.indexOf(
                    VERTICAL_BAR, KeyboardIconsSet.PREFIX_ICON.length());
            final String name = (end < 0)
                    ? keySpec.substring(KeyboardIconsSet.PREFIX_ICON.length())
                    : keySpec.substring(KeyboardIconsSet.PREFIX_ICON.length(), end);
            return KeyboardIconsSet.getIconId(name);
        }
        return KeyboardIconsSet.ICON_UNDEFINED;
    }

    @SuppressWarnings("serial")
    public static final class KeySpecParserError extends RuntimeException {
        public KeySpecParserError(final String message) {
            super(message);
        }
    }
}
