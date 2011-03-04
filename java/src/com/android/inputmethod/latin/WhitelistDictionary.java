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

package com.android.inputmethod.latin;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.util.HashMap;

public class WhitelistDictionary extends Dictionary {

    private static final boolean DBG = LatinImeLogger.sDBG;
    private static final String TAG = WhitelistDictionary.class.getSimpleName();

    private final HashMap<String, Pair<Integer, String>> mWhitelistWords =
            new HashMap<String, Pair<Integer, String>>();

    private static final WhitelistDictionary sInstance = new WhitelistDictionary();

    private WhitelistDictionary() {
    }

    public static WhitelistDictionary init(Context context) {
        synchronized (sInstance) {
            if (context != null) {
                sInstance.initWordlist(
                        context.getResources().getStringArray(R.array.wordlist_whitelist));
            } else {
                sInstance.mWhitelistWords.clear();
            }
        }
        return sInstance;
    }

    private void initWordlist(String[] wordlist) {
        mWhitelistWords.clear();
        final int N = wordlist.length;
        if (N % 3 != 0) {
            if (DBG) {
                Log.d(TAG, "The number of the whitelist is invalid.");
            }
            return;
        }
        try {
            for (int i = 0; i < N; i += 3) {
                final int score = Integer.valueOf(wordlist[i]);
                final String before = wordlist[i + 1];
                final String after = wordlist[i + 2];
                if (before != null && after != null) {
                    mWhitelistWords.put(
                            before.toLowerCase(), new Pair<Integer, String>(score, after));
                }
            }
        } catch (NumberFormatException e) {
            if (DBG) {
                Log.d(TAG, "The score of the word is invalid.");
            }
        }
    }

    public String getWhiteListedWord(String before) {
        if (before == null) return null;
        final String lowerCaseBefore = before.toLowerCase();
        if(mWhitelistWords.containsKey(lowerCaseBefore)) {
            if (DBG) {
                Log.d(TAG, "--- found whiteListedWord: " + lowerCaseBefore);
            }
            return mWhitelistWords.get(lowerCaseBefore).second;
        }
        return null;
    }

    // Not used for WhitelistDictionary.  We use getWhitelistedWord() in Suggest.java instead
    @Override
    public void getWords(WordComposer composer, WordCallback callback) {
    }

    @Override
    public boolean isValidWord(CharSequence word) {
        if (TextUtils.isEmpty(word)) return false;
        return !TextUtils.isEmpty(getWhiteListedWord(word.toString()));
    }
}
