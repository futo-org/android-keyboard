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
import com.android.inputmethod.latin.personalization.PersonalizationDictionary;
import com.android.inputmethod.latin.personalization.PersonalizationHelper;
import com.android.inputmethod.latin.personalization.UserHistoryDictionary;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.ExecutorUtils;
import com.android.inputmethod.latin.utils.LanguageModelParam;
import com.android.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.util.ArrayList;
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
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionary = new CountDownLatch(0);
    // To synchronize assigning mDictionaries to ensure closing dictionaries.
    private Object mLock = new Object();

    /**
     * Class contains dictionaries for a locale.
     */
    private static class Dictionaries {
        public final Locale mLocale;
        public final ConcurrentHashMap<String, Dictionary> mDictMap =
                CollectionUtils.newConcurrentHashMap();
        // Main dictionary will be asynchronously loaded.
        public Dictionary mMainDictionary;
        public final ContactsBinaryDictionary mContactsDictionary;
        public final UserBinaryDictionary mUserDictionary;
        public final UserHistoryDictionary mUserHistoryDictionary;
        public final PersonalizationDictionary mPersonalizationDictionary;

        public Dictionaries() {
            mLocale = null;
            mMainDictionary = null;
            mContactsDictionary = null;
            mUserDictionary = null;
            mUserHistoryDictionary = null;
            mPersonalizationDictionary = null;
        }

        public Dictionaries(final Locale locale, final Dictionary mainDict,
            final ContactsBinaryDictionary contactsDict, final UserBinaryDictionary userDict,
            final UserHistoryDictionary userHistoryDict,
            final PersonalizationDictionary personalizationDict) {
            mLocale = locale;
            setMainDict(mainDict);
            mContactsDictionary = contactsDict;
            if (mContactsDictionary != null) {
                mDictMap.put(Dictionary.TYPE_CONTACTS, mContactsDictionary);
            }
            mUserDictionary = userDict;
            if (mUserDictionary != null) {
                mDictMap.put(Dictionary.TYPE_USER, mUserDictionary);
            }
            mUserHistoryDictionary = userHistoryDict;
            if (mUserHistoryDictionary != null) {
                mDictMap.put(Dictionary.TYPE_USER_HISTORY, mUserHistoryDictionary);
            }
            mPersonalizationDictionary = personalizationDict;
            if (mPersonalizationDictionary != null) {
                mDictMap.put(Dictionary.TYPE_PERSONALIZATION, mPersonalizationDictionary);
            }
        }

        public void setMainDict(final Dictionary mainDict) {
            mMainDictionary = mainDict;
            // Close old dictionary if exists. Main dictionary can be assigned multiple times.
            final Dictionary oldDict;
            if (mMainDictionary != null) {
                oldDict = mDictMap.put(Dictionary.TYPE_MAIN, mMainDictionary);
            } else {
                oldDict = mDictMap.remove(Dictionary.TYPE_MAIN);
            }
            if (oldDict != null && mMainDictionary != oldDict) {
                oldDict.close();
            }
        }

        public boolean hasMainDict() {
            return mMainDictionary != null;
        }

        public boolean hasContactsDict() {
            return mContactsDictionary != null;
        }

        public boolean hasUserDict() {
            return mUserDictionary != null;
        }

        public boolean hasUserHistoryDict() {
            return mUserHistoryDictionary != null;
        }

        public boolean hasPersonalizationDict() {
            return mPersonalizationDictionary != null;
        }
    }

    public interface DictionaryInitializationListener {
        public void onUpdateMainDictionaryAvailability(boolean isMainDictionaryAvailable);
    }

    public DictionaryFacilitatorForSuggest() {}

    public Locale getLocale() {
        return mDictionaries.mLocale;
    }

    public void resetDictionaries(final Context context, final Locale newLocale,
            final boolean useContactsDict, final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            final DictionaryInitializationListener listener) {
        final boolean localeHasBeenChanged = !newLocale.equals(mDictionaries.mLocale);
        // We always try to have the main dictionary. Other dictionaries can be unused.
        final boolean reloadMainDictionary = localeHasBeenChanged || forceReloadMainDictionary;
        final boolean closeContactsDictionary = localeHasBeenChanged || !useContactsDict;
        final boolean closeUserDictionary = localeHasBeenChanged;
        final boolean closeUserHistoryDictionary = localeHasBeenChanged || !usePersonalizedDicts;
        final boolean closePersonalizationDictionary =
                localeHasBeenChanged || !usePersonalizedDicts;

        final Dictionary newMainDict;
        if (reloadMainDictionary) {
            // The main dictionary will be asynchronously loaded.
            newMainDict = null;
        } else {
            newMainDict = mDictionaries.mMainDictionary;
        }

        // Open or move contacts dictionary.
        final ContactsBinaryDictionary newContactsDict;
        if (!closeContactsDictionary && mDictionaries.hasContactsDict()) {
            newContactsDict = mDictionaries.mContactsDictionary;
        } else if (useContactsDict) {
            newContactsDict = new ContactsBinaryDictionary(context, newLocale);
        } else {
            newContactsDict = null;
        }

        // Open or move user dictionary.
        final UserBinaryDictionary newUserDictionary;
        if (!closeUserDictionary && mDictionaries.hasUserDict()) {
            newUserDictionary = mDictionaries.mUserDictionary;
        } else {
            newUserDictionary = new UserBinaryDictionary(context, newLocale);
        }

        // Open or move user history dictionary.
        final UserHistoryDictionary newUserHistoryDict;
        if (!closeUserHistoryDictionary && mDictionaries.hasUserHistoryDict()) {
            newUserHistoryDict = mDictionaries.mUserHistoryDictionary;
        } else if (usePersonalizedDicts) {
            newUserHistoryDict = PersonalizationHelper.getUserHistoryDictionary(context, newLocale);
        } else {
            newUserHistoryDict = null;
        }

        // Open or move personalization dictionary.
        final PersonalizationDictionary newPersonalizationDict;
        if (!closePersonalizationDictionary && mDictionaries.hasPersonalizationDict()) {
            newPersonalizationDict = mDictionaries.mPersonalizationDictionary;
        } else if (usePersonalizedDicts) {
            newPersonalizationDict =
                    PersonalizationHelper.getPersonalizationDictionary(context, newLocale);
        } else {
            newPersonalizationDict = null;
        }

        // Replace Dictionaries.
        final Dictionaries newDictionaries = new Dictionaries(newLocale, newMainDict,
                newContactsDict,  newUserDictionary, newUserHistoryDict, newPersonalizationDict);
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(newDictionaries.hasMainDict());
        }
        final Dictionaries oldDictionaries;
        synchronized (mLock) {
            oldDictionaries = mDictionaries;
            mDictionaries = newDictionaries;
            if (reloadMainDictionary) {
                asyncReloadMainDictionary(context, newLocale, listener);
            }
        }

        // Clean up old dictionaries.
        oldDictionaries.mDictMap.clear();
        if (reloadMainDictionary && oldDictionaries.hasMainDict()) {
            oldDictionaries.mMainDictionary.close();
        }
        if (closeContactsDictionary && oldDictionaries.hasContactsDict()) {
            oldDictionaries.mContactsDictionary.close();
        }
        if (closeUserDictionary && oldDictionaries.hasUserDict()) {
            oldDictionaries.mUserDictionary.close();
        }
        if (closeUserHistoryDictionary && oldDictionaries.hasUserHistoryDict()) {
            oldDictionaries.mUserHistoryDictionary.close();
        }
        if (closePersonalizationDictionary && oldDictionaries.hasPersonalizationDict()) {
            oldDictionaries.mPersonalizationDictionary.close();
        }
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
                    listener.onUpdateMainDictionaryAvailability(mDictionaries.hasMainDict());
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
        ContactsBinaryDictionary contactsDictionary = null;
        UserBinaryDictionary userDictionary = null;
        UserHistoryDictionary userHistoryDictionary = null;
        PersonalizationDictionary personalizationDictionary = null;

        for (final String dictType : dictionaryTypes) {
            if (dictType.equals(Dictionary.TYPE_MAIN)) {
                mainDictionary = DictionaryFactory.createMainDictionaryFromManager(context, locale);
            } else if (dictType.equals(Dictionary.TYPE_USER_HISTORY)) {
                userHistoryDictionary =
                        PersonalizationHelper.getUserHistoryDictionary(context, locale);
                // Staring with an empty user history dictionary for testing.
                // Testing program may populate this dictionary before actual testing.
                userHistoryDictionary.reloadDictionaryIfRequired();
                userHistoryDictionary.waitAllTasksForTests();
                if (additionalDictAttributes.containsKey(dictType)) {
                    userHistoryDictionary.clearAndFlushDictionaryWithAdditionalAttributes(
                            additionalDictAttributes.get(dictType));
                }
            } else if (dictType.equals(Dictionary.TYPE_PERSONALIZATION)) {
                personalizationDictionary =
                        PersonalizationHelper.getPersonalizationDictionary(context, locale);
                // Staring with an empty personalization dictionary for testing.
                // Testing program may populate this dictionary before actual testing.
                personalizationDictionary.reloadDictionaryIfRequired();
                personalizationDictionary.waitAllTasksForTests();
                if (additionalDictAttributes.containsKey(dictType)) {
                    personalizationDictionary.clearAndFlushDictionaryWithAdditionalAttributes(
                            additionalDictAttributes.get(dictType));
                }
            } else if (dictType.equals(Dictionary.TYPE_USER)) {
                final File file = dictionaryFiles.get(dictType);
                userDictionary = new UserBinaryDictionary(context, locale, file);
                userDictionary.reloadDictionaryIfRequired();
                userDictionary.waitAllTasksForTests();
            } else if (dictType.equals(Dictionary.TYPE_CONTACTS)) {
                final File file = dictionaryFiles.get(dictType);
                contactsDictionary = new ContactsBinaryDictionary(context, locale, file);
                contactsDictionary.reloadDictionaryIfRequired();
                contactsDictionary.waitAllTasksForTests();
            } else {
                throw new RuntimeException("Unknown dictionary type: " + dictType);
            }
        }
        mDictionaries = new Dictionaries(locale, mainDictionary, contactsDictionary,
                userDictionary, userHistoryDictionary, personalizationDictionary);
    }

    public void closeDictionaries() {
        final Dictionaries dictionaries;
        synchronized (mLock) {
            dictionaries = mDictionaries;
            mDictionaries = new Dictionaries();
        }
        if (dictionaries.hasMainDict()) {
            dictionaries.mMainDictionary.close();
        }
        if (dictionaries.hasContactsDict()) {
            dictionaries.mContactsDictionary.close();
        }
        if (dictionaries.hasUserDict()) {
            dictionaries.mUserDictionary.close();
        }
        if (dictionaries.hasUserHistoryDict()) {
            dictionaries.mUserHistoryDictionary.close();
        }
        if (dictionaries.hasPersonalizationDict()) {
            dictionaries.mPersonalizationDictionary.close();
        }
    }

    // The main dictionary could have been loaded asynchronously.  Don't cache the return value
    // of this method.
    public boolean hasInitializedMainDictionary() {
        final Dictionaries dictionaries = mDictionaries;
        return dictionaries.hasMainDict() && dictionaries.mMainDictionary.isInitialized();
    }

    public boolean hasPersonalizationDictionary() {
        return mDictionaries.hasPersonalizationDict();
    }

    public void flushPersonalizationDictionary() {
        final PersonalizationDictionary personalizationDict =
                mDictionaries.mPersonalizationDictionary;
        if (personalizationDict != null) {
            personalizationDict.flush();
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
        final Dictionaries dictionaries = mDictionaries;
        if (dictionaries.hasContactsDict()) {
            dictionaries.mContactsDictionary.waitAllTasksForTests();
        }
        if (dictionaries.hasUserDict()) {
            dictionaries.mUserDictionary.waitAllTasksForTests();
        }
        if (dictionaries.hasUserHistoryDict()) {
            dictionaries.mUserHistoryDictionary.waitAllTasksForTests();
        }
        if (dictionaries.hasPersonalizationDict()) {
            dictionaries.mPersonalizationDictionary.waitAllTasksForTests();
        }
    }

    public boolean isUserDictionaryEnabled() {
        final UserBinaryDictionary userDictionary = mDictionaries.mUserDictionary;
        if (userDictionary == null) {
            return false;
        }
        return userDictionary.mEnabled;
    }

    public void addWordToUserDictionary(String word) {
        final UserBinaryDictionary userDictionary = mDictionaries.mUserDictionary;
        if (userDictionary == null) {
            return;
        }
        userDictionary.addWordToUserDictionary(word);
    }

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            final String previousWord, final int timeStampInSeconds) {
        final Dictionaries dictionaries = mDictionaries;
        if (!dictionaries.hasUserHistoryDict()) {
            return;
        }
        final int maxFreq = getMaxFrequency(suggestion);
        if (maxFreq == 0) {
            return;
        }
        final String suggestionLowerCase = suggestion.toLowerCase(dictionaries.mLocale);
        final String secondWord;
        if (wasAutoCapitalized) {
            secondWord = suggestionLowerCase;
        } else {
            // HACK: We'd like to avoid adding the capitalized form of common words to the User
            // History dictionary in order to avoid suggesting them until the dictionary
            // consolidation is done.
            // TODO: Remove this hack when ready.
            final int lowerCaseFreqInMainDict = dictionaries.hasMainDict() ?
                    dictionaries.mMainDictionary.getFrequency(suggestionLowerCase) :
                            Dictionary.NOT_A_PROBABILITY;
            if (maxFreq < lowerCaseFreqInMainDict
                    && lowerCaseFreqInMainDict >= CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT) {
                // Use lower cased word as the word can be a distracter of the popular word.
                secondWord = suggestionLowerCase;
            } else {
                secondWord = suggestion;
            }
        }
        // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
        // We don't add words with 0-frequency (assuming they would be profanity etc.).
        final boolean isValid = maxFreq > 0;
        dictionaries.mUserHistoryDictionary.addToDictionary(
                previousWord, secondWord, isValid, timeStampInSeconds);
    }

    public void cancelAddingUserHistory(final String previousWord, final String committedWord) {
        final UserHistoryDictionary userHistoryDictionary = mDictionaries.mUserHistoryDictionary;
        if (userHistoryDictionary != null) {
            userHistoryDictionary.cancelAddingUserHistory(previousWord, committedWord);
        }
    }

    // TODO: Revise the way to fusion suggestion results.
    public SuggestionResults getSuggestionResults(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions,
            final int sessionId, final ArrayList<SuggestedWordInfo> rawSuggestions) {
        final Dictionaries dictionaries = mDictionaries;
        final Map<String, Dictionary> dictMap = dictionaries.mDictMap;
        final SuggestionResults suggestionResults =
                new SuggestionResults(dictionaries.mLocale, SuggestedWords.MAX_SUGGESTIONS);
        for (final Dictionary dictionary : dictMap.values()) {
            if (null == dictionary) continue;
            final ArrayList<SuggestedWordInfo> dictionarySuggestions =
                    dictionary.getSuggestionsWithSessionId(composer, prevWord, proximityInfo,
                            blockOffensiveWords, additionalFeaturesOptions, sessionId);
            if (null == dictionarySuggestions) continue;
            suggestionResults.addAll(dictionarySuggestions);
            if (null != rawSuggestions) {
                rawSuggestions.addAll(dictionarySuggestions);
            }
        }
        return suggestionResults;
    }

    public boolean isValidMainDictWord(final String word) {
        final Dictionaries dictionaries = mDictionaries;
        if (TextUtils.isEmpty(word) || !dictionaries.hasMainDict()) {
            return false;
        }
        return dictionaries.mMainDictionary.isValidWord(word);
    }

    public boolean isValidWord(final String word, final boolean ignoreCase) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        final Dictionaries dictionaries = mDictionaries;
        final String lowerCasedWord = word.toLowerCase(dictionaries.mLocale);
        final Map<String, Dictionary> dictMap = dictionaries.mDictMap;
        for (final Dictionary dictionary : dictMap.values()) {
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
        final Map<String, Dictionary> dictMap = mDictionaries.mDictMap;
        for (final Dictionary dictionary : dictMap.values()) {
            final int tempFreq = dictionary.getFrequency(word);
            if (tempFreq >= maxFreq) {
                maxFreq = tempFreq;
            }
        }
        return maxFreq;
    }


    public void clearUserHistoryDictionary() {
        final UserHistoryDictionary userHistoryDict = mDictionaries.mUserHistoryDictionary;
        if (userHistoryDict == null) {
            return;
        }
        userHistoryDict.clearAndFlushDictionary();
    }

    // This method gets called only when the IME receives a notification to remove the
    // personalization dictionary.
    public void clearPersonalizationDictionary() {
        final PersonalizationDictionary personalizationDict =
                mDictionaries.mPersonalizationDictionary;
        if (personalizationDict == null) {
            return;
        }
        personalizationDict.clearAndFlushDictionary();
    }

    public void addMultipleDictionaryEntriesToPersonalizationDictionary(
            final ArrayList<LanguageModelParam> languageModelParams,
            final ExpandableBinaryDictionary.AddMultipleDictionaryEntriesCallback callback) {
        final PersonalizationDictionary personalizationDict =
                mDictionaries.mPersonalizationDictionary;
        if (personalizationDict == null) {
            if (callback != null) {
                callback.onFinished();
            }
            return;
        }
        personalizationDict.addMultipleDictionaryEntriesToDictionary(languageModelParams, callback);
    }

    public void dumpDictionaryForDebug(final String dictName) {
        final ExpandableBinaryDictionary dictToDump;
        if (dictName.equals(Dictionary.TYPE_CONTACTS)) {
            dictToDump = mDictionaries.mContactsDictionary;
        } else if (dictName.equals(Dictionary.TYPE_USER)) {
            dictToDump = mDictionaries.mUserDictionary;
        } else if (dictName.equals(Dictionary.TYPE_USER_HISTORY)) {
            dictToDump = mDictionaries.mUserHistoryDictionary;
        } else if (dictName.equals(Dictionary.TYPE_PERSONALIZATION)) {
            dictToDump = mDictionaries.mPersonalizationDictionary;
        } else {
            dictToDump = null;
        }
        if (dictToDump == null) {
            Log.e(TAG, "Cannot dump " + dictName + ". "
                    + "The dictionary is not being used for suggestion or cannot be dumped.");
            return;
        }
        dictToDump.dumpAllWordsForDebug();
    }
}
