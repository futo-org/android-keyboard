/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.util.regex.Pattern;

/**
 * Wrapper for InputConnection to simplify interaction
 */
public class RichInputConnection {
    private static final String TAG = RichInputConnection.class.getSimpleName();
    private static final boolean DBG = false;
    // Provision for a long word pair and a separator
    private static final int LOOKBACK_CHARACTER_NUM = BinaryDictionary.MAX_WORD_LENGTH * 2 + 1;
    private static final Pattern spaceRegex = Pattern.compile("\\s+");
    private static final int INVALID_CURSOR_POSITION = -1;

    InputConnection mIC;
    int mNestLevel;
    public RichInputConnection() {
        mIC = null;
        mNestLevel = 0;
    }

    public void beginBatchEdit(final InputConnection newInputConnection) {
        if (++mNestLevel == 1) {
            mIC = newInputConnection;
            if (null != mIC) mIC.beginBatchEdit();
        } else {
            if (DBG) {
                throw new RuntimeException("Nest level too deep");
            } else {
                Log.e(TAG, "Nest level too deep : " + mNestLevel);
            }
        }
    }
    public void endBatchEdit() {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (--mNestLevel == 0 && null != mIC) mIC.endBatchEdit();
    }

    public void finishComposingText() {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) mIC.finishComposingText();
    }

    public void commitText(final CharSequence text, final int i) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) mIC.commitText(text, i);
    }

    public int getCursorCapsMode(final int inputType) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null == mIC) return Constants.TextUtils.CAP_MODE_OFF;
        return mIC.getCursorCapsMode(inputType);
    }

    public CharSequence getTextBeforeCursor(final int i, final int j) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) return mIC.getTextBeforeCursor(i, j);
        return null;
    }

    public CharSequence getTextAfterCursor(final int i, final int j) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) return mIC.getTextAfterCursor(i, j);
        return null;
    }

    public void deleteSurroundingText(final int i, final int j) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) mIC.deleteSurroundingText(i, j);
    }

    public void performEditorAction(final int actionId) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) mIC.performEditorAction(actionId);
    }

    public void sendKeyEvent(final KeyEvent keyEvent) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) mIC.sendKeyEvent(keyEvent);
    }

    public void setComposingText(final CharSequence text, final int i) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) mIC.setComposingText(text, i);
    }

    public void setSelection(final int from, final int to) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) mIC.setSelection(from, to);
    }

    public void commitCorrection(final CorrectionInfo correctionInfo) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) mIC.commitCorrection(correctionInfo);
    }

    public void commitCompletion(final CompletionInfo completionInfo) {
        if (mNestLevel <= 0) Log.e(TAG, "Batch edit not in progress!"); // TODO: exception instead
        if (null != mIC) mIC.commitCompletion(completionInfo);
    }

    public CharSequence getPreviousWord(final String sentenceSeperators) {
        //TODO: Should fix this. This could be slow!
        if (null == mIC) return null;
        CharSequence prev = mIC.getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0);
        return getPreviousWord(prev, sentenceSeperators);
    }

    /**
     * Represents a range of text, relative to the current cursor position.
     */
    public static class Range {
        /** Characters before selection start */
        public final int mCharsBefore;

        /**
         * Characters after selection start, including one trailing word
         * separator.
         */
        public final int mCharsAfter;

        /** The actual characters that make up a word */
        public final String mWord;

        public Range(int charsBefore, int charsAfter, String word) {
            if (charsBefore < 0 || charsAfter < 0) {
                throw new IndexOutOfBoundsException();
            }
            this.mCharsBefore = charsBefore;
            this.mCharsAfter = charsAfter;
            this.mWord = word;
        }
    }

    private static boolean isSeparator(int code, String sep) {
        return sep.indexOf(code) != -1;
    }

    // Get the word before the whitespace preceding the non-whitespace preceding the cursor.
    // Also, it won't return words that end in a separator.
    // Example :
    // "abc def|" -> abc
    // "abc def |" -> abc
    // "abc def. |" -> abc
    // "abc def . |" -> def
    // "abc|" -> null
    // "abc |" -> null
    // "abc. def|" -> null
    public static CharSequence getPreviousWord(CharSequence prev, String sentenceSeperators) {
        if (prev == null) return null;
        String[] w = spaceRegex.split(prev);

        // If we can't find two words, or we found an empty word, return null.
        if (w.length < 2 || w[w.length - 2].length() <= 0) return null;

        // If ends in a separator, return null
        char lastChar = w[w.length - 2].charAt(w[w.length - 2].length() - 1);
        if (sentenceSeperators.contains(String.valueOf(lastChar))) return null;

        return w[w.length - 2];
    }

    public CharSequence getThisWord(String sentenceSeperators) {
        if (null == mIC) return null;
        final CharSequence prev = mIC.getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0);
        return getThisWord(prev, sentenceSeperators);
    }

    // Get the word immediately before the cursor, even if there is whitespace between it and
    // the cursor - but not if there is punctuation.
    // Example :
    // "abc def|" -> def
    // "abc def |" -> def
    // "abc def. |" -> null
    // "abc def . |" -> null
    public static CharSequence getThisWord(CharSequence prev, String sentenceSeperators) {
        if (prev == null) return null;
        String[] w = spaceRegex.split(prev);

        // No word : return null
        if (w.length < 1 || w[w.length - 1].length() <= 0) return null;

        // If ends in a separator, return null
        char lastChar = w[w.length - 1].charAt(w[w.length - 1].length() - 1);
        if (sentenceSeperators.contains(String.valueOf(lastChar))) return null;

        return w[w.length - 1];
    }

    /**
     * @param separators characters which may separate words
     * @return the word that surrounds the cursor, including up to one trailing
     *   separator. For example, if the field contains "he|llo world", where |
     *   represents the cursor, then "hello " will be returned.
     */
    public String getWordAtCursor(String separators) {
        // getWordRangeAtCursor returns null if the connection is null
        Range r = getWordRangeAtCursor(separators, 0);
        return (r == null) ? null : r.mWord;
    }

    private int getCursorPosition() {
        if (null == mIC) return INVALID_CURSOR_POSITION;
        final ExtractedText extracted = mIC.getExtractedText(new ExtractedTextRequest(), 0);
        if (extracted == null) {
            return INVALID_CURSOR_POSITION;
        }
        return extracted.startOffset + extracted.selectionStart;
    }

    /**
     * Returns the text surrounding the cursor.
     *
     * @param sep a string of characters that split words.
     * @param additionalPrecedingWordsCount the number of words before the current word that should
     *   be included in the returned range
     * @return a range containing the text surrounding the cursor
     */
    public Range getWordRangeAtCursor(String sep, int additionalPrecedingWordsCount) {
        if (mIC == null || sep == null) {
            return null;
        }
        CharSequence before = mIC.getTextBeforeCursor(1000, 0);
        CharSequence after = mIC.getTextAfterCursor(1000, 0);
        if (before == null || after == null) {
            return null;
        }

        // Going backward, alternate skipping non-separators and separators until enough words
        // have been read.
        int start = before.length();
        boolean isStoppingAtWhitespace = true;  // toggles to indicate what to stop at
        while (true) { // see comments below for why this is guaranteed to halt
            while (start > 0) {
                final int codePoint = Character.codePointBefore(before, start);
                if (isStoppingAtWhitespace == isSeparator(codePoint, sep)) {
                    break;  // inner loop
                }
                --start;
                if (Character.isSupplementaryCodePoint(codePoint)) {
                    --start;
                }
            }
            // isStoppingAtWhitespace is true every other time through the loop,
            // so additionalPrecedingWordsCount is guaranteed to become < 0, which
            // guarantees outer loop termination
            if (isStoppingAtWhitespace && (--additionalPrecedingWordsCount < 0)) {
                break;  // outer loop
            }
            isStoppingAtWhitespace = !isStoppingAtWhitespace;
        }

        // Find last word separator after the cursor
        int end = -1;
        while (++end < after.length()) {
            final int codePoint = Character.codePointAt(after, end);
            if (isSeparator(codePoint, sep)) {
                break;
            }
            if (Character.isSupplementaryCodePoint(codePoint)) {
                ++end;
            }
        }

        int cursor = getCursorPosition();
        if (start >= 0 && cursor + end <= after.length() + before.length()) {
            String word = before.toString().substring(start, before.length())
                    + after.toString().substring(0, end);
            return new Range(before.length() - start, end, word);
        }

        return null;
    }

    public boolean isCursorTouchingWord(final SettingsValues settingsValues) {
        CharSequence before = getTextBeforeCursor(1, 0);
        CharSequence after = getTextAfterCursor(1, 0);
        if (!TextUtils.isEmpty(before) && !settingsValues.isWordSeparator(before.charAt(0))
                && !settingsValues.isSymbolExcludedFromWordSeparators(before.charAt(0))) {
            return true;
        }
        if (!TextUtils.isEmpty(after) && !settingsValues.isWordSeparator(after.charAt(0))
                && !settingsValues.isSymbolExcludedFromWordSeparators(after.charAt(0))) {
            return true;
        }
        return false;
    }

    public void removeTrailingSpace() {
        final CharSequence lastOne = getTextBeforeCursor(1, 0);
        if (lastOne != null && lastOne.length() == 1
                && lastOne.charAt(0) == Keyboard.CODE_SPACE) {
            deleteSurroundingText(1, 0);
            if (ProductionFlag.IS_EXPERIMENTAL) {
                ResearchLogger.latinIME_deleteSurroundingText(1);
            }
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
        // Bail out if the cursor is not at the end of a word (cursor must be preceded by
        // non-whitespace, non-separator, non-start-of-text)
        // Example ("|" is the cursor here) : <SOL>"|a" " |a" " | " all get rejected here.
        final CharSequence textBeforeCursor = getTextBeforeCursor(1, 0);
        if (TextUtils.isEmpty(textBeforeCursor)
                || settings.isWordSeparator(textBeforeCursor.charAt(0))) return null;

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
        while (!TextUtils.isEmpty(word) && Keyboard.CODE_SINGLE_QUOTE == word.charAt(0)) {
            word = word.subSequence(1, word.length());
        }
        if (TextUtils.isEmpty(word)) return null;
        final char firstChar = word.charAt(0); // we just tested that word is not empty
        if (word.length() == 1 && !Character.isLetter(firstChar)) return null;

        // We only suggest on words that start with a letter or a symbol that is excluded from
        // word separators (see #handleCharacterWhileInBatchEdit).
        if (!(Character.isLetter(firstChar)
                || settings.isSymbolExcludedFromWordSeparators(firstChar))) {
            return null;
        }

        return word;
    }
}
