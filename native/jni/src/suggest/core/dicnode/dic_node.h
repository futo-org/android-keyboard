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

#include "defines.h"
#include "suggest/core/dicnode/dic_node_profiler.h"
#include "suggest/core/dicnode/dic_node_release_listener.h"
#include "suggest/core/dicnode/internal/dic_node_state.h"
#include "suggest/core/dicnode/internal/dic_node_properties.h"
#include "suggest/core/dictionary/digraph_utils.h"
#include "utils/char_utils.h"

#if DEBUG_DICT
#define LOGI_SHOW_ADD_COST_PROP \
        do { char charBuf[50]; \
        INTS_TO_CHARS(getOutputWordBuf(), getNodeCodePointCount(), charBuf, NELEMS(charBuf)); \
        AKLOGI("%20s, \"%c\", size = %03d, total = %03d, index(0) = %02d, dist = %.4f, %s,,", \
                __FUNCTION__, getNodeCodePoint(), inputSize, getTotalInputIndex(), \
                getInputIndex(0), getNormalizedCompoundDistance(), charBuf); } while (0)
#define DUMP_WORD_AND_SCORE(header) \
        do { char charBuf[50]; char prevWordCharBuf[50]; \
        INTS_TO_CHARS(getOutputWordBuf(), getNodeCodePointCount(), charBuf, NELEMS(charBuf)); \
        INTS_TO_CHARS(mDicNodeState.mDicNodeStatePrevWord.mPrevWord, \
                mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength(), prevWordCharBuf, \
                NELEMS(prevWordCharBuf)); \
        AKLOGI("#%8s, %5f, %5f, %5f, %5f, %s, %s, %d, %5f,", header, \
                getSpatialDistanceForScoring(), getLanguageDistanceForScoring(), \
                getNormalizedCompoundDistance(), getRawLength(), prevWordCharBuf, charBuf, \
                getInputIndex(0), getNormalizedCompoundDistanceAfterFirstWord()); \
        } while (0)
#else
#define LOGI_SHOW_ADD_COST_PROP
#define DUMP_WORD_AND_SCORE(header)
#endif

namespace latinime {

// This struct is purely a bucket to return values. No instances of this struct should be kept.
struct DicNode_InputStateG {
    DicNode_InputStateG()
            : mNeedsToUpdateInputStateG(false), mPointerId(0), mInputIndex(0),
              mPrevCodePoint(0), mTerminalDiffCost(0.0f), mRawLength(0.0f),
              mDoubleLetterLevel(NOT_A_DOUBLE_LETTER) {}

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

    // Init for copy
    void initByCopy(const DicNode *dicNode) {
        mIsUsed = true;
        mIsCachedForNextSuggestion = dicNode->mIsCachedForNextSuggestion;
        mDicNodeProperties.init(&dicNode->mDicNodeProperties);
        mDicNodeState.init(&dicNode->mDicNodeState);
        PROF_NODE_COPY(&dicNode->mProfiler, mProfiler);
    }

    // Init for root with prevWordNodePos which is used for bigram
    void initAsRoot(const int rootGroupPos, const int prevWordNodePos) {
        mIsUsed = true;
        mIsCachedForNextSuggestion = false;
        mDicNodeProperties.init(
                NOT_A_DICT_POS /* pos */, rootGroupPos, NOT_A_CODE_POINT /* nodeCodePoint */,
                NOT_A_PROBABILITY /* probability */, false /* isTerminal */,
                true /* hasChildren */, false /* isBlacklistedOrNotAWord */, 0 /* depth */,
                0 /* terminalDepth */);
        mDicNodeState.init(prevWordNodePos);
        PROF_NODE_RESET(mProfiler);
    }

    // Init for root with previous word
    void initAsRootWithPreviousWord(DicNode *dicNode, const int rootGroupPos) {
        mIsUsed = true;
        mIsCachedForNextSuggestion = dicNode->mIsCachedForNextSuggestion;
        mDicNodeProperties.init(
                NOT_A_DICT_POS /* pos */, rootGroupPos, NOT_A_CODE_POINT /* nodeCodePoint */,
                NOT_A_PROBABILITY /* probability */, false /* isTerminal */,
                true /* hasChildren */, false /* isBlacklistedOrNotAWord */,  0 /* depth */,
                0 /* terminalDepth */);
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
                dicNode->mDicNodeState.mDicNodeStatePrevWord.getSecondWordFirstInputIndex(),
                mDicNodeState.mDicNodeStateInput.getInputIndex(0) /* lastInputIndex */);
        PROF_NODE_COPY(&dicNode->mProfiler, mProfiler);
    }

    void initAsPassingChild(DicNode *parentNode) {
        mIsUsed = true;
        mIsCachedForNextSuggestion = parentNode->mIsCachedForNextSuggestion;
        const int c = parentNode->getNodeTypedCodePoint();
        mDicNodeProperties.init(&parentNode->mDicNodeProperties, c);
        mDicNodeState.init(&parentNode->mDicNodeState);
        PROF_NODE_COPY(&parentNode->mProfiler, mProfiler);
    }

    void initAsChild(const DicNode *const dicNode, const int pos, const int childrenPos,
            const int probability, const bool isTerminal, const bool hasChildren,
            const bool isBlacklistedOrNotAWord, const uint16_t mergedNodeCodePointCount,
            const int *const mergedNodeCodePoints) {
        mIsUsed = true;
        uint16_t newDepth = static_cast<uint16_t>(dicNode->getNodeCodePointCount() + 1);
        mIsCachedForNextSuggestion = dicNode->mIsCachedForNextSuggestion;
        const uint16_t newLeavingDepth = static_cast<uint16_t>(
                dicNode->mDicNodeProperties.getLeavingDepth() + mergedNodeCodePointCount);
        mDicNodeProperties.init(pos, childrenPos, mergedNodeCodePoints[0], probability,
                isTerminal, hasChildren, isBlacklistedOrNotAWord, newDepth, newLeavingDepth);
        mDicNodeState.init(&dicNode->mDicNodeState, mergedNodeCodePointCount,
                mergedNodeCodePoints);
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
        return getNodeCodePointCount() == 0;
    }

    bool hasChildren() const {
        return mDicNodeProperties.hasChildren();
    }

    bool isLeavingNode() const {
        ASSERT(getNodeCodePointCount() <= mDicNodeProperties.getLeavingDepth());
        return getNodeCodePointCount() == mDicNodeProperties.getLeavingDepth();
    }

    AK_FORCE_INLINE bool isFirstLetter() const {
        return getNodeCodePointCount() == 1;
    }

    bool isCached() const {
        return mIsCachedForNextSuggestion;
    }

    void setCached() {
        mIsCachedForNextSuggestion = true;
    }

    // Used to expand the node in DicNodeUtils
    int getNodeTypedCodePoint() const {
        return mDicNodeState.mDicNodeStateOutput.getCodePointAt(getNodeCodePointCount());
    }

    // Check if the current word and the previous word can be considered as a valid multiple word
    // suggestion.
    bool isValidMultipleWordSuggestion() const {
        if (isBlacklistedOrNotAWord()) {
            return false;
        }
        // Treat suggestion as invalid if the current and the previous word are single character
        // words.
        const int prevWordLen = mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength()
                - mDicNodeState.mDicNodeStatePrevWord.getPrevWordStart() - 1;
        const int currentWordLen = getNodeCodePointCount();
        return (prevWordLen != 1 || currentWordLen != 1);
    }

    bool isFirstCharUppercase() const {
        const int c = getOutputWordBuf()[0];
        return CharUtils::isAsciiUpper(c);
    }

    bool isFirstWord() const {
        return mDicNodeState.mDicNodeStatePrevWord.getPrevWordNodePos() == NOT_A_DICT_POS;
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

    int getProbability() const {
        return mDicNodeProperties.getProbability();
    }

    AK_FORCE_INLINE bool isTerminalWordNode() const {
        const bool isTerminalNodes = mDicNodeProperties.isTerminal();
        const int currentNodeDepth = getNodeCodePointCount();
        const int terminalNodeDepth = mDicNodeProperties.getLeavingDepth();
        return isTerminalNodes && currentNodeDepth > 0 && currentNodeDepth == terminalNodeDepth;
    }

    bool shouldBeFilteredBySafetyNetForBigram() const {
        const uint16_t currentDepth = getNodeCodePointCount();
        const int prevWordLen = mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength()
                - mDicNodeState.mDicNodeStatePrevWord.getPrevWordStart() - 1;
        return !(currentDepth > 0 && (currentDepth != 1 || prevWordLen != 1));
    }

    bool isTotalInputSizeExceedingLimit() const {
        const int prevWordsLen = mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength();
        const int currentWordDepth = getNodeCodePointCount();
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
        const uint16_t currentDepth = getNodeCodePointCount();
        DicNodeUtils::appendTwoWords(mDicNodeState.mDicNodeStatePrevWord.mPrevWord,
                   prevWordLength, getOutputWordBuf(), currentDepth, dest);
        DUMP_WORD_AND_SCORE("OUTPUT");
    }

    // "Total" in this context (and other methods in this class) means the whole suggestion. When
    // this represents a multi-word suggestion, the referenced PtNode (in mDicNodeState) is only
    // the one that corresponds to the last word of the suggestion, and all the previous words
    // are concatenated together in mPrevWord - which contains a space at the end.
    int getTotalNodeSpaceCount() const {
        if (isFirstWord()) return 0;
        return CharUtils::getSpaceCount(mDicNodeState.mDicNodeStatePrevWord.mPrevWord,
                mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength());
    }

    int getSecondWordFirstInputIndex(const ProximityInfoState *const pInfoState) const {
        const int inputIndex = mDicNodeState.mDicNodeStatePrevWord.getSecondWordFirstInputIndex();
        if (inputIndex == NOT_AN_INDEX) {
            return NOT_AN_INDEX;
        } else {
            return pInfoState->getInputIndexOfSampledPoint(inputIndex);
        }
    }

    bool hasMultipleWords() const {
        return mDicNodeState.mDicNodeStatePrevWord.getPrevWordCount() > 0;
    }

    int getProximityCorrectionCount() const {
        return mDicNodeState.mDicNodeStateScoring.getProximityCorrectionCount();
    }

    int getEditCorrectionCount() const {
        return mDicNodeState.mDicNodeStateScoring.getEditCorrectionCount();
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
        return mDicNodeState.mDicNodeStateOutput.mCodePointsBuf;
    }

    int getPrevCodePointG(int pointerId) const {
        return mDicNodeState.mDicNodeStateInput.getPrevCodePoint(pointerId);
    }

    // Whether the current codepoint can be an intentional omission, in which case the traversal
    // algorithm will always check for a possible omission here.
    bool canBeIntentionalOmission() const {
        return CharUtils::isIntentionalOmissionCodePoint(getNodeCodePoint());
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

    // For space-aware gestures, we store the normalized distance at the char index
    // that ends the first word of the suggestion. We call this the distance after
    // first word.
    float getNormalizedCompoundDistanceAfterFirstWord() const {
        return mDicNodeState.mDicNodeStateScoring.getNormalizedCompoundDistanceAfterFirstWord();
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

    bool isBlacklistedOrNotAWord() const {
        return mDicNodeProperties.isBlacklistedOrNotAWord();
    }

    inline uint16_t getNodeCodePointCount() const {
        return mDicNodeProperties.getDepth();
    }

    // Returns code point count including spaces
    inline uint16_t getTotalNodeCodePointCount() const {
        return getNodeCodePointCount() + mDicNodeState.mDicNodeStatePrevWord.getPrevWordLength();
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
        // Promote exact matches to prevent them from being pruned.
        const bool leftExactMatch = isExactMatch();
        const bool rightExactMatch = right->isExactMatch();
        if (leftExactMatch != rightExactMatch) {
            return leftExactMatch;
        }
        const float diff =
                right->getNormalizedCompoundDistance() - getNormalizedCompoundDistance();
        static const float MIN_DIFF = 0.000001f;
        if (diff > MIN_DIFF) {
            return true;
        } else if (diff < -MIN_DIFF) {
            return false;
        }
        const int depth = getNodeCodePointCount();
        const int depthDiff = right->getNodeCodePointCount() - depth;
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

    // Saves the current normalized compound distance for space-aware gestures.
    // See getNormalizedCompoundDistanceAfterFirstWord for details.
    AK_FORCE_INLINE void saveNormalizedCompoundDistanceAfterFirstWordIfNoneYet() {
        mDicNodeState.mDicNodeStateScoring.saveNormalizedCompoundDistanceAfterFirstWordIfNoneYet();
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

    AK_FORCE_INLINE void updateInputIndexG(const DicNode_InputStateG *const inputStateG) {
        if (mDicNodeState.mDicNodeStatePrevWord.getPrevWordCount() == 1 && isFirstLetter()) {
            mDicNodeState.mDicNodeStatePrevWord.setSecondWordFirstInputIndex(
                    inputStateG->mInputIndex);
        }
        mDicNodeState.mDicNodeStateInput.updateInputIndexG(inputStateG->mPointerId,
                inputStateG->mInputIndex, inputStateG->mPrevCodePoint,
                inputStateG->mTerminalDiffCost, inputStateG->mRawLength);
        mDicNodeState.mDicNodeStateScoring.addRawLength(inputStateG->mRawLength);
        mDicNodeState.mDicNodeStateScoring.setDoubleLetterLevel(inputStateG->mDoubleLetterLevel);
    }
};
} // namespace latinime
#endif // LATINIME_DIC_NODE_H
