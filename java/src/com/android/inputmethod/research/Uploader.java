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

package com.android.inputmethod.research;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.define.ProductionFlag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Manages the uploading of ResearchLog files.
 */
public final class Uploader {
    private static final String TAG = Uploader.class.getSimpleName();
    private static final boolean DEBUG = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    // Set IS_INHIBITING_AUTO_UPLOAD to true for local testing
    private static final boolean IS_INHIBITING_UPLOAD = false
            && ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS_DEBUG;
    private static final int BUF_SIZE = 1024 * 8;

    private final Context mContext;
    private final ResearchLogDirectory mResearchLogDirectory;
    private final URL mUrl;

    public Uploader(final Context context) {
        mContext = context;
        mResearchLogDirectory = new ResearchLogDirectory(context);

        final String urlString = context.getString(R.string.research_logger_upload_url);
        if (TextUtils.isEmpty(urlString)) {
            mUrl = null;
            return;
        }
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (final MalformedURLException e) {
            Log.e(TAG, "Bad URL for uploading", e);
        }
        mUrl = url;
    }

    public boolean isPossibleToUpload() {
        return hasUploadingPermission() && mUrl != null && !IS_INHIBITING_UPLOAD;
    }

    private boolean hasUploadingPermission() {
        final PackageManager packageManager = mContext.getPackageManager();
        return packageManager.checkPermission(Manifest.permission.INTERNET,
                mContext.getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isConvenientToUpload() {
        return isExternallyPowered() && hasWifiConnection();
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

    public void doUpload() {
        final File[] files = mResearchLogDirectory.getUploadableLogFiles();
        if (files == null) return;
        for (final File file : files) {
            uploadFile(file);
        }
    }

    private void uploadFile(final File file) {
        if (DEBUG) {
            Log.d(TAG, "attempting upload of " + file.getAbsolutePath());
        }
        final int contentLength = (int) file.length();
        HttpURLConnection connection = null;
        InputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            connection = (HttpURLConnection) mUrl.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(contentLength);
            final OutputStream outputStream = connection.getOutputStream();
            uploadContents(fileInputStream, outputStream);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "upload failed: " + connection.getResponseCode());
                final InputStream netInputStream = connection.getInputStream();
                final BufferedReader reader = new BufferedReader(new InputStreamReader(
                        netInputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(TAG, "| " + reader.readLine());
                }
                reader.close();
                return;
            }
            file.delete();
            if (DEBUG) {
                Log.d(TAG, "upload successful");
            }
        } catch (final IOException e) {
            Log.e(TAG, "Exception uploading file", e);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (final IOException e) {
                    Log.e(TAG, "Exception closing uploaded file", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void uploadContents(final InputStream is, final OutputStream os)
            throws IOException {
        // TODO: Switch to NIO.
        final byte[] buf = new byte[BUF_SIZE];
        int numBytesRead;
        while ((numBytesRead = is.read(buf)) != -1) {
            os.write(buf, 0, numBytesRead);
        }
    }
}
