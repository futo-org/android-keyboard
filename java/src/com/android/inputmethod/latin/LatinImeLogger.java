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

import com.android.inputmethod.latin.Dictionary.DataType;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.inputmethodservice.Keyboard;
import android.os.AsyncTask;
import android.os.DropBoxManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;

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
    private static boolean sPRINTLOGGING = false;
    // SUPPRESS_EXCEPTION should be true when released to public.
    private static final boolean SUPPRESS_EXCEPTION = true;
    // DEFAULT_LOG_ENABLED should be false when released to public.
    private static final boolean DEFAULT_LOG_ENABLED = false;

    private static final long MINIMUMSENDINTERVAL = 300 * DateUtils.SECOND_IN_MILLIS; // 300 sec
    private static final long MINIMUMCOUNTINTERVAL = 20 * DateUtils.SECOND_IN_MILLIS; // 20 sec
    private static final long MINIMUMSENDSIZE = 40;
    private static final char SEPARATER = ';';
    private static final char NULL_CHAR = '\uFFFC';
    private static final int EXCEPTION_MAX_LENGTH = 400;
    private static final int INVALID_COORDINATE = -2;

    // ID_MANUALSUGGESTION has been replaced by ID_MANUALSUGGESTION_WITH_DATATYPE
    // private static final int ID_MANUALSUGGESTION = 0;
    // private static final int ID_AUTOSUGGESTIONCANCELLED = 1;
    // private static final int ID_AUTOSUGGESTION = 2;
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
    private static final int ID_LANGUAGES = 14;
    private static final int ID_MANUALSUGGESTION_WITH_DATATYPE = 15;
    private static final int ID_AUTOSUGGESTIONCANCELLED_WITH_COORDINATES = 16;
    private static final int ID_AUTOSUGGESTION_WITH_COORDINATES = 17;

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
    private static int[] sLastAutoSuggestXCoordinates;
    private static int[] sLastAutoSuggestYCoordinates;
    // This value holds MAIN, USER, AUTO, etc...
    private static int sLastAutoSuggestDicTypeId;
    // This value holds 0 (= unigram), 1 (= bigram) etc...
    private static int sLastAutoSuggestDataType;
    private static HashMap<String, Pair<Integer, Integer>> sSuggestDicMap
            = new HashMap<String, Pair<Integer, Integer>>();
    private static String[] sPreviousWords;
    private static DebugKeyEnabler sDebugKeyEnabler = new DebugKeyEnabler();
    private static int sKeyboardWidth = 0;
    private static int sKeyboardHeight = 0;

    private ArrayList<LogEntry> mLogBuffer = null;
    private ArrayList<LogEntry> mPrivacyLogBuffer = null;
    /* package */ RingCharBuffer mRingCharBuffer = null;

    private Context mContext = null;
    private DropBoxManager mDropBox = null;
    private AddTextToDropBoxTask mAddTextToDropBoxTask;
    private long mLastTimeActive;
    private long mLastTimeSend;
    private long mLastTimeCountEntry;

    private String mThemeId;
    private String mSelectedLanguages;
    private String mCurrentLanguage;
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

    private class AddTextToDropBoxTask extends AsyncTask<Void, Void, Void> {
        private final DropBoxManager mDropBox;
        private final long mTime;
        private final String mData;
        public AddTextToDropBoxTask(DropBoxManager db, long time, String data) {
            mDropBox = db;
            mTime = time;
            mData = data;
        }
        @Override
        protected Void doInBackground(Void... params) {
            if (sPRINTLOGGING) {
                Log.d(TAG, "Commit log: " + mData);
            }
            mDropBox.addText(TAG, mData);
            return null;
        }
        @Override
        protected void onPostExecute(Void v) {
            mLastTimeSend = mTime;
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
        mSelectedLanguages = prefs.getString(LatinIME.PREF_SELECTED_LANGUAGES, "");
        mCurrentLanguage = prefs.getString(LatinIME.PREF_INPUT_LANGUAGE, "");
        sPRINTLOGGING = prefs.getBoolean(PREF_DEBUG_MODE, sPRINTLOGGING);
        sDBG = sPRINTLOGGING;
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
    }

    public void destroy() {
        LatinIMEUtil.cancelTask(mAddTextToDropBoxTask, false);
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
        if (sPRINTLOGGING) {
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
        if (sPRINTLOGGING) {
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
        if (sPRINTLOGGING) {
            Log.d(TAG, "Log theme Id. (1)");
        }
        // TODO: Not to convert theme ID here. Currently "2" is treated as "6" in a log server.
        if (mThemeId.equals("2")) {
            mThemeId = "6";
        } else if (mThemeId.equals("3")) {
            mThemeId = "7";
        }
        mLogBuffer.add(new LogEntry (time, ID_THEME_ID,
                new String[] {mThemeId}));
    }

    private void addLanguagesEntry(long time) {
        if (sPRINTLOGGING) {
            Log.d(TAG, "Log language settings. (1)");
        }
        // CurrentLanguage and SelectedLanguages will be blank if user doesn't use multi-language
        // switching.
        if (TextUtils.isEmpty(mCurrentLanguage)) {
            mCurrentLanguage = mContext.getResources().getConfiguration().locale.toString();
        }
        mLogBuffer.add(new LogEntry (time, ID_LANGUAGES,
                new String[] {mCurrentLanguage , mSelectedLanguages}));
    }

    private void addSettingsEntry(long time) {
        if (sPRINTLOGGING) {
            Log.d(TAG, "Log settings. (1)");
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mLogBuffer.add(new LogEntry (time, ID_SETTING_AUTO_COMPLETE,
                new String[] {String.valueOf(prefs.getBoolean(PREF_AUTO_COMPLETE,
                        mContext.getResources().getBoolean(R.bool.enable_autocorrect)))}));
    }

    private void addVersionNameEntry(long time) {
        if (sPRINTLOGGING) {
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
        if (sPRINTLOGGING) {
            Log.d(TAG, "Log Exception. (1)");
        }
        mLogBuffer.add(new LogEntry(time, ID_EXCEPTION, data));
    }

    private void flushPrivacyLogSafely() {
        if (sPRINTLOGGING) {
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
            case ID_MANUALSUGGESTION_WITH_DATATYPE:
            case ID_AUTOSUGGESTION_WITH_COORDINATES:
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
            case ID_AUTOSUGGESTIONCANCELLED_WITH_COORDINATES:
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
        // if there is no log entry in mLogBuffer, will not send logs to DropBox.
        if (!mLogBuffer.isEmpty() && (mAddTextToDropBoxTask == null
                || mAddTextToDropBoxTask.getStatus() == AsyncTask.Status.FINISHED)) {
            if (sPRINTLOGGING) {
                Log.d(TAG, "Commit (" + mLogBuffer.size() + ")");
            }
            flushPrivacyLogSafely();
            long now = System.currentTimeMillis();
            addCountEntry(now);
            addThemeIdEntry(now);
            addLanguagesEntry(now);
            addSettingsEntry(now);
            addVersionNameEntry(now);
            addSuggestionCountEntry(now);
            String s = LogSerializer.createStringFromEntries(mLogBuffer);
            reset();
            mAddTextToDropBoxTask = (AddTextToDropBoxTask) new AddTextToDropBoxTask(
                    mDropBox, now, s).execute();
        }
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
            Log.d(TAG, "SendLog: " + tag + ";" + out + " -> will be sent after "
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
            sPRINTLOGGING = sharedPreferences.getBoolean(PREF_DEBUG_MODE, sPRINTLOGGING);
            sDBG = sPRINTLOGGING;
        } else if (LatinIME.PREF_INPUT_LANGUAGE.equals(key)) {
            mCurrentLanguage = sharedPreferences.getString(LatinIME.PREF_INPUT_LANGUAGE, "");
            addLanguagesEntry(mLastTimeActive);
        } else if (LatinIME.PREF_INPUT_LANGUAGE.equals(key)) {
            mSelectedLanguages = sharedPreferences.getString(LatinIME.PREF_SELECTED_LANGUAGES, "");
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

    public static void onDestroy() {
        sLatinImeLogger.commitInternal();
        sLatinImeLogger.destroy();
    }

    // TODO: Handle CharSequence instead of String
    public static void logOnManualSuggestion(String before, String after, int position
            , List<CharSequence> suggestions) {
        if (sLogEnabled) {
            // log punctuation
            if (before.length() == 0 && after.length() == 1) {
                sLatinImeLogger.sendLogToDropBox(ID_MANUALSUGGESTION_WITH_DATATYPE, new String[] {
                        before, after, String.valueOf(position), ""});
            } else if (!sSuggestDicMap.containsKey(after)) {
                if (sDBG) {
                    Log.e(TAG, "logOnManualSuggestion was cancelled: from unknown dic.");
                }
            } else {
                int dicTypeId = sSuggestDicMap.get(after).first;
                sLatinImeLogger.mManualSuggestCountPerDic[dicTypeId]++;
                if (dicTypeId != Suggest.DIC_MAIN) {
                    if (sDBG) {
                        Log.d(TAG, "logOnManualSuggestion was cancelled: not from main dic.");
                    }
                    before = "";
                    after = "";
                    sPreviousWords = null;
                }
                // TODO: Don't send a log if this doesn't come from Main Dictionary.
                {
                    if (before.equals(after)) {
                        before = "";
                        after = "";
                    }

                    /* Example:
                     * When user typed "Illegal imm" and picked "immigrants",
                     * the suggestion list has "immigrants, immediate, immigrant".
                     * At this time, the log strings will be something like below:
                     * strings[0 = COLUMN_BEFORE_ID] = imm
                     * strings[1 = COLUMN_AFTER_ID] = immigrants
                     * strings[2 = COLUMN_PICKED_POSITION_ID] = 0
                     * strings[3 = COLUMN_SUGGESTION_LENGTH_ID] = 3
                     * strings[4 = COLUMN_PREVIOUS_WORDS_COUNT_ID] = 1
                     * strings[5] = immigrants
                     * strings[6] = immediate
                     * strings[7] = immigrant
                     * strings[8] = 1 (= bigram)
                     * strings[9] = 0 (= unigram)
                     * strings[10] = 1 (= bigram)
                     * strings[11] = Illegal
                     */

                    // 0 for unigram, 1 for bigram, 2 for trigram...
                    int previousWordsLength = (sPreviousWords == null) ? 0 : sPreviousWords.length;
                    int suggestionLength = suggestions.size();

                    final int COLUMN_BEFORE_ID = 0;
                    final int COLUMN_AFTER_ID = 1;
                    final int COLUMN_PICKED_POSITION_ID = 2;
                    final int COLUMN_SUGGESTION_LENGTH_ID = 3;
                    final int COLUMN_PREVIOUS_WORDS_COUNT_ID = 4;
                    final int BASE_COLUMN_SIZE = 5;

                    String[] strings =
                        new String[BASE_COLUMN_SIZE + suggestionLength * 2 + previousWordsLength];
                    strings[COLUMN_BEFORE_ID] = before;
                    strings[COLUMN_AFTER_ID] = after;
                    strings[COLUMN_PICKED_POSITION_ID] = String.valueOf(position);
                    strings[COLUMN_SUGGESTION_LENGTH_ID] = String.valueOf(suggestionLength);
                    strings[COLUMN_PREVIOUS_WORDS_COUNT_ID] = String.valueOf(previousWordsLength);

                    for (int i = 0; i < suggestionLength; ++i) {
                        String s = suggestions.get(i).toString();
                        if (sSuggestDicMap.containsKey(s)) {
                            strings[BASE_COLUMN_SIZE + i] = s;
                            strings[BASE_COLUMN_SIZE + suggestionLength + i]
                                    = sSuggestDicMap.get(s).second.toString();
                        } else {
                            strings[BASE_COLUMN_SIZE + i] = "";
                            strings[BASE_COLUMN_SIZE + suggestionLength + i] = "";
                        }
                    }

                    for (int i = 0; i < previousWordsLength; ++i) {
                        strings[BASE_COLUMN_SIZE + suggestionLength * 2 + i] = sPreviousWords[i];
                    }

                    sLatinImeLogger.sendLogToDropBox(ID_MANUALSUGGESTION_WITH_DATATYPE, strings);
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
                sLastAutoSuggestDicTypeId = sSuggestDicMap.get(after).first;
                sLastAutoSuggestDataType = sSuggestDicMap.get(after).second;
                sLatinImeLogger.mAutoSuggestCountPerDic[sLastAutoSuggestDicTypeId]++;
                if (sLastAutoSuggestDicTypeId != Suggest.DIC_MAIN) {
                    if (sDBG) {
                        Log.d(TAG, "logOnAutoSuggestion was cancelled: not from main dic.:"
                                + sLastAutoSuggestDicTypeId);
                    }
                    before = "";
                    after = "";
                    sPreviousWords = null;
                }
                // TODO: Not to send a log if this doesn't come from Main Dictionary.
                {
                    if (before.equals(after)) {
                        before = "";
                        after = "";
                    }

                    final int COLUMN_BEFORE_ID = 0;
                    final int COLUMN_AFTER_ID = 1;
                    final int COLUMN_SEPARATOR_ID = 2;
                    final int COLUMN_DATA_TYPE_ID = 3;
                    final int COLUMN_KEYBOARD_SIZE_WIDTH = 4;
                    final int COLUMN_KEYBOARD_SIZE_HEIGHT = 5;
                    final int BASE_COLUMN_SIZE = 6;

                    final int userTypedWordLength = before.length();
                    final int previousWordsLength = (sPreviousWords == null) ? 0
                            : sPreviousWords.length;
                    String[] strings = new String[BASE_COLUMN_SIZE + userTypedWordLength * 2
                                                  + previousWordsLength];
                    sLastAutoSuggestXCoordinates = new int[userTypedWordLength];
                    sLastAutoSuggestXCoordinates = new int[userTypedWordLength];

                    strings[COLUMN_BEFORE_ID] = before;
                    strings[COLUMN_AFTER_ID] = after;
                    strings[COLUMN_SEPARATOR_ID] = separator;
                    strings[COLUMN_DATA_TYPE_ID] = String.valueOf(sLastAutoSuggestDataType);
                    strings[COLUMN_KEYBOARD_SIZE_WIDTH] = String.valueOf(sKeyboardWidth);
                    strings[COLUMN_KEYBOARD_SIZE_HEIGHT] = String.valueOf(sKeyboardHeight);

                    for (int i = 0; i < userTypedWordLength; ++i) {
                        int x = sLatinImeLogger.mRingCharBuffer.getPreviousX(before.charAt(i),
                                userTypedWordLength - i - 1);
                        int y = sLatinImeLogger.mRingCharBuffer.getPreviousY(before.charAt(i),
                                userTypedWordLength - i - 1);
                        strings[BASE_COLUMN_SIZE + i * 2] = String.valueOf(x);
                        strings[BASE_COLUMN_SIZE + i * 2 + 1] = String.valueOf(y);
                        sLastAutoSuggestXCoordinates[i] = x;
                        sLastAutoSuggestXCoordinates[i] = y;
                    }

                    for (int i = 0; i < previousWordsLength; ++i) {
                        strings[BASE_COLUMN_SIZE + userTypedWordLength * 2 + i] = sPreviousWords[i];
                    }

                    sLatinImeLogger.sendLogToDropBox(ID_AUTOSUGGESTION_WITH_COORDINATES, strings);
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
                final int COLUMN_BEFORE_ID = 0;
                final int COLUMN_AFTER_ID = 1;
                final int COLUMN_SEPARATOR_ID = 2;
                final int COLUMN_KEYBOARD_SIZE_WIDTH = 3;
                final int COLUMN_KEYBOARD_SIZE_HEIGHT = 4;
                final int BASE_COLUMN_SIZE = 5;

                final int userTypedWordLength = sLastAutoSuggestBefore.length();

                String[] strings = new String[BASE_COLUMN_SIZE + userTypedWordLength * 2];
                strings[COLUMN_BEFORE_ID] = sLastAutoSuggestBefore;
                strings[COLUMN_AFTER_ID] = sLastAutoSuggestAfter;
                strings[COLUMN_SEPARATOR_ID] = sLastAutoSuggestSeparator;
                strings[COLUMN_KEYBOARD_SIZE_WIDTH] = String.valueOf(sKeyboardWidth);
                strings[COLUMN_KEYBOARD_SIZE_HEIGHT] = String.valueOf(sKeyboardHeight);
                for (int i = 0; i < userTypedWordLength; ++i) {
                    strings[BASE_COLUMN_SIZE + i * 2] = String.valueOf(
                            sLastAutoSuggestXCoordinates);
                    strings[BASE_COLUMN_SIZE + i * 2 + 1] = String.valueOf(
                            sLastAutoSuggestYCoordinates);
                }
                sLatinImeLogger.sendLogToDropBox(
                        ID_AUTOSUGGESTIONCANCELLED_WITH_COORDINATES, strings);
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

    public static void logOnInputChar(char c, int x, int y) {
        if (sLogEnabled) {
            sLatinImeLogger.mRingCharBuffer.push(c, x, y);
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

    // TODO: This code supports only Bigram.
    public static void onStartSuggestion(CharSequence previousWords) {
        if (sLogEnabled) {
            sSuggestDicMap.clear();
            sPreviousWords = new String[] {
                    (previousWords == null) ? "" : previousWords.toString()};
        }
    }

    public static void onAddSuggestedWord(String word, int typeId, DataType dataType) {
        if (sLogEnabled) {
            sSuggestDicMap.put(word, new Pair<Integer, Integer>(typeId, dataType.ordinal()));
        }
    }

    public static void onSetKeyboard(Keyboard kb) {
        if (sLogEnabled) {
            sKeyboardWidth = kb.getMinWidth();
            sKeyboardHeight = kb.getHeight();
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
        /* package */ int mLength = 0;
        private char[] mCharBuf = new char[BUFSIZE];
        private int[] mXBuf = new int[BUFSIZE];
        private int[] mYBuf = new int[BUFSIZE];

        public RingCharBuffer(Context context) {
            mContext = context;
        }
        private int normalize(int in) {
            int ret = in % BUFSIZE;
            return ret < 0 ? ret + BUFSIZE : ret;
        }
        public void push(char c, int x, int y) {
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
                return NULL_CHAR;
            } else {
                mEnd = normalize(mEnd - 1);
                --mLength;
                return mCharBuf[mEnd];
            }
        }
        public char getLastChar() {
            if (mLength < 1) {
                return NULL_CHAR;
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
            StringBuffer sb = new StringBuffer();
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
