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

import com.android.inputmethod.keyboard.KeyboardId;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.util.Locale;
import java.util.StringTokenizer;

public class UserBigramSuggestHelper extends SuggestHelper {
    private final Context mContext;
    private UserBigramDictionary mUserBigram;

    public UserBigramSuggestHelper(final Context context, final File dictionaryPath,
            final long startOffset, final long length, final int userBigramMax,
            final int userBigramDelete, final KeyboardId keyboardId, final Locale locale) {
        super(context, dictionaryPath, startOffset, length, keyboardId, locale);
        mContext = context;
        mUserBigram = new UserBigramDictionary(context, null, locale.toString(),
                Suggest.DIC_USER);
        mUserBigram.setDatabaseMax(userBigramMax);
        mUserBigram.setDatabaseDelete(userBigramDelete);
        mSuggest.setCorrectionMode(Suggest.CORRECTION_FULL_BIGRAM);
        mSuggest.setUserBigramDictionary(mUserBigram);
    }

    public void changeUserBigramLocale(Locale locale) {
        if (mUserBigram != null) {
            flushUserBigrams();
            mUserBigram.close();
            mUserBigram = new UserBigramDictionary(mContext, null, locale.toString(),
                    Suggest.DIC_USER);
            mSuggest.setUserBigramDictionary(mUserBigram);
        }
    }

    public int searchUserBigramSuggestion(CharSequence previous, char typed,
            CharSequence expected) {
        if (mUserBigram == null) return -1;

        flushUserBigrams();
        if (!TextUtils.isEmpty(previous) && !TextUtils.isEmpty(Character.toString(typed))) {
            WordComposer firstChar = createWordComposer(Character.toString(typed));
            mSuggest.getSuggestions(firstChar, previous, mKeyboard.getProximityInfo());
            boolean reloading = mUserBigram.reloadDictionaryIfRequired();
            if (reloading) mUserBigram.waitForDictionaryLoading();
            mUserBigram.getBigrams(firstChar, previous, mSuggest);
        }

        for (int i = 0; i < mSuggest.mBigramSuggestions.size(); i++) {
            final CharSequence word = mSuggest.mBigramSuggestions.get(i);
            if (TextUtils.equals(word, expected))
                return i;
        }

        return -1;
    }

    public void addToUserBigram(String sentence) {
        StringTokenizer st = new StringTokenizer(sentence);
        String previous = null;
        while (st.hasMoreTokens()) {
            String current = st.nextToken();
            if (previous != null) {
                addToUserBigram(new String[] {previous, current});
            }
            previous = current;
        }
    }

    public void addToUserBigram(String[] pair) {
        if (mUserBigram != null && pair.length == 2) {
            mUserBigram.addBigrams(pair[0], pair[1]);
        }
    }

    public void flushUserBigrams() {
        if (mUserBigram != null) {
            mUserBigram.flushPendingWrites();
            mUserBigram.waitUntilUpdateDBDone();
        }
    }
}
