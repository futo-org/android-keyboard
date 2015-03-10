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

import com.android.inputmethod.latin.ContactsManager.ContactsChangedListener;
import com.android.inputmethod.latin.utils.ExecutorUtils;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A content observer that listens to updates to content provider {@link Contacts#CONTENT_URI}.
 */
public class ContactsContentObserver implements Runnable {
    private static final String TAG = ContactsContentObserver.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static AtomicBoolean sRunning = new AtomicBoolean(false);

    private final Context mContext;
    private final ContactsManager mManager;

    private ContentObserver mContentObserver;
    private ContactsChangedListener mContactsChangedListener;

    public ContactsContentObserver(final ContactsManager manager, final Context context) {
        mManager = manager;
        mContext = context;
    }

    public void registerObserver(final ContactsChangedListener listener) {
        if (DEBUG) {
            Log.d(TAG, "Registered Contacts Content Observer");
        }
        mContactsChangedListener = listener;
        mContentObserver = new ContentObserver(null /* handler */) {
            @Override
            public void onChange(boolean self) {
                // TODO(zivkovic): Schedule a separate task to reset the decoder.
                ExecutorUtils.getBackgroundExecutor().execute(ContactsContentObserver.this);
            }
        };
        final ContentResolver contentResolver = mContext.getContentResolver();
        contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, mContentObserver);
    }

    @Override
    public void run() {
        if (!sRunning.compareAndSet(false /* expect */, true /* update */)) {
            if (DEBUG) {
                Log.d(TAG, "run() : Already running. Don't waste time checking again.");
            }
            return;
        }
        if (haveContentsChanged()) {
            if (DEBUG) {
                Log.d(TAG, "run() : Contacts have changed. Notifying listeners.");
            }
            mContactsChangedListener.onContactsChange();
        }
        sRunning.set(false);
    }

    boolean haveContentsChanged() {
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
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
    }
}
