/*
 * Copyright (C) 2008-2009 Google Inc.
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
import android.text.AutoText;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of 
 * characters. This includes corrections and completions.
 * @hide pending API Council Approval
 */
public class Suggest implements Dictionary.WordCallback {

    public static final int CORRECTION_NONE = 0;
    public static final int CORRECTION_BASIC = 1;
    public static final int CORRECTION_FULL = 2;

    private Dictionary mMainDict;

    private Dictionary mUserDictionary;

    private Dictionary mAutoDictionary;

    private Dictionary mContactsDictionary;

    private int mPrefMaxSuggestions = 12;

    private int[] mPriorities = new int[mPrefMaxSuggestions];
    private List<CharSequence> mSuggestions = new ArrayList<CharSequence>();
    private boolean mIncludeTypedWordIfValid;
    private List<CharSequence> mStringPool = new ArrayList<CharSequence>();
    private Context mContext;
    private boolean mHaveCorrection;
    private CharSequence mOriginalWord;
    private String mLowerOriginalWord;

    private int mCorrectionMode = CORRECTION_BASIC;


    public Suggest(Context context, int dictionaryResId) {
        mContext = context;
        mMainDict = new BinaryDictionary(context, dictionaryResId);
        for (int i = 0; i < mPrefMaxSuggestions; i++) {
            StringBuilder sb = new StringBuilder(32);
            mStringPool.add(sb);
        }
    }

    public int getCorrectionMode() {
        return mCorrectionMode;
    }

    public void setCorrectionMode(int mode) {
        mCorrectionMode = mode;
    }

    /**
     * Sets an optional user dictionary resource to be loaded. The user dictionary is consulted
     * before the main dictionary, if set.
     */
    public void setUserDictionary(Dictionary userDictionary) {
        mUserDictionary = userDictionary;
    }

    /**
     * Sets an optional contacts dictionary resource to be loaded.
     */
    public void setContactsDictionary(Dictionary userDictionary) {
        mContactsDictionary = userDictionary;
    }
    
    public void setAutoDictionary(Dictionary autoDictionary) {
        mAutoDictionary = autoDictionary;
    }

    /**
     * Number of suggestions to generate from the input key sequence. This has
     * to be a number between 1 and 100 (inclusive).
     * @param maxSuggestions
     * @throws IllegalArgumentException if the number is out of range
     */
    public void setMaxSuggestions(int maxSuggestions) {
        if (maxSuggestions < 1 || maxSuggestions > 100) {
            throw new IllegalArgumentException("maxSuggestions must be between 1 and 100");
        }
        mPrefMaxSuggestions = maxSuggestions;
        mPriorities = new int[mPrefMaxSuggestions];
        collectGarbage();
        while (mStringPool.size() < mPrefMaxSuggestions) {
            StringBuilder sb = new StringBuilder(32);
            mStringPool.add(sb);
        }
    }

    private boolean haveSufficientCommonality(String original, CharSequence suggestion) {
        final int originalLength = original.length();
        final int suggestionLength = suggestion.length();
        final int minLength = Math.min(originalLength, suggestionLength);
        if (minLength <= 2) return true;
        int matching = 0;
        int lessMatching = 0; // Count matches if we skip one character
        int i;
        for (i = 0; i < minLength; i++) {
            final char origChar = ExpandableDictionary.toLowerCase(original.charAt(i));
            if (origChar == ExpandableDictionary.toLowerCase(suggestion.charAt(i))) {
                matching++;
                lessMatching++;
            } else if (i + 1 < suggestionLength
                    && origChar == ExpandableDictionary.toLowerCase(suggestion.charAt(i + 1))) {
                lessMatching++;
            }
        }
        matching = Math.max(matching, lessMatching);

        if (minLength <= 4) {
            return matching >= 2;
        } else {
            return matching > minLength / 2;
        }
    }

    /**
     * Returns a list of words that match the list of character codes passed in.
     * This list will be overwritten the next time this function is called.
     * @param a view for retrieving the context for AutoText
     * @param codes the list of codes. Each list item contains an array of character codes
     * in order of probability where the character at index 0 in the array has the highest 
     * probability. 
     * @return list of suggestions.
     */
    public List<CharSequence> getSuggestions(View view, WordComposer wordComposer, 
            boolean includeTypedWordIfValid) {
        mHaveCorrection = false;
        collectGarbage();
        Arrays.fill(mPriorities, 0);
        mIncludeTypedWordIfValid = includeTypedWordIfValid;
        
        // Save a lowercase version of the original word
        mOriginalWord = wordComposer.getTypedWord();
        if (mOriginalWord != null) {
            mOriginalWord = mOriginalWord.toString();
            mLowerOriginalWord = mOriginalWord.toString().toLowerCase();
        } else {
            mLowerOriginalWord = "";
        }
        // Search the dictionary only if there are at least 2 characters
        if (wordComposer.size() > 1) {
            if (mUserDictionary != null || mContactsDictionary != null) {
                if (mUserDictionary != null) {
                    mUserDictionary.getWords(wordComposer, this);
                }
                if (mContactsDictionary != null) {
                    mContactsDictionary.getWords(wordComposer, this);
                }

                if (mSuggestions.size() > 0 && isValidWord(mOriginalWord)) {
                    mHaveCorrection = true;
                }
            }
            mMainDict.getWords(wordComposer, this);
            if (mCorrectionMode == CORRECTION_FULL && mSuggestions.size() > 0) {
                mHaveCorrection = true;
            }
        }
        if (mOriginalWord != null) {
            mSuggestions.add(0, mOriginalWord.toString());
        }
        
        // Check if the first suggestion has a minimum number of characters in common
        if (mCorrectionMode == CORRECTION_FULL && mSuggestions.size() > 1) {
            if (!haveSufficientCommonality(mLowerOriginalWord, mSuggestions.get(1))) {
                mHaveCorrection = false;
            }
        }
        
        int i = 0;
        int max = 6;
        // Don't autotext the suggestions from the dictionaries
        if (mCorrectionMode == CORRECTION_BASIC) max = 1;
        while (i < mSuggestions.size() && i < max) {
            String suggestedWord = mSuggestions.get(i).toString().toLowerCase();
            CharSequence autoText =
                    AutoText.get(suggestedWord, 0, suggestedWord.length(), view);
            // Is there an AutoText correction?
            boolean canAdd = autoText != null;
            // Is that correction already the current prediction (or original word)?
            canAdd &= !TextUtils.equals(autoText, mSuggestions.get(i));
            // Is that correction already the next predicted word?
            if (canAdd && i + 1 < mSuggestions.size() && mCorrectionMode != CORRECTION_BASIC) {
                canAdd &= !TextUtils.equals(autoText, mSuggestions.get(i + 1));
            }
            if (canAdd) {
                mHaveCorrection = true;
                mSuggestions.add(i + 1, autoText);
                i++;
            }
            i++;
        }
        
        return mSuggestions;
    }

    public boolean hasMinimalCorrection() {
        return mHaveCorrection;
    }

    private boolean compareCaseInsensitive(final String mLowerOriginalWord, 
            final char[] word, final int offset, final int length) {
        final int originalLength = mLowerOriginalWord.length();
        if (originalLength == length && Character.isUpperCase(word[offset])) {
            for (int i = 0; i < originalLength; i++) {
                if (mLowerOriginalWord.charAt(i) != Character.toLowerCase(word[offset+i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public boolean addWord(final char[] word, final int offset, final int length, final int freq) {
        int pos = 0;
        final int[] priorities = mPriorities;
        final int prefMaxSuggestions = mPrefMaxSuggestions;
        // Check if it's the same word, only caps are different
        if (compareCaseInsensitive(mLowerOriginalWord, word, offset, length)) {
            pos = 0;
        } else {
            // Check the last one's priority and bail
            if (priorities[prefMaxSuggestions - 1] >= freq) return true;
            while (pos < prefMaxSuggestions) {
                if (priorities[pos] < freq
                        || (priorities[pos] == freq && length < mSuggestions
                                .get(pos).length())) {
                    break;
                }
                pos++;
            }
        }
        
        if (pos >= prefMaxSuggestions) {
            return true;
        }
        System.arraycopy(priorities, pos, priorities, pos + 1,
                prefMaxSuggestions - pos - 1);
        priorities[pos] = freq;
        int poolSize = mStringPool.size();
        StringBuilder sb = poolSize > 0 ? (StringBuilder) mStringPool.remove(poolSize - 1) 
                : new StringBuilder(32);
        sb.setLength(0);
        sb.append(word, offset, length);
        mSuggestions.add(pos, sb);
        if (mSuggestions.size() > prefMaxSuggestions) {
            CharSequence garbage = mSuggestions.remove(prefMaxSuggestions);
            if (garbage instanceof StringBuilder) {
                mStringPool.add(garbage);
            }
        }
        return true;
    }

    public boolean isValidWord(final CharSequence word) {
        if (word == null || word.length() == 0) {
            return false;
        }
        return (mCorrectionMode == CORRECTION_FULL && mMainDict.isValidWord(word)) 
                || (mCorrectionMode > CORRECTION_NONE && 
                    ((mUserDictionary != null && mUserDictionary.isValidWord(word)))
                     || (mAutoDictionary != null && mAutoDictionary.isValidWord(word))
                     || (mContactsDictionary != null && mContactsDictionary.isValidWord(word)));
    }
    
    private void collectGarbage() {
        int poolSize = mStringPool.size();
        int garbageSize = mSuggestions.size();
        while (poolSize < mPrefMaxSuggestions && garbageSize > 0) {
            CharSequence garbage = mSuggestions.get(garbageSize - 1);
            if (garbage != null && garbage instanceof StringBuilder) {
                mStringPool.add(garbage);
                poolSize++;
            }
            garbageSize--;
        }
        if (poolSize == mPrefMaxSuggestions + 1) {
            Log.w("Suggest", "String pool got too big: " + poolSize);
        }
        mSuggestions.clear();
    }
}
