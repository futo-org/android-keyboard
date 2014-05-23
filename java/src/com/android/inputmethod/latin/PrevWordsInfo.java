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

import android.util.Log;

// TODO: Support multiple previous words for n-gram.
public class PrevWordsInfo {
    public static final PrevWordsInfo BEGINNING_OF_SENTENCE = new PrevWordsInfo();

    // The previous word. May be null after resetting and before starting a new composing word, or
    // when there is no context like at the start of text for example. It can also be set to null
    // externally when the user enters a separator that does not let bigrams across, like a period
    // or a comma.
    public final String mPrevWord;

    // TODO: Have sentence separator.
    // Whether the current context is beginning of sentence or not.
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
}
