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

package org.futo.inputmethod.latin;

import static org.futo.inputmethod.latin.common.Constants.ImeOption.FORCE_ASCII;
import static org.futo.inputmethod.latin.common.Constants.ImeOption.NO_MICROPHONE;
import static org.futo.inputmethod.latin.common.Constants.ImeOption.NO_MICROPHONE_COMPAT;

import android.Manifest.permission;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Debug;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.futo.inputmethod.accessibility.AccessibilityUtils;
import org.futo.inputmethod.annotations.UsedForTesting;
import org.futo.inputmethod.compat.ViewOutlineProviderCompatUtils;
import org.futo.inputmethod.compat.ViewOutlineProviderCompatUtils.InsetsUpdater;
import org.futo.inputmethod.event.Event;
import org.futo.inputmethod.event.HardwareEventDecoder;
import org.futo.inputmethod.event.HardwareKeyboardEventDecoder;
import org.futo.inputmethod.event.InputTransaction;
import org.futo.inputmethod.keyboard.Keyboard;
import org.futo.inputmethod.keyboard.KeyboardActionListener;
import org.futo.inputmethod.keyboard.KeyboardId;
import org.futo.inputmethod.keyboard.KeyboardSwitcher;
import org.futo.inputmethod.keyboard.MainKeyboardView;
import org.futo.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback;
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.CoordinateUtils;
import org.futo.inputmethod.latin.common.InputPointers;
import org.futo.inputmethod.latin.define.DebugFlags;
import org.futo.inputmethod.latin.define.ProductionFlags;
import org.futo.inputmethod.latin.inputlogic.InputLogic;
import org.futo.inputmethod.latin.permissions.PermissionsManager;
import org.futo.inputmethod.latin.personalization.PersonalizationHelper;
import org.futo.inputmethod.latin.settings.Settings;
import org.futo.inputmethod.latin.settings.SettingsValues;
import org.futo.inputmethod.latin.suggestions.SuggestionStripView;
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewAccessor;
import org.futo.inputmethod.latin.touchinputconsumer.GestureConsumer;
import org.futo.inputmethod.latin.uix.actions.SwitchLanguageActionKt;
import org.futo.inputmethod.latin.uix.settings.SettingsActivity;
import org.futo.inputmethod.latin.utils.ApplicationUtils;
import org.futo.inputmethod.latin.utils.DialogUtils;
import org.futo.inputmethod.latin.utils.ImportantNoticeUtils;
import org.futo.inputmethod.latin.utils.IntentUtils;
import org.futo.inputmethod.latin.utils.JniUtils;
import org.futo.inputmethod.latin.utils.LeakGuardHandlerWrapper;
import org.futo.inputmethod.latin.utils.StatsUtils;
import org.futo.inputmethod.latin.utils.StatsUtilsManager;
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils;
import org.futo.inputmethod.latin.utils.ViewLayoutUtils;
import org.futo.inputmethod.latin.xlm.LanguageModelFacilitator;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIMELegacy implements KeyboardActionListener,
        SuggestionStripView.Listener, SuggestionStripViewAccessor,
        DictionaryFacilitator.DictionaryInitializationListener,
        PermissionsManager.PermissionsResultCallback {

    public interface SuggestionStripController {
        public void updateVisibility(boolean shouldShowSuggestionsStrip, boolean fullscreenMode);

        public void setSuggestions(SuggestedWords suggestedWords, boolean rtlSubtype);

        public boolean maybeShowImportantNoticeTitle();
    }
    
    private final InputMethodService mInputMethodService;

    static final String TAG = LatinIMELegacy.class.getSimpleName();
    private static final boolean TRACE = false;

    private static final int PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT = 2;
    private static final int PENDING_IMS_CALLBACK_DURATION_MILLIS = 800;
    static final long DELAY_WAIT_FOR_DICTIONARY_LOAD_MILLIS = TimeUnit.SECONDS.toMillis(2);
    static final long DELAY_DEALLOCATE_MEMORY_MILLIS = TimeUnit.SECONDS.toMillis(10);

    /**
     * The name of the scheme used by the Package Manager to warn of a new package installation,
     * replacement or removal.
     */
    private static final String SCHEME_PACKAGE = "package";

    public static boolean mPendingDictionaryUpdate = false;
    public final Settings mSettings;
    private Locale mLocale;
    final DictionaryFacilitator mDictionaryFacilitator =
            DictionaryFacilitatorProvider.getDictionaryFacilitator(
                    false /* isNeededForSpellChecking */);
    final InputLogic mInputLogic;
    // We expect to have only one decoder in almost all cases, hence the default capacity of 1.
    // If it turns out we need several, it will get grown seamlessly.
    final SparseArray<HardwareEventDecoder> mHardwareEventDecoders = new SparseArray<>(1);

    // TODO: Move these {@link View}s to {@link KeyboardSwitcher}.
    private View mInputView;
    private View mComposeInputView;
    private InsetsUpdater mInsetsUpdater;
    private final SuggestionStripController mSuggestionStripController;

    private RichInputMethodManager mRichImm;
    public final KeyboardSwitcher mKeyboardSwitcher;
    private final SubtypeState mSubtypeState = new SubtypeState();
    private EmojiAltPhysicalKeyDetector mEmojiAltPhysicalKeyDetector;
    private StatsUtilsManager mStatsUtilsManager;
    // Working variable for {@link #startShowingInputView()} and
    // {@link #onEvaluateInputViewShown()}.
    private boolean mIsExecutingStartShowingInputView;

    // Used for re-initialize keyboard layout after onConfigurationChange.
    @Nullable private Context mDisplayContext;

    private final BroadcastReceiver mDictionaryDumpBroadcastReceiver =
            new DictionaryDumpBroadcastReceiver(this);

    private AlertDialog mOptionsDialog;

    private final boolean mIsHardwareAcceleratedDrawingEnabled;

    private GestureConsumer mGestureConsumer = GestureConsumer.NULL_GESTURE_CONSUMER;

    public final UIHandler mHandler = new UIHandler(this);

    public static final class UIHandler extends LeakGuardHandlerWrapper<LatinIMELegacy> {
        private static final int MSG_UPDATE_SHIFT_STATE = 0;
        private static final int MSG_PENDING_IMS_CALLBACK = 1;
        private static final int MSG_UPDATE_SUGGESTION_STRIP_LEGACY = 2;
        private static final int MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP = 3;
        private static final int MSG_RESUME_SUGGESTIONS = 4;
        private static final int MSG_REOPEN_DICTIONARIES = 5;
        private static final int MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED = 6;
        private static final int MSG_RESET_CACHES = 7;
        private static final int MSG_WAIT_FOR_DICTIONARY_LOAD = 8;
        private static final int MSG_DEALLOCATE_MEMORY = 9;
        private static final int MSG_RESUME_SUGGESTIONS_FOR_START_INPUT = 10;
        private static final int MSG_SWITCH_LANGUAGE_AUTOMATICALLY = 11;
        // Update this when adding new messages
        private static final int MSG_LAST = MSG_SWITCH_LANGUAGE_AUTOMATICALLY;

        private static final int ARG1_NOT_GESTURE_INPUT = 0;
        private static final int ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 1;
        private static final int ARG1_SHOW_GESTURE_FLOATING_PREVIEW_TEXT = 2;
        private static final int ARG2_UNUSED = 0;
        private static final int ARG1_TRUE = 1;

        private int mDelayInMillisecondsToUpdateSuggestions;
        private int mDelayInMillisecondsToUpdateShiftState;

        public UIHandler(@Nonnull final LatinIMELegacy ownerInstance) {
            super(ownerInstance);
        }

        public void onCreate() {
            final LatinIMELegacy latinImeLegacy = getOwnerInstance();
            if (latinImeLegacy == null) {
                return;
            }
            final Resources res = latinImeLegacy.mInputMethodService.getResources();
            mDelayInMillisecondsToUpdateSuggestions = res.getInteger(
                    R.integer.config_delay_in_milliseconds_to_update_suggestions);
            mDelayInMillisecondsToUpdateShiftState = res.getInteger(
                    R.integer.config_delay_in_milliseconds_to_update_shift_state);
        }

        @Override
        public void handleMessage(final Message msg) {
            final LatinIMELegacy latinImeLegacy = getOwnerInstance();
            if (latinImeLegacy == null) {
                return;
            }
            final KeyboardSwitcher switcher = latinImeLegacy.mKeyboardSwitcher;
            switch (msg.what) {
            case MSG_UPDATE_SUGGESTION_STRIP_LEGACY:
                cancelUpdateSuggestionStrip();
                latinImeLegacy.mInputLogic.performUpdateSuggestionStripSync(
                        latinImeLegacy.mSettings.getCurrent(), msg.arg1 /* inputStyle */);
                break;
            case MSG_UPDATE_SHIFT_STATE:
                switcher.requestUpdatingShiftState(latinImeLegacy.getCurrentAutoCapsState(),
                        latinImeLegacy.getCurrentRecapitalizeState());
                break;
            case MSG_SHOW_GESTURE_PREVIEW_AND_SUGGESTION_STRIP:
                if (msg.arg1 == ARG1_NOT_GESTURE_INPUT) {
                    final SuggestedWords suggestedWords = (SuggestedWords) msg.obj;
                    latinImeLegacy.showSuggestionStrip(suggestedWords);
                } else {
                    latinImeLegacy.showGesturePreviewAndSuggestionStrip((SuggestedWords) msg.obj,
                            msg.arg1 == ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT);
                }
                break;
            case MSG_RESUME_SUGGESTIONS:
                latinImeLegacy.mInputLogic.restartSuggestionsOnWordTouchedByCursor(
                        latinImeLegacy.mSettings.getCurrent(), false /* forStartInput */,
                        latinImeLegacy.mKeyboardSwitcher.getCurrentKeyboardScriptId());
                break;
            case MSG_RESUME_SUGGESTIONS_FOR_START_INPUT:
                latinImeLegacy.mInputLogic.restartSuggestionsOnWordTouchedByCursor(
                        latinImeLegacy.mSettings.getCurrent(), true /* forStartInput */,
                        latinImeLegacy.mKeyboardSwitcher.getCurrentKeyboardScriptId());
                break;
            case MSG_REOPEN_DICTIONARIES:
                // We need to re-evaluate the currently composing word in case the script has
                // changed.
                postWaitForDictionaryLoad();
                latinImeLegacy.resetDictionaryFacilitatorIfNecessary();
                break;
            case MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED:
                final SuggestedWords suggestedWords = (SuggestedWords) msg.obj;
                latinImeLegacy.mInputLogic.onUpdateTailBatchInputCompleted(
                        latinImeLegacy.mSettings.getCurrent(),
                        suggestedWords, latinImeLegacy.mKeyboardSwitcher);
                latinImeLegacy.onTailBatchInputResultShown(suggestedWords);
                break;
            case MSG_RESET_CACHES:
                final SettingsValues settingsValues = latinImeLegacy.mSettings.getCurrent();
                if (latinImeLegacy.mInputLogic.retryResetCachesAndReturnSuccess(
                        msg.arg1 == ARG1_TRUE /* tryResumeSuggestions */,
                        msg.arg2 /* remainingTries */, this /* handler */)) {
                    // If we were able to reset the caches, then we can reload the keyboard.
                    // Otherwise, we'll do it when we can.
                    latinImeLegacy.mKeyboardSwitcher.loadKeyboard(latinImeLegacy.mInputMethodService.getCurrentInputEditorInfo(),
                            settingsValues, latinImeLegacy.getCurrentAutoCapsState(),
                            latinImeLegacy.getCurrentRecapitalizeState());
                }
                break;
            case MSG_WAIT_FOR_DICTIONARY_LOAD:
                Log.i(TAG, "Timeout waiting for dictionary load");
                break;
            case MSG_DEALLOCATE_MEMORY:
                latinImeLegacy.deallocateMemory();
                break;
            }
        }

        public void postUpdateSuggestionStrip(final int inputStyle) {
            final LatinIMELegacy latinImeLegacy = getOwnerInstance();
            assert latinImeLegacy != null;

            final LatinIME latinIme = (LatinIME)latinImeLegacy.getInputMethodService();

            if(!latinIme.postUpdateSuggestionStrip(inputStyle)) {
                updateSuggestionStripLegacy(inputStyle);
            }
        }

        public void updateSuggestionStripLegacy(final int inputStyle) {
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTION_STRIP_LEGACY, inputStyle,
                    0 /* ignored */), mDelayInMillisecondsToUpdateSuggestions);
        }

        public void postReopenDictionaries() {
            sendMessage(obtainMessage(MSG_REOPEN_DICTIONARIES));
        }

        private void postResumeSuggestionsInternal(final boolean shouldDelay,
                final boolean forStartInput) {
            final LatinIMELegacy latinImeLegacy = getOwnerInstance();
            if (latinImeLegacy == null) {
                return;
            }
            if (!latinImeLegacy.mSettings.getCurrent().isSuggestionsEnabledPerUserSettings()) {
                return;
            }
            removeMessages(MSG_RESUME_SUGGESTIONS);
            removeMessages(MSG_RESUME_SUGGESTIONS_FOR_START_INPUT);
            final int message = forStartInput ? MSG_RESUME_SUGGESTIONS_FOR_START_INPUT
                    : MSG_RESUME_SUGGESTIONS;
            if (shouldDelay) {
                sendMessageDelayed(obtainMessage(message),
                        mDelayInMillisecondsToUpdateSuggestions);
            } else {
                sendMessage(obtainMessage(message));
            }
        }

        public void postResumeSuggestions(final boolean shouldDelay) {
            postResumeSuggestionsInternal(shouldDelay, false /* forStartInput */);
        }

        public void postResumeSuggestionsForStartInput(final boolean shouldDelay) {
            postResumeSuggestionsInternal(shouldDelay, true /* forStartInput */);
        }

        public void postResetCaches(final boolean tryResumeSuggestions, final int remainingTries) {
            removeMessages(MSG_RESET_CACHES);
            sendMessage(obtainMessage(MSG_RESET_CACHES, tryResumeSuggestions ? 1 : 0,
                    remainingTries, null));
        }

        public void postWaitForDictionaryLoad() {
            sendMessageDelayed(obtainMessage(MSG_WAIT_FOR_DICTIONARY_LOAD),
                    DELAY_WAIT_FOR_DICTIONARY_LOAD_MILLIS);
        }

        public void cancelWaitForDictionaryLoad() {
            removeMessages(MSG_WAIT_FOR_DICTIONARY_LOAD);
        }

        public boolean hasPendingWaitForDictionaryLoad() {
            return hasMessages(MSG_WAIT_FOR_DICTIONARY_LOAD);
        }

        public void cancelUpdateSuggestionStrip() {
            removeMessages(MSG_UPDATE_SUGGESTION_STRIP_LEGACY);
        }

        public boolean hasPendingUpdateSuggestions() {
            return hasMessages(MSG_UPDATE_SUGGESTION_STRIP_LEGACY);
        }

        public LanguageModelFacilitator getLanguageModelFacilitator() {
            return getOwnerInstance().getLanguageModelFacilitator();
        }

        public boolean hasPendingReopenDictionaries() {
            return hasMessages(MSG_REOPEN_DICTIONARIES);
        }

        public void postUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SHIFT_STATE),
                    mDelayInMillisecondsToUpdateShiftState);
        }

        public void postDeallocateMemory() {
            sendMessageDelayed(obtainMessage(MSG_DEALLOCATE_MEMORY),
                    DELAY_DEALLOCATE_MEMORY_MILLIS);
        }

        public void cancelDeallocateMemory() {
            removeMessages(MSG_DEALLOCATE_MEMORY);
        }

        public boolean hasPendingDeallocateMemory() {
            return hasMessages(MSG_DEALLOCATE_MEMORY);
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
            final LatinIMELegacy latinImeLegacy = getOwnerInstance();
            if (latinImeLegacy == null) {
                return;
            }
            if (latinImeLegacy.mInputMethodService.isInputViewShown()) {
                latinImeLegacy.mKeyboardSwitcher.saveKeyboardState();
            }
        }

        private void resetPendingImsCallback() {
            mHasPendingFinishInputView = false;
            mHasPendingFinishInput = false;
            mHasPendingStartInput = false;
        }

        private void executePendingImsCallback(final LatinIMELegacy latinImeLegacy, final EditorInfo editorInfo,
                                               boolean restarting) {
            if (mHasPendingFinishInputView) {
                latinImeLegacy.onFinishInputViewInternal(mHasPendingFinishInput);
            }
            if (mHasPendingFinishInput) {
                latinImeLegacy.onFinishInputInternal();
            }
            if (mHasPendingStartInput) {
                latinImeLegacy.onStartInputInternal(editorInfo, restarting);
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
                final LatinIMELegacy latinImeLegacy = getOwnerInstance();
                if (latinImeLegacy != null) {
                    executePendingImsCallback(latinImeLegacy, editorInfo, restarting);
                    latinImeLegacy.onStartInputInternal(editorInfo, restarting);
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
                            PENDING_IMS_CALLBACK_DURATION_MILLIS);
                }
                final LatinIMELegacy latinImeLegacy = getOwnerInstance();
                if (latinImeLegacy != null) {
                    executePendingImsCallback(latinImeLegacy, editorInfo, restarting);
                    latinImeLegacy.onStartInputViewInternal(editorInfo, restarting);
                    mAppliedEditorInfo = editorInfo;
                }
                cancelDeallocateMemory();
            }
        }

        public void onFinishInputView(final boolean finishingInput) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInputView after orientation changed.
                mHasPendingFinishInputView = true;
            } else {
                final LatinIMELegacy latinImeLegacy = getOwnerInstance();
                if (latinImeLegacy != null) {
                    latinImeLegacy.onFinishInputViewInternal(finishingInput);
                    mAppliedEditorInfo = null;
                }
                if (!hasPendingDeallocateMemory()) {
                    postDeallocateMemory();
                }
            }
        }

        public void onFinishInput() {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInput after orientation changed.
                mHasPendingFinishInput = true;
            } else {
                final LatinIMELegacy latinImeLegacy = getOwnerInstance();
                if (latinImeLegacy != null) {
                    executePendingImsCallback(latinImeLegacy, null, false);
                    latinImeLegacy.onFinishInputInternal();
                }
            }
        }

        public void triggerAction(int actionId) {
            final LatinIMELegacy latinImeLegacy = getOwnerInstance();
            if (latinImeLegacy != null) {
                ((LatinIME) (latinImeLegacy.getInputMethodService())).getUixManager().triggerAction(actionId);
            }
        }
    }

    static final class SubtypeState {
        private InputMethodSubtype mLastActiveSubtype;
        private boolean mCurrentSubtypeHasBeenUsed;

        public void setCurrentSubtypeHasBeenUsed() {
            mCurrentSubtypeHasBeenUsed = true;
        }

    }

    // Loading the native library eagerly to avoid unexpected UnsatisfiedLinkError at the initial
    // JNI call as much as possible.
    static {
        JniUtils.loadNativeLibrary();
    }

    public LatinIMELegacy(InputMethodService inputMethodService, SuggestionStripController suggestionStripController) {
        super();
        mInputMethodService = inputMethodService;
        mSuggestionStripController = suggestionStripController;
        mSettings = Settings.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mStatsUtilsManager = StatsUtilsManager.getInstance();
        mIsHardwareAcceleratedDrawingEnabled = true;
        Log.i(TAG, "Hardware accelerated drawing: " + mIsHardwareAcceleratedDrawingEnabled);

        mInputLogic = new InputLogic(this /* LatinIME */,
                this /* SuggestionStripViewAccessor */, mDictionaryFacilitator);
    }

    public void onCreate() {
        Settings.init(mInputMethodService);
        DebugFlags.init(PreferenceManager.getDefaultSharedPreferences(mInputMethodService));
        RichInputMethodManager.init(mInputMethodService);
        mRichImm = RichInputMethodManager.getInstance();
        AudioAndHapticFeedbackManager.init(mInputMethodService);
        AccessibilityUtils.init(mInputMethodService);
        mStatsUtilsManager.onCreate(mInputMethodService, mDictionaryFacilitator);
        final WindowManager wm = mInputMethodService.getSystemService(WindowManager.class);
        mDisplayContext = getDisplayContext();
        KeyboardSwitcher.init(this);

        mHandler.onCreate();

        // TODO: Resolve mutual dependencies of {@link #loadSettings()} and
        // {@link #resetDictionaryFacilitatorIfNecessary()}.
        loadSettings();
        resetDictionaryFacilitatorIfNecessary();

        // Register to receive ringer mode change.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        ContextCompat.registerReceiver(mInputMethodService, mRingerModeChangeReceiver, filter, ContextCompat.RECEIVER_EXPORTED);

        final IntentFilter dictDumpFilter = new IntentFilter();
        dictDumpFilter.addAction(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION);
        ContextCompat.registerReceiver(mInputMethodService, mDictionaryDumpBroadcastReceiver, dictDumpFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        StatsUtils.onCreate(mSettings.getCurrent(), mRichImm);
    }

    // Has to be package-visible for unit tests
    @UsedForTesting
    void loadSettings() {
        mLocale = mRichImm.getCurrentSubtypeLocale();
        final EditorInfo editorInfo = mInputMethodService.getCurrentInputEditorInfo();
        final InputAttributes inputAttributes = new InputAttributes(
                editorInfo, mInputMethodService.isFullscreenMode(), mInputMethodService.getPackageName());
        mSettings.loadSettings(mInputMethodService, mLocale, inputAttributes);
        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        AudioAndHapticFeedbackManager.getInstance().onSettingsChanged(currentSettingsValues);
        // This method is called on startup and language switch, before the new layout has
        // been displayed. Opening dictionaries never affects responsivity as dictionaries are
        // asynchronously loaded.
        if (!mHandler.hasPendingReopenDictionaries()) {
            resetDictionaryFacilitator(mLocale);
        }
        refreshPersonalizationDictionarySession(currentSettingsValues);
        resetDictionaryFacilitatorIfNecessary();
        mStatsUtilsManager.onLoadSettings(mInputMethodService, currentSettingsValues);
    }

    private void refreshPersonalizationDictionarySession(
            final SettingsValues currentSettingsValues) {
        if (!currentSettingsValues.mUsePersonalizedDicts) {
            // Remove user history dictionaries.
            PersonalizationHelper.removeAllUserHistoryDictionaries(mInputMethodService);
            mDictionaryFacilitator.clearUserHistoryDictionary(mInputMethodService);
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
            mHandler.postResumeSuggestions(false /* shouldDelay */);
        }
    }

    void resetDictionaryFacilitatorIfNecessary() {
        final Locale subtypeSwitcherLocale = mRichImm.getCurrentSubtypeLocale();
        final Locale subtypeLocale;
        if (subtypeSwitcherLocale == null) {
            // This happens in very rare corner cases - for example, immediately after a switch
            // to LatinIME has been requested, about a frame later another switch happens. In this
            // case, we are about to go down but we still don't know it, however the system tells
            // us there is no current subtype.
            Log.e(TAG, "System is reporting no current subtype.");
            subtypeLocale = mInputMethodService.getResources().getConfiguration().locale;
        } else {
            subtypeLocale = subtypeSwitcherLocale;
        }
        if (mDictionaryFacilitator.isForLocale(subtypeLocale)
                && mDictionaryFacilitator.isForAccount(mSettings.getCurrent().mAccount)) {
            return;
        }
        resetDictionaryFacilitator(subtypeLocale);
    }

    /**
     * Reset the facilitator by loading dictionaries for the given locale and
     * the current settings values.
     *
     * @param locale the locale
     */
    // TODO: make sure the current settings always have the right locales, and read from them.
    private void resetDictionaryFacilitator(final Locale locale) {
        final SettingsValues settingsValues = mSettings.getCurrent();
        mDictionaryFacilitator.resetDictionaries(mInputMethodService, locale,
                settingsValues.mUseContactsDict, settingsValues.mUsePersonalizedDicts,
                false /* forceReloadMainDictionary */,
                settingsValues.mAccount, "" /* dictNamePrefix */,
                this /* DictionaryInitializationListener */);
        if (settingsValues.mAutoCorrectionEnabledPerUserSettings) {
            mInputLogic.mSuggest.setAutoCorrectionThreshold(
                    settingsValues.mAutoCorrectionThreshold);
        }
        mInputLogic.mSuggest.setPlausibilityThreshold(settingsValues.mPlausibilityThreshold);
    }

    /**
     * Reset suggest by loading the main dictionary of the current locale.
     */
    /* package private */ void resetSuggestMainDict() {
        final SettingsValues settingsValues = mSettings.getCurrent();
        mDictionaryFacilitator.resetDictionaries(mInputMethodService,
                mDictionaryFacilitator.getLocale(), settingsValues.mUseContactsDict,
                settingsValues.mUsePersonalizedDicts,
                true /* forceReloadMainDictionary */,
                settingsValues.mAccount, "" /* dictNamePrefix */,
                this /* DictionaryInitializationListener */);
    }

    public void onDestroy() {
        mDictionaryFacilitator.closeDictionaries();
        mSettings.onDestroy();
        mInputMethodService.unregisterReceiver(mRingerModeChangeReceiver);
        mInputMethodService.unregisterReceiver(mDictionaryDumpBroadcastReceiver);
        mStatsUtilsManager.onDestroy(mInputMethodService);
    }

    @UsedForTesting
    public void recycle() {
        mInputMethodService.unregisterReceiver(mDictionaryDumpBroadcastReceiver);
        mInputMethodService.unregisterReceiver(mRingerModeChangeReceiver);
        mInputLogic.recycle();
    }

    public boolean isImeSuppressedByHardwareKeyboard() {
        if(true) return false; // TODO: This function returning true causes some initialization issues

        final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        return !onEvaluateInputViewShown() && switcher.isImeSuppressedByHardwareKeyboard(
                mSettings.getCurrent(), switcher.getKeyboardSwitchState());
    }

    public void onConfigurationChanged(final Configuration conf) {
        SettingsValues settingsValues = mSettings.getCurrent();
        if (settingsValues.mDisplayOrientation != conf.orientation) {
            mHandler.startOrientationChanging();
            mInputLogic.onOrientationChange(mSettings.getCurrent());
        }
        if (settingsValues.mHasHardwareKeyboard != Settings.readHasHardwareKeyboard(conf)) {
            // If the state of having a hardware keyboard changed, then we want to reload the
            // settings to adjust for that.
            // TODO: we should probably do this unconditionally here, rather than only when we
            // have a change in hardware keyboard configuration.
            loadSettings();
            settingsValues = mSettings.getCurrent();
            if (isImeSuppressedByHardwareKeyboard()) {
                // We call cleanupInternalStateForFinishInput() because it's the right thing to do;
                // however, it seems at the moment the framework is passing us a seemingly valid
                // but actually non-functional InputConnection object. So if this bug ever gets
                // fixed we'll be able to remove the composition, but until it is this code is
                // actually not doing much.
                cleanupInternalStateForFinishInput();
            }
        }
    }

    public void onInitializeInterface() {
        mDisplayContext = getDisplayContext();
        mKeyboardSwitcher.updateKeyboardTheme(mDisplayContext);
    }

    /**
     * Returns the context object whose resources are adjusted to match the metrics of the display.
     *
     * Note that before {@link android.os.Build.VERSION_CODES#KITKAT}, there is no way to support
     * multi-display scenarios, so the context object will just return the IME context itself.
     *
     * With initiating multi-display APIs from {@link android.os.Build.VERSION_CODES#KITKAT}, the
     * context object has to return with re-creating the display context according the metrics
     * of the display in runtime.
     *
     * Starts from {@link android.os.Build.VERSION_CODES#S_V2}, the returning context object has
     * became to IME context self since it ends up capable of updating its resources internally.
     *
     * @see android.content.Context#createDisplayContext(Display)
     */
    private @NonNull Context getDisplayContext() {
        // TODO: We need to pass LatinIME for theming
        /*
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // createDisplayContext is not available.
            return mInputMethodService;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            // IME context sources is now managed by WindowProviderService from Android 12L.
            return mInputMethodService;
        }
        // An issue in Q that non-activity components Resources / DisplayMetrics in
        // Context doesn't well updated when the IME window moving to external display.
        // Currently we do a workaround is to create new display context directly and re-init
        // keyboard layout with this context.
        final WindowManager wm = (WindowManager) mInputMethodService.getSystemService(Context.WINDOW_SERVICE);
        return mInputMethodService.createDisplayContext(wm.getDefaultDisplay());
        */
        return mInputMethodService;
    }

    public View onCreateInputView() {
        StatsUtils.onCreateInputView();
        assert mDisplayContext != null;
        return mKeyboardSwitcher.onCreateInputView(mDisplayContext,
                mIsHardwareAcceleratedDrawingEnabled);
    }

    public void updateTheme() {
        mKeyboardSwitcher.queueThemeSwitch();
        mKeyboardSwitcher.updateKeyboardTheme(mDisplayContext);
    }


    public void setComposeInputView(final View view) {
        mComposeInputView = view;
        mInsetsUpdater = ViewOutlineProviderCompatUtils.setInsetsOutlineProvider(view);
        updateSoftInputWindowLayoutParameters();
    }

    public void setInputView(final View view) {
        mInputView = view;
    }

    public void setCandidatesView(final View view) {
        // To ensure that CandidatesView will never be set.
    }

    public void onStartInput(final EditorInfo editorInfo, final boolean restarting) {
        if(mPendingDictionaryUpdate) {
            Log.i(TAG, "Pending dictionary update received, posting update dictionaries...");
            mPendingDictionaryUpdate = false;
            resetSuggestMainDict();
        }
        mHandler.onStartInput(editorInfo, restarting);
    }

    public void onStartInputView(final EditorInfo editorInfo, final boolean restarting) {
        mHandler.onStartInputView(editorInfo, restarting);
        mStatsUtilsManager.onStartInputView();
    }

    public void onFinishInputView(final boolean finishingInput) {
        StatsUtils.onFinishInputView();
        mHandler.onFinishInputView(finishingInput);
        mStatsUtilsManager.onFinishInputView();
        mGestureConsumer = GestureConsumer.NULL_GESTURE_CONSUMER;
    }

    public void onFinishInput() {
        mHandler.onFinishInput();
    }

    public void onCurrentInputMethodSubtypeChanged(final InputMethodSubtype subtype) {
        // Note that the calling sequence of onCreate() and onCurrentInputMethodSubtypeChanged()
        // is not guaranteed. It may even be called at the same time on a different thread.
        InputMethodSubtype oldSubtype = mRichImm.getCurrentSubtype().getRawSubtype();
        StatsUtils.onSubtypeChanged(oldSubtype, subtype);
        mRichImm.onSubtypeChanged(subtype);
        mInputLogic.onSubtypeChanged(SubtypeLocaleUtils.getCombiningRulesExtraValue(subtype),
                mSettings.getCurrent());
        loadKeyboard();
    }

    void onStartInputInternal(final EditorInfo editorInfo, final boolean restarting) {

    }

    public void updateMainKeyboardViewSettings() {
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        SettingsValues currentSettingsValues = mSettings.getCurrent();

        mainKeyboardView.setMainDictionaryAvailability(
                mDictionaryFacilitator.hasAtLeastOneInitializedMainDictionary());
        mainKeyboardView.setKeyPreviewPopupEnabled(currentSettingsValues.mKeyPreviewPopupOn,
                currentSettingsValues.mKeyPreviewPopupDismissDelay);
        mainKeyboardView.setSlidingKeyInputPreviewEnabled(
                currentSettingsValues.mSlidingKeyInputPreviewEnabled);
        mainKeyboardView.setGestureHandlingEnabledByUser(
                currentSettingsValues.mGestureInputEnabled,
                currentSettingsValues.mGestureTrailEnabled,
                currentSettingsValues.mGestureFloatingPreviewTextEnabled);
    }

    @SuppressWarnings("deprecation")
    void onStartInputViewInternal(final EditorInfo editorInfo, final boolean restarting) {
        mDictionaryFacilitator.onStartInput();
        // Switch to the null consumer to handle cases leading to early exit below, for which we
        // also wouldn't be consuming gesture data.
        mGestureConsumer = GestureConsumer.NULL_GESTURE_CONSUMER;
        mRichImm.refreshSubtypeCaches();
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        switcher.updateKeyboardTheme(mDisplayContext);
        final MainKeyboardView mainKeyboardView = switcher.getMainKeyboardView();
        // If we are starting input in a different text field from before, we'll have to reload
        // settings, so currentSettingsValues can't be final.
        SettingsValues currentSettingsValues = mSettings.getCurrent();

        if (editorInfo == null) {
            Log.e(TAG, "Null EditorInfo in onStartInputView()");
            if (DebugFlags.DEBUG_ENABLED) {
                throw new NullPointerException("Null EditorInfo in onStartInputView()");
            }
            return;
        }
        if (DebugFlags.DEBUG_ENABLED) {
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
            Log.w(TAG, "Use " + mInputMethodService.getPackageName() + "." + NO_MICROPHONE + " instead");
        }
        if (InputAttributes.inPrivateImeOptions(mInputMethodService.getPackageName(), FORCE_ASCII, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: " + editorInfo.privateImeOptions);
            Log.w(TAG, "Use EditorInfo.IME_FLAG_FORCE_ASCII flag instead");
        }

        // In landscape mode, this method gets called without the input view being created.
        if (mainKeyboardView == null) {
            return;
        }

        // Update to a gesture consumer with the current editor and IME state.
        mGestureConsumer = GestureConsumer.newInstance(editorInfo,
                mInputLogic.getPrivateCommandPerformer(),
                mRichImm.getCurrentSubtypeLocale(),
                switcher.getKeyboard());

        // Forward this event to the accessibility utilities, if enabled.
        final AccessibilityUtils accessUtils = AccessibilityUtils.getInstance();
        if (accessUtils.isTouchExplorationEnabled()) {
            accessUtils.onStartInputViewInternal(mainKeyboardView, editorInfo, restarting);
        }

        final boolean inputTypeChanged = !currentSettingsValues.isSameInputType(editorInfo);
        final boolean isDifferentTextField = !restarting || inputTypeChanged;

        StatsUtils.onStartInputView(editorInfo.inputType,
                Settings.getInstance().getCurrent().mDisplayOrientation,
                !isDifferentTextField);

        // The EditorInfo might have a flag that affects fullscreen mode.
        // Note: This call should be done by InputMethodService?
        updateFullscreenMode();

        // ALERT: settings have not been reloaded and there is a chance they may be stale.
        // In the practice, if it is, we should have gotten onConfigurationChanged so it should
        // be fine, but this is horribly confusing and must be fixed AS SOON AS POSSIBLE.

        // In some cases the input connection has not been reset yet and we can't access it. In
        // this case we will need to call loadKeyboard() later, when it's accessible, so that we
        // can go into the correct mode, so we need to do some housekeeping here.
        final boolean needToCallLoadKeyboardLater;
        final Suggest suggest = mInputLogic.mSuggest;
        if (!isImeSuppressedByHardwareKeyboard()) {
            // The app calling setText() has the effect of clearing the composing
            // span, so we should reset our state unconditionally, even if restarting is true.
            // We also tell the input logic about the combining rules for the current subtype, so
            // it can adjust its combiners if needed.
            mInputLogic.startInput(mRichImm.getCombiningRulesExtraValueOfCurrentSubtype(),
                    currentSettingsValues);

            resetDictionaryFacilitatorIfNecessary();

            // TODO[IL]: Can the following be moved to InputLogic#startInput?
            if (!mInputLogic.mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                    editorInfo.initialSelStart, editorInfo.initialSelEnd,
                    false /* shouldFinishComposition */)) {
                // Sometimes, while rotating, for some reason the framework tells the app we are not
                // connected to it and that means we can't refresh the cache. In this case, schedule
                // a refresh later.
                // We try resetting the caches up to 5 times before giving up.
                mHandler.postResetCaches(isDifferentTextField, 5 /* remainingTries */);
                // mLastSelection{Start,End} are reset later in this method, no need to do it here
                needToCallLoadKeyboardLater = true;
            } else {
                // When rotating, and when input is starting again in a field from where the focus
                // didn't move (the keyboard having been closed with the back key),
                // initialSelStart and initialSelEnd sometimes are lying. Make a best effort to
                // work around this bug.
                mInputLogic.mConnection.tryFixLyingCursorPosition();
                mHandler.postResumeSuggestionsForStartInput(true /* shouldDelay */);
                needToCallLoadKeyboardLater = false;
            }
        } else {
            // If we have a hardware keyboard we don't need to call loadKeyboard later anyway.
            needToCallLoadKeyboardLater = false;
        }

        if (isDifferentTextField ||
                !currentSettingsValues.hasSameOrientation(mInputMethodService.getResources().getConfiguration())) {
            loadSettings();
        }
        if (isDifferentTextField) {
            mainKeyboardView.closing();
            currentSettingsValues = mSettings.getCurrent();

            if (currentSettingsValues.mAutoCorrectionEnabledPerUserSettings) {
                suggest.setAutoCorrectionThreshold(
                        currentSettingsValues.mAutoCorrectionThreshold);
            }
            suggest.setPlausibilityThreshold(currentSettingsValues.mPlausibilityThreshold);

            switcher.loadKeyboard(editorInfo, currentSettingsValues, getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
            if (needToCallLoadKeyboardLater) {
                // If we need to call loadKeyboard again later, we need to save its state now. The
                // later call will be done in #retryResetCaches.
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

        updateMainKeyboardViewSettings();

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    public void onWindowShown() {
        setNavigationBarVisibility(mInputMethodService.isInputViewShown());
    }

    public void onWindowHidden() {
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
        setNavigationBarVisibility(false);
    }

    void onFinishInputInternal() {
        mDictionaryFacilitator.onFinishInput(mInputMethodService);
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
    }

    void onFinishInputViewInternal(final boolean finishingInput) {
        cleanupInternalStateForFinishInput();
    }

    private void cleanupInternalStateForFinishInput() {
        // Remove pending messages related to update suggestions
        mHandler.cancelUpdateSuggestionStrip();
        // Should do the following in onFinishInputInternal but until JB MR2 it's not called :(
        mInputLogic.finishInput();
    }

    protected void deallocateMemory() {
        mKeyboardSwitcher.deallocateMemory();
    }

    public void onUpdateSelection(final int oldSelStart, final int oldSelEnd,
            final int newSelStart, final int newSelEnd,
            final int composingSpanStart, final int composingSpanEnd) {
        if (DebugFlags.DEBUG_ENABLED) {
            Log.i(TAG, "onUpdateSelection: oss=" + oldSelStart + ", ose=" + oldSelEnd
                    + ", nss=" + newSelStart + ", nse=" + newSelEnd
                    + ", cs=" + composingSpanStart + ", ce=" + composingSpanEnd);
        }

        // This call happens whether our view is displayed or not, but if it's not then we should
        // not attempt recorrection. This is true even with a hardware keyboard connected: if the
        // view is not displayed we have no means of showing suggestions anyway, and if it is then
        // we want to show suggestions anyway.
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (mInputMethodService.isInputViewShown()
                && mInputLogic.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                        settingsValues)) {
            mKeyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(),
                    getCurrentRecapitalizeState());
        }
    }

    /**
     * This is called when the user has clicked on the extracted text view,
     * when running in fullscreen mode.  The default implementation hides
     * the suggestions view when this happens, but only if the extracted text
     * editor has a vertical scroll bar because its text doesn't fit.
     * Here we override the behavior due to the possibility that a re-correction could
     * cause the suggestions strip to disappear and re-appear.
     */
    public void onExtractedTextClicked() {
        if (mSettings.getCurrent().needsToLookupSuggestions()) {
            return;
        }
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
    public void onExtractedCursorMovement(final int dx, final int dy) {
        if (mSettings.getCurrent().needsToLookupSuggestions()) {
            return;
        }
    }

    public void hideWindow() {
        mKeyboardSwitcher.onHideWindow();

        if (TRACE) Debug.stopMethodTracing();
        if (isShowingOptionDialog()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
    }

    public void onDisplayCompletions(final CompletionInfo[] applicationSpecifiedCompletions) {
        if (DebugFlags.DEBUG_ENABLED) {
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
                null /* rawSuggestions */,
                null /* typedWord */,
                false /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isObsoleteSuggestions */,
                SuggestedWords.INPUT_STYLE_APPLICATION_SPECIFIED /* inputStyle */,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER);
        // When in fullscreen mode, show completions generated by the application forcibly
        setSuggestedWords(suggestedWords);
    }

    public void setInsets(final InputMethodService.Insets insets) {
        mInsetsUpdater.setInsets(insets);
    }

    public void startShowingInputView(final boolean needsToLoadKeyboard) {
        mIsExecutingStartShowingInputView = true;
        // This {@link #showWindow(boolean)} will eventually call back
        // {@link #onEvaluateInputViewShown()}.
        mInputMethodService.showWindow(true /* showInput */);
        mIsExecutingStartShowingInputView = false;
        if (needsToLoadKeyboard) {
            loadKeyboard();
        }
    }

    public void stopShowingInputView() {
        mInputMethodService.showWindow(false /* showInput */);
    }

    public boolean onShowInputRequested(final int flags, final boolean configChange) {
        if (isImeSuppressedByHardwareKeyboard()) {
            return true;
        }
        return false;
    }

    public boolean onEvaluateInputViewShown() {
        if (mIsExecutingStartShowingInputView) {
            return true;
        }
        return false;
    }

    public boolean onEvaluateFullscreenMode(boolean parentFullscreenMode) {
        final SettingsValues settingsValues = mSettings.getCurrent();
        if (isImeSuppressedByHardwareKeyboard()) {
            // If there is a hardware keyboard, disable full screen mode.
            return false;
        }
        // Reread resource value here, because this method is called by the framework as needed.
        final boolean isFullscreenModeAllowed = Settings.readUseFullscreenMode(mInputMethodService.getResources());
        if (parentFullscreenMode && isFullscreenModeAllowed) {
            // TODO: Remove this hack. Actually we should not really assume NO_EXTRACT_UI
            // implies NO_FULLSCREEN. However, the framework mistakenly does.  i.e. NO_EXTRACT_UI
            // without NO_FULLSCREEN doesn't work as expected. Because of this we need this
            // hack for now.  Let's get rid of this once the framework gets fixed.
            final EditorInfo ei = mInputMethodService.getCurrentInputEditorInfo();
            return !(ei != null && ((ei.imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0));
        }
        return false;
    }

    public void updateFullscreenMode() {
        updateSoftInputWindowLayoutParameters();
    }

    private void updateSoftInputWindowLayoutParameters() {
        // Override layout parameters to expand {@link SoftInputWindow} to the entire screen.
        // See {@link InputMethodService#setinputView(View)} and
        // {@link SoftInputWindow#updateWidthHeight(WindowManager.LayoutParams)}.
        final Window window = mInputMethodService.getWindow().getWindow();
        ViewLayoutUtils.updateLayoutHeightOf(window, LayoutParams.MATCH_PARENT);
        // This method may be called before {@link #setInputView(View)}.
        if (mComposeInputView != null) {
            // In non-fullscreen mode, {@link InputView} and its parent inputArea should expand to
            // the entire screen and be placed at the bottom of {@link SoftInputWindow}.
            // In fullscreen mode, these shouldn't expand to the entire screen and should be
            // coexistent with {@link #mExtractedArea} above.
            // See {@link InputMethodService#setInputView(View) and
            // com.android.internal.R.layout.input_method.xml.
            final int layoutHeight = mInputMethodService.isFullscreenMode()
                    ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;
            final View inputArea = window.findViewById(android.R.id.inputArea);
            ViewLayoutUtils.updateLayoutHeightOf(inputArea, layoutHeight);
            ViewLayoutUtils.updateLayoutGravityOf(inputArea, Gravity.BOTTOM);
            ViewLayoutUtils.updateLayoutHeightOf(mComposeInputView, layoutHeight);
        }
    }

    int getCurrentAutoCapsState() {
        return mInputLogic.getCurrentAutoCapsState(mSettings.getCurrent());
    }

    int getCurrentRecapitalizeState() {
        return mInputLogic.getCurrentRecapitalizeState();
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
        }
        return keyboard.getCoordinates(codePoints);
    }

    // Callback for the {@link SuggestionStripView}, to call when the important notice strip is
    // pressed.
    @Override
    public void showImportantNoticeContents() {
        PermissionsManager.get(mInputMethodService).requestPermissions(
                this /* PermissionsResultCallback */,
                null /* activity */, permission.READ_CONTACTS);
    }

    @Override
    public void onRequestPermissionsResult(boolean allGranted) {
        ImportantNoticeUtils.updateContactsNoticeShown(mInputMethodService /* context */);
        setNeutralSuggestionStrip();
    }

    public void displaySettingsDialog() {
        launchSettings("");
    }

    @Override
    public boolean onCustomRequest(final int requestCode) {
        if (isShowingOptionDialog()) return false;
        switch (requestCode) {
        case Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER:
            ((LatinIME)mInputMethodService).getUixManager().showLanguageSwitcher();
            return true;
        }
        return false;
    }

    @Override
    public void onMovePointer(int steps) {
        int shiftMode = mKeyboardSwitcher.getKeyboardShiftMode();
        boolean select = (shiftMode == WordComposer.CAPS_MODE_MANUAL_SHIFTED) || (shiftMode == WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED);

        if(select) {
            mInputLogic.disableRecapitalization();
        }

        if(steps < 0) {
            mInputLogic.cursorLeft(steps, false, select);
        } else {
            mInputLogic.cursorRight(steps, false, select);
        }
    }

    @Override
    public void onMoveDeletePointer(int steps) {
        if (mInputLogic.mConnection.hasCursorPosition()) {
            steps = mInputLogic.mConnection.getUnicodeSteps(steps, false);
            final int end = mInputLogic.mConnection.getExpectedSelectionEnd();
            final int start = mInputLogic.mConnection.getExpectedSelectionStart() + steps;
            if (start > end)
                return;

            mInputLogic.finishInput();
            mInputLogic.mConnection.setSelection(start, end);
        } else {
            for (; steps < 0; steps++)
                onCodeInput(
                        Constants.CODE_DELETE,
                        Constants.NOT_A_COORDINATE,
                        Constants.NOT_A_COORDINATE, false);
        }
    }

    @Override
    public void onUpWithDeletePointerActive() {
        if (mInputLogic.mConnection.hasSelection()) {
            CharSequence selection = mInputLogic.mConnection.getSelectedText(0);
            if(selection != null) {
                ArrayList<SuggestedWordInfo> info = new ArrayList<>();
                info.add(new SuggestedWordInfo(
                        selection.toString(),
                        "",
                        0,
                        SuggestedWordInfo.KIND_UNDO,
                        null,
                        0,
                        0));
                showSuggestionStrip(new SuggestedWords(info,
                        null,
                        null,
                        false,
                        false,
                        false,
                        0,
                        0));
                ((LatinIME)mInputMethodService).languageModelFacilitator.ignoreNextUpdate();
            }
            onCodeInput(
                    Constants.CODE_DELETE,
                    Constants.NOT_A_COORDINATE,
                    Constants.NOT_A_COORDINATE, false);
        }
    }

    @Override
    public void onUpWithPointerActive() {
        mInputLogic.restartSuggestionsOnWordTouchedByCursor(mSettings.getCurrent(), false, mKeyboardSwitcher.getCurrentKeyboardScriptId());
    }

    private boolean isShowingOptionDialog() {
        return mOptionsDialog != null && mOptionsDialog.isShowing();
    }

    public void switchToNextSubtype() {
        SwitchLanguageActionKt.switchToNextLanguage(mInputMethodService);
    }

    // TODO: Instead of checking for alphabetic keyboard here, separate keycodes for
    // alphabetic shift and shift while in symbol layout and get rid of this method.
    private int getCodePointForKeyboard(final int codePoint) {
        if (Constants.CODE_SHIFT == codePoint) {
            final Keyboard currentKeyboard = mKeyboardSwitcher.getKeyboard();
            if (null != currentKeyboard && currentKeyboard.mId.isAlphabetKeyboard()) {
                return codePoint;
            }
            return Constants.CODE_SYMBOL_SHIFT;
        }
        return codePoint;
    }

    // Implementation of {@link KeyboardActionListener}.
    @Override
    public void onCodeInput(final int codePoint, final int x, final int y,
            final boolean isKeyRepeat) {
        // TODO: this processing does not belong inside LatinIME, the caller should be doing this.
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        // x and y include some padding, but everything down the line (especially native
        // code) needs the coordinates in the keyboard frame.
        // TODO: We should reconsider which coordinate system should be used to represent
        // keyboard event. Also we should pull this up -- LatinIME has no business doing
        // this transformation, it should be done already before calling onEvent.
        final int keyX = mainKeyboardView.getKeyX(x);
        final int keyY = mainKeyboardView.getKeyY(y);
        final Event event = createSoftwareKeypressEvent(getCodePointForKeyboard(codePoint),
                keyX, keyY, isKeyRepeat);
        onEvent(event);
    }

    // This method is public for testability of LatinIME, but also in the future it should
    // completely replace #onCodeInput.
    public void onEvent(@Nonnull final Event event) {
        if (Constants.CODE_SHORTCUT == event.mKeyCode) {
            mRichImm.switchToShortcutIme(mInputMethodService);
        }
        final InputTransaction completeInputTransaction =
                mInputLogic.onCodeInput(mSettings.getCurrent(), event,
                        mKeyboardSwitcher.getKeyboardShiftMode(),
                        mKeyboardSwitcher.getCurrentKeyboardScriptId(), mHandler);
        updateStateAfterInputTransaction(completeInputTransaction);
        mKeyboardSwitcher.onEvent(event, getCurrentAutoCapsState(), getCurrentRecapitalizeState());
    }

    // A helper method to split the code point and the key code. Ultimately, they should not be
    // squashed into the same variable, and this method should be removed.
    // public for testing, as we don't want to copy the same logic into test code
    @Nonnull
    public static Event createSoftwareKeypressEvent(final int keyCodeOrCodePoint, final int keyX,
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
        final Event event = Event.createSoftwareTextEvent(rawText, Constants.CODE_OUTPUT_TEXT);
        final InputTransaction completeInputTransaction =
                mInputLogic.onTextInput(mSettings.getCurrent(), event,
                        mKeyboardSwitcher.getKeyboardShiftMode(), mHandler);
        updateStateAfterInputTransaction(completeInputTransaction);
        mKeyboardSwitcher.onEvent(event, getCurrentAutoCapsState(), getCurrentRecapitalizeState());
    }

    @Override
    public void onStartBatchInput() {
        mInputLogic.onStartBatchInput(mSettings.getCurrent(), mKeyboardSwitcher, mHandler);
        mGestureConsumer.onGestureStarted(
                mRichImm.getCurrentSubtypeLocale(),
                mKeyboardSwitcher.getKeyboard());
    }

    @Override
    public void onUpdateBatchInput(final InputPointers batchPointers) {
        mInputLogic.onUpdateBatchInput(batchPointers);
    }

    @Override
    public void onEndBatchInput(final InputPointers batchPointers) {
        mInputLogic.onEndBatchInput(batchPointers);
        mGestureConsumer.onGestureCompleted(batchPointers);
    }

    @Override
    public void onCancelBatchInput() {
        mInputLogic.onCancelBatchInput(mHandler);
        mGestureConsumer.onGestureCanceled();
    }

    /**
     * To be called after the InputLogic has gotten a chance to act on the suggested words by the
     * IME for the full gesture, possibly updating the TextView to reflect the first suggestion.
     * <p>
     * This method must be run on the UI Thread.
     * @param suggestedWords suggested words by the IME for the full gesture.
     */
    public void onTailBatchInputResultShown(final SuggestedWords suggestedWords) {
        mGestureConsumer.onImeSuggestionsProcessed(suggestedWords,
                mInputLogic.getComposingStart(), mInputLogic.getComposingLength(),
                mDictionaryFacilitator);
    }

    // This method must run on the UI Thread.
    void showGesturePreviewAndSuggestionStrip(@Nonnull final SuggestedWords suggestedWords,
            final boolean dismissGestureFloatingPreviewText) {
        showSuggestionStrip(suggestedWords);
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        mainKeyboardView.showGestureFloatingPreviewText(suggestedWords,
                dismissGestureFloatingPreviewText /* dismissDelayed */);
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
        return null != mSuggestionStripController;
    }

    private void setSuggestedWords(final SuggestedWords suggestedWords) {
        final SettingsValues currentSettingsValues = mSettings.getCurrent();
        mInputLogic.setSuggestedWords(suggestedWords);
        // TODO: Modify this when we support suggestions with hard keyboard
        if (!hasSuggestionStripView()) {
            return;
        }
        if (!mInputMethodService.onEvaluateInputViewShown()) {
            return;
        }

        final boolean shouldShowImportantNotice =
                ImportantNoticeUtils.shouldShowImportantNotice(mInputMethodService, currentSettingsValues);
        final boolean shouldShowSuggestionCandidates =
                currentSettingsValues.mInputAttributes.mShouldShowSuggestions
                && currentSettingsValues.isSuggestionsEnabledPerUserSettings();
        final boolean shouldShowSuggestionsStripUnlessPassword = shouldShowImportantNotice
                || currentSettingsValues.mShowsVoiceInputKey
                || shouldShowSuggestionCandidates
                || currentSettingsValues.isApplicationSpecifiedCompletionsOn();
        final boolean shouldShowSuggestionsStrip = shouldShowSuggestionsStripUnlessPassword
                && !currentSettingsValues.mInputAttributes.mIsPasswordField;
        mSuggestionStripController.updateVisibility(shouldShowSuggestionsStrip, mInputMethodService.isFullscreenMode());
        if (!shouldShowSuggestionsStrip) {
            return;
        }

        final boolean isEmptyApplicationSpecifiedCompletions =
                currentSettingsValues.isApplicationSpecifiedCompletionsOn()
                && suggestedWords.isEmpty();
        final boolean noSuggestionsFromDictionaries = suggestedWords.isEmpty()
                || suggestedWords.isPunctuationSuggestions()
                || isEmptyApplicationSpecifiedCompletions;
        final boolean isBeginningOfSentencePrediction = (suggestedWords.mInputStyle
                == SuggestedWords.INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION);
        final boolean noSuggestionsToOverrideImportantNotice = noSuggestionsFromDictionaries
                || isBeginningOfSentencePrediction;
        if (shouldShowImportantNotice && noSuggestionsToOverrideImportantNotice) {
            if (mSuggestionStripController.maybeShowImportantNoticeTitle()) {
                return;
            }
        }

        if (currentSettingsValues.isSuggestionsEnabledPerUserSettings()
                || currentSettingsValues.isApplicationSpecifiedCompletionsOn()
                // We should clear the contextual strip if there is no suggestion from dictionaries.
                || noSuggestionsFromDictionaries) {
            mSuggestionStripController.setSuggestions(suggestedWords,
                    mRichImm.getCurrentSubtype().isRtlSubtype());
        }
    }

    // TODO[IL]: Move this out of LatinIME.
    public void getSuggestedWords(final int inputStyle, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        SettingsValues settings = mSettings.getCurrent();
        if(((LatinIME)getInputMethodService()).postUpdateSuggestionStrip(inputStyle)) {
            return;
        }

        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        if (keyboard == null) {
            callback.onGetSuggestedWords(SuggestedWords.getEmptyInstance());
            return;
        }
        mInputLogic.getSuggestedWords(settings, keyboard,
                mKeyboardSwitcher.getKeyboardShiftMode(), inputStyle, sequenceNumber, callback);
    }

    @Override
    public void showSuggestionStrip(SuggestedWords suggestedWords) {
        suggestedWords = ((LatinIME) mInputMethodService).getSuggestionBlacklist().filterBlacklistedSuggestions(suggestedWords);

        if (suggestedWords.isEmpty()) {
            setNeutralSuggestionStrip();
        } else {
            setSuggestedWords(suggestedWords);
        }
        // Cache the auto-correction in accessibility code so we can speak it if the user
        // touches a key that will insert it.
        AccessibilityUtils.getInstance().setAutoCorrection(suggestedWords);
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

        if(suggestionInfo.isKindOf(SuggestedWordInfo.KIND_EMOJI_SUGGESTION)) {
            ((LatinIME)mInputMethodService).rememberEmojiSuggestion(suggestionInfo);
        }
    }

    @Override
    public void requestForgetWord(SuggestedWordInfo word) {
        ((LatinIME)mInputMethodService).requestForgetWord(word);
    }

    // This will show either an empty suggestion strip (if prediction is enabled) or
    // punctuation suggestions (if it's disabled).
    @Override
    public void setNeutralSuggestionStrip() {
        final SettingsValues currentSettings = mSettings.getCurrent();
        final SuggestedWords neutralSuggestions = currentSettings.mBigramPredictionEnabled
                ? SuggestedWords.getEmptyInstance()
                : currentSettings.mSpacingAndPunctuations.mSuggestPuncList;
        setSuggestedWords(neutralSuggestions);
    }

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
            mKeyboardSwitcher.loadKeyboard(mInputMethodService.getCurrentInputEditorInfo(), mSettings.getCurrent(),
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
            final int inputStyle;
            if (inputTransaction.mEvent.isSuggestionStripPress()) {
                // Suggestion strip press: no input.
                inputStyle = SuggestedWords.INPUT_STYLE_NONE;
            } else if (inputTransaction.mEvent.isGesture()) {
                inputStyle = SuggestedWords.INPUT_STYLE_TAIL_BATCH;
            } else {
                inputStyle = SuggestedWords.INPUT_STYLE_TYPING;
            }
            mHandler.postUpdateSuggestionStrip(inputStyle);
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
    public boolean onKeyDown(final int keyCode, final KeyEvent keyEvent) {
        if (mEmojiAltPhysicalKeyDetector == null) {
            mEmojiAltPhysicalKeyDetector = new EmojiAltPhysicalKeyDetector(
                    mInputMethodService.getApplicationContext().getResources());
        }
        mEmojiAltPhysicalKeyDetector.onKeyDown(keyEvent);
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED) {
            return false;
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
        return false;
    }

    public boolean onKeyUp(final int keyCode, final KeyEvent keyEvent) {
        if (mEmojiAltPhysicalKeyDetector == null) {
            mEmojiAltPhysicalKeyDetector = new EmojiAltPhysicalKeyDetector(
                    mInputMethodService.getApplicationContext().getResources());
        }
        mEmojiAltPhysicalKeyDetector.onKeyUp(keyEvent);
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED) {
            return false;
        }
        final long keyIdentifier = keyEvent.getDeviceId() << 32 + keyEvent.getKeyCode();
        if (mInputLogic.mCurrentlyPressedHardwareKeys.remove(keyIdentifier)) {
            return true;
        }
        return false;
    }

    // onKeyDown and onKeyUp are the main events we are interested in. There are two more events
    // related to handling of hardware key events that we may want to implement in the future:
    // boolean onKeyLongPress(final int keyCode, final KeyEvent event);
    // boolean onKeyMultiple(final int keyCode, final int count, final KeyEvent event);

    // receive ringer mode change.
    private final BroadcastReceiver mRingerModeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                AudioAndHapticFeedbackManager.getInstance().onRingerModeChanged();
            }
        }
    };

    /**
     * Starts {@link Activity} on the same display where the IME is shown.
     *
     * @param intent {@link Intent} to be used to start {@link Activity}.
     */
    private void startActivityOnTheSameDisplay(Intent intent) {
        // Note that WindowManager#getDefaultDisplay() returns the display ID associated with the
        // Context from which the WindowManager instance was obtained. Therefore the following code
        // returns the display ID for the window where the IME is shown.
        final int currentDisplayId = ((WindowManager) mInputMethodService.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getDisplayId();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mInputMethodService.startActivity(intent,
                    ActivityOptions.makeBasic().setLaunchDisplayId(currentDisplayId).toBundle());
        }
    }

    void launchSettings(final String extraEntryValue) {
        mInputLogic.commitTyped(mSettings.getCurrent(), LastComposedWord.NOT_A_SEPARATOR);
        mInputMethodService.requestHideSelf(0);
        final MainKeyboardView mainKeyboardView = mKeyboardSwitcher.getMainKeyboardView();
        if (mainKeyboardView != null) {
            mainKeyboardView.closing();
        }
        final Intent intent = new Intent();
        intent.setClass(mInputMethodService, SettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        //intent.putExtra(SettingsActivity.EXTRA_SHOW_HOME_AS_UP, false);
        //intent.putExtra(SettingsActivity.EXTRA_ENTRY_KEY, extraEntryValue);
        startActivityOnTheSameDisplay(intent);
    }

    private void showSubtypeSelectorAndSettings() {
        final CharSequence title = mInputMethodService.getString(R.string.english_ime_input_options);
        // TODO: Should use new string "Select active input modes".
        final CharSequence languageSelectionTitle = mInputMethodService.getString(R.string.language_selection_title);
        final CharSequence[] items = new CharSequence[] {
                languageSelectionTitle,
                mInputMethodService.getString(ApplicationUtils.getActivityTitleResId(mInputMethodService, SettingsActivity.class))
        };
        final String imeId = mRichImm.getInputMethodIdOfThisIme();
        final OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                case 0:
                    final Intent intent = IntentUtils.getInputLanguageSelectionIntent(
                            imeId,
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra(Intent.EXTRA_TITLE, languageSelectionTitle);
                    startActivityOnTheSameDisplay(intent);
                    break;
                case 1:
                    launchSettings("");
                    break;
                }
            }
        };
        final AlertDialog.Builder builder = new AlertDialog.Builder(
                DialogUtils.getPlatformDialogThemeContext(mInputMethodService));
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

    @UsedForTesting
    SuggestedWords getSuggestedWordsForTest() {
        // You may not use this method for anything else than debug
        return DebugFlags.DEBUG_ENABLED ? mInputLogic.mSuggestedWords : null;
    }

    // DO NOT USE THIS for any other purpose than testing. This is information private to LatinIME.
    @UsedForTesting
    void waitForLoadingDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        mDictionaryFacilitator.waitForLoadingDictionariesForTesting(timeout, unit);
    }

    // DO NOT USE THIS for any other purpose than testing. This can break the keyboard badly.
    @UsedForTesting
    void replaceDictionariesForTest(final Locale locale) {
        final SettingsValues settingsValues = mSettings.getCurrent();
        mDictionaryFacilitator.resetDictionaries(mInputMethodService, locale,
            settingsValues.mUseContactsDict, settingsValues.mUsePersonalizedDicts,
            false /* forceReloadMainDictionary */,
            settingsValues.mAccount, "", /* dictionaryNamePrefix */
            this /* DictionaryInitializationListener */);
    }

    // DO NOT USE THIS for any other purpose than testing.
    @UsedForTesting
    void clearPersonalizedDictionariesForTest() {
        mDictionaryFacilitator.clearUserHistoryDictionary(mInputMethodService);
    }

    public void dumpDictionaryForDebug(final String dictName) {
        if (!mDictionaryFacilitator.isActive()) {
            resetDictionaryFacilitatorIfNecessary();
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

    protected void dump(final FileDescriptor fd, final PrintWriter fout, final String[] args) {
        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        p.println("  VersionCode = " + ApplicationUtils.getVersionCode(mInputMethodService));
        p.println("  VersionName = " + ApplicationUtils.getVersionName(mInputMethodService));
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        final int keyboardMode = keyboard != null ? keyboard.mId.mMode : -1;
        p.println("  Keyboard mode = " + keyboardMode);
        final SettingsValues settingsValues = mSettings.getCurrent();
        p.println(settingsValues.dump());
        p.println(mDictionaryFacilitator.dump(mInputMethodService));
        // TODO: Dump all settings values
    }

    public boolean shouldSwitchToOtherInputMethods() {
        // TODO: Revisit here to reorganize the settings. Probably we can/should use different
        // strategy once the implementation of
        // {@link InputMethodManager#shouldOfferSwitchingToNextInputMethod} is defined well.
        final boolean fallbackValue = mSettings.getCurrent().mIncludesOtherImesInLanguageSwitchList;
        final IBinder token = mInputMethodService.getWindow().getWindow().getAttributes().token;
        if (token == null) {
            return fallbackValue;
        }
        return mRichImm.shouldOfferSwitchingToNextInputMethod(token, fallbackValue);
    }

    private void setNavigationBarVisibility(final boolean visible) {
        ((LatinIME)mInputMethodService).updateNavigationBarVisibility(visible);
    }

    public InputMethodService getInputMethodService() {
        return mInputMethodService;
    }

    public LanguageModelFacilitator getLanguageModelFacilitator() {
        return ((LatinIME)(mInputMethodService)).getLanguageModelFacilitator();
    }

    public Locale getLocale() {
        return mLocale;
    }

    public void onCodePointDeleted(String textBeforeCursor) {
        ((LatinIME)(mInputMethodService)).onEmojiDeleted(textBeforeCursor);
    }
}
