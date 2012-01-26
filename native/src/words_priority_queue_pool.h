/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef LATINIME_WORDS_PRIORITY_QUEUE_POOL_H
#define LATINIME_WORDS_PRIORITY_QUEUE_POOL_H

#include <assert.h>
#include <new>
#include "words_priority_queue.h"

namespace latinime {

class WordsPriorityQueuePool {
 public:
    WordsPriorityQueuePool(int mainQueueMaxWords, int subQueueMaxWords, int maxWordLength) {
        mMasterQueue = new(mMasterQueueBuf) WordsPriorityQueue(mainQueueMaxWords, maxWordLength);
        for (int i = 0, subQueueBufOffset = 0; i < SUB_QUEUE_MAX_COUNT;
                ++i, subQueueBufOffset += sizeof(WordsPriorityQueue)) {
            mSubQueues1[i] = new(mSubQueueBuf1 + subQueueBufOffset)
                    WordsPriorityQueue(subQueueMaxWords, maxWordLength);
            mSubQueues2[i] = new(mSubQueueBuf2 + subQueueBufOffset)
                    WordsPriorityQueue(subQueueMaxWords, maxWordLength);
        }
    }

    virtual ~WordsPriorityQueuePool() {
    }

    WordsPriorityQueue* getMasterQueue() {
        return mMasterQueue;
    }

    WordsPriorityQueue* getSubQueue(const int wordIndex, const int inputWordLength) {
        if (wordIndex > SUB_QUEUE_MAX_WORD_INDEX) {
            return 0;
        }
        if (inputWordLength < 0 || inputWordLength >= SUB_QUEUE_MAX_COUNT) {
            if (DEBUG_WORDS_PRIORITY_QUEUE) {
                assert(false);
            }
            return 0;
        }
        // TODO: Come up with more generic pool
        if (wordIndex == 1) {
            return mSubQueues1[inputWordLength];
        } else if (wordIndex == 2) {
            return mSubQueues2[inputWordLength];
        } else {
            return 0;
        }
    }

    inline void clearAll() {
        mMasterQueue->clear();
        for (int i = 0; i < SUB_QUEUE_MAX_COUNT; ++i) {
            mSubQueues1[i]->clear();
            mSubQueues2[i]->clear();
        }
    }

    inline void clearSubQueue(const int wordIndex) {
        for (int i = 0; i < SUB_QUEUE_MAX_COUNT; ++i) {
            if (wordIndex == 1) {
                mSubQueues1[i]->clear();
            } else if (wordIndex == 2) {
                mSubQueues2[i]->clear();
            }
        }
    }

    void dumpSubQueue1TopSuggestions() {
        AKLOGI("DUMP SUBQUEUE1 TOP SUGGESTIONS");
        for (int i = 0; i < SUB_QUEUE_MAX_COUNT; ++i) {
            mSubQueues1[i]->dumpTopWord();
        }
    }

 private:
    WordsPriorityQueue* mMasterQueue;
    WordsPriorityQueue* mSubQueues1[SUB_QUEUE_MAX_COUNT];
    WordsPriorityQueue* mSubQueues2[SUB_QUEUE_MAX_COUNT];
    char mMasterQueueBuf[sizeof(WordsPriorityQueue)];
    char mSubQueueBuf1[SUB_QUEUE_MAX_COUNT * sizeof(WordsPriorityQueue)];
    char mSubQueueBuf2[SUB_QUEUE_MAX_COUNT * sizeof(WordsPriorityQueue)];
};
}

#endif // LATINIME_WORDS_PRIORITY_QUEUE_POOL_H
