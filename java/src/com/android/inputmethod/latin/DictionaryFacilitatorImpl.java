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
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.NgramContext.WordInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.personalization.UserHistoryDictionary;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;
import com.android.inputmethod.latin.utils.DistracterFilter;
import com.android.inputmethod.latin.utils.DistracterFilterCheckingExactMatchesAndSuggestions;
import com.android.inputmethod.latin.utils.DistracterFilterCheckingIsInDictionary;
import com.android.inputmethod.latin.utils.ExecutorUtils;
import com.android.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Facilitates interaction with different kinds of dictionaries. Provides APIs
 * to instantiate and select the correct dictionaries (based on language or account),
 * update entries and fetch suggestions.
 *
 * Currently AndroidSpellCheckerService and LatinIME both use DictionaryFacilitator as
 * a client for interacting with dictionaries.
 */
public class DictionaryFacilitatorImpl implements DictionaryFacilitator {
    // TODO: Consolidate dictionaries in native code.
    public static final String TAG = DictionaryFacilitatorImpl.class.getSimpleName();

    // HACK: This threshold is being used when adding a capitalized entry in the User History
    // dictionary.
    private static final int CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT = 140;
    // How many words we need to type in a row ({@see mConfidenceInMostProbableLanguage}) to
    // declare we are confident the user is typing in the most probable language.
    private static final int CONFIDENCE_THRESHOLD = 3;

    private DictionaryGroup[] mDictionaryGroups = new DictionaryGroup[] { new DictionaryGroup() };
    private DictionaryGroup mMostProbableDictionaryGroup = mDictionaryGroups[0];
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionaries = new CountDownLatch(0);
    // To synchronize assigning mDictionaryGroup to ensure closing dictionaries.
    private final Object mLock = new Object();
    private final DistracterFilter mDistracterFilter;

    private static final String[] DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS =
            new String[] {
                Dictionary.TYPE_MAIN,
                Dictionary.TYPE_USER_HISTORY,
                Dictionary.TYPE_USER,
                Dictionary.TYPE_CONTACTS,
            };

    public static final Map<String, Class<? extends ExpandableBinaryDictionary>>
            DICT_TYPE_TO_CLASS = new HashMap<>();

    static {
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER_HISTORY, UserHistoryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER, UserBinaryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_CONTACTS, ContactsBinaryDictionary.class);
    }

    private static final String DICT_FACTORY_METHOD_NAME = "getDictionary";
    private static final Class<?>[] DICT_FACTORY_METHOD_ARG_TYPES =
            new Class[] { Context.class, Locale.class, File.class, String.class, String.class };

    private static final String[] SUB_DICT_TYPES =
            Arrays.copyOfRange(DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS, 1 /* start */,
                    DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS.length);

    /**
     * Returns whether this facilitator is exactly for this list of locales.
     *
     * @param locales the list of locales to test against
     */
    public boolean isForLocales(final Locale[] locales) {
        if (locales.length != mDictionaryGroups.length) {
            return false;
        }
        for (final Locale locale : locales) {
            boolean found = false;
            for (final DictionaryGroup group : mDictionaryGroups) {
                if (locale.equals(group.mLocale)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether this facilitator is exactly for this account.
     *
     * @param account the account to test against.
     */
    public boolean isForAccount(@Nullable final String account) {
        for (final DictionaryGroup group : mDictionaryGroups) {
            if (!TextUtils.equals(group.mAccount, account)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A group of dictionaries that work together for a single language.
     */
    private static class DictionaryGroup {
        // TODO: Add null analysis annotations.
        // TODO: Run evaluation to determine a reasonable value for these constants. The current
        // values are ad-hoc and chosen without any particular care or methodology.
        public static final float WEIGHT_FOR_MOST_PROBABLE_LANGUAGE = 1.0f;
        public static final float WEIGHT_FOR_GESTURING_IN_NOT_MOST_PROBABLE_LANGUAGE = 0.95f;
        public static final float WEIGHT_FOR_TYPING_IN_NOT_MOST_PROBABLE_LANGUAGE = 0.6f;

        /**
         * The locale associated with the dictionary group.
         */
        @Nullable public final Locale mLocale;

        /**
         * The user account associated with the dictionary group.
         */
        @Nullable public final String mAccount;

        @Nullable private Dictionary mMainDict;
        // Confidence that the most probable language is actually the language the user is
        // typing in. For now, this is simply the number of times a word from this language
        // has been committed in a row.
        private int mConfidence = 0;

        public float mWeightForTypingInLocale = WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
        public float mWeightForGesturingInLocale = WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
        public final ConcurrentHashMap<String, ExpandableBinaryDictionary> mSubDictMap =
                new ConcurrentHashMap<>();

        public DictionaryGroup() {
            this(null /* locale */, null /* mainDict */, null /* account */,
                    Collections.<String, ExpandableBinaryDictionary>emptyMap() /* subDicts */);
        }

        public DictionaryGroup(@Nullable final Locale locale,
                @Nullable final Dictionary mainDict,
                @Nullable final String account,
                final Map<String, ExpandableBinaryDictionary> subDicts) {
            mLocale = locale;
            mAccount = account;
            // The main dictionary can be asynchronously loaded.
            setMainDict(mainDict);
            for (final Map.Entry<String, ExpandableBinaryDictionary> entry : subDicts.entrySet()) {
                setSubDict(entry.getKey(), entry.getValue());
            }
        }

        private void setSubDict(final String dictType, final ExpandableBinaryDictionary dict) {
            if (dict != null) {
                mSubDictMap.put(dictType, dict);
            }
        }

        public void setMainDict(final Dictionary mainDict) {
            // Close old dictionary if exists. Main dictionary can be assigned multiple times.
            final Dictionary oldDict = mMainDict;
            mMainDict = mainDict;
            if (oldDict != null && mainDict != oldDict) {
                oldDict.close();
            }
        }

        public Dictionary getDict(final String dictType) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict;
            }
            return getSubDict(dictType);
        }

        public ExpandableBinaryDictionary getSubDict(final String dictType) {
            return mSubDictMap.get(dictType);
        }

        public boolean hasDict(final String dictType, @Nullable final String account) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict != null;
            }
            if (Dictionary.TYPE_USER_HISTORY.equals(dictType) &&
                    !TextUtils.equals(account, mAccount)) {
                // If the dictionary type is user history, & if the account doesn't match,
                // return immediately. If the account matches, continue looking it up in the
                // sub dictionary map.
                return false;
            }
            return mSubDictMap.containsKey(dictType);
        }

        public void closeDict(final String dictType) {
            final Dictionary dict;
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                dict = mMainDict;
            } else {
                dict = mSubDictMap.remove(dictType);
            }
            if (dict != null) {
                dict.close();
            }
        }
    }

    public DictionaryFacilitatorImpl() {
        mDistracterFilter = DistracterFilter.EMPTY_DISTRACTER_FILTER;
    }

    public DictionaryFacilitatorImpl(final Context context) {
        mDistracterFilter = new DistracterFilterCheckingExactMatchesAndSuggestions(context);
    }

    public void updateEnabledSubtypes(final List<InputMethodSubtype> enabledSubtypes) {
        mDistracterFilter.updateEnabledSubtypes(enabledSubtypes);
    }

    // TODO: remove this, it's confusing with seamless multiple language switching
    public void setIsMonolingualUser(final boolean isMonolingualUser) {
    }

    public boolean isActive() {
        return null != mDictionaryGroups[0].mLocale;
    }

    /**
     * Returns the most probable locale among all currently active locales. BE CAREFUL using this.
     *
     * DO NOT USE THIS just because it's convenient. Use it when it's correct, for example when
     * choosing what dictionary to put a word in, or when changing the capitalization of a typed
     * string.
     * @return the most probable locale
     */
    public Locale getMostProbableLocale() {
        return getDictionaryGroupForMostProbableLanguage().mLocale;
    }

    public Locale[] getLocales() {
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        final Locale[] locales = new Locale[dictionaryGroups.length];
        for (int i = 0; i < dictionaryGroups.length; ++i) {
            locales[i] = dictionaryGroups[i].mLocale;
        }
        return locales;
    }

    private DictionaryGroup getDictionaryGroupForMostProbableLanguage() {
        return mMostProbableDictionaryGroup;
    }

    public void switchMostProbableLanguage(@Nullable final Locale locale) {
        if (null == locale) {
            // In many cases, there is no locale to a committed word. For example, a typed word
            // that is in none of the currently active dictionaries but still does not
            // auto-correct to anything has no locale. In this case we simply do not change
            // the most probable language and do not touch confidence.
            return;
        }
        final DictionaryGroup newMostProbableDictionaryGroup =
                findDictionaryGroupWithLocale(mDictionaryGroups, locale);
        if (null == newMostProbableDictionaryGroup) {
            // It seems this may happen as a race condition; pressing the globe key and space
            // in quick succession could commit a word out of a dictionary that's not in the
            // facilitator any more. In this case, just not changing things is fine.
            return;
        }
        if (newMostProbableDictionaryGroup == mMostProbableDictionaryGroup) {
            ++newMostProbableDictionaryGroup.mConfidence;
        } else {
            mMostProbableDictionaryGroup.mWeightForTypingInLocale =
                    DictionaryGroup.WEIGHT_FOR_TYPING_IN_NOT_MOST_PROBABLE_LANGUAGE;
            mMostProbableDictionaryGroup.mWeightForGesturingInLocale =
                    DictionaryGroup.WEIGHT_FOR_GESTURING_IN_NOT_MOST_PROBABLE_LANGUAGE;
            mMostProbableDictionaryGroup.mConfidence = 0;
            newMostProbableDictionaryGroup.mWeightForTypingInLocale =
                    DictionaryGroup.WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
            newMostProbableDictionaryGroup.mWeightForGesturingInLocale =
                    DictionaryGroup.WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
            mMostProbableDictionaryGroup = newMostProbableDictionaryGroup;
        }
    }

    public boolean isConfidentAboutCurrentLanguageBeing(final Locale mLocale) {
        final DictionaryGroup mostProbableDictionaryGroup = mMostProbableDictionaryGroup;
        if (!mostProbableDictionaryGroup.mLocale.equals(mLocale)) {
            return false;
        }
        if (mDictionaryGroups.length <= 1) {
            return true;
        }
        return mostProbableDictionaryGroup.mConfidence >= CONFIDENCE_THRESHOLD;
    }

    @Nullable
    private static ExpandableBinaryDictionary getSubDict(final String dictType,
            final Context context, final Locale locale, final File dictFile,
            final String dictNamePrefix, @Nullable final String account) {
        final Class<? extends ExpandableBinaryDictionary> dictClass =
                DICT_TYPE_TO_CLASS.get(dictType);
        if (dictClass == null) {
            return null;
        }
        try {
            final Method factoryMethod = dictClass.getMethod(DICT_FACTORY_METHOD_NAME,
                    DICT_FACTORY_METHOD_ARG_TYPES);
            final Object dict = factoryMethod.invoke(null /* obj */,
                    new Object[] { context, locale, dictFile, dictNamePrefix, account });
            return (ExpandableBinaryDictionary) dict;
        } catch (final NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            Log.e(TAG, "Cannot create dictionary: " + dictType, e);
            return null;
        }
    }

    public void resetDictionaries(final Context context, final Locale[] newLocales,
            final boolean useContactsDict, final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            @Nullable final String account,
            final DictionaryInitializationListener listener) {
        resetDictionariesWithDictNamePrefix(context, newLocales, useContactsDict,
                usePersonalizedDicts, forceReloadMainDictionary, listener, "" /* dictNamePrefix */,
                account);
    }

    @Nullable
    static DictionaryGroup findDictionaryGroupWithLocale(final DictionaryGroup[] dictionaryGroups,
            final Locale locale) {
        for (DictionaryGroup dictionaryGroup : dictionaryGroups) {
            if (locale.equals(dictionaryGroup.mLocale)) {
                return dictionaryGroup;
            }
        }
        return null;
    }

    public void resetDictionariesWithDictNamePrefix(final Context context,
            final Locale[] newLocales,
            final boolean useContactsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            @Nullable final DictionaryInitializationListener listener,
            final String dictNamePrefix,
            @Nullable final String account) {
        final HashMap<Locale, ArrayList<String>> existingDictionariesToCleanup = new HashMap<>();
        // TODO: Make subDictTypesToUse configurable by resource or a static final list.
        final HashSet<String> subDictTypesToUse = new HashSet<>();
        subDictTypesToUse.add(Dictionary.TYPE_USER);
        if (useContactsDict) {
            subDictTypesToUse.add(Dictionary.TYPE_CONTACTS);
        }
        if (usePersonalizedDicts) {
            subDictTypesToUse.add(Dictionary.TYPE_USER_HISTORY);
        }

        // Gather all dictionaries. We'll remove them from the list to clean up later.
        for (final Locale newLocale : newLocales) {
            final ArrayList<String> dictTypeForLocale = new ArrayList<>();
            existingDictionariesToCleanup.put(newLocale, dictTypeForLocale);
            final DictionaryGroup currentDictionaryGroupForLocale =
                    findDictionaryGroupWithLocale(mDictionaryGroups, newLocale);
            if (null == currentDictionaryGroupForLocale) {
                continue;
            }
            for (final String dictType : SUB_DICT_TYPES) {
                if (currentDictionaryGroupForLocale.hasDict(dictType, account)) {
                    dictTypeForLocale.add(dictType);
                }
            }
            if (currentDictionaryGroupForLocale.hasDict(Dictionary.TYPE_MAIN, account)) {
                dictTypeForLocale.add(Dictionary.TYPE_MAIN);
            }
        }

        final DictionaryGroup[] newDictionaryGroups = new DictionaryGroup[newLocales.length];
        for (int i = 0; i < newLocales.length; ++i) {
            final Locale newLocale = newLocales[i];
            final DictionaryGroup dictionaryGroupForLocale =
                    findDictionaryGroupWithLocale(mDictionaryGroups, newLocale);
            final ArrayList<String> dictTypesToCleanupForLocale =
                    existingDictionariesToCleanup.get(newLocale);
            final boolean noExistingDictsForThisLocale = (null == dictionaryGroupForLocale);

            final Dictionary mainDict;
            if (forceReloadMainDictionary || noExistingDictsForThisLocale
                    || !dictionaryGroupForLocale.hasDict(Dictionary.TYPE_MAIN, account)) {
                mainDict = null;
            } else {
                mainDict = dictionaryGroupForLocale.getDict(Dictionary.TYPE_MAIN);
                dictTypesToCleanupForLocale.remove(Dictionary.TYPE_MAIN);
            }

            final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();
            for (final String subDictType : subDictTypesToUse) {
                final ExpandableBinaryDictionary subDict;
                if (noExistingDictsForThisLocale
                        || !dictionaryGroupForLocale.hasDict(subDictType, account)) {
                    // Create a new dictionary.
                    subDict = getSubDict(subDictType, context, newLocale, null /* dictFile */,
                            dictNamePrefix, account);
                } else {
                    // Reuse the existing dictionary, and don't close it at the end
                    subDict = dictionaryGroupForLocale.getSubDict(subDictType);
                    dictTypesToCleanupForLocale.remove(subDictType);
                }
                subDicts.put(subDictType, subDict);
            }
            newDictionaryGroups[i] = new DictionaryGroup(newLocale, mainDict, account, subDicts);
        }

        // Replace Dictionaries.
        final DictionaryGroup[] oldDictionaryGroups;
        synchronized (mLock) {
            oldDictionaryGroups = mDictionaryGroups;
            mDictionaryGroups = newDictionaryGroups;
            mMostProbableDictionaryGroup = newDictionaryGroups[0];
            if (hasAtLeastOneUninitializedMainDictionary()) {
                asyncReloadUninitializedMainDictionaries(context, newLocales, listener);
            }
        }
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary());
        }

        // Clean up old dictionaries.
        for (final Locale localeToCleanUp : existingDictionariesToCleanup.keySet()) {
            final ArrayList<String> dictTypesToCleanUp =
                    existingDictionariesToCleanup.get(localeToCleanUp);
            final DictionaryGroup dictionarySetToCleanup =
                    findDictionaryGroupWithLocale(oldDictionaryGroups, localeToCleanUp);
            for (final String dictType : dictTypesToCleanUp) {
                dictionarySetToCleanup.closeDict(dictType);
            }
        }
    }

    private void asyncReloadUninitializedMainDictionaries(final Context context,
            final Locale[] locales, final DictionaryInitializationListener listener) {
        final CountDownLatch latchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        mLatchForWaitingLoadingMainDictionaries = latchForWaitingLoadingMainDictionary;
        ExecutorUtils.getExecutor("InitializeBinaryDictionary").execute(new Runnable() {
            @Override
            public void run() {
                doReloadUninitializedMainDictionaries(
                        context, locales, listener, latchForWaitingLoadingMainDictionary);
            }
        });
    }

    void doReloadUninitializedMainDictionaries(final Context context, final Locale[] locales,
            final DictionaryInitializationListener listener,
            final CountDownLatch latchForWaitingLoadingMainDictionary) {
        for (final Locale locale : locales) {
            final DictionaryGroup dictionaryGroup =
                    findDictionaryGroupWithLocale(mDictionaryGroups, locale);
            if (null == dictionaryGroup) {
                // This should never happen, but better safe than crashy
                Log.w(TAG, "Expected a dictionary group for " + locale + " but none found");
                continue;
            }
            final Dictionary mainDict =
                    DictionaryFactory.createMainDictionaryFromManager(context, locale);
            synchronized (mLock) {
                if (locale.equals(dictionaryGroup.mLocale)) {
                    dictionaryGroup.setMainDict(mainDict);
                } else {
                    // Dictionary facilitator has been reset for another locale.
                    mainDict.close();
                }
            }
        }
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(
                    hasAtLeastOneInitializedMainDictionary());
        }
        latchForWaitingLoadingMainDictionary.countDown();
    }

    @UsedForTesting
    public void resetDictionariesForTesting(final Context context, final Locale[] locales,
            final ArrayList<String> dictionaryTypes, final HashMap<String, File> dictionaryFiles,
            final Map<String, Map<String, String>> additionalDictAttributes,
            @Nullable final String account) {
        Dictionary mainDictionary = null;
        final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();

        final DictionaryGroup[] dictionaryGroups = new DictionaryGroup[locales.length];
        for (int i = 0; i < locales.length; ++i) {
            final Locale locale = locales[i];
            for (final String dictType : dictionaryTypes) {
                if (dictType.equals(Dictionary.TYPE_MAIN)) {
                    mainDictionary = DictionaryFactory.createMainDictionaryFromManager(context,
                            locale);
                } else {
                    final File dictFile = dictionaryFiles.get(dictType);
                    final ExpandableBinaryDictionary dict = getSubDict(
                            dictType, context, locale, dictFile, "" /* dictNamePrefix */, account);
                    if (additionalDictAttributes.containsKey(dictType)) {
                        dict.clearAndFlushDictionaryWithAdditionalAttributes(
                                additionalDictAttributes.get(dictType));
                    }
                    if (dict == null) {
                        throw new RuntimeException("Unknown dictionary type: " + dictType);
                    }
                    dict.reloadDictionaryIfRequired();
                    dict.waitAllTasksForTests();
                    subDicts.put(dictType, dict);
                }
            }
            dictionaryGroups[i] = new DictionaryGroup(locale, mainDictionary, account, subDicts);
        }
        mDictionaryGroups = dictionaryGroups;
        mMostProbableDictionaryGroup = dictionaryGroups[0];
    }

    public void closeDictionaries() {
        final DictionaryGroup[] dictionaryGroups;
        synchronized (mLock) {
            dictionaryGroups = mDictionaryGroups;
            mMostProbableDictionaryGroup = new DictionaryGroup();
            mDictionaryGroups = new DictionaryGroup[] { mMostProbableDictionaryGroup };
        }
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS) {
                dictionaryGroup.closeDict(dictType);
            }
        }
        mDistracterFilter.close();
    }

    @UsedForTesting
    public ExpandableBinaryDictionary getSubDictForTesting(final String dictName) {
        return mMostProbableDictionaryGroup.getSubDict(dictName);
    }

    // The main dictionaries are loaded asynchronously.  Don't cache the return value
    // of these methods.
    public boolean hasAtLeastOneInitializedMainDictionary() {
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            final Dictionary mainDict = dictionaryGroup.getDict(Dictionary.TYPE_MAIN);
            if (mainDict != null && mainDict.isInitialized()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAtLeastOneUninitializedMainDictionary() {
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            final Dictionary mainDict = dictionaryGroup.getDict(Dictionary.TYPE_MAIN);
            if (mainDict == null || !mainDict.isInitialized()) {
                return true;
            }
        }
        return false;
    }

    public void waitForLoadingMainDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        mLatchForWaitingLoadingMainDictionaries.await(timeout, unit);
    }

    @UsedForTesting
    public void waitForLoadingDictionariesForTesting(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        waitForLoadingMainDictionaries(timeout, unit);
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            for (final ExpandableBinaryDictionary dict : dictionaryGroup.mSubDictMap.values()) {
                dict.waitAllTasksForTests();
            }
        }
    }

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @Nonnull final NgramContext ngramContext, final int timeStampInSeconds,
            final boolean blockPotentiallyOffensive) {
        final DictionaryGroup dictionaryGroup = getDictionaryGroupForMostProbableLanguage();
        final String[] words = suggestion.split(Constants.WORD_SEPARATOR);
        NgramContext ngramContextForCurrentWord = ngramContext;
        for (int i = 0; i < words.length; i++) {
            final String currentWord = words[i];
            final boolean wasCurrentWordAutoCapitalized = (i == 0) ? wasAutoCapitalized : false;
            addWordToUserHistory(dictionaryGroup, ngramContextForCurrentWord, currentWord,
                    wasCurrentWordAutoCapitalized, timeStampInSeconds, blockPotentiallyOffensive);
            ngramContextForCurrentWord =
                    ngramContextForCurrentWord.getNextNgramContext(new WordInfo(currentWord));
        }
    }

    private void addWordToUserHistory(final DictionaryGroup dictionaryGroup,
            final NgramContext ngramContext, final String word, final boolean wasAutoCapitalized,
            final int timeStampInSeconds, final boolean blockPotentiallyOffensive) {
        final ExpandableBinaryDictionary userHistoryDictionary =
                dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userHistoryDictionary == null
                || !isConfidentAboutCurrentLanguageBeing(userHistoryDictionary.mLocale)) {
            return;
        }
        final int maxFreq = getFrequency(word);
        if (maxFreq == 0 && blockPotentiallyOffensive) {
            return;
        }
        final String lowerCasedWord = word.toLowerCase(dictionaryGroup.mLocale);
        final String secondWord;
        if (wasAutoCapitalized) {
            if (isValidWord(word, false /* ignoreCase */)
                    && !isValidWord(lowerCasedWord, false /* ignoreCase */)) {
                // If the word was auto-capitalized and exists only as a capitalized word in the
                // dictionary, then we must not downcase it before registering it. For example,
                // the name of the contacts in start-of-sentence position would come here with the
                // wasAutoCapitalized flag: if we downcase it, we'd register a lower-case version
                // of that contact's name which would end up popping in suggestions.
                secondWord = word;
            } else {
                // If however the word is not in the dictionary, or exists as a lower-case word
                // only, then we consider that was a lower-case word that had been auto-capitalized.
                secondWord = lowerCasedWord;
            }
        } else {
            // HACK: We'd like to avoid adding the capitalized form of common words to the User
            // History dictionary in order to avoid suggesting them until the dictionary
            // consolidation is done.
            // TODO: Remove this hack when ready.
            final int lowerCaseFreqInMainDict = dictionaryGroup.hasDict(Dictionary.TYPE_MAIN,
                    null /* account */) ?
                    dictionaryGroup.getDict(Dictionary.TYPE_MAIN).getFrequency(lowerCasedWord) :
                    Dictionary.NOT_A_PROBABILITY;
            if (maxFreq < lowerCaseFreqInMainDict
                    && lowerCaseFreqInMainDict >= CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT) {
                // Use lower cased word as the word can be a distracter of the popular word.
                secondWord = lowerCasedWord;
            } else {
                secondWord = word;
            }
        }
        // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
        // We don't add words with 0-frequency (assuming they would be profanity etc.).
        final boolean isValid = maxFreq > 0;
        UserHistoryDictionary.addToDictionary(userHistoryDictionary, ngramContext, secondWord,
                isValid, timeStampInSeconds,
                new DistracterFilterCheckingIsInDictionary(
                        mDistracterFilter, userHistoryDictionary));
    }

    private void removeWord(final String dictName, final String word) {
        final ExpandableBinaryDictionary dictionary =
                getDictionaryGroupForMostProbableLanguage().getSubDict(dictName);
        if (dictionary != null) {
            dictionary.removeUnigramEntryDynamically(word);
        }
    }

    public void removeWordFromPersonalizedDicts(final String word) {
        removeWord(Dictionary.TYPE_USER_HISTORY, word);
    }

    // TODO: Revise the way to fusion suggestion results.
    public SuggestionResults getSuggestionResults(final WordComposer composer,
            final NgramContext ngramContext, final long proximityInfoHandle,
            final SettingsValuesForSuggestion settingsValuesForSuggestion, final int sessionId) {
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        final SuggestionResults suggestionResults = new SuggestionResults(
                SuggestedWords.MAX_SUGGESTIONS, ngramContext.isBeginningOfSentenceContext());
        final float[] weightOfLangModelVsSpatialModel =
                new float[] { Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL };
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS) {
                final Dictionary dictionary = dictionaryGroup.getDict(dictType);
                if (null == dictionary) continue;
                final float weightForLocale = composer.isBatchMode()
                        ? dictionaryGroup.mWeightForGesturingInLocale
                        : dictionaryGroup.mWeightForTypingInLocale;
                final ArrayList<SuggestedWordInfo> dictionarySuggestions =
                        dictionary.getSuggestions(composer.getComposedDataSnapshot(), ngramContext,
                                proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                                weightForLocale, weightOfLangModelVsSpatialModel);
                if (null == dictionarySuggestions) continue;
                suggestionResults.addAll(dictionarySuggestions);
                if (null != suggestionResults.mRawSuggestions) {
                    suggestionResults.mRawSuggestions.addAll(dictionarySuggestions);
                }
            }
        }
        return suggestionResults;
    }

    public boolean isValidWord(final String word, final boolean ignoreCase) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            if (dictionaryGroup.mLocale == null) {
                continue;
            }
            final String lowerCasedWord = word.toLowerCase(dictionaryGroup.mLocale);
            for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS) {
                final Dictionary dictionary = dictionaryGroup.getDict(dictType);
                // Ideally the passed map would come out of a {@link java.util.concurrent.Future} and
                // would be immutable once it's finished initializing, but concretely a null test is
                // probably good enough for the time being.
                if (null == dictionary) continue;
                if (dictionary.isValidWord(word)
                        || (ignoreCase && dictionary.isValidWord(lowerCasedWord))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getFrequencyInternal(final String word,
            final boolean isGettingMaxFrequencyOfExactMatches) {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY;
        }
        int maxFreq = Dictionary.NOT_A_PROBABILITY;
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS) {
                final Dictionary dictionary = dictionaryGroup.getDict(dictType);
                if (dictionary == null) continue;
                final int tempFreq;
                if (isGettingMaxFrequencyOfExactMatches) {
                    tempFreq = dictionary.getMaxFrequencyOfExactMatches(word);
                } else {
                    tempFreq = dictionary.getFrequency(word);
                }
                if (tempFreq >= maxFreq) {
                    maxFreq = tempFreq;
                }
            }
        }
        return maxFreq;
    }

    public int getFrequency(final String word) {
        return getFrequencyInternal(word, false /* isGettingMaxFrequencyOfExactMatches */);
    }

    public int getMaxFrequencyOfExactMatches(final String word) {
        return getFrequencyInternal(word, true /* isGettingMaxFrequencyOfExactMatches */);
    }

    private void clearSubDictionary(final String dictName) {
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            final ExpandableBinaryDictionary dictionary = dictionaryGroup.getSubDict(dictName);
            if (dictionary != null) {
                dictionary.clear();
            }
        }
    }

    public void clearUserHistoryDictionary() {
        clearSubDictionary(Dictionary.TYPE_USER_HISTORY);
    }

    public void dumpDictionaryForDebug(final String dictName) {
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            final ExpandableBinaryDictionary dictToDump = dictionaryGroup.getSubDict(dictName);
            if (dictToDump == null) {
                Log.e(TAG, "Cannot dump " + dictName + ". "
                        + "The dictionary is not being used for suggestion or cannot be dumped.");
                return;
            }
            dictToDump.dumpAllWordsForDebug();
        }
    }

    public ArrayList<Pair<String, DictionaryStats>> getStatsOfEnabledSubDicts() {
        final ArrayList<Pair<String, DictionaryStats>> statsOfEnabledSubDicts = new ArrayList<>();
        final DictionaryGroup[] dictionaryGroups = mDictionaryGroups;
        for (final DictionaryGroup dictionaryGroup : dictionaryGroups) {
            for (final String dictType : SUB_DICT_TYPES) {
                final ExpandableBinaryDictionary dictionary = dictionaryGroup.getSubDict(dictType);
                if (dictionary == null) continue;
                statsOfEnabledSubDicts.add(new Pair<>(dictType, dictionary.getDictionaryStats()));
            }
        }
        return statsOfEnabledSubDicts;
    }
}
