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
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import com.android.inputmethod.compat.SuggestionSpanUtils;
import com.android.inputmethod.event.EventInterpreter;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
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
    public static final int NOT_A_CURSOR_POSITION = -1;
    public int mLastSelectionStart = NOT_A_CURSOR_POSITION;
    public int mLastSelectionEnd = NOT_A_CURSOR_POSITION;

    public int mDeleteCount;
    public long mLastKeyTime;
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

    public void startInput(final boolean restarting) {
    }

    public void finishInput() {
    }

    public void onCodeInput(final int primaryCode, final int x, final int y,
            // TODO: remove these three arguments
            final LatinIME.UIHandler handler, final KeyboardSwitcher keyboardSwitcher,
            final SubtypeSwitcher subtypeSwitcher) {
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_onCodeInput(primaryCode, x, y);
        }
        final SettingsValues settingsValues = Settings.getInstance().getCurrent();
        final long when = SystemClock.uptimeMillis();
        if (primaryCode != Constants.CODE_DELETE
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
        if (primaryCode != Constants.CODE_SPACE) {
            handler.cancelDoubleSpacePeriodTimer();
        }

        boolean didAutoCorrect = false;
        switch (primaryCode) {
        case Constants.CODE_DELETE:
            mSpaceState = SpaceState.NONE;
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
                handleRecapitalize();
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
                        Constants.CODE_ENTER, x, y, spaceState, keyboardSwitcher);
            }
            break;
        case Constants.CODE_SHIFT_ENTER:
            didAutoCorrect = handleNonSpecialCharacter(settingsValues,
                    Constants.CODE_ENTER, x, y, spaceState, keyboardSwitcher);
            break;
        default:
            didAutoCorrect = handleNonSpecialCharacter(settingsValues,
                    primaryCode, x, y, spaceState, keyboardSwitcher);
            break;
        }
        switcher.onCodeInput(primaryCode);
        // Reset after any single keystroke, except shift, capslock, and symbol-shift
        if (!didAutoCorrect && primaryCode != Constants.CODE_SHIFT
                && primaryCode != Constants.CODE_CAPSLOCK
                && primaryCode != Constants.CODE_SWITCH_ALPHA_SYMBOL)
            mLastComposedWord.deactivate();
        if (Constants.CODE_DELETE != primaryCode) {
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
     * @param codePoint the code point associated with the key.
     * @param x the x-coordinate of the key press, or Contants.NOT_A_COORDINATE if not applicable.
     * @param y the y-coordinate of the key press, or Contants.NOT_A_COORDINATE if not applicable.
     * @param spaceState the space state at start of the batch input.
     * @return whether this caused an auto-correction to happen.
     */
    private boolean handleNonSpecialCharacter(final SettingsValues settingsValues,
            final int codePoint, final int x, final int y, final int spaceState,
            // TODO: remove this argument.
            final KeyboardSwitcher keyboardSwitcher) {
        mSpaceState = SpaceState.NONE;
        final boolean didAutoCorrect;
        if (settingsValues.isWordSeparator(codePoint)
                || Character.getType(codePoint) == Character.OTHER_SYMBOL) {
            didAutoCorrect = mLatinIME.handleSeparator(codePoint, x, y, spaceState);
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
            mLatinIME.handleCharacter(codePoint, keyX, keyY, spaceState);
        }
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
                mLatinIME.revertCommit();
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
                if (NOT_A_CURSOR_POSITION == mLastSelectionEnd) {
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
                mLatinIME.restartSuggestionsOnWordBeforeCursorIfAtEndOfWord();
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
     * Processes a recapitalize event.
     */
    private void handleRecapitalize() {
        mLatinIME.handleRecapitalize();
    }

    /**
     * @return the editor info for the current editor
     */
    private EditorInfo getCurrentInputEditorInfo() {
        return mLatinIME.getCurrentInputEditorInfo();
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

    // This will reset the whole input state to the starting state. It will clear
    // the composing word, reset the last composed word, tell the inputconnection about it.
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

    // TODO: remove all references to this in LatinIME and make this private.
    public void resetComposingState(final boolean alsoResetLastComposedWord) {
        mWordComposer.reset();
        if (alsoResetLastComposedWord) {
            mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD;
        }
    }

    // TODO: remove all references to this in LatinIME and make this private. Also, shouldn't
    // this go in some *Utils class instead?
    public CharSequence getTextWithUnderline(final String text) {
        return mIsAutoCorrectionIndicatorOn
                ? SuggestionSpanUtils.getTextWithAutoCorrectionIndicatorUnderline(mLatinIME, text)
                : text;
    }

    private void sendDownUpKeyEvent(final int code) {
        final long eventTime = SystemClock.uptimeMillis();
        mConnection.sendKeyEvent(new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        mConnection.sendKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    // TODO: remove all references to this in LatinIME and make this private
    public void sendKeyCodePoint(final int code) {
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.latinIME_sendKeyCodePoint(code);
        }
        // TODO: Remove this special handling of digit letters.
        // For backward compatibility. See {@link InputMethodService#sendKeyChar(char)}.
        if (code >= '0' && code <= '9') {
            sendDownUpKeyEvent(code - '0' + KeyEvent.KEYCODE_0);
            return;
        }

        if (Constants.CODE_ENTER == code && mLatinIME.mAppWorkAroundsUtils.isBeforeJellyBean()) {
            // Backward compatibility mode. Before Jelly bean, the keyboard would simulate
            // a hardware keyboard event on pressing enter or delete. This is bad for many
            // reasons (there are race conditions with commits) but some applications are
            // relying on this behavior so we continue to support it for older apps.
            sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER);
        } else {
            mConnection.commitText(StringUtils.newSingleCodePointString(code), 1);
        }
    }

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
}
