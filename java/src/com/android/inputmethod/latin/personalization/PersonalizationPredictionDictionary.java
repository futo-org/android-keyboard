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
import com.android.inputmethod.latin.ExpandableDictionary;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * This class is a dictionary for the personalized prediction language model implemented in Java.
 */
public class PersonalizationPredictionDictionary extends ExpandableDictionary {
    public static void registerUpdateListener(PersonalizationDictionaryUpdateListener listener) {
        // TODO: Implement
    }

    /** Locale for which this user history dictionary is storing words */
    private final String mLocale;
    private final SharedPreferences mPrefs;

    // Singleton
    private PersonalizationPredictionDictionary(final Context context, final String locale,
            final SharedPreferences sp) {
        super(context, Dictionary.TYPE_PERSONALIZATION_PREDICTION_IN_JAVA);
        mLocale = locale;
        mPrefs = sp;
    }

    // TODO: Implement
}
