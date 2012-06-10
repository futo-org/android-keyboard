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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.util.Log;

import com.android.inputmethod.latin.UserHistoryForgettingCurveUtils.ForgettingCurveParams;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Locally gathers stats about the words user types and various other signals like auto-correction
 * cancellation or manual picks. This allows the keyboard to adapt to the typist over time.
 */
public class UserHistoryDictionary extends ExpandableDictionary {
    private static final String TAG = "UserHistoryDictionary";
    public static final boolean DBG_SAVE_RESTORE = false;
    public static final boolean DBG_STRESS_TEST = false;
    public static final boolean DBG_ALWAYS_WRITE = false;
    public static final boolean PROFILE_SAVE_RESTORE = LatinImeLogger.sDBG;

    /** Any pair being typed or picked */
    private static final int FREQUENCY_FOR_TYPED = 2;

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
    private static final String COLUMN_FORGETTING_CURVE_VALUE = "freq";

    /** Locale for which this user history dictionary is storing words */
    private final String mLocale;

    private final UserHistoryDictionaryBigramList mBigramList =
            new UserHistoryDictionaryBigramList();
    private final ReentrantLock mBigramListLock = new ReentrantLock();
    private final SharedPreferences mPrefs;

    private final static HashMap<String, String> sDictProjectionMap;
    private final static ConcurrentHashMap<String, SoftReference<UserHistoryDictionary>>
            sLangDictCache = new ConcurrentHashMap<String, SoftReference<UserHistoryDictionary>>();

    static {
        sDictProjectionMap = new HashMap<String, String>();
        sDictProjectionMap.put(MAIN_COLUMN_ID, MAIN_COLUMN_ID);
        sDictProjectionMap.put(MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD1);
        sDictProjectionMap.put(MAIN_COLUMN_WORD2, MAIN_COLUMN_WORD2);
        sDictProjectionMap.put(MAIN_COLUMN_LOCALE, MAIN_COLUMN_LOCALE);

        sDictProjectionMap.put(FREQ_COLUMN_ID, FREQ_COLUMN_ID);
        sDictProjectionMap.put(FREQ_COLUMN_PAIR_ID, FREQ_COLUMN_PAIR_ID);
        sDictProjectionMap.put(COLUMN_FORGETTING_CURVE_VALUE, COLUMN_FORGETTING_CURVE_VALUE);
    }

    private static DatabaseHelper sOpenHelper = null;

    public void setDatabaseMax(int maxHistoryBigram) {
        sMaxHistoryBigrams = maxHistoryBigram;
    }

    public void setDatabaseDelete(int deleteHistoryBigram) {
        sDeleteHistoryBigrams = deleteHistoryBigram;
    }

    public synchronized static UserHistoryDictionary getInstance(
            final Context context, final String locale,
            final int dictTypeId, final SharedPreferences sp) {
        if (sLangDictCache.containsKey(locale)) {
            final SoftReference<UserHistoryDictionary> ref = sLangDictCache.get(locale);
            final UserHistoryDictionary dict = ref == null ? null : ref.get();
            if (dict != null) {
                if (PROFILE_SAVE_RESTORE) {
                    Log.w(TAG, "Use cached UserHistoryDictionary for " + locale);
                }
                return dict;
            }
        }
        final UserHistoryDictionary dict =
                new UserHistoryDictionary(context, locale, dictTypeId, sp);
        sLangDictCache.put(locale, new SoftReference<UserHistoryDictionary>(dict));
        return dict;
    }

    private UserHistoryDictionary(final Context context, final String locale, final int dicTypeId,
            SharedPreferences sp) {
        super(context, dicTypeId);
        mLocale = locale;
        mPrefs = sp;
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
        // Ignore close because we cache UserHistoryDictionary for each language. See getInstance()
        // above.
        // super.close();
    }

    /**
     * Return whether the passed charsequence is in the dictionary.
     */
    @Override
    public synchronized boolean isValidWord(final CharSequence word) {
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
    public int addToUserHistory(final String word1, String word2, boolean isValid) {
        if (mBigramListLock.tryLock()) {
            try {
                super.addWord(
                        word2, null /* the "shortcut" parameter is null */, FREQUENCY_FOR_TYPED);
                // Do not insert a word as a bigram of itself
                if (word2.equals(word1)) {
                    return 0;
                }
                final int freq;
                if (null == word1) {
                    freq = FREQUENCY_FOR_TYPED;
                } else {
                    freq = super.setBigramAndGetFrequency(
                            word1, word2, new ForgettingCurveParams(isValid));
                }
                mBigramList.addBigram(word1, word2);
                return freq;
            } finally {
                mBigramListLock.unlock();
            }
        }
        return -1;
    }

    public boolean cancelAddingUserHistory(String word1, String word2) {
        if (mBigramListLock.tryLock()) {
            try {
                if (mBigramList.removeBigram(word1, word2)) {
                    return super.removeBigram(word1, word2);
                }
            } finally {
                mBigramListLock.unlock();
            }
        }
        return false;
    }

    /**
     * Schedules a background thread to write any pending words to the database.
     */
    private void flushPendingWrites() {
        if (mBigramListLock.isLocked()) {
            return;
        }
        // Create a background thread to write the pending entries
        new UpdateDbTask(sOpenHelper, mBigramList, mLocale, this, mPrefs).execute();
    }

    @Override
    public void loadDictionaryAsync() {
        // This must be run on non-main thread
        mBigramListLock.lock();
        try {
            loadDictionaryAsyncLocked();
        } finally {
            mBigramListLock.unlock();
        }
    }

    private void loadDictionaryAsyncLocked() {
        if (DBG_STRESS_TEST) {
            try {
                Log.w(TAG, "Start stress in loading: " + mLocale);
                Thread.sleep(15000);
                Log.w(TAG, "End stress in loading");
            } catch (InterruptedException e) {
            }
        }
        final long last = SettingsValues.getLastUserHistoryWriteTime(mPrefs, mLocale);
        final boolean initializing = last == 0;
        final long now = System.currentTimeMillis();
        // Load the words that correspond to the current input locale
        final Cursor cursor = query(MAIN_COLUMN_LOCALE + "=?", new String[] { mLocale });
        if (null == cursor) return;
        try {
            // TODO: Call SQLiteDataBase.beginTransaction / SQLiteDataBase.endTransaction
            if (cursor.moveToFirst()) {
                final int word1Index = cursor.getColumnIndex(MAIN_COLUMN_WORD1);
                final int word2Index = cursor.getColumnIndex(MAIN_COLUMN_WORD2);
                final int fcIndex = cursor.getColumnIndex(COLUMN_FORGETTING_CURVE_VALUE);
                while (!cursor.isAfterLast()) {
                    final String word1 = cursor.getString(word1Index);
                    final String word2 = cursor.getString(word2Index);
                    final int fc = cursor.getInt(fcIndex);
                    if (DBG_SAVE_RESTORE) {
                        Log.d(TAG, "--- Load user history: " + word1 + ", " + word2 + ","
                                + mLocale + "," + this);
                    }
                    // Safeguard against adding really long words. Stack may overflow due
                    // to recursive lookup
                    if (null == word1) {
                        super.addWord(word2, null /* shortcut */, fc);
                    } else if (word1.length() < BinaryDictionary.MAX_WORD_LENGTH
                            && word2.length() < BinaryDictionary.MAX_WORD_LENGTH) {
                        super.setBigramAndGetFrequency(
                                word1, word2, initializing ? new ForgettingCurveParams(true)
                                : new ForgettingCurveParams(fc, now, last));
                    }
                    mBigramList.addBigram(word1, word2, (byte)fc);
                    cursor.moveToNext();
                }
            }
        } finally {
            cursor.close();
            if (PROFILE_SAVE_RESTORE) {
                final long diff = System.currentTimeMillis() - now;
                Log.w(TAG, "PROF: Load User HistoryDictionary: "
                        + mLocale + ", " + diff + "ms.");
            }
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
                    new String[] {
                            MAIN_COLUMN_WORD1, MAIN_COLUMN_WORD2, COLUMN_FORGETTING_CURVE_VALUE },
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
                    + COLUMN_FORGETTING_CURVE_VALUE + " INTEGER,"
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
        private final UserHistoryDictionaryBigramList mBigramList;
        private final DatabaseHelper mDbHelper;
        private final String mLocale;
        private final UserHistoryDictionary mUserHistoryDictionary;
        private final SharedPreferences mPrefs;

        public UpdateDbTask(
                DatabaseHelper openHelper, UserHistoryDictionaryBigramList pendingWrites,
                String locale, UserHistoryDictionary dict, SharedPreferences prefs) {
            mBigramList = pendingWrites;
            mLocale = locale;
            mDbHelper = openHelper;
            mUserHistoryDictionary = dict;
            mPrefs = prefs;
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
                    int numDeleteRows = (totalRowCount - sMaxHistoryBigrams)
                            + sDeleteHistoryBigrams;
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
        protected Void doInBackground(Void... v) {
            SQLiteDatabase db = null;
            if (mUserHistoryDictionary.mBigramListLock.tryLock()) {
                try {
                    try {
                        db = mDbHelper.getWritableDatabase();
                    } catch (android.database.sqlite.SQLiteCantOpenDatabaseException e) {
                        // If we can't open the db, don't do anything. Exit through the next test
                        // for non-nullity of the db variable.
                    }
                    if (null == db) {
                        // Not much we can do. Just exit.
                        return null;
                    }
                    db.beginTransaction();
                    return doLoadTaskLocked(db);
                } finally {
                    if (db != null) {
                        db.endTransaction();
                    }
                    mUserHistoryDictionary.mBigramListLock.unlock();
                }
            }
            return null;
        }

        private Void doLoadTaskLocked(SQLiteDatabase db) {
            if (DBG_STRESS_TEST) {
                try {
                    Log.w(TAG, "Start stress in closing: " + mLocale);
                    Thread.sleep(15000);
                    Log.w(TAG, "End stress in closing");
                } catch (InterruptedException e) {
                }
            }
            final long now = PROFILE_SAVE_RESTORE ? System.currentTimeMillis() : 0;
            int profTotal = 0;
            int profInsert = 0;
            int profDelete = 0;
            db.execSQL("PRAGMA foreign_keys = ON;");
            final boolean addLevel0Bigram = mBigramList.size() <= sMaxHistoryBigrams;

            // Write all the entries to the db
            for (String word1 : mBigramList.keySet()) {
                final HashMap<String, Byte> word1Bigrams = mBigramList.getBigrams(word1);
                for (String word2 : word1Bigrams.keySet()) {
                    if (PROFILE_SAVE_RESTORE) {
                        ++profTotal;
                    }
                    // Get new frequency. Do not insert unigrams/bigrams which freq is "-1".
                    final int freq; // -1, or 0~255
                    if (word1 == null) { // unigram
                        freq = FREQUENCY_FOR_TYPED;
                        final byte prevFc = word1Bigrams.get(word2);
                        if (prevFc == FREQUENCY_FOR_TYPED) {
                            // No need to update since we found no changes for this entry.
                            // Just skip to the next entry.
                            if (DBG_SAVE_RESTORE) {
                                Log.d(TAG, "Skip update user history: " + word1 + "," + word2
                                        + "," + prevFc);
                            }
                            if (!DBG_ALWAYS_WRITE) {
                                continue;
                            }
                        }
                    } else { // bigram
                        final NextWord nw = mUserHistoryDictionary.getBigramWord(word1, word2);
                        if (nw != null) {
                            final ForgettingCurveParams fcp = nw.getFcParams();
                            final byte prevFc = word1Bigrams.get(word2);
                            final byte fc = (byte)fcp.getFc();
                            final boolean isValid = fcp.isValid();
                            if (prevFc > 0 && prevFc == fc) {
                                // No need to update since we found no changes for this entry.
                                // Just skip to the next entry.
                                if (DBG_SAVE_RESTORE) {
                                    Log.d(TAG, "Skip update user history: " + word1 + ","
                                            + word2 + "," + prevFc);
                                }
                                if (!DBG_ALWAYS_WRITE) {
                                    continue;
                                } else {
                                    freq = fc;
                                }
                            } else if (UserHistoryForgettingCurveUtils.
                                    needsToSave(fc, isValid, addLevel0Bigram)) {
                                freq = fc;
                            } else {
                                freq = -1;
                            }
                        } else {
                            freq = -1;
                        }
                    }
                    // TODO: this process of making a text search for each pair each time
                    // is terribly inefficient. Optimize this.
                    // Find pair id
                    Cursor c = null;
                    try {
                        if (null != word1) {
                            c = db.query(MAIN_TABLE_NAME, new String[] { MAIN_COLUMN_ID },
                                    MAIN_COLUMN_WORD1 + "=? AND " + MAIN_COLUMN_WORD2 + "=? AND "
                                            + MAIN_COLUMN_LOCALE + "=?",
                                            new String[] { word1, word2, mLocale }, null, null,
                                            null);
                        } else {
                            c = db.query(MAIN_TABLE_NAME, new String[] { MAIN_COLUMN_ID },
                                    MAIN_COLUMN_WORD1 + " IS NULL AND " + MAIN_COLUMN_WORD2
                                    + "=? AND " + MAIN_COLUMN_LOCALE + "=?",
                                    new String[] { word2, mLocale }, null, null, null);
                        }

                        final int pairId;
                        if (c.moveToFirst()) {
                            if (PROFILE_SAVE_RESTORE) {
                                ++profDelete;
                            }
                            // Delete existing pair
                            pairId = c.getInt(c.getColumnIndex(MAIN_COLUMN_ID));
                            db.delete(FREQ_TABLE_NAME, FREQ_COLUMN_PAIR_ID + "=?",
                                    new String[] { Integer.toString(pairId) });
                        } else {
                            // Create new pair
                            Long pairIdLong = db.insert(MAIN_TABLE_NAME, null,
                                    getContentValues(word1, word2, mLocale));
                            pairId = pairIdLong.intValue();
                        }
                        if (freq > 0) {
                            if (PROFILE_SAVE_RESTORE) {
                                ++profInsert;
                            }
                            if (DBG_SAVE_RESTORE) {
                                Log.d(TAG, "--- Save user history: " + word1 + ", " + word2
                                        + mLocale + "," + this);
                            }
                            // Insert new frequency
                            db.insert(FREQ_TABLE_NAME, null,
                                    getFrequencyContentValues(pairId, freq));
                            // Update an existing bigram entry in mBigramList too in order to
                            // synchronize the SQL DB and mBigramList.
                            mBigramList.updateBigram(word1, word2, (byte)freq);
                        }
                    } finally {
                        if (c != null) {
                            c.close();
                        }
                    }
                }
            }

            checkPruneData(db);
            // Save the timestamp after we finish writing the SQL DB.
            SettingsValues.setLastUserHistoryWriteTime(mPrefs, mLocale);
            if (PROFILE_SAVE_RESTORE) {
                final long diff = System.currentTimeMillis() - now;
                Log.w(TAG, "PROF: Write User HistoryDictionary: " + mLocale + ", "+ diff
                        + "ms. Total: " + profTotal + ". Insert: " + profInsert + ". Delete: "
                        + profDelete);
            }
            db.setTransactionSuccessful();
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
           values.put(COLUMN_FORGETTING_CURVE_VALUE, frequency);
           return values;
        }
    }

}
