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

package com.android.inputmethod.latin;

import com.android.inputmethod.keyboard.KeyboardSwitcher;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

/**
 * Manager of re-correction functionalities
 */
public class Recorrection {
    private static final Recorrection sInstance = new Recorrection();

    private LatinIME mService;
    private boolean mRecorrectionEnabled = false;

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
        final Resources res = context.getResources();
        // If the option should not be shown, do not read the re-correction preference
        // but always use the default setting defined in the resources.
        if (res.getBoolean(R.bool.config_enable_show_recorrection_option)) {
            mRecorrectionEnabled = prefs.getBoolean(Settings.PREF_RECORRECTION_ENABLED,
                    res.getBoolean(R.bool.config_default_recorrection_enabled));
        } else {
            mRecorrectionEnabled = res.getBoolean(R.bool.config_default_recorrection_enabled);
        }
        mService = context;
    }

    public void checkRecorrectionOnStart() {
        if (!mRecorrectionEnabled) return;

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
            CandidateView candidateView, int candidatesStart, int candidatesEnd, int newSelStart,
            int newSelEnd, int oldSelStart, int lastSelectionStart,
            int lastSelectionEnd, boolean hasUncommittedTypedChars) {
        if (mRecorrectionEnabled && mService.isShowingSuggestionsStrip()) {
            // Don't look for corrections if the keyboard is not visible
            if (keyboardSwitcher.isInputViewShown()) {
                // Check if we should go in or out of correction mode.
                if (mService.isSuggestionsRequested()
                        && (candidatesStart == candidatesEnd || newSelStart != oldSelStart
                                || TextEntryState.isRecorrecting())
                                && (newSelStart < newSelEnd - 1 || !hasUncommittedTypedChars)) {
                    if (mService.isCursorTouchingWord() || lastSelectionStart < lastSelectionEnd) {
                        mService.mHandler.cancelUpdateBigramPredictions();
                        mService.mHandler.postUpdateOldSuggestions();
                    } else {
                        mService.abortRecorrection(false);
                        // If showing the "touch again to save" hint, do not replace it. Else,
                        // show the bigrams if we are at the end of the text, punctuation otherwise.
                        if (candidateView != null
                                && !candidateView.isShowingAddToDictionaryHint()) {
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
        }
    }
}
