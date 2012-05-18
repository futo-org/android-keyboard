/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;

// TODO: This class is superseded by {@link ContactsBinaryDictionary}. Should be cleaned up.
/**
 * An expandable dictionary that stores the words from Contacts provider.
 *
 * @deprecated Use {@link ContactsBinaryDictionary}.
 */
@Deprecated
public class ContactsDictionary extends ExpandableDictionary {

    private static final String[] PROJECTION = {
        BaseColumns._ID,
        Contacts.DISPLAY_NAME,
    };

    private static final String TAG = "ContactsDictionary";

    /**
     * Frequency for contacts information into the dictionary
     */
    private static final int FREQUENCY_FOR_CONTACTS = 40;
    private static final int FREQUENCY_FOR_CONTACTS_BIGRAM = 90;

    private static final int INDEX_NAME = 1;

    private ContentObserver mObserver;

    private long mLastLoadedContacts;

    public ContactsDictionary(final Context context, final int dicTypeId) {
        super(context, dicTypeId);
        registerObserver(context);
        loadDictionary();
    }

    private synchronized void registerObserver(final Context context) {
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        if (mObserver != null) return;
        ContentResolver cres = context.getContentResolver();
        cres.registerContentObserver(
                Contacts.CONTENT_URI, true, mObserver = new ContentObserver(null) {
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
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.close();
    }

    @Override
    public void startDictionaryLoadingTaskLocked() {
        long now = SystemClock.uptimeMillis();
        if (mLastLoadedContacts == 0
                || now - mLastLoadedContacts > 30 * 60 * 1000 /* 30 minutes */) {
            super.startDictionaryLoadingTaskLocked();
        }
    }

    @Override
    public void loadDictionaryAsync() {
        try {
            Cursor cursor = getContext().getContentResolver()
                    .query(Contacts.CONTENT_URI, PROJECTION, null, null, null);
            if (cursor != null) {
                addWords(cursor);
            }
        } catch(IllegalStateException e) {
            Log.e(TAG, "Contacts DB is having problems");
        }
        mLastLoadedContacts = SystemClock.uptimeMillis();
    }

    @Override
    public void getBigrams(final WordComposer codes, final CharSequence previousWord,
            final WordCallback callback) {
        // Do not return bigrams from Contacts when nothing was typed.
        if (codes.size() <= 0) return;
        super.getBigrams(codes, previousWord, callback);
    }

    private void addWords(Cursor cursor) {
        clearDictionary();

        final int maxWordLength = getMaxWordLength();
        try {
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    String name = cursor.getString(INDEX_NAME);

                    if (name != null && -1 == name.indexOf('@')) {
                        int len = name.length();
                        String prevWord = null;

                        // TODO: Better tokenization for non-Latin writing systems
                        for (int i = 0; i < len; i++) {
                            if (Character.isLetter(name.charAt(i))) {
                                int j;
                                for (j = i + 1; j < len; j++) {
                                    char c = name.charAt(j);

                                    if (!(c == Keyboard.CODE_DASH
                                            || c == Keyboard.CODE_SINGLE_QUOTE
                                            || Character.isLetter(c))) {
                                        break;
                                    }
                                }

                                String word = name.substring(i, j);
                                i = j - 1;

                                // Safeguard against adding really long words. Stack
                                // may overflow due to recursion
                                // Also don't add single letter words, possibly confuses
                                // capitalization of i.
                                final int wordLen = word.length();
                                if (wordLen < maxWordLength && wordLen > 1) {
                                    super.addWord(word, null /* shortcut */,
                                            FREQUENCY_FOR_CONTACTS);
                                    if (!TextUtils.isEmpty(prevWord)) {
                                        super.setBigramAndGetFrequency(prevWord, word,
                                                FREQUENCY_FOR_CONTACTS_BIGRAM);
                                    }
                                    prevWord = word;
                                }
                            }
                        }
                    }
                    cursor.moveToNext();
                }
            }
            cursor.close();
        } catch(IllegalStateException e) {
            Log.e(TAG, "Contacts DB is having problems");
        }
    }
}
