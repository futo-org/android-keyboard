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

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.LatinKeyboardView;
import com.android.inputmethod.latin.Utils.RingCharBuffer;
import com.android.inputmethod.voice.VoiceIMEConnector;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService implements KeyboardActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "LatinIME";
    private static final boolean PERF_DEBUG = false;
    private static final boolean DEBUG = false;
    private static final boolean TRACE = false;

    private static final int DELAY_UPDATE_SUGGESTIONS = 180;
    private static final int DELAY_UPDATE_OLD_SUGGESTIONS = 300;
    private static final int DELAY_UPDATE_SHIFT_STATE = 300;

    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200;

    // Contextual menu positions
    private static final int POS_METHOD = 0;
    private static final int POS_SETTINGS = 1;

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
    private CandidateView mCandidateView;
    private Suggest mSuggest;
    private CompletionInfo[] mApplicationSpecifiedCompletions;

    private AlertDialog mOptionsDialog;

    private InputMethodManager mImm;
    private KeyboardSwitcher mKeyboardSwitcher;
    private SubtypeSwitcher mSubtypeSwitcher;
    private VoiceIMEConnector mVoiceConnector;

    private UserDictionary mUserDictionary;
    private UserBigramDictionary mUserBigramDictionary;
    private ContactsDictionary mContactsDictionary;
    private AutoDictionary mAutoDictionary;

    private Resources mResources;
    private SharedPreferences mPrefs;

    private final StringBuilder mComposing = new StringBuilder();
    private WordComposer mWord = new WordComposer();
    private CharSequence mBestWord;
    private boolean mPredicting;
    private boolean mPredictionOn;
    private boolean mApplicationSpecifiedCompletionOn;
    private boolean mHasDictionary;
    private boolean mAutoSpace;
    private boolean mJustAddedAutoSpace;
    private boolean mAutoCorrectEnabled;
    private boolean mReCorrectionEnabled;
    private boolean mBigramSuggestionEnabled;
    private boolean mAutoCorrectOn;
    private boolean mVibrateOn;
    private boolean mSoundOn;
    private boolean mPopupOn;
    private boolean mAutoCap;
    private boolean mQuickFixes;

    private int mCorrectionMode;
    private int mCommittedLength;
    private int mOrientation;
    // Keep track of the last selection range to decide if we need to show word alternatives
    private int mLastSelectionStart;
    private int mLastSelectionEnd;
    private SuggestedWords mSuggestPuncList;

    // Input type is such that we should not auto-correct
    private boolean mInputTypeNoAutoCorrect;

    // Indicates whether the suggestion strip is to be on in landscape
    private boolean mJustAccepted;
    private boolean mJustReverted;
    private int mDeleteCount;
    private long mLastKeyTime;

    private AudioManager mAudioManager;
    // Align sound effect volume on music volume
    private static final float FX_VOLUME = -1.0f;
    private boolean mSilentMode;

    /* package */ String mWordSeparators;
    private String mSentenceSeparators;
    private String mSuggestPuncs;
    // TODO: Move this flag to VoiceIMEConnector
    private boolean mConfigurationChanging;

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private CharSequence mEnteredText;
    private boolean mRefreshKeyboardRequired;

    private final ArrayList<WordAlternatives> mWordHistory = new ArrayList<WordAlternatives>();

    public abstract static class WordAlternatives {
        protected CharSequence mChosenWord;

        public WordAlternatives() {
            // Nothing
        }

        public WordAlternatives(CharSequence chosenWord) {
            mChosenWord = chosenWord;
        }

        @Override
        public int hashCode() {
            return mChosenWord.hashCode();
        }

        public abstract CharSequence getOriginalWord();

        public CharSequence getChosenWord() {
            return mChosenWord;
        }

        public abstract SuggestedWords.Builder getAlternatives();
    }

    public class TypedWordAlternatives extends WordAlternatives {
        private WordComposer word;

        public TypedWordAlternatives() {
            // Nothing
        }

        public TypedWordAlternatives(CharSequence chosenWord, WordComposer wordComposer) {
            super(chosenWord);
            word = wordComposer;
        }

        @Override
        public CharSequence getOriginalWord() {
            return word.getTypedWord();
        }

        @Override
        public SuggestedWords.Builder getAlternatives() {
            return getTypedSuggestions(word);
        }
    }

    public final UIHandler mHandler = new UIHandler();

    public class UIHandler extends Handler {
        private static final int MSG_UPDATE_SUGGESTIONS = 0;
        private static final int MSG_UPDATE_OLD_SUGGESTIONS = 1;
        private static final int MSG_UPDATE_SHIFT_STATE = 2;
        private static final int MSG_VOICE_RESULTS = 3;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_SUGGESTIONS:
                updateSuggestions();
                break;
            case MSG_UPDATE_OLD_SUGGESTIONS:
                setOldSuggestions();
                break;
            case MSG_UPDATE_SHIFT_STATE:
                mKeyboardSwitcher.updateShiftState();
                break;
            case MSG_VOICE_RESULTS:
                mVoiceConnector.handleVoiceResults(preferCapitalization()
                        || (mKeyboardSwitcher.isAlphabetMode()
                                && mKeyboardSwitcher.isShiftedOrShiftLocked()));
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
    }

    @Override
    public void onCreate() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs = prefs;
        LatinImeLogger.init(this, prefs);
        SubtypeSwitcher.init(this, prefs);
        KeyboardSwitcher.init(this, prefs);
        super.onCreate();
        //setStatusIcon(R.drawable.ime_qwerty);
        mResources = getResources();
        mImm = ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE));
        final Configuration conf = mResources.getConfiguration();
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mReCorrectionEnabled = prefs.getBoolean(Settings.PREF_RECORRECTION_ENABLED,
                getResources().getBoolean(R.bool.default_recorrection_enabled));

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

        mOrientation = conf.orientation;
        initSuggestPuncList();

        // register to receive ringer mode changes for silent mode
        IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);
        mVoiceConnector = VoiceIMEConnector.init(this, prefs, mHandler);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Loads a dictionary or multiple separated dictionary
     * @return returns array of dictionary resource ids
     */
    public static int[] getDictionary(Resources res) {
        String packageName = LatinIME.class.getPackage().getName();
        XmlResourceParser xrp = res.getXml(R.xml.dictionary);
        ArrayList<Integer> dictionaries = new ArrayList<Integer>();

        try {
            int current = xrp.getEventType();
            while (current != XmlPullParser.END_DOCUMENT) {
                if (current == XmlPullParser.START_TAG) {
                    String tag = xrp.getName();
                    if (tag != null) {
                        if (tag.equals("part")) {
                            String dictFileName = xrp.getAttributeValue(null, "name");
                            dictionaries.add(res.getIdentifier(dictFileName, "raw", packageName));
                        }
                    }
                }
                xrp.next();
                current = xrp.getEventType();
            }
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Dictionary XML parsing failure");
        } catch (IOException e) {
            Log.e(TAG, "Dictionary XML IOException");
        }

        int count = dictionaries.size();
        int[] dict = new int[count];
        for (int i = 0; i < count; i++) {
            dict[i] = dictionaries.get(i);
        }

        return dict;
    }

    private void initSuggest() {
        updateAutoTextEnabled();
        String locale = mSubtypeSwitcher.getInputLocaleStr();

        Resources orig = getResources();
        Locale savedLocale = mSubtypeSwitcher.changeSystemLocale(new Locale(locale));
        if (mSuggest != null) {
            mSuggest.close();
        }
        final SharedPreferences prefs = mPrefs;
        mQuickFixes = prefs.getBoolean(Settings.PREF_QUICK_FIXES, true);

        int[] dictionaries = getDictionary(orig);
        mSuggest = new Suggest(this, dictionaries);
        loadAndSetAutoCorrectionThreshold(prefs);
        if (mUserDictionary != null) mUserDictionary.close();
        mUserDictionary = new UserDictionary(this, locale);
        if (mContactsDictionary == null) {
            mContactsDictionary = new ContactsDictionary(this, Suggest.DIC_CONTACTS);
        }
        if (mAutoDictionary != null) {
            mAutoDictionary.close();
        }
        mAutoDictionary = new AutoDictionary(this, this, locale, Suggest.DIC_AUTO);
        if (mUserBigramDictionary != null) {
            mUserBigramDictionary.close();
        }
        mUserBigramDictionary = new UserBigramDictionary(this, this, locale, Suggest.DIC_USER);
        mSuggest.setUserBigramDictionary(mUserBigramDictionary);
        mSuggest.setUserDictionary(mUserDictionary);
        mSuggest.setContactsDictionary(mContactsDictionary);
        mSuggest.setAutoDictionary(mAutoDictionary);
        updateCorrectionMode();
        mWordSeparators = mResources.getString(R.string.word_separators);
        mSentenceSeparators = mResources.getString(R.string.sentence_separators);

        mSubtypeSwitcher.changeSystemLocale(savedLocale);
    }

    @Override
    public void onDestroy() {
        if (mUserDictionary != null) {
            mUserDictionary.close();
        }
        if (mContactsDictionary != null) {
            mContactsDictionary.close();
        }
        unregisterReceiver(mReceiver);
        mVoiceConnector.destroy();
        LatinImeLogger.commit();
        LatinImeLogger.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        mSubtypeSwitcher.onConfigurationChanged(conf);
        if (mSubtypeSwitcher.isKeyboardMode())
            onKeyboardLanguageChanged();
        updateAutoTextEnabled();

        // If orientation changed while predicting, commit the change
        if (conf.orientation != mOrientation) {
            InputConnection ic = getCurrentInputConnection();
            commitTyped(ic);
            if (ic != null) ic.finishComposingText(); // For voice input
            mOrientation = conf.orientation;
            final int mode = mKeyboardSwitcher.getKeyboardMode();
            final EditorInfo attribute = getCurrentInputEditorInfo();
            final int imeOptions = (attribute != null) ? attribute.imeOptions : 0;
            mKeyboardSwitcher.loadKeyboard(mode, imeOptions,
                    mVoiceConnector.isVoiceButtonEnabled(),
                    mVoiceConnector.isVoiceButtonOnPrimary());
        }

        mConfigurationChanging = true;
        super.onConfigurationChanged(conf);
        mVoiceConnector.onConfigurationChanged(mConfigurationChanging);
        mConfigurationChanging = false;
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
        if (container.getPaddingRight() != 0) {
            HorizontalScrollView scrollView =
                    (HorizontalScrollView) container.findViewById(R.id.candidates_scroll_view);
            scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            container.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        mCandidateView = (CandidateView) container.findViewById(R.id.candidates);
        mCandidateView.setService(this);
        setCandidatesViewShown(true);
        return container;
    }

    private static boolean isPasswordVariation(int variation) {
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    private static boolean isEmailVariation(int variation) {
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        LatinKeyboardView inputView = switcher.getInputView();

        // In landscape mode, this method gets called without the input view being created.
        if (inputView == null) {
            return;
        }

        mSubtypeSwitcher.updateParametersOnStartInputView();

        if (mRefreshKeyboardRequired) {
            mRefreshKeyboardRequired = false;
            onKeyboardLanguageChanged();
        }

        TextEntryState.newSession(this);

        // Most such things we decide below in the switch statement, but we need to know
        // now whether this is a password text field, because we need to know now (before
        // the switch statement) whether we want to enable the voice button.
        int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
        mVoiceConnector.resetVoiceStates(isPasswordVariation(variation));
        mInputTypeNoAutoCorrect = false;
        mPredictionOn = false;
        mApplicationSpecifiedCompletionOn = false;
        mApplicationSpecifiedCompletions = null;
        mEnteredText = null;

        final int mode;
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                mode = KeyboardId.MODE_NUMBER;
                break;
            case InputType.TYPE_CLASS_PHONE:
                mode = KeyboardId.MODE_PHONE;
                break;
            case InputType.TYPE_CLASS_TEXT:
                //startPrediction();
                mPredictionOn = true;
                // Make sure that passwords are not displayed in candidate view
                if (isPasswordVariation(variation)) {
                    mPredictionOn = false;
                }
                if (isEmailVariation(variation)
                        || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME) {
                    mAutoSpace = false;
                } else {
                    mAutoSpace = true;
                }
                if (isEmailVariation(variation)) {
                    mPredictionOn = false;
                    mode = KeyboardId.MODE_EMAIL;
                } else if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
                    mPredictionOn = false;
                    mode = KeyboardId.MODE_URL;
                } else if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                    mode = KeyboardId.MODE_IM;
                } else if (variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    mPredictionOn = false;
                    mode = KeyboardId.MODE_TEXT;
                } else if (variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
                    mode = KeyboardId.MODE_WEB;
                    // If it's a browser edit field and auto correct is not ON explicitly, then
                    // disable auto correction, but keep suggestions on.
                    if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                        mInputTypeNoAutoCorrect = true;
                    }
                } else {
                    mode = KeyboardId.MODE_TEXT;
                }

                // If NO_SUGGESTIONS is set, don't do prediction.
                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    mPredictionOn = false;
                    mInputTypeNoAutoCorrect = true;
                }
                // If it's not multiline and the autoCorrect flag is not set, then don't correct
                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0 &&
                        (attribute.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
                    mInputTypeNoAutoCorrect = true;
                }
                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    mPredictionOn = false;
                    mApplicationSpecifiedCompletionOn = isFullscreenMode();
                }
                break;
            default:
                mode = KeyboardId.MODE_TEXT;
                break;
        }
        inputView.closing();
        mComposing.setLength(0);
        mPredicting = false;
        mDeleteCount = 0;
        mJustAddedAutoSpace = false;

        loadSettings(attribute);
        if (mSubtypeSwitcher.isKeyboardMode()) {
            switcher.loadKeyboard(mode, attribute.imeOptions,
                    mVoiceConnector.isVoiceButtonEnabled(),
                    mVoiceConnector.isVoiceButtonOnPrimary());
            switcher.updateShiftState();
        }

        setCandidatesViewShownInternal(isCandidateStripVisible(),
                false /* needsInputViewShown */ );
        // Delay updating suggestions because keyboard input view may not be shown at this point.
        mHandler.postUpdateSuggestions();

        // If the dictionary is not big enough, don't auto correct
        mHasDictionary = mSuggest.hasMainDictionary();

        updateCorrectionMode();

        inputView.setPreviewEnabled(mPopupOn);
        inputView.setProximityCorrectionEnabled(true);
        mPredictionOn = mPredictionOn && (mCorrectionMode > 0 || isSuggestionShown());
        // If we just entered a text field, maybe it has some old text that requires correction
        checkReCorrectionOnStart();
        inputView.setForeground(true);

        mVoiceConnector.onStartInputView(mKeyboardSwitcher.getInputView().getWindowToken());

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    private void checkReCorrectionOnStart() {
        if (!mReCorrectionEnabled) return;

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // There could be a pending composing span.  Clean it up first.
        ic.finishComposingText();

        if (isSuggestionShown() && isPredictionOn()) {
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

        mVoiceConnector.flushVoiceInputLogs(mConfigurationChanging);

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
        mVoiceConnector.showPunctuationHintIfNecessary();
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

        mVoiceConnector.setCursorAndSelection(newSelEnd, newSelStart);

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if ((((mComposing.length() > 0 && mPredicting)
                || mVoiceConnector.isVoiceInputHighlighted()) && (newSelStart != candidatesEnd
                        || newSelEnd != candidatesEnd) && mLastSelectionStart != newSelStart)) {
            mComposing.setLength(0);
            mPredicting = false;
            mHandler.postUpdateSuggestions();
            TextEntryState.reset();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
            mVoiceConnector.setVoiceInputHighlighted(false);
        } else if (!mPredicting && !mJustAccepted) {
            switch (TextEntryState.getState()) {
            case ACCEPTED_DEFAULT:
                TextEntryState.reset();
                // $FALL-THROUGH$
            case SPACE_AFTER_PICKED:
                mJustAddedAutoSpace = false; // The user moved the cursor.
                break;
            default:
                break;
            }
        }
        mJustAccepted = false;
        mHandler.postUpdateShiftKeyState();

        // Make a note of the cursor position
        mLastSelectionStart = newSelStart;
        mLastSelectionEnd = newSelEnd;

        if (mReCorrectionEnabled && isSuggestionShown()) {
            // Don't look for corrections if the keyboard is not visible
            if (mKeyboardSwitcher.isInputViewShown()) {
                // Check if we should go in or out of correction mode.
                if (isPredictionOn() && !mJustReverted
                        && (candidatesStart == candidatesEnd || newSelStart != oldSelStart
                                || TextEntryState.isCorrecting())
                                && (newSelStart < newSelEnd - 1 || (!mPredicting))) {
                    if (isCursorTouchingWord() || mLastSelectionStart < mLastSelectionEnd) {
                        mHandler.postUpdateOldSuggestions();
                    } else {
                        abortCorrection(false);
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
        if (mReCorrectionEnabled && isPredictionOn()) return;

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
        if (mReCorrectionEnabled && isPredictionOn()) return;

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
        mVoiceConnector.hideVoiceWindow(mConfigurationChanging);
        mWordHistory.clear();
        super.hideWindow();
        TextEntryState.endSession();
    }

    @Override
    public void onDisplayCompletions(CompletionInfo[] applicationSpecifiedCompletions) {
        if (DEBUG) {
            Log.i("foo", "Received completions:");
            final int count = (applicationSpecifiedCompletions != null)
                    ? applicationSpecifiedCompletions.length : 0;
            for (int i = 0; i < count; i++) {
                Log.i("foo", "  #" + i + ": " + applicationSpecifiedCompletions[i]);
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
        // TODO: Remove this if we support candidates with hard keyboard
        if (onEvaluateInputViewShown()) {
            super.setCandidatesViewShown(shown
                    && (needsInputViewShown ? mKeyboardSwitcher.isInputViewShown() : true));
        }
    }

    @Override
    public void setCandidatesViewShown(boolean shown) {
        setCandidatesViewShownInternal(shown, true /* needsInputViewShown */ );
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (!isFullscreenMode()) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets;
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float displayHeight = dm.heightPixels;
        // If the display is more than X inches high, don't go to fullscreen mode
        float dimen = getResources().getDimension(R.dimen.max_height_for_fullscreen);
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
        if (mPredicting) {
            mPredicting = false;
            if (mComposing.length() > 0) {
                if (inputConnection != null) {
                    inputConnection.commitText(mComposing, 1);
                }
                mCommittedLength = mComposing.length();
                TextEntryState.acceptedTyped(mComposing);
                addToDictionaries(mComposing, AutoDictionary.FREQUENCY_FOR_TYPED);
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
        //if (!mAutoPunctuate) return;
        if (mCorrectionMode == Suggest.CORRECTION_NONE) return;
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && Character.isLetterOrDigit(lastThree.charAt(0))
                && lastThree.charAt(1) == Keyboard.CODE_SPACE
                && lastThree.charAt(2) == Keyboard.CODE_SPACE) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(". ", 1);
            ic.endBatchEdit();
            mKeyboardSwitcher.updateShiftState();
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
            if (Utils.hasMultipleEnabledIMEsOrSubtypes(mImm)) {
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

    // Implementation of KeyboardViewListener

    @Override
    public void onKey(int primaryCode, int[] keyCodes, int x, int y) {
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
        case Keyboard.CODE_NEXT_LANGUAGE:
            toggleLanguage(false, true);
            break;
        case Keyboard.CODE_PREV_LANGUAGE:
            toggleLanguage(false, false);
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
            RingCharBuffer.getInstance().push((char)primaryCode, x, y);
            LatinImeLogger.logOnInputChar();
            if (isWordSeparator(primaryCode)) {
                handleSeparator(primaryCode);
            } else {
                handleCharacter(primaryCode, keyCodes);
            }
            // Cancel the just reverted state
            mJustReverted = false;
        }
        switcher.onKey(primaryCode);
        // Reset after any single keystroke
        mEnteredText = null;
    }

    @Override
    public void onText(CharSequence text) {
        mVoiceConnector.commitVoiceInput();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        abortCorrection(false);
        ic.beginBatchEdit();
        commitTyped(ic);
        maybeRemovePreviousPeriod(text);
        ic.commitText(text, 1);
        ic.endBatchEdit();
        mKeyboardSwitcher.updateShiftState();
        mKeyboardSwitcher.onKey(0); // dummy key code.
        mJustReverted = false;
        mJustAddedAutoSpace = false;
        mEnteredText = text;
    }

    @Override
    public void onCancel() {
        // User released a finger outside any key
    }

    private void handleBackspace() {
        if (mVoiceConnector.logAndRevertVoiceInput()) return;
        boolean deleteChar = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        ic.beginBatchEdit();

        mVoiceConnector.handleBackspace();

        if (mPredicting) {
            final int length = mComposing.length();
            if (length > 0) {
                mComposing.delete(length - 1, length);
                mWord.deleteLast();
                ic.setComposingText(mComposing, 1);
                if (mComposing.length() == 0) {
                    mPredicting = false;
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
        if (TextEntryState.getState() == TextEntryState.State.UNDO_COMMIT) {
            revertLastWord(deleteChar);
            ic.endBatchEdit();
            return;
        } else if (mEnteredText != null && sameAsTextBeforeCursor(ic, mEnteredText)) {
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
        mJustReverted = false;
        ic.endBatchEdit();
    }

    private void handleTab() {
        final int imeOptions = getCurrentInputEditorInfo().imeOptions;
        final int navigationFlags =
                EditorInfo.IME_FLAG_NAVIGATE_NEXT | EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
        if ((imeOptions & navigationFlags) == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB);
            return;
        }

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;

        // True if keyboard is in either chording shift or manual temporary upper case mode.
        final boolean isManualTemporaryUpperCase = mKeyboardSwitcher.isManualTemporaryUpperCase();
        if ((imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0
                && !isManualTemporaryUpperCase) {
            ic.performEditorAction(EditorInfo.IME_ACTION_NEXT);
        } else if ((imeOptions & EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0
                && isManualTemporaryUpperCase) {
            ic.performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
        }
    }

    private void abortCorrection(boolean force) {
        if (force || TextEntryState.isCorrecting()) {
            TextEntryState.onAbortCorrection();
            setCandidatesViewShown(isCandidateStripVisible());
            getCurrentInputConnection().finishComposingText();
            clearSuggestions();
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        mVoiceConnector.handleCharacter();

        if (mLastSelectionStart == mLastSelectionEnd && TextEntryState.isCorrecting()) {
            abortCorrection(false);
        }

        int code = primaryCode;
        if (isAlphabet(code) && isPredictionOn() && !isCursorTouchingWord()) {
            if (!mPredicting) {
                mPredicting = true;
                mComposing.setLength(0);
                saveWordInHistory(mBestWord);
                mWord.reset();
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
                    onText(upperCase);
                    return;
                }
            }
        }
        if (mPredicting) {
            if (mComposing.length() == 0 && switcher.isAlphabetMode()
                    && switcher.isShiftedOrShiftLocked()) {
                mWord.setFirstCharCapitalized(true);
            }
            mComposing.append((char) code);
            mWord.add(code, keyCodes);
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
        TextEntryState.typedCharacter((char) code, isWordSeparator(code));
    }

    private void handleSeparator(int primaryCode) {
        mVoiceConnector.handleSeparator();

        // Should dismiss the "Touch again to save" message when handling separator
        if (mCandidateView != null && mCandidateView.dismissAddToDictionaryHint()) {
            mHandler.postUpdateSuggestions();
        }

        boolean pickedDefault = false;
        // Handle separator
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.beginBatchEdit();
            abortCorrection(false);
        }
        if (mPredicting) {
            // In certain languages where single quote is a separator, it's better
            // not to auto correct, but accept the typed word. For instance,
            // in Italian dov' should not be expanded to dove' because the elision
            // requires the last vowel to be removed.
            if (mAutoCorrectOn && primaryCode != '\'' && !mJustReverted) {
                pickedDefault = pickDefaultSuggestion();
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
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
                && primaryCode == Keyboard.CODE_PERIOD) {
            reswapPeriodAndSpace();
        }

        TextEntryState.typedCharacter((char) primaryCode, true);
        if (TextEntryState.getState() == TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED
                && primaryCode != Keyboard.CODE_ENTER) {
            swapPunctuationAndSpace();
        } else if (isPredictionOn() && primaryCode == Keyboard.CODE_SPACE) {
            doubleSpace();
        }
        if (pickedDefault) {
            CharSequence typedWord = mWord.getTypedWord();
            TextEntryState.backToAcceptedDefault(typedWord);
            if (!TextUtils.isEmpty(typedWord) && !typedWord.equals(mBestWord)) {
                if (ic != null) {
                    CorrectionInfo correctionInfo = new CorrectionInfo(
                            mLastSelectionEnd - typedWord.length(), typedWord, mBestWord);
                    ic.commitCorrection(correctionInfo);
                }
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
        mVoiceConnector.handleClose();
        requestHideSelf(0);
        LatinKeyboardView inputView = mKeyboardSwitcher.getInputView();
        if (inputView != null)
            inputView.closing();
        TextEntryState.endSession();
    }

    private void saveWordInHistory(CharSequence result) {
        if (mWord.size() <= 1) {
            mWord.reset();
            return;
        }
        // Skip if result is null. It happens in some edge case.
        if (TextUtils.isEmpty(result)) {
            return;
        }

        // Make a copy of the CharSequence, since it is/could be a mutable CharSequence
        final String resultCopy = result.toString();
        TypedWordAlternatives entry = new TypedWordAlternatives(resultCopy,
                new WordComposer(mWord));
        mWordHistory.add(entry);
    }

    private boolean isPredictionOn() {
        return mPredictionOn;
    }

    private boolean isShowingPunctuationList() {
        return mSuggestPuncList == mCandidateView.getSuggestions();
    }

    private boolean isSuggestionShown() {
        return (mSuggestionVisibility == SUGGESTION_VISIBILILTY_SHOW_VALUE)
                || (mSuggestionVisibility == SUGGESTION_VISIBILILTY_SHOW_ONLY_PORTRAIT_VALUE
                        && mOrientation == Configuration.ORIENTATION_PORTRAIT);
    }

    private boolean isCandidateStripVisible() {
        boolean forceVisible = mCandidateView.isShowingAddToDictionaryHint()
                || TextEntryState.isCorrecting();
        return forceVisible || (
                isSuggestionShown() && (isPredictionOn() || mApplicationSpecifiedCompletionOn
                        || isShowingPunctuationList()));
    }

    public void switchToKeyboardView() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
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
        });
    }

    public void clearSuggestions() {
        setSuggestions(SuggestedWords.EMPTY);
    }

    public void setSuggestions(SuggestedWords words) {
        if (mVoiceConnector.getAndResetIsShowingHint()) {
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
        mKeyboardSwitcher.setPreferredLetters(null);

        // Check if we have a suggestion engine attached.
        if ((mSuggest == null || !isPredictionOn())
                && !mVoiceConnector.isVoiceInputHighlighted()) {
            return;
        }

        if (!mPredicting) {
            setPunctuationSuggestions();
            return;
        }
        showSuggestions(mWord);
    }

    private SuggestedWords.Builder getTypedSuggestions(WordComposer word) {
        return mSuggest.getSuggestedWordBuilder(mKeyboardSwitcher.getInputView(), word, null);
    }

    private void showCorrections(WordAlternatives alternatives) {
        mKeyboardSwitcher.setPreferredLetters(null);
        SuggestedWords.Builder builder = alternatives.getAlternatives();
        builder.setTypedWordValid(false).setHasMinimalSuggestion(false);
        showSuggestions(builder.build(), alternatives.getOriginalWord());
    }

    private void showSuggestions(WordComposer word) {
        // long startTime = System.currentTimeMillis(); // TIME MEASUREMENT!
        // TODO Maybe need better way of retrieving previous word
        CharSequence prevWord = EditingUtils.getPreviousWord(getCurrentInputConnection(),
                mWordSeparators);
        SuggestedWords.Builder builder = mSuggest.getSuggestedWordBuilder(
                mKeyboardSwitcher.getInputView(), word, prevWord);

        int[] nextLettersFrequencies = mSuggest.getNextLettersFrequencies();
        mKeyboardSwitcher.setPreferredLetters(nextLettersFrequencies);

        boolean correctionAvailable = !mInputTypeNoAutoCorrect && !mJustReverted
                && mSuggest.hasMinimalCorrection();
        CharSequence typedWord = word.getTypedWord();
        // If we're in basic correct
        boolean typedWordValid = mSuggest.isValidWord(typedWord) ||
                (preferCapitalization()
                        && mSuggest.isValidWord(typedWord.toString().toLowerCase()));
        if (mCorrectionMode == Suggest.CORRECTION_FULL
                || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM) {
            correctionAvailable |= typedWordValid;
        }
        // Don't auto-correct words with multiple capital letter
        correctionAvailable &= !word.isMostlyCaps();
        correctionAvailable &= !TextEntryState.isCorrecting();

        builder.setTypedWordValid(typedWordValid).setHasMinimalSuggestion(correctionAvailable);
        showSuggestions(builder.build(), typedWord);
    }

    private void showSuggestions(SuggestedWords suggestedWords, CharSequence typedWord) {
        setSuggestions(suggestedWords);
        if (suggestedWords.size() > 0) {
            if (suggestedWords.hasAutoCorrectionWord()) {
                mBestWord = suggestedWords.getWord(1);
            } else {
                mBestWord = typedWord;
            }
        } else {
            mBestWord = null;
        }
        setCandidatesViewShown(isCandidateStripVisible());
    }

    private boolean pickDefaultSuggestion() {
        // Complete any pending candidate query first
        if (mHandler.hasPendingUpdateSuggestions()) {
            mHandler.cancelUpdateSuggestions();
            updateSuggestions();
        }
        if (mBestWord != null && mBestWord.length() > 0) {
            TextEntryState.acceptedDefault(mWord.getTypedWord(), mBestWord);
            mJustAccepted = true;
            pickSuggestion(mBestWord);
            // Add the word to the auto dictionary if it's not a known word
            addToDictionaries(mBestWord, AutoDictionary.FREQUENCY_FOR_TYPED);
            return true;

        }
        return false;
    }

    public void pickSuggestionManually(int index, CharSequence suggestion) {
        SuggestedWords suggestions = mCandidateView.getSuggestions();
        mVoiceConnector.flushAndLogAllTextModificationCounters(index, suggestion, mWordSeparators);

        final boolean correcting = TextEntryState.isCorrecting();
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
            onKey(primaryCode, new int[]{primaryCode}, KeyboardView.NOT_A_TOUCH_COORDINATE,
                    KeyboardView.NOT_A_TOUCH_COORDINATE);
            if (ic != null) {
                ic.endBatchEdit();
            }
            return;
        }
        mJustAccepted = true;
        pickSuggestion(suggestion);
        // Add the word to the auto dictionary if it's not a known word
        if (index == 0) {
            addToDictionaries(suggestion, AutoDictionary.FREQUENCY_FOR_PICKED);
        } else {
            addToBigramDictionary(suggestion, 1);
        }
        LatinImeLogger.logOnManualSuggestion(mComposing.toString(), suggestion.toString(),
                index, suggestions.mWords);
        TextEntryState.acceptedSuggestion(mComposing.toString(), suggestion);
        // Follow it with a space
        if (mAutoSpace && !correcting) {
            sendSpace();
            mJustAddedAutoSpace = true;
        }

        final boolean showingAddToDictionaryHint = index == 0 && mCorrectionMode > 0
                && !mSuggest.isValidWord(suggestion)
                && !mSuggest.isValidWord(suggestion.toString().toLowerCase());

        if (!correcting) {
            // Fool the state watcher so that a subsequent backspace will not do a revert, unless
            // we just did a correction, in which case we need to stay in
            // TextEntryState.State.PICKED_SUGGESTION state.
            TextEntryState.typedCharacter((char) Keyboard.CODE_SPACE, true);
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
            mVoiceConnector.rememberReplacedWord(suggestion, mWordSeparators);
            ic.commitText(suggestion, 1);
        }
        saveWordInHistory(suggestion);
        mPredicting = false;
        mCommittedLength = suggestion.length();
        switcher.setPreferredLetters(null);
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
        for (WordAlternatives entry : mWordHistory) {
            if (TextUtils.equals(entry.getChosenWord(), touching.mWord)) {
                if (entry instanceof TypedWordAlternatives) {
                    foundWord = ((TypedWordAlternatives) entry).word;
                }
                alternatives = entry;
                break;
            }
        }
        // If we didn't find a match, at least suggest corrections.
        if (foundWord == null
                && (mSuggest.isValidWord(touching.mWord)
                        || mSuggest.isValidWord(touching.mWord.toString().toLowerCase()))) {
            foundWord = new WordComposer();
            for (int i = 0; i < touching.mWord.length(); i++) {
                foundWord.add(touching.mWord.charAt(i), new int[] {
                    touching.mWord.charAt(i)
                });
            }
            foundWord.setFirstCharCapitalized(Character.isUpperCase(touching.mWord.charAt(0)));
        }
        // Found a match, show suggestions
        if (foundWord != null || alternatives != null) {
            if (alternatives == null) {
                alternatives = new TypedWordAlternatives(touching.mWord, foundWord);
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
        mVoiceConnector.setShowingVoiceSuggestions(false);
        if (mCandidateView != null && mCandidateView.isShowingAddToDictionaryHint()) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!mPredicting) {
            // Extract the selected or touching text
            EditingUtils.SelectedWord touching = EditingUtils.getWordAtCursorOrSelection(ic,
                    mLastSelectionStart, mLastSelectionEnd, mWordSeparators);

            if (touching != null && touching.mWord.length() > 1) {
                ic.beginBatchEdit();

                if (!mVoiceConnector.applyVoiceAlternatives(touching)
                        && !applyTypedAlternatives(touching)) {
                    abortCorrection(true);
                } else {
                    TextEntryState.selectedForCorrection();
                    EditingUtils.underlineWord(ic, touching);
                }

                ic.endBatchEdit();
            } else {
                abortCorrection(true);
                setPunctuationSuggestions();  // Show the punctuation suggestions list
            }
        } else {
            abortCorrection(true);
        }
    }

    private void setPunctuationSuggestions() {
        setCandidatesViewShown(isCandidateStripVisible());
        setSuggestions(mSuggestPuncList);
    }

    private void addToDictionaries(CharSequence suggestion, int frequencyDelta) {
        checkAddToDictionary(suggestion, frequencyDelta, false);
    }

    private void addToBigramDictionary(CharSequence suggestion, int frequencyDelta) {
        checkAddToDictionary(suggestion, frequencyDelta, true);
    }

    /**
     * Adds to the UserBigramDictionary and/or AutoDictionary
     * @param addToBigramDictionary true if it should be added to bigram dictionary if possible
     */
    private void checkAddToDictionary(CharSequence suggestion, int frequencyDelta,
            boolean addToBigramDictionary) {
        if (suggestion == null || suggestion.length() < 1) return;
        // Only auto-add to dictionary if auto-correct is ON. Otherwise we'll be
        // adding words in situations where the user or application really didn't
        // want corrections enabled or learned.
        if (!(mCorrectionMode == Suggest.CORRECTION_FULL
                || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM)) {
            return;
        }
        if (!addToBigramDictionary && mAutoDictionary.isValidWord(suggestion)
                || (!mSuggest.isValidWord(suggestion.toString())
                        && !mSuggest.isValidWord(suggestion.toString().toLowerCase()))) {
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
        if (!mPredicting && length > 0) {
            final InputConnection ic = getCurrentInputConnection();
            mPredicting = true;
            mJustReverted = true;
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
            mHandler.postUpdateSuggestions();
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            mJustReverted = false;
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
        //onKey(KEY_SPACE[0], KEY_SPACE);
    }

    public boolean preferCapitalization() {
        return mWord.isFirstCharCapitalized();
    }

    // Notify that Language has been changed and toggleLanguage will update KeyboaredID according
    // to new Language.
    public void onKeyboardLanguageChanged() {
        toggleLanguage(true, true);
    }

    // "reset" and "next" are used only for USE_SPACEBAR_LANGUAGE_SWITCHER.
    private void toggleLanguage(boolean reset, boolean next) {
        if (SubtypeSwitcher.USE_SPACEBAR_LANGUAGE_SWITCHER) {
            mSubtypeSwitcher.toggleLanguage(reset, next);
        }
        // Reload keyboard because the current language has been changed.
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        final int mode = switcher.getKeyboardMode();
        final EditorInfo attribute = getCurrentInputEditorInfo();
        final int imeOptions = (attribute != null) ? attribute.imeOptions : 0;
        switcher.loadKeyboard(mode, imeOptions, mVoiceConnector.isVoiceButtonEnabled(),
                mVoiceConnector.isVoiceButtonOnPrimary());
        initSuggest();
        switcher.updateShiftState();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        mSubtypeSwitcher.onSharedPreferenceChanged(sharedPreferences, key);
        if (Settings.PREF_SELECTED_LANGUAGES.equals(key)) {
            mRefreshKeyboardRequired = true;
        } else if (Settings.PREF_RECORRECTION_ENABLED.equals(key)) {
            mReCorrectionEnabled = sharedPreferences.getBoolean(
                    Settings.PREF_RECORRECTION_ENABLED,
                    getResources().getBoolean(R.bool.default_recorrection_enabled));
        }
    }

    @Override
    public void swipeRight() {
        // Nothing to do
    }

    @Override
    public void swipeLeft() {
        // Nothing to do
    }

    @Override
    public void swipeDown() {
        handleClose();
    }

    @Override
    public void swipeUp() {
        // Nothing to do
    }

    @Override
    public void onPress(int primaryCode) {
        if (mKeyboardSwitcher.isVibrateAndSoundFeedbackRequired()) {
            vibrate();
            playKeyClick(primaryCode);
        }
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        final boolean distinctMultiTouch = switcher.hasDistinctMultitouch();
        if (distinctMultiTouch && primaryCode == Keyboard.CODE_SHIFT) {
            switcher.onPressShift();
        } else if (distinctMultiTouch && primaryCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
            switcher.onPressSymbol();
        } else {
            switcher.onOtherKeyPressed();
        }
    }

    @Override
    public void onRelease(int primaryCode) {
        KeyboardSwitcher switcher = mKeyboardSwitcher;
        // Reset any drag flags in the keyboard
        switcher.keyReleased();
        final boolean distinctMultiTouch = switcher.hasDistinctMultitouch();
        if (distinctMultiTouch && primaryCode == Keyboard.CODE_SHIFT) {
            switcher.onReleaseShift();
        } else if (distinctMultiTouch && primaryCode == Keyboard.CODE_SWITCH_ALPHA_SYMBOL) {
            switcher.onReleaseSymbol();
        }
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
        mSuggest.setAutoTextEnabled(mQuickFixes
                && SubtypeSwitcher.getInstance().isSystemLanguageSameAsInputLanguage());
    }

    private void updateSuggestionVisibility(SharedPreferences prefs) {
        final String suggestionVisiblityStr = prefs.getString(
                Settings.PREF_SHOW_SUGGESTIONS_SETTING,
                mResources.getString(R.string.prefs_suggestion_visibility_default_value));
        for (int visibility : SUGGESTION_VISIBILITY_VALUE_ARRAY) {
            if (suggestionVisiblityStr.equals(mResources.getString(visibility))) {
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
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mVibrateOn = vibrator != null && vibrator.hasVibrator()
                && prefs.getBoolean(Settings.PREF_VIBRATE_ON, false);
        mSoundOn = prefs.getBoolean(Settings.PREF_SOUND_ON, false);
        mPopupOn = prefs.getBoolean(Settings.PREF_POPUP_ON,
                mResources.getBoolean(R.bool.config_default_popup_preview));
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true);
        mQuickFixes = prefs.getBoolean(Settings.PREF_QUICK_FIXES, true);

        mAutoCorrectEnabled = isAutoCorrectEnabled(prefs);
        mBigramSuggestionEnabled = mAutoCorrectEnabled && isBigramSuggestionEnabled(prefs);
        loadAndSetAutoCorrectionThreshold(prefs);

        mVoiceConnector.loadSettings(attribute, prefs);

        updateCorrectionMode();
        updateAutoTextEnabled();
        updateSuggestionVisibility(prefs);
        SubtypeSwitcher.getInstance().loadSettings();
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

    private boolean isAutoCorrectEnabled(SharedPreferences sp) {
        final String currentAutoCorrectionSetting = sp.getString(
                Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                mResources.getString(R.string.auto_correction_threshold_mode_index_modest));
        final String autoCorrectionOff = mResources.getString(
                R.string.auto_correction_threshold_mode_index_off);
        return !currentAutoCorrectionSetting.equals(autoCorrectionOff);
    }

    private boolean isBigramSuggestionEnabled(SharedPreferences sp) {
       // TODO: Define default value instead of 'true'.
       return sp.getBoolean(Settings.PREF_BIGRAM_SUGGESTIONS, true);
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

    private void showOptionsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.ic_dialog_keyboard);
        builder.setNegativeButton(android.R.string.cancel, null);
        CharSequence itemSettings = getString(R.string.english_ime_settings);
        CharSequence itemInputMethod = getString(R.string.selectInputMethod);
        builder.setItems(new CharSequence[] {
                itemInputMethod, itemSettings},
                new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                    case POS_SETTINGS:
                        launchSettings();
                        break;
                    case POS_METHOD:
                        mImm.showInputMethodPicker();
                        break;
                }
            }
        });
        builder.setTitle(mResources.getString(R.string.english_ime_input_options));
        mOptionsDialog = builder.create();
        Window window = mOptionsDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mKeyboardSwitcher.getInputView().getWindowToken();
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
        p.println("  mPredictionOn=" + mPredictionOn);
        p.println("  mCorrectionMode=" + mCorrectionMode);
        p.println("  mPredicting=" + mPredicting);
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

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        SubtypeSwitcher.getInstance().updateSubtype(subtype);
    }
}
