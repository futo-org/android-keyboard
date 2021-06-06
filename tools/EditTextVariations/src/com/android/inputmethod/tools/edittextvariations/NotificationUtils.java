/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.inputmethod.tools.edittextvariations;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class NotificationUtils {
    private static final String REPLY_ACTION = "REPLY_ACTION";
    private static final String KEY_REPLY = "KEY_REPLY";
    private static final String KEY_NOTIFICATION_ID = "KEY_NOTIFICATION_ID";
    private static final String CHANNEL_NAME = "Channel Name";
    private static final String CHANNEL_DESCRIPTION = "Channel Description";
    private static final String CHANNEL_ID = "Channel ID";
    private static final AtomicInteger sNextNotificationId = new AtomicInteger(1);

    private static final Object sLock = new Object();
    private static boolean sNotificationChannelInitialized = false;

    static final boolean NOTIFICATION_CHANNEL_REQUIRED =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    static final boolean DIRECT_REPLY_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;

    private static Notification.Builder createNotificationBuilder(Context context) {
        if (!NOTIFICATION_CHANNEL_REQUIRED) {
            // NotificationChannel is not implemented.  No need to set up notification channel.
            return new Notification.Builder(context);
        }

        // Make sure that a notification channel is created *before* we send a notification.
        synchronized (sLock) {
            if (!sNotificationChannelInitialized) {
                final NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(CHANNEL_DESCRIPTION);
                context.getSystemService(NotificationManager.class)
                        .createNotificationChannel(channel);
                sNotificationChannelInitialized = true;
            }
        }
        return new Notification.Builder(context, CHANNEL_ID);
    }

    static void sendDirectReplyNotification(Context context) {
        if (!DIRECT_REPLY_SUPPORTED) {
            // DirectReply is not supported.
            return;
        }

        RemoteInput remoteInput = new RemoteInput.Builder(KEY_REPLY)
                .setLabel("Reply Label")
                .build();

        final int notificationId = sNextNotificationId.getAndIncrement();
        final PendingIntent pendingIntent = getReplyPendingIntent(context, notificationId);
        final PendingIntent activityIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, EditorActivity.class), 0);
        final Notification.Action action =
                new Notification.Action.Builder(null, "Direct Reply Test", pendingIntent)
                        .addRemoteInput(remoteInput)
                        .build();
        final Notification notification = createNotificationBuilder(context)
                .setContentText("Content Title")
                .setContentIntent(activityIntent)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentText("Message from " + UserHandle.getUserHandleForUid(Process.myUid()))
                .setShowWhen(true)
                .addAction(action)
                .build();
        context.getSystemService(NotificationManager.class).notify(notificationId, notification);
    }

    static void onReceiveDirectReply(Context context, Intent intent) {
        final Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput == null) {
            return;
        }
        final CharSequence reply = remoteInput.getCharSequence(KEY_REPLY);
        final int notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, 0);
        final Notification.Builder notificationBuilder =
                new Notification.Builder(context, CHANNEL_ID);
        notificationBuilder.setContentText("Content Title")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentText(String.format("Sent \"%s\" to %s", reply,
                        UserHandle.getUserHandleForUid(Process.myUid())));
        context.getSystemService(NotificationManager.class)
                .notify(notificationId, notificationBuilder.build());
    }

    private static PendingIntent getReplyPendingIntent(Context context, int notificationId) {
        final Intent intent = new Intent(context, NotificationBroadcastReceiver.class);
        intent.setAction(REPLY_ACTION);
        intent.putExtra(KEY_NOTIFICATION_ID, notificationId);
        // Pass notificationId as the result code to get a new PendingIntent rather than an existing
        // one.
        return PendingIntent.getBroadcast(context.getApplicationContext(), notificationId, intent,
                PendingIntent.FLAG_ONE_SHOT);
    }
}
