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

package com.android.inputmethod.tools.edittextvariations;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.Locale;

final class EchoingTextWatcher implements TextWatcher {
    private static final int SET_TEXT_DELAY = 500;

    final EditText mEditText;
    CharSequence mExpected;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            final String toBeappended = (String) msg.obj;
            final CharSequence current = mEditText.getText();
            final CharSequence newText = TextUtils.concat(current, toBeappended);
            mExpected = newText;
            mEditText.setText(newText);
            mEditText.setSelection(newText.length());
        }
    };

    @SuppressWarnings("unused")
    public static void attachTo(final EditText editText) {
        final EchoingTextWatcher watcher = new EchoingTextWatcher(editText);
    }

    public EchoingTextWatcher(final EditText editText) {
        mEditText = editText;
        editText.addTextChangedListener(this);
    }

    @Override
    public void afterTextChanged(final Editable ss) {
    }

    @Override
    public void beforeTextChanged(final CharSequence s, final int start, final int count,
            final int after) {
    }

    @Override
    public void onTextChanged(final CharSequence s, final int start, final int before,
            final int count) {
        if (count == 0 || before > 0 || TextUtils.equals(s, mExpected)) {
            return;
        }
        final int len = s.length();
        if (len > 0) {
            final String last = s.subSequence(len - 1, len).toString();
            final char lastChar = last.charAt(0);
            if (Character.isUpperCase(lastChar)) {
                final String lowerCase = last.toLowerCase(Locale.getDefault());
                mHandler.sendMessageDelayed(mHandler.obtainMessage(0, lowerCase), SET_TEXT_DELAY);
            } else if (Character.isLowerCase(lastChar)) {
                final String upperCase = last.toUpperCase(Locale.getDefault());
                mHandler.sendMessageDelayed(mHandler.obtainMessage(0, upperCase), SET_TEXT_DELAY);
            }
        }
    }
}
