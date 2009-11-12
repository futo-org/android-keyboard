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
import android.os.AsyncTask;
import android.os.SystemClock;
import android.provider.ContactsContract.Contacts;

public class ContactsDictionary extends ExpandableDictionary {

    private static final String[] PROJECTION = {
        Contacts._ID,
        Contacts.DISPLAY_NAME,
    };

    private static final int INDEX_NAME = 1;

    private ContentObserver mObserver;

    private boolean mRequiresReload;

    private long mLastLoadedContacts;

    private boolean mUpdatingContacts;

    // Use this lock before touching mUpdatingContacts & mRequiresDownload
    private Object mUpdatingLock = new Object();

    public ContactsDictionary(Context context) {
        super(context);
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        ContentResolver cres = context.getContentResolver();

        cres.registerContentObserver(Contacts.CONTENT_URI, true, mObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean self) {
                synchronized (mUpdatingLock) {
                    mRequiresReload = true;
                }
            }
        });

        synchronized (mUpdatingLock) {
            loadDictionaryAsyncLocked();
        }
    }

    public synchronized void close() {
        if (mObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
    }

    private synchronized void loadDictionaryAsyncLocked() {
        long now = SystemClock.uptimeMillis();
        if (mLastLoadedContacts == 0
                || now - mLastLoadedContacts > 30 * 60 * 1000 /* 30 minutes */) {
            if (!mUpdatingContacts) {
                mUpdatingContacts = true;
                mRequiresReload = false;
                new LoadContactsTask().execute();
            }
        }
    }

    @Override
    public synchronized void getWords(final WordComposer codes, final WordCallback callback) {
        synchronized (mUpdatingLock) {
            // If we need to update, start off a background task
            if (mRequiresReload) loadDictionaryAsyncLocked();
            // Currently updating contacts, don't return any results.
            if (mUpdatingContacts) return;
        }
        super.getWords(codes, callback);
    }

    @Override
    public synchronized boolean isValidWord(CharSequence word) {
        synchronized (mUpdatingLock) {
            // If we need to update, start off a background task
            if (mRequiresReload) loadDictionaryAsyncLocked();
            if (mUpdatingContacts) return false;
        }

        return super.isValidWord(word);
    }

    private void addWords(Cursor cursor) {
        clearDictionary();

        final int maxWordLength = getMaxWordLength();
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                String name = cursor.getString(INDEX_NAME);

                if (name != null) {
                    int len = name.length();

                    // TODO: Better tokenization for non-Latin writing systems
                    for (int i = 0; i < len; i++) {
                        if (Character.isLetter(name.charAt(i))) {
                            int j;
                            for (j = i + 1; j < len; j++) {
                                char c = name.charAt(j);

                                if (!(c == '-' || c == '\'' ||
                                      Character.isLetter(c))) {
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
                                super.addWord(word, 128);
                            }
                        }
                    }
                }

                cursor.moveToNext();
            }
        }
        cursor.close();
    }
    
    private class LoadContactsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... v) {
            Cursor cursor = getContext().getContentResolver()
                    .query(Contacts.CONTENT_URI, PROJECTION, null, null, null);
            if (cursor != null) {
                addWords(cursor);
            }
            mLastLoadedContacts = SystemClock.uptimeMillis();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // TODO Auto-generated method stub
            synchronized (mUpdatingLock) {
                mUpdatingContacts = false;
            }
            super.onPostExecute(result);
        }
        
    }
}
