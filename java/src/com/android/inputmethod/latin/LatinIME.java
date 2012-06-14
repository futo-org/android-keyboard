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

import static com.android.inputmethod.latin.Constants.ImeOption.FORCE_ASCII;
import static com.android.inputmethod.latin.Constants.ImeOption.NO_MICROPHONE;
import static com.android.inputmethod.latin.Constants.ImeOption.NO_MICROPHONE_COMPAT;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Debug;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.AccessibleKeyboardViewProxy;
import com.android.inputmethod.compat.CompatUtils;
import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.compat.SuggestionSpanUtils;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.LatinKeyboardView;
import com.android.inputmethod.latin.LocaleUtils.RunInLocale;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.suggestions.SuggestionsView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService implements KeyboardActionListener,
        SuggestionsView.Listener, TargetApplicationGetter.OnTargetApplicationKnownListener {
    private static final String TAG = LatinIME.class.getSimpleName();
    private static final boolean TRACE = false;
    private static boolean DEBUG;

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

    /** Whether to use the binary version of the contacts dictionary */
    public static final boolean USE_BINARY_CONTACTS_DICTIONARY = true;

    /** Whether to use the binary version of the user dictionary */
    public static final boolean USE_BINARY_USER_DICTIONARY = true;

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

    private SettingsValues mSettingsValues;
    private InputAttributes mInputAttributes;

    private View mExtractArea;
    private View mKeyPreviewBackingView;
    private View mSuggestionsContainer;
    private SuggestionsView mSuggestionsView;
    /* package for tests */ Suggest mSuggest;
    private CompletionInfo[] mApplicationSpecifiedCompletions;
    private ApplicationInfo mTargetApplicationInfo;

    private InputMethodManagerCompatWrapper mImm;
    private Resources mResources;
    private SharedPreferences mPrefs;
    /* package for tests */ final KeyboardSwitcher mKeyboardSwitcher;
    private final SubtypeSwitcher mSubtypeSwitcher;
    private boolean mShouldSwitchToLastSubtype = true;

    private boolean mIsMainDictionaryAvailable;
    // TODO: revert this back to the concrete class after transition.
    private Dictionary mUserDictionary;
    private UserHistoryDictionary mUserHistoryDictionary;
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

    private AudioAndHapticFeedbackManager mFeedbackManager;

    // Member variables for remembering the current device orientation.
    private int mDisplayOrientation;

    // Object for reacting to adding/removing a dictionary pack.
    private BroadcastReceiver mDictionaryPackInstallReceiver =
            new DictionaryPackInstallBroadcastReceiver(this);

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private CharSequence mEnteredText;

    private boolean mIsAutoCorrectionIndicatorOn;

    private AlertDialog mOptionsDialog;

    public final UIHandler mHandler = new UIHandler(this);

    public static class UIHandler extends StaticInnerHandlerWrapper<LatinIME> {
        private static final int MSG_UPDATE_SHIFT_STATE = 1;
        private static final int MSG_SPACE_TYPED = 4;
        private static final int MSG_SET_BIGRAM_PREDICTIONS = 5;
        private static final int MSG_PENDING_IMS_CALLBACK = 6;
        private static final int MSG_UPDATE_SUGGESTIONS = 7;

        private int mDelayUpdateSuggestions;
        private int mDelayUpdateShiftState;
        private long mDoubleSpacesTurnIntoPeriodTimeout;

        public UIHandler(LatinIME outerInstance) {
            super(outerInstance);
        }

        public void onCreate() {
            final Resources res = getOuterInstance().getResources();
            mDelayUpdateSuggestions =
                    res.getInteger(R.integer.config_delay_update_suggestions);
            mDelayUpdateShiftState =
                    res.getInteger(R.integer.config_delay_update_shift_state);
            mDoubleSpacesTurnIntoPeriodTimeout = res.getInteger(
                    R.integer.config_double_spaces_turn_into_period_timeout);
        }

        @Override
        public void handleMessage(Message msg) {
            final LatinIME latinIme = getOuterInstance();
            final KeyboardSwitcher switcher = latinIme.mKeyboardSwitcher;
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

        public void postUpdateShiftState() {
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

    public LatinIME() {
        super();
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
    }

    @Override
    public void onCreate() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs = prefs;
        LatinImeLogger.init(this, prefs);
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.init(this, prefs);
        }
        InputMethodManagerCompatWrapper.init(this);
        SubtypeSwitcher.init(this);
        KeyboardSwitcher.init(this, prefs);
        AccessibilityUtils.init(this);

        super.onCreate();

        mImm = InputMethodManagerCompatWrapper.getInstance();
        mHandler.onCreate();
        DEBUG = LatinImeLogger.sDBG;

        final Resources res = getResources();
        mResources = res;

        loadSettings();

        ImfUtils.setAdditionalInputMethodSubtypes(this, mSettingsValues.getAdditionalSubtypes());

        // TODO: remove the following when it's not needed by updateCorrectionMode() any more
        mInputAttributes = new InputAttributes(null, false /* isFullscreenMode */);
        updateCorrectionMode();

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
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);

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
        // Note that the calling sequence of onCreate() and onCurrentInputMethodSubtypeChanged()
        // is not guaranteed. It may even be called at the same time on a different thread.
        if (null == mPrefs) mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        final RunInLocale<SettingsValues> job = new RunInLocale<SettingsValues>() {
            @Override
            protected SettingsValues job(Resources res) {
                return new SettingsValues(mPrefs, LatinIME.this);
            }
        };
        mSettingsValues = job.runInLocale(mResources, mSubtypeSwitcher.getCurrentSubtypeLocale());
        mFeedbackManager = new AudioAndHapticFeedbackManager(this, mSettingsValues);
        resetContactsDictionary(null == mSuggest ? null : mSuggest.getContactsDictionary());
    }

    private void initSuggest() {
        final Locale subtypeLocale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        final String localeStr = subtypeLocale.toString();

        final Dictionary oldContactsDictionary;
        if (mSuggest != null) {
            oldContactsDictionary = mSuggest.getContactsDictionary();
            mSuggest.close();
        } else {
            oldContactsDictionary = null;
        }
        mSuggest = new Suggest(this, subtypeLocale);
        if (mSettingsValues.mAutoCorrectEnabled) {
            mSuggest.setAutoCorrectionThreshold(mSettingsValues.mAutoCorrectionThreshold);
        }

        mIsMainDictionaryAvailable = DictionaryFactory.isDictionaryAvailable(this, subtypeLocale);

        if (USE_BINARY_USER_DICTIONARY) {
            mUserDictionary = new UserBinaryDictionary(this, localeStr);
            mIsUserDictionaryAvailable = ((UserBinaryDictionary)mUserDictionary).isEnabled();
        } else {
            mUserDictionary = new UserDictionary(this, localeStr);
            mIsUserDictionaryAvailable = ((UserDictionary)mUserDictionary).isEnabled();
        }
        mSuggest.setUserDictionary(mUserDictionary);

        resetContactsDictionary(oldContactsDictionary);

        // Note that the calling sequence of onCreate() and onCurrentInputMethodSubtypeChanged()
        // is not guaranteed. It may even be called at the same time on a different thread.
        if (null == mPrefs) mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mUserHistoryDictionary = UserHistoryDictionary.getInstance(
                this, localeStr, Suggest.DIC_USER_HISTORY, mPrefs);
        mSuggest.setUserHistoryDictionary(mUserHistoryDictionary);
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
    private void resetContactsDictionary(final Dictionary oldContactsDictionary) {
        final boolean shouldSetDictionary = (null != mSuggest && mSettingsValues.mUseContactsDict);

        final Dictionary dictionaryToUse;
        if (!shouldSetDictionary) {
            // Make sure the dictionary is closed. If it is already closed, this is a no-op,
            // so it's safe to call it anyways.
            if (null != oldContactsDictionary) oldContactsDictionary.close();
            dictionaryToUse = null;
        } else if (null != oldContactsDictionary) {
            // Make sure the old contacts dictionary is opened. If it is already open, this is a
            // no-op, so it's safe to call it anyways.
            if (USE_BINARY_CONTACTS_DICTIONARY) {
                ((ContactsBinaryDictionary)oldContactsDictionary).reopen(this);
            } else {
                ((ContactsDictionary)oldContactsDictionary).reopen(this);
            }
            dictionaryToUse = oldContactsDictionary;
        } else {
            if (USE_BINARY_CONTACTS_DICTIONARY) {
                dictionaryToUse = new ContactsBinaryDictionary(this, Suggest.DIC_CONTACTS,
                        mSubtypeSwitcher.getCurrentSubtypeLocale());
            } else {
                dictionaryToUse = new ContactsDictionary(this, Suggest.DIC_CONTACTS);
            }
        }

        if (null != mSuggest) {
            mSuggest.setContactsDictionary(dictionaryToUse);
        }
    }

    /* package private */ void resetSuggestMainDict() {
        final Locale subtypeLocale = mSubtypeSwitcher.getCurrentSubtypeLocale();
        mSuggest.resetMainDict(this, subtypeLocale);
        mIsMainDictionaryAvailable = DictionaryFactory.isDictionaryAvailable(this, subtypeLocale);
    }

    @Override
    public void onDestroy() {
        if (mSuggest != null) {
            mSuggest.close();
            mSuggest = null;
        }
        unregisterReceiver(mReceiver);
        unregisterReceiver(mDictionaryPackInstallReceiver);
        LatinImeLogger.commit();
        LatinImeLogger.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        mSubtypeSwitcher.onConfigurationChanged(conf);
        // If orientation changed while predicting, commit the change
        if (mDisplayOrientation != conf.orientation) {
            mDisplayOrientation = conf.orientation;
            mHandler.startOrientationChanging();
            final InputConnection ic = getCurrentInputConnection();
            commitTyped(ic, LastComposedWord.NOT_A_SEPARATOR);
            if (ic != null) ic.finishComposingText(); // For voice input
            if (isShowingOptionDialog())
                mOptionsDialog.dismiss();
        }
        super.onConfigurationChanged(conf);
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

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        // Note that the calling sequence of onCreate() and onCurrentInputMethodSubtypeChanged()
        // is not guaranteed. It may even be called at the same time on a different thread.
        mSubtypeSwitcher.updateSubtype(subtype);
    }

    private void onStartInputInternal(EditorInfo editorInfo, boolean restarting) {
        super.onStartInput(editorInfo, restarting);
    }

    @SuppressWarnings("deprecation")
    private void onStartInputViewInternal(EditorInfo editorInfo, boolean restarting) {
        super.onStartInputView(editorInfo, restarting);
        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        LatinKeyboardView inputView = switcher.getKeyboardView();

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
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_onStartInputViewInternal(editorInfo, mPrefs);
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

        mTargetApplicationInfo =
                TargetApplicationGetter.getCachedApplicationInfo(editorInfo.packageName);
        if (null == mTargetApplicationInfo) {
            new TargetApplicationGetter(this /* context */, this /* listener */)
                    .execute(editorInfo.packageName);
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

        // The EditorInfo might have a flag that affects fullscreen mode.
        // Note: This call should be done by InputMethodService?
        updateFullscreenMode();
        mLastSelectionStart = editorInfo.initialSelStart;
        mLastSelectionEnd = editorInfo.initialSelEnd;
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

        switcher.loadKeyboard(editorInfo, mSettingsValues);

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

        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    public void onTargetApplicationKnown(final ApplicationInfo info) {
        mTargetApplicationInfo = info;
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

        KeyboardView inputView = mKeyboardSwitcher.getKeyboardView();
        if (inputView != null) inputView.closing();
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
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int composingSpanStart, int composingSpanEnd) {
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
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_onUpdateSelection(mLastSelectionStart, mLastSelectionEnd,
                    oldSelStart, oldSelEnd, newSelStart, newSelEnd, composingSpanStart,
                    composingSpanEnd);
        }

        // TODO: refactor the following code to be less contrived.
        // "newSelStart != composingSpanEnd" || "newSelEnd != composingSpanEnd" means
        // that the cursor is not at the end of the composing span, or there is a selection.
        // "mLastSelectionStart != newSelStart" means that the cursor is not in the same place
        // as last time we were called (if there is a selection, it means the start hasn't
        // changed, so it's the end that did).
        final boolean selectionChanged = (newSelStart != composingSpanEnd
                || newSelEnd != composingSpanEnd) && mLastSelectionStart != newSelStart;
        // if composingSpanStart and composingSpanEnd are -1, it means there is no composing
        // span in the view - we can use that to narrow down whether the cursor was moved
        // by us or not. If we are composing a word but there is no composing span, then
        // we know for sure the cursor moved while we were composing and we should reset
        // the state.
        final boolean noComposingSpan = composingSpanStart == -1 && composingSpanEnd == -1;
        if (!mExpectingUpdateSelection) {
            // TAKE CARE: there is a race condition when we enter this test even when the user
            // did not explicitly move the cursor. This happens when typing fast, where two keys
            // turn this flag on in succession and both onUpdateSelection() calls arrive after
            // the second one - the first call successfully avoids this test, but the second one
            // enters. For the moment we rely on noComposingSpan to further reduce the impact.

            // TODO: the following is probably better done in resetEntireInputState().
            // it should only happen when the cursor moved, and the very purpose of the
            // test below is to narrow down whether this happened or not. Likewise with
            // the call to postUpdateShiftState.
            // We set this to NONE because after a cursor move, we don't want the space
            // state-related special processing to kick in.
            mSpaceState = SPACE_STATE_NONE;

            if ((!mWordComposer.isComposingWord()) || selectionChanged || noComposingSpan) {
                resetEntireInputState();
            }

            mHandler.postUpdateShiftState();
        }
        mExpectingUpdateSelection = false;
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
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_onDisplayCompletions(applicationSpecifiedCompletions);
        }
        if (mInputAttributes.mApplicationSpecifiedCompletionOn) {
            mApplicationSpecifiedCompletions = applicationSpecifiedCompletions;
            if (applicationSpecifiedCompletions == null) {
                clearSuggestions();
                return;
            }

            final ArrayList<SuggestedWords.SuggestedWordInfo> applicationSuggestedWords =
                    SuggestedWords.getFromApplicationSpecifiedCompletions(
                            applicationSpecifiedCompletions);
            final SuggestedWords suggestedWords = new SuggestedWords(
                    applicationSuggestedWords,
                    false /* typedWordValid */,
                    false /* hasAutoCorrectionCandidate */,
                    false /* allowsToBeAutoCorrected */,
                    false /* isPunctuationSuggestions */,
                    false /* isObsoleteSuggestions */,
                    false /* isPrediction */);
            // When in fullscreen mode, show completions generated by the application
            final boolean isAutoCorrection = false;
            setSuggestions(suggestedWords, isAutoCorrection);
            setAutoCorrectionIndicator(isAutoCorrection);
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

    private int getAdjustedBackingViewHeight() {
        final int currentHeight = mKeyPreviewBackingView.getHeight();
        if (currentHeight > 0) {
            return currentHeight;
        }

        final KeyboardView keyboardView = mKeyboardSwitcher.getKeyboardView();
        if (keyboardView == null) {
            return 0;
        }
        final int keyboardHeight = keyboardView.getHeight();
        final int suggestionsHeight = mSuggestionsContainer.getHeight();
        final int displayHeight = mResources.getDisplayMetrics().heightPixels;
        final Rect rect = new Rect();
        mKeyPreviewBackingView.getWindowVisibleDisplayFrame(rect);
        final int notificationBarHeight = rect.top;
        final int remainingHeight = displayHeight - notificationBarHeight - suggestionsHeight
                - keyboardHeight;

        final LayoutParams params = mKeyPreviewBackingView.getLayoutParams();
        params.height = mSuggestionsView.setMoreSuggestionsHeight(remainingHeight);
        mKeyPreviewBackingView.setLayoutParams(params);
        return params.height;
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        final KeyboardView inputView = mKeyboardSwitcher.getKeyboardView();
        if (inputView == null || mSuggestionsContainer == null)
            return;
        final int adjustedBackingHeight = getAdjustedBackingViewHeight();
        final boolean backingGone = (mKeyPreviewBackingView.getVisibility() == View.GONE);
        final int backingHeight = backingGone ? 0 : adjustedBackingHeight;
        // In fullscreen mode, the height of the extract area managed by InputMethodService should
        // be considered.
        // See {@link android.inputmethodservice.InputMethodService#onComputeInsets}.
        final int extractHeight = isFullscreenMode() ? mExtractArea.getHeight() : 0;
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
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
            outInsets.touchableRegion.set(0, touchY, touchWidth, touchHeight);
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

    // This will reset the whole input state to the starting state. It will clear
    // the composing word, reset the last composed word, tell the inputconnection
    // and the composingStateManager about it.
    private void resetEntireInputState() {
        resetComposingState(true /* alsoResetLastComposedWord */);
        updateSuggestions();
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.finishComposingText();
        }
    }

    private void resetComposingState(final boolean alsoResetLastComposedWord) {
        mWordComposer.reset();
        if (alsoResetLastComposedWord)
            mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
    }

    public void commitTyped(final InputConnection ic, final int separatorCode) {
        if (!mWordComposer.isComposingWord()) return;
        final CharSequence typedWord = mWordComposer.getTypedWord();
        if (typedWord.length() > 0) {
            if (ic != null) {
                ic.commitText(typedWord, 1);
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.latinIME_commitText(typedWord);
                }
            }
            final CharSequence prevWord = addToUserHistoryDictionary(typedWord);
            mLastComposedWord = mWordComposer.commitWord(
                    LastComposedWord.COMMIT_TYPE_USER_TYPED_WORD, typedWord.toString(),
                    separatorCode, prevWord);
        }
        updateSuggestions();
    }

    public int getCurrentAutoCapsState() {
        if (!mSettingsValues.mAutoCap) return Constants.TextUtils.CAP_MODE_OFF;

        final EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return Constants.TextUtils.CAP_MODE_OFF;

        final int inputType = ei.inputType;
        if ((inputType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
            return TextUtils.CAP_MODE_CHARACTERS;
        }

        final boolean noNeedToCheckCapsMode = (inputType & (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS)) == 0;
        if (noNeedToCheckCapsMode) return Constants.TextUtils.CAP_MODE_OFF;

        // Avoid making heavy round-trip IPC calls of {@link InputConnection#getCursorCapsMode}
        // unless needed.
        if (mWordComposer.isComposingWord()) return Constants.TextUtils.CAP_MODE_OFF;

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return Constants.TextUtils.CAP_MODE_OFF;
        // TODO: This blocking IPC call is heavy. Consider doing this without using IPC calls.
        // Note: getCursorCapsMode() returns the current capitalization mode that is any
        // combination of CAP_MODE_CHARACTERS, CAP_MODE_WORDS, and CAP_MODE_SENTENCES. 0 means none
        // of them.
        return ic.getCursorCapsMode(inputType);
    }

    // "ic" may be null
    private void swapSwapperAndSpaceWhileInBatchEdit(final InputConnection ic) {
        if (null == ic) return;
        CharSequence lastTwo = ic.getTextBeforeCursor(2, 0);
        // It is guaranteed lastTwo.charAt(1) is a swapper - else this method is not called.
        if (lastTwo != null && lastTwo.length() == 2
                && lastTwo.charAt(0) == Keyboard.CODE_SPACE) {
            ic.deleteSurroundingText(2, 0);
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_deleteSurroundingText(2);
            }
            ic.commitText(lastTwo.charAt(1) + " ", 1);
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_swapSwapperAndSpaceWhileInBatchEdit();
            }
            mKeyboardSwitcher.updateShiftState();
        }
    }

    private boolean maybeDoubleSpaceWhileInBatchEdit(final InputConnection ic) {
        if (mCorrectionMode == Suggest.CORRECTION_NONE) return false;
        if (ic == null) return false;
        final CharSequence lastThree = ic.getTextBeforeCursor(3, 0);
        if (lastThree != null && lastThree.length() == 3
                && canBeFollowedByPeriod(lastThree.charAt(0))
                && lastThree.charAt(1) == Keyboard.CODE_SPACE
                && lastThree.charAt(2) == Keyboard.CODE_SPACE
                && mHandler.isAcceptingDoubleSpaces()) {
            mHandler.cancelDoubleSpacesTimer();
            ic.deleteSurroundingText(2, 0);
            ic.commitText(". ", 1);
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_doubleSpaceAutoPeriod();
            }
            mKeyboardSwitcher.updateShiftState();
            return true;
        }
        return false;
    }

    private static boolean canBeFollowedByPeriod(final int codePoint) {
        // TODO: Check again whether there really ain't a better way to check this.
        // TODO: This should probably be language-dependant...
        return Character.isLetterOrDigit(codePoint)
                || codePoint == Keyboard.CODE_SINGLE_QUOTE
                || codePoint == Keyboard.CODE_DOUBLE_QUOTE
                || codePoint == Keyboard.CODE_CLOSING_PARENTHESIS
                || codePoint == Keyboard.CODE_CLOSING_SQUARE_BRACKET
                || codePoint == Keyboard.CODE_CLOSING_CURLY_BRACKET
                || codePoint == Keyboard.CODE_CLOSING_ANGLE_BRACKET;
    }

    // "ic" may be null
    private static void removeTrailingSpaceWhileInBatchEdit(final InputConnection ic) {
        if (ic == null) return;
        final CharSequence lastOne = ic.getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == Keyboard.CODE_SPACE) {
            ic.deleteSurroundingText(1, 0);
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_deleteSurroundingText(1);
            }
        }
    }

    @Override
    public boolean addWordToDictionary(String word) {
        if (USE_BINARY_USER_DICTIONARY) {
            ((UserBinaryDictionary)mUserDictionary).addWordToUserDictionary(word, 128);
        } else {
            ((UserDictionary)mUserDictionary).addWordToUserDictionary(word, 128);
        }
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
        showSubtypeSelectorAndSettings();
    }

    // Virtual codes representing custom requests.  These are used in onCustomRequest() below.
    public static final int CODE_SHOW_INPUT_METHOD_PICKER = 1;

    @Override
    public boolean onCustomRequest(int requestCode) {
        if (isShowingOptionDialog()) return false;
        switch (requestCode) {
        case CODE_SHOW_INPUT_METHOD_PICKER:
            if (ImfUtils.hasMultipleEnabledIMEsOrSubtypes(
                    this, true /* include aux subtypes */)) {
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

    private static int getActionId(Keyboard keyboard) {
        return keyboard != null ? keyboard.mId.imeActionId() : EditorInfo.IME_ACTION_NONE;
    }

    private void performEditorAction(int actionId) {
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.performEditorAction(actionId);
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_performEditorAction(actionId);
            }
        }
    }

    private void handleLanguageSwitchKey() {
        final boolean includesOtherImes = mSettingsValues.mIncludesOtherImesInLanguageSwitchList;
        final IBinder token = getWindow().getWindow().getAttributes().token;
        if (mShouldSwitchToLastSubtype) {
            final InputMethodSubtype lastSubtype = mImm.getLastInputMethodSubtype();
            final boolean lastSubtypeBelongsToThisIme =
                    ImfUtils.checkIfSubtypeBelongsToThisImeAndEnabled(this, lastSubtype);
            if ((includesOtherImes || lastSubtypeBelongsToThisIme)
                    && mImm.switchToLastInputMethod(token)) {
                mShouldSwitchToLastSubtype = false;
            } else {
                mImm.switchToNextInputMethod(token, !includesOtherImes);
                mShouldSwitchToLastSubtype = true;
            }
        } else {
            mImm.switchToNextInputMethod(token, !includesOtherImes);
        }
    }

    static private void sendUpDownEnterOrBackspace(final int code, final InputConnection ic) {
        final long eventTime = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        ic.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    private void sendKeyCodePoint(int code) {
        // TODO: Remove this special handling of digit letters.
        // For backward compatibility. See {@link InputMethodService#sendKeyChar(char)}.
        if (code >= '0' && code <= '9') {
            super.sendKeyChar((char)code);
            return;
        }

        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            // 16 is android.os.Build.VERSION_CODES.JELLY_BEAN but we can't write it because
            // we want to be able to compile against the Ice Cream Sandwich SDK.
            if (Keyboard.CODE_ENTER == code && mTargetApplicationInfo != null
                    && mTargetApplicationInfo.targetSdkVersion < 16) {
                // Backward compatibility mode. Before Jelly bean, the keyboard would simulate
                // a hardware keyboard event on pressing enter or delete. This is bad for many
                // reasons (there are race conditions with commits) but some applications are
                // relying on this behavior so we continue to support it for older apps.
                sendUpDownEnterOrBackspace(KeyEvent.KEYCODE_ENTER, ic);
            } else {
                final String text = new String(new int[] { code }, 0, 1);
                ic.commitText(text, text.length());
            }
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_sendKeyCodePoint(code);
            }
        }
    }

    // Implementation of {@link KeyboardActionListener}.
    @Override
    public void onCodeInput(int primaryCode, int x, int y) {
        final long when = SystemClock.uptimeMillis();
        if (primaryCode != Keyboard.CODE_DELETE || when > mLastKeyTime + QUICK_PRESS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;

        if (ProductionFlag.IS_EXPERIMENTAL) {
            if (ResearchLogger.sIsLogging) {
                ResearchLogger.getInstance().logKeyEvent(primaryCode, x, y);
            }
        }

        final KeyboardSwitcher switcher = mKeyboardSwitcher;
        // The space state depends only on the last character pressed and its own previous
        // state. Here, we revert the space state to neutral if the key is actually modifying
        // the input contents (any non-shift key), which is what we should do for
        // all inputs that do not result in a special state. Each character handling is then
        // free to override the state as they see fit.
        final int spaceState = mSpaceState;
        if (!mWordComposer.isComposingWord()) mIsAutoCorrectionIndicatorOn = false;

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
            mShouldSwitchToLastSubtype = true;
            LatinImeLogger.logOnDelete(x, y);
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
        case Keyboard.CODE_ACTION_ENTER:
            performEditorAction(getActionId(switcher.getKeyboard()));
            break;
        case Keyboard.CODE_ACTION_NEXT:
            performEditorAction(EditorInfo.IME_ACTION_NEXT);
            break;
        case Keyboard.CODE_ACTION_PREVIOUS:
            performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
            break;
        case Keyboard.CODE_LANGUAGE_SWITCH:
            handleLanguageSwitchKey();
            break;
        default:
            if (primaryCode == Keyboard.CODE_TAB
                    && mInputAttributes.mEditorAction == EditorInfo.IME_ACTION_NEXT) {
                performEditorAction(EditorInfo.IME_ACTION_NEXT);
                break;
            }
            mSpaceState = SPACE_STATE_NONE;
            if (mSettingsValues.isWordSeparator(primaryCode)) {
                didAutoCorrect = handleSeparator(primaryCode, x, y, spaceState);
            } else {
                final Keyboard keyboard = mKeyboardSwitcher.getKeyboard();
                if (keyboard != null && keyboard.hasProximityCharsCorrection(primaryCode)) {
                    handleCharacter(primaryCode, x, y, spaceState);
                } else {
                    handleCharacter(primaryCode, NOT_A_TOUCH_COORDINATE, NOT_A_TOUCH_COORDINATE,
                            spaceState);
                }
            }
            mExpectingUpdateSelection = true;
            mShouldSwitchToLastSubtype = true;
            break;
        }
        switcher.onCodeInput(primaryCode);
        // Reset after any single keystroke, except shift and symbol-shift
        if (!didAutoCorrect && primaryCode != Keyboard.CODE_SHIFT
                && primaryCode != Keyboard.CODE_SWITCH_ALPHA_SYMBOL)
            mLastComposedWord.deactivate();
        mEnteredText = null;
    }

    @Override
    public void onTextInput(CharSequence text) {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        commitTyped(ic, LastComposedWord.NOT_A_SEPARATOR);
        text = specificTldProcessingOnTextInput(ic, text);
        if (SPACE_STATE_PHANTOM == mSpaceState) {
            sendKeyCodePoint(Keyboard.CODE_SPACE);
        }
        ic.commitText(text, 1);
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_commitText(text);
        }
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
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        handleBackspaceWhileInBatchEdit(spaceState, ic);
        ic.endBatchEdit();
    }

    // "ic" may not be null.
    private void handleBackspaceWhileInBatchEdit(final int spaceState, final InputConnection ic) {
        // In many cases, we may have to put the keyboard in auto-shift state again.
        mHandler.postUpdateShiftState();

        if (mEnteredText != null && sameAsTextBeforeCursor(ic, mEnteredText)) {
            // Cancel multi-character input: remove the text we just entered.
            // This is triggered on backspace after a key that inputs multiple characters,
            // like the smiley key or the .com key.
            final int length = mEnteredText.length();
            ic.deleteSurroundingText(length, 0);
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_deleteSurroundingText(length);
            }
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
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.latinIME_deleteSurroundingText(1);
                }
            }
        } else {
            if (mLastComposedWord.canRevertCommit()) {
                Utils.Stats.onAutoCorrectionCancellation();
                revertCommit(ic);
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

            // No cancelling of commit/double space/swap: we have a regular backspace.
            // We should backspace one char and restart suggestion if at the end of a word.
            if (mLastSelectionStart != mLastSelectionEnd) {
                // If there is a selection, remove it.
                final int lengthToDelete = mLastSelectionEnd - mLastSelectionStart;
                ic.setSelection(mLastSelectionEnd, mLastSelectionEnd);
                ic.deleteSurroundingText(lengthToDelete, 0);
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.latinIME_deleteSurroundingText(lengthToDelete);
                }
            } else {
                // There is no selection, just delete one character.
                if (NOT_A_CURSOR_POSITION == mLastSelectionEnd) {
                    // This should never happen.
                    Log.e(TAG, "Backspace when we don't know the selection position");
                }
                // 16 is android.os.Build.VERSION_CODES.JELLY_BEAN but we can't write it because
                // we want to be able to compile against the Ice Cream Sandwich SDK.
                if (mTargetApplicationInfo != null
                        && mTargetApplicationInfo.targetSdkVersion < 16) {
                    // Backward compatibility mode. Before Jelly bean, the keyboard would simulate
                    // a hardware keyboard event on pressing enter or delete. This is bad for many
                    // reasons (there are race conditions with commits) but some applications are
                    // relying on this behavior so we continue to support it for older apps.
                    sendUpDownEnterOrBackspace(KeyEvent.KEYCODE_DEL, ic);
                } else {
                    ic.deleteSurroundingText(1, 0);
                }
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.latinIME_deleteSurroundingText(1);
                }
                if (mDeleteCount > DELETE_ACCELERATE_AT) {
                    ic.deleteSurroundingText(1, 0);
                    if (ProductionFlag.IS_EXPERIMENTAL) {
                        ResearchLogger.latinIME_deleteSurroundingText(1);
                    }
                }
            }
            if (isSuggestionsRequested()) {
                restartSuggestionsOnWordBeforeCursorIfAtEndOfWord(ic);
            }
        }
    }

    // ic may be null
    private boolean maybeStripSpaceWhileInBatchEdit(final InputConnection ic, final int code,
            final int spaceState, final boolean isFromSuggestionStrip) {
        if (Keyboard.CODE_ENTER == code && SPACE_STATE_SWAP_PUNCTUATION == spaceState) {
            removeTrailingSpaceWhileInBatchEdit(ic);
            return false;
        } else if ((SPACE_STATE_WEAK == spaceState
                || SPACE_STATE_SWAP_PUNCTUATION == spaceState)
                && isFromSuggestionStrip) {
            if (mSettingsValues.isWeakSpaceSwapper(code)) {
                return true;
            } else {
                if (mSettingsValues.isWeakSpaceStripper(code)) {
                    removeTrailingSpaceWhileInBatchEdit(ic);
                }
                return false;
            }
        } else {
            return false;
        }
    }

    private void handleCharacter(final int primaryCode, final int x,
            final int y, final int spaceState) {
        final InputConnection ic = getCurrentInputConnection();
        if (null != ic) ic.beginBatchEdit();
        // TODO: if ic is null, does it make any sense to call this?
        handleCharacterWhileInBatchEdit(primaryCode, x, y, spaceState, ic);
        if (null != ic) ic.endBatchEdit();
    }

    // "ic" may be null without this crashing, but the behavior will be really strange
    private void handleCharacterWhileInBatchEdit(final int primaryCode,
            final int x, final int y, final int spaceState, final InputConnection ic) {
        boolean isComposingWord = mWordComposer.isComposingWord();

        if (SPACE_STATE_PHANTOM == spaceState &&
                !mSettingsValues.isSymbolExcludedFromWordSeparators(primaryCode)) {
            if (isComposingWord) {
                // Sanity check
                throw new RuntimeException("Should not be composing here");
            }
            sendKeyCodePoint(Keyboard.CODE_SPACE);
        }

        // NOTE: isCursorTouchingWord() is a blocking IPC call, so it often takes several
        // dozen milliseconds. Avoid calling it as much as possible, since we are on the UI
        // thread here.
        if (!isComposingWord && (isAlphabet(primaryCode)
                || mSettingsValues.isSymbolExcludedFromWordSeparators(primaryCode))
                && isSuggestionsRequested() && !isCursorTouchingWord()) {
            // Reset entirely the composing state anyway, then start composing a new word unless
            // the character is a single quote. The idea here is, single quote is not a
            // separator and it should be treated as a normal character, except in the first
            // position where it should not start composing a word.
            isComposingWord = (Keyboard.CODE_SINGLE_QUOTE != primaryCode);
            // Here we don't need to reset the last composed word. It will be reset
            // when we commit this one, if we ever do; if on the other hand we backspace
            // it entirely and resume suggestions on the previous word, we'd like to still
            // have touch coordinates for it.
            resetComposingState(false /* alsoResetLastComposedWord */);
            clearSuggestions();
        }
        if (isComposingWord) {
            mWordComposer.add(
                    primaryCode, x, y, mKeyboardSwitcher.getKeyboardView().getKeyDetector());
            if (ic != null) {
                // If it's the first letter, make note of auto-caps state
                if (mWordComposer.size() == 1) {
                    mWordComposer.setAutoCapitalized(
                            getCurrentAutoCapsState() != Constants.TextUtils.CAP_MODE_OFF);
                }
                ic.setComposingText(getTextWithUnderline(mWordComposer.getTypedWord()), 1);
            }
            mHandler.postUpdateSuggestions();
        } else {
            final boolean swapWeakSpace = maybeStripSpaceWhileInBatchEdit(ic, primaryCode,
                    spaceState, KeyboardActionListener.SUGGESTION_STRIP_COORDINATE == x);

            sendKeyCodePoint(primaryCode);

            if (swapWeakSpace) {
                swapSwapperAndSpaceWhileInBatchEdit(ic);
                mSpaceState = SPACE_STATE_WEAK;
            }
            // Some characters are not word separators, yet they don't start a new
            // composing span. For these, we haven't changed the suggestion strip, and
            // if the "add to dictionary" hint is shown, we should do so now. Examples of
            // such characters include single quote, dollar, and others; the exact list is
            // the list of characters for which we enter handleCharacterWhileInBatchEdit
            // that don't match the test if ((isAlphabet...)) at the top of this method.
            if (null != mSuggestionsView && mSuggestionsView.dismissAddToDictionaryHint()) {
                mHandler.postUpdateBigramPredictions();
            }
        }
        Utils.Stats.onNonSeparator((char)primaryCode, x, y);
    }

    // Returns true if we did an autocorrection, false otherwise.
    private boolean handleSeparator(final int primaryCode, final int x, final int y,
            final int spaceState) {
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
                commitTyped(ic, primaryCode);
            }
        }

        final boolean swapWeakSpace = maybeStripSpaceWhileInBatchEdit(ic, primaryCode, spaceState,
                KeyboardActionListener.SUGGESTION_STRIP_COORDINATE == x);

        if (SPACE_STATE_PHANTOM == spaceState &&
                mSettingsValues.isPhantomSpacePromotingSymbol(primaryCode)) {
            sendKeyCodePoint(Keyboard.CODE_SPACE);
        }
        sendKeyCodePoint(primaryCode);

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
                mSpaceState = SPACE_STATE_SWAP_PUNCTUATION;
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
        return mIsAutoCorrectionIndicatorOn
                ? SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(this, text)
                : text;
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection(), LastComposedWord.NOT_A_SEPARATOR);
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
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_switchToKeyboardView();
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
        setSuggestions(SuggestedWords.EMPTY, false);
        setAutoCorrectionIndicator(false);
    }

    private void setSuggestions(final SuggestedWords words, final boolean isAutoCorrection) {
        if (mSuggestionsView != null) {
            mSuggestionsView.setSuggestions(words);
            mKeyboardSwitcher.onAutoCorrectionStateChanged(isAutoCorrection);
        }
    }

    private void setAutoCorrectionIndicator(final boolean newAutoCorrectionIndicator) {
        // Put a blue underline to a word in TextView which will be auto-corrected.
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (mIsAutoCorrectionIndicatorOn != newAutoCorrectionIndicator
                && mWordComposer.isComposingWord()) {
            mIsAutoCorrectionIndicatorOn = newAutoCorrectionIndicator;
            final CharSequence textWithUnderline =
                    getTextWithUnderline(mWordComposer.getTypedWord());
            ic.setComposingText(textWithUnderline, 1);
        }
    }

    public void updateSuggestions() {
        // Check if we have a suggestion engine attached.
        if ((mSuggest == null || !isSuggestionsRequested())) {
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

        final CharSequence typedWord = mWordComposer.getTypedWord();
        // getSuggestedWords handles gracefully a null value of prevWord
        final SuggestedWords suggestedWords = mSuggest.getSuggestedWords(mWordComposer,
                prevWord, mKeyboardSwitcher.getKeyboard().getProximityInfo(), mCorrectionMode);

        // Basically, we update the suggestion strip only when suggestion count > 1.  However,
        // there is an exception: We update the suggestion strip whenever typed word's length
        // is 1 or typed word is found in dictionary, regardless of suggestion count.  Actually,
        // in most cases, suggestion count is 1 when typed word's length is 1, but we do always
        // need to clear the previous state when the user starts typing a word (i.e. typed word's
        // length == 1).
        if (suggestedWords.size() > 1 || typedWord.length() == 1
                || !suggestedWords.mAllowsToBeAutoCorrected
                || mSuggestionsView.isShowingAddToDictionaryHint()) {
            showSuggestions(suggestedWords, typedWord);
        } else {
            SuggestedWords previousSuggestions = mSuggestionsView.getSuggestions();
            if (previousSuggestions == mSettingsValues.mSuggestPuncList) {
                previousSuggestions = SuggestedWords.EMPTY;
            }
            final ArrayList<SuggestedWords.SuggestedWordInfo> typedWordAndPreviousSuggestions =
                    SuggestedWords.getTypedWordAndPreviousSuggestions(
                            typedWord, previousSuggestions);
            final SuggestedWords obsoleteSuggestedWords =
                    new SuggestedWords(typedWordAndPreviousSuggestions,
                            false /* typedWordValid */,
                            false /* hasAutoCorrectionCandidate */,
                            false /* allowsToBeAutoCorrected */,
                            false /* isPunctuationSuggestions */,
                            true /* isObsoleteSuggestions */,
                            false /* isPrediction */);
            showSuggestions(obsoleteSuggestedWords, typedWord);
        }
    }

    public void showSuggestions(final SuggestedWords suggestedWords, final CharSequence typedWord) {
        final CharSequence autoCorrection;
        if (suggestedWords.size() > 0) {
            if (suggestedWords.hasAutoCorrectionWord()) {
                autoCorrection = suggestedWords.getWord(1);
            } else {
                autoCorrection = typedWord;
            }
        } else {
            autoCorrection = null;
        }
        mWordComposer.setAutoCorrection(autoCorrection);
        final boolean isAutoCorrection = suggestedWords.willAutoCorrect();
        setSuggestions(suggestedWords, isAutoCorrection);
        setAutoCorrectionIndicator(isAutoCorrection);
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
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_commitCurrentAutoCorrection(typedWord,
                        autoCorrection.toString());
            }
            mExpectingUpdateSelection = true;
            commitChosenWord(autoCorrection, LastComposedWord.COMMIT_TYPE_DECIDED_WORD,
                    separatorCodePoint);
            if (!typedWord.equals(autoCorrection) && null != ic) {
                // This will make the correction flash for a short while as a visual clue
                // to the user that auto-correction happened.
                ic.commitCorrection(new CorrectionInfo(mLastSelectionEnd - typedWord.length(),
                        typedWord, autoCorrection));
            }
        }
    }

    @Override
    public void pickSuggestionManually(final int index, final CharSequence suggestion,
            int x, int y) {
        final InputConnection ic = getCurrentInputConnection();
        if (null != ic) ic.beginBatchEdit();
        pickSuggestionManuallyWhileInBatchEdit(index, suggestion, x, y, ic);
        if (null != ic) ic.endBatchEdit();
    }

    public void pickSuggestionManuallyWhileInBatchEdit(final int index,
        final CharSequence suggestion, final int x, final int y, final InputConnection ic) {
        final SuggestedWords suggestedWords = mSuggestionsView.getSuggestions();
        // If this is a punctuation picked from the suggestion strip, pass it to onCodeInput
        if (suggestion.length() == 1 && isShowingPunctuationList()) {
            // Word separators are suggested before the user inputs something.
            // So, LatinImeLogger logs "" as a user's input.
            LatinImeLogger.logOnManualSuggestion("", suggestion.toString(), index, suggestedWords);
            // Rely on onCodeInput to do the complicated swapping/stripping logic consistently.
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_punctuationSuggestion(index, suggestion, x, y);
            }
            final int primaryCode = suggestion.charAt(0);
            onCodeInput(primaryCode,
                    KeyboardActionListener.SUGGESTION_STRIP_COORDINATE,
                    KeyboardActionListener.SUGGESTION_STRIP_COORDINATE);
            return;
        }

        if (SPACE_STATE_PHANTOM == mSpaceState && suggestion.length() > 0) {
            int firstChar = Character.codePointAt(suggestion, 0);
            if ((!mSettingsValues.isWeakSpaceStripper(firstChar))
                    && (!mSettingsValues.isWeakSpaceSwapper(firstChar))) {
                sendKeyCodePoint(Keyboard.CODE_SPACE);
            }
        }

        if (mInputAttributes.mApplicationSpecifiedCompletionOn
                && mApplicationSpecifiedCompletions != null
                && index >= 0 && index < mApplicationSpecifiedCompletions.length) {
            if (mSuggestionsView != null) {
                mSuggestionsView.clear();
            }
            mKeyboardSwitcher.updateShiftState();
            resetComposingState(true /* alsoResetLastComposedWord */);
            if (ic != null) {
                final CompletionInfo completionInfo = mApplicationSpecifiedCompletions[index];
                ic.commitCompletion(completionInfo);
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.latinIME_pickApplicationSpecifiedCompletion(index,
                            completionInfo.getText(), x, y);
                }
            }
            return;
        }

        // We need to log before we commit, because the word composer will store away the user
        // typed word.
        final String replacedWord = mWordComposer.getTypedWord().toString();
        LatinImeLogger.logOnManualSuggestion(replacedWord,
                suggestion.toString(), index, suggestedWords);
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_pickSuggestionManually(replacedWord, index, suggestion, x, y);
        }
        mExpectingUpdateSelection = true;
        commitChosenWord(suggestion, LastComposedWord.COMMIT_TYPE_MANUAL_PICK,
                LastComposedWord.NOT_A_SEPARATOR);
        // Don't allow cancellation of manual pick
        mLastComposedWord.deactivate();
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
    private void commitChosenWord(final CharSequence chosenWord, final int commitType,
            final int separatorCode) {
        final InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (mSettingsValues.mEnableSuggestionSpanInsertion) {
                final SuggestedWords suggestedWords = mSuggestionsView.getSuggestions();
                ic.commitText(SuggestionSpanUtils.getTextWithSuggestionSpan(
                        this, chosenWord, suggestedWords, mIsMainDictionaryAvailable),
                        1);
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.latinIME_commitText(chosenWord);
                }
            } else {
                ic.commitText(chosenWord, 1);
                if (ProductionFlag.IS_EXPERIMENTAL) {
                    ResearchLogger.latinIME_commitText(chosenWord);
                }
            }
        }
        // Add the word to the user history dictionary
        final CharSequence prevWord = addToUserHistoryDictionary(chosenWord);
        // TODO: figure out here if this is an auto-correct or if the best word is actually
        // what user typed. Note: currently this is done much later in
        // LastComposedWord#didCommitTypedWord by string equality of the remembered
        // strings.
        mLastComposedWord = mWordComposer.commitWord(commitType, chosenWord.toString(),
                separatorCode, prevWord);
    }

    public void updateBigramPredictions() {
        if (mSuggest == null || !isSuggestionsRequested())
            return;

        if (!mSettingsValues.mBigramPredictionEnabled) {
            setPunctuationSuggestions();
            return;
        }

        final SuggestedWords suggestedWords;
        if (mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM) {
            final CharSequence prevWord = EditingUtils.getThisWord(getCurrentInputConnection(),
                    mSettingsValues.mWordSeparators);
            if (!TextUtils.isEmpty(prevWord)) {
                suggestedWords = mSuggest.getBigramPredictions(prevWord);
            } else {
                suggestedWords = null;
            }
        } else {
            suggestedWords = null;
        }

        if (null != suggestedWords && suggestedWords.size() > 0) {
            // Explicitly supply an empty typed word (the no-second-arg version of
            // showSuggestions will retrieve the word near the cursor, we don't want that here)
            showSuggestions(suggestedWords, "");
        } else {
            clearSuggestions();
        }
    }

    public void setPunctuationSuggestions() {
        if (mSettingsValues.mBigramPredictionEnabled) {
            clearSuggestions();
        } else {
            setSuggestions(mSettingsValues.mSuggestPuncList, false);
        }
        setAutoCorrectionIndicator(false);
        setSuggestionStripShown(isSuggestionsStripVisible());
    }

    private CharSequence addToUserHistoryDictionary(final CharSequence suggestion) {
        if (TextUtils.isEmpty(suggestion)) return null;

        // Only auto-add to dictionary if auto-correct is ON. Otherwise we'll be
        // adding words in situations where the user or application really didn't
        // want corrections enabled or learned.
        if (!(mCorrectionMode == Suggest.CORRECTION_FULL
                || mCorrectionMode == Suggest.CORRECTION_FULL_BIGRAM)) {
            return null;
        }

        if (mUserHistoryDictionary != null) {
            final InputConnection ic = getCurrentInputConnection();
            final CharSequence prevWord;
            if (null != ic) {
                prevWord = EditingUtils.getPreviousWord(ic, mSettingsValues.mWordSeparators);
            } else {
                prevWord = null;
            }
            final String secondWord;
            if (mWordComposer.isAutoCapitalized() && !mWordComposer.isMostlyCaps()) {
                secondWord = suggestion.toString().toLowerCase(
                        mSubtypeSwitcher.getCurrentSubtypeLocale());
            } else {
                secondWord = suggestion.toString();
            }
            // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
            // We don't add words with 0-frequency (assuming they would be profanity etc.).
            final int maxFreq = AutoCorrection.getMaxFrequency(
                    mSuggest.getUnigramDictionaries(), suggestion);
            if (maxFreq == 0) return null;
            mUserHistoryDictionary.addToUserHistory(null == prevWord ? null : prevWord.toString(),
                    secondWord, maxFreq > 0);
            return prevWord;
        }
        return null;
    }

    public boolean isCursorTouchingWord() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        CharSequence after = ic.getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(before) && !mSettingsValues.isWordSeparator(before.charAt(0))
                && !mSettingsValues.isSymbolExcludedFromWordSeparators(before.charAt(0))) {
            return true;
        }
        if (!TextUtils.isEmpty(after) && !mSettingsValues.isWordSeparator(after.charAt(0))
                && !mSettingsValues.isSymbolExcludedFromWordSeparators(after.charAt(0))) {
            return true;
        }
        return false;
    }

    // "ic" must not be null
    private static boolean sameAsTextBeforeCursor(final InputConnection ic,
            final CharSequence text) {
        final CharSequence beforeText = ic.getTextBeforeCursor(text.length(), 0);
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
        final int length = word.length();
        ic.deleteSurroundingText(length, 0);
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_deleteSurroundingText(length);
        }
        ic.setComposingText(word, 1);
        mHandler.postUpdateSuggestions();
    }

    // "ic" must not be null
    private void revertCommit(final InputConnection ic) {
        final CharSequence previousWord = mLastComposedWord.mPrevWord;
        final String originallyTypedWord = mLastComposedWord.mTypedWord;
        final CharSequence committedWord = mLastComposedWord.mCommittedWord;
        final int cancelLength = committedWord.length();
        final int separatorLength = LastComposedWord.getSeparatorLength(
                mLastComposedWord.mSeparatorCode);
        // TODO: should we check our saved separator against the actual contents of the text view?
        final int deleteLength = cancelLength + separatorLength;
        if (DEBUG) {
            if (mWordComposer.isComposingWord()) {
                throw new RuntimeException("revertCommit, but we are composing a word");
            }
            final String wordBeforeCursor =
                    ic.getTextBeforeCursor(deleteLength, 0)
                            .subSequence(0, cancelLength).toString();
            if (!TextUtils.equals(committedWord, wordBeforeCursor)) {
                throw new RuntimeException("revertCommit check failed: we thought we were "
                        + "reverting \"" + committedWord
                        + "\", but before the cursor we found \"" + wordBeforeCursor + "\"");
            }
        }
        ic.deleteSurroundingText(deleteLength, 0);
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_deleteSurroundingText(deleteLength);
        }
        if (!TextUtils.isEmpty(previousWord) && !TextUtils.isEmpty(committedWord)) {
            mUserHistoryDictionary.cancelAddingUserHistory(
                    previousWord.toString(), committedWord.toString());
        }
        if (0 == separatorLength || mLastComposedWord.didCommitTypedWord()) {
            // This is the case when we cancel a manual pick.
            // We should restart suggestion on the word right away.
            mWordComposer.resumeSuggestionOnLastComposedWord(mLastComposedWord);
            ic.setComposingText(originallyTypedWord, 1);
        } else {
            ic.commitText(originallyTypedWord, 1);
            // Re-insert the separator
            sendKeyCodePoint(mLastComposedWord.mSeparatorCode);
            Utils.Stats.onSeparator(mLastComposedWord.mSeparatorCode, WordComposer.NOT_A_COORDINATE,
                    WordComposer.NOT_A_COORDINATE);
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_revertCommit(originallyTypedWord);
            }
            // Don't restart suggestion yet. We'll restart if the user deletes the
            // separator.
        }
        mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
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
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_deleteSurroundingText(2);
        }
        ic.commitText("  ", 1);
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_revertDoubleSpaceWhileInBatchEdit();
        }
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
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_deleteSurroundingText(2);
        }
        ic.commitText(" " + textBeforeCursor.subSequence(0, 1), 1);
        if (ProductionFlag.IS_EXPERIMENTAL) {
            ResearchLogger.latinIME_revertSwapPunctuation();
        }
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
        // When the device locale is changed in SetupWizard etc., this method may get called via
        // onConfigurationChanged before SoftInputWindow is shown.
        if (mKeyboardSwitcher.getKeyboardView() != null) {
            // Reload keyboard because the current language has been changed.
            mKeyboardSwitcher.loadKeyboard(getCurrentInputEditorInfo(), mSettingsValues);
        }
        initSuggest();
        updateCorrectionMode();
        loadSettings();
        // Since we just changed languages, we should re-evaluate suggestions with whatever word
        // we are currently composing. If we are not composing anything, we may want to display
        // predictions or punctuation signs (which is done by updateBigramPredictions anyway).
        if (isCursorTouchingWord()) {
            mHandler.postUpdateSuggestions();
        } else {
            mHandler.postUpdateBigramPredictions();
        }
    }

    // TODO: Remove this method from {@link LatinIME} and move {@link FeedbackManager} to
    // {@link KeyboardSwitcher}.
    public void hapticAndAudioFeedback(final int primaryCode) {
        mFeedbackManager.hapticAndAudioFeedback(primaryCode, mKeyboardSwitcher.getKeyboardView());
    }

    @Override
    public void onPressKey(int primaryCode) {
        mKeyboardSwitcher.onPressKey(primaryCode);
    }

    @Override
    public void onReleaseKey(int primaryCode, boolean withSliding) {
        mKeyboardSwitcher.onReleaseKey(primaryCode, withSliding);

        // If accessibility is on, ensure the user receives keyboard state updates.
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            switch (primaryCode) {
            case Keyboard.CODE_SHIFT:
                AccessibleKeyboardViewProxy.getInstance().notifyShiftState();
                break;
            case Keyboard.CODE_SWITCH_ALPHA_SYMBOL:
                AccessibleKeyboardViewProxy.getInstance().notifySymbolsState();
                break;
            }
        }

        if (Keyboard.CODE_DELETE == primaryCode) {
            // This is a stopgap solution to avoid leaving a high surrogate alone in a text view.
            // In the future, we need to deprecate deteleSurroundingText() and have a surrogate
            // pair-friendly way of deleting characters in InputConnection.
            final InputConnection ic = getCurrentInputConnection();
            if (null != ic) {
                final CharSequence lastChar = ic.getTextBeforeCursor(1, 0);
                if (!TextUtils.isEmpty(lastChar) && Character.isHighSurrogate(lastChar.charAt(0))) {
                    ic.deleteSurroundingText(1, 0);
                }
            }
        }
    }

    // receive ringer mode change and network state change.
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                mSubtypeSwitcher.onNetworkStateChanged(intent);
            } else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                mFeedbackManager.onRingerModeChanged();
            }
        }
    };

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

    private void launchSettings() {
        launchSettingsClass(SettingsActivity.class);
    }

    public void launchDebugSettings() {
        launchSettingsClass(DebugSettingsActivity.class);
    }

    private void launchSettingsClass(Class<? extends PreferenceActivity> settingsClass) {
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
        final Context context = this;
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int position) {
                di.dismiss();
                switch (position) {
                case 0:
                    Intent intent = CompatUtils.getInputLanguageSelectionIntent(
                            ImfUtils.getInputMethodIdOfThisIme(context),
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

    private void showOptionDialogInternal(AlertDialog dialog) {
        final IBinder windowToken = mKeyboardSwitcher.getKeyboardView().getWindowToken();
        if (windowToken == null) return;

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
