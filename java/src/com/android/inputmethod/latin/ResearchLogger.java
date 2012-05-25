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
import android.content.SharedPreferences.Editor;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.JsonWriter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
    private static final boolean DEBUG = false;
    /* package */ static boolean sIsLogging = false;
    private static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";
    private static final String FILENAME_PREFIX = "researchLog";
    private static final String FILENAME_SUFFIX = ".txt";
    private static final JsonWriter NULL_JSON_WRITER = new JsonWriter(
            new OutputStreamWriter(new NullOutputStream()));
    private static final SimpleDateFormat TIMESTAMP_DATEFORMAT =
            new SimpleDateFormat("yyyyMMDDHHmmss", Locale.US);

    // constants related to specific log points
    private static final String WHITESPACE_SEPARATORS = " \t\n\r";
    private static final int MAX_INPUTVIEW_LENGTH_TO_CAPTURE = 8192; // must be >=1
    private static final String PREF_RESEARCH_LOGGER_UUID_STRING = "pref_research_logger_uuid";

    private static final ResearchLogger sInstance = new ResearchLogger();
    private HandlerThread mHandlerThread;
    /* package */ Handler mLoggingHandler;
    // to write to a different filename, e.g., for testing, set mFile before calling start()
    private File mFilesDir;
    /* package */ File mFile;
    private JsonWriter mJsonWriter = NULL_JSON_WRITER; // should never be null

    private int mLoggingState;
    private static final int LOGGING_STATE_OFF = 0;
    private static final int LOGGING_STATE_ON = 1;
    private static final int LOGGING_STATE_STOPPING = 2;

    // set when LatinIME should ignore an onUpdateSelection() callback that
    // arises from operations in this class
    private static boolean sLatinIMEExpectingUpdateSelection = false;

    private static class NullOutputStream extends OutputStream {
        /** {@inheritDoc} */
        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            // nop
        }

        /** {@inheritDoc} */
        @Override
        public void write(byte[] buffer) throws IOException {
            // nop
        }

        @Override
        public void write(int oneByte) {
        }
    }

    private ResearchLogger() {
        mLoggingState = LOGGING_STATE_OFF;
    }

    public static ResearchLogger getInstance() {
        return sInstance;
    }

    public void init(final InputMethodService ims, final SharedPreferences prefs) {
        assert ims != null;
        if (ims == null) {
            Log.w(TAG, "IMS is null; logging is off");
        } else {
            mFilesDir = ims.getFilesDir();
            if (mFilesDir == null || !mFilesDir.exists()) {
                Log.w(TAG, "IME storage directory does not exist.");
            }
        }
        if (prefs != null) {
            sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
            prefs.registerOnSharedPreferenceChangeListener(sInstance);
        }
    }

    public synchronized void start() {
        Log.d(TAG, "start called");
        if (mFilesDir == null || !mFilesDir.exists()) {
            Log.w(TAG, "IME storage directory does not exist.  Cannot start logging.");
        } else {
            if (mHandlerThread == null || !mHandlerThread.isAlive()) {
                mHandlerThread = new HandlerThread("ResearchLogger logging task",
                        Process.THREAD_PRIORITY_BACKGROUND);
                mHandlerThread.start();
                mLoggingHandler = null;
                mLoggingState = LOGGING_STATE_OFF;
            }
            if (mLoggingHandler == null) {
                mLoggingHandler = new Handler(mHandlerThread.getLooper());
                mLoggingState = LOGGING_STATE_OFF;
            }
            if (mFile == null) {
                final String timestampString = TIMESTAMP_DATEFORMAT.format(new Date());
                mFile = new File(mFilesDir, FILENAME_PREFIX + timestampString + FILENAME_SUFFIX);
            }
            if (mLoggingState == LOGGING_STATE_OFF) {
                try {
                    mJsonWriter = new JsonWriter(new BufferedWriter(new FileWriter(mFile)));
                    mJsonWriter.setLenient(true);
                    mJsonWriter.beginArray();
                    mLoggingState = LOGGING_STATE_ON;
                } catch (IOException e) {
                    Log.w(TAG, "cannot start JsonWriter");
                    mJsonWriter = NULL_JSON_WRITER;
                    e.printStackTrace();
                }
            }
        }
    }

    public synchronized void stop() {
        Log.d(TAG, "stop called");
        if (mLoggingHandler != null && mLoggingState == LOGGING_STATE_ON) {
            mLoggingState = LOGGING_STATE_STOPPING;
            // put this in the Handler queue so pending writes are processed first.
            mLoggingHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "closing jsonwriter");
                        mJsonWriter.endArray();
                        mJsonWriter.flush();
                        mJsonWriter.close();
                    } catch (IllegalStateException e1) {
                        // assume that this is just the json not being terminated properly.
                        // ignore
                        e1.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mJsonWriter = NULL_JSON_WRITER;
                    mFile = null;
                    mLoggingState = LOGGING_STATE_OFF;
                    if (DEBUG) {
                        Log.d(TAG, "logfile closed");
                    }
                    Log.d(TAG, "finished stop(), notifying");
                    synchronized (ResearchLogger.this) {
                        ResearchLogger.this.notify();
                    }
                }
            });
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /* package */ synchronized void flush() {
        try {
            mJsonWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null || prefs == null) {
            return;
        }
        sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
    }

    private static final String CURRENT_TIME_KEY = "_ct";
    private static final String UPTIME_KEY = "_ut";
    private static final String EVENT_TYPE_KEY = "_ty";
    private static final Object[] EVENTKEYS_NULLVALUES = {};

    /**
     * Write a description of the event out to the ResearchLog.
     *
     * Runs in the background to avoid blocking the UI thread.
     *
     * @param keys an array containing a descriptive name for the event, followed by the keys
     * @param values an array of values, either a String or Number.  length should be one
     * less than the keys array
     */
    private synchronized void writeEvent(final String[] keys, final Object[] values) {
        assert values.length + 1 == keys.length;
        if (mLoggingState == LOGGING_STATE_ON) {
            mLoggingHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mJsonWriter.beginObject();
                        mJsonWriter.name(CURRENT_TIME_KEY).value(System.currentTimeMillis());
                        mJsonWriter.name(UPTIME_KEY).value(SystemClock.uptimeMillis());
                        mJsonWriter.name(EVENT_TYPE_KEY).value(keys[0]);
                        final int length = values.length;
                        for (int i = 0; i < length; i++) {
                            mJsonWriter.name(keys[i + 1]);
                            Object value = values[i];
                            if (value instanceof String) {
                                mJsonWriter.value((String) value);
                            } else if (value instanceof Number) {
                                mJsonWriter.value((Number) value);
                            } else if (value instanceof Boolean) {
                                mJsonWriter.value((Boolean) value);
                            } else if (value instanceof CompletionInfo[]) {
                                CompletionInfo[] ci = (CompletionInfo[]) value;
                                mJsonWriter.beginArray();
                                for (int j = 0; j < ci.length; j++) {
                                    mJsonWriter.value(ci[j].toString());
                                }
                                mJsonWriter.endArray();
                            } else if (value instanceof SharedPreferences) {
                                SharedPreferences prefs = (SharedPreferences) value;
                                mJsonWriter.beginObject();
                                for (Map.Entry<String,?> entry : prefs.getAll().entrySet()) {
                                    mJsonWriter.name(entry.getKey());
                                    final Object innerValue = entry.getValue();
                                    if (innerValue == null) {
                                        mJsonWriter.nullValue();
                                    } else {
                                        mJsonWriter.value(innerValue.toString());
                                    }
                                }
                                mJsonWriter.endObject();
                            } else if (value instanceof Keyboard) {
                                Keyboard keyboard = (Keyboard) value;
                                mJsonWriter.beginArray();
                                for (Key key : keyboard.mKeys) {
                                    mJsonWriter.beginObject();
                                    mJsonWriter.name("code").value(key.mCode);
                                    mJsonWriter.name("altCode").value(key.mAltCode);
                                    mJsonWriter.name("x").value(key.mX);
                                    mJsonWriter.name("y").value(key.mY);
                                    mJsonWriter.name("w").value(key.mWidth);
                                    mJsonWriter.name("h").value(key.mHeight);
                                    mJsonWriter.endObject();
                                }
                                mJsonWriter.endArray();
                            } else if (value == null) {
                                mJsonWriter.nullValue();
                            } else {
                                Log.w(TAG, "Unrecognized type to be logged: " +
                                        (value == null ? "<null>" : value.getClass().getName()));
                                mJsonWriter.nullValue();
                            }
                        }
                        mJsonWriter.endObject();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.w(TAG, "Error in JsonWriter; disabling logging");
                        try {
                            mJsonWriter.close();
                        } catch (IllegalStateException e1) {
                            // assume that this is just the json not being terminated properly.
                            // ignore
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        } finally {
                            mJsonWriter = NULL_JSON_WRITER;
                        }
                    }
                }
            });
        }
    }

    private static final String[] EVENTKEYS_LATINKEYBOARDVIEW_PROCESSMOTIONEVENT = {
        "LATINKEYBOARDVIEW_PROCESSMOTIONEVENT", "action", "eventTime", "id", "x", "y", "size",
        "pressure"
    };
    public static void latinKeyboardView_processMotionEvent(final MotionEvent me, final int action,
            final long eventTime, final int index, final int id, final int x, final int y) {
        if (me != null) {
            final String actionString;
            switch (action) {
                case MotionEvent.ACTION_CANCEL: actionString = "CANCEL"; break;
                case MotionEvent.ACTION_UP: actionString = "UP"; break;
                case MotionEvent.ACTION_DOWN: actionString = "DOWN"; break;
                case MotionEvent.ACTION_POINTER_UP: actionString = "POINTER_UP"; break;
                case MotionEvent.ACTION_POINTER_DOWN: actionString = "POINTER_DOWN"; break;
                case MotionEvent.ACTION_MOVE: actionString = "MOVE"; break;
                case MotionEvent.ACTION_OUTSIDE: actionString = "OUTSIDE"; break;
                default: actionString = "ACTION_" + action; break;
            }
            final float size = me.getSize(index);
            final float pressure = me.getPressure(index);
            final Object[] values = {
                actionString, eventTime, id, x, y, size, pressure
            };
            getInstance().writeEvent(EVENTKEYS_LATINKEYBOARDVIEW_PROCESSMOTIONEVENT, values);
        }
    }

    private static final String[] EVENTKEYS_LATINIME_ONCODEINPUT = {
        "LATINIME_ONCODEINPUT", "code", "x", "y"
    };
    public static void latinIME_onCodeInput(final int code, final int x, final int y) {
        final Object[] values = {
            Keyboard.printableCode(code), x, y
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_ONCODEINPUT, values);
    }

    private static final String[] EVENTKEYS_CORRECTION = {
        "CORRECTION", "subgroup", "before", "after", "position"
    };
    public static void logCorrection(final String subgroup, final String before, final String after,
            final int position) {
        final Object[] values = {
            subgroup, before, after, position
        };
        getInstance().writeEvent(EVENTKEYS_CORRECTION, values);
    }

    private static final String[] EVENTKEYS_STATECHANGE = {
        "STATECHANGE", "subgroup", "details"
    };
    public static void logStateChange(final String subgroup, final String details) {
        final Object[] values = {
            subgroup, details
        };
        getInstance().writeEvent(EVENTKEYS_STATECHANGE, values);
    }

    private static final String[] EVENTKEYS_KEYBOARDSTATE_ONCANCELINPUT = {
        "KEYBOARDSTATE_ONCANCELINPUT", "isSinglePointer", "keyboardState"
    };
    public static void keyboardState_onCancelInput(final boolean isSinglePointer,
            final KeyboardState keyboardState) {
        final Object[] values = {
            isSinglePointer, keyboardState.toString()
        };
        getInstance().writeEvent(EVENTKEYS_KEYBOARDSTATE_ONCANCELINPUT, values);
    }

    private static final String[] EVENTKEYS_KEYBOARDSTATE_ONCODEINPUT = {
        "KEYBOARDSTATE_ONCODEINPUT", "code", "isSinglePointer", "autoCaps", "keyboardState"
    };
    public static void keyboardState_onCodeInput(
            final int code, final boolean isSinglePointer, final int autoCaps,
            final KeyboardState keyboardState) {
        final Object[] values = {
            Keyboard.printableCode(code), isSinglePointer, autoCaps, keyboardState.toString()
        };
        getInstance().writeEvent(EVENTKEYS_KEYBOARDSTATE_ONCODEINPUT, values);
    }

    private static final String[] EVENTKEYS_KEYBOARDSTATE_ONLONGPRESSTIMEOUT = {
        "KEYBOARDSTATE_ONLONGPRESSTIMEOUT", "code", "keyboardState"
    };
    public static void keyboardState_onLongPressTimeout(final int code,
            final KeyboardState keyboardState) {
        final Object[] values = {
            Keyboard.printableCode(code), keyboardState.toString()
        };
        getInstance().writeEvent(EVENTKEYS_KEYBOARDSTATE_ONLONGPRESSTIMEOUT, values);
    }

    private static final String[] EVENTKEYS_KEYBOARDSTATE_ONPRESSKEY = {
        "KEYBOARDSTATE_ONPRESSKEY", "code", "keyboardState"
    };
    public static void keyboardState_onPressKey(final int code,
            final KeyboardState keyboardState) {
        final Object[] values = {
            Keyboard.printableCode(code), keyboardState.toString()
        };
        getInstance().writeEvent(EVENTKEYS_KEYBOARDSTATE_ONPRESSKEY, values);
    }

    private static final String[] EVENTKEYS_KEYBOARDSTATE_ONRELEASEKEY = {
        "KEYBOARDSTATE_ONRELEASEKEY", "code", "withSliding", "keyboardState"
    };
    public static void keyboardState_onReleaseKey(final KeyboardState keyboardState, final int code,
            final boolean withSliding) {
        final Object[] values = {
            Keyboard.printableCode(code), withSliding, keyboardState.toString()
        };
        getInstance().writeEvent(EVENTKEYS_KEYBOARDSTATE_ONRELEASEKEY, values);
    }

    private static final String[] EVENTKEYS_LATINIME_COMMITCURRENTAUTOCORRECTION = {
        "LATINIME_COMMITCURRENTAUTOCORRECTION", "typedWord", "autoCorrection"
    };
    public static void latinIME_commitCurrentAutoCorrection(final String typedWord,
            final String autoCorrection) {
        final Object[] values = {
            typedWord, autoCorrection
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_COMMITCURRENTAUTOCORRECTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_COMMITTEXT = {
        "LATINIME_COMMITTEXT", "typedWord"
    };
    public static void latinIME_commitText(final CharSequence typedWord) {
        final Object[] values = {
            typedWord.toString()
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_COMMITTEXT, values);
    }

    private static final String[] EVENTKEYS_LATINIME_DELETESURROUNDINGTEXT = {
        "LATINIME_DELETESURROUNDINGTEXT", "length"
    };
    public static void latinIME_deleteSurroundingText(final int length) {
        final Object[] values = {
            length
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_DELETESURROUNDINGTEXT, values);
    }

    private static final String[] EVENTKEYS_LATINIME_DOUBLESPACEAUTOPERIOD = {
        "LATINIME_DOUBLESPACEAUTOPERIOD"
    };
    public static void latinIME_doubleSpaceAutoPeriod() {
        getInstance().writeEvent(EVENTKEYS_LATINIME_DOUBLESPACEAUTOPERIOD, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINIME_ONDISPLAYCOMPLETIONS = {
        "LATINIME_ONDISPLAYCOMPLETIONS", "applicationSpecifiedCompletions"
    };
    public static void latinIME_onDisplayCompletions(
            final CompletionInfo[] applicationSpecifiedCompletions) {
        final Object[] values = {
            applicationSpecifiedCompletions
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_ONDISPLAYCOMPLETIONS, values);
    }

    /* package */ static boolean getAndClearLatinIMEExpectingUpdateSelection() {
        boolean returnValue = sLatinIMEExpectingUpdateSelection;
        sLatinIMEExpectingUpdateSelection = false;
        return returnValue;
    }

    private static final String[] EVENTKEYS_LATINIME_ONWINDOWHIDDEN = {
        "LATINIME_ONWINDOWHIDDEN", "isTextTruncated", "text"
    };
    public static void latinIME_onWindowHidden(final int savedSelectionStart,
            final int savedSelectionEnd, final InputConnection ic) {
        if (ic != null) {
            ic.beginBatchEdit();
            ic.performContextMenuAction(android.R.id.selectAll);
            CharSequence charSequence = ic.getSelectedText(0);
            ic.setSelection(savedSelectionStart, savedSelectionEnd);
            ic.endBatchEdit();
            sLatinIMEExpectingUpdateSelection = true;
            Object[] values = new Object[2];
            if (TextUtils.isEmpty(charSequence)) {
                values[0] = false;
                values[1] = "";
            } else {
                if (charSequence.length() > MAX_INPUTVIEW_LENGTH_TO_CAPTURE) {
                    int length = MAX_INPUTVIEW_LENGTH_TO_CAPTURE;
                    // do not cut in the middle of a supplementary character
                    final char c = charSequence.charAt(length - 1);
                    if (Character.isHighSurrogate(c)) {
                        length--;
                    }
                    final CharSequence truncatedCharSequence = charSequence.subSequence(0, length);
                    values[0] = true;
                    values[1] = truncatedCharSequence.toString();
                } else {
                    values[0] = false;
                    values[1] = charSequence.toString();
                }
            }
            getInstance().writeEvent(EVENTKEYS_LATINIME_ONWINDOWHIDDEN, values);
        }
    }

    private static final String[] EVENTKEYS_LATINIME_ONSTARTINPUTVIEWINTERNAL = {
        "LATINIME_ONSTARTINPUTVIEWINTERNAL", "uuid", "packageName", "inputType", "imeOptions",
        "display", "model", "prefs"
    };

    public static void latinIME_onStartInputViewInternal(final EditorInfo editorInfo,
            final SharedPreferences prefs) {
        if (editorInfo != null) {
            final Object[] values = {
                getUUID(prefs), editorInfo.packageName, Integer.toHexString(editorInfo.inputType),
                Integer.toHexString(editorInfo.imeOptions), Build.DISPLAY, Build.MODEL, prefs
            };
            getInstance().writeEvent(EVENTKEYS_LATINIME_ONSTARTINPUTVIEWINTERNAL, values);
        }
    }

    private static String getUUID(final SharedPreferences prefs) {
        String uuidString = prefs.getString(PREF_RESEARCH_LOGGER_UUID_STRING, null);
        if (null == uuidString) {
            UUID uuid = UUID.randomUUID();
            uuidString = uuid.toString();
            Editor editor = prefs.edit();
            editor.putString(PREF_RESEARCH_LOGGER_UUID_STRING, uuidString);
            editor.apply();
        }
        return uuidString;
    }

    private static final String[] EVENTKEYS_LATINIME_ONUPDATESELECTION = {
        "LATINIME_ONUPDATESELECTION", "lastSelectionStart", "lastSelectionEnd", "oldSelStart",
        "oldSelEnd", "newSelStart", "newSelEnd", "composingSpanStart", "composingSpanEnd",
        "expectingUpdateSelection", "expectingUpdateSelectionFromLogger", "context" 
    };

    public static void latinIME_onUpdateSelection(final int lastSelectionStart,
            final int lastSelectionEnd, final int oldSelStart, final int oldSelEnd,
            final int newSelStart, final int newSelEnd, final int composingSpanStart,
            final int composingSpanEnd, final boolean expectingUpdateSelection,
            final boolean expectingUpdateSelectionFromLogger, final InputConnection connection) {
        final Object[] values = {
            lastSelectionStart, lastSelectionEnd, oldSelStart, oldSelEnd, newSelStart,
            newSelEnd, composingSpanStart, composingSpanEnd, expectingUpdateSelection,
            expectingUpdateSelectionFromLogger,
            EditingUtils.getWordRangeAtCursor(connection, WHITESPACE_SEPARATORS, 1).mWord
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_ONUPDATESELECTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_PERFORMEDITORACTION = {
        "LATINIME_PERFORMEDITORACTION", "imeActionNext"
    };
    public static void latinIME_performEditorAction(final int imeActionNext) {
        final Object[] values = {
            imeActionNext
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_PERFORMEDITORACTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_PICKAPPLICATIONSPECIFIEDCOMPLETION = {
        "LATINIME_PICKAPPLICATIONSPECIFIEDCOMPLETION", "index", "text", "x", "y"
    };
    public static void latinIME_pickApplicationSpecifiedCompletion(final int index,
            final CharSequence text, int x, int y) {
        final Object[] values = {
            index, text.toString(), x, y
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_PICKAPPLICATIONSPECIFIEDCOMPLETION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_PICKSUGGESTIONMANUALLY = {
        "LATINIME_PICKSUGGESTIONMANUALLY", "replacedWord", "index", "suggestion", "x", "y"
    };
    public static void latinIME_pickSuggestionManually(final String replacedWord,
            final int index, CharSequence suggestion, int x, int y) {
        final Object[] values = {
            replacedWord, index, suggestion.toString(), x, y
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_PICKSUGGESTIONMANUALLY, values);
    }

    private static final String[] EVENTKEYS_LATINIME_PUNCTUATIONSUGGESTION = {
        "LATINIME_PUNCTUATIONSUGGESTION", "index", "suggestion", "x", "y"
    };
    public static void latinIME_punctuationSuggestion(final int index,
            final CharSequence suggestion, int x, int y) {
        final Object[] values = {
            index, suggestion.toString(), x, y
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_PUNCTUATIONSUGGESTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_REVERTDOUBLESPACEWHILEINBATCHEDIT = {
        "LATINIME_REVERTDOUBLESPACEWHILEINBATCHEDIT"
    };
    public static void latinIME_revertDoubleSpaceWhileInBatchEdit() {
        getInstance().writeEvent(EVENTKEYS_LATINIME_REVERTDOUBLESPACEWHILEINBATCHEDIT,
                EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINIME_REVERTSWAPPUNCTUATION = {
        "LATINIME_REVERTSWAPPUNCTUATION"
    };
    public static void latinIME_revertSwapPunctuation() {
        getInstance().writeEvent(EVENTKEYS_LATINIME_REVERTSWAPPUNCTUATION, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINIME_SENDKEYCODEPOINT = {
        "LATINIME_SENDKEYCODEPOINT", "code"
    };
    public static void latinIME_sendKeyCodePoint(final int code) {
        final Object[] values = {
            code
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_SENDKEYCODEPOINT, values);
    }

    private static final String[] EVENTKEYS_LATINIME_SWAPSWAPPERANDSPACEWHILEINBATCHEDIT = {
        "LATINIME_SWAPSWAPPERANDSPACEWHILEINBATCHEDIT"
    };
    public static void latinIME_swapSwapperAndSpaceWhileInBatchEdit() {
        getInstance().writeEvent(EVENTKEYS_LATINIME_SWAPSWAPPERANDSPACEWHILEINBATCHEDIT,
                EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINIME_SWITCHTOKEYBOARDVIEW = {
        "LATINIME_SWITCHTOKEYBOARDVIEW"
    };
    public static void latinIME_switchToKeyboardView() {
        getInstance().writeEvent(EVENTKEYS_LATINIME_SWITCHTOKEYBOARDVIEW, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINKEYBOARDVIEW_ONLONGPRESS = {
        "LATINKEYBOARDVIEW_ONLONGPRESS"
    };
    public static void latinKeyboardView_onLongPress() {
        getInstance().writeEvent(EVENTKEYS_LATINKEYBOARDVIEW_ONLONGPRESS, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINKEYBOARDVIEW_SETKEYBOARD = {
        "LATINKEYBOARDVIEW_SETKEYBOARD", "id", "tw", "th", "keys"
    };
    public static void latinKeyboardView_setKeyboard(final Keyboard keyboard) {
        if (keyboard != null) {
            final Object[] values = {
                keyboard.mId.toString(), keyboard.mOccupiedWidth, keyboard.mOccupiedHeight,
                keyboard
            };
            getInstance().writeEvent(EVENTKEYS_LATINKEYBOARDVIEW_SETKEYBOARD, values);
        }
    }

    private static final String[] EVENTKEYS_LATINIME_REVERTCOMMIT = {
        "LATINIME_REVERTCOMMIT", "originallyTypedWord"
    };
    public static void latinIME_revertCommit(final String originallyTypedWord) {
        final Object[] values = {
            originallyTypedWord
        };
        getInstance().writeEvent(EVENTKEYS_LATINIME_REVERTCOMMIT, values);
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_CALLLISTENERONCANCELINPUT = {
        "POINTERTRACKER_CALLLISTENERONCANCELINPUT"
    };
    public static void pointerTracker_callListenerOnCancelInput() {
        getInstance().writeEvent(EVENTKEYS_POINTERTRACKER_CALLLISTENERONCANCELINPUT,
                EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_CALLLISTENERONCODEINPUT = {
        "POINTERTRACKER_CALLLISTENERONCODEINPUT", "code", "outputText", "x", "y",
        "ignoreModifierKey", "altersCode", "isEnabled"
    };
    public static void pointerTracker_callListenerOnCodeInput(final Key key, final int x,
            final int y, final boolean ignoreModifierKey, final boolean altersCode,
            final int code) {
        if (key != null) {
            CharSequence outputText = key.mOutputText;
            final Object[] values = {
                Keyboard.printableCode(code), outputText == null ? "" : outputText.toString(),
                x, y, ignoreModifierKey, altersCode, key.isEnabled()
            };
            getInstance().writeEvent(EVENTKEYS_POINTERTRACKER_CALLLISTENERONCODEINPUT, values);
        }
    }

    private static final String[]
            EVENTKEYS_POINTERTRACKER_CALLLISTENERONPRESSANDCHECKKEYBOARDLAYOUTCHANGE = {
                "POINTERTRACKER_CALLLISTENERONPRESSANDCHECKKEYBOARDLAYOUTCHANGE", "code",
                "ignoreModifierKey", "isEnabled"
    };
    public static void pointerTracker_callListenerOnPressAndCheckKeyboardLayoutChange(
            final Key key, final boolean ignoreModifierKey) {
        if (key != null) {
            final Object[] values = {
                KeyDetector.printableCode(key), ignoreModifierKey, key.isEnabled()
            };
            getInstance().writeEvent(
                    EVENTKEYS_POINTERTRACKER_CALLLISTENERONPRESSANDCHECKKEYBOARDLAYOUTCHANGE,
                    values);
        }
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_CALLLISTENERONRELEASE = {
        "POINTERTRACKER_CALLLISTENERONRELEASE", "code", "withSliding", "ignoreModifierKey",
        "isEnabled"
    };
    public static void pointerTracker_callListenerOnRelease(final Key key, final int primaryCode,
            final boolean withSliding, final boolean ignoreModifierKey) {
        if (key != null) {
            final Object[] values = {
                Keyboard.printableCode(primaryCode), withSliding, ignoreModifierKey,
                key.isEnabled()
            };
            getInstance().writeEvent(EVENTKEYS_POINTERTRACKER_CALLLISTENERONRELEASE, values);
        }
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_ONDOWNEVENT = {
        "POINTERTRACKER_ONDOWNEVENT", "deltaT", "distanceSquared"
    };
    public static void pointerTracker_onDownEvent(long deltaT, int distanceSquared) {
        final Object[] values = {
            deltaT, distanceSquared
        };
        getInstance().writeEvent(EVENTKEYS_POINTERTRACKER_ONDOWNEVENT, values);
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_ONMOVEEVENT = {
        "POINTERTRACKER_ONMOVEEVENT", "x", "y", "lastX", "lastY"
    };
    public static void pointerTracker_onMoveEvent(final int x, final int y, final int lastX,
            final int lastY) {
        final Object[] values = {
            x, y, lastX, lastY
        };
        getInstance().writeEvent(EVENTKEYS_POINTERTRACKER_ONMOVEEVENT, values);
    }

    private static final String[] EVENTKEYS_SUDDENJUMPINGTOUCHEVENTHANDLER_ONTOUCHEVENT = {
        "SUDDENJUMPINGTOUCHEVENTHANDLER_ONTOUCHEVENT", "motionEvent"
    };
    public static void suddenJumpingTouchEventHandler_onTouchEvent(final MotionEvent me) {
        if (me != null) {
            final Object[] values = {
                me.toString()
            };
            getInstance().writeEvent(EVENTKEYS_SUDDENJUMPINGTOUCHEVENTHANDLER_ONTOUCHEVENT,
                    values);
        }
    }

    private static final String[] EVENTKEYS_SUGGESTIONSVIEW_SETSUGGESTIONS = {
        "SUGGESTIONSVIEW_SETSUGGESTIONS", "suggestedWords"
    };
    public static void suggestionsView_setSuggestions(final SuggestedWords suggestedWords) {
        if (suggestedWords != null) {
            final Object[] values = {
                suggestedWords.toString()
            };
            getInstance().writeEvent(EVENTKEYS_SUGGESTIONSVIEW_SETSUGGESTIONS, values);
        }
    }
}
