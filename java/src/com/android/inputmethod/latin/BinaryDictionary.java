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

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.ProximityInfo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import java.io.File;
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
    private static final int MAX_PROXIMITY_CHARS_SIZE = ProximityInfo.MAX_PROXIMITY_CHARS_SIZE;
    private static final int MAX_BIGRAMS = 60;

    private static final int TYPED_LETTER_MULTIPLIER = 2;

    private static final BinaryDictionary sInstance = new BinaryDictionary();
    private int mDicTypeId;
    private int mNativeDict;
    private long mDictLength;
    private final int[] mInputCodes = new int[MAX_WORD_LENGTH * MAX_PROXIMITY_CHARS_SIZE];
    private final char[] mOutputChars = new char[MAX_WORD_LENGTH * MAX_WORDS];
    private final char[] mOutputChars_bigrams = new char[MAX_WORD_LENGTH * MAX_BIGRAMS];
    private final int[] mScores = new int[MAX_WORDS];
    private final int[] mBigramScores = new int[MAX_BIGRAMS];

    private final KeyboardSwitcher mKeyboardSwitcher = KeyboardSwitcher.getInstance();

    public static final Flag FLAG_REQUIRES_GERMAN_UMLAUT_PROCESSING =
            new Flag(R.bool.config_require_umlaut_processing, 0x1);

    // Can create a new flag from extravalue :
    // public static final Flag FLAG_MYFLAG =
    //         new Flag("my_flag", 0x02);

    private static final Flag[] ALL_FLAGS = {
        // Here should reside all flags that trigger some special processing
        // These *must* match the definition in UnigramDictionary enum in
        // unigram_dictionary.h so please update both at the same time.
        FLAG_REQUIRES_GERMAN_UMLAUT_PROCESSING,
    };

    private int mFlags = 0;

    private BinaryDictionary() {
    }

    /**
     * Initializes a dictionary from a raw resource file
     * @param context application context for reading resources
     * @param resId the resource containing the raw binary dictionary
     * @param dicTypeId the type of the dictionary being created, out of the list in Suggest.DIC_*
     * @return an initialized instance of BinaryDictionary
     */
    public static BinaryDictionary initDictionary(Context context, int resId, int dicTypeId) {
        synchronized (sInstance) {
            sInstance.closeInternal();
            try {
                final AssetFileDescriptor afd = context.getResources().openRawResourceFd(resId);
                if (afd == null) {
                    Log.e(TAG, "Found the resource but it is compressed. resId=" + resId);
                    return null;
                }
                final String sourceDir = context.getApplicationInfo().sourceDir;
                final File packagePath = new File(sourceDir);
                // TODO: Come up with a way to handle a directory.
                if (!packagePath.isFile()) {
                    Log.e(TAG, "sourceDir is not a file: " + sourceDir);
                    return null;
                }
                sInstance.loadDictionary(sourceDir, afd.getStartOffset(), afd.getLength());
                sInstance.mDicTypeId = dicTypeId;
            } catch (android.content.res.Resources.NotFoundException e) {
                Log.e(TAG, "Could not find the resource. resId=" + resId);
                return null;
            }
        }
        sInstance.mFlags = Flag.initFlags(ALL_FLAGS, context, SubtypeSwitcher.getInstance());
        return sInstance;
    }

    /* package for test */ static BinaryDictionary initDictionary(Context context, File dictionary,
            long startOffset, long length, int dicTypeId, Flag[] flagArray) {
        synchronized (sInstance) {
            sInstance.closeInternal();
            if (dictionary.isFile()) {
                sInstance.loadDictionary(dictionary.getAbsolutePath(), startOffset, length);
                sInstance.mDicTypeId = dicTypeId;
            } else {
                Log.e(TAG, "Could not find the file. path=" + dictionary.getAbsolutePath());
                return null;
            }
        }
        sInstance.mFlags = Flag.initFlags(flagArray, context, null);
        return sInstance;
    }

    static {
        Utils.loadNativeLibrary();
    }

    /**
     * Initializes a dictionary from a dictionary pack.
     *
     * This searches for a content provider providing a dictionary pack for the specified
     * locale. If none is found, it falls back to using the resource passed as fallBackResId
     * as a dictionary.
     * @param context application context for reading resources
     * @param dicTypeId the type of the dictionary being created, out of the list in Suggest.DIC_*
     * @param locale the locale for which to create the dictionary
     * @param fallBackResId the id of the resource to use as a fallback if no pack is found
     * @return an initialized instance of BinaryDictionary
     */
    public static BinaryDictionary initDictionaryFromManager(Context context, int dicTypeId,
            Locale locale, int fallbackResId) {
        if (null == locale) {
            Log.e(TAG, "No locale defined for dictionary");
            return initDictionary(context, fallbackResId, dicTypeId);
        }
        synchronized (sInstance) {
            sInstance.closeInternal();

            final AssetFileAddress dictFile = BinaryDictionaryGetter.getDictionaryFile(locale,
                    context, fallbackResId);
            if (null != dictFile) {
                sInstance.loadDictionary(dictFile.mFilename, dictFile.mOffset, dictFile.mLength);
                sInstance.mDicTypeId = dicTypeId;
            }
        }
        sInstance.mFlags = Flag.initFlags(ALL_FLAGS, context, SubtypeSwitcher.getInstance());
        return sInstance;
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
        mDictLength = length;
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

    @Override
    public void getWords(final WordComposer codes, final WordCallback callback) {
        final int count = getSuggestions(codes, mKeyboardSwitcher.getLatinKeyboard(),
                mOutputChars, mScores);

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

    /* package for test */ int getSuggestions(final WordComposer codes, final Keyboard keyboard,
            char[] outputChars, int[] scores) {
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
                mNativeDict, keyboard.getProximityInfo(),
                codes.getXCoordinates(), codes.getYCoordinates(), mInputCodes, codesSize,
                mFlags, outputChars, scores);
    }

    @Override
    public boolean isValidWord(CharSequence word) {
        if (word == null) return false;
        char[] chars = word.toString().toCharArray();
        return isValidWordNative(mNativeDict, chars, chars.length);
    }

    public long getSize() {
        return mDictLength; // This value is initialized in loadDictionary()
    }

    @Override
    public synchronized void close() {
        closeInternal();
    }

    private void closeInternal() {
        if (mNativeDict != 0) {
            closeNative(mNativeDict);
            mNativeDict = 0;
            mDictLength = 0;
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
