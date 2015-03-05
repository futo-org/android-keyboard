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

package com.android.inputmethod.latin;

import android.content.Context;
import android.util.Pair;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.common.ComposedData;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;
import com.android.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface that facilitates interaction with different kinds of dictionaries. Provides APIs to
 * instantiate and select the correct dictionaries (based on language or account), update entries
 * and fetch suggestions. Currently AndroidSpellCheckerService and LatinIME both use
 * DictionaryFacilitator as a client for interacting with dictionaries.
 */
public interface DictionaryFacilitator {

    public static final String[] ALL_DICTIONARY_TYPES = new String[] {
            Dictionary.TYPE_MAIN,
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_USER,
            Dictionary.TYPE_CONTACTS};

    public static final String[] DYNAMIC_DICTIONARY_TYPES = new String[] {
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_USER,
            Dictionary.TYPE_CONTACTS};

    /**
     * {@link Dictionary#TYPE_USER} is deprecated, except for the spelling service.
     */
    public static final String[] DICTIONARY_TYPES_FOR_SPELLING = new String[] {
            Dictionary.TYPE_MAIN,
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_USER,
            Dictionary.TYPE_CONTACTS};

    /**
     * {@link Dictionary#TYPE_USER} is deprecated, except for the spelling service.
     */
    public static final String[] DICTIONARY_TYPES_FOR_SUGGESTIONS = new String[] {
            Dictionary.TYPE_MAIN,
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_CONTACTS};

    /**
     * Returns whether this facilitator is exactly for this list of locales.
     *
     * @param locales the list of locales to test against
     */
    boolean isForLocales(final Locale[] locales);

    /**
     * Returns whether this facilitator is exactly for this account.
     *
     * @param account the account to test against.
     */
    boolean isForAccount(@Nullable final String account);

    interface DictionaryInitializationListener {
        void onUpdateMainDictionaryAvailability(boolean isMainDictionaryAvailable);
    }

    // TODO: remove this, it's confusing with seamless multiple language switching
    void setIsMonolingualUser(final boolean isMonolingualUser);

    boolean isActive();

    /**
     * Returns the most probable locale among all currently active locales. BE CAREFUL using this.
     *
     * DO NOT USE THIS just because it's convenient. Use it when it's correct, for example when
     * choosing what dictionary to put a word in, or when changing the capitalization of a typed
     * string.
     * @return the most probable locale
     */
    Locale getMostProbableLocale();

    Locale[] getLocales();

    void switchMostProbableLanguage(@Nullable final Locale locale);

    boolean isConfidentAboutCurrentLanguageBeing(final Locale mLocale);

    void resetDictionaries(
            final Context context,
            final Locale[] newLocales,
            final boolean useContactsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            @Nullable final String account,
            final String dictNamePrefix,
            @Nullable final DictionaryInitializationListener listener);

    @UsedForTesting
    void resetDictionariesForTesting(
            final Context context,
            final Locale[] locales,
            final ArrayList<String> dictionaryTypes,
            final HashMap<String, File> dictionaryFiles,
            final Map<String, Map<String, String>> additionalDictAttributes,
            @Nullable final String account);

    void closeDictionaries();

    @UsedForTesting
    ExpandableBinaryDictionary getSubDictForTesting(final String dictName);

    // The main dictionaries are loaded asynchronously. Don't cache the return value
    // of these methods.
    boolean hasAtLeastOneInitializedMainDictionary();

    boolean hasAtLeastOneUninitializedMainDictionary();

    void waitForLoadingMainDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException;

    @UsedForTesting
    void waitForLoadingDictionariesForTesting(final long timeout, final TimeUnit unit)
            throws InterruptedException;

    void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final boolean blockPotentiallyOffensive);

    void unlearnFromUserHistory(final String word,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final int eventType);

    // TODO: Revise the way to fusion suggestion results.
    @Nonnull SuggestionResults getSuggestionResults(final ComposedData composedData,
            final NgramContext ngramContext, @Nonnull final Keyboard keyboard,
            final SettingsValuesForSuggestion settingsValuesForSuggestion, final int sessionId,
            final int inputStyle);

    boolean isValidSpellingWord(final String word);

    boolean isValidSuggestionWord(final String word);

    int getFrequency(final String word);

    int getMaxFrequencyOfExactMatches(final String word);

    void clearUserHistoryDictionary();
    
    String dump(final Context context);
    
    void dumpDictionaryForDebug(final String dictName);

    ArrayList<Pair<String, DictionaryStats>> getStatsOfEnabledSubDicts();
}
