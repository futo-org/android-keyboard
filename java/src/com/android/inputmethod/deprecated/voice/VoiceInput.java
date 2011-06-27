/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.inputmethod.deprecated.voice;

import com.android.inputmethod.latin.EditingUtils;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.StaticInnerHandlerWrapper;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.Parcelable;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputConnection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Speech recognition input, including both user interface and a background
 * process to stream audio to the network recognizer. This class supplies a
 * View (getView()), which it updates as recognition occurs. The user of this
 * class is responsible for making the view visible to the user, as well as
 * handling various events returned through UiListener.
 */
public class VoiceInput implements OnClickListener {
    private static final String TAG = "VoiceInput";
    private static final String EXTRA_RECOGNITION_CONTEXT =
            "android.speech.extras.RECOGNITION_CONTEXT";
    private static final String EXTRA_CALLING_PACKAGE = "calling_package";
    private static final String EXTRA_ALTERNATES = "android.speech.extra.ALTERNATES";
    private static final int MAX_ALT_LIST_LENGTH = 6;
    private static boolean DBG = LatinImeLogger.sDBG;

    private static final String DEFAULT_RECOMMENDED_PACKAGES =
            "com.android.mms " +
            "com.google.android.gm " +
            "com.google.android.talk " +
            "com.google.android.apps.googlevoice " +
            "com.android.email " +
            "com.android.browser ";

    // WARNING! Before enabling this, fix the problem with calling getExtractedText() in
    // landscape view. It causes Extracted text updates to be rejected due to a token mismatch
    public static boolean ENABLE_WORD_CORRECTIONS = true;

    // Dummy word suggestion which means "delete current word"
    public static final String DELETE_SYMBOL = " \u00D7 ";  // times symbol

    private Whitelist mRecommendedList;
    private Whitelist mBlacklist;

    private VoiceInputLogger mLogger;

    // Names of a few extras defined in VoiceSearch's RecognitionController
    // Note, the version of voicesearch that shipped in Froyo returns the raw
    // RecognitionClientAlternates protocol buffer under the key "alternates",
    // so a VS market update must be installed on Froyo devices in order to see
    // alternatives.
    private static final String ALTERNATES_BUNDLE = "alternates_bundle";

    //  This is copied from the VoiceSearch app.
    @SuppressWarnings("unused")
    private static final class AlternatesBundleKeys {
        public static final String ALTERNATES = "alternates";
        public static final String CONFIDENCE = "confidence";
        public static final String LENGTH = "length";
        public static final String MAX_SPAN_LENGTH = "max_span_length";
        public static final String SPANS = "spans";
        public static final String SPAN_KEY_DELIMITER = ":";
        public static final String START = "start";
        public static final String TEXT = "text";
    }

    // Names of a few intent extras defined in VoiceSearch's RecognitionService.
    // These let us tweak the endpointer parameters.
    private static final String EXTRA_SPEECH_MINIMUM_LENGTH_MILLIS =
            "android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS";
    private static final String EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS =
            "android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS";
    private static final String EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS =
            "android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS";

    // The usual endpointer default value for input complete silence length is 0.5 seconds,
    // but that's used for things like voice search. For dictation-like voice input like this,
    // we go with a more liberal value of 1 second. This value will only be used if a value
    // is not provided from Gservices.
    private static final String INPUT_COMPLETE_SILENCE_LENGTH_DEFAULT_VALUE_MILLIS = "1000";

    // Used to record part of that state for logging purposes.
    public static final int DEFAULT = 0;
    public static final int LISTENING = 1;
    public static final int WORKING = 2;
    public static final int ERROR = 3;

    private int mAfterVoiceInputDeleteCount = 0;
    private int mAfterVoiceInputInsertCount = 0;
    private int mAfterVoiceInputInsertPunctuationCount = 0;
    private int mAfterVoiceInputCursorPos = 0;
    private int mAfterVoiceInputSelectionSpan = 0;

    private int mState = DEFAULT;

    private final static int MSG_RESET = 1;

    private final UIHandler mHandler = new UIHandler(this);

    private static class UIHandler extends StaticInnerHandlerWrapper<VoiceInput> {
        public UIHandler(VoiceInput outerInstance) {
            super(outerInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_RESET) {
                final VoiceInput voiceInput = getOuterInstance();
                voiceInput.mState = DEFAULT;
                voiceInput.mRecognitionView.finish();
                voiceInput.mUiListener.onCancelVoice();
            }
        }
    };

    /**
     * Events relating to the recognition UI. You must implement these.
     */
    public interface UiListener {

        /**
         * @param recognitionResults a set of transcripts for what the user
         *   spoke, sorted by likelihood.
         */
        public void onVoiceResults(
            List<String> recognitionResults,
            Map<String, List<CharSequence>> alternatives);

        /**
         * Called when the user cancels speech recognition.
         */
        public void onCancelVoice();
    }

    private SpeechRecognizer mSpeechRecognizer;
    private RecognitionListener mRecognitionListener;
    private RecognitionView mRecognitionView;
    private UiListener mUiListener;
    private Context mContext;

    /**
     * @param context the service or activity in which we're running.
     * @param uiHandler object to receive events from VoiceInput.
     */
    public VoiceInput(Context context, UiListener uiHandler) {
        mLogger = VoiceInputLogger.getLogger(context);
        mRecognitionListener = new ImeRecognitionListener();
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        mSpeechRecognizer.setRecognitionListener(mRecognitionListener);
        mUiListener = uiHandler;
        mContext = context;
        newView();

        String recommendedPackages = SettingsUtil.getSettingsString(
                context.getContentResolver(),
                SettingsUtil.LATIN_IME_VOICE_INPUT_RECOMMENDED_PACKAGES,
                DEFAULT_RECOMMENDED_PACKAGES);

        mRecommendedList = new Whitelist();
        for (String recommendedPackage : recommendedPackages.split("\\s+")) {
            mRecommendedList.addApp(recommendedPackage);
        }

        mBlacklist = new Whitelist();
        mBlacklist.addApp("com.google.android.setupwizard");
    }

    public void setCursorPos(int pos) {
        mAfterVoiceInputCursorPos = pos;
    }

    public int getCursorPos() {
        return mAfterVoiceInputCursorPos;
    }

    public void setSelectionSpan(int span) {
        mAfterVoiceInputSelectionSpan = span;
    }

    public int getSelectionSpan() {
        return mAfterVoiceInputSelectionSpan;
    }

    public void incrementTextModificationDeleteCount(int count){
        mAfterVoiceInputDeleteCount += count;
        // Send up intents for other text modification types
        if (mAfterVoiceInputInsertCount > 0) {
            logTextModifiedByTypingInsertion(mAfterVoiceInputInsertCount);
            mAfterVoiceInputInsertCount = 0;
        }
        if (mAfterVoiceInputInsertPunctuationCount > 0) {
            logTextModifiedByTypingInsertionPunctuation(mAfterVoiceInputInsertPunctuationCount);
            mAfterVoiceInputInsertPunctuationCount = 0;
        }

    }

    public void incrementTextModificationInsertCount(int count){
        mAfterVoiceInputInsertCount += count;
        if (mAfterVoiceInputSelectionSpan > 0) {
            // If text was highlighted before inserting the char, count this as
            // a delete.
            mAfterVoiceInputDeleteCount += mAfterVoiceInputSelectionSpan;
        }
        // Send up intents for other text modification types
        if (mAfterVoiceInputDeleteCount > 0) {
            logTextModifiedByTypingDeletion(mAfterVoiceInputDeleteCount);
            mAfterVoiceInputDeleteCount = 0;
        }
        if (mAfterVoiceInputInsertPunctuationCount > 0) {
            logTextModifiedByTypingInsertionPunctuation(mAfterVoiceInputInsertPunctuationCount);
            mAfterVoiceInputInsertPunctuationCount = 0;
        }
    }

    public void incrementTextModificationInsertPunctuationCount(int count){
        mAfterVoiceInputInsertPunctuationCount += count;
        if (mAfterVoiceInputSelectionSpan > 0) {
            // If text was highlighted before inserting the char, count this as
            // a delete.
            mAfterVoiceInputDeleteCount += mAfterVoiceInputSelectionSpan;
        }
        // Send up intents for aggregated non-punctuation insertions
        if (mAfterVoiceInputDeleteCount > 0) {
            logTextModifiedByTypingDeletion(mAfterVoiceInputDeleteCount);
            mAfterVoiceInputDeleteCount = 0;
        }
        if (mAfterVoiceInputInsertCount > 0) {
            logTextModifiedByTypingInsertion(mAfterVoiceInputInsertCount);
            mAfterVoiceInputInsertCount = 0;
        }
    }

    public void flushAllTextModificationCounters() {
        if (mAfterVoiceInputInsertCount > 0) {
            logTextModifiedByTypingInsertion(mAfterVoiceInputInsertCount);
            mAfterVoiceInputInsertCount = 0;
        }
        if (mAfterVoiceInputDeleteCount > 0) {
            logTextModifiedByTypingDeletion(mAfterVoiceInputDeleteCount);
            mAfterVoiceInputDeleteCount = 0;
        }
        if (mAfterVoiceInputInsertPunctuationCount > 0) {
            logTextModifiedByTypingInsertionPunctuation(mAfterVoiceInputInsertPunctuationCount);
            mAfterVoiceInputInsertPunctuationCount = 0;
        }
    }

    /**
     * The configuration of the IME changed and may have caused the views to be layed out
     * again. Restore the state of the recognition view.
     */
    public void onConfigurationChanged(Configuration configuration) {
        mRecognitionView.restoreState();
        mRecognitionView.getView().dispatchConfigurationChanged(configuration);
    }

    /**
     * @return true if field is blacklisted for voice
     */
    public boolean isBlacklistedField(FieldContext context) {
        return mBlacklist.matches(context);
    }

    /**
     * Used to decide whether to show voice input hints for this field, etc.
     *
     * @return true if field is recommended for voice
     */
    public boolean isRecommendedField(FieldContext context) {
        return mRecommendedList.matches(context);
    }

    /**
     * Start listening for speech from the user. This will grab the microphone
     * and start updating the view provided by getView(). It is the caller's
     * responsibility to ensure that the view is visible to the user at this stage.
     *
     * @param context the same FieldContext supplied to voiceIsEnabled()
     * @param swipe whether this voice input was started by swipe, for logging purposes
     */
    public void startListening(FieldContext context, boolean swipe) {
        if (DBG) {
            Log.d(TAG, "startListening: " + context);
        }

        if (mState != DEFAULT) {
            Log.w(TAG, "startListening in the wrong status " + mState);
        }

        // If everything works ok, the voice input should be already in the correct state. As this
        // class can be called by third-party, we call reset just to be on the safe side.
        reset();

        Locale locale = Locale.getDefault();
        String localeString = locale.getLanguage() + "-" + locale.getCountry();

        mLogger.start(localeString, swipe);

        mState = LISTENING;

        mRecognitionView.showInitializing();
        startListeningAfterInitialization(context);
    }

    /**
     * Called only when the recognition manager's initialization completed
     *
     * @param context context with which {@link #startListening(FieldContext, boolean)} was executed
     */
    private void startListeningAfterInitialization(FieldContext context) {
        Intent intent = makeIntent();
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "");
        intent.putExtra(EXTRA_RECOGNITION_CONTEXT, context.getBundle());
        intent.putExtra(EXTRA_CALLING_PACKAGE, "VoiceIME");
        intent.putExtra(EXTRA_ALTERNATES, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,
                SettingsUtil.getSettingsInt(
                        mContext.getContentResolver(),
                        SettingsUtil.LATIN_IME_MAX_VOICE_RESULTS,
                        1));
        // Get endpointer params from Gservices.
        // TODO: Consider caching these values for improved performance on slower devices.
        final ContentResolver cr = mContext.getContentResolver();
        putEndpointerExtra(
                cr,
                intent,
                SettingsUtil.LATIN_IME_SPEECH_MINIMUM_LENGTH_MILLIS,
                EXTRA_SPEECH_MINIMUM_LENGTH_MILLIS,
                null  /* rely on endpointer default */);
        putEndpointerExtra(
                cr,
                intent,
                SettingsUtil.LATIN_IME_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                INPUT_COMPLETE_SILENCE_LENGTH_DEFAULT_VALUE_MILLIS
                /* our default value is different from the endpointer's */);
        putEndpointerExtra(
                cr,
                intent,
                SettingsUtil.
                        LATIN_IME_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                null  /* rely on endpointer default */);

        mSpeechRecognizer.startListening(intent);
    }

    /**
     * Gets the value of the provided Gservices key, attempts to parse it into a long,
     * and if successful, puts the long value as an extra in the provided intent.
     */
    private void putEndpointerExtra(ContentResolver cr, Intent i,
            String gservicesKey, String intentExtraKey, String defaultValue) {
        long l = -1;
        String s = SettingsUtil.getSettingsString(cr, gservicesKey, defaultValue);
        if (s != null) {
            try {
                l = Long.valueOf(s);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not parse value for " + gservicesKey + ": " + s);
            }
        }

        if (l != -1) i.putExtra(intentExtraKey, l);
    }

    public void destroy() {
        mSpeechRecognizer.destroy();
    }

    /**
     * Creates a new instance of the view that is returned by {@link #getView()}
     * Clients should use this when a previously returned view is stuck in a
     * layout that is being thrown away and a new one is need to show to the
     * user.
     */
    public void newView() {
        mRecognitionView = new RecognitionView(mContext, this);
    }

    /**
     * @return a view that shows the recognition flow--e.g., "Speak now" and
     * "working" dialogs.
     */
    public View getView() {
        return mRecognitionView.getView();
    }

    /**
     * Handle the cancel button.
     */
    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.button:
                cancel();
                break;
        }
    }

    public void logTextModifiedByTypingInsertion(int length) {
        mLogger.textModifiedByTypingInsertion(length);
    }

    public void logTextModifiedByTypingInsertionPunctuation(int length) {
        mLogger.textModifiedByTypingInsertionPunctuation(length);
    }

    public void logTextModifiedByTypingDeletion(int length) {
        mLogger.textModifiedByTypingDeletion(length);
    }

    public void logTextModifiedByChooseSuggestion(String suggestion, int index,
                                                  String wordSeparators, InputConnection ic) {
        String wordToBeReplaced = EditingUtils.getWordAtCursor(ic, wordSeparators);
        // If we enable phrase-based alternatives, only send up the first word
        // in suggestion and wordToBeReplaced.
        mLogger.textModifiedByChooseSuggestion(suggestion.length(), wordToBeReplaced.length(),
                                               index, wordToBeReplaced, suggestion);
    }

    public void logKeyboardWarningDialogShown() {
        mLogger.keyboardWarningDialogShown();
    }

    public void logKeyboardWarningDialogDismissed() {
        mLogger.keyboardWarningDialogDismissed();
    }

    public void logKeyboardWarningDialogOk() {
        mLogger.keyboardWarningDialogOk();
    }

    public void logKeyboardWarningDialogCancel() {
        mLogger.keyboardWarningDialogCancel();
    }

    public void logSwipeHintDisplayed() {
        mLogger.swipeHintDisplayed();
    }

    public void logPunctuationHintDisplayed() {
        mLogger.punctuationHintDisplayed();
    }

    public void logVoiceInputDelivered(int length) {
        mLogger.voiceInputDelivered(length);
    }

    public void logInputEnded() {
        mLogger.inputEnded();
    }

    public void flushLogs() {
        mLogger.flush();
    }

    private static Intent makeIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        // On Cupcake, use VoiceIMEHelper since VoiceSearch doesn't support.
        // On Donut, always use VoiceSearch, since VoiceIMEHelper and
        // VoiceSearch may conflict.
        if (Build.VERSION.RELEASE.equals("1.5")) {
            intent = intent.setClassName(
              "com.google.android.voiceservice",
              "com.google.android.voiceservice.IMERecognitionService");
        } else {
            intent = intent.setClassName(
              "com.google.android.voicesearch",
              "com.google.android.voicesearch.RecognitionService");
        }

        return intent;
    }

    /**
     * Reset the current voice recognition.
     */
    public void reset() {
        if (mState != DEFAULT) {
            mState = DEFAULT;

            // Remove all pending tasks (e.g., timers to cancel voice input)
            mHandler.removeMessages(MSG_RESET);

            mSpeechRecognizer.cancel();
            mRecognitionView.finish();
        }
    }

    /**
     * Cancel in-progress speech recognition.
     */
    public void cancel() {
        switch (mState) {
        case LISTENING:
            mLogger.cancelDuringListening();
            break;
        case WORKING:
            mLogger.cancelDuringWorking();
            break;
        case ERROR:
            mLogger.cancelDuringError();
            break;
        }

        reset();
        mUiListener.onCancelVoice();
    }

    private int getErrorStringId(int errorType, boolean endpointed) {
        switch (errorType) {
            // We use CLIENT_ERROR to signify that voice search is not available on the device.
            case SpeechRecognizer.ERROR_CLIENT:
                return R.string.voice_not_installed;
            case SpeechRecognizer.ERROR_NETWORK:
                return R.string.voice_network_error;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return endpointed ?
                        R.string.voice_network_error : R.string.voice_too_much_speech;
            case SpeechRecognizer.ERROR_AUDIO:
                return R.string.voice_audio_error;
            case SpeechRecognizer.ERROR_SERVER:
                return R.string.voice_server_error;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return R.string.voice_speech_timeout;
            case SpeechRecognizer.ERROR_NO_MATCH:
                return R.string.voice_no_match;
            default: return R.string.voice_error;
        }
    }

    private void onError(int errorType, boolean endpointed) {
        Log.i(TAG, "error " + errorType);
        mLogger.error(errorType);
        onError(mContext.getString(getErrorStringId(errorType, endpointed)));
    }

    private void onError(String error) {
        mState = ERROR;
        mRecognitionView.showError(error);
        // Wait a couple seconds and then automatically dismiss message.
        mHandler.sendMessageDelayed(Message.obtain(mHandler, MSG_RESET), 2000);
    }

    private class ImeRecognitionListener implements RecognitionListener {
        // Waveform data
        final ByteArrayOutputStream mWaveBuffer = new ByteArrayOutputStream();
        int mSpeechStart;
        private boolean mEndpointed = false;

        @Override
        public void onReadyForSpeech(Bundle noiseParams) {
            mRecognitionView.showListening();
        }

        @Override
        public void onBeginningOfSpeech() {
            mEndpointed = false;
            mSpeechStart = mWaveBuffer.size();
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            mRecognitionView.updateVoiceMeter(rmsdB);
        }

        @Override
        public void onBufferReceived(byte[] buf) {
            try {
                mWaveBuffer.write(buf);
            } catch (IOException e) {
                // ignore.
            }
        }

        @Override
        public void onEndOfSpeech() {
            mEndpointed = true;
            mState = WORKING;
            mRecognitionView.showWorking(mWaveBuffer, mSpeechStart, mWaveBuffer.size());
        }

        @Override
        public void onError(int errorType) {
            mState = ERROR;
            VoiceInput.this.onError(errorType, mEndpointed);
        }

        @Override
        public void onResults(Bundle resultsBundle) {
            List<String> results = resultsBundle
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            // VS Market update is needed for IME froyo clients to access the alternatesBundle
            // TODO: verify this.
            Bundle alternatesBundle = resultsBundle.getBundle(ALTERNATES_BUNDLE);
            mState = DEFAULT;

            final Map<String, List<CharSequence>> alternatives =
                new HashMap<String, List<CharSequence>>();

            if (ENABLE_WORD_CORRECTIONS && alternatesBundle != null && results.size() > 0) {
                // Use the top recognition result to map each alternative's start:length to a word.
                String[] words = results.get(0).split(" ");
                Bundle spansBundle = alternatesBundle.getBundle(AlternatesBundleKeys.SPANS);
                for (String key : spansBundle.keySet()) {
                    // Get the word for which these alternates correspond to.
                    Bundle spanBundle = spansBundle.getBundle(key);
                    int start = spanBundle.getInt(AlternatesBundleKeys.START);
                    int length = spanBundle.getInt(AlternatesBundleKeys.LENGTH);
                    // Only keep single-word based alternatives.
                    if (length == 1 && start < words.length) {
                        // Get the alternatives associated with the span.
                        // If a word appears twice in a recognition result,
                        // concatenate the alternatives for the word.
                        List<CharSequence> altList = alternatives.get(words[start]);
                        if (altList == null) {
                            altList = new ArrayList<CharSequence>();
                            alternatives.put(words[start], altList);
                        }
                        Parcelable[] alternatesArr = spanBundle
                            .getParcelableArray(AlternatesBundleKeys.ALTERNATES);
                        for (int j = 0; j < alternatesArr.length &&
                                 altList.size() < MAX_ALT_LIST_LENGTH; j++) {
                            Bundle alternateBundle = (Bundle) alternatesArr[j];
                            String alternate = alternateBundle.getString(AlternatesBundleKeys.TEXT);
                            // Don't allow duplicates in the alternates list.
                            if (!altList.contains(alternate)) {
                                altList.add(alternate);
                            }
                        }
                    }
                }
            }

            if (results.size() > 5) {
                results = results.subList(0, 5);
            }
            mUiListener.onVoiceResults(results, alternatives);
            mRecognitionView.finish();
        }

        @Override
        public void onPartialResults(final Bundle partialResults) {
            // currently - do nothing
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // do nothing - reserved for events that might be added in the future
        }
    }
}
