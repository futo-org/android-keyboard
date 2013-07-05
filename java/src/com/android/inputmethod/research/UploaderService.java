/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Service to invoke the uploader.
 *
 * Can be regularly invoked, invoked on boot, etc.
 */
public final class UploaderService extends IntentService {
    private static final String TAG = UploaderService.class.getSimpleName();
    public static final long RUN_INTERVAL = AlarmManager.INTERVAL_HOUR;
    public static final String EXTRA_UPLOAD_UNCONDITIONALLY = UploaderService.class.getName()
            + ".extra.UPLOAD_UNCONDITIONALLY";

    public UploaderService() {
        super("Research Uploader Service");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        // We may reach this point either because the alarm fired, or because the system explicitly
        // requested that an Upload occur.  In the latter case, we want to cancel the alarm in case
        // it's about to fire.
        cancelAndRescheduleUploadingService(this, false /* needsRescheduling */);

        final Uploader uploader = new Uploader(this);
        if (!uploader.isPossibleToUpload()) return;
        if (isUploadingUnconditionally(intent.getExtras()) || uploader.isConvenientToUpload()) {
            uploader.doUpload();
        }
        cancelAndRescheduleUploadingService(this, true /* needsRescheduling */);
    }

    private boolean isUploadingUnconditionally(final Bundle bundle) {
        if (bundle == null) return false;
        if (bundle.containsKey(EXTRA_UPLOAD_UNCONDITIONALLY)) {
            return bundle.getBoolean(EXTRA_UPLOAD_UNCONDITIONALLY);
        }
        return false;
    }

    /**
     * Arrange for the UploaderService to be run on a regular basis.
     *
     * Any existing scheduled invocation of UploaderService is removed and optionally rescheduled.
     * This may cause problems if this method is called so often that no scheduled invocation is
     * ever run.  But if the delay is short enough that it will go off when the user is sleeping,
     * then there should be no starvation.
     *
     * @param context {@link Context} object
     * @param needsRescheduling whether to schedule a future intent to be delivered to this service
     */
    public static void cancelAndRescheduleUploadingService(final Context context,
            final boolean needsRescheduling) {
        final Intent intent = new Intent(context, UploaderService.class);
        final PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(
                Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
        if (needsRescheduling) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime()
                    + UploaderService.RUN_INTERVAL, pendingIntent);
        }
    }
}
