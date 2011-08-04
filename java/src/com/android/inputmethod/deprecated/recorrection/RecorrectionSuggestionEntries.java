/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.deprecated.recorrection;

import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.WordComposer;

import android.text.TextUtils;

public class RecorrectionSuggestionEntries {
    public final CharSequence mChosenWord;
    public final WordComposer mWordComposer;

    public RecorrectionSuggestionEntries(CharSequence chosenWord, WordComposer wordComposer) {
        mChosenWord = chosenWord;
        mWordComposer = wordComposer;
    }

    public CharSequence getChosenWord() {
        return mChosenWord;
    }

    public CharSequence getOriginalWord() {
        return mWordComposer.getTypedWord();
    }

    public SuggestedWords.Builder getAlternatives(
            Suggest suggest, KeyboardSwitcher keyboardSwitcher) {
        return getTypedSuggestions(suggest, keyboardSwitcher, mWordComposer);
    }

    @Override
    public int hashCode() {
        return mChosenWord.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CharSequence && TextUtils.equals(mChosenWord, (CharSequence)o);
    }

    private static SuggestedWords.Builder getTypedSuggestions(
            Suggest suggest, KeyboardSwitcher keyboardSwitcher, WordComposer word) {
        return suggest.getSuggestedWordBuilder(keyboardSwitcher.getKeyboardView(), word, null,
                keyboardSwitcher.getLatinKeyboard().getProximityInfo());
    }
}
