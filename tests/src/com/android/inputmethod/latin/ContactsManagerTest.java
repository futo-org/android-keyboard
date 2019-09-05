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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.test.RenamingDelegatingContext;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.ContactsDictionaryConstants;
import com.android.inputmethod.latin.ContactsManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link ContactsManager}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContactsManagerTest {

    private ContactsManager mManager;
    private FakeContactsContentProvider mFakeContactsContentProvider;
    private MatrixCursor mMatrixCursor;

    private final static float EPSILON = 0.00001f;

    @Before
    public void setUp() throws Exception {
        // Fake content provider
        mFakeContactsContentProvider = new FakeContactsContentProvider();
        mMatrixCursor = new MatrixCursor(ContactsDictionaryConstants.PROJECTION);
        // Add the fake content provider to fake content resolver.
        final MockContentResolver contentResolver = new MockContentResolver();
        contentResolver.addProvider(ContactsContract.AUTHORITY, mFakeContactsContentProvider);
        // Add the fake content resolver to a fake context.
        final ContextWithMockContentResolver context =
                new ContextWithMockContentResolver(InstrumentationRegistry.getTargetContext());
        context.setContentResolver(contentResolver);

        mManager = new ContactsManager(context);
    }

    @Test
    public void testGetValidNames() {
        final String contactName1 = "firstname last-name";
        final String contactName2 = "larry";
        mMatrixCursor.addRow(new Object[] { 1, contactName1, 0, 0, 0 });
        mMatrixCursor.addRow(new Object[] { 2, null /* null name */, 0, 0, 0 });
        mMatrixCursor.addRow(new Object[] { 3, contactName2, 0, 0, 0 });
        mMatrixCursor.addRow(new Object[] { 4, "floopy@example.com" /* invalid name */, 0, 0, 0 });
        mMatrixCursor.addRow(new Object[] { 5, "news-group" /* invalid name */, 0, 0, 0 });
        mFakeContactsContentProvider.addQueryResult(Contacts.CONTENT_URI, mMatrixCursor);

        final ArrayList<String> validNames = mManager.getValidNames(Contacts.CONTENT_URI);
        assertEquals(2, validNames.size());
        assertEquals(contactName1, validNames.get(0));
        assertEquals(contactName2, validNames.get(1));
    }

    @Test
    public void testGetValidNamesAffinity() {
        final long now = System.currentTimeMillis();
        final long month_ago = now - TimeUnit.MILLISECONDS.convert(31, TimeUnit.DAYS);
        for (int i = 0; i < ContactsManager.MAX_CONTACT_NAMES + 10; ++i) {
            mMatrixCursor.addRow(new Object[] { i, "name" + i, i, now, 1 });
        }
        mFakeContactsContentProvider.addQueryResult(Contacts.CONTENT_URI, mMatrixCursor);

        final ArrayList<String> validNames = mManager.getValidNames(Contacts.CONTENT_URI);
        assertEquals(ContactsManager.MAX_CONTACT_NAMES, validNames.size());
        for (int i = 0; i < 10; ++i) {
            assertFalse(validNames.contains("name" + i));
        }
        for (int i = 10; i < ContactsManager.MAX_CONTACT_NAMES + 10; ++i) {
            assertTrue(validNames.contains("name" + i));
        }
    }

    @Test
    public void testComputeAffinity() {
        final long now = System.currentTimeMillis();
        final long month_ago = now - TimeUnit.MILLISECONDS.convert(31, TimeUnit.DAYS);
        mMatrixCursor.addRow(new Object[] { 1, "name", 1, month_ago, 1 });
        mFakeContactsContentProvider.addQueryResult(Contacts.CONTENT_URI, mMatrixCursor);

        Cursor cursor = mFakeContactsContentProvider.query(Contacts.CONTENT_URI,
                ContactsDictionaryConstants.PROJECTION_ID_ONLY, null, null, null);
        cursor.moveToFirst();
        ContactsManager.RankedContact contact = new ContactsManager.RankedContact(cursor);
        contact.computeAffinity(1, month_ago);
        assertEquals(contact.getAffinity(), 1.0f, EPSILON);
        contact.computeAffinity(2, now);
        assertEquals(contact.getAffinity(), (2.0f/3.0f + (float)Math.pow(0.5, 3) + 1.0f) / 3,
                EPSILON);
    }

    @Test
    public void testGetCount() {
        mMatrixCursor.addRow(new Object[] { 1, "firstname", 0, 0, 0 });
        mMatrixCursor.addRow(new Object[] { 2, null /* null name */, 0, 0, 0 });
        mMatrixCursor.addRow(new Object[] { 3, "larry", 0, 0, 0 });
        mMatrixCursor.addRow(new Object[] { 4, "floopy@example.com" /* invalid name */, 0, 0, 0 });
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
