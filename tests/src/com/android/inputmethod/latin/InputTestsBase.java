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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.SuggestionSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils;
import com.android.inputmethod.event.Event;
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.Dictionary.PhonyDictionary;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.InputPointers;
import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.common.StringUtils;
import com.android.inputmethod.latin.settings.DebugSettings;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class InputTestsBase extends ServiceTestCase<LatinIMEForTests> {
    private static final String TAG = InputTestsBase.class.getSimpleName();

    // Default value for auto-correction threshold. This is the string representation of the
    // index in the resources array of auto-correction threshold settings.
    private static final boolean DEFAULT_AUTO_CORRECTION = true;

    // The message that sets the underline is posted with a 500 ms delay
    protected static final int DELAY_TO_WAIT_FOR_UNDERLINE_MILLIS = 500;
    // The message that sets predictions is posted with a 200 ms delay
    protected static final int DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS = 200;
    // We wait for gesture computation for this delay
    protected static final int DELAY_TO_WAIT_FOR_GESTURE_MILLIS = 200;
    // If a dictionary takes longer to load, we could have serious problems.
    private final int TIMEOUT_TO_WAIT_FOR_LOADING_MAIN_DICTIONARY_IN_SECONDS = 5;

    // Type for a test phony dictionary
    private static final String TYPE_TEST = "test";
    private static final PhonyDictionary DICTIONARY_TEST = new PhonyDictionary(TYPE_TEST);

    protected LatinIME mLatinIME;
    protected Keyboard mKeyboard;
    protected MyEditText mEditText;
    protected View mInputView;
    protected InputConnection mInputConnection;
    private boolean mPreviousAutoCorrectSetting;
    private boolean mPreviousBigramPredictionSettings;

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
        public SuggestionSpan getSpan() {
            return (SuggestionSpan) mSpan;
        }
        public boolean isAutoCorrectionIndicator() {
            return (mSpan instanceof SuggestionSpan) &&
                    0 != (SuggestionSpan.FLAG_AUTO_CORRECTION & getSpan().getFlags());
        }
        public String[] getSuggestions() {
            return getSpan().getSuggestions();
        }
    }

    // A helper class to increase control over the EditText
    public static class MyEditText extends EditText {
        public Locale mCurrentLocale;
        public MyEditText(final Context c) {
            super(c);
        }

        @Override
        public void onAttachedToWindow() {
            // Make onAttachedToWindow "public"
            super.onAttachedToWindow();
        }

        // overriding hidden API in EditText
        public Locale getTextServicesLocale() {
            // This method is necessary because EditText is asking this method for the language
            // to check the spell in. If we don't override this, the spell checker will run in
            // whatever language the keyboard is currently set on the test device, ignoring any
            // settings we do inside the tests.
            return mCurrentLocale;
        }

        // overriding hidden API in EditText
        public Locale getSpellCheckerLocale() {
            // This method is necessary because EditText is asking this method for the language
            // to check the spell in. If we don't override this, the spell checker will run in
            // whatever language the keyboard is currently set on the test device, ignoring any
            // settings we do inside the tests.
            return mCurrentLocale;
        }

    }

    public InputTestsBase() {
        super(LatinIMEForTests.class);
    }

    protected boolean setBooleanPreference(final String key, final boolean value,
            final boolean defaultValue) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mLatinIME);
        final boolean previousSetting = prefs.getBoolean(key, defaultValue);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.apply();
        return previousSetting;
    }

    protected boolean getBooleanPreference(final String key, final boolean defaultValue) {
        return PreferenceManager.getDefaultSharedPreferences(mLatinIME)
                .getBoolean(key, defaultValue);
    }

    protected String setStringPreference(final String key, final String value,
            final String defaultValue) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mLatinIME);
        final String previousSetting = prefs.getString(key, defaultValue);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();
        return previousSetting;
    }

    protected void setDebugMode(final boolean value) {
        setBooleanPreference(DebugSettings.PREF_DEBUG_MODE, value, false);
        setBooleanPreference(Settings.PREF_KEY_IS_INTERNAL, value, false);
    }

    protected EditorInfo enrichEditorInfo(final EditorInfo ei) {
        // Some tests that inherit from us need to add some data in the EditorInfo (see
        // AppWorkaroundsTests#enrichEditorInfo() for a concrete example of this). Since we
        // control the EditorInfo, we supply a hook here for children to override.
        return ei;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mEditText = new MyEditText(getContext());
        final int inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        mEditText.setInputType(inputType);
        mEditText.setEnabled(true);
        mLastCursorPos = 0;
        if (null == Looper.myLooper()) {
            Looper.prepare();
        }
        setupService();
        mLatinIME = getService();
        setDebugMode(true);
        mPreviousBigramPredictionSettings = setBooleanPreference(Settings.PREF_BIGRAM_PREDICTIONS,
                true, true /* defaultValue */);
        mPreviousAutoCorrectSetting = setBooleanPreference(Settings.PREF_AUTO_CORRECTION,
                DEFAULT_AUTO_CORRECTION, DEFAULT_AUTO_CORRECTION);
        mLatinIME.onCreate();
        EditorInfo ei = new EditorInfo();
        final InputConnection ic = mEditText.onCreateInputConnection(ei);
        final LayoutInflater inflater =
                (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ViewGroup vg = new FrameLayout(getContext());
        mInputView = inflater.inflate(R.layout.input_view, vg);
        ei = enrichEditorInfo(ei);
        mLatinIME.onCreateInputMethodInterface().startInput(ic, ei);
        mLatinIME.setInputView(mInputView);
        mLatinIME.onBindInput();
        mLatinIME.onCreateInputView();
        mLatinIME.onStartInputView(ei, false);
        mInputConnection = ic;
        changeLanguage("en_US");
        // Run messages to avoid the messages enqueued by startInputView() and its friends
        // to run on a later call and ruin things. We need to wait first because some of them
        // can be posted with a delay (notably,  MSG_RESUME_SUGGESTIONS)
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();
    }

    @Override
    protected void tearDown() throws Exception {
        mLatinIME.onFinishInputView(true);
        mLatinIME.onFinishInput();
        runMessages();
        mLatinIME.mHandler.removeAllMessages();
        setBooleanPreference(Settings.PREF_BIGRAM_PREDICTIONS, mPreviousBigramPredictionSettings,
                true /* defaultValue */);
        setBooleanPreference(Settings.PREF_AUTO_CORRECTION, mPreviousAutoCorrectSetting,
                DEFAULT_AUTO_CORRECTION);
        setDebugMode(false);
        mLatinIME.recycle();
        super.tearDown();
        mLatinIME = null;
    }

    // We need to run the messages added to the handler from LatinIME. The only way to do
    // that is to call Looper#loop() on the right looper, so we're going to get the looper
    // object and call #loop() here. The messages in the handler actually run on the UI
    // thread of the keyboard by design of the handler, so we want to call it synchronously
    // on the same thread that the tests are running on to mimic the actual environment as
    // closely as possible.
    // Now, Looper#loop() never exits in normal operation unless the Looper#quit() method
    // is called, which has a lot of bad side effects. We can however just throw an exception
    // in the runnable which will unwind the stack and allow us to exit.
    final class InterruptRunMessagesException extends RuntimeException {
        // Empty class
    }
    protected void runMessages() {
        mLatinIME.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    throw new InterruptRunMessagesException();
                }
            });
        try {
            Looper.loop();
        } catch (InterruptRunMessagesException e) {
            // Resume normal operation
        }
    }

    // type(int) and type(String): helper methods to send a code point resp. a string to LatinIME.
    protected void typeInternal(final int codePoint, final boolean isKeyRepeat) {
        // onPressKey and onReleaseKey are explicitly deactivated here, but they do happen in the
        // code (although multitouch/slide input and other factors make the sequencing complicated).
        // They are supposed to be entirely deconnected from the input logic from LatinIME point of
        // view and only delegates to the parts of the code that care. So we don't include them here
        // to keep these tests as pinpoint as possible and avoid bringing it too many dependencies,
        // but keep them in mind if something breaks. Commenting them out as is should work.
        //mLatinIME.onPressKey(codePoint, 0 /* repeatCount */, true /* isSinglePointer */);
        final Key key = mKeyboard.getKey(codePoint);
        final Event event;
        if (key == null) {
            event = Event.createSoftwareKeypressEvent(codePoint, Event.NOT_A_KEY_CODE,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, isKeyRepeat);
        } else {
            final int x = key.getX() + key.getWidth() / 2;
            final int y = key.getY() + key.getHeight() / 2;
            event = LatinIME.createSoftwareKeypressEvent(codePoint, x, y, isKeyRepeat);
        }
        mLatinIME.onEvent(event);
        // Also see the comment at the top of this function about onReleaseKey
        //mLatinIME.onReleaseKey(codePoint, false /* withSliding */);
    }

    protected void type(final int codePoint) {
        typeInternal(codePoint, false /* isKeyRepeat */);
    }

    protected void repeatKey(final int codePoint) {
        typeInternal(codePoint, true /* isKeyRepeat */);
    }

    protected void type(final String stringToType) {
        for (int i = 0; i < stringToType.length(); i = stringToType.offsetByCodePoints(i, 1)) {
            type(stringToType.codePointAt(i));
        }
    }

    protected Point getXY(final int codePoint) {
        final Key key = mKeyboard.getKey(codePoint);
        if (key == null) {
            throw new RuntimeException("Code point not on the keyboard");
        }
        return new Point(key.getX() + key.getWidth() / 2, key.getY() + key.getHeight() / 2);
    }

    protected void gesture(final String stringToGesture) {
        if (StringUtils.codePointCount(stringToGesture) < 2) {
            throw new RuntimeException("Can't gesture strings less than 2 chars long");
        }

        mLatinIME.onStartBatchInput();
        final int startCodePoint = stringToGesture.codePointAt(0);
        Point oldPoint = getXY(startCodePoint);
        int timestamp = 0; // In milliseconds since the start of the gesture
        final InputPointers pointers = new InputPointers(Constants.DEFAULT_GESTURE_POINTS_CAPACITY);
        pointers.addPointer(oldPoint.x, oldPoint.y, 0 /* pointerId */, timestamp);

        for (int i = Character.charCount(startCodePoint); i < stringToGesture.length();
                i = stringToGesture.offsetByCodePoints(i, 1)) {
            final Point newPoint = getXY(stringToGesture.codePointAt(i));
            // Arbitrarily 0.5s between letters and 0.1 between events. Refine this later if needed.
            final int STEPS = 5;
            for (int j = 0; j < STEPS; ++j) {
                timestamp += 100;
                pointers.addPointer(oldPoint.x + ((newPoint.x - oldPoint.x) * j) / STEPS,
                        oldPoint.y + ((newPoint.y - oldPoint.y) * j) / STEPS,
                        0 /* pointerId */, timestamp);
            }
            oldPoint.x = newPoint.x;
            oldPoint.y = newPoint.y;
            mLatinIME.onUpdateBatchInput(pointers);
        }
        mLatinIME.onEndBatchInput(pointers);
        sleep(DELAY_TO_WAIT_FOR_GESTURE_MILLIS);
        runMessages();
    }

    protected void waitForDictionariesToBeLoaded() {
        try {
            mLatinIME.waitForLoadingDictionaries(
                    TIMEOUT_TO_WAIT_FOR_LOADING_MAIN_DICTIONARY_IN_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted during waiting for loading main dictionary.", e);
        }
    }

    protected void changeLanguage(final String locale) {
        changeLanguage(locale, null);
    }

    protected void changeLanguage(final String locale, final String combiningSpec) {
        changeLanguageWithoutWait(locale, combiningSpec);
        waitForDictionariesToBeLoaded();
    }

    protected void changeLanguageWithoutWait(final String locale, final String combiningSpec) {
        mEditText.mCurrentLocale = LocaleUtils.constructLocaleFromString(locale);
        // TODO: this is forcing a QWERTY keyboard for all locales, which is wrong.
        // It's still better than using whatever keyboard is the current one, but we
        // should actually use the default keyboard for this locale.
        // TODO: Use {@link InputMethodSubtype.InputMethodSubtypeBuilder} directly or indirectly so
        // that {@link InputMethodSubtype#isAsciiCapable} can return the correct value.
        final String EXTRA_VALUE_FOR_TEST =
                "KeyboardLayoutSet=" + SubtypeLocaleUtils.QWERTY
                + "," + Constants.Subtype.ExtraValue.ASCII_CAPABLE
                + "," + Constants.Subtype.ExtraValue.ENABLED_WHEN_DEFAULT_IS_NOT_ASCII_CAPABLE
                + "," + Constants.Subtype.ExtraValue.EMOJI_CAPABLE
                + null == combiningSpec ? "" : ("," + combiningSpec);
        final InputMethodSubtype subtype = InputMethodSubtypeCompatUtils.newInputMethodSubtype(
                R.string.subtype_no_language_qwerty,
                R.drawable.ic_ime_switcher_dark,
                locale,
                Constants.Subtype.KEYBOARD_MODE,
                EXTRA_VALUE_FOR_TEST,
                false /* isAuxiliary */,
                false /* overridesImplicitlyEnabledSubtype */,
                0 /* id */);
        RichInputMethodManager.forceSubtype(subtype);
        mLatinIME.onCurrentInputMethodSubtypeChanged(subtype);
        runMessages();
        mKeyboard = mLatinIME.mKeyboardSwitcher.getKeyboard();
        mLatinIME.clearPersonalizedDictionariesForTest();
    }

    protected void changeKeyboardLocaleAndDictLocale(final String keyboardLocale,
            final String dictLocale) {
        changeLanguage(keyboardLocale);
        if (!keyboardLocale.equals(dictLocale)) {
            mLatinIME.replaceDictionariesForTest(LocaleUtils.constructLocaleFromString(dictLocale));
        }
        waitForDictionariesToBeLoaded();
    }

    protected void pickSuggestionManually(final String suggestion) {
        mLatinIME.pickSuggestionManually(new SuggestedWordInfo(suggestion,
                "" /* prevWordsContext */, 1 /* score */,
                SuggestedWordInfo.KIND_CORRECTION, DICTIONARY_TEST,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
    }

    // Helper to avoid writing the try{}catch block each time
    protected static void sleep(final int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {}
    }

    // Some helper methods to manage the mock cursor position
    // DO NOT CALL LatinIME#onUpdateSelection IF YOU WANT TO USE THOSE
    int mLastCursorPos = 0;
    /**
     * Move the cached cursor position to the passed position and send onUpdateSelection to LatinIME
     */
    protected int sendUpdateForCursorMoveTo(final int position) {
        mInputConnection.setSelection(position, position);
        mLatinIME.onUpdateSelection(mLastCursorPos, mLastCursorPos, position, position, -1, -1);
        mLastCursorPos = position;
        return position;
    }

    /**
     * Move the cached cursor position by the passed amount and send onUpdateSelection to LatinIME
     */
    protected int sendUpdateForCursorMoveBy(final int offset) {
        final int lastPos = mEditText.getText().length();
        final int requestedPosition = mLastCursorPos + offset;
        if (requestedPosition < 0) {
            return sendUpdateForCursorMoveTo(0);
        } else if (requestedPosition > lastPos) {
            return sendUpdateForCursorMoveTo(lastPos);
        } else {
            return sendUpdateForCursorMoveTo(requestedPosition);
        }
    }

    /**
     * Move the cached cursor position to the end of the line and send onUpdateSelection to LatinIME
     */
    protected int sendUpdateForCursorMoveToEndOfLine() {
        final int lastPos = mEditText.getText().length();
        return sendUpdateForCursorMoveTo(lastPos);
    }
}
