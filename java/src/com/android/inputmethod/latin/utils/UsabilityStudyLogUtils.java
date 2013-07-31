/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.view.MotionEvent;

import com.android.inputmethod.latin.LatinImeLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class UsabilityStudyLogUtils {
    // TODO: remove code duplication with ResearchLog class
    private static final String USABILITY_TAG = UsabilityStudyLogUtils.class.getSimpleName();
    private static final String FILENAME = "log.txt";
    private final Handler mLoggingHandler;
    private File mFile;
    private File mDirectory;
    private InputMethodService mIms;
    private PrintWriter mWriter;
    private final Date mDate;
    private final SimpleDateFormat mDateFormat;

    private UsabilityStudyLogUtils() {
        mDate = new Date();
        mDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss.SSSZ", Locale.US);

        HandlerThread handlerThread = new HandlerThread("UsabilityStudyLogUtils logging task",
                Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        mLoggingHandler = new Handler(handlerThread.getLooper());
    }

    // Initialization-on-demand holder
    private static final class OnDemandInitializationHolder {
        public static final UsabilityStudyLogUtils sInstance = new UsabilityStudyLogUtils();
    }

    public static UsabilityStudyLogUtils getInstance() {
        return OnDemandInitializationHolder.sInstance;
    }

    public void init(final InputMethodService ims) {
        mIms = ims;
        mDirectory = ims.getFilesDir();
    }

    private void createLogFileIfNotExist() {
        if ((mFile == null || !mFile.exists())
                && (mDirectory != null && mDirectory.exists())) {
            try {
                mWriter = getPrintWriter(mDirectory, FILENAME, false);
            } catch (final IOException e) {
                Log.e(USABILITY_TAG, "Can't create log file.");
            }
        }
    }

    public static void writeBackSpace(final int x, final int y) {
        UsabilityStudyLogUtils.getInstance().write("<backspace>\t" + x + "\t" + y);
    }

    public static void writeChar(final char c, final int x, final int y) {
        String inputChar = String.valueOf(c);
        switch (c) {
            case '\n':
                inputChar = "<enter>";
                break;
            case '\t':
                inputChar = "<tab>";
                break;
            case ' ':
                inputChar = "<space>";
                break;
        }
        UsabilityStudyLogUtils.getInstance().write(inputChar + "\t" + x + "\t" + y);
        LatinImeLogger.onPrintAllUsabilityStudyLogs();
    }

    public static void writeMotionEvent(final MotionEvent me) {
        final int action = me.getActionMasked();
        final long eventTime = me.getEventTime();
        final int pointerCount = me.getPointerCount();
        for (int index = 0; index < pointerCount; index++) {
            final int id = me.getPointerId(index);
            final int x = (int)me.getX(index);
            final int y = (int)me.getY(index);
            final float size = me.getSize(index);
            final float pressure = me.getPressure(index);

            final String eventTag;
            switch (action) {
            case MotionEvent.ACTION_UP:
                eventTag = "[Up]";
                break;
            case MotionEvent.ACTION_DOWN:
                eventTag = "[Down]";
                break;
            case MotionEvent.ACTION_POINTER_UP:
                eventTag = "[PointerUp]";
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                eventTag = "[PointerDown]";
                break;
            case MotionEvent.ACTION_MOVE:
                eventTag = "[Move]";
                break;
            default:
                eventTag = "[Action" + action + "]";
                break;
            }
            getInstance().write(eventTag + eventTime + "," + id + "," + x + "," + y + "," + size
                    + "," + pressure);
        }
    }

    public void write(final String log) {
        mLoggingHandler.post(new Runnable() {
            @Override
            public void run() {
                createLogFileIfNotExist();
                final long currentTime = System.currentTimeMillis();
                mDate.setTime(currentTime);

                final String printString = String.format(Locale.US, "%s\t%d\t%s\n",
                        mDateFormat.format(mDate), currentTime, log);
                if (LatinImeLogger.sDBG) {
                    Log.d(USABILITY_TAG, "Write: " + log);
                }
                mWriter.print(printString);
            }
        });
    }

    private synchronized String getBufferedLogs() {
        mWriter.flush();
        final StringBuilder sb = new StringBuilder();
        final BufferedReader br = getBufferedReader();
        String line;
        try {
            while ((line = br.readLine()) != null) {
                sb.append('\n');
                sb.append(line);
            }
        } catch (final IOException e) {
            Log.e(USABILITY_TAG, "Can't read log file.");
        } finally {
            if (LatinImeLogger.sDBG) {
                Log.d(USABILITY_TAG, "Got all buffered logs\n" + sb.toString());
            }
            try {
                br.close();
            } catch (final IOException e) {
                // ignore.
            }
        }
        return sb.toString();
    }

    public void emailResearcherLogsAll() {
        mLoggingHandler.post(new Runnable() {
            @Override
            public void run() {
                final Date date = new Date();
                date.setTime(System.currentTimeMillis());
                final String currentDateTimeString =
                        new SimpleDateFormat("yyyyMMdd-HHmmssZ", Locale.US).format(date);
                if (mFile == null) {
                    Log.w(USABILITY_TAG, "No internal log file found.");
                    return;
                }
                if (mIms.checkCallingOrSelfPermission(
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(USABILITY_TAG, "Doesn't have the permission WRITE_EXTERNAL_STORAGE");
                    return;
                }
                mWriter.flush();
                final String destPath = Environment.getExternalStorageDirectory()
                        + "/research-" + currentDateTimeString + ".log";
                final File destFile = new File(destPath);
                try {
                    final FileInputStream srcStream = new FileInputStream(mFile);
                    final FileOutputStream destStream = new FileOutputStream(destFile);
                    final FileChannel src = srcStream.getChannel();
                    final FileChannel dest = destStream.getChannel();
                    src.transferTo(0, src.size(), dest);
                    src.close();
                    srcStream.close();
                    dest.close();
                    destStream.close();
                } catch (final FileNotFoundException e1) {
                    Log.w(USABILITY_TAG, e1);
                    return;
                } catch (final IOException e2) {
                    Log.w(USABILITY_TAG, e2);
                    return;
                }
                if (!destFile.exists()) {
                    Log.w(USABILITY_TAG, "Dest file doesn't exist.");
                    return;
                }
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (LatinImeLogger.sDBG) {
                    Log.d(USABILITY_TAG, "Destination file URI is " + destFile.toURI());
                }
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + destPath));
                intent.putExtra(Intent.EXTRA_SUBJECT,
                        "[Research Logs] " + currentDateTimeString);
                mIms.startActivity(intent);
            }
        });
    }

    public void printAll() {
        mLoggingHandler.post(new Runnable() {
            @Override
            public void run() {
                mIms.getCurrentInputConnection().commitText(getBufferedLogs(), 0);
            }
        });
    }

    public void clearAll() {
        mLoggingHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mFile != null && mFile.exists()) {
                    if (LatinImeLogger.sDBG) {
                        Log.d(USABILITY_TAG, "Delete log file.");
                    }
                    mFile.delete();
                    mWriter.close();
                }
            }
        });
    }

    private BufferedReader getBufferedReader() {
        createLogFileIfNotExist();
        try {
            return new BufferedReader(new FileReader(mFile));
        } catch (final FileNotFoundException e) {
            return null;
        }
    }

    private PrintWriter getPrintWriter(final File dir, final String filename,
            final boolean renew) throws IOException {
        mFile = new File(dir, filename);
        if (mFile.exists()) {
            if (renew) {
                mFile.delete();
            }
        }
        return new PrintWriter(new FileOutputStream(mFile), true /* autoFlush */);
    }
}
