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

import android.support.annotation.NonNull;

import javax.annotation.Nullable;

/**
 * Handles changes to account used to sign in to the keyboard.
 * e.g. account switching/sign-in/sign-out from the keyboard
 * user toggling the sync preference.
 */
public class AccountStateChangedListener {

    /**
     * Called when the current account being used in keyboard is signed out.
     *
     * @param oldAccount the account that was signed out of.
     */
    public static void onAccountSignedOut(@NonNull String oldAccount) {
    }

    /**
     * Called when the user signs-in to the keyboard.
     * This may be called when the user switches accounts to sign in with a different account.
     *
     * @param oldAccount the previous account that was being used for sign-in.
     *        May be null for a fresh sign-in.
     * @param newAccount the account being used for sign-in.
     */
    public static void onAccountSignedIn(@Nullable String oldAccount, @NonNull String newAccount) {
    }

    /**
     * Called when the user toggles the sync preference.
     *
     * @param account the account being used for sync.
     * @param syncEnabled indicates whether sync has been enabled or not.
     */
    public static void onSyncPreferenceChanged(@Nullable String account, boolean syncEnabled) {
    }

    /**
     * Forces an immediate sync to happen.
     * This should only be used for debugging purposes.
     *
     * @param account the account to use for sync.
     */
    public static void forceSync(@Nullable String account) {
    }

    /**
     * Forces an immediate deletion of user's data.
     * This should only be used for debugging purposes.
     *
     * @param account the account to use for sync.
     */
    public static void forceDelete(@Nullable String account) {
    }
}
