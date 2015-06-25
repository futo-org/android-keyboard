/**
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.dictionarypack;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.utils.DebugLogUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

/**
 * Provider for dictionaries.
 *
 * This class is a ContentProvider exposing all available dictionary data as managed by
 * the dictionary pack.
 */
public final class DictionaryProvider extends ContentProvider {
    private static final String TAG = DictionaryProvider.class.getSimpleName();
    public static final boolean DEBUG = false;

    public static final Uri CONTENT_URI =
            Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + DictionaryPackConstants.AUTHORITY);
    private static final String QUERY_PARAMETER_MAY_PROMPT_USER = "mayPrompt";
    private static final String QUERY_PARAMETER_TRUE = "true";
    private static final String QUERY_PARAMETER_DELETE_RESULT = "result";
    private static final String QUERY_PARAMETER_FAILURE = "failure";
    public static final String QUERY_PARAMETER_PROTOCOL_VERSION = "protocol";
    private static final int NO_MATCH = 0;
    private static final int DICTIONARY_V1_WHOLE_LIST = 1;
    private static final int DICTIONARY_V1_DICT_INFO = 2;
    private static final int DICTIONARY_V2_METADATA = 3;
    private static final int DICTIONARY_V2_WHOLE_LIST = 4;
    private static final int DICTIONARY_V2_DICT_INFO = 5;
    private static final int DICTIONARY_V2_DATAFILE = 6;
    private static final UriMatcher sUriMatcherV1 = new UriMatcher(NO_MATCH);
    private static final UriMatcher sUriMatcherV2 = new UriMatcher(NO_MATCH);
    static
    {
        sUriMatcherV1.addURI(DictionaryPackConstants.AUTHORITY, "list", DICTIONARY_V1_WHOLE_LIST);
        sUriMatcherV1.addURI(DictionaryPackConstants.AUTHORITY, "*", DICTIONARY_V1_DICT_INFO);
        sUriMatcherV2.addURI(DictionaryPackConstants.AUTHORITY, "*/metadata",
                DICTIONARY_V2_METADATA);
        sUriMatcherV2.addURI(DictionaryPackConstants.AUTHORITY, "*/list", DICTIONARY_V2_WHOLE_LIST);
        sUriMatcherV2.addURI(DictionaryPackConstants.AUTHORITY, "*/dict/*",
                DICTIONARY_V2_DICT_INFO);
        sUriMatcherV2.addURI(DictionaryPackConstants.AUTHORITY, "*/datafile/*",
                DICTIONARY_V2_DATAFILE);
    }

    // MIME types for dictionary and dictionary list, as required by ContentProvider contract.
    public static final String DICT_LIST_MIME_TYPE =
            "vnd.android.cursor.item/vnd.google.dictionarylist";
    public static final String DICT_DATAFILE_MIME_TYPE =
            "vnd.android.cursor.item/vnd.google.dictionary";

    public static final String ID_CATEGORY_SEPARATOR = ":";

    private static final class WordListInfo {
        public final String mId;
        public final String mLocale;
        public final String mRawChecksum;
        public final int mMatchLevel;
        public WordListInfo(final String id, final String locale, final String rawChecksum,
                final int matchLevel) {
            mId = id;
            mLocale = locale;
            mRawChecksum = rawChecksum;
            mMatchLevel = matchLevel;
        }
    }

    /**
     * A cursor for returning a list of file ids from a List of strings.
     *
     * This simulates only the necessary methods. It has no error handling to speak of,
     * and does not support everything a database does, only a few select necessary methods.
     */
    private static final class ResourcePathCursor extends AbstractCursor {

        // Column names for the cursor returned by this content provider.
        static private final String[] columnNames = { MetadataDbHelper.WORDLISTID_COLUMN,
                MetadataDbHelper.LOCALE_COLUMN, MetadataDbHelper.RAW_CHECKSUM_COLUMN };

        // The list of word lists served by this provider that match the client request.
        final WordListInfo[] mWordLists;
        // Note : the cursor also uses mPos, which is defined in AbstractCursor.

        public ResourcePathCursor(final Collection<WordListInfo> wordLists) {
            // Allocating a 0-size WordListInfo here allows the toArray() method
            // to ensure we have a strongly-typed array. It's thrown out. That's
            // what the documentation of #toArray says to do in order to get a
            // new strongly typed array of the correct size.
            mWordLists = wordLists.toArray(new WordListInfo[0]);
            mPos = 0;
        }

        @Override
        public String[] getColumnNames() {
            return columnNames;
        }

        @Override
        public int getCount() {
            return mWordLists.length;
        }

        @Override public double getDouble(int column) { return 0; }
        @Override public float getFloat(int column) { return 0; }
        @Override public int getInt(int column) { return 0; }
        @Override public short getShort(int column) { return 0; }
        @Override public long getLong(int column) { return 0; }

        @Override public String getString(final int column) {
            switch (column) {
                case 0: return mWordLists[mPos].mId;
                case 1: return mWordLists[mPos].mLocale;
                case 2: return mWordLists[mPos].mRawChecksum;
                default : return null;
            }
        }

        @Override
        public boolean isNull(final int column) {
            if (mPos >= mWordLists.length) return true;
            return column != 0;
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private static int matchUri(final Uri uri) {
        int protocolVersion = 1;
        final String protocolVersionArg = uri.getQueryParameter(QUERY_PARAMETER_PROTOCOL_VERSION);
        if ("2".equals(protocolVersionArg)) protocolVersion = 2;
        switch (protocolVersion) {
            case 1: return sUriMatcherV1.match(uri);
            case 2: return sUriMatcherV2.match(uri);
            default: return NO_MATCH;
        }
    }

    private static String getClientId(final Uri uri) {
        int protocolVersion = 1;
        final String protocolVersionArg = uri.getQueryParameter(QUERY_PARAMETER_PROTOCOL_VERSION);
        if ("2".equals(protocolVersionArg)) protocolVersion = 2;
        switch (protocolVersion) {
            case 1: return null; // In protocol 1, the client ID is always null.
            case 2: return uri.getPathSegments().get(0);
            default: return null;
        }
    }

    /**
     * Returns the MIME type of the content associated with an Uri
     *
     * @see android.content.ContentProvider#getType(android.net.Uri)
     *
     * @param uri the URI of the content the type of which should be returned.
     * @return the MIME type, or null if the URL is not recognized.
     */
    @Override
    public String getType(final Uri uri) {
        PrivateLog.log("Asked for type of : " + uri);
        final int match = matchUri(uri);
        switch (match) {
            case NO_MATCH: return null;
            case DICTIONARY_V1_WHOLE_LIST:
            case DICTIONARY_V1_DICT_INFO:
            case DICTIONARY_V2_WHOLE_LIST:
            case DICTIONARY_V2_DICT_INFO: return DICT_LIST_MIME_TYPE;
            case DICTIONARY_V2_DATAFILE: return DICT_DATAFILE_MIME_TYPE;
            default: return null;
        }
    }

    /**
     * Query the provider for dictionary files.
     *
     * This version dispatches the query according to the protocol version found in the
     * ?protocol= query parameter. If absent or not well-formed, it defaults to 1.
     * @see android.content.ContentProvider#query(Uri, String[], String, String[], String)
     *
     * @param uri a content uri (see sUriMatcherV{1,2} at the top of this file for format)
     * @param projection ignored. All columns are always returned.
     * @param selection ignored.
     * @param selectionArgs ignored.
     * @param sortOrder ignored. The results are always returned in no particular order.
     * @return a cursor matching the uri, or null if the URI was not recognized.
     */
    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection,
            final String[] selectionArgs, final String sortOrder) {
        DebugLogUtils.l("Uri =", uri);
        PrivateLog.log("Query : " + uri);
        final String clientId = getClientId(uri);
        final int match = matchUri(uri);
        switch (match) {
            case DICTIONARY_V1_WHOLE_LIST:
            case DICTIONARY_V2_WHOLE_LIST:
                final Cursor c = MetadataDbHelper.queryDictionaries(getContext(), clientId);
                DebugLogUtils.l("List of dictionaries with count", c.getCount());
                PrivateLog.log("Returned a list of " + c.getCount() + " items");
                return c;
            case DICTIONARY_V2_DICT_INFO:
                // In protocol version 2, we return null if the client is unknown. Otherwise
                // we behave exactly like for protocol 1.
                if (!MetadataDbHelper.isClientKnown(getContext(), clientId)) return null;
                // Fall through
            case DICTIONARY_V1_DICT_INFO:
                final String locale = uri.getLastPathSegment();
                final Collection<WordListInfo> dictFiles =
                        getDictionaryWordListsForLocale(clientId, locale);
                // TODO: pass clientId to the following function
                DictionaryService.updateNowIfNotUpdatedInAVeryLongTime(getContext());
                if (null != dictFiles && dictFiles.size() > 0) {
                    PrivateLog.log("Returned " + dictFiles.size() + " files");
                    return new ResourcePathCursor(dictFiles);
                }
                PrivateLog.log("No dictionary files for this URL");
                return new ResourcePathCursor(Collections.<WordListInfo>emptyList());
            // V2_METADATA and V2_DATAFILE are not supported for query()
            default:
                return null;
        }
    }

    /**
     * Helper method to get the wordlist metadata associated with a wordlist ID.
     *
     * @param clientId the ID of the client
     * @param wordlistId the ID of the wordlist for which to get the metadata.
     * @return the metadata for this wordlist ID, or null if none could be found.
     */
    private ContentValues getWordlistMetadataForWordlistId(final String clientId,
            final String wordlistId) {
        final Context context = getContext();
        if (TextUtils.isEmpty(wordlistId)) return null;
        final SQLiteDatabase db = MetadataDbHelper.getDb(context, clientId);
        return MetadataDbHelper.getInstalledOrDeletingWordListContentValuesByWordListId(
                db, wordlistId);
    }

    /**
     * Opens an asset file for an URI.
     *
     * Called by {@link android.content.ContentResolver#openAssetFileDescriptor(Uri, String)} or
     * {@link android.content.ContentResolver#openInputStream(Uri)} from a client requesting a
     * dictionary.
     * @see android.content.ContentProvider#openAssetFile(Uri, String)
     *
     * @param uri the URI the file is for.
     * @param mode the mode to read the file. MUST be "r" for readonly.
     * @return the descriptor, or null if the file is not found or if mode is not equals to "r".
     */
    @Override
    public AssetFileDescriptor openAssetFile(final Uri uri, final String mode) {
        if (null == mode || !"r".equals(mode)) return null;

        final int match = matchUri(uri);
        if (DICTIONARY_V1_DICT_INFO != match && DICTIONARY_V2_DATAFILE != match) {
            // Unsupported URI for openAssetFile
            Log.w(TAG, "Unsupported URI for openAssetFile : " + uri);
            return null;
        }
        final String wordlistId = uri.getLastPathSegment();
        final String clientId = getClientId(uri);
        final ContentValues wordList = getWordlistMetadataForWordlistId(clientId, wordlistId);

        if (null == wordList) return null;

        try {
            final int status = wordList.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
            if (MetadataDbHelper.STATUS_DELETING == status) {
                // This will return an empty file (R.raw.empty points at an empty dictionary)
                // This is how we "delete" the files. It allows Android Keyboard to fake deleting
                // a default dictionary - which is actually in its assets and can't be really
                // deleted.
                final AssetFileDescriptor afd = getContext().getResources().openRawResourceFd(
                        R.raw.empty);
                return afd;
            }
            final String localFilename =
                    wordList.getAsString(MetadataDbHelper.LOCAL_FILENAME_COLUMN);
            final File f = getContext().getFileStreamPath(localFilename);
            final ParcelFileDescriptor pfd =
                    ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            return new AssetFileDescriptor(pfd, 0, pfd.getStatSize());
        } catch (FileNotFoundException e) {
            // No file : fall through and return null
        }
        return null;
    }

    /**
     * Reads the metadata and returns the collection of dictionaries for a given locale.
     *
     * Word list IDs are expected to be in the form category:manual_id. This method
     * will select only one word list for each category: the one with the most specific
     * locale matching the locale specified in the URI. The manual id serves only to
     * distinguish a word list from another for the purpose of updating, and is arbitrary
     * but may not contain a colon.
     *
     * @param clientId the ID of the client requesting the list
     * @param locale the locale for which we want the list, as a String
     * @return a collection of ids. It is guaranteed to be non-null, but may be empty.
     */
    private Collection<WordListInfo> getDictionaryWordListsForLocale(final String clientId,
            final String locale) {
        final Context context = getContext();
        final Cursor results =
                MetadataDbHelper.queryInstalledOrDeletingOrAvailableDictionaryMetadata(context,
                        clientId);
        if (null == results) {
            return Collections.<WordListInfo>emptyList();
        }
        try {
            final HashMap<String, WordListInfo> dicts = new HashMap<>();
            final int idIndex = results.getColumnIndex(MetadataDbHelper.WORDLISTID_COLUMN);
            final int localeIndex = results.getColumnIndex(MetadataDbHelper.LOCALE_COLUMN);
            final int localFileNameIndex =
                    results.getColumnIndex(MetadataDbHelper.LOCAL_FILENAME_COLUMN);
            final int rawChecksumIndex =
                    results.getColumnIndex(MetadataDbHelper.RAW_CHECKSUM_COLUMN);
            final int statusIndex = results.getColumnIndex(MetadataDbHelper.STATUS_COLUMN);
            if (results.moveToFirst()) {
                do {
                    final String wordListId = results.getString(idIndex);
                    if (TextUtils.isEmpty(wordListId)) continue;
                    final String[] wordListIdArray =
                            TextUtils.split(wordListId, ID_CATEGORY_SEPARATOR);
                    final String wordListCategory;
                    if (2 == wordListIdArray.length) {
                        // This is at the category:manual_id format.
                        wordListCategory = wordListIdArray[0];
                        // We don't need to read wordListIdArray[1] here, because it's irrelevant to
                        // word list selection - it's just a name we use to identify which data file
                        // is a newer version of which word list. We do however return the full id
                        // string for each selected word list, so in this sense we are 'using' it.
                    } else {
                        // This does not contain a colon, like the old format does. Old-format IDs
                        // always point to main dictionaries, so we force the main category upon it.
                        wordListCategory = UpdateHandler.MAIN_DICTIONARY_CATEGORY;
                    }
                    final String wordListLocale = results.getString(localeIndex);
                    final String wordListLocalFilename = results.getString(localFileNameIndex);
                    final String wordListRawChecksum = results.getString(rawChecksumIndex);
                    final int wordListStatus = results.getInt(statusIndex);
                    // Test the requested locale against this wordlist locale. The requested locale
                    // has to either match exactly or be more specific than the dictionary - a
                    // dictionary for "en" would match both a request for "en" or for "en_US", but a
                    // dictionary for "en_GB" would not match a request for "en_US". Thus if all
                    // three of "en" "en_US" and "en_GB" dictionaries are installed, a request for
                    // "en_US" would match "en" and "en_US", and a request for "en" only would only
                    // match the generic "en" dictionary. For more details, see the documentation
                    // for LocaleUtils#getMatchLevel.
                    final int matchLevel = LocaleUtils.getMatchLevel(wordListLocale, locale);
                    if (!LocaleUtils.isMatch(matchLevel)) {
                        // The locale of this wordlist does not match the required locale.
                        // Skip this wordlist and go to the next.
                        continue;
                    }
                    if (MetadataDbHelper.STATUS_INSTALLED == wordListStatus) {
                        // If the file does not exist, it has been deleted and the IME should
                        // already have it. Do not return it. However, this only applies if the
                        // word list is INSTALLED, for if it is DELETING we should return it always
                        // so that Android Keyboard can perform the actual deletion.
                        final File f = getContext().getFileStreamPath(wordListLocalFilename);
                        if (!f.isFile()) {
                            continue;
                        }
                    } else if (MetadataDbHelper.STATUS_AVAILABLE == wordListStatus) {
                        // The locale is the id for the main dictionary.
                        UpdateHandler.installIfNeverRequested(context, clientId, wordListId);
                        continue;
                    }
                    final WordListInfo currentBestMatch = dicts.get(wordListCategory);
                    if (null == currentBestMatch
                            || currentBestMatch.mMatchLevel < matchLevel) {
                        dicts.put(wordListCategory, new WordListInfo(wordListId, wordListLocale,
                                wordListRawChecksum, matchLevel));
                    }
                } while (results.moveToNext());
            }
            return Collections.unmodifiableCollection(dicts.values());
        } finally {
            results.close();
        }
    }

    /**
     * Deletes the file pointed by Uri, as returned by openAssetFile.
     *
     * @param uri the URI the file is for.
     * @param selection ignored
     * @param selectionArgs ignored
     * @return the number of files deleted (0 or 1 in the current implementation)
     * @see android.content.ContentProvider#delete(Uri, String, String[])
     */
    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs)
            throws UnsupportedOperationException {
        final int match = matchUri(uri);
        if (DICTIONARY_V1_DICT_INFO == match || DICTIONARY_V2_DATAFILE == match) {
            return deleteDataFile(uri);
        }
        if (DICTIONARY_V2_METADATA == match) {
            if (MetadataDbHelper.deleteClient(getContext(), getClientId(uri))) {
                return 1;
            }
            return 0;
        }
        // Unsupported URI for delete
        return 0;
    }

    private int deleteDataFile(final Uri uri) {
        final String wordlistId = uri.getLastPathSegment();
        final String clientId = getClientId(uri);
        final ContentValues wordList = getWordlistMetadataForWordlistId(clientId, wordlistId);
        if (null == wordList) {
            return 0;
        }
        final int status = wordList.getAsInteger(MetadataDbHelper.STATUS_COLUMN);
        final int version = wordList.getAsInteger(MetadataDbHelper.VERSION_COLUMN);
        if (MetadataDbHelper.STATUS_DELETING == status) {
            UpdateHandler.markAsDeleted(getContext(), clientId, wordlistId, version, status);
            return 1;
        }
        if (MetadataDbHelper.STATUS_INSTALLED == status) {
            final String result = uri.getQueryParameter(QUERY_PARAMETER_DELETE_RESULT);
            if (QUERY_PARAMETER_FAILURE.equals(result)) {
                if (DEBUG) {
                    Log.d(TAG,
                            "Dictionary is broken, attempting to retry download & installation.");
                }
                UpdateHandler.markAsBrokenOrRetrying(getContext(), clientId, wordlistId, version);
            }
            final String localFilename =
                    wordList.getAsString(MetadataDbHelper.LOCAL_FILENAME_COLUMN);
            final File f = getContext().getFileStreamPath(localFilename);
            // f.delete() returns true if the file was successfully deleted, false otherwise
            return f.delete() ? 1 : 0;
        }
        Log.e(TAG, "Attempt to delete a file whose status is " + status);
        return 0;
    }

    /**
     * Insert data into the provider. May be either a metadata source URL or some dictionary info.
     *
     * @param uri the designated content URI. See sUriMatcherV{1,2} for available URIs.
     * @param values the values to insert for this content uri
     * @return the URI for the newly inserted item. May be null if arguments don't allow for insert
     */
    @Override
    public Uri insert(final Uri uri, final ContentValues values)
            throws UnsupportedOperationException {
        if (null == uri || null == values) return null; // Should never happen but let's be safe
        PrivateLog.log("Insert, uri = " + uri.toString());
        final String clientId = getClientId(uri);
        switch (matchUri(uri)) {
            case DICTIONARY_V2_METADATA:
                // The values should contain a valid client ID and a valid URI for the metadata.
                // The client ID may not be null, nor may it be empty because the empty client ID
                // is reserved for internal use.
                // The metadata URI may not be null, but it may be empty if the client does not
                // want the dictionary pack to update the metadata automatically.
                MetadataDbHelper.updateClientInfo(getContext(), clientId, values);
                break;
            case DICTIONARY_V2_DICT_INFO:
                try {
                    final WordListMetadata newDictionaryMetadata =
                            WordListMetadata.createFromContentValues(
                                    MetadataDbHelper.completeWithDefaultValues(values));
                    new ActionBatch.MarkPreInstalledAction(clientId, newDictionaryMetadata)
                            .execute(getContext());
                } catch (final BadFormatException e) {
                    Log.w(TAG, "Not enough information to insert this dictionary " + values, e);
                }
                // We just received new information about the list of dictionary for this client.
                // For all intents and purposes, this is new metadata, so we should publish it
                // so that any listeners (like the Settings interface for example) can update
                // themselves.
                UpdateHandler.publishUpdateMetadataCompleted(getContext(), true);
                break;
            case DICTIONARY_V1_WHOLE_LIST:
            case DICTIONARY_V1_DICT_INFO:
                PrivateLog.log("Attempt to insert : " + uri);
                throw new UnsupportedOperationException(
                        "Insertion in the dictionary is not supported in this version");
        }
        return uri;
    }

    /**
     * Updating data is not supported, and will throw an exception.
     * @see android.content.ContentProvider#update(Uri, ContentValues, String, String[])
     * @see android.content.ContentProvider#insert(Uri, ContentValues)
     */
    @Override
    public int update(final Uri uri, final ContentValues values, final String selection,
            final String[] selectionArgs) throws UnsupportedOperationException {
        PrivateLog.log("Attempt to update : " + uri);
        throw new UnsupportedOperationException("Updating dictionary words is not supported");
    }
}
