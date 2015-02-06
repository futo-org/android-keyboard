/*
 * Copyright (C) 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin;

import com.android.inputmethod.latin.common.NativeSuggestOptions;
import com.android.inputmethod.latin.define.DecoderSpecificConstants;
import com.android.inputmethod.latin.utils.JniUtils;

import java.util.Locale;

public final class DicTraverseSession {
    static {
        JniUtils.loadNativeLibrary();
    }
    // Must be equal to MAX_RESULTS in native/jni/src/defines.h
    private static final int MAX_RESULTS = 18;
    public final int[] mInputCodePoints =
            new int[DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH];
    public final int[][] mPrevWordCodePointArrays =
            new int[DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM][];
    public final boolean[] mIsBeginningOfSentenceArray =
            new boolean[DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM];
    public final int[] mOutputSuggestionCount = new int[1];
    public final int[] mOutputCodePoints =
            new int[DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH * MAX_RESULTS];
    public final int[] mSpaceIndices = new int[MAX_RESULTS];
    public final int[] mOutputScores = new int[MAX_RESULTS];
    public final int[] mOutputTypes = new int[MAX_RESULTS];
    // Only one result is ever used
    public final int[] mOutputAutoCommitFirstWordConfidence = new int[1];
    public final float[] mInputOutputWeightOfLangModelVsSpatialModel = new float[1];

    public final NativeSuggestOptions mNativeSuggestOptions = new NativeSuggestOptions();

    private static native long setDicTraverseSessionNative(String locale, long dictSize);
    private static native void initDicTraverseSessionNative(long nativeDicTraverseSession,
            long dictionary, int[] previousWord, int previousWordLength);
    private static native void releaseDicTraverseSessionNative(long nativeDicTraverseSession);

    private long mNativeDicTraverseSession;

    public DicTraverseSession(Locale locale, long dictionary, long dictSize) {
        mNativeDicTraverseSession = createNativeDicTraverseSession(
                locale != null ? locale.toString() : "", dictSize);
        initSession(dictionary);
    }

    public long getSession() {
        return mNativeDicTraverseSession;
    }

    public void initSession(long dictionary) {
        initSession(dictionary, null, 0);
    }

    public void initSession(long dictionary, int[] previousWord, int previousWordLength) {
        initDicTraverseSessionNative(
                mNativeDicTraverseSession, dictionary, previousWord, previousWordLength);
    }

    private static long createNativeDicTraverseSession(String locale, long dictSize) {
        return setDicTraverseSessionNative(locale, dictSize);
    }

    private void closeInternal() {
        if (mNativeDicTraverseSession != 0) {
            releaseDicTraverseSessionNative(mNativeDicTraverseSession);
            mNativeDicTraverseSession = 0;
        }
    }

    public void close() {
        closeInternal();
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
