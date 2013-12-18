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

import com.android.inputmethod.event.EventInterpreter;
import com.android.inputmethod.latin.LastComposedWord;
import com.android.inputmethod.latin.LatinIME;
import com.android.inputmethod.latin.RichInputConnection;
import com.android.inputmethod.latin.Suggest;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.RecapitalizeStatus;

import java.util.TreeSet;

/**
 * This class manages the input logic.
 */
public final class InputLogic {
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
}
