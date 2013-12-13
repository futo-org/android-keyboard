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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.Dictionary;

import java.io.File;
import java.util.Locale;

import android.content.Context;

/**
 * Locally gathers stats about the words user types and various other signals like auto-correction
 * cancellation or manual picks. This allows the keyboard to adapt to the typist over time.
 */
public class UserHistoryDictionary extends DecayingExpandableBinaryDictionaryBase {
    /* package for tests */ static final String NAME =
            UserHistoryDictionary.class.getSimpleName();
    /* package */ UserHistoryDictionary(final Context context, final Locale locale) {
        super(context, locale, Dictionary.TYPE_USER_HISTORY, getDictNameWithLocale(NAME, locale));
    }

    // Creates an instance that uses a given dictionary file for testing.
    @UsedForTesting
    public UserHistoryDictionary(final Context context, final Locale locale,
            final File dictFile) {
        super(context, locale, Dictionary.TYPE_USER_HISTORY, getDictNameWithLocale(NAME, locale),
                dictFile);
    }

    public void cancelAddingUserHistory(final String word0, final String word1) {
        removeBigramDynamically(word0, word1);
    }
}
