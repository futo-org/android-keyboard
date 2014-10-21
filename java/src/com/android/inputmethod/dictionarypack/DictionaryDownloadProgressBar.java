/**
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.inputmethod.dictionarypack;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

public class DictionaryDownloadProgressBar extends ProgressBar {
    private static final String TAG = DictionaryDownloadProgressBar.class.getSimpleName();
    private static final int NOT_A_DOWNLOADMANAGER_PENDING_ID = 0;

    private String mClientId;
    private String mWordlistId;
    private boolean mIsCurrentlyAttachedToWindow = false;
    private Thread mReporterThread = null;

    public DictionaryDownloadProgressBar(final Context context) {
        super(context);
    }

    public DictionaryDownloadProgressBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public void setIds(final String clientId, final String wordlistId) {
        mClientId = clientId;
        mWordlistId = wordlistId;
    }

    static private int getDownloadManagerPendingIdFromWordlistId(final Context context,
            final String clientId, final String wordlistId) {
        final SQLiteDatabase db = MetadataDbHelper.getDb(context, clientId);
        final ContentValues wordlistValues =
                MetadataDbHelper.getContentValuesOfLatestAvailableWordlistById(db, wordlistId);
        if (null == wordlistValues) {
            // We don't know anything about a word list with this id. Bug? This should never
            // happen, but still return to prevent a crash.
            Log.e(TAG, "Unexpected word list ID: " + wordlistId);
            return NOT_A_DOWNLOADMANAGER_PENDING_ID;
        }
        return wordlistValues.getAsInteger(MetadataDbHelper.PENDINGID_COLUMN);
    }

    /*
     * This method will stop any running updater thread for this progress bar and create and run
     * a new one only if the progress bar is visible.
     * Hence, as a result of calling this method, the progress bar will have an updater thread
     * running if and only if the progress bar is visible.
     */
    private void updateReporterThreadRunningStatusAccordingToVisibility() {
        if (null != mReporterThread) mReporterThread.interrupt();
        if (mIsCurrentlyAttachedToWindow && View.VISIBLE == getVisibility()) {
            final int downloadManagerPendingId =
                    getDownloadManagerPendingIdFromWordlistId(getContext(), mClientId, mWordlistId);
            if (NOT_A_DOWNLOADMANAGER_PENDING_ID == downloadManagerPendingId) {
                // Can't get the ID. This is never supposed to happen, but still clear the updater
                // thread and return to avoid a crash.
                mReporterThread = null;
                return;
            }
            final UpdaterThread updaterThread =
                    new UpdaterThread(getContext(), downloadManagerPendingId);
            updaterThread.start();
            mReporterThread = updaterThread;
        } else {
            // We're not going to restart the thread anyway, so we may as well garbage collect it.
            mReporterThread = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        mIsCurrentlyAttachedToWindow = true;
        updateReporterThreadRunningStatusAccordingToVisibility();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mIsCurrentlyAttachedToWindow = false;
        updateReporterThreadRunningStatusAccordingToVisibility();
    }

    private class UpdaterThread extends Thread {
        private final static int REPORT_PERIOD = 150; // how often to report progress, in ms
        final DownloadManagerWrapper mDownloadManagerWrapper;
        final int mId;
        public UpdaterThread(final Context context, final int id) {
            super();
            mDownloadManagerWrapper = new DownloadManagerWrapper(context);
            mId = id;
        }
        @Override
        public void run() {
            try {
                final UpdateHelper updateHelper = new UpdateHelper();
                final Query query = new Query().setFilterById(mId);
                setIndeterminate(true);
                while (!isInterrupted()) {
                    final Cursor cursor = mDownloadManagerWrapper.query(query);
                    if (null == cursor) {
                        // Can't contact DownloadManager: this should never happen.
                        return;
                    }
                    try {
                        if (cursor.moveToNext()) {
                            final int columnBytesDownloadedSoFar = cursor.getColumnIndex(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            final int bytesDownloadedSoFar =
                                    cursor.getInt(columnBytesDownloadedSoFar);
                            updateHelper.setProgressFromAnotherThread(bytesDownloadedSoFar);
                        } else {
                            // Download has finished and DownloadManager has already been asked to
                            // clean up the db entry.
                            updateHelper.setProgressFromAnotherThread(getMax());
                            return;
                        }
                    } finally {
                        cursor.close();
                    }
                    Thread.sleep(REPORT_PERIOD);
                }
            } catch (InterruptedException e) {
                // Do nothing and terminate normally.
            }
        }

        class UpdateHelper implements Runnable {
            private int mProgress;
            @Override
            public void run() {
                setIndeterminate(false);
                setProgress(mProgress);
            }
            public void setProgressFromAnotherThread(final int progress) {
                if (mProgress != progress) {
                    mProgress = progress;
                    // For some unknown reason, setProgress just does not work from a separate
                    // thread, although the code in ProgressBar looks like it should. Thus, we
                    // resort to a runnable posted to the handler of the view.
                    final Handler handler = getHandler();
                    // It's possible to come here before this view has been laid out. If so,
                    // just ignore the call - it will be updated again later.
                    if (null == handler) return;
                    handler.post(this);
                }
            }
        }
    }
}
