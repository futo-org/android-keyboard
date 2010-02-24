/*
 * Copyright (C) 2010 Google Inc.
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

import java.util.HashMap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.provider.UserDictionary.Words;
import android.util.Log;

/**
 * Stores new words temporarily until they are promoted to the user dictionary
 * for longevity. Words in the auto dictionary are used to determine if it's ok
 * to accept a word that's not in the main or user dictionary. Using a new word
 * repeatedly will promote it to the user dictionary.
 */
public class AutoDictionary extends ExpandableDictionary {
    // Weight added to a user picking a new word from the suggestion strip
    static final int FREQUENCY_FOR_PICKED = 3;
    // Weight added to a user typing a new word that doesn't get corrected (or is reverted)
    static final int FREQUENCY_FOR_TYPED = 1;
    // A word that is frequently typed and gets promoted to the user dictionary, uses this
    // frequency.
    static final int FREQUENCY_FOR_AUTO_ADD = 250;
    // If the user touches a typed word 2 times or more, it will become valid.
    private static final int VALIDITY_THRESHOLD = 2 * FREQUENCY_FOR_PICKED;
    // If the user touches a typed word 4 times or more, it will be added to the user dict.
    private static final int PROMOTION_THRESHOLD = 4 * FREQUENCY_FOR_PICKED;

    private LatinIME mIme;
    // Locale for which this auto dictionary is storing words
    private String mLocale;

    private static final String DATABASE_NAME = "auto_dict.db";
    private static final int DATABASE_VERSION = 1;

    // These are the columns in the dictionary
    // TODO: Consume less space by using a unique id for locale instead of the whole
    // 2-5 character string.
    private static final String COLUMN_ID = BaseColumns._ID;
    private static final String COLUMN_WORD = "word";
    private static final String COLUMN_FREQUENCY = "freq";
    private static final String COLUMN_LOCALE = "locale";

    /** Sort by descending order of frequency. */
    public static final String DEFAULT_SORT_ORDER = COLUMN_FREQUENCY + " DESC";

    /** Name of the words table in the auto_dict.db */
    private static final String AUTODICT_TABLE_NAME = "words";

    private static HashMap<String, String> sDictProjectionMap;

    static {
        sDictProjectionMap = new HashMap<String, String>();
        sDictProjectionMap.put(COLUMN_ID, COLUMN_ID);
        sDictProjectionMap.put(COLUMN_WORD, COLUMN_WORD);
        sDictProjectionMap.put(COLUMN_FREQUENCY, COLUMN_FREQUENCY);
        sDictProjectionMap.put(COLUMN_LOCALE, COLUMN_LOCALE);
    }

    private DatabaseHelper mOpenHelper;

    public AutoDictionary(Context context, LatinIME ime, String locale) {
        super(context);
        mIme = ime;
        mLocale = locale;
        mOpenHelper = new DatabaseHelper(getContext());
        if (mLocale != null && mLocale.length() > 1) {
            loadDictionary();
        }
    }

    @Override
    public boolean isValidWord(CharSequence word) {
        final int frequency = getWordFrequency(word);
        return frequency >= VALIDITY_THRESHOLD;
    }

    public void close() {
        mOpenHelper.close();
    }

    private void loadDictionary() {
        // Load the words that correspond to the current input locale
        Cursor cursor = query(COLUMN_LOCALE + "=?", new String[] { mLocale });
        if (cursor.moveToFirst()) {
            int wordIndex = cursor.getColumnIndex(COLUMN_WORD);
            int frequencyIndex = cursor.getColumnIndex(COLUMN_FREQUENCY);
            while (!cursor.isAfterLast()) {
                String word = cursor.getString(wordIndex);
                int frequency = cursor.getInt(frequencyIndex);
                // Safeguard against adding really long words. Stack may overflow due
                // to recursive lookup
                if (word.length() < getMaxWordLength()) {
                    super.addWord(word, frequency);
                }
                cursor.moveToNext();
            }
        }
        cursor.close();
    }

    @Override
    public void addWord(String word, int addFrequency) {
        final int length = word.length();
        // Don't add very short or very long words.
        if (length < 2 || length > getMaxWordLength()) return;
        if (mIme.getCurrentWord().isAutoCapitalized()) {
            // Remove caps before adding
            word = Character.toLowerCase(word.charAt(0)) + word.substring(1);
        }
        int freq = getWordFrequency(word);
        freq = freq < 0 ? addFrequency : freq + addFrequency;
        super.addWord(word, freq);
        if (freq >= PROMOTION_THRESHOLD) {
            mIme.promoteToUserDictionary(word, FREQUENCY_FOR_AUTO_ADD);
            // Delete the word (for input locale) from the auto dictionary db, as it
            // is now in the user dictionary provider.
            delete(COLUMN_WORD + "=? AND " + COLUMN_LOCALE + "=?",
                    new String[] { word, mLocale });
        } else {
            update(word, freq, mLocale);
        }
    }

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + AUTODICT_TABLE_NAME + " ("
                    + COLUMN_ID + " INTEGER PRIMARY KEY,"
                    + COLUMN_WORD + " TEXT,"
                    + COLUMN_FREQUENCY + " INTEGER,"
                    + COLUMN_LOCALE + " TEXT"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w("AutoDictionary", "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + AUTODICT_TABLE_NAME);
            onCreate(db);
        }
    }

    private Cursor query(String selection, String[] selectionArgs) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(AUTODICT_TABLE_NAME);
        qb.setProjectionMap(sDictProjectionMap);

        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, null, selection, selectionArgs, null, null,
                DEFAULT_SORT_ORDER);
        return c;
    }

    private boolean insert(ContentValues values) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(AUTODICT_TABLE_NAME, Words.WORD, values);
        if (rowId > 0) {
            return true;
        }
        return false;
    }

    private int delete(String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.delete(AUTODICT_TABLE_NAME, where, whereArgs);
        return count;
    }

    private int update(String word, int frequency, String locale) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long count = db.delete(AUTODICT_TABLE_NAME, COLUMN_WORD + "=? AND " + COLUMN_LOCALE + "=?",
                new String[] { word, locale });
        count = db.insert(AUTODICT_TABLE_NAME, null,
                getContentValues(word, frequency, locale));
        return (int) count;
    }

    private ContentValues getContentValues(String word, int frequency, String locale) {
        ContentValues values = new ContentValues(4);
        values.put(COLUMN_WORD, word);
        values.put(COLUMN_FREQUENCY, frequency);
        values.put(COLUMN_LOCALE, locale);
        return values;
    }
}
