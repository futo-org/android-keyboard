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
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;

import com.android.inputmethod.keyboard.Keyboard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logs the use of the LatinIME keyboard.
 *
 * This class logs operations on the IME keyboard, including what the user has typed.
 * Data is stored locally in a file in app-specific storage.
 *
 * This functionality is off by default. See {@link ProductionFlag.IS_EXPERIMENTAL}.
 */
public class ResearchLogger implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = ResearchLogger.class.getSimpleName();
    private static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";

    private static final ResearchLogger sInstance = new ResearchLogger(new LogFileManager());
    public static boolean sIsLogging = false;
    /* package */ final Handler mLoggingHandler;
    private InputMethodService mIms;
    private final Date mDate;
    private final SimpleDateFormat mDateFormat;

    /**
     * Isolates management of files. This variable should never be null, but can be changed
     * to support testing.
     */
    private LogFileManager mLogFileManager;

    /**
     * Manages the file(s) that stores the logs.
     *
     * Handles creation, deletion, and provides Readers, Writers, and InputStreams to access
     * the logs.
     */
    public static class LogFileManager {
        private static final String DEFAULT_FILENAME = "log.txt";
        private static final String DEFAULT_LOG_DIRECTORY = "researchLogger";

        private static final long LOGFILE_PURGE_INTERVAL = 1000 * 60 * 60 * 24;

        private InputMethodService mIms;
        private File mFile;
        private PrintWriter mPrintWriter;

        /* package */ LogFileManager() {
        }

        public void init(InputMethodService ims) {
            mIms = ims;
        }

        public synchronized void createLogFile() {
            try {
                createLogFile(DEFAULT_LOG_DIRECTORY, DEFAULT_FILENAME);
            } catch (FileNotFoundException e) {
                Log.w(TAG, e);
            }
        }

        public synchronized void createLogFile(String dir, String filename)
                throws FileNotFoundException {
            if (mIms == null) {
                Log.w(TAG, "InputMethodService is not configured.  Logging is off.");
                return;
            }
            File filesDir = mIms.getFilesDir();
            if (filesDir == null || !filesDir.exists()) {
                Log.w(TAG, "Storage directory does not exist.  Logging is off.");
                return;
            }
            File directory = new File(filesDir, dir);
            if (!directory.exists()) {
                boolean wasCreated = directory.mkdirs();
                if (!wasCreated) {
                    Log.w(TAG, "Log directory cannot be created.  Logging is off.");
                    return;
                }
            }

            close();
            mFile = new File(directory, filename);
            boolean append = true;
            if (mFile.exists() && mFile.lastModified() + LOGFILE_PURGE_INTERVAL <
                    System.currentTimeMillis()) {
                append = false;
            }
            mPrintWriter = new PrintWriter(new FileOutputStream(mFile, append), true);
        }

        public synchronized boolean append(String s) {
            if (mPrintWriter == null) {
                Log.w(TAG, "PrintWriter is null");
                return false;
            } else {
                mPrintWriter.print(s);
                return !mPrintWriter.checkError();
            }
        }

        public synchronized void reset() {
            if (mPrintWriter != null) {
                mPrintWriter.close();
                mPrintWriter = null;
            }
            if (mFile != null && mFile.exists()) {
                mFile.delete();
                mFile = null;
            }
        }

        public synchronized void close() {
            if (mPrintWriter != null) {
                mPrintWriter.close();
                mPrintWriter = null;
                mFile = null;
            }
        }
    }

    private ResearchLogger(LogFileManager logFileManager) {
        mDate = new Date();
        mDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss.SSSZ");

        HandlerThread handlerThread = new HandlerThread("ResearchLogger logging task",
                Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        mLoggingHandler = new Handler(handlerThread.getLooper());
        mLogFileManager = logFileManager;
    }

    public static ResearchLogger getInstance() {
        return sInstance;
    }

    public static void init(InputMethodService ims, SharedPreferences prefs) {
        sInstance.initInternal(ims, prefs);
    }

    public void initInternal(InputMethodService ims, SharedPreferences prefs) {
        mIms = ims;
        if (mLogFileManager != null) {
            mLogFileManager.init(ims);
            mLogFileManager.createLogFile();
        }
        if (prefs != null) {
            sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
        }
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Change to a different logFileManager.
     *
     * @throws IllegalArgumentException if logFileManager is null
     */
    void setLogFileManager(LogFileManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("warning: trying to set null logFileManager");
        } else {
            mLogFileManager = manager;
        }
    }

    /**
     * Represents a category of logging events that share the same subfield structure.
     */
    private static enum LogGroup {
        MOTION_EVENT("m"),
        KEY("k"),
        CORRECTION("c"),
        STATE_CHANGE("s");

        private final String mLogString;

        private LogGroup(String logString) {
            mLogString = logString;
        }
    }

    public void logMotionEvent(final int action, final long eventTime, final int id,
            final int x, final int y, final float size, final float pressure) {
        final String eventTag;
        switch (action) {
            case MotionEvent.ACTION_CANCEL: eventTag = "[Cancel]"; break;
            case MotionEvent.ACTION_UP: eventTag = "[Up]"; break;
            case MotionEvent.ACTION_DOWN: eventTag = "[Down]"; break;
            case MotionEvent.ACTION_POINTER_UP: eventTag = "[PointerUp]"; break;
            case MotionEvent.ACTION_POINTER_DOWN: eventTag = "[PointerDown]"; break;
            case MotionEvent.ACTION_MOVE: eventTag = "[Move]"; break;
            case MotionEvent.ACTION_OUTSIDE: eventTag = "[Outside]"; break;
            default: eventTag = "[Action" + action + "]"; break;
        }
        if (!TextUtils.isEmpty(eventTag)) {
            StringBuilder sb = new StringBuilder();
            sb.append(eventTag);
            sb.append('\t'); sb.append(eventTime);
            sb.append('\t'); sb.append(id);
            sb.append('\t'); sb.append(x);
            sb.append('\t'); sb.append(y);
            sb.append('\t'); sb.append(size);
            sb.append('\t'); sb.append(pressure);
            write(LogGroup.MOTION_EVENT, sb.toString());
        }
    }

    public void logKeyEvent(int code, int x, int y) {
        final StringBuilder sb = new StringBuilder();
        sb.append(Keyboard.printableCode(code));
        sb.append('\t'); sb.append(x);
        sb.append('\t'); sb.append(y);
        write(LogGroup.KEY, sb.toString());
    }

    public void logCorrection(String subgroup, String before, String after, int position) {
        final StringBuilder sb = new StringBuilder();
        sb.append(subgroup);
        sb.append('\t'); sb.append(before);
        sb.append('\t'); sb.append(after);
        sb.append('\t'); sb.append(position);
        write(LogGroup.CORRECTION, sb.toString());
    }

    public void logStateChange(String subgroup, String details) {
        write(LogGroup.STATE_CHANGE, subgroup + "\t" + details);
    }

    private void write(final LogGroup logGroup, final String log) {
        mLoggingHandler.post(new Runnable() {
            @Override
            public void run() {
                final long currentTime = System.currentTimeMillis();
                mDate.setTime(currentTime);
                final long upTime = SystemClock.uptimeMillis();

                final String printString = String.format("%s\t%d\t%s\t%s\n",
                        mDateFormat.format(mDate), upTime, logGroup.mLogString, log);
                if (LatinImeLogger.sDBG) {
                    Log.d(TAG, "Write: " + '[' + logGroup.mLogString + ']' + log);
                }
                if (mLogFileManager.append(printString)) {
                    // success
                } else {
                    if (LatinImeLogger.sDBG) {
                        Log.w(TAG, "Unable to write to log.");
                    }
                }
            }
        });
    }

    public void clearAll() {
        mLoggingHandler.post(new Runnable() {
            @Override
            public void run() {
                if (LatinImeLogger.sDBG) {
                    Log.d(TAG, "Delete log file.");
                }
                mLogFileManager.reset();
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null || prefs == null) {
            return;
        }
        sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
    }
}
