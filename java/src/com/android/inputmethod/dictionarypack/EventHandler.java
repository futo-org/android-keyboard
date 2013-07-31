/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.dictionarypack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class EventHandler extends BroadcastReceiver {
    /**
     * Receives a intent broadcast.
     *
     * We receive every day a broadcast indicating that date changed.
     * Then we wait a random amount of time before actually registering
     * the download, to avoid concentrating too many accesses around
     * midnight in more populated timezones.
     * We receive all broadcasts here, so this can be either the DATE_CHANGED broadcast, the
     * UPDATE_NOW private broadcast that we receive when the time-randomizing alarm triggers
     * for regular update or from applications that want to test the dictionary pack, or a
     * broadcast from DownloadManager telling that a download has finished.
     * See inside of AndroidManifest.xml to see which events are caught.
     * Also @see {@link BroadcastReceiver#onReceive(Context, Intent)}
     *
     * @param context the context of the application.
     * @param intent the intent that was broadcast.
     */
    @Override
    public void onReceive(final Context context, final Intent intent) {
        intent.setClass(context, DictionaryService.class);
        context.startService(intent);
    }
}
