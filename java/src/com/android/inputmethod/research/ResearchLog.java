/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.JsonWriter;
import android.util.Log;
import android.view.inputmethod.CompletionInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Logs the use of the LatinIME keyboard.
 *
 * This class logs operations on the IME keyboard, including what the user has typed.
 * Data is stored locally in a file in app-specific storage.
 *
 * This functionality is off by default. See {@link ProductionFlag#IS_EXPERIMENTAL}.
 */
public class ResearchLog {
    private static final String TAG = ResearchLog.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final long FLUSH_DELAY_IN_MS = 1000 * 5;
    private static final int ABORT_TIMEOUT_IN_MS = 1000 * 4;

    /* package */ final ScheduledExecutorService mExecutor;
    /* package */ final File mFile;
    private JsonWriter mJsonWriter = NULL_JSON_WRITER;
    // true if at least one byte of data has been written out to the log file.  This must be
    // remembered because JsonWriter requires that calls matching calls to beginObject and
    // endObject, as well as beginArray and endArray, and the file is opened lazily, only when
    // it is certain that data will be written.  Alternatively, the matching call exceptions
    // could be caught, but this might suppress other errors.
    private boolean mHasWrittenData = false;

    private static final JsonWriter NULL_JSON_WRITER = new JsonWriter(
            new OutputStreamWriter(new NullOutputStream()));
    private static class NullOutputStream extends OutputStream {
        /** {@inheritDoc} */
        @Override
        public void write(byte[] buffer, int offset, int count) {
            // nop
        }

        /** {@inheritDoc} */
        @Override
        public void write(byte[] buffer) {
            // nop
        }

        @Override
        public void write(int oneByte) {
        }
    }

    public ResearchLog(final File outputFile) {
        if (outputFile == null) {
            throw new IllegalArgumentException();
        }
        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mFile = outputFile;
    }

    public synchronized void close(final Runnable onClosed) {
        mExecutor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                try {
                    if (mHasWrittenData) {
                        mJsonWriter.endArray();
                        mJsonWriter.flush();
                        mJsonWriter.close();
                        if (DEBUG) {
                            Log.d(TAG, "wrote log to " + mFile);
                        }
                        mHasWrittenData = false;
                    } else {
                        if (DEBUG) {
                            Log.d(TAG, "close() called, but no data, not outputting");
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "error when closing ResearchLog:");
                    e.printStackTrace();
                } finally {
                    if (mFile.exists()) {
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

    private boolean mIsAbortSuccessful;

    public synchronized void abort() {
        mExecutor.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                try {
                    if (mHasWrittenData) {
                        mJsonWriter.endArray();
                        mJsonWriter.close();
                        mHasWrittenData = false;
                    }
                } finally {
                    mIsAbortSuccessful = mFile.delete();
                }
                return null;
            }
        });
        removeAnyScheduledFlush();
        mExecutor.shutdown();
    }

    public boolean blockingAbort() throws InterruptedException {
        abort();
        mExecutor.awaitTermination(ABORT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS);
        return mIsAbortSuccessful;
    }

    public void awaitTermination(int delay, TimeUnit timeUnit) throws InterruptedException {
        mExecutor.awaitTermination(delay, timeUnit);
    }

    /* package */ synchronized void flush() {
        removeAnyScheduledFlush();
        mExecutor.submit(mFlushCallable);
    }

    private final Callable<Object> mFlushCallable = new Callable<Object>() {
        @Override
        public Object call() throws Exception {
            mJsonWriter.flush();
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

    public synchronized void publish(final LogUnit logUnit, final boolean isIncludingPrivateData) {
        try {
            mExecutor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    logUnit.publishTo(ResearchLog.this, isIncludingPrivateData);
                    scheduleFlush();
                    return null;
                }
            });
        } catch (RejectedExecutionException e) {
            // TODO: Add code to record loss of data, and report.
        }
    }

    private static final String CURRENT_TIME_KEY = "_ct";
    private static final String UPTIME_KEY = "_ut";
    private static final String EVENT_TYPE_KEY = "_ty";

    void outputEvent(final String[] keys, final Object[] values) {
        // Not thread safe.
        if (keys.length == 0) {
            return;
        }
        if (DEBUG) {
            if (keys.length != values.length + 1) {
                Log.d(TAG, "Key and Value list sizes do not match. " + keys[0]);
            }
        }
        try {
            if (mJsonWriter == NULL_JSON_WRITER) {
                mJsonWriter = new JsonWriter(new BufferedWriter(new FileWriter(mFile)));
                mJsonWriter.beginArray();
                mHasWrittenData = true;
            }
            mJsonWriter.beginObject();
            mJsonWriter.name(CURRENT_TIME_KEY).value(System.currentTimeMillis());
            mJsonWriter.name(UPTIME_KEY).value(SystemClock.uptimeMillis());
            mJsonWriter.name(EVENT_TYPE_KEY).value(keys[0]);
            final int length = values.length;
            for (int i = 0; i < length; i++) {
                mJsonWriter.name(keys[i + 1]);
                Object value = values[i];
                if (value instanceof CharSequence) {
                    mJsonWriter.value(value.toString());
                } else if (value instanceof Number) {
                    mJsonWriter.value((Number) value);
                } else if (value instanceof Boolean) {
                    mJsonWriter.value((Boolean) value);
                } else if (value instanceof CompletionInfo[]) {
                    CompletionInfo[] ci = (CompletionInfo[]) value;
                    mJsonWriter.beginArray();
                    for (int j = 0; j < ci.length; j++) {
                        mJsonWriter.value(ci[j].toString());
                    }
                    mJsonWriter.endArray();
                } else if (value instanceof SharedPreferences) {
                    SharedPreferences prefs = (SharedPreferences) value;
                    mJsonWriter.beginObject();
                    for (Map.Entry<String,?> entry : prefs.getAll().entrySet()) {
                        mJsonWriter.name(entry.getKey());
                        final Object innerValue = entry.getValue();
                        if (innerValue == null) {
                            mJsonWriter.nullValue();
                        } else if (innerValue instanceof Boolean) {
                            mJsonWriter.value((Boolean) innerValue);
                        } else if (innerValue instanceof Number) {
                            mJsonWriter.value((Number) innerValue);
                        } else {
                            mJsonWriter.value(innerValue.toString());
                        }
                    }
                    mJsonWriter.endObject();
                } else if (value instanceof Key[]) {
                    Key[] keyboardKeys = (Key[]) value;
                    mJsonWriter.beginArray();
                    for (Key keyboardKey : keyboardKeys) {
                        mJsonWriter.beginObject();
                        mJsonWriter.name("code").value(keyboardKey.mCode);
                        mJsonWriter.name("altCode").value(keyboardKey.getAltCode());
                        mJsonWriter.name("x").value(keyboardKey.mX);
                        mJsonWriter.name("y").value(keyboardKey.mY);
                        mJsonWriter.name("w").value(keyboardKey.mWidth);
                        mJsonWriter.name("h").value(keyboardKey.mHeight);
                        mJsonWriter.endObject();
                    }
                    mJsonWriter.endArray();
                } else if (value instanceof SuggestedWords) {
                    SuggestedWords words = (SuggestedWords) value;
                    mJsonWriter.beginObject();
                    mJsonWriter.name("typedWordValid").value(words.mTypedWordValid);
                    mJsonWriter.name("willAutoCorrect").value(words.mWillAutoCorrect);
                    mJsonWriter.name("isPunctuationSuggestions")
                            .value(words.mIsPunctuationSuggestions);
                    mJsonWriter.name("isObsoleteSuggestions").value(words.mIsObsoleteSuggestions);
                    mJsonWriter.name("isPrediction").value(words.mIsPrediction);
                    mJsonWriter.name("words");
                    mJsonWriter.beginArray();
                    final int size = words.size();
                    for (int j = 0; j < size; j++) {
                        SuggestedWordInfo wordInfo = words.getWordInfo(j);
                        mJsonWriter.value(wordInfo.toString());
                    }
                    mJsonWriter.endArray();
                    mJsonWriter.endObject();
                } else if (value == null) {
                    mJsonWriter.nullValue();
                } else {
                    Log.w(TAG, "Unrecognized type to be logged: " +
                            (value == null ? "<null>" : value.getClass().getName()));
                    mJsonWriter.nullValue();
                }
            }
            mJsonWriter.endObject();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Error in JsonWriter; disabling logging");
            try {
                mJsonWriter.close();
            } catch (IllegalStateException e1) {
                // Assume that this is just the json not being terminated properly.
                // Ignore
            } catch (IOException e1) {
                e1.printStackTrace();
            } finally {
                mJsonWriter = NULL_JSON_WRITER;
            }
        }
    }
}
