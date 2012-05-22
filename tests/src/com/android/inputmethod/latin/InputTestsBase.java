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
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.MessageQueue;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.SuggestionSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;

import java.util.HashMap;

public class InputTestsBase extends ServiceTestCase<LatinIME> {

    private static final String PREF_DEBUG_MODE = "debug_mode";

    // The message that sets the underline is posted with a 100 ms delay
    protected static final int DELAY_TO_WAIT_FOR_UNDERLINE = 200;

    protected LatinIME mLatinIME;
    protected Keyboard mKeyboard;
    protected TextView mTextView;
    protected InputConnection mInputConnection;
    private final HashMap<String, InputMethodSubtype> mSubtypeMap =
            new HashMap<String, InputMethodSubtype>();

    // A helper class to ease span tests
    public static class SpanGetter {
        final SpannableStringBuilder mInputText;
        final CharacterStyle mSpan;
        final int mStart;
        final int mEnd;
        // The supplied CharSequence should be an instance of SpannableStringBuilder,
        // and it should contain exactly zero or one span. Otherwise, an exception
        // is thrown.
        public SpanGetter(final CharSequence inputText,
                final Class<? extends CharacterStyle> spanType) {
            mInputText = (SpannableStringBuilder)inputText;
            final CharacterStyle[] spans =
                    mInputText.getSpans(0, mInputText.length(), spanType);
            if (0 == spans.length) {
                mSpan = null;
                mStart = -1;
                mEnd = -1;
            } else if (1 == spans.length) {
                mSpan = spans[0];
                mStart = mInputText.getSpanStart(mSpan);
                mEnd = mInputText.getSpanEnd(mSpan);
            } else {
                throw new RuntimeException("Expected one span, found " + spans.length);
            }
        }
        public boolean isAutoCorrectionIndicator() {
            return (mSpan instanceof SuggestionSpan) &&
                    0 != (SuggestionSpan.FLAG_AUTO_CORRECTION & ((SuggestionSpan)mSpan).getFlags());
        }
    }

    public InputTestsBase() {
        super(LatinIME.class);
    }

    // TODO: Isn't there a way to make this generic somehow? We can take a <T> and return a <T>
    // but we'd have to dispatch types on editor.put...() functions
    protected boolean setBooleanPreference(final String key, final boolean value,
            final boolean defaultValue) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mLatinIME);
        final boolean previousSetting = prefs.getBoolean(key, defaultValue);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.commit();
        return previousSetting;
    }

    // returns the previous setting value
    protected boolean setDebugMode(final boolean value) {
        return setBooleanPreference(PREF_DEBUG_MODE, value, false);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTextView = new TextView(getContext());
        mTextView.setInputType(InputType.TYPE_CLASS_TEXT);
        mTextView.setEnabled(true);
        setupService();
        mLatinIME = getService();
        final boolean previousDebugSetting = setDebugMode(true);
        mLatinIME.onCreate();
        setDebugMode(previousDebugSetting);
        initSubtypeMap();
        final EditorInfo ei = new EditorInfo();
        ei.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        final InputConnection ic = mTextView.onCreateInputConnection(ei);
        ei.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
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
        mKeyboard = mLatinIME.mKeyboardSwitcher.getKeyboard();
        changeLanguage("en_US");
    }

    private void initSubtypeMap() {
        final InputMethodManager imm = (InputMethodManager)mLatinIME.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        final String packageName = mLatinIME.getPackageName();
        // The IMEs and subtypes don't need to be enabled to run this test because IMF isn't
        // involved here.
        for (final InputMethodInfo imi : imm.getInputMethodList()) {
            if (imi.getPackageName().equals(packageName)) {
                final int subtypeCount = imi.getSubtypeCount();
                for (int i = 0; i < subtypeCount; i++) {
                    final InputMethodSubtype ims = imi.getSubtypeAt(i);
                    final String locale = ims.getLocale();
                    mSubtypeMap.put(locale, ims);
                }
                return;
            }
        }
        fail("LatinIME is not found");
    }

    // We need to run the messages added to the handler from LatinIME. The only way to do
    // that is to call Looper#loop() on the right looper, so we're going to get the looper
    // object and call #loop() here. The messages in the handler actually run on the UI
    // thread of the keyboard by design of the handler, so we want to call it synchronously
    // on the same thread that the tests are running on to mimic the actual environment as
    // closely as possible.
    // Now, Looper#loop() never exits in normal operation unless the Looper#quit() method
    // is called, so we need to do that at the right time so that #loop() returns at some
    // point and we don't end up in an infinite loop.
    // After we quit, the looper is still technically ready to process more messages but
    // the handler will refuse to enqueue any because #quit() has been called and it
    // explicitly tests for it on message enqueuing, so we'll have to reset it so that
    // it lets us continue normal operation.
    protected void runMessages() {
        // Here begins deep magic.
        final Looper looper = mLatinIME.mHandler.getLooper();
        mLatinIME.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    looper.quit();
                }
            });
        // The only way to get out of Looper#loop() is to call #quit() on it (or on its queue).
        // Once #quit() is called remaining messages are not processed, which is why we post
        // a message that calls it instead of calling it directly.
        Looper.loop();

        // Once #quit() has been called, the message queue has an "mQuiting" field that prevents
        // any subsequent post in this queue. However the queue itself is still fully functional!
        // If we have a way of resetting "queue.mQuiting" then we can continue using it as normal,
        // coming back to this method to run the messages.
        MessageQueue queue = looper.getQueue();
        try {
            // However there is no way of doing it externally, and mQuiting is private.
            // So... get out the big guns.
            java.lang.reflect.Field f = MessageQueue.class.getDeclaredField("mQuiting");
            f.setAccessible(true); // What do you mean "private"?
            f.setBoolean(queue, false);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // type(int) and type(String): helper methods to send a code point resp. a string to LatinIME.
    protected void type(final int codePoint) {
        // onPressKey and onReleaseKey are explicitly deactivated here, but they do happen in the
        // code (although multitouch/slide input and other factors make the sequencing complicated).
        // They are supposed to be entirely deconnected from the input logic from LatinIME point of
        // view and only delegates to the parts of the code that care. So we don't include them here
        // to keep these tests as pinpoint as possible and avoid bringing it too many dependencies,
        // but keep them in mind if something breaks. Commenting them out as is should work.
        //mLatinIME.onPressKey(codePoint);
        for (final Key key : mKeyboard.mKeys) {
            if (key.mCode == codePoint) {
                final int x = key.mX + key.mWidth / 2;
                final int y = key.mY + key.mHeight / 2;
                mLatinIME.onCodeInput(codePoint, x, y);
                return;
            }
        }
        mLatinIME.onCodeInput(codePoint,
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE,
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE);
        //mLatinIME.onReleaseKey(codePoint, false);
    }

    protected void type(final String stringToType) {
        for (int i = 0; i < stringToType.length(); i = stringToType.offsetByCodePoints(i, 1)) {
            type(stringToType.codePointAt(i));
        }
    }

    protected void waitForDictionaryToBeLoaded() {
        int remainingAttempts = 10;
        while (remainingAttempts > 0 && !mLatinIME.mSuggest.hasMainDictionary()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Don't do much
            } finally {
                --remainingAttempts;
            }
        }
        if (!mLatinIME.mSuggest.hasMainDictionary()) {
            throw new RuntimeException("Can't initialize the main dictionary");
        }
    }

    protected void changeLanguage(final String locale) {
        final InputMethodSubtype subtype = mSubtypeMap.get(locale);
        if (subtype == null) {
            fail("InputMethodSubtype for locale " + locale + " is not enabled");
        }
        SubtypeSwitcher.getInstance().updateSubtype(subtype);
        waitForDictionaryToBeLoaded();
    }

    protected void pickSuggestionManually(final int index, final CharSequence suggestion) {
        mLatinIME.pickSuggestionManually(index, suggestion,
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE,
                KeyboardActionListener.NOT_A_TOUCH_COORDINATE);
    }

    // Helper to avoid writing the try{}catch block each time
    protected static void sleep(final int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {}
    }
}
