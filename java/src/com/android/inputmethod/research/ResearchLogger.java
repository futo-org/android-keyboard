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

package com.android.inputmethod.research;

import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.CollectionUtils;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputConnection;
import com.android.inputmethod.latin.RichInputConnection.Range;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
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
    private static final boolean DEBUG = false;
    private static final boolean OUTPUT_ENTIRE_BUFFER = false;  // true may disclose private info
    public static final boolean DEFAULT_USABILITY_STUDY_MODE = false;
    /* package */ static boolean sIsLogging = false;
    private static final int OUTPUT_FORMAT_VERSION = 1;
    private static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";
    private static final String PREF_RESEARCH_HAS_SEEN_SPLASH = "pref_research_has_seen_splash";
    /* package */ static final String FILENAME_PREFIX = "researchLog";
    private static final String FILENAME_SUFFIX = ".txt";
    private static final SimpleDateFormat TIMESTAMP_DATEFORMAT =
            new SimpleDateFormat("yyyyMMddHHmmssS", Locale.US);
    private static final boolean IS_SHOWING_INDICATOR = true;
    private static final boolean IS_SHOWING_INDICATOR_CLEARLY = false;
    public static final int FEEDBACK_WORD_BUFFER_SIZE = 5;

    // constants related to specific log points
    private static final String WHITESPACE_SEPARATORS = " \t\n\r";
    private static final int MAX_INPUTVIEW_LENGTH_TO_CAPTURE = 8192; // must be >=1
    private static final String PREF_RESEARCH_LOGGER_UUID_STRING = "pref_research_logger_uuid";

    private static final ResearchLogger sInstance = new ResearchLogger();
    // to write to a different filename, e.g., for testing, set mFile before calling start()
    /* package */ File mFilesDir;
    /* package */ String mUUIDString;
    /* package */ ResearchLog mMainResearchLog;
    // mFeedbackLog records all events for the session, private or not (excepting
    // passwords).  It is written to permanent storage only if the user explicitly commands
    // the system to do so.
    // LogUnits are queued in the LogBuffers and published to the ResearchLogs when words are
    // complete.
    /* package */ ResearchLog mFeedbackLog;
    /* package */ MainLogBuffer mMainLogBuffer;
    /* package */ LogBuffer mFeedbackLogBuffer;

    private boolean mIsPasswordView = false;
    private boolean mIsLoggingSuspended = false;
    private SharedPreferences mPrefs;

    // digits entered by the user are replaced with this codepoint.
    /* package for test */ static final int DIGIT_REPLACEMENT_CODEPOINT =
            Character.codePointAt("\uE000", 0);  // U+E000 is in the "private-use area"
    // U+E001 is in the "private-use area"
    /* package for test */ static final String WORD_REPLACEMENT_STRING = "\uE001";
    private static final String PREF_LAST_CLEANUP_TIME = "pref_last_cleanup_time";
    private static final long DURATION_BETWEEN_DIR_CLEANUP_IN_MS = DateUtils.DAY_IN_MILLIS;
    private static final long MAX_LOGFILE_AGE_IN_MS = DateUtils.DAY_IN_MILLIS;
    protected static final int SUSPEND_DURATION_IN_MINUTES = 1;
    // set when LatinIME should ignore an onUpdateSelection() callback that
    // arises from operations in this class
    private static boolean sLatinIMEExpectingUpdateSelection = false;

    // used to check whether words are not unique
    private Suggest mSuggest;
    private Dictionary mDictionary;
    private MainKeyboardView mMainKeyboardView;
    private InputMethodService mInputMethodService;
    private final Statistics mStatistics;

    private Intent mUploadIntent;
    private PendingIntent mUploadPendingIntent;

    private LogUnit mCurrentLogUnit = new LogUnit();

    private ResearchLogger() {
        mStatistics = Statistics.getInstance();
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
            mUUIDString = getUUID(prefs);
            if (!prefs.contains(PREF_USABILITY_STUDY_MODE)) {
                Editor e = prefs.edit();
                e.putBoolean(PREF_USABILITY_STUDY_MODE, DEFAULT_USABILITY_STUDY_MODE);
                e.apply();
            }
            sIsLogging = prefs.getBoolean(PREF_USABILITY_STUDY_MODE, false);
            prefs.registerOnSharedPreferenceChangeListener(this);

            final long lastCleanupTime = prefs.getLong(PREF_LAST_CLEANUP_TIME, 0L);
            final long now = System.currentTimeMillis();
            if (lastCleanupTime + DURATION_BETWEEN_DIR_CLEANUP_IN_MS < now) {
                final long timeHorizon = now - MAX_LOGFILE_AGE_IN_MS;
                cleanupLoggingDir(mFilesDir, timeHorizon);
                Editor e = prefs.edit();
                e.putLong(PREF_LAST_CLEANUP_TIME, now);
                e.apply();
            }
        }
        mInputMethodService = ims;
        mPrefs = prefs;
        mUploadIntent = new Intent(mInputMethodService, UploaderService.class);
        mUploadPendingIntent = PendingIntent.getService(mInputMethodService, 0, mUploadIntent, 0);

        if (ProductionFlag.IS_EXPERIMENTAL) {
            scheduleUploadingService(mInputMethodService);
        }
    }

    /**
     * Arrange for the UploaderService to be run on a regular basis.
     *
     * Any existing scheduled invocation of UploaderService is removed and rescheduled.  This may
     * cause problems if this method is called often and frequent updates are required, but since
     * the user will likely be sleeping at some point, if the interval is less that the expected
     * sleep duration and this method is not called during that time, the service should be invoked
     * at some point.
     */
    public static void scheduleUploadingService(Context context) {
        final Intent intent = new Intent(context, UploaderService.class);
        final PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        final AlarmManager manager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        manager.cancel(pendingIntent);
        manager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                UploaderService.RUN_INTERVAL, UploaderService.RUN_INTERVAL, pendingIntent);
    }

    private void cleanupLoggingDir(final File dir, final long time) {
        for (File file : dir.listFiles()) {
            if (file.getName().startsWith(ResearchLogger.FILENAME_PREFIX) &&
                    file.lastModified() < time) {
                file.delete();
            }
        }
    }

    public void mainKeyboardView_onAttachedToWindow(final MainKeyboardView mainKeyboardView) {
        mMainKeyboardView = mainKeyboardView;
        maybeShowSplashScreen();
    }

    public void mainKeyboardView_onDetachedFromWindow() {
        mMainKeyboardView = null;
    }

    private boolean hasSeenSplash() {
        return mPrefs.getBoolean(PREF_RESEARCH_HAS_SEEN_SPLASH, false);
    }

    private Dialog mSplashDialog = null;

    private void maybeShowSplashScreen() {
        if (hasSeenSplash()) {
            return;
        }
        if (mSplashDialog != null && mSplashDialog.isShowing()) {
            return;
        }
        final IBinder windowToken = mMainKeyboardView != null
                ? mMainKeyboardView.getWindowToken() : null;
        if (windowToken == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(mInputMethodService)
                .setTitle(R.string.research_splash_title)
                .setMessage(R.string.research_splash_content)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                onUserLoggingConsent();
                                mSplashDialog.dismiss();
                            }
                })
                .setNegativeButton(android.R.string.no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final String packageName = mInputMethodService.getPackageName();
                                final Uri packageUri = Uri.parse("package:" + packageName);
                                final Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                                        packageUri);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                mInputMethodService.startActivity(intent);
                            }
                })
                .setCancelable(true)
                .setOnCancelListener(
                        new OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                mInputMethodService.requestHideSelf(0);
                            }
                });
        mSplashDialog = builder.create();
        final Window w = mSplashDialog.getWindow();
        final WindowManager.LayoutParams lp = w.getAttributes();
        lp.token = windowToken;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        w.setAttributes(lp);
        w.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mSplashDialog.show();
    }

    public void onUserLoggingConsent() {
        setLoggingAllowed(true);
        if (mPrefs == null) {
            return;
        }
        final Editor e = mPrefs.edit();
        e.putBoolean(PREF_RESEARCH_HAS_SEEN_SPLASH, true);
        e.apply();
        restart();
    }

    private void setLoggingAllowed(boolean enableLogging) {
        if (mPrefs == null) {
            return;
        }
        Editor e = mPrefs.edit();
        e.putBoolean(PREF_USABILITY_STUDY_MODE, enableLogging);
        e.apply();
        sIsLogging = enableLogging;
    }

    private File createLogFile(File filesDir) {
        final StringBuilder sb = new StringBuilder();
        sb.append(FILENAME_PREFIX).append('-');
        sb.append(mUUIDString).append('-');
        sb.append(TIMESTAMP_DATEFORMAT.format(new Date()));
        sb.append(FILENAME_SUFFIX);
        return new File(filesDir, sb.toString());
    }

    private void checkForEmptyEditor() {
        if (mInputMethodService == null) {
            return;
        }
        final InputConnection ic = mInputMethodService.getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        final CharSequence textBefore = ic.getTextBeforeCursor(1, 0);
        if (!TextUtils.isEmpty(textBefore)) {
            mStatistics.setIsEmptyUponStarting(false);
            return;
        }
        final CharSequence textAfter = ic.getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(textAfter)) {
            mStatistics.setIsEmptyUponStarting(false);
            return;
        }
        if (textBefore != null && textAfter != null) {
            mStatistics.setIsEmptyUponStarting(true);
        }
    }

    private void start() {
        if (DEBUG) {
            Log.d(TAG, "start called");
        }
        maybeShowSplashScreen();
        updateSuspendedState();
        requestIndicatorRedraw();
        mStatistics.reset();
        checkForEmptyEditor();
        if (!isAllowedToLog()) {
            // Log.w(TAG, "not in usability mode; not logging");
            return;
        }
        if (mFilesDir == null || !mFilesDir.exists()) {
            Log.w(TAG, "IME storage directory does not exist.  Cannot start logging.");
            return;
        }
        if (mMainLogBuffer == null) {
            mMainResearchLog = new ResearchLog(createLogFile(mFilesDir));
            mMainLogBuffer = new MainLogBuffer(mMainResearchLog);
            mMainLogBuffer.setSuggest(mSuggest);
        }
        if (mFeedbackLogBuffer == null) {
            mFeedbackLog = new ResearchLog(createLogFile(mFilesDir));
            // LogBuffer is one more than FEEDBACK_WORD_BUFFER_SIZE, because it must also hold
            // the feedback LogUnit itself.
            mFeedbackLogBuffer = new LogBuffer(FEEDBACK_WORD_BUFFER_SIZE + 1);
        }
    }

    /* package */ void stop() {
        if (DEBUG) {
            Log.d(TAG, "stop called");
        }
        logStatistics();
        commitCurrentLogUnit();

        if (mMainLogBuffer != null) {
            publishLogBuffer(mMainLogBuffer, mMainResearchLog, false /* isIncludingPrivateData */);
            mMainResearchLog.close(null /* callback */);
            mMainLogBuffer = null;
        }
        if (mFeedbackLogBuffer != null) {
            mFeedbackLog.close(null /* callback */);
            mFeedbackLogBuffer = null;
        }
    }

    public boolean abort() {
        if (DEBUG) {
            Log.d(TAG, "abort called");
        }
        boolean didAbortMainLog = false;
        if (mMainLogBuffer != null) {
            mMainLogBuffer.clear();
            try {
                didAbortMainLog = mMainResearchLog.blockingAbort();
            } catch (InterruptedException e) {
                // Don't know whether this succeeded or not.  We assume not; this is reported
                // to the caller.
            }
            mMainLogBuffer = null;
        }
        boolean didAbortFeedbackLog = false;
        if (mFeedbackLogBuffer != null) {
            mFeedbackLogBuffer.clear();
            try {
                didAbortFeedbackLog = mFeedbackLog.blockingAbort();
            } catch (InterruptedException e) {
                // Don't know whether this succeeded or not.  We assume not; this is reported
                // to the caller.
            }
            mFeedbackLogBuffer = null;
        }
        return didAbortMainLog && didAbortFeedbackLog;
    }

    private void restart() {
        stop();
        start();
    }

    private long mResumeTime = 0L;
    private void suspendLoggingUntil(long time) {
        mIsLoggingSuspended = true;
        mResumeTime = time;
        requestIndicatorRedraw();
    }

    private void resumeLogging() {
        mResumeTime = 0L;
        updateSuspendedState();
        requestIndicatorRedraw();
        if (isAllowedToLog()) {
            restart();
        }
    }

    private void updateSuspendedState() {
        final long time = System.currentTimeMillis();
        if (time > mResumeTime) {
            mIsLoggingSuspended = false;
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
        requestIndicatorRedraw();
        mPrefs = prefs;
        prefsChanged(prefs);
    }

    public void onResearchKeySelected(final LatinIME latinIME) {
        if (mInFeedbackDialog) {
            Toast.makeText(latinIME, R.string.research_please_exit_feedback_form,
                    Toast.LENGTH_LONG).show();
            return;
        }
        presentFeedbackDialog(latinIME);
    }

    // TODO: currently unreachable.  Remove after being sure no menu is needed.
    /*
    public void presentResearchDialog(final LatinIME latinIME) {
        final CharSequence title = latinIME.getString(R.string.english_ime_research_log);
        final boolean showEnable = mIsLoggingSuspended || !sIsLogging;
        final CharSequence[] items = new CharSequence[] {
                latinIME.getString(R.string.research_feedback_menu_option),
                showEnable ? latinIME.getString(R.string.research_enable_session_logging) :
                        latinIME.getString(R.string.research_do_not_log_this_session)
        };
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                    case 0:
                        presentFeedbackDialog(latinIME);
                        break;
                    case 1:
                        enableOrDisable(showEnable, latinIME);
                        break;
                }
            }

        };
        final AlertDialog.Builder builder = new AlertDialog.Builder(latinIME)
                .setItems(items, listener)
                .setTitle(title);
        latinIME.showOptionDialog(builder.create());
    }
    */

    private boolean mInFeedbackDialog = false;
    public void presentFeedbackDialog(LatinIME latinIME) {
        mInFeedbackDialog = true;
        latinIME.launchKeyboardedDialogActivity(FeedbackActivity.class);
    }

    // TODO: currently unreachable.  Remove after being sure enable/disable is
    // not needed.
    /*
    public void enableOrDisable(final boolean showEnable, final LatinIME latinIME) {
        if (showEnable) {
            if (!sIsLogging) {
                setLoggingAllowed(true);
            }
            resumeLogging();
            Toast.makeText(latinIME,
                    R.string.research_notify_session_logging_enabled,
                    Toast.LENGTH_LONG).show();
        } else {
            Toast toast = Toast.makeText(latinIME,
                    R.string.research_notify_session_log_deleting,
                    Toast.LENGTH_LONG);
            toast.show();
            boolean isLogDeleted = abort();
            final long currentTime = System.currentTimeMillis();
            final long resumeTime = currentTime + 1000 * 60 *
                    SUSPEND_DURATION_IN_MINUTES;
            suspendLoggingUntil(resumeTime);
            toast.cancel();
            Toast.makeText(latinIME, R.string.research_notify_logging_suspended,
                    Toast.LENGTH_LONG).show();
        }
    }
    */

    private static final String[] EVENTKEYS_FEEDBACK = {
        "UserTimestamp", "contents"
    };
    public void sendFeedback(final String feedbackContents, final boolean includeHistory) {
        if (mFeedbackLogBuffer == null) {
            return;
        }
        if (includeHistory) {
            commitCurrentLogUnit();
        } else {
            mFeedbackLogBuffer.clear();
        }
        final LogUnit feedbackLogUnit = new LogUnit();
        final Object[] values = {
            feedbackContents
        };
        feedbackLogUnit.addLogStatement(EVENTKEYS_FEEDBACK, values,
                false /* isPotentiallyPrivate */);
        mFeedbackLogBuffer.shiftIn(feedbackLogUnit);
        publishLogBuffer(mFeedbackLogBuffer, mFeedbackLog, true /* isIncludingPrivateData */);
        mFeedbackLog.close(new Runnable() {
            @Override
            public void run() {
                uploadNow();
            }
        });
        mFeedbackLog = new ResearchLog(createLogFile(mFilesDir));
    }

    public void uploadNow() {
        if (DEBUG) {
            Log.d(TAG, "calling uploadNow()");
        }
        mInputMethodService.startService(mUploadIntent);
    }

    public void onLeavingSendFeedbackDialog() {
        mInFeedbackDialog = false;
    }

    public void initSuggest(Suggest suggest) {
        mSuggest = suggest;
        if (mMainLogBuffer != null) {
            mMainLogBuffer.setSuggest(mSuggest);
        }
    }

    private void setIsPasswordView(boolean isPasswordView) {
        mIsPasswordView = isPasswordView;
    }

    private boolean isAllowedToLog() {
        if (DEBUG) {
            Log.d(TAG, "iatl: " +
                "mipw=" + mIsPasswordView +
                ", mils=" + mIsLoggingSuspended +
                ", sil=" + sIsLogging +
                ", mInFeedbackDialog=" + mInFeedbackDialog);
        }
        return !mIsPasswordView && !mIsLoggingSuspended && sIsLogging && !mInFeedbackDialog;
    }

    public void requestIndicatorRedraw() {
        if (!IS_SHOWING_INDICATOR) {
            return;
        }
        if (mMainKeyboardView == null) {
            return;
        }
        mMainKeyboardView.invalidateAllKeys();
    }


    public void paintIndicator(KeyboardView view, Paint paint, Canvas canvas, int width,
            int height) {
        // TODO: Reimplement using a keyboard background image specific to the ResearchLogger
        // and remove this method.
        // The check for MainKeyboardView ensures that a red border is only placed around
        // the main keyboard, not every keyboard.
        if (IS_SHOWING_INDICATOR && isAllowedToLog() && view instanceof MainKeyboardView) {
            final int savedColor = paint.getColor();
            paint.setColor(Color.RED);
            final Style savedStyle = paint.getStyle();
            paint.setStyle(Style.STROKE);
            final float savedStrokeWidth = paint.getStrokeWidth();
            if (IS_SHOWING_INDICATOR_CLEARLY) {
                paint.setStrokeWidth(5);
                canvas.drawRect(0, 0, width, height, paint);
            } else {
                // Put a tiny red dot on the screen so a knowledgeable user can check whether
                // it is enabled.  The dot is actually a zero-width, zero-height rectangle,
                // placed at the lower-right corner of the canvas, painted with a non-zero border
                // width.
                paint.setStrokeWidth(3);
                canvas.drawRect(width, height, width, height, paint);
            }
            paint.setColor(savedColor);
            paint.setStyle(savedStyle);
            paint.setStrokeWidth(savedStrokeWidth);
        }
    }

    private static final Object[] EVENTKEYS_NULLVALUES = {};

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
            mCurrentLogUnit.addLogStatement(keys, values, true /* isPotentiallyPrivate */);
        }
    }

    private void setCurrentLogUnitContainsDigitFlag() {
        mCurrentLogUnit.setContainsDigit();
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
            mCurrentLogUnit.addLogStatement(keys, values, false /* isPotentiallyPrivate */);
        }
    }

    /* package for test */ void commitCurrentLogUnit() {
        if (DEBUG) {
            Log.d(TAG, "commitCurrentLogUnit");
        }
        if (!mCurrentLogUnit.isEmpty()) {
            if (mMainLogBuffer != null) {
                mMainLogBuffer.shiftIn(mCurrentLogUnit);
                if (mMainLogBuffer.isSafeToLog() && mMainResearchLog != null) {
                    publishLogBuffer(mMainLogBuffer, mMainResearchLog,
                            true /* isIncludingPrivateData */);
                    mMainLogBuffer.resetWordCounter();
                }
            }
            if (mFeedbackLogBuffer != null) {
                mFeedbackLogBuffer.shiftIn(mCurrentLogUnit);
            }
            mCurrentLogUnit = new LogUnit();
            Log.d(TAG, "commitCurrentLogUnit");
        }
    }

    /* package for test */ void publishLogBuffer(final LogBuffer logBuffer,
            final ResearchLog researchLog, final boolean isIncludingPrivateData) {
        LogUnit logUnit;
        while ((logUnit = logBuffer.shiftOut()) != null) {
            researchLog.publish(logUnit, isIncludingPrivateData);
        }
    }

    private boolean hasOnlyLetters(final String word) {
        final int length = word.length();
        for (int i = 0; i < length; i = word.offsetByCodePoints(i, 1)) {
            final int codePoint = word.codePointAt(i);
            if (!Character.isLetter(codePoint)) {
                return false;
            }
        }
        return true;
    }

    private void onWordComplete(final String word) {
        Log.d(TAG, "onWordComplete: " + word);
        if (word != null && word.length() > 0 && hasOnlyLetters(word)) {
            mCurrentLogUnit.setWord(word);
            mStatistics.recordWordEntered();
        }
        commitCurrentLogUnit();
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

    private String scrubWord(String word) {
        if (mDictionary == null) {
            return WORD_REPLACEMENT_STRING;
        }
        if (mDictionary.isValidWord(word)) {
            return word;
        }
        return WORD_REPLACEMENT_STRING;
    }

    private static final String[] EVENTKEYS_LATINIME_ONSTARTINPUTVIEWINTERNAL = {
        "LatinIMEOnStartInputViewInternal", "uuid", "packageName", "inputType", "imeOptions",
        "fieldId", "display", "model", "prefs", "versionCode", "versionName", "outputFormatVersion"
    };
    public static void latinIME_onStartInputViewInternal(final EditorInfo editorInfo,
            final SharedPreferences prefs) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.start();
        if (editorInfo != null) {
            final Context context = researchLogger.mInputMethodService;
            try {
                final PackageInfo packageInfo;
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(),
                        0);
                final Integer versionCode = packageInfo.versionCode;
                final String versionName = packageInfo.versionName;
                final Object[] values = {
                        researchLogger.mUUIDString, editorInfo.packageName,
                        Integer.toHexString(editorInfo.inputType),
                        Integer.toHexString(editorInfo.imeOptions), editorInfo.fieldId,
                        Build.DISPLAY, Build.MODEL, prefs, versionCode, versionName,
                        OUTPUT_FORMAT_VERSION
                };
                researchLogger.enqueueEvent(EVENTKEYS_LATINIME_ONSTARTINPUTVIEWINTERNAL, values);
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void latinIME_onFinishInputInternal() {
        stop();
    }

    private static final String[] EVENTKEYS_USER_FEEDBACK = {
        "UserFeedback", "FeedbackContents"
    };

    private static final String[] EVENTKEYS_PREFS_CHANGED = {
        "PrefsChanged", "prefs"
    };
    public static void prefsChanged(final SharedPreferences prefs) {
        final ResearchLogger researchLogger = getInstance();
        final Object[] values = {
            prefs
        };
        researchLogger.enqueueEvent(EVENTKEYS_PREFS_CHANGED, values);
    }

    // Regular logging methods

    private static final String[] EVENTKEYS_MAINKEYBOARDVIEW_PROCESSMOTIONEVENT = {
        "MainKeyboardViewProcessMotionEvent", "action", "eventTime", "id", "x", "y", "size",
        "pressure"
    };
    public static void mainKeyboardView_processMotionEvent(final MotionEvent me, final int action,
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
                    EVENTKEYS_MAINKEYBOARDVIEW_PROCESSMOTIONEVENT, values);
        }
    }

    private static final String[] EVENTKEYS_LATINIME_ONCODEINPUT = {
        "LatinIMEOnCodeInput", "code", "x", "y"
    };
    public static void latinIME_onCodeInput(final int code, final int x, final int y) {
        final long time = SystemClock.uptimeMillis();
        final ResearchLogger researchLogger = getInstance();
        final Object[] values = {
            Keyboard.printableCode(scrubDigitFromCodePoint(code)), x, y
        };
        researchLogger.enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_ONCODEINPUT, values);
        if (Character.isDigit(code)) {
            researchLogger.setCurrentLogUnitContainsDigitFlag();
        }
        researchLogger.mStatistics.recordChar(code, time);
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

    public static boolean getAndClearLatinIMEExpectingUpdateSelection() {
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
            // Capture the TextView contents.  This will trigger onUpdateSelection(), so we
            // set sLatinIMEExpectingUpdateSelection so that when onUpdateSelection() is called,
            // it can tell that it was generated by the logging code, and not by the user, and
            // therefore keep user-visible state as is.
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
            researchLogger.commitCurrentLogUnit();
            getInstance().stop();
        }
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

    private static final String[] EVENTKEYS_LATINIME_PICKSUGGESTIONMANUALLY = {
        "LatinIMEPickSuggestionManually", "replacedWord", "index", "suggestion", "x", "y"
    };
    public static void latinIME_pickSuggestionManually(final String replacedWord,
            final int index, CharSequence suggestion) {
        final Object[] values = {
            scrubDigitsFromString(replacedWord), index,
            (suggestion == null ? null : scrubDigitsFromString(suggestion.toString())),
            Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE
        };
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_PICKSUGGESTIONMANUALLY,
                values);
    }

    private static final String[] EVENTKEYS_LATINIME_PUNCTUATIONSUGGESTION = {
        "LatinIMEPunctuationSuggestion", "index", "suggestion", "x", "y"
    };
    public static void latinIME_punctuationSuggestion(final int index,
            final CharSequence suggestion) {
        final Object[] values = {
            index, suggestion,
            Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE
        };
        getInstance().enqueueEvent(EVENTKEYS_LATINIME_PUNCTUATIONSUGGESTION, values);
    }

    private static final String[] EVENTKEYS_LATINIME_SENDKEYCODEPOINT = {
        "LatinIMESendKeyCodePoint", "code"
    };
    public static void latinIME_sendKeyCodePoint(final int code) {
        final Object[] values = {
            Keyboard.printableCode(scrubDigitFromCodePoint(code))
        };
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueuePotentiallyPrivateEvent(EVENTKEYS_LATINIME_SENDKEYCODEPOINT, values);
        if (Character.isDigit(code)) {
            researchLogger.setCurrentLogUnitContainsDigitFlag();
        }
    }

    private static final String[] EVENTKEYS_LATINIME_SWAPSWAPPERANDSPACE = {
        "LatinIMESwapSwapperAndSpace"
    };
    public static void latinIME_swapSwapperAndSpace() {
        getInstance().enqueueEvent(EVENTKEYS_LATINIME_SWAPSWAPPERANDSPACE, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_MAINKEYBOARDVIEW_ONLONGPRESS = {
        "MainKeyboardViewOnLongPress"
    };
    public static void mainKeyboardView_onLongPress() {
        getInstance().enqueueEvent(EVENTKEYS_MAINKEYBOARDVIEW_ONLONGPRESS, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_MAINKEYBOARDVIEW_SETKEYBOARD = {
        "MainKeyboardViewSetKeyboard", "elementId", "locale", "orientation", "width",
        "modeName", "action", "navigateNext", "navigatePrevious", "clobberSettingsKey",
        "passwordInput", "shortcutKeyEnabled", "hasShortcutKey", "languageSwitchKeyEnabled",
        "isMultiLine", "tw", "th", "keys"
    };
    public static void mainKeyboardView_setKeyboard(final Keyboard keyboard) {
        if (keyboard != null) {
            final KeyboardId kid = keyboard.mId;
            final boolean isPasswordView = kid.passwordInput();
            getInstance().setIsPasswordView(isPasswordView);
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
            getInstance().setIsPasswordView(isPasswordView);
            getInstance().enqueueEvent(EVENTKEYS_MAINKEYBOARDVIEW_SETKEYBOARD, values);
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
            String outputText = key.getOutputText();
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

    private static final String[] EVENTKEYS_RICHINPUTCONNECTION_COMMITCOMPLETION = {
        "RichInputConnectionCommitCompletion", "completionInfo"
    };
    public static void richInputConnection_commitCompletion(final CompletionInfo completionInfo) {
        final Object[] values = {
            completionInfo
        };
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueuePotentiallyPrivateEvent(
                EVENTKEYS_RICHINPUTCONNECTION_COMMITCOMPLETION, values);
    }

    // Disabled for privacy-protection reasons.  Because this event comes after
    // richInputConnection_commitText, which is the event used to separate LogUnits, the
    // data in this event can be associated with the next LogUnit, revealing information
    // about the current word even if it was supposed to be suppressed.  The occurrance of
    // autocorrection can be determined by examining the difference between the text strings in
    // the last call to richInputConnection_setComposingText before
    // richInputConnection_commitText, so it's not a data loss.
    // TODO: Figure out how to log this event without loss of privacy.
    /*
    private static final String[] EVENTKEYS_RICHINPUTCONNECTION_COMMITCORRECTION = {
        "RichInputConnectionCommitCorrection", "typedWord", "autoCorrection"
    };
    */
    public static void richInputConnection_commitCorrection(CorrectionInfo correctionInfo) {
        /*
        final String typedWord = correctionInfo.getOldText().toString();
        final String autoCorrection = correctionInfo.getNewText().toString();
        final Object[] values = {
            scrubDigitsFromString(typedWord), scrubDigitsFromString(autoCorrection)
        };
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueuePotentiallyPrivateEvent(
                EVENTKEYS_RICHINPUTCONNECTION_COMMITCORRECTION, values);
        */
    }

    private static final String[] EVENTKEYS_RICHINPUTCONNECTION_COMMITTEXT = {
        "RichInputConnectionCommitText", "typedWord", "newCursorPosition"
    };
    public static void richInputConnection_commitText(final CharSequence typedWord,
            final int newCursorPosition) {
        final String scrubbedWord = scrubDigitsFromString(typedWord.toString());
        final Object[] values = {
            scrubbedWord, newCursorPosition
        };
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueuePotentiallyPrivateEvent(EVENTKEYS_RICHINPUTCONNECTION_COMMITTEXT,
                values);
        researchLogger.onWordComplete(scrubbedWord);
    }

    private static final String[] EVENTKEYS_RICHINPUTCONNECTION_DELETESURROUNDINGTEXT = {
        "RichInputConnectionDeleteSurroundingText", "beforeLength", "afterLength"
    };
    public static void richInputConnection_deleteSurroundingText(final int beforeLength,
            final int afterLength) {
        final Object[] values = {
            beforeLength, afterLength
        };
        getInstance().enqueuePotentiallyPrivateEvent(
                EVENTKEYS_RICHINPUTCONNECTION_DELETESURROUNDINGTEXT, values);
    }

    private static final String[] EVENTKEYS_RICHINPUTCONNECTION_FINISHCOMPOSINGTEXT = {
        "RichInputConnectionFinishComposingText"
    };
    public static void richInputConnection_finishComposingText() {
        getInstance().enqueueEvent(EVENTKEYS_RICHINPUTCONNECTION_FINISHCOMPOSINGTEXT,
                EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_RICHINPUTCONNECTION_PERFORMEDITORACTION = {
        "RichInputConnectionPerformEditorAction", "imeActionNext"
    };
    public static void richInputConnection_performEditorAction(final int imeActionNext) {
        final Object[] values = {
            imeActionNext
        };
        getInstance().enqueueEvent(EVENTKEYS_RICHINPUTCONNECTION_PERFORMEDITORACTION, values);
    }

    private static final String[] EVENTKEYS_RICHINPUTCONNECTION_SENDKEYEVENT = {
        "RichInputConnectionSendKeyEvent", "eventTime", "action", "code"
    };
    public static void richInputConnection_sendKeyEvent(final KeyEvent keyEvent) {
        final Object[] values = {
            keyEvent.getEventTime(),
            keyEvent.getAction(),
            keyEvent.getKeyCode()
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_RICHINPUTCONNECTION_SENDKEYEVENT,
                values);
    }

    private static final String[] EVENTKEYS_RICHINPUTCONNECTION_SETCOMPOSINGTEXT = {
        "RichInputConnectionSetComposingText", "text", "newCursorPosition"
    };
    public static void richInputConnection_setComposingText(final CharSequence text,
            final int newCursorPosition) {
        if (text == null) {
            throw new RuntimeException("setComposingText is null");
        }
        final Object[] values = {
            text, newCursorPosition
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_RICHINPUTCONNECTION_SETCOMPOSINGTEXT,
                values);
    }

    private static final String[] EVENTKEYS_RICHINPUTCONNECTION_SETSELECTION = {
        "RichInputConnectionSetSelection", "from", "to"
    };
    public static void richInputConnection_setSelection(final int from, final int to) {
        final Object[] values = {
            from, to
        };
        getInstance().enqueuePotentiallyPrivateEvent(EVENTKEYS_RICHINPUTCONNECTION_SETSELECTION,
                values);
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

    private static final String[] EVENTKEYS_SUGGESTIONSTRIPVIEW_SETSUGGESTIONS = {
        "SuggestionStripViewSetSuggestions", "suggestedWords"
    };
    public static void suggestionStripView_setSuggestions(final SuggestedWords suggestedWords) {
        if (suggestedWords != null) {
            final Object[] values = {
                suggestedWords
            };
            getInstance().enqueuePotentiallyPrivateEvent(
                    EVENTKEYS_SUGGESTIONSTRIPVIEW_SETSUGGESTIONS, values);
        }
    }

    private static final String[] EVENTKEYS_USER_TIMESTAMP = {
        "UserTimestamp"
    };
    public void userTimestamp() {
        getInstance().enqueueEvent(EVENTKEYS_USER_TIMESTAMP, EVENTKEYS_NULLVALUES);
    }

    private static final String[] EVENTKEYS_STATISTICS = {
        "Statistics", "charCount", "letterCount", "numberCount", "spaceCount", "deleteOpsCount",
        "wordCount", "isEmptyUponStarting", "isEmptinessStateKnown", "averageTimeBetweenKeys",
        "averageTimeBeforeDelete", "averageTimeDuringRepeatedDelete", "averageTimeAfterDelete"
    };
    private static void logStatistics() {
        final ResearchLogger researchLogger = getInstance();
        final Statistics statistics = researchLogger.mStatistics;
        final Object[] values = {
            statistics.mCharCount, statistics.mLetterCount, statistics.mNumberCount,
            statistics.mSpaceCount, statistics.mDeleteKeyCount,
            statistics.mWordCount, statistics.mIsEmptyUponStarting,
            statistics.mIsEmptinessStateKnown, statistics.mKeyCounter.getAverageTime(),
            statistics.mBeforeDeleteKeyCounter.getAverageTime(),
            statistics.mDuringRepeatedDeleteKeysCounter.getAverageTime(),
            statistics.mAfterDeleteKeyCounter.getAverageTime()
        };
        researchLogger.enqueueEvent(EVENTKEYS_STATISTICS, values);
    }
}
