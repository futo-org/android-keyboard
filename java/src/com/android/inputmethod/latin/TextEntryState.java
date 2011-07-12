/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.Utils.RingCharBuffer;

import android.util.Log;

public class TextEntryState {
    private static final String TAG = TextEntryState.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int UNKNOWN = 0;
    private static final int START = 1;
    private static final int IN_WORD = 2;
    private static final int ACCEPTED_DEFAULT = 3;
    private static final int PICKED_SUGGESTION = 4;
    private static final int PUNCTUATION_AFTER_WORD = 5;
    private static final int PUNCTUATION_AFTER_ACCEPTED = 6;
    private static final int SPACE_AFTER_ACCEPTED = 7;
    private static final int SPACE_AFTER_PICKED = 8;
    private static final int UNDO_COMMIT = 9;
    private static final int RECORRECTING = 10;
    private static final int PICKED_RECORRECTION = 11;

    private static int sState = UNKNOWN;
    private static int sPreviousState = UNKNOWN;

    private static void setState(final int newState) {
        sPreviousState = sState;
        sState = newState;
    }

    public static void acceptedDefault(CharSequence typedWord, CharSequence actualWord,
            int separatorCode) {
        if (typedWord == null) return;
        setState(ACCEPTED_DEFAULT);
        LatinImeLogger.logOnAutoCorrection(
                typedWord.toString(), actualWord.toString(), separatorCode);
        if (DEBUG)
            displayState("acceptedDefault", "typedWord", typedWord, "actualWord", actualWord);
    }

    // State.ACCEPTED_DEFAULT will be changed to other sub-states
    // (see "case ACCEPTED_DEFAULT" in typedCharacter() below),
    // and should be restored back to State.ACCEPTED_DEFAULT after processing for each sub-state.
    public static void backToAcceptedDefault(CharSequence typedWord) {
        if (typedWord == null) return;
        switch (sState) {
        case SPACE_AFTER_ACCEPTED:
        case PUNCTUATION_AFTER_ACCEPTED:
        case IN_WORD:
            setState(ACCEPTED_DEFAULT);
            break;
        default:
            break;
        }
        if (DEBUG) displayState("backToAcceptedDefault", "typedWord", typedWord);
    }

    public static void acceptedTyped(CharSequence typedWord) {
        setState(PICKED_SUGGESTION);
        if (DEBUG) displayState("acceptedTyped", "typedWord", typedWord);
    }

    public static void acceptedSuggestion(CharSequence typedWord, CharSequence actualWord) {
        if (sState == RECORRECTING || sState == PICKED_RECORRECTION) {
            setState(PICKED_RECORRECTION);
        } else {
            setState(PICKED_SUGGESTION);
        }
        if (DEBUG)
            displayState("acceptedSuggestion", "typedWord", typedWord, "actualWord", actualWord);
    }

    public static void selectedForRecorrection() {
        setState(RECORRECTING);
        if (DEBUG) displayState("selectedForRecorrection");
    }

    public static void onAbortRecorrection() {
        if (sState == RECORRECTING || sState == PICKED_RECORRECTION) {
            setState(START);
        }
        if (DEBUG) displayState("onAbortRecorrection");
    }

    public static void typedCharacter(char c, boolean isSeparator, int x, int y) {
        final boolean isSpace = (c == Keyboard.CODE_SPACE);
        switch (sState) {
        case IN_WORD:
            if (isSpace || isSeparator) {
                setState(START);
            } else {
                // State hasn't changed.
            }
            break;
        case ACCEPTED_DEFAULT:
        case SPACE_AFTER_PICKED:
        case PUNCTUATION_AFTER_ACCEPTED:
            if (isSpace) {
                setState(SPACE_AFTER_ACCEPTED);
            } else if (isSeparator) {
                // Swap
                setState(PUNCTUATION_AFTER_ACCEPTED);
            } else {
                setState(IN_WORD);
            }
            break;
        case PICKED_SUGGESTION:
        case PICKED_RECORRECTION:
            if (isSpace) {
                setState(SPACE_AFTER_PICKED);
            } else if (isSeparator) {
                // Swap
                setState(PUNCTUATION_AFTER_ACCEPTED);
            } else {
                setState(IN_WORD);
            }
            break;
        case START:
        case UNKNOWN:
        case SPACE_AFTER_ACCEPTED:
        case PUNCTUATION_AFTER_WORD:
            if (!isSpace && !isSeparator) {
                setState(IN_WORD);
            } else {
                setState(START);
            }
            break;
        case UNDO_COMMIT:
            if (isSpace || isSeparator) {
                setState(START);
            } else {
                setState(IN_WORD);
            }
            break;
        case RECORRECTING:
            setState(START);
            break;
        }
        RingCharBuffer.getInstance().push(c, x, y);
        if (isSeparator) {
            LatinImeLogger.logOnInputSeparator();
        } else {
            LatinImeLogger.logOnInputChar();
        }
        if (DEBUG) displayState("typedCharacter", "char", c, "isSeparator", isSeparator);
    }
    
    public static void backspace() {
        if (sState == ACCEPTED_DEFAULT) {
            setState(UNDO_COMMIT);
            LatinImeLogger.logOnAutoCorrectionCancelled();
        } else if (sState == UNDO_COMMIT) {
            setState(IN_WORD);
        }
        if (DEBUG) displayState("backspace");
    }

    public static void reset() {
        setState(START);
        if (DEBUG) displayState("reset");
    }

    public static boolean isAcceptedDefault() {
        return sState == ACCEPTED_DEFAULT;
    }

    public static boolean isSpaceAfterPicked() {
        return sState == SPACE_AFTER_PICKED;
    }

    public static boolean isUndoCommit() {
        return sState == UNDO_COMMIT;
    }

    public static boolean isPunctuationAfterAccepted() {
        return sState == PUNCTUATION_AFTER_ACCEPTED;
    }

    public static boolean isRecorrecting() {
        return sState == RECORRECTING || sState == PICKED_RECORRECTION;
    }

    public static String getState() {
        return stateName(sState);
    }

    private static String stateName(int state) {
        switch (state) {
        case START: return "START";
        case IN_WORD: return "IN_WORD";
        case ACCEPTED_DEFAULT: return "ACCEPTED_DEFAULT";
        case PICKED_SUGGESTION: return "PICKED_SUGGESTION";
        case PUNCTUATION_AFTER_WORD: return "PUNCTUATION_AFTER_WORD";
        case PUNCTUATION_AFTER_ACCEPTED: return "PUNCTUATION_AFTER_ACCEPTED";
        case SPACE_AFTER_ACCEPTED: return "SPACE_AFTER_ACCEPTED";
        case SPACE_AFTER_PICKED: return "SPACE_AFTER_PICKED";
        case UNDO_COMMIT: return "UNDO_COMMIT";
        case RECORRECTING: return "RECORRECTING";
        case PICKED_RECORRECTION: return "PICKED_RECORRECTION";
        default: return "UNKNOWN";
        }
    }

    private static void displayState(String title, Object ... args) {
        final StringBuilder sb = new StringBuilder(title);
        sb.append(':');
        for (int i = 0; i < args.length; i += 2) {
            sb.append(' ');
            sb.append(args[i]);
            sb.append('=');
            sb.append(args[i+1].toString());
        }
        sb.append(" state=");
        sb.append(stateName(sState));
        sb.append(" previous=");
        sb.append(stateName(sPreviousState));
        Log.d(TAG, sb.toString());
    }
}
