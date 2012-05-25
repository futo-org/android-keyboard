/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;

/**
 * Takes action to reload the necessary data when a dictionary pack was added/removed.
 */
public class DictionaryPackInstallBroadcastReceiver extends BroadcastReceiver {

    final LatinIME mService;
    /**
     * The action of the intent for publishing that new dictionary data is available.
     */
    /* package */ static final String NEW_DICTIONARY_INTENT_ACTION =
            "com.android.inputmethod.latin.dictionarypack.newdict";

    public DictionaryPackInstallBroadcastReceiver(final LatinIME service) {
        mService = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final PackageManager manager = context.getPackageManager();

        // We need to reread the dictionary if a new dictionary package is installed.
        if (action.equals(Intent.ACTION_PACKAGE_ADDED)) {
            final Uri packageUri = intent.getData();
            if (null == packageUri) return; // No package name : we can't do anything
            final String packageName = packageUri.getSchemeSpecificPart();
            if (null == packageName) return;
            // TODO: do this in a more appropriate place
            TargetApplicationGetter.removeApplicationInfoCache(packageName);
            final PackageInfo packageInfo;
            try {
                packageInfo = manager.getPackageInfo(packageName, PackageManager.GET_PROVIDERS);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return; // No package info : we can't do anything
            }
            final ProviderInfo[] providers = packageInfo.providers;
            if (null == providers) return; // No providers : it is not a dictionary.

            // Search for some dictionary pack in the just-installed package. If found, reread.
            for (ProviderInfo info : providers) {
                if (BinaryDictionary.DICTIONARY_PACK_AUTHORITY.equals(info.authority)) {
                    mService.resetSuggestMainDict();
                    return;
                }
            }
            // If we come here none of the authorities matched the one we searched for.
            // We can exit safely.
            return;
        } else if (action.equals(Intent.ACTION_PACKAGE_REMOVED)
                && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            // When the dictionary package is removed, we need to reread dictionary (to use the
            // next-priority one, or stop using a dictionary at all if this was the only one,
            // since this is the user request).
            // If we are replacing the package, we will receive ADDED right away so no need to
            // remove the dictionary at the moment, since we will do it when we receive the
            // ADDED broadcast.

            // TODO: Only reload dictionary on REMOVED when the removed package is the one we
            // read dictionary from?
            mService.resetSuggestMainDict();
        } else if (action.equals(NEW_DICTIONARY_INTENT_ACTION)) {
            mService.resetSuggestMainDict();
        }
    }
}
