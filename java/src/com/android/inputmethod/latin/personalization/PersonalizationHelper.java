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

import com.android.inputmethod.latin.utils.CollectionUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentHashMap;

public class PersonalizationHelper {
    private static final String TAG = PersonalizationHelper.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final ConcurrentHashMap<String, SoftReference<UserHistoryDictionary>>
            sLangUserHistoryDictCache = CollectionUtils.newConcurrentHashMap();

    private static final ConcurrentHashMap<String, SoftReference<PersonalizationDictionary>>
            sLangPersonalizationDictCache = CollectionUtils.newConcurrentHashMap();

    private static final ConcurrentHashMap<String,
            SoftReference<PersonalizationPredictionDictionary>>
                    sLangPersonalizationPredictionDictCache =
                            CollectionUtils.newConcurrentHashMap();

    public static UserHistoryDictionary getUserHistoryDictionary(
            final Context context, final String locale, final SharedPreferences sp) {
        synchronized (sLangUserHistoryDictCache) {
            if (sLangUserHistoryDictCache.containsKey(locale)) {
                final SoftReference<UserHistoryDictionary> ref =
                        sLangUserHistoryDictCache.get(locale);
                final UserHistoryDictionary dict = ref == null ? null : ref.get();
                if (dict != null) {
                    if (DEBUG) {
                        Log.w(TAG, "Use cached UserHistoryDictionary for " + locale);
                    }
                    dict.reloadDictionaryIfRequired();
                    return dict;
                }
            }
            final UserHistoryDictionary dict = new UserHistoryDictionary(context, locale, sp);
            sLangUserHistoryDictCache.put(locale, new SoftReference<UserHistoryDictionary>(dict));
            return dict;
        }
    }

    public static void tryDecayingAllOpeningUserHistoryDictionary() {
        for (final ConcurrentHashMap.Entry<String, SoftReference<UserHistoryDictionary>> entry
                : sLangUserHistoryDictCache.entrySet()) {
            if (entry.getValue() != null) {
                final UserHistoryDictionary dict = entry.getValue().get();
                if (dict != null) {
                    dict.decayIfNeeded();
                }
            }
        }
    }

    public static void registerPersonalizationDictionaryUpdateSession(final Context context,
            final PersonalizationDictionaryUpdateSession session, String locale) {
        final PersonalizationPredictionDictionary predictionDictionary =
                getPersonalizationPredictionDictionary(context, locale,
                        PreferenceManager.getDefaultSharedPreferences(context));
        predictionDictionary.registerUpdateSession(session);
        final PersonalizationDictionary dictionary =
                getPersonalizationDictionary(context, locale,
                        PreferenceManager.getDefaultSharedPreferences(context));
        dictionary.registerUpdateSession(session);
    }

    public static PersonalizationDictionary getPersonalizationDictionary(
            final Context context, final String locale, final SharedPreferences sp) {
        synchronized (sLangPersonalizationDictCache) {
            if (sLangPersonalizationDictCache.containsKey(locale)) {
                final SoftReference<PersonalizationDictionary> ref =
                        sLangPersonalizationDictCache.get(locale);
                final PersonalizationDictionary dict = ref == null ? null : ref.get();
                if (dict != null) {
                    if (DEBUG) {
                        Log.w(TAG, "Use cached PersonalizationDictCache for " + locale);
                    }
                    return dict;
                }
            }
            final PersonalizationDictionary dict =
                    new PersonalizationDictionary(context, locale, sp);
            sLangPersonalizationDictCache.put(
                    locale, new SoftReference<PersonalizationDictionary>(dict));
            return dict;
        }
    }

    public static PersonalizationPredictionDictionary getPersonalizationPredictionDictionary(
            final Context context, final String locale, final SharedPreferences sp) {
        synchronized (sLangPersonalizationPredictionDictCache) {
            if (sLangPersonalizationPredictionDictCache.containsKey(locale)) {
                final SoftReference<PersonalizationPredictionDictionary> ref =
                        sLangPersonalizationPredictionDictCache.get(locale);
                final PersonalizationPredictionDictionary dict = ref == null ? null : ref.get();
                if (dict != null) {
                    if (DEBUG) {
                        Log.w(TAG, "Use cached PersonalizationPredictionDictionary for " + locale);
                    }
                    return dict;
                }
            }
            final PersonalizationPredictionDictionary dict =
                    new PersonalizationPredictionDictionary(context, locale, sp);
            sLangPersonalizationPredictionDictCache.put(
                    locale, new SoftReference<PersonalizationPredictionDictionary>(dict));
            return dict;
        }
    }
}
