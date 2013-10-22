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
import android.content.pm.PackageInfo;
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
import android.text.style.SuggestionSpan;
import android.util.Log;
import android.util.Pair;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.compat.AppWorkaroundsUtils;
import com.android.inputmethod.compat.InputMethodServiceCompatUtils;
import com.android.inputmethod.compat.SuggestionSpanUtils;
import com.android.inputmethod.dictionarypack.DictionaryPackConstants;
import com.android.inputmethod.event.EventInterpreter;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.personalization.DictionaryDecayBroadcastReciever;
import com.android.inputmethod.latin.personalization.PersonalizationDictionary;
import com.android.inputmethod.latin.personalization.PersonalizationDictionarySessionRegister;
import com.android.inputmethod.latin.personalization.PersonalizationHelper;
import com.android.inputmethod.latin.personalization.PersonalizationPredictionDictionary;
import com.android.inputmethod.latin.personalization.UserHistoryDictionary;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsActivity;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.suggestions.SuggestionStripView;
import com.android.inputmethod.latin.utils.ApplicationUtils;
import com.android.inputmethod.latin.utils.AsyncResultHolder;
import com.android.inputmethod.latin.utils.AutoCorrectionUtils;
import com.android.inputmethod.latin.utils.CapsModeUtils;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.CompletionInfoUtils;
import com.android.inputmethod.latin.utils.InputTypeUtils;
import com.android.inputmethod.latin.utils.IntentUtils;
import com.android.inputmethod.latin.utils.JniUtils;
import com.android.inputmethod.latin.utils.LatinImeLoggerUtils;
import com.android.inputmethod.latin.utils.RecapitalizeStatus;
import com.android.inputmethod.latin.utils.StaticInnerHandlerWrapper;
import com.android.inputmethod.latin.utils.StringUtils;
import com.android.inputmethod.latin.utils.TargetPackageInfoGetterTask;
import com.android.inputmethod.latin.utils.TextRange;
import com.android.inputmethod.latin.utils.UserHistoryForgettingCurveUtils;
import com.android.inputmethod.research.ResearchLogger;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService implements KeyboardActionListener,
        SuggestionStripView.Listener, TargetPackageInfoGetterTask.OnTargetPackageInfoKnownListener,
        Suggest.SuggestInitializationListener {
    private static final String TAG = LatinIME.class.getSimpleName();
    private static final boolean TRACE = false;
    private static boolean DEBUG;

    private static final int EXTENDED_TOUCHABLE_REGION_HEIGHT = 100;

    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200;

    private static final int PENDING_IMS_CALLBACK_DURATION = 800;

    private static final int PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT = 2;

    // TODO: Set this value appropriately.
    private static final int GET_SUGGESTED_WORDS_TIMEOUT = 200;

    /**
     * The name of the scheme used by the Package Manager to warn of a new package installation,
     * replacement or removal.
     */
    private static final String SCHEME_PACKAGE = "package";

    private static final int SPACE_STATE_NONE = 0;
    // Double space: the state where the user pressed space twice quickly, which LatinIME
    // resolved as period-space. Undoing this converts the period to a space.
    private static final int SPACE_STATE_DOUBLE = 1;
    // Swap punctuation: the state where a weak space and a punctuation from the suggestion strip
    // have just been swapped. Undoing this swaps them back; the space is still considered weak.
    private static final int SPACE_STATE_SWAP_PUNCTUATION = 2;
    // Weak space: a space that should be swapped only by suggestion strip punctuation. Weak
    // spaces happen when the user presses space, accepting the current suggestion (whether
    // it's an auto-correction or not).
    private static final int SPACE_STATE_WEAK = 3;
    // Phantom space: a not-yet-inserted space that should get inserted on the next input,
    // character provided it's not a separator. If it's a separator, the phantom space is dropped.
    // Phantom spaces happen when a user chooses a word from the suggestion strip.
    private static final int SPACE_STATE_PHANTOM = 4;

    // Current space state of the input method. This can be any of the above constants.
    private int mSpaceState;

    private final Settings mSettings;

    private View mExtractArea;
    private View mKeyPreviewBackingView;
    private SuggestionStripView mSuggestionStripView;
    // Never null
    private SuggestedWords mSuggestedWords = SuggestedWords.EMPTY;
    private Suggest mSuggest;
    private CompletionInfo[] mApplicationSpecifiedCompletions;
    private AppWorkaroundsUtils mAppWorkAroundsUtils = new AppWorkaroundsUtils();

    private RichInputMethodManager mRichImm;
    @UsedForTesting final KeyboardSwitcher mKeyboardSwitcher;
    private final SubtypeSwitcher mSubtypeSwitcher;
    private final SubtypeState mSubtypeState = new SubtypeState();
    // At start, create a default event interpreter that does nothing by passing it no decoder spec.
    // The event interpreter should never be null.
    private EventInterpreter mEventInterpreter = new EventInterpreter(this);

    private boolean mIsMainDictionaryAvailable;
    private UserBinaryDictionary mUserDictionary;
    private UserHistoryDictionary mUserHistoryDictionary;
    private PersonalizationPredictionDictionary mPersonalizationPredictionDictionary;
    private PersonalizationDictionary mPersonalizationDictionary;
    private boolean mIsUserDictionaryAvailable;

    private LastComposedWord mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
    private final WordComposer mWordComposer = new WordComposer();
    private final RichInputConnection mConnection = new RichInputConnection(this);
    private final RecapitalizeStatus mRecapitalizeStatus = new RecapitalizeStatus();

    // Keep track of the last selection range to decide if we need to show word alternatives
    private static final int NOT_A_CURSOR_POSITION = -1;
    private int mLastSelectionStart = NOT_A_CURSOR_POSITION;
    private int mLastSelectionEnd = NOT_A_CURSOR_POSITION;

    // Whether we are expecting an onUpdateSelection event to fire. If it does when we don't
    // "expect" it, it means the user actually moved the cursor.
    private boolean mExpectingUpdateSelection;
    private int mDeleteCount;
    private long mLastKeyTime;
    private final TreeSet<Long> mCurrentlyPressedHardwareKeys = CollectionUtils.newTreeSet();
    // Personalization debugging params
    private boolean mUseOnlyPersonalizationDictionaryForDebug = false;
    private boolean mBoostPersonalizationDictionaryForDebug = false;

    // Member variables for remembering the current device orientation.
    private int mDisplayOrientation;

    // Object for reacting to adding/removing a dictionary pack.
    private BroadcastReceiver mDictionaryPackInstallReceiver =
            new DictionaryPackInstallBroadcastReceiver(this);

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private String mEnteredText;

    // TODO: This boolean is persistent state and causes large side effects at unexpected times.
    // Find a way to remove it for readability.
    private boolean mIsAutoCorrectionIndicatorOn;

    private AlertDialog mOptionsDialog;

    private final boolean mIsHardwareAcceleratedDrawingEnabled;

    public final UIHandler mHandler = new UIHandler(this);
    private InputUpdater mInputUpdater;

    public static final class UIHandler extends StaticInnerHandlerWrapper<LatinIME> {
        private static final int MSG_UPDATE_SHIFT_STATE = 0;
        private static final int MSG_PENDING_IMS_CALLBACK = 1;
        private static final int MSG_UPDATE_SUGGESTION_STRIP = 2;
        private static final int MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP = 3;
        private static final int MSG_RESUME_SUGGESTIONS = 4;
        private static final int MSG_REOPEN_DICTIONARIES = 5;
        private static final int MSG_ON_END_BATCH_INPUT = 6;
        private static final int MSG_RESET_CACHES = 7;

        private static final int ARG1_NOT_GESTURE_INPUT = 0;
        private static final int ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 1;
        private static final int ARG1_SHOW_GESTURE_FLOATING_PREVIEW_TEXT = 2;
        private static final int ARG2_WITHOUT_TYPED_WORD = 0;
        private static final int ARG2_WITH_TYPED_WORD = 1;

        private int mDelayUpdateSuggestions;
        private int mDelayUpdateShiftState;
        private long mDoubleSpacePeriodTimeout;
        private long mDoubleSpacePeriodTimerStart;

        public UIHandler(final LatinIME outerInstance) {
            super(outerInstance);
        }

        public void onCreate() {
            final Resources res = getOuterInstance().getResources();
            mDelayUpdateSuggestions =
                    res.getInteger(R.integer.config_delay_update_suggestions);
            mDelayUpdateShiftState =
                    res.getInteger(R.integer.config_delay_update_shift_state);
            mDoubleSpacePeriodTimeout =
                    res.getInteger(R.integer.config_double_space_period_timeout);
        }

        @Override
        public void handleMessage(final Message msg) {
            final LatinIME latinIme = getOuterInstance();
            final KeyboardSwitcher switcher = latinIme.mKeyboardSwitcher;
            switch (msg.what) {
            case MSG_UPDATE_SUGGESTION_STRIP:
                latinIme.updateSuggestionStrip();
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
                latinIme.restartSuggestionsOnWordTouchedByCursor();
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
                latinIme.retryResetCaches(msg.arg1 == 1 /* tryResumeSuggestions */,
                        msg.arg2 /* remainingTries */);
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
            final LatinIME latinIme = getOuterInstance();
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
                final LatinIME latinIme = getOuterInstance();
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
                final LatinIME latinIme = getOuterInstance();
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
                final LatinIME latinIme = getOuterInstance();
                latinIme.onFinishInputViewInternal(finishingInput);
                mAppliedEditorInfo = null;
            }
        }

        public void onFinishInput() {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInput after orientation changed.
                mHasPendingFinishInput = true;
            } else {
                final LatinIME latinIme = getOuterInstance();
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
        PersonalizationDictionarySessionRegister.init(this);

        super.onCreate();

        mHandler.onCreate();
        DEBUG = LatinImeLogger.sDBG;

        // TODO: Resolve mutual dependencies of {@link #loadSettings()} and {@link #initSuggest()}.
        loadSettings();
        initSuggest();

        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.getInstance().init(this, mKeyboardSwitcher, mSuggest);
        }
        mDisplayOrientation = getResources().getConfiguration().orientation;

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
        final InputAttributes inputAttributes =
                new InputAttributes(getCurrentInputEditorInfo(), isFullscreenMode());
        mSettings.loadSettings(locale, inputAttributes);
        AudioAndHapticFeedbackManager.getInstance().onSettingsChanged(mSettings.getCurrent());
        // To load the keyboard we need to load all the settings once, but resetting the
        // contacts dictionary should be deferred until after the new layout has been displayed
        // to improve responsivity. In the language switching process, we post a reopenDictionaries
        // message, then come here to read the settings for the new language before we change
        // the layout; at this time, we need to skip resetting the contacts dictionary. It will
        // be done later inside {@see #initSuggest()} when the reopenDictionaries message is
        // processed.
        if (!mHandler.hasPendingReopenDictionaries()) {
            // May need to reset the contacts dictionary depending on the user settings.
            resetContactsDictionary(null == mSuggest ? null : mSuggest.getContactsDictionary());
        }
    }

    // Note that this method is called from a non-UI thread.
    @Override
    public void onUpdateMainDictionaryAvailability(final boolean isMainDictionaryAvailable) {
        mIsMainDictionaryAvailable = isMainDictionaryAvailable;
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.setMainDictionaryAvailability(isMainDictionaryAvailable);
        }
    }

    private void initSuggest() {
        final Locale switcherSubtypeLocale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        final String switcherLocaleStr = switcherSubtypeLocale.toString();
        final Locale subtypeLocale;
        final String localeStr;
        if (TextUtils.isEmpty(switcherLocaleStr)) {
            // This happens in very rare corner cases - for example, immediately after a switch
            // to LatinIME has been requested, about a frame later another switch happens. In this
            // case, we are about to go down but we still don't know it, however the system tells
            // us there is no current subtype so the locale is the empty string. Take the best
            // possible guess instead -- it's bound to have no consequences, and we have no way
            // of knowing anyway.
            Log.e(TAG, "System is reporting no current subtype.");
            subtypeLocale = getResources().getConfiguration().locale;
            localeStr = subtypeLocale.toString();
        } else {
            subtypeLocale = switcherSubtypeLocale;
            localeStr = switcherLocaleStr;
        }

        final Suggest newSuggest = new Suggest(this /* Context */, subtypeLocale,
                this /* SuggestInitializationListener */);
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (settingsValues.mCorrectionEnabled) {
            newSuggest.setAutoCorrectionThreshold(settingsValues.mAutoCorrectionThreshold);
        }

        mIsMainDictionaryAvailable = DictionaryFactory.isDictionaryAvailable(this, subtypeLocale);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.getInstance().initSuggest(newSuggest);
        }

        mUserDictionary = new UserBinaryDictionary(this, localeStr);
        mIsUserDictionaryAvailable = mUserDictionary.isEnabled();
        newSuggest.setUserDictionary(mUserDictionary);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mUserHistoryDictionary = PersonalizationHelper.getUserHistoryDictionary(
                this, localeStr, prefs);
        newSuggest.setUserHistoryDictionary(mUserHistoryDictionary);
        mPersonalizationDictionary = PersonalizationHelper
                .getPersonalizationDictionary(this, localeStr, prefs);
        newSuggest.setPersonalizationDictionary(mPersonalizationDictionary);
        mPersonalizationPredictionDictionary = PersonalizationHelper
                .getPersonalizationPredictionDictionary(this, localeStr, prefs);
        newSuggest.setPersonalizationPredictionDictionary(mPersonalizationPredictionDictionary);

        final Suggest oldSuggest = mSuggest;
        resetContactsDictionary(null != oldSuggest ? oldSuggest.getContactsDictionary() : null);
        mSuggest = newSuggest;
        if (oldSuggest != null) oldSuggest.close();
    }

    /**
     * Resets the contacts dictionary in mSuggest according to the user settings.
     *
     * This method takes an optional contacts dictionary to use when the locale hasn't changed
     * since the contacts dictionary can be opened or closed as necessary depending on the settings.
     *
     * @param oldContactsDictionary an optional dictionary to use, or null
     */
    private void resetContactsDictionary(final ContactsBinaryDictionary oldContactsDictionary) {
        final Suggest suggest = mSuggest;
        final boolean shouldSetDictionary =
                (null != suggest && mSettings.getCurrent().mUseContactsDict);

        final ContactsBinaryDictionary dictionaryToUse;
        if (!shouldSetDictionary) {
            // Make sure the dictionary is closed. If it is already closed, this is a no-op,
            // so it's safe to call it anyways.
            if (null != oldContactsDictionary) oldContactsDictionary.close();
            dictionaryToUse = null;
        } else {
            final Locale locale = mSubtypeSwitcher.getCurrentSubtypeLocale();
            if (null != oldContactsDictionary) {
                if (!oldContactsDictionary.mLocale.equals(locale)) {
                    // If the locale has changed then recreate the contacts dictionary. This
                    // allows locale dependent rules for handling bigram name predictions.
                    oldContactsDictionary.close();
                    dictionaryToUse = new ContactsBinaryDictionary(this, locale);
                } else {
                    // Make sure the old contacts dictionary is opened. If it is already open,
                    // this is a no-op, so it's safe to call it anyways.
                    oldContactsDictionary.reopen(this);
                    dictionaryToUse = oldContactsDictionary;
                }
            } else {
                dictionaryToUse = new ContactsBinaryDictionary(this, locale);
            }
        }

        if (null != suggest) {
            suggest.setContactsDictionary(dictionaryToUse);
        }
    }

    /* package private */ void resetSuggestMainDict() {
        final Locale subtypeLocale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        mSuggest.resetMainDict(this, subtypeLocale, this /* SuggestInitializationListener */);
        mIsMainDictionaryAvailable = DictionaryFactory.isDictionaryAvailable(this, subtypeLocale);
    }

    @Override
    public void onDestroy() {
        final Suggest suggest = mSuggest;
        if (suggest != null) {
            suggest.close();
            mSuggest = null;
        }
        mSettings.onDestroy();
        unregisterReceiver(mReceiver);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.getInstance().onDestroy();
        }
        unregisterReceiver(mDictionaryPackInstallReceiver);
        PersonalizationDictionarySessionRegister.onDestroy(this);
        LatinImeLogger.commit();
        LatinImeLogger.onDestroy();
        if (mInputUpdater != null) {
            mInputUpdater.onDestroy();
            mInputUpdater = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(final Configuration conf) {
        // If orientation changed while predicting, commit the change
        if (mDisplayOrientation != conf.orientation) {
            mDisplayOrientation = conf.orientation;
            mHandler.startOrientationChanging();
            mConnection.beginBatchEdit();
            commitTyped(LastComposedWord.NOT_A_SEPARATOR);
            mConnection.finishComposingText();
            mConnection.endBatchEdit();
            if (isShowingOptionDialog()) {
                mOptionsDialog.dismiss();
            }
        }
        PersonalizationDictionarySessionRegister.onConfigurationChanged(this, conf);
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
        if (mSuggestionStripView != null)
            mSuggestionStripView.setListener(this, view);
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

        final PackageInfo packageInfo =
                TargetPackageInfoGetterTask.getCachedPackageInfo(editorInfo.packageName);
        mAppWorkAroundsUtils.setPackageInfo(packageInfo);
        if (null == packageInfo) {
            new TargetPackageInfoGetterTask(this /* context */, this /* listener */)
                    .execute(editorInfo.packageName);
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
        mEnteredText = null;
        resetComposingState(true /* alsoResetLastComposedWord */);
        mDeleteCount = 0;
        mSpaceState = SPACE_STATE_NONE;
        mRecapitalizeStatus.deactivate();
        mCurrentlyPressedHardwareKeys.clear();

        // Note: the following does a round-trip IPC on the main thread: be careful
        final Locale currentLocale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        final Suggest suggest = mSuggest;
        if (null != suggest && null != currentLocale && !currentLocale.equals(suggest.mLocale)) {
            initSuggest();
        }
        if (mSuggestionStripView != null) {
            // This will set the punctuation suggestions if next word suggestion is off;
            // otherwise it will clear the suggestion strip.
            setPunctuationSuggestions();
        }
        mSuggestedWords = SuggestedWords.EMPTY;

        // Sometimes, while rotating, for some reason the framework tells the app we are not
        // connected to it and that means we can't refresh the cache. In this case, schedule a
        // refresh later.
        final boolean canReachInputConnection;
        if (!mConnection.resetCachesUponCursorMoveAndReturnSuccess(editorInfo.initialSelStart,
                false /* shouldFinishComposition */)) {
            // We try resetting the caches up to 5 times before giving up.
            mHandler.postResetCaches(isDifferentTextField, 5 /* remainingTries */);
            canReachInputConnection = false;
        } else {
            if (isDifferentTextField) {
                mHandler.postResumeSuggestions();
            }
            canReachInputConnection = true;
        }

        if (isDifferentTextField) {
            mainKeyboardView.closing();
            loadSettings();
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

        mLastSelectionStart = editorInfo.initialSelStart;
        mLastSelectionEnd = editorInfo.initialSelEnd;
        // In some cases (namely, after rotation of the device) editorInfo.initialSelStart is lying
        // so we try using some heuristics to find out about these and fix them.
        tryFixLyingCursorPosition();

        mHandler.cancelUpdateSuggestionStrip();
        mHandler.cancelDoubleSpacePeriodTimer();

        mainKeyboardView.setMainDictionaryAvailability(mIsMainDictionaryAvailable);
        mainKeyboardView.setKeyPreviewPopupEnabled(currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay);
        mainKeyboardView.setSlidingKeyInputPreviewEnabled(
                currentSettingsValues.mSlidingKeyInputPreviewEnabled);
        mainKeyboardView.setGestureHandlingEnabledByUser(
                currentSettingsValues.mGestureInputEnabled,
                currentSettingsValues.mGestureTrailEnabled,
                currentSettingsValues.mGestureFloatingPreviewTextEnabled);

        initPersonalizationDebugSettings(currentSettingsValues);

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    /**
     * Try to get the text from the editor to expose lies the framework may have been
     * telling us. Concretely, when the device rotates, the frameworks tells us about where the
     * cursor used to be initially in the editor at the time it first received the focus; this
     * may be completely different from the place it is upon rotation. Since we don't have any
     * means to get the real value, try at least to ask the text view for some characters and
     * detect the most damaging cases: when the cursor position is declared to be much smaller
     * than it really is.
     */
    private void tryFixLyingCursorPosition() {
        final CharSequence textBeforeCursor =
                mConnection.getTextBeforeCursor(Constants.EDITOR_CONTENTS_CACHE_SIZE, 0);
        if (null == textBeforeCursor) {
            mLastSelectionStart = mLastSelectionEnd = NOT_A_CURSOR_POSITION;
        } else {
            final int textLength = textBeforeCursor.length();
            if (textLength > mLastSelectionStart
                    || (textLength < Constants.EDITOR_CONTENTS_CACHE_SIZE
                            && mLastSelectionStart < Constants.EDITOR_CONTENTS_CACHE_SIZE)) {
                mLastSelectionStart = textLength;
                // We can't figure out the value of mLastSelectionEnd :(
                // But at least if it's smaller than mLastSelectionStart something is wrong
                if (mLastSelectionStart > mLastSelectionEnd) {
                    mLastSelectionEnd = mLastSelectionStart;
                }
            }
        }
    }

    // Initialization of personalization debug settings. This must be called inside
    // onStartInputView.
    private void initPersonalizationDebugSettings(SettingsValues currentSettingsValues) {
        if (mUseOnlyPersonalizationDictionaryForDebug
                != currentSettingsValues.mUseOnlyPersonalizationDictionaryForDebug) {
            // Only for debug
            initSuggest();
            mUseOnlyPersonalizationDictionaryForDebug =
                    currentSettingsValues.mUseOnlyPersonalizationDictionaryForDebug;
        }

        if (mBoostPersonalizationDictionaryForDebug !=
                currentSettingsValues.mBoostPersonalizationDictionaryForDebug) {
            // Only for debug
            mBoostPersonalizationDictionaryForDebug =
                    currentSettingsValues.mBoostPersonalizationDictionaryForDebug;
            if (mBoostPersonalizationDictionaryForDebug) {
                UserHistoryForgettingCurveUtils.boostMaxFreqForDebug();
            } else {
                UserHistoryForgettingCurveUtils.resetMaxFreqForDebug();
            }
        }
    }

    // Callback for the TargetPackageInfoGetterTask
    @Override
    public void onTargetPackageInfoKnown(final PackageInfo info) {
        mAppWorkAroundsUtils.setPackageInfo(info);
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
        if (mWordComposer.isComposingWord()) mConnection.finishComposingText();
        resetComposingState(true /* alsoResetLastComposedWord */);
        // Notify ResearchLogger
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onFinishInputViewInternal(finishingInput, mLastSelectionStart,
                    mLastSelectionEnd, getCurrentInputConnection());
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
                    + ", lss=" + mLastSelectionStart
                    + ", lse=" + mLastSelectionEnd
                    + ", nss=" + newSelStart
                    + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart
                    + ", ce=" + composingSpanEnd);
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            final boolean expectingUpdateSelectionFromLogger =
                    ResearchLogger.getAndClearLatinIMEExpectingUpdateSelection();
            ResearchLogger.latinIME_onUpdateSelection(mLastSelectionStart, mLastSelectionEnd,
                    oldSelStart, oldSelEnd, newSelStart, newSelEnd, composingSpanStart,
                    composingSpanEnd, mExpectingUpdateSelection,
                    expectingUpdateSelectionFromLogger, mConnection);
            if (expectingUpdateSelectionFromLogger) {
                // TODO: Investigate. Quitting now sounds wrong - we won't do the resetting work
                return;
            }
        }

        final boolean selectionChanged = mLastSelectionStart != newSelStart
                || mLastSelectionEnd != newSelEnd;

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
        if (isInputViewShown() && !mExpectingUpdateSelection
                && !mConnection.isBelatedExpectedUpdate(oldSelStart, newSelStart)) {
            // TAKE CARE: there is a race condition when we enter this test even when the user
            // did not explicitly move the cursor. This happens when typing fast, where two keys
            // turn this flag on in succession and both onUpdateSelection() calls arrive after
            // the second one - the first call successfully avoids this test, but the second one
            // enters. For the moment we rely on noComposingSpan to further reduce the impact.

            // TODO: the following is probably better done in resetEntireInputState().
            // it should only happen when the cursor moved, and the very purpose of the
            // test below is to narrow down whether this happened or not. Likewise with
            // the call to updateShiftState.
            // We set this to NONE because after a cursor move, we don't want the space
            // state-related special processing to kick in.
            mSpaceState = SPACE_STATE_NONE;

            // TODO: is it still necessary to test for composingSpan related stuff?
            final boolean selectionChangedOrSafeToReset = selectionChanged
                    || (!mWordComposer.isComposingWord()) || noComposingSpan;
            final boolean hasOrHadSelection = (oldSelStart != oldSelEnd
                    || newSelStart != newSelEnd);
            final int moveAmount = newSelStart - oldSelStart;
            if (selectionChangedOrSafeToReset && (hasOrHadSelection
                    || !mWordComposer.moveCursorByAndReturnIfInsideComposingWord(moveAmount))) {
                // If we are composing a word and moving the cursor, we would want to set a
                // suggestion span for recorrection to work correctly. Unfortunately, that
                // would involve the keyboard committing some new text, which would move the
                // cursor back to where it was. Latin IME could then fix the position of the cursor
                // again, but the asynchronous nature of the calls results in this wreaking havoc
                // with selection on double tap and the like.
                // Another option would be to send suggestions each time we set the composing
                // text, but that is probably too expensive to do, so we decided to leave things
                // as is.
                resetEntireInputState(newSelStart);
            } else {
                // resetEntireInputState calls resetCachesUponCursorMove, but with the second
                // argument as true. But in all cases where we don't reset the entire input state,
                // we still want to tell the rich input connection about the new cursor position so
                // that it can update its caches.
                mConnection.resetCachesUponCursorMoveAndReturnSuccess(newSelStart,
                        false /* shouldFinishComposition */);
            }

            // We moved the cursor. If we are touching a word, we need to resume suggestion,
            // unless suggestions are off.
            if (isSuggestionsStripVisible()) {
                mHandler.postResumeSuggestions();
            }
            // Reset the last recapitalization.
            mRecapitalizeStatus.deactivate();
            mKeyboardSwitcher.updateShiftState();
        }
        mExpectingUpdateSelection = false;

        // Make a note of the cursor position
        mLastSelectionStart = newSelStart;
        mLastSelectionEnd = newSelEnd;
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
        if (mSettings.getCurrent().isSuggestionsRequested(mDisplayOrientation)) return;

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
        if (mSettings.getCurrent().isSuggestionsRequested(mDisplayOrientation)) return;

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
        final boolean isAutoCorrection = false;
        setSuggestedWords(suggestedWords, isAutoCorrection);
        setAutoCorrectionIndicator(isAutoCorrection);
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
        // Reread resource value here, because this method is called by framework anytime as needed.
        final boolean isFullscreenModeAllowed =
                Settings.readUseFullscreenMode(getResources());
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

    // This will reset the whole input state to the starting state. It will clear
    // the composing word, reset the last composed word, tell the inputconnection about it.
    private void resetEntireInputState(final int newCursorPosition) {
        final boolean shouldFinishComposition = mWordComposer.isComposingWord();
        resetComposingState(true /* alsoResetLastComposedWord */);
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (settingsValues.mBigramPredictionEnabled) {
            clearSuggestionStrip();
        } else {
            setSuggestedWords(settingsValues.mSuggestPuncList, false);
        }
        mConnection.resetCachesUponCursorMoveAndReturnSuccess(newCursorPosition,
                shouldFinishComposition);
    }

    private void resetComposingState(final boolean alsoResetLastComposedWord) {
        mWordComposer.reset();
        if (alsoResetLastComposedWord)
            mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
    }

    private void commitTyped(final String separatorString) {
        if (!mWordComposer.isComposingWord()) return;
        final String typedWord = mWordComposer.getTypedWord();
        if (typedWord.length() > 0) {
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.getInstance().onWordFinished(typedWord, mWordComposer.isBatchMode());
            }
            commitChosenWord(typedWord, LastComposedWord.COMMIT_TYPE_USER_TYPED_WORD,
                    separatorString);
        }
    }

    // Called from the KeyboardSwitcher which needs to know auto caps state to display
    // the right layout.
    public int getCurrentAutoCapsState() {
        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        if (!currentSettingsValues.mAutoCap) return Constants.TextUtils.CAP_MODE_OFF;

        final EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return Constants.TextUtils.CAP_MODE_OFF;
        final int inputType = ei.inputType;
        // Warning: this depends on mSpaceState, which may not be the most current value. If
        // mSpaceState gets updated later, whoever called this may need to be told about it.
        return mConnection.getCursorCapsMode(inputType, currentSettingsValues,
                SPACE_STATE_PHANTOM == mSpaceState);
    }

    public int getCurrentRecapitalizeState() {
        if (!mRecapitalizeStatus.isActive()
                || !mRecapitalizeStatus.isSetAt(mLastSelectionStart, mLastSelectionEnd)) {
            // Not recapitalizing at the moment
            return RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        }
        return mRecapitalizeStatus.getCurrentMode();
    }

    // Factor in auto-caps and manual caps and compute the current caps mode.
    private int getActualCapsMode() {
        final int keyboardShiftMode = mKeyboardSwitcher.getKeyboardShiftMode();
        if (keyboardShiftMode != WordComposer.CAPS_MODE_AUTO_SHIFTED) return keyboardShiftMode;
        final int auto = getCurrentAutoCapsState();
        if (0 != (auto & TextUtils.CAP_MODE_CHARACTERS)) {
            return WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED;
        }
        if (0 != auto) return WordComposer.CAPS_MODE_AUTO_SHIFTED;
        return WordComposer.CAPS_MODE_OFF;
    }

    private void swapSwapperAndSpace() {
        final CharSequence lastTwo = mConnection.getTextBeforeCursor(2, 0);
        // It is guaranteed lastTwo.charAt(1) is a swapper - else this method is not called.
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == Constants.CODE_SPACE) {
            mConnection.deleteSurroundingText(2, 0);
            final String text = lastTwo.charAt(1) + " ";
            mConnection.commitText(text, 1);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_swapSwapperAndSpace(lastTwo, text);
            }
            mKeyboardSwitcher.updateShiftState();
        }
    }

    private boolean maybeDoubleSpacePeriod() {
        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        if (!currentSettingsValues.mCorrectionEnabled) return false;
        if (!currentSettingsValues.mUseDoubleSpacePeriod) return false;
        if (!mHandler.isAcceptingDoubleSpacePeriod()) return false;
        // We only do this when we see two spaces and an accepted code point before the cursor.
        // The code point may be a surrogate pair but the two spaces may not, so we need 4 chars.
        final CharSequence lastThree = mConnection.getTextBeforeCursor(4, 0);
        if (null == lastThree) return false;
        final int length = lastThree.length();
        if (length < 3) return false;
        if (lastThree.charAt(length - 1) != Constants.CODE_SPACE) return false;
        if (lastThree.charAt(length - 2) != Constants.CODE_SPACE) return false;
        // We know there are spaces in pos -1 and -2, and we have at least three chars.
        // If we have only three chars, isSurrogatePairs can't return true as charAt(1) is a space,
        // so this is fine.
        final int firstCodePoint =
                Character.isSurrogatePair(lastThree.charAt(0), lastThree.charAt(1)) ?
                        Character.codePointAt(lastThree, 0) : lastThree.charAt(length - 3);
        if (canBeFollowedByDoubleSpacePeriod(firstCodePoint)) {
            mHandler.cancelDoubleSpacePeriodTimer();
            mConnection.deleteSurroundingText(2, 0);
            final String textToInsert = new String(
                    new int[] { currentSettingsValues.mSentenceSeparator, Constants.CODE_SPACE },
                    0, 2);
            mConnection.commitText(textToInsert, 1);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_maybeDoubleSpacePeriod(textToInsert,
                        false /* isBatchMode */);
            }
            mKeyboardSwitcher.updateShiftState();
            return true;
        }
        return false;
    }

    private static boolean canBeFollowedByDoubleSpacePeriod(final int codePoint) {
        // TODO: Check again whether there really ain't a better way to check this.
        // TODO: This should probably be language-dependant...
        return Character.isLetterOrDigit(codePoint)
                || codePoint == Constants.CODE_SINGLE_QUOTE
                || codePoint == Constants.CODE_DOUBLE_QUOTE
                || codePoint == Constants.CODE_CLOSING_PARENTHESIS
                || codePoint == Constants.CODE_CLOSING_SQUARE_BRACKET
                || codePoint == Constants.CODE_CLOSING_CURLY_BRACKET
                || codePoint == Constants.CODE_CLOSING_ANGLE_BRACKET
                || codePoint == Constants.CODE_PLUS
                || Character.getType(codePoint) == Character.OTHER_SYMBOL;
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
        if (CapsModeUtils.isAutoCapsMode(mLastComposedWord.mCapitalizedMode)) {
            wordToEdit = word.toLowerCase(mSubtypeSwitcher.getCurrentSubtypeLocale());
        } else {
            wordToEdit = word;
        }
        mUserDictionary.addWordToUserDictionary(wordToEdit);
    }

    private void onSettingsKeyPressed() {
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

    private void performEditorAction(final int actionId) {
        mConnection.performEditorAction(actionId);
    }

    // TODO: Revise the language switch key behavior to make it much smarter and more reasonable.
    private void handleLanguageSwitchKey() {
        final IBinder token = getWindow().getWindow().getAttributes().token;
        if (mSettings.getCurrent().mIncludesOtherImesInLanguageSwitchList) {
            mRichImm.switchToNextInputMethod(token, false /* onlyCurrentIme */);
            return;
        }
        mSubtypeState.switchSubtype(token, mRichImm);
    }

    private void sendDownUpKeyEvent(final int code) {
        final long eventTime = SystemClock.uptimeMillis();
        mConnection.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        mConnection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    private void sendKeyCodePoint(final int code) {
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_sendKeyCodePoint(code);
        }
        // TODO: Remove this special handling of digit letters.
        // For backward compatibility. See {@link InputMethodService#sendKeyChar(char)}.
        if (code >= '0' && code <= '9') {
            sendDownUpKeyEvent(code - '0' + KeyEvent.KEYCODE_0);
            return;
        }

        if (Constants.CODE_ENTER == code && mAppWorkAroundsUtils.isBeforeJellyBean()) {
            // Backward compatibility mode. Before Jelly bean, the keyboard would simulate
            // a hardware keyboard event on pressing enter or delete. This is bad for many
            // reasons (there are race conditions with commits) but some applications are
            // relying on this behavior so we continue to support it for older apps.
            sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER);
        } else {
            mConnection.commitText(StringUtils.newSingleCodePointString(code), 1);
        }
    }

    // Implementation of {@link KeyboardActionListener}.
    @Override
    public void onCodeInput(final int primaryCode, final int x, final int y) {
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onCodeInput(primaryCode, x, y);
        }
        final long when = SystemClock.uptimeMillis();
        if (primaryCode != Constants.CODE_DELETE || when > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
        mConnection.beginBatchEdit();
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        // The space state depends only on the last character pressed and its own previous
        // state. Here, we revert the space state to neutral if the key is actually modifying
        // the input contents (any non-shift key), which is what we should do for
        // all inputs that do not result in a special state. Each character handling is then
        // free to override the state as they see fit.
        final int spaceState = mSpaceState;
        if (!mWordComposer.isComposingWord()) mIsAutoCorrectionIndicatorOn = false;

        // TODO: Consolidate the double-space period timer, mLastKeyTime, and the space state.
        if (primaryCode != Constants.CODE_SPACE) {
            mHandler.cancelDoubleSpacePeriodTimer();
        }

        boolean didAutoCorrect = false;
        switch (primaryCode) {
        case Constants.CODE_DELETE:
            mSpaceState = SPACE_STATE_NONE;
            handleBackspace(spaceState);
            LatinImeLogger.logOnDelete(x, y);
            break;
        case Constants.CODE_SHIFT:
            // Note: Calling back to the keyboard on Shift key is handled in
            // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
            final Keyboard currentKeyboard = switcher.getKeyboard();
            if (null != currentKeyboard && currentKeyboard.mId.isAlphabetKeyboard()) {
                // TODO: Instead of checking for alphabetic keyboard here, separate keycodes for
                // alphabetic shift and shift while in symbol layout.
                handleRecapitalize();
            }
            break;
        case Constants.CODE_CAPSLOCK:
            // Note: Changing keyboard to shift lock state is handled in
            // {@link KeyboardSwitcher#onCodeInput(int)}.
            break;
        case Constants.CODE_SWITCH_ALPHA_SYMBOL:
            // Note: Calling back to the keyboard on symbol key is handled in
            // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
            break;
        case Constants.CODE_SETTINGS:
            onSettingsKeyPressed();
            break;
        case Constants.CODE_SHORTCUT:
            mSubtypeSwitcher.switchToShortcutIME(this);
            break;
        case Constants.CODE_ACTION_NEXT:
            performEditorAction(EditorInfo.IME_ACTION_NEXT);
            break;
        case Constants.CODE_ACTION_PREVIOUS:
            performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
            break;
        case Constants.CODE_LANGUAGE_SWITCH:
            handleLanguageSwitchKey();
            break;
        case Constants.CODE_EMOJI:
            // Note: Switching emoji keyboard is being handled in
            // {@link KeyboardState#onCodeInput(int,int)}.
            break;
        case Constants.CODE_ENTER:
            final EditorInfo editorInfo = getCurrentInputEditorInfo();
            final int imeOptionsActionId =
                    InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo);
            if (InputTypeUtils.IME_ACTION_CUSTOM_LABEL == imeOptionsActionId) {
                // Either we have an actionLabel and we should performEditorAction with actionId
                // regardless of its value.
                performEditorAction(editorInfo.actionId);
            } else if (EditorInfo.IME_ACTION_NONE != imeOptionsActionId) {
                // We didn't have an actionLabel, but we had another action to execute.
                // EditorInfo.IME_ACTION_NONE explicitly means no action. In contrast,
                // EditorInfo.IME_ACTION_UNSPECIFIED is the default value for an action, so it
                // means there should be an action and the app didn't bother to set a specific
                // code for it - presumably it only handles one. It does not have to be treated
                // in any specific way: anything that is not IME_ACTION_NONE should be sent to
                // performEditorAction.
                performEditorAction(imeOptionsActionId);
            } else {
                // No action label, and the action from imeOptions is NONE: this is a regular
                // enter key that should input a carriage return.
                didAutoCorrect = handleNonSpecialCharacter(Constants.CODE_ENTER, x, y, spaceState);
            }
            break;
        case Constants.CODE_SHIFT_ENTER:
            didAutoCorrect = handleNonSpecialCharacter(Constants.CODE_ENTER, x, y, spaceState);
            break;
        default:
            didAutoCorrect = handleNonSpecialCharacter(primaryCode, x, y, spaceState);
            break;
        }
        switcher.onCodeInput(primaryCode);
        // Reset after any single keystroke, except shift, capslock, and symbol-shift
        if (!didAutoCorrect && primaryCode != Constants.CODE_SHIFT
                && primaryCode != Constants.CODE_CAPSLOCK
                && primaryCode != Constants.CODE_SWITCH_ALPHA_SYMBOL)
            mLastComposedWord.deactivate();
        if (Constants.CODE_DELETE != primaryCode) {
            mEnteredText = null;
        }
        mConnection.endBatchEdit();
    }

    private boolean handleNonSpecialCharacter(final int primaryCode, final int x, final int y,
            final int spaceState) {
        mSpaceState = SPACE_STATE_NONE;
        final boolean didAutoCorrect;
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (settingsValues.isWordSeparator(primaryCode)
                || Character.getType(primaryCode) == Character.OTHER_SYMBOL) {
            didAutoCorrect = handleSeparator(primaryCode, x, y, spaceState);
        } else {
            didAutoCorrect = false;
            if (SPACE_STATE_PHANTOM == spaceState) {
                if (settingsValues.mIsInternal) {
                    if (mWordComposer.isComposingWord() && mWordComposer.isBatchMode()) {
                        LatinImeLoggerUtils.onAutoCorrection(
                                "", mWordComposer.getTypedWord(), " ", mWordComposer);
                    }
                }
                if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
                    // If we are in the middle of a recorrection, we need to commit the recorrection
                    // first so that we can insert the character at the current cursor position.
                    resetEntireInputState(mLastSelectionStart);
                } else {
                    commitTyped(LastComposedWord.NOT_A_SEPARATOR);
                }
            }
            final int keyX, keyY;
            final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
            if (keyboard != null && keyboard.hasProximityCharsCorrection(primaryCode)) {
                keyX = x;
                keyY = y;
            } else {
                keyX = Constants.NOT_A_COORDINATE;
                keyY = Constants.NOT_A_COORDINATE;
            }
            handleCharacter(primaryCode, keyX, keyY, spaceState);
        }
        mExpectingUpdateSelection = true;
        return didAutoCorrect;
    }

    // Called from PointerTracker through the KeyboardActionListener interface
    @Override
    public void onTextInput(final String rawText) {
        mConnection.beginBatchEdit();
        if (mWordComposer.isComposingWord()) {
            commitCurrentAutoCorrection(rawText);
        } else {
            resetComposingState(true /* alsoResetLastComposedWord */);
        }
        mHandler.postUpdateSuggestionStrip();
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS
                && ResearchLogger.RESEARCH_KEY_OUTPUT_TEXT.equals(rawText)) {
            ResearchLogger.getInstance().onResearchKeySelected(this);
            return;
        }
        final String text = specificTldProcessingOnTextInput(rawText);
        if (SPACE_STATE_PHANTOM == mSpaceState) {
            promotePhantomSpace();
        }
        mConnection.commitText(text, 1);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onTextInput(text, false /* isBatchMode */);
        }
        mConnection.endBatchEdit();
        // Space state must be updated before calling updateShiftState
        mSpaceState = SPACE_STATE_NONE;
        mKeyboardSwitcher.updateShiftState();
        mKeyboardSwitcher.onCodeInput(Constants.CODE_OUTPUT_TEXT);
        mEnteredText = text;
    }

    @Override
    public void onStartBatchInput() {
        mInputUpdater.onStartBatchInput();
        mHandler.cancelUpdateSuggestionStrip();
        mConnection.beginBatchEdit();
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (mWordComposer.isComposingWord()) {
            if (settingsValues.mIsInternal) {
                if (mWordComposer.isBatchMode()) {
                    LatinImeLoggerUtils.onAutoCorrection(
                            "", mWordComposer.getTypedWord(), " ", mWordComposer);
                }
            }
            final int wordComposerSize = mWordComposer.size();
            // Since isComposingWord() is true, the size is at least 1.
            if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
                // If we are in the middle of a recorrection, we need to commit the recorrection
                // first so that we can insert the batch input at the current cursor position.
                resetEntireInputState(mLastSelectionStart);
            } else if (wordComposerSize <= 1) {
                // We auto-correct the previous (typed, not gestured) string iff it's one character
                // long. The reason for this is, even in the middle of gesture typing, you'll still
                // tap one-letter words and you want them auto-corrected (typically, "i" in English
                // should become "I"). However for any longer word, we assume that the reason for
                // tapping probably is that the word you intend to type is not in the dictionary,
                // so we do not attempt to correct, on the assumption that if that was a dictionary
                // word, the user would probably have gestured instead.
                commitCurrentAutoCorrection(LastComposedWord.NOT_A_SEPARATOR);
            } else {
                commitTyped(LastComposedWord.NOT_A_SEPARATOR);
            }
            mExpectingUpdateSelection = true;
        }
        final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
        if (Character.isLetterOrDigit(codePointBeforeCursor)
                || settingsValues.isUsuallyFollowedBySpace(codePointBeforeCursor)) {
            mSpaceState = SPACE_STATE_PHANTOM;
        }
        mConnection.endBatchEdit();
        mWordComposer.setCapitalizedModeAtStartComposingTime(getActualCapsMode());
    }

    private static final class InputUpdater implements Handler.Callback {
        private final Handler mHandler;
        private final LatinIME mLatinIme;
        private final Object mLock = new Object();
        private boolean mInBatchInput; // synchronized using {@link #mLock}.

        private InputUpdater(final LatinIME latinIme) {
            final HandlerThread handlerThread = new HandlerThread(
                    InputUpdater.class.getSimpleName());
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper(), this);
            mLatinIme = latinIme;
        }

        private static final int MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP = 1;
        private static final int MSG_GET_SUGGESTED_WORDS = 2;

        @Override
        public boolean handleMessage(final Message msg) {
            // TODO: straighten message passing - we don't need two kinds of messages calling
            // each other.
            switch (msg.what) {
                case MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP:
                    updateBatchInput((InputPointers)msg.obj, msg.arg2 /* sequenceNumber */);
                    break;
                case MSG_GET_SUGGESTED_WORDS:
                    mLatinIme.getSuggestedWords(msg.arg1 /* sessionId */,
                            msg.arg2 /* sequenceNumber */, (OnGetSuggestedWordsCallback) msg.obj);
                    break;
            }
            return true;
        }

        // Run in the UI thread.
        public void onStartBatchInput() {
            synchronized (mLock) {
                mHandler.removeMessages(MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP);
                mInBatchInput = true;
                mLatinIme.mHandler.showGesturePreviewAndSuggestionStrip(
                        SuggestedWords.EMPTY, false /* dismissGestureFloatingPreviewText */);
            }
        }

        // Run in the Handler thread.
        private void updateBatchInput(final InputPointers batchPointers, final int sequenceNumber) {
            synchronized (mLock) {
                if (!mInBatchInput) {
                    // Batch input has ended or canceled while the message was being delivered.
                    return;
                }

                getSuggestedWordsGestureLocked(batchPointers, sequenceNumber,
                        new OnGetSuggestedWordsCallback() {
                    @Override
                    public void onGetSuggestedWords(final SuggestedWords suggestedWords) {
                        mLatinIme.mHandler.showGesturePreviewAndSuggestionStrip(
                                suggestedWords, false /* dismissGestureFloatingPreviewText */);
                    }
                });
            }
        }

        // Run in the UI thread.
        public void onUpdateBatchInput(final InputPointers batchPointers,
                final int sequenceNumber) {
            if (mHandler.hasMessages(MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP)) {
                return;
            }
            mHandler.obtainMessage(MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP, 0 /* arg1 */,
                    sequenceNumber /* arg2 */, batchPointers /* obj */).sendToTarget();
        }

        public void onCancelBatchInput() {
            synchronized (mLock) {
                mInBatchInput = false;
                mLatinIme.mHandler.showGesturePreviewAndSuggestionStrip(
                        SuggestedWords.EMPTY, true /* dismissGestureFloatingPreviewText */);
            }
        }

        // Run in the UI thread.
        public void onEndBatchInput(final InputPointers batchPointers) {
            synchronized(mLock) {
                getSuggestedWordsGestureLocked(batchPointers, SuggestedWords.NOT_A_SEQUENCE_NUMBER,
                        new OnGetSuggestedWordsCallback() {
                    @Override
                    public void onGetSuggestedWords(final SuggestedWords suggestedWords) {
                        mInBatchInput = false;
                        mLatinIme.mHandler.showGesturePreviewAndSuggestionStrip(suggestedWords,
                                true /* dismissGestureFloatingPreviewText */);
                        mLatinIme.mHandler.onEndBatchInput(suggestedWords);
                    }
                });
            }
        }

        // {@link LatinIME#getSuggestedWords(int)} method calls with same session id have to
        // be synchronized.
        private void getSuggestedWordsGestureLocked(final InputPointers batchPointers,
                final int sequenceNumber, final OnGetSuggestedWordsCallback callback) {
            mLatinIme.mWordComposer.setBatchInputPointers(batchPointers);
            mLatinIme.getSuggestedWordsOrOlderSuggestionsAsync(Suggest.SESSION_GESTURE,
                    sequenceNumber, new OnGetSuggestedWordsCallback() {
                @Override
                public void onGetSuggestedWords(SuggestedWords suggestedWords) {
                    final int suggestionCount = suggestedWords.size();
                    if (suggestionCount <= 1) {
                        final String mostProbableSuggestion = (suggestionCount == 0) ? null
                                : suggestedWords.getWord(0);
                        callback.onGetSuggestedWords(
                                mLatinIme.getOlderSuggestions(mostProbableSuggestion));
                    }
                    callback.onGetSuggestedWords(suggestedWords);
                }
            });
        }

        public void getSuggestedWords(final int sessionId, final int sequenceNumber,
                final OnGetSuggestedWordsCallback callback) {
            mHandler.obtainMessage(MSG_GET_SUGGESTED_WORDS, sessionId, sequenceNumber, callback)
                    .sendToTarget();
        }

        private void onDestroy() {
            mHandler.removeMessages(MSG_GET_SUGGESTED_WORDS);
            mHandler.removeMessages(MSG_UPDATE_GESTURE_PREVIEW_AND_SUGGESTION_STRIP);
            mHandler.getLooper().quit();
        }
    }

    // This method must run in UI Thread.
    private void showGesturePreviewAndSuggestionStrip(final SuggestedWords suggestedWords,
            final boolean dismissGestureFloatingPreviewText) {
        showSuggestionStrip(suggestedWords);
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        mainKeyboardView.showGestureFloatingPreviewText(suggestedWords);
        if (dismissGestureFloatingPreviewText) {
            mainKeyboardView.dismissGestureFloatingPreviewText();
        }
    }

    /* The sequence number member is only used in onUpdateBatchInput. It is increased each time
     * auto-commit happens. The reason we need this is, when auto-commit happens we trim the
     * input pointers that are held in a singleton, and to know how much to trim we rely on the
     * results of the suggestion process that is held in mSuggestedWords.
     * However, the suggestion process is asynchronous, and sometimes we may enter the
     * onUpdateBatchInput method twice without having recomputed suggestions yet, or having
     * received new suggestions generated from not-yet-trimmed input pointers. In this case, the
     * mIndexOfTouchPointOfSecondWords member will be out of date, and we must not use it lest we
     * remove an unrelated number of pointers (possibly even more than are left in the input
     * pointers, leading to a crash).
     * To avoid that, we increase the sequence number each time we auto-commit and trim the
     * input pointers, and we do not use any suggested words that have been generated with an
     * earlier sequence number.
     */
    private int mAutoCommitSequenceNumber = 1;
    @Override
    public void onUpdateBatchInput(final InputPointers batchPointers) {
        if (mSettings.getCurrent().mPhraseGestureEnabled) {
            final SuggestedWordInfo candidate = mSuggestedWords.getAutoCommitCandidate();
            // If these suggested words have been generated with out of date input pointers, then
            // we skip auto-commit (see comments above on the mSequenceNumber member).
            if (null != candidate && mSuggestedWords.mSequenceNumber >= mAutoCommitSequenceNumber) {
                if (candidate.mSourceDict.shouldAutoCommit(candidate)) {
                    final String[] commitParts = candidate.mWord.split(" ", 2);
                    batchPointers.shift(candidate.mIndexOfTouchPointOfSecondWord);
                    promotePhantomSpace();
                    mConnection.commitText(commitParts[0], 0);
                    mSpaceState = SPACE_STATE_PHANTOM;
                    mKeyboardSwitcher.updateShiftState();
                    mWordComposer.setCapitalizedModeAtStartComposingTime(getActualCapsMode());
                    ++mAutoCommitSequenceNumber;
                }
            }
        }
        mInputUpdater.onUpdateBatchInput(batchPointers, mAutoCommitSequenceNumber);
    }

    // This method must run in UI Thread.
    public void onEndBatchInputAsyncInternal(final SuggestedWords suggestedWords) {
        final String batchInputText = suggestedWords.isEmpty()
                ? null : suggestedWords.getWord(0);
        if (TextUtils.isEmpty(batchInputText)) {
            return;
        }
        mConnection.beginBatchEdit();
        if (SPACE_STATE_PHANTOM == mSpaceState) {
            promotePhantomSpace();
        }
        if (mSettings.getCurrent().mPhraseGestureEnabled) {
            // Find the last space
            final int indexOfLastSpace = batchInputText.lastIndexOf(Constants.CODE_SPACE) + 1;
            if (0 != indexOfLastSpace) {
                mConnection.commitText(batchInputText.substring(0, indexOfLastSpace), 1);
                showSuggestionStrip(suggestedWords.getSuggestedWordsForLastWordOfPhraseGesture());
            }
            final String lastWord = batchInputText.substring(indexOfLastSpace);
            mWordComposer.setBatchInputWord(lastWord);
            mConnection.setComposingText(lastWord, 1);
        } else {
            mWordComposer.setBatchInputWord(batchInputText);
            mConnection.setComposingText(batchInputText, 1);
        }
        mExpectingUpdateSelection = true;
        mConnection.endBatchEdit();
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onEndBatchInput(batchInputText, 0, suggestedWords);
        }
        // Space state must be updated before calling updateShiftState
        mSpaceState = SPACE_STATE_PHANTOM;
        mKeyboardSwitcher.updateShiftState();
    }

    @Override
    public void onEndBatchInput(final InputPointers batchPointers) {
        mInputUpdater.onEndBatchInput(batchPointers);
    }

    private String specificTldProcessingOnTextInput(final String text) {
        if (text.length() <= 1 || text.charAt(0) != Constants.CODE_PERIOD
                || !Character.isLetter(text.charAt(1))) {
            // Not a tld: do nothing.
            return text;
        }
        // We have a TLD (or something that looks like this): make sure we don't add
        // a space even if currently in phantom mode.
        mSpaceState = SPACE_STATE_NONE;
        // TODO: use getCodePointBeforeCursor instead to improve performance and simplify the code
        final CharSequence lastOne = mConnection.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == Constants.CODE_PERIOD) {
            return text.substring(1);
        } else {
            return text;
        }
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

    @Override
    public void onCancelBatchInput() {
        mInputUpdater.onCancelBatchInput();
    }

    private void handleBackspace(final int spaceState) {
        // We revert these in this method if the deletion doesn't happen.
        mDeleteCount++;
        mExpectingUpdateSelection = true;

        // In many cases, we may have to put the keyboard in auto-shift state again. However
        // we want to wait a few milliseconds before doing it to avoid the keyboard flashing
        // during key repeat.
        mHandler.postUpdateShiftState();

        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can remove the character at the current cursor position.
            resetEntireInputState(mLastSelectionStart);
            // When we exit this if-clause, mWordComposer.isComposingWord() will return false.
        }
        if (mWordComposer.isComposingWord()) {
            if (mWordComposer.isBatchMode()) {
                if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                    final String word = mWordComposer.getTypedWord();
                    ResearchLogger.latinIME_handleBackspace_batch(word, 1);
                }
                final String rejectedSuggestion = mWordComposer.getTypedWord();
                mWordComposer.reset();
                mWordComposer.setRejectedBatchModeSuggestion(rejectedSuggestion);
            } else {
                mWordComposer.deleteLast();
            }
            mConnection.setComposingText(getTextWithUnderline(mWordComposer.getTypedWord()), 1);
            mHandler.postUpdateSuggestionStrip();
            if (!mWordComposer.isComposingWord()) {
                // If we just removed the last character, auto-caps mode may have changed so we
                // need to re-evaluate.
                mKeyboardSwitcher.updateShiftState();
            }
        } else {
            final SettingsValues currentSettings = mSettings.getCurrent();
            if (mLastComposedWord.canRevertCommit()) {
                if (currentSettings.mIsInternal) {
                    LatinImeLoggerUtils.onAutoCorrectionCancellation();
                }
                revertCommit();
                return;
            }
            if (mEnteredText != null && mConnection.sameAsTextBeforeCursor(mEnteredText)) {
                // Cancel multi-character input: remove the text we just entered.
                // This is triggered on backspace after a key that inputs multiple characters,
                // like the smiley key or the .com key.
                mConnection.deleteSurroundingText(mEnteredText.length(), 0);
                if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                    ResearchLogger.latinIME_handleBackspace_cancelTextInput(mEnteredText);
                }
                mEnteredText = null;
                // If we have mEnteredText, then we know that mHasUncommittedTypedChars == false.
                // In addition we know that spaceState is false, and that we should not be
                // reverting any autocorrect at this point. So we can safely return.
                return;
            }
            if (SPACE_STATE_DOUBLE == spaceState) {
                mHandler.cancelDoubleSpacePeriodTimer();
                if (mConnection.revertDoubleSpacePeriod()) {
                    // No need to reset mSpaceState, it has already be done (that's why we
                    // receive it as a parameter)
                    return;
                }
            } else if (SPACE_STATE_SWAP_PUNCTUATION == spaceState) {
                if (mConnection.revertSwapPunctuation()) {
                    // Likewise
                    return;
                }
            }

            // No cancelling of commit/double space/swap: we have a regular backspace.
            // We should backspace one char and restart suggestion if at the end of a word.
            if (mLastSelectionStart != mLastSelectionEnd) {
                // If there is a selection, remove it.
                final int numCharsDeleted = mLastSelectionEnd - mLastSelectionStart;
                mConnection.setSelection(mLastSelectionEnd, mLastSelectionEnd);
                // Reset mLastSelectionEnd to mLastSelectionStart. This is what is supposed to
                // happen, and if it's wrong, the next call to onUpdateSelection will correct it,
                // but we want to set it right away to avoid it being used with the wrong values
                // later (typically, in a subsequent press on backspace).
                mLastSelectionEnd = mLastSelectionStart;
                mConnection.deleteSurroundingText(numCharsDeleted, 0);
                if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                    ResearchLogger.latinIME_handleBackspace(numCharsDeleted,
                            false /* shouldUncommitLogUnit */);
                }
            } else {
                // There is no selection, just delete one character.
                if (NOT_A_CURSOR_POSITION == mLastSelectionEnd) {
                    // This should never happen.
                    Log.e(TAG, "Backspace when we don't know the selection position");
                }
                final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
                if (codePointBeforeCursor == Constants.NOT_A_CODE) {
                    // Nothing to delete before the cursor. We have to revert the deletion states
                    // that were updated at the beginning of this method.
                    mDeleteCount--;
                    mExpectingUpdateSelection = false;
                    return;
                }
                final int lengthToDelete =
                        Character.isSupplementaryCodePoint(codePointBeforeCursor) ? 2 : 1;
                if (mAppWorkAroundsUtils.isBeforeJellyBean() ||
                        currentSettings.mInputAttributes.isTypeNull()) {
                    // There are two possible reasons to send a key event: either the field has
                    // type TYPE_NULL, in which case the keyboard should send events, or we are
                    // running in backward compatibility mode. Before Jelly bean, the keyboard
                    // would simulate a hardware keyboard event on pressing enter or delete. This
                    // is bad for many reasons (there are race conditions with commits) but some
                    // applications are relying on this behavior so we continue to support it for
                    // older apps, so we retain this behavior if the app has target SDK < JellyBean.
                    sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL);
                } else {
                    mConnection.deleteSurroundingText(lengthToDelete, 0);
                }
                if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                    ResearchLogger.latinIME_handleBackspace(lengthToDelete,
                            true /* shouldUncommitLogUnit */);
                }
                if (mDeleteCount > DELETE_ACCELERATE_AT) {
                    final int codePointBeforeCursorToDeleteAgain =
                            mConnection.getCodePointBeforeCursor();
                    if (codePointBeforeCursorToDeleteAgain != Constants.NOT_A_CODE) {
                        final int lengthToDeleteAgain = Character.isSupplementaryCodePoint(
                                codePointBeforeCursorToDeleteAgain) ? 2 : 1;
                        mConnection.deleteSurroundingText(lengthToDeleteAgain, 0);
                        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                            ResearchLogger.latinIME_handleBackspace(lengthToDeleteAgain,
                                    true /* shouldUncommitLogUnit */);
                        }
                    }
                }
            }
            if (currentSettings.isSuggestionsRequested(mDisplayOrientation)
                    && currentSettings.mCurrentLanguageHasSpaces) {
                restartSuggestionsOnWordBeforeCursorIfAtEndOfWord();
            }
            // We just removed a character. We need to update the auto-caps state.
            mKeyboardSwitcher.updateShiftState();
        }
    }

    /*
     * Strip a trailing space if necessary and returns whether it's a swap weak space situation.
     */
    private boolean maybeStripSpace(final int code,
            final int spaceState, final boolean isFromSuggestionStrip) {
        if (Constants.CODE_ENTER == code && SPACE_STATE_SWAP_PUNCTUATION == spaceState) {
            mConnection.removeTrailingSpace();
            return false;
        }
        if ((SPACE_STATE_WEAK == spaceState || SPACE_STATE_SWAP_PUNCTUATION == spaceState)
                && isFromSuggestionStrip) {
            final SettingsValues currentSettings = mSettings.getCurrent();
            if (currentSettings.isUsuallyPrecededBySpace(code)) return false;
            if (currentSettings.isUsuallyFollowedBySpace(code)) return true;
            mConnection.removeTrailingSpace();
        }
        return false;
    }

    private void handleCharacter(final int primaryCode, final int x,
            final int y, final int spaceState) {
        // TODO: refactor this method to stop flipping isComposingWord around all the time, and
        // make it shorter (possibly cut into several pieces). Also factor handleNonSpecialCharacter
        // which has the same name as other handle* methods but is not the same.
        boolean isComposingWord = mWordComposer.isComposingWord();

        // TODO: remove isWordConnector() and use isUsuallyFollowedBySpace() instead.
        // See onStartBatchInput() to see how to do it.
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (SPACE_STATE_PHANTOM == spaceState && !currentSettings.isWordConnector(primaryCode)) {
            if (isComposingWord) {
                // Sanity check
                throw new RuntimeException("Should not be composing here");
            }
            promotePhantomSpace();
        }

        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can insert the character at the current cursor position.
            resetEntireInputState(mLastSelectionStart);
            isComposingWord = false;
        }
        // We want to find out whether to start composing a new word with this character. If so,
        // we need to reset the composing state and switch isComposingWord. The order of the
        // tests is important for good performance.
        // We only start composing if we're not already composing.
        if (!isComposingWord
        // We only start composing if this is a word code point. Essentially that means it's a
        // a letter or a word connector.
                && currentSettings.isWordCodePoint(primaryCode)
        // We never go into composing state if suggestions are not requested.
                && currentSettings.isSuggestionsRequested(mDisplayOrientation) &&
        // In languages with spaces, we only start composing a word when we are not already
        // touching a word. In languages without spaces, the above conditions are sufficient.
                (!mConnection.isCursorTouchingWord(currentSettings)
                        || !currentSettings.mCurrentLanguageHasSpaces)) {
            // Reset entirely the composing state anyway, then start composing a new word unless
            // the character is a single quote or a dash. The idea here is, single quote and dash
            // are not separators and they should be treated as normal characters, except in the
            // first position where they should not start composing a word.
            isComposingWord = (Constants.CODE_SINGLE_QUOTE != primaryCode
                    && Constants.CODE_DASH != primaryCode);
            // Here we don't need to reset the last composed word. It will be reset
            // when we commit this one, if we ever do; if on the other hand we backspace
            // it entirely and resume suggestions on the previous word, we'd like to still
            // have touch coordinates for it.
            resetComposingState(false /* alsoResetLastComposedWord */);
        }
        if (isComposingWord) {
            final int keyX, keyY;
            if (Constants.isValidCoordinate(x) && Constants.isValidCoordinate(y)) {
                final KeyDetector keyDetector =
                        mKeyboardSwitcher.getMainKeyboardView().getKeyDetector();
                keyX = keyDetector.getTouchX(x);
                keyY = keyDetector.getTouchY(y);
            } else {
                keyX = x;
                keyY = y;
            }
            mWordComposer.add(primaryCode, keyX, keyY);
            // If it's the first letter, make note of auto-caps state
            if (mWordComposer.size() == 1) {
                mWordComposer.setCapitalizedModeAtStartComposingTime(getActualCapsMode());
            }
            mConnection.setComposingText(getTextWithUnderline(mWordComposer.getTypedWord()), 1);
        } else {
            final boolean swapWeakSpace = maybeStripSpace(primaryCode,
                    spaceState, Constants.SUGGESTION_STRIP_COORDINATE == x);

            sendKeyCodePoint(primaryCode);

            if (swapWeakSpace) {
                swapSwapperAndSpace();
                mSpaceState = SPACE_STATE_WEAK;
            }
            // In case the "add to dictionary" hint was still displayed.
            if (null != mSuggestionStripView) mSuggestionStripView.dismissAddToDictionaryHint();
        }
        mHandler.postUpdateSuggestionStrip();
        if (currentSettings.mIsInternal) {
            LatinImeLoggerUtils.onNonSeparator((char)primaryCode, x, y);
        }
    }

    private void handleRecapitalize() {
        if (mLastSelectionStart == mLastSelectionEnd) return; // No selection
        // If we have a recapitalize in progress, use it; otherwise, create a new one.
        if (!mRecapitalizeStatus.isActive()
                || !mRecapitalizeStatus.isSetAt(mLastSelectionStart, mLastSelectionEnd)) {
            final CharSequence selectedText =
                    mConnection.getSelectedText(0 /* flags, 0 for no styles */);
            if (TextUtils.isEmpty(selectedText)) return; // Race condition with the input connection
            final SettingsValues currentSettings = mSettings.getCurrent();
            mRecapitalizeStatus.initialize(mLastSelectionStart, mLastSelectionEnd,
                    selectedText.toString(), currentSettings.mLocale,
                    currentSettings.mWordSeparators);
            // We trim leading and trailing whitespace.
            mRecapitalizeStatus.trim();
            // Trimming the object may have changed the length of the string, and we need to
            // reposition the selection handles accordingly. As this result in an IPC call,
            // only do it if it's actually necessary, in other words if the recapitalize status
            // is not set at the same place as before.
            if (!mRecapitalizeStatus.isSetAt(mLastSelectionStart, mLastSelectionEnd)) {
                mLastSelectionStart = mRecapitalizeStatus.getNewCursorStart();
                mLastSelectionEnd = mRecapitalizeStatus.getNewCursorEnd();
                mConnection.setSelection(mLastSelectionStart, mLastSelectionEnd);
            }
        }
        mRecapitalizeStatus.rotate();
        final int numCharsDeleted = mLastSelectionEnd - mLastSelectionStart;
        mConnection.setSelection(mLastSelectionEnd, mLastSelectionEnd);
        mConnection.deleteSurroundingText(numCharsDeleted, 0);
        mConnection.commitText(mRecapitalizeStatus.getRecapitalizedString(), 0);
        mLastSelectionStart = mRecapitalizeStatus.getNewCursorStart();
        mLastSelectionEnd = mRecapitalizeStatus.getNewCursorEnd();
        mConnection.setSelection(mLastSelectionStart, mLastSelectionEnd);
        // Match the keyboard to the new state.
        mKeyboardSwitcher.updateShiftState();
    }

    // Returns true if we do an autocorrection, false otherwise.
    private boolean handleSeparator(final int primaryCode, final int x, final int y,
            final int spaceState) {
        boolean didAutoCorrect = false;
        final SettingsValues currentSettings = mSettings.getCurrent();
        // We avoid sending spaces in languages without spaces if we were composing.
        final boolean shouldAvoidSendingCode = Constants.CODE_SPACE == primaryCode
                && !currentSettings.mCurrentLanguageHasSpaces && mWordComposer.isComposingWord();
        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can insert the separator at the current cursor position.
            resetEntireInputState(mLastSelectionStart);
        }
        if (mWordComposer.isComposingWord()) { // May have changed since we stored wasComposing
            if (currentSettings.mCorrectionEnabled) {
                final String separator = shouldAvoidSendingCode ? LastComposedWord.NOT_A_SEPARATOR
                        : StringUtils.newSingleCodePointString(primaryCode);
                commitCurrentAutoCorrection(separator);
                didAutoCorrect = true;
            } else {
                commitTyped(StringUtils.newSingleCodePointString(primaryCode));
            }
        }

        final boolean swapWeakSpace = maybeStripSpace(primaryCode, spaceState,
                Constants.SUGGESTION_STRIP_COORDINATE == x);

        if (SPACE_STATE_PHANTOM == spaceState &&
                currentSettings.isUsuallyPrecededBySpace(primaryCode)) {
            promotePhantomSpace();
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_handleSeparator(primaryCode, mWordComposer.isComposingWord());
        }

        if (!shouldAvoidSendingCode) {
            sendKeyCodePoint(primaryCode);
        }

        if (Constants.CODE_SPACE == primaryCode) {
            if (currentSettings.isSuggestionsRequested(mDisplayOrientation)) {
                if (maybeDoubleSpacePeriod()) {
                    mSpaceState = SPACE_STATE_DOUBLE;
                } else if (!isShowingPunctuationList()) {
                    mSpaceState = SPACE_STATE_WEAK;
                }
            }

            mHandler.startDoubleSpacePeriodTimer();
            mHandler.postUpdateSuggestionStrip();
        } else {
            if (swapWeakSpace) {
                swapSwapperAndSpace();
                mSpaceState = SPACE_STATE_SWAP_PUNCTUATION;
            } else if (SPACE_STATE_PHANTOM == spaceState
                    && currentSettings.isUsuallyFollowedBySpace(primaryCode)) {
                // If we are in phantom space state, and the user presses a separator, we want to
                // stay in phantom space state so that the next keypress has a chance to add the
                // space. For example, if I type "Good dat", pick "day" from the suggestion strip
                // then insert a comma and go on to typing the next word, I want the space to be
                // inserted automatically before the next word, the same way it is when I don't
                // input the comma.
                // The case is a little different if the separator is a space stripper. Such a
                // separator does not normally need a space on the right (that's the difference
                // between swappers and strippers), so we should not stay in phantom space state if
                // the separator is a stripper. Hence the additional test above.
                mSpaceState = SPACE_STATE_PHANTOM;
            }

            // Set punctuation right away. onUpdateSelection will fire but tests whether it is
            // already displayed or not, so it's okay.
            setPunctuationSuggestions();
        }
        if (currentSettings.mIsInternal) {
            LatinImeLoggerUtils.onSeparator((char)primaryCode, x, y);
        }

        mKeyboardSwitcher.updateShiftState();
        return didAutoCorrect;
    }

    private CharSequence getTextWithUnderline(final String text) {
        return mIsAutoCorrectionIndicatorOn
                ? SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(this, text)
                : text;
    }

    private void handleClose() {
        // TODO: Verify that words are logged properly when IME is closed.
        commitTyped(LastComposedWord.NOT_A_SEPARATOR);
        requestHideSelf(0);
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
    }

    // TODO: make this private
    // Outside LatinIME, only used by the test suite.
    @UsedForTesting
    boolean isShowingPunctuationList() {
        if (mSuggestedWords == null) return false;
        return mSettings.getCurrent().mSuggestPuncList == mSuggestedWords;
    }

    private boolean isSuggestionsStripVisible() {
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (mSuggestionStripView == null)
            return false;
        if (mSuggestionStripView.isShowingAddToDictionaryHint())
            return true;
        if (null == currentSettings)
            return false;
        if (!currentSettings.isSuggestionStripVisibleInOrientation(mDisplayOrientation))
            return false;
        if (currentSettings.isApplicationSpecifiedCompletionsOn())
            return true;
        return currentSettings.isSuggestionsRequested(mDisplayOrientation);
    }

    private void clearSuggestionStrip() {
        setSuggestedWords(SuggestedWords.EMPTY, false);
        setAutoCorrectionIndicator(false);
    }

    private void setSuggestedWords(final SuggestedWords words, final boolean isAutoCorrection) {
        mSuggestedWords = words;
        if (mSuggestionStripView != null) {
            mSuggestionStripView.setSuggestions(words);
            mKeyboardSwitcher.onAutoCorrectionStateChanged(isAutoCorrection);
        }
    }

    private void setAutoCorrectionIndicator(final boolean newAutoCorrectionIndicator) {
        // Put a blue underline to a word in TextView which will be auto-corrected.
        if (mIsAutoCorrectionIndicatorOn != newAutoCorrectionIndicator
                && mWordComposer.isComposingWord()) {
            mIsAutoCorrectionIndicatorOn = newAutoCorrectionIndicator;
            final CharSequence textWithUnderline =
                    getTextWithUnderline(mWordComposer.getTypedWord());
            // TODO: when called from an updateSuggestionStrip() call that results from a posted
            // message, this is called outside any batch edit. Potentially, this may result in some
            // janky flickering of the screen, although the display speed makes it unlikely in
            // the practice.
            mConnection.setComposingText(textWithUnderline, 1);
        }
    }

    private void updateSuggestionStrip() {
        mHandler.cancelUpdateSuggestionStrip();
        final SettingsValues currentSettings = mSettings.getCurrent();

        // Check if we have a suggestion engine attached.
        if (mSuggest == null
                || !currentSettings.isSuggestionsRequested(mDisplayOrientation)) {
            if (mWordComposer.isComposingWord()) {
                Log.w(TAG, "Called updateSuggestionsOrPredictions but suggestions were not "
                        + "requested!");
            }
            return;
        }

        if (!mWordComposer.isComposingWord() && !currentSettings.mBigramPredictionEnabled) {
            setPunctuationSuggestions();
            return;
        }

        final AsyncResultHolder<SuggestedWords> holder = new AsyncResultHolder<SuggestedWords>();
        getSuggestedWordsOrOlderSuggestionsAsync(Suggest.SESSION_TYPING,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER, new OnGetSuggestedWordsCallback() {
                    @Override
                    public void onGetSuggestedWords(final SuggestedWords suggestedWords) {
                        holder.set(suggestedWords);
                    }
                }
        );

        // This line may cause the current thread to wait.
        final SuggestedWords suggestedWords = holder.get(null, GET_SUGGESTED_WORDS_TIMEOUT);
        if (suggestedWords != null) {
            showSuggestionStrip(suggestedWords);
        }
    }

    private void getSuggestedWords(final int sessionId, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        final Suggest suggest = mSuggest;
        if (keyboard == null || suggest == null) {
            callback.onGetSuggestedWords(SuggestedWords.EMPTY);
            return;
        }
        // Get the word on which we should search the bigrams. If we are composing a word, it's
        // whatever is *before* the half-committed word in the buffer, hence 2; if we aren't, we
        // should just skip whitespace if any, so 1.
        final SettingsValues currentSettings = mSettings.getCurrent();
        final int[] additionalFeaturesOptions = currentSettings.mAdditionalFeaturesSettingValues;
        final String prevWord;
        if (currentSettings.mCurrentLanguageHasSpaces) {
            // If we are typing in a language with spaces we can just look up the previous
            // word from textview.
            prevWord = mConnection.getNthPreviousWord(currentSettings.mWordSeparators,
                    mWordComposer.isComposingWord() ? 2 : 1);
        } else {
            prevWord = LastComposedWord.NOT_A_COMPOSED_WORD == mLastComposedWord ? null
                    : mLastComposedWord.mCommittedWord;
        }
        suggest.getSuggestedWords(mWordComposer, prevWord, keyboard.getProximityInfo(),
                currentSettings.mBlockPotentiallyOffensive, currentSettings.mCorrectionEnabled,
                additionalFeaturesOptions, sessionId, sequenceNumber, callback);
    }

    private void getSuggestedWordsOrOlderSuggestionsAsync(final int sessionId,
            final int sequenceNumber, final OnGetSuggestedWordsCallback callback) {
        mInputUpdater.getSuggestedWords(sessionId, sequenceNumber,
                new OnGetSuggestedWordsCallback() {
                    @Override
                    public void onGetSuggestedWords(SuggestedWords suggestedWords) {
                        callback.onGetSuggestedWords(maybeRetrieveOlderSuggestions(
                                mWordComposer.getTypedWord(), suggestedWords));
                    }
                });
    }

    private SuggestedWords maybeRetrieveOlderSuggestions(final String typedWord,
            final SuggestedWords suggestedWords) {
        // TODO: consolidate this into getSuggestedWords
        // We update the suggestion strip only when we have some suggestions to show, i.e. when
        // the suggestion count is > 1; else, we leave the old suggestions, with the typed word
        // replaced with the new one. However, when the word is a dictionary word, or when the
        // length of the typed word is 1 or 0 (after a deletion typically), we do want to remove the
        // old suggestions. Also, if we are showing the "add to dictionary" hint, we need to
        // revert to suggestions - although it is unclear how we can come here if it's displayed.
        if (suggestedWords.size() > 1 || typedWord.length() <= 1
                || suggestedWords.mTypedWordValid || null == mSuggestionStripView
                || mSuggestionStripView.isShowingAddToDictionaryHint()) {
            return suggestedWords;
        } else {
            return getOlderSuggestions(typedWord);
        }
    }

    private SuggestedWords getOlderSuggestions(final String typedWord) {
        SuggestedWords previousSuggestedWords = mSuggestedWords;
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

    private void setAutoCorrection(final SuggestedWords suggestedWords, final String typedWord) {
        if (suggestedWords.isEmpty()) return;
        final String autoCorrection;
        if (suggestedWords.mWillAutoCorrect) {
            autoCorrection = suggestedWords.getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION);
        } else {
            // We can't use suggestedWords.getWord(SuggestedWords.INDEX_OF_TYPED_WORD)
            // because it may differ from mWordComposer.mTypedWord.
            autoCorrection = typedWord;
        }
        mWordComposer.setAutoCorrection(autoCorrection);
    }

    private void showSuggestionStripWithTypedWord(final SuggestedWords suggestedWords,
            final String typedWord) {
      if (suggestedWords.isEmpty()) {
          // No auto-correction is available, clear the cached values.
          AccessibilityUtils.getInstance().setAutoCorrection(null, null);
          clearSuggestionStrip();
          return;
      }
      setAutoCorrection(suggestedWords, typedWord);
      final boolean isAutoCorrection = suggestedWords.willAutoCorrect();
      setSuggestedWords(suggestedWords, isAutoCorrection);
      setAutoCorrectionIndicator(isAutoCorrection);
      setSuggestionStripShown(isSuggestionsStripVisible());
      // An auto-correction is available, cache it in accessibility code so
      // we can be speak it if the user touches a key that will insert it.
      AccessibilityUtils.getInstance().setAutoCorrection(suggestedWords, typedWord);
    }

    private void showSuggestionStrip(final SuggestedWords suggestedWords) {
        if (suggestedWords.isEmpty()) {
            clearSuggestionStrip();
            return;
        }
        showSuggestionStripWithTypedWord(suggestedWords,
            suggestedWords.getWord(SuggestedWords.INDEX_OF_TYPED_WORD));
    }

    private void commitCurrentAutoCorrection(final String separator) {
        // Complete any pending suggestions query first
        if (mHandler.hasPendingUpdateSuggestions()) {
            updateSuggestionStrip();
        }
        final String typedAutoCorrection = mWordComposer.getAutoCorrectionOrNull();
        final String typedWord = mWordComposer.getTypedWord();
        final String autoCorrection = (typedAutoCorrection != null)
                ? typedAutoCorrection : typedWord;
        if (autoCorrection != null) {
            if (TextUtils.isEmpty(typedWord)) {
                throw new RuntimeException("We have an auto-correction but the typed word "
                        + "is empty? Impossible! I must commit suicide.");
            }
            if (mSettings.isInternal()) {
                LatinImeLoggerUtils.onAutoCorrection(
                        typedWord, autoCorrection, separator, mWordComposer);
            }
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                final SuggestedWords suggestedWords = mSuggestedWords;
                ResearchLogger.latinIme_commitCurrentAutoCorrection(typedWord, autoCorrection,
                        separator, mWordComposer.isBatchMode(), suggestedWords);
            }
            mExpectingUpdateSelection = true;
            commitChosenWord(autoCorrection, LastComposedWord.COMMIT_TYPE_DECIDED_WORD,
                    separator);
            if (!typedWord.equals(autoCorrection)) {
                // This will make the correction flash for a short while as a visual clue
                // to the user that auto-correction happened. It has no other effect; in particular
                // note that this won't affect the text inside the text field AT ALL: it only makes
                // the segment of text starting at the supplied index and running for the length
                // of the auto-correction flash. At this moment, the "typedWord" argument is
                // ignored by TextView.
                mConnection.commitCorrection(
                        new CorrectionInfo(mLastSelectionEnd - typedWord.length(),
                        typedWord, autoCorrection));
            }
        }
    }

    // Called from {@link SuggestionStripView} through the {@link SuggestionStripView#Listener}
    // interface
    @Override
    public void pickSuggestionManually(final int index, final SuggestedWordInfo suggestionInfo) {
        final SuggestedWords suggestedWords = mSuggestedWords;
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

        mConnection.beginBatchEdit();
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (SPACE_STATE_PHANTOM == mSpaceState && suggestion.length() > 0
                // In the batch input mode, a manually picked suggested word should just replace
                // the current batch input text and there is no need for a phantom space.
                && !mWordComposer.isBatchMode()) {
            final int firstChar = Character.codePointAt(suggestion, 0);
            if (!currentSettings.isWordSeparator(firstChar)
                    || currentSettings.isUsuallyPrecededBySpace(firstChar)) {
                promotePhantomSpace();
            }
        }

        if (currentSettings.isApplicationSpecifiedCompletionsOn()
                && mApplicationSpecifiedCompletions != null
                && index >= 0 && index < mApplicationSpecifiedCompletions.length) {
            mSuggestedWords = SuggestedWords.EMPTY;
            if (mSuggestionStripView != null) {
                mSuggestionStripView.clear();
            }
            mKeyboardSwitcher.updateShiftState();
            resetComposingState(true /* alsoResetLastComposedWord */);
            final CompletionInfo completionInfo = mApplicationSpecifiedCompletions[index];
            mConnection.commitCompletion(completionInfo);
            mConnection.endBatchEdit();
            return;
        }

        // We need to log before we commit, because the word composer will store away the user
        // typed word.
        final String replacedWord = mWordComposer.getTypedWord();
        LatinImeLogger.logOnManualSuggestion(replacedWord, suggestion, index, suggestedWords);
        mExpectingUpdateSelection = true;
        commitChosenWord(suggestion, LastComposedWord.COMMIT_TYPE_MANUAL_PICK,
                LastComposedWord.NOT_A_SEPARATOR);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_pickSuggestionManually(replacedWord, index, suggestion,
                    mWordComposer.isBatchMode(), suggestionInfo.mScore, suggestionInfo.mKind,
                    suggestionInfo.mSourceDict.mDictType);
        }
        mConnection.endBatchEdit();
        // Don't allow cancellation of manual pick
        mLastComposedWord.deactivate();
        // Space state must be updated before calling updateShiftState
        mSpaceState = SPACE_STATE_PHANTOM;
        mKeyboardSwitcher.updateShiftState();

        // We should show the "Touch again to save" hint if the user pressed the first entry
        // AND it's in none of our current dictionaries (main, user or otherwise).
        // Please note that if mSuggest is null, it means that everything is off: suggestion
        // and correction, so we shouldn't try to show the hint
        final Suggest suggest = mSuggest;
        final boolean showingAddToDictionaryHint =
                (SuggestedWordInfo.KIND_TYPED == suggestionInfo.mKind
                        || SuggestedWordInfo.KIND_OOV_CORRECTION == suggestionInfo.mKind)
                        && suggest != null
                        // If the suggestion is not in the dictionary, the hint should be shown.
                        && !AutoCorrectionUtils.isValidWord(suggest, suggestion, true);

        if (currentSettings.mIsInternal) {
            LatinImeLoggerUtils.onSeparator((char)Constants.CODE_SPACE,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        }
        if (showingAddToDictionaryHint && mIsUserDictionaryAvailable) {
            mSuggestionStripView.showAddToDictionaryHint(
                    suggestion, currentSettings.mHintToSaveText);
        } else {
            // If we're not showing the "Touch again to save", then update the suggestion strip.
            mHandler.postUpdateSuggestionStrip();
        }
    }

    /**
     * Commits the chosen word to the text field and saves it for later retrieval.
     */
    private void commitChosenWord(final String chosenWord, final int commitType,
            final String separatorString) {
        final SuggestedWords suggestedWords = mSuggestedWords;
        mConnection.commitText(SuggestionSpanUtils.getTextWithSuggestionSpan(
                this, chosenWord, suggestedWords, mIsMainDictionaryAvailable), 1);
        // Add the word to the user history dictionary
        final String prevWord = addToUserHistoryDictionary(chosenWord);
        // TODO: figure out here if this is an auto-correct or if the best word is actually
        // what user typed. Note: currently this is done much later in
        // LastComposedWord#didCommitTypedWord by string equality of the remembered
        // strings.
        mLastComposedWord = mWordComposer.commitWord(commitType, chosenWord, separatorString,
                prevWord);
    }

    private void setPunctuationSuggestions() {
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (currentSettings.mBigramPredictionEnabled) {
            clearSuggestionStrip();
        } else {
            setSuggestedWords(currentSettings.mSuggestPuncList, false);
        }
        setAutoCorrectionIndicator(false);
        setSuggestionStripShown(isSuggestionsStripVisible());
    }

    private String addToUserHistoryDictionary(final String suggestion) {
        if (TextUtils.isEmpty(suggestion)) return null;
        final Suggest suggest = mSuggest;
        if (suggest == null) return null;

        // If correction is not enabled, we don't add words to the user history dictionary.
        // That's to avoid unintended additions in some sensitive fields, or fields that
        // expect to receive non-words.
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (!currentSettings.mCorrectionEnabled) return null;

        final UserHistoryDictionary userHistoryDictionary = mUserHistoryDictionary;
        if (userHistoryDictionary == null) return null;

        final String prevWord = mConnection.getNthPreviousWord(currentSettings.mWordSeparators, 2);
        final String secondWord;
        if (mWordComposer.wasAutoCapitalized() && !mWordComposer.isMostlyCaps()) {
            secondWord = suggestion.toLowerCase(mSubtypeSwitcher.getCurrentSubtypeLocale());
        } else {
            secondWord = suggestion;
        }
        // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
        // We don't add words with 0-frequency (assuming they would be profanity etc.).
        final int maxFreq = AutoCorrectionUtils.getMaxFrequency(
                suggest.getUnigramDictionaries(), suggestion);
        if (maxFreq == 0) return null;
        userHistoryDictionary.addToDictionary(prevWord, secondWord, maxFreq > 0);
        return prevWord;
    }

    private boolean isResumableWord(final String word, final SettingsValues settings) {
        final int firstCodePoint = word.codePointAt(0);
        return settings.isWordCodePoint(firstCodePoint)
                && Constants.CODE_SINGLE_QUOTE != firstCodePoint
                && Constants.CODE_DASH != firstCodePoint;
    }

    /**
     * Check if the cursor is touching a word. If so, restart suggestions on this word, else
     * do nothing.
     */
    private void restartSuggestionsOnWordTouchedByCursor() {
        // HACK: We may want to special-case some apps that exhibit bad behavior in case of
        // recorrection. This is a temporary, stopgap measure that will be removed later.
        // TODO: remove this.
        if (mAppWorkAroundsUtils.isBrokenByRecorrection()) return;
        // A simple way to test for support from the TextView.
        if (!isSuggestionsStripVisible()) return;
        // Recorrection is not supported in languages without spaces because we don't know
        // how to segment them yet.
        if (!mSettings.getCurrent().mCurrentLanguageHasSpaces) return;
        // If the cursor is not touching a word, or if there is a selection, return right away.
        if (mLastSelectionStart != mLastSelectionEnd) return;
        // If we don't know the cursor location, return.
        if (mLastSelectionStart < 0) return;
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (!mConnection.isCursorTouchingWord(currentSettings)) return;
        final TextRange range = mConnection.getWordRangeAtCursor(currentSettings.mWordSeparators,
                0 /* additionalPrecedingWordsCount */);
        if (null == range) return; // Happens if we don't have an input connection at all
        if (range.length() <= 0) return; // Race condition. No text to resume on, so bail out.
        // If for some strange reason (editor bug or so) we measure the text before the cursor as
        // longer than what the entire text is supposed to be, the safe thing to do is bail out.
        final int numberOfCharsInWordBeforeCursor = range.getNumberOfCharsInWordBeforeCursor();
        if (numberOfCharsInWordBeforeCursor > mLastSelectionStart) return;
        final ArrayList<SuggestedWordInfo> suggestions = CollectionUtils.newArrayList();
        final String typedWord = range.mWord.toString();
        if (!isResumableWord(typedWord, currentSettings)) return;
        int i = 0;
        for (final SuggestionSpan span : range.getSuggestionSpansAtWord()) {
            for (final String s : span.getSuggestions()) {
                ++i;
                if (!TextUtils.equals(s, typedWord)) {
                    suggestions.add(new SuggestedWordInfo(s,
                            SuggestionStripView.MAX_SUGGESTIONS - i,
                            SuggestedWordInfo.KIND_RESUMED, Dictionary.DICTIONARY_RESUMED,
                            SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                            SuggestedWordInfo.NOT_A_CONFIDENCE
                                    /* autoCommitFirstWordConfidence */));
                }
            }
        }
        mWordComposer.setComposingWord(typedWord, mKeyboardSwitcher.getKeyboard());
        mWordComposer.setCursorPositionWithinWord(
                typedWord.codePointCount(0, numberOfCharsInWordBeforeCursor));
        mConnection.setComposingRegion(
                mLastSelectionStart - numberOfCharsInWordBeforeCursor,
                mLastSelectionEnd + range.getNumberOfCharsInWordAfterCursor());
        if (suggestions.isEmpty()) {
            // We come here if there weren't any suggestion spans on this word. We will try to
            // compute suggestions for it instead.
            mInputUpdater.getSuggestedWords(Suggest.SESSION_TYPING,
                    SuggestedWords.NOT_A_SEQUENCE_NUMBER, new OnGetSuggestedWordsCallback() {
                        @Override
                        public void onGetSuggestedWords(
                                final SuggestedWords suggestedWordsIncludingTypedWord) {
                            final SuggestedWords suggestedWords;
                            if (suggestedWordsIncludingTypedWord.size() > 1) {
                                // We were able to compute new suggestions for this word.
                                // Remove the typed word, since we don't want to display it in this
                                // case. The #getSuggestedWordsExcludingTypedWord() method sets
                                // willAutoCorrect to false.
                                suggestedWords = suggestedWordsIncludingTypedWord
                                        .getSuggestedWordsExcludingTypedWord();
                            } else {
                                // No saved suggestions, and we were unable to compute any good one
                                // either. Rather than displaying an empty suggestion strip, we'll
                                // display the original word alone in the middle.
                                // Since there is only one word, willAutoCorrect is false.
                                suggestedWords = suggestedWordsIncludingTypedWord;
                            }
                            // We need to pass typedWord because mWordComposer.mTypedWord may
                            // differ from typedWord.
                            unsetIsAutoCorrectionIndicatorOnAndCallShowSuggestionStrip(
                                    suggestedWords, typedWord);
                        }});
        } else {
            // We found suggestion spans in the word. We'll create the SuggestedWords out of
            // them, and make willAutoCorrect false.
            final SuggestedWords suggestedWords = new SuggestedWords(suggestions,
                    true /* typedWordValid */, false /* willAutoCorrect */,
                    false /* isPunctuationSuggestions */, false /* isObsoleteSuggestions */,
                    false /* isPrediction */);
            // We need to pass typedWord because mWordComposer.mTypedWord may differ from typedWord.
            unsetIsAutoCorrectionIndicatorOnAndCallShowSuggestionStrip(suggestedWords, typedWord);
        }
    }

    public void unsetIsAutoCorrectionIndicatorOnAndCallShowSuggestionStrip(
            final SuggestedWords suggestedWords, final String typedWord) {
        // Note that it's very important here that suggestedWords.mWillAutoCorrect is false.
        // We never want to auto-correct on a resumed suggestion. Please refer to the three places
        // above in restartSuggestionsOnWordTouchedByCursor() where suggestedWords is affected.
        // We also need to unset mIsAutoCorrectionIndicatorOn to avoid showSuggestionStrip touching
        // the text to adapt it.
        // TODO: remove mIsAutoCorrectionIndicatorOn (see comment on definition)
        mIsAutoCorrectionIndicatorOn = false;
        mHandler.showSuggestionStripWithTypedWord(suggestedWords, typedWord);
    }

    /**
     * Check if the cursor is actually at the end of a word. If so, restart suggestions on this
     * word, else do nothing.
     */
    private void restartSuggestionsOnWordBeforeCursorIfAtEndOfWord() {
        final CharSequence word =
                mConnection.getWordBeforeCursorIfAtEndOfWord(mSettings.getCurrent());
        if (null != word) {
            final String wordString = word.toString();
            restartSuggestionsOnWordBeforeCursor(wordString);
            // TODO: Handle the case where the user manually moves the cursor and then backs up over
            // a separator.  In that case, the current log unit should not be uncommitted.
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.getInstance().uncommitCurrentLogUnit(wordString,
                        true /* dumpCurrentLogUnit */);
            }
        }
    }

    private void restartSuggestionsOnWordBeforeCursor(final String word) {
        mWordComposer.setComposingWord(word, mKeyboardSwitcher.getKeyboard());
        final int length = word.length();
        mConnection.deleteSurroundingText(length, 0);
        mConnection.setComposingText(word, 1);
        mHandler.postUpdateSuggestionStrip();
    }

    /**
     * Retry resetting caches in the rich input connection.
     *
     * When the editor can't be accessed we can't reset the caches, so we schedule a retry.
     * This method handles the retry, and re-schedules a new retry if we still can't access.
     * We only retry up to 5 times before giving up.
     *
     * @param tryResumeSuggestions Whether we should resume suggestions or not.
     * @param remainingTries How many times we may try again before giving up.
     */
    private void retryResetCaches(final boolean tryResumeSuggestions, final int remainingTries) {
        if (!mConnection.resetCachesUponCursorMoveAndReturnSuccess(mLastSelectionStart, false)) {
            if (0 < remainingTries) {
                mHandler.postResetCaches(tryResumeSuggestions, remainingTries - 1);
                return;
            }
            // If remainingTries is 0, we should stop waiting for new tries, but it's still
            // better to load the keyboard (less things will be broken).
        }
        tryFixLyingCursorPosition();
        mKeyboardSwitcher.loadKeyboard(getCurrentInputEditorInfo(), mSettings.getCurrent());
        if (tryResumeSuggestions) mHandler.postResumeSuggestions();
    }

    private void revertCommit() {
        final String previousWord = mLastComposedWord.mPrevWord;
        final String originallyTypedWord = mLastComposedWord.mTypedWord;
        final String committedWord = mLastComposedWord.mCommittedWord;
        final int cancelLength = committedWord.length();
        // We want java chars, not codepoints for the following.
        final int separatorLength = mLastComposedWord.mSeparatorString.length();
        // TODO: should we check our saved separator against the actual contents of the text view?
        final int deleteLength = cancelLength + separatorLength;
        if (DEBUG) {
            if (mWordComposer.isComposingWord()) {
                throw new RuntimeException("revertCommit, but we are composing a word");
            }
            final CharSequence wordBeforeCursor =
                    mConnection.getTextBeforeCursor(deleteLength, 0)
                            .subSequence(0, cancelLength);
            if (!TextUtils.equals(committedWord, wordBeforeCursor)) {
                throw new RuntimeException("revertCommit check failed: we thought we were "
                        + "reverting \"" + committedWord
                        + "\", but before the cursor we found \"" + wordBeforeCursor + "\"");
            }
        }
        mConnection.deleteSurroundingText(deleteLength, 0);
        if (!TextUtils.isEmpty(previousWord) && !TextUtils.isEmpty(committedWord)) {
            mUserHistoryDictionary.cancelAddingUserHistory(previousWord, committedWord);
        }
        final String stringToCommit = originallyTypedWord + mLastComposedWord.mSeparatorString;
        if (mSettings.getCurrent().mCurrentLanguageHasSpaces) {
            // For languages with spaces, we revert to the typed string, but the cursor is still
            // after the separator so we don't resume suggestions. If the user wants to correct
            // the word, they have to press backspace again.
            mConnection.commitText(stringToCommit, 1);
        } else {
            // For languages without spaces, we revert the typed string but the cursor is flush
            // with the typed word, so we need to resume suggestions right away.
            mWordComposer.setComposingWord(stringToCommit, mKeyboardSwitcher.getKeyboard());
            mConnection.setComposingText(stringToCommit, 1);
        }
        if (mSettings.isInternal()) {
            LatinImeLoggerUtils.onSeparator(mLastComposedWord.mSeparatorString,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_revertCommit(committedWord, originallyTypedWord,
                    mWordComposer.isBatchMode(), mLastComposedWord.mSeparatorString);
        }
        // Don't restart suggestion yet. We'll restart if the user deletes the
        // separator.
        mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
        // We have a separator between the word and the cursor: we should show predictions.
        mHandler.postUpdateSuggestionStrip();
    }

    // This essentially inserts a space, and that's it.
    public void promotePhantomSpace() {
        final SettingsValues currentSettings = mSettings.getCurrent();
        if (currentSettings.shouldInsertSpacesAutomatically()
                && currentSettings.mCurrentLanguageHasSpaces
                && !mConnection.textBeforeCursorLooksLikeURL()) {
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_promotePhantomSpace();
            }
            sendKeyCodePoint(Constants.CODE_SPACE);
        }
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
        if (keyboardView != null && keyboardView.isInSlidingKeyInput()) {
            // No need to feedback while sliding input.
            return;
        }
        if (repeatCount > 0) {
            if (code == Constants.CODE_DELETE && !mConnection.canDeleteCharacters()) {
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
        if (mEventInterpreter.onHardwareKeyEvent(event)) {
            final long keyIdentifier = event.getDeviceId() << 32 + event.getKeyCode();
            mCurrentlyPressedHardwareKeys.add(keyIdentifier);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        final long keyIdentifier = event.getDeviceId() << 32 + event.getKeyCode();
        if (mCurrentlyPressedHardwareKeys.remove(keyIdentifier)) {
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
        handleClose();
        launchSubActivity(SettingsActivity.class);
    }

    public void launchKeyboardedDialogActivity(final Class<? extends Activity> activityClass) {
        // Put the text in the attached EditText into a safe, saved state before switching to a
        // new activity that will also use the soft keyboard.
        commitTyped(LastComposedWord.NOT_A_SEPARATOR);
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
                getString(ApplicationUtils.getAcitivityTitleResId(this, SettingsActivity.class)),
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
        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setItems(items, listener)
                .setTitle(title);
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
    /* package for test */ String getFirstSuggestedWord() {
        return mSuggestedWords.size() > 0 ? mSuggestedWords.getWord(0) : null;
    }

    // DO NOT USE THIS for any other purpose than testing. This is information private to LatinIME.
    @UsedForTesting
    /* package for test */ boolean isCurrentlyWaitingForMainDictionary() {
        return mSuggest.isCurrentlyWaitingForMainDictionary();
    }

    // DO NOT USE THIS for any other purpose than testing. This is information private to LatinIME.
    @UsedForTesting
    /* package for test */ boolean hasMainDictionary() {
        return mSuggest.hasMainDictionary();
    }

    // DO NOT USE THIS for any other purpose than testing. This can break the keyboard badly.
    @UsedForTesting
    /* package for test */ void replaceMainDictionaryForTest(final Locale locale) {
        mSuggest.resetMainDict(this, locale, null);
    }

    public void debugDumpStateAndCrashWithException(final String context) {
        final StringBuilder s = new StringBuilder(mAppWorkAroundsUtils.toString());
        s.append("\nAttributes : ").append(mSettings.getCurrent().mInputAttributes)
                .append("\nContext : ").append(context);
        throw new RuntimeException(s.toString());
    }

    @Override
    protected void dump(final FileDescriptor fd, final PrintWriter fout, final String[] args) {
        super.dump(fd, fout, args);

        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        final int keyboardMode = keyboard != null ? keyboard.mId.mMode : -1;
        p.println("  Keyboard mode = " + keyboardMode);
        final SettingsValues settingsValues = mSettings.getCurrent();
        p.println("  mIsSuggestionsSuggestionsRequested = "
                + settingsValues.isSuggestionsRequested(mDisplayOrientation));
        p.println("  mCorrectionEnabled=" + settingsValues.mCorrectionEnabled);
        p.println("  isComposingWord=" + mWordComposer.isComposingWord());
        p.println("  mSoundOn=" + settingsValues.mSoundOn);
        p.println("  mVibrateOn=" + settingsValues.mVibrateOn);
        p.println("  mKeyPreviewPopupOn=" + settingsValues.mKeyPreviewPopupOn);
        p.println("  inputAttributes=" + settingsValues.mInputAttributes);
    }
}
