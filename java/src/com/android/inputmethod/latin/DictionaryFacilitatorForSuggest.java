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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// TODO: Consolidate dictionaries in native code.
public class DictionaryFacilitatorForSuggest {
    public static final String TAG = DictionaryFacilitatorForSuggest.class.getSimpleName();

    private final Context mContext;
    private final Locale mLocale;

    private final ConcurrentHashMap<String, Dictionary> mDictionaries =
            CollectionUtils.newConcurrentHashMap();
    private HashSet<String> mDictionarySubsetForDebug = null;

    private Dictionary mMainDictionary;
    private ContactsBinaryDictionary mContactsDictionary;
    private UserBinaryDictionary mUserDictionary;
    private UserHistoryDictionary mUserHistoryDictionary;
    private PersonalizationDictionary mPersonalizationDictionary;

    @UsedForTesting
    private boolean mIsCurrentlyWaitingForMainDictionary = false;

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
        initForDebug(settingsValues);
        reloadMainDict(context, locale, listener);
        setUserDictionary(new UserBinaryDictionary(context, locale));
        resetAdditionalDictionaries(oldDictionaryFacilitator, settingsValues);
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
    DictionaryFacilitatorForSuggest(final Context context, final AssetFileAddress[] dictionaryList,
            final Locale locale) {
        final Dictionary mainDict = DictionaryFactory.createDictionaryForTest(dictionaryList,
                false /* useFullEditDistance */, locale);
        mContext = context;
        mLocale = locale;
        setMainDictionary(mainDict);
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

    public void reloadMainDict(final Context context, final Locale locale,
            final DictionaryInitializationListener listener) {
        mIsCurrentlyWaitingForMainDictionary = true;
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
                mIsCurrentlyWaitingForMainDictionary = false;
            }
        }.start();
    }

    // The main dictionary could have been loaded asynchronously.  Don't cache the return value
    // of this method.
    public boolean hasMainDictionary() {
        return null != mMainDictionary && mMainDictionary.isInitialized();
    }

    @UsedForTesting
    public boolean isCurrentlyWaitingForMainDictionary() {
        return mIsCurrentlyWaitingForMainDictionary;
    }

    private void setMainDictionary(final Dictionary mainDictionary) {
        mMainDictionary = mainDictionary;
        addOrReplaceDictionary(Dictionary.TYPE_MAIN, mainDictionary);
    }

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set. This refers to the system-managed user dictionary.
     */
    @UsedForTesting
    public void setUserDictionary(final UserBinaryDictionary userDictionary) {
        mUserDictionary = userDictionary;
        addOrReplaceDictionary(Dictionary.TYPE_USER, userDictionary);
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded. It is also possible to remove
     * the contacts dictionary by passing null to this method. In this case no contacts dictionary
     * won't be used.
     */
    @UsedForTesting
    public void setContactsDictionary(final ContactsBinaryDictionary contactsDictionary) {
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

    public String addToUserHistory(final WordComposer wordComposer, final String previousWord,
            final String suggestion) {
        if (mUserHistoryDictionary == null) {
            return null;
        }
        final String secondWord;
        if (wordComposer.wasAutoCapitalized() && !wordComposer.isMostlyCaps()) {
            secondWord = suggestion.toLowerCase(mLocale);
        } else {
            secondWord = suggestion;
        }
        // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
        // We don't add words with 0-frequency (assuming they would be profanity etc.).
        final int maxFreq = getMaxFrequency(suggestion);
        if (maxFreq == 0) {
            return null;
        }
        final boolean isValid = maxFreq > 0;
        final int timeStamp = (int)TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis()));
        mUserHistoryDictionary.addToDictionary(previousWord, secondWord, isValid, timeStamp);
        return previousWord;
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
}
