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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

import com.android.inputmethod.latin.common.Constants;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages all interactions with Contacts DB.
 *
 * The manager provides an API for listening to meaning full updates by keeping a
 * measure of the current state of the content provider.
 */
public class ContactsManager {
    private static final String TAG = "ContactsManager";

    /**
     * Interface to implement for classes interested in getting notified for updates
     * to Contacts content provider.
     */
    public static interface ContactsChangedListener {
        public void onContactsChange();
    }

    /**
     * The number of contacts observed in the most recent instance of
     * contacts content provider.
     */
    private AtomicInteger mContactCountAtLastRebuild = new AtomicInteger(0);

    /**
     * The hash code of list of valid contacts names in the most recent dictionary
     * rebuild.
     */
    private AtomicInteger mHashCodeAtLastRebuild = new AtomicInteger(0);

    private final Context mContext;
    private final ContactsContentObserver mObserver;

    public ContactsManager(final Context context) {
        mContext = context;
        mObserver = new ContactsContentObserver(this /* ContactsManager */, context);
    }

    // TODO: This was synchronized in previous version. Why?
    public void registerForUpdates(final ContactsChangedListener listener) {
        mObserver.registerObserver(listener);
    }

    public int getContactCountAtLastRebuild() {
        return mContactCountAtLastRebuild.get();
    }

    public int getHashCodeAtLastRebuild() {
        return mHashCodeAtLastRebuild.get();
    }

    /**
     * Returns all the valid names in the Contacts DB. Callers should also
     * call {@link #updateLocalState(ArrayList)} after they are done with result
     * so that the manager can cache local state for determining updates.
     */
    public ArrayList<String> getValidNames(final Uri uri) {
        final ArrayList<String> names = new ArrayList<>();
        // Check all contacts since it's not possible to find out which names have changed.
        // This is needed because it's possible to receive extraneous onChange events even when no
        // name has changed.
        final Cursor cursor = mContext.getContentResolver().query(uri,
                ContactsDictionaryConstants.PROJECTION, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        final String name = cursor.getString(
                                ContactsDictionaryConstants.NAME_INDEX);
                        if (isValidName(name)) {
                            names.add(name);
                        }
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return names;
    }

    /**
     * Returns the number of contacts in contacts content provider.
     */
    public int getContactCount() {
        // TODO: consider switching to a rawQuery("select count(*)...") on the database if
        // performance is a bottleneck.
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(Contacts.CONTENT_URI,
                    ContactsDictionaryConstants.PROJECTION_ID_ONLY, null, null, null);
            if (null == cursor) {
                return 0;
            }
            return cursor.getCount();
        } catch (final SQLiteException e) {
            Log.e(TAG, "SQLiteException in the remote Contacts process.", e);
        } finally {
            if (null != cursor) {
                cursor.close();
            }
        }
        return 0;
    }

    private static boolean isValidName(final String name) {
        if (name != null && -1 == name.indexOf(Constants.CODE_COMMERCIAL_AT)) {
            return true;
        }
        return false;
    }

    /**
     * Updates the local state of the manager. This should be called when the callers
     * are done with all the updates of the content provider successfully.
     */
    public void updateLocalState(final ArrayList<String> names) {
        mContactCountAtLastRebuild.set(getContactCount());
        mHashCodeAtLastRebuild.set(names.hashCode());
    }

    /**
     * Performs any necessary cleanup.
     */
    public void close() {
        mObserver.unregister();
    }
}
