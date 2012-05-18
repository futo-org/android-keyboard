/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.view.inputmethod.EditorInfo;

public final class Constants {
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
         * @deprecated Use {@link EditorInfo#IME_FLAG_FORCE_ASCII}.
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

    public static class TextUtils {
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

    private Constants() {
        // This utility class is not publicly instantiable.
    }
}
