/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.util.Log;

import java.util.ArrayList;

public class AutoCorrection {
    private static final boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = AutoCorrection.class.getSimpleName();
    private boolean mHasAutoCorrection;
    private CharSequence mAutoCorrectionWord;
    private double mNormalizedScore;

    public void init() {
        mHasAutoCorrection = false;
        mAutoCorrectionWord = null;
        mNormalizedScore = Integer.MIN_VALUE;
    }

    public boolean hasAutoCorrection() {
        return mHasAutoCorrection;
    }

    public CharSequence getAutoCorrectionWord() {
        return mAutoCorrectionWord;
    }

    public double getNormalizedScore() {
        return mNormalizedScore;
    }

    public void updateAutoCorrectionStatus(Suggest suggest,
            WordComposer wordComposer, ArrayList<CharSequence> suggestions, int[] priorities,
            CharSequence typedWord, double autoCorrectionThreshold, int correctionMode,
            CharSequence quickFixedWord) {
        if (hasAutoCorrectionForTypedWord(
                suggest, wordComposer, suggestions, typedWord, correctionMode)) {
            mHasAutoCorrection = true;
            mAutoCorrectionWord = typedWord;
        } else if (hasAutoCorrectForBinaryDictionary(wordComposer, suggestions, correctionMode,
                priorities, typedWord, autoCorrectionThreshold)) {
            mHasAutoCorrection = true;
            mAutoCorrectionWord = suggestions.get(0);
        } else if (hasAutoCorrectionForQuickFix(quickFixedWord)) {
            mHasAutoCorrection = true;
            mAutoCorrectionWord = quickFixedWord;
        }
    }

    private boolean hasAutoCorrectionForTypedWord(Suggest suggest, WordComposer wordComposer,
            ArrayList<CharSequence> suggestions, CharSequence typedWord, int correctionMode) {
        return wordComposer.size() > 1 && suggestions.size() > 0 && suggest.isValidWord(typedWord)
                && (correctionMode == Suggest.CORRECTION_FULL
                || correctionMode == Suggest.CORRECTION_FULL_BIGRAM);
    }

    private boolean hasAutoCorrectForBinaryDictionary(WordComposer wordComposer,
            ArrayList<CharSequence> suggestions, int correctionMode, int[] priorities,
            CharSequence typedWord, double autoCorrectionThreshold) {
        if (wordComposer.size() > 1 && (correctionMode == Suggest.CORRECTION_FULL
                || correctionMode == Suggest.CORRECTION_FULL_BIGRAM)
                && typedWord != null && suggestions.size() > 0 && priorities.length > 0) {
            final CharSequence autoCorrectionCandidate = suggestions.get(0);
            final int autoCorrectionCandidateScore = priorities[0];
            // TODO: when the normalized score of the first suggestion is nearly equals to
            //       the normalized score of the second suggestion, behave less aggressive.
            mNormalizedScore = Utils.calcNormalizedScore(
                    typedWord,autoCorrectionCandidate, autoCorrectionCandidateScore);
            if (DBG) {
                Log.d(TAG, "Normalized " + typedWord + "," + autoCorrectionCandidate + ","
                        + autoCorrectionCandidateScore + ", " + mNormalizedScore
                        + "(" + autoCorrectionThreshold + ")");
            }
            if (mNormalizedScore >= autoCorrectionThreshold) {
                if (DBG) {
                    Log.d(TAG, "Auto corrected by S-threshold.");
                }
                return true;
            }
        }
        return false;
    }

    private boolean hasAutoCorrectionForQuickFix(CharSequence quickFixedWord) {
        return quickFixedWord != null;
    }
}
