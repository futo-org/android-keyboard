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

import android.Manifest;
import android.app.AlarmManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.define.ProductionFlag;

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

public final class UploaderService extends IntentService {
    private static final String TAG = UploaderService.class.getSimpleName();
    private static final boolean DEBUG = false && ProductionFlag.IS_EXPERIMENTAL_DEBUG;
    // Set IS_INHIBITING_AUTO_UPLOAD to true for local testing
    private static final boolean IS_INHIBITING_AUTO_UPLOAD = false
            && ProductionFlag.IS_EXPERIMENTAL_DEBUG;  // Force false in production
    public static final long RUN_INTERVAL = AlarmManager.INTERVAL_HOUR;
    public static final String EXTRA_UPLOAD_UNCONDITIONALLY = UploaderService.class.getName()
            + ".extra.UPLOAD_UNCONDITIONALLY";
    private static final int BUF_SIZE = 1024 * 8;
    protected static final int TIMEOUT_IN_MS = 1000 * 4;

    private File mFilesDir;
    private URL mUrl;

    public UploaderService() {
        super("Research Uploader Service");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mFilesDir = null;
        mUrl = null;

        if (!hasUploadingPermission()) {
            return;
        }

        try {
            final String urlString = getString(R.string.research_logger_upload_url);
            if (urlString == null || urlString.equals("")) {
                return;
            }
            mFilesDir = getFilesDir();
            mUrl = new URL(urlString);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public boolean isPossibleToUpload() {
        return hasUploadingPermission() && mUrl != null && !IS_INHIBITING_AUTO_UPLOAD;
    }

    private boolean hasUploadingPermission() {
        final PackageManager packageManager = getPackageManager();
        return packageManager.checkPermission(Manifest.permission.INTERNET,
                getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!isPossibleToUpload()) return;
        if (isUploadingUnconditionally(intent.getExtras()) || isConvenientToUpload()) {
            doUpload();
        }
    }

    private boolean isUploadingUnconditionally(final Bundle bundle) {
        if (bundle == null) return false;
        if (bundle.containsKey(EXTRA_UPLOAD_UNCONDITIONALLY)) {
            return bundle.getBoolean(EXTRA_UPLOAD_UNCONDITIONALLY);
        }
        return false;
    }

    private boolean isConvenientToUpload() {
        return isExternallyPowered() && hasWifiConnection();
    }

    private boolean isExternallyPowered() {
        final Intent intent = registerReceiver(null, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        final int pluggedState = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return pluggedState == BatteryManager.BATTERY_PLUGGED_AC
                || pluggedState == BatteryManager.BATTERY_PLUGGED_USB;
    }

    private boolean hasWifiConnection() {
        final ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo wifiInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifiInfo.isConnected();
    }

    private void doUpload() {
        if (mFilesDir == null) {
            return;
        }
        final File[] files = mFilesDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().startsWith(ResearchLogger.LOG_FILENAME_PREFIX)
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
    }

    private boolean uploadFile(File file) {
        if (DEBUG) {
            Log.d(TAG, "attempting upload of " + file.getAbsolutePath());
        }
        boolean success = false;
        final int contentLength = (int) file.length();
        HttpURLConnection connection = null;
        InputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            connection = (HttpURLConnection) mUrl.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(contentLength);
            final OutputStream os = connection.getOutputStream();
            final byte[] buf = new byte[BUF_SIZE];
            int numBytesRead;
            while ((numBytesRead = fileInputStream.read(buf)) != -1) {
                os.write(buf, 0, numBytesRead);
                if (DEBUG) {
                    Log.d(TAG, new String(buf));
                }
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "upload failed: " + connection.getResponseCode());
                InputStream netInputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(netInputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(TAG, "| " + reader.readLine());
                }
                reader.close();
                return success;
            }
            file.delete();
            success = true;
            if (DEBUG) {
                Log.d(TAG, "upload successful");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
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
