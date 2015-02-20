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
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Tests for {@link ContactsManager}
 */
@SmallTest
public class ContactsManagerTest extends AndroidTestCase {

    private ContactsManager mManager;
    private FakeContactsContentProvider mFakeContactsContentProvider;
    private MatrixCursor mMatrixCursor;

    @Before
    @Override
    public void setUp() throws Exception {
        // Fake content provider
        mFakeContactsContentProvider = new FakeContactsContentProvider();
        mMatrixCursor = new MatrixCursor(ContactsDictionaryConstants.PROJECTION);
        // Add the fake content provider to fake content resolver.
        final MockContentResolver contentResolver = new MockContentResolver();
        contentResolver.addProvider(ContactsContract.AUTHORITY, mFakeContactsContentProvider);
        // Add the fake content resolver to a fake context.
        final ContextWithMockContentResolver context = new ContextWithMockContentResolver(mContext);
        context.setContentResolver(contentResolver);

        mManager = new ContactsManager(context);
    }

    @Test
    public void testGetValidNames() {
        final String contactName1 = "firstname lastname";
        final String contactName2 = "larry";
        mMatrixCursor.addRow(new Object[] { 1, contactName1 });
        mMatrixCursor.addRow(new Object[] { 2, null /* null name */ });
        mMatrixCursor.addRow(new Object[] { 3, contactName2 });
        mMatrixCursor.addRow(new Object[] { 4, "floopy@example.com" /* invalid name */ });
        mFakeContactsContentProvider.addQueryResult(Contacts.CONTENT_URI, mMatrixCursor);

        final ArrayList<String> validNames = mManager.getValidNames(Contacts.CONTENT_URI);
        assertEquals(2, validNames.size());
        assertEquals(contactName1, validNames.get(0));
        assertEquals(contactName2, validNames.get(1));
    }

    @Test
    public void testGetCount() {
        mMatrixCursor.addRow(new Object[] { 1, "firstname" });
        mMatrixCursor.addRow(new Object[] { 2, null /* null name */ });
        mMatrixCursor.addRow(new Object[] { 3, "larry" });
        mMatrixCursor.addRow(new Object[] { 4, "floopy@example.com" /* invalid name */ });
        mFakeContactsContentProvider.addQueryResult(Contacts.CONTENT_URI, mMatrixCursor);

        assertEquals(4, mManager.getContactCount());
    }


    static class ContextWithMockContentResolver extends RenamingDelegatingContext {
        private ContentResolver contentResolver;

        public void setContentResolver(final ContentResolver contentResolver) {
            this.contentResolver = contentResolver;
        }

        public ContextWithMockContentResolver(final Context targetContext) {
            super(targetContext, "test");
        }

        @Override
        public ContentResolver getContentResolver() {
            return contentResolver;
        }
    }

    static class FakeContactsContentProvider extends MockContentProvider {
        private final HashMap<String, MatrixCursor> mQueryCursorMapForTestExpectations =
                new HashMap<>();

        @Override
        public Cursor query(final Uri uri, final String[] projection, final String selection,
                final String[] selectionArgs, final String sortOrder) {
            return mQueryCursorMapForTestExpectations.get(uri.toString());
        }

        public void reset() {
            mQueryCursorMapForTestExpectations.clear();
        }

        public void addQueryResult(final Uri uri, final MatrixCursor cursor) {
            mQueryCursorMapForTestExpectations.put(uri.toString(), cursor);
        }
    }
}
