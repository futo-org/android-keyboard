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
import com.android.inputmethod.latin.utils.AsyncResultHolder;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.CombinedFormatUtils;
import com.android.inputmethod.latin.utils.ExecutorUtils;
import com.android.inputmethod.latin.utils.FileUtils;
import com.android.inputmethod.latin.utils.LanguageModelParam;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final boolean DBG_STRESS_TEST = false;

    private static final int TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS = 100;
    private static final int TIMEOUT_FOR_READ_OPS_FOR_TESTS_IN_MILLISECONDS = 10000;

    /**
     * The maximum length of a word in this dictionary.
     */
    protected static final int MAX_WORD_LENGTH = Constants.DICTIONARY_MAX_WORD_LENGTH;

    private static final int DICTIONARY_FORMAT_VERSION = FormatSpec.VERSION4;

    /**
     * A static map of update controllers, each of which records the time of accesses to a single
     * binary dictionary file and tracks whether the file is regenerating. The key for this map is
     * the dictionary name  and the value is the shared dictionary time recorder associated with
     * that dictionary name.
     */
    private static final ConcurrentHashMap<String, DictionaryUpdateController>
            sDictNameDictionaryUpdateControllerMap = CollectionUtils.newConcurrentHashMap();

    /** The application context. */
    protected final Context mContext;

    /**
     * The binary dictionary generated dynamically from the fusion dictionary. This is used to
     * answer unigram and bigram queries.
     */
    private BinaryDictionary mBinaryDictionary;

    /**
     * The name of this dictionary, used as a part of the filename for storing the binary
     * dictionary. Multiple dictionary instances with the same name is supported, with access
     * controlled by DictionaryUpdateController.
     */
    private final String mDictName;

    /** Dictionary locale */
    private final Locale mLocale;

    /** Dictionary file */
    private final File mDictFile;

    // TODO: remove, once dynamic operations is serialized
    /** Controls updating the shared binary dictionary file across multiple instances. */
    private final DictionaryUpdateController mDictNameDictionaryUpdateController;

    // TODO: remove, once dynamic operations is serialized
    /** Controls updating the local binary dictionary for this instance. */
    private final DictionaryUpdateController mPerInstanceDictionaryUpdateController =
            new DictionaryUpdateController();

    /* A extension for a binary dictionary file. */
    protected static final String DICT_FILE_EXTENSION = ".dict";

    private final AtomicReference<Runnable> mUnfinishedFlushingTask =
            new AtomicReference<Runnable>();

    /**
     * Abstract method for loading initial contents of a given dictionary.
     */
    protected abstract void loadInitialContentsLocked();

    /**
     * Indicates that the source dictionary contents have changed and a rebuild of the binary file
     * is required. If it returns false, the next reload will only read the current binary
     * dictionary from file. Note that the shared binary dictionary is locked when this is called.
     */
    protected abstract boolean haveContentsChanged();

    private boolean matchesExpectedBinaryDictFormatVersionForThisType(final int formatVersion) {
        return formatVersion == FormatSpec.VERSION4;
    }

    public boolean isValidDictionaryLocked() {
        return mBinaryDictionary.isValidDictionary();
    }

    /**
     * Gets the dictionary update controller for the given dictionary name.
     */
    private static DictionaryUpdateController getDictionaryUpdateController(
            final String dictName) {
        DictionaryUpdateController recorder = sDictNameDictionaryUpdateControllerMap.get(dictName);
        if (recorder == null) {
            synchronized(sDictNameDictionaryUpdateControllerMap) {
                recorder = new DictionaryUpdateController();
                sDictNameDictionaryUpdateControllerMap.put(dictName, recorder);
            }
        }
        return recorder;
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
        mDictNameDictionaryUpdateController = getDictionaryUpdateController(dictName);
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

    /**
     * Closes and cleans up the binary dictionary.
     */
    @Override
    public void close() {
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
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
        attributeMap.put(DictionaryHeader.DICTIONARY_ID_KEY, mDictName);
        attributeMap.put(DictionaryHeader.DICTIONARY_LOCALE_KEY, mLocale.toString());
        attributeMap.put(DictionaryHeader.DICTIONARY_VERSION_KEY,
                String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
        return attributeMap;
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

    private void createBinaryDictionaryLocked() {
        BinaryDictionary.createEmptyDictFile(mDictFile.getAbsolutePath(),
                DICTIONARY_FORMAT_VERSION, mLocale, getHeaderAttributeMap());
    }

    private void openBinaryDictionaryLocked() {
        mBinaryDictionary = new BinaryDictionary(
                mDictFile.getAbsolutePath(), 0 /* offset */, mDictFile.length(),
                true /* useFullEditDistance */, mLocale, mDictType, true /* isUpdatable */);
    }

    protected void clear() {
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                removeBinaryDictionaryLocked();
                createBinaryDictionaryLocked();
                openBinaryDictionaryLocked();
            }
        });
    }

    /**
     * Check whether GC is needed and run GC if required.
     */
    protected void runGCIfRequired(final boolean mindsBlockByGC) {
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                runGCAfterAllPrioritizedTasksIfRequiredLocked(mindsBlockByGC);
            }
        });
    }

    protected void runGCIfRequiredLocked(final boolean mindsBlockByGC) {
        if (mBinaryDictionary.needsToRunGC(mindsBlockByGC)) {
            mBinaryDictionary.flushWithGC();
        }
    }

    private void runGCAfterAllPrioritizedTasksIfRequiredLocked(final boolean mindsBlockByGC) {
        // needsToRunGC() have to be called with lock.
        if (mBinaryDictionary.needsToRunGC(mindsBlockByGC)) {
            if (setProcessingLargeTaskIfNot()) {
                // Run GC after currently existing time sensitive operations.
                ExecutorUtils.getExecutor(mDictName).executePrioritized(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mBinaryDictionary.flushWithGC();
                        } finally {
                            mDictNameDictionaryUpdateController.mProcessingLargeTask.set(false);
                        }
                    }
                });
            }
        }
    }

    /**
     * Dynamically adds a word unigram to the dictionary. May overwrite an existing entry.
     */
    protected void addWordDynamically(final String word, final int frequency,
            final String shortcutTarget, final int shortcutFreq, final boolean isNotAWord,
            final boolean isBlacklisted, final int timestamp) {
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                runGCAfterAllPrioritizedTasksIfRequiredLocked(true /* mindsBlockByGC */);
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
    protected void addBigramDynamically(final String word0, final String word1,
            final int frequency, final int timestamp) {
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                runGCAfterAllPrioritizedTasksIfRequiredLocked(true /* mindsBlockByGC */);
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
    protected void removeBigramDynamically(final String word0, final String word1) {
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                runGCAfterAllPrioritizedTasksIfRequiredLocked(true /* mindsBlockByGC */);
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
    protected void addMultipleDictionaryEntriesDynamically(
            final ArrayList<LanguageModelParam> languageModelParams,
            final AddMultipleDictionaryEntriesCallback callback) {
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                final boolean locked = setProcessingLargeTaskIfNot();
                try {
                    mBinaryDictionary.addMultipleDictionaryEntries(
                            languageModelParams.toArray(
                                    new LanguageModelParam[languageModelParams.size()]));
                } finally {
                    if (callback != null) {
                        callback.onFinished();
                    }
                    if (locked) {
                        mDictNameDictionaryUpdateController.mProcessingLargeTask.set(false);
                    }
                }
            }
        });
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestionsWithSessionId(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions,
            final int sessionId) {
        reloadDictionaryIfRequired();
        if (processingLargeTask()) {
            return null;
        }
        final AsyncResultHolder<ArrayList<SuggestedWordInfo>> holder =
                new AsyncResultHolder<ArrayList<SuggestedWordInfo>>();
        ExecutorUtils.getExecutor(mDictName).executePrioritized(new Runnable() {
            @Override
            public void run() {
                if (mBinaryDictionary == null) {
                    holder.set(null);
                    return;
                }
                final ArrayList<SuggestedWordInfo> binarySuggestion =
                        mBinaryDictionary.getSuggestionsWithSessionId(composer, prevWord,
                                proximityInfo, blockOffensiveWords, additionalFeaturesOptions,
                                sessionId);
                holder.set(binarySuggestion);
                if (mBinaryDictionary.isCorrupted()) {
                    removeBinaryDictionaryLocked();
                }
            }
        });
        return holder.get(null, TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS);
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions) {
        return getSuggestionsWithSessionId(composer, prevWord, proximityInfo, blockOffensiveWords,
                additionalFeaturesOptions, 0 /* sessionId */);
    }

    @Override
    public boolean isValidWord(final String word) {
        reloadDictionaryIfRequired();
        if (processingLargeTask()) {
            return false;
        }
        final AsyncResultHolder<Boolean> holder = new AsyncResultHolder<Boolean>();
        ExecutorUtils.getExecutor(mDictName).executePrioritized(new Runnable() {
            @Override
            public void run() {
                holder.set(isValidWordLocked(word));
            }
        });
        return holder.get(false, TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS);
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
     * Load the current binary dictionary from internal storage in a background thread. If no binary
     * dictionary exists, this method will generate one.
     */
    protected void loadDictionary() {
        mPerInstanceDictionaryUpdateController.mLastUpdateRequestTime = System.currentTimeMillis();
        reloadDictionaryIfRequired();
    }

    /**
     * Loads the current binary dictionary from internal storage. Assumes the dictionary file
     * exists.
     */
    private void loadBinaryDictionaryLocked() {
        if (DEBUG) {
            Log.d(TAG, "Loading binary dictionary: " + mDictName + " request="
                    + mDictNameDictionaryUpdateController.mLastUpdateRequestTime + " update="
                    + mDictNameDictionaryUpdateController.mLastUpdateTime);
        }
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
    }

    /**
     * Abstract method for checking if it is required to reload the dictionary before writing
     * a binary dictionary.
     */
    abstract protected boolean needsToReloadAfterCreation();

    /**
     * Create a new binary dictionary and load initial contents.
     */
    private void createNewDictionaryLocked() {
        if (DEBUG) {
            Log.d(TAG, "Generating binary dictionary: " + mDictName + " request="
                    + mDictNameDictionaryUpdateController.mLastUpdateRequestTime + " update="
                    + mDictNameDictionaryUpdateController.mLastUpdateTime);
        }
        removeBinaryDictionaryLocked();
        createBinaryDictionaryLocked();
        openBinaryDictionaryLocked();
        loadInitialContentsLocked();
        mBinaryDictionary.flushWithGC();
    }

    private void flushDictionaryLocked() {
        if (mBinaryDictionary.needsToRunGC(false /* mindsBlockByGC */)) {
            mBinaryDictionary.flushWithGC();
        } else {
            mBinaryDictionary.flush();
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
        final long time = System.currentTimeMillis();
        mPerInstanceDictionaryUpdateController.mLastUpdateRequestTime = time;
        mDictNameDictionaryUpdateController.mLastUpdateRequestTime = time;
        if (DEBUG) {
            Log.d(TAG, "Reload request: " + mDictName + ": request=" + time + " update="
                    + mDictNameDictionaryUpdateController.mLastUpdateTime);
        }
    }

    /**
     * Reloads the dictionary if required.
     */
    public final void reloadDictionaryIfRequired() {
        if (!isReloadRequired()) return;
        if (setProcessingLargeTaskIfNot()) {
            reloadDictionary();
        }
    }

    /**
     * Returns whether a dictionary reload is required.
     */
    private boolean isReloadRequired() {
        return mBinaryDictionary == null || mPerInstanceDictionaryUpdateController.isOutOfDate();
    }

    private boolean processingLargeTask() {
        return mDictNameDictionaryUpdateController.mProcessingLargeTask.get();
    }

    // Returns whether the dictionary is being used for a large task. If true, we should not use
    // this dictionary for latency sensitive operations.
    private boolean setProcessingLargeTaskIfNot() {
        return mDictNameDictionaryUpdateController.mProcessingLargeTask.compareAndSet(
                false /* expect */ , true /* update */);
    }

    /**
     * Reloads the dictionary. Access is controlled on a per dictionary file basis and supports
     * concurrent calls from multiple instances that share the same dictionary file.
     */
    private final void reloadDictionary() {
        // Ensure that only one thread attempts to read or write to the shared binary dictionary
        // file at the same time.
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final long time = System.currentTimeMillis();
                    final boolean openedDictIsOutOfDate =
                            mDictNameDictionaryUpdateController.isOutOfDate();
                    if (!dictionaryFileExists()
                            || (openedDictIsOutOfDate && haveContentsChanged())) {
                        // If the shared dictionary file does not exist or is out of date and
                        // contents have been updated, the first instance that acquires the lock
                        // will generate a new one
                        mDictNameDictionaryUpdateController.mLastUpdateTime = time;
                        createNewDictionaryLocked();
                    } else if (openedDictIsOutOfDate) {
                        // If not, the reload request was unnecessary so revert
                        // LastUpdateRequestTime to LastUpdateTime.
                        mDictNameDictionaryUpdateController.mLastUpdateRequestTime =
                                mDictNameDictionaryUpdateController.mLastUpdateTime;
                    } else if (mBinaryDictionary == null ||
                            mPerInstanceDictionaryUpdateController.mLastUpdateTime
                                    < mDictNameDictionaryUpdateController.mLastUpdateTime) {
                        // Otherwise, if the local dictionary is older than the shared dictionary,
                        // load the shared dictionary.
                        loadBinaryDictionaryLocked();
                    }
                    if (mBinaryDictionary != null && !(isValidDictionaryLocked()
                            // TODO: remove the check below
                            && matchesExpectedBinaryDictFormatVersionForThisType(
                                    mBinaryDictionary.getFormatVersion()))) {
                        // Binary dictionary or its format version is not valid. Regenerate
                        // the dictionary file. writeBinaryDictionary will remove the
                        // existing files if appropriate.
                        mDictNameDictionaryUpdateController.mLastUpdateTime = time;
                        createNewDictionaryLocked();
                    }
                    mPerInstanceDictionaryUpdateController.mLastUpdateTime = time;
                } finally {
                    mDictNameDictionaryUpdateController.mProcessingLargeTask.set(false);
                }
            }
        });
    }

    // TODO: cache the file's existence so that we avoid doing a disk access each time.
    private boolean dictionaryFileExists() {
        return mDictFile.exists();
    }

    /**
     * Flush binary dictionary to dictionary file.
     */
    protected void asyncFlushBinaryDictionary() {
        final Runnable newTask = new Runnable() {
            @Override
            public void run() {
                flushDictionaryLocked();
            }
        };
        final Runnable oldTask = mUnfinishedFlushingTask.getAndSet(newTask);
        ExecutorUtils.getExecutor(mDictName).replaceAndExecute(oldTask, newTask);
    }

    /**
     * For tracking whether the dictionary is out of date and the dictionary is used in a large
     * task. Can be shared across multiple dictionary instances that access the same filename.
     */
    private static class DictionaryUpdateController {
        public volatile long mLastUpdateTime = 0;
        public volatile long mLastUpdateRequestTime = 0;
        public volatile AtomicBoolean mProcessingLargeTask = new AtomicBoolean();

        public boolean isOutOfDate() {
            return (mLastUpdateRequestTime > mLastUpdateTime);
        }
    }

    // TODO: Implement BinaryDictionary.isInDictionary().
    @UsedForTesting
    public boolean isInUnderlyingBinaryDictionaryForTests(final String word) {
        final AsyncResultHolder<Boolean> holder = new AsyncResultHolder<Boolean>();
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
            @Override
            public void run() {
                if (mDictType == Dictionary.TYPE_USER_HISTORY) {
                    holder.set(mBinaryDictionary.isValidWord(word));
                }
            }
        });
        return holder.get(false, TIMEOUT_FOR_READ_OPS_FOR_TESTS_IN_MILLISECONDS);
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
    public void dumpAllWordsForDebug() {
        reloadDictionaryIfRequired();
        ExecutorUtils.getExecutor(mDictName).execute(new Runnable() {
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
