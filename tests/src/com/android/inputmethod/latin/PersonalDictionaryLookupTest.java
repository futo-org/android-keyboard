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

package com.android.inputmethod.latin;

import static com.android.inputmethod.latin.PersonalDictionaryLookup.ANY_LOCALE;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.inputmethod.latin.PersonalDictionaryLookup.PersonalDictionaryListener;
import com.android.inputmethod.latin.utils.ExecutorUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Unit tests for {@link PersonalDictionaryLookup}.
 *
 * Note, this test doesn't mock out the ContentResolver, in order to make sure
 * {@link PersonalDictionaryLookup} works in a real setting.
 */
@SmallTest
public class PersonalDictionaryLookupTest extends AndroidTestCase {
    private static final String TAG = PersonalDictionaryLookupTest.class.getSimpleName();

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
     * Adds the given word to the personal dictionary.
     *
     * @param word the word to add
     * @param locale the locale of the word to add
     * @param frequency the frequency of the word to add
     * @return the Uri for the given word
     */
    @SuppressLint("NewApi")
    private Uri addWord(final String word, final Locale locale, int frequency, String shortcut) {
        // Add the given word for the given locale.
        UserDictionary.Words.addWord(mContext, word, frequency, shortcut, locale);
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
        // Remove the word from the personal dictionary.
        mContentResolver.delete(uri, null, null);
    }

    private PersonalDictionaryLookup setUpWord(final Locale locale) {
        // Insert "foo" in the personal dictionary for the given locale.
        addWord("foo", locale, 17, null);

        // Create the PersonalDictionaryLookup and wait until it's loaded.
        PersonalDictionaryLookup lookup =
                new PersonalDictionaryLookup(mContext, ExecutorUtils.SPELLING);
        lookup.open();
        return lookup;
    }

    private PersonalDictionaryLookup setUpShortcut(final Locale locale) {
        // Insert "shortcut" => "Expansion" in the personal dictionary for the given locale.
        addWord("Expansion", locale, 17, "shortcut");

        // Create the PersonalDictionaryLookup and wait until it's loaded.
        PersonalDictionaryLookup lookup =
                new PersonalDictionaryLookup(mContext, ExecutorUtils.SPELLING);
        lookup.open();
        return lookup;
    }

    private void verifyWordExists(final Set<String> set, final String word) {
        assertTrue(set.contains(word));
    }

    private void verifyWordDoesNotExist(final Set<String> set, final String word) {
        assertFalse(set.contains(word));
    }

    public void testShortcutKeyMatching() {
        Log.d(TAG, "testShortcutKeyMatching");
        PersonalDictionaryLookup lookup = setUpShortcut(Locale.US);

        assertEquals("Expansion", lookup.expandShortcut("shortcut", Locale.US));
        assertNull(lookup.expandShortcut("Shortcut", Locale.US));
        assertNull(lookup.expandShortcut("SHORTCUT", Locale.US));
        assertNull(lookup.expandShortcut("shortcu", Locale.US));
        assertNull(lookup.expandShortcut("shortcutt", Locale.US));

        lookup.close();
    }

    public void testShortcutMatchesInputCountry() {
        Log.d(TAG, "testShortcutMatchesInputCountry");
        PersonalDictionaryLookup lookup = setUpShortcut(Locale.US);

        verifyWordExists(lookup.getShortcutsForLocale(Locale.US), "shortcut");
        assertTrue(lookup.getShortcutsForLocale(Locale.UK).isEmpty());
        assertTrue(lookup.getShortcutsForLocale(Locale.ENGLISH).isEmpty());
        assertTrue(lookup.getShortcutsForLocale(Locale.FRENCH).isEmpty());
        assertTrue(lookup.getShortcutsForLocale(ANY_LOCALE).isEmpty());

        assertEquals("Expansion", lookup.expandShortcut("shortcut", Locale.US));
        assertNull(lookup.expandShortcut("shortcut", Locale.UK));
        assertNull(lookup.expandShortcut("shortcut", Locale.ENGLISH));
        assertNull(lookup.expandShortcut("shortcut", Locale.FRENCH));
        assertNull(lookup.expandShortcut("shortcut", ANY_LOCALE));

        lookup.close();
    }

    public void testShortcutMatchesInputLanguage() {
        Log.d(TAG, "testShortcutMatchesInputLanguage");
        PersonalDictionaryLookup lookup = setUpShortcut(Locale.ENGLISH);

        verifyWordExists(lookup.getShortcutsForLocale(Locale.US), "shortcut");
        verifyWordExists(lookup.getShortcutsForLocale(Locale.UK), "shortcut");
        verifyWordExists(lookup.getShortcutsForLocale(Locale.ENGLISH), "shortcut");
        assertTrue(lookup.getShortcutsForLocale(Locale.FRENCH).isEmpty());
        assertTrue(lookup.getShortcutsForLocale(ANY_LOCALE).isEmpty());

        assertEquals("Expansion", lookup.expandShortcut("shortcut", Locale.US));
        assertEquals("Expansion", lookup.expandShortcut("shortcut", Locale.UK));
        assertEquals("Expansion", lookup.expandShortcut("shortcut", Locale.ENGLISH));
        assertNull(lookup.expandShortcut("shortcut", Locale.FRENCH));
        assertNull(lookup.expandShortcut("shortcut", ANY_LOCALE));

        lookup.close();
    }

    public void testShortcutMatchesAnyLocale() {
        PersonalDictionaryLookup lookup = setUpShortcut(PersonalDictionaryLookup.ANY_LOCALE);

        verifyWordExists(lookup.getShortcutsForLocale(Locale.US), "shortcut");
        verifyWordExists(lookup.getShortcutsForLocale(Locale.UK), "shortcut");
        verifyWordExists(lookup.getShortcutsForLocale(Locale.ENGLISH), "shortcut");
        verifyWordExists(lookup.getShortcutsForLocale(Locale.FRENCH), "shortcut");
        verifyWordExists(lookup.getShortcutsForLocale(ANY_LOCALE), "shortcut");

        assertEquals("Expansion", lookup.expandShortcut("shortcut", Locale.US));
        assertEquals("Expansion", lookup.expandShortcut("shortcut", Locale.UK));
        assertEquals("Expansion", lookup.expandShortcut("shortcut", Locale.ENGLISH));
        assertEquals("Expansion", lookup.expandShortcut("shortcut", Locale.FRENCH));
        assertEquals("Expansion", lookup.expandShortcut("shortcut", ANY_LOCALE));

        lookup.close();
    }

    public void testExactLocaleMatch() {
        Log.d(TAG, "testExactLocaleMatch");
        PersonalDictionaryLookup lookup = setUpWord(Locale.US);

        verifyWordExists(lookup.getWordsForLocale(Locale.US), "foo");
        verifyWordDoesNotExist(lookup.getWordsForLocale(Locale.UK), "foo");
        verifyWordDoesNotExist(lookup.getWordsForLocale(Locale.ENGLISH), "foo");
        verifyWordDoesNotExist(lookup.getWordsForLocale(Locale.FRENCH), "foo");
        verifyWordDoesNotExist(lookup.getWordsForLocale(ANY_LOCALE), "foo");

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
        assertFalse(lookup.isValidWord("foo", ANY_LOCALE));

        lookup.close();
    }

    public void testSubLocaleMatch() {
        Log.d(TAG, "testSubLocaleMatch");
        PersonalDictionaryLookup lookup = setUpWord(Locale.ENGLISH);

        verifyWordExists(lookup.getWordsForLocale(Locale.US), "foo");
        verifyWordExists(lookup.getWordsForLocale(Locale.UK), "foo");
        verifyWordExists(lookup.getWordsForLocale(Locale.ENGLISH), "foo");
        verifyWordDoesNotExist(lookup.getWordsForLocale(Locale.FRENCH), "foo");
        verifyWordDoesNotExist(lookup.getWordsForLocale(ANY_LOCALE), "foo");

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
        PersonalDictionaryLookup lookup = setUpWord(null);

        verifyWordExists(lookup.getWordsForLocale(Locale.US), "foo");
        verifyWordExists(lookup.getWordsForLocale(Locale.UK), "foo");
        verifyWordExists(lookup.getWordsForLocale(Locale.ENGLISH), "foo");
        verifyWordExists(lookup.getWordsForLocale(Locale.FRENCH), "foo");
        verifyWordExists(lookup.getWordsForLocale(ANY_LOCALE), "foo");

        // Any capitalization variation should match for fr, en and en_US.
        assertTrue(lookup.isValidWord("foo", ANY_LOCALE));
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

        // Insert "Foo" as capitalized in the personal dictionary under the en_US and en_CA and fr
        // locales.
        addWord("Foo", Locale.US, 17, null);
        addWord("foO", Locale.CANADA, 17, null);
        addWord("fOo", Locale.FRENCH, 17, null);

        // Create the PersonalDictionaryLookup and wait until it's loaded.
        PersonalDictionaryLookup lookup = new PersonalDictionaryLookup(mContext,
                ExecutorUtils.SPELLING);
        lookup.open();

        // Both en_CA and en_US match.
        assertTrue(lookup.isValidWord("foo", Locale.CANADA));
        assertTrue(lookup.isValidWord("foo", Locale.US));
        assertTrue(lookup.isValidWord("foo", Locale.FRENCH));
        // Other locales, including more general locales won't match.
        assertFalse(lookup.isValidWord("foo", Locale.ENGLISH));
        assertFalse(lookup.isValidWord("foo", Locale.UK));
        assertFalse(lookup.isValidWord("foo", ANY_LOCALE));

        lookup.close();
    }


    public void testCaseMatchingForWordsAndShortcuts() {
        Log.d(TAG, "testCaseMatchingForWordsAndShortcuts");
        addWord("Foo", Locale.US, 17, "f");
        addWord("bokabu", Locale.US, 17, "Bu");

        // Create the PersonalDictionaryLookup and wait until it's loaded.
        PersonalDictionaryLookup lookup = new PersonalDictionaryLookup(mContext,
                ExecutorUtils.SPELLING);
        lookup.open();

        // Valid, inspite of capitalization in US but not in other
        // locales.
        assertTrue(lookup.isValidWord("Foo", Locale.US));
        assertTrue(lookup.isValidWord("foo", Locale.US));
        assertFalse(lookup.isValidWord("Foo", Locale.UK));
        assertFalse(lookup.isValidWord("foo", Locale.UK));

        // Valid in all forms in US.
        assertTrue(lookup.isValidWord("bokabu", Locale.US));
        assertTrue(lookup.isValidWord("BOKABU", Locale.US));
        assertTrue(lookup.isValidWord("BokaBU", Locale.US));

        // Correct capitalization; sensitive to shortcut casing & locale.
        assertEquals("Foo", lookup.expandShortcut("f", Locale.US));
        assertNull(lookup.expandShortcut("f", Locale.UK));

        // Correct capitalization; sensitive to shortcut casing & locale.
        assertEquals("bokabu", lookup.expandShortcut("Bu", Locale.US));
        assertNull(lookup.expandShortcut("Bu", Locale.UK));
        assertNull(lookup.expandShortcut("bu", Locale.US));

        // Verify that raw strings are retained for #getWordsForLocale.
        verifyWordExists(lookup.getWordsForLocale(Locale.US), "Foo");
        verifyWordDoesNotExist(lookup.getWordsForLocale(Locale.US), "foo");
    }

    public void testManageListeners() {
        Log.d(TAG, "testManageListeners");

        PersonalDictionaryLookup lookup =
                new PersonalDictionaryLookup(mContext, ExecutorUtils.SPELLING);

        PersonalDictionaryListener listener = mock(PersonalDictionaryListener.class);
        // Add the same listener a bunch of times. It doesn't make a difference.
        lookup.addListener(listener);
        lookup.addListener(listener);
        lookup.addListener(listener);
        lookup.notifyListeners();

        verify(listener, times(1)).onUpdate();

        // Remove the same listener a bunch of times. It doesn't make a difference.
        lookup.removeListener(listener);
        lookup.removeListener(listener);
        lookup.removeListener(listener);
        lookup.notifyListeners();

        verifyNoMoreInteractions(listener);
    }

    public void testReload() {
        Log.d(TAG, "testReload");

        // Insert "foo".
        Uri uri = addWord("foo", Locale.US, 17, null);

        // Create the PersonalDictionaryLookup and wait until it's loaded.
        PersonalDictionaryLookup lookup =
                new PersonalDictionaryLookup(mContext, ExecutorUtils.SPELLING);
        lookup.open();

        // "foo" should match.
        assertTrue(lookup.isValidWord("foo", Locale.US));

        // "bar" shouldn't match.
        assertFalse(lookup.isValidWord("bar", Locale.US));

        // Now delete "foo" and add "bar".
        deleteWord(uri);
        addWord("bar", Locale.US, 18, null);

        // Wait a little bit before expecting a change. The time we wait should be greater than
        // PersonalDictionaryLookup.RELOAD_DELAY_MS.
        try {
            Thread.sleep(PersonalDictionaryLookup.RELOAD_DELAY_MS + 1000);
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

    public void testDictionaryStats() {
        Log.d(TAG, "testDictionaryStats");

        // Insert "foo" and "bar". Only "foo" has a shortcut.
        Uri uri = addWord("foo", Locale.GERMANY, 17, "f");
        addWord("bar", Locale.GERMANY, 17, null);

        // Create the PersonalDictionaryLookup and wait until it's loaded.
        PersonalDictionaryLookup lookup =
                new PersonalDictionaryLookup(mContext, ExecutorUtils.SPELLING);
        lookup.open();

        // "foo" should match.
        assertTrue(lookup.isValidWord("foo", Locale.GERMANY));

        // "bar" should match.
        assertTrue(lookup.isValidWord("bar", Locale.GERMANY));

        // "foo" should have a shortcut.
        assertEquals("foo", lookup.expandShortcut("f", Locale.GERMANY));

        // Now delete "foo".
        deleteWord(uri);

        // Wait a little bit before expecting a change. The time we wait should be greater than
        // PersonalDictionaryLookup.RELOAD_DELAY_MS.
        try {
            Thread.sleep(PersonalDictionaryLookup.RELOAD_DELAY_MS + 1000);
        } catch (InterruptedException e) {
        }

        // Perform lookups again. Reload should have occured.
        //
        // "foo" should not match.
        assertFalse(lookup.isValidWord("foo", Locale.GERMANY));

        // "foo" should not have a shortcut.
        assertNull(lookup.expandShortcut("f", Locale.GERMANY));

        // "bar" should still match.
        assertTrue(lookup.isValidWord("bar", Locale.GERMANY));

        lookup.close();
    }

    public void testClose() {
        Log.d(TAG, "testClose");

        // Insert "foo".
        Uri uri = addWord("foo", Locale.US, 17, null);

        // Create the PersonalDictionaryLookup and wait until it's loaded.
        PersonalDictionaryLookup lookup =
                new PersonalDictionaryLookup(mContext, ExecutorUtils.SPELLING);
        lookup.open();

        // "foo" should match.
        assertTrue(lookup.isValidWord("foo", Locale.US));

        // "bar" shouldn't match.
        assertFalse(lookup.isValidWord("bar", Locale.US));

        // Now close (prevents further reloads).
        lookup.close();

        // Now delete "foo" and add "bar".
        deleteWord(uri);
        addWord("bar", Locale.US, 18, null);

        // Wait a little bit before expecting a change. The time we wait should be greater than
        // PersonalDictionaryLookup.RELOAD_DELAY_MS.
        try {
            Thread.sleep(PersonalDictionaryLookup.RELOAD_DELAY_MS + 1000);
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
