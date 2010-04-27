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
import android.os.DropBoxManager;
import android.text.format.DateUtils;
import android.util.Log;

import java.util.ArrayList;

public class LatinImeLogger {
    private static final String TAG = "LatinIMELogs";
    private static final boolean DBG = false;

    // Volatile is needed for multi-cpu platform.
    private static volatile LatinImeLogger sLatinImeLogger;

    private static final long MINIMUMINTERVAL = 20 * DateUtils.SECOND_IN_MILLIS; // 20 sec
    private static final char SEPARATER = ';';
    private static final int ID_CLICKSUGGESTION = 0;
    private static final int ID_AUTOSUGGESTION = 1;
    private static final int ID_AUTOSUGGESTIONCANCELED = 2;
    private static final int ID_INPUT = 3;
    private static final int ID_DELETE = 4;

    private ArrayList<LogEntry> mLogBuffer;
    private final Context mContext;
    private final DropBoxManager mDropBox;
    private long mLastTimeActive;
    private long mLastTimeSend;

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

    /**
     * Returns the singleton of the logger.
     * @param context
     */
    public static LatinImeLogger getLogger(Context context) {
        if (sLatinImeLogger == null) {
            synchronized (LatinImeLogger.class) {
                if (sLatinImeLogger == null) {
                    sLatinImeLogger =new LatinImeLogger(context);
                }
            }
        }
        return sLatinImeLogger;
    }

    private LatinImeLogger(Context context) {
        mContext = context;
        mDropBox = (DropBoxManager) mContext.getSystemService(Context.DROPBOX_SERVICE);
        mLastTimeSend = System.currentTimeMillis();
        mLastTimeActive = mLastTimeSend;
        mDeleteCount = 0;
        mInputCount = 0;
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
    private boolean checkStringDataSafe(String s) {
        for (int i = 0; i < s.length(); ++i) {
            if (!Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add an entry
     * @param tag
     * @param data
     */
    private void addData(int tag, Object data) {
        switch (tag) {
            case ID_DELETE:
                mDeleteCount += (Integer)data;
                break;
            case ID_INPUT:
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
        StringBuffer sb = new StringBuffer();
        String nowString = String.valueOf(System.currentTimeMillis());
        appendLogEntry(sb, nowString, String.valueOf(ID_DELETE), String.valueOf(mDeleteCount));
        appendLogEntry(sb, nowString, String.valueOf(ID_INPUT), String.valueOf(mInputCount));
        for (LogEntry log: logs) {
            appendLogEntry(sb, String.valueOf(log.mTime), String.valueOf(log.mTag), log.mData);
        }
        return sb.toString();
    }

    private void commit() {
        mDropBox.addText(TAG, createStringFromEntries(mLogBuffer));
        reset();
        mLastTimeSend = System.currentTimeMillis();
    }

    private void sendLogToDropBox(int tag, Object s) {
        long now = System.currentTimeMillis();
        if (now - mLastTimeActive > MINIMUMINTERVAL) {
            // Send a log before adding an log entry if the last data is too old.
            commit();
            addData(tag, s);
        } else if (now - mLastTimeSend > MINIMUMINTERVAL) {
            // Send a log after adding an log entry.
            addData(tag, s);
            commit();
        } else {
            addData(tag, s);
        }
        mLastTimeActive = now;
    }

    public void logOnClickSuggestion(String s) {
        sendLogToDropBox(ID_CLICKSUGGESTION, s);
    }

    public void logOnAutoSuggestion(String s) {
        sendLogToDropBox(ID_AUTOSUGGESTION, s);
    }

    public void logOnAutoSuggestionCanceled(String s) {
        sendLogToDropBox(ID_AUTOSUGGESTIONCANCELED, s);
    }

    public void logOnDelete(int length) {
        sendLogToDropBox(ID_DELETE, length);
    }

    public void logOnInputChar(int length) {
        sendLogToDropBox(ID_INPUT, length);
    }
}
