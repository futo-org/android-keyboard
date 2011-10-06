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

import android.content.Intent;
import android.content.res.Resources;
import android.service.textservice.SpellCheckerService;
import android.text.TextUtils;
import android.util.Log;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

import com.android.inputmethod.compat.ArraysCompatUtils;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.Dictionary.DataType;
import com.android.inputmethod.latin.Dictionary.WordCallback;
import com.android.inputmethod.latin.DictionaryCollection;
import com.android.inputmethod.latin.DictionaryFactory;
import com.android.inputmethod.latin.Flag;
import com.android.inputmethod.latin.LocaleUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SynchronouslyLoadedUserDictionary;
import com.android.inputmethod.latin.Utils;
import com.android.inputmethod.latin.WhitelistDictionary;
import com.android.inputmethod.latin.WordComposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service for spell checking, using LatinIME's dictionaries and mechanisms.
 */
public class AndroidSpellCheckerService extends SpellCheckerService {
    private static final String TAG = AndroidSpellCheckerService.class.getSimpleName();
    private static final boolean DBG = false;
    private static final int POOL_SIZE = 2;

    private static final int CAPITALIZE_NONE = 0; // No caps, or mixed case
    private static final int CAPITALIZE_FIRST = 1; // First only
    private static final int CAPITALIZE_ALL = 2; // All caps

    private final static String[] EMPTY_STRING_ARRAY = new String[0];
    private final static SuggestionsInfo NOT_IN_DICT_EMPTY_SUGGESTIONS =
            new SuggestionsInfo(0, EMPTY_STRING_ARRAY);
    private final static SuggestionsInfo IN_DICT_EMPTY_SUGGESTIONS =
            new SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY,
                    EMPTY_STRING_ARRAY);
    private final static Flag[] USE_FULL_EDIT_DISTANCE_FLAG_ARRAY;
    static {
        // See BinaryDictionary.java for an explanation of these flags
        // Specifially, ALL_CONFIG_FLAGS means that we want to consider all flags with the
        // current dictionary configuration - for example, consider the UMLAUT flag
        // so that it will be turned on for German dictionaries and off for others.
        USE_FULL_EDIT_DISTANCE_FLAG_ARRAY = Arrays.copyOf(BinaryDictionary.ALL_CONFIG_FLAGS,
                BinaryDictionary.ALL_CONFIG_FLAGS.length + 1);
        USE_FULL_EDIT_DISTANCE_FLAG_ARRAY[BinaryDictionary.ALL_CONFIG_FLAGS.length] =
                BinaryDictionary.FLAG_USE_FULL_EDIT_DISTANCE;
    }
    private Map<String, DictionaryPool> mDictionaryPools =
            Collections.synchronizedMap(new TreeMap<String, DictionaryPool>());
    private Map<String, Dictionary> mUserDictionaries =
            Collections.synchronizedMap(new TreeMap<String, Dictionary>());
    private Map<String, Dictionary> mWhitelistDictionaries =
            Collections.synchronizedMap(new TreeMap<String, Dictionary>());

    // The threshold for a candidate to be offered as a suggestion.
    private double mSuggestionThreshold;
    // The threshold for a suggestion to be considered "likely".
    private double mLikelyThreshold;

    @Override public void onCreate() {
        super.onCreate();
        mSuggestionThreshold =
                Double.parseDouble(getString(R.string.spellchecker_suggestion_threshold_value));
        mLikelyThreshold =
                Double.parseDouble(getString(R.string.spellchecker_likely_threshold_value));
    }

    @Override
    public Session createSession() {
        return new AndroidSpellCheckerSession(this);
    }

    private static class SuggestionsGatherer implements WordCallback {
        public static class Result {
            public final String[] mSuggestions;
            public final boolean mHasLikelySuggestions;
            public Result(final String[] gatheredSuggestions, final boolean hasLikelySuggestions) {
                mSuggestions = gatheredSuggestions;
                mHasLikelySuggestions = hasLikelySuggestions;
            }
        }

        private final ArrayList<CharSequence> mSuggestions;
        private final int[] mScores;
        private final String mOriginalText;
        private final double mSuggestionThreshold;
        private final double mLikelyThreshold;
        private final int mMaxLength;
        private int mLength = 0;

        // The two following attributes are only ever filled if the requested max length
        // is 0 (or less, which is treated the same).
        private String mBestSuggestion = null;
        private int mBestScore = Integer.MIN_VALUE; // As small as possible

        SuggestionsGatherer(final String originalText, final double suggestionThreshold,
                final double likelyThreshold, final int maxLength) {
            mOriginalText = originalText;
            mSuggestionThreshold = suggestionThreshold;
            mLikelyThreshold = likelyThreshold;
            mMaxLength = maxLength;
            mSuggestions = new ArrayList<CharSequence>(maxLength + 1);
            mScores = new int[mMaxLength];
        }

        @Override
        synchronized public boolean addWord(char[] word, int wordOffset, int wordLength, int score,
                int dicTypeId, DataType dataType) {
            final int positionIndex = ArraysCompatUtils.binarySearch(mScores, 0, mLength, score);
            // binarySearch returns the index if the element exists, and -<insertion index> - 1
            // if it doesn't. See documentation for binarySearch.
            final int insertIndex = positionIndex >= 0 ? positionIndex : -positionIndex - 1;

            if (insertIndex == 0 && mLength >= mMaxLength) {
                // In the future, we may want to keep track of the best suggestion score even if
                // we are asked for 0 suggestions. In this case, we can use the following
                // (tested) code to keep it:
                // If the maxLength is 0 (should never be less, but if it is, it's treated as 0)
                // then we need to keep track of the best suggestion in mBestScore and
                // mBestSuggestion. This is so that we know whether the best suggestion makes
                // the score cutoff, since we need to know that to return a meaningful
                // looksLikeTypo.
                // if (0 >= mMaxLength) {
                //     if (score > mBestScore) {
                //         mBestScore = score;
                //         mBestSuggestion = new String(word, wordOffset, wordLength);
                //     }
                // }
                return true;
            }
            if (insertIndex >= mMaxLength) {
                // We found a suggestion, but its score is too weak to be kept considering
                // the suggestion limit.
                return true;
            }

            // Compute the normalized score and skip this word if it's normalized score does not
            // make the threshold.
            final String wordString = new String(word, wordOffset, wordLength);
            final double normalizedScore =
                    Utils.calcNormalizedScore(mOriginalText, wordString, score);
            if (normalizedScore < mSuggestionThreshold) {
                if (DBG) Log.i(TAG, wordString + " does not make the score threshold");
                return true;
            }

            if (mLength < mMaxLength) {
                final int copyLen = mLength - insertIndex;
                ++mLength;
                System.arraycopy(mScores, insertIndex, mScores, insertIndex + 1, copyLen);
                mSuggestions.add(insertIndex, wordString);
            } else {
                System.arraycopy(mScores, 1, mScores, 0, insertIndex);
                mSuggestions.add(insertIndex, wordString);
                mSuggestions.remove(0);
            }
            mScores[insertIndex] = score;

            return true;
        }

        public Result getResults(final int capitalizeType, final Locale locale) {
            final String[] gatheredSuggestions;
            final boolean hasLikelySuggestions;
            if (0 == mLength) {
                // Either we found no suggestions, or we found some BUT the max length was 0.
                // If we found some mBestSuggestion will not be null. If it is null, then
                // we found none, regardless of the max length.
                if (null == mBestSuggestion) {
                    gatheredSuggestions = null;
                    hasLikelySuggestions = false;
                } else {
                    gatheredSuggestions = EMPTY_STRING_ARRAY;
                    final double normalizedScore =
                            Utils.calcNormalizedScore(mOriginalText, mBestSuggestion, mBestScore);
                    hasLikelySuggestions = (normalizedScore > mLikelyThreshold);
                }
            } else {
                if (DBG) {
                    if (mLength != mSuggestions.size()) {
                        Log.e(TAG, "Suggestion size is not the same as stored mLength");
                    }
                    for (int i = mLength - 1; i >= 0; --i) {
                        Log.i(TAG, "" + mScores[i] + " " + mSuggestions.get(i));
                    }
                }
                Collections.reverse(mSuggestions);
                Utils.removeDupes(mSuggestions);
                if (CAPITALIZE_ALL == capitalizeType) {
                    for (int i = 0; i < mSuggestions.size(); ++i) {
                        // get(i) returns a CharSequence which is actually a String so .toString()
                        // should return the same object.
                        mSuggestions.set(i, mSuggestions.get(i).toString().toUpperCase(locale));
                    }
                } else if (CAPITALIZE_FIRST == capitalizeType) {
                    for (int i = 0; i < mSuggestions.size(); ++i) {
                        // Likewise
                        mSuggestions.set(i, Utils.toTitleCase(mSuggestions.get(i).toString(),
                                locale));
                    }
                }
                // This returns a String[], while toArray() returns an Object[] which cannot be cast
                // into a String[].
                gatheredSuggestions = mSuggestions.toArray(EMPTY_STRING_ARRAY);

                final int bestScore = mScores[mLength - 1];
                final CharSequence bestSuggestion = mSuggestions.get(0);
                final double normalizedScore =
                        Utils.calcNormalizedScore(mOriginalText, bestSuggestion, bestScore);
                hasLikelySuggestions = (normalizedScore > mLikelyThreshold);
                if (DBG) {
                    Log.i(TAG, "Best suggestion : " + bestSuggestion + ", score " + bestScore);
                    Log.i(TAG, "Normalized score = " + normalizedScore
                            + " (threshold " + mLikelyThreshold
                            + ") => hasLikelySuggestions = " + hasLikelySuggestions);
                }
            }
            return new Result(gatheredSuggestions, hasLikelySuggestions);
        }
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        final Map<String, DictionaryPool> oldPools = mDictionaryPools;
        mDictionaryPools = Collections.synchronizedMap(new TreeMap<String, DictionaryPool>());
        final Map<String, Dictionary> oldUserDictionaries = mUserDictionaries;
        mUserDictionaries = Collections.synchronizedMap(new TreeMap<String, Dictionary>());
        final Map<String, Dictionary> oldWhitelistDictionaries = mWhitelistDictionaries;
        mWhitelistDictionaries = Collections.synchronizedMap(new TreeMap<String, Dictionary>());
        for (DictionaryPool pool : oldPools.values()) {
            pool.close();
        }
        for (Dictionary dict : oldUserDictionaries.values()) {
            dict.close();
        }
        for (Dictionary dict : oldWhitelistDictionaries.values()) {
            dict.close();
        }
        return false;
    }

    private DictionaryPool getDictionaryPool(final String locale) {
        DictionaryPool pool = mDictionaryPools.get(locale);
        if (null == pool) {
            final Locale localeObject = LocaleUtils.constructLocaleFromString(locale);
            pool = new DictionaryPool(POOL_SIZE, this, localeObject);
            mDictionaryPools.put(locale, pool);
        }
        return pool;
    }

    public DictAndProximity createDictAndProximity(final Locale locale) {
        final ProximityInfo proximityInfo = ProximityInfo.createSpellCheckerProximityInfo();
        final Resources resources = getResources();
        final int fallbackResourceId = Utils.getMainDictionaryResourceId(resources);
        final DictionaryCollection dictionaryCollection =
                DictionaryFactory.createDictionaryFromManager(this, locale, fallbackResourceId,
                        USE_FULL_EDIT_DISTANCE_FLAG_ARRAY);
        final String localeStr = locale.toString();
        Dictionary userDictionary = mUserDictionaries.get(localeStr);
        if (null == userDictionary) {
            userDictionary = new SynchronouslyLoadedUserDictionary(this, localeStr, true);
            mUserDictionaries.put(localeStr, userDictionary);
        }
        dictionaryCollection.addDictionary(userDictionary);
        Dictionary whitelistDictionary = mWhitelistDictionaries.get(localeStr);
        if (null == whitelistDictionary) {
            whitelistDictionary = new WhitelistDictionary(this, locale);
            mWhitelistDictionaries.put(localeStr, whitelistDictionary);
        }
        dictionaryCollection.addDictionary(whitelistDictionary);
        return new DictAndProximity(dictionaryCollection, proximityInfo);
    }

    // This method assumes the text is not empty or null.
    private static int getCapitalizationType(String text) {
        // If the first char is not uppercase, then the word is either all lower case,
        // and in either case we return CAPITALIZE_NONE.
        if (!Character.isUpperCase(text.codePointAt(0))) return CAPITALIZE_NONE;
        final int len = text.codePointCount(0, text.length());
        int capsCount = 1;
        for (int i = 1; i < len; ++i) {
            if (1 != capsCount && i != capsCount) break;
            if (Character.isUpperCase(text.codePointAt(i))) ++capsCount;
        }
        // We know the first char is upper case. So we want to test if either everything
        // else is lower case, or if everything else is upper case. If the string is
        // exactly one char long, then we will arrive here with capsCount 1, and this is
        // correct, too.
        if (1 == capsCount) return CAPITALIZE_FIRST;
        return (len == capsCount ? CAPITALIZE_ALL : CAPITALIZE_NONE);
    }

    private static class AndroidSpellCheckerSession extends Session {
        // Immutable, but need the locale which is not available in the constructor yet
        private DictionaryPool mDictionaryPool;
        // Likewise
        private Locale mLocale;

        private final AndroidSpellCheckerService mService;

        AndroidSpellCheckerSession(final AndroidSpellCheckerService service) {
            mService = service;
        }

        @Override
        public void onCreate() {
            final String localeString = getLocale();
            mDictionaryPool = mService.getDictionaryPool(localeString);
            mLocale = LocaleUtils.constructLocaleFromString(localeString);
        }

        /**
         * Finds out whether a particular string should be filtered out of spell checking.
         *
         * This will loosely match URLs, numbers, symbols.
         *
         * @param text the string to evaluate.
         * @return true if we should filter this text out, false otherwise
         */
        private boolean shouldFilterOut(final String text) {
            if (TextUtils.isEmpty(text) || text.length() <= 1) return true;

            // TODO: check if an equivalent processing can't be done more quickly with a
            // compiled regexp.
            // Filter by first letter
            final int firstCodePoint = text.codePointAt(0);
            // Filter out words that don't start with a letter or an apostrophe
            if (!Character.isLetter(firstCodePoint)
                    && '\'' != firstCodePoint) return true;

            // Filter contents
            final int length = text.length();
            int letterCount = 0;
            for (int i = 0; i < length; ++i) {
                final int codePoint = text.codePointAt(i);
                // Any word containing a '@' is probably an e-mail address
                // Any word containing a '/' is probably either an ad-hoc combination of two
                // words or a URI - in either case we don't want to spell check that
                if ('@' == codePoint
                        || '/' == codePoint) return true;
                if (Character.isLetter(codePoint)) ++letterCount;
            }
            // Guestimate heuristic: perform spell checking if at least 3/4 of the characters
            // in this word are letters
            return (letterCount * 4 < length * 3);
        }

        // Note : this must be reentrant
        /**
         * Gets a list of suggestions for a specific string. This returns a list of possible
         * corrections for the text passed as an argument. It may split or group words, and
         * even perform grammatical analysis.
         */
        @Override
        public SuggestionsInfo onGetSuggestions(final TextInfo textInfo,
                final int suggestionsLimit) {
            try {
                final String text = textInfo.getText();

                if (shouldFilterOut(text)) {
                    DictAndProximity dictInfo = null;
                    try {
                        dictInfo = mDictionaryPool.takeOrGetNull();
                        if (null == dictInfo) return NOT_IN_DICT_EMPTY_SUGGESTIONS;
                        return dictInfo.mDictionary.isValidWord(text) ? IN_DICT_EMPTY_SUGGESTIONS
                                : NOT_IN_DICT_EMPTY_SUGGESTIONS;
                    } finally {
                        if (null != dictInfo) {
                            if (!mDictionaryPool.offer(dictInfo)) {
                                Log.e(TAG, "Can't re-insert a dictionary into its pool");
                            }
                        }
                    }
                }

                // TODO: Don't gather suggestions if the limit is <= 0 unless necessary
                final SuggestionsGatherer suggestionsGatherer = new SuggestionsGatherer(text,
                        mService.mSuggestionThreshold, mService.mLikelyThreshold, suggestionsLimit);
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
                                proximityIndex,
                                proximityIndex + SpellCheckerProximityInfo.ROW_SIZE);
                    }
                    composer.add(character, proximities,
                            WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
                }

                final int capitalizeType = getCapitalizationType(text);
                boolean isInDict = true;
                DictAndProximity dictInfo = null;
                try {
                    dictInfo = mDictionaryPool.takeOrGetNull();
                    if (null == dictInfo) return NOT_IN_DICT_EMPTY_SUGGESTIONS;
                    dictInfo.mDictionary.getWords(composer, suggestionsGatherer,
                            dictInfo.mProximityInfo);
                    isInDict = dictInfo.mDictionary.isValidWord(text);
                    if (!isInDict && CAPITALIZE_NONE != capitalizeType) {
                        // We want to test the word again if it's all caps or first caps only.
                        // If it's fully down, we already tested it, if it's mixed case, we don't
                        // want to test a lowercase version of it.
                        isInDict = dictInfo.mDictionary.isValidWord(text.toLowerCase(mLocale));
                    }
                } finally {
                    if (null != dictInfo) {
                        if (!mDictionaryPool.offer(dictInfo)) {
                            Log.e(TAG, "Can't re-insert a dictionary into its pool");
                        }
                    }
                }

                final SuggestionsGatherer.Result result = suggestionsGatherer.getResults(
                        capitalizeType, mLocale);

                if (DBG) {
                    Log.i(TAG, "Spell checking results for " + text + " with suggestion limit "
                            + suggestionsLimit);
                    Log.i(TAG, "IsInDict = " + isInDict);
                    Log.i(TAG, "LooksLikeTypo = " + (!isInDict));
                    Log.i(TAG, "HasLikelySuggestions = " + result.mHasLikelySuggestions);
                    if (null != result.mSuggestions) {
                        for (String suggestion : result.mSuggestions) {
                            Log.i(TAG, suggestion);
                        }
                    }
                }

                // TODO: actually use result.mHasLikelySuggestions
                final int flags =
                        (isInDict ? SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY
                                : SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO);
                return new SuggestionsInfo(flags, result.mSuggestions);
            } catch (RuntimeException e) {
                // Don't kill the keyboard if there is a bug in the spell checker
                if (DBG) {
                    throw e;
                } else {
                    Log.e(TAG, "Exception while spellcheking: " + e);
                    return NOT_IN_DICT_EMPTY_SUGGESTIONS;
                }
            }
        }
    }
}
