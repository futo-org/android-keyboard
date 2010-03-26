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

import android.content.Context;
import android.text.format.DateFormat;
import android.util.Log;

import android.inputmethodservice.Keyboard.Key;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;

public class TextEntryState {
    
    private static boolean LOGGING = false;
    
    private static int sBackspaceCount = 0;
    
    private static int sAutoSuggestCount = 0;
    
    private static int sAutoSuggestUndoneCount = 0;
    
    private static int sManualSuggestCount = 0;
    
    private static int sWordNotInDictionaryCount = 0;
    
    private static int sSessionCount = 0;
    
    private static int sTypedChars;
    
    private static int sActualChars;
    
    private static final String[] STATES = {
        "Unknown",
        "Start", 
        "In word",
        "Accepted default",
        "Picked suggestion",
        "Punc. after word",
        "Punc. after accepted",
        "Space after accepted",
        "Space after picked",
        "Undo commit"
    };
    
    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_START = 1;
    public static final int STATE_IN_WORD = 2;
    public static final int STATE_ACCEPTED_DEFAULT = 3;
    public static final int STATE_PICKED_SUGGESTION = 4;
    public static final int STATE_PUNCTUATION_AFTER_WORD = 5;
    public static final int STATE_PUNCTUATION_AFTER_ACCEPTED = 6;
    public static final int STATE_SPACE_AFTER_ACCEPTED = 7;
    public static final int STATE_SPACE_AFTER_PICKED = 8;
    public static final int STATE_UNDO_COMMIT = 9;
    
    private static int sState = STATE_UNKNOWN;
    
    private static FileOutputStream sKeyLocationFile;
    private static FileOutputStream sUserActionFile;
    
    public static void newSession(Context context) {
        sSessionCount++;
        sAutoSuggestCount = 0;
        sBackspaceCount = 0;
        sAutoSuggestUndoneCount = 0;
        sManualSuggestCount = 0;
        sWordNotInDictionaryCount = 0;
        sTypedChars = 0;
        sActualChars = 0;
        sState = STATE_START;
        
        if (LOGGING) {
            try {
                sKeyLocationFile = context.openFileOutput("key.txt", Context.MODE_APPEND);
                sUserActionFile = context.openFileOutput("action.txt", Context.MODE_APPEND);
            } catch (IOException ioe) {
                Log.e("TextEntryState", "Couldn't open file for output: " + ioe);
            }
        }
    }
    
    public static void endSession() {
        if (sKeyLocationFile == null) {
            return;
        }
        try {
            sKeyLocationFile.close();
            // Write to log file            
            // Write timestamp, settings,
            String out = DateFormat.format("MM:dd hh:mm:ss", Calendar.getInstance().getTime())
                    .toString()
                    + " BS: " + sBackspaceCount
                    + " auto: " + sAutoSuggestCount
                    + " manual: " + sManualSuggestCount
                    + " typed: " + sWordNotInDictionaryCount
                    + " undone: " + sAutoSuggestUndoneCount
                    + " saved: " + ((float) (sActualChars - sTypedChars) / sActualChars)
                    + "\n";
            sUserActionFile.write(out.getBytes());
            sUserActionFile.close();
            sKeyLocationFile = null;
            sUserActionFile = null;
        } catch (IOException ioe) {
            
        }
    }
    
    public static void acceptedDefault(CharSequence typedWord, CharSequence actualWord) {
        if (typedWord == null) return;
        if (!typedWord.equals(actualWord)) {
            sAutoSuggestCount++;
        }
        sTypedChars += typedWord.length();
        sActualChars += actualWord.length();
        sState = STATE_ACCEPTED_DEFAULT;
    }
    
    public static void acceptedTyped(CharSequence typedWord) {
        sWordNotInDictionaryCount++;
        sState = STATE_PICKED_SUGGESTION;
    }

    public static void acceptedSuggestion(CharSequence typedWord, CharSequence actualWord) {
        sManualSuggestCount++;
        if (typedWord.equals(actualWord)) {
            acceptedTyped(typedWord);
        }
        sState = STATE_PICKED_SUGGESTION;
    }
    
    public static void typedCharacter(char c, boolean isSeparator) {
        boolean isSpace = c == ' ';
        switch (sState) {
            case STATE_IN_WORD:
                if (isSpace || isSeparator) {
                    sState = STATE_START;
                } else {
                    // State hasn't changed.
                }
                break;
            case STATE_ACCEPTED_DEFAULT:
            case STATE_SPACE_AFTER_PICKED:
                if (isSpace) {
                    sState = STATE_SPACE_AFTER_ACCEPTED;
                } else if (isSeparator) {
                    sState = STATE_PUNCTUATION_AFTER_ACCEPTED;
                } else {
                    sState = STATE_IN_WORD;
                }
                break;
            case STATE_PICKED_SUGGESTION:
                if (isSpace) {
                    sState = STATE_SPACE_AFTER_PICKED;
                } else if (isSeparator) {
                    // Swap 
                    sState = STATE_PUNCTUATION_AFTER_ACCEPTED;
                } else {
                    sState = STATE_IN_WORD;
                }
                break;
            case STATE_START:
            case STATE_UNKNOWN:
            case STATE_SPACE_AFTER_ACCEPTED:
            case STATE_PUNCTUATION_AFTER_ACCEPTED:
            case STATE_PUNCTUATION_AFTER_WORD:
                if (!isSpace && !isSeparator) {
                    sState = STATE_IN_WORD;
                } else {
                    sState = STATE_START;
                }
                break;
            case STATE_UNDO_COMMIT:
                if (isSpace || isSeparator) {
                    sState = STATE_ACCEPTED_DEFAULT;
                } else {
                    sState = STATE_IN_WORD;
                }
        }
    }
    
    public static void backspace() {
        if (sState == STATE_ACCEPTED_DEFAULT) {
            sState = STATE_UNDO_COMMIT;
            sAutoSuggestUndoneCount++;
        } else if (sState == STATE_UNDO_COMMIT) {
            sState = STATE_IN_WORD;
        }
        sBackspaceCount++;
    }
    
    public static void reset() {
        sState = STATE_START;
    }
    
    public static int getState() {
        return sState;
    }
    
    public static void keyPressedAt(Key key, int x, int y) {
        if (LOGGING && sKeyLocationFile != null && key.codes[0] >= 32) {
            String out = 
                    "KEY: " + (char) key.codes[0] 
                    + " X: " + x 
                    + " Y: " + y
                    + " MX: " + (key.x + key.width / 2)
                    + " MY: " + (key.y + key.height / 2) 
                    + "\n";
            try {
                sKeyLocationFile.write(out.getBytes());
            } catch (IOException ioe) {
                // TODO: May run out of space
            }
        }
    }
}

