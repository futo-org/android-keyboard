/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.futo.inputmethod.latin.settings;

import android.content.res.Resources;

import org.futo.inputmethod.annotations.UsedForTesting;
import org.futo.inputmethod.keyboard.internal.MoreKeySpec;
import org.futo.inputmethod.latin.PunctuationSuggestions;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.StringUtils;

import java.util.Arrays;
import java.util.Locale;

public final class SpacingAndPunctuations {
    private final int[] mSortedSymbolsPrecededBySpace;
    private final int[] mSortedSymbolsOptionallyPrecededBySpace;
    private final int[] mSortedSymbolsFollowedBySpace;
    private final int[] mSortedSymbolsFollowedBySpaceIffPrecededBySpace;
    private final int[] mSortedSymbolsClusteringTogether;
    private final int[] mSortedWordConnectors;
    public final int[] mSortedWordSeparators;
    public final PunctuationSuggestions mSuggestPuncList;
    private final int mSentenceSeparator;
    private final int mAbbreviationMarker;
    private final int[] mSortedSentenceTerminators;
    public final String mSentenceSeparatorAndSpace;
    public final boolean mCurrentLanguageHasSpaces;
    public final boolean mUsesAmericanTypography;
    public final boolean mUsesGermanRules;

    public SpacingAndPunctuations(final Resources res) {
        // To be able to binary search the code point. See {@link #isUsuallyPrecededBySpace(int)}.
        mSortedSymbolsPrecededBySpace = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_preceded_by_space));
        // To be able to binary search the code point. See {@link #isUsuallyFollowedBySpace(int)}.
        mSortedSymbolsFollowedBySpace = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_followed_by_space));
        mSortedSymbolsOptionallyPrecededBySpace = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_optionally_preceded_by_space));
        mSortedSymbolsFollowedBySpaceIffPrecededBySpace = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_followed_by_space_iff_preceded_by_space));
        mSortedSymbolsClusteringTogether = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_clustering_together));
        // To be able to binary search the code point. See {@link #isWordConnector(int)}.
        mSortedWordConnectors = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_word_connectors));
        mSortedWordSeparators = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_word_separators));
        mSortedSentenceTerminators = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_sentence_terminators));
        mSentenceSeparator = res.getInteger(R.integer.sentence_separator);
        mAbbreviationMarker = res.getInteger(R.integer.abbreviation_marker);
        mSentenceSeparatorAndSpace = new String(new int[] {
                mSentenceSeparator, Constants.CODE_SPACE }, 0, 2);
        mCurrentLanguageHasSpaces = res.getBoolean(R.bool.current_language_has_spaces);
        final Locale locale = res.getConfiguration().locale;
        // Heuristic: we use American Typography rules because it's the most common rules for all
        // English variants. German rules (not "German typography") also have small gotchas.
        mUsesAmericanTypography = Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
        mUsesGermanRules = Locale.GERMAN.getLanguage().equals(locale.getLanguage());
        final String[] suggestPuncsSpec = MoreKeySpec.splitKeySpecs(
                res.getString(R.string.suggested_punctuations));
        mSuggestPuncList = PunctuationSuggestions.newPunctuationSuggestions(suggestPuncsSpec);
    }

    @UsedForTesting
    public SpacingAndPunctuations(final SpacingAndPunctuations model,
            final int[] overrideSortedWordSeparators) {
        mSortedSymbolsPrecededBySpace = model.mSortedSymbolsPrecededBySpace;
        mSortedSymbolsFollowedBySpace = model.mSortedSymbolsFollowedBySpace;
        mSortedSymbolsOptionallyPrecededBySpace = model.mSortedSymbolsOptionallyPrecededBySpace;
        mSortedSymbolsFollowedBySpaceIffPrecededBySpace = model.mSortedSymbolsFollowedBySpaceIffPrecededBySpace;
        mSortedSymbolsClusteringTogether = model.mSortedSymbolsClusteringTogether;
        mSortedWordConnectors = model.mSortedWordConnectors;
        mSortedWordSeparators = overrideSortedWordSeparators;
        mSortedSentenceTerminators = model.mSortedSentenceTerminators;
        mSuggestPuncList = model.mSuggestPuncList;
        mSentenceSeparator = model.mSentenceSeparator;
        mAbbreviationMarker = model.mAbbreviationMarker;
        mSentenceSeparatorAndSpace = model.mSentenceSeparatorAndSpace;
        mCurrentLanguageHasSpaces = model.mCurrentLanguageHasSpaces;
        mUsesAmericanTypography = model.mUsesAmericanTypography;
        mUsesGermanRules = model.mUsesGermanRules;
    }

    public boolean isWordSeparator(final int code) {
        return Arrays.binarySearch(mSortedWordSeparators, code) >= 0;
    }

    public boolean isWordConnector(final int code) {
        return Arrays.binarySearch(mSortedWordConnectors, code) >= 0;
    }

    public boolean isWordCodePoint(final int code) {
        return Character.isLetter(code) || isWordConnector(code);
    }

    public boolean isUsuallyPrecededBySpace(final int code) {
        return Arrays.binarySearch(mSortedSymbolsPrecededBySpace, code) >= 0;
    }

    public boolean isOptionallyPrecededBySpace(final int code) {
        return Arrays.binarySearch(mSortedSymbolsOptionallyPrecededBySpace, code) >= 0;
    }

    public boolean isUsuallyFollowedBySpace(final int code) {
        return Arrays.binarySearch(mSortedSymbolsFollowedBySpace, code) >= 0;
    }

    public boolean isUsuallyFollowedBySpaceIffPrecededBySpace(final int code) {
        return Arrays.binarySearch(mSortedSymbolsFollowedBySpaceIffPrecededBySpace, code) >= 0;
    }

    public boolean isClusteringSymbol(final int code) {
        return Arrays.binarySearch(mSortedSymbolsClusteringTogether, code) >= 0;
    }

    public boolean isSentenceTerminator(final int code) {
        return Arrays.binarySearch(mSortedSentenceTerminators, code) >= 0;
    }

    public boolean isAbbreviationMarker(final int code) {
        return code == mAbbreviationMarker;
    }

    public boolean isSentenceSeparator(final int code) {
        return code == mSentenceSeparator;
    }

    public String dump() {
        final StringBuilder sb = new StringBuilder();
        sb.append("mSortedSymbolsPrecededBySpace = ");
        sb.append("" + Arrays.toString(mSortedSymbolsPrecededBySpace));
        sb.append("\n   mSortedSymbolsFollowedBySpace = ");
        sb.append("" + Arrays.toString(mSortedSymbolsFollowedBySpace));
        sb.append("\n   mSortedWordConnectors = ");
        sb.append("" + Arrays.toString(mSortedWordConnectors));
        sb.append("\n   mSortedWordSeparators = ");
        sb.append("" + Arrays.toString(mSortedWordSeparators));
        sb.append("\n   mSuggestPuncList = ");
        sb.append("" + mSuggestPuncList);
        sb.append("\n   mSentenceSeparator = ");
        sb.append("" + mSentenceSeparator);
        sb.append("\n   mSentenceSeparatorAndSpace = ");
        sb.append("" + mSentenceSeparatorAndSpace);
        sb.append("\n   mCurrentLanguageHasSpaces = ");
        sb.append("" + mCurrentLanguageHasSpaces);
        sb.append("\n   mUsesAmericanTypography = ");
        sb.append("" + mUsesAmericanTypography);
        sb.append("\n   mUsesGermanRules = ");
        sb.append("" + mUsesGermanRules);
        return sb.toString();
    }
}
