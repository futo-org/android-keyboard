/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.util.JsonWriter;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Logs the use of the LatinIME keyboard.
 *
 * This class logs operations on the IME keyboard, including what the user has typed.  Data is
 * written to a {@link JsonWriter}, which will write to a local file.
 *
 * The JsonWriter is created on-demand by calling {@link #getInitializedJsonWriterLocked}.
 *
 * This class uses an executor to perform file-writing operations on a separate thread.  It also
 * tries to avoid creating unnecessary files if there is nothing to write.  It also handles
 * flushing, making sure it happens, but not too frequently.
 *
 * This functionality is off by default. See
 * {@link ProductionFlag#USES_DEVELOPMENT_ONLY_DIAGNOSTICS}.
 */
public class ResearchLog {
    // TODO: Automatically initialize the JsonWriter rather than requiring the caller to manage it.
    private static final String TAG = ResearchLog.class.getSimpleName();
    private static final boolean DEBUG = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    private static final long FLUSH_DELAY_IN_MS = TimeUnit.SECONDS.toMillis(5);

    /* package */ final ScheduledExecutorService mExecutor;
    /* package */ final File mFile;
    private final Context mContext;

    // Earlier implementations used a dummy JsonWriter that just swallowed what it was given, but
    // this was tricky to do well, because JsonWriter throws an exception if it is passed more than
    // one top-level object.
    private JsonWriter mJsonWriter = null;

    // true if at least one byte of data has been written out to the log file.  This must be
    // remembered because JsonWriter requires that calls matching calls to beginObject and
    // endObject, as well as beginArray and endArray, and the file is opened lazily, only when
    // it is certain that data will be written.  Alternatively, the matching call exceptions
    // could be caught, but this might suppress other errors.
    private boolean mHasWrittenData = false;

    public ResearchLog(final File outputFile, final Context context) {
        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mFile = outputFile;
        mContext = context;
    }

    /**
     * Returns true if this is a FeedbackLog.
     *
     * FeedbackLogs record only the data associated with a Feedback dialog. Instead of normal
     * logging, they contain a LogStatement with the complete feedback string and optionally a
     * recording of the user's supplied demo of the problem.
     */
    public boolean isFeedbackLog() {
        return false;
    }

    /**
     * Waits for any publication requests to finish and closes the {@link JsonWriter} used for
     * output.
     *
     * See class comment for details about {@code JsonWriter} construction.
     *
     * @param onClosed run after the close() operation has completed asynchronously
     */
    private synchronized void close(final Runnable onClosed) {
        mExecutor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                try {
                    if (mJsonWriter == null) return null;
                    // TODO: This is necessary to avoid an exception.  Better would be to not even
                    // open the JsonWriter if the file is not even opened unless there is valid data
                    // to write.
                    if (!mHasWrittenData) {
                        mJsonWriter.beginArray();
                    }
                    mJsonWriter.endArray();
                    mHasWrittenData = false;
                    mJsonWriter.flush();
                    mJsonWriter.close();
                    if (DEBUG) {
                        Log.d(TAG, "closed " + mFile);
                    }
                } catch (final Exception e) {
                    Log.d(TAG, "error when closing ResearchLog:", e);
                } finally {
                    // Marking the file as read-only signals that this log file is ready to be
                    // uploaded.
                    if (mFile != null && mFile.exists()) {
                        mFile.setWritable(false, false);
                    }
                    if (onClosed != null) {
                        onClosed.run();
                    }
                }
                return null;
            }
        });
        removeAnyScheduledFlush();
        mExecutor.shutdown();
    }

    /**
     * Block until the research log has shut down and spooled out all output or {@code timeout}
     * occurs.
     *
     * @param timeout time to wait for close in milliseconds
     */
    public void blockingClose(final long timeout) {
        close(null);
        awaitTermination(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Waits for publication requests to finish, closes the JsonWriter, but then deletes the backing
     * output file.
     *
     * @param onAbort run after the abort() operation has completed asynchronously
     */
    private synchronized void abort(final Runnable onAbort) {
        mExecutor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                try {
                    if (mJsonWriter == null) return null;
                    if (mHasWrittenData) {
                        // TODO: This is necessary to avoid an exception.  Better would be to not
                        // even open the JsonWriter if the file is not even opened unless there is
                        // valid data to write.
                        if (!mHasWrittenData) {
                            mJsonWriter.beginArray();
                        }
                        mJsonWriter.endArray();
                        mJsonWriter.close();
                        mHasWrittenData = false;
                    }
                } finally {
                    if (mFile != null) {
                        mFile.delete();
                    }
                    if (onAbort != null) {
                        onAbort.run();
                    }
                }
                return null;
            }
        });
        removeAnyScheduledFlush();
        mExecutor.shutdown();
    }

    /**
     * Block until the research log has aborted or {@code timeout} occurs.
     *
     * @param timeout time to wait for close in milliseconds
     */
    public void blockingAbort(final long timeout) {
        abort(null);
        awaitTermination(timeout, TimeUnit.MILLISECONDS);
    }

    @UsedForTesting
    public void awaitTermination(final long delay, final TimeUnit timeUnit) {
        try {
            if (!mExecutor.awaitTermination(delay, timeUnit)) {
                Log.e(TAG, "ResearchLog executor timed out while awaiting terminaion");
            }
        } catch (final InterruptedException e) {
            Log.e(TAG, "ResearchLog executor interrupted while awaiting terminaion", e);
        }
    }

    /* package */ synchronized void flush() {
        removeAnyScheduledFlush();
        mExecutor.submit(mFlushCallable);
    }

    private final Callable<Object> mFlushCallable = new Callable<Object>() {
        @Override
        public Object call() throws Exception {
            if (mJsonWriter != null) mJsonWriter.flush();
            return null;
        }
    };

    private ScheduledFuture<Object> mFlushFuture;

    private void removeAnyScheduledFlush() {
        if (mFlushFuture != null) {
            mFlushFuture.cancel(false);
            mFlushFuture = null;
        }
    }

    private void scheduleFlush() {
        removeAnyScheduledFlush();
        mFlushFuture = mExecutor.schedule(mFlushCallable, FLUSH_DELAY_IN_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Queues up {@code logUnit} to be published in the background.
     *
     * @param logUnit the {@link LogUnit} to be published
     * @param canIncludePrivateData whether private data in the LogUnit should be included
     */
    public synchronized void publish(final LogUnit logUnit, final boolean canIncludePrivateData) {
        try {
            mExecutor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    logUnit.publishTo(ResearchLog.this, canIncludePrivateData);
                    scheduleFlush();
                    return null;
                }
            });
        } catch (final RejectedExecutionException e) {
            // TODO: Add code to record loss of data, and report.
            if (DEBUG) {
                Log.d(TAG, "ResearchLog.publish() rejecting scheduled execution", e);
            }
        }
    }

    /**
     * Return a JsonWriter for this ResearchLog.  It is initialized the first time this method is
     * called.  The cached value is returned in future calls.
     *
     * @throws IOException if opening the JsonWriter is not possible
     */
    public JsonWriter getInitializedJsonWriterLocked() throws IOException {
        if (mJsonWriter != null) return mJsonWriter;
        if (mFile == null) throw new FileNotFoundException();
        try {
            final JsonWriter jsonWriter = createJsonWriter(mContext, mFile);
            if (jsonWriter == null) throw new IOException("Could not create JsonWriter");

            jsonWriter.beginArray();
            mJsonWriter = jsonWriter;
            mHasWrittenData = true;
            return mJsonWriter;
        } catch (final IOException e) {
            if (DEBUG) {
                Log.w(TAG, "Exception when creating JsonWriter", e);
                Log.w(TAG, "Closing JsonWriter");
            }
            if (mJsonWriter != null) mJsonWriter.close();
            mJsonWriter = null;
            throw e;
        }
    }

    /**
     * Create the JsonWriter to write the ResearchLog to.
     *
     * This method may be overriden in testing to redirect the output.
     */
    /* package for test */ JsonWriter createJsonWriter(final Context context, final File file)
            throws IOException {
        return new JsonWriter(new BufferedWriter(new OutputStreamWriter(
                context.openFileOutput(file.getName(), Context.MODE_PRIVATE))));
    }
}
