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

#include <cstring> // for memcpy()
#include <stdint.h>

#include "defines.h"

namespace latinime {

class DicNodeStateOutput {
 public:
    DicNodeStateOutput() : mOutputtedLength(0) {
        init();
    }

    virtual ~DicNodeStateOutput() {}

    void init() {
        mOutputtedLength = 0;
        mWordBuf[0] = 0;
    }

    void init(const DicNodeStateOutput *const stateOutput) {
        memcpy(mWordBuf, stateOutput->mWordBuf,
                stateOutput->mOutputtedLength * sizeof(mWordBuf[0]));
        mOutputtedLength = stateOutput->mOutputtedLength;
        if (mOutputtedLength < MAX_WORD_LENGTH) {
            mWordBuf[mOutputtedLength] = 0;
        }
    }

    void addSubword(const uint16_t additionalSubwordLength, const int *const additionalSubword) {
        if (additionalSubword) {
            memcpy(&mWordBuf[mOutputtedLength], additionalSubword,
                    additionalSubwordLength * sizeof(mWordBuf[0]));
            mOutputtedLength = static_cast<uint16_t>(mOutputtedLength + additionalSubwordLength);
            if (mOutputtedLength < MAX_WORD_LENGTH) {
                mWordBuf[mOutputtedLength] = 0;
            }
        }
    }

    // TODO: Remove
    int getCodePointAt(const int id) const {
        return mWordBuf[id];
    }

    // TODO: Move to private
    int mWordBuf[MAX_WORD_LENGTH];

 private:
    // Caution!!!
    // Use a default copy constructor and an assign operator because shallow copies are ok
    // for this class
    uint16_t mOutputtedLength;
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_STATE_OUTPUT_H
