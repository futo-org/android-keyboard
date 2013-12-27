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

import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.utils.AutoCorrectionUtils;
import com.android.inputmethod.latin.utils.BoundedTreeSet;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
public final class Suggest {
    public static final String TAG = Suggest.class.getSimpleName();

    // Session id for
    // {@link #getSuggestedWords(WordComposer,String,ProximityInfo,boolean,int)}.
    // We are sharing the same ID between typing and gesture to save RAM footprint.
    public static final int SESSION_TYPING = 0;
    public static final int SESSION_GESTURE = 0;

    // TODO: rename this to CORRECTION_OFF
    public static final int CORRECTION_NONE = 0;
    // TODO: rename this to CORRECTION_ON
    public static final int CORRECTION_FULL = 1;

    // Close to -2**31
    private static final int SUPPRESS_SUGGEST_THRESHOLD = -2000000000;

    public static final int MAX_SUGGESTIONS = 18;

    private static final boolean DBG = LatinImeLogger.sDBG;

    public final DictionaryFacilitatorForSuggest mDictionaryFacilitator;

    private float mAutoCorrectionThreshold;

    // Locale used for upper- and title-casing words
    public final Locale mLocale;

    public Suggest(final Locale locale,
            final DictionaryFacilitatorForSuggest dictionaryFacilitator) {
        mLocale = locale;
        mDictionaryFacilitator = dictionaryFacilitator;
    }

    // Creates instance with new dictionary facilitator.
    public Suggest(final Suggest oldSuggst,
            final DictionaryFacilitatorForSuggest dictionaryFacilitator) {
        mLocale = oldSuggst.mLocale;
        mAutoCorrectionThreshold = oldSuggst.mAutoCorrectionThreshold;
        mDictionaryFacilitator = dictionaryFacilitator;
    }

    public void setAutoCorrectionThreshold(float threshold) {
        mAutoCorrectionThreshold = threshold;
    }

    public interface OnGetSuggestedWordsCallback {
        public void onGetSuggestedWords(final SuggestedWords suggestedWords);
    }

    public void getSuggestedWords(final WordComposer wordComposer,
            final String prevWordForBigram, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final boolean isCorrectionEnabled,
            final int[] additionalFeaturesOptions, final int sessionId, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        LatinImeLogger.onStartSuggestion(prevWordForBigram);
        if (wordComposer.isBatchMode()) {
            getSuggestedWordsForBatchInput(wordComposer, prevWordForBigram, proximityInfo,
                    blockOffensiveWords, additionalFeaturesOptions, sessionId, sequenceNumber,
                    callback);
        } else {
            getSuggestedWordsForTypingInput(wordComposer, prevWordForBigram, proximityInfo,
                    blockOffensiveWords, isCorrectionEnabled, additionalFeaturesOptions,
                    sequenceNumber, callback);
        }
    }

    // Retrieves suggestions for the typing input
    // and calls the callback function with the suggestions.
    private void getSuggestedWordsForTypingInput(final WordComposer wordComposer,
            final String prevWordForBigram, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final boolean isCorrectionEnabled,
            final int[] additionalFeaturesOptions, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        final int trailingSingleQuotesCount = wordComposer.trailingSingleQuotesCount();
        final BoundedTreeSet suggestionsSet = new BoundedTreeSet(sSuggestedWordInfoComparator,
                MAX_SUGGESTIONS);

        final String typedWord = wordComposer.getTypedWord();
        final String consideredWord = trailingSingleQuotesCount > 0
                ? typedWord.substring(0, typedWord.length() - trailingSingleQuotesCount)
                : typedWord;
        LatinImeLogger.onAddSuggestedWord(typedWord, Dictionary.TYPE_USER_TYPED);

        final WordComposer wordComposerForLookup;
        if (trailingSingleQuotesCount > 0) {
            wordComposerForLookup = new WordComposer(wordComposer);
            for (int i = trailingSingleQuotesCount - 1; i >= 0; --i) {
                wordComposerForLookup.deleteLast();
            }
        } else {
            wordComposerForLookup = wordComposer;
        }
        mDictionaryFacilitator.getSuggestions(wordComposer, prevWordForBigram, proximityInfo,
                blockOffensiveWords, additionalFeaturesOptions, SESSION_TYPING, suggestionsSet);
        final String whitelistedWord;
        if (suggestionsSet.isEmpty()) {
            whitelistedWord = null;
        } else if (SuggestedWordInfo.KIND_WHITELIST != suggestionsSet.first().mKind) {
            whitelistedWord = null;
        } else {
            whitelistedWord = suggestionsSet.first().mWord;
        }

        // The word can be auto-corrected if it has a whitelist entry that is not itself,
        // or if it's a 2+ characters non-word (i.e. it's not in the dictionary).
        final boolean allowsToBeAutoCorrected = (null != whitelistedWord
                && !whitelistedWord.equals(consideredWord))
                || (consideredWord.length() > 1 && !mDictionaryFacilitator.isValidWord(
                        consideredWord, wordComposer.isFirstCharCapitalized()));

        final boolean hasAutoCorrection;
        // TODO: using isCorrectionEnabled here is not very good. It's probably useless, because
        // any attempt to do auto-correction is already shielded with a test for this flag; at the
        // same time, it feels wrong that the SuggestedWord object includes information about
        // the current settings. It may also be useful to know, when the setting is off, whether
        // the word *would* have been auto-corrected.
        if (!isCorrectionEnabled || !allowsToBeAutoCorrected || !wordComposer.isComposingWord()
                || suggestionsSet.isEmpty() || wordComposer.hasDigits()
                || wordComposer.isMostlyCaps() || wordComposer.isResumed()
                || !mDictionaryFacilitator.hasMainDictionary()
                || SuggestedWordInfo.KIND_SHORTCUT == suggestionsSet.first().mKind) {
            // If we don't have a main dictionary, we never want to auto-correct. The reason for
            // this is, the user may have a contact whose name happens to match a valid word in
            // their language, and it will unexpectedly auto-correct. For example, if the user
            // types in English with no dictionary and has a "Will" in their contact list, "will"
            // would always auto-correct to "Will" which is unwanted. Hence, no main dict => no
            // auto-correct.
            // Also, shortcuts should never auto-correct unless they are whitelist entries.
            // TODO: we may want to have shortcut-only entries auto-correct in the future.
            hasAutoCorrection = false;
        } else {
            hasAutoCorrection = AutoCorrectionUtils.suggestionExceedsAutoCorrectionThreshold(
                    suggestionsSet.first(), consideredWord, mAutoCorrectionThreshold);
        }

        final ArrayList<SuggestedWordInfo> suggestionsContainer =
                CollectionUtils.newArrayList(suggestionsSet);
        final int suggestionsCount = suggestionsContainer.size();
        final boolean isFirstCharCapitalized = wordComposer.isFirstCharCapitalized();
        final boolean isAllUpperCase = wordComposer.isAllUpperCase();
        if (isFirstCharCapitalized || isAllUpperCase || 0 != trailingSingleQuotesCount) {
            for (int i = 0; i < suggestionsCount; ++i) {
                final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
                final SuggestedWordInfo transformedWordInfo = getTransformedSuggestedWordInfo(
                        wordInfo, mLocale, isAllUpperCase, isFirstCharCapitalized,
                        trailingSingleQuotesCount);
                suggestionsContainer.set(i, transformedWordInfo);
            }
        }

        for (int i = 0; i < suggestionsCount; ++i) {
            final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
            LatinImeLogger.onAddSuggestedWord(wordInfo.mWord.toString(),
                    wordInfo.mSourceDict.mDictType);
        }

        if (!TextUtils.isEmpty(typedWord)) {
            suggestionsContainer.add(0, new SuggestedWordInfo(typedWord,
                    SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED,
                    Dictionary.DICTIONARY_USER_TYPED,
                    SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                    SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
        }
        SuggestedWordInfo.removeDups(suggestionsContainer);

        final ArrayList<SuggestedWordInfo> suggestionsList;
        if (DBG && !suggestionsContainer.isEmpty()) {
            suggestionsList = getSuggestionsInfoListWithDebugInfo(typedWord, suggestionsContainer);
        } else {
            suggestionsList = suggestionsContainer;
        }

        callback.onGetSuggestedWords(new SuggestedWords(suggestionsList,
                // TODO: this first argument is lying. If this is a whitelisted word which is an
                // actual word, it says typedWordValid = false, which looks wrong. We should either
                // rename the attribute or change the value.
                !allowsToBeAutoCorrected /* typedWordValid */,
                hasAutoCorrection, /* willAutoCorrect */
                false /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */,
                !wordComposer.isComposingWord() /* isPrediction */, sequenceNumber));
    }

    // Retrieves suggestions for the batch input
    // and calls the callback function with the suggestions.
    private void getSuggestedWordsForBatchInput(final WordComposer wordComposer,
            final String prevWordForBigram, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions,
            final int sessionId, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        final BoundedTreeSet suggestionsSet = new BoundedTreeSet(sSuggestedWordInfoComparator,
                MAX_SUGGESTIONS);
        mDictionaryFacilitator.getSuggestions(wordComposer, prevWordForBigram, proximityInfo,
                blockOffensiveWords, additionalFeaturesOptions, sessionId, suggestionsSet);
        for (SuggestedWordInfo wordInfo : suggestionsSet) {
            LatinImeLogger.onAddSuggestedWord(wordInfo.mWord, wordInfo.mSourceDict.mDictType);
        }

        final ArrayList<SuggestedWordInfo> suggestionsContainer =
                CollectionUtils.newArrayList(suggestionsSet);
        final int suggestionsCount = suggestionsContainer.size();
        final boolean isFirstCharCapitalized = wordComposer.wasShiftedNoLock();
        final boolean isAllUpperCase = wordComposer.isAllUpperCase();
        if (isFirstCharCapitalized || isAllUpperCase) {
            for (int i = 0; i < suggestionsCount; ++i) {
                final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
                final SuggestedWordInfo transformedWordInfo = getTransformedSuggestedWordInfo(
                        wordInfo, mLocale, isAllUpperCase, isFirstCharCapitalized,
                        0 /* trailingSingleQuotesCount */);
                suggestionsContainer.set(i, transformedWordInfo);
            }
        }

        if (suggestionsContainer.size() > 1 && TextUtils.equals(suggestionsContainer.get(0).mWord,
                wordComposer.getRejectedBatchModeSuggestion())) {
            final SuggestedWordInfo rejected = suggestionsContainer.remove(0);
            suggestionsContainer.add(1, rejected);
        }
        SuggestedWordInfo.removeDups(suggestionsContainer);

        // For some reason some suggestions with MIN_VALUE are making their way here.
        // TODO: Find a more robust way to detect distractors.
        for (int i = suggestionsContainer.size() - 1; i >= 0; --i) {
            if (suggestionsContainer.get(i).mScore < SUPPRESS_SUGGEST_THRESHOLD) {
                suggestionsContainer.remove(i);
            }
        }

        // In the batch input mode, the most relevant suggested word should act as a "typed word"
        // (typedWordValid=true), not as an "auto correct word" (willAutoCorrect=false).
        callback.onGetSuggestedWords(new SuggestedWords(suggestionsContainer,
                true /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */,
                false /* isPrediction */, sequenceNumber));
    }

    private static ArrayList<SuggestedWordInfo> getSuggestionsInfoListWithDebugInfo(
            final String typedWord, final ArrayList<SuggestedWordInfo> suggestions) {
        final SuggestedWordInfo typedWordInfo = suggestions.get(0);
        typedWordInfo.setDebugString("+");
        final int suggestionsSize = suggestions.size();
        final ArrayList<SuggestedWordInfo> suggestionsList =
                CollectionUtils.newArrayList(suggestionsSize);
        suggestionsList.add(typedWordInfo);
        // Note: i here is the index in mScores[], but the index in mSuggestions is one more
        // than i because we added the typed word to mSuggestions without touching mScores.
        for (int i = 0; i < suggestionsSize - 1; ++i) {
            final SuggestedWordInfo cur = suggestions.get(i + 1);
            final float normalizedScore = BinaryDictionary.calcNormalizedScore(
                    typedWord, cur.toString(), cur.mScore);
            final String scoreInfoString;
            if (normalizedScore > 0) {
                scoreInfoString = String.format(
                        Locale.ROOT, "%d (%4.2f), %s", cur.mScore, normalizedScore,
                        cur.mSourceDict.mDictType);
            } else {
                scoreInfoString = Integer.toString(cur.mScore);
            }
            cur.setDebugString(scoreInfoString);
            suggestionsList.add(cur);
        }
        return suggestionsList;
    }

    private static final class SuggestedWordInfoComparator
            implements Comparator<SuggestedWordInfo> {
        // This comparator ranks the word info with the higher frequency first. That's because
        // that's the order we want our elements in.
        @Override
        public int compare(final SuggestedWordInfo o1, final SuggestedWordInfo o2) {
            if (o1.mScore > o2.mScore) return -1;
            if (o1.mScore < o2.mScore) return 1;
            if (o1.mCodePointCount < o2.mCodePointCount) return -1;
            if (o1.mCodePointCount > o2.mCodePointCount) return 1;
            return o1.mWord.compareTo(o2.mWord);
        }
    }
    private static final SuggestedWordInfoComparator sSuggestedWordInfoComparator =
            new SuggestedWordInfoComparator();

    /* package for test */ static SuggestedWordInfo getTransformedSuggestedWordInfo(
            final SuggestedWordInfo wordInfo, final Locale locale, final boolean isAllUpperCase,
            final boolean isFirstCharCapitalized, final int trailingSingleQuotesCount) {
        final StringBuilder sb = new StringBuilder(wordInfo.mWord.length());
        if (isAllUpperCase) {
            sb.append(wordInfo.mWord.toUpperCase(locale));
        } else if (isFirstCharCapitalized) {
            sb.append(StringUtils.capitalizeFirstCodePoint(wordInfo.mWord, locale));
        } else {
            sb.append(wordInfo.mWord);
        }
        // Appending quotes is here to help people quote words. However, it's not helpful
        // when they type words with quotes toward the end like "it's" or "didn't", where
        // it's more likely the user missed the last character (or didn't type it yet).
        final int quotesToAppend = trailingSingleQuotesCount
                - (-1 == wordInfo.mWord.indexOf(Constants.CODE_SINGLE_QUOTE) ? 0 : 1);
        for (int i = quotesToAppend - 1; i >= 0; --i) {
            sb.appendCodePoint(Constants.CODE_SINGLE_QUOTE);
        }
        return new SuggestedWordInfo(sb.toString(), wordInfo.mScore, wordInfo.mKind,
                wordInfo.mSourceDict, wordInfo.mIndexOfTouchPointOfSecondWord,
                wordInfo.mAutoCommitFirstWordConfidence);
    }

    public void close() {
        mDictionaryFacilitator.close();
    }
}
