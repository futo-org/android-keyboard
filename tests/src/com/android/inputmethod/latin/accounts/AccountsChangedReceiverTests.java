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

import static org.junit.Assert.assertEquals;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.settings.LocalSettingsConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link AccountsChangedReceiver}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AccountsChangedReceiverTests {
    private static final String ACCOUNT_1 = "account1@example.com";
    private static final String ACCOUNT_2 = "account2@example.com";

    private SharedPreferences mPrefs;
    private String mLastKnownAccount = null;

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Before
    public void setUp() throws Exception {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        // Keep track of the current account so that we restore it when the test finishes.
        mLastKnownAccount = mPrefs.getString(LocalSettingsConstants.PREF_ACCOUNT_NAME, null);
    }

    @After
    public void tearDown() throws Exception {
        // Restore the account that was present before running the test.
        updateAccountName(mLastKnownAccount);
    }

    @Test
    public void testUnknownIntent() {
        updateAccountName(ACCOUNT_1);
        AccountsChangedReceiver reciever = new AccountsChangedReceiver();
        reciever.onReceive(getContext(), new Intent("some-random-action"));
        // Account should *not* be removed from preferences.
        assertAccountName(ACCOUNT_1);
    }

    @Test
    public void testAccountRemoved() {
        updateAccountName(ACCOUNT_1);
        AccountsChangedReceiver reciever = new AccountsChangedReceiver() {
            @Override
            protected String[] getAccountsForLogin(Context context) {
                return new String[] {ACCOUNT_2};
            }
        };
        reciever.onReceive(getContext(), new Intent(AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION));
        // Account should be removed from preferences.
        assertAccountName(null);
    }

    @Test
    public void testAccountRemoved_noAccounts() {
        updateAccountName(ACCOUNT_2);
        AccountsChangedReceiver reciever = new AccountsChangedReceiver() {
            @Override
            protected String[] getAccountsForLogin(Context context) {
                return new String[0];
            }
        };
        reciever.onReceive(getContext(), new Intent(AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION));
        // Account should be removed from preferences.
        assertAccountName(null);
    }

    @Test
    public void testAccountNotRemoved() {
        updateAccountName(ACCOUNT_2);
        AccountsChangedReceiver reciever = new AccountsChangedReceiver() {
            @Override
            protected String[] getAccountsForLogin(Context context) {
                return new String[] {ACCOUNT_1, ACCOUNT_2};
            }
        };
        reciever.onReceive(getContext(), new Intent(AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION));
        // Account should *not* be removed from preferences.
        assertAccountName(ACCOUNT_2);
    }

    private void updateAccountName(String accountName) {
        if (accountName == null) {
            mPrefs.edit().remove(LocalSettingsConstants.PREF_ACCOUNT_NAME).apply();
        } else {
            mPrefs.edit().putString(LocalSettingsConstants.PREF_ACCOUNT_NAME, accountName).apply();
        }
    }

    private void assertAccountName(String expectedAccountName) {
        assertEquals(expectedAccountName,
                mPrefs.getString(LocalSettingsConstants.PREF_ACCOUNT_NAME, null));
    }
}
