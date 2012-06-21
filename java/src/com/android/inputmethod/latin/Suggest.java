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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
public class Suggest implements Dictionary.WordCallback {
    public static final String TAG = Suggest.class.getSimpleName();

    public static final int APPROX_MAX_WORD_LENGTH = 32;

    // TODO: rename this to CORRECTION_OFF
    public static final int CORRECTION_NONE = 0;
    // TODO: rename this to CORRECTION_ON
    public static final int CORRECTION_FULL = 1;

    // It seems the following values are only used for logging.
    public static final int DIC_USER_TYPED = 0;
    public static final int DIC_MAIN = 1;
    public static final int DIC_USER = 2;
    public static final int DIC_USER_HISTORY = 3;
    public static final int DIC_CONTACTS = 4;
    public static final int DIC_WHITELIST = 6;
    // If you add a type of dictionary, increment DIC_TYPE_LAST_ID
    // TODO: this value seems unused. Remove it?
    public static final int DIC_TYPE_LAST_ID = 6;
    public static final String DICT_KEY_MAIN = "main";
    public static final String DICT_KEY_CONTACTS = "contacts";
    // User dictionary, the system-managed one.
    public static final String DICT_KEY_USER = "user";
    // User history dictionary for the unigram map, internal to LatinIME
    public static final String DICT_KEY_USER_HISTORY_UNIGRAM = "history_unigram";
    // User history dictionary for the bigram map, internal to LatinIME
    public static final String DICT_KEY_USER_HISTORY_BIGRAM = "history_bigram";
    public static final String DICT_KEY_WHITELIST ="whitelist";
    // TODO: remove this map. This only serves as backward compatibility with a feature
    // that has never been used and has been broken for a while.
    private static final HashMap<String, Integer> sDictKeyToDictIndex
            = new HashMap<String, Integer>();
    static {
        sDictKeyToDictIndex.put(DICT_KEY_MAIN, DIC_MAIN);
        sDictKeyToDictIndex.put(DICT_KEY_USER, DIC_USER);
        sDictKeyToDictIndex.put(DICT_KEY_USER_HISTORY_UNIGRAM, DIC_USER_HISTORY);
        sDictKeyToDictIndex.put(DICT_KEY_USER_HISTORY_BIGRAM, DIC_USER_HISTORY);
        sDictKeyToDictIndex.put(DICT_KEY_CONTACTS, DIC_CONTACTS);
        sDictKeyToDictIndex.put(DICT_KEY_WHITELIST, DIC_WHITELIST);
    }

    private static final boolean DBG = LatinImeLogger.sDBG;

    private Dictionary mMainDictionary;
    private ContactsBinaryDictionary mContactsDict;
    private WhitelistDictionary mWhiteListDictionary;
    private final ConcurrentHashMap<String, Dictionary> mUnigramDictionaries =
            new ConcurrentHashMap<String, Dictionary>();
    private final ConcurrentHashMap<String, Dictionary> mBigramDictionaries =
            new ConcurrentHashMap<String, Dictionary>();

    public static final int MAX_SUGGESTIONS = 18;

    private float mAutoCorrectionThreshold;

    private ArrayList<SuggestedWordInfo> mSuggestions = new ArrayList<SuggestedWordInfo>();
    private CharSequence mConsideredWord;

    // TODO: Remove these member variables by passing more context to addWord() callback method
    private boolean mIsFirstCharCapitalized;
    private boolean mIsAllUpperCase;
    private int mTrailingSingleQuotesCount;

    private static final int MINIMUM_SAFETY_NET_CHAR_LENGTH = 4;

    public Suggest(final Context context, final Locale locale) {
        initAsynchronously(context, locale);
    }

    /* package for test */ Suggest(final Context context, final File dictionary,
            final long startOffset, final long length, final Locale locale) {
        final Dictionary mainDict = DictionaryFactory.createDictionaryForTest(context, dictionary,
                startOffset, length /* useFullEditDistance */, false, locale);
        mMainDictionary = mainDict;
        addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_MAIN, mainDict);
        addOrReplaceDictionary(mBigramDictionaries, DICT_KEY_MAIN, mainDict);
        initWhitelistAndAutocorrectAndPool(context, locale);
    }

    private void initWhitelistAndAutocorrectAndPool(final Context context, final Locale locale) {
        mWhiteListDictionary = new WhitelistDictionary(context, locale);
        addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_WHITELIST, mWhiteListDictionary);
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
                addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_MAIN, newMainDict);
                addOrReplaceDictionary(mBigramDictionaries, DICT_KEY_MAIN, newMainDict);
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
        return mUnigramDictionaries;
    }

    public static int getApproxMaxWordLength() {
        return APPROX_MAX_WORD_LENGTH;
    }

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set. This refers to the system-managed user dictionary.
     */
    public void setUserDictionary(UserBinaryDictionary userDictionary) {
        addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_USER, userDictionary);
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded. It is also possible to remove
     * the contacts dictionary by passing null to this method. In this case no contacts dictionary
     * won't be used.
     */
    public void setContactsDictionary(ContactsBinaryDictionary contactsDictionary) {
        mContactsDict = contactsDictionary;
        addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_CONTACTS, contactsDictionary);
        addOrReplaceDictionary(mBigramDictionaries, DICT_KEY_CONTACTS, contactsDictionary);
    }

    public void setUserHistoryDictionary(UserHistoryDictionary userHistoryDictionary) {
        addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_USER_HISTORY_UNIGRAM,
                userHistoryDictionary);
        addOrReplaceDictionary(mBigramDictionaries, DICT_KEY_USER_HISTORY_BIGRAM,
                userHistoryDictionary);
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

    // Compatibility for tests. TODO: remove this
    public SuggestedWords getSuggestedWords(
            final WordComposer wordComposer, CharSequence prevWordForBigram,
            final ProximityInfo proximityInfo, final boolean isCorrectionEnabled) {
        return getSuggestedWords(wordComposer, prevWordForBigram, proximityInfo,
                isCorrectionEnabled, false);
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
        mSuggestions = new ArrayList<SuggestedWordInfo>(MAX_SUGGESTIONS);

        final String typedWord = wordComposer.getTypedWord();
        final String consideredWord = mTrailingSingleQuotesCount > 0
                ? typedWord.substring(0, typedWord.length() - mTrailingSingleQuotesCount)
                : typedWord;
        // Treating USER_TYPED as UNIGRAM suggestion for logging now.
        LatinImeLogger.onAddSuggestedWord(typedWord, Suggest.DIC_USER_TYPED, Dictionary.UNIGRAM);
        mConsideredWord = consideredWord;

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
                for (final String key : mBigramDictionaries.keySet()) {
                    final int dicTypeId = sDictKeyToDictIndex.get(key);
                    final Dictionary dictionary = mBigramDictionaries.get(key);
                    final ArrayList<SuggestedWordInfo> suggestions =
                            dictionary.getBigrams(wordComposer, prevWordForBigram);
                    if (null != lowerPrevWord) {
                        suggestions.addAll(dictionary.getBigrams(wordComposer, lowerPrevWord));
                    }
                    for (final SuggestedWordInfo suggestion : suggestions) {
                        final String suggestionStr = suggestion.mWord.toString();
                        oldAddWord(suggestionStr.toCharArray(), null, 0, suggestionStr.length(),
                                suggestion.mScore, dicTypeId, Dictionary.BIGRAM);
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
            for (final String key : mUnigramDictionaries.keySet()) {
                // Skip UserUnigramDictionary and WhitelistDictionary to lookup
                if (key.equals(DICT_KEY_USER_HISTORY_UNIGRAM) || key.equals(DICT_KEY_WHITELIST))
                    continue;
                final int dicTypeId = sDictKeyToDictIndex.get(key);
                final Dictionary dictionary = mUnigramDictionaries.get(key);
                final ArrayList<SuggestedWordInfo> suggestions = dictionary.getWords(
                        wordComposerForLookup, prevWordForBigram, proximityInfo);
                for (final SuggestedWordInfo suggestion : suggestions) {
                    final String suggestionStr = suggestion.mWord.toString();
                    oldAddWord(suggestionStr.toCharArray(), null, 0, suggestionStr.length(),
                            suggestion.mScore, dicTypeId, Dictionary.UNIGRAM);
                }
            }
        }

        final CharSequence whitelistedWord = capitalizeWord(mIsAllUpperCase,
                mIsFirstCharCapitalized, mWhiteListDictionary.getWhitelistedWord(consideredWord));

        final boolean hasAutoCorrection;
        if (isCorrectionEnabled) {
            final CharSequence autoCorrection =
                    AutoCorrection.computeAutoCorrectionWord(mUnigramDictionaries, wordComposer,
                            mSuggestions, consideredWord, mAutoCorrectionThreshold,
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
                mSuggestions.add(0, new SuggestedWordInfo(sb.toString(),
                        SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_WHITELIST));
            } else {
                mSuggestions.add(0, new SuggestedWordInfo(whitelistedWord,
                        SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_WHITELIST));
            }
        }

        if (!isPrediction) {
            mSuggestions.add(0, new SuggestedWordInfo(typedWord, SuggestedWordInfo.MAX_SCORE,
                    SuggestedWordInfo.KIND_TYPED));
        }
        SuggestedWordInfo.removeDups(mSuggestions);

        final ArrayList<SuggestedWordInfo> suggestionsList;
        if (DBG && !mSuggestions.isEmpty()) {
            suggestionsList = getSuggestionsInfoListWithDebugInfo(typedWord, mSuggestions);
        } else {
            suggestionsList = mSuggestions;
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

    // TODO: Remove this method
    @Override
    public boolean addWord(final char[] word, int[] indices, final int offset, final int length,
            int score, final int dicTypeId, final int dataType) {
        return true;
    }

    // TODO: Use codepoint instead of char
    public boolean oldAddWord(final char[] word, int[] indices, final int offset, final int length,
            int score, final int dicTypeId, final int dataType) {
        int dataTypeForLog = dataType;
        final ArrayList<SuggestedWordInfo> suggestions;
        final int prefMaxSuggestions;
        suggestions = mSuggestions;
        prefMaxSuggestions = MAX_SUGGESTIONS;

        int pos = 0;

        // Check if it's the same word, only caps are different
        if (StringUtils.equalsIgnoreCase(mConsideredWord, word, offset, length)) {
            // TODO: remove this surrounding if clause and move this logic to
            // getSuggestedWordBuilder.
            if (suggestions.size() > 0) {
                final SuggestedWordInfo currentHighestWord = suggestions.get(0);
                // If the current highest word is also equal to typed word, we need to compare
                // frequency to determine the insertion position. This does not ensure strictly
                // correct ordering, but ensures the top score is on top which is enough for
                // removing duplicates correctly.
                if (StringUtils.equalsIgnoreCase(currentHighestWord.mWord, word, offset, length)
                        && score <= currentHighestWord.mScore) {
                    pos = 1;
                }
            }
        } else {
            // Check the last one's score and bail
            if (suggestions.size() >= prefMaxSuggestions
                    && suggestions.get(prefMaxSuggestions - 1).mScore >= score) return true;
            while (pos < suggestions.size()) {
                final int curScore = suggestions.get(pos).mScore;
                if (curScore < score
                        || (curScore == score && length < suggestions.get(pos).codePointCount())) {
                    break;
                }
                pos++;
            }
        }
        if (pos >= prefMaxSuggestions) {
            return true;
        }

        final StringBuilder sb = new StringBuilder(getApproxMaxWordLength());
        // TODO: Must pay attention to locale when changing case.
        if (mIsAllUpperCase) {
            sb.append(new String(word, offset, length).toUpperCase());
        } else if (mIsFirstCharCapitalized) {
            sb.append(Character.toUpperCase(word[offset]));
            if (length > 1) {
                sb.append(word, offset + 1, length - 1);
            }
        } else {
            sb.append(word, offset, length);
        }
        for (int i = mTrailingSingleQuotesCount - 1; i >= 0; --i) {
            sb.appendCodePoint(Keyboard.CODE_SINGLE_QUOTE);
        }
        // TODO: figure out what type of suggestion this is
        suggestions.add(pos, new SuggestedWordInfo(sb, score, SuggestedWordInfo.KIND_CORRECTION));
        if (suggestions.size() > prefMaxSuggestions) {
            suggestions.remove(prefMaxSuggestions);
        } else {
            LatinImeLogger.onAddSuggestedWord(sb.toString(), dicTypeId, dataTypeForLog);
        }
        return true;
    }

    public void close() {
        final HashSet<Dictionary> dictionaries = new HashSet<Dictionary>();
        dictionaries.addAll(mUnigramDictionaries.values());
        dictionaries.addAll(mBigramDictionaries.values());
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
