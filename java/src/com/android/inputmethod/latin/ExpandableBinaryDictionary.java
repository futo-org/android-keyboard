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

import android.content.Context;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.WordProperty;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.utils.CombinedFormatUtils;
import com.android.inputmethod.latin.utils.ExecutorUtils;
import com.android.inputmethod.latin.utils.FileUtils;
import com.android.inputmethod.latin.utils.LanguageModelParam;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Abstract base class for an expandable dictionary that can be created and updated dynamically
 * during runtime. When updated it automatically generates a new binary dictionary to handle future
 * queries in native code. This binary dictionary is written to internal storage.
 */
abstract public class ExpandableBinaryDictionary extends Dictionary {

    /** Used for Log actions from this class */
    private static final String TAG = ExpandableBinaryDictionary.class.getSimpleName();

    /** Whether to print debug output to log */
    private static boolean DEBUG = false;
    private static final boolean DBG_STRESS_TEST = false;

    private static final int TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS = 100;

    private static final int DEFAULT_MAX_UNIGRAM_COUNT = 10000;
    private static final int DEFAULT_MAX_BIGRAM_COUNT = 10000;

    /**
     * The maximum length of a word in this dictionary.
     */
    protected static final int MAX_WORD_LENGTH = Constants.DICTIONARY_MAX_WORD_LENGTH;

    private static final int DICTIONARY_FORMAT_VERSION = FormatSpec.VERSION4;

    /** The application context. */
    protected final Context mContext;

    /**
     * The binary dictionary generated dynamically from the fusion dictionary. This is used to
     * answer unigram and bigram queries.
     */
    private BinaryDictionary mBinaryDictionary;

    /**
     * The name of this dictionary, used as a part of the filename for storing the binary
     * dictionary.
     */
    private final String mDictName;

    /** Dictionary locale */
    private final Locale mLocale;

    /** Dictionary file */
    private final File mDictFile;

    /** Indicates whether a task for reloading the dictionary has been scheduled. */
    private final AtomicBoolean mIsReloading;

    /** Indicates whether the current dictionary needs to be reloaded. */
    private boolean mNeedsToReload;

    private final ReentrantReadWriteLock mLock;

    private Map<String, String> mAdditionalAttributeMap = null;

    /* A extension for a binary dictionary file. */
    protected static final String DICT_FILE_EXTENSION = ".dict";

    /**
     * Abstract method for loading initial contents of a given dictionary.
     */
    protected abstract void loadInitialContentsLocked();

    /**
     * Indicates that the source dictionary contents have changed and a rebuild of the binary file
     * is required. If it returns false, the next reload will only read the current binary
     * dictionary from file.
     */
    protected abstract boolean haveContentsChanged();

    private boolean matchesExpectedBinaryDictFormatVersionForThisType(final int formatVersion) {
        return formatVersion == FormatSpec.VERSION4;
    }

    private boolean needsToMigrateDictionary(final int formatVersion) {
        // TODO: Check version.
        return false;
    }

    public boolean isValidDictionaryLocked() {
        return mBinaryDictionary.isValidDictionary();
    }

    /**
     * Creates a new expandable binary dictionary.
     *
     * @param context The application context of the parent.
     * @param dictName The name of the dictionary. Multiple instances with the same
     *        name is supported.
     * @param locale the dictionary locale.
     * @param dictType the dictionary type, as a human-readable string
     * @param dictFile dictionary file path. if null, use default dictionary path based on
     *        dictionary type.
     */
    public ExpandableBinaryDictionary(final Context context, final String dictName,
            final Locale locale, final String dictType, final File dictFile) {
        super(dictType);
        mDictName = dictName;
        mContext = context;
        mLocale = locale;
        mDictFile = getDictFile(context, dictName, dictFile);
        mBinaryDictionary = null;
        mIsReloading = new AtomicBoolean();
        mNeedsToReload = false;
        mLock = new ReentrantReadWriteLock();
    }

    public static File getDictFile(final Context context, final String dictName,
            final File dictFile) {
        return (dictFile != null) ? dictFile
                : new File(context.getFilesDir(), dictName + DICT_FILE_EXTENSION);
    }

    public static String getDictName(final String name, final Locale locale,
            final File dictFile) {
        return dictFile != null ? dictFile.getName() : name + "." + locale.toString();
    }

    private void asyncExecuteTaskWithWriteLock(final Runnable task) {
        asyncExecuteTaskWithLock(mLock.writeLock(), task);
    }

    private void asyncExecuteTaskWithLock(final Lock lock, final Runnable task) {
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                try {
                    task.run();
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    /**
     * Closes and cleans up the binary dictionary.
     */
    @Override
    public void close() {
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                if (mBinaryDictionary != null) {
                    mBinaryDictionary.close();
                    mBinaryDictionary = null;
                }
            }
        });
    }

    protected Map<String, String> getHeaderAttributeMap() {
        HashMap<String, String> attributeMap = new HashMap<String, String>();
        if (mAdditionalAttributeMap != null) {
            attributeMap.putAll(mAdditionalAttributeMap);
        }
        attributeMap.put(DictionaryHeader.DICTIONARY_ID_KEY, mDictName);
        attributeMap.put(DictionaryHeader.DICTIONARY_LOCALE_KEY, mLocale.toString());
        attributeMap.put(DictionaryHeader.DICTIONARY_VERSION_KEY,
                String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
        attributeMap.put(DictionaryHeader.MAX_UNIGRAM_COUNT_KEY,
                String.valueOf(DEFAULT_MAX_UNIGRAM_COUNT));
        attributeMap.put(DictionaryHeader.MAX_BIGRAM_COUNT_KEY,
                String.valueOf(DEFAULT_MAX_BIGRAM_COUNT));
        return attributeMap;
    }

    private void removeBinaryDictionary() {
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                removeBinaryDictionaryLocked();
            }
        });
    }

    private void removeBinaryDictionaryLocked() {
        if (mBinaryDictionary != null) {
            mBinaryDictionary.close();
        }
        if (mDictFile.exists() && !FileUtils.deleteRecursively(mDictFile)) {
            Log.e(TAG, "Can't remove a file: " + mDictFile.getName());
        }
        mBinaryDictionary = null;
    }

    private void openBinaryDictionaryLocked() {
        mBinaryDictionary = new BinaryDictionary(
                mDictFile.getAbsolutePath(), 0 /* offset */, mDictFile.length(),
                true /* useFullEditDistance */, mLocale, mDictType, true /* isUpdatable */);
    }

    private void createOnMemoryBinaryDictionaryLocked() {
        mBinaryDictionary = new BinaryDictionary(
                mDictFile.getAbsolutePath(), true /* useFullEditDistance */, mLocale, mDictType,
                DICTIONARY_FORMAT_VERSION, getHeaderAttributeMap());
    }

    public void clear() {
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                removeBinaryDictionaryLocked();
                createOnMemoryBinaryDictionaryLocked();
            }
        });
    }

    /**
     * Check whether GC is needed and run GC if required.
     */
    protected void runGCIfRequired(final boolean mindsBlockByGC) {
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                if (mBinaryDictionary == null) {
                    return;
                }
                runGCIfRequiredLocked(mindsBlockByGC);
            }
        });
    }

    protected void runGCIfRequiredLocked(final boolean mindsBlockByGC) {
        if (mBinaryDictionary.needsToRunGC(mindsBlockByGC)) {
            mBinaryDictionary.flushWithGC();
        }
    }

    /**
     * Dynamically adds a word unigram to the dictionary. May overwrite an existing entry.
     */
    public void addWordDynamically(final String word, final int frequency,
            final String shortcutTarget, final int shortcutFreq, final boolean isNotAWord,
            final boolean isBlacklisted, final int timestamp) {
        reloadDictionaryIfRequired();
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                if (mBinaryDictionary == null) {
                    return;
                }
                runGCIfRequiredLocked(true /* mindsBlockByGC */);
                addWordDynamicallyLocked(word, frequency, shortcutTarget, shortcutFreq,
                        isNotAWord, isBlacklisted, timestamp);
            }
        });
    }

    protected void addWordDynamicallyLocked(final String word, final int frequency,
            final String shortcutTarget, final int shortcutFreq, final boolean isNotAWord,
            final boolean isBlacklisted, final int timestamp) {
        mBinaryDictionary.addUnigramWord(word, frequency, shortcutTarget, shortcutFreq,
                isNotAWord, isBlacklisted, timestamp);
    }

    /**
     * Dynamically adds a word bigram in the dictionary. May overwrite an existing entry.
     */
    public void addBigramDynamically(final String word0, final String word1,
            final int frequency, final int timestamp) {
        reloadDictionaryIfRequired();
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                if (mBinaryDictionary == null) {
                    return;
                }
                runGCIfRequiredLocked(true /* mindsBlockByGC */);
                addBigramDynamicallyLocked(word0, word1, frequency, timestamp);
            }
        });
    }

    protected void addBigramDynamicallyLocked(final String word0, final String word1,
            final int frequency, final int timestamp) {
        mBinaryDictionary.addBigramWords(word0, word1, frequency, timestamp);
    }

    /**
     * Dynamically remove a word bigram in the dictionary.
     */
    public void removeBigramDynamically(final String word0, final String word1) {
        reloadDictionaryIfRequired();
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                if (mBinaryDictionary == null) {
                    return;
                }
                runGCIfRequiredLocked(true /* mindsBlockByGC */);
                mBinaryDictionary.removeBigramWords(word0, word1);
            }
        });
    }

    public interface AddMultipleDictionaryEntriesCallback {
        public void onFinished();
    }

    /**
     * Dynamically add multiple entries to the dictionary.
     */
    public void addMultipleDictionaryEntriesDynamically(
            final ArrayList<LanguageModelParam> languageModelParams,
            final AddMultipleDictionaryEntriesCallback callback) {
        reloadDictionaryIfRequired();
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mBinaryDictionary == null) {
                        return;
                    }
                    mBinaryDictionary.addMultipleDictionaryEntries(
                            languageModelParams.toArray(
                                    new LanguageModelParam[languageModelParams.size()]));
                } finally {
                    if (callback != null) {
                        callback.onFinished();
                    }
                }
            }
        });
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestionsWithSessionId(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions,
            final int sessionId, final float[] inOutLanguageWeight) {
        reloadDictionaryIfRequired();
        boolean lockAcquired = false;
        try {
            lockAcquired = mLock.readLock().tryLock(
                    TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
            if (lockAcquired) {
                if (mBinaryDictionary == null) {
                    return null;
                }
                final ArrayList<SuggestedWordInfo> suggestions =
                        mBinaryDictionary.getSuggestionsWithSessionId(composer, prevWord,
                                proximityInfo, blockOffensiveWords, additionalFeaturesOptions,
                                sessionId, inOutLanguageWeight);
                if (mBinaryDictionary.isCorrupted()) {
                    Log.i(TAG, "Dictionary (" + mDictName +") is corrupted. "
                            + "Remove and regenerate it.");
                    removeBinaryDictionary();
                }
                return suggestions;
            }
        } catch (final InterruptedException e) {
            Log.e(TAG, "Interrupted tryLock() in getSuggestionsWithSessionId().", e);
        } finally {
            if (lockAcquired) {
                mLock.readLock().unlock();
            }
        }
        return null;
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions,
            final float[] inOutLanguageWeight) {
        return getSuggestionsWithSessionId(composer, prevWord, proximityInfo, blockOffensiveWords,
                additionalFeaturesOptions, 0 /* sessionId */, inOutLanguageWeight);
    }

    @Override
    public boolean isValidWord(final String word) {
        reloadDictionaryIfRequired();
        boolean lockAcquired = false;
        try {
            lockAcquired = mLock.readLock().tryLock(
                    TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
            if (lockAcquired) {
                if (mBinaryDictionary == null) {
                    return false;
                }
                return isValidWordLocked(word);
            }
        } catch (final InterruptedException e) {
            Log.e(TAG, "Interrupted tryLock() in isValidWord().", e);
        } finally {
            if (lockAcquired) {
                mLock.readLock().unlock();
            }
        }
        return false;
    }

    protected boolean isValidWordLocked(final String word) {
        if (mBinaryDictionary == null) return false;
        return mBinaryDictionary.isValidWord(word);
    }

    protected boolean isValidBigramLocked(final String word1, final String word2) {
        if (mBinaryDictionary == null) return false;
        return mBinaryDictionary.isValidBigram(word1, word2);
    }

    /**
     * Loads the current binary dictionary from internal storage. Assumes the dictionary file
     * exists.
     */
    private void loadBinaryDictionaryLocked() {
        if (DBG_STRESS_TEST) {
            // Test if this class does not cause problems when it takes long time to load binary
            // dictionary.
            try {
                Log.w(TAG, "Start stress in loading: " + mDictName);
                Thread.sleep(15000);
                Log.w(TAG, "End stress in loading");
            } catch (InterruptedException e) {
            }
        }
        final BinaryDictionary oldBinaryDictionary = mBinaryDictionary;
        openBinaryDictionaryLocked();
        if (oldBinaryDictionary != null) {
            oldBinaryDictionary.close();
        }
        if (mBinaryDictionary.isValidDictionary()
                && needsToMigrateDictionary(mBinaryDictionary.getFormatVersion())) {
            if (!mBinaryDictionary.migrateTo(DICTIONARY_FORMAT_VERSION)) {
                Log.e(TAG, "Dictionary migration failed: " + mDictName);
                removeBinaryDictionaryLocked();
            }
        }
    }

    /**
     * Create a new binary dictionary and load initial contents.
     */
    private void createNewDictionaryLocked() {
        removeBinaryDictionaryLocked();
        createOnMemoryBinaryDictionaryLocked();
        loadInitialContentsLocked();
        // Run GC and flush to file when initial contents have been loaded.
        mBinaryDictionary.flushWithGCIfHasUpdated();
    }

    /**
     * Marks that the dictionary needs to be reloaded.
     *
     */
    protected void setNeedsToReload() {
        mNeedsToReload = true;
    }

    /**
     * Load the current binary dictionary from internal storage. If the dictionary file doesn't
     * exists or needs to be regenerated, the new dictionary file will be asynchronously generated.
     * However, the dictionary itself is accessible even before the new dictionary file is actually
     * generated. It may return a null result for getSuggestions() in that case by design.
     */
    public final void reloadDictionaryIfRequired() {
        if (!isReloadRequired()) return;
        asyncReloadDictionary();
    }

    /**
     * Returns whether a dictionary reload is required.
     */
    private boolean isReloadRequired() {
        return mBinaryDictionary == null || mNeedsToReload;
    }

    /**
     * Reloads the dictionary. Access is controlled on a per dictionary file basis.
     */
    private final void asyncReloadDictionary() {
        if (mIsReloading.compareAndSet(false, true)) {
            asyncExecuteTaskWithWriteLock(new Runnable() {
                @Override
                public void run() {
                    try {
                        // TODO: Quit checking contents in ExpandableBinaryDictionary.
                        if (!mDictFile.exists() || (mNeedsToReload && haveContentsChanged())) {
                            // If the dictionary file does not exist or contents have been updated,
                            // generate a new one.
                            createNewDictionaryLocked();
                        } else if (mBinaryDictionary == null) {
                            // Otherwise, load the existing dictionary.
                            loadBinaryDictionaryLocked();
                            if (mBinaryDictionary != null && !(isValidDictionaryLocked()
                                    // TODO: remove the check below
                                    && matchesExpectedBinaryDictFormatVersionForThisType(
                                            mBinaryDictionary.getFormatVersion()))) {
                                // Binary dictionary or its format version is not valid. Regenerate
                                // the dictionary file. writeBinaryDictionary will remove the
                                // existing files if appropriate.
                                createNewDictionaryLocked();
                            }
                        }
                        mNeedsToReload = false;
                    } finally {
                        mIsReloading.set(false);
                    }
                }
            });
        }
    }

    /**
     * Flush binary dictionary to dictionary file.
     */
    public void asyncFlushBinaryDictionary() {
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                if (mBinaryDictionary == null) {
                    return;
                }
                if (mBinaryDictionary.needsToRunGC(false /* mindsBlockByGC */)) {
                    mBinaryDictionary.flushWithGC();
                } else {
                    mBinaryDictionary.flush();
                }
            }
        });
    }

    // TODO: Implement BinaryDictionary.isInDictionary().
    @UsedForTesting
    public boolean isInUnderlyingBinaryDictionaryForTests(final String word) {
        mLock.readLock().lock();
        try {
            if (mBinaryDictionary != null && mDictType == Dictionary.TYPE_USER_HISTORY) {
                return mBinaryDictionary.isValidWord(word);
            }
            return false;
        } finally {
            mLock.readLock().unlock();
        }
    }

    @UsedForTesting
    public void waitAllTasksForTests() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for finishing dictionary operations.", e);
        }
    }

    @UsedForTesting
    public void clearAndFlushDictionaryWithAdditionalAttributes(
            final Map<String, String> attributeMap) {
        mAdditionalAttributeMap = attributeMap;
        clear();
    }

    public void dumpAllWordsForDebug() {
        reloadDictionaryIfRequired();
        asyncExecuteTaskWithLock(mLock.readLock(), new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Dump dictionary: " + mDictName);
                try {
                    final DictionaryHeader header = mBinaryDictionary.getHeader();
                    Log.d(TAG, CombinedFormatUtils.formatAttributeMap(
                            header.mDictionaryOptions.mAttributes));
                } catch (final UnsupportedFormatException e) {
                    Log.d(TAG, "Cannot fetch header information.", e);
                }
                int token = 0;
                do {
                    final BinaryDictionary.GetNextWordPropertyResult result =
                            mBinaryDictionary.getNextWordProperty(token);
                    final WordProperty wordProperty = result.mWordProperty;
                    if (wordProperty == null) {
                        Log.d(TAG, " dictionary is empty.");
                        break;
                    }
                    Log.d(TAG, wordProperty.toString());
                    token = result.mNextToken;
                } while (token != 0);
            }
        });
    }
}
