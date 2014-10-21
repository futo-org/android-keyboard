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

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A client for executing HTTP requests synchronously.
 * This must never be called from the main thread.
 */
public class BlockingHttpClient {
    private static final boolean DEBUG = false;
    private static final String TAG = BlockingHttpClient.class.getSimpleName();

    private final HttpURLConnection mConnection;

    /**
     * Interface that handles processing the response for a request.
     */
    public interface ResponseProcessor<T> {
        /**
         * Called when the HTTP request finishes successfully.
         * The {@link InputStream} is closed by the client after the method finishes,
         * so any processing must be done in this method itself.
         *
         * @param response An input stream that can be used to read the HTTP response.
         */
         T onSuccess(InputStream response) throws IOException;
    }

    public BlockingHttpClient(HttpURLConnection connection) {
        mConnection = connection;
    }

    /**
     * Executes the request on the underlying {@link HttpURLConnection}.
     *
     * @param request The request payload, if any, or null.
     * @param responseProcessor A processor for the HTTP response.
     */
    public <T> T execute(@Nullable byte[] request, @Nonnull ResponseProcessor<T> responseProcessor)
            throws IOException, AuthException, HttpException {
        if (DEBUG) {
            Log.d(TAG, "execute: " + mConnection.getURL());
        }
        try {
            if (request != null) {
                if (DEBUG) {
                    Log.d(TAG, "request size: " + request.length);
                }
                OutputStream out = new BufferedOutputStream(mConnection.getOutputStream());
                out.write(request);
                out.flush();
                out.close();
            }

            final int responseCode = mConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Response error: " +  responseCode + ", Message: "
                        + mConnection.getResponseMessage());
                if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new AuthException(mConnection.getResponseMessage());
                }
                throw new HttpException(responseCode);
            }
            if (DEBUG) {
                Log.d(TAG, "request executed successfully");
            }
            return responseProcessor.onSuccess(mConnection.getInputStream());
        } finally {
            mConnection.disconnect();
        }
    }
}
