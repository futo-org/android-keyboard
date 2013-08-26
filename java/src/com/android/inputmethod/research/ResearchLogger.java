/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.research;

import static com.android.inputmethod.latin.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputConnection;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.utils.InputTypeUtils;
import com.android.inputmethod.latin.utils.TextRange;
import com.android.inputmethod.research.MotionEventReader.ReplayData;
import com.android.inputmethod.research.ui.SplashScreen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

// TODO: Add a unit test for every "logging" method (i.e. that is called from the IME and calls
// enqueueEvent to record a LogStatement).
/**
 * Logs the use of the LatinIME keyboard.
 *
 * This class logs operations on the IME keyboard, including what the user has typed.
 * Data is stored locally in a file in app-specific storage.
 *
 * This functionality is off by default. See
 * {@link ProductionFlag#USES_DEVELOPMENT_ONLY_DIAGNOSTICS}.
 */
public class ResearchLogger implements SharedPreferences.OnSharedPreferenceChangeListener,
        SplashScreen.UserConsentListener {
    // TODO: This class has grown quite large and combines several concerns that should be
    // separated.  The following refactorings will be applied as soon as possible after adding
    // support for replaying historical events, fixing some replay bugs, adding some ui constraints
    // on the feedback dialog, and adding the survey dialog.
    // TODO: Refactor.  Move feedback screen code into separate class.
    // TODO: Refactor.  Move logging invocations into their own class.
    // TODO: Refactor.  Move currentLogUnit management into separate class.
    private static final String TAG = ResearchLogger.class.getSimpleName();
    private static final boolean DEBUG = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    private static final boolean DEBUG_REPLAY_AFTER_FEEDBACK = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    // Whether the TextView contents are logged at the end of the session.  true will disclose
    // private info.
    private static final boolean LOG_FULL_TEXTVIEW_CONTENTS = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    // Whether the feedback dialog preserves the editable text across invocations.  Should be false
    // for normal research builds so users do not have to delete the same feedback string they
    // entered earlier.  Should be true for builds internal to a development team so when the text
    // field holds a channel name, the developer does not have to re-enter it when using the
    // feedback mechanism to generate multiple tests.
    private static final boolean FEEDBACK_DIALOG_SHOULD_PRESERVE_TEXT_FIELD = false;
    /* package */ static boolean sIsLogging = false;
    private static final int OUTPUT_FORMAT_VERSION = 5;
    // Whether all words should be recorded, leaving unsampled word between bigrams.  Useful for
    // testing.
    /* package for test */ static final boolean IS_LOGGING_EVERYTHING = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    // The number of words between n-grams to omit from the log.
    private static final int NUMBER_OF_WORDS_BETWEEN_SAMPLES =
            IS_LOGGING_EVERYTHING ? 0 : (DEBUG ? 2 : 18);

    // Whether to show an indicator on the screen that logging is on.  Currently a very small red
    // dot in the lower right hand corner.  Most users should not notice it.
    private static final boolean IS_SHOWING_INDICATOR = true;
    // Change the default indicator to something very visible.  Currently two red vertical bars on
    // either side of they keyboard.
    private static final boolean IS_SHOWING_INDICATOR_CLEARLY = false ||
            (IS_LOGGING_EVERYTHING && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG);
    // FEEDBACK_WORD_BUFFER_SIZE should add 1 because it must also hold the feedback LogUnit itself.
    public static final int FEEDBACK_WORD_BUFFER_SIZE = (Integer.MAX_VALUE - 1) + 1;

    // The special output text to invoke a research feedback dialog.
    public static final String RESEARCH_KEY_OUTPUT_TEXT = ".research.";

    // constants related to specific log points
    private static final String WHITESPACE_SEPARATORS = " \t\n\r";
    private static final int MAX_INPUTVIEW_LENGTH_TO_CAPTURE = 8192; // must be >=1
    private static final String PREF_RESEARCH_SAVED_CHANNEL = "pref_research_saved_channel";

    private static final long RESEARCHLOG_CLOSE_TIMEOUT_IN_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long RESEARCHLOG_ABORT_TIMEOUT_IN_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long DURATION_BETWEEN_DIR_CLEANUP_IN_MS = TimeUnit.DAYS.toMillis(1);
    private static final long MAX_LOGFILE_AGE_IN_MS = TimeUnit.DAYS.toMillis(4);

    private static final ResearchLogger sInstance = new ResearchLogger();
    private static String sAccountType = null;
    private static String sAllowedAccountDomain = null;
    private ResearchLog mMainResearchLog; // always non-null after init() is called
    // mFeedbackLog records all events for the session, private or not (excepting
    // passwords).  It is written to permanent storage only if the user explicitly commands
    // the system to do so.
    // LogUnits are queued in the LogBuffers and published to the ResearchLogs when words are
    // complete.
    /* package for test */ MainLogBuffer mMainLogBuffer; // always non-null after init() is called
    /* package */ ResearchLog mUserRecordingLog;
    /* package */ LogBuffer mUserRecordingLogBuffer;
    private File mUserRecordingFile = null;

    private boolean mIsPasswordView = false;
    private SharedPreferences mPrefs;

    // digits entered by the user are replaced with this codepoint.
    /* package for test */ static final int DIGIT_REPLACEMENT_CODEPOINT =
            Character.codePointAt("\uE000", 0);  // U+E000 is in the "private-use area"
    // U+E001 is in the "private-use area"
    /* package for test */ static final String WORD_REPLACEMENT_STRING = "\uE001";
    protected static final int SUSPEND_DURATION_IN_MINUTES = 1;
    // set when LatinIME should ignore an onUpdateSelection() callback that
    // arises from operations in this class
    private static boolean sLatinIMEExpectingUpdateSelection = false;

    // used to check whether words are not unique
    private Suggest mSuggest;
    private MainKeyboardView mMainKeyboardView;
    // TODO: Check whether a superclass can be used instead of LatinIME.
    /* package for test */ LatinIME mLatinIME;
    private final Statistics mStatistics;
    private final MotionEventReader mMotionEventReader = new MotionEventReader();
    private final Replayer mReplayer = Replayer.getInstance();
    private ResearchLogDirectory mResearchLogDirectory;
    private SplashScreen mSplashScreen;

    private Intent mUploadNowIntent;

    /* package for test */ LogUnit mCurrentLogUnit = new LogUnit();

    // Gestured or tapped words may be committed after the gesture of the next word has started.
    // To ensure that the gesture data of the next word is not associated with the previous word,
    // thereby leaking private data, we store the time of the down event that started the second
    // gesture, and when committing the earlier word, split the LogUnit.
    private long mSavedDownEventTime;
    private Bundle mFeedbackDialogBundle = null;
    // Whether the feedback dialog is visible, and the user is typing into it.  Normal logging is
    // not performed on text that the user types into the feedback dialog.
    private boolean mInFeedbackDialog = false;
    private Handler mUserRecordingTimeoutHandler;
    private static final long USER_RECORDING_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);

    // Stores a temporary LogUnit while generating a phantom space.  Needed because phantom spaces
    // are issued out-of-order, immediately before the characters generated by other operations that
    // have already outputted LogStatements.
    private LogUnit mPhantomSpaceLogUnit = null;

    private ResearchLogger() {
        mStatistics = Statistics.getInstance();
    }

    public static ResearchLogger getInstance() {
        return sInstance;
    }

    public void init(final LatinIME latinIME, final KeyboardSwitcher keyboardSwitcher,
            final Suggest suggest) {
        assert latinIME != null;
        mLatinIME = latinIME;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(latinIME);
        mPrefs.registerOnSharedPreferenceChangeListener(this);

        // Initialize fields from preferences
        sIsLogging = ResearchSettings.readResearchLoggerEnabledFlag(mPrefs);

        // Initialize fields from resources
        final Resources res = latinIME.getResources();
        sAccountType = res.getString(R.string.research_account_type);
        sAllowedAccountDomain = res.getString(R.string.research_allowed_account_domain);

        // Initialize directory manager
        mResearchLogDirectory = new ResearchLogDirectory(mLatinIME);
        cleanLogDirectoryIfNeeded(mResearchLogDirectory, System.currentTimeMillis());

        // Initialize log buffers
        resetLogBuffers();

        // Initialize external services
        mUploadNowIntent = new Intent(mLatinIME, UploaderService.class);
        mUploadNowIntent.putExtra(UploaderService.EXTRA_UPLOAD_UNCONDITIONALLY, true);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            UploaderService.cancelAndRescheduleUploadingService(mLatinIME,
                    true /* needsRescheduling */);
        }
        mReplayer.setKeyboardSwitcher(keyboardSwitcher);
    }

    private void resetLogBuffers() {
        mMainResearchLog = new ResearchLog(mResearchLogDirectory.getLogFilePath(
                System.currentTimeMillis(), System.nanoTime()), mLatinIME);
        final int numWordsToIgnore = new Random().nextInt(NUMBER_OF_WORDS_BETWEEN_SAMPLES + 1);
        mMainLogBuffer = new MainLogBuffer(NUMBER_OF_WORDS_BETWEEN_SAMPLES, numWordsToIgnore,
                mSuggest) {
            @Override
            protected void publish(final ArrayList<LogUnit> logUnits,
                    boolean canIncludePrivateData) {
                canIncludePrivateData |= IS_LOGGING_EVERYTHING;
                for (final LogUnit logUnit : logUnits) {
                    if (DEBUG) {
                        final String wordsString = logUnit.getWordsAsString();
                        Log.d(TAG, "onPublish: '" + wordsString
                                + "', hc: " + logUnit.containsUserDeletions()
                                + ", cipd: " + canIncludePrivateData);
                    }
                    for (final String word : logUnit.getWordsAsStringArray()) {
                        final Dictionary dictionary = getDictionary();
                        mStatistics.recordWordEntered(
                                dictionary != null && dictionary.isValidWord(word),
                                logUnit.containsUserDeletions());
                    }
                }
                publishLogUnits(logUnits, mMainResearchLog, canIncludePrivateData);
            }
        };
    }

    private void cleanLogDirectoryIfNeeded(final ResearchLogDirectory researchLogDirectory,
            final long now) {
        final long lastCleanupTime = ResearchSettings.readResearchLastDirCleanupTime(mPrefs);
        if (now - lastCleanupTime < DURATION_BETWEEN_DIR_CLEANUP_IN_MS) return;
        final long oldestAllowedFileTime = now - MAX_LOGFILE_AGE_IN_MS;
        mResearchLogDirectory.cleanupLogFilesOlderThan(oldestAllowedFileTime);
        ResearchSettings.writeResearchLastDirCleanupTime(mPrefs, now);
    }

    public void mainKeyboardView_onAttachedToWindow(final MainKeyboardView mainKeyboardView) {
        mMainKeyboardView = mainKeyboardView;
        maybeShowSplashScreen();
    }

    public void mainKeyboardView_onDetachedFromWindow() {
        mMainKeyboardView = null;
    }

    public void onDestroy() {
        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    private void maybeShowSplashScreen() {
        if (ResearchSettings.readHasSeenSplash(mPrefs)) return;
        if (mSplashScreen != null && mSplashScreen.isShowing()) return;
        if (mMainKeyboardView == null) return;
        final IBinder windowToken = mMainKeyboardView.getWindowToken();
        if (windowToken == null) return;

        mSplashScreen = new SplashScreen(mLatinIME, this);
        mSplashScreen.showSplashScreen(windowToken);
    }

    @Override
    public void onSplashScreenUserClickedOk() {
        if (mPrefs == null) {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(mLatinIME);
            if (mPrefs == null) return;
        }
        sIsLogging = true;
        ResearchSettings.writeResearchLoggerEnabledFlag(mPrefs, true);
        ResearchSettings.writeHasSeenSplash(mPrefs, true);
        restart();
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
        requestIndicatorRedraw();
        mStatistics.reset();
        checkForEmptyEditor();
    }

    /* package */ void stop() {
        if (DEBUG) {
            Log.d(TAG, "stop called");
        }
        // Commit mCurrentLogUnit before closing.
        commitCurrentLogUnit();

        try {
            mMainLogBuffer.shiftAndPublishAll();
        } catch (final IOException e) {
            Log.w(TAG, "IOException when publishing LogBuffer", e);
        }
        logStatistics();
        commitCurrentLogUnit();
        mMainLogBuffer.setIsStopping();
        try {
            mMainLogBuffer.shiftAndPublishAll();
        } catch (final IOException e) {
            Log.w(TAG, "IOException when publishing LogBuffer", e);
        }
        mMainResearchLog.blockingClose(RESEARCHLOG_CLOSE_TIMEOUT_IN_MS);

        resetLogBuffers();
        cancelFeedbackDialog();
    }

    public void abort() {
        if (DEBUG) {
            Log.d(TAG, "abort called");
        }
        mMainLogBuffer.clear();
        mMainResearchLog.blockingAbort(RESEARCHLOG_ABORT_TIMEOUT_IN_MS);

        resetLogBuffers();
    }

    private void restart() {
        stop();
        start();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (key == null || prefs == null) {
            return;
        }
        requestIndicatorRedraw();
        mPrefs = prefs;
        prefsChanged(prefs);
    }

    public void onResearchKeySelected(final LatinIME latinIME) {
        mCurrentLogUnit.removeResearchButtonInvocation();
        if (mInFeedbackDialog) {
            Toast.makeText(latinIME, R.string.research_please_exit_feedback_form,
                    Toast.LENGTH_LONG).show();
            return;
        }
        presentFeedbackDialog(latinIME);
    }

    public void presentFeedbackDialogFromSettings() {
        if (mLatinIME != null) {
            presentFeedbackDialog(mLatinIME);
        }
    }

    public void presentFeedbackDialog(final LatinIME latinIME) {
        if (isMakingUserRecording()) {
            saveRecording();
        }
        mInFeedbackDialog = true;

        final Intent intent = new Intent();
        intent.setClass(mLatinIME, FeedbackActivity.class);
        if (mFeedbackDialogBundle == null) {
            // Restore feedback field with channel name
            final Bundle bundle = new Bundle();
            bundle.putBoolean(FeedbackFragment.KEY_INCLUDE_ACCOUNT_NAME, true);
            bundle.putBoolean(FeedbackFragment.KEY_HAS_USER_RECORDING, false);
            if (FEEDBACK_DIALOG_SHOULD_PRESERVE_TEXT_FIELD) {
                final String savedChannelName = mPrefs.getString(PREF_RESEARCH_SAVED_CHANNEL, "");
                bundle.putString(FeedbackFragment.KEY_FEEDBACK_STRING, savedChannelName);
            }
            mFeedbackDialogBundle = bundle;
        }
        intent.putExtras(mFeedbackDialogBundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        latinIME.startActivity(intent);
    }

    public void setFeedbackDialogBundle(final Bundle bundle) {
        mFeedbackDialogBundle = bundle;
    }

    public void startRecording() {
        final Resources res = mLatinIME.getResources();
        Toast.makeText(mLatinIME,
                res.getString(R.string.research_feedback_demonstration_instructions),
                Toast.LENGTH_LONG).show();
        startRecordingInternal();
    }

    private void startRecordingInternal() {
        if (mUserRecordingLog != null) {
            mUserRecordingLog.blockingAbort(RESEARCHLOG_ABORT_TIMEOUT_IN_MS);
        }
        mUserRecordingFile = mResearchLogDirectory.getUserRecordingFilePath(
                System.currentTimeMillis(), System.nanoTime());
        mUserRecordingLog = new ResearchLog(mUserRecordingFile, mLatinIME);
        mUserRecordingLogBuffer = new LogBuffer();
        resetRecordingTimer();
    }

    private boolean isMakingUserRecording() {
        return mUserRecordingLog != null;
    }

    private void resetRecordingTimer() {
        if (mUserRecordingTimeoutHandler == null) {
            mUserRecordingTimeoutHandler = new Handler();
        }
        clearRecordingTimer();
        mUserRecordingTimeoutHandler.postDelayed(mRecordingHandlerTimeoutRunnable,
                USER_RECORDING_TIMEOUT_MS);
    }

    private void clearRecordingTimer() {
        mUserRecordingTimeoutHandler.removeCallbacks(mRecordingHandlerTimeoutRunnable);
    }

    private Runnable mRecordingHandlerTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            cancelRecording();
            requestIndicatorRedraw();
            final Resources res = mLatinIME.getResources();
            Toast.makeText(mLatinIME, res.getString(R.string.research_feedback_recording_failure),
                    Toast.LENGTH_LONG).show();
        }
    };

    private void cancelRecording() {
        if (mUserRecordingLog != null) {
            mUserRecordingLog.blockingAbort(RESEARCHLOG_ABORT_TIMEOUT_IN_MS);
        }
        mUserRecordingLog = null;
        mUserRecordingLogBuffer = null;
        if (mFeedbackDialogBundle != null) {
            mFeedbackDialogBundle.putBoolean("HasRecording", false);
        }
    }

    private void saveRecording() {
        commitCurrentLogUnit();
        publishLogBuffer(mUserRecordingLogBuffer, mUserRecordingLog, true);
        mUserRecordingLog.blockingClose(RESEARCHLOG_CLOSE_TIMEOUT_IN_MS);
        mUserRecordingLog = null;
        mUserRecordingLogBuffer = null;

        if (mFeedbackDialogBundle != null) {
            mFeedbackDialogBundle.putBoolean(FeedbackFragment.KEY_HAS_USER_RECORDING, true);
        }
        clearRecordingTimer();
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
            final long resumeTime = currentTime
                    + TimeUnit.MINUTES.toMillis(SUSPEND_DURATION_IN_MINUTES);
            suspendLoggingUntil(resumeTime);
            toast.cancel();
            Toast.makeText(latinIME, R.string.research_notify_logging_suspended,
                    Toast.LENGTH_LONG).show();
        }
    }
    */

    /**
     * Get the name of the first allowed account on the device.
     *
     * Allowed accounts must be in the domain given by ALLOWED_ACCOUNT_DOMAIN.
     *
     * @return The user's account name.
     */
    public String getAccountName() {
        if (sAccountType == null || sAccountType.isEmpty()) {
            return null;
        }
        if (sAllowedAccountDomain == null || sAllowedAccountDomain.isEmpty()) {
            return null;
        }
        final AccountManager manager = AccountManager.get(mLatinIME);
        // Filter first by account type.
        final Account[] accounts = manager.getAccountsByType(sAccountType);

        for (final Account account : accounts) {
            if (DEBUG) {
                Log.d(TAG, account.name);
            }
            final String[] parts = account.name.split("@");
            if (parts.length > 1 && parts[1].equals(sAllowedAccountDomain)) {
                return parts[0];
            }
        }
        return null;
    }

    private static final LogStatement LOGSTATEMENT_FEEDBACK =
            new LogStatement("UserFeedback", false, false, "contents", "accountName", "recording");
    public void sendFeedback(final String feedbackContents, final boolean includeHistory,
            final boolean isIncludingAccountName, final boolean isIncludingRecording) {
        String recording = "";
        if (isIncludingRecording) {
            // Try to read recording from recently written json file
            if (mUserRecordingFile != null) {
                FileChannel channel = null;
                try {
                    channel = new FileInputStream(mUserRecordingFile).getChannel();
                    final MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0,
                            channel.size());
                    // Android's openFileOutput() creates the file, so we use Android's default
                    // Charset (UTF-8) here to read it.
                    recording = Charset.defaultCharset().decode(buffer).toString();
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "Could not find recording file", e);
                } catch (IOException e) {
                    Log.e(TAG, "Error reading recording file", e);
                } finally {
                    if (channel != null) {
                        try {
                            channel.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing recording file", e);
                        }
                    }
                }
            }
        }
        final LogUnit feedbackLogUnit = new LogUnit();
        final String accountName = isIncludingAccountName ? getAccountName() : "";
        feedbackLogUnit.addLogStatement(LOGSTATEMENT_FEEDBACK, SystemClock.uptimeMillis(),
                feedbackContents, accountName, recording);

        final ResearchLog feedbackLog = new FeedbackLog(mResearchLogDirectory.getLogFilePath(
                System.currentTimeMillis(), System.nanoTime()), mLatinIME);
        final LogBuffer feedbackLogBuffer = new LogBuffer();
        feedbackLogBuffer.shiftIn(feedbackLogUnit);
        publishLogBuffer(feedbackLogBuffer, feedbackLog, true /* isIncludingPrivateData */);
        feedbackLog.blockingClose(RESEARCHLOG_CLOSE_TIMEOUT_IN_MS);
        uploadNow();

        if (isIncludingRecording && DEBUG_REPLAY_AFTER_FEEDBACK) {
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    final ReplayData replayData =
                            mMotionEventReader.readMotionEventData(mUserRecordingFile);
                    mReplayer.replay(replayData, null);
                }
            }, TimeUnit.SECONDS.toMillis(1));
        }

        if (FEEDBACK_DIALOG_SHOULD_PRESERVE_TEXT_FIELD) {
            // Use feedback string as a channel name to label feedback strings.  Here we record the
            // string for prepopulating the field next time.
            final String channelName = feedbackContents;
            if (mPrefs == null) {
                return;
            }
            mPrefs.edit().putString(PREF_RESEARCH_SAVED_CHANNEL, channelName).apply();
        }
    }

    public void uploadNow() {
        if (DEBUG) {
            Log.d(TAG, "calling uploadNow()");
        }
        mLatinIME.startService(mUploadNowIntent);
    }

    public void onLeavingSendFeedbackDialog() {
        mInFeedbackDialog = false;
    }

    private void cancelFeedbackDialog() {
        if (isMakingUserRecording()) {
            cancelRecording();
        }
        mInFeedbackDialog = false;
    }

    public void initSuggest(final Suggest suggest) {
        mSuggest = suggest;
        // MainLogBuffer now has an out-of-date Suggest object.  Close down MainLogBuffer and create
        // a new one.
        if (mMainLogBuffer != null) {
            restart();
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

    /**
     * Returns true if logging is permitted.
     *
     * This method is called when adding a LogStatement to a LogUnit, and when adding a LogUnit to a
     * ResearchLog.  It is checked in both places in case conditions change between these times, and
     * as a defensive measure in case refactoring changes the logging pipeline.
     */
    private boolean isAllowedToLogTo(final ResearchLog researchLog) {
        // Logging is never allowed in these circumstances
        if (mIsPasswordView) return false;
        if (!sIsLogging) return false;
        if (mInFeedbackDialog) {
            // The FeedbackDialog is up.  Normal logging should not happen (the user might be trying
            // out things while the dialog is up, and their reporting of an issue may not be
            // representative of what they normally type).  However, after the user has finished
            // entering their feedback, the logger packs their comments and an encoded version of
            // any demonstration of the issue into a special "FeedbackLog".  So if the FeedbackLog
            // is the destination, we do want to allow logging to it.
            return researchLog.isFeedbackLog();
        }
        // No other exclusions.  Logging is permitted.
        return true;
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

    private boolean isReplaying() {
        return mReplayer.isReplaying();
    }

    private int getIndicatorColor() {
        if (isMakingUserRecording()) {
            return Color.YELLOW;
        }
        if (isReplaying()) {
            return Color.GREEN;
        }
        return Color.RED;
    }

    public void paintIndicator(KeyboardView view, Paint paint, Canvas canvas, int width,
            int height) {
        // TODO: Reimplement using a keyboard background image specific to the ResearchLogger
        // and remove this method.
        // The check for MainKeyboardView ensures that the indicator only decorates the main
        // keyboard, not every keyboard.
        if (IS_SHOWING_INDICATOR && (isAllowedToLogTo(mMainResearchLog) || isReplaying())
                && view instanceof MainKeyboardView) {
            final int savedColor = paint.getColor();
            paint.setColor(getIndicatorColor());
            final Style savedStyle = paint.getStyle();
            paint.setStyle(Style.STROKE);
            final float savedStrokeWidth = paint.getStrokeWidth();
            if (IS_SHOWING_INDICATOR_CLEARLY) {
                paint.setStrokeWidth(5);
                canvas.drawLine(0, 0, 0, height, paint);
                canvas.drawLine(width, 0, width, height, paint);
            } else {
                // Put a tiny dot on the screen so a knowledgeable user can check whether it is
                // enabled.  The dot is actually a zero-width, zero-height rectangle, placed at the
                // lower-right corner of the canvas, painted with a non-zero border width.
                paint.setStrokeWidth(3);
                canvas.drawRect(width - 1, height - 1, width, height, paint);
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
        assert values.length == logStatement.getKeys().length;
        if (isAllowedToLogTo(mMainResearchLog) && logUnit != null) {
            final long time = SystemClock.uptimeMillis();
            logUnit.addLogStatement(logStatement, time, values);
        }
    }

    private void setCurrentLogUnitContainsDigitFlag() {
        mCurrentLogUnit.setMayContainDigit();
    }

    private void setCurrentLogUnitContainsUserDeletions() {
        mCurrentLogUnit.setContainsUserDeletions();
    }

    private void setCurrentLogUnitCorrectionType(final int correctionType) {
        mCurrentLogUnit.setCorrectionType(correctionType);
    }

    /* package for test */ void commitCurrentLogUnit() {
        if (DEBUG) {
            Log.d(TAG, "commitCurrentLogUnit" + (mCurrentLogUnit.hasOneOrMoreWords() ?
                    ": " + mCurrentLogUnit.getWordsAsString() : ""));
        }
        if (!mCurrentLogUnit.isEmpty()) {
            mMainLogBuffer.shiftIn(mCurrentLogUnit);
            if (mUserRecordingLogBuffer != null) {
                mUserRecordingLogBuffer.shiftIn(mCurrentLogUnit);
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
        // it the mCurrentLogUnit so the new edits are captured with the word.  Optionally dump the
        // contents of mCurrentLogUnit (useful if they contain deletions of the next word that
        // should not be reported to protect user privacy)
        //
        // Note that we don't use mLastLogUnit here, because it only goes one word back and is only
        // needed for reverts, which only happen one back.
        final LogUnit oldLogUnit = mMainLogBuffer.peekLastLogUnit();

        // Check that expected word matches.  It's ok if both strings are null, because this is the
        // case where the LogUnit is storing a non-word, e.g. a separator.
        if (oldLogUnit != null) {
            // Because the word is stored in the LogUnit with digits scrubbed, the comparison must
            // be made on a scrubbed version of the expectedWord as well.
            final String scrubbedExpectedWord = scrubDigitsFromString(expectedWord);
            final String oldLogUnitWords = oldLogUnit.getWordsAsString();
            if (!TextUtils.equals(scrubbedExpectedWord, oldLogUnitWords)) return;
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
        enqueueEvent(LOGSTATEMENT_UNCOMMIT_CURRENT_LOGUNIT);
        if (DEBUG) {
            Log.d(TAG, "uncommitCurrentLogUnit (dump=" + dumpCurrentLogUnit + ") back to "
                    + (mCurrentLogUnit.hasOneOrMoreWords() ? ": '"
                        + mCurrentLogUnit.getWordsAsString() + "'" : ""));
        }
    }

    /**
     * Publish all the logUnits in the logBuffer, without doing any privacy filtering.
     */
    /* package for test */ void publishLogBuffer(final LogBuffer logBuffer,
            final ResearchLog researchLog, final boolean canIncludePrivateData) {
        publishLogUnits(logBuffer.getLogUnits(), researchLog, canIncludePrivateData);
    }

    private static final LogStatement LOGSTATEMENT_LOG_SEGMENT_OPENING =
            new LogStatement("logSegmentStart", false, false, "isIncludingPrivateData");
    private static final LogStatement LOGSTATEMENT_LOG_SEGMENT_CLOSING =
            new LogStatement("logSegmentEnd", false, false);
    /**
     * Publish all LogUnits in a list.
     *
     * Any privacy checks should be performed before calling this method.
     */
    /* package for test */ void publishLogUnits(final List<LogUnit> logUnits,
            final ResearchLog researchLog, final boolean canIncludePrivateData) {
        final LogUnit openingLogUnit = new LogUnit();
        if (logUnits.isEmpty()) return;
        if (!isAllowedToLogTo(researchLog)) return;
        // LogUnits not containing private data, such as contextual data for the log, do not require
        // logSegment boundary statements.
        if (canIncludePrivateData) {
            openingLogUnit.addLogStatement(LOGSTATEMENT_LOG_SEGMENT_OPENING,
                    SystemClock.uptimeMillis(), canIncludePrivateData);
            researchLog.publish(openingLogUnit, true /* isIncludingPrivateData */);
        }
        for (LogUnit logUnit : logUnits) {
            if (DEBUG) {
                Log.d(TAG, "publishLogBuffer: " + (logUnit.hasOneOrMoreWords()
                        ? logUnit.getWordsAsString() : "<wordless>")
                        + ", correction?: " + logUnit.containsUserDeletions());
            }
            researchLog.publish(logUnit, canIncludePrivateData);
        }
        if (canIncludePrivateData) {
            final LogUnit closingLogUnit = new LogUnit();
            closingLogUnit.addLogStatement(LOGSTATEMENT_LOG_SEGMENT_CLOSING,
                    SystemClock.uptimeMillis());
            researchLog.publish(closingLogUnit, true /* isIncludingPrivateData */);
        }
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
        if (word.length() > 0 && hasLetters(word)) {
            mCurrentLogUnit.setWords(word);
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

    /* package for test */ static String scrubDigitsFromString(final String s) {
        if (s == null) return null;
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
                    "isDevTeamBuild");
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
                final String uuid = ResearchSettings.readResearchLoggerUuid(researchLogger.mPrefs);
                researchLogger.enqueueEvent(LOGSTATEMENT_LATIN_IME_ON_START_INPUT_VIEW_INTERNAL,
                        uuid, editorInfo.packageName, Integer.toHexString(editorInfo.inputType),
                        Integer.toHexString(editorInfo.imeOptions), editorInfo.fieldId,
                        Build.DISPLAY, Build.MODEL, prefs, versionCode, versionName,
                        OUTPUT_FORMAT_VERSION, IS_LOGGING_EVERYTHING,
                        researchLogger.isDevTeamBuild());
                // Commit the logUnit so the LatinImeOnStartInputViewInternal event is in its own
                // logUnit at the beginning of the log.
                researchLogger.commitCurrentLogUnit();
            } catch (final NameNotFoundException e) {
                Log.e(TAG, "NameNotFound", e);
            }
        }
    }

    // TODO: Update this heuristic pattern to something more reliable.  Developer builds tend to
    // have the developer name and year embedded.
    private static final Pattern developerBuildRegex = Pattern.compile("[A-Za-z]\\.20[1-9]");
    private boolean isDevTeamBuild() {
        try {
            final PackageInfo packageInfo;
            packageInfo = mLatinIME.getPackageManager().getPackageInfo(mLatinIME.getPackageName(),
                    0);
            final String versionName = packageInfo.versionName;
            return developerBuildRegex.matcher(versionName).find();
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Could not determine package name", e);
            return false;
        }
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
            new LogStatement("MotionEvent", true, false, "action",
                    LogStatement.KEY_IS_LOGGING_RELATED, "motionEvent");
    public static void mainKeyboardView_processMotionEvent(final MotionEvent me) {
        if (me == null) {
            return;
        }
        final int action = me.getActionMasked();
        final long eventTime = me.getEventTime();
        final String actionString = LoggingUtils.getMotionEventActionTypeString(action);
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_MAIN_KEYBOARD_VIEW_PROCESS_MOTION_EVENT,
                actionString, false /* IS_LOGGING_RELATED */, MotionEvent.obtain(me));
        if (action == MotionEvent.ACTION_DOWN) {
            // Subtract 1 from eventTime so the down event is included in the later
            // LogUnit, not the earlier (the test is for inequality).
            researchLogger.setSavedDownEventTime(eventTime - 1);
        }
        // Refresh the timer in case we are capturing user feedback.
        if (researchLogger.isMakingUserRecording()) {
            researchLogger.resetRecordingTimer();
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
     * The IME is finishing; it is either being destroyed, or is about to be hidden.
     *
     * UserAction: The user has performed an action that has caused the IME to be closed.  They may
     * have focused on something other than a text field, or explicitly closed it.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_ONFINISHINPUTVIEWINTERNAL =
            new LogStatement("LatinIMEOnFinishInputViewInternal", false, false, "isTextTruncated",
                    "text");
    public static void latinIME_onFinishInputViewInternal(final boolean finishingInput,
            final int savedSelectionStart, final int savedSelectionEnd, final InputConnection ic) {
        // The finishingInput flag is set in InputMethodService.  It is true if called from
        // doFinishInput(), which can be called as part of doStartInput().  This can happen at times
        // when the IME is not closing, such as when powering up.  The finishinInput flag is false
        // if called from finishViews(), which is called from hideWindow() and onDestroy().  These
        // are the situations in which we want to finish up the researchLog.
        if (ic != null && !finishingInput) {
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
            researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_ONFINISHINPUTVIEWINTERNAL,
                    isTextTruncated, text);
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
            TextRange range = connection.getWordRangeAtCursor(WHITESPACE_SEPARATORS, 1);
            if (range != null) {
                word = range.mWord.toString();
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
     * Log a revert of onTextInput() (known in the IME as "EnteredText").
     *
     * SystemResponse: Remove the LogUnit recording the textInput
     */
    public static void latinIME_handleBackspace_cancelTextInput(final String text) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.uncommitCurrentLogUnit(text, true /* dumpCurrentLogUnit */);
    }

    /**
     * Log a call to LatinIME.pickSuggestionManually().
     *
     * UserAction: The user has chosen a specific word from the suggestion strip.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_PICKSUGGESTIONMANUALLY =
            new LogStatement("LatinIMEPickSuggestionManually", true, false, "replacedWord", "index",
                    "suggestion", "x", "y", "isBatchMode", "score", "kind", "sourceDict");
    /**
     * Log a call to LatinIME.pickSuggestionManually().
     *
     * @param replacedWord the typed word that this manual suggestion replaces. May not be null.
     * @param index the index in the suggestion strip
     * @param suggestion the committed suggestion. May not be null.
     * @param isBatchMode whether this was input in batch mode, aka gesture.
     * @param score the internal score of the suggestion, as output by the dictionary
     * @param kind the kind of suggestion, as one of the SuggestedWordInfo#KIND_* constants
     * @param sourceDict the source origin of this word, as one of the Dictionary#TYPE_* constants.
     */
    public static void latinIME_pickSuggestionManually(final String replacedWord,
            final int index, final String suggestion, final boolean isBatchMode,
            final int score, final int kind, final String sourceDict) {
        final ResearchLogger researchLogger = getInstance();
        // Note : suggestion can't be null here, because it's only called in a place where it
        // can't be null.
        if (!replacedWord.equals(suggestion.toString())) {
            // The user chose something other than what was already there.
            researchLogger.setCurrentLogUnitContainsUserDeletions();
            researchLogger.setCurrentLogUnitCorrectionType(LogUnit.CORRECTIONTYPE_TYPO);
        }
        final String scrubbedWord = scrubDigitsFromString(suggestion);
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_PICKSUGGESTIONMANUALLY,
                scrubDigitsFromString(replacedWord), index,
                scrubbedWord, Constants.SUGGESTION_STRIP_COORDINATE,
                Constants.SUGGESTION_STRIP_COORDINATE, isBatchMode, score, kind, sourceDict);
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
     * SystemResponse: The IME is inserting text into the TextView for non-word-constituent,
     * strings (separators, numbers, other symbols).
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_SENDKEYCODEPOINT =
            new LogStatement("LatinIMESendKeyCodePoint", true, false, "code");
    public static void latinIME_sendKeyCodePoint(final int code) {
        final ResearchLogger researchLogger = getInstance();
        final LogUnit phantomSpaceLogUnit = researchLogger.mPhantomSpaceLogUnit;
        if (phantomSpaceLogUnit == null) {
            researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_SENDKEYCODEPOINT,
                    Constants.printableCode(scrubDigitFromCodePoint(code)));
            if (Character.isDigit(code)) {
                researchLogger.setCurrentLogUnitContainsDigitFlag();
            }
            researchLogger.commitCurrentLogUnit();
        } else {
            researchLogger.enqueueEvent(phantomSpaceLogUnit, LOGSTATEMENT_LATINIME_SENDKEYCODEPOINT,
                    Constants.printableCode(scrubDigitFromCodePoint(code)));
            if (Character.isDigit(code)) {
                phantomSpaceLogUnit.setMayContainDigit();
            }
            researchLogger.mMainLogBuffer.shiftIn(phantomSpaceLogUnit);
            if (researchLogger.mUserRecordingLogBuffer != null) {
                researchLogger.mUserRecordingLogBuffer.shiftIn(phantomSpaceLogUnit);
            }
            researchLogger.mPhantomSpaceLogUnit = null;
        }
    }

    /**
     * Log a call to LatinIME.promotePhantomSpace().
     *
     * SystemResponse: The IME is inserting a real space in place of a phantom space.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_PROMOTEPHANTOMSPACE =
            new LogStatement("LatinIMEPromotePhantomSpace", false, false);
    public static void latinIME_promotePhantomSpace() {
        // A phantom space is always added before the text that triggered it.  The triggering text
        // and the events that created it will be in mCurrentLogUnit, but the phantom space should
        // be in its own LogUnit, committed before the triggering text.  Although it is created
        // here, it is not added to the LogBuffer until the following call to
        // latinIME_sendKeyCodePoint, because SENDKEYCODEPOINT LogStatement also must go into that
        // LogUnit.
        final ResearchLogger researchLogger = getInstance();
        researchLogger.mPhantomSpaceLogUnit = new LogUnit();
        researchLogger.enqueueEvent(researchLogger.mPhantomSpaceLogUnit,
                LOGSTATEMENT_LATINIME_PROMOTEPHANTOMSPACE);
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
        logUnit = researchLogger.mMainLogBuffer.peekLastLogUnit();
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
    public static void mainKeyboardView_setKeyboard(final Keyboard keyboard,
            final int orientation) {
        final KeyboardId kid = keyboard.mId;
        final boolean isPasswordView = kid.passwordInput();
        final ResearchLogger researchLogger = getInstance();
        researchLogger.setIsPasswordView(isPasswordView);
        researchLogger.enqueueEvent(LOGSTATEMENT_MAINKEYBOARDVIEW_SETKEYBOARD,
                KeyboardId.elementIdToName(kid.mElementId),
                kid.mLocale + ":" + kid.mSubtype.getExtraValueOf(KEYBOARD_LAYOUT_SET),
                orientation, kid.mWidth, KeyboardId.modeName(kid.mMode), kid.imeAction(),
                kid.navigateNext(), kid.navigatePrevious(), kid.mClobberSettingsKey,
                isPasswordView, kid.mShortcutKeyEnabled, kid.mHasShortcutKey,
                kid.mLanguageSwitchKeyEnabled, kid.isMultiLine(), keyboard.mOccupiedWidth,
                keyboard.mOccupiedHeight, keyboard.getKeys());
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
        // TODO: Prioritize adding a unit test for this method (as it is especially complex)
        // TODO: Update the UserRecording LogBuffer as well as the MainLogBuffer
        final ResearchLogger researchLogger = getInstance();
        //
        // 1. Remove separator LogUnit
        final LogUnit lastLogUnit = researchLogger.mMainLogBuffer.peekLastLogUnit();
        // Check that we're not at the beginning of input
        if (lastLogUnit == null) return;
        // Check that we're after a separator
        if (lastLogUnit.getWordsAsString() != null) return;
        // Remove separator
        final LogUnit separatorLogUnit = researchLogger.mMainLogBuffer.unshiftIn();

        // 2. Add revert LogStatement
        final LogUnit revertedLogUnit = researchLogger.mMainLogBuffer.peekLastLogUnit();
        if (revertedLogUnit == null) return;
        if (!revertedLogUnit.getWordsAsString().equals(scrubDigitsFromString(committedWord))) {
            // Any word associated with the reverted LogUnit has already had its digits scrubbed, so
            // any digits in the committedWord argument must also be scrubbed for an accurate
            // comparison.
            return;
        }
        researchLogger.enqueueEvent(revertedLogUnit, LOGSTATEMENT_LATINIME_REVERTCOMMIT,
                committedWord, originallyTypedWord, separatorString);

        // 3. Update the word associated with the LogUnit
        revertedLogUnit.setWords(originallyTypedWord);
        revertedLogUnit.setContainsUserDeletions();

        // 4. Re-add the separator LogUnit
        researchLogger.mMainLogBuffer.shiftIn(separatorLogUnit);

        // 5. Record stats
        researchLogger.mStatistics.recordRevertCommit(SystemClock.uptimeMillis());
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
            final ResearchLogger researchLogger = getInstance();
            researchLogger.enqueueEvent(LOGSTATEMENT_POINTERTRACKER_CALLLISTENERONCODEINPUT,
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
        final ResearchLogger researchLogger = getInstance();
        // An extra LogUnit is added for the period; this is removed here because of the revert.
        researchLogger.uncommitCurrentLogUnit(null, true /* dumpCurrentLogUnit */);
        // TODO: This will probably be lost as the user backspaces further.  Figure out how to put
        // it into the right logUnit.
        researchLogger.enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTION_REVERTDOUBLESPACEPERIOD);
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
     * text input to another word (or words) that the user more likely desired to type.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_COMMITCURRENTAUTOCORRECTION =
            new LogStatement("LatinIMECommitCurrentAutoCorrection", true, true, "typedWord",
                    "autoCorrection", "separatorString");
    public static void latinIme_commitCurrentAutoCorrection(final String typedWord,
            final String autoCorrection, final String separatorString, final boolean isBatchMode,
            final SuggestedWords suggestedWords) {
        final String scrubbedTypedWord = scrubDigitsFromString(typedWord);
        final String scrubbedAutoCorrection = scrubDigitsFromString(autoCorrection);
        final ResearchLogger researchLogger = getInstance();
        researchLogger.mCurrentLogUnit.initializeSuggestions(suggestedWords);
        researchLogger.onWordFinished(scrubbedAutoCorrection, isBatchMode);

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
        // Only include opening and closing logSegments if private data is included
        final String scrubbedWord = scrubDigitsFromString(committedWord);
        if (!researchLogger.isExpectingCommitText) {
            researchLogger.enqueueEvent(LOGSTATEMENT_RICHINPUTCONNECTIONCOMMITTEXT,
                    newCursorPosition);
            researchLogger.commitCurrentLogUnitAsWord(scrubbedWord, Long.MAX_VALUE, isBatchMode);
        }
        researchLogger.isExpectingCommitText = false;
    }

    /**
     * Shared events for logging committed text.
     *
     * The "CommitTextEventHappened" LogStatement is written to the log even if privacy rules
     * indicate that the word contents should not be logged.  It has no contents, and only serves to
     * record the event and thereby make it easier to calculate word-level statistics even when the
     * word contents are unknown.
     */
    private static final LogStatement LOGSTATEMENT_COMMITTEXT =
            new LogStatement("CommitText", true /* isPotentiallyPrivate */,
                    false /* isPotentiallyRevealing */, "committedText", "isBatchMode");
    private static final LogStatement LOGSTATEMENT_COMMITTEXT_EVENT_HAPPENED =
            new LogStatement("CommitTextEventHappened", false /* isPotentiallyPrivate */,
                    false /* isPotentiallyRevealing */);
    private void enqueueCommitText(final String word, final boolean isBatchMode) {
        // Event containing the word; will be published only if privacy checks pass
        enqueueEvent(LOGSTATEMENT_COMMITTEXT, word, isBatchMode);
        // Event not containing the word; will always be published
        enqueueEvent(LOGSTATEMENT_COMMITTEXT_EVENT_HAPPENED);
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
                    MotionEvent.obtain(me));
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
                    "enteredWordPos", "suggestedWords");
    public static void latinIME_onEndBatchInput(final CharSequence enteredText,
            final int enteredWordPos, final SuggestedWords suggestedWords) {
        final ResearchLogger researchLogger = getInstance();
        if (!TextUtils.isEmpty(enteredText) && hasLetters(enteredText.toString())) {
            researchLogger.mCurrentLogUnit.setWords(enteredText.toString());
        }
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_ONENDBATCHINPUT, enteredText,
                enteredWordPos, suggestedWords);
        researchLogger.mCurrentLogUnit.initializeSuggestions(suggestedWords);
        researchLogger.mStatistics.recordGestureInput(enteredText.length(),
                SystemClock.uptimeMillis());
    }

    private static final LogStatement LOGSTATEMENT_LATINIME_HANDLEBACKSPACE =
            new LogStatement("LatinIMEHandleBackspace", true, false, "numCharacters");
    /**
     * Log a call to LatinIME.handleBackspace() that is not a batch delete.
     *
     * UserInput: The user is deleting one or more characters by hitting the backspace key once.
     * The covers single character deletes as well as deleting selections.
     *
     * @param numCharacters how many characters the backspace operation deleted
     * @param shouldUncommitLogUnit whether to uncommit the last {@code LogUnit} in the
     * {@code LogBuffer}
     */
    public static void latinIME_handleBackspace(final int numCharacters,
            final boolean shouldUncommitLogUnit) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_HANDLEBACKSPACE, numCharacters);
        if (shouldUncommitLogUnit) {
            ResearchLogger.getInstance().uncommitCurrentLogUnit(
                    null, true /* dumpCurrentLogUnit */);
        }
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
        researchLogger.uncommitCurrentLogUnit(deletedText.toString(),
                false /* dumpCurrentLogUnit */);
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
     * If the current logUnit is split, then tapping, motion events, etc. before this time should
     * be assigned to one LogUnit, and events after this time should go into the following LogUnit.
     */
    public static void recordTimeForLogUnitSplit() {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.setSavedDownEventTime(SystemClock.uptimeMillis());
        researchLogger.mSavedDownEventTime = Long.MAX_VALUE;
    }

    /**
     * Log a call to LatinIME.handleSeparator()
     *
     * SystemResponse: The system is inserting a separator character, possibly performing auto-
     * correction or other actions appropriate at the end of a word.
     */
    private static final LogStatement LOGSTATEMENT_LATINIME_HANDLESEPARATOR =
            new LogStatement("LatinIMEHandleSeparator", false, false, "primaryCode",
                    "isComposingWord");
    public static void latinIME_handleSeparator(final int primaryCode,
            final boolean isComposingWord) {
        final ResearchLogger researchLogger = getInstance();
        researchLogger.enqueueEvent(LOGSTATEMENT_LATINIME_HANDLESEPARATOR, primaryCode,
                isComposingWord);
    }

    /**
     * Call this method when the logging system has attempted publication of an n-gram.
     *
     * Statistics are gathered about the success or failure.
     *
     * @param publishabilityResultCode a result code as defined by
     * {@code MainLogBuffer.PUBLISHABILITY_*}
     */
    static void recordPublishabilityResultCode(final int publishabilityResultCode) {
        final ResearchLogger researchLogger = getInstance();
        final Statistics statistics = researchLogger.mStatistics;
        statistics.recordPublishabilityResultCode(publishabilityResultCode);
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
                    "revertCommitsCount", "correctedWordsCount", "autoCorrectionsCount",
                    "publishableCount", "unpublishableStoppingCount",
                    "unpublishableIncorrectWordCount", "unpublishableSampledTooRecentlyCount",
                    "unpublishableDictionaryUnavailableCount", "unpublishableMayContainDigitCount",
                    "unpublishableNotInDictionaryCount");
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
                statistics.mRevertCommitsCount, statistics.mCorrectedWordsCount,
                statistics.mAutoCorrectionsCount, statistics.mPublishableCount,
                statistics.mUnpublishableStoppingCount, statistics.mUnpublishableIncorrectWordCount,
                statistics.mUnpublishableSampledTooRecently,
                statistics.mUnpublishableDictionaryUnavailable,
                statistics.mUnpublishableMayContainDigit, statistics.mUnpublishableNotInDictionary);
    }
}
