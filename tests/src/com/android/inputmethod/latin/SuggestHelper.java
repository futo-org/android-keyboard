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

import android.content.Context;
import android.text.TextUtils;

import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.internal.KeyboardBuilder;
import com.android.inputmethod.keyboard.internal.KeyboardParams;

import java.io.File;
import java.util.Locale;

public class SuggestHelper {
    protected final Suggest mSuggest;
    protected int mCorrectionMode;
    protected final Keyboard mKeyboard;
    private final KeyDetector mKeyDetector;

    public static final int ALPHABET_KEYBOARD = com.android.inputmethod.latin.R.xml.kbd_qwerty;

    public SuggestHelper(Context context, int dictionaryId, KeyboardId keyboardId) {
        // Use null as the locale for Suggest so as to force it to use the internal dictionary
        // (and not try to find a dictionary provider for a specified locale)
        mSuggest = new Suggest(context, dictionaryId, null);
        mKeyboard = new KeyboardBuilder<KeyboardParams>(context, new KeyboardParams())
                .load(ALPHABET_KEYBOARD, keyboardId).build();
        mKeyDetector = new KeyDetector(0);
        init();
    }

    protected SuggestHelper(final Context context, final File dictionaryPath,
            final long startOffset, final long length, final KeyboardId keyboardId,
            final Locale locale) {
        mSuggest = new Suggest(context, dictionaryPath, startOffset, length, null, locale);
        mKeyboard = new KeyboardBuilder<KeyboardParams>(context, new KeyboardParams())
                .load(ALPHABET_KEYBOARD, keyboardId).build();
        mKeyDetector = new KeyDetector(0);
        init();
    }

    private void init() {
        setCorrectionMode(Suggest.CORRECTION_FULL);
        mKeyDetector.setKeyboard(mKeyboard, 0, 0);
        mKeyDetector.setProximityCorrectionEnabled(true);
        mKeyDetector.setProximityThreshold(mKeyboard.mMostCommonKeyWidth);
    }

    public void setCorrectionMode(int correctionMode) {
        mCorrectionMode = correctionMode;
    }

    public boolean hasMainDictionary() {
        return mSuggest.hasMainDictionary();
    }

    protected WordComposer createWordComposer(CharSequence s) {
        WordComposer word = new WordComposer();
        word.setComposingWord(s, mKeyboard, mKeyDetector);
        return word;
    }

    public boolean isValidWord(CharSequence typed) {
        return AutoCorrection.isValidWord(mSuggest.getUnigramDictionaries(),
                typed, false);
    }

    // TODO: This may be slow, but is OK for test so far.
    public SuggestedWords getSuggestions(CharSequence typed) {
        return mSuggest.getSuggestions(createWordComposer(typed), null,
                mKeyboard.getProximityInfo(), mCorrectionMode);
    }

    public CharSequence getFirstSuggestion(CharSequence typed) {
        WordComposer word = createWordComposer(typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, null,
                mKeyboard.getProximityInfo(), mCorrectionMode);
        // Note that suggestions.getWord(0) is the word user typed.
        return suggestions.size() > 1 ? suggestions.getWord(1) : null;
    }

    public CharSequence getAutoCorrection(CharSequence typed) {
        WordComposer word = createWordComposer(typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, null,
                mKeyboard.getProximityInfo(), mCorrectionMode);
        // Note that suggestions.getWord(0) is the word user typed.
        return (suggestions.size() > 1 && mSuggest.hasAutoCorrection())
                ? suggestions.getWord(1) : null;
    }

    public int getSuggestIndex(CharSequence typed, CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, null,
                mKeyboard.getProximityInfo(), mCorrectionMode);
        // Note that suggestions.getWord(0) is the word user typed.
        for (int i = 1; i < suggestions.size(); i++) {
            if (TextUtils.equals(suggestions.getWord(i), expected))
                return i;
        }
        return -1;
    }

    private void getBigramSuggestions(CharSequence previous, CharSequence typed) {
        if (!TextUtils.isEmpty(previous) && (typed.length() > 1)) {
            WordComposer firstChar = createWordComposer(Character.toString(typed.charAt(0)));
            mSuggest.getSuggestions(firstChar, previous, mKeyboard.getProximityInfo(),
                    mCorrectionMode);
        }
    }

    public CharSequence getBigramFirstSuggestion(CharSequence previous, CharSequence typed) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, previous,
                mKeyboard.getProximityInfo(), mCorrectionMode);
        return suggestions.size() > 1 ? suggestions.getWord(1) : null;
    }

    public CharSequence getBigramAutoCorrection(CharSequence previous, CharSequence typed) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, previous,
                mKeyboard.getProximityInfo(), mCorrectionMode);
        return (suggestions.size() > 1 && mSuggest.hasAutoCorrection())
                ? suggestions.getWord(1) : null;
    }

    public int searchBigramSuggestion(CharSequence previous, CharSequence typed,
            CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, previous,
                mKeyboard.getProximityInfo(), mCorrectionMode);
        for (int i = 1; i < suggestions.size(); i++) {
            if (TextUtils.equals(suggestions.getWord(i), expected))
                return i;
        }
        return -1;
    }
}
