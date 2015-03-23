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
import android.content.DialogInterface.OnShowListener;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.TwoStatePreference;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.widget.ListView;
import android.widget.TextView;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.accounts.AccountStateChangedListener;
import com.android.inputmethod.latin.accounts.LoginAccountUtils;
import com.android.inputmethod.latin.define.ProductionFlags;
import com.android.inputmethod.latin.utils.ManagedProfileUtils;

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
    private static final String PREF_ENABLE_SYNC_NOW = "pref_enable_cloud_sync";
    private static final String PREF_SYNC_NOW = "pref_sync_now";
    private static final String PREF_CLEAR_SYNC_DATA = "pref_clear_sync_data";

    static final String PREF_ACCCOUNT_SWITCHER = "account_switcher";

    /**
     * Onclick listener for sync now pref.
     */
    private final Preference.OnPreferenceClickListener mSyncNowListener =
            new SyncNowListener();
    /**
     * Onclick listener for delete sync pref.
     */
    private final Preference.OnPreferenceClickListener mDeleteSyncDataListener =
            new DeleteSyncDataListener();

    /**
     * Onclick listener for enable sync pref.
     */
    private final Preference.OnPreferenceClickListener mEnableSyncClickListener =
            new EnableSyncClickListener();

    /**
     * Enable sync checkbox pref.
     */
    private TwoStatePreference mEnableSyncPreference;

    /**
     * Enable sync checkbox pref.
     */
    private Preference mSyncNowPreference;

    /**
     * Clear sync data pref.
     */
    private Preference mClearSyncDataPreference;

    /**
     * Account switcher preference.
     */
    private Preference mAccountSwitcher;


    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_accounts);

        if (ProductionFlags.IS_METRICS_LOGGING_SUPPORTED) {
            final Preference enableMetricsLogging =
                    findPreference(Settings.PREF_ENABLE_METRICS_LOGGING);
            final Resources res = getResources();
            if (enableMetricsLogging != null) {
                final String enableMetricsLoggingTitle = res.getString(
                        R.string.enable_metrics_logging, getApplicationName());
                enableMetricsLogging.setTitle(enableMetricsLoggingTitle);
            }
        } else {
            removePreference(Settings.PREF_ENABLE_METRICS_LOGGING);
        }

        if (!ProductionFlags.ENABLE_USER_HISTORY_DICTIONARY_SYNC) {
            removeSyncPreferences();
        } else {
            disableSyncPreferences();
            new ManagedProfileCheckerTask(this).execute();
        }
    }

    private static class ManagedProfileCheckerTask extends AsyncTask<Void, Void, Void> {
        private final AccountsSettingsFragment mFragment;

        private ManagedProfileCheckerTask(final AccountsSettingsFragment fragment) {
            mFragment = fragment;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (ManagedProfileUtils.getInstance().hasWorkProfile(mFragment.getActivity())) {
                mFragment.removeSyncPreferences();
            } else {
                mFragment.enableSyncPreferences();
            }
            return null;
        }
    }

    private void enableSyncPreferences() {
        mAccountSwitcher = findPreference(PREF_ACCCOUNT_SWITCHER);
        if (mAccountSwitcher == null) {
            // Preference has been removed because the device has a managed profile.
            return;
        }
        mAccountSwitcher.setEnabled(true);

        mEnableSyncPreference = (TwoStatePreference) findPreference(PREF_ENABLE_SYNC_NOW);
        mEnableSyncPreference.setEnabled(true);
        mEnableSyncPreference.setOnPreferenceClickListener(mEnableSyncClickListener);

        mSyncNowPreference = findPreference(PREF_SYNC_NOW);
        mSyncNowPreference.setEnabled(true);
        mSyncNowPreference.setOnPreferenceClickListener(mSyncNowListener);

        mClearSyncDataPreference = findPreference(PREF_CLEAR_SYNC_DATA);
        mSyncNowPreference.setEnabled(true);
        mClearSyncDataPreference.setOnPreferenceClickListener(mDeleteSyncDataListener);
    }

    private void disableSyncPreferences() {
        mAccountSwitcher = findPreference(PREF_ACCCOUNT_SWITCHER);
        if (mAccountSwitcher == null) {
            // Preference has been removed because the device has a managed profile.
            return;
        }
        mAccountSwitcher.setEnabled(false);

        mEnableSyncPreference = (TwoStatePreference) findPreference(PREF_ENABLE_SYNC_NOW);
        mEnableSyncPreference.setEnabled(false);

        mSyncNowPreference = findPreference(PREF_SYNC_NOW);
        mSyncNowPreference.setEnabled(false);

        mClearSyncDataPreference = findPreference(PREF_CLEAR_SYNC_DATA);
        mSyncNowPreference.setEnabled(false);
    }

    private void removeSyncPreferences() {
        removePreference(PREF_ACCCOUNT_SWITCHER);
        removePreference(PREF_ENABLE_CLOUD_SYNC);
        removePreference(PREF_SYNC_NOW);
        removePreference(PREF_CLEAR_SYNC_DATA);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAccountAndDependentPreferences(getSignedInAccountName());
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (TextUtils.equals(key, PREF_ACCOUNT_NAME)) {
            refreshAccountAndDependentPreferences(prefs.getString(PREF_ACCOUNT_NAME, null));
        } else if (TextUtils.equals(key, PREF_ENABLE_CLOUD_SYNC)) {
            final boolean syncEnabled = prefs.getBoolean(PREF_ENABLE_CLOUD_SYNC, false);
            mEnableSyncPreference = (TwoStatePreference) findPreference(PREF_ENABLE_SYNC_NOW);
            if (syncEnabled) {
                mEnableSyncPreference.setSummary(R.string.cloud_sync_summary);
            } else {
                mEnableSyncPreference.setSummary(R.string.cloud_sync_summary_disabled);
            }
            AccountStateChangedListener.onSyncPreferenceChanged(getSignedInAccountName(),
                    syncEnabled);
        }
    }

    /**
     * Summarizes what account is being used and turns off dependent preferences if no account
     * is currently selected.
     */
    private void refreshAccountAndDependentPreferences(@Nullable final String currentAccount) {
        // TODO(cvnguyen): Write tests.
        if (!ProductionFlags.ENABLE_ACCOUNT_SIGN_IN) {
            return;
        }

        final String[] accountsForLogin =
                LoginAccountUtils.getAccountsForLogin(getActivity());

        if (accountsForLogin.length > 0) {
            enableSyncPreferences();
            if (mAccountSwitcher == null) {
                return;
            }
            mAccountSwitcher.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    if (accountsForLogin.length > 0) {
                        // TODO: Add addition of account.
                        createAccountPicker(accountsForLogin, currentAccount,
                                new AccountChangedListener(null)).show();
                    }
                    return true;
                }
            });
        } else {
            mAccountSwitcher.setEnabled(false);
            disableSyncPreferences();
            mEnableSyncPreference.setSummary(getString(R.string.add_account_to_enable_sync));
        }

        if (currentAccount == null) {
            // No account is currently selected; switch enable sync preference off.
            mAccountSwitcher.setSummary(getString(R.string.no_accounts_selected));
            mEnableSyncPreference.setChecked(false);
        } else {
            // Set the currently selected account as the summary text.
            mAccountSwitcher.setSummary(getString(R.string.account_selected, currentAccount));
        }
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
     * the selected account by default.  The list of accounts must not be null/empty.
     *
     * Package-private for testing.
     *
     * @param accounts list of accounts on the device.
     * @param selectedAccount currently selected account
     * @param positiveButtonClickListener listener that gets called when positive button is
     * clicked
     */
    @UsedForTesting
    AlertDialog createAccountPicker(final String[] accounts,
            final String selectedAccount,
            final DialogInterface.OnClickListener positiveButtonClickListener) {
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
                .setPositiveButton(R.string.account_select_ok, positiveButtonClickListener)
                .setNegativeButton(R.string.account_select_cancel, null);
        if (isSignedIn) {
            builder.setNeutralButton(R.string.account_select_sign_out, positiveButtonClickListener);
        }
        return builder.create();
    }

    /**
     * Listener for a account selection changes from the picker.
     * Persists/removes the account to/from shared preferences and sets up sync if required.
     */
    class AccountChangedListener implements DialogInterface.OnClickListener {
        /**
         * Represents preference that should be changed based on account chosen.
         */
        private TwoStatePreference mDependentPreference;

        AccountChangedListener(final TwoStatePreference dependentPreference) {
            mDependentPreference = dependentPreference;
        }

        @Override
        public void onClick(final DialogInterface dialog, final int which) {
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
                    if (mDependentPreference != null) {
                        mDependentPreference.setChecked(true);
                    }
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

    /**
     * Listener that initiates the process of deleting user's data from the cloud.
     */
    class DeleteSyncDataListener implements Preference.OnPreferenceClickListener {
        @Override
        public boolean onPreferenceClick(final Preference preference) {
            final AlertDialog confirmationDialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.clear_sync_data_title)
                    .setMessage(R.string.clear_sync_data_confirmation)
                    .setPositiveButton(R.string.clear_sync_data_ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialog, final int which) {
                                    if (which == DialogInterface.BUTTON_POSITIVE) {
                                        AccountStateChangedListener.forceDelete(
                                                getSignedInAccountName());
                                    }
                                }
                             })
                    .setNegativeButton(R.string.cloud_sync_cancel, null /* OnClickListener */)
                    .create();
            confirmationDialog.show();
            return true;
        }
    }

    /**
     * Listens to events when user clicks on "Enable sync" feature.
     */
    class EnableSyncClickListener implements OnShowListener, Preference.OnPreferenceClickListener {
        // TODO(cvnguyen): Write tests.
        @Override
        public boolean onPreferenceClick(final Preference preference) {
            final TwoStatePreference syncPreference = (TwoStatePreference) preference;
            if (syncPreference.isChecked()) {
                // Uncheck for now.
                syncPreference.setChecked(false);

                // Show opt-in.
                final AlertDialog optInDialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.cloud_sync_title)
                        .setMessage(R.string.cloud_sync_opt_in_text)
                        .setPositiveButton(R.string.account_select_ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(final DialogInterface dialog,
                                            final int which) {
                                        if (which == DialogInterface.BUTTON_POSITIVE) {
                                            final Context context = getActivity();
                                            final String[] accountsForLogin =
                                                    LoginAccountUtils.getAccountsForLogin(context);
                                            createAccountPicker(accountsForLogin,
                                                    getSignedInAccountName(),
                                                    new AccountChangedListener(syncPreference))
                                                    .show();
                                        }
                                    }
                         })
                         .setNegativeButton(R.string.cloud_sync_cancel, null)
                         .create();
                optInDialog.setOnShowListener(this);
                optInDialog.show();
            }
            return true;
        }

        @Override
        public void onShow(DialogInterface dialog) {
            TextView messageView = (TextView) ((AlertDialog) dialog).findViewById(
                    android.R.id.message);
            if (messageView != null) {
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }
}
