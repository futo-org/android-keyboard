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
import com.android.inputmethod.latin.utils.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import android.content.Context;

public class PersonalizationDictionary extends DecayingExpandableBinaryDictionaryBase {
    /* package */ static final String NAME = PersonalizationDictionary.class.getSimpleName();

    private final ArrayList<PersonalizationDictionaryUpdateSession> mSessions =
            CollectionUtils.newArrayList();

    /* package */ PersonalizationDictionary(final Context context, final Locale locale) {
        super(context, locale, Dictionary.TYPE_PERSONALIZATION,
                getDictNameWithLocale(NAME, locale));
    }

    // Creates an instance that uses a given dictionary file for testing.
    @UsedForTesting
    public PersonalizationDictionary(final Context context, final Locale locale,
            final File dictFile) {
        super(context, locale, Dictionary.TYPE_PERSONALIZATION, getDictNameWithLocale(NAME, locale),
                dictFile);
    }

    public void registerUpdateSession(PersonalizationDictionaryUpdateSession session) {
        session.setPredictionDictionary(this);
        mSessions.add(session);
        session.onDictionaryReady();
    }

    public void unRegisterUpdateSession(PersonalizationDictionaryUpdateSession session) {
        mSessions.remove(session);
    }
}
