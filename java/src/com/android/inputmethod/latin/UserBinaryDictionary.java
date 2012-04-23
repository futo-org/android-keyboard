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

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.provider.UserDictionary.Words;
import android.text.TextUtils;

import java.util.Arrays;

/**
 * An expandable dictionary that stores the words in the user unigram dictionary.
 *
 * Largely a copy of UserDictionary, will replace that class in the future.
 */
public class UserBinaryDictionary extends ExpandableBinaryDictionary {

    // TODO: use Words.SHORTCUT when it's public in the SDK
    final static String SHORTCUT = "shortcut";
    private static final String[] PROJECTION_QUERY = {
        Words.WORD,
        SHORTCUT,
        Words.FREQUENCY,
    };

    private static final String NAME = "userunigram";

    // This is not exported by the framework so we pretty much have to write it here verbatim
    private static final String ACTION_USER_DICTIONARY_INSERT =
            "com.android.settings.USER_DICTIONARY_INSERT";

    private ContentObserver mObserver;
    final private String mLocale;
    final private boolean mAlsoUseMoreRestrictiveLocales;

    public UserBinaryDictionary(final Context context, final String locale) {
        this(context, locale, false);
    }

    public UserBinaryDictionary(final Context context, final String locale,
            final boolean alsoUseMoreRestrictiveLocales) {
        super(context, getFilenameWithLocale(NAME, locale), Suggest.DIC_USER);
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
            mContext.getContentResolver().unregisterContentObserver(mObserver);
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
        final Cursor cursor = mContext.getContentResolver().query(
            Words.CONTENT_URI, PROJECTION_QUERY, request.toString(), requestArguments, null);
        try {
            addWords(cursor);
        } finally {
            if (null != cursor) cursor.close();
        }
    }

    public boolean isEnabled() {
        final ContentResolver cr = mContext.getContentResolver();
        final ContentProviderClient client = cr.acquireContentProviderClient(Words.CONTENT_URI);
        if (client != null) {
            client.release();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a word to the user dictionary and makes it persistent.
     *
     * This will call upon the system interface to do the actual work through the intent readied by
     * the system to this effect.
     *
     * @param word the word to add. If the word is capitalized, then the dictionary will
     * recognize it as a capitalized word when searched.
     * @param frequency the frequency of occurrence of the word. A frequency of 255 is considered
     * the highest.
     * @TODO use a higher or float range for frequency
     */
    public synchronized void addWordToUserDictionary(final String word, final int frequency) {
        // TODO: do something for the UI. With the following, any sufficiently long word will
        // look like it will go to the user dictionary but it won't.
        // Safeguard against adding long words. Can cause stack overflow.
        if (word.length() >= MAX_WORD_LENGTH) return;

        // TODO: Add an argument to the intent to specify the frequency.
        Intent intent = new Intent(ACTION_USER_DICTIONARY_INSERT);
        intent.putExtra(Words.WORD, word);
        intent.putExtra(Words.LOCALE, mLocale);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void addWords(Cursor cursor) {
        clearFusionDictionary();
        if (cursor == null) return;
        if (cursor.moveToFirst()) {
            final int indexWord = cursor.getColumnIndex(Words.WORD);
            final int indexShortcut = cursor.getColumnIndex(SHORTCUT);
            final int indexFrequency = cursor.getColumnIndex(Words.FREQUENCY);
            while (!cursor.isAfterLast()) {
                String word = cursor.getString(indexWord);
                String shortcut = cursor.getString(indexShortcut);
                int frequency = cursor.getInt(indexFrequency);
                // Safeguard against adding really long words.
                if (word.length() < MAX_WORD_LENGTH) {
                    super.addWord(word, null, frequency);
                }
                if (null != shortcut && shortcut.length() < MAX_WORD_LENGTH) {
                    super.addWord(shortcut, word, frequency);
                }
                cursor.moveToNext();
            }
        }
    }

    @Override
    protected boolean hasContentChanged() {
        return true;
    }
}
