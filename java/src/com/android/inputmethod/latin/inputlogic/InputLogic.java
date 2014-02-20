/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.inputlogic;

import android.os.SystemClock;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.SuggestionSpanUtils;
import com.android.inputmethod.event.EventInterpreter;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.InputPointers;
import com.android.inputmethod.latin.LastComposedWord;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.RichInputConnection;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.settings.SpacingAndPunctuations;
import com.android.inputmethod.latin.suggestions.SuggestionStripViewAccessor;
import com.android.inputmethod.latin.utils.AsyncResultHolder;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.InputTypeUtils;
import com.android.inputmethod.latin.utils.LatinImeLoggerUtils;
import com.android.inputmethod.latin.utils.RecapitalizeStatus;
import com.android.inputmethod.latin.utils.StringUtils;
import com.android.inputmethod.latin.utils.TextRange;
import com.android.inputmethod.research.ResearchLogger;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * This class manages the input logic.
 */
public final class InputLogic {
    private static final String TAG = InputLogic.class.getSimpleName();

    // TODO : Remove this member when we can.
    private final LatinIME mLatinIME;
    private final SuggestionStripViewAccessor mSuggestionStripViewAccessor;

    // Never null.
    private InputLogicHandler mInputLogicHandler = InputLogicHandler.NULL_HANDLER;

    // TODO : make all these fields private as soon as possible.
    // Current space state of the input method. This can be any of the above constants.
    public int mSpaceState;
    // Never null
    public SuggestedWords mSuggestedWords = SuggestedWords.EMPTY;
    // TODO: mSuggest should be touched by a single thread.
    public volatile Suggest mSuggest;
    // The event interpreter should never be null.
    public final EventInterpreter mEventInterpreter;

    public LastComposedWord mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
    public final WordComposer mWordComposer;
    public final RichInputConnection mConnection;
    public final RecapitalizeStatus mRecapitalizeStatus = new RecapitalizeStatus();

    private int mDeleteCount;
    private long mLastKeyTime;
    public final TreeSet<Long> mCurrentlyPressedHardwareKeys = CollectionUtils.newTreeSet();

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private String mEnteredText;

    // TODO: This boolean is persistent state and causes large side effects at unexpected times.
    // Find a way to remove it for readability.
    public boolean mIsAutoCorrectionIndicatorOn;

    public InputLogic(final LatinIME latinIME,
            final SuggestionStripViewAccessor suggestionStripViewAccessor) {
        mLatinIME = latinIME;
        mSuggestionStripViewAccessor = suggestionStripViewAccessor;
        mWordComposer = new WordComposer();
        mEventInterpreter = new EventInterpreter(latinIME);
        mConnection = new RichInputConnection(latinIME);
        mInputLogicHandler = InputLogicHandler.NULL_HANDLER;
    }

    /**
     * Initializes the input logic for input in an editor.
     *
     * Call this when input starts or restarts in some editor (typically, in onStartInputView).
     * If the input is starting in the same field as before, set `restarting' to true. This allows
     * the input logic to reset only necessary stuff and save performance. Also, when restarting
     * some things must not be done (for example, the keyboard should not be reset to the
     * alphabetic layout), so do not send false to this just in case.
     *
     * @param restarting whether input is starting in the same field as before. Unused for now.
     * @param editorInfo the editorInfo associated with the editor.
     */
    public void startInput(final boolean restarting, final EditorInfo editorInfo) {
        mEnteredText = null;
        resetComposingState(true /* alsoResetLastComposedWord */);
        mDeleteCount = 0;
        mSpaceState = SpaceState.NONE;
        mRecapitalizeStatus.deactivate();
        mCurrentlyPressedHardwareKeys.clear();
        mSuggestedWords = SuggestedWords.EMPTY;
        // In some cases (namely, after rotation of the device) editorInfo.initialSelStart is lying
        // so we try using some heuristics to find out about these and fix them.
        mConnection.tryFixLyingCursorPosition();
        mInputLogicHandler = new InputLogicHandler(mLatinIME, this);
    }

    /**
     * Clean up the input logic after input is finished.
     */
    public void finishInput() {
        if (mWordComposer.isComposingWord()) {
            mConnection.finishComposingText();
        }
        resetComposingState(true /* alsoResetLastComposedWord */);
        mInputLogicHandler.destroy();
        mInputLogicHandler = InputLogicHandler.NULL_HANDLER;
    }

    /**
     * React to a string input.
     *
     * This is triggered by keys that input many characters at once, like the ".com" key or
     * some additional keys for example.
     *
     * @param settingsValues the current values of the settings.
     * @param rawText the text to input.
     */
    public void onTextInput(final SettingsValues settingsValues, final String rawText,
            // TODO: remove this argument
            final LatinIME.UIHandler handler) {
        mConnection.beginBatchEdit();
        if (mWordComposer.isComposingWord()) {
            commitCurrentAutoCorrection(settingsValues, rawText, handler);
        } else {
            resetComposingState(true /* alsoResetLastComposedWord */);
        }
        handler.postUpdateSuggestionStrip();
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS
                && ResearchLogger.RESEARCH_KEY_OUTPUT_TEXT.equals(rawText)) {
            ResearchLogger.getInstance().onResearchKeySelected(mLatinIME);
            return;
        }
        final String text = performSpecificTldProcessingOnTextInput(rawText);
        if (SpaceState.PHANTOM == mSpaceState) {
            promotePhantomSpace(settingsValues);
        }
        mConnection.commitText(text, 1);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onTextInput(text, false /* isBatchMode */);
        }
        mConnection.endBatchEdit();
        // Space state must be updated before calling updateShiftState
        mSpaceState = SpaceState.NONE;
        mEnteredText = text;
    }

    /**
     * A suggestion was picked from the suggestion strip.
     * @param settingsValues the current values of the settings.
     * @param index the index of the suggestion.
     * @param suggestionInfo the suggestion info.
     */
    // Called from {@link SuggestionStripView} through the {@link SuggestionStripView#Listener}
    // interface
    public void onPickSuggestionManually(final SettingsValues settingsValues,
            final int index, final SuggestedWordInfo suggestionInfo,
            // TODO: remove these two arguments
            final LatinIME.UIHandler handler, final KeyboardSwitcher keyboardSwitcher) {
        final SuggestedWords suggestedWords = mSuggestedWords;
        final String suggestion = suggestionInfo.mWord;
        // If this is a punctuation picked from the suggestion strip, pass it to onCodeInput
        if (suggestion.length() == 1 && suggestedWords.isPunctuationSuggestions()) {
            // Word separators are suggested before the user inputs something.
            // So, LatinImeLogger logs "" as a user's input.
            LatinImeLogger.logOnManualSuggestion("", suggestion, index, suggestedWords);
            // Rely on onCodeInput to do the complicated swapping/stripping logic consistently.
            final int primaryCode = suggestion.charAt(0);
            onCodeInput(primaryCode,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    settingsValues, handler, keyboardSwitcher);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_punctuationSuggestion(index, suggestion,
                        false /* isBatchMode */, suggestedWords.mIsPrediction);
            }
            return;
        }

        mConnection.beginBatchEdit();
        if (SpaceState.PHANTOM == mSpaceState && suggestion.length() > 0
                // In the batch input mode, a manually picked suggested word should just replace
                // the current batch input text and there is no need for a phantom space.
                && !mWordComposer.isBatchMode()) {
            final int firstChar = Character.codePointAt(suggestion, 0);
            if (!settingsValues.isWordSeparator(firstChar)
                    || settingsValues.isUsuallyPrecededBySpace(firstChar)) {
                promotePhantomSpace(settingsValues);
            }
        }

        // TODO: stop relying on mApplicationSpecifiedCompletions. The SuggestionInfo object
        // should contain a reference to the CompletionInfo instead.
        if (settingsValues.isApplicationSpecifiedCompletionsOn()
                && mLatinIME.mApplicationSpecifiedCompletions != null
                && index >= 0 && index < mLatinIME.mApplicationSpecifiedCompletions.length) {
            mSuggestedWords = SuggestedWords.EMPTY;
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
            keyboardSwitcher.updateShiftState();
            resetComposingState(true /* alsoResetLastComposedWord */);
            final CompletionInfo completionInfo = mLatinIME.mApplicationSpecifiedCompletions[index];
            mConnection.commitCompletion(completionInfo);
            mConnection.endBatchEdit();
            return;
        }

        // We need to log before we commit, because the word composer will store away the user
        // typed word.
        final String replacedWord = mWordComposer.getTypedWord();
        LatinImeLogger.logOnManualSuggestion(replacedWord, suggestion, index, suggestedWords);
        commitChosenWord(settingsValues, suggestion,
                LastComposedWord.COMMIT_TYPE_MANUAL_PICK, LastComposedWord.NOT_A_SEPARATOR);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_pickSuggestionManually(replacedWord, index, suggestion,
                    mWordComposer.isBatchMode(), suggestionInfo.mScore,
                    suggestionInfo.mKind, suggestionInfo.mSourceDict.mDictType);
        }
        mConnection.endBatchEdit();
        // Don't allow cancellation of manual pick
        mLastComposedWord.deactivate();
        // Space state must be updated before calling updateShiftState
        mSpaceState = SpaceState.PHANTOM;
        keyboardSwitcher.updateShiftState();

        // We should show the "Touch again to save" hint if the user pressed the first entry
        // AND it's in none of our current dictionaries (main, user or otherwise).
        // Please note that if mSuggest is null, it means that everything is off: suggestion
        // and correction, so we shouldn't try to show the hint
        final Suggest suggest = mSuggest;
        final boolean showingAddToDictionaryHint =
                (SuggestedWordInfo.KIND_TYPED == suggestionInfo.mKind
                        || SuggestedWordInfo.KIND_OOV_CORRECTION == suggestionInfo.mKind)
                        && suggest != null
                        // If the suggestion is not in the dictionary, the hint should be shown.
                        && !suggest.mDictionaryFacilitator.isValidWord(suggestion,
                                true /* ignoreCase */);

        if (settingsValues.mIsInternal) {
            LatinImeLoggerUtils.onSeparator((char)Constants.CODE_SPACE,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        }
        if (showingAddToDictionaryHint
                && suggest.mDictionaryFacilitator.isUserDictionaryEnabled()) {
            mSuggestionStripViewAccessor.showAddToDictionaryHint(suggestion);
        } else {
            // If we're not showing the "Touch again to save", then update the suggestion strip.
            handler.postUpdateSuggestionStrip();
        }
    }

    /**
     * Consider an update to the cursor position. Evaluate whether this update has happened as
     * part of normal typing or whether it was an explicit cursor move by the user. In any case,
     * do the necessary adjustments.
     * @param settingsValues the current settings
     * @param oldSelStart old selection start
     * @param oldSelEnd old selection end
     * @param newSelStart new selection start
     * @param newSelEnd new selection end
     * @param composingSpanStart composing span start
     * @param composingSpanEnd composing span end
     * @return whether the cursor has moved as a result of user interaction.
     */
    public boolean onUpdateSelection(final SettingsValues settingsValues,
            final int oldSelStart, final int oldSelEnd,
            final int newSelStart, final int newSelEnd,
            final int composingSpanStart, final int composingSpanEnd) {
        if (mConnection.isBelatedExpectedUpdate(oldSelStart, newSelStart, oldSelEnd, newSelEnd)) {
            return false;
        }
        // TODO: the following is probably better done in resetEntireInputState().
        // it should only happen when the cursor moved, and the very purpose of the
        // test below is to narrow down whether this happened or not. Likewise with
        // the call to updateShiftState.
        // We set this to NONE because after a cursor move, we don't want the space
        // state-related special processing to kick in.
        mSpaceState = SpaceState.NONE;

        final boolean selectionChangedOrSafeToReset =
                oldSelStart != newSelStart || oldSelEnd != newSelEnd // selection changed
                || !mWordComposer.isComposingWord(); // safe to reset
        final boolean hasOrHadSelection = (oldSelStart != oldSelEnd || newSelStart != newSelEnd);
        final int moveAmount = newSelStart - oldSelStart;
        if (selectionChangedOrSafeToReset && (hasOrHadSelection
                || !mWordComposer.moveCursorByAndReturnIfInsideComposingWord(moveAmount))) {
            // If we are composing a word and moving the cursor, we would want to set a
            // suggestion span for recorrection to work correctly. Unfortunately, that
            // would involve the keyboard committing some new text, which would move the
            // cursor back to where it was. Latin IME could then fix the position of the cursor
            // again, but the asynchronous nature of the calls results in this wreaking havoc
            // with selection on double tap and the like.
            // Another option would be to send suggestions each time we set the composing
            // text, but that is probably too expensive to do, so we decided to leave things
            // as is.
            // Also, we're posting a resume suggestions message, and this will update the
            // suggestions strip in a few milliseconds, so if we cleared the suggestion strip here
            // we'd have the suggestion strip noticeably janky. To avoid that, we don't clear
            // it here, which means we'll keep outdated suggestions for a split second but the
            // visual result is better.
            resetEntireInputState(settingsValues, newSelStart, newSelEnd,
                    false /* clearSuggestionStrip */);
        } else {
            // resetEntireInputState calls resetCachesUponCursorMove, but forcing the
            // composition to end. But in all cases where we don't reset the entire input
            // state, we still want to tell the rich input connection about the new cursor
            // position so that it can update its caches.
            mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                    newSelStart, newSelEnd, false /* shouldFinishComposition */);
        }

        // We moved the cursor. If we are touching a word, we need to resume suggestion.
        mLatinIME.mHandler.postResumeSuggestions();
        // Reset the last recapitalization.
        mRecapitalizeStatus.deactivate();
        return true;
    }

    /**
     * React to a code input. It may be a code point to insert, or a symbolic value that influences
     * the keyboard behavior.
     *
     * Typically, this is called whenever a key is pressed on the software keyboard. This is not
     * the entry point for gesture input; see the onBatchInput* family of functions for this.
     *
     * @param code the code to handle. It may be a code point, or an internal key code.
     * @param x the x-coordinate where the user pressed the key, or NOT_A_COORDINATE.
     * @param y the y-coordinate where the user pressed the key, or NOT_A_COORDINATE.
     */
    public void onCodeInput(final int code, final int x, final int y,
            final SettingsValues settingsValues,
            // TODO: remove these two arguments
            final LatinIME.UIHandler handler, final KeyboardSwitcher keyboardSwitcher) {
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onCodeInput(code, x, y);
        }
        final long when = SystemClock.uptimeMillis();
        if (code != Constants.CODE_DELETE
                || when > mLastKeyTime + Constants.LONG_PRESS_MILLISECONDS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
        mConnection.beginBatchEdit();
        // The space state depends only on the last character pressed and its own previous
        // state. Here, we revert the space state to neutral if the key is actually modifying
        // the input contents (any non-shift key), which is what we should do for
        // all inputs that do not result in a special state. Each character handling is then
        // free to override the state as they see fit.
        final int spaceState = mSpaceState;
        if (!mWordComposer.isComposingWord()) {
            mIsAutoCorrectionIndicatorOn = false;
        }

        // TODO: Consolidate the double-space period timer, mLastKeyTime, and the space state.
        if (code != Constants.CODE_SPACE) {
            handler.cancelDoubleSpacePeriodTimer();
        }

        boolean didAutoCorrect = false;
        switch (code) {
        case Constants.CODE_DELETE:
            handleBackspace(settingsValues, spaceState, handler, keyboardSwitcher);
            LatinImeLogger.logOnDelete(x, y);
            break;
        case Constants.CODE_SHIFT:
            performRecapitalization(settingsValues);
            keyboardSwitcher.updateShiftState();
            break;
        case Constants.CODE_CAPSLOCK:
            // Note: Changing keyboard to shift lock state is handled in
            // {@link KeyboardSwitcher#onCodeInput(int)}.
            break;
        case Constants.CODE_SYMBOL_SHIFT:
            // Note: Calling back to the keyboard on the symbol Shift key is handled in
            // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
            break;
        case Constants.CODE_SWITCH_ALPHA_SYMBOL:
            // Note: Calling back to the keyboard on symbol key is handled in
            // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
            break;
        case Constants.CODE_SETTINGS:
            onSettingsKeyPressed();
            break;
        case Constants.CODE_SHORTCUT:
            // We need to switch to the shortcut IME. This is handled by LatinIME since the
            // input logic has no business with IME switching.
            break;
        case Constants.CODE_ACTION_NEXT:
            performEditorAction(EditorInfo.IME_ACTION_NEXT);
            break;
        case Constants.CODE_ACTION_PREVIOUS:
            performEditorAction(EditorInfo.IME_ACTION_PREVIOUS);
            break;
        case Constants.CODE_LANGUAGE_SWITCH:
            handleLanguageSwitchKey();
            break;
        case Constants.CODE_EMOJI:
            // Note: Switching emoji keyboard is being handled in
            // {@link KeyboardState#onCodeInput(int,int)}.
            break;
        case Constants.CODE_ENTER:
            final EditorInfo editorInfo = getCurrentInputEditorInfo();
            final int imeOptionsActionId =
                    InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo);
            if (InputTypeUtils.IME_ACTION_CUSTOM_LABEL == imeOptionsActionId) {
                // Either we have an actionLabel and we should performEditorAction with actionId
                // regardless of its value.
                performEditorAction(editorInfo.actionId);
            } else if (EditorInfo.IME_ACTION_NONE != imeOptionsActionId) {
                // We didn't have an actionLabel, but we had another action to execute.
                // EditorInfo.IME_ACTION_NONE explicitly means no action. In contrast,
                // EditorInfo.IME_ACTION_UNSPECIFIED is the default value for an action, so it
                // means there should be an action and the app didn't bother to set a specific
                // code for it - presumably it only handles one. It does not have to be treated
                // in any specific way: anything that is not IME_ACTION_NONE should be sent to
                // performEditorAction.
                performEditorAction(imeOptionsActionId);
            } else {
                // No action label, and the action from imeOptions is NONE: this is a regular
                // enter key that should input a carriage return.
                didAutoCorrect = handleNonSpecialCharacter(settingsValues, Constants.CODE_ENTER,
                        x, y, spaceState, keyboardSwitcher, handler);
            }
            break;
        case Constants.CODE_SHIFT_ENTER:
            didAutoCorrect = handleNonSpecialCharacter(settingsValues, Constants.CODE_ENTER,
                    x, y, spaceState, keyboardSwitcher, handler);
            break;
        case Constants.CODE_ALPHA_FROM_EMOJI:
            // Note: Switching back from Emoji keyboard to the main keyboard is being handled in
            // {@link KeyboardState#onCodeInput(int,int)}.
            break;
        default:
            didAutoCorrect = handleNonSpecialCharacter(settingsValues,
                    code, x, y, spaceState, keyboardSwitcher, handler);
            break;
        }
        // Reset after any single keystroke, except shift, capslock, and symbol-shift
        if (!didAutoCorrect && code != Constants.CODE_SHIFT
                && code != Constants.CODE_CAPSLOCK
                && code != Constants.CODE_SWITCH_ALPHA_SYMBOL)
            mLastComposedWord.deactivate();
        if (Constants.CODE_DELETE != code) {
            mEnteredText = null;
        }
        mConnection.endBatchEdit();
    }

    public void onStartBatchInput(final SettingsValues settingsValues,
            // TODO: remove these arguments
            final KeyboardSwitcher keyboardSwitcher, final LatinIME.UIHandler handler) {
        mInputLogicHandler.onStartBatchInput();
        handler.showGesturePreviewAndSuggestionStrip(
                SuggestedWords.EMPTY, false /* dismissGestureFloatingPreviewText */);
        handler.cancelUpdateSuggestionStrip();
        mConnection.beginBatchEdit();
        if (mWordComposer.isComposingWord()) {
            if (settingsValues.mIsInternal) {
                if (mWordComposer.isBatchMode()) {
                    LatinImeLoggerUtils.onAutoCorrection("", mWordComposer.getTypedWord(), " ",
                            mWordComposer);
                }
            }
            final int wordComposerSize = mWordComposer.size();
            // Since isComposingWord() is true, the size is at least 1.
            if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
                // If we are in the middle of a recorrection, we need to commit the recorrection
                // first so that we can insert the batch input at the current cursor position.
                resetEntireInputState(settingsValues, mConnection.getExpectedSelectionStart(),
                        mConnection.getExpectedSelectionEnd(), true /* clearSuggestionStrip */);
            } else if (wordComposerSize <= 1) {
                // We auto-correct the previous (typed, not gestured) string iff it's one character
                // long. The reason for this is, even in the middle of gesture typing, you'll still
                // tap one-letter words and you want them auto-corrected (typically, "i" in English
                // should become "I"). However for any longer word, we assume that the reason for
                // tapping probably is that the word you intend to type is not in the dictionary,
                // so we do not attempt to correct, on the assumption that if that was a dictionary
                // word, the user would probably have gestured instead.
                commitCurrentAutoCorrection(settingsValues, LastComposedWord.NOT_A_SEPARATOR,
                        handler);
            } else {
                commitTyped(settingsValues, LastComposedWord.NOT_A_SEPARATOR);
            }
        }
        final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
        if (Character.isLetterOrDigit(codePointBeforeCursor)
                || settingsValues.isUsuallyFollowedBySpace(codePointBeforeCursor)) {
            final boolean autoShiftHasBeenOverriden = keyboardSwitcher.getKeyboardShiftMode() !=
                    getCurrentAutoCapsState(settingsValues);
            mSpaceState = SpaceState.PHANTOM;
            if (!autoShiftHasBeenOverriden) {
                // When we change the space state, we need to update the shift state of the
                // keyboard unless it has been overridden manually. This is happening for example
                // after typing some letters and a period, then gesturing; the keyboard is not in
                // caps mode yet, but since a gesture is starting, it should go in caps mode,
                // unless the user explictly said it should not.
                keyboardSwitcher.updateShiftState();
            }
        }
        mConnection.endBatchEdit();
        mWordComposer.setCapitalizedModeAndPreviousWordAtStartComposingTime(
                getActualCapsMode(settingsValues, keyboardSwitcher.getKeyboardShiftMode()),
                // Prev word is 1st word before cursor
                getNthPreviousWordForSuggestion(
                        settingsValues.mSpacingAndPunctuations, 1 /* nthPreviousWord */));
    }

    /* The sequence number member is only used in onUpdateBatchInput. It is increased each time
     * auto-commit happens. The reason we need this is, when auto-commit happens we trim the
     * input pointers that are held in a singleton, and to know how much to trim we rely on the
     * results of the suggestion process that is held in mSuggestedWords.
     * However, the suggestion process is asynchronous, and sometimes we may enter the
     * onUpdateBatchInput method twice without having recomputed suggestions yet, or having
     * received new suggestions generated from not-yet-trimmed input pointers. In this case, the
     * mIndexOfTouchPointOfSecondWords member will be out of date, and we must not use it lest we
     * remove an unrelated number of pointers (possibly even more than are left in the input
     * pointers, leading to a crash).
     * To avoid that, we increase the sequence number each time we auto-commit and trim the
     * input pointers, and we do not use any suggested words that have been generated with an
     * earlier sequence number.
     */
    private int mAutoCommitSequenceNumber = 1;
    public void onUpdateBatchInput(final SettingsValues settingsValues,
            final InputPointers batchPointers,
            // TODO: remove these arguments
            final KeyboardSwitcher keyboardSwitcher) {
        if (settingsValues.mPhraseGestureEnabled) {
            final SuggestedWordInfo candidate = mSuggestedWords.getAutoCommitCandidate();
            // If these suggested words have been generated with out of date input pointers, then
            // we skip auto-commit (see comments above on the mSequenceNumber member).
            if (null != candidate
                    && mSuggestedWords.mSequenceNumber >= mAutoCommitSequenceNumber) {
                if (candidate.mSourceDict.shouldAutoCommit(candidate)) {
                    final String[] commitParts = candidate.mWord.split(" ", 2);
                    batchPointers.shift(candidate.mIndexOfTouchPointOfSecondWord);
                    promotePhantomSpace(settingsValues);
                    mConnection.commitText(commitParts[0], 0);
                    mSpaceState = SpaceState.PHANTOM;
                    keyboardSwitcher.updateShiftState();
                    mWordComposer.setCapitalizedModeAndPreviousWordAtStartComposingTime(
                            getActualCapsMode(settingsValues,
                                    keyboardSwitcher.getKeyboardShiftMode()), commitParts[0]);
                    ++mAutoCommitSequenceNumber;
                }
            }
        }
        mInputLogicHandler.onUpdateBatchInput(batchPointers, mAutoCommitSequenceNumber);
    }

    public void onEndBatchInput(final SettingsValues settingValues,
            final InputPointers batchPointers) {
        mInputLogicHandler.onEndBatchInput(batchPointers, mAutoCommitSequenceNumber);
    }

    // TODO: remove this argument
    public void onCancelBatchInput(final LatinIME.UIHandler handler) {
        mInputLogicHandler.onCancelBatchInput();
        handler.showGesturePreviewAndSuggestionStrip(
                SuggestedWords.EMPTY, true /* dismissGestureFloatingPreviewText */);
    }

    // TODO: on the long term, this method should become private, but it will be difficult.
    // Especially, how do we deal with InputMethodService.onDisplayCompletions?
    public void setSuggestedWords(final SuggestedWords suggestedWords) {
        mSuggestedWords = suggestedWords;
        final boolean newAutoCorrectionIndicator = suggestedWords.mWillAutoCorrect;
        // Put a blue underline to a word in TextView which will be auto-corrected.
        if (mIsAutoCorrectionIndicatorOn != newAutoCorrectionIndicator
                && mWordComposer.isComposingWord()) {
            mIsAutoCorrectionIndicatorOn = newAutoCorrectionIndicator;
            final CharSequence textWithUnderline =
                    getTextWithUnderline(mWordComposer.getTypedWord());
            // TODO: when called from an updateSuggestionStrip() call that results from a posted
            // message, this is called outside any batch edit. Potentially, this may result in some
            // janky flickering of the screen, although the display speed makes it unlikely in
            // the practice.
            mConnection.setComposingText(textWithUnderline, 1);
        }
    }

    /**
     * Handle inputting a code point to the editor.
     *
     * Non-special keys are those that generate a single code point.
     * This includes all letters, digits, punctuation, separators, emoji. It excludes keys that
     * manage keyboard-related stuff like shift, language switch, settings, layout switch, or
     * any key that results in multiple code points like the ".com" key.
     *
     * @param settingsValues The current settings values.
     * @param codePoint the code point associated with the key.
     * @param x the x-coordinate of the key press, or Contants.NOT_A_COORDINATE if not applicable.
     * @param y the y-coordinate of the key press, or Contants.NOT_A_COORDINATE if not applicable.
     * @param spaceState the space state at start of the batch input.
     * @return whether this caused an auto-correction to happen.
     */
    private boolean handleNonSpecialCharacter(final SettingsValues settingsValues,
            final int codePoint, final int x, final int y, final int spaceState,
            // TODO: remove these arguments
            final KeyboardSwitcher keyboardSwitcher, final LatinIME.UIHandler handler) {
        mSpaceState = SpaceState.NONE;
        final boolean didAutoCorrect;
        if (settingsValues.isWordSeparator(codePoint)
                || Character.getType(codePoint) == Character.OTHER_SYMBOL) {
            didAutoCorrect = handleSeparator(settingsValues, codePoint,
                    Constants.SUGGESTION_STRIP_COORDINATE == x, spaceState, keyboardSwitcher,
                    handler);
            if (settingsValues.mIsInternal) {
                LatinImeLoggerUtils.onSeparator((char)codePoint, x, y);
            }
        } else {
            didAutoCorrect = false;
            if (SpaceState.PHANTOM == spaceState) {
                if (settingsValues.mIsInternal) {
                    if (mWordComposer.isComposingWord() && mWordComposer.isBatchMode()) {
                        LatinImeLoggerUtils.onAutoCorrection("", mWordComposer.getTypedWord(), " ",
                                mWordComposer);
                    }
                }
                if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
                    // If we are in the middle of a recorrection, we need to commit the recorrection
                    // first so that we can insert the character at the current cursor position.
                    resetEntireInputState(settingsValues, mConnection.getExpectedSelectionStart(),
                            mConnection.getExpectedSelectionEnd(), true /* clearSuggestionStrip */);
                } else {
                    commitTyped(settingsValues, LastComposedWord.NOT_A_SEPARATOR);
                }
            }
            handleNonSeparator(settingsValues, codePoint, x, y, spaceState,
                    keyboardSwitcher, handler);
        }
        return didAutoCorrect;
    }

    /**
     * Handle a non-separator.
     * @param settingsValues The current settings values.
     * @param codePoint the code point associated with the key.
     * @param x the x-coordinate of the key press, or Contants.NOT_A_COORDINATE if not applicable.
     * @param y the y-coordinate of the key press, or Contants.NOT_A_COORDINATE if not applicable.
     * @param spaceState the space state at start of the batch input.
     */
    private void handleNonSeparator(final SettingsValues settingsValues,
            final int codePoint, final int x, final int y, final int spaceState,
            // TODO: Remove these arguments
            final KeyboardSwitcher keyboardSwitcher, final LatinIME.UIHandler handler) {
        // TODO: refactor this method to stop flipping isComposingWord around all the time, and
        // make it shorter (possibly cut into several pieces). Also factor handleNonSpecialCharacter
        // which has the same name as other handle* methods but is not the same.
        boolean isComposingWord = mWordComposer.isComposingWord();

        // TODO: remove isWordConnector() and use isUsuallyFollowedBySpace() instead.
        // See onStartBatchInput() to see how to do it.
        if (SpaceState.PHANTOM == spaceState && !settingsValues.isWordConnector(codePoint)) {
            if (isComposingWord) {
                // Sanity check
                throw new RuntimeException("Should not be composing here");
            }
            promotePhantomSpace(settingsValues);
        }

        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can insert the character at the current cursor position.
            resetEntireInputState(settingsValues, mConnection.getExpectedSelectionStart(),
                    mConnection.getExpectedSelectionEnd(), true /* clearSuggestionStrip */);
            isComposingWord = false;
        }
        // We want to find out whether to start composing a new word with this character. If so,
        // we need to reset the composing state and switch isComposingWord. The order of the
        // tests is important for good performance.
        // We only start composing if we're not already composing.
        if (!isComposingWord
        // We only start composing if this is a word code point. Essentially that means it's a
        // a letter or a word connector.
                && settingsValues.isWordCodePoint(codePoint)
        // We never go into composing state if suggestions are not requested.
                && settingsValues.isSuggestionsRequested() &&
        // In languages with spaces, we only start composing a word when we are not already
        // touching a word. In languages without spaces, the above conditions are sufficient.
                (!mConnection.isCursorTouchingWord(settingsValues.mSpacingAndPunctuations)
                        || !settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces)) {
            // Reset entirely the composing state anyway, then start composing a new word unless
            // the character is a single quote or a dash. The idea here is, single quote and dash
            // are not separators and they should be treated as normal characters, except in the
            // first position where they should not start composing a word.
            isComposingWord = (Constants.CODE_SINGLE_QUOTE != codePoint
                    && Constants.CODE_DASH != codePoint);
            // Here we don't need to reset the last composed word. It will be reset
            // when we commit this one, if we ever do; if on the other hand we backspace
            // it entirely and resume suggestions on the previous word, we'd like to still
            // have touch coordinates for it.
            resetComposingState(false /* alsoResetLastComposedWord */);
        }
        if (isComposingWord) {
            mWordComposer.add(codePoint, x, y);
            // If it's the first letter, make note of auto-caps state
            if (mWordComposer.size() == 1) {
                // We pass 1 to getPreviousWordForSuggestion because we were not composing a word
                // yet, so the word we want is the 1st word before the cursor.
                mWordComposer.setCapitalizedModeAndPreviousWordAtStartComposingTime(
                        getActualCapsMode(settingsValues, keyboardSwitcher.getKeyboardShiftMode()),
                        getNthPreviousWordForSuggestion(
                                settingsValues.mSpacingAndPunctuations, 1 /* nthPreviousWord */));
            }
            mConnection.setComposingText(getTextWithUnderline(
                    mWordComposer.getTypedWord()), 1);
        } else {
            final boolean swapWeakSpace = maybeStripSpace(settingsValues,
                    codePoint, spaceState, Constants.SUGGESTION_STRIP_COORDINATE == x);

            sendKeyCodePoint(settingsValues, codePoint);

            if (swapWeakSpace) {
                swapSwapperAndSpace(keyboardSwitcher);
                mSpaceState = SpaceState.WEAK;
            }
            // In case the "add to dictionary" hint was still displayed.
            mSuggestionStripViewAccessor.dismissAddToDictionaryHint();
        }
        handler.postUpdateSuggestionStrip();
        if (settingsValues.mIsInternal) {
            LatinImeLoggerUtils.onNonSeparator((char)codePoint, x, y);
        }
    }

    /**
     * Handle input of a separator code point.
     * @param settingsValues The current settings values.
     * @param codePoint the code point associated with the key.
     * @param isFromSuggestionStrip whether this code point comes from the suggestion strip.
     * @param spaceState the space state at start of the batch input.
     * @return whether this caused an auto-correction to happen.
     */
    private boolean handleSeparator(final SettingsValues settingsValues,
            final int codePoint, final boolean isFromSuggestionStrip, final int spaceState,
            // TODO: remove these arguments
            final KeyboardSwitcher keyboardSwitcher, final LatinIME.UIHandler handler) {
        boolean didAutoCorrect = false;
        // We avoid sending spaces in languages without spaces if we were composing.
        final boolean shouldAvoidSendingCode = Constants.CODE_SPACE == codePoint
                && !settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                && mWordComposer.isComposingWord();
        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can insert the separator at the current cursor position.
            resetEntireInputState(settingsValues, mConnection.getExpectedSelectionStart(),
                    mConnection.getExpectedSelectionEnd(), true /* clearSuggestionStrip */);
        }
        // isComposingWord() may have changed since we stored wasComposing
        if (mWordComposer.isComposingWord()) {
            if (settingsValues.mCorrectionEnabled) {
                final String separator = shouldAvoidSendingCode ? LastComposedWord.NOT_A_SEPARATOR
                        : StringUtils.newSingleCodePointString(codePoint);
                commitCurrentAutoCorrection(settingsValues, separator, handler);
                didAutoCorrect = true;
            } else {
                commitTyped(settingsValues, StringUtils.newSingleCodePointString(codePoint));
            }
        }

        final boolean swapWeakSpace = maybeStripSpace(settingsValues, codePoint, spaceState,
                isFromSuggestionStrip);

        final boolean isInsideDoubleQuoteOrAfterDigit = Constants.CODE_DOUBLE_QUOTE == codePoint
                && mConnection.isInsideDoubleQuoteOrAfterDigit();

        final boolean needsPrecedingSpace;
        if (SpaceState.PHANTOM != spaceState) {
            needsPrecedingSpace = false;
        } else if (Constants.CODE_DOUBLE_QUOTE == codePoint) {
            // Double quotes behave like they are usually preceded by space iff we are
            // not inside a double quote or after a digit.
            needsPrecedingSpace = !isInsideDoubleQuoteOrAfterDigit;
        } else {
            needsPrecedingSpace = settingsValues.isUsuallyPrecededBySpace(codePoint);
        }

        if (needsPrecedingSpace) {
            promotePhantomSpace(settingsValues);
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_handleSeparator(codePoint, mWordComposer.isComposingWord());
        }

        if (!shouldAvoidSendingCode) {
            sendKeyCodePoint(settingsValues, codePoint);
        }

        if (Constants.CODE_SPACE == codePoint) {
            if (settingsValues.isSuggestionsRequested()) {
                if (maybeDoubleSpacePeriod(settingsValues, handler)) {
                    keyboardSwitcher.updateShiftState();
                    mSpaceState = SpaceState.DOUBLE;
                } else if (!mSuggestedWords.isPunctuationSuggestions()) {
                    mSpaceState = SpaceState.WEAK;
                }
            }

            handler.startDoubleSpacePeriodTimer();
            handler.postUpdateSuggestionStrip();
        } else {
            if (swapWeakSpace) {
                swapSwapperAndSpace(keyboardSwitcher);
                mSpaceState = SpaceState.SWAP_PUNCTUATION;
            } else if ((SpaceState.PHANTOM == spaceState
                    && settingsValues.isUsuallyFollowedBySpace(codePoint))
                    || (Constants.CODE_DOUBLE_QUOTE == codePoint
                            && isInsideDoubleQuoteOrAfterDigit)) {
                // If we are in phantom space state, and the user presses a separator, we want to
                // stay in phantom space state so that the next keypress has a chance to add the
                // space. For example, if I type "Good dat", pick "day" from the suggestion strip
                // then insert a comma and go on to typing the next word, I want the space to be
                // inserted automatically before the next word, the same way it is when I don't
                // input the comma. A double quote behaves like it's usually followed by space if
                // we're inside a double quote.
                // The case is a little different if the separator is a space stripper. Such a
                // separator does not normally need a space on the right (that's the difference
                // between swappers and strippers), so we should not stay in phantom space state if
                // the separator is a stripper. Hence the additional test above.
                mSpaceState = SpaceState.PHANTOM;
            }

            // Set punctuation right away. onUpdateSelection will fire but tests whether it is
            // already displayed or not, so it's okay.
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
        }

        keyboardSwitcher.updateShiftState();
        return didAutoCorrect;
    }

    /**
     * Handle a press on the backspace key.
     * @param settingsValues The current settings values.
     * @param spaceState The space state at start of this batch edit.
     */
    private void handleBackspace(final SettingsValues settingsValues, final int spaceState,
            // TODO: remove these arguments
            final LatinIME.UIHandler handler, final KeyboardSwitcher keyboardSwitcher) {
        mSpaceState = SpaceState.NONE;
        final int deleteCountAtStart = mDeleteCount;
        mDeleteCount++;

        // In many cases, we may have to put the keyboard in auto-shift state again. However
        // we want to wait a few milliseconds before doing it to avoid the keyboard flashing
        // during key repeat.
        handler.postUpdateShiftState();

        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can remove the character at the current cursor position.
            resetEntireInputState(settingsValues, mConnection.getExpectedSelectionStart(),
                    mConnection.getExpectedSelectionEnd(), true /* clearSuggestionStrip */);
            // When we exit this if-clause, mWordComposer.isComposingWord() will return false.
        }
        if (mWordComposer.isComposingWord()) {
            if (mWordComposer.isBatchMode()) {
                if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                    final String word = mWordComposer.getTypedWord();
                    ResearchLogger.latinIME_handleBackspace_batch(word, 1);
                }
                final String rejectedSuggestion = mWordComposer.getTypedWord();
                mWordComposer.reset();
                mWordComposer.setRejectedBatchModeSuggestion(rejectedSuggestion);
            } else {
                mWordComposer.deleteLast();
            }
            mConnection.setComposingText(getTextWithUnderline(mWordComposer.getTypedWord()), 1);
            handler.postUpdateSuggestionStrip();
            if (!mWordComposer.isComposingWord()) {
                // If we just removed the last character, auto-caps mode may have changed so we
                // need to re-evaluate.
                keyboardSwitcher.updateShiftState();
            }
        } else {
            if (mLastComposedWord.canRevertCommit()) {
                if (settingsValues.mIsInternal) {
                    LatinImeLoggerUtils.onAutoCorrectionCancellation();
                }
                revertCommit(settingsValues, handler);
                return;
            }
            if (mEnteredText != null && mConnection.sameAsTextBeforeCursor(mEnteredText)) {
                // Cancel multi-character input: remove the text we just entered.
                // This is triggered on backspace after a key that inputs multiple characters,
                // like the smiley key or the .com key.
                mConnection.deleteSurroundingText(mEnteredText.length(), 0);
                if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                    ResearchLogger.latinIME_handleBackspace_cancelTextInput(mEnteredText);
                }
                mEnteredText = null;
                // If we have mEnteredText, then we know that mHasUncommittedTypedChars == false.
                // In addition we know that spaceState is false, and that we should not be
                // reverting any autocorrect at this point. So we can safely return.
                return;
            }
            if (SpaceState.DOUBLE == spaceState) {
                handler.cancelDoubleSpacePeriodTimer();
                if (mConnection.revertDoubleSpacePeriod()) {
                    // No need to reset mSpaceState, it has already be done (that's why we
                    // receive it as a parameter)
                    return;
                }
            } else if (SpaceState.SWAP_PUNCTUATION == spaceState) {
                if (mConnection.revertSwapPunctuation()) {
                    // Likewise
                    return;
                }
            }

            // No cancelling of commit/double space/swap: we have a regular backspace.
            // We should backspace one char and restart suggestion if at the end of a word.
            if (mConnection.hasSelection()) {
                // If there is a selection, remove it.
                final int numCharsDeleted = mConnection.getExpectedSelectionEnd()
                        - mConnection.getExpectedSelectionStart();
                mConnection.setSelection(mConnection.getExpectedSelectionEnd(),
                        mConnection.getExpectedSelectionEnd());
                mConnection.deleteSurroundingText(numCharsDeleted, 0);
                if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                    ResearchLogger.latinIME_handleBackspace(numCharsDeleted,
                            false /* shouldUncommitLogUnit */);
                }
            } else {
                // There is no selection, just delete one character.
                if (Constants.NOT_A_CURSOR_POSITION == mConnection.getExpectedSelectionEnd()) {
                    // This should never happen.
                    Log.e(TAG, "Backspace when we don't know the selection position");
                }
                if (settingsValues.isBeforeJellyBean() ||
                        settingsValues.mInputAttributes.isTypeNull()) {
                    // There are two possible reasons to send a key event: either the field has
                    // type TYPE_NULL, in which case the keyboard should send events, or we are
                    // running in backward compatibility mode. Before Jelly bean, the keyboard
                    // would simulate a hardware keyboard event on pressing enter or delete. This
                    // is bad for many reasons (there are race conditions with commits) but some
                    // applications are relying on this behavior so we continue to support it for
                    // older apps, so we retain this behavior if the app has target SDK < JellyBean.
                    sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL);
                    if (mDeleteCount > Constants.DELETE_ACCELERATE_AT) {
                        sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL);
                    }
                } else {
                    final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
                    if (codePointBeforeCursor == Constants.NOT_A_CODE) {
                        // HACK for backward compatibility with broken apps that haven't realized
                        // yet that hardware keyboards are not the only way of inputting text.
                        // Nothing to delete before the cursor. We should not do anything, but many
                        // broken apps expect something to happen in this case so that they can
                        // catch it and have their broken interface react. If you need the keyboard
                        // to do this, you're doing it wrong -- please fix your app.
                        mConnection.deleteSurroundingText(1, 0);
                        return;
                    }
                    final int lengthToDelete =
                            Character.isSupplementaryCodePoint(codePointBeforeCursor) ? 2 : 1;
                    mConnection.deleteSurroundingText(lengthToDelete, 0);
                    if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                        ResearchLogger.latinIME_handleBackspace(lengthToDelete,
                                true /* shouldUncommitLogUnit */);
                    }
                    if (mDeleteCount > Constants.DELETE_ACCELERATE_AT) {
                        final int codePointBeforeCursorToDeleteAgain =
                                mConnection.getCodePointBeforeCursor();
                        if (codePointBeforeCursorToDeleteAgain != Constants.NOT_A_CODE) {
                            final int lengthToDeleteAgain = Character.isSupplementaryCodePoint(
                                    codePointBeforeCursorToDeleteAgain) ? 2 : 1;
                            mConnection.deleteSurroundingText(lengthToDeleteAgain, 0);
                            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                                ResearchLogger.latinIME_handleBackspace(lengthToDeleteAgain,
                                        true /* shouldUncommitLogUnit */);
                            }
                        }
                    }
                }
            }
            if (settingsValues.isSuggestionStripVisible()
                    && settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                    && !mConnection.isCursorFollowedByWordCharacter(
                            settingsValues.mSpacingAndPunctuations)) {
                restartSuggestionsOnWordTouchedByCursor(settingsValues,
                        true /* includeResumedWordInSuggestions */);
            }
            // We just removed at least one character. We need to update the auto-caps state.
            keyboardSwitcher.updateShiftState();
        }
    }

    /**
     * Handle a press on the language switch key (the "globe key")
     */
    private void handleLanguageSwitchKey() {
        mLatinIME.switchToNextSubtype();
    }

    /**
     * Swap a space with a space-swapping punctuation sign.
     *
     * This method will check that there are two characters before the cursor and that the first
     * one is a space before it does the actual swapping.
     */
    // TODO: Remove this argument
    private void swapSwapperAndSpace(final KeyboardSwitcher keyboardSwitcher) {
        final CharSequence lastTwo = mConnection.getTextBeforeCursor(2, 0);
        // It is guaranteed lastTwo.charAt(1) is a swapper - else this method is not called.
        if (lastTwo != null && lastTwo.length() == 2 && lastTwo.charAt(0) == Constants.CODE_SPACE) {
            mConnection.deleteSurroundingText(2, 0);
            final String text = lastTwo.charAt(1) + " ";
            mConnection.commitText(text, 1);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_swapSwapperAndSpace(lastTwo, text);
            }
            keyboardSwitcher.updateShiftState();
        }
    }

    /*
     * Strip a trailing space if necessary and returns whether it's a swap weak space situation.
     * @param settingsValues The current settings values.
     * @param codePoint The code point that is about to be inserted.
     * @param spaceState The space state at start of this batch edit.
     * @param isFromSuggestionStrip Whether this code point is coming from the suggestion strip.
     * @return whether we should swap the space instead of removing it.
     */
    private boolean maybeStripSpace(final SettingsValues settingsValues,
            final int code, final int spaceState, final boolean isFromSuggestionStrip) {
        if (Constants.CODE_ENTER == code && SpaceState.SWAP_PUNCTUATION == spaceState) {
            mConnection.removeTrailingSpace();
            return false;
        }
        if ((SpaceState.WEAK == spaceState || SpaceState.SWAP_PUNCTUATION == spaceState)
                && isFromSuggestionStrip) {
            if (settingsValues.isUsuallyPrecededBySpace(code)) return false;
            if (settingsValues.isUsuallyFollowedBySpace(code)) return true;
            mConnection.removeTrailingSpace();
        }
        return false;
    }

    /**
     * Apply the double-space-to-period transformation if applicable.
     *
     * The double-space-to-period transformation means that we replace two spaces with a
     * period-space sequence of characters. This typically happens when the user presses space
     * twice in a row quickly.
     * This method will check that the double-space-to-period is active in settings, that the
     * two spaces have been input close enough together, and that the previous character allows
     * for the transformation to take place. If all of these conditions are fulfilled, this
     * method applies the transformation and returns true. Otherwise, it does nothing and
     * returns false.
     *
     * @param settingsValues the current values of the settings.
     * @return true if we applied the double-space-to-period transformation, false otherwise.
     */
    private boolean maybeDoubleSpacePeriod(final SettingsValues settingsValues,
            // TODO: remove this argument
            final LatinIME.UIHandler handler) {
        if (!settingsValues.mUseDoubleSpacePeriod) return false;
        if (!handler.isAcceptingDoubleSpacePeriod()) return false;
        // We only do this when we see two spaces and an accepted code point before the cursor.
        // The code point may be a surrogate pair but the two spaces may not, so we need 4 chars.
        final CharSequence lastThree = mConnection.getTextBeforeCursor(4, 0);
        if (null == lastThree) return false;
        final int length = lastThree.length();
        if (length < 3) return false;
        if (lastThree.charAt(length - 1) != Constants.CODE_SPACE) return false;
        if (lastThree.charAt(length - 2) != Constants.CODE_SPACE) return false;
        // We know there are spaces in pos -1 and -2, and we have at least three chars.
        // If we have only three chars, isSurrogatePairs can't return true as charAt(1) is a space,
        // so this is fine.
        final int firstCodePoint =
                Character.isSurrogatePair(lastThree.charAt(0), lastThree.charAt(1)) ?
                        Character.codePointAt(lastThree, 0) : lastThree.charAt(length - 3);
        if (canBeFollowedByDoubleSpacePeriod(firstCodePoint)) {
            handler.cancelDoubleSpacePeriodTimer();
            mConnection.deleteSurroundingText(2, 0);
            final String textToInsert =
                    settingsValues.mSpacingAndPunctuations.mSentenceSeparatorAndSpace;
            mConnection.commitText(textToInsert, 1);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_maybeDoubleSpacePeriod(textToInsert,
                        false /* isBatchMode */);
            }
            mWordComposer.discardPreviousWordForSuggestion();
            return true;
        }
        return false;
    }

    /**
     * Returns whether this code point can be followed by the double-space-to-period transformation.
     *
     * See #maybeDoubleSpaceToPeriod for details.
     * Generally, most word characters can be followed by the double-space-to-period transformation,
     * while most punctuation can't. Some punctuation however does allow for this to take place
     * after them, like the closing parenthesis for example.
     *
     * @param codePoint the code point after which we may want to apply the transformation
     * @return whether it's fine to apply the transformation after this code point.
     */
    private static boolean canBeFollowedByDoubleSpacePeriod(final int codePoint) {
        // TODO: This should probably be a blacklist rather than a whitelist.
        // TODO: This should probably be language-dependant...
        return Character.isLetterOrDigit(codePoint)
                || codePoint == Constants.CODE_SINGLE_QUOTE
                || codePoint == Constants.CODE_DOUBLE_QUOTE
                || codePoint == Constants.CODE_CLOSING_PARENTHESIS
                || codePoint == Constants.CODE_CLOSING_SQUARE_BRACKET
                || codePoint == Constants.CODE_CLOSING_CURLY_BRACKET
                || codePoint == Constants.CODE_CLOSING_ANGLE_BRACKET
                || codePoint == Constants.CODE_PLUS
                || codePoint == Constants.CODE_PERCENT
                || Character.getType(codePoint) == Character.OTHER_SYMBOL;
    }

    /**
     * Performs a recapitalization event.
     * @param settingsValues The current settings values.
     */
    private void performRecapitalization(final SettingsValues settingsValues) {
        if (!mConnection.hasSelection()) {
            return; // No selection
        }
        // If we have a recapitalize in progress, use it; otherwise, create a new one.
        if (!mRecapitalizeStatus.isActive()
                || !mRecapitalizeStatus.isSetAt(mConnection.getExpectedSelectionStart(),
                        mConnection.getExpectedSelectionEnd())) {
            final CharSequence selectedText =
                    mConnection.getSelectedText(0 /* flags, 0 for no styles */);
            if (TextUtils.isEmpty(selectedText)) return; // Race condition with the input connection
            mRecapitalizeStatus.initialize(mConnection.getExpectedSelectionStart(),
                    mConnection.getExpectedSelectionEnd(), selectedText.toString(),
                    settingsValues.mLocale,
                    settingsValues.mSpacingAndPunctuations.mSortedWordSeparators);
            // We trim leading and trailing whitespace.
            mRecapitalizeStatus.trim();
        }
        mConnection.finishComposingText();
        mRecapitalizeStatus.rotate();
        final int numCharsDeleted = mConnection.getExpectedSelectionEnd()
                - mConnection.getExpectedSelectionStart();
        mConnection.setSelection(mConnection.getExpectedSelectionEnd(),
                mConnection.getExpectedSelectionEnd());
        mConnection.deleteSurroundingText(numCharsDeleted, 0);
        mConnection.commitText(mRecapitalizeStatus.getRecapitalizedString(), 0);
        mConnection.setSelection(mRecapitalizeStatus.getNewCursorStart(),
                mRecapitalizeStatus.getNewCursorEnd());
    }

    private void performAdditionToUserHistoryDictionary(final SettingsValues settingsValues,
            final String suggestion, final String prevWord) {
        // If correction is not enabled, we don't add words to the user history dictionary.
        // That's to avoid unintended additions in some sensitive fields, or fields that
        // expect to receive non-words.
        if (!settingsValues.mCorrectionEnabled) return;

        if (TextUtils.isEmpty(suggestion)) return;
        final Suggest suggest = mSuggest;
        if (suggest == null) return;

        final boolean wasAutoCapitalized =
                mWordComposer.wasAutoCapitalized() && !mWordComposer.isMostlyCaps();
        final int timeStampInSeconds = (int)TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis());
        suggest.mDictionaryFacilitator.addToUserHistory(suggestion, wasAutoCapitalized, prevWord,
                timeStampInSeconds);
    }

    public void performUpdateSuggestionStripSync(final SettingsValues settingsValues,
            // TODO: Remove this argument
            final LatinIME.UIHandler handler) {
        handler.cancelUpdateSuggestionStrip();

        // Check if we have a suggestion engine attached.
        if (mSuggest == null || !settingsValues.isSuggestionsRequested()) {
            if (mWordComposer.isComposingWord()) {
                Log.w(TAG, "Called updateSuggestionsOrPredictions but suggestions were not "
                        + "requested!");
            }
            return;
        }

        if (!mWordComposer.isComposingWord() && !settingsValues.mBigramPredictionEnabled) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
            return;
        }

        final AsyncResultHolder<SuggestedWords> holder = new AsyncResultHolder<SuggestedWords>();
        mInputLogicHandler.getSuggestedWords(Suggest.SESSION_TYPING,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER, new OnGetSuggestedWordsCallback() {
                    @Override
                    public void onGetSuggestedWords(final SuggestedWords suggestedWords) {
                        final SuggestedWords suggestedWordsWithMaybeOlderSuggestions =
                                mLatinIME.maybeRetrieveOlderSuggestions(
                                        mWordComposer.getTypedWord(), suggestedWords,
                                        mSuggestedWords);
                        holder.set(suggestedWordsWithMaybeOlderSuggestions);
                    }
                }
        );

        // This line may cause the current thread to wait.
        final SuggestedWords suggestedWords = holder.get(null,
                Constants.GET_SUGGESTED_WORDS_TIMEOUT);
        if (suggestedWords != null) {
            mSuggestionStripViewAccessor.showSuggestionStrip(suggestedWords);
        }
    }

    /**
     * Check if the cursor is touching a word. If so, restart suggestions on this word, else
     * do nothing.
     *
     * @param settingsValues the current values of the settings.
     * @param includeResumedWordInSuggestions whether to include the word on which we resume
     *   suggestions in the suggestion list.
     */
    // TODO: make this private.
    public void restartSuggestionsOnWordTouchedByCursor(final SettingsValues settingsValues,
            final boolean includeResumedWordInSuggestions) {
        // HACK: We may want to special-case some apps that exhibit bad behavior in case of
        // recorrection. This is a temporary, stopgap measure that will be removed later.
        // TODO: remove this.
        if (settingsValues.isBrokenByRecorrection()
        // Recorrection is not supported in languages without spaces because we don't know
        // how to segment them yet.
                || !settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
        // If no suggestions are requested, don't try restarting suggestions.
                || !settingsValues.isSuggestionsRequested()
        // If the cursor is not touching a word, or if there is a selection, return right away.
                || mConnection.hasSelection()
        // If we don't know the cursor location, return.
                || mConnection.getExpectedSelectionStart() < 0) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
            return;
        }
        final int expectedCursorPosition = mConnection.getExpectedSelectionStart();
        if (!mConnection.isCursorTouchingWord(settingsValues.mSpacingAndPunctuations)) {
            // Show predictions.
            mWordComposer.setCapitalizedModeAndPreviousWordAtStartComposingTime(
                    WordComposer.CAPS_MODE_OFF,
                    getNthPreviousWordForSuggestion(settingsValues.mSpacingAndPunctuations, 1));
            mLatinIME.mHandler.postUpdateSuggestionStrip();
            return;
        }
        final TextRange range = mConnection.getWordRangeAtCursor(
                settingsValues.mSpacingAndPunctuations.mSortedWordSeparators,
                0 /* additionalPrecedingWordsCount */);
        if (null == range) return; // Happens if we don't have an input connection at all
        if (range.length() <= 0) return; // Race condition. No text to resume on, so bail out.
        // If for some strange reason (editor bug or so) we measure the text before the cursor as
        // longer than what the entire text is supposed to be, the safe thing to do is bail out.
        if (range.mHasUrlSpans) return; // If there are links, we don't resume suggestions. Making
        // edits to a linkified text through batch commands would ruin the URL spans, and unless
        // we take very complicated steps to preserve the whole link, we can't do things right so
        // we just do not resume because it's safer.
        final int numberOfCharsInWordBeforeCursor = range.getNumberOfCharsInWordBeforeCursor();
        if (numberOfCharsInWordBeforeCursor > expectedCursorPosition) return;
        final ArrayList<SuggestedWordInfo> suggestions = CollectionUtils.newArrayList();
        final String typedWord = range.mWord.toString();
        if (includeResumedWordInSuggestions) {
            suggestions.add(new SuggestedWordInfo(typedWord,
                    SuggestedWords.MAX_SUGGESTIONS + 1,
                    SuggestedWordInfo.KIND_TYPED, Dictionary.DICTIONARY_USER_TYPED,
                    SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                    SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
        }
        if (!isResumableWord(settingsValues, typedWord)) return;
        int i = 0;
        for (final SuggestionSpan span : range.getSuggestionSpansAtWord()) {
            for (final String s : span.getSuggestions()) {
                ++i;
                if (!TextUtils.equals(s, typedWord)) {
                    suggestions.add(new SuggestedWordInfo(s,
                            SuggestedWords.MAX_SUGGESTIONS - i,
                            SuggestedWordInfo.KIND_RESUMED, Dictionary.DICTIONARY_RESUMED,
                            SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                            SuggestedWordInfo.NOT_A_CONFIDENCE
                                    /* autoCommitFirstWordConfidence */));
                }
            }
        }
        final int[] codePoints = StringUtils.toCodePointArray(typedWord);
        mWordComposer.setComposingWord(codePoints,
                mLatinIME.getCoordinatesForCurrentKeyboard(codePoints),
                getNthPreviousWordForSuggestion(settingsValues.mSpacingAndPunctuations,
                        // We want the previous word for suggestion. If we have chars in the word
                        // before the cursor, then we want the word before that, hence 2; otherwise,
                        // we want the word immediately before the cursor, hence 1.
                        0 == numberOfCharsInWordBeforeCursor ? 1 : 2));
        mWordComposer.setCursorPositionWithinWord(
                typedWord.codePointCount(0, numberOfCharsInWordBeforeCursor));
        mConnection.setComposingRegion(expectedCursorPosition - numberOfCharsInWordBeforeCursor,
                expectedCursorPosition + range.getNumberOfCharsInWordAfterCursor());
        if (suggestions.isEmpty()) {
            // We come here if there weren't any suggestion spans on this word. We will try to
            // compute suggestions for it instead.
            mInputLogicHandler.getSuggestedWords(Suggest.SESSION_TYPING,
                    SuggestedWords.NOT_A_SEQUENCE_NUMBER, new OnGetSuggestedWordsCallback() {
                        @Override
                        public void onGetSuggestedWords(
                                final SuggestedWords suggestedWordsIncludingTypedWord) {
                            final SuggestedWords suggestedWords;
                            if (suggestedWordsIncludingTypedWord.size() > 1
                                    && !includeResumedWordInSuggestions) {
                                // We were able to compute new suggestions for this word.
                                // Remove the typed word, since we don't want to display it in this
                                // case. The #getSuggestedWordsExcludingTypedWord() method sets
                                // willAutoCorrect to false.
                                suggestedWords = suggestedWordsIncludingTypedWord
                                        .getSuggestedWordsExcludingTypedWord();
                            } else {
                                // No saved suggestions, and we were unable to compute any good one
                                // either. Rather than displaying an empty suggestion strip, we'll
                                // display the original word alone in the middle.
                                // Since there is only one word, willAutoCorrect is false.
                                suggestedWords = suggestedWordsIncludingTypedWord;
                            }
                            mIsAutoCorrectionIndicatorOn = false;
                            mLatinIME.mHandler.showSuggestionStrip(suggestedWords);
                        }});
        } else {
            // We found suggestion spans in the word. We'll create the SuggestedWords out of
            // them, and make willAutoCorrect false.
            final SuggestedWords suggestedWords = new SuggestedWords(suggestions,
                    null /* rawSuggestions */, typedWord,
                    true /* typedWordValid */, false /* willAutoCorrect */,
                    false /* isObsoleteSuggestions */, false /* isPrediction */,
                    SuggestedWords.NOT_A_SEQUENCE_NUMBER);
            mIsAutoCorrectionIndicatorOn = false;
            mLatinIME.mHandler.showSuggestionStrip(suggestedWords);
        }
    }

    /**
     * Reverts a previous commit with auto-correction.
     *
     * This is triggered upon pressing backspace just after a commit with auto-correction.
     *
     * @param settingsValues the current settings values.
     */
    private void revertCommit(final SettingsValues settingsValues,
            // TODO: remove this argument
            final LatinIME.UIHandler handler) {
        final String previousWord = mLastComposedWord.mPrevWord;
        final CharSequence originallyTypedWord = mLastComposedWord.mTypedWord;
        final CharSequence committedWord = mLastComposedWord.mCommittedWord;
        final String committedWordString = committedWord.toString();
        final int cancelLength = committedWord.length();
        // We want java chars, not codepoints for the following.
        final int separatorLength = mLastComposedWord.mSeparatorString.length();
        // TODO: should we check our saved separator against the actual contents of the text view?
        final int deleteLength = cancelLength + separatorLength;
        if (LatinImeLogger.sDBG) {
            if (mWordComposer.isComposingWord()) {
                throw new RuntimeException("revertCommit, but we are composing a word");
            }
            final CharSequence wordBeforeCursor =
                    mConnection.getTextBeforeCursor(deleteLength, 0).subSequence(0, cancelLength);
            if (!TextUtils.equals(committedWord, wordBeforeCursor)) {
                throw new RuntimeException("revertCommit check failed: we thought we were "
                        + "reverting \"" + committedWord
                        + "\", but before the cursor we found \"" + wordBeforeCursor + "\"");
            }
        }
        mConnection.deleteSurroundingText(deleteLength, 0);
        if (!TextUtils.isEmpty(previousWord) && !TextUtils.isEmpty(committedWord)) {
            if (mSuggest != null) {
                mSuggest.mDictionaryFacilitator.cancelAddingUserHistory(
                        previousWord, committedWordString);
            }
        }
        final String stringToCommit = originallyTypedWord + mLastComposedWord.mSeparatorString;
        final SpannableString textToCommit = new SpannableString(stringToCommit);
        if (committedWord instanceof SpannableString) {
            final SpannableString committedWordWithSuggestionSpans = (SpannableString)committedWord;
            final Object[] spans = committedWordWithSuggestionSpans.getSpans(0,
                    committedWord.length(), Object.class);
            final int lastCharIndex = textToCommit.length() - 1;
            // We will collect all suggestions in the following array.
            final ArrayList<String> suggestions = CollectionUtils.newArrayList();
            // First, add the committed word to the list of suggestions.
            suggestions.add(committedWordString);
            for (final Object span : spans) {
                // If this is a suggestion span, we check that the locale is the right one, and
                // that the word is not the committed word. That should mostly be the case.
                // Given this, we add it to the list of suggestions, otherwise we discard it.
                if (span instanceof SuggestionSpan) {
                    final SuggestionSpan suggestionSpan = (SuggestionSpan)span;
                    if (!suggestionSpan.getLocale().equals(settingsValues.mLocale.toString())) {
                        continue;
                    }
                    for (final String suggestion : suggestionSpan.getSuggestions()) {
                        if (!suggestion.equals(committedWordString)) {
                            suggestions.add(suggestion);
                        }
                    }
                } else {
                    // If this is not a suggestion span, we just add it as is.
                    textToCommit.setSpan(span, 0 /* start */, lastCharIndex /* end */,
                            committedWordWithSuggestionSpans.getSpanFlags(span));
                }
            }
            // Add the suggestion list to the list of suggestions.
            textToCommit.setSpan(new SuggestionSpan(settingsValues.mLocale,
                    suggestions.toArray(new String[suggestions.size()]), 0 /* flags */),
                    0 /* start */, lastCharIndex /* end */, 0 /* flags */);
        }
        if (settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces) {
            // For languages with spaces, we revert to the typed string, but the cursor is still
            // after the separator so we don't resume suggestions. If the user wants to correct
            // the word, they have to press backspace again.
            mConnection.commitText(textToCommit, 1);
        } else {
            // For languages without spaces, we revert the typed string but the cursor is flush
            // with the typed word, so we need to resume suggestions right away.
            final int[] codePoints = StringUtils.toCodePointArray(stringToCommit);
            mWordComposer.setComposingWord(codePoints,
                    mLatinIME.getCoordinatesForCurrentKeyboard(codePoints), previousWord);
            mConnection.setComposingText(textToCommit, 1);
        }
        if (settingsValues.mIsInternal) {
            LatinImeLoggerUtils.onSeparator(mLastComposedWord.mSeparatorString,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_revertCommit(committedWord.toString(),
                    originallyTypedWord.toString(),
                    mWordComposer.isBatchMode(), mLastComposedWord.mSeparatorString);
        }
        // Don't restart suggestion yet. We'll restart if the user deletes the
        // separator.
        mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
        // We have a separator between the word and the cursor: we should show predictions.
        handler.postUpdateSuggestionStrip();
    }

    /**
     * Factor in auto-caps and manual caps and compute the current caps mode.
     * @param settingsValues the current settings values.
     * @param keyboardShiftMode the current shift mode of the keyboard. See
     *   KeyboardSwitcher#getKeyboardShiftMode() for possible values.
     * @return the actual caps mode the keyboard is in right now.
     */
    private int getActualCapsMode(final SettingsValues settingsValues,
            final int keyboardShiftMode) {
        if (keyboardShiftMode != WordComposer.CAPS_MODE_AUTO_SHIFTED) return keyboardShiftMode;
        final int auto = getCurrentAutoCapsState(settingsValues);
        if (0 != (auto & TextUtils.CAP_MODE_CHARACTERS)) {
            return WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED;
        }
        if (0 != auto) {
            return WordComposer.CAPS_MODE_AUTO_SHIFTED;
        }
        return WordComposer.CAPS_MODE_OFF;
    }

    /**
     * Gets the current auto-caps state, factoring in the space state.
     *
     * This method tries its best to do this in the most efficient possible manner. It avoids
     * getting text from the editor if possible at all.
     * This is called from the KeyboardSwitcher (through a trampoline in LatinIME) because it
     * needs to know auto caps state to display the right layout.
     *
     * @param settingsValues the relevant settings values
     * @return a caps mode from TextUtils.CAP_MODE_* or Constants.TextUtils.CAP_MODE_OFF.
     */
    public int getCurrentAutoCapsState(final SettingsValues settingsValues) {
        if (!settingsValues.mAutoCap) return Constants.TextUtils.CAP_MODE_OFF;

        final EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return Constants.TextUtils.CAP_MODE_OFF;
        final int inputType = ei.inputType;
        // Warning: this depends on mSpaceState, which may not be the most current value. If
        // mSpaceState gets updated later, whoever called this may need to be told about it.
        return mConnection.getCursorCapsMode(inputType, settingsValues.mSpacingAndPunctuations,
                SpaceState.PHANTOM == mSpaceState);
    }

    public int getCurrentRecapitalizeState() {
        if (!mRecapitalizeStatus.isActive()
                || !mRecapitalizeStatus.isSetAt(mConnection.getExpectedSelectionStart(),
                        mConnection.getExpectedSelectionEnd())) {
            // Not recapitalizing at the moment
            return RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE;
        }
        return mRecapitalizeStatus.getCurrentMode();
    }

    /**
     * @return the editor info for the current editor
     */
    private EditorInfo getCurrentInputEditorInfo() {
        return mLatinIME.getCurrentInputEditorInfo();
    }

    /**
     * Get the nth previous word before the cursor as context for the suggestion process.
     * @param spacingAndPunctuations the current spacing and punctuations settings.
     * @param nthPreviousWord reverse index of the word to get (1-indexed)
     * @return the nth previous word before the cursor.
     */
    // TODO: Make this private
    public CharSequence getNthPreviousWordForSuggestion(
            final SpacingAndPunctuations spacingAndPunctuations, final int nthPreviousWord) {
        if (spacingAndPunctuations.mCurrentLanguageHasSpaces) {
            // If we are typing in a language with spaces we can just look up the previous
            // word from textview.
            return mConnection.getNthPreviousWord(spacingAndPunctuations, nthPreviousWord);
        } else {
            return LastComposedWord.NOT_A_COMPOSED_WORD == mLastComposedWord ? null
                    : mLastComposedWord.mCommittedWord;
        }
    }

    /**
     * Tests the passed word for resumability.
     *
     * We can resume suggestions on words whose first code point is a word code point (with some
     * nuances: check the code for details).
     *
     * @param settings the current values of the settings.
     * @param word the word to evaluate.
     * @return whether it's fine to resume suggestions on this word.
     */
    private static boolean isResumableWord(final SettingsValues settings, final String word) {
        final int firstCodePoint = word.codePointAt(0);
        return settings.isWordCodePoint(firstCodePoint)
                && Constants.CODE_SINGLE_QUOTE != firstCodePoint
                && Constants.CODE_DASH != firstCodePoint;
    }

    /**
     * @param actionId the action to perform
     */
    private void performEditorAction(final int actionId) {
        mConnection.performEditorAction(actionId);
    }

    /**
     * Perform the processing specific to inputting TLDs.
     *
     * Some keys input a TLD (specifically, the ".com" key) and this warrants some specific
     * processing. First, if this is a TLD, we ignore PHANTOM spaces -- this is done by type
     * of character in onCodeInput, but since this gets inputted as a whole string we need to
     * do it here specifically. Then, if the last character before the cursor is a period, then
     * we cut the dot at the start of ".com". This is because humans tend to type "www.google."
     * and then press the ".com" key and instinctively don't expect to get "www.google..com".
     *
     * @param text the raw text supplied to onTextInput
     * @return the text to actually send to the editor
     */
    private String performSpecificTldProcessingOnTextInput(final String text) {
        if (text.length() <= 1 || text.charAt(0) != Constants.CODE_PERIOD
                || !Character.isLetter(text.charAt(1))) {
            // Not a tld: do nothing.
            return text;
        }
        // We have a TLD (or something that looks like this): make sure we don't add
        // a space even if currently in phantom mode.
        mSpaceState = SpaceState.NONE;
        final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
        // If no code point, #getCodePointBeforeCursor returns NOT_A_CODE_POINT.
        if (Constants.CODE_PERIOD == codePointBeforeCursor) {
            return text.substring(1);
        } else {
            return text;
        }
    }

    /**
     * Handle a press on the settings key.
     */
    private void onSettingsKeyPressed() {
        mLatinIME.displaySettingsDialog();
    }

    /**
     * Resets the whole input state to the starting state.
     *
     * This will clear the composing word, reset the last composed word, clear the suggestion
     * strip and tell the input connection about it so that it can refresh its caches.
     *
     * @param settingsValues the current values of the settings.
     * @param newSelStart the new selection start, in java characters.
     * @param newSelEnd the new selection end, in java characters.
     * @param clearSuggestionStrip whether this method should clear the suggestion strip.
     */
    // TODO: how is this different from startInput ?!
    // TODO: remove all references to this in LatinIME and make this private
    public void resetEntireInputState(final SettingsValues settingsValues,
            final int newSelStart, final int newSelEnd, final boolean clearSuggestionStrip) {
        final boolean shouldFinishComposition = mWordComposer.isComposingWord();
        resetComposingState(true /* alsoResetLastComposedWord */);
        if (clearSuggestionStrip) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
        }
        mConnection.resetCachesUponCursorMoveAndReturnSuccess(newSelStart, newSelEnd,
                shouldFinishComposition);
    }

    /**
     * Resets only the composing state.
     *
     * Compare #resetEntireInputState, which also clears the suggestion strip and resets the
     * input connection caches. This only deals with the composing state.
     *
     * @param alsoResetLastComposedWord whether to also reset the last composed word.
     */
    // TODO: remove all references to this in LatinIME and make this private.
    public void resetComposingState(final boolean alsoResetLastComposedWord) {
        mWordComposer.reset();
        if (alsoResetLastComposedWord) {
            mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
        }
    }

    /**
     * Gets a chunk of text with or the auto-correction indicator underline span as appropriate.
     *
     * This method looks at the old state of the auto-correction indicator to put or not put
     * the underline span as appropriate. It is important to note that this does not correspond
     * exactly to whether this word will be auto-corrected to or not: what's important here is
     * to keep the same indication as before.
     * When we add a new code point to a composing word, we don't know yet if we are going to
     * auto-correct it until the suggestions are computed. But in the mean time, we still need
     * to display the character and to extend the previous underline. To avoid any flickering,
     * the underline should keep the same color it used to have, even if that's not ultimately
     * the correct color for this new word. When the suggestions are finished evaluating, we
     * will call this method again to fix the color of the underline.
     *
     * @param text the text on which to maybe apply the span.
     * @return the same text, with the auto-correction underline span if that's appropriate.
     */
    // TODO: remove all references to this in LatinIME and make this private. Also, shouldn't
    // this go in some *Utils class instead?
    public CharSequence getTextWithUnderline(final String text) {
        return mIsAutoCorrectionIndicatorOn
                ? SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(mLatinIME, text)
                : text;
    }

    /**
     * Sends a DOWN key event followed by an UP key event to the editor.
     *
     * If possible at all, avoid using this method. It causes all sorts of race conditions with
     * the text view because it goes through a different, asynchronous binder. Also, batch edits
     * are ignored for key events. Use the normal software input methods instead.
     *
     * @param keyCode the key code to send inside the key event.
     */
    private void sendDownUpKeyEvent(final int keyCode) {
        final long eventTime = SystemClock.uptimeMillis();
        mConnection.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        mConnection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    /**
     * Sends a code point to the editor, using the most appropriate method.
     *
     * Normally we send code points with commitText, but there are some cases (where backward
     * compatibility is a concern for example) where we want to use deprecated methods.
     *
     * @param settingsValues the current values of the settings.
     * @param codePoint the code point to send.
     */
    private void sendKeyCodePoint(final SettingsValues settingsValues, final int codePoint) {
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_sendKeyCodePoint(codePoint);
        }
        // TODO: Remove this special handling of digit letters.
        // For backward compatibility. See {@link InputMethodService#sendKeyChar(char)}.
        if (codePoint >= '0' && codePoint <= '9') {
            sendDownUpKeyEvent(codePoint - '0' + KeyEvent.KEYCODE_0);
            return;
        }

        // TODO: we should do this also when the editor has TYPE_NULL
        if (Constants.CODE_ENTER == codePoint && settingsValues.isBeforeJellyBean()) {
            // Backward compatibility mode. Before Jelly bean, the keyboard would simulate
            // a hardware keyboard event on pressing enter or delete. This is bad for many
            // reasons (there are race conditions with commits) but some applications are
            // relying on this behavior so we continue to support it for older apps.
            sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER);
        } else {
            mConnection.commitText(StringUtils.newSingleCodePointString(codePoint), 1);
        }
    }

    /**
     * Promote a phantom space to an actual space.
     *
     * This essentially inserts a space, and that's it. It just checks the options and the text
     * before the cursor are appropriate before doing it.
     *
     * @param settingsValues the current values of the settings.
     */
    // TODO: Make this private.
    public void promotePhantomSpace(final SettingsValues settingsValues) {
        if (settingsValues.shouldInsertSpacesAutomatically()
                && settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                && !mConnection.textBeforeCursorLooksLikeURL()) {
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_promotePhantomSpace();
            }
            sendKeyCodePoint(settingsValues, Constants.CODE_SPACE);
        }
    }

    /**
     * Do the final processing after a batch input has ended. This commits the word to the editor.
     * @param settingsValues the current values of the settings.
     * @param suggestedWords suggestedWords to use.
     */
    public void endBatchInputInternal(final SettingsValues settingsValues,
            final SuggestedWords suggestedWords,
            // TODO: remove this argument
            final KeyboardSwitcher keyboardSwitcher) {
        final String batchInputText = suggestedWords.isEmpty() ? null : suggestedWords.getWord(0);
        if (TextUtils.isEmpty(batchInputText)) {
            return;
        }
        mConnection.beginBatchEdit();
        if (SpaceState.PHANTOM == mSpaceState) {
            promotePhantomSpace(settingsValues);
        }
        final SuggestedWordInfo autoCommitCandidate = mSuggestedWords.getAutoCommitCandidate();
        // Commit except the last word for phrase gesture if the top suggestion is eligible for auto
        // commit.
        if (settingsValues.mPhraseGestureEnabled && null != autoCommitCandidate) {
            // Find the last space
            final int indexOfLastSpace = batchInputText.lastIndexOf(Constants.CODE_SPACE) + 1;
            if (0 != indexOfLastSpace) {
                mConnection.commitText(batchInputText.substring(0, indexOfLastSpace), 1);
                final SuggestedWords suggestedWordsForLastWordOfPhraseGesture =
                        suggestedWords.getSuggestedWordsForLastWordOfPhraseGesture();
                mLatinIME.showSuggestionStrip(suggestedWordsForLastWordOfPhraseGesture);
            }
            final String lastWord = batchInputText.substring(indexOfLastSpace);
            mWordComposer.setBatchInputWord(lastWord);
            mConnection.setComposingText(lastWord, 1);
        } else {
            mWordComposer.setBatchInputWord(batchInputText);
            mConnection.setComposingText(batchInputText, 1);
        }
        mConnection.endBatchEdit();
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onEndBatchInput(batchInputText, 0, suggestedWords);
        }
        // Space state must be updated before calling updateShiftState
        mSpaceState = SpaceState.PHANTOM;
        keyboardSwitcher.updateShiftState();
    }

    /**
     * Commit the typed string to the editor.
     *
     * This is typically called when we should commit the currently composing word without applying
     * auto-correction to it. Typically, we come here upon pressing a separator when the keyboard
     * is configured to not do auto-correction at all (because of the settings or the properties of
     * the editor). In this case, `separatorString' is set to the separator that was pressed.
     * We also come here in a variety of cases with external user action. For example, when the
     * cursor is moved while there is a composition, or when the keyboard is closed, or when the
     * user presses the Send button for an SMS, we don't auto-correct as that would be unexpected.
     * In this case, `separatorString' is set to NOT_A_SEPARATOR.
     *
     * @param settingsValues the current values of the settings.
     * @param separatorString the separator that's causing the commit, or NOT_A_SEPARATOR if none.
     */
    // TODO: Make this private
    public void commitTyped(final SettingsValues settingsValues, final String separatorString) {
        if (!mWordComposer.isComposingWord()) return;
        final String typedWord = mWordComposer.getTypedWord();
        if (typedWord.length() > 0) {
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.getInstance().onWordFinished(typedWord, mWordComposer.isBatchMode());
            }
            commitChosenWord(settingsValues, typedWord,
                    LastComposedWord.COMMIT_TYPE_USER_TYPED_WORD, separatorString);
        }
    }

    /**
     * Commit the current auto-correction.
     *
     * This will commit the best guess of the keyboard regarding what the user meant by typing
     * the currently composing word. The IME computes suggestions and assigns a confidence score
     * to each of them; when it's confident enough in one suggestion, it replaces the typed string
     * by this suggestion at commit time. When it's not confident enough, or when it has no
     * suggestions, or when the settings or environment does not allow for auto-correction, then
     * this method just commits the typed string.
     * Note that if suggestions are currently being computed in the background, this method will
     * block until the computation returns. This is necessary for consistency (it would be very
     * strange if pressing space would commit a different word depending on how fast you press).
     *
     * @param settingsValues the current value of the settings.
     * @param separator the separator that's causing the commit to happen.
     */
    private void commitCurrentAutoCorrection(final SettingsValues settingsValues,
            final String separator,
            // TODO: Remove this argument.
            final LatinIME.UIHandler handler) {
        // Complete any pending suggestions query first
        if (handler.hasPendingUpdateSuggestions()) {
            performUpdateSuggestionStripSync(settingsValues, handler);
        }
        final String typedAutoCorrection = mWordComposer.getAutoCorrectionOrNull();
        final String typedWord = mWordComposer.getTypedWord();
        final String autoCorrection = (typedAutoCorrection != null)
                ? typedAutoCorrection : typedWord;
        if (autoCorrection != null) {
            if (TextUtils.isEmpty(typedWord)) {
                throw new RuntimeException("We have an auto-correction but the typed word "
                        + "is empty? Impossible! I must commit suicide.");
            }
            if (settingsValues.mIsInternal) {
                LatinImeLoggerUtils.onAutoCorrection(
                        typedWord, autoCorrection, separator, mWordComposer);
            }
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                final SuggestedWords suggestedWords = mSuggestedWords;
                ResearchLogger.latinIme_commitCurrentAutoCorrection(typedWord, autoCorrection,
                        separator, mWordComposer.isBatchMode(), suggestedWords);
            }
            commitChosenWord(settingsValues, autoCorrection,
                    LastComposedWord.COMMIT_TYPE_DECIDED_WORD, separator);
            if (!typedWord.equals(autoCorrection)) {
                // This will make the correction flash for a short while as a visual clue
                // to the user that auto-correction happened. It has no other effect; in particular
                // note that this won't affect the text inside the text field AT ALL: it only makes
                // the segment of text starting at the supplied index and running for the length
                // of the auto-correction flash. At this moment, the "typedWord" argument is
                // ignored by TextView.
                mConnection.commitCorrection(new CorrectionInfo(
                        mConnection.getExpectedSelectionEnd() - autoCorrection.length(),
                        typedWord, autoCorrection));
            }
        }
    }

    /**
     * Commits the chosen word to the text field and saves it for later retrieval.
     *
     * @param settingsValues the current values of the settings.
     * @param chosenWord the word we want to commit.
     * @param commitType the type of the commit, as one of LastComposedWord.COMMIT_TYPE_*
     * @param separatorString the separator that's causing the commit, or NOT_A_SEPARATOR if none.
     */
    // TODO: Make this private
    public void commitChosenWord(final SettingsValues settingsValues, final String chosenWord,
            final int commitType, final String separatorString) {
        final SuggestedWords suggestedWords = mSuggestedWords;
        final CharSequence chosenWordWithSuggestions =
                SuggestionSpanUtils.getTextWithSuggestionSpan(mLatinIME, chosenWord,
                        suggestedWords);
        mConnection.commitText(chosenWordWithSuggestions, 1);
        // TODO: we pass 2 here, but would it be better to move this above and pass 1 instead?
        final String prevWord = mConnection.getNthPreviousWord(
                settingsValues.mSpacingAndPunctuations, 2);
        // Add the word to the user history dictionary
        performAdditionToUserHistoryDictionary(settingsValues, chosenWord, prevWord);
        // TODO: figure out here if this is an auto-correct or if the best word is actually
        // what user typed. Note: currently this is done much later in
        // LastComposedWord#didCommitTypedWord by string equality of the remembered
        // strings.
        mLastComposedWord = mWordComposer.commitWord(commitType,
                chosenWordWithSuggestions, separatorString, prevWord);
        final boolean shouldDiscardPreviousWordForSuggestion;
        if (0 == StringUtils.codePointCount(separatorString)) {
            // Separator is 0-length, we can keep the previous word for suggestion. Either this
            // was a manual pick or the language has no spaces in which case we want to keep the
            // previous word, or it was the keyboard closing or the cursor moving in which case it
            // will be reset anyway.
            shouldDiscardPreviousWordForSuggestion = false;
        } else {
            // Otherwise, we discard if the separator contains any non-whitespace.
            shouldDiscardPreviousWordForSuggestion =
                    !StringUtils.containsOnlyWhitespace(separatorString);
        }
        if (shouldDiscardPreviousWordForSuggestion) {
            mWordComposer.discardPreviousWordForSuggestion();
        }
    }

    /**
     * Retry resetting caches in the rich input connection.
     *
     * When the editor can't be accessed we can't reset the caches, so we schedule a retry.
     * This method handles the retry, and re-schedules a new retry if we still can't access.
     * We only retry up to 5 times before giving up.
     *
     * @param settingsValues the current values of the settings.
     * @param tryResumeSuggestions Whether we should resume suggestions or not.
     * @param remainingTries How many times we may try again before giving up.
     * @return whether true if the caches were successfully reset, false otherwise.
     */
    // TODO: make this private
    public boolean retryResetCachesAndReturnSuccess(final SettingsValues settingsValues,
            final boolean tryResumeSuggestions, final int remainingTries,
            // TODO: remove these arguments
            final LatinIME.UIHandler handler) {
        if (!mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                mConnection.getExpectedSelectionStart(), mConnection.getExpectedSelectionEnd(),
                false /* shouldFinishComposition */)) {
            if (0 < remainingTries) {
                handler.postResetCaches(tryResumeSuggestions, remainingTries - 1);
                return false;
            }
            // If remainingTries is 0, we should stop waiting for new tries, however we'll still
            // return true as we need to perform other tasks (for example, loading the keyboard).
        }
        mConnection.tryFixLyingCursorPosition();
        if (tryResumeSuggestions) {
            handler.postResumeSuggestions();
        }
        return true;
    }
}
