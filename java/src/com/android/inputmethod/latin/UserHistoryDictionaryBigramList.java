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
import java.util.HashSet;
import java.util.Set;

/**
 * A store of bigrams which will be updated when the user history dictionary is closed
 * All bigrams including stale ones in SQL DB should be stored in this class to avoid adding stale
 * bigrams when we write to the SQL DB.
 */
public class UserHistoryDictionaryBigramList {
    private static final String TAG = UserHistoryDictionaryBigramList.class.getSimpleName();
    private static final HashSet<String> EMPTY_STRING_SET = new HashSet<String>();
    private final HashMap<String, HashSet<String>> mBigramMap =
            new HashMap<String, HashSet<String>>();
    private int mSize = 0;

    public void evictAll() {
        mSize = 0;
        mBigramMap.clear();
    }

    public void addBigram(String word1, String word2) {
        if (UserHistoryDictionary.DBG_SAVE_RESTORE) {
            Log.d(TAG, "--- add bigram: " + word1 + ", " + word2);
        }
        final HashSet<String> set;
        if (mBigramMap.containsKey(word1)) {
            set = mBigramMap.get(word1);
        } else {
            set = new HashSet<String>();
            mBigramMap.put(word1, set);
        }
        if (!set.contains(word2)) {
            ++mSize;
            set.add(word2);
        }
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

    public HashSet<String> getBigrams(String word1) {
        if (!mBigramMap.containsKey(word1)) {
            return EMPTY_STRING_SET;
        } else {
            return mBigramMap.get(word1);
        }
    }

    public boolean removeBigram(String word1, String word2) {
        final HashSet<String> set = getBigrams(word1);
        if (set.isEmpty()) {
            return false;
        }
        if (set.contains(word2)) {
            set.remove(word2);
            --mSize;
            return true;
        }
        return false;
    }
}
