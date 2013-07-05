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
import android.app.DownloadManager.Request;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.compat.DownloadManagerCompatUtils;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.DebugLogUtils;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Object representing an upgrade from one state to another.
 *
 * This implementation basically encapsulates a list of Runnable objects. In the future
 * it may manage dependencies between them. Concretely, it does not use Runnable because the
 * actions need an argument.
 */
/*

The state of a word list follows the following scheme.

       |                                   ^
  MakeAvailable                            |
       |        .------------Forget--------'
       V        |
 STATUS_AVAILABLE  <-------------------------.
       |                                     |
StartDownloadAction                  FinishDeleteAction
       |                                     |
       V                                     |
STATUS_DOWNLOADING      EnableAction-- STATUS_DELETING
       |                     |               ^
InstallAfterDownloadAction   |               |
       |     .---------------'        StartDeleteAction
       |     |                               |
       V     V                               |
 STATUS_INSTALLED  <--EnableAction--   STATUS_DISABLED
                    --DisableAction-->

  It may also be possible that DisableAction or StartDeleteAction or
  DownloadAction run when the file is still downloading.  This cancels
  the download and returns to STATUS_AVAILABLE.
  Also, an UpdateDataAction may apply in any state. It does not affect
  the state in any way (nor type, local filename, id or version) but
  may update other attributes like description or remote filename.

  Forget is an DB maintenance action that removes the entry if it is not installed or disabled.
  This happens when the word list information disappeared from the server, or when a new version
  is available and we should forget about the old one.
*/
public final class ActionBatch {
    /**
     * A piece of update.
     *
     * Action is basically like a Runnable that takes an argument.
     */
    public interface Action {
        /**
         * Execute this action NOW.
         * @param context the context to get system services, resources, databases
         */
        public void execute(final Context context);
    }

    /**
     * An action that starts downloading an available word list.
     */
    public static final class StartDownloadAction implements Action {
        static final String TAG = "DictionaryProvider:" + StartDownloadAction.class.getSimpleName();

        private final String mClientId;
        // The data to download. May not be null.
        final WordListMetadata mWordList;
        final boolean mForceStartNow;
        public StartDownloadAction(final String clientId,
                final WordListMetadata wordList, final boolean forceStartNow) {
            DebugLogUtils.l("New download action for client ", clientId, " : ", wordList);
            mClientId = clientId;
            mWordList = wordList;
            mForceStartNow = forceStartNow;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "UpdateAction with a null parameter!");
                return;
            }
            DebugLogUtils.l("Downloading word list");
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            final ContentValues values = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion);
            final int status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
            final DownloadManager manager =
                    (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (MetadataDbHelper.STATUS_DOWNLOADING == status) {
                // The word list is still downloading. Cancel the download and revert the
                // word list status to "available".
                if (null != manager) {
                    // DownloadManager is disabled (or not installed?). We can't cancel - there
                    // is nothing we can do. We still need to mark the entry as available.
                    manager.remove(values.getAsLong(MetadataDbHelper.PENDINGID_COLUMN));
                }
                MetadataDbHelper.markEntryAsAvailable(db, mWordList.mId, mWordList.mVersion);
            } else if (MetadataDbHelper.STATUS_AVAILABLE != status) {
                // Should never happen
                Log.e(TAG, "Unexpected state of the word list '" + mWordList.mId + "' : " + status
                        + " for an upgrade action. Fall back to download.");
            }
            // Download it.
            DebugLogUtils.l("Upgrade word list, downloading", mWordList.mRemoteFilename);

            // TODO: if DownloadManager is disabled or not installed, download by ourselves
            if (null == manager) return;

            // This is an upgraded word list: we should download it.
            // Adding a disambiguator to circumvent a bug in older versions of DownloadManager.
            // DownloadManager also stupidly cuts the extension to replace with its own that it
            // gets from the content-type. We need to circumvent this.
            final String disambiguator = "#" + System.currentTimeMillis()
                    + ApplicationUtils.getVersionName(context) + ".dict";
            final Uri uri = Uri.parse(mWordList.mRemoteFilename + disambiguator);
            final Request request = new Request(uri);

            final Resources res = context.getResources();
            if (!mForceStartNow) {
                if (DownloadManagerCompatUtils.hasSetAllowedOverMetered()) {
                    final boolean allowOverMetered;
                    switch (UpdateHandler.getDownloadOverMeteredSetting(context)) {
                    case UpdateHandler.DOWNLOAD_OVER_METERED_DISALLOWED:
                        // User said no: don't allow.
                        allowOverMetered = false;
                        break;
                    case UpdateHandler.DOWNLOAD_OVER_METERED_ALLOWED:
                        // User said yes: allow.
                        allowOverMetered = true;
                        break;
                    default: // UpdateHandler.DOWNLOAD_OVER_METERED_SETTING_UNKNOWN
                        // Don't know: use the default value from configuration.
                        allowOverMetered = res.getBoolean(R.bool.allow_over_metered);
                    }
                    DownloadManagerCompatUtils.setAllowedOverMetered(request, allowOverMetered);
                } else {
                    request.setAllowedNetworkTypes(Request.NETWORK_WIFI);
                }
                request.setAllowedOverRoaming(res.getBoolean(R.bool.allow_over_roaming));
            } // if mForceStartNow, then allow all network types and roaming, which is the default.
            request.setTitle(mWordList.mDescription);
            request.setNotificationVisibility(
                    res.getBoolean(R.bool.display_notification_for_auto_update)
                            ? Request.VISIBILITY_VISIBLE : Request.VISIBILITY_HIDDEN);
            request.setVisibleInDownloadsUi(
                    res.getBoolean(R.bool.dict_downloads_visible_in_download_UI));

            final long downloadId = UpdateHandler.registerDownloadRequest(manager, request, db,
                    mWordList.mId, mWordList.mVersion);
            DebugLogUtils.l("Starting download of", uri, "with id", downloadId);
            PrivateLog.log("Starting download of " + uri + ", id : " + downloadId);
        }
    }

    /**
     * An action that updates the database to reflect the status of a newly installed word list.
     */
    public static final class InstallAfterDownloadAction implements Action {
        static final String TAG = "DictionaryProvider:"
                + InstallAfterDownloadAction.class.getSimpleName();
        private final String mClientId;
        // The state to upgrade from. May not be null.
        final ContentValues mWordListValues;

        public InstallAfterDownloadAction(final String clientId,
                final ContentValues wordListValues) {
            DebugLogUtils.l("New InstallAfterDownloadAction for client ", clientId, " : ",
                    wordListValues);
            mClientId = clientId;
            mWordListValues = wordListValues;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordListValues) {
                Log.e(TAG, "InstallAfterDownloadAction with a null parameter!");
                return;
            }
            final int status = mWordListValues.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
            if (MetadataDbHelper.STATUS_DOWNLOADING != status) {
                final String id = mWordListValues.getAsString(MetadataDbHelper.WORDLISTID_COLUMN);
                Log.e(TAG, "Unexpected state of the word list '" + id + "' : " + status
                        + " for an InstallAfterDownload action. Bailing out.");
                return;
            }
            DebugLogUtils.l("Setting word list as installed");
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            MetadataDbHelper.markEntryAsFinishedDownloadingAndInstalled(db, mWordListValues);
        }
    }

    /**
     * An action that enables an existing word list.
     */
    public static final class EnableAction implements Action {
        static final String TAG = "DictionaryProvider:" + EnableAction.class.getSimpleName();
        private final String mClientId;
        // The state to upgrade from. May not be null.
        final WordListMetadata mWordList;

        public EnableAction(final String clientId, final WordListMetadata wordList) {
            DebugLogUtils.l("New EnableAction for client ", clientId, " : ", wordList);
            mClientId = clientId;
            mWordList = wordList;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordList) {
                Log.e(TAG, "EnableAction with a null parameter!");
                return;
            }
            DebugLogUtils.l("Enabling word list");
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            final ContentValues values = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion);
            final int status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
            if (MetadataDbHelper.STATUS_DISABLED != status
                    && MetadataDbHelper.STATUS_DELETING != status) {
                Log.e(TAG, "Unexpected state of the word list '" + mWordList.mId + " : " + status
                      + " for an enable action. Cancelling");
                return;
            }
            MetadataDbHelper.markEntryAsEnabled(db, mWordList.mId, mWordList.mVersion);
        }
    }

    /**
     * An action that disables a word list.
     */
    public static final class DisableAction implements Action {
        static final String TAG = "DictionaryProvider:" + DisableAction.class.getSimpleName();
        private final String mClientId;
        // The word list to disable. May not be null.
        final WordListMetadata mWordList;
        public DisableAction(final String clientId, final WordListMetadata wordlist) {
            DebugLogUtils.l("New Disable action for client ", clientId, " : ", wordlist);
            mClientId = clientId;
            mWordList = wordlist;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "DisableAction with a null word list!");
                return;
            }
            DebugLogUtils.l("Disabling word list : " + mWordList);
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            final ContentValues values = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion);
            final int status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
            if (MetadataDbHelper.STATUS_INSTALLED == status) {
                // Disabling an installed word list
                MetadataDbHelper.markEntryAsDisabled(db, mWordList.mId, mWordList.mVersion);
            } else {
                if (MetadataDbHelper.STATUS_DOWNLOADING != status) {
                    Log.e(TAG, "Unexpected state of the word list '" + mWordList.mId + "' : "
                            + status + " for a disable action. Fall back to marking as available.");
                }
                // The word list is still downloading. Cancel the download and revert the
                // word list status to "available".
                final DownloadManager manager =
                        (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (null != manager) {
                    // If we can't cancel the download because DownloadManager is not available,
                    // we still need to mark the entry as available.
                    manager.remove(values.getAsLong(MetadataDbHelper.PENDINGID_COLUMN));
                }
                MetadataDbHelper.markEntryAsAvailable(db, mWordList.mId, mWordList.mVersion);
            }
        }
    }

    /**
     * An action that makes a word list available.
     */
    public static final class MakeAvailableAction implements Action {
        static final String TAG = "DictionaryProvider:" + MakeAvailableAction.class.getSimpleName();
        private final String mClientId;
        // The word list to make available. May not be null.
        final WordListMetadata mWordList;
        public MakeAvailableAction(final String clientId, final WordListMetadata wordlist) {
            DebugLogUtils.l("New MakeAvailable action", clientId, " : ", wordlist);
            mClientId = clientId;
            mWordList = wordlist;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "MakeAvailableAction with a null word list!");
                return;
            }
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            if (null != MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion)) {
                Log.e(TAG, "Unexpected state of the word list '" + mWordList.mId + "' "
                        + " for a makeavailable action. Marking as available anyway.");
            }
            DebugLogUtils.l("Making word list available : " + mWordList);
            // If mLocalFilename is null, then it's a remote file that hasn't been downloaded
            // yet, so we set the local filename to the empty string.
            final ContentValues values = MetadataDbHelper.makeContentValues(0,
                    MetadataDbHelper.TYPE_BULK, MetadataDbHelper.STATUS_AVAILABLE,
                    mWordList.mId, mWordList.mLocale, mWordList.mDescription,
                    null == mWordList.mLocalFilename ? "" : mWordList.mLocalFilename,
                    mWordList.mRemoteFilename, mWordList.mLastUpdate, mWordList.mChecksum,
                    mWordList.mFileSize, mWordList.mVersion, mWordList.mFormatVersion);
            PrivateLog.log("Insert 'available' record for " + mWordList.mDescription
                    + " and locale " + mWordList.mLocale);
            db.insert(MetadataDbHelper.METADATA_TABLE_NAME, null, values);
        }
    }

    /**
     * An action that marks a word list as pre-installed.
     *
     * This is almost the same as MakeAvailableAction, as it only inserts a line with parameters
     * received from outside.
     * Unlike MakeAvailableAction, the parameters are not received from a downloaded metadata file
     * but from the client directly; it marks a word list as being "installed" and not "available".
     * It also explicitly sets the filename to the empty string, so that we don't try to open
     * it on our side.
     */
    public static final class MarkPreInstalledAction implements Action {
        static final String TAG = "DictionaryProvider:"
                + MarkPreInstalledAction.class.getSimpleName();
        private final String mClientId;
        // The word list to mark pre-installed. May not be null.
        final WordListMetadata mWordList;
        public MarkPreInstalledAction(final String clientId, final WordListMetadata wordlist) {
            DebugLogUtils.l("New MarkPreInstalled action", clientId, " : ", wordlist);
            mClientId = clientId;
            mWordList = wordlist;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "MarkPreInstalledAction with a null word list!");
                return;
            }
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            if (null != MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion)) {
                Log.e(TAG, "Unexpected state of the word list '" + mWordList.mId + "' "
                        + " for a markpreinstalled action. Marking as preinstalled anyway.");
            }
            DebugLogUtils.l("Marking word list preinstalled : " + mWordList);
            // This word list is pre-installed : we don't have its file. We should reset
            // the local file name to the empty string so that we don't try to open it
            // accidentally. The remote filename may be set by the application if it so wishes.
            final ContentValues values = MetadataDbHelper.makeContentValues(0,
                    MetadataDbHelper.TYPE_BULK, MetadataDbHelper.STATUS_INSTALLED,
                    mWordList.mId, mWordList.mLocale, mWordList.mDescription,
                    "", mWordList.mRemoteFilename, mWordList.mLastUpdate,
                    mWordList.mChecksum, mWordList.mFileSize, mWordList.mVersion,
                    mWordList.mFormatVersion);
            PrivateLog.log("Insert 'preinstalled' record for " + mWordList.mDescription
                    + " and locale " + mWordList.mLocale);
            db.insert(MetadataDbHelper.METADATA_TABLE_NAME, null, values);
        }
    }

    /**
     * An action that updates information about a word list - description, locale etc
     */
    public static final class UpdateDataAction implements Action {
        static final String TAG = "DictionaryProvider:" + UpdateDataAction.class.getSimpleName();
        private final String mClientId;
        final WordListMetadata mWordList;
        public UpdateDataAction(final String clientId, final WordListMetadata wordlist) {
            DebugLogUtils.l("New UpdateData action for client ", clientId, " : ", wordlist);
            mClientId = clientId;
            mWordList = wordlist;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "UpdateDataAction with a null word list!");
                return;
            }
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            ContentValues oldValues = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion);
            if (null == oldValues) {
                Log.e(TAG, "Trying to update data about a non-existing word list. Bailing out.");
                return;
            }
            DebugLogUtils.l("Updating data about a word list : " + mWordList);
            final ContentValues values = MetadataDbHelper.makeContentValues(
                    oldValues.getAsInteger(MetadataDbHelper.PENDINGID_COLUMN),
                    oldValues.getAsInteger(MetadataDbHelper.TYPE_COLUMN),
                    oldValues.getAsInteger(MetadataDbHelper.STATUS_COLUMN),
                    mWordList.mId, mWordList.mLocale, mWordList.mDescription,
                    oldValues.getAsString(MetadataDbHelper.LOCAL_FILENAME_COLUMN),
                    mWordList.mRemoteFilename, mWordList.mLastUpdate, mWordList.mChecksum,
                    mWordList.mFileSize, mWordList.mVersion, mWordList.mFormatVersion);
            PrivateLog.log("Updating record for " + mWordList.mDescription
                    + " and locale " + mWordList.mLocale);
            db.update(MetadataDbHelper.METADATA_TABLE_NAME, values,
                    MetadataDbHelper.WORDLISTID_COLUMN + " = ? AND "
                            + MetadataDbHelper.VERSION_COLUMN + " = ?",
                    new String[] { mWordList.mId, Integer.toString(mWordList.mVersion) });
        }
    }

    /**
     * An action that deletes the metadata about a word list if possible.
     *
     * This is triggered when a specific word list disappeared from the server, or when a fresher
     * word list is available and the old one was not installed.
     * If the word list has not been installed, it's possible to delete its associated metadata.
     * Otherwise, the settings are retained so that the user can still administrate it.
     */
    public static final class ForgetAction implements Action {
        static final String TAG = "DictionaryProvider:" + ForgetAction.class.getSimpleName();
        private final String mClientId;
        // The word list to remove. May not be null.
        final WordListMetadata mWordList;
        final boolean mHasNewerVersion;
        public ForgetAction(final String clientId, final WordListMetadata wordlist,
                final boolean hasNewerVersion) {
            DebugLogUtils.l("New TryRemove action for client ", clientId, " : ", wordlist);
            mClientId = clientId;
            mWordList = wordlist;
            mHasNewerVersion = hasNewerVersion;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "TryRemoveAction with a null word list!");
                return;
            }
            DebugLogUtils.l("Trying to remove word list : " + mWordList);
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            final ContentValues values = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion);
            if (null == values) {
                Log.e(TAG, "Trying to update the metadata of a non-existing wordlist. Cancelling.");
                return;
            }
            final int status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
            if (mHasNewerVersion && MetadataDbHelper.STATUS_AVAILABLE != status) {
                // If we have a newer version of this word list, we should be here ONLY if it was
                // not installed - else we should be upgrading it.
                Log.e(TAG, "Unexpected status for forgetting a word list info : " + status
                        + ", removing URL to prevent re-download");
            }
            if (MetadataDbHelper.STATUS_INSTALLED == status
                    || MetadataDbHelper.STATUS_DISABLED == status
                    || MetadataDbHelper.STATUS_DELETING == status) {
                // If it is installed or disabled, we need to mark it as deleted so that LatinIME
                // will remove it next time it enquires for dictionaries.
                // If it is deleting and we don't have a new version, then we have to wait until
                // LatinIME actually has deleted it before we can remove its metadata.
                // In both cases, remove the URI from the database since it is not supposed to
                // be accessible any more.
                values.put(MetadataDbHelper.REMOTE_FILENAME_COLUMN, "");
                values.put(MetadataDbHelper.STATUS_COLUMN, MetadataDbHelper.STATUS_DELETING);
                db.update(MetadataDbHelper.METADATA_TABLE_NAME, values,
                        MetadataDbHelper.WORDLISTID_COLUMN + " = ? AND "
                                + MetadataDbHelper.VERSION_COLUMN + " = ?",
                        new String[] { mWordList.mId, Integer.toString(mWordList.mVersion) });
            } else {
                // If it's AVAILABLE or DOWNLOADING or even UNKNOWN, delete the entry.
                db.delete(MetadataDbHelper.METADATA_TABLE_NAME,
                        MetadataDbHelper.WORDLISTID_COLUMN + " = ? AND "
                                + MetadataDbHelper.VERSION_COLUMN + " = ?",
                        new String[] { mWordList.mId, Integer.toString(mWordList.mVersion) });
            }
        }
    }

    /**
     * An action that sets the word list for deletion as soon as possible.
     *
     * This is triggered when the user requests deletion of a word list. This will mark it as
     * deleted in the database, and fire an intent for Android Keyboard to take notice and
     * reload its dictionaries right away if it is up. If it is not up now, then it will
     * delete the actual file the next time it gets up.
     * A file marked as deleted causes the content provider to supply a zero-sized file to
     * Android Keyboard, which will overwrite any existing file and provide no words for this
     * word list. This is not exactly a "deletion", since there is an actual file which takes up
     * a few bytes on the disk, but this allows to override a default dictionary with an empty
     * dictionary. This way, there is no need for the user to make a distinction between
     * dictionaries installed by default and add-on dictionaries.
     */
    public static final class StartDeleteAction implements Action {
        static final String TAG = "DictionaryProvider:" + StartDeleteAction.class.getSimpleName();
        private final String mClientId;
        // The word list to delete. May not be null.
        final WordListMetadata mWordList;
        public StartDeleteAction(final String clientId, final WordListMetadata wordlist) {
            DebugLogUtils.l("New StartDelete action for client ", clientId, " : ", wordlist);
            mClientId = clientId;
            mWordList = wordlist;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "StartDeleteAction with a null word list!");
                return;
            }
            DebugLogUtils.l("Trying to delete word list : " + mWordList);
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            final ContentValues values = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion);
            if (null == values) {
                Log.e(TAG, "Trying to set a non-existing wordlist for removal. Cancelling.");
                return;
            }
            final int status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
            if (MetadataDbHelper.STATUS_DISABLED != status) {
                Log.e(TAG, "Unexpected status for deleting a word list info : " + status);
            }
            MetadataDbHelper.markEntryAsDeleting(db, mWordList.mId, mWordList.mVersion);
        }
    }

    /**
     * An action that validates a word list as deleted.
     *
     * This will restore the word list as available if it still is, or remove the entry if
     * it is not any more.
     */
    public static final class FinishDeleteAction implements Action {
        static final String TAG = "DictionaryProvider:" + FinishDeleteAction.class.getSimpleName();
        private final String mClientId;
        // The word list to delete. May not be null.
        final WordListMetadata mWordList;
        public FinishDeleteAction(final String clientId, final WordListMetadata wordlist) {
            DebugLogUtils.l("New FinishDelete action for client", clientId, " : ", wordlist);
            mClientId = clientId;
            mWordList = wordlist;
        }

        @Override
        public void execute(final Context context) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "FinishDeleteAction with a null word list!");
                return;
            }
            DebugLogUtils.l("Trying to delete word list : " + mWordList);
            final SQLiteDatabase db = MetadataDbHelper.getDb(context, mClientId);
            final ContentValues values = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion);
            if (null == values) {
                Log.e(TAG, "Trying to set a non-existing wordlist for removal. Cancelling.");
                return;
            }
            final int status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
            if (MetadataDbHelper.STATUS_DELETING != status) {
                Log.e(TAG, "Unexpected status for finish-deleting a word list info : " + status);
            }
            final String remoteFilename =
                    values.getAsString(MetadataDbHelper.REMOTE_FILENAME_COLUMN);
            // If there isn't a remote filename any more, then we don't know where to get the file
            // from any more, so we remove the entry entirely. As a matter of fact, if the file was
            // marked DELETING but disappeared from the metadata on the server, it ended up
            // this way.
            if (TextUtils.isEmpty(remoteFilename)) {
                db.delete(MetadataDbHelper.METADATA_TABLE_NAME,
                        MetadataDbHelper.WORDLISTID_COLUMN + " = ? AND "
                                + MetadataDbHelper.VERSION_COLUMN + " = ?",
                        new String[] { mWordList.mId, Integer.toString(mWordList.mVersion) });
            } else {
                MetadataDbHelper.markEntryAsAvailable(db, mWordList.mId, mWordList.mVersion);
            }
        }
    }

    // An action batch consists of an ordered queue of Actions that can execute.
    private final Queue<Action> mActions;

    public ActionBatch() {
        mActions = new LinkedList<Action>();
    }

    public void add(final Action a) {
        mActions.add(a);
    }

    /**
     * Append all the actions of another action batch.
     * @param that the upgrade to merge into this one.
     */
    public void append(final ActionBatch that) {
        for (final Action a : that.mActions) {
            add(a);
        }
    }

    /**
     * Execute this batch.
     *
     * @param context the context for getting resources, databases, system services.
     * @param reporter a Reporter to send errors to.
     */
    public void execute(final Context context, final ProblemReporter reporter) {
        DebugLogUtils.l("Executing a batch of actions");
        Queue<Action> remainingActions = mActions;
        while (!remainingActions.isEmpty()) {
            final Action a = remainingActions.poll();
            try {
                a.execute(context);
            } catch (Exception e) {
                if (null != reporter)
                    reporter.report(e);
            }
        }
    }
}
