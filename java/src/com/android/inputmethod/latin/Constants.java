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

public final class Constants {
    public static final class Color {
        /**
         * The alpha value for fully opaque.
         */
        public final static int ALPHA_OPAQUE = 255;
    }

    public static final class ImeOption {
        /**
         * The private IME option used to indicate that no microphone should be shown for a given
         * text field. For instance, this is specified by the search dialog when the dialog is
         * already showing a voice search button.
         *
         * @deprecated Use {@link ImeOption#NO_MICROPHONE} with package name prefixed.
         */
        @SuppressWarnings("dep-ann")
        public static final String NO_MICROPHONE_COMPAT = "nm";

        /**
         * The private IME option used to indicate that no microphone should be shown for a given
         * text field. For instance, this is specified by the search dialog when the dialog is
         * already showing a voice search button.
         */
        public static final String NO_MICROPHONE = "noMicrophoneKey";

        /**
         * The private IME option used to indicate that no settings key should be shown for a given
         * text field.
         */
        public static final String NO_SETTINGS_KEY = "noSettingsKey";

        /**
         * The private IME option used to indicate that the given text field needs ASCII code points
         * input.
         *
         * @deprecated Use EditorInfo#IME_FLAG_FORCE_ASCII.
         */
        @SuppressWarnings("dep-ann")
        public static final String FORCE_ASCII = "forceAscii";

        private ImeOption() {
            // This utility class is not publicly instantiable.
        }
    }

    public static final class Subtype {
        /**
         * The subtype mode used to indicate that the subtype is a keyboard.
         */
        public static final String KEYBOARD_MODE = "keyboard";

        public static final class ExtraValue {
            /**
             * The subtype extra value used to indicate that the subtype keyboard layout is capable
             * for typing ASCII characters.
             */
            public static final String ASCII_CAPABLE = "AsciiCapable";

            /**
             * The subtype extra value used to indicate that the subtype require network connection
             * to work.
             */
            public static final String REQ_NETWORK_CONNECTIVITY = "requireNetworkConnectivity";

            /**
             * The subtype extra value used to indicate that the subtype display name contains "%s"
             * for replacement mark and it should be replaced by this extra value.
             * This extra value is supported on JellyBean and later.
             */
            public static final String UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME =
                    "UntranslatableReplacementStringInSubtypeName";

            /**
             * The subtype extra value used to indicate that the subtype keyboard layout set name.
             * This extra value is private to LatinIME.
             */
            public static final String KEYBOARD_LAYOUT_SET = "KeyboardLayoutSet";

            /**
             * The subtype extra value used to indicate that the subtype is additional subtype
             * that the user defined. This extra value is private to LatinIME.
             */
            public static final String IS_ADDITIONAL_SUBTYPE = "isAdditionalSubtype";

            private ExtraValue() {
                // This utility class is not publicly instantiable.
            }
        }

        private Subtype() {
            // This utility class is not publicly instantiable.
        }
    }

    public static final class TextUtils {
        /**
         * Capitalization mode for {@link android.text.TextUtils#getCapsMode}: don't capitalize
         * characters.  This value may be used with
         * {@link android.text.TextUtils#CAP_MODE_CHARACTERS},
         * {@link android.text.TextUtils#CAP_MODE_WORDS}, and
         * {@link android.text.TextUtils#CAP_MODE_SENTENCES}.
         */
        public static final int CAP_MODE_OFF = 0;

        private TextUtils() {
            // This utility class is not publicly instantiable.
        }
    }

    public static final class Dictionary {
        // Must be equal to MAX_WORD_LENGTH in native/jni/src/defines.h
        public static final int MAX_WORD_LENGTH = 48;

        private Dictionary() {
             // This utility class is no publicly instantiable.
        }
    }

    public static final int NOT_A_CODE = -1;

    public static final int NOT_A_COORDINATE = -1;
    public static final int SUGGESTION_STRIP_COORDINATE = -2;
    public static final int SPELL_CHECKER_COORDINATE = -3;
    public static final int EXTERNAL_KEYBOARD_COORDINATE = -4;

    public static boolean isValidCoordinate(final int coordinate) {
        // Detect {@link NOT_A_COORDINATE}, {@link SUGGESTION_STRIP_COORDINATE},
        // and {@link SPELL_CHECKER_COORDINATE}.
        return coordinate >= 0;
    }

    /**
     * Some common keys code. Must be positive.
     */
    public static final int CODE_ENTER = '\n';
    public static final int CODE_TAB = '\t';
    public static final int CODE_SPACE = ' ';
    public static final int CODE_PERIOD = '.';
    public static final int CODE_DASH = '-';
    public static final int CODE_SINGLE_QUOTE = '\'';
    public static final int CODE_DOUBLE_QUOTE = '"';
    public static final int CODE_QUESTION_MARK = '?';
    public static final int CODE_EXCLAMATION_MARK = '!';
    public static final int CODE_SLASH = '/';
    public static final int CODE_COMMERCIAL_AT = '@';
    // TODO: Check how this should work for right-to-left languages. It seems to stand
    // that for rtl languages, a closing parenthesis is a left parenthesis. Is this
    // managed by the font? Or is it a different char?
    public static final int CODE_CLOSING_PARENTHESIS = ')';
    public static final int CODE_CLOSING_SQUARE_BRACKET = ']';
    public static final int CODE_CLOSING_CURLY_BRACKET = '}';
    public static final int CODE_CLOSING_ANGLE_BRACKET = '>';

    /**
     * Special keys code. Must be negative.
     * These should be aligned with KeyboardCodesSet.ID_TO_NAME[],
     * KeyboardCodesSet.DEFAULT[] and KeyboardCodesSet.RTL[]
     */
    public static final int CODE_SHIFT = -1;
    public static final int CODE_SWITCH_ALPHA_SYMBOL = -2;
    public static final int CODE_OUTPUT_TEXT = -3;
    public static final int CODE_DELETE = -4;
    public static final int CODE_SETTINGS = -5;
    public static final int CODE_SHORTCUT = -6;
    public static final int CODE_ACTION_NEXT = -7;
    public static final int CODE_ACTION_PREVIOUS = -8;
    public static final int CODE_LANGUAGE_SWITCH = -9;
    public static final int CODE_RESEARCH = -10;
    public static final int CODE_SHIFT_ENTER = -11;
    // Code value representing the code is not specified.
    public static final int CODE_UNSPECIFIED = -12;

    public static boolean isLetterCode(final int code) {
        return code >= CODE_SPACE;
    }

    public static String printableCode(final int code) {
        switch (code) {
        case CODE_SHIFT: return "shift";
        case CODE_SWITCH_ALPHA_SYMBOL: return "symbol";
        case CODE_OUTPUT_TEXT: return "text";
        case CODE_DELETE: return "delete";
        case CODE_SETTINGS: return "settings";
        case CODE_SHORTCUT: return "shortcut";
        case CODE_ACTION_NEXT: return "actionNext";
        case CODE_ACTION_PREVIOUS: return "actionPrevious";
        case CODE_LANGUAGE_SWITCH: return "languageSwitch";
        case CODE_UNSPECIFIED: return "unspec";
        case CODE_TAB: return "tab";
        case CODE_ENTER: return "enter";
        case CODE_RESEARCH: return "research";
        default:
            if (code < CODE_SPACE) return String.format("'\\u%02x'", code);
            if (code < 0x100) return String.format("'%c'", code);
            return String.format("'\\u%04x'", code);
        }
    }

    // Constants for CSV parsing.
    public static final char CSV_SEPARATOR = ',';
    public static final char CSV_ESCAPE = '\\';

    private Constants() {
        // This utility class is not publicly instantiable.
    }
}
