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
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * Logs the use of the LatinIME keyboard.
 *
 * This class logs operations on the IME keyboard, including what the user has typed.
 * Data is stored locally in a file in app-specific storage.
 *
 * This functionality is off by default. See {@link ProductionFlag#IS_EXPERIMENTAL}.
 */
public class ResearchLogger implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = ResearchLogger.class.getSimpleName();
    private static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";
    private static final boolean DEBUG = false;

    private static final ResearchLogger sInstance = new ResearchLogger(new LogFileManager());
    public static boolean sIsLogging = false;
    /* package */ final Handler mLoggingHandler;
    private InputMethodService mIms;

    /**
     * Isolates management of files. This variable should never be null, but can be changed
     * to support testing.
     */
    /* package */ LogFileManager mLogFileManager;

    /**
     * Manages the file(s) that stores the logs.
     *
     * Handles creation, deletion, and provides Readers, Writers, and InputStreams to access
     * the logs.
     */
    /* package */ static class LogFileManager {
        public static final String RESEARCH_LOG_FILENAME_KEY = "RESEARCH_LOG_FILENAME";

        private static final String DEFAULT_FILENAME = "researchLog.txt";
        private static final long LOGFILE_PURGE_INTERVAL = 1000 * 60 * 60 * 24;

        protected InputMethodService mIms;
        protected File mFile;
        protected PrintWriter mPrintWriter;

        /* package */ LogFileManager() {
        }

        public void init(final InputMethodService ims) {
            mIms = ims;
        }

        public synchronized void createLogFile() throws IOException {
            createLogFile(DEFAULT_FILENAME);
        }

        public synchronized void createLogFile(final SharedPreferences prefs)
                throws IOException {
            final String filename =
                    prefs.getString(RESEARCH_LOG_FILENAME_KEY, DEFAULT_FILENAME);
            createLogFile(filename);
        }

        public synchronized void createLogFile(final String filename)
                throws IOException {
            if (mIms == null) {
                final String msg = "InputMethodService is not configured.  Logging is off.";
                Log.w(TAG, msg);
                throw new IOException(msg);
            }
            final File filesDir = mIms.getFilesDir();
            if (filesDir == null || !filesDir.exists()) {
                final String msg = "Storage directory does not exist.  Logging is off.";
                Log.w(TAG, msg);
                throw new IOException(msg);
            }
            close();
            final File file = new File(filesDir, filename);
            mFile = file;
            boolean append = true;
            if (file.exists() && file.lastModified() + LOGFILE_PURGE_INTERVAL <
                    System.currentTimeMillis()) {
                append = false;
            }
            mPrintWriter = new PrintWriter(new BufferedWriter(new FileWriter(file, append)), true);
        }

        public synchronized boolean append(final String s) {
            PrintWriter printWriter = mPrintWriter;
            if (printWriter == null || !mFile.exists()) {
                if (DEBUG) {
                    Log.w(TAG, "PrintWriter is null... attempting to create default log file");
                }
                try {
                    createLogFile();
                    printWriter = mPrintWriter;
                } catch (IOException e) {
                    Log.w(TAG, "Failed to create log file.  Not logging.");
                    return false;
                }
            }
            printWriter.print(s);
            printWriter.flush();
            return !printWriter.checkError();
        }

        public synchronized void reset() {
            if (mPrintWriter != null) {
                mPrintWriter.close();
                mPrintWriter = null;
                if (DEBUG) {
                    Log.d(TAG, "logfile closed");
                }
            }
            if (mFile != null) {
                mFile.delete();
                if (DEBUG) {
                    Log.d(TAG, "logfile deleted");
                }
                mFile = null;
            }
        }

        public synchronized void close() {
            if (mPrintWriter != null) {
                mPrintWriter.close();
                mPrintWriter = null;
                mFile = null;
                if (DEBUG) {
                    Log.d(TAG, "logfile closed");
                }
            }
        }

        /* package */ synchronized void flush() {
            if (mPrintWriter != null) {
                mPrintWriter.flush();
            }
        }

        /* package */ synchronized String getContents() {
            final File file = mFile;
            if (file == null) {
                return "";
            }
            if (mPrintWriter != null) {
                mPrintWriter.flush();
            }
            FileInputStream stream = null;
            FileChannel fileChannel = null;
            String s = "";
            try {
                stream = new FileInputStream(file);
                fileChannel = stream.getChannel();
                final ByteBuffer byteBuffer = ByteBuffer.allocate((int) file.length());
                fileChannel.read(byteBuffer);
                byteBuffer.rewind();
                CharBuffer charBuffer = Charset.defaultCharset().decode(byteBuffer);
                s = charBuffer.toString();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fileChannel != null) {
                        fileChannel.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (stream != null) {
                            stream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return s;
        }
    }

    private ResearchLogger(final LogFileManager logFileManager) {
        final HandlerThread handlerThread = new HandlerThread("ResearchLogger logging task",
                Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        mLoggingHandler = new Handler(handlerThread.getLooper());
        mLogFileManager = logFileManager;
    }

    public static ResearchLogger getInstance() {
        return sInstance;
    }

    public static void init(final InputMethodService ims, final SharedPreferences prefs) {
        sInstance.initInternal(ims, prefs);
    }

    /* package */ void initInternal(final InputMethodService ims, final SharedPreferences prefs) {
        mIms = ims;
        final LogFileManager logFileManager = mLogFileManager;
        if (logFileManager != null) {
            logFileManager.init(ims);
            try {
                logFileManager.createLogFile(prefs);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (prefs != null) {
            sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
            prefs.registerOnSharedPreferenceChangeListener(this);
        }
    }

    /**
     * Represents a category of logging events that share the same subfield structure.
     */
    private static enum LogGroup {
        MOTION_EVENT("m"),
        KEY("k"),
        CORRECTION("c"),
        STATE_CHANGE("s"),
        UNSTRUCTURED("u");

        private final String mLogString;

        private LogGroup(final String logString) {
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
            final StringBuilder sb = new StringBuilder();
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

    public void logKeyEvent(final int code, final int x, final int y) {
        final StringBuilder sb = new StringBuilder();
        sb.append(Keyboard.printableCode(code));
        sb.append('\t'); sb.append(x);
        sb.append('\t'); sb.append(y);
        write(LogGroup.KEY, sb.toString());
    }

    public void logCorrection(final String subgroup, final String before, final String after,
            final int position) {
        final StringBuilder sb = new StringBuilder();
        sb.append(subgroup);
        sb.append('\t'); sb.append(before);
        sb.append('\t'); sb.append(after);
        sb.append('\t'); sb.append(position);
        write(LogGroup.CORRECTION, sb.toString());
    }

    public void logStateChange(final String subgroup, final String details) {
        write(LogGroup.STATE_CHANGE, subgroup + "\t" + details);
    }

    public static class UnsLogGroup {
        private static final boolean DEFAULT_ENABLED = true;

        private static final boolean KEYBOARDSTATE_ONCANCELINPUT_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONCODEINPUT_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONLONGPRESSTIMEOUT_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONPRESSKEY_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONRELEASEKEY_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_COMMITCURRENTAUTOCORRECTION_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_COMMITTEXT_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_DELETESURROUNDINGTEXT_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_DOUBLESPACEAUTOPERIOD_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_ONDISPLAYCOMPLETIONS_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_ONSTARTINPUTVIEWINTERNAL_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_ONUPDATESELECTION_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_PERFORMEDITORACTION_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_PICKAPPLICATIONSPECIFIEDCOMPLETION_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean LATINIME_PICKPUNCTUATIONSUGGESTION_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_PICKSUGGESTIONMANUALLY_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_REVERTCOMMIT_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_REVERTDOUBLESPACEWHILEINBATCHEDIT_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean LATINIME_REVERTSWAPPUNCTUATION_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_SENDKEYCODEPOINT_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_SWAPSWAPPERANDSPACEWHILEINBATCHEDIT_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean LATINIME_SWITCHTOKEYBOARDVIEW_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINKEYBOARDVIEW_ONLONGPRESS_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINKEYBOARDVIEW_ONPROCESSMOTIONEVENT_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean LATINKEYBOARDVIEW_SETKEYBOARD_ENABLED = DEFAULT_ENABLED;
        private static final boolean POINTERTRACKER_CALLLISTENERONCANCELINPUT_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean POINTERTRACKER_CALLLISTENERONCODEINPUT_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean
                POINTERTRACKER_CALLLISTENERONPRESSANDCHECKKEYBOARDLAYOUTCHANGE_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean POINTERTRACKER_CALLLISTENERONRELEASE_ENABLED = DEFAULT_ENABLED;
        private static final boolean POINTERTRACKER_ONDOWNEVENT_ENABLED = DEFAULT_ENABLED;
        private static final boolean POINTERTRACKER_ONMOVEEVENT_ENABLED = DEFAULT_ENABLED;
        private static final boolean SUDDENJUMPINGTOUCHEVENTHANDLER_ONTOUCHEVENT_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean SUGGESTIONSVIEW_SETSUGGESTIONS_ENABLED = DEFAULT_ENABLED;
    }

    public static void logUnstructured(String logGroup, final String details) {
        // TODO: improve performance by making entire class static and/or implementing natively
        getInstance().write(LogGroup.UNSTRUCTURED, logGroup + "\t" + details);
    }

    private void write(final LogGroup logGroup, final String log) {
        // TODO: rewrite in native for better performance
        mLoggingHandler.post(new Runnable() {
            @Override
            public void run() {
                final long currentTime = System.currentTimeMillis();
                final long upTime = SystemClock.uptimeMillis();
                final StringBuilder builder = new StringBuilder();
                builder.append(currentTime);
                builder.append('\t'); builder.append(upTime);
                builder.append('\t'); builder.append(logGroup.mLogString);
                builder.append('\t'); builder.append(log);
                builder.append('\n');
                if (DEBUG) {
                    Log.d(TAG, "Write: " + '[' + logGroup.mLogString + ']' + log);
                }
                final String s = builder.toString();
                if (mLogFileManager.append(s)) {
                    // success
                } else {
                    if (DEBUG) {
                        Log.w(TAG, "Unable to write to log.");
                    }
                    // perhaps logfile was deleted.  try to recreate and relog.
                    try {
                        mLogFileManager.createLogFile(PreferenceManager
                                .getDefaultSharedPreferences(mIms));
                        mLogFileManager.append(s);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void clearAll() {
        mLoggingHandler.post(new Runnable() {
            @Override
            public void run() {
                if (DEBUG) {
                    Log.d(TAG, "Delete log file.");
                }
                mLogFileManager.reset();
            }
        });
    }

    /* package */ LogFileManager getLogFileManager() {
        return mLogFileManager;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null || prefs == null) {
            return;
        }
        sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
    }

    public static void keyboardState_onCancelInput(final boolean isSinglePointer,
            final KeyboardState keyboardState) {
        if (UnsLogGroup.KEYBOARDSTATE_ONCANCELINPUT_ENABLED) {
            final String s = "onCancelInput: single=" + isSinglePointer + " " + keyboardState;
            logUnstructured("KeyboardState_onCancelInput", s);
        }
    }

    public static void keyboardState_onCodeInput(
            final int code, final boolean isSinglePointer, final int autoCaps,
            final KeyboardState keyboardState) {
        if (UnsLogGroup.KEYBOARDSTATE_ONCODEINPUT_ENABLED) {
            final String s = "onCodeInput: code=" + Keyboard.printableCode(code)
                    + " single=" + isSinglePointer
                    + " autoCaps=" + autoCaps + " " + keyboardState;
            logUnstructured("KeyboardState_onCodeInput", s);
        }
    }

    public static void keyboardState_onLongPressTimeout(final int code,
            final KeyboardState keyboardState) {
        if (UnsLogGroup.KEYBOARDSTATE_ONLONGPRESSTIMEOUT_ENABLED) {
            final String s = "onLongPressTimeout: code=" + Keyboard.printableCode(code) + " "
                    + keyboardState;
            logUnstructured("KeyboardState_onLongPressTimeout", s);
        }
    }

    public static void keyboardState_onPressKey(final int code,
            final KeyboardState keyboardState) {
        if (UnsLogGroup.KEYBOARDSTATE_ONPRESSKEY_ENABLED) {
            final String s = "onPressKey: code=" + Keyboard.printableCode(code) + " "
                    + keyboardState;
            logUnstructured("KeyboardState_onPressKey", s);
        }
    }

    public static void keyboardState_onReleaseKey(final KeyboardState keyboardState, final int code,
            final boolean withSliding) {
        if (UnsLogGroup.KEYBOARDSTATE_ONRELEASEKEY_ENABLED) {
            final String s = "onReleaseKey: code=" + Keyboard.printableCode(code)
                    + " sliding=" + withSliding + " " + keyboardState;
            logUnstructured("KeyboardState_onReleaseKey", s);
        }
    }

    public static void latinIME_commitCurrentAutoCorrection(final String typedWord,
            final String autoCorrection) {
        if (UnsLogGroup.LATINIME_COMMITCURRENTAUTOCORRECTION_ENABLED) {
            if (typedWord.equals(autoCorrection)) {
                getInstance().logCorrection("[----]", typedWord, autoCorrection, -1);
            } else {
                getInstance().logCorrection("[Auto]", typedWord, autoCorrection, -1);
            }
        }
    }

    public static void latinIME_commitText(final CharSequence typedWord) {
        if (UnsLogGroup.LATINIME_COMMITTEXT_ENABLED) {
            logUnstructured("LatinIME_commitText", typedWord.toString());
        }
    }

    public static void latinIME_deleteSurroundingText(final int length) {
        if (UnsLogGroup.LATINIME_DELETESURROUNDINGTEXT_ENABLED) {
            logUnstructured("LatinIME_deleteSurroundingText", String.valueOf(length));
        }
    }

    public static void latinIME_doubleSpaceAutoPeriod() {
        if (UnsLogGroup.LATINIME_DOUBLESPACEAUTOPERIOD_ENABLED) {
            logUnstructured("LatinIME_doubleSpaceAutoPeriod", "");
        }
    }

    public static void latinIME_onDisplayCompletions(
            final CompletionInfo[] applicationSpecifiedCompletions) {
        if (UnsLogGroup.LATINIME_ONDISPLAYCOMPLETIONS_ENABLED) {
            final StringBuilder builder = new StringBuilder();
            builder.append("Received completions:");
            if (applicationSpecifiedCompletions != null) {
                for (int i = 0; i < applicationSpecifiedCompletions.length; i++) {
                    builder.append("  #");
                    builder.append(i);
                    builder.append(": ");
                    builder.append(applicationSpecifiedCompletions[i]);
                    builder.append("\n");
                }
            }
            logUnstructured("LatinIME_onDisplayCompletions", builder.toString());
        }
    }

    public static void latinIME_onStartInputViewInternal(final EditorInfo editorInfo,
            final SharedPreferences prefs) {
        if (UnsLogGroup.LATINIME_ONSTARTINPUTVIEWINTERNAL_ENABLED) {
            final StringBuilder builder = new StringBuilder();
            builder.append("onStartInputView: editorInfo:");
            builder.append("\tinputType=");
            builder.append(Integer.toHexString(editorInfo.inputType));
            builder.append("\timeOptions=");
            builder.append(Integer.toHexString(editorInfo.imeOptions));
            builder.append("\tdisplay="); builder.append(Build.DISPLAY);
            builder.append("\tmodel="); builder.append(Build.MODEL);
            for (Map.Entry<String,?> entry : prefs.getAll().entrySet()) {
                builder.append("\t" + entry.getKey());
                Object value = entry.getValue();
                builder.append("=" + ((value == null) ? "<null>" : value.toString()));
            }
            logUnstructured("LatinIME_onStartInputViewInternal", builder.toString());
        }
    }

    public static void latinIME_onUpdateSelection(final int lastSelectionStart,
            final int lastSelectionEnd, final int oldSelStart, final int oldSelEnd,
            final int newSelStart, final int newSelEnd, final int composingSpanStart,
            final int composingSpanEnd) {
        if (UnsLogGroup.LATINIME_ONUPDATESELECTION_ENABLED) {
            final String s = "onUpdateSelection: oss=" + oldSelStart
                    + ", ose=" + oldSelEnd
                    + ", lss=" + lastSelectionStart
                    + ", lse=" + lastSelectionEnd
                    + ", nss=" + newSelStart
                    + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart
                    + ", ce=" + composingSpanEnd;
            logUnstructured("LatinIME_onUpdateSelection", s);
        }
    }

    public static void latinIME_performEditorAction(final int imeActionNext) {
        if (UnsLogGroup.LATINIME_PERFORMEDITORACTION_ENABLED) {
            logUnstructured("LatinIME_performEditorAction", String.valueOf(imeActionNext));
        }
    }

    public static void latinIME_pickApplicationSpecifiedCompletion(final int index,
            final CharSequence text, int x, int y) {
        if (UnsLogGroup.LATINIME_PICKAPPLICATIONSPECIFIEDCOMPLETION_ENABLED) {
            final String s = String.valueOf(index) + '\t' + text + '\t' + x + '\t' + y;
            logUnstructured("LatinIME_pickApplicationSpecifiedCompletion", s);
        }
    }

    public static void latinIME_pickSuggestionManually(final String replacedWord,
            final int index, CharSequence suggestion, int x, int y) {
        if (UnsLogGroup.LATINIME_PICKSUGGESTIONMANUALLY_ENABLED) {
            final String s = String.valueOf(index) + '\t' + suggestion + '\t' + x + '\t' + y;
            logUnstructured("LatinIME_pickSuggestionManually", s);
        }
    }

    public static void latinIME_punctuationSuggestion(final int index,
            final CharSequence suggestion, int x, int y) {
        if (UnsLogGroup.LATINIME_PICKPUNCTUATIONSUGGESTION_ENABLED) {
            final String s = String.valueOf(index) + '\t' + suggestion + '\t' + x + '\t' + y;
            logUnstructured("LatinIME_pickPunctuationSuggestion", s);
        }
    }

    public static void latinIME_revertDoubleSpaceWhileInBatchEdit() {
        if (UnsLogGroup.LATINIME_REVERTDOUBLESPACEWHILEINBATCHEDIT_ENABLED) {
            logUnstructured("LatinIME_revertDoubleSpaceWhileInBatchEdit", "");
        }
    }

    public static void latinIME_revertSwapPunctuation() {
        if (UnsLogGroup.LATINIME_REVERTSWAPPUNCTUATION_ENABLED) {
            logUnstructured("LatinIME_revertSwapPunctuation", "");
        }
    }

    public static void latinIME_sendKeyCodePoint(final int code) {
        if (UnsLogGroup.LATINIME_SENDKEYCODEPOINT_ENABLED) {
            logUnstructured("LatinIME_sendKeyCodePoint", String.valueOf(code));
        }
    }

    public static void latinIME_swapSwapperAndSpaceWhileInBatchEdit() {
        if (UnsLogGroup.LATINIME_SWAPSWAPPERANDSPACEWHILEINBATCHEDIT_ENABLED) {
            logUnstructured("latinIME_swapSwapperAndSpaceWhileInBatchEdit", "");
        }
    }

    public static void latinIME_switchToKeyboardView() {
        if (UnsLogGroup.LATINIME_SWITCHTOKEYBOARDVIEW_ENABLED) {
            final String s = "Switch to keyboard view.";
            logUnstructured("LatinIME_switchToKeyboardView", s);
        }
    }

    public static void latinKeyboardView_onLongPress() {
        if (UnsLogGroup.LATINKEYBOARDVIEW_ONLONGPRESS_ENABLED) {
            final String s = "long press detected";
            logUnstructured("LatinKeyboardView_onLongPress", s);
        }
    }

    public static void latinKeyboardView_processMotionEvent(MotionEvent me, int action,
            long eventTime, int index, int id, int x, int y) {
        if (UnsLogGroup.LATINKEYBOARDVIEW_ONPROCESSMOTIONEVENT_ENABLED) {
            final float size = me.getSize(index);
            final float pressure = me.getPressure(index);
            if (action != MotionEvent.ACTION_MOVE) {
                getInstance().logMotionEvent(action, eventTime, id, x, y, size, pressure);
            }
        }
    }

    public static void latinKeyboardView_setKeyboard(final Keyboard keyboard) {
        if (UnsLogGroup.LATINKEYBOARDVIEW_SETKEYBOARD_ENABLED) {
            StringBuilder builder = new StringBuilder();
            builder.append("id=");
            builder.append(keyboard.mId);
            builder.append("\tw=");
            builder.append(keyboard.mOccupiedWidth);
            builder.append("\th=");
            builder.append(keyboard.mOccupiedHeight);
            builder.append("\tkeys=[");
            boolean first = true;
            for (Key key : keyboard.mKeys) {
                if (first) {
                    first = false;
                } else {
                    builder.append(",");
                }
                builder.append("{code:");
                builder.append(key.mCode);
                builder.append(",altCode:");
                builder.append(key.mAltCode);
                builder.append(",x:");
                builder.append(key.mX);
                builder.append(",y:");
                builder.append(key.mY);
                builder.append(",w:");
                builder.append(key.mWidth);
                builder.append(",h:");
                builder.append(key.mHeight);
                builder.append("}");
            }
            builder.append("]");
            logUnstructured("LatinKeyboardView_setKeyboard", builder.toString());
        }
    }

    public static void latinIME_revertCommit(final String originallyTypedWord) {
        if (UnsLogGroup.LATINIME_REVERTCOMMIT_ENABLED) {
            logUnstructured("LatinIME_revertCommit", originallyTypedWord);
        }
    }

    public static void pointerTracker_callListenerOnCancelInput() {
        final String s = "onCancelInput";
        if (UnsLogGroup.POINTERTRACKER_CALLLISTENERONCANCELINPUT_ENABLED) {
            logUnstructured("PointerTracker_callListenerOnCancelInput", s);
        }
    }

    public static void pointerTracker_callListenerOnCodeInput(final Key key, final int x,
            final int y, final boolean ignoreModifierKey, final boolean altersCode,
            final int code) {
        if (UnsLogGroup.POINTERTRACKER_CALLLISTENERONCODEINPUT_ENABLED) {
            final String s = "onCodeInput: " + Keyboard.printableCode(code)
                    + " text=" + key.mOutputText + " x=" + x + " y=" + y
                    + " ignoreModifier=" + ignoreModifierKey + " altersCode=" + altersCode
                    + " enabled=" + key.isEnabled();
            logUnstructured("PointerTracker_callListenerOnCodeInput", s);
        }
    }

    public static void pointerTracker_callListenerOnPressAndCheckKeyboardLayoutChange(
            final Key key, final boolean ignoreModifierKey) {
        if (UnsLogGroup.POINTERTRACKER_CALLLISTENERONPRESSANDCHECKKEYBOARDLAYOUTCHANGE_ENABLED) {
            final String s = "onPress    : " + KeyDetector.printableCode(key)
                    + " ignoreModifier=" + ignoreModifierKey
                    + " enabled=" + key.isEnabled();
            logUnstructured("PointerTracker_callListenerOnPressAndCheckKeyboardLayoutChange", s);
        }
    }

    public static void pointerTracker_callListenerOnRelease(final Key key, final int primaryCode,
            final boolean withSliding, final boolean ignoreModifierKey) {
        if (UnsLogGroup.POINTERTRACKER_CALLLISTENERONRELEASE_ENABLED) {
            final String s = "onRelease  : " + Keyboard.printableCode(primaryCode)
                    + " sliding=" + withSliding + " ignoreModifier=" + ignoreModifierKey
                    + " enabled="+ key.isEnabled();
            logUnstructured("PointerTracker_callListenerOnRelease", s);
        }
    }

    public static void pointerTracker_onDownEvent(long deltaT, int distanceSquared) {
        if (UnsLogGroup.POINTERTRACKER_ONDOWNEVENT_ENABLED) {
            final String s = "onDownEvent: ignore potential noise: time=" + deltaT
                    + " distance=" + distanceSquared;
            logUnstructured("PointerTracker_onDownEvent", s);
        }
    }

    public static void pointerTracker_onMoveEvent(final int x, final int y, final int lastX,
            final int lastY) {
        if (UnsLogGroup.POINTERTRACKER_ONMOVEEVENT_ENABLED) {
            final String s = String.format("onMoveEvent: sudden move is translated to "
                    + "up[%d,%d]/down[%d,%d] events", lastX, lastY, x, y);
            logUnstructured("PointerTracker_onMoveEvent", s);
        }
    }

    public static void suddenJumpingTouchEventHandler_onTouchEvent(final MotionEvent me) {
        if (UnsLogGroup.SUDDENJUMPINGTOUCHEVENTHANDLER_ONTOUCHEVENT_ENABLED) {
            final String s = "onTouchEvent: ignore sudden jump " + me;
            logUnstructured("SuddenJumpingTouchEventHandler_onTouchEvent", s);
        }
    }

    public static void suggestionsView_setSuggestions(final SuggestedWords mSuggestedWords) {
        if (UnsLogGroup.SUGGESTIONSVIEW_SETSUGGESTIONS_ENABLED) {
            logUnstructured("SuggestionsView_setSuggestions", mSuggestedWords.toString());
        }
    }
}