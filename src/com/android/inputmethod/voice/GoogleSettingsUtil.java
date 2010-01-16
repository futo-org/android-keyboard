/*
 * Copyright (C) 2009 Google Inc.
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

package com.android.inputmethod.voice;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * Utility for getting Google-specific settings from GoogleSettings.Partner or
 * Gservices. Retrieving such settings may fail on a non-Google Experience
 * Device (GED)
 */
public class GoogleSettingsUtil {
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
     * Uri to use to access gservices settings
     */
    private static final Uri GSERVICES_URI = Uri.parse("content://settings/gservices");

    private static final String TAG = GoogleSettingsUtil.class.getSimpleName();

    private static final boolean DBG = false;

    /**
     * Safely query for a Gservices string setting, which may not be available if this
     * is not a Google Experience Device.
     * 
     * @param cr The content resolver to use
     * @param key The setting to look up
     * @param defaultValue The default value to use if none can be found
     * @return The value of the setting, or defaultValue if it couldn't be found
     */
    public static String getGservicesString(ContentResolver cr, String key, String defaultValue) {
        return getSettingString(GSERVICES_URI, cr, key, defaultValue);
    }
    
    /**
     * Safely query for a Gservices int setting, which may not be available if this
     * is not a Google Experience Device.
     * 
     * @param cr The content resolver to use
     * @param key The setting to look up
     * @param defaultValue The default value to use if the setting couldn't be found or parsed
     * @return The value of the setting, or defaultValue if it couldn't be found or parsed
     */
    public static int getGservicesInt(ContentResolver cr, String key, int defaultValue) {
        try {
            return Integer.parseInt(getGservicesString(cr, key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Safely query for a Gservices float setting, which may not be available if this
     * is not a Google Experience Device.
     * 
     * @param cr The content resolver to use
     * @param key The setting to look up
     * @param defaultValue The default value to use if the setting couldn't be found or parsed
     * @return The value of the setting, or defaultValue if it couldn't be found or parsed
     */
    public static float getGservicesFloat(ContentResolver cr, String key, float defaultValue) {
        try {
            return Float.parseFloat(getGservicesString(cr, key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * A safe way to query for a setting on both Google Experience and
     * non-Google Experience devices, (code adapted from maps application
     * examples)
     * 
     * @param uri The uri to provide to the content resolver
     * @param cr The content resolver to use
     * @param key The setting to look up
     * @param defaultValue The default value to use if none can be found
     * @return The value of the setting, or defaultValue if it couldn't be found
     */
    private static String getSettingString(Uri uri, ContentResolver cr, String key,
            String defaultValue) {
        String value = null;

        Cursor cursor = null;
        try {
            cursor = cr.query(uri, new String[] {
                "value"
            }, "name='" + key + "'", null, null);
            if ((cursor != null) && cursor.moveToFirst()) {
                value = cursor.getString(cursor.getColumnIndexOrThrow("value"));
            }
        } catch (Throwable t) {
            // This happens because we're probably running a non Type 1 aka
            // Google Experience device which doesn't have the Google libraries.
            if (DBG) {
                Log.d(TAG, "Error getting setting from " + uri + " for key " + key + ": " + t);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (DBG && value == null) {
            Log.i(TAG, "no setting found from " + uri + " for key " + key + ", returning default");
        }
        
        return (value != null) ? value : defaultValue;
    }
}
