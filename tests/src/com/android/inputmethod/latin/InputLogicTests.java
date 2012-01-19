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

    private LatinIME mLatinIME;
    private TextView mTextView;

    public InputLogicTests() {
        super(LatinIME.class);
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
        mLatinIME.onCreate();
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
        final String wordToType = "abcd";
        type(wordToType);
        assertEquals("type word", wordToType, mTextView.getText().toString());
    }

    public void testPickSuggestionThenBackspace() {
        final String wordToType = "tgis";
        type(wordToType);
        mLatinIME.pickSuggestionManually(0, wordToType);
        type(Keyboard.CODE_DELETE);
        assertEquals("press suggestion then backspace", wordToType, mTextView.getText().toString());
    }

}
