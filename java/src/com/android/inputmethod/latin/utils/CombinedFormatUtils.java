/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.ProbabilityInfo;
import com.android.inputmethod.latin.makedict.WeightedString;
import com.android.inputmethod.latin.makedict.WordProperty;

import java.util.HashMap;

public class CombinedFormatUtils {
    public static final String DICTIONARY_TAG = "dictionary";
    public static final String BIGRAM_TAG = "bigram";
    public static final String SHORTCUT_TAG = "shortcut";
    public static final String PROBABILITY_TAG = "f";
    public static final String HISTORICAL_INFO_TAG = "historicalInfo";
    public static final String HISTORICAL_INFO_SEPARATOR = ":";
    public static final String WORD_TAG = "word";
    public static final String BEGINNING_OF_SENTENCE_TAG = "beginning_of_sentence";
    public static final String NOT_A_WORD_TAG = "not_a_word";
    public static final String BLACKLISTED_TAG = "blacklisted";

    public static String formatAttributeMap(final HashMap<String, String> attributeMap) {
        final StringBuilder builder = new StringBuilder();
        builder.append(DICTIONARY_TAG + "=");
        if (attributeMap.containsKey(DictionaryHeader.DICTIONARY_ID_KEY)) {
            builder.append(attributeMap.get(DictionaryHeader.DICTIONARY_ID_KEY));
        }
        for (final String key : attributeMap.keySet()) {
            if (key.equals(DictionaryHeader.DICTIONARY_ID_KEY)) {
                continue;
            }
            final String value = attributeMap.get(key);
            builder.append("," + key + "=" + value);
        }
        builder.append("\n");
        return builder.toString();
    }

    public static String formatWordProperty(final WordProperty wordProperty) {
        final StringBuilder builder = new StringBuilder();
        builder.append(" " + WORD_TAG + "=" + wordProperty.mWord);
        builder.append(",");
        builder.append(formatProbabilityInfo(wordProperty.mProbabilityInfo));
        if (wordProperty.mIsBeginningOfSentence) {
            builder.append("," + BEGINNING_OF_SENTENCE_TAG + "=true");
        }
        if (wordProperty.mIsNotAWord) {
            builder.append("," + NOT_A_WORD_TAG + "=true");
        }
        if (wordProperty.mIsBlacklistEntry) {
            builder.append("," + BLACKLISTED_TAG + "=true");
        }
        builder.append("\n");
        if (wordProperty.mShortcutTargets != null) {
            for (final WeightedString shortcutTarget : wordProperty.mShortcutTargets) {
                builder.append("  " + SHORTCUT_TAG + "=" + shortcutTarget.mWord);
                builder.append(",");
                builder.append(formatProbabilityInfo(shortcutTarget.mProbabilityInfo));
                builder.append("\n");
            }
        }
        if (wordProperty.mBigrams != null) {
            for (final WeightedString bigram : wordProperty.mBigrams) {
                builder.append("  " + BIGRAM_TAG + "=" + bigram.mWord);
                builder.append(",");
                builder.append(formatProbabilityInfo(bigram.mProbabilityInfo));
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    public static String formatProbabilityInfo(final ProbabilityInfo probabilityInfo) {
        final StringBuilder builder = new StringBuilder();
        builder.append(PROBABILITY_TAG + "=" + probabilityInfo.mProbability);
        if (probabilityInfo.hasHistoricalInfo()) {
            builder.append(",");
            builder.append(HISTORICAL_INFO_TAG + "=");
            builder.append(probabilityInfo.mTimestamp);
            builder.append(HISTORICAL_INFO_SEPARATOR);
            builder.append(probabilityInfo.mLevel);
            builder.append(HISTORICAL_INFO_SEPARATOR);
            builder.append(probabilityInfo.mCount);
        }
        return builder.toString();
    }
}
