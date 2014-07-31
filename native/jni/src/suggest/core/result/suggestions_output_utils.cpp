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

#include "suggest/core/result/suggestions_output_utils.h"

#include <algorithm>
#include <vector>

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_utils.h"
#include "suggest/core/dictionary/binary_dictionary_shortcut_iterator.h"
#include "suggest/core/dictionary/error_type_utils.h"
#include "suggest/core/policy/scoring.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/core/suggest_options.h"

namespace latinime {

const int SuggestionsOutputUtils::MIN_LEN_FOR_MULTI_WORD_AUTOCORRECT = 16;

/* static */ void SuggestionsOutputUtils::outputSuggestions(
        const Scoring *const scoringPolicy, DicTraverseSession *traverseSession,
        const float languageWeight, SuggestionResults *const outSuggestionResults) {
#if DEBUG_EVALUATE_MOST_PROBABLE_STRING
    const int terminalSize = 0;
#else
    const int terminalSize = traverseSession->getDicTraverseCache()->terminalSize();
#endif
    std::vector<DicNode> terminals(terminalSize);
    for (int index = terminalSize - 1; index >= 0; --index) {
        traverseSession->getDicTraverseCache()->popTerminal(&terminals[index]);
    }
    // Compute a language weight when an invalid language weight is passed.
    // NOT_A_LANGUAGE_WEIGHT (-1) is assumed as an invalid language weight.
    const float languageWeightToOutputSuggestions = (languageWeight < 0.0f) ?
            scoringPolicy->getAdjustedLanguageWeight(
                    traverseSession, terminals.data(), terminalSize) : languageWeight;
    outSuggestionResults->setLanguageWeight(languageWeightToOutputSuggestions);
    // Force autocorrection for obvious long multi-word suggestions when the top suggestion is
    // a long multiple words suggestion.
    // TODO: Implement a smarter auto-commit method for handling multi-word suggestions.
    const bool forceCommitMultiWords = scoringPolicy->autoCorrectsToMultiWordSuggestionIfTop()
            && (traverseSession->getInputSize() >= MIN_LEN_FOR_MULTI_WORD_AUTOCORRECT
                    && !terminals.empty() && terminals.front().hasMultipleWords());
    // TODO: have partial commit work even with multiple pointers.
    const bool outputSecondWordFirstLetterInputIndex =
            traverseSession->isOnlyOnePointerUsed(0 /* pointerId */);
    const bool boostExactMatches = traverseSession->getDictionaryStructurePolicy()->
            getHeaderStructurePolicy()->shouldBoostExactMatches();

    // Output suggestion results here
    for (auto &terminalDicNode : terminals) {
        outputSuggestionsOfDicNode(scoringPolicy, traverseSession, &terminalDicNode,
                languageWeightToOutputSuggestions, boostExactMatches, forceCommitMultiWords,
                outputSecondWordFirstLetterInputIndex, outSuggestionResults);
    }
    scoringPolicy->getMostProbableString(traverseSession, languageWeightToOutputSuggestions,
            outSuggestionResults);
}

/* static */ void SuggestionsOutputUtils::outputSuggestionsOfDicNode(
        const Scoring *const scoringPolicy, DicTraverseSession *traverseSession,
        const DicNode *const terminalDicNode, const float languageWeight,
        const bool boostExactMatches, const bool forceCommitMultiWords,
        const bool outputSecondWordFirstLetterInputIndex,
        SuggestionResults *const outSuggestionResults) {
    if (DEBUG_GEO_FULL) {
        terminalDicNode->dump("OUT:");
    }
    const float doubleLetterCost =
            scoringPolicy->getDoubleLetterDemotionDistanceCost(terminalDicNode);
    const float compoundDistance = terminalDicNode->getCompoundDistance(languageWeight)
            + doubleLetterCost;
    const bool isPossiblyOffensiveWord =
            traverseSession->getDictionaryStructurePolicy()->getProbability(
                    terminalDicNode->getProbability(), NOT_A_PROBABILITY) <= 0;
    const bool isExactMatch =
            ErrorTypeUtils::isExactMatch(terminalDicNode->getContainedErrorTypes());
    const bool isExactMatchWithIntentionalOmission =
            ErrorTypeUtils::isExactMatchWithIntentionalOmission(
                    terminalDicNode->getContainedErrorTypes());
    const bool isFirstCharUppercase = terminalDicNode->isFirstCharUppercase();
    // Heuristic: We exclude probability=0 first-char-uppercase words from exact match.
    // (e.g. "AMD" and "and")
    const bool isSafeExactMatch = isExactMatch
            && !(isPossiblyOffensiveWord && isFirstCharUppercase);
    const int outputTypeFlags =
            (isPossiblyOffensiveWord ? Dictionary::KIND_FLAG_POSSIBLY_OFFENSIVE : 0)
            | ((isSafeExactMatch && boostExactMatches) ? Dictionary::KIND_FLAG_EXACT_MATCH : 0)
            | (isExactMatchWithIntentionalOmission ?
                    Dictionary::KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION : 0);

    // Entries that are blacklisted or do not represent a word should not be output.
    const bool isValidWord = !terminalDicNode->isBlacklistedOrNotAWord();
    // When we have to block offensive words, non-exact matched offensive words should not be
    // output.
    const bool blockOffensiveWords = traverseSession->getSuggestOptions()->blockOffensiveWords();
    const bool isBlockedOffensiveWord = blockOffensiveWords && isPossiblyOffensiveWord
            && !isSafeExactMatch;

    // Increase output score of top typing suggestion to ensure autocorrection.
    // TODO: Better integration with java side autocorrection logic.
    const int finalScore = scoringPolicy->calculateFinalScore(
            compoundDistance, traverseSession->getInputSize(),
            terminalDicNode->getContainedErrorTypes(),
            (forceCommitMultiWords && terminalDicNode->hasMultipleWords()),
            boostExactMatches);

    // Don't output invalid or blocked offensive words. However, we still need to submit their
    // shortcuts if any.
    if (isValidWord && !isBlockedOffensiveWord) {
        int codePoints[MAX_WORD_LENGTH];
        terminalDicNode->outputResult(codePoints);
        const int indexToPartialCommit = outputSecondWordFirstLetterInputIndex ?
                terminalDicNode->getSecondWordFirstInputIndex(
                        traverseSession->getProximityInfoState(0)) :
                NOT_AN_INDEX;
        outSuggestionResults->addSuggestion(codePoints,
                terminalDicNode->getTotalNodeCodePointCount(),
                finalScore, Dictionary::KIND_CORRECTION | outputTypeFlags,
                indexToPartialCommit, computeFirstWordConfidence(terminalDicNode));
    }

    // Output shortcuts.
    // Shortcut is not supported for multiple words suggestions.
    // TODO: Check shortcuts during traversal for multiple words suggestions.
    if (!terminalDicNode->hasMultipleWords()) {
        BinaryDictionaryShortcutIterator shortcutIt(
                traverseSession->getDictionaryStructurePolicy()->getShortcutsStructurePolicy(),
                traverseSession->getDictionaryStructurePolicy()
                        ->getShortcutPositionOfPtNode(terminalDicNode->getPtNodePos()));
        const bool sameAsTyped = scoringPolicy->sameAsTyped(traverseSession, terminalDicNode);
        outputShortcuts(&shortcutIt, finalScore, sameAsTyped, outSuggestionResults);
    }
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

/* static */ void SuggestionsOutputUtils::outputShortcuts(
        BinaryDictionaryShortcutIterator *const shortcutIt, const int finalScore,
        const bool sameAsTyped, SuggestionResults *const outSuggestionResults) {
    int shortcutTarget[MAX_WORD_LENGTH];
    while (shortcutIt->hasNextShortcutTarget()) {
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
            shortcutScore = std::max(S_INT_MIN + 1, shortcutScore) - 1;
            kind = Dictionary::KIND_SHORTCUT;
        }
        outSuggestionResults->addSuggestion(shortcutTarget, shortcutTargetStringLength,
                std::max(S_INT_MIN + 1, shortcutScore) - 1, kind, NOT_AN_INDEX,
                NOT_A_FIRST_WORD_CONFIDENCE);
    }
}
} // namespace latinime
