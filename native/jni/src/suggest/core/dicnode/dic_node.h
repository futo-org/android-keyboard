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

#ifndef LATINIME_DIC_NODE_H
#define LATINIME_DIC_NODE_H

#include "char_utils.h"
#include "defines.h"
#include "dic_node_state.h"
#include "dic_node_profiler.h"
#include "dic_node_properties.h"
#include "dic_node_release_listener.h"
#include "digraph_utils.h"

#if DEBUG_DICT
#define LOGI_SHOW_ADD_COST_PROP \
        do { char charBuf[50]; \
        INTS_TO_CHARS(getOutputWordBuf(), getDepth(), charBuf); \
        AKLOGI("%20s, \"%c\", size = %03d, total = %03d, index(0) = %02d, dist = %.4f, %s,,", \
                __FUNCTION__, getNodeCodePoint(), inputSize, getTotalInputIndex(), \
                getInputIndex(0), getNormalizedCompoundDistance(), charBuf); } while (0)
#define DUMP_WORD_AND_SCORE(header) \
        do { char charBuf[50]; char prevWordCharBuf[50]; \
        INTS_TO_CHARS(getOutputWordBuf(), getDepth(), charBuf); \
        INTS_TO_CHARS(mDicNodeState.mDicNodeStatePrevWord.mPrevWord, \
                mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength(), prevWordCharBuf); \
        AKLOGI("#%8s, %5f, %5f, %5f, %5f, %s, %s, %d,,", header, \
                getSpatialDistanceForScoring(), getLanguageDistanceForScoring(), \
                getNormalizedCompoundDistance(), getRawLength(), prevWordCharBuf, charBuf, \
                getInputIndex(0)); \
        } while (0)
#else
#define LOGI_SHOW_ADD_COST_PROP
#define DUMP_WORD_AND_SCORE(header)
#endif

namespace latinime {

// This struct is purely a bucket to return values. No instances of this struct should be kept.
struct DicNode_InputStateG {
    bool mNeedsToUpdateInputStateG;
    int mPointerId;
    int16_t mInputIndex;
    int mPrevCodePoint;
    float mTerminalDiffCost;
    float mRawLength;
    DoubleLetterLevel mDoubleLetterLevel;
};

class DicNode {
    // Caveat: We define Weighting as a friend class of DicNode to let Weighting change
    // the distance of DicNode.
    // Caution!!! In general, we avoid using the "friend" access modifier.
    // This is an exception to explicitly hide DicNode::addCost() from all classes but Weighting.
    friend class Weighting;

 public:
#if DEBUG_DICT
    DicNodeProfiler mProfiler;
#endif
    //////////////////
    // Memory utils //
    //////////////////
    AK_FORCE_INLINE static void managedDelete(DicNode *node) {
        node->remove();
    }
    // end
    /////////////////

    AK_FORCE_INLINE DicNode()
            :
#if DEBUG_DICT
              mProfiler(),
#endif
              mDicNodeProperties(), mDicNodeState(), mIsCachedForNextSuggestion(false),
              mIsUsed(false), mReleaseListener(0) {}

    DicNode(const DicNode &dicNode);
    DicNode &operator=(const DicNode &dicNode);
    virtual ~DicNode() {}

    // TODO: minimize arguments by looking binary_format
    // Init for copy
    void initByCopy(const DicNode *dicNode) {
        mIsUsed = true;
        mIsCachedForNextSuggestion = dicNode->mIsCachedForNextSuggestion;
        mDicNodeProperties.init(&dicNode->mDicNodeProperties);
        mDicNodeState.init(&dicNode->mDicNodeState);
        PROF_NODE_COPY(&dicNode->mProfiler, mProfiler);
    }

    // TODO: minimize arguments by looking binary_format
    // Init for root with prevWordNodePos which is used for bigram
    void initAsRoot(const int pos, const int childrenPos, const int childrenCount,
            const int prevWordNodePos) {
        mIsUsed = true;
        mIsCachedForNextSuggestion = false;
        mDicNodeProperties.init(
                pos, 0, childrenPos, 0, 0, 0, childrenCount, 0, 0, false, false, true, 0, 0);
        mDicNodeState.init(prevWordNodePos);
        PROF_NODE_RESET(mProfiler);
    }

    void initAsPassingChild(DicNode *parentNode) {
        mIsUsed = true;
        mIsCachedForNextSuggestion = parentNode->mIsCachedForNextSuggestion;
        const int c = parentNode->getNodeTypedCodePoint();
        mDicNodeProperties.init(&parentNode->mDicNodeProperties, c);
        mDicNodeState.init(&parentNode->mDicNodeState);
        PROF_NODE_COPY(&parentNode->mProfiler, mProfiler);
    }

    // TODO: minimize arguments by looking binary_format
    // Init for root with previous word
    void initAsRootWithPreviousWord(DicNode *dicNode, const int pos, const int childrenPos,
            const int childrenCount) {
        mIsUsed = true;
        mIsCachedForNextSuggestion = false;
        mDicNodeProperties.init(
                pos, 0, childrenPos, 0, 0, 0, childrenCount, 0, 0, false, false, true, 0, 0);
        // TODO: Move to dicNodeState?
        mDicNodeState.mDicNodeStateOutput.init(); // reset for next word
        mDicNodeState.mDicNodeStateInput.init(
                &dicNode->mDicNodeState.mDicNodeStateInput, true /* resetTerminalDiffCost */);
        mDicNodeState.mDicNodeStateScoring.init(
                &dicNode->mDicNodeState.mDicNodeStateScoring);
        mDicNodeState.mDicNodeStatePrevWord.init(
                dicNode->mDicNodeState.mDicNodeStatePrevWord.getPrevWordCount() + 1,
                dicNode->mDicNodeProperties.getProbability(),
                dicNode->mDicNodeProperties.getPos(),
                dicNode->mDicNodeState.mDicNodeStatePrevWord.mPrevWord,
                dicNode->mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength(),
                dicNode->getOutputWordBuf(),
                dicNode->mDicNodeProperties.getDepth(),
                dicNode->mDicNodeState.mDicNodeStatePrevWord.mPrevSpacePositions,
                mDicNodeState.mDicNodeStateInput.getInputIndex(0) /* lastInputIndex */);
        PROF_NODE_COPY(&dicNode->mProfiler, mProfiler);
    }

    // TODO: minimize arguments by looking binary_format
    void initAsChild(DicNode *dicNode, const int pos, const uint8_t flags, const int childrenPos,
            const int attributesPos, const int siblingPos, const int nodeCodePoint,
            const int childrenCount, const int probability, const int bigramProbability,
            const bool isTerminal, const bool hasMultipleChars, const bool hasChildren,
            const uint16_t additionalSubwordLength, const int *additionalSubword) {
        mIsUsed = true;
        uint16_t newDepth = static_cast<uint16_t>(dicNode->getDepth() + 1);
        mIsCachedForNextSuggestion = dicNode->mIsCachedForNextSuggestion;
        const uint16_t newLeavingDepth = static_cast<uint16_t>(
                dicNode->mDicNodeProperties.getLeavingDepth() + additionalSubwordLength);
        mDicNodeProperties.init(pos, flags, childrenPos, attributesPos, siblingPos, nodeCodePoint,
                childrenCount, probability, bigramProbability, isTerminal, hasMultipleChars,
                hasChildren, newDepth, newLeavingDepth);
        mDicNodeState.init(&dicNode->mDicNodeState, additionalSubwordLength, additionalSubword);
        PROF_NODE_COPY(&dicNode->mProfiler, mProfiler);
    }

    AK_FORCE_INLINE void remove() {
        mIsUsed = false;
        if (mReleaseListener) {
            mReleaseListener->onReleased(this);
        }
    }

    bool isUsed() const {
        return mIsUsed;
    }

    bool isRoot() const {
        return getDepth() == 0;
    }

    bool hasChildren() const {
        return mDicNodeProperties.hasChildren();
    }

    bool isLeavingNode() const {
        ASSERT(getDepth() <= getLeavingDepth());
        return getDepth() == getLeavingDepth();
    }

    AK_FORCE_INLINE bool isFirstLetter() const {
        return getDepth() == 1;
    }

    bool isCached() const {
        return mIsCachedForNextSuggestion;
    }

    void setCached() {
        mIsCachedForNextSuggestion = true;
    }

    // Used to expand the node in DicNodeUtils
    int getNodeTypedCodePoint() const {
        return mDicNodeState.mDicNodeStateOutput.getCodePointAt(getDepth());
    }

    bool isImpossibleBigramWord() const {
        if (mDicNodeProperties.hasBlacklistedOrNotAWordFlag()) {
            return true;
        }
        const int prevWordLen = mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength()
                - mDicNodeState.mDicNodeStatePrevWord.getPrevWordStart() - 1;
        const int currentWordLen = getDepth();
        return (prevWordLen == 1 && currentWordLen == 1);
    }

    bool isFirstCharUppercase() const {
        const int c = getOutputWordBuf()[0];
        return isAsciiUpper(c);
    }

    bool isFirstWord() const {
        return mDicNodeState.mDicNodeStatePrevWord.getPrevWordNodePos() == NOT_VALID_WORD;
    }

    bool isCompletion(const int inputSize) const {
        return mDicNodeState.mDicNodeStateInput.getInputIndex(0) >= inputSize;
    }

    bool canDoLookAheadCorrection(const int inputSize) const {
        return mDicNodeState.mDicNodeStateInput.getInputIndex(0) < inputSize - 1;
    }

    // Used to get bigram probability in DicNodeUtils
    int getPos() const {
        return mDicNodeProperties.getPos();
    }

    // Used to get bigram probability in DicNodeUtils
    int getPrevWordPos() const {
        return mDicNodeState.mDicNodeStatePrevWord.getPrevWordNodePos();
    }

    // Used in DicNodeUtils
    int getChildrenPos() const {
        return mDicNodeProperties.getChildrenPos();
    }

    // Used in DicNodeUtils
    int getChildrenCount() const {
        return mDicNodeProperties.getChildrenCount();
    }

    // Used in DicNodeUtils
    int getProbability() const {
        return mDicNodeProperties.getProbability();
    }

    AK_FORCE_INLINE bool isTerminalWordNode() const {
        const bool isTerminalNodes = mDicNodeProperties.isTerminal();
        const int currentNodeDepth = getDepth();
        const int terminalNodeDepth = mDicNodeProperties.getLeavingDepth();
        return isTerminalNodes && currentNodeDepth > 0 && currentNodeDepth == terminalNodeDepth;
    }

    bool shouldBeFilterdBySafetyNetForBigram() const {
        const uint16_t currentDepth = getDepth();
        const int prevWordLen = mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength()
                - mDicNodeState.mDicNodeStatePrevWord.getPrevWordStart() - 1;
        return !(currentDepth > 0 && (currentDepth != 1 || prevWordLen != 1));
    }

    uint16_t getLeavingDepth() const {
        return mDicNodeProperties.getLeavingDepth();
    }

    bool isTotalInputSizeExceedingLimit() const {
        const int prevWordsLen = mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength();
        const int currentWordDepth = getDepth();
        // TODO: 3 can be 2? Needs to be investigated.
        // TODO: Have a const variable for 3 (or 2)
        return prevWordsLen + currentWordDepth > MAX_WORD_LENGTH - 3;
    }

    // TODO: This may be defective. Needs to be revised.
    bool truncateNode(const DicNode *const topNode, const int inputCommitPoint) {
        const int prevWordLenOfTop = mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength();
        int newPrevWordStartIndex = inputCommitPoint;
        int charCount = 0;
        // Find new word start index
        for (int i = 0; i < prevWordLenOfTop; ++i) {
            const int c = mDicNodeState.mDicNodeStatePrevWord.getPrevWordCodePointAt(i);
            // TODO: Check other separators.
            if (c != KEYCODE_SPACE && c != KEYCODE_SINGLE_QUOTE) {
                if (charCount == inputCommitPoint) {
                    newPrevWordStartIndex = i;
                    break;
                }
                ++charCount;
            }
        }
        if (!mDicNodeState.mDicNodeStatePrevWord.startsWith(
                &topNode->mDicNodeState.mDicNodeStatePrevWord, newPrevWordStartIndex - 1)) {
            // Node mismatch.
            return false;
        }
        mDicNodeState.mDicNodeStateInput.truncate(inputCommitPoint);
        mDicNodeState.mDicNodeStatePrevWord.truncate(newPrevWordStartIndex);
        return true;
    }

    void outputResult(int *dest) const {
        const uint16_t prevWordLength = mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength();
        const uint16_t currentDepth = getDepth();
        DicNodeUtils::appendTwoWords(mDicNodeState.mDicNodeStatePrevWord.mPrevWord,
                   prevWordLength, getOutputWordBuf(), currentDepth, dest);
        DUMP_WORD_AND_SCORE("OUTPUT");
    }

    void outputSpacePositionsResult(int *spaceIndices) const {
        mDicNodeState.mDicNodeStatePrevWord.outputSpacePositions(spaceIndices);
    }

    bool hasMultipleWords() const {
        return mDicNodeState.mDicNodeStatePrevWord.getPrevWordCount() > 0;
    }

    float getProximityCorrectionCount() const {
        return static_cast<float>(mDicNodeState.mDicNodeStateScoring.getProximityCorrectionCount());
    }

    float getEditCorrectionCount() const {
        return static_cast<float>(mDicNodeState.mDicNodeStateScoring.getEditCorrectionCount());
    }

    // Used to prune nodes
    float getNormalizedCompoundDistance() const {
        return mDicNodeState.mDicNodeStateScoring.getNormalizedCompoundDistance();
    }

    // Used to prune nodes
    float getNormalizedSpatialDistance() const {
        return mDicNodeState.mDicNodeStateScoring.getSpatialDistance()
                / static_cast<float>(getInputIndex(0) + 1);
    }

    // Used to prune nodes
    float getCompoundDistance() const {
        return mDicNodeState.mDicNodeStateScoring.getCompoundDistance();
    }

    // Used to prune nodes
    float getCompoundDistance(const float languageWeight) const {
        return mDicNodeState.mDicNodeStateScoring.getCompoundDistance(languageWeight);
    }

    // Used to commit input partially
    int getPrevWordNodePos() const {
        return mDicNodeState.mDicNodeStatePrevWord.getPrevWordNodePos();
    }

    AK_FORCE_INLINE const int *getOutputWordBuf() const {
        return mDicNodeState.mDicNodeStateOutput.mWordBuf;
    }

    int getPrevCodePointG(int pointerId) const {
        return mDicNodeState.mDicNodeStateInput.getPrevCodePoint(pointerId);
    }

    // Whether the current codepoint can be an intentional omission, in which case the traversal
    // algorithm will always check for a possible omission here.
    bool canBeIntentionalOmission() const {
        return isIntentionalOmissionCodePoint(getNodeCodePoint());
    }

    // Whether the omission is so frequent that it should incur zero cost.
    bool isZeroCostOmission() const {
        // TODO: do not hardcode and read from header
        return (getNodeCodePoint() == KEYCODE_SINGLE_QUOTE);
    }

    // TODO: remove
    float getTerminalDiffCostG(int path) const {
        return mDicNodeState.mDicNodeStateInput.getTerminalDiffCost(path);
    }

    //////////////////////
    // Temporary getter //
    // TODO: Remove     //
    //////////////////////
    // TODO: Remove once touch path is merged into ProximityInfoState
    // Note: Returned codepoint may be a digraph codepoint if the node is in a composite glyph.
    int getNodeCodePoint() const {
        const int codePoint = mDicNodeProperties.getNodeCodePoint();
        const DigraphUtils::DigraphCodePointIndex digraphIndex =
                mDicNodeState.mDicNodeStateScoring.getDigraphIndex();
        if (digraphIndex == DigraphUtils::NOT_A_DIGRAPH_INDEX) {
            return codePoint;
        }
        return DigraphUtils::getDigraphCodePointForIndex(codePoint, digraphIndex);
    }

    ////////////////////////////////
    // Utils for cost calculation //
    ////////////////////////////////
    AK_FORCE_INLINE bool isSameNodeCodePoint(const DicNode *const dicNode) const {
        return mDicNodeProperties.getNodeCodePoint()
                == dicNode->mDicNodeProperties.getNodeCodePoint();
    }

    // TODO: remove
    // TODO: rename getNextInputIndex
    int16_t getInputIndex(int pointerId) const {
        return mDicNodeState.mDicNodeStateInput.getInputIndex(pointerId);
    }

    ////////////////////////////////////
    // Getter of features for scoring //
    ////////////////////////////////////
    float getSpatialDistanceForScoring() const {
        return mDicNodeState.mDicNodeStateScoring.getSpatialDistance();
    }

    float getLanguageDistanceForScoring() const {
        return mDicNodeState.mDicNodeStateScoring.getLanguageDistance();
    }

    float getLanguageDistanceRatePerWordForScoring() const {
        const float langDist = getLanguageDistanceForScoring();
        const float totalWordCount =
                static_cast<float>(mDicNodeState.mDicNodeStatePrevWord.getPrevWordCount() + 1);
        return langDist / totalWordCount;
    }

    float getRawLength() const {
        return mDicNodeState.mDicNodeStateScoring.getRawLength();
    }

    bool isLessThanOneErrorForScoring() const {
        return mDicNodeState.mDicNodeStateScoring.getEditCorrectionCount()
                + mDicNodeState.mDicNodeStateScoring.getProximityCorrectionCount() <= 1;
    }

    DoubleLetterLevel getDoubleLetterLevel() const {
        return mDicNodeState.mDicNodeStateScoring.getDoubleLetterLevel();
    }

    void setDoubleLetterLevel(DoubleLetterLevel doubleLetterLevel) {
        mDicNodeState.mDicNodeStateScoring.setDoubleLetterLevel(doubleLetterLevel);
    }

    bool isInDigraph() const {
        return mDicNodeState.mDicNodeStateScoring.getDigraphIndex()
                != DigraphUtils::NOT_A_DIGRAPH_INDEX;
    }

    void advanceDigraphIndex() {
        mDicNodeState.mDicNodeStateScoring.advanceDigraphIndex();
    }

    bool isExactMatch() const {
        return mDicNodeState.mDicNodeStateScoring.isExactMatch();
    }

    uint8_t getFlags() const {
        return mDicNodeProperties.getFlags();
    }

    int getAttributesPos() const {
        return mDicNodeProperties.getAttributesPos();
    }

    inline uint16_t getDepth() const {
        return mDicNodeProperties.getDepth();
    }

    AK_FORCE_INLINE void dump(const char *tag) const {
#if DEBUG_DICT
        DUMP_WORD_AND_SCORE(tag);
#if DEBUG_DUMP_ERROR
        mProfiler.dump();
#endif
#endif
    }

    void setReleaseListener(DicNodeReleaseListener *releaseListener) {
        mReleaseListener = releaseListener;
    }

    AK_FORCE_INLINE bool compare(const DicNode *right) {
        if (!isUsed() && !right->isUsed()) {
            // Compare pointer values here for stable comparison
            return this > right;
        }
        if (!isUsed()) {
            return true;
        }
        if (!right->isUsed()) {
            return false;
        }
        const float diff =
                right->getNormalizedCompoundDistance() - getNormalizedCompoundDistance();
        static const float MIN_DIFF = 0.000001f;
        if (diff > MIN_DIFF) {
            return true;
        } else if (diff < -MIN_DIFF) {
            return false;
        }
        const int depth = getDepth();
        const int depthDiff = right->getDepth() - depth;
        if (depthDiff != 0) {
            return depthDiff > 0;
        }
        for (int i = 0; i < depth; ++i) {
            const int codePoint = mDicNodeState.mDicNodeStateOutput.getCodePointAt(i);
            const int rightCodePoint = right->mDicNodeState.mDicNodeStateOutput.getCodePointAt(i);
            if (codePoint != rightCodePoint) {
                return rightCodePoint > codePoint;
            }
        }
        // Compare pointer values here for stable comparison
        return this > right;
    }

 private:
    DicNodeProperties mDicNodeProperties;
    DicNodeState mDicNodeState;
    // TODO: Remove
    bool mIsCachedForNextSuggestion;
    bool mIsUsed;
    DicNodeReleaseListener *mReleaseListener;

    AK_FORCE_INLINE int getTotalInputIndex() const {
        int index = 0;
        for (int i = 0; i < MAX_POINTER_COUNT_G; i++) {
            index += mDicNodeState.mDicNodeStateInput.getInputIndex(i);
        }
        return index;
    }

    // Caveat: Must not be called outside Weighting
    // This restriction is guaranteed by "friend"
    AK_FORCE_INLINE void addCost(const float spatialCost, const float languageCost,
            const bool doNormalization, const int inputSize, const ErrorType errorType) {
        if (DEBUG_GEO_FULL) {
            LOGI_SHOW_ADD_COST_PROP;
        }
        mDicNodeState.mDicNodeStateScoring.addCost(spatialCost, languageCost, doNormalization,
                inputSize, getTotalInputIndex(), errorType);
    }

    // Caveat: Must not be called outside Weighting
    // This restriction is guaranteed by "friend"
    AK_FORCE_INLINE void forwardInputIndex(const int pointerId, const int count,
            const bool overwritesPrevCodePointByNodeCodePoint) {
        if (count == 0) {
            return;
        }
        mDicNodeState.mDicNodeStateInput.forwardInputIndex(pointerId, count);
        if (overwritesPrevCodePointByNodeCodePoint) {
            mDicNodeState.mDicNodeStateInput.setPrevCodePoint(0, getNodeCodePoint());
        }
    }

    AK_FORCE_INLINE void updateInputIndexG(DicNode_InputStateG *inputStateG) {
        mDicNodeState.mDicNodeStateInput.updateInputIndexG(inputStateG->mPointerId,
                inputStateG->mInputIndex, inputStateG->mPrevCodePoint,
                inputStateG->mTerminalDiffCost, inputStateG->mRawLength);
        mDicNodeState.mDicNodeStateScoring.addRawLength(inputStateG->mRawLength);
        mDicNodeState.mDicNodeStateScoring.setDoubleLetterLevel(inputStateG->mDoubleLetterLevel);
    }
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_H
