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

#include "words_priority_queue.h"

namespace latinime {

class WordsPriorityQueuePool {
public:
    WordsPriorityQueuePool(int mainQueueMaxWords, int subQueueMaxWords, int maxWordLength) {
        mMasterQueue = new WordsPriorityQueue(mainQueueMaxWords, maxWordLength);
        mSubQueue1 = new WordsPriorityQueue(subQueueMaxWords, maxWordLength);
        mSubQueue2 = new WordsPriorityQueue(subQueueMaxWords, maxWordLength);
    }

    ~WordsPriorityQueuePool() {
        delete mMasterQueue;
    }

    WordsPriorityQueue* getMasterQueue() {
        return mMasterQueue;
    }
    // TODO: Come up with more generic pool
    WordsPriorityQueue* getSubQueue1() {
        return mSubQueue1;
    }
    WordsPriorityQueue* getSubQueue2() {
        return mSubQueue2;
    }
private:
    WordsPriorityQueue *mMasterQueue;
    WordsPriorityQueue *mSubQueue1;
    WordsPriorityQueue *mSubQueue2;
};
}

#endif // LATINIME_WORDS_PRIORITY_QUEUE_POOL_H
