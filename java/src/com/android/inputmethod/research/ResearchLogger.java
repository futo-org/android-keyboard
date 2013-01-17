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
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.InputTypeUtils;
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
    private static final boolean DEBUG = false && ProductionFlag.IS_EXPERIMENTAL_DEBUG;
    // Whether all n-grams should be logged.  true will disclose private info.
    public static final boolean IS_LOGGING_EVERYTHING = false
            && ProductionFlag.IS_EXPERIMENTAL_DEBUG;
    // Whether the TextView contents are logged at the end of the session.  true will disclose
    // private info.
    private static final boolean LOG_FULL_TEXTVIEW_CONTENTS = false
            && ProductionFlag.IS_EXPERIMENTAL_DEBUG;
    public static final boolean DEFAULT_USABILITY_STUDY_MODE = false;
    /* package */ static boolean sIsLogging = false;
    private static final int OUTPUT_FORMAT_VERSION = 5;
    private static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";
    private static final String PREF_RESEARCH_HAS_SEEN_SPLASH = "pref_research_has_seen_splash";
    /* package */ static final String FILENAME_PREFIX = "researchLog";
    private static final String FILENAME_SUFFIX = ".txt";
    private static final SimpleDateFormat TIMESTAMP_DATEFORMAT =
            new SimpleDateFormat("yyyyMMddHHmmssS", Locale.US);
    // Whether to show an indicator on the screen that logging is on.  Currently a very small red
    // dot in the lower right hand corner.  Most users should not notice it.
    private static final boolean IS_SHOWING_INDICATOR = true;
    // Change the default indicator to something very visible.  Currently two red vertical bars on
    // either side of they keyboard.
    private static final boolean IS_SHOWING_INDICATOR_CLEARLY = false || IS_LOGGING_EVERYTHING;
    // FEEDBACK_WORD_BUFFER_SIZE should add 1 because it must also hold the feedback LogUnit itself.
    public static final int FEEDBACK_WORD_BUFFER_SIZE = (Integer.MAX_VALUE - 1) + 1;

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
    private MainKeyboardView mMainKeyboardView;
    private LatinIME mLatinIME;
    private final Statistics mStatistics;

    private Intent mUploadIntent;

    private LogUnit mCurrentLogUnit = new LogUnit();

    // Gestured or tapped words may be committed after the gesture of the next word has started.
    // To ensure that the gesture data of the next word is not associated with the previous word,
    // thereby leaking private data, we store the time of the down event that started the second
    // gesture, and when committing the earlier word, split the LogUnit.
    private long mSavedDownEventTime;
    private ResearchLogger() {
        mStatistics = Statistics.getInstance();
    }

    public static ResearchLogger getInstance() {
        return sInstance;
    }

    public void init(final LatinIME latinIME) {
        assert latinIME != null;
        if (latinIME == null) {
            Log.w(TAG, "IMS is null; logging is off");
        } else {
            mFilesDir = latinIME.getFilesDir();
            if (mFilesDir == null || !mFilesDir.exists()) {
                Log.w(TAG, "IME storage directory does not exist.");
            }
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(latinIME);
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
        mLatinIME = latinIME;
        mPrefs = prefs;
        mUploadIntent = new Intent(mLatinIME, UploaderService.class);

        if (ProductionFlag.IS_EXPERIMENTAL) {
            scheduleUploadingService(mLatinIME);
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
        final AlertDialog.Builder builder = new AlertDialog.Builder(mLatinIME)
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
                                final String packageName = mLatinIME.getPackageName();
                                final Uri packageUri = Uri.parse("package:" + packageName);
                                final Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                                        packageUri);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                mLatinIME.startActivity(intent);
                            }
                })
                .setCancelable(true)
                .setOnCancelListener(
                        new OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                mLatinIME.requestHideSelf(0);
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

    private static int sLogFileCounter = 0;

    private File createLogFile(File filesDir) {
        final StringBuilder sb = new StringBuilder();
        sb.append(FILENAME_PREFIX).append('-');
        sb.append(mUUIDString).append('-');
        sb.append(TIMESTAMP_DATEFORMAT.format(new Date())).append('-');
        // Sometimes logFiles are created within milliseconds of each other.  Append a counter to
        // separate these.
        if (sLogFileCounter < Integer.MAX_VALUE) {
            sLogFileCounter++;
        } else {
            // Wrap the counter, in the unlikely event of overflow.
            sLogFileCounter = 0;
        }
        sb.append(sLogFileCounter);
        sb.append(FILENAME_SUFFIX);
        return new File(filesDir, sb.toString());
    }

    private void checkForEmptyEditor() {
        if (mLatinIME == null) {
            return;
        }
        final InputConnection ic = mLatinIME.getCurrentInputConnection();
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
            mMainResearchLog = new ResearchLog(createLogFile(mFilesDir), mLatinIME);
            mMainLogBuffer = new MainLogBuffer(mMainResearchLog);
            mMainLogBuffer.setSuggest(mSuggest);
        }
        if (mFeedbackLogBuffer == null) {
            mFeedbackLog = new ResearchLog(createLogFile(mFilesDir), mLatinIME);
            mFeedbackLogBuffer = new FixedLogBuffer(FEEDBACK_WORD_BUFFER_SIZE);
        }
    }

    /* package */ void stop() {
        if (DEBUG) {
            Log.d(TAG, "stop called");
        }
        // Commit mCurrentLogUnit before closing.
        commitCurrentLogUnit();

        if (mMainLogBuffer != null) {
            while (!mMainLogBuffer.isEmpty()) {
                if ((mMainLogBuffer.isNGramSafe() || IS_LOGGING_EVERYTHING) &&
                        mMainResearchLog != null) {
                    publishLogBuffer(mMainLogBuffer, mMainResearchLog,
                            true /* isIncludingPrivateData */);
                    mMainLogBuffer.resetWordCounter();
                } else {
                    mMainLogBuffer.shiftOutThroughFirstWord();
                }
            }
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

    // The feedback dialog causes stop() to be called for the keyboard connected to the original
    // window.  This is because the feedback dialog must present its own EditText box that displays
    // a keyboard.  stop() normally causes mFeedbackLogBuffer, which contains the user's data, to be
    // cleared, and causes mFeedbackLog, which is ready to collect information in case the user
    // wants to upload, to be closed.  This is good because we don't need to log information about
    // what the user is typing in the feedback dialog, but bad because this data must be uploaded.
    // Here we save the LogBuffer and Log so the feedback dialog can later access their data.
    private LogBuffer mSavedFeedbackLogBuffer;
    private ResearchLog mSavedFeedbackLog;

    public void presentFeedbackDialog(LatinIME latinIME) {
        mInFeedbackDialog = true;
        mSavedFeedbackLogBuffer = mFeedbackLogBuffer;
        mSavedFeedbackLog = mFeedbackLog;
        // Set the non-saved versions to null so that the stop() caused by switching to the
        // Feedback dialog will not close them.
        mFeedbackLogBuffer = null;
        mFeedbackLog = null;
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

    static class LogStatement {
        final String mName;

        // mIsPotentiallyPrivate indicates that event contains potentially private information.  If
        // the word that this event is a part of is determined to be privacy-sensitive, then this
        // event should not be included in the output log.  The system waits to output until the
        // containing word is known.
        final boolean mIsPotentiallyPrivate;

        // mIsPotentiallyRevealing indicates that this statement may disclose details about other
        // words typed in other LogUnits.  This can happen if the user is not inserting spaces, and
        // data from Suggestions and/or Composing text reveals the entire "megaword".  For example,
        // say the user is typing "for the win", and the system wants to record the bigram "the
        // win".  If the user types "forthe", omitting the space, the system will give "for the" as
        // a suggestion.  If the user accepts the autocorrection, the suggestion for "for the" is
        // included in the log for the word "the", disclosing that the previous word had been "for".
        // For now, we simply do not include this data when logging part of a "megaword".
        final boolean mIsPotentiallyRevealing;

        // mKeys stores the names that are the attributes in the output json objects
        final String[] mKeys;
        private static final String[] NULL_KEYS = new String[0];

        LogStatement(final String name, final boolean isPotentiallyPrivate,
                final boolean isPotentiallyRevealing, final String... keys) {
            mName = name;
            mIsPotentiallyPrivate = isPotentiallyPrivate;
            mIsPotentiallyRevealing = isPotentiallyRevealing;
            mKeys = (keys == null) ? NULL_KEYS : keys;
        }
    }

    private static final LogStatement LOGSTATEMENT_FEEDBACK =
            new LogStatement("UserFeedback", false, false, "contents");
    public void sendFeedback(final String feedbackContents, final boolean includeHistory) {
        if (mSavedFeedbackLogBuffer == null) {
            return;
        }
        if (!includeHistory) {
            mSavedFeedbackLogBuffer.clear();
        }
        final LogUnit feedbackLogUnit = new LogUnit();
        feedbackLogUnit.addLogStatement(LOGSTATEMENT_FEEDBACK, SystemClock.uptimeMillis(),
                feedbackContents);
        mFeedbackLogBuffer.shiftIn(feedbackLogUnit);
        publishLogBuffer(mFeedbackLogBuffer, mSavedFeedbackLog, true /* isIncludingPrivateData */);
        mSavedFeedbackLog.close(new Runnable() {
            @Override
            public void run() {
                uploadNow();
            }
        });
    }

    public void uploadNow() {
        if (DEBUG) {
            Log.d(TAG, "calling uploadNow()");
        }
        mLatinIME.startService(mUploadIntent);
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

    private Dictionary getDictionary() {
        if (mSuggest == null) {
            return null;
        }
        return mSuggest.getMainDictionary();
    }

    private void setIsPasswordView(boolean isPasswordView) {
        mIsPasswordView = isPasswordView;
    }

    private boolean isAllowedToLog() {
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
                canvas.drawLine(0, 0, 0, height, paint);
                canvas.drawLine(width, 0, width, height, paint);
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

    /**
     * Buffer a research log event, flagging it as privacy-sensitive.
     */
    private synchronized void enqueueEvent(final LogStatement logStatement,
            final Object... values) {
        enqueueEvent(mCurrentLogUnit, logStatement, values);
    }

    private synchronized void enqueueEvent(final LogUnit logUnit, final LogStatement logStatement,
            final Object... values) {
        assert values.length == logStatement.mKeys.length;
        if (isAllowedToLog() && logUnit != null) {
            final long time = SystemClock.uptimeMillis();
            logUnit.addLogStatement(logStatement, time, values);
        }
    }

    private void setCurrentLogUnitContainsDigitFlag() {
        mCurrentLogUnit.setMayContainDigit();
    }

    /* package for test */ void commitCurrentLogUnit() {
        if (DEBUG) {
            Log.d(TAG, "commitCurrentLogUnit" + (mCurrentLogUnit.hasWord() ?
                    ": " + mCurrentLogUnit.getWord() : ""));
        }
        if (!mCurrentLogUnit.isEmpty()) {
            if (mMainLogBuffer != null) {
                if ((mMainLogBuffer.isNGramSafe() || IS_LOGGING_EVERYTHING) &&
                        mMainLogBuffer.isNGramComplete() &&
                        mMainResearchLog != null) {
                    publishLogBuffer(mMainLogBuffer, mMainResearchLog,
                            true /* isIncludingPrivateData */);
                    mMainLogBuffer.resetWordCounter();
                }
                mMainLogBuffer.shiftIn(mCurrentLogUnit);
            }
            if (mFeedbackLogBuffer != null) {
                mFeedbackLogBuffer.shiftIn(mCurrentLogUnit);
            }
            mCurrentLogUnit = new LogUnit();
        } else {
            if (DEBUG) {
                Log.d(TAG, "Warning: tried to commit empty log unit.");
            }
        }
    }

    private static final LogStatement LOGSTATEMENT_UNCOMMIT_CURRENT_LOGUNIT =
            new LogStatement("UncommitCurrentLogUnit", false, false);
    public void uncommitCurrentLogUnit(final String expectedWord,
            final boolean dumpCurrentLogUnit) {
        // The user has deleted this word and returned to the previous.  Check that the word in the
        // logUnit matches the expected word.  If so, restore the last log unit committed to be the
        // current logUnit.  I.e., pull out the last LogUnit from all the LogBuffers, and make
        // restore it to mCurrentLogUnit so the new edits are captured with the word.  Optionally
        // dump the contents of mCurrentLogUnit (useful if they contain deletions of the next word
        // that should not be reported to protect user privacy)
        //
        // Note that we don't use mLastLogUnit here, because it only goes one word back and is only
        // needed for reverts, which only happen one back.
        if (mMainLogBuffer == null) {
            return;
        }
        final LogUnit oldLogUnit = mMainLogBuffer.peekLastLogUnit();

        // Check that expected word matches.
        if (oldLogUnit != null) {
            final String oldLogUnitWord = oldLogUnit.getWord();
            if (!oldLogUnitWord.equals(expectedWord)) {
                return;
            }
        }

        // Uncommit, merging if necessary.
        mMainLogBuffer.unshiftIn();
        if (oldLogUnit != null && !dumpCurrentLogUnit) {
            oldLogUnit.append(mCurrentLogUnit);
            mSavedDownEventTime = Long.MAX_VALUE;
        }
        if (oldLogUnit == null) {
            mCurrentLogUnit = new LogUnit();
        } else {
            mCurrentLogUnit = oldLogUnit;
        }
        if (mFeedbackLogBuffer != null) {
            mFeedbackLogBuffer.unshiftIn();
        }
        enqueueEvent(LOGSTATEMENT_UNCOMMIT_CURRENT_LOGUNIT);
        if (DEBUG) {
            Log.d(TAG, "uncommitCurrentLogUnit (dump=" + dumpCurrentLogUnit + ") back to "
                    + (mCurrentLogUnit.hasWord() ? ": '" + mCurrentLogUnit.getWord() + "'" : ""));
        }
    }

    private static final LogStatement LOGSTATEMENT_LOG_SEGMENT_OPENING =
            new LogStatement("logSegmentStart", false, false, "isIncludingPrivateData");
    private static final LogStatement LOGSTATEMENT_LOG_SEGMENT_CLOSING =
            new LogStatement("logSegmentEnd", false, false);
    /* package for test */ void publishLogBuffer(final LogBuffer logBuffer,
            final ResearchLog researchLog, final boolean isIncludingPrivateData) {
        final LogUnit openingLogUnit = new LogUnit();
        if (logBuffer.isEmpty()) return;
        openingLogUnit.addLogStatement(LOGSTATEMENT_LOG_SEGMENT_OPENING, SystemClock.uptimeMillis(),
                isIncludingPrivateData);
        researchLog.publish(openingLogUnit, true /* isIncludingPrivateData */);
        LogUnit logUnit;
        int numWordsToPublish = MainLogBuffer.N_GRAM_SIZE;
        while ((logUnit = logBuffer.shiftOut()) != null && numWordsToPublish > 0) {
            if (DEBUG) {
                Log.d(TAG, "publishLogBuffer: " + (logUnit.hasWord() ? logUnit.getWord()
                        : "<wordless>"));
            }
            researchLog.publish(logUnit, isIncludingPrivateData);
            if (logUnit.getWord() != null) {
                numWordsToPublish--;
            }
        }
        final LogUnit closingLogUnit = new LogUnit();
        closingLogUnit.addLogStatement(LOGSTATEMENT_LOG_SEGMENT_CLOSING,
                SystemClock.uptimeMillis());
        researchLog.publish(closingLogUnit, true /* isIncludingPrivateData */);
    }

    public static boolean hasLetters(final String word) {
        final int length = word.length();
        for (int i = 0; i < length; i = word.offsetByCodePoints(i, 1)) {
            final int codePoint = word.codePointAt(i);
            if (Character.isLetter(codePoint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Commit the portion of mCurrentLogUnit before maxTime as a worded logUnit.
     *
     * After this operation completes, mCurrentLogUnit will hold any logStatements that happened
     * after maxTime.
     */
    /* package for test */ void commitCurrentLogUnitAsWord(final String word, final long maxTime,
            final boolean isBatchMode) {
        if (word == null) {
            return;
        }
        final Dictionary dictionary = getDictionary();
        if (word.length() > 0 && hasLetters(word)) {
            mCurrentLogUnit.setWord(word);
            final boolean isDictionaryWord = dictionary != null
                    && dictionary.isValidWord(word);
            mStatistics.recordWordEntered(isDictionaryWord);
        }
        final LogUnit newLogUnit = mCurrentLogUnit.splitByTime(maxTime);
        enqueueCommitText(word, isBatchMode);
        commitCurrentLogUnit();
        mCurrentLogUnit = newLogUnit;
    }

    /**
     * Record the time of a MotionEvent.ACTION_DOWN.
     *
     * Warning: Not thread safe.  Only call from the main thread.
     */
    private void setSavedDownEventTime(final long time) {
        mSavedDownEventTime = time;
    }

    public void onWordFinished(final String word, final boolean isBatchMode) {
        commitCurrentLogUnitAsWord(word, mSavedDownEventTime, isBatchMode);
        mSavedDownEventTime = Long.MAX_VALUE;
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
        final Dictionary dictionary = getDictionary();
        if (dictionary == null) {
            return WORD_REPLACEMENT_STRING;
        }
        if (dictionary.isValidWord(word)) {
            return word;
        }
        return WORD_REPLACEMENT_STRING;
    }

    // Specific logging methods follow below.  The comments for each logging method should
    // indicate what specific method is logged, and how to trigger it from the user interface.
    //
    // Logging methods can be generally classified into two flavors, "UserAction", which should
    // correspond closely to an event that is sensed by the IME, and is usually generated
    // directly by the user, and "SystemResponse" which corresponds to an event that the IME
    // generates, often after much processing of user input.  SystemResponses should correspond
    // closely to user-visible events.
    // TODO: Consider exposing the UserAction classification in the log output.

    /**
     * Log a call to LatinIME.onStartInputViewInternal().
     *
     * UserAction: called each time the keyboard is opened up.
     */
    private static final LogStatement LOGSTATEMENT_LATIN_IME_ON_START_INPUT_VIEW_INTERNAL =
            new LogStatement("LatinImeOnStartInputViewInternal", false, false, "uuid",
                    "packageName", "inputType", "imeOptions", "fieldId", "display", "model",
                    "prefs", "versionCode", "versionName", "outputFormatVersion", "logEverything",
                    "isExperimentalDebug");
    public static void latinIME_onStartInputViewInternal(final EditorInfo editorInfo,
            final SharedPreferences prefs) {
        final ResearchLogger researchLogger = getInstance();
        if (editorInfo != null) {
            final boolean isPassword = InputTypeUtils.isPasswordInputType(editorInfo.inputType)
                    || InputTypeUtils.isVisiblePasswordInputType(editorInfo.inputType);
            getInstance().setIsPasswordView(isPassword);
            researchLogger.start();
            final Context context = researchLogger.mLatinIME;
            try {
                final PackageInfo packageInfo;
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(),
                        0);
                final Integer versionCode = packageInfo.versionCode;
                final String versionName = packageInfo.versionName;
                researchLogger.enqueueEvent(LOGSTATEMENT_LATIN_IME_ON_START_INPUT_VIEW_INTERNAL,
                        researchLogger.mUUIDString, editorInfo.packageName,
                        Integer.toHexString(editorInfo.inputType),
                        Integer.toHexString(editorInfo.imeOptions), editorInfo.fieldId,
                        Build.DISPLAY, Build.MODEL, prefs, versionCode, versionName,
                        OUTPUT_FORMAT_VERSION, IS_LOGGING_EVERYTHING,
                        ProductionFlag.IS_EXPERIMENTAL_DEBUG);
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void latinIME_onFinishInputViewInternal() {
        logStatistics();
        stop();
    }

    /**
     * Log a change in preferences.
     *
     * UserAction: called when the user changes the settings.
     */
    private static final LogStatement LOGSTATEMENT_PREFS_CHANGED =
            new LogStatement("PrefsChanged", false, false, "prefs");
    public static void prefsChanged(final SharedPreferences prefs) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_PREFS_CHANGED, prefs);
    }

    /**
     * Log a call to MainKeyboardView.processMotionEvent().
     *
     * UserAction: called when the user puts their finger onto the screen (ACTION_DOWN).
     *
     */
    private static final LogStatement LOGSTATEMENT_MAIN_KEYBOARD_VIEW_PROCESS_MOTION_EVENT =
            new LogStatement("MotionEvent", true, false, "action", "MotionEvent");
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
            final ResearchLogger researchLogger = getInstance();
            researchLogger.enqueueEvent(LOGSTATEMENT_MAIN_KEYBOARD_VIEW_PROCESS_MOTION_EVENT,
                    actionString, MotionEvent.obtain(me));
            if (action == MotionEvent.ACTION_DOWN) {
                // Subtract 1 from eventTime so the down event is included in the later
                // LogUnit, not the earlier (the test is for inequality).
                researchLogger.setSavedDownEventTime(eventTime - 1);
            }
        }
    }

    /**
     * Log a call to LatinIME.onCodeInput().
     *
     * SystemResponse: The main processing step for entering text.  Called when the user performs a
     * tap, a flick, a long press, releases a gesture, or taps a punctuation suggestion.
     */
    private static final LogStatement LOGSTATEMENT_LATIN_IME_ON_CODE_INPUT =
            new LogStatement("LatinImeOnCodeInput", true, false, "code", "x", "y");
    public static void latinIME_onCodeInput(final int code, final int x, final int y) {
        final long time = SystemClock.uptimeMillis();
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_LATIN_IME_ON_CODE_INPUT,
                Constants.printableCode(scrubDigitFromCodePoint(code)), x, y);
        if (Character.isDigit(code)) {
            researchLogger.setCurrentLogUnitContainsDigitFlag();
        }
        researchLogger.mStatistics.recordChar(code, time);
    }
    /**
     * Log a call to LatinIME.onDisplayCompletions().
     *
     * SystemResponse: The IME has displayed application-specific completions.  They may show up
     * in the suggestion strip, such as a landscape phone.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_ONDISPLAYCOMPLETIONS =
            new LogStatement("LatinIMEOnDisplayCompletions", true, true,
                    "applicationSpecifiedCompletions");
    public static void latinIME_onDisplayCompletions(
            final CompletionInfo[] applicationSpecifiedCompletions) {
        // Note; passing an array as a single element in a vararg list.  Must create a new
        // dummy array around it or it will get expanded.
        getInstance().enqueueEvent(LOGSTATEMENT_LATINIME_ONDISPLAYCOMPLETIONS,
                new Object[] { applicationSpecifiedCompletions });
    }

    public static boolean getAndClearLatinIMEExpectingUpdateSelection() {
        boolean returnValue = sLatinIMEExpectingUpdateSelection;
        sLatinIMEExpectingUpdateSelection = false;
        return returnValue;
    }

    /**
     * Log a call to LatinIME.onWindowHidden().
     *
     * UserAction: The user has performed an action that has caused the IME to be closed.  They may
     * have focused on something other than a text field, or explicitly closed it.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_ONWINDOWHIDDEN =
            new LogStatement("LatinIMEOnWindowHidden", false, false, "isTextTruncated", "text");
    public static void latinIME_onWindowHidden(final int savedSelectionStart,
            final int savedSelectionEnd, final InputConnection ic) {
        if (ic != null) {
            final boolean isTextTruncated;
            final String text;
            if (LOG_FULL_TEXTVIEW_CONTENTS) {
                // Capture the TextView contents.  This will trigger onUpdateSelection(), so we
                // set sLatinIMEExpectingUpdateSelection so that when onUpdateSelection() is called,
                // it can tell that it was generated by the logging code, and not by the user, and
                // therefore keep user-visible state as is.
                ic.beginBatchEdit();
                ic.performContextMenuAction(android.R.id.selectAll);
                CharSequence charSequence = ic.getSelectedText(0);
                if (savedSelectionStart != -1 && savedSelectionEnd != -1) {
                    ic.setSelection(savedSelectionStart, savedSelectionEnd);
                }
                ic.endBatchEdit();
                sLatinIMEExpectingUpdateSelection = true;
                if (TextUtils.isEmpty(charSequence)) {
                    isTextTruncated = false;
                    text = "";
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
                        isTextTruncated = true;
                        text = truncatedCharSequence.toString();
                    } else {
                        isTextTruncated = false;
                        text = charSequence.toString();
                    }
                }
            } else {
                isTextTruncated = true;
                text = "";
            }
            final ResearchLogger researchLogger = getInstance();
            // Assume that OUTPUT_ENTIRE_BUFFER is only true when we don't care about privacy (e.g.
            // during a live user test), so the normal isPotentiallyPrivate and
            // isPotentiallyRevealing flags do not apply
            researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_ONWINDOWHIDDEN, isTextTruncated,
                    text);
            researchLogger.commitCurrentLogUnit();
            getInstance().stop();
        }
    }

    /**
     * Log a call to LatinIME.onUpdateSelection().
     *
     * UserAction/SystemResponse: The user has moved the cursor or selection.  This function may
     * be called, however, when the system has moved the cursor, say by inserting a character.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_ONUPDATESELECTION =
            new LogStatement("LatinIMEOnUpdateSelection", true, false, "lastSelectionStart",
                    "lastSelectionEnd", "oldSelStart", "oldSelEnd", "newSelStart", "newSelEnd",
                    "composingSpanStart", "composingSpanEnd", "expectingUpdateSelection",
                    "expectingUpdateSelectionFromLogger", "context");
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
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_ONUPDATESELECTION, lastSelectionStart,
                lastSelectionEnd, oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                composingSpanStart, composingSpanEnd, expectingUpdateSelection,
                expectingUpdateSelectionFromLogger, scrubbedWord);
    }

    /**
     * Log a call to LatinIME.onTextInput().
     *
     * SystemResponse: Raw text is added to the TextView.
     */
    public static void latinIME_onTextInput(final String text, final boolean isBatchMode) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.commitCurrentLogUnitAsWord(text, Long.MAX_VALUE, isBatchMode);
    }

    /**
     * Log a call to LatinIME.pickSuggestionManually().
     *
     * UserAction: The user has chosen a specific word from the suggestion strip.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_PICKSUGGESTIONMANUALLY =
            new LogStatement("LatinIMEPickSuggestionManually", true, false, "replacedWord", "index",
                    "suggestion", "x", "y");
    public static void latinIME_pickSuggestionManually(final String replacedWord,
            final int index, final String suggestion, final boolean isBatchMode) {
        final String scrubbedWord = scrubDigitsFromString(suggestion);
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_PICKSUGGESTIONMANUALLY,
                scrubDigitsFromString(replacedWord), index,
                suggestion == null ? null : scrubbedWord, Constants.SUGGESTION_STRIP_COORDINATE,
                Constants.SUGGESTION_STRIP_COORDINATE);
        researchLogger.commitCurrentLogUnitAsWord(scrubbedWord, Long.MAX_VALUE, isBatchMode);
        researchLogger.mStatistics.recordManualSuggestion(SystemClock.uptimeMillis());
    }

    /**
     * Log a call to LatinIME.punctuationSuggestion().
     *
     * UserAction: The user has chosen punctuation from the punctuation suggestion strip.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_PUNCTUATIONSUGGESTION =
            new LogStatement("LatinIMEPunctuationSuggestion", false, false, "index", "suggestion",
                    "x", "y", "isPrediction");
    public static void latinIME_punctuationSuggestion(final int index, final String suggestion,
            final boolean isBatchMode, final boolean isPrediction) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_PUNCTUATIONSUGGESTION, index, suggestion,
                Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                isPrediction);
        researchLogger.commitCurrentLogUnitAsWord(suggestion, Long.MAX_VALUE, isBatchMode);
    }

    /**
     * Log a call to LatinIME.sendKeyCodePoint().
     *
     * SystemResponse: The IME is inserting text into the TextView for numbers, fixed strings, or
     * some other unusual mechanism.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_SENDKEYCODEPOINT =
            new LogStatement("LatinIMESendKeyCodePoint", true, false, "code");
    public static void latinIME_sendKeyCodePoint(final int code) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_SENDKEYCODEPOINT,
                Constants.printableCode(scrubDigitFromCodePoint(code)));
        if (Character.isDigit(code)) {
            researchLogger.setCurrentLogUnitContainsDigitFlag();
        }
    }

    /**
     * Log a call to LatinIME.promotePhantomSpace().
     *
     * SystemResponse: The IME is inserting a real space in place of a phantom space.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_PROMOTEPHANTOMSPACE =
            new LogStatement("LatinIMEPromotPhantomSpace", false, false);
    public static void latinIME_promotePhantomSpace() {
        final ResearchLogger researchLogger = getInstance();
        final LogUnit logUnit;
        if (researchLogger.mMainLogBuffer == null) {
            logUnit = researchLogger.mCurrentLogUnit;
        } else {
            logUnit = researchLogger.mMainLogBuffer.peekLastLogUnit();
        }
        researchLogger.enqueueEvent(logUnit, LOGSTATEMENT_LATINIME_PROMOTEPHANTOMSPACE);
    }

    /**
     * Log a call to LatinIME.swapSwapperAndSpace().
     *
     * SystemResponse: A symbol has been swapped with a space character.  E.g. punctuation may swap
     * if a soft space is inserted after a word.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_SWAPSWAPPERANDSPACE =
            new LogStatement("LatinIMESwapSwapperAndSpace", false, false, "originalCharacters",
                    "charactersAfterSwap");
    public static void latinIME_swapSwapperAndSpace(final CharSequence originalCharacters,
            final String charactersAfterSwap) {
        final ResearchLogger researchLogger = getInstance();
        final LogUnit logUnit;
        if (researchLogger.mMainLogBuffer == null) {
            logUnit = null;
        } else {
            logUnit = researchLogger.mMainLogBuffer.peekLastLogUnit();
        }
        if (logUnit != null) {
            researchLogger.enqueueEvent(logUnit, LOGSTATEMENT_LATINIME_SWAPSWAPPERANDSPACE,
                    originalCharacters, charactersAfterSwap);
        }
    }

    /**
     * Log a call to LatinIME.maybeDoubleSpacePeriod().
     *
     * SystemResponse: Two spaces have been replaced by period space.
     */
    public static void latinIME_maybeDoubleSpacePeriod(final String text,
            final boolean isBatchMode) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.commitCurrentLogUnitAsWord(text, Long.MAX_VALUE, isBatchMode);
    }

    /**
     * Log a call to MainKeyboardView.onLongPress().
     *
     * UserAction: The user has performed a long-press on a key.
     */
    private static final LogStatement LOGSTATEMENT_MAINKEYBOARDVIEW_ONLONGPRESS =
            new LogStatement("MainKeyboardViewOnLongPress", false, false);
    public static void mainKeyboardView_onLongPress() {
        getInstance().enqueueEvent(LOGSTATEMENT_MAINKEYBOARDVIEW_ONLONGPRESS);
    }

    /**
     * Log a call to MainKeyboardView.setKeyboard().
     *
     * SystemResponse: The IME has switched to a new keyboard (e.g. French, English).
     * This is typically called right after LatinIME.onStartInputViewInternal (when starting a new
     * IME), but may happen at other times if the user explicitly requests a keyboard change.
     */
    private static final LogStatement LOGSTATEMENT_MAINKEYBOARDVIEW_SETKEYBOARD =
            new LogStatement("MainKeyboardViewSetKeyboard", false, false, "elementId", "locale",
                    "orientation", "width", "modeName", "action", "navigateNext",
                    "navigatePrevious", "clobberSettingsKey", "passwordInput", "shortcutKeyEnabled",
                    "hasShortcutKey", "languageSwitchKeyEnabled", "isMultiLine", "tw", "th",
                    "keys");
    public static void mainKeyboardView_setKeyboard(final Keyboard keyboard) {
        final KeyboardId kid = keyboard.mId;
        final boolean isPasswordView = kid.passwordInput();
        final ResearchLogger researchLogger = getInstance();
        researchLogger.setIsPasswordView(isPasswordView);
        researchLogger.enqueueEvent(LOGSTATEMENT_MAINKEYBOARDVIEW_SETKEYBOARD,
                KeyboardId.elementIdToName(kid.mElementId),
                kid.mLocale + ":" + kid.mSubtype.getExtraValueOf(KEYBOARD_LAYOUT_SET),
                kid.mOrientation, kid.mWidth, KeyboardId.modeName(kid.mMode), kid.imeAction(),
                kid.navigateNext(), kid.navigatePrevious(), kid.mClobberSettingsKey,
                isPasswordView, kid.mShortcutKeyEnabled, kid.mHasShortcutKey,
                kid.mLanguageSwitchKeyEnabled, kid.isMultiLine(), keyboard.mOccupiedWidth,
                keyboard.mOccupiedHeight, keyboard.mKeys);
    }

    /**
     * Log a call to LatinIME.revertCommit().
     *
     * SystemResponse: The IME has reverted commited text.  This happens when the user enters
     * a word, commits it by pressing space or punctuation, and then reverts the commit by hitting
     * backspace.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_REVERTCOMMIT =
            new LogStatement("LatinIMERevertCommit", true, false, "committedWord",
                    "originallyTypedWord", "separatorString");
    public static void latinIME_revertCommit(final String committedWord,
            final String originallyTypedWord, final boolean isBatchMode,
            final String separatorString) {
        final ResearchLogger researchLogger = getInstance();
        // TODO: Verify that mCurrentLogUnit has been restored and contains the reverted word.
        final LogUnit logUnit;
        if (researchLogger.mMainLogBuffer == null) {
            logUnit = null;
        } else {
            logUnit = researchLogger.mMainLogBuffer.peekLastLogUnit();
        }
        if (originallyTypedWord.length() > 0 && hasLetters(originallyTypedWord)) {
            if (logUnit != null) {
                logUnit.setWord(originallyTypedWord);
            }
        }
        researchLogger.enqueueEvent(logUnit != null ? logUnit : researchLogger.mCurrentLogUnit,
                LOGSTATEMENT_LATINIME_REVERTCOMMIT, committedWord, originallyTypedWord,
                separatorString);
        researchLogger.mStatistics.recordRevertCommit(SystemClock.uptimeMillis());
        researchLogger.commitCurrentLogUnitAsWord(originallyTypedWord, Long.MAX_VALUE, isBatchMode);
    }

    /**
     * Log a call to PointerTracker.callListenerOnCancelInput().
     *
     * UserAction: The user has canceled the input, e.g., by pressing down, but then removing
     * outside the keyboard area.
     * TODO: Verify
     */
    private static final LogStatement LOGSTATEMENT_POINTERTRACKER_CALLLISTENERONCANCELINPUT =
            new LogStatement("PointerTrackerCallListenerOnCancelInput", false, false);
    public static void pointerTracker_callListenerOnCancelInput() {
        getInstance().enqueueEvent(LOGSTATEMENT_POINTERTRACKER_CALLLISTENERONCANCELINPUT);
    }

    /**
     * Log a call to PointerTracker.callListenerOnCodeInput().
     *
     * SystemResponse: The user has entered a key through the normal tapping mechanism.
     * LatinIME.onCodeInput will also be called.
     */
    private static final LogStatement LOGSTATEMENT_POINTERTRACKER_CALLLISTENERONCODEINPUT =
            new LogStatement("PointerTrackerCallListenerOnCodeInput", true, false, "code",
                    "outputText", "x", "y", "ignoreModifierKey", "altersCode", "isEnabled");
    public static void pointerTracker_callListenerOnCodeInput(final Key key, final int x,
            final int y, final boolean ignoreModifierKey, final boolean altersCode,
            final int code) {
        if (key != null) {
            String outputText = key.getOutputText();
            getInstance().enqueueEvent(LOGSTATEMENT_POINTERTRACKER_CALLLISTENERONCODEINPUT,
                    Constants.printableCode(scrubDigitFromCodePoint(code)),
                    outputText == null ? null : scrubDigitsFromString(outputText.toString()),
                    x, y, ignoreModifierKey, altersCode, key.isEnabled());
        }
    }

    /**
     * Log a call to PointerTracker.callListenerCallListenerOnRelease().
     *
     * UserAction: The user has released their finger or thumb from the screen.
     */
    private static final LogStatement LOGSTATEMENT_POINTERTRACKER_CALLLISTENERONRELEASE =
            new LogStatement("PointerTrackerCallListenerOnRelease", true, false, "code",
                    "withSliding", "ignoreModifierKey", "isEnabled");
    public static void pointerTracker_callListenerOnRelease(final Key key, final int primaryCode,
            final boolean withSliding, final boolean ignoreModifierKey) {
        if (key != null) {
            getInstance().enqueueEvent(LOGSTATEMENT_POINTERTRACKER_CALLLISTENERONRELEASE,
                    Constants.printableCode(scrubDigitFromCodePoint(primaryCode)), withSliding,
                    ignoreModifierKey, key.isEnabled());
        }
    }

    /**
     * Log a call to PointerTracker.onDownEvent().
     *
     * UserAction: The user has pressed down on a key.
     * TODO: Differentiate with LatinIME.processMotionEvent.
     */
    private static final LogStatement LOGSTATEMENT_POINTERTRACKER_ONDOWNEVENT =
            new LogStatement("PointerTrackerOnDownEvent", true, false, "deltaT", "distanceSquared");
    public static void pointerTracker_onDownEvent(long deltaT, int distanceSquared) {
        getInstance().enqueueEvent(LOGSTATEMENT_POINTERTRACKER_ONDOWNEVENT, deltaT,
                distanceSquared);
    }

    /**
     * Log a call to PointerTracker.onMoveEvent().
     *
     * UserAction: The user has moved their finger while pressing on the screen.
     * TODO: Differentiate with LatinIME.processMotionEvent().
     */
    private static final LogStatement LOGSTATEMENT_POINTERTRACKER_ONMOVEEVENT =
            new LogStatement("PointerTrackerOnMoveEvent", true, false, "x", "y", "lastX", "lastY");
    public static void pointerTracker_onMoveEvent(final int x, final int y, final int lastX,
            final int lastY) {
        getInstance().enqueueEvent(LOGSTATEMENT_POINTERTRACKER_ONMOVEEVENT, x, y, lastX, lastY);
    }

    /**
     * Log a call to RichInputConnection.commitCompletion().
     *
     * SystemResponse: The IME has committed a completion.  A completion is an application-
     * specific suggestion that is presented in a pop-up menu in the TextView.
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTION_COMMITCOMPLETION =
            new LogStatement("RichInputConnectionCommitCompletion", true, false, "completionInfo");
    public static void richInputConnection_commitCompletion(final CompletionInfo completionInfo) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_COMMITCOMPLETION,
                completionInfo);
    }

    /**
     * Log a call to RichInputConnection.revertDoubleSpacePeriod().
     *
     * SystemResponse: The IME has reverted ". ", which had previously replaced two typed spaces.
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTION_REVERTDOUBLESPACEPERIOD =
            new LogStatement("RichInputConnectionRevertDoubleSpacePeriod", false, false);
    public static void richInputConnection_revertDoubleSpacePeriod() {
        getInstance().enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_REVERTDOUBLESPACEPERIOD);
    }

    /**
     * Log a call to RichInputConnection.revertSwapPunctuation().
     *
     * SystemResponse: The IME has reverted a punctuation swap.
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTION_REVERTSWAPPUNCTUATION =
            new LogStatement("RichInputConnectionRevertSwapPunctuation", false, false);
    public static void richInputConnection_revertSwapPunctuation() {
        getInstance().enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_REVERTSWAPPUNCTUATION);
    }

    /**
     * Log a call to LatinIME.commitCurrentAutoCorrection().
     *
     * SystemResponse: The IME has committed an auto-correction.  An auto-correction changes the raw
     * text input to another word that the user more likely desired to type.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_COMMITCURRENTAUTOCORRECTION =
            new LogStatement("LatinIMECommitCurrentAutoCorrection", true, true, "typedWord",
                    "autoCorrection", "separatorString");
    public static void latinIme_commitCurrentAutoCorrection(final String typedWord,
            final String autoCorrection, final String separatorString, final boolean isBatchMode) {
        final String scrubbedTypedWord = scrubDigitsFromString(typedWord);
        final String scrubbedAutoCorrection = scrubDigitsFromString(autoCorrection);
        final ResearchLogger researchLogger = getInstance();
        researchLogger.commitCurrentLogUnitAsWord(scrubbedAutoCorrection, Long.MAX_VALUE,
                isBatchMode);

        // Add the autocorrection logStatement at the end of the logUnit for the committed word.
        // We have to do this after calling commitCurrentLogUnitAsWord, because it may split the
        // current logUnit, and then we have to peek to get the logUnit reference back.
        final LogUnit logUnit = researchLogger.mMainLogBuffer.peekLastLogUnit();
        // TODO: Add test to confirm that the commitCurrentAutoCorrection log statement should
        // always be added to logUnit (if non-null) and not mCurrentLogUnit.
        researchLogger.enqueueEvent(logUnit, LOGSTATEMENT_LATINIME_COMMITCURRENTAUTOCORRECTION,
                scrubbedTypedWord, scrubbedAutoCorrection, separatorString);
    }

    private boolean isExpectingCommitText = false;
    /**
     * Log a call to (UnknownClass).commitPartialText
     *
     * SystemResponse: The IME is committing part of a word.  This happens if a space is
     * automatically inserted to split a single typed string into two or more words.
     */
    // TODO: This method is currently unused.  Find where it should be called from in the IME and
    // add invocations.
    private static final LogStatement LOGSTATEMENT_COMMIT_PARTIAL_TEXT =
            new LogStatement("CommitPartialText", true, false, "newCursorPosition");
    public static void commitPartialText(final String committedWord,
            final long lastTimestampOfWordData, final boolean isBatchMode) {
        final ResearchLogger researchLogger = getInstance();
        final String scrubbedWord = scrubDigitsFromString(committedWord);
        researchLogger.enqueueEvent(LOGSTATEMENT_COMMIT_PARTIAL_TEXT);
        researchLogger.commitCurrentLogUnitAsWord(scrubbedWord, lastTimestampOfWordData,
                isBatchMode);
    }

    /**
     * Log a call to RichInputConnection.commitText().
     *
     * SystemResponse: The IME is committing text.  This happens after the user has typed a word
     * and then a space or punctuation key.
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTIONCOMMITTEXT =
            new LogStatement("RichInputConnectionCommitText", true, false, "newCursorPosition");
    public static void richInputConnection_commitText(final String committedWord,
            final int newCursorPosition, final boolean isBatchMode) {
        final ResearchLogger researchLogger = getInstance();
        final String scrubbedWord = scrubDigitsFromString(committedWord);
        if (!researchLogger.isExpectingCommitText) {
            researchLogger.enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTIONCOMMITTEXT,
                    newCursorPosition);
            researchLogger.commitCurrentLogUnitAsWord(scrubbedWord, Long.MAX_VALUE, isBatchMode);
        }
        researchLogger.isExpectingCommitText = false;
    }

    /**
     * Shared event for logging committed text.
     */
    private static final LogStatement LOGSTATEMENT_COMMITTEXT =
            new LogStatement("CommitText", true, false, "committedText", "isBatchMode");
    private void enqueueCommitText(final String word, final boolean isBatchMode) {
        enqueueEvent(LOGSTATEMENT_COMMITTEXT, word, isBatchMode);
    }

    /**
     * Log a call to RichInputConnection.deleteSurroundingText().
     *
     * SystemResponse: The IME has deleted text.
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTION_DELETESURROUNDINGTEXT =
            new LogStatement("RichInputConnectionDeleteSurroundingText", true, false,
                    "beforeLength", "afterLength");
    public static void richInputConnection_deleteSurroundingText(final int beforeLength,
            final int afterLength) {
        getInstance().enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_DELETESURROUNDINGTEXT,
                beforeLength, afterLength);
    }

    /**
     * Log a call to RichInputConnection.finishComposingText().
     *
     * SystemResponse: The IME has left the composing text as-is.
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTION_FINISHCOMPOSINGTEXT =
            new LogStatement("RichInputConnectionFinishComposingText", false, false);
    public static void richInputConnection_finishComposingText() {
        getInstance().enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_FINISHCOMPOSINGTEXT);
    }

    /**
     * Log a call to RichInputConnection.performEditorAction().
     *
     * SystemResponse: The IME is invoking an action specific to the editor.
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTION_PERFORMEDITORACTION =
            new LogStatement("RichInputConnectionPerformEditorAction", false, false,
                    "imeActionId");
    public static void richInputConnection_performEditorAction(final int imeActionId) {
        getInstance().enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_PERFORMEDITORACTION,
                imeActionId);
    }

    /**
     * Log a call to RichInputConnection.sendKeyEvent().
     *
     * SystemResponse: The IME is telling the TextView that a key is being pressed through an
     * alternate channel.
     * TODO: only for hardware keys?
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTION_SENDKEYEVENT =
            new LogStatement("RichInputConnectionSendKeyEvent", true, false, "eventTime", "action",
                    "code");
    public static void richInputConnection_sendKeyEvent(final KeyEvent keyEvent) {
        getInstance().enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_SENDKEYEVENT,
                keyEvent.getEventTime(), keyEvent.getAction(), keyEvent.getKeyCode());
    }

    /**
     * Log a call to RichInputConnection.setComposingText().
     *
     * SystemResponse: The IME is setting the composing text.  Happens each time a character is
     * entered.
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTION_SETCOMPOSINGTEXT =
            new LogStatement("RichInputConnectionSetComposingText", true, true, "text",
                    "newCursorPosition");
    public static void richInputConnection_setComposingText(final CharSequence text,
            final int newCursorPosition) {
        if (text == null) {
            throw new RuntimeException("setComposingText is null");
        }
        getInstance().enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_SETCOMPOSINGTEXT, text,
                newCursorPosition);
    }

    /**
     * Log a call to RichInputConnection.setSelection().
     *
     * SystemResponse: The IME is requesting that the selection change.  User-initiated selection-
     * change requests do not go through this method -- it's only when the system wants to change
     * the selection.
     */
    private static final LogStatement LOGSTATEMENT_RICHINPUTCONNECTION_SETSELECTION =
            new LogStatement("RichInputConnectionSetSelection", true, false, "from", "to");
    public static void richInputConnection_setSelection(final int from, final int to) {
        getInstance().enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_SETSELECTION, from, to);
    }

    /**
     * Log a call to SuddenJumpingTouchEventHandler.onTouchEvent().
     *
     * SystemResponse: The IME has filtered input events in case of an erroneous sensor reading.
     */
    private static final LogStatement LOGSTATEMENT_SUDDENJUMPINGTOUCHEVENTHANDLER_ONTOUCHEVENT =
            new LogStatement("SuddenJumpingTouchEventHandlerOnTouchEvent", true, false,
                    "motionEvent");
    public static void suddenJumpingTouchEventHandler_onTouchEvent(final MotionEvent me) {
        if (me != null) {
            getInstance().enqueueEvent(LOGSTATEMENT_SUDDENJUMPINGTOUCHEVENTHANDLER_ONTOUCHEVENT,
                    me.toString());
        }
    }

    /**
     * Log a call to SuggestionsView.setSuggestions().
     *
     * SystemResponse: The IME is setting the suggestions in the suggestion strip.
     */
    private static final LogStatement LOGSTATEMENT_SUGGESTIONSTRIPVIEW_SETSUGGESTIONS =
            new LogStatement("SuggestionStripViewSetSuggestions", true, true, "suggestedWords");
    public static void suggestionStripView_setSuggestions(final SuggestedWords suggestedWords) {
        if (suggestedWords != null) {
            getInstance().enqueueEvent(LOGSTATEMENT_SUGGESTIONSTRIPVIEW_SETSUGGESTIONS,
                    suggestedWords);
        }
    }

    /**
     * The user has indicated a particular point in the log that is of interest.
     *
     * UserAction: From direct menu invocation.
     */
    private static final LogStatement LOGSTATEMENT_USER_TIMESTAMP =
            new LogStatement("UserTimestamp", false, false);
    public void userTimestamp() {
        getInstance().enqueueEvent(LOGSTATEMENT_USER_TIMESTAMP);
    }

    /**
     * Log a call to LatinIME.onEndBatchInput().
     *
     * SystemResponse: The system has completed a gesture.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_ONENDBATCHINPUT =
            new LogStatement("LatinIMEOnEndBatchInput", true, false, "enteredText",
                    "enteredWordPos");
    public static void latinIME_onEndBatchInput(final CharSequence enteredText,
            final int enteredWordPos) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_ONENDBATCHINPUT, enteredText,
                enteredWordPos);
        researchLogger.mStatistics.recordGestureInput(enteredText.length(),
                SystemClock.uptimeMillis());
    }

    /**
     * Log a call to LatinIME.handleBackspace() that is not a batch delete.
     *
     * UserInput: The user is deleting one or more characters by hitting the backspace key once.
     * The covers single character deletes as well as deleting selections.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_HANDLEBACKSPACE =
            new LogStatement("LatinIMEHandleBackspace", true, false, "numCharacters");
    public static void latinIME_handleBackspace(final int numCharacters) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_HANDLEBACKSPACE, numCharacters);
    }

    /**
     * Log a call to LatinIME.handleBackspace() that is a batch delete.
     *
     * UserInput: The user is deleting a gestured word by hitting the backspace key once.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_HANDLEBACKSPACE_BATCH =
            new LogStatement("LatinIMEHandleBackspaceBatch", true, false, "deletedText",
                    "numCharacters");
    public static void latinIME_handleBackspace_batch(final CharSequence deletedText,
            final int numCharacters) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_HANDLEBACKSPACE_BATCH, deletedText,
                numCharacters);
        researchLogger.mStatistics.recordGestureDelete(deletedText.length(),
                SystemClock.uptimeMillis());
    }

    /**
     * Log a long interval between user operation.
     *
     * UserInput: The user has not done anything for a while.
     */
    private static final LogStatement LOGSTATEMENT_ONUSERPAUSE = new LogStatement("OnUserPause",
            false, false, "intervalInMs");
    public static void onUserPause(final long interval) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_ONUSERPAUSE, interval);
    }

    /**
     * Record the current time in case the LogUnit is later split.
     *
     * If the current logUnitis split, then tapping, motion events, etc. before this time should
     * be assigned to one LogUnit, and events after this time should go into the following LogUnit.
     */
    public static void recordTimeForLogUnitSplit() {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.setSavedDownEventTime(SystemClock.uptimeMillis());
        researchLogger.mSavedDownEventTime = Long.MAX_VALUE;
    }

    /**
     * Log statistics.
     *
     * ContextualData, recorded at the end of a session.
     */
    private static final LogStatement LOGSTATEMENT_STATISTICS =
            new LogStatement("Statistics", false, false, "charCount", "letterCount", "numberCount",
                    "spaceCount", "deleteOpsCount", "wordCount", "isEmptyUponStarting",
                    "isEmptinessStateKnown", "averageTimeBetweenKeys", "averageTimeBeforeDelete",
                    "averageTimeDuringRepeatedDelete", "averageTimeAfterDelete",
                    "dictionaryWordCount", "splitWordsCount", "gestureInputCount",
                    "gestureCharsCount", "gesturesDeletedCount", "manualSuggestionsCount",
                    "revertCommitsCount");
    private static void logStatistics() {
        final ResearchLogger researchLogger = getInstance();
        final Statistics statistics = researchLogger.mStatistics;
        researchLogger.enqueueEvent(LOGSTATEMENT_STATISTICS, statistics.mCharCount,
                statistics.mLetterCount, statistics.mNumberCount, statistics.mSpaceCount,
                statistics.mDeleteKeyCount, statistics.mWordCount, statistics.mIsEmptyUponStarting,
                statistics.mIsEmptinessStateKnown, statistics.mKeyCounter.getAverageTime(),
                statistics.mBeforeDeleteKeyCounter.getAverageTime(),
                statistics.mDuringRepeatedDeleteKeysCounter.getAverageTime(),
                statistics.mAfterDeleteKeyCounter.getAverageTime(),
                statistics.mDictionaryWordCount, statistics.mSplitWordsCount,
                statistics.mGesturesInputCount, statistics.mGesturesCharsCount,
                statistics.mGesturesDeletedCount, statistics.mManualSuggestionsCount,
                statistics.mRevertCommitsCount);
    }
}
