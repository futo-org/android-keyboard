/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Debug;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputConnection;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.compat.CompatUtils;
import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.compat.InputConnectionCompatUtils;
import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.compat.InputMethodServiceCompatWrapper;
import com.android.inputmethod.compat.InputTypeCompatUtils;
import com.android.inputmethod.compat.SuggestionSpanUtils;
import com.android.inputmethod.compat.VibratorCompatWrapper;
import com.android.inputmethod.deprecated.LanguageSwitcherProxy;
import com.android.inputmethod.deprecated.VoiceProxy;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.LatinKeyboardView;
import com.android.inputmethod.latin.suggestions.SuggestionsView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodServiceCompatWrapper implements KeyboardActionListener,
        SuggestionsView.Listener {
    private static final String TAG = LatinIME.class.getSimpleName();
    private static final boolean TRACE = false;
    private static boolean DEBUG;

    /**
     * The private IME option used to indicate that no microphone should be
     * shown for a given text field. For instance, this is specified by the
     * search dialog when the dialog is already showing a voice search button.
     *
     * @deprecated Use {@link LatinIME#IME_OPTION_NO_MICROPHONE} with package name prefixed.
     */
    @SuppressWarnings("dep-ann")
    public static final String IME_OPTION_NO_MICROPHONE_COMPAT = "nm";

    /**
     * The private IME option used to indicate that no microphone should be
     * shown for a given text field. For instance, this is specified by the
     * search dialog when the dialog is already showing a voice search button.
     */
    public static final String IME_OPTION_NO_MICROPHONE = "noMicrophoneKey";

    /**
     * The private IME option used to indicate that no settings key should be
     * shown for a given text field.
     */
    public static final String IME_OPTION_NO_SETTINGS_KEY = "noSettingsKey";

    /**
     * The private IME option used to indicate that the given text field needs
     * ASCII code points input.
     *
     * @deprecated Use {@link EditorInfo#IME_FLAG_FORCE_ASCII}.
     */
    @SuppressWarnings("dep-ann")
    public static final String IME_OPTION_FORCE_ASCII = "forceAscii";

    /**
     * The subtype extra value used to indicate that the subtype keyboard layout is capable for
     * typing ASCII characters.
     */
    public static final String SUBTYPE_EXTRA_VALUE_ASCII_CAPABLE = "AsciiCapable";

    /**
     * The subtype extra value used to indicate that the subtype keyboard layout supports touch
     * position correction.
     */
    public static final String SUBTYPE_EXTRA_VALUE_SUPPORT_TOUCH_POSITION_CORRECTION =
            "SupportTouchPositionCorrection";
    /**
     * The subtype extra value used to indicate that the subtype keyboard layout should be loaded
     * from the specified locale.
     */
    public static final String SUBTYPE_EXTRA_VALUE_KEYBOARD_LOCALE = "KeyboardLocale";

    private static final int EXTENDED_TOUCHABLE_REGION_HEIGHT = 100;

    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200;

    private static final int PENDING_IMS_CALLBACK_DURATION = 800;

    /**
     * The name of the scheme used by the Package Manager to warn of a new package installation,
     * replacement or removal.
     */
    private static final String SCHEME_PACKAGE = "package";

    // TODO: migrate this to SettingsValues
    private int mSuggestionVisibility;
    private static final int SUGGESTION_VISIBILILTY_SHOW_VALUE
            = R.string.prefs_suggestion_visibility_show_value;
    private static final int SUGGESTION_VISIBILILTY_SHOW_ONLY_PORTRAIT_VALUE
            = R.string.prefs_suggestion_visibility_show_only_portrait_value;
    private static final int SUGGESTION_VISIBILILTY_HIDE_VALUE
            = R.string.prefs_suggestion_visibility_hide_value;

    private static final int[] SUGGESTION_VISIBILITY_VALUE_ARRAY = new int[] {
        SUGGESTION_VISIBILILTY_SHOW_VALUE,
        SUGGESTION_VISIBILILTY_SHOW_ONLY_PORTRAIT_VALUE,
        SUGGESTION_VISIBILILTY_HIDE_VALUE
    };

    private static final int SPACE_STATE_NONE = 0;
    // Double space: the state where the user pressed space twice quickly, which LatinIME
    // resolved as period-space. Undoing this converts the period to a space.
    private static final int SPACE_STATE_DOUBLE = 1;
    // Swap punctuation: the state where a (weak or magic) space and a punctuation from the
    // suggestion strip have just been swapped. Undoing this swaps them back.
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

    private SettingsValues mSettingsValues;
    private InputAttributes mInputAttributes;

    private View mExtractArea;
    private View mKeyPreviewBackingView;
    private View mSuggestionsContainer;
    private SuggestionsView mSuggestionsView;
    /* package for tests */ Suggest mSuggest;
    private CompletionInfo[] mApplicationSpecifiedCompletions;

    private InputMethodManagerCompatWrapper mImm;
    private Resources mResources;
    private SharedPreferences mPrefs;
    private KeyboardSwitcher mKeyboardSwitcher;
    private SubtypeSwitcher mSubtypeSwitcher;
    private VoiceProxy mVoiceProxy;

    private UserDictionary mUserDictionary;
    private UserBigramDictionary mUserBigramDictionary;
    private UserUnigramDictionary mUserUnigramDictionary;
    private boolean mIsUserDictionaryAvailable;

    private LastComposedWord mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
    private WordComposer mWordComposer = new WordComposer();

    private int mCorrectionMode;

    // Keep track of the last selection range to decide if we need to show word alternatives
    private static final int NOT_A_CURSOR_POSITION = -1;
    private int mLastSelectionStart = NOT_A_CURSOR_POSITION;
    private int mLastSelectionEnd = NOT_A_CURSOR_POSITION;

    // Whether we are expecting an onUpdateSelection event to fire. If it does when we don't
    // "expect" it, it means the user actually moved the cursor.
    private boolean mExpectingUpdateSelection;
    private int mDeleteCount;
    private long mLastKeyTime;

    private AudioManager mAudioManager;
    private boolean mSilentModeOn; // System-wide current configuration

    private VibratorCompatWrapper mVibrator;

    // TODO: Move this flag to VoiceProxy
    private boolean mConfigurationChanging;

    // Member variables for remembering the current device orientation.
    private int mDisplayOrientation;

    // Object for reacting to adding/removing a dictionary pack.
    private BroadcastReceiver mDictionaryPackInstallReceiver =
            new DictionaryPackInstallBroadcastReceiver(this);

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private CharSequence mEnteredText;

    private final ComposingStateManager mComposingStateManager =
            ComposingStateManager.getInstance();

    public final UIHandler mHandler = new UIHandler(this);

    public static class UIHandler extends StaticInnerHandlerWrapper<LatinIME> {
        private static final int MSG_UPDATE_SUGGESTIONS = 0;
        private static final int MSG_UPDATE_SHIFT_STATE = 1;
        private static final int MSG_VOICE_RESULTS = 2;
        private static final int MSG_FADEOUT_LANGUAGE_ON_SPACEBAR = 3;
        private static final int MSG_DISMISS_LANGUAGE_ON_SPACEBAR = 4;
        private static final int MSG_SPACE_TYPED = 5;
        private static final int MSG_SET_BIGRAM_PREDICTIONS = 6;
        private static final int MSG_PENDING_IMS_CALLBACK = 7;

        private int mDelayBeforeFadeoutLanguageOnSpacebar;
        private int mDelayUpdateSuggestions;
        private int mDelayUpdateShiftState;
        private int mDurationOfFadeoutLanguageOnSpacebar;
        private float mFinalFadeoutFactorOfLanguageOnSpacebar;
        private long mDoubleSpacesTurnIntoPeriodTimeout;

        public UIHandler(LatinIME outerInstance) {
            super(outerInstance);
        }

        public void onCreate() {
            final Resources res = getOuterInstance().getResources();
            mDelayBeforeFadeoutLanguageOnSpacebar = res.getInteger(
                    R.integer.config_delay_before_fadeout_language_on_spacebar);
            mDelayUpdateSuggestions =
                    res.getInteger(R.integer.config_delay_update_suggestions);
            mDelayUpdateShiftState =
                    res.getInteger(R.integer.config_delay_update_shift_state);
            mDurationOfFadeoutLanguageOnSpacebar = res.getInteger(
                    R.integer.config_duration_of_fadeout_language_on_spacebar);
            mFinalFadeoutFactorOfLanguageOnSpacebar = res.getInteger(
                    R.integer.config_final_fadeout_percentage_of_language_on_spacebar) / 100.0f;
            mDoubleSpacesTurnIntoPeriodTimeout = res.getInteger(
                    R.integer.config_double_spaces_turn_into_period_timeout);
        }

        @Override
        public void handleMessage(Message msg) {
            final LatinIME latinIme = getOuterInstance();
            final KeyboardSwitcher switcher = latinIme.mKeyboardSwitcher;
            final LatinKeyboardView inputView = switcher.getKeyboardView();
            switch (msg.what) {
            case MSG_UPDATE_SUGGESTIONS:
                latinIme.updateSuggestions();
                break;
            case MSG_UPDATE_SHIFT_STATE:
                switcher.updateShiftState();
                break;
            case MSG_SET_BIGRAM_PREDICTIONS:
                latinIme.updateBigramPredictions();
                break;
            case MSG_VOICE_RESULTS:
                final Keyboard keyboard = switcher.getKeyboard();
                latinIme.mVoiceProxy.handleVoiceResults(latinIme.preferCapitalization()
                        || (keyboard != null && keyboard.isShiftedOrShiftLocked()));
                break;
            case MSG_FADEOUT_LANGUAGE_ON_SPACEBAR:
                setSpacebarTextFadeFactor(inputView,
                        (1.0f + mFinalFadeoutFactorOfLanguageOnSpacebar) / 2,
                        (Keyboard)msg.obj);
                sendMessageDelayed(obtainMessage(MSG_DISMISS_LANGUAGE_ON_SPACEBAR, msg.obj),
                        mDurationOfFadeoutLanguageOnSpacebar);
                break;
            case MSG_DISMISS_LANGUAGE_ON_SPACEBAR:
                setSpacebarTextFadeFactor(inputView, mFinalFadeoutFactorOfLanguageOnSpacebar,
                        (Keyboard)msg.obj);
                break;
            }
        }

        public void postUpdateSuggestions() {
            removeMessages(MSG_UPDATE_SUGGESTIONS);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTIONS), mDelayUpdateSuggestions);
        }

        public void cancelUpdateSuggestions() {
            removeMessages(MSG_UPDATE_SUGGESTIONS);
        }

        public boolean hasPendingUpdateSuggestions() {
            return hasMessages(MSG_UPDATE_SUGGESTIONS);
        }

        public void postUpdateShiftKeyState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SHIFT_STATE), mDelayUpdateShiftState);
        }

        public void cancelUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
        }

        public void postUpdateBigramPredictions() {
            removeMessages(MSG_SET_BIGRAM_PREDICTIONS);
            sendMessageDelayed(obtainMessage(MSG_SET_BIGRAM_PREDICTIONS), mDelayUpdateSuggestions);
        }

        public void cancelUpdateBigramPredictions() {
            removeMessages(MSG_SET_BIGRAM_PREDICTIONS);
        }

        public void updateVoiceResults() {
            sendMessage(obtainMessage(MSG_VOICE_RESULTS));
        }

        private static void setSpacebarTextFadeFactor(LatinKeyboardView inputView,
                float fadeFactor, Keyboard oldKeyboard) {
            if (inputView == null) return;
            final Keyboard keyboard = inputView.getKeyboard();
            if (keyboard == oldKeyboard) {
                inputView.updateSpacebar(fadeFactor,
                        SubtypeSwitcher.getInstance().needsToDisplayLanguage(
                                keyboard.mId.mLocale));
            }
        }

        public void startDisplayLanguageOnSpacebar(boolean localeChanged) {
            final LatinIME latinIme = getOuterInstance();
            removeMessages(MSG_FADEOUT_LANGUAGE_ON_SPACEBAR);
            removeMessages(MSG_DISMISS_LANGUAGE_ON_SPACEBAR);
            final LatinKeyboardView inputView = latinIme.mKeyboardSwitcher.getKeyboardView();
            if (inputView != null) {
                final Keyboard keyboard = latinIme.mKeyboardSwitcher.getKeyboard();
                // The language is always displayed when the delay is negative.
                final boolean needsToDisplayLanguage = localeChanged
                        || mDelayBeforeFadeoutLanguageOnSpacebar < 0;
                // The language is never displayed when the delay is zero.
                if (mDelayBeforeFadeoutLanguageOnSpacebar != 0) {
                    setSpacebarTextFadeFactor(inputView,
                            needsToDisplayLanguage ? 1.0f : mFinalFadeoutFactorOfLanguageOnSpacebar,
                                    keyboard);
                }
                // The fadeout animation will start when the delay is positive.
                if (localeChanged && mDelayBeforeFadeoutLanguageOnSpacebar > 0) {
                    sendMessageDelayed(obtainMessage(MSG_FADEOUT_LANGUAGE_ON_SPACEBAR, keyboard),
                            mDelayBeforeFadeoutLanguageOnSpacebar);
                }
            }
        }

        public void startDoubleSpacesTimer() {
            removeMessages(MSG_SPACE_TYPED);
            sendMessageDelayed(obtainMessage(MSG_SPACE_TYPED), mDoubleSpacesTurnIntoPeriodTimeout);
        }

        public void cancelDoubleSpacesTimer() {
            removeMessages(MSG_SPACE_TYPED);
        }

        public boolean isAcceptingDoubleSpaces() {
            return hasMessages(MSG_SPACE_TYPED);
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

        private void executePendingImsCallback(LatinIME latinIme, EditorInfo editorInfo,
                boolean restarting) {
            if (mHasPendingFinishInputView)
                latinIme.onFinishInputViewInternal(mHasPendingFinishInput);
            if (mHasPendingFinishInput)
                latinIme.onFinishInputInternal();
            if (mHasPendingStartInput)
                latinIme.onStartInputInternal(editorInfo, restarting);
            resetPendingImsCallback();
        }

        public void onStartInput(EditorInfo editorInfo, boolean restarting) {
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

        public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
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

        public void onFinishInputView(boolean finishingInput) {
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

    @Override
    public void onCreate() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs = prefs;
        LatinImeLogger.init(this, prefs);
        LanguageSwitcherProxy.init(this, prefs);
        InputMethodManagerCompatWrapper.init(this);
        SubtypeSwitcher.init(this);
        KeyboardSwitcher.init(this, prefs);
        AccessibilityUtils.init(this);

        super.onCreate();

        mImm = InputMethodManagerCompatWrapper.getInstance();
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mVibrator = VibratorCompatWrapper.getInstance(this);
        mHandler.onCreate();
        DEBUG = LatinImeLogger.sDBG;

        final Resources res = getResources();
        mResources = res;

        loadSettings();

        // TODO: remove the following when it's not needed by updateCorrectionMode() any more
        mInputAttributes = new InputAttributes(null, false /* isFullscreenMode */);
        Utils.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < Utils.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                initSuggest();
                tryGC = false;
            } catch (OutOfMemoryError e) {
                tryGC = Utils.GCUtils.getInstance().tryGCOrWait("InitSuggest", e);
            }
        }

        mDisplayOrientation = res.getConfiguration().orientation;

        // Register to receive ringer mode change and network state change.
        // Also receive installation and removal of a dictionary pack.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mReceiver, filter);
        mVoiceProxy = VoiceProxy.init(this, prefs, mHandler);

        final IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme(SCHEME_PACKAGE);
        registerReceiver(mDictionaryPackInstallReceiver, packageFilter);

        final IntentFilter newDictFilter = new IntentFilter();
        newDictFilter.addAction(
                DictionaryPackInstallBroadcastReceiver.NEW_DICTIONARY_INTENT_ACTION);
        registerReceiver(mDictionaryPackInstallReceiver, newDictFilter);
    }

    // Has to be package-visible for unit tests
    /* package */ void loadSettings() {
        if (null == mPrefs) mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (null == mSubtypeSwitcher) mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mSettingsValues = new SettingsValues(mPrefs, this, mSubtypeSwitcher.getInputLocaleStr());
        resetContactsDictionary(null == mSuggest ? null : mSuggest.getContactsDictionary());
    }

    private void initSuggest() {
        final String localeStr = mSubtypeSwitcher.getInputLocaleStr();
        final Locale keyboardLocale = LocaleUtils.constructLocaleFromString(localeStr);

        final Resources res = mResources;
        final Locale savedLocale = LocaleUtils.setSystemLocale(res, keyboardLocale);
        final ContactsDictionary oldContactsDictionary;
        if (mSuggest != null) {
            oldContactsDictionary = mSuggest.getContactsDictionary();
            mSuggest.close();
        } else {
            oldContactsDictionary = null;
        }

        int mainDicResId = Utils.getMainDictionaryResourceId(res);
        mSuggest = new Suggest(this, mainDicResId, keyboardLocale);
        if (mSettingsValues.mAutoCorrectEnabled) {
            mSuggest.setAutoCorrectionThreshold(mSettingsValues.mAutoCorrectionThreshold);
        }

        mUserDictionary = new UserDictionary(this, localeStr);
        mSuggest.setUserDictionary(mUserDictionary);
        mIsUserDictionaryAvailable = mUserDictionary.isEnabled();

        resetContactsDictionary(oldContactsDictionary);

        mUserUnigramDictionary
                = new UserUnigramDictionary(this, this, localeStr, Suggest.DIC_USER_UNIGRAM);
        mSuggest.setUserUnigramDictionary(mUserUnigramDictionary);

        mUserBigramDictionary
                = new UserBigramDictionary(this, this, localeStr, Suggest.DIC_USER_BIGRAM);
        mSuggest.setUserBigramDictionary(mUserBigramDictionary);

        updateCorrectionMode();

        LocaleUtils.setSystemLocale(res, savedLocale);
    }

    /**
     * Resets the contacts dictionary in mSuggest according to the user settings.
     *
     * This method takes an optional contacts dictionary to use. Since the contacts dictionary
     * does not depend on the locale, it can be reused across different instances of Suggest.
     * The dictionary will also be opened or closed as necessary depending on the settings.
     *
     * @param oldContactsDictionary an optional dictionary to use, or null
     */
    private void resetContactsDictionary(final ContactsDictionary oldContactsDictionary) {
        final boolean shouldSetDictionary = (null != mSuggest && mSettingsValues.mUseContactsDict);

        final ContactsDictionary dictionaryToUse;
        if (!shouldSetDictionary) {
            // Make sure the dictionary is closed. If it is already closed, this is a no-op,
            // so it's safe to call it anyways.
            if (null != oldContactsDictionary) oldContactsDictionary.close();
            dictionaryToUse = null;
        } else if (null != oldContactsDictionary) {
            // Make sure the old contacts dictionary is opened. If it is already open, this is a
            // no-op, so it's safe to call it anyways.
            oldContactsDictionary.reopen(this);
            dictionaryToUse = oldContactsDictionary;
        } else {
            dictionaryToUse = new ContactsDictionary(this, Suggest.DIC_CONTACTS);
        }

        if (null != mSuggest) {
            mSuggest.setContactsDictionary(dictionaryToUse);
        }
    }

    /* package private */ void resetSuggestMainDict() {
        final String localeStr = mSubtypeSwitcher.getInputLocaleStr();
        final Locale keyboardLocale = LocaleUtils.constructLocaleFromString(localeStr);
        int mainDicResId = Utils.getMainDictionaryResourceId(mResources);
        mSuggest.resetMainDict(this, mainDicResId, keyboardLocale);
    }

    @Override
    public void onDestroy() {
        if (mSuggest != null) {
            mSuggest.close();
            mSuggest = null;
        }
        unregisterReceiver(mReceiver);
        unregisterReceiver(mDictionaryPackInstallReceiver);
        mVoiceProxy.destroy();
        LatinImeLogger.commit();
        LatinImeLogger.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        mSubtypeSwitcher.onConfigurationChanged(conf);
        mComposingStateManager.onFinishComposingText();
        // If orientation changed while predicting, commit the change
        if (mDisplayOrientation != conf.orientation) {
            mDisplayOrientation = conf.orientation;
            mHandler.startOrientationChanging();
            final InputConnection ic = getCurrentInputConnection();
            commitTyped(ic);
            if (ic != null) ic.finishComposingText(); // For voice input
            if (isShowingOptionDialog())
                mOptionsDialog.dismiss();
        }

        mConfigurationChanging = true;
        super.onConfigurationChanged(conf);
        mVoiceProxy.onConfigurationChanged(conf);
        mConfigurationChanging = false;

        // This will work only when the subtype is not supported.
        LanguageSwitcherProxy.onConfigurationChanged(conf);
    }

    @Override
    public View onCreateInputView() {
        return mKeyboardSwitcher.onCreateInputView();
    }

    @Override
    public void setInputView(View view) {
        super.setInputView(view);
        mExtractArea = getWindow().getWindow().getDecorView()
                .findViewById(android.R.id.extractArea);
        mKeyPreviewBackingView = view.findViewById(R.id.key_preview_backing);
        mSuggestionsContainer = view.findViewById(R.id.suggestions_container);
        mSuggestionsView = (SuggestionsView) view.findViewById(R.id.suggestions_view);
        if (mSuggestionsView != null)
            mSuggestionsView.setListener(this, view);
        if (LatinImeLogger.sVISUALDEBUG) {
            mKeyPreviewBackingView.setBackgroundColor(0x10FF0000);
        }
    }

    @Override
    public void setCandidatesView(View view) {
        // To ensure that CandidatesView will never be set.
        return;
    }

    @Override
    public void onStartInput(EditorInfo editorInfo, boolean restarting) {
        mHandler.onStartInput(editorInfo, restarting);
    }

    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        mHandler.onStartInputView(editorInfo, restarting);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        mHandler.onFinishInputView(finishingInput);
    }

    @Override
    public void onFinishInput() {
        mHandler.onFinishInput();
    }

    private void onStartInputInternal(EditorInfo editorInfo, boolean restarting) {
        super.onStartInput(editorInfo, restarting);
    }

    private void onStartInputViewInternal(EditorInfo editorInfo, boolean restarting) {
        super.onStartInputView(editorInfo, restarting);
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        LatinKeyboardView inputView = switcher.getKeyboardView();

        if (DEBUG) {
            Log.d(TAG, "onStartInputView: editorInfo:" + ((editorInfo == null) ? "none"
                    : String.format("inputType=0x%08x imeOptions=0x%08x",
                            editorInfo.inputType, editorInfo.imeOptions)));
        }
        if (Utils.inPrivateImeOptions(null, IME_OPTION_NO_MICROPHONE_COMPAT, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: "
                    + editorInfo.privateImeOptions);
            Log.w(TAG, "Use " + getPackageName() + "." + IME_OPTION_NO_MICROPHONE + " instead");
        }
        if (Utils.inPrivateImeOptions(getPackageName(), IME_OPTION_FORCE_ASCII, editorInfo)) {
            Log.w(TAG, "Deprecated private IME option specified: "
                    + editorInfo.privateImeOptions);
            Log.w(TAG, "Use EditorInfo.IME_FLAG_FORCE_ASCII flag instead");
        }

        LatinImeLogger.onStartInputView(editorInfo);
        // In landscape mode, this method gets called without the input view being created.
        if (inputView == null) {
            return;
        }

        // Forward this event to the accessibility utilities, if enabled.
        final AccessibilityUtils accessUtils = AccessibilityUtils.getInstance();
        if (accessUtils.isTouchExplorationEnabled()) {
            accessUtils.onStartInputViewInternal(editorInfo, restarting);
        }

        mSubtypeSwitcher.updateParametersOnStartInputView();

        // Most such things we decide below in initializeInputAttributesAndGetMode, but we need to
        // know now whether this is a password text field, because we need to know now whether we
        // want to enable the voice button.
        final VoiceProxy voiceIme = mVoiceProxy;
        final int inputType = (editorInfo != null) ? editorInfo.inputType : 0;
        voiceIme.resetVoiceStates(InputTypeCompatUtils.isPasswordInputType(inputType)
                || InputTypeCompatUtils.isVisiblePasswordInputType(inputType));

        // The EditorInfo might have a flag that affects fullscreen mode.
        // Note: This call should be done by InputMethodService?
        updateFullscreenMode();
        mInputAttributes = new InputAttributes(editorInfo, isFullscreenMode());
        mApplicationSpecifiedCompletions = null;

        inputView.closing();
        mEnteredText = null;
        resetComposingState(true /* alsoResetLastComposedWord */);
        mDeleteCount = 0;
        mSpaceState = SPACE_STATE_NONE;

        loadSettings();
        updateCorrectionMode();
        updateSuggestionVisibility(mResources);

        if (mSuggest != null && mSettingsValues.mAutoCorrectEnabled) {
            mSuggest.setAutoCorrectionThreshold(mSettingsValues.mAutoCorrectionThreshold);
        }
        mVoiceProxy.loadSettings(editorInfo, mPrefs);
        // This will work only when the subtype is not supported.
        LanguageSwitcherProxy.loadSettings();

        if (mSubtypeSwitcher.isKeyboardMode()) {
            switcher.loadKeyboard(editorInfo, mSettingsValues);
        }

        if (mSuggestionsView != null)
            mSuggestionsView.clear();
        setSuggestionStripShownInternal(
                isSuggestionsStripVisible(), /* needsInputViewShown */ false);
        // Delay updating suggestions because keyboard input view may not be shown at this point.
        mHandler.postUpdateSuggestions();
        mHandler.cancelDoubleSpacesTimer();

        inputView.setKeyPreviewPopupEnabled(mSettingsValues.mKeyPreviewPopupOn,
                mSettingsValues.mKeyPreviewPopupDismissDelay);
        inputView.setProximityCorrectionEnabled(true);

        voiceIme.onStartInputView(inputView.getWindowToken());

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        KeyboardView inputView = mKeyboardSwitcher.getKeyboardView();
        if (inputView != null) inputView.closing();
    }

    private void onFinishInputInternal() {
        super.onFinishInput();

        LatinImeLogger.commit();

        mVoiceProxy.flushVoiceInputLogs(mConfigurationChanging);

        KeyboardView inputView = mKeyboardSwitcher.getKeyboardView();
        if (inputView != null) inputView.closing();
        if (mUserUnigramDictionary != null) mUserUnigramDictionary.flushPendingWrites();
        if (mUserBigramDictionary != null) mUserBigramDictionary.flushPendingWrites();
    }

    private void onFinishInputViewInternal(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        mKeyboardSwitcher.onFinishInputView();
        KeyboardView inputView = mKeyboardSwitcher.getKeyboardView();
        if (inputView != null) inputView.cancelAllMessages();
        // Remove pending messages related to update suggestions
        mHandler.cancelUpdateSuggestions();
    }

    @Override
    public void onUpdateExtractedText(int token, ExtractedText text) {
        super.onUpdateExtractedText(token, text);
        mVoiceProxy.showPunctuationHintIfNecessary();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);

        if (DEBUG) {
            Log.i(TAG, "onUpdateSelection: oss=" + oldSelStart
                    + ", ose=" + oldSelEnd
                    + ", lss=" + mLastSelectionStart
                    + ", lse=" + mLastSelectionEnd
                    + ", nss=" + newSelStart
                    + ", nse=" + newSelEnd
                    + ", cs=" + candidatesStart
                    + ", ce=" + candidatesEnd);
        }

        mVoiceProxy.setCursorAndSelection(newSelEnd, newSelStart);

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        final boolean selectionChanged = (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd) && mLastSelectionStart != newSelStart;
        final boolean candidatesCleared = candidatesStart == -1 && candidatesEnd == -1;
        if (!mExpectingUpdateSelection) {
            // TAKE CARE: there is a race condition when we enter this test even when the user
            // did not explicitly move the cursor. This happens when typing fast, where two keys
            // turn this flag on in succession and both onUpdateSelection() calls arrive after
            // the second one - the first call successfully avoids this test, but the second one
            // enters. For the moment we rely on candidatesCleared to further reduce the impact.

            // We set this to NONE because after a cursor move, we don't want the space
            // state-related special processing to kick in.
            mSpaceState = SPACE_STATE_NONE;

            if (((mWordComposer.isComposingWord())
                    || mVoiceProxy.isVoiceInputHighlighted())
                    && (selectionChanged || candidatesCleared)) {
                resetComposingState(true /* alsoResetLastComposedWord */);
                updateSuggestions();
                final InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.finishComposingText();
                }
                mComposingStateManager.onFinishComposingText();
                mVoiceProxy.setVoiceInputHighlighted(false);
            } else if (!mWordComposer.isComposingWord()) {
                // TODO: is the following reset still needed, given that we are not composing
                // a word?
                resetComposingState(true /* alsoResetLastComposedWord */);
                updateSuggestions();
            }
        }
        mExpectingUpdateSelection = false;
        mHandler.postUpdateShiftKeyState();
        // TODO: Decide to call restartSuggestionsOnWordBeforeCursorIfAtEndOfWord() or not
        // here. It would probably be too expensive to call directly here but we may want to post a
        // message to delay it. The point would be to unify behavior between backspace to the
        // end of a word and manually put the pointer at the end of the word.

        // Make a note of the cursor position
        mLastSelectionStart = newSelStart;
        mLastSelectionEnd = newSelEnd;
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
        if (isSuggestionsRequested()) return;

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
    public void onExtractedCursorMovement(int dx, int dy) {
        if (isSuggestionsRequested()) return;

        super.onExtractedCursorMovement(dx, dy);
    }

    @Override
    public void hideWindow() {
        LatinImeLogger.commit();
        mKeyboardSwitcher.onHideWindow();

        if (TRACE) Debug.stopMethodTracing();
        if (mOptionsDialog != null && mOptionsDialog.isShowing()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
        mVoiceProxy.hideVoiceWindow(mConfigurationChanging);
        super.hideWindow();
    }

    @Override
    public void onDisplayCompletions(CompletionInfo[] applicationSpecifiedCompletions) {
        if (DEBUG) {
            Log.i(TAG, "Received completions:");
            if (applicationSpecifiedCompletions != null) {
                for (int i = 0; i < applicationSpecifiedCompletions.length; i++) {
                    Log.i(TAG, "  #" + i + ": " + applicationSpecifiedCompletions[i]);
                }
            }
        }
        if (mInputAttributes.mApplicationSpecifiedCompletionOn) {
            mApplicationSpecifiedCompletions = applicationSpecifiedCompletions;
            if (applicationSpecifiedCompletions == null) {
                clearSuggestions();
                return;
            }

            SuggestedWords.Builder builder = new SuggestedWords.Builder()
                    .setApplicationSpecifiedCompletions(applicationSpecifiedCompletions)
                    .setTypedWordValid(false)
                    .setHasMinimalSuggestion(false);
            // When in fullscreen mode, show completions generated by the application
            setSuggestions(builder.build());
            // TODO: is this the right thing to do? What should we auto-correct to in
            // this case? This says to keep whatever the user typed.
            mWordComposer.setAutoCorrection(mWordComposer.getTypedWord());
            setSuggestionStripShown(true);
        }
    }

    private void setSuggestionStripShownInternal(boolean shown, boolean needsInputViewShown) {
        // TODO: Modify this if we support suggestions with hard keyboard
        if (onEvaluateInputViewShown() && mSuggestionsContainer != null) {
            final LatinKeyboardView keyboardView = mKeyboardSwitcher.getKeyboardView();
            final boolean inputViewShown = (keyboardView != null) ? keyboardView.isShown() : false;
            final boolean shouldShowSuggestions = shown
                    && (needsInputViewShown ? inputViewShown : true);
            if (isFullscreenMode()) {
                mSuggestionsContainer.setVisibility(
                        shouldShowSuggestions ? View.VISIBLE : View.GONE);
            } else {
                mSuggestionsContainer.setVisibility(
                        shouldShowSuggestions ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private void setSuggestionStripShown(boolean shown) {
        setSuggestionStripShownInternal(shown, /* needsInputViewShown */true);
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        final KeyboardView inputView = mKeyboardSwitcher.getKeyboardView();
        if (inputView == null || mSuggestionsContainer == null)
            return;
        // In fullscreen mode, the height of the extract area managed by InputMethodService should
        // be considered.
        // See {@link android.inputmethodservice.InputMethodService#onComputeInsets}.
        final int extractHeight = isFullscreenMode() ? mExtractArea.getHeight() : 0;
        final int backingHeight = (mKeyPreviewBackingView.getVisibility() == View.GONE) ? 0
                : mKeyPreviewBackingView.getHeight();
        final int suggestionsHeight = (mSuggestionsContainer.getVisibility() == View.GONE) ? 0
                : mSuggestionsContainer.getHeight();
        final int extraHeight = extractHeight + backingHeight + suggestionsHeight;
        int touchY = extraHeight;
        // Need to set touchable region only if input view is being shown
        final LatinKeyboardView keyboardView = mKeyboardSwitcher.getKeyboardView();
        if (keyboardView != null && keyboardView.isShown()) {
            if (mSuggestionsContainer.getVisibility() == View.VISIBLE) {
                touchY -= suggestionsHeight;
            }
            final int touchWidth = inputView.getWidth();
            final int touchHeight = inputView.getHeight() + extraHeight
                    // Extend touchable region below the keyboard.
                    + EXTENDED_TOUCHABLE_REGION_HEIGHT;
            if (DEBUG) {
                Log.d(TAG, "Touchable region: y=" + touchY + " width=" + touchWidth
                        + " height=" + touchHeight);
            }
            setTouchableRegionCompat(outInsets, 0, touchY, touchWidth, touchHeight);
        }
        outInsets.contentTopInsets = touchY;
        outInsets.visibleTopInsets = touchY;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // Reread resource value here, because this method is called by framework anytime as needed.
        final boolean isFullscreenModeAllowed =
                mSettingsValues.isFullscreenModeAllowed(getResources());
        return super.onEvaluateFullscreenMode() && isFullscreenModeAllowed;
    }

    @Override
    public void updateFullscreenMode() {
        super.updateFullscreenMode();

        if (mKeyPreviewBackingView == null) return;
        // In fullscreen mode, no need to have extra space to show the key preview.
        // If not, we should have extra space above the keyboard to show the key preview.
        mKeyPreviewBackingView.setVisibility(isFullscreenMode() ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            if (event.getRepeatCount() == 0) {
                if (mSuggestionsView != null && mSuggestionsView.handleBack()) {
                    return true;
                }
                final LatinKeyboardView keyboardView = mKeyboardSwitcher.getKeyboardView();
                if (keyboardView != null && keyboardView.handleBack()) {
                    return true;
                }
            }
            break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_DOWN:
        case KeyEvent.KEYCODE_DPAD_UP:
        case KeyEvent.KEYCODE_DPAD_LEFT:
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            final LatinKeyboardView keyboardView = mKeyboardSwitcher.getKeyboardView();
            final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
            // Enable shift key and DPAD to do selections
            if ((keyboardView != null && keyboardView.isShown())
                    && (keyboard != null && keyboard.isShiftedOrShiftLocked())) {
                KeyEvent newEvent = new KeyEvent(event.getDownTime(), event.getEventTime(),
                        event.getAction(), event.getKeyCode(), event.getRepeatCount(),
                        event.getDeviceId(), event.getScanCode(),
                        KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON);
                final InputConnection ic = getCurrentInputConnection();
                if (ic != null)
                    ic.sendKeyEvent(newEvent);
                return true;
            }
            break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void resetComposingState(final boolean alsoResetLastComposedWord) {
        mWordComposer.reset();
        if (alsoResetLastComposedWord)
            mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
    }

    public void commitTyped(final InputConnection ic) {
        if (!mWordComposer.isComposingWord()) return;
        final CharSequence typedWord = mWordComposer.getTypedWord();
        mLastComposedWord = mWordComposer.commitWord(LastComposedWord.COMMIT_TYPE_USER_TYPED_WORD);
        if (typedWord.length() > 0) {
            if (ic != null) {
                ic.commitText(typedWord, 1);
            }
            addToUserUnigramAndBigramDictionaries(typedWord,
                    UserUnigramDictionary.FREQUENCY_FOR_TYPED);
        }
        updateSuggestions();
    }

    public boolean getCurrentAutoCapsState() {
        final InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (mSettingsValues.mAutoCap && ic != null && ei != null
                && ei.inputType != InputType.TYPE_NULL) {
            return ic.getCursorCapsMode(ei.inputType) != 0;
        }
        return false;
    }

    // "ic" may be null
    private void swapSwapperAndSpaceWhileInBatchEdit(final InputConnection ic) {
        if (null == ic) return;
        CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
        // It is guaranteed lastTwo.charAt(1) is a swapper - else this method is not called.
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == Keyboard.CODE_SPACE) {
            ic.deleteSurroundingText(2, 0);
            ic.commitText(lastTwo.charAt(1) + " ", 1);
            mKeyboardSwitcher.updateShiftState();
        }
    }

    private boolean maybeDoubleSpaceWhileInBatchEdit(final InputConnection ic) {
        if (mCorrectionMode == Suggest.CORRECTION_NONE) return false;
        if (ic == null) return false;
        final CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && Utils.canBeFollowedByPeriod(lastThree.charAt(0))
                && lastThree.charAt(1) == Keyboard.CODE_SPACE
                && lastThree.charAt(2) == Keyboard.CODE_SPACE
                && mHandler.isAcceptingDoubleSpaces()) {
            mHandler.cancelDoubleSpacesTimer();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(". ", 1);
            mKeyboardSwitcher.updateShiftState();
            return true;
        }
        return false;
    }

    // "ic" may be null
    private static void removeTrailingSpaceWhileInBatchEdit(final InputConnection ic) {
        if (ic == null) return;
        final CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == Keyboard.CODE_SPACE) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    @Override
    public boolean addWordToDictionary(String word) {
        mUserDictionary.addWord(word, 128);
        // Suggestion strip should be updated after the operation of adding word to the
        // user dictionary
        mHandler.postUpdateSuggestions();
        return true;
    }

    private static boolean isAlphabet(int code) {
        return Character.isLetter(code);
    }

    private void onSettingsKeyPressed() {
        if (isShowingOptionDialog()) return;
        if (InputMethodServiceCompatWrapper.CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED) {
            showSubtypeSelectorAndSettings();
        } else if (Utils.hasMultipleEnabledIMEsOrSubtypes(false /* exclude aux subtypes */)) {
            showOptionsMenu();
        } else {
            launchSettings();
        }
    }

    // Virtual codes representing custom requests.  These are used in onCustomRequest() below.
    public static final int CODE_SHOW_INPUT_METHOD_PICKER = 1;
    public static final int CODE_HAPTIC_AND_AUDIO_FEEDBACK = 2;

    @Override
    public boolean onCustomRequest(int requestCode) {
        if (isShowingOptionDialog()) return false;
        switch (requestCode) {
        case CODE_SHOW_INPUT_METHOD_PICKER:
            if (Utils.hasMultipleEnabledIMEsOrSubtypes(true /* include aux subtypes */)) {
                mImm.showInputMethodPicker();
                return true;
            }
            return false;
        case CODE_HAPTIC_AND_AUDIO_FEEDBACK:
            hapticAndAudioFeedback(Keyboard.CODE_UNSPECIFIED);
            return true;
        }
        return false;
    }

    private boolean isShowingOptionDialog() {
        return mOptionsDialog != null && mOptionsDialog.isShowing();
    }

    private void insertPunctuationFromSuggestionStrip(final int code) {
        onCodeInput(code, new int[] { code },
                KeyboardActionListener.SUGGESTION_STRIP_COORDINATE,
                KeyboardActionListener.SUGGESTION_STRIP_COORDINATE);
    }

    // Implementation of {@link KeyboardActionListener}.
    @Override
    public void onCodeInput(int primaryCode, int[] keyCodes, int x, int y) {
        final long when = SystemClock.uptimeMillis();
        if (primaryCode != Keyboard.CODE_DELETE || when > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        // The space state depends only on the last character pressed and its own previous
        // state. Here, we revert the space state to neutral if the key is actually modifying
        // the input contents (any non-shift key), which is what we should do for
        // all inputs that do not result in a special state. Each character handling is then
        // free to override the state as they see fit.
        final int spaceState = mSpaceState;

        // TODO: Consolidate the double space timer, mLastKeyTime, and the space state.
        if (primaryCode != Keyboard.CODE_SPACE) {
            mHandler.cancelDoubleSpacesTimer();
        }

        boolean didAutoCorrect = false;
        switch (primaryCode) {
        case Keyboard.CODE_DELETE:
            mSpaceState = SPACE_STATE_NONE;
            handleBackspace(spaceState);
            mDeleteCount++;
            mExpectingUpdateSelection = true;
            LatinImeLogger.logOnDelete();
            break;
        case Keyboard.CODE_SHIFT:
        case Keyboard.CODE_SWITCH_ALPHA_SYMBOL:
            // Shift and symbol key is handled in onPressKey() and onReleaseKey().
            break;
        case Keyboard.CODE_SETTINGS:
            onSettingsKeyPressed();
            break;
        case Keyboard.CODE_SHORTCUT:
            mSubtypeSwitcher.switchToShortcutIME();
            break;
        case Keyboard.CODE_TAB:
            handleTab();
            // There are two cases for tab. Either we send a "next" event, that may change the
            // focus but will never move the cursor. Or, we send a real tab keycode, which some
            // applications may accept or ignore, and we don't know whether this will move the
            // cursor or not. So actually, we don't really know.
            // So to go with the safer option, we'd rather behave as if the user moved the
            // cursor when they didn't than the opposite. We also expect that most applications
            // will actually use tab only for focus movement.
            // To sum it up: do not update mExpectingUpdateSelection here.
            break;
        default:
            mSpaceState = SPACE_STATE_NONE;
            if (mSettingsValues.isWordSeparator(primaryCode)) {
                didAutoCorrect = handleSeparator(primaryCode, x, y, spaceState);
            } else {
                handleCharacter(primaryCode, keyCodes, x, y, spaceState);
            }
            mExpectingUpdateSelection = true;
            break;
        }
        switcher.onCodeInput(primaryCode);
        // Reset after any single keystroke
        if (!didAutoCorrect)
            mLastComposedWord.deactivate();
        mEnteredText = null;
    }

    @Override
    public void onTextInput(CharSequence text) {
        mVoiceProxy.commitVoiceInput();
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        commitTyped(ic);
        text = specificTldProcessingOnTextInput(ic, text);
        if (SPACE_STATE_PHANTOM == mSpaceState) {
            sendKeyChar((char)Keyboard.CODE_SPACE);
        }
        ic.commitText(text, 1);
        ic.endBatchEdit();
        mKeyboardSwitcher.updateShiftState();
        mKeyboardSwitcher.onCodeInput(Keyboard.CODE_OUTPUT_TEXT);
        mSpaceState = SPACE_STATE_NONE;
        mEnteredText = text;
        resetComposingState(true /* alsoResetLastComposedWord */);
    }

    // ic may not be null
    private CharSequence specificTldProcessingOnTextInput(final InputConnection ic,
            final CharSequence text) {
        if (text.length() <= 1 || text.charAt(0) != Keyboard.CODE_PERIOD
                || !Character.isLetter(text.charAt(1))) {
            // Not a tld: do nothing.
            return text;
        }
        // We have a TLD (or something that looks like this): make sure we don't add
        // a space even if currently in phantom mode.
        mSpaceState = SPACE_STATE_NONE;
        final CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == Keyboard.CODE_PERIOD) {
            return text.subSequence(1, text.length());
        } else {
            return text;
        }
    }

    @Override
    public void onCancelInput() {
        // User released a finger outside any key
        mKeyboardSwitcher.onCancelInput();
    }

    private void handleBackspace(final int spaceState) {
        if (mVoiceProxy.logAndRevertVoiceInput()) return;
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        handleBackspaceWhileInBatchEdit(spaceState, ic);
        ic.endBatchEdit();
    }

    // "ic" may not be null.
    private void handleBackspaceWhileInBatchEdit(final int spaceState, final InputConnection ic) {
        mVoiceProxy.handleBackspace();

        // In many cases, we may have to put the keyboard in auto-shift state again.
        mHandler.postUpdateShiftKeyState();

        if (mEnteredText != null && sameAsTextBeforeCursor(ic, mEnteredText)) {
            // Cancel multi-character input: remove the text we just entered.
            // This is triggered on backspace after a key that inputs multiple characters,
            // like the smiley key or the .com key.
            ic.deleteSurroundingText(mEnteredText.length(), 0);
            // If we have mEnteredText, then we know that mHasUncommittedTypedChars == false.
            // In addition we know that spaceState is false, and that we should not be
            // reverting any autocorrect at this point. So we can safely return.
            return;
        }

        if (mWordComposer.isComposingWord()) {
            final int length = mWordComposer.size();
            if (length > 0) {
                mWordComposer.deleteLast();
                ic.setComposingText(getTextWithUnderline(mWordComposer.getTypedWord()), 1);
                // If we have deleted the last remaining character of a word, then we are not
                // isComposingWord() any more.
                if (!mWordComposer.isComposingWord()) {
                    // Not composing word any more, so we can show bigrams.
                    mHandler.postUpdateBigramPredictions();
                } else {
                    // Still composing a word, so we still have letters to deduce a suggestion from.
                    mHandler.postUpdateSuggestions();
                }
            } else {
                ic.deleteSurroundingText(1, 0);
            }
        } else {
            // We should be very careful about auto-correction cancellation and suggestion
            // resuming here. The behavior needs to be different according to text field types,
            // and it would be much clearer to test for them explicitly here rather than
            // relying on implicit values like "whether the suggestion strip is displayed".
            if (mLastComposedWord.canCancelAutoCorrect()) {
                Utils.Stats.onAutoCorrectionCancellation();
                cancelAutoCorrect(ic);
                return;
            }

            if (SPACE_STATE_DOUBLE == spaceState) {
                if (revertDoubleSpaceWhileInBatchEdit(ic)) {
                    // No need to reset mSpaceState, it has already be done (that's why we
                    // receive it as a parameter)
                    return;
                }
            } else if (SPACE_STATE_SWAP_PUNCTUATION == spaceState) {
                if (revertSwapPunctuation(ic)) {
                    // Likewise
                    return;
                }
            }

            // See the comment above: must be careful about resuming auto-suggestion.
            if (mSuggestionsView != null && mSuggestionsView.dismissAddToDictionaryHint()) {
                // Go back to the suggestion mode if the user canceled the
                // "Touch again to save".
                // NOTE: In general, we don't revert the word when backspacing
                // from a manual suggestion pick.  We deliberately chose a
                // different behavior only in the case of picking the first
                // suggestion (typed word).  It's intentional to have made this
                // inconsistent with backspacing after selecting other suggestions.
                restartSuggestionsOnManuallyPickedTypedWord(ic);
            } else {
                // Here we must check whether there is a selection. If so we should remove the
                // selected text, otherwise we should just delete the character before the cursor.
                if (mLastSelectionStart != mLastSelectionEnd) {
                    final int lengthToDelete = mLastSelectionEnd - mLastSelectionStart;
                    ic.setSelection(mLastSelectionEnd, mLastSelectionEnd);
                    ic.deleteSurroundingText(lengthToDelete, 0);
                } else {
                    if (NOT_A_CURSOR_POSITION == mLastSelectionEnd) {
                        // We don't know whether there is a selection or not. We just send a false
                        // hardware key event and let TextView sort it out for us. The problem
                        // here is, this is asynchronous with respect to the input connection
                        // batch edit, so it may flicker. But this only ever happens if backspace
                        // is pressed just after the IME is invoked, and then again only once.
                        // TODO: add an API call that gets the selection indices. This is available
                        // to the IME in the general case via onUpdateSelection anyway, and would
                        // allow us to remove this race condition.
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                    } else {
                        ic.deleteSurroundingText(1, 0);
                    }
                    if (mDeleteCount > DELETE_ACCELERATE_AT) {
                        ic.deleteSurroundingText(1, 0);
                    }
                }
                if (isSuggestionsRequested()) {
                    restartSuggestionsOnWordBeforeCursorIfAtEndOfWord(ic);
                }
            }
        }
    }

    private void handleTab() {
        final int imeOptions = getCurrentInputEditorInfo().imeOptions;
        if (!EditorInfoCompatUtils.hasFlagNavigateNext(imeOptions)
                && !EditorInfoCompatUtils.hasFlagNavigatePrevious(imeOptions)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB);
            return;
        }

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;

        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        // True if keyboard is in either shift chording or manual shifted state.
        final boolean isManualShifted = (keyboard != null  && keyboard.isManualShifted());
        if (EditorInfoCompatUtils.hasFlagNavigateNext(imeOptions) && !isManualShifted) {
            EditorInfoCompatUtils.performEditorActionNext(ic);
        } else if (EditorInfoCompatUtils.hasFlagNavigatePrevious(imeOptions) && isManualShifted) {
            EditorInfoCompatUtils.performEditorActionPrevious(ic);
        }
    }

    private void handleCharacter(final int primaryCode, final int[] keyCodes, final int x,
            final int y, final int spaceState) {
        mVoiceProxy.handleCharacter();
        final InputConnection ic = getCurrentInputConnection();
        if (null != ic) ic.beginBatchEdit();
        // TODO: if ic is null, does it make any sense to call this?
        handleCharacterWhileInBatchEdit(primaryCode, keyCodes, x, y, spaceState, ic);
        if (null != ic) ic.endBatchEdit();
    }

    // "ic" may be null without this crashing, but the behavior will be really strange
    private void handleCharacterWhileInBatchEdit(final int primaryCode, final int[] keyCodes,
            final int x, final int y, final int spaceState, final InputConnection ic) {
        boolean isComposingWord = mWordComposer.isComposingWord();
        int code = primaryCode;

        if (SPACE_STATE_PHANTOM == spaceState &&
                !mSettingsValues.isSymbolExcludedFromWordSeparators(primaryCode)) {
            if (isComposingWord) {
                // Sanity check
                throw new RuntimeException("Should not be composing here");
            }
            sendKeyChar((char)Keyboard.CODE_SPACE);
        }

        if ((isAlphabet(code) || mSettingsValues.isSymbolExcludedFromWordSeparators(code))
                && isSuggestionsRequested() && !isCursorTouchingWord()) {
            if (!isComposingWord) {
                // Reset entirely the composing state anyway, then start composing a new word unless
                // the character is a single quote. The idea here is, single quote is not a
                // separator and it should be treated as a normal character, except in the first
                // position where it should not start composing a word.
                isComposingWord = (Keyboard.CODE_SINGLE_QUOTE != code);
                // Here we don't need to reset the last composed word. It will be reset
                // when we commit this one, if we ever do; if on the other hand we backspace
                // it entirely and resume suggestions on the previous word, we'd like to still
                // have touch coordinates for it.
                resetComposingState(false /* alsoResetLastComposedWord */);
                clearSuggestions();
                mComposingStateManager.onFinishComposingText();
            }
        }
        if (isComposingWord) {
            mWordComposer.add(code, keyCodes, x, y);
            if (ic != null) {
                // If it's the first letter, make note of auto-caps state
                if (mWordComposer.size() == 1) {
                    mWordComposer.setAutoCapitalized(getCurrentAutoCapsState());
                    mComposingStateManager.onStartComposingText();
                }
                ic.setComposingText(getTextWithUnderline(mWordComposer.getTypedWord()), 1);
            }
            mHandler.postUpdateSuggestions();
        } else {
            sendKeyChar((char)code);
        }

        Utils.Stats.onNonSeparator((char)code, x, y);
    }

    // Returns true if we did an autocorrection, false otherwise.
    private boolean handleSeparator(final int primaryCode, final int x, final int y,
            final int spaceState) {
        mVoiceProxy.handleSeparator();
        mComposingStateManager.onFinishComposingText();

        // Should dismiss the "Touch again to save" message when handling separator
        if (mSuggestionsView != null && mSuggestionsView.dismissAddToDictionaryHint()) {
            mHandler.cancelUpdateBigramPredictions();
            mHandler.postUpdateSuggestions();
        }

        boolean didAutoCorrect = false;
        // Handle separator
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
        }
        if (mWordComposer.isComposingWord()) {
            // In certain languages where single quote is a separator, it's better
            // not to auto correct, but accept the typed word. For instance,
            // in Italian dov' should not be expanded to dove' because the elision
            // requires the last vowel to be removed.
            final boolean shouldAutoCorrect = mSettingsValues.mAutoCorrectEnabled
                    && !mInputAttributes.mInputTypeNoAutoCorrect;
            if (shouldAutoCorrect && primaryCode != Keyboard.CODE_SINGLE_QUOTE) {
                commitCurrentAutoCorrection(primaryCode, ic);
                didAutoCorrect = true;
            } else {
                commitTyped(ic);
            }
        }

        final boolean swapWeakSpace;
        if (Keyboard.CODE_ENTER == primaryCode && SPACE_STATE_SWAP_PUNCTUATION == spaceState) {
            removeTrailingSpaceWhileInBatchEdit(ic);
            swapWeakSpace = false;
        } else if ((SPACE_STATE_WEAK == spaceState || SPACE_STATE_SWAP_PUNCTUATION == spaceState)
                && KeyboardActionListener.SUGGESTION_STRIP_COORDINATE == x) {
            if (mSettingsValues.isMagicSpaceSwapper(primaryCode)) {
                swapWeakSpace = true;
            } else {
                swapWeakSpace = false;
                if (mSettingsValues.isMagicSpaceStripper(primaryCode)) {
                    removeTrailingSpaceWhileInBatchEdit(ic);
                }
            }
        } else {
            swapWeakSpace = false;
        }

        // TODO: rethink interactions of sendKeyChar, commitText("\n") and actions. sendKeyChar
        // with a CODE_ENTER parameter will have the default InputMethodService implementation
        // possibly redirect on the keyboard action. That may be the right thing to do, but
        // on Shift+Enter, it's generally not, so we may want to do the redirection right here.
        sendKeyChar((char)primaryCode);

        if (Keyboard.CODE_SPACE == primaryCode) {
            if (isSuggestionsRequested()) {
                if (maybeDoubleSpaceWhileInBatchEdit(ic)) {
                    mSpaceState = SPACE_STATE_DOUBLE;
                } else if (!isShowingPunctuationList()) {
                    mSpaceState = SPACE_STATE_WEAK;
                }
            }

            mHandler.startDoubleSpacesTimer();
            if (!isCursorTouchingWord()) {
                mHandler.cancelUpdateSuggestions();
                mHandler.postUpdateBigramPredictions();
            }
        } else {
            if (swapWeakSpace) {
                swapSwapperAndSpaceWhileInBatchEdit(ic);
                mSpaceState = SPACE_STATE_WEAK;
            } else if (SPACE_STATE_PHANTOM == spaceState) {
                // If we are in phantom space state, and the user presses a separator, we want to
                // stay in phantom space state so that the next keypress has a chance to add the
                // space. For example, if I type "Good dat", pick "day" from the suggestion strip
                // then insert a comma and go on to typing the next word, I want the space to be
                // inserted automatically before the next word, the same way it is when I don't
                // input the comma.
                mSpaceState = SPACE_STATE_PHANTOM;
            }

            // Set punctuation right away. onUpdateSelection will fire but tests whether it is
            // already displayed or not, so it's okay.
            setPunctuationSuggestions();
        }

        Utils.Stats.onSeparator((char)primaryCode, x, y);

        if (ic != null) {
            ic.endBatchEdit();
        }
        return didAutoCorrect;
    }

    private CharSequence getTextWithUnderline(final CharSequence text) {
        return mComposingStateManager.isAutoCorrectionIndicatorOn()
                ? SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(this, text)
                : text;
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        mVoiceProxy.handleClose();
        requestHideSelf(0);
        LatinKeyboardView inputView = mKeyboardSwitcher.getKeyboardView();
        if (inputView != null)
            inputView.closing();
    }

    public boolean isSuggestionsRequested() {
        return mInputAttributes.mIsSettingsSuggestionStripOn
                && (mCorrectionMode > 0 || isShowingSuggestionsStrip());
    }

    public boolean isShowingPunctuationList() {
        if (mSuggestionsView == null) return false;
        return mSettingsValues.mSuggestPuncList == mSuggestionsView.getSuggestions();
    }

    public boolean isShowingSuggestionsStrip() {
        return (mSuggestionVisibility == SUGGESTION_VISIBILILTY_SHOW_VALUE)
                || (mSuggestionVisibility == SUGGESTION_VISIBILILTY_SHOW_ONLY_PORTRAIT_VALUE
                        && mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT);
    }

    public boolean isSuggestionsStripVisible() {
        if (mSuggestionsView == null)
            return false;
        if (mSuggestionsView.isShowingAddToDictionaryHint())
            return true;
        if (!isShowingSuggestionsStrip())
            return false;
        if (mInputAttributes.mApplicationSpecifiedCompletionOn)
            return true;
        return isSuggestionsRequested();
    }

    public void switchToKeyboardView() {
        if (DEBUG) {
            Log.d(TAG, "Switch to keyboard view.");
        }
        View v = mKeyboardSwitcher.getKeyboardView();
        if (v != null) {
            // Confirms that the keyboard view doesn't have parent view.
            ViewParent p = v.getParent();
            if (p != null && p instanceof ViewGroup) {
                ((ViewGroup) p).removeView(v);
            }
            setInputView(v);
        }
        setSuggestionStripShown(isSuggestionsStripVisible());
        updateInputViewShown();
        mHandler.postUpdateSuggestions();
    }

    public void clearSuggestions() {
        setSuggestions(SuggestedWords.EMPTY);
    }

    public void setSuggestions(SuggestedWords words) {
        if (mSuggestionsView != null) {
            mSuggestionsView.setSuggestions(words);
            mKeyboardSwitcher.onAutoCorrectionStateChanged(
                    words.hasWordAboveAutoCorrectionScoreThreshold());
        }

        // Put a blue underline to a word in TextView which will be auto-corrected.
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            final boolean oldAutoCorrectionIndicator =
                    mComposingStateManager.isAutoCorrectionIndicatorOn();
            final boolean newAutoCorrectionIndicator = Utils.willAutoCorrect(words);
            if (oldAutoCorrectionIndicator != newAutoCorrectionIndicator) {
                mComposingStateManager.setAutoCorrectionIndicatorOn(newAutoCorrectionIndicator);
                if (DEBUG) {
                    Log.d(TAG, "Flip the indicator. " + oldAutoCorrectionIndicator
                            + " -> " + newAutoCorrectionIndicator);
                    if (mComposingStateManager.isComposing() && newAutoCorrectionIndicator
                            != mComposingStateManager.isAutoCorrectionIndicatorOn()) {
                        throw new RuntimeException("Couldn't flip the indicator!");
                    }
                }
                final CharSequence textWithUnderline =
                        getTextWithUnderline(mWordComposer.getTypedWord());
                if (!TextUtils.isEmpty(textWithUnderline)) {
                    ic.setComposingText(textWithUnderline, 1);
                }
            }
        }
    }

    public void updateSuggestions() {
        // Check if we have a suggestion engine attached.
        if ((mSuggest == null || !isSuggestionsRequested())
                && !mVoiceProxy.isVoiceInputHighlighted()) {
            if (mWordComposer.isComposingWord()) {
                Log.w(TAG, "Called updateSuggestions but suggestions were not requested!");
                mWordComposer.setAutoCorrection(mWordComposer.getTypedWord());
            }
            return;
        }

        mHandler.cancelUpdateSuggestions();
        mHandler.cancelUpdateBigramPredictions();

        if (!mWordComposer.isComposingWord()) {
            setPunctuationSuggestions();
            return;
        }

        // TODO: May need a better way of retrieving previous word
        final InputConnection ic = getCurrentInputConnection();
        final CharSequence prevWord;
        if (null == ic) {
            prevWord = null;
        } else {
            prevWord = EditingUtils.getPreviousWord(ic, mSettingsValues.mWordSeparators);
        }
        // getSuggestedWordBuilder handles gracefully a null value of prevWord
        final SuggestedWords.Builder builder = mSuggest.getSuggestedWordBuilder(mWordComposer,
                prevWord, mKeyboardSwitcher.getKeyboard().getProximityInfo(), mCorrectionMode);

        boolean autoCorrectionAvailable = !mInputAttributes.mInputTypeNoAutoCorrect
                && mSuggest.hasAutoCorrection();
        final CharSequence typedWord = mWordComposer.getTypedWord();
        // Here, we want to promote a whitelisted word if exists.
        // TODO: Change this scheme - a boolean is not enough. A whitelisted word may be "valid"
        // but still autocorrected from - in the case the whitelist only capitalizes the word.
        // The whitelist should be case-insensitive, so it's not possible to be consistent with
        // a boolean flag. Right now this is handled with a slight hack in
        // WhitelistDictionary#shouldForciblyAutoCorrectFrom.
        final int quotesCount = mWordComposer.trailingSingleQuotesCount();
        final boolean allowsToBeAutoCorrected = AutoCorrection.allowsToBeAutoCorrected(
                mSuggest.getUnigramDictionaries(),
                // If the typed string ends with a single quote, for dictionary lookup purposes
                // we behave as if the single quote was not here. Here, we are looking up the
                // typed string in the dictionary (to avoid autocorrecting from an existing
                // word, so for consistency this lookup should be made WITHOUT the trailing
                // single quote.
                quotesCount > 0
                        ? typedWord.subSequence(0, typedWord.length() - quotesCount) : typedWord,
                preferCapitalization());
        if (mCorrectionMode == Suggest.CORRECTION_FULL
                || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM) {
            autoCorrectionAvailable |= (!allowsToBeAutoCorrected);
        }
        // Don't auto-correct words with multiple capital letter
        autoCorrectionAvailable &= !mWordComposer.isMostlyCaps();

        // Basically, we update the suggestion strip only when suggestion count > 1.  However,
        // there is an exception: We update the suggestion strip whenever typed word's length
        // is 1 or typed word is found in dictionary, regardless of suggestion count.  Actually,
        // in most cases, suggestion count is 1 when typed word's length is 1, but we do always
        // need to clear the previous state when the user starts typing a word (i.e. typed word's
        // length == 1).
        if (typedWord != null) {
            if (builder.size() > 1 || typedWord.length() == 1 || (!allowsToBeAutoCorrected)
                    || mSuggestionsView.isShowingAddToDictionaryHint()) {
                builder.setTypedWordValid(!allowsToBeAutoCorrected).setHasMinimalSuggestion(
                        autoCorrectionAvailable);
            } else {
                SuggestedWords previousSuggestions = mSuggestionsView.getSuggestions();
                if (previousSuggestions == mSettingsValues.mSuggestPuncList) {
                    if (builder.size() == 0) {
                        return;
                    }
                    previousSuggestions = SuggestedWords.EMPTY;
                }
                builder.addTypedWordAndPreviousSuggestions(typedWord, previousSuggestions);
            }
        }
        showSuggestions(builder.build(), typedWord);
    }

    public void showSuggestions(SuggestedWords suggestedWords, CharSequence typedWord) {
        final boolean shouldBlockAutoCorrectionBySafetyNet =
                Utils.shouldBlockAutoCorrectionBySafetyNet(suggestedWords, mSuggest);
        if (shouldBlockAutoCorrectionBySafetyNet) {
            suggestedWords.setShouldBlockAutoCorrection();
        }
        setSuggestions(suggestedWords);
        if (suggestedWords.size() > 0) {
            if (shouldBlockAutoCorrectionBySafetyNet) {
                mWordComposer.setAutoCorrection(typedWord);
            } else if (suggestedWords.hasAutoCorrectionWord()) {
                mWordComposer.setAutoCorrection(suggestedWords.getWord(1));
            } else {
                mWordComposer.setAutoCorrection(typedWord);
            }
        } else {
            // TODO: replace with mWordComposer.deleteAutoCorrection()?
            mWordComposer.setAutoCorrection(null);
        }
        setSuggestionStripShown(isSuggestionsStripVisible());
    }

    private void commitCurrentAutoCorrection(final int separatorCodePoint,
            final InputConnection ic) {
        // Complete any pending suggestions query first
        if (mHandler.hasPendingUpdateSuggestions()) {
            mHandler.cancelUpdateSuggestions();
            updateSuggestions();
        }
        final CharSequence autoCorrection = mWordComposer.getAutoCorrectionOrNull();
        if (autoCorrection != null) {
            final String typedWord = mWordComposer.getTypedWord();
            if (TextUtils.isEmpty(typedWord)) {
                throw new RuntimeException("We have an auto-correction but the typed word "
                        + "is empty? Impossible! I must commit suicide.");
            }
            Utils.Stats.onAutoCorrection(typedWord, autoCorrection.toString(), separatorCodePoint);
            mExpectingUpdateSelection = true;
            commitChosenWord(autoCorrection, LastComposedWord.COMMIT_TYPE_DECIDED_WORD);
            // Add the word to the user unigram dictionary if it's not a known word
            addToUserUnigramAndBigramDictionaries(autoCorrection,
                    UserUnigramDictionary.FREQUENCY_FOR_TYPED);
            if (!typedWord.equals(autoCorrection) && null != ic) {
                // This will make the correction flash for a short while as a visual clue
                // to the user that auto-correction happened.
                InputConnectionCompatUtils.commitCorrection(ic,
                        mLastSelectionEnd - typedWord.length(), typedWord, autoCorrection);
            }
        }
    }

    @Override
    public void pickSuggestionManually(final int index, final CharSequence suggestion) {
        mComposingStateManager.onFinishComposingText();
        final SuggestedWords suggestions = mSuggestionsView.getSuggestions();
        mVoiceProxy.flushAndLogAllTextModificationCounters(index, suggestion,
                mSettingsValues.mWordSeparators);

        if (mInputAttributes.mApplicationSpecifiedCompletionOn
                && mApplicationSpecifiedCompletions != null
                && index >= 0 && index < mApplicationSpecifiedCompletions.length) {
            final InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.beginBatchEdit();
                final CompletionInfo completionInfo = mApplicationSpecifiedCompletions[index];
                ic.commitCompletion(completionInfo);
            }
            if (mSuggestionsView != null) {
                mSuggestionsView.clear();
            }
            mKeyboardSwitcher.updateShiftState();
            if (ic != null) {
                ic.endBatchEdit();
            }
            return;
        }

        // If this is a punctuation, apply it through the normal key press
        if (suggestion.length() == 1 && (mSettingsValues.isWordSeparator(suggestion.charAt(0))
                || mSettingsValues.isSuggestedPunctuation(suggestion.charAt(0)))) {
            // Word separators are suggested before the user inputs something.
            // So, LatinImeLogger logs "" as a user's input.
            LatinImeLogger.logOnManualSuggestion(
                    "", suggestion.toString(), index, suggestions.mWords);
            final int primaryCode = suggestion.charAt(0);
            // Find out whether the previous character is a space. If it is, as a special case
            // for punctuation entered through the suggestion strip, it should be swapped
            // if it was a magic or a weak space. This is meant to help in case the user
            // pressed space on purpose of displaying the suggestion strip punctuation.
            insertPunctuationFromSuggestionStrip(primaryCode);
            return;
        }
        // We need to log before we commit, because the word composer will store away the user
        // typed word.
        LatinImeLogger.logOnManualSuggestion(mWordComposer.getTypedWord().toString(),
                suggestion.toString(), index, suggestions.mWords);
        mExpectingUpdateSelection = true;
        commitChosenWord(suggestion, LastComposedWord.COMMIT_TYPE_MANUAL_PICK);
        // Add the word to the auto dictionary if it's not a known word
        if (index == 0) {
            addToUserUnigramAndBigramDictionaries(suggestion,
                    UserUnigramDictionary.FREQUENCY_FOR_PICKED);
        } else {
            addToOnlyBigramDictionary(suggestion, 1);
        }
        mSpaceState = SPACE_STATE_PHANTOM;
        // TODO: is this necessary?
        mKeyboardSwitcher.updateShiftState();

        // We should show the "Touch again to save" hint if the user pressed the first entry
        // AND either:
        // - There is no dictionary (we know that because we tried to load it => null != mSuggest
        //   AND mSuggest.hasMainDictionary() is false)
        // - There is a dictionary and the word is not in it
        // Please note that if mSuggest is null, it means that everything is off: suggestion
        // and correction, so we shouldn't try to show the hint
        // We used to look at mCorrectionMode here, but showing the hint should have nothing
        // to do with the autocorrection setting.
        final boolean showingAddToDictionaryHint = index == 0 && mSuggest != null
                // If there is no dictionary the hint should be shown.
                && (!mSuggest.hasMainDictionary()
                        // If "suggestion" is not in the dictionary, the hint should be shown.
                        || !AutoCorrection.isValidWord(
                                mSuggest.getUnigramDictionaries(), suggestion, true));

        Utils.Stats.onSeparator((char)Keyboard.CODE_SPACE, WordComposer.NOT_A_COORDINATE,
                WordComposer.NOT_A_COORDINATE);
        if (!showingAddToDictionaryHint) {
            // If we're not showing the "Touch again to save", then show corrections again.
            // In case the cursor position doesn't change, make sure we show the suggestions again.
            updateBigramPredictions();
            // Updating the predictions right away may be slow and feel unresponsive on slower
            // terminals. On the other hand if we just postUpdateBigramPredictions() it will
            // take a noticeable delay to update them which may feel uneasy.
        } else {
            if (mIsUserDictionaryAvailable) {
                mSuggestionsView.showAddToDictionaryHint(
                        suggestion, mSettingsValues.mHintToSaveText);
            } else {
                mHandler.postUpdateSuggestions();
            }
        }
    }

    /**
     * Commits the chosen word to the text field and saves it for later retrieval.
     */
    private void commitChosenWord(final CharSequence bestWord, final int commitType) {
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            mVoiceProxy.rememberReplacedWord(bestWord, mSettingsValues.mWordSeparators);
            if (mSettingsValues.mEnableSuggestionSpanInsertion) {
                final SuggestedWords suggestedWords = mSuggestionsView.getSuggestions();
                ic.commitText(SuggestionSpanUtils.getTextWithSuggestionSpan(
                        this, bestWord, suggestedWords), 1);
            } else {
                ic.commitText(bestWord, 1);
            }
        }
        // TODO: figure out here if this is an auto-correct or if the best word is actually
        // what user typed. Note: currently this is done much later in
        // LastComposedWord#canCancelAutoCorrect by string equality of the remembered
        // strings.
        mLastComposedWord = mWordComposer.commitWord(commitType);
    }

    private static final WordComposer sEmptyWordComposer = new WordComposer();
    public void updateBigramPredictions() {
        if (mSuggest == null || !isSuggestionsRequested())
            return;

        if (!mSettingsValues.mBigramPredictionEnabled) {
            setPunctuationSuggestions();
            return;
        }

        final CharSequence prevWord = EditingUtils.getThisWord(getCurrentInputConnection(),
                mSettingsValues.mWordSeparators);
        SuggestedWords.Builder builder = mSuggest.getSuggestedWordBuilder(sEmptyWordComposer,
                prevWord, mKeyboardSwitcher.getKeyboard().getProximityInfo(), mCorrectionMode);

        if (builder.size() > 0) {
            // Explicitly supply an empty typed word (the no-second-arg version of
            // showSuggestions will retrieve the word near the cursor, we don't want that here)
            showSuggestions(builder.build(), "");
        } else {
            if (!isShowingPunctuationList()) setPunctuationSuggestions();
        }
    }

    public void setPunctuationSuggestions() {
        setSuggestions(mSettingsValues.mSuggestPuncList);
        setSuggestionStripShown(isSuggestionsStripVisible());
    }

    private void addToUserUnigramAndBigramDictionaries(CharSequence suggestion,
            int frequencyDelta) {
        checkAddToDictionary(suggestion, frequencyDelta, false);
    }

    private void addToOnlyBigramDictionary(CharSequence suggestion, int frequencyDelta) {
        checkAddToDictionary(suggestion, frequencyDelta, true);
    }

    /**
     * Adds to the UserBigramDictionary and/or UserUnigramDictionary
     * @param selectedANotTypedWord true if it should be added to bigram dictionary if possible
     */
    private void checkAddToDictionary(CharSequence suggestion, int frequencyDelta,
            boolean selectedANotTypedWord) {
        if (suggestion == null || suggestion.length() < 1) return;

        // Only auto-add to dictionary if auto-correct is ON. Otherwise we'll be
        // adding words in situations where the user or application really didn't
        // want corrections enabled or learned.
        if (!(mCorrectionMode == Suggest.CORRECTION_FULL
                || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM)) {
            return;
        }

        if (null != mSuggest && null != mUserUnigramDictionary) {
            final boolean selectedATypedWordAndItsInUserUnigramDic =
                    !selectedANotTypedWord && mUserUnigramDictionary.isValidWord(suggestion);
            final boolean isValidWord = AutoCorrection.isValidWord(
                    mSuggest.getUnigramDictionaries(), suggestion, true);
            final boolean needsToAddToUserUnigramDictionary =
                    selectedATypedWordAndItsInUserUnigramDic || !isValidWord;
            if (needsToAddToUserUnigramDictionary) {
                mUserUnigramDictionary.addWord(suggestion.toString(), frequencyDelta);
            }
        }

        if (mUserBigramDictionary != null) {
            // We don't want to register as bigrams words separated by a separator.
            // For example "I will, and you too" : we don't want the pair ("will" "and") to be
            // a bigram.
            final InputConnection ic = getCurrentInputConnection();
            if (null != ic) {
                final CharSequence prevWord =
                        EditingUtils.getPreviousWord(ic, mSettingsValues.mWordSeparators);
                if (!TextUtils.isEmpty(prevWord)) {
                    mUserBigramDictionary.addBigrams(prevWord.toString(), suggestion.toString());
                }
            }
        }
    }

    public boolean isCursorTouchingWord() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence toLeft = ic.getTextBeforeCursor(1, 0);
        CharSequence toRight = ic.getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(toLeft)
                && !mSettingsValues.isWordSeparator(toLeft.charAt(0))
                && !mSettingsValues.isSuggestedPunctuation(toLeft.charAt(0))) {
            return true;
        }
        if (!TextUtils.isEmpty(toRight)
                && !mSettingsValues.isWordSeparator(toRight.charAt(0))
                && !mSettingsValues.isSuggestedPunctuation(toRight.charAt(0))) {
            return true;
        }
        return false;
    }

    // "ic" must not be null
    private static boolean sameAsTextBeforeCursor(final InputConnection ic, CharSequence text) {
        CharSequence beforeText = ic.getTextBeforeCursor(text.length(), 0);
        return TextUtils.equals(text, beforeText);
    }

    // "ic" must not be null
    /**
     * Check if the cursor is actually at the end of a word. If so, restart suggestions on this
     * word, else do nothing.
     */
    private void restartSuggestionsOnWordBeforeCursorIfAtEndOfWord(
            final InputConnection ic) {
        // Bail out if the cursor is not at the end of a word (cursor must be preceded by
        // non-whitespace, non-separator, non-start-of-text)
        // Example ("|" is the cursor here) : <SOL>"|a" " |a" " | " all get rejected here.
        final CharSequence textBeforeCursor = ic.getTextBeforeCursor(1, 0);
        if (TextUtils.isEmpty(textBeforeCursor)
                || mSettingsValues.isWordSeparator(textBeforeCursor.charAt(0))) return;

        // Bail out if the cursor is in the middle of a word (cursor must be followed by whitespace,
        // separator or end of line/text)
        // Example: "test|"<EOL> "te|st" get rejected here
        final CharSequence textAfterCursor = ic.getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(textAfterCursor)
                && !mSettingsValues.isWordSeparator(textAfterCursor.charAt(0))) return;

        // Bail out if word before cursor is 0-length or a single non letter (like an apostrophe)
        // Example: " -|" gets rejected here but "e-|" and "e|" are okay
        CharSequence word = EditingUtils.getWordAtCursor(ic, mSettingsValues.mWordSeparators);
        // We don't suggest on leading single quotes, so we have to remove them from the word if
        // it starts with single quotes.
        while (!TextUtils.isEmpty(word) && Keyboard.CODE_SINGLE_QUOTE == word.charAt(0)) {
            word = word.subSequence(1, word.length());
        }
        if (TextUtils.isEmpty(word)) return;
        final char firstChar = word.charAt(0); // we just tested that word is not empty
        if (word.length() == 1 && !Character.isLetter(firstChar)) return;

        // We only suggest on words that start with a letter or a symbol that is excluded from
        // word separators (see #handleCharacterWhileInBatchEdit).
        if (!(isAlphabet(firstChar)
                || mSettingsValues.isSymbolExcludedFromWordSeparators(firstChar))) {
            return;
        }

        // Okay, we are at the end of a word. Restart suggestions.
        restartSuggestionsOnWordBeforeCursor(ic, word);
    }

    // "ic" must not be null
    private void restartSuggestionsOnWordBeforeCursor(final InputConnection ic,
            final CharSequence word) {
        mWordComposer.setComposingWord(word, mKeyboardSwitcher.getKeyboard());
        mComposingStateManager.onStartComposingText();
        ic.deleteSurroundingText(word.length(), 0);
        ic.setComposingText(word, 1);
        mHandler.postUpdateSuggestions();
    }

    // "ic" must not be null
    private void cancelAutoCorrect(final InputConnection ic) {
        final String originallyTypedWord = mLastComposedWord.mTypedWord;
        final CharSequence autoCorrectedTo = mLastComposedWord.mAutoCorrection;
        final int cancelLength = autoCorrectedTo.length();
        final CharSequence separator = ic.getTextBeforeCursor(1, 0);
        if (DEBUG) {
            if (mWordComposer.isComposingWord()) {
                throw new RuntimeException("cancelAutoCorrect, but we are composing a word");
            }
            final String wordBeforeCursor =
                    ic.getTextBeforeCursor(cancelLength + 1, 0).subSequence(0, cancelLength)
                    .toString();
            if (!TextUtils.equals(autoCorrectedTo, wordBeforeCursor)) {
                throw new RuntimeException("cancelAutoCorrect check failed: we thought we were "
                        + "reverting \"" + autoCorrectedTo
                        + "\", but before the cursor we found \"" + wordBeforeCursor + "\"");
            }
            if (TextUtils.equals(originallyTypedWord, wordBeforeCursor)) {
                throw new RuntimeException("cancelAutoCorrect check failed: we wanted to cancel "
                        + "auto correction and revert to \"" + originallyTypedWord
                        + "\" but we found this very string before the cursor");
            }
        }
        ic.deleteSurroundingText(cancelLength + 1, 0);
        ic.commitText(originallyTypedWord, 1);
        // Re-insert the separator
        ic.commitText(separator, 1);
        mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
        Utils.Stats.onSeparator(separator.charAt(0), WordComposer.NOT_A_COORDINATE,
                WordComposer.NOT_A_COORDINATE);
        mHandler.cancelUpdateBigramPredictions();
        mHandler.postUpdateSuggestions();
    }

    // "ic" must not be null
    private void restartSuggestionsOnManuallyPickedTypedWord(final InputConnection ic) {
        // Note: this relies on the last word still being held in the WordComposer, in
        // the field for suggestion resuming.
        // Note: in the interest of code simplicity, we may want to just call
        // restartSuggestionsOnWordBeforeCursorIfAtEndOfWord instead, but retrieving
        // the old WordComposer allows to reuse the actual typed coordinates.
        mWordComposer.resumeSuggestionOnLastComposedWord(mLastComposedWord);
        // We resume suggestion, and then we want to set the composing text to the content
        // of the word composer again. But since we just manually picked a word, there is
        // no composing text at the moment, so we have to delete the word before we set a
        // new composing text.
        final int restartLength = mWordComposer.size();
        if (DEBUG) {
            final String wordBeforeCursor = ic.getTextBeforeCursor(restartLength, 0).toString();
            if (!TextUtils.equals(mWordComposer.getTypedWord(), wordBeforeCursor)) {
                throw new RuntimeException("restartSuggestionsOnManuallyPickedTypedWord "
                        + "check failed: we thought we were reverting \""
                        + mWordComposer.getTypedWord()
                        + "\", but before the cursor we found \""
                        + wordBeforeCursor + "\"");
            }
        }
        ic.deleteSurroundingText(restartLength, 0);
        mComposingStateManager.onStartComposingText();
        ic.setComposingText(mWordComposer.getTypedWord(), 1);
        mHandler.cancelUpdateBigramPredictions();
        mHandler.postUpdateSuggestions();
    }

    // "ic" must not be null
    private boolean revertDoubleSpaceWhileInBatchEdit(final InputConnection ic) {
        mHandler.cancelDoubleSpacesTimer();
        // Here we test whether we indeed have a period and a space before us. This should not
        // be needed, but it's there just in case something went wrong.
        final CharSequence textBeforeCursor = ic.getTextBeforeCursor(2, 0);
        if (!". ".equals(textBeforeCursor)) {
            // Theoretically we should not be coming here if there isn't ". " before the
            // cursor, but the application may be changing the text while we are typing, so
            // anything goes. We should not crash.
            Log.d(TAG, "Tried to revert double-space combo but we didn't find "
                    + "\". \" just before the cursor.");
            return false;
        }
        ic.deleteSurroundingText(2, 0);
        ic.commitText("  ", 1);
        return true;
    }

    private static boolean revertSwapPunctuation(final InputConnection ic) {
        // Here we test whether we indeed have a space and something else before us. This should not
        // be needed, but it's there just in case something went wrong.
        final CharSequence textBeforeCursor = ic.getTextBeforeCursor(2, 0);
        // NOTE: This does not work with surrogate pairs. Hopefully when the keyboard is able to
        // enter surrogate pairs this code will have been removed.
        if (TextUtils.isEmpty(textBeforeCursor)
                || (Keyboard.CODE_SPACE != textBeforeCursor.charAt(1))) {
            // We may only come here if the application is changing the text while we are typing.
            // This is quite a broken case, but not logically impossible, so we shouldn't crash,
            // but some debugging log may be in order.
            Log.d(TAG, "Tried to revert a swap of punctuation but we didn't "
                    + "find a space just before the cursor.");
            return false;
        }
        ic.beginBatchEdit();
        ic.deleteSurroundingText(2, 0);
        ic.commitText(" " + textBeforeCursor.subSequence(0, 1), 1);
        ic.endBatchEdit();
        return true;
    }

    public boolean isWordSeparator(int code) {
        return mSettingsValues.isWordSeparator(code);
    }

    public boolean preferCapitalization() {
        return mWordComposer.isFirstCharCapitalized();
    }

    // Notify that language or mode have been changed and toggleLanguage will update KeyboardID
    // according to new language or mode.
    public void onRefreshKeyboard() {
        if (!CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED) {
            // Before Honeycomb, Voice IME is in LatinIME and it changes the current input view,
            // so that we need to re-create the keyboard input view here.
            setInputView(mKeyboardSwitcher.onCreateInputView());
        }
        // When the device locale is changed in SetupWizard etc., this method may get called via
        // onConfigurationChanged before SoftInputWindow is shown.
        if (mKeyboardSwitcher.getKeyboardView() != null) {
            // Reload keyboard because the current language has been changed.
            mKeyboardSwitcher.loadKeyboard(getCurrentInputEditorInfo(), mSettingsValues);
        }
        initSuggest();
        loadSettings();
    }

    public void hapticAndAudioFeedback(int primaryCode) {
        vibrate();
        playKeyClick(primaryCode);
    }

    @Override
    public void onPressKey(int primaryCode) {
        mKeyboardSwitcher.onPressKey(primaryCode);
    }

    @Override
    public void onReleaseKey(int primaryCode, boolean withSliding) {
        mKeyboardSwitcher.onReleaseKey(primaryCode, withSliding);
    }

    // receive ringer mode change and network state change.
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                updateRingerMode();
            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                mSubtypeSwitcher.onNetworkStateChanged(intent);
            }
        }
    };

    // update flags for silent mode
    private void updateRingerMode() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (mAudioManager == null) return;
        }
        mSilentModeOn = (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL);
    }

    private void playKeyClick(int primaryCode) {
        // if mAudioManager is null, we don't have the ringer state yet
        // mAudioManager will be set by updateRingerMode
        if (mAudioManager == null) {
            if (mKeyboardSwitcher.getKeyboardView() != null) {
                updateRingerMode();
            }
        }
        if (isSoundOn()) {
            final int sound;
            switch (primaryCode) {
            case Keyboard.CODE_DELETE:
                sound = AudioManager.FX_KEYPRESS_DELETE;
                break;
            case Keyboard.CODE_ENTER:
                sound = AudioManager.FX_KEYPRESS_RETURN;
                break;
            case Keyboard.CODE_SPACE:
                sound = AudioManager.FX_KEYPRESS_SPACEBAR;
                break;
            default:
                sound = AudioManager.FX_KEYPRESS_STANDARD;
                break;
            }
            mAudioManager.playSoundEffect(sound, mSettingsValues.mFxVolume);
        }
    }

    public void vibrate() {
        if (!mSettingsValues.mVibrateOn) {
            return;
        }
        if (mSettingsValues.mKeypressVibrationDuration < 0) {
            // Go ahead with the system default
            LatinKeyboardView inputView = mKeyboardSwitcher.getKeyboardView();
            if (inputView != null) {
                inputView.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
        } else if (mVibrator != null) {
            mVibrator.vibrate(mSettingsValues.mKeypressVibrationDuration);
        }
    }

    public boolean isAutoCapitalized() {
        return mWordComposer.isAutoCapitalized();
    }

    boolean isSoundOn() {
        return mSettingsValues.mSoundOn && !mSilentModeOn;
    }

    private void updateCorrectionMode() {
        // TODO: cleanup messy flags
        final boolean shouldAutoCorrect = mSettingsValues.mAutoCorrectEnabled
                && !mInputAttributes.mInputTypeNoAutoCorrect;
        mCorrectionMode = shouldAutoCorrect ? Suggest.CORRECTION_FULL : Suggest.CORRECTION_NONE;
        mCorrectionMode = (mSettingsValues.mBigramSuggestionEnabled && shouldAutoCorrect)
                ? Suggest.CORRECTION_FULL_BIGRAM : mCorrectionMode;
    }

    private void updateSuggestionVisibility(final Resources res) {
        final String suggestionVisiblityStr = mSettingsValues.mShowSuggestionsSetting;
        for (int visibility : SUGGESTION_VISIBILITY_VALUE_ARRAY) {
            if (suggestionVisiblityStr.equals(res.getString(visibility))) {
                mSuggestionVisibility = visibility;
                break;
            }
        }
    }

    protected void launchSettings() {
        launchSettingsClass(Settings.class);
    }

    public void launchDebugSettings() {
        launchSettingsClass(DebugSettings.class);
    }

    protected void launchSettingsClass(Class<? extends PreferenceActivity> settingsClass) {
        handleClose();
        Intent intent = new Intent();
        intent.setClass(LatinIME.this, settingsClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showSubtypeSelectorAndSettings() {
        final CharSequence title = getString(R.string.english_ime_input_options);
        final CharSequence[] items = new CharSequence[] {
                // TODO: Should use new string "Select active input modes".
                getString(R.string.language_selection_title),
                getString(R.string.english_ime_settings),
        };
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                case 0:
                    Intent intent = CompatUtils.getInputLanguageSelectionIntent(
                            Utils.getInputMethodId(mImm, getPackageName()),
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
        showOptionDialogInternal(builder.create());
    }

    private void showOptionsMenu() {
        final CharSequence title = getString(R.string.english_ime_input_options);
        final CharSequence[] items = new CharSequence[] {
                getString(R.string.selectInputMethod),
                getString(R.string.english_ime_settings),
        };
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                case 0:
                    mImm.showInputMethodPicker();
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
        showOptionDialogInternal(builder.create());
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
        super.dump(fd, fout, args);

        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
        final int keyboardMode = keyboard != null ? keyboard.mId.mMode : -1;
        p.println("  Keyboard mode = " + keyboardMode);
        p.println("  mIsSuggestionsRequested=" + mInputAttributes.mIsSettingsSuggestionStripOn);
        p.println("  mCorrectionMode=" + mCorrectionMode);
        p.println("  isComposingWord=" + mWordComposer.isComposingWord());
        p.println("  mAutoCorrectEnabled=" + mSettingsValues.mAutoCorrectEnabled);
        p.println("  mSoundOn=" + mSettingsValues.mSoundOn);
        p.println("  mVibrateOn=" + mSettingsValues.mVibrateOn);
        p.println("  mKeyPreviewPopupOn=" + mSettingsValues.mKeyPreviewPopupOn);
        p.println("  mInputAttributes=" + mInputAttributes.toString());
    }
}
