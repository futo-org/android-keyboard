/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin.spellcheck;

import android.content.res.Resources;
import android.service.textservice.SpellCheckerService;
import android.service.textservice.SpellCheckerService.Session;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

import com.android.inputmethod.compat.ArraysCompatUtils;
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.Dictionary.DataType;
import com.android.inputmethod.latin.Dictionary.WordCallback;
import com.android.inputmethod.latin.DictionaryFactory;
import com.android.inputmethod.latin.Utils;
import com.android.inputmethod.latin.WordComposer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service for spell checking, using LatinIME's dictionaries and mechanisms.
 */
public class AndroidSpellCheckerService extends SpellCheckerService {
    private static final String TAG = AndroidSpellCheckerService.class.getSimpleName();
    private static final boolean DBG = true;

    private final static String[] emptyArray = new String[0];
    private final ProximityInfo mProximityInfo = ProximityInfo.getSpellCheckerProximityInfo();
    private final Map<String, Dictionary> mDictionaries =
            Collections.synchronizedMap(new TreeMap<String, Dictionary>());

    @Override
    public Session createSession() {
        return new AndroidSpellCheckerSession();
    }

    private static class SuggestionsGatherer implements WordCallback {
        private final int DEFAULT_SUGGESTION_LENGTH = 16;
        private final String[] mSuggestions;
        private final int[] mScores;
        private final int mMaxLength;
        private int mLength = 0;

        SuggestionsGatherer(final int maxLength) {
            mMaxLength = maxLength;
            mSuggestions = new String[mMaxLength];
            mScores = new int[mMaxLength];
        }

        @Override
        synchronized public boolean addWord(char[] word, int wordOffset, int wordLength, int score,
                int dicTypeId, DataType dataType) {
            final int positionIndex = ArraysCompatUtils.binarySearch(mScores, 0, mLength, score);
            // binarySearch returns the index if the element exists, and -<insertion index> - 1
            // if it doesn't. See documentation for binarySearch.
            final int insertIndex = positionIndex >= 0 ? positionIndex : -positionIndex - 1;

            if (mLength < mMaxLength) {
                final int copyLen = mLength - insertIndex;
                ++mLength;
                System.arraycopy(mScores, insertIndex, mScores, insertIndex + 1, copyLen);
                System.arraycopy(mSuggestions, insertIndex, mSuggestions, insertIndex + 1, copyLen);
            } else {
                if (insertIndex == 0) return true;
                System.arraycopy(mScores, 1, mScores, 0, insertIndex);
                System.arraycopy(mSuggestions, 1, mSuggestions, 0, insertIndex);
            }
            mScores[insertIndex] = score;
            mSuggestions[insertIndex] = new String(word, wordOffset, wordLength);

            return true;
        }

        public String[] getGatheredSuggestions() {
            if (0 == mLength) return null;

            final String[] results = new String[mLength];
            for (int i = mLength - 1; i >= 0; --i) {
                results[mLength - i - 1] = mSuggestions[i];
            }
            return results;
        }
    }

    private Dictionary getDictionary(final String locale) {
        Dictionary dictionary = mDictionaries.get(locale);
        if (null == dictionary) {
            final Resources resources = getResources();
            final int fallbackResourceId = Utils.getMainDictionaryResourceId(resources);
            final Locale localeObject = Utils.constructLocaleFromString(locale);
            dictionary = DictionaryFactory.createDictionaryFromManager(this, localeObject,
                    fallbackResourceId);
            mDictionaries.put(locale, dictionary);
        }
        return dictionary;
    }

    private class AndroidSpellCheckerSession extends Session {
        @Override
        public void onCreate() {
        }

        // Note : this must be reentrant
        /**
         * Gets a list of suggestions for a specific string. This returns a list of possible
         * corrections for the text passed as an arguments. It may split or group words, and
         * even perform grammatical analysis.
         */
        @Override
        public SuggestionsInfo onGetSuggestions(final TextInfo textInfo,
                final int suggestionsLimit) {
            final String locale = getLocale();
            final Dictionary dictionary = getDictionary(locale);
            final String text = textInfo.getText();

            final SuggestionsGatherer suggestionsGatherer =
                    new SuggestionsGatherer(suggestionsLimit);
            final WordComposer composer = new WordComposer();
            final int length = text.length();
            for (int i = 0; i < length; ++i) {
                final int character = text.codePointAt(i);
                final int proximityIndex = SpellCheckerProximityInfo.getIndexOf(character);
                final int[] proximities;
                if (-1 == proximityIndex) {
                    proximities = new int[] { character };
                } else {
                    proximities = Arrays.copyOfRange(SpellCheckerProximityInfo.PROXIMITY,
                            proximityIndex, proximityIndex + SpellCheckerProximityInfo.ROW_SIZE);
                }
                composer.add(character, proximities,
                        WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
            }
            dictionary.getWords(composer, suggestionsGatherer, mProximityInfo);
            final boolean isInDict = dictionary.isValidWord(text);
            final String[] suggestions = suggestionsGatherer.getGatheredSuggestions();

            final int flags =
                    (isInDict ? SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY : 0)
                            | (null != suggestions
                                    ? SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO : 0);
            return new SuggestionsInfo(flags, suggestions);
        }
    }
}
