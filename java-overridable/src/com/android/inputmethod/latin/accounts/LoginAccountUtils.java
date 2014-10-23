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

package com.android.inputmethod.latin.accounts;

import android.content.Context;

import javax.annotation.Nonnull;

/**
 * Utility class for retrieving accounts that may be used for login.
 */
public class LoginAccountUtils {
    /**
     * This defines the type of account this class deals with.
     * This account type is used when listing the accounts available on the device for login.
     */
    public static final String ACCOUNT_TYPE = "";

    private LoginAccountUtils() {
        // This utility class is not publicly instantiable.
    }

    /**
     * Get the accounts available for login.
     *
     * @return an array of accounts. Empty (never null) if no accounts are available for login.
     */
    @Nonnull
    @SuppressWarnings("unused")
    public static String[] getAccountsForLogin(final Context context) {
        return new String[0];
    }
}
