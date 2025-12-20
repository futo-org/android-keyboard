/*
7 * Copyright (C) 2013 The Android Open Source Project
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

package org.futo.inputmethod.latin;

import android.Manifest;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;

import org.futo.inputmethod.annotations.UsedForTesting;
import org.futo.inputmethod.keyboard.Keyboard;
import org.futo.inputmethod.latin.NgramContext.WordInfo;
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.futo.inputmethod.latin.common.ComposedData;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.StringUtils;
import org.futo.inputmethod.latin.permissions.PermissionsUtil;
import org.futo.inputmethod.latin.personalization.UserHistoryDictionary;
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion;
import org.futo.inputmethod.latin.utils.ExecutorUtils;
import org.futo.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
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

    private List<DictionaryGroup> mDictionaryGroups = new ArrayList<>();
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionaries = new CountDownLatch(0);
    // To synchronize assigning mDictionaryGroup to ensure closing dictionaries.
    private final Object mLock = new Object();

    public static final Map<String, Class<? extends ExpandableBinaryDictionary>>
            DICT_TYPE_TO_CLASS = new HashMap<>();

    static {
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER_HISTORY, UserHistoryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER, UserBinaryDictionary.class);
    }

    private static final String DICT_FACTORY_METHOD_NAME = "getDictionary";
    private static final Class<?>[] DICT_FACTORY_METHOD_ARG_TYPES =
            new Class[] { Context.class, Locale.class, File.class, String.class, String.class };

    private LruCache<String, Boolean> mValidSpellingWordReadCache;
    private LruCache<String, Boolean> mValidSpellingWordWriteCache;

    @Override
    public void setValidSpellingWordReadCache(final LruCache<String, Boolean> cache) {
        mValidSpellingWordReadCache = cache;
    }

    @Override
    public void setValidSpellingWordWriteCache(final LruCache<String, Boolean> cache) {
        mValidSpellingWordWriteCache = cache;
    }

    @Override
    public boolean isForLocales(final List<Locale> locales) {
        if(locales.size() != mDictionaryGroups.size()) return false;
        for(int i=0; i<locales.size(); i++) {
            if(locales.get(i) != mDictionaryGroups.get(i).mLocale) return false;
        }

        return true;
    }

    //@Override
    public boolean isForLocale(final Locale locale) {
        for(int i=0; i<mDictionaryGroups.size(); i++) {
            if(locale == mDictionaryGroups.get(i).mLocale) return true;
        }

        return false;
    }

    /**
     * Returns whether this facilitator is exactly for this account.
     *
     * @param account the account to test against.
     */
    public boolean isForAccount(@Nullable final String account) {
        if(mDictionaryGroups.isEmpty()) return false;

        return TextUtils.equals(mDictionaryGroups.get(0).mAccount, account);
    }

    /**
     * A group of dictionaries that work together for a single language.
     */
    private static class DictionaryGroup {
        // TODO: Add null analysis annotations.
        // TODO: Run evaluation to determine a reasonable value for these constants. The current
        // values are ad-hoc and chosen without any particular care or methodology.
        public static final float WEIGHT_FOR_MOST_PROBABLE_LANGUAGE = 1.0f;
        public static final float WEIGHT_FOR_GESTURING_IN_NOT_MOST_PROBABLE_LANGUAGE = 0.0f;
        public static final float WEIGHT_FOR_TYPING_IN_NOT_MOST_PROBABLE_LANGUAGE = 0.8f;

        /**
         * The locale associated with the dictionary group.
         */
        @Nullable public final Locale mLocale;

        /**
         * The user account associated with the dictionary group.
         */
        @Nullable public final String mAccount;

        @Nullable private Dictionary mMainDict;
        private Dictionary mEmojiDict;
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

            mEmojiDict = new EmojiDictionary(locale);
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
            }else if (Dictionary.TYPE_EMOJI.equals(dictType)) {
                return mEmojiDict;
            }else {
                return getSubDict(dictType);
            }
        }

        public ExpandableBinaryDictionary getSubDict(final String dictType) {
            return mSubDictMap.get(dictType);
        }

        public boolean hasDict(final String dictType, @Nullable final String account) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict != null;
            } else if (Dictionary.TYPE_EMOJI.equals(dictType)) {
                return mEmojiDict != null;
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
            } else if (Dictionary.TYPE_EMOJI.equals(dictType)) {
                dict = mEmojiDict;
            } else {
                dict = mSubDictMap.remove(dictType);
            }
            if (dict != null) {
                dict.close();
            }
        }
    }

    public DictionaryFacilitatorImpl() {
    }

    @Override
    public void onStartInput() {
    }

    @Override
    public void onFinishInput(Context context) {
    }

    @Override
    public boolean isActive() {
        return !mDictionaryGroups.isEmpty() && mDictionaryGroups.get(0).mLocale != null;
    }

    @Override
    public List<Locale> getLocales() {
        final ArrayList<Locale> locales = new ArrayList<>();
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            locales.add(dictionaryGroup.mLocale);
        }
        return locales;
    }

    @Override
    public Locale getPrimaryLocale() {
        if(mDictionaryGroups.isEmpty()) return Locale.ROOT;
        return mDictionaryGroups.get(0).mLocale;
    }

    @Override
    public boolean usesContacts() {
        if(mDictionaryGroups.isEmpty()) return false;
        return mDictionaryGroups.get(0).getSubDict(Dictionary.TYPE_CONTACTS) != null;
    }

    @Override
    public String getAccount() {
        return null;
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

    @Nullable
    static DictionaryGroup findDictionaryGroupWithLocale(final List<DictionaryGroup> dictionaryGroups,
            final Locale locale) {
        for(DictionaryGroup dictionaryGroup : dictionaryGroups) {
            if(locale.equals(dictionaryGroup.mLocale)) return dictionaryGroup;
        }
        return null;
    }

    @Override
    public void resetDictionaries(
            final Context context,
            final List<Locale> newLocales,
            final boolean useContactsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadAllDictionaries,
            @Nullable final String account,
            final String dictNamePrefix,
            @Nullable final DictionaryInitializationListener listener) {
        final HashMap<Locale, ArrayList<String>> existingDictionariesToCleanup = new HashMap<>();
        // TODO: Make subDictTypesToUse configurable by resource or a static final list.
        final HashSet<String> subDictTypesToUse = new HashSet<>();
        subDictTypesToUse.add(Dictionary.TYPE_USER);

        // Do not use contacts dictionary if we do not have permissions to read contacts.
        final boolean contactsPermissionGranted = PermissionsUtil.checkAllPermissionsGranted(
                context, Manifest.permission.READ_CONTACTS);
        if (useContactsDict && contactsPermissionGranted) {
            subDictTypesToUse.add(Dictionary.TYPE_CONTACTS);
        }
        if (usePersonalizedDicts) {
            subDictTypesToUse.add(Dictionary.TYPE_USER_HISTORY);
        }

        // Gather all dictionaries. We'll remove them from the list to clean up later.
        for(DictionaryGroup group : mDictionaryGroups) {
            final ArrayList<String> dictTypeForLocale = new ArrayList<>();
            existingDictionariesToCleanup.put(group.mLocale, dictTypeForLocale);
            for (final String dictType : DYNAMIC_DICTIONARY_TYPES) {
                if (group.hasDict(dictType, account)) {
                    dictTypeForLocale.add(dictType);
                }
            }
            if (group.hasDict(Dictionary.TYPE_MAIN, account)) {
                dictTypeForLocale.add(Dictionary.TYPE_MAIN);
            }
        }

        ArrayList<DictionaryGroup> newDictionaryGroups = new ArrayList<>();
        for(Locale newLocale : newLocales) {
            final DictionaryGroup dictionaryGroupForLocale =
                    findDictionaryGroupWithLocale(mDictionaryGroups, newLocale);
            final ArrayList<String> dictTypesToCleanupForLocale =
                    existingDictionariesToCleanup.getOrDefault(newLocale, new ArrayList<>());
            final boolean noExistingDictsForThisLocale = (null == dictionaryGroupForLocale);

            final Dictionary mainDict;
            if (forceReloadAllDictionaries || noExistingDictsForThisLocale
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
                        || !dictionaryGroupForLocale.hasDict(subDictType, account)
                        || forceReloadAllDictionaries) {
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
            DictionaryGroup newDictionaryGroup =
                    new DictionaryGroup(newLocale, mainDict, account, subDicts);

            newDictionaryGroups.add(newDictionaryGroup);
        }

        if(forceReloadAllDictionaries) UserHistoryDictionary.forceUncleanClose = true;
        try {
            // Replace Dictionaries.
            final List<DictionaryGroup> oldDictionaryGroups;
            synchronized (mLock) {
                oldDictionaryGroups = mDictionaryGroups;
                mDictionaryGroups = newDictionaryGroups;

                for (DictionaryGroup dictionaryGroup : newDictionaryGroups) {
                    final Dictionary mainDict = dictionaryGroup.getDict(Dictionary.TYPE_MAIN);
                    if (mainDict == null || !mainDict.isInitialized()) {
                        asyncReloadUninitializedMainDictionaries(context, dictionaryGroup.mLocale, listener);
                    }
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
        } finally {
            UserHistoryDictionary.forceUncleanClose = false;
        }

        if (mValidSpellingWordWriteCache != null) {
            mValidSpellingWordWriteCache.evictAll();
        }
    }

    private void asyncReloadUninitializedMainDictionaries(final Context context,
            final Locale locale, final DictionaryInitializationListener listener) {
        final CountDownLatch latchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        mLatchForWaitingLoadingMainDictionaries = latchForWaitingLoadingMainDictionary;
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
            @Override
            public void run() {
                doReloadUninitializedMainDictionaries(
                        context, locale, listener, latchForWaitingLoadingMainDictionary);
            }
        });
    }

    void doReloadUninitializedMainDictionaries(final Context context, final Locale locale,
            final DictionaryInitializationListener listener,
            final CountDownLatch latchForWaitingLoadingMainDictionary) {
        final DictionaryGroup dictionaryGroup =
                findDictionaryGroupWithLocale(mDictionaryGroups, locale);
        if (null == dictionaryGroup) {
            // This should never happen, but better safe than crashy
            Log.w(TAG, "Expected a dictionary group for " + locale + " but none found");
            return;
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
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary());
        }
        latchForWaitingLoadingMainDictionary.countDown();
    }

    @UsedForTesting
    public void resetDictionariesForTesting(final Context context, final Locale locale,
            final ArrayList<String> dictionaryTypes, final HashMap<String, File> dictionaryFiles,
            final Map<String, Map<String, String>> additionalDictAttributes,
            @Nullable final String account) { // TODO Test multiple locales
        Dictionary mainDictionary = null;
        final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();

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
        mDictionaryGroups = new ArrayList<>();
        mDictionaryGroups.add(new DictionaryGroup(locale, mainDictionary, account, subDicts));
    }

    public void closeDictionaries() {
        final List<DictionaryGroup> dictionaryGroupsToClose;
        synchronized (mLock) {
            dictionaryGroupsToClose = mDictionaryGroups;
            mDictionaryGroups = new ArrayList<>();
        }

        for(DictionaryGroup dictionaryGroupToClose : dictionaryGroupsToClose) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                dictionaryGroupToClose.closeDict(dictType);
            }
        }
    }

    @UsedForTesting
    public ExpandableBinaryDictionary getSubDictForTesting(final String dictName) {
        if(mDictionaryGroups.isEmpty()) return null;
        return mDictionaryGroups.get(0).getSubDict(dictName);
    }

    // The main dictionaries are loaded asynchronously.  Don't cache the return value
    // of these methods.
    public boolean hasAtLeastOneInitializedMainDictionary() {
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final Dictionary mainDict = dictionaryGroup.getDict(Dictionary.TYPE_MAIN);
            if (mainDict != null && mainDict.isInitialized()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAtLeastOneUninitializedMainDictionary() {
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
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
        if(mDictionaryGroups.isEmpty()) return; // TODO
        for (final ExpandableBinaryDictionary dict : mDictionaryGroups.get(0).mSubDictMap.values()) {
            dict.waitAllTasksForTests();
        }
    }

    //public Locale getProbableLocaleFromNgramContext(final NgramContext ngramContext) {
    //    ngramContext.getPrevWordCount()
    //}

    @Override
    public void onWordCommitted(final String suggestion) {
        final String[] words = suggestion.split(Constants.WORD_SEPARATOR);

        for(String word : words) {
            final ArrayList<DictionaryGroup> dictionariesValidForWord = new ArrayList<>();
            for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
                if (dictionaryGroup.mLocale == null) {
                    continue;
                }
                for (final String dictType : ALL_DICTIONARY_TYPES) {
                    final Dictionary dictionary = dictionaryGroup.getDict(dictType);
                    if (null == dictionary) continue;

                    if (dictionary.isValidWord(word)) {
                        dictionariesValidForWord.add(dictionaryGroup);
                    }
                }
            }

            // Only change confidences if any dictionary contains this word. If none contain it,
            // keep the confidences the way they were before.
            boolean anyContain = false;
            for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
                if (dictionariesValidForWord.contains(dictionaryGroup)) {
                    anyContain = true;
                    break;
                }
            }
            if(anyContain) {
                for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
                    if (dictionariesValidForWord.contains(dictionaryGroup)) {
                        dictionaryGroup.mConfidence += 1;
                    } else {
                        dictionaryGroup.mConfidence = 0;
                    }
                }
            }
        }
    }

    @Override
    public Locale getMostConfidentLocale() {
        DictionaryGroup maxDictionary = null;
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            if(dictionaryGroup.mLocale == null) continue;
            if(maxDictionary == null || dictionaryGroup.mConfidence > maxDictionary.mConfidence) {
                maxDictionary = dictionaryGroup;
            }
        }

        if(maxDictionary == null) {
            return Locale.ROOT;
        } else {
            return maxDictionary.mLocale;
        }
    }

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final boolean blockPotentiallyOffensive) {
        // Update the spelling cache before learning. Words that are not yet added to user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("addToUserHistory", suggestion);

        final String[] words = suggestion.split(Constants.WORD_SEPARATOR);
        NgramContext ngramContextForCurrentWord = ngramContext;

        DictionaryGroup mostConfidentDictionary = findDictionaryGroupWithLocale(mDictionaryGroups,
                getMostConfidentLocale());
        if(mostConfidentDictionary == null) return;
        if(mostConfidentDictionary.mConfidence == 0) return;
        for (int i = 0; i < words.length; i++) {
            final String currentWord = words[i];
            final boolean wasCurrentWordAutoCapitalized = i == 0 && wasAutoCapitalized;
            addWordToUserHistory(mostConfidentDictionary, ngramContextForCurrentWord, currentWord,
                    wasCurrentWordAutoCapitalized, (int) timeStampInSeconds,
                    blockPotentiallyOffensive);
            ngramContextForCurrentWord =
                    ngramContextForCurrentWord.getNextNgramContext(new WordInfo(currentWord));
        }
    }

    private void putWordIntoValidSpellingWordCache(
            @Nonnull final String caller,
            @Nonnull final String originalWord) {
        if (mValidSpellingWordWriteCache == null) {
            return;
        }

        final String lowerCaseWord = originalWord.toLowerCase(getPrimaryLocale());
        final boolean lowerCaseValid = isValidSpellingWord(lowerCaseWord);
        mValidSpellingWordWriteCache.put(lowerCaseWord, lowerCaseValid);

        final String capitalWord =
                StringUtils.capitalizeFirstAndDowncaseRest(originalWord, getPrimaryLocale());
        final boolean capitalValid;
        if (lowerCaseValid) {
            // The lower case form of the word is valid, so the upper case must be valid.
            capitalValid = true;
        } else {
            capitalValid = isValidSpellingWord(capitalWord);
        }
        mValidSpellingWordWriteCache.put(capitalWord, capitalValid);
    }

    private void addWordToUserHistory(final DictionaryGroup dictionaryGroup,
            final NgramContext ngramContext, final String word, final boolean wasAutoCapitalized,
            final int timeStampInSeconds, final boolean blockPotentiallyOffensive) {
        final ExpandableBinaryDictionary userHistoryDictionary =
                dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userHistoryDictionary == null || !isForLocale(userHistoryDictionary.mLocale)) {
            return;
        }
        final int maxFreq = getFrequency(word);
        if (maxFreq == 0 && blockPotentiallyOffensive) {
            return;
        }
        final String lowerCasedWord = word.toLowerCase(dictionaryGroup.mLocale);
        final String secondWord;
        if (wasAutoCapitalized) {
            if (isValidSuggestionWord(word) && !isValidSuggestionWord(lowerCasedWord)) {
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
                isValid, timeStampInSeconds);
    }

    private void removeWord(final String dictName, final String word) {
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final ExpandableBinaryDictionary dictionary = dictionaryGroup.getSubDict(dictName);
            if (dictionary != null) {
                dictionary.removeUnigramEntryDynamically(word);
            }
        }
    }

    @Override
    public void unlearnFromUserHistory(final String word,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final int eventType) {
        // TODO: Decide whether or not to remove the word on EVENT_BACKSPACE.
        if (eventType != Constants.EVENT_BACKSPACE) {
            removeWord(Dictionary.TYPE_USER_HISTORY, word);
        }

        // Update the spelling cache after unlearning. Words that are removed from user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("unlearnFromUserHistory", word.toLowerCase());
    }

    @NonNull
    @Override
    public ArrayList<Integer> getValidNextCodePoints(ComposedData composedData) {
        final ArrayList<Integer> codePoints = new ArrayList<>();
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                final Dictionary dictionary = dictionaryGroup.getDict(dictType);
                if (null == dictionary) continue;
                final ArrayList<Integer> codes =
                        dictionary.getNextValidCodePoints(composedData);
                if (null == codes) continue;
                codePoints.addAll(codes);
            }
        }
        return codePoints;
    }

    private void updateDictionaryGroupWeights() {
        int maxConfidence = 0;
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            if(dictionaryGroup.mConfidence > maxConfidence) maxConfidence = dictionaryGroup.mConfidence;
        }

        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            if(dictionaryGroup.mConfidence >= maxConfidence) {
                dictionaryGroup.mWeightForTypingInLocale = DictionaryGroup.WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
                dictionaryGroup.mWeightForGesturingInLocale = DictionaryGroup.WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
            } else {
                dictionaryGroup.mWeightForTypingInLocale = DictionaryGroup.WEIGHT_FOR_TYPING_IN_NOT_MOST_PROBABLE_LANGUAGE;
                dictionaryGroup.mWeightForGesturingInLocale = DictionaryGroup.WEIGHT_FOR_GESTURING_IN_NOT_MOST_PROBABLE_LANGUAGE;
            }
        }
    }

    // TODO: Revise the way to fusion suggestion results.
    @Override
    @Nonnull public SuggestionResults getSuggestionResults(ComposedData composedData,
            NgramContext ngramContext, @Nonnull final Keyboard keyboard,
            SettingsValuesForSuggestion settingsValuesForSuggestion, int sessionId,
            int inputStyle) {
        long proximityInfoHandle = keyboard.getProximityInfo().getNativeProximityInfo();
        final SuggestionResults suggestionResults = new SuggestionResults(
                SuggestedWords.MAX_SUGGESTIONS, ngramContext.isBeginningOfSentenceContext(),
                false /* firstSuggestionExceedsConfidenceThreshold */);
        final float[] weightOfLangModelVsSpatialModel =
                new float[] { Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL };

        updateDictionaryGroupWeights();

        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                final Dictionary dictionary = dictionaryGroup.getDict(dictType);
                if (null == dictionary) continue;
                final float weightForLocale = composedData.mIsBatchMode
                        ? dictionaryGroup.mWeightForGesturingInLocale
                        : dictionaryGroup.mWeightForTypingInLocale;
                final ArrayList<SuggestedWordInfo> dictionarySuggestions =
                        dictionary.getSuggestions(composedData, ngramContext,
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

    public boolean isValidSpellingWord(final String word) {
        if (mValidSpellingWordReadCache != null) {
            final Boolean cachedValue = mValidSpellingWordReadCache.get(word);
            if (cachedValue != null) {
                return cachedValue;
            }
        }

        return isValidWord(word, ALL_DICTIONARY_TYPES);
    }

    public boolean isValidSuggestionWord(final String word) {
        return isValidWord(word, ALL_DICTIONARY_TYPES);
    }

    private boolean isValidWord(final String word, final String[] dictionariesToCheck) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }

        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            if (dictionaryGroup.mLocale == null) {
                continue;
            }
            for (final String dictType : dictionariesToCheck) {
                final Dictionary dictionary = dictionaryGroup.getDict(dictType);
                // Ideally the passed map would come out of a {@link java.util.concurrent.Future} and
                // would be immutable once it's finished initializing, but concretely a null test is
                // probably good enough for the time being.
                if (null == dictionary) continue;
                if (dictionary.isValidWord(word)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getFrequency(final String word) {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY;
        }
        int maxFreq = Dictionary.NOT_A_PROBABILITY;
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                final Dictionary dictionary = dictionaryGroup.getDict(dictType);
                if (dictionary == null) continue;
                final int tempFreq = dictionary.getFrequency(word);
                if (tempFreq >= maxFreq) {
                    maxFreq = tempFreq;
                }
            }
        }
        return maxFreq;
    }

    private boolean clearSubDictionary(final String dictName) {
        boolean anyCleared = false;
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final ExpandableBinaryDictionary dictionary = dictionaryGroup.getSubDict(dictName);
            if (dictionary == null) {
                continue;
            }

            dictionary.clear();
            anyCleared = true;
        }
        return anyCleared;
    }

    @Override
    public boolean clearUserHistoryDictionary(final Context context) {
        return clearSubDictionary(Dictionary.TYPE_USER_HISTORY);
    }

    @Override
    public void dumpDictionaryForDebug(final String dictName) {
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final ExpandableBinaryDictionary dictToDump = dictionaryGroup.getSubDict(dictName);
            if (dictToDump == null) {
                Log.e(TAG, "Cannot dump " + dictName + ". "
                        + "The dictionary is not being used for suggestion or cannot be dumped.");
                continue;
            }
            dictToDump.dumpAllWordsForDebug();
        }
    }

    @Override
    @Nonnull public List<DictionaryStats> getDictionaryStats(final Context context) {
        final ArrayList<DictionaryStats> statsOfEnabledSubDicts = new ArrayList<>();
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            for (final String dictType : DYNAMIC_DICTIONARY_TYPES) {
                final ExpandableBinaryDictionary dictionary = dictionaryGroup.getSubDict(dictType);
                if (dictionary == null) continue;
                statsOfEnabledSubDicts.add(dictionary.getDictionaryStats());
            }
        }
        return statsOfEnabledSubDicts;
    }

    @Override
    public void flushUserHistoryDictionaries() {
        for(DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final ExpandableBinaryDictionary dictionary =
                    dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
            if(dictionary == null) continue;
            dictionary.asyncFlushBinaryDictionary();
        }
    }

    @Override
    public String dump(final Context context) {
        return "";
    }
}
