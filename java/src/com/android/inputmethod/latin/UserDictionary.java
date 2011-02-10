/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary.Words;

public class UserDictionary extends ExpandableDictionary {
    
    private static final String[] PROJECTION_QUERY = {
        Words.WORD,
        Words.FREQUENCY,
    };
    
    private static final String[] PROJECTION_ADD = {
        Words._ID,
        Words.FREQUENCY,
        Words.LOCALE,
    };
    
    private ContentObserver mObserver;
    private String mLocale;

    public UserDictionary(Context context, String locale) {
        super(context, Suggest.DIC_USER);
        mLocale = locale;
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        ContentResolver cres = context.getContentResolver();
        
        cres.registerContentObserver(Words.CONTENT_URI, true, mObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean self) {
                setRequiresReload(true);
            }
        });

        loadDictionary();
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
    public void loadDictionaryAsync() {
        Cursor cursor = getContext().getContentResolver()
                .query(Words.CONTENT_URI, PROJECTION_QUERY, "(locale IS NULL) or (locale=?)",
                        new String[] { mLocale }, null);
        addWords(cursor);
    }

    /**
     * Adds a word to the dictionary and makes it persistent.
     * @param word the word to add. If the word is capitalized, then the dictionary will
     * recognize it as a capitalized word when searched.
     * @param frequency the frequency of occurrence of the word. A frequency of 255 is considered
     * the highest.
     * @TODO use a higher or float range for frequency
     */
    @Override
    public synchronized void addWord(final String word, final int frequency) {
        // Force load the dictionary here synchronously
        if (getRequiresReload()) loadDictionaryAsync();
        // Safeguard against adding long words. Can cause stack overflow.
        if (word.length() >= getMaxWordLength()) return;

        super.addWord(word, frequency);

        // Update the user dictionary provider
        final ContentValues values = new ContentValues(5);
        values.put(Words.WORD, word);
        values.put(Words.FREQUENCY, frequency);
        values.put(Words.LOCALE, mLocale);
        values.put(Words.APP_ID, 0);

        final ContentResolver contentResolver = getContext().getContentResolver();
        new Thread("addWord") {
            @Override
            public void run() {
                Cursor cursor = contentResolver.query(Words.CONTENT_URI, PROJECTION_ADD,
                        "word=? and ((locale IS NULL) or (locale=?))",
                        new String[] { word, mLocale }, null);
                if (cursor != null && cursor.moveToFirst()) {
                    String locale = cursor.getString(cursor.getColumnIndex(Words.LOCALE));
                    // If locale is null, we will not override the entry.
                    if (locale != null && locale.equals(mLocale.toString())) {
                        long id = cursor.getLong(cursor.getColumnIndex(Words._ID));
                        Uri uri = Uri.withAppendedPath(Words.CONTENT_URI, Long.toString(id));
                        // Update the entry with new frequency value.
                        contentResolver.update(uri, values, null, null);
                    }
                } else {
                    // Insert new entry.
                    contentResolver.insert(Words.CONTENT_URI, values);
                }
            }
        }.start();

        // In case the above does a synchronous callback of the change observer
        setRequiresReload(false);
    }

    @Override
    public synchronized void getWords(final WordComposer codes, final WordCallback callback) {
        super.getWords(codes, callback);
    }

    @Override
    public synchronized boolean isValidWord(CharSequence word) {
        return super.isValidWord(word);
    }

    private void addWords(Cursor cursor) {
        clearDictionary();
        if (cursor == null) return;
        final int maxWordLength = getMaxWordLength();
        if (cursor.moveToFirst()) {
            final int indexWord = cursor.getColumnIndex(Words.WORD);
            final int indexFrequency = cursor.getColumnIndex(Words.FREQUENCY);
            while (!cursor.isAfterLast()) {
                String word = cursor.getString(indexWord);
                int frequency = cursor.getInt(indexFrequency);
                // Safeguard against adding really long words. Stack may overflow due
                // to recursion
                if (word.length() < maxWordLength) {
                    super.addWord(word, frequency);
                }
                cursor.moveToNext();
            }
        }
        cursor.close();
    }
}
