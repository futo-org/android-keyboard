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

#ifndef LATINIME_SUGGEST_IMPL_H
#define LATINIME_SUGGEST_IMPL_H

#include "defines.h"
#include "suggest/core/suggest_interface.h"
#include "suggest/core/policy/suggest_policy.h"

namespace latinime {

// Naming convention
// - Distance: "Weighted" edit distance -- used both for spatial and language.
// - Compound Distance: Spatial Distance + Language Distance -- used for pruning and scoring
// - Cost: delta/diff for Distance -- used both for spatial and language
// - Length: "Non-weighted" -- used only for spatial
// - Probability: "Non-weighted" -- used only for language
// - Score: Final calibrated score based on the compound distance, which is sent to java as the
//       priority of a suggested word

class DicNode;
class DicTraverseSession;
class ProximityInfo;
class Scoring;
class Traversal;
class Weighting;

class Suggest : public SuggestInterface {
 public:
    AK_FORCE_INLINE Suggest(const SuggestPolicy *const suggestPolicy)
            : TRAVERSAL(suggestPolicy ? suggestPolicy->getTraversal() : 0),
              SCORING(suggestPolicy ? suggestPolicy->getScoring() : 0),
              WEIGHTING(suggestPolicy ? suggestPolicy->getWeighting() : 0) {}
    AK_FORCE_INLINE virtual ~Suggest() {}
    int getSuggestions(ProximityInfo *pInfo, void *traverseSession, int *inputXs, int *inputYs,
            int *times, int *pointerIds, int *inputCodePoints, int inputSize, int commitPoint,
            int *outWords, int *frequencies, int *outputIndices, int *outputTypes,
            int *outputAutoCommitFirstWordConfidence) const;

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(Suggest);
    void createNextWordDicNode(DicTraverseSession *traverseSession, DicNode *dicNode,
            const bool spaceSubstitution) const;
    int outputSuggestions(DicTraverseSession *traverseSession, int *frequencies,
            int *outputCodePoints, int *outputIndicesToPartialCommit, int *outputTypes,
            int *outputAutoCommitFirstWordConfidence) const;
    int computeFirstWordConfidence(const DicNode *const terminalDicNode) const;
    void initializeSearch(DicTraverseSession *traverseSession, int commitPoint) const;
    void expandCurrentDicNodes(DicTraverseSession *traverseSession) const;
    void processTerminalDicNode(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void processExpandedDicNode(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void weightChildNode(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    float getAutocorrectScore(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void generateFeatures(
            DicTraverseSession *traverseSession, DicNode *dicNode, float *features) const;
    void processDicNodeAsOmission(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void processDicNodeAsDigraph(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void processDicNodeAsTransposition(DicTraverseSession *traverseSession,
            DicNode *dicNode) const;
    void processDicNodeAsInsertion(DicTraverseSession *traverseSession, DicNode *dicNode) const;
    void processDicNodeAsAdditionalProximityChar(DicTraverseSession *traverseSession,
            DicNode *dicNode, DicNode *childDicNode) const;
    void processDicNodeAsSubstitution(DicTraverseSession *traverseSession, DicNode *dicNode,
            DicNode *childDicNode) const;
    void processDicNodeAsMatch(DicTraverseSession *traverseSession,
            DicNode *childDicNode) const;

    // Inputs longer than this will autocorrect if the suggestion is multi-word
    static const int MIN_LEN_FOR_MULTI_WORD_AUTOCORRECT;
    static const int MIN_CONTINUOUS_SUGGESTION_INPUT_SIZE;

    // Threshold for autocorrection classifier
    static const float AUTOCORRECT_CLASSIFICATION_THRESHOLD;

    const Traversal *const TRAVERSAL;
    const Scoring *const SCORING;
    const Weighting *const WEIGHTING;
};
} // namespace latinime
#endif // LATINIME_SUGGEST_IMPL_H
