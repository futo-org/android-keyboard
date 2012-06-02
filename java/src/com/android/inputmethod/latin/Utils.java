/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;

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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Utils {
    private Utils() {
        // This utility class is not publicly instantiable.
    }

    /**
     * Cancel an {@link AsyncTask}.
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
     *        task should be interrupted; otherwise, in-progress tasks are allowed
     *        to complete.
     */
    public static void cancelTask(AsyncTask<?, ?, ?> task, boolean mayInterruptIfRunning) {
        if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
            task.cancel(mayInterruptIfRunning);
        }
    }

    public static class GCUtils {
        private static final String GC_TAG = GCUtils.class.getSimpleName();
        public static final int GC_TRY_COUNT = 2;
        // GC_TRY_LOOP_MAX is used for the hard limit of GC wait,
        // GC_TRY_LOOP_MAX should be greater than GC_TRY_COUNT.
        public static final int GC_TRY_LOOP_MAX = 5;
        private static final long GC_INTERVAL = DateUtils.SECOND_IN_MILLIS;
        private static GCUtils sInstance = new GCUtils();
        private int mGCTryCount = 0;

        public static GCUtils getInstance() {
            return sInstance;
        }

        public void reset() {
            mGCTryCount = 0;
        }

        public boolean tryGCOrWait(String metaData, Throwable t) {
            if (mGCTryCount == 0) {
                System.gc();
            }
            if (++mGCTryCount > GC_TRY_COUNT) {
                LatinImeLogger.logOnException(metaData, t);
                return false;
            } else {
                try {
                    Thread.sleep(GC_INTERVAL);
                    return true;
                } catch (InterruptedException e) {
                    Log.e(GC_TAG, "Sleep was interrupted.");
                    LatinImeLogger.logOnException(metaData, t);
                    return false;
                }
            }
        }
    }

    /* package */ static class RingCharBuffer {
        private static RingCharBuffer sRingCharBuffer = new RingCharBuffer();
        private static final char PLACEHOLDER_DELIMITER_CHAR = '\uFFFC';
        private static final int INVALID_COORDINATE = -2;
        /* package */ static final int BUFSIZE = 20;
        private InputMethodService mContext;
        private boolean mEnabled = false;
        private int mEnd = 0;
        /* package */ int mLength = 0;
        private char[] mCharBuf = new char[BUFSIZE];
        private int[] mXBuf = new int[BUFSIZE];
        private int[] mYBuf = new int[BUFSIZE];

        private RingCharBuffer() {
            // Intentional empty constructor for singleton.
        }
        public static RingCharBuffer getInstance() {
            return sRingCharBuffer;
        }
        public static RingCharBuffer init(InputMethodService context, boolean enabled,
                boolean usabilityStudy) {
            if (!(enabled || usabilityStudy)) return null;
            sRingCharBuffer.mContext = context;
            sRingCharBuffer.mEnabled = true;
            UsabilityStudyLogUtils.getInstance().init(context);
            return sRingCharBuffer;
        }
        private static int normalize(int in) {
            int ret = in % BUFSIZE;
            return ret < 0 ? ret + BUFSIZE : ret;
        }
        // TODO: accept code points
        public void push(char c, int x, int y) {
            if (!mEnabled) return;
            mCharBuf[mEnd] = c;
            mXBuf[mEnd] = x;
            mYBuf[mEnd] = y;
            mEnd = normalize(mEnd + 1);
            if (mLength < BUFSIZE) {
                ++mLength;
            }
        }
        public char pop() {
            if (mLength < 1) {
                return PLACEHOLDER_DELIMITER_CHAR;
            } else {
                mEnd = normalize(mEnd - 1);
                --mLength;
                return mCharBuf[mEnd];
            }
        }
        public char getBackwardNthChar(int n) {
            if (mLength <= n || n < 0) {
                return PLACEHOLDER_DELIMITER_CHAR;
            } else {
                return mCharBuf[normalize(mEnd - n - 1)];
            }
        }
        public int getPreviousX(char c, int back) {
            int index = normalize(mEnd - 2 - back);
            if (mLength <= back
                    || Character.toLowerCase(c) != Character.toLowerCase(mCharBuf[index])) {
                return INVALID_COORDINATE;
            } else {
                return mXBuf[index];
            }
        }
        public int getPreviousY(char c, int back) {
            int index = normalize(mEnd - 2 - back);
            if (mLength <= back
                    || Character.toLowerCase(c) != Character.toLowerCase(mCharBuf[index])) {
                return INVALID_COORDINATE;
            } else {
                return mYBuf[index];
            }
        }
        public String getLastWord(int ignoreCharCount) {
            StringBuilder sb = new StringBuilder();
            int i = ignoreCharCount;
            for (; i < mLength; ++i) {
                char c = mCharBuf[normalize(mEnd - 1 - i)];
                if (!((LatinIME)mContext).isWordSeparator(c)) {
                    break;
                }
            }
            for (; i < mLength; ++i) {
                char c = mCharBuf[normalize(mEnd - 1 - i)];
                if (!((LatinIME)mContext).isWordSeparator(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            return sb.reverse().toString();
        }
        public void reset() {
            mLength = 0;
        }
    }

    // Get the current stack trace
    public static String getStackTrace() {
        StringBuilder sb = new StringBuilder();
        try {
            throw new RuntimeException();
        } catch (RuntimeException e) {
            StackTraceElement[] frames = e.getStackTrace();
            // Start at 1 because the first frame is here and we don't care about it
            for (int j = 1; j < frames.length; ++j) sb.append(frames[j].toString() + "\n");
        }
        return sb.toString();
    }

    public static class UsabilityStudyLogUtils {
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
            mDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss.SSSZ");

            HandlerThread handlerThread = new HandlerThread("UsabilityStudyLogUtils logging task",
                    Process.THREAD_PRIORITY_BACKGROUND);
            handlerThread.start();
            mLoggingHandler = new Handler(handlerThread.getLooper());
        }

        // Initialization-on-demand holder
        private static class OnDemandInitializationHolder {
            public static final UsabilityStudyLogUtils sInstance = new UsabilityStudyLogUtils();
        }

        public static UsabilityStudyLogUtils getInstance() {
            return OnDemandInitializationHolder.sInstance;
        }

        public void init(InputMethodService ims) {
            mIms = ims;
            mDirectory = ims.getFilesDir();
        }

        private void createLogFileIfNotExist() {
            if ((mFile == null || !mFile.exists())
                    && (mDirectory != null && mDirectory.exists())) {
                try {
                    mWriter = getPrintWriter(mDirectory, FILENAME, false);
                } catch (IOException e) {
                    Log.e(USABILITY_TAG, "Can't create log file.");
                }
            }
        }

        public static void writeBackSpace(int x, int y) {
            UsabilityStudyLogUtils.getInstance().write("<backspace>\t" + x + "\t" + y);
        }

        public void writeChar(char c, int x, int y) {
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

        public void write(final String log) {
            mLoggingHandler.post(new Runnable() {
                @Override
                public void run() {
                    createLogFileIfNotExist();
                    final long currentTime = System.currentTimeMillis();
                    mDate.setTime(currentTime);

                    final String printString = String.format("%s\t%d\t%s\n",
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
            StringBuilder sb = new StringBuilder();
            BufferedReader br = getBufferedReader();
            String line;
            try {
                while ((line = br.readLine()) != null) {
                    sb.append('\n');
                    sb.append(line);
                }
            } catch (IOException e) {
                Log.e(USABILITY_TAG, "Can't read log file.");
            } finally {
                if (LatinImeLogger.sDBG) {
                    Log.d(USABILITY_TAG, "Got all buffered logs\n" + sb.toString());
                }
                try {
                    br.close();
                } catch (IOException e) {
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
                            new SimpleDateFormat("yyyyMMdd-HHmmssZ").format(date);
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
                        final FileChannel src = (new FileInputStream(mFile)).getChannel();
                        final FileChannel dest = (new FileOutputStream(destFile)).getChannel();
                        src.transferTo(0, src.size(), dest);
                        src.close();
                        dest.close();
                    } catch (FileNotFoundException e1) {
                        Log.w(USABILITY_TAG, e1);
                        return;
                    } catch (IOException e2) {
                        Log.w(USABILITY_TAG, e2);
                        return;
                    }
                    if (destFile == null || !destFile.exists()) {
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
            } catch (FileNotFoundException e) {
                return null;
            }
        }

        private PrintWriter getPrintWriter(
                File dir, String filename, boolean renew) throws IOException {
            mFile = new File(dir, filename);
            if (mFile.exists()) {
                if (renew) {
                    mFile.delete();
                }
            }
            return new PrintWriter(new FileOutputStream(mFile), true /* autoFlush */);
        }
    }

    public static float getDipScale(Context context) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return scale;
    }

    /** Convert pixel to DIP */
    public static int dipToPixel(float scale, int dip) {
        return (int) (dip * scale + 0.5);
    }

    public static class Stats {
        public static void onNonSeparator(final char code, final int x,
                final int y) {
            RingCharBuffer.getInstance().push(code, x, y);
            LatinImeLogger.logOnInputChar();
        }

        public static void onSeparator(final int code, final int x,
                final int y) {
            // TODO: accept code points
            RingCharBuffer.getInstance().push((char)code, x, y);
            LatinImeLogger.logOnInputSeparator();
        }

        public static void onAutoCorrection(final String typedWord, final String correctedWord,
                final int separatorCode) {
            if (TextUtils.isEmpty(typedWord)) return;
            LatinImeLogger.logOnAutoCorrection(typedWord, correctedWord, separatorCode);
        }

        public static void onAutoCorrectionCancellation() {
            LatinImeLogger.logOnAutoCorrectionCancelled();
        }
    }

    public static String getDebugInfo(final SuggestedWords suggestions, final int pos) {
        if (!LatinImeLogger.sDBG) return null;
        final SuggestedWordInfo wordInfo = suggestions.getInfo(pos);
        if (wordInfo == null) return null;
        final String info = wordInfo.getDebugString();
        if (TextUtils.isEmpty(info)) return null;
        return info;
    }

    private static final String HARDWARE_PREFIX = Build.HARDWARE + ",";
    private static final HashMap<String, String> sDeviceOverrideValueMap =
            new HashMap<String, String>();

    public static String getDeviceOverrideValue(Resources res, int overrideResId, String defValue) {
        final int orientation = res.getConfiguration().orientation;
        final String key = overrideResId + "-" + orientation;
        if (!sDeviceOverrideValueMap.containsKey(key)) {
            String overrideValue = defValue;
            for (final String element : res.getStringArray(overrideResId)) {
                if (element.startsWith(HARDWARE_PREFIX)) {
                    overrideValue = element.substring(HARDWARE_PREFIX.length());
                    break;
                }
            }
            sDeviceOverrideValueMap.put(key, overrideValue);
        }
        return sDeviceOverrideValueMap.get(key);
    }

    private static final HashMap<String, Long> EMPTY_LT_HASH_MAP = new HashMap<String, Long>();
    private static final String LOCALE_AND_TIME_STR_SEPARATER = ",";
    public static HashMap<String, Long> localeAndTimeStrToHashMap(String str) {
        if (TextUtils.isEmpty(str)) {
            return EMPTY_LT_HASH_MAP;
        }
        final String[] ss = str.split(LOCALE_AND_TIME_STR_SEPARATER);
        final int N = ss.length;
        if (N < 2 || N % 2 != 0) {
            return EMPTY_LT_HASH_MAP;
        }
        final HashMap<String, Long> retval = new HashMap<String, Long>();
        for (int i = 0; i < N / 2; ++i) {
            final String localeStr = ss[i * 2];
            final long time = Long.valueOf(ss[i * 2 + 1]);
            retval.put(localeStr, time);
        }
        return retval;
    }

    public static String localeAndTimeHashMapToStr(HashMap<String, Long> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        for (String localeStr : map.keySet()) {
            if (builder.length() > 0) {
                builder.append(LOCALE_AND_TIME_STR_SEPARATER);
            }
            final Long time = map.get(localeStr);
            builder.append(localeStr).append(LOCALE_AND_TIME_STR_SEPARATER);
            builder.append(String.valueOf(time));
        }
        return builder.toString();
    }
}
