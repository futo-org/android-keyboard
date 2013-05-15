/**
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.inputmethod.latin.R;

import java.util.Locale;
import java.util.Random;

/**
 * Service that handles background tasks for the dictionary provider.
 *
 * This service provides the context for the long-running operations done by the
 * dictionary provider. Those include:
 * - Checking for the last update date and scheduling the next update. This runs every
 *   day around midnight, upon reception of the DATE_CHANGED_INTENT_ACTION broadcast.
 *   Every four days, it schedules an update of the metadata with the alarm manager.
 * - Issuing the order to update the metadata. This runs every four days, between 0 and
 *   6, upon reception of the UPDATE_NOW_INTENT_ACTION broadcast sent by the alarm manager
 *   as a result of the above action.
 * - Handling a download that just ended. These come in two flavors:
 *   - Metadata is finished downloading. We should check whether there are new dictionaries
 *     available, and download those that we need that have new versions.
 *   - A dictionary file finished downloading. We should put the file ready for a client IME
 *     to access, and mark the current state as such.
 */
public final class DictionaryService extends Service {
    private static final String TAG = DictionaryService.class.getName();

    /**
     * The package name, to use in the intent actions.
     */
    private static final String PACKAGE_NAME = "com.android.android.inputmethod.latin";

    /**
     * The action of the intent to tell the dictionary provider to update now.
     */
    private static final String UPDATE_NOW_INTENT_ACTION = PACKAGE_NAME + ".UPDATE_NOW";

    /**
     * The action of the date changing, used to schedule a periodic freshness check
     */
    private static final String DATE_CHANGED_INTENT_ACTION =
            Intent.ACTION_DATE_CHANGED;

    /**
     * The action of displaying a toast to warn the user an automatic download is starting.
     */
    /* package */ static final String SHOW_DOWNLOAD_TOAST_INTENT_ACTION =
            PACKAGE_NAME + ".SHOW_DOWNLOAD_TOAST_INTENT_ACTION";

    /**
     * A locale argument, as a String.
     */
    /* package */ static final String LOCALE_INTENT_ARGUMENT = "locale";

    /**
     * How often, in milliseconds, we want to update the metadata. This is a
     * floor value; actually, it may happen several hours later, or even more.
     */
    private static final long UPDATE_FREQUENCY = 4 * DateUtils.DAY_IN_MILLIS;

    /**
     * We are waked around midnight, local time. We want to wake between midnight and 6 am,
     * roughly. So use a random time between 0 and this delay.
     */
    private static final int MAX_ALARM_DELAY = 6 * ((int)AlarmManager.INTERVAL_HOUR);

    /**
     * How long we consider a "very long time". If no update took place in this time,
     * the content provider will trigger an update in the background.
     */
    private static final long VERY_LONG_TIME = 14 * DateUtils.DAY_IN_MILLIS;

    /**
     * The last seen start Id. This must be stored because we must only call stopSelfResult() with
     * the last seen Id, or the service won't stop.
     */
    private int mLastSeenStartId;

    /**
     * The command count. We need this because we need to not call stopSelfResult() while we still
     * have commands running.
     */
    private int mCommandCount;

    @Override
    public void onCreate() {
        mLastSeenStartId = 0;
        mCommandCount = 0;
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // This service cannot be bound
        return null;
    }

    /**
     * Executes an explicit command.
     *
     * This is the entry point for arbitrary commands that are executed upon reception of certain
     * events that should be executed on the context of this service. The supported commands are:
     * - Check last update time and possibly schedule an update of the data for later.
     *     This is triggered every day, upon reception of the DATE_CHANGED_INTENT_ACTION broadcast.
     * - Update data NOW.
     *     This is normally received upon trigger of the scheduled update.
     * - Handle a finished download.
     *     This executes the actions that must be taken after a file (metadata or dictionary data
     *     has been downloaded (or failed to download).
     */
    @Override
    public synchronized int onStartCommand(final Intent intent, final int flags,
            final int startId) {
        final DictionaryService self = this;
        mLastSeenStartId = startId;
        mCommandCount += 1;
        if (SHOW_DOWNLOAD_TOAST_INTENT_ACTION.equals(intent.getAction())) {
            // This is a UI action, it can't be run in another thread
            showStartDownloadingToast(this, LocaleUtils.constructLocaleFromString(
                    intent.getStringExtra(LOCALE_INTENT_ARGUMENT)));
        } else {
            // If it's a command that does not require UI, create a thread to do the work
            // and return right away. DATE_CHANGED or UPDATE_NOW are examples of such commands.
            new Thread("updateOrFinishDownload") {
                @Override
                public void run() {
                    dispatchBroadcast(self, intent);
                    synchronized(self) {
                        if (--mCommandCount <= 0) {
                            if (!stopSelfResult(mLastSeenStartId)) {
                                Log.e(TAG, "Can't stop ourselves");
                            }
                        }
                    }
                }
            }.start();
        }
        return Service.START_REDELIVER_INTENT;
    }

    private static void dispatchBroadcast(final Context context, final Intent intent) {
        if (DATE_CHANGED_INTENT_ACTION.equals(intent.getAction())) {
            // This happens when the date of the device changes. This normally happens
            // at midnight local time, but it may happen if the user changes the date
            // by hand or something similar happens.
            checkTimeAndMaybeSetupUpdateAlarm(context);
        } else if (UPDATE_NOW_INTENT_ACTION.equals(intent.getAction())) {
            // Intent to trigger an update now.
            UpdateHandler.update(context, false);
        } else {
            UpdateHandler.downloadFinished(context, intent);
        }
    }

    /**
     * Setups an alarm to check for updates if an update is due.
     */
    private static void checkTimeAndMaybeSetupUpdateAlarm(final Context context) {
        // Of all clients, if the one that hasn't been updated for the longest
        // is still more recent than UPDATE_FREQUENCY, do nothing.
        if (!isLastUpdateAtLeastThisOld(context, UPDATE_FREQUENCY)) return;

        PrivateLog.log("Date changed - registering alarm");
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

        // Best effort to wake between midnight and MAX_ALARM_DELAY in the morning.
        // It doesn't matter too much if this is very inexact.
        final long now = System.currentTimeMillis();
        final long alarmTime = now + new Random().nextInt(MAX_ALARM_DELAY);
        final Intent updateIntent = new Intent(DictionaryService.UPDATE_NOW_INTENT_ACTION);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                updateIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        // We set the alarm in the type that doesn't forcefully wake the device
        // from sleep, but fires the next time the device actually wakes for any
        // other reason.
        if (null != alarmManager) alarmManager.set(AlarmManager.RTC, alarmTime, pendingIntent);
    }

    /**
     * Utility method to decide whether the last update is older than a certain time.
     *
     * @return true if at least `time' milliseconds have elapsed since last update, false otherwise.
     */
    private static boolean isLastUpdateAtLeastThisOld(final Context context, final long time) {
        final long now = System.currentTimeMillis();
        final long lastUpdate = MetadataDbHelper.getOldestUpdateTime(context);
        PrivateLog.log("Last update was " + lastUpdate);
        return lastUpdate + time < now;
    }

    /**
     * Refreshes data if it hasn't been refreshed in a very long time.
     *
     * This will check the last update time, and if it's been more than VERY_LONG_TIME,
     * update metadata now - and possibly take subsequent update actions.
     */
    public static void updateNowIfNotUpdatedInAVeryLongTime(final Context context) {
        if (!isLastUpdateAtLeastThisOld(context, VERY_LONG_TIME)) return;
        UpdateHandler.update(context, false);
    }

    /**
     * Shows a toast informing the user that an automatic dictionary download is starting.
     */
    private static void showStartDownloadingToast(final Context context, final Locale locale) {
        final String toastText = String.format(
                context.getString(R.string.toast_downloading_suggestions),
                locale.getDisplayName());
        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show();
    }
}
