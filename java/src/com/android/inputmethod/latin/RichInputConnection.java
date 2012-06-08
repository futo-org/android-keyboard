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

import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.InputConnection;

/**
 * Wrapper for InputConnection to simplify interaction
 */
public class RichInputConnection {
    private static final String TAG = RichInputConnection.class.getSimpleName();
    private static final boolean DBG = false;
    InputConnection mIC;
    int mNestLevel;
    public RichInputConnection() {
        mIC = null;
        mNestLevel = 0;
    }

    // TODO: remove this method - the whole point of this class is void if mIC is escaping
    public InputConnection getInputConnection() {
        return mIC;
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
}
