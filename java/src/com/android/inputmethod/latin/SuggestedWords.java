/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.text.TextUtils;
import android.view.inputmethod.CompletionInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class SuggestedWords {
    public static final SuggestedWords EMPTY = new SuggestedWords(
            new ArrayList<SuggestedWordInfo>(0), false, false, false, false, false, false);

    public final boolean mTypedWordValid;
    public final boolean mHasAutoCorrectionCandidate;
    public final boolean mIsPunctuationSuggestions;
    public final boolean mAllowsToBeAutoCorrected;
    public final boolean mIsObsoleteSuggestions;
    public final boolean mIsPrediction;
    private final ArrayList<SuggestedWordInfo> mSuggestedWordInfoList;

    public SuggestedWords(final ArrayList<SuggestedWordInfo> suggestedWordInfoList,
            final boolean typedWordValid,
            final boolean hasAutoCorrectionCandidate,
            final boolean allowsToBeAutoCorrected,
            final boolean isPunctuationSuggestions,
            final boolean isObsoleteSuggestions,
            final boolean isPrediction) {
        mSuggestedWordInfoList = suggestedWordInfoList;
        mTypedWordValid = typedWordValid;
        mHasAutoCorrectionCandidate = hasAutoCorrectionCandidate;
        mAllowsToBeAutoCorrected = allowsToBeAutoCorrected;
        mIsPunctuationSuggestions = isPunctuationSuggestions;
        mIsObsoleteSuggestions = isObsoleteSuggestions;
        mIsPrediction = isPrediction;
    }

    public int size() {
        return mSuggestedWordInfoList.size();
    }

    public CharSequence getWord(int pos) {
        return mSuggestedWordInfoList.get(pos).mWord;
    }

    public SuggestedWordInfo getWordInfo(int pos) {
        return mSuggestedWordInfoList.get(pos);
    }

    public SuggestedWordInfo getInfo(int pos) {
        return mSuggestedWordInfoList.get(pos);
    }

    public boolean hasAutoCorrectionWord() {
        return mHasAutoCorrectionCandidate && size() > 1 && !mTypedWordValid;
    }

    public boolean willAutoCorrect() {
        return !mTypedWordValid && mHasAutoCorrectionCandidate;
    }

    @Override
    public String toString() {
        // Pretty-print method to help debug
        return "SuggestedWords:"
                + " mTypedWordValid=" + mTypedWordValid
                + " mHasAutoCorrectionCandidate=" + mHasAutoCorrectionCandidate
                + " mAllowsToBeAutoCorrected=" + mAllowsToBeAutoCorrected
                + " mIsPunctuationSuggestions=" + mIsPunctuationSuggestions
                + " words=" + Arrays.toString(mSuggestedWordInfoList.toArray());
    }

    public static ArrayList<SuggestedWordInfo> getFromApplicationSpecifiedCompletions(
            final CompletionInfo[] infos) {
        final ArrayList<SuggestedWordInfo> result = new ArrayList<SuggestedWordInfo>();
        for (CompletionInfo info : infos) {
            if (null != info && info.getText() != null) {
                result.add(new SuggestedWordInfo(info.getText(), SuggestedWordInfo.MAX_SCORE));
            }
        }
        return result;
    }

    // Should get rid of the first one (what the user typed previously) from suggestions
    // and replace it with what the user currently typed.
    public static ArrayList<SuggestedWordInfo> getTypedWordAndPreviousSuggestions(
            final CharSequence typedWord, final SuggestedWords previousSuggestions) {
        final ArrayList<SuggestedWordInfo> suggestionsList = new ArrayList<SuggestedWordInfo>();
        final HashSet<String> alreadySeen = new HashSet<String>();
        suggestionsList.add(new SuggestedWordInfo(typedWord, SuggestedWordInfo.MAX_SCORE));
        alreadySeen.add(typedWord.toString());
        final int previousSize = previousSuggestions.size();
        for (int pos = 1; pos < previousSize; pos++) {
            final SuggestedWordInfo prevWordInfo = previousSuggestions.getWordInfo(pos);
            final String prevWord = prevWordInfo.mWord.toString();
            // Filter out duplicate suggestion.
            if (!alreadySeen.contains(prevWord)) {
                suggestionsList.add(prevWordInfo);
                alreadySeen.add(prevWord);
            }
        }
        return suggestionsList;
    }

    public static class SuggestedWordInfo {
        public static final int MAX_SCORE = Integer.MAX_VALUE;
        private final String mWordStr;
        public final CharSequence mWord;
        public final int mScore;
        public final int mCodePointCount;
        private String mDebugString = "";

        public SuggestedWordInfo(final CharSequence word, final int score) {
            mWordStr = word.toString();
            mWord = word;
            mScore = score;
            mCodePointCount = mWordStr.codePointCount(0, mWordStr.length());
        }


        public void setDebugString(String str) {
            if (null == str) throw new NullPointerException("Debug info is null");
            mDebugString = str;
        }

        public String getDebugString() {
            return mDebugString;
        }

        public int codePointCount() {
            return mCodePointCount;
        }

        public int codePointAt(int i) {
            return mWordStr.codePointAt(i);
        }

        @Override
        public String toString() {
            if (TextUtils.isEmpty(mDebugString)) {
                return mWordStr;
            } else {
                return mWordStr + " (" + mDebugString.toString() + ")";
            }
        }

        // TODO: Consolidate this method and StringUtils.removeDupes() in the future.
        public static void removeDups(ArrayList<SuggestedWordInfo> candidates) {
            if (candidates.size() <= 1) {
                return;
            }
            int i = 1;
            while(i < candidates.size()) {
                final SuggestedWordInfo cur = candidates.get(i);
                for (int j = 0; j < i; ++j) {
                    final SuggestedWordInfo previous = candidates.get(j);
                    if (TextUtils.equals(cur.mWord, previous.mWord)) {
                        candidates.remove(cur.mScore < previous.mScore ? i : j);
                        --i;
                        break;
                    }
                }
                ++i;
            }
        }
    }
}
