/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.settings.SettingsValues;
import com.android.inputmethod.latin.utils.CapsModeUtils;
import com.android.inputmethod.latin.utils.DebugLogUtils;
import com.android.inputmethod.latin.utils.SpannableStringUtils;
import com.android.inputmethod.latin.utils.StringUtils;
import com.android.inputmethod.latin.utils.TextRange;
import com.android.inputmethod.research.ResearchLogger;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Enrichment class for InputConnection to simplify interaction and add functionality.
 *
 * This class serves as a wrapper to be able to simply add hooks to any calls to the underlying
 * InputConnection. It also keeps track of a number of things to avoid having to call upon IPC
 * all the time to find out what text is in the buffer, when we need it to determine caps mode
 * for example.
 */
public final class RichInputConnection {
    private static final String TAG = RichInputConnection.class.getSimpleName();
    private static final boolean DBG = false;
    private static final boolean DEBUG_PREVIOUS_TEXT = false;
    private static final boolean DEBUG_BATCH_NESTING = false;
    // Provision for a long word pair and a separator
    private static final int LOOKBACK_CHARACTER_NUM = Constants.DICTIONARY_MAX_WORD_LENGTH * 2 + 1;
    private static final Pattern spaceRegex = Pattern.compile("\\s+");
    private static final int INVALID_CURSOR_POSITION = -1;

    /**
     * This variable contains an expected value for the cursor position. This is where the
     * cursor may end up after all the keyboard-triggered updates have passed. We keep this to
     * compare it to the actual cursor position to guess whether the move was caused by a
     * keyboard command or not.
     * It's not really the cursor position: the cursor may not be there yet, and it's also expected 
     * there be cases where it never actually comes to be there.
     */
    private int mExpectedCursorPosition = INVALID_CURSOR_POSITION; // in chars, not code points
    /**
     * This contains the committed text immediately preceding the cursor and the composing
     * text if any. It is refreshed when the cursor moves by calling upon the TextView.
     */
    private final StringBuilder mCommittedTextBeforeComposingText = new StringBuilder();
    /**
     * This contains the currently composing text, as LatinIME thinks the TextView is seeing it.
     */
    private final StringBuilder mComposingText = new StringBuilder();

    private final InputMethodService mParent;
    InputConnection mIC;
    int mNestLevel;
    public RichInputConnection(final InputMethodService parent) {
        mParent = parent;
        mIC = null;
        mNestLevel = 0;
    }

    private void checkConsistencyForDebug() {
        final ExtractedTextRequest r = new ExtractedTextRequest();
        r.hintMaxChars = 0;
        r.hintMaxLines = 0;
        r.token = 1;
        r.flags = 0;
        final ExtractedText et = mIC.getExtractedText(r, 0);
        final CharSequence beforeCursor = getTextBeforeCursor(Constants.EDITOR_CONTENTS_CACHE_SIZE,
                0);
        final StringBuilder internal = new StringBuilder().append(mCommittedTextBeforeComposingText)
                .append(mComposingText);
        if (null == et || null == beforeCursor) return;
        final int actualLength = Math.min(beforeCursor.length(), internal.length());
        if (internal.length() > actualLength) {
            internal.delete(0, internal.length() - actualLength);
        }
        final String reference = (beforeCursor.length() <= actualLength) ? beforeCursor.toString()
                : beforeCursor.subSequence(beforeCursor.length() - actualLength,
                        beforeCursor.length()).toString();
        if (et.selectionStart != mExpectedCursorPosition
                || !(reference.equals(internal.toString()))) {
            final String context = "Expected cursor position = " + mExpectedCursorPosition
                    + "\nActual cursor position = " + et.selectionStart
                    + "\nExpected text = " + internal.length() + " " + internal
                    + "\nActual text = " + reference.length() + " " + reference;
            ((LatinIME)mParent).debugDumpStateAndCrashWithException(context);
        } else {
            Log.e(TAG, DebugLogUtils.getStackTrace(2));
            Log.e(TAG, "Exp <> Actual : " + mExpectedCursorPosition + " <> " + et.selectionStart);
        }
    }

    public void beginBatchEdit() {
        if (++mNestLevel == 1) {
            mIC = mParent.getCurrentInputConnection();
            if (null != mIC) {
                mIC.beginBatchEdit();
            }
        } else {
            if (DBG) {
                throw new RuntimeException("Nest level too deep");
            } else {
                Log.e(TAG, "Nest level too deep : " + mNestLevel);
            }
        }
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
    }

    public void endBatchEdit() {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (--mNestLevel == 0 && null != mIC) {
            mIC.endBatchEdit();
        }
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
    }

    /**
     * Reset the cached text and retrieve it again from the editor.
     *
     * This should be called when the cursor moved. It's possible that we can't connect to
     * the application when doing this; notably, this happens sometimes during rotation, probably
     * because of a race condition in the framework. In this case, we just can't retrieve the
     * data, so we empty the cache and note that we don't know the new cursor position, and we
     * return false so that the caller knows about this and can retry later.
     *
     * @param newCursorPosition The new position of the cursor, as received from the system.
     * @param shouldFinishComposition Whether we should finish the composition in progress.
     * @return true if we were able to connect to the editor successfully, false otherwise. When
     *   this method returns false, the caches could not be correctly refreshed so they were only
     *   reset: the caller should try again later to return to normal operation.
     */
    public boolean resetCachesUponCursorMoveAndReturnSuccess(final int newCursorPosition,
            final boolean shouldFinishComposition) {
        mExpectedCursorPosition = newCursorPosition;
        mComposingText.setLength(0);
        mCommittedTextBeforeComposingText.setLength(0);
        mIC = mParent.getCurrentInputConnection();
        // Call upon the inputconnection directly since our own method is using the cache, and
        // we want to refresh it.
        final CharSequence textBeforeCursor = null == mIC ? null :
                mIC.getTextBeforeCursor(Constants.EDITOR_CONTENTS_CACHE_SIZE, 0);
        if (null == textBeforeCursor) {
            // For some reason the app thinks we are not connected to it. This looks like a
            // framework bug... Fall back to ground state and return false.
            mExpectedCursorPosition = INVALID_CURSOR_POSITION;
            Log.e(TAG, "Unable to connect to the editor to retrieve text... will retry later");
            return false;
        }
        mCommittedTextBeforeComposingText.append(textBeforeCursor);
        final int lengthOfTextBeforeCursor = textBeforeCursor.length();
        if (lengthOfTextBeforeCursor > newCursorPosition
                || (lengthOfTextBeforeCursor < Constants.EDITOR_CONTENTS_CACHE_SIZE
                        && newCursorPosition < Constants.EDITOR_CONTENTS_CACHE_SIZE)) {
            // newCursorPosition may be lying -- when rotating the device (probably a framework
            // bug). If we have less chars than we asked for, then we know how many chars we have,
            // and if we got more than newCursorPosition says, then we know it was lying. In both
            // cases the length is more reliable
            mExpectedCursorPosition = lengthOfTextBeforeCursor;
        }
        if (null != mIC && shouldFinishComposition) {
            mIC.finishComposingText();
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.richInputConnection_finishComposingText();
            }
        }
        return true;
    }

    private void checkBatchEdit() {
        if (mNestLevel != 1) {
            // TODO: exception instead
            Log.e(TAG, "Batch edit level incorrect : " + mNestLevel);
            Log.e(TAG, DebugLogUtils.getStackTrace(4));
        }
    }

    public void finishComposingText() {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
        mCommittedTextBeforeComposingText.append(mComposingText);
        mComposingText.setLength(0);
        if (null != mIC) {
            mIC.finishComposingText();
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.richInputConnection_finishComposingText();
            }
        }
    }

    public void commitText(final CharSequence text, final int i) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
        mCommittedTextBeforeComposingText.append(text);
        mExpectedCursorPosition += text.length() - mComposingText.length();
        mComposingText.setLength(0);
        if (null != mIC) {
            mIC.commitText(text, i);
        }
    }

    public CharSequence getSelectedText(final int flags) {
        if (null == mIC) return null;
        return mIC.getSelectedText(flags);
    }

    public boolean canDeleteCharacters() {
        return mExpectedCursorPosition > 0;
    }

    /**
     * Gets the caps modes we should be in after this specific string.
     *
     * This returns a bit set of TextUtils#CAP_MODE_*, masked by the inputType argument.
     * This method also supports faking an additional space after the string passed in argument,
     * to support cases where a space will be added automatically, like in phantom space
     * state for example.
     * Note that for English, we are using American typography rules (which are not specific to
     * American English, it's just the most common set of rules for English).
     *
     * @param inputType a mask of the caps modes to test for.
     * @param settingsValues the values of the settings to use for locale and separators.
     * @param hasSpaceBefore if we should consider there should be a space after the string.
     * @return the caps modes that should be on as a set of bits
     */
    public int getCursorCapsMode(final int inputType, final SettingsValues settingsValues,
            final boolean hasSpaceBefore) {
        mIC = mParent.getCurrentInputConnection();
        if (null == mIC) return Constants.TextUtils.CAP_MODE_OFF;
        if (!TextUtils.isEmpty(mComposingText)) {
            if (hasSpaceBefore) {
                // If we have some composing text and a space before, then we should have
                // MODE_CHARACTERS and MODE_WORDS on.
                return (TextUtils.CAP_MODE_CHARACTERS | TextUtils.CAP_MODE_WORDS) & inputType;
            } else {
                // We have some composing text - we should be in MODE_CHARACTERS only.
                return TextUtils.CAP_MODE_CHARACTERS & inputType;
            }
        }
        // TODO: this will generally work, but there may be cases where the buffer contains SOME
        // information but not enough to determine the caps mode accurately. This may happen after
        // heavy pressing of delete, for example DEFAULT_TEXT_CACHE_SIZE - 5 times or so.
        // getCapsMode should be updated to be able to return a "not enough info" result so that
        // we can get more context only when needed.
        if (TextUtils.isEmpty(mCommittedTextBeforeComposingText) && 0 != mExpectedCursorPosition) {
            final CharSequence textBeforeCursor = getTextBeforeCursor(
                    Constants.EDITOR_CONTENTS_CACHE_SIZE, 0);
            if (!TextUtils.isEmpty(textBeforeCursor)) {
                mCommittedTextBeforeComposingText.append(textBeforeCursor);
            }
        }
        // This never calls InputConnection#getCapsMode - in fact, it's a static method that
        // never blocks or initiates IPC.
        return CapsModeUtils.getCapsMode(mCommittedTextBeforeComposingText, inputType,
                settingsValues, hasSpaceBefore);
    }

    public int getCodePointBeforeCursor() {
        if (mCommittedTextBeforeComposingText.length() < 1) return Constants.NOT_A_CODE;
        return Character.codePointBefore(mCommittedTextBeforeComposingText,
                mCommittedTextBeforeComposingText.length());
    }

    public CharSequence getTextBeforeCursor(final int n, final int flags) {
        final int cachedLength =
                mCommittedTextBeforeComposingText.length() + mComposingText.length();
        // If we have enough characters to satisfy the request, or if we have all characters in
        // the text field, then we can return the cached version right away.
        if (cachedLength >= n || cachedLength >= mExpectedCursorPosition) {
            final StringBuilder s = new StringBuilder(mCommittedTextBeforeComposingText);
            // We call #toString() here to create a temporary object.
            // In some situations, this method is called on a worker thread, and it's possible
            // the main thread touches the contents of mComposingText while this worker thread
            // is suspended, because mComposingText is a StringBuilder. This may lead to crashes,
            // so we call #toString() on it. That will result in the return value being strictly
            // speaking wrong, but since this is used for basing bigram probability off, and
            // it's only going to matter for one getSuggestions call, it's fine in the practice.
            s.append(mComposingText.toString());
            if (s.length() > n) {
                s.delete(0, s.length() - n);
            }
            return s;
        }
        mIC = mParent.getCurrentInputConnection();
        if (null != mIC) {
            return mIC.getTextBeforeCursor(n, flags);
        }
        return null;
    }

    public CharSequence getTextAfterCursor(final int n, final int flags) {
        mIC = mParent.getCurrentInputConnection();
        if (null != mIC) return mIC.getTextAfterCursor(n, flags);
        return null;
    }

    public void deleteSurroundingText(final int beforeLength, final int afterLength) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        final int remainingChars = mComposingText.length() - beforeLength;
        if (remainingChars >= 0) {
            mComposingText.setLength(remainingChars);
        } else {
            mComposingText.setLength(0);
            // Never cut under 0
            final int len = Math.max(mCommittedTextBeforeComposingText.length()
                    + remainingChars, 0);
            mCommittedTextBeforeComposingText.setLength(len);
        }
        if (mExpectedCursorPosition > beforeLength) {
            mExpectedCursorPosition -= beforeLength;
        } else {
            mExpectedCursorPosition = 0;
        }
        if (null != mIC) {
            mIC.deleteSurroundingText(beforeLength, afterLength);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.richInputConnection_deleteSurroundingText(beforeLength, afterLength);
            }
        }
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
    }

    public void performEditorAction(final int actionId) {
        mIC = mParent.getCurrentInputConnection();
        if (null != mIC) {
            mIC.performEditorAction(actionId);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.richInputConnection_performEditorAction(actionId);
            }
        }
    }

    public void sendKeyEvent(final KeyEvent keyEvent) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
            // This method is only called for enter or backspace when speaking to old applications
            // (target SDK <= 15 (Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)), or for digits.
            // When talking to new applications we never use this method because it's inherently
            // racy and has unpredictable results, but for backward compatibility we continue
            // sending the key events for only Enter and Backspace because some applications
            // mistakenly catch them to do some stuff.
            switch (keyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_ENTER:
                mCommittedTextBeforeComposingText.append("\n");
                mExpectedCursorPosition += 1;
                break;
            case KeyEvent.KEYCODE_DEL:
                if (0 == mComposingText.length()) {
                    if (mCommittedTextBeforeComposingText.length() > 0) {
                        mCommittedTextBeforeComposingText.delete(
                                mCommittedTextBeforeComposingText.length() - 1,
                                mCommittedTextBeforeComposingText.length());
                    }
                } else {
                    mComposingText.delete(mComposingText.length() - 1, mComposingText.length());
                }
                if (mExpectedCursorPosition > 0) mExpectedCursorPosition -= 1;
                break;
            case KeyEvent.KEYCODE_UNKNOWN:
                if (null != keyEvent.getCharacters()) {
                    mCommittedTextBeforeComposingText.append(keyEvent.getCharacters());
                    mExpectedCursorPosition += keyEvent.getCharacters().length();
                }
                break;
            default:
                final String text = new String(new int[] { keyEvent.getUnicodeChar() }, 0, 1);
                mCommittedTextBeforeComposingText.append(text);
                mExpectedCursorPosition += text.length();
                break;
            }
        }
        if (null != mIC) {
            mIC.sendKeyEvent(keyEvent);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.richInputConnection_sendKeyEvent(keyEvent);
            }
        }
    }

    public void setComposingRegion(final int start, final int end) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
        final CharSequence textBeforeCursor =
                getTextBeforeCursor(Constants.EDITOR_CONTENTS_CACHE_SIZE + (end - start), 0);
        mCommittedTextBeforeComposingText.setLength(0);
        if (!TextUtils.isEmpty(textBeforeCursor)) {
            final int indexOfStartOfComposingText =
                    Math.max(textBeforeCursor.length() - (end - start), 0);
            mComposingText.append(textBeforeCursor.subSequence(indexOfStartOfComposingText,
                    textBeforeCursor.length()));
            mCommittedTextBeforeComposingText.append(
                    textBeforeCursor.subSequence(0, indexOfStartOfComposingText));
        }
        if (null != mIC) {
            mIC.setComposingRegion(start, end);
        }
    }

    public void setComposingText(final CharSequence text, final int newCursorPosition) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
        mExpectedCursorPosition += text.length() - mComposingText.length();
        mComposingText.setLength(0);
        mComposingText.append(text);
        // TODO: support values of i != 1. At this time, this is never called with i != 1.
        if (null != mIC) {
            mIC.setComposingText(text, newCursorPosition);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.richInputConnection_setComposingText(text, newCursorPosition);
            }
        }
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
    }

    public void setSelection(final int start, final int end) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
        if (null != mIC) {
            mIC.setSelection(start, end);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.richInputConnection_setSelection(start, end);
            }
        }
        mExpectedCursorPosition = start;
        mCommittedTextBeforeComposingText.setLength(0);
        mCommittedTextBeforeComposingText.append(
                getTextBeforeCursor(Constants.EDITOR_CONTENTS_CACHE_SIZE, 0));
    }

    public void commitCorrection(final CorrectionInfo correctionInfo) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
        // This has no effect on the text field and does not change its content. It only makes
        // TextView flash the text for a second based on indices contained in the argument.
        if (null != mIC) {
            mIC.commitCorrection(correctionInfo);
        }
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
    }

    public void commitCompletion(final CompletionInfo completionInfo) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
        CharSequence text = completionInfo.getText();
        // text should never be null, but just in case, it's better to insert nothing than to crash
        if (null == text) text = "";
        mCommittedTextBeforeComposingText.append(text);
        mExpectedCursorPosition += text.length() - mComposingText.length();
        mComposingText.setLength(0);
        if (null != mIC) {
            mIC.commitCompletion(completionInfo);
            if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                ResearchLogger.richInputConnection_commitCompletion(completionInfo);
            }
        }
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug();
    }

    @SuppressWarnings("unused")
    public String getNthPreviousWord(final String sentenceSeperators, final int n) {
        mIC = mParent.getCurrentInputConnection();
        if (null == mIC) return null;
        final CharSequence prev = getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0);
        if (DEBUG_PREVIOUS_TEXT && null != prev) {
            final int checkLength = LOOKBACK_CHARACTER_NUM - 1;
            final String reference = prev.length() <= checkLength ? prev.toString()
                    : prev.subSequence(prev.length() - checkLength, prev.length()).toString();
            final StringBuilder internal = new StringBuilder()
                    .append(mCommittedTextBeforeComposingText).append(mComposingText);
            if (internal.length() > checkLength) {
                internal.delete(0, internal.length() - checkLength);
                if (!(reference.equals(internal.toString()))) {
                    final String context =
                            "Expected text = " + internal + "\nActual text = " + reference;
                    ((LatinIME)mParent).debugDumpStateAndCrashWithException(context);
                }
            }
        }
        return getNthPreviousWord(prev, sentenceSeperators, n);
    }

    private static boolean isSeparator(int code, String sep) {
        return sep.indexOf(code) != -1;
    }

    // Get the nth word before cursor. n = 1 retrieves the word immediately before the cursor,
    // n = 2 retrieves the word before that, and so on. This splits on whitespace only.
    // Also, it won't return words that end in a separator (if the nth word before the cursor
    // ends in a separator, it returns null).
    // Example :
    // (n = 1) "abc def|" -> def
    // (n = 1) "abc def |" -> def
    // (n = 1) "abc def. |" -> null
    // (n = 1) "abc def . |" -> null
    // (n = 2) "abc def|" -> abc
    // (n = 2) "abc def |" -> abc
    // (n = 2) "abc def. |" -> abc
    // (n = 2) "abc def . |" -> def
    // (n = 2) "abc|" -> null
    // (n = 2) "abc |" -> null
    // (n = 2) "abc. def|" -> null
    public static String getNthPreviousWord(final CharSequence prev,
            final String sentenceSeperators, final int n) {
        if (prev == null) return null;
        final String[] w = spaceRegex.split(prev);

        // If we can't find n words, or we found an empty word, return null.
        if (w.length < n) return null;
        final String nthPrevWord = w[w.length - n];
        final int length = nthPrevWord.length();
        if (length <= 0) return null;

        // If ends in a separator, return null
        final char lastChar = nthPrevWord.charAt(length - 1);
        if (sentenceSeperators.contains(String.valueOf(lastChar))) return null;

        return nthPrevWord;
    }

    /**
     * @param separators characters which may separate words
     * @return the word that surrounds the cursor, including up to one trailing
     *   separator. For example, if the field contains "he|llo world", where |
     *   represents the cursor, then "hello " will be returned.
     */
    public CharSequence getWordAtCursor(String separators) {
        // getWordRangeAtCursor returns null if the connection is null
        TextRange r = getWordRangeAtCursor(separators, 0);
        return (r == null) ? null : r.mWord;
    }

    /**
     * Returns the text surrounding the cursor.
     *
     * @param sep a string of characters that split words.
     * @param additionalPrecedingWordsCount the number of words before the current word that should
     *   be included in the returned range
     * @return a range containing the text surrounding the cursor
     */
    public TextRange getWordRangeAtCursor(final String sep,
            final int additionalPrecedingWordsCount) {
        mIC = mParent.getCurrentInputConnection();
        if (mIC == null || sep == null) {
            return null;
        }
        final CharSequence before = mIC.getTextBeforeCursor(Constants.EDITOR_CONTENTS_CACHE_SIZE,
                InputConnection.GET_TEXT_WITH_STYLES);
        final CharSequence after = mIC.getTextAfterCursor(Constants.EDITOR_CONTENTS_CACHE_SIZE,
                InputConnection.GET_TEXT_WITH_STYLES);
        if (before == null || after == null) {
            return null;
        }

        // Going backward, alternate skipping non-separators and separators until enough words
        // have been read.
        int count = additionalPrecedingWordsCount;
        int startIndexInBefore = before.length();
        boolean isStoppingAtWhitespace = true;  // toggles to indicate what to stop at
        while (true) { // see comments below for why this is guaranteed to halt
            while (startIndexInBefore > 0) {
                final int codePoint = Character.codePointBefore(before, startIndexInBefore);
                if (isStoppingAtWhitespace == isSeparator(codePoint, sep)) {
                    break;  // inner loop
                }
                --startIndexInBefore;
                if (Character.isSupplementaryCodePoint(codePoint)) {
                    --startIndexInBefore;
                }
            }
            // isStoppingAtWhitespace is true every other time through the loop,
            // so additionalPrecedingWordsCount is guaranteed to become < 0, which
            // guarantees outer loop termination
            if (isStoppingAtWhitespace && (--count < 0)) {
                break;  // outer loop
            }
            isStoppingAtWhitespace = !isStoppingAtWhitespace;
        }

        // Find last word separator after the cursor
        int endIndexInAfter = -1;
        while (++endIndexInAfter < after.length()) {
            final int codePoint = Character.codePointAt(after, endIndexInAfter);
            if (isSeparator(codePoint, sep)) {
                break;
            }
            if (Character.isSupplementaryCodePoint(codePoint)) {
                ++endIndexInAfter;
            }
        }

        // We don't use TextUtils#concat because it copies all spans without respect to their
        // nature. If the text includes a PARAGRAPH span and it has been split, then
        // TextUtils#concat will crash when it tries to concat both sides of it.
        return new TextRange(
                SpannableStringUtils.concatWithNonParagraphSuggestionSpansOnly(before, after),
                        startIndexInBefore, before.length() + endIndexInAfter, before.length());
    }

    public boolean isCursorTouchingWord(final SettingsValues settingsValues) {
        final int codePointBeforeCursor = getCodePointBeforeCursor();
        if (Constants.NOT_A_CODE != codePointBeforeCursor
                && !settingsValues.isWordSeparator(codePointBeforeCursor)
                && !settingsValues.isWordConnector(codePointBeforeCursor)) {
            return true;
        }
        final CharSequence after = getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(after) && !settingsValues.isWordSeparator(after.charAt(0))
                && !settingsValues.isWordConnector(after.charAt(0))) {
            return true;
        }
        return false;
    }

    public void removeTrailingSpace() {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        final int codePointBeforeCursor = getCodePointBeforeCursor();
        if (Constants.CODE_SPACE == codePointBeforeCursor) {
            deleteSurroundingText(1, 0);
        }
    }

    public boolean sameAsTextBeforeCursor(final CharSequence text) {
        final CharSequence beforeText = getTextBeforeCursor(text.length(), 0);
        return TextUtils.equals(text, beforeText);
    }

    /* (non-javadoc)
     * Returns the word before the cursor if the cursor is at the end of a word, null otherwise
     */
    public CharSequence getWordBeforeCursorIfAtEndOfWord(final SettingsValues settings) {
        // Bail out if the cursor is in the middle of a word (cursor must be followed by whitespace,
        // separator or end of line/text)
        // Example: "test|"<EOL> "te|st" get rejected here
        final CharSequence textAfterCursor = getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(textAfterCursor)
                && !settings.isWordSeparator(textAfterCursor.charAt(0))) return null;

        // Bail out if word before cursor is 0-length or a single non letter (like an apostrophe)
        // Example: " -|" gets rejected here but "e-|" and "e|" are okay
        CharSequence word = getWordAtCursor(settings.mWordSeparators);
        // We don't suggest on leading single quotes, so we have to remove them from the word if
        // it starts with single quotes.
        while (!TextUtils.isEmpty(word) && Constants.CODE_SINGLE_QUOTE == word.charAt(0)) {
            word = word.subSequence(1, word.length());
        }
        if (TextUtils.isEmpty(word)) return null;
        // Find the last code point of the string
        final int lastCodePoint = Character.codePointBefore(word, word.length());
        // If for some reason the text field contains non-unicode binary data, or if the
        // charsequence is exactly one char long and the contents is a low surrogate, return null.
        if (!Character.isDefined(lastCodePoint)) return null;
        // Bail out if the cursor is not at the end of a word (cursor must be preceded by
        // non-whitespace, non-separator, non-start-of-text)
        // Example ("|" is the cursor here) : <SOL>"|a" " |a" " | " all get rejected here.
        if (settings.isWordSeparator(lastCodePoint)) return null;
        final char firstChar = word.charAt(0); // we just tested that word is not empty
        if (word.length() == 1 && !Character.isLetter(firstChar)) return null;

        // We don't restart suggestion if the first character is not a letter, because we don't
        // start composing when the first character is not a letter.
        if (!Character.isLetter(firstChar)) return null;

        return word;
    }

    public boolean revertDoubleSpacePeriod() {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        // Here we test whether we indeed have a period and a space before us. This should not
        // be needed, but it's there just in case something went wrong.
        final CharSequence textBeforeCursor = getTextBeforeCursor(2, 0);
        final String periodSpace = ". ";
        if (!TextUtils.equals(periodSpace, textBeforeCursor)) {
            // Theoretically we should not be coming here if there isn't ". " before the
            // cursor, but the application may be changing the text while we are typing, so
            // anything goes. We should not crash.
            Log.d(TAG, "Tried to revert double-space combo but we didn't find "
                    + "\"" + periodSpace + "\" just before the cursor.");
            return false;
        }
        // Double-space results in ". ". A backspace to cancel this should result in a single
        // space in the text field, so we replace ". " with a single space.
        deleteSurroundingText(2, 0);
        final String singleSpace = " ";
        commitText(singleSpace, 1);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.richInputConnection_revertDoubleSpacePeriod();
        }
        return true;
    }

    public boolean revertSwapPunctuation() {
        if (DEBUG_BATCH_NESTING) checkBatchEdit();
        // Here we test whether we indeed have a space and something else before us. This should not
        // be needed, but it's there just in case something went wrong.
        final CharSequence textBeforeCursor = getTextBeforeCursor(2, 0);
        // NOTE: This does not work with surrogate pairs. Hopefully when the keyboard is able to
        // enter surrogate pairs this code will have been removed.
        if (TextUtils.isEmpty(textBeforeCursor)
                || (Constants.CODE_SPACE != textBeforeCursor.charAt(1))) {
            // We may only come here if the application is changing the text while we are typing.
            // This is quite a broken case, but not logically impossible, so we shouldn't crash,
            // but some debugging log may be in order.
            Log.d(TAG, "Tried to revert a swap of punctuation but we didn't "
                    + "find a space just before the cursor.");
            return false;
        }
        deleteSurroundingText(2, 0);
        final String text = " " + textBeforeCursor.subSequence(0, 1);
        commitText(text, 1);
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.richInputConnection_revertSwapPunctuation();
        }
        return true;
    }

    /**
     * Heuristic to determine if this is an expected update of the cursor.
     *
     * Sometimes updates to the cursor position are late because of their asynchronous nature.
     * This method tries to determine if this update is one, based on the values of the cursor
     * position in the update, and the currently expected position of the cursor according to
     * LatinIME's internal accounting. If this is not a belated expected update, then it should
     * mean that the user moved the cursor explicitly.
     * This is quite robust, but of course it's not perfect. In particular, it will fail in the
     * case we get an update A, the user types in N characters so as to move the cursor to A+N but
     * we don't get those, and then the user places the cursor between A and A+N, and we get only
     * this update and not the ones in-between. This is almost impossible to achieve even trying
     * very very hard.
     *
     * @param oldSelStart The value of the old cursor position in the update.
     * @param newSelStart The value of the new cursor position in the update.
     * @return whether this is a belated expected update or not.
     */
    public boolean isBelatedExpectedUpdate(final int oldSelStart, final int newSelStart) {
        // If this is an update that arrives at our expected position, it's a belated update.
        if (newSelStart == mExpectedCursorPosition) return true;
        // If this is an update that moves the cursor from our expected position, it must be
        // an explicit move.
        if (oldSelStart == mExpectedCursorPosition) return false;
        // The following returns true if newSelStart is between oldSelStart and
        // mCurrentCursorPosition. We assume that if the updated position is between the old
        // position and the expected position, then it must be a belated update.
        return (newSelStart - oldSelStart) * (mExpectedCursorPosition - newSelStart) >= 0;
    }

    /**
     * Looks at the text just before the cursor to find out if it looks like a URL.
     *
     * The weakest point here is, if we don't have enough text bufferized, we may fail to realize
     * we are in URL situation, but other places in this class have the same limitation and it
     * does not matter too much in the practice.
     */
    public boolean textBeforeCursorLooksLikeURL() {
        return StringUtils.lastPartLooksLikeURL(mCommittedTextBeforeComposingText);
    }
}
