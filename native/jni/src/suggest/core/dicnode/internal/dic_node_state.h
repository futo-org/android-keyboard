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

#ifndef LATINIME_DIC_NODE_STATE_H
#define LATINIME_DIC_NODE_STATE_H

#include "defines.h"
#include "suggest/core/dicnode/internal/dic_node_state_input.h"
#include "suggest/core/dicnode/internal/dic_node_state_output.h"
#include "suggest/core/dicnode/internal/dic_node_state_prevword.h"
#include "suggest/core/dicnode/internal/dic_node_state_scoring.h"

namespace latinime {

class DicNodeState {
 public:
    DicNodeStateInput mDicNodeStateInput;
    DicNodeStateOutput mDicNodeStateOutput;
    DicNodeStatePrevWord mDicNodeStatePrevWord;
    DicNodeStateScoring mDicNodeStateScoring;

    AK_FORCE_INLINE DicNodeState()
            : mDicNodeStateInput(), mDicNodeStateOutput(), mDicNodeStatePrevWord(),
              mDicNodeStateScoring() {
    }

    virtual ~DicNodeState() {}

    // Init with prevWordPos
    void init(const int prevWordPos) {
        mDicNodeStateInput.init();
        mDicNodeStateOutput.init();
        mDicNodeStatePrevWord.init(prevWordPos);
        mDicNodeStateScoring.init();
    }

    // Init by copy
    AK_FORCE_INLINE void init(const DicNodeState *const src) {
        mDicNodeStateInput.init(&src->mDicNodeStateInput);
        mDicNodeStateOutput.init(&src->mDicNodeStateOutput);
        mDicNodeStatePrevWord.init(&src->mDicNodeStatePrevWord);
        mDicNodeStateScoring.init(&src->mDicNodeStateScoring);
    }

    // Init by copy and adding merged node code points.
    void init(const DicNodeState *const src, const uint16_t mergedNodeCodePointCount,
            const int *const mergedNodeCodePoints) {
        init(src);
        mDicNodeStateOutput.addMergedNodeCodePoints(
                mergedNodeCodePointCount, mergedNodeCodePoints);
    }

 private:
    // Caution!!!
    // Use a default copy constructor and an assign operator because shallow copies are ok
    // for this class
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_STATE_H
