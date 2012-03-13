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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class SuggestedWords {
    public static final SuggestedWords EMPTY = new SuggestedWords(false, false, false, false,
            Collections.<SuggestedWordInfo>emptyList());

    public final boolean mTypedWordValid;
    public final boolean mHasAutoCorrectionCandidate;
    public final boolean mIsPunctuationSuggestions;
    private final List<SuggestedWordInfo> mSuggestedWordInfoList;

    SuggestedWords(boolean typedWordValid,
            boolean hasAutoCorrectionCandidate, boolean isPunctuationSuggestions,
            boolean shouldBlockAutoCorrectionBySafetyNet,
            List<SuggestedWordInfo> suggestedWordInfoList) {
        mTypedWordValid = typedWordValid;
        mHasAutoCorrectionCandidate = hasAutoCorrectionCandidate
                && !shouldBlockAutoCorrectionBySafetyNet;
        mIsPunctuationSuggestions = isPunctuationSuggestions;
        mSuggestedWordInfoList = suggestedWordInfoList;
    }

    public int size() {
        return mSuggestedWordInfoList.size();
    }

    public CharSequence getWord(int pos) {
        return mSuggestedWordInfoList.get(pos).mWord;
    }

    public SuggestedWordInfo getInfo(int pos) {
        return mSuggestedWordInfoList != null ? mSuggestedWordInfoList.get(pos) : null;
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
                + " mIsPunctuationSuggestions=" + mIsPunctuationSuggestions;
    }

    public static class Builder {
        private boolean mTypedWordValid;
        private boolean mHasMinimalSuggestion;
        private boolean mIsPunctuationSuggestions;
        private boolean mShouldBlockAutoCorrectionBySafetyNet;
        private boolean mAllowsToBeAutoCorrected;
        private boolean mHasAutoCorrection;
        private List<SuggestedWordInfo> mSuggestedWordInfoList =
                new ArrayList<SuggestedWordInfo>();

        public Builder() {
            // Nothing to do here.
        }

        // TODO: compatibility for tests. Remove this once tests are okay.
        public Builder addWords(List<SuggestedWordInfo> suggestedWordInfoList) {
            return setWords(suggestedWordInfoList);
        }

        public Builder setWords(List<SuggestedWordInfo> suggestedWordInfoList) {
            mSuggestedWordInfoList = suggestedWordInfoList;
            return this;
        }

        /* package for tests */
        Builder addWord(CharSequence word, SuggestedWordInfo suggestedWordInfo) {
            if (!TextUtils.isEmpty(suggestedWordInfo.mWord)) {
                // It's okay if suggestedWordInfo is null since it's checked where it's used.
                mSuggestedWordInfoList.add(suggestedWordInfo);
            }
            return this;
        }

        public static List<SuggestedWordInfo> getFromCharSequenceList(
                final List<CharSequence> wordList) {
            final ArrayList<SuggestedWordInfo> result = new ArrayList<SuggestedWordInfo>();
            for (CharSequence word : wordList) {
                if (null != word) result.add(new SuggestedWordInfo(word, null, false));
            }
            return result;
        }

        public static List<SuggestedWordInfo> getFromApplicationSpecifiedCompletions(
                final CompletionInfo[] infos) {
            final ArrayList<SuggestedWordInfo> result = new ArrayList<SuggestedWordInfo>();
            for (CompletionInfo info : infos) {
                if (null != info) result.add(new SuggestedWordInfo(info.getText(), null, false));
            }
            return result;
        }

        public Builder setTypedWordValid(boolean typedWordValid) {
            mTypedWordValid = typedWordValid;
            return this;
        }

        public Builder setHasMinimalSuggestion(boolean hasMinimalSuggestion) {
            mHasMinimalSuggestion = hasMinimalSuggestion;
            return this;
        }

        public Builder setIsPunctuationSuggestions() {
            mIsPunctuationSuggestions = true;
            return this;
        }

        public Builder setShouldBlockAutoCorrectionBySafetyNet() {
            mShouldBlockAutoCorrectionBySafetyNet = true;
            return this;
        }

        public Builder setAllowsToBeAutoCorrected(final boolean allowsToBeAutoCorrected) {
            mAllowsToBeAutoCorrected = allowsToBeAutoCorrected;
            return this;
        }

        public Builder setHasAutoCorrection(final boolean hasAutoCorrection) {
            mHasAutoCorrection = hasAutoCorrection;
            return this;
        }

        // Should get rid of the first one (what the user typed previously) from suggestions
        // and replace it with what the user currently typed.
        public static ArrayList<SuggestedWordInfo> getTypedWordAndPreviousSuggestions(
                final CharSequence typedWord, final SuggestedWords previousSuggestions) {
            final ArrayList<SuggestedWordInfo> suggestionsList = new ArrayList<SuggestedWordInfo>();
            final HashSet<String> alreadySeen = new HashSet<String>();
            suggestionsList.add(new SuggestedWordInfo(typedWord, null, false));
            alreadySeen.add(typedWord.toString());
            final int previousSize = previousSuggestions.size();
            for (int pos = 1; pos < previousSize; pos++) {
                final String prevWord = previousSuggestions.getWord(pos).toString();
                // Filter out duplicate suggestion.
                if (!alreadySeen.contains(prevWord)) {
                    suggestionsList.add(new SuggestedWordInfo(prevWord, null, true));
                    alreadySeen.add(prevWord);
                }
            }
            return suggestionsList;
        }

        public SuggestedWords build() {
            return new SuggestedWords(mTypedWordValid, mHasMinimalSuggestion,
                    mIsPunctuationSuggestions, mShouldBlockAutoCorrectionBySafetyNet,
                    mSuggestedWordInfoList);
        }

        public int size() {
            return mSuggestedWordInfoList.size();
        }

        public CharSequence getWord(int pos) {
            return mSuggestedWordInfoList.get(pos).mWord;
        }

        public boolean allowsToBeAutoCorrected() {
            return mAllowsToBeAutoCorrected;
        }

        @Override
        public String toString() {
            // Pretty-print method to help debug
            return "SuggestedWords.Builder:"
                    + " mTypedWordValid=" + mTypedWordValid
                    + " mHasMinimalSuggestion=" + mHasMinimalSuggestion
                    + " mIsPunctuationSuggestions=" + mIsPunctuationSuggestions
                    + " mShouldBlockAutoCorrectionBySafetyNet="
                    + mShouldBlockAutoCorrectionBySafetyNet;
        }
    }

    public static class SuggestedWordInfo {
        private final CharSequence mWord;
        private final CharSequence mDebugString;
        private final boolean mPreviousSuggestedWord;

        public SuggestedWordInfo(final CharSequence word) {
            mWord = word;
            mDebugString = "";
            mPreviousSuggestedWord = false;
        }

        public SuggestedWordInfo(final CharSequence word, final CharSequence debugString,
                final boolean previousSuggestedWord) {
            mWord = word;
            mDebugString = debugString;
            mPreviousSuggestedWord = previousSuggestedWord;
        }

        public String getDebugString() {
            if (mDebugString == null) {
                return "";
            } else {
                return mDebugString.toString();
            }
        }

        public boolean isObsoleteSuggestedWord () {
            return mPreviousSuggestedWord;
        }
    }
}
