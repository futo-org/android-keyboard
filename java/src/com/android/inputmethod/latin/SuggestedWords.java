/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.view.inputmethod.CompletionInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SuggestedWords {
    public static final SuggestedWords EMPTY = new SuggestedWords(null, false, false, false, null);

    public final List<CharSequence> mWords;
    public final boolean mIsApplicationSpecifiedCompletions;
    public final boolean mTypedWordValid;
    public final boolean mHasMinimalSuggestion;
    public final Object[] mDebugInfo;

    private SuggestedWords(List<CharSequence> words, boolean isApplicationSpecifiedCompletions,
            boolean typedWordValid, boolean hasMinamlSuggestion, Object[] debugInfo) {
        if (words != null) {
            mWords = words;
        } else {
            mWords = Collections.emptyList();
        }
        mIsApplicationSpecifiedCompletions = isApplicationSpecifiedCompletions;
        mTypedWordValid = typedWordValid;
        mHasMinimalSuggestion = hasMinamlSuggestion;
        mDebugInfo = debugInfo;
    }

    public int size() {
        return mWords.size();
    }

    public CharSequence getWord(int pos) {
        return mWords.get(pos);
    }

    public boolean hasAutoCorrectionWord() {
        return mHasMinimalSuggestion && size() > 1 && !mTypedWordValid;
    }

    public boolean hasWordAboveAutoCorrectionScoreThreshold() {
        return mHasMinimalSuggestion && ((size() > 1 && !mTypedWordValid) || mTypedWordValid);
    }

    public static class Builder {
        private List<CharSequence> mWords;
        private boolean mIsCompletions;
        private boolean mTypedWordValid;
        private boolean mHasMinimalSuggestion;
        private Object[] mDebugInfo;

        public Builder() {
            // Nothing to do here.
        }

        public Builder addWords(List<CharSequence> words) {
            for (final CharSequence word : words)
                addWord(word);
            return this;
        }

        public Builder setDebugInfo(Object[] debuginfo) {
            mDebugInfo = debuginfo;
            return this;
        }

        public Builder addWord(int pos, CharSequence word) {
            if (mWords == null)
                mWords = new ArrayList<CharSequence>();
            mWords.add(pos, word);
            return this;
        }

        public Builder addWord(CharSequence word) {
            if (mWords == null)
                mWords = new ArrayList<CharSequence>();
            mWords.add(word);
            return this;
        }

        public Builder setApplicationSpecifiedCompletions(CompletionInfo[] infos) {
            for (CompletionInfo info : infos)
                addWord(info.getText());
            mIsCompletions = true;
            return this;
        }

        public Builder setTypedWordValid(boolean typedWordValid) {
            mTypedWordValid = typedWordValid;
            return this;
        }

        public Builder setHasMinimalSuggestion(boolean hasMinamlSuggestion) {
            mHasMinimalSuggestion = hasMinamlSuggestion;
            return this;
        }

        // Should get rid of the first one (what the user typed previously) from suggestions
        // and replace it with what the user currently typed.
        public Builder addTypedWordAndPreviousSuggestions(CharSequence typedWord,
                SuggestedWords previousSuggestions) {
            if (mWords != null) mWords.clear();
            addWord(typedWord);
            final int previousSize = previousSuggestions.size();
            for (int pos = 1; pos < previousSize; pos++)
                addWord(previousSuggestions.getWord(pos));
            mIsCompletions = false;
            mTypedWordValid = false;
            mHasMinimalSuggestion = false;
            return this;
        }

        public SuggestedWords build() {
            return new SuggestedWords(mWords, mIsCompletions, mTypedWordValid,
                    mHasMinimalSuggestion, mDebugInfo);
        }

        public int size() {
            return mWords == null ? 0 : mWords.size();
        }

        public CharSequence getWord(int pos) {
            return mWords.get(pos);
        }
    }
}
