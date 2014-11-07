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

import static com.android.inputmethod.latin.settings.LocalSettingsConstants.PREF_ACCOUNT_NAME;
import static com.android.inputmethod.latin.settings.LocalSettingsConstants.PREF_ENABLE_CLOUD_SYNC;

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

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.accounts.AccountStateChangedListener;
import com.android.inputmethod.latin.accounts.LoginAccountUtils;
import com.android.inputmethod.latin.define.ProductionFlags;

import javax.annotation.Nullable;

/**
 * "Accounts & Privacy" settings sub screen.
 *
 * This settings sub screen handles the following preferences:
 * <li> Account selection/management for IME </li>
 * <li> Sync preferences </li>
 * <li> Privacy preferences </li>
 */
public final class AccountsSettingsFragment extends SubScreenFragment {
    private static final String PREF_SYNC_NOW = "pref_beanstalk";

    static final String PREF_ACCCOUNT_SWITCHER = "account_switcher";

    private final DialogInterface.OnClickListener mAccountChangedListener =
            new AccountChangedListener();
    private final Preference.OnPreferenceClickListener mSyncNowListener = new SyncNowListener();

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_accounts);

        final Resources res = getResources();

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

        if (!ProductionFlags.ENABLE_ACCOUNT_SIGN_IN) {
            removePreference(PREF_ACCCOUNT_SWITCHER);
            removePreference(PREF_ENABLE_CLOUD_SYNC);
            removePreference(PREF_SYNC_NOW);
        }
        if (!ProductionFlags.ENABLE_PERSONAL_DICTIONARY_SYNC) {
            removePreference(PREF_ENABLE_CLOUD_SYNC);
            removePreference(PREF_SYNC_NOW);
        } else {
            final Preference syncNowPreference = findPreference(PREF_SYNC_NOW);
            syncNowPreference.setOnPreferenceClickListener(mSyncNowListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAccountAndDependentPreferences(getSignedInAccountName());
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (TextUtils.equals(key, PREF_ACCOUNT_NAME)) {
            refreshAccountAndDependentPreferences(
                    prefs.getString(PREF_ACCOUNT_NAME, null));
        } else if (TextUtils.equals(key, PREF_ENABLE_CLOUD_SYNC)) {
            final boolean syncEnabled = prefs.getBoolean(PREF_ENABLE_CLOUD_SYNC, false);
            AccountStateChangedListener.onSyncPreferenceChanged(
                    getSignedInAccountName(), syncEnabled);
        }
    }

    private void refreshAccountAndDependentPreferences(@Nullable final String currentAccount) {
        if (!ProductionFlags.ENABLE_ACCOUNT_SIGN_IN) {
            return;
        }

        final Preference accountSwitcher = findPreference(PREF_ACCCOUNT_SWITCHER);
        if (currentAccount == null) {
            // No account is currently selected.
            accountSwitcher.setSummary(getString(R.string.no_accounts_selected));
            // Disable the sync preference UI.
            disableSyncPreference();
        } else {
            // Set the currently selected account.
            accountSwitcher.setSummary(getString(R.string.account_selected, currentAccount));
            // Enable the sync preference UI.
            enableSyncPreference();
        }
        // Set up onClick listener for the account picker preference.
        final Context context = getActivity();
        final String[] accountsForLogin = LoginAccountUtils.getAccountsForLogin(context);
        accountSwitcher.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (accountsForLogin.length == 0) {
                    // TODO: Handle account addition.
                    Toast.makeText(getActivity(), getString(R.string.account_select_cancel),
                            Toast.LENGTH_SHORT).show();
                } else {
                    createAccountPicker(accountsForLogin, currentAccount).show();
                }
                return true;
            }
        });
    }

    /**
     * Enables the Sync preference UI and updates its summary.
     */
    private void enableSyncPreference() {
        if (!ProductionFlags.ENABLE_PERSONAL_DICTIONARY_SYNC) {
            return;
        }

        final Preference syncPreference = findPreference(PREF_ENABLE_CLOUD_SYNC);
        syncPreference.setEnabled(true);
        syncPreference.setSummary(R.string.cloud_sync_summary);
    }

    /**
     * Disables the Sync preference UI and updates its summary to indicate
     * the fact that an account needs to be selected for sync.
     */
    private void disableSyncPreference() {
        if (!ProductionFlags.ENABLE_PERSONAL_DICTIONARY_SYNC) {
            return;
        }

        final Preference syncPreference = findPreference(PREF_ENABLE_CLOUD_SYNC);
        syncPreference.setEnabled(false);
        syncPreference.setSummary(R.string.cloud_sync_summary_disabled_signed_out);
    }

    @Nullable
    String getSignedInAccountName() {
        return getSharedPreferences().getString(LocalSettingsConstants.PREF_ACCOUNT_NAME, null);
    }

    boolean isSyncEnabled() {
        return getSharedPreferences().getBoolean(PREF_ENABLE_CLOUD_SYNC, false);
    }

    /**
     * Creates an account picker dialog showing the given accounts in a list and selecting
     * the selected account by default.
     * The list of accounts must not be null/empty.
     *
     * Package-private for testing.
     */
    @UsedForTesting
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
                .setPositiveButton(R.string.account_select_ok, mAccountChangedListener)
                .setNegativeButton(R.string.account_select_cancel, null);
        if (isSignedIn) {
            builder.setNeutralButton(R.string.account_select_sign_out, mAccountChangedListener);
        }
        return builder.create();
    }

    /**
     * Listener for a account selection changes from the picker.
     * Persists/removes the account to/from shared preferences and sets up sync if required.
     */
    class AccountChangedListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            final String oldAccount = getSignedInAccountName();
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE: // Signed in
                    final ListView lv = ((AlertDialog)dialog).getListView();
                    final String newAccount =
                            (String) lv.getItemAtPosition(lv.getCheckedItemPosition());
                    getSharedPreferences()
                            .edit()
                            .putString(PREF_ACCOUNT_NAME, newAccount)
                            .apply();
                    AccountStateChangedListener.onAccountSignedIn(oldAccount, newAccount);
                    break;
                case DialogInterface.BUTTON_NEUTRAL: // Signed out
                    AccountStateChangedListener.onAccountSignedOut(oldAccount);
                    getSharedPreferences()
                            .edit()
                            .remove(PREF_ACCOUNT_NAME)
                            .apply();
                    break;
            }
        }
    }

    /**
     * Listener that initiates the process of sync in the background.
     */
    class SyncNowListener implements Preference.OnPreferenceClickListener {
        @Override
        public boolean onPreferenceClick(final Preference preference) {
            AccountStateChangedListener.forceSync(getSignedInAccountName());
            return true;
        }
    }
}
