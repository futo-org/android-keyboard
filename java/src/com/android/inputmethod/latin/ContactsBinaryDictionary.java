/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.inputmethod.latin.personalization.AccountUtils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.latin.utils.StringUtils;

import java.util.List;
import java.util.Locale;

public class ContactsBinaryDictionary extends ExpandableBinaryDictionary {

    private static final String[] PROJECTION = {BaseColumns._ID, Contacts.DISPLAY_NAME};
    private static final String[] PROJECTION_ID_ONLY = {BaseColumns._ID};

    private static final String TAG = ContactsBinaryDictionary.class.getSimpleName();
    private static final String NAME = "contacts";

    private static boolean DEBUG = false;

    /**
     * Frequency for contacts information into the dictionary
     */
    private static final int FREQUENCY_FOR_CONTACTS = 40;
    private static final int FREQUENCY_FOR_CONTACTS_BIGRAM = 90;

    /** The maximum number of contacts that this dictionary supports. */
    private static final int MAX_CONTACT_COUNT = 10000;

    private static final int INDEX_NAME = 1;

    /** The number of contacts in the most recent dictionary rebuild. */
    static private int sContactCountAtLastRebuild = 0;

    /** The locale for this contacts dictionary. Controls name bigram predictions. */
    public final Locale mLocale;

    private ContentObserver mObserver;

    /**
     * Whether to use "firstname lastname" in bigram predictions.
     */
    private final boolean mUseFirstLastBigrams;

    public ContactsBinaryDictionary(final Context context, final Locale locale) {
        super(context, getFilenameWithLocale(NAME, locale.toString()), Dictionary.TYPE_CONTACTS,
                false /* isUpdatable */);
        mLocale = locale;
        mUseFirstLastBigrams = useFirstLastBigramsForLocale(locale);
        registerObserver(context);

        // Load the current binary dictionary from internal storage. If no binary dictionary exists,
        // loadDictionary will start a new thread to generate one asynchronously.
        loadDictionary();
    }

    private synchronized void registerObserver(final Context context) {
        // Perform a managed query. The Activity will handle closing and requerying the cursor
        // when needed.
        if (mObserver != null) return;
        ContentResolver cres = context.getContentResolver();
        cres.registerContentObserver(Contacts.CONTENT_URI, true, mObserver =
                new ContentObserver(null) {
                    @Override
                    public void onChange(boolean self) {
                        setRequiresReload(true);
                    }
                });
    }

    public void reopen(final Context context) {
        registerObserver(context);
    }

    @Override
    public synchronized void close() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.close();
    }

    @Override
    public void loadDictionaryAsync() {
        loadDeviceAccountsEmailAddresses();
        loadDictionaryAsyncForUri(ContactsContract.Profile.CONTENT_URI);
        // TODO: Switch this URL to the newer ContactsContract too
        loadDictionaryAsyncForUri(Contacts.CONTENT_URI);
    }

    private void loadDeviceAccountsEmailAddresses() {
        final List<String> accountVocabulary =
                AccountUtils.getDeviceAccountsEmailAddresses(mContext);
        if (accountVocabulary == null || accountVocabulary.isEmpty()) {
            return;
        }
        for (String word : accountVocabulary) {
            if (DEBUG) {
                Log.d(TAG, "loadAccountVocabulary: " + word);
            }
            super.addWord(word, null /* shortcut */, FREQUENCY_FOR_CONTACTS, 0 /* shortcutFreq */,
                    false /* isNotAWord */);
        }
    }

    private void loadDictionaryAsyncForUri(final Uri uri) {
        try {
            Cursor cursor = mContext.getContentResolver()
                    .query(uri, PROJECTION, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        sContactCountAtLastRebuild = getContactCount();
                        addWords(cursor);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (final SQLiteException e) {
            Log.e(TAG, "SQLiteException in the remote Contacts process.", e);
        } catch (final IllegalStateException e) {
            Log.e(TAG, "Contacts DB is having problems", e);
        }
    }

    private boolean useFirstLastBigramsForLocale(final Locale locale) {
        // TODO: Add firstname/lastname bigram rules for other languages.
        if (locale != null && locale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
            return true;
        }
        return false;
    }

    private void addWords(final Cursor cursor) {
        int count = 0;
        while (!cursor.isAfterLast() && count < MAX_CONTACT_COUNT) {
            String name = cursor.getString(INDEX_NAME);
            if (isValidName(name)) {
                addName(name);
                ++count;
            }
            cursor.moveToNext();
        }
    }

    private int getContactCount() {
        // TODO: consider switching to a rawQuery("select count(*)...") on the database if
        // performance is a bottleneck.
        try {
            final Cursor cursor = mContext.getContentResolver().query(
                    Contacts.CONTENT_URI, PROJECTION_ID_ONLY, null, null, null);
            if (cursor != null) {
                try {
                    return cursor.getCount();
                } finally {
                    cursor.close();
                }
            }
        } catch (final SQLiteException e) {
            Log.e(TAG, "SQLiteException in the remote Contacts process.", e);
        }
        return 0;
    }

    /**
     * Adds the words in a name (e.g., firstname/lastname) to the binary dictionary along with their
     * bigrams depending on locale.
     */
    private void addName(final String name) {
        int len = StringUtils.codePointCount(name);
        String prevWord = null;
        // TODO: Better tokenization for non-Latin writing systems
        for (int i = 0; i < len; i++) {
            if (Character.isLetter(name.codePointAt(i))) {
                int end = getWordEndPosition(name, len, i);
                String word = name.substring(i, end);
                i = end - 1;
                // Don't add single letter words, possibly confuses
                // capitalization of i.
                final int wordLen = StringUtils.codePointCount(word);
                if (wordLen < MAX_WORD_LENGTH && wordLen > 1) {
                    if (DEBUG) {
                        Log.d(TAG, "addName " + name + ", " + word + ", " + prevWord);
                    }
                    super.addWord(word, null /* shortcut */, FREQUENCY_FOR_CONTACTS,
                            0 /* shortcutFreq */, false /* isNotAWord */);
                    if (!TextUtils.isEmpty(prevWord)) {
                        if (mUseFirstLastBigrams) {
                            super.addBigram(prevWord, word, FREQUENCY_FOR_CONTACTS_BIGRAM,
                                    0 /* lastModifiedTime */);
                        }
                    }
                    prevWord = word;
                }
            }
        }
    }

    /**
     * Returns the index of the last letter in the word, starting from position startIndex.
     */
    private static int getWordEndPosition(final String string, final int len,
            final int startIndex) {
        int end;
        int cp = 0;
        for (end = startIndex + 1; end < len; end += Character.charCount(cp)) {
            cp = string.codePointAt(end);
            if (!(cp == Constants.CODE_DASH || cp == Constants.CODE_SINGLE_QUOTE
                    || Character.isLetter(cp))) {
                break;
            }
        }
        return end;
    }

    @Override
    protected boolean needsToReloadBeforeWriting() {
        return true;
    }

    @Override
    protected boolean hasContentChanged() {
        final long startTime = SystemClock.uptimeMillis();
        final int contactCount = getContactCount();
        if (contactCount > MAX_CONTACT_COUNT) {
            // If there are too many contacts then return false. In this rare case it is impossible
            // to include all of them anyways and the cost of rebuilding the dictionary is too high.
            // TODO: Sort and check only the MAX_CONTACT_COUNT most recent contacts?
            return false;
        }
        if (contactCount != sContactCountAtLastRebuild) {
            if (DEBUG) {
                Log.d(TAG, "Contact count changed: " + sContactCountAtLastRebuild + " to "
                        + contactCount);
            }
            return true;
        }
        // Check all contacts since it's not possible to find out which names have changed.
        // This is needed because it's possible to receive extraneous onChange events even when no
        // name has changed.
        Cursor cursor = mContext.getContentResolver().query(
                Contacts.CONTENT_URI, PROJECTION, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        String name = cursor.getString(INDEX_NAME);
                        if (isValidName(name) && !isNameInDictionary(name)) {
                            if (DEBUG) {
                                Log.d(TAG, "Contact name missing: " + name + " (runtime = "
                                        + (SystemClock.uptimeMillis() - startTime) + " ms)");
                            }
                            return true;
                        }
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        if (DEBUG) {
            Log.d(TAG, "No contacts changed. (runtime = " + (SystemClock.uptimeMillis() - startTime)
                    + " ms)");
        }
        return false;
    }

    private static boolean isValidName(final String name) {
        if (name != null && -1 == name.indexOf(Constants.CODE_COMMERCIAL_AT)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the words in a name are in the current binary dictionary.
     */
    private boolean isNameInDictionary(final String name) {
        int len = StringUtils.codePointCount(name);
        String prevWord = null;
        for (int i = 0; i < len; i++) {
            if (Character.isLetter(name.codePointAt(i))) {
                int end = getWordEndPosition(name, len, i);
                String word = name.substring(i, end);
                i = end - 1;
                final int wordLen = StringUtils.codePointCount(word);
                if (wordLen < MAX_WORD_LENGTH && wordLen > 1) {
                    if (!TextUtils.isEmpty(prevWord) && mUseFirstLastBigrams) {
                        if (!super.isValidBigramLocked(prevWord, word)) {
                            return false;
                        }
                    } else {
                        if (!super.isValidWordLocked(word)) {
                            return false;
                        }
                    }
                    prevWord = word;
                }
            }
        }
        return true;
    }
}
