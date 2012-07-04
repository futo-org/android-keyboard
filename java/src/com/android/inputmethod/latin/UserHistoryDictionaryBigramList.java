/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.util.HashMap;
import java.util.Set;

/**
 * A store of bigrams which will be updated when the user history dictionary is closed
 * All bigrams including stale ones in SQL DB should be stored in this class to avoid adding stale
 * bigrams when we write to the SQL DB.
 */
public class UserHistoryDictionaryBigramList {
    public static final byte FORGETTING_CURVE_INITIAL_VALUE = 0;
    private static final String TAG = UserHistoryDictionaryBigramList.class.getSimpleName();
    private static final HashMap<String, Byte> EMPTY_BIGRAM_MAP = new HashMap<String, Byte>();
    private final HashMap<String, HashMap<String, Byte>> mBigramMap =
            new HashMap<String, HashMap<String, Byte>>();
    private int mSize = 0;

    public void evictAll() {
        mSize = 0;
        mBigramMap.clear();
    }

    /**
     * Called when the user typed a word.
     */
    public void addBigram(String word1, String word2) {
        addBigram(word1, word2, FORGETTING_CURVE_INITIAL_VALUE);
    }

    /**
     * Called when loaded from the SQL DB.
     */
    public void addBigram(String word1, String word2, byte fcValue) {
        if (UserHistoryDictionary.DBG_SAVE_RESTORE) {
            Log.d(TAG, "--- add bigram: " + word1 + ", " + word2 + ", " + fcValue);
        }
        final HashMap<String, Byte> map;
        if (mBigramMap.containsKey(word1)) {
            map = mBigramMap.get(word1);
        } else {
            map = new HashMap<String, Byte>();
            mBigramMap.put(word1, map);
        }
        if (!map.containsKey(word2)) {
            ++mSize;
            map.put(word2, fcValue);
        }
    }

    /**
     * Called when inserted to the SQL DB.
     */
    public void updateBigram(String word1, String word2, byte fcValue) {
        if (UserHistoryDictionary.DBG_SAVE_RESTORE) {
            Log.d(TAG, "--- update bigram: " + word1 + ", " + word2 + ", " + fcValue);
        }
        final HashMap<String, Byte> map;
        if (mBigramMap.containsKey(word1)) {
            map = mBigramMap.get(word1);
        } else {
            return;
        }
        if (!map.containsKey(word2)) {
            return;
        }
        map.put(word2, fcValue);
    }

    public int size() {
        return mSize;
    }

    public boolean isEmpty() {
        return mBigramMap.isEmpty();
    }

    public Set<String> keySet() {
        return mBigramMap.keySet();
    }

    public HashMap<String, Byte> getBigrams(String word1) {
        if (!mBigramMap.containsKey(word1)) {
            return EMPTY_BIGRAM_MAP;
        } else {
            return mBigramMap.get(word1);
        }
    }

    public boolean removeBigram(String word1, String word2) {
        final HashMap<String, Byte> set = getBigrams(word1);
        if (set.isEmpty()) {
            return false;
        }
        if (set.containsKey(word2)) {
            set.remove(word2);
            --mSize;
            return true;
        }
        return false;
    }
}
