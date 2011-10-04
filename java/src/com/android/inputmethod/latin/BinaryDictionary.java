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

import com.android.inputmethod.keyboard.ProximityInfo;

import java.util.Arrays;

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
    private static final int MAX_PROXIMITY_CHARS_SIZE = ProximityInfo.MAX_PROXIMITY_CHARS_SIZE;
    private static final int MAX_BIGRAMS = 60;

    private static final int TYPED_LETTER_MULTIPLIER = 2;

    private int mDicTypeId;
    private int mNativeDict;
    private final int[] mInputCodes = new int[MAX_WORD_LENGTH * MAX_PROXIMITY_CHARS_SIZE];
    private final char[] mOutputChars = new char[MAX_WORD_LENGTH * MAX_WORDS];
    private final char[] mOutputChars_bigrams = new char[MAX_WORD_LENGTH * MAX_BIGRAMS];
    private final int[] mScores = new int[MAX_WORDS];
    private final int[] mBigramScores = new int[MAX_BIGRAMS];

    public static final Flag FLAG_REQUIRES_GERMAN_UMLAUT_PROCESSING =
            new Flag(R.bool.config_require_umlaut_processing, 0x1);

    // FULL_EDIT_DISTANCE is a flag that forces the dictionary to use full words
    // when computing edit distance, instead of the default behavior of stopping
    // the evaluation at the size the user typed.
    public static final Flag FLAG_USE_FULL_EDIT_DISTANCE = new Flag(0x2);

    // Can create a new flag from extravalue :
    // public static final Flag FLAG_MYFLAG =
    //         new Flag("my_flag", 0x02);

    // ALL_CONFIG_FLAGS is a collection of flags that enable reading all flags from configuration.
    // This is but a mask - it does not mean the flags will be on, only that the configuration
    // will be read for this particular flag.
    public static final Flag[] ALL_CONFIG_FLAGS = {
        // Here should reside all flags that trigger some special processing
        // These *must* match the definition in UnigramDictionary enum in
        // unigram_dictionary.h so please update both at the same time.
        // Please note that flags created with a resource are of type CONFIG while flags
        // created with a string are of type EXTRAVALUE. These behave like masks, and the
        // actual value will be read from the configuration/extra value at run time for
        // the configuration at dictionary creation time.
        FLAG_REQUIRES_GERMAN_UMLAUT_PROCESSING,
    };

    private int mFlags = 0;

    /**
     * Constructor for the binary dictionary. This is supposed to be called from the
     * dictionary factory.
     * All implementations should pass null into flagArray, except for testing purposes.
     * @param context the context to access the environment from.
     * @param filename the name of the file to read through native code.
     * @param offset the offset of the dictionary data within the file.
     * @param length the length of the binary data.
     * @param flagArray the flags to limit the dictionary to, or null for default.
     */
    public BinaryDictionary(final Context context,
            final String filename, final long offset, final long length, Flag[] flagArray) {
        // Note: at the moment a binary dictionary is always of the "main" type.
        // Initializing this here will help transitioning out of the scheme where
        // the Suggest class knows everything about every single dictionary.
        mDicTypeId = Suggest.DIC_MAIN;
        // TODO: Stop relying on the state of SubtypeSwitcher, get it as a parameter
        mFlags = Flag.initFlags(null == flagArray ? ALL_CONFIG_FLAGS : flagArray, context,
                SubtypeSwitcher.getInstance());
        loadDictionary(filename, offset, length);
    }

    static {
        Utils.loadNativeLibrary();
    }

    private native int openNative(String sourceDir, long dictOffset, long dictSize,
            int typedLetterMultiplier, int fullWordMultiplier, int maxWordLength,
            int maxWords, int maxAlternatives);
    private native void closeNative(int dict);
    private native boolean isValidWordNative(int nativeData, char[] word, int wordLength);
    private native int getSuggestionsNative(int dict, int proximityInfo, int[] xCoordinates,
            int[] yCoordinates, int[] inputCodes, int codesSize, int flags, char[] outputChars,
            int[] scores);
    private native int getBigramsNative(int dict, char[] prevWord, int prevWordLength,
            int[] inputCodes, int inputCodesLength, char[] outputChars, int[] scores,
            int maxWordLength, int maxBigrams, int maxAlternatives);

    private final void loadDictionary(String path, long startOffset, long length) {
        mNativeDict = openNative(path, startOffset, length,
                    TYPED_LETTER_MULTIPLIER, FULL_WORD_SCORE_MULTIPLIER,
                    MAX_WORD_LENGTH, MAX_WORDS, MAX_PROXIMITY_CHARS_SIZE);
    }

    @Override
    public void getBigrams(final WordComposer codes, final CharSequence previousWord,
            final WordCallback callback) {
        if (mNativeDict == 0) return;

        char[] chars = previousWord.toString().toCharArray();
        Arrays.fill(mOutputChars_bigrams, (char) 0);
        Arrays.fill(mBigramScores, 0);

        int codesSize = codes.size();
        if (codesSize <= 0) {
            // Do not return bigrams from BinaryDictionary when nothing was typed.
            // Only use user-history bigrams (or whatever other bigram dictionaries decide).
            return;
        }
        Arrays.fill(mInputCodes, -1);
        int[] alternatives = codes.getCodesAt(0);
        System.arraycopy(alternatives, 0, mInputCodes, 0,
                Math.min(alternatives.length, MAX_PROXIMITY_CHARS_SIZE));

        int count = getBigramsNative(mNativeDict, chars, chars.length, mInputCodes, codesSize,
                mOutputChars_bigrams, mBigramScores, MAX_WORD_LENGTH, MAX_BIGRAMS,
                MAX_PROXIMITY_CHARS_SIZE);

        for (int j = 0; j < count; ++j) {
            if (mBigramScores[j] < 1) break;
            final int start = j * MAX_WORD_LENGTH;
            int len = 0;
            while (len <  MAX_WORD_LENGTH && mOutputChars_bigrams[start + len] != 0) {
                ++len;
            }
            if (len > 0) {
                callback.addWord(mOutputChars_bigrams, start, len, mBigramScores[j],
                        mDicTypeId, DataType.BIGRAM);
            }
        }
    }

    // proximityInfo may not be null.
    @Override
    public void getWords(final WordComposer codes, final WordCallback callback,
            final ProximityInfo proximityInfo) {
        final int count = getSuggestions(codes, proximityInfo, mOutputChars, mScores);

        for (int j = 0; j < count; ++j) {
            if (mScores[j] < 1) break;
            final int start = j * MAX_WORD_LENGTH;
            int len = 0;
            while (len < MAX_WORD_LENGTH && mOutputChars[start + len] != 0) {
                ++len;
            }
            if (len > 0) {
                callback.addWord(mOutputChars, start, len, mScores[j], mDicTypeId,
                        DataType.UNIGRAM);
            }
        }
    }

    /* package for test */ boolean isValidDictionary() {
        return mNativeDict != 0;
    }

    // proximityInfo may not be null.
    /* package for test */ int getSuggestions(final WordComposer codes,
            final ProximityInfo proximityInfo, char[] outputChars, int[] scores) {
        if (!isValidDictionary()) return -1;

        final int codesSize = codes.size();
        // Won't deal with really long words.
        if (codesSize > MAX_WORD_LENGTH - 1) return -1;

        Arrays.fill(mInputCodes, WordComposer.NOT_A_CODE);
        for (int i = 0; i < codesSize; i++) {
            int[] alternatives = codes.getCodesAt(i);
            System.arraycopy(alternatives, 0, mInputCodes, i * MAX_PROXIMITY_CHARS_SIZE,
                    Math.min(alternatives.length, MAX_PROXIMITY_CHARS_SIZE));
        }
        Arrays.fill(outputChars, (char) 0);
        Arrays.fill(scores, 0);

        return getSuggestionsNative(
                mNativeDict, proximityInfo.getNativeProximityInfo(),
                codes.getXCoordinates(), codes.getYCoordinates(), mInputCodes, codesSize,
                mFlags, outputChars, scores);
    }

    @Override
    public boolean isValidWord(CharSequence word) {
        if (word == null) return false;
        char[] chars = word.toString().toCharArray();
        return isValidWordNative(mNativeDict, chars, chars.length);
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
