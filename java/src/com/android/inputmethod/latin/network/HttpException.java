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

import com.android.inputmethod.annotations.UsedForTesting;

/**
 * The HttpException exception represents a XML/HTTP fault with a HTTP status code.
 */
public class HttpException extends Exception {

    /**
     * The HTTP status code.
     */
    private final int mStatusCode;

    /**
     * @param statusCode int HTTP status code.
     */
    public HttpException(int statusCode) {
        super("Response Code: " + statusCode);
        mStatusCode = statusCode;
    }

    /**
     * @return the HTTP status code related to this exception.
     */
    @UsedForTesting
    public int getHttpStatusCode() {
        return mStatusCode;
    }
}