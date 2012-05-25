/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.makedict.BinaryDictInputOutput;
import com.android.inputmethod.latin.makedict.FusionDictionary;
import com.android.inputmethod.latin.makedict.FusionDictionary.Node;
import com.android.inputmethod.latin.makedict.FusionDictionary.WeightedString;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract base class for an expandable dictionary that can be created and updated dynamically
 * during runtime. When updated it automatically generates a new binary dictionary to handle future
 * queries in native code. This binary dictionary is written to internal storage, and potentially
 * shared across multiple ExpandableBinaryDictionary instances. Updates to each dictionary filename
 * are controlled across multiple instances to ensure that only one instance can update the same
 * dictionary at the same time.
 */
abstract public class ExpandableBinaryDictionary extends Dictionary {

    /** Used for Log actions from this class */
    private static final String TAG = ExpandableBinaryDictionary.class.getSimpleName();

    /** Whether to print debug output to log */
    private static boolean DEBUG = false;

    /**
     * The maximum length of a word in this dictionary. This is the same value as the binary
     * dictionary.
     */
    protected static final int MAX_WORD_LENGTH = BinaryDictionary.MAX_WORD_LENGTH;

    /**
     * A static map of locks, each of which controls access to a single binary dictionary file. They
     * ensure that only one instance can update the same dictionary at the same time. The key for
     * this map is the filename and the value is the shared dictionary controller associated with
     * that filename.
     */
    private static final HashMap<String, DictionaryController> sSharedDictionaryControllers =
            new HashMap<String, DictionaryController>();

    /** The application context. */
    protected final Context mContext;

    /**
     * The binary dictionary generated dynamically from the fusion dictionary. This is used to
     * answer unigram and bigram queries.
     */
    private BinaryDictionary mBinaryDictionary;

    /** The expandable fusion dictionary used to generate the binary dictionary. */
    private FusionDictionary mFusionDictionary;

    /** The dictionary type id. */
    public final int mDicTypeId;

    /**
     * The name of this dictionary, used as the filename for storing the binary dictionary. Multiple
     * dictionary instances with the same filename is supported, with access controlled by
     * DictionaryController.
     */
    private final String mFilename;

    /** Controls access to the shared binary dictionary file across multiple instances. */
    private final DictionaryController mSharedDictionaryController;

    /** Controls access to the local binary dictionary for this instance. */
    private final DictionaryController mLocalDictionaryController = new DictionaryController();

    /**
     * Abstract method for loading the unigrams and bigrams of a given dictionary in a background
     * thread.
     */
    protected abstract void loadDictionaryAsync();

    /**
     * Indicates that the source dictionary content has changed and a rebuild of the binary file is
     * required. If it returns false, the next reload will only read the current binary dictionary
     * from file. Note that the shared binary dictionary is locked when this is called.
     */
    protected abstract boolean hasContentChanged();

    /**
     * Gets the shared dictionary controller for the given filename.
     */
    private static synchronized DictionaryController getSharedDictionaryController(
            String filename) {
        DictionaryController controller = sSharedDictionaryControllers.get(filename);
        if (controller == null) {
            controller = new DictionaryController();
            sSharedDictionaryControllers.put(filename, controller);
        }
        return controller;
    }

    /**
     * Creates a new expandable binary dictionary.
     *
     * @param context The application context of the parent.
     * @param filename The filename for this binary dictionary. Multiple dictionaries with the same
     *        filename is supported.
     * @param dictType The type of this dictionary.
     */
    public ExpandableBinaryDictionary(
            final Context context, final String filename, final int dictType) {
        mDicTypeId = dictType;
        mFilename = filename;
        mContext = context;
        mBinaryDictionary = null;
        mSharedDictionaryController = getSharedDictionaryController(filename);
        clearFusionDictionary();
    }

    protected static String getFilenameWithLocale(final String name, final String localeStr) {
        return name + "." + localeStr + ".dict";
    }

    /**
     * Closes and cleans up the binary dictionary.
     */
    @Override
    public void close() {
        // Ensure that no other threads are accessing the local binary dictionary.
        mLocalDictionaryController.lock();
        try {
            if (mBinaryDictionary != null) {
                mBinaryDictionary.close();
                mBinaryDictionary = null;
            }
        } finally {
            mLocalDictionaryController.unlock();
        }
    }

    /**
     * Clears the fusion dictionary on the Java side. Note: Does not modify the binary dictionary on
     * the native side.
     */
    public void clearFusionDictionary() {
        mFusionDictionary = new FusionDictionary(new Node(),
                new FusionDictionary.DictionaryOptions(new HashMap<String, String>(), false, 
                        false));
    }

    /**
     * Adds a word unigram to the fusion dictionary. Call updateBinaryDictionary when all changes
     * are done to update the binary dictionary.
     */
    // TODO: Create "cache dictionary" to cache fresh words for frequently updated dictionaries,
    // considering performance regression.
    protected void addWord(final String word, final String shortcutTarget, final int frequency) {
        if (shortcutTarget == null) {
            mFusionDictionary.add(word, frequency, null);
        } else {
            // TODO: Do this in the subclass, with this class taking an arraylist.
            final ArrayList<WeightedString> shortcutTargets = new ArrayList<WeightedString>();
            shortcutTargets.add(new WeightedString(shortcutTarget, frequency));
            mFusionDictionary.add(word, frequency, shortcutTargets);
        }
    }

    /**
     * Sets a word bigram in the fusion dictionary. Call updateBinaryDictionary when all changes are
     * done to update the binary dictionary.
     */
    // TODO: Create "cache dictionary" to cache fresh bigrams for frequently updated dictionaries,
    // considering performance regression.
    protected void setBigram(final String prevWord, final String word, final int frequency) {
        mFusionDictionary.setBigram(prevWord, word, frequency);
    }

    @Override
    public void getWords(final WordComposer codes, final CharSequence prevWordForBigrams,
            final WordCallback callback, final ProximityInfo proximityInfo) {
        asyncReloadDictionaryIfRequired();
        getWordsInner(codes, prevWordForBigrams, callback, proximityInfo);
    }

    protected final void getWordsInner(final WordComposer codes,
            final CharSequence prevWordForBigrams, final WordCallback callback,
            final ProximityInfo proximityInfo) {
        // Ensure that there are no concurrent calls to getWords. If there are, do nothing and
        // return.
        if (mLocalDictionaryController.tryLock()) {
            try {
                if (mBinaryDictionary != null) {
                    mBinaryDictionary.getWords(codes, prevWordForBigrams, callback, proximityInfo);
                }
            } finally {
                mLocalDictionaryController.unlock();
            }
        }
    }

    @Override
    public void getBigrams(final WordComposer codes, final CharSequence previousWord,
            final WordCallback callback) {
        asyncReloadDictionaryIfRequired();
        getBigramsInner(codes, previousWord, callback);
    }

    protected void getBigramsInner(final WordComposer codes, final CharSequence previousWord,
            final WordCallback callback) {
        if (mLocalDictionaryController.tryLock()) {
            try {
                if (mBinaryDictionary != null) {
                    mBinaryDictionary.getBigrams(codes, previousWord, callback);
                }
            } finally {
                mLocalDictionaryController.unlock();
            }
        }
    }

    @Override
    public boolean isValidWord(final CharSequence word) {
        asyncReloadDictionaryIfRequired();
        return isValidWordInner(word);
    }

    protected boolean isValidWordInner(final CharSequence word) {
        if (mLocalDictionaryController.tryLock()) {
            try {
                return isValidWordLocked(word);
            } finally {
                mLocalDictionaryController.unlock();
            }
        }
        return false;
    }

    protected boolean isValidWordLocked(final CharSequence word) {
        if (mBinaryDictionary == null) return false;
        return mBinaryDictionary.isValidWord(word);
    }

    protected boolean isValidBigram(final CharSequence word1, final CharSequence word2) {
        if (mBinaryDictionary == null) return false;
        return mBinaryDictionary.isValidBigram(word1, word2);
    }

    protected boolean isValidBigramInner(final CharSequence word1, final CharSequence word2) {
        if (mLocalDictionaryController.tryLock()) {
            try {
                return isValidBigramLocked(word1, word2);
            } finally {
                mLocalDictionaryController.unlock();
            }
        }
        return false;
    }

    protected boolean isValidBigramLocked(final CharSequence word1, final CharSequence word2) {
        if (mBinaryDictionary == null) return false;
        return mBinaryDictionary.isValidBigram(word1, word2);
    }

    /**
     * Load the current binary dictionary from internal storage in a background thread. If no binary
     * dictionary exists, this method will generate one.
     */
    protected void loadDictionary() {
        mLocalDictionaryController.mLastUpdateRequestTime = SystemClock.uptimeMillis();
        asyncReloadDictionaryIfRequired();
    }

    /**
     * Loads the current binary dictionary from internal storage. Assumes the dictionary file
     * exists.
     */
    protected void loadBinaryDictionary() {
        if (DEBUG) {
            Log.d(TAG, "Loading binary dictionary: " + mFilename + " request="
                    + mSharedDictionaryController.mLastUpdateRequestTime + " update="
                    + mSharedDictionaryController.mLastUpdateTime);
        }

        final File file = new File(mContext.getFilesDir(), mFilename);
        final String filename = file.getAbsolutePath();
        final long length = file.length();

        // Build the new binary dictionary
        final BinaryDictionary newBinaryDictionary =
                new BinaryDictionary(mContext, filename, 0, length, true /* useFullEditDistance */,
                        null);

        if (mBinaryDictionary != null) {
            // Ensure all threads accessing the current dictionary have finished before swapping in
            // the new one.
            final BinaryDictionary oldBinaryDictionary = mBinaryDictionary;
            mLocalDictionaryController.lock();
            mBinaryDictionary = newBinaryDictionary;
            mLocalDictionaryController.unlock();
            oldBinaryDictionary.close();
        } else {
            mBinaryDictionary = newBinaryDictionary;
        }
    }

    /**
     * Generates and writes a new binary dictionary based on the contents of the fusion dictionary.
     */
    private void generateBinaryDictionary() {
        if (DEBUG) {
            Log.d(TAG, "Generating binary dictionary: " + mFilename + " request="
                    + mSharedDictionaryController.mLastUpdateRequestTime + " update="
                    + mSharedDictionaryController.mLastUpdateTime);
        }

        loadDictionaryAsync();

        final String tempFileName = mFilename + ".temp";
        final File file = new File(mContext.getFilesDir(), mFilename);
        final File tempFile = new File(mContext.getFilesDir(), tempFileName);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tempFile);
            BinaryDictInputOutput.writeDictionaryBinary(out, mFusionDictionary, 1);
            out.flush();
            out.close();
            tempFile.renameTo(file);
            clearFusionDictionary();
        } catch (IOException e) {
            Log.e(TAG, "IO exception while writing file: " + e);
        } catch (UnsupportedFormatException e) {
            Log.e(TAG, "Unsupported format: " + e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Marks that the dictionary is out of date and requires a reload.
     *
     * @param requiresRebuild Indicates that the source dictionary content has changed and a rebuild
     *        of the binary file is required. If not true, the next reload process will only read
     *        the current binary dictionary from file.
     */
    protected void setRequiresReload(final boolean requiresRebuild) {
        final long time = SystemClock.uptimeMillis();
        mLocalDictionaryController.mLastUpdateRequestTime = time;
        mSharedDictionaryController.mLastUpdateRequestTime = time;
        if (DEBUG) {
            Log.d(TAG, "Reload request: " + mFilename + ": request=" + time + " update="
                    + mSharedDictionaryController.mLastUpdateTime);
        }
    }

    /**
     * Reloads the dictionary if required. Reload will occur asynchronously in a separate thread.
     */
    void asyncReloadDictionaryIfRequired() {
        if (!isReloadRequired()) return;
        if (DEBUG) {
            Log.d(TAG, "Starting AsyncReloadDictionaryTask: " + mFilename);
        }
        new AsyncReloadDictionaryTask().start();
    }

    /**
     * Reloads the dictionary if required.
     */
    protected final void syncReloadDictionaryIfRequired() {
        if (!isReloadRequired()) return;
        syncReloadDictionaryInternal();
    }

    /**
     * Returns whether a dictionary reload is required.
     */
    private boolean isReloadRequired() {
        return mBinaryDictionary == null || mLocalDictionaryController.isOutOfDate();
    }

    /**
     * Reloads the dictionary. Access is controlled on a per dictionary file basis and supports
     * concurrent calls from multiple instances that share the same dictionary file.
     */
    private final void syncReloadDictionaryInternal() {
        // Ensure that only one thread attempts to read or write to the shared binary dictionary
        // file at the same time.
        mSharedDictionaryController.lock();
        try {
            final long time = SystemClock.uptimeMillis();
            final boolean dictionaryFileExists = dictionaryFileExists();
            if (mSharedDictionaryController.isOutOfDate() || !dictionaryFileExists) {
                // If the shared dictionary file does not exist or is out of date, the first
                // instance that acquires the lock will generate a new one.
                if (hasContentChanged() || !dictionaryFileExists) {
                    // If the source content has changed or the dictionary does not exist, rebuild
                    // the binary dictionary. Empty dictionaries are supported (in the case where
                    // loadDictionaryAsync() adds nothing) in order to provide a uniform framework.
                    mSharedDictionaryController.mLastUpdateTime = time;
                    generateBinaryDictionary();
                    loadBinaryDictionary();
                } else {
                    // If not, the reload request was unnecessary so revert LastUpdateRequestTime
                    // to LastUpdateTime.
                    mSharedDictionaryController.mLastUpdateRequestTime =
                            mSharedDictionaryController.mLastUpdateTime;
                }
            } else if (mBinaryDictionary == null || mLocalDictionaryController.mLastUpdateTime
                    < mSharedDictionaryController.mLastUpdateTime) {
                // Otherwise, if the local dictionary is older than the shared dictionary, load the
                // shared dictionary.
                loadBinaryDictionary();
            }
            mLocalDictionaryController.mLastUpdateTime = time;
        } finally {
            mSharedDictionaryController.unlock();
        }
    }

    // TODO: cache the file's existence so that we avoid doing a disk access each time.
    private boolean dictionaryFileExists() {
        final File file = new File(mContext.getFilesDir(), mFilename);
        return file.exists();
    }

    /**
     * Thread class for asynchronously reloading and rewriting the binary dictionary.
     */
    private class AsyncReloadDictionaryTask extends Thread {
        @Override
        public void run() {
            syncReloadDictionaryInternal();
        }
    }

    /**
     * Lock for controlling access to a given binary dictionary and for tracking whether the
     * dictionary is out of date. Can be shared across multiple dictionary instances that access the
     * same filename.
     */
    private static class DictionaryController extends ReentrantLock {
        private volatile long mLastUpdateTime = 0;
        private volatile long mLastUpdateRequestTime = 0;

        private boolean isOutOfDate() {
            return (mLastUpdateRequestTime > mLastUpdateTime);
        }
    }
}
