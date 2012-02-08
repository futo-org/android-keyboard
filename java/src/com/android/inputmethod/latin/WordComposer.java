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

    final static int N = BinaryDictionary.MAX_WORD_LENGTH;

    private ArrayList<int[]> mCodes;
    private int[] mXCoordinates;
    private int[] mYCoordinates;
    private StringBuilder mTypedWord;
    private CharSequence mAutoCorrection;

    // Cache these values for performance
    private int mCapsCount;
    private boolean mAutoCapitalized;
    private int mTrailingSingleQuotesCount;

    /**
     * Whether the user chose to capitalize the first char of the word.
     */
    private boolean mIsFirstCharCapitalized;

    public WordComposer() {
        mCodes = new ArrayList<int[]>(N);
        mTypedWord = new StringBuilder(N);
        mXCoordinates = new int[N];
        mYCoordinates = new int[N];
        mAutoCorrection = null;
        mTrailingSingleQuotesCount = 0;
    }

    public WordComposer(WordComposer source) {
        init(source);
    }

    public void init(WordComposer source) {
        mCodes = new ArrayList<int[]>(source.mCodes);
        mTypedWord = new StringBuilder(source.mTypedWord);
        mXCoordinates = Arrays.copyOf(source.mXCoordinates, source.mXCoordinates.length);
        mYCoordinates = Arrays.copyOf(source.mYCoordinates, source.mYCoordinates.length);
        mCapsCount = source.mCapsCount;
        mIsFirstCharCapitalized = source.mIsFirstCharCapitalized;
        mAutoCapitalized = source.mAutoCapitalized;
        mTrailingSingleQuotesCount = source.mTrailingSingleQuotesCount;
    }

    /**
     * Clear out the keys registered so far.
     */
    public void reset() {
        mCodes.clear();
        mTypedWord.setLength(0);
        mAutoCorrection = null;
        mCapsCount = 0;
        mIsFirstCharCapitalized = false;
        mTrailingSingleQuotesCount = 0;
    }

    /**
     * Number of keystrokes in the composing word.
     * @return the number of keystrokes
     */
    public final int size() {
        return mCodes.size();
    }

    public final boolean isComposingWord() {
        return mCodes.size() > 0;
    }

    /**
     * Returns the codes at a particular position in the word.
     * @param index the position in the word
     * @return the unicode for the pressed and surrounding keys
     */
    public int[] getCodesAt(int index) {
        return mCodes.get(index);
    }

    public int[] getXCoordinates() {
        return mXCoordinates;
    }

    public int[] getYCoordinates() {
        return mYCoordinates;
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
        final int newIndex = mCodes.size();
        mTypedWord.appendCodePoint(primaryCode);
        correctPrimaryJuxtapos(primaryCode, codes);
        mCodes.add(codes);
        if (newIndex < BinaryDictionary.MAX_WORD_LENGTH) {
            mXCoordinates[newIndex] = x;
            mYCoordinates[newIndex] = y;
        }
        mIsFirstCharCapitalized = isFirstCharCapitalized(
                newIndex, primaryCode, mIsFirstCharCapitalized);
        if (Character.isUpperCase(primaryCode)) mCapsCount++;
        if (Keyboard.CODE_SINGLE_QUOTE == primaryCode) {
            ++mTrailingSingleQuotesCount;
        } else {
            mTrailingSingleQuotesCount = 0;
        }
        mAutoCorrection = null;
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
        for (int i = 0; i < length; i = Character.offsetByCodePoints(word, i, 1)) {
            int codePoint = Character.codePointAt(word, i);
            addKeyInfo(codePoint, keyboard, keyDetector);
        }
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
        final int size = mCodes.size();
        if (size > 0) {
            mCodes.remove(size - 1);
            // Note: mTypedWord.length() and mCodes.length differ when there are surrogate pairs
            final int stringBuilderLength = mTypedWord.length();
            if (stringBuilderLength < size) {
                throw new RuntimeException(
                        "In WordComposer: mCodes and mTypedWords have non-matching lengths");
            }
            final int lastChar = mTypedWord.codePointBefore(stringBuilderLength);
            if (Character.isSupplementaryCodePoint(lastChar)) {
                mTypedWord.delete(stringBuilderLength - 2, stringBuilderLength);
            } else {
                mTypedWord.deleteCharAt(stringBuilderLength - 1);
            }
            if (Character.isUpperCase(lastChar)) mCapsCount--;
        }
        // We may have deleted the last one.
        if (0 == mCodes.size()) {
            mIsFirstCharCapitalized = false;
        }
        if (mTrailingSingleQuotesCount > 0) {
            --mTrailingSingleQuotesCount;
        } else {
            int i = mTypedWord.length();
            while (i > 0) {
                i = mTypedWord.offsetByCodePoints(i, -1);
                if (Keyboard.CODE_SINGLE_QUOTE != mTypedWord.codePointAt(i)) break;
                ++mTrailingSingleQuotesCount;
            }
        }
        mAutoCorrection = null;
    }

    /**
     * Returns the word as it was typed, without any correction applied.
     * @return the word that was typed so far. Never returns null.
     */
    public String getTypedWord() {
        return mTypedWord.toString();
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
        mAutoCorrection = correction;
    }

    /**
     * @return the auto-correction for this word, or null if none.
     */
    public CharSequence getAutoCorrectionOrNull() {
        return mAutoCorrection;
    }

    // `type' should be one of the LastComposedWord.COMMIT_TYPE_* constants above.
    public LastComposedWord commitWord(final int type) {
        // Note: currently, we come here whenever we commit a word. If it's any *other* kind than
        // DECIDED_WORD, we should reset mAutoCorrection so that we don't attempt to cancel later.
        // If it's a DECIDED_WORD, it may be an actual auto-correction by the IME, or what the user
        // typed because the IME decided *not* to auto-correct for whatever reason.
        // Ideally we would also null it when it was a DECIDED_WORD that was not an auto-correct.
        // As it happens these two cases should behave differently, because the former can be
        // canceled while the latter can't. Currently, we figure this out in
        // LastComposedWord#didAutoCorrectToAnotherWord with #equals(). It would be marginally
        // cleaner to do it here, but it would be slower (since we would #equals() for each commit,
        // instead of only on cancel), and ultimately we want to figure it out even earlier anyway.
        final ArrayList<int[]> codes = mCodes;
        final int[] xCoordinates = mXCoordinates;
        final int[] yCoordinates = mYCoordinates;
        mCodes = new ArrayList<int[]>(N);
        mXCoordinates = new int[N];
        mYCoordinates = new int[N];
        final LastComposedWord lastComposedWord = new LastComposedWord(codes,
                xCoordinates, yCoordinates, mTypedWord.toString(),
                null == mAutoCorrection ? null : mAutoCorrection.toString());
        if (type != LastComposedWord.COMMIT_TYPE_DECIDED_WORD) {
            lastComposedWord.deactivate();
        }
        mTypedWord.setLength(0);
        mAutoCorrection = null;
        return lastComposedWord;
    }

    public void resumeSuggestionOnLastComposedWord(final LastComposedWord lastComposedWord) {
        mCodes = lastComposedWord.mCodes;
        mXCoordinates = lastComposedWord.mXCoordinates;
        mYCoordinates = lastComposedWord.mYCoordinates;
        mTypedWord.setLength(0);
        mTypedWord.append(lastComposedWord.mTypedWord);
        mAutoCorrection = lastComposedWord.mAutoCorrection;
    }
}
