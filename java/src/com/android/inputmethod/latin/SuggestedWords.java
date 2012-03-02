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
    public static final SuggestedWords EMPTY = new SuggestedWords(null, false, false, false, null);

    public final List<CharSequence> mWords;
    public final boolean mTypedWordValid;
    public final boolean mHasAutoCorrectionCandidate;
    public final boolean mIsPunctuationSuggestions;
    private final List<SuggestedWordInfo> mSuggestedWordInfoList;
    private boolean mShouldBlockAutoCorrection;

    private SuggestedWords(List<CharSequence> words, boolean typedWordValid,
            boolean hasAutoCorrectionCandidate, boolean isPunctuationSuggestions,
            List<SuggestedWordInfo> suggestedWordInfoList) {
        if (words != null) {
            mWords = words;
        } else {
            mWords = Collections.emptyList();
        }
        mTypedWordValid = typedWordValid;
        mHasAutoCorrectionCandidate = hasAutoCorrectionCandidate;
        mIsPunctuationSuggestions = isPunctuationSuggestions;
        mSuggestedWordInfoList = suggestedWordInfoList;
        mShouldBlockAutoCorrection = false;
    }

    public int size() {
        return mWords.size();
    }

    public CharSequence getWord(int pos) {
        return mWords.get(pos);
    }

    public SuggestedWordInfo getInfo(int pos) {
        return mSuggestedWordInfoList != null ? mSuggestedWordInfoList.get(pos) : null;
    }

    public boolean hasAutoCorrectionWord() {
        return mHasAutoCorrectionCandidate && size() > 1 && !mTypedWordValid;
    }

    public boolean hasWordAboveAutoCorrectionScoreThreshold() {
        return mHasAutoCorrectionCandidate && ((size() > 1 && !mTypedWordValid) || mTypedWordValid);
    }

    public boolean isPunctuationSuggestions() {
        return mIsPunctuationSuggestions;
    }

    public void setShouldBlockAutoCorrection() {
        mShouldBlockAutoCorrection = true;
    }

    public boolean shouldBlockAutoCorrection() {
        return mShouldBlockAutoCorrection;
    }

    public static class Builder {
        private List<CharSequence> mWords = new ArrayList<CharSequence>();
        private boolean mTypedWordValid;
        private boolean mHasMinimalSuggestion;
        private boolean mIsPunctuationSuggestions;
        private List<SuggestedWordInfo> mSuggestedWordInfoList =
                new ArrayList<SuggestedWordInfo>();

        public Builder() {
            // Nothing to do here.
        }

        public Builder addWords(List<CharSequence> words,
                List<SuggestedWordInfo> suggestedWordInfoList) {
            final int N = words.size();
            for (int i = 0; i < N; ++i) {
                SuggestedWordInfo suggestedWordInfo = null;
                if (suggestedWordInfoList != null) {
                    suggestedWordInfo = suggestedWordInfoList.get(i);
                }
                if (suggestedWordInfo == null) {
                    suggestedWordInfo = new SuggestedWordInfo();
                }
                addWord(words.get(i), suggestedWordInfo);
            }
            return this;
        }

        public Builder addWord(CharSequence word) {
            return addWord(word, null, false);
        }

        public Builder addWord(CharSequence word, CharSequence debugString,
                boolean isPreviousSuggestedWord) {
            SuggestedWordInfo info = new SuggestedWordInfo(debugString, isPreviousSuggestedWord);
            return addWord(word, info);
        }

        private Builder addWord(CharSequence word, SuggestedWordInfo suggestedWordInfo) {
            if (!TextUtils.isEmpty(word)) {
                mWords.add(word);
                // It's okay if suggestedWordInfo is null since it's checked where it's used.
                mSuggestedWordInfoList.add(suggestedWordInfo);
            }
            return this;
        }

        public Builder setApplicationSpecifiedCompletions(CompletionInfo[] infos) {
            for (CompletionInfo info : infos) {
                if (null != info) addWord(info.getText());
            }
            return this;
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

        // Should get rid of the first one (what the user typed previously) from suggestions
        // and replace it with what the user currently typed.
        public Builder addTypedWordAndPreviousSuggestions(CharSequence typedWord,
                SuggestedWords previousSuggestions) {
            mWords.clear();
            mSuggestedWordInfoList.clear();
            final HashSet<String> alreadySeen = new HashSet<String>();
            addWord(typedWord, null, false);
            alreadySeen.add(typedWord.toString());
            final int previousSize = previousSuggestions.size();
            for (int pos = 1; pos < previousSize; pos++) {
                final String prevWord = previousSuggestions.getWord(pos).toString();
                // Filter out duplicate suggestion.
                if (!alreadySeen.contains(prevWord)) {
                    addWord(prevWord, null, true);
                    alreadySeen.add(prevWord);
                }
            }
            mTypedWordValid = false;
            mHasMinimalSuggestion = false;
            return this;
        }

        public SuggestedWords build() {
            return new SuggestedWords(mWords, mTypedWordValid, mHasMinimalSuggestion,
                    mIsPunctuationSuggestions, mSuggestedWordInfoList);
        }

        public int size() {
            return mWords.size();
        }

        public CharSequence getWord(int pos) {
            return mWords.get(pos);
        }

        @Override
        public String toString() {
            // Pretty-print method to help debug
            final StringBuilder sb = new StringBuilder("StringBuilder: mTypedWordValid = "
                    + mTypedWordValid + " ; mHasMinimalSuggestion = " + mHasMinimalSuggestion
                    + " ; mIsPunctuationSuggestions = " + mIsPunctuationSuggestions
                    + " --- ");
            for (CharSequence s : mWords) {
                sb.append(s);
                sb.append(" ; ");
            }
            return sb.toString();
        }
    }

    public static class SuggestedWordInfo {
        private final CharSequence mDebugString;
        private final boolean mPreviousSuggestedWord;

        public SuggestedWordInfo() {
            mDebugString = "";
            mPreviousSuggestedWord = false;
        }

        public SuggestedWordInfo(CharSequence debugString, boolean previousSuggestedWord) {
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
