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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.DebugLogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import javax.annotation.Nullable;

/**
 * Various helper functions for the state database
 */
public class MetadataDbHelper extends SQLiteOpenHelper {
    private static final String TAG = MetadataDbHelper.class.getSimpleName();

    // This was the initial release version of the database. It should never be
    // changed going forward.
    private static final int METADATA_DATABASE_INITIAL_VERSION = 3;
    // This is the first released version of the database that implements CLIENTID. It is
    // used to identify the versions for upgrades. This should never change going forward.
    private static final int METADATA_DATABASE_VERSION_WITH_CLIENTID = 6;
    // The current database version.
    // This MUST be increased every time the dictionary pack metadata URL changes.
    private static final int CURRENT_METADATA_DATABASE_VERSION = 16;

    private final static long NOT_A_DOWNLOAD_ID = -1;

    // The number of retries allowed when attempting to download a broken dictionary.
    public static final int DICTIONARY_RETRY_THRESHOLD = 2;

    public static final String METADATA_TABLE_NAME = "pendingUpdates";
    static final String CLIENT_TABLE_NAME = "clients";
    public static final String PENDINGID_COLUMN = "pendingid"; // Download Manager ID
    public static final String TYPE_COLUMN = "type";
    public static final String STATUS_COLUMN = "status";
    public static final String LOCALE_COLUMN = "locale";
    public static final String WORDLISTID_COLUMN = "id";
    public static final String DESCRIPTION_COLUMN = "description";
    public static final String LOCAL_FILENAME_COLUMN = "filename";
    public static final String REMOTE_FILENAME_COLUMN = "url";
    public static final String DATE_COLUMN = "date";
    public static final String CHECKSUM_COLUMN = "checksum";
    public static final String FILESIZE_COLUMN = "filesize";
    public static final String VERSION_COLUMN = "version";
    public static final String FORMATVERSION_COLUMN = "formatversion";
    public static final String FLAGS_COLUMN = "flags";
    public static final String RAW_CHECKSUM_COLUMN = "rawChecksum";
    public static final String RETRY_COUNT_COLUMN = "remainingRetries";
    public static final int COLUMN_COUNT = 15;

    private static final String CLIENT_CLIENT_ID_COLUMN = "clientid";
    private static final String CLIENT_METADATA_URI_COLUMN = "uri";
    private static final String CLIENT_METADATA_ADDITIONAL_ID_COLUMN = "additionalid";
    private static final String CLIENT_LAST_UPDATE_DATE_COLUMN = "lastupdate";
    private static final String CLIENT_PENDINGID_COLUMN = "pendingid"; // Download Manager ID

    public static final String METADATA_DATABASE_NAME_STEM = "pendingUpdates";
    public static final String METADATA_UPDATE_DESCRIPTION = "metadata";

    public static final String DICTIONARIES_ASSETS_PATH = "dictionaries";

    // Statuses, for storing in the STATUS_COLUMN
    // IMPORTANT: The following are used as index arrays in ../WordListPreference
    // Do not change their values without updating the matched code.
    // Unknown status: this should never happen.
    public static final int STATUS_UNKNOWN = 0;
    // Available: this word list is available, but it is not downloaded (not downloading), because
    // it is set not to be used.
    public static final int STATUS_AVAILABLE = 1;
    // Downloading: this word list is being downloaded.
    public static final int STATUS_DOWNLOADING = 2;
    // Installed: this word list is installed and usable.
    public static final int STATUS_INSTALLED = 3;
    // Disabled: this word list is installed, but has been disabled by the user.
    public static final int STATUS_DISABLED = 4;
    // Deleting: the user marked this word list to be deleted, but it has not been yet because
    // Latin IME is not up yet.
    public static final int STATUS_DELETING = 5;
    // Retry: dictionary got corrupted, so an attempt must be done to download & install it again.
    public static final int STATUS_RETRYING = 6;

    // Types, for storing in the TYPE_COLUMN
    // This is metadata about what is available.
    public static final int TYPE_METADATA = 1;
    // This is a bulk file. It should replace older files.
    public static final int TYPE_BULK = 2;
    // This is an incremental update, expected to be small, and meaningless on its own.
    public static final int TYPE_UPDATE = 3;

    private static final String METADATA_TABLE_CREATE =
            "CREATE TABLE " + METADATA_TABLE_NAME + " ("
            + PENDINGID_COLUMN + " INTEGER, "
            + TYPE_COLUMN + " INTEGER, "
            + STATUS_COLUMN + " INTEGER, "
            + WORDLISTID_COLUMN + " TEXT, "
            + LOCALE_COLUMN + " TEXT, "
            + DESCRIPTION_COLUMN + " TEXT, "
            + LOCAL_FILENAME_COLUMN + " TEXT, "
            + REMOTE_FILENAME_COLUMN + " TEXT, "
            + DATE_COLUMN + " INTEGER, "
            + CHECKSUM_COLUMN + " TEXT, "
            + FILESIZE_COLUMN + " INTEGER, "
            + VERSION_COLUMN + " INTEGER,"
            + FORMATVERSION_COLUMN + " INTEGER, "
            + FLAGS_COLUMN + " INTEGER, "
            + RAW_CHECKSUM_COLUMN + " TEXT,"
            + RETRY_COUNT_COLUMN + " INTEGER, "
            + "PRIMARY KEY (" + WORDLISTID_COLUMN + "," + VERSION_COLUMN + "));";
    private static final String METADATA_CREATE_CLIENT_TABLE =
            "CREATE TABLE IF NOT EXISTS " + CLIENT_TABLE_NAME + " ("
            + CLIENT_CLIENT_ID_COLUMN + " TEXT, "
            + CLIENT_METADATA_URI_COLUMN + " TEXT, "
            + CLIENT_METADATA_ADDITIONAL_ID_COLUMN + " TEXT, "
            + CLIENT_LAST_UPDATE_DATE_COLUMN + " INTEGER NOT NULL DEFAULT 0, "
            + CLIENT_PENDINGID_COLUMN + " INTEGER, "
            + FLAGS_COLUMN + " INTEGER, "
            + "PRIMARY KEY (" + CLIENT_CLIENT_ID_COLUMN + "));";

    // List of all metadata table columns.
    static final String[] METADATA_TABLE_COLUMNS = { PENDINGID_COLUMN, TYPE_COLUMN,
            STATUS_COLUMN, WORDLISTID_COLUMN, LOCALE_COLUMN, DESCRIPTION_COLUMN,
            LOCAL_FILENAME_COLUMN, REMOTE_FILENAME_COLUMN, DATE_COLUMN, CHECKSUM_COLUMN,
            FILESIZE_COLUMN, VERSION_COLUMN, FORMATVERSION_COLUMN, FLAGS_COLUMN,
            RAW_CHECKSUM_COLUMN, RETRY_COUNT_COLUMN };
    // List of all client table columns.
    static final String[] CLIENT_TABLE_COLUMNS = { CLIENT_CLIENT_ID_COLUMN,
            CLIENT_METADATA_URI_COLUMN, CLIENT_PENDINGID_COLUMN, FLAGS_COLUMN };
    // List of public columns returned to clients. Everything that is not in this list is
    // private and implementation-dependent.
    static final String[] DICTIONARIES_LIST_PUBLIC_COLUMNS = { STATUS_COLUMN, WORDLISTID_COLUMN,
            LOCALE_COLUMN, DESCRIPTION_COLUMN, DATE_COLUMN, FILESIZE_COLUMN, VERSION_COLUMN };

    // This class exhibits a singleton-like behavior by client ID, so it is getInstance'd
    // and has a private c'tor.
    private static TreeMap<String, MetadataDbHelper> sInstanceMap = null;
    public static synchronized MetadataDbHelper getInstance(final Context context,
            final String clientIdOrNull) {
        // As a backward compatibility feature, null can be passed here to retrieve the "default"
        // database. Before multi-client support, the dictionary packed used only one database
        // and would not be able to handle several dictionary sets. Passing null here retrieves
        // this legacy database. New clients should make sure to always pass a client ID so as
        // to avoid conflicts.
        final String clientId = null != clientIdOrNull ? clientIdOrNull : "";
        if (null == sInstanceMap) sInstanceMap = new TreeMap<>();
        MetadataDbHelper helper = sInstanceMap.get(clientId);
        if (null == helper) {
            helper = new MetadataDbHelper(context, clientId);
            sInstanceMap.put(clientId, helper);
        }
        return helper;
    }
    private MetadataDbHelper(final Context context, final String clientId) {
        super(context,
                METADATA_DATABASE_NAME_STEM + (TextUtils.isEmpty(clientId) ? "" : "." + clientId),
                null, CURRENT_METADATA_DATABASE_VERSION);
        mContext = context;
        mClientId = clientId;
    }

    private final Context mContext;
    private final String mClientId;

    /**
     * Get the database itself. This always returns the same object for any client ID. If the
     * client ID is null, a default database is returned for backward compatibility. Don't
     * pass null for new calls.
     *
     * @param context the context to create the database from. This is ignored after the first call.
     * @param clientId the client id to retrieve the database of. null for default (deprecated)
     * @return the database.
     */
    public static SQLiteDatabase getDb(final Context context, final String clientId) {
        return getInstance(context, clientId).getWritableDatabase();
    }

    private void createClientTable(final SQLiteDatabase db) {
        // The clients table only exists in the primary db, the one that has an empty client id
        if (!TextUtils.isEmpty(mClientId)) return;
        db.execSQL(METADATA_CREATE_CLIENT_TABLE);
        final String defaultMetadataUri = mContext.getString(R.string.default_metadata_uri);
        if (!TextUtils.isEmpty(defaultMetadataUri)) {
            final ContentValues defaultMetadataValues = new ContentValues();
            defaultMetadataValues.put(CLIENT_CLIENT_ID_COLUMN, "");
            defaultMetadataValues.put(CLIENT_METADATA_URI_COLUMN, defaultMetadataUri);
            defaultMetadataValues.put(CLIENT_PENDINGID_COLUMN, UpdateHandler.NOT_AN_ID);
            db.insert(CLIENT_TABLE_NAME, null, defaultMetadataValues);
        }
    }

    /**
     * Create the table and populate it with the resources found inside the apk.
     *
     * @see SQLiteOpenHelper#onCreate(SQLiteDatabase)
     *
     * @param db the database to create and populate.
     */
    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL(METADATA_TABLE_CREATE);
        createClientTable(db);
    }

    private static void addRawChecksumColumnUnlessPresent(final SQLiteDatabase db) {
        try {
            db.execSQL("SELECT " + RAW_CHECKSUM_COLUMN + " FROM "
                    + METADATA_TABLE_NAME + " LIMIT 0;");
        } catch (SQLiteException e) {
            Log.i(TAG, "No " + RAW_CHECKSUM_COLUMN + " column : creating it");
            db.execSQL("ALTER TABLE " + METADATA_TABLE_NAME + " ADD COLUMN "
                    + RAW_CHECKSUM_COLUMN + " TEXT;");
        }
    }

    private static void addRetryCountColumnUnlessPresent(final SQLiteDatabase db) {
        try {
            db.execSQL("SELECT " + RETRY_COUNT_COLUMN + " FROM "
                    + METADATA_TABLE_NAME + " LIMIT 0;");
        } catch (SQLiteException e) {
            Log.i(TAG, "No " + RETRY_COUNT_COLUMN + " column : creating it");
            db.execSQL("ALTER TABLE " + METADATA_TABLE_NAME + " ADD COLUMN "
                    + RETRY_COUNT_COLUMN + " INTEGER DEFAULT " + DICTIONARY_RETRY_THRESHOLD + ";");
        }
    }

    /**
     * Upgrade the database. Upgrade from version 3 is supported.
     * Version 3 has a DB named METADATA_DATABASE_NAME_STEM containing a table METADATA_TABLE_NAME.
     * Version 6 and above has a DB named METADATA_DATABASE_NAME_STEM containing a
     * table CLIENT_TABLE_NAME, and for each client a table called METADATA_TABLE_STEM + "." + the
     * name of the client and contains a table METADATA_TABLE_NAME.
     * For schemas, see the above create statements. The schemas have never changed so far.
     *
     * This method is called by the framework. See {@link SQLiteOpenHelper#onUpgrade}
     * @param db The database we are upgrading
     * @param oldVersion The old database version (the one on the disk)
     * @param newVersion The new database version as supplied to the constructor of SQLiteOpenHelper
     */
    @Override
    public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        if (METADATA_DATABASE_INITIAL_VERSION == oldVersion
                && METADATA_DATABASE_VERSION_WITH_CLIENTID <= newVersion
                && CURRENT_METADATA_DATABASE_VERSION >= newVersion) {
            // Upgrade from version METADATA_DATABASE_INITIAL_VERSION to version
            // METADATA_DATABASE_VERSION_WITH_CLIENT_ID
            // Only the default database should contain the client table, so we test for mClientId.
            if (TextUtils.isEmpty(mClientId)) {
                // Anyway in version 3 only the default table existed so the emptiness
                // test should always be true, but better check to be sure.
                createClientTable(db);
            }
        } else if (METADATA_DATABASE_VERSION_WITH_CLIENTID < newVersion
                && CURRENT_METADATA_DATABASE_VERSION >= newVersion) {
            // Here we drop the client table, so that all clients send us their information again.
            // The client table contains the URL to hit to update the available dictionaries list,
            // but the info about the dictionaries themselves is stored in the table called
            // METADATA_TABLE_NAME and we want to keep it, so we only drop the client table.
            db.execSQL("DROP TABLE IF EXISTS " + CLIENT_TABLE_NAME);
            // Only the default database should contain the client table, so we test for mClientId.
            if (TextUtils.isEmpty(mClientId)) {
                createClientTable(db);
            }
        } else {
            // If we're not in the above case, either we are upgrading from an earlier versionCode
            // and we should wipe the database, or we are handling a version we never heard about
            // (can only be a bug) so it's safer to wipe the database.
            db.execSQL("DROP TABLE IF EXISTS " + METADATA_TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + CLIENT_TABLE_NAME);
            onCreate(db);
        }
        // A rawChecksum column that did not exist in the previous versions was added that
        // corresponds to the md5 checksum of the file after decompression/decryption. This is to
        // strengthen the system against corrupted dictionary files.
        // The most secure way to upgrade a database is to just test for the column presence, and
        // add it if it's not there.
        addRawChecksumColumnUnlessPresent(db);

        // A retry count column that did not exist in the previous versions was added that
        // corresponds to the number of download & installation attempts that have been made
        // in order to strengthen the system recovery from corrupted dictionary files.
        // The most secure way to upgrade a database is to just test for the column presence, and
        // add it if it's not there.
        addRetryCountColumnUnlessPresent(db);
    }

    /**
     * Downgrade the database. This drops and recreates the table in all cases.
     */
    @Override
    public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        // No matter what the numerical values of oldVersion and newVersion are, we know this
        // is a downgrade (newVersion < oldVersion). There is no way to know what the future
        // databases will look like, but we know it's extremely likely that it's okay to just
        // drop the tables and start from scratch. Hence, we ignore the versions and just wipe
        // everything we want to use.
        if (oldVersion <= newVersion) {
            Log.e(TAG, "onDowngrade database but new version is higher? " + oldVersion + " <= "
                    + newVersion);
        }
        db.execSQL("DROP TABLE IF EXISTS " + METADATA_TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + CLIENT_TABLE_NAME);
        onCreate(db);
    }

    /**
     * Given a client ID, returns whether this client exists.
     *
     * @param context a context to open the database
     * @param clientId the client ID to check
     * @return true if the client is known, false otherwise
     */
    public static boolean isClientKnown(final Context context, final String clientId) {
        // If the client is known, they'll have a non-null metadata URI. An empty string is
        // allowed as a metadata URI, if the client doesn't want any updates to happen.
        return null != getMetadataUriAsString(context, clientId);
    }

    private static final MetadataUriGetter sMetadataUriGetter = new MetadataUriGetter();

    /**
     * Returns the metadata URI as a string.
     *
     * If the client is not known, this will return null. If it is known, it will return
     * the URI as a string. Note that the empty string is a valid value.
     *
     * @param context a context instance to open the database on
     * @param clientId the ID of the client we want the metadata URI of
     * @return the string representation of the URI
     */
    public static String getMetadataUriAsString(final Context context, final String clientId) {
        SQLiteDatabase defaultDb = MetadataDbHelper.getDb(context, null);
        final Cursor cursor = defaultDb.query(MetadataDbHelper.CLIENT_TABLE_NAME,
                new String[] { MetadataDbHelper.CLIENT_METADATA_URI_COLUMN },
                MetadataDbHelper.CLIENT_CLIENT_ID_COLUMN + " = ?", new String[] { clientId },
                null, null, null, null);
        try {
            if (!cursor.moveToFirst()) return null;
            return sMetadataUriGetter.getUri(context, cursor.getString(0));
        } finally {
            cursor.close();
        }
    }

    /**
     * Update the last metadata update time for all clients using a particular URI.
     *
     * This method searches for all clients using a particular URI and updates the last
     * update time for this client.
     * The current time is used as the latest update time. This saved date will be what
     * is returned henceforth by {@link #getLastUpdateDateForClient(Context, String)},
     * until this method is called again.
     *
     * @param context a context instance to open the database on
     * @param uri the metadata URI we just downloaded
     */
    public static void saveLastUpdateTimeOfUri(final Context context, final String uri) {
        PrivateLog.log("Save last update time of URI : " + uri + " " + System.currentTimeMillis());
        final ContentValues values = new ContentValues();
        values.put(CLIENT_LAST_UPDATE_DATE_COLUMN, System.currentTimeMillis());
        final SQLiteDatabase defaultDb = getDb(context, null);
        final Cursor cursor = MetadataDbHelper.queryClientIds(context);
        if (null == cursor) return;
        try {
            if (!cursor.moveToFirst()) return;
            do {
                final String clientId = cursor.getString(0);
                final String metadataUri =
                        MetadataDbHelper.getMetadataUriAsString(context, clientId);
                if (metadataUri.equals(uri)) {
                    defaultDb.update(CLIENT_TABLE_NAME, values,
                            CLIENT_CLIENT_ID_COLUMN + " = ?", new String[] { clientId });
                }
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
    }

    /**
     * Retrieves the last date at which we updated the metadata for this client.
     *
     * The returned date is in milliseconds from the EPOCH; this is the same unit as
     * returned by {@link System#currentTimeMillis()}.
     *
     * @param context a context instance to open the database on
     * @param clientId the client ID to get the latest update date of
     * @return the last date at which this client was updated, as a long.
     */
    public static long getLastUpdateDateForClient(final Context context, final String clientId) {
        SQLiteDatabase defaultDb = getDb(context, null);
        final Cursor cursor = defaultDb.query(CLIENT_TABLE_NAME,
                new String[] { CLIENT_LAST_UPDATE_DATE_COLUMN },
                CLIENT_CLIENT_ID_COLUMN + " = ?",
                new String[] { null == clientId ? "" : clientId },
                null, null, null, null);
        try {
            if (!cursor.moveToFirst()) return 0;
            return cursor.getLong(0); // Only one column, return it
        } finally {
            cursor.close();
        }
    }

    /**
     * Get the metadata download ID for a metadata URI.
     *
     * This will retrieve the download ID for the metadata file that has the passed URI.
     * If this URI is not being downloaded right now, it will return NOT_AN_ID.
     *
     * @param context a context instance to open the database on
     * @param uri the URI to retrieve the metadata download ID of
     * @return the download id and start date, or null if the URL is not known
     */
    public static DownloadIdAndStartDate getMetadataDownloadIdAndStartDateForURI(
            final Context context, final String uri) {
        SQLiteDatabase defaultDb = getDb(context, null);
        final Cursor cursor = defaultDb.query(CLIENT_TABLE_NAME,
                new String[] { CLIENT_PENDINGID_COLUMN, CLIENT_LAST_UPDATE_DATE_COLUMN },
                CLIENT_METADATA_URI_COLUMN + " = ?", new String[] { uri },
                null, null, null, null);
        try {
            if (!cursor.moveToFirst()) return null;
            return new DownloadIdAndStartDate(cursor.getInt(0), cursor.getLong(1));
        } finally {
            cursor.close();
        }
    }

    public static long getOldestUpdateTime(final Context context) {
        SQLiteDatabase defaultDb = getDb(context, null);
        final Cursor cursor = defaultDb.query(CLIENT_TABLE_NAME,
                new String[] { CLIENT_LAST_UPDATE_DATE_COLUMN },
                null, null, null, null, null);
        try {
            if (!cursor.moveToFirst()) return 0;
            final int columnIndex = 0; // Only one column queried
            // Initialize the earliestTime to the largest possible value.
            long earliestTime = Long.MAX_VALUE; // Almost 300 million years in the future
            do {
                final long thisTime = cursor.getLong(columnIndex);
                earliestTime = Math.min(thisTime, earliestTime);
            } while (cursor.moveToNext());
            return earliestTime;
        } finally {
            cursor.close();
        }
    }

    /**
     * Helper method to make content values to write into the database.
     * @return content values with all the arguments put with the right column names.
     */
    public static ContentValues makeContentValues(final int pendingId, final int type,
            final int status, final String wordlistId, final String locale,
            final String description, final String filename, final String url, final long date,
            final String rawChecksum, final String checksum, final int retryCount,
            final long filesize, final int version, final int formatVersion) {
        final ContentValues result = new ContentValues(COLUMN_COUNT);
        result.put(PENDINGID_COLUMN, pendingId);
        result.put(TYPE_COLUMN, type);
        result.put(WORDLISTID_COLUMN, wordlistId);
        result.put(STATUS_COLUMN, status);
        result.put(LOCALE_COLUMN, locale);
        result.put(DESCRIPTION_COLUMN, description);
        result.put(LOCAL_FILENAME_COLUMN, filename);
        result.put(REMOTE_FILENAME_COLUMN, url);
        result.put(DATE_COLUMN, date);
        result.put(RAW_CHECKSUM_COLUMN, rawChecksum);
        result.put(RETRY_COUNT_COLUMN, retryCount);
        result.put(CHECKSUM_COLUMN, checksum);
        result.put(FILESIZE_COLUMN, filesize);
        result.put(VERSION_COLUMN, version);
        result.put(FORMATVERSION_COLUMN, formatVersion);
        result.put(FLAGS_COLUMN, 0);
        return result;
    }

    /**
     * Helper method to fill in an incomplete ContentValues with default values.
     * A wordlist ID and a locale are required, otherwise BadFormatException is thrown.
     * @return the same object that was passed in, completed with default values.
     */
    public static ContentValues completeWithDefaultValues(final ContentValues result)
            throws BadFormatException {
        if (null == result.get(WORDLISTID_COLUMN) || null == result.get(LOCALE_COLUMN)) {
            throw new BadFormatException();
        }
        // 0 for the pending id, because there is none
        if (null == result.get(PENDINGID_COLUMN)) result.put(PENDINGID_COLUMN, 0);
        // This is a binary blob of a dictionary
        if (null == result.get(TYPE_COLUMN)) result.put(TYPE_COLUMN, TYPE_BULK);
        // This word list is unknown, but it's present, else we wouldn't be here, so INSTALLED
        if (null == result.get(STATUS_COLUMN)) result.put(STATUS_COLUMN, STATUS_INSTALLED);
        // No description unless specified, because we can't guess it
        if (null == result.get(DESCRIPTION_COLUMN)) result.put(DESCRIPTION_COLUMN, "");
        // File name - this is an asset, so it works as an already deleted file.
        //     hence, we need to supply a non-existent file name. Anything will
        //     do as long as it returns false when tested with File#exist(), and
        //     the empty string does not, so it's set to "_".
        if (null == result.get(LOCAL_FILENAME_COLUMN)) result.put(LOCAL_FILENAME_COLUMN, "_");
        // No remote file name : this can't be downloaded. Unless specified.
        if (null == result.get(REMOTE_FILENAME_COLUMN)) result.put(REMOTE_FILENAME_COLUMN, "");
        // 0 for the update date : 1970/1/1. Unless specified.
        if (null == result.get(DATE_COLUMN)) result.put(DATE_COLUMN, 0);
        // Raw checksum unknown unless specified
        if (null == result.get(RAW_CHECKSUM_COLUMN)) result.put(RAW_CHECKSUM_COLUMN, "");
        // Retry column 0 unless specified
        if (null == result.get(RETRY_COUNT_COLUMN)) result.put(RETRY_COUNT_COLUMN,
                DICTIONARY_RETRY_THRESHOLD);
        // Checksum unknown unless specified
        if (null == result.get(CHECKSUM_COLUMN)) result.put(CHECKSUM_COLUMN, "");
        // No filesize unless specified
        if (null == result.get(FILESIZE_COLUMN)) result.put(FILESIZE_COLUMN, 0);
        // Smallest possible version unless specified
        if (null == result.get(VERSION_COLUMN)) result.put(VERSION_COLUMN, 1);
        // Assume current format unless specified
        if (null == result.get(FORMATVERSION_COLUMN))
            result.put(FORMATVERSION_COLUMN, UpdateHandler.MAXIMUM_SUPPORTED_FORMAT_VERSION);
        // No flags unless specified
        if (null == result.get(FLAGS_COLUMN)) result.put(FLAGS_COLUMN, 0);
        return result;
    }

    /**
     * Reads a column in a Cursor as a String and stores it in a ContentValues object.
     * @param result the ContentValues object to store the result in.
     * @param cursor the Cursor to read the column from.
     * @param columnId the column ID to read.
     */
    private static void putStringResult(ContentValues result, Cursor cursor, String columnId) {
        result.put(columnId, cursor.getString(cursor.getColumnIndex(columnId)));
    }

    /**
     * Reads a column in a Cursor as an int and stores it in a ContentValues object.
     * @param result the ContentValues object to store the result in.
     * @param cursor the Cursor to read the column from.
     * @param columnId the column ID to read.
     */
    private static void putIntResult(ContentValues result, Cursor cursor, String columnId) {
        result.put(columnId, cursor.getInt(cursor.getColumnIndex(columnId)));
    }

    private static ContentValues getFirstLineAsContentValues(final Cursor cursor) {
        final ContentValues result;
        if (cursor.moveToFirst()) {
            result = new ContentValues(COLUMN_COUNT);
            putIntResult(result, cursor, PENDINGID_COLUMN);
            putIntResult(result, cursor, TYPE_COLUMN);
            putIntResult(result, cursor, STATUS_COLUMN);
            putStringResult(result, cursor, WORDLISTID_COLUMN);
            putStringResult(result, cursor, LOCALE_COLUMN);
            putStringResult(result, cursor, DESCRIPTION_COLUMN);
            putStringResult(result, cursor, LOCAL_FILENAME_COLUMN);
            putStringResult(result, cursor, REMOTE_FILENAME_COLUMN);
            putIntResult(result, cursor, DATE_COLUMN);
            putStringResult(result, cursor, RAW_CHECKSUM_COLUMN);
            putStringResult(result, cursor, CHECKSUM_COLUMN);
            putIntResult(result, cursor, RETRY_COUNT_COLUMN);
            putIntResult(result, cursor, FILESIZE_COLUMN);
            putIntResult(result, cursor, VERSION_COLUMN);
            putIntResult(result, cursor, FORMATVERSION_COLUMN);
            putIntResult(result, cursor, FLAGS_COLUMN);
            if (cursor.moveToNext()) {
                // TODO: print the second level of the stack to the log so that we know
                // in which code path the error happened
                Log.e(TAG, "Several SQL results when we expected only one!");
            }
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Gets the info about as specific download, indexed by its DownloadManager ID.
     * @param db the database to get the information from.
     * @param id the DownloadManager id.
     * @return metadata about this download. This returns all columns in the database.
     */
    public static ContentValues getContentValuesByPendingId(final SQLiteDatabase db,
            final long id) {
        final Cursor cursor = db.query(METADATA_TABLE_NAME,
                METADATA_TABLE_COLUMNS,
                PENDINGID_COLUMN + "= ?",
                new String[] { Long.toString(id) },
                null, null, null);
        if (null == cursor) {
            return null;
        }
        try {
            // There should never be more than one result. If because of some bug there are,
            // returning only one result is the right thing to do, because we couldn't handle
            // several anyway and we should still handle one.
            return getFirstLineAsContentValues(cursor);
        } finally {
            cursor.close();
        }
    }

    /**
     * Gets the info about an installed OR deleting word list with a specified id.
     *
     * Basically, this is the word list that we want to return to Android Keyboard when
     * it asks for a specific id.
     *
     * @param db the database to get the information from.
     * @param id the word list ID.
     * @return the metadata about this word list.
     */
    public static ContentValues getInstalledOrDeletingWordListContentValuesByWordListId(
            final SQLiteDatabase db, final String id) {
        final Cursor cursor = db.query(METADATA_TABLE_NAME,
                METADATA_TABLE_COLUMNS,
                WORDLISTID_COLUMN + "=? AND (" + STATUS_COLUMN + "=? OR " + STATUS_COLUMN + "=?)",
                new String[] { id, Integer.toString(STATUS_INSTALLED),
                        Integer.toString(STATUS_DELETING) },
                null, null, null);
        if (null == cursor) {
            return null;
        }
        try {
            // There should only be one result, but if there are several, we can't tell which
            // is the best, so we just return the first one.
            return getFirstLineAsContentValues(cursor);
        } finally {
            cursor.close();
        }
    }

    /**
     * Given a specific download ID, return records for all pending downloads across all clients.
     *
     * If several clients use the same metadata URL, we know to only download it once, and
     * dispatch the update process across all relevant clients when the download ends. This means
     * several clients may share a single download ID if they share a metadata URI.
     * The dispatching is done in
     * {@link UpdateHandler#downloadFinished(Context, android.content.Intent)}, which
     * finds out about the list of relevant clients by calling this method.
     *
     * @param context a context instance to open the databases
     * @param downloadId the download ID to query about
     * @return the list of records. Never null, but may be empty.
     */
    public static ArrayList<DownloadRecord> getDownloadRecordsForDownloadId(final Context context,
            final long downloadId) {
        final SQLiteDatabase defaultDb = getDb(context, "");
        final ArrayList<DownloadRecord> results = new ArrayList<>();
        final Cursor cursor = defaultDb.query(CLIENT_TABLE_NAME, CLIENT_TABLE_COLUMNS,
                null, null, null, null, null);
        try {
            if (!cursor.moveToFirst()) return results;
            final int clientIdIndex = cursor.getColumnIndex(CLIENT_CLIENT_ID_COLUMN);
            final int pendingIdColumn = cursor.getColumnIndex(CLIENT_PENDINGID_COLUMN);
            do {
                final long pendingId = cursor.getInt(pendingIdColumn);
                final String clientId = cursor.getString(clientIdIndex);
                if (pendingId == downloadId) {
                    results.add(new DownloadRecord(clientId, null));
                }
                final ContentValues valuesForThisClient =
                        getContentValuesByPendingId(getDb(context, clientId), downloadId);
                if (null != valuesForThisClient) {
                    results.add(new DownloadRecord(clientId, valuesForThisClient));
                }
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
        return results;
    }

    /**
     * Gets the info about a specific word list.
     *
     * @param db the database to get the information from.
     * @param id the word list ID.
     * @param version the word list version.
     * @return the metadata about this word list.
     */
    @Nullable
    public static ContentValues getContentValuesByWordListId(final SQLiteDatabase db,
            final String id, final int version) {
        final Cursor cursor = db.query(METADATA_TABLE_NAME,
                METADATA_TABLE_COLUMNS,
                WORDLISTID_COLUMN + "= ? AND " + VERSION_COLUMN + "= ? AND "
                        + FORMATVERSION_COLUMN + "<= ?",
                new String[]
                        { id,
                          Integer.toString(version),
                          Integer.toString(UpdateHandler.MAXIMUM_SUPPORTED_FORMAT_VERSION)
                        },
                null /* groupBy */,
                null /* having */,
                FORMATVERSION_COLUMN + " DESC"/* orderBy */);
        if (null == cursor) {
            return null;
        }
        try {
            // This is a lookup by primary key, so there can't be more than one result.
            return getFirstLineAsContentValues(cursor);
        } finally {
            cursor.close();
        }
    }

    /**
     * Gets the info about the latest word list with an id.
     *
     * @param db the database to get the information from.
     * @param id the word list ID.
     * @return the metadata about the word list with this id and the latest version number.
     */
    public static ContentValues getContentValuesOfLatestAvailableWordlistById(
            final SQLiteDatabase db, final String id) {
        final Cursor cursor = db.query(METADATA_TABLE_NAME,
                METADATA_TABLE_COLUMNS,
                WORDLISTID_COLUMN + "= ?",
                new String[] { id }, null, null, VERSION_COLUMN + " DESC", "1");
        if (null == cursor) {
            return null;
        }
        try {
            // Return the first result from the list of results.
            return getFirstLineAsContentValues(cursor);
        } finally {
            cursor.close();
        }
    }

    /**
     * Gets the current metadata about INSTALLED, AVAILABLE or DELETING dictionaries.
     *
     * This odd method is tailored to the needs of
     * DictionaryProvider#getDictionaryWordListsForContentUri, which needs the word list if
     * it is:
     * - INSTALLED: this should be returned to LatinIME if the file is still inside the dictionary
     * pack, so that it can be copied. If the file is not there, it's been copied already and should
     * not be returned, so getDictionaryWordListsForContentUri takes care of this.
     * - DELETING: this should be returned to LatinIME so that it can actually delete the file.
     * - AVAILABLE: this should not be returned, but should be checked for auto-installation.
     *
     * @param context the context for getting the database.
     * @param clientId the client id for retrieving the database. null for default (deprecated)
     * @return a cursor with metadata about usable dictionaries.
     */
    public static Cursor queryInstalledOrDeletingOrAvailableDictionaryMetadata(
            final Context context, final String clientId) {
        // If clientId is null, we get the defaut DB (see #getInstance() for more about this)
        final Cursor results = getDb(context, clientId).query(METADATA_TABLE_NAME,
                METADATA_TABLE_COLUMNS,
                STATUS_COLUMN + " = ? OR " + STATUS_COLUMN + " = ? OR " + STATUS_COLUMN + " = ?",
                new String[] { Integer.toString(STATUS_INSTALLED),
                        Integer.toString(STATUS_DELETING),
                        Integer.toString(STATUS_AVAILABLE) },
                null, null, LOCALE_COLUMN);
        return results;
    }

    /**
     * Gets the current metadata about all dictionaries.
     *
     * This will retrieve the metadata about all dictionaries, including
     * older files, or files not yet downloaded.
     *
     * @param context the context for getting the database.
     * @param clientId the client id for retrieving the database. null for default (deprecated)
     * @return a cursor with metadata about usable dictionaries.
     */
    public static Cursor queryCurrentMetadata(final Context context, final String clientId) {
        // If clientId is null, we get the defaut DB (see #getInstance() for more about this)
        final Cursor results = getDb(context, clientId).query(METADATA_TABLE_NAME,
                METADATA_TABLE_COLUMNS, null, null, null, null, LOCALE_COLUMN);
        return results;
    }

    /**
     * Gets the list of all dictionaries known to the dictionary provider, with only public columns.
     *
     * This will retrieve information about all known dictionaries, and their status. As such,
     * it will also return information about dictionaries on the server that have not been
     * downloaded yet, but may be requested.
     * This only returns public columns. It does not populate internal columns in the returned
     * cursor.
     * The value returned by this method is intended to be good to be returned directly for a
     * request of the list of dictionaries by a client.
     *
     * @param context the context to read the database from.
     * @param clientId the client id for retrieving the database. null for default (deprecated)
     * @return a cursor that lists all available dictionaries and their metadata.
     */
    public static Cursor queryDictionaries(final Context context, final String clientId) {
        // If clientId is null, we get the defaut DB (see #getInstance() for more about this)
        final Cursor results = getDb(context, clientId).query(METADATA_TABLE_NAME,
                DICTIONARIES_LIST_PUBLIC_COLUMNS,
                // Filter out empty locales so as not to return auxiliary data, like a
                // data line for downloading metadata:
                MetadataDbHelper.LOCALE_COLUMN + " != ?", new String[] {""},
                // TODO: Reinstate the following code for bulk, then implement partial updates
                /*                MetadataDbHelper.TYPE_COLUMN + " = ?",
                new String[] { Integer.toString(MetadataDbHelper.TYPE_BULK) }, */
                null, null, LOCALE_COLUMN);
        return results;
    }

    /**
     * Deletes all data associated with a client.
     *
     * @param context the context for opening the database
     * @param clientId the ID of the client to delete.
     * @return true if the client was successfully deleted, false otherwise.
     */
    public static boolean deleteClient(final Context context, final String clientId) {
        // Remove all metadata associated with this client
        final SQLiteDatabase db = getDb(context, clientId);
        db.execSQL("DROP TABLE IF EXISTS " + METADATA_TABLE_NAME);
        db.execSQL(METADATA_TABLE_CREATE);
        // Remove this client's entry in the clients table
        final SQLiteDatabase defaultDb = getDb(context, "");
        if (0 == defaultDb.delete(CLIENT_TABLE_NAME,
                CLIENT_CLIENT_ID_COLUMN + " = ?", new String[] { clientId })) {
            return false;
        }
        return true;
    }

    /**
     * Updates information relative to a specific client.
     *
     * Updatable information includes the metadata URI and the additional ID column. It may be
     * expanded in the future.
     * The passed values must include a client ID in the key CLIENT_CLIENT_ID_COLUMN, and it must
     * be equal to the string passed as an argument for clientId. It may not be empty.
     * The passed values must also include a non-null metadata URI in the
     * CLIENT_METADATA_URI_COLUMN column, as well as a non-null additional ID in the
     * CLIENT_METADATA_ADDITIONAL_ID_COLUMN. Both these strings may be empty.
     * If any of the above is not complied with, this function returns without updating data.
     *
     * @param context the context, to open the database
     * @param clientId the ID of the client to update
     * @param values the values to update. Must conform to the protocol (see above)
     */
    public static void updateClientInfo(final Context context, final String clientId,
            final ContentValues values) {
        // Validity check the content values
        final String valuesClientId = values.getAsString(CLIENT_CLIENT_ID_COLUMN);
        final String valuesMetadataUri = values.getAsString(CLIENT_METADATA_URI_COLUMN);
        final String valuesMetadataAdditionalId =
                values.getAsString(CLIENT_METADATA_ADDITIONAL_ID_COLUMN);
        // Empty string is a valid client ID, but external apps may not configure it, so disallow
        // both null and empty string.
        // Empty string is a valid metadata URI if the client does not want updates, so allow
        // empty string but disallow null.
        // Empty string is a valid additional ID so allow empty string but disallow null.
        if (TextUtils.isEmpty(valuesClientId) || null == valuesMetadataUri
                || null == valuesMetadataAdditionalId) {
            // We need all these columns to be filled in
            DebugLogUtils.l("Missing parameter for updateClientInfo");
            return;
        }
        if (!clientId.equals(valuesClientId)) {
            // Mismatch! The client violates the protocol.
            DebugLogUtils.l("Received an updateClientInfo request for ", clientId,
                    " but the values " + "contain a different ID : ", valuesClientId);
            return;
        }
        // Default value for a pending ID is NOT_AN_ID
        values.put(CLIENT_PENDINGID_COLUMN, UpdateHandler.NOT_AN_ID);
        final SQLiteDatabase defaultDb = getDb(context, "");
        if (-1 == defaultDb.insert(CLIENT_TABLE_NAME, null, values)) {
            defaultDb.update(CLIENT_TABLE_NAME, values,
                    CLIENT_CLIENT_ID_COLUMN + " = ?", new String[] { clientId });
        }
    }

    /**
     * Retrieves the list of existing client IDs.
     * @param context the context to open the database
     * @return a cursor containing only one column, and one client ID per line.
     */
    public static Cursor queryClientIds(final Context context) {
        return getDb(context, null).query(CLIENT_TABLE_NAME,
                new String[] { CLIENT_CLIENT_ID_COLUMN }, null, null, null, null, null);
    }

    /**
     * Register a download ID for a specific metadata URI.
     *
     * This method should be called when a download for a metadata URI is starting. It will
     * search for all clients using this metadata URI and will register for each of them
     * the download ID into the database for later retrieval by
     * {@link #getDownloadRecordsForDownloadId(Context, long)}.
     *
     * @param context a context for opening databases
     * @param uri the metadata URI
     * @param downloadId the download ID
     */
    public static void registerMetadataDownloadId(final Context context, final String uri,
            final long downloadId) {
        final ContentValues values = new ContentValues();
        values.put(CLIENT_PENDINGID_COLUMN, downloadId);
        values.put(CLIENT_LAST_UPDATE_DATE_COLUMN, System.currentTimeMillis());
        final SQLiteDatabase defaultDb = getDb(context, "");
        final Cursor cursor = MetadataDbHelper.queryClientIds(context);
        if (null == cursor) return;
        try {
            if (!cursor.moveToFirst()) return;
            do {
                final String clientId = cursor.getString(0);
                final String metadataUri =
                        MetadataDbHelper.getMetadataUriAsString(context, clientId);
                if (metadataUri.equals(uri)) {
                    defaultDb.update(CLIENT_TABLE_NAME, values,
                            CLIENT_CLIENT_ID_COLUMN + " = ?", new String[] { clientId });
                }
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
    }

    /**
     * Marks a downloading entry as having successfully downloaded and being installed.
     *
     * The metadata database contains information about ongoing processes, typically ongoing
     * downloads. This marks such an entry as having finished and having installed successfully,
     * so it becomes INSTALLED.
     *
     * @param db the metadata database.
     * @param r content values about the entry to mark as processed.
     */
    public static void markEntryAsFinishedDownloadingAndInstalled(final SQLiteDatabase db,
            final ContentValues r) {
        switch (r.getAsInteger(TYPE_COLUMN)) {
            case TYPE_BULK:
                DebugLogUtils.l("Ended processing a wordlist");
                // Updating a bulk word list is a three-step operation:
                // - Add the new entry to the table
                // - Remove the old entry from the table
                // - Erase the old file
                // We start by gathering the names of the files we should delete.
                final List<String> filenames = new LinkedList<>();
                final Cursor c = db.query(METADATA_TABLE_NAME,
                        new String[] { LOCAL_FILENAME_COLUMN },
                        LOCALE_COLUMN + " = ? AND " +
                        WORDLISTID_COLUMN + " = ? AND " + STATUS_COLUMN + " = ?",
                        new String[] { r.getAsString(LOCALE_COLUMN),
                                r.getAsString(WORDLISTID_COLUMN),
                                Integer.toString(STATUS_INSTALLED) },
                        null, null, null);
                try {
                    if (c.moveToFirst()) {
                        // There should never be more than one file, but if there are, it's a bug
                        // and we should remove them all. I think it might happen if the power of
                        // the phone is suddenly cut during an update.
                        final int filenameIndex = c.getColumnIndex(LOCAL_FILENAME_COLUMN);
                        do {
                            DebugLogUtils.l("Setting for removal", c.getString(filenameIndex));
                            filenames.add(c.getString(filenameIndex));
                        } while (c.moveToNext());
                    }
                } finally {
                    c.close();
                }
                r.put(STATUS_COLUMN, STATUS_INSTALLED);
                db.beginTransactionNonExclusive();
                // Delete all old entries. There should never be any stalled entries, but if
                // there are, this deletes them.
                db.delete(METADATA_TABLE_NAME,
                        WORDLISTID_COLUMN + " = ?",
                        new String[] { r.getAsString(WORDLISTID_COLUMN) });
                db.insert(METADATA_TABLE_NAME, null, r);
                db.setTransactionSuccessful();
                db.endTransaction();
                for (String filename : filenames) {
                    try {
                        final File f = new File(filename);
                        f.delete();
                    } catch (SecurityException e) {
                        // No permissions to delete. Um. Can't do anything.
                    } // I don't think anything else can be thrown
                }
                break;
            default:
                // Unknown type: do nothing.
                break;
        }
     }

    /**
     * Removes a downloading entry from the database.
     *
     * This is invoked when a download fails. Either we tried to download, but
     * we received a permanent failure and we should remove it, or we got manually
     * cancelled and we should leave it at that.
     *
     * @param db the metadata database.
     * @param id the DownloadManager id of the file.
     */
    public static void deleteDownloadingEntry(final SQLiteDatabase db, final long id) {
        db.delete(METADATA_TABLE_NAME, PENDINGID_COLUMN + " = ? AND " + STATUS_COLUMN + " = ?",
                new String[] { Long.toString(id), Integer.toString(STATUS_DOWNLOADING) });
    }

    /**
     * Forcefully removes an entry from the database.
     *
     * This is invoked when a file is broken. The file has been downloaded, but Android
     * Keyboard is telling us it could not open it.
     *
     * @param db the metadata database.
     * @param id the id of the word list.
     * @param version the version of the word list.
     */
    public static void deleteEntry(final SQLiteDatabase db, final String id, final int version) {
        db.delete(METADATA_TABLE_NAME, WORDLISTID_COLUMN + " = ? AND " + VERSION_COLUMN + " = ?",
                new String[] { id, Integer.toString(version) });
    }

    /**
     * Internal method that sets the current status of an entry of the database.
     *
     * @param db the metadata database.
     * @param id the id of the word list.
     * @param version the version of the word list.
     * @param status the status to set the word list to.
     * @param downloadId an optional download id to write, or NOT_A_DOWNLOAD_ID
     */
    private static void markEntryAs(final SQLiteDatabase db, final String id,
            final int version, final int status, final long downloadId) {
        final ContentValues values = MetadataDbHelper.getContentValuesByWordListId(db, id, version);
        values.put(STATUS_COLUMN, status);
        if (NOT_A_DOWNLOAD_ID != downloadId) {
            values.put(MetadataDbHelper.PENDINGID_COLUMN, downloadId);
        }
        db.update(METADATA_TABLE_NAME, values,
                WORDLISTID_COLUMN + " = ? AND " + VERSION_COLUMN + " = ?",
                new String[] { id, Integer.toString(version) });
    }

    /**
     * Writes the status column for the wordlist with this id as enabled. Typically this
     * means the word list is currently disabled and we want to set its status to INSTALLED.
     *
     * @param db the metadata database.
     * @param id the id of the word list.
     * @param version the version of the word list.
     */
    public static void markEntryAsEnabled(final SQLiteDatabase db, final String id,
            final int version) {
        markEntryAs(db, id, version, STATUS_INSTALLED, NOT_A_DOWNLOAD_ID);
    }

    /**
     * Writes the status column for the wordlist with this id as disabled. Typically this
     * means the word list is currently installed and we want to set its status to DISABLED.
     *
     * @param db the metadata database.
     * @param id the id of the word list.
     * @param version the version of the word list.
     */
    public static void markEntryAsDisabled(final SQLiteDatabase db, final String id,
            final int version) {
        markEntryAs(db, id, version, STATUS_DISABLED, NOT_A_DOWNLOAD_ID);
    }

    /**
     * Writes the status column for the wordlist with this id as available. This happens for
     * example when a word list has been deleted but can be downloaded again.
     *
     * @param db the metadata database.
     * @param id the id of the word list.
     * @param version the version of the word list.
     */
    public static void markEntryAsAvailable(final SQLiteDatabase db, final String id,
            final int version) {
        markEntryAs(db, id, version, STATUS_AVAILABLE, NOT_A_DOWNLOAD_ID);
    }

    /**
     * Writes the designated word list as downloadable, alongside with its download id.
     *
     * @param db the metadata database.
     * @param id the id of the word list.
     * @param version the version of the word list.
     * @param downloadId the download id.
     */
    public static void markEntryAsDownloading(final SQLiteDatabase db, final String id,
            final int version, final long downloadId) {
        markEntryAs(db, id, version, STATUS_DOWNLOADING, downloadId);
    }

    /**
     * Writes the designated word list as deleting.
     *
     * @param db the metadata database.
     * @param id the id of the word list.
     * @param version the version of the word list.
     */
    public static void markEntryAsDeleting(final SQLiteDatabase db, final String id,
            final int version) {
        markEntryAs(db, id, version, STATUS_DELETING, NOT_A_DOWNLOAD_ID);
    }

    /**
     * Checks retry counts and marks the word list as retrying if retry is possible.
     *
     * @param db the metadata database.
     * @param id the id of the word list.
     * @param version the version of the word list.
     * @return {@code true} if the retry is possible.
     */
    public static boolean maybeMarkEntryAsRetrying(final SQLiteDatabase db, final String id,
            final int version) {
        final ContentValues values = MetadataDbHelper.getContentValuesByWordListId(db, id, version);
        int retryCount = values.getAsInteger(MetadataDbHelper.RETRY_COUNT_COLUMN);
        if (retryCount > 1) {
            values.put(STATUS_COLUMN, STATUS_RETRYING);
            values.put(RETRY_COUNT_COLUMN, retryCount - 1);
            db.update(METADATA_TABLE_NAME, values,
                    WORDLISTID_COLUMN + " = ? AND " + VERSION_COLUMN + " = ?",
                    new String[] { id, Integer.toString(version) });
            return true;
        }
        return false;
    }
}
