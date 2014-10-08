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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.TextUtils;
import android.widget.ListView;
import android.widget.Toast;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SubtypeSwitcher;
import com.android.inputmethod.latin.accounts.LoginAccountUtils;
import com.android.inputmethod.latin.define.ProductionFlags;
import com.android.inputmethod.latin.sync.BeanstalkManager;

import javax.annotation.Nullable;

/**
 * "Accounts & Privacy" settings sub screen.
 *
 * This settings sub screen handles the following preferences:
 * <li> Account selection/management for IME
 * <li> TODO: Sync preferences
 * <li> TODO: Privacy preferences
 * <li> Sync now
 */
public final class AccountsSettingsFragment extends SubScreenFragment {
    static final String PREF_ACCCOUNT_SWITCHER = "account_switcher";
    static final String PREF_SYNC_NOW = "pref_beanstalk";

    private final DialogInterface.OnClickListener mAccountSelectedListener =
            new AccountSelectedListener();
    private final DialogInterface.OnClickListener mAccountSignedOutListener =
            new AccountSignedOutListener();
    private final Preference.OnPreferenceClickListener mSyncNowListener = new SyncNowListener();

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_accounts);

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        SubtypeSwitcher.init(context);

        if (ProductionFlags.IS_METRICS_LOGGING_SUPPORTED) {
            final Preference enableMetricsLogging =
                    findPreference(Settings.PREF_ENABLE_METRICS_LOGGING);
            if (enableMetricsLogging != null) {
                final String enableMetricsLoggingTitle = res.getString(
                        R.string.enable_metrics_logging, getApplicationName());
                enableMetricsLogging.setTitle(enableMetricsLoggingTitle);
            }
        } else {
            removePreference(Settings.PREF_ENABLE_METRICS_LOGGING);
        }

        if (!ProductionFlags.ENABLE_PERSONAL_DICTIONARY_SYNC) {
            removePreference(PREF_SYNC_NOW);
        } else {
            final Preference syncNowPreference = findPreference(PREF_SYNC_NOW);
            if (syncNowPreference != null) {
                syncNowPreference.setOnPreferenceClickListener(mSyncNowListener);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        // TODO: Look at the preference that changed before refreshing the view.
        refreshUi();
    }

    private void refreshUi() {
        refreshAccountSelection();
        refreshSyncNow();
    }

    private void refreshAccountSelection() {
        if (!ProductionFlags.ENABLE_ACCOUNT_SIGN_IN) {
            return;
        }

        final String currentAccount = getCurrentlySelectedAccount();
        final Preference accountSwitcher = findPreference(PREF_ACCCOUNT_SWITCHER);
        if (currentAccount == null) {
            // No account is currently selected.
            accountSwitcher.setSummary(getString(R.string.no_accounts_selected));
        } else {
            // Set the currently selected account.
            accountSwitcher.setSummary(getString(R.string.account_selected, currentAccount));
        }
        final Context context = getActivity();
        final String[] accountsForLogin = LoginAccountUtils.getAccountsForLogin(context);
        accountSwitcher.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (accountsForLogin.length == 0) {
                    // TODO: Handle account addition.
                    Toast.makeText(getActivity(),
                            getString(R.string.account_select_cancel), Toast.LENGTH_SHORT).show();
                } else {
                    createAccountPicker(accountsForLogin, currentAccount).show();
                }
                return true;
            }
        });

        // TODO: Depending on the account selection, enable/disable preferences that
        // depend on an account.
    }

    /**
     * Refreshes the "Sync Now" feature
     */
    private void refreshSyncNow() {
        if (!ProductionFlags.ENABLE_PERSONAL_DICTIONARY_SYNC) {
            return;
        }

        final Preference syncNowPreference = findPreference(PREF_SYNC_NOW);
        if (syncNowPreference == null) {
            return;
        }

        final String currentAccount = getCurrentlySelectedAccount();
        if (currentAccount == null) {
            syncNowPreference.setEnabled(false);
            syncNowPreference.setSummary(R.string.sync_now_summary_disabled_signed_out);
        } else {
            syncNowPreference.setEnabled(true);
            syncNowPreference.setSummary(R.string.sync_now_summary);
        }
    }

    @Nullable
    private String getCurrentlySelectedAccount() {
        return getSharedPreferences().getString(Settings.PREF_ACCOUNT_NAME, null);
    }

    /**
     * Creates an account picker dialog showing the given accounts in a list and selecting
     * the selected account by default.
     * The list of accounts must not be null/empty.
     *
     * Package-private for testing.
     */
    AlertDialog createAccountPicker(final String[] accounts,
            final String selectedAccount) {
        if (accounts == null || accounts.length == 0) {
            throw new IllegalArgumentException("List of accounts must not be empty");
        }

        // See if the currently selected account is in the list.
        // If it is, the entry is selected, and a sign-out button is provided.
        // If it isn't, select the 0th account by default which will get picked up
        // if the user presses OK.
        int index = 0;
        boolean isSignedIn = false;
        for (int i = 0;  i < accounts.length; i++) {
            if (TextUtils.equals(accounts[i], selectedAccount)) {
                index = i;
                isSignedIn = true;
                break;
            }
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.account_select_title)
                .setSingleChoiceItems(accounts, index, null)
                .setPositiveButton(R.string.account_select_ok, mAccountSelectedListener)
                .setNegativeButton(R.string.account_select_cancel, null);
        if (isSignedIn) {
            builder.setNeutralButton(R.string.account_select_sign_out, mAccountSignedOutListener);
        }
        return builder.create();
    }

    /**
     * Listener for an account being selected from the picker.
     * Persists the account to shared preferences.
     */
    class AccountSelectedListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            final ListView lv = ((AlertDialog)dialog).getListView();
            final Object selectedItem = lv.getItemAtPosition(lv.getCheckedItemPosition());
            getSharedPreferences()
                    .edit()
                    .putString(Settings.PREF_ACCOUNT_NAME, (String) selectedItem)
                    .apply();
        }
    }

    /**
     * Listener for sign-out being initiated from from the picker.
     * Removed the account from shared preferences.
     */
    class AccountSignedOutListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            getSharedPreferences()
                    .edit()
                    .remove(Settings.PREF_ACCOUNT_NAME)
                    .apply();
        }
    }

    /**
     * Listener that initates the process of sync in the background.
     */
    class SyncNowListener implements Preference.OnPreferenceClickListener {
        @Override
        public boolean onPreferenceClick(final Preference preference) {
            BeanstalkManager.getInstance(getActivity() /* context */).requestSync();
            return true;
        }
    }
}
