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

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
public class Suggest implements Dictionary.WordCallback {
    public static final String TAG = Suggest.class.getSimpleName();

    public static final int APPROX_MAX_WORD_LENGTH = 32;

    public static final int CORRECTION_NONE = 0;
    public static final int CORRECTION_FULL = 1;
    public static final int CORRECTION_FULL_BIGRAM = 2;

    /**
     * Words that appear in both bigram and unigram data gets multiplier ranging from
     * BIGRAM_MULTIPLIER_MIN to BIGRAM_MULTIPLIER_MAX depending on the score from
     * bigram data.
     */
    public static final double BIGRAM_MULTIPLIER_MIN = 1.2;
    public static final double BIGRAM_MULTIPLIER_MAX = 1.5;

    /**
     * Maximum possible bigram frequency. Will depend on how many bits are being used in data
     * structure. Maximum bigram frequency will get the BIGRAM_MULTIPLIER_MAX as the multiplier.
     */
    public static final int MAXIMUM_BIGRAM_FREQUENCY = 127;

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

    private static final boolean DBG = LatinImeLogger.sDBG;

    private Dictionary mMainDict;
    private Dictionary mContactsDict;
    private WhitelistDictionary mWhiteListDictionary;
    private final HashMap<String, Dictionary> mUnigramDictionaries =
            new HashMap<String, Dictionary>();
    private final HashMap<String, Dictionary> mBigramDictionaries =
            new HashMap<String, Dictionary>();

    private int mPrefMaxSuggestions = 18;

    private static final int PREF_MAX_BIGRAMS = 60;

    private double mAutoCorrectionThreshold;

    private ArrayList<SuggestedWordInfo> mSuggestions = new ArrayList<SuggestedWordInfo>();
    private ArrayList<SuggestedWordInfo> mBigramSuggestions = new ArrayList<SuggestedWordInfo>();
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
        initSynchronously(context, DictionaryFactory.createDictionaryForTest(context, dictionary,
                startOffset, length /* useFullEditDistance */, false, locale), locale);
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

    private void initSynchronously(final Context context, final Dictionary mainDict,
            final Locale locale) {
        mMainDict = mainDict;
        addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_MAIN, mainDict);
        addOrReplaceDictionary(mBigramDictionaries, DICT_KEY_MAIN, mainDict);
        initWhitelistAndAutocorrectAndPool(context, locale);
    }

    private static void addOrReplaceDictionary(HashMap<String, Dictionary> dictionaries, String key,
            Dictionary dict) {
        final Dictionary oldDict = (dict == null)
                ? dictionaries.remove(key)
                : dictionaries.put(key, dict);
        if (oldDict != null && dict != oldDict) {
            oldDict.close();
        }
    }

    public void resetMainDict(final Context context, final Locale locale) {
        mMainDict = null;
        new Thread("InitializeBinaryDictionary") {
            @Override
            public void run() {
                final Dictionary newMainDict = DictionaryFactory.createDictionaryFromManager(
                        context, locale);
                mMainDict = newMainDict;
                addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_MAIN, newMainDict);
                addOrReplaceDictionary(mBigramDictionaries, DICT_KEY_MAIN, newMainDict);
            }
        }.start();
    }

    // The main dictionary could have been loaded asynchronously.  Don't cache the return value
    // of this method.
    public boolean hasMainDictionary() {
        return mMainDict != null;
    }

    public Dictionary getContactsDictionary() {
        return mContactsDict;
    }

    public HashMap<String, Dictionary> getUnigramDictionaries() {
        return mUnigramDictionaries;
    }

    public static int getApproxMaxWordLength() {
        return APPROX_MAX_WORD_LENGTH;
    }

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set. This refers to the system-managed user dictionary.
     */
    public void setUserDictionary(Dictionary userDictionary) {
        addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_USER, userDictionary);
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded. It is also possible to remove
     * the contacts dictionary by passing null to this method. In this case no contacts dictionary
     * won't be used.
     */
    public void setContactsDictionary(Dictionary contactsDictionary) {
        mContactsDict = contactsDictionary;
        addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_CONTACTS, contactsDictionary);
        addOrReplaceDictionary(mBigramDictionaries, DICT_KEY_CONTACTS, contactsDictionary);
    }

    public void setUserHistoryDictionary(Dictionary userHistoryDictionary) {
        addOrReplaceDictionary(mUnigramDictionaries, DICT_KEY_USER_HISTORY_UNIGRAM,
                userHistoryDictionary);
        addOrReplaceDictionary(mBigramDictionaries, DICT_KEY_USER_HISTORY_BIGRAM,
                userHistoryDictionary);
    }

    public void setAutoCorrectionThreshold(double threshold) {
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

    protected void addBigramToSuggestions(SuggestedWordInfo bigram) {
        mSuggestions.add(bigram);
    }

    private static final WordComposer sEmptyWordComposer = new WordComposer();
    public SuggestedWords getBigramPredictions(CharSequence prevWordForBigram) {
        LatinImeLogger.onStartSuggestion(prevWordForBigram);
        mIsFirstCharCapitalized = false;
        mIsAllUpperCase = false;
        mTrailingSingleQuotesCount = 0;
        mSuggestions = new ArrayList<SuggestedWordInfo>(mPrefMaxSuggestions);

        // Treating USER_TYPED as UNIGRAM suggestion for logging now.
        LatinImeLogger.onAddSuggestedWord("", Suggest.DIC_USER_TYPED, Dictionary.UNIGRAM);
        mConsideredWord = "";

        mBigramSuggestions = new ArrayList<SuggestedWordInfo>(PREF_MAX_BIGRAMS);

        CharSequence lowerPrevWord = prevWordForBigram.toString().toLowerCase();
        if (mMainDict != null && mMainDict.isValidWord(lowerPrevWord)) {
            prevWordForBigram = lowerPrevWord;
        }
        for (final Dictionary dictionary : mBigramDictionaries.values()) {
            dictionary.getBigrams(sEmptyWordComposer, prevWordForBigram, this);
        }
        // Nothing entered: return all bigrams for the previous word
        int insertCount = Math.min(mBigramSuggestions.size(), mPrefMaxSuggestions);
        for (int i = 0; i < insertCount; ++i) {
            addBigramToSuggestions(mBigramSuggestions.get(i));
        }

        SuggestedWordInfo.removeDups(mSuggestions);

        return new SuggestedWords(mSuggestions,
                // TODO: Just assuming the suggestions that came from the bigram prediction are
                // valid now. Need to assign a correct value for typedWordValid.
                true /* typedWordValid */,
                false /* hasAutoCorrectionCandidate */,
                false /* allowsToBeAutoCorrected */,
                false /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */);
    }

    // TODO: cleanup dictionaries looking up and suggestions building with SuggestedWords.Builder
    public SuggestedWords getSuggestedWords(
            final WordComposer wordComposer, CharSequence prevWordForBigram,
            final ProximityInfo proximityInfo, final int correctionMode) {
        LatinImeLogger.onStartSuggestion(prevWordForBigram);
        mIsFirstCharCapitalized = wordComposer.isFirstCharCapitalized();
        mIsAllUpperCase = wordComposer.isAllUpperCase();
        mTrailingSingleQuotesCount = wordComposer.trailingSingleQuotesCount();
        mSuggestions = new ArrayList<SuggestedWordInfo>(mPrefMaxSuggestions);

        final String typedWord = wordComposer.getTypedWord();
        final String consideredWord = mTrailingSingleQuotesCount > 0
                ? typedWord.substring(0, typedWord.length() - mTrailingSingleQuotesCount)
                : typedWord;
        // Treating USER_TYPED as UNIGRAM suggestion for logging now.
        LatinImeLogger.onAddSuggestedWord(typedWord, Suggest.DIC_USER_TYPED, Dictionary.UNIGRAM);
        mConsideredWord = consideredWord;

        if (wordComposer.size() <= 1 && (correctionMode == CORRECTION_FULL_BIGRAM)) {
            // At first character typed, search only the bigrams
            mBigramSuggestions = new ArrayList<SuggestedWordInfo>(PREF_MAX_BIGRAMS);

            if (!TextUtils.isEmpty(prevWordForBigram)) {
                CharSequence lowerPrevWord = prevWordForBigram.toString().toLowerCase();
                if (mMainDict != null && mMainDict.isValidWord(lowerPrevWord)) {
                    prevWordForBigram = lowerPrevWord;
                }
                for (final Dictionary dictionary : mBigramDictionaries.values()) {
                    dictionary.getBigrams(wordComposer, prevWordForBigram, this);
                }
                if (TextUtils.isEmpty(consideredWord)) {
                    // Nothing entered: return all bigrams for the previous word
                    int insertCount = Math.min(mBigramSuggestions.size(), mPrefMaxSuggestions);
                    for (int i = 0; i < insertCount; ++i) {
                        addBigramToSuggestions(mBigramSuggestions.get(i));
                    }
                } else {
                    // Word entered: return only bigrams that match the first char of the typed word
                    final char currentChar = consideredWord.charAt(0);
                    // TODO: Must pay attention to locale when changing case.
                    // TODO: Use codepoint instead of char
                    final char currentCharUpper = Character.toUpperCase(currentChar);
                    int count = 0;
                    final int bigramSuggestionSize = mBigramSuggestions.size();
                    for (int i = 0; i < bigramSuggestionSize; i++) {
                        final SuggestedWordInfo bigramSuggestion = mBigramSuggestions.get(i);
                        final char bigramSuggestionFirstChar =
                                (char)bigramSuggestion.codePointAt(0);
                        if (bigramSuggestionFirstChar == currentChar
                                || bigramSuggestionFirstChar == currentCharUpper) {
                            addBigramToSuggestions(bigramSuggestion);
                            if (++count > mPrefMaxSuggestions) break;
                        }
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
                final Dictionary dictionary = mUnigramDictionaries.get(key);
                dictionary.getWords(wordComposerForLookup, this, proximityInfo);
            }
        }

        final CharSequence whitelistedWord = capitalizeWord(mIsAllUpperCase,
                mIsFirstCharCapitalized, mWhiteListDictionary.getWhitelistedWord(consideredWord));

        final boolean hasAutoCorrection;
        if (CORRECTION_FULL == correctionMode || CORRECTION_FULL_BIGRAM == correctionMode) {
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
                mSuggestions.add(0, new SuggestedWordInfo(
                        sb.toString(), SuggestedWordInfo.MAX_SCORE));
            } else {
                mSuggestions.add(0, new SuggestedWordInfo(
                        whitelistedWord, SuggestedWordInfo.MAX_SCORE));
            }
        }

        mSuggestions.add(0, new SuggestedWordInfo(typedWord, SuggestedWordInfo.MAX_SCORE));
        SuggestedWordInfo.removeDups(mSuggestions);

        final ArrayList<SuggestedWordInfo> suggestionsList;
        if (DBG) {
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
                getUnigramDictionaries(), consideredWord, wordComposer.isFirstCharCapitalized());

        boolean autoCorrectionAvailable = hasAutoCorrection;
        if (correctionMode == CORRECTION_FULL || correctionMode == CORRECTION_FULL_BIGRAM) {
            autoCorrectionAvailable |= !allowsToBeAutoCorrected;
        }
        // Don't auto-correct words with multiple capital letter
        autoCorrectionAvailable &= !wordComposer.isMostlyCaps();
        if (allowsToBeAutoCorrected && suggestionsList.size() > 1 && mAutoCorrectionThreshold > 0
                && Suggest.shouldBlockAutoCorrectionBySafetyNet(typedWord,
                        suggestionsList.get(1).mWord)) {
            autoCorrectionAvailable = false;
        }
        return new SuggestedWords(suggestionsList,
                !allowsToBeAutoCorrected /* typedWordValid */,
                autoCorrectionAvailable /* hasAutoCorrectionCandidate */,
                allowsToBeAutoCorrected /* allowsToBeAutoCorrected */,
                false /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */);
    }

    private static ArrayList<SuggestedWordInfo> getSuggestionsInfoListWithDebugInfo(
            final String typedWord, final ArrayList<SuggestedWordInfo> suggestions) {
        final SuggestedWordInfo typedWordInfo = suggestions.get(0);
        typedWordInfo.setDebugString("+");
        double normalizedScore = BinaryDictionary.calcNormalizedScore(
                typedWord, typedWordInfo.toString(), typedWordInfo.mScore);
        final int suggestionsSize = suggestions.size();
        final ArrayList<SuggestedWordInfo> suggestionsList =
                new ArrayList<SuggestedWordInfo>(suggestionsSize);
        suggestionsList.add(typedWordInfo);
        // Note: i here is the index in mScores[], but the index in mSuggestions is one more
        // than i because we added the typed word to mSuggestions without touching mScores.
        for (int i = 0; i < suggestionsSize - 1; ++i) {
            final SuggestedWordInfo cur = suggestions.get(i + 1);
            final String scoreInfoString;
            if (normalizedScore > 0) {
                scoreInfoString = String.format("%d (%4.2f)", cur.mScore, normalizedScore);
                normalizedScore = 0.0;
            } else {
                scoreInfoString = Integer.toString(cur.mScore);
            }
            cur.setDebugString(scoreInfoString);
            suggestionsList.add(cur);
        }
        return suggestionsList;
    }

    // TODO: Use codepoint instead of char
    @Override
    public boolean addWord(final char[] word, final int offset, final int length, int score,
            final int dicTypeId, final int dataType) {
        int dataTypeForLog = dataType;
        final ArrayList<SuggestedWordInfo> suggestions;
        final int prefMaxSuggestions;
        if (dataType == Dictionary.BIGRAM) {
            suggestions = mBigramSuggestions;
            prefMaxSuggestions = PREF_MAX_BIGRAMS;
        } else {
            suggestions = mSuggestions;
            prefMaxSuggestions = mPrefMaxSuggestions;
        }

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
            if (dataType == Dictionary.UNIGRAM) {
                // Check if the word was already added before (by bigram data)
                int bigramSuggestion = searchBigramSuggestion(word,offset,length);
                if(bigramSuggestion >= 0) {
                    dataTypeForLog = Dictionary.BIGRAM;
                    // turn freq from bigram into multiplier specified above
                    double multiplier = (((double) mBigramSuggestions.get(bigramSuggestion).mScore)
                            / MAXIMUM_BIGRAM_FREQUENCY)
                            * (BIGRAM_MULTIPLIER_MAX - BIGRAM_MULTIPLIER_MIN)
                            + BIGRAM_MULTIPLIER_MIN;
                    /* Log.d(TAG,"bigram num: " + bigramSuggestion
                            + "  wordB: " + mBigramSuggestions.get(bigramSuggestion).toString()
                            + "  currentScore: " + score + "  bigramScore: "
                            + mBigramScores[bigramSuggestion]
                            + "  multiplier: " + multiplier); */
                    score = (int)Math.round((score * multiplier));
                }
            }

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
        suggestions.add(pos, new SuggestedWordInfo(sb, score));
        if (suggestions.size() > prefMaxSuggestions) {
            suggestions.remove(prefMaxSuggestions);
        } else {
            LatinImeLogger.onAddSuggestedWord(sb.toString(), dicTypeId, dataTypeForLog);
        }
        return true;
    }

    // TODO: Use codepoint instead of char
    private int searchBigramSuggestion(final char[] word, final int offset, final int length) {
        // TODO This is almost O(n^2). Might need fix.
        // search whether the word appeared in bigram data
        int bigramSuggestSize = mBigramSuggestions.size();
        for (int i = 0; i < bigramSuggestSize; i++) {
            if (mBigramSuggestions.get(i).codePointCount() == length) {
                boolean chk = true;
                for (int j = 0; j < length; j++) {
                    if (mBigramSuggestions.get(i).codePointAt(j) != word[offset+j]) {
                        chk = false;
                        break;
                    }
                }
                if (chk) return i;
            }
        }

        return -1;
    }

    public void close() {
        final HashSet<Dictionary> dictionaries = new HashSet<Dictionary>();
        dictionaries.addAll(mUnigramDictionaries.values());
        dictionaries.addAll(mBigramDictionaries.values());
        for (final Dictionary dictionary : dictionaries) {
            dictionary.close();
        }
        mMainDict = null;
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
