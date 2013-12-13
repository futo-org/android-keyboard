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

#include "suggest/policyimpl/typing/typing_weighting.h"

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/policyimpl/typing/scoring_params.h"

namespace latinime {

const TypingWeighting TypingWeighting::sInstance;

ErrorType TypingWeighting::getErrorType(const CorrectionType correctionType,
        const DicTraverseSession *const traverseSession, const DicNode *const parentDicNode,
        const DicNode *const dicNode) const {
    switch (correctionType) {
        case CT_MATCH:
            if (isProximityDicNode(traverseSession, dicNode)) {
                return ET_PROXIMITY_CORRECTION;
            } else {
                return ET_NOT_AN_ERROR;
            }
        case CT_ADDITIONAL_PROXIMITY:
            return ET_PROXIMITY_CORRECTION;
        case CT_OMISSION:
            if (parentDicNode->canBeIntentionalOmission()) {
                return ET_INTENTIONAL_OMISSION;
            } else {
                return ET_EDIT_CORRECTION;
            }
            break;
        case CT_SUBSTITUTION:
        case CT_INSERTION:
        case CT_TERMINAL_INSERTION:
        case CT_TRANSPOSITION:
            return ET_EDIT_CORRECTION;
        case CT_NEW_WORD_SPACE_OMISSION:
        case CT_NEW_WORD_SPACE_SUBSTITUTION:
            return ET_NEW_WORD;
        case CT_TERMINAL:
            return ET_NOT_AN_ERROR;
        case CT_COMPLETION:
            return ET_COMPLETION;
        default:
            return ET_NOT_AN_ERROR;
    }
}
}  // namespace latinime
