/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;

/**
 * Manages log files.
 *
 * This class handles all aspects where and how research log data is stored.  This includes
 * generating log filenames in the correct place with the correct names, and cleaning up log files
 * under this directory.
 */
public class ResearchLogDirectory {
    public static final String TAG = ResearchLogDirectory.class.getSimpleName();
    /* package */ static final String LOG_FILENAME_PREFIX = "researchLog";
    private static final String FILENAME_SUFFIX = ".txt";
    private static final String USER_RECORDING_FILENAME_PREFIX = "recording";

    private static final ReadOnlyLogFileFilter sUploadableLogFileFilter =
            new ReadOnlyLogFileFilter();

    private final File mFilesDir;

    static class ReadOnlyLogFileFilter implements FileFilter {
        @Override
        public boolean accept(final File pathname) {
            return pathname.getName().startsWith(ResearchLogDirectory.LOG_FILENAME_PREFIX)
                    && !pathname.canWrite();
        }
    }

    /**
     * Creates a new ResearchLogDirectory, creating the storage directory if it does not exist.
     */
    public ResearchLogDirectory(final Context context) {
        mFilesDir = getLoggingDirectory(context);
        if (mFilesDir == null) {
            throw new NullPointerException("No files directory specified");
        }
        if (!mFilesDir.exists()) {
            mFilesDir.mkdirs();
        }
    }

    private File getLoggingDirectory(final Context context) {
        // TODO: Switch to using a subdirectory of getFilesDir().
        return context.getFilesDir();
    }

    /**
     * Get an array of log files that are ready for uploading.
     *
     * A file is ready for uploading if it is marked as read-only.
     *
     * @return the array of uploadable files
     */
    public File[] getUploadableLogFiles() {
        try {
            return mFilesDir.listFiles(sUploadableLogFileFilter);
        } catch (final SecurityException e) {
            Log.e(TAG, "Could not cleanup log directory, permission denied", e);
            return new File[0];
        }
    }

    public void cleanupLogFilesOlderThan(final long time) {
        try {
            for (final File file : mFilesDir.listFiles()) {
                final String filename = file.getName();
                if ((filename.startsWith(LOG_FILENAME_PREFIX)
                        || filename.startsWith(USER_RECORDING_FILENAME_PREFIX))
                        && (file.lastModified() < time)) {
                    file.delete();
                }
            }
        } catch (final SecurityException e) {
            Log.e(TAG, "Could not cleanup log directory, permission denied", e);
        }
    }

    public File getLogFilePath(final long time, final long nanoTime) {
        return new File(mFilesDir, getUniqueFilename(LOG_FILENAME_PREFIX, time, nanoTime));
    }

    public File getUserRecordingFilePath(final long time, final long nanoTime) {
        return new File(mFilesDir, getUniqueFilename(USER_RECORDING_FILENAME_PREFIX, time,
                nanoTime));
    }

    private static String getUniqueFilename(final String prefix, final long time,
            final long nanoTime) {
        return prefix + "-" + time + "-" + nanoTime + FILENAME_SUFFIX;
    }
}
