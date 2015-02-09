/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.inputmethod.latin.common.Constants;

import java.util.Locale;

/**
 * Utility methods related contacts dictionary.
 */
public class ContactsDictionaryUtils {

    /**
     * Returns the index of the last letter in the word, starting from position startIndex.
     */
    public static int getWordEndPosition(final String string, final int len,
            final int startIndex) {
        int end;
        int cp = 0;
        for (end = startIndex + 1; end < len; end += Character.charCount(cp)) {
            cp = string.codePointAt(end);
            if (cp != Constants.CODE_DASH && cp != Constants.CODE_SINGLE_QUOTE
                   && !Character.isLetter(cp)) {
                break;
            }
        }
        return end;
    }

    /**
     * Returns true if the locale supports using first name and last name as bigrams.
     */
    public static boolean useFirstLastBigramsForLocale(final Locale locale) {
        // TODO: Add firstname/lastname bigram rules for other languages.
        if (locale != null && locale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
            return true;
        }
        return false;
    }
}
