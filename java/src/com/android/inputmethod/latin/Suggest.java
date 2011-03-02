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
import android.text.AutoText;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
public class Suggest implements Dictionary.WordCallback {

    public static final String TAG = Suggest.class.getSimpleName();

    public static final int APPROX_MAX_WORD_LENGTH = 32;

    public static final int CORRECTION_NONE = 0;
    public static final int CORRECTION_BASIC = 1;
    public static final int CORRECTION_FULL = 2;
    public static final int CORRECTION_FULL_BIGRAM = 3;

    /**
     * Words that appear in both bigram and unigram data gets multiplier ranging from
     * BIGRAM_MULTIPLIER_MIN to BIGRAM_MULTIPLIER_MAX depending on the frequency score from
     * bigram data.
     */
    public static final double BIGRAM_MULTIPLIER_MIN = 1.2;
    public static final double BIGRAM_MULTIPLIER_MAX = 1.5;

    /**
     * Maximum possible bigram frequency. Will depend on how many bits are being used in data
     * structure. Maximum bigram freqeuncy will get the BIGRAM_MULTIPLIER_MAX as the multiplier.
     */
    public static final int MAXIMUM_BIGRAM_FREQUENCY = 127;

    public static final int DIC_USER_TYPED = 0;
    public static final int DIC_MAIN = 1;
    public static final int DIC_USER = 2;
    public static final int DIC_AUTO = 3;
    public static final int DIC_CONTACTS = 4;
    // If you add a type of dictionary, increment DIC_TYPE_LAST_ID
    public static final int DIC_TYPE_LAST_ID = 4;

    static final int LARGE_DICTIONARY_THRESHOLD = 200 * 1000;

    private static final boolean DBG = LatinImeLogger.sDBG;

    private AutoCorrection mAutoCorrection;

    private BinaryDictionary mMainDict;

    private Dictionary mUserDictionary;

    private Dictionary mAutoDictionary;

    private Dictionary mContactsDictionary;

    private Dictionary mUserBigramDictionary;

    private int mPrefMaxSuggestions = 12;

    private static final int PREF_MAX_BIGRAMS = 60;

    private boolean mQuickFixesEnabled;

    private double mAutoCorrectionThreshold;
    private int[] mPriorities = new int[mPrefMaxSuggestions];
    private int[] mBigramPriorities = new int[PREF_MAX_BIGRAMS];

    private ArrayList<CharSequence> mSuggestions = new ArrayList<CharSequence>();
    ArrayList<CharSequence> mBigramSuggestions  = new ArrayList<CharSequence>();
    private ArrayList<CharSequence> mStringPool = new ArrayList<CharSequence>();
    private String mLowerOriginalWord;

    // TODO: Remove these member variables by passing more context to addWord() callback method
    private boolean mIsFirstCharCapitalized;
    private boolean mIsAllUpperCase;

    private int mCorrectionMode = CORRECTION_BASIC;

    public Suggest(Context context, int dictionaryResId) {
        mMainDict = BinaryDictionary.initDictionary(context, dictionaryResId, DIC_MAIN);
        init();
    }

    /* package for test */ Suggest(File dictionary, long startOffset, long length) {
        mMainDict = BinaryDictionary.initDictionary(dictionary, startOffset, length, DIC_MAIN);
        init();
    }

    private void init() {
        mAutoCorrection = new AutoCorrection();
        initPool();
    }

    private void initPool() {
        for (int i = 0; i < mPrefMaxSuggestions; i++) {
            StringBuilder sb = new StringBuilder(getApproxMaxWordLength());
            mStringPool.add(sb);
        }
    }

    public void setQuickFixesEnabled(boolean enabled) {
        mQuickFixesEnabled = enabled;
    }

    public int getCorrectionMode() {
        return mCorrectionMode;
    }

    public void setCorrectionMode(int mode) {
        mCorrectionMode = mode;
    }

    public boolean hasMainDictionary() {
        return mMainDict != null && mMainDict.getSize() > LARGE_DICTIONARY_THRESHOLD;
    }

    public int getApproxMaxWordLength() {
        return APPROX_MAX_WORD_LENGTH;
    }

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set.
     */
    public void setUserDictionary(Dictionary userDictionary) {
        mUserDictionary = userDictionary;
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded.
     */
    public void setContactsDictionary(Dictionary userDictionary) {
        mContactsDictionary = userDictionary;
    }

    public void setAutoDictionary(Dictionary autoDictionary) {
        mAutoDictionary = autoDictionary;
    }

    public void setUserBigramDictionary(Dictionary userBigramDictionary) {
        mUserBigramDictionary = userBigramDictionary;
    }

    public void setAutoCorrectionThreshold(double threshold) {
        mAutoCorrectionThreshold = threshold;
    }

    public boolean isAggressiveAutoCorrectionMode() {
        return (mAutoCorrectionThreshold == 0);
    }

    /**
     * Number of suggestions to generate from the input key sequence. This has
     * to be a number between 1 and 100 (inclusive).
     * @param maxSuggestions
     * @throws IllegalArgumentException if the number is out of range
     */
    public void setMaxSuggestions(int maxSuggestions) {
        if (maxSuggestions < 1 || maxSuggestions > 100) {
            throw new IllegalArgumentException("maxSuggestions must be between 1 and 100");
        }
        mPrefMaxSuggestions = maxSuggestions;
        mPriorities = new int[mPrefMaxSuggestions];
        mBigramPriorities = new int[PREF_MAX_BIGRAMS];
        collectGarbage(mSuggestions, mPrefMaxSuggestions);
        while (mStringPool.size() < mPrefMaxSuggestions) {
            StringBuilder sb = new StringBuilder(getApproxMaxWordLength());
            mStringPool.add(sb);
        }
    }

    /**
     * Returns a object which represents suggested words that match the list of character codes
     * passed in. This object contents will be overwritten the next time this function is called.
     * @param view a view for retrieving the context for AutoText
     * @param wordComposer contains what is currently being typed
     * @param prevWordForBigram previous word (used only for bigram)
     * @return suggested words object.
     */
    public SuggestedWords getSuggestions(View view, WordComposer wordComposer,
            CharSequence prevWordForBigram) {
        return getSuggestedWordBuilder(view, wordComposer, prevWordForBigram).build();
    }

    // TODO: cleanup dictionaries looking up and suggestions building with SuggestedWords.Builder
    public SuggestedWords.Builder getSuggestedWordBuilder(View view, WordComposer wordComposer,
            CharSequence prevWordForBigram) {
        LatinImeLogger.onStartSuggestion(prevWordForBigram);
        mAutoCorrection.init();
        mIsFirstCharCapitalized = wordComposer.isFirstCharCapitalized();
        mIsAllUpperCase = wordComposer.isAllUpperCase();
        collectGarbage(mSuggestions, mPrefMaxSuggestions);
        Arrays.fill(mPriorities, 0);

        // Save a lowercase version of the original word
        CharSequence typedWord = wordComposer.getTypedWord();
        if (typedWord != null) {
            final String typedWordString = typedWord.toString();
            typedWord = typedWordString;
            mLowerOriginalWord = typedWordString.toLowerCase();
            // Treating USER_TYPED as UNIGRAM suggestion for logging now.
            LatinImeLogger.onAddSuggestedWord(typedWordString, Suggest.DIC_USER_TYPED,
                    Dictionary.DataType.UNIGRAM);
        } else {
            mLowerOriginalWord = "";
        }

        if (wordComposer.size() == 1 && (mCorrectionMode == CORRECTION_FULL_BIGRAM
                || mCorrectionMode == CORRECTION_BASIC)) {
            // At first character typed, search only the bigrams
            Arrays.fill(mBigramPriorities, 0);
            collectGarbage(mBigramSuggestions, PREF_MAX_BIGRAMS);

            if (!TextUtils.isEmpty(prevWordForBigram)) {
                CharSequence lowerPrevWord = prevWordForBigram.toString().toLowerCase();
                if (mMainDict != null && mMainDict.isValidWord(lowerPrevWord)) {
                    prevWordForBigram = lowerPrevWord;
                }
                if (mUserBigramDictionary != null) {
                    mUserBigramDictionary.getBigrams(wordComposer, prevWordForBigram, this);
                }
                if (mContactsDictionary != null) {
                    mContactsDictionary.getBigrams(wordComposer, prevWordForBigram, this);
                }
                if (mMainDict != null) {
                    mMainDict.getBigrams(wordComposer, prevWordForBigram, this);
                }
                char currentChar = wordComposer.getTypedWord().charAt(0);
                char currentCharUpper = Character.toUpperCase(currentChar);
                int count = 0;
                int bigramSuggestionSize = mBigramSuggestions.size();
                for (int i = 0; i < bigramSuggestionSize; i++) {
                    if (mBigramSuggestions.get(i).charAt(0) == currentChar
                            || mBigramSuggestions.get(i).charAt(0) == currentCharUpper) {
                        int poolSize = mStringPool.size();
                        StringBuilder sb = poolSize > 0 ?
                                (StringBuilder) mStringPool.remove(poolSize - 1)
                                : new StringBuilder(getApproxMaxWordLength());
                        sb.setLength(0);
                        sb.append(mBigramSuggestions.get(i));
                        mSuggestions.add(count++, sb);
                        if (count > mPrefMaxSuggestions) break;
                    }
                }
            }

        } else if (wordComposer.size() > 1) {
            // At second character typed, search the unigrams (scores being affected by bigrams)
            if (mUserDictionary != null || mContactsDictionary != null) {
                if (mUserDictionary != null) {
                    mUserDictionary.getWords(wordComposer, this);
                }
                if (mContactsDictionary != null) {
                    mContactsDictionary.getWords(wordComposer, this);
                }
            }
            if (mMainDict != null) mMainDict.getWords(wordComposer, this);
        }
        CharSequence autoText = null;
        final String typedWordString = typedWord == null ? null : typedWord.toString();
        if (typedWord != null) {
            // Apply quick fix only for the typed word.
            if (mQuickFixesEnabled) {
                final String lowerCaseTypedWord = typedWordString.toLowerCase();
                CharSequence tempAutoText =
                        AutoText.get(lowerCaseTypedWord, 0, lowerCaseTypedWord.length(), view);
                // Is there an AutoText (also known as Quick Fixes) correction?
                // Capitalize as needed
                if (!TextUtils.isEmpty(tempAutoText)
                        && (mIsAllUpperCase || mIsFirstCharCapitalized)) {
                    final int tempAutoTextLength = tempAutoText.length();
                    final int poolSize = mStringPool.size();
                    final StringBuilder sb =
                            poolSize > 0 ? (StringBuilder) mStringPool.remove(poolSize - 1)
                                    : new StringBuilder(getApproxMaxWordLength());
                    sb.setLength(0);
                    if (mIsAllUpperCase) {
                        sb.append(tempAutoText.toString().toUpperCase());
                    } else if (mIsFirstCharCapitalized) {
                        sb.append(Character.toUpperCase(tempAutoText.charAt(0)));
                        if (tempAutoTextLength > 1) {
                            sb.append(tempAutoText.subSequence(1, tempAutoTextLength));
                        }
                    }
                    tempAutoText = sb.toString();
                }
                boolean canAdd = tempAutoText != null;
                // Is that correction already the current prediction (or original word)?
                canAdd &= !TextUtils.equals(tempAutoText, typedWord);
                // Is that correction already the next predicted word?
                if (canAdd && mSuggestions.size() > 0 && mCorrectionMode != CORRECTION_BASIC) {
                    canAdd &= !TextUtils.equals(tempAutoText, mSuggestions.get(0));
                }
                if (canAdd) {
                    if (DBG) {
                        Log.d(TAG, "Auto corrected by AUTOTEXT.");
                    }
                    autoText = tempAutoText;
                }
            }
        }

        mAutoCorrection.updateAutoCorrectionStatus(this, wordComposer, mSuggestions, mPriorities,
                typedWord, mAutoCorrectionThreshold, mCorrectionMode, autoText);

        if (autoText != null) {
            mSuggestions.add(0, autoText);
        }

        if (typedWord != null) {
            mSuggestions.add(0, typedWordString);
        }
        removeDupes();

        if (DBG) {
            double normalizedScore = mAutoCorrection.getNormalizedScore();
            ArrayList<SuggestedWords.SuggestedWordInfo> frequencyInfoList =
                    new ArrayList<SuggestedWords.SuggestedWordInfo>();
            frequencyInfoList.add(new SuggestedWords.SuggestedWordInfo("+", false));
            final int priorityLength = mPriorities.length;
            for (int i = 0; i < priorityLength; ++i) {
                if (normalizedScore > 0) {
                    final String priorityThreshold = Integer.toString(mPriorities[i]) + " (" +
                            normalizedScore + ")";
                    frequencyInfoList.add(
                            new SuggestedWords.SuggestedWordInfo(priorityThreshold, false));
                    normalizedScore = 0.0;
                } else {
                    final String priority = Integer.toString(mPriorities[i]);
                    frequencyInfoList.add(new SuggestedWords.SuggestedWordInfo(priority, false));
                }
            }
            for (int i = priorityLength; i < mSuggestions.size(); ++i) {
                frequencyInfoList.add(new SuggestedWords.SuggestedWordInfo("--", false));
            }
            return new SuggestedWords.Builder().addWords(mSuggestions, frequencyInfoList);
        }
        return new SuggestedWords.Builder().addWords(mSuggestions, null);
    }

    private void removeDupes() {
        final ArrayList<CharSequence> suggestions = mSuggestions;
        if (suggestions.size() < 2) return;
        int i = 1;
        // Don't cache suggestions.size(), since we may be removing items
        while (i < suggestions.size()) {
            final CharSequence cur = suggestions.get(i);
            // Compare each candidate with each previous candidate
            for (int j = 0; j < i; j++) {
                CharSequence previous = suggestions.get(j);
                if (TextUtils.equals(cur, previous)) {
                    removeFromSuggestions(i);
                    i--;
                    break;
                }
            }
            i++;
        }
    }

    private void removeFromSuggestions(int index) {
        CharSequence garbage = mSuggestions.remove(index);
        if (garbage != null && garbage instanceof StringBuilder) {
            mStringPool.add(garbage);
        }
    }

    public boolean hasAutoCorrection() {
        return mAutoCorrection.hasAutoCorrection();
    }

    private static boolean compareCaseInsensitive(final String lowerOriginalWord,
            final char[] word, final int offset, final int length) {
        final int originalLength = lowerOriginalWord.length();
        if (originalLength == length && Character.isUpperCase(word[offset])) {
            for (int i = 0; i < originalLength; i++) {
                if (lowerOriginalWord.charAt(i) != Character.toLowerCase(word[offset+i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean addWord(final char[] word, final int offset, final int length, int freq,
            final int dicTypeId, final Dictionary.DataType dataType) {
        Dictionary.DataType dataTypeForLog = dataType;
        ArrayList<CharSequence> suggestions;
        int[] priorities;
        int prefMaxSuggestions;
        if(dataType == Dictionary.DataType.BIGRAM) {
            suggestions = mBigramSuggestions;
            priorities = mBigramPriorities;
            prefMaxSuggestions = PREF_MAX_BIGRAMS;
        } else {
            suggestions = mSuggestions;
            priorities = mPriorities;
            prefMaxSuggestions = mPrefMaxSuggestions;
        }

        int pos = 0;

        // Check if it's the same word, only caps are different
        if (compareCaseInsensitive(mLowerOriginalWord, word, offset, length)) {
            pos = 0;
        } else {
            if (dataType == Dictionary.DataType.UNIGRAM) {
                // Check if the word was already added before (by bigram data)
                int bigramSuggestion = searchBigramSuggestion(word,offset,length);
                if(bigramSuggestion >= 0) {
                    dataTypeForLog = Dictionary.DataType.BIGRAM;
                    // turn freq from bigram into multiplier specified above
                    double multiplier = (((double) mBigramPriorities[bigramSuggestion])
                            / MAXIMUM_BIGRAM_FREQUENCY)
                            * (BIGRAM_MULTIPLIER_MAX - BIGRAM_MULTIPLIER_MIN)
                            + BIGRAM_MULTIPLIER_MIN;
                    /* Log.d(TAG,"bigram num: " + bigramSuggestion
                            + "  wordB: " + mBigramSuggestions.get(bigramSuggestion).toString()
                            + "  currentPriority: " + freq + "  bigramPriority: "
                            + mBigramPriorities[bigramSuggestion]
                            + "  multiplier: " + multiplier); */
                    freq = (int)Math.round((freq * multiplier));
                }
            }

            // Check the last one's priority and bail
            if (priorities[prefMaxSuggestions - 1] >= freq) return true;
            while (pos < prefMaxSuggestions) {
                if (priorities[pos] < freq
                        || (priorities[pos] == freq && length < suggestions.get(pos).length())) {
                    break;
                }
                pos++;
            }
        }
        if (pos >= prefMaxSuggestions) {
            return true;
        }

        System.arraycopy(priorities, pos, priorities, pos + 1, prefMaxSuggestions - pos - 1);
        priorities[pos] = freq;
        int poolSize = mStringPool.size();
        StringBuilder sb = poolSize > 0 ? (StringBuilder) mStringPool.remove(poolSize - 1)
                : new StringBuilder(getApproxMaxWordLength());
        sb.setLength(0);
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
        suggestions.add(pos, sb);
        if (suggestions.size() > prefMaxSuggestions) {
            CharSequence garbage = suggestions.remove(prefMaxSuggestions);
            if (garbage instanceof StringBuilder) {
                mStringPool.add(garbage);
            }
        } else {
            LatinImeLogger.onAddSuggestedWord(sb.toString(), dicTypeId, dataTypeForLog);
        }
        return true;
    }

    private int searchBigramSuggestion(final char[] word, final int offset, final int length) {
        // TODO This is almost O(n^2). Might need fix.
        // search whether the word appeared in bigram data
        int bigramSuggestSize = mBigramSuggestions.size();
        for(int i = 0; i < bigramSuggestSize; i++) {
            if(mBigramSuggestions.get(i).length() == length) {
                boolean chk = true;
                for(int j = 0; j < length; j++) {
                    if(mBigramSuggestions.get(i).charAt(j) != word[offset+j]) {
                        chk = false;
                        break;
                    }
                }
                if(chk) return i;
            }
        }

        return -1;
    }

    public boolean isValidWord(final CharSequence word) {
        if (word == null || word.length() == 0 || mMainDict == null) {
            return false;
        }
        return mMainDict.isValidWord(word)
                || (mUserDictionary != null && mUserDictionary.isValidWord(word))
                || (mAutoDictionary != null && mAutoDictionary.isValidWord(word))
                || (mContactsDictionary != null && mContactsDictionary.isValidWord(word));
    }

    private void collectGarbage(ArrayList<CharSequence> suggestions, int prefMaxSuggestions) {
        int poolSize = mStringPool.size();
        int garbageSize = suggestions.size();
        while (poolSize < prefMaxSuggestions && garbageSize > 0) {
            CharSequence garbage = suggestions.get(garbageSize - 1);
            if (garbage != null && garbage instanceof StringBuilder) {
                mStringPool.add(garbage);
                poolSize++;
            }
            garbageSize--;
        }
        if (poolSize == prefMaxSuggestions + 1) {
            Log.w("Suggest", "String pool got too big: " + poolSize);
        }
        suggestions.clear();
    }

    public void close() {
        if (mMainDict != null) {
            mMainDict.close();
            mMainDict = null;
        }
        if (mUserDictionary != null) {
            mUserDictionary.close();
            mUserDictionary = null;
        }
        if (mUserBigramDictionary != null) {
            mUserBigramDictionary.close();
            mUserBigramDictionary = null;
        }
        if (mContactsDictionary != null) {
            mContactsDictionary.close();
            mContactsDictionary = null;
        }
        if (mAutoDictionary != null) {
            mAutoDictionary.close();
            mAutoDictionary = null;
        }
    }
}
