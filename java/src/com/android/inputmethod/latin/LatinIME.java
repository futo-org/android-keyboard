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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.compat.InputMethodServiceCompatUtils;
import com.android.inputmethod.dictionarypack.DictionaryPackConstants;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.inputlogic.InputLogic;
import com.android.inputmethod.latin.inputlogic.SpaceState;
import com.android.inputmethod.latin.personalization.DictionaryDecayBroadcastReciever;
import com.android.inputmethod.latin.personalization.PersonalizationDictionarySessionRegistrar;
import com.android.inputmethod.latin.personalization.PersonalizationHelper;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsActivity;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.suggestions.SuggestionStripView;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.CapsModeUtils;
import com.android.inputmethod.latin.utils.CompletionInfoUtils;
import com.android.inputmethod.latin.utils.IntentUtils;
import com.android.inputmethod.latin.utils.JniUtils;
import com.android.inputmethod.latin.utils.LatinImeLoggerUtils;
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper;
import com.android.inputmethod.research.ResearchLogger;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService implements KeyboardActionListener,
        SuggestionStripView.Listener,
        DictionaryFacilitatorForSuggest.DictionaryInitializationListener {
    private static final String TAG = LatinIME.class.getSimpleName();
    private static final boolean TRACE = false;
    private static boolean DEBUG = false;

    private static final int EXTENDED_TOUCHABLE_REGION_HEIGHT = 100;

    private static final int PENDING_IMS_CALLBACK_DURATION = 800;

    private static final int PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT = 2;

    /**
     * The name of the scheme used by the Package Manager to warn of a new package installation,
     * replacement or removal.
     */
    private static final String SCHEME_PACKAGE = "package";

    private final Settings mSettings;
    private final InputLogic mInputLogic = new InputLogic(this);

    private View mExtractArea;
    private View mKeyPreviewBackingView;
    private SuggestionStripView mSuggestionStripView;

    private CompletionInfo[] mApplicationSpecifiedCompletions;

    private RichInputMethodManager mRichImm;
    @UsedForTesting final KeyboardSwitcher mKeyboardSwitcher;
    private final SubtypeSwitcher mSubtypeSwitcher;
    private final SubtypeState mSubtypeState = new SubtypeState();

    // Object for reacting to adding/removing a dictionary pack.
    private BroadcastReceiver mDictionaryPackInstallReceiver =
            new DictionaryPackInstallBroadcastReceiver(this);

    private AlertDialog mOptionsDialog;

    private final boolean mIsHardwareAcceleratedDrawingEnabled;

    public final UIHandler mHandler = new UIHandler(this);
    private InputUpdater mInputUpdater;

    public static final class UIHandler extends LeakGuardHandlerWrapper<LatinIME> {
        private static final int MSG_UPDATE_SHIFT_STATE = 0;
        private static final int MSG_PENDING_IMS_CALLBACK = 1;
        private static final int MSG_UPDATE_SUGGESTION_STRIP = 2;
        private static final int MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP = 3;
        private static final int MSG_RESUME_SUGGESTIONS = 4;
        private static final int MSG_REOPEN_DICTIONARIES = 5;
        private static final int MSG_ON_END_BATCH_INPUT = 6;
        private static final int MSG_RESET_CACHES = 7;
        // Update this when adding new messages
        private static final int MSG_LAST = MSG_RESET_CACHES;

        private static final int ARG1_NOT_GESTURE_INPUT = 0;
        private static final int ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 1;
        private static final int ARG1_SHOW_GESTURE_FLOATING_PREVIEW_TEXT = 2;
        private static final int ARG2_WITHOUT_TYPED_WORD = 0;
        private static final int ARG2_WITH_TYPED_WORD = 1;

        private int mDelayUpdateSuggestions;
        private int mDelayUpdateShiftState;
        private long mDoubleSpacePeriodTimeout;
        private long mDoubleSpacePeriodTimerStart;

        public UIHandler(final LatinIME ownerInstance) {
            super(ownerInstance);
        }

        public void onCreate() {
            final Resources res = getOwnerInstance().getResources();
            mDelayUpdateSuggestions =
                    res.getInteger(R.integer.config_delay_update_suggestions);
            mDelayUpdateShiftState =
                    res.getInteger(R.integer.config_delay_update_shift_state);
            mDoubleSpacePeriodTimeout =
                    res.getInteger(R.integer.config_double_space_period_timeout);
        }

        @Override
        public void handleMessage(final Message msg) {
            final LatinIME latinIme = getOwnerInstance();
            final KeyboardSwitcher switcher = latinIme.mKeyboardSwitcher;
            switch (msg.what) {
            case MSG_UPDATE_SUGGESTION_STRIP:
                latinIme.mInputLogic.performUpdateSuggestionStripSync(
                        latinIme.mSettings.getCurrent(), this /* handler */,
                        latinIme.mInputUpdater);
                break;
            case MSG_UPDATE_SHIFT_STATE:
                switcher.updateShiftState();
                break;
            case MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP:
                if (msg.arg1 == ARG1_NOT_GESTURE_INPUT) {
                    if (msg.arg2 == ARG2_WITH_TYPED_WORD) {
                        final Pair<SuggestedWords, String> p =
                                (Pair<SuggestedWords, String>) msg.obj;
                        latinIme.showSuggestionStripWithTypedWord(p.first, p.second);
                    } else {
                        latinIme.showSuggestionStrip((SuggestedWords) msg.obj);
                    }
                } else {
                    latinIme.showGesturePreviewAndSuggestionStrip((SuggestedWords) msg.obj,
                            msg.arg1 == ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT);
                }
                break;
            case MSG_RESUME_SUGGESTIONS:
                latinIme.mInputLogic.restartSuggestionsOnWordTouchedByCursor(
                        latinIme.mSettings.getCurrent(), latinIme.mKeyboardSwitcher,
                        latinIme.mInputUpdater);
                break;
            case MSG_REOPEN_DICTIONARIES:
                latinIme.initSuggest();
                // In theory we could call latinIme.updateSuggestionStrip() right away, but
                // in the practice, the dictionary is not finished opening yet so we wouldn't
                // get any suggestions. Wait one frame.
                postUpdateSuggestionStrip();
                break;
            case MSG_ON_END_BATCH_INPUT:
                latinIme.onEndBatchInputAsyncInternal((SuggestedWords) msg.obj);
                break;
            case MSG_RESET_CACHES:
                latinIme.mInputLogic.retryResetCaches(latinIme.mSettings.getCurrent(),
                        msg.arg1 == 1 /* tryResumeSuggestions */,
                        msg.arg2 /* remainingTries */,
                        latinIme.mKeyboardSwitcher, this);
                break;
            }
        }

        public void postUpdateSuggestionStrip() {
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTION_STRIP), mDelayUpdateSuggestions);
        }

        public void postReopenDictionaries() {
            sendMessage(obtainMessage(MSG_REOPEN_DICTIONARIES));
        }

        public void postResumeSuggestions() {
            removeMessages(MSG_RESUME_SUGGESTIONS);
            sendMessageDelayed(obtainMessage(MSG_RESUME_SUGGESTIONS), mDelayUpdateSuggestions);
        }

        public void postResetCaches(final boolean tryResumeSuggestions, final int remainingTries) {
            removeMessages(MSG_RESET_CACHES);
            sendMessage(obtainMessage(MSG_RESET_CACHES, tryResumeSuggestions ? 1 : 0,
                    remainingTries, null));
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

        public void cancelUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
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
                    ARG2_WITHOUT_TYPED_WORD, suggestedWords).sendToTarget();
        }

        public void showSuggestionStrip(final SuggestedWords suggestedWords) {
            removeMessages(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP);
            obtainMessage(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP,
                    ARG1_NOT_GESTURE_INPUT, ARG2_WITHOUT_TYPED_WORD, suggestedWords).sendToTarget();
        }

        // TODO: Remove this method.
        public void showSuggestionStripWithTypedWord(final SuggestedWords suggestedWords,
                final String typedWord) {
            removeMessages(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP);
            obtainMessage(MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP, ARG1_NOT_GESTURE_INPUT,
                    ARG2_WITH_TYPED_WORD,
                    new Pair<SuggestedWords, String>(suggestedWords, typedWord)).sendToTarget();
        }

        public void onEndBatchInput(final SuggestedWords suggestedWords) {
            obtainMessage(MSG_ON_END_BATCH_INPUT, suggestedWords).sendToTarget();
        }

        public void startDoubleSpacePeriodTimer() {
            mDoubleSpacePeriodTimerStart = SystemClock.uptimeMillis();
        }

        public void cancelDoubleSpacePeriodTimer() {
            mDoubleSpacePeriodTimerStart = 0;
        }

        public boolean isAcceptingDoubleSpacePeriod() {
            return SystemClock.uptimeMillis() - mDoubleSpacePeriodTimerStart
                    < mDoubleSpacePeriodTimeout;
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
            if (mHasPendingFinishInputView)
                latinIme.onFinishInputViewInternal(mHasPendingFinishInput);
            if (mHasPendingFinishInput)
                latinIme.onFinishInputInternal();
            if (mHasPendingStartInput)
                latinIme.onStartInputInternal(editorInfo, restarting);
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
                executePendingImsCallback(latinIme, editorInfo, restarting);
                latinIme.onStartInputInternal(editorInfo, restarting);
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
                executePendingImsCallback(latinIme, editorInfo, restarting);
                latinIme.onStartInputViewInternal(editorInfo, restarting);
                mAppliedEditorInfo = editorInfo;
            }
        }

        public void onFinishInputView(final boolean finishingInput) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInputView after orientation changed.
                mHasPendingFinishInputView = true;
            } else {
                final LatinIME latinIme = getOwnerInstance();
                latinIme.onFinishInputViewInternal(finishingInput);
                mAppliedEditorInfo = null;
            }
        }

        public void onFinishInput() {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInput after orientation changed.
                mHasPendingFinishInput = true;
            } else {
                final LatinIME latinIme = getOwnerInstance();
                executePendingImsCallback(latinIme, null, false);
                latinIme.onFinishInputInternal();
            }
        }
    }

    static final class SubtypeState {
        private InputMethodSubtype mLastActiveSubtype;
        private boolean mCurrentSubtypeUsed;

        public void currentSubtypeUsed() {
            mCurrentSubtypeUsed = true;
        }

        public void switchSubtype(final IBinder token, final RichInputMethodManager richImm) {
            final InputMethodSubtype currentSubtype = richImm.getInputMethodManager()
                    .getCurrentInputMethodSubtype();
            final InputMethodSubtype lastActiveSubtype = mLastActiveSubtype;
            final boolean currentSubtypeUsed = mCurrentSubtypeUsed;
            if (currentSubtypeUsed) {
                mLastActiveSubtype = currentSubtype;
                mCurrentSubtypeUsed = false;
            }
            if (currentSubtypeUsed
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
        LatinImeLogger.init(this);
        RichInputMethodManager.init(this);
        mRichImm = RichInputMethodManager.getInstance();
        SubtypeSwitcher.init(this);
        KeyboardSwitcher.init(this);
        AudioAndHapticFeedbackManager.init(this);
        AccessibilityUtils.init(this);

        super.onCreate();

        mHandler.onCreate();
        DEBUG = LatinImeLogger.sDBG;

        // TODO: Resolve mutual dependencies of {@link #loadSettings()} and {@link #initSuggest()}.
        loadSettings();
        initSuggest();

        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.getInstance().init(this, mKeyboardSwitcher);
        }

        // Register to receive ringer mode change and network state change.
        // Also receive installation and removal of a dictionary pack.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);

        final IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme(SCHEME_PACKAGE);
        registerReceiver(mDictionaryPackInstallReceiver, packageFilter);

        final IntentFilter newDictFilter = new IntentFilter();
        newDictFilter.addAction(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION);
        registerReceiver(mDictionaryPackInstallReceiver, newDictFilter);

        DictionaryDecayBroadcastReciever.setUpIntervalAlarmForDictionaryDecaying(this);

        mInputUpdater = new InputUpdater(this);
    }

    // Has to be package-visible for unit tests
    @UsedForTesting
    void loadSettings() {
        final Locale locale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        final EditorInfo editorInfo = getCurrentInputEditorInfo();
        final InputAttributes inputAttributes = new InputAttributes(editorInfo, isFullscreenMode());
        mSettings.loadSettings(this, locale, inputAttributes);
        AudioAndHapticFeedbackManager.getInstance().onSettingsChanged(mSettings.getCurrent());
        // To load the keyboard we need to load all the settings once, but resetting the
        // contacts dictionary should be deferred until after the new layout has been displayed
        // to improve responsivity. In the language switching process, we post a reopenDictionaries
        // message, then come here to read the settings for the new language before we change
        // the layout; at this time, we need to skip resetting the contacts dictionary. It will
        // be done later inside {@see #initSuggest()} when the reopenDictionaries message is
        // processed.
        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        final Suggest suggest = mInputLogic.mSuggest;
        if (!mHandler.hasPendingReopenDictionaries() && suggest != null) {
            // May need to reset dictionaries depending on the user settings.
            final DictionaryFacilitatorForSuggest oldDictionaryFacilitator =
                    suggest.mDictionaryFacilitator;
            final DictionaryFacilitatorForSuggest dictionaryFacilitator =
                    new DictionaryFacilitatorForSuggest(currentSettingsValues,
                            oldDictionaryFacilitator);
            // Create Suggest instance with the new dictionary facilitator.
            mInputLogic.mSuggest = new Suggest(suggest /* oldSuggest */, dictionaryFacilitator);
            suggest.close();
        }
        if (currentSettingsValues.mUsePersonalizedDicts) {
            PersonalizationDictionarySessionRegistrar.init(this);
        } else {
            PersonalizationHelper.removeAllPersonalizedDictionaries(this);
            PersonalizationDictionarySessionRegistrar.resetAll(this);
        }
    }

    // Note that this method is called from a non-UI thread.
    @Override
    public void onUpdateMainDictionaryAvailability(final boolean isMainDictionaryAvailable) {
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.setMainDictionaryAvailability(isMainDictionaryAvailable);
        }
    }

    private void initSuggest() {
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

        final SettingsValues settingsValues = mSettings.getCurrent();
        final DictionaryFacilitatorForSuggest oldDictionaryFacilitator =
                (mInputLogic.mSuggest == null) ? null : mInputLogic.mSuggest.mDictionaryFacilitator;
        // Creates new dictionary facilitator for the new locale.
        final DictionaryFacilitatorForSuggest dictionaryFacilitator =
                new DictionaryFacilitatorForSuggest(this /* context */, subtypeLocale,
                        settingsValues, this /* DictionaryInitializationListener */,
                        oldDictionaryFacilitator);
        final Suggest newSuggest = new Suggest(subtypeLocale, dictionaryFacilitator);
        if (settingsValues.mCorrectionEnabled) {
            newSuggest.setAutoCorrectionThreshold(settingsValues.mAutoCorrectionThreshold);
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.getInstance().initDictionary(newSuggest.mDictionaryFacilitator);
        }
        final Suggest oldSuggest = mInputLogic.mSuggest;
        mInputLogic.mSuggest = newSuggest;
        if (oldSuggest != null) oldSuggest.close();
    }

    /* package private */ void resetSuggestMainDict() {
        final Locale subtypeLocale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        mInputLogic.mSuggest.mDictionaryFacilitator.reloadMainDict(this, subtypeLocale,
                this /* SuggestInitializationListener */);
    }

    @Override
    public void onDestroy() {
        final Suggest suggest = mInputLogic.mSuggest;
        if (suggest != null) {
            suggest.close();
            mInputLogic.mSuggest = null;
        }
        if (mInputUpdater != null) {
            mInputUpdater.quitLooper();
        }
        mSettings.onDestroy();
        unregisterReceiver(mReceiver);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.getInstance().onDestroy();
        }
        unregisterReceiver(mDictionaryPackInstallReceiver);
        PersonalizationDictionarySessionRegistrar.onDestroy(this);
        LatinImeLogger.commit();
        LatinImeLogger.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(final Configuration conf) {
        // If orientation changed while predicting, commit the change
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (settingsValues.mDisplayOrientation != conf.orientation) {
            mHandler.startOrientationChanging();
            mInputLogic.mConnection.beginBatchEdit();
            mInputLogic.commitTyped(mSettings.getCurrent(), LastComposedWord.NOT_A_SEPARATOR);
            mInputLogic.mConnection.finishComposingText();
            mInputLogic.mConnection.endBatchEdit();
            if (isShowingOptionDialog()) {
                mOptionsDialog.dismiss();
            }
        }
        PersonalizationDictionarySessionRegistrar.onConfigurationChanged(this, conf);
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
        if (mSuggestionStripView != null) {
            mSuggestionStripView.setListener(this, view);
        }
        if (LatinImeLogger.sVISUALDEBUG) {
            mKeyPreviewBackingView.setBackgroundColor(0x10FF0000);
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
        loadKeyboard();
    }

    private void onStartInputInternal(final EditorInfo editorInfo, final boolean restarting) {
        super.onStartInput(editorInfo, restarting);
    }

    @SuppressWarnings("deprecation")
    private void onStartInputViewInternal(final EditorInfo editorInfo, final boolean restarting) {
        super.onStartInputView(editorInfo, restarting);
        mRichImm.clearSubtypeCaches();
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        switcher.updateKeyboardTheme();
        final MainKeyboardView mainKeyboardView = switcher.getMainKeyboardView();
        // If we are starting input in a different text field from before, we'll have to reload
        // settings, so currentSettingsValues can't be final.
        SettingsValues currentSettingsValues = mSettings.getCurrent();

        if (editorInfo == null) {
            Log.e(TAG, "Null EditorInfo in onStartInputView()");
            if (LatinImeLogger.sDBG) {
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
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            ResearchLogger.latinIME_onStartInputViewInternal(editorInfo, prefs);
        }
        if (InputAttributes.inPrivateImeOptions(null, NO_MICROPHONE_COMPAT, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: "
                    + editorInfo.privateImeOptions);
            Log.w(TAG, "Use " + getPackageName() + "." + NO_MICROPHONE + " instead");
        }
        if (InputAttributes.inPrivateImeOptions(getPackageName(), FORCE_ASCII, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: "
                    + editorInfo.privateImeOptions);
            Log.w(TAG, "Use EditorInfo.IME_FLAG_FORCE_ASCII flag instead");
        }

        LatinImeLogger.onStartInputView(editorInfo);
        // In landscape mode, this method gets called without the input view being created.
        if (mainKeyboardView == null) {
            return;
        }

        // Forward this event to the accessibility utilities, if enabled.
        final AccessibilityUtils accessUtils = AccessibilityUtils.getInstance();
        if (accessUtils.isTouchExplorationEnabled()) {
            accessUtils.onStartInputViewInternal(mainKeyboardView, editorInfo, restarting);
        }

        final boolean inputTypeChanged = !currentSettingsValues.isSameInputType(editorInfo);
        final boolean isDifferentTextField = !restarting || inputTypeChanged;
        if (isDifferentTextField) {
            mSubtypeSwitcher.updateParametersOnStartInputView();
        }

        // The EditorInfo might have a flag that affects fullscreen mode.
        // Note: This call should be done by InputMethodService?
        updateFullscreenMode();
        mApplicationSpecifiedCompletions = null;

        // The app calling setText() has the effect of clearing the composing
        // span, so we should reset our state unconditionally, even if restarting is true.
        mInputLogic.mEnteredText = null;
        mInputLogic.resetComposingState(true /* alsoResetLastComposedWord */);
        mInputLogic.mDeleteCount = 0;
        mInputLogic.mSpaceState = SpaceState.NONE;
        mInputLogic.mRecapitalizeStatus.deactivate();
        mInputLogic.mCurrentlyPressedHardwareKeys.clear();

        // Note: the following does a round-trip IPC on the main thread: be careful
        final Locale currentLocale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        final Suggest suggest = mInputLogic.mSuggest;
        if (null != suggest && null != currentLocale && !currentLocale.equals(suggest.mLocale)) {
            initSuggest();
        }
        if (mSuggestionStripView != null) {
            // This will set the punctuation suggestions if next word suggestion is off;
            // otherwise it will clear the suggestion strip.
            setPunctuationSuggestions();
        }
        mInputLogic.mSuggestedWords = SuggestedWords.EMPTY;

        // Sometimes, while rotating, for some reason the framework tells the app we are not
        // connected to it and that means we can't refresh the cache. In this case, schedule a
        // refresh later.
        final boolean canReachInputConnection;
        if (!mInputLogic.mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                editorInfo.initialSelStart, editorInfo.initialSelEnd,
                false /* shouldFinishComposition */)) {
            // We try resetting the caches up to 5 times before giving up.
            mHandler.postResetCaches(isDifferentTextField, 5 /* remainingTries */);
            // mLastSelection{Start,End} are reset later in this method, don't need to do it here
            canReachInputConnection = false;
        } else {
            if (isDifferentTextField) {
                mHandler.postResumeSuggestions();
            }
            canReachInputConnection = true;
        }

        if (isDifferentTextField ||
                !currentSettingsValues.hasSameOrientation(getResources().getConfiguration())) {
            loadSettings();
        }
        if (isDifferentTextField) {
            mainKeyboardView.closing();
            currentSettingsValues = mSettings.getCurrent();

            if (suggest != null && currentSettingsValues.mCorrectionEnabled) {
                suggest.setAutoCorrectionThreshold(currentSettingsValues.mAutoCorrectionThreshold);
            }

            switcher.loadKeyboard(editorInfo, currentSettingsValues);
            if (!canReachInputConnection) {
                // If we can't reach the input connection, we will call loadKeyboard again later,
                // so we need to save its state now. The call will be done in #retryResetCaches.
                switcher.saveKeyboardState();
            }
        } else if (restarting) {
            // TODO: Come up with a more comprehensive way to reset the keyboard layout when
            // a keyboard layout set doesn't get reloaded in this method.
            switcher.resetKeyboardStateToAlphabet();
            // In apps like Talk, we come here when the text is sent and the field gets emptied and
            // we need to re-evaluate the shift state, but not the whole layout which would be
            // disruptive.
            // Space state must be updated before calling updateShiftState
            switcher.updateShiftState();
        }
        setSuggestionStripShownInternal(
                isSuggestionsStripVisible(), /* needsInputViewShown */ false);

        mInputLogic.mLastSelectionStart = editorInfo.initialSelStart;
        mInputLogic.mLastSelectionEnd = editorInfo.initialSelEnd;
        // In some cases (namely, after rotation of the device) editorInfo.initialSelStart is lying
        // so we try using some heuristics to find out about these and fix them.
        mInputLogic.tryFixLyingCursorPosition();

        mHandler.cancelUpdateSuggestionStrip();
        mHandler.cancelDoubleSpacePeriodTimer();

        mainKeyboardView.setMainDictionaryAvailability(null != suggest
                ? suggest.mDictionaryFacilitator.hasMainDictionary() : false);
        mainKeyboardView.setKeyPreviewPopupEnabled(currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay);
        mainKeyboardView.setSlidingKeyInputPreviewEnabled(
                currentSettingsValues.mSlidingKeyInputPreviewEnabled);
        mainKeyboardView.setGestureHandlingEnabledByUser(
                currentSettingsValues.mGestureInputEnabled,
                currentSettingsValues.mGestureTrailEnabled,
                currentSettingsValues.mGestureFloatingPreviewTextEnabled);

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

        LatinImeLogger.commit();
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
    }

    private void onFinishInputViewInternal(final boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        mKeyboardSwitcher.onFinishInputView();
        mKeyboardSwitcher.deallocateMemory();
        // Remove pending messages related to update suggestions
        mHandler.cancelUpdateSuggestionStrip();
        // Should do the following in onFinishInputInternal but until JB MR2 it's not called :(
        if (mInputLogic.mWordComposer.isComposingWord()) {
            mInputLogic.mConnection.finishComposingText();
        }
        mInputLogic.resetComposingState(true /* alsoResetLastComposedWord */);
        // Notify ResearchLogger
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onFinishInputViewInternal(finishingInput,
                    mInputLogic.mLastSelectionStart,
                    mInputLogic.mLastSelectionEnd, getCurrentInputConnection());
        }
    }

    @Override
    public void onUpdateSelection(final int oldSelStart, final int oldSelEnd,
            final int newSelStart, final int newSelEnd,
            final int composingSpanStart, final int composingSpanEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                composingSpanStart, composingSpanEnd);
        if (DEBUG) {
            Log.i(TAG, "onUpdateSelection: oss=" + oldSelStart
                    + ", ose=" + oldSelEnd
                    + ", lss=" + mInputLogic.mLastSelectionStart
                    + ", lse=" + mInputLogic.mLastSelectionEnd
                    + ", nss=" + newSelStart
                    + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart
                    + ", ce=" + composingSpanEnd);
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onUpdateSelection(mInputLogic.mLastSelectionStart,
                    mInputLogic.mLastSelectionEnd,
                    oldSelStart, oldSelEnd, newSelStart, newSelEnd, composingSpanStart,
                    composingSpanEnd, mInputLogic.mConnection);
        }

        final boolean selectionChanged = mInputLogic.mLastSelectionStart != newSelStart
                || mInputLogic.mLastSelectionEnd != newSelEnd;

        // if composingSpanStart and composingSpanEnd are -1, it means there is no composing
        // span in the view - we can use that to narrow down whether the cursor was moved
        // by us or not. If we are composing a word but there is no composing span, then
        // we know for sure the cursor moved while we were composing and we should reset
        // the state. TODO: rescind this policy: the framework never removes the composing
        // span on its own accord while editing. This test is useless.
        final boolean noComposingSpan = composingSpanStart == -1 && composingSpanEnd == -1;

        // If the keyboard is not visible, we don't need to do all the housekeeping work, as it
        // will be reset when the keyboard shows up anyway.
        // TODO: revisit this when LatinIME supports hardware keyboards.
        // NOTE: the test harness subclasses LatinIME and overrides isInputViewShown().
        // TODO: find a better way to simulate actual execution.
        if (isInputViewShown() && !mInputLogic.mConnection.isBelatedExpectedUpdate(oldSelStart,
                newSelStart, oldSelEnd, newSelEnd)) {
            // TODO: the following is probably better done in resetEntireInputState().
            // it should only happen when the cursor moved, and the very purpose of the
            // test below is to narrow down whether this happened or not. Likewise with
            // the call to updateShiftState.
            // We set this to NONE because after a cursor move, we don't want the space
            // state-related special processing to kick in.
            mInputLogic.mSpaceState = SpaceState.NONE;

            // TODO: is it still necessary to test for composingSpan related stuff?
            final boolean selectionChangedOrSafeToReset = selectionChanged
                    || (!mInputLogic.mWordComposer.isComposingWord()) || noComposingSpan;
            final boolean hasOrHadSelection = (oldSelStart != oldSelEnd
                    || newSelStart != newSelEnd);
            final int moveAmount = newSelStart - oldSelStart;
            if (selectionChangedOrSafeToReset && (hasOrHadSelection
                    || !mInputLogic.mWordComposer.moveCursorByAndReturnIfInsideComposingWord(
                            moveAmount))) {
                // If we are composing a word and moving the cursor, we would want to set a
                // suggestion span for recorrection to work correctly. Unfortunately, that
                // would involve the keyboard committing some new text, which would move the
                // cursor back to where it was. Latin IME could then fix the position of the cursor
                // again, but the asynchronous nature of the calls results in this wreaking havoc
                // with selection on double tap and the like.
                // Another option would be to send suggestions each time we set the composing
                // text, but that is probably too expensive to do, so we decided to leave things
                // as is.
                mInputLogic.resetEntireInputState(mSettings.getCurrent(), newSelStart, newSelEnd);
            } else {
                // resetEntireInputState calls resetCachesUponCursorMove, but forcing the
                // composition to end.  But in all cases where we don't reset the entire input
                // state, we still want to tell the rich input connection about the new cursor
                // position so that it can update its caches.
                mInputLogic.mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                        newSelStart, newSelEnd, false /* shouldFinishComposition */);
            }

            // We moved the cursor. If we are touching a word, we need to resume suggestion,
            // unless suggestions are off.
            if (isSuggestionsStripVisible()) {
                mHandler.postResumeSuggestions();
            }
            // Reset the last recapitalization.
            mInputLogic.mRecapitalizeStatus.deactivate();
            mKeyboardSwitcher.updateShiftState();
        }

        // Make a note of the cursor position
        mInputLogic.mLastSelectionStart = newSelStart;
        mInputLogic.mLastSelectionEnd = newSelEnd;
        mSubtypeState.currentSubtypeUsed();
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
        if (mSettings.getCurrent().isSuggestionsRequested()) {
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
        if (mSettings.getCurrent().isSuggestionsRequested()) {
            return;
        }

        super.onExtractedCursorMovement(dx, dy);
    }

    @Override
    public void hideWindow() {
        LatinImeLogger.commit();
        mKeyboardSwitcher.onHideWindow();

        if (AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            AccessibleKeyboardViewProxy.getInstance().onHideWindow();
        }

        if (TRACE) Debug.stopMethodTracing();
        if (mOptionsDialog != null && mOptionsDialog.isShowing()) {
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
        if (!mSettings.getCurrent().isApplicationSpecifiedCompletionsOn()) return;
        if (applicationSpecifiedCompletions == null) {
            clearSuggestionStrip();
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_onDisplayCompletions(null);
            }
            return;
        }
        mApplicationSpecifiedCompletions =
                CompletionInfoUtils.removeNulls(applicationSpecifiedCompletions);

        final ArrayList<SuggestedWords.SuggestedWordInfo> applicationSuggestedWords =
                SuggestedWords.getFromApplicationSpecifiedCompletions(
                        applicationSpecifiedCompletions);
        final SuggestedWords suggestedWords = new SuggestedWords(
                applicationSuggestedWords,
                false /* typedWordValid */,
                false /* hasAutoCorrectionCandidate */,
                false /* isPunctuationSuggestions */,
                false /* isObsoleteSuggestions */,
                false /* isPrediction */);
        // When in fullscreen mode, show completions generated by the application
        setSuggestedWords(suggestedWords);
        setAutoCorrectionIndicator(false);
        setSuggestionStripShown(true);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onDisplayCompletions(applicationSpecifiedCompletions);
        }
    }

    private void setSuggestionStripShownInternal(final boolean shown,
            final boolean needsInputViewShown) {
        // TODO: Modify this if we support suggestions with hard keyboard
        if (onEvaluateInputViewShown() && mSuggestionStripView != null) {
            final boolean inputViewShown = mKeyboardSwitcher.isShowingMainKeyboardOrEmojiPalettes();
            final boolean shouldShowSuggestions = shown
                    && (needsInputViewShown ? inputViewShown : true);
            if (isFullscreenMode()) {
                mSuggestionStripView.setVisibility(
                        shouldShowSuggestions ? View.VISIBLE : View.GONE);
            } else {
                mSuggestionStripView.setVisibility(
                        shouldShowSuggestions ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private void setSuggestionStripShown(final boolean shown) {
        setSuggestionStripShownInternal(shown, /* needsInputViewShown */true);
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
        if (visibleKeyboardView == null || mSuggestionStripView == null) {
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

    // Called from the KeyboardSwitcher which needs to know auto caps state to display
    // the right layout.
    // TODO[IL]: Remove this, pass the input logic to the keyboard switcher instead?
    public int getCurrentAutoCapsState() {
        return mInputLogic.getCurrentAutoCapsState(null /* optionalSettingsValues */);
    }

    // Called from the KeyboardSwitcher which needs to know recaps state to display
    // the right layout.
    // TODO[IL]: Remove this, pass the input logic to the keyboard switcher instead?
    public int getCurrentRecapitalizeState() {
        return mInputLogic.getCurrentRecapitalizeState();
    }

    public Locale getCurrentSubtypeLocale() {
        return mSubtypeSwitcher.getCurrentSubtypeLocale();
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
        mInputLogic.mSuggest.mDictionaryFacilitator.addWordToUserDictionary(wordToEdit);
    }

    public void displaySettingsDialog() {
        if (isShowingOptionDialog()) return;
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
        if (mSettings.getCurrent().mIncludesOtherImesInLanguageSwitchList) {
            mRichImm.switchToNextInputMethod(token, false /* onlyCurrentIme */);
            return;
        }
        mSubtypeState.switchSubtype(token, mRichImm);
    }

    // Implementation of {@link KeyboardActionListener}.
    @Override
    public void onCodeInput(final int primaryCode, final int x, final int y) {
        mInputLogic.onCodeInput(primaryCode, x, y, mHandler, mInputUpdater,
                mKeyboardSwitcher, mSubtypeSwitcher);
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onTextInput(final String rawText) {
        mInputLogic.onTextInput(mSettings.getCurrent(), rawText, mHandler, mInputUpdater);
        mKeyboardSwitcher.updateShiftState();
        mKeyboardSwitcher.onCodeInput(Constants.CODE_OUTPUT_TEXT);
    }

    @Override
    public void onStartBatchInput() {
        mInputLogic.onStartBatchInput(mSettings.getCurrent(),  mKeyboardSwitcher, mHandler,
                mInputUpdater);
    }

    @Override
    public void onUpdateBatchInput(final InputPointers batchPointers) {
        mInputLogic.onUpdateBatchInput(mSettings.getCurrent(), batchPointers, mKeyboardSwitcher,
                mInputUpdater);
    }

    @Override
    public void onEndBatchInput(final InputPointers batchPointers) {
        mInputLogic.onEndBatchInput(mSettings.getCurrent(), batchPointers, mInputUpdater);
    }

    @Override
    public void onCancelBatchInput() {
        mInputLogic.onCancelBatchInput(mHandler, mInputUpdater);
    }

    // TODO[IL]: Make this a package-private standalone class in inputlogic/ and remove all
    // references to it in LatinIME
    public static final class InputUpdater implements Handler.Callback {
        private final Handler mHandler;
        private final LatinIME mLatinIme;
        private final Object mLock = new Object();
        private boolean mInBatchInput; // synchronized using {@link #mLock}.

        InputUpdater(final LatinIME latinIme) {
            final HandlerThread handlerThread = new HandlerThread(
                    InputUpdater.class.getSimpleName());
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper(), this);
            mLatinIme = latinIme;
        }

        private static final int MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP = 1;
        private static final int MSG_GET_SUGGESTED_WORDS = 2;

        // Called on the InputUpdater thread by the Handler code.
        @Override
        public boolean handleMessage(final Message msg) {
            // TODO: straighten message passing - we don't need two kinds of messages calling
            // each other.
            switch (msg.what) {
                case MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP:
                    updateBatchInput((InputPointers)msg.obj, msg.arg2 /* sequenceNumber */,
                            false /* forEnd */);
                    break;
                case MSG_GET_SUGGESTED_WORDS:
                    mLatinIme.getSuggestedWords(msg.arg1 /* sessionId */,
                            msg.arg2 /* sequenceNumber */, (OnGetSuggestedWordsCallback) msg.obj);
                    break;
            }
            return true;
        }

        // Called on the UI thread by LatinIME.
        public void onStartBatchInput() {
            synchronized (mLock) {
                mHandler.removeMessages(MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP);
                mInBatchInput = true;
            }
        }

        /**
         * Fetch suggestions corresponding to an update of a batch input.
         * @param batchPointers the updated pointers, including the part that was passed last time.
         * @param sequenceNumber the sequence number associated with this batch input.
         * @param forEnd true if this is the end of a batch input, false if it's an update.
         */
        // This method can be called from any thread and will see to it that the correct threads
        // are used for parts that require it. This method will send a message to the
        // InputUpdater thread to pull suggestions, and get the inlined callback to get called
        // on the InputUpdater thread. The callback will then proceed to send a message to the
        // UI handler in LatinIME so that showing suggestions can be done on the UI thread.
        private void updateBatchInput(final InputPointers batchPointers,
                final int sequenceNumber, final boolean forEnd) {
            synchronized (mLock) {
                if (!mInBatchInput) {
                    // Batch input has ended or canceled while the message was being delivered.
                    return;
                }
                getSuggestedWordsGestureLocked(batchPointers, sequenceNumber,
                        new OnGetSuggestedWordsCallback() {
                            @Override
                            public void onGetSuggestedWords(final SuggestedWords suggestedWords) {
                                // We're now inside the callback. This always runs on the
                                // InputUpdater thread, no matter what thread updateBatchInput
                                // was called on.
                                mLatinIme.mHandler.showGesturePreviewAndSuggestionStrip(
                                        suggestedWords,
                                        forEnd /* dismissGestureFloatingPreviewText */);
                                if (forEnd) {
                                    mInBatchInput = false;
                                    // The following call schedules onEndBatchInputAsyncInternal
                                    // to be called on the UI thread.
                                    mLatinIme.mHandler.onEndBatchInput(suggestedWords);
                                }
                            }
                        });
            }
        }

        /**
         * Update a batch input.
         *
         * This fetches suggestions and updates the suggestion strip and the floating text preview.
         *
         * @param batchPointers the updated batch pointers.
         * @param sequenceNumber the sequence number associated with this batch input.
         */
        // Called on the UI thread by LatinIME.
        public void onUpdateBatchInput(final InputPointers batchPointers,
                final int sequenceNumber) {
            if (mHandler.hasMessages(MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP)) {
                return;
            }
            mHandler.obtainMessage(MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP, 0 /* arg1 */,
                    sequenceNumber /* arg2 */, batchPointers /* obj */).sendToTarget();
        }

        /**
         * Cancel a batch input.
         *
         * Note that as opposed to onEndBatchInput, we do the UI side of this immediately on the
         * same thread, rather than get this to call a method in LatinIME. This is because
         * cancelling a batch input does not necessitate the long operation of pulling suggestions.
         */
        // Called on the UI thread by LatinIME.
        public void onCancelBatchInput() {
            synchronized (mLock) {
                mInBatchInput = false;
            }
        }

        /**
         * Finish a batch input.
         *
         * This fetches suggestions, updates the suggestion strip and commits the first suggestion.
         * It also dismisses the floating text preview.
         *
         * @param batchPointers the updated batch pointers.
         * @param sequenceNumber the sequence number associated with this batch input.
         */
        // Called on the UI thread by LatinIME.
        public void onEndBatchInput(final InputPointers batchPointers, final int sequenceNumber) {
            updateBatchInput(batchPointers, sequenceNumber, true /* forEnd */);
        }

        // {@link LatinIME#getSuggestedWords(int)} method calls with same session id have to
        // be synchronized.
        private void getSuggestedWordsGestureLocked(final InputPointers batchPointers,
                final int sequenceNumber, final OnGetSuggestedWordsCallback callback) {
            mLatinIme.mInputLogic.mWordComposer.setBatchInputPointers(batchPointers);
            getSuggestedWords(Suggest.SESSION_GESTURE, sequenceNumber,
                    new OnGetSuggestedWordsCallback() {
                        @Override
                        public void onGetSuggestedWords(SuggestedWords suggestedWords) {
                            if (suggestedWords.isEmpty()) {
                                // Previous suggestions are found in InputLogic#mSuggestedWords.
                                // Since these are the most recent ones and we just recomputed new
                                // ones to update them, it means the previous ones are there.
                                callback.onGetSuggestedWords(mLatinIme.mInputLogic.mSuggestedWords);
                            } else {
                                callback.onGetSuggestedWords(suggestedWords);
                            }
                        }
                    });
        }

        public void getSuggestedWords(final int sessionId, final int sequenceNumber,
                final OnGetSuggestedWordsCallback callback) {
            mHandler.obtainMessage(MSG_GET_SUGGESTED_WORDS, sessionId, sequenceNumber, callback)
                    .sendToTarget();
        }

        void quitLooper() {
            mHandler.removeMessages(MSG_GET_SUGGESTED_WORDS);
            mHandler.removeMessages(MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP);
            mHandler.getLooper().quit();
        }
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

    // This method must run on the UI Thread.
    public void onEndBatchInputAsyncInternal(final SuggestedWords suggestedWords) {
        final String batchInputText = suggestedWords.isEmpty() ? null : suggestedWords.getWord(0);
        if (TextUtils.isEmpty(batchInputText)) {
            return;
        }
        mInputLogic.mConnection.beginBatchEdit();
        if (SpaceState.PHANTOM == mInputLogic.mSpaceState) {
            mInputLogic.promotePhantomSpace(mSettings.getCurrent());
        }
        if (mSettings.getCurrent().mPhraseGestureEnabled) {
            // Find the last space
            final int indexOfLastSpace = batchInputText.lastIndexOf(Constants.CODE_SPACE) + 1;
            if (0 != indexOfLastSpace) {
                mInputLogic.mConnection.commitText(batchInputText.substring(0, indexOfLastSpace),
                        1);
                showSuggestionStrip(suggestedWords.getSuggestedWordsForLastWordOfPhraseGesture());
            }
            final String lastWord = batchInputText.substring(indexOfLastSpace);
            mInputLogic.mWordComposer.setBatchInputWord(lastWord);
            mInputLogic.mConnection.setComposingText(lastWord, 1);
        } else {
            mInputLogic.mWordComposer.setBatchInputWord(batchInputText);
            mInputLogic.mConnection.setComposingText(batchInputText, 1);
        }
        mInputLogic.mConnection.endBatchEdit();
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onEndBatchInput(batchInputText, 0, suggestedWords);
        }
        // Space state must be updated before calling updateShiftState
        mInputLogic.mSpaceState = SpaceState.PHANTOM;
        mKeyboardSwitcher.updateShiftState();
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onFinishSlidingInput() {
        // User finished sliding input.
        mKeyboardSwitcher.onFinishSlidingInput();
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onCancelInput() {
        // User released a finger outside any key
        // Nothing to do so far.
    }

    // TODO[IL]: Move this to InputLogic and make it private
    // Outside LatinIME, only used by the test suite.
    @UsedForTesting
    public boolean isShowingPunctuationList() {
        if (mInputLogic.mSuggestedWords == null) return false;
        return mSettings.getCurrent().mSuggestPuncList == mInputLogic.mSuggestedWords;
    }

    // TODO[IL]: Define a clear interface for this
    public boolean isSuggestionsStripVisible() {
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (mSuggestionStripView == null)
            return false;
        if (mSuggestionStripView.isShowingAddToDictionaryHint())
            return true;
        if (null == currentSettings)
            return false;
        if (!currentSettings.isSuggestionStripVisible())
            return false;
        if (currentSettings.isApplicationSpecifiedCompletionsOn())
            return true;
        return currentSettings.isSuggestionsRequested();
    }

    public void dismissAddToDictionaryHint() {
        if (null != mSuggestionStripView) {
            mSuggestionStripView.dismissAddToDictionaryHint();
        }
    }

    // TODO[IL]: Define a clear interface for this
    public void clearSuggestionStrip() {
        setSuggestedWords(SuggestedWords.EMPTY);
        setAutoCorrectionIndicator(false);
    }

    // TODO[IL]: Define a clear interface for this
    public void setSuggestedWords(final SuggestedWords words) {
        mInputLogic.mSuggestedWords = words;
        if (mSuggestionStripView != null) {
            mSuggestionStripView.setSuggestions(words);
            mKeyboardSwitcher.onAutoCorrectionStateChanged(words.mWillAutoCorrect);
        }
    }

    private void setAutoCorrectionIndicator(final boolean newAutoCorrectionIndicator) {
        // Put a blue underline to a word in TextView which will be auto-corrected.
        if (mInputLogic.mIsAutoCorrectionIndicatorOn != newAutoCorrectionIndicator
                && mInputLogic.mWordComposer.isComposingWord()) {
            mInputLogic.mIsAutoCorrectionIndicatorOn = newAutoCorrectionIndicator;
            final CharSequence textWithUnderline =
                    mInputLogic.getTextWithUnderline(mInputLogic.mWordComposer.getTypedWord());
            // TODO: when called from an updateSuggestionStrip() call that results from a posted
            // message, this is called outside any batch edit. Potentially, this may result in some
            // janky flickering of the screen, although the display speed makes it unlikely in
            // the practice.
            mInputLogic.mConnection.setComposingText(textWithUnderline, 1);
        }
    }

    private void getSuggestedWords(final int sessionId, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        final Suggest suggest = mInputLogic.mSuggest;
        if (keyboard == null || suggest == null) {
            callback.onGetSuggestedWords(SuggestedWords.EMPTY);
            return;
        }
        // Get the word on which we should search the bigrams. If we are composing a word, it's
        // whatever is *before* the half-committed word in the buffer, hence 2; if we aren't, we
        // should just skip whitespace if any, so 1.
        final SettingsValues currentSettings = mSettings.getCurrent();
        final int[] additionalFeaturesOptions = currentSettings.mAdditionalFeaturesSettingValues;

        if (DEBUG) {
            if (mInputLogic.mWordComposer.isComposingWord()
                    || mInputLogic.mWordComposer.isBatchMode()) {
                final String previousWord
                        = mInputLogic.mWordComposer.getPreviousWordForSuggestion();
                // TODO: this is for checking consistency with older versions. Remove this when
                // we are confident this is stable.
                // We're checking the previous word in the text field against the memorized previous
                // word. If we are composing a word we should have the second word before the cursor
                // memorized, otherwise we should have the first.
                final String rereadPrevWord = mInputLogic.getNthPreviousWordForSuggestion(
                        currentSettings, mInputLogic.mWordComposer.isComposingWord() ? 2 : 1);
                if (!TextUtils.equals(previousWord, rereadPrevWord)) {
                    throw new RuntimeException("Unexpected previous word: "
                            + previousWord + " <> " + rereadPrevWord);
                }
            }
        }
        suggest.getSuggestedWords(mInputLogic.mWordComposer,
                mInputLogic.mWordComposer.getPreviousWordForSuggestion(),
                keyboard.getProximityInfo(),
                currentSettings.mBlockPotentiallyOffensive, currentSettings.mCorrectionEnabled,
                additionalFeaturesOptions, sessionId, sequenceNumber, callback);
    }

    // TODO[IL]: Move this to InputLogic
    public SuggestedWords maybeRetrieveOlderSuggestions(final String typedWord,
            final SuggestedWords suggestedWords) {
        // TODO: consolidate this into getSuggestedWords
        // We update the suggestion strip only when we have some suggestions to show, i.e. when
        // the suggestion count is > 1; else, we leave the old suggestions, with the typed word
        // replaced with the new one. However, when the length of the typed word is 1 or 0 (after
        // a deletion typically), we do want to remove the old suggestions. Also, if we are showing
        // the "add to dictionary" hint, we need to revert to suggestions - although it is unclear
        // how we can come here if it's displayed.
        if (suggestedWords.size() > 1 || typedWord.length() <= 1
                || null == mSuggestionStripView
                || mSuggestionStripView.isShowingAddToDictionaryHint()) {
            return suggestedWords;
        } else {
            return getOlderSuggestions(typedWord);
        }
    }

    private SuggestedWords getOlderSuggestions(final String typedWord) {
        SuggestedWords previousSuggestedWords = mInputLogic.mSuggestedWords;
        if (previousSuggestedWords == mSettings.getCurrent().mSuggestPuncList) {
            previousSuggestedWords = SuggestedWords.EMPTY;
        }
        if (typedWord == null) {
            return previousSuggestedWords;
        }
        final ArrayList<SuggestedWords.SuggestedWordInfo> typedWordAndPreviousSuggestions =
                SuggestedWords.getTypedWordAndPreviousSuggestions(typedWord,
                        previousSuggestedWords);
        return new SuggestedWords(typedWordAndPreviousSuggestions,
                false /* typedWordValid */,
                false /* hasAutoCorrectionCandidate */,
                false /* isPunctuationSuggestions */,
                true /* isObsoleteSuggestions */,
                false /* isPrediction */);
    }

    private void showSuggestionStripWithTypedWord(final SuggestedWords suggestedWords,
            final String typedWord) {
      if (suggestedWords.isEmpty()) {
          // No auto-correction is available, clear the cached values.
          AccessibilityUtils.getInstance().setAutoCorrection(null, null);
          clearSuggestionStrip();
          return;
      }
      final String autoCorrection;
      if (suggestedWords.mWillAutoCorrect) {
          autoCorrection = suggestedWords.getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION);
      } else {
          // We can't use suggestedWords.getWord(SuggestedWords.INDEX_OF_TYPED_WORD)
          // because it may differ from mWordComposer.mTypedWord.
          autoCorrection = typedWord;
      }
      mInputLogic.mWordComposer.setAutoCorrection(autoCorrection);
      setSuggestedWords(suggestedWords);
      setAutoCorrectionIndicator(suggestedWords.mWillAutoCorrect);
      setSuggestionStripShown(isSuggestionsStripVisible());
      // An auto-correction is available, cache it in accessibility code so
      // we can be speak it if the user touches a key that will insert it.
      AccessibilityUtils.getInstance().setAutoCorrection(suggestedWords, typedWord);
    }

    // TODO[IL]: Define a clean interface for this
    public void showSuggestionStrip(final SuggestedWords suggestedWords) {
        if (suggestedWords.isEmpty()) {
            clearSuggestionStrip();
            return;
        }
        showSuggestionStripWithTypedWord(suggestedWords,
            suggestedWords.getWord(SuggestedWords.INDEX_OF_TYPED_WORD));
    }

    // Called from {@link SuggestionStripView} through the {@link SuggestionStripView#Listener}
    // interface
    @Override
    public void pickSuggestionManually(final int index, final SuggestedWordInfo suggestionInfo) {
        final SuggestedWords suggestedWords = mInputLogic.mSuggestedWords;
        final String suggestion = suggestionInfo.mWord;
        // If this is a punctuation picked from the suggestion strip, pass it to onCodeInput
        if (suggestion.length() == 1 && isShowingPunctuationList()) {
            // Word separators are suggested before the user inputs something.
            // So, LatinImeLogger logs "" as a user's input.
            LatinImeLogger.logOnManualSuggestion("", suggestion, index, suggestedWords);
            // Rely on onCodeInput to do the complicated swapping/stripping logic consistently.
            final int primaryCode = suggestion.charAt(0);
            onCodeInput(primaryCode,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_punctuationSuggestion(index, suggestion,
                        false /* isBatchMode */, suggestedWords.mIsPrediction);
            }
            return;
        }

        mInputLogic.mConnection.beginBatchEdit();
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (SpaceState.PHANTOM == mInputLogic.mSpaceState && suggestion.length() > 0
                // In the batch input mode, a manually picked suggested word should just replace
                // the current batch input text and there is no need for a phantom space.
                && !mInputLogic.mWordComposer.isBatchMode()) {
            final int firstChar = Character.codePointAt(suggestion, 0);
            if (!currentSettings.isWordSeparator(firstChar)
                    || currentSettings.isUsuallyPrecededBySpace(firstChar)) {
                mInputLogic.promotePhantomSpace(currentSettings);
            }
        }

        if (currentSettings.isApplicationSpecifiedCompletionsOn()
                && mApplicationSpecifiedCompletions != null
                && index >= 0 && index < mApplicationSpecifiedCompletions.length) {
            mInputLogic.mSuggestedWords = SuggestedWords.EMPTY;
            if (mSuggestionStripView != null) {
                mSuggestionStripView.clear();
            }
            mKeyboardSwitcher.updateShiftState();
            mInputLogic.resetComposingState(true /* alsoResetLastComposedWord */);
            final CompletionInfo completionInfo = mApplicationSpecifiedCompletions[index];
            mInputLogic.mConnection.commitCompletion(completionInfo);
            mInputLogic.mConnection.endBatchEdit();
            return;
        }

        // We need to log before we commit, because the word composer will store away the user
        // typed word.
        final String replacedWord = mInputLogic.mWordComposer.getTypedWord();
        LatinImeLogger.logOnManualSuggestion(replacedWord, suggestion, index, suggestedWords);
        mInputLogic.commitChosenWord(currentSettings, suggestion,
                LastComposedWord.COMMIT_TYPE_MANUAL_PICK, LastComposedWord.NOT_A_SEPARATOR);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_pickSuggestionManually(replacedWord, index, suggestion,
                    mInputLogic.mWordComposer.isBatchMode(), suggestionInfo.mScore,
                    suggestionInfo.mKind, suggestionInfo.mSourceDict.mDictType);
        }
        mInputLogic.mConnection.endBatchEdit();
        // Don't allow cancellation of manual pick
        mInputLogic.mLastComposedWord.deactivate();
        // Space state must be updated before calling updateShiftState
        mInputLogic.mSpaceState = SpaceState.PHANTOM;
        mKeyboardSwitcher.updateShiftState();

        // We should show the "Touch again to save" hint if the user pressed the first entry
        // AND it's in none of our current dictionaries (main, user or otherwise).
        // Please note that if mSuggest is null, it means that everything is off: suggestion
        // and correction, so we shouldn't try to show the hint
        final Suggest suggest = mInputLogic.mSuggest;
        final boolean showingAddToDictionaryHint =
                (SuggestedWordInfo.KIND_TYPED == suggestionInfo.mKind
                        || SuggestedWordInfo.KIND_OOV_CORRECTION == suggestionInfo.mKind)
                        && suggest != null
                        // If the suggestion is not in the dictionary, the hint should be shown.
                        && !suggest.mDictionaryFacilitator.isValidWord(suggestion,
                                true /* ignoreCase */);

        if (currentSettings.mIsInternal) {
            LatinImeLoggerUtils.onSeparator((char)Constants.CODE_SPACE,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        }
        if (showingAddToDictionaryHint
                && suggest.mDictionaryFacilitator.isUserDictionaryEnabled()) {
            mSuggestionStripView.showAddToDictionaryHint(
                    suggestion, currentSettings.mHintToSaveText);
        } else {
            // If we're not showing the "Touch again to save", then update the suggestion strip.
            mHandler.postUpdateSuggestionStrip();
        }
    }

    // TODO[IL]: Define a clean interface for this
    public void setPunctuationSuggestions() {
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (currentSettings.mBigramPredictionEnabled) {
            clearSuggestionStrip();
        } else {
            setSuggestedWords(currentSettings.mSuggestPuncList);
        }
        setAutoCorrectionIndicator(false);
        setSuggestionStripShown(isSuggestionsStripVisible());
    }

    public void unsetIsAutoCorrectionIndicatorOnAndCallShowSuggestionStrip(
            final SuggestedWords suggestedWords, final String typedWord) {
        // Note that it's very important here that suggestedWords.mWillAutoCorrect is false.
        // We never want to auto-correct on a resumed suggestion. Please refer to the three places
        // above in restartSuggestionsOnWordTouchedByCursor() where suggestedWords is affected.
        // We also need to unset mIsAutoCorrectionIndicatorOn to avoid showSuggestionStrip touching
        // the text to adapt it.
        // TODO: remove mIsAutoCorrectionIndicatorOn (see comment on definition)
        mInputLogic.mIsAutoCorrectionIndicatorOn = false;
        mHandler.showSuggestionStripWithTypedWord(suggestedWords, typedWord);
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
            mKeyboardSwitcher.loadKeyboard(getCurrentInputEditorInfo(), mSettings.getCurrent());
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
        mKeyboardSwitcher.onPressKey(primaryCode, isSinglePointer);
        hapticAndAudioFeedback(primaryCode, repeatCount);
    }

    // Callback of the {@link KeyboardActionListener}. This is called when a key is released;
    // press matching call is {@link #onPressKey(int,int,boolean)} above.
    @Override
    public void onReleaseKey(final int primaryCode, final boolean withSliding) {
        mKeyboardSwitcher.onReleaseKey(primaryCode, withSliding);

        // If accessibility is on, ensure the user receives keyboard state updates.
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            switch (primaryCode) {
            case Constants.CODE_SHIFT:
                AccessibleKeyboardViewProxy.getInstance().notifyShiftState();
                break;
            case Constants.CODE_SWITCH_ALPHA_SYMBOL:
                AccessibleKeyboardViewProxy.getInstance().notifySymbolsState();
                break;
            }
        }
    }

    // Hooks for hardware keyboard
    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (!ProductionFlag.IS_HARDWARE_KEYBOARD_SUPPORTED) return super.onKeyDown(keyCode, event);
        // onHardwareKeyEvent, like onKeyDown returns true if it handled the event, false if
        // it doesn't know what to do with it and leave it to the application. For example,
        // hardware key events for adjusting the screen's brightness are passed as is.
        if (mInputLogic.mEventInterpreter.onHardwareKeyEvent(event)) {
            final long keyIdentifier = event.getDeviceId() << 32 + event.getKeyCode();
            mInputLogic.mCurrentlyPressedHardwareKeys.add(keyIdentifier);
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
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
        launchSubActivity(SettingsActivity.class);
    }

    public void launchKeyboardedDialogActivity(final Class<? extends Activity> activityClass) {
        // Put the text in the attached EditText into a safe, saved state before switching to a
        // new activity that will also use the soft keyboard.
        mInputLogic.commitTyped(mSettings.getCurrent(), LastComposedWord.NOT_A_SEPARATOR);
        launchSubActivity(activityClass);
    }

    private void launchSubActivity(final Class<? extends Activity> activityClass) {
        Intent intent = new Intent();
        intent.setClass(LatinIME.this, activityClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void showSubtypeSelectorAndSettings() {
        final CharSequence title = getString(R.string.english_ime_input_options);
        final CharSequence[] items = new CharSequence[] {
                // TODO: Should use new string "Select active input modes".
                getString(R.string.language_selection_title),
                getString(ApplicationUtils.getActivityTitleResId(this, SettingsActivity.class)),
        };
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
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
                    startActivity(intent);
                    break;
                case 1:
                    launchSettings();
                    break;
                }
            }
        };
        final AlertDialog.Builder builder =
                new AlertDialog.Builder(this).setItems(items, listener).setTitle(title);
        showOptionDialog(builder.create());
    }

    public void showOptionDialog(final AlertDialog dialog) {
        final IBinder windowToken = mKeyboardSwitcher.getMainKeyboardView().getWindowToken();
        if (windowToken == null) {
            return;
        }

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

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
    /* package for test */ SuggestedWords getSuggestedWords() {
        // You may not use this method for anything else than debug
        return DEBUG ? mInputLogic.mSuggestedWords : null;
    }

    // DO NOT USE THIS for any other purpose than testing. This is information private to LatinIME.
    @UsedForTesting
    /* package for test */ boolean isCurrentlyWaitingForMainDictionary() {
        return mInputLogic.mSuggest.mDictionaryFacilitator.isCurrentlyWaitingForMainDictionary();
    }

    // DO NOT USE THIS for any other purpose than testing. This can break the keyboard badly.
    @UsedForTesting
    /* package for test */ void replaceMainDictionaryForTest(final Locale locale) {
        mInputLogic.mSuggest.mDictionaryFacilitator.reloadMainDict(this, locale, null);
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
        p.println("  mIsSuggestionsRequested = " + settingsValues.isSuggestionsRequested());
        p.println("  mCorrectionEnabled=" + settingsValues.mCorrectionEnabled);
        p.println("  isComposingWord=" + mInputLogic.mWordComposer.isComposingWord());
        p.println("  mSoundOn=" + settingsValues.mSoundOn);
        p.println("  mVibrateOn=" + settingsValues.mVibrateOn);
        p.println("  mKeyPreviewPopupOn=" + settingsValues.mKeyPreviewPopupOn);
        p.println("  inputAttributes=" + settingsValues.mInputAttributes);
        // TODO: Dump all settings values
    }
}
