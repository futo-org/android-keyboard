/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileNotFoundException;

import javax.annotation.Nullable;

/**
 * A class to help with calling DownloadManager methods.
 *
 * Mostly, the problem here is that most methods from DownloadManager may throw SQL exceptions if
 * they can't open the database on disk. We want to avoid crashing in these cases but can't do
 * much more, so this class insulates the callers from these. SQLiteException also inherit from
 * RuntimeException so they are unchecked :(
 * While we're at it, we also insulate callers from the cases where DownloadManager is disabled,
 * and getSystemService returns null.
 */
public class DownloadManagerWrapper {
    private final static String TAG = DownloadManagerWrapper.class.getSimpleName();
    private final DownloadManager mDownloadManager;

    public DownloadManagerWrapper(final Context context) {
        this((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE));
    }

    private DownloadManagerWrapper(final DownloadManager downloadManager) {
        mDownloadManager = downloadManager;
    }

    public void remove(final long... ids) {
        try {
            if (null != mDownloadManager) {
                mDownloadManager.remove(ids);
            }
        } catch (IllegalArgumentException e) {
            // This is expected to happen on boot when the device is encrypted.
        } catch (SQLiteException e) {
            // We couldn't remove the file from DownloadManager. Apparently, the database can't
            // be opened. It may be a problem with file system corruption. In any case, there is
            // not much we can do apart from avoiding crashing.
            Log.e(TAG, "Can't remove files with ID " + ids + " from download manager", e);
        }
    }

    public ParcelFileDescriptor openDownloadedFile(final long fileId) throws FileNotFoundException {
        try {
            if (null != mDownloadManager) {
                return mDownloadManager.openDownloadedFile(fileId);
            }
        } catch (IllegalArgumentException e) {
            // This is expected to happen on boot when the device is encrypted.
        } catch (SQLiteException e) {
            Log.e(TAG, "Can't open downloaded file with ID " + fileId, e);
        }
        // We come here if mDownloadManager is null or if an exception was thrown.
        throw new FileNotFoundException();
    }

    @Nullable
    public Cursor query(final Query query) {
        try {
            if (null != mDownloadManager) {
                return mDownloadManager.query(query);
            }
        } catch (IllegalArgumentException e) {
            // This is expected to happen on boot when the device is encrypted.
        } catch (SQLiteException e) {
            Log.e(TAG, "Can't query the download manager", e);
        }
        // We come here if mDownloadManager is null or if an exception was thrown.
        return null;
    }

    public long enqueue(final Request request) {
        try {
            if (null != mDownloadManager) {
                return mDownloadManager.enqueue(request);
            }
        } catch (IllegalArgumentException e) {
            // This is expected to happen on boot when the device is encrypted.
        } catch (SQLiteException e) {
            Log.e(TAG, "Can't enqueue a request with the download manager", e);
        }
        return 0;
    }
}
