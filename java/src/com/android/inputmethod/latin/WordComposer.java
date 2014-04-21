/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.inputmethod.event.CombinerChain;
import com.android.inputmethod.event.Event;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.CoordinateUtils;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;

/**
 * A place to store the currently composing word with information such as adjacent key codes as well
 */
public final class WordComposer {
    private static final int MAX_WORD_LENGTH = Constants.DICTIONARY_MAX_WORD_LENGTH;
    private static final boolean DBG = LatinImeLogger.sDBG;

    public static final int CAPS_MODE_OFF = 0;
    // 1 is shift bit, 2 is caps bit, 4 is auto bit but this is just a convention as these bits
    // aren't used anywhere in the code
    public static final int CAPS_MODE_MANUAL_SHIFTED = 0x1;
    public static final int CAPS_MODE_MANUAL_SHIFT_LOCKED = 0x3;
    public static final int CAPS_MODE_AUTO_SHIFTED = 0x5;
    public static final int CAPS_MODE_AUTO_SHIFT_LOCKED = 0x7;

    private CombinerChain mCombinerChain;

    // The list of events that served to compose this string.
    private final ArrayList<Event> mEvents;
    private final InputPointers mInputPointers = new InputPointers(MAX_WORD_LENGTH);
    // The previous word (before the composing word). Used as context for suggestions. May be null
    // after resetting and before starting a new composing word, or when there is no context like
    // at the start of text for example. It can also be set to null externally when the user
    // enters a separator that does not let bigrams across, like a period or a comma.
    private String mPreviousWordForSuggestion;
    private String mAutoCorrection;
    private boolean mIsResumed;
    private boolean mIsBatchMode;
    // A memory of the last rejected batch mode suggestion, if any. This goes like this: the user
    // gestures a word, is displeased with the results and hits backspace, then gestures again.
    // At the very least we should avoid re-suggesting the same thing, and to do that we memorize
    // the rejected suggestion in this variable.
    // TODO: this should be done in a comprehensive way by the User History feature instead of
    // as an ad-hockery here.
    private String mRejectedBatchModeSuggestion;

    // Cache these values for performance
    private CharSequence mTypedWordCache;
    private int mCapsCount;
    private int mDigitsCount;
    private int mCapitalizedMode;
    // This is the number of code points entered so far. This is not limited to MAX_WORD_LENGTH.
    // In general, this contains the size of mPrimaryKeyCodes, except when this is greater than
    // MAX_WORD_LENGTH in which case mPrimaryKeyCodes only contain the first MAX_WORD_LENGTH
    // code points.
    private int mCodePointSize;
    private int mCursorPositionWithinWord;

    /**
     * Whether the user chose to capitalize the first char of the word.
     */
    private boolean mIsFirstCharCapitalized;

    public WordComposer() {
        mCombinerChain = new CombinerChain();
        mEvents = CollectionUtils.newArrayList();
        mAutoCorrection = null;
        mIsResumed = false;
        mIsBatchMode = false;
        mCursorPositionWithinWord = 0;
        mRejectedBatchModeSuggestion = null;
        mPreviousWordForSuggestion = null;
        refreshTypedWordCache();
    }

    /**
     * Clear out the keys registered so far.
     */
    public void reset() {
        mCombinerChain.reset();
        mEvents.clear();
        mAutoCorrection = null;
        mCapsCount = 0;
        mDigitsCount = 0;
        mIsFirstCharCapitalized = false;
        mIsResumed = false;
        mIsBatchMode = false;
        mCursorPositionWithinWord = 0;
        mRejectedBatchModeSuggestion = null;
        mPreviousWordForSuggestion = null;
        refreshTypedWordCache();
    }

    private final void refreshTypedWordCache() {
        mTypedWordCache = mCombinerChain.getComposingWordWithCombiningFeedback();
        mCodePointSize = Character.codePointCount(mTypedWordCache, 0, mTypedWordCache.length());
    }

    /**
     * Number of keystrokes in the composing word.
     * @return the number of keystrokes
     */
    // This may be made public if need be, but right now it's not used anywhere
    /* package for tests */ int size() {
        return mCodePointSize;
    }

    /**
     * Copy the code points in the typed word to a destination array of ints.
     *
     * If the array is too small to hold the code points in the typed word, nothing is copied and
     * -1 is returned.
     *
     * @param destination the array of ints.
     * @return the number of copied code points.
     */
    public int copyCodePointsExceptTrailingSingleQuotesAndReturnCodePointCount(
            final int[] destination) {
        // lastIndex is exclusive
        final int lastIndex = mTypedWordCache.length() - trailingSingleQuotesCount();
        if (lastIndex <= 0) {
            // The string is empty or contains only single quotes.
            return 0;
        }

        // The following function counts the number of code points in the text range which begins
        // at index 0 and extends to the character at lastIndex.
        final int codePointSize = Character.codePointCount(mTypedWordCache, 0, lastIndex);
        if (codePointSize > destination.length) {
            return -1;
        }
        return StringUtils.copyCodePointsAndReturnCodePointCount(destination, mTypedWordCache, 0,
                lastIndex, true /* downCase */);
    }

    public boolean isSingleLetter() {
        return size() == 1;
    }

    public final boolean isComposingWord() {
        return size() > 0;
    }

    public InputPointers getInputPointers() {
        return mInputPointers;
    }

    private static boolean isFirstCharCapitalized(final int index, final int codePoint,
            final boolean previous) {
        if (index == 0) return Character.isUpperCase(codePoint);
        return previous && !Character.isUpperCase(codePoint);
    }

    /**
     * Process an input event.
     *
     * All input events should be supported, including software/hardware events, characters as well
     * as deletions, multiple inputs and gestures.
     *
     * @param event the event to process.
     */
    public void processEvent(final Event event) {
        final int primaryCode = event.mCodePoint;
        final int keyX = event.mX;
        final int keyY = event.mY;
        final int newIndex = size();
        mCombinerChain.processEvent(mEvents, event);
        mEvents.add(event);
        refreshTypedWordCache();
        mCursorPositionWithinWord = mCodePointSize;
        // We may have deleted the last one.
        if (0 == mCodePointSize) {
            mIsFirstCharCapitalized = false;
        }
        if (Constants.CODE_DELETE != event.mKeyCode) {
            if (newIndex < MAX_WORD_LENGTH) {
                // In the batch input mode, the {@code mInputPointers} holds batch input points and
                // shouldn't be overridden by the "typed key" coordinates
                // (See {@link #setBatchInputWord}).
                if (!mIsBatchMode) {
                    // TODO: Set correct pointer id and time
                    mInputPointers.addPointerAt(newIndex, keyX, keyY, 0, 0);
                }
            }
            mIsFirstCharCapitalized = isFirstCharCapitalized(
                    newIndex, primaryCode, mIsFirstCharCapitalized);
            if (Character.isUpperCase(primaryCode)) mCapsCount++;
            if (Character.isDigit(primaryCode)) mDigitsCount++;
        }
        mAutoCorrection = null;
    }

    public void setCursorPositionWithinWord(final int posWithinWord) {
        mCursorPositionWithinWord = posWithinWord;
        // TODO: compute where that puts us inside the events
    }

    public boolean isCursorFrontOrMiddleOfComposingWord() {
        if (DBG && mCursorPositionWithinWord > mCodePointSize) {
            throw new RuntimeException("Wrong cursor position : " + mCursorPositionWithinWord
                    + "in a word of size " + mCodePointSize);
        }
        return mCursorPositionWithinWord != mCodePointSize;
    }

    /**
     * When the cursor is moved by the user, we need to update its position.
     * If it falls inside the currently composing word, we don't reset the composition, and
     * only update the cursor position.
     *
     * @param expectedMoveAmount How many java chars to move the cursor. Negative values move
     * the cursor backward, positive values move the cursor forward.
     * @return true if the cursor is still inside the composing word, false otherwise.
     */
    public boolean moveCursorByAndReturnIfInsideComposingWord(final int expectedMoveAmount) {
        // TODO: should uncommit the composing feedback
        mCombinerChain.reset();
        int actualMoveAmountWithinWord = 0;
        int cursorPos = mCursorPositionWithinWord;
        // TODO: Don't make that copy. We can do this directly from mTypedWordCache.
        final int[] codePoints = StringUtils.toCodePointArray(mTypedWordCache);
        if (expectedMoveAmount >= 0) {
            // Moving the cursor forward for the expected amount or until the end of the word has
            // been reached, whichever comes first.
            while (actualMoveAmountWithinWord < expectedMoveAmount && cursorPos < mCodePointSize) {
                actualMoveAmountWithinWord += Character.charCount(codePoints[cursorPos]);
                ++cursorPos;
            }
        } else {
            // Moving the cursor backward for the expected amount or until the start of the word
            // has been reached, whichever comes first.
            while (actualMoveAmountWithinWord > expectedMoveAmount && cursorPos > 0) {
                --cursorPos;
                actualMoveAmountWithinWord -= Character.charCount(codePoints[cursorPos]);
            }
        }
        // If the actual and expected amounts differ, we crossed the start or the end of the word
        // so the result would not be inside the composing word.
        if (actualMoveAmountWithinWord != expectedMoveAmount) return false;
        mCursorPositionWithinWord = cursorPos;
        return true;
    }

    public void setBatchInputPointers(final InputPointers batchPointers) {
        mInputPointers.set(batchPointers);
        mIsBatchMode = true;
    }

    public void setBatchInputWord(final String word) {
        reset();
        mIsBatchMode = true;
        final int length = word.length();
        for (int i = 0; i < length; i = Character.offsetByCodePoints(word, i, 1)) {
            final int codePoint = Character.codePointAt(word, i);
            // We don't want to override the batch input points that are held in mInputPointers
            // (See {@link #add(int,int,int)}).
            processEvent(Event.createEventForCodePointFromUnknownSource(codePoint));
        }
    }

    /**
     * Set the currently composing word to the one passed as an argument.
     * This will register NOT_A_COORDINATE for X and Ys, and use the passed keyboard for proximity.
     * @param codePoints the code points to set as the composing word.
     * @param coordinates the x, y coordinates of the key in the CoordinateUtils format
     * @param previousWord the previous word, to use as context for suggestions. Can be null if
     *   the context is nil (typically, at start of text).
     */
    public void setComposingWord(final int[] codePoints, final int[] coordinates,
            final CharSequence previousWord) {
        reset();
        final int length = codePoints.length;
        for (int i = 0; i < length; ++i) {
            processEvent(Event.createEventForCodePointFromAlreadyTypedText(codePoints[i],
                    CoordinateUtils.xFromArray(coordinates, i),
                    CoordinateUtils.yFromArray(coordinates, i)));
        }
        mIsResumed = true;
        mPreviousWordForSuggestion = null == previousWord ? null : previousWord.toString();
    }

    /**
     * Returns the word as it was typed, without any correction applied.
     * @return the word that was typed so far. Never returns null.
     */
    public String getTypedWord() {
        return mTypedWordCache.toString();
    }

    public String getPreviousWordForSuggestion() {
        return mPreviousWordForSuggestion;
    }

    /**
     * Whether or not the user typed a capital letter as the first letter in the word
     * @return capitalization preference
     */
    public boolean isFirstCharCapitalized() {
        return mIsFirstCharCapitalized;
    }

    public int trailingSingleQuotesCount() {
        final int lastIndex = mTypedWordCache.length() - 1;
        int i = lastIndex;
        while (i >= 0 && mTypedWordCache.charAt(i) == Constants.CODE_SINGLE_QUOTE) {
            --i;
        }
        return lastIndex - i;
    }

    /**
     * Whether or not all of the user typed chars are upper case
     * @return true if all user typed chars are upper case, false otherwise
     */
    public boolean isAllUpperCase() {
        if (size() <= 1) {
            return mCapitalizedMode == CAPS_MODE_AUTO_SHIFT_LOCKED
                    || mCapitalizedMode == CAPS_MODE_MANUAL_SHIFT_LOCKED;
        } else {
            return mCapsCount == size();
        }
    }

    public boolean wasShiftedNoLock() {
        return mCapitalizedMode == CAPS_MODE_AUTO_SHIFTED
                || mCapitalizedMode == CAPS_MODE_MANUAL_SHIFTED;
    }

    /**
     * Returns true if more than one character is upper case, otherwise returns false.
     */
    public boolean isMostlyCaps() {
        return mCapsCount > 1;
    }

    /**
     * Returns true if we have digits in the composing word.
     */
    public boolean hasDigits() {
        return mDigitsCount > 0;
    }

    /**
     * Saves the caps mode and the previous word at the start of composing.
     *
     * WordComposer needs to know about the caps mode for several reasons. The first is, we need
     * to know after the fact what the reason was, to register the correct form into the user
     * history dictionary: if the word was automatically capitalized, we should insert it in
     * all-lower case but if it's a manual pressing of shift, then it should be inserted as is.
     * Also, batch input needs to know about the current caps mode to display correctly
     * capitalized suggestions.
     * @param mode the mode at the time of start
     * @param previousWord the previous word as context for suggestions. May be null if none.
     */
    public void setCapitalizedModeAndPreviousWordAtStartComposingTime(final int mode,
            final CharSequence previousWord) {
        mCapitalizedMode = mode;
        mPreviousWordForSuggestion = null == previousWord ? null : previousWord.toString();
    }

    /**
     * Returns whether the word was automatically capitalized.
     * @return whether the word was automatically capitalized
     */
    public boolean wasAutoCapitalized() {
        return mCapitalizedMode == CAPS_MODE_AUTO_SHIFT_LOCKED
                || mCapitalizedMode == CAPS_MODE_AUTO_SHIFTED;
    }

    /**
     * Sets the auto-correction for this word.
     */
    public void setAutoCorrection(final String correction) {
        mAutoCorrection = correction;
    }

    /**
     * @return the auto-correction for this word, or null if none.
     */
    public String getAutoCorrectionOrNull() {
        return mAutoCorrection;
    }

    /**
     * @return whether we started composing this word by resuming suggestion on an existing string
     */
    public boolean isResumed() {
        return mIsResumed;
    }

    // `type' should be one of the LastComposedWord.COMMIT_TYPE_* constants above.
    // committedWord should contain suggestion spans if applicable.
    public LastComposedWord commitWord(final int type, final CharSequence committedWord,
            final String separatorString, final String prevWord) {
        // Note: currently, we come here whenever we commit a word. If it's a MANUAL_PICK
        // or a DECIDED_WORD we may cancel the commit later; otherwise, we should deactivate
        // the last composed word to ensure this does not happen.
        final LastComposedWord lastComposedWord = new LastComposedWord(mEvents,
                mInputPointers, mTypedWordCache.toString(), committedWord, separatorString,
                prevWord, mCapitalizedMode);
        mInputPointers.reset();
        if (type != LastComposedWord.COMMIT_TYPE_DECIDED_WORD
                && type != LastComposedWord.COMMIT_TYPE_MANUAL_PICK) {
            lastComposedWord.deactivate();
        }
        mCapsCount = 0;
        mDigitsCount = 0;
        mIsBatchMode = false;
        mPreviousWordForSuggestion = committedWord.toString();
        mCombinerChain.reset();
        mEvents.clear();
        mCodePointSize = 0;
        mIsFirstCharCapitalized = false;
        mCapitalizedMode = CAPS_MODE_OFF;
        refreshTypedWordCache();
        mAutoCorrection = null;
        mCursorPositionWithinWord = 0;
        mIsResumed = false;
        mRejectedBatchModeSuggestion = null;
        return lastComposedWord;
    }

    // Call this when the recorded previous word should be discarded. This is typically called
    // when the user inputs a separator that's not whitespace (including the case of the
    // double-space-to-period feature).
    public void discardPreviousWordForSuggestion() {
        mPreviousWordForSuggestion = null;
    }

    public void resumeSuggestionOnLastComposedWord(final LastComposedWord lastComposedWord,
            final String previousWord) {
        mEvents.clear();
        Collections.copy(mEvents, lastComposedWord.mEvents);
        mInputPointers.set(lastComposedWord.mInputPointers);
        mCombinerChain.reset();
        refreshTypedWordCache();
        mCapitalizedMode = lastComposedWord.mCapitalizedMode;
        mAutoCorrection = null; // This will be filled by the next call to updateSuggestion.
        mCursorPositionWithinWord = mCodePointSize;
        mRejectedBatchModeSuggestion = null;
        mIsResumed = true;
        mPreviousWordForSuggestion = previousWord;
    }

    public boolean isBatchMode() {
        return mIsBatchMode;
    }

    public void setRejectedBatchModeSuggestion(final String rejectedSuggestion) {
        mRejectedBatchModeSuggestion = rejectedSuggestion;
    }

    public String getRejectedBatchModeSuggestion() {
        return mRejectedBatchModeSuggestion;
    }
}
