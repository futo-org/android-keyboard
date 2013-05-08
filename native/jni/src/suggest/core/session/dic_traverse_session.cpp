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

#include "binary_format.h"
#include "defines.h"
#include "dictionary.h"
#include "dic_traverse_wrapper.h"
#include "jni.h"
#include "suggest/core/dicnode/dic_node_utils.h"

namespace latinime {

const int DicTraverseSession::CACHE_START_INPUT_LENGTH_THRESHOLD = 20;

// A factory method for DicTraverseSession
static void *getSessionInstance(JNIEnv *env, jstring localeStr) {
    return new DicTraverseSession(env, localeStr);
}

// TODO: Pass "DicTraverseSession *traverseSession" when the source code structure settles down.
static void initSessionInstance(void *traverseSession, const Dictionary *const dictionary,
        const int *prevWord, const int prevWordLength) {
    if (traverseSession) {
        DicTraverseSession *tSession = static_cast<DicTraverseSession *>(traverseSession);
        tSession->init(dictionary, prevWord, prevWordLength);
    }
}

// TODO: Pass "DicTraverseSession *traverseSession" when the source code structure settles down.
static void releaseSessionInstance(void *traverseSession) {
    delete static_cast<DicTraverseSession *>(traverseSession);
}

// An ad-hoc internal class to register the factory method defined above
class TraverseSessionFactoryRegisterer {
 public:
    TraverseSessionFactoryRegisterer() {
        DicTraverseWrapper::setTraverseSessionFactoryMethod(getSessionInstance);
        DicTraverseWrapper::setTraverseSessionInitMethod(initSessionInstance);
        DicTraverseWrapper::setTraverseSessionReleaseMethod(releaseSessionInstance);
    }
 private:
    DISALLOW_COPY_AND_ASSIGN(TraverseSessionFactoryRegisterer);
};

// To invoke the TraverseSessionFactoryRegisterer constructor in the global constructor.
static TraverseSessionFactoryRegisterer traverseSessionFactoryRegisterer;

void DicTraverseSession::init(const Dictionary *const dictionary, const int *prevWord,
        int prevWordLength) {
    mDictionary = dictionary;
    mMultiWordCostMultiplier = BinaryFormat::getMultiWordCostMultiplier(mDictionary->getDict(),
            mDictionary->getDictSize());
    if (!prevWord) {
        mPrevWordPos = NOT_VALID_WORD;
        return;
    }
    // TODO: merge following similar calls to getTerminalPosition into one case-insensitive call.
    mPrevWordPos = BinaryFormat::getTerminalPosition(dictionary->getOffsetDict(), prevWord,
            prevWordLength, false /* forceLowerCaseSearch */);
    if (mPrevWordPos == NOT_VALID_WORD) {
        // Check bigrams for lower-cased previous word if original was not found. Useful for
        // auto-capitalized words like "The [current_word]".
        mPrevWordPos = BinaryFormat::getTerminalPosition(dictionary->getOffsetDict(), prevWord,
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

const uint8_t *DicTraverseSession::getOffsetDict() const {
    return mDictionary->getOffsetDict();
}

int DicTraverseSession::getDictFlags() const {
    return mDictionary->getDictFlags();
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
