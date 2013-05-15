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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
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
import java.util.Date;
import java.util.Locale;

public final class Utils {
    private static final String TAG = Utils.class.getSimpleName();

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

    /* package */ static final class RingCharBuffer {
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
        @UsedForTesting
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
        @UsedForTesting
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
    public static String getStackTrace(final int limit) {
        StringBuilder sb = new StringBuilder();
        try {
            throw new RuntimeException();
        } catch (RuntimeException e) {
            StackTraceElement[] frames = e.getStackTrace();
            // Start at 1 because the first frame is here and we don't care about it
            for (int j = 1; j < frames.length && j < limit + 1; ++j) {
                sb.append(frames[j].toString() + "\n");
            }
        }
        return sb.toString();
    }

    public static String getStackTrace() {
        return getStackTrace(Integer.MAX_VALUE - 1);
    }

    public static final class UsabilityStudyLogUtils {
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

    public static final class Stats {
        public static void onNonSeparator(final char code, final int x,
                final int y) {
            RingCharBuffer.getInstance().push(code, x, y);
            LatinImeLogger.logOnInputChar();
        }

        public static void onSeparator(final int code, final int x, final int y) {
            // Helper method to log a single code point separator
            // TODO: cache this mapping of a code point to a string in a sparse array in StringUtils
            onSeparator(new String(new int[]{code}, 0, 1), x, y);
        }

        public static void onSeparator(final String separator, final int x, final int y) {
            final int length = separator.length();
            for (int i = 0; i < length; i = Character.offsetByCodePoints(separator, i, 1)) {
                int codePoint = Character.codePointAt(separator, i);
                // TODO: accept code points
                RingCharBuffer.getInstance().push((char)codePoint, x, y);
            }
            LatinImeLogger.logOnInputSeparator();
        }

        public static void onAutoCorrection(final String typedWord, final String correctedWord,
                final String separatorString, final WordComposer wordComposer) {
            final boolean isBatchMode = wordComposer.isBatchMode();
            if (!isBatchMode && TextUtils.isEmpty(typedWord)) return;
            // TODO: this fails when the separator is more than 1 code point long, but
            // the backend can't handle it yet. The only case when this happens is with
            // smileys and other multi-character keys.
            final int codePoint = TextUtils.isEmpty(separatorString) ? Constants.NOT_A_CODE
                    : separatorString.codePointAt(0);
            if (!isBatchMode) {
                LatinImeLogger.logOnAutoCorrectionForTyping(typedWord, correctedWord, codePoint);
            } else {
                if (!TextUtils.isEmpty(correctedWord)) {
                    // We must make sure that InputPointer contains only the relative timestamps,
                    // not actual timestamps.
                    LatinImeLogger.logOnAutoCorrectionForGeometric(
                            "", correctedWord, codePoint, wordComposer.getInputPointers());
                }
            }
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

    public static int getAcitivityTitleResId(Context context, Class<? extends Activity> cls) {
        final ComponentName cn = new ComponentName(context, cls);
        try {
            final ActivityInfo ai = context.getPackageManager().getActivityInfo(cn, 0);
            if (ai != null) {
                return ai.labelRes;
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Failed to get settings activity title res id.", e);
        }
        return 0;
    }

    public static String getVersionName(Context context) {
        try {
            if (context == null) {
                return "";
            }
            final String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.", e);
        }
        return "";
    }
}
