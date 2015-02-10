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
 * limitations under the License.
 */

package com.android.inputmethod.latin.spellcheck;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import java.util.HashSet;
import java.util.Locale;

/**
 * Unit tests for {@link UserDictionaryLookup}.
 *
 * Note, this test doesn't mock out the ContentResolver, in order to make sure UserDictionaryLookup
 * works in a real setting.
 */
@SmallTest
public class UserDictionaryLookupTest extends AndroidTestCase {
    private static final String TAG = UserDictionaryLookupTest.class.getSimpleName();

    private ContentResolver mContentResolver;
    private HashSet<Uri> mAddedBackup;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContentResolver = mContext.getContentResolver();
        mAddedBackup = new HashSet<Uri>();
    }

    @Override
    protected void tearDown() throws Exception {
        // Remove all entries added during this test.
        for (Uri row : mAddedBackup) {
            mContentResolver.delete(row, null, null);
        }
        mAddedBackup.clear();

        super.tearDown();
    }

    /**
     * Adds the given word to UserDictionary.
     *
     * @param word the word to add
     * @param locale the locale of the word to add
     * @param frequency the frequency of the word to add
     * @return the Uri for the given word
     */
    @SuppressLint("NewApi")
    private Uri addWord(final String word, final Locale locale, int frequency) {
        // Add the given word for the given locale.
        UserDictionary.Words.addWord(mContext, word, frequency, null, locale);
        // Obtain an Uri for the given word.
        Cursor cursor = mContentResolver.query(UserDictionary.Words.CONTENT_URI, null,
                UserDictionary.Words.WORD + "='" + word + "'", null, null);
        assertTrue(cursor.moveToFirst());
        Uri uri = Uri.withAppendedPath(UserDictionary.Words.CONTENT_URI,
                cursor.getString(cursor.getColumnIndex(UserDictionary.Words._ID)));
        // Add the row to the backup for later clearing.
        mAddedBackup.add(uri);
        return uri;
    }

    /**
     * Deletes the entry for the given word from UserDictionary.
     *
     * @param uri the Uri for the word as returned by addWord
     */
    private void deleteWord(Uri uri) {
        // Remove the word from the backup so that it's not cleared again later.
        mAddedBackup.remove(uri);
        // Remove the word from UserDictionary.
        mContentResolver.delete(uri, null, null);
    }

    public void testExactLocaleMatch() {
        Log.d(TAG, "testExactLocaleMatch");

        // Insert "Foo" as capitalized in the UserDictionary under en_US locale.
        addWord("Foo", Locale.US, 17);

        // Create the UserDictionaryLookup and wait until it's loaded.
        UserDictionaryLookup lookup = new UserDictionaryLookup(mContext);
        while (!lookup.isLoaded()) {
        }

        // Any capitalization variation should match.
        assertTrue(lookup.isValidWord("foo", Locale.US));
        assertTrue(lookup.isValidWord("Foo", Locale.US));
        assertTrue(lookup.isValidWord("FOO", Locale.US));
        // But similar looking words don't match.
        assertFalse(lookup.isValidWord("fo", Locale.US));
        assertFalse(lookup.isValidWord("fop", Locale.US));
        assertFalse(lookup.isValidWord("fooo", Locale.US));
        // Other locales, including more general locales won't match.
        assertFalse(lookup.isValidWord("foo", Locale.ENGLISH));
        assertFalse(lookup.isValidWord("foo", Locale.UK));
        assertFalse(lookup.isValidWord("foo", Locale.FRENCH));
        assertFalse(lookup.isValidWord("foo", new Locale("")));

        lookup.close();
    }

    public void testSubLocaleMatch() {
        Log.d(TAG, "testSubLocaleMatch");

        // Insert "Foo" as capitalized in the UserDictionary under the en locale.
        addWord("Foo", Locale.ENGLISH, 17);

        // Create the UserDictionaryLookup and wait until it's loaded.
        UserDictionaryLookup lookup = new UserDictionaryLookup(mContext);
        while (!lookup.isLoaded()) {
        }

        // Any capitalization variation should match for both en and en_US.
        assertTrue(lookup.isValidWord("foo", Locale.ENGLISH));
        assertTrue(lookup.isValidWord("foo", Locale.US));
        assertTrue(lookup.isValidWord("Foo", Locale.US));
        assertTrue(lookup.isValidWord("FOO", Locale.US));
        // But similar looking words don't match.
        assertFalse(lookup.isValidWord("fo", Locale.US));
        assertFalse(lookup.isValidWord("fop", Locale.US));
        assertFalse(lookup.isValidWord("fooo", Locale.US));

        lookup.close();
    }

    public void testAllLocalesMatch() {
        Log.d(TAG, "testAllLocalesMatch");

        // Insert "Foo" as capitalized in the UserDictionary under the all locales.
        addWord("Foo", null, 17);

        // Create the UserDictionaryLookup and wait until it's loaded.
        UserDictionaryLookup lookup = new UserDictionaryLookup(mContext);
        while (!lookup.isLoaded()) {
        }

        // Any capitalization variation should match for fr, en and en_US.
        assertTrue(lookup.isValidWord("foo", new Locale("")));
        assertTrue(lookup.isValidWord("foo", Locale.FRENCH));
        assertTrue(lookup.isValidWord("foo", Locale.ENGLISH));
        assertTrue(lookup.isValidWord("foo", Locale.US));
        assertTrue(lookup.isValidWord("Foo", Locale.US));
        assertTrue(lookup.isValidWord("FOO", Locale.US));
        // But similar looking words don't match.
        assertFalse(lookup.isValidWord("fo", Locale.US));
        assertFalse(lookup.isValidWord("fop", Locale.US));
        assertFalse(lookup.isValidWord("fooo", Locale.US));

        lookup.close();
    }

    public void testMultipleLocalesMatch() {
        Log.d(TAG, "testMultipleLocalesMatch");

        // Insert "Foo" as capitalized in the UserDictionary under the en_US and en_CA and fr
        // locales.
        addWord("Foo", Locale.US, 17);
        addWord("foO", Locale.CANADA, 17);
        addWord("fOo", Locale.FRENCH, 17);

        // Create the UserDictionaryLookup and wait until it's loaded.
        UserDictionaryLookup lookup = new UserDictionaryLookup(mContext);
        while (!lookup.isLoaded()) {
        }

        // Both en_CA and en_US match.
        assertTrue(lookup.isValidWord("foo", Locale.CANADA));
        assertTrue(lookup.isValidWord("foo", Locale.US));
        assertTrue(lookup.isValidWord("foo", Locale.FRENCH));
        // Other locales, including more general locales won't match.
        assertFalse(lookup.isValidWord("foo", Locale.ENGLISH));
        assertFalse(lookup.isValidWord("foo", Locale.UK));
        assertFalse(lookup.isValidWord("foo", new Locale("")));

        lookup.close();
    }

    public void testReload() {
        Log.d(TAG, "testReload");

        // Insert "foo".
        Uri uri = addWord("foo", Locale.US, 17);

        // Create the UserDictionaryLookup and wait until it's loaded.
        UserDictionaryLookup lookup = new UserDictionaryLookup(mContext);
        while (!lookup.isLoaded()) {
        }

        // "foo" should match.
        assertTrue(lookup.isValidWord("foo", Locale.US));

        // "bar" shouldn't match.
        assertFalse(lookup.isValidWord("bar", Locale.US));

        // Now delete "foo" and add "bar".
        deleteWord(uri);
        addWord("bar", Locale.US, 18);

        // Wait a little bit before expecting a change. The time we wait should be greater than
        // UserDictionaryLookup.RELOAD_DELAY_MS.
        try {
            Thread.sleep(UserDictionaryLookup.RELOAD_DELAY_MS + 1000);
        } catch (InterruptedException e) {
        }

        // Perform lookups again. Reload should have occured.
        //
        // "foo" should not match.
        assertFalse(lookup.isValidWord("foo", Locale.US));

        // "bar" should match.
        assertTrue(lookup.isValidWord("bar", Locale.US));

        lookup.close();
    }

    public void testClose() {
        Log.d(TAG, "testClose");

        // Insert "foo".
        Uri uri = addWord("foo", Locale.US, 17);

        // Create the UserDictionaryLookup and wait until it's loaded.
        UserDictionaryLookup lookup = new UserDictionaryLookup(mContext);
        while (!lookup.isLoaded()) {
        }

        // "foo" should match.
        assertTrue(lookup.isValidWord("foo", Locale.US));

        // "bar" shouldn't match.
        assertFalse(lookup.isValidWord("bar", Locale.US));

        // Now close (prevents further reloads).
        lookup.close();

        // Now delete "foo" and add "bar".
        deleteWord(uri);
        addWord("bar", Locale.US, 18);

        // Wait a little bit before expecting a change. The time we wait should be greater than
        // UserDictionaryLookup.RELOAD_DELAY_MS.
        try {
            Thread.sleep(UserDictionaryLookup.RELOAD_DELAY_MS + 1000);
        } catch (InterruptedException e) {
        }

        // Perform lookups again. Reload should not have occurred.
        //
        // "foo" should stil match.
        assertTrue(lookup.isValidWord("foo", Locale.US));

        // "bar" should still not match.
        assertFalse(lookup.isValidWord("bar", Locale.US));
    }
}
