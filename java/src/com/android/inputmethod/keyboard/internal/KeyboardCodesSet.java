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

package com.android.inputmethod.keyboard.internal;

import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.util.HashMap;

public final class KeyboardCodesSet {
    private static final HashMap<String, int[]> sLanguageToCodesMap = CollectionUtils.newHashMap();
    private static final HashMap<String, Integer> sNameToIdMap = CollectionUtils.newHashMap();

    private int[] mCodes = DEFAULT;

    public void setLanguage(final String language) {
        final int[] codes = sLanguageToCodesMap.get(language);
        mCodes = (codes != null) ? codes : DEFAULT;
    }

    public int getCode(final String name) {
        Integer id = sNameToIdMap.get(name);
        if (id == null) throw new RuntimeException("Unknown key code: " + name);
        return mCodes[id];
    }

    private static final String[] ID_TO_NAME = {
        "key_tab",
        "key_enter",
        "key_space",
        "key_shift",
        "key_capslock",
        "key_switch_alpha_symbol",
        "key_output_text",
        "key_delete",
        "key_settings",
        "key_shortcut",
        "key_action_next",
        "key_action_previous",
        "key_shift_enter",
        "key_language_switch",
        "key_emoji",
        "key_unspecified",
        "key_left_parenthesis",
        "key_right_parenthesis",
        "key_less_than",
        "key_greater_than",
        "key_left_square_bracket",
        "key_right_square_bracket",
        "key_left_curly_bracket",
        "key_right_curly_bracket",
    };

    private static final int CODE_LEFT_PARENTHESIS = '(';
    private static final int CODE_RIGHT_PARENTHESIS = ')';
    private static final int CODE_LESS_THAN_SIGN = '<';
    private static final int CODE_GREATER_THAN_SIGN = '>';
    private static final int CODE_LEFT_SQUARE_BRACKET = '[';
    private static final int CODE_RIGHT_SQUARE_BRACKET = ']';
    private static final int CODE_LEFT_CURLY_BRACKET = '{';
    private static final int CODE_RIGHT_CURLY_BRACKET = '}';

    // This array should be aligned with the array RTL below.
    private static final int[] DEFAULT = {
        Constants.CODE_TAB,
        Constants.CODE_ENTER,
        Constants.CODE_SPACE,
        Constants.CODE_SHIFT,
        Constants.CODE_CAPSLOCK,
        Constants.CODE_SWITCH_ALPHA_SYMBOL,
        Constants.CODE_OUTPUT_TEXT,
        Constants.CODE_DELETE,
        Constants.CODE_SETTINGS,
        Constants.CODE_SHORTCUT,
        Constants.CODE_ACTION_NEXT,
        Constants.CODE_ACTION_PREVIOUS,
        Constants.CODE_SHIFT_ENTER,
        Constants.CODE_LANGUAGE_SWITCH,
        Constants.CODE_EMOJI,
        Constants.CODE_UNSPECIFIED,
        CODE_LEFT_PARENTHESIS,
        CODE_RIGHT_PARENTHESIS,
        CODE_LESS_THAN_SIGN,
        CODE_GREATER_THAN_SIGN,
        CODE_LEFT_SQUARE_BRACKET,
        CODE_RIGHT_SQUARE_BRACKET,
        CODE_LEFT_CURLY_BRACKET,
        CODE_RIGHT_CURLY_BRACKET,
    };

    private static final int[] RTL = {
        DEFAULT[0],
        DEFAULT[1],
        DEFAULT[2],
        DEFAULT[3],
        DEFAULT[4],
        DEFAULT[5],
        DEFAULT[6],
        DEFAULT[7],
        DEFAULT[8],
        DEFAULT[9],
        DEFAULT[10],
        DEFAULT[11],
        DEFAULT[12],
        DEFAULT[13],
        DEFAULT[14],
        DEFAULT[15],
        CODE_RIGHT_PARENTHESIS,
        CODE_LEFT_PARENTHESIS,
        CODE_GREATER_THAN_SIGN,
        CODE_LESS_THAN_SIGN,
        CODE_RIGHT_SQUARE_BRACKET,
        CODE_LEFT_SQUARE_BRACKET,
        CODE_RIGHT_CURLY_BRACKET,
        CODE_LEFT_CURLY_BRACKET,
    };

    private static final String LANGUAGE_DEFAULT = "DEFAULT";
    private static final String LANGUAGE_ARABIC = "ar";
    private static final String LANGUAGE_PERSIAN = "fa";
    private static final String LANGUAGE_HEBREW = "iw";

    private static final Object[] LANGUAGE_AND_CODES = {
        LANGUAGE_DEFAULT, DEFAULT,
        LANGUAGE_ARABIC, RTL,
        LANGUAGE_PERSIAN, RTL,
        LANGUAGE_HEBREW, RTL,
    };

    static {
        if (DEFAULT.length != RTL.length || DEFAULT.length != ID_TO_NAME.length) {
            throw new RuntimeException("Internal inconsistency");
        }
        for (int i = 0; i < ID_TO_NAME.length; i++) {
            sNameToIdMap.put(ID_TO_NAME[i], i);
        }

        for (int i = 0; i < LANGUAGE_AND_CODES.length; i += 2) {
            final String language = (String)LANGUAGE_AND_CODES[i];
            final int[] codes = (int[])LANGUAGE_AND_CODES[i + 1];
            sLanguageToCodesMap.put(language, codes);
        }
    }
}
