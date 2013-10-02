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

package com.android.inputmethod.latin.personalization;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.TimeUnit;

/**
 * Broadcast receiver for periodically updating decaying dictionaries.
 */
public class DictionaryDecayBroadcastReciever extends BroadcastReceiver {
    /**
     * The root domain for the personalization.
     */
    private static final String PERSONALIZATION_DOMAIN =
            "com.android.inputmethod.latin.personalization";

    /**
     * The action of the intent to tell the time to decay dictionaries.
     */
    private static final String DICTIONARY_DECAY_INTENT_ACTION =
            PERSONALIZATION_DOMAIN + ".DICT_DECAY";

    /**
     * Interval to update for decaying dictionaries.
     */
    private static final long DICTIONARY_DECAY_INTERVAL = TimeUnit.MINUTES.toMillis(60);

    public static void setUpIntervalAlarmForDictionaryDecaying(Context context) {
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        final Intent updateIntent = new Intent(DICTIONARY_DECAY_INTENT_ACTION);
        updateIntent.setClass(context, DictionaryDecayBroadcastReciever.class);
        final long alarmTime =  System.currentTimeMillis() + DICTIONARY_DECAY_INTERVAL;
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0 /* requestCode */,
                updateIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        if (null != alarmManager) alarmManager.setInexactRepeating(AlarmManager.RTC,
                alarmTime, DICTIONARY_DECAY_INTERVAL, pendingIntent);
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (action.equals(DICTIONARY_DECAY_INTENT_ACTION)) {
            PersonalizationHelper.tryDecayingAllOpeningUserHistoryDictionary();
        }
    }
}
