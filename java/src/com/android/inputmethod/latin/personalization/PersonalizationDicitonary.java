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

/**
 * This class is a dictionary for the personalized language model that uses binary dictionary.
 */
public class PersonalizationDicitonary extends ExpandableBinaryDictionary {
    private static final String NAME = "personalization";

    public static void registerUpdateListener(PersonalizationDictionaryUpdateListener listener) {
        // TODO: Implement
    }

    /** Locale for which this user history dictionary is storing words */
    private final String mLocale;

    // Singleton
    private PersonalizationDicitonary(final Context context, final String locale) {
        super(context, getFilenameWithLocale(NAME, locale), Dictionary.TYPE_PERSONALIZATION);
        mLocale = locale;
    }

    @Override
    protected void loadDictionaryAsync() {
        // TODO: Implement
    }

    @Override
    protected boolean hasContentChanged() {
        // TODO: Implement
        return false;
    }

    @Override
    protected boolean needsToReloadBeforeWriting() {
        // TODO: Implement
        return false;
    }

    // TODO: Implement
}
