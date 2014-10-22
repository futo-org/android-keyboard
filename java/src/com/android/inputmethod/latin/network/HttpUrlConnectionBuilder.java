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

package com.android.inputmethod.latin.network;

import android.text.TextUtils;

import com.android.inputmethod.annotations.UsedForTesting;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Builder for {@link HttpURLConnection}s.
 *
 * TODO: Remove @UsedForTesting after this is actually used.
 */
@UsedForTesting
public class HttpUrlConnectionBuilder {
    private static final int DEFAULT_TIMEOUT_MILLIS = 5 * 1000;

    /**
     * Request header key for authentication.
     */
    public static final String HTTP_HEADER_AUTHORIZATION = "Authorization";

    /**
     * Request header key for cache control.
     */
    public static final String KEY_CACHE_CONTROL = "Cache-Control";
    /**
     * Request header value for cache control indicating no caching.
     * @see #KEY_CACHE_CONTROL
     */
    public static final String VALUE_NO_CACHE = "no-cache";

    /**
     * Indicates that the request is unidirectional - upload-only.
     * TODO: Remove @UsedForTesting after this is actually used.
     */
    @UsedForTesting
    public static final int MODE_UPLOAD_ONLY = 1;
    /**
     * Indicates that the request is unidirectional - download only.
     * TODO: Remove @UsedForTesting after this is actually used.
     */
    @UsedForTesting
    public static final int MODE_DOWNLOAD_ONLY = 2;
    /**
     * Indicates that the request is bi-directional.
     * TODO: Remove @UsedForTesting after this is actually used.
     */
    @UsedForTesting
    public static final int MODE_BI_DIRECTIONAL = 3;

    private final HashMap<String, String> mHeaderMap = new HashMap<>();

    private URL mUrl;
    private int mConnectTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private int mReadTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    private int mContentLength = -1;
    private boolean mUseCache;
    private int mMode;

    /**
     * Sets the URL that'll be used for the request.
     * This *must* be set before calling {@link #build()}
     *
     * TODO: Remove @UsedForTesting after this method is actually used.
     */
    @UsedForTesting
    public HttpUrlConnectionBuilder setUrl(String url) throws MalformedURLException {
        if (TextUtils.isEmpty(url)) {
            throw new IllegalArgumentException("URL must not be empty");
        }
        mUrl = new URL(url);
        return this;
    }

    /**
     * Sets the connect timeout. Defaults to {@value #DEFAULT_TIMEOUT_MILLIS} milliseconds.
     *
     * TODO: Remove @UsedForTesting after this method is actually used.
     */
    @UsedForTesting
    public HttpUrlConnectionBuilder setConnectTimeout(int timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("connect-timeout must be >= 0, but was "
                    + timeoutMillis);
        }
        mConnectTimeoutMillis = timeoutMillis;
        return this;
    }

    /**
     * Sets the read timeout. Defaults to {@value #DEFAULT_TIMEOUT_MILLIS} milliseconds.
     *
     * TODO: Remove @UsedForTesting after this method is actually used.
     */
    @UsedForTesting
    public HttpUrlConnectionBuilder setReadTimeout(int timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("read-timeout must be >= 0, but was "
                    + timeoutMillis);
        }
        mReadTimeoutMillis = timeoutMillis;
        return this;
    }

    /**
     * Adds an entry to the request header.
     *
     * TODO: Remove @UsedForTesting after this method is actually used.
     */
    @UsedForTesting
    public HttpUrlConnectionBuilder addHeader(String key, String value) {
        mHeaderMap.put(key, value);
        return this;
    }

    /**
     * Sets an authentication token.
     *
     * TODO: Remove @UsedForTesting after this method is actually used.
     */
    @UsedForTesting
    public HttpUrlConnectionBuilder setAuthToken(String value) {
        mHeaderMap.put(HTTP_HEADER_AUTHORIZATION, value);
        return this;
    }

    /**
     * Sets the request to be executed such that the input is not buffered.
     * This may be set when the request size is known beforehand.
     *
     * TODO: Remove @UsedForTesting after this method is actually used.
     */
    @UsedForTesting
    public HttpUrlConnectionBuilder setFixedLengthForStreaming(int length) {
        mContentLength = length;
        return this;
    }

    /**
     * Indicates if the request can use cached responses or not.
     *
     * TODO: Remove @UsedForTesting after this method is actually used.
     */
    @UsedForTesting
    public HttpUrlConnectionBuilder setUseCache(boolean useCache) {
        mUseCache = useCache;
        return this;
    }

    /**
     * The request mode.
     * Sets the request mode to be one of: upload-only, download-only or bidirectional.
     *
     * @see #MODE_UPLOAD_ONLY
     * @see #MODE_DOWNLOAD_ONLY
     * @see #MODE_BI_DIRECTIONAL
     *
     * TODO: Remove @UsedForTesting after this method is actually used
     */
    @UsedForTesting
    public HttpUrlConnectionBuilder setMode(int mode) {
        if (mode != MODE_UPLOAD_ONLY
                && mode != MODE_DOWNLOAD_ONLY
                && mode != MODE_BI_DIRECTIONAL) {
            throw new IllegalArgumentException("Invalid mode specified:" + mode);
        }
        mMode = mode;
        return this;
    }

    /**
     * Builds the {@link HttpURLConnection} instance that can be used to execute the request.
     *
     * TODO: Remove @UsedForTesting after this method is actually used.
     */
    @UsedForTesting
    public HttpURLConnection build() throws IOException {
        if (mUrl == null) {
            throw new IllegalArgumentException("A URL must be specified!");
        }
        final HttpURLConnection connection = (HttpURLConnection) mUrl.openConnection();
        connection.setConnectTimeout(mConnectTimeoutMillis);
        connection.setReadTimeout(mReadTimeoutMillis);
        connection.setUseCaches(mUseCache);
        switch (mMode) {
            case MODE_UPLOAD_ONLY:
                connection.setDoInput(true);
                connection.setDoOutput(false);
                break;
            case MODE_DOWNLOAD_ONLY:
                connection.setDoInput(false);
                connection.setDoOutput(true);
                break;
            case MODE_BI_DIRECTIONAL:
                connection.setDoInput(true);
                connection.setDoOutput(true);
                break;
        }
        for (final Entry<String, String> entry : mHeaderMap.entrySet()) {
            connection.addRequestProperty(entry.getKey(), entry.getValue());
        }
        if (mContentLength >= 0) {
            connection.setFixedLengthStreamingMode(mContentLength);
        }
        return connection;
    }
}