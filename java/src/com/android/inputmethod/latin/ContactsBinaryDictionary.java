/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;

import java.util.Locale;

public class ContactsBinaryDictionary extends ExpandableBinaryDictionary {

    private static final String[] PROJECTION = {BaseColumns._ID, Contacts.DISPLAY_NAME,};

    private static final String TAG = ContactsBinaryDictionary.class.getSimpleName();
    private static final String NAME = "contacts";

    /**
     * Frequency for contacts information into the dictionary
     */
    private static final int FREQUENCY_FOR_CONTACTS = 40;
    private static final int FREQUENCY_FOR_CONTACTS_BIGRAM = 90;

    private static final int INDEX_NAME = 1;

    private ContentObserver mObserver;

    /**
     * Whether to use "firstname lastname" in bigram predictions.
     */
    private final boolean mUseFirstLastBigrams;

    public ContactsBinaryDictionary(final Context context, final int dicTypeId, Locale locale) {
        super(context, getFilenameWithLocale(locale), dicTypeId);
        mUseFirstLastBigrams = useFirstLastBigramsForLocale(locale);
        registerObserver(context);

        // Load the current binary dictionary from internal storage. If no binary dictionary exists,
        // loadDictionary will start a new thread to generate one asynchronously.
        loadDictionary();
    }

    private static String getFilenameWithLocale(Locale locale) {
        return NAME + "." + locale.toString() + ".dict";
    }

    private synchronized void registerObserver(final Context context) {
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        if (mObserver != null) return;
        ContentResolver cres = context.getContentResolver();
        cres.registerContentObserver(Contacts.CONTENT_URI, true, mObserver =
                new ContentObserver(null) {
                    @Override
                    public void onChange(boolean self) {
                        setRequiresReload(true);
                    }
                });
    }

    public void reopen(final Context context) {
        registerObserver(context);
    }

    @Override
    public synchronized void close() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.close();
    }

    @Override
    public void loadDictionaryAsync() {
        try {
            Cursor cursor = mContext.getContentResolver()
                    .query(Contacts.CONTENT_URI, PROJECTION, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        addWords(cursor);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Contacts DB is having problems");
        }
    }

    @Override
    public void getBigrams(final WordComposer codes, final CharSequence previousWord,
            final WordCallback callback) {
        super.getBigrams(codes, previousWord, callback);
    }

    private boolean useFirstLastBigramsForLocale(Locale locale) {
        // TODO: Add firstname/lastname bigram rules for other languages.
        if (locale != null && locale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
            return true;
        }
        return false;
    }

    private void addWords(Cursor cursor) {
        clearFusionDictionary();
        while (!cursor.isAfterLast()) {
            String name = cursor.getString(INDEX_NAME);
            if (name != null && -1 == name.indexOf('@')) {
                addName(name);
            }
            cursor.moveToNext();
        }
    }

    /**
     * Adds the words in a name (e.g., firstname/lastname) to the binary dictionary along with their
     * bigrams depending on locale.
     */
    private void addName(String name) {
        int len = name.codePointCount(0, name.length());
        String prevWord = null;
        // TODO: Better tokenization for non-Latin writing systems
        for (int i = 0; i < len; i++) {
            if (Character.isLetter(name.codePointAt(i))) {
                int j;
                for (j = i + 1; j < len; j++) {
                    final int codePoint = name.codePointAt(j);
                    if (!(codePoint == Keyboard.CODE_DASH || codePoint == Keyboard.CODE_SINGLE_QUOTE
                            || Character.isLetter(codePoint))) {
                        break;
                    }
                }
                String word = name.substring(i, j);
                i = j - 1;
                // Don't add single letter words, possibly confuses
                // capitalization of i.
                final int wordLen = word.codePointCount(0, word.length());
                if (wordLen < MAX_WORD_LENGTH && wordLen > 1) {
                    super.addWord(word, FREQUENCY_FOR_CONTACTS);
                    if (!TextUtils.isEmpty(prevWord)) {
                        if (mUseFirstLastBigrams) {
                            super.setBigram(prevWord, word, FREQUENCY_FOR_CONTACTS_BIGRAM);
                        }
                    }
                    prevWord = word;
                }
            }
        }
    }
}
