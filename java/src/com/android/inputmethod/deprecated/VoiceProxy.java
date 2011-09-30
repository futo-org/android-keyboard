/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.deprecated;

import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.compat.InputMethodServiceCompatWrapper;
import com.android.inputmethod.compat.SharedPreferencesCompat;
import com.android.inputmethod.deprecated.voice.FieldContext;
import com.android.inputmethod.deprecated.voice.Hints;
import com.android.inputmethod.deprecated.voice.SettingsUtil;
import com.android.inputmethod.deprecated.voice.VoiceInput;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.EditingUtils;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinIME.UIHandler;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.Utils;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.speech.SpeechRecognizer;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VoiceProxy implements VoiceInput.UiListener {
    private static final VoiceProxy sInstance = new VoiceProxy();

    public static final boolean VOICE_INSTALLED =
            !InputMethodServiceCompatWrapper.CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED;
    private static final boolean ENABLE_VOICE_BUTTON = true;
    private static final String PREF_VOICE_MODE = "voice_mode";
    // Whether or not the user has used voice input before (and thus, whether to show the
    // first-run warning dialog or not).
    private static final String PREF_HAS_USED_VOICE_INPUT = "has_used_voice_input";
    // Whether or not the user has used voice input from an unsupported locale UI before.
    // For example, the user has a Chinese UI but activates voice input.
    private static final String PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE =
            "has_used_voice_input_unsupported_locale";
    private static final int RECOGNITIONVIEW_HEIGHT_THRESHOLD_RATIO = 6;
    // TODO: Adjusted on phones for now
    private static final int RECOGNITIONVIEW_MINIMUM_HEIGHT_DIP = 244;

    private static final String TAG = VoiceProxy.class.getSimpleName();
    private static final boolean DEBUG = LatinImeLogger.sDBG;

    private boolean mAfterVoiceInput;
    private boolean mHasUsedVoiceInput;
    private boolean mHasUsedVoiceInputUnsupportedLocale;
    private boolean mImmediatelyAfterVoiceInput;
    private boolean mIsShowingHint;
    private boolean mLocaleSupportedForVoiceInput;
    private boolean mPasswordText;
    private boolean mRecognizing;
    private boolean mShowingVoiceSuggestions;
    private boolean mVoiceButtonEnabled;
    private boolean mVoiceButtonOnPrimary;
    private boolean mVoiceInputHighlighted;

    private int mMinimumVoiceRecognitionViewHeightPixel;
    private InputMethodManagerCompatWrapper mImm;
    private LatinIME mService;
    private AlertDialog mVoiceWarningDialog;
    private VoiceInput mVoiceInput;
    private final VoiceResults mVoiceResults = new VoiceResults();
    private Hints mHints;
    private UIHandler mHandler;
    private SubtypeSwitcher mSubtypeSwitcher;

    // For each word, a list of potential replacements, usually from voice.
    private final Map<String, List<CharSequence>> mWordToSuggestions =
            new HashMap<String, List<CharSequence>>();

    public static VoiceProxy init(LatinIME context, SharedPreferences prefs, UIHandler h) {
        sInstance.initInternal(context, prefs, h);
        return sInstance;
    }

    public static VoiceProxy getInstance() {
        return sInstance;
    }

    private void initInternal(LatinIME service, SharedPreferences prefs, UIHandler h) {
        if (!VOICE_INSTALLED) {
            return;
        }
        mService = service;
        mHandler = h;
        mMinimumVoiceRecognitionViewHeightPixel = Utils.dipToPixel(
                Utils.getDipScale(service), RECOGNITIONVIEW_MINIMUM_HEIGHT_DIP);
        mImm = InputMethodManagerCompatWrapper.getInstance();
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        mVoiceInput = new VoiceInput(service, this);
        mHints = new Hints(service, prefs, new Hints.Display() {
            @Override
            public void showHint(int viewResource) {
                View view = LayoutInflater.from(mService).inflate(viewResource, null);
                mIsShowingHint = true;
            }
        });
    }

    private VoiceProxy() {
        // Intentional empty constructor for singleton.
    }

    public void resetVoiceStates(boolean isPasswordText) {
        mAfterVoiceInput = false;
        mImmediatelyAfterVoiceInput = false;
        mShowingVoiceSuggestions = false;
        mVoiceInputHighlighted = false;
        mPasswordText = isPasswordText;
    }

    public void flushVoiceInputLogs(boolean configurationChanged) {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (!configurationChanged) {
            if (mAfterVoiceInput) {
                mVoiceInput.flushAllTextModificationCounters();
                mVoiceInput.logInputEnded();
            }
            mVoiceInput.flushLogs();
            mVoiceInput.cancel();
        }
    }

    public void flushAndLogAllTextModificationCounters(int index, CharSequence suggestion,
            String wordSeparators) {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (mAfterVoiceInput && mShowingVoiceSuggestions) {
            mVoiceInput.flushAllTextModificationCounters();
            // send this intent AFTER logging any prior aggregated edits.
            mVoiceInput.logTextModifiedByChooseSuggestion(suggestion.toString(), index,
                    wordSeparators, mService.getCurrentInputConnection());
        }
    }

    private void showVoiceWarningDialog(final boolean swipe, IBinder token) {
        if (mVoiceWarningDialog != null && mVoiceWarningDialog.isShowing()) {
            return;
        }
        AlertDialog.Builder builder = new UrlLinkAlertDialogBuilder(mService);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.ic_mic_dialog);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                mVoiceInput.logKeyboardWarningDialogOk();
                reallyStartListening(swipe);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                mVoiceInput.logKeyboardWarningDialogCancel();
                switchToLastInputMethod();
            }
        });
        // When the dialog is dismissed by user's cancellation, switch back to the last input method
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface arg0) {
                mVoiceInput.logKeyboardWarningDialogCancel();
                switchToLastInputMethod();
            }
        });

        final CharSequence message;
        if (mLocaleSupportedForVoiceInput) {
            message = TextUtils.concat(
                    mService.getText(R.string.voice_warning_may_not_understand), "\n\n",
                            mService.getText(R.string.voice_warning_how_to_turn_off));
        } else {
            message = TextUtils.concat(
                    mService.getText(R.string.voice_warning_locale_not_supported), "\n\n",
                            mService.getText(R.string.voice_warning_may_not_understand), "\n\n",
                                    mService.getText(R.string.voice_warning_how_to_turn_off));
        }
        builder.setMessage(message);
        builder.setTitle(R.string.voice_warning_title);
        mVoiceWarningDialog = builder.create();
        final Window window = mVoiceWarningDialog.getWindow();
        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = token;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mVoiceInput.logKeyboardWarningDialogShown();
        mVoiceWarningDialog.show();
    }

    private static class UrlLinkAlertDialogBuilder extends AlertDialog.Builder {
        private AlertDialog mAlertDialog;

        public UrlLinkAlertDialogBuilder(Context context) {
            super(context);
        }

        @Override
        public AlertDialog.Builder setMessage(CharSequence message) {
            return super.setMessage(replaceURLSpan(message));
        }

        private Spanned replaceURLSpan(CharSequence message) {
            // Replace all spans with the custom span
            final SpannableStringBuilder ssb = new SpannableStringBuilder(message);
            for (URLSpan span : ssb.getSpans(0, ssb.length(), URLSpan.class)) {
                int spanStart = ssb.getSpanStart(span);
                int spanEnd = ssb.getSpanEnd(span);
                int spanFlags = ssb.getSpanFlags(span);
                ssb.removeSpan(span);
                ssb.setSpan(new ClickableSpan(span.getURL()), spanStart, spanEnd, spanFlags);
            }
            return ssb;
        }

        @Override
        public AlertDialog create() {
            final AlertDialog dialog = super.create();

            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    // Make URL in the dialog message click-able.
                    TextView textView = (TextView) mAlertDialog.findViewById(android.R.id.message);
                    if (textView != null) {
                        textView.setMovementMethod(LinkMovementMethod.getInstance());
                    }
                }
            });
            mAlertDialog = dialog;
            return dialog;
        }

        class ClickableSpan extends URLSpan {
            public ClickableSpan(String url) {
                super(url);
            }

            @Override
            public void onClick(View widget) {
                Uri uri = Uri.parse(getURL());
                Context context = widget.getContext();
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                // Add this flag to start an activity from service
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
                // Dismiss the warning dialog and go back to the previous IME.
                // TODO: If we can find a way to bring the new activity to front while keeping
                // the warning dialog, we don't need to dismiss it here.
                mAlertDialog.cancel();
                context.startActivity(intent);
            }
        }
    }

    public void showPunctuationHintIfNecessary() {
        if (!VOICE_INSTALLED) {
            return;
        }
        InputConnection ic = mService.getCurrentInputConnection();
        if (!mImmediatelyAfterVoiceInput && mAfterVoiceInput && ic != null) {
            if (mHints.showPunctuationHintIfNecessary(ic)) {
                mVoiceInput.logPunctuationHintDisplayed();
            }
        }
        mImmediatelyAfterVoiceInput = false;
    }

    public void hideVoiceWindow(boolean configurationChanging) {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (!configurationChanging) {
            if (mAfterVoiceInput)
                mVoiceInput.logInputEnded();
            if (mVoiceWarningDialog != null && mVoiceWarningDialog.isShowing()) {
                mVoiceInput.logKeyboardWarningDialogDismissed();
                mVoiceWarningDialog.dismiss();
                mVoiceWarningDialog = null;
            }
            if (VOICE_INSTALLED & mRecognizing) {
                mVoiceInput.cancel();
            }
        }
        mWordToSuggestions.clear();
    }

    public void setCursorAndSelection(int newSelEnd, int newSelStart) {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (mAfterVoiceInput) {
            mVoiceInput.setCursorPos(newSelEnd);
            mVoiceInput.setSelectionSpan(newSelEnd - newSelStart);
        }
    }

    public void setVoiceInputHighlighted(boolean b) {
        mVoiceInputHighlighted = b;
    }

    public void setShowingVoiceSuggestions(boolean b) {
        mShowingVoiceSuggestions = b;
    }

    public boolean isVoiceButtonEnabled() {
        return mVoiceButtonEnabled;
    }

    public boolean isVoiceButtonOnPrimary() {
        return mVoiceButtonOnPrimary;
    }

    public boolean isVoiceInputHighlighted() {
        return mVoiceInputHighlighted;
    }

    public boolean isRecognizing() {
        return mRecognizing;
    }

    public boolean needsToShowWarningDialog() {
        return !mHasUsedVoiceInput
                || (!mLocaleSupportedForVoiceInput && !mHasUsedVoiceInputUnsupportedLocale);
    }

    public boolean getAndResetIsShowingHint() {
        boolean ret = mIsShowingHint;
        mIsShowingHint = false;
        return ret;
    }

    private void revertVoiceInput() {
        InputConnection ic = mService.getCurrentInputConnection();
        if (ic != null) ic.commitText("", 1);
        mService.updateSuggestions();
        mVoiceInputHighlighted = false;
    }

    public void commitVoiceInput() {
        if (VOICE_INSTALLED && mVoiceInputHighlighted) {
            InputConnection ic = mService.getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            mService.updateSuggestions();
            mVoiceInputHighlighted = false;
        }
    }

    public boolean logAndRevertVoiceInput() {
        if (!VOICE_INSTALLED) {
            return false;
        }
        if (mVoiceInputHighlighted) {
            mVoiceInput.incrementTextModificationDeleteCount(
                    mVoiceResults.candidates.get(0).toString().length());
            revertVoiceInput();
            return true;
        } else {
            return false;
        }
    }

    public void rememberReplacedWord(CharSequence suggestion,String wordSeparators) {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (mShowingVoiceSuggestions) {
            // Retain the replaced word in the alternatives array.
            String wordToBeReplaced = EditingUtils.getWordAtCursor(
                    mService.getCurrentInputConnection(), wordSeparators);
            if (!mWordToSuggestions.containsKey(wordToBeReplaced)) {
                wordToBeReplaced = wordToBeReplaced.toLowerCase();
            }
            if (mWordToSuggestions.containsKey(wordToBeReplaced)) {
                List<CharSequence> suggestions = mWordToSuggestions.get(wordToBeReplaced);
                if (suggestions.contains(suggestion)) {
                    suggestions.remove(suggestion);
                }
                suggestions.add(wordToBeReplaced);
                mWordToSuggestions.remove(wordToBeReplaced);
                mWordToSuggestions.put(suggestion.toString(), suggestions);
            }
        }
    }

    /**
     * Tries to apply any voice alternatives for the word if this was a spoken word and
     * there are voice alternatives.
     * @param touching The word that the cursor is touching, with position information
     * @return true if an alternative was found, false otherwise.
     */
    public boolean applyVoiceAlternatives(EditingUtils.SelectedWord touching) {
        if (!VOICE_INSTALLED) {
            return false;
        }
        // Search for result in spoken word alternatives
        String selectedWord = touching.mWord.toString().trim();
        if (!mWordToSuggestions.containsKey(selectedWord)) {
            selectedWord = selectedWord.toLowerCase();
        }
        if (mWordToSuggestions.containsKey(selectedWord)) {
            mShowingVoiceSuggestions = true;
            List<CharSequence> suggestions = mWordToSuggestions.get(selectedWord);
            SuggestedWords.Builder builder = new SuggestedWords.Builder();
            // If the first letter of touching is capitalized, make all the suggestions
            // start with a capital letter.
            if (Character.isUpperCase(touching.mWord.charAt(0))) {
                for (CharSequence word : suggestions) {
                    String str = word.toString();
                    word = Character.toUpperCase(str.charAt(0)) + str.substring(1);
                    builder.addWord(word);
                }
            } else {
                builder.addWords(suggestions, null);
            }
            builder.setTypedWordValid(true).setHasMinimalSuggestion(true);
            mService.setSuggestions(builder.build());
//            mService.setCandidatesViewShown(true);
            return true;
        }
        return false;
    }

    public void handleBackspace() {
        if (!VOICE_INSTALLED) {
            return;
        }
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
    }

    public void handleCharacter() {
        if (!VOICE_INSTALLED) {
            return;
        }
        commitVoiceInput();
        if (mAfterVoiceInput) {
            // Assume input length is 1. This assumption fails for smiley face insertions.
            mVoiceInput.incrementTextModificationInsertCount(1);
        }
    }

    public void handleSeparator() {
        if (!VOICE_INSTALLED) {
            return;
        }
        commitVoiceInput();
        if (mAfterVoiceInput){
            // Assume input length is 1. This assumption fails for smiley face insertions.
            mVoiceInput.incrementTextModificationInsertPunctuationCount(1);
        }
    }

    public void handleClose() {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (mRecognizing) {
            mVoiceInput.cancel();
        }
    }


    public void handleVoiceResults(boolean capitalizeFirstWord) {
        if (!VOICE_INSTALLED) {
            return;
        }
        mAfterVoiceInput = true;
        mImmediatelyAfterVoiceInput = true;

        InputConnection ic = mService.getCurrentInputConnection();
        if (!mService.isFullscreenMode()) {
            // Start listening for updates to the text from typing, etc.
            if (ic != null) {
                ExtractedTextRequest req = new ExtractedTextRequest();
                ic.getExtractedText(req, InputConnection.GET_EXTRACTED_TEXT_MONITOR);
            }
        }
        mService.vibrate();

        final List<CharSequence> nBest = new ArrayList<CharSequence>();
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
        mService.commitTyped(ic);
        EditingUtils.appendText(ic, bestResult);
        if (ic != null) ic.endBatchEdit();

        mVoiceInputHighlighted = true;
        mWordToSuggestions.putAll(mVoiceResults.alternatives);
        onCancelVoice();
    }

    public void switchToRecognitionStatusView(final Configuration configuration) {
        if (!VOICE_INSTALLED) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
//                mService.setCandidatesViewShown(false);
                mRecognizing = true;
                mVoiceInput.newView();
                View v = mVoiceInput.getView();

                ViewParent p = v.getParent();
                if (p != null && p instanceof ViewGroup) {
                    ((ViewGroup) p).removeView(v);
                }

                View keyboardView = KeyboardSwitcher.getInstance().getKeyboardView();

                // The full height of the keyboard is difficult to calculate
                // as the dimension is expressed in "mm" and not in "pixel"
                // As we add mm, we don't know how the rounding is going to work
                // thus we may end up with few pixels extra (or less).
                if (keyboardView != null) {
                    View popupLayout = v.findViewById(R.id.popup_layout);
                    final int displayHeight =
                            mService.getResources().getDisplayMetrics().heightPixels;
                    final int currentHeight = popupLayout.getLayoutParams().height;
                    final int keyboardHeight = keyboardView.getHeight();
                    if (mMinimumVoiceRecognitionViewHeightPixel > keyboardHeight
                            || mMinimumVoiceRecognitionViewHeightPixel > currentHeight) {
                        popupLayout.getLayoutParams().height =
                            mMinimumVoiceRecognitionViewHeightPixel;
                    } else if (keyboardHeight > currentHeight || keyboardHeight
                            > (displayHeight / RECOGNITIONVIEW_HEIGHT_THRESHOLD_RATIO)) {
                        popupLayout.getLayoutParams().height = keyboardHeight;
                    }
                }
                mService.setInputView(v);
                mService.updateInputViewShown();

                if (configuration != null) {
                    mVoiceInput.onConfigurationChanged(configuration);
                }
        }});
    }

    private void switchToLastInputMethod() {
        if (!VOICE_INSTALLED) {
            return;
        }
        final IBinder token = mService.getWindow().getWindow().getAttributes().token;
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return mImm.switchToLastInputMethod(token);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                // Calls in this method need to be done in the same thread as the thread which
                // called switchToLastInputMethod()
                if (!result) {
                    if (DEBUG) {
                        Log.d(TAG, "Couldn't switch back to last IME.");
                    }
                    // Because the current IME and subtype failed to switch to any other IME and
                    // subtype by switchToLastInputMethod, the current IME and subtype should keep
                    // being LatinIME and voice subtype in the next time. And for re-showing voice
                    // mode, the state of voice input should be reset and the voice view should be
                    // hidden.
                    mVoiceInput.reset();
                    mService.requestHideSelf(0);
                } else {
                    // Notify an event that the current subtype was changed. This event will be
                    // handled if "onCurrentInputMethodSubtypeChanged" can't be implemented
                    // when the API level is 10 or previous.
                    mService.notifyOnCurrentInputMethodSubtypeChanged(null);
                }
            }
        }.execute();
    }

    private void reallyStartListening(boolean swipe) {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (!mHasUsedVoiceInput) {
            // The user has started a voice input, so remember that in the
            // future (so we don't show the warning dialog after the first run).
            SharedPreferences.Editor editor =
                    PreferenceManager.getDefaultSharedPreferences(mService).edit();
            editor.putBoolean(PREF_HAS_USED_VOICE_INPUT, true);
            SharedPreferencesCompat.apply(editor);
            mHasUsedVoiceInput = true;
        }

        if (!mLocaleSupportedForVoiceInput && !mHasUsedVoiceInputUnsupportedLocale) {
            // The user has started a voice input from an unsupported locale, so remember that
            // in the future (so we don't show the warning dialog the next time they do this).
            SharedPreferences.Editor editor =
                    PreferenceManager.getDefaultSharedPreferences(mService).edit();
            editor.putBoolean(PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE, true);
            SharedPreferencesCompat.apply(editor);
            mHasUsedVoiceInputUnsupportedLocale = true;
        }

        // Clear N-best suggestions
        mService.clearSuggestions();

        FieldContext context = makeFieldContext();
        mVoiceInput.startListening(context, swipe);
        switchToRecognitionStatusView(null);
    }

    public void startListening(final boolean swipe, IBinder token) {
        if (!VOICE_INSTALLED) {
            return;
        }
        // TODO: remove swipe which is no longer used.
        if (needsToShowWarningDialog()) {
            // Calls reallyStartListening if user clicks OK, does nothing if user clicks Cancel.
            showVoiceWarningDialog(swipe, token);
        } else {
            reallyStartListening(swipe);
        }
    }

    private boolean fieldCanDoVoice(FieldContext fieldContext) {
        return !mPasswordText
                && mVoiceInput != null
                && !mVoiceInput.isBlacklistedField(fieldContext);
    }

    private boolean shouldShowVoiceButton(FieldContext fieldContext, EditorInfo attribute) {
        @SuppressWarnings("deprecation")
        final boolean noMic = Utils.inPrivateImeOptions(null,
                LatinIME.IME_OPTION_NO_MICROPHONE_COMPAT, attribute)
                || Utils.inPrivateImeOptions(mService.getPackageName(),
                        LatinIME.IME_OPTION_NO_MICROPHONE, attribute);
        return ENABLE_VOICE_BUTTON && fieldCanDoVoice(fieldContext) && !noMic
                && SpeechRecognizer.isRecognitionAvailable(mService);
    }

    public static boolean isRecognitionAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public void loadSettings(EditorInfo attribute, SharedPreferences sp) {
        if (!VOICE_INSTALLED) {
            return;
        }
        mHasUsedVoiceInput = sp.getBoolean(PREF_HAS_USED_VOICE_INPUT, false);
        mHasUsedVoiceInputUnsupportedLocale =
                sp.getBoolean(PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE, false);

        mLocaleSupportedForVoiceInput = SubtypeSwitcher.isVoiceSupported(
                mService, SubtypeSwitcher.getInstance().getInputLocaleStr());

        final String voiceMode = sp.getString(PREF_VOICE_MODE,
                mService.getString(R.string.voice_mode_main));
        mVoiceButtonEnabled = !voiceMode.equals(mService.getString(R.string.voice_mode_off))
                && shouldShowVoiceButton(makeFieldContext(), attribute);
        mVoiceButtonOnPrimary = voiceMode.equals(mService.getString(R.string.voice_mode_main));
    }

    public void destroy() {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (mVoiceInput != null) {
            mVoiceInput.destroy();
        }
    }

    public void onStartInputView(IBinder keyboardViewToken) {
        if (!VOICE_INSTALLED) {
            return;
        }
        // If keyboardViewToken is null, keyboardView is not attached but voiceView is attached.
        IBinder windowToken = keyboardViewToken != null ? keyboardViewToken
                : mVoiceInput.getView().getWindowToken();
        // If IME is in voice mode, but still needs to show the voice warning dialog,
        // keep showing the warning.
        if (mSubtypeSwitcher.isVoiceMode() && windowToken != null) {
            // Close keyboard view if it is been shown.
            if (KeyboardSwitcher.getInstance().isInputViewShown())
                KeyboardSwitcher.getInstance().getKeyboardView().purgeKeyboardAndClosing();
            startListening(false, windowToken);
        }
        // If we have no token, onAttachedToWindow will take care of showing dialog and start
        // listening.
    }

    public void onAttachedToWindow() {
        if (!VOICE_INSTALLED) {
            return;
        }
        // After onAttachedToWindow, we can show the voice warning dialog. See startListening()
        // above.
        VoiceInputWrapper.getInstance().setVoiceInput(mVoiceInput, mSubtypeSwitcher);
    }

    public void onConfigurationChanged(Configuration configuration) {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (mRecognizing) {
            switchToRecognitionStatusView(configuration);
        }
    }

    @Override
    public void onCancelVoice() {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (mRecognizing) {
            if (mSubtypeSwitcher.isVoiceMode()) {
                // If voice mode is being canceled within LatinIME (i.e. time-out or user
                // cancellation etc.), onCancelVoice() will be called first. LatinIME thinks it's
                // still in voice mode. LatinIME needs to call switchToLastInputMethod().
                // Note that onCancelVoice() will be called again from SubtypeSwitcher.
                switchToLastInputMethod();
            } else if (mSubtypeSwitcher.isKeyboardMode()) {
                // If voice mode is being canceled out of LatinIME (i.e. by user's IME switching or
                // as a result of switchToLastInputMethod() etc.),
                // onCurrentInputMethodSubtypeChanged() will be called first. LatinIME will know
                // that it's in keyboard mode and SubtypeSwitcher will call onCancelVoice().
                mRecognizing = false;
                mService.switchToKeyboardView();
            }
        }
    }

    @Override
    public void onVoiceResults(List<String> candidates,
            Map<String, List<CharSequence>> alternatives) {
        if (!VOICE_INSTALLED) {
            return;
        }
        if (!mRecognizing) {
            return;
        }
        mVoiceResults.candidates = candidates;
        mVoiceResults.alternatives = alternatives;
        mHandler.updateVoiceResults();
    }

    private FieldContext makeFieldContext() {
        SubtypeSwitcher switcher = SubtypeSwitcher.getInstance();
        return new FieldContext(mService.getCurrentInputConnection(),
                mService.getCurrentInputEditorInfo(), switcher.getInputLocaleStr(),
                switcher.getEnabledLanguages());
    }

    // TODO: make this private (proguard issue)
    public static class VoiceResults {
        List<String> candidates;
        Map<String, List<CharSequence>> alternatives;
    }

    public static class VoiceInputWrapper {
        private static final VoiceInputWrapper sInputWrapperInstance = new VoiceInputWrapper();
        private VoiceInput mVoiceInput;
        public static VoiceInputWrapper getInstance() {
            return sInputWrapperInstance;
        }
        private void setVoiceInput(VoiceInput voiceInput, SubtypeSwitcher switcher) {
            if (!VOICE_INSTALLED) {
                return;
            }
            if (mVoiceInput == null && voiceInput != null) {
                mVoiceInput = voiceInput;
            }
            switcher.setVoiceInputWrapper(this);
        }

        private VoiceInputWrapper() {
        }

        public void cancel() {
            if (!VOICE_INSTALLED) {
                return;
            }
            if (mVoiceInput != null) mVoiceInput.cancel();
        }

        public void reset() {
            if (!VOICE_INSTALLED) {
                return;
            }
            if (mVoiceInput != null) mVoiceInput.reset();
        }
    }

    // A list of locales which are supported by default for voice input, unless we get a
    // different list from Gservices.
    private static final String DEFAULT_VOICE_INPUT_SUPPORTED_LOCALES =
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

    public static String getSupportedLocalesString (ContentResolver resolver) {
        return SettingsUtil.getSettingsString(
                resolver,
                SettingsUtil.LATIN_IME_VOICE_INPUT_SUPPORTED_LOCALES,
                DEFAULT_VOICE_INPUT_SUPPORTED_LOCALES);
    }
}
