/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.UserDictionary.Words;
import android.text.TextUtils;

import com.android.inputmethod.keyboard.ProximityInfo;

import java.util.Arrays;

public class UserDictionary extends ExpandableDictionary {

    private static final String[] PROJECTION_QUERY = {
        Words.WORD,
        Words.FREQUENCY,
    };

    private static final String[] PROJECTION_ADD = {
        Words._ID,
        Words.FREQUENCY,
        Words.LOCALE,
    };

    private ContentObserver mObserver;
    final private String mLocale;
    final private boolean mAlsoUseMoreRestrictiveLocales;

    public UserDictionary(final Context context, final String locale) {
        this(context, locale, false);
    }

    public UserDictionary(final Context context, final String locale,
            final boolean alsoUseMoreRestrictiveLocales) {
        super(context, Suggest.DIC_USER);
        if (null == locale) throw new NullPointerException(); // Catch the error earlier
        mLocale = locale;
        mAlsoUseMoreRestrictiveLocales = alsoUseMoreRestrictiveLocales;
        // Perform a managed query. The Activity will handle closing and re-querying the cursor
        // when needed.
        ContentResolver cres = context.getContentResolver();

        mObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean self) {
                setRequiresReload(true);
            }
        };
        cres.registerContentObserver(Words.CONTENT_URI, true, mObserver);

        loadDictionary();
    }

    @Override
    public synchronized void close() {
        if (mObserver != null) {
            getContext().getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.close();
    }

    @Override
    public void loadDictionaryAsync() {
        // Split the locale. For example "en" => ["en"], "de_DE" => ["de", "DE"],
        // "en_US_foo_bar_qux" => ["en", "US", "foo_bar_qux"] because of the limit of 3.
        // This is correct for locale processing.
        // For this example, we'll look at the "en_US_POSIX" case.
        final String[] localeElements =
                TextUtils.isEmpty(mLocale) ? new String[] {} : mLocale.split("_", 3);
        final int length = localeElements.length;

        final StringBuilder request = new StringBuilder("(locale is NULL)");
        String localeSoFar = "";
        // At start, localeElements = ["en", "US", "POSIX"] ; localeSoFar = "" ;
        // and request = "(locale is NULL)"
        for (int i = 0; i < length; ++i) {
            // i | localeSoFar    | localeElements
            // 0 | ""             | ["en", "US", "POSIX"]
            // 1 | "en_"          | ["en", "US", "POSIX"]
            // 2 | "en_US_"       | ["en", "en_US", "POSIX"]
            localeElements[i] = localeSoFar + localeElements[i];
            localeSoFar = localeElements[i] + "_";
            // i | request
            // 0 | "(locale is NULL)"
            // 1 | "(locale is NULL) or (locale=?)"
            // 2 | "(locale is NULL) or (locale=?) or (locale=?)"
            request.append(" or (locale=?)");
        }
        // At the end, localeElements = ["en", "en_US", "en_US_POSIX"]; localeSoFar = en_US_POSIX_"
        // and request = "(locale is NULL) or (locale=?) or (locale=?) or (locale=?)"

        final String[] requestArguments;
        // If length == 3, we already have all the arguments we need (common prefix is meaningless
        // inside variants
        if (mAlsoUseMoreRestrictiveLocales && length < 3) {
            request.append(" or (locale like ?)");
            // The following creates an array with one more (null) position
            final String[] localeElementsWithMoreRestrictiveLocalesIncluded =
                    Arrays.copyOf(localeElements, length + 1);
            localeElementsWithMoreRestrictiveLocalesIncluded[length] =
                    localeElements[length - 1] + "_%";
            requestArguments = localeElementsWithMoreRestrictiveLocalesIncluded;
            // If for example localeElements = ["en"]
            // then requestArguments = ["en", "en_%"]
            // and request = (locale is NULL) or (locale=?) or (locale like ?)
            // If localeElements = ["en", "en_US"]
            // then requestArguments = ["en", "en_US", "en_US_%"]
        } else {
            requestArguments = localeElements;
        }
        final Cursor cursor = getContext().getContentResolver()
                .query(Words.CONTENT_URI, PROJECTION_QUERY, request.toString(),
                        requestArguments, null);
        addWords(cursor);
    }

    public boolean isEnabled() {
        final ContentResolver cr = getContext().getContentResolver();
        final ContentProviderClient client = cr.acquireContentProviderClient(Words.CONTENT_URI);
        if (client != null) {
            client.release();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a word to the dictionary and makes it persistent.
     * @param word the word to add. If the word is capitalized, then the dictionary will
     * recognize it as a capitalized word when searched.
     * @param frequency the frequency of occurrence of the word. A frequency of 255 is considered
     * the highest.
     * @TODO use a higher or float range for frequency
     */
    @Override
    public synchronized void addWord(final String word, final int frequency) {
        // Force load the dictionary here synchronously
        if (getRequiresReload()) loadDictionaryAsync();
        // Safeguard against adding long words. Can cause stack overflow.
        if (word.length() >= getMaxWordLength()) return;

        super.addWord(word, frequency);

        // Update the user dictionary provider
        final ContentValues values = new ContentValues(5);
        values.put(Words.WORD, word);
        values.put(Words.FREQUENCY, frequency);
        values.put(Words.LOCALE, mLocale);
        values.put(Words.APP_ID, 0);

        final ContentResolver contentResolver = getContext().getContentResolver();
        final ContentProviderClient client =
                contentResolver.acquireContentProviderClient(Words.CONTENT_URI);
        if (null == client) return;
        new Thread("addWord") {
            @Override
            public void run() {
                try {
                    final Cursor cursor = client.query(Words.CONTENT_URI, PROJECTION_ADD,
                            "word=? and ((locale IS NULL) or (locale=?))",
                                    new String[] { word, mLocale }, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        final String locale = cursor.getString(cursor.getColumnIndex(Words.LOCALE));
                        // If locale is null, we will not override the entry.
                        if (locale != null && locale.equals(mLocale.toString())) {
                            final long id = cursor.getLong(cursor.getColumnIndex(Words._ID));
                            final Uri uri =
                                    Uri.withAppendedPath(Words.CONTENT_URI, Long.toString(id));
                            // Update the entry with new frequency value.
                            client.update(uri, values, null, null);
                        }
                    } else {
                        // Insert new entry.
                        client.insert(Words.CONTENT_URI, values);
                    }
                } catch (RemoteException e) {
                    // If we come here, the activity is already about to be killed, and we
                    // have no means of contacting the content provider any more.
                    // See ContentResolver#insert, inside the catch(){}
                }
            }
        }.start();

        // In case the above does a synchronous callback of the change observer
        setRequiresReload(false);
    }

    @Override
    public synchronized void getWords(final WordComposer codes, final WordCallback callback,
            final ProximityInfo proximityInfo) {
        super.getWords(codes, callback, proximityInfo);
    }

    @Override
    public synchronized boolean isValidWord(CharSequence word) {
        return super.isValidWord(word);
    }

    private void addWords(Cursor cursor) {
        clearDictionary();
        if (cursor == null) return;
        final int maxWordLength = getMaxWordLength();
        if (cursor.moveToFirst()) {
            final int indexWord = cursor.getColumnIndex(Words.WORD);
            final int indexFrequency = cursor.getColumnIndex(Words.FREQUENCY);
            while (!cursor.isAfterLast()) {
                String word = cursor.getString(indexWord);
                int frequency = cursor.getInt(indexFrequency);
                // Safeguard against adding really long words. Stack may overflow due
                // to recursion
                if (word.length() < maxWordLength) {
                    super.addWord(word, frequency);
                }
                cursor.moveToNext();
            }
        }
        cursor.close();
    }
}
