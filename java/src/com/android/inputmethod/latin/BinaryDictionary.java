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

import com.android.inputmethod.keyboard.ProximityInfo;

import java.util.Arrays;
import java.util.Locale;

/**
 * Implements a static, compacted, binary dictionary of standard words.
 */
public class BinaryDictionary extends Dictionary {

    public static final String DICTIONARY_PACK_AUTHORITY =
            "com.android.inputmethod.latin.dictionarypack";

    /**
     * There is a difference between what java and native code can handle.
     * This value should only be used in BinaryDictionary.java
     * It is necessary to keep it at this value because some languages e.g. German have
     * really long words.
     */
    public static final int MAX_WORD_LENGTH = 48;
    public static final int MAX_WORDS = 18;

    private static final String TAG = "BinaryDictionary";
    private static final int MAX_BIGRAMS = 60;

    private static final int TYPED_LETTER_MULTIPLIER = 2;

    private int mDicTypeId;
    private long mNativeDict;
    private final int[] mInputCodes = new int[MAX_WORD_LENGTH];
    private final char[] mOutputChars = new char[MAX_WORD_LENGTH * MAX_WORDS];
    private final char[] mOutputChars_bigrams = new char[MAX_WORD_LENGTH * MAX_BIGRAMS];
    private final int[] mScores = new int[MAX_WORDS];
    private final int[] mBigramScores = new int[MAX_BIGRAMS];

    private final boolean mUseFullEditDistance;

    /**
     * Constructor for the binary dictionary. This is supposed to be called from the
     * dictionary factory.
     * All implementations should pass null into flagArray, except for testing purposes.
     * @param context the context to access the environment from.
     * @param filename the name of the file to read through native code.
     * @param offset the offset of the dictionary data within the file.
     * @param length the length of the binary data.
     * @param useFullEditDistance whether to use the full edit distance in suggestions
     */
    public BinaryDictionary(final Context context,
            final String filename, final long offset, final long length,
            final boolean useFullEditDistance, final Locale locale) {
        // Note: at the moment a binary dictionary is always of the "main" type.
        // Initializing this here will help transitioning out of the scheme where
        // the Suggest class knows everything about every single dictionary.
        mDicTypeId = Suggest.DIC_MAIN;
        mUseFullEditDistance = useFullEditDistance;
        loadDictionary(filename, offset, length);
    }

    static {
        JniUtils.loadNativeLibrary();
    }

    private native long openNative(String sourceDir, long dictOffset, long dictSize,
            int typedLetterMultiplier, int fullWordMultiplier, int maxWordLength, int maxWords);
    private native void closeNative(long dict);
    private native int getFrequencyNative(long dict, int[] word, int wordLength);
    private native boolean isValidBigramNative(long dict, int[] word1, int[] word2);
    private native int getSuggestionsNative(long dict, long proximityInfo, int[] xCoordinates,
            int[] yCoordinates, int[] inputCodes, int codesSize, int[] prevWordForBigrams,
            boolean useFullEditDistance, char[] outputChars, int[] scores);
    private native int getBigramsNative(long dict, int[] prevWord, int prevWordLength,
            int[] inputCodes, int inputCodesLength, char[] outputChars, int[] scores,
            int maxWordLength, int maxBigrams);
    private static native float calcNormalizedScoreNative(
            char[] before, int beforeLength, char[] after, int afterLength, int score);
    private static native int editDistanceNative(
            char[] before, int beforeLength, char[] after, int afterLength);

    private final void loadDictionary(String path, long startOffset, long length) {
        mNativeDict = openNative(path, startOffset, length,
                TYPED_LETTER_MULTIPLIER, FULL_WORD_SCORE_MULTIPLIER, MAX_WORD_LENGTH, MAX_WORDS);
    }

    @Override
    public void getBigrams(final WordComposer codes, final CharSequence previousWord,
            final WordCallback callback) {
        if (mNativeDict == 0) return;

        int[] codePoints = StringUtils.toCodePointArray(previousWord.toString());
        Arrays.fill(mOutputChars_bigrams, (char) 0);
        Arrays.fill(mBigramScores, 0);

        int codesSize = codes.size();
        Arrays.fill(mInputCodes, -1);
        if (codesSize > 0) {
            mInputCodes[0] = codes.getCodeAt(0);
        }

        int count = getBigramsNative(mNativeDict, codePoints, codePoints.length, mInputCodes,
                codesSize, mOutputChars_bigrams, mBigramScores, MAX_WORD_LENGTH, MAX_BIGRAMS);
        if (count > MAX_BIGRAMS) {
            count = MAX_BIGRAMS;
        }

        for (int j = 0; j < count; ++j) {
            if (codesSize > 0 && mBigramScores[j] < 1) break;
            final int start = j * MAX_WORD_LENGTH;
            int len = 0;
            while (len <  MAX_WORD_LENGTH && mOutputChars_bigrams[start + len] != 0) {
                ++len;
            }
            if (len > 0) {
                callback.addWord(mOutputChars_bigrams, start, len, mBigramScores[j],
                        mDicTypeId, Dictionary.BIGRAM);
            }
        }
    }

    // proximityInfo and/or prevWordForBigrams may not be null.
    @Override
    public void getWords(final WordComposer codes, final CharSequence prevWordForBigrams,
            final WordCallback callback, final ProximityInfo proximityInfo) {
        final int count = getSuggestions(codes, prevWordForBigrams, proximityInfo, mOutputChars,
                mScores);

        for (int j = 0; j < count; ++j) {
            if (mScores[j] < 1) break;
            final int start = j * MAX_WORD_LENGTH;
            int len = 0;
            while (len < MAX_WORD_LENGTH && mOutputChars[start + len] != 0) {
                ++len;
            }
            if (len > 0) {
                callback.addWord(mOutputChars, start, len, mScores[j], mDicTypeId,
                        Dictionary.UNIGRAM);
            }
        }
    }

    /* package for test */ boolean isValidDictionary() {
        return mNativeDict != 0;
    }

    // proximityInfo may not be null.
    /* package for test */ int getSuggestions(final WordComposer codes,
            final CharSequence prevWordForBigrams, final ProximityInfo proximityInfo,
            char[] outputChars, int[] scores) {
        if (!isValidDictionary()) return -1;

        final int codesSize = codes.size();
        // Won't deal with really long words.
        if (codesSize > MAX_WORD_LENGTH - 1) return -1;

        Arrays.fill(mInputCodes, WordComposer.NOT_A_CODE);
        for (int i = 0; i < codesSize; i++) {
            mInputCodes[i] = codes.getCodeAt(i);
        }
        Arrays.fill(outputChars, (char) 0);
        Arrays.fill(scores, 0);

        final int[] prevWordCodePointArray = null == prevWordForBigrams
                ? null : StringUtils.toCodePointArray(prevWordForBigrams.toString());

        // TODO: pass the previous word to native code
        return getSuggestionsNative(
                mNativeDict, proximityInfo.getNativeProximityInfo(),
                codes.getXCoordinates(), codes.getYCoordinates(), mInputCodes, codesSize,
                prevWordCodePointArray, mUseFullEditDistance, outputChars, scores);
    }

    public static float calcNormalizedScore(String before, String after, int score) {
        return calcNormalizedScoreNative(before.toCharArray(), before.length(),
                after.toCharArray(), after.length(), score);
    }

    public static int editDistance(String before, String after) {
        return editDistanceNative(
                before.toCharArray(), before.length(), after.toCharArray(), after.length());
    }

    @Override
    public boolean isValidWord(CharSequence word) {
        return getFrequency(word) >= 0;
    }

    @Override
    public int getFrequency(CharSequence word) {
        if (word == null) return -1;
        int[] chars = StringUtils.toCodePointArray(word.toString());
        return getFrequencyNative(mNativeDict, chars, chars.length);
    }

    // TODO: Add a batch process version (isValidBigramMultiple?) to avoid excessive numbers of jni
    // calls when checking for changes in an entire dictionary.
    public boolean isValidBigram(CharSequence word1, CharSequence word2) {
        if (TextUtils.isEmpty(word1) || TextUtils.isEmpty(word2)) return false;
        int[] chars1 = StringUtils.toCodePointArray(word1.toString());
        int[] chars2 = StringUtils.toCodePointArray(word2.toString());
        return isValidBigramNative(mNativeDict, chars1, chars2);
    }

    @Override
    public synchronized void close() {
        closeInternal();
    }

    private void closeInternal() {
        if (mNativeDict != 0) {
            closeNative(mNativeDict);
            mNativeDict = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            closeInternal();
        } finally {
            super.finalize();
        }
    }
}
