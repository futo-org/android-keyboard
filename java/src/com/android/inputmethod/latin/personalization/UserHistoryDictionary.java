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

package com.android.inputmethod.latin.personalization;

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.ExpandableBinaryDictionary;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Locally gathers stats about the words user types and various other signals like auto-correction
 * cancellation or manual picks. This allows the keyboard to adapt to the typist over time.
 */
public class UserHistoryDictionary extends DecayingExpandableBinaryDictionaryBase {
    /* package for tests */ static final String NAME =
            UserHistoryDictionary.class.getSimpleName();
    /* package */ UserHistoryDictionary(final Context context, final String locale,
            final SharedPreferences sp) {
        super(context, locale, sp, Dictionary.TYPE_USER_HISTORY, getDictionaryFileName(locale));
    }

    private static String getDictionaryFileName(final String locale) {
        return NAME + "." + locale + ExpandableBinaryDictionary.DICT_FILE_EXTENSION;
    }
}
