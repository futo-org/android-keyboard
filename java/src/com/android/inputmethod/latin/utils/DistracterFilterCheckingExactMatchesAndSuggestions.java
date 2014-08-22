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

package com.android.inputmethod.latin.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.content.res.Resources;
import android.text.InputType;
import android.util.Log;
import android.util.LruCache;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.PrevWordsInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;

/**
 * This class is used to prevent distracters being added to personalization
 * or user history dictionaries
 */
public class DistracterFilterCheckingExactMatchesAndSuggestions implements DistracterFilter {
    private static final String TAG =
            DistracterFilterCheckingExactMatchesAndSuggestions.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final long TIMEOUT_TO_WAIT_LOADING_DICTIONARIES_IN_SECONDS = 120;
    private static final int MAX_DISTRACTERS_CACHE_SIZE = 512;

    private final Context mContext;
    private final Map<Locale, InputMethodSubtype> mLocaleToSubtypeMap;
    private final Map<Locale, Keyboard> mLocaleToKeyboardMap;
    private final DictionaryFacilitator mDictionaryFacilitator;
    private final LruCache<String, Boolean> mDistractersCache;
    private Keyboard mKeyboard;
    private final Object mLock = new Object();

    // If the score of the top suggestion exceeds this value, the tested word (e.g.,
    // an OOV, a misspelling, or an in-vocabulary word) would be considered as a distractor to
    // words in dictionary. The greater the threshold is, the less likely the tested word would
    // become a distractor, which means the tested word will be more likely to be added to
    // the dictionary.
    private static final float DISTRACTER_WORD_SCORE_THRESHOLD = 0.4f;

    /**
     * Create a DistracterFilter instance.
     *
     * @param context the context.
     */
    public DistracterFilterCheckingExactMatchesAndSuggestions(final Context context) {
        mContext = context;
        mLocaleToSubtypeMap = new HashMap<>();
        mLocaleToKeyboardMap = new HashMap<>();
        mDictionaryFacilitator = new DictionaryFacilitator();
        mDistractersCache = new LruCache<>(MAX_DISTRACTERS_CACHE_SIZE);
        mKeyboard = null;
    }

    @Override
    public void close() {
        mDictionaryFacilitator.closeDictionaries();
    }

    @Override
    public void updateEnabledSubtypes(final List<InputMethodSubtype> enabledSubtypes) {
        final Map<Locale, InputMethodSubtype> newLocaleToSubtypeMap = new HashMap<>();
        if (enabledSubtypes != null) {
            for (final InputMethodSubtype subtype : enabledSubtypes) {
                final Locale locale = SubtypeLocaleUtils.getSubtypeLocale(subtype);
                if (newLocaleToSubtypeMap.containsKey(locale)) {
                    // Multiple subtypes are enabled for one locale.
                    // TODO: Investigate what we should do for this case.
                    continue;
                }
                newLocaleToSubtypeMap.put(locale, subtype);
            }
        }
        if (mLocaleToSubtypeMap.equals(newLocaleToSubtypeMap)) {
            // Enabled subtypes have not been changed.
            return;
        }
        synchronized (mLock) {
            mLocaleToSubtypeMap.clear();
            mLocaleToSubtypeMap.putAll(newLocaleToSubtypeMap);
            mLocaleToKeyboardMap.clear();
        }
    }

    private void loadKeyboardForLocale(final Locale newLocale) {
        final Keyboard cachedKeyboard = mLocaleToKeyboardMap.get(newLocale);
        if (cachedKeyboard != null) {
            mKeyboard = cachedKeyboard;
            return;
        }
        final InputMethodSubtype subtype;
        synchronized (mLock) {
            subtype = mLocaleToSubtypeMap.get(newLocale);
        }
        if (subtype == null) {
            return;
        }
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT;
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                mContext, editorInfo);
        final Resources res = mContext.getResources();
        final int keyboardWidth = ResourceUtils.getDefaultKeyboardWidth(res);
        final int keyboardHeight = ResourceUtils.getDefaultKeyboardHeight(res);
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight);
        builder.setSubtype(subtype);
        builder.setIsSpellChecker(false /* isSpellChecker */);
        final KeyboardLayoutSet layoutSet = builder.build();
        mKeyboard = layoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET);
    }

    private void loadDictionariesForLocale(final Locale newlocale) throws InterruptedException {
        mDictionaryFacilitator.resetDictionaries(mContext, newlocale,
                false /* useContactsDict */, false /* usePersonalizedDicts */,
                false /* forceReloadMainDictionary */, null /* listener */);
        mDictionaryFacilitator.waitForLoadingMainDictionary(
                TIMEOUT_TO_WAIT_LOADING_DICTIONARIES_IN_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Determine whether a word is a distracter to words in dictionaries.
     *
     * @param prevWordsInfo the information of previous words. Not used for now.
     * @param testedWord the word that will be tested to see whether it is a distracter to words
     *                   in dictionaries.
     * @param locale the locale of word.
     * @return true if testedWord is a distracter, otherwise false.
     */
    @Override
    public boolean isDistracterToWordsInDictionaries(final PrevWordsInfo prevWordsInfo,
            final String testedWord, final Locale locale) {
        if (locale == null) {
            return false;
        }
        if (!locale.equals(mDictionaryFacilitator.getLocale())) {
            synchronized (mLock) {
                if (!mLocaleToSubtypeMap.containsKey(locale)) {
                    Log.e(TAG, "Locale " + locale + " is not enabled.");
                    // TODO: Investigate what we should do for disabled locales.
                    return false;
                }
                loadKeyboardForLocale(locale);
                // Reset dictionaries for the locale.
                try {
                    mDistractersCache.evictAll();
                    loadDictionariesForLocale(locale);
                } catch (final InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for loading dicts in DistracterFilter",
                            e);
                    return false;
                }
            }
        }

        if (DEBUG) {
            Log.d(TAG, "testedWord: " + testedWord);
        }
        final Boolean isCachedDistracter = mDistractersCache.get(testedWord);
        if (isCachedDistracter != null && isCachedDistracter) {
            if (DEBUG) {
                Log.d(TAG, "isDistracter: true (cache hit)");
            }
            return true;
        }

        final boolean isDistracterCheckedByGetMaxFreqencyOfExactMatches =
                checkDistracterUsingMaxFreqencyOfExactMatches(testedWord);
        if (isDistracterCheckedByGetMaxFreqencyOfExactMatches) {
            // Add the word to the cache.
            mDistractersCache.put(testedWord, Boolean.TRUE);
            return true;
        }
        final boolean isValidWord = mDictionaryFacilitator.isValidWord(testedWord,
                false /* ignoreCase */);
        if (isValidWord) {
            // Valid word is not a distractor.
            if (DEBUG) {
                Log.d(TAG, "isDistracter: false (valid word)");
            }
            return false;
        }

        final boolean isDistracterCheckedByGetSuggestion =
                checkDistracterUsingGetSuggestions(testedWord);
        if (isDistracterCheckedByGetSuggestion) {
            // Add the word to the cache.
            mDistractersCache.put(testedWord, Boolean.TRUE);
            return true;
        }
        return false;
    }

    private boolean checkDistracterUsingMaxFreqencyOfExactMatches(final String testedWord) {
        // The tested word is a distracter when there is a word that is exact matched to the tested
        // word and its probability is higher than the tested word's probability.
        final int perfectMatchFreq = mDictionaryFacilitator.getFrequency(testedWord);
        final int exactMatchFreq = mDictionaryFacilitator.getMaxFrequencyOfExactMatches(testedWord);
        final boolean isDistracter = perfectMatchFreq < exactMatchFreq;
        if (DEBUG) {
            Log.d(TAG, "perfectMatchFreq: " + perfectMatchFreq);
            Log.d(TAG, "exactMatchFreq: " + exactMatchFreq);
            Log.d(TAG, "isDistracter: " + isDistracter);
        }
        return isDistracter;
    }

    private boolean checkDistracterUsingGetSuggestions(final String testedWord) {
        if (mKeyboard == null) {
            return false;
        }
        final SettingsValuesForSuggestion settingsValuesForSuggestion =
                new SettingsValuesForSuggestion(false /* blockPotentiallyOffensive */,
                        false /* spaceAwareGestureEnabled */,
                        null /* additionalFeaturesSettingValues */);
        final int trailingSingleQuotesCount = StringUtils.getTrailingSingleQuotesCount(testedWord);
        final String consideredWord = trailingSingleQuotesCount > 0 ?
                testedWord.substring(0, testedWord.length() - trailingSingleQuotesCount) :
                testedWord;
        final WordComposer composer = new WordComposer();
        final int[] codePoints = StringUtils.toCodePointArray(testedWord);

        synchronized (mLock) {
            final int[] coordinates = mKeyboard.getCoordinates(codePoints);
            composer.setComposingWord(codePoints, coordinates);
            final SuggestionResults suggestionResults = mDictionaryFacilitator.getSuggestionResults(
                    composer, PrevWordsInfo.EMPTY_PREV_WORDS_INFO, mKeyboard.getProximityInfo(),
                    settingsValuesForSuggestion, 0 /* sessionId */);
            if (suggestionResults.isEmpty()) {
                return false;
            }
            final SuggestedWordInfo firstSuggestion = suggestionResults.first();
            final boolean isDistractor = suggestionExceedsDistracterThreshold(
                    firstSuggestion, consideredWord, DISTRACTER_WORD_SCORE_THRESHOLD);
            if (DEBUG) {
                Log.d(TAG, "isDistracter: " + isDistractor);
            }
            return isDistractor;
        }
    }

    private static boolean suggestionExceedsDistracterThreshold(final SuggestedWordInfo suggestion,
            final String consideredWord, final float distracterThreshold) {
        if (suggestion == null) {
            return false;
        }
        final int suggestionScore = suggestion.mScore;
        final float normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(
                consideredWord, suggestion.mWord, suggestionScore);
        if (DEBUG) {
            Log.d(TAG, "normalizedScore: " + normalizedScore);
            Log.d(TAG, "distracterThreshold: " + distracterThreshold);
        }
        if (normalizedScore > distracterThreshold) {
            return true;
        }
        return false;
    }
}
