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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.latin.DictionaryFacilitator;
import com.android.inputmethod.latin.PrevWordsInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.WordComposer;

/**
 * This class is used to prevent distracters being added to personalization
 * or user history dictionaries
 */
public class DistracterFilterUsingSuggestion implements DistracterFilter {
    private static final String TAG = DistracterFilterUsingSuggestion.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final long TIMEOUT_TO_WAIT_LOADING_DICTIONARIES_IN_SECONDS = 120;

    private final Context mContext;
    private final Map<Locale, InputMethodSubtype> mLocaleToSubtypeMap;
    private final Map<Locale, Keyboard> mLocaleToKeyboardMap;
    private final DictionaryFacilitator mDictionaryFacilitator;
    private Keyboard mKeyboard;
    private final Object mLock = new Object();

    /**
     * Create a DistracterFilter instance.
     *
     * @param context the context.
     */
    public DistracterFilterUsingSuggestion(final Context context) {
        mContext = context;
        mLocaleToSubtypeMap = new HashMap<>();
        mLocaleToKeyboardMap = new HashMap<>();
        mDictionaryFacilitator = new DictionaryFacilitator();
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

    private static boolean isDistracter(
            final SuggestionResults suggestionResults, final String consideredWord) {
        for (final SuggestedWordInfo suggestedWordInfo : suggestionResults) {
            if (suggestedWordInfo.mWord.equals(consideredWord)) {
                continue;
            }
            // Exact match can include case errors, accent errors, digraph conversions.
            final boolean isExactMatch =
                    (suggestedWordInfo.mKind & SuggestedWordInfo.KIND_FLAG_EXACT_MATCH) != 0;
            final boolean isExactMatchWithIntentionalOmission = (suggestedWordInfo.mKind
                    & SuggestedWordInfo.KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION) != 0;

            if (DEBUG) {
                final float normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(
                        consideredWord, suggestedWordInfo.mWord, suggestedWordInfo.mScore);
                Log.d(TAG, "consideredWord: " +  consideredWord);
                Log.d(TAG, "top suggestion: " +  suggestedWordInfo.mWord);
                Log.d(TAG, "suggestionScore: " +  suggestedWordInfo.mScore);
                Log.d(TAG, "normalizedScore: " +  normalizedScore);
                Log.d(TAG, "isExactMatch: " + isExactMatch);
                Log.d(TAG, "isExactMatchWithIntentionalOmission: "
                            + isExactMatchWithIntentionalOmission);
            }
            return isExactMatch || isExactMatchWithIntentionalOmission;
        }
        return false;
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
                    loadDictionariesForLocale(locale);
                } catch (final InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for loading dicts in DistracterFilter",
                            e);
                    return false;
                }
            }
        }
        if (mKeyboard == null) {
            return false;
        }
        final WordComposer composer = new WordComposer();
        final int[] codePoints = StringUtils.toCodePointArray(testedWord);
        final int[] coordinates = mKeyboard.getCoordinates(codePoints);
        composer.setComposingWord(codePoints, coordinates, PrevWordsInfo.EMPTY_PREV_WORDS_INFO);

        final int trailingSingleQuotesCount = StringUtils.getTrailingSingleQuotesCount(testedWord);
        final String consideredWord = trailingSingleQuotesCount > 0 ?
                testedWord.substring(0, testedWord.length() - trailingSingleQuotesCount) :
                testedWord;

        final SuggestionResults suggestionResults = mDictionaryFacilitator.getSuggestionResults(
                composer, PrevWordsInfo.EMPTY_PREV_WORDS_INFO, mKeyboard.getProximityInfo(),
                true /* blockOffensiveWords */, null /* additionalFeaturesOptions */,
                0 /* sessionId */, null /* rawSuggestions */);
        if (suggestionResults.isEmpty()) {
            return false;
        }
        return isDistracter(suggestionResults, consideredWord);
    }
}
