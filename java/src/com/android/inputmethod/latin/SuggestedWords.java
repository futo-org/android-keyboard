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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class SuggestedWords {
    public static final SuggestedWords EMPTY = new SuggestedWords(
            Collections.<SuggestedWordInfo>emptyList(), false, false, false, false, false);

    public final boolean mTypedWordValid;
    public final boolean mHasAutoCorrectionCandidate;
    public final boolean mIsPunctuationSuggestions;
    public final boolean mAllowsToBeAutoCorrected;
    public final boolean mIsObsoleteSuggestions;
    private final List<SuggestedWordInfo> mSuggestedWordInfoList;

    public SuggestedWords(final List<SuggestedWordInfo> suggestedWordInfoList,
            final boolean typedWordValid,
            final boolean hasAutoCorrectionCandidate,
            final boolean allowsToBeAutoCorrected,
            final boolean isPunctuationSuggestions,
            final boolean isObsoleteSuggestions) {
        mSuggestedWordInfoList = suggestedWordInfoList;
        mTypedWordValid = typedWordValid;
        mHasAutoCorrectionCandidate = hasAutoCorrectionCandidate;
        mAllowsToBeAutoCorrected = allowsToBeAutoCorrected;
        mIsPunctuationSuggestions = isPunctuationSuggestions;
        mIsObsoleteSuggestions = isObsoleteSuggestions;
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
                + " mAllowsToBeAutoCorrected=" + mAllowsToBeAutoCorrected
                + " mIsPunctuationSuggestions=" + mIsPunctuationSuggestions
                + " words=" + Arrays.toString(mSuggestedWordInfoList.toArray());
    }

    public static ArrayList<SuggestedWordInfo> getFromCharSequenceList(
            final List<CharSequence> wordList) {
        final ArrayList<SuggestedWordInfo> result = new ArrayList<SuggestedWordInfo>();
        for (CharSequence word : wordList) {
            if (null != word) result.add(new SuggestedWordInfo(word));
        }
        return result;
    }

    public static List<SuggestedWordInfo> getFromApplicationSpecifiedCompletions(
            final CompletionInfo[] infos) {
        final ArrayList<SuggestedWordInfo> result = new ArrayList<SuggestedWordInfo>();
        for (CompletionInfo info : infos) {
            if (null != info) result.add(new SuggestedWordInfo(info.getText()));
        }
        return result;
    }

    // Should get rid of the first one (what the user typed previously) from suggestions
    // and replace it with what the user currently typed.
    public static ArrayList<SuggestedWordInfo> getTypedWordAndPreviousSuggestions(
            final CharSequence typedWord, final SuggestedWords previousSuggestions) {
        final ArrayList<SuggestedWordInfo> suggestionsList = new ArrayList<SuggestedWordInfo>();
        final HashSet<String> alreadySeen = new HashSet<String>();
        suggestionsList.add(new SuggestedWordInfo(typedWord));
        alreadySeen.add(typedWord.toString());
        final int previousSize = previousSuggestions.size();
        for (int pos = 1; pos < previousSize; pos++) {
            final String prevWord = previousSuggestions.getWord(pos).toString();
            // Filter out duplicate suggestion.
            if (!alreadySeen.contains(prevWord)) {
                suggestionsList.add(new SuggestedWordInfo(prevWord));
                alreadySeen.add(prevWord);
            }
        }
        return suggestionsList;
    }

    public static class SuggestedWordInfo {
        public final CharSequence mWord;
        private final String mDebugString;

        public SuggestedWordInfo(final CharSequence word) {
            mWord = word;
            mDebugString = "";
        }

        public SuggestedWordInfo(final CharSequence word, final String debugString) {
            mWord = word;
            if (null == debugString) throw new NullPointerException("");
            mDebugString = debugString;
        }

        public String getDebugString() {
            return mDebugString;
        }

        @Override
        public String toString() {
            if (TextUtils.isEmpty(mDebugString)) {
                return mWord.toString();
            } else {
                return mWord.toString() + " (" + mDebugString.toString() + ")";
            }
        }
    }
}
