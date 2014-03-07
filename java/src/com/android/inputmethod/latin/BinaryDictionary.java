/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.FusionDictionary.DictionaryOptions;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.WordProperty;
import com.android.inputmethod.latin.personalization.PersonalizationHelper;
import com.android.inputmethod.latin.settings.NativeSuggestOptions;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.JniUtils;
import com.android.inputmethod.latin.utils.LanguageModelParam;
import com.android.inputmethod.latin.utils.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

/**
 * Implements a static, compacted, binary dictionary of standard words.
 */
// TODO: All methods which should be locked need to have a suffix "Locked".
public final class BinaryDictionary extends Dictionary {
    private static final String TAG = BinaryDictionary.class.getSimpleName();

    // Must be equal to MAX_WORD_LENGTH in native/jni/src/defines.h
    private static final int MAX_WORD_LENGTH = Constants.DICTIONARY_MAX_WORD_LENGTH;
    // Must be equal to MAX_RESULTS in native/jni/src/defines.h
    private static final int MAX_RESULTS = 18;
    // The cutoff returned by native for auto-commit confidence.
    // Must be equal to CONFIDENCE_TO_AUTO_COMMIT in native/jni/src/defines.h
    private static final int CONFIDENCE_TO_AUTO_COMMIT = 1000000;

    @UsedForTesting
    public static final String UNIGRAM_COUNT_QUERY = "UNIGRAM_COUNT";
    @UsedForTesting
    public static final String BIGRAM_COUNT_QUERY = "BIGRAM_COUNT";
    @UsedForTesting
    public static final String MAX_UNIGRAM_COUNT_QUERY = "MAX_UNIGRAM_COUNT";
    @UsedForTesting
    public static final String MAX_BIGRAM_COUNT_QUERY = "MAX_BIGRAM_COUNT";

    public static final int NOT_A_VALID_TIMESTAMP = -1;

    // Format to get unigram flags from native side via getWordPropertyNative().
    private static final int FORMAT_WORD_PROPERTY_OUTPUT_FLAG_COUNT = 4;
    private static final int FORMAT_WORD_PROPERTY_IS_NOT_A_WORD_INDEX = 0;
    private static final int FORMAT_WORD_PROPERTY_IS_BLACKLISTED_INDEX = 1;
    private static final int FORMAT_WORD_PROPERTY_HAS_BIGRAMS_INDEX = 2;
    private static final int FORMAT_WORD_PROPERTY_HAS_SHORTCUTS_INDEX = 3;

    // Format to get probability and historical info from native side via getWordPropertyNative().
    public static final int FORMAT_WORD_PROPERTY_OUTPUT_PROBABILITY_INFO_COUNT = 4;
    public static final int FORMAT_WORD_PROPERTY_PROBABILITY_INDEX = 0;
    public static final int FORMAT_WORD_PROPERTY_TIMESTAMP_INDEX = 1;
    public static final int FORMAT_WORD_PROPERTY_LEVEL_INDEX = 2;
    public static final int FORMAT_WORD_PROPERTY_COUNT_INDEX = 3;

    private long mNativeDict;
    private final Locale mLocale;
    private final long mDictSize;
    private final String mDictFilePath;
    private final boolean mIsUpdatable;
    private final int[] mInputCodePoints = new int[MAX_WORD_LENGTH];
    private final int[] mOutputSuggestionCount = new int[1];
    private final int[] mOutputCodePoints = new int[MAX_WORD_LENGTH * MAX_RESULTS];
    private final int[] mSpaceIndices = new int[MAX_RESULTS];
    private final int[] mOutputScores = new int[MAX_RESULTS];
    private final int[] mOutputTypes = new int[MAX_RESULTS];
    // Only one result is ever used
    private final int[] mOutputAutoCommitFirstWordConfidence = new int[1];

    private final NativeSuggestOptions mNativeSuggestOptions = new NativeSuggestOptions();

    private final SparseArray<DicTraverseSession> mDicTraverseSessions =
            CollectionUtils.newSparseArray();

    // TODO: There should be a way to remove used DicTraverseSession objects from
    // {@code mDicTraverseSessions}.
    private DicTraverseSession getTraverseSession(final int traverseSessionId) {
        synchronized(mDicTraverseSessions) {
            DicTraverseSession traverseSession = mDicTraverseSessions.get(traverseSessionId);
            if (traverseSession == null) {
                traverseSession = mDicTraverseSessions.get(traverseSessionId);
                if (traverseSession == null) {
                    traverseSession = new DicTraverseSession(mLocale, mNativeDict, mDictSize);
                    mDicTraverseSessions.put(traverseSessionId, traverseSession);
                }
            }
            return traverseSession;
        }
    }

    /**
     * Constructor for the binary dictionary. This is supposed to be called from the
     * dictionary factory.
     * @param filename the name of the file to read through native code.
     * @param offset the offset of the dictionary data within the file.
     * @param length the length of the binary data.
     * @param useFullEditDistance whether to use the full edit distance in suggestions
     * @param dictType the dictionary type, as a human-readable string
     * @param isUpdatable whether to open the dictionary file in writable mode.
     */
    public BinaryDictionary(final String filename, final long offset, final long length,
            final boolean useFullEditDistance, final Locale locale, final String dictType,
            final boolean isUpdatable) {
        super(dictType);
        mLocale = locale;
        mDictSize = length;
        mDictFilePath = filename;
        mIsUpdatable = isUpdatable;
        mNativeSuggestOptions.setUseFullEditDistance(useFullEditDistance);
        loadDictionary(filename, offset, length, isUpdatable);
    }

    static {
        JniUtils.loadNativeLibrary();
    }

    private static native long openNative(String sourceDir, long dictOffset, long dictSize,
            boolean isUpdatable);
    private static native void getHeaderInfoNative(long dict, int[] outHeaderSize,
            int[] outFormatVersion, ArrayList<int[]> outAttributeKeys,
            ArrayList<int[]> outAttributeValues);
    private static native void flushNative(long dict, String filePath);
    private static native boolean needsToRunGCNative(long dict, boolean mindsBlockByGC);
    private static native void flushWithGCNative(long dict, String filePath);
    private static native void closeNative(long dict);
    private static native int getFormatVersionNative(long dict);
    private static native int getProbabilityNative(long dict, int[] word);
    private static native int getBigramProbabilityNative(long dict, int[] word0, int[] word1);
    private static native void getWordPropertyNative(long dict, int[] word,
            int[] outCodePoints, boolean[] outFlags, int[] outProbabilityInfo,
            ArrayList<int[]> outBigramTargets, ArrayList<int[]> outBigramProbabilityInfo,
            ArrayList<int[]> outShortcutTargets, ArrayList<Integer> outShortcutProbabilities);
    private static native int getNextWordNative(long dict, int token, int[] outCodePoints);
    private static native void getSuggestionsNative(long dict, long proximityInfo,
            long traverseSession, int[] xCoordinates, int[] yCoordinates, int[] times,
            int[] pointerIds, int[] inputCodePoints, int inputSize, int commitPoint,
            int[] suggestOptions, int[] prevWordCodePointArray, int[] outputSuggestionCount,
            int[] outputCodePoints, int[] outputScores, int[] outputIndices, int[] outputTypes,
            int[] outputAutoCommitFirstWordConfidence);
    private static native void addUnigramWordNative(long dict, int[] word, int probability,
            int[] shortcutTarget, int shortcutProbability, boolean isNotAWord,
            boolean isBlacklisted, int timestamp);
    private static native void addBigramWordsNative(long dict, int[] word0, int[] word1,
            int probability, int timestamp);
    private static native void removeBigramWordsNative(long dict, int[] word0, int[] word1);
    private static native int addMultipleDictionaryEntriesNative(long dict,
            LanguageModelParam[] languageModelParams, int startIndex);
    private static native int calculateProbabilityNative(long dict, int unigramProbability,
            int bigramProbability);
    private static native String getPropertyNative(long dict, String query);
    private static native boolean isCorruptedNative(long dict);

    // TODO: Move native dict into session
    private final void loadDictionary(final String path, final long startOffset,
            final long length, final boolean isUpdatable) {
        mNativeDict = openNative(path, startOffset, length, isUpdatable);
    }

    // TODO: Check isCorrupted() for main dictionaries.
    public boolean isCorrupted() {
        if (!isValidDictionary()) {
            return false;
        }
        if (!isCorruptedNative(mNativeDict)) {
            return false;
        }
        // TODO: Record the corruption.
        Log.e(TAG, "BinaryDictionary (" + mDictFilePath + ") is corrupted.");
        Log.e(TAG, "locale: " + mLocale);
        Log.e(TAG, "dict size: " + mDictSize);
        Log.e(TAG, "updatable: " + mIsUpdatable);
        return true;
    }

    public DictionaryHeader getHeader() throws UnsupportedFormatException {
        if (mNativeDict == 0) {
            return null;
        }
        final int[] outHeaderSize = new int[1];
        final int[] outFormatVersion = new int[1];
        final ArrayList<int[]> outAttributeKeys = CollectionUtils.newArrayList();
        final ArrayList<int[]> outAttributeValues = CollectionUtils.newArrayList();
        getHeaderInfoNative(mNativeDict, outHeaderSize, outFormatVersion, outAttributeKeys,
                outAttributeValues);
        final HashMap<String, String> attributes = new HashMap<String, String>();
        for (int i = 0; i < outAttributeKeys.size(); i++) {
            final String attributeKey = StringUtils.getStringFromNullTerminatedCodePointArray(
                    outAttributeKeys.get(i));
            final String attributeValue = StringUtils.getStringFromNullTerminatedCodePointArray(
                    outAttributeValues.get(i));
            attributes.put(attributeKey, attributeValue);
        }
        final boolean hasHistoricalInfo = DictionaryHeader.ATTRIBUTE_VALUE_TRUE.equals(
                attributes.get(DictionaryHeader.HAS_HISTORICAL_INFO_KEY));
        return new DictionaryHeader(outHeaderSize[0], new DictionaryOptions(attributes),
                new FormatSpec.FormatOptions(outFormatVersion[0], hasHistoricalInfo));
    }


    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions) {
        return getSuggestionsWithSessionId(composer, prevWord, proximityInfo, blockOffensiveWords,
                additionalFeaturesOptions, 0 /* sessionId */);
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestionsWithSessionId(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions,
            final int sessionId) {
        if (!isValidDictionary()) return null;

        Arrays.fill(mInputCodePoints, Constants.NOT_A_CODE);
        // TODO: toLowerCase in the native code
        final int[] prevWordCodePointArray = (null == prevWord)
                ? null : StringUtils.toCodePointArray(prevWord);
        final int composerSize = composer.size();

        final boolean isGesture = composer.isBatchMode();
        if (composerSize <= 1 || !isGesture) {
            if (composerSize > MAX_WORD_LENGTH - 1) return null;
            for (int i = 0; i < composerSize; i++) {
                mInputCodePoints[i] = composer.getCodeAt(i);
            }
        }

        final InputPointers ips = composer.getInputPointers();
        final int inputSize = isGesture ? ips.getPointerSize() : composerSize;
        mNativeSuggestOptions.setIsGesture(isGesture);
        mNativeSuggestOptions.setAdditionalFeaturesOptions(additionalFeaturesOptions);
        // proximityInfo and/or prevWordForBigrams may not be null.
        getSuggestionsNative(mNativeDict, proximityInfo.getNativeProximityInfo(),
                getTraverseSession(sessionId).getSession(), ips.getXCoordinates(),
                ips.getYCoordinates(), ips.getTimes(), ips.getPointerIds(), mInputCodePoints,
                inputSize, 0 /* commitPoint */, mNativeSuggestOptions.getOptions(),
                prevWordCodePointArray, mOutputSuggestionCount, mOutputCodePoints, mOutputScores,
                mSpaceIndices, mOutputTypes, mOutputAutoCommitFirstWordConfidence);
        final int count = mOutputSuggestionCount[0];
        final ArrayList<SuggestedWordInfo> suggestions = CollectionUtils.newArrayList();
        for (int j = 0; j < count; ++j) {
            final int start = j * MAX_WORD_LENGTH;
            int len = 0;
            while (len < MAX_WORD_LENGTH && mOutputCodePoints[start + len] != 0) {
                ++len;
            }
            if (len > 0) {
                final int flags = mOutputTypes[j] & SuggestedWordInfo.KIND_MASK_FLAGS;
                if (blockOffensiveWords
                        && 0 != (flags & SuggestedWordInfo.KIND_FLAG_POSSIBLY_OFFENSIVE)
                        && 0 == (flags & SuggestedWordInfo.KIND_FLAG_EXACT_MATCH)) {
                    // If we block potentially offensive words, and if the word is possibly
                    // offensive, then we don't output it unless it's also an exact match.
                    continue;
                }
                final int kind = mOutputTypes[j] & SuggestedWordInfo.KIND_MASK_KIND;
                final int score = SuggestedWordInfo.KIND_WHITELIST == kind
                        ? SuggestedWordInfo.MAX_SCORE : mOutputScores[j];
                // TODO: check that all users of the `kind' parameter are ready to accept
                // flags too and pass mOutputTypes[j] instead of kind
                suggestions.add(new SuggestedWordInfo(new String(mOutputCodePoints, start, len),
                        score, kind, this /* sourceDict */,
                        mSpaceIndices[j] /* indexOfTouchPointOfSecondWord */,
                        mOutputAutoCommitFirstWordConfidence[0]));
            }
        }
        return suggestions;
    }

    public boolean isValidDictionary() {
        return mNativeDict != 0;
    }

    public int getFormatVersion() {
        return getFormatVersionNative(mNativeDict);
    }

    @Override
    public boolean isValidWord(final String word) {
        return getFrequency(word) != NOT_A_PROBABILITY;
    }

    @Override
    public int getFrequency(final String word) {
        if (word == null) return NOT_A_PROBABILITY;
        int[] codePoints = StringUtils.toCodePointArray(word);
        return getProbabilityNative(mNativeDict, codePoints);
    }

    // TODO: Add a batch process version (isValidBigramMultiple?) to avoid excessive numbers of jni
    // calls when checking for changes in an entire dictionary.
    public boolean isValidBigram(final String word0, final String word1) {
        return getBigramProbability(word0, word1) != NOT_A_PROBABILITY;
    }

    public int getBigramProbability(final String word0, final String word1) {
        if (TextUtils.isEmpty(word0) || TextUtils.isEmpty(word1)) return NOT_A_PROBABILITY;
        final int[] codePoints0 = StringUtils.toCodePointArray(word0);
        final int[] codePoints1 = StringUtils.toCodePointArray(word1);
        return getBigramProbabilityNative(mNativeDict, codePoints0, codePoints1);
    }

    public WordProperty getWordProperty(final String word) {
        if (TextUtils.isEmpty(word)) {
            return null;
        }
        final int[] codePoints = StringUtils.toCodePointArray(word);
        final int[] outCodePoints = new int[MAX_WORD_LENGTH];
        final boolean[] outFlags = new boolean[FORMAT_WORD_PROPERTY_OUTPUT_FLAG_COUNT];
        final int[] outProbabilityInfo =
                new int[FORMAT_WORD_PROPERTY_OUTPUT_PROBABILITY_INFO_COUNT];
        final ArrayList<int[]> outBigramTargets = CollectionUtils.newArrayList();
        final ArrayList<int[]> outBigramProbabilityInfo = CollectionUtils.newArrayList();
        final ArrayList<int[]> outShortcutTargets = CollectionUtils.newArrayList();
        final ArrayList<Integer> outShortcutProbabilities = CollectionUtils.newArrayList();
        getWordPropertyNative(mNativeDict, codePoints, outCodePoints, outFlags, outProbabilityInfo,
                outBigramTargets, outBigramProbabilityInfo, outShortcutTargets,
                outShortcutProbabilities);
        return new WordProperty(codePoints,
                outFlags[FORMAT_WORD_PROPERTY_IS_NOT_A_WORD_INDEX],
                outFlags[FORMAT_WORD_PROPERTY_IS_BLACKLISTED_INDEX],
                outFlags[FORMAT_WORD_PROPERTY_HAS_BIGRAMS_INDEX],
                outFlags[FORMAT_WORD_PROPERTY_HAS_SHORTCUTS_INDEX], outProbabilityInfo,
                outBigramTargets, outBigramProbabilityInfo, outShortcutTargets,
                outShortcutProbabilities);
    }

    public static class GetNextWordPropertyResult {
        public WordProperty mWordProperty;
        public int mNextToken;

        public GetNextWordPropertyResult(final WordProperty wordPreperty, final int nextToken) {
            mWordProperty = wordPreperty;
            mNextToken = nextToken;
        }
    }

    /**
     * Method to iterate all words in the dictionary for makedict.
     * If token is 0, this method newly starts iterating the dictionary.
     */
    public GetNextWordPropertyResult getNextWordProperty(final int token) {
        final int[] codePoints = new int[MAX_WORD_LENGTH];
        final int nextToken = getNextWordNative(mNativeDict, token, codePoints);
        final String word = StringUtils.getStringFromNullTerminatedCodePointArray(codePoints);
        return new GetNextWordPropertyResult(getWordProperty(word), nextToken);
    }

    // Add a unigram entry to binary dictionary with unigram attributes in native code.
    public void addUnigramWord(final String word, final int probability,
            final String shortcutTarget, final int shortcutProbability, final boolean isNotAWord,
            final boolean isBlacklisted, final int timestamp) {
        if (TextUtils.isEmpty(word)) {
            return;
        }
        final int[] codePoints = StringUtils.toCodePointArray(word);
        final int[] shortcutTargetCodePoints = (shortcutTarget != null) ?
                StringUtils.toCodePointArray(shortcutTarget) : null;
        addUnigramWordNative(mNativeDict, codePoints, probability, shortcutTargetCodePoints,
                shortcutProbability, isNotAWord, isBlacklisted, timestamp);
    }

    // Add a bigram entry to binary dictionary with timestamp in native code.
    public void addBigramWords(final String word0, final String word1, final int probability,
            final int timestamp) {
        if (TextUtils.isEmpty(word0) || TextUtils.isEmpty(word1)) {
            return;
        }
        final int[] codePoints0 = StringUtils.toCodePointArray(word0);
        final int[] codePoints1 = StringUtils.toCodePointArray(word1);
        addBigramWordsNative(mNativeDict, codePoints0, codePoints1, probability, timestamp);
    }

    // Remove a bigram entry form binary dictionary in native code.
    public void removeBigramWords(final String word0, final String word1) {
        if (TextUtils.isEmpty(word0) || TextUtils.isEmpty(word1)) {
            return;
        }
        final int[] codePoints0 = StringUtils.toCodePointArray(word0);
        final int[] codePoints1 = StringUtils.toCodePointArray(word1);
        removeBigramWordsNative(mNativeDict, codePoints0, codePoints1);
    }

    public void addMultipleDictionaryEntries(final LanguageModelParam[] languageModelParams) {
        if (!isValidDictionary()) return;
        int processedParamCount = 0;
        while (processedParamCount < languageModelParams.length) {
            if (needsToRunGC(true /* mindsBlockByGC */)) {
                flushWithGC();
            }
            processedParamCount = addMultipleDictionaryEntriesNative(mNativeDict,
                    languageModelParams, processedParamCount);
            if (processedParamCount <= 0) {
                return;
            }
        }
    }

    private void reopen() {
        close();
        final File dictFile = new File(mDictFilePath);
        // WARNING: Because we pass 0 as the offset and file.length() as the length, this can
        // only be called for actual files. Right now it's only called by the flush() family of
        // functions, which require an updatable dictionary, so it's okay. But beware.
        loadDictionary(dictFile.getAbsolutePath(), 0 /* startOffset */,
                dictFile.length(), mIsUpdatable);
    }

    public void flush() {
        if (!isValidDictionary()) return;
        flushNative(mNativeDict, mDictFilePath);
        reopen();
    }

    public void flushWithGC() {
        if (!isValidDictionary()) return;
        flushWithGCNative(mNativeDict, mDictFilePath);
        reopen();
    }

    /**
     * Checks whether GC is needed to run or not.
     * @param mindsBlockByGC Whether to mind operations blocked by GC. We don't need to care about
     * the blocking in some situations such as in idle time or just before closing.
     * @return whether GC is needed to run or not.
     */
    public boolean needsToRunGC(final boolean mindsBlockByGC) {
        if (!isValidDictionary()) return false;
        return needsToRunGCNative(mNativeDict, mindsBlockByGC);
    }

    @UsedForTesting
    public int calculateProbability(final int unigramProbability, final int bigramProbability) {
        if (!isValidDictionary()) return NOT_A_PROBABILITY;
        return calculateProbabilityNative(mNativeDict, unigramProbability, bigramProbability);
    }

    @UsedForTesting
    public String getPropertyForTest(final String query) {
        if (!isValidDictionary()) return "";
        return getPropertyNative(mNativeDict, query);
    }

    @Override
    public boolean shouldAutoCommit(final SuggestedWordInfo candidate) {
        return candidate.mAutoCommitFirstWordConfidence > CONFIDENCE_TO_AUTO_COMMIT;
    }

    @Override
    public void close() {
        synchronized (mDicTraverseSessions) {
            final int sessionsSize = mDicTraverseSessions.size();
            for (int index = 0; index < sessionsSize; ++index) {
                final DicTraverseSession traverseSession = mDicTraverseSessions.valueAt(index);
                if (traverseSession != null) {
                    traverseSession.close();
                }
            }
            mDicTraverseSessions.clear();
        }
        closeInternalLocked();
    }

    private synchronized void closeInternalLocked() {
        if (mNativeDict != 0) {
            closeNative(mNativeDict);
            mNativeDict = 0;
        }
    }

    // TODO: Manage BinaryDictionary instances without using WeakReference or something.
    @Override
    protected void finalize() throws Throwable {
        try {
            closeInternalLocked();
        } finally {
            super.finalize();
        }
    }
}
