/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.inputmethod.latin.utils;

import static com.android.inputmethod.latin.utils.ImportantNoticeUtils.KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE;
import static com.android.inputmethod.latin.utils.ImportantNoticeUtils.KEY_IMPORTANT_NOTICE_VERSION;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;

import java.util.concurrent.TimeUnit;

@SmallTest
public class ImportantNoticeUtilsTests extends AndroidTestCase {
    // This should be aligned with R.integer.config_important_notice_version.
    private static final int CURRENT_IMPORTANT_NOTICE_VERSION = 1;

    private ImportantNoticePreferences mImportantNoticePreferences;

    private static class ImportantNoticePreferences {
        private final SharedPreferences mPref;

        private Integer mVersion;
        private Long mLastTime;

        public ImportantNoticePreferences(final Context context) {
            mPref = ImportantNoticeUtils.getImportantNoticePreferences(context);
        }

        private Integer getInt(final String key) {
            if (mPref.contains(key)) {
                return mPref.getInt(key, 0);
            }
            return null;
        }

        public Long getLong(final String key) {
            if (mPref.contains(key)) {
                return mPref.getLong(key, 0);
            }
            return null;
        }

        private void putInt(final String key, final Integer value) {
            if (value == null) {
                removePreference(key);
            } else {
                mPref.edit().putInt(key, value).apply();
            }
        }

        private void putLong(final String key, final Long value) {
            if (value == null) {
                removePreference(key);
            } else {
                mPref.edit().putLong(key, value).apply();
            }
        }

        private void removePreference(final String key) {
            mPref.edit().remove(key).apply();
        }

        public void save() {
            mVersion = getInt(KEY_IMPORTANT_NOTICE_VERSION);
            mLastTime = getLong(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE);
        }

        public void restore() {
            putInt(KEY_IMPORTANT_NOTICE_VERSION, mVersion);
            putLong(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE, mLastTime);
        }

        public void clear() {
            removePreference(KEY_IMPORTANT_NOTICE_VERSION);
            removePreference(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE);
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mImportantNoticePreferences = new ImportantNoticePreferences(getContext());
        mImportantNoticePreferences.save();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mImportantNoticePreferences.restore();
    }

    public void testCurrentVersion() {
        assertEquals("Current version", CURRENT_IMPORTANT_NOTICE_VERSION,
                ImportantNoticeUtils.getCurrentImportantNoticeVersion(getContext()));
    }

    public void testUpdateVersion() {
        mImportantNoticePreferences.clear();

        assertEquals("Current boolean before update", true,
                ImportantNoticeUtils.shouldShowImportantNotice(getContext()));
        assertEquals("Last version before update", 0,
                ImportantNoticeUtils.getLastImportantNoticeVersion(getContext()));
        assertEquals("Next version before update ", 1,
                ImportantNoticeUtils.getNextImportantNoticeVersion(getContext()));
        assertEquals("Current title before update", false, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeTitle(getContext())));
        assertEquals("Current contents before update", false, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeContents(getContext())));

        ImportantNoticeUtils.updateLastImportantNoticeVersion(getContext());

        assertEquals("Current boolean after update", false,
                ImportantNoticeUtils.shouldShowImportantNotice(getContext()));
        assertEquals("Last version after update", 1,
                ImportantNoticeUtils.getLastImportantNoticeVersion(getContext()));
        assertEquals("Next version after update", 2,
                ImportantNoticeUtils.getNextImportantNoticeVersion(getContext()));
        assertEquals("Current title after update", true, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeTitle(getContext())));
        assertEquals("Current contents after update", true, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeContents(getContext())));
    }

    private static void sleep(final long millseconds) {
        try { Thread.sleep(millseconds); } catch (final Exception e) { /* ignore */ }
    }

    public void testTimeout() {
        final long lastTime = System.currentTimeMillis()
                - ImportantNoticeUtils.TIMEOUT_OF_IMPORTANT_NOTICE
                + TimeUnit.MILLISECONDS.toMillis(1000);
        mImportantNoticePreferences.clear();
        assertEquals("Before set last time", null,
                mImportantNoticePreferences.getLong(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE));
        assertEquals("Set last time", false,
                ImportantNoticeUtils.hasTimeoutPassed(getContext(), lastTime));
        assertEquals("After set last time", (Long)lastTime,
                mImportantNoticePreferences.getLong(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE));

        // Call {@link ImportantNoticeUtils#shouldShowImportantNotice(Context)} before timeout.
        assertEquals("Current boolean before timeout 1", true,
                ImportantNoticeUtils.shouldShowImportantNotice(getContext()));
        assertEquals("Last version before timeout 1", 0,
                ImportantNoticeUtils.getLastImportantNoticeVersion(getContext()));
        assertEquals("Next version before timeout 1", 1,
                ImportantNoticeUtils.getNextImportantNoticeVersion(getContext()));
        assertEquals("Last time before timeout 1", (Long)lastTime,
                mImportantNoticePreferences.getLong(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE));
        assertEquals("Current title before timeout 1", false, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeTitle(getContext())));
        assertEquals("Current contents before timeout 1", false, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeContents(getContext())));

        sleep(TimeUnit.MILLISECONDS.toMillis(600));

        // Call {@link ImportantNoticeUtils#shouldShowImportantNotice(Context)} before timeout
        // again.
        assertEquals("Current boolean before timeout 2", true,
                ImportantNoticeUtils.shouldShowImportantNotice(getContext()));
        assertEquals("Last version before timeout 2", 0,
                ImportantNoticeUtils.getLastImportantNoticeVersion(getContext()));
        assertEquals("Next version before timeout 2", 1,
                ImportantNoticeUtils.getNextImportantNoticeVersion(getContext()));
        assertEquals("Last time before timeout 2", (Long)lastTime,
                mImportantNoticePreferences.getLong(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE));
        assertEquals("Current title before timeout 2", false, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeTitle(getContext())));
        assertEquals("Current contents before timeout 2", false, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeContents(getContext())));

        sleep(TimeUnit.MILLISECONDS.toMillis(600));

        // Call {@link ImportantNoticeUtils#shouldShowImportantNotice(Context)} after timeout.
        assertEquals("Current boolean after timeout 1", false,
                ImportantNoticeUtils.shouldShowImportantNotice(getContext()));
        assertEquals("Last version after timeout 1", 1,
                ImportantNoticeUtils.getLastImportantNoticeVersion(getContext()));
        assertEquals("Next version after timeout 1", 2,
                ImportantNoticeUtils.getNextImportantNoticeVersion(getContext()));
        assertEquals("Last time aflter timeout 1", null,
                mImportantNoticePreferences.getLong(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE));
        assertEquals("Current title after timeout 1", true, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeTitle(getContext())));
        assertEquals("Current contents after timeout 1", true, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeContents(getContext())));

        sleep(TimeUnit.MILLISECONDS.toMillis(600));

        // Call {@link ImportantNoticeUtils#shouldShowImportantNotice(Context)} after timeout again.
        assertEquals("Current boolean after timeout 2", false,
                ImportantNoticeUtils.shouldShowImportantNotice(getContext()));
        assertEquals("Last version after timeout 2", 1,
                ImportantNoticeUtils.getLastImportantNoticeVersion(getContext()));
        assertEquals("Next version after timeout 2", 2,
                ImportantNoticeUtils.getNextImportantNoticeVersion(getContext()));
        assertEquals("Last time aflter timeout 2", null,
                mImportantNoticePreferences.getLong(KEY_TIMESTAMP_OF_FIRST_IMPORTANT_NOTICE));
        assertEquals("Current title after timeout 2", true, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeTitle(getContext())));
        assertEquals("Current contents after timeout 2", true, TextUtils.isEmpty(
                ImportantNoticeUtils.getNextImportantNoticeContents(getContext())));
    }
}
