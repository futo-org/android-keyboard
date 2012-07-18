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

package com.android.inputmethod.latin;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.JsonWriter;
import android.util.Log;
import android.view.inputmethod.CompletionInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.latin.ResearchLogger.LogUnit;
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
    private static final JsonWriter NULL_JSON_WRITER = new JsonWriter(
            new OutputStreamWriter(new NullOutputStream()));

    final ScheduledExecutorService mExecutor;
    /* package */ final File mFile;
    private JsonWriter mJsonWriter = NULL_JSON_WRITER;

    private int mLoggingState;
    private static final int LOGGING_STATE_UNSTARTED = 0;
    private static final int LOGGING_STATE_READY = 1;   // don't create file until necessary
    private static final int LOGGING_STATE_RUNNING = 2;
    private static final int LOGGING_STATE_STOPPING = 3;
    private static final int LOGGING_STATE_STOPPED = 4;
    private static final long FLUSH_DELAY_IN_MS = 1000 * 5;

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

    public ResearchLog(File outputFile) {
        mExecutor = Executors.newSingleThreadScheduledExecutor();
        if (outputFile == null) {
            throw new IllegalArgumentException();
        }
        mFile = outputFile;
        mLoggingState = LOGGING_STATE_UNSTARTED;
    }

    public synchronized void start() throws IOException {
        switch (mLoggingState) {
            case LOGGING_STATE_UNSTARTED:
                mLoggingState = LOGGING_STATE_READY;
                break;
            case LOGGING_STATE_READY:
            case LOGGING_STATE_RUNNING:
            case LOGGING_STATE_STOPPING:
            case LOGGING_STATE_STOPPED:
                break;
        }
    }

    public synchronized void stop() {
        switch (mLoggingState) {
            case LOGGING_STATE_UNSTARTED:
                mLoggingState = LOGGING_STATE_STOPPED;
                break;
            case LOGGING_STATE_READY:
            case LOGGING_STATE_RUNNING:
                mExecutor.submit(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        try {
                            mJsonWriter.endArray();
                            mJsonWriter.flush();
                            mJsonWriter.close();
                        } finally {
                            boolean success = mFile.setWritable(false, false);
                            mLoggingState = LOGGING_STATE_STOPPED;
                        }
                        return null;
                    }
                });
                removeAnyScheduledFlush();
                mExecutor.shutdown();
                mLoggingState = LOGGING_STATE_STOPPING;
                break;
            case LOGGING_STATE_STOPPING:
            case LOGGING_STATE_STOPPED:
        }
    }

    public boolean isAlive() {
        switch (mLoggingState) {
            case LOGGING_STATE_UNSTARTED:
            case LOGGING_STATE_READY:
            case LOGGING_STATE_RUNNING:
                return true;
        }
        return false;
    }

    public void waitUntilStopped(final int timeoutInMs) throws InterruptedException {
        removeAnyScheduledFlush();
        mExecutor.shutdown();
        mExecutor.awaitTermination(timeoutInMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void abort() {
        switch (mLoggingState) {
            case LOGGING_STATE_UNSTARTED:
                mLoggingState = LOGGING_STATE_STOPPED;
                isAbortSuccessful = true;
                break;
            case LOGGING_STATE_READY:
            case LOGGING_STATE_RUNNING:
                mExecutor.submit(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        try {
                            mJsonWriter.endArray();
                            mJsonWriter.close();
                        } finally {
                            isAbortSuccessful = mFile.delete();
                        }
                        return null;
                    }
                });
                removeAnyScheduledFlush();
                mExecutor.shutdown();
                mLoggingState = LOGGING_STATE_STOPPING;
                break;
            case LOGGING_STATE_STOPPING:
            case LOGGING_STATE_STOPPED:
        }
    }

    private boolean isAbortSuccessful;
    public boolean isAbortSuccessful() {
        return isAbortSuccessful;
    }

    /* package */ synchronized void flush() {
        switch (mLoggingState) {
            case LOGGING_STATE_UNSTARTED:
                break;
            case LOGGING_STATE_READY:
            case LOGGING_STATE_RUNNING:
                removeAnyScheduledFlush();
                mExecutor.submit(mFlushCallable);
                break;
            case LOGGING_STATE_STOPPING:
            case LOGGING_STATE_STOPPED:
        }
    }

    private Callable<Object> mFlushCallable = new Callable<Object>() {
        @Override
        public Object call() throws Exception {
            if (mLoggingState == LOGGING_STATE_RUNNING) {
                mJsonWriter.flush();
            }
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

    public synchronized void publishPublicEvents(final LogUnit logUnit) {
        switch (mLoggingState) {
            case LOGGING_STATE_UNSTARTED:
                break;
            case LOGGING_STATE_READY:
            case LOGGING_STATE_RUNNING:
                mExecutor.submit(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        logUnit.publishPublicEventsTo(ResearchLog.this);
                        scheduleFlush();
                        return null;
                    }
                });
                break;
            case LOGGING_STATE_STOPPING:
            case LOGGING_STATE_STOPPED:
        }
    }

    public synchronized void publishAllEvents(final LogUnit logUnit) {
        switch (mLoggingState) {
            case LOGGING_STATE_UNSTARTED:
                break;
            case LOGGING_STATE_READY:
            case LOGGING_STATE_RUNNING:
                mExecutor.submit(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        logUnit.publishAllEventsTo(ResearchLog.this);
                        scheduleFlush();
                        return null;
                    }
                });
                break;
            case LOGGING_STATE_STOPPING:
            case LOGGING_STATE_STOPPED:
        }
    }

    private static final String CURRENT_TIME_KEY = "_ct";
    private static final String UPTIME_KEY = "_ut";
    private static final String EVENT_TYPE_KEY = "_ty";
    void outputEvent(final String[] keys, final Object[] values) {
        // not thread safe.
        try {
            if (mJsonWriter == NULL_JSON_WRITER) {
                mJsonWriter = new JsonWriter(new BufferedWriter(new FileWriter(mFile)));
                mJsonWriter.setLenient(true);
                mJsonWriter.beginArray();
            }
            mJsonWriter.beginObject();
            mJsonWriter.name(CURRENT_TIME_KEY).value(System.currentTimeMillis());
            mJsonWriter.name(UPTIME_KEY).value(SystemClock.uptimeMillis());
            mJsonWriter.name(EVENT_TYPE_KEY).value(keys[0]);
            final int length = values.length;
            for (int i = 0; i < length; i++) {
                mJsonWriter.name(keys[i + 1]);
                Object value = values[i];
                if (value instanceof String) {
                    mJsonWriter.value((String) value);
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
                        mJsonWriter.name("altCode").value(keyboardKey.mAltCode);
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
                    mJsonWriter.name("willAutoCorrect")
                        .value(words.mWillAutoCorrect);
                    mJsonWriter.name("isPunctuationSuggestions")
                        .value(words.mIsPunctuationSuggestions);
                    mJsonWriter.name("isObsoleteSuggestions")
                        .value(words.mIsObsoleteSuggestions);
                    mJsonWriter.name("isPrediction")
                        .value(words.mIsPrediction);
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
                // assume that this is just the json not being terminated properly.
                // ignore
            } catch (IOException e1) {
                e1.printStackTrace();
            } finally {
                mJsonWriter = NULL_JSON_WRITER;
            }
        }
    }
}
