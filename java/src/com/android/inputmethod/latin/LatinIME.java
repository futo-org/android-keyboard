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
import android.os.Build;
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
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.LatinKeyboard;
import com.android.inputmethod.keyboard.LatinKeyboardView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodServiceCompatWrapper implements KeyboardActionListener,
        SuggestionsView.Listener {
    private static final String TAG = LatinIME.class.getSimpleName();
    private static final boolean PERF_DEBUG = false;
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
     */
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

    private Settings.Values mSettingsValues;

    private View mExtractArea;
    private View mKeyPreviewBackingView;
    private View mSuggestionsContainer;
    private SuggestionsView mSuggestionsView;
    private Suggest mSuggest;
    private CompletionInfo[] mApplicationSpecifiedCompletions;

    private InputMethodManagerCompatWrapper mImm;
    private Resources mResources;
    private SharedPreferences mPrefs;
    private String mInputMethodId;
    private KeyboardSwitcher mKeyboardSwitcher;
    private SubtypeSwitcher mSubtypeSwitcher;
    private VoiceProxy mVoiceProxy;

    private UserDictionary mUserDictionary;
    private UserBigramDictionary mUserBigramDictionary;
    private UserUnigramDictionary mUserUnigramDictionary;
    private boolean mIsUserDictionaryAvaliable;

    // TODO: Create an inner class to group options and pseudo-options to improve readability.
    // These variables are initialized according to the {@link EditorInfo#inputType}.
    private boolean mShouldInsertMagicSpace;
    private boolean mInputTypeNoAutoCorrect;
    private boolean mIsSettingsSuggestionStripOn;
    private boolean mApplicationSpecifiedCompletionOn;

    private final StringBuilder mComposingStringBuilder = new StringBuilder();
    private WordComposer mWordComposer = new WordComposer();
    private CharSequence mBestWord;
    private boolean mHasUncommittedTypedChars;
    // Magic space: a space that should disappear on space/apostrophe insertion, move after the
    // punctuation on punctuation insertion, and become a real space on alpha char insertion.
    private boolean mJustAddedMagicSpace; // This indicates whether the last char is a magic space.
    // This indicates whether the last keypress resulted in processing of double space replacement
    // with period-space.
    private boolean mJustReplacedDoubleSpace;

    private int mCorrectionMode;
    private int mCommittedLength;
    // Keep track of the last selection range to decide if we need to show word alternatives
    private int mLastSelectionStart;
    private int mLastSelectionEnd;

    // Whether we are expecting an onUpdateSelection event to fire. If it does when we don't
    // "expect" it, it means the user actually moved the cursor.
    private boolean mExpectingUpdateSelection;
    private int mDeleteCount;
    private long mLastKeyTime;

    private AudioManager mAudioManager;
    private float mFxVolume = -1.0f; // default volume
    private boolean mSilentModeOn; // System-wide current configuration

    private VibratorCompatWrapper mVibrator;
    private long mKeypressVibrationDuration = -1;

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

        public UIHandler(LatinIME outerInstance) {
            super(outerInstance);
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
                latinIme.mVoiceProxy.handleVoiceResults(latinIme.preferCapitalization()
                        || (switcher.isAlphabetMode() && switcher.isShiftedOrShiftLocked()));
                break;
            case MSG_FADEOUT_LANGUAGE_ON_SPACEBAR:
                if (inputView != null) {
                    inputView.setSpacebarTextFadeFactor(
                            (1.0f + latinIme.mSettingsValues.
                                    mFinalFadeoutFactorOfLanguageOnSpacebar) / 2,
                            (LatinKeyboard)msg.obj);
                }
                sendMessageDelayed(obtainMessage(MSG_DISMISS_LANGUAGE_ON_SPACEBAR, msg.obj),
                        latinIme.mSettingsValues.mDurationOfFadeoutLanguageOnSpacebar);
                break;
            case MSG_DISMISS_LANGUAGE_ON_SPACEBAR:
                if (inputView != null) {
                    inputView.setSpacebarTextFadeFactor(
                            latinIme.mSettingsValues.mFinalFadeoutFactorOfLanguageOnSpacebar,
                            (LatinKeyboard)msg.obj);
                }
                break;
            }
        }

        public void postUpdateSuggestions() {
            removeMessages(MSG_UPDATE_SUGGESTIONS);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTIONS),
                    getOuterInstance().mSettingsValues.mDelayUpdateSuggestions);
        }

        public void cancelUpdateSuggestions() {
            removeMessages(MSG_UPDATE_SUGGESTIONS);
        }

        public boolean hasPendingUpdateSuggestions() {
            return hasMessages(MSG_UPDATE_SUGGESTIONS);
        }

        public void postUpdateShiftKeyState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SHIFT_STATE),
                    getOuterInstance().mSettingsValues.mDelayUpdateShiftState);
        }

        public void cancelUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
        }

        public void postUpdateBigramPredictions() {
            removeMessages(MSG_SET_BIGRAM_PREDICTIONS);
            sendMessageDelayed(obtainMessage(MSG_SET_BIGRAM_PREDICTIONS),
                    getOuterInstance().mSettingsValues.mDelayUpdateSuggestions);
        }

        public void cancelUpdateBigramPredictions() {
            removeMessages(MSG_SET_BIGRAM_PREDICTIONS);
        }

        public void updateVoiceResults() {
            sendMessage(obtainMessage(MSG_VOICE_RESULTS));
        }

        public void startDisplayLanguageOnSpacebar(boolean localeChanged) {
            final LatinIME latinIme = getOuterInstance();
            removeMessages(MSG_FADEOUT_LANGUAGE_ON_SPACEBAR);
            removeMessages(MSG_DISMISS_LANGUAGE_ON_SPACEBAR);
            final LatinKeyboardView inputView = latinIme.mKeyboardSwitcher.getKeyboardView();
            if (inputView != null) {
                final LatinKeyboard keyboard = latinIme.mKeyboardSwitcher.getLatinKeyboard();
                // The language is always displayed when the delay is negative.
                final boolean needsToDisplayLanguage = localeChanged
                        || latinIme.mSettingsValues.mDelayBeforeFadeoutLanguageOnSpacebar < 0;
                // The language is never displayed when the delay is zero.
                if (latinIme.mSettingsValues.mDelayBeforeFadeoutLanguageOnSpacebar != 0) {
                    inputView.setSpacebarTextFadeFactor(needsToDisplayLanguage ? 1.0f
                            : latinIme.mSettingsValues.mFinalFadeoutFactorOfLanguageOnSpacebar,
                            keyboard);
                }
                // The fadeout animation will start when the delay is positive.
                if (localeChanged
                        && latinIme.mSettingsValues.mDelayBeforeFadeoutLanguageOnSpacebar > 0) {
                    sendMessageDelayed(obtainMessage(MSG_FADEOUT_LANGUAGE_ON_SPACEBAR, keyboard),
                            latinIme.mSettingsValues.mDelayBeforeFadeoutLanguageOnSpacebar);
                }
            }
        }

        public void startDoubleSpacesTimer() {
            removeMessages(MSG_SPACE_TYPED);
            sendMessageDelayed(obtainMessage(MSG_SPACE_TYPED),
                    getOuterInstance().mSettingsValues.mDoubleSpacesTurnIntoPeriodTimeout);
        }

        public void cancelDoubleSpacesTimer() {
            removeMessages(MSG_SPACE_TYPED);
        }

        public boolean isAcceptingDoubleSpaces() {
            return hasMessages(MSG_SPACE_TYPED);
        }

        // Working variables for the following methods.
        private boolean mIsOrientationChanging;
        private boolean mPendingSuccesiveImsCallback;
        private boolean mHasPendingStartInput;
        private boolean mHasPendingFinishInputView;
        private boolean mHasPendingFinishInput;

        public void startOrientationChanging() {
            mIsOrientationChanging = true;
            final LatinIME latinIme = getOuterInstance();
            latinIme.mKeyboardSwitcher.saveKeyboardState();
        }

        private void resetPendingImsCallback() {
            mHasPendingFinishInputView = false;
            mHasPendingFinishInput = false;
            mHasPendingStartInput = false;
        }

        private void executePendingImsCallback(LatinIME latinIme, EditorInfo attribute,
                boolean restarting) {
            if (mHasPendingFinishInputView)
                latinIme.onFinishInputViewInternal(mHasPendingFinishInput);
            if (mHasPendingFinishInput)
                latinIme.onFinishInputInternal();
            if (mHasPendingStartInput)
                latinIme.onStartInputInternal(attribute, restarting);
            resetPendingImsCallback();
        }

        public void onStartInput(EditorInfo attribute, boolean restarting) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the second onStartInput after orientation changed.
                mHasPendingStartInput = true;
            } else {
                if (mIsOrientationChanging && restarting) {
                    // This is the first onStartInput after orientation changed.
                    mIsOrientationChanging = false;
                    mPendingSuccesiveImsCallback = true;
                }
                final LatinIME latinIme = getOuterInstance();
                executePendingImsCallback(latinIme, attribute, restarting);
                latinIme.onStartInputInternal(attribute, restarting);
            }
        }

        public void onStartInputView(EditorInfo attribute, boolean restarting) {
             if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                 // Typically this is the second onStartInputView after orientation changed.
                 resetPendingImsCallback();
             } else {
                 if (mPendingSuccesiveImsCallback) {
                     // This is the first onStartInputView after orientation changed.
                     mPendingSuccesiveImsCallback = false;
                     resetPendingImsCallback();
                     sendMessageDelayed(obtainMessage(MSG_PENDING_IMS_CALLBACK),
                             PENDING_IMS_CALLBACK_DURATION);
                 }
                 final LatinIME latinIme = getOuterInstance();
                 executePendingImsCallback(latinIme, attribute, restarting);
                 latinIme.onStartInputViewInternal(attribute, restarting);
             }
        }

        public void onFinishInputView(boolean finishingInput) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                // Typically this is the first onFinishInputView after orientation changed.
                mHasPendingFinishInputView = true;
            } else {
                final LatinIME latinIme = getOuterInstance();
                latinIme.onFinishInputViewInternal(finishingInput);
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
        AccessibilityUtils.init(this, prefs);

        super.onCreate();

        mImm = InputMethodManagerCompatWrapper.getInstance();
        mInputMethodId = Utils.getInputMethodId(mImm, getPackageName());
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mVibrator = VibratorCompatWrapper.getInstance(this);
        DEBUG = LatinImeLogger.sDBG;

        final Resources res = getResources();
        mResources = res;

        loadSettings();

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
        mSettingsValues = new Settings.Values(mPrefs, this, mSubtypeSwitcher.getInputLocaleStr());
        resetContactsDictionary(null == mSuggest ? null : mSuggest.getContactsDictionary());
        updateSoundEffectVolume();
        updateKeypressVibrationDuration();
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
        mIsUserDictionaryAvaliable = mUserDictionary.isEnabled();

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
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        mHandler.onStartInput(attribute, restarting);
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        mHandler.onStartInputView(attribute, restarting);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        mHandler.onFinishInputView(finishingInput);
    }

    @Override
    public void onFinishInput() {
        mHandler.onFinishInput();
    }

    private void onStartInputInternal(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
    }

    private void onStartInputViewInternal(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        LatinKeyboardView inputView = switcher.getKeyboardView();

        if (DEBUG) {
            Log.d(TAG, "onStartInputView: attribute:" + ((attribute == null) ? "none"
                    : String.format("inputType=0x%08x imeOptions=0x%08x",
                            attribute.inputType, attribute.imeOptions)));
        }
        // In landscape mode, this method gets called without the input view being created.
        if (inputView == null) {
            return;
        }

        // Forward this event to the accessibility utilities, if enabled.
        final AccessibilityUtils accessUtils = AccessibilityUtils.getInstance();
        if (accessUtils.isTouchExplorationEnabled()) {
            accessUtils.onStartInputViewInternal(attribute, restarting);
        }

        mSubtypeSwitcher.updateParametersOnStartInputView();

        TextEntryState.reset();

        // Most such things we decide below in initializeInputAttributesAndGetMode, but we need to
        // know now whether this is a password text field, because we need to know now whether we
        // want to enable the voice button.
        final VoiceProxy voiceIme = mVoiceProxy;
        final int inputType = (attribute != null) ? attribute.inputType : 0;
        voiceIme.resetVoiceStates(InputTypeCompatUtils.isPasswordInputType(inputType)
                || InputTypeCompatUtils.isVisiblePasswordInputType(inputType));

        initializeInputAttributes(attribute);

        inputView.closing();
        mEnteredText = null;
        mComposingStringBuilder.setLength(0);
        mHasUncommittedTypedChars = false;
        mDeleteCount = 0;
        mJustAddedMagicSpace = false;
        mJustReplacedDoubleSpace = false;

        loadSettings();
        updateCorrectionMode();
        updateSuggestionVisibility(mPrefs, mResources);

        if (mSuggest != null && mSettingsValues.mAutoCorrectEnabled) {
            mSuggest.setAutoCorrectionThreshold(mSettingsValues.mAutoCorrectionThreshold);
         }
        mVoiceProxy.loadSettings(attribute, mPrefs);
        // This will work only when the subtype is not supported.
        LanguageSwitcherProxy.loadSettings();

        if (mSubtypeSwitcher.isKeyboardMode()) {
            switcher.loadKeyboard(attribute, mSettingsValues);
        }

        if (mSuggestionsView != null)
            mSuggestionsView.clear();
        // The EditorInfo might have a flag that affects fullscreen mode.
        updateFullscreenMode();
        setSuggestionStripShownInternal(
                isSuggestionsStripVisible(), /* needsInputViewShown */ false);
        // Delay updating suggestions because keyboard input view may not be shown at this point.
        mHandler.postUpdateSuggestions();

        inputView.setKeyPreviewPopupEnabled(mSettingsValues.mKeyPreviewPopupOn,
                mSettingsValues.mKeyPreviewPopupDismissDelay);
        inputView.setProximityCorrectionEnabled(true);

        voiceIme.onStartInputView(inputView.getWindowToken());

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    private void initializeInputAttributes(EditorInfo attribute) {
        if (attribute == null)
            return;
        final int inputType = attribute.inputType;
        if (inputType == InputType.TYPE_NULL) {
            // TODO: We should honor TYPE_NULL specification.
            Log.i(TAG, "InputType.TYPE_NULL is specified");
        }
        final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        final int variation = inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == 0) {
            Log.w(TAG, String.format("Unexpected input class: inputType=0x%08x imeOptions=0x%08x",
                    inputType, attribute.imeOptions));
        }

        mShouldInsertMagicSpace = false;
        mInputTypeNoAutoCorrect = false;
        mIsSettingsSuggestionStripOn = false;
        mApplicationSpecifiedCompletionOn = false;
        mApplicationSpecifiedCompletions = null;

        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            mIsSettingsSuggestionStripOn = true;
            // Make sure that passwords are not displayed in {@link SuggestionsView}.
            if (InputTypeCompatUtils.isPasswordInputType(inputType)
                    || InputTypeCompatUtils.isVisiblePasswordInputType(inputType)) {
                mIsSettingsSuggestionStripOn = false;
            }
            if (InputTypeCompatUtils.isEmailVariation(variation)
                    || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME) {
                mShouldInsertMagicSpace = false;
            } else {
                mShouldInsertMagicSpace = true;
            }
            if (InputTypeCompatUtils.isEmailVariation(variation)) {
                mIsSettingsSuggestionStripOn = false;
            } else if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
                mIsSettingsSuggestionStripOn = false;
            } else if (variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                mIsSettingsSuggestionStripOn = false;
            } else if (variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
                // If it's a browser edit field and auto correct is not ON explicitly, then
                // disable auto correction, but keep suggestions on.
                if ((inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                    mInputTypeNoAutoCorrect = true;
                }
            }

            // If NO_SUGGESTIONS is set, don't do prediction.
            if ((inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                mIsSettingsSuggestionStripOn = false;
                mInputTypeNoAutoCorrect = true;
            }
            // If it's not multiline and the autoCorrect flag is not set, then don't correct
            if ((inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
                    && (inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
                mInputTypeNoAutoCorrect = true;
            }
            if ((inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                mIsSettingsSuggestionStripOn = false;
                mApplicationSpecifiedCompletionOn = isFullscreenMode();
            }
        }
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
            if (((mComposingStringBuilder.length() > 0 && mHasUncommittedTypedChars)
                    || mVoiceProxy.isVoiceInputHighlighted())
                    && (selectionChanged || candidatesCleared)) {
                mComposingStringBuilder.setLength(0);
                mHasUncommittedTypedChars = false;
                TextEntryState.reset();
                updateSuggestions();
                final InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.finishComposingText();
                }
                mComposingStateManager.onFinishComposingText();
                mVoiceProxy.setVoiceInputHighlighted(false);
            } else if (!mHasUncommittedTypedChars) {
                TextEntryState.reset();
                updateSuggestions();
            }
            mJustAddedMagicSpace = false; // The user moved the cursor.
            mJustReplacedDoubleSpace = false;
        }
        mExpectingUpdateSelection = false;
        mHandler.postUpdateShiftKeyState();

        // Make a note of the cursor position
        mLastSelectionStart = newSelStart;
        mLastSelectionEnd = newSelEnd;
    }

    public void setLastSelection(int start, int end) {
        mLastSelectionStart = start;
        mLastSelectionEnd = end;
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
        if (mApplicationSpecifiedCompletionOn) {
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
            mBestWord = null;
            setSuggestionStripShown(true);
        }
    }

    private void setSuggestionStripShownInternal(boolean shown, boolean needsInputViewShown) {
        // TODO: Modify this if we support suggestions with hard keyboard
        if (onEvaluateInputViewShown() && mSuggestionsContainer != null) {
            final boolean shouldShowSuggestions = shown
                    && (needsInputViewShown ? mKeyboardSwitcher.isInputViewShown() : true);
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
        if (mKeyboardSwitcher.isInputViewShown()) {
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
        return super.onEvaluateFullscreenMode()
                && mResources.getBoolean(R.bool.config_use_fullscreen_mode);
    }

    @Override
    public void updateFullscreenMode() {
        super.updateFullscreenMode();

        if (mKeyPreviewBackingView == null) return;
        // In extract mode, no need to have extra space to show the key preview.
        // If not, we should have extra space above the keyboard to show the key preview.
        mKeyPreviewBackingView.setVisibility(isExtractViewShown() ? View.GONE : View.VISIBLE);
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
            // Enable shift key and DPAD to do selections
            if (mKeyboardSwitcher.isInputViewShown()
                    && mKeyboardSwitcher.isShiftedOrShiftLocked()) {
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

    public void commitTyped(final InputConnection ic) {
        if (!mHasUncommittedTypedChars) return;
        mHasUncommittedTypedChars = false;
        if (mComposingStringBuilder.length() > 0) {
            if (ic != null) {
                ic.commitText(mComposingStringBuilder, 1);
            }
            mCommittedLength = mComposingStringBuilder.length();
            TextEntryState.acceptedTyped(mComposingStringBuilder);
            addToUserUnigramAndBigramDictionaries(mComposingStringBuilder,
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

    private void swapSwapperAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
        // It is guaranteed lastTwo.charAt(1) is a swapper - else this method is not called.
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == Keyboard.CODE_SPACE) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(lastTwo.charAt(1) + " ", 1);
            ic.endBatchEdit();
            mKeyboardSwitcher.updateShiftState();
        }
    }

    private void maybeDoubleSpace() {
        if (mCorrectionMode == Suggest.CORRECTION_NONE) return;
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        final CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && Utils.canBeFollowedByPeriod(lastThree.charAt(0))
                && lastThree.charAt(1) == Keyboard.CODE_SPACE
                && lastThree.charAt(2) == Keyboard.CODE_SPACE
                && mHandler.isAcceptingDoubleSpaces()) {
            mHandler.cancelDoubleSpacesTimer();
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(". ", 1);
            ic.endBatchEdit();
            mKeyboardSwitcher.updateShiftState();
            mJustReplacedDoubleSpace = true;
        } else {
            mHandler.startDoubleSpacesTimer();
        }
    }

    // "ic" must not null
    private void maybeRemovePreviousPeriod(final InputConnection ic, CharSequence text) {
        // When the text's first character is '.', remove the previous period
        // if there is one.
        CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == Keyboard.CODE_PERIOD
                && text.charAt(0) == Keyboard.CODE_PERIOD) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void removeTrailingSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
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

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    private void onSettingsKeyPressed() {
        if (isShowingOptionDialog()) return;
        if (InputMethodServiceCompatWrapper.CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED) {
            showSubtypeSelectorAndSettings();
        } else if (Utils.hasMultipleEnabledIMEsOrSubtypes(mImm, false /* exclude aux subtypes */)) {
            showOptionsMenu();
        } else {
            launchSettings();
        }
    }

    // Virtual codes representing custom requests.  These are used in onCustomRequest() below.
    public static final int CODE_SHOW_INPUT_METHOD_PICKER = 1;

    @Override
    public boolean onCustomRequest(int requestCode) {
        if (isShowingOptionDialog()) return false;
        switch (requestCode) {
        case CODE_SHOW_INPUT_METHOD_PICKER:
            if (Utils.hasMultipleEnabledIMEsOrSubtypes(mImm, true /* include aux subtypes */)) {
                mImm.showInputMethodPicker();
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean isShowingOptionDialog() {
        return mOptionsDialog != null && mOptionsDialog.isShowing();
    }

    // Implementation of {@link KeyboardActionListener}.
    @Override
    public void onCodeInput(int primaryCode, int[] keyCodes, int x, int y) {
        long when = SystemClock.uptimeMillis();
        if (primaryCode != Keyboard.CODE_DELETE || when > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        final boolean distinctMultiTouch = switcher.hasDistinctMultitouch();
        final boolean lastStateOfJustReplacedDoubleSpace = mJustReplacedDoubleSpace;
        mJustReplacedDoubleSpace = false;
        switch (primaryCode) {
        case Keyboard.CODE_DELETE:
            handleBackspace(lastStateOfJustReplacedDoubleSpace);
            mDeleteCount++;
            mExpectingUpdateSelection = true;
            LatinImeLogger.logOnDelete();
            break;
        case Keyboard.CODE_SHIFT:
            // Shift key is handled in onPress() when device has distinct multi-touch panel.
            if (!distinctMultiTouch)
                switcher.toggleShift();
            break;
        case Keyboard.CODE_SWITCH_ALPHA_SYMBOL:
            // Symbol key is handled in onPress() when device has distinct multi-touch panel.
            if (!distinctMultiTouch)
                switcher.changeKeyboardMode();
            break;
        case Keyboard.CODE_CANCEL:
            if (!isShowingOptionDialog()) {
                handleClose();
            }
            break;
        case Keyboard.CODE_SETTINGS:
            onSettingsKeyPressed();
            break;
        case Keyboard.CODE_CAPSLOCK:
            switcher.toggleCapsLock();
            //$FALL-THROUGH$
        case Keyboard.CODE_HAPTIC_AND_AUDIO_FEEDBACK_ONLY:
            // Dummy code for haptic and audio feedbacks.
            vibrate();
            playKeyClick(primaryCode);
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
            if (mSettingsValues.isWordSeparator(primaryCode)) {
                handleSeparator(primaryCode, x, y);
            } else {
                handleCharacter(primaryCode, keyCodes, x, y);
            }
            mExpectingUpdateSelection = true;
            break;
        }
        switcher.onKey(primaryCode);
        // Reset after any single keystroke
        mEnteredText = null;
    }

    @Override
    public void onTextInput(CharSequence text) {
        mVoiceProxy.commitVoiceInput();
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        commitTyped(ic);
        maybeRemovePreviousPeriod(ic, text);
        ic.commitText(text, 1);
        ic.endBatchEdit();
        mKeyboardSwitcher.updateShiftState();
        mKeyboardSwitcher.onKey(Keyboard.CODE_DUMMY);
        mJustAddedMagicSpace = false;
        mEnteredText = text;
    }

    @Override
    public void onCancelInput() {
        // User released a finger outside any key
        mKeyboardSwitcher.onCancelInput();
    }

    private void handleBackspace(boolean justReplacedDoubleSpace) {
        if (mVoiceProxy.logAndRevertVoiceInput()) return;

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();

        mVoiceProxy.handleBackspace();

        final boolean deleteChar = !mHasUncommittedTypedChars;
        if (mHasUncommittedTypedChars) {
            final int length = mComposingStringBuilder.length();
            if (length > 0) {
                mComposingStringBuilder.delete(length - 1, length);
                mWordComposer.deleteLast();
                final CharSequence textWithUnderline =
                        mComposingStateManager.isAutoCorrectionIndicatorOn()
                                ? SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(
                                            this, mComposingStringBuilder)
                                : mComposingStringBuilder;
                ic.setComposingText(textWithUnderline, 1);
                if (mComposingStringBuilder.length() == 0) {
                    mHasUncommittedTypedChars = false;
                }
                if (1 == length) {
                    // 1 == length means we are about to erase the last character of the word,
                    // so we can show bigrams.
                    mHandler.postUpdateBigramPredictions();
                } else {
                    // length > 1, so we still have letters to deduce a suggestion from.
                    mHandler.postUpdateSuggestions();
                }
            } else {
                ic.deleteSurroundingText(1, 0);
            }
        }
        mHandler.postUpdateShiftKeyState();

        TextEntryState.backspace();
        if (TextEntryState.isUndoCommit()) {
            revertLastWord(ic);
            ic.endBatchEdit();
            return;
        }
        if (justReplacedDoubleSpace) {
            if (revertDoubleSpace(ic)) {
                ic.endBatchEdit();
                return;
            }
        }

        if (mEnteredText != null && sameAsTextBeforeCursor(ic, mEnteredText)) {
            ic.deleteSurroundingText(mEnteredText.length(), 0);
        } else if (deleteChar) {
            if (mSuggestionsView != null && mSuggestionsView.dismissAddToDictionaryHint()) {
                // Go back to the suggestion mode if the user canceled the
                // "Touch again to save".
                // NOTE: In gerenal, we don't revert the word when backspacing
                // from a manual suggestion pick.  We deliberately chose a
                // different behavior only in the case of picking the first
                // suggestion (typed word).  It's intentional to have made this
                // inconsistent with backspacing after selecting other suggestions.
                revertLastWord(ic);
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                if (mDeleteCount > DELETE_ACCELERATE_AT) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
            }
        }
        ic.endBatchEdit();
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

        // True if keyboard is in either chording shift or manual temporary upper case mode.
        final boolean isManualTemporaryUpperCase = mKeyboardSwitcher.isManualTemporaryUpperCase();
        if (EditorInfoCompatUtils.hasFlagNavigateNext(imeOptions)
                && !isManualTemporaryUpperCase) {
            EditorInfoCompatUtils.performEditorActionNext(ic);
        } else if (EditorInfoCompatUtils.hasFlagNavigatePrevious(imeOptions)
                && isManualTemporaryUpperCase) {
            EditorInfoCompatUtils.performEditorActionPrevious(ic);
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes, int x, int y) {
        mVoiceProxy.handleCharacter();

        if (mJustAddedMagicSpace && mSettingsValues.isMagicSpaceStripper(primaryCode)) {
            removeTrailingSpace();
        }

        int code = primaryCode;
        if ((isAlphabet(code) || mSettingsValues.isSymbolExcludedFromWordSeparators(code))
                && isSuggestionsRequested() && !isCursorTouchingWord()) {
            if (!mHasUncommittedTypedChars) {
                mHasUncommittedTypedChars = true;
                mComposingStringBuilder.setLength(0);
                mWordComposer.reset();
                clearSuggestions();
                mComposingStateManager.onFinishComposingText();
            }
        }
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        if (switcher.isShiftedOrShiftLocked()) {
            if (keyCodes == null || keyCodes[0] < Character.MIN_CODE_POINT
                    || keyCodes[0] > Character.MAX_CODE_POINT) {
                return;
            }
            code = keyCodes[0];
            if (switcher.isAlphabetMode() && Character.isLowerCase(code)) {
                // In some locales, such as Turkish, Character.toUpperCase() may return a wrong
                // character because it doesn't take care of locale.
                final String upperCaseString = new String(new int[] {code}, 0, 1)
                        .toUpperCase(mSubtypeSwitcher.getInputLocale());
                if (upperCaseString.codePointCount(0, upperCaseString.length()) == 1) {
                    code = upperCaseString.codePointAt(0);
                } else {
                    // Some keys, such as [eszett], have upper case as multi-characters.
                    onTextInput(upperCaseString);
                    return;
                }
            }
        }
        if (mHasUncommittedTypedChars) {
            mComposingStringBuilder.append((char) code);
            mWordComposer.add(code, keyCodes, x, y);
            final InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                // If it's the first letter, make note of auto-caps state
                if (mWordComposer.size() == 1) {
                    mWordComposer.setAutoCapitalized(getCurrentAutoCapsState());
                    mComposingStateManager.onStartComposingText();
                }
                final CharSequence textWithUnderline =
                        mComposingStateManager.isAutoCorrectionIndicatorOn()
                                ? SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(
                                        this, mComposingStringBuilder)
                                : mComposingStringBuilder;
                ic.setComposingText(textWithUnderline, 1);
            }
            mHandler.postUpdateSuggestions();
        } else {
            sendKeyChar((char)code);
        }
        if (mJustAddedMagicSpace && mSettingsValues.isMagicSpaceSwapper(primaryCode)) {
            swapSwapperAndSpace();
        } else {
            mJustAddedMagicSpace = false;
        }

        switcher.updateShiftState();
        if (LatinIME.PERF_DEBUG) measureCps();
        TextEntryState.typedCharacter((char) code, mSettingsValues.isWordSeparator(code), x, y);
    }

    private void handleSeparator(int primaryCode, int x, int y) {
        mVoiceProxy.handleSeparator();
        mComposingStateManager.onFinishComposingText();

        // Should dismiss the "Touch again to save" message when handling separator
        if (mSuggestionsView != null && mSuggestionsView.dismissAddToDictionaryHint()) {
            mHandler.cancelUpdateBigramPredictions();
            mHandler.postUpdateSuggestions();
        }

        boolean pickedDefault = false;
        // Handle separator
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
        }
        if (mHasUncommittedTypedChars) {
            // In certain languages where single quote is a separator, it's better
            // not to auto correct, but accept the typed word. For instance,
            // in Italian dov' should not be expanded to dove' because the elision
            // requires the last vowel to be removed.
            final boolean shouldAutoCorrect = mSettingsValues.mAutoCorrectEnabled
                    && !mInputTypeNoAutoCorrect;
            if (shouldAutoCorrect && primaryCode != Keyboard.CODE_SINGLE_QUOTE) {
                pickedDefault = pickDefaultSuggestion(primaryCode);
            } else {
                commitTyped(ic);
            }
        }

        if (mJustAddedMagicSpace) {
            if (mSettingsValues.isMagicSpaceSwapper(primaryCode)) {
                sendKeyChar((char)primaryCode);
                swapSwapperAndSpace();
            } else {
                if (mSettingsValues.isMagicSpaceStripper(primaryCode)) removeTrailingSpace();
                sendKeyChar((char)primaryCode);
                mJustAddedMagicSpace = false;
            }
        } else {
            sendKeyChar((char)primaryCode);
        }

        if (isSuggestionsRequested() && primaryCode == Keyboard.CODE_SPACE) {
            maybeDoubleSpace();
        }

        TextEntryState.typedCharacter((char) primaryCode, true, x, y);

        if (pickedDefault) {
            CharSequence typedWord = mWordComposer.getTypedWord();
            TextEntryState.backToAcceptedDefault(typedWord);
            if (!TextUtils.isEmpty(typedWord) && !typedWord.equals(mBestWord)) {
                InputConnectionCompatUtils.commitCorrection(
                        ic, mLastSelectionEnd - typedWord.length(), typedWord, mBestWord);
            }
        }
        if (Keyboard.CODE_SPACE == primaryCode) {
            if (!isCursorTouchingWord()) {
                mHandler.cancelUpdateSuggestions();
                mHandler.postUpdateBigramPredictions();
            }
        } else {
            // Set punctuation right away. onUpdateSelection will fire but tests whether it is
            // already displayed or not, so it's okay.
            setPunctuationSuggestions();
        }
        mKeyboardSwitcher.updateShiftState();
        if (ic != null) {
            ic.endBatchEdit();
        }
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
        return mIsSettingsSuggestionStripOn
                && (mCorrectionMode > 0 || isShowingSuggestionsStrip());
    }

    public boolean isShowingPunctuationList() {
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
        if (mSuggestionsView.isShowingAddToDictionaryHint() || TextEntryState.isRecorrecting())
            return true;
        if (!isShowingSuggestionsStrip())
            return false;
        if (mApplicationSpecifiedCompletionOn)
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
                if (LatinImeLogger.sDBG) {
                    Log.d(TAG, "Flip the indicator. " + oldAutoCorrectionIndicator
                            + " -> " + newAutoCorrectionIndicator);
                }
                final CharSequence textWithUnderline = newAutoCorrectionIndicator
                        ? SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(
                                this, mComposingStringBuilder)
                        : mComposingStringBuilder;
                if (!TextUtils.isEmpty(textWithUnderline)) {
                    ic.setComposingText(textWithUnderline, 1);
                }
                mComposingStateManager.setAutoCorrectionIndicatorOn(newAutoCorrectionIndicator);
            }
        }
    }

    public void updateSuggestions() {
        // Check if we have a suggestion engine attached.
        if ((mSuggest == null || !isSuggestionsRequested())
                && !mVoiceProxy.isVoiceInputHighlighted()) {
            return;
        }

        mHandler.cancelUpdateSuggestions();
        mHandler.cancelUpdateBigramPredictions();

        if (!mHasUncommittedTypedChars) {
            setPunctuationSuggestions();
            return;
        }

        final WordComposer wordComposer = mWordComposer;
        // TODO: May need a better way of retrieving previous word
        final InputConnection ic = getCurrentInputConnection();
        final CharSequence prevWord;
        if (null == ic) {
            prevWord = null;
        } else {
            prevWord = EditingUtils.getPreviousWord(ic, mSettingsValues.mWordSeparators);
        }
        // getSuggestedWordBuilder handles gracefully a null value of prevWord
        final SuggestedWords.Builder builder = mSuggest.getSuggestedWordBuilder(
                wordComposer, prevWord, mKeyboardSwitcher.getLatinKeyboard().getProximityInfo());

        boolean autoCorrectionAvailable = !mInputTypeNoAutoCorrect && mSuggest.hasAutoCorrection();
        final CharSequence typedWord = wordComposer.getTypedWord();
        // Here, we want to promote a whitelisted word if exists.
        // TODO: Change this scheme - a boolean is not enough. A whitelisted word may be "valid"
        // but still autocorrected from - in the case the whitelist only capitalizes the word.
        // The whitelist should be case-insensitive, so it's not possible to be consistent with
        // a boolean flag. Right now this is handled with a slight hack in
        // WhitelistDictionary#shouldForciblyAutoCorrectFrom.
        final boolean allowsToBeAutoCorrected = AutoCorrection.allowsToBeAutoCorrected(
                mSuggest.getUnigramDictionaries(), typedWord, preferCapitalization());
        if (mCorrectionMode == Suggest.CORRECTION_FULL
                || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM) {
            autoCorrectionAvailable |= (!allowsToBeAutoCorrected);
        }
        // Don't auto-correct words with multiple capital letter
        autoCorrectionAvailable &= !wordComposer.isMostlyCaps();
        autoCorrectionAvailable &= !TextEntryState.isRecorrecting();

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
                mBestWord = typedWord;
            } else if (suggestedWords.hasAutoCorrectionWord()) {
                mBestWord = suggestedWords.getWord(1);
            } else {
                mBestWord = typedWord;
            }
        } else {
            mBestWord = null;
        }
        setSuggestionStripShown(isSuggestionsStripVisible());
    }

    private boolean pickDefaultSuggestion(int separatorCode) {
        // Complete any pending suggestions query first
        if (mHandler.hasPendingUpdateSuggestions()) {
            mHandler.cancelUpdateSuggestions();
            updateSuggestions();
        }
        if (mBestWord != null && mBestWord.length() > 0) {
            TextEntryState.acceptedDefault(mWordComposer.getTypedWord(), mBestWord, separatorCode);
            mExpectingUpdateSelection = true;
            commitBestWord(mBestWord);
            // Add the word to the user unigram dictionary if it's not a known word
            addToUserUnigramAndBigramDictionaries(mBestWord,
                    UserUnigramDictionary.FREQUENCY_FOR_TYPED);
            return true;
        }
        return false;
    }

    @Override
    public void pickSuggestionManually(int index, CharSequence suggestion) {
        mComposingStateManager.onFinishComposingText();
        SuggestedWords suggestions = mSuggestionsView.getSuggestions();
        mVoiceProxy.flushAndLogAllTextModificationCounters(index, suggestion,
                mSettingsValues.mWordSeparators);

        final boolean recorrecting = TextEntryState.isRecorrecting();
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
        }
        if (mApplicationSpecifiedCompletionOn && mApplicationSpecifiedCompletions != null
                && index >= 0 && index < mApplicationSpecifiedCompletions.length) {
            if (ic != null) {
                final CompletionInfo completionInfo = mApplicationSpecifiedCompletions[index];
                ic.commitCompletion(completionInfo);
            }
            mCommittedLength = suggestion.length();
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
            // Find out whether the previous character is a space. If it is, as a special case
            // for punctuation entered through the suggestion strip, it should be considered
            // a magic space even if it was a normal space. This is meant to help in case the user
            // pressed space on purpose of displaying the suggestion strip punctuation.
            final int rawPrimaryCode = suggestion.charAt(0);
            // Maybe apply the "bidi mirrored" conversions for parentheses
            final LatinKeyboard keyboard = mKeyboardSwitcher.getLatinKeyboard();
            final int primaryCode = Key.getRtlParenthesisCode(
                    rawPrimaryCode, keyboard.mIsRtlKeyboard);

            final CharSequence beforeText = ic != null ? ic.getTextBeforeCursor(1, 0) : "";
            final int toLeft = (ic == null || TextUtils.isEmpty(beforeText))
                    ? 0 : beforeText.charAt(0);
            final boolean oldMagicSpace = mJustAddedMagicSpace;
            if (Keyboard.CODE_SPACE == toLeft) mJustAddedMagicSpace = true;
            onCodeInput(primaryCode, new int[] { primaryCode },
                    KeyboardActionListener.NOT_A_TOUCH_COORDINATE,
                    KeyboardActionListener.NOT_A_TOUCH_COORDINATE);
            mJustAddedMagicSpace = oldMagicSpace;
            if (ic != null) {
                ic.endBatchEdit();
            }
            return;
        }
        if (!mHasUncommittedTypedChars) {
            // If we are not composing a word, then it was a suggestion inferred from
            // context - no user input. We should reset the word composer.
            mWordComposer.reset();
        }
        mExpectingUpdateSelection = true;
        commitBestWord(suggestion);
        // Add the word to the auto dictionary if it's not a known word
        if (index == 0) {
            addToUserUnigramAndBigramDictionaries(suggestion,
                    UserUnigramDictionary.FREQUENCY_FOR_PICKED);
        } else {
            addToOnlyBigramDictionary(suggestion, 1);
        }
        LatinImeLogger.logOnManualSuggestion(mComposingStringBuilder.toString(),
                suggestion.toString(), index, suggestions.mWords);
        TextEntryState.acceptedSuggestion(mComposingStringBuilder.toString(), suggestion);
        // Follow it with a space
        if (mShouldInsertMagicSpace && !recorrecting) {
            sendMagicSpace();
        }

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

        if (!recorrecting) {
            // Fool the state watcher so that a subsequent backspace will not do a revert, unless
            // we just did a correction, in which case we need to stay in
            // TextEntryState.State.PICKED_SUGGESTION state.
            TextEntryState.typedCharacter((char) Keyboard.CODE_SPACE, true,
                    WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
        }
        if (!showingAddToDictionaryHint) {
            // If we're not showing the "Touch again to save", then show corrections again.
            // In case the cursor position doesn't change, make sure we show the suggestions again.
            updateBigramPredictions();
            // Updating the predictions right away may be slow and feel unresponsive on slower
            // terminals. On the other hand if we just postUpdateBigramPredictions() it will
            // take a noticeable delay to update them which may feel uneasy.
        }
        if (showingAddToDictionaryHint) {
            if (mIsUserDictionaryAvaliable) {
                mSuggestionsView.showAddToDictionaryHint(suggestion);
            } else {
                mHandler.postUpdateSuggestions();
            }
        }
        if (ic != null) {
            ic.endBatchEdit();
        }
    }

    /**
     * Commits the chosen word to the text field and saves it for later retrieval.
     */
    private void commitBestWord(CharSequence bestWord) {
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        if (!switcher.isKeyboardAvailable())
            return;
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
        mHasUncommittedTypedChars = false;
        mCommittedLength = bestWord.length();
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
                prevWord, mKeyboardSwitcher.getLatinKeyboard().getProximityInfo());

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

    // "ic" must not null
    private boolean sameAsTextBeforeCursor(final InputConnection ic, CharSequence text) {
        CharSequence beforeText = ic.getTextBeforeCursor(text.length(), 0);
        return TextUtils.equals(text, beforeText);
    }

    // "ic" must not null
    private void revertLastWord(final InputConnection ic) {
        if (mHasUncommittedTypedChars || mComposingStringBuilder.length() <= 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            return;
        }

        final CharSequence separator = ic.getTextBeforeCursor(1, 0);
        ic.deleteSurroundingText(1, 0);
        final CharSequence textToTheLeft = ic.getTextBeforeCursor(mCommittedLength, 0);
        ic.deleteSurroundingText(mCommittedLength, 0);

        // Re-insert "separator" only when the deleted character was word separator and the
        // composing text wasn't equal to the auto-corrected text which can be found before
        // the cursor.
        if (!TextUtils.isEmpty(separator)
                && mSettingsValues.isWordSeparator(separator.charAt(0))
                && !TextUtils.equals(mComposingStringBuilder, textToTheLeft)) {
            ic.commitText(mComposingStringBuilder, 1);
            TextEntryState.acceptedTyped(mComposingStringBuilder);
            ic.commitText(separator, 1);
            TextEntryState.typedCharacter(separator.charAt(0), true,
                    WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
            // Clear composing text
            mComposingStringBuilder.setLength(0);
        } else {
            mHasUncommittedTypedChars = true;
            ic.setComposingText(mComposingStringBuilder, 1);
            TextEntryState.backspace();
        }
        mHandler.cancelUpdateBigramPredictions();
        mHandler.postUpdateSuggestions();
    }

    // "ic" must not null
    private boolean revertDoubleSpace(final InputConnection ic) {
        mHandler.cancelDoubleSpacesTimer();
        // Here we test whether we indeed have a period and a space before us. This should not
        // be needed, but it's there just in case something went wrong.
        final CharSequence textBeforeCursor = ic.getTextBeforeCursor(2, 0);
        if (!". ".equals(textBeforeCursor))
            return false;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(2, 0);
        ic.commitText("  ", 1);
        ic.endBatchEdit();
        return true;
    }

    public boolean isWordSeparator(int code) {
        return mSettingsValues.isWordSeparator(code);
    }

    private void sendMagicSpace() {
        sendKeyChar((char)Keyboard.CODE_SPACE);
        mJustAddedMagicSpace = true;
        mKeyboardSwitcher.updateShiftState();
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
        // Reload keyboard because the current language has been changed.
        mKeyboardSwitcher.loadKeyboard(getCurrentInputEditorInfo(), mSettingsValues);
        initSuggest();
        loadSettings();
    }

    @Override
    public void onPress(int primaryCode, boolean withSliding) {
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        if (switcher.isVibrateAndSoundFeedbackRequired()) {
            vibrate();
            playKeyClick(primaryCode);
        }
        final boolean distinctMultiTouch = switcher.hasDistinctMultitouch();
        if (distinctMultiTouch && primaryCode == Keyboard.CODE_SHIFT) {
            switcher.onPressShift(withSliding);
        } else if (distinctMultiTouch && primaryCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
            switcher.onPressSymbol();
        } else {
            switcher.onOtherKeyPressed();
        }
    }

    @Override
    public void onRelease(int primaryCode, boolean withSliding) {
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        // Reset any drag flags in the keyboard
        final boolean distinctMultiTouch = switcher.hasDistinctMultitouch();
        if (distinctMultiTouch && primaryCode == Keyboard.CODE_SHIFT) {
            switcher.onReleaseShift(withSliding);
        } else if (distinctMultiTouch && primaryCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
            switcher.onReleaseSymbol();
        }
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

    // update sound effect volume
    private void updateSoundEffectVolume() {
        final String[] volumePerHardwareList = mResources.getStringArray(R.array.keypress_volumes);
        final String hardwarePrefix = Build.HARDWARE + ",";
        for (final String element : volumePerHardwareList) {
            if (element.startsWith(hardwarePrefix)) {
                mFxVolume = Float.parseFloat(element.substring(element.lastIndexOf(',') + 1));
                break;
            }
        }
    }

    // update flags for silent mode
    private void updateRingerMode() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (mAudioManager == null) return;
        }
        mSilentModeOn = (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL);
    }

    private void updateKeypressVibrationDuration() {
        mKeypressVibrationDuration = Utils.getCurrentVibrationDuration(mPrefs, mResources);
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
            mAudioManager.playSoundEffect(sound, mFxVolume);
        }
    }

    public void vibrate() {
        if (!mSettingsValues.mVibrateOn) {
            return;
        }
        if (mKeypressVibrationDuration < 0) {
            // Go ahead with the system default
            LatinKeyboardView inputView = mKeyboardSwitcher.getKeyboardView();
            if (inputView != null) {
                inputView.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
        } else if (mVibrator != null) {
            mVibrator.vibrate(mKeypressVibrationDuration);
        }
    }

    public WordComposer getCurrentWord() {
        return mWordComposer;
    }

    boolean isSoundOn() {
        return mSettingsValues.mSoundOn && !mSilentModeOn;
    }

    private void updateCorrectionMode() {
        // TODO: cleanup messy flags
        final boolean shouldAutoCorrect = mSettingsValues.mAutoCorrectEnabled
                && !mInputTypeNoAutoCorrect;
        mCorrectionMode = (shouldAutoCorrect && mSettingsValues.mAutoCorrectEnabled)
                ? Suggest.CORRECTION_FULL
                : (shouldAutoCorrect ? Suggest.CORRECTION_BASIC : Suggest.CORRECTION_NONE);
        mCorrectionMode = (mSettingsValues.mBigramSuggestionEnabled && shouldAutoCorrect
                && mSettingsValues.mAutoCorrectEnabled)
                ? Suggest.CORRECTION_FULL_BIGRAM : mCorrectionMode;
        if (mSuggest != null) {
            mSuggest.setCorrectionMode(mCorrectionMode);
        }
    }

    private void updateSuggestionVisibility(final SharedPreferences prefs, final Resources res) {
        final String suggestionVisiblityStr = prefs.getString(
                Settings.PREF_SHOW_SUGGESTIONS_SETTING,
                res.getString(R.string.prefs_suggestion_visibility_default_value));
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
                            mInputMethodId, Intent.FLAG_ACTIVITY_NEW_TASK
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
        p.println("  Keyboard mode = " + mKeyboardSwitcher.getKeyboardMode());
        p.println("  mComposingStringBuilder=" + mComposingStringBuilder.toString());
        p.println("  mIsSuggestionsRequested=" + mIsSettingsSuggestionStripOn);
        p.println("  mCorrectionMode=" + mCorrectionMode);
        p.println("  mHasUncommittedTypedChars=" + mHasUncommittedTypedChars);
        p.println("  mAutoCorrectEnabled=" + mSettingsValues.mAutoCorrectEnabled);
        p.println("  mShouldInsertMagicSpace=" + mShouldInsertMagicSpace);
        p.println("  mApplicationSpecifiedCompletionOn=" + mApplicationSpecifiedCompletionOn);
        p.println("  TextEntryState.state=" + TextEntryState.getState());
        p.println("  mSoundOn=" + mSettingsValues.mSoundOn);
        p.println("  mVibrateOn=" + mSettingsValues.mVibrateOn);
        p.println("  mKeyPreviewPopupOn=" + mSettingsValues.mKeyPreviewPopupOn);
    }

    // Characters per second measurement

    private long mLastCpsTime;
    private static final int CPS_BUFFER_SIZE = 16;
    private long[] mCpsIntervals = new long[CPS_BUFFER_SIZE];
    private int mCpsIndex;

    private void measureCps() {
        long now = System.currentTimeMillis();
        if (mLastCpsTime == 0) mLastCpsTime = now - 100; // Initial
        mCpsIntervals[mCpsIndex] = now - mLastCpsTime;
        mLastCpsTime = now;
        mCpsIndex = (mCpsIndex + 1) % CPS_BUFFER_SIZE;
        long total = 0;
        for (int i = 0; i < CPS_BUFFER_SIZE; i++) total += mCpsIntervals[i];
        System.out.println("CPS = " + ((CPS_BUFFER_SIZE * 1000f) / total));
    }
}
