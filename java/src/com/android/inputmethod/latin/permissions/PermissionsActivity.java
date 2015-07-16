/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.inputmethod.latin.permissions;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

/**
 * An activity to help request permissions. It's used when no other activity is available, e.g. in
 * InputMethodService. This activity assumes that all permissions are not granted yet.
 */
public final class PermissionsActivity
        extends Activity implements ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * Key to retrieve requested permissions from the intent.
     */
    public static final String EXTRA_PERMISSION_REQUESTED_PERMISSIONS = "requested_permissions";

    /**
     * Key to retrieve request code from the intent.
     */
    public static final String EXTRA_PERMISSION_REQUEST_CODE = "request_code";

    private static final int INVALID_REQUEST_CODE = -1;

    private int mPendingRequestCode = INVALID_REQUEST_CODE;

    /**
     * Starts a PermissionsActivity and checks/requests supplied permissions.
     */
    public static void run(
            @NonNull Context context, int requestCode, @NonNull String... permissionStrings) {
        Intent intent = new Intent(context.getApplicationContext(), PermissionsActivity.class);
        intent.putExtra(EXTRA_PERMISSION_REQUESTED_PERMISSIONS, permissionStrings);
        intent.putExtra(EXTRA_PERMISSION_REQUEST_CODE, requestCode);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPendingRequestCode = (savedInstanceState != null)
                ? savedInstanceState.getInt(EXTRA_PERMISSION_REQUEST_CODE, INVALID_REQUEST_CODE)
                : INVALID_REQUEST_CODE;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_PERMISSION_REQUEST_CODE, mPendingRequestCode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only do request when there is no pending request to avoid duplicated requests.
        if (mPendingRequestCode == INVALID_REQUEST_CODE) {
            final Bundle extras = getIntent().getExtras();
            final String[] permissionsToRequest =
                    extras.getStringArray(EXTRA_PERMISSION_REQUESTED_PERMISSIONS);
            mPendingRequestCode = extras.getInt(EXTRA_PERMISSION_REQUEST_CODE);
            // Assuming that all supplied permissions are not granted yet, so that we don't need to
            // check them again.
            PermissionsUtil.requestPermissions(this, mPendingRequestCode, permissionsToRequest);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mPendingRequestCode = INVALID_REQUEST_CODE;
        PermissionsManager.get(this).onRequestPermissionsResult(
                requestCode, permissions, grantResults);
        finish();
    }
}
