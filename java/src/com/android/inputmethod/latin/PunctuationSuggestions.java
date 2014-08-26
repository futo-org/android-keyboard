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

package com.android.inputmethod.latin;

import com.android.inputmethod.keyboard.internal.KeySpecParser;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The extended {@link SuggestedWords} class to represent punctuation suggestions.
 *
 * Each punctuation specification string is the key specification that can be parsed by
 * {@link KeySpecParser}.
 */
public final class PunctuationSuggestions extends SuggestedWords {
    private PunctuationSuggestions(final ArrayList<SuggestedWordInfo> punctuationsList) {
        super(punctuationsList,
                null /* rawSuggestions */,
                false /* typedWordValid */,
                false /* hasAutoCorrectionCandidate */,
                false /* isObsoleteSuggestions */,
                INPUT_STYLE_NONE /* inputStyle */);
    }

    /**
     * Create new instance of {@link PunctuationSuggestions} from the array of punctuation key
     * specifications.
     *
     * @param punctuationSpecs The array of punctuation key specifications.
     * @return The {@link PunctuationSuggestions} object.
     */
    public static PunctuationSuggestions newPunctuationSuggestions(
            final String[] punctuationSpecs) {
        final ArrayList<SuggestedWordInfo> puncuationsList = new ArrayList<>();
        for (final String puncSpec : punctuationSpecs) {
            puncuationsList.add(newHardCodedWordInfo(puncSpec));
        }
        return new PunctuationSuggestions(puncuationsList);
    }

    /**
     * {@inheritDoc}
     * Note that {@link super#getWord(int)} returns a punctuation key specification text.
     * The suggested punctuation should be gotten by parsing the key specification.
     */
    @Override
    public String getWord(final int index) {
        final String keySpec = super.getWord(index);
        final int code = KeySpecParser.getCode(keySpec);
        return (code == Constants.CODE_OUTPUT_TEXT)
                ? KeySpecParser.getOutputText(keySpec)
                : StringUtils.newSingleCodePointString(code);
    }

    /**
     * {@inheritDoc}
     * Note that {@link super#getWord(int)} returns a punctuation key specification text.
     * The displayed text should be gotten by parsing the key specification.
     */
    @Override
    public String getLabel(final int index) {
        final String keySpec = super.getWord(index);
        return KeySpecParser.getLabel(keySpec);
    }

    /**
     * {@inheritDoc}
     * Note that {@link #getWord(int)} returns a suggested punctuation. We should create a
     * {@link SuggestedWordInfo} object that represents a hard coded word.
     */
    @Override
    public SuggestedWordInfo getInfo(final int index) {
        return newHardCodedWordInfo(getWord(index));
    }

    /**
     * The predicator to tell whether this object represents punctuation suggestions.
     * @return true if this object represents punctuation suggestions.
     */
    @Override
    public boolean isPunctuationSuggestions() {
        return true;
    }

    @Override
    public String toString() {
        return "PunctuationSuggestions: "
                + " words=" + Arrays.toString(mSuggestedWordInfoList.toArray());
    }

    private static SuggestedWordInfo newHardCodedWordInfo(final String keySpec) {
        return new SuggestedWordInfo(keySpec, SuggestedWordInfo.MAX_SCORE,
                SuggestedWordInfo.KIND_HARDCODED,
                Dictionary.DICTIONARY_HARDCODED,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */);
    }
}
