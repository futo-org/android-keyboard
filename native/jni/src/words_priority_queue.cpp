/*
 * Copyright (C) 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "words_priority_queue.h"

namespace latinime {

int WordsPriorityQueue::outputSuggestions(const int *before, const int beforeLength,
        int *frequencies, int *outputCodePoints, int* outputTypes) {
    mHighestSuggestedWord = 0;
    const int size = min(MAX_WORDS, static_cast<int>(mSuggestions.size()));
    SuggestedWord *swBuffer[size];
    int index = size - 1;
    while (!mSuggestions.empty() && index >= 0) {
        SuggestedWord *sw = mSuggestions.top();
        if (DEBUG_WORDS_PRIORITY_QUEUE) {
            AKLOGI("dump word. %d", sw->mScore);
            DUMP_WORD(sw->mWord, sw->mWordLength);
        }
        swBuffer[index] = sw;
        mSuggestions.pop();
        --index;
    }
    if (size >= 2) {
        SuggestedWord *nsMaxSw = 0;
        int maxIndex = 0;
        float maxNs = 0;
        for (int i = 0; i < size; ++i) {
            SuggestedWord *tempSw = swBuffer[i];
            if (!tempSw) {
                continue;
            }
            const float tempNs = getNormalizedScore(tempSw, before, beforeLength, 0, 0, 0);
            if (tempNs >= maxNs) {
                maxNs = tempNs;
                maxIndex = i;
                nsMaxSw = tempSw;
            }
        }
        if (maxIndex > 0 && nsMaxSw) {
            memmove(&swBuffer[1], &swBuffer[0], maxIndex * sizeof(swBuffer[0]));
            swBuffer[0] = nsMaxSw;
        }
    }
    for (int i = 0; i < size; ++i) {
        SuggestedWord *sw = swBuffer[i];
        if (!sw) {
            AKLOGE("SuggestedWord is null %d", i);
            continue;
        }
        const int wordLength = sw->mWordLength;
        int *targetAddress = outputCodePoints + i * MAX_WORD_LENGTH;
        frequencies[i] = sw->mScore;
        outputTypes[i] = sw->mType;
        memcpy(targetAddress, sw->mWord, wordLength * sizeof(targetAddress[0]));
        if (wordLength < MAX_WORD_LENGTH) {
            targetAddress[wordLength] = 0;
        }
        sw->mUsed = false;
    }
    return size;
}
} // namespace latinime
