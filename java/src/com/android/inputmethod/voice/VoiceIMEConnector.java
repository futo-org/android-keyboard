/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.inputmethod.voice;

import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.EditingUtils;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinIME.UIHandler;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SharedPreferencesCompat;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.SuggestedWords;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.speech.SpeechRecognizer;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VoiceIMEConnector implements VoiceInput.UiListener {
    private static final VoiceIMEConnector sInstance = new VoiceIMEConnector();

    public static final boolean VOICE_INSTALLED = true;
    private static final boolean ENABLE_VOICE_BUTTON = true;
    private static final String PREF_VOICE_MODE = "voice_mode";
    // Whether or not the user has used voice input before (and thus, whether to show the
    // first-run warning dialog or not).
    private static final String PREF_HAS_USED_VOICE_INPUT = "has_used_voice_input";
    // Whether or not the user has used voice input from an unsupported locale UI before.
    // For example, the user has a Chinese UI but activates voice input.
    private static final String PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE =
            "has_used_voice_input_unsupported_locale";
    // The private IME option used to indicate that no microphone should be shown for a
    // given text field. For instance this is specified by the search dialog when the
    // dialog is already showing a voice search button.
    private static final String IME_OPTION_NO_MICROPHONE = "nm";

    @SuppressWarnings("unused")
    private static final String TAG = "VoiceIMEConnector";

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

    private InputMethodManager mImm;
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

    public static VoiceIMEConnector init(LatinIME context, SharedPreferences prefs, UIHandler h) {
        sInstance.initInternal(context, prefs, h);
        return sInstance;
    }

    public static VoiceIMEConnector getInstance() {
        return sInstance;
    }

    private void initInternal(LatinIME service, SharedPreferences prefs, UIHandler h) {
        mService = service;
        mHandler = h;
        mImm = (InputMethodManager) service.getSystemService(Context.INPUT_METHOD_SERVICE);
        mSubtypeSwitcher = SubtypeSwitcher.getInstance();
        if (VOICE_INSTALLED) {
            mVoiceInput = new VoiceInput(service, this);
            mHints = new Hints(service, prefs, new Hints.Display() {
                @Override
                public void showHint(int viewResource) {
                    View view = LayoutInflater.from(mService).inflate(viewResource, null);
                    mService.setCandidatesView(view);
                    mService.setCandidatesViewShown(true);
                    mIsShowingHint = true;
                }
              });
        }
    }

    private VoiceIMEConnector() {
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
        if (VOICE_INSTALLED && !configurationChanged) {
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
        AlertDialog.Builder builder = new AlertDialog.Builder(mService);
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
        Window window = mVoiceWarningDialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = token;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        mVoiceInput.logKeyboardWarningDialogShown();
        mVoiceWarningDialog.show();
        // Make URL in the dialog message clickable
        TextView textView = (TextView) mVoiceWarningDialog.findViewById(android.R.id.message);
        if (textView != null) {
            final CustomLinkMovementMethod method = CustomLinkMovementMethod.getInstance();
            method.setVoiceWarningDialog(mVoiceWarningDialog);
            textView.setMovementMethod(method);
        }
    }

    private static class CustomLinkMovementMethod extends LinkMovementMethod {
        private static CustomLinkMovementMethod sLinkMovementMethodInstance =
                new CustomLinkMovementMethod();
        private AlertDialog mAlertDialog;

        public void setVoiceWarningDialog(AlertDialog alertDialog) {
            mAlertDialog = alertDialog;
        }

        public static CustomLinkMovementMethod getInstance() {
            return sLinkMovementMethodInstance;
        }

        // Almost the same as LinkMovementMethod.onTouchEvent(), but overrides it for
        // FLAG_ACTIVITY_NEW_TASK and mAlertDialog.cancel().
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            int action = event.getAction();

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getTotalPaddingLeft();
                y -= widget.getTotalPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

                if (link.length != 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        if (link[0] instanceof URLSpan) {
                            URLSpan urlSpan = (URLSpan) link[0];
                            Uri uri = Uri.parse(urlSpan.getURL());
                            Context context = widget.getContext();
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
                            if (mAlertDialog != null) {
                                // Go back to the previous IME for now.
                                // TODO: If we can find a way to bring the new activity to front
                                // while keeping the warning dialog, we don't need to cancel here.
                                mAlertDialog.cancel();
                            }
                            context.startActivity(intent);
                        } else {
                            link[0].onClick(widget);
                        }
                    } else if (action == MotionEvent.ACTION_DOWN) {
                        Selection.setSelection(buffer, buffer.getSpanStart(link[0]),
                                buffer.getSpanEnd(link[0]));
                    }
                    return true;
                } else {
                    Selection.removeSelection(buffer);
                }
            }
            return super.onTouchEvent(widget, buffer, event);
        }
    }

    public void showPunctuationHintIfNecessary() {
        InputConnection ic = mService.getCurrentInputConnection();
        if (!mImmediatelyAfterVoiceInput && mAfterVoiceInput && ic != null) {
            if (mHints.showPunctuationHintIfNecessary(ic)) {
                mVoiceInput.logPunctuationHintDisplayed();
            }
        }
        mImmediatelyAfterVoiceInput = false;
    }

    public void hideVoiceWindow(boolean configurationChanging) {
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
        if (VOICE_INSTALLED && mVoiceInputHighlighted) {
            mVoiceInput.incrementTextModificationDeleteCount(
                    mVoiceResults.candidates.get(0).toString().length());
            revertVoiceInput();
            return true;
        } else {
            return false;
        }
    }

    public void rememberReplacedWord(CharSequence suggestion,String wordSeparators) {
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
            mService.setCandidatesViewShown(true);
            return true;
        }
        return false;
    }

    public void handleBackspace() {
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
        commitVoiceInput();
        if (mAfterVoiceInput) {
            // Assume input length is 1. This assumption fails for smiley face insertions.
            mVoiceInput.incrementTextModificationInsertCount(1);
        }
    }

    public void handleSeparator() {
        commitVoiceInput();
        if (mAfterVoiceInput){
            // Assume input length is 1. This assumption fails for smiley face insertions.
            mVoiceInput.incrementTextModificationInsertPunctuationCount(1);
        }
    }

    public void handleClose() {
        if (VOICE_INSTALLED & mRecognizing) {
            mVoiceInput.cancel();
        }
    }


    public void handleVoiceResults(boolean capitalizeFirstWord) {
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
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mService.setCandidatesViewShown(false);
                mRecognizing = true;
                mVoiceInput.newView();
                View v = mVoiceInput.getView();

                ViewParent p = v.getParent();
                if (p != null && p instanceof ViewGroup) {
                    ((ViewGroup) p).removeView(v);
                }

                View keyboardView = KeyboardSwitcher.getInstance().getInputView();

                // The full height of the keyboard is difficult to calculate
                // as the dimension is expressed in "mm" and not in "pixel"
                // As we add mm, we don't know how the rounding is going to work
                // thus we may end up with few pixels extra (or less).
                if (keyboardView != null) {
                    int h = keyboardView.getHeight();
                    if (h > 0) {
                        View popupLayout = v.findViewById(R.id.popup_layout);
                        popupLayout.getLayoutParams().height = h;
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
        IBinder token = mService.getWindow().getWindow().getAttributes().token;
        mImm.switchToLastInputMethod(token);
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
        // TODO: remove swipe which is no longer used.
        if (VOICE_INSTALLED) {
            if (needsToShowWarningDialog()) {
                // Calls reallyStartListening if user clicks OK, does nothing if user clicks Cancel.
                showVoiceWarningDialog(swipe, token);
            } else {
                reallyStartListening(swipe);
            }
        }
    }

    private boolean fieldCanDoVoice(FieldContext fieldContext) {
        return !mPasswordText
                && mVoiceInput != null
                && !mVoiceInput.isBlacklistedField(fieldContext);
    }

    private boolean shouldShowVoiceButton(FieldContext fieldContext, EditorInfo attribute) {
        return ENABLE_VOICE_BUTTON && fieldCanDoVoice(fieldContext)
                && !(attribute != null
                        && IME_OPTION_NO_MICROPHONE.equals(attribute.privateImeOptions))
                && SpeechRecognizer.isRecognitionAvailable(mService);
    }

    public void loadSettings(EditorInfo attribute, SharedPreferences sp) {
        mHasUsedVoiceInput = sp.getBoolean(PREF_HAS_USED_VOICE_INPUT, false);
        mHasUsedVoiceInputUnsupportedLocale =
                sp.getBoolean(PREF_HAS_USED_VOICE_INPUT_UNSUPPORTED_LOCALE, false);

        mLocaleSupportedForVoiceInput = SubtypeSwitcher.getInstance().isVoiceSupported(
                SubtypeSwitcher.getInstance().getInputLocaleStr());

        if (VOICE_INSTALLED) {
            final String voiceMode = sp.getString(PREF_VOICE_MODE,
                    mService.getString(R.string.voice_mode_main));
            mVoiceButtonEnabled = !voiceMode.equals(mService.getString(R.string.voice_mode_off))
                    && shouldShowVoiceButton(makeFieldContext(), attribute);
            mVoiceButtonOnPrimary = voiceMode.equals(mService.getString(R.string.voice_mode_main));
        }
    }

    public void destroy() {
        if (VOICE_INSTALLED && mVoiceInput != null) {
            mVoiceInput.destroy();
        }
    }

    public void onStartInputView(IBinder token) {
        // If IME is in voice mode, but still needs to show the voice warning dialog,
        // keep showing the warning.
        if (mSubtypeSwitcher.isVoiceMode() && token != null) {
            // Close keyboard view if it is been shown.
            if (KeyboardSwitcher.getInstance().isInputViewShown())
                KeyboardSwitcher.getInstance().getInputView().purgeKeyboardAndClosing();
            startListening(false, token);
        }
        // If we have no token, onAttachedToWindow will take care of showing dialog and start
        // listening.
    }

    public void onAttachedToWindow() {
        // After onAttachedToWindow, we can show the voice warning dialog. See startListening()
        // above.
        mSubtypeSwitcher.setVoiceInput(mVoiceInput);
    }

    public void onConfigurationChanged(Configuration configuration) {
        if (mRecognizing) {
            switchToRecognitionStatusView(configuration);
        }
    }

    @Override
    public void onCancelVoice() {
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
        if (!mRecognizing) {
            return;
        }
        mVoiceResults.candidates = candidates;
        mVoiceResults.alternatives = alternatives;
        mHandler.updateVoiceResults();
    }

    public FieldContext makeFieldContext() {
        SubtypeSwitcher switcher = SubtypeSwitcher.getInstance();
        return new FieldContext(mService.getCurrentInputConnection(),
                mService.getCurrentInputEditorInfo(), switcher.getInputLocaleStr(),
                switcher.getEnabledLanguages());
    }

    private class VoiceResults {
        List<String> candidates;
        Map<String, List<CharSequence>> alternatives;
    }
}
