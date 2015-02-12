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

package com.android.inputmethod.latin;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.SystemClock;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.latin.ContactsManager.ContactsChangedListener;
import com.android.inputmethod.latin.utils.ExecutorUtils;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

/**
 * A content observer that listens to updates to content provider {@link Contacts.CONTENT_URI}.
 */
// TODO:add test
public class ContactsContentObserver {
    private static final String TAG = ContactsContentObserver.class.getSimpleName();
    private static final boolean DEBUG = false;

    private ContentObserver mObserver;

    private final Context mContext;
    private final ContactsManager mManager;

    public ContactsContentObserver(final ContactsManager manager, final Context context) {
        mManager = manager;
        mContext = context;
    }

    public void registerObserver(final ContactsChangedListener listener) {
        if (DEBUG) {
            Log.d(TAG, "Registered Contacts Content Observer");
        }
        mObserver = new ContentObserver(null /* handler */) {
            @Override
            public void onChange(boolean self) {
                getBgExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (haveContentsChanged()) {
                            if (DEBUG) {
                                Log.d(TAG, "Contacts have changed; notifying listeners");
                            }
                            listener.onContactsChange();
                        }
                    }
                });
            }
        };
        final ContentResolver contentResolver = mContext.getContentResolver();
        contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, mObserver);
    }

    @UsedForTesting
    private ExecutorService getBgExecutor() {
        return ExecutorUtils.getExecutor("Check Contacts");
    }

    private boolean haveContentsChanged() {
        final long startTime = SystemClock.uptimeMillis();
        final int contactCount = mManager.getContactCount();
        if (contactCount > ContactsDictionaryConstants.MAX_CONTACT_COUNT) {
            // If there are too many contacts then return false. In this rare case it is impossible
            // to include all of them anyways and the cost of rebuilding the dictionary is too high.
            // TODO: Sort and check only the MAX_CONTACT_COUNT most recent contacts?
            return false;
        }
        if (contactCount != mManager.getContactCountAtLastRebuild()) {
            if (DEBUG) {
                Log.d(TAG, "Contact count changed: " + mManager.getContactCountAtLastRebuild()
                        + " to " + contactCount);
            }
            return true;
        }
        final ArrayList<String> names = mManager.getValidNames(Contacts.CONTENT_URI);
        if (names.hashCode() != mManager.getHashCodeAtLastRebuild()) {
            return true;
        }
        if (DEBUG) {
            Log.d(TAG, "No contacts changed. (runtime = " + (SystemClock.uptimeMillis() - startTime)
                    + " ms)");
        }
        return false;
    }

    public void unregister() {
        mContext.getContentResolver().unregisterContentObserver(mObserver);
    }
}
