/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.content.SharedPreferences;

import java.util.UUID;

public final class ResearchSettings {
    public static final String PREF_RESEARCH_LOGGER_UUID = "pref_research_logger_uuid";
    public static final String PREF_RESEARCH_LOGGER_ENABLED_FLAG =
            "pref_research_logger_enabled_flag";
    public static final String PREF_RESEARCH_LOGGER_HAS_SEEN_SPLASH =
            "pref_research_logger_has_seen_splash";
    public static final String PREF_RESEARCH_LAST_DIR_CLEANUP_TIME =
            "pref_research_last_dir_cleanup_time";

    private ResearchSettings() {
        // Intentional empty constructor for singleton.
    }

    public static String readResearchLoggerUuid(final SharedPreferences prefs) {
        if (prefs.contains(PREF_RESEARCH_LOGGER_UUID)) {
            return prefs.getString(PREF_RESEARCH_LOGGER_UUID, null);
        }
        // Generate a random string as uuid if not yet set
        final String newUuid = UUID.randomUUID().toString();
        prefs.edit().putString(PREF_RESEARCH_LOGGER_UUID, newUuid).apply();
        return newUuid;
    }

    public static boolean readResearchLoggerEnabledFlag(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_RESEARCH_LOGGER_ENABLED_FLAG, false);
    }

    public static void writeResearchLoggerEnabledFlag(final SharedPreferences prefs,
            final boolean isEnabled) {
        prefs.edit().putBoolean(PREF_RESEARCH_LOGGER_ENABLED_FLAG, isEnabled).apply();
    }

    public static boolean readHasSeenSplash(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_RESEARCH_LOGGER_HAS_SEEN_SPLASH, false);
    }

    public static void writeHasSeenSplash(final SharedPreferences prefs,
            final boolean hasSeenSplash) {
        prefs.edit().putBoolean(PREF_RESEARCH_LOGGER_HAS_SEEN_SPLASH, hasSeenSplash).apply();
    }

    public static long readResearchLastDirCleanupTime(final SharedPreferences prefs) {
        return prefs.getLong(PREF_RESEARCH_LAST_DIR_CLEANUP_TIME, 0L);
    }

    public static void writeResearchLastDirCleanupTime(final SharedPreferences prefs,
            final long lastDirCleanupTime) {
        prefs.edit().putLong(PREF_RESEARCH_LAST_DIR_CLEANUP_TIME, lastDirCleanupTime).apply();
    }
}
