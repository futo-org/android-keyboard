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

#include "suggest/core/session/dic_traverse_session.h"

#include "defines.h"
#include "jni.h"
#include "suggest/core/dicnode/dic_node_utils.h"
#include "suggest/core/dictionary/binary_dictionary_header.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/core/dictionary/binary_format.h"
#include "suggest/core/dictionary/dictionary.h"

namespace latinime {

void DicTraverseSession::init(const Dictionary *const dictionary, const int *prevWord,
        int prevWordLength, const SuggestOptions *const suggestOptions) {
    mDictionary = dictionary;
    mMultiWordCostMultiplier = mDictionary->getBinaryDictionaryInfo()
            ->getHeader()->getMultiWordCostMultiplier();
    mSuggestOptions = suggestOptions;
    if (!prevWord) {
        mPrevWordPos = NOT_VALID_WORD;
        return;
    }
    // TODO: merge following similar calls to getTerminalPosition into one case-insensitive call.
    mPrevWordPos = BinaryFormat::getTerminalPosition(
            dictionary->getBinaryDictionaryInfo()->getDictRoot(), prevWord,
            prevWordLength, false /* forceLowerCaseSearch */);
    if (mPrevWordPos == NOT_VALID_WORD) {
        // Check bigrams for lower-cased previous word if original was not found. Useful for
        // auto-capitalized words like "The [current_word]".
        mPrevWordPos = BinaryFormat::getTerminalPosition(
                dictionary->getBinaryDictionaryInfo()->getDictRoot(), prevWord,
                prevWordLength, true /* forceLowerCaseSearch */);
    }
}

void DicTraverseSession::setupForGetSuggestions(const ProximityInfo *pInfo,
        const int *inputCodePoints, const int inputSize, const int *const inputXs,
        const int *const inputYs, const int *const times, const int *const pointerIds,
        const float maxSpatialDistance, const int maxPointerCount) {
    mProximityInfo = pInfo;
    mMaxPointerCount = maxPointerCount;
    initializeProximityInfoStates(inputCodePoints, inputXs, inputYs, times, pointerIds, inputSize,
            maxSpatialDistance, maxPointerCount);
}

const BinaryDictionaryInfo *DicTraverseSession::getBinaryDictionaryInfo() const {
    return mDictionary->getBinaryDictionaryInfo();
}

void DicTraverseSession::resetCache(const int nextActiveCacheSize, const int maxWords) {
    mDicNodesCache.reset(nextActiveCacheSize, maxWords);
    mMultiBigramMap.clear();
    mPartiallyCommited = false;
}

void DicTraverseSession::initializeProximityInfoStates(const int *const inputCodePoints,
        const int *const inputXs, const int *const inputYs, const int *const times,
        const int *const pointerIds, const int inputSize, const float maxSpatialDistance,
        const int maxPointerCount) {
    ASSERT(1 <= maxPointerCount && maxPointerCount <= MAX_POINTER_COUNT_G);
    mInputSize = 0;
    for (int i = 0; i < maxPointerCount; ++i) {
        mProximityInfoStates[i].initInputParams(i, maxSpatialDistance, getProximityInfo(),
                inputCodePoints, inputSize, inputXs, inputYs, times, pointerIds,
                maxPointerCount == MAX_POINTER_COUNT_G
                /* TODO: this is a hack. fix proximity info state */);
        mInputSize += mProximityInfoStates[i].size();
    }
}
} // namespace latinime
