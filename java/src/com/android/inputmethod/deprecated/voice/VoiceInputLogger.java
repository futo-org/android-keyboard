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

package com.android.inputmethod.deprecated.voice;

import com.android.common.speech.LoggingEvents;
import com.android.inputmethod.deprecated.compat.VoiceInputLoggerCompatUtils;

import android.content.Context;
import android.content.Intent;

/**
 * Provides the logging facility for voice input events. This fires broadcasts back to
 * the voice search app which then logs on our behalf.
 *
 * Note that debug console logging does not occur in this class. If you want to
 * see console output of these logging events, there is a boolean switch to turn
 * on on the VoiceSearch side.
 */
public class VoiceInputLogger {
    @SuppressWarnings("unused")
    private static final String TAG = VoiceInputLogger.class.getSimpleName();

    private static VoiceInputLogger sVoiceInputLogger;

    private final Context mContext;

    // The base intent used to form all broadcast intents to the logger
    // in VoiceSearch.
    private final Intent mBaseIntent;

    // This flag is used to indicate when there are voice events that
    // need to be flushed.
    private boolean mHasLoggingInfo = false;

    /**
     * Returns the singleton of the logger.
     *
     * @param contextHint a hint context used when creating the logger instance.
     * Ignored if the singleton instance already exists.
     */
    public static synchronized VoiceInputLogger getLogger(Context contextHint) {
        if (sVoiceInputLogger == null) {
            sVoiceInputLogger = new VoiceInputLogger(contextHint);
        }
        return sVoiceInputLogger;
    }

    public VoiceInputLogger(Context context) {
        mContext = context;
        
        mBaseIntent = new Intent(LoggingEvents.ACTION_LOG_EVENT);
        mBaseIntent.putExtra(LoggingEvents.EXTRA_APP_NAME, LoggingEvents.VoiceIme.APP_NAME);
    }
    
    private Intent newLoggingBroadcast(int event) {
        Intent i = new Intent(mBaseIntent);
        i.putExtra(LoggingEvents.EXTRA_EVENT, event);
        return i;
    }

    public void flush() {
        if (hasLoggingInfo()) {
            Intent i = new Intent(mBaseIntent);
            i.putExtra(LoggingEvents.EXTRA_FLUSH, true);
            mContext.sendBroadcast(i);
            setHasLoggingInfo(false);
        }
    }
    
    public void keyboardWarningDialogShown() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.KEYBOARD_WARNING_DIALOG_SHOWN));
    }
    
    public void keyboardWarningDialogDismissed() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.KEYBOARD_WARNING_DIALOG_DISMISSED));
    }

    public void keyboardWarningDialogOk() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.KEYBOARD_WARNING_DIALOG_OK));
    }

    public void keyboardWarningDialogCancel() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.KEYBOARD_WARNING_DIALOG_CANCEL));
    }

    public void settingsWarningDialogShown() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.SETTINGS_WARNING_DIALOG_SHOWN));
    }
    
    public void settingsWarningDialogDismissed() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.SETTINGS_WARNING_DIALOG_DISMISSED));
    }

    public void settingsWarningDialogOk() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.SETTINGS_WARNING_DIALOG_OK));
    }

    public void settingsWarningDialogCancel() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.SETTINGS_WARNING_DIALOG_CANCEL));
    }
    
    public void swipeHintDisplayed() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.SWIPE_HINT_DISPLAYED));
    }
    
    public void cancelDuringListening() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.CANCEL_DURING_LISTENING));
    }

    public void cancelDuringWorking() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.CANCEL_DURING_WORKING));
    }

    public void cancelDuringError() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.CANCEL_DURING_ERROR));
    }
    
    public void punctuationHintDisplayed() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.PUNCTUATION_HINT_DISPLAYED));
    }
    
    public void error(int code) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.ERROR);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_ERROR_CODE, code);
        mContext.sendBroadcast(i);
    }

    public void start(String locale, boolean swipe) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.START);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_START_LOCALE, locale);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_START_SWIPE, swipe);
        i.putExtra(LoggingEvents.EXTRA_TIMESTAMP, System.currentTimeMillis());
        mContext.sendBroadcast(i);
    }
    
    public void voiceInputDelivered(int length) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.VOICE_INPUT_DELIVERED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, length);
        mContext.sendBroadcast(i);
    }

    public void textModifiedByTypingInsertion(int length) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.TEXT_MODIFIED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, length);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_TYPE,
                LoggingEvents.VoiceIme.TEXT_MODIFIED_TYPE_TYPING_INSERTION);
        mContext.sendBroadcast(i);
    }

    public void textModifiedByTypingInsertionPunctuation(int length) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.TEXT_MODIFIED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, length);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_TYPE,
                LoggingEvents.VoiceIme.TEXT_MODIFIED_TYPE_TYPING_INSERTION_PUNCTUATION);
        mContext.sendBroadcast(i);
    }

    public void textModifiedByTypingDeletion(int length) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.TEXT_MODIFIED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, length);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_TYPE,
                LoggingEvents.VoiceIme.TEXT_MODIFIED_TYPE_TYPING_DELETION);

        mContext.sendBroadcast(i);
    }


    public void textModifiedByChooseSuggestion(int suggestionLength, int replacedPhraseLength,
                                               int index, String before, String after) {
        setHasLoggingInfo(true);
        Intent i = newLoggingBroadcast(LoggingEvents.VoiceIme.TEXT_MODIFIED);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_LENGTH, suggestionLength);
        i.putExtra(VoiceInputLoggerCompatUtils.EXTRA_TEXT_REPLACED_LENGTH, replacedPhraseLength);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_TEXT_MODIFIED_TYPE,
                LoggingEvents.VoiceIme.TEXT_MODIFIED_TYPE_CHOOSE_SUGGESTION);
        i.putExtra(LoggingEvents.VoiceIme.EXTRA_N_BEST_CHOOSE_INDEX, index);
        i.putExtra(VoiceInputLoggerCompatUtils.EXTRA_BEFORE_N_BEST_CHOOSE, before);
        i.putExtra(VoiceInputLoggerCompatUtils.EXTRA_AFTER_N_BEST_CHOOSE, after);
        mContext.sendBroadcast(i);
    }

    public void inputEnded() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(LoggingEvents.VoiceIme.INPUT_ENDED));
    }
    
    public void voiceInputSettingEnabled() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.VOICE_INPUT_SETTING_ENABLED));
    }
    
    public void voiceInputSettingDisabled() {
        setHasLoggingInfo(true);
        mContext.sendBroadcast(newLoggingBroadcast(
                LoggingEvents.VoiceIme.VOICE_INPUT_SETTING_DISABLED));
    }

    private void setHasLoggingInfo(boolean hasLoggingInfo) {
        mHasLoggingInfo = hasLoggingInfo;
        // If applications that call UserHappinessSignals.userAcceptedImeText
        // make that call after VoiceInputLogger.flush() calls this method with false, we
        // will lose those happiness signals. For example, consider the gmail sequence:
        // 1. compose message
        // 2. speak message into message field
        // 3. type subject into subject field
        // 4. press send
        // We will NOT get the signal that the user accepted the voice inputted message text
        // because when the user tapped on the subject field, the ime's flush will be triggered
        // and the hasLoggingInfo will be then set to false. So by the time the user hits send
        // we have essentially forgotten about any voice input.
        // However the following (more common) use case is properly logged
        // 1. compose message
        // 2. type subject in subject field
        // 3. speak message in message field
        // 4. press send
        VoiceInputLoggerCompatUtils.setHasVoiceLoggingInfoCompat(hasLoggingInfo);
    }

    private boolean hasLoggingInfo(){
        return mHasLoggingInfo;
    }

}
