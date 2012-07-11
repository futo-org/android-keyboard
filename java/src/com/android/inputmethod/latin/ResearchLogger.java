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

import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.RichInputConnection.Range;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    private static final boolean OUTPUT_ENTIRE_BUFFER = false;  // true may disclose private info
    /* package */ static boolean sIsLogging = false;
    private static final int OUTPUT_FORMAT_VERSION = 1;
    private static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";
    private static final String FILENAME_PREFIX = "researchLog";
    private static final String FILENAME_SUFFIX = ".txt";
    private static final SimpleDateFormat TIMESTAMP_DATEFORMAT =
            new SimpleDateFormat("yyyyMMddHHmmssS", Locale.US);

    // constants related to specific log points
    private static final String WHITESPACE_SEPARATORS = " \t\n\r";
    private static final int MAX_INPUTVIEW_LENGTH_TO_CAPTURE = 8192; // must be >=1
    private static final String PREF_RESEARCH_LOGGER_UUID_STRING = "pref_research_logger_uuid";
    private static final int ABORT_TIMEOUT_IN_MS = 10 * 1000;

    private static final ResearchLogger sInstance = new ResearchLogger();
    // to write to a different filename, e.g., for testing, set mFile before calling start()
    /* package */ File mFilesDir;
    /* package */ String mUUIDString;
    /* package */ ResearchLog mMainResearchLog;
    // The mIntentionalResearchLog records all events for the session, private or not (excepting
    // passwords).  It is written to permanent storage only if the user explicitly commands
    // the system to do so.
    /* package */ ResearchLog mIntentionalResearchLog;
    // LogUnits are queued here and released only when the user requests the intentional log.
    private final List<LogUnit> mIntentionalResearchLogQueue = new ArrayList<LogUnit>();

    private boolean mIsPasswordView = false;
    private boolean mIsLoggingSuspended = false;

    // digits entered by the user are replaced with this codepoint.
    /* package for test */ static final int DIGIT_REPLACEMENT_CODEPOINT =
            Character.codePointAt("\uE000", 0);  // U+E000 is in the "private-use area"
    // U+E001 is in the "private-use area"
    /* package for test */ static final String WORD_REPLACEMENT_STRING = "\uE001";
    // set when LatinIME should ignore an onUpdateSelection() callback that
    // arises from operations in this class
    private static boolean sLatinIMEExpectingUpdateSelection = false;

    // used to check whether words are not unique
    private Suggest mSuggest;
    private Dictionary mDictionary;
    private KeyboardSwitcher mKeyboardSwitcher;

    private ResearchLogger() {
    }

    public static ResearchLogger getInstance() {
        return sInstance;
    }

    public void init(final InputMethodService ims, final SharedPreferences prefs,
            KeyboardSwitcher keyboardSwitcher) {
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
            mUUIDString = getUUID(prefs);
            sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
            prefs.registerOnSharedPreferenceChangeListener(this);
        }
        mKeyboardSwitcher = keyboardSwitcher;
    }

    private File createLogFile(File filesDir) {
        final StringBuilder sb = new StringBuilder();
        sb.append(FILENAME_PREFIX).append('-');
        sb.append(mUUIDString).append('-');
        sb.append(TIMESTAMP_DATEFORMAT.format(new Date()));
        sb.append(FILENAME_SUFFIX);
        return new File(filesDir, sb.toString());
    }

    public void start() {
        if (!sIsLogging) {
            // Log.w(TAG, "not in usability mode; not logging");
            return;
        }
        if (mFilesDir == null || !mFilesDir.exists()) {
            Log.w(TAG, "IME storage directory does not exist.  Cannot start logging.");
            return;
        }
        if (mMainResearchLog == null || !mMainResearchLog.isAlive()) {
            mMainResearchLog = new ResearchLog(createLogFile(mFilesDir));
        }
        try {
            mMainResearchLog.start();
            if (mIntentionalResearchLog == null || !mIntentionalResearchLog.isAlive()) {
                mIntentionalResearchLog = new ResearchLog(createLogFile(mFilesDir));
            }
            mIntentionalResearchLog.start();
        } catch (IOException e) {
            Log.w(TAG, "Could not start ResearchLogger.");
        }
    }

    public void stop() {
        if (mMainResearchLog != null) {
            mMainResearchLog.stop();
        }
    }

    public boolean abort() {
        mIsLoggingSuspended = true;
        requestIndicatorRedraw();
        boolean didAbortMainLog = false;
        if (mMainResearchLog != null) {
            mMainResearchLog.abort();
            try {
                mMainResearchLog.waitUntilStopped(ABORT_TIMEOUT_IN_MS);
            } catch (InterruptedException e) {
                // interrupted early.  carry on.
            }
            if (mMainResearchLog.isAbortSuccessful()) {
                didAbortMainLog = true;
            }
        }
        boolean didAbortIntentionalLog = false;
        if (mIntentionalResearchLog != null) {
            mIntentionalResearchLog.abort();
            try {
                mIntentionalResearchLog.waitUntilStopped(ABORT_TIMEOUT_IN_MS);
            } catch (InterruptedException e) {
                // interrupted early.  carry on.
            }
            if (mIntentionalResearchLog.isAbortSuccessful()) {
                didAbortIntentionalLog = true;
            }
        }
        return didAbortMainLog && didAbortIntentionalLog;
    }

    /* package */ void flush() {
        if (mMainResearchLog != null) {
            mMainResearchLog.flush();
        }
    }

    private void logWholeSessionHistory() throws IOException {
        try {
            LogUnit headerLogUnit = new LogUnit();
            headerLogUnit.addLogAtom(EVENTKEYS_INTENTIONAL_LOG, EVENTKEYS_NULLVALUES, false);
            mIntentionalResearchLog.publishAllEvents(headerLogUnit);
            for (LogUnit logUnit : mIntentionalResearchLogQueue) {
                mIntentionalResearchLog.publishAllEvents(logUnit);
            }
            mIntentionalResearchLog.stop();
            mIntentionalResearchLog = new ResearchLog(createLogFile(mFilesDir));
            mIntentionalResearchLog.start();
        } finally {
            mIntentionalResearchLogQueue.clear();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null || prefs == null) {
            return;
        }
        sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
        if (sIsLogging == false) {
            abort();
        }
    }

    /* package */ void presentResearchDialog(final LatinIME latinIME) {
        final CharSequence title = latinIME.getString(R.string.english_ime_research_log);
        final CharSequence[] items = new CharSequence[] {
                latinIME.getString(R.string.note_timestamp_for_researchlog),
                mIsLoggingSuspended ? latinIME.getString(R.string.enable_session_logging) :
                        latinIME.getString(R.string.do_not_log_this_session),
                latinIME.getString(R.string.log_whole_session_history),
        };
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                    case 0:
                        userTimestamp();
                        Toast.makeText(latinIME, R.string.notify_recorded_timestamp,
                                Toast.LENGTH_LONG).show();
                        break;
                    case 1:
                        if (mIsLoggingSuspended) {
                            mIsLoggingSuspended = false;
                            requestIndicatorRedraw();
                            Toast toast = Toast.makeText(latinIME,
                                    R.string.notify_session_logging_enabled, Toast.LENGTH_LONG);
                        } else {
                            Toast toast = Toast.makeText(latinIME,
                                    R.string.notify_session_log_deleting, Toast.LENGTH_LONG);
                            toast.show();
                            boolean isLogDeleted = abort();
                            toast.cancel();
                            if (isLogDeleted) {
                                Toast.makeText(latinIME, R.string.notify_session_log_deleted,
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(latinIME,
                                        R.string.notify_session_log_not_deleted, Toast.LENGTH_LONG)
                                        .show();
                            }
                        }
                        break;
                    case 2:
                        try {
                            logWholeSessionHistory();
                            Toast.makeText(latinIME, R.string.notify_session_history_logged,
                                    Toast.LENGTH_LONG).show();
                        } catch (IOException e) {
                            Toast.makeText(latinIME, R.string.notify_session_history_not_logged,
                                    Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                        break;
                }
            }

        };
        final AlertDialog.Builder builder = new AlertDialog.Builder(latinIME)
                .setItems(items, listener)
                .setTitle(title);
        latinIME.showOptionDialog(builder.create());
    }

    public void initSuggest(Suggest suggest) {
        mSuggest = suggest;
    }

    private void setIsPasswordView(boolean isPasswordView) {
        mIsPasswordView = isPasswordView;
    }

    private boolean isAllowedToLog() {
        return !mIsPasswordView && !mIsLoggingSuspended;
    }

    public void requestIndicatorRedraw() {
        // invalidate any existing graphics
        if (mKeyboardSwitcher != null) {
            mKeyboardSwitcher.getKeyboardView().invalidateAllKeys();
        }
    }

    private static final String CURRENT_TIME_KEY = "_ct";
    private static final String UPTIME_KEY = "_ut";
    private static final String EVENT_TYPE_KEY = "_ty";
    private static final Object[] EVENTKEYS_NULLVALUES = {};

    private LogUnit mCurrentLogUnit = new LogUnit();

    /**
     * Buffer a research log event, flagging it as privacy-sensitive.
     *
     * This event contains potentially private information.  If the word that this event is a part
     * of is determined to be privacy-sensitive, then this event should not be included in the
     * output log.  The system waits to output until the containing word is known.
     *
     * @param keys an array containing a descriptive name for the event, followed by the keys
     * @param values an array of values, either a String or Number.  length should be one
     * less than the keys array
     */
    private synchronized void enqueuePotentiallyPrivateEvent(final String[] keys,
            final Object[] values) {
        assert values.length + 1 == keys.length;
        if (isAllowedToLog()) {
            mCurrentLogUnit.addLogAtom(keys, values, true);
        }
    }

    /**
     * Buffer a research log event, flaggint it as not privacy-sensitive.
     *
     * This event contains no potentially private information.  Even if the word that this event
     * is privacy-sensitive, this event can still safely be sent to the output log.  The system
     * waits until the containing word is known so that this event can be written in the proper
     * temporal order with other events that may be privacy sensitive.
     *
     * @param keys an array containing a descriptive name for the event, followed by the keys
     * @param values an array of values, either a String or Number.  length should be one
     * less than the keys array
     */
    private synchronized void enqueueEvent(final String[] keys, final Object[] values) {
        assert values.length + 1 == keys.length;
        if (isAllowedToLog()) {
            mCurrentLogUnit.addLogAtom(keys, values, false);
        }
    }

    // Used to track how often words are logged.  Too-frequent logging can leak
    // semantics, disclosing private data.
    /* package for test */ static class LoggingFrequencyState {
        private static final int DEFAULT_WORD_LOG_FREQUENCY = 10;
        private int mWordsRemainingToSkip;
        private final int mFrequency;

        /**
         * Tracks how often words may be uploaded.
         *
         * @param frequency 1=Every word, 2=Every other word, etc.
         */
        public LoggingFrequencyState(int frequency) {
            mFrequency = frequency;
            mWordsRemainingToSkip = mFrequency;
        }

        public void onWordLogged() {
            mWordsRemainingToSkip = mFrequency;
        }

        public void onWordNotLogged() {
            if (mWordsRemainingToSkip > 1) {
                mWordsRemainingToSkip--;
            }
        }

        public boolean isSafeToLog() {
            return mWordsRemainingToSkip <= 1;
        }
    }

    /* package for test */ LoggingFrequencyState mLoggingFrequencyState =
            new LoggingFrequencyState(LoggingFrequencyState.DEFAULT_WORD_LOG_FREQUENCY);

    /* package for test */ boolean isPrivacyThreat(String word) {
        // Current checks:
        // - Word not in dictionary
        // - Word contains numbers
        // - Privacy-safe word not logged recently
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        if (!mLoggingFrequencyState.isSafeToLog()) {
            return true;
        }
        final int length = word.length();
        boolean hasLetter = false;
        for (int i = 0; i < length; i = word.offsetByCodePoints(i, 1)) {
            final int codePoint = Character.codePointAt(word, i);
            if (Character.isDigit(codePoint)) {
                return true;
            }
            if (Character.isLetter(codePoint)) {
                hasLetter = true;
                break; // Word may contain digits, but will only be allowed if in the dictionary.
            }
        }
        if (hasLetter) {
            if (mDictionary == null && mSuggest != null && mSuggest.hasMainDictionary()) {
                mDictionary = mSuggest.getMainDictionary();
            }
            if (mDictionary == null) {
                // Can't access dictionary.  Assume privacy threat.
                return true;
            }
            return !(mDictionary.isValidWord(word));
        }
        // No letters, no numbers.  Punctuation, space, or something else.
        return false;
    }

    private void onWordComplete(String word) {
        if (isPrivacyThreat(word)) {
            publishLogUnit(mCurrentLogUnit, true);
            mLoggingFrequencyState.onWordNotLogged();
        } else {
            publishLogUnit(mCurrentLogUnit, false);
            mLoggingFrequencyState.onWordLogged();
        }
        mCurrentLogUnit = new LogUnit();
    }

    private void publishLogUnit(LogUnit logUnit, boolean isPrivacySensitive) {
        if (isPrivacySensitive) {
            mMainResearchLog.publishPublicEvents(logUnit);
        } else {
            mMainResearchLog.publishAllEvents(logUnit);
        }
        mIntentionalResearchLogQueue.add(logUnit);
    }

    /* package */ void publishCurrentLogUnit(ResearchLog researchLog, boolean isPrivacySensitive) {
        publishLogUnit(mCurrentLogUnit, isPrivacySensitive);
    }

    static class LogUnit {
        private final List<String[]> mKeysList = new ArrayList<String[]>();
        private final List<Object[]> mValuesList = new ArrayList<Object[]>();
        private final List<Boolean> mIsPotentiallyPrivate = new ArrayList<Boolean>();

        private void addLogAtom(final String[] keys, final Object[] values,
                final Boolean isPotentiallyPrivate) {
            mKeysList.add(keys);
            mValuesList.add(values);
            mIsPotentiallyPrivate.add(isPotentiallyPrivate);
        }

        public void publishPublicEventsTo(ResearchLog researchLog) {
            final int size = mKeysList.size();
            for (int i = 0; i < size; i++) {
                if (!mIsPotentiallyPrivate.get(i)) {
                    researchLog.outputEvent(mKeysList.get(i), mValuesList.get(i));
                }
            }
        }

        public void publishAllEventsTo(ResearchLog researchLog) {
            final int size = mKeysList.size();
            for (int i = 0; i < size; i++) {
                researchLog.outputEvent(mKeysList.get(i), mValuesList.get(i));
            }
        }
    }

    private static int scrubDigitFromCodePoint(int codePoint) {
        return Character.isDigit(codePoint) ? DIGIT_REPLACEMENT_CODEPOINT : codePoint;
    }

    /* package for test */ static String scrubDigitsFromString(String s) {
        StringBuilder sb = null;
        final int length = s.length();
        for (int i = 0; i < length; i = s.offsetByCodePoints(i, 1)) {
            final int codePoint = Character.codePointAt(s, i);
            if (Character.isDigit(codePoint)) {
                if (sb == null) {
                    sb = new StringBuilder(length);
                    sb.append(s.substring(0, i));
                }
                sb.appendCodePoint(DIGIT_REPLACEMENT_CODEPOINT);
            } else {
                if (sb != null) {
                    sb.appendCodePoint(codePoint);
                }
            }
        }
        if (sb == null) {
            return s;
        } else {
            return sb.toString();
        }
    }

    private String scrubWord(String word) {
        if (mDictionary == null) {
            return WORD_REPLACEMENT_STRING;
        }
        if (mDictionary.isValidWord(word)) {
            return word;
        }
        return WORD_REPLACEMENT_STRING;
    }

    private static final String[] EVENTKEYS_INTENTIONAL_LOG = {
        "IntentionalLog"
    };
    private static final String[] EVENTKEYS_LATINKEYBOARDVIEW_PROCESSMOTIONEVENT = {
        "LatinKeyboardViewProcessMotionEvent", "action", "eventTime", "id", "x", "y", "size",
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
            getInstance().enqueuePotentiallyPrivateEvent(
                    EVENTKEYS_LATINKEYBOARDVIEW_PROCESSMOTIONEVENT, values);
        }
    }

    private static final String[] EVENTKEYS_LATINIME_ONCODEINPUT = {
        "LatinIMEOnCodeInput", "code", "x", "y"
    };
    public static void latinIME_onCodeInput(final int code, final int x, final int y) {
        final Object[] values = {
            Keyboard.printableCode(scrubDigitFromCodePoint(code)), x, y
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_ONCODEINPUT, values);
    }

    private static final String[] EVENTKEYS_CORRECTION = {
        "LogCorrection", "subgroup", "before", "after", "position"
    };
    public static void logCorrection(final String subgroup, final String before, final String after,
            final int position) {
        final Object[] values = {
            subgroup, scrubDigitsFromString(before), scrubDigitsFromString(after), position
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_CORRECTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_COMMITCURRENTAUTOCORRECTION = {
        "LatinIMECommitCurrentAutoCorrection", "typedWord", "autoCorrection"
    };
    public static void latinIME_commitCurrentAutoCorrection(final String typedWord,
            final String autoCorrection) {
        final Object[] values = {
            scrubDigitsFromString(typedWord), scrubDigitsFromString(autoCorrection)
        };
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueuePotentiallyPrivateEvent(
                EVENTKEYS_LATINIME_COMMITCURRENTAUTOCORRECTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_COMMITTEXT = {
        "LatinIMECommitText", "typedWord"
    };
    public static void latinIME_commitText(final CharSequence typedWord) {
        final String scrubbedWord = scrubDigitsFromString(typedWord.toString());
        final Object[] values = {
            scrubbedWord
        };
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_COMMITTEXT, values);
        researchLogger.onWordComplete(scrubbedWord);
    }

    private static final String[] EVENTKEYS_LATINIME_DELETESURROUNDINGTEXT = {
        "LatinIMEDeleteSurroundingText", "length"
    };
    public static void latinIME_deleteSurroundingText(final int length) {
        final Object[] values = {
            length
        };
        getInstance().enqueueEvent(EVENTKEYS_LATINIME_DELETESURROUNDINGTEXT, values);
    }

    private static final String[] EVENTKEYS_LATINIME_DOUBLESPACEAUTOPERIOD = {
        "LatinIMEDoubleSpaceAutoPeriod"
    };
    public static void latinIME_doubleSpaceAutoPeriod() {
        getInstance().enqueueEvent(EVENTKEYS_LATINIME_DOUBLESPACEAUTOPERIOD, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINIME_ONDISPLAYCOMPLETIONS = {
        "LatinIMEOnDisplayCompletions", "applicationSpecifiedCompletions"
    };
    public static void latinIME_onDisplayCompletions(
            final CompletionInfo[] applicationSpecifiedCompletions) {
        final Object[] values = {
            applicationSpecifiedCompletions
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_ONDISPLAYCOMPLETIONS,
                values);
    }

    /* package */ static boolean getAndClearLatinIMEExpectingUpdateSelection() {
        boolean returnValue = sLatinIMEExpectingUpdateSelection;
        sLatinIMEExpectingUpdateSelection = false;
        return returnValue;
    }

    private static final String[] EVENTKEYS_LATINIME_ONWINDOWHIDDEN = {
        "LatinIMEOnWindowHidden", "isTextTruncated", "text"
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
            final Object[] values = new Object[2];
            if (OUTPUT_ENTIRE_BUFFER) {
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
                        final CharSequence truncatedCharSequence = charSequence.subSequence(0,
                                length);
                        values[0] = true;
                        values[1] = truncatedCharSequence.toString();
                    } else {
                        values[0] = false;
                        values[1] = charSequence.toString();
                    }
                }
            } else {
                values[0] = true;
                values[1] = "";
            }
            final ResearchLogger researchLogger = getInstance();
            researchLogger.enqueueEvent(EVENTKEYS_LATINIME_ONWINDOWHIDDEN, values);
            // Play it safe.  Remove privacy-sensitive events.
            researchLogger.publishLogUnit(researchLogger.mCurrentLogUnit, true);
            researchLogger.mCurrentLogUnit = new LogUnit();
        }
    }

    private static final String[] EVENTKEYS_LATINIME_ONSTARTINPUTVIEWINTERNAL = {
        "LatinIMEOnStartInputViewInternal", "uuid", "packageName", "inputType", "imeOptions",
        "fieldId", "display", "model", "prefs", "outputFormatVersion"
    };
    public static void latinIME_onStartInputViewInternal(final EditorInfo editorInfo,
            final SharedPreferences prefs) {
        if (editorInfo != null) {
            final Object[] values = {
                getInstance().mUUIDString, editorInfo.packageName,
                Integer.toHexString(editorInfo.inputType),
                Integer.toHexString(editorInfo.imeOptions), editorInfo.fieldId, Build.DISPLAY,
                Build.MODEL, prefs, OUTPUT_FORMAT_VERSION
            };
            getInstance().enqueueEvent(EVENTKEYS_LATINIME_ONSTARTINPUTVIEWINTERNAL, values);
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
        "LatinIMEOnUpdateSelection", "lastSelectionStart", "lastSelectionEnd", "oldSelStart",
        "oldSelEnd", "newSelStart", "newSelEnd", "composingSpanStart", "composingSpanEnd",
        "expectingUpdateSelection", "expectingUpdateSelectionFromLogger", "context"
    };
    public static void latinIME_onUpdateSelection(final int lastSelectionStart,
            final int lastSelectionEnd, final int oldSelStart, final int oldSelEnd,
            final int newSelStart, final int newSelEnd, final int composingSpanStart,
            final int composingSpanEnd, final boolean expectingUpdateSelection,
            final boolean expectingUpdateSelectionFromLogger,
            final RichInputConnection connection) {
        String word = "";
        if (connection != null) {
            Range range = connection.getWordRangeAtCursor(WHITESPACE_SEPARATORS, 1);
            if (range != null) {
                word = range.mWord;
            }
        }
        final ResearchLogger researchLogger = getInstance();
        final String scrubbedWord = researchLogger.scrubWord(word);
        final Object[] values = {
            lastSelectionStart, lastSelectionEnd, oldSelStart, oldSelEnd, newSelStart,
            newSelEnd, composingSpanStart, composingSpanEnd, expectingUpdateSelection,
            expectingUpdateSelectionFromLogger, scrubbedWord
        };
        researchLogger.enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_ONUPDATESELECTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_PERFORMEDITORACTION = {
        "LatinIMEPerformEditorAction", "imeActionNext"
    };
    public static void latinIME_performEditorAction(final int imeActionNext) {
        final Object[] values = {
            imeActionNext
        };
        getInstance().enqueueEvent(EVENTKEYS_LATINIME_PERFORMEDITORACTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_PICKAPPLICATIONSPECIFIEDCOMPLETION = {
        "LatinIMEPickApplicationSpecifiedCompletion", "index", "text", "x", "y"
    };
    public static void latinIME_pickApplicationSpecifiedCompletion(final int index,
            final CharSequence cs, int x, int y) {
        final Object[] values = {
            index, cs, x, y
        };
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueuePotentiallyPrivateEvent(
                EVENTKEYS_LATINIME_PICKAPPLICATIONSPECIFIEDCOMPLETION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_PICKSUGGESTIONMANUALLY = {
        "LatinIMEPickSuggestionManually", "replacedWord", "index", "suggestion", "x", "y"
    };
    public static void latinIME_pickSuggestionManually(final String replacedWord,
            final int index, CharSequence suggestion, int x, int y) {
        final Object[] values = {
            scrubDigitsFromString(replacedWord), index, suggestion == null ? null :
                    scrubDigitsFromString(suggestion.toString()), x, y
        };
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_PICKSUGGESTIONMANUALLY,
                values);
    }

    private static final String[] EVENTKEYS_LATINIME_PUNCTUATIONSUGGESTION = {
        "LatinIMEPunctuationSuggestion", "index", "suggestion", "x", "y"
    };
    public static void latinIME_punctuationSuggestion(final int index,
            final CharSequence suggestion, int x, int y) {
        final Object[] values = {
            index, suggestion, x, y
        };
        getInstance().enqueueEvent(EVENTKEYS_LATINIME_PUNCTUATIONSUGGESTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_REVERTDOUBLESPACEWHILEINBATCHEDIT = {
        "LatinIMERevertDoubleSpaceWhileInBatchEdit"
    };
    public static void latinIME_revertDoubleSpaceWhileInBatchEdit() {
        getInstance().enqueueEvent(EVENTKEYS_LATINIME_REVERTDOUBLESPACEWHILEINBATCHEDIT,
                EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINIME_REVERTSWAPPUNCTUATION = {
        "LatinIMERevertSwapPunctuation"
    };
    public static void latinIME_revertSwapPunctuation() {
        getInstance().enqueueEvent(EVENTKEYS_LATINIME_REVERTSWAPPUNCTUATION, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINIME_SENDKEYCODEPOINT = {
        "LatinIMESendKeyCodePoint", "code"
    };
    public static void latinIME_sendKeyCodePoint(final int code) {
        final Object[] values = {
            Keyboard.printableCode(scrubDigitFromCodePoint(code))
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_SENDKEYCODEPOINT, values);
    }

    private static final String[] EVENTKEYS_LATINIME_SWAPSWAPPERANDSPACEWHILEINBATCHEDIT = {
        "LatinIMESwapSwapperAndSpaceWhileInBatchEdit"
    };
    public static void latinIME_swapSwapperAndSpaceWhileInBatchEdit() {
        getInstance().enqueueEvent(EVENTKEYS_LATINIME_SWAPSWAPPERANDSPACEWHILEINBATCHEDIT,
                EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINKEYBOARDVIEW_ONLONGPRESS = {
        "LatinKeyboardViewOnLongPress"
    };
    public static void latinKeyboardView_onLongPress() {
        getInstance().enqueueEvent(EVENTKEYS_LATINKEYBOARDVIEW_ONLONGPRESS, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_LATINKEYBOARDVIEW_SETKEYBOARD = {
        "LatinKeyboardViewSetKeyboard", "elementId", "locale", "orientation", "width",
        "modeName", "action", "navigateNext", "navigatePrevious", "clobberSettingsKey",
        "passwordInput", "shortcutKeyEnabled", "hasShortcutKey", "languageSwitchKeyEnabled",
        "isMultiLine", "tw", "th", "keys"
    };
    public static void latinKeyboardView_setKeyboard(final Keyboard keyboard) {
        if (keyboard != null) {
            final KeyboardId kid = keyboard.mId;
            final boolean isPasswordView = kid.passwordInput();
            final Object[] values = {
                    KeyboardId.elementIdToName(kid.mElementId),
                    kid.mLocale + ":" + kid.mSubtype.getExtraValueOf(KEYBOARD_LAYOUT_SET),
                    kid.mOrientation,
                    kid.mWidth,
                    KeyboardId.modeName(kid.mMode),
                    kid.imeAction(),
                    kid.navigateNext(),
                    kid.navigatePrevious(),
                    kid.mClobberSettingsKey,
                    isPasswordView,
                    kid.mShortcutKeyEnabled,
                    kid.mHasShortcutKey,
                    kid.mLanguageSwitchKeyEnabled,
                    kid.isMultiLine(),
                    keyboard.mOccupiedWidth,
                    keyboard.mOccupiedHeight,
                    keyboard.mKeys
                };
            getInstance().enqueueEvent(EVENTKEYS_LATINKEYBOARDVIEW_SETKEYBOARD, values);
            getInstance().setIsPasswordView(isPasswordView);
        }
    }

    private static final String[] EVENTKEYS_LATINIME_REVERTCOMMIT = {
        "LatinIMERevertCommit", "originallyTypedWord"
    };
    public static void latinIME_revertCommit(final String originallyTypedWord) {
        final Object[] values = {
            originallyTypedWord
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_REVERTCOMMIT, values);
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_CALLLISTENERONCANCELINPUT = {
        "PointerTrackerCallListenerOnCancelInput"
    };
    public static void pointerTracker_callListenerOnCancelInput() {
        getInstance().enqueueEvent(EVENTKEYS_POINTERTRACKER_CALLLISTENERONCANCELINPUT,
                EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_CALLLISTENERONCODEINPUT = {
        "PointerTrackerCallListenerOnCodeInput", "code", "outputText", "x", "y",
        "ignoreModifierKey", "altersCode", "isEnabled"
    };
    public static void pointerTracker_callListenerOnCodeInput(final Key key, final int x,
            final int y, final boolean ignoreModifierKey, final boolean altersCode,
            final int code) {
        if (key != null) {
            CharSequence outputText = key.mOutputText;
            final Object[] values = {
                Keyboard.printableCode(scrubDigitFromCodePoint(code)), outputText == null ? null
                        : scrubDigitsFromString(outputText.toString()),
                x, y, ignoreModifierKey, altersCode, key.isEnabled()
            };
            getInstance().enqueuePotentiallyPrivateEvent(
                    EVENTKEYS_POINTERTRACKER_CALLLISTENERONCODEINPUT, values);
        }
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_CALLLISTENERONRELEASE = {
        "PointerTrackerCallListenerOnRelease", "code", "withSliding", "ignoreModifierKey",
        "isEnabled"
    };
    public static void pointerTracker_callListenerOnRelease(final Key key, final int primaryCode,
            final boolean withSliding, final boolean ignoreModifierKey) {
        if (key != null) {
            final Object[] values = {
                Keyboard.printableCode(scrubDigitFromCodePoint(primaryCode)), withSliding,
                ignoreModifierKey, key.isEnabled()
            };
            getInstance().enqueuePotentiallyPrivateEvent(
                    EVENTKEYS_POINTERTRACKER_CALLLISTENERONRELEASE, values);
        }
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_ONDOWNEVENT = {
        "PointerTrackerOnDownEvent", "deltaT", "distanceSquared"
    };
    public static void pointerTracker_onDownEvent(long deltaT, int distanceSquared) {
        final Object[] values = {
            deltaT, distanceSquared
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_POINTERTRACKER_ONDOWNEVENT, values);
    }

    private static final String[] EVENTKEYS_POINTERTRACKER_ONMOVEEVENT = {
        "PointerTrackerOnMoveEvent", "x", "y", "lastX", "lastY"
    };
    public static void pointerTracker_onMoveEvent(final int x, final int y, final int lastX,
            final int lastY) {
        final Object[] values = {
            x, y, lastX, lastY
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_POINTERTRACKER_ONMOVEEVENT, values);
    }

    private static final String[] EVENTKEYS_SUDDENJUMPINGTOUCHEVENTHANDLER_ONTOUCHEVENT = {
        "SuddenJumpingTouchEventHandlerOnTouchEvent", "motionEvent"
    };
    public static void suddenJumpingTouchEventHandler_onTouchEvent(final MotionEvent me) {
        if (me != null) {
            final Object[] values = {
                me.toString()
            };
            getInstance().enqueuePotentiallyPrivateEvent(
                    EVENTKEYS_SUDDENJUMPINGTOUCHEVENTHANDLER_ONTOUCHEVENT, values);
        }
    }

    private static final String[] EVENTKEYS_SUGGESTIONSVIEW_SETSUGGESTIONS = {
        "SuggestionsViewSetSuggestions", "suggestedWords"
    };
    public static void suggestionsView_setSuggestions(final SuggestedWords suggestedWords) {
        if (suggestedWords != null) {
            final Object[] values = {
                suggestedWords
            };
            getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_SUGGESTIONSVIEW_SETSUGGESTIONS,
                    values);
        }
    }

    private static final String[] EVENTKEYS_USER_TIMESTAMP = {
        "UserTimestamp"
    };
    public void userTimestamp() {
        getInstance().enqueueEvent(EVENTKEYS_USER_TIMESTAMP, EVENTKEYS_NULLVALUES);
    }
}
