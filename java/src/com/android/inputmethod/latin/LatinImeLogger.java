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
import android.os.DropBoxManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;

public class LatinImeLogger implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "LatinIMELogs";
    private static boolean sDBG = false;
    // DEFAULT_LOG_ENABLED should be false when released to public.
    private static final boolean DEFAULT_LOG_ENABLED = true;

    private static final long MINIMUMSENDINTERVAL = 300 * DateUtils.SECOND_IN_MILLIS; // 300 sec
    private static final long MINIMUMCOUNTINTERVAL = 20 * DateUtils.SECOND_IN_MILLIS; // 20 sec
    private static final char SEPARATER = ';';
    private static final int ID_CLICKSUGGESTION = 0;
    private static final int ID_AUTOSUGGESTION = 1;
    private static final int ID_AUTOSUGGESTIONCANCELED = 2;
    private static final int ID_INPUT_COUNT = 3;
    private static final int ID_DELETE_COUNT = 4;
    private static final int ID_WORD_COUNT = 5;
    private static final int ID_ACTUAL_CHAR_COUNT = 6;
    private static final int ID_THEME_ID = 7;

    private static final String PREF_ENABLE_LOG = "enable_logging";
    private static final String PREF_DEBUG_MODE = "debug_mode";

    public static boolean sLogEnabled = true;
    private static LatinImeLogger sLatinImeLogger = new LatinImeLogger();
    // Store the last auto suggested word.
    // This is required for a cancellation log of auto suggestion of that word.
    private static String sLastAutoSuggestBefore;
    private static String sLastAutoSuggestAfter;

    private ArrayList<LogEntry> mLogBuffer = null;
    private ArrayList<LogEntry> mPrivacyLogBuffer = null;
    private Context mContext = null;
    private DropBoxManager mDropBox = null;
    private long mLastTimeActive;
    private long mLastTimeSend;
    private long mLastTimeCountEntry;

    private String mThemeId;
    private int mDeleteCount;
    private int mInputCount;
    private int mWordCount;
    // ActualCharCount includes all characters that were completed.
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
        mLogBuffer = new ArrayList<LogEntry>();
        mPrivacyLogBuffer = new ArrayList<LogEntry>();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        sLogEnabled = prefs.getBoolean(PREF_ENABLE_LOG, DEFAULT_LOG_ENABLED);
        mThemeId = prefs.getString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT,
                KeyboardSwitcher.DEFAULT_LAYOUT_ID);
        sDBG = prefs.getBoolean(PREF_DEBUG_MODE, sDBG);
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
        mLogBuffer.clear();
        mPrivacyLogBuffer.clear();
    }

    /**
     * Check if the input string is safe as an entry or not.
     */
    private static boolean checkStringDataSafe(String s) {
        if (sDBG) {
            Log.d(TAG, "Check String safety: " + s);
        }
        for (int i = 0; i < s.length(); ++i) {
            if (!Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void addCountEntry(long time) {
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
    }

    private void addThemeIdEntry(long time) {
        mLogBuffer.add(new LogEntry (time, ID_THEME_ID,
                new String[] {mThemeId}));
    }

    private void flushPrivacyLogSafely() {
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
                if (mLastTimeActive - mLastTimeCountEntry > MINIMUMCOUNTINTERVAL
                        || (mDeleteCount == 0 && mInputCount == 0)) {
                    addCountEntry(mLastTimeActive);
                    addThemeIdEntry(mLastTimeActive);
                }
                mDeleteCount += (Integer)data;
                break;
            case ID_INPUT_COUNT:
                if (mLastTimeActive - mLastTimeCountEntry > MINIMUMCOUNTINTERVAL
                        || (mDeleteCount == 0 && mInputCount == 0)) {
                    addCountEntry(mLastTimeActive);
                    addThemeIdEntry(mLastTimeActive);
                }
                mInputCount += (Integer)data;
                break;
            case ID_CLICKSUGGESTION:
            case ID_AUTOSUGGESTION:
                ++mWordCount;
                String[] dataStrings = (String[]) data;
                if (dataStrings.length != 3) {
                    if (sDBG) {
                        Log.e(TAG, "The length of string array is invalid.");
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
            case ID_AUTOSUGGESTIONCANCELED:
                --mWordCount;
                dataStrings = (String[]) data;
                if (dataStrings.length != 3) {
                    if (sDBG) {
                        Log.e(TAG, "The length of string array is invalid.");
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
            default:
                if (sDBG) {
                    Log.e(TAG, "Log Tag is not entried.");
                }
                break;
        }
    }

    private void commitInternal() {
        flushPrivacyLogSafely();
        long now = System.currentTimeMillis();
        addCountEntry(now);
        addThemeIdEntry(now);
        String s = LogSerializer.createStringFromEntries(mLogBuffer);
        if (!TextUtils.isEmpty(s)) {
            if (sDBG) {
                Log.d(TAG, "Commit log: " + s);
            }
            mDropBox.addText(TAG, s);
        }
        reset();
        mLastTimeSend = now;
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
        } else if (KeyboardSwitcher.PREF_KEYBOARD_LAYOUT.equals(key)) {
            mThemeId = sharedPreferences.getString(KeyboardSwitcher.PREF_KEYBOARD_LAYOUT,
                    KeyboardSwitcher.DEFAULT_LAYOUT_ID);
        } else if (PREF_DEBUG_MODE.equals(key)) {
            sDBG = sharedPreferences.getBoolean(PREF_DEBUG_MODE, sDBG);
        }
    }

    public static void init(Context context) {
        sLatinImeLogger.initInternal(context);
    }

    public static void commit() {
        if (sLogEnabled) {
            sLatinImeLogger.commitInternal();
        }
    }

    // TODO: Handle CharSequence instead of String
    public static void logOnClickSuggestion(String before, String after, int position) {
        if (sLogEnabled) {
            String[] strings = new String[] {before, after, String.valueOf(position)};
            sLatinImeLogger.sendLogToDropBox(ID_CLICKSUGGESTION, strings);
        }
    }

    public static void logOnAutoSuggestion(String before, String after) {
        if (sLogEnabled) {
            String[] strings = new String[] {before, after};
            synchronized (LatinImeLogger.class) {
                sLastAutoSuggestBefore = before;
                sLastAutoSuggestAfter = after;
            }
            sLatinImeLogger.sendLogToDropBox(ID_AUTOSUGGESTIONCANCELED, strings);
        }
    }

    public static void logOnAutoSuggestionCanceled() {
        if (sLogEnabled) {
            if (sLastAutoSuggestBefore != null && sLastAutoSuggestAfter != null) {
                String[] strings = new String[] {sLastAutoSuggestBefore, sLastAutoSuggestAfter};
                sLatinImeLogger.sendLogToDropBox(ID_AUTOSUGGESTION, strings);
            }
        }
    }

    public static void logOnDelete(int length) {
        if (sLogEnabled) {
            sLatinImeLogger.sendLogToDropBox(ID_DELETE_COUNT, length);
        }
    }

    public static void logOnInputChar(int length) {
        if (sLogEnabled) {
            sLatinImeLogger.sendLogToDropBox(ID_INPUT_COUNT, length);
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
}
