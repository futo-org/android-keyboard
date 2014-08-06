/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.inputmethod.latin.Constants.ImeOption.FORCE_ASCII;
import static com.android.inputmethod.latin.Constants.ImeOption.NO_MICROPHONE;
import static com.android.inputmethod.latin.Constants.ImeOption.NO_MICROPHONE_COMPAT;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Debug;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.compat.InputConnectionCompatUtils;
import com.android.inputmethod.compat.InputMethodServiceCompatUtils;
import com.android.inputmethod.dictionarypack.DictionaryPackConstants;
import com.android.inputmethod.event.Event;
import com.android.inputmethod.event.HardwareEventDecoder;
import com.android.inputmethod.event.HardwareKeyboardEventDecoder;
import com.android.inputmethod.event.InputTransaction;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.define.DebugFlags;
import com.android.inputmethod.latin.define.ProductionFlags;
import com.android.inputmethod.latin.inputlogic.InputLogic;
import com.android.inputmethod.latin.personalization.ContextualDictionaryUpdater;
import com.android.inputmethod.latin.personalization.DictionaryDecayBroadcastReciever;
import com.android.inputmethod.latin.personalization.PersonalizationDictionaryUpdater;
import com.android.inputmethod.latin.personalization.PersonalizationHelper;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsActivity;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.suggestions.SuggestionStripView;
import com.android.inputmethod.latin.suggestions.SuggestionStripViewAccessor;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.CapsModeUtils;
import com.android.inputmethod.latin.utils.CoordinateUtils;
import com.android.inputmethod.latin.utils.DialogUtils;
import com.android.inputmethod.latin.utils.DistracterFilterCheckingExactMatches;
import com.android.inputmethod.latin.utils.ImportantNoticeUtils;
import com.android.inputmethod.latin.utils.IntentUtils;
import com.android.inputmethod.latin.utils.JniUtils;
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper;
import com.android.inputmethod.latin.utils.StatsUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService implements KeyboardActionListener,
        SuggestionStripView.Listener, SuggestionStripViewAccessor,
        DictionaryFacilitator.DictionaryInitializationListener,
        ImportantNoticeDialog.ImportantNoticeDialogListener {
    private static final String TAG = LatinIME.class.getSimpleName();
    private static final boolean TRACE = false;
    private static boolean DEBUG = false;

    private static final int EXTENDED_TOUCHABLE_REGION_HEIGHT = 100;

    private static final int PENDING_IMS_CALLBACK_DURATION = 800;

    private static final int DELAY_WAIT_FOR_DICTIONARY_LOAD = 2000; // 2s

    private static final int PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT = 2;

    /**
     * The name of the scheme used by the Package Manager to warn of a new package installation,
     * replacement or removal.
     */
    private static final String SCHEME_PACKAGE = "package";

    private final Settings mSettings;
    private final DictionaryFacilitator mDictionaryFacilitator =
            new DictionaryFacilitator(new DistracterFilterCheckingExactMatches(this /* context */));
    // TODO: Move from LatinIME.
    private final PersonalizationDictionaryUpdater mPersonalizationDictionaryUpdater =
            new PersonalizationDictionaryUpdater(this /* context */, mDictionaryFacilitator);
    private final ContextualDictionaryUpdater mContextualDictionaryUpdater =
            new ContextualDictionaryUpdater(this /* context */, mDictionaryFacilitator,
                    new Runnable() {
                        @Override
                        public void run() {
                            mHandler.postUpdateSuggestionStrip();
                        }
                    });
    private final InputLogic mInputLogic = new InputLogic(this /* LatinIME */,
            this /* SuggestionStripViewAccessor */, mDictionaryFacilitator);
    // We expect to have only one decoder in almost all cases, hence the default capacity of 1.
    // If it turns out we need several, it will get grown seamlessly.
    final SparseArray<HardwareEventDecoder> mHardwareEventDecoders = new SparseArray<>(1);

    private View mExtractArea;
    private View mKeyPreviewBackingView;
    private SuggestionStripView mSuggestionStripView;

    private RichInputMethodManager mRichImm;
    @UsedForTesting final KeyboardSwitcher mKeyboardSwitcher;
    private final SubtypeSwitcher mSubtypeSwitcher;
    private final SubtypeState mSubtypeState = new SubtypeState();

    // Object for reacting to adding/removing a dictionary pack.
    private final BroadcastReceiver mDictionaryPackInstallReceiver =
            new DictionaryPackInstallBroadcastReceiver(this);

    private final BroadcastReceiver mDictionaryDumpBroadcastReceiver =
            new DictionaryDumpBroadcastReceiver(this);

    private AlertDialog mOptionsDialog;

    private final boolean mIsHardwareAcceleratedDrawingEnabled;

    public final UIHandler mHandler = new UIHandler(this);

    public static final class UIHandler extends LeakGuardHandlerWrapper<LatinIME> {
        private static final int MSG_UPDATE_SHIFT_STATE = 0;
        private static final int MSG_PENDING_IMS_CALLBACK = 1;
        private static final int MSG_UPDATE_SUGGESTION_STRIP = 2;
        private static final int MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP = 3;
        private static final int MSG_RESUME_SUGGESTIONS = 4;
        private static final int MSG_REOPEN_DICTIONARIES = 5;
        private static final int MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED = 6;
        private static final int MSG_RESET_CACHES = 7;
        private static final int MSG_WAIT_FOR_DICTIONARY_LOAD = 8;
        // Update this when adding new messages
        private static final int MSG_LAST = MSG_WAIT_FOR_DICTIONARY_LOAD;

        private static final int ARG1_NOT_GESTURE_INPUT = 0;
        private static final int ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 1;
        private static final int ARG1_SHOW_GESTURE_FLOATING_PREVIEW_TEXT = 2;
        private static final int ARG2_UNUSED = 0;
        private static final int ARG1_FALSE = 0;
        private static final int ARG1_TRUE = 1;

        private int mDelayUpdateSuggestions;
        private int mDelayUpdateShiftState;

        public UIHandler(final LatinIME ownerInstance) {
            super(ownerInstance);
        }

        public void onCreate() {
            final LatinIME latinIme = getOwnerInstance();
            if (latinIme == null) {
                return;
            }
            final Resources res = latinIme.getResources();
            mDelayUpdateSuggestions = res.getInteger(R.integer.config_delay_update_suggestions);
            mDelayUpdateShiftState = res.getInteger(R.integer.config_delay_update_shift_state);
        }

        @Override
        public void handleMessage(final Message msg) {
            final LatinIME latinIme = getOwnerInstance();
            if (latinIme == null) {
                return;
            }
            final KeyboardSwitcher switcher = latinIme.mKeyboardSwitcher;
            switch (msg.what) {
            case MSG_UPDATE_SUGGESTION_STRIP:
                cancelUpdateSuggestionStrip();
                latinIme.mInputLogic.performUpdateSuggestionStripSync(
                        latinIme.mSettings.getCurrent());
                break;
            case MSG_UPDATE_SHIFT_STATE:
                switcher.requestUpdatingShiftState(latinIme.getCurrentAutoCapsState(),
                        latinIme.getCurrentRecapitalizeState());
                break;
            case MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP:
                if (msg.arg1 == ARG1_NOT_GESTURE_INPUT) {
                    final SuggestedWords suggestedWords = (SuggestedWords) msg.obj;
                    latinIme.showSuggestionStrip(suggestedWords);
                } else {
                    latinIme.showGesturePreviewAndSuggestionStrip((SuggestedWords) msg.obj,
                            msg.arg1 == ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT);
                }
                break;
            case MSG_RESUME_SUGGESTIONS:
                latinIme.mInputLogic.restartSuggestionsOnWordTouchedByCursor(
                        latinIme.mSettings.getCurrent(),
                        msg.arg1 == ARG1_TRUE /* shouldIncludeResumedWordInSuggestions */,
                        latinIme.mKeyboardSwitcher.getCurrentKeyboardScriptId());
                break;
            case MSG_REOPEN_DICTIONARIES:
                // We need to re-evaluate the currently composing word in case the script has
                // changed.
                postWaitForDictionaryLoad();
                latinIme.resetSuggest();
                break;
            case MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED:
                latinIme.mInputLogic.onUpdateTailBatchInputCompleted(
                        latinIme.mSettings.getCurrent(),
                        (SuggestedWords) msg.obj, latinIme.mKeyboardSwitcher);
                break;
            case MSG_RESET_CACHES:
                final SettingsValues settingsValues = latinIme.mSettings.getCurrent();
                if (latinIme.mInputLogic.retryResetCachesAndReturnSuccess(
                        msg.arg1 == 1 /* tryResumeSuggestions */,
                        msg.arg2 /* remainingTries */, this /* handler */)) {
                    // If we were able to reset the caches, then we can reload the keyboard.
                    // Otherwise, we'll do it when we can.
                    latinIme.mKeyboardSwitcher.loadKeyboard(latinIme.getCurrentInputEditorInfo(),
                            settingsValues, latinIme.getCurrentAutoCapsState(),
                            latinIme.getCurrentRecapitalizeState());
                }
                break;
            case MSG_WAIT_FOR_DICTIONARY_LOAD:
                Log.i(TAG, "Timeout waiting for dictionary load");
                break;
            }
        }

        public void postUpdateSuggestionStrip() {
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTION_STRIP), mDelayUpdateSuggestions);
        }

        public void postReopenDictionaries() {
            sendMessage(obtainMessage(MSG_REOPEN_DICTIONARIES));
        }

        public void postResumeSuggestions(final boolean shouldIncludeResumedWordInSuggestions,
                final boolean shouldDelay) {
            final LatinIME latinIme = getOwnerInstance();
            if (latinIme == null) {
                return;
            }
            if (!latinIme.mSettings.getCurrent()
                    .isSuggestionsEnabledPerUserSettings()) {
                return;
            }
            removeMessages(MSG_RESUME_SUGGESTIONS);
            if (shouldDelay) {
                sendMessageDelayed(obtainMessage(MSG_RESUME_SUGGESTIONS,
                                shouldIncludeResumedWordInSuggestions ? ARG1_TRUE : ARG1_FALSE,
                                0 /* ignored */),
                        mDelayUpdateSuggestions);
            } else {
                sendMessage(obtainMessage(MSG_RESUME_SUGGESTIONS,
                        shouldIncludeResumedWordInSuggestions ? ARG1_TRUE : ARG1_FALSE,
                        0 /* ignored */));
            }
        }

        public void postResetInputConnectionCaches(final boolean tryResumeSuggestions,
                final int remainingTries) {
            removeMessages(MSG_RESET_CACHES);
            sendMessage(obtainMessage(MSG_RESET_CACHES, tryResumeSuggestions ? 1 : 0,
                    remainingTries, null));
        }

        public void postWaitForDictionaryLoad() {
            sendMessageDelayed(obtainMessage(MSG_WAIT_FOR_DICTIONARY_LOAD),
                    DELAY_WAIT_FOR_DICTIONARY_LOAD);
        }

        public void cancelWaitForDictionaryLoad() {
            removeMessages(MSG_WAIT_FOR_DICTIONARY_LOAD);
        }

        public boolean hasPendingWaitForDictionaryLoad() {
            return hasMessages(MSG_WAIT_FOR_DICTIONARY_LOAD);
        }

        public void cancelUpdateSuggestionStrip() {
            removeMessages(MSG_UPDATE_SUGGESTION_STRIP);
        }

        public boolean hasPendingUpdateSuggestions() {
            return hasMessages(MSG_UPDATE_SUGGESTION_STRIP);
        }

        public boolean hasPendingReopenDictionaries() {
            return hasMessages(MSG_REOPEN_DICTIONARIES);
        }

        public void postUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SHIFT_STATE), mDelayUpdateShiftState);
        }

        @UsedForTesting
        public void removeAllMessages() {
            for (int i = 0; i <= MSG_LAST; ++i) {
                removeMessages(i);
            }
        }

        public void showGesturePreviewAndSuggestionStrip(final SuggestedWords suggestedWords,
                final boolean dismissGestureFloatingPreviewText) {
            removeMessages(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP);
            final int arg1 = dismissGestureFloatingPreviewText
                    ? ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT
                    : ARG1_SHOW_GESTURE_FLOATING_PREVIEW_TEXT;
            obtainMessage(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP, arg1,
                    ARG2_UNUSED, suggestedWords).sendToTarget();
        }

        public void showSuggestionStrip(final SuggestedWords suggestedWords) {
            removeMessages(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP);
            obtainMessage(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP,
                    ARG1_NOT_GESTURE_INPUT, ARG2_UNUSED, suggestedWords).sendToTarget();
        }

        public void showTailBatchInputResult(final SuggestedWords suggestedWords) {
            obtainMessage(MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED, suggestedWords).sendToTarget();
        }

        // Working variables for the following methods.
        private boolean mIsOrientationChanging;
        private boolean mPendingSuccessiveImsCallback;
        private boolean mHasPendingStartInput;
        private boolean mHasPendingFinishInputView;
        private boolean mHasPendingFinishInput;
        private EditorInfo mAppliedEditorInfo;

        public void startOrientationChanging() {
            removeMessages(MSG_PENDING_IMS_CALLBACK);
            resetPendingImsCallback();
            mIsOrientationChanging = true;
            final LatinIME latinIme = getOwnerInstance();
            if (latinIme == null) {
                return;
            }
            if (latinIme.isInputViewShown()) {
                latinIme.mKeyboardSwitcher.saveKeyboardState();
            }
        }

        private void resetPendingImsCallback() {
            mHasPendingFinishInputView = false;
            mHasPendingFinishInput = false;
            mHasPendingStartInput = false;
        }

        private void executePendingImsCallback(final LatinIME latinIme, final EditorInfo editorInfo,
                boolean restarting) {
            if (mHasPendingFinishInputView) {
                latinIme.onFinishInputViewInternal(mHasPendingFinishInput);
            }
            if (mHasPendingFinishInput) {
                latinIme.onFinishInputInternal();
            }
            if (mHasPendingStartInput) {
                latinIme.onStartInputInternal(editorInfo, restarting);
            }
            resetPendingImsCallback();
        }

        public void onStartInput(final EditorInfo editorInfo, final boolean restarting) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the second onStartInput after orientation changed.
                mHasPendingStartInput = true;
            } else {
                if (mIsOrientationChanging && restarting) {
                    // This is the first onStartInput after orientation changed.
                    mIsOrientationChanging = false;
                    mPendingSuccessiveImsCallback = true;
                }
                final LatinIME latinIme = getOwnerInstance();
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting);
                    latinIme.onStartInputInternal(editorInfo, restarting);
                    if (ProductionFlags.ENABLE_CURSOR_RECT_CALLBACK) {
                        InputConnectionCompatUtils.requestCursorRect(
                                latinIme.getCurrentInputConnection(), true /* enableMonitor */);
                    }
                    if (ProductionFlags.ENABLE_CURSOR_ANCHOR_INFO_CALLBACK) {
                        InputConnectionCompatUtils.requestCursorAnchorInfo(
                                latinIme.getCurrentInputConnection(), true /* enableMonitor */,
                                true /* requestImmediateCallback */);
                    }
                }
            }
        }

        public void onStartInputView(final EditorInfo editorInfo, final boolean restarting) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)
                    && KeyboardId.equivalentEditorInfoForKeyboard(editorInfo, mAppliedEditorInfo)) {
                // Typically this is the second onStartInputView after orientation changed.
                resetPendingImsCallback();
            } else {
                if (mPendingSuccessiveImsCallback) {
                    // This is the first onStartInputView after orientation changed.
                    mPendingSuccessiveImsCallback = false;
                    resetPendingImsCallback();
                    sendMessageDelayed(obtainMessage(MSG_PENDING_IMS_CALLBACK),
                            PENDING_IMS_CALLBACK_DURATION);
                }
                final LatinIME latinIme = getOwnerInstance();
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting);
                    latinIme.onStartInputViewInternal(editorInfo, restarting);
                    mAppliedEditorInfo = editorInfo;
                }
            }
        }

        public void onFinishInputView(final boolean finishingInput) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInputView after orientation changed.
                mHasPendingFinishInputView = true;
            } else {
                final LatinIME latinIme = getOwnerInstance();
                if (latinIme != null) {
                    latinIme.onFinishInputViewInternal(finishingInput);
                    mAppliedEditorInfo = null;
                }
            }
        }

        public void onFinishInput() {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInput after orientation changed.
                mHasPendingFinishInput = true;
            } else {
                final LatinIME latinIme = getOwnerInstance();
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, null, false);
                    latinIme.onFinishInputInternal();
                }
            }
        }
    }

    static final class SubtypeState {
        private InputMethodSubtype mLastActiveSubtype;
        private boolean mCurrentSubtypeHasBeenUsed;

        public void setCurrentSubtypeHasBeenUsed() {
            mCurrentSubtypeHasBeenUsed = true;
        }

        public void switchSubtype(final IBinder token, final RichInputMethodManager richImm) {
            final InputMethodSubtype currentSubtype = richImm.getInputMethodManager()
                    .getCurrentInputMethodSubtype();
            final InputMethodSubtype lastActiveSubtype = mLastActiveSubtype;
            final boolean currentSubtypeHasBeenUsed = mCurrentSubtypeHasBeenUsed;
            if (currentSubtypeHasBeenUsed) {
                mLastActiveSubtype = currentSubtype;
                mCurrentSubtypeHasBeenUsed = false;
            }
            if (currentSubtypeHasBeenUsed
                    && richImm.checkIfSubtypeBelongsToThisImeAndEnabled(lastActiveSubtype)
                    && !currentSubtype.equals(lastActiveSubtype)) {
                richImm.setInputMethodAndSubtype(token, lastActiveSubtype);
                return;
            }
            richImm.switchToNextInputMethod(token, true /* onlyCurrentIme */);
        }
    }

    // Loading the native library eagerly to avoid unexpected UnsatisfiedLinkError at the initial
    // JNI call as much as possible.
    static {
        JniUtils.loadNativeLibrary();
    }

    public LatinIME() {
        super();
        mSettings = Settings.getInstance();
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mIsHardwareAcceleratedDrawingEnabled =
                InputMethodServiceCompatUtils.enableHardwareAcceleration(this);
        Log.i(TAG, "Hardware accelerated drawing: " + mIsHardwareAcceleratedDrawingEnabled);
    }

    @Override
    public void onCreate() {
        Settings.init(this);
        DebugFlags.init(PreferenceManager.getDefaultSharedPreferences(this));
        RichInputMethodManager.init(this);
        mRichImm = RichInputMethodManager.getInstance();
        SubtypeSwitcher.init(this);
        KeyboardSwitcher.init(this);
        AudioAndHapticFeedbackManager.init(this);
        AccessibilityUtils.init(this);
        StatsUtils.init(this);

        super.onCreate();

        mHandler.onCreate();
        DEBUG = DebugFlags.DEBUG_ENABLED;

        // TODO: Resolve mutual dependencies of {@link #loadSettings()} and {@link #initSuggest()}.
        loadSettings();
        resetSuggest();

        // Register to receive ringer mode change and network state change.
        // Also receive installation and removal of a dictionary pack.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mConnectivityAndRingerModeChangeReceiver, filter);

        final IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme(SCHEME_PACKAGE);
        registerReceiver(mDictionaryPackInstallReceiver, packageFilter);

        final IntentFilter newDictFilter = new IntentFilter();
        newDictFilter.addAction(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION);
        registerReceiver(mDictionaryPackInstallReceiver, newDictFilter);

        final IntentFilter dictDumpFilter = new IntentFilter();
        dictDumpFilter.addAction(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION);
        registerReceiver(mDictionaryDumpBroadcastReceiver, dictDumpFilter);

        DictionaryDecayBroadcastReciever.setUpIntervalAlarmForDictionaryDecaying(this);

        StatsUtils.onCreate(mSettings.getCurrent());
    }

    // Has to be package-visible for unit tests
    @UsedForTesting
    void loadSettings() {
        final Locale locale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        final EditorInfo editorInfo = getCurrentInputEditorInfo();
        final InputAttributes inputAttributes = new InputAttributes(
                editorInfo, isFullscreenMode(), getPackageName());
        mSettings.loadSettings(this, locale, inputAttributes);
        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        AudioAndHapticFeedbackManager.getInstance().onSettingsChanged(currentSettingsValues);
        // This method is called on startup and language switch, before the new layout has
        // been displayed. Opening dictionaries never affects responsivity as dictionaries are
        // asynchronously loaded.
        if (!mHandler.hasPendingReopenDictionaries()) {
            resetSuggestForLocale(locale);
        }
        mDictionaryFacilitator.updateEnabledSubtypes(mRichImm.getMyEnabledInputMethodSubtypeList(
                true /* allowsImplicitlySelectedSubtypes */));
        refreshPersonalizationDictionarySession(currentSettingsValues);
        StatsUtils.onLoadSettings(currentSettingsValues);
    }

    private void refreshPersonalizationDictionarySession(
            final SettingsValues currentSettingsValues) {
        mPersonalizationDictionaryUpdater.onLoadSettings(
                currentSettingsValues.mUsePersonalizedDicts,
                mSubtypeSwitcher.isSystemLocaleSameAsLocaleOfAllEnabledSubtypesOfEnabledImes());
        mContextualDictionaryUpdater.onLoadSettings(currentSettingsValues.mUsePersonalizedDicts);
        final boolean shouldKeepUserHistoryDictionaries;
        if (currentSettingsValues.mUsePersonalizedDicts) {
            shouldKeepUserHistoryDictionaries = true;
        } else {
            shouldKeepUserHistoryDictionaries = false;
        }
        if (!shouldKeepUserHistoryDictionaries) {
            // Remove user history dictionaries.
            PersonalizationHelper.removeAllUserHistoryDictionaries(this);
            mDictionaryFacilitator.clearUserHistoryDictionary();
        }
    }

    // Note that this method is called from a non-UI thread.
    @Override
    public void onUpdateMainDictionaryAvailability(final boolean isMainDictionaryAvailable) {
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.setMainDictionaryAvailability(isMainDictionaryAvailable);
        }
        if (mHandler.hasPendingWaitForDictionaryLoad()) {
            mHandler.cancelWaitForDictionaryLoad();
            mHandler.postResumeSuggestions(true /* shouldIncludeResumedWordInSuggestions */,
                    false /* shouldDelay */);
        }
    }

    private void resetSuggest() {
        final Locale switcherSubtypeLocale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        final String switcherLocaleStr = switcherSubtypeLocale.toString();
        final Locale subtypeLocale;
        if (TextUtils.isEmpty(switcherLocaleStr)) {
            // This happens in very rare corner cases - for example, immediately after a switch
            // to LatinIME has been requested, about a frame later another switch happens. In this
            // case, we are about to go down but we still don't know it, however the system tells
            // us there is no current subtype so the locale is the empty string. Take the best
            // possible guess instead -- it's bound to have no consequences, and we have no way
            // of knowing anyway.
            Log.e(TAG, "System is reporting no current subtype.");
            subtypeLocale = getResources().getConfiguration().locale;
        } else {
            subtypeLocale = switcherSubtypeLocale;
        }
        resetSuggestForLocale(subtypeLocale);
    }

    /**
     * Reset suggest by loading dictionaries for the locale and the current settings values.
     *
     * @param locale the locale
     */
    private void resetSuggestForLocale(final Locale locale) {
        final SettingsValues settingsValues = mSettings.getCurrent();
        mDictionaryFacilitator.resetDictionaries(this /* context */, locale,
                settingsValues.mUseContactsDict, settingsValues.mUsePersonalizedDicts,
                false /* forceReloadMainDictionary */, this);
        if (settingsValues.mAutoCorrectionEnabledPerUserSettings) {
            mInputLogic.mSuggest.setAutoCorrectionThreshold(
                    settingsValues.mAutoCorrectionThreshold);
        }
    }

    /**
     * Reset suggest by loading the main dictionary of the current locale.
     */
    /* package private */ void resetSuggestMainDict() {
        final SettingsValues settingsValues = mSettings.getCurrent();
        mDictionaryFacilitator.resetDictionaries(this /* context */,
                mDictionaryFacilitator.getLocale(), settingsValues.mUseContactsDict,
                settingsValues.mUsePersonalizedDicts, true /* forceReloadMainDictionary */, this);
    }

    @Override
    public void onDestroy() {
        mDictionaryFacilitator.closeDictionaries();
        mPersonalizationDictionaryUpdater.onDestroy();
        mContextualDictionaryUpdater.onDestroy();
        mSettings.onDestroy();
        unregisterReceiver(mConnectivityAndRingerModeChangeReceiver);
        unregisterReceiver(mDictionaryPackInstallReceiver);
        unregisterReceiver(mDictionaryDumpBroadcastReceiver);
        StatsUtils.onDestroy();
        super.onDestroy();
    }

    @UsedForTesting
    public void recycle() {
        unregisterReceiver(mDictionaryPackInstallReceiver);
        unregisterReceiver(mDictionaryDumpBroadcastReceiver);
        unregisterReceiver(mConnectivityAndRingerModeChangeReceiver);
        mInputLogic.recycle();
    }

    @Override
    public void onConfigurationChanged(final Configuration conf) {
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (settingsValues.mDisplayOrientation != conf.orientation) {
            mHandler.startOrientationChanging();
            mInputLogic.onOrientationChange(mSettings.getCurrent());
        }
        // TODO: Remove this test.
        if (!conf.locale.equals(mPersonalizationDictionaryUpdater.getLocale())) {
            refreshPersonalizationDictionarySession(settingsValues);
        }
        super.onConfigurationChanged(conf);
    }

    @Override
    public View onCreateInputView() {
        return mKeyboardSwitcher.onCreateInputView(mIsHardwareAcceleratedDrawingEnabled);
    }

    @Override
    public void setInputView(final View view) {
        super.setInputView(view);
        mExtractArea = getWindow().getWindow().getDecorView()
                .findViewById(android.R.id.extractArea);
        mKeyPreviewBackingView = view.findViewById(R.id.key_preview_backing);
        mSuggestionStripView = (SuggestionStripView)view.findViewById(R.id.suggestion_strip_view);
        if (hasSuggestionStripView()) {
            mSuggestionStripView.setListener(this, view);
        }
    }

    @Override
    public void setCandidatesView(final View view) {
        // To ensure that CandidatesView will never be set.
        return;
    }

    @Override
    public void onStartInput(final EditorInfo editorInfo, final boolean restarting) {
        mHandler.onStartInput(editorInfo, restarting);
    }

    @Override
    public void onStartInputView(final EditorInfo editorInfo, final boolean restarting) {
        mHandler.onStartInputView(editorInfo, restarting);
    }

    @Override
    public void onFinishInputView(final boolean finishingInput) {
        mHandler.onFinishInputView(finishingInput);
    }

    @Override
    public void onFinishInput() {
        mHandler.onFinishInput();
    }

    @Override
    public void onCurrentInputMethodSubtypeChanged(final InputMethodSubtype subtype) {
        // Note that the calling sequence of onCreate() and onCurrentInputMethodSubtypeChanged()
        // is not guaranteed. It may even be called at the same time on a different thread.
        mSubtypeSwitcher.onSubtypeChanged(subtype);
        mInputLogic.onSubtypeChanged(SubtypeLocaleUtils.getCombiningRulesExtraValue(subtype));
        loadKeyboard();
    }

    /**
     * A class that holds information to pass from onStartInputInternal to onStartInputViewInternal
     *
     * OnStartInput needs to reload the settings and that will prevent onStartInputViewInternal
     * from comparing the old settings with the new ones, so we use this memory to pass the
     * necessary information along.
     */
    private static class EditorChangeInfo {
        public final boolean mIsSameInputType;
        public final boolean mHasSameOrientation;
        public final boolean mCanReachInputConnection;
        public EditorChangeInfo(final boolean isSameInputType, final boolean hasSameOrientation,
                final boolean canReachInputConnection) {
            mIsSameInputType = isSameInputType;
            mHasSameOrientation = hasSameOrientation;
            mCanReachInputConnection = canReachInputConnection;
        }
    }

    private EditorChangeInfo mLastEditorChangeInfo;

    private void onStartInputInternal(final EditorInfo editorInfo, final boolean restarting) {
        super.onStartInput(editorInfo, restarting);
        if (editorInfo == null) {
            Log.e(TAG, "Null EditorInfo in onStartInput()");
            return;
        }
        SettingsValues currentSettingsValues = mSettings.getCurrent();
        final boolean isSameInputType = currentSettingsValues.isSameInputType(editorInfo);
        final boolean hasSameOrientation =
                currentSettingsValues.hasSameOrientation(getResources().getConfiguration());
        mRichImm.clearSubtypeCaches();
        final boolean inputTypeChanged = !isSameInputType;
        final boolean isDifferentTextField = !restarting || inputTypeChanged;
        if (isDifferentTextField || !hasSameOrientation) {
            loadSettings();
            currentSettingsValues = mSettings.getCurrent();
        }

        // Note: the following does a round-trip IPC on the main thread: be careful
        final Locale currentLocale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        final Suggest suggest = mInputLogic.mSuggest;
        if (null != currentLocale && !currentLocale.equals(suggest.getLocale())) {
            // TODO: Do this automatically.
            resetSuggest();
        }
        if (isDifferentTextField && currentSettingsValues.mAutoCorrectionEnabledPerUserSettings) {
            suggest.setAutoCorrectionThreshold(currentSettingsValues.mAutoCorrectionThreshold);
        }

        // The app calling setText() has the effect of clearing the composing
        // span, so we should reset our state unconditionally, even if restarting is true.
        // We also tell the input logic about the combining rules for the current subtype, so
        // it can adjust its combiners if needed.
        mInputLogic.startInput(mSubtypeSwitcher.getCombiningRulesExtraValueOfCurrentSubtype());
        // TODO[IL]: Can the following be moved to InputLogic#startInput?
        final boolean canReachInputConnection;
        if (!mInputLogic.mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                editorInfo.initialSelStart, editorInfo.initialSelEnd,
                false /* shouldFinishComposition */)) {
            // Sometimes, while rotating, for some reason the framework tells the app we are not
            // connected to it and that means we can't refresh the cache. In this case, schedule a
            // refresh later.
            // We try resetting the caches up to 5 times before giving up.
            mHandler.postResetInputConnectionCaches(isDifferentTextField || !hasSameOrientation,
                    5 /* remainingTries */);
            canReachInputConnection = false;
        } else {
            // When rotating, initialSelStart and initialSelEnd sometimes are lying. Make a best
            // effort to work around this bug.
            mInputLogic.mConnection.tryFixLyingCursorPosition();
            mHandler.postResumeSuggestions(true /* shouldIncludeResumedWordInSuggestions */,
                    true /* shouldDelay */);
            canReachInputConnection = true;
        }

        mLastEditorChangeInfo = new EditorChangeInfo(isSameInputType, hasSameOrientation,
                canReachInputConnection);
    }

    @SuppressWarnings("deprecation")
    private void onStartInputViewInternal(final EditorInfo editorInfo, final boolean restarting) {
        super.onStartInputView(editorInfo, restarting);
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        switcher.updateKeyboardTheme();
        final MainKeyboardView mainKeyboardView = switcher.getMainKeyboardView();

        if (editorInfo == null) {
            Log.e(TAG, "Null EditorInfo in onStartInputView()");
            if (DebugFlags.DEBUG_ENABLED) {
                throw new NullPointerException("Null EditorInfo in onStartInputView()");
            }
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onStartInputView: editorInfo:"
                    + String.format("inputType=0x%08x imeOptions=0x%08x",
                            editorInfo.inputType, editorInfo.imeOptions));
            Log.d(TAG, "All caps = "
                    + ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0)
                    + ", sentence caps = "
                    + ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0)
                    + ", word caps = "
                    + ((editorInfo.inputType & InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0));
        }
        Log.i(TAG, "Starting input. Cursor position = "
                + editorInfo.initialSelStart + "," + editorInfo.initialSelEnd);
        // TODO: Consolidate these checks with {@link InputAttributes}.
        if (InputAttributes.inPrivateImeOptions(null, NO_MICROPHONE_COMPAT, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: " + editorInfo.privateImeOptions);
            Log.w(TAG, "Use " + getPackageName() + "." + NO_MICROPHONE + " instead");
        }
        if (InputAttributes.inPrivateImeOptions(getPackageName(), FORCE_ASCII, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: " + editorInfo.privateImeOptions);
            Log.w(TAG, "Use EditorInfo.IME_FLAG_FORCE_ASCII flag instead");
        }

        // In landscape mode, this method gets called without the input view being created.
        if (mainKeyboardView == null) {
            return;
        }

        // Forward this event to the accessibility utilities, if enabled.
        final AccessibilityUtils accessUtils = AccessibilityUtils.getInstance();
        if (accessUtils.isTouchExplorationEnabled()) {
            accessUtils.onStartInputViewInternal(mainKeyboardView, editorInfo, restarting);
        }

        final boolean inputTypeChanged = !mLastEditorChangeInfo.mIsSameInputType;
        final boolean isDifferentTextField = !restarting || inputTypeChanged;
        if (isDifferentTextField) {
            mSubtypeSwitcher.updateParametersOnStartInputView();
        }

        // The EditorInfo might have a flag that affects fullscreen mode.
        // Note: This call should be done by InputMethodService?
        updateFullscreenMode();

        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        if (isDifferentTextField) {
            mainKeyboardView.closing();

            switcher.loadKeyboard(editorInfo, currentSettingsValues, getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
            if (!mLastEditorChangeInfo.mCanReachInputConnection) {
                // If we can't reach the input connection, we will call loadKeyboard again later,
                // so we need to save its state now. The call will be done in #retryResetCaches.
                switcher.saveKeyboardState();
            }
        } else if (restarting) {
            // TODO: Come up with a more comprehensive way to reset the keyboard layout when
            // a keyboard layout set doesn't get reloaded in this method.
            switcher.resetKeyboardStateToAlphabet(getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
            // In apps like Talk, we come here when the text is sent and the field gets emptied and
            // we need to re-evaluate the shift state, but not the whole layout which would be
            // disruptive.
            // Space state must be updated before calling updateShiftState
            switcher.requestUpdatingShiftState(getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
        }
        // This will set the punctuation suggestions if next word suggestion is off;
        // otherwise it will clear the suggestion strip.
        setNeutralSuggestionStrip();

        mHandler.cancelUpdateSuggestionStrip();

        mainKeyboardView.setMainDictionaryAvailability(
                mDictionaryFacilitator.hasInitializedMainDictionary());
        mainKeyboardView.setKeyPreviewPopupEnabled(currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay);
        mainKeyboardView.setSlidingKeyInputPreviewEnabled(
                currentSettingsValues.mSlidingKeyInputPreviewEnabled);
        mainKeyboardView.setGestureHandlingEnabledByUser(
                currentSettingsValues.mGestureInputEnabled,
                currentSettingsValues.mGestureTrailEnabled,
                currentSettingsValues.mGestureFloatingPreviewTextEnabled);

        // Contextual dictionary should be updated for the current application.
        mContextualDictionaryUpdater.onStartInputView(editorInfo.packageName);
        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
    }

    private void onFinishInputInternal() {
        super.onFinishInput();

        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
    }

    private void onFinishInputViewInternal(final boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        mKeyboardSwitcher.deallocateMemory();
        // Remove pending messages related to update suggestions
        mHandler.cancelUpdateSuggestionStrip();
        // Should do the following in onFinishInputInternal but until JB MR2 it's not called :(
        mInputLogic.finishInput();
    }

    @Override
    public void onUpdateSelection(final int oldSelStart, final int oldSelEnd,
            final int newSelStart, final int newSelEnd,
            final int composingSpanStart, final int composingSpanEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                composingSpanStart, composingSpanEnd);
        if (DEBUG) {
            Log.i(TAG, "onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
                    + ", nss=" + newSelStart + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart + ", ce=" + composingSpanEnd);
        }

        // If the keyboard is not visible, we don't need to do all the housekeeping work, as it
        // will be reset when the keyboard shows up anyway.
        // TODO: revisit this when LatinIME supports hardware keyboards.
        // NOTE: the test harness subclasses LatinIME and overrides isInputViewShown().
        // TODO: find a better way to simulate actual execution.
        if (isInputViewShown() &&
                mInputLogic.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd)) {
            mKeyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
        }
    }

    @Override
    public void onUpdateCursor(final Rect rect) {
        if (DEBUG) {
            Log.i(TAG, "onUpdateCursor:" + rect.toShortString());
        }
        super.onUpdateCursor(rect);
    }

    /**
     * This is called when the user has clicked on the extracted text view,
     * when running in fullscreen mode.  The default implementation hides
     * the suggestions view when this happens, but only if the extracted text
     * editor has a vertical scroll bar because its text doesn't fit.
     * Here we override the behavior due to the possibility that a re-correction could
     * cause the suggestions strip to disappear and re-appear.
     */
    @Override
    public void onExtractedTextClicked() {
        if (mSettings.getCurrent().needsToLookupSuggestions()) {
            return;
        }

        super.onExtractedTextClicked();
    }

    /**
     * This is called when the user has performed a cursor movement in the
     * extracted text view, when it is running in fullscreen mode.  The default
     * implementation hides the suggestions view when a vertical movement
     * happens, but only if the extracted text editor has a vertical scroll bar
     * because its text doesn't fit.
     * Here we override the behavior due to the possibility that a re-correction could
     * cause the suggestions strip to disappear and re-appear.
     */
    @Override
    public void onExtractedCursorMovement(final int dx, final int dy) {
        if (mSettings.getCurrent().needsToLookupSuggestions()) {
            return;
        }

        super.onExtractedCursorMovement(dx, dy);
    }

    @Override
    public void hideWindow() {
        mKeyboardSwitcher.onHideWindow();

        if (TRACE) Debug.stopMethodTracing();
        if (isShowingOptionDialog()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
        super.hideWindow();
    }

    @Override
    public void onDisplayCompletions(final CompletionInfo[] applicationSpecifiedCompletions) {
        if (DEBUG) {
            Log.i(TAG, "Received completions:");
            if (applicationSpecifiedCompletions != null) {
                for (int i = 0; i < applicationSpecifiedCompletions.length; i++) {
                    Log.i(TAG, "  #" + i + ": " + applicationSpecifiedCompletions[i]);
                }
            }
        }
        if (!mSettings.getCurrent().isApplicationSpecifiedCompletionsOn()) {
            return;
        }
        // If we have an update request in flight, we need to cancel it so it does not override
        // these completions.
        mHandler.cancelUpdateSuggestionStrip();
        if (applicationSpecifiedCompletions == null) {
            setNeutralSuggestionStrip();
            return;
        }

        final ArrayList<SuggestedWords.SuggestedWordInfo> applicationSuggestedWords =
                SuggestedWords.getFromApplicationSpecifiedCompletions(
                        applicationSpecifiedCompletions);
        final SuggestedWords suggestedWords = new SuggestedWords(applicationSuggestedWords,
                null /* rawSuggestions */, false /* typedWordValid */, false /* willAutoCorrect */,
                false /* isObsoleteSuggestions */, false /* isPrediction */);
        // When in fullscreen mode, show completions generated by the application forcibly
        setSuggestedWords(suggestedWords);
    }

    private int getAdjustedBackingViewHeight() {
        final int currentHeight = mKeyPreviewBackingView.getHeight();
        if (currentHeight > 0) {
            return currentHeight;
        }

        final View visibleKeyboardView = mKeyboardSwitcher.getVisibleKeyboardView();
        if (visibleKeyboardView == null) {
            return 0;
        }
        // TODO: !!!!!!!!!!!!!!!!!!!! Handle different backing view heights between the main   !!!
        // keyboard and the emoji keyboard. !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        final int keyboardHeight = visibleKeyboardView.getHeight();
        final int suggestionsHeight = mSuggestionStripView.getHeight();
        final int displayHeight = getResources().getDisplayMetrics().heightPixels;
        final Rect rect = new Rect();
        mKeyPreviewBackingView.getWindowVisibleDisplayFrame(rect);
        final int notificationBarHeight = rect.top;
        final int remainingHeight = displayHeight - notificationBarHeight - suggestionsHeight
                - keyboardHeight;

        final LayoutParams params = mKeyPreviewBackingView.getLayoutParams();
        params.height = mSuggestionStripView.setMoreSuggestionsHeight(remainingHeight);
        mKeyPreviewBackingView.setLayoutParams(params);
        return params.height;
    }

    @Override
    public void onComputeInsets(final InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        final View visibleKeyboardView = mKeyboardSwitcher.getVisibleKeyboardView();
        if (visibleKeyboardView == null || !hasSuggestionStripView()) {
            return;
        }
        final int adjustedBackingHeight = getAdjustedBackingViewHeight();
        final boolean backingGone = (mKeyPreviewBackingView.getVisibility() == View.GONE);
        final int backingHeight = backingGone ? 0 : adjustedBackingHeight;
        // In fullscreen mode, the height of the extract area managed by InputMethodService should
        // be considered.
        // See {@link android.inputmethodservice.InputMethodService#onComputeInsets}.
        final int extractHeight = isFullscreenMode() ? mExtractArea.getHeight() : 0;
        final int suggestionsHeight = (mSuggestionStripView.getVisibility() == View.GONE) ? 0
                : mSuggestionStripView.getHeight();
        final int extraHeight = extractHeight + backingHeight + suggestionsHeight;
        int visibleTopY = extraHeight;
        // Need to set touchable region only if input view is being shown
        if (visibleKeyboardView.isShown()) {
            // Note that the height of Emoji layout is the same as the height of the main keyboard
            // and the suggestion strip
            if (mKeyboardSwitcher.isShowingEmojiPalettes()
                    || mSuggestionStripView.getVisibility() == View.VISIBLE) {
                visibleTopY -= suggestionsHeight;
            }
            final int touchY = mKeyboardSwitcher.isShowingMoreKeysPanel() ? 0 : visibleTopY;
            final int touchWidth = visibleKeyboardView.getWidth();
            final int touchHeight = visibleKeyboardView.getHeight() + extraHeight
                    // Extend touchable region below the keyboard.
                    + EXTENDED_TOUCHABLE_REGION_HEIGHT;
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
            outInsets.touchableRegion.set(0, touchY, touchWidth, touchHeight);
        }
        outInsets.contentTopInsets = visibleTopY;
        outInsets.visibleTopInsets = visibleTopY;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // Reread resource value here, because this method is called by the framework as needed.
        final boolean isFullscreenModeAllowed = Settings.readUseFullscreenMode(getResources());
        if (super.onEvaluateFullscreenMode() && isFullscreenModeAllowed) {
            // TODO: Remove this hack. Actually we should not really assume NO_EXTRACT_UI
            // implies NO_FULLSCREEN. However, the framework mistakenly does.  i.e. NO_EXTRACT_UI
            // without NO_FULLSCREEN doesn't work as expected. Because of this we need this
            // hack for now.  Let's get rid of this once the framework gets fixed.
            final EditorInfo ei = getCurrentInputEditorInfo();
            return !(ei != null && ((ei.imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0));
        } else {
            return false;
        }
    }

    @Override
    public void updateFullscreenMode() {
        super.updateFullscreenMode();

        if (mKeyPreviewBackingView == null) return;
        // In fullscreen mode, no need to have extra space to show the key preview.
        // If not, we should have extra space above the keyboard to show the key preview.
        mKeyPreviewBackingView.setVisibility(isFullscreenMode() ? View.GONE : View.VISIBLE);
    }

    private int getCurrentAutoCapsState() {
        return mInputLogic.getCurrentAutoCapsState(mSettings.getCurrent());
    }

    private int getCurrentRecapitalizeState() {
        return mInputLogic.getCurrentRecapitalizeState();
    }

    public Locale getCurrentSubtypeLocale() {
        return mSubtypeSwitcher.getCurrentSubtypeLocale();
    }

    /**
     * @param codePoints code points to get coordinates for.
     * @return x,y coordinates for this keyboard, as a flattened array.
     */
    public int[] getCoordinatesForCurrentKeyboard(final int[] codePoints) {
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        if (null == keyboard) {
            return CoordinateUtils.newCoordinateArray(codePoints.length,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        } else {
            return keyboard.getCoordinates(codePoints);
        }
    }

    // Callback for the {@link SuggestionStripView}, to call when the "add to dictionary" hint is
    // pressed.
    @Override
    public void addWordToUserDictionary(final String word) {
        if (TextUtils.isEmpty(word)) {
            // Probably never supposed to happen, but just in case.
            return;
        }
        final String wordToEdit;
        if (CapsModeUtils.isAutoCapsMode(mInputLogic.mLastComposedWord.mCapitalizedMode)) {
            wordToEdit = word.toLowerCase(getCurrentSubtypeLocale());
        } else {
            wordToEdit = word;
        }
        mDictionaryFacilitator.addWordToUserDictionary(this /* context */, wordToEdit);
    }

    // Callback for the {@link SuggestionStripView}, to call when the important notice strip is
    // pressed.
    @Override
    public void showImportantNoticeContents() {
        showOptionDialog(new ImportantNoticeDialog(this /* context */, this /* listener */));
    }

    // Implement {@link ImportantNoticeDialog.ImportantNoticeDialogListener}
    @Override
    public void onClickSettingsOfImportantNoticeDialog(final int nextVersion) {
        launchSettings();
    }

    // Implement {@link ImportantNoticeDialog.ImportantNoticeDialogListener}
    @Override
    public void onUserAcknowledgmentOfImportantNoticeDialog(final int nextVersion) {
        setNeutralSuggestionStrip();
    }

    public void displaySettingsDialog() {
        if (isShowingOptionDialog()) {
            return;
        }
        showSubtypeSelectorAndSettings();
    }

    @Override
    public boolean onCustomRequest(final int requestCode) {
        if (isShowingOptionDialog()) return false;
        switch (requestCode) {
        case Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER:
            if (mRichImm.hasMultipleEnabledIMEsOrSubtypes(true /* include aux subtypes */)) {
                mRichImm.getInputMethodManager().showInputMethodPicker();
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean isShowingOptionDialog() {
        return mOptionsDialog != null && mOptionsDialog.isShowing();
    }

    // TODO: Revise the language switch key behavior to make it much smarter and more reasonable.
    public void switchToNextSubtype() {
        final IBinder token = getWindow().getWindow().getAttributes().token;
        if (shouldSwitchToOtherInputMethods()) {
            mRichImm.switchToNextInputMethod(token, false /* onlyCurrentIme */);
            return;
        }
        mSubtypeState.switchSubtype(token, mRichImm);
    }

    // Implementation of {@link KeyboardActionListener}.
    @Override
    public void onCodeInput(final int codePoint, final int x, final int y,
            final boolean isKeyRepeat) {
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        // x and y include some padding, but everything down the line (especially native
        // code) needs the coordinates in the keyboard frame.
        // TODO: We should reconsider which coordinate system should be used to represent
        // keyboard event. Also we should pull this up -- LatinIME has no business doing
        // this transformation, it should be done already before calling onCodeInput.
        final int keyX = mainKeyboardView.getKeyX(x);
        final int keyY = mainKeyboardView.getKeyY(y);
        final int codeToSend;
        if (Constants.CODE_SHIFT == codePoint) {
            // TODO: Instead of checking for alphabetic keyboard here, separate keycodes for
            // alphabetic shift and shift while in symbol layout.
            final Keyboard currentKeyboard = mKeyboardSwitcher.getKeyboard();
            if (null != currentKeyboard && currentKeyboard.mId.isAlphabetKeyboard()) {
                codeToSend = codePoint;
            } else {
                codeToSend = Constants.CODE_SYMBOL_SHIFT;
            }
        } else {
            codeToSend = codePoint;
        }
        if (Constants.CODE_SHORTCUT == codePoint) {
            mSubtypeSwitcher.switchToShortcutIME(this);
            // Still call the *#onCodeInput methods for readability.
        }
        final Event event = createSoftwareKeypressEvent(codeToSend, keyX, keyY, isKeyRepeat);
        final InputTransaction completeInputTransaction =
                mInputLogic.onCodeInput(mSettings.getCurrent(), event,
                        mKeyboardSwitcher.getKeyboardShiftMode(),
                        mKeyboardSwitcher.getCurrentKeyboardScriptId(), mHandler);
        updateStateAfterInputTransaction(completeInputTransaction);
        mKeyboardSwitcher.onCodeInput(codePoint, getCurrentAutoCapsState(),
                getCurrentRecapitalizeState());
    }

    // A helper method to split the code point and the key code. Ultimately, they should not be
    // squashed into the same variable, and this method should be removed.
    private static Event createSoftwareKeypressEvent(final int keyCodeOrCodePoint, final int keyX,
             final int keyY, final boolean isKeyRepeat) {
        final int keyCode;
        final int codePoint;
        if (keyCodeOrCodePoint <= 0) {
            keyCode = keyCodeOrCodePoint;
            codePoint = Event.NOT_A_CODE_POINT;
        } else {
            keyCode = Event.NOT_A_KEY_CODE;
            codePoint = keyCodeOrCodePoint;
        }
        return Event.createSoftwareKeypressEvent(codePoint, keyCode, keyX, keyY, isKeyRepeat);
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onTextInput(final String rawText) {
        // TODO: have the keyboard pass the correct key code when we need it.
        final Event event = Event.createSoftwareTextEvent(rawText, Event.NOT_A_KEY_CODE);
        final InputTransaction completeInputTransaction =
                mInputLogic.onTextInput(mSettings.getCurrent(), event,
                        mKeyboardSwitcher.getKeyboardShiftMode(), mHandler);
        updateStateAfterInputTransaction(completeInputTransaction);
        mKeyboardSwitcher.onCodeInput(Constants.CODE_OUTPUT_TEXT, getCurrentAutoCapsState(),
                getCurrentRecapitalizeState());
    }

    @Override
    public void onStartBatchInput() {
        mInputLogic.onStartBatchInput(mSettings.getCurrent(), mKeyboardSwitcher, mHandler);
    }

    @Override
    public void onUpdateBatchInput(final InputPointers batchPointers) {
        mInputLogic.onUpdateBatchInput(mSettings.getCurrent(), batchPointers, mKeyboardSwitcher);
    }

    @Override
    public void onEndBatchInput(final InputPointers batchPointers) {
        mInputLogic.onEndBatchInput(batchPointers);
    }

    @Override
    public void onCancelBatchInput() {
        mInputLogic.onCancelBatchInput(mHandler);
    }

    // This method must run on the UI Thread.
    private void showGesturePreviewAndSuggestionStrip(final SuggestedWords suggestedWords,
            final boolean dismissGestureFloatingPreviewText) {
        showSuggestionStrip(suggestedWords);
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        mainKeyboardView.showGestureFloatingPreviewText(suggestedWords);
        if (dismissGestureFloatingPreviewText) {
            mainKeyboardView.dismissGestureFloatingPreviewText();
        }
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onFinishSlidingInput() {
        // User finished sliding input.
        mKeyboardSwitcher.onFinishSlidingInput(getCurrentAutoCapsState(),
                getCurrentRecapitalizeState());
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onCancelInput() {
        // User released a finger outside any key
        // Nothing to do so far.
    }

    public boolean hasSuggestionStripView() {
        return null != mSuggestionStripView;
    }

    @Override
    public boolean isShowingAddToDictionaryHint() {
        return hasSuggestionStripView() && mSuggestionStripView.isShowingAddToDictionaryHint();
    }

    @Override
    public void dismissAddToDictionaryHint() {
        if (!hasSuggestionStripView()) {
            return;
        }
        mSuggestionStripView.dismissAddToDictionaryHint();
    }

    private void setSuggestedWords(final SuggestedWords suggestedWords) {
        mInputLogic.setSuggestedWords(suggestedWords);
        // TODO: Modify this when we support suggestions with hard keyboard
        if (!hasSuggestionStripView()) {
            return;
        }
        if (!onEvaluateInputViewShown()) {
            return;
        }

        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        final boolean shouldShowImportantNotice =
                ImportantNoticeUtils.shouldShowImportantNotice(this);
        final boolean shouldShowSuggestionCandidates =
                currentSettingsValues.mInputAttributes.mShouldShowSuggestions
                && currentSettingsValues.isSuggestionsEnabledPerUserSettings();
        final boolean shouldShowSuggestionsStripUnlessPassword = shouldShowImportantNotice
                || currentSettingsValues.mShowsVoiceInputKey
                || shouldShowSuggestionCandidates
                || currentSettingsValues.isApplicationSpecifiedCompletionsOn();
        final boolean shouldShowSuggestionsStrip = shouldShowSuggestionsStripUnlessPassword
                && !currentSettingsValues.mInputAttributes.mIsPasswordField;
        mSuggestionStripView.updateVisibility(shouldShowSuggestionsStrip, isFullscreenMode());
        if (!shouldShowSuggestionsStrip) {
            return;
        }

        final boolean isEmptyApplicationSpecifiedCompletions =
                currentSettingsValues.isApplicationSpecifiedCompletionsOn()
                && suggestedWords.isEmpty();
        final boolean noSuggestionsToShow = (SuggestedWords.EMPTY == suggestedWords)
                || suggestedWords.isPunctuationSuggestions()
                || isEmptyApplicationSpecifiedCompletions;
        if (shouldShowImportantNotice && noSuggestionsToShow) {
            if (mSuggestionStripView.maybeShowImportantNoticeTitle()) {
                return;
            }
        }

        if (currentSettingsValues.isSuggestionsEnabledPerUserSettings()
                // We should clear suggestions if there is no suggestion to show.
                || noSuggestionsToShow
                || currentSettingsValues.isApplicationSpecifiedCompletionsOn()) {
            mSuggestionStripView.setSuggestions(suggestedWords,
                    SubtypeLocaleUtils.isRtlLanguage(mSubtypeSwitcher.getCurrentSubtype()));
        }
    }

    // TODO[IL]: Move this out of LatinIME.
    public void getSuggestedWords(final int sessionId, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        if (keyboard == null) {
            callback.onGetSuggestedWords(SuggestedWords.EMPTY);
            return;
        }
        mInputLogic.getSuggestedWords(mSettings.getCurrent(), keyboard.getProximityInfo(),
                mKeyboardSwitcher.getKeyboardShiftMode(), sessionId, sequenceNumber, callback);
    }

    @Override
    public void showSuggestionStrip(final SuggestedWords sourceSuggestedWords) {
        final SuggestedWords suggestedWords =
                sourceSuggestedWords.isEmpty() ? SuggestedWords.EMPTY : sourceSuggestedWords;
        if (SuggestedWords.EMPTY == suggestedWords) {
            setNeutralSuggestionStrip();
        } else {
            setSuggestedWords(suggestedWords);
        }
        // Cache the auto-correction in accessibility code so we can speak it if the user
        // touches a key that will insert it.
        AccessibilityUtils.getInstance().setAutoCorrection(suggestedWords,
                sourceSuggestedWords.mTypedWord);
    }

    // Called from {@link SuggestionStripView} through the {@link SuggestionStripView#Listener}
    // interface
    @Override
    public void pickSuggestionManually(final SuggestedWordInfo suggestionInfo) {
        final InputTransaction completeInputTransaction = mInputLogic.onPickSuggestionManually(
                mSettings.getCurrent(), suggestionInfo,
                mKeyboardSwitcher.getKeyboardShiftMode(),
                mKeyboardSwitcher.getCurrentKeyboardScriptId(),
                mHandler);
        updateStateAfterInputTransaction(completeInputTransaction);
    }

    @Override
    public void showAddToDictionaryHint(final String word) {
        if (!hasSuggestionStripView()) {
            return;
        }
        mSuggestionStripView.showAddToDictionaryHint(word);
    }

    // This will show either an empty suggestion strip (if prediction is enabled) or
    // punctuation suggestions (if it's disabled).
    @Override
    public void setNeutralSuggestionStrip() {
        final SettingsValues currentSettings = mSettings.getCurrent();
        final SuggestedWords neutralSuggestions = currentSettings.mBigramPredictionEnabled
                ? SuggestedWords.EMPTY : currentSettings.mSpacingAndPunctuations.mSuggestPuncList;
        setSuggestedWords(neutralSuggestions);
    }

    // TODO: Make this private
    // Outside LatinIME, only used by the {@link InputTestsBase} test suite.
    @UsedForTesting
    void loadKeyboard() {
        // Since we are switching languages, the most urgent thing is to let the keyboard graphics
        // update. LoadKeyboard does that, but we need to wait for buffer flip for it to be on
        // the screen. Anything we do right now will delay this, so wait until the next frame
        // before we do the rest, like reopening dictionaries and updating suggestions. So we
        // post a message.
        mHandler.postReopenDictionaries();
        loadSettings();
        if (mKeyboardSwitcher.getMainKeyboardView() != null) {
            // Reload keyboard because the current language has been changed.
            mKeyboardSwitcher.loadKeyboard(getCurrentInputEditorInfo(), mSettings.getCurrent(),
                    getCurrentAutoCapsState(), getCurrentRecapitalizeState());
        }
    }

    /**
     * After an input transaction has been executed, some state must be updated. This includes
     * the shift state of the keyboard and suggestions. This method looks at the finished
     * inputTransaction to find out what is necessary and updates the state accordingly.
     * @param inputTransaction The transaction that has been executed.
     */
    private void updateStateAfterInputTransaction(final InputTransaction inputTransaction) {
        switch (inputTransaction.getRequiredShiftUpdate()) {
        case InputTransaction.SHIFT_UPDATE_LATER:
            mHandler.postUpdateShiftState();
            break;
        case InputTransaction.SHIFT_UPDATE_NOW:
            mKeyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
            break;
        default: // SHIFT_NO_UPDATE
        }
        if (inputTransaction.requiresUpdateSuggestions()) {
            mHandler.postUpdateSuggestionStrip();
        }
        if (inputTransaction.didAffectContents()) {
            mSubtypeState.setCurrentSubtypeHasBeenUsed();
        }
    }

    private void hapticAndAudioFeedback(final int code, final int repeatCount) {
        final MainKeyboardView keyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (keyboardView != null && keyboardView.isInDraggingFinger()) {
            // No need to feedback while finger is dragging.
            return;
        }
        if (repeatCount > 0) {
            if (code == Constants.CODE_DELETE && !mInputLogic.mConnection.canDeleteCharacters()) {
                // No need to feedback when repeat delete key will have no effect.
                return;
            }
            // TODO: Use event time that the last feedback has been generated instead of relying on
            // a repeat count to thin out feedback.
            if (repeatCount % PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT == 0) {
                return;
            }
        }
        final AudioAndHapticFeedbackManager feedbackManager =
                AudioAndHapticFeedbackManager.getInstance();
        if (repeatCount == 0) {
            // TODO: Reconsider how to perform haptic feedback when repeating key.
            feedbackManager.performHapticFeedback(keyboardView);
        }
        feedbackManager.performAudioFeedback(code);
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is depressed;
    // release matching call is {@link #onReleaseKey(int,boolean)} below.
    @Override
    public void onPressKey(final int primaryCode, final int repeatCount,
            final boolean isSinglePointer) {
        mKeyboardSwitcher.onPressKey(primaryCode, isSinglePointer, getCurrentAutoCapsState(),
                getCurrentRecapitalizeState());
        hapticAndAudioFeedback(primaryCode, repeatCount);
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is released;
    // press matching call is {@link #onPressKey(int,int,boolean)} above.
    @Override
    public void onReleaseKey(final int primaryCode, final boolean withSliding) {
        mKeyboardSwitcher.onReleaseKey(primaryCode, withSliding, getCurrentAutoCapsState(),
                getCurrentRecapitalizeState());
    }

    private HardwareEventDecoder getHardwareKeyEventDecoder(final int deviceId) {
        final HardwareEventDecoder decoder = mHardwareEventDecoders.get(deviceId);
        if (null != decoder) return decoder;
        // TODO: create the decoder according to the specification
        final HardwareEventDecoder newDecoder = new HardwareKeyboardEventDecoder(deviceId);
        mHardwareEventDecoders.put(deviceId, newDecoder);
        return newDecoder;
    }

    // Hooks for hardware keyboard
    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent keyEvent) {
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED) {
            return super.onKeyDown(keyCode, keyEvent);
        }
        final Event event = getHardwareKeyEventDecoder(
                keyEvent.getDeviceId()).decodeHardwareKey(keyEvent);
        // If the event is not handled by LatinIME, we just pass it to the parent implementation.
        // If it's handled, we return true because we did handle it.
        if (event.isHandled()) {
            mInputLogic.onCodeInput(mSettings.getCurrent(), event,
                    mKeyboardSwitcher.getKeyboardShiftMode(),
                    // TODO: this is not necessarily correct for a hardware keyboard right now
                    mKeyboardSwitcher.getCurrentKeyboardScriptId(),
                    mHandler);
            return true;
        }
        return super.onKeyDown(keyCode, keyEvent);
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        final long keyIdentifier = event.getDeviceId() << 32 + event.getKeyCode();
        if (mInputLogic.mCurrentlyPressedHardwareKeys.remove(keyIdentifier)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // onKeyDown and onKeyUp are the main events we are interested in. There are two more events
    // related to handling of hardware key events that we may want to implement in the future:
    // boolean onKeyLongPress(final int keyCode, final KeyEvent event);
    // boolean onKeyMultiple(final int keyCode, final int count, final KeyEvent event);

    // receive ringer mode change and network state change.
    private final BroadcastReceiver mConnectivityAndRingerModeChangeReceiver =
            new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                mSubtypeSwitcher.onNetworkStateChanged(intent);
            } else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                AudioAndHapticFeedbackManager.getInstance().onRingerModeChanged();
            }
        }
    };

    private void launchSettings() {
        mInputLogic.commitTyped(mSettings.getCurrent(), LastComposedWord.NOT_A_SEPARATOR);
        requestHideSelf(0);
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
        final Intent intent = new Intent();
        intent.setClass(LatinIME.this, SettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(SettingsActivity.EXTRA_SHOW_HOME_AS_UP, false);
        startActivity(intent);
    }

    private void showSubtypeSelectorAndSettings() {
        final CharSequence title = getString(R.string.english_ime_input_options);
        // TODO: Should use new string "Select active input modes".
        final CharSequence languageSelectionTitle = getString(R.string.language_selection_title);
        final CharSequence[] items = new CharSequence[] {
                languageSelectionTitle,
                getString(ApplicationUtils.getActivityTitleResId(this, SettingsActivity.class))
        };
        final OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                case 0:
                    final Intent intent = IntentUtils.getInputLanguageSelectionIntent(
                            mRichImm.getInputMethodIdOfThisIme(),
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra(Intent.EXTRA_TITLE, languageSelectionTitle);
                    startActivity(intent);
                    break;
                case 1:
                    launchSettings();
                    break;
                }
            }
        };
        final AlertDialog.Builder builder = new AlertDialog.Builder(
                DialogUtils.getPlatformDialogThemeContext(this));
        builder.setItems(items, listener).setTitle(title);
        final AlertDialog dialog = builder.create();
        dialog.setCancelable(true /* cancelable */);
        dialog.setCanceledOnTouchOutside(true /* cancelable */);
        showOptionDialog(dialog);
    }

    // TODO: Move this method out of {@link LatinIME}.
    private void showOptionDialog(final AlertDialog dialog) {
        final IBinder windowToken = mKeyboardSwitcher.getMainKeyboardView().getWindowToken();
        if (windowToken == null) {
            return;
        }

        final Window window = dialog.getWindow();
        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = windowToken;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        mOptionsDialog = dialog;
        dialog.show();
    }

    // TODO: can this be removed somehow without breaking the tests?
    @UsedForTesting
    /* package for test */ SuggestedWords getSuggestedWordsForTest() {
        // You may not use this method for anything else than debug
        return DEBUG ? mInputLogic.mSuggestedWords : null;
    }

    // DO NOT USE THIS for any other purpose than testing. This is information private to LatinIME.
    @UsedForTesting
    /* package for test */ void waitForLoadingDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        mDictionaryFacilitator.waitForLoadingDictionariesForTesting(timeout, unit);
    }

    // DO NOT USE THIS for any other purpose than testing. This can break the keyboard badly.
    @UsedForTesting
    /* package for test */ void replaceDictionariesForTest(final Locale locale) {
        final SettingsValues settingsValues = mSettings.getCurrent();
        mDictionaryFacilitator.resetDictionaries(this, locale,
            settingsValues.mUseContactsDict, settingsValues.mUsePersonalizedDicts,
            false /* forceReloadMainDictionary */, this /* listener */);
    }

    // DO NOT USE THIS for any other purpose than testing.
    @UsedForTesting
    /* package for test */ void clearPersonalizedDictionariesForTest() {
        mDictionaryFacilitator.clearUserHistoryDictionary();
        mDictionaryFacilitator.clearPersonalizationDictionary();
    }

    @UsedForTesting
    /* package for test */ List<InputMethodSubtype> getEnabledSubtypesForTest() {
        return (mRichImm != null) ? mRichImm.getMyEnabledInputMethodSubtypeList(
                true /* allowsImplicitlySelectedSubtypes */) : new ArrayList<InputMethodSubtype>();
    }

    public void dumpDictionaryForDebug(final String dictName) {
        if (mDictionaryFacilitator.getLocale() == null) {
            resetSuggest();
        }
        mDictionaryFacilitator.dumpDictionaryForDebug(dictName);
    }

    public void debugDumpStateAndCrashWithException(final String context) {
        final SettingsValues settingsValues = mSettings.getCurrent();
        final StringBuilder s = new StringBuilder(settingsValues.toString());
        s.append("\nAttributes : ").append(settingsValues.mInputAttributes)
                .append("\nContext : ").append(context);
        throw new RuntimeException(s.toString());
    }

    @Override
    protected void dump(final FileDescriptor fd, final PrintWriter fout, final String[] args) {
        super.dump(fd, fout, args);

        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        p.println("  VersionCode = " + ApplicationUtils.getVersionCode(this));
        p.println("  VersionName = " + ApplicationUtils.getVersionName(this));
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        final int keyboardMode = keyboard != null ? keyboard.mId.mMode : -1;
        p.println("  Keyboard mode = " + keyboardMode);
        final SettingsValues settingsValues = mSettings.getCurrent();
        p.println(settingsValues.dump());
        // TODO: Dump all settings values
    }

    public boolean shouldSwitchToOtherInputMethods() {
        // TODO: Revisit here to reorganize the settings. Probably we can/should use different
        // strategy once the implementation of
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} is defined well.
        final boolean fallbackValue = mSettings.getCurrent().mIncludesOtherImesInLanguageSwitchList;
        final IBinder token = getWindow().getWindow().getAttributes().token;
        if (token == null) {
            return fallbackValue;
        }
        return mRichImm.shouldOfferSwitchingToNextInputMethod(token, fallbackValue);
    }

    public boolean shouldShowLanguageSwitchKey() {
        // TODO: Revisit here to reorganize the settings. Probably we can/should use different
        // strategy once the implementation of
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} is defined well.
        final boolean fallbackValue = mSettings.getCurrent().isLanguageSwitchKeyEnabled();
        final IBinder token = getWindow().getWindow().getAttributes().token;
        if (token == null) {
            return fallbackValue;
        }
        return mRichImm.shouldOfferSwitchingToNextInputMethod(token, fallbackValue);
    }
}
