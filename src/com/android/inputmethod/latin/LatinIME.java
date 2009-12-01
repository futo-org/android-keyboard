/*
 * Copyright (C) 2008-2009 Google Inc.
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
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.AutoText;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.PrintWriterPrinter;
import android.util.Printer;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
public class LatinIME extends InputMethodService 
        implements KeyboardView.OnKeyboardActionListener {
    static final boolean DEBUG = false;
    static final boolean TRACE = false;
    
    private static final String PREF_VIBRATE_ON = "vibrate_on";
    private static final String PREF_SOUND_ON = "sound_on";
    private static final String PREF_AUTO_CAP = "auto_cap";
    private static final String PREF_QUICK_FIXES = "quick_fixes";
    private static final String PREF_SHOW_SUGGESTIONS = "show_suggestions";
    private static final String PREF_AUTO_COMPLETE = "auto_complete";

    private static final int MSG_UPDATE_SUGGESTIONS = 0;
    private static final int MSG_START_TUTORIAL = 1;
    private static final int MSG_UPDATE_SHIFT_STATE = 2;
    
    // How many continuous deletes at which to start deleting at a higher speed.
    private static final int DELETE_ACCELERATE_AT = 20;
    // Key events coming any faster than this are long-presses.
    private static final int QUICK_PRESS = 200;
    // Weight added to a user picking a new word from the suggestion strip
    static final int FREQUENCY_FOR_PICKED = 3;
    // Weight added to a user typing a new word that doesn't get corrected (or is reverted)
    static final int FREQUENCY_FOR_TYPED = 1;
    // A word that is frequently typed and get's promoted to the user dictionary, uses this
    // frequency.
    static final int FREQUENCY_FOR_AUTO_ADD = 250;
    
    static final int KEYCODE_ENTER = '\n';
    static final int KEYCODE_SPACE = ' ';

    // Contextual menu positions
    private static final int POS_SETTINGS = 0;
    private static final int POS_METHOD = 1;
    
    private LatinKeyboardView mInputView;
    private CandidateViewContainer mCandidateViewContainer;
    private CandidateView mCandidateView;
    private Suggest mSuggest;
    private CompletionInfo[] mCompletions;
    
    private AlertDialog mOptionsDialog;
    
    KeyboardSwitcher mKeyboardSwitcher;
    
    private UserDictionary mUserDictionary;
    private ContactsDictionary mContactsDictionary;
    private ExpandableDictionary mAutoDictionary;
    
    private String mLocale;

    private StringBuilder mComposing = new StringBuilder();
    private WordComposer mWord = new WordComposer();
    private int mCommittedLength;
    private boolean mPredicting;
    private CharSequence mBestWord;
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private boolean mAutoSpace;
    private boolean mAutoCorrectOn;
    private boolean mCapsLock;
    private boolean mVibrateOn;
    private boolean mSoundOn;
    private boolean mAutoCap;
    private boolean mQuickFixes;
    private boolean mShowSuggestions;
    private int     mCorrectionMode;
    private int     mOrientation;

    // Indicates whether the suggestion strip is to be on in landscape
    private boolean mJustAccepted;
    private CharSequence mJustRevertedSeparator;
    private int mDeleteCount;
    private long mLastKeyTime;
    
    private Tutorial mTutorial;

    private Vibrator mVibrator;
    private long mVibrateDuration;

    private AudioManager mAudioManager;
    // Align sound effect volume on music volume
    private final float FX_VOLUME = -1.0f;
    private boolean mSilentMode;

    private String mWordSeparators;
    private String mSentenceSeparators;
    
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
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        //setStatusIcon(R.drawable.ime_qwerty);
        mKeyboardSwitcher = new KeyboardSwitcher(this);
        final Configuration conf = getResources().getConfiguration();
        initSuggest(conf.locale.toString());
        mOrientation = conf.orientation;

        mVibrateDuration = getResources().getInteger(R.integer.vibrate_duration_ms);

        // register to receive ringer mode changes for silent mode
        IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);
    }
    
    private void initSuggest(String locale) {
        mLocale = locale;
        mSuggest = new Suggest(this, R.raw.main);
        mSuggest.setCorrectionMode(mCorrectionMode);
        mUserDictionary = new UserDictionary(this);
        mContactsDictionary = new ContactsDictionary(this);
        mAutoDictionary = new AutoDictionary(this);
        mSuggest.setUserDictionary(mUserDictionary);
        mSuggest.setContactsDictionary(mContactsDictionary);
        mSuggest.setAutoDictionary(mAutoDictionary);
        mWordSeparators = getResources().getString(R.string.word_separators);
        mSentenceSeparators = getResources().getString(R.string.sentence_separators);
    }
    
    @Override public void onDestroy() {
        mUserDictionary.close();
        mContactsDictionary.close();
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration conf) {
        if (!TextUtils.equals(conf.locale.toString(), mLocale)) {
            initSuggest(conf.locale.toString());
        }
        // If orientation changed while predicting, commit the change
        if (conf.orientation != mOrientation) {
            commitTyped(getCurrentInputConnection());
            mOrientation = conf.orientation;
        }
        if (mKeyboardSwitcher == null) {
            mKeyboardSwitcher = new KeyboardSwitcher(this);
        }
        mKeyboardSwitcher.makeKeyboards(true);
        super.onConfigurationChanged(conf);
    }

    @Override
    public View onCreateInputView() {
        mInputView = (LatinKeyboardView) getLayoutInflater().inflate(
                R.layout.input, null);
        mKeyboardSwitcher.setInputView(mInputView);
        mKeyboardSwitcher.makeKeyboards(true);
        mInputView.setOnKeyboardActionListener(this);
        mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT, 0);
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

        mKeyboardSwitcher.makeKeyboards(false);

        TextEntryState.newSession(this);

        boolean disableAutoCorrect = false;
        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;
        mCapsLock = false;
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_SYMBOLS,
                        attribute.imeOptions);
                break;
            case EditorInfo.TYPE_CLASS_PHONE:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_PHONE,
                        attribute.imeOptions);
                break;
            case EditorInfo.TYPE_CLASS_TEXT:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT,
                        attribute.imeOptions);
                //startPrediction();
                mPredictionOn = true;
                // Make sure that passwords are not displayed in candidate view
                int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ) {
                    mPredictionOn = false;
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
                            attribute.imeOptions);
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
                    mPredictionOn = false;
                    mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_URL,
                            attribute.imeOptions);
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                    mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_IM,
                            attribute.imeOptions);
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    mPredictionOn = false;
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
                    // If it's a browser edit field and auto correct is not ON explicitly, then
                    // disable auto correction, but keep suggestions on.
                    if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                        disableAutoCorrect = true;
                    }
                }

                // If NO_SUGGESTIONS is set, don't do prediction.
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    mPredictionOn = false;
                    disableAutoCorrect = true;
                }
                // If it's not multiline and the autoCorrect flag is not set, then don't correct
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0 &&
                        (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
                    disableAutoCorrect = true;
                }
                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    mPredictionOn = false;
                    mCompletionOn = true && isFullscreenMode();
                }
                updateShiftKeyState(attribute);
                break;
            default:
                mKeyboardSwitcher.setKeyboardMode(KeyboardSwitcher.MODE_TEXT,
                        attribute.imeOptions);
                updateShiftKeyState(attribute);
        }
        mInputView.closing();
        mComposing.setLength(0);
        mPredicting = false;
        mDeleteCount = 0;
        setCandidatesViewShown(false);
        if (mCandidateView != null) mCandidateView.setSuggestions(null, false, false, false);
        loadSettings();
        // Override auto correct
        if (disableAutoCorrect) {
            mAutoCorrectOn = false;
            if (mCorrectionMode == Suggest.CORRECTION_FULL) {
                mCorrectionMode = Suggest.CORRECTION_BASIC;
            }
        }
        mInputView.setProximityCorrectionEnabled(true);
        if (mSuggest != null) {
            mSuggest.setCorrectionMode(mCorrectionMode);
        }
        mPredictionOn = mPredictionOn && mCorrectionMode > 0;
        checkTutorial(attribute.privateImeOptions);
        if (TRACE) Debug.startMethodTracing("/data/trace/latinime");
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();

        if (mInputView != null) {
            mInputView.closing();
        }
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && mPredicting && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            mPredicting = false;
            updateSuggestions();
            TextEntryState.reset();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        } else if (!mPredicting && !mJustAccepted
                && TextEntryState.getState() == TextEntryState.STATE_ACCEPTED_DEFAULT) {
            TextEntryState.reset();
        }
        mJustAccepted = false;
        postUpdateShiftKeyState();
    }

    @Override
    public void hideWindow() {
        if (TRACE) Debug.stopMethodTracing();
        if (mOptionsDialog != null && mOptionsDialog.isShowing()) {
            mOptionsDialog.dismiss();
            mOptionsDialog = null;
        }
        if (mTutorial != null) {
            mTutorial.close();
            mTutorial = null;
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
                mCandidateView.setSuggestions(null, false, false, false);
                return;
            }
            
            List<CharSequence> stringList = new ArrayList<CharSequence>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText());
            }
            //CharSequence typedWord = mWord.getTypedWord();
            mCandidateView.setSuggestions(stringList, true, true, true);
            mBestWord = null;
            setCandidatesViewShown(isCandidateStripVisible() || mCompletionOn);
        }
    }

    @Override
    public void setCandidatesViewShown(boolean shown) {
        // TODO: Remove this if we support candidates with hard keyboard
        if (onEvaluateInputViewShown()) {
            super.setCandidatesViewShown(shown);
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

    private void commitTyped(InputConnection inputConnection) {
        if (mPredicting) {
            mPredicting = false;
            if (mComposing.length() > 0) {
                if (inputConnection != null) {
                    inputConnection.commitText(mComposing, 1);
                }
                mCommittedLength = mComposing.length();
                TextEntryState.acceptedTyped(mComposing);
                mAutoDictionary.addWord(mComposing.toString(), FREQUENCY_FOR_TYPED);
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
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (mAutoCap && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = ic.getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
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
            default:
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
    }
    
    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mPredicting) {
            commitTyped(ic);
        }
        ic.commitText(text, 1);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
        mJustRevertedSeparator = null;
    }

    private void handleBackspace() {
        boolean deleteChar = false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
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
        } else if (deleteChar) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            if (mDeleteCount > DELETE_ACCELERATE_AT) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            }
        }
        mJustRevertedSeparator = null;
    }

    private void handleShift() {
        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (mKeyboardSwitcher.isAlphabetMode()) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else {
            mKeyboardSwitcher.toggleShift();
        }
    }
    
    private void handleCharacter(int primaryCode, int[] keyCodes) {
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
            } else {
                commitTyped(ic);
            }
        }
        sendKeyChar((char)primaryCode);
        TextEntryState.typedCharacter((char) primaryCode, true);
        if (TextEntryState.getState() == TextEntryState.STATE_PUNCTUATION_AFTER_ACCEPTED 
                && primaryCode != KEYCODE_ENTER) {
            swapPunctuationAndSpace();
        } else if (isPredictionOn() && primaryCode == ' ') { 
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
        //if (isFullscreenMode()) predictionOn &= mPredictionLandscape;
        return predictionOn;
    }
    
    private boolean isCandidateStripVisible() {
        return isPredictionOn() && mShowSuggestions;
    }

    private void updateSuggestions() {
        // Check if we have a suggestion engine attached.
        if (mSuggest == null || !isPredictionOn()) {
            return;
        }
        
        if (!mPredicting) {
            mCandidateView.setSuggestions(null, false, false, false);
            return;
        }

        List<CharSequence> stringList = mSuggest.getSuggestions(mInputView, mWord, false);
        boolean correctionAvailable = mSuggest.hasMinimalCorrection();
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

        mCandidateView.setSuggestions(stringList, false, typedWordValid, correctionAvailable); 
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
        if (mBestWord != null) {
            TextEntryState.acceptedDefault(mWord.getTypedWord(), mBestWord);
            mJustAccepted = true;
            pickSuggestion(mBestWord);
        }
    }

    public void pickSuggestionManually(int index, CharSequence suggestion) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitCompletion(ci);
            }
            mCommittedLength = suggestion.length();
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
            return;
        }
        pickSuggestion(suggestion);
        TextEntryState.acceptedSuggestion(mComposing.toString(), suggestion);
        // Follow it with a space
        if (mAutoSpace) {
            sendSpace();
        }
        // Fool the state watcher so that a subsequent backspace will not do a revert
        TextEntryState.typedCharacter((char) KEYCODE_SPACE, true);
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
            ic.commitText(suggestion, 1);
        }
        // Add the word to the auto dictionary if it's not a known word
        if (mAutoDictionary.isValidWord(suggestion) || !mSuggest.isValidWord(suggestion)) {
            mAutoDictionary.addWord(suggestion.toString(), FREQUENCY_FOR_PICKED);
        }
        mPredicting = false;
        mCommittedLength = suggestion.length();
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(null, false, false, false);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
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
        if (LatinKeyboardView.DEBUG_AUTO_PLAY) {
            ClipboardManager cm = ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE));
            CharSequence text = cm.getText();
            if (!TextUtils.isEmpty(text)) {
                mInputView.startPlaying(text.toString());
            }
        }
    }
    
    public void swipeLeft() {
        //handleBackspace();
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
        //vibrate();
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
        if (mVibrator == null) {
            mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        mVibrator.vibrate(mVibrateDuration);
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

    private void launchSettings() {
        handleClose();
        Intent intent = new Intent();
        intent.setClass(LatinIME.this, LatinIMESettings.class);
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
        // If there is no auto text data, then quickfix is forced to "on", so that the other options
        // will continue to work
        if (AutoText.getSize(mInputView) < 1) mQuickFixes = true;
        mShowSuggestions = sp.getBoolean(PREF_SHOW_SUGGESTIONS, true) & mQuickFixes;
        boolean autoComplete = sp.getBoolean(PREF_AUTO_COMPLETE,
                getResources().getBoolean(R.bool.enable_autocorrect)) & mShowSuggestions;
        mAutoCorrectOn = mSuggest != null && (autoComplete || mQuickFixes);
        mCorrectionMode = autoComplete
                ? Suggest.CORRECTION_FULL
                : (mQuickFixes ? Suggest.CORRECTION_BASIC : Suggest.CORRECTION_NONE);
    }

    private void showOptionsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.ic_dialog_keyboard);
        builder.setNegativeButton(android.R.string.cancel, null);
        CharSequence itemSettings = getString(R.string.english_ime_settings);
        CharSequence itemInputMethod = getString(com.android.internal.R.string.inputMethod);
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
        builder.setTitle(getResources().getString(R.string.english_ime_name));
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

    class AutoDictionary extends ExpandableDictionary {
        // If the user touches a typed word 2 times or more, it will become valid.
        private static final int VALIDITY_THRESHOLD = 2 * FREQUENCY_FOR_PICKED;
        // If the user touches a typed word 5 times or more, it will be added to the user dict.
        private static final int PROMOTION_THRESHOLD = 5 * FREQUENCY_FOR_PICKED;

        public AutoDictionary(Context context) {
            super(context);
        }

        @Override
        public boolean isValidWord(CharSequence word) {
            final int frequency = getWordFrequency(word);
            return frequency > VALIDITY_THRESHOLD;
        }

        @Override
        public void addWord(String word, int addFrequency) {
            final int length = word.length();
            // Don't add very short or very long words.
            if (length < 2 || length > getMaxWordLength()) return;
            super.addWord(word, addFrequency);
            final int freq = getWordFrequency(word);
            if (freq > PROMOTION_THRESHOLD) {
                LatinIME.this.promoteToUserDictionary(word, FREQUENCY_FOR_AUTO_ADD);
            }
        }
    }
}



