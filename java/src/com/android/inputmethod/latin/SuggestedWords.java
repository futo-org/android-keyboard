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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.define.DebugFlags;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class SuggestedWords {
    public static final int INDEX_OF_TYPED_WORD = 0;
    public static final int INDEX_OF_AUTO_CORRECTION = 1;
    public static final int NOT_A_SEQUENCE_NUMBER = -1;

    public static final int INPUT_STYLE_NONE = 0;
    public static final int INPUT_STYLE_TYPING = 1;
    public static final int INPUT_STYLE_UPDATE_BATCH = 2;
    public static final int INPUT_STYLE_TAIL_BATCH = 3;
    public static final int INPUT_STYLE_APPLICATION_SPECIFIED = 4;
    public static final int INPUT_STYLE_RECORRECTION = 5;
    public static final int INPUT_STYLE_PREDICTION = 6;
    public static final int INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION = 7;

    // The maximum number of suggestions available.
    public static final int MAX_SUGGESTIONS = 18;

    private static final ArrayList<SuggestedWordInfo> EMPTY_WORD_INFO_LIST = new ArrayList<>(0);
    public static final SuggestedWords EMPTY = new SuggestedWords(
            EMPTY_WORD_INFO_LIST, null /* rawSuggestions */, false /* typedWordValid */,
            false /* willAutoCorrect */, false /* isObsoleteSuggestions */, INPUT_STYLE_NONE);

    public final String mTypedWord;
    public final boolean mTypedWordValid;
    // Note: this INCLUDES cases where the word will auto-correct to itself. A good definition
    // of what this flag means would be "the top suggestion is strong enough to auto-correct",
    // whether this exactly matches the user entry or not.
    public final boolean mWillAutoCorrect;
    public final boolean mIsObsoleteSuggestions;
    // How the input for these suggested words was done by the user. Must be one of the
    // INPUT_STYLE_* constants above.
    public final int mInputStyle;
    public final int mSequenceNumber; // Sequence number for auto-commit.
    protected final ArrayList<SuggestedWordInfo> mSuggestedWordInfoList;
    public final ArrayList<SuggestedWordInfo> mRawSuggestions;

    public SuggestedWords(final ArrayList<SuggestedWordInfo> suggestedWordInfoList,
            final ArrayList<SuggestedWordInfo> rawSuggestions,
            final boolean typedWordValid,
            final boolean willAutoCorrect,
            final boolean isObsoleteSuggestions,
            final int inputStyle) {
        this(suggestedWordInfoList, rawSuggestions, typedWordValid, willAutoCorrect,
                isObsoleteSuggestions, inputStyle, NOT_A_SEQUENCE_NUMBER);
    }

    public SuggestedWords(final ArrayList<SuggestedWordInfo> suggestedWordInfoList,
            final ArrayList<SuggestedWordInfo> rawSuggestions,
            final boolean typedWordValid,
            final boolean willAutoCorrect,
            final boolean isObsoleteSuggestions,
            final int inputStyle,
            final int sequenceNumber) {
        this(suggestedWordInfoList, rawSuggestions,
                (suggestedWordInfoList.isEmpty() || isPrediction(inputStyle)) ? null
                        : suggestedWordInfoList.get(INDEX_OF_TYPED_WORD).mWord,
                typedWordValid, willAutoCorrect, isObsoleteSuggestions, inputStyle, sequenceNumber);
    }

    public SuggestedWords(final ArrayList<SuggestedWordInfo> suggestedWordInfoList,
            final ArrayList<SuggestedWordInfo> rawSuggestions,
            final String typedWord,
            final boolean typedWordValid,
            final boolean willAutoCorrect,
            final boolean isObsoleteSuggestions,
            final int inputStyle,
            final int sequenceNumber) {
        mSuggestedWordInfoList = suggestedWordInfoList;
        mRawSuggestions = rawSuggestions;
        mTypedWordValid = typedWordValid;
        mWillAutoCorrect = willAutoCorrect;
        mIsObsoleteSuggestions = isObsoleteSuggestions;
        mInputStyle = inputStyle;
        mSequenceNumber = sequenceNumber;
        mTypedWord = typedWord;
    }

    public boolean isEmpty() {
        return mSuggestedWordInfoList.isEmpty();
    }

    public int size() {
        return mSuggestedWordInfoList.size();
    }

    /**
     * Get suggested word at <code>index</code>.
     * @param index The index of the suggested word.
     * @return The suggested word.
     */
    public String getWord(final int index) {
        return mSuggestedWordInfoList.get(index).mWord;
    }

    /**
     * Get displayed text at <code>index</code>.
     * In RTL languages, the displayed text on the suggestion strip may be different from the
     * suggested word that is returned from {@link #getWord(int)}. For example the displayed text
     * of punctuation suggestion "(" should be ")".
     * @param index The index of the text to display.
     * @return The text to be displayed.
     */
    public String getLabel(final int index) {
        return mSuggestedWordInfoList.get(index).mWord;
    }

    /**
     * Get {@link SuggestedWordInfo} object at <code>index</code>.
     * @param index The index of the {@link SuggestedWordInfo}.
     * @return The {@link SuggestedWordInfo} object.
     */
    public SuggestedWordInfo getInfo(final int index) {
        return mSuggestedWordInfoList.get(index);
    }

    public String getDebugString(final int pos) {
        if (!DebugFlags.DEBUG_ENABLED) {
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

    /**
     * The predicator to tell whether this object represents punctuation suggestions.
     * @return false if this object desn't represent punctuation suggestions.
     */
    public boolean isPunctuationSuggestions() {
        return false;
    }

    @Override
    public String toString() {
        // Pretty-print method to help debug
        return "SuggestedWords:"
                + " mTypedWordValid=" + mTypedWordValid
                + " mWillAutoCorrect=" + mWillAutoCorrect
                + " mInputStyle=" + mInputStyle
                + " words=" + Arrays.toString(mSuggestedWordInfoList.toArray());
    }

    public static ArrayList<SuggestedWordInfo> getFromApplicationSpecifiedCompletions(
            final CompletionInfo[] infos) {
        final ArrayList<SuggestedWordInfo> result = new ArrayList<>();
        for (final CompletionInfo info : infos) {
            if (null == info || null == info.getText()) {
                continue;
            }
            result.add(new SuggestedWordInfo(info));
        }
        return result;
    }

    // Should get rid of the first one (what the user typed previously) from suggestions
    // and replace it with what the user currently typed.
    public static ArrayList<SuggestedWordInfo> getTypedWordAndPreviousSuggestions(
            final String typedWord, final SuggestedWords previousSuggestions) {
        final ArrayList<SuggestedWordInfo> suggestionsList = new ArrayList<>();
        final HashSet<String> alreadySeen = new HashSet<>();
        suggestionsList.add(new SuggestedWordInfo(typedWord, SuggestedWordInfo.MAX_SCORE,
                SuggestedWordInfo.KIND_TYPED, Dictionary.DICTIONARY_USER_TYPED,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
        alreadySeen.add(typedWord.toString());
        final int previousSize = previousSuggestions.size();
        for (int index = 1; index < previousSize; index++) {
            final SuggestedWordInfo prevWordInfo = previousSuggestions.getInfo(index);
            final String prevWord = prevWordInfo.mWord;
            // Filter out duplicate suggestions.
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

        private static final int KIND_MASK_KIND = 0xFF; // Mask to get only the kind
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

        public static final int KIND_FLAG_POSSIBLY_OFFENSIVE = 0x80000000;
        public static final int KIND_FLAG_EXACT_MATCH = 0x40000000;
        public static final int KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION = 0x20000000;

        public final String mWord;
        // The completion info from the application. Null for suggestions that don't come from
        // the application (including keyboard-computed ones, so this is almost always null)
        public final CompletionInfo mApplicationSpecifiedCompletionInfo;
        public final int mScore;
        public final int mKindAndFlags;
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
         * @param kindAndFlags The kind of suggestion, as one of the above KIND_* constants with
         * flags.
         * @param sourceDict What instance of Dictionary produced this suggestion.
         * @param indexOfTouchPointOfSecondWord See mIndexOfTouchPointOfSecondWord.
         * @param autoCommitFirstWordConfidence See mAutoCommitFirstWordConfidence.
         */
        public SuggestedWordInfo(final String word, final int score, final int kindAndFlags,
                final Dictionary sourceDict, final int indexOfTouchPointOfSecondWord,
                final int autoCommitFirstWordConfidence) {
            mWord = word;
            mApplicationSpecifiedCompletionInfo = null;
            mScore = score;
            mKindAndFlags = kindAndFlags;
            mSourceDict = sourceDict;
            mCodePointCount = StringUtils.codePointCount(mWord);
            mIndexOfTouchPointOfSecondWord = indexOfTouchPointOfSecondWord;
            mAutoCommitFirstWordConfidence = autoCommitFirstWordConfidence;
        }

        /**
         * Create a new suggested word info from an application-specified completion.
         * If the passed argument or its contained text is null, this throws a NPE.
         * @param applicationSpecifiedCompletion The application-specified completion info.
         */
        public SuggestedWordInfo(final CompletionInfo applicationSpecifiedCompletion) {
            mWord = applicationSpecifiedCompletion.getText().toString();
            mApplicationSpecifiedCompletionInfo = applicationSpecifiedCompletion;
            mScore = SuggestedWordInfo.MAX_SCORE;
            mKindAndFlags = SuggestedWordInfo.KIND_APP_DEFINED;
            mSourceDict = Dictionary.DICTIONARY_APPLICATION_DEFINED;
            mCodePointCount = StringUtils.codePointCount(mWord);
            mIndexOfTouchPointOfSecondWord = SuggestedWordInfo.NOT_AN_INDEX;
            mAutoCommitFirstWordConfidence = SuggestedWordInfo.NOT_A_CONFIDENCE;
        }

        public boolean isEligibleForAutoCommit() {
            return (isKindOf(KIND_CORRECTION) && NOT_AN_INDEX != mIndexOfTouchPointOfSecondWord);
        }

        public int getKind() {
            return (mKindAndFlags & KIND_MASK_KIND);
        }

        public boolean isKindOf(final int kind) {
            return getKind() == kind;
        }

        public boolean isPossiblyOffensive() {
            return (mKindAndFlags & KIND_FLAG_POSSIBLY_OFFENSIVE) != 0;
        }

        public boolean isExactMatch() {
            return (mKindAndFlags & KIND_FLAG_EXACT_MATCH) != 0;
        }

        public boolean isExactMatchWithIntentionalOmission() {
            return (mKindAndFlags & KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION) != 0;
        }

        public void setDebugString(final String str) {
            if (null == str) throw new NullPointerException("Debug info is null");
            mDebugString = str;
        }

        public String getDebugString() {
            return mDebugString;
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

        // This will always remove the higher index if a duplicate is found.
        public static boolean removeDups(final String typedWord,
                ArrayList<SuggestedWordInfo> candidates) {
            if (candidates.isEmpty()) {
                return false;
            }
            final boolean didRemoveTypedWord;
            if (!TextUtils.isEmpty(typedWord)) {
                didRemoveTypedWord = removeSuggestedWordInfoFrom(typedWord, candidates,
                        -1 /* startIndexExclusive */);
            } else {
                didRemoveTypedWord = false;
            }
            for (int i = 0; i < candidates.size(); ++i) {
                removeSuggestedWordInfoFrom(candidates.get(i).mWord, candidates,
                        i /* startIndexExclusive */);
            }
            return didRemoveTypedWord;
        }

        private static boolean removeSuggestedWordInfoFrom(final String word,
                final ArrayList<SuggestedWordInfo> candidates, final int startIndexExclusive) {
            boolean didRemove = false;
            for (int i = startIndexExclusive + 1; i < candidates.size(); ++i) {
                final SuggestedWordInfo previous = candidates.get(i);
                if (word.equals(previous.mWord)) {
                    didRemove = true;
                    candidates.remove(i);
                    --i;
                }
            }
            return didRemove;
        }
    }

    private static boolean isPrediction(final int inputStyle) {
        return INPUT_STYLE_PREDICTION == inputStyle
                || INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION == inputStyle;
    }

    public boolean isPrediction() {
        return isPrediction(mInputStyle);
    }

    // SuggestedWords is an immutable object, as much as possible. We must not just remove
    // words from the member ArrayList as some other parties may expect the object to never change.
    // This is only ever called by recorrection at the moment, hence the ForRecorrection moniker.
    public SuggestedWords getSuggestedWordsExcludingTypedWordForRecorrection() {
        final ArrayList<SuggestedWordInfo> newSuggestions = new ArrayList<>();
        String typedWord = null;
        for (int i = 0; i < mSuggestedWordInfoList.size(); ++i) {
            final SuggestedWordInfo info = mSuggestedWordInfoList.get(i);
            if (!info.isKindOf(SuggestedWordInfo.KIND_TYPED)) {
                newSuggestions.add(info);
            } else {
                assert(null == typedWord);
                typedWord = info.mWord;
            }
        }
        // We should never autocorrect, so we say the typed word is valid. Also, in this case,
        // no auto-correction should take place hence willAutoCorrect = false.
        return new SuggestedWords(newSuggestions, null /* rawSuggestions */, typedWord,
                true /* typedWordValid */, false /* willAutoCorrect */, mIsObsoleteSuggestions,
                SuggestedWords.INPUT_STYLE_RECORRECTION, NOT_A_SEQUENCE_NUMBER);
    }

    // Creates a new SuggestedWordInfo from the currently suggested words that removes all but the
    // last word of all suggestions, separated by a space. This is necessary because when we commit
    // a multiple-word suggestion, the IME only retains the last word as the composing word, and
    // we should only suggest replacements for this last word.
    // TODO: make this work with languages without spaces.
    public SuggestedWords getSuggestedWordsForLastWordOfPhraseGesture() {
        final ArrayList<SuggestedWordInfo> newSuggestions = new ArrayList<>();
        for (int i = 0; i < mSuggestedWordInfoList.size(); ++i) {
            final SuggestedWordInfo info = mSuggestedWordInfoList.get(i);
            final int indexOfLastSpace = info.mWord.lastIndexOf(Constants.CODE_SPACE) + 1;
            final String lastWord = info.mWord.substring(indexOfLastSpace);
            newSuggestions.add(new SuggestedWordInfo(lastWord, info.mScore, info.mKindAndFlags,
                    info.mSourceDict, SuggestedWordInfo.NOT_AN_INDEX,
                    SuggestedWordInfo.NOT_A_CONFIDENCE));
        }
        return new SuggestedWords(newSuggestions, null /* rawSuggestions */, mTypedWordValid,
                mWillAutoCorrect, mIsObsoleteSuggestions, INPUT_STYLE_TAIL_BATCH);
    }

    /**
     * @return the {@link SuggestedWordInfo} which corresponds to the word that is originally
     * typed by the user. Otherwise returns {@code null}. Note that gesture input is not
     * considered to be a typed word.
     */
    @UsedForTesting
    public SuggestedWordInfo getTypedWordInfoOrNull() {
        if (SuggestedWords.INDEX_OF_TYPED_WORD >= size()) {
            return null;
        }
        final SuggestedWordInfo info = getInfo(SuggestedWords.INDEX_OF_TYPED_WORD);
        return (info.getKind() == SuggestedWordInfo.KIND_TYPED) ? info : null;
    }
}
