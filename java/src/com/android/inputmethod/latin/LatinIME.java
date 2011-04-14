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

import com.android.inputmethod.compat.CompatUtils;
import com.android.inputmethod.compat.EditorInfoCompatUtils;
import com.android.inputmethod.compat.InputConnectionCompatUtils;
import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.compat.InputMethodServiceCompatWrapper;
import com.android.inputmethod.compat.InputTypeCompatUtils;
import com.android.inputmethod.compat.VibratorCompatWrapper;
import com.android.inputmethod.deprecated.LanguageSwitcherProxy;
import com.android.inputmethod.deprecated.VoiceProxy;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.LatinKeyboard;
import com.android.inputmethod.keyboard.LatinKeyboardView;

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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodServiceCompatWrapper implements KeyboardActionListener {
    private static final String TAG = LatinIME.class.getSimpleName();
    private static final boolean PERF_DEBUG = false;
    private static final boolean TRACE = false;
    private static boolean DEBUG = LatinImeLogger.sDBG;

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

    private static final int DELAY_UPDATE_SUGGESTIONS = 180;
    private static final int DELAY_UPDATE_OLD_SUGGESTIONS = 300;
    private static final int DELAY_UPDATE_SHIFT_STATE = 300;
    private static final int EXTENDED_TOUCHABLE_REGION_HEIGHT = 100;

    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200;

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

    private View mCandidateViewContainer;
    private int mCandidateStripHeight;
    private CandidateView mCandidateView;
    private Suggest mSuggest;
    private CompletionInfo[] mApplicationSpecifiedCompletions;

    private AlertDialog mOptionsDialog;

    private InputMethodManagerCompatWrapper mImm;
    private Resources mResources;
    private SharedPreferences mPrefs;
    private String mInputMethodId;
    private KeyboardSwitcher mKeyboardSwitcher;
    private SubtypeSwitcher mSubtypeSwitcher;
    private VoiceProxy mVoiceProxy;

    private UserDictionary mUserDictionary;
    private UserBigramDictionary mUserBigramDictionary;
    private ContactsDictionary mContactsDictionary;
    private AutoDictionary mAutoDictionary;

    // These variables are initialized according to the {@link EditorInfo#inputType}.
    private boolean mAutoSpace;
    private boolean mInputTypeNoAutoCorrect;
    private boolean mIsSettingsSuggestionStripOn;
    private boolean mApplicationSpecifiedCompletionOn;

    private AccessibilityUtils mAccessibilityUtils;

    private final StringBuilder mComposing = new StringBuilder();
    private WordComposer mWord = new WordComposer();
    private CharSequence mBestWord;
    private boolean mHasValidSuggestions;
    private boolean mHasDictionary;
    private boolean mJustAddedAutoSpace;
    private boolean mAutoCorrectEnabled;
    private boolean mRecorrectionEnabled;
    private boolean mBigramSuggestionEnabled;
    private boolean mAutoCorrectOn;
    private boolean mVibrateOn;
    private boolean mSoundOn;
    private boolean mPopupOn;
    private boolean mAutoCap;
    private boolean mQuickFixes;
    private boolean mConfigEnableShowSubtypeSettings;
    private boolean mConfigSwipeDownDismissKeyboardEnabled;
    private int mConfigDelayBeforeFadeoutLanguageOnSpacebar;
    private int mConfigDurationOfFadeoutLanguageOnSpacebar;
    private float mConfigFinalFadeoutFactorOfLanguageOnSpacebar;
    private long mConfigDoubleSpacesTurnIntoPeriodTimeout;

    private int mCorrectionMode;
    private int mCommittedLength;
    private int mOrientation;
    // Keep track of the last selection range to decide if we need to show word alternatives
    private int mLastSelectionStart;
    private int mLastSelectionEnd;
    private SuggestedWords mSuggestPuncList;

    // Indicates whether the suggestion strip is to be on in landscape
    private boolean mJustAccepted;
    private int mDeleteCount;
    private long mLastKeyTime;

    private AudioManager mAudioManager;
    // Align sound effect volume on music volume
    private static final float FX_VOLUME = -1.0f;
    private boolean mSilentMode;

    /* package */ String mWordSeparators;
    private String mSentenceSeparators;
    private String mSuggestPuncs;
    // TODO: Move this flag to VoiceProxy
    private boolean mConfigurationChanging;

    // Object for reacting to adding/removing a dictionary pack.
    private BroadcastReceiver mDictionaryPackInstallReceiver =
            new DictionaryPackInstallBroadcastReceiver(this);

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private CharSequence mEnteredText;

    private final ArrayList<WordAlternatives> mWordHistory = new ArrayList<WordAlternatives>();

    public class WordAlternatives {
        private final CharSequence mChosenWord;
        private final WordComposer mWordComposer;

        public WordAlternatives(CharSequence chosenWord, WordComposer wordComposer) {
            mChosenWord = chosenWord;
            mWordComposer = wordComposer;
        }

        public CharSequence getChosenWord() {
            return mChosenWord;
        }

        public CharSequence getOriginalWord() {
            return mWordComposer.getTypedWord();
        }

        public SuggestedWords.Builder getAlternatives() {
            return getTypedSuggestions(mWordComposer);
        }

        @Override
        public int hashCode() {
            return mChosenWord.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CharSequence && TextUtils.equals(mChosenWord, (CharSequence)o);
        }
    }

    public final UIHandler mHandler = new UIHandler();

    public class UIHandler extends Handler {
        private static final int MSG_UPDATE_SUGGESTIONS = 0;
        private static final int MSG_UPDATE_OLD_SUGGESTIONS = 1;
        private static final int MSG_UPDATE_SHIFT_STATE = 2;
        private static final int MSG_VOICE_RESULTS = 3;
        private static final int MSG_FADEOUT_LANGUAGE_ON_SPACEBAR = 4;
        private static final int MSG_DISMISS_LANGUAGE_ON_SPACEBAR = 5;
        private static final int MSG_SPACE_TYPED = 6;

        @Override
        public void handleMessage(Message msg) {
            final KeyboardSwitcher switcher = mKeyboardSwitcher;
            final LatinKeyboardView inputView = switcher.getInputView();
            switch (msg.what) {
            case MSG_UPDATE_SUGGESTIONS:
                updateSuggestions();
                break;
            case MSG_UPDATE_OLD_SUGGESTIONS:
                setOldSuggestions();
                break;
            case MSG_UPDATE_SHIFT_STATE:
                switcher.updateShiftState();
                break;
            case MSG_VOICE_RESULTS:
                mVoiceProxy.handleVoiceResults(preferCapitalization()
                        || (switcher.isAlphabetMode() && switcher.isShiftedOrShiftLocked()));
                break;
            case MSG_FADEOUT_LANGUAGE_ON_SPACEBAR:
                if (inputView != null)
                    inputView.setSpacebarTextFadeFactor(
                            (1.0f + mConfigFinalFadeoutFactorOfLanguageOnSpacebar) / 2,
                            (LatinKeyboard)msg.obj);
                sendMessageDelayed(obtainMessage(MSG_DISMISS_LANGUAGE_ON_SPACEBAR, msg.obj),
                        mConfigDurationOfFadeoutLanguageOnSpacebar);
                break;
            case MSG_DISMISS_LANGUAGE_ON_SPACEBAR:
                if (inputView != null)
                    inputView.setSpacebarTextFadeFactor(
                            mConfigFinalFadeoutFactorOfLanguageOnSpacebar, (LatinKeyboard)msg.obj);
                break;
            }
        }

        public void postUpdateSuggestions() {
            removeMessages(MSG_UPDATE_SUGGESTIONS);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTIONS), DELAY_UPDATE_SUGGESTIONS);
        }

        public void cancelUpdateSuggestions() {
            removeMessages(MSG_UPDATE_SUGGESTIONS);
        }

        public boolean hasPendingUpdateSuggestions() {
            return hasMessages(MSG_UPDATE_SUGGESTIONS);
        }

        public void postUpdateOldSuggestions() {
            removeMessages(MSG_UPDATE_OLD_SUGGESTIONS);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_OLD_SUGGESTIONS),
                    DELAY_UPDATE_OLD_SUGGESTIONS);
        }

        public void cancelUpdateOldSuggestions() {
            removeMessages(MSG_UPDATE_OLD_SUGGESTIONS);
        }

        public void postUpdateShiftKeyState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SHIFT_STATE), DELAY_UPDATE_SHIFT_STATE);
        }

        public void cancelUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
        }

        public void updateVoiceResults() {
            sendMessage(obtainMessage(MSG_VOICE_RESULTS));
        }

        public void startDisplayLanguageOnSpacebar(boolean localeChanged) {
            removeMessages(MSG_FADEOUT_LANGUAGE_ON_SPACEBAR);
            removeMessages(MSG_DISMISS_LANGUAGE_ON_SPACEBAR);
            final LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
            if (inputView != null) {
                final LatinKeyboard keyboard = mKeyboardSwitcher.getLatinKeyboard();
                // The language is never displayed when the delay is zero.
                if (mConfigDelayBeforeFadeoutLanguageOnSpacebar != 0)
                    inputView.setSpacebarTextFadeFactor(localeChanged ? 1.0f
                            : mConfigFinalFadeoutFactorOfLanguageOnSpacebar, keyboard);
                // The language is always displayed when the delay is negative.
                if (localeChanged && mConfigDelayBeforeFadeoutLanguageOnSpacebar > 0) {
                    sendMessageDelayed(obtainMessage(MSG_FADEOUT_LANGUAGE_ON_SPACEBAR, keyboard),
                            mConfigDelayBeforeFadeoutLanguageOnSpacebar);
                }
            }
        }

        public void startDoubleSpacesTimer() {
            removeMessages(MSG_SPACE_TYPED);
            sendMessageDelayed(obtainMessage(MSG_SPACE_TYPED),
                    mConfigDoubleSpacesTurnIntoPeriodTimeout);
        }

        public void cancelDoubleSpacesTimer() {
            removeMessages(MSG_SPACE_TYPED);
        }

        public boolean isAcceptingDoubleSpaces() {
            return hasMessages(MSG_SPACE_TYPED);
        }
    }

    @Override
    public void onCreate() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs = prefs;
        LatinImeLogger.init(this, prefs);
        LanguageSwitcherProxy.init(this, prefs);
        SubtypeSwitcher.init(this, prefs);
        KeyboardSwitcher.init(this, prefs);
        AccessibilityUtils.init(this, prefs);

        super.onCreate();

        mImm = InputMethodManagerCompatWrapper.getInstance(this);
        mInputMethodId = Utils.getInputMethodId(mImm, getPackageName());
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mAccessibilityUtils = AccessibilityUtils.getInstance();

        final Resources res = getResources();
        mResources = res;

        // If the option should not be shown, do not read the recorrection preference
        // but always use the default setting defined in the resources.
        if (res.getBoolean(R.bool.config_enable_show_recorrection_option)) {
            mRecorrectionEnabled = prefs.getBoolean(Settings.PREF_RECORRECTION_ENABLED,
                    res.getBoolean(R.bool.config_default_recorrection_enabled));
        } else {
            mRecorrectionEnabled = res.getBoolean(R.bool.config_default_recorrection_enabled);
        }

        mConfigEnableShowSubtypeSettings = res.getBoolean(
                R.bool.config_enable_show_subtype_settings);
        mConfigSwipeDownDismissKeyboardEnabled = res.getBoolean(
                R.bool.config_swipe_down_dismiss_keyboard_enabled);
        mConfigDelayBeforeFadeoutLanguageOnSpacebar = res.getInteger(
                R.integer.config_delay_before_fadeout_language_on_spacebar);
        mConfigDurationOfFadeoutLanguageOnSpacebar = res.getInteger(
                R.integer.config_duration_of_fadeout_language_on_spacebar);
        mConfigFinalFadeoutFactorOfLanguageOnSpacebar = res.getInteger(
                R.integer.config_final_fadeout_percentage_of_language_on_spacebar) / 100.0f;
        mConfigDoubleSpacesTurnIntoPeriodTimeout = res.getInteger(
                R.integer.config_double_spaces_turn_into_period_timeout);

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

        mOrientation = res.getConfiguration().orientation;
        initSuggestPuncList();

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
    }

    private void initSuggest() {
        final String localeStr = mSubtypeSwitcher.getInputLocaleStr();
        final Locale keyboardLocale = new Locale(localeStr);

        final Locale savedLocale = mSubtypeSwitcher.changeSystemLocale(keyboardLocale);
        if (mSuggest != null) {
            mSuggest.close();
        }
        final SharedPreferences prefs = mPrefs;
        mQuickFixes = isQuickFixesEnabled(prefs);

        final Resources res = mResources;
        int mainDicResId = Utils.getMainDictionaryResourceId(res);
        mSuggest = new Suggest(this, mainDicResId, keyboardLocale);
        loadAndSetAutoCorrectionThreshold(prefs);
        updateAutoTextEnabled();

        mUserDictionary = new UserDictionary(this, localeStr);
        mSuggest.setUserDictionary(mUserDictionary);

        mContactsDictionary = new ContactsDictionary(this, Suggest.DIC_CONTACTS);
        mSuggest.setContactsDictionary(mContactsDictionary);

        mAutoDictionary = new AutoDictionary(this, this, localeStr, Suggest.DIC_AUTO);
        mSuggest.setAutoDictionary(mAutoDictionary);

        mUserBigramDictionary = new UserBigramDictionary(this, this, localeStr, Suggest.DIC_USER);
        mSuggest.setUserBigramDictionary(mUserBigramDictionary);

        updateCorrectionMode();
        mWordSeparators = res.getString(R.string.word_separators);
        mSentenceSeparators = res.getString(R.string.sentence_separators);

        mSubtypeSwitcher.changeSystemLocale(savedLocale);
    }

    /* package private */ void resetSuggestMainDict() {
        final String localeStr = mSubtypeSwitcher.getInputLocaleStr();
        final Locale keyboardLocale = new Locale(localeStr);
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
        // If orientation changed while predicting, commit the change
        if (conf.orientation != mOrientation) {
            InputConnection ic = getCurrentInputConnection();
            commitTyped(ic);
            if (ic != null) ic.finishComposingText(); // For voice input
            mOrientation = conf.orientation;
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
    public View onCreateCandidatesView() {
        LayoutInflater inflater = getLayoutInflater();
        LinearLayout container = (LinearLayout)inflater.inflate(R.layout.candidates, null);
        mCandidateViewContainer = container;
        mCandidateStripHeight = (int)mResources.getDimension(R.dimen.candidate_strip_height);
        mCandidateView = (CandidateView) container.findViewById(R.id.candidates);
        mCandidateView.setService(this);
        setCandidatesViewShown(true);
        return container;
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        LatinKeyboardView inputView = switcher.getInputView();

        if(DEBUG) {
            Log.d(TAG, "onStartInputView: " + inputView);
        }
        // In landscape mode, this method gets called without the input view being created.
        if (inputView == null) {
            return;
        }

        mSubtypeSwitcher.updateParametersOnStartInputView();

        TextEntryState.reset();

        // Most such things we decide below in initializeInputAttributesAndGetMode, but we need to
        // know now whether this is a password text field, because we need to know now whether we
        // want to enable the voice button.
        final VoiceProxy voiceIme = mVoiceProxy;
        voiceIme.resetVoiceStates(InputTypeCompatUtils.isPasswordInputType(attribute.inputType)
                || InputTypeCompatUtils.isVisiblePasswordInputType(attribute.inputType));

        initializeInputAttributes(attribute);

        inputView.closing();
        mEnteredText = null;
        mComposing.setLength(0);
        mHasValidSuggestions = false;
        mDeleteCount = 0;
        mJustAddedAutoSpace = false;

        loadSettings(attribute);
        if (mSubtypeSwitcher.isKeyboardMode()) {
            switcher.loadKeyboard(attribute,
                    mSubtypeSwitcher.isShortcutImeEnabled() && voiceIme.isVoiceButtonEnabled(),
                    voiceIme.isVoiceButtonOnPrimary());
            switcher.updateShiftState();
        }

        setCandidatesViewShownInternal(isCandidateStripVisible(), false /* needsInputViewShown */ );
        // Delay updating suggestions because keyboard input view may not be shown at this point.
        mHandler.postUpdateSuggestions();

        updateCorrectionMode();

        final boolean accessibilityEnabled = mAccessibilityUtils.isAccessibilityEnabled();

        inputView.setPreviewEnabled(mPopupOn);
        inputView.setProximityCorrectionEnabled(true);
        inputView.setAccessibilityEnabled(accessibilityEnabled);
        // If we just entered a text field, maybe it has some old text that requires correction
        checkRecorrectionOnStart();
        inputView.setForeground(true);

        voiceIme.onStartInputView(inputView.getWindowToken());

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    private void initializeInputAttributes(EditorInfo attribute) {
        if (attribute == null)
            return;
        final int inputType = attribute.inputType;
        final int variation = inputType & InputType.TYPE_MASK_VARIATION;
        mAutoSpace = false;
        mInputTypeNoAutoCorrect = false;
        mIsSettingsSuggestionStripOn = false;
        mApplicationSpecifiedCompletionOn = false;
        mApplicationSpecifiedCompletions = null;

        if ((inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) {
            mIsSettingsSuggestionStripOn = true;
            // Make sure that passwords are not displayed in candidate view
            if (InputTypeCompatUtils.isPasswordInputType(inputType)
                    || InputTypeCompatUtils.isVisiblePasswordInputType(inputType)) {
                mIsSettingsSuggestionStripOn = false;
            }
            if (InputTypeCompatUtils.isEmailVariation(variation)
                    || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME) {
                mAutoSpace = false;
            } else {
                mAutoSpace = true;
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

    private void checkRecorrectionOnStart() {
        if (!mRecorrectionEnabled) return;

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // There could be a pending composing span.  Clean it up first.
        ic.finishComposingText();

        if (isShowingSuggestionsStrip() && isSuggestionsRequested()) {
            // First get the cursor position. This is required by setOldSuggestions(), so that
            // it can pass the correct range to setComposingRegion(). At this point, we don't
            // have valid values for mLastSelectionStart/End because onUpdateSelection() has
            // not been called yet.
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0; // anything is fine here
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et == null) return;

            mLastSelectionStart = et.startOffset + et.selectionStart;
            mLastSelectionEnd = et.startOffset + et.selectionEnd;

            // Then look for possible corrections in a delayed fashion
            if (!TextUtils.isEmpty(et.text) && isCursorTouchingWord()) {
                mHandler.postUpdateOldSuggestions();
            }
        }
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();

        LatinImeLogger.commit();
        mKeyboardSwitcher.onAutoCorrectionStateChanged(false);

        mVoiceProxy.flushVoiceInputLogs(mConfigurationChanging);

        KeyboardView inputView = mKeyboardSwitcher.getInputView();
        if (inputView != null) inputView.closing();
        if (mAutoDictionary != null) mAutoDictionary.flushPendingWrites();
        if (mUserBigramDictionary != null) mUserBigramDictionary.flushPendingWrites();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        KeyboardView inputView = mKeyboardSwitcher.getInputView();
        if (inputView != null) inputView.setForeground(false);
        // Remove pending messages related to update suggestions
        mHandler.cancelUpdateSuggestions();
        mHandler.cancelUpdateOldSuggestions();
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
        if (((mComposing.length() > 0 && mHasValidSuggestions)
                || mVoiceProxy.isVoiceInputHighlighted())
                && (selectionChanged || candidatesCleared)) {
            if (candidatesCleared) {
                // If the composing span has been cleared, save the typed word in the history for
                // recorrection before we reset the candidate strip.  Then, we'll be able to show
                // suggestions for recorrection right away.
                saveWordInHistory(mComposing);
            }
            mComposing.setLength(0);
            mHasValidSuggestions = false;
            mHandler.postUpdateSuggestions();
            TextEntryState.reset();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
            mVoiceProxy.setVoiceInputHighlighted(false);
        } else if (!mHasValidSuggestions && !mJustAccepted) {
            if (TextEntryState.isAcceptedDefault() || TextEntryState.isSpaceAfterPicked()) {
                if (TextEntryState.isAcceptedDefault())
                    TextEntryState.reset();
                mJustAddedAutoSpace = false; // The user moved the cursor.
            }
        }
        mJustAccepted = false;
        mHandler.postUpdateShiftKeyState();

        // Make a note of the cursor position
        mLastSelectionStart = newSelStart;
        mLastSelectionEnd = newSelEnd;

        if (mRecorrectionEnabled && isShowingSuggestionsStrip()) {
            // Don't look for corrections if the keyboard is not visible
            if (mKeyboardSwitcher.isInputViewShown()) {
                // Check if we should go in or out of correction mode.
                if (isSuggestionsRequested()
                        && (candidatesStart == candidatesEnd || newSelStart != oldSelStart
                                || TextEntryState.isRecorrecting())
                                && (newSelStart < newSelEnd - 1 || !mHasValidSuggestions)) {
                    if (isCursorTouchingWord() || mLastSelectionStart < mLastSelectionEnd) {
                        mHandler.postUpdateOldSuggestions();
                    } else {
                        abortRecorrection(false);
                        // Show the punctuation suggestions list if the current one is not
                        // and if not showing "Touch again to save".
                        if (mCandidateView != null && !isShowingPunctuationList()
                                && !mCandidateView.isShowingAddToDictionaryHint()) {
                            setPunctuationSuggestions();
                        }
                    }
                }
            }
        }
    }

    /**
     * This is called when the user has clicked on the extracted text view,
     * when running in fullscreen mode.  The default implementation hides
     * the candidates view when this happens, but only if the extracted text
     * editor has a vertical scroll bar because its text doesn't fit.
     * Here we override the behavior due to the possibility that a re-correction could
     * cause the candidate strip to disappear and re-appear.
     */
    @Override
    public void onExtractedTextClicked() {
        if (mRecorrectionEnabled && isSuggestionsRequested()) return;

        super.onExtractedTextClicked();
    }

    /**
     * This is called when the user has performed a cursor movement in the
     * extracted text view, when it is running in fullscreen mode.  The default
     * implementation hides the candidates view when a vertical movement
     * happens, but only if the extracted text editor has a vertical scroll bar
     * because its text doesn't fit.
     * Here we override the behavior due to the possibility that a re-correction could
     * cause the candidate strip to disappear and re-appear.
     */
    @Override
    public void onExtractedCursorMovement(int dx, int dy) {
        if (mRecorrectionEnabled && isSuggestionsRequested()) return;

        super.onExtractedCursorMovement(dx, dy);
    }

    @Override
    public void hideWindow() {
        LatinImeLogger.commit();
        mKeyboardSwitcher.onAutoCorrectionStateChanged(false);

        if (TRACE) Debug.stopMethodTracing();
        if (mOptionsDialog != null && mOptionsDialog.isShowing()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
        mVoiceProxy.hideVoiceWindow(mConfigurationChanging);
        mWordHistory.clear();
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
                    .setTypedWordValid(true)
                    .setHasMinimalSuggestion(true);
            // When in fullscreen mode, show completions generated by the application
            setSuggestions(builder.build());
            mBestWord = null;
            setCandidatesViewShown(true);
        }
    }

    private void setCandidatesViewShownInternal(boolean shown, boolean needsInputViewShown) {
        // TODO: Modify this if we support candidates with hard keyboard
        if (onEvaluateInputViewShown()) {
            final boolean shouldShowCandidates = shown
                    && (needsInputViewShown ? mKeyboardSwitcher.isInputViewShown() : true);
            if (isExtractViewShown()) {
                // No need to have extra space to show the key preview.
                mCandidateViewContainer.setMinimumHeight(0);
                super.setCandidatesViewShown(shown);
            } else {
                // We must control the visibility of the suggestion strip in order to avoid clipped
                // key previews, even when we don't show the suggestion strip.
                mCandidateViewContainer.setVisibility(
                        shouldShowCandidates ? View.VISIBLE : View.INVISIBLE);
                super.setCandidatesViewShown(true);
            }
        }
    }

    @Override
    public void setCandidatesViewShown(boolean shown) {
        setCandidatesViewShownInternal(shown, true /* needsInputViewShown */ );
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        final KeyboardView inputView = mKeyboardSwitcher.getInputView();
        // Need to set touchable region only if input view is being shown
        if (inputView != null && mKeyboardSwitcher.isInputViewShown()) {
            final int containerHeight = mCandidateViewContainer.getHeight();
            int touchY = containerHeight;
            if (mCandidateViewContainer.getVisibility() == View.VISIBLE) {
                touchY -= mCandidateStripHeight;
            }
            outInsets.contentTopInsets = touchY;
            outInsets.visibleTopInsets = touchY;
            final int touchWidth = inputView.getWidth();
            final int touchHeight = inputView.getHeight() + containerHeight
                    // Extend touchable region below the keyboard.
                    + EXTENDED_TOUCHABLE_REGION_HEIGHT;
            if (DEBUG) {
                Log.d(TAG, "Touchable region: y=" + touchY + " width=" + touchWidth
                        + " height=" + touchHeight);
            }
            setTouchableRegionCompat(outInsets, 0, touchY, touchWidth, touchHeight);
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        final Resources res = mResources;
        DisplayMetrics dm = res.getDisplayMetrics();
        float displayHeight = dm.heightPixels;
        // If the display is more than X inches high, don't go to fullscreen mode
        float dimen = res.getDimension(R.dimen.max_height_for_fullscreen);
        if (displayHeight > dimen) {
            return false;
        } else {
            return super.onEvaluateFullscreenMode();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            if (event.getRepeatCount() == 0 && mKeyboardSwitcher.getInputView() != null) {
                if (mKeyboardSwitcher.getInputView().handleBack()) {
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
                InputConnection ic = getCurrentInputConnection();
                if (ic != null)
                    ic.sendKeyEvent(newEvent);
                return true;
            }
            break;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void commitTyped(InputConnection inputConnection) {
        if (mHasValidSuggestions) {
            mHasValidSuggestions = false;
            if (mComposing.length() > 0) {
                if (inputConnection != null) {
                    inputConnection.commitText(mComposing, 1);
                }
                mCommittedLength = mComposing.length();
                TextEntryState.acceptedTyped(mComposing);
                addToAutoAndUserBigramDictionaries(mComposing, AutoDictionary.FREQUENCY_FOR_TYPED);
            }
            updateSuggestions();
        }
    }

    public boolean getCurrentAutoCapsState() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (mAutoCap && ic != null && ei != null && ei.inputType != InputType.TYPE_NULL) {
            return ic.getCursorCapsMode(ei.inputType) != 0;
        }
        return false;
    }

    private void swapPunctuationAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == Keyboard.CODE_SPACE
                && isSentenceSeparator(lastTwo.charAt(1))) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(lastTwo.charAt(1) + " ", 1);
            ic.endBatchEdit();
            mKeyboardSwitcher.updateShiftState();
            mJustAddedAutoSpace = true;
        }
    }

    private void reswapPeriodAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && lastThree.charAt(0) == Keyboard.CODE_PERIOD
                && lastThree.charAt(1) == Keyboard.CODE_SPACE
                && lastThree.charAt(2) == Keyboard.CODE_PERIOD) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(3, 0);
            ic.commitText(" ..", 1);
            ic.endBatchEdit();
            mKeyboardSwitcher.updateShiftState();
        }
    }

    private void doubleSpace() {
        if (mCorrectionMode == Suggest.CORRECTION_NONE) return;
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && Character.isLetterOrDigit(lastThree.charAt(0))
                && lastThree.charAt(1) == Keyboard.CODE_SPACE
                && lastThree.charAt(2) == Keyboard.CODE_SPACE
                && mHandler.isAcceptingDoubleSpaces()) {
            mHandler.cancelDoubleSpacesTimer();
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(". ", 1);
            ic.endBatchEdit();
            mKeyboardSwitcher.updateShiftState();
            mJustAddedAutoSpace = true;
        } else {
            mHandler.startDoubleSpacesTimer();
        }
    }

    private void maybeRemovePreviousPeriod(CharSequence text) {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

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
        if (!isShowingOptionDialog()) {
            if (!mConfigEnableShowSubtypeSettings) {
                showSubtypeSelectorAndSettings();
            } else if (Utils.hasMultipleEnabledIMEsOrSubtypes(mImm)) {
                showOptionsMenu();
            } else {
                launchSettings();
            }
        }
    }

    private void onSettingsKeyLongPressed() {
        if (!isShowingOptionDialog()) {
            if (Utils.hasMultipleEnabledIMEsOrSubtypes(mImm)) {
                mImm.showInputMethodPicker();
            } else {
                launchSettings();
            }
        }
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
        final boolean accessibilityEnabled = switcher.isAccessibilityEnabled();
        final boolean distinctMultiTouch = switcher.hasDistinctMultitouch();
        switch (primaryCode) {
        case Keyboard.CODE_DELETE:
            handleBackspace();
            mDeleteCount++;
            LatinImeLogger.logOnDelete();
            break;
        case Keyboard.CODE_SHIFT:
            // Shift key is handled in onPress() when device has distinct multi-touch panel.
            if (!distinctMultiTouch || accessibilityEnabled)
                switcher.toggleShift();
            break;
        case Keyboard.CODE_SWITCH_ALPHA_SYMBOL:
            // Symbol key is handled in onPress() when device has distinct multi-touch panel.
            if (!distinctMultiTouch || accessibilityEnabled)
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
        case Keyboard.CODE_SETTINGS_LONGPRESS:
            onSettingsKeyLongPressed();
            break;
        case Keyboard.CODE_NEXT_LANGUAGE:
            toggleLanguage(true);
            break;
        case Keyboard.CODE_PREV_LANGUAGE:
            toggleLanguage(false);
            break;
        case Keyboard.CODE_CAPSLOCK:
            switcher.toggleCapsLock();
            break;
        case Keyboard.CODE_VOICE:
            mSubtypeSwitcher.switchToShortcutIME();
            break;
        case Keyboard.CODE_TAB:
            handleTab();
            break;
        default:
            if (primaryCode != Keyboard.CODE_ENTER) {
                mJustAddedAutoSpace = false;
            }
            if (isWordSeparator(primaryCode)) {
                handleSeparator(primaryCode, x, y);
            } else {
                handleCharacter(primaryCode, keyCodes, x, y);
            }
        }
        switcher.onKey(primaryCode);
        // Reset after any single keystroke
        mEnteredText = null;
    }

    @Override
    public void onTextInput(CharSequence text) {
        mVoiceProxy.commitVoiceInput();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        abortRecorrection(false);
        ic.beginBatchEdit();
        commitTyped(ic);
        maybeRemovePreviousPeriod(text);
        ic.commitText(text, 1);
        ic.endBatchEdit();
        mKeyboardSwitcher.updateShiftState();
        mKeyboardSwitcher.onKey(Keyboard.CODE_DUMMY);
        mJustAddedAutoSpace = false;
        mEnteredText = text;
    }

    @Override
    public void onCancelInput() {
        // User released a finger outside any key
        mKeyboardSwitcher.onCancelInput();
    }

    private void handleBackspace() {
        if (mVoiceProxy.logAndRevertVoiceInput()) return;

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();

        mVoiceProxy.handleBackspace();

        boolean deleteChar = false;
        if (mHasValidSuggestions) {
            final int length = mComposing.length();
            if (length > 0) {
                mComposing.delete(length - 1, length);
                mWord.deleteLast();
                ic.setComposingText(mComposing, 1);
                if (mComposing.length() == 0) {
                    mHasValidSuggestions = false;
                }
                mHandler.postUpdateSuggestions();
            } else {
                ic.deleteSurroundingText(1, 0);
            }
        } else {
            deleteChar = true;
        }
        mHandler.postUpdateShiftKeyState();

        TextEntryState.backspace();
        if (TextEntryState.isUndoCommit()) {
            revertLastWord(deleteChar);
            ic.endBatchEdit();
            return;
        }

        if (mEnteredText != null && sameAsTextBeforeCursor(ic, mEnteredText)) {
            ic.deleteSurroundingText(mEnteredText.length(), 0);
        } else if (deleteChar) {
            if (mCandidateView != null && mCandidateView.dismissAddToDictionaryHint()) {
                // Go back to the suggestion mode if the user canceled the
                // "Touch again to save".
                // NOTE: In gerenal, we don't revert the word when backspacing
                // from a manual suggestion pick.  We deliberately chose a
                // different behavior only in the case of picking the first
                // suggestion (typed word).  It's intentional to have made this
                // inconsistent with backspacing after selecting other suggestions.
                revertLastWord(deleteChar);
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
            ic.performEditorAction(EditorInfo.IME_ACTION_NEXT);
        } else if (EditorInfoCompatUtils.hasFlagNavigatePrevious(imeOptions)
                && isManualTemporaryUpperCase) {
            EditorInfoCompatUtils.performEditorActionPrevious(ic);
        }
    }

    private void abortRecorrection(boolean force) {
        if (force || TextEntryState.isRecorrecting()) {
            TextEntryState.onAbortRecorrection();
            setCandidatesViewShown(isCandidateStripVisible());
            getCurrentInputConnection().finishComposingText();
            clearSuggestions();
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes, int x, int y) {
        mVoiceProxy.handleCharacter();

        if (mLastSelectionStart == mLastSelectionEnd && TextEntryState.isRecorrecting()) {
            abortRecorrection(false);
        }

        int code = primaryCode;
        if (isAlphabet(code) && isSuggestionsRequested() && !isCursorTouchingWord()) {
            if (!mHasValidSuggestions) {
                mHasValidSuggestions = true;
                mComposing.setLength(0);
                saveWordInHistory(mBestWord);
                mWord.reset();
                clearSuggestions();
            }
        }
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        if (switcher.isShiftedOrShiftLocked()) {
            if (keyCodes == null || keyCodes[0] < Character.MIN_CODE_POINT
                    || keyCodes[0] > Character.MAX_CODE_POINT) {
                return;
            }
            code = keyCodes[0];
            if (switcher.isAlphabetMode() && Character.isLowerCase(code)) {
                int upperCaseCode = Character.toUpperCase(code);
                if (upperCaseCode != code) {
                    code = upperCaseCode;
                } else {
                    // Some keys, such as [eszett], have upper case as multi-characters.
                    String upperCase = new String(new int[] {code}, 0, 1).toUpperCase();
                    onTextInput(upperCase);
                    return;
                }
            }
        }
        if (mHasValidSuggestions) {
            if (mComposing.length() == 0 && switcher.isAlphabetMode()
                    && switcher.isShiftedOrShiftLocked()) {
                mWord.setFirstCharCapitalized(true);
            }
            mComposing.append((char) code);
            mWord.add(code, keyCodes, x, y);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                // If it's the first letter, make note of auto-caps state
                if (mWord.size() == 1) {
                    mWord.setAutoCapitalized(getCurrentAutoCapsState());
                }
                ic.setComposingText(mComposing, 1);
            }
            mHandler.postUpdateSuggestions();
        } else {
            sendKeyChar((char)code);
        }
        switcher.updateShiftState();
        if (LatinIME.PERF_DEBUG) measureCps();
        TextEntryState.typedCharacter((char) code, isWordSeparator(code), x, y);
    }

    private void handleSeparator(int primaryCode, int x, int y) {
        mVoiceProxy.handleSeparator();

        // Should dismiss the "Touch again to save" message when handling separator
        if (mCandidateView != null && mCandidateView.dismissAddToDictionaryHint()) {
            mHandler.postUpdateSuggestions();
        }

        boolean pickedDefault = false;
        // Handle separator
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
            abortRecorrection(false);
        }
        if (mHasValidSuggestions) {
            // In certain languages where single quote is a separator, it's better
            // not to auto correct, but accept the typed word. For instance,
            // in Italian dov' should not be expanded to dove' because the elision
            // requires the last vowel to be removed.
            if (mAutoCorrectOn && primaryCode != '\'') {
                pickedDefault = pickDefaultSuggestion(primaryCode);
                // Picked the suggestion by the space key.  We consider this
                // as "added an auto space".
                if (primaryCode == Keyboard.CODE_SPACE) {
                    mJustAddedAutoSpace = true;
                }
            } else {
                commitTyped(ic);
            }
        }
        if (mJustAddedAutoSpace && primaryCode == Keyboard.CODE_ENTER) {
            removeTrailingSpace();
            mJustAddedAutoSpace = false;
        }
        sendKeyChar((char)primaryCode);

        // Handle the case of ". ." -> " .." with auto-space if necessary
        // before changing the TextEntryState.
        if (TextEntryState.isPunctuationAfterAccepted() && primaryCode == Keyboard.CODE_PERIOD) {
            reswapPeriodAndSpace();
        }

        TextEntryState.typedCharacter((char) primaryCode, true, x, y);
        if (TextEntryState.isPunctuationAfterAccepted() && primaryCode != Keyboard.CODE_ENTER) {
            swapPunctuationAndSpace();
        } else if (isSuggestionsRequested() && primaryCode == Keyboard.CODE_SPACE) {
            doubleSpace();
        }
        if (pickedDefault) {
            CharSequence typedWord = mWord.getTypedWord();
            TextEntryState.backToAcceptedDefault(typedWord);
            if (!TextUtils.isEmpty(typedWord) && !typedWord.equals(mBestWord)) {
                InputConnectionCompatUtils.commitCorrection(
                        ic,  mLastSelectionEnd - typedWord.length(), typedWord, mBestWord);
                if (mCandidateView != null)
                    mCandidateView.onAutoCorrectionInverted(mBestWord);
            }
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
        LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
        if (inputView != null)
            inputView.closing();
    }

    private void saveWordInHistory(CharSequence result) {
        if (mWord.size() <= 1) {
            return;
        }
        // Skip if result is null. It happens in some edge case.
        if (TextUtils.isEmpty(result)) {
            return;
        }

        // Make a copy of the CharSequence, since it is/could be a mutable CharSequence
        final String resultCopy = result.toString();
        WordAlternatives entry = new WordAlternatives(resultCopy,
                new WordComposer(mWord));
        mWordHistory.add(entry);
    }

    private boolean isSuggestionsRequested() {
        return mIsSettingsSuggestionStripOn
                && (mCorrectionMode > 0 || isShowingSuggestionsStrip());
    }

    private boolean isShowingPunctuationList() {
        return mSuggestPuncList == mCandidateView.getSuggestions();
    }

    private boolean isShowingSuggestionsStrip() {
        return (mSuggestionVisibility == SUGGESTION_VISIBILILTY_SHOW_VALUE)
                || (mSuggestionVisibility == SUGGESTION_VISIBILILTY_SHOW_ONLY_PORTRAIT_VALUE
                        && mOrientation == Configuration.ORIENTATION_PORTRAIT);
    }

    private boolean isCandidateStripVisible() {
        if (mCandidateView == null)
            return false;
        if (mCandidateView.isShowingAddToDictionaryHint() || TextEntryState.isRecorrecting())
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
        View v = mKeyboardSwitcher.getInputView();
        if (v != null) {
            // Confirms that the keyboard view doesn't have parent view.
            ViewParent p = v.getParent();
            if (p != null && p instanceof ViewGroup) {
                ((ViewGroup) p).removeView(v);
            }
            setInputView(v);
        }
        setCandidatesViewShown(isCandidateStripVisible());
        updateInputViewShown();
        mHandler.postUpdateSuggestions();
    }

    public void clearSuggestions() {
        setSuggestions(SuggestedWords.EMPTY);
    }

    public void setSuggestions(SuggestedWords words) {
        if (mVoiceProxy.getAndResetIsShowingHint()) {
             setCandidatesView(mCandidateViewContainer);
        }

        if (mCandidateView != null) {
            mCandidateView.setSuggestions(words);
            if (mCandidateView.isConfigCandidateHighlightFontColorEnabled()) {
                mKeyboardSwitcher.onAutoCorrectionStateChanged(
                        words.hasWordAboveAutoCorrectionScoreThreshold());
            }
        }
    }

    public void updateSuggestions() {
        // Check if we have a suggestion engine attached.
        if ((mSuggest == null || !isSuggestionsRequested())
                && !mVoiceProxy.isVoiceInputHighlighted()) {
            return;
        }

        if (!mHasValidSuggestions) {
            setPunctuationSuggestions();
            return;
        }
        showSuggestions(mWord);
    }

    private SuggestedWords.Builder getTypedSuggestions(WordComposer word) {
        return mSuggest.getSuggestedWordBuilder(mKeyboardSwitcher.getInputView(), word, null);
    }

    private void showCorrections(WordAlternatives alternatives) {
        SuggestedWords.Builder builder = alternatives.getAlternatives();
        builder.setTypedWordValid(false).setHasMinimalSuggestion(false);
        showSuggestions(builder.build(), alternatives.getOriginalWord());
    }

    private void showSuggestions(WordComposer word) {
        // TODO: May need a better way of retrieving previous word
        CharSequence prevWord = EditingUtils.getPreviousWord(getCurrentInputConnection(),
                mWordSeparators);
        SuggestedWords.Builder builder = mSuggest.getSuggestedWordBuilder(
                mKeyboardSwitcher.getInputView(), word, prevWord);

        boolean correctionAvailable = !mInputTypeNoAutoCorrect && mSuggest.hasAutoCorrection();
        final CharSequence typedWord = word.getTypedWord();
        // Here, we want to promote a whitelisted word if exists.
        final boolean typedWordValid = AutoCorrection.isValidWordForAutoCorrection(
                mSuggest.getUnigramDictionaries(), typedWord, preferCapitalization());
        if (mCorrectionMode == Suggest.CORRECTION_FULL
                || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM) {
            correctionAvailable |= typedWordValid;
        }
        // Don't auto-correct words with multiple capital letter
        correctionAvailable &= !word.isMostlyCaps();
        correctionAvailable &= !TextEntryState.isRecorrecting();

        // Basically, we update the suggestion strip only when suggestion count > 1.  However,
        // there is an exception: We update the suggestion strip whenever typed word's length
        // is 1 or typed word is found in dictionary, regardless of suggestion count.  Actually,
        // in most cases, suggestion count is 1 when typed word's length is 1, but we do always
        // need to clear the previous state when the user starts typing a word (i.e. typed word's
        // length == 1).
        if (builder.size() > 1 || typedWord.length() == 1 || typedWordValid
                || mCandidateView.isShowingAddToDictionaryHint()) {
            builder.setTypedWordValid(typedWordValid).setHasMinimalSuggestion(correctionAvailable);
        } else {
            final SuggestedWords previousSuggestions = mCandidateView.getSuggestions();
            if (previousSuggestions == mSuggestPuncList)
                return;
            builder.addTypedWordAndPreviousSuggestions(typedWord, previousSuggestions);
        }
        showSuggestions(builder.build(), typedWord);
    }

    private void showSuggestions(SuggestedWords suggestedWords, CharSequence typedWord) {
        setSuggestions(suggestedWords);
        if (suggestedWords.size() > 0) {
            if (Utils.shouldBlockedBySafetyNetForAutoCorrection(suggestedWords, mSuggest)) {
                mBestWord = typedWord;
            } else if (suggestedWords.hasAutoCorrectionWord()) {
                mBestWord = suggestedWords.getWord(1);
            } else {
                mBestWord = typedWord;
            }
        } else {
            mBestWord = null;
        }
        setCandidatesViewShown(isCandidateStripVisible());
    }

    private boolean pickDefaultSuggestion(int separatorCode) {
        // Complete any pending candidate query first
        if (mHandler.hasPendingUpdateSuggestions()) {
            mHandler.cancelUpdateSuggestions();
            updateSuggestions();
        }
        if (mBestWord != null && mBestWord.length() > 0) {
            TextEntryState.acceptedDefault(mWord.getTypedWord(), mBestWord, separatorCode);
            mJustAccepted = true;
            pickSuggestion(mBestWord);
            // Add the word to the auto dictionary if it's not a known word
            addToAutoAndUserBigramDictionaries(mBestWord, AutoDictionary.FREQUENCY_FOR_TYPED);
            return true;

        }
        return false;
    }

    public void pickSuggestionManually(int index, CharSequence suggestion) {
        SuggestedWords suggestions = mCandidateView.getSuggestions();
        mVoiceProxy.flushAndLogAllTextModificationCounters(index, suggestion, mWordSeparators);

        final boolean recorrecting = TextEntryState.isRecorrecting();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
        }
        if (mApplicationSpecifiedCompletionOn && mApplicationSpecifiedCompletions != null
                && index >= 0 && index < mApplicationSpecifiedCompletions.length) {
            CompletionInfo ci = mApplicationSpecifiedCompletions[index];
            if (ic != null) {
                ic.commitCompletion(ci);
            }
            mCommittedLength = suggestion.length();
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            mKeyboardSwitcher.updateShiftState();
            if (ic != null) {
                ic.endBatchEdit();
            }
            return;
        }

        // If this is a punctuation, apply it through the normal key press
        if (suggestion.length() == 1 && (isWordSeparator(suggestion.charAt(0))
                || isSuggestedPunctuation(suggestion.charAt(0)))) {
            // Word separators are suggested before the user inputs something.
            // So, LatinImeLogger logs "" as a user's input.
            LatinImeLogger.logOnManualSuggestion(
                    "", suggestion.toString(), index, suggestions.mWords);
            final char primaryCode = suggestion.charAt(0);
            onCodeInput(primaryCode, new int[] { primaryCode },
                    KeyboardActionListener.NOT_A_TOUCH_COORDINATE,
                    KeyboardActionListener.NOT_A_TOUCH_COORDINATE);
            if (ic != null) {
                ic.endBatchEdit();
            }
            return;
        }
        mJustAccepted = true;
        pickSuggestion(suggestion);
        // Add the word to the auto dictionary if it's not a known word
        if (index == 0) {
            addToAutoAndUserBigramDictionaries(suggestion, AutoDictionary.FREQUENCY_FOR_PICKED);
        } else {
            addToOnlyBigramDictionary(suggestion, 1);
        }
        LatinImeLogger.logOnManualSuggestion(mComposing.toString(), suggestion.toString(),
                index, suggestions.mWords);
        TextEntryState.acceptedSuggestion(mComposing.toString(), suggestion);
        // Follow it with a space
        if (mAutoSpace && !recorrecting) {
            sendSpace();
            mJustAddedAutoSpace = true;
        }

        // We should show the hint if the user pressed the first entry AND either:
        // - There is no dictionary (we know that because we tried to load it => null != mSuggest
        //   AND mHasDictionary is false)
        // - There is a dictionary and the word is not in it
        // Please note that if mSuggest is null, it means that everything is off: suggestion
        // and correction, so we shouldn't try to show the hint
        // We used to look at mCorrectionMode here, but showing the hint should have nothing
        // to do with the autocorrection setting.
        final boolean showingAddToDictionaryHint = index == 0 && mSuggest != null
                // If there is no dictionary the hint should be shown.
                && (!mHasDictionary
                        // If "suggestion" is not in the dictionary, the hint should be shown.
                        || !AutoCorrection.isValidWord(
                                mSuggest.getUnigramDictionaries(), suggestion, true));

        if (!recorrecting) {
            // Fool the state watcher so that a subsequent backspace will not do a revert, unless
            // we just did a correction, in which case we need to stay in
            // TextEntryState.State.PICKED_SUGGESTION state.
            TextEntryState.typedCharacter((char) Keyboard.CODE_SPACE, true,
                    WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
            setPunctuationSuggestions();
        } else if (!showingAddToDictionaryHint) {
            // If we're not showing the "Touch again to save", then show corrections again.
            // In case the cursor position doesn't change, make sure we show the suggestions again.
            clearSuggestions();
            mHandler.postUpdateOldSuggestions();
        }
        if (showingAddToDictionaryHint) {
            mCandidateView.showAddToDictionaryHint(suggestion);
        }
        if (ic != null) {
            ic.endBatchEdit();
        }
    }

    /**
     * Commits the chosen word to the text field and saves it for later
     * retrieval.
     * @param suggestion the suggestion picked by the user to be committed to
     *            the text field
     */
    private void pickSuggestion(CharSequence suggestion) {
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        if (!switcher.isKeyboardAvailable())
            return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            mVoiceProxy.rememberReplacedWord(suggestion, mWordSeparators);
            ic.commitText(suggestion, 1);
        }
        saveWordInHistory(suggestion);
        mHasValidSuggestions = false;
        mCommittedLength = suggestion.length();
    }

    /**
     * Tries to apply any typed alternatives for the word if we have any cached alternatives,
     * otherwise tries to find new corrections and completions for the word.
     * @param touching The word that the cursor is touching, with position information
     * @return true if an alternative was found, false otherwise.
     */
    private boolean applyTypedAlternatives(EditingUtils.SelectedWord touching) {
        // If we didn't find a match, search for result in typed word history
        WordComposer foundWord = null;
        WordAlternatives alternatives = null;
        // Search old suggestions to suggest re-corrected suggestions.
        for (WordAlternatives entry : mWordHistory) {
            if (TextUtils.equals(entry.getChosenWord(), touching.mWord)) {
                foundWord = entry.mWordComposer;
                alternatives = entry;
                break;
            }
        }
        // If we didn't find a match, at least suggest corrections as re-corrected suggestions.
        if (foundWord == null
                && (AutoCorrection.isValidWord(
                        mSuggest.getUnigramDictionaries(), touching.mWord, true))) {
            foundWord = new WordComposer();
            for (int i = 0; i < touching.mWord.length(); i++) {
                foundWord.add(touching.mWord.charAt(i), new int[] {
                    touching.mWord.charAt(i)
                }, WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
            }
            foundWord.setFirstCharCapitalized(Character.isUpperCase(touching.mWord.charAt(0)));
        }
        // Found a match, show suggestions
        if (foundWord != null || alternatives != null) {
            if (alternatives == null) {
                alternatives = new WordAlternatives(touching.mWord, foundWord);
            }
            showCorrections(alternatives);
            if (foundWord != null) {
                mWord = new WordComposer(foundWord);
            } else {
                mWord.reset();
            }
            return true;
        }
        return false;
    }

    private void setOldSuggestions() {
        if (!InputConnectionCompatUtils.RECORRECTION_SUPPORTED) return;
        mVoiceProxy.setShowingVoiceSuggestions(false);
        if (mCandidateView != null && mCandidateView.isShowingAddToDictionaryHint()) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!mHasValidSuggestions) {
            // Extract the selected or touching text
            EditingUtils.SelectedWord touching = EditingUtils.getWordAtCursorOrSelection(ic,
                    mLastSelectionStart, mLastSelectionEnd, mWordSeparators);

            if (touching != null && touching.mWord.length() > 1) {
                ic.beginBatchEdit();

                if (!mVoiceProxy.applyVoiceAlternatives(touching)
                        && !applyTypedAlternatives(touching)) {
                    abortRecorrection(true);
                } else {
                    TextEntryState.selectedForRecorrection();
                    InputConnectionCompatUtils.underlineWord(ic, touching);
                }

                ic.endBatchEdit();
            } else {
                abortRecorrection(true);
                setPunctuationSuggestions();  // Show the punctuation suggestions list
            }
        } else {
            abortRecorrection(true);
        }
    }

    private void setPunctuationSuggestions() {
        setSuggestions(mSuggestPuncList);
        setCandidatesViewShown(isCandidateStripVisible());
    }

    private void addToAutoAndUserBigramDictionaries(CharSequence suggestion, int frequencyDelta) {
        checkAddToDictionary(suggestion, frequencyDelta, false);
    }

    private void addToOnlyBigramDictionary(CharSequence suggestion, int frequencyDelta) {
        checkAddToDictionary(suggestion, frequencyDelta, true);
    }

    /**
     * Adds to the UserBigramDictionary and/or AutoDictionary
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

        final boolean selectedATypedWordAndItsInAutoDic =
                !selectedANotTypedWord && mAutoDictionary.isValidWord(suggestion);
        final boolean isValidWord = AutoCorrection.isValidWord(
                mSuggest.getUnigramDictionaries(), suggestion, true);
        final boolean needsToAddToAutoDictionary = selectedATypedWordAndItsInAutoDic
                || !isValidWord;
        if (needsToAddToAutoDictionary) {
            mAutoDictionary.addWord(suggestion.toString(), frequencyDelta);
        }

        if (mUserBigramDictionary != null) {
            CharSequence prevWord = EditingUtils.getPreviousWord(getCurrentInputConnection(),
                    mSentenceSeparators);
            if (!TextUtils.isEmpty(prevWord)) {
                mUserBigramDictionary.addBigrams(prevWord.toString(), suggestion.toString());
            }
        }
    }

    private boolean isCursorTouchingWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence toLeft = ic.getTextBeforeCursor(1, 0);
        CharSequence toRight = ic.getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(toLeft)
                && !isWordSeparator(toLeft.charAt(0))
                && !isSuggestedPunctuation(toLeft.charAt(0))) {
            return true;
        }
        if (!TextUtils.isEmpty(toRight)
                && !isWordSeparator(toRight.charAt(0))
                && !isSuggestedPunctuation(toRight.charAt(0))) {
            return true;
        }
        return false;
    }

    private boolean sameAsTextBeforeCursor(InputConnection ic, CharSequence text) {
        CharSequence beforeText = ic.getTextBeforeCursor(text.length(), 0);
        return TextUtils.equals(text, beforeText);
    }

    public void revertLastWord(boolean deleteChar) {
        final int length = mComposing.length();
        if (!mHasValidSuggestions && length > 0) {
            final InputConnection ic = getCurrentInputConnection();
            final CharSequence punctuation = ic.getTextBeforeCursor(1, 0);
            if (deleteChar) ic.deleteSurroundingText(1, 0);
            int toDelete = mCommittedLength;
            final CharSequence toTheLeft = ic.getTextBeforeCursor(mCommittedLength, 0);
            if (!TextUtils.isEmpty(toTheLeft) && isWordSeparator(toTheLeft.charAt(0))) {
                toDelete--;
            }
            ic.deleteSurroundingText(toDelete, 0);
            // Re-insert punctuation only when the deleted character was word separator and the
            // composing text wasn't equal to the auto-corrected text.
            if (deleteChar
                    && !TextUtils.isEmpty(punctuation) && isWordSeparator(punctuation.charAt(0))
                    && !TextUtils.equals(mComposing, toTheLeft)) {
                ic.commitText(mComposing, 1);
                TextEntryState.acceptedTyped(mComposing);
                ic.commitText(punctuation, 1);
                TextEntryState.typedCharacter(punctuation.charAt(0), true,
                        WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
                // Clear composing text
                mComposing.setLength(0);
            } else {
                mHasValidSuggestions = true;
                ic.setComposingText(mComposing, 1);
                TextEntryState.backspace();
            }
            mHandler.postUpdateSuggestions();
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
    }

    protected String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    private boolean isSentenceSeparator(int code) {
        return mSentenceSeparators.contains(String.valueOf((char)code));
    }

    private void sendSpace() {
        sendKeyChar((char)Keyboard.CODE_SPACE);
        mKeyboardSwitcher.updateShiftState();
    }

    public boolean preferCapitalization() {
        return mWord.isFirstCharCapitalized();
    }

    // Notify that language or mode have been changed and toggleLanguage will update KeyboardID
    // according to new language or mode.
    public void onRefreshKeyboard() {
        // Reload keyboard because the current language has been changed.
        mKeyboardSwitcher.loadKeyboard(getCurrentInputEditorInfo(),
                mSubtypeSwitcher.isShortcutImeEnabled() && mVoiceProxy.isVoiceButtonEnabled(),
                mVoiceProxy.isVoiceButtonOnPrimary());
        initSuggest();
        mKeyboardSwitcher.updateShiftState();
    }

    // "reset" and "next" are used only for USE_SPACEBAR_LANGUAGE_SWITCHER.
    private void toggleLanguage(boolean next) {
        if (mSubtypeSwitcher.useSpacebarLanguageSwitcher()) {
            mSubtypeSwitcher.toggleLanguage(next);
        }
        onRefreshKeyboard();// no need??
    }

    @Override
    public void onSwipeDown() {
        if (mConfigSwipeDownDismissKeyboardEnabled)
            handleClose();
    }

    @Override
    public void onPress(int primaryCode, boolean withSliding) {
        if (mKeyboardSwitcher.isVibrateAndSoundFeedbackRequired()) {
            vibrate();
            playKeyClick(primaryCode);
        }
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        final boolean distinctMultiTouch = switcher.hasDistinctMultitouch();
        if (distinctMultiTouch && primaryCode == Keyboard.CODE_SHIFT) {
            switcher.onPressShift(withSliding);
        } else if (distinctMultiTouch && primaryCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
            switcher.onPressSymbol();
        } else {
            switcher.onOtherKeyPressed();
        }
        mAccessibilityUtils.onPress(primaryCode, switcher);
    }

    @Override
    public void onRelease(int primaryCode, boolean withSliding) {
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        // Reset any drag flags in the keyboard
        switcher.keyReleased();
        final boolean distinctMultiTouch = switcher.hasDistinctMultitouch();
        if (distinctMultiTouch && primaryCode == Keyboard.CODE_SHIFT) {
            switcher.onReleaseShift(withSliding);
        } else if (distinctMultiTouch && primaryCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
            switcher.onReleaseSymbol();
        }
        mAccessibilityUtils.onRelease(primaryCode, switcher);
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
        }
        if (mAudioManager != null) {
            mSilentMode = (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL);
        }
    }

    private void playKeyClick(int primaryCode) {
        // if mAudioManager is null, we don't have the ringer state yet
        // mAudioManager will be set by updateRingerMode
        if (mAudioManager == null) {
            if (mKeyboardSwitcher.getInputView() != null) {
                updateRingerMode();
            }
        }
        if (mSoundOn && !mSilentMode) {
            // FIXME: Volume and enable should come from UI settings
            // FIXME: These should be triggered after auto-repeat logic
            int sound = AudioManager.FX_KEYPRESS_STANDARD;
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
            }
            mAudioManager.playSoundEffect(sound, FX_VOLUME);
        }
    }

    public void vibrate() {
        if (!mVibrateOn) {
            return;
        }
        LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
        if (inputView != null) {
            inputView.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public void promoteToUserDictionary(String word, int frequency) {
        if (mUserDictionary.isValidWord(word)) return;
        mUserDictionary.addWord(word, frequency);
    }

    public WordComposer getCurrentWord() {
        return mWord;
    }

    public boolean getPopupOn() {
        return mPopupOn;
    }

    private void updateCorrectionMode() {
        // TODO: cleanup messy flags
        mHasDictionary = mSuggest != null ? mSuggest.hasMainDictionary() : false;
        mAutoCorrectOn = (mAutoCorrectEnabled || mQuickFixes)
                && !mInputTypeNoAutoCorrect && mHasDictionary;
        mCorrectionMode = (mAutoCorrectOn && mAutoCorrectEnabled)
                ? Suggest.CORRECTION_FULL
                : (mAutoCorrectOn ? Suggest.CORRECTION_BASIC : Suggest.CORRECTION_NONE);
        mCorrectionMode = (mBigramSuggestionEnabled && mAutoCorrectOn && mAutoCorrectEnabled)
                ? Suggest.CORRECTION_FULL_BIGRAM : mCorrectionMode;
        if (mSuggest != null) {
            mSuggest.setCorrectionMode(mCorrectionMode);
        }
    }

    private void updateAutoTextEnabled() {
        if (mSuggest == null) return;
        mSuggest.setQuickFixesEnabled(mQuickFixes
                && SubtypeSwitcher.getInstance().isSystemLanguageSameAsInputLanguage());
    }

    private void updateSuggestionVisibility(SharedPreferences prefs) {
        final Resources res = mResources;
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
        launchSettings(Settings.class);
    }

    public void launchDebugSettings() {
        launchSettings(DebugSettings.class);
    }

    protected void launchSettings(Class<? extends PreferenceActivity> settingsClass) {
        handleClose();
        Intent intent = new Intent();
        intent.setClass(LatinIME.this, settingsClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void loadSettings(EditorInfo attribute) {
        // Get the settings preferences
        final SharedPreferences prefs = mPrefs;
        final boolean hasVibrator = VibratorCompatWrapper.getInstance(this).hasVibrator();
        mVibrateOn = hasVibrator && prefs.getBoolean(Settings.PREF_VIBRATE_ON, false);
        mSoundOn = prefs.getBoolean(Settings.PREF_SOUND_ON,
                mResources.getBoolean(R.bool.config_default_sound_enabled));

        mPopupOn = isPopupEnabled(prefs);
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true);
        mQuickFixes = isQuickFixesEnabled(prefs);

        mAutoCorrectEnabled = isAutoCorrectEnabled(prefs);
        mBigramSuggestionEnabled = mAutoCorrectEnabled && isBigramSuggestionEnabled(prefs);
        loadAndSetAutoCorrectionThreshold(prefs);

        mVoiceProxy.loadSettings(attribute, prefs);

        updateCorrectionMode();
        updateAutoTextEnabled();
        updateSuggestionVisibility(prefs);

        // This will work only when the subtype is not supported.
        LanguageSwitcherProxy.loadSettings();
    }

    /**
     *  Load Auto correction threshold from SharedPreferences, and modify mSuggest's threshold.
     */
    private void loadAndSetAutoCorrectionThreshold(SharedPreferences sp) {
        // When mSuggest is not initialized, cannnot modify mSuggest's threshold.
        if (mSuggest == null) return;
        // When auto correction setting is turned off, the threshold is ignored.
        if (!isAutoCorrectEnabled(sp)) return;

        final String currentAutoCorrectionSetting = sp.getString(
                Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                mResources.getString(R.string.auto_correction_threshold_mode_index_modest));
        final String[] autoCorrectionThresholdValues = mResources.getStringArray(
                R.array.auto_correction_threshold_values);
        // When autoCrrectionThreshold is greater than 1.0, auto correction is virtually turned off.
        double autoCorrectionThreshold = Double.MAX_VALUE;
        try {
            final int arrayIndex = Integer.valueOf(currentAutoCorrectionSetting);
            if (arrayIndex >= 0 && arrayIndex < autoCorrectionThresholdValues.length) {
                autoCorrectionThreshold = Double.parseDouble(
                        autoCorrectionThresholdValues[arrayIndex]);
            }
        } catch (NumberFormatException e) {
            // Whenever the threshold settings are correct, never come here.
            autoCorrectionThreshold = Double.MAX_VALUE;
            Log.w(TAG, "Cannot load auto correction threshold setting."
                    + " currentAutoCorrectionSetting: " + currentAutoCorrectionSetting
                    + ", autoCorrectionThresholdValues: "
                    + Arrays.toString(autoCorrectionThresholdValues));
        }
        // TODO: This should be refactored :
        //           setAutoCorrectionThreshold should be called outside of this method.
        mSuggest.setAutoCorrectionThreshold(autoCorrectionThreshold);
    }

    private boolean isPopupEnabled(SharedPreferences sp) {
        final boolean showPopupOption = getResources().getBoolean(
                R.bool.config_enable_show_popup_on_keypress_option);
        if (!showPopupOption) return mResources.getBoolean(R.bool.config_default_popup_preview);
        return sp.getBoolean(Settings.PREF_POPUP_ON,
                mResources.getBoolean(R.bool.config_default_popup_preview));
    }

    private boolean isQuickFixesEnabled(SharedPreferences sp) {
        final boolean showQuickFixesOption = mResources.getBoolean(
                R.bool.config_enable_quick_fixes_option);
        if (!showQuickFixesOption) {
            return isAutoCorrectEnabled(sp);
        }
        return sp.getBoolean(Settings.PREF_QUICK_FIXES, mResources.getBoolean(
                R.bool.config_default_quick_fixes));
    }

    private boolean isAutoCorrectEnabled(SharedPreferences sp) {
        final String currentAutoCorrectionSetting = sp.getString(
                Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                mResources.getString(R.string.auto_correction_threshold_mode_index_modest));
        final String autoCorrectionOff = mResources.getString(
                R.string.auto_correction_threshold_mode_index_off);
        return !currentAutoCorrectionSetting.equals(autoCorrectionOff);
    }

    private boolean isBigramSuggestionEnabled(SharedPreferences sp) {
        final boolean showBigramSuggestionsOption = mResources.getBoolean(
                R.bool.config_enable_bigram_suggestions_option);
        if (!showBigramSuggestionsOption) {
            return isAutoCorrectEnabled(sp);
        }
        return sp.getBoolean(Settings.PREF_BIGRAM_SUGGESTIONS, mResources.getBoolean(
                R.bool.config_default_bigram_suggestions));
    }

    private void initSuggestPuncList() {
        if (mSuggestPuncs != null || mSuggestPuncList != null)
            return;
        SuggestedWords.Builder builder = new SuggestedWords.Builder();
        String puncs = mResources.getString(R.string.suggested_punctuations);
        if (puncs != null) {
            for (int i = 0; i < puncs.length(); i++) {
                builder.addWord(puncs.subSequence(i, i + 1));
            }
        }
        mSuggestPuncList = builder.build();
        mSuggestPuncs = puncs;
    }

    private boolean isSuggestedPunctuation(int code) {
        return mSuggestPuncs.contains(String.valueOf((char)code));
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
        showOptionsMenuInternal(title, items, listener);
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
        showOptionsMenuInternal(title, items, listener);
    }

    private void showOptionsMenuInternal(CharSequence title, CharSequence[] items,
            DialogInterface.OnClickListener listener) {
        final IBinder windowToken = mKeyboardSwitcher.getInputView().getWindowToken();
        if (windowToken == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.ic_dialog_keyboard);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setItems(items, listener);
        builder.setTitle(title);
        mOptionsDialog = builder.create();
        mOptionsDialog.setCanceledOnTouchOutside(true);
        Window window = mOptionsDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = windowToken;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mOptionsDialog.show();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
        super.dump(fd, fout, args);

        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        p.println("  Keyboard mode = " + mKeyboardSwitcher.getKeyboardMode());
        p.println("  mComposing=" + mComposing.toString());
        p.println("  mIsSuggestionsRequested=" + mIsSettingsSuggestionStripOn);
        p.println("  mCorrectionMode=" + mCorrectionMode);
        p.println("  mHasValidSuggestions=" + mHasValidSuggestions);
        p.println("  mAutoCorrectOn=" + mAutoCorrectOn);
        p.println("  mAutoSpace=" + mAutoSpace);
        p.println("  mApplicationSpecifiedCompletionOn=" + mApplicationSpecifiedCompletionOn);
        p.println("  TextEntryState.state=" + TextEntryState.getState());
        p.println("  mSoundOn=" + mSoundOn);
        p.println("  mVibrateOn=" + mVibrateOn);
        p.println("  mPopupOn=" + mPopupOn);
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
