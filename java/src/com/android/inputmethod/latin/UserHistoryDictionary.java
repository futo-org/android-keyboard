/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Locally gathers stats about the words user types and various other signals like auto-correction
 * cancellation or manual picks. This allows the keyboard to adapt to the typist over time.
 */
public class UserHistoryDictionary extends ExpandableDictionary {
    private static final String TAG = "UserHistoryDictionary";

    /** Any pair being typed or picked */
    private static final int FREQUENCY_FOR_TYPED = 2;

    /** Maximum frequency for all pairs */
    private static final int FREQUENCY_MAX = 127;

    /** Maximum number of pairs. Pruning will start when databases goes above this number. */
    private static int sMaxHistoryBigrams = 10000;

    /**
     * When it hits maximum bigram pair, it will delete until you are left with
     * only (sMaxHistoryBigrams - sDeleteHistoryBigrams) pairs.
     * Do not keep this number small to avoid deleting too often.
     */
    private static int sDeleteHistoryBigrams = 1000;

    /**
     * Database version should increase if the database structure changes
     */
    private static final int DATABASE_VERSION = 1;

    private static final String DATABASE_NAME = "userbigram_dict.db";

    /** Name of the words table in the database */
    private static final String MAIN_TABLE_NAME = "main";
    // TODO: Consume less space by using a unique id for locale instead of the whole
    // 2-5 character string.
    private static final String MAIN_COLUMN_ID = BaseColumns._ID;
    private static final String MAIN_COLUMN_WORD1 = "word1";
    private static final String MAIN_COLUMN_WORD2 = "word2";
    private static final String MAIN_COLUMN_LOCALE = "locale";

    /** Name of the frequency table in the database */
    private static final String FREQ_TABLE_NAME = "frequency";
    private static final String FREQ_COLUMN_ID = BaseColumns._ID;
    private static final String FREQ_COLUMN_PAIR_ID = "pair_id";
    private static final String FREQ_COLUMN_FREQUENCY = "freq";

    /** Locale for which this auto dictionary is storing words */
    private String mLocale;

    private HashSet<Bigram> mPendingWrites = new HashSet<Bigram>();
    private final Object mPendingWritesLock = new Object();
    private static volatile boolean sUpdatingDB = false;

    private final static HashMap<String, String> sDictProjectionMap;

    static {
        sDictProjectionMap = new HashMap<String, String>();
        sDictProjectionMap.put(MAIN_COLUMN_ID, MAIN_COLUMN_ID);
        sDictProjectionMap.put(MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD1);
        sDictProjectionMap.put(MAIN_COLUMN_WORD2, MAIN_COLUMN_WORD2);
        sDictProjectionMap.put(MAIN_COLUMN_LOCALE, MAIN_COLUMN_LOCALE);

        sDictProjectionMap.put(FREQ_COLUMN_ID, FREQ_COLUMN_ID);
        sDictProjectionMap.put(FREQ_COLUMN_PAIR_ID, FREQ_COLUMN_PAIR_ID);
        sDictProjectionMap.put(FREQ_COLUMN_FREQUENCY, FREQ_COLUMN_FREQUENCY);
    }

    private static DatabaseHelper sOpenHelper = null;

    private static class Bigram {
        public final String mWord1;
        public final String mWord2;
        public final int mFrequency;

        Bigram(String word1, String word2, int frequency) {
            this.mWord1 = word1;
            this.mWord2 = word2;
            this.mFrequency = frequency;
        }

        @Override
        public boolean equals(Object bigram) {
            if (!(bigram instanceof Bigram)) {
                return false;
            }
            final Bigram bigram2 = (Bigram) bigram;
            final boolean eq1 =
                    mWord1 == null ? bigram2.mWord1 == null : mWord1.equals(bigram2.mWord1);
            if (!eq1) {
                return false;
            }
            return mWord2 == null ? bigram2.mWord2 == null : mWord2.equals(bigram2.mWord2);
        }

        @Override
        public int hashCode() {
            return (mWord1 + " " + mWord2).hashCode();
        }
    }

    public void setDatabaseMax(int maxHistoryBigram) {
        sMaxHistoryBigrams = maxHistoryBigram;
    }

    public void setDatabaseDelete(int deleteHistoryBigram) {
        sDeleteHistoryBigrams = deleteHistoryBigram;
    }

    public UserHistoryDictionary(final Context context, final String locale, final int dicTypeId) {
        super(context, dicTypeId);
        mLocale = locale;
        if (sOpenHelper == null) {
            sOpenHelper = new DatabaseHelper(getContext());
        }
        if (mLocale != null && mLocale.length() > 1) {
            loadDictionary();
        }
    }

    @Override
    public void close() {
        flushPendingWrites();
        // Don't close the database as locale changes will require it to be reopened anyway
        // Also, the database is written to somewhat frequently, so it needs to be kept alive
        // throughout the life of the process.
        // mOpenHelper.close();
        super.close();
    }

    /**
     * Return whether the passed charsequence is in the dictionary.
     */
    @Override
    public boolean isValidWord(final CharSequence word) {
        // TODO: figure out what is the correct thing to do here.
        return false;
    }

    /**
     * Pair will be added to the user history dictionary.
     *
     * The first word may be null. That means we don't know the context, in other words,
     * it's only a unigram. The first word may also be an empty string : this means start
     * context, as in beginning of a sentence for example.
     * The second word may not be null (a NullPointerException would be thrown).
     */
    public int addToUserHistory(final String word1, String word2) {
        super.addWord(word2, FREQUENCY_FOR_TYPED);
        // Do not insert a word as a bigram of itself
        if (word2.equals(word1)) {
            return 0;
        }

        int freq;
        if (null == word1) {
            freq = FREQUENCY_FOR_TYPED;
        } else {
            freq = super.addBigram(word1, word2, FREQUENCY_FOR_TYPED);
        }
        if (freq > FREQUENCY_MAX) freq = FREQUENCY_MAX;
        synchronized (mPendingWritesLock) {
            if (freq == FREQUENCY_FOR_TYPED || mPendingWrites.isEmpty()) {
                mPendingWrites.add(new Bigram(word1, word2, freq));
            } else {
                Bigram bi = new Bigram(word1, word2, freq);
                mPendingWrites.remove(bi);
                mPendingWrites.add(bi);
            }
        }

        return freq;
    }

    /**
     * Schedules a background thread to write any pending words to the database.
     */
    public void flushPendingWrites() {
        synchronized (mPendingWritesLock) {
            // Nothing pending? Return
            if (mPendingWrites.isEmpty()) return;
            // Create a background thread to write the pending entries
            new UpdateDbTask(sOpenHelper, mPendingWrites, mLocale).execute();
            // Create a new map for writing new entries into while the old one is written to db
            mPendingWrites = new HashSet<Bigram>();
        }
    }

    /** Used for testing purpose **/
    void waitUntilUpdateDBDone() {
        synchronized (mPendingWritesLock) {
            while (sUpdatingDB) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
            return;
        }
    }

    @Override
    public void loadDictionaryAsync() {
        // Load the words that correspond to the current input locale
        final Cursor cursor = query(MAIN_COLUMN_LOCALE + "=?", new String[] { mLocale });
        if (null == cursor) return;
        try {
            if (cursor.moveToFirst()) {
                int word1Index = cursor.getColumnIndex(MAIN_COLUMN_WORD1);
                int word2Index = cursor.getColumnIndex(MAIN_COLUMN_WORD2);
                int frequencyIndex = cursor.getColumnIndex(FREQ_COLUMN_FREQUENCY);
                while (!cursor.isAfterLast()) {
                    String word1 = cursor.getString(word1Index);
                    String word2 = cursor.getString(word2Index);
                    int frequency = cursor.getInt(frequencyIndex);
                    // Safeguard against adding really long words. Stack may overflow due
                    // to recursive lookup
                    if (null == word1) {
                        super.addWord(word2, frequency);
                    } else if (word1.length() < BinaryDictionary.MAX_WORD_LENGTH
                            && word2.length() < BinaryDictionary.MAX_WORD_LENGTH) {
                        super.setBigram(word1, word2, frequency);
                    }
                    cursor.moveToNext();
                }
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Query the database
     */
    private static Cursor query(String selection, String[] selectionArgs) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        // main INNER JOIN frequency ON (main._id=freq.pair_id)
        qb.setTables(MAIN_TABLE_NAME + " INNER JOIN " + FREQ_TABLE_NAME + " ON ("
                + MAIN_TABLE_NAME + "." + MAIN_COLUMN_ID + "=" + FREQ_TABLE_NAME + "."
                + FREQ_COLUMN_PAIR_ID +")");

        qb.setProjectionMap(sDictProjectionMap);

        // Get the database and run the query
        try {
            SQLiteDatabase db = sOpenHelper.getReadableDatabase();
            Cursor c = qb.query(db,
                    new String[] { MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD2, FREQ_COLUMN_FREQUENCY },
                    selection, selectionArgs, null, null, null);
            return c;
        } catch (android.database.sqlite.SQLiteCantOpenDatabaseException e) {
            // Can't open the database : presumably we can't access storage. That may happen
            // when the device is wedged; do a best effort to still start the keyboard.
            return null;
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
            db.execSQL("PRAGMA foreign_keys = ON;");
            db.execSQL("CREATE TABLE " + MAIN_TABLE_NAME + " ("
                    + MAIN_COLUMN_ID + " INTEGER PRIMARY KEY,"
                    + MAIN_COLUMN_WORD1 + " TEXT,"
                    + MAIN_COLUMN_WORD2 + " TEXT,"
                    + MAIN_COLUMN_LOCALE + " TEXT"
                    + ");");
            db.execSQL("CREATE TABLE " + FREQ_TABLE_NAME + " ("
                    + FREQ_COLUMN_ID + " INTEGER PRIMARY KEY,"
                    + FREQ_COLUMN_PAIR_ID + " INTEGER,"
                    + FREQ_COLUMN_FREQUENCY + " INTEGER,"
                    + "FOREIGN KEY(" + FREQ_COLUMN_PAIR_ID + ") REFERENCES " + MAIN_TABLE_NAME
                    + "(" + MAIN_COLUMN_ID + ")" + " ON DELETE CASCADE"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + MAIN_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + FREQ_TABLE_NAME);
            onCreate(db);
        }
    }

    /**
     * Async task to write pending words to the database so that it stays in sync with
     * the in-memory trie.
     */
    private static class UpdateDbTask extends AsyncTask<Void, Void, Void> {
        private final HashSet<Bigram> mMap;
        private final DatabaseHelper mDbHelper;
        private final String mLocale;

        public UpdateDbTask(DatabaseHelper openHelper, HashSet<Bigram> pendingWrites,
                String locale) {
            mMap = pendingWrites;
            mLocale = locale;
            mDbHelper = openHelper;
        }

        /** Prune any old data if the database is getting too big. */
        private static void checkPruneData(SQLiteDatabase db) {
            db.execSQL("PRAGMA foreign_keys = ON;");
            Cursor c = db.query(FREQ_TABLE_NAME, new String[] { FREQ_COLUMN_PAIR_ID },
                    null, null, null, null, null);
            try {
                int totalRowCount = c.getCount();
                // prune out old data if we have too much data
                if (totalRowCount > sMaxHistoryBigrams) {
                    int numDeleteRows = (totalRowCount - sMaxHistoryBigrams) + sDeleteHistoryBigrams;
                    int pairIdColumnId = c.getColumnIndex(FREQ_COLUMN_PAIR_ID);
                    c.moveToFirst();
                    int count = 0;
                    while (count < numDeleteRows && !c.isAfterLast()) {
                        String pairId = c.getString(pairIdColumnId);
                        // Deleting from MAIN table will delete the frequencies
                        // due to FOREIGN KEY .. ON DELETE CASCADE
                        db.delete(MAIN_TABLE_NAME, MAIN_COLUMN_ID + "=?",
                            new String[] { pairId });
                        c.moveToNext();
                        count++;
                    }
                }
            } finally {
                c.close();
            }
        }

        @Override
        protected void onPreExecute() {
            sUpdatingDB = true;
        }

        @Override
        protected Void doInBackground(Void... v) {
            SQLiteDatabase db = null;
            try {
                db = mDbHelper.getWritableDatabase();
            } catch (android.database.sqlite.SQLiteCantOpenDatabaseException e) {
                // If we can't open the db, don't do anything. Exit through the next test
                // for non-nullity of the db variable.
            }
            if (null == db) {
                // Not much we can do. Just exit.
                sUpdatingDB = false;
                return null;
            }
            db.execSQL("PRAGMA foreign_keys = ON;");
            // Write all the entries to the db
            Iterator<Bigram> iterator = mMap.iterator();
            while (iterator.hasNext()) {
                // TODO: this process of making a text search for each pair each time
                // is terribly inefficient. Optimize this.
                Bigram bi = iterator.next();

                // find pair id
                final Cursor c;
                if (null != bi.mWord1) {
                    c = db.query(MAIN_TABLE_NAME, new String[] { MAIN_COLUMN_ID },
                            MAIN_COLUMN_WORD1 + "=? AND " + MAIN_COLUMN_WORD2 + "=? AND "
                            + MAIN_COLUMN_LOCALE + "=?",
                            new String[] { bi.mWord1, bi.mWord2, mLocale }, null, null, null);
                } else {
                    c = db.query(MAIN_TABLE_NAME, new String[] { MAIN_COLUMN_ID },
                            MAIN_COLUMN_WORD1 + " IS NULL AND " + MAIN_COLUMN_WORD2 + "=? AND "
                            + MAIN_COLUMN_LOCALE + "=?",
                            new String[] { bi.mWord2, mLocale }, null, null, null);
                }

                int pairId;
                if (c.moveToFirst()) {
                    // existing pair
                    pairId = c.getInt(c.getColumnIndex(MAIN_COLUMN_ID));
                    db.delete(FREQ_TABLE_NAME, FREQ_COLUMN_PAIR_ID + "=?",
                            new String[] { Integer.toString(pairId) });
                } else {
                    // new pair
                    Long pairIdLong = db.insert(MAIN_TABLE_NAME, null,
                            getContentValues(bi.mWord1, bi.mWord2, mLocale));
                    pairId = pairIdLong.intValue();
                }
                c.close();

                // insert new frequency
                db.insert(FREQ_TABLE_NAME, null, getFrequencyContentValues(pairId, bi.mFrequency));
            }
            checkPruneData(db);
            sUpdatingDB = false;

            return null;
        }

        private static ContentValues getContentValues(String word1, String word2, String locale) {
            ContentValues values = new ContentValues(3);
            values.put(MAIN_COLUMN_WORD1, word1);
            values.put(MAIN_COLUMN_WORD2, word2);
            values.put(MAIN_COLUMN_LOCALE, locale);
            return values;
        }

        private static ContentValues getFrequencyContentValues(int pairId, int frequency) {
           ContentValues values = new ContentValues(2);
           values.put(FREQ_COLUMN_PAIR_ID, pairId);
           values.put(FREQ_COLUMN_FREQUENCY, frequency);
           return values;
        }
    }

}
