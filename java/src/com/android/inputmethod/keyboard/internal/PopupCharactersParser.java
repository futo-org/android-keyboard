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

/**
 * String parser of popupCharacters attribute of Key.
 * The string is comma separated texts each of which represents one popup key.
 * Each popup key text is one of the following:
 * - A single letter (Letter)
 * - Label optionally followed by keyOutputText or code (keyLabel|keyOutputText).
 * - Icon followed by keyOutputText or code (@icon/icon_number|@integer/key_code)
 * Special character, comma ',' backslash '\', and bar '|' can be escaped by '\'
 * character.
 * Note that the character '@' and '\' are also parsed by XML parser and CSV parser as well.
 * See {@link KeyboardIconsSet} about icon_number.
 */
public class PopupCharactersParser {
    private static final String TAG = PopupCharactersParser.class.getSimpleName();

    private static final char ESCAPE = '\\';
    private static final String LABEL_END = "|";
    private static final String PREFIX_AT = "@";
    private static final String PREFIX_ICON = PREFIX_AT + "icon/";
    private static final String PREFIX_CODE = PREFIX_AT + "integer/";

    private PopupCharactersParser() {
        // Intentional empty constructor for utility class.
    }

    private static boolean hasIcon(String popupSpec) {
        if (popupSpec.startsWith(PREFIX_ICON)) {
            final int end = indexOfLabelEnd(popupSpec, 0);
            if (end > 0)
                return true;
            throw new PopupCharactersParserError("outputText or code not specified: " + popupSpec);
        }
        return false;
    }

    private static boolean hasCode(String popupSpec) {
        final int end = indexOfLabelEnd(popupSpec, 0);
        if (end > 0 && end + 1 < popupSpec.length()
                && popupSpec.substring(end + 1).startsWith(PREFIX_CODE)) {
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

    private static int indexOfLabelEnd(String popupSpec, int start) {
        if (popupSpec.indexOf(ESCAPE, start) < 0) {
            final int end = popupSpec.indexOf(LABEL_END, start);
            if (end == 0)
                throw new PopupCharactersParserError(LABEL_END + " at " + start + ": " + popupSpec);
            return end;
        }
        final int length = popupSpec.length();
        for (int pos = start; pos < length; pos++) {
            final char c = popupSpec.charAt(pos);
            if (c == ESCAPE && pos + 1 < length) {
                pos++;
            } else if (popupSpec.startsWith(LABEL_END, pos)) {
                return pos;
            }
        }
        return -1;
    }

    public static String getLabel(String popupSpec) {
        if (hasIcon(popupSpec))
            return null;
        final int end = indexOfLabelEnd(popupSpec, 0);
        final String label = (end > 0) ? parseEscape(popupSpec.substring(0, end))
                : parseEscape(popupSpec);
        if (TextUtils.isEmpty(label))
            throw new PopupCharactersParserError("Empty label: " + popupSpec);
        return label;
    }

    public static String getOutputText(String popupSpec) {
        if (hasCode(popupSpec))
            return null;
        final int end = indexOfLabelEnd(popupSpec, 0);
        if (end > 0) {
            if (indexOfLabelEnd(popupSpec, end + 1) >= 0)
                    throw new PopupCharactersParserError("Multiple " + LABEL_END + ": "
                            + popupSpec);
            final String outputText = parseEscape(popupSpec.substring(end + LABEL_END.length()));
            if (!TextUtils.isEmpty(outputText))
                return outputText;
            throw new PopupCharactersParserError("Empty outputText: " + popupSpec);
        }
        final String label = getLabel(popupSpec);
        if (label == null)
            throw new PopupCharactersParserError("Empty label: " + popupSpec);
        // Code is automatically generated for one letter label. See {@link getCode()}.
        if (label.length() == 1)
            return null;
        return label;
    }

    public static int getCode(Resources res, String popupSpec) {
        if (hasCode(popupSpec)) {
            final int end = indexOfLabelEnd(popupSpec, 0);
            if (indexOfLabelEnd(popupSpec, end + 1) >= 0)
                throw new PopupCharactersParserError("Multiple " + LABEL_END + ": " + popupSpec);
            final int resId = getResourceId(res,
                    popupSpec.substring(end + LABEL_END.length() + PREFIX_AT.length()));
            final int code = res.getInteger(resId);
            return code;
        }
        if (indexOfLabelEnd(popupSpec, 0) > 0)
            return Keyboard.CODE_DUMMY;
        final String label = getLabel(popupSpec);
        // Code is automatically generated for one letter label.
        if (label != null && label.length() == 1)
            return label.charAt(0);
        return Keyboard.CODE_DUMMY;
    }

    public static int getIconId(String popupSpec) {
        if (hasIcon(popupSpec)) {
            int end = popupSpec.indexOf(LABEL_END, PREFIX_ICON.length() + 1);
            final String iconId = popupSpec.substring(PREFIX_ICON.length(), end);
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
            throw new PopupCharactersParserError("Unknown resource: " + name);
        return resId;
    }

    @SuppressWarnings("serial")
    public static class PopupCharactersParserError extends RuntimeException {
        public PopupCharactersParserError(String message) {
            super(message);
        }
    }
}
