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
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.SuggestionSpanUtils;
import com.android.inputmethod.event.EventInterpreter;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LastComposedWord;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.RichInputConnection;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.InputTypeUtils;
import com.android.inputmethod.latin.utils.LatinImeLoggerUtils;
import com.android.inputmethod.latin.utils.RecapitalizeStatus;
import com.android.inputmethod.latin.utils.StringUtils;
import com.android.inputmethod.research.ResearchLogger;

import java.util.TreeSet;

/**
 * This class manages the input logic.
 */
public final class InputLogic {
    private static final String TAG = InputLogic.class.getSimpleName();

    // TODO : Remove this member when we can.
    private final LatinIME mLatinIME;

    // TODO : make all these fields private as soon as possible.
    // Current space state of the input method. This can be any of the above constants.
    public int mSpaceState;
    // Never null
    public SuggestedWords mSuggestedWords = SuggestedWords.EMPTY;
    public Suggest mSuggest;
    // The event interpreter should never be null.
    public EventInterpreter mEventInterpreter;

    public LastComposedWord mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
    public final WordComposer mWordComposer;
    public final RichInputConnection mConnection;
    public final RecapitalizeStatus mRecapitalizeStatus = new RecapitalizeStatus();

    // Keep track of the last selection range to decide if we need to show word alternatives
    public int mLastSelectionStart = Constants.NOT_A_CURSOR_POSITION;
    public int mLastSelectionEnd = Constants.NOT_A_CURSOR_POSITION;

    public int mDeleteCount;
    private long mLastKeyTime;
    public final TreeSet<Long> mCurrentlyPressedHardwareKeys = CollectionUtils.newTreeSet();

    // Keeps track of most recently inserted text (multi-character key) for reverting
    public String mEnteredText;

    // TODO: This boolean is persistent state and causes large side effects at unexpected times.
    // Find a way to remove it for readability.
    public boolean mIsAutoCorrectionIndicatorOn;

    public InputLogic(final LatinIME latinIME) {
        mLatinIME = latinIME;
        mWordComposer = new WordComposer();
        mEventInterpreter = new EventInterpreter(latinIME);
        mConnection = new RichInputConnection(latinIME);
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
     * @param restarting whether input is starting in the same field as before.
     */
    public void startInput(final boolean restarting) {
    }

    /**
     * Clean up the input logic after input is finished.
     */
    public void finishInput() {
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
            // TODO: remove these three arguments
            final LatinIME.UIHandler handler, final KeyboardSwitcher keyboardSwitcher,
            final SubtypeSwitcher subtypeSwitcher) {
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onCodeInput(code, x, y);
        }
        final SettingsValues settingsValues = Settings.getInstance().getCurrent();
        final long when = SystemClock.uptimeMillis();
        if (code != Constants.CODE_DELETE
                || when > mLastKeyTime + Constants.LONG_PRESS_MILLISECONDS) {
            mDeleteCount = 0;
        }
        mLastKeyTime = when;
        mConnection.beginBatchEdit();
        final KeyboardSwitcher switcher = keyboardSwitcher;
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
            // Note: Calling back to the keyboard on Shift key is handled in
            // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
            final Keyboard currentKeyboard = switcher.getKeyboard();
            if (null != currentKeyboard && currentKeyboard.mId.isAlphabetKeyboard()) {
                // TODO: Instead of checking for alphabetic keyboard here, separate keycodes for
                // alphabetic shift and shift while in symbol layout.
                performRecapitalization(settingsValues, keyboardSwitcher);
            }
            break;
        case Constants.CODE_CAPSLOCK:
            // Note: Changing keyboard to shift lock state is handled in
            // {@link KeyboardSwitcher#onCodeInput(int)}.
            break;
        case Constants.CODE_SWITCH_ALPHA_SYMBOL:
            // Note: Calling back to the keyboard on symbol key is handled in
            // {@link #onPressKey(int,int,boolean)} and {@link #onReleaseKey(int,boolean)}.
            break;
        case Constants.CODE_SETTINGS:
            onSettingsKeyPressed();
            break;
        case Constants.CODE_SHORTCUT:
            subtypeSwitcher.switchToShortcutIME(mLatinIME);
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
                didAutoCorrect = handleNonSpecialCharacter(settingsValues,
                        Constants.CODE_ENTER, x, y, spaceState, keyboardSwitcher, handler);
            }
            break;
        case Constants.CODE_SHIFT_ENTER:
            didAutoCorrect = handleNonSpecialCharacter(settingsValues,
                    Constants.CODE_ENTER, x, y, spaceState, keyboardSwitcher, handler);
            break;
        default:
            didAutoCorrect = handleNonSpecialCharacter(settingsValues,
                    code, x, y, spaceState, keyboardSwitcher, handler);
            break;
        }
        switcher.onCodeInput(code);
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
            didAutoCorrect = handleSeparator(settingsValues, codePoint, x, y, spaceState,
                    keyboardSwitcher, handler);
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
                    resetEntireInputState(settingsValues, mLastSelectionStart, mLastSelectionEnd);
                } else {
                    commitTyped(LastComposedWord.NOT_A_SEPARATOR);
                }
            }
            final int keyX, keyY;
            final Keyboard keyboard = keyboardSwitcher.getKeyboard();
            if (keyboard != null && keyboard.hasProximityCharsCorrection(codePoint)) {
                keyX = x;
                keyY = y;
            } else {
                keyX = Constants.NOT_A_COORDINATE;
                keyY = Constants.NOT_A_COORDINATE;
            }
            handleNonSeparator(settingsValues, codePoint, keyX, keyY, spaceState,
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
            resetEntireInputState(settingsValues, mLastSelectionStart, mLastSelectionEnd);
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
                && settingsValues.isSuggestionsRequested(mLatinIME.mDisplayOrientation) &&
        // In languages with spaces, we only start composing a word when we are not already
        // touching a word. In languages without spaces, the above conditions are sufficient.
                (!mConnection.isCursorTouchingWord(settingsValues)
                        || !settingsValues.mCurrentLanguageHasSpaces)) {
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
            final MainKeyboardView mainKeyboardView = keyboardSwitcher.getMainKeyboardView();
            // TODO: We should reconsider which coordinate system should be used to represent
            // keyboard event.
            final int keyX = mainKeyboardView.getKeyX(x);
            final int keyY = mainKeyboardView.getKeyY(y);
            mWordComposer.add(codePoint, keyX, keyY);
            // If it's the first letter, make note of auto-caps state
            if (mWordComposer.size() == 1) {
                // We pass 1 to getPreviousWordForSuggestion because we were not composing a word
                // yet, so the word we want is the 1st word before the cursor.
                mWordComposer.setCapitalizedModeAndPreviousWordAtStartComposingTime(
                        getActualCapsMode(keyboardSwitcher),
                        getNthPreviousWordForSuggestion(settingsValues, 1 /* nthPreviousWord */));
            }
            mConnection.setComposingText(getTextWithUnderline(
                    mWordComposer.getTypedWord()), 1);
        } else {
            final boolean swapWeakSpace = maybeStripSpace(settingsValues,
                    codePoint, spaceState, Constants.SUGGESTION_STRIP_COORDINATE == x);

            sendKeyCodePoint(codePoint);

            if (swapWeakSpace) {
                swapSwapperAndSpace(keyboardSwitcher);
                mSpaceState = SpaceState.WEAK;
            }
            // In case the "add to dictionary" hint was still displayed.
            mLatinIME.dismissAddToDictionaryHint();
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
     * @param x the x-coordinate of the key press, or Contants.NOT_A_COORDINATE if not applicable.
     * @param y the y-coordinate of the key press, or Contants.NOT_A_COORDINATE if not applicable.
     * @param spaceState the space state at start of the batch input.
     * @return whether this caused an auto-correction to happen.
     */
    private boolean handleSeparator(final SettingsValues settingsValues,
            final int codePoint, final int x, final int y, final int spaceState,
            // TODO: remove these arguments
            final KeyboardSwitcher keyboardSwitcher, final LatinIME.UIHandler handler) {
        boolean didAutoCorrect = false;
        // We avoid sending spaces in languages without spaces if we were composing.
        final boolean shouldAvoidSendingCode = Constants.CODE_SPACE == codePoint
                && !settingsValues.mCurrentLanguageHasSpaces
                && mWordComposer.isComposingWord();
        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can insert the separator at the current cursor position.
            resetEntireInputState(settingsValues, mLastSelectionStart, mLastSelectionEnd);
        }
        // isComposingWord() may have changed since we stored wasComposing
        if (mWordComposer.isComposingWord()) {
            if (settingsValues.mCorrectionEnabled) {
                final String separator = shouldAvoidSendingCode ? LastComposedWord.NOT_A_SEPARATOR
                        : StringUtils.newSingleCodePointString(codePoint);
                commitCurrentAutoCorrection(settingsValues, separator, handler);
                didAutoCorrect = true;
            } else {
                commitTyped(StringUtils.newSingleCodePointString(codePoint));
            }
        }

        final boolean swapWeakSpace = maybeStripSpace(settingsValues, codePoint, spaceState,
                Constants.SUGGESTION_STRIP_COORDINATE == x);

        if (SpaceState.PHANTOM == spaceState &&
                settingsValues.isUsuallyPrecededBySpace(codePoint)) {
            promotePhantomSpace(settingsValues);
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_handleSeparator(codePoint, mWordComposer.isComposingWord());
        }

        if (!shouldAvoidSendingCode) {
            sendKeyCodePoint(codePoint);
        }

        if (Constants.CODE_SPACE == codePoint) {
            if (settingsValues.isSuggestionsRequested(mLatinIME.mDisplayOrientation)) {
                if (maybeDoubleSpacePeriod(settingsValues, keyboardSwitcher, handler)) {
                    mSpaceState = SpaceState.DOUBLE;
                } else if (!mLatinIME.isShowingPunctuationList()) {
                    mSpaceState = SpaceState.WEAK;
                }
            }

            handler.startDoubleSpacePeriodTimer();
            handler.postUpdateSuggestionStrip();
        } else {
            if (swapWeakSpace) {
                swapSwapperAndSpace(keyboardSwitcher);
                mSpaceState = SpaceState.SWAP_PUNCTUATION;
            } else if (SpaceState.PHANTOM == spaceState
                    && settingsValues.isUsuallyFollowedBySpace(codePoint)) {
                // If we are in phantom space state, and the user presses a separator, we want to
                // stay in phantom space state so that the next keypress has a chance to add the
                // space. For example, if I type "Good dat", pick "day" from the suggestion strip
                // then insert a comma and go on to typing the next word, I want the space to be
                // inserted automatically before the next word, the same way it is when I don't
                // input the comma.
                // The case is a little different if the separator is a space stripper. Such a
                // separator does not normally need a space on the right (that's the difference
                // between swappers and strippers), so we should not stay in phantom space state if
                // the separator is a stripper. Hence the additional test above.
                mSpaceState = SpaceState.PHANTOM;
            }

            // Set punctuation right away. onUpdateSelection will fire but tests whether it is
            // already displayed or not, so it's okay.
            mLatinIME.setPunctuationSuggestions();
        }
        if (settingsValues.mIsInternal) {
            LatinImeLoggerUtils.onSeparator((char)codePoint, x, y);
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
        mDeleteCount++;

        // In many cases, we may have to put the keyboard in auto-shift state again. However
        // we want to wait a few milliseconds before doing it to avoid the keyboard flashing
        // during key repeat.
        handler.postUpdateShiftState();

        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            // If we are in the middle of a recorrection, we need to commit the recorrection
            // first so that we can remove the character at the current cursor position.
            resetEntireInputState(settingsValues, mLastSelectionStart, mLastSelectionEnd);
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
                revertCommit(settingsValues, keyboardSwitcher, handler);
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
            if (mLastSelectionStart != mLastSelectionEnd) {
                // If there is a selection, remove it.
                final int numCharsDeleted = mLastSelectionEnd - mLastSelectionStart;
                mConnection.setSelection(mLastSelectionEnd, mLastSelectionEnd);
                // Reset mLastSelectionEnd to mLastSelectionStart. This is what is supposed to
                // happen, and if it's wrong, the next call to onUpdateSelection will correct it,
                // but we want to set it right away to avoid it being used with the wrong values
                // later (typically, in a subsequent press on backspace).
                mLastSelectionEnd = mLastSelectionStart;
                mConnection.deleteSurroundingText(numCharsDeleted, 0);
                if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                    ResearchLogger.latinIME_handleBackspace(numCharsDeleted,
                            false /* shouldUncommitLogUnit */);
                }
            } else {
                // There is no selection, just delete one character.
                if (Constants.NOT_A_CURSOR_POSITION == mLastSelectionEnd) {
                    // This should never happen.
                    Log.e(TAG, "Backspace when we don't know the selection position");
                }
                if (mLatinIME.mAppWorkAroundsUtils.isBeforeJellyBean() ||
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
                        // Nothing to delete before the cursor.
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
            // TODO: move mDisplayOrientation to CurrentSettings.
            if (settingsValues.isSuggestionsRequested(mLatinIME.mDisplayOrientation)
                    && settingsValues.mCurrentLanguageHasSpaces) {
                restartSuggestionsOnWordBeforeCursorIfAtEndOfWord(settingsValues, keyboardSwitcher,
                        handler);
            }
            // We just removed a character. We need to update the auto-caps state.
            keyboardSwitcher.updateShiftState();
        }
    }

    /**
     * Handle a press on the language switch key (the "globe key")
     */
    private void handleLanguageSwitchKey() {
        mLatinIME.handleLanguageSwitchKey();
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
            // TODO: remove these arguments
            final KeyboardSwitcher keyboardSwitcher, final LatinIME.UIHandler handler) {
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
            final String textToInsert = new String(
                    new int[] { settingsValues.mSentenceSeparator, Constants.CODE_SPACE }, 0, 2);
            mConnection.commitText(textToInsert, 1);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_maybeDoubleSpacePeriod(textToInsert,
                        false /* isBatchMode */);
            }
            mWordComposer.discardPreviousWordForSuggestion();
            keyboardSwitcher.updateShiftState();
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
    private void performRecapitalization(final SettingsValues settingsValues,
            // TODO: remove this argument.
            final KeyboardSwitcher keyboardSwitcher) {
        if (mLastSelectionStart == mLastSelectionEnd) {
            return; // No selection
        }
        // If we have a recapitalize in progress, use it; otherwise, create a new one.
        if (!mRecapitalizeStatus.isActive()
                || !mRecapitalizeStatus.isSetAt(mLastSelectionStart, mLastSelectionEnd)) {
            final CharSequence selectedText =
                    mConnection.getSelectedText(0 /* flags, 0 for no styles */);
            if (TextUtils.isEmpty(selectedText)) return; // Race condition with the input connection
            mRecapitalizeStatus.initialize(mLastSelectionStart, mLastSelectionEnd,
                    selectedText.toString(),
                    settingsValues.mLocale, settingsValues.mWordSeparators);
            // We trim leading and trailing whitespace.
            mRecapitalizeStatus.trim();
            // Trimming the object may have changed the length of the string, and we need to
            // reposition the selection handles accordingly. As this result in an IPC call,
            // only do it if it's actually necessary, in other words if the recapitalize status
            // is not set at the same place as before.
            if (!mRecapitalizeStatus.isSetAt(mLastSelectionStart, mLastSelectionEnd)) {
                mLastSelectionStart = mRecapitalizeStatus.getNewCursorStart();
                mLastSelectionEnd = mRecapitalizeStatus.getNewCursorEnd();
            }
        }
        mConnection.finishComposingText();
        mRecapitalizeStatus.rotate();
        final int numCharsDeleted = mLastSelectionEnd - mLastSelectionStart;
        mConnection.setSelection(mLastSelectionEnd, mLastSelectionEnd);
        mConnection.deleteSurroundingText(numCharsDeleted, 0);
        mConnection.commitText(mRecapitalizeStatus.getRecapitalizedString(), 0);
        mLastSelectionStart = mRecapitalizeStatus.getNewCursorStart();
        mLastSelectionEnd = mRecapitalizeStatus.getNewCursorEnd();
        mConnection.setSelection(mLastSelectionStart, mLastSelectionEnd);
        // Match the keyboard to the new state.
        keyboardSwitcher.updateShiftState();
    }

    /**
     * Check if the cursor is actually at the end of a word. If so, restart suggestions on this
     * word, otherwise do nothing.
     * @param settingsValues the current values of the settings.
     */
    private void restartSuggestionsOnWordBeforeCursorIfAtEndOfWord(
            final SettingsValues settingsValues,
            // TODO: remove these two arguments
            final KeyboardSwitcher keyboardSwitcher, final LatinIME.UIHandler handler) {
        final CharSequence word = mConnection.getWordBeforeCursorIfAtEndOfWord(settingsValues);
        if (null != word) {
            final String wordString = word.toString();
            restartSuggestionsOnWordBeforeCursor(settingsValues, wordString, keyboardSwitcher,
                    handler);
            // TODO: Handle the case where the user manually moves the cursor and then backs up over
            // a separator.  In that case, the current log unit should not be uncommitted.
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.getInstance().uncommitCurrentLogUnit(wordString,
                        true /* dumpCurrentLogUnit */);
            }
        }
    }

    /**
     * Restart suggestions on the word passed as an argument, assuming it is before the cursor.
     * @param settingsValues the current settings values.
     */
    private void restartSuggestionsOnWordBeforeCursor(final SettingsValues settingsValues,
            final String word,
            // TODO: remove these two arguments
            final KeyboardSwitcher keyboardSwitcher, final LatinIME.UIHandler handler) {
        mWordComposer.setComposingWord(word,
                // Previous word is the 2nd word before cursor because we are restarting on the
                // 1st word before cursor.
                getNthPreviousWordForSuggestion(settingsValues, 2 /* nthPreviousWord */),
                keyboardSwitcher.getKeyboard());
        final int length = word.length();
        mConnection.deleteSurroundingText(length, 0);
        mConnection.setComposingText(word, 1);
        handler.postUpdateSuggestionStrip();
    }

    /**
     * Reverts a previous commit with auto-correction.
     *
     * This is triggered upon pressing backspace just after a commit with auto-correction.
     *
     * @param settingsValues the current settings values.
     */
    private void revertCommit(final SettingsValues settingsValues,
            // TODO: remove these arguments
            final KeyboardSwitcher keyboardSwitcher, final LatinIME.UIHandler handler) {
        final String previousWord = mLastComposedWord.mPrevWord;
        final String originallyTypedWord = mLastComposedWord.mTypedWord;
        final String committedWord = mLastComposedWord.mCommittedWord;
        final int cancelLength = committedWord.length();
        // We want java chars, not codepoints for the following.
        final int separatorLength = mLastComposedWord.mSeparatorString.length();
        // TODO: should we check our saved separator against the actual contents of the text view?
        final int deleteLength = cancelLength + separatorLength;
        if (LatinIME.DEBUG) {
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
                mSuggest.cancelAddingUserHistory(previousWord, committedWord);
            }
        }
        final String stringToCommit = originallyTypedWord + mLastComposedWord.mSeparatorString;
        if (settingsValues.mCurrentLanguageHasSpaces) {
            // For languages with spaces, we revert to the typed string, but the cursor is still
            // after the separator so we don't resume suggestions. If the user wants to correct
            // the word, they have to press backspace again.
            mConnection.commitText(stringToCommit, 1);
        } else {
            // For languages without spaces, we revert the typed string but the cursor is flush
            // with the typed word, so we need to resume suggestions right away.
            mWordComposer.setComposingWord(stringToCommit, previousWord,
                    keyboardSwitcher.getKeyboard());
            mConnection.setComposingText(stringToCommit, 1);
        }
        if (settingsValues.mIsInternal) {
            LatinImeLoggerUtils.onSeparator(mLastComposedWord.mSeparatorString,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_revertCommit(committedWord, originallyTypedWord,
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
     * @param keyboardSwitcher the keyboard switcher. Caps mode depends on its mode.
     * @return the actual caps mode the keyboard is in right now.
     */
    // TODO: Make this private
    public int getActualCapsMode(final KeyboardSwitcher keyboardSwitcher) {
        final int keyboardShiftMode = keyboardSwitcher.getKeyboardShiftMode();
        if (keyboardShiftMode != WordComposer.CAPS_MODE_AUTO_SHIFTED) return keyboardShiftMode;
        final int auto = mLatinIME.getCurrentAutoCapsState();
        if (0 != (auto & TextUtils.CAP_MODE_CHARACTERS)) {
            return WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED;
        }
        if (0 != auto) {
            return WordComposer.CAPS_MODE_AUTO_SHIFTED;
        }
        return WordComposer.CAPS_MODE_OFF;
    }

    /**
     * @return the editor info for the current editor
     */
    private EditorInfo getCurrentInputEditorInfo() {
        return mLatinIME.getCurrentInputEditorInfo();
    }

    /**
     * Get the nth previous word before the cursor as context for the suggestion process.
     * @param currentSettings the current settings values.
     * @param nthPreviousWord reverse index of the word to get (1-indexed)
     * @return the nth previous word before the cursor.
     */
    // TODO: Make this private
    public String getNthPreviousWordForSuggestion(final SettingsValues currentSettings,
            final int nthPreviousWord) {
        if (currentSettings.mCurrentLanguageHasSpaces) {
            // If we are typing in a language with spaces we can just look up the previous
            // word from textview.
            return mConnection.getNthPreviousWord(currentSettings, nthPreviousWord);
        } else {
            return LastComposedWord.NOT_A_COMPOSED_WORD == mLastComposedWord ? null
                    : mLastComposedWord.mCommittedWord;
        }
    }

    /**
     * @param actionId the action to perform
     */
    private void performEditorAction(final int actionId) {
        mConnection.performEditorAction(actionId);
    }

    /**
     * Handle a press on the settings key.
     */
    private void onSettingsKeyPressed() {
        mLatinIME.onSettingsKeyPressed();
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
     */
    // TODO: how is this different from startInput ?!
    // TODO: remove all references to this in LatinIME and make this private
    public void resetEntireInputState(final SettingsValues settingsValues,
            final int newSelStart, final int newSelEnd) {
        final boolean shouldFinishComposition = mWordComposer.isComposingWord();
        resetComposingState(true /* alsoResetLastComposedWord */);
        if (settingsValues.mBigramPredictionEnabled) {
            mLatinIME.clearSuggestionStrip();
        } else {
            mLatinIME.setSuggestedWords(settingsValues.mSuggestPuncList, false);
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
     * @param codePoint the code point to send.
     */
    private void sendKeyCodePoint(final int codePoint) {
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
        if (Constants.CODE_ENTER == codePoint
                && mLatinIME.mAppWorkAroundsUtils.isBeforeJellyBean()) {
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
                && settingsValues.mCurrentLanguageHasSpaces
                && !mConnection.textBeforeCursorLooksLikeURL()) {
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.latinIME_promotePhantomSpace();
            }
            sendKeyCodePoint(Constants.CODE_SPACE);
        }
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
     * @param separatorString the separator string that's causing the commit, or NOT_A_SEPARATOR if
     *   none.
     */
    // TODO: Make this private
    public void commitTyped(final String separatorString) {
        if (!mWordComposer.isComposingWord()) return;
        final String typedWord = mWordComposer.getTypedWord();
        if (typedWord.length() > 0) {
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.getInstance().onWordFinished(typedWord, mWordComposer.isBatchMode());
            }
            mLatinIME.commitChosenWord(typedWord, LastComposedWord.COMMIT_TYPE_USER_TYPED_WORD,
                    separatorString);
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
    // TODO: Make this private
    public void commitCurrentAutoCorrection(final SettingsValues settingsValues,
            final String separator,
            // TODO: Remove this argument.
            final LatinIME.UIHandler handler) {
        // Complete any pending suggestions query first
        if (handler.hasPendingUpdateSuggestions()) {
            mLatinIME.updateSuggestionStrip();
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
            mLatinIME.commitChosenWord(autoCorrection, LastComposedWord.COMMIT_TYPE_DECIDED_WORD,
                    separator);
            if (!typedWord.equals(autoCorrection)) {
                // This will make the correction flash for a short while as a visual clue
                // to the user that auto-correction happened. It has no other effect; in particular
                // note that this won't affect the text inside the text field AT ALL: it only makes
                // the segment of text starting at the supplied index and running for the length
                // of the auto-correction flash. At this moment, the "typedWord" argument is
                // ignored by TextView.
                mConnection.commitCorrection(
                        new CorrectionInfo(mLastSelectionEnd - typedWord.length(),
                        typedWord, autoCorrection));
            }
        }
    }
}
