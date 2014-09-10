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
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.content.res.Resources;
import android.text.InputType;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.DictionaryFacilitatorLruCache;
import com.android.inputmethod.latin.PrevWordsInfo;
import com.android.inputmethod.latin.RichInputMethodSubtype;
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

    private static final int MAX_DICTIONARY_FACILITATOR_CACHE_SIZE = 3;
    private static final int MAX_DISTRACTERS_CACHE_SIZE = 1024;

    private final Context mContext;
    private final ConcurrentHashMap<Locale, InputMethodSubtype> mLocaleToSubtypeCache;
    private final ConcurrentHashMap<Locale, Keyboard> mLocaleToKeyboardCache;
    private final DictionaryFacilitatorLruCache mDictionaryFacilitatorLruCache;
    // The key is a pair of a locale and a word. The value indicates the word is a distracter to
    // words of the locale.
    private final LruCache<Pair<Locale, String>, Boolean> mDistractersCache;
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
        mLocaleToSubtypeCache = new ConcurrentHashMap<>();
        mLocaleToKeyboardCache = new ConcurrentHashMap<>();
        mDictionaryFacilitatorLruCache = new DictionaryFacilitatorLruCache(context,
                MAX_DICTIONARY_FACILITATOR_CACHE_SIZE, "" /* dictionaryNamePrefix */);
        mDistractersCache = new LruCache<>(MAX_DISTRACTERS_CACHE_SIZE);
    }

    @Override
    public void close() {
        mLocaleToSubtypeCache.clear();
        mLocaleToKeyboardCache.clear();
        mDictionaryFacilitatorLruCache.evictAll();
        // Don't clear mDistractersCache.
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
        if (mLocaleToSubtypeCache.equals(newLocaleToSubtypeMap)) {
            // Enabled subtypes have not been changed.
            return;
        }
        // Update subtype and keyboard map for locales that are in the current mapping.
        for (final Locale locale: mLocaleToSubtypeCache.keySet()) {
            if (newLocaleToSubtypeMap.containsKey(locale)) {
                final InputMethodSubtype newSubtype = newLocaleToSubtypeMap.remove(locale);
                if (newSubtype.equals(newLocaleToSubtypeMap.get(locale))) {
                    // Mapping has not been changed.
                    continue;
                }
                mLocaleToSubtypeCache.replace(locale, newSubtype);
            } else {
                mLocaleToSubtypeCache.remove(locale);
            }
            mLocaleToKeyboardCache.remove(locale);
        }
        // Add locales that are not in the current mapping.
        mLocaleToSubtypeCache.putAll(newLocaleToSubtypeMap);
    }

    private Keyboard getKeyboardForLocale(final Locale locale) {
        final Keyboard cachedKeyboard = mLocaleToKeyboardCache.get(locale);
        if (cachedKeyboard != null) {
            return cachedKeyboard;
        }
        final InputMethodSubtype subtype = mLocaleToSubtypeCache.get(locale);
        if (subtype == null) {
            return null;
        }
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT;
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                mContext, editorInfo);
        final Resources res = mContext.getResources();
        final int keyboardWidth = ResourceUtils.getDefaultKeyboardWidth(res);
        final int keyboardHeight = ResourceUtils.getDefaultKeyboardHeight(res);
        builder.setKeyboardGeometry(keyboardWidth, keyboardHeight);
        builder.setSubtype(new RichInputMethodSubtype(subtype));
        builder.setIsSpellChecker(false /* isSpellChecker */);
        final KeyboardLayoutSet layoutSet = builder.build();
        final Keyboard newKeyboard = layoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET);
        mLocaleToKeyboardCache.put(locale, newKeyboard);
        return newKeyboard;
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
        if (!mLocaleToSubtypeCache.containsKey(locale)) {
            Log.e(TAG, "Locale " + locale + " is not enabled.");
            // TODO: Investigate what we should do for disabled locales.
            return false;
        }
        final DictionaryFacilitator dictionaryFacilitator =
                mDictionaryFacilitatorLruCache.get(locale);
        if (DEBUG) {
            Log.d(TAG, "testedWord: " + testedWord);
        }
        final Pair<Locale, String> cacheKey = new Pair<>(locale, testedWord);
        final Boolean isCachedDistracter = mDistractersCache.get(cacheKey);
        if (isCachedDistracter != null && isCachedDistracter) {
            if (DEBUG) {
                Log.d(TAG, "isDistracter: true (cache hit)");
            }
            return true;
        }

        final boolean isDistracterCheckedByGetMaxFreqencyOfExactMatches =
                checkDistracterUsingMaxFreqencyOfExactMatches(dictionaryFacilitator, testedWord);
        if (isDistracterCheckedByGetMaxFreqencyOfExactMatches) {
            // Add the pair of locale and word to the cache.
            mDistractersCache.put(cacheKey, Boolean.TRUE);
            return true;
        }
        final boolean Word = dictionaryFacilitator.isValidWord(testedWord, false /* ignoreCase */);
        if (Word) {
            // Valid word is not a distractor.
            if (DEBUG) {
                Log.d(TAG, "isDistracter: false (valid word)");
            }
            return false;
        }

        final Keyboard keyboard = getKeyboardForLocale(locale);
        final boolean isDistracterCheckedByGetSuggestion =
                checkDistracterUsingGetSuggestions(dictionaryFacilitator, keyboard, testedWord);
        if (isDistracterCheckedByGetSuggestion) {
            // Add the pair of locale and word to the cache.
            mDistractersCache.put(cacheKey, Boolean.TRUE);
            return true;
        }
        return false;
    }

    private static boolean checkDistracterUsingMaxFreqencyOfExactMatches(
            final DictionaryFacilitator dictionaryFacilitator, final String testedWord) {
        // The tested word is a distracter when there is a word that is exact matched to the tested
        // word and its probability is higher than the tested word's probability.
        final int perfectMatchFreq = dictionaryFacilitator.getFrequency(testedWord);
        final int exactMatchFreq = dictionaryFacilitator.getMaxFrequencyOfExactMatches(testedWord);
        final boolean isDistracter = perfectMatchFreq < exactMatchFreq;
        if (DEBUG) {
            Log.d(TAG, "perfectMatchFreq: " + perfectMatchFreq);
            Log.d(TAG, "exactMatchFreq: " + exactMatchFreq);
            Log.d(TAG, "isDistracter: " + isDistracter);
        }
        return isDistracter;
    }

    private boolean checkDistracterUsingGetSuggestions(
            final DictionaryFacilitator dictionaryFacilitator, final Keyboard keyboard,
            final String testedWord) {
        if (keyboard == null) {
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
        final int[] coordinates = keyboard.getCoordinates(codePoints);
        composer.setComposingWord(codePoints, coordinates);
        final SuggestionResults suggestionResults;
        synchronized (mLock) {
            suggestionResults = dictionaryFacilitator.getSuggestionResults(
                    composer, PrevWordsInfo.EMPTY_PREV_WORDS_INFO, keyboard.getProximityInfo(),
                    settingsValuesForSuggestion, 0 /* sessionId */);
        }
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

    private boolean shouldBeLowerCased(final PrevWordsInfo prevWordsInfo, final String testedWord,
            final Locale locale) {
        final DictionaryFacilitator dictionaryFacilitator =
                mDictionaryFacilitatorLruCache.get(locale);
        if (dictionaryFacilitator.isValidWord(testedWord, false /* ignoreCase */)) {
            return false;
        }
        final String lowerCaseTargetWord = testedWord.toLowerCase(locale);
        if (testedWord.equals(lowerCaseTargetWord)) {
            return false;
        }
        if (dictionaryFacilitator.isValidWord(lowerCaseTargetWord, false /* ignoreCase */)) {
            return true;
        }
        if (StringUtils.getCapitalizationType(testedWord) == StringUtils.CAPITALIZE_FIRST
                && !prevWordsInfo.isValid()) {
            // TODO: Check beginning-of-sentence.
            return true;
        }
        return false;
    }

    @Override
    public int getWordHandlingType(final PrevWordsInfo prevWordsInfo, final String testedWord,
            final Locale locale) {
        // TODO: Use this method for user history dictionary.
        if (testedWord == null|| locale == null) {
            return HandlingType.getHandlingType(false /* shouldBeLowerCased */, false /* isOov */);
        }
        final boolean shouldBeLowerCased = shouldBeLowerCased(prevWordsInfo, testedWord, locale);
        final String caseModifiedWord =
                shouldBeLowerCased ? testedWord.toLowerCase(locale) : testedWord;
        final boolean isOov = !mDictionaryFacilitatorLruCache.get(locale).isValidWord(
                caseModifiedWord, false /* ignoreCase */);
        return HandlingType.getHandlingType(shouldBeLowerCased, isOov);
    }
}
