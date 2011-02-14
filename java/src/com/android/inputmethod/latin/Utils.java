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

import android.inputmethodservice.InputMethodService;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();
    private static final int MINIMUM_SAFETY_NET_CHAR_LENGTH = 4;
    private static boolean DBG = LatinImeLogger.sDBG;

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
        private static final String TAG = "GCUtils";
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
                    Log.e(TAG, "Sleep was interrupted.");
                    LatinImeLogger.logOnException(metaData, t);
                    return false;
                }
            }
        }
    }

    public static boolean hasMultipleEnabledIMEsOrSubtypes(InputMethodManager imm) {
        return imm.getEnabledInputMethodList().size() > 1
        // imm.getEnabledInputMethodSubtypeList(null, false) will return the current IME's enabled
        // input method subtype (The current IME should be LatinIME.)
                || imm.getEnabledInputMethodSubtypeList(null, false).size() > 1;
    }

    public static String getInputMethodId(InputMethodManager imm, String packageName) {
        for (final InputMethodInfo imi : imm.getEnabledInputMethodList()) {
            if (imi.getPackageName().equals(packageName))
                return imi.getId();
        }
        throw new RuntimeException("Can not find input method id for " + packageName);
    }

    public static boolean shouldBlockedBySafetyNetForAutoCorrection(SuggestedWords suggestions,
            Suggest suggest) {
        // Safety net for auto correction.
        // Actually if we hit this safety net, it's actually a bug.
        if (suggestions.size() <= 1 || suggestions.mTypedWordValid) return false;
        // If user selected aggressive auto correction mode, there is no need to use the safety
        // net.
        if (suggest.isAggressiveAutoCorrectionMode()) return false;
        CharSequence typedWord = suggestions.getWord(0);
        // If the length of typed word is less than MINIMUM_SAFETY_NET_CHAR_LENGTH,
        // we should not use net because relatively edit distance can be big.
        if (typedWord.length() < MINIMUM_SAFETY_NET_CHAR_LENGTH) return false;
        CharSequence candidateWord = suggestions.getWord(1);
        final int typedWordLength = typedWord.length();
        final int maxEditDistanceOfNativeDictionary = typedWordLength < 5 ? 2 : typedWordLength / 2;
        final int distance = Utils.editDistance(typedWord, candidateWord);
        if (DBG) {
            Log.d(TAG, "Autocorrected edit distance = " + distance
                    + ", " + maxEditDistanceOfNativeDictionary);
        }
        if (distance > maxEditDistanceOfNativeDictionary) {
            if (DBG) {
                Log.d(TAG, "Safety net: before = " + typedWord + ", after = " + candidateWord);
                Log.w(TAG, "(Error) The edit distance of this correction exceeds limit. "
                        + "Turning off auto-correction.");
            }
            return true;
        } else {
            return false;
        }
    }

    /* package */ static class RingCharBuffer {
        private static RingCharBuffer sRingCharBuffer = new RingCharBuffer();
        private static final char PLACEHOLDER_DELIMITER_CHAR = '\uFFFC';
        private static final int INVALID_COORDINATE = -2;
        /* package */ static final int BUFSIZE = 20;
        private InputMethodService mContext;
        private boolean mEnabled = false;
        private boolean mUsabilityStudy = false;
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
            sRingCharBuffer.mContext = context;
            sRingCharBuffer.mEnabled = enabled || usabilityStudy;
            sRingCharBuffer.mUsabilityStudy = usabilityStudy;
            UsabilityStudyLogUtils.getInstance().init(context);
            return sRingCharBuffer;
        }
        private int normalize(int in) {
            int ret = in % BUFSIZE;
            return ret < 0 ? ret + BUFSIZE : ret;
        }
        public void push(char c, int x, int y) {
            if (!mEnabled) return;
            if (mUsabilityStudy) {
                UsabilityStudyLogUtils.getInstance().writeChar(c, x, y);
            }
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
        public char getLastChar() {
            if (mLength < 1) {
                return PLACEHOLDER_DELIMITER_CHAR;
            } else {
                return mCharBuf[normalize(mEnd - 1)];
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
        public String getLastString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mLength; ++i) {
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

    public static int editDistance(CharSequence s, CharSequence t) {
        if (s == null || t == null) {
            throw new IllegalArgumentException("editDistance: Arguments should not be null.");
        }
        final int sl = s.length();
        final int tl = t.length();
        int[][] dp = new int [sl + 1][tl + 1];
        for (int i = 0; i <= sl; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= tl; j++) {
            dp[0][j] = j;
        }
        for (int i = 0; i < sl; ++i) {
            for (int j = 0; j < tl; ++j) {
                if (Character.toLowerCase(s.charAt(i)) == Character.toLowerCase(t.charAt(j))) {
                    dp[i + 1][j + 1] = dp[i][j];
                } else {
                    dp[i + 1][j + 1] = 1 + Math.min(dp[i][j],
                            Math.min(dp[i + 1][j], dp[i][j + 1]));
                }
            }
        }
        return dp[sl][tl];
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

    // In dictionary.cpp, getSuggestion() method,
    // suggestion scores are computed using the below formula.
    // original score (called 'frequency')
    //  := pow(mTypedLetterMultiplier (this is defined 2),
    //         (the number of matched characters between typed word and suggested word))
    //     * (individual word's score which defined in the unigram dictionary,
    //         and this score is defined in range [0, 255].)
    //     * (when before.length() == after.length(),
    //         mFullWordMultiplier (this is defined 2))
    // So, maximum original score is pow(2, before.length()) * 255 * 2
    // So, we can normalize original score by dividing this value.
    private static final int MAX_INITIAL_SCORE = 255;
    private static final int TYPED_LETTER_MULTIPLIER = 2;
    private static final int FULL_WORD_MULTIPLYER = 2;
    public static double calcNormalizedScore(CharSequence before, CharSequence after, int score) {
        final int beforeLength = before.length();
        final int afterLength = after.length();
        if (beforeLength == 0 || afterLength == 0) return 0;
        final int distance = editDistance(before, after);
        // If afterLength < beforeLength, the algorithm is suggesting a word by excessive character
        // correction.
        final double maximumScore = MAX_INITIAL_SCORE
                * Math.pow(TYPED_LETTER_MULTIPLIER, Math.min(beforeLength, afterLength))
                * FULL_WORD_MULTIPLYER;
        // add a weight based on edit distance.
        // distance <= max(afterLength, beforeLength) == afterLength,
        // so, 0 <= distance / afterLength <= 1
        final double weight = 1.0 - (double) distance / afterLength;
        return (score / maximumScore) * weight;
    }

    public static class UsabilityStudyLogUtils {
        private static final String TAG = "UsabilityStudyLogUtils";
        private static final String FILENAME = "log.txt";
        private static final UsabilityStudyLogUtils sInstance =
                new UsabilityStudyLogUtils();
        private final Handler mLoggingHandler;
        private File mFile;
        private File mDirectory;
        private InputMethodService mIms;
        private PrintWriter mWriter;
        private final Date mDate;
        private final SimpleDateFormat mDateFormat;

        private UsabilityStudyLogUtils() {
            mDate = new Date();
            mDateFormat = new SimpleDateFormat("dd MMM HH:mm:ss.SSS");

            HandlerThread handlerThread = new HandlerThread("UsabilityStudyLogUtils logging task",
                    Process.THREAD_PRIORITY_BACKGROUND);
            handlerThread.start();
            mLoggingHandler = new Handler(handlerThread.getLooper());
        }

        public static UsabilityStudyLogUtils getInstance() {
            return sInstance;
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
                    Log.e(TAG, "Can't create log file.");
                }
            }
        }

        public void writeBackSpace() {
            UsabilityStudyLogUtils.getInstance().write("<backspace>\t0\t0");
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
                        Log.d(TAG, "Write: " + log);
                    }
                    mWriter.print(printString);
                }
            });
        }

        public void printAll() {
            mLoggingHandler.post(new Runnable() {
                @Override
                public void run() {
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
                        Log.e(TAG, "Can't read log file.");
                    } finally {
                        if (LatinImeLogger.sDBG) {
                            Log.d(TAG, "output all logs\n" + sb.toString());
                        }
                        mIms.getCurrentInputConnection().commitText(sb.toString(), 0);
                        try {
                            br.close();
                        } catch (IOException e) {
                            // ignore.
                        }
                    }
                }
            });
        }

        public void clearAll() {
            mLoggingHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mFile != null && mFile.exists()) {
                        if (LatinImeLogger.sDBG) {
                            Log.d(TAG, "Delete log file.");
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
}
