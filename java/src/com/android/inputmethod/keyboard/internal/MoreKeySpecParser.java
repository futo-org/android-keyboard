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
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Utils;

import java.util.ArrayList;

/**
 * String parser of moreKeys attribute of Key.
 * The string is comma separated texts each of which represents one "more key".
 * Each "more key" specification is one of the following:
 * - A single letter (Letter)
 * - Label optionally followed by keyOutputText or code (keyLabel|keyOutputText).
 * - Icon followed by keyOutputText or code (@icon/icon_name|@integer/key_code)
 * Special character, comma ',' backslash '\', and bar '|' can be escaped by '\'
 * character.
 * Note that the character '@' and '\' are also parsed by XML parser and CSV parser as well.
 * See {@link KeyboardIconsSet} about icon_number.
 */
public class MoreKeySpecParser {
    private static final char LABEL_END = '|';
    private static final String PREFIX_ICON = Utils.PREFIX_AT + "icon" + Utils.SUFFIX_SLASH;
    private static final String PREFIX_CODE = Utils.PREFIX_AT + "integer" + Utils.SUFFIX_SLASH;

    private MoreKeySpecParser() {
        // Intentional empty constructor for utility class.
    }

    private static boolean hasIcon(String moreKeySpec) {
        if (moreKeySpec.startsWith(PREFIX_ICON)) {
            final int end = indexOfLabelEnd(moreKeySpec, 0);
            if (end > 0) {
                return true;
            }
            throw new MoreKeySpecParserError("outputText or code not specified: " + moreKeySpec);
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
        if (text.indexOf(Utils.ESCAPE_CHAR) < 0) {
            return text;
        }
        final int length = text.length();
        final StringBuilder sb = new StringBuilder();
        for (int pos = 0; pos < length; pos++) {
            final char c = text.charAt(pos);
            if (c == Utils.ESCAPE_CHAR && pos + 1 < length) {
                sb.append(text.charAt(++pos));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int indexOfLabelEnd(String moreKeySpec, int start) {
        if (moreKeySpec.indexOf(Utils.ESCAPE_CHAR, start) < 0) {
            final int end = moreKeySpec.indexOf(LABEL_END, start);
            if (end == 0) {
                throw new MoreKeySpecParserError(LABEL_END + " at " + start + ": " + moreKeySpec);
            }
            return end;
        }
        final int length = moreKeySpec.length();
        for (int pos = start; pos < length; pos++) {
            final char c = moreKeySpec.charAt(pos);
            if (c == Utils.ESCAPE_CHAR && pos + 1 < length) {
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
            throw new MoreKeySpecParserError("Empty label: " + moreKeySpec);
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
                    throw new MoreKeySpecParserError("Multiple " + LABEL_END + ": "
                            + moreKeySpec);
            }
            final String outputText = parseEscape(
                    moreKeySpec.substring(end + /* LABEL_END */1));
            if (!TextUtils.isEmpty(outputText)) {
                return outputText;
            }
            throw new MoreKeySpecParserError("Empty outputText: " + moreKeySpec);
        }
        final String label = getLabel(moreKeySpec);
        if (label == null) {
            throw new MoreKeySpecParserError("Empty label: " + moreKeySpec);
        }
        // Code is automatically generated for one letter label. See {@link getCode()}.
        return (label.length() == 1) ? null : label;
    }

    public static int getCode(Resources res, String moreKeySpec) {
        if (hasCode(moreKeySpec)) {
            final int end = indexOfLabelEnd(moreKeySpec, 0);
            if (indexOfLabelEnd(moreKeySpec, end + 1) >= 0) {
                throw new MoreKeySpecParserError("Multiple " + LABEL_END + ": " + moreKeySpec);
            }
            final int resId = Utils.getResourceId(res,
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
        if (label != null && label.length() == 1) {
            return label.charAt(0);
        }
        return Keyboard.CODE_OUTPUT_TEXT;
    }

    public static int getIconAttrId(String moreKeySpec) {
        if (hasIcon(moreKeySpec)) {
            final int end = moreKeySpec.indexOf(LABEL_END, PREFIX_ICON.length());
            final String name = moreKeySpec.substring(PREFIX_ICON.length(), end);
            return KeyboardIconsSet.getIconAttrId(name);
        }
        return KeyboardIconsSet.ICON_UNDEFINED;
    }

    @SuppressWarnings("serial")
    public static class MoreKeySpecParserError extends RuntimeException {
        public MoreKeySpecParserError(String message) {
            super(message);
        }
    }

    public interface CodeFilter {
        public boolean shouldFilterOut(int code);
    }

    public static final CodeFilter DIGIT_FILTER = new CodeFilter() {
        @Override
        public boolean shouldFilterOut(int code) {
            return Character.isDigit(code);
        }
    };

    public static String[] filterOut(Resources res, String[] moreKeys, CodeFilter filter) {
        if (moreKeys == null || moreKeys.length < 1) {
            return null;
        }
        if (moreKeys.length == 1 && filter.shouldFilterOut(getCode(res, moreKeys[0]))) {
            return null;
        }
        ArrayList<String> filtered = null;
        for (int i = 0; i < moreKeys.length; i++) {
            final String moreKeySpec = moreKeys[i];
            if (filter.shouldFilterOut(getCode(res, moreKeySpec))) {
                if (filtered == null) {
                    filtered = new ArrayList<String>();
                    for (int j = 0; j < i; j++) {
                        filtered.add(moreKeys[j]);
                    }
                }
            } else if (filtered != null) {
                filtered.add(moreKeySpec);
            }
        }
        if (filtered == null) {
            return moreKeys;
        }
        if (filtered.size() == 0) {
            return null;
        }
        return filtered.toArray(new String[filtered.size()]);
    }
}
