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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.provider.ContactsContract.Contacts;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/**
 * Tests for {@link ContactsContentObserver}.
 */
@SmallTest
public class ContactsContentObserverTest {
    private static final int UPDATED_CONTACT_COUNT = 10;
    private static final int STALE_CONTACT_COUNT = 8;
    private static final ArrayList<String> STALE_NAMES_LIST = new ArrayList<>();
    private static final ArrayList<String> UPDATED_NAMES_LIST = new ArrayList<>();

    static {
        STALE_NAMES_LIST.add("Larry Page");
        STALE_NAMES_LIST.add("Roger Federer");
        UPDATED_NAMES_LIST.add("Larry Page");
        UPDATED_NAMES_LIST.add("Roger Federer");
        UPDATED_NAMES_LIST.add("Barak Obama");
    }

    @Mock private ContactsManager mMockManager;
    @Mock private Context mContext;

    private ContactsContentObserver mObserver;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mObserver = new ContactsContentObserver(mMockManager, mContext);
    }

    @After
    public void tearDown() {
        validateMockitoUsage();
    }

    @Test
    public void testHaveContentsChanged_NoChange() {
        when(mMockManager.getContactCount()).thenReturn(STALE_CONTACT_COUNT);
        when(mMockManager.getContactCountAtLastRebuild()).thenReturn(STALE_CONTACT_COUNT);
        when(mMockManager.getValidNames(eq(Contacts.CONTENT_URI))).thenReturn(STALE_NAMES_LIST);
        when(mMockManager.getHashCodeAtLastRebuild()).thenReturn(STALE_NAMES_LIST.hashCode());
        assertFalse(mObserver.haveContentsChanged());
    }
    @Test
    public void testHaveContentsChanged_UpdatedCount() {
        when(mMockManager.getContactCount()).thenReturn(UPDATED_CONTACT_COUNT);
        when(mMockManager.getContactCountAtLastRebuild()).thenReturn(STALE_CONTACT_COUNT);
        assertTrue(mObserver.haveContentsChanged());
    }

    @Test
    public void testHaveContentsChanged_HashUpdate() {
        when(mMockManager.getContactCount()).thenReturn(STALE_CONTACT_COUNT);
        when(mMockManager.getContactCountAtLastRebuild()).thenReturn(STALE_CONTACT_COUNT);
        when(mMockManager.getValidNames(eq(Contacts.CONTENT_URI))).thenReturn(UPDATED_NAMES_LIST);
        when(mMockManager.getHashCodeAtLastRebuild()).thenReturn(STALE_NAMES_LIST.hashCode());
        assertTrue(mObserver.haveContentsChanged());
    }
}
