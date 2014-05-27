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

/**
 * Class to represent information of previous words. This class is used to add n-gram entries
 * into binary dictionaries, to get predictions, and to get suggestions.
 */
// TODO: Support multiple previous words for n-gram.
public class PrevWordsInfo {
    public static final PrevWordsInfo EMPTY_PREV_WORDS_INFO = new PrevWordsInfo(null);
    public static final PrevWordsInfo BEGINNING_OF_SENTENCE = new PrevWordsInfo();

    // The word immediately before the considered word. null means we don't have any context
    // including the "beginning of sentence context" - we just don't know what to predict.
    // An example of that is after a comma.
    // For simplicity of implementation, this may also be null transiently after the WordComposer
    // was reset and before starting a new composing word, but we should never be calling
    // getSuggetions* in this situation.
    // This is an empty string when mIsBeginningOfSentence is true.
    public final String mPrevWord;

    // TODO: Have sentence separator.
    // Whether the current context is beginning of sentence or not. This is true when composing at
    // the beginning of an input field or composing a word after a sentence separator.
    public final boolean mIsBeginningOfSentence;

    // Beginning of sentence.
    public PrevWordsInfo() {
        mPrevWord = "";
        mIsBeginningOfSentence = true;
    }

    public PrevWordsInfo(final String prevWord) {
        mPrevWord = prevWord;
        mIsBeginningOfSentence = false;
    }

    public boolean isValid() {
        return mPrevWord != null;
    }

    @Override
    public String toString() {
        return "PrevWord: " + mPrevWord + ", isBeginningOfSentence: "
                    + mIsBeginningOfSentence + ".";
    }
}
