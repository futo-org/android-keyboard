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
import com.android.inputmethod.latin.makedict.FormatSpec.DictionaryOptions;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.WordProperty;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import com.android.inputmethod.latin.utils.FileUtils;
import com.android.inputmethod.latin.utils.JniUtils;
import com.android.inputmethod.latin.utils.LanguageModelParam;
import com.android.inputmethod.latin.utils.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Implements a static, compacted, binary dictionary of standard words.
 */
// TODO: All methods which should be locked need to have a suffix "Locked".
public final class BinaryDictionary extends Dictionary {
    private static final String TAG = BinaryDictionary.class.getSimpleName();

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
    private static final int FORMAT_WORD_PROPERTY_OUTPUT_FLAG_COUNT = 5;
    private static final int FORMAT_WORD_PROPERTY_IS_NOT_A_WORD_INDEX = 0;
    private static final int FORMAT_WORD_PROPERTY_IS_BLACKLISTED_INDEX = 1;
    private static final int FORMAT_WORD_PROPERTY_HAS_BIGRAMS_INDEX = 2;
    private static final int FORMAT_WORD_PROPERTY_HAS_SHORTCUTS_INDEX = 3;
    private static final int FORMAT_WORD_PROPERTY_IS_BEGINNING_OF_SENTENCE_INDEX = 4;

    // Format to get probability and historical info from native side via getWordPropertyNative().
    public static final int FORMAT_WORD_PROPERTY_OUTPUT_PROBABILITY_INFO_COUNT = 4;
    public static final int FORMAT_WORD_PROPERTY_PROBABILITY_INDEX = 0;
    public static final int FORMAT_WORD_PROPERTY_TIMESTAMP_INDEX = 1;
    public static final int FORMAT_WORD_PROPERTY_LEVEL_INDEX = 2;
    public static final int FORMAT_WORD_PROPERTY_COUNT_INDEX = 3;

    public static final String DICT_FILE_NAME_SUFFIX_FOR_MIGRATION = ".migrate";
    public static final String DIR_NAME_SUFFIX_FOR_RECORD_MIGRATION = ".migrating";

    private long mNativeDict;
    private final Locale mLocale;
    private final long mDictSize;
    private final String mDictFilePath;
    private final boolean mUseFullEditDistance;
    private final boolean mIsUpdatable;
    private boolean mHasUpdated;

    private final SparseArray<DicTraverseSession> mDicTraverseSessions = new SparseArray<>();

    // TODO: There should be a way to remove used DicTraverseSession objects from
    // {@code mDicTraverseSessions}.
    private DicTraverseSession getTraverseSession(final int traverseSessionId) {
        synchronized(mDicTraverseSessions) {
            DicTraverseSession traverseSession = mDicTraverseSessions.get(traverseSessionId);
            if (traverseSession == null) {
                traverseSession = new DicTraverseSession(mLocale, mNativeDict, mDictSize);
                mDicTraverseSessions.put(traverseSessionId, traverseSession);
            }
            return traverseSession;
        }
    }

    /**
     * Constructs binary dictionary using existing dictionary file.
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
        mHasUpdated = false;
        mUseFullEditDistance = useFullEditDistance;
        loadDictionary(filename, offset, length, isUpdatable);
    }

    /**
     * Constructs binary dictionary on memory.
     * @param filename the name of the file used to flush.
     * @param useFullEditDistance whether to use the full edit distance in suggestions
     * @param dictType the dictionary type, as a human-readable string
     * @param formatVersion the format version of the dictionary
     * @param attributeMap the attributes of the dictionary
     */
    public BinaryDictionary(final String filename, final boolean useFullEditDistance,
            final Locale locale, final String dictType, final long formatVersion,
            final Map<String, String> attributeMap) {
        super(dictType);
        mLocale = locale;
        mDictSize = 0;
        mDictFilePath = filename;
        // On memory dictionary is always updatable.
        mIsUpdatable = true;
        mHasUpdated = false;
        mUseFullEditDistance = useFullEditDistance;
        final String[] keyArray = new String[attributeMap.size()];
        final String[] valueArray = new String[attributeMap.size()];
        int index = 0;
        for (final String key : attributeMap.keySet()) {
            keyArray[index] = key;
            valueArray[index] = attributeMap.get(key);
            index++;
        }
        mNativeDict = createOnMemoryNative(formatVersion, locale.toString(), keyArray, valueArray);
    }


    static {
        JniUtils.loadNativeLibrary();
    }

    private static native long openNative(String sourceDir, long dictOffset, long dictSize,
            boolean isUpdatable);
    private static native long createOnMemoryNative(long formatVersion,
            String locale, String[] attributeKeyStringArray, String[] attributeValueStringArray);
    private static native void getHeaderInfoNative(long dict, int[] outHeaderSize,
            int[] outFormatVersion, ArrayList<int[]> outAttributeKeys,
            ArrayList<int[]> outAttributeValues);
    private static native boolean flushNative(long dict, String filePath);
    private static native boolean needsToRunGCNative(long dict, boolean mindsBlockByGC);
    private static native boolean flushWithGCNative(long dict, String filePath);
    private static native void closeNative(long dict);
    private static native int getFormatVersionNative(long dict);
    private static native int getProbabilityNative(long dict, int[] word);
    private static native int getMaxProbabilityOfExactMatchesNative(long dict, int[] word);
    private static native int getNgramProbabilityNative(long dict, int[][] prevWordCodePointArrays,
            boolean[] isBeginningOfSentenceArray, int[] word);
    private static native void getWordPropertyNative(long dict, int[] word,
            boolean isBeginningOfSentence, int[] outCodePoints, boolean[] outFlags,
            int[] outProbabilityInfo, ArrayList<int[]> outBigramTargets,
            ArrayList<int[]> outBigramProbabilityInfo, ArrayList<int[]> outShortcutTargets,
            ArrayList<Integer> outShortcutProbabilities);
    private static native int getNextWordNative(long dict, int token, int[] outCodePoints,
            boolean[] outIsBeginningOfSentence);
    private static native void getSuggestionsNative(long dict, long proximityInfo,
            long traverseSession, int[] xCoordinates, int[] yCoordinates, int[] times,
            int[] pointerIds, int[] inputCodePoints, int inputSize, int[] suggestOptions,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int[] outputSuggestionCount, int[] outputCodePoints, int[] outputScores,
            int[] outputIndices, int[] outputTypes, int[] outputAutoCommitFirstWordConfidence,
            float[] inOutLanguageWeight);
    private static native boolean addUnigramEntryNative(long dict, int[] word, int probability,
            int[] shortcutTarget, int shortcutProbability, boolean isBeginningOfSentence,
            boolean isNotAWord, boolean isBlacklisted, int timestamp);
    private static native boolean removeUnigramEntryNative(long dict, int[] word);
    private static native boolean addNgramEntryNative(long dict,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int[] word, int probability, int timestamp);
    private static native boolean removeNgramEntryNative(long dict,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray, int[] word);
    private static native int addMultipleDictionaryEntriesNative(long dict,
            LanguageModelParam[] languageModelParams, int startIndex);
    private static native String getPropertyNative(long dict, String query);
    private static native boolean isCorruptedNative(long dict);
    private static native boolean migrateNative(long dict, String dictFilePath,
            long newFormatVersion);

    // TODO: Move native dict into session
    private final void loadDictionary(final String path, final long startOffset,
            final long length, final boolean isUpdatable) {
        mHasUpdated = false;
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
        final ArrayList<int[]> outAttributeKeys = new ArrayList<>();
        final ArrayList<int[]> outAttributeValues = new ArrayList<>();
        getHeaderInfoNative(mNativeDict, outHeaderSize, outFormatVersion, outAttributeKeys,
                outAttributeValues);
        final HashMap<String, String> attributes = new HashMap<>();
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
            final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final int sessionId, final float[] inOutLanguageWeight) {
        if (!isValidDictionary()) {
            return null;
        }
        final DicTraverseSession session = getTraverseSession(sessionId);
        Arrays.fill(session.mInputCodePoints, Constants.NOT_A_CODE);
        prevWordsInfo.outputToArray(session.mPrevWordCodePointArrays,
                session.mIsBeginningOfSentenceArray);
        final InputPointers inputPointers = composer.getInputPointers();
        final boolean isGesture = composer.isBatchMode();
        final int inputSize;
        if (!isGesture) {
            inputSize = composer.copyCodePointsExceptTrailingSingleQuotesAndReturnCodePointCount(
                    session.mInputCodePoints);
            if (inputSize < 0) {
                return null;
            }
        } else {
            inputSize = inputPointers.getPointerSize();
        }
        session.mNativeSuggestOptions.setUseFullEditDistance(mUseFullEditDistance);
        session.mNativeSuggestOptions.setIsGesture(isGesture);
        session.mNativeSuggestOptions.setBlockOffensiveWords(
                settingsValuesForSuggestion.mBlockPotentiallyOffensive);
        session.mNativeSuggestOptions.setSpaceAwareGestureEnabled(
                settingsValuesForSuggestion.mSpaceAwareGestureEnabled);
        session.mNativeSuggestOptions.setAdditionalFeaturesOptions(
                settingsValuesForSuggestion.mAdditionalFeaturesSettingValues);
        if (inOutLanguageWeight != null) {
            session.mInputOutputLanguageWeight[0] = inOutLanguageWeight[0];
        } else {
            session.mInputOutputLanguageWeight[0] = Dictionary.NOT_A_LANGUAGE_WEIGHT;
        }
        // TOOD: Pass multiple previous words information for n-gram.
        getSuggestionsNative(mNativeDict, proximityInfo.getNativeProximityInfo(),
                getTraverseSession(sessionId).getSession(), inputPointers.getXCoordinates(),
                inputPointers.getYCoordinates(), inputPointers.getTimes(),
                inputPointers.getPointerIds(), session.mInputCodePoints, inputSize,
                session.mNativeSuggestOptions.getOptions(), session.mPrevWordCodePointArrays,
                session.mIsBeginningOfSentenceArray, session.mOutputSuggestionCount,
                session.mOutputCodePoints, session.mOutputScores, session.mSpaceIndices,
                session.mOutputTypes, session.mOutputAutoCommitFirstWordConfidence,
                session.mInputOutputLanguageWeight);
        if (inOutLanguageWeight != null) {
            inOutLanguageWeight[0] = session.mInputOutputLanguageWeight[0];
        }
        final int count = session.mOutputSuggestionCount[0];
        final ArrayList<SuggestedWordInfo> suggestions = new ArrayList<>();
        for (int j = 0; j < count; ++j) {
            final int start = j * Constants.DICTIONARY_MAX_WORD_LENGTH;
            int len = 0;
            while (len < Constants.DICTIONARY_MAX_WORD_LENGTH
                    && session.mOutputCodePoints[start + len] != 0) {
                ++len;
            }
            if (len > 0) {
                suggestions.add(new SuggestedWordInfo(
                        new String(session.mOutputCodePoints, start, len),
                        session.mOutputScores[j], session.mOutputTypes[j], this /* sourceDict */,
                        session.mSpaceIndices[j] /* indexOfTouchPointOfSecondWord */,
                        session.mOutputAutoCommitFirstWordConfidence[0]));
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
    public boolean isInDictionary(final String word) {
        return getFrequency(word) != NOT_A_PROBABILITY;
    }

    @Override
    public int getFrequency(final String word) {
        if (TextUtils.isEmpty(word)) return NOT_A_PROBABILITY;
        int[] codePoints = StringUtils.toCodePointArray(word);
        return getProbabilityNative(mNativeDict, codePoints);
    }

    @Override
    public int getMaxFrequencyOfExactMatches(final String word) {
        if (TextUtils.isEmpty(word)) return NOT_A_PROBABILITY;
        int[] codePoints = StringUtils.toCodePointArray(word);
        return getMaxProbabilityOfExactMatchesNative(mNativeDict, codePoints);
    }

    @UsedForTesting
    public boolean isValidNgram(final PrevWordsInfo prevWordsInfo, final String word) {
        return getNgramProbability(prevWordsInfo, word) != NOT_A_PROBABILITY;
    }

    public int getNgramProbability(final PrevWordsInfo prevWordsInfo, final String word) {
        if (!prevWordsInfo.isValid() || TextUtils.isEmpty(word)) {
            return NOT_A_PROBABILITY;
        }
        final int[][] prevWordCodePointArrays = new int[Constants.MAX_PREV_WORD_COUNT_FOR_N_GRAM][];
        final boolean[] isBeginningOfSentenceArray =
                new boolean[Constants.MAX_PREV_WORD_COUNT_FOR_N_GRAM];
        prevWordsInfo.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray);
        final int[] wordCodePoints = StringUtils.toCodePointArray(word);
        return getNgramProbabilityNative(mNativeDict, prevWordCodePointArrays,
                isBeginningOfSentenceArray, wordCodePoints);
    }

    public WordProperty getWordProperty(final String word, final boolean isBeginningOfSentence) {
        if (word == null) {
            return null;
        }
        final int[] codePoints = StringUtils.toCodePointArray(word);
        final int[] outCodePoints = new int[Constants.DICTIONARY_MAX_WORD_LENGTH];
        final boolean[] outFlags = new boolean[FORMAT_WORD_PROPERTY_OUTPUT_FLAG_COUNT];
        final int[] outProbabilityInfo =
                new int[FORMAT_WORD_PROPERTY_OUTPUT_PROBABILITY_INFO_COUNT];
        final ArrayList<int[]> outBigramTargets = new ArrayList<>();
        final ArrayList<int[]> outBigramProbabilityInfo = new ArrayList<>();
        final ArrayList<int[]> outShortcutTargets = new ArrayList<>();
        final ArrayList<Integer> outShortcutProbabilities = new ArrayList<>();
        getWordPropertyNative(mNativeDict, codePoints, isBeginningOfSentence, outCodePoints,
                outFlags, outProbabilityInfo, outBigramTargets, outBigramProbabilityInfo,
                outShortcutTargets, outShortcutProbabilities);
        return new WordProperty(codePoints,
                outFlags[FORMAT_WORD_PROPERTY_IS_NOT_A_WORD_INDEX],
                outFlags[FORMAT_WORD_PROPERTY_IS_BLACKLISTED_INDEX],
                outFlags[FORMAT_WORD_PROPERTY_HAS_BIGRAMS_INDEX],
                outFlags[FORMAT_WORD_PROPERTY_HAS_SHORTCUTS_INDEX],
                outFlags[FORMAT_WORD_PROPERTY_IS_BEGINNING_OF_SENTENCE_INDEX], outProbabilityInfo,
                outBigramTargets, outBigramProbabilityInfo, outShortcutTargets,
                outShortcutProbabilities);
    }

    public static class GetNextWordPropertyResult {
        public WordProperty mWordProperty;
        public int mNextToken;

        public GetNextWordPropertyResult(final WordProperty wordProperty, final int nextToken) {
            mWordProperty = wordProperty;
            mNextToken = nextToken;
        }
    }

    /**
     * Method to iterate all words in the dictionary for makedict.
     * If token is 0, this method newly starts iterating the dictionary.
     */
    public GetNextWordPropertyResult getNextWordProperty(final int token) {
        final int[] codePoints = new int[Constants.DICTIONARY_MAX_WORD_LENGTH];
        final boolean[] isBeginningOfSentence = new boolean[1];
        final int nextToken = getNextWordNative(mNativeDict, token, codePoints,
                isBeginningOfSentence);
        final String word = StringUtils.getStringFromNullTerminatedCodePointArray(codePoints);
        return new GetNextWordPropertyResult(
                getWordProperty(word, isBeginningOfSentence[0]), nextToken);
    }

    // Add a unigram entry to binary dictionary with unigram attributes in native code.
    public boolean addUnigramEntry(final String word, final int probability,
            final String shortcutTarget, final int shortcutProbability,
            final boolean isBeginningOfSentence, final boolean isNotAWord,
            final boolean isBlacklisted, final int timestamp) {
        if (word == null || (word.isEmpty() && !isBeginningOfSentence)) {
            return false;
        }
        final int[] codePoints = StringUtils.toCodePointArray(word);
        final int[] shortcutTargetCodePoints = (shortcutTarget != null) ?
                StringUtils.toCodePointArray(shortcutTarget) : null;
        if (!addUnigramEntryNative(mNativeDict, codePoints, probability, shortcutTargetCodePoints,
                shortcutProbability, isBeginningOfSentence, isNotAWord, isBlacklisted, timestamp)) {
            return false;
        }
        mHasUpdated = true;
        return true;
    }

    // Remove a unigram entry from the binary dictionary in native code.
    public boolean removeUnigramEntry(final String word) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        final int[] codePoints = StringUtils.toCodePointArray(word);
        if (!removeUnigramEntryNative(mNativeDict, codePoints)) {
            return false;
        }
        mHasUpdated = true;
        return true;
    }

    // Add an n-gram entry to the binary dictionary with timestamp in native code.
    public boolean addNgramEntry(final PrevWordsInfo prevWordsInfo, final String word,
            final int probability, final int timestamp) {
        if (!prevWordsInfo.isValid() || TextUtils.isEmpty(word)) {
            return false;
        }
        final int[][] prevWordCodePointArrays = new int[Constants.MAX_PREV_WORD_COUNT_FOR_N_GRAM][];
        final boolean[] isBeginningOfSentenceArray =
                new boolean[Constants.MAX_PREV_WORD_COUNT_FOR_N_GRAM];
        prevWordsInfo.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray);
        final int[] wordCodePoints = StringUtils.toCodePointArray(word);
        if (!addNgramEntryNative(mNativeDict, prevWordCodePointArrays,
                isBeginningOfSentenceArray, wordCodePoints, probability, timestamp)) {
            return false;
        }
        mHasUpdated = true;
        return true;
    }

    // Remove an n-gram entry from the binary dictionary in native code.
    public boolean removeNgramEntry(final PrevWordsInfo prevWordsInfo, final String word) {
        if (!prevWordsInfo.isValid() || TextUtils.isEmpty(word)) {
            return false;
        }
        final int[][] prevWordCodePointArrays = new int[Constants.MAX_PREV_WORD_COUNT_FOR_N_GRAM][];
        final boolean[] isBeginningOfSentenceArray =
                new boolean[Constants.MAX_PREV_WORD_COUNT_FOR_N_GRAM];
        prevWordsInfo.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray);
        final int[] wordCodePoints = StringUtils.toCodePointArray(word);
        if (!removeNgramEntryNative(mNativeDict, prevWordCodePointArrays,
                isBeginningOfSentenceArray, wordCodePoints)) {
            return false;
        }
        mHasUpdated = true;
        return true;
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
            mHasUpdated = true;
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

    // Flush to dict file if the dictionary has been updated.
    public boolean flush() {
        if (!isValidDictionary()) return false;
        if (mHasUpdated) {
            if (!flushNative(mNativeDict, mDictFilePath)) {
                return false;
            }
            reopen();
        }
        return true;
    }

    // Run GC and flush to dict file if the dictionary has been updated.
    public boolean flushWithGCIfHasUpdated() {
        if (mHasUpdated) {
            return flushWithGC();
        }
        return true;
    }

    // Run GC and flush to dict file.
    public boolean flushWithGC() {
        if (!isValidDictionary()) return false;
        if (!flushWithGCNative(mNativeDict, mDictFilePath)) {
            return false;
        }
        reopen();
        return true;
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

    public boolean migrateTo(final int newFormatVersion) {
        if (!isValidDictionary()) {
            return false;
        }
        final File isMigratingDir =
                new File(mDictFilePath + DIR_NAME_SUFFIX_FOR_RECORD_MIGRATION);
        if (isMigratingDir.exists()) {
            isMigratingDir.delete();
            Log.e(TAG, "Previous migration attempt failed probably due to a crash. "
                        + "Giving up using the old dictionary (" + mDictFilePath + ").");
            return false;
        }
        if (!isMigratingDir.mkdir()) {
            Log.e(TAG, "Cannot create a dir (" + isMigratingDir.getAbsolutePath()
                    + ") to record migration.");
            return false;
        }
        try {
            final String tmpDictFilePath = mDictFilePath + DICT_FILE_NAME_SUFFIX_FOR_MIGRATION;
            if (!migrateNative(mNativeDict, tmpDictFilePath, newFormatVersion)) {
                return false;
            }
            close();
            final File dictFile = new File(mDictFilePath);
            final File tmpDictFile = new File(tmpDictFilePath);
            if (!FileUtils.deleteRecursively(dictFile)) {
                return false;
            }
            if (!BinaryDictionaryUtils.renameDict(tmpDictFile, dictFile)) {
                return false;
            }
            loadDictionary(dictFile.getAbsolutePath(), 0 /* startOffset */,
                    dictFile.length(), mIsUpdatable);
            return true;
        } finally {
            isMigratingDir.delete();
        }
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
