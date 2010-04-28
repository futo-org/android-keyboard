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
import android.text.format.DateUtils;
import android.util.Log;

import java.util.ArrayList;

public class LatinImeLogger implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "LatinIMELogs";
    private static final boolean DBG = false;
    // DEFAULT_LOG_ENABLED should be false when released to public.
    private static final boolean DEFAULT_LOG_ENABLED = true;

    private static final long MINIMUMSENDINTERVAL = 1 * DateUtils.MINUTE_IN_MILLIS; // 1 min
    private static final long MINIMUMCOUNTINTERVAL = 20 * DateUtils.SECOND_IN_MILLIS; // 20 sec
    private static final char SEPARATER = ';';
    private static final int ID_CLICKSUGGESTION = 0;
    private static final int ID_AUTOSUGGESTION = 1;
    private static final int ID_AUTOSUGGESTIONCANCELED = 2;
    private static final int ID_INPUT = 3;
    private static final int ID_DELETE = 4;
    private static final String PREF_ENABLE_LOG = "enable_log";

    private static LatinImeLogger sLatinImeLogger = new LatinImeLogger();
    public static boolean sLogEnabled = true;

    private ArrayList<LogEntry> mLogBuffer = null;
    private Context mContext = null;
    private DropBoxManager mDropBox = null;
    private long mLastTimeActive;
    private long mLastTimeSend;
    private long mLastTimeCountEntry;

    private int mDeleteCount;
    private int mInputCount;


    private static class LogEntry {
        public final int mTag;
        public final long mTime;
        public final String mData;
        public LogEntry (long time, int tag, String data) {
            mTag = tag;
            mTime = time;
            mData = data;
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
        mLogBuffer = new ArrayList<LogEntry>();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        sLogEnabled = prefs.getBoolean(PREF_ENABLE_LOG, DEFAULT_LOG_ENABLED);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Clear all logged data
     */
    private void reset() {
        mDeleteCount = 0;
        mInputCount = 0;
        mLogBuffer.clear();
    }

    /**
     * Check if the input string is safe as an entry or not.
     */
    private static boolean checkStringDataSafe(String s) {
        for (int i = 0; i < s.length(); ++i) {
            if (!Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void addCountEntry(long time) {
        mLogBuffer.add(new LogEntry (time, ID_DELETE, String.valueOf(mDeleteCount)));
        mLogBuffer.add(new LogEntry (time, ID_INPUT, String.valueOf(mInputCount)));
        mDeleteCount = 0;
        mInputCount = 0;
    }

    /**
     * Add an entry
     * @param tag
     * @param data
     */
    private void addData(int tag, Object data) {
        switch (tag) {
            case ID_DELETE:
                if (mLastTimeActive - mLastTimeCountEntry > MINIMUMCOUNTINTERVAL
                        || (mDeleteCount == 0 && mInputCount == 0)) {
                    addCountEntry(mLastTimeActive);
                }
                mDeleteCount += (Integer)data;
                break;
            case ID_INPUT:
                if (mLastTimeActive - mLastTimeCountEntry > MINIMUMCOUNTINTERVAL
                        || (mDeleteCount == 0 && mInputCount == 0)) {
                    addCountEntry(mLastTimeActive);
                }
                mInputCount += (Integer)data;
                break;
            default:
                if (data instanceof String) {
                    String dataString = (String) data;
                    if (checkStringDataSafe(dataString)) {
                        mLogBuffer.add(new LogEntry (System.currentTimeMillis(), tag, dataString));
                    } else {
                        if (DBG) {
                            Log.d(TAG, "Skipped to add an entry because data is unsafe.");
                        }
                    }
                } else {
                    if (DBG) {
                        Log.e(TAG, "Log is invalid.");
                    }
                }
                break;
        }
    }

    private static void appendWithLength(StringBuffer sb, String data) {
        sb.append(data.length());
        sb.append(SEPARATER);
        sb.append(data);
    }

    private static void appendLogEntry(StringBuffer sb, String time, String tag, String data) {
        appendWithLength(sb, time);
        appendWithLength(sb, tag);
        appendWithLength(sb, data);
    }

    private String createStringFromEntries(ArrayList<LogEntry> logs) {
        addCountEntry(System.currentTimeMillis());
        StringBuffer sb = new StringBuffer();
        for (LogEntry log: logs) {
            appendLogEntry(sb, String.valueOf(log.mTime), String.valueOf(log.mTag), log.mData);
        }
        return sb.toString();
    }

    private void commitInternal() {
        String s = createStringFromEntries(mLogBuffer);
        if (DBG) {
            Log.d(TAG, "Commit log: " + s);
        }
        mDropBox.addText(TAG, s);
        reset();
        mLastTimeSend = System.currentTimeMillis();
    }

    private void sendLogToDropBox(int tag, Object s) {
        if (DBG) {
            Log.d(TAG, "SendLog: " + tag + ";" + s);
        }
        long now = System.currentTimeMillis();
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

    public static void logOnClickSuggestion(String s) {
        if (sLogEnabled) {
            sLatinImeLogger.sendLogToDropBox(ID_CLICKSUGGESTION, s);
        }
    }

    public static void logOnAutoSuggestion(String s) {
        if (sLogEnabled) {
            sLatinImeLogger.sendLogToDropBox(ID_AUTOSUGGESTION, s);
        }
    }

    public static void logOnAutoSuggestionCanceled(String s) {
        if (sLogEnabled) {
            sLatinImeLogger.sendLogToDropBox(ID_AUTOSUGGESTIONCANCELED, s);
        }
    }

    public static void logOnDelete(int length) {
        if (sLogEnabled) {
            sLatinImeLogger.sendLogToDropBox(ID_DELETE, length);
        }
    }

    public static void logOnInputChar(int length) {
        if (sLogEnabled) {
            sLatinImeLogger.sendLogToDropBox(ID_INPUT, length);
        }
    }

}
