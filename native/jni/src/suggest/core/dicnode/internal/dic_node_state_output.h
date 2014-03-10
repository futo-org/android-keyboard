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

#ifndef LATINIME_DIC_NODE_STATE_OUTPUT_H
#define LATINIME_DIC_NODE_STATE_OUTPUT_H

#include <algorithm>
#include <cstring> // for memmove()
#include <stdint.h>

#include "defines.h"

namespace latinime {

class DicNodeStateOutput {
 public:
    DicNodeStateOutput() : mOutputtedCodePointCount(0) {}

    ~DicNodeStateOutput() {}

    void init() {
        mOutputtedCodePointCount = 0;
        mCodePointsBuf[0] = 0;
    }

    void init(const DicNodeStateOutput *const stateOutput) {
        memmove(mCodePointsBuf, stateOutput->mCodePointsBuf,
                stateOutput->mOutputtedCodePointCount * sizeof(mCodePointsBuf[0]));
        mOutputtedCodePointCount = stateOutput->mOutputtedCodePointCount;
        if (mOutputtedCodePointCount < MAX_WORD_LENGTH) {
            mCodePointsBuf[mOutputtedCodePointCount] = 0;
        }
    }

    void addMergedNodeCodePoints(const uint16_t mergedNodeCodePointCount,
            const int *const mergedNodeCodePoints) {
        if (mergedNodeCodePoints) {
            const int additionalCodePointCount = std::min(
                    static_cast<int>(mergedNodeCodePointCount),
                    MAX_WORD_LENGTH - mOutputtedCodePointCount);
            memmove(&mCodePointsBuf[mOutputtedCodePointCount], mergedNodeCodePoints,
                    additionalCodePointCount * sizeof(mCodePointsBuf[0]));
            mOutputtedCodePointCount = static_cast<uint16_t>(
                    mOutputtedCodePointCount + mergedNodeCodePointCount);
            if (mOutputtedCodePointCount < MAX_WORD_LENGTH) {
                mCodePointsBuf[mOutputtedCodePointCount] = 0;
            }
        }
    }

    int getCodePointAt(const int index) const {
        return mCodePointsBuf[index];
    }

    const int *getCodePointBuf() const {
        return mCodePointsBuf;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(DicNodeStateOutput);

    uint16_t mOutputtedCodePointCount;
    int mCodePointsBuf[MAX_WORD_LENGTH];
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_STATE_OUTPUT_H
