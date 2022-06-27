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

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.DebugLogUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

/**
 * Handler for the update process.
 *
 * This class is in charge of coordinating the update process for the various dictionaries
 * stored in the dictionary pack.
 */
public final class UpdateHandler {
    static final String TAG = "DictionaryProvider:" + UpdateHandler.class.getSimpleName();
    private static final boolean DEBUG = DictionaryProvider.DEBUG;

    // Used to prevent trying to read the id of the downloaded file before it is written
    static final Object sSharedIdProtector = new Object();

    // Value used to mean this is not a real DownloadManager downloaded file id
    // DownloadManager uses as an ID numbers returned out of an AUTOINCREMENT column
    // in SQLite, so it should never return anything < 0.
    public static final int NOT_AN_ID = -1;
    public static final int MAXIMUM_SUPPORTED_FORMAT_VERSION =
            FormatSpec.MAXIMUM_SUPPORTED_STATIC_VERSION;

    // Arbitrary. Probably good if it's a power of 2, and a couple thousand bytes long.
    private static final int FILE_COPY_BUFFER_SIZE = 8192;

    // Table fixed values for metadata / downloads
    final static String METADATA_NAME = "metadata";
    final static int METADATA_TYPE = 0;
    final static int WORDLIST_TYPE = 1;

    // Suffix for generated dictionary files
    private static final String DICT_FILE_SUFFIX = ".dict";
    // Name of the category for the main dictionary
    public static final String MAIN_DICTIONARY_CATEGORY = "main";

    public static final String TEMP_DICT_FILE_SUB = "___";

    // The id for the "dictionary available" notification.
    static final int DICT_AVAILABLE_NOTIFICATION_ID = 1;

    /**
     * An interface for UIs or services that want to know when something happened.
     *
     * This is chiefly used by the dictionary manager UI.
     */
    public interface UpdateEventListener {
        void downloadedMetadata(boolean succeeded);
        void wordListDownloadFinished(String wordListId, boolean succeeded);
        void updateCycleCompleted();
    }

    /**
     * The list of currently registered listeners.
     */
    private static List<UpdateEventListener> sUpdateEventListeners
            = Collections.synchronizedList(new LinkedList<UpdateEventListener>());

    /**
     * Register a new listener to be notified of updates.
     *
     * Don't forget to call unregisterUpdateEventListener when done with it, or
     * it will leak the register.
     */
    public static void registerUpdateEventListener(final UpdateEventListener listener) {
        sUpdateEventListeners.add(listener);
    }

    /**
     * Unregister a previously registered listener.
     */
    public static void unregisterUpdateEventListener(final UpdateEventListener listener) {
        sUpdateEventListeners.remove(listener);
    }

    private static final String DOWNLOAD_OVER_METERED_SETTING_PREFS_KEY = "downloadOverMetered";

    /**
     * Write the DownloadManager ID of the currently downloading metadata to permanent storage.
     *
     * @param context to open shared prefs
     * @param uri the uri of the metadata
     * @param downloadId the id returned by DownloadManager
     */
    private static void writeMetadataDownloadId(final Context context, final String uri,
            final long downloadId) {
        MetadataDbHelper.registerMetadataDownloadId(context, uri, downloadId);
    }

    public static final int DOWNLOAD_OVER_METERED_SETTING_UNKNOWN = 0;
    public static final int DOWNLOAD_OVER_METERED_ALLOWED = 1;
    public static final int DOWNLOAD_OVER_METERED_DISALLOWED = 2;

    /**
     * Sets the setting that tells us whether we may download over a metered connection.
     */
    public static void setDownloadOverMeteredSetting(final Context context,
            final boolean shouldDownloadOverMetered) {
        final SharedPreferences prefs = CommonPreferences.getCommonPreferences(context);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(DOWNLOAD_OVER_METERED_SETTING_PREFS_KEY, shouldDownloadOverMetered
                ? DOWNLOAD_OVER_METERED_ALLOWED : DOWNLOAD_OVER_METERED_DISALLOWED);
        editor.apply();
    }

    /**
     * Gets the setting that tells us whether we may download over a metered connection.
     *
     * This returns one of the constants above.
     */
    public static int getDownloadOverMeteredSetting(final Context context) {
        final SharedPreferences prefs = CommonPreferences.getCommonPreferences(context);
        final int setting = prefs.getInt(DOWNLOAD_OVER_METERED_SETTING_PREFS_KEY,
                DOWNLOAD_OVER_METERED_SETTING_UNKNOWN);
        return setting;
    }

    /**
     * Download latest metadata from the server through DownloadManager for all known clients
     * @param context The context for retrieving resources
     * @return true if an update successfully started, false otherwise.
     */
    public static boolean tryUpdate(final Context context) {
        // TODO: loop through all clients instead of only doing the default one.
        final TreeSet<String> uris = new TreeSet<>();
        final Cursor cursor = MetadataDbHelper.queryClientIds(context);
        if (null == cursor) return false;
        try {
            if (!cursor.moveToFirst()) return false;
            do {
                final String clientId = cursor.getString(0);
                final String metadataUri =
                        MetadataDbHelper.getMetadataUriAsString(context, clientId);
                PrivateLog.log("Update for clientId " + DebugLogUtils.s(clientId));
                DebugLogUtils.l("Update for clientId", clientId, " which uses URI ", metadataUri);
                uris.add(metadataUri);
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
        boolean started = false;
        for (final String metadataUri : uris) {
            if (!TextUtils.isEmpty(metadataUri)) {
                // If the metadata URI is empty, that means we should never update it at all.
                // It should not be possible to come here with a null metadata URI, because
                // it should have been rejected at the time of client registration; if there
                // is a bug and it happens anyway, doing nothing is the right thing to do.
                // For more information, {@see DictionaryProvider#insert(Uri, ContentValues)}.
                updateClientsWithMetadataUri(context, metadataUri);
                started = true;
            }
        }
        return started;
    }

    /**
     * Download latest metadata from the server through DownloadManager for all relevant clients
     *
     * @param context The context for retrieving resources
     * @param metadataUri The client to update
     */
    private static void updateClientsWithMetadataUri(
            final Context context, final String metadataUri) {
        Log.i(TAG, "updateClientsWithMetadataUri() : MetadataUri = " + metadataUri);
        // Adding a disambiguator to circumvent a bug in older versions of DownloadManager.
        // DownloadManager also stupidly cuts the extension to replace with its own that it
        // gets from the content-type. We need to circumvent this.
        final String disambiguator = "#" + System.currentTimeMillis()
                + ApplicationUtils.getVersionName(context) + ".json";
        final Request metadataRequest = new Request(Uri.parse(metadataUri + disambiguator));
        DebugLogUtils.l("Request =", metadataRequest);

        final Resources res = context.getResources();
        metadataRequest.setAllowedNetworkTypes(Request.NETWORK_WIFI | Request.NETWORK_MOBILE);
        metadataRequest.setTitle(res.getString(R.string.download_description));
        // Do not show the notification when downloading the metadata.
        metadataRequest.setNotificationVisibility(Request.VISIBILITY_HIDDEN);
        metadataRequest.setVisibleInDownloadsUi(
                res.getBoolean(R.bool.metadata_downloads_visible_in_download_UI));

        final DownloadManagerWrapper manager = new DownloadManagerWrapper(context);
        if (maybeCancelUpdateAndReturnIfStillRunning(context, metadataUri, manager,
                DictionaryService.NO_CANCEL_DOWNLOAD_PERIOD_MILLIS)) {
            // We already have a recent download in progress. Don't register a new download.
            return;
        }
        final long downloadId;
        synchronized (sSharedIdProtector) {
            downloadId = manager.enqueue(metadataRequest);
            DebugLogUtils.l("Metadata download requested with id", downloadId);
            // If there is still a download in progress, it's been there for a while and
            // there is probably something wrong with download manager. It's best to just
            // overwrite the id and request it again. If the old one happens to finish
            // anyway, we don't know about its ID any more, so the downloadFinished
            // method will ignore it.
            writeMetadataDownloadId(context, metadataUri, downloadId);
        }
        Log.i(TAG, "updateClientsWithMetadataUri() : DownloadId = " + downloadId);
    }

    /**
     * Cancels downloading a file if there is one for this URI and it's too long.
     *
     * If we are not currently downloading the file at this URI, this is a no-op.
     *
     * @param context the context to open the database on
     * @param metadataUri the URI to cancel
     * @param manager an wrapped instance of DownloadManager
     * @param graceTime if there was a download started less than this many milliseconds, don't
     *  cancel and return true
     * @return whether the download is still active
     */
    private static boolean maybeCancelUpdateAndReturnIfStillRunning(final Context context,
            final String metadataUri, final DownloadManagerWrapper manager, final long graceTime) {
        synchronized (sSharedIdProtector) {
            final DownloadIdAndStartDate metadataDownloadIdAndStartDate =
                    MetadataDbHelper.getMetadataDownloadIdAndStartDateForURI(context, metadataUri);
            if (null == metadataDownloadIdAndStartDate) return false;
            if (NOT_AN_ID == metadataDownloadIdAndStartDate.mId) return false;
            if (metadataDownloadIdAndStartDate.mStartDate + graceTime
                    > System.currentTimeMillis()) {
                return true;
            }
            manager.remove(metadataDownloadIdAndStartDate.mId);
            writeMetadataDownloadId(context, metadataUri, NOT_AN_ID);
        }
        // Consider a cancellation as a failure. As such, inform listeners that the download
        // has failed.
        for (UpdateEventListener listener : linkedCopyOfList(sUpdateEventListeners)) {
            listener.downloadedMetadata(false);
        }
        return false;
    }

    /**
     * Cancels a pending update for this client, if there is one.
     *
     * If we are not currently updating metadata for this client, this is a no-op. This is a helper
     * method that gets the download manager service and the metadata URI for this client.
     *
     * @param context the context, to get an instance of DownloadManager
     * @param clientId the ID of the client we want to cancel the update of
     */
    public static void cancelUpdate(final Context context, final String clientId) {
        final DownloadManagerWrapper manager = new DownloadManagerWrapper(context);
        final String metadataUri = MetadataDbHelper.getMetadataUriAsString(context, clientId);
        maybeCancelUpdateAndReturnIfStillRunning(context, metadataUri, manager, 0 /* graceTime */);
    }

    /**
     * Registers a download request and flags it as downloading in the metadata table.
     *
     * This is a helper method that exists to avoid race conditions where DownloadManager might
     * finish downloading the file before the data is committed to the database.
     * It registers the request with the DownloadManager service and also updates the metadata
     * database directly within a synchronized section.
     * This method has no intelligence about the data it commits to the database aside from the
     * download request id, which is not known before submitting the request to the download
     * manager. Hence, it only updates the relevant line.
     *
     * @param manager a wrapped download manager service to register the request with.
     * @param request the request to register.
     * @param db the metadata database.
     * @param id the id of the word list.
     * @param version the version of the word list.
     * @return the download id returned by the download manager.
     */
    public static long registerDownloadRequest(final DownloadManagerWrapper manager,
            final Request request, final SQLiteDatabase db, final String id, final int version) {
        Log.i(TAG, "registerDownloadRequest() : Id = " + id + " : Version = " + version);
        final long downloadId;
        synchronized (sSharedIdProtector) {
            downloadId = manager.enqueue(request);
            Log.i(TAG, "registerDownloadRequest() : DownloadId = " + downloadId);
            MetadataDbHelper.markEntryAsDownloading(db, id, version, downloadId);
        }
        return downloadId;
    }

    /**
     * Retrieve information about a specific download from DownloadManager.
     */
    private static CompletedDownloadInfo getCompletedDownloadInfo(
            final DownloadManagerWrapper manager, final long downloadId) {
        final Query query = new Query().setFilterById(downloadId);
        final Cursor cursor = manager.query(query);

        if (null == cursor) {
            return new CompletedDownloadInfo(null, downloadId, DownloadManager.STATUS_FAILED);
        }
        try {
            final String uri;
            final int status;
            if (cursor.moveToNext()) {
                final int columnStatus = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                final int columnError = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                final int columnUri = cursor.getColumnIndex(DownloadManager.COLUMN_URI);
                final int error = cursor.getInt(columnError);
                status = cursor.getInt(columnStatus);
                final String uriWithAnchor = cursor.getString(columnUri);
                int anchorIndex = uriWithAnchor.indexOf('#');
                if (anchorIndex != -1) {
                    uri = uriWithAnchor.substring(0, anchorIndex);
                } else {
                    uri = uriWithAnchor;
                }
                if (DownloadManager.STATUS_SUCCESSFUL != status) {
                    Log.e(TAG, "Permanent failure of download " + downloadId
                            + " with error code: " + error);
                }
            } else {
                uri = null;
                status = DownloadManager.STATUS_FAILED;
            }
            return new CompletedDownloadInfo(uri, downloadId, status);
        } finally {
            cursor.close();
        }
    }

    private static ArrayList<DownloadRecord> getDownloadRecordsForCompletedDownloadInfo(
            final Context context, final CompletedDownloadInfo downloadInfo) {
        // Get and check the ID of the file we are waiting for, compare them to downloaded ones
        synchronized(sSharedIdProtector) {
            final ArrayList<DownloadRecord> downloadRecords =
                    MetadataDbHelper.getDownloadRecordsForDownloadId(context,
                            downloadInfo.mDownloadId);
            // If any of these is metadata, we should update the DB
            boolean hasMetadata = false;
            for (DownloadRecord record : downloadRecords) {
                if (record.isMetadata()) {
                    hasMetadata = true;
                    break;
                }
            }
            if (hasMetadata) {
                writeMetadataDownloadId(context, downloadInfo.mUri, NOT_AN_ID);
                MetadataDbHelper.saveLastUpdateTimeOfUri(context, downloadInfo.mUri);
            }
            return downloadRecords;
        }
    }

    /**
     * Take appropriate action after a download finished, in success or in error.
     *
     * This is called by the system upon broadcast from the DownloadManager that a file
     * has been downloaded successfully.
     * After a simple check that this is actually the file we are waiting for, this
     * method basically coordinates the parsing and comparison of metadata, and fires
     * the computation of the list of actions that should be taken then executes them.
     *
     * @param context The context for this action.
     * @param intent The intent from the DownloadManager containing details about the download.
     */
    /* package */ static void downloadFinished(final Context context, final Intent intent) {
        // Get and check the ID of the file that was downloaded
        final long fileId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, NOT_AN_ID);
        Log.i(TAG, "downloadFinished() : DownloadId = " + fileId);
        if (NOT_AN_ID == fileId) return; // Spurious wake-up: ignore

        final DownloadManagerWrapper manager = new DownloadManagerWrapper(context);
        final CompletedDownloadInfo downloadInfo = getCompletedDownloadInfo(manager, fileId);

        final ArrayList<DownloadRecord> recordList =
                getDownloadRecordsForCompletedDownloadInfo(context, downloadInfo);
        if (null == recordList) return; // It was someone else's download.
        DebugLogUtils.l("Received result for download ", fileId);

        // TODO: handle gracefully a null pointer here. This is practically impossible because
        // we come here only when DownloadManager explicitly called us when it ended a
        // download, so we are pretty sure it's alive. It's theoretically possible that it's
        // disabled right inbetween the firing of the intent and the control reaching here.

        for (final DownloadRecord record : recordList) {
            // downloadSuccessful is not final because we may still have exceptions from now on
            boolean downloadSuccessful = false;
            try {
                if (downloadInfo.wasSuccessful()) {
                    downloadSuccessful = handleDownloadedFile(context, record, manager, fileId);
                    Log.i(TAG, "downloadFinished() : Success = " + downloadSuccessful);
                }
            } finally {
                final String resultMessage = downloadSuccessful ? "Success" : "Failure";
                if (record.isMetadata()) {
                    Log.i(TAG, "downloadFinished() : Metadata " + resultMessage);
                    publishUpdateMetadataCompleted(context, downloadSuccessful);
                } else {
                    Log.i(TAG, "downloadFinished() : WordList " + resultMessage);
                    final SQLiteDatabase db = MetadataDbHelper.getDb(context, record.mClientId);
                    publishUpdateWordListCompleted(context, downloadSuccessful, fileId,
                            db, record.mAttributes, record.mClientId);
                }
            }
        }
        // Now that we're done using it, we can remove this download from DLManager
        manager.remove(fileId);
    }

    /**
     * Sends a broadcast informing listeners that the dictionaries were updated.
     *
     * This will call all local listeners through the UpdateEventListener#downloadedMetadata
     * callback (for example, the dictionary provider interface uses this to stop the Loading
     * animation) and send a broadcast about the metadata having been updated. For a client of
     * the dictionary pack like Latin IME, this means it should re-query the dictionary pack
     * for any relevant new data.
     *
     * @param context the context, to send the broadcast.
     * @param downloadSuccessful whether the download of the metadata was successful or not.
     */
    public static void publishUpdateMetadataCompleted(final Context context,
            final boolean downloadSuccessful) {
        // We need to warn all listeners of what happened. But some listeners may want to
        // remove themselves or re-register something in response. Hence we should take a
        // snapshot of the listener list and warn them all. This also prevents any
        // concurrent modification problem of the static list.
        for (UpdateEventListener listener : linkedCopyOfList(sUpdateEventListeners)) {
            listener.downloadedMetadata(downloadSuccessful);
        }
        publishUpdateCycleCompletedEvent(context);
    }

    private static void publishUpdateWordListCompleted(final Context context,
            final boolean downloadSuccessful, final long fileId,
            final SQLiteDatabase db, final ContentValues downloadedFileRecord,
            final String clientId) {
        synchronized(sSharedIdProtector) {
            if (downloadSuccessful) {
                final ActionBatch actions = new ActionBatch();
                actions.add(new ActionBatch.InstallAfterDownloadAction(clientId,
                        downloadedFileRecord));
                actions.execute(context, new LogProblemReporter(TAG));
            } else {
                MetadataDbHelper.deleteDownloadingEntry(db, fileId);
            }
        }
        // See comment above about #linkedCopyOfLists
        for (UpdateEventListener listener : linkedCopyOfList(sUpdateEventListeners)) {
            listener.wordListDownloadFinished(downloadedFileRecord.getAsString(
                            MetadataDbHelper.WORDLISTID_COLUMN), downloadSuccessful);
        }
        publishUpdateCycleCompletedEvent(context);
    }

    private static void publishUpdateCycleCompletedEvent(final Context context) {
        // Even if this is not successful, we have to publish the new state.
        PrivateLog.log("Publishing update cycle completed event");
        DebugLogUtils.l("Publishing update cycle completed event");
        for (UpdateEventListener listener : linkedCopyOfList(sUpdateEventListeners)) {
            listener.updateCycleCompleted();
        }
        signalNewDictionaryState(context);
    }

    private static boolean handleDownloadedFile(final Context context,
            final DownloadRecord downloadRecord, final DownloadManagerWrapper manager,
            final long fileId) {
        try {
            // {@link handleWordList(Context,InputStream,ContentValues)}.
            // Handle the downloaded file according to its type
            if (downloadRecord.isMetadata()) {
                DebugLogUtils.l("Data D/L'd is metadata for", downloadRecord.mClientId);
                // #handleMetadata() closes its InputStream argument
                handleMetadata(context, new ParcelFileDescriptor.AutoCloseInputStream(
                        manager.openDownloadedFile(fileId)), downloadRecord.mClientId);
            } else {
                DebugLogUtils.l("Data D/L'd is a word list");
                final int wordListStatus = downloadRecord.mAttributes.getAsInteger(
                        MetadataDbHelper.STATUS_COLUMN);
                if (MetadataDbHelper.STATUS_DOWNLOADING == wordListStatus) {
                    // #handleWordList() closes its InputStream argument
                    handleWordList(context, new ParcelFileDescriptor.AutoCloseInputStream(
                            manager.openDownloadedFile(fileId)), downloadRecord);
                } else {
                    Log.e(TAG, "Spurious download ended. Maybe a cancelled download?");
                }
            }
            return true;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "A file was downloaded but it can't be opened", e);
        } catch (IOException e) {
            // Can't read the file... disk damage?
            Log.e(TAG, "Can't read a file", e);
            // TODO: Check with UX how we should warn the user.
        } catch (IllegalStateException e) {
            // The format of the downloaded file is incorrect. We should maybe report upstream?
            Log.e(TAG, "Incorrect data received", e);
        } catch (BadFormatException e) {
            // The format of the downloaded file is incorrect. We should maybe report upstream?
            Log.e(TAG, "Incorrect data received", e);
        }
        return false;
    }

    /**
     * Returns a copy of the specified list, with all elements copied.
     *
     * This returns a linked list.
     */
    private static <T> List<T> linkedCopyOfList(final List<T> src) {
        // Instantiation of a parameterized type is not possible in Java, so it's not possible to
        // return the same type of list that was passed - probably the same reason why Collections
        // does not do it. So we need to decide statically which concrete type to return.
        return new LinkedList<>(src);
    }

    /**
     * Warn Android Keyboard that the state of dictionaries changed and it should refresh its data.
     */
    private static void signalNewDictionaryState(final Context context) {
        // TODO: Also provide the locale of the updated dictionary so that the LatinIme
        // does not have to reset if it is a different locale.
        final Intent newDictBroadcast =
                new Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION);
        context.sendBroadcast(newDictBroadcast);
    }

    /**
     * Parse metadata and take appropriate action (that is, upgrade dictionaries).
     * @param context the context to read settings.
     * @param stream an input stream pointing to the downloaded data. May not be null.
     *  Will be closed upon finishing.
     * @param clientId the ID of the client to update
     * @throws BadFormatException if the metadata is not in a known format.
     * @throws IOException if the downloaded file can't be read from the disk
     */
    public static void handleMetadata(final Context context, final InputStream stream,
            final String clientId) throws IOException, BadFormatException {
        DebugLogUtils.l("Entering handleMetadata");
        final List<WordListMetadata> newMetadata;
        final InputStreamReader reader = new InputStreamReader(stream);
        try {
            // According to the doc InputStreamReader buffers, so no need to add a buffering layer
            newMetadata = MetadataHandler.readMetadata(reader);
        } finally {
            reader.close();
        }

        DebugLogUtils.l("Downloaded metadata :", newMetadata);
        PrivateLog.log("Downloaded metadata\n" + newMetadata);

        final ActionBatch actions = computeUpgradeTo(context, clientId, newMetadata);
        // TODO: Check with UX how we should report to the user
        // TODO: add an action to close the database
        actions.execute(context, new LogProblemReporter(TAG));
    }

    /**
     * Handle a word list: put it in its right place, and update the passed content values.
     * @param context the context for opening files.
     * @param inputStream an input stream pointing to the downloaded data. May not be null.
     *  Will be closed upon finishing.
     * @param downloadRecord the content values to fill the file name in.
     * @throws IOException if files can't be read or written.
     * @throws BadFormatException if the md5 checksum doesn't match the metadata.
     */
    private static void handleWordList(final Context context,
            final InputStream inputStream, final DownloadRecord downloadRecord)
            throws IOException, BadFormatException {

        // DownloadManager does not have the ability to put the file directly where we want
        // it, so we had it download to a temporary place. Now we move it. It will be deleted
        // automatically by DownloadManager.
        DebugLogUtils.l("Downloaded a new word list :", downloadRecord.mAttributes.getAsString(
                MetadataDbHelper.DESCRIPTION_COLUMN), "for", downloadRecord.mClientId);
        PrivateLog.log("Downloaded a new word list with description : "
                + downloadRecord.mAttributes.getAsString(MetadataDbHelper.DESCRIPTION_COLUMN)
                + " for " + downloadRecord.mClientId);

        final String locale =
                downloadRecord.mAttributes.getAsString(MetadataDbHelper.LOCALE_COLUMN);
        final String destinationFile = getTempFileName(context, locale);
        downloadRecord.mAttributes.put(MetadataDbHelper.LOCAL_FILENAME_COLUMN, destinationFile);

        FileOutputStream outputStream = null;
        try {
            outputStream = context.openFileOutput(destinationFile, Context.MODE_PRIVATE);
            copyFile(inputStream, outputStream);
        } finally {
            inputStream.close();
            if (outputStream != null) {
                outputStream.close();
            }
        }

        // TODO: Consolidate this MD5 calculation with file copying above.
        // We need to reopen the file because the inputstream bytes have been consumed, and there
        // is nothing in InputStream to reopen or rewind the stream
        FileInputStream copiedFile = null;
        final String md5sum;
        try {
            copiedFile = context.openFileInput(destinationFile);
            md5sum = MD5Calculator.checksum(copiedFile);
        } finally {
            if (copiedFile != null) {
                copiedFile.close();
            }
        }
        if (TextUtils.isEmpty(md5sum)) {
            return; // We can't compute the checksum anyway, so return and hope for the best
        }
        if (!md5sum.equals(downloadRecord.mAttributes.getAsString(
                MetadataDbHelper.CHECKSUM_COLUMN))) {
            context.deleteFile(destinationFile);
            throw new BadFormatException("MD5 checksum check failed : \"" + md5sum + "\" <> \""
                    + downloadRecord.mAttributes.getAsString(MetadataDbHelper.CHECKSUM_COLUMN)
                    + "\"");
        }
    }

    /**
     * Copies in to out using FileChannels.
     *
     * This tries to use channels for fast copying. If it doesn't work, fall back to
     * copyFileFallBack below.
     *
     * @param in the stream to copy from.
     * @param out the stream to copy to.
     * @throws IOException if both the normal and fallback methods raise exceptions.
     */
    private static void copyFile(final InputStream in, final OutputStream out)
            throws IOException {
        DebugLogUtils.l("Copying files");
        if (!(in instanceof FileInputStream) || !(out instanceof FileOutputStream)) {
            DebugLogUtils.l("Not the right types");
            copyFileFallback(in, out);
        } else {
            try {
                final FileChannel sourceChannel = ((FileInputStream) in).getChannel();
                final FileChannel destinationChannel = ((FileOutputStream) out).getChannel();
                sourceChannel.transferTo(0, Integer.MAX_VALUE, destinationChannel);
            } catch (IOException e) {
                // Can't work with channels, or something went wrong. Copy by hand.
                DebugLogUtils.l("Won't work");
                copyFileFallback(in, out);
            }
        }
    }

    /**
     * Copies in to out with read/write methods, not FileChannels.
     *
     * @param in the stream to copy from.
     * @param out the stream to copy to.
     * @throws IOException if a read or a write fails.
     */
    private static void copyFileFallback(final InputStream in, final OutputStream out)
            throws IOException {
        DebugLogUtils.l("Falling back to slow copy");
        final byte[] buffer = new byte[FILE_COPY_BUFFER_SIZE];
        for (int readBytes = in.read(buffer); readBytes >= 0; readBytes = in.read(buffer))
            out.write(buffer, 0, readBytes);
    }

    /**
     * Creates and returns a new file to store a dictionary
     * @param context the context to use to open the file.
     * @param locale the locale for this dictionary, to make the file name more readable.
     * @return the file name, or throw an exception.
     * @throws IOException if the file cannot be created.
     */
    private static String getTempFileName(final Context context, final String locale)
            throws IOException {
        DebugLogUtils.l("Entering openTempFileOutput");
        final File dir = context.getFilesDir();
        final File f = File.createTempFile(locale + TEMP_DICT_FILE_SUB, DICT_FILE_SUFFIX, dir);
        DebugLogUtils.l("File name is", f.getName());
        return f.getName();
    }

    /**
     * Compare metadata (collections of word lists).
     *
     * This method takes whole metadata sets directly and compares them, matching the wordlists in
     * each of them on the id. It creates an ActionBatch object that can be .execute()'d to perform
     * the actual upgrade from `from' to `to'.
     *
     * @param context the context to open databases on.
     * @param clientId the id of the client.
     * @param from the dictionary descriptor (as a list of wordlists) to upgrade from.
     * @param to the dictionary descriptor (as a list of wordlists) to upgrade to.
     * @return an ordered list of runnables to be called to upgrade.
     */
    private static ActionBatch compareMetadataForUpgrade(final Context context,
            final String clientId, @Nullable final List<WordListMetadata> from,
            @Nullable final List<WordListMetadata> to) {
        final ActionBatch actions = new ActionBatch();
        // Upgrade existing word lists
        DebugLogUtils.l("Comparing dictionaries");
        final Set<String> wordListIds = new TreeSet<>();
        // TODO: Can these be null?
        final List<WordListMetadata> fromList = (from == null) ? new ArrayList<WordListMetadata>()
                : from;
        final List<WordListMetadata> toList = (to == null) ? new ArrayList<WordListMetadata>()
                : to;
        for (WordListMetadata wlData : fromList) wordListIds.add(wlData.mId);
        for (WordListMetadata wlData : toList) wordListIds.add(wlData.mId);
        for (String id : wordListIds) {
            final WordListMetadata currentInfo = MetadataHandler.findWordListById(fromList, id);
            final WordListMetadata metadataInfo = MetadataHandler.findWordListById(toList, id);
            // TODO: Remove the following unnecessary check, since we are now doing the filtering
            // inside findWordListById.
            final WordListMetadata newInfo = null == metadataInfo
                    || metadataInfo.mFormatVersion > MAXIMUM_SUPPORTED_FORMAT_VERSION
                            ? null : metadataInfo;
            DebugLogUtils.l("Considering updating ", id, "currentInfo =", currentInfo);

            if (null == currentInfo && null == newInfo) {
                // This may happen if a new word list appeared that we can't handle.
                if (null == metadataInfo) {
                    // What happened? Bug in Set<>?
                    Log.e(TAG, "Got an id for a wordlist that is neither in from nor in to");
                } else {
                    // We may come here if there is a new word list that we can't handle.
                    Log.i(TAG, "Can't handle word list with id '" + id + "' because it has format"
                            + " version " + metadataInfo.mFormatVersion + " and the maximum version"
                            + " we can handle is " + MAXIMUM_SUPPORTED_FORMAT_VERSION);
                }
                continue;
            } else if (null == currentInfo) {
                // This is the case where a new list that we did not know of popped on the server.
                // Make it available.
                actions.add(new ActionBatch.MakeAvailableAction(clientId, newInfo));
            } else if (null == newInfo) {
                // This is the case where an old list we had is not in the server data any more.
                // Pass false to ForgetAction: this may be installed and we still want to apply
                // a forget-like action (remove the URL) if it is, so we want to turn off the
                // status == AVAILABLE check. If it's DELETING, this is the right thing to do,
                // as we want to leave the record as long as Android Keyboard has not deleted it ;
                // the record will be removed when the file is actually deleted.
                actions.add(new ActionBatch.ForgetAction(clientId, currentInfo, false));
            } else {
                final SQLiteDatabase db = MetadataDbHelper.getDb(context, clientId);
                if (newInfo.mVersion == currentInfo.mVersion) {
                    if (TextUtils.equals(newInfo.mRemoteFilename, currentInfo.mRemoteFilename)) {
                        // If the dictionary url hasn't changed, we should preserve the retryCount.
                        newInfo.mRetryCount = currentInfo.mRetryCount;
                    }
                    // If it's the same id/version, we update the DB with the new values.
                    // It doesn't matter too much if they didn't change.
                    actions.add(new ActionBatch.UpdateDataAction(clientId, newInfo));
                } else if (newInfo.mVersion > currentInfo.mVersion) {
                    // If it's a new version, it's a different entry in the database. Make it
                    // available, and if it's installed, also start the download.
                    final ContentValues values = MetadataDbHelper.getContentValuesByWordListId(db,
                            currentInfo.mId, currentInfo.mVersion);
                    final int status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
                    actions.add(new ActionBatch.MakeAvailableAction(clientId, newInfo));
                    if (status == MetadataDbHelper.STATUS_INSTALLED
                            || status == MetadataDbHelper.STATUS_DISABLED) {
                        actions.add(new ActionBatch.StartDownloadAction(clientId, newInfo));
                    } else {
                        // Pass true to ForgetAction: this is indeed an update to a non-installed
                        // word list, so activate status == AVAILABLE check
                        // In case the status is DELETING, this is the right thing to do. It will
                        // leave the entry as DELETING and remove its URL so that Android Keyboard
                        // can delete it the next time it starts up.
                        actions.add(new ActionBatch.ForgetAction(clientId, currentInfo, true));
                    }
                } else if (DEBUG) {
                    Log.i(TAG, "Not updating word list " + id
                            + " : current list timestamp is " + currentInfo.mLastUpdate
                                    + " ; new list timestamp is " + newInfo.mLastUpdate);
                }
            }
        }
        return actions;
    }

    /**
     * Computes an upgrade from the current state of the dictionaries to some desired state.
     * @param context the context for reading settings and files.
     * @param clientId the id of the client.
     * @param newMetadata the state we want to upgrade to.
     * @return the upgrade from the current state to the desired state, ready to be executed.
     */
    public static ActionBatch computeUpgradeTo(final Context context, final String clientId,
            final List<WordListMetadata> newMetadata) {
        final List<WordListMetadata> currentMetadata =
                MetadataHandler.getCurrentMetadata(context, clientId);
        return compareMetadataForUpgrade(context, clientId, currentMetadata, newMetadata);
    }

    /**
     * Installs a word list if it has never been requested.
     *
     * This is called when a word list is requested, and is available but not installed. It checks
     * the conditions for auto-installation: if the dictionary is a main dictionary for this
     * language, and it has never been opted out through the dictionary interface, then we start
     * installing it. For the user who enables a language and uses it for the first time, the
     * dictionary should magically start being used a short time after they start typing.
     * The mayPrompt argument indicates whether we should prompt the user for a decision to
     * download or not, in case we decide we are in the case where we should download - this
     * roughly happens when the current connectivity is 3G. See
     * DictionaryProvider#getDictionaryWordListsForContentUri for details.
     */
    // As opposed to many other methods, this method does not need the version of the word
    // list because it may only install the latest version we know about for this specific
    // word list ID / client ID combination.
    public static void installIfNeverRequested(final Context context, final String clientId,
            final String wordlistId) {
        Log.i(TAG, "installIfNeverRequested() : ClientId = " + clientId
                + " : WordListId = " + wordlistId);
        final String[] idArray = wordlistId.split(DictionaryProvider.ID_CATEGORY_SEPARATOR);
        // If we have a new-format dictionary id (category:manual_id), then use the
        // specified category. Otherwise, it is a main dictionary, so force the
        // MAIN category upon it.
        final String category = 2 == idArray.length ? idArray[0] : MAIN_DICTIONARY_CATEGORY;
        if (!MAIN_DICTIONARY_CATEGORY.equals(category)) {
            // Not a main dictionary. We only auto-install main dictionaries, so we can return now.
            return;
        }
        if (CommonPreferences.getCommonPreferences(context).contains(wordlistId)) {
            // If some kind of settings has been done in the past for this specific id, then
            // this is not a candidate for auto-install. Because it already is either true,
            // in which case it may be installed or downloading or whatever, and we don't
            // need to care about it because it's already handled or being handled, or it's false
            // in which case it means the user explicitely turned it off and don't want to have
            // it installed. So we quit right away.
            return;
        }

        final SQLiteDatabase db = MetadataDbHelper.getDb(context, clientId);
        final ContentValues installCandidate =
                MetadataDbHelper.getContentValuesOfLatestAvailableWordlistById(db, wordlistId);
        if (MetadataDbHelper.STATUS_AVAILABLE
                != installCandidate.getAsInteger(MetadataDbHelper.STATUS_COLUMN)) {
            // If it's not "AVAILABLE", we want to stop now. Because candidates for auto-install
            // are lists that we know are available, but we also know have never been installed.
            // It does obviously not concern already installed lists, or downloading lists,
            // or those that have been disabled, flagged as deleting... So anything else than
            // AVAILABLE means we don't auto-install.
            return;
        }

        // We decided against prompting the user for a decision. This may be because we were
        // explicitly asked not to, or because we are currently on wi-fi anyway, or because we
        // already know the answer to the question. We'll enqueue a request ; StartDownloadAction
        // knows to use the correct type of network according to the current settings.

        // Also note that once it's auto-installed, a word list will be marked as INSTALLED. It will
        // thus receive automatic updates if there are any, which is what we want. If the user does
        // not want this word list, they will have to go to the settings and change them, which will
        // change the shared preferences. So there is no way for a word list that has been
        // auto-installed once to get auto-installed again, and that's what we want.
        final ActionBatch actions = new ActionBatch();
        WordListMetadata metadata = WordListMetadata.createFromContentValues(installCandidate);
        actions.add(new ActionBatch.StartDownloadAction(clientId, metadata));
        final String localeString = installCandidate.getAsString(MetadataDbHelper.LOCALE_COLUMN);

        // We are in a content provider: we can't do any UI at all. We have to defer the displaying
        // itself to the service. Also, we only display this when the user does not have a
        // dictionary for this language already. During setup wizard, however, this UI is
        // suppressed.
        final boolean deviceProvisioned = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
        if (deviceProvisioned) {
            final Intent intent = new Intent();
            intent.setClass(context, DictionaryService.class);
            intent.setAction(DictionaryService.SHOW_DOWNLOAD_TOAST_INTENT_ACTION);
            intent.putExtra(DictionaryService.LOCALE_INTENT_ARGUMENT, localeString);
            context.startService(intent);
        } else {
            Log.i(TAG, "installIfNeverRequested() : Don't show download toast");
        }

        Log.i(TAG, "installIfNeverRequested() : StartDownloadAction for " + metadata);
        actions.execute(context, new LogProblemReporter(TAG));
    }

    /**
     * Marks the word list with the passed id as used.
     *
     * This will download/install the list as required. The action will see that the destination
     * word list is a valid list, and take appropriate action - in this case, mark it as used.
     * @see ActionBatch.Action#execute
     *
     * @param context the context for using action batches.
     * @param clientId the id of the client.
     * @param wordlistId the id of the word list to mark as installed.
     * @param version the version of the word list to mark as installed.
     * @param status the current status of the word list.
     * @param allowDownloadOnMeteredData whether to download even on metered data connection
     */
    // The version argument is not used yet, because we don't need it to retrieve the information
    // we need. However, the pair (id, version) being the primary key to a word list in the database
    // it feels better for consistency to pass it, and some methods retrieving information about a
    // word list need it so we may need it in the future.
    public static void markAsUsed(final Context context, final String clientId,
            final String wordlistId, final int version,
            final int status, final boolean allowDownloadOnMeteredData) {
        final WordListMetadata wordListMetaData = MetadataHandler.getCurrentMetadataForWordList(
                context, clientId, wordlistId, version);

        if (null == wordListMetaData) return;

        final ActionBatch actions = new ActionBatch();
        if (MetadataDbHelper.STATUS_DISABLED == status
                || MetadataDbHelper.STATUS_DELETING == status) {
            actions.add(new ActionBatch.EnableAction(clientId, wordListMetaData));
        } else if (MetadataDbHelper.STATUS_AVAILABLE == status) {
            actions.add(new ActionBatch.StartDownloadAction(clientId, wordListMetaData));
        } else {
            Log.e(TAG, "Unexpected state of the word list for markAsUsed : " + status);
        }
        actions.execute(context, new LogProblemReporter(TAG));
        signalNewDictionaryState(context);
    }

    /**
     * Marks the word list with the passed id as unused.
     *
     * This leaves the file on the disk for ulterior use. The action will see that the destination
     * word list is null, and take appropriate action - in this case, mark it as unused.
     * @see ActionBatch.Action#execute
     *
     * @param context the context for using action batches.
     * @param clientId the id of the client.
     * @param wordlistId the id of the word list to mark as installed.
     * @param version the version of the word list to mark as installed.
     * @param status the current status of the word list.
     */
    // The version and status arguments are not used yet, but this method matches its interface to
    // markAsUsed for consistency.
    public static void markAsUnused(final Context context, final String clientId,
            final String wordlistId, final int version, final int status) {

        final WordListMetadata wordListMetaData = MetadataHandler.getCurrentMetadataForWordList(
                context, clientId, wordlistId, version);

        if (null == wordListMetaData) return;
        final ActionBatch actions = new ActionBatch();
        actions.add(new ActionBatch.DisableAction(clientId, wordListMetaData));
        actions.execute(context, new LogProblemReporter(TAG));
        signalNewDictionaryState(context);
    }

    /**
     * Marks the word list with the passed id as deleting.
     *
     * This basically means that on the next chance there is (right away if Android Keyboard
     * happens to be up, or the next time it gets up otherwise) the dictionary pack will
     * supply an empty dictionary to it that will replace whatever dictionary is installed.
     * This allows to release the space taken by a dictionary (except for the few bytes the
     * empty dictionary takes up), and override a built-in default dictionary so that we
     * can fake delete a built-in dictionary.
     *
     * @param context the context to open the database on.
     * @param clientId the id of the client.
     * @param wordlistId the id of the word list to mark as deleted.
     * @param version the version of the word list to mark as deleted.
     * @param status the current status of the word list.
     */
    public static void markAsDeleting(final Context context, final String clientId,
            final String wordlistId, final int version, final int status) {

        final WordListMetadata wordListMetaData = MetadataHandler.getCurrentMetadataForWordList(
                context, clientId, wordlistId, version);

        if (null == wordListMetaData) return;
        final ActionBatch actions = new ActionBatch();
        actions.add(new ActionBatch.DisableAction(clientId, wordListMetaData));
        actions.add(new ActionBatch.StartDeleteAction(clientId, wordListMetaData));
        actions.execute(context, new LogProblemReporter(TAG));
        signalNewDictionaryState(context);
    }

    /**
     * Marks the word list with the passed id as actually deleted.
     *
     * This reverts to available status or deletes the row as appropriate.
     *
     * @param context the context to open the database on.
     * @param clientId the id of the client.
     * @param wordlistId the id of the word list to mark as deleted.
     * @param version the version of the word list to mark as deleted.
     * @param status the current status of the word list.
     */
    public static void markAsDeleted(final Context context, final String clientId,
            final String wordlistId, final int version, final int status) {
        final WordListMetadata wordListMetaData = MetadataHandler.getCurrentMetadataForWordList(
                        context, clientId, wordlistId, version);

        if (null == wordListMetaData) return;

        final ActionBatch actions = new ActionBatch();
        actions.add(new ActionBatch.FinishDeleteAction(clientId, wordListMetaData));
        actions.execute(context, new LogProblemReporter(TAG));
        signalNewDictionaryState(context);
    }

    /**
     * Checks whether the word list should be downloaded again; in which case an download &
     * installation attempt is made. Otherwise the word list is marked broken.
     *
     * @param context the context to open the database on.
     * @param clientId the id of the client.
     * @param wordlistId the id of the word list which is broken.
     * @param version the version of the broken word list.
     */
    public static void markAsBrokenOrRetrying(final Context context, final String clientId,
            final String wordlistId, final int version) {
        boolean isRetryPossible = MetadataDbHelper.maybeMarkEntryAsRetrying(
                MetadataDbHelper.getDb(context, clientId), wordlistId, version);

        if (isRetryPossible) {
            if (DEBUG) {
                Log.d(TAG, "Attempting to download & install the wordlist again.");
            }
            final WordListMetadata wordListMetaData = MetadataHandler.getCurrentMetadataForWordList(
                    context, clientId, wordlistId, version);
            if (wordListMetaData == null) {
                return;
            }

            final ActionBatch actions = new ActionBatch();
            actions.add(new ActionBatch.StartDownloadAction(clientId, wordListMetaData));
            actions.execute(context, new LogProblemReporter(TAG));
        } else {
            if (DEBUG) {
                Log.d(TAG, "Retries for wordlist exhausted, deleting the wordlist from table.");
            }
            MetadataDbHelper.deleteEntry(MetadataDbHelper.getDb(context, clientId),
                    wordlistId, version);
        }
    }
}
