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

#include "defines.h"
#include "words_priority_queue.h"

namespace latinime {

class WordsPriorityQueuePool {
 public:
    WordsPriorityQueuePool(int mainQueueMaxWords, int subQueueMaxWords)
            // Note: using placement new() requires the caller to call the destructor explicitly.
            : mMasterQueue(new(mMasterQueueBuf) WordsPriorityQueue(mainQueueMaxWords)) {
        for (int i = 0, subQueueBufOffset = 0;
                i < MULTIPLE_WORDS_SUGGESTION_MAX_WORDS * SUB_QUEUE_MAX_COUNT;
                ++i, subQueueBufOffset += static_cast<int>(sizeof(WordsPriorityQueue))) {
            mSubQueues[i] = new(mSubQueueBuf + subQueueBufOffset)
                    WordsPriorityQueue(subQueueMaxWords);
        }
    }

    // Non virtual inline destructor -- never inherit this class
    ~WordsPriorityQueuePool() {
        // Note: these explicit calls to the destructor match the calls to placement new() above.
        if (mMasterQueue) mMasterQueue->~WordsPriorityQueue();
        for (int i = 0; i < MULTIPLE_WORDS_SUGGESTION_MAX_WORDS * SUB_QUEUE_MAX_COUNT; ++i) {
            if (mSubQueues[i]) mSubQueues[i]->~WordsPriorityQueue();
        }
    }

    WordsPriorityQueue *getMasterQueue() const {
        return mMasterQueue;
    }

    WordsPriorityQueue *getSubQueue(const int wordIndex, const int inputWordLength) const {
        if (wordIndex >= MULTIPLE_WORDS_SUGGESTION_MAX_WORDS) {
            return 0;
        }
        if (inputWordLength < 0 || inputWordLength >= SUB_QUEUE_MAX_COUNT) {
            if (DEBUG_WORDS_PRIORITY_QUEUE) {
                ASSERT(false);
            }
            return 0;
        }
        return mSubQueues[wordIndex * SUB_QUEUE_MAX_COUNT + inputWordLength];
    }

    inline void clearAll() {
        mMasterQueue->clear();
        for (int i = 0; i < MULTIPLE_WORDS_SUGGESTION_MAX_WORDS; ++i) {
            clearSubQueue(i);
        }
    }

    AK_FORCE_INLINE void clearSubQueue(const int wordIndex) {
        for (int i = 0; i < SUB_QUEUE_MAX_COUNT; ++i) {
            WordsPriorityQueue *queue = getSubQueue(wordIndex, i);
            if (queue) {
                queue->clear();
            }
        }
    }

    void dumpSubQueue1TopSuggestions() const {
        AKLOGI("DUMP SUBQUEUE1 TOP SUGGESTIONS");
        for (int i = 0; i < SUB_QUEUE_MAX_COUNT; ++i) {
            getSubQueue(0, i)->dumpTopWord();
        }
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(WordsPriorityQueuePool);
    char mMasterQueueBuf[sizeof(WordsPriorityQueue)];
    char mSubQueueBuf[SUB_QUEUE_MAX_COUNT * MULTIPLE_WORDS_SUGGESTION_MAX_WORDS
            * sizeof(WordsPriorityQueue)];
    WordsPriorityQueue *mMasterQueue;
    WordsPriorityQueue *mSubQueues[SUB_QUEUE_MAX_COUNT * MULTIPLE_WORDS_SUGGESTION_MAX_WORDS];
};
} // namespace latinime
#endif // LATINIME_WORDS_PRIORITY_QUEUE_POOL_H
