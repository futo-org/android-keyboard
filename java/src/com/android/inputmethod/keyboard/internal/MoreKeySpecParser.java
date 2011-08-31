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
import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.R;

import java.util.ArrayList;

/**
 * String parser of moreKeys attribute of Key.
 * The string is comma separated texts each of which represents one "more key".
 * Each "more key" specification is one of the following:
 * - A single letter (Letter)
 * - Label optionally followed by keyOutputText or code (keyLabel|keyOutputText).
 * - Icon followed by keyOutputText or code (@icon/icon_number|@integer/key_code)
 * Special character, comma ',' backslash '\', and bar '|' can be escaped by '\'
 * character.
 * Note that the character '@' and '\' are also parsed by XML parser and CSV parser as well.
 * See {@link KeyboardIconsSet} about icon_number.
 */
public class MoreKeySpecParser {
    private static final String TAG = MoreKeySpecParser.class.getSimpleName();

    private static final char ESCAPE = '\\';
    private static final String LABEL_END = "|";
    private static final String PREFIX_AT = "@";
    private static final String PREFIX_ICON = PREFIX_AT + "icon/";
    private static final String PREFIX_CODE = PREFIX_AT + "integer/";

    private MoreKeySpecParser() {
        // Intentional empty constructor for utility class.
    }

    private static boolean hasIcon(String moreKeySpec) {
        if (moreKeySpec.startsWith(PREFIX_ICON)) {
            final int end = indexOfLabelEnd(moreKeySpec, 0);
            if (end > 0)
                return true;
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
        if (text.indexOf(ESCAPE) < 0)
            return text;
        final int length = text.length();
        final StringBuilder sb = new StringBuilder();
        for (int pos = 0; pos < length; pos++) {
            final char c = text.charAt(pos);
            if (c == ESCAPE && pos + 1 < length) {
                sb.append(text.charAt(++pos));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int indexOfLabelEnd(String moreKeySpec, int start) {
        if (moreKeySpec.indexOf(ESCAPE, start) < 0) {
            final int end = moreKeySpec.indexOf(LABEL_END, start);
            if (end == 0)
                throw new MoreKeySpecParserError(LABEL_END + " at " + start + ": " + moreKeySpec);
            return end;
        }
        final int length = moreKeySpec.length();
        for (int pos = start; pos < length; pos++) {
            final char c = moreKeySpec.charAt(pos);
            if (c == ESCAPE && pos + 1 < length) {
                pos++;
            } else if (moreKeySpec.startsWith(LABEL_END, pos)) {
                return pos;
            }
        }
        return -1;
    }

    public static String getLabel(String moreKeySpec) {
        if (hasIcon(moreKeySpec))
            return null;
        final int end = indexOfLabelEnd(moreKeySpec, 0);
        final String label = (end > 0) ? parseEscape(moreKeySpec.substring(0, end))
                : parseEscape(moreKeySpec);
        if (TextUtils.isEmpty(label))
            throw new MoreKeySpecParserError("Empty label: " + moreKeySpec);
        return label;
    }

    public static String getOutputText(String moreKeySpec) {
        if (hasCode(moreKeySpec))
            return null;
        final int end = indexOfLabelEnd(moreKeySpec, 0);
        if (end > 0) {
            if (indexOfLabelEnd(moreKeySpec, end + 1) >= 0)
                    throw new MoreKeySpecParserError("Multiple " + LABEL_END + ": "
                            + moreKeySpec);
            final String outputText = parseEscape(moreKeySpec.substring(end + LABEL_END.length()));
            if (!TextUtils.isEmpty(outputText))
                return outputText;
            throw new MoreKeySpecParserError("Empty outputText: " + moreKeySpec);
        }
        final String label = getLabel(moreKeySpec);
        if (label == null)
            throw new MoreKeySpecParserError("Empty label: " + moreKeySpec);
        // Code is automatically generated for one letter label. See {@link getCode()}.
        if (label.length() == 1)
            return null;
        return label;
    }

    public static int getCode(Resources res, String moreKeySpec) {
        if (hasCode(moreKeySpec)) {
            final int end = indexOfLabelEnd(moreKeySpec, 0);
            if (indexOfLabelEnd(moreKeySpec, end + 1) >= 0)
                throw new MoreKeySpecParserError("Multiple " + LABEL_END + ": " + moreKeySpec);
            final int resId = getResourceId(res,
                    moreKeySpec.substring(end + LABEL_END.length() + PREFIX_AT.length()));
            final int code = res.getInteger(resId);
            return code;
        }
        if (indexOfLabelEnd(moreKeySpec, 0) > 0)
            return Keyboard.CODE_DUMMY;
        final String label = getLabel(moreKeySpec);
        // Code is automatically generated for one letter label.
        if (label != null && label.length() == 1)
            return label.charAt(0);
        return Keyboard.CODE_DUMMY;
    }

    public static int getIconId(String moreKeySpec) {
        if (hasIcon(moreKeySpec)) {
            int end = moreKeySpec.indexOf(LABEL_END, PREFIX_ICON.length() + 1);
            final String iconId = moreKeySpec.substring(PREFIX_ICON.length(), end);
            try {
                return Integer.valueOf(iconId);
            } catch (NumberFormatException e) {
                Log.w(TAG, "illegal icon id specified: " + iconId);
                return KeyboardIconsSet.ICON_UNDEFINED;
            }
        }
        return KeyboardIconsSet.ICON_UNDEFINED;
    }

    private static int getResourceId(Resources res, String name) {
        String packageName = res.getResourcePackageName(R.string.english_ime_name);
        int resId = res.getIdentifier(name, null, packageName);
        if (resId == 0)
            throw new MoreKeySpecParserError("Unknown resource: " + name);
        return resId;
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

    public static CharSequence[] filterOut(Resources res, CharSequence[] moreKeys,
            CodeFilter filter) {
        if (moreKeys == null || moreKeys.length < 1) {
            return null;
        }
        if (moreKeys.length == 1
                && filter.shouldFilterOut(getCode(res, moreKeys[0].toString()))) {
            return null;
        }
        ArrayList<CharSequence> filtered = null;
        for (int i = 0; i < moreKeys.length; i++) {
            final CharSequence moreKeySpec = moreKeys[i];
            if (filter.shouldFilterOut(getCode(res, moreKeySpec.toString()))) {
                if (filtered == null) {
                    filtered = new ArrayList<CharSequence>();
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
        return filtered.toArray(new CharSequence[filtered.size()]);
    }
}
