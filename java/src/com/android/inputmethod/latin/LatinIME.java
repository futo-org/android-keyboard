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
import com.android.inputmethod.deprecated.LanguageSwitcherProxy;
import com.android.inputmethod.deprecated.VoiceProxy;
import com.android.inputmethod.deprecated.recorrection.Recorrection;
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
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;

import java.io.FileDescriptor;
import java.io.PrintWriter;
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

    private Settings.Values mSettingsValues;

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
    private Recorrection mRecorrection;

    private UserDictionary mUserDictionary;
    private UserBigramDictionary mUserBigramDictionary;
    private ContactsDictionary mContactsDictionary;
    private AutoDictionary mAutoDictionary;

    // TODO: Create an inner class to group options and pseudo-options to improve readability.
    // These variables are initialized according to the {@link EditorInfo#inputType}.
    private boolean mShouldInsertMagicSpace;
    private boolean mInputTypeNoAutoCorrect;
    private boolean mIsSettingsSuggestionStripOn;
    private boolean mApplicationSpecifiedCompletionOn;

    private final StringBuilder mComposing = new StringBuilder();
    private WordComposer mWord = new WordComposer();
    private CharSequence mBestWord;
    private boolean mHasUncommittedTypedChars;
    private boolean mHasDictionary;
    // Magic space: a space that should disappear on space/apostrophe insertion, move after the
    // punctuation on punctuation insertion, and become a real space on alpha char insertion.
    private boolean mJustAddedMagicSpace; // This indicates whether the last char is a magic space.

    private int mCorrectionMode;
    private int mCommittedLength;
    private int mOrientation;
    // Keep track of the last selection range to decide if we need to show word alternatives
    private int mLastSelectionStart;
    private int mLastSelectionEnd;

    // Indicates whether the suggestion strip is to be on in landscape
    private boolean mJustAccepted;
    private int mDeleteCount;
    private long mLastKeyTime;

    private AudioManager mAudioManager;
    // Align sound effect volume on music volume
    private static final float FX_VOLUME = -1.0f;
    private boolean mSilentModeOn; // System-wide current configuration

    // TODO: Move this flag to VoiceProxy
    private boolean mConfigurationChanging;

    // Object for reacting to adding/removing a dictionary pack.
    private BroadcastReceiver mDictionaryPackInstallReceiver =
            new DictionaryPackInstallBroadcastReceiver(this);

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private CharSequence mEnteredText;


    public final UIHandler mHandler = new UIHandler();

    public class UIHandler extends Handler {
        private static final int MSG_UPDATE_SUGGESTIONS = 0;
        private static final int MSG_UPDATE_OLD_SUGGESTIONS = 1;
        private static final int MSG_UPDATE_SHIFT_STATE = 2;
        private static final int MSG_VOICE_RESULTS = 3;
        private static final int MSG_FADEOUT_LANGUAGE_ON_SPACEBAR = 4;
        private static final int MSG_DISMISS_LANGUAGE_ON_SPACEBAR = 5;
        private static final int MSG_SPACE_TYPED = 6;
        private static final int MSG_SET_BIGRAM_PREDICTIONS = 7;

        @Override
        public void handleMessage(Message msg) {
            final KeyboardSwitcher switcher = mKeyboardSwitcher;
            final LatinKeyboardView inputView = switcher.getInputView();
            switch (msg.what) {
            case MSG_UPDATE_SUGGESTIONS:
                updateSuggestions();
                break;
            case MSG_UPDATE_OLD_SUGGESTIONS:
                mRecorrection.setRecorrectionSuggestions(mVoiceProxy, mCandidateView, mSuggest,
                        mKeyboardSwitcher, mWord, mHasUncommittedTypedChars, mLastSelectionStart,
                        mLastSelectionEnd, mSettingsValues.mWordSeparators);
                break;
            case MSG_UPDATE_SHIFT_STATE:
                switcher.updateShiftState();
                break;
            case MSG_SET_BIGRAM_PREDICTIONS:
                updateBigramPredictions();
                break;
            case MSG_VOICE_RESULTS:
                mVoiceProxy.handleVoiceResults(preferCapitalization()
                        || (switcher.isAlphabetMode() && switcher.isShiftedOrShiftLocked()));
                break;
            case MSG_FADEOUT_LANGUAGE_ON_SPACEBAR:
                if (inputView != null) {
                    inputView.setSpacebarTextFadeFactor(
                            (1.0f + mSettingsValues.mFinalFadeoutFactorOfLanguageOnSpacebar) / 2,
                            (LatinKeyboard)msg.obj);
                }
                sendMessageDelayed(obtainMessage(MSG_DISMISS_LANGUAGE_ON_SPACEBAR, msg.obj),
                        mSettingsValues.mDurationOfFadeoutLanguageOnSpacebar);
                break;
            case MSG_DISMISS_LANGUAGE_ON_SPACEBAR:
                if (inputView != null) {
                    inputView.setSpacebarTextFadeFactor(
                            mSettingsValues.mFinalFadeoutFactorOfLanguageOnSpacebar,
                            (LatinKeyboard)msg.obj);
                }
                break;
            }
        }

        public void postUpdateSuggestions() {
            removeMessages(MSG_UPDATE_SUGGESTIONS);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTIONS),
                    mSettingsValues.mDelayUpdateSuggestions);
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
                    mSettingsValues.mDelayUpdateOldSuggestions);
        }

        public void cancelUpdateOldSuggestions() {
            removeMessages(MSG_UPDATE_OLD_SUGGESTIONS);
        }

        public void postUpdateShiftKeyState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SHIFT_STATE),
                    mSettingsValues.mDelayUpdateShiftState);
        }

        public void cancelUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE);
        }

        public void postUpdateBigramPredictions() {
            removeMessages(MSG_SET_BIGRAM_PREDICTIONS);
            sendMessageDelayed(obtainMessage(MSG_SET_BIGRAM_PREDICTIONS),
                    mSettingsValues.mDelayUpdateSuggestions);
        }

        public void cancelUpdateBigramPredictions() {
            removeMessages(MSG_SET_BIGRAM_PREDICTIONS);
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
                // The language is always displayed when the delay is negative.
                final boolean needsToDisplayLanguage = localeChanged
                        || mSettingsValues.mDelayBeforeFadeoutLanguageOnSpacebar < 0;
                // The language is never displayed when the delay is zero.
                if (mSettingsValues.mDelayBeforeFadeoutLanguageOnSpacebar != 0) {
                    inputView.setSpacebarTextFadeFactor(needsToDisplayLanguage ? 1.0f
                            : mSettingsValues.mFinalFadeoutFactorOfLanguageOnSpacebar, keyboard);
                }
                // The fadeout animation will start when the delay is positive.
                if (localeChanged && mSettingsValues.mDelayBeforeFadeoutLanguageOnSpacebar > 0) {
                    sendMessageDelayed(obtainMessage(MSG_FADEOUT_LANGUAGE_ON_SPACEBAR, keyboard),
                            mSettingsValues.mDelayBeforeFadeoutLanguageOnSpacebar);
                }
            }
        }

        public void startDoubleSpacesTimer() {
            removeMessages(MSG_SPACE_TYPED);
            sendMessageDelayed(obtainMessage(MSG_SPACE_TYPED),
                    mSettingsValues.mDoubleSpacesTurnIntoPeriodTimeout);
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
        Recorrection.init(this, prefs);

        super.onCreate();

        mImm = InputMethodManagerCompatWrapper.getInstance(this);
        mInputMethodId = Utils.getInputMethodId(mImm, getPackageName());
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mRecorrection = Recorrection.getInstance();

        loadSettings();

        final Resources res = getResources();
        mResources = res;

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
    }

    private void initSuggest() {
        final String localeStr = mSubtypeSwitcher.getInputLocaleStr();
        final Locale keyboardLocale = new Locale(localeStr);

        final Resources res = mResources;
        final Locale savedLocale = Utils.setSystemLocale(res, keyboardLocale);
        if (mSuggest != null) {
            mSuggest.close();
        }

        int mainDicResId = Utils.getMainDictionaryResourceId(res);
        mSuggest = new Suggest(this, mainDicResId, keyboardLocale);
        if (mSettingsValues.mAutoCorrectEnabled) {
            mSuggest.setAutoCorrectionThreshold(mSettingsValues.mAutoCorrectionThreshold);
        }
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

        Utils.setSystemLocale(res, savedLocale);
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

        if (DEBUG) {
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
        mHasUncommittedTypedChars = false;
        mDeleteCount = 0;
        mJustAddedMagicSpace = false;

        loadSettings();
        updateCorrectionMode();
        updateAutoTextEnabled();
        updateSuggestionVisibility(mPrefs, mResources);

        if (mSuggest != null && mSettingsValues.mAutoCorrectEnabled) {
            mSuggest.setAutoCorrectionThreshold(mSettingsValues.mAutoCorrectionThreshold);
         }
        mVoiceProxy.loadSettings(attribute, mPrefs);
        // This will work only when the subtype is not supported.
        LanguageSwitcherProxy.loadSettings();

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

        inputView.setKeyPreviewPopupEnabled(mSettingsValues.mKeyPreviewPopupOn,
                mSettingsValues.mKeyPreviewPopupDismissDelay);
        inputView.setProximityCorrectionEnabled(true);
        // If we just entered a text field, maybe it has some old text that requires correction
        mRecorrection.checkRecorrectionOnStart();
        inputView.setForeground(true);

        voiceIme.onStartInputView(inputView.getWindowToken());

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    private void initializeInputAttributes(EditorInfo attribute) {
        if (attribute == null)
            return;
        final int inputType = attribute.inputType;
        final int variation = inputType & InputType.TYPE_MASK_VARIATION;
        mShouldInsertMagicSpace = false;
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
        KeyboardView inputView = mKeyboardSwitcher.getInputView();
        if (inputView != null) inputView.closing();
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
        if (((mComposing.length() > 0 && mHasUncommittedTypedChars)
                || mVoiceProxy.isVoiceInputHighlighted())
                && (selectionChanged || candidatesCleared)) {
            if (candidatesCleared) {
                // If the composing span has been cleared, save the typed word in the history for
                // recorrection before we reset the candidate strip.  Then, we'll be able to show
                // suggestions for recorrection right away.
                mRecorrection.saveRecorrectionSuggestion(mWord, mComposing);
            }
            mComposing.setLength(0);
            mHasUncommittedTypedChars = false;
            if (isCursorTouchingWord()) {
                mHandler.cancelUpdateBigramPredictions();
                mHandler.postUpdateSuggestions();
            } else {
                setPunctuationSuggestions();
            }
            TextEntryState.reset();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
            mVoiceProxy.setVoiceInputHighlighted(false);
        } else if (!mHasUncommittedTypedChars && !mJustAccepted) {
            if (TextEntryState.isAcceptedDefault() || TextEntryState.isSpaceAfterPicked()) {
                if (TextEntryState.isAcceptedDefault())
                    TextEntryState.reset();
                mJustAddedMagicSpace = false; // The user moved the cursor.
            }
        }
        mJustAccepted = false;
        mHandler.postUpdateShiftKeyState();

        // Make a note of the cursor position
        mLastSelectionStart = newSelStart;
        mLastSelectionEnd = newSelEnd;

        mRecorrection.updateRecorrectionSelection(mKeyboardSwitcher,
                mCandidateView, candidatesStart, candidatesEnd, newSelStart,
                newSelEnd, oldSelStart, mLastSelectionStart,
                mLastSelectionEnd, mHasUncommittedTypedChars);
    }

    public void setLastSelection(int start, int end) {
        mLastSelectionStart = start;
        mLastSelectionEnd = end;
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
        if (mRecorrection.isRecorrectionEnabled() && isSuggestionsRequested()) return;

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
        if (mRecorrection.isRecorrectionEnabled() && isSuggestionsRequested()) return;

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
        mRecorrection.clearWordsInHistory();
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
                super.setCandidatesViewShown(shouldShowCandidates);
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
        if (inputView == null)
            return;
        final int containerHeight = mCandidateViewContainer.getHeight();
        int touchY = containerHeight;
        // Need to set touchable region only if input view is being shown
        if (mKeyboardSwitcher.isInputViewShown()) {
            if (mCandidateViewContainer.getVisibility() == View.VISIBLE) {
                touchY -= mCandidateStripHeight;
            }
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
        outInsets.contentTopInsets = touchY;
        outInsets.visibleTopInsets = touchY;
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
        if (mHasUncommittedTypedChars) {
            mHasUncommittedTypedChars = false;
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
            if (!mSettingsValues.mEnableShowSubtypeSettings) {
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
        final boolean distinctMultiTouch = switcher.hasDistinctMultitouch();
        switch (primaryCode) {
        case Keyboard.CODE_DELETE:
            handleBackspace();
            mDeleteCount++;
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
        case Keyboard.CODE_SETTINGS_LONGPRESS:
            onSettingsKeyLongPressed();
            break;
        case LatinKeyboard.CODE_NEXT_LANGUAGE:
            toggleLanguage(true);
            break;
        case LatinKeyboard.CODE_PREV_LANGUAGE:
            toggleLanguage(false);
            break;
        case Keyboard.CODE_CAPSLOCK:
            switcher.toggleCapsLock();
            break;
        case Keyboard.CODE_SHORTCUT:
            mSubtypeSwitcher.switchToShortcutIME();
            break;
        case Keyboard.CODE_TAB:
            handleTab();
            break;
        default:
            if (mSettingsValues.isWordSeparator(primaryCode)) {
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
        mRecorrection.abortRecorrection(false);
        ic.beginBatchEdit();
        commitTyped(ic);
        maybeRemovePreviousPeriod(text);
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

    private void handleBackspace() {
        if (mVoiceProxy.logAndRevertVoiceInput()) return;

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();

        mVoiceProxy.handleBackspace();

        boolean deleteChar = false;
        if (mHasUncommittedTypedChars) {
            final int length = mComposing.length();
            if (length > 0) {
                mComposing.delete(length - 1, length);
                mWord.deleteLast();
                ic.setComposingText(mComposing, 1);
                if (mComposing.length() == 0) {
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

    private void handleCharacter(int primaryCode, int[] keyCodes, int x, int y) {
        mVoiceProxy.handleCharacter();

        if (mJustAddedMagicSpace && mSettingsValues.isMagicSpaceStripper(primaryCode)) {
            removeTrailingSpace();
        }

        if (mLastSelectionStart == mLastSelectionEnd) {
            mRecorrection.abortRecorrection(false);
        }

        int code = primaryCode;
        if (isAlphabet(code) && isSuggestionsRequested() && !isCursorTouchingWord()) {
            if (!mHasUncommittedTypedChars) {
                mHasUncommittedTypedChars = true;
                mComposing.setLength(0);
                mRecorrection.saveRecorrectionSuggestion(mWord, mBestWord);
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
        if (mHasUncommittedTypedChars) {
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

        // Should dismiss the "Touch again to save" message when handling separator
        if (mCandidateView != null && mCandidateView.dismissAddToDictionaryHint()) {
            mHandler.cancelUpdateBigramPredictions();
            mHandler.postUpdateSuggestions();
        }

        boolean pickedDefault = false;
        // Handle separator
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
            mRecorrection.abortRecorrection(false);
        }
        if (mHasUncommittedTypedChars) {
            // In certain languages where single quote is a separator, it's better
            // not to auto correct, but accept the typed word. For instance,
            // in Italian dov' should not be expanded to dove' because the elision
            // requires the last vowel to be removed.
            final boolean shouldAutoCorrect =
                    (mSettingsValues.mAutoCorrectEnabled || mSettingsValues.mQuickFixes)
                    && !mInputTypeNoAutoCorrect && mHasDictionary;
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
            CharSequence typedWord = mWord.getTypedWord();
            TextEntryState.backToAcceptedDefault(typedWord);
            if (!TextUtils.isEmpty(typedWord) && !typedWord.equals(mBestWord)) {
                InputConnectionCompatUtils.commitCorrection(
                        ic, mLastSelectionEnd - typedWord.length(), typedWord, mBestWord);
                if (mCandidateView != null)
                    mCandidateView.onAutoCorrectionInverted(mBestWord);
            }
        }
        if (Keyboard.CODE_SPACE == primaryCode) {
            if (!isCursorTouchingWord()) {
                mHandler.cancelUpdateSuggestions();
                mHandler.cancelUpdateOldSuggestions();
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
        LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
        if (inputView != null)
            inputView.closing();
    }

    public boolean isSuggestionsRequested() {
        return mIsSettingsSuggestionStripOn
                && (mCorrectionMode > 0 || isShowingSuggestionsStrip());
    }

    public boolean isShowingPunctuationList() {
        return mSettingsValues.mSuggestPuncList == mCandidateView.getSuggestions();
    }

    public boolean isShowingSuggestionsStrip() {
        return (mSuggestionVisibility == SUGGESTION_VISIBILILTY_SHOW_VALUE)
                || (mSuggestionVisibility == SUGGESTION_VISIBILILTY_SHOW_ONLY_PORTRAIT_VALUE
                        && mOrientation == Configuration.ORIENTATION_PORTRAIT);
    }

    public boolean isCandidateStripVisible() {
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

        if (!mHasUncommittedTypedChars) {
            setPunctuationSuggestions();
            return;
        }
        showSuggestions(mWord);
    }

    private void showSuggestions(WordComposer word) {
        // TODO: May need a better way of retrieving previous word
        CharSequence prevWord = EditingUtils.getPreviousWord(getCurrentInputConnection(),
                mSettingsValues.mWordSeparators);
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
        if (typedWord != null) {
            if (builder.size() > 1 || typedWord.length() == 1 || typedWordValid
                    || mCandidateView.isShowingAddToDictionaryHint()) {
                builder.setTypedWordValid(typedWordValid).setHasMinimalSuggestion(
                        correctionAvailable);
            } else {
                final SuggestedWords previousSuggestions = mCandidateView.getSuggestions();
                if (previousSuggestions == mSettingsValues.mSuggestPuncList)
                    return;
                builder.addTypedWordAndPreviousSuggestions(typedWord, previousSuggestions);
            }
        }
        showSuggestions(builder.build(), typedWord);
    }

    public void showSuggestions(SuggestedWords suggestedWords, CharSequence typedWord) {
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
        mVoiceProxy.flushAndLogAllTextModificationCounters(index, suggestion,
                mSettingsValues.mWordSeparators);

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
            final char primaryCode = suggestion.charAt(0);
            final CharSequence beforeText = ic.getTextBeforeCursor(1, 0);
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
            mWord.reset();
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
        if (mShouldInsertMagicSpace && !recorrecting) {
            sendMagicSpace();
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
        }
        if (!showingAddToDictionaryHint) {
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
            mVoiceProxy.rememberReplacedWord(suggestion, mSettingsValues.mWordSeparators);
            ic.commitText(suggestion, 1);
        }
        mRecorrection.saveRecorrectionSuggestion(mWord, suggestion);
        mHasUncommittedTypedChars = false;
        mCommittedLength = suggestion.length();
    }

    private static final WordComposer sEmptyWordComposer = new WordComposer();
    private void updateBigramPredictions() {
        if (mSuggest == null || !isSuggestionsRequested())
            return;

        if (!mSettingsValues.mBigramPredictionEnabled) {
            setPunctuationSuggestions();
            return;
        }

        final CharSequence prevWord = EditingUtils.getThisWord(getCurrentInputConnection(),
                mSettingsValues.mWordSeparators);
        SuggestedWords.Builder builder = mSuggest.getSuggestedWordBuilder(
                mKeyboardSwitcher.getInputView(), sEmptyWordComposer, prevWord);

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
            // We don't want to register as bigrams words separated by a separator.
            // For example "I will, and you too" : we don't want the pair ("will" "and") to be
            // a bigram.
            CharSequence prevWord = EditingUtils.getPreviousWord(getCurrentInputConnection(),
                    mSettingsValues.mWordSeparators);
            if (!TextUtils.isEmpty(prevWord)) {
                mUserBigramDictionary.addBigrams(prevWord.toString(), suggestion.toString());
            }
        }
    }

    public boolean isCursorTouchingWord() {
        InputConnection ic = getCurrentInputConnection();
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

    private boolean sameAsTextBeforeCursor(InputConnection ic, CharSequence text) {
        CharSequence beforeText = ic.getTextBeforeCursor(text.length(), 0);
        return TextUtils.equals(text, beforeText);
    }

    public void revertLastWord(boolean deleteChar) {
        final int length = mComposing.length();
        if (!mHasUncommittedTypedChars && length > 0) {
            final InputConnection ic = getCurrentInputConnection();
            final CharSequence punctuation = ic.getTextBeforeCursor(1, 0);
            if (deleteChar) ic.deleteSurroundingText(1, 0);
            int toDelete = mCommittedLength;
            final CharSequence toTheLeft = ic.getTextBeforeCursor(mCommittedLength, 0);
            if (!TextUtils.isEmpty(toTheLeft)
                    && mSettingsValues.isWordSeparator(toTheLeft.charAt(0))) {
                toDelete--;
            }
            ic.deleteSurroundingText(toDelete, 0);
            // Re-insert punctuation only when the deleted character was word separator and the
            // composing text wasn't equal to the auto-corrected text.
            if (deleteChar
                    && !TextUtils.isEmpty(punctuation)
                    && mSettingsValues.isWordSeparator(punctuation.charAt(0))
                    && !TextUtils.equals(mComposing, toTheLeft)) {
                ic.commitText(mComposing, 1);
                TextEntryState.acceptedTyped(mComposing);
                ic.commitText(punctuation, 1);
                TextEntryState.typedCharacter(punctuation.charAt(0), true,
                        WordComposer.NOT_A_COORDINATE, WordComposer.NOT_A_COORDINATE);
                // Clear composing text
                mComposing.setLength(0);
            } else {
                mHasUncommittedTypedChars = true;
                ic.setComposingText(mComposing, 1);
                TextEntryState.backspace();
            }
            mHandler.cancelUpdateBigramPredictions();
            mHandler.postUpdateSuggestions();
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
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
        loadSettings();
        mKeyboardSwitcher.updateShiftState();
    }

    // "reset" and "next" are used only for USE_SPACEBAR_LANGUAGE_SWITCHER.
    private void toggleLanguage(boolean next) {
        if (mSubtypeSwitcher.useSpacebarLanguageSwitcher()) {
            mSubtypeSwitcher.toggleLanguage(next);
        }
        // The following is necessary because on API levels < 10, we don't get notified when
        // subtype changes.
        onRefreshKeyboard();
     }

    @Override
    public void onSwipeDown() {
        if (mSettingsValues.mSwipeDownDismissKeyboardEnabled)
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

    // update flags for silent mode
    private void updateRingerMode() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (mAudioManager != null) {
            mSilentModeOn = (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL);
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
        if (isSoundOn()) {
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
        if (!mSettingsValues.mVibrateOn) {
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

    boolean isSoundOn() {
        return mSettingsValues.mSoundOn && !mSilentModeOn;
    }

    private void updateCorrectionMode() {
        // TODO: cleanup messy flags
        mHasDictionary = mSuggest != null ? mSuggest.hasMainDictionary() : false;
        final boolean shouldAutoCorrect = (mSettingsValues.mAutoCorrectEnabled
                || mSettingsValues.mQuickFixes) && !mInputTypeNoAutoCorrect && mHasDictionary;
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

    private void updateAutoTextEnabled() {
        if (mSuggest == null) return;
        mSuggest.setQuickFixesEnabled(mSettingsValues.mQuickFixes
                && SubtypeSwitcher.getInstance().isSystemLanguageSameAsInputLanguage());
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
