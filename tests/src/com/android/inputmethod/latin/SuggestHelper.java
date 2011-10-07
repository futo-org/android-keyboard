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

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.LatinKeyboard;

import java.io.File;
import java.util.Locale;

public class SuggestHelper {
    protected final Suggest mSuggest;
    protected final LatinKeyboard mKeyboard;
    private final KeyDetector mKeyDetector;

    public SuggestHelper(Context context, int dictionaryId, KeyboardId keyboardId) {
        // Use null as the locale for Suggest so as to force it to use the internal dictionary
        // (and not try to find a dictionary provider for a specified locale)
        mSuggest = new Suggest(context, dictionaryId, null);
        mKeyboard = new LatinKeyboard.Builder(context).load(keyboardId).build();
        mKeyDetector = new KeyDetector(0);
        init();
    }

    protected SuggestHelper(final Context context, final File dictionaryPath,
            final long startOffset, final long length, final KeyboardId keyboardId,
            final Locale locale) {
        mSuggest = new Suggest(context, dictionaryPath, startOffset, length, null, locale);
        mKeyboard = new LatinKeyboard.Builder(context).load(keyboardId).build();
        mKeyDetector = new KeyDetector(0);
        init();
    }

    private void init() {
        mSuggest.setCorrectionMode(Suggest.CORRECTION_FULL);
        mKeyDetector.setKeyboard(mKeyboard, 0, 0);
        mKeyDetector.setProximityCorrectionEnabled(true);
        mKeyDetector.setProximityThreshold(mKeyboard.mMostCommonKeyWidth);
    }

    public void setCorrectionMode(int correctionMode) {
        mSuggest.setCorrectionMode(correctionMode);
    }

    public boolean hasMainDictionary() {
        return mSuggest.hasMainDictionary();
    }

    private void addKeyInfo(WordComposer word, char c) {
        for (final Key key : mKeyboard.mKeys) {
            if (key.mCode == c) {
                final int x = key.mX + key.mWidth / 2;
                final int y = key.mY + key.mHeight / 2;
                final int[] codes = mKeyDetector.newCodeArray();
                mKeyDetector.getKeyIndexAndNearbyCodes(x, y, codes);
                word.add(c, codes, x, y);
                return;
            }
        }
        word.add(c, new int[] { c }, WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
    }

    protected WordComposer createWordComposer(CharSequence s) {
        WordComposer word = new WordComposer();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            addKeyInfo(word, c);
        }
        return word;
    }

    public boolean isValidWord(CharSequence typed) {
        return AutoCorrection.isValidWord(mSuggest.getUnigramDictionaries(),
                typed, false);
    }

    // TODO: This may be slow, but is OK for test so far.
    public SuggestedWords getSuggestions(CharSequence typed) {
        return mSuggest.getSuggestions(createWordComposer(typed), null,
                mKeyboard.getProximityInfo());
    }

    public CharSequence getFirstSuggestion(CharSequence typed) {
        WordComposer word = createWordComposer(typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, null,
                mKeyboard.getProximityInfo());
        // Note that suggestions.getWord(0) is the word user typed.
        return suggestions.size() > 1 ? suggestions.getWord(1) : null;
    }

    public CharSequence getAutoCorrection(CharSequence typed) {
        WordComposer word = createWordComposer(typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, null,
                mKeyboard.getProximityInfo());
        // Note that suggestions.getWord(0) is the word user typed.
        return (suggestions.size() > 1 && mSuggest.hasAutoCorrection())
                ? suggestions.getWord(1) : null;
    }

    public int getSuggestIndex(CharSequence typed, CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, null,
                mKeyboard.getProximityInfo());
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
            mSuggest.getSuggestions(firstChar, previous, mKeyboard.getProximityInfo());
        }
    }

    public CharSequence getBigramFirstSuggestion(CharSequence previous, CharSequence typed) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, previous,
                mKeyboard.getProximityInfo());
        return suggestions.size() > 1 ? suggestions.getWord(1) : null;
    }

    public CharSequence getBigramAutoCorrection(CharSequence previous, CharSequence typed) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, previous,
                mKeyboard.getProximityInfo());
        return (suggestions.size() > 1 && mSuggest.hasAutoCorrection())
                ? suggestions.getWord(1) : null;
    }

    public int searchBigramSuggestion(CharSequence previous, CharSequence typed,
            CharSequence expected) {
        WordComposer word = createWordComposer(typed);
        getBigramSuggestions(previous, typed);
        SuggestedWords suggestions = mSuggest.getSuggestions(word, previous,
                mKeyboard.getProximityInfo());
        for (int i = 1; i < suggestions.size(); i++) {
            if (TextUtils.equals(suggestions.getWord(i), expected))
                return i;
        }
        return -1;
    }
}
