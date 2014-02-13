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

import android.content.Context;

import com.android.inputmethod.latin.Dictionary;

import java.io.File;
import java.util.Locale;

public class PersonalizationDictionary extends DecayingExpandableBinaryDictionaryBase {
    /* package */ static final String NAME = PersonalizationDictionary.class.getSimpleName();

    /* package */ PersonalizationDictionary(final Context context, final Locale locale) {
        this(context, locale, null /* dictFile */);
    }

    public PersonalizationDictionary(final Context context, final Locale locale,
            final File dictFile) {
        super(context, getDictName(NAME, locale, dictFile), locale, Dictionary.TYPE_PERSONALIZATION,
                dictFile);
    }

    @Override
    public boolean isValidWord(final String word) {
        // Strings out of this dictionary should not be considered existing words.
        return false;
    }
}
