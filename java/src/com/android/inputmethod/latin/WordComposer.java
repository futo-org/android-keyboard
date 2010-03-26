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

import java.util.ArrayList;
import java.util.List;

/**
 * A place to store the currently composing word with information such as adjacent key codes as well
 */
public class WordComposer {
    /**
     * The list of unicode values for each keystroke (including surrounding keys)
     */
    private ArrayList<int[]> mCodes;
    
    /**
     * The word chosen from the candidate list, until it is committed.
     */
    private String mPreferredWord;
    
    private StringBuilder mTypedWord;

    private int mCapsCount;

    private boolean mAutoCapitalized;
    
    /**
     * Whether the user chose to capitalize the word.
     */
    private boolean mIsCapitalized;

    WordComposer() {
        mCodes = new ArrayList<int[]>(12);
        mTypedWord = new StringBuilder(20);
    }

    /**
     * Clear out the keys registered so far.
     */
    public void reset() {
        mCodes.clear();
        mIsCapitalized = false;
        mPreferredWord = null;
        mTypedWord.setLength(0);
        mCapsCount = 0;
    }

    /**
     * Number of keystrokes in the composing word.
     * @return the number of keystrokes
     */
    public int size() {
        return mCodes.size();
    }

    /**
     * Returns the codes at a particular position in the word.
     * @param index the position in the word
     * @return the unicode for the pressed and surrounding keys
     */
    public int[] getCodesAt(int index) {
        return mCodes.get(index);
    }

    /**
     * Add a new keystroke, with codes[0] containing the pressed key's unicode and the rest of
     * the array containing unicode for adjacent keys, sorted by reducing probability/proximity.
     * @param codes the array of unicode values
     */
    public void add(int primaryCode, int[] codes) {
        mTypedWord.append((char) primaryCode);
        correctPrimaryJuxtapos(primaryCode, codes);
        mCodes.add(codes);
        if (Character.isUpperCase((char) primaryCode)) mCapsCount++;
    }

    /**
     * Swaps the first and second values in the codes array if the primary code is not the first
     * value in the array but the second. This happens when the preferred key is not the key that
     * the user released the finger on.
     * @param primaryCode the preferred character
     * @param codes array of codes based on distance from touch point
     */
    private void correctPrimaryJuxtapos(int primaryCode, int[] codes) {
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
        mCodes.remove(mCodes.size() - 1);
        final int lastPos = mTypedWord.length() - 1;
        char last = mTypedWord.charAt(lastPos);
        mTypedWord.deleteCharAt(lastPos);
        if (Character.isUpperCase(last)) mCapsCount--;
    }

    /**
     * Returns the word as it was typed, without any correction applied.
     * @return the word that was typed so far
     */
    public CharSequence getTypedWord() {
        int wordSize = mCodes.size();
        if (wordSize == 0) {
            return null;
        }
//        StringBuffer sb = new StringBuffer(wordSize);
//        for (int i = 0; i < wordSize; i++) {
//            char c = (char) mCodes.get(i)[0];
//            if (i == 0 && mIsCapitalized) {
//                c = Character.toUpperCase(c);
//            }
//            sb.append(c);
//        }
//        return sb;
        return mTypedWord;
    }

    public void setCapitalized(boolean capitalized) {
        mIsCapitalized = capitalized;
    }
    
    /**
     * Whether or not the user typed a capital letter as the first letter in the word
     * @return capitalization preference
     */
    public boolean isCapitalized() {
        return mIsCapitalized;
    }
    
    /**
     * Stores the user's selected word, before it is actually committed to the text field.
     * @param preferred
     */
    public void setPreferredWord(String preferred) {
        mPreferredWord = preferred;
    }
    
    /**
     * Return the word chosen by the user, or the typed word if no other word was chosen.
     * @return the preferred word
     */
    public CharSequence getPreferredWord() {
        return mPreferredWord != null ? mPreferredWord : getTypedWord();
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
}
