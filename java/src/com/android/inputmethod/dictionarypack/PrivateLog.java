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

package com.android.inputmethod.dictionarypack;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Class to keep long-term log. This is inactive in production, and is only for debug purposes.
 */
public class PrivateLog {

    public static final boolean DEBUG = DictionaryProvider.DEBUG;

    private static final String LOG_DATABASE_NAME = "log";
    private static final String LOG_TABLE_NAME = "log";
    private static final int LOG_DATABASE_VERSION = 1;

    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_EVENT = "event";

    private static final String LOG_TABLE_CREATE = "CREATE TABLE " + LOG_TABLE_NAME + " ("
            + COLUMN_DATE + " TEXT,"
            + COLUMN_EVENT + " TEXT);";

    static final SimpleDateFormat sDateFormat = new SimpleDateFormat(
            "yyyy/MM/dd HH:mm:ss", Locale.ROOT);

    private static PrivateLog sInstance = new PrivateLog();
    private static DebugHelper sDebugHelper = null;

    private PrivateLog() {
    }

    public static synchronized PrivateLog getInstance(final Context context) {
        if (!DEBUG) return sInstance;
        synchronized(PrivateLog.class) {
            if (sDebugHelper == null) {
                sDebugHelper = new DebugHelper(context);
            }
            return sInstance;
        }
    }

    static class DebugHelper extends SQLiteOpenHelper {

        DebugHelper(final Context context) {
            super(context, LOG_DATABASE_NAME, null, LOG_DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (!DEBUG) return;
            db.execSQL(LOG_TABLE_CREATE);
            insert(db, "Created table");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (!DEBUG) return;
            // Remove all data.
            db.execSQL("DROP TABLE IF EXISTS " + LOG_TABLE_NAME);
            onCreate(db);
            insert(db, "Upgrade finished");
        }

        static void insert(SQLiteDatabase db, String event) {
            if (!DEBUG) return;
            final ContentValues c = new ContentValues(2);
            c.put(COLUMN_DATE, sDateFormat.format(new Date(System.currentTimeMillis())));
            c.put(COLUMN_EVENT, event);
            db.insert(LOG_TABLE_NAME, null, c);
        }

    }

    public static void log(String event) {
        if (!DEBUG) return;
        final SQLiteDatabase l = sDebugHelper.getWritableDatabase();
        DebugHelper.insert(l, event);
    }
}
