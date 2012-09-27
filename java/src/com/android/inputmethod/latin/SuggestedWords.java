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

public final class SuggestedWords {
    private static final ArrayList<SuggestedWordInfo> EMPTY_WORD_INFO_LIST =
            CollectionUtils.newArrayList(0);
    public static final SuggestedWords EMPTY = new SuggestedWords(
            EMPTY_WORD_INFO_LIST, false, false, false, false, false);

    public final boolean mTypedWordValid;
    // Note: this INCLUDES cases where the word will auto-correct to itself. A good definition
    // of what this flag means would be "the top suggestion is strong enough to auto-correct",
    // whether this exactly matches the user entry or not.
    public final boolean mWillAutoCorrect;
    public final boolean mIsPunctuationSuggestions;
    public final boolean mIsObsoleteSuggestions;
    public final boolean mIsPrediction;
    private final ArrayList<SuggestedWordInfo> mSuggestedWordInfoList;

    public SuggestedWords(final ArrayList<SuggestedWordInfo> suggestedWordInfoList,
            final boolean typedWordValid,
            final boolean willAutoCorrect,
            final boolean isPunctuationSuggestions,
            final boolean isObsoleteSuggestions,
            final boolean isPrediction) {
        mSuggestedWordInfoList = suggestedWordInfoList;
        mTypedWordValid = typedWordValid;
        mWillAutoCorrect = willAutoCorrect;
        mIsPunctuationSuggestions = isPunctuationSuggestions;
        mIsObsoleteSuggestions = isObsoleteSuggestions;
        mIsPrediction = isPrediction;
    }

    public int size() {
        return mSuggestedWordInfoList.size();
    }

    public String getWord(int pos) {
        return mSuggestedWordInfoList.get(pos).mWord;
    }

    public SuggestedWordInfo getWordInfo(int pos) {
        return mSuggestedWordInfoList.get(pos);
    }

    public SuggestedWordInfo getInfo(int pos) {
        return mSuggestedWordInfoList.get(pos);
    }

    public boolean willAutoCorrect() {
        return mWillAutoCorrect;
    }

    @Override
    public String toString() {
        // Pretty-print method to help debug
        return "SuggestedWords:"
                + " mTypedWordValid=" + mTypedWordValid
                + " mWillAutoCorrect=" + mWillAutoCorrect
                + " mIsPunctuationSuggestions=" + mIsPunctuationSuggestions
                + " words=" + Arrays.toString(mSuggestedWordInfoList.toArray());
    }

    public static ArrayList<SuggestedWordInfo> getFromApplicationSpecifiedCompletions(
            final CompletionInfo[] infos) {
        final ArrayList<SuggestedWordInfo> result = CollectionUtils.newArrayList();
        for (CompletionInfo info : infos) {
            if (null != info && info.getText() != null) {
                result.add(new SuggestedWordInfo(info.getText(), SuggestedWordInfo.MAX_SCORE,
                        SuggestedWordInfo.KIND_APP_DEFINED, Dictionary.TYPE_APPLICATION_DEFINED));
            }
        }
        return result;
    }

    // Should get rid of the first one (what the user typed previously) from suggestions
    // and replace it with what the user currently typed.
    public static ArrayList<SuggestedWordInfo> getTypedWordAndPreviousSuggestions(
            final CharSequence typedWord, final SuggestedWords previousSuggestions) {
        final ArrayList<SuggestedWordInfo> suggestionsList = CollectionUtils.newArrayList();
        final HashSet<String> alreadySeen = CollectionUtils.newHashSet();
        suggestionsList.add(new SuggestedWordInfo(typedWord, SuggestedWordInfo.MAX_SCORE,
                SuggestedWordInfo.KIND_TYPED, Dictionary.TYPE_USER_TYPED));
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

    public static final class SuggestedWordInfo {
        public static final int MAX_SCORE = Integer.MAX_VALUE;
        public static final int KIND_TYPED = 0; // What user typed
        public static final int KIND_CORRECTION = 1; // Simple correction/suggestion
        public static final int KIND_COMPLETION = 2; // Completion (suggestion with appended chars)
        public static final int KIND_WHITELIST = 3; // Whitelisted word
        public static final int KIND_BLACKLIST = 4; // Blacklisted word
        public static final int KIND_HARDCODED = 5; // Hardcoded suggestion, e.g. punctuation
        public static final int KIND_APP_DEFINED = 6; // Suggested by the application
        public static final int KIND_SHORTCUT = 7; // A shortcut
        public static final int KIND_PREDICTION = 8; // A prediction (== a suggestion with no input)
        public final String mWord;
        public final int mScore;
        public final int mKind; // one of the KIND_* constants above
        public final int mCodePointCount;
        public final String mSourceDict;
        private String mDebugString = "";

        public SuggestedWordInfo(final CharSequence word, final int score, final int kind,
                final String sourceDict) {
            mWord = word.toString();
            mScore = score;
            mKind = kind;
            mSourceDict = sourceDict;
            mCodePointCount = StringUtils.codePointCount(mWord);
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
            return mWord.codePointAt(i);
        }

        @Override
        public String toString() {
            if (TextUtils.isEmpty(mDebugString)) {
                return mWord;
            } else {
                return mWord + " (" + mDebugString.toString() + ")";
            }
        }

        // TODO: Consolidate this method and StringUtils.removeDupes() in the future.
        public static void removeDups(ArrayList<SuggestedWordInfo> candidates) {
            if (candidates.size() <= 1) {
                return;
            }
            int i = 1;
            while (i < candidates.size()) {
                final SuggestedWordInfo cur = candidates.get(i);
                for (int j = 0; j < i; ++j) {
                    final SuggestedWordInfo previous = candidates.get(j);
                    if (cur.mWord.equals(previous.mWord)) {
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
