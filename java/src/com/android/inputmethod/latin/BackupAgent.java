/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import com.android.inputmethod.latin.settings.LocalSettingsConstants;

import java.io.IOException;

/**
 * Backup/restore agent for LatinIME.
 * Currently it backs up the default shared preferences.
 */
public final class BackupAgent extends BackupAgentHelper {
    private static final String PREF_SUFFIX = "_preferences";

    @Override
    public void onCreate() {
        addHelper("shared_pref", new SharedPreferencesBackupHelper(this,
                getPackageName() + PREF_SUFFIX));
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        // Let the restore operation go through
        super.onRestore(data, appVersionCode, newState);

        // Remove the preferences that we don't want restored.
        final SharedPreferences.Editor prefEditor = getSharedPreferences(
                getPackageName() + PREF_SUFFIX, MODE_PRIVATE).edit();
        for (final String key : LocalSettingsConstants.PREFS_TO_SKIP_RESTORING) {
            prefEditor.remove(key);
        }
        // Flush the changes to disk.
        prefEditor.commit();
    }
}
