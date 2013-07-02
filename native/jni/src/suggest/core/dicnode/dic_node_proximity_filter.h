/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef LATINIME_DIC_NODE_PROXIMITY_FILTER_H
#define LATINIME_DIC_NODE_PROXIMITY_FILTER_H

#include "defines.h"
#include "suggest/core/layout/proximity_info_state.h"
#include "suggest/core/layout/proximity_info_utils.h"
#include "suggest/core/policy/dictionary_structure_policy.h"

namespace latinime {

class DicNodeProximityFilter : public DictionaryStructurePolicy::NodeFilter {
 public:
    DicNodeProximityFilter(const ProximityInfoState *const pInfoState,
            const int pointIndex, const bool exactOnly)
            : mProximityInfoState(pInfoState), mPointIndex(pointIndex), mExactOnly(exactOnly) {}

    bool isFilteredOut(const int codePoint) const {
        return !isProximityCodePoint(codePoint);
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DicNodeProximityFilter);

    const ProximityInfoState *const mProximityInfoState;
    const int mPointIndex;
    const bool mExactOnly;

    // TODO: Move to proximity info state
    bool isProximityCodePoint(const int codePoint) const {
        if (!mProximityInfoState) {
            return true;
        }
        if (mExactOnly) {
            return mProximityInfoState->getPrimaryCodePointAt(mPointIndex) == codePoint;
        }
        const ProximityType matchedId = mProximityInfoState->getProximityType(
                mPointIndex, codePoint, true /* checkProximityChars */);
        return ProximityInfoUtils::isMatchOrProximityChar(matchedId);
    }
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_PROXIMITY_FILTER_H
