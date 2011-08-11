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

#ifndef LATINIME_CORRECTION_STATE_H
#define LATINIME_CORRECTION_STATE_H

#include <stdint.h>

#include "defines.h"

namespace latinime {

struct CorrectionState {
    int mParentIndex;
    int mSiblingPos;
    uint16_t mChildCount;
    uint8_t mInputIndex;
    uint8_t mProximityCount;
    uint8_t mSkippedCount;
    int8_t mSkipPos; // should be signed
    bool mMatching;
    bool mSkipping;
    bool mProximityMatching;
    bool mNeedsToTraverseAllNodes;

};

inline static void initCorrectionState(CorrectionState *state, const int rootPos,
        const uint16_t childCount, const bool traverseAll) {
    state->mParentIndex = -1;
    state->mChildCount = childCount;
    state->mInputIndex = 0;
    state->mProximityCount = 0;
    state->mSiblingPos = rootPos;
    state->mSkippedCount = 0;
    state->mMatching = false;
    state->mSkipping = false;
    state->mProximityMatching = false;
    state->mNeedsToTraverseAllNodes = traverseAll;
    state->mSkipPos = -1;
}

} // namespace latinime
#endif // LATINIME_CORRECTION_STATE_H
