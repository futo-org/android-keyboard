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

import com.android.inputmethod.voice.EditingUtil;
import com.android.inputmethod.voice.FieldContext;
import com.android.inputmethod.voice.SettingsUtil;
import com.android.inputmethod.voice.VoiceInput;

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
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.speech.SpeechRecognizer;
import android.text.AutoText;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener,
        VoiceInput.UiListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "LatinIME";
    static final boolean DEBUG = false;
    static final boolean TRACE = false;
    static final boolean VOICE_INSTALLED = true;
    static final boolean ENABLE_VOICE_BUTTON = true;

    private static final String PREF_VIBRATE_ON = "vibrate_on";
    private static final String PREF_SOUND_ON = "sound_on";
    private static final String PREF_AUTO_CAP = "auto_cap";
    private static final String PREF_QUICK_FIXES = "quick_fixes";
    private static final String PREF_SHOW_SUGGESTIONS = "show_suggestions";
    private static final String PREF_AUTO_COMPLETE = "auto_complete";
    private static final String PREF_VOICE_MODE = "voice_mode";

    // Whether or not the user has used voice input before (and thus, whether to show the
    // first-run warning dialog or not).
    private static final String PREF_HAS_USED_VOICE_INPUT = "has_used_voice_input";

    // Whether or not the user has used voice input from an unsupported locale UI before.
    // For example, the user has a Chinese UI but activates voice input.
    private static final String PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE =
            "has_used_voice_input_unsupported_locale";

    // A list of locales which are supported by default for voice input, unless we get a
    // different list from Gservices.
    public static final String DEFAULT_VOICE_INPUT_SUPPORTED_LOCALES =
            "en " +
            "en_US " +
            "en_GB " +
            "en_AU " +
            "en_CA " +
            "en_IE " +
            "en_IN " +
            "en_NZ " +
            "en_SG " +
            "en_ZA ";

    // The private IME option used to indicate that no microphone should be shown for a
    // given text field. For instance this is specified by the search dialog when the
    // dialog is already showing a voice search button.
    private static final String IME_OPTION_NO_MICROPHONE = "nm";

    public static final String PREF_SELECTED_LANGUAGES = "selected_languages";
    public static final String PREF_INPUT_LANGUAGE = "input_language";

    private static final int MSG_UPDATE_SUGGESTIONS = 0;
    private static final int MSG_START_TUTORIAL = 1;
    private static final int MSG_UPDATE_SHIFT_STATE = 2;
    private static final int MSG_VOICE_RESULTS = 3;
    private static final int MSG_START_LISTENING_AFTER_SWIPE = 4;

    // If we detect a swipe gesture within N ms of typing, then swipe is
    // ignored, since it may in fact be two key presses in quick succession.
    private static final long MIN_MILLIS_AFTER_TYPING_BEFORE_SWIPE = 1000;

    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200;

    static final int KEYCODE_ENTER = '\n';
    static final int KEYCODE_SPACE = ' ';
    static final int KEYCODE_PERIOD = '.';

    // Contextual menu positions
    private static final int POS_SETTINGS = 0;
    private static final int POS_METHOD = 1;

    private LatinKeyboardView mInputView;
    private CandidateViewContainer mCandidateViewContainer;
    private CandidateView mCandidateView;
    private Suggest mSuggest;
    private CompletionInfo[] mCompletions;

    private AlertDialog mOptionsDialog;
    private AlertDialog mVoiceWarningDialog;

    KeyboardSwitcher mKeyboardSwitcher;

    private UserDictionary mUserDictionary;
    private ContactsDictionary mContactsDictionary;
    private AutoDictionary mAutoDictionary;

    private Hints mHints;

    Resources mResources;

    private String mInputLocale;
    private String mSystemLocale;
    private LanguageSwitcher mLanguageSwitcher;

    private StringBuilder mComposing = new StringBuilder();
    private WordComposer mWord = new WordComposer();
    private int mCommittedLength;
    private boolean mPredicting;
    private boolean mRecognizing;
    private boolean mAfterVoiceInput;
    private boolean mImmediatelyAfterVoiceInput;
    private boolean mShowingVoiceSuggestions;
    private boolean mImmediatelyAfterVoiceSuggestions;
    private boolean mVoiceInputHighlighted;
    private boolean mEnableVoiceButton;
    private CharSequence mBestWord;
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private boolean mHasDictionary;
    private boolean mAutoSpace;
    private boolean mJustAddedAutoSpace;
    private boolean mAutoCorrectEnabled;
    private boolean mAutoCorrectOn;
    private boolean mCapsLock;
    private boolean mPasswordText;
    private boolean mEmailText;
    private boolean mVibrateOn;
    private boolean mSoundOn;
    private boolean mAutoCap;
    private boolean mQuickFixes;
    private boolean mHasUsedVoiceInput;
    private boolean mHasUsedVoiceInputUnsupportedLocale;
    private boolean mLocaleSupportedForVoiceInput;
    private boolean mShowSuggestions;
    private boolean mSuggestionShouldReplaceCurrentWord;
    private boolean mIsShowingHint;
    private int     mCorrectionMode;
    private boolean mEnableVoice = true;
    private boolean mVoiceOnPrimary;
    private int     mOrientation;
    private List<CharSequence> mSuggestPuncList;

    // Indicates whether the suggestion strip is to be on in landscape
    private boolean mJustAccepted;
    private CharSequence mJustRevertedSeparator;
    private int mDeleteCount;
    private long mLastKeyTime;

    private Tutorial mTutorial;

    private AudioManager mAudioManager;
    // Align sound effect volume on music volume
    private final float FX_VOLUME = -1.0f;
    private boolean mSilentMode;

    private String mWordSeparators;
    private String mSentenceSeparators;
    private VoiceInput mVoiceInput;
    private VoiceResults mVoiceResults = new VoiceResults();
    private long mSwipeTriggerTimeMillis;
    private boolean mConfigurationChanging;

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private CharSequence mEnteredText;

    // For each word, a list of potential replacements, usually from voice.
    private Map<String, List<CharSequence>> mWordToSuggestions =
            new HashMap<String, List<CharSequence>>();

    private class VoiceResults {
        List<String> candidates;
        Map<String, List<CharSequence>> alternatives;
    }

    private boolean mRefreshKeyboardRequired;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_SUGGESTIONS:
                    updateSuggestions();
                    break;
                case MSG_START_TUTORIAL:
                    if (mTutorial == null) {
                        if (mInputView.isShown()) {
                            mTutorial = new Tutorial(LatinIME.this, mInputView);
                            mTutorial.start();
                        } else {
                            // Try again soon if the view is not yet showing
                            sendMessageDelayed(obtainMessage(MSG_START_TUTORIAL), 100);
                        }
                    }
                    break;
                case MSG_UPDATE_SHIFT_STATE:
                    updateShiftKeyState(getCurrentInputEditorInfo());
                    break;
                case MSG_VOICE_RESULTS:
                    handleVoiceResults();
                    break;
                case MSG_START_LISTENING_AFTER_SWIPE:
                    if (mLastKeyTime < mSwipeTriggerTimeMillis) {
                        startListening(true);
                    }
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        //setStatusIcon(R.drawable.ime_qwerty);
        mResources = getResources();
        final Configuration conf = mResources.getConfiguration();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mLanguageSwitcher = new LanguageSwitcher(this);
        mLanguageSwitcher.loadLocales(prefs);
        mKeyboardSwitcher = new KeyboardSwitcher(this, this);
        mKeyboardSwitcher.setLanguageSwitcher(mLanguageSwitcher);
        mSystemLocale = conf.locale.toString();
        mLanguageSwitcher.setSystemLocale(conf.locale);
        String inputLanguage = mLanguageSwitcher.getInputLanguage();
        if (inputLanguage == null) {
            inputLanguage = conf.locale.toString();
        }
        initSuggest(inputLanguage);
        mOrientation = conf.orientation;
        initSuggestPuncList();

        // register to receive ringer mode changes for silent mode
        IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);
        if (VOICE_INSTALLED) {
            mVoiceInput = new VoiceInput(this, this);
            mHints = new Hints(this, new Hints.Display() {
                public void showHint(int viewResource) {
                    LayoutInflater inflater = (LayoutInflater) getSystemService(
                            Context.LAYOUT_INFLATER_SERVICE);
                    View view = inflater.inflate(viewResource, null);
                    setCandidatesView(view);
                    setCandidatesViewShown(true);
                    mIsShowingHint = true;
                }
              });
        }
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    private void initSuggest(String locale) {
        mInputLocale = locale;

        Resources orig = getResources();
        Configuration conf = orig.getConfiguration();
        Locale saveLocale = conf.locale;
        conf.locale = new Locale(locale);
        orig.updateConfiguration(conf, orig.getDisplayMetrics());
        if (mSuggest != null) {
            mSuggest.close();
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        mQuickFixes = sp.getBoolean(PREF_QUICK_FIXES, true);
        mSuggest = new Suggest(this, R.raw.main);
        updateAutoTextEnabled(saveLocale);
        if (mUserDictionary != null) mUserDictionary.close();
        mUserDictionary = new UserDictionary(this, mInputLocale);
        if (mContactsDictionary == null) {
            mContactsDictionary = new ContactsDictionary(this);
        }
        if (mAutoDictionary != null) {
            mAutoDictionary.close();
        }
        mAutoDictionary = new AutoDictionary(this, this, mInputLocale);
        mSuggest.setUserDictionary(mUserDictionary);
        mSuggest.setContactsDictionary(mContactsDictionary);
        mSuggest.setAutoDictionary(mAutoDictionary);
        updateCorrectionMode();
        mWordSeparators = mResources.getString(R.string.word_separators);
        mSentenceSeparators = mResources.getString(R.string.sentence_separators);

        conf.locale = saveLocale;
        orig.updateConfiguration(conf, orig.getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        mUserDictionary.close();
        mContactsDictionary.close();
        unregisterReceiver(mReceiver);
        if (VOICE_INSTALLED) {
            mVoiceInput.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        // If the system locale changes and is different from the saved
        // locale (mSystemLocale), then reload the input locale list from the
        // latin ime settings (shared prefs) and reset the input locale
        // to the first one.
        final String systemLocale = conf.locale.toString();
        if (!TextUtils.equals(systemLocale, mSystemLocale)) {
            mSystemLocale = systemLocale;
            if (mLanguageSwitcher != null) {
                mLanguageSwitcher.loadLocales(
                        PreferenceManager.getDefaultSharedPreferences(this));
                mLanguageSwitcher.setSystemLocale(conf.locale);
                toggleLanguage(true, true);
            } else {
                reloadKeyboards();
            }
        }
        // If orientation changed while predicting, commit the change
        if (conf.orientation != mOrientation) {
            InputConnection ic = getCurrentInputConnection();
            commitTyped(ic);
            if (ic != null) ic.finishComposingText(); // For voice input
            mOrientation = conf.orientation;
            reloadKeyboards();
        }
        mConfigurationChanging = true;
        super.onConfigurationChanged(conf);
        if (mRecognizing) {
            switchToRecognitionStatusView();
        }
        mConfigurationChanging = false;
    }

    @Override
    public View onCreateInputView() {
        mInputView = (LatinKeyboardView) getLayoutInflater().inflate(
                R.layout.input, null);
        mKeyboardSwitcher.setInputView(mInputView);
        mKeyboardSwitcher.makeKeyboards(true);
        mInputView.setOnKeyboardActionListener(this);
        mKeyboardSwitcher.setKeyboardMode(
                KeyboardSwitcher.MODE_TEXT, 0,
                shouldShowVoiceButton(makeFieldContext(), getCurrentInputEditorInfo()));
        return mInputView;
    }

    @Override
    public View onCreateCandidatesView() {
        mKeyboardSwitcher.makeKeyboards(true);
        mCandidateViewContainer = (CandidateViewContainer) getLayoutInflater().inflate(
                R.layout.candidates, null);
        mCandidateViewContainer.initViews();
        mCandidateView = (CandidateView) mCandidateViewContainer.findViewById(R.id.candidates);
        mCandidateView.setService(this);
        setCandidatesViewShown(true);
        return mCandidateViewContainer;
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        // In landscape mode, this method gets called without the input view being created.
        if (mInputView == null) {
            return;
        }

        if (mRefreshKeyboardRequired) {
            mRefreshKeyboardRequired = false;
            toggleLanguage(true, true);
        }

        mKeyboardSwitcher.makeKeyboards(false);

        TextEntryState.newSession(this);

        // Most such things we decide below in the switch statement, but we need to know
        // now whether this is a password text field, because we need to know now (before
        // the switch statement) whether we want to enable the voice button.
        mPasswordText = false;
        int variation = attribute.inputType & EditorInfo.TYPE_MASK_VARIATION;
        if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
            mPasswordText = true;
        }

        mEnableVoiceButton = shouldShowVoiceButton(makeFieldContext(), attribute);
        final boolean enableVoiceButton = mEnableVoiceButton && mEnableVoice;

        mAfterVoiceInput = false;
        mImmediatelyAfterVoiceInput = false;
        mShowingVoiceSuggestions = false;
        mImmediatelyAfterVoiceSuggestions = false;
        mVoiceInputHighlighted = false;
        mWordToSuggestions.clear();
        mInputTypeNoAutoCorrect = false;
        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;
        mCapsLock = false;
        mEmailText = false;
        mEnteredText = null;

        switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_SYMBOLS,
                        attribute.imeOptions, enableVoiceButton);
                break;
            case EditorInfo.TYPE_CLASS_PHONE:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_PHONE,
                        attribute.imeOptions, enableVoiceButton);
                break;
            case EditorInfo.TYPE_CLASS_TEXT:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT,
                        attribute.imeOptions, enableVoiceButton);
                //startPrediction();
                mPredictionOn = true;
                // Make sure that passwords are not displayed in candidate view
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ) {
                    mPredictionOn = false;
                }
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                    mEmailText = true;
                }
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME) {
                    mAutoSpace = false;
                } else {
                    mAutoSpace = true;
                }
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                    mPredictionOn = false;
                    mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_EMAIL,
                            attribute.imeOptions, enableVoiceButton);
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
                    mPredictionOn = false;
                    mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_URL,
                            attribute.imeOptions, enableVoiceButton);
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                    mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_IM,
                            attribute.imeOptions, enableVoiceButton);
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    mPredictionOn = false;
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
                    mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_WEB,
                            attribute.imeOptions, enableVoiceButton);
                    // If it's a browser edit field and auto correct is not ON explicitly, then
                    // disable auto correction, but keep suggestions on.
                    if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                        mInputTypeNoAutoCorrect = true;
                    }
                }

                // If NO_SUGGESTIONS is set, don't do prediction.
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    mPredictionOn = false;
                    mInputTypeNoAutoCorrect = true;
                }
                // If it's not multiline and the autoCorrect flag is not set, then don't correct
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0 &&
                        (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
                    mInputTypeNoAutoCorrect = true;
                }
                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    mPredictionOn = false;
                    mCompletionOn = true && isFullscreenMode();
                }
                updateShiftKeyState(attribute);
                break;
            default:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT,
                        attribute.imeOptions, enableVoiceButton);
                updateShiftKeyState(attribute);
        }
        mInputView.closing();
        mComposing.setLength(0);
        mPredicting = false;
        mDeleteCount = 0;
        mJustAddedAutoSpace = false;
        loadSettings();
        updateShiftKeyState(attribute);

        setCandidatesViewShown(false);
        setSuggestions(null, false, false, false);

        // If the dictionary is not big enough, don't auto correct
        mHasDictionary = mSuggest.hasMainDictionary();

        updateCorrectionMode();

        mInputView.setProximityCorrectionEnabled(true);
        mPredictionOn = mPredictionOn && (mCorrectionMode > 0 || mShowSuggestions);
        checkTutorial(attribute.privateImeOptions);
        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();

        if (VOICE_INSTALLED && !mConfigurationChanging) {
            if (mAfterVoiceInput) {
                mVoiceInput.flushAllTextModificationCounters();
                mVoiceInput.logInputEnded();
            }
            mVoiceInput.flushLogs();
            mVoiceInput.cancel();
        }
        if (mInputView != null) {
            mInputView.closing();
        }
        if (mAutoDictionary != null) mAutoDictionary.flushPendingWrites();
    }

    @Override
    public void onUpdateExtractedText(int token, ExtractedText text) {
        super.onUpdateExtractedText(token, text);
        InputConnection ic = getCurrentInputConnection();
        if (!mImmediatelyAfterVoiceInput && mAfterVoiceInput && ic != null) {
            if (mHints.showPunctuationHintIfNecessary(ic)) {
                mVoiceInput.logPunctuationHintDisplayed();
            }
        }
        mImmediatelyAfterVoiceInput = false;
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
                    + ", nss=" + newSelStart
                    + ", nse=" + newSelEnd
                    + ", cs=" + candidatesStart
                    + ", ce=" + candidatesEnd);
        }

        if (mAfterVoiceInput) {
            mVoiceInput.setCursorPos(newSelEnd);
            mVoiceInput.setSelectionSpan(newSelEnd - newSelStart);
        }

        mSuggestionShouldReplaceCurrentWord = false;
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if ((((mComposing.length() > 0 && mPredicting) || mVoiceInputHighlighted)
                && (newSelStart != candidatesEnd
                    || newSelEnd != candidatesEnd))) {
            mComposing.setLength(0);
            mPredicting = false;
            updateSuggestions();
            TextEntryState.reset();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
            mVoiceInputHighlighted = false;
        } else if (!mPredicting && !mJustAccepted) {
            switch (TextEntryState.getState()) {
                case TextEntryState.STATE_ACCEPTED_DEFAULT:
                    TextEntryState.reset();
                    // fall through
                case TextEntryState.STATE_SPACE_AFTER_PICKED:
                    mJustAddedAutoSpace = false;  // The user moved the cursor.
                    break;
            }
        }
        mJustAccepted = false;
        postUpdateShiftKeyState();

        if (VOICE_INSTALLED) {
            if (mShowingVoiceSuggestions) {
                if (mImmediatelyAfterVoiceSuggestions) {
                    mImmediatelyAfterVoiceSuggestions = false;
                } else {
                    updateSuggestions();
                    mShowingVoiceSuggestions = false;
                }
            }
            if (VoiceInput.ENABLE_WORD_CORRECTIONS) {
                // If we have alternatives for the current word, then show them.
                String word = EditingUtil.getWordAtCursor(
                        getCurrentInputConnection(), getWordSeparators());
                if (word != null && mWordToSuggestions.containsKey(word.trim())) {
                    mSuggestionShouldReplaceCurrentWord = true;
                    final List<CharSequence> suggestions = mWordToSuggestions.get(word.trim());

                    setSuggestions(suggestions, false, true, true);
                    setCandidatesViewShown(true);
                }
            }
        }
    }

    @Override
    public void hideWindow() {
        if (TRACE) Debug.stopMethodTracing();
        if (mOptionsDialog != null && mOptionsDialog.isShowing()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
        if (!mConfigurationChanging) {
            if (mAfterVoiceInput) mVoiceInput.logInputEnded();
            if (mVoiceWarningDialog != null && mVoiceWarningDialog.isShowing()) {
                mVoiceInput.logKeyboardWarningDialogDismissed();
                mVoiceWarningDialog.dismiss();
                mVoiceWarningDialog = null;
            }
            if (VOICE_INSTALLED & mRecognizing) {
                mVoiceInput.cancel();
            }
        }
        super.hideWindow();
        TextEntryState.endSession();
    }

    @Override
    public void onDisplayCompletions(CompletionInfo[] completions) {
        if (false) {
            Log.i("foo", "Received completions:");
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                Log.i("foo", "  #" + i + ": " + completions[i]);
            }
        }
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false, false);
                return;
            }

            List<CharSequence> stringList = new ArrayList<CharSequence>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText());
            }
            //CharSequence typedWord = mWord.getTypedWord();
            setSuggestions(stringList, true, true, true);
            mBestWord = null;
            setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);
        }
    }

    @Override
    public void setCandidatesViewShown(boolean shown) {
        // TODO: Remove this if we support candidates with hard keyboard
        if (onEvaluateInputViewShown()) {
            // Show the candidates view only if input view is showing
            super.setCandidatesViewShown(shown && mInputView != null && mInputView.isShown());
        }
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (!isFullscreenMode()) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    } else if (mTutorial != null) {
                        mTutorial.close();
                        mTutorial = null;
                    }
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // If tutorial is visible, don't allow dpad to work
                if (mTutorial != null) {
                    return true;
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
                // If tutorial is visible, don't allow dpad to work
                if (mTutorial != null) {
                    return true;
                }
                // Enable shift key and DPAD to do selections
                if (mInputView != null && mInputView.isShown() && mInputView.isShifted()) {
                    event = new KeyEvent(event.getDownTime(), event.getEventTime(),
                            event.getAction(), event.getKeyCode(), event.getRepeatCount(),
                            event.getDeviceId(), event.getScanCode(),
                            KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON);
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.sendKeyEvent(event);
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void revertVoiceInput() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText("", 1);
        updateSuggestions();
        mVoiceInputHighlighted = false;
    }

    private void commitVoiceInput() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        updateSuggestions();
        mVoiceInputHighlighted = false;
    }

    private void reloadKeyboards() {
        if (mKeyboardSwitcher == null) {
            mKeyboardSwitcher = new KeyboardSwitcher(this, this);
        }
        mKeyboardSwitcher.setLanguageSwitcher(mLanguageSwitcher);
        if (mInputView != null) {
            mKeyboardSwitcher.setVoiceMode(mEnableVoice && mEnableVoiceButton, mVoiceOnPrimary);
        }
        mKeyboardSwitcher.makeKeyboards(true);
    }

    private void commitTyped(InputConnection inputConnection) {
        if (mPredicting) {
            mPredicting = false;
            if (mComposing.length() > 0) {
                if (inputConnection != null) {
                    inputConnection.commitText(mComposing, 1);
                }
                mCommittedLength = mComposing.length();
                TextEntryState.acceptedTyped(mComposing);
                checkAddToDictionary(mComposing, AutoDictionary.FREQUENCY_FOR_TYPED);
            }
            updateSuggestions();
        }
    }

    private void postUpdateShiftKeyState() {
        mHandler.removeMessages(MSG_UPDATE_SHIFT_STATE);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_SHIFT_STATE), 300);
    }

    public void updateShiftKeyState(EditorInfo attr) {
        InputConnection ic = getCurrentInputConnection();
        if (attr != null && mInputView != null && mKeyboardSwitcher.isAlphabetMode()
                && ic != null) {
            mInputView.setShifted(mCapsLock || getCursorCapsMode(ic, attr) != 0);
        }
    }

    private int getCursorCapsMode(InputConnection ic, EditorInfo attr) {
        int caps = 0;
        EditorInfo ei = getCurrentInputEditorInfo();
        if (mAutoCap && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
            caps = ic.getCursorCapsMode(attr.inputType);
        }
        return caps;
    }

    private void swapPunctuationAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == KEYCODE_SPACE && isSentenceSeparator(lastTwo.charAt(1))) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(lastTwo.charAt(1) + " ", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
            mJustAddedAutoSpace = true;
        }
    }

    private void reswapPeriodAndSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && lastThree.charAt(0) == KEYCODE_PERIOD
                && lastThree.charAt(1) == KEYCODE_SPACE
                && lastThree.charAt(2) == KEYCODE_PERIOD) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(3, 0);
            ic.commitText(" ..", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
    }

    private void doubleSpace() {
        //if (!mAutoPunctuate) return;
        if (mCorrectionMode == Suggest.CORRECTION_NONE) return;
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && Character.isLetterOrDigit(lastThree.charAt(0))
                && lastThree.charAt(1) == KEYCODE_SPACE && lastThree.charAt(2) == KEYCODE_SPACE) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(". ", 1);
            ic.endBatchEdit();
            updateShiftKeyState(getCurrentInputEditorInfo());
            mJustAddedAutoSpace = true;
        }
    }

    private void maybeRemovePreviousPeriod(CharSequence text) {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        // When the text's first character is '.', remove the previous period
        // if there is one.
        CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == KEYCODE_PERIOD
                && text.charAt(0) == KEYCODE_PERIOD) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void removeTrailingSpace() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == KEYCODE_SPACE) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    public boolean addWordToDictionary(String word) {
        mUserDictionary.addWord(word, 128);
        return true;
    }

    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes) {
        long when = SystemClock.uptimeMillis();
        if (primaryCode != Keyboard.KEYCODE_DELETE ||
                when > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                handleBackspace();
                mDeleteCount++;
                break;
            case Keyboard.KEYCODE_SHIFT:
                handleShift();
                break;
            case Keyboard.KEYCODE_CANCEL:
                if (mOptionsDialog == null || !mOptionsDialog.isShowing()) {
                    handleClose();
                }
                break;
            case LatinKeyboardView.KEYCODE_OPTIONS:
                showOptionsMenu();
                break;
            case LatinKeyboardView.KEYCODE_NEXT_LANGUAGE:
                toggleLanguage(false, true);
                break;
            case LatinKeyboardView.KEYCODE_PREV_LANGUAGE:
                toggleLanguage(false, false);
                break;
            case LatinKeyboardView.KEYCODE_SHIFT_LONGPRESS:
                if (mCapsLock) {
                    handleShift();
                } else {
                    toggleCapsLock();
                }
                break;
            case Keyboard.KEYCODE_MODE_CHANGE:
                changeKeyboardMode();
                break;
            case LatinKeyboardView.KEYCODE_VOICE:
                if (VOICE_INSTALLED) {
                    startListening(false /* was a button press, was not a swipe */);
                }
                break;
            case 9 /*Tab*/:
                sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB);
                break;
            default:
                if (primaryCode != KEYCODE_ENTER) {
                    mJustAddedAutoSpace = false;
                }
                if (isWordSeparator(primaryCode)) {
                    handleSeparator(primaryCode);
                } else {
                    handleCharacter(primaryCode, keyCodes);
                }
                // Cancel the just reverted state
                mJustRevertedSeparator = null;
        }
        if (mKeyboardSwitcher.onKey(primaryCode)) {
            changeKeyboardMode();
        }
        // Reset after any single keystroke
        mEnteredText = null;
    }

    public void onText(CharSequence text) {
        if (VOICE_INSTALLED && mVoiceInputHighlighted) {
            commitVoiceInput();
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mPredicting) {
            commitTyped(ic);
        }
        maybeRemovePreviousPeriod(text);
        ic.commitText(text, 1);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
        mJustRevertedSeparator = null;
        mJustAddedAutoSpace = false;
        mEnteredText = text;
    }

    private void handleBackspace() {
        if (VOICE_INSTALLED && mVoiceInputHighlighted) {
            mVoiceInput.incrementTextModificationDeleteCount(
                    mVoiceResults.candidates.get(0).toString().length());
            revertVoiceInput();
            return;
        }
        boolean deleteChar = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        if (mAfterVoiceInput) {
            // Don't log delete if the user is pressing delete at
            // the beginning of the text box (hence not deleting anything)
            if (mVoiceInput.getCursorPos() > 0) {
                // If anything was selected before the delete was pressed, increment the
                // delete count by the length of the selection
                int deleteLen  =  mVoiceInput.getSelectionSpan() > 0 ?
                        mVoiceInput.getSelectionSpan() : 1;
                mVoiceInput.incrementTextModificationDeleteCount(deleteLen);
            }
        }

        if (mPredicting) {
            final int length = mComposing.length();
            if (length > 0) {
                mComposing.delete(length - 1, length);
                mWord.deleteLast();
                ic.setComposingText(mComposing, 1);
                if (mComposing.length() == 0) {
                    mPredicting = false;
                }
                postUpdateSuggestions();
            } else {
                ic.deleteSurroundingText(1, 0);
            }
        } else {
            deleteChar = true;
        }
        postUpdateShiftKeyState();
        TextEntryState.backspace();
        if (TextEntryState.getState() == TextEntryState.STATE_UNDO_COMMIT) {
            revertLastWord(deleteChar);
            return;
        } else if (mEnteredText != null && sameAsTextBeforeCursor(ic, mEnteredText)) {
            ic.deleteSurroundingText(mEnteredText.length(), 0);
        } else if (deleteChar) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            if (mDeleteCount > DELETE_ACCELERATE_AT) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            }
        }
        mJustRevertedSeparator = null;
    }

    private void handleShift() {
        mHandler.removeMessages(MSG_UPDATE_SHIFT_STATE);
        if (mKeyboardSwitcher.isAlphabetMode()) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else {
            mKeyboardSwitcher.toggleShift();
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (VOICE_INSTALLED && mVoiceInputHighlighted) {
            commitVoiceInput();
        }

        if (mAfterVoiceInput) {
            // Assume input length is 1. This assumption fails for smiley face insertions.
            mVoiceInput.incrementTextModificationInsertCount(1);
        }

        if (isAlphabet(primaryCode) && isPredictionOn() && !isCursorTouchingWord()) {
            if (!mPredicting) {
                mPredicting = true;
                mComposing.setLength(0);
                mWord.reset();
            }
        }
        if (mInputView.isShifted()) {
            // TODO: This doesn't work with ß, need to fix it in the next release.
            if (keyCodes == null || keyCodes[0] < Character.MIN_CODE_POINT
                    || keyCodes[0] > Character.MAX_CODE_POINT) {
                return;
            }
            primaryCode = new String(keyCodes, 0, 1).toUpperCase().charAt(0);
        }
        if (mPredicting) {
            if (mInputView.isShifted() && mComposing.length() == 0) {
                mWord.setCapitalized(true);
            }
            mComposing.append((char) primaryCode);
            mWord.add(primaryCode, keyCodes);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                // If it's the first letter, make note of auto-caps state
                if (mWord.size() == 1) {
                    mWord.setAutoCapitalized(
                            getCursorCapsMode(ic, getCurrentInputEditorInfo()) != 0);
                }
                ic.setComposingText(mComposing, 1);
            }
            postUpdateSuggestions();
        } else {
            sendKeyChar((char)primaryCode);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
        measureCps();
        TextEntryState.typedCharacter((char) primaryCode, isWordSeparator(primaryCode));
    }

    private void handleSeparator(int primaryCode) {
        if (VOICE_INSTALLED && mVoiceInputHighlighted) {
            commitVoiceInput();
        }

        if (mAfterVoiceInput){
            // Assume input length is 1. This assumption fails for smiley face insertions.
            mVoiceInput.incrementTextModificationInsertPunctuationCount(1);
        }

        boolean pickedDefault = false;
        // Handle separator
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
        }
        if (mPredicting) {
            // In certain languages where single quote is a separator, it's better
            // not to auto correct, but accept the typed word. For instance,
            // in Italian dov' should not be expanded to dove' because the elision
            // requires the last vowel to be removed.
            if (mAutoCorrectOn && primaryCode != '\'' &&
                    (mJustRevertedSeparator == null
                            || mJustRevertedSeparator.length() == 0
                            || mJustRevertedSeparator.charAt(0) != primaryCode)) {
                pickDefaultSuggestion();
                pickedDefault = true;
                // Picked the suggestion by the space key.  We consider this
                // as "added an auto space".
                if (primaryCode == KEYCODE_SPACE) {
                    mJustAddedAutoSpace = true;
                }
            } else {
                commitTyped(ic);
            }
        }
        if (mJustAddedAutoSpace && primaryCode == KEYCODE_ENTER) {
            removeTrailingSpace();
            mJustAddedAutoSpace = false;
        }
        sendKeyChar((char)primaryCode);

        // Handle the case of ". ." -> " .." with auto-space if necessary
        // before changing the TextEntryState.
        if (TextEntryState.getState() == TextEntryState.STATE_PUNCTUATION_AFTER_ACCEPTED
                && primaryCode == KEYCODE_PERIOD) {
            reswapPeriodAndSpace();
        }

        TextEntryState.typedCharacter((char) primaryCode, true);
        if (TextEntryState.getState() == TextEntryState.STATE_PUNCTUATION_AFTER_ACCEPTED
                && primaryCode != KEYCODE_ENTER) {
            swapPunctuationAndSpace();
        } else if (isPredictionOn() && primaryCode == KEYCODE_SPACE) {
        //else if (TextEntryState.STATE_SPACE_AFTER_ACCEPTED) {
            doubleSpace();
        }
        if (pickedDefault && mBestWord != null) {
            TextEntryState.acceptedDefault(mWord.getTypedWord(), mBestWord);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
        if (ic != null) {
            ic.endBatchEdit();
        }
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        if (VOICE_INSTALLED & mRecognizing) {
            mVoiceInput.cancel();
        }
        requestHideSelf(0);
        mInputView.closing();
        TextEntryState.endSession();
    }

    private void checkToggleCapsLock() {
        if (mInputView.getKeyboard().isShifted()) {
            toggleCapsLock();
        }
    }

    private void toggleCapsLock() {
        mCapsLock = !mCapsLock;
        if (mKeyboardSwitcher.isAlphabetMode()) {
            ((LatinKeyboard) mInputView.getKeyboard()).setShiftLocked(mCapsLock);
        }
    }

    private void postUpdateSuggestions() {
        mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_UPDATE_SUGGESTIONS), 100);
    }

    private boolean isPredictionOn() {
        boolean predictionOn = mPredictionOn;
        return predictionOn;
    }

    private boolean isCandidateStripVisible() {
        return isPredictionOn() && mShowSuggestions;
    }

    public void onCancelVoice() {
        if (mRecognizing) {
            switchToKeyboardView();
        }
    }

    private void switchToKeyboardView() {
      mHandler.post(new Runnable() {
          public void run() {
              mRecognizing = false;
              if (mInputView != null) {
                setInputView(mInputView);
              }
              updateInputViewShown();
          }});
    }

    private void switchToRecognitionStatusView() {
        final boolean configChanged = mConfigurationChanging;
        mHandler.post(new Runnable() {
            public void run() {
                mRecognizing = true;
                View v = mVoiceInput.getView();
                ViewParent p = v.getParent();
                if (p != null && p instanceof ViewGroup) {
                    ((ViewGroup)v.getParent()).removeView(v);
                }
                setInputView(v);
                updateInputViewShown();
                if (configChanged) {
                    mVoiceInput.onConfigurationChanged();
                }
        }});
    }

    private void startListening(boolean swipe) {
        if (!mHasUsedVoiceInput ||
                (!mLocaleSupportedForVoiceInput && !mHasUsedVoiceInputUnsupportedLocale)) {
            // Calls reallyStartListening if user clicks OK, does nothing if user clicks Cancel.
            showVoiceWarningDialog(swipe);
        } else {
            reallyStartListening(swipe);
        }
    }

    private void reallyStartListening(boolean swipe) {
        if (!mHasUsedVoiceInput) {
            // The user has started a voice input, so remember that in the
            // future (so we don't show the warning dialog after the first run).
            SharedPreferences.Editor editor =
                    PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putBoolean(PREF_HAS_USED_VOICE_INPUT, true);
            editor.commit();
            mHasUsedVoiceInput = true;
        }

        if (!mLocaleSupportedForVoiceInput && !mHasUsedVoiceInputUnsupportedLocale) {
            // The user has started a voice input from an unsupported locale, so remember that
            // in the future (so we don't show the warning dialog the next time they do this).
            SharedPreferences.Editor editor =
                    PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putBoolean(PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE, true);
            editor.commit();
            mHasUsedVoiceInputUnsupportedLocale = true;
        }

        // Clear N-best suggestions
        setSuggestions(null, false, false, true);

        FieldContext context = new FieldContext(
            getCurrentInputConnection(),
            getCurrentInputEditorInfo(),
            mLanguageSwitcher.getInputLanguage(),
            mLanguageSwitcher.getEnabledLanguages());
        mVoiceInput.startListening(context, swipe);
        switchToRecognitionStatusView();
    }

    private void showVoiceWarningDialog(final boolean swipe) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.ic_mic_dialog);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mVoiceInput.logKeyboardWarningDialogOk();
                reallyStartListening(swipe);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mVoiceInput.logKeyboardWarningDialogCancel();
            }
        });

        if (mLocaleSupportedForVoiceInput) {
            String message = getString(R.string.voice_warning_may_not_understand) + "\n\n" +
                    getString(R.string.voice_warning_how_to_turn_off);
            builder.setMessage(message);
        } else {
            String message = getString(R.string.voice_warning_locale_not_supported) + "\n\n" +
                    getString(R.string.voice_warning_may_not_understand) + "\n\n" +
                    getString(R.string.voice_warning_how_to_turn_off);
            builder.setMessage(message);
        }

        builder.setTitle(R.string.voice_warning_title);
        mVoiceWarningDialog = builder.create();

        Window window = mVoiceWarningDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mInputView.getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mVoiceInput.logKeyboardWarningDialogShown();
        mVoiceWarningDialog.show();
    }

    public void onVoiceResults(List<String> candidates,
            Map<String, List<CharSequence>> alternatives) {
        if (!mRecognizing) {
            return;
        }
        mVoiceResults.candidates = candidates;
        mVoiceResults.alternatives = alternatives;
        mHandler.sendMessage(mHandler.obtainMessage(MSG_VOICE_RESULTS));
    }

    private void handleVoiceResults() {
        mAfterVoiceInput = true;
        mImmediatelyAfterVoiceInput = true;

        InputConnection ic = getCurrentInputConnection();
        if (!isFullscreenMode()) {
            // Start listening for updates to the text from typing, etc.
            if (ic != null) {
                ExtractedTextRequest req = new ExtractedTextRequest();
                ic.getExtractedText(req, InputConnection.GET_EXTRACTED_TEXT_MONITOR);
            }
        }

        vibrate();
        switchToKeyboardView();

        final List<CharSequence> nBest = new ArrayList<CharSequence>();
        boolean capitalizeFirstWord = preferCapitalization()
                || (mKeyboardSwitcher.isAlphabetMode() && mInputView.isShifted());
        for (String c : mVoiceResults.candidates) {
            if (capitalizeFirstWord) {
                c = Character.toUpperCase(c.charAt(0)) + c.substring(1, c.length());
            }
            nBest.add(c);
        }

        if (nBest.size() == 0) {
            return;
        }

        String bestResult = nBest.get(0).toString();

        mVoiceInput.logVoiceInputDelivered(bestResult.length());

        mHints.registerVoiceResult(bestResult);

        if (ic != null) ic.beginBatchEdit(); // To avoid extra updates on committing older text

        commitTyped(ic);
        EditingUtil.appendText(ic, bestResult);

        if (ic != null) ic.endBatchEdit();

        // Show N-Best alternates, if there is more than one choice.
        if (nBest.size() > 1) {
            mImmediatelyAfterVoiceSuggestions = true;
            mShowingVoiceSuggestions = true;
            setSuggestions(nBest.subList(1, nBest.size()), false, true, true);
            setCandidatesViewShown(true);
        }
        mVoiceInputHighlighted = true;
        mWordToSuggestions.putAll(mVoiceResults.alternatives);

    }

    private void setSuggestions(
            List<CharSequence> suggestions,
            boolean completions,

            boolean typedWordValid,
            boolean haveMinimalSuggestion) {

        if (mIsShowingHint) {
             setCandidatesView(mCandidateViewContainer);
             mIsShowingHint = false;
        }

        if (mCandidateView != null) {
            mCandidateView.setSuggestions(
                    suggestions, completions, typedWordValid, haveMinimalSuggestion);
        }
    }

    private void updateSuggestions() {
        mSuggestionShouldReplaceCurrentWord = false;

        ((LatinKeyboard) mInputView.getKeyboard()).setPreferredLetters(null);

        // Check if we have a suggestion engine attached.
        if ((mSuggest == null || !isPredictionOn()) && !mVoiceInputHighlighted) {
            return;
        }

        if (!mPredicting) {
            setNextSuggestions();
            return;
        }

        List<CharSequence> stringList = mSuggest.getSuggestions(mInputView, mWord, false);
        int[] nextLettersFrequencies = mSuggest.getNextLettersFrequencies();

        ((LatinKeyboard) mInputView.getKeyboard()).setPreferredLetters(nextLettersFrequencies);

        boolean correctionAvailable = !mInputTypeNoAutoCorrect && mSuggest.hasMinimalCorrection();
        //|| mCorrectionMode == mSuggest.CORRECTION_FULL;
        CharSequence typedWord = mWord.getTypedWord();
        // If we're in basic correct
        boolean typedWordValid = mSuggest.isValidWord(typedWord) ||
                (preferCapitalization() && mSuggest.isValidWord(typedWord.toString().toLowerCase()));
        if (mCorrectionMode == Suggest.CORRECTION_FULL) {
            correctionAvailable |= typedWordValid;
        }
        // Don't auto-correct words with multiple capital letter
        correctionAvailable &= !mWord.isMostlyCaps();

        setSuggestions(stringList, false, typedWordValid, correctionAvailable);
        if (stringList.size() > 0) {
            if (correctionAvailable && !typedWordValid && stringList.size() > 1) {
                mBestWord = stringList.get(1);
            } else {
                mBestWord = typedWord;
            }
        } else {
            mBestWord = null;
        }
        setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);
    }

    private void pickDefaultSuggestion() {
        // Complete any pending candidate query first
        if (mHandler.hasMessages(MSG_UPDATE_SUGGESTIONS)) {
            mHandler.removeMessages(MSG_UPDATE_SUGGESTIONS);
            updateSuggestions();
        }
        if (mBestWord != null && mBestWord.length() > 0) {
            TextEntryState.acceptedDefault(mWord.getTypedWord(), mBestWord);
            mJustAccepted = true;
            pickSuggestion(mBestWord);
            // Add the word to the auto dictionary if it's not a known word
            checkAddToDictionary(mBestWord, AutoDictionary.FREQUENCY_FOR_TYPED);
        }
    }

    public void pickSuggestionManually(int index, CharSequence suggestion) {
        if (mAfterVoiceInput && mShowingVoiceSuggestions) mVoiceInput.logNBestChoose(index);

        if (mAfterVoiceInput && !mShowingVoiceSuggestions) {
            mVoiceInput.flushAllTextModificationCounters();
            // send this intent AFTER logging any prior aggregated edits.
            mVoiceInput.logTextModifiedByChooseSuggestion(suggestion.length());
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
        }
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            if (ic != null) {
                ic.commitCompletion(ci);
            }
            mCommittedLength = suggestion.length();
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
            if (ic != null) {
                ic.endBatchEdit();
            }
            return;
        }

        // If this is a punctuation, apply it through the normal key press
        if (suggestion.length() == 1 && isWordSeparator(suggestion.charAt(0))) {
            onKey(suggestion.charAt(0), null);
            if (ic != null) {
                ic.endBatchEdit();
            }
            return;
        }
        mJustAccepted = true;
        pickSuggestion(suggestion);
        // Add the word to the auto dictionary if it's not a known word
        if (index == 0) {
            checkAddToDictionary(suggestion, AutoDictionary.FREQUENCY_FOR_PICKED);
        }
        TextEntryState.acceptedSuggestion(mComposing.toString(), suggestion);
        // Follow it with a space
        if (mAutoSpace) {
            sendSpace();
            mJustAddedAutoSpace = true;
        }
        // Fool the state watcher so that a subsequent backspace will not do a revert
        TextEntryState.typedCharacter((char) KEYCODE_SPACE, true);
        if (index == 0 && mCorrectionMode > 0 && !mSuggest.isValidWord(suggestion)) {
            mCandidateView.showAddToDictionaryHint(suggestion);
        }
        if (ic != null) {
            ic.endBatchEdit();
        }
    }

    private void pickSuggestion(CharSequence suggestion) {
        if (mCapsLock) {
            suggestion = suggestion.toString().toUpperCase();
        } else if (preferCapitalization()
                || (mKeyboardSwitcher.isAlphabetMode() && mInputView.isShifted())) {
            suggestion = suggestion.toString().toUpperCase().charAt(0)
                    + suggestion.subSequence(1, suggestion.length()).toString();
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (mSuggestionShouldReplaceCurrentWord) {
                EditingUtil.deleteWordAtCursor(ic, getWordSeparators());
            }
            if (!VoiceInput.DELETE_SYMBOL.equals(suggestion)) {
                ic.commitText(suggestion, 1);
            }
        }
        mPredicting = false;
        mCommittedLength = suggestion.length();
        ((LatinKeyboard) mInputView.getKeyboard()).setPreferredLetters(null);
        setNextSuggestions();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void setNextSuggestions() {
        setSuggestions(mSuggestPuncList, false, false, false);
    }

    private void checkAddToDictionary(CharSequence suggestion, int frequencyDelta) {
        // Only auto-add to dictionary if auto-correct is ON. Otherwise we'll be
        // adding words in situations where the user or application really didn't
        // want corrections enabled or learned.
        if (!(mCorrectionMode == Suggest.CORRECTION_FULL)) return;
        if (mAutoDictionary.isValidWord(suggestion)
                || (!mSuggest.isValidWord(suggestion.toString())
                    && !mSuggest.isValidWord(suggestion.toString().toLowerCase()))) {
            mAutoDictionary.addWord(suggestion.toString(), frequencyDelta);
        }
    }

    private boolean isCursorTouchingWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence toLeft = ic.getTextBeforeCursor(1, 0);
        CharSequence toRight = ic.getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(toLeft)
                && !isWordSeparator(toLeft.charAt(0))) {
            return true;
        }
        if (!TextUtils.isEmpty(toRight)
                && !isWordSeparator(toRight.charAt(0))) {
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
        if (!mPredicting && length > 0) {
            final InputConnection ic = getCurrentInputConnection();
            mPredicting = true;
            ic.beginBatchEdit();
            mJustRevertedSeparator = ic.getTextBeforeCursor(1, 0);
            if (deleteChar) ic.deleteSurroundingText(1, 0);
            int toDelete = mCommittedLength;
            CharSequence toTheLeft = ic.getTextBeforeCursor(mCommittedLength, 0);
            if (toTheLeft != null && toTheLeft.length() > 0
                    && isWordSeparator(toTheLeft.charAt(0))) {
                toDelete--;
            }
            ic.deleteSurroundingText(toDelete, 0);
            ic.setComposingText(mComposing, 1);
            TextEntryState.backspace();
            ic.endBatchEdit();
            postUpdateSuggestions();
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            mJustRevertedSeparator = null;
        }
    }

    protected String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

    public boolean isSentenceSeparator(int code) {
        return mSentenceSeparators.contains(String.valueOf((char)code));
    }

    private void sendSpace() {
        sendKeyChar((char)KEYCODE_SPACE);
        updateShiftKeyState(getCurrentInputEditorInfo());
        //onKey(KEY_SPACE[0], KEY_SPACE);
    }

    public boolean preferCapitalization() {
        return mWord.isCapitalized();
    }

    public void swipeRight() {
        if (userHasNotTypedRecently() && VOICE_INSTALLED && mEnableVoice &&
                fieldCanDoVoice(makeFieldContext())) {
            startListening(true /* was a swipe */);
        }

        if (LatinKeyboardView.DEBUG_AUTO_PLAY) {
            ClipboardManager cm = ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE));
            CharSequence text = cm.getText();
            if (!TextUtils.isEmpty(text)) {
                mInputView.startPlaying(text.toString());
            }
        }
    }

    private void toggleLanguage(boolean reset, boolean next) {
        if (reset) {
            mLanguageSwitcher.reset();
        } else {
            if (next) {
                mLanguageSwitcher.next();
            } else {
                mLanguageSwitcher.prev();
            }
        }
        int currentKeyboardMode = mKeyboardSwitcher.getKeyboardMode();
        reloadKeyboards();
        mKeyboardSwitcher.makeKeyboards(true);
        mKeyboardSwitcher.setKeyboardMode(currentKeyboardMode, 0,
                mEnableVoiceButton && mEnableVoice);
        initSuggest(mLanguageSwitcher.getInputLanguage());
        mLanguageSwitcher.persist();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (PREF_SELECTED_LANGUAGES.equals(key)) {
            mLanguageSwitcher.loadLocales(sharedPreferences);
            mRefreshKeyboardRequired = true;
        }
    }

    public void swipeLeft() {
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
        //launchSettings();
    }

    public void onPress(int primaryCode) {
        vibrate();
        playKeyClick(primaryCode);
    }

    public void onRelease(int primaryCode) {
        // Reset any drag flags in the keyboard
        ((LatinKeyboard) mInputView.getKeyboard()).keyReleased();
        //vibrate();
    }

    private FieldContext makeFieldContext() {
        return new FieldContext(
                getCurrentInputConnection(),
                getCurrentInputEditorInfo(),
                mLanguageSwitcher.getInputLanguage(),
                mLanguageSwitcher.getEnabledLanguages());
    }

    private boolean fieldCanDoVoice(FieldContext fieldContext) {
        return !mPasswordText
                && mVoiceInput != null
                && !mVoiceInput.isBlacklistedField(fieldContext);
    }

    private boolean shouldShowVoiceButton(FieldContext fieldContext, EditorInfo attribute) {
        return ENABLE_VOICE_BUTTON && fieldCanDoVoice(fieldContext)
                && !(attribute != null && attribute.privateImeOptions != null
                        && attribute.privateImeOptions.equals(IME_OPTION_NO_MICROPHONE))
                && SpeechRecognizer.isRecognitionAvailable(this);
    }

    // receive ringer mode changes to detect silent mode
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateRingerMode();
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

    private boolean userHasNotTypedRecently() {
        return (SystemClock.uptimeMillis() - mLastKeyTime)
            > MIN_MILLIS_AFTER_TYPING_BEFORE_SWIPE;
    }

    private void playKeyClick(int primaryCode) {
        // if mAudioManager is null, we don't have the ringer state yet
        // mAudioManager will be set by updateRingerMode
        if (mAudioManager == null) {
            if (mInputView != null) {
                updateRingerMode();
            }
        }
        if (mSoundOn && !mSilentMode) {
            // FIXME: Volume and enable should come from UI settings
            // FIXME: These should be triggered after auto-repeat logic
            int sound = AudioManager.FX_KEYPRESS_STANDARD;
            switch (primaryCode) {
                case Keyboard.KEYCODE_DELETE:
                    sound = AudioManager.FX_KEYPRESS_DELETE;
                    break;
                case KEYCODE_ENTER:
                    sound = AudioManager.FX_KEYPRESS_RETURN;
                    break;
                case KEYCODE_SPACE:
                    sound = AudioManager.FX_KEYPRESS_SPACEBAR;
                    break;
            }
            mAudioManager.playSoundEffect(sound, FX_VOLUME);
        }
    }

    private void vibrate() {
        if (!mVibrateOn) {
            return;
        }
        if (mInputView != null) {
            mInputView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    private void checkTutorial(String privateImeOptions) {
        if (privateImeOptions == null) return;
        if (privateImeOptions.equals("com.android.setupwizard:ShowTutorial")) {
            if (mTutorial == null) startTutorial();
        } else if (privateImeOptions.equals("com.android.setupwizard:HideTutorial")) {
            if (mTutorial != null) {
                if (mTutorial.close()) {
                    mTutorial = null;
                }
            }
        }
    }

    private void startTutorial() {
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_START_TUTORIAL), 500);
    }

    void tutorialDone() {
        mTutorial = null;
    }

    void promoteToUserDictionary(String word, int frequency) {
        if (mUserDictionary.isValidWord(word)) return;
        mUserDictionary.addWord(word, frequency);
    }

    WordComposer getCurrentWord() {
        return mWord;
    }

    private void updateCorrectionMode() {
        mHasDictionary = mSuggest != null ? mSuggest.hasMainDictionary() : false;
        mAutoCorrectOn = (mAutoCorrectEnabled || mQuickFixes)
                && !mInputTypeNoAutoCorrect && mHasDictionary;
        mCorrectionMode = (mAutoCorrectOn && mAutoCorrectEnabled)
                ? Suggest.CORRECTION_FULL
                : (mAutoCorrectOn ? Suggest.CORRECTION_BASIC : Suggest.CORRECTION_NONE);
        if (mSuggest != null) {
            mSuggest.setCorrectionMode(mCorrectionMode);
        }
    }

    private void updateAutoTextEnabled(Locale systemLocale) {
        if (mSuggest == null) return;
        boolean different =
                !systemLocale.getLanguage().equalsIgnoreCase(mInputLocale.substring(0, 2));
        mSuggest.setAutoTextEnabled(!different && mQuickFixes);
    }

    protected void launchSettings() {
        launchSettings(LatinIMESettings.class);
    }

    protected void launchSettings(Class settingsClass) {
        handleClose();
        Intent intent = new Intent();
        intent.setClass(LatinIME.this, settingsClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void loadSettings() {
        // Get the settings preferences
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        mVibrateOn = sp.getBoolean(PREF_VIBRATE_ON, false);
        mSoundOn = sp.getBoolean(PREF_SOUND_ON, false);
        mAutoCap = sp.getBoolean(PREF_AUTO_CAP, true);
        mQuickFixes = sp.getBoolean(PREF_QUICK_FIXES, true);
        mHasUsedVoiceInput = sp.getBoolean(PREF_HAS_USED_VOICE_INPUT, false);
        mHasUsedVoiceInputUnsupportedLocale =
                sp.getBoolean(PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE, false);

        // Get the current list of supported locales and check the current locale against that
        // list. We cache this value so as not to check it every time the user starts a voice
        // input. Because this method is called by onStartInputView, this should mean that as
        // long as the locale doesn't change while the user is keeping the IME open, the
        // value should never be stale.
        String supportedLocalesString = SettingsUtil.getSettingsString(
                getContentResolver(),
                SettingsUtil.LATIN_IME_VOICE_INPUT_SUPPORTED_LOCALES,
                DEFAULT_VOICE_INPUT_SUPPORTED_LOCALES);
        ArrayList<String> voiceInputSupportedLocales =
                newArrayList(supportedLocalesString.split("\\s+"));

        mLocaleSupportedForVoiceInput = voiceInputSupportedLocales.contains(mInputLocale);

        mShowSuggestions = sp.getBoolean(PREF_SHOW_SUGGESTIONS, true);

        if (VOICE_INSTALLED) {
            final String voiceMode = sp.getString(PREF_VOICE_MODE,
                    getString(R.string.voice_mode_main));
            boolean enableVoice = !voiceMode.equals(getString(R.string.voice_mode_off))
                    && mEnableVoiceButton;
            boolean voiceOnPrimary = voiceMode.equals(getString(R.string.voice_mode_main));
            if (mKeyboardSwitcher != null &&
                    (enableVoice != mEnableVoice || voiceOnPrimary != mVoiceOnPrimary)) {
                mKeyboardSwitcher.setVoiceMode(enableVoice, voiceOnPrimary);
            }
            mEnableVoice = enableVoice;
            mVoiceOnPrimary = voiceOnPrimary;
        }
        mAutoCorrectEnabled = sp.getBoolean(PREF_AUTO_COMPLETE,
                mResources.getBoolean(R.bool.enable_autocorrect)) & mShowSuggestions;
        updateCorrectionMode();
        updateAutoTextEnabled(mResources.getConfiguration().locale);
        mLanguageSwitcher.loadLocales(sp);
    }

    private void initSuggestPuncList() {
        mSuggestPuncList = new ArrayList<CharSequence>();
        String suggestPuncs = mResources.getString(R.string.suggested_punctuations);
        if (suggestPuncs != null) {
            for (int i = 0; i < suggestPuncs.length(); i++) {
                mSuggestPuncList.add(suggestPuncs.subSequence(i, i + 1));
            }
        }
    }

    private void showOptionsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.ic_dialog_keyboard);
        builder.setNegativeButton(android.R.string.cancel, null);
        CharSequence itemSettings = getString(R.string.english_ime_settings);
        CharSequence itemInputMethod = getString(R.string.inputMethod);
        builder.setItems(new CharSequence[] {
                itemSettings, itemInputMethod},
                new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                    case POS_SETTINGS:
                        launchSettings();
                        break;
                    case POS_METHOD:
                        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                            .showInputMethodPicker();
                        break;
                }
            }
        });
        builder.setTitle(mResources.getString(R.string.english_ime_name));
        mOptionsDialog = builder.create();
        Window window = mOptionsDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mInputView.getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mOptionsDialog.show();
    }

    private void changeKeyboardMode() {
        mKeyboardSwitcher.toggleSymbols();
        if (mCapsLock && mKeyboardSwitcher.isAlphabetMode()) {
            ((LatinKeyboard) mInputView.getKeyboard()).setShiftLocked(mCapsLock);
        }

        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    public static <E> ArrayList<E> newArrayList(E... elements) {
        int capacity = (elements.length * 110) / 100 + 5;
        ArrayList<E> list = new ArrayList<E>(capacity);
        Collections.addAll(list, elements);
        return list;
    }

    @Override protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
        super.dump(fd, fout, args);

        final Printer p = new PrintWriterPrinter(fout);
        p.println("LatinIME state :");
        p.println("  Keyboard mode = " + mKeyboardSwitcher.getKeyboardMode());
        p.println("  mCapsLock=" + mCapsLock);
        p.println("  mComposing=" + mComposing.toString());
        p.println("  mPredictionOn=" + mPredictionOn);
        p.println("  mCorrectionMode=" + mCorrectionMode);
        p.println("  mPredicting=" + mPredicting);
        p.println("  mAutoCorrectOn=" + mAutoCorrectOn);
        p.println("  mAutoSpace=" + mAutoSpace);
        p.println("  mCompletionOn=" + mCompletionOn);
        p.println("  TextEntryState.state=" + TextEntryState.getState());
        p.println("  mSoundOn=" + mSoundOn);
        p.println("  mVibrateOn=" + mVibrateOn);
    }

    // Characters per second measurement

    private static final boolean PERF_DEBUG = false;
    private long mLastCpsTime;
    private static final int CPS_BUFFER_SIZE = 16;
    private long[] mCpsIntervals = new long[CPS_BUFFER_SIZE];
    private int mCpsIndex;
    private boolean mInputTypeNoAutoCorrect;

    private void measureCps() {
        if (!LatinIME.PERF_DEBUG) return;
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
