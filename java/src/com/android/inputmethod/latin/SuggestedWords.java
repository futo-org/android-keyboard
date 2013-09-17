/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.view.inputmethod.CompletionInfo;

import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public final class SuggestedWords {
    public static final int INDEX_OF_TYPED_WORD = 0;
    public static final int INDEX_OF_AUTO_CORRECTION = 1;

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

    public boolean isEmpty() {
        return mSuggestedWordInfoList.isEmpty();
    }

    public int size() {
        return mSuggestedWordInfoList.size();
    }

    public String getWord(final int index) {
        return mSuggestedWordInfoList.get(index).mWord;
    }

    public SuggestedWordInfo getInfo(final int index) {
        return mSuggestedWordInfoList.get(index);
    }

    public String getDebugString(final int pos) {
        if (!LatinImeLogger.sDBG) {
            return null;
        }
        final SuggestedWordInfo wordInfo = getInfo(pos);
        if (wordInfo == null) {
            return null;
        }
        final String debugString = wordInfo.getDebugString();
        if (TextUtils.isEmpty(debugString)) {
            return null;
        }
        return debugString;
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
        for (final CompletionInfo info : infos) {
            if (info == null) continue;
            final CharSequence text = info.getText();
            if (null == text) continue;
            final SuggestedWordInfo suggestedWordInfo = new SuggestedWordInfo(text.toString(),
                    SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_APP_DEFINED,
                    Dictionary.DICTIONARY_APPLICATION_DEFINED,
                    SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                    SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */);
            result.add(suggestedWordInfo);
        }
        return result;
    }

    // Should get rid of the first one (what the user typed previously) from suggestions
    // and replace it with what the user currently typed.
    public static ArrayList<SuggestedWordInfo> getTypedWordAndPreviousSuggestions(
            final String typedWord, final SuggestedWords previousSuggestions) {
        final ArrayList<SuggestedWordInfo> suggestionsList = CollectionUtils.newArrayList();
        final HashSet<String> alreadySeen = CollectionUtils.newHashSet();
        suggestionsList.add(new SuggestedWordInfo(typedWord, SuggestedWordInfo.MAX_SCORE,
                SuggestedWordInfo.KIND_TYPED, Dictionary.DICTIONARY_USER_TYPED,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
        alreadySeen.add(typedWord.toString());
        final int previousSize = previousSuggestions.size();
        for (int index = 1; index < previousSize; index++) {
            final SuggestedWordInfo prevWordInfo = previousSuggestions.getInfo(index);
            final String prevWord = prevWordInfo.mWord;
            // Filter out duplicate suggestion.
            if (!alreadySeen.contains(prevWord)) {
                suggestionsList.add(prevWordInfo);
                alreadySeen.add(prevWord);
            }
        }
        return suggestionsList;
    }

    public SuggestedWordInfo getAutoCommitCandidate() {
        if (mSuggestedWordInfoList.size() <= 0) return null;
        final SuggestedWordInfo candidate = mSuggestedWordInfoList.get(0);
        return candidate.isEligibleForAutoCommit() ? candidate : null;
    }

    public static final class SuggestedWordInfo {
        public static final int NOT_AN_INDEX = -1;
        public static final int NOT_A_CONFIDENCE = -1;
        public static final int MAX_SCORE = Integer.MAX_VALUE;
        public static final int KIND_MASK_KIND = 0xFF; // Mask to get only the kind
        public static final int KIND_TYPED = 0; // What user typed
        public static final int KIND_CORRECTION = 1; // Simple correction/suggestion
        public static final int KIND_COMPLETION = 2; // Completion (suggestion with appended chars)
        public static final int KIND_WHITELIST = 3; // Whitelisted word
        public static final int KIND_BLACKLIST = 4; // Blacklisted word
        public static final int KIND_HARDCODED = 5; // Hardcoded suggestion, e.g. punctuation
        public static final int KIND_APP_DEFINED = 6; // Suggested by the application
        public static final int KIND_SHORTCUT = 7; // A shortcut
        public static final int KIND_PREDICTION = 8; // A prediction (== a suggestion with no input)
        // KIND_RESUMED: A resumed suggestion (comes from a span, currently this type is used only
        // in java for re-correction)
        public static final int KIND_RESUMED = 9;
        public static final int KIND_OOV_CORRECTION = 10; // Most probable string correction

        public static final int KIND_MASK_FLAGS = 0xFFFFFF00; // Mask to get the flags
        public static final int KIND_FLAG_POSSIBLY_OFFENSIVE = 0x80000000;
        public static final int KIND_FLAG_EXACT_MATCH = 0x40000000;

        public final String mWord;
        public final int mScore;
        public final int mKind; // one of the KIND_* constants above
        public final int mCodePointCount;
        public final Dictionary mSourceDict;
        // For auto-commit. This keeps track of the index inside the touch coordinates array
        // passed to native code to get suggestions for a gesture that corresponds to the first
        // letter of the second word.
        public final int mIndexOfTouchPointOfSecondWord;
        // For auto-commit. This is a measure of how confident we are that we can commit the
        // first word of this suggestion.
        public final int mAutoCommitFirstWordConfidence;
        private String mDebugString = "";

        /**
         * Create a new suggested word info.
         * @param word The string to suggest.
         * @param score A measure of how likely this suggestion is.
         * @param kind The kind of suggestion, as one of the above KIND_* constants.
         * @param sourceDict What instance of Dictionary produced this suggestion.
         * @param indexOfTouchPointOfSecondWord See mIndexOfTouchPointOfSecondWord.
         * @param autoCommitFirstWordConfidence See mAutoCommitFirstWordConfidence.
         */
        public SuggestedWordInfo(final String word, final int score, final int kind,
                final Dictionary sourceDict, final int indexOfTouchPointOfSecondWord,
                final int autoCommitFirstWordConfidence) {
            mWord = word;
            mScore = score;
            mKind = kind;
            mSourceDict = sourceDict;
            mCodePointCount = StringUtils.codePointCount(mWord);
            mIndexOfTouchPointOfSecondWord = indexOfTouchPointOfSecondWord;
            mAutoCommitFirstWordConfidence = autoCommitFirstWordConfidence;
        }

        public boolean isEligibleForAutoCommit() {
            return (KIND_CORRECTION == mKind && NOT_AN_INDEX != mIndexOfTouchPointOfSecondWord);
        }

        public void setDebugString(final String str) {
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
                return mWord + " (" + mDebugString + ")";
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

    // SuggestedWords is an immutable object, as much as possible. We must not just remove
    // words from the member ArrayList as some other parties may expect the object to never change.
    public SuggestedWords getSuggestedWordsExcludingTypedWord() {
        final ArrayList<SuggestedWordInfo> newSuggestions = CollectionUtils.newArrayList();
        for (int i = 0; i < mSuggestedWordInfoList.size(); ++i) {
            final SuggestedWordInfo info = mSuggestedWordInfoList.get(i);
            if (SuggestedWordInfo.KIND_TYPED != info.mKind) {
                newSuggestions.add(info);
            }
        }
        // We should never autocorrect, so we say the typed word is valid. Also, in this case,
        // no auto-correction should take place hence willAutoCorrect = false.
        return new SuggestedWords(newSuggestions, true /* typedWordValid */,
                false /* willAutoCorrect */, mIsPunctuationSuggestions, mIsObsoleteSuggestions,
                mIsPrediction);
    }

    // Creates a new SuggestedWordInfo from the currently suggested words that removes all but the
    // last word of all suggestions, separated by a space. This is necessary because when we commit
    // a multiple-word suggestion, the IME only retains the last word as the composing word, and
    // we should only suggest replacements for this last word.
    // TODO: make this work with languages without spaces.
    public SuggestedWords getSuggestedWordsForLastWordOfPhraseGesture() {
        final ArrayList<SuggestedWordInfo> newSuggestions = CollectionUtils.newArrayList();
        for (int i = 0; i < mSuggestedWordInfoList.size(); ++i) {
            final SuggestedWordInfo info = mSuggestedWordInfoList.get(i);
            final int indexOfLastSpace = info.mWord.lastIndexOf(Constants.CODE_SPACE) + 1;
            final String lastWord = info.mWord.substring(indexOfLastSpace);
            newSuggestions.add(new SuggestedWordInfo(lastWord, info.mScore, info.mKind,
                    info.mSourceDict, SuggestedWordInfo.NOT_AN_INDEX,
                    SuggestedWordInfo.NOT_A_CONFIDENCE));
        }
        return new SuggestedWords(newSuggestions, mTypedWordValid,
                mWillAutoCorrect, mIsPunctuationSuggestions, mIsObsoleteSuggestions,
                mIsPrediction);
    }
}
