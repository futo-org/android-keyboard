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

import android.view.inputmethod.EditorInfo;

import java.util.Locale;

/**
 * Holder class for data about a word already committed but that may still be edited.
 *
 * When the user chooses to add a word to the user dictionary by pressing the appropriate
 * suggestion, a dialog is presented to give a chance to edit the word before it is actually
 * registered as a user dictionary word. If the word is actually modified, the IME needs to
 * go back and replace the word that was committed with the amended version.
 * The word we need to replace with will only be known after it's actually committed, so
 * the IME needs to take a note of what it has to replace and where it is.
 * This class encapsulates this data.
 */
public final class PositionalInfoForUserDictPendingAddition {
    final private String mOriginalWord;
    final private int mCursorPos; // Position of the cursor after the word
    final private EditorInfo mEditorInfo; // On what binding this has been added
    final private int mCapitalizedMode;
    private String mActualWordBeingAdded;

    public PositionalInfoForUserDictPendingAddition(final String word, final int cursorPos,
            final EditorInfo editorInfo, final int capitalizedMode) {
        mOriginalWord = word;
        mCursorPos = cursorPos;
        mEditorInfo = editorInfo;
        mCapitalizedMode = capitalizedMode;
    }

    public void setActualWordBeingAdded(final String actualWordBeingAdded) {
        mActualWordBeingAdded = actualWordBeingAdded;
    }

    /**
     * Try to replace the string at the remembered position with the actual word being added.
     *
     * After the user validated the word being added, the IME has to replace the old version
     * (which has been committed in the text view) with the amended version if it's different.
     * This method tries to do that, but may fail because the IME is not yet ready to do so -
     * for example, it is still waiting for the new string, or it is waiting to return to the text
     * view in which the amendment should be made. In these cases, we should keep the data
     * and wait until all conditions are met.
     * This method returns true if the replacement has been successfully made and this data
     * can be forgotten; it returns false if the replacement can't be made yet and we need to
     * keep this until a later time.
     * The IME knows about the actual word being added through a callback called by the
     * user dictionary facility of the device. When this callback comes, the keyboard may still
     * be connected to the edition dialog, or it may have already returned to the original text
     * field. Replacement has to work in both cases.
     * Accordingly, this method is called at two different points in time : upon getting the
     * event that a new word was added to the user dictionary, and upon starting up in a
     * new text field.
     * @param connection The RichInputConnection through which to contact the editor.
     * @param editorInfo Information pertaining to the editor we are currently in.
     * @param currentCursorPosition The current cursor position, for checking purposes.
     * @param locale The locale for changing case, if necessary
     * @return true if the edit has been successfully made, false if we need to try again later
     */
    public boolean tryReplaceWithActualWord(final RichInputConnection connection,
            final EditorInfo editorInfo, final int currentCursorPosition, final Locale locale) {
        // If we still don't know the actual word being added, we need to try again later.
        if (null == mActualWordBeingAdded) return false;
        // The entered text and the registered text were the same anyway : we can
        // return success right away even if focus has not returned yet to the text field we
        // want to amend.
        if (mActualWordBeingAdded.equals(mOriginalWord)) return true;
        // Not the same text field : we need to try again later. This happens when the addition
        // is reported by the user dictionary provider before the focus has moved back to the
        // original text view, so the IME is still in the text view of the dialog and has no way to
        // edit the original text view at this time.
        if (!mEditorInfo.packageName.equals(editorInfo.packageName)
                || mEditorInfo.fieldId != editorInfo.fieldId) {
            return false;
        }
        // Same text field, but not the same cursor position : we give up, so we return success
        // so that it won't be tried again
        if (currentCursorPosition != mCursorPos) return true;
        // We have made all the checks : do the replacement and report success
        // If this was auto-capitalized, we need to restore the case before committing
        final String wordWithCaseFixed = CapsModeUtils.applyAutoCapsMode(mActualWordBeingAdded,
                mCapitalizedMode, locale);
        connection.setComposingRegion(currentCursorPosition - mOriginalWord.length(),
                currentCursorPosition);
        connection.commitText(wordWithCaseFixed, wordWithCaseFixed.length());
        return true;
    }
}
