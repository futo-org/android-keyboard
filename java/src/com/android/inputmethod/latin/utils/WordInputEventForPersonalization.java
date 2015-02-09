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

package com.android.inputmethod.latin.utils;

import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.NgramContext;
import com.android.inputmethod.latin.common.StringUtils;
import com.android.inputmethod.latin.define.DecoderSpecificConstants;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Note: this class is used as a parameter type of a native method. You should be careful when you
// rename this class or field name. See BinaryDictionary#addMultipleDictionaryEntriesNative().
public final class WordInputEventForPersonalization {
    private static final String TAG = WordInputEventForPersonalization.class.getSimpleName();
    private static final boolean DEBUG_TOKEN = false;

    public final int[] mTargetWord;
    public final int mPrevWordsCount;
    public final int[][] mPrevWordArray =
            new int[DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM][];
    public final boolean[] mIsPrevWordBeginningOfSentenceArray =
            new boolean[DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM];
    // Time stamp in seconds.
    public final int mTimestamp;

    @UsedForTesting
    public WordInputEventForPersonalization(final CharSequence targetWord,
            final NgramContext ngramContext, final int timestamp) {
        mTargetWord = StringUtils.toCodePointArray(targetWord);
        mPrevWordsCount = ngramContext.getPrevWordCount();
        ngramContext.outputToArray(mPrevWordArray, mIsPrevWordBeginningOfSentenceArray);
        mTimestamp = timestamp;
    }

    // Process a list of words and return a list of {@link WordInputEventForPersonalization}
    // objects.
    public static ArrayList<WordInputEventForPersonalization> createInputEventFrom(
            final List<String> tokens, final int timestamp,
            final SpacingAndPunctuations spacingAndPunctuations, final Locale locale) {
        final ArrayList<WordInputEventForPersonalization> inputEvents = new ArrayList<>();
        final int N = tokens.size();
        NgramContext ngramContext = NgramContext.EMPTY_PREV_WORDS_INFO;
        for (int i = 0; i < N; ++i) {
            final String tempWord = tokens.get(i);
            if (StringUtils.isEmptyStringOrWhiteSpaces(tempWord)) {
                // just skip this token
                if (DEBUG_TOKEN) {
                    Log.d(TAG, "--- isEmptyStringOrWhiteSpaces: \"" + tempWord + "\"");
                }
                continue;
            }
            if (!DictionaryInfoUtils.looksValidForDictionaryInsertion(
                    tempWord, spacingAndPunctuations)) {
                if (DEBUG_TOKEN) {
                    Log.d(TAG, "--- not looksValidForDictionaryInsertion: \""
                            + tempWord + "\"");
                }
                // Sentence terminator found. Split.
                // TODO: Detect whether the context is beginning-of-sentence.
                ngramContext = NgramContext.EMPTY_PREV_WORDS_INFO;
                continue;
            }
            if (DEBUG_TOKEN) {
                Log.d(TAG, "--- word: \"" + tempWord + "\"");
            }
            final WordInputEventForPersonalization inputEvent =
                    detectWhetherVaildWordOrNotAndGetInputEvent(
                            ngramContext, tempWord, timestamp, locale);
            if (inputEvent == null) {
                continue;
            }
            inputEvents.add(inputEvent);
            ngramContext = ngramContext.getNextNgramContext(new NgramContext.WordInfo(tempWord));
        }
        return inputEvents;
    }

    private static WordInputEventForPersonalization detectWhetherVaildWordOrNotAndGetInputEvent(
            final NgramContext ngramContext, final String targetWord, final int timestamp,
            final Locale locale) {
        if (locale == null) {
            return null;
        }
        return new WordInputEventForPersonalization(targetWord, ngramContext, timestamp);
    }
}
