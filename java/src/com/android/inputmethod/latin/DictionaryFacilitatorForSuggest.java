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
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.LanguageModelParam;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
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

    private final Context mContext;
    public final Locale mLocale;

    private final ConcurrentHashMap<String, Dictionary> mDictionaries =
            CollectionUtils.newConcurrentHashMap();
    private HashSet<String> mDictionarySubsetForDebug = null;

    private Dictionary mMainDictionary;
    private ContactsBinaryDictionary mContactsDictionary;
    private UserBinaryDictionary mUserDictionary;
    private UserHistoryDictionary mUserHistoryDictionary;
    private PersonalizationDictionary mPersonalizationDictionary;

    private final CountDownLatch mLatchForWaitingLoadingMainDictionary;

    public interface DictionaryInitializationListener {
        public void onUpdateMainDictionaryAvailability(boolean isMainDictionaryAvailable);
    }

    /**
     * Creates instance for initialization or when the locale is changed.
     *
     * @param context the context
     * @param locale the locale
     * @param settingsValues current settings values to control what dictionaries should be used
     * @param listener the listener
     * @param oldDictionaryFacilitator the instance having old dictionaries. This is null when the
     * instance is initially created.
     */
    public DictionaryFacilitatorForSuggest(final Context context, final Locale locale,
            final SettingsValues settingsValues, final DictionaryInitializationListener listener,
            final DictionaryFacilitatorForSuggest oldDictionaryFacilitator) {
        mContext = context;
        mLocale = locale;
        mLatchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        initForDebug(settingsValues);
        loadMainDict(context, locale, listener);
        setUserDictionary(new UserBinaryDictionary(context, locale));
        resetAdditionalDictionaries(oldDictionaryFacilitator, settingsValues);
    }

    /**
     * Creates instance for reloading the main dict.
     *
     * @param listener the listener
     * @param oldDictionaryFacilitator the instance having old dictionaries. This must not be null.
     */
    public DictionaryFacilitatorForSuggest(final DictionaryInitializationListener listener,
            final DictionaryFacilitatorForSuggest oldDictionaryFacilitator) {
        mContext = oldDictionaryFacilitator.mContext;
        mLocale = oldDictionaryFacilitator.mLocale;
        mDictionarySubsetForDebug = oldDictionaryFacilitator.mDictionarySubsetForDebug;
        mLatchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        loadMainDict(mContext, mLocale, listener);
        // Transfer user dictionary.
        setUserDictionary(oldDictionaryFacilitator.mUserDictionary);
        oldDictionaryFacilitator.removeDictionary(Dictionary.TYPE_USER);
        // Transfer contacts dictionary.
        setContactsDictionary(oldDictionaryFacilitator.mContactsDictionary);
        oldDictionaryFacilitator.removeDictionary(Dictionary.TYPE_CONTACTS);
        // Transfer user history dictionary.
        setUserHistoryDictionary(oldDictionaryFacilitator.mUserHistoryDictionary);
        oldDictionaryFacilitator.removeDictionary(Dictionary.TYPE_USER_HISTORY);
        // Transfer personalization dictionary.
        setPersonalizationDictionary(oldDictionaryFacilitator.mPersonalizationDictionary);
        oldDictionaryFacilitator.removeDictionary(Dictionary.TYPE_PERSONALIZATION);
    }

    /**
     * Creates instance for when the settings values have been changed.
     *
     * @param settingsValues the new settings values
     * @param oldDictionaryFacilitator the instance having old dictionaries. This must not be null.
     */
    //
    public DictionaryFacilitatorForSuggest(final SettingsValues settingsValues,
            final DictionaryFacilitatorForSuggest oldDictionaryFacilitator) {
        mContext = oldDictionaryFacilitator.mContext;
        mLocale = oldDictionaryFacilitator.mLocale;
        mLatchForWaitingLoadingMainDictionary = new CountDownLatch(0);
        initForDebug(settingsValues);
        // Transfer main dictionary.
        setMainDictionary(oldDictionaryFacilitator.mMainDictionary);
        oldDictionaryFacilitator.removeDictionary(Dictionary.TYPE_MAIN);
        // Transfer user dictionary.
        setUserDictionary(oldDictionaryFacilitator.mUserDictionary);
        oldDictionaryFacilitator.removeDictionary(Dictionary.TYPE_USER);
        // Transfer or create additional dictionaries depending on the settings values.
        resetAdditionalDictionaries(oldDictionaryFacilitator, settingsValues);
    }

    @UsedForTesting
    public DictionaryFacilitatorForSuggest(final Context context, final Locale locale,
            final ArrayList<String> dictionaryTypes, final HashMap<String, File> dictionaryFiles) {
        mContext = context;
        mLocale = locale;
        mLatchForWaitingLoadingMainDictionary = new CountDownLatch(0);
        for (final String dictType : dictionaryTypes) {
            if (dictType.equals(Dictionary.TYPE_MAIN)) {
                final DictionaryCollection mainDictionary =
                        DictionaryFactory.createMainDictionaryFromManager(context, locale);
                setMainDictionary(mainDictionary);
            } else if (dictType.equals(Dictionary.TYPE_USER_HISTORY)) {
                final UserHistoryDictionary userHistoryDictionary =
                        PersonalizationHelper.getUserHistoryDictionary(context, locale);
                // Staring with an empty user history dictionary for testing.
                // Testing program may populate this dictionary before actual testing.
                userHistoryDictionary.reloadDictionaryIfRequired();
                userHistoryDictionary.waitAllTasksForTests();
                setUserHistoryDictionary(userHistoryDictionary);
            } else if (dictType.equals(Dictionary.TYPE_PERSONALIZATION)) {
                final PersonalizationDictionary personalizationDictionary =
                        PersonalizationHelper.getPersonalizationDictionary(context, locale);
                // Staring with an empty personalization dictionary for testing.
                // Testing program may populate this dictionary before actual testing.
                personalizationDictionary.reloadDictionaryIfRequired();
                personalizationDictionary.waitAllTasksForTests();
                setPersonalizationDictionary(personalizationDictionary);
            } else if (dictType.equals(Dictionary.TYPE_USER)) {
                final File file = dictionaryFiles.get(dictType);
                final UserBinaryDictionary userDictionary = new UserBinaryDictionary(
                        context, locale, file);
                userDictionary.reloadDictionaryIfRequired();
                userDictionary.waitAllTasksForTests();
                setUserDictionary(userDictionary);
            } else if (dictType.equals(Dictionary.TYPE_CONTACTS)) {
                final File file = dictionaryFiles.get(dictType);
                final ContactsBinaryDictionary contactsDictionary = new ContactsBinaryDictionary(
                        context, locale, file);
                contactsDictionary.reloadDictionaryIfRequired();
                contactsDictionary.waitAllTasksForTests();
                setContactsDictionary(contactsDictionary);
            } else {
                throw new RuntimeException("Unknown dictionary type: " + dictType);
            }
        }
    }

    // initialize a debug flag for the personalization
    private void initForDebug(final SettingsValues settingsValues) {
        if (settingsValues.mUseOnlyPersonalizationDictionaryForDebug) {
            mDictionarySubsetForDebug = new HashSet<String>();
            mDictionarySubsetForDebug.add(Dictionary.TYPE_PERSONALIZATION);
        }
    }

    public void close() {
        final HashSet<Dictionary> dictionaries = CollectionUtils.newHashSet();
        dictionaries.addAll(mDictionaries.values());
        for (final Dictionary dictionary : dictionaries) {
            dictionary.close();
        }
    }

    private void loadMainDict(final Context context, final Locale locale,
            final DictionaryInitializationListener listener) {
        mMainDictionary = null;
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasMainDictionary());
        }
        new Thread("InitializeBinaryDictionary") {
            @Override
            public void run() {
                final DictionaryCollection newMainDict =
                        DictionaryFactory.createMainDictionaryFromManager(context, locale);
                setMainDictionary(newMainDict);
                if (listener != null) {
                    listener.onUpdateMainDictionaryAvailability(hasMainDictionary());
                }
                mLatchForWaitingLoadingMainDictionary.countDown();
            }
        }.start();
    }

    // The main dictionary could have been loaded asynchronously.  Don't cache the return value
    // of this method.
    public boolean hasMainDictionary() {
        return null != mMainDictionary && mMainDictionary.isInitialized();
    }

    public boolean hasPersonalizationDictionary() {
        return null != mPersonalizationDictionary;
    }

    public void waitForLoadingMainDictionary(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        mLatchForWaitingLoadingMainDictionary.await(timeout, unit);
    }

    private void setMainDictionary(final Dictionary mainDictionary) {
        mMainDictionary = mainDictionary;
        addOrReplaceDictionary(Dictionary.TYPE_MAIN, mainDictionary);
    }

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set. This refers to the system-managed user dictionary.
     */
    private void setUserDictionary(final UserBinaryDictionary userDictionary) {
        mUserDictionary = userDictionary;
        addOrReplaceDictionary(Dictionary.TYPE_USER, userDictionary);
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded. It is also possible to remove
     * the contacts dictionary by passing null to this method. In this case no contacts dictionary
     * won't be used.
     */
    private void setContactsDictionary(final ContactsBinaryDictionary contactsDictionary) {
        mContactsDictionary = contactsDictionary;
        addOrReplaceDictionary(Dictionary.TYPE_CONTACTS, contactsDictionary);
    }

    private void setUserHistoryDictionary(final UserHistoryDictionary userHistoryDictionary) {
        mUserHistoryDictionary = userHistoryDictionary;
        addOrReplaceDictionary(Dictionary.TYPE_USER_HISTORY, userHistoryDictionary);
    }

    private void setPersonalizationDictionary(
            final PersonalizationDictionary personalizationDictionary) {
        mPersonalizationDictionary = personalizationDictionary;
        addOrReplaceDictionary(Dictionary.TYPE_PERSONALIZATION, personalizationDictionary);
    }

    /**
     * Reset dictionaries that can be turned off according to the user settings.
     *
     * @param oldDictionaryFacilitator the instance having old dictionaries
     * @param settingsValues current SettingsValues
     */
    private void resetAdditionalDictionaries(
            final DictionaryFacilitatorForSuggest oldDictionaryFacilitator,
            final SettingsValues settingsValues) {
        // Contacts dictionary
        resetContactsDictionary(null != oldDictionaryFacilitator ?
                oldDictionaryFacilitator.mContactsDictionary : null, settingsValues);
        // User history dictionary & Personalization dictionary
        resetPersonalizedDictionaries(oldDictionaryFacilitator, settingsValues);
    }

    /**
     * Set the user history dictionary and personalization dictionary according to the user
     * settings.
     *
     * @param oldDictionaryFacilitator the instance that has been used
     * @param settingsValues current settingsValues
     */
    // TODO: Consolidate resetPersonalizedDictionaries() and resetContactsDictionary(). Call up the
    // new method for each dictionary.
    private void resetPersonalizedDictionaries(
            final DictionaryFacilitatorForSuggest oldDictionaryFacilitator,
            final SettingsValues settingsValues) {
        final boolean shouldSetDictionaries = settingsValues.mUsePersonalizedDicts;

        final UserHistoryDictionary oldUserHistoryDictionary = (null == oldDictionaryFacilitator) ?
                null : oldDictionaryFacilitator.mUserHistoryDictionary;
        final PersonalizationDictionary oldPersonalizationDictionary =
                (null == oldDictionaryFacilitator) ? null :
                        oldDictionaryFacilitator.mPersonalizationDictionary;
        final UserHistoryDictionary userHistoryDictionaryToUse;
        final PersonalizationDictionary personalizationDictionaryToUse;
        if (!shouldSetDictionaries) {
            userHistoryDictionaryToUse = null;
            personalizationDictionaryToUse = null;
        } else {
            if (null != oldUserHistoryDictionary
                    && oldUserHistoryDictionary.mLocale.equals(mLocale)) {
                userHistoryDictionaryToUse = oldUserHistoryDictionary;
            } else {
                userHistoryDictionaryToUse =
                        PersonalizationHelper.getUserHistoryDictionary(mContext, mLocale);
            }
            if (null != oldPersonalizationDictionary
                    && oldPersonalizationDictionary.mLocale.equals(mLocale)) {
                personalizationDictionaryToUse = oldPersonalizationDictionary;
            } else {
                personalizationDictionaryToUse =
                        PersonalizationHelper.getPersonalizationDictionary(mContext, mLocale);
            }
        }
        setUserHistoryDictionary(userHistoryDictionaryToUse);
        setPersonalizationDictionary(personalizationDictionaryToUse);
    }

    /**
     * Set the contacts dictionary according to the user settings.
     *
     * This method takes an optional contacts dictionary to use when the locale hasn't changed
     * since the contacts dictionary can be opened or closed as necessary depending on the settings.
     *
     * @param oldContactsDictionary an optional dictionary to use, or null
     * @param settingsValues current settingsValues
     */
    private void resetContactsDictionary(final ContactsBinaryDictionary oldContactsDictionary,
            final SettingsValues settingsValues) {
        final boolean shouldSetDictionary = settingsValues.mUseContactsDict;
        final ContactsBinaryDictionary dictionaryToUse;
        if (!shouldSetDictionary) {
            // Make sure the dictionary is closed. If it is already closed, this is a no-op,
            // so it's safe to call it anyways.
            if (null != oldContactsDictionary) oldContactsDictionary.close();
            dictionaryToUse = null;
        } else {
            if (null != oldContactsDictionary) {
                if (!oldContactsDictionary.mLocale.equals(mLocale)) {
                    // If the locale has changed then recreate the contacts dictionary. This
                    // allows locale dependent rules for handling bigram name predictions.
                    oldContactsDictionary.close();
                    dictionaryToUse = new ContactsBinaryDictionary(mContext, mLocale);
                } else {
                    // Make sure the old contacts dictionary is opened. If it is already open,
                    // this is a no-op, so it's safe to call it anyways.
                    oldContactsDictionary.reopen(mContext);
                    dictionaryToUse = oldContactsDictionary;
                }
            } else {
                dictionaryToUse = new ContactsBinaryDictionary(mContext, mLocale);
            }
        }
        setContactsDictionary(dictionaryToUse);
    }

    public boolean isUserDictionaryEnabled() {
        if (mUserDictionary == null) {
            return false;
        }
        return mUserDictionary.mEnabled;
    }

    public void addWordToUserDictionary(String word) {
        if (mUserDictionary == null) {
            return;
        }
        mUserDictionary.addWordToUserDictionary(word);
    }

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            final String previousWord, final int timeStampInSeconds) {
        if (mUserHistoryDictionary == null) {
            return;
        }
        final int maxFreq = getMaxFrequency(suggestion);
        if (maxFreq == 0) {
            return;
        }
        final String suggestionLowerCase = suggestion.toLowerCase(mLocale);
        final String secondWord;
        if (wasAutoCapitalized) {
            secondWord = suggestionLowerCase;
        } else {
            // HACK: We'd like to avoid adding the capitalized form of common words to the User
            // History dictionary in order to avoid suggesting them until the dictionary
            // consolidation is done.
            // TODO: Remove this hack when ready.
            final int lowerCasefreqInMainDict = mMainDictionary != null ?
                    mMainDictionary.getFrequency(suggestionLowerCase) :
                            Dictionary.NOT_A_PROBABILITY;
            if (maxFreq < lowerCasefreqInMainDict
                    && lowerCasefreqInMainDict >= CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT) {
                // Use lower cased word as the word can be a distracter of the popular word.
                secondWord = suggestionLowerCase;
            } else {
                secondWord = suggestion;
            }
        }
        // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
        // We don't add words with 0-frequency (assuming they would be profanity etc.).
        final boolean isValid = maxFreq > 0;
        mUserHistoryDictionary.addToDictionary(
                previousWord, secondWord, isValid, timeStampInSeconds);
    }

    public void cancelAddingUserHistory(final String previousWord, final String committedWord) {
        if (mUserHistoryDictionary != null) {
            mUserHistoryDictionary.cancelAddingUserHistory(previousWord, committedWord);
        }
    }

    // TODO: Revise the way to fusion suggestion results.
    public void getSuggestions(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions,
            final int sessionId, final Set<SuggestedWordInfo> suggestionSet) {
        for (final String key : mDictionaries.keySet()) {
            final Dictionary dictionary = mDictionaries.get(key);
            if (null == dictionary) continue;
            suggestionSet.addAll(dictionary.getSuggestionsWithSessionId(composer, prevWord,
                    proximityInfo, blockOffensiveWords, additionalFeaturesOptions, sessionId));
        }
    }

    public boolean isValidMainDictWord(final String word) {
        if (TextUtils.isEmpty(word) || !hasMainDictionary()) {
            return false;
        }
        return mMainDictionary.isValidWord(word);
    }

    public boolean isValidWord(final String word, final boolean ignoreCase) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        final String lowerCasedWord = word.toLowerCase(mLocale);
        for (final String key : mDictionaries.keySet()) {
            final Dictionary dictionary = mDictionaries.get(key);
            // It's unclear how realistically 'dictionary' can be null, but the monkey is somehow
            // managing to get null in here. Presumably the language is changing to a language with
            // no main dictionary and the monkey manages to type a whole word before the thread
            // that reads the dictionary is started or something?
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
        for (final String key : mDictionaries.keySet()) {
            final Dictionary dictionary = mDictionaries.get(key);
            if (null == dictionary) continue;
            final int tempFreq = dictionary.getFrequency(word);
            if (tempFreq >= maxFreq) {
                maxFreq = tempFreq;
            }
        }
        return maxFreq;
    }

    private void removeDictionary(final String key) {
        mDictionaries.remove(key);
    }

    private void addOrReplaceDictionary(final String key, final Dictionary dict) {
        if (mDictionarySubsetForDebug != null && !mDictionarySubsetForDebug.contains(key)) {
            Log.w(TAG, "Ignore add " + key + " dictionary for debug.");
            return;
        }
        final Dictionary oldDict;
        if (dict == null) {
            oldDict = mDictionaries.remove(key);
        } else {
            oldDict = mDictionaries.put(key, dict);
        }
        if (oldDict != null && dict != oldDict) {
            oldDict.close();
        }
    }

    @UsedForTesting
    public void clearUserHistoryDictionary() {
        if (mUserHistoryDictionary == null) {
            return;
        }
        mUserHistoryDictionary.clearAndFlushDictionary();
    }

    // This method gets called only when the IME receives a notification to remove the
    // personalization dictionary.
    public void clearPersonalizationDictionary() {
        if (!hasPersonalizationDictionary()) {
            return;
        }
        mPersonalizationDictionary.clearAndFlushDictionary();
    }

    public void addMultipleDictionaryEntriesToPersonalizationDictionary(
            final ArrayList<LanguageModelParam> languageModelParams,
            final ExpandableBinaryDictionary.AddMultipleDictionaryEntriesCallback callback) {
        if (!hasPersonalizationDictionary()) {
            if (callback != null) {
                callback.onFinished();
            }
            return;
        }
        mPersonalizationDictionary.addMultipleDictionaryEntriesToDictionary(languageModelParams,
                callback);
    }
}
