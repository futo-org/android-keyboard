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
import android.util.Log;

import com.android.inputmethod.latin.utils.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.ref.SoftReference;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PersonalizationHelper {
    private static final String TAG = PersonalizationHelper.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final ConcurrentHashMap<String, SoftReference<UserHistoryDictionary>>
            sLangUserHistoryDictCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SoftReference<PersonalizationDictionary>>
            sLangPersonalizationDictCache = new ConcurrentHashMap<>();

    public static UserHistoryDictionary getUserHistoryDictionary(
            final Context context, final Locale locale) {
        final String localeStr = locale.toString();
        synchronized (sLangUserHistoryDictCache) {
            if (sLangUserHistoryDictCache.containsKey(localeStr)) {
                final SoftReference<UserHistoryDictionary> ref =
                        sLangUserHistoryDictCache.get(localeStr);
                final UserHistoryDictionary dict = ref == null ? null : ref.get();
                if (dict != null) {
                    if (DEBUG) {
                        Log.w(TAG, "Use cached UserHistoryDictionary for " + locale);
                    }
                    dict.reloadDictionaryIfRequired();
                    return dict;
                }
            }
            final UserHistoryDictionary dict = new UserHistoryDictionary(context, locale);
            sLangUserHistoryDictCache.put(localeStr, new SoftReference<>(dict));
            return dict;
        }
    }

    private static int sCurrentTimestampForTesting = 0;
    public static void currentTimeChangedForTesting(final int currentTimestamp) {
        if (TimeUnit.MILLISECONDS.toSeconds(
                DictionaryDecayBroadcastReciever.DICTIONARY_DECAY_INTERVAL)
                        < currentTimestamp - sCurrentTimestampForTesting) {
            runGCOnAllOpenedUserHistoryDictionaries();
            runGCOnAllOpenedPersonalizationDictionaries();
        }
    }

    public static void runGCOnAllOpenedUserHistoryDictionaries() {
        runGCOnAllDictionariesIfRequired(sLangUserHistoryDictCache);
    }

    public static void runGCOnAllOpenedPersonalizationDictionaries() {
        runGCOnAllDictionariesIfRequired(sLangPersonalizationDictCache);
    }

    private static <T extends DecayingExpandableBinaryDictionaryBase>
            void runGCOnAllDictionariesIfRequired(
                    final ConcurrentHashMap<String, SoftReference<T>> dictionaryMap) {
        for (final ConcurrentHashMap.Entry<String, SoftReference<T>> entry
                : dictionaryMap.entrySet()) {
            final DecayingExpandableBinaryDictionaryBase dict = entry.getValue().get();
            if (dict != null) {
                dict.runGCIfRequired();
            } else {
                dictionaryMap.remove(entry.getKey());
            }
        }
    }

    public static PersonalizationDictionary getPersonalizationDictionary(
            final Context context, final Locale locale) {
        final String localeStr = locale.toString();
        synchronized (sLangPersonalizationDictCache) {
            if (sLangPersonalizationDictCache.containsKey(localeStr)) {
                final SoftReference<PersonalizationDictionary> ref =
                        sLangPersonalizationDictCache.get(localeStr);
                final PersonalizationDictionary dict = ref == null ? null : ref.get();
                if (dict != null) {
                    if (DEBUG) {
                        Log.w(TAG, "Use cached PersonalizationDictionary for " + locale);
                    }
                    return dict;
                }
            }
            final PersonalizationDictionary dict = new PersonalizationDictionary(context, locale);
            sLangPersonalizationDictCache.put(localeStr, new SoftReference<>(dict));
            return dict;
        }
    }

    public static void removeAllPersonalizationDictionaries(final Context context) {
        removeAllDictionaries(context, sLangPersonalizationDictCache,
                PersonalizationDictionary.NAME);
    }

    public static void removeAllUserHistoryDictionaries(final Context context) {
        removeAllDictionaries(context, sLangUserHistoryDictCache,
                UserHistoryDictionary.NAME);
    }

    private static <T extends DecayingExpandableBinaryDictionaryBase> void removeAllDictionaries(
            final Context context, final ConcurrentHashMap<String, SoftReference<T>> dictionaryMap,
            final String dictNamePrefix) {
        synchronized (dictionaryMap) {
            for (final ConcurrentHashMap.Entry<String, SoftReference<T>> entry
                    : dictionaryMap.entrySet()) {
                if (entry.getValue() != null) {
                    final DecayingExpandableBinaryDictionaryBase dict = entry.getValue().get();
                    if (dict != null) {
                        dict.clear();
                    }
                }
            }
            dictionaryMap.clear();
            final File filesDir = context.getFilesDir();
            if (filesDir == null) {
                Log.e(TAG, "context.getFilesDir() returned null.");
                return;
            }
            if (!FileUtils.deleteFilteredFiles(filesDir, new DictFilter(dictNamePrefix))) {
                Log.e(TAG, "Cannot remove all existing dictionary files. filesDir: "
                        + filesDir.getAbsolutePath() + ", dictNamePrefix: " + dictNamePrefix);
            }
        }
    }

    private static class DictFilter implements FilenameFilter {
        private final String mName;

        DictFilter(final String name) {
            mName = name;
        }

        @Override
        public boolean accept(final File dir, final String name) {
            return name.startsWith(mName);
        }
    }
}
