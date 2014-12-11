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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import javax.annotation.Nonnull;

/**
 * This class keeps track of the network connectivity state by receiving the system intent
 * {@link ConnectivityManager#CONNECTIVITY_ACTION}, and invokes an registered call back to notify
 * changes of the network connectivity state.
 */
public final class NetworkConnectivityUtils {
    private static NetworkConnectivityReceiver sNetworkConnectivityReceiver;

    public interface NetworkStateChangeListener {
        /**
         * Called when the network connectivity state has changed.
         */
        public void onNetworkStateChanged();
    }

    private static class NetworkConnectivityReceiver extends BroadcastReceiver {
        @Nonnull
        private final NetworkStateChangeListener mListener;
        private boolean mIsNetworkConnected;

        public NetworkConnectivityReceiver(@Nonnull final NetworkStateChangeListener listener,
                final boolean isNetworkConnected) {
            mListener = listener;
            mIsNetworkConnected = isNetworkConnected;
        }

        public synchronized boolean isNetworkConnected() {
            return mIsNetworkConnected;
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                final boolean noConnection = intent.getBooleanExtra(
                        ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
                synchronized (this) {
                    mIsNetworkConnected = !noConnection;
                }
                mListener.onNetworkStateChanged();
            }
        }
    }

    private NetworkConnectivityUtils() {
        // This utility class is not publicly instantiable.
    }

    public static void onCreate(@Nonnull final Context context,
            @Nonnull final NetworkStateChangeListener listener) {
        final ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        final boolean isNetworkConnected = (info != null && info.isConnected());

        // Register {@link BroadcastReceiver} for the network connectivity state change.
        final NetworkConnectivityReceiver receiver = new NetworkConnectivityReceiver(
                listener, isNetworkConnected);
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(receiver, filter);

        sNetworkConnectivityReceiver = receiver;
    }

    public static void onDestroy(final Context context) {
        context.unregisterReceiver(sNetworkConnectivityReceiver);
    }

    public static boolean isNetworkConnected() {
        final NetworkConnectivityReceiver receiver = sNetworkConnectivityReceiver;
        return receiver != null && receiver.isNetworkConnected();
    }
}
