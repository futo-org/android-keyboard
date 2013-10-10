/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.personalization.PersonalizationDictionary;
import com.android.inputmethod.latin.personalization.PersonalizationPredictionDictionary;
import com.android.inputmethod.latin.personalization.UserHistoryDictionary;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.AutoCorrectionUtils;
import com.android.inputmethod.latin.utils.BoundedTreeSet;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
public final class Suggest {
    public static final String TAG = Suggest.class.getSimpleName();

    // Session id for
    // {@link #getSuggestedWords(WordComposer,String,ProximityInfo,boolean,int)}.
    // We are sharing the same ID between typing and gesture to save RAM footprint.
    public static final int SESSION_TYPING = 0;
    public static final int SESSION_GESTURE = 0;

    // TODO: rename this to CORRECTION_OFF
    public static final int CORRECTION_NONE = 0;
    // TODO: rename this to CORRECTION_ON
    public static final int CORRECTION_FULL = 1;

    // Close to -2**31
    private static final int SUPPRESS_SUGGEST_THRESHOLD = -2000000000;

    public static final int MAX_SUGGESTIONS = 18;

    public interface SuggestInitializationListener {
        public void onUpdateMainDictionaryAvailability(boolean isMainDictionaryAvailable);
    }

    private static final boolean DBG = LatinImeLogger.sDBG;

    private final ConcurrentHashMap<String, Dictionary> mDictionaries =
            CollectionUtils.newConcurrentHashMap();
    private HashSet<String> mOnlyDictionarySetForDebug = null;
    private Dictionary mMainDictionary;
    private ContactsBinaryDictionary mContactsDict;
    @UsedForTesting
    private boolean mIsCurrentlyWaitingForMainDictionary = false;

    private float mAutoCorrectionThreshold;

    // Locale used for upper- and title-casing words
    public final Locale mLocale;

    public Suggest(final Context context, final Locale locale,
            final SuggestInitializationListener listener) {
        initAsynchronously(context, locale, listener);
        mLocale = locale;
        // initialize a debug flag for the personalization
        if (Settings.readUseOnlyPersonalizationDictionaryForDebug(
                PreferenceManager.getDefaultSharedPreferences(context))) {
            mOnlyDictionarySetForDebug = new HashSet<String>();
            mOnlyDictionarySetForDebug.add(Dictionary.TYPE_PERSONALIZATION);
            mOnlyDictionarySetForDebug.add(Dictionary.TYPE_PERSONALIZATION_PREDICTION_IN_JAVA);
        }
    }

    @UsedForTesting
    Suggest(final AssetFileAddress[] dictionaryList, final Locale locale) {
        final Dictionary mainDict = DictionaryFactory.createDictionaryForTest(dictionaryList,
                false /* useFullEditDistance */, locale);
        mLocale = locale;
        mMainDictionary = mainDict;
        addOrReplaceDictionaryInternal(Dictionary.TYPE_MAIN, mainDict);
    }

    private void initAsynchronously(final Context context, final Locale locale,
            final SuggestInitializationListener listener) {
        resetMainDict(context, locale, listener);
    }

    private void addOrReplaceDictionaryInternal(final String key, final Dictionary dict) {
        if (mOnlyDictionarySetForDebug != null && !mOnlyDictionarySetForDebug.contains(key)) {
            Log.w(TAG, "Ignore add " + key + " dictionary for debug.");
            return;
        }
        addOrReplaceDictionary(mDictionaries, key, dict);
    }

    private static void addOrReplaceDictionary(
            final ConcurrentHashMap<String, Dictionary> dictionaries,
            final String key, final Dictionary dict) {
        final Dictionary oldDict = (dict == null)
                ? dictionaries.remove(key)
                : dictionaries.put(key, dict);
        if (oldDict != null && dict != oldDict) {
            oldDict.close();
        }
    }

    public void resetMainDict(final Context context, final Locale locale,
            final SuggestInitializationListener listener) {
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
                addOrReplaceDictionaryInternal(Dictionary.TYPE_MAIN, newMainDict);
                mMainDictionary = newMainDict;
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

    public Dictionary getMainDictionary() {
        return mMainDictionary;
    }

    public ContactsBinaryDictionary getContactsDictionary() {
        return mContactsDict;
    }

    public ConcurrentHashMap<String, Dictionary> getUnigramDictionaries() {
        return mDictionaries;
    }

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set. This refers to the system-managed user dictionary.
     */
    public void setUserDictionary(final UserBinaryDictionary userDictionary) {
        addOrReplaceDictionaryInternal(Dictionary.TYPE_USER, userDictionary);
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded. It is also possible to remove
     * the contacts dictionary by passing null to this method. In this case no contacts dictionary
     * won't be used.
     */
    public void setContactsDictionary(final ContactsBinaryDictionary contactsDictionary) {
        mContactsDict = contactsDictionary;
        addOrReplaceDictionaryInternal(Dictionary.TYPE_CONTACTS, contactsDictionary);
    }

    public void setUserHistoryDictionary(final UserHistoryDictionary userHistoryDictionary) {
        addOrReplaceDictionaryInternal(Dictionary.TYPE_USER_HISTORY, userHistoryDictionary);
    }

    public void setPersonalizationPredictionDictionary(
            final PersonalizationPredictionDictionary personalizationPredictionDictionary) {
        addOrReplaceDictionaryInternal(Dictionary.TYPE_PERSONALIZATION_PREDICTION_IN_JAVA,
                personalizationPredictionDictionary);
    }

    public void setPersonalizationDictionary(
            final PersonalizationDictionary personalizationDictionary) {
        addOrReplaceDictionaryInternal(Dictionary.TYPE_PERSONALIZATION,
                personalizationDictionary);
    }

    public void setAutoCorrectionThreshold(float threshold) {
        mAutoCorrectionThreshold = threshold;
    }

    public interface OnGetSuggestedWordsCallback {
        public void onGetSuggestedWords(final SuggestedWords suggestedWords);
    }

    public void getSuggestedWords(final WordComposer wordComposer,
            final String prevWordForBigram, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final boolean isCorrectionEnabled,
            final int[] additionalFeaturesOptions, final int sessionId,
            final OnGetSuggestedWordsCallback callback) {
        LatinImeLogger.onStartSuggestion(prevWordForBigram);
        if (wordComposer.isBatchMode()) {
            getSuggestedWordsForBatchInput(wordComposer, prevWordForBigram, proximityInfo,
                    blockOffensiveWords, additionalFeaturesOptions, sessionId, callback);
        } else {
            getSuggestedWordsForTypingInput(wordComposer, prevWordForBigram, proximityInfo,
                    blockOffensiveWords, isCorrectionEnabled, additionalFeaturesOptions, callback);
        }
    }

    // Retrieves suggestions for the typing input
    // and calls the callback function with the suggestions.
    private void getSuggestedWordsForTypingInput(final WordComposer wordComposer,
            final String prevWordForBigram, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final boolean isCorrectionEnabled,
            final int[] additionalFeaturesOptions, final OnGetSuggestedWordsCallback callback) {
        final int trailingSingleQuotesCount = wordComposer.trailingSingleQuotesCount();
        final BoundedTreeSet suggestionsSet = new BoundedTreeSet(sSuggestedWordInfoComparator,
                MAX_SUGGESTIONS);

        final String typedWord = wordComposer.getTypedWord();
        final String consideredWord = trailingSingleQuotesCount > 0
                ? typedWord.substring(0, typedWord.length() - trailingSingleQuotesCount)
                : typedWord;
        LatinImeLogger.onAddSuggestedWord(typedWord, Dictionary.TYPE_USER_TYPED);

        final WordComposer wordComposerForLookup;
        if (trailingSingleQuotesCount > 0) {
            wordComposerForLookup = new WordComposer(wordComposer);
            for (int i = trailingSingleQuotesCount - 1; i >= 0; --i) {
                wordComposerForLookup.deleteLast();
            }
        } else {
            wordComposerForLookup = wordComposer;
        }

        for (final String key : mDictionaries.keySet()) {
            final Dictionary dictionary = mDictionaries.get(key);
            suggestionsSet.addAll(dictionary.getSuggestions(wordComposerForLookup,
                    prevWordForBigram, proximityInfo, blockOffensiveWords,
                    additionalFeaturesOptions));
        }

        final String whitelistedWord;
        if (suggestionsSet.isEmpty()) {
            whitelistedWord = null;
        } else if (SuggestedWordInfo.KIND_WHITELIST != suggestionsSet.first().mKind) {
            whitelistedWord = null;
        } else {
            whitelistedWord = suggestionsSet.first().mWord;
        }

        // The word can be auto-corrected if it has a whitelist entry that is not itself,
        // or if it's a 2+ characters non-word (i.e. it's not in the dictionary).
        final boolean allowsToBeAutoCorrected = (null != whitelistedWord
                && !whitelistedWord.equals(consideredWord))
                || (consideredWord.length() > 1 && !AutoCorrectionUtils.isValidWord(this,
                        consideredWord, wordComposer.isFirstCharCapitalized()));

        final boolean hasAutoCorrection;
        // TODO: using isCorrectionEnabled here is not very good. It's probably useless, because
        // any attempt to do auto-correction is already shielded with a test for this flag; at the
        // same time, it feels wrong that the SuggestedWord object includes information about
        // the current settings. It may also be useful to know, when the setting is off, whether
        // the word *would* have been auto-corrected.
        if (!isCorrectionEnabled || !allowsToBeAutoCorrected || !wordComposer.isComposingWord()
                || suggestionsSet.isEmpty() || wordComposer.hasDigits()
                || wordComposer.isMostlyCaps() || wordComposer.isResumed() || !hasMainDictionary()
                || SuggestedWordInfo.KIND_SHORTCUT == suggestionsSet.first().mKind) {
            // If we don't have a main dictionary, we never want to auto-correct. The reason for
            // this is, the user may have a contact whose name happens to match a valid word in
            // their language, and it will unexpectedly auto-correct. For example, if the user
            // types in English with no dictionary and has a "Will" in their contact list, "will"
            // would always auto-correct to "Will" which is unwanted. Hence, no main dict => no
            // auto-correct.
            // Also, shortcuts should never auto-correct unless they are whitelist entries.
            // TODO: we may want to have shortcut-only entries auto-correct in the future.
            hasAutoCorrection = false;
        } else {
            hasAutoCorrection = AutoCorrectionUtils.suggestionExceedsAutoCorrectionThreshold(
                    suggestionsSet.first(), consideredWord, mAutoCorrectionThreshold);
        }

        final ArrayList<SuggestedWordInfo> suggestionsContainer =
                CollectionUtils.newArrayList(suggestionsSet);
        final int suggestionsCount = suggestionsContainer.size();
        final boolean isFirstCharCapitalized = wordComposer.isFirstCharCapitalized();
        final boolean isAllUpperCase = wordComposer.isAllUpperCase();
        if (isFirstCharCapitalized || isAllUpperCase || 0 != trailingSingleQuotesCount) {
            for (int i = 0; i < suggestionsCount; ++i) {
                final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
                final SuggestedWordInfo transformedWordInfo = getTransformedSuggestedWordInfo(
                        wordInfo, mLocale, isAllUpperCase, isFirstCharCapitalized,
                        trailingSingleQuotesCount);
                suggestionsContainer.set(i, transformedWordInfo);
            }
        }

        for (int i = 0; i < suggestionsCount; ++i) {
            final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
            LatinImeLogger.onAddSuggestedWord(wordInfo.mWord.toString(),
                    wordInfo.mSourceDict.mDictType);
        }

        if (!TextUtils.isEmpty(typedWord)) {
            suggestionsContainer.add(0, new SuggestedWordInfo(typedWord,
                    SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED,
                    Dictionary.DICTIONARY_USER_TYPED,
                    SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                    SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
        }
        SuggestedWordInfo.removeDups(suggestionsContainer);

        final ArrayList<SuggestedWordInfo> suggestionsList;
        if (DBG && !suggestionsContainer.isEmpty()) {
            suggestionsList = getSuggestionsInfoListWithDebugInfo(typedWord, suggestionsContainer);
        } else {
            suggestionsList = suggestionsContainer;
        }

        callback.onGetSuggestedWords(new SuggestedWords(suggestionsList,
                // TODO: this first argument is lying. If this is a whitelisted word which is an
                // actual word, it says typedWordValid = false, which looks wrong. We should either
                // rename the attribute or change the value.
                !allowsToBeAutoCorrected /* typedWordValid */,
                hasAutoCorrection, /* willAutoCorrect */
                false /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */,
                !wordComposer.isComposingWord() /* isPrediction */));
    }

    // Retrieves suggestions for the batch input
    // and calls the callback function with the suggestions.
    private void getSuggestedWordsForBatchInput(final WordComposer wordComposer,
            final String prevWordForBigram, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions,
            final int sessionId, final OnGetSuggestedWordsCallback callback) {
        final BoundedTreeSet suggestionsSet = new BoundedTreeSet(sSuggestedWordInfoComparator,
                MAX_SUGGESTIONS);

        // At second character typed, search the unigrams (scores being affected by bigrams)
        for (final String key : mDictionaries.keySet()) {
            final Dictionary dictionary = mDictionaries.get(key);
            suggestionsSet.addAll(dictionary.getSuggestionsWithSessionId(wordComposer,
                    prevWordForBigram, proximityInfo, blockOffensiveWords,
                    additionalFeaturesOptions, sessionId));
        }

        for (SuggestedWordInfo wordInfo : suggestionsSet) {
            LatinImeLogger.onAddSuggestedWord(wordInfo.mWord, wordInfo.mSourceDict.mDictType);
        }

        final ArrayList<SuggestedWordInfo> suggestionsContainer =
                CollectionUtils.newArrayList(suggestionsSet);
        final int suggestionsCount = suggestionsContainer.size();
        final boolean isFirstCharCapitalized = wordComposer.wasShiftedNoLock();
        final boolean isAllUpperCase = wordComposer.isAllUpperCase();
        if (isFirstCharCapitalized || isAllUpperCase) {
            for (int i = 0; i < suggestionsCount; ++i) {
                final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
                final SuggestedWordInfo transformedWordInfo = getTransformedSuggestedWordInfo(
                        wordInfo, mLocale, isAllUpperCase, isFirstCharCapitalized,
                        0 /* trailingSingleQuotesCount */);
                suggestionsContainer.set(i, transformedWordInfo);
            }
        }

        if (suggestionsContainer.size() > 1 && TextUtils.equals(suggestionsContainer.get(0).mWord,
                wordComposer.getRejectedBatchModeSuggestion())) {
            final SuggestedWordInfo rejected = suggestionsContainer.remove(0);
            suggestionsContainer.add(1, rejected);
        }
        SuggestedWordInfo.removeDups(suggestionsContainer);

        // For some reason some suggestions with MIN_VALUE are making their way here.
        // TODO: Find a more robust way to detect distractors.
        for (int i = suggestionsContainer.size() - 1; i >= 0; --i) {
            if (suggestionsContainer.get(i).mScore < SUPPRESS_SUGGEST_THRESHOLD) {
                suggestionsContainer.remove(i);
            }
        }

        // In the batch input mode, the most relevant suggested word should act as a "typed word"
        // (typedWordValid=true), not as an "auto correct word" (willAutoCorrect=false).
        callback.onGetSuggestedWords(new SuggestedWords(suggestionsContainer,
                true /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */,
                false /* isPrediction */));
    }

    private static ArrayList<SuggestedWordInfo> getSuggestionsInfoListWithDebugInfo(
            final String typedWord, final ArrayList<SuggestedWordInfo> suggestions) {
        final SuggestedWordInfo typedWordInfo = suggestions.get(0);
        typedWordInfo.setDebugString("+");
        final int suggestionsSize = suggestions.size();
        final ArrayList<SuggestedWordInfo> suggestionsList =
                CollectionUtils.newArrayList(suggestionsSize);
        suggestionsList.add(typedWordInfo);
        // Note: i here is the index in mScores[], but the index in mSuggestions is one more
        // than i because we added the typed word to mSuggestions without touching mScores.
        for (int i = 0; i < suggestionsSize - 1; ++i) {
            final SuggestedWordInfo cur = suggestions.get(i + 1);
            final float normalizedScore = BinaryDictionary.calcNormalizedScore(
                    typedWord, cur.toString(), cur.mScore);
            final String scoreInfoString;
            if (normalizedScore > 0) {
                scoreInfoString = String.format(
                        Locale.ROOT, "%d (%4.2f)", cur.mScore, normalizedScore);
            } else {
                scoreInfoString = Integer.toString(cur.mScore);
            }
            cur.setDebugString(scoreInfoString);
            suggestionsList.add(cur);
        }
        return suggestionsList;
    }

    private static final class SuggestedWordInfoComparator
            implements Comparator<SuggestedWordInfo> {
        // This comparator ranks the word info with the higher frequency first. That's because
        // that's the order we want our elements in.
        @Override
        public int compare(final SuggestedWordInfo o1, final SuggestedWordInfo o2) {
            if (o1.mScore > o2.mScore) return -1;
            if (o1.mScore < o2.mScore) return 1;
            if (o1.mCodePointCount < o2.mCodePointCount) return -1;
            if (o1.mCodePointCount > o2.mCodePointCount) return 1;
            return o1.mWord.compareTo(o2.mWord);
        }
    }
    private static final SuggestedWordInfoComparator sSuggestedWordInfoComparator =
            new SuggestedWordInfoComparator();

    /* package for test */ static SuggestedWordInfo getTransformedSuggestedWordInfo(
            final SuggestedWordInfo wordInfo, final Locale locale, final boolean isAllUpperCase,
            final boolean isFirstCharCapitalized, final int trailingSingleQuotesCount) {
        final StringBuilder sb = new StringBuilder(wordInfo.mWord.length());
        if (isAllUpperCase) {
            sb.append(wordInfo.mWord.toUpperCase(locale));
        } else if (isFirstCharCapitalized) {
            sb.append(StringUtils.capitalizeFirstCodePoint(wordInfo.mWord, locale));
        } else {
            sb.append(wordInfo.mWord);
        }
        // Appending quotes is here to help people quote words. However, it's not helpful
        // when they type words with quotes toward the end like "it's" or "didn't", where
        // it's more likely the user missed the last character (or didn't type it yet).
        final int quotesToAppend = trailingSingleQuotesCount
                - (-1 == wordInfo.mWord.indexOf(Constants.CODE_SINGLE_QUOTE) ? 0 : 1);
        for (int i = quotesToAppend - 1; i >= 0; --i) {
            sb.appendCodePoint(Constants.CODE_SINGLE_QUOTE);
        }
        return new SuggestedWordInfo(sb.toString(), wordInfo.mScore, wordInfo.mKind,
                wordInfo.mSourceDict, wordInfo.mIndexOfTouchPointOfSecondWord,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */);
    }

    public void close() {
        final HashSet<Dictionary> dictionaries = CollectionUtils.newHashSet();
        dictionaries.addAll(mDictionaries.values());
        for (final Dictionary dictionary : dictionaries) {
            dictionary.close();
        }
        mMainDictionary = null;
    }
}
