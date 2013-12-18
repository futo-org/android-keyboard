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

#include "suggest/core/dictionary/suggestions_output_utils.h"

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_utils.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/dictionary/binary_dictionary_shortcut_iterator.h"
#include "suggest/core/policy/scoring.h"
#include "suggest/core/session/dic_traverse_session.h"

namespace latinime {

const int SuggestionsOutputUtils::MIN_LEN_FOR_MULTI_WORD_AUTOCORRECT = 16;

// TODO: Split this method.
/* static */ int SuggestionsOutputUtils::outputSuggestions(
        const Scoring *const scoringPolicy, DicTraverseSession *traverseSession,
        int *frequencies, int *outputCodePoints, int *outputIndicesToPartialCommit,
        int *outputTypes, int *outputAutoCommitFirstWordConfidence) {
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

    const float languageWeight = scoringPolicy->getAdjustedLanguageWeight(
            traverseSession, terminals, terminalSize);

    int outputWordIndex = 0;
    // Insert most probable word at index == 0 as long as there is one terminal at least
    const bool hasMostProbableString =
            scoringPolicy->getMostProbableString(traverseSession, terminalSize, languageWeight,
                    &outputCodePoints[0], &outputTypes[0], &frequencies[0]);
    if (hasMostProbableString) {
        outputIndicesToPartialCommit[outputWordIndex] = NOT_AN_INDEX;
        ++outputWordIndex;
    }

    // Initial value of the loop index for terminal nodes (words)
    int doubleLetterTerminalIndex = -1;
    DoubleLetterLevel doubleLetterLevel = NOT_A_DOUBLE_LETTER;
    scoringPolicy->searchWordWithDoubleLetter(terminals, terminalSize,
            &doubleLetterTerminalIndex, &doubleLetterLevel);

    int maxScore = S_INT_MIN;
    // Force autocorrection for obvious long multi-word suggestions when the top suggestion is
    // a long multiple words suggestion.
    // TODO: Implement a smarter auto-commit method for handling multi-word suggestions.
    // traverseSession->isPartiallyCommited() always returns false because we never auto partial
    // commit for now.
    const bool forceCommitMultiWords = (terminalSize > 0) ?
            scoringPolicy->autoCorrectsToMultiWordSuggestionIfTop()
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
        const float doubleLetterCost = scoringPolicy->getDoubleLetterDemotionDistanceCost(
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
        const int finalScore = scoringPolicy->calculateFinalScore(
                compoundDistance, traverseSession->getInputSize(),
                terminalDicNode->isExactMatch()
                        || (forceCommitMultiWords && terminalDicNode->hasMultipleWords())
                                || (isValidWord && scoringPolicy->doesAutoCorrectValidWord()));
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
                            ->getShortcutPositionOfPtNode(terminalDicNode->getPtNodePos()));
            // Shortcut is not supported for multiple words suggestions.
            // TODO: Check shortcuts during traversal for multiple words suggestions.
            const bool sameAsTyped = scoringPolicy->sameAsTyped(traverseSession, terminalDicNode);
            const int shortcutBaseScore = scoringPolicy->doesAutoCorrectValidWord() ?
                     scoringPolicy->calculateFinalScore(compoundDistance,
                             traverseSession->getInputSize(), true /* forceCommit */) : finalScore;
            const int updatedOutputWordIndex = outputShortcuts(&shortcutIt,
                    outputWordIndex, shortcutBaseScore, outputCodePoints, frequencies, outputTypes,
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
        scoringPolicy->safetyNetForMostProbableString(terminalSize, maxScore,
                &outputCodePoints[0], &frequencies[0]);
    }
    return outputWordIndex;
}

/* static */ int SuggestionsOutputUtils::computeFirstWordConfidence(
        const DicNode *const terminalDicNode) {
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

/* static */ int SuggestionsOutputUtils::outputShortcuts(
        BinaryDictionaryShortcutIterator *const shortcutIt,
        int outputWordIndex, const int finalScore, int *const outputCodePoints,
        int *const frequencies, int *const outputTypes, const bool sameAsTyped) {
    int shortcutTarget[MAX_WORD_LENGTH];
    while (shortcutIt->hasNextShortcutTarget() && outputWordIndex < MAX_RESULTS) {
        bool isWhilelist;
        int shortcutTargetStringLength;
        shortcutIt->nextShortcutTarget(MAX_WORD_LENGTH, shortcutTarget,
                &shortcutTargetStringLength, &isWhilelist);
        int shortcutScore;
        int kind;
        if (isWhilelist && sameAsTyped) {
            shortcutScore = S_INT_MAX;
            kind = Dictionary::KIND_WHITELIST;
        } else {
            // shortcut entry's score == its base entry's score - 1
            shortcutScore = finalScore;
            // Protection against int underflow
            shortcutScore = max(S_INT_MIN + 1, shortcutScore) - 1;
            kind = Dictionary::KIND_SHORTCUT;
        }
        outputTypes[outputWordIndex] = kind;
        frequencies[outputWordIndex] = shortcutScore;
        frequencies[outputWordIndex] = max(S_INT_MIN + 1, shortcutScore) - 1;
        const int startIndex2 = outputWordIndex * MAX_WORD_LENGTH;
        DicNodeUtils::appendTwoWords(0, 0, shortcutTarget, shortcutTargetStringLength,
                &outputCodePoints[startIndex2]);
        ++outputWordIndex;
    }
    return outputWordIndex;
}
} // namespace latinime
