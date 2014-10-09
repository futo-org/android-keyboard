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

package com.android.inputmethod.latin.settings;

import static com.android.inputmethod.latin.settings.AccountsSettingsFragment.AUTHORITY;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.widget.ListView;

import com.android.inputmethod.latin.define.ProductionFlags;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
public class AccountsSettingsFragmentTests
        extends ActivityInstrumentationTestCase2<TestFragmentActivity> {
    private static final String FRAG_NAME = AccountsSettingsFragment.class.getName();
    private static final long TEST_TIMEOUT_MILLIS = 5000;
    private static final Account TEST_ACCOUNT = new Account("account-for-test", "account-type");

    private AlertDialog mDialog;

    public AccountsSettingsFragmentTests() {
        super(TestFragmentActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Intent intent = new Intent();
        intent.putExtra(TestFragmentActivity.EXTRA_SHOW_FRAGMENT, FRAG_NAME);
        setActivityIntent(intent);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // reset the syncable state to unknown
        ContentResolver.setIsSyncable(TEST_ACCOUNT, AUTHORITY, -1);
    }

    public void testEmptyAccounts() {
        final AccountsSettingsFragment fragment =
                (AccountsSettingsFragment) getActivity().mFragment;
        try {
            fragment.createAccountPicker(new String[0], null);
            fail("Expected IllegalArgumentException, never thrown");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    public void testMultipleAccounts_noCurrentAccount() {
        final AccountsSettingsFragment fragment =
                (AccountsSettingsFragment) getActivity().mFragment;
        final CountDownLatch latch = new CountDownLatch(1);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDialog = fragment.createAccountPicker(
                        new String[] {
                                "1@example.com",
                                "2@example.com",
                                "3@example.com",
                                "4@example.com"},
                        null);
                mDialog.show();
                latch.countDown();
            }
        });

        try {
            latch.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail();
        }
        getInstrumentation().waitForIdleSync();
        final ListView lv = mDialog.getListView();
        // The 1st account should be checked by default.
        assertEquals("checked-item", 0, lv.getCheckedItemPosition());
        // There should be 4 accounts in the list.
        assertEquals("count", 4, lv.getCount());
        // The sign-out button shouldn't exist
        assertEquals(View.GONE, mDialog.getButton(Dialog.BUTTON_NEUTRAL).getVisibility());
        assertEquals(View.VISIBLE, mDialog.getButton(Dialog.BUTTON_NEGATIVE).getVisibility());
        assertEquals(View.VISIBLE, mDialog.getButton(Dialog.BUTTON_POSITIVE).getVisibility());
    }

    public void testMultipleAccounts_currentAccount() {
        final AccountsSettingsFragment fragment =
                (AccountsSettingsFragment) getActivity().mFragment;
        final CountDownLatch latch = new CountDownLatch(1);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDialog = fragment.createAccountPicker(
                        new String[] {
                                "1@example.com",
                                "2@example.com",
                                "3@example.com",
                                "4@example.com"},
                        "3@example.com");
                mDialog.show();
                latch.countDown();
            }
        });

        try {
            latch.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail();
        }
        getInstrumentation().waitForIdleSync();
        final ListView lv = mDialog.getListView();
        // The 3rd account should be checked by default.
        assertEquals("checked-item", 2, lv.getCheckedItemPosition());
        // There should be 4 accounts in the list.
        assertEquals("count", 4, lv.getCount());
        // The sign-out button should be shown
        assertEquals(View.VISIBLE, mDialog.getButton(Dialog.BUTTON_NEUTRAL).getVisibility());
        assertEquals(View.VISIBLE, mDialog.getButton(Dialog.BUTTON_NEGATIVE).getVisibility());
        assertEquals(View.VISIBLE, mDialog.getButton(Dialog.BUTTON_POSITIVE).getVisibility());
    }

    public void testUpdateSyncPolicy_enable() {
        // This test is a no-op when ENABLE_PERSONAL_DICTIONARY_SYNC is not enabled
        if (!ProductionFlags.ENABLE_PERSONAL_DICTIONARY_SYNC) {
            return;
        }
        // Should be unknown by default.
        assertTrue(ContentResolver.getIsSyncable(TEST_ACCOUNT, AUTHORITY) < 0);

        final AccountsSettingsFragment fragment =
                (AccountsSettingsFragment) getActivity().mFragment;
        fragment.updateSyncPolicy(true, TEST_ACCOUNT);

        // Should be syncable now.
        assertEquals(1, ContentResolver.getIsSyncable(TEST_ACCOUNT, AUTHORITY));
    }

    public void testUpdateSyncPolicy_disable() {
        // This test is a no-op when ENABLE_PERSONAL_DICTIONARY_SYNC is not enabled
        if (!ProductionFlags.ENABLE_PERSONAL_DICTIONARY_SYNC) {
            return;
        }
        // Should be unknown by default.
        assertTrue(ContentResolver.getIsSyncable(TEST_ACCOUNT, AUTHORITY) < 0);

        final AccountsSettingsFragment fragment =
                (AccountsSettingsFragment) getActivity().mFragment;
        fragment.updateSyncPolicy(false, TEST_ACCOUNT);

        // Should not be syncable now.
        assertEquals(0, ContentResolver.getIsSyncable(TEST_ACCOUNT, AUTHORITY));
    }

    public void testUpdateSyncPolicy_enableDisable() {
        // This test is a no-op when ENABLE_PERSONAL_DICTIONARY_SYNC is not enabled
        if (!ProductionFlags.ENABLE_PERSONAL_DICTIONARY_SYNC) {
            return;
        }
        // Should be unknown by default.
        assertTrue(ContentResolver.getIsSyncable(TEST_ACCOUNT, AUTHORITY) < 0);

        final AccountsSettingsFragment fragment =
                (AccountsSettingsFragment) getActivity().mFragment;
        fragment.updateSyncPolicy(true, TEST_ACCOUNT);

        // Should be syncable now.
        assertEquals(1, ContentResolver.getIsSyncable(TEST_ACCOUNT, AUTHORITY));

        fragment.updateSyncPolicy(false, TEST_ACCOUNT);

        // Should not be syncable now.
        assertEquals(0, ContentResolver.getIsSyncable(TEST_ACCOUNT, AUTHORITY));
    }
}
