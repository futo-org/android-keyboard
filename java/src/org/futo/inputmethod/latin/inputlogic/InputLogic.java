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

package org.futo.inputmethod.latin.inputlogic;

import android.graphics.Color;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.SuggestionSpan;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;

import org.futo.inputmethod.accessibility.AccessibilityUtils;
import org.futo.inputmethod.compat.SuggestionSpanUtils;
import org.futo.inputmethod.engine.IMEHelper;
import org.futo.inputmethod.engine.general.GeneralIME;
import org.futo.inputmethod.event.Event;
import org.futo.inputmethod.event.InputTransaction;
import org.futo.inputmethod.keyboard.Keyboard;
import org.futo.inputmethod.keyboard.KeyboardSwitcher;
import org.futo.inputmethod.latin.DictionaryFacilitator;
import org.futo.inputmethod.latin.LastComposedWord;
import org.futo.inputmethod.latin.NgramContext;
import org.futo.inputmethod.latin.RichInputConnection;
import org.futo.inputmethod.latin.Suggest;
import org.futo.inputmethod.latin.Suggest.OnGetSuggestedWordsCallback;
import org.futo.inputmethod.latin.SuggestedWords;
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.futo.inputmethod.latin.WordComposer;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.InputPointers;
import org.futo.inputmethod.latin.common.StringUtils;
import org.futo.inputmethod.latin.define.DebugFlags;
import org.futo.inputmethod.latin.settings.Settings;
import org.futo.inputmethod.latin.settings.SettingsValues;
import org.futo.inputmethod.latin.settings.SettingsValuesForSuggestion;
import org.futo.inputmethod.latin.settings.SpacingAndPunctuations;
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewAccessor;
import org.futo.inputmethod.latin.uix.actions.BugViewerKt;
import org.futo.inputmethod.latin.utils.InputTypeUtils;
import org.futo.inputmethod.latin.utils.RecapitalizeStatus;
import org.futo.inputmethod.latin.utils.StatsUtils;
import org.futo.inputmethod.latin.utils.TextRange;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

/**
 * This class manages the input logic.
 */
public final class InputLogic {
    private static final boolean COMPOSITION_TEXT_AFTER = false;

    private static final String TAG = InputLogic.class.getSimpleName();

    private final IMEHelper mImeHelper;
    private final GeneralIME mIme;
    public final SuggestionStripViewAccessor mSuggestionStripViewAccessor;

    // Never null.
    private InputLogicHandler mInputLogicHandler = InputLogicHandler.NULL_HANDLER;

    // TODO : make all these fields private as soon as possible.
    // Current space state of the input method. This can be any of the above constants.
    private int mSpaceState;
    // Never null
    public SuggestedWords mSuggestedWords = SuggestedWords.getEmptyInstance();
    public final Suggest mSuggest;
    private final DictionaryFacilitator mDictionaryFacilitator;

    public LastComposedWord mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
    
    public final WordComposer mWordComposer;
    public final RichInputConnection mConnection;
    private final RecapitalizeStatus mRecapitalizeStatus = new RecapitalizeStatus();

    private int mDeleteCount;
    private long mLastKeyTime;
    public final TreeSet<Long> mCurrentlyPressedHardwareKeys = new TreeSet<>();

    // Keeps track of most recently inserted text (multi-character key) for reverting
    private String mEnteredText;

    // TODO: This boolean is persistent state and causes large side effects at unexpected times.
    // Find a way to remove it for readability.
    private boolean mIsAutoCorrectionIndicatorOn;
    private long mDoubleSpacePeriodCountdownStart;

    // The word being corrected while the cursor is in the middle of the word.
    // Note: This does not have a composing span, so it must be handled separately.
    private String mWordBeingCorrectedByCursor = null;

    /**
     * Create a new instance of the input logic.
     * @param imeHelper the interface to access IME stuff
     * @param suggestionStripViewAccessor an object to access the suggestion strip view.
     * @param dictionaryFacilitator facilitator for getting suggestions and updating user history
     * dictionary.
     */
    public InputLogic(final IMEHelper imeHelper,
          final SuggestionStripViewAccessor suggestionStripViewAccessor,
          final DictionaryFacilitator dictionaryFacilitator,
          final GeneralIME ime) {
        mSuggestionStripViewAccessor = suggestionStripViewAccessor;
        mWordComposer = new WordComposer();
        mConnection = new RichInputConnection(imeHelper);
        mInputLogicHandler = InputLogicHandler.NULL_HANDLER;
        mSuggest = new Suggest(dictionaryFacilitator);
        mDictionaryFacilitator = dictionaryFacilitator;
        mImeHelper = imeHelper;
        mIme = ime;
    }

    private int numCursorUpdatesSinceInputStarted = 0;
    /**
     * Initializes the input logic for input in an editor.
     *
     * Call this when input starts or restarts in some editor (typically, in onStartInputView).
     *
     * @param combiningSpec the combining spec string for this subtype
     * @param settingsValues the current settings values
     */
    public void startInput(final String combiningSpec, final SettingsValues settingsValues) {
        mEnteredText = null;
        mWordBeingCorrectedByCursor = null;
        numCursorUpdatesSinceInputStarted = 0;
        mConnection.finishComposingText(); // On screen rotation in case we were composing, finish composition before resetting
        mConnection.onStartInput();
        if (!mWordComposer.getTypedWord().isEmpty()) {
            // For messaging apps that offer send button, the IME does not get the opportunity
            // to capture the last word. This block should capture those uncommitted words.
            // The timestamp at which it is captured is not accurate but close enough.
            StatsUtils.onWordCommitUserTyped(
                    mWordComposer.getTypedWord(), mWordComposer.isBatchMode());
        }
        mWordComposer.restartCombining(combiningSpec);
        resetComposingState(true /* alsoResetLastComposedWord */);
        mDeleteCount = 0;
        mSpaceState = SpaceState.NONE;
        mRecapitalizeStatus.disable(); // Do not perform recapitalize until the cursor is moved once
        mCurrentlyPressedHardwareKeys.clear();
        mSuggestedWords = SuggestedWords.getEmptyInstance();

        final EditorInfo ei = getCurrentInputEditorInfo();
        if(ei != null && !mConnection.resetCachesUponCursorMoveAndReturnSuccess(
            ei.initialSelStart, ei.initialSelEnd, false)) {
            // Sometimes, while rotating, for some reason the framework tells the app we are not
            // connected to it and that means we can't refresh the cache. In this case, schedule
            // a refresh later.
            // We try resetting the caches up to 5 times before giving up.
            // TODO: Not sure this is necessary anymore
        }

        // In some cases (namely, after rotation of the device) editorInfo.initialSelStart is lying
        // so we try using some heuristics to find out about these and fix them.
        mConnection.tryFixLyingCursorPosition();
        mConnection.updateICCursor(-1, mConnection.mExpectedSelStart, -1, mConnection.mExpectedSelEnd);

        cancelDoubleSpacePeriodCountdown();
        if (InputLogicHandler.NULL_HANDLER == mInputLogicHandler) {
            mInputLogicHandler = new InputLogicHandler(null, this);
        } else {
            mInputLogicHandler.reset();
        }

        if (settingsValues.mShouldShowLxxSuggestionUi) {
            mConnection.requestCursorUpdates(true /* enableMonitor */,
                    true /* requestImmediateCallback */);
        }

        restartSuggestionsOnWordTouchedByCursor(
                settingsValues, null,
                true,
                mImeHelper.getCurrentKeyboardScriptId()
        );
    }

    /**
     * Call this when the subtype changes.
     * @param combiningSpec the spec string for the combining rules
     * @param settingsValues the current settings values
     */
    public void onSubtypeChanged(final String combiningSpec, final SettingsValues settingsValues) {
        finishInput();
        startInput(combiningSpec, settingsValues);
    }

    /**
     * Call this when the orientation changes.
     * @param settingsValues the current values of the settings.
     */
    public void onOrientationChange(final SettingsValues settingsValues) {
        // If !isComposingWord, #commitTyped() is a no-op, but still, it's better to avoid
        // the useless IPC of {begin,end}BatchEdit.
        if (mWordComposer.isComposingWord()) {
            mConnection.beginBatchEdit();
            // If we had a composition in progress, we need to commit the word so that the
            // suggestionsSpan will be added. This will allow resuming on the same suggestions
            // after rotation is finished.
            commitTyped(settingsValues, LastComposedWord.NOT_A_SEPARATOR);
            mConnection.endBatchEdit();
        }
    }

    /**
     * Clean up the input logic after input is finished.
     */
    public void finishInput() {
        if (mWordComposer.isComposingWord()) {
            mConnection.finishComposingText();
            StatsUtils.onWordCommitUserTyped(
                    mWordComposer.getTypedWord(), mWordComposer.isBatchMode());
        }
        mIsAutoCorrectionIndicatorOn = false;
        resetComposingState(true /* alsoResetLastComposedWord */);
        mInputLogicHandler.reset();
        mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
    }

    // Normally this class just gets out of scope after the process ends, but in unit tests, we
    // create several instances of LatinIME in the same process, which results in several
    // instances of InputLogic. This cleans up the associated handler so that tests don't leak
    // handlers.
    public void recycle() {
        final InputLogicHandler inputLogicHandler = mInputLogicHandler;
        mInputLogicHandler = InputLogicHandler.NULL_HANDLER;
        inputLogicHandler.destroy();
        mDictionaryFacilitator.closeDictionaries();
    }

    void postUpdateSuggestionStrip(int style) {
        mIme.updateSuggestions(style);
    }

    /**
     * React to a string input.
     *
     * This is triggered by keys that input many characters at once, like the ".com" key or
     * some additional keys for example.
     *
     * @param settingsValues the current values of the settings.
     * @param event the input event containing the data.
     * @return the complete transaction object
     */
    public InputTransaction onTextInput(final SettingsValues settingsValues, final Event event, final int keyboardShiftMode) {
        final String rawText = event.getTextToCommit().toString();
        final InputTransaction inputTransaction = new InputTransaction(settingsValues, event,
                SystemClock.uptimeMillis(), mSpaceState,
                getActualCapsMode(settingsValues, keyboardShiftMode));
        mConnection.beginBatchEdit();
        if (mWordComposer.isComposingWord()) {
            commitCurrentAutoCorrection(settingsValues, rawText);
        } else {
            resetComposingState(true /* alsoResetLastComposedWord */);
        }
        postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_NONE);
        final String text = performSpecificTldProcessingOnTextInput(rawText);
        if (SpaceState.PHANTOM == mSpaceState) {
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues);
        }

        if(event.mKeyCode == Constants.CODE_OUTPUT_TEXT_WITH_SPACES) {
            if(mConnection.cursorNotPrecededByWhitespace()) mConnection.commitText(" ", 1);
        }
        mConnection.commitText(text, 1);
        StatsUtils.onWordCommitUserTyped(mEnteredText, mWordComposer.isBatchMode());

        if(event.mKeyCode == Constants.CODE_OUTPUT_TEXT_WITH_SPACES) {
            insertOrSetPhantomSpace(settingsValues);
        }
        mConnection.endBatchEdit();
        // Space state must be updated before calling updateShiftState
        mSpaceState = SpaceState.NONE;
        mEnteredText = text;
        mWordBeingCorrectedByCursor = null;
        inputTransaction.setDidAffectContents();
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
        return inputTransaction;
    }

    /** Only used when automatic spaces is set to NONE */
    private void insertSuggestionAndKeepComposing(final SettingsValues settingsValues,
                                                  final String word,
                                                  final boolean updateSuggestions) {
        final boolean needToUpdateSuggestionStrip = updateSuggestions && word != mWordComposer.getTypedWord();

        if(word.indexOf(' ') != -1 || mWordComposer.isBatchMode()) {
            commitChosenWord(settingsValues, word, LastComposedWord.COMMIT_TYPE_MANUAL_PICK,
                    LastComposedWord.NOT_A_SEPARATOR, 1);
            mLastComposedWord.deactivate();
            resetComposingWord(settingsValues, false);
        } else {
            final int[] codePoints = StringUtils.toCodePointArray(word);
            mWordComposer.setComposingWord(codePoints,
                    mImeHelper.getCodepointCoordinates(codePoints));
            setComposingTextInternal(word, 1);
        }

        if(needToUpdateSuggestionStrip) postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING);
    }

    /**
     * A suggestion was picked from the suggestion strip.
     * @param settingsValues the current values of the settings.
     * @param suggestionInfo the suggestion info.
     * @param keyboardShiftState the shift state of the keyboard, as returned by
     *     {@link org.futo.inputmethod.keyboard.KeyboardSwitcher#getKeyboardShiftMode()}
     * @return the complete transaction object
     */
    // Called from {@link SuggestionStripView} through the {@link SuggestionStripView#Listener}
    // interface
    public InputTransaction onPickSuggestionManually(final SettingsValues settingsValues,
            final SuggestedWordInfo suggestionInfo, final int keyboardShiftState,
            final int currentKeyboardScriptId) {
        final SuggestedWords suggestedWords = mSuggestedWords;
        final String suggestion = suggestionInfo.mWord;
        // If this is a punctuation picked from the suggestion strip, pass it to onCodeInput
        if (suggestion.length() == 1 && suggestedWords.isPunctuationSuggestions()) {
            // We still want to log a suggestion click.
            StatsUtils.onPickSuggestionManually(mImeHelper.getContext(),
                    mSuggestedWords, suggestionInfo, mDictionaryFacilitator);
            // Word separators are suggested before the user inputs something.
            // Rely on onCodeInput to do the complicated swapping/stripping logic consistently.
            final Event event = Event.createPunctuationSuggestionPickedEvent(suggestionInfo);
            return onCodeInput(settingsValues, event, keyboardShiftState,
                    currentKeyboardScriptId);
        }

        final Event event = Event.createSuggestionPickedEvent(suggestionInfo);
        final InputTransaction inputTransaction = new InputTransaction(settingsValues,
                event, SystemClock.uptimeMillis(), mSpaceState, keyboardShiftState);
        // Manual pick affects the contents of the editor, so we take note of this. It's important
        // for the sequence of language switching.
        inputTransaction.setDidAffectContents();

        if(suggestionInfo.mKindAndFlags == SuggestedWordInfo.KIND_UNDO) {
            inputTransaction.setRequiresUpdateSuggestions();

            mConnection.finishComposingText();
            mWordComposer.reset(true);

            mConnection.commitText(suggestionInfo.mWord, 1);

            return inputTransaction;
        }

        mConnection.beginBatchEdit();
        if (SpaceState.PHANTOM == mSpaceState && suggestion.length() > 0
                // In the batch input mode, a manually picked suggested word should just replace
                // the current batch input text and there is no need for a phantom space.
                && !mWordComposer.isBatchMode()) {
            final int firstChar = Character.codePointAt(suggestion, 0);
            if (!settingsValues.isWordSeparator(firstChar)
                    || settingsValues.isUsuallyPrecededBySpace(firstChar)) {

                if(!mConnection.spacePrecedesComposingText())
                    insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues);
            }
        }

        // TODO: We should not need the following branch. We should be able to take the same
        // code path as for other kinds, use commitChosenWord, and do everything normally. We will
        // however need to reset the suggestion strip right away, because we know we can't take
        // the risk of calling commitCompletion twice because we don't know how the app will react.
        if (suggestionInfo.isKindOf(SuggestedWordInfo.KIND_APP_DEFINED)) {
            mSuggestedWords = SuggestedWords.getEmptyInstance();
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
            inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
            resetComposingState(true /* alsoResetLastComposedWord */);
            mConnection.commitCompletion(suggestionInfo.mApplicationSpecifiedCompletionInfo);
            mConnection.endBatchEdit();
            return inputTransaction;
        }

        // The logic for no auto-spaces is a little different, we need to stay in composing
        // state. If the suggestion has spaces in it, then we have to compose the final word.
        if(settingsValues.mAltSpacesMode == Settings.SPACES_MODE_NONE) {
            insertSuggestionAndKeepComposing(settingsValues, suggestion, true);
            mConnection.endBatchEdit();
            return inputTransaction;
        }

        commitChosenWord(settingsValues, suggestion, LastComposedWord.COMMIT_TYPE_MANUAL_PICK,
                LastComposedWord.NOT_A_SEPARATOR, suggestionInfo.isKindOf(SuggestedWordInfo.KIND_TYPED) ? 3 : 1);
        mConnection.endBatchEdit();
        // Don't allow cancellation of manual pick
        mLastComposedWord.deactivate();
        // Space state must be updated before calling updateShiftState
        insertOrSetPhantomSpace(settingsValues);
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);

        // If we're not showing the "Touch again to save", then update the suggestion strip.
        // That's going to be predictions (or punctuation suggestions), so INPUT_STYLE_NONE.
        postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_NONE);

        StatsUtils.onPickSuggestionManually(mImeHelper.getContext(),
                mSuggestedWords, suggestionInfo, mDictionaryFacilitator);
        StatsUtils.onWordCommitSuggestionPickedManually(
                suggestionInfo.mWord, mWordComposer.isBatchMode());
        return inputTransaction;
    }

    /**
     * Consider an update to the cursor position. Evaluate whether this update has happened as
     * part of normal typing or whether it was an explicit cursor move by the user. In any case,
     * do the necessary adjustments.
     * @param oldSelStart old selection start
     * @param oldSelEnd old selection end
     * @param newSelStart new selection start
     * @param newSelEnd new selection end
     * @param settingsValues the current values of the settings.
     * @return whether the cursor has moved as a result of user interaction.
     */
    public boolean onUpdateSelection(final int oldSelStart, final int oldSelEnd,
            int newSelStart, int newSelEnd,
            final int composingStart, final int composingEnd,
            final SettingsValues settingsValues) {
        // TODO: Not sure this is necessary anymore
        /*numCursorUpdatesSinceInputStarted++;
        if(numCursorUpdatesSinceInputStarted <= 1) {
            if(mConnection.tryFixLyingCursorPosition()) {
                newSelStart = mConnection.mExpectedSelStart;
                newSelEnd = mConnection.mExpectedSelEnd;
            }
        }*/

        mConnection.updateICCursor(oldSelStart, newSelStart, oldSelEnd, newSelEnd);

        if (mConnection.isBelatedExpectedUpdate(oldSelStart, newSelStart, oldSelEnd, newSelEnd, composingStart, composingEnd)) {
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
        // As an added small gift from the framework, it happens upon rotation when there
        // is a selection that we get a wrong cursor position delivered to startInput() that
        // does not get reflected in the oldSel{Start,End} parameters to the next call to
        // onUpdateSelection. In this case, we may have set a composition, and when we're here
        // we realize we shouldn't have. In theory, in this case, selectionChangedOrSafeToReset
        // should be true, but that is if the framework had taken that wrong cursor position
        // into account, which means we have to reset the entire composing state whenever there
        // is or was a selection regardless of whether it changed or not.
        boolean suggestionsDisabled = !settingsValues.needsToLookupSuggestions();
        boolean noLongerInWord = selectionChangedOrSafeToReset && !mWordComposer.moveCursorByAndReturnIfInsideComposingWord(moveAmount);
        if (hasOrHadSelection || suggestionsDisabled || noLongerInWord) {
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
            resetEntireInputState(newSelStart, newSelEnd, false /* clearSuggestionStrip */);
            // If the user is in the middle of correcting a word, we should learn it before moving
            // the cursor away.
            if (!TextUtils.isEmpty(mWordBeingCorrectedByCursor)) {
                final int timeStampInSeconds = (int)TimeUnit.MILLISECONDS.toSeconds(
                        System.currentTimeMillis());
                performAdditionToUserHistoryDictionary(settingsValues, mWordBeingCorrectedByCursor,
                        NgramContext.EMPTY_PREV_WORDS_INFO, -1);
            }
        } else {
            // resetEntireInputState calls resetCachesUponCursorMove, but forcing the
            // composition to end. But in all cases where we don't reset the entire input
            // state, we still want to tell the rich input connection about the new cursor
            // position so that it can update its caches.
            mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                    newSelStart, newSelEnd, false /* shouldFinishComposition */);
        }

        // The cursor has been moved : we now accept to perform recapitalization
        mRecapitalizeStatus.enable();
        // We moved the cursor. If we are touching a word, we need to resume suggestion.
        mIsAutoCorrectionIndicatorOn = false;
        //mIme.updateSuggestions(SuggestedWords.INPUT_STYLE_TYPING);

        // TODO: Fairly sure some of the above is redundant with this
        restartSuggestionsOnWordTouchedByCursor(settingsValues, null, true, mImeHelper.getCurrentKeyboardScriptId());
        // Stop the last recapitalization, if started.
        mRecapitalizeStatus.stop();
        mWordBeingCorrectedByCursor = null;
        return true;
    }

    /**
     * React to a code input. It may be a code point to insert, or a symbolic value that influences
     * the keyboard behavior.
     *
     * Typically, this is called whenever a key is pressed on the software keyboard. This is not
     * the entry point for gesture input; see the onBatchInput* family of functions for this.
     *
     * @param settingsValues the current settings values.
     * @param event the event to handle.
     * @param keyboardShiftMode the current shift mode of the keyboard, as returned by
     *     {@link org.futo.inputmethod.keyboard.KeyboardSwitcher#getKeyboardShiftMode()}
     * @return the complete transaction object
     */
    public InputTransaction onCodeInput(final SettingsValues settingsValues,
            @Nonnull final Event event, final int keyboardShiftMode,
            final int currentKeyboardScriptId) {
        mWordBeingCorrectedByCursor = null;

        if(mWordComposer.isComposingWord() && mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // TODO: Double check this isn't causing any new regressions. I believe this is also redundant
            //  with a lot of subsequent similar checks scattered in various handleXyz functions
            unlearnWord(mWordComposer.getTypedWord(), settingsValues, Constants.EVENT_BACKSPACE);
            resetComposingWord(settingsValues, false);
        }
        final Event processedEvent = mWordComposer.processEvent(event);
        final InputTransaction inputTransaction = new InputTransaction(settingsValues,
                processedEvent, SystemClock.uptimeMillis(), mSpaceState,
                getActualCapsMode(settingsValues, keyboardShiftMode));
        if (processedEvent.mKeyCode != Constants.CODE_DELETE
                || inputTransaction.mTimestamp > mLastKeyTime + Constants.LONG_PRESS_MILLISECONDS) {
            mDeleteCount = 0;
        }

        mLastKeyTime = inputTransaction.mTimestamp;
        mConnection.beginBatchEdit();
        if (!mWordComposer.isComposingWord()) {
            // TODO: is this useful? It doesn't look like it should be done here, but rather after
            // a word is committed.
            mIsAutoCorrectionIndicatorOn = false;
        }

        // TODO: Consolidate the double-space period timer, mLastKeyTime, and the space state.
        if (processedEvent.mCodePoint != Constants.CODE_SPACE) {
            cancelDoubleSpacePeriodCountdown();
        }

        Event currentEvent = processedEvent;
        while (null != currentEvent) {
            if (currentEvent.isConsumed()) {
                handleConsumedEvent(currentEvent, inputTransaction);
            } else if (currentEvent.isFunctionalKeyEvent()) {
                handleFunctionalEvent(currentEvent, inputTransaction, currentKeyboardScriptId);
            } else {
                handleNonFunctionalEvent(currentEvent, inputTransaction);
            }
            currentEvent = currentEvent.mNextEvent;
        }
        // Try to record the word being corrected when the user enters a word character or
        // the backspace key.
        if (!mConnection.hasSlowInputConnection() && !mWordComposer.isComposingWord()
                && (settingsValues.isWordCodePoint(processedEvent.mCodePoint) ||
                        processedEvent.mKeyCode == Constants.CODE_DELETE)) {
            mWordBeingCorrectedByCursor = getWordAtCursor(
                   settingsValues, currentKeyboardScriptId);
        }
        if (!inputTransaction.didAutoCorrect() && processedEvent.mKeyCode != Constants.CODE_SHIFT
                && processedEvent.mKeyCode != Constants.CODE_CAPSLOCK
                && processedEvent.mKeyCode != Constants.CODE_SWITCH_ALPHA_SYMBOL)
            mLastComposedWord.deactivate();
        if (Constants.CODE_DELETE != processedEvent.mKeyCode) {
            mEnteredText = null;
        }
        mConnection.endBatchEdit();


        updateBoostedCodePoints(
                settingsValues,
                settingsValues.isWordCodePoint(processedEvent.mCodePoint)
        );

        updateUiInputState();


        return inputTransaction;
    }

    /**
     * Updates keys whose hitboxes are boosted. This works by looking at the word being composed,
     * and checking for next letters that would still produce a valid word within the dictionary
     */
    private void updateBoostedCodePoints(
            final SettingsValues settingsValues,
            final boolean wasWordCodePoint
    ) {
        Set<Integer> boostedCodePoints = null;
        // Require key boosting setting to be enabled
        if(settingsValues.mUseDictionaryKeyBoosting
                // text field must allow autocorrection
                && settingsValues.mAutoCorrectionEnabledPerTextFieldSettings
                // previous codepoint must have been a word codepoint (i.e. exclude boosting after backspace or symbols)
                && wasWordCodePoint
                // accessibility must not be enabled
                && !AccessibilityUtils.getInstance().isAccessibilityEnabled()
        ) {
            boostedCodePoints = mSuggest.getValidNextCodePoints(mWordComposer);
        }

        mImeHelper.updateBoostedCodePoints(boostedCodePoints);
    }

    public void onStartBatchInput(final SettingsValues settingsValues,
            final KeyboardSwitcher keyboardSwitcher) {
        mWordBeingCorrectedByCursor = null;
        mInputLogicHandler.onStartBatchInput();

        mIme.setNeutralSuggestionStrip();
        //handler.showGesturePreviewAndSuggestionStrip(
        //        SuggestedWords.getEmptyInstance(), false /* dismissGestureFloatingPreviewText */);
        //handler.cancelUpdateSuggestionStrip();

        ++mAutoCommitSequenceNumber;
        mConnection.beginBatchEdit();
        if (mWordComposer.isComposingWord()) {
            if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
                // If we are in the middle of a recorrection, we need to commit the recorrection
                // first so that we can insert the batch input at the current cursor position.
                // We also need to unlearn the original word that is now being corrected.
                unlearnWord(mWordComposer.getTypedWord(), settingsValues,
                        Constants.EVENT_BACKSPACE);
                resetEntireInputState(mConnection.getExpectedSelectionStart(),
                        mConnection.getExpectedSelectionEnd(), true /* clearSuggestionStrip */);
            } else {
                commitCurrentAutoCorrection(settingsValues, LastComposedWord.NOT_A_SEPARATOR);
            }
        }
        final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
        if (Character.isLetterOrDigit(codePointBeforeCursor)
                || settingsValues.isUsuallyFollowedBySpace(codePointBeforeCursor)) {
            final boolean autoShiftHasBeenOverriden = keyboardSwitcher.getKeyboardShiftMode() !=
                    getCurrentAutoCapsState(settingsValues);
            if(settingsValues.mAltSpacesMode != Settings.SPACES_MODE_NONE) mSpaceState = SpaceState.PHANTOM;
            if (!autoShiftHasBeenOverriden) {
                // When we change the space state, we need to update the shift state of the
                // keyboard unless it has been overridden manually. This is happening for example
                // after typing some letters and a period, then gesturing; the keyboard is not in
                // caps mode yet, but since a gesture is starting, it should go in caps mode,
                // unless the user explictly said it should not.
                keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(settingsValues));
            }
        }
        mConnection.endBatchEdit();
        mWordComposer.setCapitalizedModeAtStartComposingTime(
                getActualCapsMode(settingsValues, keyboardSwitcher.getKeyboardShiftMode()));
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
    public void onUpdateBatchInput(final InputPointers batchPointers) {
        mInputLogicHandler.onUpdateBatchInput(batchPointers, mAutoCommitSequenceNumber);
    }

    public void onEndBatchInput(final InputPointers batchPointers) {
        mInputLogicHandler.updateTailBatchInput(batchPointers, mAutoCommitSequenceNumber);
        ++mAutoCommitSequenceNumber;
    }

    public void onCancelBatchInput() {
        mInputLogicHandler.onCancelBatchInput();
        mIme.setNeutralSuggestionStrip();
    }

    // TODO: on the long term, this method should become private, but it will be difficult.
    // Especially, how do we deal with InputMethodService.onDisplayCompletions?
    public void setSuggestedWords(final SuggestedWords suggestedWords) {
        if (!suggestedWords.isEmpty()) {
            suggestedWords.mWillAutoCorrect = suggestedWords.mWillAutoCorrect
                    && !mConnection.textBeforeCursorLooksLikeURL();
            final SuggestedWordInfo suggestedWordInfo;
            if (suggestedWords.mWillAutoCorrect) {
                suggestedWordInfo = suggestedWords.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION);
            } else {
                // We can't use suggestedWords.getWord(SuggestedWords.INDEX_OF_TYPED_WORD)
                // because it may differ from mWordComposer.mTypedWord.
                suggestedWordInfo = suggestedWords.mTypedWordInfo;
            }
            mWordComposer.setAutoCorrection(suggestedWordInfo);
        }
        mSuggestedWords = suggestedWords;
        final boolean newAutoCorrectionIndicator = suggestedWords.mWillAutoCorrect;

        // Put a blue underline to a word in TextView which will be auto-corrected.
        if (mIsAutoCorrectionIndicatorOn != newAutoCorrectionIndicator
                && mWordComposer.isComposingWord() && mConnection.useAutoCorrectIndicator()) {
            mIsAutoCorrectionIndicatorOn = newAutoCorrectionIndicator;
            final CharSequence textWithUnderline =
                    getTextWithUnderline(mWordComposer.getTypedWord());
            // TODO: when called from an updateSuggestionStrip() call that results from a posted
            // message, this is called outside any batch edit. Potentially, this may result in some
            // janky flickering of the screen, although the display speed makes it unlikely in
            // the practice.
            setComposingTextInternal(textWithUnderline, 1);
        }
    }

    /**
     * Handle a consumed event.
     *
     * Consumed events represent events that have already been consumed, typically by the
     * combining chain.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleConsumedEvent(final Event event, final InputTransaction inputTransaction) {
        // A consumed event may have text to commit and an update to the composing state, so
        // we evaluate both. With some combiners, it's possible than an event contains both
        // and we enter both of the following if clauses.
        final CharSequence textToCommit = event.getTextToCommit();
        if (!TextUtils.isEmpty(textToCommit)) {
            mConnection.commitText(textToCommit, 1);
            inputTransaction.setDidAffectContents();
        }
        if (mWordComposer.isComposingWord()) {
            setComposingTextInternal(mWordComposer.getTypedWordWithStyles(), 1);
            inputTransaction.setDidAffectContents();
            inputTransaction.setRequiresUpdateSuggestions();
        } else {
            setComposingTextInternal("", 1);
            inputTransaction.setDidAffectContents();
            inputTransaction.setRequiresUpdateSuggestions();
        }
    }

    /**
     * Handle a functional key event.
     *
     * A functional event is a special key, like delete, shift, emoji, or the settings key.
     * Non-special keys are those that generate a single code point.
     * This includes all letters, digits, punctuation, separators, emoji. It excludes keys that
     * manage keyboard-related stuff like shift, language switch, settings, layout switch, or
     * any key that results in multiple code points like the ".com" key.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleFunctionalEvent(final Event event, final InputTransaction inputTransaction,
            final int currentKeyboardScriptId) {

        if(event.getEventType() == Event.EVENT_TYPE_STOP_COMPOSING) {
            commitTyped(inputTransaction.mSettingsValues, "");
            inputTransaction.setRequiresUpdateSuggestions();
            return;
        }

        if(event.mKeyCode <= Constants.CODE_ACTION_MAX && event.mKeyCode >= Constants.CODE_ACTION_0) {
            final int actionId = event.mKeyCode - Constants.CODE_ACTION_0;
            mImeHelper.triggerAction(actionId, false);
            return;
        }

        if(event.mKeyCode <= Constants.CODE_ALT_ACTION_MAX && event.mKeyCode >= Constants.CODE_ALT_ACTION_0) {
            final int actionId = event.mKeyCode - Constants.CODE_ALT_ACTION_0;
            mImeHelper.triggerAction(actionId, true);
            return;
        }

        switch (event.mKeyCode) {
            case Constants.CODE_DELETE:
                handleBackspaceEvent(event, inputTransaction, currentKeyboardScriptId);
                // Backspace is a functional key, but it affects the contents of the editor.
                inputTransaction.setDidAffectContents();
                break;
            case Constants.CODE_SHIFT:
                performRecapitalization(inputTransaction.mSettingsValues);
                inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
                if (mSuggestedWords.isPrediction()) {
                    inputTransaction.setRequiresUpdateSuggestions();
                }
                break;
            case Constants.CODE_CAPSLOCK:
                // Note: Changing keyboard to shift lock state is handled in
                // {@link KeyboardSwitcher#onEvent(Event)}.
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
                // obsolete
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
                // obsolete
                break;
            case Constants.CODE_EMOJI:
                // Note: Switching emoji keyboard is being handled in
                // {@link KeyboardState#onEvent(Event,int)}.
                break;
            case Constants.CODE_ALPHA_FROM_EMOJI:
                // Note: Switching back from Emoji keyboard to the main keyboard is being
                // handled in {@link KeyboardState#onEvent(Event,int)}.
                break;
            case Constants.CODE_SHIFT_ENTER:
                final Event tmpEvent = Event.createSoftwareKeypressEvent(Constants.CODE_ENTER,
                        event.mKeyCode, event.mX, event.mY, event.isKeyRepeat());
                handleNonSpecialCharacterEvent(tmpEvent, inputTransaction);
                // Shift + Enter is treated as a functional key but it results in adding a new
                // line, so that does affect the contents of the editor.
                inputTransaction.setDidAffectContents();
                break;
            case Constants.CODE_TO_NUMBER_LAYOUT:
            case Constants.CODE_TO_ALT_0_LAYOUT:
            case Constants.CODE_TO_ALT_1_LAYOUT:
            case Constants.CODE_TO_ALT_2_LAYOUT:
                // Handled in KeyboardState
                break;
            default:
                throw new RuntimeException("Unknown key code : " + event.mKeyCode);
        }
    }

    /**
     * Handle an event that is not a functional event.
     *
     * These events are generally events that cause input, but in some cases they may do other
     * things like trigger an editor action.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleNonFunctionalEvent(final Event event,
            final InputTransaction inputTransaction) {
        inputTransaction.setDidAffectContents();
        switch (event.mCodePoint) {
            case Constants.CODE_ENTER:
                final EditorInfo editorInfo = getCurrentInputEditorInfo();
                final int imeOptionsActionId =
                        InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo);

                final boolean isCustomAction =
                        InputTypeUtils.IME_ACTION_CUSTOM_LABEL == imeOptionsActionId;
                final boolean isEditorAction =
                        EditorInfo.IME_ACTION_NONE != imeOptionsActionId;

                // In some websites on Chrome, Enter will not actually perform any action as long
                // as we are still composing text. To work around this it makes sense to just finish
                // input when we are sending editor action.
                if(isCustomAction || isEditorAction) {
                    finishInput();
                }

                if (isCustomAction) {
                    // Either we have an actionLabel and we should performEditorAction with
                    // actionId regardless of its value.
                    performEditorAction(editorInfo.actionId);
                } else if (isEditorAction) {
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
                    handleNonSpecialCharacterEvent(event, inputTransaction);
                }
                break;
            default:
                handleNonSpecialCharacterEvent(event, inputTransaction);
                break;
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
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleNonSpecialCharacterEvent(final Event event,
            final InputTransaction inputTransaction) {
        final int codePoint = event.mCodePoint;
        mSpaceState = SpaceState.NONE;

        if(codePoint == Constants.CODE_SPACE
                && inputTransaction.mSpaceState == SpaceState.ANTIPHANTOM) {
            return;
        }

        if (inputTransaction.mSettingsValues.isWordSeparator(codePoint)
                || Character.getType(codePoint) == Character.OTHER_SYMBOL) {
            handleSeparatorEvent(event, inputTransaction);
        } else {
            if (SpaceState.PHANTOM == inputTransaction.mSpaceState) {
                if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
                    // If we are in the middle of a recorrection, we need to commit the recorrection
                    // first so that we can insert the character at the current cursor position.
                    // We also need to unlearn the original word that is now being corrected.
                    unlearnWord(mWordComposer.getTypedWord(), inputTransaction.mSettingsValues,
                            Constants.EVENT_BACKSPACE);
                    resetComposingWord(inputTransaction.mSettingsValues, false);
                }
                commitTyped(inputTransaction.mSettingsValues, LastComposedWord.NOT_A_SEPARATOR);
            }
            handleNonSeparatorEvent(event, inputTransaction.mSettingsValues, inputTransaction);
        }
    }

    /**
     * Handle a non-separator.
     * @param event The event to handle.
     * @param settingsValues The current settings values.
     * @param inputTransaction The transaction in progress.
     */
    private void handleNonSeparatorEvent(final Event event, final SettingsValues settingsValues,
            final InputTransaction inputTransaction) {
        final int codePoint = event.mCodePoint;
        // TODO: refactor this method to stop flipping isComposingWord around all the time, and
        // make it shorter (possibly cut into several pieces). Also factor
        // handleNonSpecialCharacterEvent which has the same name as other handle* methods but is
        // not the same.
        boolean isComposingWord = mWordComposer.isComposingWord();

        // TODO: remove isWordConnector() and use isUsuallyFollowedBySpace() instead.
        // See onStartBatchInput() to see how to do it.
        if (SpaceState.PHANTOM == inputTransaction.mSpaceState
                && !settingsValues.isWordConnector(codePoint)) {
            if (isComposingWord) {
                // Validity check
                throw new RuntimeException("Should not be composing here");
            }
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues);
        }

        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord() || mWordComposer.isBatchMode()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can insert the character at the current cursor position.
            // We also need to unlearn the original word that is now being corrected.
            unlearnWord(mWordComposer.getTypedWord(), inputTransaction.mSettingsValues,
                    Constants.EVENT_BACKSPACE);
            resetComposingWord(settingsValues, false);
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
                && settingsValues.needsToLookupSuggestions() &&
        // In languages with spaces, we only start composing a word when we are not already
        // touching a word. In languages without spaces, the above conditions are sufficient.
        // NOTE: If the InputConnection is slow, we skip the text-after-cursor check since it
        // can incur a very expensive getTextAfterCursor() lookup, potentially making the
        // keyboard UI slow and non-responsive.
        // TODO: Cache the text after the cursor so we don't need to go to the InputConnection
        // each time. We are already doing this for getTextBeforeCursor().
                (!settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                        || !mConnection.isCursorTouchingWord(settingsValues.mSpacingAndPunctuations,
                                InputLogic.COMPOSITION_TEXT_AFTER && !mConnection.hasSlowInputConnection() /* checkTextAfter */))) {
            // Reset entirely the composing state anyway, then start composing a new word unless
            // the character is a word connector. The idea here is, word connectors are not
            // separators and they should be treated as normal characters, except in the first
            // position where they should not start composing a word.
            isComposingWord = !settingsValues.mSpacingAndPunctuations.isWordConnector(codePoint);
            // Here we don't need to reset the last composed word. It will be reset
            // when we commit this one, if we ever do; if on the other hand we backspace
            // it entirely and resume suggestions on the previous word, we'd like to still
            // have touch coordinates for it.
            resetComposingState(false /* alsoResetLastComposedWord */);
        }
        if (isComposingWord) {
            mWordComposer.applyProcessedEvent(event);
            // If it's the first letter, make note of auto-caps state
            if (mWordComposer.isSingleLetter()) {
                mWordComposer.setCapitalizedModeAtStartComposingTime(inputTransaction.mShiftState);
            }
            setComposingTextInternal(getTextWithUnderline(mWordComposer.getTypedWord()), 1);
        } else {
            final boolean swapWeakSpace = tryStripSpaceAndReturnWhetherShouldSwapInstead(event,
                    inputTransaction);

            if (swapWeakSpace && trySwapSwapperAndSpace(event, inputTransaction)) {
                mSpaceState = SpaceState.WEAK;
            } else {
                boolean autoInsertSpaces = canInsertAutoSpace(settingsValues)
                        && (settingsValues.mAltSpacesMode >= Settings.SPACES_MODE_ALL)
                        && !mConnection.digitPrecedesCursor();

                boolean spacePrecedesCursor = mConnection.spacePrecedesCursor();

                sendKeyCodePoint(settingsValues, codePoint);

                if(autoInsertSpaces
                        && spacePrecedesCursor
                        && settingsValues.isUsuallyFollowedBySpaceIffPrecededBySpace(codePoint)) {
                    insertOrSetPhantomSpace(settingsValues);
                }
            }
        }
        inputTransaction.setRequiresUpdateSuggestions();
    }

    /**
     * Handle input of a separator code point.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleSeparatorEvent(final Event event, final InputTransaction inputTransaction) {
        final int codePoint = event.mCodePoint;
        final SettingsValues settingsValues = inputTransaction.mSettingsValues;
        final boolean wasComposingWord = mWordComposer.isComposingWord();
        // We avoid sending spaces in languages without spaces if we were composing.
        final boolean shouldAvoidSendingCode = Constants.CODE_SPACE == codePoint
                && !settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                && wasComposingWord;
        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can insert the separator at the current cursor position.
            // We also need to unlearn the original word that is now being corrected.
            unlearnWord(mWordComposer.getTypedWord(), inputTransaction.mSettingsValues,
                    Constants.EVENT_BACKSPACE);
            resetComposingWord(settingsValues, false);
        }
        // isComposingWord() may have changed since we stored wasComposing
        if (mWordComposer.isComposingWord()) {
            if (settingsValues.mAutoCorrectionEnabledPerUserSettings
                    && Suggest.shouldCodePointAutocorrect(codePoint)) {
                final String separator = shouldAvoidSendingCode ? LastComposedWord.NOT_A_SEPARATOR
                        : StringUtils.newSingleCodePointString(codePoint);
                commitCurrentAutoCorrection(settingsValues, separator);
                inputTransaction.setDidAutoCorrect();
            } else {
                commitTyped(settingsValues,
                        StringUtils.newSingleCodePointString(codePoint));
            }
        }

        final boolean swapWeakSpace = tryStripSpaceAndReturnWhetherShouldSwapInstead(event,
                inputTransaction);

        final boolean isInsideDoubleQuoteOrAfterDigit = Constants.CODE_DOUBLE_QUOTE == codePoint
                && mConnection.isInsideDoubleQuoteOrAfterDigit();

        final boolean needsPrecedingSpace;
        if (SpaceState.PHANTOM != inputTransaction.mSpaceState) {
            needsPrecedingSpace = false;
        } else if (Constants.CODE_DOUBLE_QUOTE == codePoint) {
            // Double quotes behave like they are usually preceded by space iff we are
            // not inside a double quote or after a digit.
            needsPrecedingSpace = !isInsideDoubleQuoteOrAfterDigit;
        } else if (settingsValues.mSpacingAndPunctuations.isClusteringSymbol(codePoint)
                && settingsValues.mSpacingAndPunctuations.isClusteringSymbol(
                        mConnection.getCodePointBeforeCursor())) {
            needsPrecedingSpace = false;
        } else {
            needsPrecedingSpace = settingsValues.isUsuallyPrecededBySpace(codePoint);
        }

        if (needsPrecedingSpace) {
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues);
        }

        if (tryPerformDoubleSpacePeriod(event, inputTransaction)) {
            mSpaceState = SpaceState.DOUBLE;
            inputTransaction.setRequiresUpdateSuggestions();
            StatsUtils.onDoubleSpacePeriod();
        } else if (swapWeakSpace && trySwapSwapperAndSpace(event, inputTransaction)) {
            if(inputTransaction.mSpaceState == SpaceState.ANTIPHANTOM) {
                mSpaceState = SpaceState.ANTIPHANTOM;
            } else {
                mSpaceState = SpaceState.SWAP_PUNCTUATION;
            }
            inputTransaction.setRequiresUpdateSuggestions();
        } else if (Constants.CODE_SPACE == codePoint) {
            if (!mSuggestedWords.isPunctuationSuggestions()) {
                mSpaceState = SpaceState.WEAK;
            }

            startDoubleSpacePeriodCountdown(inputTransaction);
            if (wasComposingWord || mSuggestedWords.isEmpty()) {
                inputTransaction.setRequiresUpdateSuggestions();
            }

            if (!shouldAvoidSendingCode) {
                sendKeyCodePoint(settingsValues, codePoint);
            }
        } else {
            if ((SpaceState.PHANTOM == inputTransaction.mSpaceState
                    && settingsValues.isUsuallyFollowedBySpace(codePoint)
                    && !settingsValues.mInputAttributes.mIsUriField)
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

            boolean autoInsertSpaces = canInsertAutoSpace(settingsValues)
                    && (settingsValues.mAltSpacesMode >= Settings.SPACES_MODE_ALL)
                    && !mConnection.digitPrecedesCursor();

            boolean spacePrecedesCursor = !mConnection.cursorNotPrecededByWhitespace();

            boolean codeShouldBePrecededBySpace = settingsValues.isUsuallyPrecededBySpace(codePoint);
            // Disabled: this behavior is annoying in some circumstances (e.g. coding) and other
            // keyboards seem to not do it
                    //|| (codePoint == Constants.CODE_DOUBLE_QUOTE && !isInsideDoubleQuoteOrAfterDigit);

            codeShouldBePrecededBySpace = codeShouldBePrecededBySpace
                    && !symbolRequiringPrecedingSpaceShouldSkipPrecedingSpace(
                            codePoint, mConnection.getCodePointBeforeCursor(), inputTransaction);

            if(autoInsertSpaces
                    && codeShouldBePrecededBySpace
                    && !spacePrecedesCursor
            ) {
                sendKeyCodePoint(settingsValues, Constants.CODE_SPACE);
                mSpaceState = SpaceState.SYMBOL_PREFIX;

                if(inputTransaction.didAutoCorrect()) {
                    mLastComposedWord.mSeparatorString = " " + mLastComposedWord.mSeparatorString;
                }
            }

            final boolean fieldEmptyBeforeText =
                    (mConnection.getCodePointBeforeCursor() == Constants.NOT_A_CODE);

            sendKeyCodePoint(settingsValues, codePoint);

            boolean codeShouldBeFollowedBySpace = false;
            if(settingsValues.isUsuallyFollowedBySpace(codePoint)) {
                if(settingsValues.isOptionallyPrecededBySpace(codePoint)) {
                    // If it's optionally preceded by space, only auto-insert space if there is no space before cursor
                    // i.e. typing "hello :" will not auto insert space
                    // but typing "hello: " will auto insert space
                    codeShouldBeFollowedBySpace = !spacePrecedesCursor;
                } else {
                    // otherwise it's safe to follow with space
                    codeShouldBeFollowedBySpace = true;
                }
            } else if(settingsValues.isUsuallyFollowedBySpaceIffPrecededBySpace(codePoint)) {
                // For some symbols like - and & we only insert if its preceded by a space
                codeShouldBeFollowedBySpace = spacePrecedesCursor;
            } else if(codePoint == Constants.CODE_DOUBLE_QUOTE) {
                codeShouldBeFollowedBySpace = isInsideDoubleQuoteOrAfterDigit;
            }

            // Do not automatically insert a space if there is no text preceding the cursor
            // (e.g. if we are typing "!g", do not insert space after exclamation mark,
            // and if we are typing ":)", do not insert space after colon)
            codeShouldBeFollowedBySpace = codeShouldBeFollowedBySpace && !fieldEmptyBeforeText;

            if(autoInsertSpaces && codeShouldBeFollowedBySpace) {
                insertOrSetPhantomSpace(settingsValues);

                if(inputTransaction.didAutoCorrect()) {
                    mLastComposedWord.mSeparatorString = mLastComposedWord.mSeparatorString + " ";
                }
            }

            inputTransaction.setRequiresUpdateSuggestions();
        }

        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
    }

    /**
     * Handle a press on the backspace key.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     */
    private void handleBackspaceEvent(final Event event, final InputTransaction inputTransaction,
            final int currentKeyboardScriptId) {
        mSpaceState = SpaceState.NONE;
        mDeleteCount++;

        // In many cases after backspace, we need to update the shift state. Normally we need
        // to do this right away to avoid the shift state being out of date in case the user types
        // backspace then some other character very fast. However, in the case of backspace key
        // repeat, this can lead to flashiness when the cursor flies over positions where the
        // shift state should be updated, so if this is a key repeat, we update after a small delay.
        // Then again, even in the case of a key repeat, if the cursor is at start of text, it
        // can't go any further back, so we can update right away even if it's a key repeat.
        final int shiftUpdateKind =
                event.isKeyRepeat() && mConnection.getExpectedSelectionStart() > 0
                ? InputTransaction.SHIFT_UPDATE_LATER : InputTransaction.SHIFT_UPDATE_NOW;
        inputTransaction.requireShiftUpdate(shiftUpdateKind);

        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can remove the character at the current cursor position.
            // We also need to unlearn the original word that is now being corrected.
            unlearnWord(mWordComposer.getTypedWord(), inputTransaction.mSettingsValues,
                    Constants.EVENT_BACKSPACE);
            resetComposingWord(inputTransaction.mSettingsValues, false);
        }

        final boolean deleteWholeWords = event.isKeyRepeat()
                && inputTransaction.mSettingsValues.mBackspaceMode == Settings.BACKSPACE_MODE_WORDS;

        if (mWordComposer.isComposingWord() && !mConnection.hasSelection()) {
            if (mWordComposer.isBatchMode()) {
                final String rejectedSuggestion = mWordComposer.getTypedWord();
                mWordComposer.reset(true);
                mWordComposer.setRejectedBatchModeSuggestion(rejectedSuggestion);
                if (!TextUtils.isEmpty(rejectedSuggestion)) {
                    unlearnWord(rejectedSuggestion, inputTransaction.mSettingsValues,
                            Constants.EVENT_REJECTION);
                }
                StatsUtils.onBackspaceWordDelete(rejectedSuggestion.length());
            } else if(deleteWholeWords) {
                final String removedWord = mWordComposer.getTypedWord();
                mWordComposer.reset(true);
                if (!TextUtils.isEmpty(removedWord)) {
                    unlearnWord(removedWord, inputTransaction.mSettingsValues,
                            Constants.EVENT_BACKSPACE);
                }
            } else {
                mWordComposer.applyProcessedEvent(event);
                StatsUtils.onBackspacePressed(1);
            }
            if (mWordComposer.isComposingWord()) {
                setComposingTextInternal(getTextWithUnderline(mWordComposer.getTypedWord()), 1);
            } else {
                mConnection.commitText("", 1);
            }
            inputTransaction.setRequiresUpdateSuggestions();
        } else {
            if (mLastComposedWord.canRevertCommit()
                    && inputTransaction.mSettingsValues.mBackspaceUndoesAutocorrect) {
                final String lastComposedWord = mLastComposedWord.mTypedWord;
                revertCommit(inputTransaction, inputTransaction.mSettingsValues);
                StatsUtils.onRevertAutoCorrect(mImeHelper.getContext(), lastComposedWord);
                StatsUtils.onWordCommitUserTyped(lastComposedWord, mWordComposer.isBatchMode());
                // Restart suggestions when backspacing into a reverted word. This is required for
                // the final corrected word to be learned, as learning only occurs when suggestions
                // are active.
                //
                // Note: restartSuggestionsOnWordTouchedByCursor is already called for normal
                // (non-revert) backspace handling.
                if (inputTransaction.mSettingsValues.isSuggestionsEnabledPerUserSettings()
                        && inputTransaction.mSettingsValues.mSpacingAndPunctuations
                                .mCurrentLanguageHasSpaces
                        && !mConnection.isCursorFollowedByWordCharacter(
                                inputTransaction.mSettingsValues.mSpacingAndPunctuations)) {
                    final int spaceState = mSpaceState; // Need to preserve space state, which restart resets
                    restartSuggestionsOnWordTouchedByCursor(inputTransaction.mSettingsValues,
                            inputTransaction,
                            false /* forStartInput */, currentKeyboardScriptId);
                    mSpaceState = spaceState;
                }
                return;
            }
            if (mEnteredText != null && mConnection.sameAsTextBeforeCursor(mEnteredText)
                    && inputTransaction.mSettingsValues.mBackspaceDeletesInsertedText) {
                // Cancel multi-character input: remove the text we just entered.
                // This is triggered on backspace after a key that inputs multiple characters,
                // like the smiley key or the .com key.
                mConnection.deleteTextBeforeCursor(mEnteredText.length());
                StatsUtils.onDeleteMultiCharInput(mEnteredText.length());
                mEnteredText = null;
                // If we have mEnteredText, then we know that mHasUncommittedTypedChars == false.
                // In addition we know that spaceState is false, and that we should not be
                // reverting any autocorrect at this point. So we can safely return.
                return;
            }
            if (SpaceState.DOUBLE == inputTransaction.mSpaceState) {
                cancelDoubleSpacePeriodCountdown();
                if (mConnection.revertDoubleSpacePeriod(
                        inputTransaction.mSettingsValues.mSpacingAndPunctuations)) {
                    // No need to reset mSpaceState, it has already be done (that's why we
                    // receive it as a parameter)
                    inputTransaction.setRequiresUpdateSuggestions();
                    mWordComposer.setCapitalizedModeAtStartComposingTime(
                            WordComposer.CAPS_MODE_OFF);
                    StatsUtils.onRevertDoubleSpacePeriod();
                    return;
                }
            } else if (SpaceState.SWAP_PUNCTUATION == inputTransaction.mSpaceState) {
                if (mConnection.revertSwapPunctuation()) {
                    StatsUtils.onRevertSwapPunctuation();
                    // Likewise
                    return;
                }
            } else if (SpaceState.SYMBOL_PREFIX == inputTransaction.mSpaceState) {
                if (mConnection.revertPrefixSpace()) {
                    return;
                }
            }

            boolean hasUnlearnedWordBeingDeleted = false;

            boolean nowHasWordCharacter = false;

            // No cancelling of commit/double space/swap: we have a regular backspace.
            // We should backspace one char and restart suggestion if at the end of a word.
            if (mConnection.hasSelection()) {
                // If there is a selection, remove it.
                // We also need to unlearn the selected text.
                final CharSequence selection = mConnection.getSelectedText(0 /* 0 for no styles */);
                if (!TextUtils.isEmpty(selection)) {
                    unlearnWord(selection.toString(), inputTransaction.mSettingsValues,
                            Constants.EVENT_BACKSPACE);
                    hasUnlearnedWordBeingDeleted = true;
                }
                final int numCharsDeleted = mConnection.getExpectedSelectionEnd()
                        - mConnection.getExpectedSelectionStart();
                mConnection.setSelection(mConnection.getExpectedSelectionEnd(),
                        mConnection.getExpectedSelectionEnd());
                mConnection.deleteTextBeforeCursor(numCharsDeleted);
                StatsUtils.onBackspaceSelectedText(numCharsDeleted);
            } else {
                // There is no selection, just delete one character.
                if (inputTransaction.mSettingsValues.isBeforeJellyBean()
                        || inputTransaction.mSettingsValues.mInputAttributes.isTypeNull()
                        || Constants.NOT_A_CURSOR_POSITION
                                == mConnection.getExpectedSelectionEnd()) {
                    // There are three possible reasons to send a key event: either the field has
                    // type TYPE_NULL, in which case the keyboard should send events, or we are
                    // running in backward compatibility mode, or we don't know the cursor position.
                    // Before Jelly bean, the keyboard would simulate a hardware keyboard event on
                    // pressing enter or delete. This is bad for many reasons (there are race
                    // conditions with commits) but some applications are relying on this behavior
                    // so we continue to support it for older apps, so we retain this behavior if
                    // the app has target SDK < JellyBean.
                    // As for the case where we don't know the cursor position, it can happen
                    // because of bugs in the framework. But the framework should know, so the next
                    // best thing is to leave it to whatever it thinks is best.
                    sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL, 0);
                    int totalDeletedLength = 1;
                    if (mDeleteCount > Constants.DELETE_ACCELERATE_AT) {
                        // If this is an accelerated (i.e., double) deletion, then we need to
                        // consider unlearning here because we may have already reached
                        // the previous word, and will lose it after next deletion.
                        hasUnlearnedWordBeingDeleted |= unlearnWordBeingDeleted(
                                inputTransaction.mSettingsValues, currentKeyboardScriptId);
                        sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL, 0);
                        totalDeletedLength++;
                    }
                    StatsUtils.onBackspacePressed(totalDeletedLength);
                } else {
                    final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
                    if (codePointBeforeCursor == Constants.NOT_A_CODE) {
                        // HACK for backward compatibility with broken apps that haven't realized
                        // yet that hardware keyboards are not the only way of inputting text.
                        // Nothing to delete before the cursor. We should not do anything, but many
                        // broken apps expect something to happen in this case so that they can
                        // catch it and have their broken interface react. If you need the keyboard
                        // to do this, you're doing it wrong -- please fix your app.
                        //mConnection.deleteTextBeforeCursor(1);
                        sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL, 0);
                        // TODO: Add a new StatsUtils method onBackspaceWhenNoText()
                        return;
                    }

                    if (!inputTransaction.mSettingsValues.isWordCodePoint(codePointBeforeCursor)) {
                        nowHasWordCharacter = true;
                    }

                    String textDeleted = new String(Character.toChars(codePointBeforeCursor));
                    int lengthToDelete =
                            Character.isSupplementaryCodePoint(codePointBeforeCursor) ? 2 : 1;

                    // Handle emoji sequences (flags, etc)
                    CharSequence textBeforeCursor = mConnection.getTextBeforeCursor(deleteWholeWords ? 48 : 8, 0);
                    if (textBeforeCursor != null && textBeforeCursor.length() > 0) {
                        BreakIterator breakIterator;

                        if(deleteWholeWords) {
                            breakIterator = BreakIterator.getWordInstance();
                        } else {
                            breakIterator = BreakIterator.getCharacterInstance();
                        }
                        breakIterator.setText(textBeforeCursor.toString());
                        int end = breakIterator.last();
                        int start = breakIterator.previous();

                        if(deleteWholeWords && textBeforeCursor.subSequence(start, end).toString().equals(" ")) {
                            start = breakIterator.previous();
                        }

                        if (start != BreakIterator.DONE) {
                            lengthToDelete = end - start;
                            textDeleted = textBeforeCursor.subSequence(start, end).toString();
                        }
                    }

                    mConnection.deleteTextBeforeCursor(lengthToDelete);
                    int totalDeletedLength = lengthToDelete;
                    if (mDeleteCount > Constants.DELETE_ACCELERATE_AT) {
                        // If this is an accelerated (i.e., double) deletion, then we need to
                        // consider unlearning here because we may have already reached
                        // the previous word, and will lose it after next deletion.
                        hasUnlearnedWordBeingDeleted |= unlearnWordBeingDeleted(
                                inputTransaction.mSettingsValues, currentKeyboardScriptId);
                        final int codePointBeforeCursorToDeleteAgain =
                                mConnection.getCodePointBeforeCursor();
                        if (codePointBeforeCursorToDeleteAgain != Constants.NOT_A_CODE) {
                            final int lengthToDeleteAgain = Character.isSupplementaryCodePoint(
                                    codePointBeforeCursorToDeleteAgain) ? 2 : 1;
                            mConnection.deleteTextBeforeCursor(lengthToDeleteAgain);
                            totalDeletedLength += lengthToDeleteAgain;
                        }
                    }
                    StatsUtils.onBackspacePressed(totalDeletedLength);
                }
            }
            if (!hasUnlearnedWordBeingDeleted) {
                // Consider unlearning the word being deleted (if we have not done so already).
                unlearnWordBeingDeleted(
                        inputTransaction.mSettingsValues, currentKeyboardScriptId);
            }

            nowHasWordCharacter = nowHasWordCharacter && mConnection.isCursorPrecededByWordCharacter(
                    inputTransaction.mSettingsValues.mSpacingAndPunctuations);

            if (mConnection.hasSlowInputConnection()) {
                mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
            } else if (inputTransaction.mSettingsValues.isSuggestionsEnabledPerUserSettings()
                    && inputTransaction.mSettingsValues.mSpacingAndPunctuations
                            .mCurrentLanguageHasSpaces
                    && (
                            !mConnection.isCursorFollowedByWordCharacter(inputTransaction.mSettingsValues.mSpacingAndPunctuations)
                                    || nowHasWordCharacter
                    )
            ) {
                restartSuggestionsOnWordTouchedByCursor(inputTransaction.mSettingsValues, inputTransaction,
                        false /* forStartInput */, currentKeyboardScriptId);
            }
        }
    }

    String getWordAtCursor(final SettingsValues settingsValues, final int currentKeyboardScriptId) {
        if (!mConnection.hasSelection()
                && settingsValues.isSuggestionsEnabledPerUserSettings()
                && settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces) {
            final TextRange range = mConnection.getWordRangeAtCursor(
                    settingsValues.mSpacingAndPunctuations,
                    currentKeyboardScriptId, true);
            if (range != null) {
                return range.mWord.toString();
            }
        }
        return "";
    }

    boolean unlearnWordBeingDeleted(
            final SettingsValues settingsValues, final int currentKeyboardScriptId) {
        if (mConnection.hasSlowInputConnection()) {
            // TODO: Refactor unlearning so that it does not incur any extra calls
            // to the InputConnection. That way it can still be performed on a slow
            // InputConnection.
            Log.w(TAG, "Skipping unlearning due to slow InputConnection.");
            return false;
        }
        // If we just started backspacing to delete a previous word (but have not
        // entered the composing state yet), unlearn the word.
        // TODO: Consider tracking whether or not this word was typed by the user.
        if (!mConnection.isCursorFollowedByWordCharacter(settingsValues.mSpacingAndPunctuations)) {
            final String wordBeingDeleted = getWordAtCursor(
                    settingsValues, currentKeyboardScriptId);
            if (!TextUtils.isEmpty(wordBeingDeleted)) {
                unlearnWord(wordBeingDeleted, settingsValues, Constants.EVENT_BACKSPACE);
                return true;
            }
        }
        return false;
    }

    void unlearnWord(final String word, final SettingsValues settingsValues, final int eventType) {
        final NgramContext ngramContext = mConnection.getNgramContextFromNthPreviousWord(
            settingsValues.mSpacingAndPunctuations, 2);

        final long timeStampInSeconds = TimeUnit.MILLISECONDS.toSeconds(
            System.currentTimeMillis());

        mIme.removeFromHistory(word, ngramContext, timeStampInSeconds, eventType);
        mDictionaryFacilitator.unlearnFromUserHistory(
                word, ngramContext, timeStampInSeconds, eventType);

        StatsUtils.onWordUnlearned(mImeHelper.getContext(), word);

        // TODO
        //final NgramContext ngramContext1 = mConnection.getNgramContextFromNthPreviousWord(
        //        settingsValues.mSpacingAndPunctuations, 1);
        // FIXME: For some reason, 2 is the right value some times and 1 is the right value at other times.
        // To make sure it's deleted from history, we just call it with both and one of them should work
        //mDictionaryFacilitator.unlearnFromUserHistory(
        //        word, ngramContext1, timeStampInSeconds, eventType);
    }

    /**
     * Swap a space with a space-swapping punctuation sign.
     *
     * This method will check that there are two characters before the cursor and that the first
     * one is a space before it does the actual swapping.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     * @return true if the swap has been performed, false if it was prevented by preliminary checks.
     */
    private boolean trySwapSwapperAndSpace(final Event event,
            final InputTransaction inputTransaction) {
        final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
        if (Constants.CODE_SPACE != codePointBeforeCursor) {
            return false;
        }
        final boolean isWritingSchema = event.mCodePoint == '/'
                && mConnection.isPotentiallyWritingSchema();
        mConnection.deleteTextBeforeCursor(1);

        boolean stripSpace = inputTransaction.mSettingsValues.mInputAttributes.mIsUriField || isWritingSchema;

        final String text = event.getTextToCommit() +
                (stripSpace ? "" : " ");

        mConnection.commitText(text, 1);
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
        return true;
    }

    /*
     * Special case for symbols that are usually preceded by space.
     * Given the codepoint and the prior codepoint (skipping over a space if one is there),
     * it will return true if the preceding space should be skipped.
     *
     * This is mainly applicable for French question marks, so that typing ...??!? would not result
     * in ... ? ? ! ?
     */
    private boolean symbolRequiringPrecedingSpaceShouldSkipPrecedingSpace(final int codePoint,
          final int priorCodePoint, final InputTransaction inputTransaction) {

        if(!inputTransaction.mSettingsValues.isUsuallyPrecededBySpace(codePoint)) return false;
        if(!inputTransaction.mSettingsValues.isUsuallyFollowedBySpace(priorCodePoint)) return false;

        return (codePoint == priorCodePoint)
                || inputTransaction.mSettingsValues.mSpacingAndPunctuations.isSentenceTerminator(priorCodePoint);
    }

    /*
     * Strip a trailing space if necessary and returns whether it's a swap weak space situation.
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     * @return whether we should swap the space instead of removing it.
     */
    private boolean tryStripSpaceAndReturnWhetherShouldSwapInstead(final Event event,
            final InputTransaction inputTransaction) {
        final int codePoint = event.mCodePoint;
        final boolean isFromSuggestionStrip = event.isSuggestionStripPress();
        if (Constants.CODE_ENTER == codePoint && (
                (SpaceState.SWAP_PUNCTUATION == inputTransaction.mSpaceState) ||
                (SpaceState.ANTIPHANTOM == inputTransaction.mSpaceState)
        )) {
            mConnection.removeTrailingSpace();
            return false;
        }
        if ((SpaceState.WEAK == inputTransaction.mSpaceState
                || SpaceState.SWAP_PUNCTUATION == inputTransaction.mSpaceState)
                && isFromSuggestionStrip) {
            if (inputTransaction.mSettingsValues.isUsuallyPrecededBySpace(codePoint)) {
                return false;
            }
            if (inputTransaction.mSettingsValues.isUsuallyFollowedBySpace(codePoint)) {
                return true;
            }
            mConnection.removeTrailingSpace();
        }

        final boolean isInsideDoubleQuoteOrAfterDigit = Constants.CODE_DOUBLE_QUOTE == codePoint
                && mConnection.isInsideDoubleQuoteOrAfterDigit();

        final boolean isPotentiallyWritingSchema = codePoint == '/'
                && mConnection.isPotentiallyWritingSchema();


        final boolean settingsPermitSwapping =
                inputTransaction.mSettingsValues.mAltSpacesMode >= Settings.SPACES_MODE_ALL
                        || (inputTransaction.mSettingsValues.mAltSpacesMode >= Settings.SPACES_MODE_SUGGESTIONS
                                && inputTransaction.mSpaceState == SpaceState.ANTIPHANTOM);

        // If we are typing a bunch of dashes, strip the space instead of padding them with spaces
        if(inputTransaction.mSpaceState == SpaceState.ANTIPHANTOM
                && inputTransaction.mSettingsValues.isUsuallyFollowedBySpaceIffPrecededBySpace(codePoint)
                && codePoint == mConnection.getNthCodePointBeforeCursor(1)
        ) {
            mConnection.removeTrailingSpace();
            return false;
        }

        // Text field either needs to be fit for automatic spaces, or be a uri field
        // (in the case of uri field, we will strip trailing space without adding it back
        // in trySwapSwapperAndSpace)
        // TODO: Maybe just do mConnection.removeTrailingSpace() here in case of uri field
        final boolean textFieldFitForSwapping =
                inputTransaction.mSettingsValues.shouldInsertSpacesAutomatically()
                    || inputTransaction.mSettingsValues.mInputAttributes.mIsUriField;

        // Character needs to be usually followed by space (eg . , ? !)
        // or be an ending quotation mark, or be a schema to strip the space in case of https: //
        // TODO: Maybe just do mConnection.removeTrailingSpace() here in case of schema
        boolean characterFitForSwapping = (
                inputTransaction.mSettingsValues.isUsuallyFollowedBySpace(codePoint)
                    || isInsideDoubleQuoteOrAfterDigit
                    || isPotentiallyWritingSchema
        );

        // If this codepoint is preceded by a space, we swap it only if the preceding space is
        // skippable, and if we are in antiphantom state.
        //
        // French example: if I type "je..", a space is automatically inserted, and I type a
        // question mark, the space will be swapped to produce "je..? "
        // If I type "je..", the space is automatically inserted, I tap space manually which unsets
        // antiphantom state, the space will NOT be swapped and will instead get "je.. ? "
        if(characterFitForSwapping
                && inputTransaction.mSettingsValues.isUsuallyPrecededBySpace(codePoint)) {

            characterFitForSwapping = symbolRequiringPrecedingSpaceShouldSkipPrecedingSpace(
                    codePoint,
                    mConnection.getNthCodePointBeforeCursor(1),
                    inputTransaction
            ) && inputTransaction.mSpaceState == SpaceState.ANTIPHANTOM;
        }

        // Don't swap space for : or ; ever, see #1436
        if(characterFitForSwapping
                && inputTransaction.mSettingsValues.isOptionallyPrecededBySpace(codePoint)) {
            characterFitForSwapping = false;
        }

        return settingsPermitSwapping && textFieldFitForSwapping && characterFitForSwapping;
    }

    public void startDoubleSpacePeriodCountdown(final InputTransaction inputTransaction) {
        mDoubleSpacePeriodCountdownStart = inputTransaction.mTimestamp;
    }

    public void cancelDoubleSpacePeriodCountdown() {
        mDoubleSpacePeriodCountdownStart = 0;
    }

    public boolean isDoubleSpacePeriodCountdownActive(final InputTransaction inputTransaction) {
        return inputTransaction.mTimestamp - mDoubleSpacePeriodCountdownStart
                < inputTransaction.mSettingsValues.mDoubleSpacePeriodTimeout;
    }

    /**
     * Apply the double-space-to-period transformation if applicable.
     *
     * The double-space-to-period transformation means that we replace two spaces with a
     * period-space sequence of characters. This typically happens when the user presses space
     * twice in a row quickly.
     * This method will check that the double-space-to-period is active in settings, that the
     * two spaces have been input close enough together, that the typed character is a space
     * and that the previous character allows for the transformation to take place. If all of
     * these conditions are fulfilled, this method applies the transformation and returns true.
     * Otherwise, it does nothing and returns false.
     *
     * @param event The event to handle.
     * @param inputTransaction The transaction in progress.
     * @return true if we applied the double-space-to-period transformation, false otherwise.
     */
    private boolean tryPerformDoubleSpacePeriod(final Event event,
            final InputTransaction inputTransaction) {
        // Check the setting, the typed character and the countdown. If any of the conditions is
        // not fulfilled, return false.
        if (!inputTransaction.mSettingsValues.mUseDoubleSpacePeriod
                || Constants.CODE_SPACE != event.mCodePoint
                || !isDoubleSpacePeriodCountdownActive(inputTransaction)) {
            return false;
        }
        // We only do this when we see one space and an accepted code point before the cursor.
        // The code point may be a surrogate pair but the space may not, so we need 3 chars.
        final CharSequence lastTwo = mConnection.getTextBeforeCursor(3, 0);
        if (null == lastTwo) return false;
        final int length = lastTwo.length();
        if (length < 2) return false;
        if (lastTwo.charAt(length - 1) != Constants.CODE_SPACE) {
            return false;
        }
        // We know there is a space in pos -1, and we have at least two chars. If we have only two
        // chars, isSurrogatePairs can't return true as charAt(1) is a space, so this is fine.
        final int firstCodePoint =
                Character.isSurrogatePair(lastTwo.charAt(0), lastTwo.charAt(1)) ?
                        Character.codePointAt(lastTwo, length - 3) : lastTwo.charAt(length - 2);
        if (canBeFollowedByDoubleSpacePeriod(firstCodePoint)) {
            cancelDoubleSpacePeriodCountdown();
            mConnection.deleteTextBeforeCursor(1);
            final String textToInsert = inputTransaction.mSettingsValues.mSpacingAndPunctuations
                    .mSentenceSeparatorAndSpace;
            mConnection.commitText(textToInsert, 1);
            inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW);
            inputTransaction.setRequiresUpdateSuggestions();
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
        // TODO: This should probably be a denylist rather than a allowlist.
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
        if (!mConnection.hasSelection() || !mRecapitalizeStatus.mIsEnabled()) {
            return; // No selection or recapitalize is disabled for now
        }
        final int selectionStart = mConnection.getExpectedSelectionStart();
        final int selectionEnd = mConnection.getExpectedSelectionEnd();
        final int numCharsSelected = selectionEnd - selectionStart;
        if (numCharsSelected > Constants.MAX_CHARACTERS_FOR_RECAPITALIZATION) {
            // We bail out if we have too many characters for performance reasons. We don't want
            // to suck possibly multiple-megabyte data.
            return;
        }
        // If we have a recapitalize in progress, use it; otherwise, start a new one.
        if (!mRecapitalizeStatus.isStarted()
                || !mRecapitalizeStatus.isSetAt(selectionStart, selectionEnd)) {
            final CharSequence selectedText =
                    mConnection.getSelectedText(0 /* flags, 0 for no styles */);
            if (TextUtils.isEmpty(selectedText)) return; // Race condition with the input connection
            mRecapitalizeStatus.start(selectionStart, selectionEnd, selectedText.toString(),
                    settingsValues.mLocale,
                    settingsValues.mSpacingAndPunctuations.mSortedWordSeparators);
            // We trim leading and trailing whitespace.
            mRecapitalizeStatus.trim();
        }
        mConnection.finishComposingText();
        mRecapitalizeStatus.rotate();
        mConnection.setSelection(selectionEnd, selectionEnd);
        mConnection.deleteTextBeforeCursor(numCharsSelected);
        mConnection.commitText(mRecapitalizeStatus.getRecapitalizedString(), 1); // This was originally 0, but we no longer support 0
        mConnection.send();
        mConnection.setSelection(mRecapitalizeStatus.getNewCursorStart(),
                mRecapitalizeStatus.getNewCursorEnd());
    }

    private void performAdditionToUserHistoryDictionary(final SettingsValues settingsValues,
            final String suggestion, @Nonnull final NgramContext ngramContext, final int importance) {
        // If correction is not enabled, we don't add words to the user history dictionary.
        // That's to avoid unintended additions in some sensitive fields, or fields that
        // expect to receive non-words.
        if (!settingsValues.mAutoCorrectionEnabledPerTextFieldSettings) return;
        if (!settingsValues.isPersonalizationEnabled()) return;
        if (settingsValues.mInputAttributes.mNoLearning) return;

        if (mConnection.hasSlowInputConnection()) {
            // Since we don't unlearn when the user backspaces on a slow InputConnection,
            // turn off learning to guard against adding typos that the user later deletes.
            Log.w(TAG, "Skipping learning due to slow InputConnection.");
            return;
        }

        if (TextUtils.isEmpty(suggestion)) return;
        final boolean wasAutoCapitalized =
                mWordComposer.wasAutoCapitalized() && !mWordComposer.isMostlyCaps();
        final long timeStampInSeconds = TimeUnit.MILLISECONDS.toSeconds(
                System.currentTimeMillis());

        StatsUtils.onWordLearned(mImeHelper.getContext(), suggestion);

        mIme.addToHistory(suggestion, wasAutoCapitalized,
                ngramContext, timeStampInSeconds, settingsValues.mBlockPotentiallyOffensive,
                importance);
    }

    private boolean ensureSuggestionStripCompleted(final SettingsValues settingsValues,
            final String separator) {
        return mIme.ensureSuggestionsCompleted();
    }

    public boolean resetComposingWord(
            final SettingsValues settingsValues,
            final boolean useAfter
    ) {
        resetEntireInputState(mConnection.getExpectedSelectionStart(),
                mConnection.getExpectedSelectionEnd(), true /* clearSuggestionStrip */);
        //mSpaceState = SpaceState.NONE;
        if (settingsValues.isBrokenByRecorrection()
                // Recorrection is not supported in languages without spaces because we don't know
                // how to segment them yet.
                || !settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                // If we are currently in a batch input, we must not resume suggestions, or the result
                // of the batch input will replace the new composition. This may happen in the corner case
                // that the app moves the cursor on its own accord during a batch input.
                || mInputLogicHandler.isInBatchInput()
                // If the cursor is not touching a word, or if there is a selection, return right away.
                || mConnection.hasSelection()
                // If we don't know the cursor location, return.
                || mConnection.getExpectedSelectionStart() < 0) {
            //mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
            return false;
        }
        final int expectedCursorPosition = mConnection.getExpectedSelectionStart();
        final int actualCursorPosition = mConnection.getExtractedSelectionStart();
        if(actualCursorPosition != -1 && actualCursorPosition != expectedCursorPosition) {
            Log.e(TAG, "ResetComposingWord: cursors don't match! Expected: " + expectedCursorPosition + ", actual: " + actualCursorPosition);
            mConnection.tryExtractCursorPosition();
            return false;
        }
        if (!mConnection.isCursorTouchingWord(settingsValues.mSpacingAndPunctuations,
                InputLogic.COMPOSITION_TEXT_AFTER /* checkTextAfter */)) {
            resetEntireInputState(
                    mConnection.getExpectedSelectionStart(),
                    mConnection.getExpectedSelectionEnd(),
                    true
            );

            return false;

            //Log.d(TAG, "ComposingText1 [" + mConnection.getComposingTextForDebug() + "] , TypedWord [" + mWordComposer.getTypedWord() + "]");

            // Show predictions.
            //mWordComposer.setCapitalizedModeAtStartComposingTime(WordComposer.CAPS_MODE_OFF);
            //postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING);
            //return;
        }

        final int currentKeyboardScriptId = mImeHelper.getCurrentKeyboardScriptId();

        final TextRange range = mConnection.getWordRangeAtCursor(
                settingsValues.mSpacingAndPunctuations, currentKeyboardScriptId, useAfter);

        if (null == range) {
            return false; // Happens if we don't have an input connection at all
        }

        if (range.length() <= 0) {
            // Race condition, or touching a word in a non-supported script.
            //mIme.setNeutralSuggestionStrip();
            return false;
        }
        // If for some strange reason (editor bug or so) we measure the text before the cursor as
        // longer than what the entire text is supposed to be, the safe thing to do is bail out.
        if (range.mHasUrlSpans) {
            return false; // If there are links, we don't resume suggestions. Making
        }
        // edits to a linkified text through batch commands would ruin the URL spans, and unless
        // we take very complicated steps to preserve the whole link, we can't do things right so
        // we just do not resume because it's safer.
        final int numberOfCharsInWordBeforeCursor = range.getNumberOfCharsInWordBeforeCursor();
        if (numberOfCharsInWordBeforeCursor > expectedCursorPosition) {
            return false;
        }

        final String typedWordString = range.mWord.toString();

        final int[] codePoints = StringUtils.toCodePointArray(typedWordString);
        if(!mWordComposer.setComposingWord(codePoints,
                mImeHelper.getCodepointCoordinates(codePoints))) {
            return false;
        }
        mWordComposer.setCursorPositionWithinWord(
                typedWordString.codePointCount(0, numberOfCharsInWordBeforeCursor));
        //if (forStartInput) {
        //    mConnection.maybeMoveTheCursorAroundAndRestoreToWorkaroundABug();
        //}
        mConnection.setComposingRegion(expectedCursorPosition - numberOfCharsInWordBeforeCursor,
                expectedCursorPosition + range.getNumberOfCharsInWordAfterCursor(), typedWordString);

        mConnection.send();
        return true;
    }

    /**
     * Check if the cursor is touching a word. If so, restart suggestions on this word, else
     * do nothing.
     *
     * @param settingsValues the current values of the settings.
     * @param forStartInput whether we're doing this in answer to starting the input (as opposed
     *   to a cursor move, for example). In ICS, there is a platform bug that we need to work
     *   around only when we come here at input start time.
     */
    public void restartSuggestionsOnWordTouchedByCursor(final SettingsValues settingsValues,
            final InputTransaction inputTransaction,
            final boolean forStartInput,
            // TODO: remove this argument, put it into settingsValues
            final int currentKeyboardScriptId) {
        mConnection.send();
        mSpaceState = SpaceState.NONE;
        // HACK: We may want to special-case some apps that exhibit bad behavior in case of
        // recorrection. This is a temporary, stopgap measure that will be removed later.
        // TODO: remove this.
        if (settingsValues.isBrokenByRecorrection()
        // Recorrection is not supported in languages without spaces because we don't know
        // how to segment them yet.
                || !settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
        // If no suggestions are requested, don't try restarting suggestions.
                || !settingsValues.needsToLookupSuggestions()
        // If we are currently in a batch input, we must not resume suggestions, or the result
        // of the batch input will replace the new composition. This may happen in the corner case
        // that the app moves the cursor on its own accord during a batch input.
                || mInputLogicHandler.isInBatchInput()
        // If the cursor is not touching a word, or if there is a selection, return right away.
                || mConnection.hasSelection()
        // If we don't know the cursor location, return.
                || mConnection.getExpectedSelectionStart() < 0) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
            return;
        }

        resetComposingWord(settingsValues, true);

        if(inputTransaction != null) {
            inputTransaction.setRequiresUpdateSuggestions();
        } else {
            mIme.updateSuggestions(SuggestedWords.INPUT_STYLE_TYPING);
        }
    }

    /**
     * Reverts a previous commit with auto-correction.
     *
     * This is triggered upon pressing backspace just after a commit with auto-correction.
     *
     * @param inputTransaction The transaction in progress.
     * @param settingsValues the current values of the settings.
     */
    private void revertCommit(final InputTransaction inputTransaction,
            final SettingsValues settingsValues) {
        final CharSequence originallyTypedWord = mLastComposedWord.mTypedWord;
        final String originallyTypedWordString =
                originallyTypedWord != null ? originallyTypedWord.toString() : "";
        final CharSequence committedWord = mLastComposedWord.mCommittedWord;
        final String committedWordString = committedWord.toString();
        final int cancelLength = committedWord.length();
        final String separatorString = mLastComposedWord.mSeparatorString;
        // If our separator is a space, we won't actually commit it,
        // but set the space state to PHANTOM so that a space will be inserted
        // on the next keypress
        final boolean usePhantomSpace = separatorString.equals(Constants.STRING_SPACE);
        // We want java chars, not codepoints for the following.
        int separatorLength = separatorString.length();
        // TODO: should we check our saved separator against the actual contents of the text view?
        final int deleteLength = cancelLength + separatorLength;
        if (DebugFlags.DEBUG_ENABLED) {
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
        mConnection.deleteTextBeforeCursor(deleteLength);
        if (!TextUtils.isEmpty(committedWord)) {
            unlearnWord(committedWordString, inputTransaction.mSettingsValues,
                    Constants.EVENT_REVERT);
        }
        final String stringToCommit = originallyTypedWord +
                (usePhantomSpace ? "" : separatorString);
        final SpannableString textToCommit = new SpannableString(stringToCommit);
        if (committedWord instanceof SpannableString) {
            final SpannableString committedWordWithSuggestionSpans = (SpannableString)committedWord;
            final Object[] spans = committedWordWithSuggestionSpans.getSpans(0,
                    committedWord.length(), Object.class);
            final int lastCharIndex = textToCommit.length() - 1;
            // We will collect all suggestions in the following array.
            final ArrayList<String> suggestions = new ArrayList<>();
            // First, add the committed word to the list of suggestions.
            suggestions.add(committedWordString);
            for (final Object span : spans) {
                // If this is a suggestion span, we check that the word is not the committed word.
                // That should mostly be the case.
                // Given this, we add it to the list of suggestions, otherwise we discard it.
                if (span instanceof SuggestionSpan) {
                    final SuggestionSpan suggestionSpan = (SuggestionSpan)span;
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
            textToCommit.setSpan(
                    new SuggestionSpan(
                            mImeHelper.getContext(),
                            inputTransaction.mSettingsValues.mLocale,
                            suggestions.toArray(new String[suggestions.size()]),
                            0 /* flags */,
                            null /* notificationTargetClass */
                    ),
                    0 /* start */,
                    lastCharIndex /* end */,
                    0 /* flags */
            );
        }

        if (inputTransaction.mSettingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces) {
            mConnection.commitText(textToCommit, 1);
            if (usePhantomSpace) {
                if(settingsValues.mAltSpacesMode != Settings.SPACES_MODE_NONE) mSpaceState = SpaceState.PHANTOM;
            }
        } else {
            // For languages without spaces, we revert the typed string but the cursor is flush
            // with the typed word, so we need to resume suggestions right away.
            final int[] codePoints = StringUtils.toCodePointArray(stringToCommit);
            mWordComposer.setComposingWord(codePoints,
                    mImeHelper.getCodepointCoordinates(codePoints));
            setComposingTextInternal(getTextWithUnderline(textToCommit.toString()), 1);
        }
        // Don't restart suggestion yet. We'll restart if the user deletes the separator.
        mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;

        // We have a separator between the word and the cursor: we should show predictions.
        inputTransaction.setRequiresUpdateSuggestions();
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
        if (keyboardShiftMode != WordComposer.CAPS_MODE_AUTO_SHIFTED) {
            return keyboardShiftMode;
        }
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
        if (!settingsValues.mAutoCap) {
            return Constants.TextUtils.CAP_MODE_OFF;
        }

        final EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) {
            return Constants.TextUtils.CAP_MODE_OFF;
        }
        final int inputType = ei.inputType;
        // Warning: this depends on mSpaceState, which may not be the most current value. If
        // mSpaceState gets updated later, whoever called this may need to be told about it.
        try {
            return mConnection.getCursorCapsMode(inputType, settingsValues.mSpacingAndPunctuations,
                    SpaceState.PHANTOM == mSpaceState);
        } catch(StringIndexOutOfBoundsException ex) {
            BugViewerKt.throwIfDebug(ex);
            return Constants.TextUtils.CAP_MODE_OFF;
        }
    }

    public int getCurrentRecapitalizeState() {
        if (!mRecapitalizeStatus.isStarted()
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
        return mImeHelper.getCurrentEditorInfo();
    }

    /**
     * Get n-gram context from the nth previous word before the cursor as context
     * for the suggestion process.
     * @param spacingAndPunctuations the current spacing and punctuations settings.
     * @param nthPreviousWord reverse index of the word to get (1-indexed)
     * @return the information of previous words
     */
    public NgramContext getNgramContextFromNthPreviousWordForSuggestion(
            final SpacingAndPunctuations spacingAndPunctuations, final int nthPreviousWord) {
        if (spacingAndPunctuations.mCurrentLanguageHasSpaces) {
            // If we are typing in a language with spaces we can just look up the previous
            // word information from textview.
            return mConnection.getNgramContextFromNthPreviousWord(
                    spacingAndPunctuations, nthPreviousWord);
        }
        if (LastComposedWord.NOT_A_COMPOSED_WORD == mLastComposedWord) {
            return NgramContext.BEGINNING_OF_SENTENCE;
        }
        return new NgramContext(new NgramContext.WordInfo(
                mLastComposedWord.mCommittedWord.toString()));
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
        }
        return text;
    }

    /**
     * Resets the whole input state to the starting state.
     *
     * This will clear the composing word, reset the last composed word, clear the suggestion
     * strip and tell the input connection about it so that it can refresh its caches.
     *
     * @param newSelStart the new selection start, in java characters.
     * @param newSelEnd the new selection end, in java characters.
     * @param clearSuggestionStrip whether this method should clear the suggestion strip.
     */
    // TODO: how is this different from startInput ?!
    private void resetEntireInputState(final int newSelStart, final int newSelEnd,
            final boolean clearSuggestionStrip) {
        final boolean shouldFinishComposition = mWordComposer.isComposingWord();
        resetComposingState(true /* alsoResetLastComposedWord */);
        if (clearSuggestionStrip) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip();
        }
        mConnection.resetCachesUponCursorMoveAndReturnSuccess(newSelStart, newSelEnd,
                shouldFinishComposition);

        mImeHelper.getKeyboardSwitcher().requestUpdatingShiftState(getCurrentAutoCapsState(Settings.getInstance().getCurrent()));
    }

    /**
     * Resets only the composing state.
     *
     * Compare #resetEntireInputState, which also clears the suggestion strip and resets the
     * input connection caches. This only deals with the composing state.
     *
     * @param alsoResetLastComposedWord whether to also reset the last composed word.
     */
    private void resetComposingState(final boolean alsoResetLastComposedWord) {
        mWordComposer.reset(alsoResetLastComposedWord);
        if (alsoResetLastComposedWord) {
            mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
        }
    }

    /**
     * @return the {@link Locale} of the {@link #mDictionaryFacilitator} if available. Otherwise
     * {@link Locale#ROOT}.
     */
    @Nonnull
    private Locale getDictionaryFacilitatorLocale() {
        return mDictionaryFacilitator != null ? mDictionaryFacilitator.getPrimaryLocale() : Locale.ROOT;
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
    // TODO: Shouldn't this go in some *Utils class instead?
    private CharSequence getTextWithUnderline(final String text) {
        // TODO: Locale should be determined based on context and the text given.
        return (mIsAutoCorrectionIndicatorOn && mConnection.useAutoCorrectIndicator())
                ? SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(
                    mImeHelper.getContext(), text, getDictionaryFacilitatorLocale())
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
    public boolean sendDownUpKeyEvent(final int keyCode, final int metaState) {
        final long eventTime = SystemClock.uptimeMillis();
        boolean a = mConnection.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        boolean b = mConnection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));

        return a || b;
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
    // TODO: replace these two parameters with an InputTransaction
    private void sendKeyCodePoint(final SettingsValues settingsValues, final int codePoint) {
        // In some (rare?) cases KeyEvent will not work because the view isn't focused ( https://github.com/futo-org/android-keyboard/issues/938 )
        // In other cases commitText won't work (Spotify login code in Grayjay)
        // We try sending keyEvent first and fallback to commitText. This might cause double numeric
        // inputs in InputConnections that return false but commit text anyway
        if (codePoint >= '0' && codePoint <= '9') {
            if(sendDownUpKeyEvent(codePoint - '0' + KeyEvent.KEYCODE_0, 0)) {
                return;
            }
        }

        // TODO: we should do this also when the editor has TYPE_NULL
        if (Constants.CODE_ENTER == codePoint && settingsValues.isBeforeJellyBean()) {
            // Backward compatibility mode. Before Jelly bean, the keyboard would simulate
            // a hardware keyboard event on pressing enter or delete. This is bad for many
            // reasons (there are race conditions with commits) but some applications are
            // relying on this behavior so we continue to support it for older apps.
            sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER, 0);
        } else {
            mConnection.commitText(StringUtils.newSingleCodePointString(codePoint), 1);
        }
    }

    /**
     * Insert an automatic space, if the options allow it.
     *
     * This checks the options and the text before the cursor are appropriate before inserting
     * an automatic space.
     *
     * @param settingsValues the current values of the settings.
     */
    private void insertAutomaticSpaceIfOptionsAndTextAllow(final SettingsValues settingsValues) {
        if (settingsValues.shouldInsertSpacesAutomatically()
                && settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                && !mConnection.textBeforeCursorLooksLikeURL()) {
            sendKeyCodePoint(settingsValues, Constants.CODE_SPACE);
        }
    }

    private boolean canInsertAutoSpace(final SettingsValues settingsValues) {
        return (settingsValues.mAltSpacesMode >= Settings.SPACES_MODE_SUGGESTIONS)
                && settingsValues.shouldInsertSpacesAutomatically()
                && settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                && !settingsValues.mInputAttributes.mIsUriField
                && !mConnection.textBeforeCursorLooksLikeURL();
    }

    private void insertOrSetPhantomSpace(final SettingsValues settingsValues) {
        if(canInsertAutoSpace(settingsValues)
                // If the next character is usually followed by space, it likely doesn't make sense
                // to put a space immediately (e.g. cursor at "example sentence|.")
                && !settingsValues.isUsuallyFollowedBySpace(mConnection.getCodePointAfterCursor())){

            // If a space already follows the cursor it should be removed then re-inserted, so that
            // the cursor is after the space.
            if(mConnection.spaceFollowsCursor()) mConnection.removeLeadingSpace();

            mSpaceState = SpaceState.ANTIPHANTOM;
            sendKeyCodePoint(settingsValues, Constants.CODE_SPACE);
        } else if(settingsValues.mAltSpacesMode != Settings.SPACES_MODE_NONE) {
            mSpaceState = SpaceState.PHANTOM;
        }
    }


    /**
     * Do the final processing after a batch input has ended. This commits the word to the editor.
     * @param settingsValues the current values of the settings.
     * @param suggestedWords suggestedWords to use.
     */
    public void onUpdateTailBatchInputCompleted(final SettingsValues settingsValues,
            final SuggestedWords suggestedWords, final KeyboardSwitcher keyboardSwitcher) {
        final String batchInputText = suggestedWords.isEmpty() ? null : suggestedWords.getWord(0);
        if (TextUtils.isEmpty(batchInputText)) {
            return;
        }
        mConnection.beginBatchEdit();
        if (SpaceState.PHANTOM == mSpaceState) {
            if(!mConnection.spacePrecedesComposingText())
                insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues);
        }
        mWordComposer.setBatchInputWord(batchInputText);
        setComposingTextInternal(batchInputText, 1);
        mConnection.endBatchEdit();
        mConnection.send();
        // Space state must be updated before calling updateShiftState
        if(settingsValues.mAltSpacesMode != Settings.SPACES_MODE_NONE) mSpaceState = SpaceState.PHANTOM;
        keyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(settingsValues));

        updateUiInputState();
    }

    private void updateUiInputState() {
        final CharSequence textBeforeCursor = mConnection.getTextBeforeCursor(1, 0);
        mImeHelper.updateUiInputState(textBeforeCursor != null
                && textBeforeCursor.length() == 0);
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
    public void commitTyped(final SettingsValues settingsValues, final String separatorString) {
        if (!mWordComposer.isComposingWord()) return;
        final String typedWord = mWordComposer.getTypedWord();
        if (typedWord.length() > 0) {
            final boolean isBatchMode = mWordComposer.isBatchMode();
            commitChosenWord(settingsValues, typedWord,
                    LastComposedWord.COMMIT_TYPE_USER_TYPED_WORD, separatorString, -1);
            StatsUtils.onWordCommitUserTyped(typedWord, isBatchMode);
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
    private boolean commitCurrentAutoCorrection(final SettingsValues settingsValues,
            final String separator) {
        // Complete any pending suggestions query first
        if(!ensureSuggestionStripCompleted(settingsValues, separator)) {
            mConnection.finishComposingText();
            mWordComposer.reset(true);
            return false;
        }

        final SuggestedWordInfo autoCorrectionOrNull = mWordComposer.getAutoCorrectionOrNull();
        final String typedWord = mWordComposer.getTypedWord();
        final String stringToCommit = (autoCorrectionOrNull != null)
                ? autoCorrectionOrNull.mWord : typedWord;
        if (stringToCommit != null) {
            if (TextUtils.isEmpty(typedWord)) {
                throw new RuntimeException("We have an auto-correction but the typed word "
                        + "is empty? Impossible! I must commit suicide.");
            }
            final boolean isBatchMode = mWordComposer.isBatchMode();
            commitChosenWord(settingsValues, stringToCommit,
                    LastComposedWord.COMMIT_TYPE_DECIDED_WORD, separator, 0);
            if (!typedWord.equals(stringToCommit)) {
                // This will make the correction flash for a short while as a visual clue
                // to the user that auto-correction happened. It has no other effect; in particular
                // note that this won't affect the text inside the text field AT ALL: it only makes
                // the segment of text starting at the supplied index and running for the length
                // of the auto-correction flash. At this moment, the "typedWord" argument is
                // ignored by TextView.
                mConnection.commitCorrection(new CorrectionInfo(
                        mConnection.getExpectedSelectionEnd() - stringToCommit.length(),
                        "", stringToCommit));
                String prevWordsContext = (autoCorrectionOrNull != null)
                        ? autoCorrectionOrNull.mPrevWordsContext
                        : "";
                StatsUtils.onAutoCorrection(typedWord, stringToCommit, isBatchMode,
                        mDictionaryFacilitator, prevWordsContext);
                StatsUtils.onWordCommitAutoCorrect(stringToCommit, isBatchMode);
            } else {
                StatsUtils.onWordCommitUserTyped(stringToCommit, isBatchMode);
            }
        }
        return true;
    }

    /**
     * Commits the chosen word to the text field and saves it for later retrieval.
     *
     * @param settingsValues the current values of the settings.
     * @param chosenWord the word we want to commit.
     * @param commitType the type of the commit, as one of LastComposedWord.COMMIT_TYPE_*
     * @param separatorString the separator that's causing the commit, or NOT_A_SEPARATOR if none.
     */
    private void commitChosenWord(final SettingsValues settingsValues, final String chosenWord,
            final int commitType, final String separatorString, final int importance) {
        long startTimeMillis = 0;
        if (DebugFlags.DEBUG_ENABLED) {
            startTimeMillis = System.currentTimeMillis();
            Log.d(TAG, "commitChosenWord() : [" + chosenWord + "]");
        }
        final SuggestedWords suggestedWords = mSuggestedWords;
        // TODO: Locale should be determined based on context and the text given.
        final Locale locale = getDictionaryFacilitatorLocale();
        final CharSequence chosenWordWithSuggestions = chosenWord;
        // b/21926256
        //      SuggestionSpanUtils.getTextWithSuggestionSpan(mLatinIME, chosenWord,
        //                suggestedWords, locale);
        if (DebugFlags.DEBUG_ENABLED) {
            long runTimeMillis = System.currentTimeMillis() - startTimeMillis;
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "SuggestionSpanUtils.getTextWithSuggestionSpan()");
            startTimeMillis = System.currentTimeMillis();
        }
        // When we are composing word, get n-gram context from the 2nd previous word because the
        // 1st previous word is the word to be committed. Otherwise get n-gram context from the 1st
        // previous word.
        final NgramContext ngramContext = mConnection.getNgramContextFromNthPreviousWord(
                settingsValues.mSpacingAndPunctuations, mWordComposer.isComposingWord() ? 2 : 1);
        if (DebugFlags.DEBUG_ENABLED) {
            long runTimeMillis = System.currentTimeMillis() - startTimeMillis;
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "Connection.getNgramContextFromNthPreviousWord()");
            Log.d(TAG, "commitChosenWord() : NgramContext = " + ngramContext);
            startTimeMillis = System.currentTimeMillis();
        }
        mConnection.commitText(chosenWordWithSuggestions, 1);
        if (DebugFlags.DEBUG_ENABLED) {
            long runTimeMillis = System.currentTimeMillis() - startTimeMillis;
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "Connection.commitText");
            startTimeMillis = System.currentTimeMillis();
        }
        // Add the word to the user history dictionary
        mDictionaryFacilitator.onWordCommitted(chosenWord);
        performAdditionToUserHistoryDictionary(settingsValues, chosenWord, ngramContext, importance);
        if (DebugFlags.DEBUG_ENABLED) {
            long runTimeMillis = System.currentTimeMillis() - startTimeMillis;
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "performAdditionToUserHistoryDictionary()");
            startTimeMillis = System.currentTimeMillis();
        }
        // TODO: figure out here if this is an auto-correct or if the best word is actually
        // what user typed. Note: currently this is done much later in
        // LastComposedWord#didCommitTypedWord by string equality of the remembered
        // strings.
        mLastComposedWord = mWordComposer.commitWord(commitType,
                chosenWordWithSuggestions, separatorString, ngramContext);
        if (DebugFlags.DEBUG_ENABLED) {
            long runTimeMillis = System.currentTimeMillis() - startTimeMillis;
            Log.d(TAG, "commitChosenWord() : " + runTimeMillis + " ms to run "
                    + "WordComposer.commitWord()");
            startTimeMillis = System.currentTimeMillis();
        }
    }

    /**
     * Retry resetting caches in the rich input connection.
     *
     * When the editor can't be accessed we can't reset the caches, so we schedule a retry.
     * This method handles the retry, and re-schedules a new retry if we still can't access.
     * We only retry up to 5 times before giving up.
     *
     * @param tryResumeSuggestions Whether we should resume suggestions or not.
     * @param remainingTries How many times we may try again before giving up.
     * @return whether true if the caches were successfully reset, false otherwise.
     */
    public boolean retryResetCachesAndReturnSuccess(final boolean tryResumeSuggestions,
            final int remainingTries) {
        final boolean shouldFinishComposition = mConnection.hasSelection()
                || !mConnection.isCursorPositionKnown();
        if (!mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                mConnection.getExpectedSelectionStart(), mConnection.getExpectedSelectionEnd(),
                shouldFinishComposition)) {
            if (0 < remainingTries) {
                // TODO
                //handler.postResetCaches(tryResumeSuggestions, remainingTries - 1);
                return false;
            }
            // If remainingTries is 0, we should stop waiting for new tries, however we'll still
            // return true as we need to perform other tasks (for example, loading the keyboard).
        }
        mConnection.tryFixLyingCursorPosition();
        if (tryResumeSuggestions) {
            // TODO
            //handler.postResumeSuggestions(true /* shouldDelay */);
        }
        return true;
    }

    public void getSuggestedWords(final SettingsValues settingsValues,
            final Keyboard keyboard, final int keyboardShiftMode, final int inputStyle,
            final int sequenceNumber, final OnGetSuggestedWordsCallback callback) {
        mWordComposer.adviseCapitalizedModeBeforeFetchingSuggestions(
                getActualCapsMode(settingsValues, keyboardShiftMode));
        mSuggest.getSuggestedWords(mWordComposer,
                getNgramContextFromNthPreviousWordForSuggestion(
                        settingsValues.mSpacingAndPunctuations,
                        // Get the word on which we should search the bigrams. If we are composing
                        // a word, it's whatever is *before* the half-committed word in the buffer,
                        // hence 2; if we aren't, we should just skip whitespace if any, so 1.
                        mWordComposer.isComposingWord() ? 2 : 1),
                keyboard,
                new SettingsValuesForSuggestion(
                    settingsValues.mBlockPotentiallyOffensive,
                    settingsValues.mTransformerPredictionEnabled
                ),
                settingsValues.mAutoCorrectionEnabledPerUserSettings,
                inputStyle, sequenceNumber, callback);
    }

    /**
     * Used as an injection point for each call of
     * {@link RichInputConnection#setComposingText(CharSequence, int)}.
     *
     * @param newComposingText the composing text to be set
     * @param newCursorPosition the new cursor position
     */
    private void setComposingTextInternal(final CharSequence newComposingText,
            final int newCursorPosition) {
        mConnection.setComposingText(newComposingText, newCursorPosition);
    }

    /**
     * Which direction the selection should be expanded/contracted in via cursorLeft/Right methods
     * If true, the right side of the selection (the end) is fixed and the left side (start) gets
     * moved around, and vice versa.
     */
    private boolean isRightSidePointer = true;

    /**
     * Shifts the cursor/selection based on isRightSidePointer and the parameters
     * @param steps How many characters to step over, or the direction if stepOverWords
     * @param stepOverWords Whether to ignore the magnitude of steps and step over full words
     * @param select Whether or not to start/continue selection
     */
    private void cursorStep(int steps, boolean stepOverWords, boolean select) {
        if(stepOverWords) {
            steps = mConnection.getWordBoundarySteps(steps, isRightSidePointer);
        } else {
            steps = mConnection.getUnicodeSteps(steps, isRightSidePointer);
        }

        int cursor = isRightSidePointer ? mConnection.getExpectedSelectionStart() : mConnection.getExpectedSelectionEnd();
        int start = mConnection.getExpectedSelectionStart();
        int end = mConnection.getExpectedSelectionEnd();
        if(isRightSidePointer) {
            start += steps;
            cursor += steps;
        } else {
            end += steps;
            cursor += steps;
        }

        if (!select) {
            start = cursor;
            end = cursor;
        }

        start = Math.max(0, start);
        end = Math.max(0, end);

        mConnection.setSelection(start, end);
    }

    /**
     * Disables recapitalization
     */
    public void disableRecapitalization() {
        mRecapitalizeStatus.disable();
    }


    private void cursorLeftInternal(int steps, boolean stepOverWords, boolean select) {
        finishInput();

        if(!mConnection.hasSelection()) isRightSidePointer = true;

        cursorStep(-steps, stepOverWords, select);
    }

    private void cursorRightInternal(int steps, boolean stepOverWords, boolean select) {
        finishInput();

        if(!mConnection.hasSelection()) isRightSidePointer = false;

        cursorStep(steps, stepOverWords, select);
    }


    /**
     * Shifts the cursor left by a number of characters
     * @param steps How many characters to step over, or the direction if stepOverWords
     * @param stepOverWords Whether to ignore the magnitude of steps and step over full words
     * @param select Whether or not to start/continue selection
     */
    public void cursorLeft(int steps, boolean stepOverWords, boolean select) {
        final SettingsValues settingsValues = Settings.getInstance().getCurrent();
        steps = Math.abs(steps);
        if(!mConnection.hasCursorPosition() || settingsValues.mInputAttributes.mIsCodeField) {
            mConnection.finishComposingText();
            int meta = 0;
            if(stepOverWords) meta = meta | KeyEvent.META_CTRL_ON;
            if(select) meta = meta | KeyEvent.META_SHIFT_ON;

            mConnection.beginBatchEdit();
            for(int i=0; i<steps; i++)
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta);
            mConnection.endBatchEdit();
        } else {
            if(settingsValues.mIsRTL) {
                cursorRightInternal(steps, stepOverWords, select);
            } else {
                cursorLeftInternal(steps, stepOverWords, select);
            }
        }
    }

    /**
     * Shifts the cursor right by a number of characters
     * @param steps How many characters to step over, or the direction if stepOverWords
     * @param stepOverWords Whether to ignore the magnitude of steps and step over full words
     * @param select Whether or not to start/continue selection
     */
    public void cursorRight(int steps, boolean stepOverWords, boolean select) {
        final SettingsValues settingsValues = Settings.getInstance().getCurrent();
        steps = Math.abs(steps);
        if(!mConnection.hasCursorPosition() || settingsValues.mInputAttributes.mIsCodeField) {
            mConnection.finishComposingText();
            int meta = 0;
            if(stepOverWords) meta = meta | KeyEvent.META_CTRL_ON;
            if(select) meta = meta | KeyEvent.META_SHIFT_ON;

            mConnection.beginBatchEdit();
            for(int i=0; i<steps; i++)
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta);
            mConnection.endBatchEdit();
        } else {
            if(settingsValues.mIsRTL) {
                cursorLeftInternal(steps, stepOverWords, select);
            } else {
                cursorRightInternal(steps, stepOverWords, select);
            }
        }
    }
}
