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

#include "suggest/core/suggest.h"

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_priority_queue.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/dictionary/binary_dictionary_shortcut_iterator.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/dictionary/digraph_utils.h"
#include "suggest/core/dictionary/shortcut_utils.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/policy/dictionary_structure_with_buffer_policy.h"
#include "suggest/core/policy/scoring.h"
#include "suggest/core/policy/traversal.h"
#include "suggest/core/policy/weighting.h"
#include "suggest/core/session/dic_traverse_session.h"

namespace latinime {

// Initialization of class constants.
const int Suggest::MIN_LEN_FOR_MULTI_WORD_AUTOCORRECT = 16;
const int Suggest::MIN_CONTINUOUS_SUGGESTION_INPUT_SIZE = 2;
const float Suggest::AUTOCORRECT_CLASSIFICATION_THRESHOLD = 0.33f;

/**
 * Returns a set of suggestions for the given input touch points. The commitPoint argument indicates
 * whether to prematurely commit the suggested words up to the given point for sentence-level
 * suggestion.
 *
 * Note: Currently does not support concurrent calls across threads. Continuous suggestion is
 * automatically activated for sequential calls that share the same starting input.
 * TODO: Stop detecting continuous suggestion. Start using traverseSession instead.
 */
int Suggest::getSuggestions(ProximityInfo *pInfo, void *traverseSession,
        int *inputXs, int *inputYs, int *times, int *pointerIds, int *inputCodePoints,
        int inputSize, int commitPoint, int *outWords, int *frequencies, int *outputIndices,
        int *outputTypes, int *outputAutoCommitFirstWordConfidence) const {
    PROF_OPEN;
    PROF_START(0);
    const float maxSpatialDistance = TRAVERSAL->getMaxSpatialDistance();
    DicTraverseSession *tSession = static_cast<DicTraverseSession *>(traverseSession);
    tSession->setupForGetSuggestions(pInfo, inputCodePoints, inputSize, inputXs, inputYs, times,
            pointerIds, maxSpatialDistance, TRAVERSAL->getMaxPointerCount());
    // TODO: Add the way to evaluate cache

    initializeSearch(tSession, commitPoint);
    PROF_END(0);
    PROF_START(1);

    // keep expanding search dicNodes until all have terminated.
    while (tSession->getDicTraverseCache()->activeSize() > 0) {
        expandCurrentDicNodes(tSession);
        tSession->getDicTraverseCache()->advanceActiveDicNodes();
        tSession->getDicTraverseCache()->advanceInputIndex(inputSize);
    }
    PROF_END(1);
    PROF_START(2);
    const int size = outputSuggestions(tSession, frequencies, outWords, outputIndices, outputTypes,
            outputAutoCommitFirstWordConfidence);
    PROF_END(2);
    PROF_CLOSE;
    return size;
}

/**
 * Initializes the search at the root of the lexicon trie. Note that when possible the search will
 * continue suggestion from where it left off during the last call.
 */
void Suggest::initializeSearch(DicTraverseSession *traverseSession, int commitPoint) const {
    if (!traverseSession->getProximityInfoState(0)->isUsed()) {
        return;
    }

    // Never auto partial commit for now.
    commitPoint = 0;

    if (traverseSession->getInputSize() > MIN_CONTINUOUS_SUGGESTION_INPUT_SIZE
            && traverseSession->isContinuousSuggestionPossible()) {
        if (commitPoint == 0) {
            // Continue suggestion
            traverseSession->getDicTraverseCache()->continueSearch();
        } else {
            // Continue suggestion after partial commit.
            DicNode *topDicNode =
                    traverseSession->getDicTraverseCache()->setCommitPoint(commitPoint);
            traverseSession->setPrevWordPos(topDicNode->getPrevWordNodePos());
            traverseSession->getDicTraverseCache()->continueSearch();
            traverseSession->setPartiallyCommited();
        }
    } else {
        // Restart recognition at the root.
        traverseSession->resetCache(TRAVERSAL->getMaxCacheSize(traverseSession->getInputSize()),
                MAX_RESULTS);
        // Create a new dic node here
        DicNode rootNode;
        DicNodeUtils::initAsRoot(traverseSession->getDictionaryStructurePolicy(),
                traverseSession->getPrevWordPos(), &rootNode);
        traverseSession->getDicTraverseCache()->copyPushActive(&rootNode);
    }
}

/**
 * Outputs the final list of suggestions (i.e., terminal nodes).
 */
int Suggest::outputSuggestions(DicTraverseSession *traverseSession, int *frequencies,
        int *outputCodePoints, int *outputIndicesToPartialCommit, int *outputTypes,
        int *outputAutoCommitFirstWordConfidence) const {
#if DEBUG_EVALUATE_MOST_PROBABLE_STRING
    const int terminalSize = 0;
#else
    const int terminalSize = min(MAX_RESULTS,
            static_cast<int>(traverseSession->getDicTraverseCache()->terminalSize()));
#endif
    DicNode terminals[MAX_RESULTS]; // Avoiding non-POD variable length array

    for (int index = terminalSize - 1; index >= 0; --index) {
        traverseSession->getDicTraverseCache()->popTerminal(&terminals[index]);
    }

    const float languageWeight = SCORING->getAdjustedLanguageWeight(
            traverseSession, terminals, terminalSize);

    int outputWordIndex = 0;
    // Insert most probable word at index == 0 as long as there is one terminal at least
    const bool hasMostProbableString =
            SCORING->getMostProbableString(traverseSession, terminalSize, languageWeight,
                    &outputCodePoints[0], &outputTypes[0], &frequencies[0]);
    if (hasMostProbableString) {
        outputIndicesToPartialCommit[outputWordIndex] = NOT_AN_INDEX;
        ++outputWordIndex;
    }

    // Initial value of the loop index for terminal nodes (words)
    int doubleLetterTerminalIndex = -1;
    DoubleLetterLevel doubleLetterLevel = NOT_A_DOUBLE_LETTER;
    SCORING->searchWordWithDoubleLetter(terminals, terminalSize,
            &doubleLetterTerminalIndex, &doubleLetterLevel);

    int maxScore = S_INT_MIN;
    // Force autocorrection for obvious long multi-word suggestions when the top suggestion is
    // a long multiple words suggestion.
    // TODO: Implement a smarter auto-commit method for handling multi-word suggestions.
    // traverseSession->isPartiallyCommited() always returns false because we never auto partial
    // commit for now.
    const bool forceCommitMultiWords = (terminalSize > 0) ?
            TRAVERSAL->autoCorrectsToMultiWordSuggestionIfTop()
                    && (traverseSession->isPartiallyCommited()
                            || (traverseSession->getInputSize()
                                    >= MIN_LEN_FOR_MULTI_WORD_AUTOCORRECT
                                            && terminals[0].hasMultipleWords())) : false;
    // TODO: have partial commit work even with multiple pointers.
    const bool outputSecondWordFirstLetterInputIndex =
            traverseSession->isOnlyOnePointerUsed(0 /* pointerId */);
    if (terminalSize > 0) {
        // If we have no suggestions, don't write this
        outputAutoCommitFirstWordConfidence[0] =
                computeFirstWordConfidence(&terminals[0]);
    }

    // Output suggestion results here
    for (int terminalIndex = 0; terminalIndex < terminalSize && outputWordIndex < MAX_RESULTS;
            ++terminalIndex) {
        DicNode *terminalDicNode = &terminals[terminalIndex];
        if (DEBUG_GEO_FULL) {
            terminalDicNode->dump("OUT:");
        }
        const float doubleLetterCost = SCORING->getDoubleLetterDemotionDistanceCost(
                terminalIndex, doubleLetterTerminalIndex, doubleLetterLevel);
        const float compoundDistance = terminalDicNode->getCompoundDistance(languageWeight)
                + doubleLetterCost;
        const bool isPossiblyOffensiveWord =
                traverseSession->getDictionaryStructurePolicy()->getProbability(
                        terminalDicNode->getProbability(), NOT_A_PROBABILITY) <= 0;
        const bool isExactMatch = terminalDicNode->isExactMatch();
        const bool isFirstCharUppercase = terminalDicNode->isFirstCharUppercase();
        // Heuristic: We exclude freq=0 first-char-uppercase words from exact match.
        // (e.g. "AMD" and "and")
        const bool isSafeExactMatch = isExactMatch
                && !(isPossiblyOffensiveWord && isFirstCharUppercase);
        const int outputTypeFlags =
                (isPossiblyOffensiveWord ? Dictionary::KIND_FLAG_POSSIBLY_OFFENSIVE : 0)
                | (isSafeExactMatch ? Dictionary::KIND_FLAG_EXACT_MATCH : 0);

        // Entries that are blacklisted or do not represent a word should not be output.
        const bool isValidWord = !terminalDicNode->isBlacklistedOrNotAWord();

        // Increase output score of top typing suggestion to ensure autocorrection.
        // TODO: Better integration with java side autocorrection logic.
        const int finalScore = SCORING->calculateFinalScore(
                compoundDistance, traverseSession->getInputSize(),
                terminalDicNode->isExactMatch()
                        || (forceCommitMultiWords && terminalDicNode->hasMultipleWords())
                                || (isValidWord && SCORING->doesAutoCorrectValidWord()));
        if (maxScore < finalScore && isValidWord) {
            maxScore = finalScore;
        }

        // Don't output invalid words. However, we still need to submit their shortcuts if any.
        if (isValidWord) {
            outputTypes[outputWordIndex] = Dictionary::KIND_CORRECTION | outputTypeFlags;
            frequencies[outputWordIndex] = finalScore;
            if (outputSecondWordFirstLetterInputIndex) {
                outputIndicesToPartialCommit[outputWordIndex] =
                        terminalDicNode->getSecondWordFirstInputIndex(
                                traverseSession->getProximityInfoState(0));
            } else {
                outputIndicesToPartialCommit[outputWordIndex] = NOT_AN_INDEX;
            }
            // Populate the outputChars array with the suggested word.
            const int startIndex = outputWordIndex * MAX_WORD_LENGTH;
            terminalDicNode->outputResult(&outputCodePoints[startIndex]);
            ++outputWordIndex;
        }

        if (!terminalDicNode->hasMultipleWords()) {
            BinaryDictionaryShortcutIterator shortcutIt(
                    traverseSession->getDictionaryStructurePolicy()->getShortcutsStructurePolicy(),
                    traverseSession->getDictionaryStructurePolicy()
                            ->getShortcutPositionOfPtNode(terminalDicNode->getPos()));
            // Shortcut is not supported for multiple words suggestions.
            // TODO: Check shortcuts during traversal for multiple words suggestions.
            const bool sameAsTyped = TRAVERSAL->sameAsTyped(traverseSession, terminalDicNode);
            const int updatedOutputWordIndex = ShortcutUtils::outputShortcuts(&shortcutIt,
                    outputWordIndex,  finalScore, outputCodePoints, frequencies, outputTypes,
                    sameAsTyped);
            const int secondWordFirstInputIndex = terminalDicNode->getSecondWordFirstInputIndex(
                    traverseSession->getProximityInfoState(0));
            for (int i = outputWordIndex; i < updatedOutputWordIndex; ++i) {
                if (outputSecondWordFirstLetterInputIndex) {
                    outputIndicesToPartialCommit[i] = secondWordFirstInputIndex;
                } else {
                    outputIndicesToPartialCommit[i] = NOT_AN_INDEX;
                }
            }
            outputWordIndex = updatedOutputWordIndex;
        }
        DicNode::managedDelete(terminalDicNode);
    }

    if (hasMostProbableString) {
        SCORING->safetyNetForMostProbableString(terminalSize, maxScore,
                &outputCodePoints[0], &frequencies[0]);
    }
    return outputWordIndex;
}

int Suggest::computeFirstWordConfidence(const DicNode *const terminalDicNode) const {
    // Get the number of spaces in the first suggestion
    const int spaceCount = terminalDicNode->getTotalNodeSpaceCount();
    // Get the number of characters in the first suggestion
    const int length = terminalDicNode->getTotalNodeCodePointCount();
    // Get the distance for the first word of the suggestion
    const float distance = terminalDicNode->getNormalizedCompoundDistanceAfterFirstWord();

    // Arbitrarily, we give a score whose useful values range from 0 to 1,000,000.
    // 1,000,000 will be the cutoff to auto-commit. It's fine if the number is under 0 or
    // above 1,000,000 : under 0 just means it's very bad to commit, and above 1,000,000 means
    // we are very confident.
    // Expected space count is 1 ~ 5
    static const int MIN_EXPECTED_SPACE_COUNT = 1;
    static const int MAX_EXPECTED_SPACE_COUNT = 5;
    // Expected length is about 4 ~ 30
    static const int MIN_EXPECTED_LENGTH = 4;
    static const int MAX_EXPECTED_LENGTH = 30;
    // Expected distance is about 0.2 ~ 2.0, but consider 0.0 ~ 2.0
    static const float MIN_EXPECTED_DISTANCE = 0.0;
    static const float MAX_EXPECTED_DISTANCE = 2.0;
    // This is not strict: it's where most stuff will be falling, but it's still fine if it's
    // outside these values. We want to output a value that reflects all of these. Each factor
    // contributes a bit.

    // We need at least a space.
    if (spaceCount < 1) return NOT_A_FIRST_WORD_CONFIDENCE;

    // The smaller the edit distance, the higher the contribution. MIN_EXPECTED_DISTANCE means 0
    // contribution, while MAX_EXPECTED_DISTANCE means full contribution according to the
    // weight of the distance. Clamp to avoid overflows.
    const float clampedDistance = distance < MIN_EXPECTED_DISTANCE ? MIN_EXPECTED_DISTANCE
            : distance > MAX_EXPECTED_DISTANCE ? MAX_EXPECTED_DISTANCE : distance;
    const int distanceContribution = DISTANCE_WEIGHT_FOR_AUTO_COMMIT
            * (MAX_EXPECTED_DISTANCE - clampedDistance)
            / (MAX_EXPECTED_DISTANCE - MIN_EXPECTED_DISTANCE);
    // The larger the suggestion length, the larger the contribution. MIN_EXPECTED_LENGTH is no
    // contribution, MAX_EXPECTED_LENGTH is full contribution according to the weight of the
    // length. Length is guaranteed to be between 1 and 48, so we don't need to clamp.
    const int lengthContribution = LENGTH_WEIGHT_FOR_AUTO_COMMIT
            * (length - MIN_EXPECTED_LENGTH) / (MAX_EXPECTED_LENGTH - MIN_EXPECTED_LENGTH);
    // The more spaces, the larger the contribution. MIN_EXPECTED_SPACE_COUNT space is no
    // contribution, MAX_EXPECTED_SPACE_COUNT spaces is full contribution according to the
    // weight of the space count.
    const int spaceContribution = SPACE_COUNT_WEIGHT_FOR_AUTO_COMMIT
            * (spaceCount - MIN_EXPECTED_SPACE_COUNT)
            / (MAX_EXPECTED_SPACE_COUNT - MIN_EXPECTED_SPACE_COUNT);

    return distanceContribution + lengthContribution + spaceContribution;
}

/**
 * Expands the dicNodes in the current search priority queue by advancing to the possible child
 * nodes based on the next touch point(s) (or no touch points for lookahead)
 */
void Suggest::expandCurrentDicNodes(DicTraverseSession *traverseSession) const {
    const int inputSize = traverseSession->getInputSize();
    DicNodeVector childDicNodes(TRAVERSAL->getDefaultExpandDicNodeSize());
    DicNode correctionDicNode;

    // TODO: Find more efficient caching
    const bool shouldDepthLevelCache = TRAVERSAL->shouldDepthLevelCache(traverseSession);
    if (shouldDepthLevelCache) {
        traverseSession->getDicTraverseCache()->updateLastCachedInputIndex();
    }
    if (DEBUG_CACHE) {
        AKLOGI("expandCurrentDicNodes depth level cache = %d, inputSize = %d",
                shouldDepthLevelCache, inputSize);
    }
    while (traverseSession->getDicTraverseCache()->activeSize() > 0) {
        DicNode dicNode;
        traverseSession->getDicTraverseCache()->popActive(&dicNode);
        if (dicNode.isTotalInputSizeExceedingLimit()) {
            return;
        }
        childDicNodes.clear();
        const int point0Index = dicNode.getInputIndex(0);
        const bool canDoLookAheadCorrection =
                TRAVERSAL->canDoLookAheadCorrection(traverseSession, &dicNode);
        const bool isLookAheadCorrection = canDoLookAheadCorrection
                && traverseSession->getDicTraverseCache()->
                        isLookAheadCorrectionInputIndex(static_cast<int>(point0Index));
        const bool isCompletion = dicNode.isCompletion(inputSize);

        const bool shouldNodeLevelCache =
                TRAVERSAL->shouldNodeLevelCache(traverseSession, &dicNode);
        if (shouldDepthLevelCache || shouldNodeLevelCache) {
            if (DEBUG_CACHE) {
                dicNode.dump("PUSH_CACHE");
            }
            traverseSession->getDicTraverseCache()->copyPushContinue(&dicNode);
            dicNode.setCached();
        }

        if (dicNode.isInDigraph()) {
            // Finish digraph handling if the node is in the middle of a digraph expansion.
            processDicNodeAsDigraph(traverseSession, &dicNode);
        } else if (isLookAheadCorrection) {
            // The algorithm maintains a small set of "deferred" nodes that have not consumed the
            // latest touch point yet. These are needed to apply look-ahead correction operations
            // that require special handling of the latest touch point. For example, with insertions
            // (e.g., "thiis" -> "this") the latest touch point should not be consumed at all.
            processDicNodeAsTransposition(traverseSession, &dicNode);
            processDicNodeAsInsertion(traverseSession, &dicNode);
        } else { // !isLookAheadCorrection
            // Only consider typing error corrections if the normalized compound distance is
            // below a spatial distance threshold.
            // NOTE: the threshold may need to be updated if scoring model changes.
            // TODO: Remove. Do not prune node here.
            const bool allowsErrorCorrections = TRAVERSAL->allowsErrorCorrections(&dicNode);
            // Process for handling space substitution (e.g., hevis => he is)
            if (allowsErrorCorrections
                    && TRAVERSAL->isSpaceSubstitutionTerminal(traverseSession, &dicNode)) {
                createNextWordDicNode(traverseSession, &dicNode, true /* spaceSubstitution */);
            }

            DicNodeUtils::getAllChildDicNodes(
                    &dicNode, traverseSession->getDictionaryStructurePolicy(), &childDicNodes);

            const int childDicNodesSize = childDicNodes.getSizeAndLock();
            for (int i = 0; i < childDicNodesSize; ++i) {
                DicNode *const childDicNode = childDicNodes[i];
                if (isCompletion) {
                    // Handle forward lookahead when the lexicon letter exceeds the input size.
                    processDicNodeAsMatch(traverseSession, childDicNode);
                    continue;
                }
                if (DigraphUtils::hasDigraphForCodePoint(
                        traverseSession->getDictionaryStructurePolicy()
                                ->getHeaderStructurePolicy(),
                        childDicNode->getNodeCodePoint())) {
                    correctionDicNode.initByCopy(childDicNode);
                    correctionDicNode.advanceDigraphIndex();
                    processDicNodeAsDigraph(traverseSession, &correctionDicNode);
                }
                if (TRAVERSAL->isOmission(traverseSession, &dicNode, childDicNode,
                        allowsErrorCorrections)) {
                    // TODO: (Gesture) Change weight between omission and substitution errors
                    // TODO: (Gesture) Terminal node should not be handled as omission
                    correctionDicNode.initByCopy(childDicNode);
                    processDicNodeAsOmission(traverseSession, &correctionDicNode);
                }
                const ProximityType proximityType = TRAVERSAL->getProximityType(
                        traverseSession, &dicNode, childDicNode);
                switch (proximityType) {
                    // TODO: Consider the difference of proximityType here
                    case MATCH_CHAR:
                    case PROXIMITY_CHAR:
                        processDicNodeAsMatch(traverseSession, childDicNode);
                        break;
                    case ADDITIONAL_PROXIMITY_CHAR:
                        if (allowsErrorCorrections) {
                            processDicNodeAsAdditionalProximityChar(traverseSession, &dicNode,
                                    childDicNode);
                        }
                        break;
                    case SUBSTITUTION_CHAR:
                        if (allowsErrorCorrections) {
                            processDicNodeAsSubstitution(traverseSession, &dicNode, childDicNode);
                        }
                        break;
                    case UNRELATED_CHAR:
                        // Just drop this node and do nothing.
                        break;
                    default:
                        // Just drop this node and do nothing.
                        break;
                }
            }

            // Push the node for look-ahead correction
            if (allowsErrorCorrections && canDoLookAheadCorrection) {
                traverseSession->getDicTraverseCache()->copyPushNextActive(&dicNode);
            }
        }
    }
}

void Suggest::processTerminalDicNode(
        DicTraverseSession *traverseSession, DicNode *dicNode) const {
    if (dicNode->getCompoundDistance() >= static_cast<float>(MAX_VALUE_FOR_WEIGHTING)) {
        return;
    }
    if (!dicNode->isTerminalWordNode()) {
        return;
    }
    if (dicNode->shouldBeFilteredBySafetyNetForBigram()) {
        return;
    }
    // Create a non-cached node here.
    DicNode terminalDicNode;
    DicNodeUtils::initByCopy(dicNode, &terminalDicNode);
    if (TRAVERSAL->needsToTraverseAllUserInput()
            && dicNode->getInputIndex(0) < traverseSession->getInputSize()) {
        Weighting::addCostAndForwardInputIndex(WEIGHTING, CT_TERMINAL_INSERTION, traverseSession, 0,
                &terminalDicNode, traverseSession->getMultiBigramMap());
    }
    Weighting::addCostAndForwardInputIndex(WEIGHTING, CT_TERMINAL, traverseSession, 0,
            &terminalDicNode, traverseSession->getMultiBigramMap());
    traverseSession->getDicTraverseCache()->copyPushTerminal(&terminalDicNode);
}

/**
 * Adds the expanded dicNode to the next search priority queue. Also creates an additional next word
 * (by the space omission error correction) search path if input dicNode is on a terminal node.
 */
void Suggest::processExpandedDicNode(
        DicTraverseSession *traverseSession, DicNode *dicNode) const {
    processTerminalDicNode(traverseSession, dicNode);
    if (dicNode->getCompoundDistance() < static_cast<float>(MAX_VALUE_FOR_WEIGHTING)) {
        if (TRAVERSAL->isSpaceOmissionTerminal(traverseSession, dicNode)) {
            createNextWordDicNode(traverseSession, dicNode, false /* spaceSubstitution */);
        }
        const int allowsLookAhead = !(dicNode->hasMultipleWords()
                && dicNode->isCompletion(traverseSession->getInputSize()));
        if (dicNode->hasChildren() && allowsLookAhead) {
            traverseSession->getDicTraverseCache()->copyPushNextActive(dicNode);
        }
    }
    DicNode::managedDelete(dicNode);
}

void Suggest::processDicNodeAsMatch(DicTraverseSession *traverseSession,
        DicNode *childDicNode) const {
    weightChildNode(traverseSession, childDicNode);
    processExpandedDicNode(traverseSession, childDicNode);
}

void Suggest::processDicNodeAsAdditionalProximityChar(DicTraverseSession *traverseSession,
        DicNode *dicNode, DicNode *childDicNode) const {
    // Note: Most types of corrections don't need to look up the bigram information since they do
    // not treat the node as a terminal. There is no need to pass the bigram map in these cases.
    Weighting::addCostAndForwardInputIndex(WEIGHTING, CT_ADDITIONAL_PROXIMITY,
            traverseSession, dicNode, childDicNode, 0 /* multiBigramMap */);
    weightChildNode(traverseSession, childDicNode);
    processExpandedDicNode(traverseSession, childDicNode);
}

void Suggest::processDicNodeAsSubstitution(DicTraverseSession *traverseSession,
        DicNode *dicNode, DicNode *childDicNode) const {
    Weighting::addCostAndForwardInputIndex(WEIGHTING, CT_SUBSTITUTION, traverseSession,
            dicNode, childDicNode, 0 /* multiBigramMap */);
    weightChildNode(traverseSession, childDicNode);
    processExpandedDicNode(traverseSession, childDicNode);
}

// Process the node codepoint as a digraph. This means that composite glyphs like the German
// u-umlaut is expanded to the transliteration "ue". Note that this happens in parallel with
// the normal non-digraph traversal, so both "uber" and "ueber" can be corrected to "[u-umlaut]ber".
void Suggest::processDicNodeAsDigraph(DicTraverseSession *traverseSession,
        DicNode *childDicNode) const {
    weightChildNode(traverseSession, childDicNode);
    childDicNode->advanceDigraphIndex();
    processExpandedDicNode(traverseSession, childDicNode);
}

/**
 * Handle the dicNode as an omission error (e.g., ths => this). Skip the current letter and consider
 * matches for all possible next letters. Note that just skipping the current letter without any
 * other conditions tends to flood the search dic nodes cache with omission nodes. Instead, check
 * the possible *next* letters after the omission to better limit search to plausible omissions.
 * Note that apostrophes are handled as omissions.
 */
void Suggest::processDicNodeAsOmission(
        DicTraverseSession *traverseSession, DicNode *dicNode) const {
    DicNodeVector childDicNodes;
    DicNodeUtils::getAllChildDicNodes(
            dicNode, traverseSession->getDictionaryStructurePolicy(), &childDicNodes);

    const int size = childDicNodes.getSizeAndLock();
    for (int i = 0; i < size; i++) {
        DicNode *const childDicNode = childDicNodes[i];
        // Treat this word as omission
        Weighting::addCostAndForwardInputIndex(WEIGHTING, CT_OMISSION, traverseSession,
                dicNode, childDicNode, 0 /* multiBigramMap */);
        weightChildNode(traverseSession, childDicNode);
        if (!TRAVERSAL->isPossibleOmissionChildNode(traverseSession, dicNode, childDicNode)) {
            continue;
        }
        processExpandedDicNode(traverseSession, childDicNode);
    }
}

/**
 * Handle the dicNode as an insertion error (e.g., thiis => this). Skip the current touch point and
 * consider matches for the next touch point.
 */
void Suggest::processDicNodeAsInsertion(DicTraverseSession *traverseSession,
        DicNode *dicNode) const {
    const int16_t pointIndex = dicNode->getInputIndex(0);
    DicNodeVector childDicNodes;
    DicNodeUtils::getAllChildDicNodes(dicNode, traverseSession->getDictionaryStructurePolicy(),
            &childDicNodes);
    const int size = childDicNodes.getSizeAndLock();
    for (int i = 0; i < size; i++) {
        if (traverseSession->getProximityInfoState(0)->getPrimaryCodePointAt(pointIndex + 1)
                != childDicNodes[i]->getNodeCodePoint()) {
            continue;
        }
        DicNode *const childDicNode = childDicNodes[i];
        Weighting::addCostAndForwardInputIndex(WEIGHTING, CT_INSERTION, traverseSession,
                dicNode, childDicNode, 0 /* multiBigramMap */);
        processExpandedDicNode(traverseSession, childDicNode);
    }
}

/**
 * Handle the dicNode as a transposition error (e.g., thsi => this). Swap the next two touch points.
 */
void Suggest::processDicNodeAsTransposition(DicTraverseSession *traverseSession,
        DicNode *dicNode) const {
    const int16_t pointIndex = dicNode->getInputIndex(0);
    DicNodeVector childDicNodes1;
    DicNodeUtils::getAllChildDicNodes(dicNode, traverseSession->getDictionaryStructurePolicy(),
            &childDicNodes1);
    const int childSize1 = childDicNodes1.getSizeAndLock();
    for (int i = 0; i < childSize1; i++) {
        const ProximityType matchedId1 = traverseSession->getProximityInfoState(0)
                ->getProximityType(pointIndex + 1, childDicNodes1[i]->getNodeCodePoint(),
                        true /* checkProximityChars */);
        if (!ProximityInfoUtils::isMatchOrProximityChar(matchedId1)) {
            continue;
        }
        if (childDicNodes1[i]->hasChildren()) {
            DicNodeVector childDicNodes2;
            DicNodeUtils::getAllChildDicNodes(childDicNodes1[i],
                    traverseSession->getDictionaryStructurePolicy(), &childDicNodes2);
            const int childSize2 = childDicNodes2.getSizeAndLock();
            for (int j = 0; j < childSize2; j++) {
                DicNode *const childDicNode2 = childDicNodes2[j];
                const ProximityType matchedId2 = traverseSession->getProximityInfoState(0)
                        ->getProximityType(pointIndex, childDicNode2->getNodeCodePoint(),
                                true /* checkProximityChars */);
                if (!ProximityInfoUtils::isMatchOrProximityChar(matchedId2)) {
                    continue;
                }
                Weighting::addCostAndForwardInputIndex(WEIGHTING, CT_TRANSPOSITION,
                        traverseSession, childDicNodes1[i], childDicNode2, 0 /* multiBigramMap */);
                processExpandedDicNode(traverseSession, childDicNode2);
            }
        }
        DicNode::managedDelete(childDicNodes1[i]);
    }
}

/**
 * Weight child node by aligning it to the key
 */
void Suggest::weightChildNode(DicTraverseSession *traverseSession, DicNode *dicNode) const {
    const int inputSize = traverseSession->getInputSize();
    if (dicNode->isCompletion(inputSize)) {
        Weighting::addCostAndForwardInputIndex(WEIGHTING, CT_COMPLETION, traverseSession,
                0 /* parentDicNode */, dicNode, 0 /* multiBigramMap */);
    } else { // completion
        Weighting::addCostAndForwardInputIndex(WEIGHTING, CT_MATCH, traverseSession,
                0 /* parentDicNode */, dicNode, 0 /* multiBigramMap */);
    }
}

/**
 * Creates a new dicNode that represents a space insertion at the end of the input dicNode. Also
 * incorporates the unigram / bigram score for the ending word into the new dicNode.
 */
void Suggest::createNextWordDicNode(DicTraverseSession *traverseSession, DicNode *dicNode,
        const bool spaceSubstitution) const {
    if (!TRAVERSAL->isGoodToTraverseNextWord(dicNode)) {
        return;
    }

    // Create a non-cached node here.
    DicNode newDicNode;
    DicNodeUtils::initAsRootWithPreviousWord(
            traverseSession->getDictionaryStructurePolicy(), dicNode, &newDicNode);
    const CorrectionType correctionType = spaceSubstitution ?
            CT_NEW_WORD_SPACE_SUBSTITUTION : CT_NEW_WORD_SPACE_OMISSION;
    Weighting::addCostAndForwardInputIndex(WEIGHTING, correctionType, traverseSession, dicNode,
            &newDicNode, traverseSession->getMultiBigramMap());
    if (newDicNode.getCompoundDistance() < static_cast<float>(MAX_VALUE_FOR_WEIGHTING)) {
        // newDicNode is worth continuing to traverse.
        // CAVEAT: This pruning is important for speed. Remove this when we can afford not to prune
        // here because here is not the right place to do pruning. Pruning should take place only
        // in DicNodePriorityQueue.
        traverseSession->getDicTraverseCache()->copyPushNextActive(&newDicNode);
    }
}
} // namespace latinime
