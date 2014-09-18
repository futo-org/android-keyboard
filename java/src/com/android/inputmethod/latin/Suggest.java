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
import com.android.inputmethod.latin.define.DebugFlags;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;
import com.android.inputmethod.latin.utils.AutoCorrectionUtils;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import com.android.inputmethod.latin.utils.StringUtils;
import com.android.inputmethod.latin.utils.SuggestionResults;

import java.util.ArrayList;
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
    public static final int SESSION_ID_TYPING = 0;
    public static final int SESSION_ID_GESTURE = 0;

    // Close to -2**31
    private static final int SUPPRESS_SUGGEST_THRESHOLD = -2000000000;

    private static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private final DictionaryFacilitator mDictionaryFacilitator;

    private float mAutoCorrectionThreshold;

    public Suggest(final DictionaryFacilitator dictionaryFacilitator) {
        mDictionaryFacilitator = dictionaryFacilitator;
    }

    public Locale getLocale() {
        return mDictionaryFacilitator.getLocale();
    }

    public void setAutoCorrectionThreshold(final float threshold) {
        mAutoCorrectionThreshold = threshold;
    }

    public interface OnGetSuggestedWordsCallback {
        public void onGetSuggestedWords(final SuggestedWords suggestedWords);
    }

    public void getSuggestedWords(final WordComposer wordComposer,
            final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final boolean isCorrectionEnabled, final int inputStyle, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        if (wordComposer.isBatchMode()) {
            getSuggestedWordsForBatchInput(wordComposer, prevWordsInfo, proximityInfo,
                    settingsValuesForSuggestion, inputStyle, sequenceNumber, callback);
        } else {
            getSuggestedWordsForNonBatchInput(wordComposer, prevWordsInfo, proximityInfo,
                    settingsValuesForSuggestion, inputStyle, isCorrectionEnabled,
                    sequenceNumber, callback);
        }
    }

    private static ArrayList<SuggestedWordInfo> getTransformedSuggestedWordInfoList(
            final WordComposer wordComposer, final SuggestionResults results,
            final int trailingSingleQuotesCount) {
        final boolean shouldMakeSuggestionsAllUpperCase = wordComposer.isAllUpperCase()
                && !wordComposer.isResumed();
        final boolean isOnlyFirstCharCapitalized =
                wordComposer.isOrWillBeOnlyFirstCharCapitalized();

        final ArrayList<SuggestedWordInfo> suggestionsContainer = new ArrayList<>(results);
        final int suggestionsCount = suggestionsContainer.size();
        if (isOnlyFirstCharCapitalized || shouldMakeSuggestionsAllUpperCase
                || 0 != trailingSingleQuotesCount) {
            for (int i = 0; i < suggestionsCount; ++i) {
                final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
                final SuggestedWordInfo transformedWordInfo = getTransformedSuggestedWordInfo(
                        wordInfo, results.mLocale, shouldMakeSuggestionsAllUpperCase,
                        isOnlyFirstCharCapitalized, trailingSingleQuotesCount);
                suggestionsContainer.set(i, transformedWordInfo);
            }
        }
        return suggestionsContainer;
    }

    private static String getWhitelistedWordOrNull(final ArrayList<SuggestedWordInfo> suggestions) {
        if (suggestions.isEmpty()) {
            return null;
        }
        final SuggestedWordInfo firstSuggestedWordInfo = suggestions.get(0);
        if (!firstSuggestedWordInfo.isKindOf(SuggestedWordInfo.KIND_WHITELIST)) {
            return null;
        }
        return firstSuggestedWordInfo.mWord;
    }

    // Retrieves suggestions for non-batch input (typing, recorrection, predictions...)
    // and calls the callback function with the suggestions.
    private void getSuggestedWordsForNonBatchInput(final WordComposer wordComposer,
            final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final int inputStyleIfNotPrediction, final boolean isCorrectionEnabled,
            final int sequenceNumber, final OnGetSuggestedWordsCallback callback) {
        final String typedWord = wordComposer.getTypedWord();
        final int trailingSingleQuotesCount = StringUtils.getTrailingSingleQuotesCount(typedWord);
        final String consideredWord = trailingSingleQuotesCount > 0
                ? typedWord.substring(0, typedWord.length() - trailingSingleQuotesCount)
                : typedWord;

        final SuggestionResults suggestionResults = mDictionaryFacilitator.getSuggestionResults(
                wordComposer, prevWordsInfo, proximityInfo, settingsValuesForSuggestion,
                SESSION_ID_TYPING);
        final ArrayList<SuggestedWordInfo> suggestionsContainer =
                getTransformedSuggestedWordInfoList(wordComposer, suggestionResults,
                        trailingSingleQuotesCount);
        final boolean didRemoveTypedWord =
                SuggestedWordInfo.removeDups(wordComposer.getTypedWord(), suggestionsContainer);

        final String whitelistedWord = getWhitelistedWordOrNull(suggestionsContainer);
        final boolean resultsArePredictions = !wordComposer.isComposingWord();

        // We allow auto-correction if we have a whitelisted word, or if the word had more than
        // one char and was not suggested.
        final boolean allowsToBeAutoCorrected = (null != whitelistedWord)
                || (consideredWord.length() > 1 && !didRemoveTypedWord);

        final boolean hasAutoCorrection;
        // TODO: using isCorrectionEnabled here is not very good. It's probably useless, because
        // any attempt to do auto-correction is already shielded with a test for this flag; at the
        // same time, it feels wrong that the SuggestedWord object includes information about
        // the current settings. It may also be useful to know, when the setting is off, whether
        // the word *would* have been auto-corrected.
        if (!isCorrectionEnabled || !allowsToBeAutoCorrected || resultsArePredictions
                || suggestionResults.isEmpty() || wordComposer.hasDigits()
                || wordComposer.isMostlyCaps() || wordComposer.isResumed()
                || !mDictionaryFacilitator.hasInitializedMainDictionary()
                || suggestionResults.first().isKindOf(SuggestedWordInfo.KIND_SHORTCUT)) {
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
                    suggestionResults.first(), consideredWord, mAutoCorrectionThreshold);
        }

        if (!TextUtils.isEmpty(typedWord)) {
            suggestionsContainer.add(0, new SuggestedWordInfo(typedWord,
                    SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED,
                    Dictionary.DICTIONARY_USER_TYPED,
                    SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                    SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
        }

        final ArrayList<SuggestedWordInfo> suggestionsList;
        if (DBG && !suggestionsContainer.isEmpty()) {
            suggestionsList = getSuggestionsInfoListWithDebugInfo(typedWord, suggestionsContainer);
        } else {
            suggestionsList = suggestionsContainer;
        }

        final int inputStyle;
        if (resultsArePredictions) {
            inputStyle = suggestionResults.mIsBeginningOfSentence
                    ? SuggestedWords.INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION
                    : SuggestedWords.INPUT_STYLE_PREDICTION;
        } else {
            inputStyle = inputStyleIfNotPrediction;
        }
        callback.onGetSuggestedWords(new SuggestedWords(suggestionsList,
                suggestionResults.mRawSuggestions,
                // TODO: this first argument is lying. If this is a whitelisted word which is an
                // actual word, it says typedWordValid = false, which looks wrong. We should either
                // rename the attribute or change the value.
                !resultsArePredictions && !allowsToBeAutoCorrected /* typedWordValid */,
                hasAutoCorrection /* willAutoCorrect */,
                false /* isObsoleteSuggestions */, inputStyle, sequenceNumber));
    }

    // Retrieves suggestions for the batch input
    // and calls the callback function with the suggestions.
    private void getSuggestedWordsForBatchInput(final WordComposer wordComposer,
            final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final int inputStyle, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        final SuggestionResults suggestionResults = mDictionaryFacilitator.getSuggestionResults(
                wordComposer, prevWordsInfo, proximityInfo, settingsValuesForSuggestion,
                SESSION_ID_GESTURE);
        final ArrayList<SuggestedWordInfo> suggestionsContainer =
                new ArrayList<>(suggestionResults);
        final int suggestionsCount = suggestionsContainer.size();
        final boolean isFirstCharCapitalized = wordComposer.wasShiftedNoLock();
        final boolean isAllUpperCase = wordComposer.isAllUpperCase();
        if (isFirstCharCapitalized || isAllUpperCase) {
            for (int i = 0; i < suggestionsCount; ++i) {
                final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
                final SuggestedWordInfo transformedWordInfo = getTransformedSuggestedWordInfo(
                        wordInfo, suggestionResults.mLocale, isAllUpperCase, isFirstCharCapitalized,
                        0 /* trailingSingleQuotesCount */);
                suggestionsContainer.set(i, transformedWordInfo);
            }
        }

        if (suggestionsContainer.size() > 1 && TextUtils.equals(suggestionsContainer.get(0).mWord,
                wordComposer.getRejectedBatchModeSuggestion())) {
            final SuggestedWordInfo rejected = suggestionsContainer.remove(0);
            suggestionsContainer.add(1, rejected);
        }
        SuggestedWordInfo.removeDups(null /* typedWord */, suggestionsContainer);

        // For some reason some suggestions with MIN_VALUE are making their way here.
        // TODO: Find a more robust way to detect distractors.
        for (int i = suggestionsContainer.size() - 1; i >= 0; --i) {
            if (suggestionsContainer.get(i).mScore < SUPPRESS_SUGGEST_THRESHOLD) {
                suggestionsContainer.remove(i);
            }
        }

        // In the batch input mode, the most relevant suggested word should act as a "typed word"
        // (typedWordValid=true), not as an "auto correct word" (willAutoCorrect=false).
        // Note that because this method is never used to get predictions, there is no need to
        // modify inputType such in getSuggestedWordsForNonBatchInput.
        callback.onGetSuggestedWords(new SuggestedWords(suggestionsContainer,
                suggestionResults.mRawSuggestions,
                true /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isObsoleteSuggestions */,
                inputStyle, sequenceNumber));
    }

    private static ArrayList<SuggestedWordInfo> getSuggestionsInfoListWithDebugInfo(
            final String typedWord, final ArrayList<SuggestedWordInfo> suggestions) {
        final SuggestedWordInfo typedWordInfo = suggestions.get(0);
        typedWordInfo.setDebugString("+");
        final int suggestionsSize = suggestions.size();
        final ArrayList<SuggestedWordInfo> suggestionsList = new ArrayList<>(suggestionsSize);
        suggestionsList.add(typedWordInfo);
        // Note: i here is the index in mScores[], but the index in mSuggestions is one more
        // than i because we added the typed word to mSuggestions without touching mScores.
        for (int i = 0; i < suggestionsSize - 1; ++i) {
            final SuggestedWordInfo cur = suggestions.get(i + 1);
            final float normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(
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

    /* package for test */ static SuggestedWordInfo getTransformedSuggestedWordInfo(
            final SuggestedWordInfo wordInfo, final Locale locale, final boolean isAllUpperCase,
            final boolean isOnlyFirstCharCapitalized, final int trailingSingleQuotesCount) {
        final StringBuilder sb = new StringBuilder(wordInfo.mWord.length());
        if (isAllUpperCase) {
            sb.append(wordInfo.mWord.toUpperCase(locale));
        } else if (isOnlyFirstCharCapitalized) {
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
        return new SuggestedWordInfo(sb.toString(), wordInfo.mScore, wordInfo.mKindAndFlags,
                wordInfo.mSourceDict, wordInfo.mIndexOfTouchPointOfSecondWord,
                wordInfo.mAutoCommitFirstWordConfidence);
    }
}
