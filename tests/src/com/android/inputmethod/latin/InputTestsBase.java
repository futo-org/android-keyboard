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
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.settings.DebugSettings;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.LocaleUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class InputTestsBase extends ServiceTestCase<LatinIMEForTests> {
    private static final String TAG = InputTestsBase.class.getSimpleName();

    // Default value for auto-correction threshold. This is the string representation of the
    // index in the resources array of auto-correction threshold settings.
    private static final String DEFAULT_AUTO_CORRECTION_THRESHOLD = "1";

    // The message that sets the underline is posted with a 500 ms delay
    protected static final int DELAY_TO_WAIT_FOR_UNDERLINE = 500;
    // The message that sets predictions is posted with a 200 ms delay
    protected static final int DELAY_TO_WAIT_FOR_PREDICTIONS = 200;
    private final int TIMEOUT_TO_WAIT_FOR_LOADING_MAIN_DICTIONARY_IN_SECONDS = 60;

    protected LatinIME mLatinIME;
    protected Keyboard mKeyboard;
    protected MyEditText mEditText;
    protected View mInputView;
    protected InputConnection mInputConnection;
    private boolean mPreviousBigramPredictionSettings;
    private String mPreviousAutoCorrectSetting;

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
        public String[] getSuggestions() {
            return ((SuggestionSpan)mSpan).getSuggestions();
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

    // TODO: Isn't there a way to make this generic somehow? We can take a <T> and return a <T>
    // but we'd have to dispatch types on editor.put...() functions
    protected boolean setBooleanPreference(final String key, final boolean value,
            final boolean defaultValue) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mLatinIME);
        final boolean previousSetting = prefs.getBoolean(key, defaultValue);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.apply();
        return previousSetting;
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
        setupService();
        mLatinIME = getService();
        setDebugMode(true);
        mPreviousBigramPredictionSettings = setBooleanPreference(Settings.PREF_BIGRAM_PREDICTIONS,
                true, true /* defaultValue */);
        mPreviousAutoCorrectSetting = setStringPreference(Settings.PREF_AUTO_CORRECTION_THRESHOLD,
                DEFAULT_AUTO_CORRECTION_THRESHOLD, DEFAULT_AUTO_CORRECTION_THRESHOLD);
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
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS);
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
        setStringPreference(Settings.PREF_AUTO_CORRECTION_THRESHOLD, mPreviousAutoCorrectSetting,
                DEFAULT_AUTO_CORRECTION_THRESHOLD);
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
    private final class InterruptRunMessagesException extends RuntimeException {
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
        if (key == null) {
            mLatinIME.onCodeInput(codePoint, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                    isKeyRepeat);
        } else {
            final int x = key.getX() + key.getWidth() / 2;
            final int y = key.getY() + key.getHeight() / 2;
            mLatinIME.onCodeInput(codePoint, x, y, isKeyRepeat);
        }
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
        SubtypeSwitcher.getInstance().forceSubtype(subtype);
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
        mLatinIME.pickSuggestionManually(new SuggestedWordInfo(suggestion, 1,
                SuggestedWordInfo.KIND_CORRECTION, null /* sourceDict */,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */));
    }

    // Helper to avoid writing the try{}catch block each time
    protected static void sleep(final int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {}
    }
}
