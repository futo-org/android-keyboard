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

import android.content.ContentResolver;
import android.provider.Settings;

/**
 * Utility for retrieving settings from Settings.Secure.
 */
public class SettingsUtil {
    /**
     * A whitespace-separated list of supported locales for voice input from the keyboard.
     */
    public static final String LATIN_IME_VOICE_INPUT_SUPPORTED_LOCALES =
            "latin_ime_voice_input_supported_locales";

    /**
     * A whitespace-separated list of recommended app packages for voice input from the
     * keyboard.
     */
    public static final String LATIN_IME_VOICE_INPUT_RECOMMENDED_PACKAGES =
            "latin_ime_voice_input_recommended_packages";

    /**
     * The maximum number of unique days to show the swipe hint for voice input.
     */
    public static final String LATIN_IME_VOICE_INPUT_SWIPE_HINT_MAX_DAYS =
            "latin_ime_voice_input_swipe_hint_max_days";

    /**
     * The maximum number of times to show the punctuation hint for voice input.
     */
    public static final String LATIN_IME_VOICE_INPUT_PUNCTUATION_HINT_MAX_DISPLAYS =
            "latin_ime_voice_input_punctuation_hint_max_displays";

    /**
     * Endpointer parameters for voice input from the keyboard.
     */
    public static final String LATIN_IME_SPEECH_MINIMUM_LENGTH_MILLIS =
            "latin_ime_speech_minimum_length_millis";
    public static final String LATIN_IME_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS =
            "latin_ime_speech_input_complete_silence_length_millis";
    public static final String LATIN_IME_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS =
            "latin_ime_speech_input_possibly_complete_silence_length_millis";

    /**
     * Min and max volume levels that can be displayed on the "speak now" screen.
     */
    public static final String LATIN_IME_MIN_MICROPHONE_LEVEL =
            "latin_ime_min_microphone_level";
    public static final String LATIN_IME_MAX_MICROPHONE_LEVEL =
            "latin_ime_max_microphone_level";

    /**
     * The number of sentence-level alternates to request of the server.
     */
    public static final String LATIN_IME_MAX_VOICE_RESULTS = "latin_ime_max_voice_results";

    /**
     * Get a string-valued setting.
     *
     * @param cr The content resolver to use
     * @param key The setting to look up
     * @param defaultValue The default value to use if none can be found
     * @return The value of the setting, or defaultValue if it couldn't be found
     */
    public static String getSettingsString(ContentResolver cr, String key, String defaultValue) {
        String result = Settings.Secure.getString(cr, key);
        return (result == null) ? defaultValue : result;
    }

    /**
     * Get an int-valued setting.
     *
     * @param cr The content resolver to use
     * @param key The setting to look up
     * @param defaultValue The default value to use if the setting couldn't be found or parsed
     * @return The value of the setting, or defaultValue if it couldn't be found or parsed
     */
    public static int getSettingsInt(ContentResolver cr, String key, int defaultValue) {
        return Settings.Secure.getInt(cr, key, defaultValue);
    }

    /**
     * Get a float-valued setting.
     *
     * @param cr The content resolver to use
     * @param key The setting to look up
     * @param defaultValue The default value to use if the setting couldn't be found or parsed
     * @return The value of the setting, or defaultValue if it couldn't be found or parsed
     */
    public static float getSettingsFloat(ContentResolver cr, String key, float defaultValue) {
        return Settings.Secure.getFloat(cr, key, defaultValue);
    }
}
