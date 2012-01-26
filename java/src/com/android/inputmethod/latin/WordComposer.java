/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.text.TextUtils;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A place to store the currently composing word with information such as adjacent key codes as well
 */
public class WordComposer {

    public static final int NOT_A_CODE = KeyDetector.NOT_A_CODE;
    public static final int NOT_A_COORDINATE = -1;

    // Storage for all the info about the current input.
    private static class CharacterStore {
        /**
         * The list of unicode values for each keystroke (including surrounding keys)
         */
        ArrayList<int[]> mCodes;
        int[] mXCoordinates;
        int[] mYCoordinates;
        StringBuilder mTypedWord;
        CharSequence mAutoCorrection;
        CharacterStore() {
            final int N = BinaryDictionary.MAX_WORD_LENGTH;
            mCodes = new ArrayList<int[]>(N);
            mTypedWord = new StringBuilder(N);
            mXCoordinates = new int[N];
            mYCoordinates = new int[N];
            mAutoCorrection = null;
        }
        CharacterStore(final CharacterStore that) {
            mCodes = new ArrayList<int[]>(that.mCodes);
            mTypedWord = new StringBuilder(that.mTypedWord);
            mXCoordinates = Arrays.copyOf(that.mXCoordinates, that.mXCoordinates.length);
            mYCoordinates = Arrays.copyOf(that.mYCoordinates, that.mYCoordinates.length);
        }
        void reset() {
            // For better performance than creating a new character store.
            mCodes.clear();
            mTypedWord.setLength(0);
            mAutoCorrection = null;
        }
    }

    // The currently typing word. May not be null.
    private CharacterStore mCurrentWord;
    // The information being kept for resuming suggestion. May be null if wiped.
    private CharacterStore mCommittedWordSavedForSuggestionResuming;

    private int mCapsCount;

    private boolean mAutoCapitalized;
    // Cache this value for performance
    private int mTrailingSingleQuotesCount;

    /**
     * Whether the user chose to capitalize the first char of the word.
     */
    private boolean mIsFirstCharCapitalized;

    public WordComposer() {
        mCurrentWord = new CharacterStore();
        mCommittedWordSavedForSuggestionResuming = null;
        mTrailingSingleQuotesCount = 0;
    }

    public WordComposer(WordComposer source) {
        init(source);
    }

    public void init(WordComposer source) {
        mCurrentWord = new CharacterStore(source.mCurrentWord);
        mCommittedWordSavedForSuggestionResuming = source.mCommittedWordSavedForSuggestionResuming;
        mCapsCount = source.mCapsCount;
        mIsFirstCharCapitalized = source.mIsFirstCharCapitalized;
        mAutoCapitalized = source.mAutoCapitalized;
        mTrailingSingleQuotesCount = source.mTrailingSingleQuotesCount;
    }

    /**
     * Clear out the keys registered so far.
     */
    public void reset() {
        mCurrentWord.reset();
        mCommittedWordSavedForSuggestionResuming = null;
        mCapsCount = 0;
        mIsFirstCharCapitalized = false;
        mTrailingSingleQuotesCount = 0;
    }

    /**
     * Number of keystrokes in the composing word.
     * @return the number of keystrokes
     */
    public final int size() {
        return mCurrentWord.mTypedWord.length();
    }

    public final boolean isComposingWord() {
        return size() > 0;
    }

    /**
     * Returns the codes at a particular position in the word.
     * @param index the position in the word
     * @return the unicode for the pressed and surrounding keys
     */
    public int[] getCodesAt(int index) {
        return mCurrentWord.mCodes.get(index);
    }

    public int[] getXCoordinates() {
        return mCurrentWord.mXCoordinates;
    }

    public int[] getYCoordinates() {
        return mCurrentWord.mYCoordinates;
    }

    private static boolean isFirstCharCapitalized(int index, int codePoint, boolean previous) {
        if (index == 0) return Character.isUpperCase(codePoint);
        return previous && !Character.isUpperCase(codePoint);
    }

    /**
     * Add a new keystroke, with codes[0] containing the pressed key's unicode and the rest of
     * the array containing unicode for adjacent keys, sorted by reducing probability/proximity.
     * @param codes the array of unicode values
     */
    public void add(int primaryCode, int[] codes, int x, int y) {
        final int newIndex = size();
        mCurrentWord.mTypedWord.append((char) primaryCode);
        correctPrimaryJuxtapos(primaryCode, codes);
        mCurrentWord.mCodes.add(codes);
        if (newIndex < BinaryDictionary.MAX_WORD_LENGTH) {
            mCurrentWord.mXCoordinates[newIndex] = x;
            mCurrentWord.mYCoordinates[newIndex] = y;
        }
        mIsFirstCharCapitalized = isFirstCharCapitalized(
                newIndex, primaryCode, mIsFirstCharCapitalized);
        if (Character.isUpperCase(primaryCode)) mCapsCount++;
        if (Keyboard.CODE_SINGLE_QUOTE == primaryCode) {
            ++mTrailingSingleQuotesCount;
        } else {
            mTrailingSingleQuotesCount = 0;
        }
        mCurrentWord.mAutoCorrection = null;
    }

    /**
     * Internal method to retrieve reasonable proximity info for a character.
     */
    private void addKeyInfo(final int codePoint, final Keyboard keyboard,
            final KeyDetector keyDetector) {
        for (final Key key : keyboard.mKeys) {
            if (key.mCode == codePoint) {
                final int x = key.mX + key.mWidth / 2;
                final int y = key.mY + key.mHeight / 2;
                final int[] codes = keyDetector.newCodeArray();
                keyDetector.getKeyAndNearbyCodes(x, y, codes);
                add(codePoint, codes, x, y);
                return;
            }
        }
        add(codePoint, new int[] { codePoint },
                WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
    }

    /**
     * Set the currently composing word to the one passed as an argument.
     * This will register NOT_A_COORDINATE for X and Ys, and use the passed keyboard for proximity.
     */
    public void setComposingWord(final CharSequence word, final Keyboard keyboard,
            final KeyDetector keyDetector) {
        reset();
        final int length = word.length();
        for (int i = 0; i < length; ++i) {
            int codePoint = word.charAt(i);
            addKeyInfo(codePoint, keyboard, keyDetector);
        }
        mCommittedWordSavedForSuggestionResuming = null;
    }

    /**
     * Shortcut for the above method, this will create a new KeyDetector for the passed keyboard.
     */
    public void setComposingWord(final CharSequence word, final Keyboard keyboard) {
        final KeyDetector keyDetector = new KeyDetector(0);
        keyDetector.setKeyboard(keyboard, 0, 0);
        keyDetector.setProximityCorrectionEnabled(true);
        keyDetector.setProximityThreshold(keyboard.mMostCommonKeyWidth);
        setComposingWord(word, keyboard, keyDetector);
    }

    /**
     * Swaps the first and second values in the codes array if the primary code is not the first
     * value in the array but the second. This happens when the preferred key is not the key that
     * the user released the finger on.
     * @param primaryCode the preferred character
     * @param codes array of codes based on distance from touch point
     */
    private static void correctPrimaryJuxtapos(int primaryCode, int[] codes) {
        if (codes.length < 2) return;
        if (codes[0] > 0 && codes[1] > 0 && codes[0] != primaryCode && codes[1] == primaryCode) {
            codes[1] = codes[0];
            codes[0] = primaryCode;
        }
    }

    /**
     * Delete the last keystroke as a result of hitting backspace.
     */
    public void deleteLast() {
        final int size = size();
        if (size > 0) {
            final int lastPos = size - 1;
            char lastChar = mCurrentWord.mTypedWord.charAt(lastPos);
            mCurrentWord.mCodes.remove(lastPos);
            mCurrentWord.mTypedWord.deleteCharAt(lastPos);
            if (Character.isUpperCase(lastChar)) mCapsCount--;
        }
        if (size() == 0) {
            mIsFirstCharCapitalized = false;
        }
        if (mTrailingSingleQuotesCount > 0) {
            --mTrailingSingleQuotesCount;
        } else {
            for (int i = mCurrentWord.mTypedWord.length() - 1; i >= 0; --i) {
                if (Keyboard.CODE_SINGLE_QUOTE != mCurrentWord.mTypedWord.codePointAt(i)) break;
                ++mTrailingSingleQuotesCount;
            }
        }
        mCurrentWord.mAutoCorrection = null;
    }

    /**
     * Returns the word as it was typed, without any correction applied.
     * @return the word that was typed so far. Never returns null.
     */
    public String getTypedWord() {
        return mCurrentWord.mTypedWord.toString();
    }

    /**
     * Whether or not the user typed a capital letter as the first letter in the word
     * @return capitalization preference
     */
    public boolean isFirstCharCapitalized() {
        return mIsFirstCharCapitalized;
    }

    public int trailingSingleQuotesCount() {
        return mTrailingSingleQuotesCount;
    }

    /**
     * Whether or not all of the user typed chars are upper case
     * @return true if all user typed chars are upper case, false otherwise
     */
    public boolean isAllUpperCase() {
        return (mCapsCount > 0) && (mCapsCount == size());
    }

    /**
     * Returns true if more than one character is upper case, otherwise returns false.
     */
    public boolean isMostlyCaps() {
        return mCapsCount > 1;
    }

    /**
     * Saves the reason why the word is capitalized - whether it was automatic or
     * due to the user hitting shift in the middle of a sentence.
     * @param auto whether it was an automatic capitalization due to start of sentence
     */
    public void setAutoCapitalized(boolean auto) {
        mAutoCapitalized = auto;
    }

    /**
     * Returns whether the word was automatically capitalized.
     * @return whether the word was automatically capitalized
     */
    public boolean isAutoCapitalized() {
        return mAutoCapitalized;
    }

    /**
     * Sets the auto-correction for this word.
     */
    public void setAutoCorrection(final CharSequence correction) {
        mCurrentWord.mAutoCorrection = correction;
    }

    /**
     * Remove any auto-correction that may have been set.
     */
    public void deleteAutoCorrection() {
        mCurrentWord.mAutoCorrection = null;
    }

    /**
     * @return the auto-correction for this word, or null if none.
     */
    public CharSequence getAutoCorrectionOrNull() {
        return mCurrentWord.mAutoCorrection;
    }

    // `type' should be one of the LastComposedWord.COMMIT_TYPE_* constants above.
    public LastComposedWord commitWord(final int type) {
        mCommittedWordSavedForSuggestionResuming = mCurrentWord;
        // Note: currently, we come here whenever we commit a word. If it's any *other* kind than
        // DECIDED_WORD, we should reset mAutoCorrection so that we don't attempt to cancel later.
        // If it's a DECIDED_WORD, it may be an actual auto-correction by the IME, or what the user
        // typed because the IME decided *not* to auto-correct for whatever reason.
        // Ideally we would also null it when it was a DECIDED_WORD that was not an auto-correct.
        // As it happens these two cases should behave differently, because the former can be
        // canceled while the latter can't. Currently, we figure this out in
        // #didAutoCorrectToAnotherWord with #equals(). It would be marginally cleaner to do it
        // here, but it would be slower (since we would #equals() for each commit, instead of
        // only on cancel), and ultimately we want to figure it out even earlier anyway.
        if (type != LastComposedWord.COMMIT_TYPE_DECIDED_WORD) {
            // Only ever revert an auto-correct.
            mCommittedWordSavedForSuggestionResuming.mAutoCorrection = null;
        }
        final LastComposedWord lastComposedWord = new LastComposedWord(type, mCurrentWord.mCodes,
                mCurrentWord.mXCoordinates, mCurrentWord.mYCoordinates,
                mCurrentWord.mTypedWord.toString(),
                null == mCurrentWord.mAutoCorrection
                        ? null : mCurrentWord.mAutoCorrection.toString());
        // TODO: improve performance by swapping buffers instead of creating a new object.
        mCurrentWord = new CharacterStore();
        return lastComposedWord;
    }

    public boolean hasWordKeptForSuggestionResuming() {
        return null != mCommittedWordSavedForSuggestionResuming;
    }

    public void resumeSuggestionOnKeptWord() {
        mCurrentWord = mCommittedWordSavedForSuggestionResuming;
        mCommittedWordSavedForSuggestionResuming = null;
    }
}
