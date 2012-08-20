/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.research;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.util.Log;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.R.string;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ResearchLogUploader {
    private static final String TAG = ResearchLogUploader.class.getSimpleName();
    private static final int UPLOAD_INTERVAL_IN_MS = 1000 * 60 * 15; // every 15 min
    private static final int BUF_SIZE = 1024 * 8;

    private final boolean mCanUpload;
    private final Context mContext;
    private final File mFilesDir;
    private final URL mUrl;
    private final ScheduledExecutorService mExecutor;

    private Runnable doUploadRunnable = new UploadRunnable(null, false);

    public ResearchLogUploader(final Context context, final File filesDir) {
        mContext = context;
        mFilesDir = filesDir;
        final PackageManager packageManager = context.getPackageManager();
        final boolean hasPermission = packageManager.checkPermission(Manifest.permission.INTERNET,
                context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
        if (!hasPermission) {
            mCanUpload = false;
            mUrl = null;
            mExecutor = null;
            return;
        }
        URL tempUrl = null;
        boolean canUpload = false;
        ScheduledExecutorService executor = null;
        try {
            final String urlString = context.getString(R.string.research_logger_upload_url);
            if (urlString == null || urlString.equals("")) {
                return;
            }
            tempUrl = new URL(urlString);
            canUpload = true;
            executor = Executors.newSingleThreadScheduledExecutor();
        } catch (MalformedURLException e) {
            tempUrl = null;
            e.printStackTrace();
            return;
        } finally {
            mCanUpload = canUpload;
            mUrl = tempUrl;
            mExecutor = executor;
        }
    }

    public void start() {
        if (mCanUpload) {
            Log.d(TAG, "scheduling regular uploading");
            mExecutor.scheduleWithFixedDelay(doUploadRunnable, UPLOAD_INTERVAL_IN_MS,
                    UPLOAD_INTERVAL_IN_MS, TimeUnit.MILLISECONDS);
        } else {
            Log.d(TAG, "no permission to upload");
        }
    }

    public void uploadNow(final Callback callback) {
        // Perform an immediate upload.  Note that this should happen even if there is
        // another upload happening right now, as it may have missed the latest changes.
        // TODO: Reschedule regular upload tests starting from now.
        if (mCanUpload) {
            mExecutor.submit(new UploadRunnable(callback, true));
        }
    }

    public interface Callback {
        public void onUploadCompleted(final boolean success);
    }

    private boolean isExternallyPowered() {
        final Intent intent = mContext.registerReceiver(null, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        final int pluggedState = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return pluggedState == BatteryManager.BATTERY_PLUGGED_AC
                || pluggedState == BatteryManager.BATTERY_PLUGGED_USB;
    }

    private boolean hasWifiConnection() {
        final ConnectivityManager manager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo wifiInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifiInfo.isConnected();
    }

    class UploadRunnable implements Runnable {
        private final Callback mCallback;
        private final boolean mForceUpload;

        public UploadRunnable(final Callback callback, final boolean forceUpload) {
            mCallback = callback;
            mForceUpload = forceUpload;
        }

        @Override
        public void run() {
            doUpload();
        }

        private void doUpload() {
            if (!mForceUpload && (!isExternallyPowered() || !hasWifiConnection())) {
                return;
            }
            if (mFilesDir == null) {
                return;
            }
            final File[] files = mFilesDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().startsWith(ResearchLogger.FILENAME_PREFIX)
                            && !pathname.canWrite();
                }
            });
            boolean success = true;
            if (files.length == 0) {
                success = false;
            }
            for (final File file : files) {
                if (!uploadFile(file)) {
                    success = false;
                }
            }
            if (mCallback != null) {
                mCallback.onUploadCompleted(success);
            }
        }

        private boolean uploadFile(File file) {
            Log.d(TAG, "attempting upload of " + file.getAbsolutePath());
            boolean success = false;
            final int contentLength = (int) file.length();
            HttpURLConnection connection = null;
            InputStream fileIs = null;
            try {
                fileIs = new FileInputStream(file);
                connection = (HttpURLConnection) mUrl.openConnection();
                connection.setRequestMethod("PUT");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(contentLength);
                final OutputStream os = connection.getOutputStream();
                final byte[] buf = new byte[BUF_SIZE];
                int numBytesRead;
                while ((numBytesRead = fileIs.read(buf)) != -1) {
                    os.write(buf, 0, numBytesRead);
                }
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "upload failed: " + connection.getResponseCode());
                    InputStream netIs = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(netIs));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.d(TAG, "| " + reader.readLine());
                    }
                    reader.close();
                    return success;
                }
                file.delete();
                success = true;
                Log.d(TAG, "upload successful");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fileIs != null) {
                    try {
                        fileIs.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return success;
        }
    }
}
