/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.content.res.Resources;
import android.text.TextUtils;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Utils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * String parser of moreKeys attribute of Key.
 * The string is comma separated texts each of which represents one "more key".
 * - String resource can be embedded into specification @string/name. This is done before parsing
 *   comma.
 * Each "more key" specification is one of the following:
 * - A single letter (Letter)
 * - Label optionally followed by keyOutputText or code (keyLabel|keyOutputText).
 * - Icon followed by keyOutputText or code (@icon/icon_name|@integer/key_code)
 * Special character, comma ',' backslash '\', and bar '|' can be escaped by '\' character.
 * Note that the character '@' and '\' are also parsed by XML parser and CSV parser as well.
 * See {@link KeyboardIconsSet} about icon_name.
 */
public class KeySpecParser {
    private static final boolean DEBUG = LatinImeLogger.sDBG;

    private static final int MAX_STRING_REFERENCE_INDIRECTION = 10;

    // Constants for parsing.
    private static int COMMA = ',';
    private static final char ESCAPE_CHAR = '\\';
    private static final char PREFIX_AT = '@';
    private static final char SUFFIX_SLASH = '/';
    private static final String PREFIX_STRING = PREFIX_AT + "string" + SUFFIX_SLASH;
    private static final char LABEL_END = '|';
    private static final String PREFIX_ICON = PREFIX_AT + "icon" + SUFFIX_SLASH;
    private static final String PREFIX_CODE = PREFIX_AT + "integer" + SUFFIX_SLASH;
    private static final String ADDITIONAL_MORE_KEY_MARKER = "%";

    private KeySpecParser() {
        // Intentional empty constructor for utility class.
    }

    private static boolean hasIcon(String moreKeySpec) {
        if (moreKeySpec.startsWith(PREFIX_ICON)) {
            final int end = indexOfLabelEnd(moreKeySpec, 0);
            if (end > 0) {
                return true;
            }
            throw new KeySpecParserError("outputText or code not specified: " + moreKeySpec);
        }
        return false;
    }

    private static boolean hasCode(String moreKeySpec) {
        final int end = indexOfLabelEnd(moreKeySpec, 0);
        if (end > 0 && end + 1 < moreKeySpec.length()
                && moreKeySpec.substring(end + 1).startsWith(PREFIX_CODE)) {
            return true;
        }
        return false;
    }

    private static String parseEscape(String text) {
        if (text.indexOf(ESCAPE_CHAR) < 0) {
            return text;
        }
        final int length = text.length();
        final StringBuilder sb = new StringBuilder();
        for (int pos = 0; pos < length; pos++) {
            final char c = text.charAt(pos);
            if (c == ESCAPE_CHAR && pos + 1 < length) {
                // Skip escape char
                pos++;
                sb.append(text.charAt(pos));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int indexOfLabelEnd(String moreKeySpec, int start) {
        if (moreKeySpec.indexOf(ESCAPE_CHAR, start) < 0) {
            final int end = moreKeySpec.indexOf(LABEL_END, start);
            if (end == 0) {
                throw new KeySpecParserError(LABEL_END + " at " + start + ": " + moreKeySpec);
            }
            return end;
        }
        final int length = moreKeySpec.length();
        for (int pos = start; pos < length; pos++) {
            final char c = moreKeySpec.charAt(pos);
            if (c == ESCAPE_CHAR && pos + 1 < length) {
                // Skip escape char
                pos++;
            } else if (c == LABEL_END) {
                return pos;
            }
        }
        return -1;
    }

    public static String getLabel(String moreKeySpec) {
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

    public static String getOutputText(String moreKeySpec) {
        if (hasCode(moreKeySpec)) {
            return null;
        }
        final int end = indexOfLabelEnd(moreKeySpec, 0);
        if (end > 0) {
            if (indexOfLabelEnd(moreKeySpec, end + 1) >= 0) {
                    throw new KeySpecParserError("Multiple " + LABEL_END + ": "
                            + moreKeySpec);
            }
            final String outputText = parseEscape(
                    moreKeySpec.substring(end + /* LABEL_END */1));
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
        return (Utils.codePointCount(label) == 1) ? null : label;
    }

    public static int getCode(Resources res, String moreKeySpec) {
        if (hasCode(moreKeySpec)) {
            final int end = indexOfLabelEnd(moreKeySpec, 0);
            if (indexOfLabelEnd(moreKeySpec, end + 1) >= 0) {
                throw new KeySpecParserError("Multiple " + LABEL_END + ": " + moreKeySpec);
            }
            final int resId = getResourceId(res,
                    moreKeySpec.substring(end + /* LABEL_END */1 + /* PREFIX_AT */1),
                    R.string.english_ime_name);
            final int code = res.getInteger(resId);
            return code;
        }
        if (indexOfLabelEnd(moreKeySpec, 0) > 0) {
            return Keyboard.CODE_OUTPUT_TEXT;
        }
        final String label = getLabel(moreKeySpec);
        // Code is automatically generated for one letter label.
        if (Utils.codePointCount(label) == 1) {
            return label.codePointAt(0);
        }
        return Keyboard.CODE_OUTPUT_TEXT;
    }

    public static int getIconId(String moreKeySpec) {
        if (hasIcon(moreKeySpec)) {
            final int end = moreKeySpec.indexOf(LABEL_END, PREFIX_ICON.length());
            final String name = moreKeySpec.substring(PREFIX_ICON.length(), end);
            return KeyboardIconsSet.getIconId(name);
        }
        return KeyboardIconsSet.ICON_UNDEFINED;
    }

    public static String[] insertAddtionalMoreKeys(String[] moreKeys, String[] additionalMoreKeys) {
        final int moreKeysCount = (moreKeys != null) ? moreKeys.length : 0;
        final int additionalCount = (additionalMoreKeys != null) ? additionalMoreKeys.length : 0;
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
                        out = new ArrayList<String>(moreKeyIndex);
                        for (int i = 0; i < moreKeyIndex; i++) {
                            out.add(moreKeys[i]);
                        }
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
            out = new ArrayList<String>(additionalCount + moreKeysCount);
            for (int i = additionalIndex; i < additionalCount; i++) {
                out.add(additionalMoreKeys[i]);
            }
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
            out = new ArrayList<String>(moreKeysCount);
            for (int i = 0; i < moreKeysCount; i++) {
                out.add(moreKeys[i]);
            }
            for (int i = additionalIndex; i < additionalCount; i++) {
                out.add(additionalMoreKeys[additionalIndex]);
            }
        }
        if (out != null) {
            return out.size() > 0 ? out.toArray(new String[out.size()]) : null;
        } else {
            return moreKeys;
        }
    }

    @SuppressWarnings("serial")
    public static class KeySpecParserError extends RuntimeException {
        public KeySpecParserError(String message) {
            super(message);
        }
    }

    private static int getResourceId(Resources res, String name, int packageNameResId) {
        String packageName = res.getResourcePackageName(packageNameResId);
        int resId = res.getIdentifier(name, null, packageName);
        if (resId == 0) {
            throw new RuntimeException("Unknown resource: " + name);
        }
        return resId;
    }

    private static String resolveStringResource(String rawText, Resources res,
            int packageNameResId) {
        int level = 0;
        String text = rawText;
        StringBuilder sb;
        do {
            level++;
            if (level >= MAX_STRING_REFERENCE_INDIRECTION) {
                throw new RuntimeException("too many @string/resource indirection: " + text);
            }

            final int size = text.length();
            if (size < PREFIX_STRING.length()) {
                return text;
            }

            sb = null;
            for (int pos = 0; pos < size; pos++) {
                final char c = text.charAt(pos);
                if (c == PREFIX_AT && text.startsWith(PREFIX_STRING, pos)) {
                    if (sb == null) {
                        sb = new StringBuilder(text.substring(0, pos));
                    }
                    final int end = searchResourceNameEnd(text, pos + PREFIX_STRING.length());
                    final String resName = text.substring(pos + 1, end);
                    final int resId = getResourceId(res, resName, packageNameResId);
                    sb.append(res.getString(resId));
                    pos = end - 1;
                } else if (c == ESCAPE_CHAR) {
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

    private static int searchResourceNameEnd(String text, int start) {
        final int size = text.length();
        for (int pos = start; pos < size; pos++) {
            final char c = text.charAt(pos);
            // String resource name should be consisted of [a-z_0-9].
            if ((c >= 'a' && c <= 'z') || c == '_' || (c >= '0' && c <= '9')) {
                continue;
            }
            return pos;
        }
        return size;
    }

    public static String[] parseCsvString(String rawText, Resources res, int packageNameResId) {
        final String text = resolveStringResource(rawText, res, packageNameResId);
        final int size = text.length();
        if (size == 0) {
            return null;
        }
        if (Utils.codePointCount(text) == 1) {
            return text.codePointAt(0) == COMMA ? null : new String[] { text };
        }

        ArrayList<String> list = null;
        int start = 0;
        for (int pos = 0; pos < size; pos++) {
            final char c = text.charAt(pos);
            if (c == COMMA) {
                // Skip empty entry.
                if (pos - start > 0) {
                    if (list == null) {
                        list = new ArrayList<String>();
                    }
                    list.add(text.substring(start, pos));
                }
                // Skip comma
                start = pos + 1;
            } else if (c == ESCAPE_CHAR) {
                // Skip escape character and escaped character.
                pos++;
            }
        }
        final String remain = (size - start > 0) ? text.substring(start) : null;
        if (list == null) {
            return remain != null ? new String[] { remain } : null;
        } else {
            if (remain != null) {
                list.add(remain);
            }
            return list.toArray(new String[list.size()]);
        }
    }
}
