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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;

import java.io.IOException;

/**
 * Utility class that handles generation/invalidation of auth tokens in the app.
 */
public class AuthUtils {
    private final AccountManager mAccountManager;

    public AuthUtils(Context context) {
        mAccountManager = AccountManager.get(context);
    }

    /**
     * @see AccountManager#invalidateAuthToken(String, String)
     */
    public void invalidateAuthToken(final String accountType, final String authToken) {
        mAccountManager.invalidateAuthToken(accountType, authToken);
    }

    /**
     * @see AccountManager#getAuthToken(
     *              Account, String, Bundle, boolean, AccountManagerCallback, Handler)
     */
    public AccountManagerFuture<Bundle> getAuthToken(final Account account,
            final String authTokenType, final Bundle options, final boolean notifyAuthFailure,
            final AccountManagerCallback<Bundle> callback, final Handler handler) {
        return mAccountManager.getAuthToken(account, authTokenType, options, notifyAuthFailure,
                callback, handler);
    }

    /**
     * @see AccountManager#blockingGetAuthToken(Account, String, boolean)
     */
    public String blockingGetAuthToken(final Account account, final String authTokenType,
            final boolean notifyAuthFailure) throws OperationCanceledException,
            AuthenticatorException, IOException {
        return mAccountManager.blockingGetAuthToken(account, authTokenType, notifyAuthFailure);
    }
}
