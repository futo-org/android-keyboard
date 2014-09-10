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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.ExpandableBinaryDictionary.AddMultipleDictionaryEntriesCallback;
import com.android.inputmethod.latin.personalization.PersonalizationDataChunk;
import com.android.inputmethod.latin.personalization.PersonalizationDictionary;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;
import com.android.inputmethod.latin.utils.DistracterFilter;
import com.android.inputmethod.latin.utils.DistracterFilterCheckingIsInDictionary;
import com.android.inputmethod.latin.utils.LanguageModelParam;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

/**
 * Class for managing and updating personalization dictionaries.
 */
public class PersonalizationDictionaryFacilitator {
    private final Context mContext;
    private final DistracterFilter mDistracterFilter;
    private final HashMap<String, HashSet<Locale>> mLangToLocalesMap = new HashMap<>();
    private final HashMap<Locale, ExpandableBinaryDictionary> mPersonalizationDictsToUpdate =
            new HashMap<>();
    private boolean mIsMonolingualUser = false;;

    PersonalizationDictionaryFacilitator(final Context context,
            final DistracterFilter distracterFilter) {
        mContext = context;
        mDistracterFilter = distracterFilter;
    }

    public void close() {
        mLangToLocalesMap.clear();
        for (final ExpandableBinaryDictionary dict : mPersonalizationDictsToUpdate.values()) {
            dict.close();
        }
        mPersonalizationDictsToUpdate.clear();
    }

    public void clearDictionariesToUpdate() {
        for (final ExpandableBinaryDictionary dict : mPersonalizationDictsToUpdate.values()) {
            dict.clear();
        }
        mPersonalizationDictsToUpdate.clear();
    }

    public void updateEnabledSubtypes(final List<InputMethodSubtype> enabledSubtypes) {
        for (final InputMethodSubtype subtype : enabledSubtypes) {
            final Locale locale = SubtypeLocaleUtils.getSubtypeLocale(subtype);
            final String language = locale.getLanguage();
            final HashSet<Locale> locales = mLangToLocalesMap.get(language);
            if (locales != null) {
                locales.add(locale);
            } else {
                final HashSet<Locale> localeSet = new HashSet<>();
                localeSet.add(locale);
                mLangToLocalesMap.put(language, localeSet);
            }
        }
    }

    public void setIsMonolingualUser(final boolean isMonolingualUser) {
        mIsMonolingualUser = isMonolingualUser;
    }

    /**
     * Flush personalization dictionaries to dictionary files. Close dictionaries after writing
     * files except the dictionary that is used for generating suggestions.
     *
     * @param personalizationDictUsedForSuggestion the personalization dictionary used for
     * generating suggestions that won't be closed.
     */
    public void flushPersonalizationDictionariesToUpdate(
            final ExpandableBinaryDictionary personalizationDictUsedForSuggestion) {
        for (final ExpandableBinaryDictionary personalizationDict :
                mPersonalizationDictsToUpdate.values()) {
            personalizationDict.asyncFlushBinaryDictionary();
            if (personalizationDict != personalizationDictUsedForSuggestion) {
                // Close if the dictionary is not being used for suggestion.
                personalizationDict.close();
            }
        }
        mDistracterFilter.close();
        mPersonalizationDictsToUpdate.clear();
    }

    private ExpandableBinaryDictionary getPersonalizationDictToUpdate(final Context context,
            final Locale locale) {
        ExpandableBinaryDictionary personalizationDict = mPersonalizationDictsToUpdate.get(locale);
        if (personalizationDict != null) {
            return personalizationDict;
        }
        personalizationDict = PersonalizationDictionary.getDictionary(context, locale,
                null /* dictFile */, "" /* dictNamePrefix */);
        mPersonalizationDictsToUpdate.put(locale, personalizationDict);
        return personalizationDict;
    }

    private void addEntriesToPersonalizationDictionariesForLocale(final Locale locale,
            final PersonalizationDataChunk personalizationDataChunk,
            final SpacingAndPunctuations spacingAndPunctuations,
            final AddMultipleDictionaryEntriesCallback callback) {
        final ExpandableBinaryDictionary personalizationDict =
                getPersonalizationDictToUpdate(mContext, locale);
        if (personalizationDict == null) {
            if (callback != null) {
                callback.onFinished();
            }
            return;
        }
        final ArrayList<LanguageModelParam> languageModelParams =
                LanguageModelParam.createLanguageModelParamsFrom(
                        personalizationDataChunk.mTokens,
                        personalizationDataChunk.mTimestampInSeconds, spacingAndPunctuations,
                        locale, new DistracterFilterCheckingIsInDictionary(
                                mDistracterFilter, personalizationDict));
        if (languageModelParams == null || languageModelParams.isEmpty()) {
            if (callback != null) {
                callback.onFinished();
            }
            return;
        }
        personalizationDict.addMultipleDictionaryEntriesDynamically(languageModelParams, callback);
    }

    public void addEntriesToPersonalizationDictionariesToUpdate(final Locale defaultLocale,
            final PersonalizationDataChunk personalizationDataChunk,
            final SpacingAndPunctuations spacingAndPunctuations,
            final AddMultipleDictionaryEntriesCallback callback) {
        final String language = personalizationDataChunk.mDetectedLanguage;
        final HashSet<Locale> locales;
        if (mIsMonolingualUser && PersonalizationDataChunk.LANGUAGE_UNKNOWN.equals(language)
                && mLangToLocalesMap.size() == 1) {
            locales = mLangToLocalesMap.get(defaultLocale.getLanguage());
        } else {
            locales = mLangToLocalesMap.get(language);
        }
        if (locales == null || locales.isEmpty()) {
            if (callback != null) {
                callback.onFinished();
            }
            return;
        }
        final AtomicInteger remainingTaskCount = new AtomicInteger(locales.size());
        final AddMultipleDictionaryEntriesCallback callbackForLocales =
                new AddMultipleDictionaryEntriesCallback() {
                    @Override
                    public void onFinished() {
                        if (remainingTaskCount.decrementAndGet() == 0) {
                            // Update tasks for all locales have been finished.
                            if (callback != null) {
                                callback.onFinished();
                            }
                        }
                    }
                };
        for (final Locale locale : locales) {
            addEntriesToPersonalizationDictionariesForLocale(locale, personalizationDataChunk,
                    spacingAndPunctuations, callbackForLocales);
        }
    }
}
