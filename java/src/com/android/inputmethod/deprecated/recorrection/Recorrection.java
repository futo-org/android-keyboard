/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.deprecated.recorrection;

import com.android.inputmethod.compat.InputConnectionCompatUtils;
import com.android.inputmethod.compat.SuggestionSpanUtils;
import com.android.inputmethod.deprecated.VoiceProxy;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.AutoCorrection;
import com.android.inputmethod.latin.CandidateView;
import com.android.inputmethod.latin.EditingUtils;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.Settings;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.TextEntryState;
import com.android.inputmethod.latin.WordComposer;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;

/**
 * Manager of re-correction functionalities
 */
public class Recorrection implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final Recorrection sInstance = new Recorrection();

    private LatinIME mService;
    private boolean mRecorrectionEnabled = false;
    private final ArrayList<RecorrectionSuggestionEntries> mRecorrectionSuggestionsList =
            new ArrayList<RecorrectionSuggestionEntries>();

    public static Recorrection getInstance() {
        return sInstance;
    }

    public static void init(LatinIME context, SharedPreferences prefs) {
        if (context == null || prefs == null) {
            return;
        }
        sInstance.initInternal(context, prefs);
    }

    private Recorrection() {
    }

    public boolean isRecorrectionEnabled() {
        return mRecorrectionEnabled;
    }

    private void initInternal(LatinIME context, SharedPreferences prefs) {
        if (SuggestionSpanUtils.SUGGESTION_SPAN_IS_SUPPORTED) {
            mRecorrectionEnabled = false;
            return;
        }
        updateRecorrectionEnabled(context.getResources(), prefs);
        mService = context;
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void checkRecorrectionOnStart() {
        if (SuggestionSpanUtils.SUGGESTION_SPAN_IS_SUPPORTED || !mRecorrectionEnabled) return;

        final InputConnection ic = mService.getCurrentInputConnection();
        if (ic == null) return;
        // There could be a pending composing span.  Clean it up first.
        ic.finishComposingText();

        if (mService.isShowingSuggestionsStrip() && mService.isSuggestionsRequested()) {
            // First get the cursor position. This is required by setOldSuggestions(), so that
            // it can pass the correct range to setComposingRegion(). At this point, we don't
            // have valid values for mLastSelectionStart/End because onUpdateSelection() has
            // not been called yet.
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0; // anything is fine here
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et == null) return;
            mService.setLastSelection(
                    et.startOffset + et.selectionStart, et.startOffset + et.selectionEnd);

            // Then look for possible corrections in a delayed fashion
            if (!TextUtils.isEmpty(et.text) && mService.isCursorTouchingWord()) {
                mService.mHandler.postUpdateOldSuggestions();
            }
        }
    }

    public void updateRecorrectionSelection(KeyboardSwitcher keyboardSwitcher,
            CandidateView candidateView, int candidatesStart, int candidatesEnd,
            int newSelStart, int newSelEnd, int oldSelStart, int lastSelectionStart,
            int lastSelectionEnd, boolean hasUncommittedTypedChars) {
        if (SuggestionSpanUtils.SUGGESTION_SPAN_IS_SUPPORTED || !mRecorrectionEnabled) return;
        if (!mService.isShowingSuggestionsStrip()) return;
        if (!keyboardSwitcher.isInputViewShown()) return;
        if (!mService.isSuggestionsRequested()) return;
        // Don't look for corrections if the keyboard is not visible
        // Check if we should go in or out of correction mode.
        if ((candidatesStart == candidatesEnd || newSelStart != oldSelStart || TextEntryState
                .isRecorrecting())
                && (newSelStart < newSelEnd - 1 || !hasUncommittedTypedChars)) {
            if (mService.isCursorTouchingWord() || lastSelectionStart < lastSelectionEnd) {
                mService.mHandler.cancelUpdateBigramPredictions();
                mService.mHandler.postUpdateOldSuggestions();
            } else {
                abortRecorrection(false);
                // If showing the "touch again to save" hint, do not replace it. Else,
                // show the bigrams if we are at the end of the text, punctuation
                // otherwise.
                if (candidateView != null && !candidateView.isShowingAddToDictionaryHint()) {
                    InputConnection ic = mService.getCurrentInputConnection();
                    if (null == ic || !TextUtils.isEmpty(ic.getTextAfterCursor(1, 0))) {
                        if (!mService.isShowingPunctuationList()) {
                            mService.setPunctuationSuggestions();
                        }
                    } else {
                        mService.mHandler.postUpdateBigramPredictions();
                    }
                }
            }
        }
    }

    public void saveRecorrectionSuggestion(WordComposer word, CharSequence result) {
        if (SuggestionSpanUtils.SUGGESTION_SPAN_IS_SUPPORTED || !mRecorrectionEnabled) return;
        if (word.size() <= 1) {
            return;
        }
        // Skip if result is null. It happens in some edge case.
        if (TextUtils.isEmpty(result)) {
            return;
        }

        // Make a copy of the CharSequence, since it is/could be a mutable CharSequence
        final String resultCopy = result.toString();
        RecorrectionSuggestionEntries entry = new RecorrectionSuggestionEntries(
                resultCopy, new WordComposer(word));
        mRecorrectionSuggestionsList.add(entry);
    }

    public void clearWordsInHistory() {
        mRecorrectionSuggestionsList.clear();
    }

    /**
     * Tries to apply any typed alternatives for the word if we have any cached alternatives,
     * otherwise tries to find new corrections and completions for the word.
     * @param touching The word that the cursor is touching, with position information
     * @return true if an alternative was found, false otherwise.
     */
    public boolean applyTypedAlternatives(WordComposer word, Suggest suggest,
            KeyboardSwitcher keyboardSwitcher, EditingUtils.SelectedWord touching) {
        if (SuggestionSpanUtils.SUGGESTION_SPAN_IS_SUPPORTED || !mRecorrectionEnabled) return false;
        // If we didn't find a match, search for result in typed word history
        WordComposer foundWord = null;
        RecorrectionSuggestionEntries alternatives = null;
        // Search old suggestions to suggest re-corrected suggestions.
        for (RecorrectionSuggestionEntries entry : mRecorrectionSuggestionsList) {
            if (TextUtils.equals(entry.getChosenWord(), touching.mWord)) {
                foundWord = entry.mWordComposer;
                alternatives = entry;
                break;
            }
        }
        // If we didn't find a match, at least suggest corrections as re-corrected suggestions.
        if (foundWord == null
                && (AutoCorrection.isValidWord(suggest.getUnigramDictionaries(),
                        touching.mWord, true))) {
            foundWord = new WordComposer();
            for (int i = 0; i < touching.mWord.length(); i++) {
                foundWord.add(touching.mWord.charAt(i),
                        new int[] { touching.mWord.charAt(i) }, WordComposer.NOT_A_COORDINATE,
                        WordComposer.NOT_A_COORDINATE);
            }
            foundWord.setFirstCharCapitalized(Character.isUpperCase(touching.mWord.charAt(0)));
        }
        // Found a match, show suggestions
        if (foundWord != null || alternatives != null) {
            if (alternatives == null) {
                alternatives = new RecorrectionSuggestionEntries(touching.mWord, foundWord);
            }
            showRecorrections(suggest, keyboardSwitcher, alternatives);
            if (foundWord != null) {
                word.init(foundWord);
            } else {
                word.reset();
            }
            return true;
        }
        return false;
    }


    private void showRecorrections(Suggest suggest, KeyboardSwitcher keyboardSwitcher,
            RecorrectionSuggestionEntries entries) {
        SuggestedWords.Builder builder = entries.getAlternatives(suggest, keyboardSwitcher);
        builder.setTypedWordValid(false).setHasMinimalSuggestion(false);
        mService.showSuggestions(builder.build(), entries.getOriginalWord());
    }

    public void setRecorrectionSuggestions(VoiceProxy voiceProxy, CandidateView candidateView,
            Suggest suggest, KeyboardSwitcher keyboardSwitcher, WordComposer word,
            boolean hasUncommittedTypedChars, int lastSelectionStart, int lastSelectionEnd,
            String wordSeparators) {
        if (!InputConnectionCompatUtils.RECORRECTION_SUPPORTED) return;
        if (SuggestionSpanUtils.SUGGESTION_SPAN_IS_SUPPORTED || !mRecorrectionEnabled) return;
        voiceProxy.setShowingVoiceSuggestions(false);
        if (candidateView != null && candidateView.isShowingAddToDictionaryHint()) {
            return;
        }
        InputConnection ic = mService.getCurrentInputConnection();
        if (ic == null) return;
        if (!hasUncommittedTypedChars) {
            // Extract the selected or touching text
            EditingUtils.SelectedWord touching = EditingUtils.getWordAtCursorOrSelection(ic,
                    lastSelectionStart, lastSelectionEnd, wordSeparators);

            if (touching != null && touching.mWord.length() > 1) {
                ic.beginBatchEdit();

                if (applyTypedAlternatives(word, suggest, keyboardSwitcher, touching)
                        || voiceProxy.applyVoiceAlternatives(touching)) {
                    TextEntryState.selectedForRecorrection();
                    InputConnectionCompatUtils.underlineWord(ic, touching);
                } else {
                    abortRecorrection(true);
                }

                ic.endBatchEdit();
            } else {
                abortRecorrection(true);
                mService.setPunctuationSuggestions();  // Show the punctuation suggestions list
            }
        } else {
            abortRecorrection(true);
        }
    }

    public void abortRecorrection(boolean force) {
        if (SuggestionSpanUtils.SUGGESTION_SPAN_IS_SUPPORTED) return;
        if (force || TextEntryState.isRecorrecting()) {
            TextEntryState.onAbortRecorrection();
            mService.setCandidatesViewShown(mService.isCandidateStripVisible());
            mService.getCurrentInputConnection().finishComposingText();
            mService.clearSuggestions();
        }
    }

    public void updateRecorrectionEnabled(Resources res, SharedPreferences prefs) {
        // If the option should not be shown, do not read the re-correction preference
        // but always use the default setting defined in the resources.
        if (res.getBoolean(R.bool.config_enable_show_recorrection_option)) {
            mRecorrectionEnabled = prefs.getBoolean(Settings.PREF_RECORRECTION_ENABLED,
                    res.getBoolean(R.bool.config_default_recorrection_enabled));
        } else {
            mRecorrectionEnabled = res.getBoolean(R.bool.config_default_recorrection_enabled);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (SuggestionSpanUtils.SUGGESTION_SPAN_IS_SUPPORTED) return;
        if (key.equals(Settings.PREF_RECORRECTION_ENABLED)) {
            updateRecorrectionEnabled(mService.getResources(), prefs);
        }
    }
}
