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
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.DropBoxManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class LatinImeLogger implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "LatinIMELogs";
    public static boolean sDBG = false;
    private static boolean sLOGPRINT = false;
    // SUPPRESS_EXCEPTION should be true when released to public.
    private static final boolean SUPPRESS_EXCEPTION = true;
    // DEFAULT_LOG_ENABLED should be false when released to public.
    private static final boolean DEFAULT_LOG_ENABLED = true;

    private static final long MINIMUMSENDINTERVAL = 300 * DateUtils.SECOND_IN_MILLIS; // 300 sec
    private static final long MINIMUMCOUNTINTERVAL = 20 * DateUtils.SECOND_IN_MILLIS; // 20 sec
    private static final long MINIMUMSENDSIZE = 40;
    private static final char SEPARATER = ';';
    private static final char NULL_CHAR = '\uFFFC';
    private static final int EXCEPTION_MAX_LENGTH = 400;

    private static final int ID_MANUALSUGGESTION = 0;
    private static final int ID_AUTOSUGGESTIONCANCELLED = 1;
    private static final int ID_AUTOSUGGESTION = 2;
    private static final int ID_INPUT_COUNT = 3;
    private static final int ID_DELETE_COUNT = 4;
    private static final int ID_WORD_COUNT = 5;
    private static final int ID_ACTUAL_CHAR_COUNT = 6;
    private static final int ID_THEME_ID = 7;
    private static final int ID_SETTING_AUTO_COMPLETE = 8;
    private static final int ID_VERSION = 9;
    private static final int ID_EXCEPTION = 10;
    private static final int ID_MANUALSUGGESTIONCOUNT = 11;
    private static final int ID_AUTOSUGGESTIONCANCELLEDCOUNT = 12;
    private static final int ID_AUTOSUGGESTIONCOUNT = 13;

    private static final String PREF_ENABLE_LOG = "enable_logging";
    private static final String PREF_DEBUG_MODE = "debug_mode";
    private static final String PREF_AUTO_COMPLETE = "auto_complete";

    public static boolean sLogEnabled = true;
    /* package */ static LatinImeLogger sLatinImeLogger = new LatinImeLogger();
    // Store the last auto suggested word.
    // This is required for a cancellation log of auto suggestion of that word.
    /* package */ static String sLastAutoSuggestBefore;
    /* package */ static String sLastAutoSuggestAfter;
    /* package */ static String sLastAutoSuggestSeparator;
    private static int sLastAutoSuggestDicTypeId;
    private static HashMap<String, Integer> sSuggestDicMap = new HashMap<String, Integer>();
    private static DebugKeyEnabler sDebugKeyEnabler = new DebugKeyEnabler();

    private ArrayList<LogEntry> mLogBuffer = null;
    private ArrayList<LogEntry> mPrivacyLogBuffer = null;
    /* package */ RingCharBuffer mRingCharBuffer = null;

    private Context mContext = null;
    private DropBoxManager mDropBox = null;
    private long mLastTimeActive;
    private long mLastTimeSend;
    private long mLastTimeCountEntry;

    private String mThemeId;
    private int mDeleteCount;
    private int mInputCount;
    private int mWordCount;
    private int[] mAutoSuggestCountPerDic = new int[Suggest.DIC_TYPE_LAST_ID + 1];
    private int[] mManualSuggestCountPerDic = new int[Suggest.DIC_TYPE_LAST_ID + 1];
    private int[] mAutoCancelledCountPerDic = new int[Suggest.DIC_TYPE_LAST_ID + 1];
    private int mActualCharCount;

    private static class LogEntry implements Comparable<LogEntry> {
        public final int mTag;
        public final String[] mData;
        public long mTime;

        public LogEntry (long time, int tag, String[] data) {
            mTag = tag;
            mTime = time;
            mData = data;
        }

        public int compareTo(LogEntry log2) {
            if (mData.length == 0 && log2.mData.length == 0) {
                return 0;
            } else if (mData.length == 0) {
                return 1;
            } else if (log2.mData.length == 0) {
                return -1;
            }
            return log2.mData[0].compareTo(mData[0]);
        }
    }

    private void initInternal(Context context) {
        mContext = context;
        mDropBox = (DropBoxManager) mContext.getSystemService(Context.DROPBOX_SERVICE);
        mLastTimeSend = System.currentTimeMillis();
        mLastTimeActive = mLastTimeSend;
        mLastTimeCountEntry = mLastTimeSend;
        mDeleteCount = 0;
        mInputCount = 0;
        mWordCount = 0;
        mActualCharCount = 0;
        Arrays.fill(mAutoSuggestCountPerDic, 0);
        Arrays.fill(mManualSuggestCountPerDic, 0);
        Arrays.fill(mAutoCancelledCountPerDic, 0);
        mLogBuffer = new ArrayList<LogEntry>();
        mPrivacyLogBuffer = new ArrayList<LogEntry>();
        mRingCharBuffer = new RingCharBuffer(context);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        sLogEnabled = prefs.getBoolean(PREF_ENABLE_LOG, DEFAULT_LOG_ENABLED);
        mThemeId = prefs.getString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT,
                KeyboardSwitcher.DEFAULT_LAYOUT_ID);
        sLOGPRINT = prefs.getBoolean(PREF_DEBUG_MODE, sLOGPRINT);
        sDBG = sLOGPRINT;
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Clear all logged data
     */
    private void reset() {
        mDeleteCount = 0;
        mInputCount = 0;
        mWordCount = 0;
        mActualCharCount = 0;
        Arrays.fill(mAutoSuggestCountPerDic, 0);
        Arrays.fill(mManualSuggestCountPerDic, 0);
        Arrays.fill(mAutoCancelledCountPerDic, 0);
        mLogBuffer.clear();
        mPrivacyLogBuffer.clear();
        mRingCharBuffer.reset();
    }

    /**
     * Check if the input string is safe as an entry or not.
     */
    private static boolean checkStringDataSafe(String s) {
        if (sDBG) {
            Log.d(TAG, "Check String safety: " + s);
        }
        for (int i = 0; i < s.length(); ++i) {
            if (Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void addCountEntry(long time) {
        if (sLOGPRINT) {
            Log.d(TAG, "Log counts. (4)");
        }
        mLogBuffer.add(new LogEntry (time, ID_DELETE_COUNT,
                new String[] {String.valueOf(mDeleteCount)}));
        mLogBuffer.add(new LogEntry (time, ID_INPUT_COUNT,
                new String[] {String.valueOf(mInputCount)}));
        mLogBuffer.add(new LogEntry (time, ID_WORD_COUNT,
                new String[] {String.valueOf(mWordCount)}));
        mLogBuffer.add(new LogEntry (time, ID_ACTUAL_CHAR_COUNT,
                new String[] {String.valueOf(mActualCharCount)}));
        mDeleteCount = 0;
        mInputCount = 0;
        mWordCount = 0;
        mActualCharCount = 0;
        mLastTimeCountEntry = time;
    }

    private void addSuggestionCountEntry(long time) {
        if (sLOGPRINT) {
            Log.d(TAG, "log suggest counts. (1)");
        }
        String[] s = new String[mAutoSuggestCountPerDic.length];
        for (int i = 0; i < s.length; ++i) {
            s[i] = String.valueOf(mAutoSuggestCountPerDic[i]);
        }
        mLogBuffer.add(new LogEntry(time, ID_AUTOSUGGESTIONCOUNT, s));

        s = new String[mAutoCancelledCountPerDic.length];
        for (int i = 0; i < s.length; ++i) {
            s[i] = String.valueOf(mAutoCancelledCountPerDic[i]);
        }
        mLogBuffer.add(new LogEntry(time, ID_AUTOSUGGESTIONCANCELLEDCOUNT, s));

        s = new String[mManualSuggestCountPerDic.length];
        for (int i = 0; i < s.length; ++i) {
            s[i] = String.valueOf(mManualSuggestCountPerDic[i]);
        }
        mLogBuffer.add(new LogEntry(time, ID_MANUALSUGGESTIONCOUNT, s));

        Arrays.fill(mAutoSuggestCountPerDic, 0);
        Arrays.fill(mManualSuggestCountPerDic, 0);
        Arrays.fill(mAutoCancelledCountPerDic, 0);
    }

    private void addThemeIdEntry(long time) {
        if (sLOGPRINT) {
            Log.d(TAG, "Log theme Id. (1)");
        }
        mLogBuffer.add(new LogEntry (time, ID_THEME_ID,
                new String[] {mThemeId}));
    }

    private void addSettingsEntry(long time) {
        if (sLOGPRINT) {
            Log.d(TAG, "Log settings. (1)");
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mLogBuffer.add(new LogEntry (time, ID_SETTING_AUTO_COMPLETE,
                new String[] {String.valueOf(prefs.getBoolean(PREF_AUTO_COMPLETE,
                        mContext.getResources().getBoolean(R.bool.enable_autocorrect)))}));
    }

    private void addVersionNameEntry(long time) {
        if (sLOGPRINT) {
            Log.d(TAG, "Log Version. (1)");
        }
        try {
            PackageInfo info = mContext.getPackageManager().getPackageInfo(
                    mContext.getPackageName(), 0);
            mLogBuffer.add(new LogEntry (time, ID_VERSION,
                    new String[] {String.valueOf(info.versionCode), info.versionName}));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not find version name.");
        }
    }

    private void addExceptionEntry(long time, String[] data) {
        if (sLOGPRINT) {
            Log.d(TAG, "Log Exception. (1)");
        }
        mLogBuffer.add(new LogEntry(time, ID_EXCEPTION, data));
    }

    private void flushPrivacyLogSafely() {
        if (sLOGPRINT) {
            Log.d(TAG, "Log obfuscated data. (" + mPrivacyLogBuffer.size() + ")");
        }
        long now = System.currentTimeMillis();
        Collections.sort(mPrivacyLogBuffer);
        for (LogEntry l: mPrivacyLogBuffer) {
            l.mTime = now;
            mLogBuffer.add(l);
        }
        mPrivacyLogBuffer.clear();
    }

    /**
     * Add an entry
     * @param tag
     * @param data
     */
    private void addData(int tag, Object data) {
        switch (tag) {
            case ID_DELETE_COUNT:
                if (((mLastTimeActive - mLastTimeCountEntry) > MINIMUMCOUNTINTERVAL)
                        || (mDeleteCount == 0 && mInputCount == 0)) {
                    addCountEntry(mLastTimeActive);
                }
                mDeleteCount += (Integer)data;
                break;
            case ID_INPUT_COUNT:
                if (((mLastTimeActive - mLastTimeCountEntry) > MINIMUMCOUNTINTERVAL)
                        || (mDeleteCount == 0 && mInputCount == 0)) {
                    addCountEntry(mLastTimeActive);
                }
                mInputCount += (Integer)data;
                break;
            case ID_MANUALSUGGESTION:
            case ID_AUTOSUGGESTION:
                ++mWordCount;
                String[] dataStrings = (String[]) data;
                if (dataStrings.length < 2) {
                    if (sDBG) {
                        Log.e(TAG, "The length of logged string array is invalid.");
                    }
                    break;
                }
                mActualCharCount += dataStrings[1].length();
                if (checkStringDataSafe(dataStrings[0]) && checkStringDataSafe(dataStrings[1])) {
                    mPrivacyLogBuffer.add(
                            new LogEntry (System.currentTimeMillis(), tag, dataStrings));
                } else {
                    if (sDBG) {
                        Log.d(TAG, "Skipped to add an entry because data is unsafe.");
                    }
                }
                break;
            case ID_AUTOSUGGESTIONCANCELLED:
                --mWordCount;
                dataStrings = (String[]) data;
                if (dataStrings.length < 2) {
                    if (sDBG) {
                        Log.e(TAG, "The length of logged string array is invalid.");
                    }
                    break;
                }
                mActualCharCount -= dataStrings[1].length();
                if (checkStringDataSafe(dataStrings[0]) && checkStringDataSafe(dataStrings[1])) {
                    mPrivacyLogBuffer.add(
                            new LogEntry (System.currentTimeMillis(), tag, dataStrings));
                } else {
                    if (sDBG) {
                        Log.d(TAG, "Skipped to add an entry because data is unsafe.");
                    }
                }
                break;
            case ID_EXCEPTION:
                dataStrings = (String[]) data;
                if (dataStrings.length < 2) {
                    if (sDBG) {
                        Log.e(TAG, "The length of logged string array is invalid.");
                    }
                    break;
                }
                addExceptionEntry(System.currentTimeMillis(), dataStrings);
                break;
            default:
                if (sDBG) {
                    Log.e(TAG, "Log Tag is not entried.");
                }
                break;
        }
    }

    private void commitInternal() {
        if (sLOGPRINT) {
            Log.d(TAG, "Commit (" + mLogBuffer.size() + ")");
        }
        flushPrivacyLogSafely();
        long now = System.currentTimeMillis();
        addCountEntry(now);
        addThemeIdEntry(now);
        addSettingsEntry(now);
        addVersionNameEntry(now);
        addSuggestionCountEntry(now);
        String s = LogSerializer.createStringFromEntries(mLogBuffer);
        if (!TextUtils.isEmpty(s)) {
            if (sLOGPRINT) {
                Log.d(TAG, "Commit log: " + s);
            }
            mDropBox.addText(TAG, s);
        }
        reset();
        mLastTimeSend = now;
    }

    private void commitInternalAndStopSelf() {
        if (sDBG) {
            Log.e(TAG, "Exception was thrown and let's die.");
        }
        commitInternal();
        LatinIME ime = ((LatinIME) mContext);
        ime.hideWindow();
        ime.stopSelf();
    }

    private synchronized void sendLogToDropBox(int tag, Object s) {
        long now = System.currentTimeMillis();
        if (sDBG) {
            String out = "";
            if (s instanceof String[]) {
                for (String str: ((String[]) s)) {
                    out += str + ",";
                }
            } else if (s instanceof Integer) {
                out += (Integer) s;
            }
            Log.d(TAG, "SendLog: " + tag + ";" + out + ", will be sent after "
                    + (- (now - mLastTimeSend - MINIMUMSENDINTERVAL) / 1000) + " sec.");
        }
        if (now - mLastTimeActive > MINIMUMSENDINTERVAL) {
            // Send a log before adding an log entry if the last data is too old.
            commitInternal();
            addData(tag, s);
        } else if (now - mLastTimeSend > MINIMUMSENDINTERVAL) {
            // Send a log after adding an log entry.
            addData(tag, s);
            commitInternal();
        } else {
            addData(tag, s);
        }
        mLastTimeActive = now;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_ENABLE_LOG.equals(key)) {
            if (sharedPreferences.getBoolean(key, DEFAULT_LOG_ENABLED)) {
                sLogEnabled = (mContext != null);
            } else {
                sLogEnabled = false;
            }
            if (sDebugKeyEnabler.check()) {
                sharedPreferences.edit().putBoolean(PREF_DEBUG_MODE, true).commit();
            }
        } else if (KeyboardSwitcher.PREF_KEYBOARD_LAYOUT.equals(key)) {
            mThemeId = sharedPreferences.getString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT,
                    KeyboardSwitcher.DEFAULT_LAYOUT_ID);
            addThemeIdEntry(mLastTimeActive);
        } else if (PREF_DEBUG_MODE.equals(key)) {
            sLOGPRINT = sharedPreferences.getBoolean(PREF_DEBUG_MODE, sLOGPRINT);
            sDBG = sLOGPRINT;
        }
    }

    public static void init(Context context) {
        sLatinImeLogger.initInternal(context);
    }

    public static void commit() {
        if (sLogEnabled) {
            if (System.currentTimeMillis() - sLatinImeLogger.mLastTimeActive > MINIMUMCOUNTINTERVAL
                        || (sLatinImeLogger.mLogBuffer.size()
                                + sLatinImeLogger.mPrivacyLogBuffer.size() > MINIMUMSENDSIZE)) {
                sLatinImeLogger.commitInternal();
            }
        }
    }

    // TODO: Handle CharSequence instead of String
    public static void logOnManualSuggestion(String before, String after, int position
            , List<CharSequence> suggestions) {
        if (sLogEnabled) {
            // log punctuation
            if (before.length() == 0 && after.length() == 1) {
                sLatinImeLogger.sendLogToDropBox(ID_MANUALSUGGESTION, new String[] {
                        before, after, String.valueOf(position), ""});
            } else if (!sSuggestDicMap.containsKey(after)) {
                if (sDBG) {
                    Log.e(TAG, "logOnManualSuggestion was cancelled: from unknown dic.");
                }
            } else {
                int dicTypeId = sSuggestDicMap.get(after);
                sLatinImeLogger.mManualSuggestCountPerDic[dicTypeId]++;
                if (dicTypeId != Suggest.DIC_MAIN) {
                    if (sDBG) {
                        Log.d(TAG, "logOnManualSuggestion was cancelled: not from main dic.");
                    }
                    before = "";
                    after = "";
                }
                // TODO: Don't send a log if this doesn't come from Main Dictionary.
                {
                    if (before.equals(after)) {
                        before = "";
                        after = "";
                    }
                    String[] strings = new String[3 + suggestions.size()];
                    strings[0] = before;
                    strings[1] = after;
                    strings[2] = String.valueOf(position);
                    for (int i = 0; i < suggestions.size(); ++i) {
                        String s = suggestions.get(i).toString();
                        strings[i + 3] = sSuggestDicMap.containsKey(s) ? s : "";
                    }
                    sLatinImeLogger.sendLogToDropBox(ID_MANUALSUGGESTION, strings);
                }
            }
            sSuggestDicMap.clear();
        }
    }

    public static void logOnAutoSuggestion(String before, String after) {
        if (sLogEnabled) {
            if (!sSuggestDicMap.containsKey(after)) {
                if (sDBG) {
                    Log.e(TAG, "logOnAutoSuggestion was cancelled: from unknown dic.");
                }
            } else {
                String separator = String.valueOf(sLatinImeLogger.mRingCharBuffer.getLastChar());
                sLastAutoSuggestDicTypeId = sSuggestDicMap.get(after);
                sLatinImeLogger.mAutoSuggestCountPerDic[sLastAutoSuggestDicTypeId]++;
                if (sLastAutoSuggestDicTypeId != Suggest.DIC_MAIN) {
                    if (sDBG) {
                        Log.d(TAG, "logOnAutoSuggestion was cancelled: not from main dic.");
                    }
                    before = "";
                    after = "";
                }
                // TODO: Not to send a log if this doesn't come from Main Dictionary.
                {
                    if (before.equals(after)) {
                        before = "";
                        after = "";
                    }
                    String[] strings = new String[] {before, after, separator};
                    sLatinImeLogger.sendLogToDropBox(ID_AUTOSUGGESTION, strings);
                }
                synchronized (LatinImeLogger.class) {
                    sLastAutoSuggestBefore = before;
                    sLastAutoSuggestAfter = after;
                    sLastAutoSuggestSeparator = separator;
                }
            }
            sSuggestDicMap.clear();
        }
    }

    public static void logOnAutoSuggestionCanceled() {
        if (sLogEnabled) {
            sLatinImeLogger.mAutoCancelledCountPerDic[sLastAutoSuggestDicTypeId]++;
            if (sLastAutoSuggestBefore != null && sLastAutoSuggestAfter != null) {
                String[] strings = new String[] {
                        sLastAutoSuggestBefore, sLastAutoSuggestAfter, sLastAutoSuggestSeparator};
                sLatinImeLogger.sendLogToDropBox(ID_AUTOSUGGESTIONCANCELLED, strings);
            }
            synchronized (LatinImeLogger.class) {
                sLastAutoSuggestBefore = "";
                sLastAutoSuggestAfter = "";
                sLastAutoSuggestSeparator = "";
            }
        }
    }

    public static void logOnDelete() {
        if (sLogEnabled) {
            String mLastWord = sLatinImeLogger.mRingCharBuffer.getLastString();
            if (!TextUtils.isEmpty(mLastWord)
                    && mLastWord.equalsIgnoreCase(sLastAutoSuggestBefore)) {
                logOnAutoSuggestionCanceled();
            }
            sLatinImeLogger.mRingCharBuffer.pop();
            sLatinImeLogger.sendLogToDropBox(ID_DELETE_COUNT, 1);
        }
    }

    public static void logOnInputChar(char c) {
        if (sLogEnabled) {
            sLatinImeLogger.mRingCharBuffer.push(c);
            sLatinImeLogger.sendLogToDropBox(ID_INPUT_COUNT, 1);
        }
    }

    public static void logOnException(String metaData, Throwable e) {
        if (sLogEnabled) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            e.printStackTrace(ps);
            String exceptionString = URLEncoder.encode(new String(baos.toByteArray(), 0,
                    Math.min(EXCEPTION_MAX_LENGTH, baos.size())));
            sLatinImeLogger.sendLogToDropBox(
                    ID_EXCEPTION, new String[] {metaData, exceptionString});
            if (sDBG) {
                Log.e(TAG, "Exception: " + new String(baos.toByteArray())+ ":" + exceptionString);
            }
            if (SUPPRESS_EXCEPTION) {
                sLatinImeLogger.commitInternalAndStopSelf();
            } else {
                sLatinImeLogger.commitInternal();
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else if (e instanceof Error) {
                    throw (Error) e;
                }
            }
        }
    }

    public static void logOnWarning(String warning) {
        if (sLogEnabled) {
            sLatinImeLogger.sendLogToDropBox(
                    ID_EXCEPTION, new String[] {warning, ""});
        }
    }

    public static void onStartSuggestion() {
        if (sLogEnabled) {
            sSuggestDicMap.clear();
        }
    }

    public static void onAddSuggestedWord(String word, int typeId) {
        if (sLogEnabled) {
            sSuggestDicMap.put(word, typeId);
        }
    }

    private static class LogSerializer {
        private static void appendWithLength(StringBuffer sb, String data) {
            sb.append(data.length());
            sb.append(SEPARATER);
            sb.append(data);
            sb.append(SEPARATER);
        }

        private static void appendLogEntry(StringBuffer sb, String time, String tag,
                String[] data) {
            if (data.length > 0) {
                appendWithLength(sb, String.valueOf(data.length + 2));
                appendWithLength(sb, time);
                appendWithLength(sb, tag);
                for (String s: data) {
                    appendWithLength(sb, s);
                }
            }
        }

        public static String createStringFromEntries(ArrayList<LogEntry> logs) {
            StringBuffer sb = new StringBuffer();
            for (LogEntry log: logs) {
                appendLogEntry(sb, String.valueOf(log.mTime), String.valueOf(log.mTag), log.mData);
            }
            return sb.toString();
        }
    }

    /* package */ static class RingCharBuffer {
        final int BUFSIZE = 20;
        private Context mContext;
        private int mEnd = 0;
        /* package */ int length = 0;
        private char[] mCharBuf = new char[BUFSIZE];

        public RingCharBuffer(Context context) {
            mContext = context;
        }

        private int normalize(int in) {
            int ret = in % BUFSIZE;
            return ret < 0 ? ret + BUFSIZE : ret;
        }
        public void push(char c) {
            mCharBuf[mEnd] = c;
            mEnd = normalize(mEnd + 1);
            if (length < BUFSIZE) {
                ++length;
            }
        }
        public char pop() {
            if (length < 1) {
                return NULL_CHAR;
            } else {
                mEnd = normalize(mEnd - 1);
                --length;
                return mCharBuf[mEnd];
            }
        }
        public char getLastChar() {
            if (length < 1) {
                return NULL_CHAR;
            } else {
                return mCharBuf[normalize(mEnd - 1)];
            }
        }
        public String getLastString() {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < length; ++i) {
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
            length = 0;
        }
    }

    private static class DebugKeyEnabler {
        private int mCounter = 0;
        private long mLastTime = 0;
        public boolean check() {
            if (System.currentTimeMillis() - mLastTime > 10 * 1000) {
                mCounter = 0;
                mLastTime = System.currentTimeMillis();
            } else if (++mCounter >= 10) {
                return true;
            }
            return false;
        }
    }
}
