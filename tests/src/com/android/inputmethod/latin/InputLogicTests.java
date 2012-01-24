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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;

public class InputLogicTests extends ServiceTestCase<LatinIME> {

    private static final String PREF_DEBUG_MODE = "debug_mode";

    private LatinIME mLatinIME;
    private TextView mTextView;
    private InputConnection mInputConnection;

    public InputLogicTests() {
        super(LatinIME.class);
    }

    // returns the previous setting value
    private boolean setDebugMode(final boolean mode) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mLatinIME);
        final boolean previousDebugSetting = prefs.getBoolean(PREF_DEBUG_MODE, false);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_DEBUG_MODE, true);
        editor.commit();
        return previousDebugSetting;
    }

    @Override
    protected void setUp() {
        try {
            super.setUp();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mTextView = new TextView(getContext());
        mTextView.setInputType(InputType.TYPE_CLASS_TEXT);
        mTextView.setEnabled(true);
        setupService();
        mLatinIME = getService();
        final boolean previousDebugSetting = setDebugMode(true);
        mLatinIME.onCreate();
        setDebugMode(previousDebugSetting);
        final EditorInfo ei = new EditorInfo();
        final InputConnection ic = mTextView.onCreateInputConnection(ei);
        final LayoutInflater inflater =
                (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ViewGroup vg = new FrameLayout(getContext());
        final View inputView = inflater.inflate(R.layout.input_view, vg);
        mLatinIME.setInputView(inputView);
        mLatinIME.onBindInput();
        mLatinIME.onCreateInputView();
        mLatinIME.onStartInputView(ei, false);
        mLatinIME.onCreateInputMethodInterface().startInput(ic, ei);
        mInputConnection = ic;
    }

    // type(int) and type(String): helper methods to send a code point resp. a string to LatinIME.
    private void type(final int codePoint) {
        // onPressKey and onReleaseKey are explicitly deactivated here, but they do happen in the
        // code (although multitouch/slide input and other factors make the sequencing complicated).
        // They are supposed to be entirely deconnected from the input logic from LatinIME point of
        // view and only delegates to the parts of the code that care. So we don't include them here
        // to keep these tests as pinpoint as possible and avoid bringing it too many dependencies,
        // but keep them in mind if something breaks. Commenting them out as is should work.
        //mLatinIME.onPressKey(codePoint);
        mLatinIME.onCodeInput(codePoint, new int[] { codePoint },
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE,
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE);
        //mLatinIME.onReleaseKey(codePoint, false);
    }

    private void type(final String stringToType) {
        for (int i = 0; i < stringToType.length(); ++i) {
            type(stringToType.codePointAt(i));
        }
    }

    public void testTypeWord() {
        final String WORD_TO_TYPE = "abcd";
        type(WORD_TO_TYPE);
        assertEquals("type word", WORD_TO_TYPE, mTextView.getText().toString());
    }

    public void testPickSuggestionThenBackspace() {
        final String WORD_TO_TYPE = "tgis";
        type(WORD_TO_TYPE);
        mLatinIME.pickSuggestionManually(0, WORD_TO_TYPE);
        type(Keyboard.CODE_DELETE);
        assertEquals("press suggestion then backspace", WORD_TO_TYPE,
                mTextView.getText().toString());
    }

    public void testDeleteSelection() {
        final String STRING_TO_TYPE = "some text delete me some text";
        final int SELECTION_START = 10;
        final int SELECTION_END = 19;
        final String EXPECTED_RESULT = "some text  some text";
        type(STRING_TO_TYPE);
        // There is no IMF to call onUpdateSelection for us so we must do it by hand.
        // Send once to simulate the cursor actually responding to the move caused by typing.
        // This is necessary because LatinIME is bookkeeping to avoid confusing a real cursor
        // move with a move triggered by LatinIME inputting stuff.
        mLatinIME.onUpdateSelection(0, 0, STRING_TO_TYPE.length(), STRING_TO_TYPE.length(), -1, -1);
        mInputConnection.setSelection(SELECTION_START, SELECTION_END);
        // And now we simulate the user actually selecting some text.
        mLatinIME.onUpdateSelection(0, 0, SELECTION_START, SELECTION_END, -1, -1);
        type(Keyboard.CODE_DELETE);
        assertEquals("delete selection", EXPECTED_RESULT, mTextView.getText().toString());
    }
}
