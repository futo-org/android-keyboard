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

#ifndef LATINIME_DIC_TRAVERSE_SESSION_H
#define LATINIME_DIC_TRAVERSE_SESSION_H

#include <stdint.h>
#include <vector>

#include "defines.h"
#include "jni.h"
#include "suggest/core/dicnode/dic_nodes_cache.h"
#include "suggest/core/dictionary/multi_bigram_map.h"
#include "suggest/core/layout/proximity_info_state.h"

namespace latinime {

class Dictionary;
class DictionaryStructureWithBufferPolicy;
class ProximityInfo;
class SuggestOptions;

class DicTraverseSession {
 public:

    // A factory method for DicTraverseSession
    static AK_FORCE_INLINE void *getSessionInstance(JNIEnv *env, jstring localeStr,
            jlong dictSize) {
        // To deal with the trade-off between accuracy and memory space, large cache is used for
        // dictionaries larger that the threshold
        return new DicTraverseSession(env, localeStr,
                dictSize >= DICTIONARY_SIZE_THRESHOLD_TO_USE_LARGE_CACHE_FOR_SUGGESTION);
    }

    static AK_FORCE_INLINE void initSessionInstance(DicTraverseSession *traverseSession,
            const Dictionary *const dictionary, const int *prevWord, const int prevWordLength,
            const SuggestOptions *const suggestOptions) {
        if (traverseSession) {
            DicTraverseSession *tSession = static_cast<DicTraverseSession *>(traverseSession);
            tSession->init(dictionary, prevWord, prevWordLength, suggestOptions);
        }
    }

    static AK_FORCE_INLINE void releaseSessionInstance(DicTraverseSession *traverseSession) {
        delete traverseSession;
    }

    AK_FORCE_INLINE DicTraverseSession(JNIEnv *env, jstring localeStr, bool usesLargeCache)
            : mPrevWordPos(NOT_A_DICT_POS), mProximityInfo(0),
              mDictionary(0), mSuggestOptions(0), mDicNodesCache(usesLargeCache),
              mMultiBigramMap(), mInputSize(0), mPartiallyCommited(false), mMaxPointerCount(1),
              mMultiWordCostMultiplier(1.0f) {
        // NOTE: mProximityInfoStates is an array of instances.
        // No need to initialize it explicitly here.
    }

    // Non virtual inline destructor -- never inherit this class
    AK_FORCE_INLINE ~DicTraverseSession() {}

    void init(const Dictionary *dictionary, const int *prevWord, int prevWordLength,
            const SuggestOptions *const suggestOptions);
    // TODO: Remove and merge into init
    void setupForGetSuggestions(const ProximityInfo *pInfo, const int *inputCodePoints,
            const int inputSize, const int *const inputXs, const int *const inputYs,
            const int *const times, const int *const pointerIds, const float maxSpatialDistance,
            const int maxPointerCount);
    void resetCache(const int thresholdForNextActiveDicNodes, const int maxWords);

    const DictionaryStructureWithBufferPolicy *getDictionaryStructurePolicy() const;

    //--------------------
    // getters and setters
    //--------------------
    const ProximityInfo *getProximityInfo() const { return mProximityInfo; }
    const SuggestOptions *getSuggestOptions() const { return mSuggestOptions; }
    int getPrevWordPos() const { return mPrevWordPos; }
    // TODO: REMOVE
    void setPrevWordPos(int pos) { mPrevWordPos = pos; }
    // TODO: Use proper parameter when changed
    int getDicRootPos() const { return 0; }
    DicNodesCache *getDicTraverseCache() { return &mDicNodesCache; }
    MultiBigramMap *getMultiBigramMap() { return &mMultiBigramMap; }
    const ProximityInfoState *getProximityInfoState(int id) const {
        return &mProximityInfoStates[id];
    }
    int getInputSize() const { return mInputSize; }
    void setPartiallyCommited() { mPartiallyCommited = true; }
    bool isPartiallyCommited() const { return mPartiallyCommited; }

    bool isOnlyOnePointerUsed(int *pointerId) const {
        // Not in the dictionary word
        int usedPointerCount = 0;
        int usedPointerId = 0;
        for (int i = 0; i < mMaxPointerCount; ++i) {
            if (mProximityInfoStates[i].isUsed()) {
                ++usedPointerCount;
                usedPointerId = i;
            }
        }
        if (usedPointerCount != 1) {
            return false;
        }
        if (pointerId) {
            *pointerId = usedPointerId;
        }
        return true;
    }

    void getSearchKeys(const DicNode *node, std::vector<int> *const outputSearchKeyVector) const {
        for (int i = 0; i < MAX_POINTER_COUNT_G; ++i) {
            if (!mProximityInfoStates[i].isUsed()) {
                continue;
            }
            const int pointerId = node->getInputIndex(i);
            const std::vector<int> *const searchKeyVector =
                    mProximityInfoStates[i].getSearchKeyVector(pointerId);
            outputSearchKeyVector->insert(outputSearchKeyVector->end(), searchKeyVector->begin(),
                    searchKeyVector->end());
        }
    }

    ProximityType getProximityTypeG(const DicNode *const node, const int childCodePoint) const {
        ProximityType proximityType = UNRELATED_CHAR;
        for (int i = 0; i < MAX_POINTER_COUNT_G; ++i) {
            if (!mProximityInfoStates[i].isUsed()) {
                continue;
            }
            const int pointerId = node->getInputIndex(i);
            proximityType = mProximityInfoStates[i].getProximityTypeG(pointerId, childCodePoint);
            ASSERT(proximityType == UNRELATED_CHAR || proximityType == MATCH_CHAR);
            // TODO: Make this more generic
            // Currently we assume there are only two types here -- UNRELATED_CHAR
            // and MATCH_CHAR
            if (proximityType != UNRELATED_CHAR) {
                return proximityType;
            }
        }
        return proximityType;
    }

    AK_FORCE_INLINE bool isCacheBorderForTyping(const int inputSize) const {
        return mDicNodesCache.isCacheBorderForTyping(inputSize);
    }

    /**
     * Returns whether or not it is possible to continue suggestion from the previous search.
     */
    // TODO: Remove. No need to check once the session is fully implemented.
    bool isContinuousSuggestionPossible() const {
        if (!mDicNodesCache.hasCachedDicNodesForContinuousSuggestion()) {
            return false;
        }
        ASSERT(mMaxPointerCount <= MAX_POINTER_COUNT_G);
        for (int i = 0; i < mMaxPointerCount; ++i) {
            const ProximityInfoState *const pInfoState = getProximityInfoState(i);
            // If a proximity info state is not continuous suggestion possible,
            // do not continue searching.
            if (pInfoState->isUsed() && !pInfoState->isContinuousSuggestionPossible()) {
                return false;
            }
        }
        return true;
    }

    bool isTouchPositionCorrectionEnabled() const {
        return mProximityInfoStates[0].touchPositionCorrectionEnabled();
    }

    float getMultiWordCostMultiplier() const {
        return mMultiWordCostMultiplier;
    }

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(DicTraverseSession);
    // threshold to start caching
    static const int CACHE_START_INPUT_LENGTH_THRESHOLD;
    static const int DICTIONARY_SIZE_THRESHOLD_TO_USE_LARGE_CACHE_FOR_SUGGESTION;
    void initializeProximityInfoStates(const int *const inputCodePoints, const int *const inputXs,
            const int *const inputYs, const int *const times, const int *const pointerIds,
            const int inputSize, const float maxSpatialDistance, const int maxPointerCount);

    int mPrevWordPos;
    const ProximityInfo *mProximityInfo;
    const Dictionary *mDictionary;
    const SuggestOptions *mSuggestOptions;

    DicNodesCache mDicNodesCache;
    // Temporary cache for bigram frequencies
    MultiBigramMap mMultiBigramMap;
    ProximityInfoState mProximityInfoStates[MAX_POINTER_COUNT_G];

    int mInputSize;
    bool mPartiallyCommited;
    int mMaxPointerCount;

    /////////////////////////////////
    // Configuration per dictionary
    float mMultiWordCostMultiplier;

};
} // namespace latinime
#endif // LATINIME_DIC_TRAVERSE_SESSION_H
