/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
public class Suggest {
    public static final String TAG = Suggest.class.getSimpleName();

    public static final int APPROX_MAX_WORD_LENGTH = 32;

    // TODO: rename this to CORRECTION_OFF
    public static final int CORRECTION_NONE = 0;
    // TODO: rename this to CORRECTION_ON
    public static final int CORRECTION_FULL = 1;

    public static final String DICT_KEY_USER_TYPED = "user_typed";
    public static final String DICT_KEY_MAIN = "main";
    public static final String DICT_KEY_CONTACTS = "contacts";
    // User dictionary, the system-managed one.
    public static final String DICT_KEY_USER = "user";
    // User history dictionary internal to LatinIME
    public static final String DICT_KEY_USER_HISTORY = "history";
    public static final String DICT_KEY_WHITELIST ="whitelist";

    private static final boolean DBG = LatinImeLogger.sDBG;

    private Dictionary mMainDictionary;
    private ContactsBinaryDictionary mContactsDict;
    private WhitelistDictionary mWhiteListDictionary;
    private final ConcurrentHashMap<String, Dictionary> mDictionaries =
            new ConcurrentHashMap<String, Dictionary>();

    public static final int MAX_SUGGESTIONS = 18;

    private float mAutoCorrectionThreshold;

    // TODO: Remove these member variables by passing more context to addWord() callback method
    private boolean mIsFirstCharCapitalized;
    private boolean mIsAllUpperCase;
    private int mTrailingSingleQuotesCount;

    // Locale used for upper- and title-casing words
    final private Locale mLocale;

    private static final int MINIMUM_SAFETY_NET_CHAR_LENGTH = 4;

    public Suggest(final Context context, final Locale locale) {
        initAsynchronously(context, locale);
        mLocale = locale;
    }

    /* package for test */ Suggest(final Context context, final File dictionary,
            final long startOffset, final long length, final Locale locale) {
        final Dictionary mainDict = DictionaryFactory.createDictionaryForTest(context, dictionary,
                startOffset, length /* useFullEditDistance */, false, locale);
        mLocale = locale;
        mMainDictionary = mainDict;
        addOrReplaceDictionary(mDictionaries, DICT_KEY_MAIN, mainDict);
        initWhitelistAndAutocorrectAndPool(context, locale);
    }

    private void initWhitelistAndAutocorrectAndPool(final Context context, final Locale locale) {
        mWhiteListDictionary = new WhitelistDictionary(context, locale);
        addOrReplaceDictionary(mDictionaries, DICT_KEY_WHITELIST, mWhiteListDictionary);
    }

    private void initAsynchronously(final Context context, final Locale locale) {
        resetMainDict(context, locale);

        // TODO: read the whitelist and init the pool asynchronously too.
        // initPool should be done asynchronously now that the pool is thread-safe.
        initWhitelistAndAutocorrectAndPool(context, locale);
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

    public void resetMainDict(final Context context, final Locale locale) {
        mMainDictionary = null;
        new Thread("InitializeBinaryDictionary") {
            @Override
            public void run() {
                final DictionaryCollection newMainDict =
                        DictionaryFactory.createMainDictionaryFromManager(context, locale);
                addOrReplaceDictionary(mDictionaries, DICT_KEY_MAIN, newMainDict);
                mMainDictionary = newMainDict;
            }
        }.start();
    }

    // The main dictionary could have been loaded asynchronously.  Don't cache the return value
    // of this method.
    public boolean hasMainDictionary() {
        return null != mMainDictionary && mMainDictionary.isInitialized();
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

    public static int getApproxMaxWordLength() {
        return APPROX_MAX_WORD_LENGTH;
    }

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set. This refers to the system-managed user dictionary.
     */
    public void setUserDictionary(UserBinaryDictionary userDictionary) {
        addOrReplaceDictionary(mDictionaries, DICT_KEY_USER, userDictionary);
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded. It is also possible to remove
     * the contacts dictionary by passing null to this method. In this case no contacts dictionary
     * won't be used.
     */
    public void setContactsDictionary(ContactsBinaryDictionary contactsDictionary) {
        mContactsDict = contactsDictionary;
        addOrReplaceDictionary(mDictionaries, DICT_KEY_CONTACTS, contactsDictionary);
    }

    public void setUserHistoryDictionary(UserHistoryDictionary userHistoryDictionary) {
        addOrReplaceDictionary(mDictionaries, DICT_KEY_USER_HISTORY, userHistoryDictionary);
    }

    public void setAutoCorrectionThreshold(float threshold) {
        mAutoCorrectionThreshold = threshold;
    }

    private static CharSequence capitalizeWord(final boolean all, final boolean first,
            final CharSequence word) {
        if (TextUtils.isEmpty(word) || !(all || first)) return word;
        final int wordLength = word.length();
        final StringBuilder sb = new StringBuilder(getApproxMaxWordLength());
        // TODO: Must pay attention to locale when changing case.
        if (all) {
            sb.append(word.toString().toUpperCase());
        } else if (first) {
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (wordLength > 1) {
                sb.append(word.subSequence(1, wordLength));
            }
        }
        return sb;
    }

    // TODO: cleanup dictionaries looking up and suggestions building with SuggestedWords.Builder
    public SuggestedWords getSuggestedWords(
            final WordComposer wordComposer, CharSequence prevWordForBigram,
            final ProximityInfo proximityInfo, final boolean isCorrectionEnabled,
            final boolean isPrediction) {
        LatinImeLogger.onStartSuggestion(prevWordForBigram);
        mIsFirstCharCapitalized = !isPrediction && wordComposer.isFirstCharCapitalized();
        mIsAllUpperCase = !isPrediction && wordComposer.isAllUpperCase();
        mTrailingSingleQuotesCount = wordComposer.trailingSingleQuotesCount();
        final ArrayList<SuggestedWordInfo> suggestionsContainer =
                new ArrayList<SuggestedWordInfo>(MAX_SUGGESTIONS);

        final String typedWord = wordComposer.getTypedWord();
        final String consideredWord = mTrailingSingleQuotesCount > 0
                ? typedWord.substring(0, typedWord.length() - mTrailingSingleQuotesCount)
                : typedWord;
        // Treating USER_TYPED as UNIGRAM suggestion for logging now.
        LatinImeLogger.onAddSuggestedWord(typedWord, DICT_KEY_USER_TYPED);

        if (wordComposer.size() <= 1 && isCorrectionEnabled) {
            // At first character typed, search only the bigrams
            if (!TextUtils.isEmpty(prevWordForBigram)) {
                final CharSequence lowerPrevWord;
                if (StringUtils.hasUpperCase(prevWordForBigram)) {
                    // TODO: Must pay attention to locale when changing case.
                    lowerPrevWord = prevWordForBigram.toString().toLowerCase();
                } else {
                    lowerPrevWord = null;
                }
                for (final String key : mDictionaries.keySet()) {
                    final Dictionary dictionary = mDictionaries.get(key);
                    final ArrayList<SuggestedWordInfo> localSuggestions =
                            dictionary.getBigrams(wordComposer, prevWordForBigram);
                    if (null != lowerPrevWord) {
                        localSuggestions.addAll(dictionary.getBigrams(wordComposer, lowerPrevWord));
                    }
                    for (final SuggestedWordInfo localSuggestion : localSuggestions) {
                        addWord(localSuggestion, key, suggestionsContainer, consideredWord);
                    }
                }
            }
        } else if (wordComposer.size() > 1) {
            final WordComposer wordComposerForLookup;
            if (mTrailingSingleQuotesCount > 0) {
                wordComposerForLookup = new WordComposer(wordComposer);
                for (int i = mTrailingSingleQuotesCount - 1; i >= 0; --i) {
                    wordComposerForLookup.deleteLast();
                }
            } else {
                wordComposerForLookup = wordComposer;
            }
            // At second character typed, search the unigrams (scores being affected by bigrams)
            for (final String key : mDictionaries.keySet()) {
                // Skip UserUnigramDictionary and WhitelistDictionary to lookup
                if (key.equals(DICT_KEY_USER_HISTORY) || key.equals(DICT_KEY_WHITELIST))
                    continue;
                final Dictionary dictionary = mDictionaries.get(key);
                final ArrayList<SuggestedWordInfo> localSuggestions = dictionary.getWords(
                        wordComposerForLookup, prevWordForBigram, proximityInfo);
                for (final SuggestedWordInfo suggestion : localSuggestions) {
                    addWord(suggestion, key, suggestionsContainer, consideredWord);
                }
            }
        }

        final CharSequence whitelistedWord = capitalizeWord(mIsAllUpperCase,
                mIsFirstCharCapitalized, mWhiteListDictionary.getWhitelistedWord(consideredWord));

        final boolean hasAutoCorrection;
        if (isCorrectionEnabled) {
            final CharSequence autoCorrection =
                    AutoCorrection.computeAutoCorrectionWord(mDictionaries, wordComposer,
                            suggestionsContainer, consideredWord, mAutoCorrectionThreshold,
                            whitelistedWord);
            hasAutoCorrection = (null != autoCorrection);
        } else {
            hasAutoCorrection = false;
        }

        if (whitelistedWord != null) {
            if (mTrailingSingleQuotesCount > 0) {
                final StringBuilder sb = new StringBuilder(whitelistedWord);
                for (int i = mTrailingSingleQuotesCount - 1; i >= 0; --i) {
                    sb.appendCodePoint(Keyboard.CODE_SINGLE_QUOTE);
                }
                suggestionsContainer.add(0, new SuggestedWordInfo(sb.toString(),
                        SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_WHITELIST));
            } else {
                suggestionsContainer.add(0, new SuggestedWordInfo(whitelistedWord,
                        SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_WHITELIST));
            }
        }

        if (!isPrediction) {
            suggestionsContainer.add(0, new SuggestedWordInfo(typedWord,
                    SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED));
        }
        SuggestedWordInfo.removeDups(suggestionsContainer);

        final ArrayList<SuggestedWordInfo> suggestionsList;
        if (DBG && !suggestionsContainer.isEmpty()) {
            suggestionsList = getSuggestionsInfoListWithDebugInfo(typedWord, suggestionsContainer);
        } else {
            suggestionsList = suggestionsContainer;
        }

        // TODO: Change this scheme - a boolean is not enough. A whitelisted word may be "valid"
        // but still autocorrected from - in the case the whitelist only capitalizes the word.
        // The whitelist should be case-insensitive, so it's not possible to be consistent with
        // a boolean flag. Right now this is handled with a slight hack in
        // WhitelistDictionary#shouldForciblyAutoCorrectFrom.
        final boolean allowsToBeAutoCorrected = AutoCorrection.allowsToBeAutoCorrected(
                getUnigramDictionaries(), consideredWord, wordComposer.isFirstCharCapitalized())
        // If we don't have a main dictionary, we never want to auto-correct. The reason for this
        // is, the user may have a contact whose name happens to match a valid word in their
        // language, and it will unexpectedly auto-correct. For example, if the user types in
        // English with no dictionary and has a "Will" in their contact list, "will" would
        // always auto-correct to "Will" which is unwanted. Hence, no main dict => no auto-correct.
                && hasMainDictionary();

        boolean autoCorrectionAvailable = hasAutoCorrection;
        if (isCorrectionEnabled) {
            autoCorrectionAvailable |= !allowsToBeAutoCorrected;
        }
        // Don't auto-correct words with multiple capital letter
        autoCorrectionAvailable &= !wordComposer.isMostlyCaps();
        autoCorrectionAvailable &= !wordComposer.isResumed();
        if (allowsToBeAutoCorrected && suggestionsList.size() > 1 && mAutoCorrectionThreshold > 0
                && Suggest.shouldBlockAutoCorrectionBySafetyNet(typedWord,
                        suggestionsList.get(1).mWord)) {
            autoCorrectionAvailable = false;
        }
        return new SuggestedWords(suggestionsList,
                !isPrediction && !allowsToBeAutoCorrected /* typedWordValid */,
                !isPrediction && autoCorrectionAvailable /* hasAutoCorrectionCandidate */,
                !isPrediction && allowsToBeAutoCorrected /* allowsToBeAutoCorrected */,
                false /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */,
                isPrediction);
    }

    private static ArrayList<SuggestedWordInfo> getSuggestionsInfoListWithDebugInfo(
            final String typedWord, final ArrayList<SuggestedWordInfo> suggestions) {
        final SuggestedWordInfo typedWordInfo = suggestions.get(0);
        typedWordInfo.setDebugString("+");
        final int suggestionsSize = suggestions.size();
        final ArrayList<SuggestedWordInfo> suggestionsList =
                new ArrayList<SuggestedWordInfo>(suggestionsSize);
        suggestionsList.add(typedWordInfo);
        // Note: i here is the index in mScores[], but the index in mSuggestions is one more
        // than i because we added the typed word to mSuggestions without touching mScores.
        for (int i = 0; i < suggestionsSize - 1; ++i) {
            final SuggestedWordInfo cur = suggestions.get(i + 1);
            final float normalizedScore = BinaryDictionary.calcNormalizedScore(
                    typedWord, cur.toString(), cur.mScore);
            final String scoreInfoString;
            if (normalizedScore > 0) {
                scoreInfoString = String.format("%d (%4.2f)", cur.mScore, normalizedScore);
            } else {
                scoreInfoString = Integer.toString(cur.mScore);
            }
            cur.setDebugString(scoreInfoString);
            suggestionsList.add(cur);
        }
        return suggestionsList;
    }

    private static class SuggestedWordInfoComparator implements Comparator<SuggestedWordInfo> {
        // This comparator ranks the word info with the higher frequency first. That's because
        // that's the order we want our elements in.
        @Override
        public int compare(final SuggestedWordInfo o1, final SuggestedWordInfo o2) {
            if (o1.mScore > o2.mScore) return -1;
            if (o1.mScore < o2.mScore) return 1;
            if (o1.mCodePointCount < o2.mCodePointCount) return -1;
            if (o1.mCodePointCount > o2.mCodePointCount) return 1;
            return o1.mWord.toString().compareTo(o2.mWord.toString());
        }
    }
    private static final SuggestedWordInfoComparator sSuggestedWordInfoComparator =
            new SuggestedWordInfoComparator();

    public boolean addWord(final SuggestedWordInfo wordInfo, final String dictTypeKey,
            final ArrayList<SuggestedWordInfo> suggestions, final String consideredWord) {
        final int prefMaxSuggestions = MAX_SUGGESTIONS;

        final CharSequence word = wordInfo.mWord;
        final int score = wordInfo.mScore;
        int pos = 0;

        final int index =
                Collections.binarySearch(suggestions, wordInfo, sSuggestedWordInfoComparator);
        // binarySearch returns the index of an equal word info if found. If not found
        // it returns -insertionPoint - 1. We want the insertion point, so:
        pos = index >= 0 ? index : -index - 1;
        if (pos >= prefMaxSuggestions) {
            return true;
        }

        final SuggestedWordInfo transformedWordInfo = getTransformedSuggestedWordInfo(wordInfo,
                mLocale, mIsAllUpperCase, mIsFirstCharCapitalized, mTrailingSingleQuotesCount);
        suggestions.add(pos, transformedWordInfo);
        if (suggestions.size() > prefMaxSuggestions) {
            suggestions.remove(prefMaxSuggestions);
        }
        LatinImeLogger.onAddSuggestedWord(transformedWordInfo.mWord.toString(), dictTypeKey);
        return true;
    }

    private static SuggestedWordInfo getTransformedSuggestedWordInfo(
            final SuggestedWordInfo wordInfo, final Locale locale, final boolean isAllUpperCase,
            final boolean isFirstCharCapitalized, final int trailingSingleQuotesCount) {
        if (!isFirstCharCapitalized && !isAllUpperCase && 0 == trailingSingleQuotesCount) {
            return wordInfo;
        }
        final StringBuilder sb = new StringBuilder(getApproxMaxWordLength());
        if (isAllUpperCase) {
            sb.append(wordInfo.mWord.toString().toUpperCase(locale));
        } else if (isFirstCharCapitalized) {
            sb.append(StringUtils.toTitleCase(wordInfo.mWord.toString(), locale));
        } else {
            sb.append(wordInfo.mWord);
        }
        for (int i = trailingSingleQuotesCount - 1; i >= 0; --i) {
            sb.appendCodePoint(Keyboard.CODE_SINGLE_QUOTE);
        }
        return new SuggestedWordInfo(sb, wordInfo.mScore, wordInfo.mKind);
    }

    public void close() {
        final HashSet<Dictionary> dictionaries = new HashSet<Dictionary>();
        dictionaries.addAll(mDictionaries.values());
        for (final Dictionary dictionary : dictionaries) {
            dictionary.close();
        }
        mMainDictionary = null;
    }

    // TODO: Resolve the inconsistencies between the native auto correction algorithms and
    // this safety net
    public static boolean shouldBlockAutoCorrectionBySafetyNet(final String typedWord,
            final CharSequence suggestion) {
        // Safety net for auto correction.
        // Actually if we hit this safety net, it's a bug.
        // If user selected aggressive auto correction mode, there is no need to use the safety
        // net.
        // If the length of typed word is less than MINIMUM_SAFETY_NET_CHAR_LENGTH,
        // we should not use net because relatively edit distance can be big.
        final int typedWordLength = typedWord.length();
        if (typedWordLength < Suggest.MINIMUM_SAFETY_NET_CHAR_LENGTH) {
            return false;
        }
        final int maxEditDistanceOfNativeDictionary =
                (typedWordLength < 5 ? 2 : typedWordLength / 2) + 1;
        final int distance = BinaryDictionary.editDistance(typedWord, suggestion.toString());
        if (DBG) {
            Log.d(TAG, "Autocorrected edit distance = " + distance
                    + ", " + maxEditDistanceOfNativeDictionary);
        }
        if (distance > maxEditDistanceOfNativeDictionary) {
            if (DBG) {
                Log.e(TAG, "Safety net: before = " + typedWord + ", after = " + suggestion);
                Log.e(TAG, "(Error) The edit distance of this correction exceeds limit. "
                        + "Turning off auto-correction.");
            }
            return true;
        } else {
            return false;
        }
    }
}
