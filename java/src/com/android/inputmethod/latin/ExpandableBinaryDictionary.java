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
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.FormatSpec;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;
import com.android.inputmethod.latin.makedict.WordProperty;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;
import com.android.inputmethod.latin.utils.AsyncResultHolder;
import com.android.inputmethod.latin.utils.CombinedFormatUtils;
import com.android.inputmethod.latin.utils.DistracterFilter;
import com.android.inputmethod.latin.utils.ExecutorUtils;
import com.android.inputmethod.latin.utils.FileUtils;
import com.android.inputmethod.latin.utils.WordInputEventForPersonalization;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Abstract base class for an expandable dictionary that can be created and updated dynamically
 * during runtime. When updated it automatically generates a new binary dictionary to handle future
 * queries in native code. This binary dictionary is written to internal storage.
 *
 * A class that extends this abstract class must have a static factory method named
 *   getDictionary(Context context, Locale locale, File dictFile, String dictNamePrefix)
 * @see DictionaryFacilitator#getSubDict(String,Context,Locale,File,String)
 */
abstract public class ExpandableBinaryDictionary extends Dictionary {
    private static final boolean DEBUG = false;

    /** Used for Log actions from this class */
    private static final String TAG = ExpandableBinaryDictionary.class.getSimpleName();

    /** Whether to print debug output to log */
    private static final boolean DBG_STRESS_TEST = false;

    private static final int TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS = 100;

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

    /** Dictionary file */
    private final File mDictFile;

    /** Indicates whether a task for reloading the dictionary has been scheduled. */
    private final AtomicBoolean mIsReloading;

    /** Indicates whether the current dictionary needs to be recreated. */
    private boolean mNeedsToRecreate;

    private final ReentrantReadWriteLock mLock;

    private Map<String, String> mAdditionalAttributeMap = null;

    /* A extension for a binary dictionary file. */
    protected static final String DICT_FILE_EXTENSION = ".dict";

    /**
     * Abstract method for loading initial contents of a given dictionary.
     */
    protected abstract void loadInitialContentsLocked();

    static boolean matchesExpectedBinaryDictFormatVersionForThisType(final int formatVersion) {
        return formatVersion == FormatSpec.VERSION4;
    }

    private static boolean needsToMigrateDictionary(final int formatVersion) {
        // When we bump up the dictionary format version, the old version should be added to here
        // for supporting migration. Note that native code has to support reading such formats.
        return formatVersion == FormatSpec.VERSION4_ONLY_FOR_TESTING;
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
        super(dictType, locale);
        mDictName = dictName;
        mContext = context;
        mDictFile = getDictFile(context, dictName, dictFile);
        mBinaryDictionary = null;
        mIsReloading = new AtomicBoolean();
        mNeedsToRecreate = false;
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
        asyncExecuteTaskWithLock(mLock.writeLock(), mDictName /* executorName */, task);
    }

    private static void asyncExecuteTaskWithLock(final Lock lock, final String executorName,
            final Runnable task) {
        asyncPreCheckAndExecuteTaskWithLock(lock, null /* preCheckTask */, executorName, task);
    }

    private void asyncPreCheckAndExecuteTaskWithWriteLock(
            final Callable<Boolean> preCheckTask, final Runnable task) {
        asyncPreCheckAndExecuteTaskWithLock(mLock.writeLock(), preCheckTask,
                mDictName /* executorName */, task);

    }

    // Execute task with lock when the result of preCheckTask is true or preCheckTask is null.
    private static void asyncPreCheckAndExecuteTaskWithLock(final Lock lock,
            final Callable<Boolean> preCheckTask, final String executorName, final Runnable task) {
        final String tag = TAG;
        ExecutorUtils.getExecutor(executorName).execute(new Runnable() {
            @Override
            public void run() {
                if (preCheckTask != null) {
                    try {
                        if (!preCheckTask.call().booleanValue()) {
                            return;
                        }
                    } catch (final Exception e) {
                        Log.e(tag, "The pre check task throws an exception.", e);
                        return;
                    }
                }
                lock.lock();
                try {
                    task.run();
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    @Nullable
    BinaryDictionary getBinaryDictionary() {
        return mBinaryDictionary;
    }

    void closeBinaryDictionary() {
        if (mBinaryDictionary != null) {
            mBinaryDictionary.close();
            mBinaryDictionary = null;
        }
    }

    /**
     * Closes and cleans up the binary dictionary.
     */
    @Override
    public void close() {
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                closeBinaryDictionary();
            }
        });
    }

    protected Map<String, String> getHeaderAttributeMap() {
        HashMap<String, String> attributeMap = new HashMap<>();
        if (mAdditionalAttributeMap != null) {
            attributeMap.putAll(mAdditionalAttributeMap);
        }
        attributeMap.put(DictionaryHeader.DICTIONARY_ID_KEY, mDictName);
        attributeMap.put(DictionaryHeader.DICTIONARY_LOCALE_KEY, mLocale.toString());
        attributeMap.put(DictionaryHeader.DICTIONARY_VERSION_KEY,
                String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
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

    void removeBinaryDictionaryLocked() {
        closeBinaryDictionary();
        if (mDictFile.exists() && !FileUtils.deleteRecursively(mDictFile)) {
            Log.e(TAG, "Can't remove a file: " + mDictFile.getName());
        }
    }

    private void openBinaryDictionaryLocked() {
        mBinaryDictionary = new BinaryDictionary(
                mDictFile.getAbsolutePath(), 0 /* offset */, mDictFile.length(),
                true /* useFullEditDistance */, mLocale, mDictType, true /* isUpdatable */);
    }

    void createOnMemoryBinaryDictionaryLocked() {
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
                if (getBinaryDictionary() == null) {
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

    private void updateDictionaryWithWriteLockIfWordIsNotADistracter(
            @Nonnull final Runnable updateTask,
            @Nonnull final String word, @Nonnull final DistracterFilter distracterFilter) {
        reloadDictionaryIfRequired();
        final Callable<Boolean> preCheckTask = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return !distracterFilter.isDistracterToWordsInDictionaries(
                        NgramContext.EMPTY_PREV_WORDS_INFO, word, mLocale);
            }
        };
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                if (getBinaryDictionary() == null) {
                    return;
                }
                runGCIfRequiredLocked(true /* mindsBlockByGC */);
                updateTask.run();
            }
        };
        asyncPreCheckAndExecuteTaskWithWriteLock(preCheckTask, task);
    }

    /**
     * Adds unigram information of a word to the dictionary. May overwrite an existing entry.
     */
    public void addUnigramEntryWithCheckingDistracter(final String word, final int frequency,
            final String shortcutTarget, final int shortcutFreq, final boolean isNotAWord,
            final boolean isPossiblyOffensive, final int timestamp,
            @Nonnull final DistracterFilter distracterFilter) {
        updateDictionaryWithWriteLockIfWordIsNotADistracter(new Runnable() {
            @Override
            public void run() {
                addUnigramLocked(word, frequency, shortcutTarget, shortcutFreq,
                        isNotAWord, isPossiblyOffensive, timestamp);
            }
        }, word, distracterFilter);
    }

    protected void addUnigramLocked(final String word, final int frequency,
            final String shortcutTarget, final int shortcutFreq, final boolean isNotAWord,
            final boolean isPossiblyOffensive, final int timestamp) {
        if (!mBinaryDictionary.addUnigramEntry(word, frequency, shortcutTarget, shortcutFreq,
                false /* isBeginningOfSentence */, isNotAWord, isPossiblyOffensive, timestamp)) {
            Log.e(TAG, "Cannot add unigram entry. word: " + word);
        }
    }

    /**
     * Dynamically remove the unigram entry from the dictionary.
     */
    public void removeUnigramEntryDynamically(final String word) {
        reloadDictionaryIfRequired();
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                final BinaryDictionary binaryDictionary = getBinaryDictionary();
                if (binaryDictionary == null) {
                    return;
                }
                runGCIfRequiredLocked(true /* mindsBlockByGC */);
                if (!binaryDictionary.removeUnigramEntry(word)) {
                    if (DEBUG) {
                        Log.i(TAG, "Cannot remove unigram entry: " + word);
                    }
                }
            }
        });
    }

    /**
     * Adds n-gram information of a word to the dictionary. May overwrite an existing entry.
     */
    public void addNgramEntry(@Nonnull final NgramContext ngramContext, final String word,
            final int frequency, final int timestamp) {
        reloadDictionaryIfRequired();
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                if (getBinaryDictionary() == null) {
                    return;
                }
                runGCIfRequiredLocked(true /* mindsBlockByGC */);
                addNgramEntryLocked(ngramContext, word, frequency, timestamp);
            }
        });
    }

    protected void addNgramEntryLocked(@Nonnull final NgramContext ngramContext, final String word,
            final int frequency, final int timestamp) {
        if (!mBinaryDictionary.addNgramEntry(ngramContext, word, frequency, timestamp)) {
            if (DEBUG) {
                Log.i(TAG, "Cannot add n-gram entry.");
                Log.i(TAG, "  NgramContext: " + ngramContext + ", word: " + word);
            }
        }
    }

    /**
     * Dynamically remove the n-gram entry in the dictionary.
     */
    @UsedForTesting
    public void removeNgramDynamically(@Nonnull final NgramContext ngramContext,
            final String word) {
        reloadDictionaryIfRequired();
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                final BinaryDictionary binaryDictionary = getBinaryDictionary();
                if (binaryDictionary == null) {
                    return;
                }
                runGCIfRequiredLocked(true /* mindsBlockByGC */);
                if (!binaryDictionary.removeNgramEntry(ngramContext, word)) {
                    if (DEBUG) {
                        Log.i(TAG, "Cannot remove n-gram entry.");
                        Log.i(TAG, "  NgramContext: " + ngramContext + ", word: " + word);
                    }
                }
            }
        });
    }

    /**
     * Update dictionary for the word with the ngramContext if the word is not a distracter.
     */
    public void updateEntriesForWordWithCheckingDistracter(@Nonnull final NgramContext ngramContext,
            final String word, final boolean isValidWord, final int count, final int timestamp,
            @Nonnull final DistracterFilter distracterFilter) {
        updateDictionaryWithWriteLockIfWordIsNotADistracter(new Runnable() {
            @Override
            public void run() {
                final BinaryDictionary binaryDictionary = getBinaryDictionary();
                if (binaryDictionary == null) {
                    return;
                }
                if (!binaryDictionary.updateEntriesForWordWithNgramContext(ngramContext, word,
                        isValidWord, count, timestamp)) {
                    if (DEBUG) {
                        Log.e(TAG, "Cannot update counter. word: " + word
                                + " context: "+ ngramContext.toString());
                    }
                }
            }
        }, word, distracterFilter);
    }

    public interface UpdateEntriesForInputEventsCallback {
        public void onFinished();
    }

    /**
     * Dynamically update entries according to input events.
     */
    public void updateEntriesForInputEvents(
            @Nonnull final ArrayList<WordInputEventForPersonalization> inputEvents,
            final UpdateEntriesForInputEventsCallback callback) {
        reloadDictionaryIfRequired();
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                try {
                    final BinaryDictionary binaryDictionary = getBinaryDictionary();
                    if (binaryDictionary == null) {
                        return;
                    }
                    binaryDictionary.updateEntriesForInputEvents(
                            inputEvents.toArray(
                                    new WordInputEventForPersonalization[inputEvents.size()]));
                } finally {
                    if (callback != null) {
                        callback.onFinished();
                    }
                }
            }
        });
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
            final NgramContext ngramContext, final ProximityInfo proximityInfo,
            final SettingsValuesForSuggestion settingsValuesForSuggestion, final int sessionId,
            final float weightForLocale, final float[] inOutWeightOfLangModelVsSpatialModel) {
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
                        mBinaryDictionary.getSuggestions(composer, ngramContext, proximityInfo,
                                settingsValuesForSuggestion, sessionId, weightForLocale,
                                inOutWeightOfLangModelVsSpatialModel);
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
    public boolean isInDictionary(final String word) {
        reloadDictionaryIfRequired();
        boolean lockAcquired = false;
        try {
            lockAcquired = mLock.readLock().tryLock(
                    TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
            if (lockAcquired) {
                if (mBinaryDictionary == null) {
                    return false;
                }
                return isInDictionaryLocked(word);
            }
        } catch (final InterruptedException e) {
            Log.e(TAG, "Interrupted tryLock() in isInDictionary().", e);
        } finally {
            if (lockAcquired) {
                mLock.readLock().unlock();
            }
        }
        return false;
    }

    protected boolean isInDictionaryLocked(final String word) {
        if (mBinaryDictionary == null) return false;
        return mBinaryDictionary.isInDictionary(word);
    }

    @Override
    public int getMaxFrequencyOfExactMatches(final String word) {
        reloadDictionaryIfRequired();
        boolean lockAcquired = false;
        try {
            lockAcquired = mLock.readLock().tryLock(
                    TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
            if (lockAcquired) {
                if (mBinaryDictionary == null) {
                    return NOT_A_PROBABILITY;
                }
                return mBinaryDictionary.getMaxFrequencyOfExactMatches(word);
            }
        } catch (final InterruptedException e) {
            Log.e(TAG, "Interrupted tryLock() in getMaxFrequencyOfExactMatches().", e);
        } finally {
            if (lockAcquired) {
                mLock.readLock().unlock();
            }
        }
        return NOT_A_PROBABILITY;
    }


    protected boolean isValidNgramLocked(final NgramContext ngramContext, final String word) {
        if (mBinaryDictionary == null) return false;
        return mBinaryDictionary.isValidNgram(ngramContext, word);
    }

    /**
     * Loads the current binary dictionary from internal storage. Assumes the dictionary file
     * exists.
     */
    void loadBinaryDictionaryLocked() {
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
    void createNewDictionaryLocked() {
        removeBinaryDictionaryLocked();
        createOnMemoryBinaryDictionaryLocked();
        loadInitialContentsLocked();
        // Run GC and flush to file when initial contents have been loaded.
        mBinaryDictionary.flushWithGCIfHasUpdated();
    }

    /**
     * Marks that the dictionary needs to be recreated.
     *
     */
    protected void setNeedsToRecreate() {
        mNeedsToRecreate = true;
    }

    void clearNeedsToRecreate() {
        mNeedsToRecreate = false;
    }

    boolean isNeededToRecreate() {
        return mNeedsToRecreate;
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
        return mBinaryDictionary == null || mNeedsToRecreate;
    }

    /**
     * Reloads the dictionary. Access is controlled on a per dictionary file basis.
     */
    private final void asyncReloadDictionary() {
        final AtomicBoolean isReloading = mIsReloading;
        if (!isReloading.compareAndSet(false, true)) {
            return;
        }
        final File dictFile = mDictFile;
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!dictFile.exists() || isNeededToRecreate()) {
                        // If the dictionary file does not exist or contents have been updated,
                        // generate a new one.
                        createNewDictionaryLocked();
                    } else if (getBinaryDictionary() == null) {
                        // Otherwise, load the existing dictionary.
                        loadBinaryDictionaryLocked();
                        final BinaryDictionary binaryDictionary = getBinaryDictionary();
                        if (binaryDictionary != null && !(isValidDictionaryLocked()
                                // TODO: remove the check below
                                && matchesExpectedBinaryDictFormatVersionForThisType(
                                        binaryDictionary.getFormatVersion()))) {
                            // Binary dictionary or its format version is not valid. Regenerate
                            // the dictionary file. createNewDictionaryLocked will remove the
                            // existing files if appropriate.
                            createNewDictionaryLocked();
                        }
                    }
                    clearNeedsToRecreate();
                } finally {
                    isReloading.set(false);
                }
            }
        });
    }

    /**
     * Flush binary dictionary to dictionary file.
     */
    public void asyncFlushBinaryDictionary() {
        asyncExecuteTaskWithWriteLock(new Runnable() {
            @Override
            public void run() {
                final BinaryDictionary binaryDictionary = getBinaryDictionary();
                if (binaryDictionary == null) {
                    return;
                }
                if (binaryDictionary.needsToRunGC(false /* mindsBlockByGC */)) {
                    binaryDictionary.flushWithGC();
                } else {
                    binaryDictionary.flush();
                }
            }
        });
    }

    static int parseEntryCount(final String entryCountStr) {
        int entryCount;
        try {
            entryCount = Integer.parseInt(entryCountStr);
        } catch (final NumberFormatException e) {
            entryCount = DictionaryStats.NOT_AN_ENTRY_COUNT;
        }
        return entryCount;
    }

    public DictionaryStats getDictionaryStats() {
        reloadDictionaryIfRequired();
        final String dictName = mDictName;
        final File dictFile = mDictFile;
        final AsyncResultHolder<DictionaryStats> result = new AsyncResultHolder<>();
        asyncExecuteTaskWithLock(mLock.readLock(), dictName /* executorName */, new Runnable() {
            @Override
            public void run() {
                final BinaryDictionary binaryDictionary = getBinaryDictionary();
                if (binaryDictionary == null) {
                    result.set(new DictionaryStats(mLocale, dictName, dictFile,
                            DictionaryStats.NOT_AN_ENTRY_COUNT,
                            DictionaryStats.NOT_AN_ENTRY_COUNT));
                    return;
                }
                final int unigramCount = parseEntryCount(
                        binaryDictionary.getPropertyForGettingStats(
                                BinaryDictionary.MAX_UNIGRAM_COUNT_QUERY));
                // TODO: Get dedicated entry counts for bigram, trigram, and so on.
                final int ngramCount = parseEntryCount(binaryDictionary.getPropertyForGettingStats(
                        BinaryDictionary.MAX_BIGRAM_COUNT_QUERY));
                // TODO: Get more information from dictionary.
                result.set(new DictionaryStats(mLocale, dictName, dictFile, unigramCount,
                        ngramCount));
            }
        });
        return result.get(null /* defaultValue */, TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS);
    }

    @UsedForTesting
    public void waitAllTasksForTests() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        asyncExecuteTaskWithWriteLock(new Runnable() {
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
        final String tag = TAG;
        final String dictName = mDictName;
        asyncExecuteTaskWithLock(mLock.readLock(), "dumpAllWordsForDebug", new Runnable() {
            @Override
            public void run() {
                Log.d(tag, "Dump dictionary: " + dictName + " for " + mLocale);
                final BinaryDictionary binaryDictionary = getBinaryDictionary();
                if (binaryDictionary == null) {
                    return;
                }
                try {
                    final DictionaryHeader header = binaryDictionary.getHeader();
                    Log.d(tag, "Format version: " + binaryDictionary.getFormatVersion());
                    Log.d(tag, CombinedFormatUtils.formatAttributeMap(
                            header.mDictionaryOptions.mAttributes));
                } catch (final UnsupportedFormatException e) {
                    Log.d(tag, "Cannot fetch header information.", e);
                }
                int token = 0;
                do {
                    final BinaryDictionary.GetNextWordPropertyResult result =
                            binaryDictionary.getNextWordProperty(token);
                    final WordProperty wordProperty = result.mWordProperty;
                    if (wordProperty == null) {
                        Log.d(tag, " dictionary is empty.");
                        break;
                    }
                    Log.d(tag, wordProperty.toString());
                    token = result.mNextToken;
                } while (token != 0);
            }
        });
    }
}
