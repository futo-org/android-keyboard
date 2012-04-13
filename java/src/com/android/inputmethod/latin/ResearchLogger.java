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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.internal.AlphabetShiftState;
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

/**
 * Logs the use of the LatinIME keyboard.
 *
 * This class logs operations on the IME keyboard, including what the user has typed.
 * Data is stored locally in a file in app-specific storage.
 *
 * This functionality is off by default. See {@link ProductionFlag.IS_EXPERIMENTAL}.
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
    private LogFileManager mLogFileManager;

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

        public synchronized boolean createLogFile() {
            try {
                return createLogFile(DEFAULT_FILENAME);
            } catch (IOException e) {
                e.printStackTrace();
                Log.w(TAG, e);
                return false;
            }
        }

        public synchronized boolean createLogFile(final SharedPreferences prefs) {
            try {
                final String filename =
                        prefs.getString(RESEARCH_LOG_FILENAME_KEY, DEFAULT_FILENAME);
                return createLogFile(filename);
            } catch (IOException e) {
                Log.w(TAG, e);
                e.printStackTrace();
            }
            return false;
        }

        public synchronized boolean createLogFile(final String filename)
                throws IOException {
            if (mIms == null) {
                Log.w(TAG, "InputMethodService is not configured.  Logging is off.");
                return false;
            }
            final File filesDir = mIms.getFilesDir();
            if (filesDir == null || !filesDir.exists()) {
                Log.w(TAG, "Storage directory does not exist.  Logging is off.");
                return false;
            }
            close();
            final File file = new File(filesDir, filename);
            mFile = file;
            file.setReadable(false, false);
            boolean append = true;
            if (file.exists() && file.lastModified() + LOGFILE_PURGE_INTERVAL <
                    System.currentTimeMillis()) {
                append = false;
            }
            mPrintWriter = new PrintWriter(new BufferedWriter(new FileWriter(file, append)), true);
            return true;
        }

        public synchronized boolean append(final String s) {
            final PrintWriter printWriter = mPrintWriter;
            if (printWriter == null) {
                if (DEBUG) {
                    Log.w(TAG, "PrintWriter is null... attempting to create default log file");
                }
                if (!createLogFile()) {
                    if (DEBUG) {
                        Log.w(TAG, "Failed to create log file.  Not logging.");
                        return false;
                    }
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
            }
            if (mFile != null) {
                mFile.delete();
                mFile = null;
            }
        }

        public synchronized void close() {
            if (mPrintWriter != null) {
                mPrintWriter.close();
                mPrintWriter = null;
                mFile = null;
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

    public void initInternal(final InputMethodService ims, final SharedPreferences prefs) {
        mIms = ims;
        final LogFileManager logFileManager = mLogFileManager;
        if (logFileManager != null) {
            logFileManager.init(ims);
            logFileManager.createLogFile(prefs);
        }
        if (prefs != null) {
            sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
            prefs.registerOnSharedPreferenceChangeListener(this);
        }
    }

    /**
     * Change to a different logFileManager.
     *
     * @throws IllegalArgumentException if logFileManager is null
     */
    void setLogFileManager(final LogFileManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("warning: trying to set null logFileManager");
        } else {
            mLogFileManager = manager;
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

        private static final boolean ALPHABETSHIFTSTATE_SETSHIFTED_ENABLED = DEFAULT_ENABLED;
        private static final boolean ALPHABETSHIFTSTATE_SETSHIFTLOCKED_ENABLED = DEFAULT_ENABLED;
        private static final boolean ALPHABETSHIFTSTATE_SETAUTOMATICSHIFTED_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONCANCELINPUT_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONCODEINPUT_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONLONGPRESSTIMEOUT_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONPRESSKEY_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONRELEASEKEY_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONRESTOREKEYBOARDSTATE_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONSAVEKEYBOARDSTATE_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_ONUPDATESHIFTSTATE_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_SETALPHABETKEYBOARD_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_SETSHIFTED_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_SETSHIFTLOCKED_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_SETSYMBOLSKEYBOARD_ENABLED = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_SETSYMBOLSSHIFTEDKEYBOARD_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean KEYBOARDSTATE_TOGGLEALPHABETANDSYMBOLS_ENABLED
                = DEFAULT_ENABLED;
        private static final boolean LATINIME_COMMITCURRENTAUTOCORRECTION_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_ONDISPLAYCOMPLETIONS_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_ONSTARTINPUTVIEWINTERNAL_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_ONUPDATESELECTION_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINIME_SWITCHTOKEYBOARDVIEW_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINKEYBOARDVIEW_ONLONGPRESS_ENABLED = DEFAULT_ENABLED;
        private static final boolean LATINKEYBOARDVIEW_ONPROCESSMOTIONEVENT_ENABLED
                = DEFAULT_ENABLED;
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
                if (mLogFileManager.append(builder.toString())) {
                    // success
                } else {
                    if (DEBUG) {
                        Log.w(TAG, "Unable to write to log.");
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

    public static void alphabetShiftState_setShifted(final boolean newShiftState,
            final int oldState, final AlphabetShiftState alphabetShiftState) {
        if (UnsLogGroup.ALPHABETSHIFTSTATE_SETSHIFTED_ENABLED) {
            final String s = "setShifted(" + newShiftState + "): " + oldState
                    + " > " + alphabetShiftState;
            logUnstructured("AlphabetShiftState_setShifted", s);
        }
    }

    public static void alphabetShiftState_setShiftLocked(final boolean newShiftLockState,
            final int oldState, final AlphabetShiftState alphabetShiftState) {
        if (UnsLogGroup.ALPHABETSHIFTSTATE_SETSHIFTLOCKED_ENABLED) {
            final String s = "setShiftLocked(" + newShiftLockState + "): "
                    + oldState + " > " + alphabetShiftState;
            logUnstructured("AlphabetShiftState_setShiftLocked", s);
        }
    }

    public static void alphabetShiftState_setAutomaticShifted(final int oldState,
            final AlphabetShiftState alphabetShiftState) {
        if (UnsLogGroup.ALPHABETSHIFTSTATE_SETAUTOMATICSHIFTED_ENABLED) {
            final String s = "setAutomaticShifted: " + oldState + " > " + alphabetShiftState;
            logUnstructured("AlphabetShiftState_setAutomaticShifted", s);
        }
    }

    public static void keyboardState_onCancelInput(final boolean isSinglePointer,
            final KeyboardState keyboardState) {
        if (UnsLogGroup.KEYBOARDSTATE_ONCANCELINPUT_ENABLED) {
            final String s = "onCancelInput: single=" + isSinglePointer + " " + keyboardState;
            logUnstructured("KeyboardState_onCancelInput", s);
        }
    }

    public static void keyboardState_onCodeInput(
            final int code, final boolean isSinglePointer, final boolean autoCaps,
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

    public static void keyboardState_onRestoreKeyboardState(final KeyboardState keyboardState,
            final String savedKeyboardState) {
        if (UnsLogGroup.KEYBOARDSTATE_ONRESTOREKEYBOARDSTATE_ENABLED) {
            final String s = "onRestoreKeyboardState: saved=" + savedKeyboardState + " "
                    + keyboardState;
            logUnstructured("KeyboardState_onRestoreKeyboardState", s);
        }
    }

    public static void keyboardState_onSaveKeyboardState(final KeyboardState keyboardState,
            final String savedKeyboardState) {
        if (UnsLogGroup.KEYBOARDSTATE_ONSAVEKEYBOARDSTATE_ENABLED) {
            final String s = "onSaveKeyboardState: saved=" + savedKeyboardState + " "
                    + keyboardState;
            logUnstructured("KeyboardState_onSaveKeyboardState", s);
        }
    }

    public static void keyboardState_onUpdateShiftState(final KeyboardState keyboardState,
            final boolean autoCaps) {
        if (UnsLogGroup.KEYBOARDSTATE_ONUPDATESHIFTSTATE_ENABLED) {
            final String s = "onUpdateShiftState: autoCaps=" + autoCaps + " " + keyboardState;
            logUnstructured("KeyboardState_onUpdateShiftState", s);
        }
    }

    public static void keyboardState_setAlphabetKeyboard() {
        if (UnsLogGroup.KEYBOARDSTATE_SETALPHABETKEYBOARD_ENABLED) {
            final String s = "setAlphabetKeyboard";
            logUnstructured("KeyboardState_setAlphabetKeyboard", s);
        }
    }

    public static void keyboardState_setShifted(final KeyboardState keyboardState,
            final String shiftMode) {
        if (UnsLogGroup.KEYBOARDSTATE_SETSHIFTED_ENABLED) {
            final String s = "setShifted: shiftMode=" + shiftMode + " " + keyboardState;
            logUnstructured("KeyboardState_setShifted", s);
        }
    }

    public static void keyboardState_setShiftLocked(final KeyboardState keyboardState,
            final boolean shiftLocked) {
        if (UnsLogGroup.KEYBOARDSTATE_SETSHIFTLOCKED_ENABLED) {
            final String s = "setShiftLocked: shiftLocked=" + shiftLocked + " " + keyboardState;
            logUnstructured("KeyboardState_setShiftLocked", s);
        }
    }

    public static void keyboardState_setSymbolsKeyboard() {
        if (UnsLogGroup.KEYBOARDSTATE_SETSYMBOLSKEYBOARD_ENABLED) {
            final String s = "setSymbolsKeyboard";
            logUnstructured("KeyboardState_setSymbolsKeyboard", s);
        }
    }

    public static void keyboardState_setSymbolsShiftedKeyboard() {
        if (UnsLogGroup.KEYBOARDSTATE_SETSYMBOLSSHIFTEDKEYBOARD_ENABLED) {
            final String s = "setSymbolsShiftedKeyboard";
            logUnstructured("KeyboardState_setSymbolsShiftedKeyboard", s);
        }
    }

    public static void keyboardState_toggleAlphabetAndSymbols(final KeyboardState keyboardState) {
        if (UnsLogGroup.KEYBOARDSTATE_TOGGLEALPHABETANDSYMBOLS_ENABLED) {
            final String s = "toggleAlphabetAndSymbols: " + keyboardState;
            logUnstructured("KeyboardState_toggleAlphabetAndSymbols", s);
        }
    }

    public static void LatinIME_commitCurrentAutoCorrection(final String typedWord,
            final String autoCorrection) {
        if (UnsLogGroup.LATINIME_COMMITCURRENTAUTOCORRECTION_ENABLED) {
            if (typedWord.equals(autoCorrection)) {
                getInstance().logCorrection("[----]", typedWord, autoCorrection, -1);
            } else {
                getInstance().logCorrection("[Auto]", typedWord, autoCorrection, -1);
            }
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

    public static void latinIME_onStartInputViewInternal(final EditorInfo editorInfo) {
        if (UnsLogGroup.LATINIME_ONSTARTINPUTVIEWINTERNAL_ENABLED) {
            final StringBuilder builder = new StringBuilder();
            builder.append("onStartInputView: editorInfo:");
            builder.append("inputType=");
            builder.append(editorInfo.inputType);
            builder.append("imeOptions=");
            builder.append(editorInfo.imeOptions);
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