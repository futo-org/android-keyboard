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
import android.os.SystemClock;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.personalization.DynamicPersonalizationDictionaryWriter;
import com.android.inputmethod.latin.utils.AsyncResultHolder;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.PrioritizedSerialExecutor;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
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

    // TODO: Remove and enable dynamic update in native code.
    /** Whether to call binary dictionary dynamically updating methods. */
    private static boolean ENABLE_BINARY_DICTIONARY_DYNAMIC_UPDATE = false;

    private static final int TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS = 100;

    /**
     * The maximum length of a word in this dictionary.
     */
    protected static final int MAX_WORD_LENGTH = Constants.DICTIONARY_MAX_WORD_LENGTH;

    /**
     * A static map of time recorders, each of which records the time of accesses to a single binary
     * dictionary file. The key for this map is the filename and the value is the shared dictionary
     * time recorder associated with that filename.
     */
    private static volatile ConcurrentHashMap<String, DictionaryTimeRecorder>
            sFilenameDictionaryTimeRecorderMap = CollectionUtils.newConcurrentHashMap();

    private static volatile ConcurrentHashMap<String, PrioritizedSerialExecutor>
            sFilenameExecutorMap = CollectionUtils.newConcurrentHashMap();

    /** The application context. */
    protected final Context mContext;

    /**
     * The binary dictionary generated dynamically from the fusion dictionary. This is used to
     * answer unigram and bigram queries.
     */
    private BinaryDictionary mBinaryDictionary;

    // TODO: Remove and handle dictionaries in native code.
    /** The in-memory dictionary used to generate the binary dictionary. */
    protected AbstractDictionaryWriter mDictionaryWriter;

    /**
     * The name of this dictionary, used as the filename for storing the binary dictionary. Multiple
     * dictionary instances with the same filename is supported, with access controlled by
     * DictionaryTimeRecorder.
     */
    private final String mFilename;

    /** Whether to support dynamically updating the dictionary */
    private final boolean mIsUpdatable;

    // TODO: remove, once dynamic operations is serialized
    /** Records access to the shared binary dictionary file across multiple instances. */
    private final DictionaryTimeRecorder mFilenameDictionaryTimeRecorder;

    // TODO: remove, once dynamic operations is serialized
    /** Records access to the local binary dictionary for this instance. */
    private final DictionaryTimeRecorder mPerInstanceDictionaryTimeRecorder =
            new DictionaryTimeRecorder();

    /* A extension for a binary dictionary file. */
    public static final String DICT_FILE_EXTENSION = ".dict";

    private final AtomicReference<Runnable> mUnfinishedFlushingTask =
            new AtomicReference<Runnable>();

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
     * Gets the dictionary time recorder for the given filename.
     */
    private static DictionaryTimeRecorder getDictionaryTimeRecorder(
            String filename) {
        DictionaryTimeRecorder recorder = sFilenameDictionaryTimeRecorderMap.get(filename);
        if (recorder == null) {
            synchronized(sFilenameDictionaryTimeRecorderMap) {
                recorder = new DictionaryTimeRecorder();
                sFilenameDictionaryTimeRecorderMap.put(filename, recorder);
            }
        }
        return recorder;
    }

    /**
     * Gets the executor for the given filename.
     */
    private static PrioritizedSerialExecutor getExecutor(final String filename) {
        PrioritizedSerialExecutor executor = sFilenameExecutorMap.get(filename);
        if (executor == null) {
            synchronized(sFilenameExecutorMap) {
                executor = new PrioritizedSerialExecutor();
                sFilenameExecutorMap.put(filename, executor);
            }
        }
        return executor;
    }

    private static AbstractDictionaryWriter getDictionaryWriter(final Context context,
            final String dictType, final boolean isDynamicPersonalizationDictionary) {
        if (isDynamicPersonalizationDictionary) {
            return new DynamicPersonalizationDictionaryWriter(context, dictType);
        } else {
            return new DictionaryWriter(context, dictType);
        }
    }

    /**
     * Creates a new expandable binary dictionary.
     *
     * @param context The application context of the parent.
     * @param filename The filename for this binary dictionary. Multiple dictionaries with the same
     *        filename is supported.
     * @param dictType the dictionary type, as a human-readable string
     * @param isUpdatable whether to support dynamically updating the dictionary. Please note that
     *        dynamic dictionary has negative effects on memory space and computation time.
     */
    public ExpandableBinaryDictionary(final Context context, final String filename,
            final String dictType, final boolean isUpdatable) {
        super(dictType);
        mFilename = filename;
        mContext = context;
        mIsUpdatable = isUpdatable;
        mBinaryDictionary = null;
        mFilenameDictionaryTimeRecorder = getDictionaryTimeRecorder(filename);
        // Currently, only dynamic personalization dictionary is updatable.
        mDictionaryWriter = getDictionaryWriter(context, dictType, isUpdatable);
    }

    protected static String getFilenameWithLocale(final String name, final String localeStr) {
        return name + "." + localeStr + DICT_FILE_EXTENSION;
    }

    /**
     * Closes and cleans up the binary dictionary.
     */
    @Override
    public void close() {
        getExecutor(mFilename).execute(new Runnable() {
            @Override
            public void run() {
                if (mBinaryDictionary!= null) {
                    mBinaryDictionary.close();
                    mBinaryDictionary = null;
                }
                mDictionaryWriter.close();
            }
        });
    }

    protected void closeBinaryDictionary() {
        // Ensure that no other threads are accessing the local binary dictionary.
        getExecutor(mFilename).execute(new Runnable() {
            @Override
            public void run() {
                if (mBinaryDictionary != null) {
                    mBinaryDictionary.close();
                    mBinaryDictionary = null;
                }
            }
        });
    }

    protected void clear() {
        getExecutor(mFilename).execute(new Runnable() {
            @Override
            public void run() {
                mDictionaryWriter.clear();
            }
        });
    }

    /**
     * Adds a word unigram to the dictionary. Used for loading a dictionary.
     */
    protected void addWord(final String word, final String shortcutTarget,
            final int frequency, final boolean isNotAWord) {
        mDictionaryWriter.addUnigramWord(word, shortcutTarget, frequency, isNotAWord);
    }

    /**
     * Adds a word bigram in the dictionary. Used for loading a dictionary.
     */
    protected void addBigram(final String prevWord, final String word, final int frequency,
            final long lastModifiedTime) {
        mDictionaryWriter.addBigramWords(prevWord, word, frequency, true /* isValid */,
                lastModifiedTime);
    }

    /**
     * Dynamically adds a word unigram to the dictionary. May overwrite an existing entry.
     */
    protected void addWordDynamically(final String word, final String shortcutTarget,
            final int frequency, final boolean isNotAWord) {
        if (!mIsUpdatable) {
            Log.w(TAG, "addWordDynamically is called for non-updatable dictionary: " + mFilename);
            return;
        }

        getExecutor(mFilename).execute(new Runnable() {
            @Override
            public void run() {
                if (ENABLE_BINARY_DICTIONARY_DYNAMIC_UPDATE) {
                    mBinaryDictionary.addUnigramWord(word, frequency);
                }
                // TODO: Remove.
                mDictionaryWriter.addUnigramWord(word, shortcutTarget, frequency, isNotAWord);
            }
        });
    }

    /**
     * Dynamically adds a word bigram in the dictionary. May overwrite an existing entry.
     */
    protected void addBigramDynamically(final String word0, final String word1,
            final int frequency, final boolean isValid) {
        if (!mIsUpdatable) {
            Log.w(TAG, "addBigramDynamically is called for non-updatable dictionary: "
                    + mFilename);
            return;
        }

        getExecutor(mFilename).execute(new Runnable() {
            @Override
            public void run() {
                if (ENABLE_BINARY_DICTIONARY_DYNAMIC_UPDATE) {
                    mBinaryDictionary.addBigramWords(word0, word1, frequency);
                }
                // TODO: Remove.
                mDictionaryWriter.addBigramWords(word0, word1, frequency, isValid,
                        0 /* lastTouchedTime */);
            }
        });
    }

    /**
     * Dynamically remove a word bigram in the dictionary.
     */
    protected void removeBigramDynamically(final String word0, final String word1) {
        if (!mIsUpdatable) {
            Log.w(TAG, "removeBigramDynamically is called for non-updatable dictionary: "
                    + mFilename);
            return;
        }

        getExecutor(mFilename).execute(new Runnable() {
            @Override
            public void run() {
                if (ENABLE_BINARY_DICTIONARY_DYNAMIC_UPDATE) {
                    mBinaryDictionary.removeBigramWords(word0, word1);
                }
                // TODO: Remove.
                mDictionaryWriter.removeBigramWords(word0, word1);
            }
        });
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final WordComposer composer,
            final String prevWord, final ProximityInfo proximityInfo,
            final boolean blockOffensiveWords, final int[] additionalFeaturesOptions) {
        reloadDictionaryIfRequired();
        final ArrayList<SuggestedWordInfo> suggestions = CollectionUtils.newArrayList();
        final AsyncResultHolder<ArrayList<SuggestedWordInfo>> holder =
                new AsyncResultHolder<ArrayList<SuggestedWordInfo>>();
        getExecutor(mFilename).executePrioritized(new Runnable() {
            @Override
            public void run() {
                final ArrayList<SuggestedWordInfo> inMemDictSuggestion =
                        mDictionaryWriter.getSuggestions(composer, prevWord, proximityInfo,
                                blockOffensiveWords, additionalFeaturesOptions);
                // TODO: Remove checking mIsUpdatable and use native suggestion.
                if (mBinaryDictionary != null && !mIsUpdatable) {
                    final ArrayList<SuggestedWordInfo> binarySuggestion =
                            mBinaryDictionary.getSuggestions(composer, prevWord, proximityInfo,
                                    blockOffensiveWords, additionalFeaturesOptions);
                    if (inMemDictSuggestion == null) {
                        holder.set(binarySuggestion);
                    } else if (binarySuggestion == null) {
                        holder.set(inMemDictSuggestion);
                    } else {
                        binarySuggestion.addAll(inMemDictSuggestion);
                        holder.set(binarySuggestion);
                    }
                } else {
                    holder.set(inMemDictSuggestion);
                }
            }
        });

        return holder.get(null, TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS);
    }

    @Override
    public boolean isValidWord(final String word) {
        reloadDictionaryIfRequired();
        return isValidWordInner(word);
    }

    protected boolean isValidWordInner(final String word) {
        final AsyncResultHolder<Boolean> holder = new AsyncResultHolder<Boolean>();
        getExecutor(mFilename).executePrioritized(new Runnable() {
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
        mPerInstanceDictionaryTimeRecorder.mLastUpdateRequestTime = SystemClock.uptimeMillis();
        reloadDictionaryIfRequired();
    }

    /**
     * Loads the current binary dictionary from internal storage. Assumes the dictionary file
     * exists.
     */
    private void loadBinaryDictionary() {
        if (DEBUG) {
            Log.d(TAG, "Loading binary dictionary: " + mFilename + " request="
                    + mFilenameDictionaryTimeRecorder.mLastUpdateRequestTime + " update="
                    + mFilenameDictionaryTimeRecorder.mLastUpdateTime);
        }

        final File file = new File(mContext.getFilesDir(), mFilename);
        final String filename = file.getAbsolutePath();
        final long length = file.length();

        // Build the new binary dictionary
        final BinaryDictionary newBinaryDictionary = new BinaryDictionary(filename, 0, length,
                true /* useFullEditDistance */, null, mDictType, mIsUpdatable);

        // Ensure all threads accessing the current dictionary have finished before swapping in
        // the new one.
        final BinaryDictionary oldBinaryDictionary = mBinaryDictionary;
        getExecutor(mFilename).executePrioritized(new Runnable() {
            @Override
            public void run() {
                mBinaryDictionary = newBinaryDictionary;
                if (oldBinaryDictionary != null) {
                    oldBinaryDictionary.close();
                }
            }
        });
    }

    /**
     * Abstract method for checking if it is required to reload the dictionary before writing
     * a binary dictionary.
     */
    abstract protected boolean needsToReloadBeforeWriting();

    /**
     * Writes a new binary dictionary based on the contents of the fusion dictionary.
     */
    private void writeBinaryDictionary() {
        if (DEBUG) {
            Log.d(TAG, "Generating binary dictionary: " + mFilename + " request="
                    + mFilenameDictionaryTimeRecorder.mLastUpdateRequestTime + " update="
                    + mFilenameDictionaryTimeRecorder.mLastUpdateTime);
        }
        if (needsToReloadBeforeWriting()) {
            mDictionaryWriter.clear();
            loadDictionaryAsync();
        }
        mDictionaryWriter.write(mFilename);
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
        mPerInstanceDictionaryTimeRecorder.mLastUpdateRequestTime = time;
        mFilenameDictionaryTimeRecorder.mLastUpdateRequestTime = time;
        if (DEBUG) {
            Log.d(TAG, "Reload request: " + mFilename + ": request=" + time + " update="
                    + mFilenameDictionaryTimeRecorder.mLastUpdateTime);
        }
    }

    /**
     * Reloads the dictionary if required.
     */
    public final void reloadDictionaryIfRequired() {
        if (!isReloadRequired()) return;
        reloadDictionary();
    }

    /**
     * Returns whether a dictionary reload is required.
     */
    private boolean isReloadRequired() {
        return mBinaryDictionary == null || mPerInstanceDictionaryTimeRecorder.isOutOfDate();
    }

    /**
     * Reloads the dictionary. Access is controlled on a per dictionary file basis and supports
     * concurrent calls from multiple instances that share the same dictionary file.
     */
    private final void reloadDictionary() {
        // Ensure that only one thread attempts to read or write to the shared binary dictionary
        // file at the same time.
        getExecutor(mFilename).execute(new Runnable() {
            @Override
            public void run() {
                final long time = SystemClock.uptimeMillis();
                final boolean dictionaryFileExists = dictionaryFileExists();
                if (mFilenameDictionaryTimeRecorder.isOutOfDate() || !dictionaryFileExists) {
                    // If the shared dictionary file does not exist or is out of date, the first
                    // instance that acquires the lock will generate a new one.
                    if (hasContentChanged() || !dictionaryFileExists) {
                        // If the source content has changed or the dictionary does not exist,
                        // rebuild the binary dictionary. Empty dictionaries are supported (in the
                        // case where loadDictionaryAsync() adds nothing) in order to provide a
                        // uniform framework.
                        mFilenameDictionaryTimeRecorder.mLastUpdateTime = time;
                        writeBinaryDictionary();
                        loadBinaryDictionary();
                    } else {
                        // If not, the reload request was unnecessary so revert
                        // LastUpdateRequestTime to LastUpdateTime.
                        mFilenameDictionaryTimeRecorder.mLastUpdateRequestTime =
                                mFilenameDictionaryTimeRecorder.mLastUpdateTime;
                    }
                } else if (mBinaryDictionary == null ||
                        mPerInstanceDictionaryTimeRecorder.mLastUpdateTime
                                < mFilenameDictionaryTimeRecorder.mLastUpdateTime) {
                    // Otherwise, if the local dictionary is older than the shared dictionary, load
                    // the shared dictionary.
                    loadBinaryDictionary();
                }
                if (mBinaryDictionary != null && !mBinaryDictionary.isValidDictionary()) {
                    // Binary dictionary is not valid. Regenerate the dictionary file.
                    mFilenameDictionaryTimeRecorder.mLastUpdateTime = time;
                    writeBinaryDictionary();
                    loadBinaryDictionary();
                }
                mPerInstanceDictionaryTimeRecorder.mLastUpdateTime = time;
            }
        });
    }

    // TODO: cache the file's existence so that we avoid doing a disk access each time.
    private boolean dictionaryFileExists() {
        final File file = new File(mContext.getFilesDir(), mFilename);
        return file.exists();
    }

    /**
     * Load the dictionary to memory.
     */
    protected void asyncLoadDictionaryToMemory() {
        getExecutor(mFilename).executePrioritized(new Runnable() {
            @Override
            public void run() {
                loadDictionaryAsync();
            }
        });
    }

    /**
     * Generate binary dictionary using DictionaryWriter.
     */
    protected void asyncWriteBinaryDictionary() {
        final Runnable newTask = new Runnable() {
            @Override
            public void run() {
                writeBinaryDictionary();
            }
        };
        final Runnable oldTask = mUnfinishedFlushingTask.getAndSet(newTask);
        getExecutor(mFilename).replaceAndExecute(oldTask, newTask);
    }

    /**
     * Time recorder for tracking whether the dictionary is out of date.
     * Can be shared across multiple dictionary instances that access the same filename.
     */
    private static class DictionaryTimeRecorder {
        private volatile long mLastUpdateTime = 0;
        private volatile long mLastUpdateRequestTime = 0;

        private boolean isOutOfDate() {
            return (mLastUpdateRequestTime > mLastUpdateTime);
        }
    }

    /**
     * Dynamically adds a word unigram to the dictionary for testing with blocking-lock.
     */
    @UsedForTesting
    protected void addWordDynamicallyForTests(final String word, final String shortcutTarget,
            final int frequency, final boolean isNotAWord) {
        getExecutor(mFilename).executePrioritized(new Runnable() {
            @Override
            public void run() {
                addWordDynamically(word, shortcutTarget, frequency, isNotAWord);
            }
        });
    }

    /**
     * Dynamically adds a word bigram in the dictionary for testing with blocking-lock.
     */
    @UsedForTesting
    protected void addBigramDynamicallyForTests(final String word0, final String word1,
            final int frequency, final boolean isValid) {
        getExecutor(mFilename).executePrioritized(new Runnable() {
            @Override
            public void run() {
                addBigramDynamically(word0, word1, frequency, isValid);
            }
        });
    }

    /**
     * Dynamically remove a word bigram in the dictionary for testing with blocking-lock.
     */
    @UsedForTesting
    protected void removeBigramDynamicallyForTests(final String word0, final String word1) {
        getExecutor(mFilename).executePrioritized(new Runnable() {
            @Override
            public void run() {
                removeBigramDynamically(word0, word1);
            }
        });
    }

    // TODO: Implement native binary methods once the dynamic dictionary implementation is done.
    @UsedForTesting
    public boolean isInDictionaryForTests(final String word) {
        final AsyncResultHolder<Boolean> holder = new AsyncResultHolder<Boolean>();
        getExecutor(mFilename).executePrioritized(new Runnable() {
            @Override
            public void run() {
                if (mDictType == Dictionary.TYPE_USER_HISTORY) {
                    holder.set(((DynamicPersonalizationDictionaryWriter) mDictionaryWriter)
                            .isInDictionaryForTests(word));
                }
            }
        });
        return holder.get(false, TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS);
    }
}
