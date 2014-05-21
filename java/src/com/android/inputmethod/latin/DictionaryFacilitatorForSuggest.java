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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.personalization.ContextualDictionary;
import com.android.inputmethod.latin.personalization.PersonalizationDictionary;
import com.android.inputmethod.latin.personalization.UserHistoryDictionary;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.ExecutorUtils;
import com.android.inputmethod.latin.utils.LanguageModelParam;
import com.android.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// TODO: Consolidate dictionaries in native code.
public class DictionaryFacilitatorForSuggest {
    public static final String TAG = DictionaryFacilitatorForSuggest.class.getSimpleName();

    // HACK: This threshold is being used when adding a capitalized entry in the User History
    // dictionary.
    private static final int CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT = 140;

    private Dictionaries mDictionaries = new Dictionaries();
    private boolean mIsUserDictEnabled = false;
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionary = new CountDownLatch(0);
    // To synchronize assigning mDictionaries to ensure closing dictionaries.
    private final Object mLock = new Object();

    private static final String[] DICT_TYPES_ORDERED_TO_GET_SUGGESTION =
            new String[] {
                Dictionary.TYPE_MAIN,
                Dictionary.TYPE_USER_HISTORY,
                Dictionary.TYPE_PERSONALIZATION,
                Dictionary.TYPE_USER,
                Dictionary.TYPE_CONTACTS,
                Dictionary.TYPE_CONTEXTUAL
            };

    private static final Map<String, Class<? extends ExpandableBinaryDictionary>>
            DICT_TYPE_TO_CLASS = CollectionUtils.newHashMap();

    static {
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER_HISTORY, UserHistoryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_PERSONALIZATION, PersonalizationDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER, UserBinaryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_CONTACTS, ContactsBinaryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_CONTEXTUAL, ContextualDictionary.class);
    }

    private static final String DICT_FACTORY_METHOD_NAME = "getDictionary";
    private static final Class<?>[] DICT_FACTORY_METHOD_ARG_TYPES =
            new Class[] { Context.class, Locale.class, File.class };

    private static final String[] SUB_DICT_TYPES =
            Arrays.copyOfRange(DICT_TYPES_ORDERED_TO_GET_SUGGESTION, 1 /* start */,
                    DICT_TYPES_ORDERED_TO_GET_SUGGESTION.length);

    /**
     * Class contains dictionaries for a locale.
     */
    private static class Dictionaries {
        public final Locale mLocale;
        private Dictionary mMainDict;
        public final ConcurrentHashMap<String, ExpandableBinaryDictionary> mSubDictMap =
                CollectionUtils.newConcurrentHashMap();

        public Dictionaries() {
            mLocale = null;
        }

        public Dictionaries(final Locale locale, final Dictionary mainDict,
                final Map<String, ExpandableBinaryDictionary> subDicts) {
            mLocale = locale;
            // Main dictionary can be asynchronously loaded.
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
            } else {
                return getSubDict(dictType);
            }
        }

        public ExpandableBinaryDictionary getSubDict(final String dictType) {
            return mSubDictMap.get(dictType);
        }

        public boolean hasDict(final String dictType) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict != null;
            } else {
                return mSubDictMap.containsKey(dictType);
            }
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

    public interface DictionaryInitializationListener {
        public void onUpdateMainDictionaryAvailability(boolean isMainDictionaryAvailable);
    }

    public DictionaryFacilitatorForSuggest() {}

    public Locale getLocale() {
        return mDictionaries.mLocale;
    }

    private static ExpandableBinaryDictionary getSubDict(final String dictType,
            final Context context, final Locale locale, final File dictFile) {
        final Class<? extends ExpandableBinaryDictionary> dictClass =
                DICT_TYPE_TO_CLASS.get(dictType);
        if (dictClass == null) {
            return null;
        }
        try {
            final Method factoryMethod = dictClass.getMethod(DICT_FACTORY_METHOD_NAME,
                    DICT_FACTORY_METHOD_ARG_TYPES);
            final Object dict = factoryMethod.invoke(null /* obj */,
                    new Object[] { context, locale, dictFile });
            return (ExpandableBinaryDictionary) dict;
        } catch (final NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            Log.e(TAG, "Cannot create dictionary: " + dictType, e);
            return null;
        }
    }

    public void resetDictionaries(final Context context, final Locale newLocale,
            final boolean useContactsDict, final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            final DictionaryInitializationListener listener) {
        final boolean localeHasBeenChanged = !newLocale.equals(mDictionaries.mLocale);
        // We always try to have the main dictionary. Other dictionaries can be unused.
        final boolean reloadMainDictionary = localeHasBeenChanged || forceReloadMainDictionary;
        // TODO: Make subDictTypesToUse configurable by resource or a static final list.
        final Set<String> subDictTypesToUse = CollectionUtils.newHashSet();
        if (useContactsDict) {
            subDictTypesToUse.add(Dictionary.TYPE_CONTACTS);
        }
        subDictTypesToUse.add(Dictionary.TYPE_USER);
        if (usePersonalizedDicts) {
            subDictTypesToUse.add(Dictionary.TYPE_USER_HISTORY);
            subDictTypesToUse.add(Dictionary.TYPE_PERSONALIZATION);
            subDictTypesToUse.add(Dictionary.TYPE_CONTEXTUAL);
        }

        final Dictionary newMainDict;
        if (reloadMainDictionary) {
            // The main dictionary will be asynchronously loaded.
            newMainDict = null;
        } else {
            newMainDict = mDictionaries.getDict(Dictionary.TYPE_MAIN);
        }

        final Map<String, ExpandableBinaryDictionary> subDicts = CollectionUtils.newHashMap();
        for (final String dictType : SUB_DICT_TYPES) {
            if (!subDictTypesToUse.contains(dictType)) {
                // This dictionary will not be used.
                continue;
            }
            final ExpandableBinaryDictionary dict;
            if (!localeHasBeenChanged && mDictionaries.hasDict(dictType)) {
                // Continue to use current dictionary.
                dict = mDictionaries.getSubDict(dictType);
            } else {
                // Start to use new dictionary.
                dict = getSubDict(dictType, context, newLocale, null /* dictFile */);
            }
            subDicts.put(dictType, dict);
        }

        // Replace Dictionaries.
        final Dictionaries newDictionaries = new Dictionaries(newLocale, newMainDict, subDicts);
        final Dictionaries oldDictionaries;
        synchronized (mLock) {
            oldDictionaries = mDictionaries;
            mDictionaries = newDictionaries;
            mIsUserDictEnabled = UserBinaryDictionary.isEnabled(context);
            if (reloadMainDictionary) {
                asyncReloadMainDictionary(context, newLocale, listener);
            }
        }
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasInitializedMainDictionary());
        }
        // Clean up old dictionaries.
        if (reloadMainDictionary) {
            oldDictionaries.closeDict(Dictionary.TYPE_MAIN);
        }
        for (final String dictType : SUB_DICT_TYPES) {
            if (localeHasBeenChanged || !subDictTypesToUse.contains(dictType)) {
                oldDictionaries.closeDict(dictType);
            }
        }
        oldDictionaries.mSubDictMap.clear();
    }

    private void asyncReloadMainDictionary(final Context context, final Locale locale,
            final DictionaryInitializationListener listener) {
        final CountDownLatch latchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        mLatchForWaitingLoadingMainDictionary = latchForWaitingLoadingMainDictionary;
        ExecutorUtils.getExecutor("InitializeBinaryDictionary").execute(new Runnable() {
            @Override
            public void run() {
                final Dictionary mainDict =
                        DictionaryFactory.createMainDictionaryFromManager(context, locale);
                synchronized (mLock) {
                    if (locale.equals(mDictionaries.mLocale)) {
                        mDictionaries.setMainDict(mainDict);
                    } else {
                        // Dictionary facilitator has been reset for another locale.
                        mainDict.close();
                    }
                }
                if (listener != null) {
                    listener.onUpdateMainDictionaryAvailability(hasInitializedMainDictionary());
                }
                latchForWaitingLoadingMainDictionary.countDown();
            }
        });
    }

    @UsedForTesting
    public void resetDictionariesForTesting(final Context context, final Locale locale,
            final ArrayList<String> dictionaryTypes, final HashMap<String, File> dictionaryFiles,
            final Map<String, Map<String, String>> additionalDictAttributes) {
        Dictionary mainDictionary = null;
        final Map<String, ExpandableBinaryDictionary> subDicts = CollectionUtils.newHashMap();

        for (final String dictType : dictionaryTypes) {
            if (dictType.equals(Dictionary.TYPE_MAIN)) {
                mainDictionary = DictionaryFactory.createMainDictionaryFromManager(context, locale);
            } else {
                final File dictFile = dictionaryFiles.get(dictType);
                final ExpandableBinaryDictionary dict = getSubDict(
                        dictType, context, locale, dictFile);
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
        mDictionaries = new Dictionaries(locale, mainDictionary, subDicts);
    }

    public void closeDictionaries() {
        final Dictionaries dictionaries;
        synchronized (mLock) {
            dictionaries = mDictionaries;
            mDictionaries = new Dictionaries();
        }
        for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTION) {
            dictionaries.closeDict(dictType);
        }
    }

    // The main dictionary could have been loaded asynchronously.  Don't cache the return value
    // of this method.
    public boolean hasInitializedMainDictionary() {
        final Dictionary mainDict = mDictionaries.getDict(Dictionary.TYPE_MAIN);
        return mainDict != null && mainDict.isInitialized();
    }

    public boolean hasPersonalizationDictionary() {
        return mDictionaries.hasDict(Dictionary.TYPE_PERSONALIZATION);
    }

    public void flushPersonalizationDictionary() {
        final ExpandableBinaryDictionary personalizationDict =
                mDictionaries.getSubDict(Dictionary.TYPE_PERSONALIZATION);
        if (personalizationDict != null) {
            personalizationDict.asyncFlushBinaryDictionary();
        }
    }

    public void waitForLoadingMainDictionary(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        mLatchForWaitingLoadingMainDictionary.await(timeout, unit);
    }

    @UsedForTesting
    public void waitForLoadingDictionariesForTesting(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        waitForLoadingMainDictionary(timeout, unit);
        final Map<String, ExpandableBinaryDictionary> dictMap = mDictionaries.mSubDictMap;
        for (final ExpandableBinaryDictionary dict : dictMap.values()) {
            dict.waitAllTasksForTests();
        }
    }

    public boolean isUserDictionaryEnabled() {
        return mIsUserDictEnabled;
    }

    public void addWordToUserDictionary(final Context context, final String word) {
        final Locale locale = getLocale();
        if (locale == null) {
            return;
        }
        UserBinaryDictionary.addWordToUserDictionary(context, locale, word);
    }

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            final PrevWordsInfo prevWordsInfo, final int timeStampInSeconds,
            final boolean blockPotentiallyOffensive) {
        final Dictionaries dictionaries = mDictionaries;
        final String[] words = suggestion.split(Constants.WORD_SEPARATOR);
        for (int i = 0; i < words.length; i++) {
            final String currentWord = words[i];
            final PrevWordsInfo prevWordsInfoForCurrentWord =
                    (i == 0) ? prevWordsInfo : new PrevWordsInfo(words[i - 1]);
            final boolean wasCurrentWordAutoCapitalized = (i == 0) ? wasAutoCapitalized : false;
            addWordToUserHistory(dictionaries, prevWordsInfoForCurrentWord, currentWord,
                    wasCurrentWordAutoCapitalized, timeStampInSeconds, blockPotentiallyOffensive);
        }
    }

    private void addWordToUserHistory(final Dictionaries dictionaries,
            final PrevWordsInfo prevWordsInfo, final String word, final boolean wasAutoCapitalized,
            final int timeStampInSeconds, final boolean blockPotentiallyOffensive) {
        final ExpandableBinaryDictionary userHistoryDictionary =
                dictionaries.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userHistoryDictionary == null) {
            return;
        }
        final int maxFreq = getMaxFrequency(word);
        if (maxFreq == 0 && blockPotentiallyOffensive) {
            return;
        }
        final String lowerCasedWord = word.toLowerCase(dictionaries.mLocale);
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
            final int lowerCaseFreqInMainDict = dictionaries.hasDict(Dictionary.TYPE_MAIN) ?
                    dictionaries.getDict(Dictionary.TYPE_MAIN).getFrequency(lowerCasedWord) :
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
        UserHistoryDictionary.addToDictionary(userHistoryDictionary, prevWordsInfo, secondWord,
                isValid, timeStampInSeconds);
    }

    public void cancelAddingUserHistory(final PrevWordsInfo prevWordsInfo,
            final String committedWord) {
        final ExpandableBinaryDictionary userHistoryDictionary =
                mDictionaries.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userHistoryDictionary != null) {
            userHistoryDictionary.removeNgramDynamically(prevWordsInfo, committedWord);
        }
    }

    // TODO: Revise the way to fusion suggestion results.
    public SuggestionResults getSuggestionResults(final WordComposer composer,
            final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions,
            final int sessionId, final ArrayList<SuggestedWordInfo> rawSuggestions) {
        final Dictionaries dictionaries = mDictionaries;
        final SuggestionResults suggestionResults =
                new SuggestionResults(dictionaries.mLocale, SuggestedWords.MAX_SUGGESTIONS);
        final float[] languageWeight = new float[] { Dictionary.NOT_A_LANGUAGE_WEIGHT };
        for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTION) {
            final Dictionary dictionary = dictionaries.getDict(dictType);
            if (null == dictionary) continue;
            final ArrayList<SuggestedWordInfo> dictionarySuggestions =
                    dictionary.getSuggestions(composer, prevWordsInfo, proximityInfo,
                            blockOffensiveWords, additionalFeaturesOptions, sessionId,
                            languageWeight);
            if (null == dictionarySuggestions) continue;
            suggestionResults.addAll(dictionarySuggestions);
            if (null != rawSuggestions) {
                rawSuggestions.addAll(dictionarySuggestions);
            }
        }
        return suggestionResults;
    }

    public boolean isValidMainDictWord(final String word) {
        final Dictionary mainDict = mDictionaries.getDict(Dictionary.TYPE_MAIN);
        if (TextUtils.isEmpty(word) || mainDict == null) {
            return false;
        }
        return mainDict.isValidWord(word);
    }

    public boolean isValidWord(final String word, final boolean ignoreCase) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        final Dictionaries dictionaries = mDictionaries;
        if (dictionaries.mLocale == null) {
            return false;
        }
        final String lowerCasedWord = word.toLowerCase(dictionaries.mLocale);
        for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTION) {
            final Dictionary dictionary = dictionaries.getDict(dictType);
            // Ideally the passed map would come out of a {@link java.util.concurrent.Future} and
            // would be immutable once it's finished initializing, but concretely a null test is
            // probably good enough for the time being.
            if (null == dictionary) continue;
            if (dictionary.isValidWord(word)
                    || (ignoreCase && dictionary.isValidWord(lowerCasedWord))) {
                return true;
            }
        }
        return false;
    }

    private int getMaxFrequency(final String word) {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY;
        }
        int maxFreq = -1;
        final Dictionaries dictionaries = mDictionaries;
        for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTION) {
            final Dictionary dictionary = dictionaries.getDict(dictType);
            if (dictionary == null) continue;
            final int tempFreq = dictionary.getFrequency(word);
            if (tempFreq >= maxFreq) {
                maxFreq = tempFreq;
            }
        }
        return maxFreq;
    }

    public void clearUserHistoryDictionary() {
        final ExpandableBinaryDictionary userHistoryDict =
                mDictionaries.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userHistoryDict == null) {
            return;
        }
        userHistoryDict.clear();
    }

    // This method gets called only when the IME receives a notification to remove the
    // personalization dictionary.
    public void clearPersonalizationDictionary() {
        final ExpandableBinaryDictionary personalizationDict =
                mDictionaries.getSubDict(Dictionary.TYPE_PERSONALIZATION);
        if (personalizationDict == null) {
            return;
        }
        personalizationDict.clear();
    }

    public void addMultipleDictionaryEntriesToPersonalizationDictionary(
            final ArrayList<LanguageModelParam> languageModelParams,
            final ExpandableBinaryDictionary.AddMultipleDictionaryEntriesCallback callback) {
        final ExpandableBinaryDictionary personalizationDict =
                mDictionaries.getSubDict(Dictionary.TYPE_PERSONALIZATION);
        if (personalizationDict == null || languageModelParams == null
                || languageModelParams.isEmpty()) {
            if (callback != null) {
                callback.onFinished();
            }
            return;
        }
        personalizationDict.addMultipleDictionaryEntriesDynamically(languageModelParams, callback);
    }

    public void dumpDictionaryForDebug(final String dictName) {
        final ExpandableBinaryDictionary dictToDump = mDictionaries.getSubDict(dictName);
        if (dictToDump == null) {
            Log.e(TAG, "Cannot dump " + dictName + ". "
                    + "The dictionary is not being used for suggestion or cannot be dumped.");
            return;
        }
        dictToDump.dumpAllWordsForDebug();
    }
}
