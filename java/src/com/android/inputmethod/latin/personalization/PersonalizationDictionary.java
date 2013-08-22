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
import com.android.inputmethod.latin.utils.CollectionUtils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;

/**
 * This class is a dictionary for the personalized language model that uses binary dictionary.
 */
public class PersonalizationDictionary extends ExpandableBinaryDictionary {
    private static final String NAME = "personalization";
    private final ArrayList<PersonalizationDictionaryUpdateSession> mSessions =
            CollectionUtils.newArrayList();

    /** Locale for which this user history dictionary is storing words */
    private final String mLocale;

    public PersonalizationDictionary(final Context context, final String locale,
            final SharedPreferences prefs) {
        // TODO: Make isUpdatable true.
        super(context, getFilenameWithLocale(NAME, locale), Dictionary.TYPE_PERSONALIZATION,
                false /* isUpdatable */);
        mLocale = locale;
        // TODO: Restore last updated time
        loadDictionary();
    }

    @Override
    protected void loadDictionaryAsync() {
        // TODO: Implement
    }

    @Override
    protected boolean hasContentChanged() {
        return false;
    }

    @Override
    protected boolean needsToReloadBeforeWriting() {
        return false;
    }

    public void registerUpdateSession(PersonalizationDictionaryUpdateSession session) {
        session.setDictionary(this);
        mSessions.add(session);
        session.onDictionaryReady();
    }

    public void unRegisterUpdateSession(PersonalizationDictionaryUpdateSession session) {
        mSessions.remove(session);
    }
}
